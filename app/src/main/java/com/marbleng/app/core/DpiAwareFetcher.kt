package com.marbleng.app.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPS-only subscription fetch that survives Iranian DPI on GitHub/raw SNI and UA fingerprinting.
 *
 * Iranian operators commonly:
 *  - RST or blackhole `raw.githubusercontent.com` / `gist.githubusercontent.com` by SNI
 *  - fingerprint non-browser User-Agents on TLS + HTTP
 *  - reset first-flight TLS records larger than a few hundred bytes
 *  - never permit a safe HTTP fallback (cleartext is disabled app-wide)
 *
 * Countermeasures: Chrome UA, GitHub→jsDelivr mirrors, bounded payload, no-cleartext redirects,
 * and (caller-driven) a temporary Freedom-fragment SOCKS bridge.
 */
object DpiAwareFetcher {
    data class Payload(
        val text: String,
        val userInfo: String = ""
    )

    fun candidateUrls(url: String): List<String> {
        val trimmed = url.trim()
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Remote subscriptions must use HTTPS"
        }
        val out = linkedSetOf(trimmed)
        githubRawMirror(trimmed)?.let { out += it }
        gistMirror(trimmed)?.let { out += it }
        return out.toList()
    }

    fun fetchDirect(
        url: String,
        maxBytes: Int,
        userAgent: String = DpiEvasionPolicy.BROWSER_UA,
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 30_000
    ): Payload {
        require(url.trim().startsWith("https://", ignoreCase = true)) {
            "Remote subscriptions must use HTTPS"
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "text/plain, text/*, */*")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Cache-Control", "no-cache")

        return try {
            val code = connection.responseCode
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Subscription redirect left HTTPS"
            }
            require(code in 200..299) { "HTTPS $code" }
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= maxBytes.toLong()) {
                "Subscription exceeds ${maxBytes / 1024 / 1024} MiB"
            }
            val userInfo = connection.getHeaderField("subscription-userinfo")
                ?: connection.getHeaderField("Subscription-Userinfo")
                ?: ""
            Payload(
                text = connection.inputStream.use { readBoundedUtf8(it, maxBytes) },
                userInfo = userInfo
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Direct Chrome-UA attempt, then GitHub mirrors, then the caller's fragment SOCKS bridge.
     * Never retries over HTTP.
     *
     * MARBLE_FETCH_RELIABILITY_V78 — smarter fallback order:
     *  1. Direct with browser UA first (covers most unfiltered networks)
     *  2. Mirror URLs for GitHub-blocked networks
     *  3. SOCKS path (connected or Freedom bridge) for fully filtered environments
     *  4. Secondary UA with all mirrors for extreme cases
     */
    fun fetch(
        url: String,
        maxBytes: Int,
        iranActive: Boolean,
        allowDirect: Boolean = true,
        throughSocks: ((candidateUrl: String, userAgent: String) -> Payload)? = null
    ): Payload {
        val urls = candidateUrls(url)
        // Prefer browser UA on any network, add Marble UA only as secondary fallback
        val primaryAgent = DpiEvasionPolicy.BROWSER_UA
        val secondaryAgent = DpiEvasionPolicy.MARBLE_UA
        var last: Throwable? = null

        // Pass 1: direct with primary UA (fast path for most networks)
        if (allowDirect) {
            for (candidate in urls) {
                val attempt = runCatching {
                    fetchDirect(candidate, maxBytes, primaryAgent, connectTimeoutMs = 10_000, readTimeoutMs = 25_000)
                }
                if (attempt.isSuccess) return attempt.getOrThrow()
                last = attempt.exceptionOrNull()
            }
        }

        // Pass 2: SOCKS with primary UA (connected/Iran mode)
        if (throughSocks != null) {
            for (candidate in urls) {
                val attempt = runCatching { throughSocks(candidate, primaryAgent) }
                if (attempt.isSuccess) return attempt.getOrThrow()
                last = attempt.exceptionOrNull()
            }
        }

        // Pass 3: direct with secondary UA
        if (allowDirect) {
            for (candidate in urls) {
                val attempt = runCatching {
                    fetchDirect(candidate, maxBytes, secondaryAgent, connectTimeoutMs = 12_000, readTimeoutMs = 30_000)
                }
                if (attempt.isSuccess) return attempt.getOrThrow()
                last = attempt.exceptionOrNull()
            }
        }

        // Pass 4: SOCKS with secondary UA
        if (throughSocks != null) {
            for (candidate in urls) {
                val attempt = runCatching { throughSocks(candidate, secondaryAgent) }
                if (attempt.isSuccess) return attempt.getOrThrow()
                last = attempt.exceptionOrNull()
            }
        }

        throw last ?: IllegalStateException("Subscription fetch failed after all fallback paths")
    }

    internal fun githubRawMirror(url: String): String? {
        val raw = Regex(
            "^https://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(url.trim()) ?: run {
            val blob = Regex(
                "^https://github\\.com/([^/]+)/([^/]+)/(?:raw|blob)/(.+)$",
                RegexOption.IGNORE_CASE
            ).matchEntire(url.trim()) ?: return null
            val user = blob.groupValues[1]
            val repo = blob.groupValues[2]
            val rest = blob.groupValues[3]
            return "https://cdn.jsdelivr.net/gh/$user/$repo@$rest"
        }
        val user = raw.groupValues[1]
        val repo = raw.groupValues[2]
        val rest = raw.groupValues[3]
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val ref = rest.substring(0, slash)
        val path = rest.substring(slash + 1)
        return "https://cdn.jsdelivr.net/gh/$user/$repo@$ref/$path"
    }

    internal fun gistMirror(url: String): String? {
        val match = Regex(
            "^https://gist\\.githubusercontent\\.com/([^/]+)/([^/]+)/raw/(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(url.trim()) ?: return null
        val user = match.groupValues[1]
        val gist = match.groupValues[2]
        val rest = match.groupValues[3]
        return "https://cdn.jsdelivr.net/gh/$user/$gist@$rest"
    }

    private fun readBoundedUtf8(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            require(total <= maxBytes) { "Subscription exceeds ${maxBytes / 1024 / 1024} MiB" }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
