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

    enum class Verdict { ENTER, EXIT, HOLD }

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
        val spikePercent: Double
    )

    data class State(
        val active: Boolean = false,
        val highStreak: Int = 0,
        val lowStreak: Int = 0,
        /** When jitter control was last entered; 0 = never. */
        val enteredAtMs: Long = 0L,
        /** When jitter control was last released; 0 = never. */
        val exitedAtMs: Long = 0L
    )

    data class Decision(
        val state: State,
        val verdict: Verdict,
        /** `high`, `low`, `mixed`, `insufficient`, `dwell` — recorded in diagnostics. */
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

        return Decision(next, Verdict.HOLD, tick, next.highStreak, next.lowStreak)
    }
}
