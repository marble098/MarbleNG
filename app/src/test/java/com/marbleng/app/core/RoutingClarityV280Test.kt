package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MARBLE_ROUTING_CLARITY_V280 — the routing page offers two outcomes, the rules always run, and the
 * simulator walks the same list the core does.
 *
 * Every test here is a bug that the old page shipped: a mode that emitted nothing its own summary
 * promised, a saved rule that made Xray refuse the whole config, a switch that turned the rules off
 * on one core only, and a simulator that answered "the tunnel" for an address on the home network.
 */
class RoutingClarityV280Test {

    @Before
    fun resetGeoIndex() {
        GeoAssetIndex.resetForTests()
    }

    private fun proxyConfigSource(): String = JSONObject()
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
                                    .put("address", "1.1.1.1")
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
            )
        )
        .toString()

    private fun rule(
        id: String,
        kind: RoutingRuleKind = RoutingRuleKind.DOMAIN,
        matcher: String = "example.com",
        outbound: RoutingOutbound = RoutingOutbound.DIRECT,
        enabled: Boolean = true
    ) = RoutingRule(
        id = id,
        enabled = enabled,
        kind = kind,
        matcher = matcher,
        outbound = outbound
    )

    // ------------------------------------------------------------- two outcomes, not four modes

    @Test
    fun thePageWritesBothFlagsOfTheCountryPolicyTogether() {
        val base = AppSettings(routingMode = RoutingMode.PROXY_ALL, iranDomesticDirect = false)

        val iran = RoutingOverview.applyPolicy(base, RoutingOverview.Policy.IRAN_DIRECT)
        assertEquals(RoutingMode.GEO_DIRECT, iran.routingMode)
        assertTrue("the country layer has to be armed", iran.iranDomesticDirect)
        assertEquals(RoutingOverview.Policy.IRAN_DIRECT, RoutingOverview.policyOf(iran))

        val tunnel = RoutingOverview.applyPolicy(iran, RoutingOverview.Policy.TUNNEL_ALL)
        assertEquals(RoutingMode.PROXY_ALL, tunnel.routingMode)
        assertFalse(tunnel.iranDomesticDirect)
        assertEquals(RoutingOverview.Policy.TUNNEL_ALL, RoutingOverview.policyOf(tunnel))
    }

    @Test
    fun aPolicyChangeNeverTouchesTheRulesSomebodyWrote() {
        val written = AppSettings(
            routingRulesJson = RoutingEngine.serializeRules(listOf(rule("keep-me")))
        )
        val tunnel = RoutingOverview.applyPolicy(written, RoutingOverview.Policy.TUNNEL_ALL)

        assertEquals(
            "a policy change must never delete a rule",
            1,
            RoutingEngine.effectiveRules(tunnel).size
        )
        assertEquals(1, RoutingOverview.liveRules(tunnel).size)
    }

    @Test
    fun theCountryTagsComeBackIfAnOlderPassBlankedThem() {
        val blank = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            routeGeoIpTags = "",
            routeGeoSiteTags = ""
        )
        val armed = RoutingOverview.applyPolicy(blank, RoutingOverview.Policy.IRAN_DIRECT)

        assertTrue(armed.routeGeoIpTags.isNotBlank())
        assertTrue(armed.routeGeoSiteTags.isNotBlank())
    }

    @Test
    fun theIranSwitchStopsTheCountryRulesOnXrayToo() {
        val on = AppSettings(routingMode = RoutingMode.GEO_DIRECT, iranDomesticDirect = true)
        val off = on.copy(iranDomesticDirect = false)

        assertTrue(RoutingEngine.implicitRules(on).directSiteTags.isNotEmpty())
        assertTrue(RoutingEngine.implicitRules(off).directSiteTags.isEmpty())
        assertTrue(RoutingEngine.implicitRules(off).directIpTags.isEmpty())

        val rules = JSONArray()
        RoutingEngine.applyUserRules(rules, off, "proxy")
        assertFalse(
            "the switch has to mean the same thing on the core that reads geoip.dat",
            rules.toString().contains("geosite:ir")
        )
    }

    // ------------------------------------------------------------------ the rules always run

    @Test
    fun aSavedCountryRuleCannotMakeTheXrayConfigInvalid() {
        // The old page could reach this state in two taps: add a country-list rule (which switched
        // the mode to CUSTOM), then pick "Iranian sites stay direct". The emitter skipped the rule
        // because `customRoutingEnabled` was false, and the verifier demanded it — every connect
        // failed with a config error.
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            customRoutingEnabled = false,
            ipv6Enabled = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    rule("yt", kind = RoutingRuleKind.GEOSITE, matcher = "youtube", outbound = RoutingOutbound.PROXY)
                )
            )
        )

        val hardened = JSONObject(
            XrayConfigHardener.harden(proxyConfigSource(), 10808, settings, underlayHasIpv6 = false)
        )
        val text = hardened.getJSONObject("routing").getJSONArray("rules").toString()
        assertTrue("the saved rule has to be in the config", text.contains("geosite:youtube"))
    }

    @Test
    fun xrayAndSingBoxAgreeThatARuleIsAlwaysLive() {
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            customRoutingEnabled = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(rule("d", kind = RoutingRuleKind.DOMAIN, matcher = "example.com"))
            )
        )
        assertTrue(
            "the flag no longer gates a rule that is switched on",
            RoutingEngine.emittableUserRules(settings).size == 1
        )

        val rules = JSONArray()
        RoutingEngine.applyUserRules(rules, settings, "proxy")
        assertTrue(rules.toString().contains("domain:example.com"))
    }

    @Test
    fun theTunnelPolicyStillKeepsTheHomeNetworkOut() {
        val settings = AppSettings(routingMode = RoutingMode.PROXY_ALL, routeBypassPrivate = true)
        assertTrue(RoutingEngine.needsDirectOutbound(settings))

        val rules = JSONArray()
        RoutingEngine.applyUserRules(rules, settings, "proxy")
        assertTrue(rules.toString().contains("10.0.0.0/8"))
    }

    @Test
    fun theDomesticLayerStillNeedsADirectOutbound() {
        // Emitting `direct` rules without the outbound made Xray refuse the config; the outbound
        // decision has to know about the curated layer, not only about the country tags.
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            iranDomesticDirect = true,
            routeGeoIpTags = "",
            routeGeoSiteTags = "",
            routeBypassPrivate = false,
            routeBlockAds = false,
            geoPrecision = GeoPrecision.ENHANCED.id
        )
        assertTrue(RoutingEngine.precisionActive(settings))
        assertTrue(RoutingEngine.needsDirectOutbound(settings))
    }

    // -------------------------------------------------------------- what the page says is live

    @Test
    fun theLayersNameOnlyWhatTheFilesCanAnswer() {
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            iranDomesticDirect = true,
            routeBlockAds = true
        )
        val ready = RoutingOverview.layers(settings, geoIpReady = true, geoSiteReady = true)
            .map { it.title }
        assertTrue(ready.contains("Ads and trackers"))
        assertTrue(ready.contains("Iranian sites"))
        assertTrue(ready.contains("Iranian addresses"))
        assertTrue(ready.contains("Home network"))

        val missing = RoutingOverview.layers(settings, geoIpReady = false, geoSiteReady = false)
            .map { it.title }
        assertFalse("a layer whose file is absent must not be promised", missing.contains("Iranian sites"))
        assertFalse(missing.contains("Ads and trackers"))
        assertTrue("the home network needs no file", missing.contains("Home network"))
    }

    @Test
    fun theTunnelPolicyAddsNoCountryLayer() {
        val settings = AppSettings(
            routingMode = RoutingMode.PROXY_ALL,
            iranDomesticDirect = false,
            routeBlockAds = false,
            geoPrecision = GeoPrecision.MAXIMUM.id
        )
        val titles = RoutingOverview.layers(settings).map { it.title }
        assertEquals(listOf("Home network"), titles)
    }

    @Test
    fun everyAdvertisedOutcomeHasASentenceAPersonWouldSay() {
        RoutingOverview.Policy.entries.forEach { policy ->
            assertTrue(RoutingOverview.title(policy).isNotBlank())
            assertTrue(
                "${policy.id} still speaks in mechanism words",
                com.marbleng.app.model.PlainVoice.isPlain(RoutingOverview.summary(policy))
            )
            assertTrue(RoutingOverview.note(policy).isNotBlank())
        }
    }

    // ---------------------------------------------------------------- the simulator tells the truth

    @Test
    fun theHomeNetworkNeverRidesTheTunnel() {
        val settings = AppSettings(routingMode = RoutingMode.PROXY_ALL, routeBypassPrivate = true)
        val sim = RoutingEngine.simulate(settings, "192.168.1.20")
        assertEquals(RoutingOutbound.DIRECT, sim.verdict)
        assertTrue(sim.verdictReason.contains("own network"))
    }

    @Test
    fun aWrittenRuleBeatsTheDomesticLayerAndTheSimulatorShowsIt() {
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            iranDomesticDirect = true,
            geoPrecision = GeoPrecision.ENHANCED.id,
            routeBypassPrivate = false,
            routeBlockAds = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(rule("d", matcher = "domain:digikala.com", outbound = RoutingOutbound.PROXY))
            )
        )
        assertEquals(
            "the curated layer would say direct; the written rule says tunnel and it is read first",
            RoutingOutbound.PROXY,
            RoutingEngine.simulate(settings, "digikala.com").verdict
        )
    }

    @Test
    fun aPausedRuleIsSkippedInsteadOfWinning() {
        val settings = AppSettings(
            routingMode = RoutingMode.PROXY_ALL,
            routeBypassPrivate = false,
            routeBlockAds = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(rule("off", matcher = "example.com", outbound = RoutingOutbound.BLOCK, enabled = false))
            )
        )
        val sim = RoutingEngine.simulate(settings, "example.com")
        assertEquals(RoutingOutbound.PROXY, sim.verdict)
        assertTrue(sim.steps.any { it.skipped })
    }

    @Test
    fun aMissingSiteListIsNamedInsteadOfGuessed() {
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            iranDomesticDirect = true,
            geoPrecision = GeoPrecision.STANDARD.id,
            routeBypassPrivate = false,
            routeBlockAds = false
        )
        val sim = RoutingEngine.simulate(settings, "digikala.com", geoIpReady = false, geoSiteReady = false)
        assertEquals(RoutingOutbound.PROXY, sim.verdict)
        assertTrue(
            "the page has to say why the country layer could not answer",
            sim.steps.any { it.detail.contains("not downloaded") }
        )
    }
}
