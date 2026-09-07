package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.DelayTest
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress

class CoreInteropRegressionTest {
    private val plain = AppSettings(routingMode = RoutingMode.PROXY_ALL, routeBlockAds = false, routeBypassPrivate = false)
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private fun profile(outbound: JSONObject, extra: List<JSONObject> = emptyList()) = ProxyProfile(
        "test", "Synthetic node", "vless", "", JSONObject().put("outbounds", JSONArray(listOf(outbound) + extra)).toString(),
        "192.0.2.1", 443
    )
    private fun vless(): JSONObject = JSONObject().put("protocol", "vless").put("tag", "proxy")
        .put("settings", JSONObject().put("address", "192.0.2.1").put("port", 443).put("id", uuid).put("encryption", "none"))
    private fun build(p: ProxyProfile, settings: AppSettings = plain, pool: List<String> = emptyList()): JSONObject =
        JSONObject(SingBoxConfigBuilder.build(p, settings, 10808, 39090, "test-secret", "", "cache.db",
            resolverPool = pool, bootstrapDnsPort = 53530).json)
    private fun proxy(config: JSONObject): JSONObject = NativeSingBoxConfig.objects(config.getJSONArray("outbounds"))
        .single { it.optString("tag") == SingBoxConfigBuilder.PROXY_TAG }

    @Test fun sampleShapePreservesRealityPasswordAndXhttpExtra() {
        // Same schema as the report, but documentation IPs and synthetic credentials only.
        val outbound = vless().put("streamSettings", JSONObject().put("method", "xhttp").put("security", "reality")
            .put("realitySettings", JSONObject().put("serverName", "example.com").put("fingerprint", "chrome")
                .put("password", publicKey).put("shortId", "806b49dd").put("spiderX", "/test"))
            .put("xhttpSettings", JSONObject().put("path", "/").put("mode", "auto").put("extra", JSONObject()
                .put("mode", "auto").put("xPaddingBytes", "100-1000").put("xPaddingObfsMode", true))))
        val result = proxy(build(profile(outbound)))
        assertEquals(publicKey, result.getJSONObject("tls").getJSONObject("reality").getString("public_key"))
        assertEquals("806b49dd", result.getJSONObject("tls").getJSONObject("reality").getString("short_id"))
        assertEquals("100-1000", result.getJSONObject("transport").getString("x_padding_bytes"))
        assertTrue(result.getJSONObject("transport").getBoolean("x_padding_obfs_mode"))
        assertTrue(ProfilePreflightValidator.validate(profile(outbound)).valid)
    }

    @Test fun xhttpHasRequiredPaddingAndDoesNotConfuseExtraPrecedence() {
        val transport = JSONObject().put("mode", "stream-up").put("path", "/main")
            .put("extra", JSONObject().put("mode", "packet-up").put("path", "/ignored")
                .put("xmux", JSONObject().put("maxConcurrency", "16-32")))
        val result = proxy(build(profile(vless().put("streamSettings", JSONObject().put("network", "xhttp")
            .put("xhttpSettings", transport))))).getJSONObject("transport")
        assertEquals("stream-up", result.getString("mode"))
        assertEquals("/main", result.getString("path"))
        assertEquals("100-1000", result.getString("x_padding_bytes"))
        assertEquals("16-32", result.getJSONObject("xmux").getString("max_concurrency"))
    }

    @Test fun websocketHeadersAreNotReducedToHost() {
        val ws = JSONObject().put("path", "/ws").put("headers", JSONObject().put("Host", "example.com").put("X-Token", "test"))
        val result = proxy(build(profile(vless().put("streamSettings", JSONObject().put("network", "ws").put("wsSettings", ws)))))
        assertEquals("test", result.getJSONObject("transport").getJSONObject("headers").getString("X-Token"))
    }

    @Test fun mkcpIsExtendedTransportNotSilentTcpFallback() {
        val kcp = JSONObject().put("mtu", 1280).put("seed", "test").put("uplinkCapacity", 8)
            .put("header", JSONObject().put("type", "srtp"))
        val result = proxy(build(profile(vless().put("streamSettings", JSONObject().put("network", "kcp").put("kcpSettings", kcp)))))
        val transport = result.getJSONObject("transport")
        assertEquals("mkcp", transport.getString("type"))
        assertEquals("srtp", transport.getString("header_type"))
        assertEquals(8, transport.getInt("uplink_capacity"))
    }

    @Test fun unsupportedTransportAndSecurityCannotDisappearDuringDescribe() {
        listOf(
            JSONObject().put("network", "unknown-wire-protocol"),
            JSONObject().put("network", "xhttp").put("xhttpSettings", JSONObject().put("extra", JSONObject().put("unknownWireField", true))),
            JSONObject().put("security", "tls").put("tlsSettings", JSONObject().put("pinnedPeerCertSha256", "ab".repeat(32))),
            JSONObject().put("security", "reality").put("realitySettings", JSONObject().put("serverName", "example.com"))
        ).forEach { stream ->
            val support = SingBoxConfigBuilder.describe(profile(vless().put("streamSettings", stream)), plain)
            assertFalse(support.toString(), support.supported)
            assertTrue(support.reason, support.reason.startsWith("config-unsupported:"))
        }
    }

    @Test fun xrayChainEdgesPointFromEntryTowardTheOuterHop() {
        val entry = vless().put("streamSettings", JSONObject().put("sockopt", JSONObject().put("dialerProxy", "outer")))
        val outer = JSONObject().put("protocol", "socks").put("tag", "outer").put("settings", JSONObject()
            .put("address", "192.0.2.2").put("port", 1080).put("user", "user").put("pass", "test"))
        val config = build(profile(entry, listOf(outer)))
        assertEquals("marble-hop-1", proxy(config).getString("detour"))
        val hop = config.getJSONArray("outbounds").getJSONObject(1)
        assertEquals("marble-hop-1", hop.getString("tag"))
        assertFalse(hop.has("detour"))
        assertEquals("user", hop.getString("username"))
    }

    @Test fun missingAndCyclicChainHopsAreVisibleConfigErrors() {
        val entry = vless().put("streamSettings", JSONObject().put("sockopt", JSONObject().put("dialerProxy", "missing")))
        assertFalse(SingBoxConfigBuilder.describe(profile(entry), plain).supported)
        entry.getJSONObject("streamSettings").getJSONObject("sockopt").put("dialerProxy", "proxy")
        assertFalse(SingBoxConfigBuilder.describe(profile(entry), plain).supported)
    }

    @Test fun uuidAliasAndTopLevelUserFieldsAreResolvedIndependently() {
        val outbound = vless()
        outbound.getJSONObject("settings").remove("id")
        outbound.getJSONObject("settings").put("uuid", uuid)
        assertEquals(uuid, proxy(build(profile(outbound))).getString("uuid"))
    }

    @Test fun xrayHysteriaVersionTwoAndSalamanderStayVersionTwo() {
        val outbound = JSONObject().put("protocol", "hysteria").put("tag", "proxy")
            .put("settings", JSONObject().put("version", 2).put("address", "192.0.2.1").put("port", 443))
            .put("streamSettings", JSONObject().put("method", "hysteria").put("security", "tls")
                .put("tlsSettings", JSONObject().put("serverName", "example.com").put("fingerprint", "chrome"))
                .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", "test"))
                .put("finalmask", JSONObject().put("udp", JSONArray().put(JSONObject().put("type", "salamander")
                    .put("settings", JSONObject().put("password", "mask"))))))
        val result = proxy(build(profile(outbound)))
        assertEquals("hysteria2", result.getString("type"))
        assertEquals("test", result.getString("password"))
        assertEquals("mask", result.getJSONObject("obfs").getString("password"))
        assertFalse(result.getJSONObject("tls").has("utls"))
    }

    @Test fun duplicateResolversUseAnActiveBoundedFallbackGraph() {
        val config = build(profile(vless()), plain.copy(dnsPrimaryDoH = "https://1.1.1.1/dns-query", dnsSecondaryDoH = "https://1.1.1.1/dns-query"),
            listOf("tls://[2001:db8::1]:8853", "https://dns.example.com:8443/custom", "tls://[2001:db8::1]:8853"))
        val servers = NativeSingBoxConfig.objects(config.getJSONObject("dns").getJSONArray("servers"))
        val fallback = servers.single { it.getString("tag") == SingBoxConfigBuilder.DNS_REMOTE_TAG }
        assertEquals("fallback", fallback.getString("type"))
        assertEquals(2, fallback.getJSONArray("servers").length())
        assertEquals("2001:db8::1", servers.single { it.getString("tag") == "dns-remote-0" }.getString("server"))
        assertEquals(8853, servers.single { it.getString("tag") == "dns-remote-0" }.getInt("server_port"))
        assertEquals("/custom", servers.single { it.getString("tag") == "dns-remote-1" }.getString("path"))
        assertEquals(8443, servers.single { it.getString("tag") == "dns-remote-1" }.getInt("server_port"))
        assertEquals("dns-local", servers.single { it.getString("tag") == "dns-remote-1" }.getString("domain_resolver"))
        assertEquals("marble-proxy", servers.single { it.getString("tag") == "dns-remote-0" }.getString("detour"))
        assertEquals("udp", servers.single { it.getString("tag") == "dns-local" }.getString("type"))
        assertEquals(53530, servers.single { it.getString("tag") == "dns-local" }.getInt("server_port"))
    }

    @Test fun offlineRoutingHasNoStartupDownloadOrLegacyAddressFilter() {
        val config = build(profile(vless()), AppSettings())
        NativeSingBoxConfig.objects(config.getJSONObject("route").getJSONArray("rule_set")).forEach {
            assertEquals("local", it.getString("type"))
            assertTrue(it.getString("path").endsWith(".srs"))
            assertFalse(it.has("url"))
        }
        assertFalse(config.getJSONObject("dns").getJSONArray("rules").toString().contains("geoip"))
        assertFalse(config.toString().contains("independent_cache"))
        assertFalse(config.toString().contains("auto_detect_interface"))
    }

    @Test fun nativeImportAndReverseTranslationPreserveTheSelectedChain() {
        val root = JSONObject().put("outbounds", JSONArray()
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "vless").put("tag", "exit").put("server", "192.0.2.1")
                .put("server_port", 443).put("uuid", uuid).put("detour", "outer")
                .put("tls", JSONObject().put("enabled", true).put("server_name", "example.com")
                    .put("reality", JSONObject().put("enabled", true).put("public_key", publicKey).put("short_id", "1234"))))
            .put(JSONObject().put("type", "socks").put("tag", "outer").put("server", "192.0.2.2").put("server_port", 1080)))
            .put("route", JSONObject().put("final", "exit"))
        val imported = ProxyParser.parseInput(root.toString()).single()
        assertTrue(ProfilePreflightValidator.validate(imported).valid)
        val config = build(imported)
        assertEquals("import-outer", proxy(config).getString("detour"))
        val xray = XrayConfigAdapter.document(imported.configJson)
        val out = xray.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", out.getString("protocol"))
        assertEquals("outer", out.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
        assertEquals(publicKey, out.getJSONObject("streamSettings").getJSONObject("realitySettings").getString("password"))
        assertEquals(root.toString(), imported.configJson)
    }

    @Test fun coreExclusiveNativeProtocolsAndPinsAreNotFakedForXray() {
        listOf(
            JSONObject().put("type", "tuic").put("tag", "proxy").put("server", "192.0.2.1").put("server_port", 443),
            JSONObject().put("type", "vless").put("tag", "proxy").put("server", "192.0.2.1").put("server_port", 443).put("uuid", uuid)
                .put("tls", JSONObject().put("enabled", true).put("certificate_public_key_sha256", JSONArray().put("test")))
        ).forEach { outbound ->
            assertThrows(ConfigTranslationException::class.java) { XrayConfigAdapter.document(NativeSingBoxConfig.root(outbound).toString()) }
        }
    }

    @Test fun bootstrapCodecPreservesTransactionQuestionAndFamily() {
        val query = DnsWireCodec.buildQuery("example.com")
        query[0] = 0x7a
        val response = DnsBootstrapCodec.answer(query, listOf(InetAddress.getByName("192.0.2.4")))!!
        assertEquals(0x7a.toByte(), response[0])
        assertEquals("192.0.2.4", DnsWireCodec.parseAnswers(response).single().hostAddress)
        assertEquals(2, DnsBootstrapCodec.error(query, 2)!![3].toInt() and 15)
        assertNull(DnsBootstrapCodec.question(byteArrayOf(0, 1)))
        val pointer = query.clone().apply { this[12] = 0xc0.toByte(); this[13] = 12 }
        assertNull(DnsBootstrapCodec.question(pointer))
    }

    @Test fun diagnosticsTailDoesNotReadAnEntireRetainedLog() {
        val file = File.createTempFile("core-log-", ".log")
        try {
            file.writeText("x".repeat(1_000_000) + "failure-at-end")
            val tail = SingBoxProcessSession.tail(file, 1024)
            assertEquals(1024, tail.length)
            assertTrue(tail.endsWith("failure-at-end"))
        } finally { file.delete() }
    }

    @Test fun certificateMismatchIsNotReportedAsExpiryAndPeerResetIsNotTeardown() {
        assertEquals(ResolverFailureKind.TLS, ResolverFailureClassifier.classify("dns: tls: failed to verify certificate: wrong name"))
        assertEquals(ResolverFailureKind.DEADLINE, ResolverFailureClassifier.classify("dns: TLS handshake timeout"))
        assertEquals(ResolverFailureKind.OTHER, ResolverFailureClassifier.classify("dns: read: connection reset by peer"))
    }
    // MARBLE_URLTEST_SINGBOX_ONLY_V156 — the URL test is sing-box extended's own measurement, so
    // the target contract lives with the session that asks the core, not with a second HTTP
    // client. What is pinned is the part that protects the user: no plain http (the core would
    // silently substitute its own target) and never a URL carrying credentials.
    @Test fun urlTestAcceptsOnlyAPlainHttpsTarget() {
        assertNull(UrlTestTarget.validate("https://example.com:8443/check?token=kept"))
        assertNull(UrlTestTarget.validate(DelayTest.URL))
        assertEquals(UrlTestTarget.INVALID, UrlTestTarget.validate("http://example.com/generate_204"))
        assertEquals(UrlTestTarget.INVALID, UrlTestTarget.validate("https://user:pw@example.com/"))
        assertEquals(UrlTestTarget.INVALID, UrlTestTarget.validate("https://"))
        assertEquals(UrlTestTarget.INVALID, UrlTestTarget.validate("not a url at all"))
        assertTrue(CoreFailurePolicy.isLocal(UrlTestTarget.INVALID))
    }

    // The method belongs to one engine now: on any other it says so instead of measuring
    // something else behind the same name.
    @Test fun urlTestRefusesAnEngineThatDoesNotOwnIt() {
        RouteProbe.urlTestHook = { _, _, _ ->
            RouteProbe.ProbeResult(RouteProbe.METHOD_URL_TEST, 42.0, 100, 1)
        }
        try {
            val onXray = RouteProbe.urlTest(profile(vless()), 1000, plain)
            assertEquals(0, onXray.successPercent)
            assertEquals(RouteProbe.URL_TEST_ENGINE_GATE, onXray.failureReason)

            val onSingBox = RouteProbe.urlTest(
                profile(vless()), 1000, plain.copy(coreEngineId = CoreEngine.SINGBOX.id)
            )
            assertEquals(100, onSingBox.successPercent)
            assertEquals(42.0, onSingBox.latencyMs, 0.001)
        } finally {
            RouteProbe.urlTestHook = null
        }
    }

    @Test fun localFaultsAndAbsentTunnelDoNotPretendToMeasureServerHealth() {
        assertTrue(CoreFailurePolicy.isLocal("core-config: invalid DNS field"))
        assertTrue(CoreFailurePolicy.isLocal("config-unsupported: tls pin"))
        assertFalse(CoreFailurePolicy.isLocal("urltest-http-503: protocol handshake failed"))
        val measured = RouteProbe.realDelay(profile(vless()), 0, 1000, 2, plain)
        assertEquals(0, measured.successPercent)
        assertEquals("no-live-tunnel", measured.failureReason)
    }

}
