package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_CONFIG_SUPERSET_V165 — a config refusal is a state, so repeating it must cost nothing
 * and must not bury the one line that explains a dead tunnel.
 *
 * The reported session produced 31 identical `profile-preflight-rejected` rows in ten seconds: two
 * profiles, re-handed by auto-reconnect every few hundred milliseconds, each one promoted a
 * foreground notification, refused, tore down. Nothing about the *state* was news after the first
 * row — and the rows that mattered (the DNS demotions, the process exits, the `stressed=true` TCP
 * sample) were somewhere under that pile.
 *
 * The guard is also deliberately **not** a retry latch: it never decides whether a connection is
 * attempted, only whether the same refusal is worth an event again. That boundary is what these
 * tests hold.
 */
class ConfigBlockGuardV165Test {

    private var clock = 1_000_000L
    private val guard = ConfigBlockGuard { clock }

    @Test
    fun theFirstRefusalIsAlwaysReported() {
        val decision = guard.observe("p1", "Unsupported VLESS • pick a server with TLS/REALITY")
        assertTrue(decision.report)
        assertEquals(1, decision.attempts)
        assertEquals(0, decision.suppressedBefore)
        assertEquals(ConfigBlockGuard.BASE_QUIET_MS, decision.quietForMs)
    }

    @Test
    fun repeatsOfTheSameRefusalFoldIntoTheOneThatReported() {
        val reason = "Unsupported VLESS • pick a server with TLS/REALITY"
        assertTrue(guard.observe("p1", reason).report)
        val folded = List(9) {
            guard.observe("p1", reason).also { clock += 200 }
        }
        folded.forEachIndexed { index, decision ->
            assertFalse("repeat ${index + 2} must not report", decision.report)
        }
        // Ten observations, nine of them folded into the row that reported.
        assertEquals(10, folded.last().attempts)
        assertEquals(9, folded.last().suppressedBefore)
        val observations = Regex("observations=(\\d+)").find(guard.summary())!!.groupValues[1].toInt()
        assertEquals(10, observations)
    }

    @Test
    fun aDifferentReasonForTheSameProfileIsNewsImmediately() {
        assertTrue(guard.observe("p1", "first reason").report)
        assertFalse(guard.observe("p1", "first reason").report)
        assertTrue(guard.observe("p1", "second reason").report)
        // …and the two reasons keep independent windows.
        assertFalse(guard.observe("p1", "first reason").report)
        val second = guard.observe("p1", "second reason")
        assertFalse(second.report)
        assertEquals(2, second.attempts)
    }

    @Test
    fun theQuietWindowDoublesPerRepeatAndIsCapped() {
        val reason = "Xray rejected the config"
        assertEquals(ConfigBlockGuard.BASE_QUIET_MS, guard.observe("p1", reason).quietForMs)
        // One millisecond inside the window: folded, and the next caller gets twice the silence.
        clock += ConfigBlockGuard.BASE_QUIET_MS - 1
        val second = guard.observe("p1", reason)
        assertFalse(second.report)
        assertEquals(ConfigBlockGuard.BASE_QUIET_MS shl 1, second.quietForMs)
        // Outlive the doubled window and it reports again.
        clock += ConfigBlockGuard.BASE_QUIET_MS shl 1
        assertTrue(guard.observe("p1", reason).report)
        // The cap is what keeps a permanently broken profile honest: it must resurface.
        repeat(12) {
            clock += 10
            guard.observe("p1", reason)
        }
        assertEquals(ConfigBlockGuard.MAX_QUIET_MS, guard.observe("p1", reason).quietForMs)
    }

    @Test
    fun isQuietOnlyReportsWhetherTheSameRefusalWouldBeNews() {
        val reason = "plaintext-prohibited"
        assertFalse(guard.isQuiet("p1", reason))
        guard.observe("p1", reason)
        assertTrue(guard.isQuiet("p1", reason))
        assertFalse(guard.isQuiet("p2", reason))
        clock += ConfigBlockGuard.BASE_QUIET_MS + 1
        assertFalse(guard.isQuiet("p1", reason))
    }

    @Test
    fun aChangeTheUserCanSeeClearsTheSilence() {
        val reason = "Unsupported VLESS • pick a server with TLS/REALITY"
        assertTrue(guard.observe("p1", reason).report)
        assertFalse(guard.observe("p1", reason).report)
        guard.reset("p1")
        assertTrue("after a settings change the user must get their feedback back", guard.observe("p1", reason).report)

        guard.observe("p2", reason)
        guard.observe("p2", reason)
        guard.reset()
        assertEquals(0, guard.trackedStates())
    }

    @Test
    fun reasonsAreComparedTheWayTheLogReadsThem() {
        assertTrue(guard.observe("p1", "  Unsupported VLESS  ").report)
        assertFalse(guard.observe("p1", "unsupported vless").report)
    }

    @Test
    fun summaryIsCompactAndEmptyFriendly() {
        assertEquals("configBlock=none", guard.summary())
        guard.observe("p1", "reason one")
        guard.observe("p1", "reason one")
        guard.observe("p2", "reason two")
        val line = guard.summary()
        assertTrue(line, line.startsWith("configBlock=profiles=2,observations=3,suppressed=1"))
        assertTrue(line, line.contains("reason one") && line.contains("reason two"))
    }

    @Test
    fun aRefusalNeverDecidesAnythingAboutConnecting() {
        // The guard is a reporting filter. It must be safe to observe the same refusal forever,
        // including across a reconnect schedule that keeps running on its own.
        repeat(500) { guard.observe("p1", "same") }
        assertEquals(1, guard.trackedStates())
        assertTrue(guard.observe("p1", "other").report)
    }
}
