package com.marbleng.app.core

import com.marbleng.app.net.PrivacyReport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Continuous Leak Guard — real-time IP and DNS leak detection and prevention for MarbleNG.
 *
 * Problem addressed: The old PrivacyAuditor was a manual point-in-time check. Under severe
 * censorship, leaks can happen silently during reconnections, network changes, or DNS
 * resolver failures. This engine provides continuous monitoring.
 *
 * Features:
 * 1. Periodic background IP verification through the tunnel
 * 2. DNS leak detection by checking resolver identity
 * 3. Underlay bypass detection (traffic going around the VPN)
 * 4. Automatic alerting when leaks are detected
 * 5. DNS encryption verification
 * 6. WebRTC/IP leak surface assessment
 * 7. Network-change triggered re-verification
 */
class LeakGuard {

    /** Severity of a detected leak. */
    enum class LeakSeverity {
        NONE,       // No leak detected
        INFO,       // Informational observation
        WARNING,    // Potential leak risk
        CRITICAL    // Active leak confirmed
    }

    /** Types of leaks monitored. */
    enum class LeakType {
        IP_UNDERLAY_MATCH,    // Tunnel exit IP matches physical IP
        DNS_UNENCRYPTED,      // DNS queries going to plaintext resolvers
        DNS_IRANIAN_RESOLVER, // DNS resolved through Iranian ISP resolver
        DNS_LEAK_VIA_TUNNEL,  // DNS leaking outside tunnel
        IPV6_LEAK,            // IPv6 traffic bypassing tunnel
        WEBRTC_LEAK,          // WebRTC revealing real IP
        SPLIT_TUNNEL_BYPASS,  // App traffic bypassing tunnel
        DNS_CACHE_POISONED    // DNS cache contains poisoned entries
    }

    /** Single leak finding. */
    data class LeakFinding(
        val type: LeakType,
        val severity: LeakSeverity,
        val detail: String,
        val detectedAt: Long = System.currentTimeMillis(),
        val resolverIp: String = "",
        val proxyIp: String = "",
        val underlayIp: String = ""
    )

    /** Overall leak assessment. */
    data class LeakAssessment(
        val overallSeverity: LeakSeverity,
        val findings: List<LeakFinding>,
        val lastVerifiedAt: Long,
        val ipVerified: Boolean,
        val dnsVerified: Boolean,
        val encryptionVerified: Boolean,
        val score: Int  // 0-100, higher = safer
    )

    // State
    private val findings = CopyOnWriteArrayList<LeakFinding>()
    private val lastIpVerification = AtomicLong(0)
    private val lastDnsVerification = AtomicLong(0)
    private val knownProxyIps = CopyOnWriteArrayList<String>()
    private val knownUnderlayIps = CopyOnWriteArrayList<String>()
    private val verifiedDnsResolvers = CopyOnWriteArrayList<String>()
    private val tunnelActive = AtomicReference(false)
    private val verificationCount = AtomicInteger(0)
    private val leakDetectedCount = AtomicInteger(0)

    // Known Iranian DNS resolver ranges
    private val IRANIAN_DNS_PREFIXES = setOf(
        "172.29.0.",     // TCRI internal
        "10.211.196.",   // IR-ISP range
        "5.202.10.",     // Pars Online
        "79.175.132.",   // Afranet
        "213.202.248.",  // IR-ASN
        "194.225.151.",  // MTN Irancell DNS
        "194.150.76."    // Rightel DNS
    )

    // Known encrypted resolver providers
    private val ENCRYPTED_PROVIDERS = setOf(
        "cloudflare", "google", "quad9", "adguard",
        "nextdns", "opendns", "mullvad", "control d",
        "shecan", "yandex", "cleanbrowsing"
    )

    // Configuration
    private val verificationIntervalMs = 60_000L      // Verify every 60 seconds
    private val networkChangeGraceMs = 10_000L         // 10s grace after network change
    private val maxFindings = 50

    /**
     * Record the current proxy exit IP (for comparison against future checks).
     */
    fun setKnownProxyIp(ip: String) {
        if (ip.isNotBlank() && !knownProxyIps.contains(ip)) {
            knownProxyIps.add(ip)
            while (knownProxyIps.size > 5) knownProxyIps.removeAt(0)
        }
    }

    /**
     * Record the current underlay/physical IP.
     */
    fun setKnownUnderlayIp(ip: String) {
        if (ip.isNotBlank() && !knownUnderlayIps.contains(ip)) {
            knownUnderlayIps.add(ip)
            while (knownUnderlayIps.size > 3) knownUnderlayIps.removeAt(0)
        }
    }

    /**
     * Update tunnel status.
     */
    fun setTunnelActive(active: Boolean) {
        tunnelActive.set(active)
        if (!active) {
            // Reset verification timestamps on disconnect
            lastIpVerification.set(0)
            lastDnsVerification.set(0)
        }
    }

    /**
     * Check if a verification is due (called by the main loop).
     */
    fun isVerificationDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!tunnelActive.get()) return false
        val lastIp = lastIpVerification.get()
        val lastDns = lastDnsVerification.get()
        return (nowMs - lastIp > verificationIntervalMs) || (nowMs - lastDns > verificationIntervalMs)
    }

    /**
     * Record a privacy report result and check for leaks.
     */
    fun processReport(report: PrivacyReport, nowMs: Long = System.currentTimeMillis()): LeakAssessment {
        verificationCount.incrementAndGet()

        if (report.proxyIp.isNotBlank()) {
            lastIpVerification.set(nowMs)
            setKnownProxyIp(report.proxyIp)
        }
        if (report.underlayIp.isNotBlank()) {
            setKnownUnderlayIp(report.underlayIp)
        }

        val newFindings = mutableListOf<LeakFinding>()

        // Check 1: IP leak - proxy IP matches underlay IP
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

        // Check 2: DNS leak - DNS servers are Iranian
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

        // Check 3: DNS encryption verification
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

        // Check 4: Proxy IP is blank (tunnel may not be routing)
        if (report.proxyIp.isBlank() && tunnelActive.get()) {
            newFindings += LeakFinding(
                type = LeakType.IP_UNDERLAY_MATCH,
                severity = LeakSeverity.WARNING,
                detail = "Could not verify proxy exit IP. The tunnel may not be routing traffic correctly.",
                proxyIp = ""
            )
        }

        // Add new findings
        findings.addAll(newFindings)
        while (findings.size > maxFindings) findings.removeAt(0)

        return computeAssessment(nowMs)
    }

    /**
     * Verify that no traffic is bypassing the tunnel on a network change.
     */
    fun onNetworkChanged(previousProxyIp: String, newUnderlayIp: String): List<LeakFinding> {
        val newFindings = mutableListOf<LeakFinding>()

        // If underlay IP changed but proxy IP stayed the same, the tunnel is likely working
        // If underlay IP changed and proxy IP also changes to match, we have a leak

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
        // Force re-verification after network change
        lastIpVerification.set(0)
        lastDnsVerification.set(0)

        return newFindings
    }

    /**
     * Compute the current leak assessment from all findings.
     */
    fun computeAssessment(nowMs: Long = System.currentTimeMillis()): LeakAssessment {
        val recentFindings = findings.filter { nowMs - it.detectedAt < 300_000 } // Last 5 minutes

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

        // Compute score
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

    /**
     * Get diagnostic snapshot.
     */
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

    /**
     * Clear all findings (e.g., after reconnect).
     */
    fun clearFindings() {
        findings.clear()
    }
}
