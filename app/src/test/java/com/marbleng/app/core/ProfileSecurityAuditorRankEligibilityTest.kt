package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for rank-pool security eligibility (MARBLE_SMART_RANK_V90, amended by
 * MARBLE_CORE_CONFIG_SUPERSET_V165).
 *
 * VMess without forward secrecy stays hidden from the active rank pool: it is usually WEAK rather
 * than INSECURE, so the gate matches scheme+security explicitly instead of trusting the score.
 *
 * VLESS without TLS does **not** work that way any more, and this file is where that decision is
 * pinned. The rank gate used to keep its own copy of "cleartext VLESS is deprecated", which — on top
 * of the connect path's own copy of Xray's plaintext rule — removed all 42 nodes of a cleartext
 * subscription from ranking while the log said nothing but `rank-deprecated-hidden`. Rank now asks
 * the same authority the tunnel asks: a node the selected core can dial is rankable (labelled, via
 * [ProfileSecurityAuditor.assess], as unencrypted), and a node it cannot load is hidden with the
 * reason that names the other core. Withdrawing consent — Settings → Engine → Dial unencrypted nodes
 * — is what puts a cleartext node back out of the pool, on both engines.
 */
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
        // The reason is not "active": it is the fact the Servers row and Bug Finder report from.
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
        // `h2` is not a preference and not a security judgement: the pinned Xray's
        // `TransportProtocol.Build` answers it with PrintRemovedFeatureError, so a probe against it
        // would spend its whole timeout on a config that cannot load.
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
        // The shipped default is "usable": refusing an entire cleartext subscription is what this
        // test exists to make impossible again.
        val (active, deprecated) = ProfileSecurityAuditor.partitionForRank(
            listOf(profile("a", "vless", "none"), profile("b", "vless", "none"))
        )
        assertEquals(2, active.size)
        assertEquals(0, deprecated.size)
    }
}
