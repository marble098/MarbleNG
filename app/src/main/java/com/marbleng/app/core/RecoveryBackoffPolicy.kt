package com.marbleng.app.core

/**
 * MARBLE_RECOVERY_CIRCUIT_V135
 *
 * Why six automatic connect/disconnect cycles fired in under two minutes.
 *
 * The runtime log that motivated this policy showed the failure loop in its pure form: the tunnel
 * came up, the encrypted DNS path stalled on endpoints the underlay could not reach, the datapath
 * delivered nothing, a watchdog declared the route failed — and Smart Fallback immediately
 * re-dialled the next candidate with **zero delay**. Every candidate failed for the same
 * structural reason, so the recovery machinery became the workload: six full HEV/Xray lifecycles
 * in 113 seconds, each one paying a core start, a TLS bring-up and a teardown, heating the CPU,
 * allocating fresh native session buffers (the PSS climbed to ~318 MB) and re-arming the very
 * watchdog that would kill it again.
 *
 * Two defects made the loop possible:
 *
 *  1. **No pacing.** A failed fallback advanced to the next candidate instantly. Retry pacing
 *     existed only on the Identity Guard same-route path (750 ms × attempt, capped at 3 s); the
 *     Smart Fallback path had none at all.
 *  2. **No circuit.** Nothing counted failures inside a time window, so a failure cause that
 *     affects every candidate identically (a dead DNS path, a filtered underlay, a memory-pressed
 *     OS) could burn candidates forever.
 *
 * The fix is deliberately a pure, side-effect-free policy in the same shape as
 * [JitterControlPolicy] and [TurboBackoffPolicy]: the service owns the state object and the wall
 * clock, the policy owns the rules, and both are unit-testable without Android.
 *
 *  - [delayMs] is exponential: 1.5 s, 3 s, 6 s, 12 s, 24 s, 30 s. The first retry stays fast
 *    enough to hide a single transient core death; the ladder makes a structural fault cheap.
 *  - [circuitOpen] counts attempts inside a rolling five-minute window. Six automatic recoveries
 *    in that window open the circuit: recovery holds fail-closed (Full TUN keeps blocking traffic
 *    as a kill switch) and the user is asked to retry deliberately. A manual connect or a stable
 *    route resets the ladder, so the circuit can never lock the product.
 */
object RecoveryBackoffPolicy {

    /** First automatic retry waits this long. */
    const val BASE_DELAY_MS = 1_500L

    /** No automatic retry ever waits longer than this. */
    const val MAX_DELAY_MS = 30_000L

    /** Doubling stops here; the ceiling then keeps every later step identical. */
    const val MAX_DOUBLINGS = 5

    /** Rolling window in which automatic recoveries are counted. */
    const val WINDOW_MS = 300_000L

    /** This many automatic recoveries inside [WINDOW_MS] open the circuit. */
    const val MAX_RECOVERIES_PER_WINDOW = 6

    /** The attempt ledger never grows beyond this; the window bound is what matters. */
    const val MAX_TRACKED_ATTEMPTS = 16

    /** Timestamps of automatic recovery, newest last. */
    data class State(val attempts: List<Long> = emptyList())

    /**
     * Exponential backoff for the `attempt`-th automatic recovery (1-based).
     *
     * Attempt 1 → [BASE_DELAY_MS]; each further attempt doubles the wait up to [MAX_DELAY_MS].
     * Non-positive input is treated as a first attempt, so a caller that lost its count still gets
     * a sane delay instead of an immediate retry.
     */
    fun delayMs(attempt: Int): Long {
        val doublings = (attempt - 1).coerceIn(0, MAX_DOUBLINGS)
        var delay = BASE_DELAY_MS
        repeat(doublings) { delay *= 2L }
        return delay.coerceAtMost(MAX_DELAY_MS)
    }

    /** Record one automatic recovery attempt at [nowMs]. */
    fun recordAttempt(state: State, nowMs: Long): State =
        State(attempts = (state.attempts + nowMs).takeLast(MAX_TRACKED_ATTEMPTS))

    /** Automatic recovery attempts that fall inside the rolling window ending at [nowMs]. */
    fun recentAttempts(state: State, nowMs: Long): Int =
        state.attempts.count { nowMs - it in 0..WINDOW_MS }

    /**
     * The circuit opens when the window already holds [MAX_RECOVERIES_PER_WINDOW] attempts: the
     * next failure must stop re-dialling and hand the decision back to the user.
     */
    fun circuitOpen(state: State, nowMs: Long): Boolean =
        recentAttempts(state, nowMs) >= MAX_RECOVERIES_PER_WINDOW

    /**
     * The attempt counter for the *next* recovery: how many live in the window, plus the one
     * about to be scheduled. Feeds [delayMs].
     */
    fun nextAttemptIndex(state: State, nowMs: Long): Int = recentAttempts(state, nowMs) + 1

    /** A stable route or a fresh user-initiated connect clears the ladder entirely. */
    fun reset(): State = State()
}
