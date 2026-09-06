package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_MODULAR_LAYOUT_V145 / MARBLE_DOCK_CUSTOM_V145.
 *
 * Two chrome preferences that used to be able to render an unusable product: a modular Home
 * order that had lost the CONNECT module, and a navigation bar with neither icons nor labels.
 * Both are repaired by pure functions, so both are pinned here.
 */
class ModularLayoutAndDockPolicyTest {

    @Test
    fun aTruncatedOrderStillRendersEveryModule() {
        val repaired = ModularLayout.order("STATUS,SERVERS")
        assertEquals(ModularLayout.CANONICAL.size, repaired.size)
        assertTrue(repaired.containsAll(ModularLayout.CANONICAL))
        // The user's own ordering of what they did save is preserved, first.
        assertEquals(ModularLayout.STATUS, repaired[0])
        assertEquals(ModularLayout.SERVERS, repaired[1])
    }

    @Test
    fun anOrderWithoutConnectStillContainsConnect() {
        // The regression that made Home unusable: no CONNECT token, therefore no connect button.
        val repaired = ModularLayout.order("STATS,SERVERS,STATUS")
        assertTrue(ModularLayout.CONNECT in repaired)
    }

    @Test
    fun duplicatesAndUnknownTokensAreDropped() {
        val repaired = ModularLayout.order("CONNECT,CONNECT,WIDGET,,connect ,STATUS")
        assertEquals(repaired.distinct(), repaired)
        assertEquals(ModularLayout.CONNECT, repaired.first())
        assertFalse("WIDGET" in repaired)
        assertEquals(ModularLayout.CANONICAL.size, repaired.size)
    }

    @Test
    fun orderIsIdempotentAndSerializable() {
        val once = ModularLayout.order("SERVERS,CONNECT")
        val twice = ModularLayout.order(ModularLayout.serialize(once))
        assertEquals(once, twice)
        assertEquals(ModularLayout.CANONICAL, ModularLayout.order(ModularLayout.DEFAULT_ORDER))
    }

    @Test
    fun blankOrderFallsBackToTheCanonicalLayout() {
        assertEquals(ModularLayout.CANONICAL, ModularLayout.order(""))
    }

    @Test
    fun everyDockSizeRoundTrips() {
        DockSize.entries.forEach { size ->
            assertEquals(size, parseDockSize(size.id))
            assertEquals(size, parseDockSize(size.id.uppercase()))
        }
        assertEquals(DockSize.MEDIUM, parseDockSize("nonsense"))
        assertEquals(DockSize.SMALL, parseDockSize("compact"))
        assertEquals(DockSize.LARGE, parseDockSize("spacious"))
    }

    @Test
    fun aDockWithNeitherIconsNorLabelsFallsBackToIcons() {
        assertTrue(dockShowsIcons(showIcons = false, showLabels = false))
        assertFalse(dockShowsLabels(showIcons = false, showLabels = false))
    }

    @Test
    fun eachSingleChoiceIsHonouredExactly() {
        assertTrue(dockShowsIcons(showIcons = true, showLabels = false))
        assertFalse(dockShowsLabels(showIcons = true, showLabels = false))
        assertFalse(dockShowsIcons(showIcons = false, showLabels = true))
        assertTrue(dockShowsLabels(showIcons = false, showLabels = true))
        assertTrue(dockShowsIcons(showIcons = true, showLabels = true))
        assertTrue(dockShowsLabels(showIcons = true, showLabels = true))
    }
}
