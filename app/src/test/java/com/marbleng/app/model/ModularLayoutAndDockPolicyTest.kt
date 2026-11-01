package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModularLayoutAndDockPolicyTest {
    @Test
    fun aTruncatedOrderStillRendersEveryModule() {
        val repaired = ModularLayout.order("STATUS,SERVERS")
        assertEquals(ModularLayout.CANONICAL.size, repaired.size)
        assertTrue(repaired.containsAll(ModularLayout.CANONICAL))

        assertEquals(ModularLayout.STATUS, repaired[0])
        assertEquals(ModularLayout.SERVERS, repaired[1])
    }

    @Test
    fun anOrderWithoutConnectStillContainsConnect() {
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
    fun theCaptionIsTheRungsDecisionAndTheDockAlwaysDrawsItsGlyphs() {
        // MARBLE_FLOATING_DOCK_V258 — V255's two switches are gone from the model and from
        // Settings. What replaces them is one derived answer: the capsule always draws glyphs,
        // and only the largest rung keeps a word on screen.
        assertTrue(DockStudio.showsCaptionsAlways(DockSize.LARGE))
        assertFalse(DockStudio.showsCaptionsAlways(DockSize.MEDIUM))
        assertFalse(DockStudio.showsCaptionsAlways(DockSize.SMALL))
        DockSize.entries.forEach { size ->
            assertTrue("${size.id} must keep its glyphs", DockStudio.iconSizeDp(size) > 0)
        }
    }
}
