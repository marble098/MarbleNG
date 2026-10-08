package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpStressTelemetryPolicyTest {
    private fun sample(
        retrans: Int = 0,
        lost: Int = 0,
        lostDelta: Int = lost,
        unacked: Int = 0,
        rtt: Int = 0,
        rttVar: Int = 0,
        sockets: Int = 1,
        cwnd: Int = 1,
        mss: Int = 1_460,
        pmtu: Int = 1_500
    ) = TransportTelemetrySnapshot(
        atMs = 1_000L,
        sockets = sockets,
        rttMs = rtt,
        p95RttMs = rtt,
        rttVarMs = rttVar,
        retransDelta = retrans,
        totalRetrans = retrans,
        lost = lost,
        unacked = unacked,
        pmtu = pmtu,
        mss = mss,
        cwndPackets = cwnd,
        pacingBps = 0L,
        deliveryBps = 0L,
        lostDelta = lostDelta
    )

    @Test
    fun oneRetransmissionOneLostSegmentAndUnknownRttAreNotPercentages() {
        val assessment = TcpStressTelemetryPolicy.assess(
            sample(retrans = 1, lost = 1, unacked = 1, rtt = 0, rttVar = 250, mss = 524),
            activeMtu = 1_500,
            hasIpv6 = false
        )
        assertEquals(0.0, assessment.retransmitRate, 0.0)
        assertEquals(0.0, assessment.lossRate, 0.0)
        assertFalse(assessment.jitterPressure)
        assertFalse(assessment.stressed)
        // Low MSS remains visible to diagnostics but is not proof that the TUN MTU should shrink.
        assertTrue(assessment.mssRatio < 0.55)
    }

    @Test
    fun cumulativeSocketLossIsNotMistakenForNewLossAndJitterAloneDoesNotResizeMtu() {
        val assessment = TcpStressTelemetryPolicy.assess(
            sample(lost = 500, lostDelta = 0, unacked = 20, rtt = 1_000, rttVar = 700),
            activeMtu = 1_500,
            hasIpv6 = false
        )
        assertFalse(assessment.lossPressure)
        assertTrue(assessment.stressed)
        assertFalse(assessment.mtuRelevantStress)
        assertTrue(assessment.jitterPressure)
    }

    @Test
    fun multipleCountersAndKnownHighJitterProduceCorroboratedStress() {
        val assessment = TcpStressTelemetryPolicy.assess(
            sample(
                retrans = 6, lost = 5, unacked = 6, rtt = 1_000, rttVar = 600,
                sockets = 2, cwnd = 10, mss = 1_440
            ),
            activeMtu = 1_500,
            hasIpv6 = true
        )
        assertTrue(assessment.retransmissionPressure)
        assertTrue(assessment.lossPressure)
        assertTrue(assessment.jitterPressure)
        assertTrue(assessment.stressed)
        assertTrue(assessment.retransmitRate in 0.0..1.0)
        assertTrue(assessment.lossRate in 0.0..1.0)
        assertEquals(1.0, assessment.mssRatio, 0.001)
    }

    @Test
    fun lowMssAloneCannotClaimTransportStress() {
        val assessment = TcpStressTelemetryPolicy.assess(
            sample(rtt = 1_000, rttVar = 100, mss = 256),
            activeMtu = 1_500,
            hasIpv6 = false
        )
        assertFalse(assessment.stressed)
        assertFalse(assessment.retransmissionPressure)
        assertFalse(assessment.lossPressure)
        assertFalse(assessment.jitterPressure)
    }
}
