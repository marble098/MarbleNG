package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

        assertEquals(HomePingAction.DEFAULT, parseHomePingAction("something-not-invented-yet"))
        assertEquals(HomePingAction.DEFAULT, parseHomePingAction(""))
    }

    @Test
    fun theThreeSurfacesShipWithTwoDifferentVerbsSoTheyAreNotTwoCopiesOfOneControl() {
        val defaults = AppSettings()

        assertEquals(HomePingAction.ROUTE, parseHomePingAction(defaults.homePingGaugeAction))
        assertEquals(HomePingAction.GROUP, parseHomePingAction(defaults.homePingChipAction))
        assertEquals(HomePingAction.ROUTE, parseHomePingAction(defaults.homePingHeaderAction))
        assertNotEquals(defaults.homePingGaugeAction, defaults.homePingChipAction)

        HomePingAction.entries.forEach { action ->
            assertEquals(action, parseHomePingAction(action.id))
        }
    }

    @Test
    fun thePrecisionDialHasThreeRealLevelsAndShipsOnTheMiddleOne() {
        assertEquals(3, GeoPrecision.entries.size)
        assertEquals(GeoPrecision.ENHANCED, GeoPrecision.DEFAULT)
        assertEquals(GeoPrecision.ENHANCED, parseGeoPrecision(AppSettings().geoPrecision))

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

        assertEquals("", defaults.measuredGeoReadyFiles)
    }

    @Test
    fun thePreferencesSchemaIsVersionThreeAndItsMigrationHasASourceOfTruth() {
        assertEquals(3, RoutingDefaults.PREFS_SCHEMA_VERSION)
    }
}
