package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PingBudgetTest {
    @Test
    fun everyOfferedChoiceSurvivesItsOwnClamp() {
        PingBudget.TIMEOUT_CHOICES.forEach { assertEquals(it, PingBudget.timeoutSec(it)) }
        PingBudget.SAMPLE_CHOICES.forEach { assertEquals(it, PingBudget.samples(it)) }
        PingBudget.CONCURRENCY_CHOICES.forEach { assertEquals(it, PingBudget.concurrency(it)) }
    }

    @Test
    fun illegalValuesAreClampedIntoTheLegalRange() {
        assertEquals(PingBudget.TIMEOUT_MIN_SEC, PingBudget.timeoutSec(0))
        assertEquals(PingBudget.TIMEOUT_MIN_SEC, PingBudget.timeoutSec(-9))
        assertEquals(PingBudget.TIMEOUT_MAX_SEC, PingBudget.timeoutSec(9_000))
        assertEquals(PingBudget.SAMPLES_MIN, PingBudget.samples(0))
        assertEquals(PingBudget.SAMPLES_MAX, PingBudget.samples(400))
        assertEquals(PingBudget.CONCURRENCY_MIN, PingBudget.concurrency(0))
        assertEquals(PingBudget.CONCURRENCY_MAX, PingBudget.concurrency(4_096))
    }

    @Test
    fun tenSecondsPerServerIsAllowed() {
        assertEquals(10, PingBudget.timeoutSec(10))
        assertEquals(10_000, AppSettings(pingTimeoutSec = 10).pingTimeoutMs())
        assertEquals(5_000, AppSettings(pingTimeoutSec = 5).pingTimeoutMs())
    }

    @Test
    fun settingsAccessorsAlwaysReturnLegalBudgets() {
        val hostile = AppSettings(pingTimeoutSec = -3, pingSamples = 99, pingConcurrency = 0)
        assertEquals(PingBudget.TIMEOUT_MIN_SEC * 1_000, hostile.pingTimeoutMs())
        assertEquals(PingBudget.SAMPLES_MAX, hostile.pingSampleCount())
        assertEquals(PingBudget.CONCURRENCY_MIN, hostile.pingWorkers())
    }

    @Test
    fun perServerBudgetGrowsWithTimeoutAndSamples() {
        val one = PingBudget.perServerBudgetMs(5, 1)
        val three = PingBudget.perServerBudgetMs(5, 3)
        val longer = PingBudget.perServerBudgetMs(10, 3)
        assertTrue("more samples must cost more time", three > one)
        assertTrue("a longer timeout must cost more time", longer > three)

        assertEquals(5 * 1_000L + 750L, one)
    }

    @Test
    fun defaultsAreTheProductDefaults() {
        val defaults = AppSettings()
        assertEquals(5, defaults.pingTimeoutSec)
        assertEquals(3, defaults.pingSamples)
        assertEquals(16, defaults.pingConcurrency)
    }
}
