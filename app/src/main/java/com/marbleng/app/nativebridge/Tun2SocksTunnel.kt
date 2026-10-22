package com.marbleng.app.nativebridge

/**
 * MARBLE_TUN2SOCKS_CORE_V222 — the second TUN datapath.
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
 */
object Tun2SocksTunnel {
    /** The core could not be loaded, so Settings must explain that instead of offering it. */
    const val CODE_UNAVAILABLE: Int = -1000

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

    fun quit() {
        if (available()) runCatching { nativeQuit() }
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

    private external fun nativeStats(): LongArray?

    private external fun nativeError(): String?

    private external fun nativeVersion(): String?
}
