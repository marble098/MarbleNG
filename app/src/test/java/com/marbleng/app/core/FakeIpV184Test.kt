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
 * MARBLE_FAKE_IP_V184 — the fake-IP cold-DNS fix, pinned on BOTH engines.
 *
 * A WhatsApp cold start asks ~15 domains of the encrypted resolvers *through the cold tunnel*
 * before the first byte of the first flow can leave the device. These tests pin the emitted
 * configs the pinned cores actually load — the shapes below were validated against the pinned
 * XTLS/Xray-core v26.9.9 source (fakedns app + nameserver + sniffing override; the v26
 * `RoutingRule` proto has no `fakedns` field, so no routing rule is emitted) and against the
 * pinned shtorm-7/sing-box-extended v1.14.1 source (`fakeip` DNS server, `query_type` rule,
 * forced route `resolve`, and the `allowFakeIP=false` exclusion that keeps the proxy's own
 * resolution off the fake pool).
 */
class FakeIpV184Test {

    // ─────────────────────────────────────────────────────────────────────────
    // Xray
    // ─────────────────────────────────────────────────────────────────────────

    /** One VLESS node with a literal address: no bootstrap ladder, cleanest DNS graph. */
    private fun vlessSource(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "proxy")
                    .put("protocol", "vless")
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
                                                .put("id", "11111111-1111-1111-1111-111111111111")
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
        .toString()

    private fun xrayFakeIndexes(servers: JSONArray): List<Int> =
        (0 until servers.length()).filter { servers.getJSONObject(it).optString("address") == "fakedns" }

    private fun xrayDestOverrideHasFake(inbounds: JSONArray): Boolean {
        for (i in 0 until inbounds.length()) {
            val override = inbounds.getJSONObject(i).optJSONObject("sniffing")
                ?.optJSONArray("destOverride") ?: continue
            for (j in 0 until override.length()) {
                if (override.optString(j) == "fakedns") return true
            }
        }
        return false
    }

    @Test
    fun xrayArmsFakednsPoolNameserverAndSniffingOverride() {
        val hardened = JSONObject(XrayConfigHardener.harden(vlessSource(), 21080, AppSettings()))

        // The v26.9.9 JSON schema (`infra/conf/fakedns.go`): ipPool + poolSize.
        val fakedns = hardened.getJSONObject("fakedns")
        assertEquals("283.0.0.0/8", fakedns.getString("ipPool"))
        assertEquals(65536, fakedns.getInt("poolSize"))

        // The fakedns nameserver exists, carries the list-wide queryStrategy verify() demands,
        // and sits BEFORE the first encrypted DoH server: in serial mode it answers every
        // ordinary question in-process and the tunnel resolvers are left to the proxy's own
        // dial-time lookups, which skip the FakeDNS client.
        val dns = hardened.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        val fakeIndexes = xrayFakeIndexes(servers)
        assertEquals("exactly one fakedns nameserver", 1, fakeIndexes.size)
        var firstRemote = -1
        for (i in 0 until servers.length()) {
            val address = servers.getJSONObject(i).optString("address")
            if (firstRemote < 0 && address.startsWith("https://")) firstRemote = i
        }
        assertTrue("an encrypted DoH server must stay in the graph", firstRemote >= 0)
        assertTrue("fakedns must answer before the tunnel DoH", fakeIndexes[0] < firstRemote)
        assertEquals(
            dns.optString("queryStrategy"),
            servers.getJSONObject(fakeIndexes[0]).optString("queryStrategy")
        )

        // The sniffer is what turns a 283.x answer back into the real host; every inbound that
        // carries a sniffing block must name the fakedns destination override.
        assertTrue(xrayDestOverrideHasFake(hardened.getJSONArray("inbounds")))

        // No v25-style fakedns routing rule: fake addresses fall through to the final rule,
        // which must still ride the selected proxy (the hardener's own invariant).
        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        assertEquals("proxy", rules.getJSONObject(rules.length() - 1).optString("outboundTag"))
        val ruleText = rules.toString()
        assertFalse("no fakedns routing rule exists in v26", ruleText.contains("\"fakedns\""))
    }

    @Test
    fun xrayKeepsFakednsOutWhenSniffingIsDisabled() {
        // Without the inbound sniffer the domain restore cannot run, and the proxy would dial
        // the fake address itself — so the whole chain stays off, not just half of it.
        val hardened = JSONObject(
            XrayConfigHardener.harden(
                vlessSource(),
                21080,
                AppSettings(xraySniffingEnabled = false)
            )
        )
        assertFalse(hardened.has("fakedns"))
        assertTrue(xrayFakeIndexes(hardened.getJSONObject("dns").getJSONArray("servers")).isEmpty())
        assertFalse(xrayDestOverrideHasFake(hardened.getJSONArray("inbounds")))
    }

    @Test
    fun xrayKeepsFakednsOutWhenTheUserTurnsItOff() {
        val hardened = JSONObject(
            XrayConfigHardener.harden(
                vlessSource(),
                21080,
                AppSettings(dnsFakeIpEnabled = false)
            )
        )
        assertFalse(hardened.has("fakedns"))
        assertTrue(xrayFakeIndexes(hardened.getJSONObject("dns").getJSONArray("servers")).isEmpty())
        assertFalse(xrayDestOverrideHasFake(hardened.getJSONArray("inbounds")))
    }

    @Test
    fun xrayRankInstanceNeverCarriesFakedns() {
        // The rank instance has no TUN and no sniffing inbound: a fake address there would be
        // an address nothing can restore. harden() re-arms the block late on purpose, so the
        // rank path strips it explicitly.
        val rank = JSONObject(XrayConfigHardener.hardenForNativeRank(vlessSource(), AppSettings()))
        assertFalse(rank.has("fakedns"))
        rank.optJSONObject("dns")?.optJSONArray("servers")?.let { servers ->
            assertTrue(xrayFakeIndexes(servers).isEmpty())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sing-box
    // ─────────────────────────────────────────────────────────────────────────

    private fun profile(host: String): ProxyProfile = ProxyProfile(
        id = "node-1",
        name = "Node 1",
        scheme = "vless",
        raw = "",
        configJson = JSONObject()
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
                                        .put("address", host)
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
        host = host,
        port = 443
    )

    private fun singBoxBuild(settings: AppSettings, host: String = "198.51.100.7"): JSONObject =
        JSONObject(
            SingBoxConfigBuilder.build(
                profile = profile(host),
                settings = settings,
                socksPort = 10808,
                apiPort = 39090,
                apiSecret = "secret",
                logPath = "/data/local/tmp/singbox.log",
                cachePath = "/data/local/tmp/singbox-cache.db"
            ).json
        )

    @Test
    fun singBoxArmsFakeipServerQueryRuleAndResolveAction() {
        val config = singBoxBuild(AppSettings())
        val dns = config.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")

        var fake: JSONObject? = null
        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            if (server.optString("tag") == SingBoxConfigBuilder.DNS_FAKEIP_TAG) fake = server
        }
        assertTrue("the fakeip server must exist", fake != null)
        assertEquals("fakeip", fake!!.optString("type"))
        assertEquals(SingBoxConfigBuilder.FAKE_IP_POOL, fake.optString("inet4_range"))

        // The app's A/AAAA questions route to the fake pool; `final` stays the encrypted
        // resolver over the proxy — that is what keeps the engine's own resolution off the
        // fake addresses.
        assertEquals(SingBoxConfigBuilder.DNS_REMOTE_TAG, dns.optString("final"))
        val rules = dns.getJSONArray("rules")
        var queryRule: JSONObject? = null
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.optJSONArray("query_type") != null) queryRule = rule
        }
        assertTrue("a query_type rule must route the app's A/AAAA questions", queryRule != null)
        val queryTypes = queryRule!!.getJSONArray("query_type")
        assertEquals(2, queryTypes.length())
        assertEquals("A", queryTypes.optString(0))
        assertEquals("AAAA", queryTypes.optString(1))
        assertEquals(SingBoxConfigBuilder.DNS_FAKEIP_TAG, queryRule.optString("server"))

        // A restored fake flow reaches the router's pre-match with a domain destination;
        // without a resolve action the flow would be rejected
        // ("a resolve action is required before routing to outbound").
        val routeRules = config.getJSONObject("route").getJSONArray("rules")
        var resolve = false
        for (i in 0 until routeRules.length()) {
            if (routeRules.getJSONObject(i).optString("action") == "resolve") resolve = true
        }
        assertTrue("the route resolve action must ride along with fakeip", resolve)
    }

    @Test
    fun singBoxKeepsNodeBootstrapAheadOfFakeip() {
        // The VLESS hostname must never see a fake answer: the node-host DNS rule is written
        // before the query_type rule on both the TUN path and the engine's dial path.
        val config = singBoxBuild(AppSettings(), host = "edge.example.com")
        val rules = config.getJSONObject("dns").getJSONArray("rules")
        var hostRuleIndex = -1
        var fakeRuleIndex = -1
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.optString("server") == SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG) hostRuleIndex = i
            if (rule.optString("server") == SingBoxConfigBuilder.DNS_FAKEIP_TAG) fakeRuleIndex = i
        }
        assertTrue(hostRuleIndex in 0 until rules.length())
        assertTrue(fakeRuleIndex in 0 until rules.length())
        assertTrue("node-host bootstrap must outrank fakeip", hostRuleIndex < fakeRuleIndex)
    }

    @Test
    fun singBoxStaysClassicWhenFakeipIsOff() {
        val config = singBoxBuild(AppSettings(dnsFakeIpEnabled = false))
        val dns = config.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        for (i in 0 until servers.length()) {
            assertFalse(servers.getJSONObject(i).optString("tag") == SingBoxConfigBuilder.DNS_FAKEIP_TAG)
            assertFalse(servers.getJSONObject(i).optString("type") == "fakeip")
        }
        val rules = dns.getJSONArray("rules")
        for (i in 0 until rules.length()) {
            assertFalse(rules.getJSONObject(i).optJSONArray("query_type") != null)
        }
        // With fakeip off the resolve action follows the (off-by-default) Exclave switch.
        val routeRules = config.getJSONObject("route").getJSONArray("rules")
        for (i in 0 until routeRules.length()) {
            assertFalse(routeRules.getJSONObject(i).optString("action") == "resolve")
        }
    }
}
