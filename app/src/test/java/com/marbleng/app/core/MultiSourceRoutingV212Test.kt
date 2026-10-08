package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_MULTI_SOURCE_ROUTING_V212 — several databases, and the precision layer.
 *
 * Two contracts, both of which fail in a way the user cannot see:
 *
 *  - **fail-closed emission.** A geo rule names a file. If the file is not on disk the core
 *    refuses to load the whole config, so a source that has not reported itself ready must
 *    contribute *no* token rather than a hopeful one — the tunnel still connects, with the
 *    sources that did answer.
 *  - **the precision layer is a DIRECT rule set**, so it belongs only in the mode whose job is
 *    keeping domestic traffic here. In CUSTOM the user's own list is the policy; in PROXY_ALL
 *    nothing is direct by design. Emitting it anyway would be a product-authored override of a
 *    choice the user made, and invisible in Settings.
 */
class MultiSourceRoutingV212Test {

    @Test
    fun withOneSourceTheTokensAreTheOnesEveryEarlierReleaseWrote() {
        val settings = AppSettings()
        assertEquals(
            listOf("geoip:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, "ir")
        )
        assertEquals(
            listOf("geosite:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "ir")
        )
    }

    @Test
    fun aSecondEnabledSourceAddsItsOwnFileReferenceNextToTheCanonicalOne() {
        val settings = twoSources(
            readyFiles = "geoip.dat,geosite.dat,geoip-loyalsoldier.dat,geosite-loyalsoldier.dat"
        )
        assertEquals(
            listOf("geoip:ir", "ext:geoip-loyalsoldier.dat:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, "ir")
        )
        assertEquals(
            listOf("geosite:ir", "ext:geosite-loyalsoldier.dat:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "ir")
        )
    }

    @Test
    fun aSourceWhoseFileIsNotOnDiskContributesNoToken() {
        // Only the canonical pair reported itself: the second source is enabled but its download
        // has not landed. A reference to a missing file is a config the core will not load.
        val settings = twoSources(readyFiles = "geoip.dat,geosite.dat")
        assertEquals(
            listOf("geoip:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, "ir")
        )
    }

    @Test
    fun nothingReportedMeansAssumeTheCanonicalPairAndNoMore() {
        val settings = twoSources(readyFiles = "")
        assertEquals(
            setOf(GeoAssetRegistry.PRIMARY_GEOIP_FILE, GeoAssetRegistry.PRIMARY_GEOSITE_FILE),
            RoutingEngine.readyGeoFiles(settings)
        )
        assertEquals(
            listOf("geoip:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, "ir")
        )
    }

    @Test
    fun theReportedFileListIgnoresJunkAndKeepsOnlyDatFiles() {
        val settings = AppSettings(measuredGeoReadyFiles = " geoip.dat , notes.txt, ,geosite.dat;geoip.dat ")
        assertEquals(
            setOf("geoip.dat", "geosite.dat"),
            RoutingEngine.readyGeoFiles(settings)
        )
    }

    @Test
    fun everyEnabledSourceIsAskedForTheAdCategorySoBlockingIsTheUnion() {
        val settings = twoSources(
            readyFiles = "geoip.dat,geosite.dat,geoip-loyalsoldier.dat,geosite-loyalsoldier.dat"
        )
        val rulesOut = JSONArray()
        RoutingEngine.applyUserRules(rulesOut, settings, "proxy")
        val text = rulesOut.toString()
        assertTrue(text.contains("geosite:category-ads-all"))
        assertTrue(text.contains("ext:geosite-loyalsoldier.dat:category-ads-all"))
    }

    @Test
    fun precisionIsTheDefaultAndOnlyInAModeThatKeepsDomesticTrafficHere() {
        val defaults = AppSettings()
        assertEquals(GeoPrecision.DEFAULT, GeoPrecision.ENHANCED)
        assertTrue("the shipped default separates domestic traffic", RoutingEngine.precisionActive(defaults))
        assertFalse(
            "the geo tags level means no curated rules at all",
            RoutingEngine.precisionActive(defaults.copy(geoPrecision = GeoPrecision.STANDARD.id))
        )
        assertFalse(
            "CUSTOM is strictly the user's own rules",
            RoutingEngine.precisionActive(defaults.copy(routingMode = RoutingMode.CUSTOM))
        )
        assertFalse(
            "PROXY_ALL is never direct",
            RoutingEngine.precisionActive(defaults.copy(routingMode = RoutingMode.PROXY_ALL))
        )
        assertFalse(
            "the domestic switch is the user's, not the product's",
            RoutingEngine.precisionActive(defaults.copy(iranDomesticDirect = false))
        )
    }

    @Test
    fun precisionEmitsChunkedDomainRulesInTheDefaultConfig() {
        val rulesOut = JSONArray()
        RoutingEngine.applyUserRules(rulesOut, AppSettings(), "proxy")
        val text = rulesOut.toString()
        assertTrue("the country TLD is a direct rule", text.contains("domain:ir"))
        assertTrue(
            "a curated domain is a direct rule",
            text.contains("domain:${IranPrecisionPack.domains(GeoPrecision.ENHANCED).first()}")
        )
        assertFalse("the strongest level's keywords are not in the default", text.contains("keyword:"))
    }

    @Test
    fun theStrongestLevelAddsKeywordsAndTheGeoLevelRemovesEverything() {
        val maximum = JSONArray()
        RoutingEngine.applyUserRules(
            maximum,
            AppSettings(geoPrecision = GeoPrecision.MAXIMUM.id),
            "proxy"
        )
        assertTrue(maximum.toString().contains("keyword:${IranPrecisionPack.KEYWORDS.first()}"))

        val standard = JSONArray()
        RoutingEngine.applyUserRules(
            standard,
            AppSettings(geoPrecision = GeoPrecision.STANDARD.id),
            "proxy"
        )
        val text = standard.toString()
        assertFalse(text.contains("domain:ir"))
        assertFalse(text.contains("keyword:"))
        // The geo tags themselves are untouched by the dial: it adds, it never replaces.
        assertTrue(text.contains("geoip:ir"))
    }

    @Test
    fun precisionNeverDuplicatesADomainTheUserAlreadyRouted() {
        val settings = AppSettings(
            // The user's own rules are opt-in, so the test has to opt in: the point is the
            // interaction between the user's rule and the product's, not one of them alone.
            customRoutingEnabled = true,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    RoutingRule(
                        id = "user-direct",
                        kind = RoutingRuleKind.DOMAIN,
                        matcher = "domain:${IranPrecisionPack.domains(GeoPrecision.ENHANCED).first()}",
                        outbound = RoutingOutbound.DIRECT
                    )
                )
            )
        )
        val rulesOut = JSONArray()
        RoutingEngine.applyUserRules(rulesOut, settings, "proxy")
        val text = rulesOut.toString()
        val domain = IranPrecisionPack.domains(GeoPrecision.ENHANCED).first()
        assertEquals(
            "one rule per domain, whichever layer wrote it first",
            1,
            Regex(Regex.escape("domain:$domain")).findAll(text).count()
        )
    }

    @Test
    fun aRuleIsNeverEmittedForATagNoEnabledSourceCanAnswer() {
        // v2fly-geoip publishes geoip only: enabling it must not create a geosite reference.
        val settings = AppSettings(
            geoAssetSourceIds = "chocolate4u-iran,v2fly-geoip",
            geoMultiSourceEnabled = true,
            measuredGeoReadyFiles = "geoip.dat,geosite.dat,geoip-v2fly-geoip.dat"
        )
        assertEquals(
            listOf("geoip:ir", "ext:geoip-v2fly-geoip.dat:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, "ir")
        )
        assertEquals(
            listOf("geosite:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "ir")
        )
    }

    @Test
    fun theSimulatorAnswersWithThePrecisionLayerWhenThatIsWhatDecided() {
        // The page that has to explain "why did a domestic site go through the tunnel" is the
        // simulator, so the layer has to appear in it — and only when it is what answered.
        val curated = IranPrecisionPack.ENTRIES.first().domain
        val sim = RoutingEngine.simulate(AppSettings(), curated)
        val step = sim.steps.firstOrNull { it.title == "Domestic precision" }
        assertTrue("the precision layer reported itself", step != null)
        assertEquals(true, step?.matched)
        assertEquals(RoutingOutbound.DIRECT, sim.verdict)

        // The geo-tags level has no curated knowledge, so the same host is not claimed by it.
        val tagsOnly = RoutingEngine.simulate(
            AppSettings(geoPrecision = GeoPrecision.STANDARD.id),
            curated
        )
        assertFalse(tagsOnly.steps.any { it.title == "Domestic precision" })
    }

    private fun twoSources(readyFiles: String): AppSettings = AppSettings(
        geoAssetSourceIds = "chocolate4u-iran,loyalsoldier",
        geoMultiSourceEnabled = true,
        measuredGeoReadyFiles = readyFiles
    )
}
