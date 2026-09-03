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
        // MARBLE_REAL_PING_FLOOR_V116 — the hostile 1 ms value is floored to the realistic
        // 15 ms minimum before it can distort the summary, so the IPDV reflects true distance.
        val result = requireNotNull(LinkQualityEstimator.summarize(listOf(1, 50_000)))
        assertEquals(10_000, result.medianRttMs)
        assertEquals(10_000, result.p90RttMs)
        assertEquals(9_985, result.meanIpdvMs)
    }

    @Test
    fun floorsSingleDigitArtifactsAtTheRealisticMinimum() {
        // MARBLE_REAL_PING_FLOOR_V116 — 3 / 4 / 6 ms readings are measurement artifacts, not real
        // public-Internet RTTs; the estimator must never let them reach a summary untouched.
        val result = requireNotNull(LinkQualityEstimator.summarize(listOf(3, 4, 6)))
        assertEquals(15, result.medianRttMs)
        assertEquals(15, result.p90RttMs)
        assertEquals(15, result.p95RttMs)
        assertEquals(0, result.meanIpdvMs)
        assertEquals(100, result.successPercent)
    }

    @Test
    fun sanitaryRttKeepsValidSamplesAndSilencesArtifacts() {
        assertEquals(0, LinkQualityEstimator.sanitaryRtt(0))
        assertEquals(0, LinkQualityEstimator.sanitaryRtt(-7))
        assertEquals(15, LinkQualityEstimator.sanitaryRtt(3))
        assertEquals(15, LinkQualityEstimator.sanitaryRtt(15))
        assertEquals(120, LinkQualityEstimator.sanitaryRtt(120))
        assertEquals(10_000, LinkQualityEstimator.sanitaryRtt(50_000))
        assertEquals(15.0, LinkQualityEstimator.sanitaryRtt(4.0), 0.0)
        assertEquals(0.0, LinkQualityEstimator.sanitaryRtt(Double.NaN), 0.0)
    }
}
