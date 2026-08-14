package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import kotlin.math.max

data class ActiveRouteQuality(
    val latencyMs: Int,
    val jitterMs: Int,
    val samples: Int
)

data class OptimizerPlan(
    val candidates: List<ProxyProfile>,
    val deep: Boolean,
    val cycle: Int
)

data class OptimizerDecision(
    val target: ProxyProfile? = null,
    val summary: String,
    val gain: Double = 0.0
)

/**
 * Continuous route controller.
 *
 * The engine continuously exploits historically good routes while rotating an exploration window
 * through the whole library. This lets every node get real Xray challenges over successive cycles
 * without turning the phone into a permanent speed-test machine.
 */
class ContinuousRouteOptimizer(
    private val intelligence: MarbleIntelligence
) {
    private var cycle = 0
    private var lastScanAt = 0L
    private var lastSwitchAt = 0L
    private var pendingProfileId = ""
    private var pendingWins = 0

    @Synchronized
    fun reset(lastScan: Long = System.currentTimeMillis()) {
        cycle = 0
        lastScanAt = lastScan
        pendingProfileId = ""
        pendingWins = 0
    }

    @Synchronized
    fun shouldScan(
        settings: AppSettings,
        active: ActiveRouteQuality,
        thermalBudget: Double,
        liveDownBps: Long,
        force: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!settings.continuousOptimizerEnabled || active.samples < 3) return false
        if (thermalBudget < 0.45) return false

        val baseIntervalMs = settings.optimizerIntervalSec.coerceIn(60, 900) * 1_000L
        val degraded = active.latencyMs >= 300 || active.jitterMs >= 80
        val thermalFactor = if (thermalBudget < 0.65) 2L else 1L
        val interval = if (degraded) max(45_000L, baseIntervalMs / 2L) else baseIntervalMs * thermalFactor
        val elapsed = now - lastScanAt
        if (!force && elapsed < interval) return false

        val heavyTraffic = liveDownBps >= 3L * 1024L * 1024L
        if (settings.optimizerAvoidHeavyTraffic && heavyTraffic && !degraded && elapsed < interval * 2L) {
            return false
        }
        return true
    }

    @Synchronized
    fun plan(
        active: ProxyProfile,
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis()
    ): OptimizerPlan {
        val ordered = intelligence.orderCandidates(profiles, settings).filterNot { it.id == active.id }
        if (ordered.isEmpty()) {
            lastScanAt = now
            return OptimizerPlan(emptyList(), false, cycle)
        }

        val limit = settings.optimizerCandidateCount.coerceIn(2, 8).coerceAtMost(ordered.size)
        val stableCount = minOf(2, max(1, limit / 2))
        val stable = ordered.take(stableCount)
        val explorationPool = ordered.drop(stableCount)
        val exploreCount = (limit - stable.size).coerceAtLeast(0)
        val exploration = mutableListOf<ProxyProfile>()

        if (explorationPool.isNotEmpty() && exploreCount > 0) {
            val start = ((cycle.toLong() * exploreCount.toLong()) % explorationPool.size).toInt()
            repeat(exploreCount) { offset ->
                exploration += explorationPool[(start + offset) % explorationPool.size]
            }
        }

        val selected = (stable + exploration + ordered).distinctBy { it.id }.take(limit)
        cycle++
        lastScanAt = now
        val deepEvery = settings.optimizerDeepScanEvery.coerceIn(3, 20)
        return OptimizerPlan(selected, cycle % deepEvery == 0, cycle)
    }

    @Synchronized
    fun resolveTarget(
        active: ProxyProfile,
        profiles: List<ProxyProfile>,
        results: List<BenchmarkResult>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis()
    ): OptimizerDecision {
        val current = results.firstOrNull { it.profileId == active.id }
            ?: return OptimizerDecision(summary = "Autopilot • active route verification unavailable")
        val best = results.asSequence()
            .filter { it.profileId != active.id && it.success >= 75 }
            .maxByOrNull { it.score }
            ?: run {
                clearPending()
                return OptimizerDecision(summary = "Autopilot • ${active.name} remains best-known healthy route")
            }

        val cooldownMs = settings.optimizerSwitchCooldownSec.coerceIn(60, 1800) * 1_000L
        if (lastSwitchAt > 0L && now - lastSwitchAt < cooldownMs) {
            clearPending()
            return OptimizerDecision(summary = "Autopilot • challenger seen, switch cooldown is protecting stability")
        }

        val latencyGain = if (current.latencyMs > 0.0 && current.latencyMs < 9000.0) {
            (current.latencyMs - best.latencyMs) / current.latencyMs
        } else 1.0
        val jitterGain = if (current.jitterMs > 0.0 && current.jitterMs < 9000.0) {
            (current.jitterMs - best.jitterMs) / current.jitterMs
        } else 1.0
        val speedGain = if (current.bytesPerSecond > 64.0 * 1024.0) {
            (best.bytesPerSecond - current.bytesPerSecond) / current.bytesPerSecond
        } else if (best.bytesPerSecond > current.bytesPerSecond + 256.0 * 1024.0) 1.0 else 0.0

        val compositeGain = best.score - current.score
        val emergency = current.success < 75 && best.success >= 75
        val regressionSafe = emergency || (
            best.latencyMs <= current.latencyMs * 1.15 &&
                best.jitterMs <= max(25.0, current.jitterMs * 1.40)
            )
        val meaningful = emergency || (
            compositeGain >= 7.0 &&
                (latencyGain >= 0.12 || jitterGain >= 0.20 || speedGain >= 0.25) &&
                regressionSafe
            )

        if (!meaningful) {
            clearPending()
            return OptimizerDecision(
                summary = "Autopilot • ${active.name} held • challenger gain ${"%.1f".format(compositeGain)} below hysteresis"
            )
        }

        if (pendingProfileId == best.profileId) pendingWins++ else {
            pendingProfileId = best.profileId
            pendingWins = 1
        }
        val required = if (emergency) 1 else settings.optimizerConfirmations.coerceIn(1, 3)
        if (pendingWins < required) {
            return OptimizerDecision(summary = "Autopilot • ${best.name} leads • confirmation $pendingWins/$required")
        }

        val target = profiles.firstOrNull { it.id == best.profileId }
            ?: return OptimizerDecision(summary = "Autopilot • winning route no longer exists")
        clearPending()
        return OptimizerDecision(
            target = target,
            summary = "Autopilot • ${best.name} wins • ${best.latencyMs.toInt()} ms • jitter ${best.jitterMs.toInt()} ms • gain ${"%.1f".format(compositeGain)}",
            gain = compositeGain
        )
    }

    @Synchronized
    fun noteSwitch(now: Long = System.currentTimeMillis()) {
        lastSwitchAt = now
        clearPending()
    }

    @Synchronized
    private fun clearPending() {
        pendingProfileId = ""
        pendingWins = 0
    }
}

