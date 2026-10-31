package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import com.marbleng.app.model.parseGeoPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MARBLE_ROUTING_REBUILD_V254 — the routing page may only show what the core actually does.
 *
 * The complaint that started this pass was not "the page is ugly", it was "the page makes no
 * sense": it rendered storage instead of the decision, and it offered controls whose combinations
 * the core could not honour. These tests pin the rules the rebuilt page is built on, so the page
 * cannot drift back into a second, decorative policy:
 *
 *  - the mode and the rule-list switch describe one decision, never two;
 *  - every layer that really emits rules is named in the summary, and no layer that is off is;
 *  - a rule list is only "live" when the mode says it is;
 *  - the mistakes that used to be silent (duplicates, regexp) are visible.
 */
class RoutingOverviewV254Test {

    @Before
    fun resetGeoIndex() {
        GeoAssetIndex.resetForTests()
    }

    private fun rule(
        id: String,
        kind: RoutingRuleKind = RoutingRuleKind.DOMAIN,
        matcher: String = "example.com",
        outbound: RoutingOutbound = RoutingOutbound.DIRECT,
        enabled: Boolean = true,
        remark: String = "",
        port: String = ""
    ) = RoutingRule(
        id = id,
        enabled = enabled,
        kind = kind,
        matcher = matcher,
        outbound = outbound,
        remark = remark,
        port = port
    )

    // ---------------------------------------------------------------- one decision, one switch

    @Test
    fun choosingTheCustomModeIsTheOnlyWayToTurnTheRuleListOn() {
        val before = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            customRoutingEnabled = false
        )

        val custom = RoutingOverview.decide(before, RoutingMode.CUSTOM)
        assertEquals(RoutingMode.CUSTOM, custom.routingMode)
        assertTrue("the rule list has to run in the custom mode", custom.customRoutingEnabled)

        // Leaving the custom mode pauses the list but never deletes a single rule.
        val written = custom.copy(
            routingRulesJson = RoutingEngine.serializeRules(listOf(rule("keep-me")))
        )
        val geo = RoutingOverview.decide(written, RoutingMode.GEO_DIRECT)
        assertEquals(RoutingMode.GEO_DIRECT, geo.routingMode)
        assertFalse(geo.customRoutingEnabled)
        assertEquals(
            "a mode change never loses a rule that was written by hand",
            1,
            RoutingEngine.effectiveRules(geo).size
        )
    }

    @Test
    fun theBypassModeIsTheOneThatAddsTheHomeRanges() {
        val plain = AppSettings(routingMode = RoutingMode.PROXY_ALL, routeBypassPrivate = false)
        assertFalse(
            "away from the bypass mode nothing claims the home ranges",
            RoutingOverview.sections(plain).any { it.title == "Home network direct" }
        )
        val bypass = RoutingOverview.decide(plain, RoutingMode.BYPASS_PRIVATE)
        assertTrue(
            "the bypass mode is the one that adds the private ranges",
            RoutingOverview.sections(bypass).any { it.title == "Home network direct" }
        )
    }

    @Test
    fun theSummaryOfEveryModeSaysWhatHappensToOrdinaryTraffic() {
        RoutingMode.entries.forEach { mode ->
            val summary = RoutingOverview.summary(mode)
            assertTrue("$mode has no summary", summary.isNotBlank())
            assertTrue(
                "$mode still speaks in mechanism words: $summary",
                com.marbleng.app.model.PlainVoice.isPlain(summary)
            )
            assertTrue("$mode has no note", RoutingOverview.note(mode).isNotBlank())
        }
    }

    // ------------------------------------------------- what the page claims is really emitting

    @Test
    fun theGeoModeNamesItsThreeLayersAndNothingElse() {
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            routeBlockAds = true,
            geoPrecision = GeoPrecision.STANDARD.id,
            customRoutingEnabled = false
        )
        val titles = RoutingOverview.sections(settings).map { it.title }
        assertTrue("ad blocking is missing from the summary", titles.contains("Ad and tracker sites blocked"))
        assertTrue("home network direct is missing", titles.contains("Home network direct"))
        assertTrue("Iranian addresses are missing", titles.contains("Iranian addresses direct"))
        assertTrue("Iranian sites are missing", titles.contains("Iranian sites direct"))
        assertFalse(
            "a rule list nobody runs must not be advertised",
            titles.contains("Your own rules")
        )
        assertFalse(
            "the domestic precision layer is off at the standard level",
            titles.contains("Domestic precision")
        )
    }

    @Test
    fun switchingAdBlockingOffRemovesTheAdLayerInsteadOfShowingZero() {
        val settings = AppSettings(routingMode = RoutingMode.GEO_DIRECT, routeBlockAds = false)
        assertFalse(
            RoutingOverview.sections(settings).any { it.title == "Ad and tracker sites blocked" }
        )
    }

    @Test
    fun theDomesticPrecisionLayerAppearsOnlyWhenItAddsSomething() {
        val enhanced = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            geoPrecision = GeoPrecision.ENHANCED.id,
            iranDomesticDirect = true
        )
        val section = RoutingOverview.sections(enhanced).firstOrNull { it.title == "Domestic precision" }
        assertTrue("the enhanced level adds curated sites", section != null && section.count > 0)

        val standard = enhanced.copy(geoPrecision = GeoPrecision.STANDARD.id)
        assertNull(
            "the standard level adds nothing, so it must not be listed",
            RoutingOverview.sections(standard).firstOrNull { it.title == "Domestic precision" }
        )

        val off = enhanced.copy(iranDomesticDirect = false)
        assertNull(
            "Iranian sites staying direct can switch the whole layer off",
            RoutingOverview.sections(off).firstOrNull { it.title == "Domestic precision" }
        )
    }

    @Test
    fun theExpertListsAreCountedWithTheSameSplitTheCoreUses() {
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            customRoutingEnabled = true,
            routeDirectDomains = "a.example, b.example",
            routeDirectIps = "1.1.1.1\n10.0.0.0/8",
            routeBlockDomains = "ads.example; tracker.example",
            routeProxyDomains = ""
        )
        val sections = RoutingOverview.sections(settings)
        val direct = sections.first { it.title == "Your direct list" }
        assertEquals(4, direct.count)
        val blocked = sections.first { it.title == "Your block list" }
        assertEquals(2, blocked.count)
        assertFalse(
            "an empty expert list is not a layer",
            sections.any { it.title == "Always through the tunnel" }
        )
    }

    @Test
    fun aLiveRuleListIsCountedByItsEnabledRules() {
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            customRoutingEnabled = true,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    rule("on", outbound = RoutingOutbound.DIRECT),
                    rule("off", matcher = "other.example", enabled = false)
                )
            )
        )
        val section = RoutingOverview.sections(settings).first { it.title == "Your own rules" }
        assertEquals("only the rule that runs is counted", 1, section.count)
    }

    // ------------------------------------------------------------------ the two silent mistakes

    @Test
    fun theSameMatcherTwiceIsReportedAgainstTheFirstOne() {
        val rules = listOf(
            rule("first", matcher = "example.com"),
            rule("copy", matcher = " example.com "),
            rule("blocked", matcher = "example.com", outbound = RoutingOutbound.BLOCK)
        )
        val duplicates = RoutingOverview.duplicateMatchers(rules)
        assertEquals(setOf("copy"), duplicates.keys)
        assertEquals("first", duplicates["copy"])
        assertNull(
            "the same address with a different outcome is a real, different rule",
            duplicates["blocked"]
        )
    }

    @Test
    fun aRegexRuleIsFlaggedWhereverItHides() {
        val rules = listOf(
            rule("plain", matcher = "example.com"),
            rule("exact", kind = RoutingRuleKind.DOMAIN, matcher = "regexp:^.+\\.example"),
            rule("second-in-list", kind = RoutingRuleKind.DOMAIN, matcher = "shop.example, regexp:^cdn\\d+"),
            rule("not-a-regex", matcher = "myregexp.example")
        )
        val flagged = RoutingOverview.regexRules(rules).map { it.id }
        assertEquals(listOf("exact", "second-in-list"), flagged)
    }

    @Test
    fun theActivePresetIsReadFromTheRulesThemselves() {
        val installed = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            customRoutingEnabled = true,
            routingRulesJson = RoutingEngine.serializeRules(
                RoutingPresets.materialize(RoutingPresets.Preset.RECOMMENDED)
            )
        )
        assertEquals(
            RoutingPresets.Preset.RECOMMENDED.id,
            RoutingOverview.activePresetId(installed)
        )
        assertNull(
            "an empty list is not the recommended preset",
            RoutingOverview.activePresetId(AppSettings(routingRulesJson = "[]"))
        )
    }

    @Test
    fun thePageAndTheCoreAgreeOnTheShipDefault() {
        assertEquals(RoutingMode.GEO_DIRECT, RoutingOverview.DEFAULT_MODE)
        assertEquals(RoutingOverview.DEFAULT_MODE, AppSettings().routingMode)
        assertEquals(
            GeoPrecision.ENHANCED,
            parseGeoPrecision(AppSettings().geoPrecision)
        )
    }
}
