package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigHardenerTest {
    private fun handImportedFreedomChain(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray()
                .put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "freedom")
                        .put(
                            "settings",
                            JSONObject()
                                .put("domainStrategy", "AsIs")
                                .put(
                                    "fragment",
                                    JSONObject()
                                        .put("packets", "1-1")
                                        .put("length", "1-3")
                                        .put("interval", "5-10")
                                )
                        )
                        .put(
                            "streamSettings",
                            JSONObject()
                                .put("sockopt", JSONObject().put("dialerProxy", "full-fragment"))
                        )
                )
                .put(
                    JSONObject()
                        .put("tag", "full-fragment")
                        .put("protocol", "freedom")
                        .put(
                            "settings",
                            JSONObject()
                                .put("domainStrategy", "AsIs")
                                .put(
                                    "fragment",
                                    JSONObject()
                                        .put("packets", "1-1")
                                        .put("length", "1")
                                        .put("interval", "4")
                                        .put("maxSplit", 517)
                                )
                        )
                )
                .put(
                    JSONObject()
                        .put("tag", "udp-noises")
                        .put("protocol", "freedom")
                        .put(
                            "settings",
                            JSONObject()
                                .put(
                                    "noises",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("type", "rand")
                                            .put("packet", "10-20")
                                            .put("interval", "1-3")
                                    )
                                )
                        )
                )
        )
        .toString()

    private fun chainSourceWithoutNoises(): String {
        val root = JSONObject(handImportedFreedomChain())
        val outbounds = root.getJSONArray("outbounds")
        val kept = org.json.JSONArray()
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(index)
            if (outbound.optJSONObject("settings")?.optJSONArray("noises") == null) kept.put(outbound)
        }
        root.put("outbounds", kept)
        return root.toString()
    }

    private fun fragmentChainSettings() = AppSettings()

    private fun outbound(root: JSONObject, tag: String): JSONObject {
        val outs = root.getJSONArray("outbounds")
        for (index in 0 until outs.length()) {
            val candidate = outs.getJSONObject(index)
            if (candidate.optString("tag") == tag) return candidate
        }
        throw AssertionError("outbound $tag missing")
    }

    private fun harden(
        settings: AppSettings,
        source: String = handImportedFreedomChain()
    ): JSONObject =
        JSONObject(XrayConfigHardener.harden(source, 21080, settings))

    private fun udp443Rules(rules: org.json.JSONArray): List<JSONObject> =
        (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .filter { rule ->
                val protocols = rule.optJSONArray("protocol")
                rule.optString("network") == "udp" &&
                    (rule.optString("port") == "443" ||
                        (protocols != null && (0 until protocols.length()).any { protocols.optString(it) == "quic" }))
            }

    @Test
    fun `innermost freedom hop resolves user destinations through xray dns`() {
        val settings = fragmentChainSettings()
        val hardened = harden(settings)

        val inner = outbound(hardened, "full-fragment")
        assertEquals("freedom", inner.optString("protocol"))
        val innerSettings = inner.optJSONObject("settings")

        assertFalse("settings.domainStrategy is deprecated", innerSettings?.has("domainStrategy") == true)
        assertFalse("settings.targetStrategy is deprecated", innerSettings?.has("targetStrategy") == true)
        val innerSockopt = inner.optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertTrue("sockopt.domainStrategy must be written", innerSockopt?.has("domainStrategy") == true)
        assertTrue(
            "sockopt.domainStrategy must be a plan value",
            innerSockopt!!.optString("domainStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES
        )

        val outerSockopt = outbound(hardened, "proxy")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertFalse(outerSockopt?.has("domainStrategy") ?: false)
        assertEquals("full-fragment", outerSockopt?.optString("dialerProxy"))

        val noise = outbound(hardened, "udp-noises")
        assertTrue((noise.optJSONObject("settings")?.optJSONArray("noises")?.length() ?: 0) > 0)
        val noiseSettings = noise.optJSONObject("settings")
        assertFalse(noiseSettings?.has("domainStrategy") == true)
        assertFalse(noiseSettings?.has("targetStrategy") == true)

        val noiseSockopt = noise.optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertTrue(noiseSockopt?.has("domainStrategy") == true)
        assertTrue(
            noiseSockopt!!.optString("domainStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES
        )
    }

    @Test
    fun `freedom hop honours ipv6 off end to end`() {
        val settings = fragmentChainSettings().copy(ipv6Enabled = false, dnsQueryStrategy = "UseIPv4")
        val hardened = harden(settings)

        val innerSettings = outbound(hardened, "full-fragment")
            .optJSONObject("settings")
        assertFalse(innerSettings?.has("domainStrategy") == true)
        val innerSockopt = outbound(hardened, "full-fragment")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertTrue(innerSockopt?.optString("domainStrategy")?.equals("ForceIPv4", true) == true)
        assertFalse(innerSockopt?.has("happyEyeballs") ?: false)
    }

    @Test
    fun `generic fragment mode keeps endpoint resolution on the node`() {
        val settings = AppSettings(
            routingMode = com.marbleng.app.model.RoutingMode.PROXY_ALL
        )

        val hardened = harden(settings)
        assertEquals("freedom", outbound(hardened, "full-fragment").optString("protocol"))
    }

    @Test
    fun `iran mode blocks poison injector ranges and media udp is opt in`() {
        val settings = fragmentChainSettings().copy(iranModePolicy = IranModePolicy.ALWAYS_ON)

        val hardened = harden(settings, chainSourceWithoutNoises())

        val routing = hardened.getJSONObject("routing")
        assertEquals("IPIfNonMatch", routing.optString("domainStrategy"))
        val rules = routing.getJSONArray("rules")
        val ruleText = rules.toString()
        assertTrue("poison injector range blocked", ruleText.contains("10.10.34.0/24"))

        val udp443 = udp443Rules(rules)
        assertTrue(udp443.isEmpty())
    }

    @Test
    fun `iran mode with udp blocked rejects media udp before the app stalls on retransmits`() {
        val settings = fragmentChainSettings().copy(
            iranModePolicy = IranModePolicy.ALWAYS_ON,
            muxUdp443 = "reject"
        )
        val hardened = harden(settings)
        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        val udp443 = udp443Rules(rules)
        assertTrue("quic/udp443 must be rejected for TCP fallback", udp443.any { it.optString("outboundTag") == "block" })
    }

    @Test
    fun `dedicated udp noises outbound receives quic when tcp fallback is off`() {
        val settings = fragmentChainSettings().copy(muxUdp443 = "skip")
        val hardened = harden(settings)
        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        val udp443 = udp443Rules(rules)

        assertTrue(
            "QUIC/UDP443 should route to the dedicated noises outbound when it exists",
            udp443.any { it.optString("outboundTag") == "udp-noises" }
        )
        assertFalse(
            "noise mode should not add a UDP443 block rule",
            udp443.any { it.optString("outboundTag") == "block" }
        )
    }

    @Test
    fun `iran mode off keeps the poison ranges out of the rules`() {
        val hardened = harden(fragmentChainSettings())
        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        assertFalse("poison injector range must not appear", rules.toString().contains("10.10.34.0/24"))
    }

    @Test
    fun `domain endpoint gets an independent bootstrap resolver ladder`() {
        val settings = AppSettings(
            routingMode = com.marbleng.app.model.RoutingMode.PROXY_ALL,
            routeBlockAds = false
        )
        val source = JSONObject()
            .put(
                "outbounds",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "vless")
                        .put(
                            "settings",
                            JSONObject().put(
                                "vnext",
                                org.json.JSONArray().put(
                                    JSONObject()
                                        .put("address", "node.example.net")
                                        .put("port", 443)
                                        .put(
                                            "users",
                                            org.json.JSONArray().put(
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
                            JSONObject()
                                .put("network", "tcp")
                                .put("security", "tls")
                        )
                )
            )
            .toString()

        val dns = harden(settings, source).getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        val addresses = (0 until servers.length()).map { servers.getJSONObject(it).optString("address") }
        val bootstrap = addresses.filter { it.startsWith("https+local://") }

        assertTrue("a domain endpoint must own a bootstrap ladder", bootstrap.size >= 4)
        assertTrue(
            "the user's configured resolver stays first",
            bootstrap.first().contains(settings.dnsPrimaryIp)
        )
        assertEquals("bootstrap literals are unique", bootstrap.size, bootstrap.distinct().size)
        assertTrue(
            "every bootstrap entry keeps skipFallback so ordinary DNS never leaks to it",
            (0 until servers.length())
                .map { servers.getJSONObject(it) }
                .filter { it.optString("address").startsWith("https+local://") }
                .all { it.optBoolean("skipFallback") }
        )
        assertTrue(
            "the endpoint hostname is the only domain the bootstrap servers claim",
            (0 until servers.length())
                .map { servers.getJSONObject(it) }
                .filter { it.optString("address").startsWith("https+local://") }
                .all { server ->
                    val domains = server.getJSONArray("domains")
                    domains.length() == 1 && domains.optString(0) == "full:node.example.net"
                }
        )
    }

    private fun vlessDomainSource(): String = JSONObject()
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
                                    .put("address", "node.example.net")
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

    @Test
    fun `iran mode applies longer tcp liveness to the tunnel outbound`() {
        val baseline = harden(fragmentChainSettings(), vlessDomainSource())
        val iran = harden(
            fragmentChainSettings().copy(iranModePolicy = IranModePolicy.ALWAYS_ON),
            vlessDomainSource()
        )

        val baseSockopt = outbound(baseline, "proxy")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        val iranSockopt = outbound(iran, "proxy")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")

        assertEquals(60, baseSockopt?.optInt("tcpKeepAliveIdle"))
        assertEquals(15, baseSockopt?.optInt("tcpKeepAliveInterval"))
        assertEquals(60_000, baseSockopt?.optInt("tcpUserTimeout"))

        assertEquals(108, iranSockopt?.optInt("tcpKeepAliveIdle"))
        assertEquals(22, iranSockopt?.optInt("tcpKeepAliveInterval"))
        assertEquals(120_000, iranSockopt?.optInt("tcpUserTimeout"))
    }

    @Test
    fun `freedom only config without fragment is accepted as a hand imported exit`() {
        val source = JSONObject()
            .put(
                "outbounds",
                org.json.JSONArray().put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "freedom")
                        .put(
                            "settings",
                            JSONObject()
                                .put("domainStrategy", "ForceIPv6v4")
                                .put("targetStrategy", "ForceIPv6v4")
                        )
                )
            )
            .toString()
        val hardened = harden(AppSettings(), source)

        assertEquals("freedom", outbound(hardened, "proxy").optString("protocol"))
        assertEquals("blackhole", outbound(hardened, "block").optString("protocol"))
        val proxySettings = outbound(hardened, "proxy").optJSONObject("settings")

        assertFalse(proxySettings?.has("targetStrategy") == true)
        assertFalse(proxySettings?.has("domainStrategy") == true)
        val proxySockopt = outbound(hardened, "proxy").optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertTrue(proxySockopt?.has("domainStrategy") == true)
        assertTrue(proxySockopt!!.optString("domainStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES)
    }
}
