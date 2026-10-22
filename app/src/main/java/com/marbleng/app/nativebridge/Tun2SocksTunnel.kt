package com.marbleng.app.nativebridge

/**
 * MARBLE_TUN2SOCKS_CORE_V222 — the second TUN datapath.
 * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — stop with a budget, and never close the descriptor here.
 * MARBLE_FDSAN_DOUBLE_CLOSE_V224 — the descriptor is borrowed; the core runs on its own dup.
 *
 * `hev-socks5-tunnel` is the datapath Marble has always shipped, and it stays the default.
 * This is the alternative the Settings → Tunnel engine page lets a user choose: the pinned
 * `xjasonlyu/tun2socks` release, built into `libtun2socks.so` as a Go c-shared library and
 * loaded into this process, because only this process holds the descriptor Android's
 * VpnService created.
 *
 * The JNI entry points live in `libmarbleng.so` (`jni/tun2socks_jni.c`), which resolves the Go
 * symbols with `dlopen`. That keeps two promises:
 *
 *  - a build or an ABI without the library fails a connection with a reason
 *    (`available() == false`), never a class-initialisation crash;
 *  - the tunnel core can be switched at runtime, with both libraries loaded side by side,
 *    without a second process and without handing a raw descriptor to one.
 *
 * Descriptor ownership: the descriptor passed to [run] belongs to [MarbleVpnService]. The core
 * reads it, [stopAndWait] proves it has stopped reading it, and the service closes it — once,
 * in `closeHevFd()`. The library must never close it: closing it on both sides is a double close
 * of a number the app may already have recycled, and that is the failure this file's V223 marker
 * exists to keep out.
 *
 * V224 completes that sentence, because the V223 library *did* close it without containing a
 * close: gVisor's `Stack.Wait()` removes every NIC with `closeLinkEndpoint=true`, which runs the
 * pinned tun2socks `FD.Close()` — an embedded-endpoint no-op wrapped around `unix.Close(fd)`.
 * The service's adopted close then met EBADF with an fdsan owner tag and bionic aborted the
 * process (`fdsan: double-close of file descriptor 148 detected`). So the wrapper now duplicates
 * the borrowed descriptor (`F_DUPFD_CLOEXEC`) before opening the device, and only the dup — a
 * number the library owns outright — is ever visible to the device, the NIC and the stack. The
 * borrowed number stays open until the service's one close, whatever a future stack decides to
 * close on teardown.
 */
object Tun2SocksTunnel {
    /** The core could not be loaded, so Settings must explain that instead of offering it. */
    const val CODE_UNAVAILABLE: Int = -1000

    /** The library loaded but a symbol is missing — a packaging bug, reported, never fatal. */
    const val CODE_NO_SYMBOL: Int = -1001

    /** The JNI shim could not hand the strings to the core (out of memory), not a core fault. */
    const val CODE_JNI: Int = -1002

    /** The core is still tearing down a previous datapath; this connect must retry later. */
    const val CODE_STOPPING: Int = -7

    /**
     * How long `stopAndWait` may hold the caller while the datapath releases its descriptor.
     * The teardown itself is microseconds (it writes a stopfd and joins the dispatch loops);
     * the budget only bounds the worst case, so a wedged core degrades to "the fd is closed
     * anyway, with a logged timeout" instead of an ANR.
     */
    const val STOP_TIMEOUT_MS: Int = 1_500

    private val loadError: String? = runCatching {
        System.loadLibrary("marbleng")
        System.loadLibrary("tun2socks")
        null
    }.getOrElse { it.message ?: it.javaClass.simpleName }

    fun available(): Boolean = loadError == null

    fun unavailableReason(): String = loadError.orEmpty()

    /** Blocking. Call from the datapath worker exactly like [HevTunnel.run]. */
    fun run(
        proxy: String,
        mtu: Int,
        tunFd: Int,
        udpTimeoutSec: Int,
        tcpSendBuffer: Long,
        tcpReceiveBuffer: Long,
        moderateReceiveBuffer: Boolean,
        logLevel: String,
        logPath: String
    ): Int = if (available()) {
        nativeRun(
            proxy,
            mtu,
            tunFd,
            udpTimeoutSec,
            tcpSendBuffer,
            tcpReceiveBuffer,
            moderateReceiveBuffer,
            logLevel,
            logPath
        )
    } else {
        CODE_UNAVAILABLE
    }

    /** Signals the datapath and returns immediately. Prefer [stopAndWait] before closing the fd. */
    fun quit() {
        if (available()) runCatching { nativeQuit() }
    }

    /**
     * Signals the datapath and waits up to [timeoutMs] for its teardown to finish.
     *
     * `true` means nothing is running and the caller owns the descriptor again, so it may close
     * it. `false` means the budget expired; the caller closes it anyway and logs the timeout,
     * because leaving a descriptor open over a wedged core is the worse of the two failures.
     */
    fun stopAndWait(timeoutMs: Int = STOP_TIMEOUT_MS): Boolean {
        if (!available()) return true
        return runCatching { nativeStop(timeoutMs) != 0 }.getOrDefault(false)
    }

    fun stats(): LongArray? =
        if (available()) runCatching { nativeStats() }.getOrNull() else null

    fun lastError(): String =
        if (available()) runCatching { nativeError() ?: "" }.getOrDefault("") else unavailableReason()

    fun version(): String =
        if (available()) runCatching { nativeVersion() ?: "" }.getOrDefault("") else ""

    private external fun nativeRun(
        proxy: String,
        mtu: Int,
        tunFd: Int,
        udpTimeoutSec: Int,
        tcpSendBuffer: Long,
        tcpReceiveBuffer: Long,
        moderateReceiveBuffer: Boolean,
        logLevel: String,
        logPath: String
    ): Int

    private external fun nativeQuit()

    private external fun nativeStop(waitMs: Int): Int

    private external fun nativeStats(): LongArray?

    private external fun nativeError(): String?

    private external fun nativeVersion(): String?
}
