package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextScaleV215Test {
    @Test
    fun theDefaultIsTheSmallestStepAndIsOnTheLadder() {
        // MARBLE_CALM_DEFAULT_TEXT_V253 — a fresh install starts at the quietest, smallest text.
        assertEquals(TextScale.MIN_PERCENT, TextScale.DEFAULT_PERCENT)
        assertTrue(TextScale.DEFAULT_PERCENT in TextScale.STEPS)
    }

    @Test
    fun everyStepIsInsideTheClampedRange() {
        TextScale.STEPS.forEach { step ->
            assertTrue("$step is outside ${TextScale.MIN_PERCENT}..${TextScale.MAX_PERCENT}", step in TextScale.MIN_PERCENT..TextScale.MAX_PERCENT)
        }
    }

    @Test
    fun theLadderIsStrictlyIncreasing() {
        assertEquals(TextScale.STEPS.sorted(), TextScale.STEPS)
        assertEquals(TextScale.STEPS.distinct(), TextScale.STEPS)
    }

    @Test
    fun aStoredValueOutsideTheLadderIsClamped() {
        assertEquals(TextScale.MIN_PERCENT, TextScale.clamp(-40))
        assertEquals(TextScale.MIN_PERCENT, TextScale.clamp(0))
        assertEquals(TextScale.MAX_PERCENT, TextScale.clamp(400))
        assertEquals(115, TextScale.clamp(115))
    }

    @Test
    fun theFactorIsThePercentAsAMultiplier() {
        assertEquals(1.0f, TextScale.factor(100), 0.0001f)
        assertEquals(1.3f, TextScale.factor(130), 0.0001f)
        assertEquals(TextScale.MIN_PERCENT / 100f, TextScale.factor(1), 0.0001f)
    }

    @Test
    fun snappedLandsOnFivePercentDetents() {
        assertEquals(100, TextScale.snapped(100))
        assertEquals(100, TextScale.snapped(102))
        assertEquals(105, TextScale.snapped(103))
        assertEquals(TextScale.MIN_PERCENT, TextScale.snapped(-40))
        assertEquals(TextScale.MAX_PERCENT, TextScale.snapped(400))
    }
}
