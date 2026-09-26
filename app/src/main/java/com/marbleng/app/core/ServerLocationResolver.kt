package com.marbleng.app.core

// MARBLE_SERVER_LOCATION_V192 — the app tests each server's location once, automatically.
//
// The label a subscription gives a node ("DE Frankfurt 01", a leading flag emoji, nothing at
// all) is a guess. The location the server ACTUALLY lives in is a fact about its public address:
// resolve the host, ask independent public lookups where that address is, and remember the
// answer for good. "Once" is the contract — a result is cached per endpoint in local storage,
// so the radio is used at most once per server per install, and the rows simply show the flag
// the first time the page opens.
//
// The resolver is deliberately network-light and defensive:
//   * bounded endpoints (three, tried in order until one answers), each with a hard 3 s budget;
//   * private / loopback / CGNAT addresses are never sent anywhere — a node that points at the
//     user's own LAN has no public location to test;
//   * a code is accepted only as a clean two-letter ISO alpha-2, so a partial or poisoned
//     answer can never become a flag.

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

/** Per-endpoint identity of a location test: the cache key, stable across renames. */
object ServerLocationKey {
    /** Normalises `host:port`; blank host yields a blank key (nothing to test). */
    fun of(host: String, port: Int): String {
        val h = host.trim().removeSurrounding("[", "]").lowercase().trim('.')
        if (h.isBlank()) return ""
        return if (port in 1..65535) "$h:$port" else h
    }
}

/** One geolocation observation from one public endpoint. */
data class GeoObservation(
    /** ISO 3166-1 alpha-2, upper-case, or blank when the endpoint did not name a country. */
    val country: String = "",
    /** The address the observation was made for (diagnostics only). */
    val ip: String = ""
)

/** Pluggable HTTP transport so the parse/consensus logic is unit-testable off-device. */
fun interface LocationHttpSource {
    /** Returns the response body, or null when the endpoint cannot be reached in budget. */
    fun fetch(url: String): String?
}

/** Pluggable DNS so the private-address gate is unit-testable off-device. */
fun interface LocationDnsSource {
    /** All public addresses of [host] as dotted/colon literals, or empty when unresolvable. */
    fun addresses(host: String): List<String>
}

object ServerLocationResolver {

    /**
     * Independent public lookups that answer for a given address. The first that returns a
     * usable code wins; when several answer, a majority must agree (see [voteCountry]).
     */
    private val ENDPOINTS: List<(String) -> String> = listOf(
        { ip -> "https://ipwho.is/$ip" },
        { ip -> "https://api.country.is/$ip" },
        { ip -> "https://ipapi.co/$ip/json/" }
    )

    const val TIMEOUT_MS = 3_000

    /**
     * Tests where the public endpoint [host]:[port] lives and returns the ISO alpha-2 code, or
     * blank when the address cannot be resolved, is private, or no lookup agrees. Never throws.
     */
    fun resolveCountry(
        host: String,
        port: Int = 0,
        dns: LocationDnsSource = defaultDns,
        http: LocationHttpSource = defaultHttp,
        timeoutMs: Int = TIMEOUT_MS
    ): String {
        val address = publicAddressOf(host, dns) ?: return ""
        val observations = ENDPOINTS.mapNotNull { builder ->
            val body = runCatching { http.fetch(builder(address)) }.getOrNull()
            val observation = body?.let { parseLookup(it, address) }
            if (observation != null && observation.country.isNotBlank()) observation else null
        }
        return voteCountry(observations)
    }

    /**
     * The address to test for [host]: a literal IP when the host is one, otherwise the first
     * public answer from DNS. Private ranges (loopback, RFC1918, CGNAT, ULA, link-local,
     * IPv4-mapped forms of all of them) have no public location, so they yield null instead of
     * ever crossing the wire.
     */
    fun publicAddressOf(host: String, dns: LocationDnsSource = defaultDns): String? {
        val normalized = host.trim().removeSurrounding("[", "]").trim()
        if (normalized.isBlank()) return null
        val candidates = if (isIpLiteral(normalized)) listOf(normalized) else dns.addresses(normalized)
        return candidates.firstOrNull { isPublicAddress(it) }
    }

    /** True for addresses that name the user's own network rather than the Internet. */
    fun isPublicAddress(literal: String): Boolean {
        val v4 = toIpv4(literal)
        if (v4 != null) {
            val a = (v4 shr 24) and 0xFFL
            val b = (v4 shr 16) and 0xFFL
            return when {
                // 0.0.0.0/8
                a == 0L -> false
                // 10.0.0.0/8
                a == 10L -> false
                // 100.64.0.0/10 (CGNAT)
                a == 100L && (b and 0xC0L) == 64L -> false
                // 127.0.0.0/8
                a == 127L -> false
                // 169.254.0.0/16 (link-local)
                a == 169L && b == 254L -> false
                // 172.16.0.0/12
                a == 172L && (b and 0xF0L) == 16L -> false
                // 192.168.0.0/16
                a == 192L && b == 168L -> false
                // 192.0.0.0/24, 192.0.2.0/24 (TEST-NET-1/2)
                a == 192L && (b == 0L || b == 2L) -> false
                else -> true
            }
        }
        val words = toIpv6(literal) ?: return false
        return when {
            // :: — the unspecified address has no location either.
            words.all { it == 0L } -> false
            // ::1 — loopback: seven zero words, then one.
            words.dropLast(1).all { it == 0L } && words.last() == 1L -> false
            // fe80::/10 (link-local) — the masks are built by shifting so the literals stay
            // inside the signed Long range.
            (words[0] and (0xFFC0L shl 48)) == (0xFE80L shl 48) -> false
            // fc00::/7 (unique local, fc00::/8 + fd00::/8)
            (words[0] and (0xFE00L shl 48)) == (0xFC00L shl 48) -> false
            // ::ffff:a.b.c.d mapped — judge by the mapped IPv4, so a mapped 127.x stays private.
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

    /** Parses any of the three lookup bodies into an observation (blank when unusable). */
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
                ?: // ipwho.is / api.country.is fall back to the spelled-out name
                json.optString("country")
                    .trim()
                    .let { name -> nameToCode(name) }
            GeoObservation(code.orEmpty(), ip)
        } catch (_: Throwable) {
            GeoObservation("", ip)
        }
    }

    /**
     * Majority over independent observations. Two agreeing codes win; a single usable
     * observation is accepted (the test is one-shot, and one honest answer is still far better
     * than no flag); contradictory answers resolve to blank rather than a coin flip.
     */
    fun voteCountry(observations: List<GeoObservation>): String {
        val usable = observations.map { it.country.uppercase() }.filter { it.length == 2 }
        if (usable.isEmpty()) return ""
        val votes = usable.groupingBy { it }.eachCount()
        val top = votes.entries.maxByOrNull { it.value } ?: return ""
        val rivals = votes.filterValues { it == top.value }.size
        return if (top.value >= 2 || rivals == 1) top.key else ""
    }

    /** Spelled-out country names the lookups use ("Turkey") mapped back to alpha-2. */
    private fun nameToCode(name: String): String? {
        if (name.isBlank()) return null
        return ServerCountry.codeForName(name)
    }

    /** Dotted-quad IPv4 to 32-bit int. */
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

    /** Full IPv6 literal to eight 16-bit words (compressed forms only). */
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
                    // The IPv4-mapped tail: a.b.c.d stands for the LAST two 16-bit words, and
                    // only the last token may take that form.
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
        // "::" stands for at least one zero group, so the explicit halves hold at most seven
        // words; the uncompressed form needs exactly eight.
        val explicit = left.size + right.size
        if (compressed) {
            if (explicit > 7) return null
        } else if (explicit != 8) {
            return null
        }
        // The zeros the "::" stands for sit BETWEEN the explicit halves: ::1 is seven leading
        // zeros, 2001:db8::1 keeps its prefix and pads before the tail.
        val words = left + List(8 - explicit) { 0L } + right
        if (words.size != 8) return null
        if (words.any { it < 0L || it > 0xFFFFL }) return null
        return words
    }

    /** The default HTTP transport: plain sockets, the same budget discipline as IranMode. */
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
        runCatching { InetAddress.getAllByName(host) }
            .getOrDefault(emptyArray<InetAddress>())
            .filter { it is Inet4Address || it is Inet6Address }
            .map { it.hostAddress.orEmpty() }
            .filter { it.isNotBlank() }
    }
}
