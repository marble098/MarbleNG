package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MARBLE_GEO_READY_GATE_V145 — geo routing is disabled until its databases are really present.
 *
 * The connect path used to hard-fail when the selected policy needed geoip.dat/geosite.dat and
 * neither a downloaded nor a bundled copy existed, which is a fresh install on a censored link:
 * the product could not connect at all, over an optimisation. These tests pin the replacement
 * contract — the policy degrades, the user's stored settings are never rewritten, and a complete
 * pair of databases changes nothing at all.
 */
class GeoAssetGateTest {

    @Before
    fun resetGeoIndex() {
        GeoAssetIndex.resetForTests()
    }

    private fun iranianDefaults(): AppSettings = AppSettings(
        routingMode = RoutingMode.GEO_DIRECT,
        routeGeoIpTags = "ir,private",
        routeGeoSiteTags = "ir",
        routeBlockAds = true,
        routeDirectDomains = "geosite:ir,intra.example.com",
        routeDirectIps = "geoip:ir,geoip:private,10.8.0.0/24"
    )

    @Test
    fun readyAssetsChangeNothing() {
        val settings = iranianDefaults()
        val gated = RoutingEngine.withGeoAssetGate(settings, geoIpReady = true, geoSiteReady = true)
        assertEquals(settings, gated)
        assertEquals("", RoutingEngine.geoDowngradeReason(settings, true, true))
    }

    @Test
    fun missingGeoIpDowngradesTheModeButKeepsPrivateDirect() {
        val gated = RoutingEngine.withGeoAssetGate(
            iranianDefaults(),
            geoIpReady = false,
            geoSiteReady = true
        )
        assertEquals(RoutingMode.BYPASS_PRIVATE, gated.routingMode)
        assertTrue(gated.routeBypassPrivate)
        assertEquals("", gated.routeGeoIpTags)
        // Literal CIDRs and the database-free "private" token survive; country tags do not.
        assertTrue(gated.routeDirectIps.contains("10.8.0.0/24"))
        assertTrue(gated.routeDirectIps.contains("geoip:private"))
        assertFalse(gated.routeDirectIps.contains("geoip:ir"))
        assertFalse(RoutingEngine.needsGeoIp(gated))
    }

    @Test
    fun missingGeoSiteDisablesAdBlockingAndGeoSiteTokens() {
        val gated = RoutingEngine.withGeoAssetGate(
            iranianDefaults(),
            geoIpReady = true,
            geoSiteReady = false
        )
        assertFalse(gated.routeBlockAds)
        assertEquals("", gated.routeGeoSiteTags)
        assertTrue(gated.routeDirectDomains.contains("intra.example.com"))
        assertFalse(gated.routeDirectDomains.contains("geosite:ir"))
        assertFalse(RoutingEngine.needsGeoSite(gated))
    }

    @Test
    fun neitherDatabasePresentLeavesAConfigTheEngineCanLoad() {
        val gated = RoutingEngine.withGeoAssetGate(
            iranianDefaults(),
            geoIpReady = false,
            geoSiteReady = false
        )
        assertFalse(RoutingEngine.needsGeoIp(gated))
        assertFalse(RoutingEngine.needsGeoSite(gated))
    }

    @Test
    fun userRulesThatNeedAMissingDatabaseAreDisabledNotDeleted() {
        val rules = listOf(
            RoutingRule(
                id = "r1",
                kind = RoutingRuleKind.GEOSITE,
                matcher = "geosite:category-ads-all",
                outbound = RoutingOutbound.BLOCK
            ),
            RoutingRule(
                id = "r2",
                kind = RoutingRuleKind.DOMAIN,
                matcher = "example.com",
                outbound = RoutingOutbound.PROXY
            )
        )
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            routeBlockAds = false,
            customRoutingEnabled = true,
            routingRulesJson = RoutingEngine.serializeRules(rules)
        )
        val gated = RoutingEngine.withGeoAssetGate(settings, geoIpReady = true, geoSiteReady = false)
        val gatedRules = RoutingEngine.parseRules(gated.routingRulesJson)
        assertEquals(2, gatedRules.size)
        assertFalse("the geosite rule cannot run yet", gatedRules.first { it.id == "r1" }.enabled)
        assertTrue("a plain domain rule is unaffected", gatedRules.first { it.id == "r2" }.enabled)
        // The user's own stored settings are untouched: the gate is applied to a copy only.
        assertTrue(RoutingEngine.parseRules(settings.routingRulesJson).all { it.enabled })
    }

    @Test
    fun theReasonNamesExactlyTheMissingDatabases() {
        val settings = iranianDefaults()
        assertTrue(RoutingEngine.geoDowngradeReason(settings, false, true).contains("geoip.dat"))
        assertTrue(RoutingEngine.geoDowngradeReason(settings, true, false).contains("geosite.dat"))
        val both = RoutingEngine.geoDowngradeReason(settings, false, false)
        assertTrue(both.contains("geoip.dat") && both.contains("geosite.dat"))
        // A policy that needs no database never claims to be waiting for one.
        val plain = AppSettings(routingMode = RoutingMode.PROXY_ALL, routeBlockAds = false)
        assertEquals("", RoutingEngine.geoDowngradeReason(plain, false, false))
    }
}
