package com.marbleng.app.core

import kotlin.math.abs

/**
 * MARBLE_PATH_MTU_STABILITY_V133
 *
 * Why the userspace tunnel MTU alternated between 1360 and 1400 between sessions.
 *
 * The monitor loop read the core's `TCP_INFO` PMTU on every second tick and immediately did two
 * things with it:
 *
 * ```
 * repo.intelligence.rememberPathMtu(activeProfileId, transport.pmtu)
 * if (activeMtu > transport.pmtu) tuningRequested.set(true)
 * ```
 *
 * Both are single-sample decisions on a signal that is *per socket*, not per path: `pmtu` is the
 * MSS of whichever flow the telemetry happened to sample, so it legitimately reports 1400 on one
 * tick and 1360 on the next as different sockets rotate through. The consequences were exactly the
 * two symptoms in the report:
 *
 *  - the last value written won, and it was pinned for the whole TTL. A single low sample therefore
 *    resized every *subsequent* session, which is why MTU differed between sessions of the same
 *    route rather than converging.
 *  - `activeMtu > transport.pmtu` was true on every rotation, so `tuningRequested` was set over and
 *    over. That flag makes the next acceleration pass *forced*, bypassing the sample, thermal,
 *    heavy-traffic and interval guards — so a PMTU reading that meant nothing spent link capacity on
 *    a full transport re-measurement, and when that pass was inconclusive it escalated the Turbo
 *    backoff. One unstable counter was feeding the other failure mode.
 *
 * This policy makes PMTU learning evidence-based:
 *
 *  - a value is only committed after it has been observed [CONFIRMATIONS] times in a row, so a
 *    rotating socket can never resize the tunnel;
 *  - a commit is rate-limited, so the learned MTU cannot chase the signal tick by tick;
 *  - a re-measurement is only requested when the drop is *material* (at least one full MSS step
 *    below the running MTU) and corroborated, which is the only case where the userspace tunnel is
 *    genuinely oversized.
 */
object PathMtuPolicy {

    data class State(
        /** Last PMTU value observed from the core telemetry. */
        val lastObserved: Int = 0,
        /** How many consecutive ticks reported [lastObserved]. */
        val repeats: Int = 0,
        /** The value actually committed to the learned store. */
        val committed: Int = 0,
        val committedAtMs: Long = 0L
    )

    data class Decision(
        val state: State,
        /** Non-null when the learned PMTU must be written; null means "keep what is stored". */
        val commitMtu: Int?,
        /** True when a re-measurement of the transport is justified by this observation. */
        val requestTune: Boolean,
        val reason: String
    )

    /** Consecutive identical observations required before a value is trusted. */
    const val CONFIRMATIONS = 2

    /**
     * A drop smaller than this is socket rotation, not a path change. One MSS step (40 bytes) is the
     * smallest difference that changes how a packet is segmented on the wire.
     */
    const val MATERIAL_DROP_BYTES = 40

    /** Minimum interval between two learned-MTU commits. */
    const val MIN_COMMIT_INTERVAL_MS = 45_000L

    /** Plausible PMTU range; anything outside is a telemetry artefact and is ignored. */
    const val MIN_MTU = 1280
    const val MAX_MTU = 9000

    /**
     * Fold one telemetry PMTU reading into the learning state.
     *
     * @param observedMtu `TCP_INFO` PMTU of the sampled socket, 0 when the field is unavailable.
     * @param activeMtu the MTU the userspace tunnel is currently running with.
     */
    fun observe(
        state: State,
        observedMtu: Int,
        activeMtu: Int,
        nowMs: Long
    ): Decision {
        if (observedMtu !in MIN_MTU..MAX_MTU) {
            return Decision(state, null, false, "out-of-range")
        }

        val repeated = observedMtu == state.lastObserved
        val tracked = state.copy(
            lastObserved = observedMtu,
            repeats = if (repeated) (state.repeats + 1).coerceAtMost(64) else 1
        )

        // A single differing sample is socket rotation. Never commit and never re-measure on it.
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

        // Only a corroborated, material drop below the running MTU justifies spending link capacity
        // on a transport re-measurement.
        val drop = if (activeMtu in MIN_MTU..MAX_MTU) activeMtu - observedMtu else 0
        val requestTune = drop >= MATERIAL_DROP_BYTES && commitMtu != null
        val reason = when {
            requestTune -> "material-drop-$drop"
            commitMtu != null -> "committed-$observedMtu"
            alreadyCommitted -> "unchanged"
            else -> "commit-rate-limited"
        }
        return Decision(committed, commitMtu, requestTune, reason)
    }

    /**
     * True when two observations are the same MTU within the rotation tolerance, i.e. the signal is
     * stable rather than alternating.
     */
    fun isStable(previous: Int, observed: Int): Boolean =
        previous in MIN_MTU..MAX_MTU &&
            observed in MIN_MTU..MAX_MTU &&
            abs(previous - observed) < MATERIAL_DROP_BYTES
}
