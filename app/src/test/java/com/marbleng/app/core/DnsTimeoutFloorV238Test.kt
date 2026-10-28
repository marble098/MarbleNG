package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_DNS_DEADLINE_V238 — the resolver budget contract the Bug Finder report (P1) asked for.
 *
 * A DoH query through a cold tunnel is a multi-stage TLS handshake; the legacy 1 350 ms floor
 * turned every first query into a timeout, demoted healthy resolvers, and re-armed the storm
 * guard in a loop. The floor now starts where a proxied handshake can actually finish, and thin
 * evidence never scales a deadline above that floor.
 */
class DnsTimeoutFloorV238Test {

    private val slowCellular = LinkEvidence(
        rttMs = 1_126.0,
        tailRttMs = 1_326.0,
        jitterMs = 100.0,
        lossPercent = 2.0,
        samples = 12
    )

    @Test
    fun `the floors are where a proxied handshake can finish`() {
        assertEquals(4_000L, LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS)
        assertEquals(4_500L, LinkDeadlinePolicy.SECONDARY_DNS_FLOOR_MS)
    }

    @Test
    fun `an unmeasured link gets exactly the floors`() {
        assertEquals(
            4_000L,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 0, fragmented = false)
        )
        assertEquals(
            4_500L,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 1, fragmented = false)
        )
        assertEquals(
            4_750L,
            LinkDeadlinePolicy.dnsServerTimeoutMs(LinkEvidence.UNKNOWN, 2, fragmented = false)
        )
    }

    @Test
    fun `thin evidence never scales above the floor`() {
        // One cold 4 141 ms live sample must not inflate every deadline to the ceiling —
        // the exact defect that produced the first-query storm. The fallback ladder's step
        // spacing stays, but the link's jitter/tail terms are ignored.
        val thin = LinkEvidence(
            rttMs = 4_141.0,
            tailRttMs = 4_141.0,
            jitterMs = 900.0,
            lossPercent = 0.0,
            samples = 1
        )
        (1 until LinkDeadlinePolicy.MIN_DNS_EVIDENCE_SAMPLES).forEach { sampleCount ->
            val evidence = thin.copy(samples = sampleCount)
            assertEquals(
                "samples=$sampleCount",
                LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS,
                LinkDeadlinePolicy.dnsServerTimeoutMs(evidence, 0, fragmented = false)
            )
            assertEquals(
                "samples=$sampleCount",
                LinkDeadlinePolicy.SECONDARY_DNS_FLOOR_MS + LinkDeadlinePolicy.SECONDARY_DNS_STEP_MS,
                LinkDeadlinePolicy.dnsServerTimeoutMs(evidence, 1, fragmented = false)
            )
        }
    }

    @Test
    fun `real evidence scales within the floor and the ceiling`() {
        val primary = LinkDeadlinePolicy.dnsServerTimeoutMs(slowCellular, 0, fragmented = false)
        val secondary = LinkDeadlinePolicy.dnsServerTimeoutMs(slowCellular, 1, fragmented = false)
        assertTrue("primary=$primary", primary >= LinkDeadlinePolicy.PRIMARY_DNS_FLOOR_MS)
        assertTrue("secondary=$secondary", secondary >= LinkDeadlinePolicy.SECONDARY_DNS_FLOOR_MS)
        assertTrue(primary <= LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS)
        assertTrue(secondary <= LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS)
        assertTrue("the fallback ladder grows", secondary > primary)

        val minimum = 3 * slowCellular.tailRttMs.toLong()
        assertTrue(
            "a real budget survives three tail round trips ($minimum)",
            primary >= minimum
        )
    }

    @Test
    fun `resolver demotion probation is short enough to recover`() {
        // The report watched a demotion outlive its cause by half an hour.
        assertEquals(15L * 60_000L, ResolverEvidencePolicy.DEMOTE_TTL_MS)
        assertTrue(
            "probation shorter than before",
            ResolverEvidencePolicy.DEMOTE_TTL_MS < 30L * 60_000L
        )
    }
}
