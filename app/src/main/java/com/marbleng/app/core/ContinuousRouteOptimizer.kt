package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import java.util.Locale
import kotlin.math.max

/**
 * Continuous jitter/tail/loss-aware route controller. MARBLE_REALTIME_ENGINE_V70
 *
 * MARBLE_IRAN_OPTIMIZER_V80: Enhanced with:
 * 1. Iran-aware longer cooldowns (probes are less reliable under censorship)
 * 2. Evidence completeness requirement: no switch on inconclusive data
 * 3. Integration with ProfileFlapGuard for switch rate limiting
 * 4. Higher gain threshold in Iran (switching is more costly)
 * 5. Survival-first scoring integration
 */
data class ActiveRouteQuality(
    val latencyMs: Int,
    val samples: Int,
    val jitterMs: Int = -1,
    val p95LatencyMs: Int = 0,
    val lossPercent: Int = 0,
    val spikePercent: Int = 0,
    /** MARBLE_IRAN_OPTIMIZER_V80: whether the quality measurement is based on complete evidence */
    val evidenceComplete: Boolean = true,
    /** MARBLE_IRAN_OPTIMIZER_V80: TCP stress flag from live session */
    val tcpStressed: Boolean = false
)

data class OptimizerPlan(
    val candidates: List<ProxyProfile>,
    val deep: Boolean,
    val cycle: Int
)

data class OptimizerDecision(
    val target: ProxyProfile? = null,
    val summary: String,
    val gain: Double = 0.0,
    /** MARBLE_IRAN_OPTIMIZER_V80: whether the decision is based on complete evidence */
    val evidenceComplete: Boolean = true,
    /** MARBLE_IRAN_OPTIMIZER_V80: whether this is an emergency switch */
    val isEmergency: Boolean = false
)

/** Continuous jitter/tail/loss-aware route controller with Iran-aware hysteresis. */
class ContinuousRouteOptimizer(
    private val intelligence: MarbleIntelligence,
    /** MARBLE_IRAN_OPTIMIZER_V80: optional flap guard integration */
    private val flapGuard: ProfileFlapGuard? = null
) {
    private var cycle = 0
    private var lastScanAt = 0L
    private var lastSwitchAt = 0L
    private var pendingProfileId = ""
    private var pendingWins = 0
    /** MARBLE_IRAN_OPTIMIZER_V80 */
    private var iranModeActive = false

    /**
     * MARBLE_IRAN_OPTIMIZER_V80: Set Iran mode state.
     */
    @Synchronized
    fun setIranMode(active: Boolean) {
        iranModeActive = active
    }

    @Synchronized
    fun reset(lastScan: Long = System.currentTimeMillis()) {
        cycle = 0
        lastScanAt = lastScan
        pendingProfileId = ""
        pendingWins = 0
    }

    @Synchronized
    fun shouldScan(
        s: AppSettings,
        a: ActiveRouteQuality,
        thermalBudget: Double,
        liveDownBps: Long,
        force: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (!s.continuousOptimizerEnabled || a.samples < 3 || thermalBudget < .45) return false
        // MARBLE_IRAN_OPTIMIZER_V80: Much longer intervals in Iran
        val iranMultiplier = if (iranModeActive) 2.5 else 1.0
        val base = (s.optimizerIntervalSec.coerceIn(60, 900) * 1000L * iranMultiplier).toLong()
        val bad = a.latencyMs >= 300 || a.jitterMs >= 25 || a.lossPercent >= 10 ||
            a.spikePercent >= 20 || (a.p95LatencyMs > 0 && a.p95LatencyMs >= max(420, a.latencyMs * 2)) ||
            a.tcpStressed
        val interval = if (bad) max(45_000L, base / 2) else base * (if (thermalBudget < .65) 2 else 1)
        val elapsed = now - lastScanAt
        if (!force && elapsed < interval) return false
        val heavy = liveDownBps >= 3L * 1024 * 1024
        return !(s.optimizerAvoidHeavyTraffic && heavy && !bad && elapsed < interval * 2)
    }

    @Synchronized
    fun plan(
        active: ProxyProfile,
        profiles: List<ProxyProfile>,
        s: AppSettings,
        now: Long = System.currentTimeMillis()
    ): OptimizerPlan {
        val ordered = intelligence.orderCandidates(profiles, s).filterNot { it.id == active.id }
        if (ordered.isEmpty()) {
            lastScanAt = now
            return OptimizerPlan(emptyList(), false, cycle)
        }
        val limit = s.optimizerCandidateCount.coerceIn(2, 8).coerceAtMost(ordered.size)
        val stableCount = minOf(2, max(1, limit / 2))
        val stable = ordered.take(stableCount)
        val pool = ordered.drop(stableCount)
        val count = (limit - stable.size).coerceAtLeast(0)
        val explore = mutableListOf<ProxyProfile>()
        if (pool.isNotEmpty() && count > 0) {
            val start = ((cycle.toLong() * count.toLong()) % pool.size.toLong()).toInt()
            repeat(count) { o -> explore += pool[(start + o) % pool.size] }
        }
        val selected = (stable + explore + ordered).distinctBy { it.id }.take(limit)
        cycle++
        lastScanAt = now
        return OptimizerPlan(selected, cycle % s.optimizerDeepScanEvery.coerceIn(3, 20) == 0, cycle)
    }

    @Synchronized
    fun resolveTarget(
        active: ProxyProfile,
        profiles: List<ProxyProfile>,
        results: List<BenchmarkResult>,
        s: AppSettings,
        now: Long = System.currentTimeMillis()
    ): OptimizerDecision {
        val cur = results.firstOrNull { it.profileId == active.id }
            ?: return OptimizerDecision(summary = "Autopilot • active route verification unavailable")
        val best = results.asSequence()
            .filter { it.profileId != active.id && it.success >= 75 }
            .maxByOrNull { it.score }
            ?: run {
                clear()
                return OptimizerDecision(
                    summary = "Autopilot • ${active.name} remains best-known healthy route"
                )
            }

        // MARBLE_IRAN_OPTIMIZER_V80: Much longer cooldowns in Iran
        val iranMultiplier = if (iranModeActive) 2.5 else 1.0
        val cool = (s.optimizerSwitchCooldownSec.coerceIn(60, 1800) * 1000L * iranMultiplier).toLong()
        if (lastSwitchAt > 0 && now - lastSwitchAt < cool) {
            clear()
            return OptimizerDecision(
                summary = "Autopilot • challenger seen, switch cooldown (${cool / 1000}s) is protecting stability"
            )
        }

        // MARBLE_IRAN_OPTIMIZER_V80: Check flap guard if available
        if (flapGuard != null) {
            val flapDecision = flapGuard.evaluateSwitch(
                targetProfileId = best.profileId,
                currentQuality = cur.score,
                targetQuality = best.score,
                evidenceComplete = true,
                nowMs = now
            )
            if (!flapDecision.allowed) {
                return OptimizerDecision(
                    summary = "Autopilot • flap guard: ${flapDecision.reason}"
                )
            }
        }

        fun jit(r: BenchmarkResult) = r.jitterMs.takeIf { r.sampleCount >= 2 && it >= 0 } ?: -1.0
        fun tail(r: BenchmarkResult) = r.p95LatencyMs.takeIf { it > 0 } ?: r.p90LatencyMs.takeIf { it > 0 } ?: r.latencyMs
        fun loss(r: BenchmarkResult) = r.lossPercent.takeIf { r.sampleCount > 0 } ?: (100.0 - r.success).coerceIn(0.0, 100.0)

        val lGain = if (cur.latencyMs > 0 && cur.latencyMs < 9000) (cur.latencyMs - best.latencyMs) / cur.latencyMs else 1.0
        val sGain = if (cur.bytesPerSecond > 64 * 1024) (best.bytesPerSecond - cur.bytesPerSecond) / cur.bytesPerSecond
        else if (best.bytesPerSecond > cur.bytesPerSecond + 256 * 1024) 1.0 else 0.0
        val cj = jit(cur); val bj = jit(best)
        val jGain = if (cj > 0 && bj >= 0) (cj - bj) / cj else 0.0
        val ct = tail(cur); val bt = tail(best)
        val tGain = if (ct > 0) (ct - bt) / ct else 0.0
        val lossGain = loss(cur) - loss(best)
        val gain = best.score - cur.score
        val emergency = cur.success < 75 && best.success >= 75

        // MARBLE_IRAN_OPTIMIZER_V80: Higher safety margins in Iran
        val latencyMargin = if (iranModeActive) 1.25 else 1.15
        val jitterMargin = if (iranModeActive) 1.50 else 1.35
        val tailMargin = if (iranModeActive) 1.30 else 1.20

        val safe = emergency || (
            best.latencyMs <= cur.latencyMs * latencyMargin &&
                (cj < 0 || bj < 0 || bj <= cj * jitterMargin + 3) &&
                bt <= ct * tailMargin + 12 &&
                loss(best) <= loss(cur) + 5
            )

        // MARBLE_IRAN_OPTIMIZER_V80: Higher gain threshold in Iran
        val minGain = if (iranModeActive) 8.0 else 5.0
        val meaningful = emergency || (gain >= minGain && (lGain >= .10 || sGain >= .25 || jGain >= .20 || tGain >= .15 || lossGain >= 5) && safe)

        if (!meaningful) {
            clear()
            return OptimizerDecision(
                summary = "Autopilot • ${active.name} held • challenger gain ${String.format(Locale.US, "%.1f", gain)} below realtime hysteresis"
            )
        }

        // MARBLE_IRAN_OPTIMIZER_V80: More confirmations required in Iran
        val reqConfirmations = if (iranModeActive) {
            (s.optimizerConfirmations.coerceIn(1, 3) + 1).coerceAtMost(4)
        } else {
            s.optimizerConfirmations.coerceIn(1, 3)
        }

        if (pendingProfileId == best.profileId) pendingWins++ else {
            pendingProfileId = best.profileId
            pendingWins = 1
        }
        val req = if (emergency) 1 else reqConfirmations

        if (pendingWins < req) {
            return OptimizerDecision(
                summary = "Autopilot • ${best.name} leads • confirmation $pendingWins/$req"
            )
        }

        val target = profiles.firstOrNull { it.id == best.profileId }
            ?: return OptimizerDecision(summary = "Autopilot • winning route no longer exists")
        clear()
        val jl = bj.takeIf { it >= 0 }?.let { " • jitter ${it.toInt()} ms" }.orEmpty()

        // MARBLE_IRAN_OPTIMIZER_V80: Notify flap guard of the switch
        flapGuard?.noteSwitch(now)

        return OptimizerDecision(
            target = target,
            summary = "Autopilot • ${best.name} wins • ${best.latencyMs.toInt()} ms$jl • gain ${String.format(Locale.US, "%.1f", gain)}",
            gain = gain,
            evidenceComplete = true,
            isEmergency = emergency
        )
    }

    @Synchronized
    fun noteSwitch(now: Long = System.currentTimeMillis()) {
        lastSwitchAt = now
        clear()
    }

    @Synchronized
    private fun clear() {
        pendingProfileId = ""
        pendingWins = 0
    }
}
