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
    enum class NodeClassification(val rank: Int) {
        /** Proven healthy: real session or multiple successful probes. */
        HEALTHY(0),
        /** Uncertain: probe timed out but historical data suggests it may work. */
        UNCERTAIN(1),
        /** Degraded: connects but with significant packet issues. */
        DEGRADED(2),
        /** Proven dead: multiple failed probes with no historical evidence. */
        DEAD(3),
        /** Structurally invalid; quarantined and excluded from selection. */
        INVALID(4)
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
        val confidence: Double = 0.0,
        /** Human- and machine-readable reason for the final ranking decision. */
        val rankingDecisionReason: String = ""
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
        iranActive: Boolean = true,
        /** Number of reconnect attempts for this node during the session (0 = first attempt). */
        reconnectCount: Int = 0,
        /** Resolver success ratio observed on this node (0.0-1.0); 1.0 = unknown/perfect. */
        resolverSuccessRatio: Double = 1.0,
        /** Whether the path MSS was stable during the session. */
        mssStable: Boolean = true,
        /** When true the profile is quarantined (INVALID) and never selected. */
        quarantined: Boolean = false
    ): SurvivalScore {
        val probeScore = if (iranActive) {
            iranAdjustedProbeScore(probeResult)
        } else {
            standardProbeScore(probeResult)
        }

        val historicalScore = computeHistoricalScore(historicalHealth)
        val longevityBonus = computeLongevityBonus(connectedDurationMs)
        val packetHealth = computePacketHealthScore(tcpStress)
        val classification = if (quarantined) {
            NodeClassification.INVALID
        } else {
            classifyNode(
                probeResult, historicalHealth, tcpStress, connectedDurationMs
            )
        }

        // Weighted combination
        val weighted = when (classification) {
            NodeClassification.INVALID -> 0.0
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

        // Survival-first refinements: weigh real session evidence so a node that actually carries
        // traffic outranks a synthetic probe that merely timed out.
        val weightedWithSessionEvidence = when {
            classification == NodeClassification.INVALID -> 0.0
            // Repeated reconnects on the same node mean the path is unstable -> penalise.
            reconnectCount >= 3 -> weighted * 0.85
            reconnectCount >= 6 -> weighted * 0.70
            else -> weighted
        }.let { base ->
            // Resolver pressure: a node whose resolver keeps failing is less survivable.
            val resolverFactor = (resolverSuccessRatio.coerceIn(0.0, 1.0) * 0.30 + 0.70)
            // MSS instability is a stress symptom; penalise survival score a little.
            val mssFactor = if (mssStable) 1.0 else 0.94
            (base * resolverFactor * mssFactor).coerceIn(0.0, 100.0)
        }

        val confidence = computeConfidence(probeResult, historicalHealth, tcpStress)
        val penalty = determinePenalty(probeResult, tcpStress, classification)
        val decisionReason = buildDecisionReason(
            profile, classification, probeResult, historicalHealth, connectedDurationMs,
            reconnectCount, resolverSuccessRatio, mssStable, quarantined
        )

        return SurvivalScore(
            classification = classification,
            survivalScore = weightedWithSessionEvidence,
            longevityBonus = longevityBonus,
            packetHealthScore = packetHealth,
            probeScore = probeScore,
            historicalScore = historicalScore,
            penaltyReason = penalty,
            confidence = confidence,
            rankingDecisionReason = decisionReason
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
     * Build a stable, machine-readable ranking decision reason so diagnostics can explain why a
     * node was selected (or why it was quarantined / not selected) without requiring raw numbers.
     */
    private fun buildDecisionReason(
        profile: ProxyProfile,
        classification: NodeClassification,
        probe: BenchmarkResult?,
        health: HealthHistory?,
        connectedMs: Long,
        reconnectCount: Int,
        resolverSuccessRatio: Double,
        mssStable: Boolean,
        quarantined: Boolean
    ): String {
        val probeState = when {
            quarantined -> "quarantined"
            probe == null -> "no-probe"
            probe.success > 0 -> "probe-ok"
            probe.failureReason.contains("timeout") || probe.failureReason.contains("timed out") ->
                "probe-timeout"
            probe.failureReason.contains("xray-start") -> "xray-start-failure"
            else -> "probe-failed"
        }
        val historyState = when {
            health == null -> "no-history"
            health.successEwma >= 60.0 -> "history-strong"
            else -> "history-weak"
        }
        return buildString {
            append("class=").append(classification.name.lowercase())
            append("|probe=").append(probeState)
            append("|history=").append(historyState)
            if (connectedMs > 0) append("|connectedMs=").append(connectedMs)
            if (reconnectCount > 0) append("|reconnects=").append(reconnectCount)
            if (resolverSuccessRatio < 1.0) append("|resolverRate=").append(String.format("%.2f", resolverSuccessRatio))
            if (!mssStable) append("|mss=unstable")
        }
    }

    /** Minimal placeholder profile used when reordering pure BenchmarkResults without a Profile. */
    private fun placeholderProfile(result: BenchmarkResult): ProxyProfile = ProxyProfile(
        id = result.profileId,
        name = result.name,
        scheme = "",
        raw = "",
        configJson = "",
        host = "",
        port = 0
    )

    /**
     * Re-order a finished Smart Rank result list for Iran-style censorship.
     *
     * The probe-driven result list is re-ranked survival-first: a node that timed out on the
     * generate204 probe is NOT hard-failed if it carries strong historical success evidence, and
     * quarantined/INVALID nodes are pushed to the very end. Healthy nodes keep their probe order.
     *
     * @param results the probe results as returned by the rank engine.
     * @param healthHistories per-profile health history (profileId -> HealthHistory).
     * @param settings app settings.
     * @param iranActive whether Iran Mode is active.
     * @param quarantinedIds profile ids that failed preflight and must never be selected.
     * @return the same results, re-ordered survival-first.
     */
    fun reorderResults(
        results: List<BenchmarkResult>,
        healthHistories: Map<String, HealthHistory>,
        settings: AppSettings,
        iranActive: Boolean,
        quarantinedIds: Set<String> = emptySet()
    ): List<BenchmarkResult> {
        return results
            .map { result ->
                val quarantined = result.profileId in quarantinedIds
                val score = scoreForSurvival(
                    profile = placeholderProfile(result),
                    probeResult = result,
                    historicalHealth = healthHistories[result.profileId],
                    tcpStress = null,
                    connectedDurationMs = 0,
                    settings = settings,
                    iranActive = iranActive,
                    quarantined = quarantined
                )
                result to score
            }
            .sortedWith(
                compareBy<Pair<BenchmarkResult, SurvivalScore>> {
                    it.second.classification.rank
                }.thenByDescending {
                    it.second.survivalScore
                }.thenBy {
                    it.first.latencyMs
                }
            )
            .map { it.first }
    }

    /** Categorise a result list into healthy / uncertain / failed counts for diagnostics. */
    fun categorize(
        results: List<BenchmarkResult>,
        healthHistories: Map<String, HealthHistory>,
        settings: AppSettings,
        iranActive: Boolean,
        quarantinedIds: Set<String> = emptySet()
    ): DiagnosticsSummary.RankingDecision {
        val uncert = results.count { r ->
            r.profileId !in quarantinedIds &&
                r.success <= 0 &&
                (r.failureReason.contains("timeout") || r.failureReason.contains("timed out")) &&
                (healthHistories[r.profileId]?.successEwma ?: 0.0) > 50.0
        }
        val healthy = results.count { it.success > 0 }
        val failed = results.size - healthy - uncert
        return DiagnosticsSummary.RankingDecision(
            selectedProfileId = results.firstOrNull()?.profileId.orEmpty(),
            decisionReason = results.firstOrNull()?.let {
                scoreForSurvival(
                    placeholderProfile(it), it,
                    healthHistories[it.profileId], null, 0, settings, iranActive,
                    quarantined = it.profileId in quarantinedIds
                ).rankingDecisionReason
            }.orEmpty(),
            uncertainCount = uncert,
            failedCount = failed,
            healthCount = healthy
        )
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
        iranActive: Boolean,
        quarantinedIds: Set<String> = emptySet(),
        reconnectCounts: Map<String, Int> = emptyMap()
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
                iranActive = iranActive,
                reconnectCount = reconnectCounts[profile.id] ?: 0,
                quarantined = profile.id in quarantinedIds
            )
            profile to score
        }.sortedWith(
            compareBy<Pair<ProxyProfile, SurvivalScore>> { it.second.classification.rank }
                .thenByDescending { it.second.survivalScore }
        )
    }
}
