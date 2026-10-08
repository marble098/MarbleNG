package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_HOME_PING_CONTROLS_V212 — the vocabulary behind the connection page's ping buttons.
 *
 * The report was that the page has two ping buttons and one of them does nothing when pressed. A
 * control that is a no-op is a product decision that nobody made out loud, and the fix has to be
 * the opposite of another silent decision: every surface names a verb, the verb comes from a
 * stored id, and an id this build does not recognise still resolves to a real action — because a
 * stored preference outliving a release is exactly how a button goes dead in the first place.
 */
class HomePingControlsV212Test {

    @Test
    fun everyActionHasADistinctStableId() {
        val ids = HomePingAction.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() && it == it.lowercase() })
    }

    @Test
    fun anUnknownOrOlderIdStillResolvesToARealActionNeverToANoOp() {
        assertEquals(HomePingAction.ROUTE, parseHomePingAction("route"))
        assertEquals(HomePingAction.ROUTE, parseHomePingAction(" Route "))
        assertEquals(HomePingAction.GROUP, parseHomePingAction("subscription"))
        assertEquals(HomePingAction.LIBRARY, parseHomePingAction("all"))
        assertEquals(HomePingAction.TESTS, parseHomePingAction("settings"))
        // A value written by some future release is not a dead button either.
        assertEquals(HomePingAction.DEFAULT, parseHomePingAction("something-not-invented-yet"))
        assertEquals(HomePingAction.DEFAULT, parseHomePingAction(""))
    }

    @Test
    fun theThreeSurfacesShipWithTwoDifferentVerbsSoTheyAreNotTwoCopiesOfOneControl() {
        val defaults = AppSettings()
        // The gauge and the header answer about the route on screen; the deck pill is the bulk
        // verb. Shipping all three on one action would make the settings look configurable and
        // the page look identical, which is the ambiguity the chapter exists to remove.
        assertEquals(HomePingAction.ROUTE, parseHomePingAction(defaults.homePingGaugeAction))
        assertEquals(HomePingAction.GROUP, parseHomePingAction(defaults.homePingChipAction))
        assertEquals(HomePingAction.ROUTE, parseHomePingAction(defaults.homePingHeaderAction))
        assertNotEquals(defaults.homePingGaugeAction, defaults.homePingChipAction)
        // Every stored value round-trips through the parser to itself.
        HomePingAction.entries.forEach { action ->
            assertEquals(action, parseHomePingAction(action.id))
        }
    }

    @Test
    fun thePrecisionDialHasThreeRealLevelsAndShipsOnTheMiddleOne() {
        assertEquals(3, GeoPrecision.entries.size)
        assertEquals(GeoPrecision.ENHANCED, GeoPrecision.DEFAULT)
        assertEquals(GeoPrecision.ENHANCED, parseGeoPrecision(AppSettings().geoPrecision))
        // The dial is stored as an id, and an id this build has never seen resolves to the
        // shipped default rather than to "no rules at all".
        assertEquals(GeoPrecision.DEFAULT, parseGeoPrecision("invented-level"))
        assertEquals(GeoPrecision.STANDARD, parseGeoPrecision("off"))
        assertEquals(GeoPrecision.MAXIMUM, parseGeoPrecision("strict"))
    }

    @Test
    fun theGeoSetShipsMultiSourceOnWithTheBundledPrimaryFirst() {
        val defaults = AppSettings()
        assertTrue(
            "the union is the default, because that is the defect being closed",
            defaults.geoMultiSourceEnabled
        )
        assertEquals(RoutingDefaults.SOURCE_CHOCOLATE4U, defaults.geoAssetSourceId)
        assertEquals(RoutingDefaults.SOURCE_CHOCOLATE4U, defaults.geoAssetSourceIds)
        assertEquals("", defaults.geoCustomSourcesJson)
        // Readiness is a fact about this session's filesystem, never a preference.
        assertEquals("", defaults.measuredGeoReadyFiles)
    }

    @Test
    fun thePreferencesSchemaIsVersionThreeAndItsMigrationHasASourceOfTruth() {
        assertEquals(3, RoutingDefaults.PREFS_SCHEMA_VERSION)
    }
}
