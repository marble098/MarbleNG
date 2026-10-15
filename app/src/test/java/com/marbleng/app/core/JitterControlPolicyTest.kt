package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterControlPolicyTest {
    private val t0 = 1_700_000_000_000L

    private fun sample(
        jitter: Double,
        samples: Int = 6,
        jitterReady: Boolean = true,
        p95Ipdv: Double = 8.0,
        loss: Double = 0.0,
        spike: Double = 0.0
    ) = JitterControlPolicy.Sample(
        samples = samples,
        jitterReady = jitterReady,
        jitterMs = jitter,
        triggerMs = 25.0,
        releaseMs = 12.0,
        p95IpdvMs = p95Ipdv,
        lossPercent = loss,
        spikePercent = spike
    )

    @Test
    fun `three degraded ticks enter jitter control`() {
        var state = JitterControlPolicy.State()
        repeat(JitterControlPolicy.HIGH_CONFIRMATIONS - 1) { index ->
            val decision = JitterControlPolicy.evaluate(sample(90.0), state, t0 + index * 1_000L)
            state = decision.state
            assertEquals(JitterControlPolicy.Verdict.HOLD, decision.verdict)
        }
        val entered = JitterControlPolicy.evaluate(
            sample(90.0), state, t0 + JitterControlPolicy.HIGH_CONFIRMATIONS * 1_000L
        )
        assertEquals(JitterControlPolicy.Verdict.ENTER, entered.verdict)
        assertTrue(entered.state.active)
    }

    @Test
    fun `an ambiguous tick holds both counters instead of wiping the release streak`() {
        var state = JitterControlPolicy.State()

        repeat(JitterControlPolicy.HIGH_CONFIRMATIONS) {
            state = JitterControlPolicy.evaluate(sample(90.0), state, t0).state
        }
        assertTrue(state.active)

        state = JitterControlPolicy.evaluate(sample(5.0), state, t0 + 1_000L).state
        state = JitterControlPolicy.evaluate(sample(5.0), state, t0 + 2_000L).state
        val ambiguous = JitterControlPolicy.evaluate(sample(18.0), state, t0 + 3_000L)
        assertEquals("mixed", ambiguous.tick)
        assertEquals(JitterControlPolicy.Verdict.HOLD, ambiguous.verdict)
        assertTrue(
            "a mixed tick must not erase the accumulated release trend, lowStreak=${ambiguous.lowStreak}",
            ambiguous.lowStreak > 0
        )

        val insufficient = JitterControlPolicy.evaluate(
            sample(5.0, samples = 1, jitterReady = false), ambiguous.state, t0 + 4_000L
        )
        assertEquals("insufficient", insufficient.tick)
        assertEquals(ambiguous.lowStreak, insufficient.lowStreak)
        assertEquals(ambiguous.highStreak, insufficient.highStreak)
    }

    @Test
    fun `release needs a full clean run and then exits`() {
        var state = JitterControlPolicy.State()
        repeat(JitterControlPolicy.HIGH_CONFIRMATIONS) {
            state = JitterControlPolicy.evaluate(sample(90.0), state, t0).state
        }
        var decision = JitterControlPolicy.evaluate(sample(5.0), state, t0 + 1_000L)
        state = decision.state
        repeat(JitterControlPolicy.RELEASE_CONFIRMATIONS - 1) { index ->
            decision = JitterControlPolicy.evaluate(sample(5.0), state, t0 + (index + 2) * 1_000L)
            state = decision.state
            assertEquals(JitterControlPolicy.Verdict.HOLD, decision.verdict)
        }

        decision = JitterControlPolicy.evaluate(
            sample(5.0), state, t0 + JitterControlPolicy.MIN_HOLD_MS + 1_000L
        )
        assertEquals(JitterControlPolicy.Verdict.EXIT, decision.verdict)
        assertFalse(decision.state.active)
    }

    @Test
    fun `the minimum hold blocks an immediate exit`() {
        var state = JitterControlPolicy.State()
        repeat(JitterControlPolicy.HIGH_CONFIRMATIONS) {
            state = JitterControlPolicy.evaluate(sample(90.0), state, t0).state
        }
        var decision = JitterControlPolicy.evaluate(sample(5.0), state, t0 + 1_000L)
        repeat(JitterControlPolicy.RELEASE_CONFIRMATIONS) {
            decision = JitterControlPolicy.evaluate(sample(5.0), decision.state, t0 + 1_100L)
        }
        assertEquals("hold", decision.tick)
        assertEquals(JitterControlPolicy.Verdict.HOLD, decision.verdict)
        assertTrue(decision.state.active)
    }

    @Test
    fun `the dwell window blocks an immediate re-entry`() {
        val exitedAt = t0 + 1_000L
        var state = JitterControlPolicy.State(exitedAtMs = exitedAt)
        var decision = JitterControlPolicy.evaluate(sample(90.0), state, exitedAt + 1_000L)
        repeat(JitterControlPolicy.HIGH_CONFIRMATIONS - 1) {
            decision = JitterControlPolicy.evaluate(sample(90.0), decision.state, exitedAt + 1_000L)
        }
        assertEquals("dwell", decision.tick)
        assertEquals(JitterControlPolicy.Verdict.HOLD, decision.verdict)
        assertFalse(decision.state.active)

        decision = JitterControlPolicy.evaluate(
            sample(90.0), decision.state, exitedAt + JitterControlPolicy.MIN_DWELL_MS + 1L
        )
        assertEquals(JitterControlPolicy.Verdict.ENTER, decision.verdict)
    }

    @Test
    fun `a single contradictory sample steps the trend down without erasing it`() {
        var state = JitterControlPolicy.State()
        repeat(2) { state = JitterControlPolicy.evaluate(sample(90.0), state, t0).state }
        assertEquals(2, state.highStreak)
        val contradicted = JitterControlPolicy.evaluate(sample(5.0), state, t0 + 1_000L)
        assertEquals(1, contradicted.highStreak)
        assertEquals(1, contradicted.lowStreak)
    }

    @Test
    fun `instability signals enter control even when ewma jitter looks calm`() {
        var state = JitterControlPolicy.State()
        var decision = JitterControlPolicy.evaluate(sample(10.0, loss = 22.0), state, t0)
        repeat(JitterControlPolicy.HIGH_CONFIRMATIONS - 1) {
            decision = JitterControlPolicy.evaluate(sample(10.0, loss = 22.0), decision.state, t0 + 1_000L)
        }
        assertEquals(JitterControlPolicy.Verdict.ENTER, decision.verdict)
    }
}
