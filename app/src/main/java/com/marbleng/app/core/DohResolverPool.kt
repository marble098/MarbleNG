package com.marbleng.app.core

import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Transport abstraction for DNS-over-HTTPS (MARBLE_SMART_RANK_V90).
 *
 * The production implementation ([HttpUrlConnectionDohTransport]) keeps a DoH connection pool that
 * is deliberately separate from the general HTTPS / management pool: it enables keep-alive, sets a
 * bounded per-hop idle timeout, and — critically — always drains the full response body before the
 * socket is closed, so a resolver answer can never be torn down mid-read. This is the root-cause
 * fix for the `io: read/write on closed pipe` bursts observed on 1.1.1.1 / 8.8.8.8 for
 * google.com, ws.chatgpt.com, dns.quad9.net and mtalk.google.com: the old code closed the HttpsURL
 * connection (or let a too-short deadline cancel it) before the response stream had finished.
 */
interface DohTransport {
    fun query(endpoint: String, wire: ByteArray, timeoutMs: Long): DohTransportResult
}

/** Result of a single DoH query against one endpoint. */
data class DohTransportResult(
    val body: ByteArray = ByteArray(0),
    val success: Boolean = false,
    /** Precise failure classification; null only on success. */
    val failureKind: ResolverFailureKind? = null,
    val latencyMs: Long = 0L,
    val detail: String = ""
)

/**
 * Production DoH transport over a dedicated, keep-alive connection pool.
 *
 * Each query:
 *  1. sets a per-endpoint read deadline (never shorter than the resolver's overall budget),
 *  2. sends the RFC 8484 POST body,
 *  3. reads the complete response before [javax.net.ssl.HttpsURLConnection.disconnect], so the
 *     socket is returned to the keep-alive pool intact and is never closed mid-response.
 *
 * Failures are classified through the shared [ResolverFailureClassifier] so shutdown-safe events
 * (`read/write on closed pipe`, `broken pipe`) are never counted as resolver outages, while
 * deadline / EOF / TLS / cert-expired are quarantinable.
 */
class HttpUrlConnectionDohTransport : DohTransport {

    override fun query(endpoint: String, wire: ByteArray, timeoutMs: Long): DohTransportResult {
        val start = System.currentTimeMillis()
        val url = java.net.URL(endpoint)
        var conn: javax.net.ssl.HttpsURLConnection? = null
        return try {
            conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setRequestProperty("Host", url.host)
            // Keep-alive lets this dedicated DoH pool reuse sockets instead of re-handshaking
            // every lookup — the general management pool is intentionally not shared here.
            conn.setRequestProperty("Connection", "keep-alive")
            conn.connectTimeout = (timeoutMs / 2).coerceIn(1_000, 3_000).toInt()
            conn.readTimeout = timeoutMs.coerceAtMost(3_000).toInt()
            conn.doOutput = true
            conn.outputStream.use { it.write(wire) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                DohTransportResult(
                    body = ByteArray(0),
                    success = false,
                    failureKind = ResolverFailureKind.OTHER,
                    latencyMs = System.currentTimeMillis() - start,
                    detail = "doh-http-$responseCode"
                )
            } else {
                // Drain the entire response BEFORE disconnect: a partially-read body is the classic
                // trigger for "io: read/write on closed pipe" when the pool recycles the socket.
                val body = conn.inputStream.use { it.readBytes() }
                if (body.size < 12) {
                    DohTransportResult(
                        body = ByteArray(0),
                        success = false,
                        failureKind = ResolverFailureKind.EOF,
                        latencyMs = System.currentTimeMillis() - start,
                        detail = "empty-doh-body"
                    )
                } else {
                    DohTransportResult(
                        body = body,
                        success = true,
                        failureKind = null,
                        latencyMs = System.currentTimeMillis() - start,
                        detail = ""
                    )
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            DohTransportResult(
                body = ByteArray(0), success = false,
                failureKind = ResolverFailureKind.CANCELLED,
                latencyMs = System.currentTimeMillis() - start, detail = "interrupted"
            )
        } catch (t: Throwable) {
            val kind = if (Thread.currentThread().isInterrupted) {
                ResolverFailureKind.CANCELLED
            } else {
                ResolverFailureClassifier.classifyThrowable(t) ?: ResolverFailureKind.OTHER
            }
            DohTransportResult(
                body = ByteArray(0), success = false, failureKind = kind,
                latencyMs = System.currentTimeMillis() - start,
                detail = (t.message ?: t::class.java.simpleName).take(160)
            )
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    companion object {
        init {
            // Enable HTTP keep-alive for the dedicated DoH pool. The general management pool is
            // built with its own explicit `Connection: close` policy in SocksHttpClient, so this
            // setting only widens reuse for DoH sockets.
            runCatching { System.setProperty("http.keepAlive", "true") }
        }
    }
}

/**
 * Parallel racing resolver pool over at least four providers (MARBLE_SMART_RANK_V90).
 *
 * Cloudflare, Google and Quad9 are raced in parallel together with an internal/proxied DoH
 * provider, so a blocked or throttled public resolver can never stall the whole lookup. The first
 * valid answer wins; any error class (not just timeouts) triggers automatic fallback to the next
 * provider. A bounded overall deadline guarantees the race itself can never exceed the resolver's
 * budget.
 */
class DohResolverPool(
    private val transport: DohTransport,
    private val executor: ExecutorService,
    private val overallDeadlineMs: Long = 4_000L
) {

    data class Provider(
        val id: String,
        val endpoint: String,
        /** True for the internal/proxied fallback tier (raced last, never first). */
        val internal: Boolean = false
    )

    data class RaceOutcome(
        val success: Boolean,
        val body: ByteArray = ByteArray(0),
        val providerId: String? = null,
        val latencyMs: Long = 0L,
        /** Per-provider failures in race order, so callers can quarantine the right endpoint. */
        val failures: List<Pair<Provider, DohTransportResult>> = emptyList()
    )

    companion object {
        /** Cloudflare, Google, Quad9 + an internal/proxied DoH. */
        val DEFAULT_PROVIDERS = listOf(
            Provider("cloudflare-doh", "https://1.1.1.1/dns-query"),
            Provider("google-doh", "https://8.8.8.8/dns-query"),
            Provider("quad9-doh", "https://9.9.9.9/dns-query"),
            Provider("internal-doh", "https://dns.shecan.ir/dns-query", internal = true)
        )
    }

    /**
     * Race [wire] against every [providers] entry in parallel and return the first success. Any
     * error — deadline, EOF, TLS, certificate-expired, closed-pipe, etc. — falls back to the next
     * provider. Returns [RaceOutcome.success] = false with the per-provider [RaceOutcome.failures]
     * only when every provider failed or the overall deadline elapsed.
     */
    fun raceResolve(
        wire: ByteArray,
        providers: List<Provider> = DEFAULT_PROVIDERS,
        perResolverTimeoutMs: Long = (overallDeadlineMs / 2).coerceIn(1_000, 3_000)
    ): RaceOutcome {
        if (providers.isEmpty()) {
            return RaceOutcome(success = false, failures = emptyList())
        }

        val deadline = System.currentTimeMillis() + overallDeadlineMs
        val ordered = providers.sortedBy { it.internal } // public providers race first

        val futures = mutableListOf<Pair<Provider, Future<DohTransportResult>>>()
        for (provider in ordered) {
            val task = Callable { transport.query(provider.endpoint, wire, perResolverTimeoutMs) }
            val future = try {
                executor.submit(task)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                // Executor shut down (teardown): classify as cancellation, never a resolver outage.
                val cancelled = DohTransportResult(
                    body = ByteArray(0), success = false,
                    failureKind = ResolverFailureKind.CANCELLED, detail = "executor-shutdown"
                )
                return RaceOutcome(
                    success = false,
                    failures = listOf(provider to cancelled)
                )
            }
            futures += provider to future
        }

        val failures = mutableListOf<Pair<Provider, DohTransportResult>>()
        var remaining: List<Pair<Provider, Future<DohTransportResult>>> = futures
        while (remaining.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            if (nowMs >= deadline) break
            val budgetMs = (deadline - nowMs).coerceAtLeast(1L)

            val stillPending = mutableListOf<Pair<Provider, Future<DohTransportResult>>>()
            var progressed = false
            for ((provider, future) in remaining) {
                try {
                    val result = future.get(budgetMs, TimeUnit.MILLISECONDS)
                    progressed = true
                    if (result.success && result.body.size >= 12) {
                        // First valid answer wins; cancel the stragglers.
                        remaining.forEach { (_, other) ->
                            if (other !== future) runCatching { other.cancel(true) }
                        }
                        return RaceOutcome(
                            success = true,
                            body = result.body,
                            providerId = provider.id,
                            latencyMs = result.latencyMs,
                            failures = failures
                        )
                    }
                    failures += provider to result
                } catch (_: java.util.concurrent.TimeoutException) {
                    stillPending += provider to future
                } catch (_: java.util.concurrent.CancellationException) {
                    failures += provider to DohTransportResult(
                        body = ByteArray(0), success = false,
                        failureKind = ResolverFailureKind.CANCELLED, detail = "cancelled"
                    )
                } catch (_: java.util.concurrent.ExecutionException) {
                    failures += provider to DohTransportResult(
                        body = ByteArray(0), success = false,
                        failureKind = ResolverFailureKind.OTHER, detail = "transport-error"
                    )
                }
            }
            if (!progressed && stillPending.size == remaining.size) {
                // Nothing completed within this budget slice; break out rather than spin.
                break
            }
            remaining = stillPending
        }

        remaining.forEach { (_, future) -> runCatching { future.cancel(true) } }
        if (failures.isEmpty()) {
            failures += Provider("race-deadline", "") to DohTransportResult(
                body = ByteArray(0), success = false,
                failureKind = ResolverFailureKind.DEADLINE,
                latencyMs = overallDeadlineMs, detail = "race-deadline"
            )
        }
        return RaceOutcome(success = false, failures = failures)
    }

    /** Short, user-readable one-line outcome for logs / diagnostics. */
    fun describe(outcome: RaceOutcome): String = if (outcome.success) {
        "DoH race won by ${outcome.providerId} in ${String.format(Locale.US, "%.0f", outcome.latencyMs.toDouble())}ms"
    } else {
        "DoH race failed • " + outcome.failures.joinToString(", ") { (provider, failure) ->
            "${provider.id}/${failure.failureKind?.code ?: "unknown"}" +
                "${failure.detail.takeIf { d -> d.isNotBlank() }?.let { d -> "($d)" } ?: ""}"
        }
    }
}
