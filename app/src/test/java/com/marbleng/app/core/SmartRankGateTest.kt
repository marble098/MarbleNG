package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Smart Rank debounce / single-flight gate (MARBLE_SMART_RANK_V90).
 *
 * These lock in the fix for the observed "9 Rank triggers in 7 seconds with zero real results":
 * every trigger re-ran the full preflight + benchmark pool, so a tap storm produced nothing but
 * wasted probes. The gate must accept exactly one run per cooldown window and never allow two
 * concurrent runs.
 */
class SmartRankGateTest {

    @Test
    fun acceptsFirstTriggerAndHoldsSingleFlightLock() {
        val gate = SmartRankGate(cooldownMs = 1_000L) { 1_000_000L }
        val first = gate.tryAcquire()
        assertEquals(SmartRankGate.Verdict.ACCEPTED, first.verdict)
        assertTrue(gate.isInFlight())
        gate.release()
        assertFalse(gate.isInFlight())
    }

    @Test
    fun secondTriggerWithinCooldownIsDebounced() {
        var t = 1_000_000L
        val gate = SmartRankGate(cooldownMs = 1_000L) { t }
        assertEquals(SmartRankGate.Verdict.ACCEPTED, gate.tryAcquire().verdict)
        gate.release()

        t = 1_000_500L // 500ms later — still inside the 1000ms cooldown
        val second = gate.tryAcquire()
        assertEquals(SmartRankGate.Verdict.COOLDOWN, second.verdict)
        assertTrue(second.retryInMs in 1L..500L)
        assertFalse(gate.isInFlight())
    }

    @Test
    fun concurrentTriggerWhileInFlightIsRejectedAsBusy() {
        val gate = SmartRankGate(cooldownMs = 1_000L) { 1_000_000L }
        assertEquals(SmartRankGate.Verdict.ACCEPTED, gate.tryAcquire().verdict)
        // No release: the first run is still in flight.
        val second = gate.tryAcquire()
        assertEquals(SmartRankGate.Verdict.BUSY, second.verdict)
        assertTrue(second.message.contains("already running", ignoreCase = true))
        gate.release()
    }

    @Test
    fun acceptsAgainAfterCooldownElapses() {
        var t = 1_000_000L
        val gate = SmartRankGate(cooldownMs = 1_000L) { t }
        assertEquals(SmartRankGate.Verdict.ACCEPTED, gate.tryAcquire().verdict)
        gate.release()

        t = 1_001_000L // exactly the cooldown window later
        assertEquals(SmartRankGate.Verdict.ACCEPTED, gate.tryAcquire().verdict)
        gate.release()
    }

    @Test
    fun releaseWithoutAcquireIsHarmless() {
        val gate = SmartRankGate() { 1_000_000L }
        gate.release()
        assertFalse(gate.isInFlight())
    }
}
