package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * MARBLE_ADDRESS_FAMILY_POLICY_V65
 *
 * How the app decides between IPv4 and IPv6, in exactly one place.
 *
 * Marble already had IPv6 switches — `ipv6Enabled`, `preferIpv6`, `dnsQueryStrategy`,
 * `adaptiveDualStackEnabled` — and every subsystem read them differently: the Xray `sockopt`
 * writer, the DNS writer, the ranking config, the Kotlin latency probes, the SOCKS prober and the
 * SSH bridge. The visible bug came from the first two. With the default profile the hardener wrote
 * `sockopt.domainStrategy = "ForceIP"` and no `happyEyeballs` block, and Xray reads that as
 * "resolve both families, then pick one **at random**": its Happy Eyeballs race is skipped unless
 * `tryDelayMs` and `maxConcurrentTry` are non-zero, and the engine's own default for `tryDelayMs`
 * is 0. A fragment/UDP/chained outbound sets `dialerProxy` or is not TCP, so it never races either.
 * Enabling IPv6 therefore changed nothing on the wire — which is also why leak checks that only
 * looked at IPv4 kept passing while the IPv6 path was quietly unused.
 *
 * This policy guarantees three things for every path:
 *  - "IPv6 off" is enforced end to end: endpoint resolution, DNS record selection, a `::/0` block
 *    rule, and no v6 literal is ever handed to a SOCKS/UDP prober;
 *  - "IPv6 on" actually uses it: addresses are ordered v6-first whenever the underlay has a global
 *    IPv6 address, and the choice is never left to chance;
 *  - every preference has an explicit fallback (`...v4`), so a blackholed v6 path degrades into a
 *    slightly slower connect instead of a dead tunnel.
 *
 * The emitted strings are exactly the ones Xray's `infra/conf` accepts (`forceipv4`, `forceipv6`,
 * `forceipv6v4`, `forceip` for `sockopt.domainStrategy`; `useipv4`, `useipv6`, `useip` for
 * `dns.queryStrategy`), so a value can never be silently dropped by the engine.
 */
enum class IpFamilyPreference {
    /** The user turned IPv6 off: no v6 may be resolved, dialled or routed. */
    IPV4_ONLY,

    /** Both families are usable; order them by measurement and race when racing is possible. */
    DUAL,

    /** IPv6 is the intended path, IPv4 stays the safety net. */
    IPV6_FIRST,

    /** IPv6 only: a missing AAAA record must surface as a failure, not a silent v4 fallback. */
    IPV6_ONLY
}

/**
 * The concrete knobs one connection path must apply. Plain values on purpose: the same plan drives
 * the Xray JSON, the JVM probers and the diagnostics line, and it stays unit-testable without
 * Android's `org.json` stubs.
 */
data class IpFamilyPlan(
    val preference: IpFamilyPreference,
    /** Value for `sockopt.domainStrategy` / WireGuard peer `domainStrategy`. */
    val endpointStrategy: String,
    /** Value for `dns.queryStrategy` (top level and every server entry). */
    val dnsQueryStrategy: String,
    /** Whether the current underlay can carry IPv6 at all. */
    val underlayHasIpv6: Boolean,
    /** RFC 8305 destination order, also used by the JVM probers. */
    val prioritizeIpv6: Boolean,
    /** True when Xray's Happy Eyeballs race is actually armed (both families + non-zero delay). */
    val raceEnabled: Boolean,
    val tryDelayMs: Int,
    val maxConcurrentTry: Int,
    /** True when `::/0` must be routed to `block` so the OS cannot bypass the tunnel over v6. */
    val blockIpv6Traffic: Boolean,
    /** Short, stable diagnostic string: `ipv4-only`, `v6-first+race`, ... */
    val reason: String
)

object AddressFamilyPolicy {
    /**
     * Xray skips the Happy Eyeballs race when `tryDelayMs` or `maxConcurrentTry` is zero, so an
     * armed race always gets a non-zero, bounded delay.
     */
    const val MIN_TRY_DELAY_MS = 20
    const val MAX_TRY_DELAY_MS = 500
    const val MIN_CONCURRENT_TRY = 2
    const val MAX_CONCURRENT_TRY = 8

    /** Strategies that resolve both families, i.e. the only ones a race can order. */
    private val raceableStrategies = setOf("ForceIP", "ForceIPv4v6", "ForceIPv6v4")

    /**
     * Every `sockopt.domainStrategy` value the engine accepts for endpoint resolution. Anything
     * outside this set is a hard config error in Xray, so Marble never emits it and `verify()` can
     * reject a regression instead of letting the engine silently ignore the block.
     */
    val ENDPOINT_STRATEGIES = setOf(
        "AsIs", "UseIP", "UseIPv4", "UseIPv6", "UseIPv4v6", "UseIPv6v4",
        "ForceIP", "ForceIPv4", "ForceIPv6", "ForceIPv4v6", "ForceIPv6v4"
    )

    @Volatile
    private var underlayCacheMs = 0L

    @Volatile
    private var underlayCacheValue = false

    /**
     * A global (routable, non-ULA, non-link-local) IPv6 address on a real interface. The tunnel's
     * own `fc00::/7` address is deliberately excluded: it proves the VPN exists, not that the
     * network can carry IPv6 towards the node.
     */
    fun underlayHasIpv6(nowMs: Long = System.currentTimeMillis(), ttlMs: Long = 2_000): Boolean {
        if (underlayCacheMs != 0L && nowMs - underlayCacheMs < ttlMs) return underlayCacheValue
        val value = runCatching {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.filterNot { network ->
                    val name = network.name?.lowercase().orEmpty()
                    name.startsWith("tun") || name.startsWith("utun") ||
                        name.startsWith("ppp") || name.startsWith("tap")
                }
                ?.flatMap { network -> network.inetAddresses.toList().asSequence() }
                ?.filterIsInstance<Inet6Address>()
                ?.any { address ->
                    !address.isLoopbackAddress && !address.isLinkLocalAddress &&
                        !address.isAnyLocalAddress && !isUniqueLocal(address)
                }
                ?: false
        }.getOrDefault(false)
        underlayCacheValue = value
        underlayCacheMs = nowMs
        return value
    }

    private fun isUniqueLocal(address: Inet6Address): Boolean {
        val first = address.address?.firstOrNull()?.toInt()?.and(0xFF) ?: return false
        return first in 0xFC..0xFD
    }

    /**
     * Resolve the user's intent for one outbound.
     *
     * @param importedStrategy `sockopt.domainStrategy` the node config already carried. A node that
     *   pins itself to IPv4 stays pinned: a global "IPv6 on" must never dial an endpoint that has
     *   no AAAA record and then sit in a connect timeout.
     * @param measuredV6Healthy the per-node verdict from [SmartIpRacePolicy]; `null` means Marble
     *   has no measurement yet and keeps the automatic behaviour.
     */
    fun preference(
        settings: AppSettings,
        underlayHasIpv6: Boolean,
        importedStrategy: String = "",
        measuredV6Healthy: Boolean? = null
    ): IpFamilyPreference {
        if (!settings.ipv6Enabled || settings.dnsQueryStrategy.equals("UseIPv4", true)) {
            return IpFamilyPreference.IPV4_ONLY
        }

        val imported = importedStrategy.trim()
        if (
            imported.equals("ForceIPv4", true) || imported.equals("UseIPv4", true) ||
            imported.equals("ForceIP4", true) || imported.equals("UseIP4", true)
        ) {
            return IpFamilyPreference.IPV4_ONLY
        }

        val importsV6 = imported.equals("ForceIPv6", true) || imported.equals("UseIPv6", true)
        if (settings.dnsQueryStrategy.equals("UseIPv6", true) || importsV6) {
            // Demanding v6-only on a network that cannot carry it would fail closed and read as a
            // dead node, so the strict mode is honoured only on top of a real v6 underlay.
            return if (underlayHasIpv6) IpFamilyPreference.IPV6_ONLY else IpFamilyPreference.DUAL
        }

        // Turning IPv6 on with a v6-capable underlay means "use it": the explicit Prefer IPv6 switch
        // then only decides how hard the fallback is. Without this, the default config resolved both
        // families and let the engine pick one at random — the exact behaviour this file removes.
        val wantsV6 = settings.preferIpv6 || underlayHasIpv6
        if (!wantsV6) return IpFamilyPreference.DUAL
        if (measuredV6Healthy == false && !settings.preferIpv6) return IpFamilyPreference.DUAL
        return IpFamilyPreference.IPV6_FIRST
    }

    fun prioritizeIpv6(
        preference: IpFamilyPreference,
        underlayHasIpv6: Boolean,
        measuredV6Healthy: Boolean? = null
    ): Boolean = when (preference) {
        IpFamilyPreference.IPV4_ONLY -> false
        IpFamilyPreference.IPV6_ONLY, IpFamilyPreference.IPV6_FIRST -> true
        IpFamilyPreference.DUAL -> underlayHasIpv6 && measuredV6Healthy != false
    }

    /**
     * A race is only possible on a TCP path that is not wrapped inside another dialer, with racing
     * enabled and a usable delay budget.
     */
    fun canRace(
        settings: AppSettings,
        preference: IpFamilyPreference,
        tcpTransport: Boolean,
        chained: Boolean,
        dialerProxy: String
    ): Boolean {
        // A v4-only or v6-only plan has one family to dial, so there is nothing to race; a UDP or
        // dialer-wrapped path is decided inside Xray's own dialer and never reaches the race.
        if (
            preference != IpFamilyPreference.DUAL &&
            preference != IpFamilyPreference.IPV6_FIRST
        ) {
            return false
        }
        return settings.adaptiveDualStackEnabled &&
            settings.happyEyeballsTryDelayMs > 0 &&
            tcpTransport &&
            !chained &&
            dialerProxy.isBlank()
    }

    /**
     * The endpoint strategy is chosen together with the race, never separately: `ForceIP` only
     * makes sense when Xray is armed to order the answers, and a path that cannot race must get a
     * deterministic order instead of a coin flip.
     */
    fun endpointStrategy(
        preference: IpFamilyPreference,
        raceEnabled: Boolean,
        v6Usable: Boolean
    ): String = when (preference) {
        IpFamilyPreference.IPV4_ONLY -> "ForceIPv4"
        IpFamilyPreference.IPV6_ONLY -> "ForceIPv6"
        IpFamilyPreference.IPV6_FIRST -> if (raceEnabled) "ForceIP" else "ForceIPv6v4"
        IpFamilyPreference.DUAL -> when {
            raceEnabled -> "ForceIP"
            v6Usable -> "ForceIPv6v4"
            else -> "ForceIPv4"
        }
    }

    /**
     * DNS record selection. Xray's DNS module only accepts `UseIP` (A + AAAA), `UseIPv4` and
     * `UseIPv6`; anything else is a hard config error, so an unknown stored value is normalised
     * instead of forwarded to the engine.
     */
    fun dnsQueryStrategy(
        settings: AppSettings,
        preference: IpFamilyPreference
    ): String = when (preference) {
        IpFamilyPreference.IPV4_ONLY -> "UseIPv4"
        IpFamilyPreference.IPV6_ONLY -> "UseIPv6"
        // v6-first still needs A records: the fallback is part of the promise.
        IpFamilyPreference.IPV6_FIRST,
        IpFamilyPreference.DUAL -> "UseIP"
    }

    fun plan(
        settings: AppSettings = AppSettings(),
        underlayHasIpv6: Boolean = underlayHasIpv6(),
        tcpTransport: Boolean = true,
        chained: Boolean = false,
        dialerProxy: String = "",
        importedStrategy: String = "",
        measuredV6Healthy: Boolean? = null
    ): IpFamilyPlan {
        val preference = preference(settings, underlayHasIpv6, importedStrategy, measuredV6Healthy)
        val prioritize = prioritizeIpv6(preference, underlayHasIpv6, measuredV6Healthy)
        val race = canRace(settings, preference, tcpTransport, chained, dialerProxy)
        val strategy = endpointStrategy(preference, race, underlayHasIpv6 && prioritize)
        val armed = race && strategy in raceableStrategies
        val delay = if (armed) {
            settings.happyEyeballsTryDelayMs.coerceIn(MIN_TRY_DELAY_MS, MAX_TRY_DELAY_MS)
        } else {
            0
        }
        return IpFamilyPlan(
            preference = preference,
            endpointStrategy = strategy,
            dnsQueryStrategy = dnsQueryStrategy(settings, preference),
            underlayHasIpv6 = underlayHasIpv6,
            prioritizeIpv6 = prioritize,
            raceEnabled = armed,
            tryDelayMs = delay,
            maxConcurrentTry = settings.happyEyeballsMaxConcurrent
                .coerceIn(MIN_CONCURRENT_TRY, MAX_CONCURRENT_TRY),
            // Only the user's own switch may drop IPv6 app traffic: a node that pins itself to IPv4,
            // or a measured plan that prefers A records, must not black-hole the rest of the tunnel.
            blockIpv6Traffic = !settings.ipv6Enabled,
            reason = when (preference) {
                IpFamilyPreference.IPV4_ONLY -> "ipv4-only"
                IpFamilyPreference.IPV6_ONLY -> "ipv6-only"
                IpFamilyPreference.IPV6_FIRST -> if (delay > 0) "v6-first+race" else "v6-first"
                IpFamilyPreference.DUAL -> when {
                    delay > 0 -> "dual+race"
                    prioritize -> "dual-v6-first"
                    else -> "dual-v4-first"
                }
            }
        )
    }

    /**
     * Order resolved addresses the way RFC 8305 does, for the connection paths Marble measures in
     * Kotlin itself (latency tests, route probes, SOCKS and SSH handshakes). A single answer is
     * returned untouched; a family the user excluded is dropped rather than tried and timed out.
     */
    fun orderAddresses(
        addresses: List<InetAddress>,
        plan: IpFamilyPlan
    ): List<InetAddress> = orderAddresses(addresses, plan.preference, plan.prioritizeIpv6)

    fun orderAddresses(
        addresses: List<InetAddress>,
        preference: IpFamilyPreference,
        prioritizeIpv6: Boolean = true
    ): List<InetAddress> {
        val v6 = addresses.filterIsInstance<Inet6Address>()
        val v4 = addresses.filterIsInstance<Inet4Address>()
        return when (preference) {
            IpFamilyPreference.IPV4_ONLY -> v4
            IpFamilyPreference.IPV6_ONLY -> v6
            IpFamilyPreference.IPV6_FIRST,
            IpFamilyPreference.DUAL -> if (prioritizeIpv6) v6 + v4 else v4 + v6
        }
    }

    /**
     * Resolve a node hostname into the addresses a prober should try, in the order the tunnel itself
     * will use. Literal addresses never touch a resolver, so a ping can never be blamed on DNS, and
     * `getAllByName` order (which is what `Socket.connect(host, port)` uses) is deliberately not
     * trusted: on Android it returns whatever the system resolver happened to answer first.
     */
    fun resolveCandidates(
        host: String,
        plan: IpFamilyPlan,
        resolver: (String) -> Array<InetAddress> = InetAddress::getAllByName
    ): List<InetAddress> {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isBlank()) return emptyList()
        return runCatching {
            orderAddresses(resolver(clean).toList(), plan)
        }.getOrElse { emptyList() }
    }

    /** Convenience for the JVM probers: the first usable address under the plan. */
    fun selectAddress(
        addresses: List<InetAddress>,
        plan: IpFamilyPlan
    ): InetAddress? = orderAddresses(addresses, plan).firstOrNull()

    fun describe(plan: IpFamilyPlan): String = when (plan.preference) {
        IpFamilyPreference.IPV4_ONLY -> "IPv4 only — IPv6 traffic is blocked"
        IpFamilyPreference.IPV6_ONLY -> "IPv6 only — no IPv4 fallback"
        IpFamilyPreference.IPV6_FIRST -> if (plan.raceEnabled) {
            "IPv6 preferred, IPv4 raced after ${plan.tryDelayMs} ms"
        } else {
            "IPv6 preferred, sequential IPv4 fallback"
        }
        IpFamilyPreference.DUAL -> when {
            plan.raceEnabled -> "Dual stack, fastest answer wins"
            plan.prioritizeIpv6 -> "Dual stack, IPv6 first"
            else -> "Dual stack, IPv4 first"
        }
    }
}
