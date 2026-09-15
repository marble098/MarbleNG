package com.marbleng.app.core

/**
 * MARBLE_SOCKET_FLIGHT_V168 — the hev-socks5-tunnel configuration, built in one pure policy so
 * every TUN→SOCKS data-plane option the pinned 2.17.1 core accepts is declared and pinned here
 * instead of hand-joined inside the Android service.
 *
 * Why the non-default values:
 *
 *  - `socks5.pipeline: true` pipelines the SOCKS5 method selection and CONNECT request, so each
 *    newly opened app connection saves one full SOCKS handshake round trip through HEV to the
 *    local core. Both pinned SOCKS servers (Xray's socks inbound and sing-box's mixed inbound)
 *    read a buffered handshake and accept pipelining; the option exists in hev-socks5-tunnel
 *    precisely for trusted-local-server cases like this one.
 *  - `misc.connect-timeout` covers the SOCKS CONNECT reply, which includes the core dialling the
 *    destination through a fragmented, filtered link. The upstream default is 10 s; a first
 *    write fragmented TLS handshake plus a high-RTT Iran transit can legitimately exceed it, so
 *    the session was killed while a connection was still being born. 15 s stays under the cores'
 *    own 16 s dial ceiling.
 *  - `misc.udp-copy-buffer-nums` raises the splice UDP burst pool (upstream default 10 × 1500 B)
 *    to 32 × 1500 B so a QUIC/Hysteria burst is absorbed instead of dropped and retransmitted.
 *    At 48 000 B it stays below the 64 KiB TCP buffer floor, so it never forces the parser to
 *    raise the 86 016 B task stack; a BDP-enlarged TCP buffer still auto-raises it in C as
 *    before.
 */
object HevTunnelPolicy {

    const val MARKER = CoreSocketPolicy.MARKER

    /** SOCKS CONNECT (upstream dial included) ceiling, ms. */
    const val CONNECT_TIMEOUT_MS = 15_000

    /** UDP splice buffers, 1500 bytes each. Upstream default is 10. */
    const val UDP_COPY_BUFFER_NUMS = 32

    /** Kept explicit; the C parser raises this itself when buffer sizes demand more. */
    const val TASK_STACK_SIZE = 86_016

    /**
     * Build the exact YAML document `HevTunnel.run` consumes.
     *
     * @param ipv6 whether the TUN captured `::/0`; the v6 address is advertised only then, matching
     *   the established service behaviour.
     */
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
            // MARBLE_SOCKET_FLIGHT_V168 — one fewer SOCKS round trip per new app connection.
            add("  pipeline: true")
            // TCP_FASTOPEN_CONNECT (the pinned core's exact implementation) has transparent
            // kernel fallback to a normal three-way handshake when the listener lacks a cookie,
            // and the setsockopt result is discarded — fail-safe. The core-side listeners (Xray
            // socks inbound / sing-box mixed inbound) advertise TFO when the same setting arms it.
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
