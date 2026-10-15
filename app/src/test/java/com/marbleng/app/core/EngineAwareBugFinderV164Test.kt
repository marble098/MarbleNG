package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAwareBugFinderV164Test {
    @Test
    fun xrayEngineReadsXrayEvidence() {
        val state = resolveActiveCoreState(
            engine = CoreEngine.XRAY,
            xrayAlive = true,
            xrayPid = 4242L,
            xrayPhase = "ready",
            xrayError = "",
            singboxAlive = false,
            singboxPhase = "stopped",
            singboxError = ""
        )
        assertTrue(state.alive)
        assertEquals(4242L, state.pid)
        assertEquals("ready", state.phase)
        assertEquals("Xray-core", state.label)
    }

    @Test
    fun singboxEngineReadsSingboxEvidenceEvenWhenXrayIsDead() {
        val state = resolveActiveCoreState(
            engine = CoreEngine.SINGBOX,
            xrayAlive = false,
            xrayPid = -1L,
            xrayPhase = "stopped",
            xrayError = "",
            singboxAlive = true,
            singboxPhase = "ready",
            singboxError = ""
        )
        assertTrue(
            "a healthy sing-box session must not read as a dead core",
            state.alive
        )
        assertEquals("ready", state.phase)
        assertEquals("sing-box extended", state.label)
    }

    @Test
    fun singboxEngineHasNoChildPidOnTheDiagnosticPlane() {
        val state = resolveActiveCoreState(
            engine = CoreEngine.SINGBOX,
            xrayAlive = false,
            xrayPid = -1L,
            xrayPhase = "stopped",
            xrayError = "",
            singboxAlive = true,
            singboxPhase = "ready",
            singboxError = ""
        )
        assertEquals(-1L, state.pid)
    }

    @Test
    fun aDeadSelectedCoreIsStillReportedDead() {
        val state = resolveActiveCoreState(
            engine = CoreEngine.SINGBOX,
            xrayAlive = false,
            xrayPid = -1L,
            xrayPhase = "stopped",
            xrayError = "exit 1",
            singboxAlive = false,
            singboxPhase = "failed",
            singboxError = "sing-box rejected profile"
        )
        assertFalse(state.alive)
        assertEquals("failed", state.phase)
        assertEquals("sing-box rejected profile", state.error)
        assertEquals("singbox", state.id)
    }
}
