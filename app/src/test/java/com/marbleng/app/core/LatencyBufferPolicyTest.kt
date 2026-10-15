package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyBufferPolicyTest {
    @Test
    fun unknownEvidenceKeepsTheHistoricalBaseline() {
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
        val small = LatencyBufferPolicy.tcpBytes(
            downstreamKbps = 4_000,
            rttMs = 100.0,
            tunedBytes = 262_144
        )
        assertEquals(LatencyBufferPolicy.BASELINE_TCP_BYTES, small)

        val mid = LatencyBufferPolicy.tcpBytes(
            downstreamKbps = 12_000,
            rttMs = 120.0,
            tunedBytes = 262_144
        )
        assertTrue("BDP-sized queue: $mid", mid in 180_000..188_416)
    }

    @Test
    fun theThroughputPassRemainsTheCeiling() {
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

        assertEquals(
            524_288,
            LatencyBufferPolicy.udpBytes(downstreamKbps = 0, rttMs = 0.0, tunedBytes = 524_288)
        )
    }
}
