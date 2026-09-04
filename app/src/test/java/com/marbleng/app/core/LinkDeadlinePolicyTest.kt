package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LINK_DEADLINE_V133 regressions.
 *
 * The runtime log that motivated this policy showed a healthy tunnel with a 1126 ms verified RTT,
 * sixteen `DoH deadline` resolver errors and **zero** TLS/cert errors, plus Home ping readings with
 * `responders=1`. Both are the same defect: deadlines that were constants sized for a fast link.
 * These tests pin the two behaviours that matter — an unmeasured link keeps the legacy constants
 * exactly, and a measured slow link gets a budget that can actually be met.
 */
class LinkDeadlinePolicyTest {

    /** The link from the attached runtime log: ~1.1 s RTT with 50–150 ms of jitter. */
    private val slowCellular = LinkEvidence(
        rttMs = 1126.0,
        tailRttMs = 1326.0,
        jitterMs = 100.0,
        lossPercent = 2.0,
        samples = 12
    )

    @Test
    fun `unknown evidence reproduces the legacy dns budgets exactly`() {
        assertEquals(
            LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 0, fragmented = false)
        )
        assertEquals(
            LinkDeadlinePolicy.SECONDARY_DNS_FLOOR_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 1, fragmented = false)
        )
        assertEquals(
            LinkDeadlinePolicy.SECONDARY_DNS_FLOOR_MS + LinkDeadlinePolicy.SECONDARY_DNS_STEP_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 2, fragmented = false)
        )
    }

    @Test
    fun `a measured slow link gets a dns budget it can actually meet`() {
        val timeout = LinkDeadlinePolicy.dnsServerTimeoutMs(slowCellular, 0, fragmented = false)
        // Three round trips of the tail RTT is the minimum a cold encrypted query needs.
        assertTrue(
            "expected >= 3x tail RTT (3978 ms) but was $timeout",
            timeout >= 3 * slowCellular.tailRttMs.toLong()
        )
        assertTrue(
            "the 1350 ms legacy budget is what caused the deadline storm, got $timeout",
            timeout > LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS
        )
        assertTrue(
            "budget must stay inside the upstream XTLS ceiling, got $timeout",
            timeout <= LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS
        )
    }

    @Test
    fun `dns budget is clamped to the upstream ceiling on an extreme link`() {
        val extreme = LinkEvidence(rttMs = 6_000.0, tailRttMs = 9_000.0, jitterMs = 900.0, samples = 5)
        assertEquals(
            LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(extreme, 0, fragmented = false)
        )
    }

    @Test
    fun `the freedom fragment chain keeps the upstream xtls schedule when unmeasured`() {
        assertEquals(
            LinkDeadlinePolicy.FRAGMENTED_DNS_BASE_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 0, fragmented = true)
        )
        assertEquals(
            LinkDeadlinePolicy.FRAGMENTED_DNS_BASE_MS + LinkDeadlinePolicy.FRAGMENTED_DNS_STEP_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 1, fragmented = true)
        )
    }

    @Test
    fun `a measured link pulls the fragment budget down from the 8s xtls constant`() {
        // MARBLE_FRAGMENT_DEADLINE_V135 — the attached log was a 242–347 ms route that still paid
        // the full 8 s per resolver; serial failover therefore sat behind a dead endpoint for the
        // whole budget before rotating. On measured evidence the budget must be the pacing
        // overhead plus round trips of the tail, never the legacy constant.
        val irancell = LinkEvidence(
            rttMs = 300.0, tailRttMs = 347.0, jitterMs = 60.0, lossPercent = 1.0, samples = 9
        )
        val primary = LinkDeadlinePolicy.dnsServerTimeoutMs(irancell, 0, fragmented = true)
        assertTrue(
            "the measured budget must beat the 8 s constant, got $primary",
            primary < LinkDeadlinePolicy.FRAGMENTED_DNS_BASE_MS
        )
        assertTrue(
            "the budget must still cover pacing + three tail round trips, got $primary",
            primary >= LinkDeadlinePolicy.FRAGMENT_PACING_OVERHEAD_MS +
                3 * irancell.tailRttMs.toLong()
        )
        assertTrue("the floor must hold, got $primary", primary >= LinkDeadlinePolicy.FRAGMENT_DNS_FLOOR_MS)
        assertTrue("the ceiling must hold, got $primary", primary <= LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS)
        // A slower measured link gets a bigger budget than the fast one, but stays bounded.
        val slowPrimary = LinkDeadlinePolicy.dnsServerTimeoutMs(slowCellular, 0, fragmented = true)
        assertTrue(slowPrimary > primary)
        assertTrue(slowPrimary <= LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS)
    }

    @Test
    fun `a fast measured link is never slowed below its floor`() {
        val fast = LinkEvidence(rttMs = 18.0, tailRttMs = 24.0, jitterMs = 3.0, samples = 20)
        assertEquals(
            LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(fast, 0, fragmented = false)
        )
    }

    @Test
    fun `probe budgets scale with the link so the race is not truncated`() {
        val perProbe = LinkDeadlinePolicy.httpsProbeTimeoutMs(slowCellular)
        assertTrue("per-probe budget was $perProbe", perProbe > 2_000L)
        val batch = LinkDeadlinePolicy.probeBatchBudgetMs(perProbe)
        assertTrue(
            "the batch must cover a single probe, batch=$batch probe=$perProbe",
            batch >= perProbe
        )
        assertTrue("batch must stay bounded, got $batch", batch <= 9_000L)
        // An unmeasured link keeps the 2600 ms batch the code always used.
        assertEquals(
            2_600L,
            LinkDeadlinePolicy.probeBatchBudgetMs(LinkDeadlinePolicy.httpsProbeTimeoutMs(LinkEvidence.UNKNOWN))
        )
    }

    @Test
    fun `the race delay becomes a real fraction of a slow rtt`() {
        // 60 ms on a 1126 ms link starts the second dial before the first SYN-ACK can arrive.
        val scaled = LinkDeadlinePolicy.raceTryDelayMs(slowCellular, 60)
        assertTrue("scaled delay was $scaled", scaled > 60)
        assertTrue("scaled delay must stay inside the engine range, got $scaled", scaled <= 500)
        // Never below what the user configured.
        assertEquals(500, LinkDeadlinePolicy.raceTryDelayMs(slowCellular, 500))
        // Unmeasured: the configured value is kept as-is.
        assertEquals(60, LinkDeadlinePolicy.raceTryDelayMs(LinkEvidence.UNKNOWN, 60))
    }

    @Test
    fun `the 9999 sentinel in the health store is not treated as a measurement`() {
        val sentinel = NodeHealthRecord(
            profileId = "p",
            networkKey = "n",
            samples = 3,
            latencyEwma = 9999.0
        )
        assertFalse(LinkEvidence.fromHealth(sentinel).known)
        assertEquals(LinkEvidence.UNKNOWN, LinkEvidence.fromHealth(null))
    }

    // ------------------------------------------------------------------
    // MARBLE_LINK_DEADLINE_V134 — merging evidence, and the measurement plane
    // ------------------------------------------------------------------

    /** The link from the second runtime log: a stable tunnel with 267-444 ms verified pings. */
    private val noisyCellular = LinkEvidence(
        rttMs = 444.0,
        tailRttMs = 644.0,
        jitterMs = 90.0,
        lossPercent = 3.0,
        samples = 6
    )

    @Test
    fun `unknown evidence is the identity of a conservative merge`() {
        assertEquals(noisyCellular, LinkEvidence.UNKNOWN.conservativeOf(noisyCellular))
        assertEquals(noisyCellular, noisyCellular.conservativeOf(LinkEvidence.UNKNOWN))
        assertEquals(LinkEvidence.UNKNOWN, LinkEvidence.UNKNOWN.conservativeOf(LinkEvidence.UNKNOWN))
        assertFalse(LinkEvidence.UNKNOWN.conservativeOf(LinkEvidence.UNKNOWN).known)
    }

    @Test
    fun `a conservative merge keeps the slower of two observations`() {
        val fast = LinkEvidence(rttMs = 60.0, tailRttMs = 80.0, jitterMs = 5.0, samples = 9)
        val merged = fast.conservativeOf(noisyCellular)
        assertEquals(noisyCellular.rttMs, merged.rttMs, 0.001)
        assertEquals(noisyCellular.tailRttMs, merged.tailRttMs, 0.001)
        assertEquals(noisyCellular.jitterMs, merged.jitterMs, 0.001)
        assertEquals(noisyCellular.lossPercent, merged.lossPercent, 0.001)
        // Sample count is confidence, not latency: the merge keeps the better-supported figure.
        assertEquals(maxOf(fast.samples, noisyCellular.samples), merged.samples)
        assertTrue(
            "a merged prior must never shorten a deadline below the slower observation",
            LinkDeadlinePolicy.dnsServerTimeoutMs(merged, 0, false) >=
                LinkDeadlinePolicy.dnsServerTimeoutMs(noisyCellular, 0, false)
        )
        assertTrue(
            "and it must lengthen it relative to the fast one",
            LinkDeadlinePolicy.dnsServerTimeoutMs(merged, 0, false) >
                LinkDeadlinePolicy.dnsServerTimeoutMs(fast, 0, false)
        )
    }

    @Test
    fun `a first connect with a prior is not sent back to the legacy constants`() {
        // This is the regression V133 left behind: the per-node record for *this* network is written
        // by the measurements the session is about to make, so the first config of a session had no
        // evidence at all and was emitted with 1350/1650 ms budgets on a 444 ms route.
        val legacy = LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 0, false)
        val withPrior = LinkDeadlinePolicy.dnsServerTimeoutMs(
            LinkEvidence.UNKNOWN.conservativeOf(noisyCellular),
            0,
            false
        )
        assertEquals(LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS, legacy)
        assertTrue("the prior must reach the emitted budget, got $withPrior", withPrior > legacy)
        // Three round trips of the tail is the floor a cold encrypted query needs.
        assertTrue(withPrior >= 3 * noisyCellular.tailRttMs.toLong())
    }

    @Test
    fun `a tuning trial keeps the legacy budget as its floor and grows with the link`() {
        assertEquals(
            4_000L,
            LinkDeadlinePolicy.tuningTrialTimeoutMs(LinkEvidence.UNKNOWN, 4_000L)
        )
        assertEquals(
            2_000L,
            LinkDeadlinePolicy.tuningTrialTimeoutMs(LinkEvidence.UNKNOWN, 2_000L)
        )
        // A 444 ms route with 90 ms of jitter still fits inside the legacy 4 s trial budget. A floor
        // is a floor: a policy that only ever inflates is not derived from evidence.
        assertEquals(
            4_000L,
            LinkDeadlinePolicy.tuningTrialTimeoutMs(noisyCellular, 4_000L)
        )
        val derived = LinkDeadlinePolicy.tuningTrialTimeoutMs(slowCellular, 4_000L)
        assertTrue(
            "a trial on the ~1.1 s route needs more than the legacy 4 s, got $derived",
            derived > 4_000L
        )
        assertTrue(
            "it has to cover four round trips of the tail, got $derived",
            derived >= 4 * slowCellular.tailRttMs.toLong()
        )
        assertTrue(derived <= LinkDeadlinePolicy.MAX_TUNING_TRIAL_MS)
        // And an extreme route is capped, not unbounded.
        val extreme = LinkEvidence(
            rttMs = 2_500.0, tailRttMs = 2_800.0, jitterMs = 200.0, samples = 4
        )
        assertEquals(
            LinkDeadlinePolicy.MAX_TUNING_TRIAL_MS,
            LinkDeadlinePolicy.tuningTrialTimeoutMs(extreme, 4_000L)
        )
    }

    @Test
    fun `an acceleration pass always contains at least one whole trial`() {
        val trial = LinkDeadlinePolicy.tuningTrialTimeoutMs(slowCellular, 4_000L)
        val pass = LinkDeadlinePolicy.tuningPassBudgetMs(trial, 4_000L)
        assertEquals(trial + LinkDeadlinePolicy.TUNING_PASS_SLACK_MS, pass)
        assertTrue(pass <= LinkDeadlinePolicy.MAX_TUNING_PASS_MS)

        // The user's configured tuning budget is a floor, never a ceiling that truncates the pass.
        assertEquals(12_000L, LinkDeadlinePolicy.tuningPassBudgetMs(2_000L, 12_000L))
        // And an extreme link still cannot make a pass unbounded.
        assertEquals(
            LinkDeadlinePolicy.MAX_TUNING_PASS_MS,
            LinkDeadlinePolicy.tuningPassBudgetMs(LinkDeadlinePolicy.MAX_TUNING_PASS_MS, 30_000L)
        )
    }

    @Test
    fun `health evidence carries jitter and loss into the deadline`() {
        val evidence = LinkEvidence.fromHealth(
            NodeHealthRecord(
                profileId = "p",
                networkKey = "n",
                samples = 8,
                latencyEwma = 900.0,
                jitterEwma = 120.0,
                successEwma = 92.0
            )
        )
        assertTrue(evidence.known)
        assertEquals(120.0, evidence.jitterMs, 0.001)
        assertEquals(8.0, evidence.lossPercent, 0.001)
        val calm = LinkEvidence.fromHealth(
            NodeHealthRecord(
                profileId = "p", networkKey = "n", samples = 8, latencyEwma = 900.0
            )
        )
        assertTrue(
            "jitter and loss must lengthen the deadline",
            LinkDeadlinePolicy.dnsServerTimeoutMs(evidence, 0, false) >
                LinkDeadlinePolicy.dnsServerTimeoutMs(calm, 0, false)
        )
    }
}
