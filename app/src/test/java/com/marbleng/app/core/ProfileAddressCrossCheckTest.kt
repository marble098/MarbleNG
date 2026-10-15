package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAddressCrossCheckTest {
    private fun profile(
        id: String,
        configJson: String,
        host: String = "",
        port: Int = 0
    ) = ProxyProfile(
        id = id,
        name = id,
        scheme = "vless",
        raw = "",
        configJson = configJson,
        host = host,
        port = port,
        transport = "tcp",
        security = "reality",
        subscriptionId = "sub-1",
        subscriptionName = "Sub"
    )

    private fun vlessConfig(address: String): String =
        JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vless")
                        .put("tag", "proxy")
                        .put(
                            "settings",
                            JSONObject().put(
                                "vnext",
                                JSONArray().put(
                                    JSONObject().put("address", address).put("port", 443)
                                )
                            )
                        )
                        .put(
                            "streamSettings",
                            JSONObject()
                                .put("security", "reality")
                                .put("realitySettings", JSONObject().put("serverName", "example.com"))
                        )
                )
            )
            .toString()

    @Test
    fun thirteenOfFourteenMissingAddressesAreClassifiedPrecisely() {
        val nodes = (1..14).map { i ->
            val config = if (i == 14) vlessConfig("node$i.example.com") else vlessConfig("")
            profile("node-$i", config, host = "node$i.example.com", port = 443)
        }
        val sources = ProfileAddressCrossCheck.CrossCheckSources(
            freshSubscriptionProfiles = nodes,
            freshSubscriptionRaw = ""
        )

        val (valid, invalid) = ProfilePreflightValidator.partition(nodes, sources)

        assertEquals(1, valid.size)
        assertEquals(13, invalid.size)
        val reasons = invalid.map { it.second.reason }.toSet()
        assertFalse("blanket missing-address must never appear", reasons.contains("missing-address"))
        assertTrue(
            "address present in local cache + subscription but not usable in config",
            reasons.contains("address-resolved-but-invalid")
        )
    }

    @Test
    fun majorityQuarantineStopsRankInsteadOfFinishingEmpty() {
        assertTrue(ProfilePreflightValidator.isMajorityQuarantined(13, 14))
        assertTrue(ProfilePreflightValidator.isMajorityQuarantined(8, 14))
        assertFalse(ProfilePreflightValidator.isMajorityQuarantined(7, 14))
        assertFalse("zero candidates is not a majority quarantine", ProfilePreflightValidator.isMajorityQuarantined(0, 0))
    }

    @Test
    fun staleLocalCopyIsClassifiedAsStaleSubscription() {
        val config = vlessConfig("")
        val node = profile("stale-node", config, host = "stale.example.com", port = 443)
        val result = ProfileAddressCrossCheck.crossCheck(
            node, config, ProfileAddressCrossCheck.CrossCheckSources()
        )
        assertEquals(ProfileAddressCrossCheck.FailureReason.STALE_SUBSCRIPTION, result.failure)
    }

    @Test
    fun malformedConfigIsClassifiedAsMalformedConfig() {
        val node = profile("malformed", "{ this is not json")
        val result = ProfileAddressCrossCheck.crossCheck(
            node, node.configJson, ProfileAddressCrossCheck.CrossCheckSources()
        )
        assertEquals(ProfileAddressCrossCheck.FailureReason.MALFORMED_CONFIG, result.failure)
    }

    @Test
    fun trulyMissingAddressIsClassifiedAsMissingAddress() {
        val node = profile("gone", vlessConfig(""), host = "", port = 0)
        val result = ProfileAddressCrossCheck.crossCheck(
            node, node.configJson, ProfileAddressCrossCheck.CrossCheckSources()
        )
        assertEquals(ProfileAddressCrossCheck.FailureReason.MISSING_ADDRESS, result.failure)
    }

    @Test
    fun oneLineMessageIsUserReadable() {
        val node = profile("gone", vlessConfig(""), host = "", port = 0)
        val result = ProfileAddressCrossCheck.crossCheck(
            node, node.configJson, ProfileAddressCrossCheck.CrossCheckSources()
        )
        val line = ProfileAddressCrossCheck.oneLineMessage("gone", result)
        assertTrue(line.contains("gone"))
        assertTrue(line.contains("no server address"))
    }
}
