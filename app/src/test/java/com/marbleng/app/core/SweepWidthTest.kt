package com.marbleng.app.core

import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.ProbeMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SweepWidthTest {
    private fun width(
        method: ProbeMethod = ProbeMethod.REAL_DELAY,
        engine: CoreEngine = CoreEngine.XRAY,
        tcpWorkers: Int = 20,
        cpus: Int = 8,
        singBoxCeiling: Int = 8,
        xrayChildCeiling: Int = 8,
        speedFactor: Double = 1.5,
        v2rayStyleDelay: Boolean = false
    ): Int = sweepWidth(
        method = method,
        engine = engine,
        tcpWorkers = tcpWorkers,
        cpus = cpus,
        singBoxCeiling = singBoxCeiling,
        xrayChildCeiling = xrayChildCeiling,
        speedFactor = speedFactor,
        v2rayStyleDelay = v2rayStyleDelay
    )

    @Test
    fun theDirectSweepRunsExactlyAsWideAsTheCallerAsked() {
        assertEquals(24, width(method = ProbeMethod.TCP_PING, tcpWorkers = 24))
        assertEquals(16, width(method = ProbeMethod.TCP_PING, tcpWorkers = 16))

        assertEquals(8, width(method = ProbeMethod.TCP_PING, tcpWorkers = 8, speedFactor = 2.0))
    }

    @Test
    fun nativeChildMeasurementsStayUnderTheDeviceCeiling() {
        for (factor in listOf(0.5, 1.0, 1.5, 2.0)) {
            assertEquals(
                4,
                width(
                    method = ProbeMethod.URL_TEST,
                    tcpWorkers = 24,
                    singBoxCeiling = 4,
                    speedFactor = factor
                )
            )
            assertEquals(
                6,
                width(
                    method = ProbeMethod.REAL_DELAY,
                    engine = CoreEngine.SINGBOX,
                    tcpWorkers = 24,
                    singBoxCeiling = 6,
                    speedFactor = factor
                )
            )
        }

        assertEquals(8, width(method = ProbeMethod.URL_TEST, tcpWorkers = 24, singBoxCeiling = 8, speedFactor = 1.5))
        assertEquals(4, width(method = ProbeMethod.URL_TEST, tcpWorkers = 4, singBoxCeiling = 8, speedFactor = 1.5))
    }

    @Test
    fun theXrayRealDelayPoolScalesWithTheDial() {
        assertEquals(4, width(cpus = 8, speedFactor = 1.0))
        assertEquals(2, width(cpus = 2, speedFactor = 1.0))

        assertEquals(6, width(cpus = 8, speedFactor = 1.5))
        assertEquals(3, width(cpus = 2, speedFactor = 1.5))

        assertEquals(2, width(cpus = 8, speedFactor = 0.5))

        assertEquals(8, width(cpus = 8, speedFactor = 2.0, xrayChildCeiling = 8))
        assertEquals(5, width(cpus = 8, speedFactor = 2.0, xrayChildCeiling = 5))

        assertEquals(4, width(cpus = 8, speedFactor = 1.5, xrayChildCeiling = 4))
    }

    @Test
    fun theLegacyLadderKeepsItsUnscaledEnvelope() {
        assertEquals(4, width(v2rayStyleDelay = true, tcpWorkers = 24, speedFactor = 2.0))
        assertEquals(2, width(v2rayStyleDelay = true, tcpWorkers = 1, speedFactor = 0.5))
    }

    @Test
    fun noInputEscapesTheLegalRanges() {
        for (cpus in 1..16) {
            for (workers in intArrayOf(0, 1, 20, 64, 999)) {
                for (factor in listOf(-1.0, 0.0, 0.5, 1.0, 1.5, 2.0, 40.0, Double.NaN)) {
                    val w = width(
                        tcpWorkers = workers,
                        cpus = cpus,
                        singBoxCeiling = 6,
                        xrayChildCeiling = 6,
                        speedFactor = factor
                    )
                    assertTrue("width $w out of range (cpus=$cpus workers=$workers factor=$factor)", w in 1..64)
                }
            }
        }
    }

    @Test
    fun theDeviceCeilingsThisEngineActuallyReads() {
        val granted = MeasurementCoreBudget.ceiling(cpus = 8, memoryClassMb = 256)
        assertEquals(MeasurementCoreBudget.MAX, granted)
        assertTrue(granted >= MeasurementCoreBudget.BASE)

        val extra = MeasurementCoreBudget.extraSlots(cpus = 8, memoryClassMb = 256)
        assertEquals(granted, MeasurementCoreBudget.BASE + extra)
        assertTrue(extra >= 0)

        assertEquals(
            0,
            MeasurementCoreBudget.extraSlots(cpus = 4, memoryClassMb = 128, lowRam = true)
        )
    }
}
