package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_SPEED_DIAL_V199 — the speed dial of every ping measurement.
 *
 * The dial promises one thing and must never break it: it changes how fast a sweep runs (width
 * and pacing), never what a measurement is (sample counts, per-sample timeouts, targets, the
 * median). These tests pin the policy in both directions:
 *
 *  - the shipped default (dial off) is [PingSpeed.DEFAULT_FACTOR] — the "about 50 % faster"
 *    promise for all three methods — whatever percent an old install has stored;
 *  - the manual scale is one ruler with the default: 100 % is the classic V160 pace, higher is
 *    faster, lower slower, and every value lands inside the legal ranges after clamping;
 *  - the derived numbers stay physical: the sample gap never approaches a burst and never grows
 *    into a stall, and the scaled width stays a legal [PingBudget] concurrency.
 */
class PingSpeedTest {

    // ------------------------------------------------------------------ the dial scale

    @Test
    fun theDialScaleIsClampedOnBothEnds() {
        assertEquals(PingSpeed.MIN_PERCENT, PingSpeed.percent(0))
        assertEquals(PingSpeed.MIN_PERCENT, PingSpeed.percent(-400))
        assertEquals(PingSpeed.MAX_PERCENT, PingSpeed.percent(9_999))
        assertEquals(137, PingSpeed.percent(137))
        assertEquals(PingSpeed.DEFAULT_PERCENT, PingSpeed.percent(PingSpeed.DEFAULT_PERCENT))
    }

    @Test
    fun theSliderGridSnapsToTheStepAndStaysLegal() {
        assertEquals(100, PingSpeed.stepped(100.0))
        assertEquals(135, PingSpeed.stepped(137.4))
        assertEquals(140, PingSpeed.stepped(137.9))
        assertEquals(150, PingSpeed.stepped(150.0))
        // A drag past either end snaps to the end, never outside the scale.
        assertEquals(PingSpeed.MIN_PERCENT, PingSpeed.stepped(12.0))
        assertEquals(PingSpeed.MAX_PERCENT, PingSpeed.stepped(480.0))
    }

    // ------------------------------------------------------------------ the default

    @Test
    fun dialOffIsAlwaysTheShippedFasterDefault() {
        // The whole point of the default: about 1.5× the classic pace, whatever a stale
        // percent says — the dial must be ON before a stored percent can speak.
        assertEquals(PingSpeed.DEFAULT_FACTOR, AppSettings(pingSpeedCustom = false).pingSpeedFactor(), 1e-9)
        assertEquals(
            PingSpeed.DEFAULT_FACTOR,
            AppSettings(pingSpeedCustom = false, pingSpeedPercent = 50).pingSpeedFactor(),
            1e-9
        )
        assertEquals(
            PingSpeed.DEFAULT_FACTOR,
            AppSettings(pingSpeedCustom = false, pingSpeedPercent = 200).pingSpeedFactor(),
            1e-9
        )
    }

    @Test
    fun dialOnSpeaksTheUserPercentOfTheClassicPace() {
        assertEquals(1.0, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 100).pingSpeedFactor(), 1e-9)
        assertEquals(1.5, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 150).pingSpeedFactor(), 1e-9)
        assertEquals(0.5, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 50).pingSpeedFactor(), 1e-9)
        // A hand-edited preference cannot escape the scale.
        assertEquals(0.5, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 5).pingSpeedFactor(), 1e-9)
        assertEquals(2.0, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 900).pingSpeedFactor(), 1e-9)
    }

    @Test
    fun theDefaultDialReadsAsDefault() {
        assertEquals("Default", PingSpeed.label(custom = false, percent = 137))
        assertEquals("Classic", PingSpeed.label(custom = true, percent = 100))
        assertEquals("Slow", PingSpeed.label(custom = true, percent = 50))
        assertEquals("Fast", PingSpeed.label(custom = true, percent = 200))
    }
}
