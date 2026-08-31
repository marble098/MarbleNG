package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the weighted multi-signal rank scorer (MARBLE_SMART_RANK_V90).
 *
 * These lock in the replacement of the single short HTTPS probe with a composite built from TCP
 * handshake success ratio, RTT median/p95, jitter, retransmission and packet loss. The crucial
 * behaviour for Iran's filtering: a single timeout must NOT fail a node — a node is only convicted
 * after the probe's 2-3 backoff attempts all fail.
 */
class MultiSignalRankScorerTest {

    private fun signals(
        ratio: Double,
        attempts: Int,
        rtt: Double = MultiSignalRankScorer.UNKNOWN_RTT,
        p95: Double = MultiSignalRankScorer.UNKNOWN_RTT,
        jitter: Double = 0.0,
        retransmit: Double = 0.0,
        loss: Double = 0.0,
        uncertain: Boolean = false,
        deprecated: Boolean = false
    ) = MultiSignalRankScorer.Signals(
        tcpHandshakeSuccessRatio = ratio,
        handshakeAttempts = attempts,
        rttMedianMs = rtt,
        rttP95Ms = p95,
        jitterMs = jitter,
        retransmitRate = retransmit,
        lossRate = loss,
        uncertain = uncertain,
        deprecated = deprecated
    )

    @Test
    fun healthyNodeScoresHigh() {
        val score = MultiSignalRankScorer.score(
            signals(ratio = 1.0, attempts = 3, rtt = 120.0, p95 = 180.0, jitter = 8.0)
        )
        assertEquals(MultiSignalRankScorer.Classification.HEALTHY, score.classification)
        assertTrue(score.score > 80.0)
        assertTrue(score.reason.contains("class=healthy"))
    }

    @Test
    fun oneTimeoutIsUncertainNotDead() {
        // Exactly the bug being fixed: one failed attempt must never convict a node.
        val score = MultiSignalRankScorer.score(signals(ratio = 0.0, attempts = 1))
        assertEquals(MultiSignalRankScorer.Classification.UNCERTAIN, score.classification)
        assertTrue("uncertain nodes stay selectable", score.score > 0.0)
    }

    @Test
    fun threeFailedAttemptsConvictDead() {
        val score = MultiSignalRankScorer.score(signals(ratio = 0.0, attempts = 3))
        assertEquals(MultiSignalRankScorer.Classification.DEAD, score.classification)
        assertEquals(0.0, score.score, 0.001)
    }

    @Test
    fun uncertainFlagKeepsNodeEligible() {
        val score = MultiSignalRankScorer.score(signals(ratio = 0.0, attempts = 3, uncertain = true))
        assertEquals(MultiSignalRankScorer.Classification.UNCERTAIN, score.classification)
        assertTrue(score.score > 0.0)
    }

    @Test
    fun deprecatedNodeIsInvalidAndHidden() {
        val score = MultiSignalRankScorer.score(signals(ratio = 1.0, attempts = 3, deprecated = true))
        assertEquals(MultiSignalRankScorer.Classification.INVALID, score.classification)
        assertEquals(0.0, score.score, 0.001)
    }

    @Test
    fun packetDegradationLowersScore() {
        val clean = MultiSignalRankScorer.score(
            signals(ratio = 1.0, attempts = 3, rtt = 120.0, p95 = 180.0, retransmit = 0.0, loss = 0.0)
        )
        val degraded = MultiSignalRankScorer.score(
            signals(ratio = 1.0, attempts = 3, rtt = 120.0, p95 = 180.0, retransmit = 0.08, loss = 0.10)
        )
        assertTrue(degraded.score < clean.score)
    }

    @Test
    fun longLivedSessionIsClassifiedHealthy() {
        val score = MultiSignalRankScorer.score(
            MultiSignalRankScorer.Signals(
                tcpHandshakeSuccessRatio = 0.8,
                handshakeAttempts = 3,
                sessionLifetimeMs = 120_000L
            )
        )
        assertEquals(MultiSignalRankScorer.Classification.HEALTHY, score.classification)
    }

    @Test
    fun reasonIsMachineReadableAndOneLine() {
        val score = MultiSignalRankScorer.score(
            signals(ratio = 1.0, attempts = 2, rtt = 300.0, jitter = 25.0)
        )
        assertTrue(score.reason.contains("attempts=2"))
        assertTrue(score.reason.contains("ratio=1.00"))
        assertTrue(score.reason.contains("rtt=300"))
    }
}
