package com.marbleng.app.core

object JitterControlPolicy {
    enum class Verdict { ENTER, EXIT, HOLD, SUSPECTED_THROTTLE, THROTTLE_CLEARED }

    data class Sample(
        val samples: Int,
        val jitterReady: Boolean,
        val jitterMs: Double,
        val triggerMs: Double,
        val releaseMs: Double,
        val p95IpdvMs: Double,
        val lossPercent: Double,
        val spikePercent: Double,
        val sawtoothConfidence: Double = 0.0
    )

    data class State(
        val active: Boolean = false,
        val highStreak: Int = 0,
        val lowStreak: Int = 0,
        val enteredAtMs: Long = 0L,
        val exitedAtMs: Long = 0L,
        val throttleActive: Boolean = false,
        val throttleStreak: Int = 0,
        val throttleClearStreak: Int = 0,
        val throttleEnteredAtMs: Long = 0L
    )

    data class Decision(
        val state: State,
        val verdict: Verdict,
        val tick: String,
        val highStreak: Int,
        val lowStreak: Int
    )

    const val HIGH_CONFIRMATIONS = 3

    const val RELEASE_CONFIRMATIONS = 4

    const val MIN_HOLD_MS = 30_000L

    const val MIN_DWELL_MS = 45_000L

    const val MAX_STREAK = 1_000

    const val MIN_SAMPLES = 3

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

    fun evaluate(
        sample: Sample,
        state: State,
        nowMs: Long
    ): Decision {
        val trigger = sample.triggerMs.coerceAtLeast(1.0)
        val release = sample.releaseMs.coerceAtLeast(0.0)

        if (sample.samples < MIN_SAMPLES || !sample.jitterReady) {
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

            else -> state.copy(
                highStreak = (state.highStreak - 1).coerceAtLeast(0),
                lowStreak = (state.lowStreak - 1).coerceAtLeast(0)
            )
        }
        val tick = if (high) "high" else if (low) "low" else "mixed"

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
            if (nowMs - next.enteredAtMs < MIN_HOLD_MS) {
                return Decision(next, Verdict.HOLD, "hold", next.highStreak, next.lowStreak)
            }
            val exited = next.copy(active = false, highStreak = 0, lowStreak = 0, exitedAtMs = nowMs)
            return Decision(exited, Verdict.EXIT, tick, exited.highStreak, exited.lowStreak)
        }

        return Decision(throttleNext, Verdict.HOLD, tick, throttleNext.highStreak, throttleNext.lowStreak)
    }
}
