package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_EGRESS_EVIDENCE_V133 regressions.
 *
 * The attached runtime log repeated `EGRESS startup-observation-inconclusive` with
 * `literalIpHttps=true, domainHttps=false` and a `TURBO startup-family-tune-requested` right behind
 * it. The two probes were not comparable — a full 3500 ms HTTPS GET against a 1500 ms TLS first-byte
 * measurement — so the "domains are broken" verdict was an artefact of unequal budgets, and the
 * observation was one-shot for the whole session. These tests pin the corrected interpretation.
 */
class EgressObservationPolicyTest {

    @Test
    fun `a verified domain transaction is healthy and needs no re-arm`() {
        val decision = EgressObservationPolicy.observe(
            state = EgressObservationPolicy.State(),
            domainHealthy = true,
            literalHealthy = true
        )
        assertEquals(EgressObservationPolicy.Verdict.HEALTHY, decision.verdict)
        assertFalse(decision.requestFamilyTune)
        assertEquals(0L, decision.rearmDelayMs)
        assertEquals(0, decision.state.consecutiveDnsSuspicions)
    }

    @Test
    fun `one literal-only reading is a suspicion, not a verdict`() {
        val decision = EgressObservationPolicy.observe(
            state = EgressObservationPolicy.State(),
            domainHealthy = false,
            literalHealthy = true
        )
        assertEquals(EgressObservationPolicy.Verdict.INCONCLUSIVE, decision.verdict)
        assertFalse(
            "a single reading must never send the engine off to re-measure address families",
            decision.requestFamilyTune
        )
        assertEquals(1, decision.state.consecutiveDnsSuspicions)
        assertTrue(decision.rearmDelayMs > 0L)
    }

    @Test
    fun `two agreeing readings confirm and request the family tune exactly once`() {
        val first = EgressObservationPolicy.observe(
            EgressObservationPolicy.State(), domainHealthy = false, literalHealthy = true
        )
        val second = EgressObservationPolicy.observe(
            first.state, domainHealthy = false, literalHealthy = true
        )
        assertTrue(second.requestFamilyTune)
        assertEquals(2, second.state.consecutiveDnsSuspicions)

        val third = EgressObservationPolicy.observe(
            second.state, domainHealthy = false, literalHealthy = true
        )
        assertFalse("the tune must not be requested again", third.requestFamilyTune)
    }

    @Test
    fun `a healthy reading in between resets the suspicion streak`() {
        val first = EgressObservationPolicy.observe(
            EgressObservationPolicy.State(), domainHealthy = false, literalHealthy = true
        )
        val healthy = EgressObservationPolicy.observe(
            first.state, domainHealthy = true, literalHealthy = true
        )
        assertEquals(0, healthy.state.consecutiveDnsSuspicions)
        val again = EgressObservationPolicy.observe(
            healthy.state, domainHealthy = false, literalHealthy = true
        )
        assertFalse(again.requestFamilyTune)
    }

    @Test
    fun `nothing answering is route suspicion, never a dns verdict`() {
        val decision = EgressObservationPolicy.observe(
            EgressObservationPolicy.State(), domainHealthy = false, literalHealthy = false
        )
        assertEquals(EgressObservationPolicy.Verdict.ROUTE_SUSPECT, decision.verdict)
        assertFalse(decision.requestFamilyTune)
        assertEquals(0, decision.state.consecutiveDnsSuspicions)
    }

    @Test
    fun `the re-arm schedule is bounded and then stops`() {
        assertEquals(
            EgressObservationPolicy.REARM_SCHEDULE_MS.first(),
            EgressObservationPolicy.rearmDelay(1)
        )
        assertEquals(
            EgressObservationPolicy.REARM_SCHEDULE_MS.last(),
            EgressObservationPolicy.rearmDelay(EgressObservationPolicy.REARM_SCHEDULE_MS.size)
        )
        assertEquals(
            0L,
            EgressObservationPolicy.rearmDelay(EgressObservationPolicy.REARM_SCHEDULE_MS.size + 1)
        )
    }
}
