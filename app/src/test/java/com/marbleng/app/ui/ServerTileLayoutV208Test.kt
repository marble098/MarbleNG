package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVER_TILE_LAYOUT_V208 — the numbers behind the compact server boxes.
 *
 * A grid that decides its own column count from the width it is handed is a grid that has to be
 * right at both ends: too greedy and a tile is narrower than the text inside it, too timid and
 * the tablet gets the same two-up page the phone gets and the new layout bought nothing.
 *
 * So the two ends are pinned here. The floor is the readability rule (a tile is never narrower
 * than the width at which its name ellipsises to nothing), the ceiling is a hard four, and the
 * arithmetic between them is the one that turns 360 dp into two columns and 720 dp into four.
 */
class ServerTileLayoutV208Test {

    @Test
    fun aPhoneGetsTwoColumnsBecauseOneTileNeeds148Dp() {
        // 360 dp is the most common Android window there is.
        assertEquals(2, ServerTilePolicy.columnsFor(360))
        // Two tiles plus one gap is 304 dp; a third would need 460 and does not fit.
        assertEquals(
            2 * ServerTilePolicy.MinTileWidthDp + ServerTilePolicy.TileGapDp,
            2 * 148 + 8
        )
        assertTrue(2 * 148 + 8 <= 360)
        assertTrue(3 * 148 + 2 * 8 > 360)
    }

    @Test
    fun aNarrowWindowNeverCollapsesBelowOneColumn() {
        assertEquals(1, ServerTilePolicy.columnsFor(0))
        assertEquals(1, ServerTilePolicy.columnsFor(-40))
        assertEquals(1, ServerTilePolicy.columnsFor(100))
        assertEquals(1, ServerTilePolicy.columnsFor(ServerTilePolicy.MinTileWidthDp))
    }

    @Test
    fun wideWindowsGrowToThreeThenFourAndStopThere() {
        assertEquals(2, ServerTilePolicy.columnsFor(459))
        assertEquals(3, ServerTilePolicy.columnsFor(460))
        assertEquals(3, ServerTilePolicy.columnsFor(615))
        assertEquals(4, ServerTilePolicy.columnsFor(616))
        // A desktop-sized window still gets four: a tile the width of a thumb is unreadable.
        assertEquals(4, ServerTilePolicy.columnsFor(2000))
        assertEquals(ServerTilePolicy.MaxColumns, ServerTilePolicy.columnsFor(10_000))
    }

    @Test
    fun theColumnRuleIsMonotonicSoDraggingAWindowNeverLosesAColumn() {
        var previous = ServerTilePolicy.columnsFor(0)
        for (width in 0..1400 step 5) {
            val now = ServerTilePolicy.columnsFor(width)
            assertTrue("columns shrank at $width dp", now >= previous)
            previous = now
        }
    }

    @Test
    fun theWindowPaddingIsSubtractedBeforeTheColumnsAreCounted() {
        // The page body carries 16 dp of side padding on both edges, so a 360 dp window offers
        // 328 dp of content — still two columns, but the third one is not measured into
        // existence and then clipped by the padding.
        assertEquals(2, ServerTilePolicy.columnsFor(360 - 32))
    }

    @Test
    fun chunkRowsSplitsTheListWithoutDroppingOrDuplicatingAServer() {
        val servers = (1..7).toList()
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7)), ServerTilePolicy.chunkRows(servers, 3))
        assertEquals(7, ServerTilePolicy.chunkRows(servers, 3).flatten().size)
        assertEquals(1, ServerTilePolicy.chunkRows(servers, 3).last().size)
    }

    @Test
    fun chunkRowsToleratesANonsenseColumnCount() {
        val servers = listOf("a", "b")
        // A zero or negative column count would be an infinite loop in a naive implementation.
        assertEquals(listOf(listOf("a"), listOf("b")), ServerTilePolicy.chunkRows(servers, 0))
        assertEquals(listOf(listOf("a"), listOf("b")), ServerTilePolicy.chunkRows(servers, -3))
    }

    @Test
    fun anEmptyListChunksToNothingInsteadOfOneEmptyRow() {
        assertTrue(ServerTilePolicy.chunkRows(emptyList<String>(), 3).isEmpty())
    }
}
