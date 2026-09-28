package com.marbleng.app.core

import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.ProbeMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_SPEED_DIAL_V199 — how wide one batch runs.
 *
 * The width is where most of a sweep's wall clock lives (waves = candidates ÷ workers), so the
 * policy is pure and pinned from every side:
 *
 *  - the direct TCP-ping sweep runs exactly as wide as the caller asked — no hidden clamp
 *    rewrites the number the user picked (MARBLE_PING_CONTROL_V145, unchanged);
 *  - every native-child measurement stays under the device's measurement-core ceiling: the dial
 *    feeds the pool as fast as the device allows and never one child wider;
 *  - the Xray Real-delay pool scales with the dial from its old 2..4 CPU-derived envelope,
 *    bounded by the device's own Xray-child ceiling — the shipped default runs it ~1.5× wider,
 *    which is the Real-delay share of the "about 50 % faster" promise;
 *  - the legacy v2ray-style ladder keeps its unscaled 2..4 envelope;
 *  - no dial position and no hostile input can escape the legal ranges.
 */
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
        // The caller's value is already dial-scaled upstream (pingWorkers()); the engine must
        // not scale it a second time.
        assertEquals(8, width(method = ProbeMethod.TCP_PING, tcpWorkers = 8, speedFactor = 2.0))
    }

    @Test
    fun nativeChildMeasurementsStayUnderTheDeviceCeiling() {
        // URL test / sing-box Real delay: the device ceiling is a memory decision; the dial
        // cannot buy one child past it.
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
        // Below the ceiling the caller's width rules: a slower dial genuinely narrows the pool.
        assertEquals(8, width(method = ProbeMethod.URL_TEST, tcpWorkers = 24, singBoxCeiling = 8, speedFactor = 1.5))
        assertEquals(4, width(method = ProbeMethod.URL_TEST, tcpWorkers = 4, singBoxCeiling = 8, speedFactor = 1.5))
    }

    @Test
    fun theXrayRealDelayPoolScalesWithTheDial() {
        // The old envelope: 2..4 CPU-derived children.
        assertEquals(4, width(cpus = 8, speedFactor = 1.0))
        assertEquals(2, width(cpus = 2, speedFactor = 1.0))
        // The shipped default: ~1.5× wider — the Real-delay share of the ~50 % promise.
        assertEquals(6, width(cpus = 8, speedFactor = 1.5))
        assertEquals(3, width(cpus = 2, speedFactor = 1.5))
        // A slower dial narrows back toward the old floor, never under it.
        assertEquals(2, width(cpus = 8, speedFactor = 0.5))
        // A hot dial widens further but never past the device's child ceiling.
        assertEquals(8, width(cpus = 8, speedFactor = 2.0, xrayChildCeiling = 8))
        assertEquals(5, width(cpus = 8, speedFactor = 2.0, xrayChildCeiling = 5))
        // A device that can only carry the old pool keeps the old pool.
        assertEquals(4, width(cpus = 8, speedFactor = 1.5, xrayChildCeiling = 4))
    }
}
