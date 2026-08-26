package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarbleDesignSystemTest {
    @Test
    fun pingBandsFollowProductThresholds() {
        assertEquals(MarbleMetricBand.UNKNOWN, pingMetricBand(0))
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(1))
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(99))
        assertEquals(MarbleMetricBand.WARNING, pingMetricBand(100))
        assertEquals(MarbleMetricBand.WARNING, pingMetricBand(250))
        assertEquals(MarbleMetricBand.POOR, pingMetricBand(251))
        assertEquals(MarbleMetricBand.POOR, pingMetricBand(999))
    }

    @Test
    fun jitterBandsKeepUnknownNeutral() {
        assertEquals(MarbleMetricBand.UNKNOWN, jitterMetricBand(0, 0))
        assertEquals(MarbleMetricBand.GOOD, jitterMetricBand(19, 2))
        assertEquals(MarbleMetricBand.WARNING, jitterMetricBand(20, 2))
        assertEquals(MarbleMetricBand.WARNING, jitterMetricBand(50, 2))
        assertEquals(MarbleMetricBand.POOR, jitterMetricBand(51, 2))
    }

    @Test
    fun qualityBandsAreMonotonic() {
        assertEquals(MarbleMetricBand.UNKNOWN, qualityMetricBand(-1))
        assertEquals(MarbleMetricBand.POOR, qualityMetricBand(59))
        assertEquals(MarbleMetricBand.WARNING, qualityMetricBand(60))
        assertEquals(MarbleMetricBand.GOOD, qualityMetricBand(80))
        assertEquals(MarbleMetricBand.GOOD, qualityMetricBand(100))
    }
}
