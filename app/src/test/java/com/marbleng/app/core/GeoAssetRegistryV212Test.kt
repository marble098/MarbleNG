package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoAssetRegistryV212Test {
    @Test
    fun anEmptyStoredListMeansTheBundledPrimaryAndNeverNothing() {
        assertEquals(
            listOf(RoutingDefaults.SOURCE_CHOCOLATE4U),
            GeoAssetRegistry.parseIds("")
        )
        assertEquals(listOf(RoutingDefaults.SOURCE_CHOCOLATE4U), GeoAssetRegistry.parseIds("  "))

        assertEquals(listOf(RoutingDefaults.SOURCE_CHOCOLATE4U), GeoAssetRegistry.parseIds("all"))
    }

    @Test
    fun theStoredListIsOrderedDeduplicatedAndCapped() {
        val parsed = GeoAssetRegistry.parseIds(
            " Loyalsoldier , chocolate4u-iran,LOYALSOLDIER,v2fly,v2fly-geoip,a,b,c "
        )
        assertEquals(
            listOf("loyalsoldier", "chocolate4u-iran", "v2fly", "v2fly-geoip", "a", "b"),
            parsed
        )
        assertEquals(GeoAssetRegistry.MAX_SOURCES, parsed.size)
        assertFalse("a set at its cap has no room", GeoAssetRegistry.hasRoom(parsed.joinToString(",")))
        assertTrue(GeoAssetRegistry.hasRoom("loyalsoldier"))
    }

    @Test
    fun pickingAPrimaryReordersInsteadOfDroppingTheRest() {
        val set = listOf("chocolate4u-iran", "loyalsoldier", "v2fly")
        val reordered = GeoAssetRegistry.withPrimary(set, "v2fly")
        assertEquals(listOf("v2fly", "chocolate4u-iran", "loyalsoldier"), reordered)
        assertEquals("nothing was lost", set.size, reordered.size)
    }

    @Test
    fun theSetAlwaysKeepsAtLeastOneSource() {
        val only = listOf(RoutingDefaults.SOURCE_CHOCOLATE4U)
        assertEquals(only, GeoAssetRegistry.without(only, RoutingDefaults.SOURCE_CHOCOLATE4U))
        val two = GeoAssetRegistry.withAdded(only, "loyalsoldier")
        assertEquals(listOf(RoutingDefaults.SOURCE_CHOCOLATE4U, "loyalsoldier"), two)
        assertEquals(only, GeoAssetRegistry.without(two, "loyalsoldier"))

        assertEquals(two, GeoAssetRegistry.withAdded(two, "LOYALSOLDIER"))
    }

    @Test
    fun thePrimaryKeepsTheCanonicalFileNamesAndTheOthersNameTheirs() {
        val primary = GeoAssetRegistry.catalog(AppSettings()).first()
        assertEquals(
            GeoAssetRegistry.PRIMARY_GEOIP_FILE,
            GeoAssetRegistry.fileName(primary, GeoAssetRegistry.Kind.GEOIP, primary = true)
        )
        assertEquals(
            GeoAssetRegistry.PRIMARY_GEOSITE_FILE,
            GeoAssetRegistry.fileName(primary, GeoAssetRegistry.Kind.GEOSITE, primary = true)
        )
        assertEquals(
            "geoip-loyalsoldier.dat",
            GeoAssetRegistry.fileName(loyalsoldier(), GeoAssetRegistry.Kind.GEOIP, primary = false)
        )
        assertEquals(
            "geosite-loyalsoldier.dat",
            GeoAssetRegistry.fileName(loyalsoldier(), GeoAssetRegistry.Kind.GEOSITE, primary = false)
        )
    }

    @Test
    fun thePrimaryTokenIsUnchangedSoOlderCoresStillReadIt() {
        val spec = loyalsoldier()
        assertEquals(
            "geoip:ir",
            GeoAssetRegistry.token(GeoAssetRegistry.Kind.GEOIP, "ir", spec, primary = true)
        )
        assertEquals(
            "ext:geoip-loyalsoldier.dat:ir",
            GeoAssetRegistry.token(GeoAssetRegistry.Kind.GEOIP, "ir", spec, primary = false)
        )
        assertEquals(
            "ext:geosite-loyalsoldier.dat:category-ads-all",
            GeoAssetRegistry.token(
                GeoAssetRegistry.Kind.GEOSITE,
                "geosite:category-ads-all",
                spec,
                primary = false
            )
        )
    }

    @Test
    fun geoipPrivateNamesNoFileBecauseTheCoreAnswersItAlone() {
        val spec = loyalsoldier()
        assertEquals(
            "geoip:private",
            GeoAssetRegistry.token(GeoAssetRegistry.Kind.GEOIP, "private", spec, primary = false)
        )
        assertFalse(GeoAssetRegistry.isFileReference("geoip:private"))
        assertEquals("", GeoAssetRegistry.fileOf("geoip:private"))
    }

    @Test
    fun aSourceThatPublishesOneFamilyCannotBeAskedForTheOther() {
        val geoIpOnly = GeoAssetRegistry.SourceSpec(
            id = "v2fly-geoip",
            label = "v2fly GeoIP",
            geoIpUrl = "https://example.invalid/geoip.dat"
        )
        assertNull(
            GeoAssetRegistry.token(GeoAssetRegistry.Kind.GEOSITE, "ir", geoIpOnly, primary = false)
        )
        assertTrue(geoIpOnly.provides(GeoAssetRegistry.Kind.GEOIP))
        assertFalse(geoIpOnly.provides(GeoAssetRegistry.Kind.GEOSITE))
    }

    @Test
    fun theCustomUrlFormIsAFormNotADatabase() {
        val ids = GeoAssetRegistry.catalog(AppSettings()).map { it.id }
        assertFalse(
            "the 'custom' entry only opens the URL editor",
            GeoAssetRegistry.CUSTOM_FORM_ID in ids
        )
        assertTrue(GeoAssetRegistry.CUSTOM_FORM_ID in RoutingDefaults.SOURCES.map { it.id })
    }

    @Test
    fun aUserSourceSurvivesARoundTripAndABrokenOneIsSkippedNotThrown() {
        val sources = GeoAssetRegistry.customSources(
            """[{"id":"My Bank","label":"My Bank","geoIpUrl":"https://a.example/x.dat"}]"""
        )
        assertEquals(1, sources.size)
        assertEquals("custom-my-bank", sources.first().id)
        assertEquals("My Bank", sources.first().label)
        assertTrue(sources.first().custom)

        val restored = GeoAssetRegistry.customSources(GeoAssetRegistry.serializeCustom(sources))
        assertEquals(sources, restored)

        assertTrue(
            GeoAssetRegistry.customSources(
                """[{"id":"x","geoIpUrl":"http://a.example/x.dat"}]"""
            ).isEmpty()
        )
        assertTrue(GeoAssetRegistry.customSources("""[{"id":"x"}]""").isEmpty())
        assertTrue(GeoAssetRegistry.customSources("not json at all").isEmpty())
    }

    @Test
    fun anIdIsReducedToAFilenameSafeAlphabetBecauseItBecomesAFileName() {
        assertEquals("my-bank", GeoAssetRegistry.safeId(" My Bank "))
        assertEquals("a-b", GeoAssetRegistry.safeId("../../a//b"))
        assertEquals("", GeoAssetRegistry.safeId("///"))
        assertTrue(GeoAssetRegistry.safeId("x".repeat(80)).length <= 32)
    }

    @Test
    fun theSelectionReadsTheNewSetAndFallsBackToTheLegacySingleId() {
        val legacy = AppSettings(geoAssetSourceIds = "", geoAssetSourceId = "loyalsoldier")
        assertEquals(listOf("loyalsoldier"), GeoAssetRegistry.parseSelection(legacy).ids)
        assertEquals("loyalsoldier", GeoAssetRegistry.parseSelection(legacy).primaryId)
        assertTrue(GeoAssetRegistry.parseSelection(legacy).multiSource)
        assertEquals(GeoPrecision.DEFAULT, GeoAssetRegistry.parseSelection(legacy).precision)
    }

    @Test
    fun multiSourceOffMeansExactlyOneDatabaseEvenWhenTheSetIsFull() {
        val settings = AppSettings(
            geoAssetSourceIds = "chocolate4u-iran,loyalsoldier,v2fly",
            geoMultiSourceEnabled = false
        )
        val resolved = GeoAssetRegistry.resolve(settings)
        assertEquals(1, resolved.size)
        assertEquals("chocolate4u-iran", resolved.first().id)
        assertEquals(listOf("geoip:ir"), GeoAssetRegistry.tokensFor(settings, GeoAssetRegistry.Kind.GEOIP, "ir"))
    }

    @Test
    fun theSummaryNamesSourcesBeforeItNamesBytes() {
        val state = GeoAssetRegistry.SourceState(
            spec = loyalsoldier(),
            primary = true,
            geoIpReady = true,
            geoSiteReady = true,
            geoIpBytes = 5_000_000L,
            geoSiteBytes = 1_000_000L,
            updatedAtMs = 0L
        )
        assertEquals("No geo database ready yet", GeoAssetRegistry.summary(emptyList()))
        assertEquals(
            "1 source • geoip ×1 • geosite ×1 • 5.7 MB",
            GeoAssetRegistry.summary(listOf(state))
        )

        val pending = state.copy(geoIpReady = false, geoSiteReady = false)
        assertFalse(pending.usable())
        assertFalse(pending.readyFor(GeoAssetRegistry.Kind.GEOIP))
        assertEquals("No geo database ready yet", GeoAssetRegistry.summary(listOf(pending)))
    }

    @Test
    fun bytesReadTheWayTheAssetCardSays() {
        assertEquals("0 KB", GeoAssetRegistry.formatBytes(0L))
        assertEquals("512 B", GeoAssetRegistry.formatBytes(512L))
        assertEquals("4 KB", GeoAssetRegistry.formatBytes(4_096L))
        assertEquals("5.7 MB", GeoAssetRegistry.formatBytes(5_976_883L))
    }

    private fun loyalsoldier(): GeoAssetRegistry.SourceSpec =
        GeoAssetRegistry.catalog(AppSettings()).first { it.id == "loyalsoldier" }
}
