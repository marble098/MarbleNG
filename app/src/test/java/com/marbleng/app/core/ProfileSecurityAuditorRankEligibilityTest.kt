package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSecurityAuditorRankEligibilityTest {
    private val consented = AppSettings(allowUnencryptedPublicOutbound = true)
    private val refused = AppSettings(allowUnencryptedPublicOutbound = false)

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
    fun cleartextVlessIsRankableBecauseItIsConnectable() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "none"), consented)
        assertTrue(eligibility.active)

        assertEquals("unencrypted-outbound", eligibility.reason)
    }

    @Test
    fun cleartextVlessLeavesThePoolWhenConsentIsWithdrawn() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "none"), refused)
        assertFalse(eligibility.active)
        assertEquals("plaintext-prohibited", eligibility.reason)
    }

    @Test
    fun aPrivateCleartextNodeIsAlwaysRankable() {
        val lan = profile("a", "vless", "none").copy(host = "10.8.0.9")
        assertTrue(ProfileSecurityAuditor.rankEligibility(lan, refused).active)
    }

    @Test
    fun vlessWithRealityIsActive() {
        assertTrue(ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "reality"), refused).active)
    }

    @Test
    fun vlessWithTlsIsActive() {
        assertTrue(ProfileSecurityAuditor.rankEligibility(profile("a", "vless", "tls"), refused).active)
    }

    @Test
    fun vmessWithoutForwardSecrecyIsDeprecated() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(profile("a", "vmess", "none"), consented)
        assertFalse(eligibility.active)
        assertEquals("vmess-without-forward-secrecy", eligibility.reason)
    }

    @Test
    fun legacyVmessAlterIdIsDeprecated() {
        val eligibility = ProfileSecurityAuditor.rankEligibility(
            profile("a", "vmess", "tls", raw = "vmess://x?alterId=2"),
            consented
        )
        assertFalse(eligibility.active)
        assertEquals("deprecated-cipher", eligibility.reason)
    }

    @Test
    fun aTransportTheSelectedCoreDeletedIsHiddenWithItsEngineNamedReason() {
        val h2 = profile("a", "vless", "tls").copy(
            transport = "h2",
            configJson = JSONObject()
                .put(
                    "outbounds",
                    JSONArray().put(
                        JSONObject()
                            .put("tag", "proxy")
                            .put("protocol", "vless")
                            .put("settings", JSONObject().put("address", "h.example.com").put("port", 443))
                            .put("streamSettings", JSONObject().put("network", "http").put("security", "tls"))
                    )
                )
                .toString()
        )
        val eligibility = ProfileSecurityAuditor.rankEligibility(h2, consented)
        assertFalse(eligibility.active)
        assertEquals("core-gap", eligibility.reason)
    }

    @Test
    fun partitionForRankSeparatesActiveFromDeprecated() {
        val (active, deprecated) = ProfileSecurityAuditor.partitionForRank(
            listOf(
                profile("good", "vless", "reality"),
                profile("plain-vless", "vless", "none"),
                profile("plain-vmess", "vmess", "none")
            ),
            consented
        )
        assertEquals(listOf("good", "plain-vless"), active.map { it.id })
        assertEquals(setOf("plain-vmess"), deprecated.map { it.first.id }.toSet())

        val (refusedActive, refusedDeprecated) = ProfileSecurityAuditor.partitionForRank(
            listOf(
                profile("good", "vless", "reality"),
                profile("plain-vless", "vless", "none"),
                profile("plain-vmess", "vmess", "none")
            ),
            refused
        )
        assertEquals(listOf("good"), refusedActive.map { it.id })
        assertEquals(setOf("plain-vless", "plain-vmess"), refusedDeprecated.map { it.first.id }.toSet())
    }

    @Test
    fun theDefaultSettingsKeepTheWholeSubscriptionRankable() {
        val (active, deprecated) = ProfileSecurityAuditor.partitionForRank(
            listOf(profile("a", "vless", "none"), profile("b", "vless", "none"))
        )
        assertEquals(2, active.size)
        assertEquals(0, deprecated.size)
    }
}
