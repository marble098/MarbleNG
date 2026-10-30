package com.marbleng.app.model

import com.marbleng.app.ui.theme.MarbleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * MARBLE_LIQUID_DOCK_V245 — the glass pill's arithmetic, pinned without a device.
 *
 * The dock is a physical object, so its promises are numbers: the pill fits the chrome budget at
 * every size, the lens centre lines up with a slot centre, the nearest tab is the tab the eye is
 * looking at, one haptic per crossed edge, +30 % magnification at the centre that dies to nothing
 * one slot away, +13 % growth under the finger, and a badge that stops counting at nine.
 */
class LiquidDockV245Test {

    @Test
    fun everyDockSizeFitsTheChromeBudgetTheScreensReserve() {
        assertTrue(LiquidDockSpec.allFit())
        assertEquals(MarbleSpec.BOTTOM_BAR_CLEARANCE_DP, LiquidDockSpec.CHROME_BUDGET_DP)
        DockSize.entries.forEach { size ->
            val total = LiquidDockSpec.totalHeightDp(size)
            assertEquals(LiquidDockSpec.barHeightDp(size) + LiquidDockSpec.BOTTOM_MARGIN_DP, total)
            assertTrue("$size needs $total dp", total <= LiquidDockSpec.CHROME_BUDGET_DP)
        }
    }

    @Test
    fun thePillIsAStadiumAndTheLensFitsInsideIt() {
        DockSize.entries.forEach { size ->
            val height = LiquidDockSpec.barHeightDp(size)
            assertEquals(height / 2f, LiquidDockSpec.cornerRadiusDp(size), 0.001f)

            val lensHeight = LiquidDockSpec.lensHeightDp(size)
            val lensWidth = LiquidDockSpec.lensWidthDp(size)
            assertTrue("$size lens is taller than the pill", lensHeight < height)
            assertTrue("$size lens has no air above it", lensHeight <= height - LiquidDockSpec.LENS_INSET_DP)
            assertTrue("$size lens is not a drop", lensWidth > lensHeight)
            assertTrue("$size lens is wider than a slot", lensWidth <= height)
        }
    }

    @Test
    fun everySlotCentreIsEvenlySpacedInsideTheBar() {
        (3..5).forEach { count ->
            val centres = (0 until count).map { LiquidDockSpec.slotCenterFraction(it, count) }
            centres.forEach { centre ->
                assertTrue("centre $centre outside the bar", centre > 0f && centre < 1f)
            }
            centres.zipWithNext().forEach { (left, right) ->
                assertEquals(1f / count, right - left, 1e-6f)
            }
            assertEquals(0.5f, centres[count / 2], 0.26f)
        }
    }

    @Test
    fun theNearestTabIsTheTabUnderTheFingerAndTheEdgeBelongsToTheTabItEnters() {
        assertEquals(0, LiquidDockSpec.nearestIndex(0f, 4))
        assertEquals(0, LiquidDockSpec.nearestIndex(0.249f, 4))
        assertEquals(1, LiquidDockSpec.nearestIndex(0.25f, 4))
        assertEquals(1, LiquidDockSpec.nearestIndex(0.499f, 4))
        assertEquals(2, LiquidDockSpec.nearestIndex(0.5f, 4))
        assertEquals(3, LiquidDockSpec.nearestIndex(1f, 4))
        // A fraction outside the bar is a finger outside the bar: clamp, never throw.
        assertEquals(0, LiquidDockSpec.nearestIndex(-2f, 4))
        assertEquals(3, LiquidDockSpec.nearestIndex(9f, 4))
        assertEquals(0, LiquidDockSpec.nearestIndex(0.5f, 0))
    }

    @Test
    fun crossingAnEdgeIsExactlyOneHapticTick() {
        assertFalse(LiquidDockSpec.boundaryCrossed(0.10f, 0.20f, 4))
        assertTrue(LiquidDockSpec.boundaryCrossed(0.20f, 0.30f, 4))
        // A fast flick across three tabs is three ticks, not one — the finger crossed three edges.
        var ticks = 0
        var cursor = 0.05f
        listOf(0.30f, 0.55f, 0.80f).forEach { step ->
            if (LiquidDockSpec.boundaryCrossed(cursor, step, 4)) ticks++
            cursor = step
        }
        assertEquals(3, ticks)
    }

    @Test
    fun theLensMagnifiesItsOwnTabAndLetsGoOfItsNeighbours() {
        val centre = LiquidDockSpec.magnification(0f)
        val neighbour = LiquidDockSpec.magnification(1f)
        val stranger = LiquidDockSpec.magnification(2f)

        assertEquals(1.30f, centre, 0.001f)
        assertTrue("$neighbour is not magnified at all", neighbour > 1.05f)
        assertTrue("$stranger is still magnified", stranger < 1.02f)
        assertTrue(centre > neighbour && neighbour > stranger && stranger >= 1f)

        assertTrue(
            "labels must magnify less than icons",
            LiquidDockSpec.labelMagnification(0f) < LiquidDockSpec.magnification(0f)
        )
        // The lens is bright under itself and gone one slot away — that is what tints the glyph.
        assertEquals(1f, LiquidDockSpec.highlight(0f), 1e-6f)
        assertEquals(0f, LiquidDockSpec.highlight(2f), 0.01f)
    }

    @Test
    fun theLensGrowsInsideTheTenToFifteenPercentBrief() {
        assertEquals(1f, LiquidDockSpec.pressedScale(0f), 1e-6f)
        assertEquals(1.13f, LiquidDockSpec.pressedScale(1f), 1e-6f)
        val growth = LiquidDockSpec.pressedScale(1f) - 1f
        assertTrue("growth $growth is outside 10–15 %", growth in 0.10f..0.15f)
        assertTrue(LiquidDockSpec.pressedScale(0.5f) < LiquidDockSpec.pressedScale(1f))
        // Clamped: a spring overshoot may hand this function 1.2 and must not inflate the lens.
        assertEquals(LiquidDockSpec.pressedScale(1f), LiquidDockSpec.pressedScale(1.2f), 1e-6f)
    }

    @Test
    fun aBadgeStopsCountingAtNine() {
        assertEquals("", LiquidDockSpec.badgeLabel(0))
        assertEquals("", LiquidDockSpec.badgeLabel(-3))
        assertEquals("2", LiquidDockSpec.badgeLabel(2))
        assertEquals("9", LiquidDockSpec.badgeLabel(9))
        assertEquals("9+", LiquidDockSpec.badgeLabel(10))
        assertEquals(16, LiquidDockSpec.BADGE_MIN_SIZE_DP)
    }

    @Test
    fun theCounterOnlyCountsWhatIsWaitingForATap() {
        assertEquals(0, LiquidDockNotices.count(updateAvailable = false, unappliedFindings = 0))
        assertEquals(1, LiquidDockNotices.count(updateAvailable = true, unappliedFindings = 0))
        assertEquals(2, LiquidDockNotices.count(updateAvailable = false, unappliedFindings = 2))
        assertEquals(3, LiquidDockNotices.count(updateAvailable = true, unappliedFindings = 2))
        // A negative count is a bug somewhere else; it must not eat the update.
        assertEquals(1, LiquidDockNotices.count(updateAvailable = true, unappliedFindings = -4))
    }

    @Test
    fun aDragAcrossTheWholeRowVisitsEveryTabExactlyOnce() {
        val count = 4
        val visited = mutableListOf<Int>()
        var previous = 0f
        var step = 0
        while (step <= 100) {
            val fraction = step / 100f
            val index = LiquidDockSpec.nearestIndex(fraction, count)
            if (visited.lastOrNull() != index) visited.add(index)
            if (LiquidDockSpec.boundaryCrossed(previous, fraction, count)) {
                assertTrue("crossed an edge without a new tab", visited.size > 1)
            }
            previous = fraction
            step++
        }
        assertEquals(listOf(0, 1, 2, 3), visited)
        assertTrue(
            "the finger's own centre must be the selected centre",
            abs(LiquidDockSpec.slotCenterFraction(2, count) - 0.625f) < 1e-6f
        )
    }
}
