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

    /**
     * MARBLE_IP_FAMILY_TRUTH_V197 — both families, each answered by independent providers.
     *
     * [resolve] races the providers and keeps the FIRST answer that comes back. That is the right
     * contract for "give me an address, quickly" and the wrong contract for "does this server have
     * an IPv6 address at all": the winner is chosen before anybody knows what it answered, so a
     * provider that replies NOERROR with an empty answer section (or an answer the parser rejects)
     * ends the race and the other providers — which may hold the AAAA — are cancelled. A scan built
     * on that reports a dual-stack server as IPv4-only, which is the single most expensive wrong
     * answer this app can give: it is the difference between a node Marble races over IPv6 and a
     * node it never tries.
     *
     * This entry point inverts the contract:
     *  - A (1) and AAAA (28) are queried **in parallel**, so one slow family can never eat the
     *    other family's budget (the old serial order asked AAAA first with a 3 s ceiling inside a
     *    2.5 s outer budget, which is how a whole resolution was abandoned as unresolvable);
     *  - each family asks up to [MAX_WITNESS_PROVIDERS] providers and keeps the union, so "no
     *    record" is only concluded when several independent resolvers say so;
     *  - the whole call stays inside [timeoutMs], and literal addresses still never touch a socket.
     */
    fun resolveAll(host: String, timeoutMs: Long = FAMILY_BUDGET_MS): Array<InetAddress> {
        val clean = host.trim().removeSurrounding("[", "]")
        if (clean.isBlank()) return emptyArray()
        if (AddressFamilyPolicy.isLiteralIp(clean)) {
            return runCatching { arrayOf(InetAddress.getByName(clean)) }.getOrDefault(emptyArray())
        }
        val budget = timeoutMs.coerceIn(500L, 15_000L)
        val deadline = System.currentTimeMillis() + budget
        val answers = linkedSetOf<InetAddress>()
        val jobs = listOf(28, 1).map { type ->
            workers.submit<Pair<Int, List<InetAddress>>> {
                type to queryFamily(clean, type, deadline)
            }
        }
        for (job in jobs) {
            val left = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
            val pair = try {
                job.get(left, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                // A cancelled sweep must stay cancelled: restore the flag and let the workers go.
                Thread.currentThread().interrupt()
                job.cancel(true)
                continue
            } catch (_: Throwable) {
                job.cancel(true)
                continue
            }
            val (type, found) = pair
            answers.addAll(found.filter { if (type == 28) it is Inet6Address else it is Inet4Address })
        }
        return answers.toTypedArray()
    }

    /**
     * One record type, answered by as many independent providers as the budget allows.
     *
     * A provider that answers with an empty answer section is not a failure and is not a witness:
     * it is one resolver's opinion that the record does not exist. Only providers that actually
     * returned addresses count towards [MIN_WITNESS_PROVIDERS], so the loop keeps asking while a
     * family still looks absent — and stops the moment two independent resolvers agree it exists.
     */
    private fun queryFamily(clean: String, type: Int, deadline: Long): List<InetAddress> {
        val wire = runCatching { DnsWireCodec.buildQuery(clean, type) }.getOrNull() ?: return emptyList()
        val found = linkedSetOf<InetAddress>()
        var witnesses = 0
        for (provider in providers().take(MAX_WITNESS_PROVIDERS)) {
            if (witnesses >= MIN_WITNESS_PROVIDERS && found.size >= MIN_WITNESS_ADDRESSES) break
            val left = deadline - System.currentTimeMillis()
            if (left <= 0L) break
            if (Thread.currentThread().isInterrupted) break
            val body = runCatching {
                pool.raceResolve(wire, listOf(provider), left.coerceAtMost(PER_WITNESS_TIMEOUT_MS))
            }.getOrNull()?.takeIf { it.success }?.body ?: continue
            val parsed = DnsWireCodec.parseAnswers(body)
                .filter { if (type == 28) it is Inet6Address else it is Inet4Address }
            if (parsed.isNotEmpty()) witnesses += 1
            found.addAll(parsed)
        }
        return found.toList()
    }

    /**
     * Longest a single provider may take inside [resolveAll]. Short enough that three witnesses
     * fit inside one family budget, long enough for a distant anycast node on a slow radio.
     */
    const val PER_WITNESS_TIMEOUT_MS: Long = 900L

    /** How many providers one family may ask, and how many must answer before it is believed. */
    const val MAX_WITNESS_PROVIDERS = 3
    const val MIN_WITNESS_PROVIDERS = 2
    const val MIN_WITNESS_ADDRESSES = 2

    /** The budget [resolveAll] spends on both families together. */
    const val FAMILY_BUDGET_MS: Long = 3_000L

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
