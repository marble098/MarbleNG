package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for profile preflight quarantine (MARBLE_PROFILE_QUARANTINE_V1).
 *
 * These lock in the fix for the "Turkey 4-All" class of structurally broken profiles: a VLESS/TLS
 * (or REALITY) config that fails Xray's config-load / xray-start validation must be quarantined
 * before Smart Rank so it can never poison ranking or user selection.
 */
class ProfilePreflightValidatorTest {

    private fun profile(configJson: String, id: String = "node-1") = ProxyProfile(
        id = id,
        name = id,
        scheme = "vless",
        raw = "vless://x",
        configJson = configJson,
        host = "example.com",
        port = 443,
        transport = "tcp",
        security = "reality"
    )

    private fun vlessConfig(security: String = "reality", serverName: String? = "example.com"): String =
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
                                    JSONObject()
                                        .put("address", "example.com")
                                        .put("port", 443)
                                        .put("users", JSONArray())
                                )
                            )
                        )
                        .put(
                            "streamSettings",
                            JSONObject()
                                .put("security", security)
                                .apply { if (serverName != null) put("tlsSettings", JSONObject().put("serverName", serverName)) }
                        )
                )
            )
            .toString()

    @Test
    fun validVlessRealityConfigIsValid() {
        val verdict = ProfilePreflightValidator.validate(profile(vlessConfig("reality", "example.com")))
        assertTrue(verdict.valid)
        assertEquals(ProfilePreflightValidator.Verdict.VALID, verdict.verdict)
    }

    @Test
    fun vlessTlsWithoutServerNameIsQuarantined() {
        // This is the "Turkey 4-All" family: VLESS/TLS missing its required serverName -> Xray
        // rejects at config-load, so the profile must be quarantined before ranking.
        val config = JSONObject()
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
                                    JSONObject().put("address", "example.com").put("port", 443)
                                )
                            )
                        )
                        .put(
                            "streamSettings",
                            JSONObject().put("security", "tls") // no tlsSettings.serverName
                        )
                )
            )
            .toString()
        val verdict = ProfilePreflightValidator.validate(profile(config))
        assertFalse(verdict.valid)
        assertEquals("vless-tls-missing-servername", verdict.reason)
    }

    @Test
    fun nonJsonConfigIsInvalid() {
        val verdict = ProfilePreflightValidator.validate(profile("this is not json {", id = "bad-json"))
        assertFalse(verdict.valid)
        assertEquals("invalid-json", verdict.reason)
    }

    @Test
    fun blankConfigIsInvalid() {
        val verdict = ProfilePreflightValidator.validate(profile("", id = "blank"))
        assertFalse(verdict.valid)
    }

    @Test
    fun missingOutboundsIsInvalid() {
        val verdict = ProfilePreflightValidator.validate(profile(JSONObject().put("log", JSONObject()).toString()))
        assertFalse(verdict.valid)
        assertEquals("no-outbounds", verdict.reason)
    }

    @Test
    fun noDialingOutboundIsInvalid() {
        val config = JSONObject()
            .put("outbounds", JSONArray().put(JSONObject().put("protocol", "freedom").put("tag", "freedom")))
            .toString()
        val verdict = ProfilePreflightValidator.validate(profile(config))
        assertFalse(verdict.valid)
        assertEquals("no-dialing-outbound", verdict.reason)
    }

    @Test
    fun invalidProfileIsExcludedFromTheRankPool() {
        val valid = profile(vlessConfig("reality", "example.com"), id = "healthy")
        val broken = profile(vlessConfig("tls", null), id = "turkey-4-all")

        val (validPool, invalid) = ProfilePreflightValidator.partition(listOf(valid, broken))
        assertEquals(listOf("healthy"), validPool.map { it.id })
        assertEquals(listOf("turkey-4-all"), invalid.map { it.first.id })
        assertEquals("vless-tls-missing-servername", invalid.single().second.reason)
    }
}
