package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.IranModePolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emitted-config regressions for the hardener's generic handling of fragment chains.
 *
 * These tests mirror the two known-good upstream serverless configs: XTLS
 * Serverless-for-Iran/serverless_for_Iran.jsonc and GFW-knocker
 * ServerLess_TLSFrag_Xray_Config_New.json. Both give the directly-dialing fragment hop
 * an explicit domain strategy + Happy Eyeballs. Marble no longer ships the Freedom engine,
 * but a hand-imported fragment chain must still be hardened exactly the same way.
 */
class XrayConfigHardenerTest {

    /** A hand-imported 2-hop freedom fragment chain plus a dedicated UDP-noises outbound. */
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

    /** The same chain with the dedicated noises outbound removed. */
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

    private fun fragmentChainSettings() = AppSettings(
        fragmentEnabled = true,
        fragmentInnerEnabled = true
    )

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
        assertTrue("settings.domainStrategy must be written", innerSettings?.has("domainStrategy") == true)
        assertTrue(
            "settings.domainStrategy must be a plan value",
            innerSettings!!.optString("domainStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES
        )
        val innerSockopt = inner.optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertTrue("sockopt.domainStrategy must be written", innerSockopt?.has("domainStrategy") == true)

        // Only the hop that opens the real socket resolves; the dialer hops just bridge.
        val outerSockopt = outbound(hardened, "proxy")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertFalse(outerSockopt?.has("domainStrategy") ?: false)
        assertEquals("full-fragment", outerSockopt?.optString("dialerProxy"))

        // Dedicated UDP-noises outbound is kept and also resolves through Xray DNS.
        val noise = outbound(hardened, "udp-noises")
        assertTrue((noise.optJSONObject("settings")?.optJSONArray("noises")?.length() ?: 0) > 0)
        val noiseSettings = noise.optJSONObject("settings")
        assertTrue(noiseSettings?.has("domainStrategy") == true)
        // Official XTLS gives the dedicated UDP path targetStrategy (ForceIPv6v4). Marble must
        // emit the same outbound-level field so the PacketWriter resolves through Xray DNS.
        assertTrue(noiseSettings?.has("targetStrategy") == true)
        assertTrue(
            noiseSettings!!.optString("targetStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES
        )
    }

    @Test
    fun `freedom hop honours ipv6 off end to end`() {
        val settings = fragmentChainSettings().copy(ipv6Enabled = false, dnsQueryStrategy = "UseIPv4")
        val hardened = harden(settings)

        val innerSettings = outbound(hardened, "full-fragment")
            .optJSONObject("settings")
        assertTrue(innerSettings?.optString("domainStrategy")?.equals("ForceIPv4", true) == true)
        val innerSockopt = outbound(hardened, "full-fragment")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertTrue(innerSockopt?.optString("domainStrategy")?.equals("ForceIPv4", true) == true)
        assertFalse(innerSockopt?.has("happyEyeballs") ?: false)
    }

    @Test
    fun `generic fragment mode keeps endpoint resolution on the node`() {
        val settings = AppSettings(
            fragmentEnabled = true,
            fragmentInnerEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "100-200",
            fragmentInterval = "10-20",
            routingMode = com.marbleng.app.model.RoutingMode.PROXY_ALL
        )
        // The generic path must still harden a hand-imported fragment chain under PROXY_ALL:
        // this asserts the shared graph stays valid there too.
        val hardened = harden(settings)
        assertEquals("freedom", outbound(hardened, "full-fragment").optString("protocol"))
    }

    @Test
    fun `iran mode blocks poison injector ranges and media udp is opt in`() {
        val settings = fragmentChainSettings().copy(iranModePolicy = IranModePolicy.ALWAYS_ON)
        // A chain without a dedicated noises outbound: with muxUdp443 at its default no
        // QUIC/UDP443 rule is emitted at all, so media keeps trying QUIC through the tunnel.
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
    fun `no default recipe emits the tlshello record rewriter`() {
        // Runtime regression (real Xray v26.7.28): the "tlshello" fragment mode rewrites the
        // ClientHello into complete tiny TLS records (Xray-core #4370) and servers RST that
        // shape. Every recipe the connection ladder can pick must use the packet split instead.
        val states = listOf(
            IranModeState(active = true),
            IranModeState(active = true, techniques = setOf(CensorTechnique.SNI_FILTERING)),
            IranModeState(
                active = true,
                techniques = setOf(CensorTechnique.SNI_FILTERING, CensorTechnique.TCP_RESET)
            ),
            IranModeState(active = true, techniques = setOf(CensorTechnique.NATIONAL_INTRANET))
        )
        states.forEach { state ->
            val recipe = DpiEvasionPolicy.connectionRecipe(state)
            assertFalse(
                "connectionRecipe must never emit tlshello",
                recipe.packets.equals("tlshello", ignoreCase = true)
            )
        }
    }

    /**
     * Minimal serverless-style configs (a hand-imported freedom-only custom JSON) contain no
     * proxy besides a freedom/direct outbound. The hardener must treat that as the exit
     * instead of rejecting it with "No proxy outbound".
     */
    /**
     * MARBLE_ENDPOINT_BOOTSTRAP_V132 — the node hostname is resolved by the `https+local://`
     * bootstrap servers and by nothing else (Xray's list-1 / disableFallbackIfMatch flow), so
     * the bootstrap set must never collapse to a single filtered resolver.
     */
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
        assertTrue(proxySettings?.has("targetStrategy") == true)
        assertTrue(proxySettings!!.optString("targetStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES)
    }
}
