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
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

/**
 * MARBLE_SERVER_LOCATION_V197 — how much a location answer is worth.
 *
 * Not every answer deserves to become a flag, and not every missing answer deserves to become a
 * blank circle. The difference is how many independent services agreed.
 */
enum class LocationConfidence {
    /** No usable answer at all: the circle stays neutral. */
    NONE,

    /**
     * Exactly one independent service answered and nobody contradicted it. Real evidence, but
     * thin: it is shown, and it is re-tested the next time more of the pool can be reached.
     */
    LONE,

    /** Two or more independent services named the same country. */
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
    /** ISO 3166-1 alpha-2, upper-case, or blank when nothing usable was learned. */
    val code: String = "",
    val confidence: LocationConfidence = LocationConfidence.NONE,
    /** How many providers returned a usable country for this address. */
    val observers: Int = 0,
    /** How many providers were asked. */
    val providers: Int = 0,
    /** How many of them named [code]. */
    val witnesses: Int = 0,
    /** The public address the test was made against (diagnostics only). */
    val address: String = ""
) {
    val isKnown: Boolean get() = code.length == 2 && code.all { it in 'A'..'Z' }

    /**
     * A lone answer is shown but not trusted forever. The repository re-tests provisional
     * verdicts on the next sweep, which is what turns "one service could answer" into a quorum
     * once the network stops being the thing that is broken.
     */
    val provisional: Boolean get() = isKnown && confidence == LocationConfidence.LONE

    /** One line for diagnostics: `DE (quorum 3/5)`. */
    val evidence: String
        get() = if (!isKnown) "unknown ($observers/$providers answered)" else {
            "$code (${confidence.name.lowercase()} $witnesses/$providers)"
        }
}

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

    /** How many providers are asked for one address. Exposed so callers can report `n/m`. */
    const val PROVIDER_COUNT = 5

    /**
     * How many providers must agree before an answer is treated as more than one opinion.
     * Two independent services naming the same country is the smallest quorum worth a flag.
     */
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
        // Query all providers together: five independent answers should be one parallel round,
        // not five serial three-second waits. invokeAll also cancels unfinished requests when the
        // caller's budget expires, so a refresh can never strand location workers.
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
            val c = (v4 shr 8) and 0xFFL
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
                // 192.0.0.0/24 (This host on this network) and 192.0.2.0/24 (TEST-NET-1),
                // judged at /24 on the third octet so 192.0.3.0/24 and the real public
                // space at 192.2.0.0/16 are not swallowed.
                a == 192L && b == 0L && c == 0L -> false
                a == 192L && b == 0L && c == 2L -> false
                else -> true
            }
        }
        val words = toIpv6(literal) ?: return false
        return when {
            // :: — the unspecified address has no location either.
            words.all { it == 0L } -> false
            // ::1 — loopback: seven zero words, then one.
            words.dropLast(1).all { it == 0L } && words.last() == 1L -> false
            // fe80::/10 (link-local): the top ten bits of the first 16-bit word are 1111111010.
            (words[0] and 0xFFC0L) == 0xFE80L -> false
            // fc00::/7 (unique local, fc00::/8 + fd00::/8): the top nine bits are 111111100.
            (words[0] and 0xFE00L) == 0xFC00L -> false
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
     * Majority over independent observations. At least two agreeing providers are required before
     * a code becomes visible; contradictory or one-off answers resolve to blank rather than a
     * misleading flag.
     */
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
        // A quorum is the strong answer and it is unchanged: two independent services naming the
        // same country, with nobody else tying them, earns the flag outright.
        if (top.value >= VOTE_QUORUM && rivals == 1) return top.key
        // A tie is a coin flip — the product refuses to flip. Two services that disagree are worse
        // evidence than one service that answered, because they prove the address is contested.
        if (rivals > 1) return ""
        // MARBLE_SERVER_LOCATION_V197 — the missing case. A national flag used to require a quorum
        // that a filtered network can never produce, so a server the app had successfully located
        // rendered as an empty circle. Silence is not disagreement: when most of the pool could not
        // be reached at all and the one service that did answer stands uncontradicted, the answer
        // is real evidence about the address and thin evidence about the network. It is accepted,
        // and [ServerLocationVerdict.provisional] makes sure it is re-tested later.
        val silent = (providersQueried - usable.size).coerceAtLeast(0)
        return if (top.value == 1 && silent >= MIN_SILENT_WITNESSES_FOR_LONE_ACCEPT) top.key else ""
    }

    /** IPv6 literals must be bracketed when inserted into a URL path/authority. */
    private fun urlHost(ip: String): String =
        if (ip.contains(':') && !ip.startsWith('[')) "[$ip]" else ip

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
        // Auto-location runs on imported server names before VPN bring-up. Never reveal those
        // endpoint names through the phone's plaintext system resolver.
        EncryptedEndpointResolver.resolve(host)
            .filter { it is Inet4Address || it is Inet6Address }
            .map { it.hostAddress.orEmpty() }
            .filter { it.isNotBlank() }
    }
}
