package com.marbleng.app.core

import com.marbleng.app.net.PrivacyReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeakGuardIpv6Test {
    private fun report(v6Exit: String, v6Physical: String) = PrivacyReport(
        proxyIp = "203.0.113.20", underlayIp = "198.51.100.10",
        cloudflareLocation = "", dnsServers = "inconclusive", ipLeakScore = 100,
        dnsLeakScore = 60, overallScore = 84, healthy = false, note = "",
        proxyIpv6 = v6Exit, underlayIpv6 = v6Physical
    )

    @Test fun independentIpv6OnlyComparisonCatchesBypassMissedByIpv4Trace() {
        val assessment = LeakGuard().apply { setTunnelActive(true) }.processReport(
            report("2001:db8::20", "2001:db8::20")
        )
        assertEquals(LeakGuard.LeakSeverity.CRITICAL, assessment.overallSeverity)
        assertTrue(assessment.findings.any { it.type == LeakGuard.LeakType.IPV6_LEAK })
    }

    @Test fun absentIpv6ExitObservationIsInconclusiveNotProofOfBypass() {
        val assessment = LeakGuard().apply { setTunnelActive(true) }.processReport(
            report("", "2001:db8::20")
        )
        assertFalse(assessment.findings.any { it.type == LeakGuard.LeakType.IPV6_LEAK })
        val separated = LeakGuard().apply { setTunnelActive(true) }.processReport(
            report("2001:db8::42", "2001:db8::20")
        )
        assertFalse(separated.findings.any { it.type == LeakGuard.LeakType.IPV6_LEAK })
    }
}
