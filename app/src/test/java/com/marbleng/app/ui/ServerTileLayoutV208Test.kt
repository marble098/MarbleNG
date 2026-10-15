package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerTileLayoutV208Test {
    @Test
    fun aPhoneGetsTwoColumnsBecauseOneTileNeeds148Dp() {
        assertEquals(2, ServerTilePolicy.columnsFor(360))

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

        assertEquals(listOf(listOf("a"), listOf("b")), ServerTilePolicy.chunkRows(servers, 0))
        assertEquals(listOf(listOf("a"), listOf("b")), ServerTilePolicy.chunkRows(servers, -3))
    }

    @Test
    fun anEmptyListChunksToNothingInsteadOfOneEmptyRow() {
        assertTrue(ServerTilePolicy.chunkRows(emptyList<String>(), 3).isEmpty())
    }
}
