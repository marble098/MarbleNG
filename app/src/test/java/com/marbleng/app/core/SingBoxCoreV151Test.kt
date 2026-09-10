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
        settings: AppSettings = AppSettings(),
        resolverPool: List<String> = emptyList()
    ): SingBoxConfigBuilder.Build = SingBoxConfigBuilder.build(
        profile = profile,
        settings = settings,
        socksPort = 10808,
        apiPort = 39090,
        apiSecret = "secret",
        logPath = "/data/local/tmp/singbox.log",
        cachePath = "/data/local/tmp/singbox-cache.db",
        resolverPool = resolverPool
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
            SingBoxConfigBuilder.BLOCK_TAG
        ).forEach { tag ->
            assertNotNull("outbound \"$tag\" must exist", outboundOrNull(config, tag))
        }
        assertEquals(SingBoxConfigBuilder.PROXY_TAG, config.getJSONObject("route").getString("final"))
    }

    /**
     * MARBLE_SINGBOX_DNS_ACTION_V152 — the `dns` outbound is deprecated since 1.11.0 and
     * REMOVED in 1.13.0, and the pinned extended core (v1.14.x) rejects the entire config for
     * carrying one: "outbounds[3]: dns outbound is deprecated in sing-box 1.11.0 and removed in
     * sing-box 1.13.0, use rule actions instead". That single line refused all 17 profiles of
     * the shipped log and left every session BLOCKED. The builder must never write it, and the
     * `hijack-dns` rule action — the replacement the error itself names — must be there instead.
     */
    @Test
    fun theDnsOutboundRemovedInSingBox113IsNeverWritten() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val type = outbounds.getJSONObject(i).optString("type")
            assertFalse(
                "the removed \"dns\" outbound must never be written (found \"dns-out\" at $i)",
                type.equals("dns", ignoreCase = true)
            )
        }
        val rules = config.getJSONObject("route").getJSONArray("rules")
        var hijackPresent = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if ("hijack-dns" == rule.optString("action")) hijackPresent = true
            assertFalse(
                "no route rule may target a removed dns outbound",
                rule.optString("outbound").equals("dns-out", ignoreCase = true)
            )
        }
        assertTrue("the hijack-dns rule action must intercept DNS", hijackPresent)
    }

    /**
     * MARBLE_SINGBOX_BOOTSTRAP_DOH_V163 — the node's own hostname must not ask the Iranian
     * system resolver, which answers 10.10.34.35/36. Encrypted DoH over DIRECT (IP-literal
     * endpoints, Xray's `https+local://` equivalent) is the first hop; `dns-local` stays as
     * last-resort so a total DoH outage still bootstraps. A literal-IP endpoint needs neither.
     */
    @Test
    fun theProxyHostnameBootstrapsThroughEncryptedDirectDns() {
        val literalConfig = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val hostnameProfile = linkProfile(VLESS_LINK).copy(host = "edge.example.com")
        val hostnameConfig = JSONObject(build(hostnameProfile).json)

        val servers = hostnameConfig.getJSONObject("dns").getJSONArray("servers")
        var localPresent = false
        var bootstrapPresent = false
        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            if (SingBoxConfigBuilder.DNS_LOCAL_TAG == server.optString("tag")) localPresent = true
            if (SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG == server.optString("tag")) {
                bootstrapPresent = true
                assertEquals("fallback", server.getString("type"))
                val peers = server.getJSONArray("servers")
                val peerTags = (0 until peers.length()).map { peers.getString(it) }
                assertTrue("bootstrap must keep dns-local as last resort: $peerTags", SingBoxConfigBuilder.DNS_LOCAL_TAG in peerTags)
                assertTrue("bootstrap must try an encrypted peer first: $peerTags", peerTags.first() != SingBoxConfigBuilder.DNS_LOCAL_TAG)
            }
        }
        assertTrue("a system-resolver DNS server must still exist as last resort", localPresent)
        assertTrue("an encrypted-direct bootstrap resolver must exist", bootstrapPresent)

        fun bootstrapRuleServer(config: JSONObject, host: String): String? {
            val rules = config.getJSONObject("dns").getJSONArray("rules")
            for (i in 0 until rules.length()) {
                val rule = rules.getJSONObject(i)
                val domains = rule.optJSONArray("domain") ?: continue
                for (d in 0 until domains.length()) {
                    if (domains.optString(d) == host) return rule.optString("server")
                }
            }
            return null
        }
        assertEquals(
            "the proxy endpoint's own hostname must resolve via encrypted-direct bootstrap",
            SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG,
            bootstrapRuleServer(hostnameConfig, "edge.example.com")
        )
        assertNull(
            "a literal-IP endpoint needs no DNS bootstrap rule",
            bootstrapRuleServer(literalConfig, "198.51.100.7")
        )
        assertEquals(
            SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG,
            hostnameConfig.getJSONObject("route").getString("default_domain_resolver")
        )
    }

    @Test
    fun iranModeRejectsThePoisonInjectorRange() {
        val on = JSONObject(
            build(
                linkProfile(VLESS_LINK),
                AppSettings(
                    iranModePolicy = com.marbleng.app.model.IranModePolicy.ALWAYS_ON,
                    iranModeCountermeasures = true
                )
            ).json
        )
        val off = JSONObject(
            build(
                linkProfile(VLESS_LINK),
                AppSettings(iranModePolicy = com.marbleng.app.model.IranModePolicy.OFF)
            ).json
        )
        val onRules = on.getJSONObject("route").getJSONArray("rules").toString()
        val offRules = off.getJSONObject("route").getJSONArray("rules").toString()
        assertTrue("poison injector range blocked", onRules.contains("10.10.34.0/24"))
        assertFalse("poison injector range must not appear when Iran mode is off", offRules.contains("10.10.34.0/24"))
    }

    @Test
    fun modernConfigNeverCarriesDeprecatedIndependentCacheOrAutoDetectInterface() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val dns = config.getJSONObject("dns")
        assertFalse(
            "sing-box 1.14+ deprecated independent_cache; modern configs must omit it",
            dns.has("independent_cache")
        )
        val route = config.getJSONObject("route")
        assertFalse(
            "sing-box auto_detect_interface crashes on Android due to banned netlink socket; must omit it",
            route.has("auto_detect_interface")
        )
    }

    @Test
    fun dnsServersUseTheSchemaSingBoxActuallyReads() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        assertTrue(servers.length() >= 2)
        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            val type = server.getString("type")
            // `hosts` and `local` (MARBLE_SINGBOX_DNS_ACTION_V152, the system resolver) have no
            // address of their own; every address-bearing type must use the 1.12 `server` key.
            if (type == "hosts" || type == "local" || type == "fallback") continue
            // sing-box 1.12 renamed the DNS server address key. A server written with `address`
            // parses to nothing and every lookup dies, so this is the single most expensive typo
            // this builder could make.
            assertFalse("a DNS server must not use the pre-1.12 `address` key", server.has("address"))
            assertTrue("a DNS server must carry `server`", server.getString("server").isNotBlank())
        }
        val direct = dnsServer(config, SingBoxConfigBuilder.DNS_DIRECT_TAG)
        assertEquals("fallback", direct.getString("type"))
        assertEquals(SingBoxConfigBuilder.DNS_LOCAL_TAG, direct.getJSONArray("servers").getString(0))
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
    fun ruleSetsAreBundledLocallySoAnOfflineFirstInstallCanBoot() {
        val config = JSONObject(build(linkProfile(VLESS_LINK)).json)
        val sets = config.getJSONObject("route").getJSONArray("rule_set")
        assertTrue(sets.length() >= 3)
        for (i in 0 until sets.length()) {
            val set = sets.getJSONObject(i)
            assertFalse(set.has("download_detour"))
            assertFalse(set.has("url"))
            assertEquals("local", set.getString("type"))
            assertTrue(set.getString("path").endsWith(".srs"))
            assertEquals("binary", set.getString("format"))
        }
    }

    @Test
    fun certificatePinningIsTranslatedNotPretended() {
        // MARBLE_SINGBOX_PINNED_PEER_V164 — the pinned core speaks Xray's pinning vocabulary, so
        // a pcs profile is now translated with the pin intact. The promise this test guards is
        // unchanged: the pin must never be dropped or downgraded into `insecure` behind the
        // user's back. The base64 spelling below is normalised to hex on the way in.
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
        assertTrue("pcs/vcn runs on the patched core: $support", support.supported)
        val built = JSONObject(
            SingBoxConfigBuilder.build(profile, AppSettings(), 10808, 39090, "s", "", "").json
        )
        val tls = NativeSingBoxConfig.objects(built.getJSONArray("outbounds"))
            .single { it.optString("tag") == SingBoxConfigBuilder.PROXY_TAG }
            .getJSONObject("tls")
        assertEquals(listOf("00".repeat(32)), tls.getJSONArray("pinned_peer_cert_sha256").toList())
        assertFalse("dropping certificate verification is not a supported conversion", tls.optBoolean("insecure", false))
    }

    @Test
    fun shareLinkIgnoresAnythingThatIsNotALink() {
        assertNull(SingBoxConfigBuilder.shareLink(jsonProfile("vless")))
        assertNull(SingBoxConfigBuilder.shareLink(linkProfile("ftp://example.com")))
        // MARBLE_SINGBOX_PROTOCOLS_V153 — a single line with spaces in its display-name fragment
        // is a real link, not a blob; the core parser can read it.
        assertEquals(
            "vless://two words",
            SingBoxConfigBuilder.shareLink(linkProfile("vless://two words"))
        )
        assertEquals(VLESS_LINK, SingBoxConfigBuilder.shareLink(linkProfile(VLESS_LINK)))
    }

    // MARBLE_SINGBOX_PROTOCOLS_V153 — the network field regression and the link-parser guard that
    // together made "none of the other servers work" on the sing-box engine.
    @Test
    fun shareLinksWithSpacesInTheDisplayNameAreHandedToTheCoreParser() {
        val withSpace = "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
            "?security=tls&type=tcp#Node A"
        assertEquals(
            "a single line may carry unencoded spaces in its display-name fragment",
            withSpace,
            SingBoxConfigBuilder.shareLink(linkProfile(withSpace))
        )
        assertNull(SingBoxConfigBuilder.shareLink(linkProfile("vless://a\nvless://b")))
    }

    @Test
    fun translatedOutboundsNeverCarryAnArrayNetworkField() {
        val config = JSONObject(
            build(jsonProfile("vless"), AppSettings(singBoxPreferParser = false)).json
        )
        val proxy = outbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertFalse(
            "sing-box `network` is a scalar or absent; an array rejects the whole config",
            proxy.has("network")
        )
    }

    @Test
    fun hysteriaV1TranslatesToTheV1OutboundNotHysteria2() {
        val profile = jsonProfile("hysteria").let { source ->
            val root = JSONObject(source.configJson)
            val outbound = root.getJSONArray("outbounds").getJSONObject(0)
            outbound.put(
                "settings",
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put("auth_str", "hysteria-auth")
            )
            outbound.put(
                "streamSettings",
                JSONObject().put("method", "hysteria").put("security", "tls")
            )
            source.copy(configJson = root.toString())
        }
        val config = JSONObject(
            build(profile, AppSettings(singBoxPreferParser = false)).json
        )
        val proxy = outbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("hysteria", proxy.getString("type"))
        assertEquals("hysteria-auth", proxy.getString("auth_str"))
    }

    @Test
    fun directServerShapesAreTranslatedForShadowsocks() {
        val profile = jsonProfile("shadowsocks").let { source ->
            val root = JSONObject(source.configJson)
            root.getJSONArray("outbounds").getJSONObject(0).put(
                "settings",
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put("method", "2022-blake3-aes-128-gcm")
                    .put("password", "password")
            )
            source.copy(configJson = root.toString())
        }
        val config = JSONObject(
            build(profile, AppSettings(singBoxPreferParser = false)).json
        )
        val proxy = outbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("shadowsocks", proxy.getString("type"))
        assertEquals("2022-blake3-aes-128-gcm", proxy.getString("method"))
        assertEquals("password", proxy.getString("password"))
    }

    @Test
    fun resolverPoolReachesTheDnsConfigSoDemotedEndpointsHaveIndependentFallbacks() {
        val pool = listOf(
            "https://9.9.9.9/dns-query",
            "https://dns.adguard-dns.com/dns-query"
        )
        val config = JSONObject(
            build(linkProfile(VLESS_LINK), AppSettings(), resolverPool = pool).json
        )
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        val tags = (0 until servers.length()).map { servers.getJSONObject(it).optString("tag") }
        assertTrue(
            "the intelligence pool must reach the emitted DNS graph: $tags",
            tags.contains(SingBoxConfigBuilder.DNS_REMOTE_TAG) &&
                tags.contains(SingBoxConfigBuilder.DNS_DIRECT_TAG) &&
                tags.contains("dns-remote-0") &&
                tags.contains("dns-remote-1")
        )
    }

    @Test
    fun singboxOnlyProtocolsPassPreflightWhenTheyCarryAParserLink() {
        val tuic = ProxyProfile(
            id = "tuic-1",
            name = "TUIC",
            scheme = "tuic",
            raw = "tuic://uuid:password@198.51.100.7:443?sni=example.com#Node",
            configJson = "",
            host = "198.51.100.7",
            port = 443
        )
        val anytls = ProxyProfile(
            id = "anytls-1",
            name = "AnyTLS",
            scheme = "anytls",
            raw = "anytls://password@198.51.100.7:443?sni=example.com#Node",
            configJson = "",
            host = "198.51.100.7",
            port = 443
        )
        assertTrue(
            "TUIC has no Xray JSON shape but runs on sing-box extended",
            ProfilePreflightValidator.validate(tuic).valid
        )
        assertTrue(
            "AnyTLS has no Xray JSON shape but runs on sing-box extended",
            ProfilePreflightValidator.validate(anytls).valid
        )
    }

    @Test
    fun tlsFragmentUsesTheSupportedOneFourTlsField() {
        val profile = jsonProfile("vless").let {
            val root = JSONObject(it.configJson)
            root.getJSONArray("outbounds")
                .getJSONObject(0)
                .getJSONObject("streamSettings")
                .put("security", "tls")
                .put("tlsSettings", JSONObject().put("serverName", "example.com"))
            it.copy(configJson = root.toString())
        }
        val config = JSONObject(
            build(profile, AppSettings(singBoxPreferParser = false, fragmentEnabled = true)).json
        )
        val proxy = outbound(config, SingBoxConfigBuilder.PROXY_TAG)
        val tls = proxy.optJSONObject("tls")
        if (tls != null) {
            assertTrue("TLS fragment is supported by the pinned 1.14 core", tls.getBoolean("fragment"))
        }
    }

    /**
     * MARBLE_SINGBOX_AUTOPARSER_V154 — a vless/vmess node whose endpoint lives on `settings`
     * directly (no `vnext[]`) is exactly what PattNG exports and Marble imports for scheme `json`.
     * The old translator threw "no vnext server" for these, so the sing-box URL test painted an
     * otherwise-healthy server FAILED. The translator must accept the direct form.
     */
    @Test
    fun aDirectFormVlessWithoutVnextTranslatesInsteadOfThrowing() {
        val profile = directFormProfile(
            protocol = "vless",
            user = mapOf("id" to "11111111-2222-3333-4444-555555555555", "encryption" to "none"),
            extra = mapOf("flow" to "xtls-rprx-vision")
        )
        val build = build(profile) // must not throw "no vnext server"
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, build.strategy)
        val config = JSONObject(build.json)
        val proxy = outbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("vless", proxy.getString("type"))
        assertEquals("198.51.100.20", proxy.getString("server"))
        assertEquals(443, proxy.getInt("server_port"))
        assertEquals("11111111-2222-3333-4444-555555555555", proxy.getString("uuid"))
        assertEquals("xtls-rprx-vision", proxy.getString("flow"))
    }

    @Test
    fun aDirectFormVmessCarriesItsUdpPacketEncoding() {
        val profile = directFormProfile(
            protocol = "vmess",
            user = mapOf("id" to "11111111-2222-3333-4444-555555555555", "alterId" to "0"),
            extra = mapOf("packetEncoding" to "xudp")
        )
        val proxy = outbound(JSONObject(build(profile).json), SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("vmess", proxy.getString("type"))
        assertEquals("198.51.100.20", proxy.getString("server"))
        assertEquals("11111111-2222-3333-4444-555555555555", proxy.getString("uuid"))
        assertEquals("xudp", proxy.getString("packet_encoding"))
    }

    /**
     * A user object that only carries part of the account (for example `users[0]` with an id but
     * the alter-id on the server block) must not lose the whole node: every field is resolved
     * independently across `users[0]`, the server and the settings object.
     */
    @Test
    fun aPartiallyPopulatedUserResolvesEveryFieldInsteadOfDroppingTheNode() {
        val profile = ProxyProfile(
            id = "split-user",
            name = "Split user",
            scheme = "vmess",
            raw = "",
            configJson = JSONObject().put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vmess")
                        .put("tag", "proxy")
                        .put(
                            "settings",
                            JSONObject()
                                .put(
                                    "vnext",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("address", "198.51.100.20")
                                            .put("port", 443)
                                            .put(
                                                "users",
                                                JSONArray().put(
                                                    JSONObject().put(
                                                        "id", "11111111-2222-3333-4444-555555555555"
                                                    )
                                                )
                                            )
                                    )
                                )
                                .put("alterId", 64)
                        )
                )
            ).toString(),
            host = "198.51.100.20",
            port = 443
        )
        val proxy = outbound(JSONObject(build(profile).json), SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("198.51.100.20", proxy.getString("server"))
        assertEquals("11111111-2222-3333-4444-555555555555", proxy.getString("uuid"))
        assertEquals(64, proxy.getInt("alter_id"))
    }

    private fun directFormProfile(
        protocol: String,
        user: Map<String, String>,
        extra: Map<String, String> = emptyMap()
    ): ProxyProfile {
        val settings = JSONObject().put("address", "198.51.100.20").put("port", 443)
        user.forEach { (k, v) -> settings.put(k, v) }
        extra.forEach { (k, v) -> settings.put(k, v) }
        val outbound = JSONObject()
            .put("protocol", protocol)
            .put("tag", "proxy")
            .put("settings", settings)
        return ProxyProfile(
            id = "direct-$protocol",
            name = "Direct $protocol",
            scheme = protocol,
            raw = "",
            configJson = JSONObject().put(
                "outbounds",
                JSONArray().put(outbound)
            ).toString(),
            host = "198.51.100.20",
            port = 443
        )
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
