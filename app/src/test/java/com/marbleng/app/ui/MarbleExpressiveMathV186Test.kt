package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_EXPRESSIVE_MOTION_V186 — the expressive layer's pure mathematics, pinned.
 *
 * Every wavy indicator, entrance cascade, page-depth transform and shape morph in the product
 * reads [ExpressiveMath]. These numbers are the difference between "expressive" and "wobbly":
 * a stagger that never caps animates a list's tail for a second, a depth curve that does not
 * clamp makes off-screen pages shrink forever, and a wave that leaves its band makes an arc
 * sweep past its own circle. The drawing code is allowed to evolve; the schedule, the bands and
 * the clamps are the contract.
 */
class MarbleExpressiveMathV186Test {

    // ------------------------------------------------------------------ stagger schedule

    @Test
    fun firstCardOfACascadeNeverWaits() {
        assertEquals(0L, ExpressiveMath.staggerDelayMs(0))
        // Negative indices are clamped to "first", never to a negative delay.
        assertEquals(0L, ExpressiveMath.staggerDelayMs(-3))
    }

    @Test
    fun staggerDelaysGrowOneStepPerCard() {
        assertEquals(ExpressiveMath.STAGGER_STEP_MS, ExpressiveMath.staggerDelayMs(1))
        assertEquals(ExpressiveMath.STAGGER_STEP_MS * 2, ExpressiveMath.staggerDelayMs(2))
        assertEquals(ExpressiveMath.STAGGER_STEP_MS * 3, ExpressiveMath.staggerDelayMs(3))
    }

    @Test
    fun staggerDelaysCapSoLongListsNeverAnimateATail() {
        val capped = ExpressiveMath.STAGGER_MAX_INDEX * ExpressiveMath.STAGGER_STEP_MS
        assertEquals(capped, ExpressiveMath.staggerDelayMs(ExpressiveMath.STAGGER_MAX_INDEX))
        assertEquals(capped, ExpressiveMath.staggerDelayMs(ExpressiveMath.STAGGER_MAX_INDEX + 1))
        assertEquals(capped, ExpressiveMath.staggerDelayMs(999))
    }

    // ------------------------------------------------------------------ phase wrapping and the wave

    @Test
    fun phaseWrapsIntoTheUnitLoopFromBothDirections() {
        assertEquals(0f, ExpressiveMath.wrap01(0f), 1e-6f)
        assertEquals(.5f, ExpressiveMath.wrap01(.5f), 1e-6f)
        assertEquals(0f, ExpressiveMath.wrap01(1f), 1e-6f)
        assertEquals(.25f, ExpressiveMath.wrap01(1.25f), 1e-6f)
        // Negative phases wrap forward: the wave is periodic, never mirrored.
        assertEquals(.75f, ExpressiveMath.wrap01(-0.25f), 1e-6f)
    }

    @Test
    fun waveStartsAtZeroPeaksMidLoopAndReturnsToZero() {
        assertEquals(0f, ExpressiveMath.wave(0f), 1e-5f)
        assertEquals(1f, ExpressiveMath.wave(.5f), 1e-5f)
        assertEquals(0f, ExpressiveMath.wave(1f), 1e-5f)
    }

    @Test
    fun waveNeverLeavesTheUnitBand() {
        var phase = 0f
        while (phase < 4f) {
            val value = ExpressiveMath.wave(phase)
            assertTrue("wave($phase) = $value left 0..1", value in -1e-5f..1.00001f)
            phase += .01f
        }
    }

    @Test
    fun wavyValueStaysInsideItsOwnBand() {
        var phase = 0f
        while (phase < 3f) {
            val sweep = ExpressiveMath.wavyValue(phase, 16f, 68f)
            assertTrue("sweep($phase) = $sweep left 16..68", sweep in 15.999f..68.001f)
            phase += .013f
        }
        // The band endpoints are actually reached (the wave is not damped).
        assertEquals(16f, ExpressiveMath.wavyValue(0f, 16f, 68f), 1e-4f)
        assertEquals(68f, ExpressiveMath.wavyValue(.5f, 16f, 68f), 1e-4f)
    }

    @Test
    fun arcSweepKeepsTheIndicatorInsideACircle() {
        // Four arcs of at most 68° can never overlap into a solid ring: the wavy spinner must
        // always read as separate travelling arcs.
        assertTrue(ExpressiveMath.arcSweep(.5f) * 4f < 360f)
        assertTrue(ExpressiveMath.arcSweep(.0f) > 0f)
    }

    // ------------------------------------------------------------------ shape morph

    @Test
    fun morphHitsBothEndpointsAndClampsOvershoot() {
        assertEquals(15f, ExpressiveMath.morph(15f, 999f, 0f), 1e-6f)
        assertEquals(999f, ExpressiveMath.morph(15f, 999f, 1f), 1e-6f)
        // 15 + (999 - 15) * .45 = 457.8 — the exact lerp midpoint the morph promises.
        assertEquals(457.8f, ExpressiveMath.morph(15f, 999f, .45f), 1e-4f)
        // A release spring overshoots t past 1: the radius clamps, the shape never inverts.
        assertEquals(999f, ExpressiveMath.morph(15f, 999f, 1.18f), 1e-6f)
        assertEquals(15f, ExpressiveMath.morph(15f, 999f, -.2f), 1e-6f)
    }

    // ------------------------------------------------------------------ page depth

    @Test
    fun settledPageIsFullSizeAndFullyOpaque() {
        assertEquals(1f, ExpressiveMath.depthScale(0f), 1e-6f)
        assertEquals(1f, ExpressiveMath.depthAlpha(0f), 1e-6f)
    }

    @Test
    fun depthRecedesSymmetricallyInBothSwipeDirections() {
        assertEquals(ExpressiveMath.depthScale(.4f), ExpressiveMath.depthScale(-.4f), 1e-6f)
        assertEquals(ExpressiveMath.depthAlpha(.4f), ExpressiveMath.depthAlpha(-.4f), 1e-6f)
        assertTrue(ExpressiveMath.depthScale(.6f) < ExpressiveMath.depthScale(.2f))
        assertTrue(ExpressiveMath.depthAlpha(.6f) < ExpressiveMath.depthAlpha(.2f))
    }

    @Test
    fun depthClampsAtOnePageSoOffscreenPagesStopShrinking() {
        assertEquals(ExpressiveMath.depthScale(1f), ExpressiveMath.depthScale(3.5f), 1e-6f)
        assertEquals(ExpressiveMath.depthAlpha(1f), ExpressiveMath.depthAlpha(-7f), 1e-6f)
        // The one-page floor stays clearly visible (a receding page is dimmed, never blanked).
        assertTrue(ExpressiveMath.depthAlpha(1f) >= .5f)
        assertTrue(ExpressiveMath.depthScale(1f) >= .9f)
    }

    // ------------------------------------------------------------------ trigonometry helpers

    @Test
    fun pointOnCircleTracksTheArcHead() {
        // 0° is the 3-o'clock point; 90° the 6-o'clock point; the determinate arc's leading dot
        // is drawn from exactly this mapping.
        val right = ExpressiveMath.pointOnCircle(50f, 50f, 20f, 0f)
        assertEquals(70f, right.x, 1e-4f)
        assertEquals(50f, right.y, 1e-4f)
        val bottom = ExpressiveMath.pointOnCircle(50f, 50f, 20f, 90f)
        assertEquals(50f, bottom.x, 1e-4f)
        assertEquals(70f, bottom.y, 1e-4f)
        val top = ExpressiveMath.pointOnCircle(50f, 50f, 20f, -90f)
        assertEquals(50f, top.x, 1e-4f)
        assertEquals(30f, top.y, 1e-4f)
    }

    @Test
    fun degreesToRadiansMapsTheFullTurn() {
        assertEquals(0f, ExpressiveMath.degreesToRadians(0f), 1e-6f)
        assertEquals(Math.PI.toFloat(), ExpressiveMath.degreesToRadians(180f), 1e-5f)
        assertEquals(2f * Math.PI.toFloat(), ExpressiveMath.degreesToRadians(360f), 1e-4f)
    }

    // ------------------------------------------------------------------ motion tokens

    @Test
    fun durationLadderIsStrictlyAscendingInsideEveryFamily() {
        val shorts = listOf(
            MarbleExpressiveMotion.Short1,
            MarbleExpressiveMotion.Short2,
            MarbleExpressiveMotion.Short3,
            MarbleExpressiveMotion.Short4
        )
        val mediums = listOf(
            MarbleExpressiveMotion.Medium1,
            MarbleExpressiveMotion.Medium2,
            MarbleExpressiveMotion.Medium3,
            MarbleExpressiveMotion.Medium4
        )
        val longs = listOf(
            MarbleExpressiveMotion.Long1,
            MarbleExpressiveMotion.Long2,
            MarbleExpressiveMotion.Long3,
            MarbleExpressiveMotion.Long4
        )
        val extraLongs = listOf(
            MarbleExpressiveMotion.ExtraLong1,
            MarbleExpressiveMotion.ExtraLong2,
            MarbleExpressiveMotion.ExtraLong3,
            MarbleExpressiveMotion.ExtraLong4
        )
        listOf(shorts, mediums, longs, extraLongs).forEach { family ->
            family.zipWithNext { earlier, later ->
                assertTrue("$earlier must precede $later", earlier < later)
            }
        }
        // The families never interleave: a Medium is always longer than any Short.
        assertTrue(shorts.last() < mediums.first())
        assertTrue(mediums.last() < longs.first())
        assertTrue(longs.last() < extraLongs.first())
        // And nothing in the product is allowed to take longer than the ExtraLong ceiling.
        assertTrue(extraLongs.last() <= 1_000)
    }

    @Test
    fun staggerStepMatchesTheTokenTheLibraryShips() {
        // The cascade schedule and the token ladder are one vocabulary: the stagger step the
        // modifier uses is the same constant the motion object documents.
        assertEquals(MarbleExpressiveMotion.StaggerStepMs, ExpressiveMath.STAGGER_STEP_MS)
    }
}
