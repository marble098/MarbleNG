package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Marble Freedom Aegis adaptive scorer (MARBLE_SMART_RANK_V90).
 *
 * These lock in the continuous adaptive-selection contract: per-network learned score tables,
 * 90-second dwell hysteresis after every successful selection, a catastrophic override (>40% loss
 * or full drop), and inconclusive first tests that stay `uncertain` and are re-probed in the
 * background instead of penalising the node.
 */
class AdaptiveAegisScorerTest {

    @Test
    fun healthyRouteIsKept() {
        val scorer = AdaptiveAegisScorer(dwellTimeMs = 90_000L) { 1_000_000L }
        scorer.recordScore("fp", "active", 80.0)
        scorer.noteSwitch("active", "fp", 1_000_000L)
        val decision = scorer.evaluate(
            "fp", "active",
            AdaptiveAegisScorer.Quality(rttMs = 120, lossPercent = 1.0),
            mapOf("other" to 60.0),
            nowMs = 1_001_000L
        )
        assertTrue(decision.keep)
        assertEquals(AdaptiveAegisScorer.State.HEALTHY, decision.state)
    }

    @Test
    fun dwellHysteresisBlocksSwitchBeforeNinetySeconds() {
        val scorer = AdaptiveAegisScorer(dwellTimeMs = 90_000L) { 1_000_000L }
        scorer.noteSwitch("active", "fp", 1_000_000L)
        scorer.recordScore("fp", "other", 95.0)
        val decision = scorer.evaluate(
            "fp", "active",
            AdaptiveAegisScorer.Quality(rttMs = 500, lossPercent = 30.0, stressFlag = true),
            mapOf("other" to 95.0),
            nowMs = 1_030_000L // only 30s after the selection
        )
        assertTrue(decision.keep)
        assertTrue(decision.reason.contains("dwell"))
    }

    @Test
    fun catastrophicDegradationBypassesDwell() {
        val scorer = AdaptiveAegisScorer(dwellTimeMs = 90_000L) { 1_000_000L }
        scorer.noteSwitch("active", "fp", 1_000_000L)
        scorer.recordScore("fp", "other", 95.0)
        val decision = scorer.evaluate(
            "fp", "active",
            AdaptiveAegisScorer.Quality(lossPercent = 60.0),
            mapOf("other" to 95.0),
            nowMs = 1_030_000L
        )
        assertFalse(decision.keep)
        assertEquals("other", decision.switchTo)
        assertTrue(decision.catastrophic)
    }

    @Test
    fun fullDropIsCatastrophicEvenWithoutLossPercentage() {
        val scorer = AdaptiveAegisScorer() { 1_000_000L }
        scorer.noteSwitch("active", "fp", 1_000_000L)
        scorer.recordScore("fp", "other", 95.0)
        val decision = scorer.evaluate(
            "fp", "active",
            AdaptiveAegisScorer.Quality(fullDrop = true),
            mapOf("other" to 95.0),
            nowMs = 1_010_000L
        )
        assertFalse(decision.keep)
        assertTrue(decision.catastrophic)
        assertEquals("other", decision.switchTo)
    }

    @Test
    fun inconclusiveFirstTestStaysUncertainAndIsNotPenalised() {
        val scorer = AdaptiveAegisScorer() { 1_000_000L }
        val decision = scorer.evaluate(
            "fp", "active",
            AdaptiveAegisScorer.Quality(uncertain = true),
            emptyMap(),
            nowMs = 1_000_000L
        )
        assertTrue(decision.keep)
        assertTrue(decision.uncertain)
        assertEquals(AdaptiveAegisScorer.State.UNCERTAIN, decision.state)
        assertTrue(decision.reason.contains("not penalised"))
    }

    @Test
    fun degradedRouteMigratesAfterDwellExpires() {
        val scorer = AdaptiveAegisScorer(dwellTimeMs = 90_000L) { 1_000_000L }
        scorer.noteSwitch("active", "fp", 1_000_000L)
        scorer.recordScore("fp", "other", 95.0)
        val decision = scorer.evaluate(
            "fp", "active",
            AdaptiveAegisScorer.Quality(rttMs = 500, lossPercent = 25.0, stressFlag = true),
            mapOf("other" to 95.0),
            nowMs = 1_100_000L // 100s after selection — dwell satisfied
        )
        assertFalse(decision.keep)
        assertEquals("other", decision.switchTo)
        assertEquals(AdaptiveAegisScorer.State.DEGRADED, decision.state)
    }

    @Test
    fun scoresAreSeparatePerNetworkFingerprint() {
        val scorer = AdaptiveAegisScorer() { 1_000_000L }
        scorer.recordScore("wifi-home", "a", 90.0)
        scorer.recordScore("operator-x", "a", 10.0)
        assertEquals(90.0, scorer.scoresFor("wifi-home")["a"]!!, 0.001)
        assertEquals(10.0, scorer.scoresFor("operator-x")["a"]!!, 0.001)
    }

    @Test
    fun fingerprintHashesIdentifiersInsteadOfStoringThem() {
        val fingerprint = NetworkFingerprint.compose(
            "wifi|v4|v6|unmetered|m1500",
            ssid = "MyHomeSSID",
            mobileNetworkCode = "43211"
        )
        assertTrue(fingerprint.contains("ssid-"))
        assertFalse(fingerprint.contains("MyHomeSSID"))
        assertTrue(fingerprint.contains("mnc-"))
        assertFalse(fingerprint.contains("43211"))
    }
}
