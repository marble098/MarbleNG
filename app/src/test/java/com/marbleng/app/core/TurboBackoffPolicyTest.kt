package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_TURBO_BACKOFF_V133 regressions.
 *
 * The attached runtime log showed `TURBO live-inconclusive-backoff` with 600 s, 1200 s and 1800 s
 * windows repeating for the whole session. Three defects produced that: a pass that could not
 * measure anything escalated the *transport* backoff, nothing ever decayed the streak, and nothing
 * could release the timer when the route recovered. Each test below pins one of those fixes.
 */
class TurboBackoffPolicyTest {

    private val base = 600_000L
    private val max = 1_800_000L

    @Test
    fun `transport evidence escalates up to the ceiling`() {
        var state = TurboBackoffPolicy.State()
        val first = TurboBackoffPolicy.inconclusive(state, 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max)
        assertEquals(600_000L, first.backoffMs)
        assertTrue(first.escalated.not())

        state = first.state
        val second = TurboBackoffPolicy.inconclusive(state, 1_000L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max)
        assertEquals(1_200_000L, second.backoffMs)
        assertTrue(second.escalated)

        state = second.state
        val third = TurboBackoffPolicy.inconclusive(state, 2_000L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max)
        assertEquals(1_800_000L, third.backoffMs)

        // The ceiling holds: a fourth failure must not grow past it.
        val fourth = TurboBackoffPolicy.inconclusive(
            third.state, 3_000L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
        )
        assertEquals(1_800_000L, fourth.backoffMs)
    }

    @Test
    fun `an unavailable probe never escalates the transport backoff`() {
        var state = TurboBackoffPolicy.State()
        repeat(5) { index ->
            val outcome = TurboBackoffPolicy.inconclusive(
                state = state,
                nowMs = index * 1_000L,
                cause = TurboBackoffPolicy.Cause.PROBE_UNAVAILABLE,
                baseMs = base,
                maxMs = max
            )
            assertEquals(0, outcome.state.streak)
            assertFalse(outcome.escalated)
            assertEquals(TurboBackoffPolicy.PROBE_UNAVAILABLE_RETRY_MS, outcome.backoffMs)
            state = outcome.state
        }
    }

    @Test
    fun `a pass that never ran cannot escalate the transport backoff`() {
        // MARBLE_TURBO_BACKOFF_V134 — `report == null` is not an unhealthy verdict, it is no verdict:
        // the tuner vetoed itself on the thermal budget, found fewer than two methods to compare, or
        // threw. The service used to answer TRANSPORT_INCONCLUSIVE for all of them by construction.
        var state = TurboBackoffPolicy.State()
        repeat(4) { index ->
            val outcome = TurboBackoffPolicy.inconclusive(
                state = state,
                nowMs = index * 1_000L,
                cause = TurboBackoffPolicy.Cause.NOT_ATTEMPTED,
                baseMs = base,
                maxMs = max
            )
            assertEquals(0, outcome.state.streak)
            assertFalse(outcome.escalated)
            assertEquals(TurboBackoffPolicy.NOT_ATTEMPTED_RETRY_MS, outcome.backoffMs)
            assertEquals("pass-not-attempted-no-escalation", outcome.reason)
            state = outcome.state
        }
        // A vetoed pass must not consume the escalation budget either: the next genuine transport
        // verdict still starts at the base window instead of inheriting a streak it did not earn.
        val transport = TurboBackoffPolicy.inconclusive(
            state,
            10_000L,
            TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE,
            base,
            max
        )
        assertEquals(1, transport.state.streak)
        assertEquals(base, transport.backoffMs)
    }

    @Test
    fun `the three causes are distinguishable in the recorded reason`() {
        val state = TurboBackoffPolicy.State()
        val notAttempted = TurboBackoffPolicy.inconclusive(
            state, 0L, TurboBackoffPolicy.Cause.NOT_ATTEMPTED, base, max
        )
        val probeUnavailable = TurboBackoffPolicy.inconclusive(
            state, 0L, TurboBackoffPolicy.Cause.PROBE_UNAVAILABLE, base, max
        )
        val transport = TurboBackoffPolicy.inconclusive(
            state, 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
        )
        assertEquals("pass-not-attempted-no-escalation", notAttempted.reason)
        assertEquals("probe-unavailable-no-escalation", probeUnavailable.reason)
        assertEquals("transport-inconclusive-streak-1", transport.reason)
        assertTrue(
            "only real transport evidence may escalate",
            !notAttempted.escalated && !probeUnavailable.escalated
        )
        assertTrue(
            "a non-escalating cause must not arm the 1800 s ceiling",
            notAttempted.backoffMs < TurboBackoffPolicy.NOT_ATTEMPTED_RETRY_MS + 1 &&
                notAttempted.backoffMs < base
        )
    }

    @Test
    fun `a stale escalation decays instead of lasting the whole session`() {
        // Three failures in a row reach the 1800 s ceiling.
        var state = TurboBackoffPolicy.State()
        repeat(3) {
            state = TurboBackoffPolicy.inconclusive(
                state, 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
            ).state
        }
        assertEquals(3, state.streak)

        // One half-life of quiet forgives half the streak (floored, so a streak of three drops to
        // one); three half-lives forgive all of it.
        assertEquals(
            1,
            TurboBackoffPolicy.decayedStreak(3, TurboBackoffPolicy.STREAK_HALF_LIFE_MS)
        )
        assertEquals(
            2,
            TurboBackoffPolicy.decayedStreak(4, TurboBackoffPolicy.STREAK_HALF_LIFE_MS)
        )
        assertEquals(
            0,
            TurboBackoffPolicy.decayedStreak(3, TurboBackoffPolicy.STREAK_HALF_LIFE_MS * 3)
        )

        // A new failure after three quiet half-lives starts from one, not from four.
        val afterQuiet = TurboBackoffPolicy.inconclusive(
            state = state,
            nowMs = TurboBackoffPolicy.STREAK_HALF_LIFE_MS * 3,
            cause = TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE,
            baseMs = base,
            maxMs = max
        )
        assertEquals(1, afterQuiet.state.streak)
        assertEquals(600_000L, afterQuiet.backoffMs)

        // One quiet half-life leaves one step of escalation (3 >> 1 == 1), so the next failure lands
        // on 1200 s instead of restarting at the ceiling or staying there.
        val partlyDecayed = TurboBackoffPolicy.inconclusive(
            state = state,
            nowMs = TurboBackoffPolicy.STREAK_HALF_LIFE_MS,
            cause = TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE,
            baseMs = base,
            maxMs = max
        )
        assertEquals(2, partlyDecayed.state.streak)
        assertEquals(1_200_000L, partlyDecayed.backoffMs)
    }

    @Test
    fun `a recovered route cancels the timer immediately`() {
        val armed = TurboBackoffPolicy.inconclusive(
            TurboBackoffPolicy.State(), 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
        ).state
        assertTrue(TurboBackoffPolicy.isWaiting(armed, 10_000L))

        val released = TurboBackoffPolicy.observeRoute(armed, 10_000L, recovered = true)
        assertFalse(TurboBackoffPolicy.isWaiting(released, 10_000L))
        assertEquals(0L, released.untilMs)
        assertEquals(1, released.earlyReleases)
    }

    @Test
    fun `early release is bounded so a flapping link cannot re-arm forever`() {
        var state = TurboBackoffPolicy.State()
        repeat(TurboBackoffPolicy.MAX_EARLY_RELEASES + 3) {
            state = TurboBackoffPolicy.inconclusive(
                state, 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
            ).state
            state = TurboBackoffPolicy.observeRoute(state, 1L, recovered = true)
        }
        assertEquals(TurboBackoffPolicy.MAX_EARLY_RELEASES, state.earlyReleases)
        // Once the budget is spent the timer is left alone.
        assertTrue(TurboBackoffPolicy.isWaiting(state, 1L))
    }

    @Test
    fun `an unrecovered route never releases the timer`() {
        val armed = TurboBackoffPolicy.inconclusive(
            TurboBackoffPolicy.State(), 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
        ).state
        assertEquals(armed, TurboBackoffPolicy.observeRoute(armed, 5_000L, recovered = false))
        assertTrue(TurboBackoffPolicy.isWaiting(armed, 5_000L))
    }

    @Test
    fun `a conclusive pass clears streak and timer`() {
        val armed = TurboBackoffPolicy.inconclusive(
            TurboBackoffPolicy.State(), 0L, TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE, base, max
        ).state
        val cleared = TurboBackoffPolicy.succeeded(armed)
        assertTrue(cleared.idle)
        assertEquals(0, cleared.earlyReleases)
    }
}
