package com.marbleng.app.core

import kotlin.math.max
import kotlin.math.min

object LatencyBufferPolicy {
    const val BASELINE_TCP_BYTES = 65_536

    const val MAX_TCP_BYTES = 262_144

    fun tcpBytes(downstreamKbps: Int, rttMs: Double, tunedBytes: Int): Int {
        val ceiling = max(BASELINE_TCP_BYTES, min(tunedBytes, MAX_TCP_BYTES))
        val bytesPerSecond = downstreamKbps.toDouble() * 1000.0 / 8.0
        if (bytesPerSecond <= 0.0 || rttMs < MIN_RTT_MS || rttMs > MAX_RTT_MS) return BASELINE_TCP_BYTES
        val bdp = bytesPerSecond * (rttMs / 1000.0)
        if (!bdp.isFinite() || bdp <= 0.0) return BASELINE_TCP_BYTES

        val rounded = ((bdp + 4095.0) / 4096.0).toInt() * 4096
        return rounded.coerceIn(BASELINE_TCP_BYTES, ceiling)
    }

    fun udpBytes(downstreamKbps: Int, rttMs: Double, tunedBytes: Int): Int =
        max(524_288, tcpBytes(downstreamKbps, rttMs, tunedBytes) * 4)
            .coerceAtMost(max(524_288, min(tunedBytes, 1_048_576)))

    private const val MIN_RTT_MS = 20.0
    private const val MAX_RTT_MS = 4_000.0
}
