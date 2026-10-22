package com.marbleng.app.core

/**
 * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — who stops which datapath, and what a forced process exit means.
 *
 * Extracted from `MarbleVpnService` so the two rules that produced a SIGABRT during disconnect
 * are testable without a device:
 *
 *  1. **Only the datapath that started is stopped.** The service used to call both
 *     `HevTunnel.quit()` and `Tun2SocksTunnel.quit()` on every cleanup. HEV's quit is not the
 *     no-op it looks like: `hev_socks5_tunnel_stop()` ORs a sticky `SYNC_STOP` into `tsync`, and
 *     `hev_socks5_tunnel_run()` returns immediately the next time it sees that bit — a tunnel
 *     that "connects" and carries nothing. Worse, when the bit is set while HEV still owns a
 *     `SYNC_SEND` but the event socketpair has already been closed by `hev_socks5_tunnel_fini()`,
 *     its `assert(res > 0 && "socks5 tunnel write event")` fails and **abort()s the process**.
 *     A tun2socks session must therefore never touch HEV, and vice versa.
 *
 *  2. **The tun2socks datapath is joined, the HEV one is only signalled.** gVisor's fdbased
 *     endpoint keeps polling the descriptor until its stopfd fires, so the service may only
 *     close that descriptor once the datapath has reported that it stopped
 *     ([DatapathStop.QUIT_AND_JOIN]). HEV blocks in its own loop and never closes an external
 *     descriptor at all, so signalling it is enough — that is the shipped default and its
 *     behaviour is left exactly as it was.
 *
 * The second half of this file decodes `ApplicationExitInfo`. A record with
 * `reason=SIGNALED` and `status=6` is a native `abort()` (SIGABRT) — never a Java exception,
 * which is why the crash reports the app can reach contain no Java stack and no
 * `fatal-crash-pending.txt`: nothing in the JVM ran.
 */
enum class DatapathStop {
    /** No datapath is running for this session; stopping it would poke another session's state. */
    NONE,

    /** Signal only: the datapath owns no descriptor this service closes. */
    QUIT,

    /** Signal and wait for the teardown before the caller closes the TUN descriptor. */
    QUIT_AND_JOIN
}

object TunnelShutdownPolicy {
    const val MARKER: String = "MARBLE_TUN2SOCKS_SHUTDOWN_V223"

    /** `android.app.ApplicationExitInfo.REASON_SIGNALED`, duplicated so this file is JVM-testable. */
    const val REASON_SIGNALED: Int = 2

    const val SIGABRT: Int = 6

    fun stopPlan(core: TunnelCore, datapathActive: Boolean): DatapathStop = when {
        !datapathActive -> DatapathStop.NONE
        core == TunnelCore.TUN2SOCKS -> DatapathStop.QUIT_AND_JOIN
        else -> DatapathStop.QUIT
    }

    /**
     * Whether the service may close the duplicated TUN descriptor now.
     *
     * The rule the V222 code broke: a descriptor is closed by exactly one owner, and never by the
     * service while the datapath that is polling it still has it. The Go core used to close it at
     * the end of `marble_t2s_run` and the service closed it again in `closeHevFd()` — two closes
     * of one number, hundreds of milliseconds apart, in a process that is opening and closing
     * descriptors the whole time.
     */
    fun mayCloseDescriptor(datapathOwnsFd: Boolean): Boolean = !datapathOwnsFd

    /** The POSIX name of the signal an `ApplicationExitInfo.SIGNALED` status carries. */
    fun signalName(status: Int): String = when (status) {
        1 -> "SIGHUP"
        2 -> "SIGINT"
        3 -> "SIGQUIT"
        4 -> "SIGILL"
        5 -> "SIGTRAP"
        6 -> "SIGABRT"
        7 -> "SIGBUS"
        8 -> "SIGFPE"
        9 -> "SIGKILL"
        11 -> "SIGSEGV"
        13 -> "SIGPIPE"
        15 -> "SIGTERM"
        else -> "SIG$status"
    }

    fun isNativeAbort(reason: Int, status: Int): Boolean =
        reason == REASON_SIGNALED && status == SIGABRT

    /**
     * One readable line for an exit record, or an empty string when the record is not a signal
     * death. This is what turns `reason=SIGNALED status=6` — which says nothing to a reader — into
     * "the process called abort()", with the place to look for the reason.
     */
    fun forcedExitReading(reason: Int, status: Int): String {
        if (reason != REASON_SIGNALED) return ""
        val name = signalName(status)
        return when (status) {
            SIGABRT ->
                "signal=$name($status) • native abort(), not a Java exception: an assert/abort() " +
                    "inside a native library (HEV's socks5 tunnel write assert, a libc heap check) " +
                    "or an ART JNI/LOG(FATAL) check. The Abort message is in logcat for this pid."
            11 ->
                "signal=$name($status) • native fault (null/dangling pointer) — logcat carries the " +
                    "fault address and the backtrace for this pid."
            9 ->
                "signal=$name($status) • the system killed the process (memory, background " +
                    "restriction or a force-stop); this is not a crash in Marble's code."
            else -> "signal=$name($status) • terminated by a signal, no Java exception exists."
        }
    }
}
