package com.marbleng.app.model

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CONNECT_PILL_CRASH_V242 — reproduces the reported `Padding must be non-negative`
 * without a device: the expressive spatial spring really does pass below zero on 20 -> 0.
 * The layout boundary must never hand that frame to Modifier.padding.
 */
class ConnectPillMotionV242Test {

    @Test
    fun theOldSpatialSpringOvershootsZeroButTheLayoutInsetNeverDoes() {
        val spec = MotionScheme.expressive().defaultSpatialSpec<Dp>()
            .vectorize(Dp.VectorConverter)
        val start = AnimationVector1D(ConnectPillMotion.RESTING_INSET_DP)
        val end = AnimationVector1D(0f)
        val velocity = AnimationVector1D(0f)
        val duration = spec.getDurationNanos(start, end, velocity)
        val raw = (0..duration step 1_000_000L).map { nanos ->
            spec.getValueFromNanos(nanos, start, end, velocity).value
        }

        assertTrue("the regression needs a real negative overshoot", raw.any { it < 0f })
        raw.forEach { value ->
            val safe = ConnectPillMotion.insetDp(value)
            assertTrue("unsafe inset $safe", safe >= 0f && safe <= ConnectPillMotion.RESTING_INSET_DP)
        }
        assertEquals(0f, ConnectPillMotion.insetDp(-0.01f), 0f)
        assertEquals(ConnectPillMotion.RESTING_INSET_DP, ConnectPillMotion.insetDp(24f), 0f)
    }

    @Test
    fun theReplacementEffectsSpringStaysInsideThePaddingContract() {
        val spec = MotionScheme.expressive().defaultEffectsSpec<Dp>()
            .vectorize(Dp.VectorConverter)
        listOf(0f to ConnectPillMotion.RESTING_INSET_DP, ConnectPillMotion.RESTING_INSET_DP to 0f)
            .forEach { (from, to) ->
                val start = AnimationVector1D(from)
                val end = AnimationVector1D(to)
                val velocity = AnimationVector1D(0f)
                val duration = spec.getDurationNanos(start, end, velocity)
                for (nanos in 0..duration step 1_000_000L) {
                    val value = spec.getValueFromNanos(nanos, start, end, velocity).value
                    assertTrue("effects spring left the inset range: $value", value in 0f..ConnectPillMotion.RESTING_INSET_DP)
                }
            }
    }

    @Test
    fun nonFiniteAnimationFramesFailClosedAtTheLayoutBoundary() {
        assertEquals(0f, ConnectPillMotion.insetDp(Float.NaN), 0f)
        assertEquals(0f, ConnectPillMotion.insetDp(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, ConnectPillMotion.insetDp(Float.POSITIVE_INFINITY), 0f)
    }
}
