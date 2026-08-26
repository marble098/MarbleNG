package com.marbleng.app.core

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Deterministic rolling link-quality statistics.
 *
 * Outcomes > 0 are verified RTT samples; non-positive values are failures. A failure breaks
 * RTT adjacency, so IPDV is never fabricated across an unknown interval.
 */
object LinkQualityEstimator {
    data class Summary(
        val medianRttMs: Int,
        val p90RttMs: Int,
        val meanIpdvMs: Int,
        val jitterSamples: Int,
        val attempts: Int,
        val successes: Int,
        val successPercent: Int,
        val successLowerBoundPercent: Int
    )

    fun summarize(rawOutcomes: List<Int>): Summary? {
        if (rawOutcomes.isEmpty()) return null
        val outcomes = rawOutcomes.map { if (it > 0) it.coerceIn(1, 10_000) else -1 }
        val rtts = outcomes.filter { it > 0 }
        if (rtts.isEmpty()) return null

        val deltas = outcomes.zipWithNext().mapNotNull { (a, b) ->
            if (a > 0 && b > 0) kotlin.math.abs(b - a).coerceIn(0, 10_000) else null
        }
        val sorted = rtts.sorted()
        val p90Index = ceil(sorted.size * 0.90).toInt().coerceIn(1, sorted.size) - 1
        val successRatio = rtts.size.toDouble() / outcomes.size

        return Summary(
            medianRttMs = sorted[sorted.size / 2],
            p90RttMs = sorted[p90Index],
            meanIpdvMs = if (deltas.isEmpty()) -1 else deltas.average().roundToInt(),
            jitterSamples = deltas.size,
            attempts = outcomes.size,
            successes = rtts.size,
            successPercent = (successRatio * 100.0).roundToInt(),
            successLowerBoundPercent = (wilsonLowerBound(rtts.size, outcomes.size) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        )
    }

    /** 95% Wilson lower confidence bound; stable for tiny rolling windows. */
    private fun wilsonLowerBound(successes: Int, attempts: Int): Double {
        if (attempts <= 0) return 0.0
        val z = 1.959963984540054
        val n = attempts.toDouble()
        val p = successes / n
        val z2OverN = z * z / n
        val center = p + z2OverN / 2.0
        val margin = z * sqrt((p * (1.0 - p) + z * z / (4.0 * n)) / n)
        return ((center - margin) / (1.0 + z2OverN)).coerceIn(0.0, 1.0)
    }
}
