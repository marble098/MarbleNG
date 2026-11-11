package com.marbleng.app.core

object HevTunnelPolicy {
    const val MARKER = CoreSocketPolicy.MARKER

    const val CONNECT_TIMEOUT_MS = 15_000

    const val UDP_COPY_BUFFER_NUMS = 32

    /** Upstream's default, and the floor HEV's own parser enforces for these buffers. */
    const val TASK_STACK_SIZE = 86_016

    // MARBLE_HEV_STACK_HEADROOM_V278 — HEV runs every session on a coroutine stack that
    // hev-task-system mmaps with a guard page below it (CONFIG_STACK_OVERFLOW_DETECTION). An
    // overflow therefore dies as exactly `SIGSEGV / SEGV_ACCERR / "stack pointer is not in a rw
    // map; likely due to stack overflow"` on the thread blocked in HevTunnel.run — the record
    // Android kept returning in the historical process exits. At 86 016 B the 64 KiB TCP splice
    // buffer leaves ~20 KiB for lwIP, the SOCKS handshake, logging and libc underneath it: the
    // upstream minimum, on a pin that tracks upstream's unreleased snapshot. The stacks are
    // anonymous mmaps, committed page by page as they are touched, so a larger reservation
    // costs address space, not RAM. 64-bit processes get 256 KiB; 32-bit ones 128 KiB, because
    // 4 096 sessions there have to fit a 4 GiB address space.
    const val TASK_STACK_SIZE_64BIT = 262_144
    const val TASK_STACK_SIZE_32BIT = 131_072

    fun taskStackSize(is64Bit: Boolean): Int =
        if (is64Bit) TASK_STACK_SIZE_64BIT else TASK_STACK_SIZE_32BIT

    fun buildConfig(
        socksPort: Int,
        mtu: Int,
        ipv6: Boolean,
        logFilePath: String,
        datapath: TunnelTuning,
        fastOpen: Boolean = false,
        taskStackSize: Int = TASK_STACK_SIZE
    ): String {
        require(socksPort in 1..65_535) { "Invalid HEV SOCKS port" }
        require(mtu in 1_200..9_000) { "Invalid HEV MTU" }
        require(datapath.tcpBufferBytes > 0 && datapath.udpBufferBytes > 0) {
            "Invalid HEV datapath buffers"
        }
        require(taskStackSize in TASK_STACK_SIZE..1_048_576) { "Invalid HEV task stack size" }
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
            add("  task-stack-size: $taskStackSize")
            add("  tcp-buffer-size: ${datapath.tcpBufferBytes}")
            add("  udp-recv-buffer-size: ${datapath.udpBufferBytes}")
            add("  udp-copy-buffer-nums: $UDP_COPY_BUFFER_NUMS")
            add("  max-session-count: ${datapath.maxSessions}")
            add("  connect-timeout: $CONNECT_TIMEOUT_MS")
        }.joinToString(separator = "\n", postfix = "\n")
    }
}
