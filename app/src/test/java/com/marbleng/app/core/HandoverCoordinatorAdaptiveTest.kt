package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for soft session migration (MARBLE_SMART_RANK_V90).
 *
 * The adaptive scorer decides WHEN and TO WHOM a live session should migrate;
 * the make-before-break coordinator decides HOW — always a soft migration (start challenger, move
 * new flows, drain the old route), never a raw reconnect. Catastrophic degradation bypasses the
 * anti-flap dwell/cooldown via forceBegin.
 */
class HandoverCoordinatorAdaptiveTest {

    private fun scorer() = AdaptiveAegisScorer(dwellTimeMs = 90_000L) { 0L }

    @Test
    fun degradedRouteMigratesAsMakeBeforeBreakNotReconnect() {
        val coordinator = HandoverCoordinator()
        val adaptive = scorer()
        adaptive.recordScore("fp", "challenger", 95.0)
        coordinator.setAdaptiveScorer(adaptive, "fp")

        val migration = coordinator.consultAdaptiveMigration(
            activeRoute = "active",
            quality = AdaptiveAegisScorer.Quality(rttMs = 600, lossPercent = 25.0, stressFlag = true),
            candidateScores = mapOf("challenger" to 95.0),
            nowMs = 1_000_000L // no prior switch: dwell is already satisfied
        )

        assertFalse(migration.decision.keep)
        assertEquals("challenger", migration.target)
        assertEquals(HandoverCoordinator.Action.START_CHALLENGER, migration.action)
        assertEquals(HandoverCoordinator.State.WARMING, coordinator.snapshot().state)
    }

    @Test
    fun catastrophicDegradationBypassesAntiFlapDwell() {
        val coordinator = HandoverCoordinator()
        val adaptive = scorer()
        adaptive.recordScore("fp", "challenger", 95.0)
        coordinator.setAdaptiveScorer(adaptive, "fp")

        val migration = coordinator.consultAdaptiveMigration(
            activeRoute = "active",
            quality = AdaptiveAegisScorer.Quality(lossPercent = 60.0),
            candidateScores = mapOf("challenger" to 95.0),
            nowMs = 1_000_000L
        )

        assertTrue(migration.decision.catastrophic)
        assertEquals("challenger", migration.target)
        assertEquals(HandoverCoordinator.Action.START_CHALLENGER, migration.action)
    }

    @Test
    fun uncertainMeasurementHoldsAndReturnsNoAction() {
        val coordinator = HandoverCoordinator()
        val adaptive = scorer()
        coordinator.setAdaptiveScorer(adaptive, "fp")

        val migration = coordinator.consultAdaptiveMigration(
            activeRoute = "active",
            quality = AdaptiveAegisScorer.Quality(uncertain = true),
            candidateScores = emptyMap(),
            nowMs = 1_000_000L
        )

        assertTrue(migration.decision.keep)
        assertTrue(migration.decision.uncertain)
        assertEquals(HandoverCoordinator.Action.NONE, migration.action)
        assertEquals(HandoverCoordinator.State.IDLE, coordinator.snapshot().state)
    }

    @Test
    fun noScorerConfiguredIsASafeHold() {
        val coordinator = HandoverCoordinator()
        val migration = coordinator.consultAdaptiveMigration(
            activeRoute = "active",
            quality = AdaptiveAegisScorer.Quality(),
            candidateScores = emptyMap()
        )
        assertTrue(migration.decision.keep)
        assertEquals(HandoverCoordinator.Action.NONE, migration.action)
    }
}
