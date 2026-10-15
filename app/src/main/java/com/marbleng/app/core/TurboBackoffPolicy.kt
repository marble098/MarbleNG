package com.marbleng.app.core

import kotlin.math.max

object TurboBackoffPolicy {
    enum class Cause {
        TRANSPORT_INCONCLUSIVE,

        PROBE_UNAVAILABLE,

        NOT_ATTEMPTED
    }

    data class State(
        val streak: Int = 0,
        val untilMs: Long = 0L,
        val lastOutcomeAtMs: Long = 0L,
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

    const val MAX_STREAK = 3

    const val STREAK_HALF_LIFE_MS = 600_000L

    const val PROBE_UNAVAILABLE_RETRY_MS = 180_000L

    const val NOT_ATTEMPTED_RETRY_MS = 90_000L

    const val MAX_EARLY_RELEASES = 2

    const val NATIONAL_EVENT_RETRY_MS = 300_000L

    private fun quietMsFor(state: State, nowMs: Long): Long {
        if (state.streak <= 0 || nowMs < state.lastOutcomeAtMs) return 0L
        return nowMs - state.lastOutcomeAtMs
    }

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

    fun inconclusive(
        state: State,
        nowMs: Long,
        cause: Cause,
        baseMs: Long,
        maxMs: Long,
        nationalFilteringEvent: Boolean = false
    ): Outcome {
        require(baseMs > 0L) { "baseMs must be positive" }
        require(maxMs >= baseMs) { "maxMs must not be below baseMs" }

        if (nationalFilteringEvent) {
            val backoff = NATIONAL_EVENT_RETRY_MS.coerceAtMost(maxMs)
            return Outcome(
                state = state.copy(
                    streak = decayedStreak(state.streak, quietMsFor(state, nowMs)),
                    untilMs = nowMs + backoff
                ),
                backoffMs = backoff,
                escalated = false,
                reason = "national-filtering-event-no-escalation"
            )
        }

        if (cause == Cause.PROBE_UNAVAILABLE) {
            val backoff = PROBE_UNAVAILABLE_RETRY_MS.coerceAtMost(maxMs)
            return Outcome(
                state = state.copy(untilMs = nowMs + backoff),
                backoffMs = backoff,
                escalated = false,
                reason = "probe-unavailable-no-escalation"
            )
        }

        if (cause == Cause.NOT_ATTEMPTED) {
            val backoff = NOT_ATTEMPTED_RETRY_MS.coerceAtMost(maxMs)
            return Outcome(
                state = state.copy(untilMs = nowMs + backoff),
                backoffMs = backoff,
                escalated = false,
                reason = "pass-not-attempted-no-escalation"
            )
        }

        val quietMs = if (state.streak > 0 && nowMs >= state.lastOutcomeAtMs) {
            nowMs - state.lastOutcomeAtMs
        } else {
            0L
        }
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

    fun succeeded(state: State): State =
        State(streak = 0, untilMs = 0L, lastOutcomeAtMs = state.lastOutcomeAtMs, earlyReleases = 0)

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
