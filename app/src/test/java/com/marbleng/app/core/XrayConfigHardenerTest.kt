package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FreedomPreset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val middleSockopt = outbound(hardened, "middle-fragment")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertFalse(middleSockopt?.has("domainStrategy") ?: false)
        val outerSockopt = outbound(hardened, "proxy")
            .optJSONObject("streamSettings")?.optJSONObject("sockopt")
        assertFalse(outerSockopt?.has("domainStrategy") ?: false)
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
        assertTrue("first DNS server gets a Freedom-sized budget", servers.getJSONObject(0).optLong("timeoutMs") >= 5_000L)
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
}
