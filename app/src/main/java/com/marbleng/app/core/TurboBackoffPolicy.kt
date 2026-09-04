package com.marbleng.app.core

import kotlin.math.max

/**
 * MARBLE_TURBO_BACKOFF_V133
 *
 * Why the acceleration engine went quiet for half an hour at a time.
 *
 * The previous implementation kept two variables in the service: an inconclusive streak and a
 * `tuningBackoffUntilMs` timestamp. Three properties of that design produced the
 * `TURBO live-inconclusive-backoff` storms in the runtime log:
 *
 *  1. **Every** failed pass escalated the streak, including passes that failed because the probes
 *     themselves could not run — which is what a DNS deadline window looks like from the tuner's
 *     seat. A broken resolver therefore escalated the *transport* backoff to its 1800 s ceiling.
 *  2. Nothing ever decayed the streak. One bad window at 17:56 kept the engine suppressed for the
 *     rest of the session even after the link recovered, because the streak was only reset by a
 *     successful pass — and a successful pass cannot happen while the backoff is blocking passes.
 *     That is a self-locking loop, not a backoff.
 *  3. Nothing could release the timer early. `jitter-control-exit` proved the route had recovered
 *     and the backoff stayed armed anyway.
 *
 * This policy separates the three concerns and makes each one explicit:
 *
 *  - **cause**: only real transport evidence escalates. Probe infrastructure being unavailable
 *    gets a short, non-escalating retry window.
 *  - **decay**: the streak halves for every full half-life that passed since the last outcome, so
 *    an escalation can never outlive the conditions that caused it.
 *  - **release**: a demonstrably recovered route cancels the timer immediately — bounded to a
 *    small number of early releases so a flapping link cannot ping-pong the engine.
 */
object TurboBackoffPolicy {

    /** Why the last acceleration pass produced no usable verdict. */
    enum class Cause {
        /** The pass ran and the transport evidence was genuinely inconclusive. Escalates. */
        TRANSPORT_INCONCLUSIVE,

        /**
         * The pass could not measure anything (probes unavailable, resolver dead, no samples).
         * Not evidence about the transport, so it must not escalate the transport backoff.
         */
        PROBE_UNAVAILABLE
    }

    /**
     * Immutable backoff state. Held by the service across ticks; every transition returns a new
     * value so the state machine stays testable without hidden mutation.
     */
    data class State(
        /** Consecutive *escalating* inconclusive outcomes, after time decay. */
        val streak: Int = 0,
        /** Absolute wall-clock millisecond until which tuning is suppressed. 0 = not suppressed. */
        val untilMs: Long = 0L,
        /** When the last inconclusive outcome was recorded; drives decay. */
        val lastOutcomeAtMs: Long = 0L,
        /** How many times the timer was cancelled early by recovered route evidence. */
        val earlyReleases: Int = 0
    ) {
        val idle: Boolean
            get() = streak == 0 && untilMs == 0L
    }

    data class Outcome(
        val state: State,
        val backoffMs: Long,
        val escalated: Boolean,
        val reason: String
    )

    /** Streak above which the backoff stops growing. */
    const val MAX_STREAK = 3

    /** A streak halves for every full half-life without a new inconclusive outcome. */
    const val STREAK_HALF_LIFE_MS = 600_000L

    /**
     * Non-escalating retry window when the probes themselves were unavailable. Long enough to skip
     * a filtering window, short enough that a recovered resolver is retried promptly.
     */
    const val PROBE_UNAVAILABLE_RETRY_MS = 180_000L

    /** After this many early releases the engine trusts the full timer again. */
    const val MAX_EARLY_RELEASES = 2

    /** Streak after [elapsedMs] of quiet, i.e. escalation that no longer reflects the link. */
    fun decayedStreak(streak: Int, elapsedMs: Long, halfLifeMs: Long = STREAK_HALF_LIFE_MS): Int {
        if (streak <= 0 || elapsedMs <= 0L) return max(0, streak)
        val life = halfLifeMs.coerceAtLeast(1L)
        val halvings = (elapsedMs / life).toInt()
        if (halvings <= 0) return streak
        if (halvings >= 31) return 0
        return streak shr halvings
    }

    fun isWaiting(state: State, nowMs: Long): Boolean =
        state.untilMs > 0L && nowMs < state.untilMs

    /**
     * Record an inconclusive acceleration pass.
     *
     * The streak is decayed *before* the new outcome is folded in, so the escalation reflects the
     * recent link and not a window from half an hour ago.
     */
    fun inconclusive(
        state: State,
        nowMs: Long,
        cause: Cause,
        baseMs: Long,
        maxMs: Long
    ): Outcome {
        require(baseMs > 0L) { "baseMs must be positive" }
        require(maxMs >= baseMs) { "maxMs must not be below baseMs" }

        if (cause == Cause.PROBE_UNAVAILABLE) {
            val backoff = PROBE_UNAVAILABLE_RETRY_MS.coerceAtMost(maxMs)
            return Outcome(
                state = state.copy(untilMs = nowMs + backoff),
                backoffMs = backoff,
                escalated = false,
                reason = "probe-unavailable-no-escalation"
            )
        }

        val quietMs = if (state.lastOutcomeAtMs > 0L) nowMs - state.lastOutcomeAtMs else 0L
        val streak = decayedStreak(state.streak, quietMs).coerceAtMost(MAX_STREAK - 1) + 1
        val multiplier = 1L shl (streak - 1).coerceIn(0, MAX_STREAK - 1)
        val backoff = (baseMs * multiplier).coerceAtMost(maxMs)
        return Outcome(
            state = state.copy(
                streak = streak,
                untilMs = nowMs + backoff,
                lastOutcomeAtMs = nowMs
            ),
            backoffMs = backoff,
            escalated = streak > 1,
            reason = "transport-inconclusive-streak-$streak"
        )
    }

    /** A conclusive, healthy pass clears both the streak and the timer. */
    fun succeeded(state: State): State =
        State(streak = 0, untilMs = 0L, lastOutcomeAtMs = state.lastOutcomeAtMs, earlyReleases = 0)

    /**
     * Fold one observation of the live route into the state.
     *
     * [recovered] must mean "the route is measurably good again" (jitter control released and the
     * verified latency back under the tuning trigger). When it holds, an armed timer is cancelled
     * early and one step of escalation is forgiven — bounded by [MAX_EARLY_RELEASES] so a link that
     * flaps cannot keep re-arming the engine forever.
     */
    fun observeRoute(
        state: State,
        nowMs: Long,
        recovered: Boolean
    ): State {
        if (state.idle) return state
        if (!recovered) return state
        if (!isWaiting(state, nowMs)) return state
        if (state.earlyReleases >= MAX_EARLY_RELEASES) return state
        return state.copy(
            streak = max(0, state.streak - 1),
            untilMs = 0L,
            earlyReleases = state.earlyReleases + 1
        )
    }
}
