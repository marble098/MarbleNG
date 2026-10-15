package com.marbleng.app.core

object TcpStressTelemetryPolicy {
    data class Assessment(
        val retransmitRate: Double,
        val lossRate: Double,
        val mssRatio: Double,
        val stressed: Boolean,
        val mtuRelevantStress: Boolean,
        val retransmissionPressure: Boolean,
        val lossPressure: Boolean,
        val jitterPressure: Boolean
    )

    fun assess(
        sample: TransportTelemetrySnapshot,
        activeMtu: Int,
        hasIpv6: Boolean
    ): Assessment {
        val socketCount = sample.sockets.coerceIn(1, 64)
        val retransDelta = sample.retransDelta.coerceAtLeast(0)
        val congestionWindow = sample.cwndPackets.coerceAtLeast(0).toLong() * socketCount
        val rateDenominator = maxOf(MIN_RATE_DENOMINATOR, congestionWindow)

        val retransmissionPressure = retransDelta >= maxOf(MIN_RETRANSMITS_FOR_PRESSURE, socketCount * 2)
        val retransmitRate = if (retransmissionPressure) {
            (retransDelta.toDouble() / rateDenominator.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val lostDelta = sample.lostDelta.coerceAtLeast(0)
        val unacked = sample.unacked.coerceAtLeast(0)
        val lossPressure = lostDelta >= maxOf(MIN_LOST_FOR_PRESSURE, socketCount * 2) &&
            unacked >= maxOf(MIN_UNACKED_FOR_LOSS, socketCount)
        val lossRate = if (lossPressure) {
            (lostDelta.toDouble() / rateDenominator.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val rttKnown = sample.rttMs in 1..MAX_RTT_MS
        val jitterPressure = rttKnown && sample.rttVarMs >= maxOf(
            MIN_RTT_VARIANCE_MS,
            (sample.rttMs * RTT_VARIANCE_RATIO).toInt()
        )

        val expectedMss = (activeMtu - if (hasIpv6) IPV6_TCP_OVERHEAD else IPV4_TCP_OVERHEAD)
            .coerceAtLeast(1)
        val mssRatio = if (sample.mss in MIN_PLAUSIBLE_MSS..MAX_PLAUSIBLE_MSS) {
            (sample.mss.toDouble() / expectedMss.toDouble()).coerceIn(0.0, 2.0)
        } else {
            1.0
        }

        return Assessment(
            retransmitRate = retransmitRate,
            lossRate = lossRate,
            mssRatio = mssRatio,
            stressed = retransmissionPressure || lossPressure || jitterPressure,
            mtuRelevantStress = retransmissionPressure || lossPressure,
            retransmissionPressure = retransmissionPressure,
            lossPressure = lossPressure,
            jitterPressure = jitterPressure
        )
    }

    private const val MIN_RATE_DENOMINATOR = 10L
    private const val MIN_RETRANSMITS_FOR_PRESSURE = 3
    private const val MIN_LOST_FOR_PRESSURE = 3
    private const val MIN_UNACKED_FOR_LOSS = 2
    private const val MIN_RTT_VARIANCE_MS = 150
    private const val RTT_VARIANCE_RATIO = 0.5
    private const val MAX_RTT_MS = 8_000
    private const val IPV4_TCP_OVERHEAD = 40
    private const val IPV6_TCP_OVERHEAD = 60
    private const val MIN_PLAUSIBLE_MSS = 256
    private const val MAX_PLAUSIBLE_MSS = 9_000
}
