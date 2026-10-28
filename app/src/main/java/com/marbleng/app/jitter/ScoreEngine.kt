package com.marbleng.app.jitter

/**
 * MARBLE_SMART_JITTER_V238 — the pure scorer of the Smart Jitter Reducer.
 *
 * For one path sample the engine returns a 0..100 score in which **jitter and the p95 tail weigh
 * more than the mean RTT, and loss is punished heavily** — a path that answers fast twice and
 * dies once is not a path for a call or a game, and the arithmetic below says so. The logic has
 * no Android import and no clock: the unit tests feed it fixed samples.
 *
 * A window that has not produced [JitterWindow.DEFAULT_MIN_SAMPLES] valid samples is not scored
 * at all (`null`) — the report's P0-2 rule: no decision on cold-start statistics.
 */
object ScoreEngine {

    data class Weights(
        val jitter: Double = 0.35,
        val p95: Double = 0.25,
        val loss: Double = 0.25,
        val rtt: Double = 0.15
    ) {
        init {
            require(jitter >= 0.0 && p95 >= 0.0 && loss >= 0.0 && rtt >= 0.0)
            val total = jitter + p95 + loss + rtt
            require(total > 0.0)
        }

        companion object {
            val DEFAULT = Weights()
        }
    }

    /** A candidate path: its measured window plus static facts that modulate the score. */
    data class PathSample(
        val jitterMs: Double,
        val p95RttMs: Double,
        val rttMeanMs: Double,
        val lossPercent: Double,
        val validSamples: Int,
        val muxCapable: Boolean = false
    )

    fun score(
        sample: PathSample,
        weights: Weights = Weights.DEFAULT,
        minSamples: Int = JitterWindow.DEFAULT_MIN_SAMPLES
    ): Double? {
        if (sample.validSamples < minSamples.coerceAtLeast(1)) return null

        val jitter = normalizedLatencyLike(sample.jitterMs, REFERENCE_JITTER_MS)
        val p95 = normalizedLatencyLike(sample.p95RttMs, REFERENCE_RTT_MS)
        val rtt = normalizedLatencyLike(sample.rttMeanMs, REFERENCE_RTT_MS)
        // Loss is punished super-linearly: the first 5% cost little, everything past 10% is a
        // broken path for interactive traffic.
        val lossFactor = sample.lossPercent.coerceIn(0.0, 100.0) / 100.0
        val lossScore = (1.0 - lossFactor * LOSS_PENALTY_GAIN).coerceIn(0.0, 1.0)

        var total = jitter * weights.jitter +
            p95 * weights.p95 +
            rtt * weights.rtt +
            lossScore * weights.loss
        total = total.coerceIn(0.0, 1.0)

        if (sample.muxCapable && sample.jitterMs >= MUX_HOL_JITTER_MS) {
            // Multiplexed transports share one TCP stream: on a jittery path head-of-line
            // blocking turns every spike into stalls for every stream (report P1-5).
            total *= MUX_JITTER_PENALTY
        }

        return (total * 100.0)
    }

    /** 1.0 at (and below) the reference value, falling hyperbolically for worse values. */
    private fun normalizedLatencyLike(valueMs: Double, referenceMs: Double): Double {
        if (!valueMs.isFinite() || valueMs < 0.0) return 0.5
        return (1.0 / (1.0 + valueMs / referenceMs)).coerceIn(0.0, 1.0)
    }

    const val REFERENCE_JITTER_MS = 25.0
    const val REFERENCE_RTT_MS = 160.0
    const val LOSS_PENALTY_GAIN = 3.0
    const val MUX_HOL_JITTER_MS = 40.0
    const val MUX_JITTER_PENALTY = 0.9
}
