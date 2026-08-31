package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.WorkloadProfile
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Survival-First Ranking Engine for MarbleNG under severe censorship (Iran).
 *
 * Problem addressed: The old benchmark system would reject nodes with
 * `httpsSocketTimeoutException: Read timed out` even though those same nodes
 * could later establish CONNECTED sessions. This is because the ranking was
 * too sensitive to short HTTPS probes. In Iran's filtering environment:
 *
 * - A node that passes a 4-second HTTPS probe is not necessarily stable
 * - A node that fails a 4-second probe is not necessarily dead
 * - Connection longevity under load matters more than raw probe speed
 * - Packet health (retransmission, loss, MSS stability) is the true indicator
 *
 * This engine implements survival-first scoring:
 * 1. Timeout forgiveness: longer timeouts for initial probing
 * 2. Uncertain classification: nodes that timeout go to "uncertain" not "dead"
 * 3. Passive evidence weighting: real session data beats synthetic probes
 * 4. Longevity bonus: nodes that stay connected longer score higher
 * 5. Packet health scoring: retransmission/loss/MSS stability included
 * 6. False-negative prevention: historical connectivity data can override probe failures
 */
object SurvivalFirstRanker {

    /** Classification of a node's readiness under censorship. */
    enum class NodeClassification {
        /** Proven healthy: real session or multiple successful probes. */
        HEALTHY,
        /** Uncertain: probe timed out but historical data suggests it may work. */
        UNCERTAIN,
        /** Degraded: connects but with significant packet issues. */
        DEGRADED,
        /** Proven dead: multiple failed probes with no historical evidence. */
        DEAD
    }

    /** Extended scoring context with survival metrics. */
    data class SurvivalScore(
        val classification: NodeClassification,
        val survivalScore: Double,
        val longevityBonus: Double,
        val packetHealthScore: Double,
        val probeScore: Double,
        val historicalScore: Double,
        val penaltyReason: String = "",
        val confidence: Double = 0.0
    )

    /** TCP stress metrics from a live session. */
    data class TcpStressMetrics(
        val retransmitRate: Double = 0.0,      // 0.0 = no retransmissions
        val lossRate: Double = 0.0,             // 0.0 = no loss
        val mssRatio: Double = 1.0,             // 1.0 = MSS at expected level
        val unackedSegments: Int = 0,
        val stressed: Boolean = false,
        val observedMs: Long = 0
    )

    // Configuration constants for Iran's filtering environment
    private const val IRAN_PROBE_TIMEOUT_MULTIPLIER = 2.5  // 2.5x longer timeouts
    private const val UNCERTAIN_RETENTION_HOURS = 4        // Keep uncertain nodes for 4 hours
    private const val LONGEVITY_MAX_BONUS = 25.0           // Max 25 points for longevity
    private const val LONGEVITY_RAMP_MINUTES = 15          // Full bonus after 15 min connected
    private const val PACKET_HEALTH_WEIGHT = 0.30          // 30% of score from packet health
    private const val PROBE_WEIGHT = 0.25                  // 25% from synthetic probe
    private const val HISTORICAL_WEIGHT = 0.25             // 25% from history
    private const val LONGEVITY_WEIGHT = 0.20              // 20% from connection longevity
    private const val FALSE_NEGATIVE_OVERRIDE_THRESHOLD = 60.0  // Historical score to override probe fail
    private const val DEAD_PROBE_THRESHOLD = 3             // 3 failed probes = dead
    private const val MAX_RETRANSMIT_RATE = 0.15           // 15% retransmit = severely degraded
    private const val MAX_LOSS_RATE = 0.20                 // 20% loss = severely degraded
    private const val MIN_MSS_RATIO = 0.40                 // Below 40% of expected MSS = bad

    /**
     * Score a profile with survival-first logic for Iran's filtering environment.
     *
     * This replaces the raw HTTPS-probe-only scoring when Iran Mode is active or
     * when the environment is known to be heavily filtered.
     */
    fun scoreForSurvival(
        profile: ProxyProfile,
        probeResult: BenchmarkResult?,
        historicalHealth: HealthHistory?,
        tcpStress: TcpStressMetrics?,
        connectedDurationMs: Long = 0,
        settings: AppSettings,
        iranActive: Boolean = true
    ): SurvivalScore {
        val probeScore = if (iranActive) {
            iranAdjustedProbeScore(probeResult)
        } else {
            standardProbeScore(probeResult)
        }

        val historicalScore = computeHistoricalScore(historicalHealth)
        val longevityBonus = computeLongevityBonus(connectedDurationMs)
        val packetHealth = computePacketHealthScore(tcpStress)
        val classification = classifyNode(
            probeResult, historicalHealth, tcpStress, connectedDurationMs
        )

        // Weighted combination
        val weighted = when (classification) {
            NodeClassification.HEALTHY -> {
                probeScore * PROBE_WEIGHT +
                    historicalScore * HISTORICAL_WEIGHT +
                    longevityBonus * LONGEVITY_WEIGHT +
                    packetHealth * PACKET_HEALTH_WEIGHT +
                    15.0 // Base healthy bonus
            }
            NodeClassification.UNCERTAIN -> {
                // For uncertain nodes, historical data dominates over probe failure
                val probeContribution = if (historicalScore > FALSE_NEGATIVE_OVERRIDE_THRESHOLD) {
                    historicalScore * 0.60 + probeScore * 0.10 + packetHealth * 0.20 + longevityBonus * 0.10
                } else {
                    probeScore * 0.30 + historicalScore * 0.40 + packetHealth * 0.20 + longevityBonus * 0.10
                }
                probeContribution
            }
            NodeClassification.DEGRADED -> {
                packetHealth * 0.50 + historicalScore * 0.25 + probeScore * 0.15 + longevityBonus * 0.10
            }
            NodeClassification.DEAD -> {
                // Dead nodes get a small chance if history is very strong
                if (historicalScore > 80.0 && (historicalHealth?.consecutiveSuccesses ?: 0) > 5) {
                    historicalScore * 0.15
                } else {
                    0.0
                }
            }
        }.coerceIn(0.0, 100.0)

        val confidence = computeConfidence(probeResult, historicalHealth, tcpStress)
        val penalty = determinePenalty(probeResult, tcpStress, classification)

        return SurvivalScore(
            classification = classification,
            survivalScore = weighted,
            longevityBonus = longevityBonus,
            packetHealthScore = packetHealth,
            probeScore = probeScore,
            historicalScore = historicalScore,
            penaltyReason = penalty,
            confidence = confidence
        )
    }

    /**
     * Adjust probe scoring for Iran's filtering: timeouts are not fatal.
     */
    private fun iranAdjustedProbeScore(probe: BenchmarkResult?): Double {
        if (probe == null) return 30.0 // No probe = uncertain, not dead

        // If probe succeeded, score normally but with reduced speed emphasis
        if (probe.success > 0 && probe.latencyMs < 9000) {
            val reliability = probe.success.toDouble().coerceIn(0.0, 100.0)
            val latency = 100.0 * exp(-probe.latencyMs.coerceAtMost(5000.0) / 350.0) // Gentler decay
            return (reliability * 0.55 + latency * 0.45).coerceIn(0.0, 100.0)
        }

        // If probe failed due to timeout (not connection refused), give partial credit
        val reason = probe.failureReason.lowercase()
        val isTimeout = reason.contains("timeout") || reason.contains("timed out") ||
            reason.contains("deadline") || reason.contains("read timed")
        val isConnectionRefused = reason.contains("refused") || reason.contains("reset") ||
            reason.contains("connection reset")

        return when {
            isTimeout -> 35.0  // Timeout in Iran is not conclusive -> uncertain
            isConnectionRefused -> 15.0  // Connection refused is more concerning
            reason.contains("xray-start") -> 10.0  // Xray startup failure = config issue
            reason.isBlank() && probe.success <= 0 -> 25.0  // Unknown failure
            else -> 20.0
        }
    }

    private fun standardProbeScore(probe: BenchmarkResult?): Double {
        if (probe == null) return 0.0
        if (probe.success <= 0 || probe.latencyMs >= 9000) return 0.0
        val reliability = probe.success.toDouble().coerceIn(0.0, 100.0)
        val latency = 100.0 * exp(-probe.latencyMs.coerceAtMost(5000.0) / 240.0)
        return (reliability * 0.45 + latency * 0.55).coerceIn(0.0, 100.0)
    }

    /**
     * Historical health score: how well has this node performed in past sessions.
     */
    private fun computeHistoricalScore(health: HealthHistory?): Double {
        if (health == null) return 40.0 // No history = neutral

        val baseScore = health.successEwma.coerceIn(0.0, 100.0)
        val latencyFactor = if (health.latencyEwma < 9000) {
            100.0 * exp(-health.latencyEwma / 400.0)
        } else {
            0.0
        }

        // Bonus for consistency (low variance in past performance)
        val consistencyBonus = if (health.consecutiveSuccesses > 3) {
            min(15.0, health.consecutiveSuccesses * 2.0)
        } else {
            0.0
        }

        return (baseScore * 0.60 + latencyFactor * 0.40 + consistencyBonus).coerceIn(0.0, 100.0)
    }

    /**
     * Connection longevity bonus: longer-lived connections are more valuable.
     */
    private fun computeLongevityBonus(durationMs: Long): Double {
        if (durationMs <= 0) return 0.0
        val minutes = durationMs / 60_000.0
        val ramp = min(1.0, minutes / LONGEVITY_RAMP_MINUTES)
        return (LONGEVITY_MAX_BONUS * ramp).coerceIn(0.0, LONGEVITY_MAX_BONUS)
    }

    /**
     * Packet health score based on TCP stress metrics.
     */
    private fun computePacketHealthScore(stress: TcpStressMetrics?): Double {
        if (stress == null) return 60.0 // No metrics = assume average

        var score = 100.0

        // Retransmission penalty
        if (stress.retransmitRate > 0.0) {
            val retransPenalty = when {
                stress.retransmitRate <= 0.02 -> 0.0   // Normal
                stress.retransmitRate <= 0.05 -> 10.0   // Mild
                stress.retransmitRate <= 0.10 -> 25.0   // Moderate
                stress.retransmitRate <= MAX_RETRANSMIT_RATE -> 45.0  // Severe
                else -> 70.0  // Critical
            }
            score -= retransPenalty
        }

        // Loss penalty
        if (stress.lossRate > 0.0) {
            val lossPenalty = when {
                stress.lossRate <= 0.01 -> 0.0
                stress.lossRate <= 0.05 -> 15.0
                stress.lossRate <= 0.10 -> 30.0
                stress.lossRate <= MAX_LOSS_RATE -> 55.0
                else -> 80.0
            }
            score -= lossPenalty
        }

        // MSS degradation penalty
        if (stress.mssRatio < 1.0) {
            val mssPenalty = when {
                stress.mssRatio >= 0.85 -> 0.0
                stress.mssRatio >= 0.60 -> 15.0
                stress.mssRatio >= MIN_MSS_RATIO -> 35.0
                else -> 60.0
            }
            score -= mssPenalty
        }

        // Stress flag bonus penalty
        if (stress.stressed) {
            score -= 10.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    /**
     * Classify a node's state based on all available evidence.
     */
    private fun classifyNode(
        probe: BenchmarkResult?,
        health: HealthHistory?,
        stress: TcpStressMetrics?,
        connectedMs: Long
    ): NodeClassification {
        // Currently connected and surviving -> healthy
        if (connectedMs > 60_000 && stress != null && !stress.stressed) {
            return NodeClassification.HEALTHY
        }
        if (connectedMs > 120_000) return NodeClassification.HEALTHY

        // Probe succeeded -> healthy (unless packet health is terrible)
        if (probe != null && probe.success > 0 && probe.latencyMs < 9000) {
            if (stress != null && stress.retransmitRate > MAX_RETRANSMIT_RATE) {
                return NodeClassification.DEGRADED
            }
            return NodeClassification.HEALTHY
        }

        // Probe failed but strong history -> uncertain (false negative prevention)
        if (probe != null && probe.success <= 0) {
            val timeout = probe.failureReason.lowercase().let { reason ->
                reason.contains("timeout") || reason.contains("timed out") || reason.contains("deadline")
            }
            if (timeout && health != null && health.successEwma > 50.0) {
                return NodeClassification.UNCERTAIN
            }
            if (health != null && health.successEwma > 70.0 && health.consecutiveSuccesses > 3) {
                return NodeClassification.UNCERTAIN
            }
        }

        // Severe packet issues -> degraded
        if (stress != null && (stress.retransmitRate > MAX_RETRANSMIT_RATE || stress.lossRate > MAX_LOSS_RATE)) {
            return NodeClassification.DEGRADED
        }

        // No evidence of connectivity and probe failed -> dead
        if (probe == null && health == null) return NodeClassification.DEAD
        if (probe != null && probe.success <= 0 && (health == null || health.successEwma < 30.0)) {
            return NodeClassification.DEAD
        }

        return NodeClassification.UNCERTAIN
    }

    /**
     * Compute confidence in the score (0.0 = guessing, 1.0 = very sure).
     */
    private fun computeConfidence(
        probe: BenchmarkResult?,
        health: HealthHistory?,
        stress: TcpStressMetrics?
    ): Double {
        var evidence = 0.0

        if (probe != null && probe.sampleCount > 0) {
            evidence += min(0.35, probe.sampleCount * 0.10)
        }
        if (health != null) {
            evidence += min(0.35, health.totalSessions * 0.05)
        }
        if (stress != null && stress.observedMs > 30_000) {
            evidence += min(0.30, stress.observedMs / 300_000.0)
        }

        return evidence.coerceIn(0.0, 1.0)
    }

    private fun determinePenalty(
        probe: BenchmarkResult?,
        stress: TcpStressMetrics?,
        classification: NodeClassification
    ): String {
        val reasons = mutableListOf<String>()
        if (classification == NodeClassification.DEAD) reasons += "no-connectivity-evidence"
        if (classification == NodeClassification.DEGRADED) reasons += "packet-health-degraded"
        if (stress != null && stress.stressed) reasons += "tcp-stressed"
        if (stress != null && stress.mssRatio < MIN_MSS_RATIO) reasons += "mss-degraded"
        if (probe != null && probe.failureReason.contains("xray-start")) reasons += "xray-start-failure"
        return reasons.joinToString("+")
    }

    /**
     * Minimal health history representation for survival scoring.
     */
    data class HealthHistory(
        val successEwma: Double = 50.0,
        val latencyEwma: Double = 500.0,
        val consecutiveSuccesses: Int = 0,
        val totalSessions: Int = 0,
        val lastSeenAt: Long = 0
    )

    /**
     * Convert a NodeHealthRecord to our HealthHistory format.
     */
    fun fromNodeHealth(record: com.marbleng.app.core.NodeHealthRecord?): HealthHistory? {
        if (record == null) return null
        // Estimate consecutive successes from failure streak and samples
        val consecutive = (record.samples - record.failureStreak).coerceAtLeast(0)
        return HealthHistory(
            successEwma = record.successEwma,
            latencyEwma = record.latencyEwma,
            consecutiveSuccesses = consecutive,
            totalSessions = record.samples,
            lastSeenAt = record.lastSeenAt
        )
    }

    /**
     * Apply survival-first ranking to a batch of benchmark results.
     * Returns re-ranked results with survival scores attached.
     */
    fun rankBatch(
        profiles: List<ProxyProfile>,
        results: List<BenchmarkResult>,
        healthHistories: Map<String, HealthHistory>,
        stressMetrics: Map<String, TcpStressMetrics>,
        connectedDurations: Map<String, Long>,
        settings: AppSettings,
        iranActive: Boolean
    ): List<Pair<ProxyProfile, SurvivalScore>> {
        val resultById = results.associateBy { it.profileId }
        return profiles.map { profile ->
            val score = scoreForSurvival(
                profile = profile,
                probeResult = resultById[profile.id],
                historicalHealth = healthHistories[profile.id],
                tcpStress = stressMetrics[profile.id],
                connectedDurationMs = connectedDurations[profile.id] ?: 0,
                settings = settings,
                iranActive = iranActive
            )
            profile to score
        }.sortedByDescending { it.second.survivalScore }
    }
}
