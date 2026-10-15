package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

        t = 1_000_500L
        val second = gate.tryAcquire()
        assertEquals(SmartRankGate.Verdict.COOLDOWN, second.verdict)
        assertTrue(second.retryInMs in 1L..500L)
        assertFalse(gate.isInFlight())
    }

    @Test
    fun concurrentTriggerWhileInFlightIsRejectedAsBusy() {
        val gate = SmartRankGate(cooldownMs = 1_000L) { 1_000_000L }
        assertEquals(SmartRankGate.Verdict.ACCEPTED, gate.tryAcquire().verdict)

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

        t = 1_001_000L
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
