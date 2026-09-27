package com.marbleng.app.core

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Direct hostname lookup for endpoint probes and management diagnostics. The phone's resolver is
 * NOT a fallback: a node name sent through netd before the tunnel starts is a DNS leak. DoH is
 * sent to IP-literal HTTPS endpoints (no bootstrap lookup), with one RFC 8484 question each for
 * A and AAAA. Failure returns no addresses; callers must never retry through OS DNS.
 *
 * This is intentionally separate from the core's browsing DNS: browser names use encrypted
 * resolvers through the selected proxy, while a node hostname must resolve BEFORE that proxy
 * can be used. A query to a DoH provider exposes only the node hostname to that provider, not
 * to the local ISP. Numeric endpoints are parsed without network access.
 */
object EncryptedEndpointResolver {
    private val workers = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "marble-endpoint-doh").apply { isDaemon = true }
    }
    private val pool = DohResolverPool(HttpUrlConnectionDohTransport(), workers, 3_000)

    private fun providers(): List<DohResolverPool.Provider> {
        val v4 = listOf(
            DohResolverPool.Provider("cloudflare-v4", "https://1.1.1.1/dns-query"),
            DohResolverPool.Provider("google-v4", "https://8.8.8.8/dns-query"),
            DohResolverPool.Provider("quad9-v4", "https://9.9.9.9/dns-query")
        )
        val v6 = listOf(
            DohResolverPool.Provider("cloudflare-v6", "https://[2606:4700:4700::1111]/dns-query"),
            DohResolverPool.Provider("google-v6", "https://[2001:4860:4860::8888]/dns-query")
        )
        return if (AddressFamilyPolicy.underlayHasIpv6()) v6 + v4 else v4
    }

    fun resolve(host: String): Array<InetAddress> = resolveWith(host) { wire ->
        runCatching { pool.raceResolve(wire, providers(), 2_500) }
            .getOrNull()?.takeIf { it.success }?.body
    }

    /** Query seam for JVM regressions: no socket, Android class, or real DoH provider is needed. */
    internal fun resolveWith(host: String, query: (ByteArray) -> ByteArray?): Array<InetAddress> {
        val clean = host.trim().removeSurrounding("[", "]")
        if (clean.isBlank()) return emptyArray()
        if (AddressFamilyPolicy.isLiteralIp(clean)) {
            return runCatching { arrayOf(InetAddress.getByName(clean)) }.getOrDefault(emptyArray())
        }
        val answers = linkedSetOf<InetAddress>()
        // AAAA first so an IPv6-only node can be measured even when A is absent or poisoned.
        for (type in listOf(28, 1)) {
            if (Thread.currentThread().isInterrupted) break
            val response = runCatching { query(DnsWireCodec.buildQuery(clean, type)) }.getOrNull()
                ?: continue
            DnsWireCodec.parseAnswers(response).filterTo(answers) { address ->
                if (type == 28) address is Inet6Address else address is Inet4Address
            }
        }
        return answers.toTypedArray()
    }
}
