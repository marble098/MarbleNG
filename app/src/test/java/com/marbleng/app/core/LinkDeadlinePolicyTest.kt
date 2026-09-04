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
    fun `the freedom fragment chain keeps the upstream xtls schedule`() {
        assertEquals(
            LinkDeadlinePolicy.FRAGMENTED_DNS_BASE_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 0, fragmented = true)
        )
        assertEquals(
            LinkDeadlinePolicy.FRAGMENTED_DNS_BASE_MS + LinkDeadlinePolicy.FRAGMENTED_DNS_STEP_MS,
            LinkDeadlinePolicy.dnsServerTimeoutMs(slowCellular, 1, fragmented = true)
        )
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
