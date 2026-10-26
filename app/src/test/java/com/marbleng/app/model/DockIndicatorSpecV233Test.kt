package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the little blue line used to be an unmeasured
 * overlay. This test proves the replacement is measured, centred and fits every dock size, every
 * icon/label combination and every rung of the text-scale ladder.
 */
class DockIndicatorSpecV233Test {

    @Test
    fun everyDockSizeAndTextScaleHasRoomForTheIndicator() {
        assertTrue(DockIndicatorSpec.allFit())
        DockSize.entries.forEach { size ->
            TextScale.STEPS.forEach { scale ->
                listOf(true, false).forEach { icons ->
                    listOf(true, false).forEach { labels ->
                        assertTrue(
                            "${size.name} icons=$icons labels=$labels scale=$scale overflows",
                            DockIndicatorSpec.fits(size, icons, labels, scale)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theSlotReservesAirAboveAndBelowTheLine() {
        assertTrue(DockIndicatorSpec.SLOT_DP >= DockIndicatorSpec.HEIGHT_DP + 2)
        assertEquals(18, DockIndicatorSpec.WIDTH_DP)
        assertEquals(3, DockIndicatorSpec.HEIGHT_DP)
        assertEquals(6, DockIndicatorSpec.SLOT_DP)
        assertEquals(4, DockIndicatorSpec.TAB_VERTICAL_PADDING_DP)
    }

    @Test
    fun theCompactDockGrewOnlyEnoughForTheLargestLabel() {
        assertEquals(56, DockIndicatorSpec.barHeightDp(DockSize.SMALL))
        assertEquals(60, DockIndicatorSpec.barHeightDp(DockSize.MEDIUM))
        assertEquals(68, DockIndicatorSpec.barHeightDp(DockSize.LARGE))
        assertEquals(21, DockIndicatorSpec.labelLineHeightDp(TextScale.MAX_PERCENT))
        val worst = DockIndicatorSpec.contentHeightDp(
            DockSize.SMALL,
            showIcons = true,
            showLabels = true,
            textScalePercent = TextScale.MAX_PERCENT
        )
        assertTrue(worst <= DockIndicatorSpec.barHeightDp(DockSize.SMALL))
    }

    @Test
    fun iconOnlyAndLabelOnlyLayoutsAreAlsoMeasured() {
        assertTrue(DockIndicatorSpec.contentHeightDp(DockSize.SMALL, true, false) > 0)
        assertTrue(DockIndicatorSpec.contentHeightDp(DockSize.SMALL, false, true) > 0)
        assertTrue(
            DockIndicatorSpec.contentHeightDp(DockSize.SMALL, true, true) >
                DockIndicatorSpec.contentHeightDp(DockSize.SMALL, true, false)
        )
    }
}
