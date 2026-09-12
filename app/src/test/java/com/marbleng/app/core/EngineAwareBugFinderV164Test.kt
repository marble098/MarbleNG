package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_ENGINE_AWARE_BUGFINDER_V164 — the Bug Finder must ask about the core the settings
 * selected, never the Xray manager a sing-box session stops on purpose.
 *
 * The report that motivated this showed `appState=CONNECTED`, `xrayAlive=false`, `xrayPid=-1` and
 * `xrayStartPhase=stopped` beside `HEV run-enter xrayAlive=true` — a healthy sing-box route whose
 * Bug Finder printed "UI says CONNECTED but Xray is dead" because it only read `xray.isAlive`.
 * `resolveActiveCoreState` is the single accessor that decides which manager's evidence is honest
 * for the session, so every reader (process check, SOCKS-listener check, CURRENT CONNECTION
 * section) agrees.
 */
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
        // The exact regression shape from the report: the Xray manager is stopped (it always is on
        // a sing-box session), while the engine that actually carries the route is alive.
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
        // The honest failure must survive the fix: when the *selected* engine is down while the
        // UI says CONNECTED, the check must still FAIL — just about the right core.
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
