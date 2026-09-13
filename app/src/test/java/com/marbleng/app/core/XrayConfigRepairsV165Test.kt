package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_CONFIG_SUPERSET_V165 — the repairs that turn "Xray rejected the generated
 * configuration" into a connection.
 *
 * Every one of these is a shape a real panel, a real exporter or a real older Marble produced, and
 * every one of them is a *fatal load error* on the pinned core rather than something it can be
 * tolerant about. The pass is deliberately boring: it rewrites only what the document already says
 * (a `proxySettings` tag is a dialer; a `tlsSettings` block is TLS; a quoted `"port": "8443"` is a
 * number), never what the user meant in a way that changes the wire.
 */
class XrayConfigRepairsV165Test {

    @Test
    fun nothingToRepairMeansTheStoredBytesComeBackUntouched() {
        val clean = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "vless")
                        .put("settings", JSONObject().put("address", "1.2.3.4").put("port", 443).put("encryption", "none"))
                        .put("streamSettings", JSONObject().put("security", "tls"))
                )
            )
            .toString()
        val report = XrayConfigRepairs.apply(clean)
        assertFalse(report.changed)
        assertEquals(clean, report.document)
        assertEquals(emptyList<String>(), report.repairs)
    }

    @Test
    fun removedChainFieldBecomesTheFieldTheCoreReads() {
        val source = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "exit")
                        .put("protocol", "vless")
                        .put("settings", JSONObject().put("address", "1.2.3.4").put("port", 443).put("encryption", "none"))
                        .put("proxySettings", JSONObject().put("tag", "entry").put("transportLayer", true))
                )
            )
            .toString()

        val report = XrayConfigRepairs.apply(source)
        assertTrue(report.repairs.contains("proxySettings-to-dialerProxy"))
        val repaired = JSONObject(report.document)
        val outbound = repaired.getJSONArray("outbounds").getJSONObject(0)
        assertFalse(outbound.has("proxySettings"))
        assertEquals(
            "entry",
            outbound.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy")
        )
    }

    @Test
    fun anExistingDialerWinsAndAUselessChainFieldIsDropped() {
        val withBoth = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "exit")
                        .put("protocol", "vless")
                        .put("proxySettings", JSONObject().put("tag", "ignored"))
                        .put("streamSettings", JSONObject().put("sockopt", JSONObject().put("dialerProxy", "real")))
                )
            )
            .toString()
        val report = XrayConfigRepairs.apply(withBoth)
        assertTrue(report.repairs.contains("proxySettings-superseded-by-dialerProxy"))
        val outbound = JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("real", outbound.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))

        val dangling = JSONObject()
            .put("outbounds", JSONArray().put(JSONObject().put("tag", "x").put("protocol", "vless").put("proxySettings", JSONObject())))
            .toString()
        val dropped = XrayConfigRepairs.apply(dangling)
        assertTrue(dropped.repairs.contains("proxySettings-dropped"))
        assertFalse(JSONObject(dropped.document).getJSONArray("outbounds").getJSONObject(0).has("proxySettings"))
    }

    @Test
    fun aVlessUserWithoutEncryptionGetsTheOnlyValueThatIsNotAFatalError() {
        val classic = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vless")
                        .put(
                            "settings",
                            JSONObject().put(
                                "vnext",
                                JSONArray().put(
                                    JSONObject()
                                        .put("address", "1.2.3.4")
                                        .put("port", 443)
                                        .put("users", JSONArray().put(JSONObject().put("id", "uuid")))
                                )
                            )
                        )
                )
            )
            .toString()
        val report = XrayConfigRepairs.apply(classic)
        assertTrue(report.repairs.contains("vless-encryption-defaulted"))
        // The lift runs right after, so the classic form also gains the simplified fields the core
        // reads first — with `vnext` preserved for a re-export.
        assertTrue(report.repairs.contains("vless-simplified-form"))
        val settings = JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings")
        assertEquals("none", settings.getString("encryption"))
        assertEquals("1.2.3.4", settings.getString("address"))
        assertEquals(443, settings.getInt("port"))
        assertEquals("uuid", settings.getString("id"))
        assertTrue(settings.has("vnext"))
        assertEquals(
            "none",
            settings.getJSONArray("vnext").getJSONObject(0).getJSONArray("users").getJSONObject(0).getString("encryption")
        )
    }

    @Test
    fun meaninglessVlessEncryptionTokensBecomeNoneAndRealClaimsAreLeftAlone() {
        listOf("auto", "none/none", "zero", "plain", "default").forEach { token ->
            val source = vlessWithEncryption(token)
            val report = XrayConfigRepairs.apply(source)
            assertTrue("$token must be rewritten", report.repairs.contains("vless-encryption-defaulted"))
            assertEquals(
                "none",
                JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0)
                    .getJSONObject("settings").getString("encryption")
            )
        }
        // A value that claims a payload cipher is not rewritten into nothing: it is reported as a
        // core gap instead, which is the difference between a repair and an edit.
        val cipher = vlessWithEncryption("aes-128-gcm")
        assertFalse(XrayConfigRepairs.apply(cipher).repairs.contains("vless-encryption-defaulted"))
        assertEquals("aes-128-gcm", JSONObject(XrayConfigRepairs.apply(cipher).document).getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").getString("encryption"))
    }

    @Test
    fun aSimplifiedVlessOutboundWithoutAnyEncryptionFieldIsRepairedToo() {
        val source = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vless")
                        .put("settings", JSONObject().put("address", "1.2.3.4").put("port", 443).put("id", "uuid"))
                )
            )
            .toString()
        val report = XrayConfigRepairs.apply(source)
        assertTrue(report.repairs.contains("vless-encryption-defaulted"))
        assertEquals(
            "none",
            JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").getString("encryption")
        )
    }

    @Test
    fun aTrojanServerListLosesNothingByBeingLifted() {
        val source = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "trojan")
                        .put(
                            "settings",
                            JSONObject().put(
                                "servers",
                                JSONArray().put(JSONObject().put("address", "1.2.3.4").put("port", 443).put("password", "secret"))
                            )
                        )
                )
            )
            .toString()
        val report = XrayConfigRepairs.apply(source)
        assertTrue(report.repairs.contains("trojan-password-lifted"))
        assertEquals(
            "secret",
            JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").getString("password")
        )
    }

    @Test
    fun securityIsNamedOnlyWhenTheDocumentAlreadyCarriesTheBlock() {
        listOf("tlsSettings" to "tls", "realitySettings" to "reality").forEach { (block, expected) ->
            val stream = JSONObject().put("method", "raw").put(block, JSONObject().put("serverName", "example.com"))
            val source = JSONObject()
                .put("outbounds", JSONArray().put(JSONObject().put("protocol", "vless").put("streamSettings", stream)))
                .toString()
            val report = XrayConfigRepairs.apply(source)
            assertTrue(report.repairs.contains("security-inferred-from-$expected"))
            assertEquals(
                expected,
                JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0)
                    .getJSONObject("streamSettings").getString("security")
            )
        }

        // A document that states a security, even a wrong one, is never re-labelled here: that would
        // be a translation, and the conversion contract forbids it.
        val stated = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vless")
                        .put("streamSettings", JSONObject().put("security", "none").put("tlsSettings", JSONObject()))
                )
            )
            .toString()
        assertFalse(XrayConfigRepairs.apply(stated).repairs.any { it.startsWith("security-inferred") })
    }

    @Test
    fun quotedNumbersAreUnquotedWhereverTheySit() {
        val source = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vmess")
                        .put("port", "8443")
                        .put(
                            "settings",
                            JSONObject().put(
                                "vnext",
                                JSONArray().put(
                                    JSONObject()
                                        .put("port", "443")
                                        .put("users", JSONArray().put(JSONObject().put("alterId", "0").put("level", "1")))
                                )
                            )
                        )
                )
            )
            .toString()
        val report = XrayConfigRepairs.apply(source)
        assertTrue(report.repairs.contains("port-unquoted"))
        assertTrue(report.repairs.contains("alterId-unquoted"))
        val repaired = JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0)
        assertEquals(8443, repaired.getInt("port"))
        val server = repaired.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals(443, server.getInt("port"))
        assertEquals(0, server.getJSONArray("users").getJSONObject(0).getInt("alterId"))
    }

    @Test
    fun nonsenseNumbersAreLeftExactlyAsTheyWere() {
        // A port of "8443abc" is not a number in any reading, and inventing one would be worse than
        // the error the user will be told about.
        val source = JSONObject()
            .put("outbounds", JSONArray().put(JSONObject().put("protocol", "vless").put("port", "8443abc")))
            .toString()
        val report = XrayConfigRepairs.apply(source)
        assertFalse(report.repairs.contains("port-unquoted"))
        assertEquals("8443abc", JSONObject(report.document).getJSONArray("outbounds").getJSONObject(0).getString("port"))
    }

    @Test
    fun garbageInGarbageOutNeverThrows() {
        listOf("", "   ", "not json", "{", "[1,2]", "{}").forEach { input ->
            val report = XrayConfigRepairs.apply(input)
            assertEquals(input, report.document)
            assertFalse(report.changed)
        }
    }

    @Test
    fun aSingBoxDocumentIsNeverTouchedByAnXrayShapedPass() {
        val nativeConfig = JSONObject()
            .put("log", JSONObject().put("level", "info"))
            .put("outbounds", JSONArray().put(JSONObject().put("tag", "direct").put("type", "direct")))
            .toString()
        val report = XrayConfigRepairs.apply(nativeConfig)
        assertFalse(report.changed)
        assertEquals(nativeConfig, report.document)
    }

    private fun vlessWithEncryption(token: String): String = JSONObject()
        .put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("protocol", "vless")
                    .put(
                        "settings",
                        JSONObject()
                            .put("address", "1.2.3.4")
                            .put("port", 443)
                            .put("id", "uuid")
                            .apply { if (token.isNotEmpty()) put("encryption", token) }
                    )
            )
        )
        .toString()
}
