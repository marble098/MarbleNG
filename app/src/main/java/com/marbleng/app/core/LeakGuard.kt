package com.marbleng.app.core

import com.marbleng.app.net.PrivacyReport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class LeakGuard {
    enum class LeakSeverity {
        NONE,
        INFO,
        WARNING,
        CRITICAL
    }

    enum class LeakType {
        IP_UNDERLAY_MATCH,
        DNS_UNENCRYPTED,
        DNS_IRANIAN_RESOLVER,
        DNS_LEAK_VIA_TUNNEL,
        IPV6_LEAK,
        WEBRTC_LEAK,
        SPLIT_TUNNEL_BYPASS,
        DNS_CACHE_POISONED
    }

    data class LeakFinding(
        val type: LeakType,
        val severity: LeakSeverity,
        val detail: String,
        val detectedAt: Long = System.currentTimeMillis(),
        val resolverIp: String = "",
        val proxyIp: String = "",
        val underlayIp: String = ""
    )

    data class LeakAssessment(
        val overallSeverity: LeakSeverity,
        val findings: List<LeakFinding>,
        val lastVerifiedAt: Long,
        val ipVerified: Boolean,
        val dnsVerified: Boolean,
        val encryptionVerified: Boolean,
        val score: Int
    )

    private val findings = CopyOnWriteArrayList<LeakFinding>()
    private val lastIpVerification = AtomicLong(0)
    private val lastDnsVerification = AtomicLong(0)
    private val knownProxyIps = CopyOnWriteArrayList<String>()
    private val knownUnderlayIps = CopyOnWriteArrayList<String>()
    private val verifiedDnsResolvers = CopyOnWriteArrayList<String>()
    private val tunnelActive = AtomicReference(false)
    private val verificationCount = AtomicInteger(0)
    private val leakDetectedCount = AtomicInteger(0)

    private val IRANIAN_DNS_PREFIXES = setOf(
        "172.29.0.",
        "10.211.196.",
        "5.202.10.",
        "79.175.132.",
        "213.202.248.",
        "194.225.151.",
        "194.150.76."
    )

    private val ENCRYPTED_PROVIDERS = setOf(
        "cloudflare", "google", "quad9", "adguard",
        "nextdns", "opendns", "mullvad", "control d",
        "yandex", "cleanbrowsing"
    )

    private val verificationIntervalMs = 60_000L
    private val networkChangeGraceMs = 10_000L
    private val maxFindings = 50

    fun setKnownProxyIp(ip: String) {
        if (ip.isNotBlank() && !knownProxyIps.contains(ip)) {
            knownProxyIps.add(ip)
            while (knownProxyIps.size > 5) knownProxyIps.removeAt(0)
        }
    }

    fun setKnownUnderlayIp(ip: String) {
        if (ip.isNotBlank() && !knownUnderlayIps.contains(ip)) {
            knownUnderlayIps.add(ip)
            while (knownUnderlayIps.size > 3) knownUnderlayIps.removeAt(0)
        }
    }

    fun setTunnelActive(active: Boolean) {
        tunnelActive.set(active)
        if (!active) {
            lastIpVerification.set(0)
            lastDnsVerification.set(0)
        }
    }

    fun isVerificationDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!tunnelActive.get()) return false
        val lastIp = lastIpVerification.get()
        val lastDns = lastDnsVerification.get()
        return (nowMs - lastIp > verificationIntervalMs) || (nowMs - lastDns > verificationIntervalMs)
    }

    fun processReport(report: PrivacyReport, nowMs: Long = System.currentTimeMillis()): LeakAssessment {
        verificationCount.incrementAndGet()

        if (report.proxyIp.isNotBlank() || report.proxyIpv6.isNotBlank()) {
            lastIpVerification.set(nowMs)
            setKnownProxyIp(report.proxyIp)
            setKnownProxyIp(report.proxyIpv6)
        }
        if (report.underlayIp.isNotBlank()) setKnownUnderlayIp(report.underlayIp)
        if (report.underlayIpv6.isNotBlank()) setKnownUnderlayIp(report.underlayIpv6)

        val newFindings = mutableListOf<LeakFinding>()

        if (report.proxyIp.isNotBlank() && report.underlayIp.isNotBlank() &&
            report.proxyIp == report.underlayIp) {
            newFindings += LeakFinding(
                type = LeakType.IP_UNDERLAY_MATCH,
                severity = LeakSeverity.CRITICAL,
                detail = "CRITICAL: Proxy exit IP (${report.proxyIp}) matches physical underlay IP. " +
                    "All traffic is likely leaking outside the tunnel.",
                proxyIp = report.proxyIp,
                underlayIp = report.underlayIp
            )
            leakDetectedCount.incrementAndGet()
        }

        if (report.proxyIpv6.isNotBlank() && report.underlayIpv6.isNotBlank() &&
            report.proxyIpv6 == report.underlayIpv6) {
            newFindings += LeakFinding(
                type = LeakType.IPV6_LEAK,
                severity = LeakSeverity.CRITICAL,
                detail = "IPv6-only proxy egress matches the physical IPv6 egress; possible VPN bypass.",
                proxyIp = report.proxyIpv6,
                underlayIp = report.underlayIpv6
            )
            leakDetectedCount.incrementAndGet()
        }

        val dnsRows = report.dnsServers
        if (dnsRows.isNotBlank() && dnsRows != "inconclusive") {
            val isIranianDns = IRANIAN_DNS_PREFIXES.any { prefix ->
                dnsRows.contains(prefix)
            }
            if (isIranianDns) {
                newFindings += LeakFinding(
                    type = LeakType.DNS_IRANIAN_RESOLVER,
                    severity = LeakSeverity.CRITICAL,
                    detail = "CRITICAL: DNS queries are being resolved through an Iranian resolver. " +
                        "This reveals browsing targets to the ISP. DNS: $dnsRows",
                    resolverIp = dnsRows
                )
                leakDetectedCount.incrementAndGet()
            }
        }

        val dnsEncrypted = report.dnsServers.isNotBlank() &&
            ENCRYPTED_PROVIDERS.any { provider ->
                report.dnsServers.contains(provider, ignoreCase = true)
            }

        if (!dnsEncrypted && report.dnsServers.isNotBlank() && report.dnsServers != "inconclusive") {
            newFindings += LeakFinding(
                type = LeakType.DNS_UNENCRYPTED,
                severity = LeakSeverity.WARNING,
                detail = "DNS queries may not be using encrypted resolvers. " +
                    "Observed DNS: ${report.dnsServers}. " +
                    "Recommend enabling DoH/DoT in settings.",
                resolverIp = report.dnsServers
            )
        }

        if (dnsEncrypted) {
            lastDnsVerification.set(nowMs)
        }

        if (report.proxyIp.isBlank() && report.proxyIpv6.isBlank() && tunnelActive.get()) {
            newFindings += LeakFinding(
                type = LeakType.IP_UNDERLAY_MATCH,
                severity = LeakSeverity.WARNING,
                detail = "Could not verify proxy exit IP. The tunnel may not be routing traffic correctly.",
                proxyIp = ""
            )
        }

        findings.addAll(newFindings)
        while (findings.size > maxFindings) findings.removeAt(0)

        return computeAssessment(nowMs)
    }

    fun onNetworkChanged(previousProxyIp: String, newUnderlayIp: String): List<LeakFinding> {
        val newFindings = mutableListOf<LeakFinding>()

        if (previousProxyIp.isNotBlank() && newUnderlayIp == previousProxyIp) {
            newFindings += LeakFinding(
                type = LeakType.IP_UNDERLAY_MATCH,
                severity = LeakSeverity.WARNING,
                detail = "After network change, underlay IP (${newUnderlayIp}) matches previous proxy IP. " +
                    "Verify the tunnel has re-established on the new network.",
                proxyIp = previousProxyIp,
                underlayIp = newUnderlayIp
            )
        }

        findings.addAll(newFindings)

        lastIpVerification.set(0)
        lastDnsVerification.set(0)

        return newFindings
    }

    fun computeAssessment(nowMs: Long = System.currentTimeMillis()): LeakAssessment {
        val recentFindings = findings.filter { nowMs - it.detectedAt < 300_000 }

        val hasCritical = recentFindings.any { it.severity == LeakSeverity.CRITICAL }
        val hasWarning = recentFindings.any { it.severity == LeakSeverity.WARNING }

        val overallSeverity = when {
            hasCritical -> LeakSeverity.CRITICAL
            hasWarning -> LeakSeverity.WARNING
            recentFindings.any { it.severity == LeakSeverity.INFO } -> LeakSeverity.INFO
            else -> LeakSeverity.NONE
        }

        val ipVerified = lastIpVerification.get() > 0 && nowMs - lastIpVerification.get() < verificationIntervalMs * 2
        val dnsVerified = lastDnsVerification.get() > 0 && nowMs - lastDnsVerification.get() < verificationIntervalMs * 2
        val encryptionVerified = recentFindings.none { it.type == LeakType.DNS_UNENCRYPTED || it.type == LeakType.DNS_IRANIAN_RESOLVER }

        var score = 100
        recentFindings.forEach { finding ->
            score -= when (finding.severity) {
                LeakSeverity.CRITICAL -> 35
                LeakSeverity.WARNING -> 15
                LeakSeverity.INFO -> 5
                LeakSeverity.NONE -> 0
            }
        }

        if (!ipVerified) score -= 10
        if (!dnsVerified) score -= 10
        if (!encryptionVerified) score -= 15

        return LeakAssessment(
            overallSeverity = overallSeverity,
            findings = recentFindings,
            lastVerifiedAt = maxOf(lastIpVerification.get(), lastDnsVerification.get()),
            ipVerified = ipVerified,
            dnsVerified = dnsVerified,
            encryptionVerified = encryptionVerified,
            score = score.coerceIn(0, 100)
        )
    }

    fun snapshot(): Map<String, Any> = mapOf(
        "tunnelActive" to tunnelActive.get(),
        "findingsCount" to findings.size,
        "leakDetectedCount" to leakDetectedCount.get(),
        "verificationCount" to verificationCount.get(),
        "lastIpVerification" to lastIpVerification.get(),
        "lastDnsVerification" to lastDnsVerification.get(),
        "knownProxyIps" to knownProxyIps.toList(),
        "knownUnderlayIps" to knownUnderlayIps.toList()
    )

    fun clearFindings() {
        findings.clear()
    }
}
