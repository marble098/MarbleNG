package com.marbleng.app.core

import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

interface DohTransport {
    fun query(endpoint: String, wire: ByteArray, timeoutMs: Long): DohTransportResult
}

data class DohTransportResult(
    val body: ByteArray = ByteArray(0),
    val success: Boolean = false,
    val failureKind: ResolverFailureKind? = null,
    val latencyMs: Long = 0L,
    val detail: String = ""
)

class HttpUrlConnectionDohTransport : DohTransport {
    override fun query(endpoint: String, wire: ByteArray, timeoutMs: Long): DohTransportResult {
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
            // MARBLE_DOH_KEEPALIVE_V238 — the retry used to send `Connection: close`, so every
            // fallback query paid a full TCP+TLS handshake through the path — exactly the
            // handshake storm the report (P1) measured. The pool now keeps the connection warm
            // on every attempt; the JDK pools the underlying socket when streams are drained,
            // which `use { }` below guarantees.
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
            runCatching { System.setProperty("http.keepAlive", "true") }
        }
    }
}

class DohResolverPool(
    private val transport: DohTransport,
    private val executor: ExecutorService,
    private val overallDeadlineMs: Long = 4_000L
) {
    data class Provider(
        val id: String,
        val endpoint: String,
        val internal: Boolean = false
    )

    data class RaceOutcome(
        val success: Boolean,
        val body: ByteArray = ByteArray(0),
        val providerId: String? = null,
        val latencyMs: Long = 0L,
        val failures: List<Pair<Provider, DohTransportResult>> = emptyList()
    )

    companion object {
        val DEFAULT_PROVIDERS = listOf(
            Provider("cloudflare-doh", "https://1.1.1.1/dns-query"),
            Provider("google-doh", "https://8.8.8.8/dns-query"),
            Provider("cloudflare-fallback", "https://1.0.0.1/dns-query"),
            Provider("quad9-doh", "https://9.9.9.9/dns-query"),
            Provider("internal-doh", "https://149.112.112.112/dns-query", internal = true)
        )
    }

    fun raceResolve(
        wire: ByteArray,
        providers: List<Provider> = DEFAULT_PROVIDERS,
        perResolverTimeoutMs: Long = (overallDeadlineMs / 2).coerceIn(1_000, 3_000)
    ): RaceOutcome {
        if (providers.isEmpty()) {
            return RaceOutcome(success = false, failures = emptyList())
        }

        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(overallDeadlineMs.coerceAtLeast(0L))
        val ordered = providers.sortedBy { it.internal }

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

    fun describe(outcome: RaceOutcome): String = if (outcome.success) {
        "DoH race won by ${outcome.providerId} in ${String.format(Locale.US, "%.0f", outcome.latencyMs.toDouble())}ms"
    } else {
        "DoH race failed • " + outcome.failures.joinToString(", ") { (provider, failure) ->
            "${provider.id}/${failure.failureKind?.code ?: "unknown"}" +
                "${failure.detail.takeIf { d -> d.isNotBlank() }?.let { d -> "($d)" } ?: ""}"
        }
    }
}
