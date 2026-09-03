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
        // MARBLE_HONEST_PING_V119 — the hostile 50 s value is clamped to the 10 s ceiling before
        // it can distort the summary; the honest 1 ms value is preserved as-is.
        val result = requireNotNull(LinkQualityEstimator.summarize(listOf(1, 50_000)))
        assertEquals(10_000, result.medianRttMs)
        assertEquals(10_000, result.p90RttMs)
        assertEquals(9_999, result.meanIpdvMs)
    }

    @Test
    fun preservesHonestSingleDigitReadings() {
        // MARBLE_HONEST_PING_V119 — 3 / 4 / 6 ms are genuine fast-route measurements, not
        // artifacts; the estimator publishes them untouched instead of rewriting them to 15.
        val result = requireNotNull(LinkQualityEstimator.summarize(listOf(3, 4, 6)))
        assertEquals(4, result.medianRttMs)
        assertEquals(6, result.p90RttMs)
        assertEquals(6, result.p95RttMs)
        assertEquals(2, result.meanIpdvMs)
        assertEquals(100, result.successPercent)
    }

    @Test
    fun sanitaryRttKeepsValidSamplesAndSilencesArtifacts() {
        assertEquals(0, LinkQualityEstimator.sanitaryRtt(0))
        assertEquals(0, LinkQualityEstimator.sanitaryRtt(-7))
        assertEquals(3, LinkQualityEstimator.sanitaryRtt(3))
        assertEquals(15, LinkQualityEstimator.sanitaryRtt(15))
        assertEquals(120, LinkQualityEstimator.sanitaryRtt(120))
        assertEquals(10_000, LinkQualityEstimator.sanitaryRtt(50_000))
        assertEquals(4.0, LinkQualityEstimator.sanitaryRtt(4.0), 0.0)
        assertEquals(0.0, LinkQualityEstimator.sanitaryRtt(Double.NaN), 0.0)
    }
}
