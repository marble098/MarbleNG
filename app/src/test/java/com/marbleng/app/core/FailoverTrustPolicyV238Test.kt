package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FAILOVER_TRUST_V238 — a failure-driven route switch needs a quiet period and evidence
 * worth trusting. The report (P1-5) watched failovers fire at confidence 0.15 twice within a
 * minute while the underlay was mid-handover, costing two full reconnects for nothing.
 */
class FailoverTrustPolicyV238Test {

    private val nowMs = 10 * 60_000L

    @Test
    fun `the first switch of a session is allowed`() {
        val decision = FailoverTrustPolicy.decide(nowMs, lastSwitchAtMs = 0L, confidence = 0.9)
        assertTrue(decision.allowed)
        assertEquals(FailoverTrustPolicy.Reason.ALLOWED, decision.reason)
    }

    @Test
    fun `a switch inside the cooldown is held and names the wait`() {
        val decision = FailoverTrustPolicy.decide(
            nowMs,
            lastSwitchAtMs = nowMs - 30_000L,
            confidence = 1.0
        )
        assertFalse(decision.allowed)
        assertEquals(FailoverTrustPolicy.Reason.COOLDOWN, decision.reason)
        assertEquals("90 s minus the elapsed 30 s", 60_000L, decision.remainingMs)
    }

    @Test
    fun `the cooldown boundary itself opens the gate`() {
        // "No two switches closer than 90 s": switches exactly 90 s apart are not closer, so
        // the quiet period no longer holds one.
        val decision = FailoverTrustPolicy.decide(
            nowMs,
            lastSwitchAtMs = nowMs - FailoverTrustPolicy.COOLDOWN_MS,
            confidence = 1.0
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun `thin evidence is held even outside the cooldown`() {
        val decision = FailoverTrustPolicy.decide(
            nowMs,
            lastSwitchAtMs = nowMs - FailoverTrustPolicy.COOLDOWN_MS - 1L,
            confidence = 0.15
        )
        assertFalse(decision.allowed)
        assertEquals(FailoverTrustPolicy.Reason.LOW_CONFIDENCE, decision.reason)
    }

    @Test
    fun `the confidence floor is inclusive`() {
        val atFloor = FailoverTrustPolicy.decide(
            nowMs,
            lastSwitchAtMs = nowMs - FailoverTrustPolicy.COOLDOWN_MS - 1L,
            confidence = 0.5
        )
        assertTrue(atFloor.allowed)

        val below = FailoverTrustPolicy.decide(
            nowMs,
            lastSwitchAtMs = nowMs - FailoverTrustPolicy.COOLDOWN_MS - 1L,
            confidence = 0.4999
        )
        assertFalse(below.allowed)
    }

    @Test
    fun `the numbers match the constants the repository publishes`() {
        assertEquals(90_000L, FailoverTrustPolicy.COOLDOWN_MS)
        assertEquals(0.5, FailoverTrustPolicy.MIN_CONFIDENCE, 0.0001)
    }
}
