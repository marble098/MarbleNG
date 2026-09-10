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
 * MARBLE_RESOLVER_SINKHOLE_V163 / MARBLE_SINGBOX_PINNED_PEER_V163 /
 * MARBLE_FREEDOM_SOCKOPT_STRATEGY_V163 — the runtime log that motivated this release, in tests.
 *
 * The log carried, side by side:
 *
 *  - `The "freedom.domainStrategy" setting is deprecated and will be removed … migrated to
 *    "sockopt.domainStrategy"` on every start — the hardener wrote the alias the core is about
 *    to drop, on every freedom hop;
 *  - `Post "https://dns.shecan.ir/dns-query": x509: certificate has expired or is not yet valid`
 *    followed by `context deadline exceeded` for the same endpoint — a domestic anti-sanction
 *    resolver, sitting in the user's primary slot, being asked *through the tunnel* and failing a
 *    full handshake on every lookup;
 *  - a VLESS/TCP/TLS node with `pcs=` and `vcn=` that "connected" on sing-box extended and moved
 *    no traffic, because the fork's parser drops both keys and verifies the fronted SNI.
 */
class ResolverSinkholeV163Test {

    private val t0 = 1_700_000_000_000L
    private companion object {
        const val PLAIN_LINK =
            "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
                "?encryption=none&security=tls&type=tcp&sni=example.com#Node+1"
    }
    private val shecan = "https://dns.shecan.ir/dns-query"
    private val cloudflare = "https://1.1.1.1/dns-query"
    private val google = "https://8.8.8.8/dns-query"
    private val quad9 = "https://9.9.9.9/dns-query"

    private fun expiredLine(endpoint: String) =
        "2026-09-08 22:01:10.000000 [Error] app/dns: failed to retrieve response for " +
            "www.google.com. > Post \"$endpoint\": tls: failed to verify certificate: x509: " +
            "certificate has expired or is not yet valid: current time 2026-09-08T22:01:10+03:30 " +
            "is after 2026-07-10T04:40:15Z"

    // ------------------------------------------------------------------ domestic resolvers

    @Test
    fun `domestic anti-sanction resolvers are recognised by host, ccTLD and literal`() {
        assertTrue(ResolverEvidencePolicy.isDomesticResolver(shecan))
        assertTrue(ResolverEvidencePolicy.isDomesticResolver("https://free.shecan.ir/dns-query"))
        assertTrue(ResolverEvidencePolicy.isDomesticResolver("tls://dns.electrotm.org"))
        assertTrue(ResolverEvidencePolicy.isDomesticResolver("https://anything.ir/dns-query"))
        assertTrue(ResolverEvidencePolicy.isDomesticResolver("https://178.22.122.100/dns-query"))
        assertFalse(ResolverEvidencePolicy.isDomesticResolver(cloudflare))
        assertFalse(ResolverEvidencePolicy.isDomesticResolver("https://dns.adguard-dns.com/dns-query"))
        assertFalse(ResolverEvidencePolicy.isDomesticResolver("https://irrelevant.example/dns-query"))
    }

    @Test
    fun `an expired certificate excludes the endpoint instead of merely demoting it`() {
        val evidence = ResolverEvidencePolicy.observe(
            sequenceOf(expiredLine(google)), emptyList(), t0
        )
        val pool = listOf(google, cloudflare, quad9)
        assertEquals(listOf(google), ResolverEvidencePolicy.excluded(pool, evidence, t0 + 60_000L))
        assertEquals(
            listOf(cloudflare, quad9),
            ResolverEvidencePolicy.withoutExcluded(pool, evidence, t0 + 60_000L)
        )
        // A proven answer after the failure clears the exclusion — a renewed certificate must
        // not stay banned for the TTL.
        val recovered = ResolverEvidencePolicy.recordSuccess(google, evidence, t0 + 120_000L)
        assertTrue(ResolverEvidencePolicy.excluded(pool, recovered, t0 + 180_000L).isEmpty())
        // And the exclusion expires on its own.
        assertTrue(
            ResolverEvidencePolicy.excluded(
                pool, evidence, t0 + ResolverEvidencePolicy.CERT_BROKEN_TTL_MS + 1L
            ).isEmpty()
        )
    }

    @Test
    fun `exclusion never empties the pool but never keeps a domestic resolver`() {
        val evidence = ResolverEvidencePolicy.observe(
            sequenceOf(expiredLine(cloudflare)), emptyList(), t0
        )
        // Only a cert-broken endpoint: kept as last resort rather than emitting nothing.
        assertEquals(listOf(cloudflare), ResolverEvidencePolicy.withoutExcluded(listOf(cloudflare), evidence, t0))
        // Cert-broken plus domestic: the cert-broken one survives, the domestic one never does.
        assertEquals(
            listOf(cloudflare),
            ResolverEvidencePolicy.withoutExcluded(listOf(shecan, cloudflare), evidence, t0)
        )
    }

    // ------------------------------------------------------------------ Xray resolver graph

    private fun proxySource(): String = JSONObject()
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
                                    .put("address", "node.example.com")
                                    .put("port", 443)
                                    .put("users", JSONArray().put(JSONObject().put("id", "x")))
                            )
                        )
                    )
                    .put("streamSettings", JSONObject().put("network", "tcp").put("security", "tls"))
            )
        )
        .toString()

    private fun remoteDnsAddresses(root: JSONObject): List<String> {
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        return (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .filter { it.optString("address").startsWith("https://") }
            .map { it.optString("address") }
    }

    @Test
    fun `the xray resolver graph never carries a domestic or cert-broken endpoint`() {
        val settings = AppSettings(
            dnsPrimaryDoH = shecan,
            dnsSecondaryDoH = google,
            measuredDnsExcludedEndpoints = google
        )
        val root = JSONObject(XrayConfigHardener.harden(proxySource(), 21080, settings))
        val remote = remoteDnsAddresses(root)
        assertTrue("remote pool must not be empty: $remote", remote.isNotEmpty())
        assertFalse("shecan must never be asked through the tunnel: $remote", remote.any { it.contains("shecan") })
        assertFalse("a cert-broken endpoint leaves the graph: $remote", remote.any { it == google })
        assertEquals("a healthy stock resolver leads", cloudflare, remote.first())
        // The non-adaptive path drops the domestic entry as well.
        val plain = JSONObject(
            XrayConfigHardener.harden(proxySource(), 21080, AppSettings(adaptiveDnsEnabled = false, dnsPrimaryDoH = shecan))
        )
        assertFalse(remoteDnsAddresses(plain).any { it.contains("shecan") })
    }

    // ------------------------------------------------------------------ freedom strategy

    @Test
    fun `no freedom hop ever carries the deprecated domainStrategy alias`() {
        val settings = AppSettings(fragmentEnabled = true, fragmentPackets = "tlshello")
        val root = JSONObject(XrayConfigHardener.harden(proxySource(), 21080, settings))
        val outbounds = root.getJSONArray("outbounds")
        var freedomSeen = 0
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(index)
            if (outbound.optString("protocol") !in setOf("freedom", "direct")) continue
            freedomSeen++
            val legacy = outbound.optJSONObject("settings")
            assertFalse(
                "freedom.domainStrategy is deprecated by the pinned core: ${outbound.optString("tag")}",
                legacy?.has("domainStrategy") == true
            )
            assertFalse(legacy?.has("targetStrategy") == true)
            assertFalse(outbound.has("targetStrategy"))
        }
        assertTrue("the fragment chain must contain a freedom hop", freedomSeen > 0)
    }

    @Test
    fun `an imported alias is migrated into sockopt not duplicated`() {
        val outbound = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIPv4").put("targetStrategy", "UseIPv4"))
            .put("targetStrategy", "UseIPv4")
        XrayConfigHardener.writeFreedomResolveStrategy(outbound, "ForceIPv4")
        assertFalse(outbound.getJSONObject("settings").has("domainStrategy"))
        assertFalse(outbound.getJSONObject("settings").has("targetStrategy"))
        assertFalse(outbound.has("targetStrategy"))
        assertEquals(
            "ForceIPv4",
            outbound.getJSONObject("streamSettings").getJSONObject("sockopt").getString("domainStrategy")
        )
        // AsIs means "write nothing", exactly as the core migrates it.
        XrayConfigHardener.writeFreedomResolveStrategy(outbound, "AsIs")
        assertFalse(outbound.getJSONObject("streamSettings").getJSONObject("sockopt").has("domainStrategy"))
    }

    // ------------------------------------------------------------------ sing-box pinned peer

    private val pinnedLink =
        "vless://2857d647-4d55-4a13-adc2-3b3063c735df@vps1.example.org:8443" +
            "?encryption=none&security=tls&type=tcp&flow=xtls-rprx-vision&sni=spotify.com&fp=chrome" +
            "&alpn=h2%2Chttp%2F1.1&pcs=" + "ab".repeat(32) + "%2C" + "cd".repeat(32) +
            "&vcn=vps1.example.org&allowInsecure=0#SOLIDVPS"

    private fun linkProfile(raw: String) = ProxyProfile(
        id = "solid", name = "SOLIDVPS", scheme = "vless", raw = raw, configJson = "",
        host = "vps1.example.org", port = 8443
    )

    @Test
    fun `a pcs or vcn share link is refused for sing-box before the parser can accept it`() {
        assertTrue(SingBoxConfigBuilder.linkCarriesPin(pinnedLink))
        assertFalse(SingBoxConfigBuilder.linkCarriesPin(PLAIN_LINK))
        val refusal = SingBoxConfigBuilder.pinnedPeerRefusal(linkProfile(pinnedLink))
        assertNotNull(refusal)
        assertTrue(refusal!!.startsWith("config-unsupported:"))
        assertTrue(refusal.contains("pinnedPeerCertSha256"))
        assertTrue("the user must be told which engine can honour the pin", refusal.contains("Xray"))
        assertNull(SingBoxConfigBuilder.pinnedPeerRefusal(linkProfile(PLAIN_LINK)))

        // The parser-first default must not become a candidate: "connected, no Internet".
        val support = SingBoxConfigBuilder.describe(linkProfile(pinnedLink), AppSettings(singBoxPreferParser = true))
        assertFalse(support.supported)
        assertEquals(refusal, support.reason)
        assertNull(
            SingBoxConfigBuilder.pinnedPeerRefusal(linkProfile("vless://x@h:1?security=tls&pcs=&vcn="))
        )
    }

    @Test
    fun `a pinned stored json is refused the same way`() {
        val json = JSONObject().put(
            "outbounds",
            JSONArray().put(
                JSONObject().put("protocol", "vless").put("tag", "proxy")
                    .put("settings", JSONObject().put("vnext", JSONArray().put(
                        JSONObject().put("address", "vps1.example.org").put("port", 8443)
                            .put("users", JSONArray().put(JSONObject().put("id", "x")))
                    )))
                    .put("streamSettings", JSONObject().put("network", "tcp").put("security", "tls")
                        .put("tlsSettings", JSONObject().put("serverName", "spotify.com")
                            .put("verifyPeerCertByName", "vps1.example.org")))
            )
        ).toString()
        val profile = ProxyProfile(id = "p", name = "p", scheme = "vless", raw = "", configJson = json)
        assertEquals(SingBoxConfigBuilder.PINNED_PEER_REFUSAL, SingBoxConfigBuilder.pinnedPeerRefusal(profile))
    }

    // ------------------------------------------------------------------ sing-box resolver graph

    @Test
    fun `the sing-box dns graph drops domestic and excluded endpoints and races on evidence`() {
        val settings = AppSettings(
            dnsPrimaryDoH = shecan,
            dnsSecondaryDoH = google,
            measuredDnsExcludedEndpoints = google,
            measuredDnsParallel = true
        )
        val config = JSONObject(
            SingBoxConfigBuilder.build(
                profile = linkProfile(PLAIN_LINK),
                settings = settings,
                socksPort = 10808,
                apiPort = 39090,
                apiSecret = "secret",
                logPath = "/data/local/tmp/singbox.log",
                cachePath = "/data/local/tmp/singbox-cache.db",
                resolverPool = listOf(shecan, google, quad9)
            ).json
        )
        val servers = config.getJSONObject("dns").getJSONArray("servers")
        val objects = (0 until servers.length()).map { servers.getJSONObject(it) }
        val remote = objects.filter { it.optString("tag").startsWith("dns-remote-") }
        assertTrue(remote.isNotEmpty())
        assertFalse(remote.any { it.optString("server").contains("shecan") })
        assertFalse(remote.any { it.optString("server") == "8.8.8.8" })
        assertEquals(listOf("9.9.9.9"), remote.map { it.optString("server") })
        val bootstrap = objects.filter { it.optString("tag").startsWith("dns-bootstrap-") }
        assertFalse(bootstrap.any { it.optString("server").contains("shecan") })
        val fallback = objects.single { it.optString("tag") == SingBoxConfigBuilder.DNS_REMOTE_TAG }
        assertEquals("parallel", fallback.getString("strategy"))
        val calm = JSONObject(
            SingBoxConfigBuilder.build(
                profile = linkProfile(PLAIN_LINK),
                settings = AppSettings(),
                socksPort = 10808, apiPort = 39090, apiSecret = "secret",
                logPath = "/tmp/l", cachePath = "/tmp/c"
            ).json
        )
        val calmServers = calm.getJSONObject("dns").getJSONArray("servers")
        val calmFallback = (0 until calmServers.length()).map { calmServers.getJSONObject(it) }
            .single { it.optString("tag") == SingBoxConfigBuilder.DNS_REMOTE_TAG }
        assertEquals("sequential", calmFallback.getString("strategy"))
    }
}
