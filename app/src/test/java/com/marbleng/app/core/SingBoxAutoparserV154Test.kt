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
 * MARBLE_SINGBOX_AUTOPARSER_V154 — "a server that is alive never reports FAILED", pinned at the
 * layer that can be tested without a device: the candidate set.
 *
 * The whole promise of this release is that a profile is carried by **every** reader that can
 * express it, in preference order: the core's own link parser and Marble's own translation of the
 * stored Xray JSON. [SingBoxManager] walks the list and the first config the core accepts wins,
 * so a single refusal can never kill a node. Nothing here spawns a process; the JSON is the
 * contract.
 */
class SingBoxAutoparserV154Test {

    private fun profile(scheme: String, raw: String = "", configJson: String = "") = ProxyProfile(
        id = "node-1",
        name = "Node 1",
        scheme = scheme,
        raw = raw,
        configJson = configJson,
        host = "198.51.100.7",
        port = 443
    )

    /** One Xray-shaped outbound in a stored config: protocol + settings (+ streamSettings). */
    private fun xrayProfile(
        protocol: String,
        settings: JSONObject,
        stream: JSONObject? = null
    ): ProxyProfile = profile(
        scheme = protocol,
        configJson = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", protocol)
                        .put("tag", "proxy")
                        .put("settings", settings)
                        .apply { if (stream != null) put("streamSettings", stream) }
                )
            )
            .toString()
    )

    private fun build(profile: ProxyProfile, settings: AppSettings = AppSettings()): SingBoxConfigBuilder.Build =
        SingBoxConfigBuilder.build(
            profile, settings, 10808, 39090, "secret",
            "/data/local/tmp/singbox.log", "/data/local/tmp/singbox-cache.db"
        )

    private fun candidates(
        profile: ProxyProfile,
        settings: AppSettings = AppSettings()
    ): List<SingBoxConfigBuilder.Build> = SingBoxConfigBuilder.candidateBuilds(
        profile, settings, 10808, 39090, "secret",
        "/data/local/tmp/singbox.log", "/data/local/tmp/singbox-cache.db"
    )

    private fun translated(
        profile: ProxyProfile,
        settings: AppSettings = AppSettings()
    ): SingBoxConfigBuilder.Build = candidates(profile, settings)
        .firstOrNull { it.strategy == SingBoxConfigBuilder.STRATEGY_TRANSLATED }
        ?: error("no TRANSLATED candidate for ${profile.scheme}")

    private fun proxyOutbound(b: SingBoxConfigBuilder.Build): JSONObject {
        val outbounds = JSONObject(b.json).getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.getJSONObject(i)
            if (candidate.optString("tag") == SingBoxConfigBuilder.PROXY_TAG) return candidate
        }
        error("missing outbound ${SingBoxConfigBuilder.PROXY_TAG}")
    }

    private fun outbound(config: JSONObject, tag: String): JSONObject {
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.getJSONObject(i)
            if (candidate.optString("tag") == tag) return candidate
        }
        error("missing outbound $tag")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The candidate set
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun aLinkProfileYieldsExactlyTheParserCandidate() {
        val profile = profile("vless", raw = VLESS_LINK)
        val list = candidates(profile)
        assertEquals(1, list.size)
        assertEquals(SingBoxConfigBuilder.STRATEGY_LINK, list[0].strategy)
        val hop = proxyOutbound(list[0])
        assertEquals("parser", hop.getString("type"))
        assertEquals(VLESS_LINK, hop.getString("link"))
    }

    @Test
    fun aStoredConfigYieldsExactlyTheTranslatedCandidate() {
        val profile = xrayProfile("vless", VNEXT_SETTINGS, TCP_TLS_STREAM)
        val list = candidates(profile)
        assertEquals(1, list.size)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, list[0].strategy)
        assertEquals("vless", proxyOutbound(list[0]).getString("type"))
    }

    @Test
    fun aProfileWithBothReadersYieldsBothCandidates() {
        // A real import keeps both: the share link it came from AND the config it was written as.
        val profile = profile("vless", raw = VLESS_LINK, configJson = storedVless())
        val list = candidates(profile)
        assertEquals(2, list.size)
        assertEquals(SingBoxConfigBuilder.STRATEGY_LINK, list[0].strategy)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, list[1].strategy)
        assertEquals("parser", proxyOutbound(list[0]).getString("type"))
        assertEquals("vless", proxyOutbound(list[1]).getString("type"))
    }

    @Test
    fun thePreferenceSwitchFlipsTheCandidateOrder() {
        val profile = profile("vless", raw = VLESS_LINK, configJson = storedVless())
        val parserFirst = candidates(profile, AppSettings(singBoxPreferParser = true))
        val translatedFirst = candidates(profile, AppSettings(singBoxPreferParser = false))
        assertEquals(listOf(SingBoxConfigBuilder.STRATEGY_LINK, SingBoxConfigBuilder.STRATEGY_TRANSLATED), parserFirst.map { it.strategy })
        assertEquals(listOf(SingBoxConfigBuilder.STRATEGY_TRANSLATED, SingBoxConfigBuilder.STRATEGY_LINK), translatedFirst.map { it.strategy })
        // Flipping the switch must not change a candidate's content, only its rank.
        assertEquals(parserFirst[0].json, translatedFirst[1].json)
        assertEquals(parserFirst[1].json, translatedFirst[0].json)
    }

    @Test
    fun sshYieldsNoCandidateAtAll() {
        val profile = profile("ssh", configJson = storedVless())
        assertTrue(candidates(profile).isEmpty())
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse(support.supported)
        assertTrue(support.reason, support.reason.contains("SSH"))
    }

    @Test
    fun anUnreadableConfigWithNoLinkRefusesWithAReadableReason() {
        val profile = profile("vless", configJson = "{not json")
        assertTrue(candidates(profile).isEmpty())
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse(support.supported)
        assertTrue(support.reason, support.reason.isNotBlank())
    }

    @Test
    fun everyCandidateSharesTheSameEnvelope() {
        // The URL test, the routing rules and the DNS graph must behave identically no matter
        // which reader carried the profile, so both candidates get the exact same shell.
        val profile = profile("vless", raw = VLESS_LINK, configJson = storedVless())
        candidates(profile).forEach { candidate ->
            val config = JSONObject(candidate.json)
            val inbound = config.getJSONArray("inbounds").getJSONObject(0)
            assertEquals("mixed", inbound.getString("type"))
            assertEquals(SingBoxConfigBuilder.INBOUND_TAG, inbound.getString("tag"))
            assertEquals(10808, inbound.getInt("listen_port"))

            val experimental = config.getJSONObject("experimental")
            assertEquals("127.0.0.1:39090", experimental.getJSONObject("clash_api").getString("external_controller"))
            assertEquals("secret", experimental.getJSONObject("clash_api").getString("secret"))
            assertTrue(experimental.has("unified_delay"))

            assertTrue(candidate.json, candidate.json.contains("\"hijack-dns\""))
            // The removed `dns` outbound must not be written by either candidate.
            val outbounds = config.getJSONArray("outbounds")
            for (i in 0 until outbounds.length()) {
                assertFalse(
                    "the removed \"dns\" outbound must never be written",
                    outbounds.getJSONObject(i).optString("type").equals("dns", ignoreCase = true)
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // The translator
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun theDirectFormVlessTranslates() {
        val profile = xrayProfile(
            "vless",
            JSONObject()
                .put("address", "198.51.100.7")
                .put("port", 443)
                .put("id", "uuid-direct")
                .put("flow", "xtls-rprx-vision")
                .put("encryption", "none"),
            TCP_TLS_STREAM
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("vless", hop.getString("type"))
        assertEquals("198.51.100.7", hop.getString("server"))
        assertEquals(443, hop.getInt("server_port"))
        assertEquals("uuid-direct", hop.getString("uuid"))
        assertEquals("xtls-rprx-vision", hop.getString("flow"))
    }

    @Test
    fun theArrayFormVlessReadsUsersFieldByField() {
        val profile = xrayProfile("vless", VNEXT_SETTINGS, TCP_TLS_STREAM)
        val hop = proxyOutbound(translated(profile))
        assertEquals("vless", hop.getString("type"))
        assertEquals("uuid-array", hop.getString("uuid"))
        assertEquals("xtls-rprx-vision", hop.getString("flow"))
        assertEquals("none", hop.getString("encryption"))
    }

    @Test
    fun vmessPacketEncodingIsMapped() {
        val profile = xrayProfile(
            "vmess",
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
                                    .put("id", "uuid-vmess")
                                    .put("security", "auto")
                                    .put("packetEncoding", "xudp")
                            )
                        )
                )
            ),
            JSONObject().put("network", "tcp")
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("vmess", hop.getString("type"))
        assertEquals("auto", hop.getString("security"))
        assertEquals("xudp", hop.getString("packet_encoding"))
    }

    @Test
    fun shadowsocksUotBecomesUdpOverTcp() {
        val profile = xrayProfile(
            "shadowsocks",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject()
                        .put("address", "198.51.100.7")
                        .put("port", 8388)
                        .put("method", "aes-256-gcm")
                        .put("password", "pw")
                        .put("uot", true)
                )
            )
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("shadowsocks", hop.getString("type"))
        assertEquals("aes-256-gcm", hop.getString("method"))
        assertEquals(true, hop.getJSONObject("udp_over_tcp").getBoolean("enabled"))
    }

    @Test
    fun wireguardTranslatesKeyServerAndPeers() {
        val profile = xrayProfile(
            "wireguard",
            JSONObject()
                .put("secretKey", "CLIENT_KEY")
                .put(
                    "peers",
                    JSONArray().put(
                        JSONObject()
                            .put("publicKey", "PEER_KEY")
                            .put("endpoint", "198.51.100.7:51820")
                            .put("allowedIPs", JSONArray().put("0.0.0.0/0").put("::/0"))
                            .put("keepAlive", "25s")
                    )
                )
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("wireguard", hop.getString("type"))
        assertEquals("CLIENT_KEY", hop.getString("private_key"))
        assertEquals("198.51.100.7", hop.getString("server"))
        assertEquals(51820, hop.getInt("server_port"))
        val peer = hop.getJSONArray("peers").getJSONObject(0)
        assertEquals("PEER_KEY", peer.getString("public_key"))
        assertEquals(25, peer.getInt("keep_alive"))
        val allowed = peer.getJSONArray("allowed_ips")
        assertEquals("0.0.0.0/0", allowed.getString(0))
        assertEquals("::/0", allowed.getString(1))
    }

    @Test
    fun anExplicitHysteriaVersionWinsOverTheProtocolName() {
        // Stored hy2 nodes are `protocol: hysteria` + `version: 2`; the name alone must not
        // mistype them as v1.
        val profile = xrayProfile(
            "hysteria",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject()
                        .put("address", "198.51.100.7")
                        .put("port", 443)
                        .put("password", "pw2")
                )
            ),
            JSONObject()
                .put("network", "udp")
                .put("security", "tls")
                .put("tlsSettings", JSONObject().put("serverName", "cdn.example"))
                .put("hysteriaSettings", JSONObject().put("version", 2))
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("hysteria2", hop.getString("type"))
        assertEquals("pw2", hop.getString("password"))
        assertEquals("cdn.example", hop.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun hysteriaV1TakesTheAuthStringAndParsesHumanRates() {
        val profile = xrayProfile(
            "hysteria",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject()
                        .put("address", "198.51.100.7")
                        .put("port", 443)
                        .put("password", "pw1")
                )
            ),
            JSONObject()
                .put("network", "udp")
                .put("security", "tls")
                .put("tlsSettings", JSONObject().put("serverName", "cdn.example"))
                .put(
                    "hysteriaSettings",
                    JSONObject()
                        .put("up", "100mbps/s")
                        .put("down", "200mbps/s")
                        .put("obfs", "salamander")
                )
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("hysteria", hop.getString("type"))
        assertEquals("pw1", hop.getString("auth_str"))
        assertEquals(100, hop.getInt("up_mbps"))
        assertEquals(200, hop.getInt("down_mbps"))
        assertEquals("salamander", hop.getString("obfs"))
    }

    @Test
    fun hysteriaV1DefaultsToTenFiftyWhenRatesAreMissing() {
        val profile = xrayProfile(
            "hysteria",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject()
                        .put("address", "198.51.100.7")
                        .put("port", 443)
                        .put("password", "pw1")
                )
            )
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("hysteria", hop.getString("type"))
        assertEquals(10, hop.getInt("up_mbps"))
        assertEquals(50, hop.getInt("down_mbps"))
    }

    @Test
    fun hysteriaV2ObfsComesFromTheFinalmask() {
        val profile = xrayProfile(
            "hysteria2",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject()
                        .put("address", "198.51.100.7")
                        .put("port", 443)
                        .put("password", "pw2")
                )
            ),
            JSONObject()
                .put("network", "udp")
                .put("security", "tls")
                .put("tlsSettings", JSONObject().put("serverName", "cdn.example"))
                .put(
                    "finalmask",
                    JSONObject().put(
                        "udp",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "salamander")
                                .put("settings", JSONObject().put("salamander", JSONObject().put("password", "salt")))
                        )
                    )
                )
        )
        val hop = proxyOutbound(translated(profile))
        assertEquals("hysteria2", hop.getString("type"))
        assertEquals("salamander", hop.getJSONObject("obfs").getString("type"))
        assertEquals("salt", hop.getJSONObject("obfs").getString("password"))
    }

    @Test
    fun theWebSocketHostArrivesInThreeSpellings() {
        val base = JSONObject().put(
            "vnext",
            JSONArray().put(
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put(
                        "users",
                        JSONArray().put(JSONObject().put("id", "uuid-ws").put("encryption", "none"))
                    )
            )
        )
        val cases = mapOf(
            "headers.Host" to JSONObject().put("headers", JSONObject().put("Host", "w.example")).put("path", "/ws"),
            "headers.host" to JSONObject().put("headers", JSONObject().put("host", "w2.example")).put("path", "/ws"),
            "flat host" to JSONObject().put("host", "w3.example").put("path", "/ws")
        )
        cases.forEach { (label, wsSettings) ->
            val profile = xrayProfile(
                "vless",
                base,
                JSONObject().put("network", "ws").put("wsSettings", wsSettings)
            )
            val transport = proxyOutbound(translated(profile)).getJSONObject("transport")
            assertEquals("ws", transport.getString("type"))
            assertEquals("/ws", transport.getString("path"))
            val expected = when (label) {
                "headers.Host" -> "w.example"
                "headers.host" -> "w2.example"
                else -> "w3.example"
            }
            assertEquals(label, expected, transport.getJSONObject("headers").getString("Host"))
        }
    }

    @Test
    fun grpcMultiModeIsReadInBothSpellings() {
        val base = JSONObject().put(
            "vnext",
            JSONArray().put(
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put(
                        "users",
                        JSONArray().put(JSONObject().put("id", "uuid-grpc").put("encryption", "none"))
                    )
            )
        )
        listOf(
            JSONObject().put("serviceName", "svc").put("multi_mode", true),
            JSONObject().put("serviceName", "svc").put("multiMode", true)
        ).forEach { grpcSettings ->
            val profile = xrayProfile(
                "vless",
                base,
                JSONObject().put("network", "grpc").put("grpcSettings", grpcSettings)
            )
            val transport = proxyOutbound(translated(profile)).getJSONObject("transport")
            assertEquals("grpc", transport.getString("type"))
            assertEquals("svc", transport.getString("service_name"))
            assertEquals(true, transport.getBoolean("permit_without_stream"))
        }
    }

    @Test
    fun aTcpHeaderDisguiseIsReportedNotInvented() {
        val profile = xrayProfile(
            "vless",
            VNEXT_SETTINGS,
            JSONObject().put("network", "tcp").put("headerType", "srtp")
        )
        val b = translated(profile)
        val hop = proxyOutbound(b)
        assertNull(hop.optJSONObject("transport"))
        assertTrue(
            b.notes.toString(),
            b.notes.any { it.contains("header disguise") }
        )
    }

    @Test
    fun tlsFloorsCeilingsAndEchTranslate() {
        val profile = xrayProfile(
            "vless",
            VNEXT_SETTINGS,
            JSONObject()
                .put("network", "tcp")
                .put("security", "tls")
                .put(
                    "tlsSettings",
                    JSONObject()
                        .put("serverName", "cdn.example")
                        .put("minVersion", "1.2")
                        .put("maxVersion", "1.3")
                        .put("echSettings", JSONObject().put("config", "YWJj"))
                )
        )
        val tls = proxyOutbound(translated(profile)).getJSONObject("tls")
        assertEquals(true, tls.getBoolean("enabled"))
        assertEquals("cdn.example", tls.getString("server_name"))
        assertEquals("1.2", tls.getString("min_version"))
        assertEquals("1.3", tls.getString("max_version"))
        assertEquals(true, tls.getJSONObject("ech").getBoolean("enabled"))
        assertEquals("YWJj", tls.getJSONObject("ech").getString("config"))
    }

    @Test
    fun realityReadsBothPublicKeySpellings() {
        // Marble's own Reality emitter writes the public key under `password`; foreign emitters
        // use `publicKey`. Both must land in `reality.public_key`.
        val stream = JSONObject()
            .put("network", "tcp")
            .put("security", "reality")
            .put(
                "realitySettings",
                JSONObject()
                    .put("serverName", "cdn.example")
                    .put("password", "REALITY_PUB")
                    .put("shortId", "aa")
            )
        val marble = xrayProfile("vless", VNEXT_SETTINGS, stream)
        val reality = proxyOutbound(translated(marble)).getJSONObject("tls").getJSONObject("reality")
        assertEquals(true, reality.getBoolean("enabled"))
        assertEquals("REALITY_PUB", reality.getString("public_key"))
        assertEquals("aa", reality.getString("short_id"))

        val foreign = xrayProfile(
            "vless",
            VNEXT_SETTINGS,
            stream.put("realitySettings", JSONObject().put("serverName", "cdn.example").put("publicKey", "REALITY_PUB2"))
        )
        assertEquals(
            "REALITY_PUB2",
            proxyOutbound(translated(foreign)).getJSONObject("tls").getJSONObject("reality").getString("public_key")
        )
    }

    @Test
    fun muxIsGatedToTheTcpFamily() {
        val settings = AppSettings(muxEnabled = true)
        // vless over ws: TCP family, mux applies.
        val ws = xrayProfile(
            "vless",
            VNEXT_SETTINGS,
            JSONObject().put("network", "ws").put("wsSettings", JSONObject().put("path", "/ws"))
        )
        val wsHop = proxyOutbound(translated(ws, settings))
        assertEquals(true, wsHop.getJSONObject("multiplex").getBoolean("enabled"))

        // Hysteria v2: QUIC family, mux must not be written.
        val hy = xrayProfile(
            "hysteria2",
            JSONObject().put(
                "servers",
                JSONArray().put(
                    JSONObject().put("address", "198.51.100.7").put("port", 443).put("password", "pw")
                )
            )
        )
        assertNull(proxyOutbound(translated(hy, settings)).optJSONObject("multiplex"))

        // WireGuard: UDP protocol, mux must not be written.
        val wg = xrayProfile(
            "wireguard",
            JSONObject()
                .put("secretKey", "CLIENT_KEY")
                .put(
                    "peers",
                    JSONArray().put(
                        JSONObject().put("publicKey", "PEER_KEY").put("endpoint", "198.51.100.7:51820")
                    )
                )
        )
        assertNull(proxyOutbound(translated(wg, settings)).optJSONObject("multiplex"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Link-only sing-box-extended protocols (TUIC / AnyTLS)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun aTuicLinkIsTranslatedAsWellAsParsed() {
        val link = "tuic://uuid-tuic:secret-tuic@198.51.100.7:8443" +
            "?sni=cdn.example&congestion_control=bbr&udp_relay_mode=native"
        val profile = profile("tuic", raw = link)
        // Two readers, one link: the core's parser first, Marble's own translation as the
        // second chance when the core reader refuses.
        val list = candidates(profile)
        assertEquals(2, list.size)
        assertEquals(SingBoxConfigBuilder.STRATEGY_LINK, list[0].strategy)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, list[1].strategy)

        val hop = proxyOutbound(list[1])
        assertEquals("tuic", hop.getString("type"))
        assertEquals("198.51.100.7", hop.getString("server"))
        assertEquals(8443, hop.getInt("server_port"))
        assertEquals("uuid-tuic", hop.getString("uuid"))
        assertEquals("secret-tuic", hop.getString("password"))
        assertEquals("cdn.example", hop.getJSONObject("tls").getString("server_name"))
        assertEquals("bbr", hop.getString("congestion_control"))
        assertEquals("native", hop.getString("udp_relay_mode"))
    }

    @Test
    fun anAnytlsLinkIsTranslatedAsWellAsParsed() {
        val link = "anytls://pass-anytls@198.51.100.7:8443?sni=cdn.example"
        val profile = profile("anytls", raw = link)
        val list = candidates(profile)
        assertEquals(2, list.size)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, list[1].strategy)

        val hop = proxyOutbound(list[1])
        assertEquals("anytls", hop.getString("type"))
        assertEquals("198.51.100.7", hop.getString("server"))
        assertEquals(8443, hop.getInt("server_port"))
        assertEquals("pass-anytls", hop.getString("password"))
        assertEquals("cdn.example", hop.getJSONObject("tls").getString("server_name"))
    }

    @Test
    fun aTuicNodeWithNoLinkAtAllRefusesInsteadOfBuilding() {
        // TUIC/AnyTLS have no Xray JSON shape: with neither a link nor a config, there is no
        // reader that can serve the node, and the refusal says exactly that.
        val profile = profile("tuic", raw = "", configJson = "")
        assertTrue(candidates(profile).isEmpty())
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse(support.supported)
        assertTrue(support.reason, support.reason.isNotBlank())
        assertTrue(support.reason, support.reason.contains("tuic"))
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun storedVless(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("protocol", "vless")
                    .put("tag", "proxy")
                    .put("settings", VNEXT_SETTINGS)
                    .put("streamSettings", TCP_TLS_STREAM)
            )
        )
        .toString()

    private companion object {
        const val VLESS_LINK =
            "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
                "?encryption=none&security=tls&type=tcp&sni=example.com#Node+1"

        val VNEXT_SETTINGS = JSONObject().put(
            "vnext",
            JSONArray().put(
                JSONObject()
                    .put("address", "198.51.100.7")
                    .put("port", 443)
                    .put(
                        "users",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "uuid-array")
                                .put("encryption", "none")
                                .put("flow", "xtls-rprx-vision")
                        )
                    )
            )
        )

        val TCP_TLS_STREAM = JSONObject()
            .put("network", "tcp")
            .put("security", "tls")
            .put("tlsSettings", JSONObject().put("serverName", "cdn.example"))
    }
}
