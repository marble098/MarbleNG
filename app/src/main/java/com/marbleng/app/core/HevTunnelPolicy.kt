package com.marbleng.app.core

object HevTunnelPolicy {
    const val MARKER = CoreSocketPolicy.MARKER

    const val CONNECT_TIMEOUT_MS = 15_000

    const val UDP_COPY_BUFFER_NUMS = 32

    const val TASK_STACK_SIZE = 86_016

    fun buildConfig(
        socksPort: Int,
        mtu: Int,
        ipv6: Boolean,
        logFilePath: String,
        datapath: TunnelTuning,
        fastOpen: Boolean = false
    ): String {
        require(socksPort in 1..65_535) { "Invalid HEV SOCKS port" }
        require(mtu in 1_200..9_000) { "Invalid HEV MTU" }
        require(datapath.tcpBufferBytes > 0 && datapath.udpBufferBytes > 0) {
            "Invalid HEV datapath buffers"
        }
        val safeLogPath = logFilePath.replace("'", "''")
        return buildList {
            add("tunnel:")
            add("  mtu: $mtu")
            add("  ipv4: 198.18.0.1")
            if (ipv6) add("  ipv6: 'fc00::1'")
            add("  icmp: 'off'")
            add("socks5:")
            add("  address: '127.0.0.1'")
            add("  port: $socksPort")
            add("  udp: 'udp'")

            add("  pipeline: true")

            if (fastOpen) add("  tcp-fastopen: true")
            add("misc:")
            add("  log-file: '$safeLogPath'")
            add("  log-level: error")
            add("  task-stack-size: $TASK_STACK_SIZE")
            add("  tcp-buffer-size: ${datapath.tcpBufferBytes}")
            add("  udp-recv-buffer-size: ${datapath.udpBufferBytes}")
            add("  udp-copy-buffer-nums: $UDP_COPY_BUFFER_NUMS")
            add("  max-session-count: ${datapath.maxSessions}")
            add("  connect-timeout: $CONNECT_TIMEOUT_MS")
        }.joinToString(separator = "\n", postfix = "\n")
    }
}
