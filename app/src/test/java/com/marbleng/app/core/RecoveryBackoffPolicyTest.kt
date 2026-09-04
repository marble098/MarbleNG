package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_RECOVERY_CIRCUIT_V135 regressions.
 *
 * The motivating log showed six automatic connect/disconnect cycles inside two minutes. These
 * tests pin the two properties that prevent that storm: every automatic recovery is paced by an
 * exponential ladder, and the circuit opens once too many recoveries land in one window.
 */
class RecoveryBackoffPolicyTest {

    @Test
    fun `the delay ladder is exponential and capped`() {
        assertEquals(1_500L, RecoveryBackoffPolicy.delayMs(1))
        assertEquals(3_000L, RecoveryBackoffPolicy.delayMs(2))
        assertEquals(6_000L, RecoveryBackoffPolicy.delayMs(3))
        assertEquals(12_000L, RecoveryBackoffPolicy.delayMs(4))
        assertEquals(24_000L, RecoveryBackoffPolicy.delayMs(5))
        assertEquals(30_000L, RecoveryBackoffPolicy.delayMs(6))
        // The ceiling holds forever after; the ladder never grows unbounded.
        assertEquals(30_000L, RecoveryBackoffPolicy.delayMs(7))
        assertEquals(30_000L, RecoveryBackoffPolicy.delayMs(1_000))
    }

    @Test
    fun `a non-positive attempt is treated as a first attempt`() {
        assertEquals(RecoveryBackoffPolicy.BASE_DELAY_MS, RecoveryBackoffPolicy.delayMs(0))
        assertEquals(RecoveryBackoffPolicy.BASE_DELAY_MS, RecoveryBackoffPolicy.delayMs(-3))
    }

    @Test
    fun `attempts outside the window do not count`() {
        val now = 1_000_000L
        var state = RecoveryBackoffPolicy.State()
        // Six attempts, but all of them older than the window.
        repeat(RecoveryBackoffPolicy.MAX_RECOVERIES_PER_WINDOW) { index ->
            state = RecoveryBackoffPolicy.recordAttempt(
                state,
                now - RecoveryBackoffPolicy.WINDOW_MS - 1_000L - index
            )
        }
        assertEquals(0, RecoveryBackoffPolicy.recentAttempts(state, now))
        assertFalse(RecoveryBackoffPolicy.circuitOpen(state, now))
        assertEquals(1, RecoveryBackoffPolicy.nextAttemptIndex(state, now))
        assertEquals(RecoveryBackoffPolicy.BASE_DELAY_MS, RecoveryBackoffPolicy.delayMs(
            RecoveryBackoffPolicy.nextAttemptIndex(state, now) - 1
        ))
    }

    @Test
    fun `the circuit opens at the configured count inside the window`() {
        val now = 5_000_000L
        var state = RecoveryBackoffPolicy.State()
        // One fewer than the limit keeps the circuit closed.
        repeat(RecoveryBackoffPolicy.MAX_RECOVERIES_PER_WINDOW - 1) {
            state = RecoveryBackoffPolicy.recordAttempt(state, now - it * 1_000L)
        }
        assertFalse(RecoveryBackoffPolicy.circuitOpen(state, now))
        // The attempt that reaches the limit opens it.
        state = RecoveryBackoffPolicy.recordAttempt(state, now)
        assertTrue(RecoveryBackoffPolicy.circuitOpen(state, now))
        assertEquals(
            RecoveryBackoffPolicy.MAX_RECOVERIES_PER_WINDOW,
            RecoveryBackoffPolicy.recentAttempts(state, now)
        )
    }

    @Test
    fun `the delay escalates with each in-window attempt`() {
        val now = 9_000_000L
        var state = RecoveryBackoffPolicy.State()
        var previousDelay = 0L
        repeat(4) {
            state = RecoveryBackoffPolicy.recordAttempt(state, now)
            val delay = RecoveryBackoffPolicy.delayMs(
                RecoveryBackoffPolicy.recentAttempts(state, now)
            )
            assertTrue("delay must escalate, got $delay after $previousDelay", delay > previousDelay)
            previousDelay = delay
        }
    }

    @Test
    fun `reset clears the ladder completely`() {
        var state = RecoveryBackoffPolicy.State()
        repeat(3) { state = RecoveryBackoffPolicy.recordAttempt(state, System.currentTimeMillis()) }
        assertTrue(RecoveryBackoffPolicy.recentAttempts(state, System.currentTimeMillis()) > 0)
        val cleared = RecoveryBackoffPolicy.reset()
        assertEquals(0, RecoveryBackoffPolicy.recentAttempts(cleared, System.currentTimeMillis()))
        assertFalse(RecoveryBackoffPolicy.circuitOpen(cleared, System.currentTimeMillis()))
    }

    @Test
    fun `the attempt ledger is bounded`() {
        var state = RecoveryBackoffPolicy.State()
        repeat(RecoveryBackoffPolicy.MAX_TRACKED_ATTEMPTS * 3) { index ->
            state = RecoveryBackoffPolicy.recordAttempt(state, index.toLong())
        }
        assertTrue(state.attempts.size <= RecoveryBackoffPolicy.MAX_TRACKED_ATTEMPTS)
    }
}
