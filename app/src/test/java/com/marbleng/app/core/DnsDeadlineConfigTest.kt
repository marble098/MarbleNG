package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LINK_DEADLINE_V133 / MARBLE_MEASURED_FAMILY_V133 emitted-config regressions.
 *
 * These lock the two config-level defects that made a healthy core deliver no internet:
 *
 *  - every encrypted DNS server carried a fixed 1350/1650 ms budget even though those queries are
 *    routed through the tunnel, so a ~1.1 s link produced nothing but `DoH deadline` errors and
 *    Xray could not resolve a single destination domain;
 *  - the address-family plan re-derived "IPv6 first" from the underlay alone, discarding the
 *    measured verdict, so a node whose IPv6 was known-broken was still dialled IPv6-first.
 */
class DnsDeadlineConfigTest {

    private val slowCellular = LinkEvidence(
        rttMs = 1126.0,
        tailRttMs = 1326.0,
        jitterMs = 100.0,
        lossPercent = 2.0,
        samples = 12
    )

    private fun proxySource(): String = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
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
                    .put(
                        "streamSettings",
                        JSONObject()
                            .put("network", "tcp")
                            .put("security", "tls")
                    )
            )
        )
        .toString()

    private fun hardened(settings: AppSettings, link: LinkEvidence): JSONObject =
        JSONObject(XrayConfigHardener.harden(proxySource(), 21080, settings, link))

    private fun hardened(
        settings: AppSettings,
        underlayHasIpv6: Boolean
    ): JSONObject =
        JSONObject(
            XrayConfigHardener.harden(
                proxySource(), 21080, settings, LinkEvidence.UNKNOWN, underlayHasIpv6
            )
        )

    /** Addresses of the underlay-direct `https+local://` bootstrap resolvers, in emitted order. */
    private fun bootstrapAddresses(root: JSONObject): List<String> {
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        return (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .filter { it.optString("address").startsWith("https+local://") }
            .map { it.optString("address") }
    }

    /** Timeouts of the encrypted resolvers that answer ordinary app DNS (not the +local bootstrap). */
    private fun remoteDnsTimeouts(root: JSONObject): List<Long> {
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        return (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .filter { it.optString("address").startsWith("https://") }
            .map { it.optLong("timeoutMs") }
    }

    @Test
    fun `an unmeasured link keeps the legacy dns budgets`() {
        val timeouts = remoteDnsTimeouts(hardened(AppSettings(), LinkEvidence.UNKNOWN))
        assertTrue("expected at least two encrypted resolvers, got $timeouts", timeouts.size >= 2)
        assertEquals(LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS, timeouts.first())
        assertEquals(LinkDeadlinePolicy.SECONDARY_DNS_FLOOR_MS, timeouts[1])
    }

    @Test
    fun `a measured slow link gets dns budgets that survive three round trips`() {
        val timeouts = remoteDnsTimeouts(hardened(AppSettings(), slowCellular))
        val minimum = 3 * slowCellular.tailRttMs.toLong()
        timeouts.forEach { timeout ->
            assertTrue(
                "resolver budget $timeout cannot survive 3x tail RTT ($minimum ms) — this is the " +
                    "exact budget that produced the DoH deadline storm",
                timeout >= minimum
            )
            assertTrue("resolver budget $timeout exceeded the ceiling", timeout <= 10_000L)
        }
        assertTrue(
            "the primary budget must grow with the link",
            timeouts.first() > LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS
        )
    }

    @Test
    fun `the endpoint bootstrap resolvers keep their own direct budgets`() {
        // https+local resolvers dial the underlay directly, not through the tunnel, so the tunnel
        // RTT must not inflate them.
        val root = hardened(AppSettings(), slowCellular)
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        val bootstrap = (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .filter { it.optString("address").startsWith("https+local://") }
        assertTrue("the endpoint hostname needs its bootstrap resolvers", bootstrap.isNotEmpty())
        bootstrap.forEach { entry ->
            assertTrue(
                "bootstrap budget ${entry.optLong("timeoutMs")} drifted from the direct-link budget",
                entry.optLong("timeoutMs") in 2_500L..3_000L
            )
        }
    }

    @Test
    fun `dns stays encrypted-only and serial whatever the link looks like`() {
        val root = hardened(AppSettings(), slowCellular)
        val dns = root.getJSONObject("dns")
        assertFalse(
            "parallel fan-out must stay off so one dead resolver cannot stall the others",
            dns.optBoolean("enableParallelQuery", true)
        )
        assertTrue("optimistic cache must stay on", dns.optBoolean("serveStale", false))
    }

    /** Addresses of the encrypted resolvers that answer ordinary app DNS, in emitted order. */
    private fun remoteDnsAddresses(root: JSONObject): List<String> {
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        return (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .filter { it.optString("address").startsWith("https://") }
            .map { it.optString("address") }
    }

    // ------------------------------------------------------------------
    // MARBLE_RESOLVER_EVIDENCE_V134 — the emitted resolver graph follows the evidence
    // ------------------------------------------------------------------

    @Test
    fun `the emitted resolver list keeps three independent encrypted providers`() {
        val addresses = remoteDnsAddresses(hardened(AppSettings(), slowCellular))
        assertEquals(3, addresses.size)
        assertEquals("https://1.1.1.1/dns-query", addresses[0])
        assertEquals(3, addresses.distinct().size)
    }

    @Test
    fun `a demoted resolver moves to the end of the emitted list`() {
        // 29 attributed `DoH deadline` events used to change nothing: this list was a fixed order
        // forever, so a disrupted endpoint kept its rank and every cold lookup paid its full
        // RTT-derived deadline before failover reached a resolver that could answer.
        val settings = AppSettings(
            measuredDnsDemotedEndpoints = "https://1.1.1.1/dns-query"
        )
        val addresses = remoteDnsAddresses(hardened(settings, slowCellular))
        assertEquals(3, addresses.size)
        assertEquals(
            "a healthy provider must take the primary slot",
            "https://8.8.8.8/dns-query",
            addresses[0]
        )
        assertEquals(
            "the demoted provider stays in the graph as the last fallback, it is never deleted",
            "https://1.1.1.1/dns-query",
            addresses.last()
        )
    }

    @Test
    fun `demoting every provider leaves the configured order alone`() {
        val settings = AppSettings(
            measuredDnsDemotedEndpoints = "https://1.1.1.1/dns-query,https://8.8.8.8/dns-query," +
                "https://9.9.9.9/dns-query"
        )
        val addresses = remoteDnsAddresses(hardened(settings, slowCellular))
        assertEquals(
            listOf(
                "https://1.1.1.1/dns-query",
                "https://8.8.8.8/dns-query",
                "https://9.9.9.9/dns-query"
            ),
            addresses
        )
    }

    @Test
    fun `the endpoint bootstrap resolvers are never demoted or reordered`() {
        val settings = AppSettings(
            measuredDnsDemotedEndpoints = "https://1.1.1.1/dns-query",
            measuredDnsParallel = true
        )
        val root = hardened(settings, slowCellular)
        val servers = root.getJSONObject("dns").getJSONArray("servers")
        val bootstrap = (0 until servers.length())
            .map { servers.getJSONObject(it) }
            .filter { it.optString("address").startsWith("https+local://") }
        assertTrue("the node hostname still needs its bootstrap resolvers", bootstrap.isNotEmpty())
        bootstrap.forEach { entry ->
            assertTrue(
                "bootstrap resolvers dial the underlay, not the tunnel: their budget must not move",
                entry.optLong("timeoutMs") in 2_500L..3_000L
            )
            assertTrue(entry.optBoolean("skipFallback", false))
        }
        // They are emitted before the tunnel resolvers, so the node hostname can still be resolved
        // while the tunnel's own encrypted providers are being raced.
        assertTrue(
            servers.getJSONObject(0).optString("address").startsWith("https+local://")
        )
    }

    @Test
    fun `resolver racing is armed only by measured evidence`() {
        val serial = hardened(AppSettings(), slowCellular).getJSONObject("dns")
        assertFalse(
            "serial failover is the default: one query, one answer, no fan-out",
            serial.optBoolean("enableParallelQuery", true)
        )

        val raced = hardened(
            AppSettings(measuredDnsParallel = true),
            slowCellular
        ).getJSONObject("dns")
        assertTrue(
            "a decisively failing provider in the emitted list is exactly what racing is for",
            raced.optBoolean("enableParallelQuery", false)
        )

        val userOptedOut = hardened(
            AppSettings(adaptiveDnsEnabled = false, measuredDnsParallel = true),
            slowCellular
        ).getJSONObject("dns")
        assertFalse(
            "adaptive DNS off means the deterministic serial graph the user asked for",
            userOptedOut.optBoolean("enableParallelQuery", true)
        )
    }

    @Test
    fun `a measured unhealthy ipv6 demotes the family order`() {
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(
                ipv6Enabled = true,
                adaptiveDualStackEnabled = false,
                measuredIpv6Unhealthy = true
            ),
            underlayHasIpv6 = true
        )
        assertEquals(IpFamilyPreference.DUAL, plan.preference)
        assertFalse(
            "a measured-broken family must not be dialled first",
            plan.prioritizeIpv6
        )
        assertEquals("dual-v4-first", plan.reason)
    }

    @Test
    fun `a strict ipv6 demand survives the measurement`() {
        // MARBLE_SMART_FAMILY_V136 — Prefer IPv6 ships ON and is an ordering, so a measured-broken
        // family demotes the default plan automatically. The demand a measurement cannot override
        // is the strict v6 configuration (UseIPv6): the user asked for v6 only and must see it
        // fail as v6-only, not as a silent IPv4 success.
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(
                ipv6Enabled = true,
                preferIpv6 = true,
                dnsQueryStrategy = "UseIPv6",
                measuredIpv6Unhealthy = true
            ),
            underlayHasIpv6 = true
        )
        assertEquals(IpFamilyPreference.IPV6_ONLY, plan.preference)
        assertTrue(plan.prioritizeIpv6)
        assertEquals("UseIPv6", plan.dnsQueryStrategy)
    }

    @Test
    fun `the race delay in the plan scales with the measured rtt`() {
        val armed = AppSettings(ipv6Enabled = true, adaptiveDualStackEnabled = true)
        val fast = AddressFamilyPolicy.plan(armed, underlayHasIpv6 = true)
        val slow = AddressFamilyPolicy.plan(armed, underlayHasIpv6 = true, link = slowCellular)
        assertTrue("the race must stay armed", fast.raceEnabled && slow.raceEnabled)
        assertEquals(60, fast.tryDelayMs)
        assertTrue(
            "a 60 ms delay on a 1126 ms link can never be won; got ${slow.tryDelayMs}",
            slow.tryDelayMs > fast.tryDelayMs
        )
    }

    @Test
    fun `a disarmed race is never re-armed by scaling`() {
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(ipv6Enabled = true, happyEyeballsTryDelayMs = 0),
            underlayHasIpv6 = true,
            link = slowCellular
        )
        assertFalse(plan.raceEnabled)
        assertEquals(0, plan.tryDelayMs)
    }

    // ------------------------------------------------------------------
    // MARBLE_IPV6_DNS_PURGE_V135 — the underlay verdict purges the resolver graph
    // ------------------------------------------------------------------

    @Test
    fun `an ipv4-only underlay emits no ipv6 bootstrap resolver`() {
        // The attached log's Wi-Fi had no global IPv6, yet the resolver graph still carried
        // `[2606:4700:4700::1111]`-class literals; each one burned its whole timeout with
        // `context deadline exceeded` before failover could reach a resolver that answers.
        val root = hardened(AppSettings(), underlayHasIpv6 = false)
        val bootstrap = bootstrapAddresses(root)
        assertTrue("the node hostname still needs bootstrap resolvers", bootstrap.isNotEmpty())
        bootstrap.forEach { address ->
            assertFalse(
                "an unreachable v6 resolver survived on an IPv4-only underlay: $address",
                address.contains("[") || address.substringAfter("https+local://").contains(':')
            )
        }
    }

    @Test
    fun `a user-configured ipv6 resolver is dropped on an ipv4-only underlay`() {
        val settings = AppSettings(
            dnsPrimaryIp = "2606:4700:4700::1111",
            dnsSecondaryIp = "2001:4860:4860::8888"
        )
        val root = hardened(settings, underlayHasIpv6 = false)
        val bootstrap = bootstrapAddresses(root)
        assertTrue("stock IPv4 literals must still resolve the node", bootstrap.isNotEmpty())
        bootstrap.forEach { address ->
            assertFalse(
                "a configured-but-unreachable v6 resolver must be purged: $address",
                address.substringAfter("https+local://").startsWith("[")
            )
        }
    }

    @Test
    fun `a v6-capable underlay keeps ipv6 bootstrap resolvers`() {
        val root = hardened(AppSettings(ipv6Enabled = true), underlayHasIpv6 = true)
        val bootstrap = bootstrapAddresses(root)
        assertTrue(bootstrap.isNotEmpty())
        assertTrue(
            "a v6-capable underlay should keep at least one v6 bootstrap resolver",
            bootstrap.any { it.substringAfter("https+local://").startsWith("[") }
        )
    }

    @Test
    fun `turning ipv6 off purges v6 resolvers even on a v6-capable underlay`() {
        val root = hardened(AppSettings(ipv6Enabled = false), underlayHasIpv6 = true)
        val bootstrap = bootstrapAddresses(root)
        bootstrap.forEach { address ->
            assertFalse(
                "IPv6 disabled but a v6 resolver was emitted: $address",
                address.substringAfter("https+local://").startsWith("[")
            )
        }
    }
}
