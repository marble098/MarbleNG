package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PATH_MTU_STABILITY_V133 regressions.
 *
 * The attached runtime log showed the userspace MTU alternating between 1360 and 1400 between
 * sessions, and `TURBO live-inconclusive-backoff` storms that were fed by it: every PMTU sample was
 * committed to the learned store, and `activeMtu > pmtu` forced a full transport re-measurement on
 * every socket rotation. These tests pin the corroboration rule that stops both.
 */
class PathMtuPolicyTest {

    @Test
    fun `a single sample is never committed`() {
        val decision = PathMtuPolicy.observe(
            state = PathMtuPolicy.State(),
            observedMtu = 1360,
            activeMtu = 1400,
            nowMs = 0L
        )
        assertNull(decision.commitMtu)
        assertFalse(decision.requestTune)
        assertEquals(1, decision.state.repeats)
    }

    @Test
    fun `an alternating signal never commits either value`() {
        var state = PathMtuPolicy.State()
        repeat(20) { index ->
            val observed = if (index % 2 == 0) 1400 else 1360
            val decision = PathMtuPolicy.observe(state, observed, 1400, index * 1_000L)
            state = decision.state
            assertNull("tick $index must not commit", decision.commitMtu)
            assertFalse(decision.requestTune)
        }
    }

    @Test
    fun `a corroborated drop is committed and requests a re-measurement`() {
        var state = PathMtuPolicy.State()
        val first = PathMtuPolicy.observe(state, 1360, 1400, 0L)
        state = first.state
        val second = PathMtuPolicy.observe(state, 1360, 1400, 1_000L)
        assertEquals(1360, second.commitMtu)
        assertTrue(second.requestTune)
        assertEquals(1360, second.state.committed)
    }

    @Test
    fun `a small difference is socket rotation and never forces a re-measurement`() {
        var state = PathMtuPolicy.State()
        state = PathMtuPolicy.observe(state, 1392, 1400, 0L).state
        val decision = PathMtuPolicy.observe(state, 1392, 1400, 1_000L)
        assertEquals(1392, decision.commitMtu)
        assertFalse(
            "an 8-byte difference must not spend link capacity on a re-measurement",
            decision.requestTune
        )
    }

    @Test
    fun `the learned value is not re-committed on every tick`() {
        var state = PathMtuPolicy.State()
        state = PathMtuPolicy.observe(state, 1360, 1400, 0L).state
        state = PathMtuPolicy.observe(state, 1360, 1400, 1_000L).state
        assertEquals(1360, state.committed)

        val again = PathMtuPolicy.observe(state, 1360, 1400, 2_000L)
        assertNull(again.commitMtu)
        assertEquals("unchanged", again.reason)
    }

    @Test
    fun `recovery upward needs the same corroboration and the same rate limit`() {
        var state = PathMtuPolicy.State()
        state = PathMtuPolicy.observe(state, 1360, 1400, 0L).state
        state = PathMtuPolicy.observe(state, 1360, 1400, 1_000L).state

        val immediate = PathMtuPolicy.observe(state, 1400, 1360, 2_000L)
        assertNull(immediate.commitMtu)
        val corroborated = PathMtuPolicy.observe(immediate.state, 1400, 1360, 3_000L)
        assertNull(
            "the commit interval must be respected in both directions",
            corroborated.commitMtu
        )

        val later = PathMtuPolicy.observe(
            corroborated.state, 1400, 1360, PathMtuPolicy.MIN_COMMIT_INTERVAL_MS + 4_000L
        )
        assertEquals(1400, later.commitMtu)
        assertFalse("growing the MTU is never a degradation", later.requestTune)
    }

    @Test
    fun `out of range telemetry is ignored`() {
        val state = PathMtuPolicy.State(committed = 1400)
        listOf(0, 576, 1279, 9001, 65_535).forEach { bogus ->
            val decision = PathMtuPolicy.observe(state, bogus, 1400, 0L)
            assertNull(decision.commitMtu)
            assertEquals("out-of-range", decision.reason)
            assertEquals(state, decision.state)
        }
    }

    @Test
    fun `stability compares within the rotation tolerance`() {
        assertTrue(PathMtuPolicy.isStable(1400, 1392))
        assertFalse(PathMtuPolicy.isStable(1400, 1360))
        assertFalse(PathMtuPolicy.isStable(0, 1400))
    }
}
