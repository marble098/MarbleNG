package com.marbleng.app.jitter

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * MARBLE_SMART_JITTER_V238 — the sliding measurement window of the Smart Jitter Reducer.
 *
 * One [JitterWindow] holds the last [capacity] probe outcomes of ONE target over ONE path. It is
 * pure Kotlin with a fixed number of allocations at construction (the ring), so the probe loop
 * adds samples without touching the data plane and without per-sample garbage beyond the boxed
 * doubles Kotlin gives it — the probe runs at most once every few seconds, never per packet.
 *
 * The statistics follow the two rules the Bug Finder report (v11.2.3, P0-2) pinned for the
 * legacy probe and that this module inherits:
 *
 *  - **Cold samples are excluded.** The first probe of a fresh TCP+TLS connection is dominated
 *    by the handshake, not by the path. It is stored with `cold = true` and contributes to
 *    neither the RTT statistics nor the jitter deltas, only to the attempt count.
 *  - **Failures are losses, not zeros.** A timed-out probe enters the window as `ok = false`;
 *    a loss never lowers a latency figure, it raises the loss percentage.
 *
 * Jitter is the RFC 3550 §A.8 estimate: `J = J + (|D| - J) / 16` over consecutive valid
 * inter-probe delays, where `D` is the difference of two consecutive RTT samples. Exactly one
 * EWMA state is kept per window; a cold or failed sample breaks the delta chain instead of
 * feeding a handshake spike into it.
 */
class JitterWindow(private val capacity: Int = DEFAULT_CAPACITY) {

    data class Snapshot(
        val samples: Int,
        val validSamples: Int,
        val ready: Boolean,
        val rttMeanMs: Double,
        val jitterMs: Double,
        val rttStdDevMs: Double,
        val p95RttMs: Double,
        val lossPercent: Double,
        val lastRttMs: Double,
        val series: List<Double>
    )

    private class Entry(val rttMs: Double, val ok: Boolean, val cold: Boolean)

    private val ring = ArrayDeque<Entry>(capacity.coerceIn(MIN_CAPACITY, MAX_CAPACITY))
    private val limit = capacity.coerceIn(MIN_CAPACITY, MAX_CAPACITY)
    private var jitterEwmaMs = -1.0
    private var previousValidRttMs = -1.0
    private var totalAttempts = 0
    private var totalFailures = 0

    val size: Int get() = ring.size

    /**
     * Adds one probe outcome. [rttMs] is ignored when [ok] is `false` (a timeout has no latency);
     * pass [cold] = `true` for the first sample of a freshly (re)established connection.
     */
    fun add(rttMs: Double, ok: Boolean = true, cold: Boolean = false) {
        val healthy = ok && rttMs.isFinite() && rttMs > 0.0
        val entry = Entry(if (healthy) rttMs.coerceIn(1.0, 30_000.0) else 0.0, healthy, cold)
        ring.addLast(entry)
        while (ring.size > limit) ring.removeFirst()
        totalAttempts++
        if (!healthy) totalFailures++

        if (!healthy || cold) {
            // A break in the chain: RFC 3550 deltas need two consecutive in-path samples.
            previousValidRttMs = -1.0
            return
        }
        val sample = entry.rttMs
        if (previousValidRttMs > 0.0) {
            val delta = abs(sample - previousValidRttMs)
            jitterEwmaMs = if (jitterEwmaMs < 0.0) delta else jitterEwmaMs + (delta - jitterEwmaMs) / RFC3550_JITTER_GAIN
        }
        previousValidRttMs = sample
    }

    /** Statistics over the current window; `null` until [minSamples] valid samples are present. */
    fun summarize(minSamples: Int = DEFAULT_MIN_SAMPLES): Snapshot? {
        val valid = ring.filter { it.ok && !it.cold }.map { it.rttMs }
        if (valid.size < minSamples.coerceAtLeast(1)) return null

        val mean = valid.average()
        val variance = if (valid.size > 1) {
            valid.sumOf { (it - mean) * (it - mean) } / (valid.size - 1)
        } else {
            0.0
        }
        val sorted = valid.sorted()
        val losses = ring.count { !it.ok }
        val attemptsInWindow = ring.size
        val lossPercent = if (attemptsInWindow == 0) {
            0.0
        } else {
            (losses * 100.0 / attemptsInWindow)
        }
        return Snapshot(
            samples = attemptsInWindow,
            validSamples = valid.size,
            ready = true,
            rttMeanMs = mean,
            jitterMs = (if (jitterEwmaMs < 0.0) 0.0 else jitterEwmaMs),
            rttStdDevMs = sqrt(variance.coerceAtLeast(0.0)),
            p95RttMs = percentile(sorted, 0.95),
            lossPercent = lossPercent.coerceIn(0.0, 100.0),
            lastRttMs = valid.last(),
            series = valid
        )
    }

    /** Cumulative attempt/failure counters since [clear] — the live UI's honest loss figure. */
    fun cumulativeLossPercent(): Double =
        if (totalAttempts == 0) 0.0 else (totalFailures * 100.0 / totalAttempts).coerceIn(0.0, 100.0)

    fun cumulativeAttempts(): Int = totalAttempts

    companion object {
        const val DEFAULT_CAPACITY = 30
        const val DEFAULT_MIN_SAMPLES = 8

        /** RFC 3550 §A.8: J = J + (|D| − J) / 16. */
        const val RFC3550_JITTER_GAIN = 16.0

        private const val MIN_CAPACITY = 4
        private const val MAX_CAPACITY = 240

        fun percentile(sorted: List<Double>, q: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val index = ceil(sorted.size * q.coerceIn(0.0, 1.0)).toInt().coerceIn(1, sorted.size) - 1
            return sorted[index]
        }
    }
}
