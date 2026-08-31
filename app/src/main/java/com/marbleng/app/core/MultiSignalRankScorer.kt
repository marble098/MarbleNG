package com.marbleng.app.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-signal weighted ranking model (MARBLE_SMART_RANK_V90).
 *
 * Replaces the old "one short HTTPS generate204 probe" pass/fail criterion with a weighted
 * composite built from several survivability signals:
 *
 *  - TCP handshake success ratio (over all attempts),
 *  - RTT median and p95,
 *  - jitter,
 *  - TCP retransmission rate,
 *  - packet-loss rate,
 *  - live session lifetime.
 *
 * The crucial behaviour for Iran's filtering environment: a single timeout is NOT a death
 * sentence. A node is only classified [Classification.DEAD] after it has been convicted by
 * multiple attempts (the probe layer performs 2-3 attempts with exponential backoff before a
 * final failure), and even then an explicit `uncertain` signal keeps it in
 * [Classification.UNCERTAIN] instead of penalising the whole node for one inconclusive pass.
 *
 * Pure and dependency-free so the weighting, classification and uncertain handling are all
 * deterministic in JVM unit tests.
 */
object MultiSignalRankScorer {

    enum class Classification(val rank: Int) {
        HEALTHY(0),
        DEGRADED(1),
        UNCERTAIN(2),
        DEAD(3),
        INVALID(4)
    }

    /** All the signals a completed rank pass can contribute for one node. */
    data class Signals(
        /** Successful handshakes / total handshake attempts, 0.0..1.0. */
        val tcpHandshakeSuccessRatio: Double = 0.0,
        val handshakeAttempts: Int = 0,
        val rttMedianMs: Double = UNKNOWN_RTT,
        val rttP95Ms: Double = UNKNOWN_RTT,
        val jitterMs: Double = 0.0,
        /** Fraction of retransmitted segments, 0.0..1.0. */
        val retransmitRate: Double = 0.0,
        val lossRate: Double = 0.0,
        val sessionLifetimeMs: Long = 0L,
        /** Inconclusive measurement (e.g. TURBO live-inconclusive-backoff): hold, do not penalise. */
        val uncertain: Boolean = false,
        val structurallyInvalid: Boolean = false,
        /** Deprecated/hidden node (VLESS without TLS/REALITY, VMess without forward secrecy). */
        val deprecated: Boolean = false
    )

    data class Score(
        val score: Double,
        val classification: Classification,
        val reason: String
    )

    /** A node is only convicted after this many failed attempts with exponential backoff. */
    const val MIN_ATTEMPTS_TO_CONVICT = 3

    const val UNKNOWN_RTT = Double.MAX_VALUE

    private const val MAX_RETRANSMIT = 0.15
    private const val MAX_LOSS = 0.20
    private const val HEALTHY_SESSION_MS = 120_000L

    private const val W_RELIABILITY = 0.35
    private const val W_LATENCY = 0.20
    private const val W_TAIL = 0.10
    private const val W_JITTER = 0.15
    private const val W_PACKET = 0.15
    private const val W_LONGEVITY = 0.05

    /**
     * Compute the weighted multi-signal score and classification for a node.
     */
    fun score(signals: Signals): Score {
        if (signals.structurallyInvalid) {
            return Score(0.0, Classification.INVALID, "structurally-invalid")
        }
        if (signals.deprecated) {
            return Score(0.0, Classification.INVALID, "deprecated-hidden")
        }

        val attempts = signals.handshakeAttempts.coerceAtLeast(0)
        val ratio = signals.tcpHandshakeSuccessRatio.coerceIn(0.0, 1.0)
        val rttUsable = signals.rttMedianMs.isFinite() && signals.rttMedianMs in 1.0..9_000.0
        val packetDegraded = signals.retransmitRate >= MAX_RETRANSMIT ||
            signals.lossRate >= MAX_LOSS

        val classification = when {
            signals.sessionLifetimeMs >= HEALTHY_SESSION_MS -> Classification.HEALTHY
            ratio >= 0.999 && attempts >= 1 && rttUsable ->
                if (packetDegraded) Classification.DEGRADED else Classification.HEALTHY
            ratio >= 0.5 -> Classification.DEGRADED
            signals.uncertain || attempts < MIN_ATTEMPTS_TO_CONVICT -> Classification.UNCERTAIN
            else -> Classification.DEAD
        }

        val weighted = when (classification) {
            Classification.INVALID -> 0.0
            Classification.DEAD -> 0.0
            else -> {
                val reliability = ratio * 100.0
                val latency = latencyScore(signals.rttMedianMs, 300.0)
                val tail = latencyScore(signals.rttP95Ms, 420.0)
                val jitter = 100.0 * exp(-signals.jitterMs.coerceAtLeast(0.0) / 40.0)
                val packet = packetHealthScore(signals.retransmitRate, signals.lossRate)
                val longevity = min(25.0, signals.sessionLifetimeMs.coerceAtLeast(0L) / 60_000.0 * 1.0)

                val composite = reliability * W_RELIABILITY +
                    latency * W_LATENCY +
                    tail * W_TAIL +
                    jitter * W_JITTER +
                    packet * W_PACKET +
                    longevity * W_LONGEVITY

                if (classification == Classification.UNCERTAIN) {
                    // Inconclusive evidence: shrink toward a neutral-but-liveable floor so the node
                    // stays eligible for selection instead of being dropped to zero.
                    composite * 0.45 + 25.0
                } else {
                    composite
                }
            }
        }.coerceIn(0.0, 100.0)

        return Score(
            score = weighted,
            classification = classification,
            reason = reasonFor(signals, classification)
        )
    }

    private fun latencyScore(value: Double, scale: Double): Double {
        if (!value.isFinite() || value <= 0.0 || value >= 9_000.0) return 50.0
        return 100.0 * exp(-value.coerceAtLeast(0.0) / scale)
    }

    private fun packetHealthScore(retransmit: Double, loss: Double): Double {
        var score = 100.0
        val r = retransmit.coerceAtLeast(0.0)
        val l = loss.coerceAtLeast(0.0)
        score -= when {
            r <= 0.02 -> 0.0
            r <= 0.05 -> 10.0
            r <= 0.10 -> 25.0
            r <= MAX_RETRANSMIT -> 45.0
            else -> 70.0
        }
        score -= when {
            l <= 0.01 -> 0.0
            l <= 0.05 -> 15.0
            l <= 0.10 -> 30.0
            l <= MAX_LOSS -> 55.0
            else -> 80.0
        }
        return score.coerceIn(0.0, 100.0)
    }

    private fun reasonFor(signals: Signals, classification: Classification): String {
        val parts = mutableListOf<String>()
        parts += "class=" + classification.name.lowercase()
        parts += "attempts=" + signals.handshakeAttempts.coerceAtLeast(0)
        parts += "ratio=" + String.format(java.util.Locale.US, "%.2f", signals.tcpHandshakeSuccessRatio.coerceIn(0.0, 1.0))
        if (signals.rttMedianMs.isFinite() && signals.rttMedianMs < 9_000.0) {
            parts += "rtt=" + signals.rttMedianMs.toInt()
        }
        if (signals.retransmitRate > 0.02) {
            parts += "retrans=" + String.format(java.util.Locale.US, "%.2f", signals.retransmitRate)
        }
        if (signals.lossRate > 0.01) {
            parts += "loss=" + String.format(java.util.Locale.US, "%.2f", signals.lossRate)
        }
        if (signals.uncertain) parts += "uncertain"
        if (signals.sessionLifetimeMs > 0) parts += "session=" + (signals.sessionLifetimeMs / 1000L)
        return parts.joinToString("|")
    }
}
