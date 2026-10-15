package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeIpV184Test {
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

    private fun ipv4Number(address: String): Long {
        val octets = address.split('.')
        require(octets.size == 4) { "not an IPv4 address: $address" }
        return octets.fold(0L) { value, raw ->
            val octet = raw.toIntOrNull()
            require(octet != null && octet in 0..255) { "invalid IPv4 octet in $address" }
            (value shl 8) or octet.toLong()
        }
    }

    private fun cidrContains(cidr: String, address: String): Boolean {
        val pieces = cidr.split('/', limit = 2)
        require(pieces.size == 2) { "not a CIDR: $cidr" }
        val prefix = pieces[1].toIntOrNull()
        require(prefix != null && prefix in 0..32) { "invalid CIDR prefix: $cidr" }
        val mask = if (prefix == 0) 0L else (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL
        val network = ipv4Number(pieces[0])
        require((network and mask) == network) { "non-canonical CIDR: $cidr" }
        return (ipv4Number(address) and mask) == network
    }

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
    fun sharedPoolIsAValidReservedCidrThatDoesNotContainTheTunInterface() {
        val pool = FakeIpPolicy.IPV4_POOL
        assertEquals(pool, SingBoxConfigBuilder.FAKE_IP_POOL)
        assertTrue(
            "the fake pool must be inside the reserved benchmark block",
            cidrContains("198.18.0.0/15", pool.substringBefore('/'))
        )
        assertTrue("the configured fake pool must contain its usable addresses", cidrContains(pool, "198.19.0.2"))
        assertFalse("the fake pool must not swallow HEV's TUN address", cidrContains(pool, "198.18.0.1"))

        val prefix = pool.substringAfter('/').toInt()
        val addressCount = 1L shl (32 - prefix)
        assertTrue(
            "Xray requires its LRU to be strictly smaller than the subnet",
            FakeIpPolicy.XRAY_LRU_SIZE.toLong() < addressCount
        )
    }

    @Test
    fun xrayArmsFakednsPoolNameserverAndSniffingOverride() {
        val hardened = JSONObject(XrayConfigHardener.harden(vlessSource(), 21080, AppSettings()))

        val pools = hardened.getJSONArray("fakedns")
        assertEquals(2, pools.length())
        assertEquals(FakeIpPolicy.IPV4_POOL, pools.getJSONObject(0).getString("ipPool"))
        assertEquals(FakeIpPolicy.IPV6_POOL, pools.getJSONObject(1).getString("ipPool"))
        assertEquals(FakeIpPolicy.XRAY_LRU_SIZE, pools.getJSONObject(0).getInt("poolSize"))
        assertEquals(FakeIpPolicy.XRAY_LRU_SIZE, pools.getJSONObject(1).getInt("poolSize"))

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

        assertTrue(xrayDestOverrideHasFake(hardened.getJSONArray("inbounds")))

        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        assertEquals("proxy", rules.getJSONObject(rules.length() - 1).optString("outboundTag"))
        val ruleText = rules.toString()
        assertFalse("no fakedns routing rule exists in v26", ruleText.contains("\"fakedns\""))
    }

    @Test
    fun xrayKeepsFakednsOutWhenSniffingIsDisabled() {
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
        val rank = JSONObject(XrayConfigHardener.hardenForNativeRank(vlessSource(), AppSettings()))
        assertFalse(rank.has("fakedns"))
        rank.optJSONObject("dns")?.optJSONArray("servers")?.let { servers ->
            assertTrue(xrayFakeIndexes(servers).isEmpty())
        }
    }

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
        assertEquals(FakeIpPolicy.IPV6_POOL, fake.optString("inet6_range"))
        assertFalse("fakeip owns a range, not an upstream server", fake.has("server"))

        val cache = config.getJSONObject("experimental").getJSONObject("cache_file")
        assertTrue("fakeip reverse mappings must survive core restarts", cache.getBoolean("store_fakeip"))

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

        val routeRules = config.getJSONObject("route").getJSONArray("rules")
        var resolve = false
        for (i in 0 until routeRules.length()) {
            if (routeRules.getJSONObject(i).optString("action") == "resolve") resolve = true
        }
        assertTrue("the route resolve action must ride along with fakeip", resolve)
    }

    @Test
    fun singBoxKeepsNodeBootstrapAheadOfFakeip() {
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
    fun bothEnginesKeepFakeIpOffWhenDnsInterceptionIsOff() {
        val settings = AppSettings(dnsHijackEnabled = false)
        val xray = JSONObject(XrayConfigHardener.harden(vlessSource(), 21080, settings))
        assertFalse(xray.has("fakedns"))
        assertTrue(xrayFakeIndexes(xray.getJSONObject("dns").getJSONArray("servers")).isEmpty())
        assertFalse(xrayDestOverrideHasFake(xray.getJSONArray("inbounds")))

        val singBox = singBoxBuild(settings)
        val dns = singBox.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        for (i in 0 until servers.length()) {
            assertFalse(servers.getJSONObject(i).optString("type") == "fakeip")
        }
        val dnsRules = dns.getJSONArray("rules")
        for (i in 0 until dnsRules.length()) {
            assertFalse(dnsRules.getJSONObject(i).optJSONArray("query_type") != null)
        }
        val routeRules = singBox.getJSONObject("route").getJSONArray("rules")
        for (i in 0 until routeRules.length()) {
            assertFalse(routeRules.getJSONObject(i).optString("action") == "resolve")
        }
        val cache = singBox.getJSONObject("experimental").getJSONObject("cache_file")
        assertFalse(cache.has("store_fakeip"))
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

        val routeRules = config.getJSONObject("route").getJSONArray("rules")
        for (i in 0 until routeRules.length()) {
            assertFalse(routeRules.getJSONObject(i).optString("action") == "resolve")
        }
        assertFalse(
            config.getJSONObject("experimental").getJSONObject("cache_file").has("store_fakeip")
        )
    }
}
