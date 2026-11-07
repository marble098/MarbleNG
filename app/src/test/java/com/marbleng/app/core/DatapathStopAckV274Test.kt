package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_DATAPATH_STOP_ACK_V274 — the run/stop handshake that ends the BLOCKED↔CONNECTING loop,
 * pinned branch by branch.
 *
 * The debug timeline that drove this fix:
 *
 * ```
 * 03:28:34.430  VPN | blocked | reason=Xray core stopped | killSwitchHold=true | xrayAlive=false
 * 03:28:34.464  VPN | blocked | reason=hev-socks5-tunnel stopped (0)
 * 03:28:37.948  HEV  | run-enter | hevFd=118
 * 03:28:37.950  HEV  | run-exit | code=0 | runningFlag=true | xrayAlive=true
 * 03:28:37.950  HEV  | exited-before-ready | code=0
 * ```
 *
 * Three claims have to hold for that loop to be impossible again, and each is a test here:
 *
 *  1. a run that exits because it was *asked* to stop is an acknowledgement, not a failure;
 *  2. a run that exits instantly, clean and unasked is HEV's consumed `SYNC_STOP` latch, and it
 *     is re-entered exactly once before being declared a real failure;
 *  3. no failure reason ever names an engine that was not selected.
 */
class DatapathStopAckV274Test {

    @Test
    fun aStopThatWasAskedForIsAnAcknowledgementNotAFailure() {
        val reading = DatapathLifecyclePolicy.readingOf(
            code = 0,
            stopRequestedForThisRun = true,
            elapsedMs = 42L,
            attempt = 1
        )
        assertEquals(DatapathLifecyclePolicy.ExitKind.ACKNOWLEDGED_STOP, reading.kind)
        assertFalse(reading.latchExhausted)
    }

    @Test
    fun aSlowCleanExitNobodyAskedForIsARealFailure() {
        val reading = DatapathLifecyclePolicy.readingOf(
            code = 0,
            stopRequestedForThisRun = false,
            elapsedMs = 400_000L,
            attempt = 1
        )
        assertEquals(DatapathLifecyclePolicy.ExitKind.EXITED, reading.kind)
    }

    @Test
    fun theObservedTwoMillisecondNoOpIsTheLatch() {
        // The exact shape of the field failure: code=0, nobody asked, 2 ms.
        val reading = DatapathLifecyclePolicy.readingOf(
            code = 0,
            stopRequestedForThisRun = false,
            elapsedMs = 2L,
            attempt = 1
        )
        assertEquals(DatapathLifecyclePolicy.ExitKind.LATCHED_NOOP, reading.kind)
    }

    @Test
    fun theLatchIsRetriedExactlyOnce() {
        val first = DatapathLifecyclePolicy.readingOf(0, false, 2L, attempt = 1)
        assertEquals(DatapathLifecyclePolicy.ExitKind.LATCHED_NOOP, first.kind)

        val second = DatapathLifecyclePolicy.readingOf(0, false, 2L, attempt = 2)
        assertEquals(DatapathLifecyclePolicy.ExitKind.EXITED, second.kind)
        assertTrue(second.latchExhausted)
    }

    @Test
    fun aNonZeroExitIsNeverTheLatchEvenWhenItIsInstant() {
        val refused = DatapathLifecyclePolicy.readingOf(
            code = -7,
            stopRequestedForThisRun = false,
            elapsedMs = 1L,
            attempt = 1
        )
        assertEquals(DatapathLifecyclePolicy.ExitKind.EXITED, refused.kind)
    }

    @Test
    fun aRequestedStopWithANonZeroCodeIsStillARealExit() {
        val reading = DatapathLifecyclePolicy.readingOf(
            code = -1,
            stopRequestedForThisRun = true,
            elapsedMs = 10L,
            attempt = 1
        )
        assertEquals(DatapathLifecyclePolicy.ExitKind.EXITED, reading.kind)
    }

    @Test
    fun theLatchThresholdCannotClassifyAWorkingSession() {
        // A healthy HEV run blocks for the lifetime of the session; the ready grace alone is
        // 250 ms and real sessions run for minutes. Even a run that somehow ended within the
        // threshold without a stop is re-entered once, not failed on the spot.
        assertTrue(DatapathLifecyclePolicy.LATCHED_NOOP_MAX_MS <= 250L)
        assertEquals(1, DatapathLifecyclePolicy.LATCH_RETRIES)
    }

    @Test
    fun failureReasonsNameTheEngineThatActuallyStopped() {
        // The field report: a sing-box session was filed as "Xray core stopped".
        assertEquals("sing-box core stopped", DatapathLifecyclePolicy.coreStoppedReason(CoreEngine.SINGBOX))
        assertEquals("Xray core stopped", DatapathLifecyclePolicy.coreStoppedReason(CoreEngine.XRAY))
        assertEquals(
            "sing-box local proxy stopped",
            DatapathLifecyclePolicy.localProxyStoppedReason(CoreEngine.SINGBOX)
        )
        assertEquals(
            "Xray local proxy stopped",
            DatapathLifecyclePolicy.localProxyStoppedReason(CoreEngine.XRAY)
        )
        assertEquals("hev-socks5-tunnel stopped (0)", DatapathLifecyclePolicy.datapathStoppedReason(TunnelCore.HEV, 0))
        assertEquals("tun2socks stopped (-7)", DatapathLifecyclePolicy.datapathStoppedReason(TunnelCore.TUN2SOCKS, -7))
    }

    @Test
    fun theEchoGuardMatchesTheSentencesTheProductActuallyEmits() {
        // handleFailure used to compare against "HEV stopped" — a sentence nothing emits. The
        // real teardown echoes begin with the datapath's own display names.
        assertTrue(DatapathLifecyclePolicy.isDatapathEcho("hev-socks5-tunnel stopped (0)"))
        assertTrue(DatapathLifecyclePolicy.isDatapathEcho("tun2socks stopped (0)"))
        assertTrue(DatapathLifecyclePolicy.isDatapathEcho("HEV stopped (legacy spelling)"))
        // A root cause must never be swallowed by the echo guard.
        assertFalse(DatapathLifecyclePolicy.isDatapathEcho("sing-box core stopped"))
        assertFalse(DatapathLifecyclePolicy.isDatapathEcho("Xray core stopped"))
        assertFalse(DatapathLifecyclePolicy.isDatapathEcho("HEV datapath stalled and independent HTTPS confirmation failed"))
    }

    @Test
    fun theSocksGateOnlyProceedsAgainstALiveListener() {
        assertEquals(
            DatapathLifecyclePolicy.SocksGateVerdict.PROCEED,
            DatapathLifecyclePolicy.socksGateVerdict(probeSucceeded = true, coreAlive = true)
        )
        assertEquals(
            DatapathLifecyclePolicy.SocksGateVerdict.RETRY,
            DatapathLifecyclePolicy.socksGateVerdict(probeSucceeded = false, coreAlive = true)
        )
        assertEquals(
            DatapathLifecyclePolicy.SocksGateVerdict.ABANDON,
            DatapathLifecyclePolicy.socksGateVerdict(probeSucceeded = false, coreAlive = false)
        )
        // A dead core with a live port is not a state the gate can observe, but it must not
        // hang: the verdict is still a decision, never an absence of one.
        assertEquals(
            DatapathLifecyclePolicy.SocksGateVerdict.PROCEED,
            DatapathLifecyclePolicy.socksGateVerdict(probeSucceeded = true, coreAlive = false)
        )
    }

    @Test
    fun theMarkerIsTheOneTheSourceCarries() {
        assertEquals("MARBLE_DATAPATH_STOP_ACK_V274", DatapathLifecyclePolicy.MARKER)
    }
}
