package com.marbleng.app.core

import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object NetworkFingerprint {
    fun compose(networkKey: String, ssid: String? = null, mobileNetworkCode: String? = null): String {
        val ssidHash = hashComponent(ssid)
        val mncHash = hashComponent(mobileNetworkCode)
        return listOf(
            networkKey.ifBlank { "unknown" },
            if (ssidHash.isBlank()) "ssid-any" else "ssid-$ssidHash",
            if (mncHash.isBlank()) "mnc-any" else "mnc-$mncHash"
        ).joinToString("|")
    }

    fun hashComponent(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}

class AdaptiveAegisScorer(
    private val dwellTimeMs: Long = DEFAULT_DWELL_MS,
    private val catastrophicLossThreshold: Double = 0.40,
    private val switchMargin: Double = 8.0,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    enum class State { UNKNOWN, UNCERTAIN, HEALTHY, DEGRADED, CATASTROPHIC }

    data class Quality(
        val rttMs: Int = 0,
        val lossPercent: Double = 0.0,
        val jitterMs: Int = 0,
        val stressFlag: Boolean = false,
        val uncertain: Boolean = false,
        val fullDrop: Boolean = false
    )

    data class NetworkTable(
        val fingerprint: String,
        val scores: Map<String, Double>,
        val states: Map<String, State>
    )

    data class Decision(
        val keep: Boolean,
        val switchTo: String?,
        val state: State,
        val uncertain: Boolean,
        val catastrophic: Boolean,
        val reason: String
    )

    private val scoreTables = ConcurrentHashMap<String, ConcurrentHashMap<String, Double>>()
    private val stateTables = ConcurrentHashMap<String, ConcurrentHashMap<String, State>>()
    private val lastSwitchAt = AtomicLong(0L)
    private val currentProfile = AtomicReference("")
    private val currentFingerprint = AtomicReference("")

    fun recordScore(fingerprint: String, profileId: String, score: Double, state: State = State.HEALTHY) {
        scoreTable(fingerprint)[profileId] = score.coerceIn(0.0, 100.0)
        stateTable(fingerprint)[profileId] = state
    }

    fun noteSwitch(profileId: String, fingerprint: String, nowMs: Long = now()) {
        currentProfile.set(profileId)
        currentFingerprint.set(fingerprint)
        lastSwitchAt.set(nowMs)
    }

    fun scoresFor(fingerprint: String): Map<String, Double> =
        scoreTable(fingerprint).toMap()

    fun tableFor(fingerprint: String): NetworkTable = NetworkTable(
        fingerprint = fingerprint,
        scores = scoreTable(fingerprint).toMap(),
        states = stateTable(fingerprint).toMap()
    )

    fun evaluate(
        fingerprint: String,
        activeProfileId: String,
        activeQuality: Quality,
        candidateScores: Map<String, Double>,
        nowMs: Long = now()
    ): Decision {
        val catastrophic = activeQuality.fullDrop ||
            activeQuality.lossPercent > catastrophicLossThreshold * 100.0 ||
            activeQuality.lossPercent >= 100.0

        val state = when {
            activeQuality.uncertain -> State.UNCERTAIN
            catastrophic -> State.CATASTROPHIC
            activeQuality.stressFlag || activeQuality.lossPercent >= 20.0 -> State.DEGRADED
            else -> State.HEALTHY
        }

        val activeScore = scoreTable(fingerprint)[activeProfileId] ?: 0.0

        if (activeQuality.uncertain) {
            return Decision(
                keep = true, switchTo = null, state = State.UNCERTAIN, uncertain = true,
                catastrophic = false,
                reason = "uncertain • background re-probe scheduled, node not penalised"
            )
        }

        if (catastrophic) {
            val best = bestAlternative(fingerprint, activeProfileId, candidateScores)
            if (best != null) {
                return Decision(
                    keep = false, switchTo = best.first, state = State.CATASTROPHIC,
                    uncertain = false, catastrophic = true,
                    reason = "catastrophic • loss=${String.format(Locale.US, "%.0f%%", activeQuality.lossPercent)} • migrating to ${best.first}"
                )
            }
            return Decision(
                keep = true, switchTo = null, state = State.CATASTROPHIC, uncertain = false,
                catastrophic = true, reason = "catastrophic but no healthy alternative available"
            )
        }

        val lastSwitch = lastSwitchAt.get()
        val current = currentProfile.get()
        if (current == activeProfileId && lastSwitch > 0L && nowMs - lastSwitch < dwellTimeMs) {
            return Decision(
                keep = true, switchTo = null, state = state, uncertain = false,
                catastrophic = false,
                reason = "dwell • ${((dwellTimeMs - (nowMs - lastSwitch)) / 1000L).coerceAtLeast(0)}s remaining before any switch is allowed"
            )
        }

        if (!activeQuality.stressFlag && activeQuality.lossPercent < 20.0 && state == State.HEALTHY) {
            recordScore(fingerprint, activeProfileId, maxOf(activeScore, 70.0), State.HEALTHY)
            return Decision(
                keep = true, switchTo = null, state = State.HEALTHY, uncertain = false,
                catastrophic = false, reason = "healthy • ${activeProfileId} is stable"
            )
        }

        val best = bestAlternative(fingerprint, activeProfileId, candidateScores)
            ?: return Decision(
                keep = true, switchTo = null, state = state, uncertain = false,
                catastrophic = false, reason = "degraded but no alternative beats the active route"
            )

        val gain = best.second - activeScore
        if (gain < switchMargin) {
            return Decision(
                keep = true, switchTo = null, state = state, uncertain = false,
                catastrophic = false,
                reason = "held • challenger ${best.first} gain ${String.format(Locale.US, "%.1f", gain)} below hysteresis margin ${String.format(Locale.US, "%.1f", switchMargin)}"
            )
        }

        return Decision(
            keep = false, switchTo = best.first, state = state, uncertain = false,
            catastrophic = false,
            reason = "switch • ${best.first} gain ${String.format(Locale.US, "%.1f", gain)} over ${activeProfileId}"
        )
    }

    fun reset() {
        scoreTables.clear()
        stateTables.clear()
        lastSwitchAt.set(0L)
        currentProfile.set("")
        currentFingerprint.set("")
    }

    private fun bestAlternative(
        fingerprint: String,
        activeProfileId: String,
        candidateScores: Map<String, Double>
    ): Pair<String, Double>? {
        val learned = scoreTable(fingerprint)

        val merged = learned.toMutableMap()
        candidateScores.forEach { (id, score) ->
            merged[id] = maxOf(merged[id] ?: 0.0, score)
        }
        return merged.entries
            .filter { it.key != activeProfileId }
            .maxByOrNull { it.value }
            ?.let { it.key to it.value }
    }

    private fun scoreTable(fingerprint: String): ConcurrentHashMap<String, Double> =
        scoreTables.getOrPut(fingerprint) { ConcurrentHashMap() }

    private fun stateTable(fingerprint: String): ConcurrentHashMap<String, State> =
        stateTables.getOrPut(fingerprint) { ConcurrentHashMap() }

    companion object {
        const val DEFAULT_DWELL_MS = 90_000L
    }
}
