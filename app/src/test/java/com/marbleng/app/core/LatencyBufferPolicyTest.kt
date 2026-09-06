package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_XRAY_THROUGHPUT_V151 — the latency-first buffer cap, pinned.
 *
 * The number this policy returns is the size of the userspace queue the tunnel pushes through, so
 * getting it wrong is not a cosmetic drift: too small and a single stream is throttled to
 * `buffer / RTT` no matter how fast the radio is, which is the "connected but slow" symptom; too
 * large and Marble adds a second queue in front of the physical link and interactive latency pays
 * for it. Both failure modes are asserted here, in both directions.
 */
class LatencyBufferPolicyTest {

    @Test
    fun unknownEvidenceKeepsTheHistoricalBaseline() {
        // No rate, no believable RTT — a guess is not a measurement.
        assertEquals(
            LatencyBufferPolicy.BASELINE_TCP_BYTES,
            LatencyBufferPolicy.tcpBytes(downstreamKbps = 0, rttMs = 200.0, tunedBytes = 262_144)
        )
        assertEquals(
            LatencyBufferPolicy.BASELINE_TCP_BYTES,
            LatencyBufferPolicy.tcpBytes(downstreamKbps = 20_000, rttMs = 0.0, tunedBytes = 262_144)
        )
        assertEquals(
            LatencyBufferPolicy.BASELINE_TCP_BYTES,
            LatencyBufferPolicy.tcpBytes(downstreamKbps = 20_000, rttMs = 12_000.0, tunedBytes = 262_144)
        )
    }

    @Test
    fun aLongFatTunnelGetsEnoughQueueToFillThePipe() {
        // 20 Mbit/s down at 200 ms RTT: BDP = 500 KB, which the ceiling clamps to 256 KiB. The
        // old constant returned 65 536 here — a hard ~2.6 Mbit/s cap on a 20 Mbit/s link.
        val tuned = LatencyBufferPolicy.tcpBytes(
            downstreamKbps = 20_000,
            rttMs = 200.0,
            tunedBytes = 262_144
        )
        assertEquals(LatencyBufferPolicy.MAX_TCP_BYTES, tuned)
        assertTrue(
            "the latency-first queue must exceed the baseline on a long fat path",
            tuned > LatencyBufferPolicy.BASELINE_TCP_BYTES
        )
    }

    @Test
    fun theQueueIsSizedToTheBandwidthDelayProduct() {
        // 4 Mbit/s down at 100 ms RTT: BDP = 50 KB, rounded up to the 52 KiB page boundary… and
        // floored at the baseline, because a queue smaller than the baseline helps nobody.
        val small = LatencyBufferPolicy.tcpBytes(
            downstreamKbps = 4_000,
            rttMs = 100.0,
            tunedBytes = 262_144
        )
        assertEquals(LatencyBufferPolicy.BASELINE_TCP_BYTES, small)

        // 12 Mbit/s at 120 ms RTT: BDP = 180 KB, so the queue lands on the 184 KiB page boundary
        // and stays under the throughput pass's own request.
        val mid = LatencyBufferPolicy.tcpBytes(
            downstreamKbps = 12_000,
            rttMs = 120.0,
            tunedBytes = 262_144
        )
        assertTrue("BDP-sized queue: $mid", mid in 180_000..188_416)
    }

    @Test
    fun theThroughputPassRemainsTheCeiling() {
        // A link may be fat, but the latency-first path never exceeds what the throughput pass
        // asked for: this policy relaxes a cap, it does not raise the product's own limit.
        assertEquals(
            LatencyBufferPolicy.BASELINE_TCP_BYTES,
            LatencyBufferPolicy.tcpBytes(
                downstreamKbps = 50_000,
                rttMs = 300.0,
                tunedBytes = LatencyBufferPolicy.BASELINE_TCP_BYTES
            )
        )
    }

    @Test
    fun udpQueuesAbsorbABurstWithoutExceedingTheDatagramCeiling() {
        val tuned = LatencyBufferPolicy.udpBytes(
            downstreamKbps = 20_000,
            rttMs = 200.0,
            tunedBytes = 1_048_576
        )
        assertTrue("udp queue: $tuned", tuned >= 524_288)
        assertTrue("udp queue: $tuned", tuned <= 1_048_576)

        // Unknown evidence keeps the historical UDP baseline rather than shrinking it: a datagram
        // queue that is too small drops a burst instead of delaying it.
        assertEquals(
            524_288,
            LatencyBufferPolicy.udpBytes(downstreamKbps = 0, rttMs = 0.0, tunedBytes = 524_288)
        )
    }
}
