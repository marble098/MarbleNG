package com.marbleng.app.core

import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Per-network fingerprint helper (MARBLE_SMART_RANK_V90).
 *
 * In Iran the quality of every operator is completely different from the next, so Aegis learns a
 * separate score table per physical network. The fingerprint extends the existing stable
 * [com.marbleng.app.model] network key (transport / address-family / metered / MTU bucket) with a
 * hashed SSID and a hashed mobile-network code. Only the hash is ever kept — no plaintext SSID,
 * IMSI or other user identifier is persisted, matching the privacy boundary already documented in
 * [com.marbleng.app.core.NetworkSnapshot].
 */
object NetworkFingerprint {

    /**
     * Compose a stable, non-identifying fingerprint.
     *
     * @param networkKey the MarbleIntelligence network key (transport|family|metered|mtu bucket).
     * @param ssid optional Wi-Fi SSID (hashed; never stored in plaintext).
     * @param mobileNetworkCode optional MCC+MNC (hashed; never stored in plaintext).
     */
    fun compose(networkKey: String, ssid: String? = null, mobileNetworkCode: String? = null): String {
        val ssidHash = hashComponent(ssid)
        val mncHash = hashComponent(mobileNetworkCode)
        return listOf(
            networkKey.ifBlank { "unknown" },
            if (ssidHash.isBlank()) "ssid-any" else "ssid-$ssidHash",
            if (mncHash.isBlank()) "mnc-any" else "mnc-$mncHash"
        ).joinToString("|")
    }

    /** SHA-256 of [value] truncated to a short, stable token; blank for null/empty input. */
    fun hashComponent(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}

/**
 * Continuous adaptive scoring engine (MARBLE_SMART_RANK_V90).
 *
 * Replaces the one-shot 10-second test with a fixed score by a continuous evaluation loop:
 *
 *  - every 30-60 seconds the active connection is re-measured (RTT, loss, stress flag);
 *  - a significant degradation silently migrates the session to the best alternative instead of
 *    performing a raw reconnect;
 *  - per-network learning keeps a separate score table per [NetworkFingerprint.compose] fingerprint;
 *  - hysteresis enforces a minimum 90-second dwell after every successful selection before another
 *    switch is allowed — unless the degradation is catastrophic (>40% loss or a full drop);
 *  - an inconclusive first test (the observed TURBO live-inconclusive-backoff family) keeps the
 *    node in an `uncertain` state and schedules a background re-probe instead of penalising it.
 *
 * Pure (injectable clock) and thread-safe, so the whole decision model is deterministic in JVM
 * unit tests. The live migration loop consumes [Decision] results; the scorer itself never touches
 * Android, so it runs unchanged on-device and in tests.
 */
class AdaptiveAegisScorer(
    private val dwellTimeMs: Long = DEFAULT_DWELL_MS,
    private val catastrophicLossThreshold: Double = 0.40,
    private val switchMargin: Double = 8.0,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    enum class State { UNKNOWN, UNCERTAIN, HEALTHY, DEGRADED, CATASTROPHIC }

    /** Live quality sample of the currently-active route. */
    data class Quality(
        val rttMs: Int = 0,
        val lossPercent: Double = 0.0,
        val jitterMs: Int = 0,
        val stressFlag: Boolean = false,
        val uncertain: Boolean = false,
        /** True when the datapath has fully dropped (complete disconnection). */
        val fullDrop: Boolean = false
    )

    /** Per-network learned table snapshot for diagnostics. */
    data class NetworkTable(
        val fingerprint: String,
        val scores: Map<String, Double>,
        val states: Map<String, State>
    )

    /**
     * The decision the live loop must execute. [keep] is false when a switch to [switchTo] was
     * decided. [uncertain] tells the loop to re-probe in the background instead of switching.
     */
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

    /** Record a measured score for [profileId] on [fingerprint]. */
    fun recordScore(fingerprint: String, profileId: String, score: Double, state: State = State.HEALTHY) {
        scoreTable(fingerprint)[profileId] = score.coerceIn(0.0, 100.0)
        stateTable(fingerprint)[profileId] = state
    }

    /** Mark a switch so dwell-time hysteresis is measured from here. */
    fun noteSwitch(profileId: String, fingerprint: String, nowMs: Long = now()) {
        currentProfile.set(profileId)
        currentFingerprint.set(fingerprint)
        lastSwitchAt.set(nowMs)
    }

    /** Learned scores for one network fingerprint (best-first for the caller). */
    fun scoresFor(fingerprint: String): Map<String, Double> =
        scoreTable(fingerprint).toMap()

    /** Full learned table snapshot for one network fingerprint. */
    fun tableFor(fingerprint: String): NetworkTable = NetworkTable(
        fingerprint = fingerprint,
        scores = scoreTable(fingerprint).toMap(),
        states = stateTable(fingerprint).toMap()
    )

    /**
     * Decide whether to keep the active route or migrate to a better alternative.
     *
     * @param fingerprint the current physical-network fingerprint.
     * @param activeProfileId the currently-connected profile.
     * @param activeQuality the latest re-measured quality of the active route.
     * @param candidateScores fresh/learned scores for every alternative (profileId -> score).
     * @param nowMs injectable now for deterministic tests.
     */
    fun evaluate(
        fingerprint: String,
        activeProfileId: String,
        activeQuality: Quality,
        candidateScores: Map<String, Double>,
        nowMs: Long = now()
    ): Decision {
        // Catastrophic = packet loss above the configured threshold OR a complete datapath drop.
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

        // Inconclusive first test: hold the node and ask the caller to re-probe in the background.
        if (activeQuality.uncertain) {
            return Decision(
                keep = true, switchTo = null, state = State.UNCERTAIN, uncertain = true,
                catastrophic = false,
                reason = "uncertain • background re-probe scheduled, node not penalised"
            )
        }

        // Catastrophic degradation: bypass hysteresis immediately.
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

        // Hysteresis: 90s dwell after every successful selection (unless catastrophic, above).
        val lastSwitch = lastSwitchAt.get()
        val current = currentProfile.get()
        if (current == activeProfileId && lastSwitch > 0L && nowMs - lastSwitch < dwellTimeMs) {
            return Decision(
                keep = true, switchTo = null, state = state, uncertain = false,
                catastrophic = false,
                reason = "dwell • ${((dwellTimeMs - (nowMs - lastSwitch)) / 1000L).coerceAtLeast(0)}s remaining before any switch is allowed"
            )
        }

        // A healthy route has nothing to gain from switching.
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

    /** Reset every learned table (network change / explicit reset). */
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
        // Merge live candidate scores over the learned table; live evidence wins when present.
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
