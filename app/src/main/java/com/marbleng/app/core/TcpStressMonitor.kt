package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * TCP Stress Monitor & Adaptive MTU/MSS Engine for MarbleNG.
 *
 * Problem addressed: Logs showed `stressed=true` with high `retransDelta`, high `lost`,
 * many `unacked` segments, and MSS dropping to 524 or even 256. This indicates
 * path MTU issues, fragmentation, or throttling on the route. The system needs
 * to detect these conditions and automatically tune MTU/MSS downward to stabilize.
 *
 * This engine provides:
 * 1. Continuous TCP stress monitoring from live session telemetry
 * 2. Automatic MTU step-down when stress persists
 * 3. MSS auto-tuning based on measured path MTU
 * 4. Recovery detection: when stress clears, gradually step MTU back up
 * 5. Per-profile memory of optimal MTU for the current network
 */
class TcpStressMonitor {

    /** MTU step levels for progressive tuning. */
    enum class MtuLevel(val mtu: Int, val label: String) {
        FULL(1500, "Full MTU"),
        CELLULAR(1420, "Cellular-safe"),
        CONSERVATIVE(1380, "Conservative"),
        LOW(1360, "Low MTU"),
        MINIMUM(1280, "Minimum IPv6-safe");

        companion object {
            fun fromMtu(mtu: Int): MtuLevel = entries.minBy { kotlin.math.abs(it.mtu - mtu) }
            fun stepDown(current: MtuLevel): MtuLevel? {
                val idx = entries.indexOf(current)
                return if (idx < entries.size - 1) entries[idx + 1] else null
            }
            fun stepUp(current: MtuLevel): MtuLevel? {
                val idx = entries.indexOf(current)
                return if (idx > 0) entries[idx - 1] else null
            }
        }
    }

    /** Decision from the stress monitor. */
    data class TuningDecision(
        val shouldReduceMtu: Boolean,
        val shouldIncreaseMtu: Boolean,
        val recommendedLevel: MtuLevel,
        val reason: String,
        val urgency: Urgency
    ) {
        enum class Urgency { NONE, LOW, MEDIUM, HIGH, CRITICAL }
    }

    /** Rolling stress sample. */
    data class StressSample(
        val timestamp: Long,
        val retransmitRate: Double,
        val lossRate: Double,
        val mssRatio: Double,
        val unackedSegments: Int,
        val stressed: Boolean,
        val rttMs: Int
    )

    // State
    private val samples = mutableListOf<StressSample>()
    private val maxSamples = 20
    private var currentLevel = MtuLevel.FULL
    private var profileId = ""
    private var networkKey = ""
    private val stressStartedAt = AtomicLong(0)
    private val healthyStartedAt = AtomicLong(0)
    private val consecutiveStressed = AtomicInteger(0)
    private val consecutiveHealthy = AtomicInteger(0)

    // Per-profile learned MTU levels (persisted across sessions on same network)
    private val learnedLevels = mutableMapOf<String, MtuLevel>()

    // Thresholds
    private val stressRetransmitThreshold = 0.05   // 5% retransmit = stressed
    private val stressLossThreshold = 0.05          // 5% loss = stressed
    private val stressMssRatioThreshold = 0.55      // Below 55% = stressed
    private val healthyRetransmitThreshold = 0.02   // 2% = healthy
    private val healthyLossThreshold = 0.02
    private val stressConfirmSamples = 3            // 3 stressed samples in a row = confirmed
    private val recoveryConfirmSamples = 5          // 5 healthy samples = recovery candidate
    private val stressDurationBeforeAction = 30_000L  // 30s of stress before reducing MTU
    private val recoveryDurationBeforeAction = 120_000L  // 2 min healthy before increasing MTU

    /**
     * Record a new stress observation from the live session.
     */
    @Synchronized
    fun observe(
        retransmitRate: Double,
        lossRate: Double,
        mssRatio: Double,
        unackedSegments: Int,
        stressed: Boolean,
        rttMs: Int,
        profileId: String,
        networkKey: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (this.profileId != profileId || this.networkKey != networkKey) {
            resetForProfile(profileId, networkKey)
        }

        val sample = StressSample(nowMs, retransmitRate, lossRate, mssRatio, unackedSegments, stressed, rttMs)
        samples.add(sample)
        while (samples.size > maxSamples) samples.removeAt(0)

        if (stressed || isStressed(sample)) {
            consecutiveStressed.incrementAndGet()
            consecutiveHealthy.set(0)
            if (stressStartedAt.get() == 0L) {
                stressStartedAt.set(nowMs)
            }
        } else {
            consecutiveHealthy.incrementAndGet()
            consecutiveStressed.set(0)
            if (healthyStartedAt.get() == 0L) {
                healthyStartedAt.set(nowMs)
            }
        }
    }

    /**
     * Evaluate current stress level and produce a tuning decision.
     */
    @Synchronized
    fun evaluate(nowMs: Long = System.currentTimeMillis()): TuningDecision {
        if (samples.isEmpty()) {
            return TuningDecision(false, false, currentLevel, "no-data", TuningDecision.Urgency.NONE)
        }

        val recentSamples = samples.takeLast(5)
        val avgRetransmit = recentSamples.map { it.retransmitRate }.average()
        val avgLoss = recentSamples.map { it.lossRate }.average()
        val avgMssRatio = recentSamples.map { it.mssRatio }.average()
        val anyStressed = recentSamples.any { it.stressed }

        // Check if we should reduce MTU
        val stressDuration = if (stressStartedAt.get() > 0) nowMs - stressStartedAt.get() else 0
        val confirmedStress = consecutiveStressed.get() >= stressConfirmSamples &&
            stressDuration >= stressDurationBeforeAction

        if (confirmedStress && currentLevel != MtuLevel.MINIMUM) {
            val nextLevel = MtuLevel.stepDown(currentLevel)
            if (nextLevel != null) {
                currentLevel = nextLevel
                learnedLevels[profileKey()] = currentLevel
                stressStartedAt.set(0)
                consecutiveStressed.set(0)
                return TuningDecision(
                    shouldReduceMtu = true,
                    shouldIncreaseMtu = false,
                    recommendedLevel = currentLevel,
                    reason = "stress-confirmed: retrans=${String.format("%.1f%%", avgRetransmit * 100)}, " +
                        "loss=${String.format("%.1f%%", avgLoss * 100)}, mss=${String.format("%.0f%%", avgMssRatio * 100)}",
                    urgency = if (avgLoss > 0.10) TuningDecision.Urgency.CRITICAL else TuningDecision.Urgency.HIGH
                )
            }
        }

        // Critical: immediate action needed for severe stress
        if (avgLoss > 0.15 || avgRetransmit > 0.20 || avgMssRatio < 0.30) {
            if (currentLevel != MtuLevel.MINIMUM) {
                val nextLevel = MtuLevel.stepDown(currentLevel) ?: MtuLevel.MINIMUM
                currentLevel = nextLevel
                learnedLevels[profileKey()] = currentLevel
                return TuningDecision(
                    shouldReduceMtu = true,
                    shouldIncreaseMtu = false,
                    recommendedLevel = currentLevel,
                    reason = "critical-stress: immediate MTU reduction needed",
                    urgency = TuningDecision.Urgency.CRITICAL
                )
            }
        }

        // Check if we should increase MTU (recovery)
        val recoveryDuration = if (healthyStartedAt.get() > 0) nowMs - healthyStartedAt.get() else 0
        val confirmedRecovery = consecutiveHealthy.get() >= recoveryConfirmSamples &&
            recoveryDuration >= recoveryDurationBeforeAction

        if (confirmedRecovery && currentLevel != MtuLevel.FULL) {
            // Gradual recovery: only step up one level at a time
            val nextLevel = MtuLevel.stepUp(currentLevel)
            if (nextLevel != null) {
                currentLevel = nextLevel
                learnedLevels[profileKey()] = currentLevel
                healthyStartedAt.set(0)
                consecutiveHealthy.set(0)
                return TuningDecision(
                    shouldReduceMtu = false,
                    shouldIncreaseMtu = true,
                    recommendedLevel = currentLevel,
                    reason = "recovery-confirmed: path stabilized, testing higher MTU",
                    urgency = TuningDecision.Urgency.LOW
                )
            }
        }

        // No action needed
        return TuningDecision(
            shouldReduceMtu = false,
            shouldIncreaseMtu = false,
            recommendedLevel = currentLevel,
            reason = currentStressDescription(avgRetransmit, avgLoss, avgMssRatio),
            urgency = if (anyStressed) TuningDecision.Urgency.MEDIUM else TuningDecision.Urgency.NONE
        )
    }

    private fun isStressed(sample: StressSample): Boolean {
        return sample.retransmitRate > stressRetransmitThreshold ||
            sample.lossRate > stressLossThreshold ||
            sample.mssRatio < stressMssRatioThreshold ||
            sample.stressed
    }

    private fun currentStressDescription(retransmit: Double, loss: Double, mssRatio: Double): String {
        val parts = mutableListOf<String>()
        if (retransmit > stressRetransmitThreshold) parts += "high-retransmit"
        if (loss > stressLossThreshold) parts += "high-loss"
        if (mssRatio < stressMssRatioThreshold) parts += "low-mss"
        return if (parts.isEmpty()) "healthy" else parts.joinToString("+")
    }

    /**
     * Get the recommended MSS based on current MTU level and IPv6 status.
     */
    fun recommendedMss(hasIpv6: Boolean): Int {
        val overhead = if (hasIpv6) 60 else 40  // TCP/IP header overhead
        return (currentLevel.mtu - overhead).coerceIn(1160, 1460)
    }

    /**
     * Get the recommended MTU value.
     */
    fun recommendedMtu(): Int = currentLevel.mtu

    /**
     * Get the current MTU level.
     */
    fun currentMtuLevel(): MtuLevel = currentLevel

    /**
     * Get learned MTU level for a profile (from previous sessions).
     */
    fun learnedLevel(profileId: String, networkKey: String): MtuLevel? {
        return learnedLevels["$profileId@$networkKey"]
    }

    /**
     * Initialize for a profile/network combination, restoring learned state.
     */
    @Synchronized
    fun resetForProfile(profileId: String, networkKey: String) {
        this.profileId = profileId
        this.networkKey = networkKey
        samples.clear()
        stressStartedAt.set(0)
        healthyStartedAt.set(0)
        consecutiveStressed.set(0)
        consecutiveHealthy.set(0)

        // Restore learned level if available
        val learned = learnedLevels[profileKey()]
        currentLevel = learned ?: MtuLevel.FULL
    }

    private fun profileKey(): String = "$profileId@$networkKey"

    /**
     * Get diagnostic snapshot.
     */
    @Synchronized
    fun snapshot(): Map<String, Any> {
        val recent = samples.takeLast(5)
        return mapOf(
            "profileId" to profileId,
            "networkKey" to networkKey,
            "currentLevel" to currentLevel.name,
            "currentMtu" to currentLevel.mtu,
            "sampleCount" to samples.size,
            "consecutiveStressed" to consecutiveStressed.get(),
            "consecutiveHealthy" to consecutiveHealthy.get(),
            "avgRetransmit" to recent.map { it.retransmitRate }.average(),
            "avgLoss" to recent.map { it.lossRate }.average(),
            "avgMssRatio" to recent.map { it.mssRatio }.average(),
            "learnedProfiles" to learnedLevels.size
        )
    }
}
