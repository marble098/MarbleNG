package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class IpFamilyPreference {
    IPV4_ONLY,

    DUAL,

    IPV4_FIRST,

    IPV6_FIRST,

    IPV6_ONLY
}

data class IpFamilyPlan(
    val preference: IpFamilyPreference,
    val endpointStrategy: String,
    val dnsQueryStrategy: String,
    val underlayHasIpv6: Boolean,
    val prioritizeIpv6: Boolean,
    val raceEnabled: Boolean,
    val tryDelayMs: Int,
    val maxConcurrentTry: Int,
    val blockIpv6Traffic: Boolean,
    val blockIpv4Traffic: Boolean,
    val reason: String
)

object AddressFamilyPolicy {
    const val MIN_TRY_DELAY_MS = 20
    const val MAX_TRY_DELAY_MS = 500
    const val MIN_CONCURRENT_TRY = 2
    const val MAX_CONCURRENT_TRY = 8

    private val raceableStrategies = setOf("ForceIP", "ForceIPv4v6", "ForceIPv6v4")

    private const val RESOLVE_POOL_SIZE = 4
    private const val RESOLVE_MIN_BUDGET_MS = 250
    private const val RESOLVE_MAX_BUDGET_MS = 30_000
    private val resolvePool by lazy {
        Executors.newFixedThreadPool(RESOLVE_POOL_SIZE) { runnable ->
            Thread(runnable, "marble-resolve").apply { isDaemon = true }
        }
    }

    val ENDPOINT_STRATEGIES = setOf(
        "AsIs", "UseIP", "UseIPv4", "UseIPv6", "UseIPv4v6", "UseIPv6v4",
        "ForceIP", "ForceIPv4", "ForceIPv6", "ForceIPv4v6", "ForceIPv6v4"
    )

    @Volatile
    private var underlayCacheMs = 0L

    @Volatile
    private var underlayCacheValue = false

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

    data class UnderlayCapabilities(
        val hasIpv6: Boolean,
        val hasIpv4: Boolean = true,
        val interfaceCount: Int = 0,
        val ipv6AddressCount: Int = 0,
        val hasNat64: Boolean = false,
        val mtuHint: Int = -1
    )

    fun probeUnderlay(nowMs: Long = System.currentTimeMillis(), ttlMs: Long = 2_000): UnderlayCapabilities {
        if (underlayCacheMs != 0L && nowMs - underlayCacheMs < ttlMs) {
            return UnderlayCapabilities(hasIpv6 = underlayCacheValue)
        }
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.filterNot { net ->
                    val name = net.name?.lowercase().orEmpty()
                    name.startsWith("tun") || name.startsWith("utun") || name.startsWith("ppp") || name.startsWith("tap")
                }?.toList() ?: emptyList()

            var v6Count = 0
            var hasV6 = false
            var hasNat64 = false
            for (iface in interfaces) {
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet6Address) {
                        if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress && !addr.isAnyLocalAddress && !isUniqueLocal(addr)) {
                            hasV6 = true
                            v6Count++

                            if (addr.address.size >= 4 && addr.address[0] == 0x00.toByte() && addr.address[1] == 0x64.toByte() &&
                                addr.address[2] == 0xFF.toByte() && addr.address[3] == 0x9B.toByte()) {
                                hasNat64 = true
                            }
                        }
                    }
                }
            }
            underlayCacheValue = hasV6
            underlayCacheMs = nowMs
            UnderlayCapabilities(
                hasIpv6 = hasV6,
                hasIpv4 = true,
                interfaceCount = interfaces.size,
                ipv6AddressCount = v6Count,
                hasNat64 = hasNat64
            )
        }.getOrDefault(UnderlayCapabilities(hasIpv6 = underlayCacheValue))
    }

    fun preference(
        settings: AppSettings,
        underlayHasIpv6: Boolean,
        importedStrategy: String = "",
        measuredV6Healthy: Boolean? = null
    ): IpFamilyPreference {
        val mode = when {
            settings.addressFamilyMode == AddressFamilyMode.SMART && !settings.ipv6Enabled ->
                AddressFamilyMode.FORCE_IPV4
            settings.addressFamilyMode == AddressFamilyMode.SMART && !settings.preferIpv6 ->
                AddressFamilyMode.PREFER_IPV4
            else -> settings.addressFamilyMode
        }
        when (mode) {
            AddressFamilyMode.FORCE_IPV4 -> return IpFamilyPreference.IPV4_ONLY
            AddressFamilyMode.FORCE_IPV6 -> return IpFamilyPreference.IPV6_ONLY
            AddressFamilyMode.PREFER_IPV4 -> return IpFamilyPreference.IPV4_FIRST
            AddressFamilyMode.PREFER_IPV6 -> return if (underlayHasIpv6) {
                IpFamilyPreference.IPV6_FIRST
            } else {
                IpFamilyPreference.IPV4_FIRST
            }
            AddressFamilyMode.SMART -> Unit
        }

        if (settings.dnsQueryStrategy.equals("UseIPv6", true)) {
            return if (underlayHasIpv6) IpFamilyPreference.IPV6_ONLY else IpFamilyPreference.DUAL
        }
        if (settings.dnsQueryStrategy.equals("UseIPv4", true)) return IpFamilyPreference.IPV4_ONLY

        val imported = importedStrategy.trim()
        if (
            imported.equals("ForceIPv4", true) || imported.equals("UseIPv4", true) ||
            imported.equals("ForceIP4", true) || imported.equals("UseIP4", true)
        ) return IpFamilyPreference.IPV4_ONLY

        val importsV6 = imported.equals("ForceIPv6", true) || imported.equals("UseIPv6", true)
        if (importsV6) {
            return if (underlayHasIpv6) IpFamilyPreference.IPV6_ONLY else IpFamilyPreference.IPV4_FIRST
        }

        if (!underlayHasIpv6) return IpFamilyPreference.DUAL

        val measuredUnhealthy = measuredV6Healthy == false || settings.measuredIpv6Unhealthy
        if (measuredUnhealthy) return IpFamilyPreference.DUAL
        return IpFamilyPreference.IPV6_FIRST
    }

    fun prioritizeIpv6(
        preference: IpFamilyPreference,
        underlayHasIpv6: Boolean,
        measuredV6Healthy: Boolean? = null
    ): Boolean = when (preference) {
        IpFamilyPreference.IPV4_ONLY, IpFamilyPreference.IPV4_FIRST -> false

        IpFamilyPreference.IPV6_ONLY, IpFamilyPreference.IPV6_FIRST -> underlayHasIpv6
        IpFamilyPreference.DUAL -> underlayHasIpv6 && measuredV6Healthy != false
    }

    fun canRace(
        settings: AppSettings,
        preference: IpFamilyPreference,
        tcpTransport: Boolean,
        chained: Boolean,
        dialerProxy: String
    ): Boolean {
        if (
            preference != IpFamilyPreference.DUAL &&
            preference != IpFamilyPreference.IPV4_FIRST &&
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

    fun endpointStrategy(
        preference: IpFamilyPreference,
        raceEnabled: Boolean,
        v6Usable: Boolean
    ): String = when (preference) {
        IpFamilyPreference.IPV4_ONLY -> "ForceIPv4"
        IpFamilyPreference.IPV6_ONLY -> "ForceIPv6"
        IpFamilyPreference.IPV4_FIRST -> if (raceEnabled) "ForceIP" else "ForceIPv4v6"
        IpFamilyPreference.IPV6_FIRST -> if (raceEnabled) "ForceIP" else "ForceIPv6v4"
        IpFamilyPreference.DUAL -> when {
            raceEnabled -> "ForceIP"
            v6Usable -> "ForceIPv6v4"
            else -> "ForceIPv4"
        }
    }

    fun dnsQueryStrategy(
        settings: AppSettings,
        preference: IpFamilyPreference
    ): String = when (preference) {
        IpFamilyPreference.IPV4_ONLY -> "UseIPv4"
        IpFamilyPreference.IPV6_ONLY -> "UseIPv6"

        IpFamilyPreference.IPV4_FIRST,
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
        measuredV6Healthy: Boolean? = null,
        link: LinkEvidence = LinkEvidence.UNKNOWN
    ): IpFamilyPlan {
        val measured = measuredV6Healthy ?: if (settings.measuredIpv6Unhealthy) false else null
        val preference = preference(settings, underlayHasIpv6, importedStrategy, measured)
        val prioritize = prioritizeIpv6(preference, underlayHasIpv6, measured)
        val race = canRace(settings, preference, tcpTransport, chained, dialerProxy)
        val strategy = endpointStrategy(preference, race, underlayHasIpv6 && prioritize)
        val armed = race && strategy in raceableStrategies
        val delay = if (armed) {
            LinkDeadlinePolicy.raceTryDelayMs(
                link,
                settings.happyEyeballsTryDelayMs.coerceIn(MIN_TRY_DELAY_MS, MAX_TRY_DELAY_MS)
            )
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

            blockIpv6Traffic = preference == IpFamilyPreference.IPV4_ONLY,
            blockIpv4Traffic = preference == IpFamilyPreference.IPV6_ONLY,
            reason = when (preference) {
                IpFamilyPreference.IPV4_ONLY -> "ipv4-only"
                IpFamilyPreference.IPV6_ONLY -> "ipv6-only"
                IpFamilyPreference.IPV4_FIRST -> if (delay > 0) "v4-first+race" else "v4-first"
                IpFamilyPreference.IPV6_FIRST -> if (delay > 0) "v6-first+race" else "v6-first"
                IpFamilyPreference.DUAL -> when {
                    delay > 0 -> "dual+race"
                    prioritize -> "dual-v6-first"
                    else -> "dual-v4-first"
                }
            }
        )
    }

    fun singBoxEndpointStrategy(plan: IpFamilyPlan): String = when (plan.endpointStrategy) {
        "ForceIPv4" -> "ipv4_only"
        "ForceIPv6" -> "ipv6_only"
        else -> if (plan.prioritizeIpv6) "prefer_ipv6" else "prefer_ipv4"
    }

    fun singBoxDestinationStrategy(settings: AppSettings): String = when (
        preference(settings, underlayHasIpv6 = true)
    ) {
        IpFamilyPreference.IPV4_ONLY -> "ipv4_only"
        IpFamilyPreference.IPV6_ONLY -> "ipv6_only"
        IpFamilyPreference.IPV4_FIRST -> "prefer_ipv4"
        IpFamilyPreference.IPV6_FIRST -> "prefer_ipv6"
        IpFamilyPreference.DUAL -> if (settings.measuredIpv6Unhealthy) "prefer_ipv4" else "prefer_ipv6"
    }

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
            IpFamilyPreference.IPV4_FIRST,
            IpFamilyPreference.IPV6_FIRST,
            IpFamilyPreference.DUAL -> if (prioritizeIpv6) v6 + v4 else v4 + v6
        }
    }

    fun isLiteralIp(host: String): Boolean {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isEmpty()) return false
        if (clean.contains(':')) return true
        val parts = clean.split('.')
        return parts.size == 4 && parts.all { octet ->
            octet.isNotEmpty() && octet.length <= 3 && octet.all { it.isDigit() } &&
                (octet.toIntOrNull() in 0..255)
        }
    }

    const val IPV6_LITERAL_DISABLED =
        "This server has only an IPv6 address. Enable IPv6 in Settings → DNS and use an IPv6-capable network."

    fun excludedIpv6Endpoint(host: String, settings: AppSettings): Boolean {
        if (preference(settings, underlayHasIpv6 = true) != IpFamilyPreference.IPV4_ONLY) return false
        val clean = host.trim().removeSurrounding("[", "]")
        if (!clean.contains(':')) return false
        return runCatching { InetAddress.getByName(clean) is Inet6Address }.getOrDefault(false)
    }

    const val IPV4_LITERAL_DISABLED =
        "This server has only an IPv4 address. Force IPv6 cannot dial it; select a server with an IPv6 address or choose Prefer IPv6."

    fun excludedIpv4Endpoint(host: String, settings: AppSettings): Boolean {
        if (!settings.strictAddressFamily) return false
        if (preference(settings, underlayHasIpv6 = true) != IpFamilyPreference.IPV6_ONLY) return false
        val clean = host.trim().removeSurrounding("[", "]")
        if (!isLiteralIp(clean)) return false

        val address = runCatching { InetAddress.getByName(clean) }.getOrNull()

        return address is Inet4Address && !address.isLoopbackAddress
    }

    fun resolveCandidates(
        host: String,
        plan: IpFamilyPlan,
        timeoutMs: Int = 0,
        resolver: (String) -> Array<InetAddress> = EncryptedEndpointResolver::resolve
    ): List<InetAddress> {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isBlank()) return emptyList()
        if (timeoutMs <= 0) {
            return runCatching {
                orderAddresses(resolver(clean).toList(), plan)
            }.getOrElse { emptyList() }
        }
        return orderAddresses(resolveWithBudget(clean, timeoutMs, resolver), plan)
    }

    fun resolveWithBudget(
        host: String,
        timeoutMs: Int,
        resolver: (String) -> Array<InetAddress> = EncryptedEndpointResolver::resolve
    ): List<InetAddress> {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isBlank()) return emptyList()
        if (isLiteralIp(clean)) {
            return runCatching { resolver(clean).toList() }.getOrElse { emptyList() }
        }
        val budgetMs = timeoutMs.coerceIn(RESOLVE_MIN_BUDGET_MS, RESOLVE_MAX_BUDGET_MS).toLong()
        val future = resolvePool.submit<List<InetAddress>> {
            runCatching { resolver(clean).toList() }.getOrElse { emptyList() }
        }
        return try {
            future.get(budgetMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            emptyList()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resolveFamilyWithBudget(host: String, timeoutMs: Int): List<InetAddress> {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isBlank()) return emptyList()
        if (isLiteralIp(clean)) {
            return runCatching { EncryptedEndpointResolver.resolveAll(clean, 500L).toList() }
                .getOrElse { emptyList() }
        }
        val budgetMs = timeoutMs.coerceIn(RESOLVE_MIN_BUDGET_MS, RESOLVE_MAX_BUDGET_MS).toLong()
        val future = resolvePool.submit<List<InetAddress>> {
            runCatching { EncryptedEndpointResolver.resolveAll(clean, budgetMs).toList() }
                .getOrElse { emptyList() }
        }
        return try {
            future.get(budgetMs + RESOLVE_GRACE_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            emptyList()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            future.cancel(true)
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private const val RESOLVE_GRACE_MS = 400L

    fun shouldCaptureIpv6(): Boolean = true

    fun selectAddress(
        addresses: List<InetAddress>,
        plan: IpFamilyPlan
    ): InetAddress? = orderAddresses(addresses, plan).firstOrNull()

    fun describe(plan: IpFamilyPlan): String = when (plan.preference) {
        IpFamilyPreference.IPV4_ONLY -> "IPv4 only — IPv6 traffic is blocked"
        IpFamilyPreference.IPV6_ONLY -> "IPv6 only — no IPv4 fallback"
        IpFamilyPreference.IPV4_FIRST -> if (plan.raceEnabled) {
            "IPv4 preferred, IPv6 raced after ${plan.tryDelayMs} ms"
        } else {
            "IPv4 preferred, sequential IPv6 fallback"
        }
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
