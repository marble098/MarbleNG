package com.marbleng.app.core

import kotlin.math.max
import kotlin.math.min

/**
 * MARBLE_XRAY_THROUGHPUT_V151 — how big Marble's userspace queues may get on a latency-first link.
 *
 * The datapath has always carried a bufferbloat guard: bulk-shaped socket buffers let a cellular
 * burst sit in Marble/HEV instead of in the radio's own queue, which buys throughput and pays for
 * it in latency, so the interactive profiles were clamped back to the 64 KiB baseline.
 *
 * The clamp was a constant, and that is the part this replaces. A socket buffer only limits
 * throughput once it is smaller than the bandwidth-delay product of the path — the bytes in flight
 * on a full pipe. On a 200 ms Iran-to-Europe tunnel a 64 KiB queue caps a single stream at
 * 64 KiB / 0.2 s ≈ 2.6 Mbit/s no matter what the radio can do, which is precisely the "connected,
 * signal is fine, everything is slow" report this policy exists for. It also explains why the
 * symptom showed up on the tunnel and never on the same phone's direct traffic: the kernel sizes
 * its own buffers, Marble's clamp only ever applied to the proxy path.
 *
 * So the guard now sizes the queue to the measured BDP: enough to keep the pipe full, and not one
 * byte more, which is the actual definition of not adding a second queue. With no rate or no
 * believable RTT to go on it returns the historical baseline, because a guess dressed up as
 * evidence is what got the product here.
 */
object LatencyBufferPolicy {

    /** The pre-V151 constant. Still the answer whenever there is nothing measured to size from. */
    const val BASELINE_TCP_BYTES = 65_536

    /** A single stream is never given more than this, however fat the measured pipe is. */
    const val MAX_TCP_BYTES = 262_144

    /**
     * The TCP buffer a latency-first session may use.
     *
     * @param downstreamKbps measured or reported downlink rate of the physical underlay; `<= 0`
     *   means "unknown", which is not the same as "slow".
     * @param rttMs measured round-trip time to the node. Values outside [MIN_RTT_MS, MAX_RTT_MS]
     *   are treated as unknown: an RTT of 0 is an unmeasured path and an RTT in the seconds is a
     *   stalled one, and neither should be multiplied by a bit rate.
     * @param tunedBytes what the throughput pass asked for, which is the ceiling this may reach.
     */
    fun tcpBytes(downstreamKbps: Int, rttMs: Double, tunedBytes: Int): Int {
        val ceiling = max(BASELINE_TCP_BYTES, min(tunedBytes, MAX_TCP_BYTES))
        val bytesPerSecond = downstreamKbps.toDouble() * 1000.0 / 8.0
        if (bytesPerSecond <= 0.0 || rttMs < MIN_RTT_MS || rttMs > MAX_RTT_MS) return BASELINE_TCP_BYTES
        val bdp = bytesPerSecond * (rttMs / 1000.0)
        if (!bdp.isFinite() || bdp <= 0.0) return BASELINE_TCP_BYTES
        // Round up to the page the kernel would have rounded to anyway; a buffer that is 200 bytes
        // short of the BDP still throttles the last segment of every window.
        val rounded = ((bdp + 4095.0) / 4096.0).toInt() * 4096
        return rounded.coerceIn(BASELINE_TCP_BYTES, ceiling)
    }

    /**
     * The UDP companion. Datagram queues do not shape a stream, they absorb a burst, so the same
     * BDP argument applies with a wider floor: too small and a Hy2/QUIC burst is dropped instead
     * of delayed, which is a retransmit the tunnel pays for in latency anyway.
     */
    fun udpBytes(downstreamKbps: Int, rttMs: Double, tunedBytes: Int): Int =
        max(524_288, tcpBytes(downstreamKbps, rttMs, tunedBytes) * 4)
            .coerceAtMost(max(524_288, min(tunedBytes, 1_048_576)))

    private const val MIN_RTT_MS = 20.0
    private const val MAX_RTT_MS = 4_000.0
}
