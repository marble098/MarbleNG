package com.marbleng.app.core

/**
 * Converts kernel TCP_INFO aggregates into conservative stress signals.
 *
 * `lost` is not a packet-loss rate, `unacked` is not the number of packets sent in the sample
 * window, and the minimum MSS/PMTU may come from different sockets. Treating those raw counters as
 * percentages made one retransmission with one unacked segment look like 100% retransmit and 50%
 * loss, which could drive the MTU to its IPv6 floor in a few monitor ticks. Rates below are bounded
 * by the congestion window and socket count; loss and RTT need independent corroboration. MSS is
 * retained as a diagnostic only — stable PMTU decisions belong to [PathMtuPolicy].
 */
object TcpStressTelemetryPolicy {
    data class Assessment(
        val retransmitRate: Double,
        val lossRate: Double,
        val mssRatio: Double,
        val stressed: Boolean,
        /** Packet-counter pressure relevant to MTU decisions; jitter alone is not path-MTU proof. */
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
        // A lone retransmit is a normal recovery event and cannot produce a critical percentage.
        val retransmissionPressure = retransDelta >= maxOf(MIN_RETRANSMITS_FOR_PRESSURE, socketCount * 2)
        val retransmitRate = if (retransmissionPressure) {
            (retransDelta.toDouble() / rateDenominator.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        // TCP_INFO.lost is cumulative for a socket's lifetime. Only its interval delta is evidence;
        // combining the lifetime total with a momentary unacked count creates a fictitious loss rate.
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
