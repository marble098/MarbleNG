package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FDSAN_DOUBLE_CLOSE_V224 — the `fdsan: double-close` disconnect abort, pinned on the
 * Kotlin side of the boundary.
 *
 * The device log this chapter answers is unambiguous, in this order:
 *
 * ```
 * TUN2SOCKS | datapath-stop-ack
 * TUN2SOCKS | run-exit            code=0
 * TUN | datapath-released-fd      hevFd=148
 * TUN | fd-close                  hevFd=148  owner=datapath-worker
 * fdsan: double-close of file descriptor 148 detected
 * Fatal signal 6 (SIGABRT)        tid … (pool-19-thread-)
 * ```
 *
 * The service had done everything V223 asked — one owner, one close, under a lock, after a
 * joined stop — and the abort still fired *inside* that one close, because the number was
 * already closed at kernel level: gVisor's `Stack.Wait()` removes every NIC with
 * `closeLinkEndpoint=true`, which runs the pinned tun2socks `FD.Close()`, which is
 * `unix.Close(fd)`. A raw syscall close never touches bionic's fdsan table, so the service's
 * `ParcelFileDescriptor.adoptFd(fd).close()` — a close *with* an fdsan owner tag — met EBADF,
 * and `android_fdsan_close_with_tag` turns exactly that pair into
 * "double-close of file descriptor %d detected" + abort.
 *
 * The fix lives across the JNI boundary (both datapaths now duplicate the borrowed descriptor
 * with `F_DUPFD_CLOEXEC` and close only their own dup), so no JVM test can execute it. What this
 * class pins is the Kotlin-visible half of the contract the fix rests on:
 *
 *  1. the chapter marker the integrity audit looks for in every layer of the fix exists, next to
 *     the V223 marker it completes rather than replaces;
 *  2. the service-side rule is unchanged — this release must not "fix" the crash by stopping the
 *     service from closing its own number, which would just trade the abort for a leaked TUN
 *     descriptor on every early-refusal path (`-1`/`-5`/`-7`) where the native side never ran;
 *  3. a forced-exit reading names the fdsan double-close, so the next report of this family
 *     arrives pre-diagnosed instead of as a bare `status=6`.
 *
 * The Go/C half is pinned where those sources are actually read: the V224 checks in
 * `scripts/system-integrity-check.py` (the wrapper may only open its device on `ownedFd`, the dup
 * of the borrowed number — never on the borrowed `fd` — and both JNI bridges must
 * `F_DUPFD_CLOEXEC`) and the stage-time guard in `scripts/prepare-native.sh`, which fails a
 * release build whose staged wrapper has lost the dup.
 */
class FdsanDoubleCloseV224Test {

    @Test
    fun theOwnershipChapterCompletesV223InsteadOfReplacingIt() {
        assertEquals(
            "MARBLE_FDSAN_DOUBLE_CLOSE_V224",
            TunnelShutdownPolicy.FD_OWNERSHIP_MARKER
        )
        // The V223 rules (stop only your own datapath, join before closing, read status=6 as a
        // signal) are all still load-bearing; V224 adds the borrowed-descriptor rule beneath them.
        assertEquals(
            "MARBLE_TUN2SOCKS_SHUTDOWN_V223",
            TunnelShutdownPolicy.MARKER
        )
    }

    @Test
    fun theServiceStillClosesItsOwnNumberExactlyOnceTheDatapathHasLetGo() {
        // Never under a live datapath: the worker that ran the tunnel performs the close when
        // its native run returns, and a racing cleanup defers to it.
        assertFalse(TunnelShutdownPolicy.mayCloseDescriptor(datapathOwnsFd = true))
        // And it *does* close once the run is over. Refusing here is not a fix for the fdsan
        // abort: on every native early-refusal path nothing else owns this number, so an
        // unclosed service descriptor would leak a TUN fd per failed connect.
        assertTrue(TunnelShutdownPolicy.mayCloseDescriptor(datapathOwnsFd = false))

        // The stop that precedes the close is still the joined one for tun2socks (the core from
        // the crash log) and a plain signal for HEV, and a datapath that never started is never
        // stopped.
        assertEquals(
            DatapathStop.QUIT_AND_JOIN,
            TunnelShutdownPolicy.stopPlan(TunnelCore.TUN2SOCKS, true)
        )
        assertEquals(
            DatapathStop.QUIT,
            TunnelShutdownPolicy.stopPlan(TunnelCore.HEV, true)
        )
        TunnelCore.entries.forEach { core ->
            assertEquals("$core", DatapathStop.NONE, TunnelShutdownPolicy.stopPlan(core, false))
        }
    }

    @Test
    fun theCrashFamilyIsNamedInTheForcedExitReading() {
        val reading = TunnelShutdownPolicy.forcedExitReading(
            TunnelShutdownPolicy.REASON_SIGNALED,
            TunnelShutdownPolicy.SIGABRT
        )
        // The exact wording a reader of the next fdsan report needs, beside the V223 basics.
        assertTrue(reading, reading.contains("fdsan double-close"))
        assertTrue(reading, reading.contains("file descriptor"))
        assertTrue(reading, reading.contains("SIGABRT(6)"))
        assertTrue(reading, reading.contains("abort()"))
        assertTrue(reading, reading.contains("logcat"))
        assertTrue(
            TunnelShutdownPolicy.isNativeAbort(
                TunnelShutdownPolicy.REASON_SIGNALED,
                TunnelShutdownPolicy.SIGABRT
            )
        )
        // No other exit kind may borrow the diagnosis.
        assertFalse(
            TunnelShutdownPolicy.forcedExitReading(4, TunnelShutdownPolicy.SIGABRT)
                .contains("fdsan")
        )
    }
}
