package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.GeoPrecision
import com.marbleng.app.model.RoutingDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_MULTI_SOURCE_ROUTING_V212 — the set of geo databases routing reads.
 *
 * The report was that routing is "still weak" at separating domestic traffic, and the structural
 * half of that is here: the preference used to be ONE source id, and choosing a second database
 * meant giving up the first. What can go wrong invisibly in the replacement is not the download —
 * it is the *set*: its order (the first entry owns the canonical file names), its floor (routing
 * with no database is a worse product than routing with the bundled one), its ceiling (a set the
 * writer cannot finish downloading), and the token shape each member is addressed by (a wrong
 * `ext:` reference is a config the core refuses to load).
 */
class GeoAssetRegistryV212Test {

    @Test
    fun anEmptyStoredListMeansTheBundledPrimaryAndNeverNothing() {
        assertEquals(
            listOf(RoutingDefaults.SOURCE_CHOCOLATE4U),
            GeoAssetRegistry.parseIds("")
        )
        assertEquals(listOf(RoutingDefaults.SOURCE_CHOCOLATE4U), GeoAssetRegistry.parseIds("  "))
        // The legacy spelling of "everything" is not a source: it is the word the old list used.
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
        // The defect this exists to remove: a source chooser that replaced the previous source.
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
        // Adding one that is already there is not a duplicate.
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
        // v2fly-geoip ships geoip.dat only: asking it for a geosite tag has no answer, and a
        // half-answer written as a rule is a reference to a file that does not exist.
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

        // http (not https), a blank url pair, and junk JSON are all "no source here".
        assertEquals(
            0,
            GeoAssetRegistry.customSources(
                """[{"id":"x","geoIpUrl":"http://a.example/x.dat"}]"""
            ).size
        )
        assertEquals(0, GeoAssetRegistry.customSources("""[{"id":"x"}]""").size)
        assertEquals(0, GeoAssetRegistry.customSources("not json at all"))
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
        // v2 → v3 migration seeds the set from the single id; a stored set that predates the
        // seeding must still resolve, or an upgrade would silently lose the user's source.
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
        // A source that is enabled but not on disk is not an error and is not counted as ready.
        val pending = state.copy(geoIpReady = false, geoSiteReady = false)
        assertFalse(pending.usable())
        assertFalse(pending.readyFor(GeoAssetRegistry.Kind.GEOIP))
        assertEquals("No geo database ready yet", GeoAssetRegistry.summary(listOf(pending)))
    }

    @Test
    fun bytesReadTheWayTheAssetCardSays() {
        assertEquals("0 KB", GeoAssetRegistry.formatBytes(0))
        assertEquals("512 B", GeoAssetRegistry.formatBytes(512))
        assertEquals("4 KB", GeoAssetRegistry.formatBytes(4_096))
        assertEquals("5.7 MB", GeoAssetRegistry.formatBytes(5_976_883))
    }

    private fun loyalsoldier(): GeoAssetRegistry.SourceSpec =
        GeoAssetRegistry.catalog(AppSettings()).first { it.id == "loyalsoldier" }
}
