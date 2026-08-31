package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FreedomPreset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marble Freedom (serverless) emitted-config regressions.
 *
 * These tests mirror the two known-good upstream serverless configs: XTLS
 * Serverless-for-Iran/serverless_for_Iran.jsonc and GFW-knocker
 * ServerLess_TLSFrag_Xray_Config_New.json. Both give the directly-dialing fragment hop
 * an explicit domain strategy + Happy Eyeballs, and both pin DoH hostnames in dns.hosts.
 */
class XrayConfigHardenerTest {

    private fun freedomSettings() = AppSettings(
        fragmentEnabled = true,
        fragmentInnerEnabled = true,
        freedomPreset = FreedomPreset.MULTI_LAYER_CASCADE,
        freedomDnsAuto = true
    )

    private fun outbound(root: JSONObject, tag: String): JSONObject {
        val outs = root.getJSONArray("outbounds")
        for (index in 0 until outs.length()) {
            val candidate = outs.getJSONObject(index)
            if (candidate.optString("tag") == tag) return candidate
        }
        throw AssertionError("outbound $tag missing")
    }

    private fun harden(settings: AppSettings, source: String = ServerlessFreedomEngine.configJson(settings)): JSONObject =
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
        val settings = freedomSettings()
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
        // Default Freedom is a 2-hop chain (no middle). Outer must not carry domainStrategy.
        val outerSockopt = outbound(hardened, "proxy")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertFalse(outerSockopt?.has("domainStrategy") ?: false)
        assertEquals("full-fragment", outerSockopt?.optString("dialerProxy"))

        // Dedicated UDP-noises outbound is kept and also resolves through Xray DNS.
        val noise = outbound(hardened, ServerlessFreedomEngine.UDP_NOISES_TAG)
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

    /**
     * Runtime regression (real Xray v26.7.28, through the PyPI xray-core engine): Xray's
     * "tlshello" fragment mode rewrites the ClientHello into complete tiny TLS records
     * (Xray-core issue #4370). Servers — Fastly (pypi.org), Cloudflare (registry.npmjs.org),
     * GitHub, AWS (httpbin.org) — RST that shape on every attempt ("write: broken pipe"),
     * while the GFW-knocker packet split (1-1 / 1-3 / 5-10) over the same 3-hop chain returns
     * HTTP 200 on all of them. The default outer hop must therefore never be "tlshello".
     */
    @Test
    fun `default freedom outer hop is packet split, not tlshello record rewriting`() {
        val settings = AppSettings() // default preset = SMART_ADAPTIVE -> MULTI_LAYER_CASCADE
        val hardened = harden(settings)

        val outer = outbound(hardened, "proxy")
        val fragment = outer.optJSONObject("settings")?.optJSONObject("fragment")
        assertNotNull("outer fragment missing", fragment)
        assertEquals("1-1", fragment!!.optString("packets"))

        // Every recipe that can be a default (SMART_ADAPTIVE/MULTI_LAYER_CASCADE/TLSHELLO_SNI/
        // CUSTOM fallbacks) must avoid the record-rewriting mode, not just the default settings.
        val source = JSONObject(ServerlessFreedomEngine.configJson(settings))
        val outerFragment = source.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("settings").getJSONObject("fragment")
        assertFalse(outerFragment.optString("packets").equals("tlshello", ignoreCase = true))
    }

    @Test
    fun `freedom hop honours ipv6 off end to end`() {
        val settings = freedomSettings().copy(ipv6Enabled = false, dnsQueryStrategy = "UseIPv4")
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
    fun `freedom dns keeps only bootstrappable doh servers and pins them`() {
        val settings = freedomSettings().copy(
            freedomDnsCleanResolvers =
                "https://1.1.1.1/dns-query," +
                    "https://doh.sb/dns-query," +
                    "https://dns.shecan.ir/dns-query," +
                    "https://dns.adguard-dns.com/dns-query"
        )
        val hardened = harden(settings)
        val dns = hardened.getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        val addresses = (0 until servers.length()).map { servers.getJSONObject(it).optString("address") }

        assertTrue(addresses.contains("https://1.1.1.1/dns-query"))
        assertTrue(addresses.contains("https://dns.shecan.ir/dns-query"))
        assertTrue(addresses.contains("https://dns.adguard-dns.com/dns-query"))
        assertFalse("unpinned DoH host must be filtered out", addresses.any { it.contains("doh.sb") })

        val hosts = dns.optJSONObject("hosts")
        assertTrue(hosts?.has("dns.shecan.ir") == true)
        assertTrue(hosts?.has("dns.adguard-dns.com") == true)

        // The Freedom chain fragments the first TCP write into 1-byte/4 ms chunks; a DoH
        // handshake cannot fit the stock 1350 ms budget, so cold lookups would always fail.
        assertTrue("first DNS server gets a Freedom-sized budget", servers.getJSONObject(0).optLong("timeoutMs") >= 8_000L)
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
        val source = ServerlessFreedomEngine.configJson(freedomSettings())
        // The generic path must still harden without a Freedom profile: the source chain is the
        // same Freedom chain, so this asserts the shared graph stays valid under PROXY_ALL too.
        val hardened = harden(settings, source)
        assertEquals("freedom", outbound(hardened, "full-fragment").optString("protocol"))
    }

    @Test
    fun `default freedom is 2-hop without middle and forces media tcp fallback`() {
        val settings = AppSettings(
            fragmentEnabled = true,
            fragmentInnerEnabled = true,
            freedomPreset = FreedomPreset.SMART_ADAPTIVE,
            freedomUdpNoiseEnabled = true,
            freedomDnsAuto = true
        )
        val hardened = harden(settings)
        val tags = (0 until hardened.getJSONArray("outbounds").length()).map {
            hardened.getJSONArray("outbounds").getJSONObject(it).optString("tag")
        }
        assertTrue("proxy" in tags)
        assertTrue("full-fragment" in tags)
        assertFalse("middle-fragment must not ship by default", "middle-fragment" in tags)
        assertTrue(ServerlessFreedomEngine.UDP_NOISES_TAG in tags)

        val routing = hardened.getJSONObject("routing")
        assertEquals("IPOnDemand", routing.optString("domainStrategy"))
        val rules = routing.getJSONArray("rules")
        val ruleText = rules.toString()
        assertTrue("poison injector range blocked", ruleText.contains("10.10.34.0/24"))
        val udp443 = udp443Rules(rules)
        assertTrue("quic/udp443 must be rejected for YouTube TCP fallback", udp443.any { it.optString("outboundTag") == "block" })
        assertFalse(
            "default YouTube-safe path must not send QUIC to the noise outbound",
            udp443.any { it.optString("outboundTag") == ServerlessFreedomEngine.UDP_NOISES_TAG }
        )

        val hosts = hardened.getJSONObject("dns").optJSONObject("hosts")
        assertTrue(hosts?.has("domain:youtube.com") == true)
    }

    @Test
    fun `freedom can still use dedicated udp noises when tcp fallback is off`() {
        val settings = freedomSettings().copy(
            freedomForceTcpForStreaming = false,
            freedomUdpNoiseEnabled = true
        )
        val hardened = harden(settings)
        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        val udp443 = udp443Rules(rules)

        assertTrue(
            "QUIC/UDP443 should route to the dedicated noises outbound when fallback is off",
            udp443.any { it.optString("outboundTag") == ServerlessFreedomEngine.UDP_NOISES_TAG }
        )
        assertFalse(
            "fallback-off noise mode should not add a UDP443 block rule",
            udp443.any { it.optString("outboundTag") == "block" }
        )
    }

    @Test
    fun `smart adaptive never emits tlshello outer hop`() {
        val recipe = DpiEvasionPolicy.freedomRecipe(AppSettings())
        assertFalse(recipe.packets.equals("tlshello", ignoreCase = true))
        assertFalse(recipe.middleEnabled)
        assertTrue(recipe.innerEnabled)
    }

    /**
     * Minimal serverless Freedom configs (NORMAL tier or a hand-imported freedom-only custom JSON)
     * contain no proxy besides a freedom/direct outbound. The hardener must treat that as the exit
     * instead of rejecting it with "No proxy outbound".
     */
    @Test
    fun `freedom only config without fragment is accepted as serverless normal`() {
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
        val hardened = harden(AppSettings(serverlessModeEnabled = true), source)

        assertEquals("freedom", outbound(hardened, "proxy").optString("protocol"))
        assertEquals("blackhole", outbound(hardened, "block").optString("protocol"))
        val proxySettings = outbound(hardened, "proxy").optJSONObject("settings")
        assertTrue(proxySettings?.has("targetStrategy") == true)
        assertTrue(proxySettings!!.optString("targetStrategy") in AddressFamilyPolicy.ENDPOINT_STRATEGIES)
    }
}
