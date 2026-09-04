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
    fun `an explicit user demand for ipv6 survives the measurement`() {
        val plan = AddressFamilyPolicy.plan(
            settings = AppSettings(
                ipv6Enabled = true,
                preferIpv6 = true,
                measuredIpv6Unhealthy = true
            ),
            underlayHasIpv6 = true
        )
        assertEquals(IpFamilyPreference.IPV6_FIRST, plan.preference)
        assertTrue(plan.prioritizeIpv6)
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
}
