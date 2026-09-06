package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SINGBOX_CORE_V151 — the second engine, pinned by unit test.
 *
 * A config the extended core cannot parse fails at `run`, after the tunnel is already half up, so
 * every promise here is about the JSON Marble writes: the tags the URL test and the routing rules
 * depend on, the DNS schema sing-box 1.12+ actually reads (`server`, not `address`), and the three
 * switches the Engine page exposes. Nothing here spawns a process; the schema is the contract.
 */
class SingBoxCoreV151Test {

    private fun linkProfile(raw: String) = ProxyProfile(
        id = "node-1",
        name = "Node 1",
        scheme = raw.substringBefore("://"),
        raw = raw,
        configJson = "",
        host = "198.51.100.7",
        port = 443
    )

    private fun jsonProfile(protocol: String) = ProxyProfile(
        id = "node-2",
        name = "Node 2",
        scheme = protocol,
        raw = "",
        configJson = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", protocol)
                        .put("tag", "proxy")
                        .put(
                            "settings",
                            JSONObject().put(
                                "vnext",
                                JSONArray().put(
                                    JSONObject()
                                        .put("address", "198.51.100.7")
                                        .put("port", 443)
                                        .put(
                                            "users",
                                            JSONArray().put(
                                                JSONObject()
                                                    .put("id", "11111111-2222-3333-4444-555555555555")
                                                    .put("encryption", "none")
                                            )
                                        )
                                )
                            )
                        )
                        .put(
                            "streamSettings",
                            JSONObject().put("network", "tcp").put("security", "tls")
                        )
                )
            )
            .toString(),
        host = "198.51.100.7",
        port = 443
    )

    private fun build(
        profile: ProxyProfile,
        settings: AppSettings = AppSettings()
    ): SingBoxConfigBuilder.Build = SingBoxConfigBuilder.build(
        profile = profile,
        settings = settings,
        socksPort = 10808,
        apiPort = 39090,
        apiSecret = "secret",
        logPath = "/data/local/tmp/singbox.log",
        cachePath = "/data/local/tmp/singbox-cache.db"
    )

    @Test
    fun engineIdsRoundTripAndUnknownFallsBackToXray() {
        assertEquals(CoreEngine.XRAY, parseCoreEngine("xray"))
        assertEquals(CoreEngine.SINGBOX, parseCoreEngine("singbox"))
        assertEquals(CoreEngine.SINGBOX, parseCoreEngine(" SING-BOX "))
        assertEquals(CoreEngine.XRAY, parseCoreEngine(""))
        assertEquals(CoreEngine.XRAY, parseCoreEngine("something-else"))
        assertEquals(CoreEngine.XRAY, parseCoreEngine(AppSettings().coreEngineId))
    }

    @Test
    fun bothCoresShipAsExtractableNativeLibraries() {
        assertEquals("libxray.so", CoreEngineInfo.binaryName(CoreEngine.XRAY))
        assertEquals("libsingbox.so", CoreEngineInfo.binaryName(CoreEngine.SINGBOX))
        assertEquals("Xray-core", CoreEngineInfo.displayName(CoreEngine.XRAY))
        assertEquals("sing-box extended", CoreEngineInfo.displayName(CoreEngine.SINGBOX))
    }

    @Test
    fun aShareLinkGoesToTheCoresOwnParser() {
        val support = SingBoxConfigBuilder.describe(linkProfile(VLESS_LINK), AppSettings())
        assertTrue(support.reason, support.supported)
        assertEquals(SingBoxConfigBuilder.STRATEGY_LINK, support.strategy)

        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val proxy = outbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("parser", proxy.getString("type"))
        assertEquals(VLESS_LINK, proxy.getString("link"))
    }

    @Test
    fun turningTheParserOffTranslatesTheStoredConfig() {
        val profile = jsonProfile("vless")
        val parser = SingBoxConfigBuilder.describe(profile, AppSettings(singBoxPreferParser = true))
        val translated = SingBoxConfigBuilder.describe(profile, AppSettings(singBoxPreferParser = false))
        assertTrue(parser.supported)
        assertTrue(translated.supported)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, translated.strategy)
    }

    @Test
    fun anUnreadableNodeIsReportedInsteadOfGuessed() {
        val profile = jsonProfile("dokodemo-door")
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse("an unknown protocol must not be claimed as supported", support.supported)
        assertTrue(support.reason.isNotBlank())
        assertEquals("", support.strategy)
    }

    @Test
    fun sshChainsStayOnTheXrayEngine() {
        val profile = ProxyProfile(
            id = "ssh",
            name = "SSH",
            scheme = "ssh",
            raw = "",
            configJson = "{}",
            host = "198.51.100.9",
            port = 22
        )
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse(support.supported)
        assertTrue(support.reason.contains("Xray"))
    }

    @Test
    fun theDataPathIsOneLocalMixedInbound() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val inbounds = config.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        val inbound = inbounds.getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals(SingBoxConfigBuilder.INBOUND_TAG, inbound.getString("tag"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(10808, inbound.getInt("listen_port"))
    }

    @Test
    fun routingTagsExistSoTheUrlTestCanAddressTheProxy() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        listOf(
            SingBoxConfigBuilder.PROXY_TAG,
            SingBoxConfigBuilder.DIRECT_TAG,
            SingBoxConfigBuilder.BLOCK_TAG,
            SingBoxConfigBuilder.DNS_OUT_TAG
        ).forEach { tag ->
            assertNotNull("outbound \"$tag\" must exist", outboundOrNull(config, tag))
        }
        assertEquals(SingBoxConfigBuilder.PROXY_TAG, config.getJSONObject("route").getString("final"))
    }

    @Test
    fun dnsServersUseTheSchemaSingBoxActuallyReads() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        assertTrue(servers.length() >= 2)
        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            if (server.getString("type") == "hosts") continue
            // sing-box 1.12 renamed the DNS server address key. A server written with `address`
            // parses to nothing and every lookup dies, so this is the single most expensive typo
            // this builder could make.
            assertFalse("a DNS server must not use the pre-1.12 `address` key", server.has("address"))
            assertTrue("a DNS server must carry `server`", server.getString("server").isNotBlank())
        }
        val direct = dnsServer(config, SingBoxConfigBuilder.DNS_DIRECT_TAG)
        assertEquals(
            "the direct resolver must not detour into the tunnel it is establishing",
            SingBoxConfigBuilder.DIRECT_TAG,
            direct.getString("detour")
        )
    }

    @Test
    fun theClashApiIsLocalOnlyAndCarriesTheUrlTestSecret() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val api = config.getJSONObject("experimental").getJSONObject("clash_api")
        assertEquals("127.0.0.1:39090", api.getString("external_controller"))
        assertEquals("secret", api.getString("secret"))
        assertTrue(api.getBoolean("access_control_allow_private_network"))
    }

    @Test
    fun theThreeEngineSwitchesReachTheConfig() {
        val on = JSONObject(build(linkProfile(VLESS_LINK), AppSettings()).json)
        val off = JSONObject(
            build(
                linkProfile(VLESS_LINK),
                AppSettings(
                    singBoxUnifiedDelay = false,
                    singBoxCacheFile = false
                )
            ).json
        )
        assertTrue(on.getJSONObject("experimental").getJSONObject("unified_delay").getBoolean("enabled"))
        assertTrue(on.getJSONObject("experimental").has("cache_file"))

        assertFalse(off.getJSONObject("experimental").getJSONObject("unified_delay").getBoolean("enabled"))
        assertFalse(
            "the cache file must be absent, not merely disabled",
            off.getJSONObject("experimental").has("cache_file")
        )
    }

    @Test
    fun ruleSetsAreFetchedDirectlySoAFreshInstallCanBoot() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val sets = config.getJSONObject("route").getJSONArray("rule_set")
        assertTrue(sets.length() >= 3)
        for (i in 0 until sets.length()) {
            val set = sets.getJSONObject(i)
            assertEquals("direct", set.getString("download_detour"))
            assertTrue(set.getString("url").startsWith("https://"))
            assertEquals("binary", set.getString("format"))
        }
    }

    @Test
    fun certificatePinningIsReportedNotPretended() {
        val profile = jsonProfile("vless").let {
            val root = JSONObject(it.configJson)
            root.getJSONArray("outbounds")
                .getJSONObject(0)
                .getJSONObject("streamSettings")
                .put(
                    "tlsSettings",
                    JSONObject().put("pinnedPeerCertSha256", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                )
            it.copy(configJson = root.toString())
        }
        val support = SingBoxConfigBuilder.describe(profile, AppSettings(singBoxPreferParser = false))
        assertTrue(support.supported)
        assertTrue(
            "the pinning limitation must be stated on the node",
            support.notes.any { note -> note.contains("pin", ignoreCase = true) }
        )
    }

    @Test
    fun shareLinkIgnoresAnythingThatIsNotALink() {
        assertNull(SingBoxConfigBuilder.shareLink(jsonProfile("vless")))
        assertNull(SingBoxConfigBuilder.shareLink(linkProfile("ftp://example.com")))
        assertNull(SingBoxConfigBuilder.shareLink(linkProfile("vless://two words")))
        assertEquals(VLESS_LINK, SingBoxConfigBuilder.shareLink(linkProfile(VLESS_LINK)))
    }

    private fun outboundOrNull(config: JSONObject, tag: String): JSONObject? {
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.getJSONObject(i)
            if (candidate.optString("tag") == tag) return candidate
        }
        return null
    }

    private fun outbound(config: JSONObject, tag: String): JSONObject =
        outboundOrNull(config, tag) ?: error("missing outbound $tag")

    private fun dnsServer(config: JSONObject, tag: String): JSONObject {
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        for (i in 0 until servers.length()) {
            val candidate = servers.getJSONObject(i)
            if (candidate.optString("tag") == tag) return candidate
        }
        error("missing DNS server $tag")
    }

    private companion object {
        const val VLESS_LINK =
            "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
                "?encryption=none&security=tls&type=tcp&sni=example.com#Node+1"
    }
}
