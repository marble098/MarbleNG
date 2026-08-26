package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkQualityEstimatorTest {
    @Test
    fun emptyAndAllFailureWindowsHaveNoSummary() {
        assertNull(LinkQualityEstimator.summarize(emptyList()))
        assertNull(LinkQualityEstimator.summarize(listOf(-1, 0, -1)))
    }

    @Test
    fun missBreaksIpdvAdjacency() {
        val result = requireNotNull(
            LinkQualityEstimator.summarize(listOf(40, 50, -1, 100, 130))
        )
        assertEquals(2, result.jitterSamples)
        assertEquals(20, result.meanIpdvMs)
        assertEquals(80, result.successPercent)
        assertTrue(result.successLowerBoundPercent in 0..result.successPercent)
    }

    @Test
    fun reportsMedianAndNearestRankP90() {
        val result = requireNotNull(
            LinkQualityEstimator.summarize(listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100))
        )
        assertEquals(60, result.medianRttMs)
        assertEquals(90, result.p90RttMs)
        assertEquals(100, result.successPercent)
        assertEquals(9, result.jitterSamples)
    }

    @Test
    fun clampsHostileRttValues() {
        val result = requireNotNull(LinkQualityEstimator.summarize(listOf(1, 50_000)))
        assertEquals(10_000, result.p90RttMs)
        assertEquals(9_999, result.meanIpdvMs)
    }
}
