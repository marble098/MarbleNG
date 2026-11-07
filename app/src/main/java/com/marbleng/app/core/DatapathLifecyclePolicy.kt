package com.marbleng.app.core

/**
 * MARBLE_DATAPATH_STOP_ACK_V274 — the run/stop handshake that ends the BLOCKED↔CONNECTING loop.
 *
 * ## What was wrong
 *
 * The debug timeline of a failing night read like this, over and over:
 *
 * ```
 * 03:28:34.430  VPN | blocked | reason=Xray core stopped | killSwitchHold=true | xrayAlive=false
 * 03:28:34.464  VPN | blocked | reason=hev-socks5-tunnel stopped (0)
 * 03:28:37.948  HEV  | run-enter | hevFd=118
 * 03:28:37.950  HEV  | run-exit | code=0 | runningFlag=true | xrayAlive=true
 * 03:28:37.950  HEV  | exited-before-ready | code=0
 * ```
 *
 * Three separate defects produced that loop, and the third is the one that kept it turning:
 *
 *  1. **A stop nobody acknowledged.** A datapath run that exits because *Marble itself asked it
 *     to stop* was reported with the same sentence as a run that died on its own
 *     (`"hev-socks5-tunnel stopped (0)"`), so every legitimate teardown of a failed session
 *     filed a *second* failure — a second `failure-generation`, a second BLOCKED, a second
 *     recovery — for the one root cause that started it.
 *
 *  2. **A stale quit that armed the next run's landmine.** `hev_socks5_tunnel_quit()` ORs a
 *     sticky `SYNC_STOP` bit into HEV's `tsync` whenever it is called while no tunnel lifecycle
 *     is active, and the next `hev_socks5_tunnel_run()` *consumes that bit and returns 0 without
 *     running at all*. The service's own `runDatapath` tail called `handleFailure` while its
 *     descriptor was still marked owned, so `stopDatapath()` forwarded one more quit into a core
 *     that had already finalized — arming the latch the recovery run then stepped on 3 seconds
 *     later. That is the whole distance between `run-enter` and `run-exit` above: **2 ms**.
 *
 *  3. **Labels that pointed at the wrong engine.** The session ran `engine=singbox`, and the
 *     failure still said `Xray core stopped`, because the reason strings and the `xrayAlive`
 *     diagnostic key were written when Xray was the only core. A reader (and Bug Finder) was
 *     sent to debug a core that was not even selected.
 *
 * ## What this file owns
 *
 * The decisions are extracted here so a unit test can drive every branch without a device:
 *
 *  - [readingOf] classifies a finished native run — an **acknowledged stop** (we asked for it:
 *    not a failure), a **latched no-op** (HEV consumed a stale `SYNC_STOP` and returned in
 *    milliseconds: re-enter the run once, the bit cannot survive being consumed), or a **real
 *    exit** (the datapath died on its own: that is a failure).
 *  - [datapathStoppedReason] / [coreStoppedReason] name the engine that actually stopped, so a
 *    sing-box session never reports itself as Xray.
 *  - [isDatapathEcho] is the second line of defence in `handleFailure`: once a recovery is
 *    scheduled, the teardown echoes of the run that just failed must not open a new generation.
 *
 * The socket probe and the flag choreography stay in `MarbleVpnService`, where the threads live;
 * this object is the pure arithmetic of the decision.
 */
object DatapathLifecyclePolicy {
    const val MARKER: String = "MARBLE_DATAPATH_STOP_ACK_V274"

    /**
     * A native run that returns in less than this, clean (`code == 0`), without any stop having
     * been requested for it, is a HEV `SYNC_STOP` no-op: config parse + init + fini and nothing
     * else, which is exactly what the pinned core does when it consumes the latch. The observed
     * duration of the bug was ~2 ms; healthy runs block for the whole session. The threshold is
     * an order of magnitude above the failure and three orders below a real run, so it cannot
     * classify a working session.
     */
    const val LATCHED_NOOP_MAX_MS: Long = 250L

    /**
     * How many times a latched no-op is re-entered before it is declared a real failure. One is
     * enough: the latch is consumed by the run that tripped it, so the second entry starts with a
     * clean `tsync`. A second consecutive instant exit means something else refuses to run, and
     * that deserves a failure with a precise reason, not a loop.
     */
    const val LATCH_RETRIES: Int = 1

    /** How long the datapath waits for the core's loopback SOCKS inbound before it starts. */
    const val SOCKS_GATE_TIMEOUT_MS: Long = 2_500L

    /** Poll interval of the SOCKS gate. */
    const val SOCKS_GATE_POLL_MS: Long = 200L

    /** One attempt to connect to the loopback SOCKS inbound. */
    const val SOCKS_GATE_CONNECT_TIMEOUT_MS: Int = 250

    /** How a finished native run reads. */
    enum class ExitKind {
        /** We asked this run to stop and it obeyed — an acknowledgement, never a failure. */
        ACKNOWLEDGED_STOP,

        /** HEV consumed a stale stop latch and ran nothing — re-enter the run once. */
        LATCHED_NOOP,

        /** The datapath exited on its own — this is the failure the reader is looking for. */
        EXITED
    }

    data class ExitReading(
        val kind: ExitKind,
        /** True when [LATCHED_NOOP] transitions to [EXITED] because the retry budget is spent. */
        val latchExhausted: Boolean = false
    )

    /**
     * Classifies one finished native run.
     *
     * @param code the native exit code (`0` = the core says "clean").
     * @param stopRequestedForThisRun a stop was delivered while this exact run was in flight.
     * @param elapsedMs how long the native call blocked before returning.
     * @param attempt 1-based entry number of this run within the session.
     */
    fun readingOf(
        code: Int,
        stopRequestedForThisRun: Boolean,
        elapsedMs: Long,
        attempt: Int
    ): ExitReading = when {
        stopRequestedForThisRun && code == 0 -> ExitReading(ExitKind.ACKNOWLEDGED_STOP)
        stopRequestedForThisRun -> ExitReading(ExitKind.EXITED)
        code == 0 && elapsedMs in 0 until LATCHED_NOOP_MAX_MS && attempt <= LATCH_RETRIES ->
            ExitReading(ExitKind.LATCHED_NOOP)
        code == 0 && elapsedMs in 0 until LATCHED_NOOP_MAX_MS ->
            ExitReading(ExitKind.EXITED, latchExhausted = true)
        else -> ExitReading(ExitKind.EXITED)
    }

    /**
     * The failure reason for a datapath run that exited on its own, with the engine named.
     *
     * V-code readers saw `hev-socks5-tunnel stopped (0)` for a run that had *been stopped*, and
     * nothing at all distinguished an acknowledged stop from a dead one. The `(code)` stays
     * because it is the one number the native log can be correlated against.
     */
    fun datapathStoppedReason(core: TunnelCore, code: Int): String =
        "${TunnelCoreInfo.displayName(core)} stopped ($code)"

    /**
     * The failure reason for a proxy core that is no longer alive, named for the engine that was
     * actually selected. `Xray core stopped` on a sing-box session sent two nights of debugging
     * after the wrong binary; the engine is in the reason now, and so is the diagnostic key
     * (`coreAlive`, not `xrayAlive`).
     */
    fun coreStoppedReason(engine: CoreEngine): String =
        "${engineLabel(engine)} core stopped"

    /** The same engine-aware naming for the local-proxy (SOCKS) monitor. */
    fun localProxyStoppedReason(engine: CoreEngine): String =
        "${engineLabel(engine)} local proxy stopped"

    /** The user-facing name of the selected engine, one spelling. */
    fun engineLabel(engine: CoreEngine): String = when (engine) {
        CoreEngine.SINGBOX -> "sing-box"
        CoreEngine.XRAY -> "Xray"
    }

    /**
     * Whether `reason` is the echo of a datapath teardown rather than a new root cause.
     *
     * `handleFailure` used to compare against `"HEV stopped"`, a sentence nothing in the product
     * had ever emitted — the real reasons begin with `hev-socks5-tunnel` and `tun2socks`. While a
     * recovery is already scheduled, an echo must not open a second failure generation.
     */
    fun isDatapathEcho(reason: String): Boolean {
        val value = reason.trim()
        return value.startsWith("HEV stopped") ||
            value.startsWith("${TunnelCoreInfo.displayName(TunnelCore.HEV)} stopped") ||
            value.startsWith("${TunnelCoreInfo.displayName(TunnelCore.TUN2SOCKS)} stopped")
    }

    /**
     * How the SOCKS gate decides. The datapath may only start dialing `127.0.0.1:port` once the
     * core is actually listening: during recovery the old datapath's refused connections were
     * user-visible, and a datapath pointed at a dead port logs a burst of `connect: connection
     * refused` that reads exactly like a server fault.
     */
    fun socksGateVerdict(
        probeSucceeded: Boolean,
        coreAlive: Boolean
    ): SocksGateVerdict = when {
        probeSucceeded -> SocksGateVerdict.PROCEED
        coreAlive -> SocksGateVerdict.RETRY
        else -> SocksGateVerdict.ABANDON
    }

    enum class SocksGateVerdict { PROCEED, RETRY, ABANDON }
}
