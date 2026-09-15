package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SOCKET_FLIGHT_V168 — the physical-socket tuning sweep.
 *
 * Four defects were fixed, and every one of them is about a *socket the config actually opens*
 * rather than a knob on paper:
 *
 *  1. with Xray fragmentation on, the real socket belongs to the terminal freedom fragment
 *     dialer, yet liveness/MPTCP/BBR were only written to the proxy hop;
 *  2. Multipath TCP was never offered by either engine despite both cores supporting it with
 *     guaranteed plain-TCP fallback;
 *  3. sing-box's direct hop carried no dial tuning at all and QUIC got no `udp_fragment`;
 *  4. every new TUN→SOCKS connection paid a SOCKS handshake round trip HEV can pipeline.
 *
 * Nothing here spawns a core: the emitted documents ARE the contract.
 */
class SocketFlightV168Test {

    // ── HEV YAML ────────────────────────────────────────────────────────────────────────────

    private val datapath = TunnelTuning(
        maxSessions = 12_000,
        tcpBufferBytes = 65_536,
        udpBufferBytes = 524_288,
        label = "test"
    )

    /** Every key the pinned hev-socks5-tunnel 2.17.1 parses (src/hev-config.c). */
    private val accepted = mapOf(
        "tunnel" to setOf("mtu", "multi-queue", "ipv4", "ipv6", "icmp", "name",
            "post-up-script", "pre-down-script"),
        "socks5" to setOf("port", "address", "udp", "udp-address", "pipeline",
            "username", "password", "mark", "tcp-fastopen"),
        "mapdns" to setOf("address", "port", "network", "netmask", "cache-size"),
        "misc" to setOf("task-stack-size", "tcp-buffer-size", "udp-recv-buffer-size",
            "udp-copy-buffer-nums", "max-session-count", "connect-timeout",
            "read-write-timeout", "tcp-read-write-timeout", "udp-read-write-timeout",
            "pid-file", "log-file", "log-level", "limit-nofile")
    )

    private fun parseSections(yaml: String): Map<String, Map<String, String>> {
        val sections = linkedMapOf<String, LinkedHashMap<String, String>>()
        var current = ""
        yaml.trim().lines().forEach { raw ->
            val line = raw.substringAfter('#').trimEnd()
            if (line.isBlank()) return@forEach
            if (!line.startsWith(" ") && line.endsWith(":")) {
                current = line.removeSuffix(":")
                sections[current] = linkedMapOf()
            } else {
                val (key, value) = line.trim().split(":", limit = 2)
                sections.getValue(current)[key.trim()] = value.trim().trim('\'')
            }
        }
        return sections
    }

    @Test
    fun hevYamlOnlyUsesKeysThePinnedCoreParses() {
        val yaml = HevTunnelPolicy.buildConfig(
            socksPort = 10808,
            mtu = 9000,
            ipv6 = true,
            logFilePath = "/data/local/tmp/hev.log",
            datapath = datapath,
            fastOpen = true
        )
        val sections = parseSections(yaml)
        assertEquals(setOf("tunnel", "socks5", "misc"), sections.keys)
        sections.forEach { (name, values) ->
            values.keys.forEach { key ->
                assertTrue("HEV 2.17.1 silently ignores unknown $name.$key", key in accepted.getValue(name))
            }
        }
    }

    @Test
    fun hevPipelinesHandshakeWidensUdpPoolAndRaisesConnectTimeout() {
        val yaml = HevTunnelPolicy.buildConfig(10808, 9000, true, "/x.log", datapath)
        val sections = parseSections(yaml)
        assertEquals("true", sections.getValue("socks5").getValue("pipeline"))
        assertFalse("tcp-fastopen is opt-in via the TFO setting", sections.getValue("socks5").containsKey("tcp-fastopen"))
        assertEquals(HevTunnelPolicy.CONNECT_TIMEOUT_MS.toString(), sections.getValue("misc").getValue("connect-timeout"))
        assertEquals(HevTunnelPolicy.UDP_COPY_BUFFER_NUMS.toString(), sections.getValue("misc").getValue("udp-copy-buffer-nums"))
        assertEquals(HevTunnelPolicy.TASK_STACK_SIZE.toString(), sections.getValue("misc").getValue("task-stack-size"))
        assertEquals("65536", sections.getValue("misc").getValue("tcp-buffer-size"))
        assertEquals("524288", sections.getValue("misc").getValue("udp-recv-buffer-size"))
        assertEquals("12000", sections.getValue("misc").getValue("max-session-count"))
        // 32 × 1500 = 48 KiB must stay under the 64 KiB TCP floor so the auto-sized task stack
        // (TASK_STACK_SIZE + max(tcp buffer, UDP pool)) does not grow beyond the emitted 86 016.
        assertTrue(1500 * HevTunnelPolicy.UDP_COPY_BUFFER_NUMS <= 65_536)
        assertTrue(HevTunnelPolicy.CONNECT_TIMEOUT_MS < 16_000)
    }

    @Test
    fun hevTfoAndIpv6AreConditional() {
        val withTfo = HevTunnelPolicy.buildConfig(10808, 9000, false, "/x.log", datapath, fastOpen = true)
        assertEquals("true", parseSections(withTfo).getValue("socks5").getValue("tcp-fastopen"))
        assertNull(parseSections(withTfo).getValue("tunnel")["ipv6"])
        val withV6 = HevTunnelPolicy.buildConfig(10808, 9000, true, "/x.log", datapath)
        assertEquals("fc00::1", parseSections(withV6).getValue("tunnel").getValue("ipv6"))
    }

    @Test
    fun hevRejectsImpossibleInputsAndQuotesTheLogPath() {
        runCatching {
            HevTunnelPolicy.buildConfig(70_000, 9000, true, "/x.log", datapath)
        }.also { assertTrue(it.isFailure) }
        runCatching {
            HevTunnelPolicy.buildConfig(10808, 500, true, "/x.log", datapath)
        }.also { assertTrue(it.isFailure) }
        val yaml = HevTunnelPolicy.buildConfig(10808, 9000, true, "/a'b.log", datapath)
        assertTrue(yaml.contains("log-file: '/a''b.log'"))
    }

    // ── CoreSocketPolicy — Xray shape ─────────────────────────────────────────────────────────

    @Test
    fun xrayPhysicalSocketFillsEveryOmission() {
        val sockopt = JSONObject()
        CoreSocketPolicy.writeXrayPhysicalTcpSockopt(
            sockopt,
            AppSettings(tcpFastOpenEnabled = true, tcpMaxSeg = 1400),
            iranActive = false
        )
        assertTrue(sockopt.getBoolean("tcpMptcp"))
        assertEquals("bbr", sockopt.getString("tcpCongestion"))
        assertTrue(sockopt.getBoolean("tcpFastOpen"))
        assertEquals(1400, sockopt.getInt("tcpMaxSeg"))
        assertEquals(60, sockopt.getInt("tcpKeepAliveIdle"))
        assertEquals(15, sockopt.getInt("tcpKeepAliveInterval"))
        assertEquals(60_000, sockopt.getInt("tcpUserTimeout"))
    }

    @Test
    fun xrayPhysicalSocketHonoursIranAndNeverOverwritesAnOperatorChoice() {
        val iran = JSONObject()
        CoreSocketPolicy.writeXrayPhysicalTcpSockopt(iran, AppSettings(), iranActive = true)
        assertTrue("Iran transit gets the longer liveness profile", iran.getInt("tcpKeepAliveIdle") >= 90)
        assertTrue(iran.getInt("tcpUserTimeout") >= 120_000)

        val imported = JSONObject()
            .put("tcpMptcp", false)
            .put("tcpCongestion", "cubic")
        CoreSocketPolicy.writeXrayPhysicalTcpSockopt(imported, AppSettings(tcpFastOpenEnabled = true), false)
        assertFalse("an explicit tcpMptcp:false must survive", imported.getBoolean("tcpMptcp"))
        assertEquals("cubic", imported.getString("tcpCongestion"))
    }

    @Test
    fun missingUtlsFingerprintGetsChromeButExplicitValuesAreKept() {
        fun outbound(security: String, fingerprint: String? = null, network: String = "tcp"): JSONObject {
            val tls = JSONObject()
            if (fingerprint != null) tls.put("fingerprint", fingerprint)
            return JSONObject().put(
                "streamSettings",
                JSONObject().put("network", network).put("security", security)
                    .put(if (security == "reality") "realitySettings" else "tlsSettings", tls)
            )
        }

        val bare = outbound("tls")
        CoreSocketPolicy.applyDefaultUtlsFingerprint(bare)
        assertEquals("chrome", bare.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("fingerprint"))

        val reality = outbound("reality")
        CoreSocketPolicy.applyDefaultUtlsFingerprint(reality)
        assertEquals("chrome", reality.getJSONObject("streamSettings").getJSONObject("realitySettings").getString("fingerprint"))

        val firefox = outbound("tls", "firefox")
        CoreSocketPolicy.applyDefaultUtlsFingerprint(firefox)
        assertEquals("firefox", firefox.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("fingerprint"))

        val unsafe = outbound("tls", "unsafe")
        CoreSocketPolicy.applyDefaultUtlsFingerprint(unsafe)
        assertEquals("unsafe", unsafe.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("fingerprint"))

        val quic = outbound("tls", network = "quic")
        CoreSocketPolicy.applyDefaultUtlsFingerprint(quic)
        assertFalse("QUIC/KCP have no uTLS layer", quic.getJSONObject("streamSettings").getJSONObject("tlsSettings").has("fingerprint"))
    }

    // ── CoreSocketPolicy — sing-box shape ─────────────────────────────────────────────────────

    @Test
    fun singBoxPhysicalDialIsParityForTcp() {
        val outbound = JSONObject()
        CoreSocketPolicy.writeSingBoxPhysicalDial(outbound, AppSettings(tcpFastOpenEnabled = true), "vless", "tcp")
        assertTrue(outbound.getBoolean("tcp_multi_path"))
        assertTrue(outbound.getBoolean("udp_fragment"))
        assertTrue(outbound.getBoolean("tcp_fast_open"))
        assertTrue(outbound.getString("connect_timeout").endsWith("s"))
        assertEquals("60s", outbound.getString("tcp_keep_alive"))
        assertEquals("15s", outbound.getString("tcp_keep_alive_interval"))
        assertFalse("the Android-forbidden dial keys are never written", outbound.has("network_strategy"))
    }

    @Test
    fun singBoxQuicAndUdpProtocolsGetNoTcpKeepAlive() {
        listOf("hysteria2", "hysteria", "tuic", "wireguard").forEach { protocol ->
            val outbound = JSONObject()
            CoreSocketPolicy.writeSingBoxPhysicalDial(outbound, AppSettings(), protocol)
            assertFalse("$protocol carries no TCP keep-alive", outbound.has("tcp_keep_alive"))
            assertFalse(outbound.has("tcp_keep_alive_interval"))
            assertTrue("QUIC still needs UDP fragmentation against PMTU black holes", outbound.getBoolean("udp_fragment"))
            assertTrue(outbound.getBoolean("tcp_multi_path"))
        }
        val quic = JSONObject()
        CoreSocketPolicy.writeSingBoxPhysicalDial(quic, AppSettings(), "vless", "quic")
        assertFalse(quic.has("tcp_keep_alive"))
        assertTrue(quic.getBoolean("udp_fragment"))
    }

    // ── Xray hardener integration ─────────────────────────────────────────────────────────────

    private fun xrayConfig(host: String = "node.example.com", network: String = "tcp"): String =
        JSONObject()
            .put("outbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "proxy")
                    .put("protocol", "vless")
                    .put(
                        "settings",
                        JSONObject().put(
                            "vnext",
                            JSONArray().put(
                                JSONObject().put("address", host).put("port", 443)
                                    .put("users", JSONArray().put(JSONObject().put("id", "x")))
                            )
                        )
                    )
                    .put(
                        "streamSettings",
                        // Marble's own builders name the transport "method" (with "network"
                        // mirrored for Xray proper); set both for realism.
                        JSONObject().put("method", network).put("network", network)
                            .put("security", "tls")
                            .put("tlsSettings", JSONObject().put("serverName", host))
                    )
            ))
            .toString()

    private fun hardened(settings: AppSettings = AppSettings(), host: String = "node.example.com", network: String = "tcp") =
        JSONObject(XrayConfigHardener.harden(xrayConfig(host, network), 21080, settings))

    private fun xrayOutbound(config: JSONObject, tag: String): JSONObject {
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            if (outbounds.getJSONObject(i).optString("tag") == tag) return outbounds.getJSONObject(i)
        }
        error("missing outbound $tag")
    }

    @Test
    fun xrayProxyHopOffersMptcpBbrAndAFilledFingerprint() {
        val config = hardened()
        assertFalse(config.toString().contains("tcpKeepAliveCount"))
        val sockopt = xrayOutbound(config, "proxy").getJSONObject("streamSettings").getJSONObject("sockopt")
        assertTrue(sockopt.getBoolean("tcpMptcp"))
        assertEquals("bbr", sockopt.getString("tcpCongestion"))
        assertEquals("chrome", xrayOutbound(config, "proxy").getJSONObject("streamSettings")
            .getJSONObject("tlsSettings").getString("fingerprint"))
    }

    @Test
    fun xrayTfoReachesBothThePhysicalSocketAndTheLocalListener() {
        val config = hardened(AppSettings(tcpFastOpenEnabled = true))
        assertTrue(
            xrayOutbound(config, "proxy").getJSONObject("streamSettings").getJSONObject("sockopt").getBoolean("tcpFastOpen")
        )
        val inbound = config.getJSONArray("inbounds").getJSONObject(0)
        assertTrue(inbound.getJSONObject("streamSettings").getJSONObject("sockopt").getBoolean("tcpFastOpen"))

        val off = hardened(AppSettings(tcpFastOpenEnabled = false))
        assertFalse(off.getJSONArray("inbounds").getJSONObject(0).has("streamSettings"))
    }

    @Test
    fun fragmentedConnectionTunesTheFreedomDialerThatActuallyOpensTheSocket() {
        val settings = AppSettings(
            fragmentEnabled = true,
            tcpFastOpenEnabled = true,
            iranModePolicy = IranModePolicy.ALWAYS_ON,
            iranModeCountermeasures = true
        )
        val config = hardened(settings)
        val physical = xrayOutbound(config, "fragment-direct")
        val sockopt = physical.getJSONObject("streamSettings").getJSONObject("sockopt")
        assertTrue("the fragment socket gets MPTCP", sockopt.getBoolean("tcpMptcp"))
        assertEquals("bbr", sockopt.getString("tcpCongestion"))
        assertTrue(sockopt.getBoolean("tcpFastOpen"))
        assertTrue("the fragment socket gets Iran keep-alives", sockopt.getInt("tcpKeepAliveIdle") >= 90)
        assertTrue(sockopt.getInt("tcpUserTimeout") >= 120_000)
    }

    @Test
    fun twoLayerFragmentationTunesOnlyTheTerminalHop() {
        val settings = AppSettings(
            fragmentEnabled = true,
            fragmentInnerEnabled = true,
            tcpFastOpenEnabled = true
        )
        val config = hardened(settings)
        val middle = xrayOutbound(config, "tls-fragment")
        val middleSockopt = middle.getJSONObject("streamSettings").getJSONObject("sockopt")
        assertEquals("fragment-direct", middleSockopt.getString("dialerProxy"))
        assertFalse("an intermediate fragment hop opens no socket of its own", middleSockopt.has("tcpMptcp"))
        val terminal = xrayOutbound(config, "fragment-direct").getJSONObject("streamSettings").getJSONObject("sockopt")
        assertTrue(terminal.getBoolean("tcpMptcp"))
    }

    @Test
    fun xrayUdpTransportsNeverCarryTcpFlightKnobs() {
        val config = hardened(AppSettings(), network = "mkcp")
        val sockopt = xrayOutbound(config, "proxy").optJSONObject("streamSettings")?.optJSONObject("sockopt")
        if (sockopt != null) {
            assertFalse(sockopt.has("tcpMptcp"))
            assertFalse(sockopt.has("tcpCongestion"))
            assertFalse(sockopt.has("tcpKeepAliveIdle"))
        }
    }

    // ── sing-box builder integration ──────────────────────────────────────────────────────────

    private fun linkProfile(raw: String) = ProxyProfile(
        id = "node-1",
        name = "Node 1",
        scheme = raw.substringBefore("://"),
        raw = raw,
        configJson = "",
        host = "198.51.100.7",
        port = 443
    )

    private fun build(profile: ProxyProfile, settings: AppSettings = AppSettings()): JSONObject =
        JSONObject(
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

    private fun singOutbound(config: JSONObject, tag: String): JSONObject {
        val outbounds = config.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            if (outbounds.getJSONObject(i).optString("tag") == tag) return outbounds.getJSONObject(i)
        }
        error("missing outbound $tag")
    }

    @Test
    fun singBoxParserAndDirectHopsCarryTheFullDialFlight() {
        val link = "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
            "?encryption=none&security=tls&type=tcp&sni=example.com#Node"
        val config = build(linkProfile(link))
        val proxy = singOutbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertTrue(proxy.getBoolean("tcp_multi_path"))
        assertTrue(proxy.getBoolean("udp_fragment"))
        assertTrue(proxy.has("tcp_keep_alive"))
        assertTrue(proxy.getString("connect_timeout").endsWith("s"))

        val direct = singOutbound(config, SingBoxConfigBuilder.DIRECT_TAG)
        assertTrue("the direct hop (encrypted bootstrap DoH + direct routes) is tuned too", direct.getBoolean("tcp_multi_path"))
        assertTrue(direct.getBoolean("udp_fragment"))
        assertTrue(direct.has("tcp_keep_alive"))
    }

    @Test
    fun singBoxHysteria2SkipsTcpKeepAliveButKeepsUdpFragment() {
        val link = "hy2://test-password@198.51.100.7:443?sni=h.example.com#Hy2"
        val config = build(linkProfile(link))
        val proxy = singOutbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertFalse(proxy.has("tcp_keep_alive"))
        assertFalse(proxy.has("tcp_keep_alive_interval"))
        assertTrue(proxy.getBoolean("udp_fragment"))
        assertTrue(proxy.getBoolean("tcp_multi_path"))
    }

    @Test
    fun singBoxTfoSettingFlowsToDialFields() {
        val link = "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
            "?encryption=none&security=tls&type=tcp#Node"
        val on = build(linkProfile(link), AppSettings(tcpFastOpenEnabled = true))
        assertTrue(singOutbound(on, SingBoxConfigBuilder.PROXY_TAG).getBoolean("tcp_fast_open"))
        val off = build(linkProfile(link), AppSettings(tcpFastOpenEnabled = false))
        assertFalse(singOutbound(off, SingBoxConfigBuilder.PROXY_TAG).getBoolean("tcp_fast_open"))
    }

    @Test
    fun singBoxChainTunesOnlyTheTerminalHop() {
        val entry = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put("vnext", JSONArray().put(
                    JSONObject().put("address", "entry.example.com").put("port", 443)
                        .put("users", JSONArray().put(JSONObject().put("id", "x")))
                ))
            )
            .put(
                "streamSettings",
                JSONObject().put("network", "tcp").put("security", "tls")
                    .put("sockopt", JSONObject().put("dialerProxy", "exit"))
            )
        val exit = JSONObject()
            .put("tag", "exit")
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put("vnext", JSONArray().put(
                    JSONObject().put("address", "exit.example.com").put("port", 443)
                        .put("users", JSONArray().put(JSONObject().put("id", "y")))
                ))
            )
            .put("streamSettings", JSONObject().put("network", "tcp").put("security", "tls"))
        val profile = ProxyProfile(
            id = "chain",
            name = "Chain",
            scheme = "vless",
            raw = "",
            configJson = JSONObject().put("outbounds", JSONArray().put(entry).put(exit)).toString(),
            host = "entry.example.com",
            port = 443
        )
        // Force the stored-JSON reader so the detour edge is Marble's own translation.
        val config = build(profile, AppSettings(singBoxPreferParser = false))
        val proxy = singOutbound(config, SingBoxConfigBuilder.PROXY_TAG)
        assertEquals("marble-hop-1", proxy.getString("detour"))
        assertFalse("a chained entry opens no physical socket", proxy.has("tcp_multi_path"))
        val terminal = singOutbound(config, "marble-hop-1")
        assertTrue(terminal.getBoolean("tcp_multi_path"))
        assertTrue(terminal.getBoolean("udp_fragment"))
        assertTrue(terminal.has("tcp_keep_alive"))
    }
}
