package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_TCP_INFO_TRUTH_V238 — the pressure verdict only counts what the kernel actually
 * measured, and one verdict is weather, not climate.
 *
 * The Bug Finder report (P0-1) traced `jitterPressure=true` on healthy paths to sockets that are
 * not path measurements at all: dials to loopback (the local plumbing between TUN and proxy, RTT
 * of a few microseconds reported as ~0) and sockets still in handshake (rtt=0 with the stack's
 * 250 ms rttvar default). The native emitter now reports how many sockets were real
 * (`validSockets`), and the policy refuses to read the RTT columns when that count is zero.
 */
class TcpInfoPressureGateV238Test {

    private fun snapshot(
        rttMs: Int = 40,
        rttVarMs: Int = 250,
        sockets: Int = 4,
        validSockets: Int = 0,
        retransDelta: Int = 0,
        lostDelta: Int = 0
    ) = TransportTelemetrySnapshot(
        atMs = 1_000L,
        sockets = sockets,
        rttMs = rttMs,
        p95RttMs = rttMs,
        rttVarMs = rttVarMs,
        retransDelta = retransDelta,
        totalRetrans = 0,
        lost = 0,
        unacked = 0,
        pmtu = 1_420,
        mss = 1_360,
        cwndPackets = 10,
        pacingBps = 0L,
        deliveryBps = 0L,
        lostDelta = lostDelta,
        validSockets = validSockets
    )

    @Test
    fun `a full snapshot of handshake sockets is never jitter pressure`() {
        // The exact signature from the report: rtt=0 with the 250 ms rttvar default, every
        // socket still in handshake.
        val assessment = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 0, rttVarMs = 250, sockets = 6, validSockets = 0),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        assertFalse(
            "no valid socket means no RTT evidence at all, got jitterPressure=${assessment.jitterPressure}",
            assessment.jitterPressure
        )
        assertFalse(assessment.stressed)
        assertEquals(0, assessment.validSockets)
    }

    @Test
    fun `loopback dials do not become jitter evidence`() {
        // The emitter filters loopback at track time, so a path whose only sockets are local
        // plumbing reports validSockets=0 while `sockets` stays high.
        val assessment = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 4, rttVarMs = 210, sockets = 5, validSockets = 0),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        assertFalse(assessment.jitterPressure)
        assertFalse(assessment.stressed)
    }

    @Test
    fun `one real measured socket keeps the verdict possible`() {
        // validSockets = 2: the columns carry real kernel observations, so a genuinely spiking
        // rttvar may be called pressure again.
        val assessment = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 40, rttVarMs = 250, sockets = 4, validSockets = 2),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        assertTrue(assessment.jitterPressure)
        assertEquals(2, assessment.validSockets)
    }

    @Test
    fun `a measured quiet path is not stressed`() {
        val assessment = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 40, rttVarMs = 20, sockets = 4, validSockets = 4),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        assertFalse(assessment.jitterPressure)
        assertFalse(assessment.stressed)
    }

    @Test
    fun `a pre-V238 core keeps the historic behaviour`() {
        // validSockets = -1 (field absent): the legacy assumption is that every socket counted.
        val assessment = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 40, rttVarMs = 250, sockets = 4, validSockets = -1),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        assertTrue(assessment.jitterPressure)
        assertEquals(4, assessment.validSockets)
    }

    @Test
    fun `one spiky verdict does not stress the tunnel - two in a row do`() {
        val gate = TcpStressTelemetryPolicy.PressureGate()
        val spiky = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 40, rttVarMs = 250, validSockets = 2),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        val quiet = TcpStressTelemetryPolicy.assess(
            snapshot(rttMs = 40, rttVarMs = 20, validSockets = 2),
            activeMtu = 1_420,
            hasIpv6 = false
        )
        assertTrue(spiky.jitterPressure)
        assertFalse(quiet.jitterPressure)

        assertFalse("the first pressured snapshot is weather", gate.observe(spiky.jitterPressure))
        assertTrue("two in a row are climate", gate.observe(spiky.jitterPressure))
        assertFalse("one honest snapshot resets the streak", gate.observe(quiet.jitterPressure))
        assertEquals(0, gate.currentStreak())
        assertFalse(gate.observe(quiet.jitterPressure))
    }
}
