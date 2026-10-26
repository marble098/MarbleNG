package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_RTL_CONNECT_CONTROL_V234 — the slidable connect control, mirrored.
 *
 * The defect this pins is not cosmetic. On a Persian layout the thumb rests against the right edge,
 * so a positive `IntOffset` pushed it out of the track, a positive drag delta pulled the bar the
 * wrong way, and the guide chevrons pointed away from the gesture. All three signs now live in
 * `SlideConnectSpec` instead of inside a composable, which is what makes them assertable here.
 */
class SlideConnectSpecV234Test {

    @Test
    fun theDragFollowsTheReadingDirection() {
        val start = 0f
        // LTR: the thumb travels right, so a rightwards finger moves the fraction forward.
        assertEquals(0.5f, SlideConnectSpec.dragFraction(start, 100f, 200f, rtl = false), 0.0001f)
        // RTL: the same finger movement is backwards.
        assertEquals(0f, SlideConnectSpec.dragFraction(start, 100f, 200f, rtl = true), 0.0001f)
        // RTL: the thumb is dragged left, which is a negative delta.
        assertEquals(0.5f, SlideConnectSpec.dragFraction(start, -100f, 200f, rtl = true), 0.0001f)
    }

    @Test
    fun theFractionStaysInsideTheTrack() {
        assertEquals(1f, SlideConnectSpec.dragFraction(0.9f, 500f, 200f, rtl = false), 0.0001f)
        assertEquals(0f, SlideConnectSpec.dragFraction(0.1f, -500f, 200f, rtl = false), 0.0001f)
        assertEquals(1f, SlideConnectSpec.dragFraction(0.9f, -500f, 200f, rtl = true), 0.0001f)
        // A zero-width track keeps what it had instead of dividing by zero.
        assertEquals(0.4f, SlideConnectSpec.dragFraction(0.4f, 40f, 0f, rtl = false), 0.0001f)
    }

    @Test
    fun theThumbTravelsTowardsTheFarEdgeOnly() {
        // LTR: positive x from the start edge.
        assertEquals(150, SlideConnectSpec.thumbOffsetPx(travelPx = 300f, fraction = 0.5f, rtl = false))
        assertEquals(300, SlideConnectSpec.thumbOffsetPx(travelPx = 300f, fraction = 1f, rtl = false))
        // RTL: the resting edge is the right one, so travel is negative x and never positive.
        assertEquals(-150, SlideConnectSpec.thumbOffsetPx(travelPx = 300f, fraction = 0.5f, rtl = true))
        assertEquals(-300, SlideConnectSpec.thumbOffsetPx(travelPx = 300f, fraction = 1f, rtl = true))
        assertEquals(0, SlideConnectSpec.thumbOffsetPx(travelPx = 300f, fraction = 0f, rtl = true))
        assertEquals(0, SlideConnectSpec.thumbOffsetPx(travelPx = 0f, fraction = 1f, rtl = true))
    }

    @Test
    fun theSameGestureCommitsInBothDirections() {
        val travelPx = 200f
        // A full leftward drag on a Persian phone is a decision.
        var rtlFraction = 0f
        repeat(10) {
            rtlFraction = SlideConnectSpec.dragFraction(rtlFraction, -20f, travelPx, rtl = true)
        }
        assertTrue(SlideConnectSpec.released(rtlFraction))

        // The mirror image, in English.
        var ltrFraction = 0f
        repeat(10) {
            ltrFraction = SlideConnectSpec.dragFraction(ltrFraction, 20f, travelPx, rtl = false)
        }
        assertTrue(SlideConnectSpec.released(ltrFraction))
        assertEquals(rtlFraction, ltrFraction, 0.0001f)

        // A nudge is not a decision.
        assertFalse(SlideConnectSpec.released(0f))
        assertFalse(SlideConnectSpec.released(0.5f))
        assertFalse(SlideConnectSpec.released(0.759f))
        assertTrue(SlideConnectSpec.released(0.76f))
    }
}
