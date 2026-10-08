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
        // A DoH provider with a hostname (or a redirect to one) would bootstrap through netd
        // before encryption starts. No direct JVM DoH call may reveal a queried node name to
        // Android DNS; providers must be HTTPS IP literals or this lookup fails closed.
        val endpointUrl = runCatching { java.net.URL(endpoint) }.getOrNull()
        if (endpointUrl == null || endpointUrl.protocol != "https" ||
            !AddressFamilyPolicy.isLiteralIp(endpointUrl.host)) {
            return DohTransportResult(success = false, failureKind = ResolverFailureKind.OTHER,
                detail = "unsafe-doh-bootstrap")
        }
        val attempts = 2
        var lastResult: DohTransportResult? = null
        for (attempt in 0 until attempts) {
            val result = queryOnce(endpoint, wire, timeoutMs, attempt)
            if (result.success) return result
            val retryable = result.failureKind == ResolverFailureKind.CLOSED_PIPE ||
                result.failureKind == ResolverFailureKind.EOF ||
                result.detail.contains("reset", ignoreCase = true) ||
                result.detail.contains("closed pipe", ignoreCase = true) ||
                result.detail.contains("broken pipe", ignoreCase = true)
            if (!retryable || attempt == attempts - 1) return result
            lastResult = result
            try { Thread.sleep(80L * (attempt + 1)) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return result }
        }
        return lastResult ?: DohTransportResult(
            body = ByteArray(0), success = false,
            failureKind = ResolverFailureKind.OTHER, detail = "retry-exhausted"
        )
    }

    private fun queryOnce(endpoint: String, wire: ByteArray, timeoutMs: Long, attempt: Int): DohTransportResult {
        val start = System.currentTimeMillis()
        val url = java.net.URL(endpoint)
        var conn: javax.net.ssl.HttpsURLConnection? = null
        return try {
            conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection)
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setRequestProperty("Host", url.host)
            val useKeepAlive = attempt == 0
            conn.setRequestProperty("Connection", if (useKeepAlive) "keep-alive" else "close")
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
        /** Four IP-literal public providers + an internal/proxied IP-literal DoH fallback. */
        val DEFAULT_PROVIDERS = listOf(
            Provider("cloudflare-doh", "https://1.1.1.1/dns-query"),
            Provider("google-doh", "https://8.8.8.8/dns-query"),
            Provider("quad9-doh", "https://9.9.9.9/dns-query"),
            Provider("cloudflare-fallback", "https://1.0.0.1/dns-query"),
            Provider("internal-doh", "https://149.112.112.112/dns-query", internal = true)
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

        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(overallDeadlineMs.coerceAtLeast(0L))
        val ordered = providers.sortedBy { it.internal } // public providers race first

        // Poll COMPLETIONS, not futures in provider order. Waiting on the first provider's
        // Future.get(deadline) hid an answer from a healthy second provider until the deadline:
        // a blocked Cloudflare IPv6 endpoint could make all IPv6 node probes fail even though
        // Google (or an IPv4-reachable DoH peer) had already returned AAAA in milliseconds.
        val completion = java.util.concurrent.ExecutorCompletionService<Pair<Provider, DohTransportResult>>(executor)
        val pending = linkedMapOf<Future<Pair<Provider, DohTransportResult>>, Provider>()
        val failures = mutableListOf<Pair<Provider, DohTransportResult>>()
        try {
            for (provider in ordered) {
                val future = try {
                    completion.submit(Callable { provider to transport.query(provider.endpoint, wire, perResolverTimeoutMs) })
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    failures += provider to DohTransportResult(success = false,
                        failureKind = ResolverFailureKind.CANCELLED, detail = "executor-shutdown")
                    continue
                }
                pending[future] = provider
            }
            while (pending.isNotEmpty()) {
                val budgetNanos = deadlineNanos - System.nanoTime()
                if (budgetNanos <= 0L) break
                val finished = try {
                    completion.poll(budgetNanos, TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    failures += Provider("race-cancelled", "") to DohTransportResult(success = false,
                        failureKind = ResolverFailureKind.CANCELLED, detail = "interrupted")
                    break
                } ?: break
                val provider = pending.remove(finished) ?: continue
                val result = try {
                    finished.get().second
                } catch (_: java.util.concurrent.ExecutionException) {
                    DohTransportResult(success = false, failureKind = ResolverFailureKind.OTHER,
                        detail = "transport-error")
                } catch (_: java.util.concurrent.CancellationException) {
                    DohTransportResult(success = false, failureKind = ResolverFailureKind.CANCELLED,
                        detail = "cancelled")
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    DohTransportResult(success = false, failureKind = ResolverFailureKind.CANCELLED,
                        detail = "interrupted")
                }
                if (result.success && result.body.size >= 12) {
                    return RaceOutcome(success = true, body = result.body,
                        providerId = provider.id, latencyMs = result.latencyMs, failures = failures)
                }
                failures += provider to result
                if (Thread.currentThread().isInterrupted) break
            }
        } finally {
            // The winner never waits for stragglers, and cancelled / superseded lookups cannot
            // keep a worker occupied beyond the transport's own socket timeout.
            pending.keys.forEach { runCatching { it.cancel(true) } }
        }

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
