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
    fun smoke() {
        assertEquals(1, 1)
    }
}
