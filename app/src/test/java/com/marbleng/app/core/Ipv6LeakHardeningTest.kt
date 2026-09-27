package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Regression contract for the entire physical endpoint -> DNS -> fake IP -> TUN data path.
 * Config tests cannot simulate Android's VpnService; the dual-route TUN invariant is pinned by
 * NetworkPolicyTest and actual IPv6 egress must also be checked on a device. */
class Ipv6LeakHardeningTest {
    private val strict = AppSettings(addressFamilyMode = AddressFamilyMode.FORCE_IPV6)
    private val prefer = AppSettings(addressFamilyMode = AddressFamilyMode.PREFER_IPV6)

    private fun node(host: String): ProxyProfile {
        val outbound = JSONObject().put("tag", "proxy").put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
                .put("address", host).put("port", 443)
                .put("users", JSONArray().put(JSONObject()
                    .put("id", "11111111-2222-3333-4444-555555555555")
                    .put("encryption", "none"))))))
            .put("streamSettings", JSONObject().put("network", "tcp").put("security", "tls")
                .put("tlsSettings", JSONObject().put("serverName", "edge.example.org")))
        return ProxyProfile("fixture", "IPv6 fixture", "vless", "",
            JSONObject().put("outbounds", JSONArray().put(outbound)).toString(), host, 443)
    }

    private fun xray(host: String, settings: AppSettings): JSONObject = JSONObject(
        XrayConfigHardener.harden(node(host).configJson, 10808, settings,
            underlayHasIpv6 = true)
    )

    private fun singBox(host: String, settings: AppSettings, underlay: Boolean = true): JSONObject = JSONObject(
        SingBoxConfigBuilder.build(node(host), settings, 10808, 39090, "fixture", "", "cache.db",
            underlayHasIpv6 = underlay).json
    )

    private fun entries(root: JSONObject, path: String): List<JSONObject> {
        val array = root.getJSONArray(path)
        return (0 until array.length()).map(array::getJSONObject)
    }

    @Test fun forceIpv6RejectsPhysicalV4LiteralsIncludingMappedButAllowsLoopbackBridge() {
        listOf("192.0.2.1", "::ffff:192.0.2.1", "::ffff:c000:201").forEach { host ->
            assertTrue("$host", AddressFamilyPolicy.excludedIpv4Endpoint(host, strict))
            assertFalse(ProfilePreflightValidator.validate(node(host), settings = strict).valid)
            assertThrows(IllegalArgumentException::class.java) { xray(host, strict) }
            assertThrows(IllegalArgumentException::class.java) { singBox(host, strict) }
        }
        assertFalse(AddressFamilyPolicy.excludedIpv4Endpoint("127.0.0.1", strict))
        assertFalse(AddressFamilyPolicy.excludedIpv4Endpoint("::ffff:127.0.0.1", strict))
    }

    @Test fun forceIpv6UsesIpv6BootstrapAndNeverSilentlyFallsBackToIpv4() {
        val host = "edge.example.org"
        val xr = xray(host, strict)
        assertEquals("UseIPv6", xr.getJSONObject("dns").getString("queryStrategy"))
        val proxy = entries(xr, "outbounds").single { it.optString("tag") == "proxy" }
        assertEquals("ForceIPv6", proxy.getJSONObject("streamSettings")
            .getJSONObject("sockopt").getString("domainStrategy"))
        val bootstrap = entries(xr.getJSONObject("dns"), "servers")
            .filter { it.optString("address").startsWith("https+local://") }
        assertTrue(bootstrap.isNotEmpty())
        assertTrue(bootstrap.all { it.getString("address").contains('[') })
        val xrRules = entries(xr.getJSONObject("routing"), "rules")
        assertTrue(xrRules.any { it.optString("outboundTag") == "block" &&
            it.optJSONArray("ip")?.toString()?.contains("0.0.0.0/0") == true })

        val sb = singBox(host, strict)
        val servers = entries(sb.getJSONObject("dns"), "servers")
        val peers = servers.single { it.optString("tag") == SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG }
            .getJSONArray("servers")
        assertTrue(peers.length() > 0)
        for (i in 0 until peers.length()) {
            val peer = servers.single { it.getString("tag") == peers.getString(i) }
            assertEquals("marble-direct", peer.getString("detour"))
            assertTrue("Force IPv6 must have no IPv4 bootstrap peer", peer.getString("server").contains(':'))
        }
        val route = sb.getJSONObject("route")
        val resolver = route.getJSONObject("default_domain_resolver")
        assertEquals("ipv6_only", resolver.getString("strategy"))
        assertEquals(SingBoxConfigBuilder.DNS_BOOTSTRAP_TAG, resolver.getString("server"))
        assertEquals("ipv6_only", sb.getJSONObject("dns").getString("strategy"))
        assertTrue(entries(route, "rules").any { it.optString("action") == "reject" &&
            it.optJSONArray("ip_cidr")?.toString()?.contains("0.0.0.0/0") == true })
    }

    @Test fun preferIpv6KeepsIpv4ProxyUnderlayButResolvesExitAaaa() {
        val sb = singBox("192.0.2.1", prefer, underlay = false)
        assertEquals("prefer_ipv6", sb.getJSONObject("dns").getString("strategy"))
        assertEquals("prefer_ipv4", sb.getJSONObject("route")
            .getJSONObject("default_domain_resolver").getString("strategy"))
        val xr = xray("192.0.2.1", prefer)
        assertEquals("UseIP", xr.getJSONObject("dns").getString("queryStrategy"))
        assertFalse(entries(xr.getJSONObject("routing"), "rules").any {
            it.optString("outboundTag") == "block" &&
                it.optJSONArray("ip")?.toString()?.contains("0.0.0.0/0") == true
        })
    }

    @Test fun singBoxDoctorRefusesAnyConfigWithoutEncryptedIpLiteralDirectBootstrap() {
        val root = singBox("edge.example.org", strict)
        val dns = root.getJSONObject("dns")
        val kept = JSONArray()
        entries(dns, "servers").filterNot { it.optString("tag").startsWith("dns-bootstrap-") }
            .forEach(kept::put)
        dns.put("servers", kept)
        assertThrows(IllegalArgumentException::class.java) {
            SingBoxConfigDoctor.hardenForAndroid(root.toString())
        }
    }

    @Test fun singBoxDoctorRepairsAnImportedIpv4DialStrategyInForceIpv6Mode() {
        val root = singBox("edge.example.org", strict)
        root.getJSONObject("route").getJSONObject("default_domain_resolver")
            .put("strategy", "ipv4_only")
        val healed = SingBoxConfigDoctor.hardenForAndroid(root.toString())
        assertTrue(healed.repaired)
        assertEquals("ipv6_only", JSONObject(healed.json).getJSONObject("route")
            .getJSONObject("default_domain_resolver").getString("strategy"))
    }

    @Test fun singBoxFakeIpV6TokenIsProxiedAheadOfPrivateDirect() {
        val config = singBox("edge.example.org", prefer)
        val dnsServers = entries(config.getJSONObject("dns"), "servers")
        assertTrue(dnsServers.any { it.optString("type") == "fakeip" &&
            it.optString("inet6_range") == FakeIpPolicy.IPV6_POOL })
        val rules = entries(config.getJSONObject("route"), "rules")
        val fakeIndex = rules.indexOfFirst {
            it.optJSONArray("ip_cidr")?.toString()?.contains(FakeIpPolicy.IPV6_POOL) == true
        }
        val privateIndex = rules.indexOfFirst { it.optBoolean("ip_is_private") }
        assertTrue(fakeIndex >= 0)
        assertEquals("marble-proxy", rules[fakeIndex].getString("outbound"))
        if (privateIndex >= 0) assertTrue(fakeIndex < privateIndex)
    }
}
