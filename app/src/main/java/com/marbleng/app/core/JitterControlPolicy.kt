package com.marbleng.app.core

/**
 * MARBLE_JITTER_HYSTERESIS_V133
 *
 * Why `ROUTE jitter-control-enter` and `jitter-control-exit` alternated in the runtime log.
 *
 * The previous state machine lived inline in the service and had one asymmetry that made it
 * oscillate on any noisy cellular link:
 *
 * ```
 * instantHigh -> highStreak++, lowStreak = 0
 * instantLow  -> lowStreak++,  highStreak--
 * else        -> lowStreak = 0, highStreak--   // <-- the bug
 * ```
 *
 * The `else` branch is entered by every *ambiguous* tick: not enough samples yet, a mixed reading,
 * a probe that produced neither a clean high nor a clean low. Wiping `lowStreak` to zero on an
 * ambiguous tick means the release counter has to reach four *consecutive* unambiguous good ticks
 * to ever release, while entry only needs three highs. On a link whose jitter swings between 50 and
 * 150 ms, clean runs of four are rare — so jitter control latched on, the probe cadence dropped to
 * the degraded 8-tick interval, `degraded=true` was forced into the tuning gate, and the engine
 * spent the session entering and exiting instead of measuring.
 *
 * This policy fixes the three real defects:
 *
 *  1. an ambiguous tick is a **hold**: it changes no counter. Absence of evidence is not evidence
 *     of stability, and it is not evidence of instability either.
 *  2. a tick of the opposite kind decrements the other streak by one instead of wiping it, so a
 *     single contradictory sample cannot erase an accumulated trend.
 *  3. both transitions get a dwell time. Entering again right after an exit (or exiting right after
 *     an entry) is what the user sees as flapping; a bounded dwell makes each state meaningful.
 */
object JitterControlPolicy {

    enum class Verdict { ENTER, EXIT, HOLD, SUSPECTED_THROTTLE, THROTTLE_CLEARED }

    /** One route-quality tick, already reduced to the numbers the state machine needs. */
    data class Sample(
        /** Verified samples currently in the outcome window. */
        val samples: Int,
        /** True when at least two samples exist, i.e. a jitter value is meaningful. */
        val jitterReady: Boolean,
        val jitterMs: Double,
        /** Enter threshold: EWMA jitter at or above this is degraded. */
        val triggerMs: Double,
        /** Release threshold: EWMA jitter at or below this is healthy. */
        val releaseMs: Double,
        val p95IpdvMs: Double,
        val lossPercent: Double,
        val spikePercent: Double,
        /**
         * MARBLE_IRAN_AWARE_PING_L2 — 0..1 confidence that the *shape* of the transfer is
         * deliberate throttling ([SawtoothDetector]). Only a jitter signal plus an independent
         * sawtooth signal may escalate to [Verdict.SUSPECTED_THROTTLE] (cross-validation rule).
         */
        val sawtoothConfidence: Double = 0.0
    )

    data class State(
        val active: Boolean = false,
        val highStreak: Int = 0,
        val lowStreak: Int = 0,
        /** When jitter control was last entered; 0 = never. */
        val enteredAtMs: Long = 0L,
        /** When jitter control was last released; 0 = never. */
        val exitedAtMs: Long = 0L,
        /** MARBLE_IRAN_AWARE_PING_L2 — is the stronger throttle state active? */
        val throttleActive: Boolean = false,
        /** Consecutive ticks that satisfy jitter+sawtooth together. */
        val throttleStreak: Int = 0,
        /** Consecutive ticks that satisfy neither signal; releases throttle state. */
        val throttleClearStreak: Int = 0,
        /** When throttle state was last entered; 0 = never. */
        val throttleEnteredAtMs: Long = 0L
    )

    data class Decision(
        val state: State,
        val verdict: Verdict,
        /** `high`, `low`, `mixed`, `insufficient`, `dwell`, `throttle` … recorded in diagnostics. */
        val tick: String,
        val highStreak: Int,
        val lowStreak: Int
    )

    /** Consecutive degraded ticks required to enter. */
    const val HIGH_CONFIRMATIONS = 3

    /** Consecutive healthy ticks required to release. */
    const val RELEASE_CONFIRMATIONS = 4

    /** Jitter control must hold at least this long before it may release. */
    const val MIN_HOLD_MS = 30_000L

    /** After a release, at least this long must pass before it may enter again. */
    const val MIN_DWELL_MS = 45_000L

    /** Counters are capped: a long session must not overflow them. */
    const val MAX_STREAK = 1_000

    /** Minimum verified samples before any verdict is possible. */
    const val MIN_SAMPLES = 3

    /** MARBLE_IRAN_AWARE_PING_L2 — throttle confirmation constants. */
    const val THROTTLE_CONFIRMATIONS = 2
    const val THROTTLE_CLEAR_CONFIRMATIONS = 3
    const val THROTTLE_MIN_HOLD_MS = 60_000L
    const val THROTTLE_SAWTOOTH_ENTER = ProtocolFingerprintAwareVerifier.SAWTOOTH_HIGH_CONFIDENCE
    const val THROTTLE_SAWTOOTH_RELEASE = 0.30
    const val THROTTLE_JITTER_FOLD = 2.0

    private const val TAIL_IPDV_ENTER_MS = 35.0
    private const val TAIL_IPDV_RELEASE_MS = 20.0
    private const val LOSS_ENTER_PERCENT = 15.0
    private const val LOSS_RELEASE_PERCENT = 5.0
    private const val SPIKE_ENTER_PERCENT = 25.0
    private const val SPIKE_RELEASE_PERCENT = 10.0

    /**
     * Fold one tick into the state machine.
     *
     * @param nowMs wall clock; only used for the dwell/hold windows.
     */
    fun evaluate(
        sample: Sample,
        state: State,
        nowMs: Long
    ): Decision {
        val trigger = sample.triggerMs.coerceAtLeast(1.0)
        val release = sample.releaseMs.coerceAtLeast(0.0)

        if (sample.samples < MIN_SAMPLES || !sample.jitterReady) {
            // No verdict is possible. Hold both counters exactly where they are.
            return Decision(state, Verdict.HOLD, "insufficient", state.highStreak, state.lowStreak)
        }

        val tailEnter = maxOf(trigger * 2.0, TAIL_IPDV_ENTER_MS)
        val tailRelease = maxOf(release * 2.0, TAIL_IPDV_RELEASE_MS)
        val high = sample.jitterMs >= trigger ||
            sample.p95IpdvMs >= tailEnter ||
            sample.lossPercent >= LOSS_ENTER_PERCENT ||
            sample.spikePercent >= SPIKE_ENTER_PERCENT
        val low = sample.jitterMs <= release &&
            sample.p95IpdvMs <= tailRelease &&
            sample.lossPercent <= LOSS_RELEASE_PERCENT &&
            sample.spikePercent <= SPIKE_RELEASE_PERCENT

        val next = when {
            high -> state.copy(
                highStreak = (state.highStreak + 1).coerceAtMost(MAX_STREAK),
                lowStreak = (state.lowStreak - 1).coerceAtLeast(0)
            )
            low -> state.copy(
                lowStreak = (state.lowStreak + 1).coerceAtMost(MAX_STREAK),
                highStreak = (state.highStreak - 1).coerceAtLeast(0)
            )
            // Mixed evidence: step the dominant trend down by one, never wipe it.
            else -> state.copy(
                highStreak = (state.highStreak - 1).coerceAtLeast(0),
                lowStreak = (state.lowStreak - 1).coerceAtLeast(0)
            )
        }
        val tick = if (high) "high" else if (low) "low" else "mixed"

        // MARBLE_IRAN_AWARE_PING_L2 — suspected throttle: the cross-validation rule in action.
        // Jitter alone is a *weak* signal (congestion also jitters). Sawtooth alone is a weak
        // signal (a slow server also drains). The throttle verdict is only reached when BOTH are
        // present in the same tick — one signal folds the other's confidence in.
        val sawtoothHigh = sample.sawtoothConfidence >= THROTTLE_SAWTOOTH_ENTER
        val sawtoothLow = sample.sawtoothConfidence <= THROTTLE_SAWTOOTH_RELEASE
        val jitterFold = sample.jitterMs >= trigger * THROTTLE_JITTER_FOLD
        val throttleEvidence = sawtoothHigh && (jitterFold || high)
        val throttleClean = sawtoothLow && low

        val throttleNext = when {
            throttleEvidence -> next.copy(
                throttleStreak = (next.throttleStreak + 1).coerceAtMost(MAX_STREAK),
                throttleClearStreak = (next.throttleClearStreak - 1).coerceAtLeast(0)
            )
            throttleClean -> next.copy(
                throttleClearStreak = (next.throttleClearStreak + 1).coerceAtMost(MAX_STREAK),
                throttleStreak = (next.throttleStreak - 1).coerceAtLeast(0)
            )
            else -> next.copy(
                throttleStreak = (next.throttleStreak - 1).coerceAtLeast(0),
                throttleClearStreak = (next.throttleClearStreak - 1).coerceAtLeast(0)
            )
        }

        if (!throttleNext.throttleActive &&
            throttleNext.throttleStreak >= THROTTLE_CONFIRMATIONS
        ) {
            val entered = throttleNext.copy(
                throttleActive = true,
                throttleStreak = 0,
                throttleClearStreak = 0,
                throttleEnteredAtMs = nowMs
            )
            return Decision(
                entered,
                Verdict.SUSPECTED_THROTTLE,
                "throttle",
                entered.highStreak,
                entered.lowStreak
            )
        }

        if (throttleNext.throttleActive &&
            throttleNext.throttleClearStreak >= THROTTLE_CLEAR_CONFIRMATIONS &&
            nowMs - throttleNext.throttleEnteredAtMs >= THROTTLE_MIN_HOLD_MS
        ) {
            val cleared = throttleNext.copy(
                throttleActive = false,
                throttleStreak = 0,
                throttleClearStreak = 0
            )
            return Decision(
                cleared,
                Verdict.THROTTLE_CLEARED,
                "throttle-cleared",
                cleared.highStreak,
                cleared.lowStreak
            )
        }

        if (!next.active && next.highStreak >= HIGH_CONFIRMATIONS) {
            if (next.exitedAtMs > 0L && nowMs - next.exitedAtMs < MIN_DWELL_MS) {
                return Decision(next, Verdict.HOLD, "dwell", next.highStreak, next.lowStreak)
            }
            val entered = next.copy(active = true, highStreak = 0, lowStreak = 0, enteredAtMs = nowMs)
            return Decision(entered, Verdict.ENTER, tick, entered.highStreak, entered.lowStreak)
        }

        if (next.active && next.lowStreak >= RELEASE_CONFIRMATIONS) {
            // No sentinel test here: being active already proves the state was entered, so the hold
            // window applies unconditionally. Gating it on a non-zero timestamp silently skipped the
            // minimum hold whenever the entry timestamp happened to equal the "never" value.
            if (nowMs - next.enteredAtMs < MIN_HOLD_MS) {
                return Decision(next, Verdict.HOLD, "hold", next.highStreak, next.lowStreak)
            }
            val exited = next.copy(active = false, highStreak = 0, lowStreak = 0, exitedAtMs = nowMs)
            return Decision(exited, Verdict.EXIT, tick, exited.highStreak, exited.lowStreak)
        }

        return Decision(throttleNext, Verdict.HOLD, tick, throttleNext.highStreak, throttleNext.lowStreak)
    }
}
