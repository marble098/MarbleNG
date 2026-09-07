package com.marbleng.app.core

import java.net.URL
import java.util.concurrent.TimeUnit

/** A real request through the selected outbound, never a substituted TCP-connect number. */
data class CoreUrlTestResult(val delayMs: Long, val ok: Boolean, val detail: String = "", val live: Boolean = false)

/** Xray has no Clash delay controller. Its URL Test therefore performs the same HTTPS HEAD
 * operation through its selected SOCKS outbound, with verified TLS and one total deadline.
 * Sing-box uses its own native controller instead. No method silently changes the chosen core. */
object SocksUrlTest {
    fun measure(port: Int, urls: List<String>, timeoutMs: Int): CoreUrlTestResult =
        measureTargets(urls, timeoutMs) { target, budget ->
            SocksHttpClient.request(port = port, host = target.host.removePrefix("[").removeSuffix("]"),
                targetPort = target.port.takeIf { it > 0 } ?: 443,
                path = target.file.ifBlank { "/" }, method = "HEAD", timeoutMs = budget, maxBytes = 16 * 1024)
        }

    internal fun measureTargets(urls: List<String>, timeoutMs: Int,
                                request: (URL, Int) -> HttpProbe): CoreUrlTestResult {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceIn(500, 30_000).toLong())
        var last = CoreUrlTestResult(0, false, "urltest-no-target")
        for (url in urls.distinct().take(3)) {
            SingBoxProcessSession.checkInterrupted()
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).toInt()
            if (remaining <= 0) break
            val target = runCatching { URL(url) }.getOrNull()
            if (target?.protocol != "https" || target.host.isNullOrBlank() || target.userInfo != null) {
                return CoreUrlTestResult(0, false, "urltest-url: an HTTPS URL without user info is required")
            }
            try {
                val response = request(target, remaining)
                // The native Clash test accepts any complete HTTP response. Auth/HTTP errors
                // at a reference site are not proxy handshake failures.
                if (response.status in 100..599 && response.elapsedMs.isFinite() && response.elapsedMs > 0) {
                    return CoreUrlTestResult(kotlin.math.ceil(response.elapsedMs).toLong(), true)
                }
                last = CoreUrlTestResult(0, false, "urltest-response: incomplete HTTP reply")
            } catch (error: Exception) {
                SingBoxProcessSession.checkInterrupted()
                last = CoreUrlTestResult(0, false, "urltest-transport: ${error.message ?: error.javaClass.simpleName}".take(300))
            }
        }
        return last
    }
}
