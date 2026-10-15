package com.marbleng.app.core

object RecoveryBackoffPolicy {
    const val BASE_DELAY_MS = 1_500L

    const val MAX_DELAY_MS = 30_000L

    const val MAX_DOUBLINGS = 5

    const val WINDOW_MS = 300_000L

    const val MAX_RECOVERIES_PER_WINDOW = 6

    const val MAX_TRACKED_ATTEMPTS = 16

    data class State(val attempts: List<Long> = emptyList())

    fun delayMs(attempt: Int): Long {
        val doublings = (attempt - 1).coerceIn(0, MAX_DOUBLINGS)
        var delay = BASE_DELAY_MS
        repeat(doublings) { delay *= 2L }
        return delay.coerceAtMost(MAX_DELAY_MS)
    }

    fun recordAttempt(state: State, nowMs: Long): State =
        State(attempts = (state.attempts + nowMs).takeLast(MAX_TRACKED_ATTEMPTS))

    fun recentAttempts(state: State, nowMs: Long): Int =
        state.attempts.count { nowMs - it in 0..WINDOW_MS }

    fun circuitOpen(state: State, nowMs: Long): Boolean =
        recentAttempts(state, nowMs) >= MAX_RECOVERIES_PER_WINDOW

    fun nextAttemptIndex(state: State, nowMs: Long): Int = recentAttempts(state, nowMs) + 1

    fun reset(): State = State()
}
