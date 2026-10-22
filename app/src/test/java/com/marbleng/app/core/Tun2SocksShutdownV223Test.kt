package com.marbleng.app.core

import com.marbleng.app.nativebridge.Tun2SocksTunnel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — the disconnect abort, pinned as three rules.
 *
 * The reported failure was a `reason=SIGNALED status=6` process exit (SIGABRT) about 400 ms after
 * `VPN | shutdown-begin`, with `TUN2SOCKS | run-exit code=0` two milliseconds in, no Java stack,
 * no tombstone and no `fatal-crash-pending.txt` — the signature of a native `abort()`, not of a
 * JVM exception. The three rules below are the ones the V222 code broke, and they are the reason
 * the abort could only ever happen on the tun2socks datapath:
 *
 *  1. a cleanup stops the datapath that started, never the other one (HEV's quit leaves a sticky
 *     `SYNC_STOP`, whose `assert()` on a closed event socketpair aborts the process);
 *  2. only one owner closes the TUN descriptor, and the service waits for the datapath to say it
 *     stopped before it closes it at all;
 *  3. `status=6` on a SIGNALED record is SIGABRT and has to be read as one.
 */
class Tun2SocksShutdownV223Test {

    @Test
    fun aCleanupStopsTheDatapathThatStartedAndNeverTheOtherOne() {
        // The HEV default is unchanged: signal it, exactly as V222 did.
        assertEquals(DatapathStop.QUIT, TunnelShutdownPolicy.stopPlan(TunnelCore.HEV, true))
        // tun2socks owns a descriptor gVisor is still polling, so the stop is joined.
        assertEquals(
            DatapathStop.QUIT_AND_JOIN,
            TunnelShutdownPolicy.stopPlan(TunnelCore.TUN2SOCKS, true)
        )
        // Nobody quits a core that was never running: that is how HEV's sticky stop bit — and the
        // assert() that aborts inside hev_socks5_tunnel_stop() — used to be armed from a
        // tun2socks session.
        TunnelCore.entries.forEach { core ->
            assertEquals("$core", DatapathStop.NONE, TunnelShutdownPolicy.stopPlan(core, false))
        }
        // The two datapaths never share a plan: only tun2socks is joined.
        assertTrue(
            TunnelCore.entries.count { TunnelShutdownPolicy.stopPlan(it, true) == DatapathStop.QUIT_AND_JOIN } == 1
        )
    }

    @Test
    fun theDescriptorIsClosedByOneOwnerAndNeverUnderALiveDatapath() {
        assertFalse(
            "a service-side close is refused while the datapath holds the descriptor",
            TunnelShutdownPolicy.mayCloseDescriptor(datapathOwnsFd = true)
        )
        assertTrue(
            "with no datapath run in flight the service closes it, once",
            TunnelShutdownPolicy.mayCloseDescriptor(datapathOwnsFd = false)
        )
    }

    @Test
    fun theReportedExitRecordIsReadAsANativeAbort() {
        // Exactly the pair from the report: REASON_SIGNALED with status 6.
        assertTrue(TunnelShutdownPolicy.isNativeAbort(TunnelShutdownPolicy.REASON_SIGNALED, 6))
        assertEquals("SIGABRT", TunnelShutdownPolicy.signalName(6))

        val reading = TunnelShutdownPolicy.forcedExitReading(
            TunnelShutdownPolicy.REASON_SIGNALED,
            6
        )
        assertTrue(reading, reading.contains("SIGABRT(6)"))
        assertTrue(reading, reading.contains("abort()"))
        assertTrue(reading, reading.contains("logcat"))

        // A signal death that is not an abort is reported as what it is, not as a crash report.
        val fault = TunnelShutdownPolicy.forcedExitReading(TunnelShutdownPolicy.REASON_SIGNALED, 11)
        assertTrue(fault, fault.contains("SIGSEGV(11)"))
        val killed = TunnelShutdownPolicy.forcedExitReading(TunnelShutdownPolicy.REASON_SIGNALED, 9)
        assertTrue(killed, killed.contains("SIGKILL(9)"))

        // Java crashes, ANRs, low memory and a clean exit are not signal deaths and must not
        // borrow this reading.
        listOf(0, 1, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13).forEach { reason ->
            assertEquals("reason=$reason", "", TunnelShutdownPolicy.forcedExitReading(reason, 6))
        }
        assertFalse(TunnelShutdownPolicy.isNativeAbort(4, 6))
        assertFalse(TunnelShutdownPolicy.isNativeAbort(TunnelShutdownPolicy.REASON_SIGNALED, 11))
    }

    @Test
    fun everySignalThePlatformCanReportHasAReadableName() {
        val expected = mapOf(
            1 to "SIGHUP", 2 to "SIGINT", 3 to "SIGQUIT", 4 to "SIGILL", 5 to "SIGTRAP",
            6 to "SIGABRT", 7 to "SIGBUS", 8 to "SIGFPE", 9 to "SIGKILL", 11 to "SIGSEGV",
            13 to "SIGPIPE", 15 to "SIGTERM"
        )
        expected.forEach { (number, name) ->
            assertEquals(number.toString(), name, TunnelShutdownPolicy.signalName(number))
        }
        // An exotic signal still prints its number instead of an empty line.
        assertEquals("SIG34", TunnelShutdownPolicy.signalName(34))
        assertTrue(
            TunnelShutdownPolicy.forcedExitReading(TunnelShutdownPolicy.REASON_SIGNALED, 34).isNotEmpty()
        )
    }

    @Test
    fun theNativeContractTheFixRestsOnIsExportedAsDocumented() {
        // -7 is the code a reconnect sees while the previous datapath is still tearing down; it is
        // what stops a second gVisor stack from being opened over one descriptor.
        assertEquals(-7, Tun2SocksTunnel.CODE_STOPPING)
        assertEquals(-1000, Tun2SocksTunnel.CODE_UNAVAILABLE)
        assertEquals(-1001, Tun2SocksTunnel.CODE_NO_SYMBOL)
        assertEquals(-1002, Tun2SocksTunnel.CODE_JNI)

        // The join budget must exist, and must stay under the platform's service timeout: a
        // wedged core degrades to a logged timeout, never to an ANR on the cleanup path.
        assertTrue(Tun2SocksTunnel.STOP_TIMEOUT_MS in 250..4_000)

        // On a JVM there is no native library directory, so the datapath reports itself gone and
        // a stop is a no-op that reports "not running" rather than throwing.
        assertFalse(TunnelCoreInfo.isAvailable(TunnelCore.TUN2SOCKS))
        assertTrue(Tun2SocksTunnel.stopAndWait(0))
    }
}
