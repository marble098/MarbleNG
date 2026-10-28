package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PROBE_TARGET_WINDOWS_V238 — one measurement window per probe target.
 *
 * The report (P0-2) showed the legacy single window losing and mixing evidence: switching probe
 * target cleared the samples already collected, and a returning target interleaved its new
 * samples with the previous target's. These tests pin the replacement's contract.
 */
class RouteSampleWindowsV238Test {

    @Test
    fun `windows of two hosts never mix`() {
        val windows = RouteSampleWindows()
        repeat(6) { windows.add("1.1.1.1", 100 + it, nowMs = it * 1_000L) }
        repeat(4) { windows.add("8.8.8.8", 300 + it, nowMs = it * 1_000L) }

        val cloudflare = windows.outcomes("1.1.1.1", nowMs = 10_000L)
        val google = windows.outcomes("8.8.8.8", nowMs = 10_000L)

        assertEquals(6, cloudflare.size)
        assertTrue(cloudflare.all { it in 100..105 })
        assertEquals(4, google.size)
        assertTrue(google.all { it in 300..303 })
    }

    @Test
    fun `a returning target keeps its window`() {
        val windows = RouteSampleWindows()
        repeat(5) { windows.add("pivot", 200, nowMs = it * 1_000L) }
        // The probe walks to another host and back; the pivot's window must survive.
        windows.add("other", 900, nowMs = 6_000L)
        windows.add("pivot", 210, nowMs = 7_000L)

        val outcomes = windows.outcomes("pivot", nowMs = 8_000L)
        assertEquals("the pre-walk evidence must still be there", 6, outcomes.size)
    }

    @Test
    fun `cold samples are marked and ride along with warm ones`() {
        val windows = RouteSampleWindows()
        windows.add("pivot", 400, nowMs = 0L, cold = true)
        repeat(3) { windows.add("pivot", 100, nowMs = (it + 1) * 1_000L) }

        val (outcomes, cold) = windows.outcomesMarked("pivot", nowMs = 9_000L)
        assertEquals(4, outcomes.size)
        assertEquals(listOf(true, false, false, false), cold)
        assertEquals(400, outcomes.first())
    }

    @Test
    fun `a cold sample is excluded from the summary statistics but not from attempts`() {
        val windows = RouteSampleWindows()
        windows.add("pivot", 4_000, nowMs = 0L, cold = true)
        repeat(9) { windows.add("pivot", 100, nowMs = (it + 1) * 1_000L) }

        val (outcomes, cold) = windows.outcomesMarked("pivot", nowMs = 60_000L)
        val summary = LinkQualityEstimator.summarizeMarked(outcomes, cold)
        assertEquals(10, summary?.attempts)
        // A cold handshake that succeeded is still a success and an attempt — only the latency
        // statistics and the IPDV delta chain must exclude it.
        assertEquals(10, summary?.successes)
        assertEquals("the handshake cost must not be a latency sample", 100, summary?.medianRttMs)
        assertEquals("the handshake must not manufacture jitter", 0, summary?.meanIpdvMs)
    }

    @Test
    fun `windows expire after the ttl`() {
        val windows = RouteSampleWindows(ttlMs = 10_000L)
        windows.add("pivot", 100, nowMs = 0L)
        assertEquals(1, windows.outcomes("pivot", nowMs = 5_000L).size)
        assertTrue(windows.outcomes("pivot", nowMs = 20_000L).isEmpty())
        assertFalse(windows.has("pivot"))
    }

    @Test
    fun `the host count is bounded and the oldest window is evicted`() {
        val windows = RouteSampleWindows(maxHosts = 2)
        windows.add("a", 100, nowMs = 0L)
        windows.add("b", 200, nowMs = 1_000L)
        windows.add("c", 300, nowMs = 2_000L)

        assertEquals(listOf("b", "c"), windows.knownHosts().sorted())
        assertTrue(windows.outcomes("a", nowMs = 3_000L).isEmpty())
        assertEquals(1, windows.outcomes("b", nowMs = 3_000L).size)
    }

    @Test
    fun `clear can drop one target or everything`() {
        val windows = RouteSampleWindows()
        windows.add("a", 100, nowMs = 0L)
        windows.add("b", 200, nowMs = 0L)

        windows.clear("a")
        assertEquals(listOf("b"), windows.knownHosts())

        windows.clear()
        assertTrue(windows.knownHosts().isEmpty())
    }

    @Test
    fun `samples are sanitized on the way in`() {
        val windows = RouteSampleWindows()
        windows.add("pivot", 250_000, nowMs = 0L)
        windows.add("pivot", -3, nowMs = 1_000L)
        val outcomes = windows.outcomes("pivot", nowMs = 2_000L)
        assertTrue("absurd RTTs must be clamped", outcomes.first() <= 10_000)
        assertEquals("a failure stays a failure", -1, outcomes.last())
    }
}
