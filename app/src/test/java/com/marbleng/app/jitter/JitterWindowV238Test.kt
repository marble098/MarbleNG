package com.marbleng.app.jitter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SMART_JITTER_V238 — the Smart Jitter Reducer's own measurement window.
 *
 * The report (P0-2) showed the legacy probe handing a verdict built from a single cold TLS
 * handshake to the switch policy: the handshake cost read as catastrophic latency and jitter, and
 * the route was condemned (or exonerated) on weather. Two rules are pinned here: a cold sample
 * counts as an attempt but never as a latency observation, and no verdict exists before
 * [JitterWindow.DEFAULT_MIN_SAMPLES] valid samples.
 */
class JitterWindowV238Test {

    @Test
    fun `a lone cold sample has no verdict`() {
        val window = JitterWindow()
        window.add(rttMs = 4_000.0, ok = true, cold = true)
        assertNull("one cold handshake is not a path measurement", window.summarize())
    }

    @Test
    fun `fewer valid samples than the minimum has no verdict`() {
        val window = JitterWindow()
        repeat(JitterWindow.DEFAULT_MIN_SAMPLES - 1) {
            window.add(rttMs = 100.0, ok = true, cold = false)
        }
        assertNull(window.summarize())
    }

    @Test
    fun `the minimum of valid samples produces a verdict`() {
        val window = JitterWindow()
        repeat(JitterWindow.DEFAULT_MIN_SAMPLES) {
            window.add(rttMs = 100.0, ok = true, cold = false)
        }
        val snapshot = window.summarize()
        assertNotNull(snapshot)
        assertEquals(JitterWindow.DEFAULT_MIN_SAMPLES, snapshot?.validSamples)
    }

    @Test
    fun `cold samples count as attempts but stay out of the statistics`() {
        val window = JitterWindow()
        window.add(rttMs = 4_000.0, ok = true, cold = true)
        repeat(JitterWindow.DEFAULT_MIN_SAMPLES) {
            window.add(rttMs = 100.0, ok = true, cold = false)
        }
        val snapshot = window.summarize()
        assertNotNull(snapshot)
        assertEquals(
            "the cold handshake is an attempt",
            JitterWindow.DEFAULT_MIN_SAMPLES + 1,
            snapshot?.samples
        )
        assertEquals(
            "but never a latency observation",
            JitterWindow.DEFAULT_MIN_SAMPLES,
            snapshot?.validSamples
        )
        assertTrue(
            "the handshake cost must not move the mean",
            (snapshot?.rttMeanMs ?: 9e9) < 150.0
        )
    }

    @Test
    fun `a failed probe is an attempt and feeds the loss percent`() {
        val window = JitterWindow()
        repeat(8) { window.add(rttMs = 100.0, ok = true) }
        window.add(rttMs = 0.0, ok = false)
        val snapshot = window.summarize()
        assertNotNull(snapshot)
        assertEquals(9, snapshot?.samples)
        assertEquals("the failure is not a valid sample", 8, snapshot?.validSamples)
        assertEquals(100.0 * 1.0 / 9.0, snapshot?.lossPercent ?: 0.0, 0.01)
    }

    @Test
    fun `the window rolls at its capacity`() {
        val window = JitterWindow()
        repeat(JitterWindow.DEFAULT_CAPACITY + 10) { index ->
            window.add(rttMs = 100.0 + index, ok = true)
        }
        val snapshot = window.summarize()
        assertEquals(JitterWindow.DEFAULT_CAPACITY, snapshot?.samples)
        assertEquals("only the newest samples remain", 139.0, snapshot?.lastRttMs ?: 0.0, 0.01)
        assertEquals(JitterWindow.DEFAULT_CAPACITY, snapshot?.series?.size)
    }

    @Test
    fun `the cold flag breaks the jitter pairing chain`() {
        val window = JitterWindow()
        window.add(rttMs = 4_000.0, ok = true, cold = true)
        repeat(JitterWindow.DEFAULT_MIN_SAMPLES) {
            window.add(rttMs = 100.0, ok = true, cold = false)
        }
        val snapshot = window.summarize()
        assertNotNull(snapshot)
        assertTrue(
            "4 000 → 100 must not be read as 3 900 ms of jitter, got ${snapshot?.jitterMs}",
            (snapshot?.jitterMs ?: 9e9) < 50.0
        )
    }

    @Test
    fun `a custom minimum is honored`() {
        val window = JitterWindow()
        repeat(4) { window.add(rttMs = 100.0) }
        assertNull(window.summarize(minSamples = 8))
        assertNotNull(window.summarize(minSamples = 4))
    }

    @Test
    fun `enough failures evict the valid samples and end the verdict`() {
        val window = JitterWindow()
        repeat(8) { window.add(rttMs = 100.0, ok = true, cold = false) }
        assertEquals(0.0, window.cumulativeLossPercent(), 0.001)
        // A full ring of failures pushes the eight valid samples out: the tunnel is down, and
        // the honest answer is "no measurement", not "a verdict with stale data".
        repeat(JitterWindow.DEFAULT_CAPACITY) { window.add(rttMs = 0.0, ok = false, cold = false) }
        assertTrue(window.cumulativeLossPercent() > 70.0)
        assertNull(window.summarize())
    }
}
