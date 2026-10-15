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

        assertTrue(text.contains("geoip:ir"))
    }

    @Test
    fun precisionNeverDuplicatesADomainTheUserAlreadyRouted() {
        val settings = AppSettings(

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
    fun measuredTagMembershipBelongsToItsOwnReadyFile() {
        val settings = AppSettings(
            geoAssetSourceIds = "chocolate4u-iran,loyalsoldier",
            geoMultiSourceEnabled = true,
            measuredGeoReadyFiles = "geosite.dat,geosite-loyalsoldier.dat",
            measuredGeoMembershipKnown = true,
            measuredGeoTagsByFile = mapOf(
                "geosite.dat" to setOf("ir", "category-ads-all"),
                "geosite-loyalsoldier.dat" to setOf("category-social-media")
            )
        )
        assertEquals(
            listOf("geosite:ir"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "ir")
        )
        assertEquals(
            listOf("ext:geosite-loyalsoldier.dat:category-social-media"),
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "category-social-media")
        )
        assertTrue(
            "a tag in a sibling database must not leak into another file's reference",
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "category-ads-all")
                .none { it.startsWith("ext:geosite-loyalsoldier.dat:") }
        )
        assertTrue(
            "no matching file membership means no emitted reference",
            RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "unknown-tag").isEmpty()
        )
    }

    @Test
    fun anExplicitEmptyMeasuredFileSetNeverFallsBackToCanonicalFiles() {
        val settings = AppSettings(
            geoAssetSourceIds = "chocolate4u-iran,loyalsoldier",
            geoMultiSourceEnabled = true,
            measuredGeoReadyFiles = "",
            measuredGeoMembershipKnown = true,
            measuredGeoTagsByFile = emptyMap()
        )
        assertTrue(RoutingEngine.readyGeoFiles(settings).isEmpty())
        assertTrue(RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOSITE, "ir").isEmpty())
        assertTrue(RoutingEngine.geoTokens(settings, GeoAssetRegistry.Kind.GEOIP, "ir").isEmpty())
    }

    @Test
    fun theSimulatorAnswersWithThePrecisionLayerWhenThatIsWhatDecided() {
        val curated = IranPrecisionPack.ENTRIES.first().domain
        val sim = RoutingEngine.simulate(AppSettings(), curated)
        val step = sim.steps.firstOrNull { it.title == "Domestic precision" }
        assertTrue("the precision layer reported itself", step != null)
        assertEquals(true, step?.matched)
        assertEquals(RoutingOutbound.DIRECT, sim.verdict)

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
