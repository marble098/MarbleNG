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
 * MARBLE_SINGBOX_AUTOPARSER_V154 — the automatic Xray → sing-box converter, pinned by test.
 *
 * The converter contract is what stands between a subscription and a red row: every shape Xray
 * can express — the `vnext` arrays, MarbleNG's own direct form, WS's three Host spellings,
 * REALITY's two key names, hysteria v1/v2 disambiguation, WireGuard, TUIC/AnyTLS link-only
 * nodes — must become a valid sing-box outbound, and a rejected candidate must always have a
 * second one ready ([SingBoxConfigBuilder.candidateBuilds]).
 *
 * Nothing here spawns a process; JSON in ⇒ JSON out is the contract.
 */
class SingBoxAutoparserV154Test {

    private fun outbound(config: JSONObject, tag: String = SingBoxConfigBuilder.PROXY_TAG): JSONObject {
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.getJSONObject(i)
            if (candidate.optString("tag") == tag) return candidate
        }
        error("missing outbound $tag")
    }

    private fun profileOf(
        raw: String,
        scheme: String = raw.substringBefore("://"),
        configJson: String = "",
        host: String = "198.51.100.7",
        port: Int = 443
    ) = ProxyProfile(
        id = "node",
        name = "Node",
        scheme = scheme,
        raw = raw,
        configJson = configJson,
        host = host,
        port = port
    )

    private fun jsonWith(protocol: String, settings: JSONObject, stream: JSONObject = JSONObject()): String =
        JSONObject().put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("protocol", protocol)
                    .put("tag", "proxy")
                    .put("settings", settings)
                    .put("streamSettings", stream)
            )
        ).toString()

    private fun build(profile: ProxyProfile, settings: AppSettings = AppSettings(
        singBoxPreferParser = false
    )): JSONObject = JSONObject(
        SingBoxConfigBuilder.build(
            profile = profile,
            settings = settings,
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "/data/local/tmp/singbox.log",
            cachePath = "/data/local/tmp/singbox-cache.db"
        ).json
    )

    // ─── direct-form emitters: the shape MarbleNG itself stores ────────────────────────────

    @Test
    fun directFormVlessTranslatesServerUuidAndWsHost() {
        val profile = profileOf(
            raw = "",
            scheme = "vless",
            configJson = jsonWith(
                "vless",
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put("id", "11111111-2222-3333-4444-555555555555")
                    .put("encryption", "none")
                    .put("flow", ""),
                JSONObject()
                    .put("method", "websocket")
                    .put("security", "tls")
                    .put("wsSettings", JSONObject().put("path", "/ws").put("host", "cdn.example.com"))
                    .put("tlsSettings", JSONObject().put("serverName", "sni.example.com"))
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("vless", proxy.getString("type"))
        assertEquals("198.51.100.7", proxy.getString("server"))
        assertEquals(443, proxy.getInt("server_port"))
        assertEquals("11111111-2222-3333-4444-555555555555", proxy.getString("uuid"))
        assertEquals("none", proxy.getString("encryption"))
        val transport = proxy.getJSONObject("transport")
        assertEquals("ws", transport.getString("type"))
        assertEquals("/ws", transport.getString("path"))
        assertEquals(
            "cdn.example.com",
            transport.getJSONObject("headers").getString("Host")
        )
        assertEquals("sni.example.com", proxy.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun mixedShapesResolvePerFieldSoOneLostFieldNeverDropsTheNode() {
        // vnext names the server; users[0] is missing entirely, so the direct form has to
        // supply the id. The old reader errored as soon as `users` was absent.
        val profile = profileOf(
            raw = "",
            scheme = "vless",
            configJson = jsonWith(
                "vless",
                JSONObject()
                    .put("address", "")
                    .put("id", "22222222-3333-4444-5555-666666666666")
                    .put(
                        "vnext",
                        JSONArray().put(
                            JSONObject().put("address", "203.0.113.9").put("port", 8443)
                        )
                    )
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("203.0.113.9", proxy.getString("server"))
        assertEquals(8443, proxy.getInt("server_port"))
        assertEquals("22222222-3333-4444-5555-666666666666", proxy.getString("uuid"))
    }

    @Test
    fun realityKeyStoredAsPasswordReachesThePublicKeyField() {
        val profile = profileOf(
            raw = "",
            scheme = "vless",
            configJson = jsonWith(
                "vless",
                JSONObject()
                    .put("address", "198.51.100.7").put("port", 443)
                    .put("id", "11111111-2222-3333-4444-555555555555")
                    .put("flow", "xtls-rprx-vision"),
                JSONObject()
                    .put("security", "reality")
                    .put(
                        "realitySettings",
                        JSONObject()
                            .put("serverName", "dl.google.com")
                            .put("password", "PUBLIC-KEY-FROM-PBK")
                            .put("shortId", "abcd0123")
                    )
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("xtls-rprx-vision", proxy.getString("flow"))
        val reality = proxy.getJSONObject("tls").getJSONObject("reality")
        assertEquals("PUBLIC-KEY-FROM-PBK", reality.getString("public_key"))
        assertEquals("abcd0123", reality.getString("short_id"))
    }

    // ─── hysteria: v1 vs v2 and Marble's settings-level storage ────────────────────────────

    @Test
    fun storedHysteria2ShapeTranslatesAuthBeyondTheStreamBlock() {
        // Exactly what ProxyParser.parseHy2 stores: protocol "hysteria" with version 2, the
        // endpoint at settings level, auth in streamSettings.hysteriaSettings.
        val profile = profileOf(
            raw = "hy2://top-secret@hy2.example.com:443?sni=cdn.example.com",
            scheme = "hysteria2",
            configJson = jsonWith(
                "hysteria",
                JSONObject()
                    .put("version", 2)
                    .put("address", "hy2.example.com")
                    .put("port", 443),
                JSONObject()
                    .put("method", "hysteria")
                    .put("security", "tls")
                    .put("tlsSettings", JSONObject().put("serverName", "cdn.example.com"))
                    .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", "top-secret"))
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("hysteria2", proxy.getString("type"))
        assertEquals("hy2.example.com", proxy.getString("server"))
        assertEquals(443, proxy.getInt("server_port"))
        assertEquals("top-secret", proxy.getString("password"))
        assertEquals("cdn.example.com", proxy.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun hysteriaRatesArePickedEvenFromHumanStrings() {
        val profile = profileOf(
            raw = "",
            scheme = "hysteria2",
            configJson = jsonWith(
                "hysteria",
                JSONObject()
                    .put("version", 2)
                    .put("address", "hy2.example.com")
                    .put("port", 443)
                    .put("up_mbps", "100 Mbps")
                    .put("down_mbps", "200"),
                JSONObject().put(
                    "hysteriaSettings",
                    JSONObject().put("auth", "top-secret")
                )
            )
        )
        val proxy = outbound(build(profile))
        assertEquals(100, proxy.getInt("up_mbps"))
        assertEquals(200, proxy.getInt("down_mbps"))
        assertEquals("top-secret", proxy.getString("password"))
    }

    @Test
    fun hysteriaV2ObfsFromMarbleFinalmaskBecomesSalamander() {
        val profile = profileOf(
            raw = "",
            scheme = "hysteria2",
            configJson = jsonWith(
                "hysteria",
                JSONObject()
                    .put("version", 2)
                    .put("auth", "top-secret")
                    .put("server", "hy2.example.com")
                    .put("port", 443),
                JSONObject().put(
                    "finalmask",
                    JSONObject().put(
                        "udp",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "salamander")
                                .put("settings", JSONObject().put("password", "obfs-pass"))
                        )
                    )
                )
            )
        )
        val proxy = outbound(build(profile))
        val obfs = proxy.getJSONObject("obfs")
        assertEquals("salamander", obfs.getString("type"))
        assertEquals("obfs-pass", obfs.getString("password"))
    }

    @Test
    fun v1HysteriaGetsForcedRateDefaultsBecauseTheCoreRequiresThem() {
        val profile = profileOf(
            raw = "",
            scheme = "hysteria",
            configJson = jsonWith(
                "hysteria",
                JSONObject()
                    .put("address", "hy1.example.com")
                    .put("port", 443)
                    .put("auth_str", "hy1-auth"),
                JSONObject().put("method", "hysteria").put("security", "tls")
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("hysteria", proxy.getString("type"))
        assertEquals("hy1-auth", proxy.getString("auth_str"))
        assertTrue("sing-box hysteria v1 requires up_mbps", proxy.getInt("up_mbps") > 0)
        assertTrue("sing-box hysteria v1 requires down_mbps", proxy.getInt("down_mbps") > 0)
    }

    @Test
    fun hysteria2ProtocolNameKeepsTheV2Shape() {
        // Xray JSON can spell a v2 node `protocol: "hysteria2"` — never let the v1 rule typed by
        // the shorter name's default turn it into an auth mismatch.
        val profile = profileOf(
            raw = "",
            scheme = "hy2",
            configJson = jsonWith(
                "hysteria2",
                JSONObject()
                    .put("address", "hy2.example.com")
                    .put("port", 443)
                    .put("password", "v2-password")
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("hysteria2", proxy.getString("type"))
        assertEquals("v2-password", proxy.getString("password"))
    }

    // ─── wireguard ─────────────────────────────────────────────────────────────────────────

    @Test
    fun fullXrayWireGuardOutboundTranslatesPeerKeysAndAddresses() {
        val profile = profileOf(
            raw = "",
            scheme = "wireguard",
            configJson = jsonWith(
                "wireguard",
                JSONObject()
                    .put("secretKey", "yAnzprivatekey00000000000000000000000000000=")
                    .put("address", JSONArray().put("172.16.0.2/32").put("2606:4700:110:8f81::2/128"))
                    .put("mtu", 1280)
                    .put(
                        "peers",
                        JSONArray().put(
                            JSONObject()
                                .put("publicKey", "bmXOCpeerkey0000000000000000000000000000000=")
                                .put("preSharedKey", "psk0000000000000000000000000000000000000=")
                                .put("endpoint", "engage.cloudflareclient.com:2408")
                                .put("keepAlive", 16)
                        )
                    )
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("wireguard", proxy.getString("type"))
        assertEquals("engage.cloudflareclient.com", proxy.getString("server"))
        assertEquals(2408, proxy.getInt("server_port"))
        assertEquals("yAnzprivatekey00000000000000000000000000000=", proxy.getString("private_key"))
        assertEquals(
            "bmXOCpeerkey0000000000000000000000000000000=",
            proxy.getString("peer_public_key")
        )
        assertEquals(
            "psk0000000000000000000000000000000000000=",
            proxy.getString("pre_shared_key")
        )
        assertEquals(2, proxy.getJSONArray("local_address").length())
        assertEquals(1280, proxy.getInt("mtu"))
    }

    // ─── tuic / anytls: raw-link translation ───────────────────────────────────────────────

    @Test
    fun tuicLinkOnlyNodeTranslatesToTheNativeOutbound() {
        val profile = profileOf(
            raw = "tuic://11111111-2222-3333-4444-555555555555:secretpass@tuic.example.com:4443" +
                "?sni=cdn.example.com&congestion_control=bbr&alpn=h3#TUIC Node",
            host = "tuic.example.com",
            port = 4443
        )
        val support = SingBoxConfigBuilder.describe(
            profile,
            AppSettings(singBoxPreferParser = false)
        )
        assertTrue(support.supported)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, support.strategy)

        val proxy = outbound(build(profile))
        assertEquals("tuic", proxy.getString("type"))
        assertEquals("tuic.example.com", proxy.getString("server"))
        assertEquals(4443, proxy.getInt("server_port"))
        assertEquals("11111111-2222-3333-4444-555555555555", proxy.getString("uuid"))
        assertEquals("secretpass", proxy.getString("password"))
        assertEquals("bbr", proxy.getString("congestion_control"))
        val tls = proxy.getJSONObject("tls")
        assertEquals("cdn.example.com", tls.getString("server_name"))
        assertEquals("h3", tls.getJSONArray("alpn").getString(0))
    }

    @Test
    fun anytlsLinkOnlyNodeTranslatesWithInsecureFlag() {
        val profile = profileOf(
            raw = "anytls://superpass@any.example.com:8443?sni=sni.example.com" +
                "&insecure=1#AnyTLS Node",
            host = "any.example.com",
            port = 8443
        )
        val proxy = outbound(build(profile))
        assertEquals("anytls", proxy.getString("type"))
        assertEquals("any.example.com", proxy.getString("server"))
        assertEquals(8443, proxy.getInt("server_port"))
        assertEquals("superpass", proxy.getString("password"))
        val tls = proxy.getJSONObject("tls")
        assertEquals("sni.example.com", tls.getString("server_name"))
        assertTrue(tls.getBoolean("insecure"))
    }

    @Test
    fun linkOnlyNodeWithoutAnyLinkIsReportedInsteadOfGuessed() {
        val profile = profileOf(raw = "", scheme = "tuic")
        val support = SingBoxConfigBuilder.describe(
            profile,
            AppSettings(singBoxPreferParser = false)
        )
        assertFalse(support.supported)
        assertTrue(support.reason.isNotBlank())
    }

    // ─── small shapes that broke silently before ───────────────────────────────────────────

    @Test
    fun shadowsocksUotBecomesSingBoxUdpOverTcp() {
        val profile = profileOf(
            raw = "",
            scheme = "ss",
            configJson = jsonWith(
                "shadowsocks",
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put("method", "aes-128-gcm")
                    .put("password", "password")
                    .put("uot", true)
            )
        )
        val proxy = outbound(build(profile))
        assertTrue(proxy.getJSONObject("udp_over_tcp").getBoolean("enabled"))
    }

    @Test
    fun grpcMultiModeCamelCaseLiteralReachesPermitWithoutStream() {
        val profile = profileOf(
            raw = "",
            scheme = "vless",
            configJson = jsonWith(
                "vless",
                JSONObject()
                    .put("address", "198.51.100.7").put("port", 443)
                    .put("id", "11111111-2222-3333-4444-555555555555"),
                JSONObject()
                    .put("network", "grpc")
                    .put("security", "tls")
                    .put(
                        "grpcSettings",
                        JSONObject().put("serviceName", "svc").put("multiMode", true)
                    )
            )
        )
        val proxy = outbound(build(profile))
        val transport = proxy.getJSONObject("transport")
        assertEquals("grpc", transport.getString("type"))
        assertEquals("svc", transport.getString("service_name"))
        assertTrue(transport.getBoolean("permit_without_stream"))
    }

    @Test
    fun vmessPacketEncodingTranslatesToSingBoxPacketEncoding() {
        val profile = profileOf(
            raw = "",
            scheme = "vmess",
            configJson = jsonWith(
                "vmess",
                JSONObject()
                    .put("address", "198.51.100.7").put("port", 443)
                    .put("id", "11111111-2222-3333-4444-555555555555")
                    .put("packetEncoding", "xudp")
            )
        )
        val proxy = outbound(build(profile))
        assertEquals("vmess", proxy.getString("type"))
        assertEquals("xudp", proxy.getString("packet_encoding"))
    }

    @Test
    fun quicFamilyNodesNeverCarryMultiplexBecauseTheCoreRefusesIt() {
        val profile = profileOf(
            raw = "",
            scheme = "wireguard",
            configJson = jsonWith(
                "wireguard",
                JSONObject()
                    .put("secretKey", "privatekey")
                    .put("address", "172.16.0.2/32")
                    .put(
                        "peers",
                        JSONArray().put(
                            JSONObject()
                                .put("publicKey", "peerkey")
                                .put("endpoint", "198.51.100.7:2408")
                        )
                    ),
                JSONObject().put("mux", JSONObject().put("enabled", true))
            )
        )
        val proxy = outbound(build(profile, AppSettings(
            singBoxPreferParser = false,
            muxEnabled = true
        )))
        assertFalse("wireguard may never ask for multiplex", proxy.has("multiplex"))
    }

    // ─── the candidate net: one refusal can never kill a node again ────────────────────────

    @Test
    fun candidateBuildsOffersBothReadersForALinkCarryingNode() {
        val profile = profileOf(
            raw = "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443?security=tls#N",
            scheme = "vless",
            configJson = jsonWith(
                "vless",
                JSONObject()
                    .put("address", "198.51.100.7").put("port", 443)
                    .put("id", "11111111-2222-3333-4444-555555555555")
            )
        )
        val prefer = SingBoxConfigBuilder.candidateBuilds(
            profile = profile,
            settings = AppSettings(singBoxPreferParser = true),
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "/tmp/a.log",
            cachePath = "/tmp/a.db"
        )
        val translatedFirst = SingBoxConfigBuilder.candidateBuilds(
            profile = profile,
            settings = AppSettings(singBoxPreferParser = false),
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "/tmp/a.log",
            cachePath = "/tmp/a.db"
        )
        assertEquals(
            listOf(SingBoxConfigBuilder.STRATEGY_LINK, SingBoxConfigBuilder.STRATEGY_TRANSLATED),
            prefer.map { it.strategy }
        )
        assertEquals(
            listOf(SingBoxConfigBuilder.STRATEGY_TRANSLATED, SingBoxConfigBuilder.STRATEGY_LINK),
            translatedFirst.map { it.strategy }
        )
    }

    @Test
    fun candidateBuildsFallsBackCleanlyWhenAReaderCannotServe() {
        val jsonOnly = profileOf(
            raw = "",
            scheme = "vless",
            configJson = jsonWith(
                "vless",
                JSONObject()
                    .put("address", "198.51.100.7").put("port", 443)
                    .put("id", "11111111-2222-3333-4444-555555555555")
            )
        )
        val candidates = SingBoxConfigBuilder.candidateBuilds(
            profile = jsonOnly,
            settings = AppSettings(),
            socksPort = 10808, apiPort = 39090, apiSecret = "s",
            logPath = "/tmp/a.log", cachePath = "/tmp/a.db"
        )
        assertEquals(listOf(SingBoxConfigBuilder.STRATEGY_TRANSLATED), candidates.map { it.strategy })

        val impossible = profileOf(
            raw = "",
            scheme = "dokodemo-door",
            configJson = jsonWith(
                "dokodemo-door",
                JSONObject().put("address", "198.51.100.7").put("port", 443)
            )
        )
        assertTrue(
            SingBoxConfigBuilder.candidateBuilds(
                profile = impossible,
                settings = AppSettings(singBoxPreferParser = false),
                socksPort = 10808, apiPort = 39090, apiSecret = "s",
                logPath = "/tmp/a.log", cachePath = "/tmp/a.db"
            ).isEmpty()
        )
    }

    @Test
    fun tuicAndAnytlsNodesAlwaysRunEvenWhenTheStoredConfigIsBlank() {
        // Regression guard for the whole V154 premise: a link-only sing-box node must describe
        // as supported AND build on BOTH strategies, so a stale core parser never kills it.
        listOf(
            profileOf(raw = "tuic://u:p@198.51.100.7:443?sni=a.b#T"),
            profileOf(raw = "anytls://p@198.51.100.7:443?sni=a.b#A")
        ).forEach { profile ->
            listOf(true, false).forEach { prefer ->
                val support = SingBoxConfigBuilder.describe(
                    profile,
                    AppSettings(singBoxPreferParser = prefer)
                )
                assertTrue("describe must promise the node (prefer=$prefer)", support.supported)
                val candidates = SingBoxConfigBuilder.candidateBuilds(
                    profile = profile,
                    settings = AppSettings(singBoxPreferParser = prefer),
                    socksPort = 10808, apiPort = 39090, apiSecret = "s",
                    logPath = "/tmp/a.log", cachePath = "/tmp/a.db"
                )
                assertEquals(
                    "both readers must be able to run a link-only node (prefer=$prefer)",
                    2,
                    candidates.size
                )
            }
        }
    }

    @Test
    fun unknownProtocolsStillRefuseLoudlyRatherThanFakingSupport() {
        val profile = profileOf(
            raw = "",
            scheme = "flux",
            configJson = jsonWith("flux", JSONObject())
        )
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse(support.supported)
        assertTrue("the reason must name the protocol", support.reason.contains("flux"))
    }
}
