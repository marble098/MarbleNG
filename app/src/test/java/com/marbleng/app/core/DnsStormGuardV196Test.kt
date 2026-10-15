package com.marbleng.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsStormGuardV196Test {
    private fun armed(): DnsStormGuard = DnsStormGuard().apply {
        recordDeadlineFailures(DnsStormGuard.STORM_ARM_EVENTS, 0L)
    }

    @Test fun threeFailuresInsideTheWindowArmTheRegime() {
        val guard = armed()
        assertTrue(guard.active(1_000L))
        assertTrue(guard.armMs(1_000L) != null)
    }

    @Test fun twoConsecutiveProvenAnswersStandTheRegimeDown() {
        val guard = armed()
        guard.recordProvenAnswer(1_000L)
        guard.recordProvenAnswer(2_000L)
        assertFalse("a recovered network must stop racing at once", guard.active(3_000L))
    }

    @Test fun oneLuckyAnswerDuringARealStormDoesNotDisarmIt() {
        val guard = DnsStormGuard()
        guard.recordDeadlineFailures(12, 0L)
        guard.recordProvenAnswer(1_000L)
        assertTrue(guard.active(2_000L))
    }

    @Test fun aProvenAnswerOnAQuietGuardIsHarmless() {
        val guard = DnsStormGuard()
        guard.recordProvenAnswer(1_000L)
        assertFalse(guard.active(1_000L))
        assertTrue(guard.eventsPerMinute(1_000L) == 0.0)
    }

    @Test fun theRegimeStillExpiresByClockWhenNothingAnswersAtAll() {
        val guard = armed()
        assertTrue(guard.active(1_000L))
        assertFalse(guard.active(DnsStormGuard.STAND_DOWN_WINDOW_MS + 1_000L))
    }
}
