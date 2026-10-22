package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.WorkloadProfile
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

object SurvivalFirstRanker {
    enum class NodeClassification(val rank: Int) {
        HEALTHY(0),
        UNCERTAIN(1),
        DEGRADED(2),
        DEAD(3),
        INVALID(4)
    }

    data class SurvivalScore(
        val classification: NodeClassification,
        val survivalScore: Double,
        val longevityBonus: Double,
        val packetHealthScore: Double,
        val probeScore: Double,
        val historicalScore: Double,
        val penaltyReason: String = "",
        val confidence: Double = 0.0,
        val rankingDecisionReason: String = ""
    )

    data class TcpStressMetrics(
        val retransmitRate: Double = 0.0,
        val lossRate: Double = 0.0,
        val mssRatio: Double = 1.0,
        val unackedSegments: Int = 0,
        val stressed: Boolean = false,
        val observedMs: Long = 0
    )

    private const val IRAN_PROBE_TIMEOUT_MULTIPLIER = 2.5
    private const val UNCERTAIN_RETENTION_HOURS = 4
    private const val LONGEVITY_MAX_BONUS = 25.0
    private const val LONGEVITY_RAMP_MINUTES = 15
    private const val PACKET_HEALTH_WEIGHT = 0.30
    private const val PROBE_WEIGHT = 0.25
    private const val HISTORICAL_WEIGHT = 0.25
    private const val LONGEVITY_WEIGHT = 0.20
    private const val FALSE_NEGATIVE_OVERRIDE_THRESHOLD = 60.0
    private const val DEAD_PROBE_THRESHOLD = 3
    private const val MAX_RETRANSMIT_RATE = 0.15
    private const val MAX_LOSS_RATE = 0.20
    private const val MIN_MSS_RATIO = 0.40

    fun scoreForSurvival(
        profile: ProxyProfile,
        probeResult: BenchmarkResult?,
        historicalHealth: HealthHistory?,
        tcpStress: TcpStressMetrics?,
        connectedDurationMs: Long = 0,
        settings: AppSettings,
        iranActive: Boolean = true,
        reconnectCount: Int = 0,
        resolverSuccessRatio: Double = 1.0,
        mssStable: Boolean = true,
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

        val weighted = when (classification) {
            NodeClassification.INVALID -> 0.0
            NodeClassification.HEALTHY -> {
                probeScore * PROBE_WEIGHT +
                    historicalScore * HISTORICAL_WEIGHT +
                    longevityBonus * LONGEVITY_WEIGHT +
                    packetHealth * PACKET_HEALTH_WEIGHT +
                    15.0
            }
            NodeClassification.UNCERTAIN -> {
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
                if (historicalScore > 80.0 && (historicalHealth?.consecutiveSuccesses ?: 0) > 5) {
                    historicalScore * 0.15
                } else {
                    0.0
                }
            }
        }.coerceIn(0.0, 100.0)

        val weightedWithSessionEvidence = when {
            classification == NodeClassification.INVALID -> 0.0

            reconnectCount >= 3 -> weighted * 0.85
            reconnectCount >= 6 -> weighted * 0.70
            else -> weighted
        }.let { base ->

            val resolverFactor = (resolverSuccessRatio.coerceIn(0.0, 1.0) * 0.30 + 0.70)

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

    private fun iranAdjustedProbeScore(probe: BenchmarkResult?): Double {
        if (probe == null) return 30.0

        if (probe.success > 0 && probe.latencyMs < 9000) {
            val reliability = probe.success.toDouble().coerceIn(0.0, 100.0)
            val latency = 100.0 * exp(-probe.latencyMs.coerceAtMost(5000.0) / 350.0)
            return (reliability * 0.55 + latency * 0.45).coerceIn(0.0, 100.0)
        }

        val reason = probe.failureReason.lowercase()
        val isTimeout = reason.contains("timeout") || reason.contains("timed out") ||
            reason.contains("deadline") || reason.contains("read timed")
        val isConnectionRefused = reason.contains("refused") || reason.contains("reset") ||
            reason.contains("connection reset")

        return when {
            isTimeout -> 35.0
            isConnectionRefused -> 15.0
            reason.contains("xray-start") -> 10.0
            reason.isBlank() && probe.success <= 0 -> 25.0
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

    private fun computeHistoricalScore(health: HealthHistory?): Double {
        if (health == null) return 40.0

        val baseScore = health.successEwma.coerceIn(0.0, 100.0)
        val latencyFactor = if (health.latencyEwma < 9000) {
            100.0 * exp(-health.latencyEwma / 400.0)
        } else {
            0.0
        }

        val consistencyBonus = if (health.consecutiveSuccesses > 3) {
            min(15.0, health.consecutiveSuccesses * 2.0)
        } else {
            0.0
        }

        return (baseScore * 0.60 + latencyFactor * 0.40 + consistencyBonus).coerceIn(0.0, 100.0)
    }

    private fun computeLongevityBonus(durationMs: Long): Double {
        if (durationMs <= 0) return 0.0
        val minutes = durationMs / 60_000.0
        val ramp = min(1.0, minutes / LONGEVITY_RAMP_MINUTES)
        return (LONGEVITY_MAX_BONUS * ramp).coerceIn(0.0, LONGEVITY_MAX_BONUS)
    }

    private fun computePacketHealthScore(stress: TcpStressMetrics?): Double {
        if (stress == null) return 60.0

        var score = 100.0

        if (stress.retransmitRate > 0.0) {
            val retransPenalty = when {
                stress.retransmitRate <= 0.02 -> 0.0
                stress.retransmitRate <= 0.05 -> 10.0
                stress.retransmitRate <= 0.10 -> 25.0
                stress.retransmitRate <= MAX_RETRANSMIT_RATE -> 45.0
                else -> 70.0
            }
            score -= retransPenalty
        }

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

        if (stress.mssRatio < 1.0) {
            val mssPenalty = when {
                stress.mssRatio >= 0.85 -> 0.0
                stress.mssRatio >= 0.60 -> 15.0
                stress.mssRatio >= MIN_MSS_RATIO -> 35.0
                else -> 60.0
            }
            score -= mssPenalty
        }

        if (stress.stressed) {
            score -= 10.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun classifyNode(
        probe: BenchmarkResult?,
        health: HealthHistory?,
        stress: TcpStressMetrics?,
        connectedMs: Long
    ): NodeClassification {
        if (connectedMs > 60_000 && stress != null && !stress.stressed) {
            return NodeClassification.HEALTHY
        }
        if (connectedMs > 120_000) return NodeClassification.HEALTHY

        if (probe != null && probe.success > 0 && probe.latencyMs < 9000) {
            if (stress != null && stress.retransmitRate > MAX_RETRANSMIT_RATE) {
                return NodeClassification.DEGRADED
            }
            return NodeClassification.HEALTHY
        }

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

        if (stress != null && (stress.retransmitRate > MAX_RETRANSMIT_RATE || stress.lossRate > MAX_LOSS_RATE)) {
            return NodeClassification.DEGRADED
        }

        if (probe == null && health == null) return NodeClassification.DEAD
        if (probe != null && probe.success <= 0 && (health == null || health.successEwma < 30.0)) {
            return NodeClassification.DEAD
        }

        return NodeClassification.UNCERTAIN
    }

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
            if (resolverSuccessRatio < 1.0) append("|resolverRate=").append(String.format(java.util.Locale.US, "%.2f", resolverSuccessRatio))
            if (!mssStable) append("|mss=unstable")
        }
    }

    private fun placeholderProfile(result: BenchmarkResult): ProxyProfile = ProxyProfile(
        id = result.profileId,
        name = result.name,
        scheme = "",
        raw = "",
        configJson = "",
        host = "",
        port = 0
    )

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

    data class HealthHistory(
        val successEwma: Double = 50.0,
        val latencyEwma: Double = 500.0,
        val consecutiveSuccesses: Int = 0,
        val totalSessions: Int = 0,
        val lastSeenAt: Long = 0
    )

    fun fromNodeHealth(record: com.marbleng.app.core.NodeHealthRecord?): HealthHistory? {
        if (record == null) return null

        val consecutive = (record.samples - record.failureStreak).coerceAtLeast(0)
        return HealthHistory(
            successEwma = record.successEwma,
            latencyEwma = record.latencyEwma,
            consecutiveSuccesses = consecutive,
            totalSessions = record.samples,
            lastSeenAt = record.lastSeenAt
        )
    }

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
