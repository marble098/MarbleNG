package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PingSpeedTest {
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

        assertEquals(PingSpeed.MIN_PERCENT, PingSpeed.stepped(12.0))
        assertEquals(PingSpeed.MAX_PERCENT, PingSpeed.stepped(480.0))
    }

    @Test
    fun dialOffIsAlwaysTheShippedFasterDefault() {
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

        assertEquals(0.5, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 5).pingSpeedFactor(), 1e-9)
        assertEquals(2.0, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 900).pingSpeedFactor(), 1e-9)
    }

    @Test
    fun theDefaultDialReadsAsDefault() {
        assertEquals("Default", PingSpeed.label(custom = false, dialPercent = 137))
        assertEquals("Classic", PingSpeed.label(custom = true, dialPercent = 100))
        assertEquals("Slow", PingSpeed.label(custom = true, dialPercent = 50))
        assertEquals("Fast", PingSpeed.label(custom = true, dialPercent = 200))
    }

    @Test
    fun theShippedDefaultSweepsHalfAgainAsWide() {
        assertEquals(24, AppSettings(pingConcurrency = 16).pingWorkers())
        assertEquals(24, AppSettings(pingConcurrency = 16, pingSpeedCustom = false, pingSpeedPercent = 50).pingWorkers())

        assertEquals(16, AppSettings(pingConcurrency = 16, pingSpeedCustom = true, pingSpeedPercent = 100).pingWorkers())
        assertEquals(8, AppSettings(pingConcurrency = 16, pingSpeedCustom = true, pingSpeedPercent = 50).pingWorkers())
        assertEquals(32, AppSettings(pingConcurrency = 16, pingSpeedCustom = true, pingSpeedPercent = 200).pingWorkers())
    }

    @Test
    fun theScaledWidthStaysALegalConcurrency() {
        for (chips in PingBudget.CONCURRENCY_CHOICES) {
            for (percent in PingSpeed.MIN_PERCENT..PingSpeed.MAX_PERCENT step PingSpeed.STEP_PERCENT) {
                val width = AppSettings(pingConcurrency = chips, pingSpeedCustom = true, pingSpeedPercent = percent)
                    .pingWorkers()
                assertTrue(
                    "width $width out of range for chips=$chips percent=$percent",
                    width in PingBudget.CONCURRENCY_MIN..PingBudget.CONCURRENCY_MAX
                )

                val slower = AppSettings(pingConcurrency = chips, pingSpeedCustom = true, pingSpeedPercent = 50)
                    .pingWorkers()
                val faster = AppSettings(pingConcurrency = chips, pingSpeedCustom = true, pingSpeedPercent = 200)
                    .pingWorkers()
                assertTrue("slower dial widened $chips: $slower", slower <= width + chips)
                assertTrue("faster dial narrowed $chips: $faster", faster >= width - chips)
            }
        }

        val hostile = AppSettings(pingConcurrency = 0, pingSpeedCustom = true, pingSpeedPercent = 200)
        assertEquals(PingBudget.CONCURRENCY_MIN, hostile.pingWorkers())
    }

    @Test
    fun theShippedDefaultPacesTheQuietGapShorter() {
        assertEquals(40L, AppSettings().pingSampleSpacingMs())
        assertEquals(60L, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 100).pingSampleSpacingMs())
        assertEquals(30L, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 200).pingSampleSpacingMs())
        assertEquals(120L, AppSettings(pingSpeedCustom = true, pingSpeedPercent = 50).pingSampleSpacingMs())
    }

    @Test
    fun theQuietGapNeverBecomesABurstOrAStall() {
        for (percent in PingSpeed.MIN_PERCENT..PingSpeed.MAX_PERCENT step PingSpeed.STEP_PERCENT) {
            val gap = AppSettings(pingSpeedCustom = true, pingSpeedPercent = percent).pingSampleSpacingMs()
            assertTrue("gap $gap is a burst at percent=$percent", gap >= 20L)
            assertTrue("gap $gap is a stall at percent=$percent", gap <= 300L)
        }
    }

    @Test
    fun theDialNeverTouchesTheAccuracyBudget() {
        val classic = AppSettings(pingTimeoutSec = 5, pingSamples = 3)
        val hot = classic.copy(pingSpeedCustom = true, pingSpeedPercent = 200)
        val gentle = classic.copy(pingSpeedCustom = true, pingSpeedPercent = 50)
        assertEquals(classic.pingTimeoutMs(), hot.pingTimeoutMs())
        assertEquals(classic.pingTimeoutMs(), gentle.pingTimeoutMs())
        assertEquals(classic.pingSampleCount(), hot.pingSampleCount())
        assertEquals(classic.pingSampleCount(), gentle.pingSampleCount())
    }

    @Test
    fun thePerServerBudgetFollowsTheRealSpacing() {
        val withBaseGap = PingBudget.perServerBudgetMs(5, 3)
        val withDialGap = PingBudget.perServerBudgetMs(5, 3, 40L)
        assertTrue("a shorter gap must shave the budget: $withDialGap", withDialGap < withBaseGap)
        assertEquals(5 * 1_000L + 750L, PingBudget.perServerBudgetMs(5, 1))

        assertEquals(
            PingBudget.perServerBudgetMs(5, 3),
            PingBudget.perServerBudgetMs(5, 3, PingBudget.SAMPLE_SPACING_MS)
        )

        assertEquals(
            PingBudget.perServerBudgetMs(5, 3, 5_000L),
            PingBudget.perServerBudgetMs(5, 3, 60_000L)
        )
    }

    @Test
    fun theShippedDefaultsAreUnchanged() {
        val defaults = AppSettings()
        assertEquals(5, defaults.pingTimeoutSec)
        assertEquals(3, defaults.pingSamples)
        assertEquals(16, defaults.pingConcurrency)
        assertEquals(false, defaults.pingSpeedCustom)
        assertEquals(PingSpeed.DEFAULT_PERCENT, defaults.pingSpeedPercent)
    }
}
