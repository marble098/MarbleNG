package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TcpStressMonitor {
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

    data class TuningDecision(
        val shouldReduceMtu: Boolean,
        val shouldIncreaseMtu: Boolean,
        val recommendedLevel: MtuLevel,
        val reason: String,
        val urgency: Urgency
    ) {
        enum class Urgency { NONE, LOW, MEDIUM, HIGH, CRITICAL }
    }

    data class StressSample(
        val timestamp: Long,
        val retransmitRate: Double,
        val lossRate: Double,
        val mssRatio: Double,
        val unackedSegments: Int,
        val stressed: Boolean,
        val rttMs: Int
    )

    private val samples = mutableListOf<StressSample>()
    private val maxSamples = 20
    private var currentLevel = MtuLevel.FULL
    private var profileId = ""
    private var networkKey = ""
    private val stressStartedAt = AtomicLong(0)
    private val healthyStartedAt = AtomicLong(0)
    private val consecutiveStressed = AtomicInteger(0)
    private val consecutiveHealthy = AtomicInteger(0)

    private val learnedLevels = mutableMapOf<String, MtuLevel>()

    private val stressRetransmitThreshold = 0.05
    private val stressLossThreshold = 0.05
    private val stressMssRatioThreshold = 0.55
    private val stressConfirmSamples = 3
    private val recoveryConfirmSamples = 5
    private val stressDurationBeforeAction = 30_000L
    private val severeStressDurationBeforeAction = 10_000L
    private val recoveryDurationBeforeAction = 120_000L

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
        nowMs: Long = System.currentTimeMillis(),
        currentMtu: Int? = null
    ) {
        if (this.profileId != profileId || this.networkKey != networkKey) {
            resetForProfile(profileId, networkKey, currentMtu)
        }

        val sample = StressSample(nowMs, retransmitRate, lossRate, mssRatio, unackedSegments, stressed, rttMs)
        samples.add(sample)
        while (samples.size > maxSamples) samples.removeAt(0)

        if (stressed || isStressed(sample)) {
            healthyStartedAt.set(0L)
            consecutiveStressed.incrementAndGet()
            consecutiveHealthy.set(0)
            if (stressStartedAt.get() == 0L) stressStartedAt.set(nowMs)
        } else {
            stressStartedAt.set(0L)
            consecutiveHealthy.incrementAndGet()
            consecutiveStressed.set(0)
            if (healthyStartedAt.get() == 0L) healthyStartedAt.set(nowMs)
        }
    }

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

        val stressDuration = if (stressStartedAt.get() > 0) {
            (nowMs - stressStartedAt.get()).coerceAtLeast(0L)
        } else 0L
        val severeSignal = avgLoss > 0.15 || avgRetransmit > 0.20
        val confirmedSevere = severeSignal &&
            consecutiveStressed.get() >= stressConfirmSamples &&
            stressDuration >= severeStressDurationBeforeAction
        if (confirmedSevere && currentLevel != MtuLevel.MINIMUM) {
            val nextLevel = MtuLevel.stepDown(currentLevel) ?: MtuLevel.MINIMUM
            currentLevel = nextLevel
            learnedLevels[profileKey()] = currentLevel
            stressStartedAt.set(0L)
            consecutiveStressed.set(0)
            return TuningDecision(
                shouldReduceMtu = true,
                shouldIncreaseMtu = false,
                recommendedLevel = currentLevel,
                reason = "severe-stress-confirmed: retrans=${String.format("%.1f%%", avgRetransmit * 100)}, " +
                    "loss=${String.format("%.1f%%", avgLoss * 100)}",
                urgency = TuningDecision.Urgency.CRITICAL
            )
        }

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
                    urgency = TuningDecision.Urgency.HIGH
                )
            }
        }

        val recoveryDuration = if (healthyStartedAt.get() > 0) nowMs - healthyStartedAt.get() else 0
        val confirmedRecovery = consecutiveHealthy.get() >= recoveryConfirmSamples &&
            recoveryDuration >= recoveryDurationBeforeAction

        if (confirmedRecovery && currentLevel != MtuLevel.FULL) {
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
            sample.stressed
    }

    private fun currentStressDescription(retransmit: Double, loss: Double, mssRatio: Double): String {
        val parts = mutableListOf<String>()
        if (retransmit > stressRetransmitThreshold) parts += "high-retransmit"
        if (loss > stressLossThreshold) parts += "high-loss"
        if (mssRatio < stressMssRatioThreshold) parts += "low-mss"
        return if (parts.isEmpty()) "healthy" else parts.joinToString("+")
    }

    fun recommendedMss(hasIpv6: Boolean): Int {
        val overhead = if (hasIpv6) 60 else 40
        return (currentLevel.mtu - overhead).coerceIn(1160, 1460)
    }

    fun recommendedMtu(): Int = currentLevel.mtu

    fun currentMtuLevel(): MtuLevel = currentLevel

    fun learnedLevel(profileId: String, networkKey: String): MtuLevel? {
        return learnedLevels["$profileId@$networkKey"]
    }

    @Synchronized
    fun resetForProfile(profileId: String, networkKey: String, currentMtu: Int? = null) {
        this.profileId = profileId
        this.networkKey = networkKey
        samples.clear()
        stressStartedAt.set(0L)
        healthyStartedAt.set(0L)
        consecutiveStressed.set(0)
        consecutiveHealthy.set(0)

        currentLevel = currentMtu?.takeIf { it in 1280..9000 }?.let { MtuLevel.fromMtu(it) }
            ?: learnedLevels[profileKey()]
            ?: MtuLevel.FULL
    }

    private fun profileKey(): String = "$profileId@$networkKey"

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
