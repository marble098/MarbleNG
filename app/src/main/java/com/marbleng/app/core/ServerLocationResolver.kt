package com.marbleng.app.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ServerLocationKey {
    fun of(host: String, port: Int): String {
        val h = host.trim().removeSurrounding("[", "]").lowercase().trim('.')
        if (h.isBlank()) return ""
        return if (port in 1..65535) "$h:$port" else h
    }
}

data class GeoObservation(
    val country: String = "",
    val ip: String = ""
)

/**
 * MARBLE_SERVER_LOCATION_V197 — how much a location answer is worth.
 *
 * Not every answer deserves to become a flag, and not every missing answer deserves to become a
 * blank circle. The difference is how many independent services agreed.
 */
enum class LocationConfidence {
    NONE,

    LONE,

    QUORUM
}

/**
 * The result of one location test, with its evidence attached.
 *
 * MARBLE_SERVER_LOCATION_V197 — the old contract was a bare `String`: the caller could not tell
 * "five services agreed" from "one service answered while four were unreachable", so the code that
 * cached it either had to trust the thin answer forever or throw it away forever. Carrying the
 * confidence lets the app do the only honest thing with a lone answer: show it, remember that it is
 * thin, and quietly try again later.
 */
data class ServerLocationVerdict(
    val code: String = "",
    val confidence: LocationConfidence = LocationConfidence.NONE,
    val observers: Int = 0,
    val providers: Int = 0,
    val witnesses: Int = 0,
    val address: String = ""
) {
    val isKnown: Boolean get() = code.length == 2 && code.all { it in 'A'..'Z' }

    val provisional: Boolean get() = isKnown && confidence == LocationConfidence.LONE

    val evidence: String
        get() = if (!isKnown) "unknown ($observers/$providers answered)" else {
            "$code (${confidence.name.lowercase()} $witnesses/$providers)"
        }
}

fun interface LocationHttpSource {
    fun fetch(url: String): String?
}

fun interface LocationDnsSource {
    fun addresses(host: String): List<String>
}

object ServerLocationResolver {
    /**
     * Independent public lookups that answer for a given address.
     *
     * MARBLE_SERVER_LOCATION_V197 — three providers was not a quorum, it was a coin flip. These
     * services are free and keyless, which also means each of them is rate-limited, blocked or
     * simply unreachable from some networks some of the time. With three of them, the common case
     * on a filtered network was "one answered", and the old two-vote rule threw that answer away —
     * so the app tested a server's location and then showed nothing. Five providers make two
     * independent agreements reachable again, and the shapes are deliberately different (two use
     * `country_code`, one uses `countryCode`, one answers with a spelled-out name) so a single
     * upstream changing its schema cannot empty the whole vote.
     */
    private val ENDPOINTS: List<(String) -> String> = listOf(
        { ip -> "https://ipwho.is/${urlHost(ip)}" },
        { ip -> "https://api.country.is/${urlHost(ip)}" },
        { ip -> "https://ipapi.co/${urlHost(ip)}/json/" },
        { ip -> "https://freeipapi.com/api/json/${urlHost(ip)}" },
        { ip -> "https://api.ip.sb/geoip/${urlHost(ip)}" }
    )

    const val PROVIDER_COUNT = 5

    const val VOTE_QUORUM = 2

    /**
     * MARBLE_SERVER_LOCATION_V197 — how many providers must be *unreachable* before a single
     * answer is trusted on its own.
     *
     * One service answering while one other service stays silent is not evidence: the silent one
     * may simply have been reachable and disagreeing. One service answering while four others
     * cannot be reached at all is a different situation — nobody contradicted it, and the reason
     * there is no second opinion is the network, not the address. That answer is shown and marked
     * provisional, so it is re-tested as soon as the network lets more of the pool through.
     */
    const val MIN_SILENT_WITNESSES_FOR_LONE_ACCEPT = 2

    const val TIMEOUT_MS = 3_000

    fun resolveCountry(
        host: String,
        port: Int = 0,
        dns: LocationDnsSource = defaultDns,
        http: LocationHttpSource = defaultHttp,
        timeoutMs: Int = TIMEOUT_MS
    ): String = resolveCountryDetailed(
        host = host,
        port = port,
        dns = dns,
        http = http,
        timeoutMs = timeoutMs
    ).code

    /**
     * MARBLE_SERVER_LOCATION_V197 — the same test, with its evidence attached.
     *
     * Everything the caller needs to decide what to do with an answer travels with it: the code,
     * how many providers answered, how many of them agreed, and whether that makes the answer a
     * quorum or a single uncontradicted opinion. "Where is this server" is answered here once, in
     * parallel, inside one bounded budget — never serially, and never on the frame clock.
     */
    fun resolveCountryDetailed(
        host: String,
        port: Int = 0,
        dns: LocationDnsSource = defaultDns,
        http: LocationHttpSource = defaultHttp,
        timeoutMs: Int = TIMEOUT_MS
    ): ServerLocationVerdict {
        val address = publicAddressOf(host, dns) ?: return ServerLocationVerdict()

        val pool = Executors.newFixedThreadPool(ENDPOINTS.size)
        val observations = try {
            val jobs = ENDPOINTS.map { builder ->
                Callable {
                    runCatching { http.fetch(builder(address)) }
                        .getOrNull()
                        ?.let { parseLookup(it, address) }
                        ?.takeIf { it.country.isNotBlank() }
                }
            }
            pool.invokeAll(
                jobs,
                timeoutMs.coerceIn(250, TIMEOUT_MS * ENDPOINTS.size).toLong(),
                TimeUnit.MILLISECONDS
            ).mapNotNull { future -> runCatching { future.get() }.getOrNull() }
        } catch (_: Throwable) {
            emptyList<GeoObservation>()
        } finally {
            pool.shutdownNow()
        }

        val code = voteCountry(observations, ENDPOINTS.size)
        if (code.isBlank()) {
            val answered = observations.count { it.country.isNotBlank() }
            return ServerLocationVerdict(
                code = "",
                confidence = LocationConfidence.NONE,
                observers = answered,
                providers = ENDPOINTS.size,
                witnesses = 0,
                address = address
            )
        }
        val witnesses = observations.count { it.country.equals(code, ignoreCase = true) }
        return ServerLocationVerdict(
            code = code,
            confidence = if (witnesses >= VOTE_QUORUM) {
                LocationConfidence.QUORUM
            } else {
                LocationConfidence.LONE
            },
            observers = observations.count { it.country.isNotBlank() },
            providers = ENDPOINTS.size,
            witnesses = witnesses,
            address = address
        )
    }

    fun publicAddressOf(host: String, dns: LocationDnsSource = defaultDns): String? {
        val normalized = host.trim().removeSurrounding("[", "]").trim()
        if (normalized.isBlank()) return null
        val candidates = if (isIpLiteral(normalized)) listOf(normalized) else dns.addresses(normalized)
        return candidates.firstOrNull { isPublicAddress(it) }
    }

    fun isPublicAddress(literal: String): Boolean {
        val v4 = toIpv4(literal)
        if (v4 != null) {
            val a = (v4 shr 24) and 0xFFL
            val b = (v4 shr 16) and 0xFFL
            val c = (v4 shr 8) and 0xFFL
            return when {
                a == 0L -> false

                a == 10L -> false

                a == 100L && (b and 0xC0L) == 64L -> false

                a == 127L -> false

                a == 169L && b == 254L -> false

                a == 172L && (b and 0xF0L) == 16L -> false

                a == 192L && b == 168L -> false

                a == 192L && b == 0L && c == 0L -> false
                a == 192L && b == 0L && c == 2L -> false
                else -> true
            }
        }
        val words = toIpv6(literal) ?: return false
        return when {
            words.all { it == 0L } -> false

            words.dropLast(1).all { it == 0L } && words.last() == 1L -> false

            (words[0] and 0xFFC0L) == 0xFE80L -> false

            (words[0] and 0xFE00L) == 0xFC00L -> false

            words[0] == 0L && words[1] == 0L && words[2] == 0L &&
                words[3] == 0L && words[4] == 0L && words[5] == 0xFFFFL -> {
                val mapped = (words[6] shl 16) or words[7]
                isPublicAddress(
                    "%d.%d.%d.%d".format(
                        (mapped shr 24) and 0xFF, (mapped shr 16) and 0xFF,
                        (mapped shr 8) and 0xFF, mapped and 0xFF
                    )
                )
            }
            else -> true
        }
    }

    fun isIpLiteral(text: String): Boolean =
        toIpv4(text) != null || toIpv6(text) != null

    fun parseLookup(body: String, ip: String = ""): GeoObservation {
        val trimmed = body.trim()
        return try {
            if (!trimmed.startsWith("{")) return GeoObservation("", ip)
            val json = JSONObject(trimmed)
            val code = listOf("country_code", "countryCode")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
                ?:
                json.optString("country")
                    .trim()
                    .let { name -> nameToCode(name) }
            GeoObservation(code.orEmpty(), ip)
        } catch (_: Throwable) {
            GeoObservation("", ip)
        }
    }

    fun voteCountry(
        observations: List<GeoObservation>,
        providersQueried: Int = observations.size
    ): String {
        val usable = observations.map { it.country.trim().uppercase() }
            .filter { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
        if (usable.isEmpty()) return ""
        val votes = usable.groupingBy { it }.eachCount()
        val top = votes.entries.maxByOrNull { it.value } ?: return ""
        val rivals = votes.filterValues { it == top.value }.size

        if (top.value >= VOTE_QUORUM && rivals == 1) return top.key

        if (rivals > 1) return ""
        // MARBLE_SERVER_LOCATION_V197 — the missing case. A national flag used to require a quorum

        val silent = (providersQueried - usable.size).coerceAtLeast(0)
        return if (top.value == 1 && silent >= MIN_SILENT_WITNESSES_FOR_LONE_ACCEPT) top.key else ""
    }

    private fun urlHost(ip: String): String =
        if (ip.contains(':') && !ip.startsWith('[')) "[$ip]" else ip

    private fun nameToCode(name: String): String? {
        if (name.isBlank()) return null
        return ServerCountry.codeForName(name)
    }

    private fun toIpv4(literal: String): Long? {
        if (literal.contains(":")) return null
        val parts = literal.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
        val n = part.toIntOrNull() ?: return null
        if (n < 0 || n > 255) return null
        value = (value shl 8) or n.toLong()
        }
        return value
    }

    private fun toIpv6(literal: String): List<Long>? {
        if (!literal.contains(":")) return null
        val compressed = literal.contains("::")
        val headText: String
        val tailText: String
        if (compressed) {
            val at = literal.indexOf("::")
            headText = literal.substring(0, at)
            tailText = literal.substring(at + 2)
        } else {
            headText = literal
            tailText = ""
        }
        fun groupWords(text: String): List<Long>? {
            if (text.isEmpty()) return emptyList()
            val tokens = text.split(':')
            val words = ArrayList<Long>(tokens.size)
            for (token in tokens) {
                if (token.isEmpty()) return null
                if (token.contains('.')) {
                    if (tokens.last() != token) return null
                    val quad = token.split('.')
                    if (quad.size != 4) return null
                    val nums = quad.map { it.toIntOrNull() ?: return null }
                    if (nums.any { it < 0 || it > 255 }) return null
                    words.add((nums[0].toLong() shl 8) or nums[1].toLong())
                    words.add((nums[2].toLong() shl 8) or nums[3].toLong())
                } else {
                    val value = token.toLongOrNull(radix = 16) ?: return null
                    if (value > 0xFFFFL) return null
                    words.add(value)
                }
            }
            return words
        }
        val left = groupWords(headText) ?: return null
        val right = groupWords(tailText) ?: return null

        val explicit = left.size + right.size
        if (compressed) {
            if (explicit > 7) return null
        } else if (explicit != 8) {
            return null
        }

        val words = left + List(8 - explicit) { 0L } + right
        if (words.size != 8) return null
        if (words.any { it < 0L || it > 0xFFFFL }) return null
        return words
    }

    private val defaultHttp = LocationHttpSource { url ->
        var connection: HttpURLConnection? = null
        try {
            val conn = (URL(url).openConnection()) as HttpURLConnection
            connection = conn
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "MarbleNG/ServerLocation")
            conn.setRequestProperty("Accept", "application/json, text/plain")
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private val defaultDns = LocationDnsSource { host ->

        EncryptedEndpointResolver.resolve(host)
            .filter { it is Inet4Address || it is Inet6Address }
            .map { it.hostAddress.orEmpty() }
            .filter { it.isNotBlank() }
    }
}
