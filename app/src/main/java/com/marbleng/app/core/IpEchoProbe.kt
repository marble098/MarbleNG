package com.marbleng.app.core

import com.marbleng.app.model.IpAddressBook
import com.marbleng.app.model.IpEgress
import com.marbleng.app.model.IpKind
import com.marbleng.app.model.IpScope
import java.net.HttpURLConnection
import java.net.URL

/**
 * MARBLE_IP_IDENTITY_V251 — the one place an address is asked for.
 *
 * The IP page needs the same question answered twice: *what address does the internet see*, once
 * through the tunnel and once straight off the device. Both answers come from a public echo service
 * that prints nothing but the literal, which is why no JSON is parsed here and why a wrong-looking
 * answer is rejected instead of printed: [IpAddressBook] must be able to recognise the literal as an
 * address of the family that was asked for, or the observation does not exist.
 *
 * It lives in `core` rather than in the repository for two reasons. The first is policy: the
 * repository's committed rule is that a *ping* may never dial SOCKS by hand (`RouteProbe` owns that
 * ladder), and keeping this probe in its own object keeps that rule literally true. The second is
 * testability: everything here is a pure function of the two transports it is handed, so a JVM test
 * can prove the rejection rules without a socket.
 */
object IpEchoProbe {
    const val MARKER: String = "MARBLE_IP_IDENTITY_V251"

    const val V4_HOST: String = "ipv4.icanhazip.com"
    const val V6_HOST: String = "ipv6.icanhazip.com"

    const val CONNECT_TIMEOUT_MS: Int = 5_000
    const val READ_TIMEOUT_MS: Int = 6_000
    const val MAX_BODY_BYTES: Int = 256

    /** The host that answers for a family, so the two planes cannot drift apart. */
    fun hostFor(family: IpKind): String = when (family) {
        IpKind.IPV6 -> V6_HOST
        else -> V4_HOST
    }

    /**
     * Read the literal out of whatever the service returned.
     *
     * A service may answer with a trailing newline, a `text/plain` banner, or — on a captured
     * network — an HTML error page that happens to start with digits. Only a first line that parses
     * as an address **of the requested family** survives; everything else is `null`, which the page
     * reads as "not discovered" instead of as an address.
     */
    fun literalOf(body: String, family: IpKind): String? {
        body.lineSequence().forEach { raw ->
            val literal = IpAddressBook.shorten(raw)
            if (literal.isEmpty()) return@forEach
            val kind = IpAddressBook.kindOf(literal)
            if (kind == IpKind.OTHER) return@forEach
            if (family != IpKind.OTHER && kind != family) return@forEach
            val scope = IpAddressBook.scopeOf(literal)
            // A private answer from a public echo service is a hijacked or captive response, not an
            // exit identity: the page must not print "10.0.0.7" as what the internet sees.
            val local = scope == IpScope.LOOPBACK || scope == IpScope.PRIVATE ||
                scope == IpScope.UNIQUE_LOCAL || scope == IpScope.LINK_LOCAL ||
                scope == IpScope.UNSPECIFIED
            if (local) return@forEach
            return literal
        }
        return null
    }

    /** An observation built from a body, or `null` when the body is not an address of that family. */
    fun observationOf(
        body: String,
        family: IpKind,
        viaTunnel: Boolean
    ): IpEgress? {
        val literal = literalOf(body, family) ?: return null
        return IpEgress(
            family = IpAddressBook.kindOf(literal),
            address = literal,
            viaTunnel = viaTunnel,
            global = IpAddressBook.scopeOf(literal) == IpScope.PUBLIC
        )
    }

    /**
     * The tunnel plane: the same question, asked through the local SOCKS port so the answer is the
     * exit the app is actually using. `port` is the app's own inbound; it is never a public one.
     */
    fun viaTunnel(port: Int, family: IpKind): String = runCatching {
        if (port !in 1..65535) return ""
        val response = SocksHttpClient.get(
            port = port,
            host = hostFor(family),
            path = "/",
            timeoutMs = READ_TIMEOUT_MS,
            maxBytes = MAX_BODY_BYTES
        )
        if (response.status in 200..299) String(response.body) else ""
    }.getOrDefault("")

    /** The bare-device plane: no tunnel, no proxy — what the phone's own route says. */
    fun direct(family: IpKind, userAgent: String = "MarbleNG"): String = runCatching {
        val connection = (URL("https://${hostFor(family)}/").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode !in 200..299) {
                ""
            } else {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault("")

    /**
     * One plane, both families. The v4 answer is the exit on every network this app runs on; the v6
     * answer is reported separately so a tunnel that silently drops IPv6 is visible on the page
     * instead of looking like a dual-stack exit.
     */
    fun observe(port: Int, viaTunnel: Boolean, userAgent: String = "MarbleNG"): List<IpEgress> =
        listOf(IpKind.IPV4, IpKind.IPV6).mapNotNull { family ->
            val body = if (viaTunnel) viaTunnel(port, family) else direct(family, userAgent)
            observationOf(body, family, viaTunnel)
        }
}
