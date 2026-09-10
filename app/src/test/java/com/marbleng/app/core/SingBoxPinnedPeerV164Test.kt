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
 * MARBLE_SINGBOX_PINNED_PEER_V164 — the pinned sing-box extended core speaks Xray's pinning
 * vocabulary, and this is the translation contract in tests:
 *
 *   - `pcs` pinnedPeerCertSha256           → `pinned_peer_cert_sha256` (hex list, lowercase)
 *   - `vcn` verifyPeerCertByName           → `verify_peer_cert_by_name` (name list)
 *   - pinnedPeerCertificatePublicKeySha256 → `certificate_public_key_sha256` (base64 SPKI digest)
 *   - pinnedPeerCertificateChainSha256     → still refused: no sing-box option can express it
 *
 * A pin is a security promise the user made: it is translated losslessly or the profile is
 * refused — never silently downgraded to `insecure`.
 */
class SingBoxPinnedPeerV164Test {

    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val pinA = "ab".repeat(32)
    private val pinB = "cd".repeat(32)

    private fun profile(stream: JSONObject, raw: String = "") = ProxyProfile(
        id = "pin", name = "SOLIDVPS", scheme = "vless", raw = raw,
        configJson = JSONObject().put(
            "outbounds",
            JSONArray().put(
                JSONObject().put("protocol", "vless").put("tag", "proxy")
                    .put("settings", JSONObject().put("address", "vps1.example.org").put("port", 8443)
                        .put("id", uuid).put("encryption", "none"))
                    .put("streamSettings", stream)
            )
        ).toString(),
        host = "vps1.example.org", port = 8443
    )

    private fun tls(p: ProxyProfile): JSONObject {
        val config = JSONObject(
            SingBoxConfigBuilder.build(p, AppSettings(), 10808, 39090, "secret", "", "").json
        )
        val proxy = NativeSingBoxConfig.objects(config.getJSONArray("outbounds"))
            .single { it.optString("tag") == SingBoxConfigBuilder.PROXY_TAG }
        return proxy.getJSONObject("tls")
    }

    private fun solidVpsLink() =
        "vless://$uuid@vps1.example.org:8443" +
            "?encryption=none&security=tls&type=tcp&flow=xtls-rprx-vision&sni=spotify.com&fp=chrome" +
            "&alpn=h2%2Chttp%2F1.1&pcs=$pinA%2C$pinB" +
            "&vcn=vps1.example.org&allowInsecure=0#SOLIDVPS"

    @Test
    fun theReportedSolidVpsLinkTranslatesPcsAndVcnIntoThePatchedCoreOptions() {
        // The exact reported shape: pinned leaf + verify name behind a fronted SNI. Marble's own
        // importer (ProxyParser) turns `pcs` into tlsSettings.pinnedPeerCertSha256 and `vcn` into
        // tlsSettings.verifyPeerCertByName; the unit-test seam [SingBoxConfigBuilder.linkJson]
        // stands in for it because the real parser uses android.net.Uri.
        val previous = SingBoxConfigBuilder.linkJson
        try {
            SingBoxConfigBuilder.linkJson = { raw ->
                JSONObject().put(
                    "outbounds",
                    JSONArray().put(
                        JSONObject().put("protocol", "vless").put("tag", "proxy")
                            .put("settings", JSONObject().put("address", "vps1.example.org")
                                .put("port", 8443).put("id", uuid).put("encryption", "none")
                                .put("flow", "xtls-rprx-vision"))
                            .put("streamSettings", JSONObject().put("network", "tcp")
                                .put("security", "tls").put("tlsSettings", JSONObject()
                                    .put("serverName", "spotify.com").put("fingerprint", "chrome")
                                    .put("alpn", JSONArray().put("h2").put("http/1.1"))
                                    .put("pinnedPeerCertSha256", "$pinA,$pinB")
                                    .put("verifyPeerCertByName", "vps1.example.org")))
                    )
                )
            }
            val p = ProxyProfile(
                id = "solid", name = "SOLIDVPS", scheme = "vless", raw = solidVpsLink(),
                configJson = "", host = "vps1.example.org", port = 8443
            )
            val support = SingBoxConfigBuilder.describe(p, AppSettings(singBoxPreferParser = false))
            assertTrue(support.toString(), support.supported)
            // With the link translated by Marble itself (reader 2), the pin survives verbatim.
            val builds = SingBoxConfigBuilder.candidateBuilds(p, AppSettings(), 10808, 39090, "secret", "", "")
            val translated = builds.first { it.strategy == SingBoxConfigBuilder.STRATEGY_LINK_TRANSLATED }
            val outbounds = JSONObject(translated.json).getJSONArray("outbounds")
            val tls = NativeSingBoxConfig.objects(outbounds)
                .single { it.optString("tag") == SingBoxConfigBuilder.PROXY_TAG }
                .getJSONObject("tls")
            assertEquals("spotify.com", tls.getString("server_name"))
            assertEquals(listOf(pinA, pinB), tls.getJSONArray("pinned_peer_cert_sha256").toList())
            assertEquals(listOf("vps1.example.org"), tls.getJSONArray("verify_peer_cert_by_name").toList())
            assertFalse(tls.has("insecure"))
        } finally {
            SingBoxConfigBuilder.linkJson = previous
        }
    }

    @Test
    fun pinnedPeerCertSha256NormalizesToLowercaseHexList() {
        val stream = JSONObject().put("security", "tls").put("tlsSettings", JSONObject()
            .put("serverName", "spotify.com").put("pinnedPeerCertSha256", "${pinA.uppercase()},$pinB"))
        val tls = tls(profile(stream))
        assertEquals(listOf(pinA, pinB), tls.getJSONArray("pinned_peer_cert_sha256").toList())
    }

    @Test
    fun spkiPinBecomesTheNativeBase64PublicKeyDigest() {
        val hex = "ab".repeat(32)
        val stream = JSONObject().put("security", "tls").put("tlsSettings", JSONObject()
            .put("serverName", "vps1.example.org").put("pinnedPeerCertificatePublicKeySha256", hex))
        val tls = tls(profile(stream))
        assertFalse(tls.has("pinned_peer_cert_sha256"))
        val encoded = tls.getJSONArray("certificate_public_key_sha256").getString(0)
        val decoded = java.util.Base64.getDecoder().decode(encoded)
        assertEquals(32, decoded.size)
        decoded.forEach { assertEquals(0xAB.toByte(), it) }
    }

    @Test
    fun verifyNameWithoutAnyPinStillMapsAndKeepsServerName() {
        val stream = JSONObject().put("security", "tls").put("tlsSettings", JSONObject()
            .put("serverName", "spotify.com").put("verifyPeerCertByName", "vps1.example.org"))
        val tls = tls(profile(stream))
        assertEquals("spotify.com", tls.getString("server_name"))
        assertEquals(listOf("vps1.example.org"), tls.getJSONArray("verify_peer_cert_by_name").toList())
    }

    @Test
    fun aMalformedPinIsAConfigErrorNotASilentDrop() {
        val stream = JSONObject().put("security", "tls").put("tlsSettings", JSONObject()
            .put("serverName", "spotify.com").put("pinnedPeerCertSha256", "not-a-sha256"))
        val support = SingBoxConfigBuilder.describe(profile(stream), AppSettings())
        assertFalse(support.toString(), support.supported)
        assertTrue(support.reason, support.reason.startsWith("config-unsupported:"))
        assertTrue(support.reason, support.reason.contains("not-a-sha256"))
    }

    @Test
    fun theWholeChainPinRemainsARefusalWithTheEngineToUseInstead() {
        val stream = JSONObject().put("security", "tls").put("tlsSettings", JSONObject()
            .put("serverName", "spotify.com").put("pinnedPeerCertificateChainSha256", pinA))
        val support = SingBoxConfigBuilder.describe(profile(stream), AppSettings())
        assertFalse(support.toString(), support.supported)
        assertEquals(SingBoxConfigBuilder.CHAIN_PIN_REFUSAL, support.reason)
    }

    @Test
    fun allowInsecureStillMapsToInsecureWithoutErasingThePin() {
        val stream = JSONObject().put("security", "tls").put("tlsSettings", JSONObject()
            .put("serverName", "spotify.com").put("pinnedPeerCertSha256", pinA)
            .put("allowInsecure", true))
        val tls = tls(profile(stream))
        assertTrue(tls.getBoolean("insecure"))
        assertEquals(listOf(pinA), tls.getJSONArray("pinned_peer_cert_sha256").toList())
    }

    @Test
    fun theParserCandidateIsAFirstClassReaderForPinnedLinks() {
        // preferParser=true used to exclude the parser for pinned nodes because it dropped the
        // pin. The patched fork reads pcs/vcn itself, so the parser now leads.
        val p = ProxyProfile(
            id = "solid", name = "SOLIDVPS", scheme = "vless", raw = solidVpsLink(),
            configJson = "", host = "vps1.example.org", port = 8443
        )
        val support = SingBoxConfigBuilder.describe(p, AppSettings(singBoxPreferParser = true))
        assertTrue(support.toString(), support.supported)
        assertEquals(SingBoxConfigBuilder.STRATEGY_LINK, support.strategy)
    }
}
