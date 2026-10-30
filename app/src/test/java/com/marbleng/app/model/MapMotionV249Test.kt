package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_MAP_MOTION_V249 — the tabs are places on one map, and this pins the geometry of the pan.
 *
 * Before this pass a tab change was a single-frame swap: the new page simply *was*. The request was
 * that the app move like a map, which is a claim about direction, not about speed — the page you
 * leave has to go where you are going, and the page you arrive on has to come from there. Every rule
 * below is a number, so none of them needs a device to be proved wrong.
 */
class MapMotionV249Test {

    @Test
    fun `the direction comes from where the two tabs are, not from which tabs they are`() {
        assertEquals(1, MapMotion.direction(0, 3))
        assertEquals(-1, MapMotion.direction(3, 0))
        assertEquals(1, MapMotion.direction(1, 2))
        assertEquals(-1, MapMotion.direction(2, 1))
        // Reordering the dock changes the indexes, and the motion follows the indexes: that is the
        // whole reason the rule is not written as "SETTINGS always slides in from the right".
        assertEquals(3, MapMotion.distance(0, 3))
        assertEquals(1, MapMotion.distance(2, 1))
    }

    @Test
    fun `a tap on the tab you are already on does not travel`() {
        assertEquals(0, MapMotion.direction(2, 2))
        assertEquals(0, MapMotion.distance(2, 2))
        assertFalse(MapMotion.travels(MapMotion.direction(2, 2)))
        assertTrue(MapMotion.travels(MapMotion.direction(0, 1)))
        // An unknown pair (a hidden fourth tab, a stale index) is not a direction either.
        assertEquals(0, MapMotion.direction(-1, 2))
        assertEquals(0, MapMotion.distance(-1, 2))
        assertFalse(MapMotion.travels(0))
    }

    @Test
    fun `a right to left interface mirrors the pan`() {
        // In Persian the fourth tab is on the left, so the same two indexes travel the other way.
        assertEquals(-1, MapMotion.mirrored(1, rightToLeft = true))
        assertEquals(1, MapMotion.mirrored(-1, rightToLeft = true))
        assertEquals(0, MapMotion.mirrored(0, rightToLeft = true))
        assertEquals(1, MapMotion.mirrored(1, rightToLeft = false))
        // A direction out of range is clamped before it is mirrored, so a stray 2 cannot become -2
        // and a stray -7 cannot become -7: the pan is always one of three things.
        assertEquals(-1, MapMotion.mirrored(2, rightToLeft = true))
        assertEquals(-1, MapMotion.mirrored(-7, rightToLeft = false))
        assertEquals(1, MapMotion.mirrored(7, rightToLeft = false))
    }

    @Test
    fun `the arriving page and the departing page travel in opposite directions`() {
        val arriving = MapMotion.arrivingOffsetFraction(1)
        val departing = MapMotion.departingOffsetFraction(1)
        assertTrue(arriving > 0f)
        assertTrue(departing < 0f)
        assertTrue(MapMotion.arrivingOffsetFraction(-1) < 0f)
        assertTrue(MapMotion.departingOffsetFraction(-1) > 0f)
        // No travel at all when there is nowhere to go.
        assertEquals(0f, MapMotion.arrivingOffsetFraction(0), 0.0001f)
        assertEquals(0f, MapMotion.departingOffsetFraction(0), 0.0001f)
    }

    @Test
    fun `the arriving page drifts less than the departing one, so the pair reads as one surface`() {
        assertTrue(
            MapMotion.ARRIVING_DRIFT_FRACTION < MapMotion.DEPARTING_DRIFT_FRACTION
        )
        // Both are a fraction of the width, and neither is a full width: a page that starts entirely
        // off-screen and one that leaves entirely are two slides, not a pan.
        assertTrue(MapMotion.ARRIVING_DRIFT_FRACTION in 0.05f..0.6f)
        assertTrue(MapMotion.DEPARTING_DRIFT_FRACTION in 0.05f..0.6f)
        // Both pages scale a little, and both stay nearly full size: a map recedes, it does not vanish.
        assertTrue(MapMotion.ARRIVING_SCALE < 1f && MapMotion.ARRIVING_SCALE > 0.85f)
        assertTrue(MapMotion.DEPARTING_SCALE < MapMotion.ARRIVING_SCALE)
        assertTrue(MapMotion.DEPARTING_SCALE > 0.85f)
        assertTrue(MapMotion.FLIGHT_ALPHA_FLOOR in 0f..1f)
    }

    @Test
    fun `a longer jump takes a little longer, and never too long`() {
        assertEquals(MapMotion.BASE_DURATION_MS, MapMotion.durationMs(1))
        assertEquals(MapMotion.BASE_DURATION_MS, MapMotion.durationMs(0))
        assertEquals(
            MapMotion.BASE_DURATION_MS + MapMotion.PER_STEP_DURATION_MS,
            MapMotion.durationMs(2)
        )
        assertEquals(
            MapMotion.BASE_DURATION_MS + 2 * MapMotion.PER_STEP_DURATION_MS,
            MapMotion.durationMs(3)
        )
        // Crossing the whole dock is still one gesture, so the cap is what keeps it from feeling slow.
        assertEquals(MapMotion.MAX_DURATION_MS, MapMotion.durationMs(12))
        assertTrue(MapMotion.durationMs(12) <= MapMotion.MAX_DURATION_MS)
        assertTrue(MapMotion.durationMs(3) >= MapMotion.MIN_DURATION_MS)
        // Monotonic: a longer pan is never quicker than a shorter one.
        assertTrue(MapMotion.durationMs(3) >= MapMotion.durationMs(2))
        assertTrue(MapMotion.durationMs(2) >= MapMotion.durationMs(1))
        // A negative distance (an impossible pair) is treated as no distance, not as a negative time.
        assertEquals(MapMotion.BASE_DURATION_MS, MapMotion.durationMs(-4))
    }

    @Test
    fun `opening a detail page is a zoom, not a pan`() {
        assertTrue(MapMotion.DETAIL_ENTER_SCALE < 1f)
        assertTrue(MapMotion.DETAIL_EXIT_SCALE < 1f)
        // The detail arrives from slightly smaller than the map it came from, and returns to slightly
        // smaller than it: one continuous surface, in and out.
        assertTrue(MapMotion.DETAIL_ENTER_SCALE < MapMotion.DETAIL_EXIT_SCALE)
        assertTrue(MapMotion.DETAIL_ENTER_SCALE > 0.85f)
    }
}
