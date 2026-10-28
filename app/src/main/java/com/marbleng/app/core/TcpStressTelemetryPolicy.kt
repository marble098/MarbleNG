package com.marbleng.app.core

import kotlin.math.max

object TcpStressTelemetryPolicy {
    data class Assessment(
        val retransmitRate: Double,
        val lossRate: Double,
        val mssRatio: Double,
        val stressed: Boolean,
        val mtuRelevantStress: Boolean,
        val retransmissionPressure: Boolean,
        val lossPressure: Boolean,
        val jitterPressure: Boolean,
        /** Sockets whose TCP_INFO was a real, established, non-loopback measurement (V238). */
        val validSockets: Int = 0
    )

    fun assess(
        sample: TransportTelemetrySnapshot,
        activeMtu: Int,
        hasIpv6: Boolean,
        validSockets: Int = sample.validSockets
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

        // MARBLE_TCP_INFO_TRUTH_V238 — the jitter verdict may only come from sockets the native
        // telemetry actually measured: established, non-loopback, with a real kernel RTT. The
        // Bug Finder report (P0-1) caught handshake sockets (rtt=0, rttvar=250 default) and
        // loopback dials (rtt≈4 ms) feeding a median that then flagged `jitterPressure=true` on
        // a healthy path. With no valid socket the RTT column is empty, not fast — and empty
        // columns are never jitter evidence.
        // A snapshot from a pre-V238 core has no `validSockets` field (-1): keep the historic
        // behaviour for it by assuming every socket was valid. Fresh cores report the truth.
        val valid = if (validSockets < 0) sample.sockets else validSockets
        val rttKnown = valid > 0 && sample.rttMs in 1..MAX_RTT_MS
        val jitterPressure = rttKnown &&
            sample.rttVarMs >= maxOf(
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
            jitterPressure = jitterPressure,
            validSockets = valid
        )
    }

    /**
     * MARBLE_TCP_INFO_TRUTH_V238 — a pressure verdict needs [requiredStreak] consecutive
     * pressured assessments. One snapshot with a spiking rttvar (a burst of new handshakes does
     * exactly that, even measured honestly) is weather, not climate; the report asked for
     * "N valid samples in a row" before `stressed=true` may drive a probe or a rerank.
     */
    class PressureGate(private val requiredStreak: Int = DEFAULT_REQUIRED_STREAK) {
        private var streak = 0

        fun observe(pressured: Boolean): Boolean {
            streak = if (pressured) streak + 1 else 0
            return streak >= requiredStreak.coerceAtLeast(1)
        }

        fun currentStreak(): Int = streak

        fun reset() {
            streak = 0
        }

        companion object {
            const val DEFAULT_REQUIRED_STREAK = 2
        }
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
