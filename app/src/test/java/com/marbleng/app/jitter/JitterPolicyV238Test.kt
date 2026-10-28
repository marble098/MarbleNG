package com.marbleng.app.jitter

import com.marbleng.app.core.AdaptiveProbeCadence
import com.marbleng.app.core.FailoverTrustPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SMART_JITTER_V238 — the pure decision layer of the Smart Jitter Reducer: scoring, the
 * switch guard rails, and the adaptive probe cadence. Every number the report (P0-2) asked for —
 * "no verdict under N samples", "hysteresis before a switch", "no probing storm on a calm link" —
 * lives in one of these three objects.
 */
class JitterPolicyV238Test {

    // ------------------------------------------------------------------ scoring

    private fun sample(
        jitterMs: Double = 10.0,
        p95RttMs: Double = 120.0,
        rttMeanMs: Double = 100.0,
        lossPercent: Double = 0.0,
        validSamples: Int = 8,
        muxCapable: Boolean = false
    ) = ScoreEngine.PathSample(
        jitterMs = jitterMs,
        p95RttMs = p95RttMs,
        rttMeanMs = rttMeanMs,
        lossPercent = lossPercent,
        validSamples = validSamples,
        muxCapable = muxCapable
    )

    @Test
    fun `no score below the minimum sample count`() {
        assertNull(ScoreEngine.score(sample(validSamples = 7), minSamples = 8))
        assertEquals(8, JitterWindow.DEFAULT_MIN_SAMPLES)
        assertNotNull(ScoreEngine.score(sample(validSamples = 8), minSamples = 8))
    }

    @Test
    fun `a good path scores high and a bad path scores low`() {
        val good = ScoreEngine.score(sample()) ?: 0.0
        val bad = ScoreEngine.score(
            sample(jitterMs = 90.0, p95RttMs = 900.0, rttMeanMs = 700.0, lossPercent = 8.0)
        ) ?: 100.0
        assertTrue("good=$good bad=$bad", good > 70.0 && bad < 40.0)
    }

    @Test
    fun `mux on a jittery path is penalized, mux on a calm path is not`() {
        val jitteryMuxed = ScoreEngine.score(sample(jitterMs = 60.0, muxCapable = true)) ?: 0.0
        val jitteryPlain = ScoreEngine.score(sample(jitterMs = 60.0, muxCapable = false)) ?: 0.0
        assertTrue(
            "mux over ≥40 ms of jitter must score lower, muxed=$jitteryMuxed plain=$jitteryPlain",
            jitteryMuxed < jitteryPlain
        )

        val calmMuxed = ScoreEngine.score(sample(muxCapable = true)) ?: 0.0
        val calmPlain = ScoreEngine.score(sample(muxCapable = false)) ?: 0.0
        assertEquals(calmPlain, calmMuxed, 0.001)
    }

    // ------------------------------------------------------------------ switching

    private fun decision(
        incumbent: Double,
        challenger: Double,
        lastSwitchAtMs: Long = 0L,
        liveSinceMs: Long = 10 * 60_000L,
        nowMs: Long = 20 * 60_000L
    ) = SwitchPolicy.decide(
        incumbentScore = incumbent,
        challengerScore = challenger,
        incumbentLiveSinceMs = liveSinceMs,
        state = SwitchPolicy.State(lastSwitchAtMs = lastSwitchAtMs),
        nowMs = nowMs
    )

    @Test
    fun `a challenger must beat the incumbent by the hysteresis margin`() {
        val marginal = decision(incumbent = 50.0, challenger = 55.0)
        assertFalse(marginal.shouldSwitch)

        val convincing = decision(incumbent = 50.0, challenger = 65.0)
        assertTrue(convincing.shouldSwitch)
    }

    @Test
    fun `no switch inside the cooldown`() {
        val nowMs = 20 * 60_000L
        val recent = decision(
            incumbent = 50.0,
            challenger = 90.0,
            lastSwitchAtMs = nowMs - (SwitchPolicy.DEFAULT_COOLDOWN_MS - 1_000L),
            nowMs = nowMs
        )
        assertFalse("180 s cooldown", recent.shouldSwitch)

        val settled = decision(
            incumbent = 50.0,
            challenger = 90.0,
            lastSwitchAtMs = nowMs - SwitchPolicy.DEFAULT_COOLDOWN_MS - 1_000L,
            nowMs = nowMs
        )
        assertTrue(settled.shouldSwitch)
    }

    @Test
    fun `no switch while the incumbent is still warming up`() {
        val nowMs = 10 * 60_000L
        val fresh = SwitchPolicy.decide(
            incumbentScore = 30.0,
            challengerScore = 90.0,
            incumbentLiveSinceMs = nowMs - (SwitchPolicy.DEFAULT_MIN_DWELL_MS - 1_000L),
            state = SwitchPolicy.State(),
            nowMs = nowMs
        )
        assertFalse("90 s dwell", fresh.shouldSwitch)
    }

    @Test
    fun `a dead incumbent needs an absolutely viable challenger`() {
        val viable = decision(incumbent = 0.0, challenger = 50.0)
        assertTrue(viable.shouldSwitch)

        val desperate = decision(incumbent = 0.0, challenger = 39.0)
        assertFalse("a 39-point challenger is another guess", desperate.shouldSwitch)
    }

    @Test
    fun `thin evidence on either side blocks the switch`() {
        val unknownIncumbent = SwitchPolicy.decide(
            incumbentScore = null,
            challengerScore = 90.0,
            incumbentLiveSinceMs = 10 * 60_000L,
            state = SwitchPolicy.State(),
            nowMs = 20 * 60_000L
        )
        assertFalse(unknownIncumbent.shouldSwitch)

        val unknownChallenger = SwitchPolicy.decide(
            incumbentScore = 50.0,
            challengerScore = null,
            incumbentLiveSinceMs = 10 * 60_000L,
            state = SwitchPolicy.State(),
            nowMs = 20 * 60_000L
        )
        assertFalse(unknownChallenger.shouldSwitch)
    }

    // ------------------------------------------------------------------ cadence

    @Test
    fun `a calm link is probed progressively less`() {
        val base = AdaptiveProbeCadence.intervalTicks(
            calmStreak = 0, normalTicks = 5, degradedTicks = 2, jitterControlActive = false
        )
        val stretched = AdaptiveProbeCadence.intervalTicks(
            calmStreak = 4, normalTicks = 5, degradedTicks = 2, jitterControlActive = false
        )
        assertTrue("calm must stretch the interval, $base → $stretched", stretched > base)
        assertTrue(
            "the stretch is bounded",
            stretched <= AdaptiveProbeCadence.MAX_INTERVAL_TICKS
        )
    }

    @Test
    fun `jitter control starts at the degraded cadence and still stretches when calm`() {
        val engaged = AdaptiveProbeCadence.intervalTicks(
            calmStreak = 0, normalTicks = 5, degradedTicks = 2, jitterControlActive = true
        )
        val idle = AdaptiveProbeCadence.intervalTicks(
            calmStreak = 0, normalTicks = 5, degradedTicks = 2, jitterControlActive = false
        )
        assertEquals("engaged jitter control runs at the degraded cadence", 2, engaged)
        assertEquals("an idle tunnel runs at the normal cadence", 5, idle)

        val recovered = AdaptiveProbeCadence.intervalTicks(
            calmStreak = 5, normalTicks = 5, degradedTicks = 2, jitterControlActive = true
        )
        assertTrue("a calm streak still stretches, $engaged → $recovered", recovered > engaged)
        assertTrue(recovered <= AdaptiveProbeCadence.MAX_INTERVAL_TICKS)
    }

    @Test
    fun `one rough probe collapses the streak`() {
        assertEquals(1, AdaptiveProbeCadence.nextStreak(calm = true, currentStreak = 0, jitterMs = 5, lossPercent = 0, successPercent = 100))
        assertEquals(3, AdaptiveProbeCadence.nextStreak(calm = true, currentStreak = 2, jitterMs = 5, lossPercent = 0, successPercent = 100))
        assertEquals(0, AdaptiveProbeCadence.nextStreak(calm = true, currentStreak = 4, jitterMs = 40, lossPercent = 0, successPercent = 100))
        assertEquals(0, AdaptiveProbeCadence.nextStreak(calm = true, currentStreak = 4, jitterMs = 5, lossPercent = 1, successPercent = 100))
        assertEquals(0, AdaptiveProbeCadence.nextStreak(calm = true, currentStreak = 4, jitterMs = 5, lossPercent = 0, successPercent = 90))
        assertEquals(0, AdaptiveProbeCadence.nextStreak(calm = false, currentStreak = 4, jitterMs = 5, lossPercent = 0, successPercent = 100))
    }

    @Test
    fun `the scheduler grows on calm and answers zero when off`() {
        assertEquals(0L, ProbeScheduler.nextDelayMs(baseIntervalMs = 5_000L, calmStreak = 3, mode = JitterMode.OFF))
        val grown = ProbeScheduler.nextDelayMs(baseIntervalMs = 5_000L, calmStreak = 6, mode = JitterMode.BALANCED)
        val base = ProbeScheduler.nextDelayMs(baseIntervalMs = 5_000L, calmStreak = 0, mode = JitterMode.BALANCED)
        assertTrue("calm grows the delay, $base → $grown", grown > base)
        assertTrue(
            "the power-aware ceiling holds",
            grown <= ProbeScheduler.DEFAULT_MAX_INTERVAL_MS * ProbeScheduler.MAX_POWER_MULTIPLIER
        )
    }

    // ------------------------------------------------------------------ failover trust

    @Test
    fun `a failure-driven switch respects cooldown and confidence`() {
        val nowMs = 1_000_000L
        // First ever switch: allowed.
        assertEquals(
            FailoverTrustPolicy.Reason.ALLOWED,
            FailoverTrustPolicy.decide(nowMs, lastSwitchAtMs = 0L, confidence = 1.0).reason
        )
        // 30 s after the last one: held, whatever the evidence.
        val held = FailoverTrustPolicy.decide(
            nowMs,
            lastSwitchAtMs = nowMs - 30_000L,
            confidence = 1.0
        )
        assertEquals(FailoverTrustPolicy.Reason.COOLDOWN, held.reason)
        assertEquals(60_000L, held.remainingMs)
        // 91 s later but the challenger's evidence is a guess: still held.
        assertEquals(
            FailoverTrustPolicy.Reason.LOW_CONFIDENCE,
            FailoverTrustPolicy.decide(
                nowMs,
                lastSwitchAtMs = nowMs - 91_000L,
                confidence = 0.49
            ).reason
        )
        // 91 s later with real evidence: allowed.
        assertEquals(
            FailoverTrustPolicy.Reason.ALLOWED,
            FailoverTrustPolicy.decide(
                nowMs,
                lastSwitchAtMs = nowMs - 91_000L,
                confidence = 0.5
            ).reason
        )
        assertEquals(FailoverTrustPolicy.COOLDOWN_MS, 90_000L)
        assertEquals(FailoverTrustPolicy.MIN_CONFIDENCE, 0.5, 0.0001)
    }
}
