package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_HIGH_JITTER_SHIELD_V206 / V207 regressions.
 *
 * These tests are the critique, written down. Each one names a behaviour the previous
 * jitter handling got wrong and pins the replacement:
 *
 *  • a mean is destroyed by one packet, a median/MAD window is not;
 *  • the statistics are computed once per change, not once per sample;
 *  • severity is relative to the link's own baseline, not to a constant;
 *  • the level attacks fast, releases slowly, and holds after an escalation;
 *  • an idle link fades, and a mitigation never costs more than the probe budget.
 */
class HighJitterShieldTest {

    private val t0 = 1_700_000_000_000L

    private fun window(vararg rttMs: Double): HighJitterShield.RobustWindow {
        val w = HighJitterShield.RobustWindow()
        rttMs.forEach { w.add(it) }
        return w
    }

    /** Feeds [rtts] one per second into one window/state pair and returns the last decision. */
    private fun feed(
        rtts: List<Double>,
        startMs: Long = t0,
        stepMs: Long = 1_000L,
        state: HighJitterShield.State = HighJitterShield.State(),
        target: HighJitterShield.RobustWindow = HighJitterShield.RobustWindow()
    ): HighJitterShield.Decision {
        var current = state
        var now = startMs
        var last = HighJitterShield.Decision(current, planOf(current))
        for (rtt in rtts) {
            last = HighJitterShield.observe(
                sample = HighJitterShield.Sample(rttMs = rtt, nowMs = now),
                state = current,
                window = target
            )
            current = last.state
            now += stepMs
        }
        return last
    }

    private fun planOf(state: HighJitterShield.State): HighJitterShield.Plan = HighJitterShield.Plan(
        verdict = state.verdict,
        level = state.level,
        jitterMs = 0.0,
        medianRttMs = 0.0,
        p95RttMs = 0.0,
        spikeRatio = 0.0,
        baselineJitterMs = state.baselineJitterMs,
        samples = 0,
        probeBurst = 1,
        probeEveryTicks = HighJitterShield.BASE_TICKS,
        bufferScale = 1.0,
        requestRerank = false,
        reason = "seed"
    )

    // ─── The estimator ───────────────────────────────────────────────────────────────────

    @Test
    fun `one stalled handshake does not move the robust jitter but destroys the mean`() {
        val calm = MutableList(23) { 120.0 }
        // One retransmitted TLS handshake inside an otherwise flat window.
        val samples = calm + 3_000.0

        val robust = window(*samples.toDoubleArray()).robustJitterMs()
        val naive = HighJitterShield.naiveJitterMs(samples)

        assertTrue(
            "the mean-based estimate panics on one packet ($naive ms); the robust one must not ($robust ms)",
            naive > 100.0
        )
        assertTrue(
            "a single spike in 24 samples must not read as a jittery link: $robust ms",
            robust < 15.0
        )
    }

    @Test
    fun `statistics are recomputed once per change, not once per read or per sample`() {
        val w = HighJitterShield.RobustWindow()
        repeat(6) { index -> w.add(100.0 + index) }

        val first = w.medianMs()
        val afterFirstRead = w.evaluations
        val second = w.medianMs()
        assertEquals("reading twice must not recompute", afterFirstRead, w.evaluations)
        assertEquals(first, second, 0.0)

        w.madMs()
        w.p95Ms()
        w.spikeRatio()
        assertEquals("three more reads of the same window are free", afterFirstRead, w.evaluations)

        w.add(140.0)
        w.medianMs()
        assertEquals("one write costs exactly one recomputation", afterFirstRead + 1, w.evaluations)
    }

    @Test
    fun `the window is bounded and a failed probe is not a sample`() {
        val w = HighJitterShield.RobustWindow(capacity = 8)
        repeat(200) { index -> w.add(100.0 + index.toDouble()) }
        assertEquals(8, w.size)

        val before = w.size
        HighJitterShield.observe(
            sample = HighJitterShield.Sample(rttMs = 4_000.0, nowMs = t0, verified = false),
            state = HighJitterShield.State(),
            window = w
        )
        assertEquals("a probe that produced no answer must not enter the window", before, w.size)

        HighJitterShield.observe(
            sample = HighJitterShield.Sample(rttMs = -5.0, nowMs = t0, verified = true),
            state = HighJitterShield.State(),
            window = w
        )
        assertEquals("a negative round trip is a measurement error, not a measurement", before, w.size)
    }

    // ─── The verdict ─────────────────────────────────────────────────────────────────────

    @Test
    fun `fewer than the minimum samples stays calm`() {
        val decision = feed(listOf(400.0, 40.0, 900.0, 30.0, 800.0))
        assertEquals(HighJitterShield.Verdict.CALM, decision.plan.verdict)
        assertEquals(0.0, decision.plan.level, 0.0)
    }

    @Test
    fun `severity is relative to the baseline of this link, not to one constant`() {
        // Two links, both calm, with very different ideas of calm:
        //  • Iranian mobile in the evening: 120/160 ms alternating, ~30 ms of real jitter.
        //  • A fibre line: 30/34 ms alternating, ~3 ms of real jitter.
        val mobileCalm = List(20) { if (it % 2 == 0) 120.0 else 160.0 }
        val fibreCalm = List(20) { if (it % 2 == 0) 30.0 else 34.0 }
        val mobile = feed(mobileCalm)
        val fibre = feed(fibreCalm)

        assertTrue("the mobile link must have learned a real baseline", mobile.state.baselineJitterMs > 20.0)
        assertTrue("the fibre link must have learned a real baseline", fibre.state.baselineJitterMs > 1.0)
        assertEquals(HighJitterShield.Verdict.CALM, mobile.plan.verdict)
        assertEquals(HighJitterShield.Verdict.CALM, fibre.plan.verdict)

        // The SAME absolute jitter (~95 ms) arrives on both. The window is filled with it, so
        // this is real jitter and not a spike: 200/328 alternating, MAD 64 ms.
        val bad = List(24) { if (it % 2 == 0) 200.0 else 328.0 }
        val mobileThenBad = feed(
            rtts = bad,
            startMs = t0 + 20_000L,
            state = mobile.state,
            target = window(*mobileCalm.toDoubleArray())
        )
        val fibreThenBad = feed(
            rtts = bad,
            startMs = t0 + 20_000L,
            state = fibre.state,
            target = window(*fibreCalm.toDoubleArray())
        )

        assertEquals(
            "95 ms of jitter is business as usual for the mobile link",
            HighJitterShield.Verdict.ELEVATED,
            mobileThenBad.plan.verdict
        )
        assertEquals(
            "the same 95 ms is a catastrophe for the fibre link",
            HighJitterShield.Verdict.EXTREME,
            fibreThenBad.plan.verdict
        )
        assertTrue(
            "and the mitigation follows the verdict: ${mobileThenBad.plan.level} vs ${fibreThenBad.plan.level}",
            fibreThenBad.plan.level > mobileThenBad.plan.level + 0.2
        )
    }

    @Test
    fun `a bimodal window reads as jittery, not as perfectly smooth`() {
        // A filtered route and its failover: half the packets take one path, half the other.
        // The median absolute deviation of this window is ZERO — more than half the samples
        // sit on the median — which is exactly why dispersion is measured by the IQR here.
        val bimodal = window(*(List(11) { 120.0 } + List(9) { 520.0 }).toDoubleArray())
        assertEquals(
            "this is the defect: the MAD of a bimodal window is exactly zero",
            0.0,
            bimodal.madMs(),
            1e-9
        )
        assertTrue(
            "a link that alternates between two paths must not report 0 ms of jitter",
            bimodal.robustJitterMs() > 200.0
        )
    }

    @Test
    fun `a minority of spikes escalates the verdict one step, uniform noise does not`() {
        // Two windows with the same spread; one of them also carries three packets that
        // arrived from another planet. Tukey's fence separates them.
        val core = listOf(
            60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0, 140.0,
            60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0
        )
        val withoutSpikes = feed(
            rtts = core + listOf(65.0, 95.0, 125.0),
            state = HighJitterShield.State(baselineJitterMs = 40.0)
        )
        val withSpikes = feed(
            rtts = core + listOf(800.0, 810.0, 820.0),
            state = HighJitterShield.State(baselineJitterMs = 40.0)
        )

        assertTrue(
            "the noisy-but-clean window must not read as spiky: ${withoutSpikes.plan.spikeRatio}",
            withoutSpikes.plan.spikeRatio < HighJitterShield.SPIKE_SEVERE_RATIO
        )
        assertTrue(
            "three packets in twenty beyond the fence must be seen: ${withSpikes.plan.spikeRatio}",
            withSpikes.plan.spikeRatio >= HighJitterShield.SPIKE_SEVERE_RATIO
        )
        assertEquals(
            "the same jitter, one verdict worse because the window is also spiky",
            HighJitterShield.Verdict.CALM,
            withoutSpikes.plan.verdict
        )
        assertEquals(
            HighJitterShield.Verdict.ELEVATED,
            withSpikes.plan.verdict
        )
    }

    // ─── The controller ──────────────────────────────────────────────────────────────────

    @Test
    fun `the level attacks fast and releases slowly`() {
        val bad = List(12) { index -> if (index % 2 == 0) 120.0 else 520.0 }
        val escalated = feed(bad)
        assertTrue(
            "two seconds of severe jitter must already move the level: ${escalated.plan.level}",
            escalated.plan.level > 0.5
        )

        val recovering = feed(
            rtts = List(40) { 120.0 },
            startMs = t0 + 12_000L,
            state = escalated.state,
            target = window(*bad.toDoubleArray())
        )
        val before = escalated.plan.level
        assertTrue(
            "40 seconds of a healthy link must not disarm the shield at once: $before → ${recovering.plan.level}",
            recovering.plan.level > 0.10
        )
        assertTrue(
            "recovery must be visible, not forbidden: $before → ${recovering.plan.level}",
            recovering.plan.level < before
        )
    }

    @Test
    fun `an escalation holds before it may release`() {
        val bad = List(12) { index -> if (index % 2 == 0) 120.0 else 520.0 }
        val escalated = feed(bad)
        assertTrue(escalated.state.escalatedAtMs > 0L)

        // One good sample one second later: inside the hold window the level may not fall.
        val next = HighJitterShield.observe(
            sample = HighJitterShield.Sample(rttMs = 120.0, nowMs = t0 + 12_000L),
            state = escalated.state,
            window = window(*bad.toDoubleArray())
        )
        assertTrue(
            "a mitigation that just engaged cannot disengage on the next sample",
            next.plan.level >= escalated.plan.level - 1e-9
        )
    }

    @Test
    fun `an idle link fades instead of staying armed`() {
        val armed = HighJitterShield.State(
            level = 1.0,
            lastSampleAtMs = t0,
            escalatedAtMs = t0 - 120_000L,
            verdict = HighJitterShield.Verdict.EXTREME
        )
        val idle = HighJitterShield.observe(
            sample = HighJitterShield.Sample(rttMs = 120.0, nowMs = t0 + 60_000L),
            state = armed,
            window = HighJitterShield.RobustWindow()
        )
        val releaseOnly = 1.0 + (0.0 - 1.0) * HighJitterShield.ALPHA_RELEASE
        assertTrue(
            "stale evidence must fade faster than a normal release: ${idle.plan.level}",
            idle.plan.level < releaseOnly
        )
        assertTrue("but it must not vanish: ${idle.plan.level}", idle.plan.level > 0.5)
    }

    // ─── The cost ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the probe budget is never exceeded at any level`() {
        for (step in 0..20) {
            val level = step / 20.0
            // The ladder lives in the shield, not in the test: a copy here would be a second
            // opinion about how many probes a level costs the moment either one changed.
            val burst = HighJitterShield.burstFor(level)
            val wanted = (HighJitterShield.BASE_TICKS -
                (HighJitterShield.BASE_TICKS - HighJitterShield.MIN_TICKS) * level)
                .toInt()
                .coerceAtLeast(HighJitterShield.MIN_TICKS)
            val ticks = HighJitterShield.budgetedTicks(wanted, burst)
            val perMinute = HighJitterShield.probesPerMinute(ticks, burst)
            assertTrue(
                "level $level spends $perMinute probes/min (budget ${HighJitterShield.MAX_PROBES_PER_MINUTE})",
                perMinute <= HighJitterShield.MAX_PROBES_PER_MINUTE + 1e-9
            )
            assertTrue(ticks >= HighJitterShield.MIN_TICKS)
        }
    }

    @Test
    fun `an absurd cadence is clamped by the budget, not refused`() {
        val ticks = HighJitterShield.budgetedTicks(1, HighJitterShield.MAX_BURST)
        assertTrue(
            "a one-tick cadence of ${HighJitterShield.MAX_BURST} probes would be " +
                "${HighJitterShield.probesPerMinute(1, HighJitterShield.MAX_BURST)}/min; it must be clamped",
            HighJitterShield.probesPerMinute(ticks, HighJitterShield.MAX_BURST) <=
                HighJitterShield.MAX_PROBES_PER_MINUTE + 1e-9
        )
    }

    @Test
    fun `extreme jitter asks for another route once, then waits out the cooldown`() {
        val extreme = List(12) { index -> if (index % 2 == 0) 120.0 else 1_400.0 }
        var state = HighJitterShield.State()
        val rolling = HighJitterShield.RobustWindow()
        var now = t0
        var requests = 0
        var firstRequestAt = 0L

        for (rtt in extreme) {
            val decision = HighJitterShield.observe(
                sample = HighJitterShield.Sample(rttMs = rtt, nowMs = now),
                state = state,
                window = rolling
            )
            state = decision.state
            if (decision.plan.requestRerank) {
                requests++
                if (firstRequestAt == 0L) firstRequestAt = now
            }
            now += 1_000L
        }

        assertEquals(
            "a sustained extreme link asks once, not once per sample",
            1,
            requests
        )
        assertTrue(
            "and only after the extreme streak, never on the first samples",
            firstRequestAt >= t0 + HighJitterShield.RERANK_STREAK * 1_000L
        )

        // A second extreme stretch immediately afterwards is inside the cooldown.
        var repeated = 0
        for (rtt in extreme) {
            val decision = HighJitterShield.observe(
                sample = HighJitterShield.Sample(rttMs = rtt, nowMs = now),
                state = state,
                window = rolling
            )
            state = decision.state
            if (decision.plan.requestRerank) repeated++
            now += 1_000L
        }
        assertEquals("the optimizer must not be asked again inside the cooldown", 0, repeated)
    }

    @Test
    fun `the plan describes itself in one line`() {
        val decision = feed(List(12) { index -> if (index % 2 == 0) 120.0 else 900.0 })
        val described = HighJitterShield.describe(decision.plan)
        assertTrue(described.contains("burst"))
        assertTrue(described.contains("buffer"))
        assertTrue(described.contains(decision.plan.verdict.name.lowercase()))
    }
}
