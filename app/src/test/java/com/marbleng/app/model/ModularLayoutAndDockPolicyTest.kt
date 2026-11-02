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
    fun theDockAlwaysDrawsItsGlyphsAndNeverDrawsACaption() {
        // MARBLE_QUIET_TABS_V263 — V255's two switches and V258's per-rung caption decision are
        // both gone. One answer remains, in the composable itself: every rung draws one glyph per
        // tab and no rung ever composes the tab's name beside it.
        DockSize.entries.forEach { size ->
            assertTrue("${size.id} must keep its glyphs", DockStudio.iconSizeDp(size) > 0)
            assertTrue("${size.id} must describe its own size", DockStudio.ladderHint(size).isNotBlank())
        }
        val item = java.io.File("src/main/java/com/marbleng/app/ui/NavigationItemView.kt").readText()
        assertFalse("a tab caption came back", "AnimatedContent(" in item)
        assertTrue("the label belongs to a screen reader", "contentDescription = tab.label" in item)
    }
}
