package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for rank-pool security eligibility (MARBLE_SMART_RANK_V90).
 *
 * These lock in the removal of censorship-unsafe nodes before ranking: VLESS without TLS/REALITY
 * and VMess without forward secrecy must be hidden from the active rank pool (they are usually
 * WEAK, not INSECURE, so the gate must match scheme+security explicitly rather than rely on the
 * security level alone).
 */
class ProfileSecurityAuditorRankEligibilityTest {

    private fun profile(id: String, scheme: String, security: String, raw: String = "") = ProxyProfile(
        id = id,
        name = id,
        scheme = scheme,
        raw = raw,
        configJson = "",
        host = "h.example.com",
        port = 443,
        transport = "tcp",
        security = security
    )

    @Test
    fun vlessWithoutTlsRealityIsDeprecated() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "none"))
        assertFalse(eligibility.active)
        assertEquals("vless-without-tls-reality", eligibility.reason)
    }

    @Test
    fun vlessWithRealityIsActive() {
        assertTrue(ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "reality")).active)
    }

    @Test
    fun vlessWithTlsIsActive() {
        assertTrue(ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "tls")).active)
    }

    @Test
    fun vmessWithoutForwardSecrecyIsDeprecated() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(profile("a", "vmess", "none"))
        assertFalse(eligibility.active)
        assertEquals("vmess-without-forward-secrecy", eligibility.reason)
    }

    @Test
    fun legacyVmessAlterIdIsDeprecated() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(
            profile("a", "vmess", "tls", raw = "vmess://x?alterId=2")
        )
        assertFalse(eligibility.active)
        assertEquals("deprecated-cipher", eligibility.reason)
    }

    @Test
    fun partitionForRankSeparatesActiveFromDeprecated() {
        val (active, deprecated) = ProfileSecurityAuditor.partitionForRank(
            listOf(
                profile("good", "vless", "reality"),
                profile("plain-vless", "vless", "none"),
                profile("plain-vmess", "vmess", "none")
            )
        )
        assertEquals(listOf("good"), active.map { it.id })
        assertEquals(setOf("plain-vless", "plain-vmess"), deprecated.map { it.first.id }.toSet())
    }
}
