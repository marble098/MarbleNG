package com.marbleng.app.core

/**
 * MARBLE_TUN2SOCKS_CORE_V222 — everything the tun2socks datapath is given, decided in one place
 * and unit-tested without a device.
 *
 * The native entry point takes scalars, not YAML, so this object is the only writer of that
 * contract. It clamps every value the Settings page can produce, because a bad MTU or a zero
 * UDP timeout must become a failed connect with a readable reason rather than a native
 * assertion. The defaults are the upstream defaults: a 1500-byte MTU, a 60-second UDP session,
 * warning-level logs and the kernel's own TCP buffer sizing.
 */
object Tun2SocksPolicy {
    const val MARKER = "MARBLE_TUN2SOCKS_CORE_V222"

    const val MIN_MTU: Int = 576
    const val MAX_MTU: Int = 9000
    const val DEFAULT_UDP_TIMEOUT_SEC: Int = 60
    const val MIN_UDP_TIMEOUT_SEC: Int = 5
    const val MAX_UDP_TIMEOUT_SEC: Int = 600
    const val MAX_BUFFER_KB: Int = 64 * 1024

    val LOG_LEVELS: List<String> = listOf("debug", "info", "warn", "error", "silent")

    val UDP_TIMEOUT_CHOICES: List<Int> = listOf(30, 60, 120, 300)

    data class Config(
        val proxy: String,
        val mtu: Int,
        val tunFd: Int,
        val udpTimeoutSec: Int,
        val tcpSendBufferBytes: Long,
        val tcpReceiveBufferBytes: Long,
        val moderateReceiveBuffer: Boolean,
        val logLevel: String,
        val logPath: String
    ) {
        fun summary(): String =
            "$proxy • mtu=$mtu • udp=${udpTimeoutSec}s • " +
                "txbuf=${tcpSendBufferBytes}B • rxbuf=${tcpReceiveBufferBytes}B • " +
                "moderate=$moderateReceiveBuffer • log=$logLevel"
    }

    /**
     * Builds the native call for a live TUN descriptor. Returns null when the descriptor or the
     * proxy address cannot make a valid tunnel, so the caller can fail the connect with its own
     * wording instead of starting a datapath that will refuse every packet.
     */
    fun buildConfig(
        socksPort: Int,
        mtu: Int,
        tunFd: Int,
        udpTimeoutSec: Int,
        tcpSendBufferKb: Int,
        tcpReceiveBufferKb: Int,
        moderateReceiveBuffer: Boolean,
        logLevel: String,
        logPath: String,
        mtuCap: Int = 0
    ): Config? {
        if (socksPort !in 1..65535) return null
        if (tunFd < 0) return null
        val effectiveMtu = resolveMtu(mtu, mtuCap) ?: return null
        return Config(
            proxy = "socks5://127.0.0.1:$socksPort",
            mtu = effectiveMtu,
            tunFd = tunFd,
            udpTimeoutSec = normalizeUdpTimeout(udpTimeoutSec),
            tcpSendBufferBytes = normalizeBufferKb(tcpSendBufferKb) * 1024L,
            tcpReceiveBufferBytes = normalizeBufferKb(tcpReceiveBufferKb) * 1024L,
            moderateReceiveBuffer = moderateReceiveBuffer,
            logLevel = normalizeLogLevel(logLevel),
            logPath = logPath.trim()
        )
    }

    /** `mtuCap` of 0 means "use the session MTU"; anything else may only lower it. */
    fun resolveMtu(sessionMtu: Int, mtuCap: Int = 0): Int? {
        val base = if (sessionMtu in MIN_MTU..MAX_MTU) sessionMtu else 1500
        val capped = if (mtuCap in MIN_MTU..MAX_MTU) minOf(base, mtuCap) else base
        return capped.coerceIn(MIN_MTU, MAX_MTU)
    }

    fun normalizeUdpTimeout(value: Int): Int =
        if (value <= 0) DEFAULT_UDP_TIMEOUT_SEC else value.coerceIn(MIN_UDP_TIMEOUT_SEC, MAX_UDP_TIMEOUT_SEC)

    fun normalizeBufferKb(value: Int): Int = value.coerceIn(0, MAX_BUFFER_KB)

    fun normalizeLogLevel(raw: String): String {
        val clean = raw.trim().lowercase()
        return if (clean in LOG_LEVELS) clean else "warn"
    }

    fun normalize(raw: com.marbleng.app.model.AppSettings): com.marbleng.app.model.AppSettings =
        raw.copy(
            tunnelCoreId = parseTunnelCore(raw.tunnelCoreId).id,
            tun2SocksUdpTimeoutSec = normalizeUdpTimeout(raw.tun2SocksUdpTimeoutSec),
            tun2SocksTcpSendBufferKb = normalizeBufferKb(raw.tun2SocksTcpSendBufferKb),
            tun2SocksTcpReceiveBufferKb = normalizeBufferKb(raw.tun2SocksTcpReceiveBufferKb),
            tun2SocksLogLevel = normalizeLogLevel(raw.tun2SocksLogLevel),
            tun2SocksMtu = if (raw.tun2SocksMtu == 0) 0 else (resolveMtu(raw.tun2SocksMtu) ?: 0)
        )
}
