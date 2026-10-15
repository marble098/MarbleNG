package com.marbleng.app.core

import kotlin.math.abs

object PathMtuPolicy {
    data class State(
        val lastObserved: Int = 0,
        val repeats: Int = 0,
        val committed: Int = 0,
        val committedAtMs: Long = 0L
    )

    data class Decision(
        val state: State,
        val commitMtu: Int?,
        val requestTune: Boolean,
        val reason: String,
        val throttlingSynchronized: Boolean = false
    )

    const val CONFIRMATIONS = 2

    const val MATERIAL_DROP_BYTES = 40

    const val MIN_COMMIT_INTERVAL_MS = 45_000L

    const val MIN_MTU = 1280
    const val MAX_MTU = 9000

    fun observe(
        state: State,
        observedMtu: Int,
        activeMtu: Int,
        nowMs: Long,
        sawtoothConfidence: Double = 0.0,
        throttleActive: Boolean = false
    ): Decision {
        if (observedMtu !in MIN_MTU..MAX_MTU) {
            return Decision(state, null, false, "out-of-range")
        }

        val repeated = observedMtu == state.lastObserved
        val tracked = state.copy(
            lastObserved = observedMtu,
            repeats = if (repeated) (state.repeats + 1).coerceAtMost(64) else 1
        )

        if (!repeated || tracked.repeats < CONFIRMATIONS) {
            return Decision(tracked, null, false, if (repeated) "awaiting-confirmation" else "first-observation")
        }

        val alreadyCommitted = tracked.committed == observedMtu
        val commitAllowed = nowMs - tracked.committedAtMs >= MIN_COMMIT_INTERVAL_MS || tracked.committed == 0
        val commitMtu = if (!alreadyCommitted && commitAllowed) observedMtu else null
        val committed = if (commitMtu != null) {
            tracked.copy(committed = observedMtu, committedAtMs = nowMs)
        } else {
            tracked
        }

        val drop = if (activeMtu in MIN_MTU..MAX_MTU) activeMtu - observedMtu else 0
        val throttlingSynchronized = drop >= MATERIAL_DROP_BYTES &&
            (throttleActive || sawtoothConfidence >= ProtocolFingerprintAwareVerifier.SAWTOOTH_HIGH_CONFIDENCE)
        val requestTune = drop >= MATERIAL_DROP_BYTES && commitMtu != null
        val reason = when {
            throttlingSynchronized -> "throttle-synchronized-drop-$drop"
            requestTune -> "material-drop-$drop"
            commitMtu != null -> "committed-$observedMtu"
            alreadyCommitted -> "unchanged"
            else -> "commit-rate-limited"
        }
        return Decision(committed, commitMtu, requestTune, reason, throttlingSynchronized)
    }

    fun isStable(previous: Int, observed: Int): Boolean =
        previous in MIN_MTU..MAX_MTU &&
            observed in MIN_MTU..MAX_MTU &&
            abs(previous - observed) < MATERIAL_DROP_BYTES
}
