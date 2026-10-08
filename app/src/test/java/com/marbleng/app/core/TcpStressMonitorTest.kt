package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpStressMonitorTest {
    private val t0 = 1_000_000L

    private fun sample(
        monitor: TcpStressMonitor,
        at: Long,
        stressed: Boolean,
        retransmitRate: Double = if (stressed) 0.30 else 0.0,
        lossRate: Double = if (stressed) 0.30 else 0.0,
        mssRatio: Double = 1.0,
        currentMtu: Int = 1_500
    ): TcpStressMonitor.TuningDecision {
        monitor.observe(
            retransmitRate = retransmitRate,
            lossRate = lossRate,
            mssRatio = mssRatio,
            unackedSegments = if (stressed) 3 else 0,
            stressed = stressed,
            rttMs = if (stressed) 500 else 100,
            profileId = "profile",
            networkKey = "wifi:test",
            nowMs = at,
            currentMtu = currentMtu
        )
        return monitor.evaluate(at)
    }

    @Test
    fun aSingleNoisySampleCannotImmediatelyReduceMtu() {
        val monitor = TcpStressMonitor()
        val decision = sample(
            monitor, t0, stressed = true,
            retransmitRate = 1.0, lossRate = 0.5, mssRatio = 0.20
        )
        assertFalse(decision.shouldReduceMtu)
        assertEquals(TcpStressMonitor.MtuLevel.FULL, decision.recommendedLevel)
    }

    @Test
    fun evenSeverePressureMustPersistAcrossSamplesAndTimeBeforeStepDown() {
        val monitor = TcpStressMonitor()
        var decision = sample(monitor, t0, stressed = true)
        assertFalse(decision.shouldReduceMtu)
        for (offset in 2_000L..8_000L step 2_000L) {
            decision = sample(monitor, t0 + offset, stressed = true)
            assertFalse("reduced before severe evidence persisted $offset ms", decision.shouldReduceMtu)
        }
        decision = sample(monitor, t0 + 10_000L, stressed = true)
        assertTrue(decision.shouldReduceMtu)
        assertEquals(TcpStressMonitor.MtuLevel.CELLULAR, decision.recommendedLevel)
        assertEquals(TcpStressMonitor.TuningDecision.Urgency.CRITICAL, decision.urgency)
    }

    @Test
    fun ordinaryPressureNeedsThreeSamplesAndThirtyUninterruptedSeconds() {
        val monitor = TcpStressMonitor()
        var decision = sample(monitor, t0, stressed = true, retransmitRate = 0.10, lossRate = 0.08)
        assertFalse(decision.shouldReduceMtu)
        sample(monitor, t0 + 10_000L, stressed = true, retransmitRate = 0.10, lossRate = 0.08)
        decision = sample(monitor, t0 + 20_000L, stressed = true, retransmitRate = 0.10, lossRate = 0.08)
        assertFalse(decision.shouldReduceMtu)
        decision = sample(monitor, t0 + 29_000L, stressed = true, retransmitRate = 0.10, lossRate = 0.08)
        assertFalse(decision.shouldReduceMtu)
        decision = sample(monitor, t0 + 30_000L, stressed = true, retransmitRate = 0.10, lossRate = 0.08)
        assertTrue(decision.shouldReduceMtu)
        assertEquals(TcpStressMonitor.MtuLevel.CELLULAR, decision.recommendedLevel)
        assertEquals(TcpStressMonitor.TuningDecision.Urgency.HIGH, decision.urgency)
    }

    @Test
    fun anInterveningHealthySampleResetsTheSevereStressTimer() {
        val monitor = TcpStressMonitor()
        sample(monitor, t0, stressed = true)
        sample(monitor, t0 + 1_000L, stressed = false)
        sample(monitor, t0 + 25_000L, stressed = true)
        sample(monitor, t0 + 27_000L, stressed = true)
        var decision = sample(monitor, t0 + 29_000L, stressed = true)
        assertFalse("the old stress timestamp must not survive a healthy interval", decision.shouldReduceMtu)
        decision = sample(monitor, t0 + 35_000L, stressed = true)
        assertTrue("a fresh, uninterrupted severe interval eventually acts", decision.shouldReduceMtu)
    }

    @Test
    fun healthyRecoveryTimerAlsoResetsWhenStressReturns() {
        val monitor = TcpStressMonitor()
        for (offset in 0L..10_000L step 2_000L) {
            sample(monitor, t0 + offset, stressed = true)
        }
        assertEquals(TcpStressMonitor.MtuLevel.CELLULAR, monitor.currentMtuLevel())

        sample(monitor, t0 + 20_000L, stressed = false)
        sample(monitor, t0 + 22_000L, stressed = false)
        sample(monitor, t0 + 24_000L, stressed = false)
        sample(monitor, t0 + 25_000L, stressed = true)
        for (offset in 26_000L..34_000L step 2_000L) {
            sample(monitor, t0 + offset, stressed = false)
        }
        val beforeFullRecovery = monitor.evaluate(t0 + 140_000L)
        assertFalse("a pre-stress healthy timer must not trigger an early step-up", beforeFullRecovery.shouldIncreaseMtu)
        val recovered = monitor.evaluate(t0 + 146_000L)
        assertTrue(recovered.shouldIncreaseMtu)
        assertEquals(TcpStressMonitor.MtuLevel.FULL, recovered.recommendedLevel)
    }

    @Test
    fun initialMonitorLevelTracksAnAlreadyConservativeActiveTunnel() {
        val monitor = TcpStressMonitor()
        sample(monitor, t0, stressed = false, currentMtu = 1_280)
        assertEquals(TcpStressMonitor.MtuLevel.MINIMUM, monitor.currentMtuLevel())
    }
}
