package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivalFirstRankerTest {
    private val settings = AppSettings()

    private fun timeoutProbe(id: String, name: String) = BenchmarkResult(
        profileId = id, name = name, success = 0, latencyMs = 9_999.0, bytesPerSecond = 0.0,
        score = 0.0, probeKind = "TUNNEL", failureReason = "httpsSocketTimeoutException: Read timed out"
    )

    private fun healthyProbe(id: String, name: String, latency: Double = 180.0) = BenchmarkResult(
        profileId = id, name = name, success = 100, latencyMs = latency, bytesPerSecond = 0.0,
        score = 90.0, probeKind = "TUNNEL", failureReason = ""
    )

    @Test
    fun timeoutWithStrongHistoryIsUncertainNotDead() {
        val history = SurvivalFirstRanker.HealthHistory(successEwma = 85.0, latencyEwma = 400.0, consecutiveSuccesses = 8, totalSessions = 12)
        val score = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("turkey-8"),
            probeResult = timeoutProbe("turkey-8", "Turkey 8"),
            historicalHealth = history,
            tcpStress = null,
            connectedDurationMs = 0,
            settings = settings,
            iranActive = true
        )
        assertEquals(SurvivalFirstRanker.NodeClassification.UNCERTAIN, score.classification)
        assertTrue(score.survivalScore > 0.0)
    }

    @Test
    fun timeoutWithoutHistoryIsDead() {
        val score = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("unknown-node"),
            probeResult = timeoutProbe("unknown-node", "Unknown"),
            historicalHealth = null,
            tcpStress = null,
            connectedDurationMs = 0,
            settings = settings,
            iranActive = true
        )
        assertEquals(SurvivalFirstRanker.NodeClassification.DEAD, score.classification)
        assertEquals(0.0, score.survivalScore, 0.001)
    }

    @Test
    fun healthyProbeBeatsUncertainAndDead() {
        val healthy = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("a"), probeResult = healthyProbe("a", "A"),
            historicalHealth = null, tcpStress = null, connectedDurationMs = 0,
            settings = settings, iranActive = true
        )
        val uncertain = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("b"), probeResult = timeoutProbe("b", "B"),
            historicalHealth = SurvivalFirstRanker.HealthHistory(successEwma = 80.0),
            tcpStress = null, connectedDurationMs = 0, settings = settings, iranActive = true
        )
        val dead = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("c"), probeResult = timeoutProbe("c", "C"),
            historicalHealth = null, tcpStress = null, connectedDurationMs = 0,
            settings = settings, iranActive = true
        )
        assertTrue(healthy.classification.rank < uncertain.classification.rank)
        assertTrue(uncertain.classification.rank < dead.classification.rank)
    }

    @Test
    fun partialProbeFailureStillKeepsSuccessfulTunnelNodeSelectable() {
        val results = listOf(
            timeoutProbe("aegis", "Aegis 1"),
            healthyProbe("turkey-8", "Turkey 8")
        )
        val histories = mapOf(
            "aegis" to SurvivalFirstRanker.HealthHistory(successEwma = 88.0, latencyEwma = 300.0, consecutiveSuccesses = 9, totalSessions = 20)
        )
        val reordered = SurvivalFirstRanker.reorderResults(results, histories, settings, iranActive = true)

        assertEquals("turkey-8", reordered.first().profileId)
        assertEquals("aegis", reordered[1].profileId)
    }

    @Test
    fun quarantinedProfileIsPinnedLastAndNeverSelected() {
        val results = listOf(
            timeoutProbe("good", "Good"),
            healthyProbe("turkey-4-all", "Turkey 4-All")
        )
        val histories = mapOf(
            "good" to SurvivalFirstRanker.HealthHistory(successEwma = 70.0)
        )
        val reordered = SurvivalFirstRanker.reorderResults(
            results, histories, settings, iranActive = true,
            quarantinedIds = setOf("turkey-4-all")
        )
        assertEquals("good", reordered.first().profileId)
        assertEquals("turkey-4-all", reordered.last().profileId)
    }

    @Test
    fun quaraantinedProfileScoresZeroAndInvalid() {
        val score = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("broken"), probeResult = healthyProbe("broken", "Broken"),
            historicalHealth = SurvivalFirstRanker.HealthHistory(successEwma = 95.0),
            tcpStress = null, connectedDurationMs = 0, settings = settings, iranActive = true,
            quarantined = true
        )
        assertEquals(SurvivalFirstRanker.NodeClassification.INVALID, score.classification)
        assertEquals(0.0, score.survivalScore, 0.001)
    }

    @Test
    fun reconnectPressureLowersSurvivalScore() {
        val baseline = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("x"), probeResult = healthyProbe("x", "X"),
            historicalHealth = null, tcpStress = null, connectedDurationMs = 0,
            settings = settings, iranActive = true, reconnectCount = 0
        )
        val churny = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("x"), probeResult = healthyProbe("x", "X"),
            historicalHealth = null, tcpStress = null, connectedDurationMs = 0,
            settings = settings, iranActive = true, reconnectCount = 8
        )
        assertTrue(churny.survivalScore < baseline.survivalScore)
    }

    @Test
    fun decisionReasonIsMachineReadable() {
        val score = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("aegis"), probeResult = timeoutProbe("aegis", "Aegis"),
            historicalHealth = SurvivalFirstRanker.HealthHistory(successEwma = 85.0),
            tcpStress = null, connectedDurationMs = 0, settings = settings, iranActive = true
        )
        assertTrue(score.rankingDecisionReason.contains("class=uncertain"))
        assertTrue(score.rankingDecisionReason.contains("probe=probe-timeout"))
        assertTrue(score.rankingDecisionReason.contains("history=history-strong"))
    }

    @Test
    fun iranSurvivalScoringOutranksSyntheticLowLatency() {
        val fastButUnproven = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("fast"), probeResult = timeoutProbe("fast", "Fast"),
            historicalHealth = null, tcpStress = null, connectedDurationMs = 0,
            settings = settings, iranActive = true
        )
        val slowButProven = SurvivalFirstRanker.scoreForSurvival(
            profile = placeholder("proven"), probeResult = timeoutProbe("proven", "Proven"),
            historicalHealth = SurvivalFirstRanker.HealthHistory(
                successEwma = 90.0, latencyEwma = 700.0, consecutiveSuccesses = 20, totalSessions = 40
            ),
            tcpStress = null, connectedDurationMs = 0, settings = settings, iranActive = true
        )
        assertTrue(slowButProven.survivalScore > fastButUnproven.survivalScore)
        assertEquals(SurvivalFirstRanker.NodeClassification.UNCERTAIN, slowButProven.classification)
    }

    private fun placeholder(id: String) = ProxyProfile(
        id = id, name = id, scheme = "vless", raw = "", configJson = "", host = "", port = 0
    )
}
