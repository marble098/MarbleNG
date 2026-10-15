package com.marbleng.app.core

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

object HighJitterShield {
    enum class Verdict {
        CALM,

        ELEVATED,

        SEVERE,

        EXTREME
    }

    class RobustWindow(capacity: Int = DEFAULT_WINDOW) {
        private val ring: DoubleArray
        private val scratch: DoubleArray

        init {
            val size = capacity.coerceIn(MIN_WINDOW, MAX_WINDOW)
            ring = DoubleArray(size)
            scratch = DoubleArray(size)
        }

        private var filled = 0
        private var cursor = 0
        private var dirty = true
        private var cachedMedian = 0.0
        private var cachedQ1 = 0.0
        private var cachedQ3 = 0.0
        private var cachedMad = 0.0
        private var cachedP95 = 0.0
        private var cachedSpikeRatio = 0.0

        var evaluations: Int = 0
            private set

        val size: Int get() = filled

        val limit: Int get() = ring.size

        fun clear() {
            filled = 0
            cursor = 0
            dirty = true
        }

        fun add(rttMs: Double) {
            if (!rttMs.isFinite() || rttMs <= 0.0) return
            ring[cursor] = rttMs
            cursor = (cursor + 1) % ring.size
            if (filled < ring.size) filled++
            dirty = true
        }

        fun medianMs(): Double {
            recomputeIfNeeded()
            return cachedMedian
        }

        fun p95Ms(): Double {
            recomputeIfNeeded()
            return cachedP95
        }

        fun iqrMs(): Double {
            recomputeIfNeeded()
            return (cachedQ3 - cachedQ1).coerceAtLeast(0.0)
        }

        fun madMs(): Double {
            recomputeIfNeeded()
            return cachedMad
        }

        fun spikeRatio(): Double {
            recomputeIfNeeded()
            return cachedSpikeRatio
        }

        fun robustJitterMs(): Double {
            recomputeIfNeeded()
            return ((cachedQ3 - cachedQ1) / IQR_TO_SIGMA).coerceAtLeast(0.0)
        }

        private fun recomputeIfNeeded() {
            if (!dirty) return
            evaluations++
            dirty = false
            if (filled == 0) {
                cachedMedian = 0.0
                cachedQ1 = 0.0
                cachedQ3 = 0.0
                cachedMad = 0.0
                cachedP95 = 0.0
                cachedSpikeRatio = 0.0
                return
            }

            for (index in 0 until filled) {
                scratch[index] = ring[(cursor - filled + index + ring.size) % ring.size]
            }
            Arrays.sort(scratch, 0, filled)
            cachedMedian = quantile(scratch, filled, 0.50)
            cachedP95 = quantile(scratch, filled, 0.95)
            cachedQ1 = quantile(scratch, filled, 0.25)
            cachedQ3 = quantile(scratch, filled, 0.75)

            val iqr = (cachedQ3 - cachedQ1).coerceAtLeast(0.0)
            val low = cachedQ1 - SPIKE_IQR_FACTOR * iqr
            val high = cachedQ3 + SPIKE_IQR_FACTOR * iqr
            var spikes = 0
            for (index in 0 until filled) {
                val value = scratch[index]
                if (value < low || value > high) spikes++
            }
            cachedSpikeRatio = spikes.toDouble() / filled.toDouble()

            for (index in 0 until filled) {
                scratch[index] = abs(scratch[index] - cachedMedian)
            }
            Arrays.sort(scratch, 0, filled)
            cachedMad = quantile(scratch, filled, 0.50)
        }

        private fun quantile(sorted: DoubleArray, n: Int, q: Double): Double {
            if (n <= 0) return 0.0
            if (n == 1) return sorted[0]
            val position = q * (n - 1).toDouble()
            val low = floor(position).toInt().coerceIn(0, n - 1)
            val high = (low + 1).coerceAtMost(n - 1)
            return sorted[low] + (sorted[high] - sorted[low]) * (position - low)
        }
    }

    data class Sample(
        val rttMs: Double,
        val nowMs: Long,
        val verified: Boolean = true,
        val isRadioWakeup: Boolean = false
    )

    data class State(
        val level: Double = 0.0,
        val baselineJitterMs: Double = 0.0,
        val baselineSamples: Int = 0,
        val lastSampleAtMs: Long = 0L,
        val escalatedAtMs: Long = 0L,
        val severeStreak: Int = 0,
        val calmStreak: Int = 0,
        val verdict: Verdict = Verdict.CALM,
        val lastRerankAtMs: Long = 0L,
        val escalations: Int = 0
    )

    data class Plan(
        val verdict: Verdict,
        val level: Double,
        val jitterMs: Double,
        val medianRttMs: Double,
        val p95RttMs: Double,
        val spikeRatio: Double,
        val baselineJitterMs: Double,
        val samples: Int,
        val probeBurst: Int,
        val probeEveryTicks: Int,
        val bufferScale: Double,
        val requestRerank: Boolean,
        val reason: String,
        val isRadioWakeup: Boolean = false
    )

    data class Decision(val state: State, val plan: Plan)

    const val RRC_IDLE_GAP_MS = 6_000L

    const val DEFAULT_WINDOW = 24
    const val MIN_WINDOW = 6
    const val MAX_WINDOW = 64

    const val MIN_SAMPLES = 6

    const val IQR_TO_SIGMA = 1.349

    const val SPIKE_IQR_FACTOR = 1.5

    const val SPIKE_SEVERE_RATIO = 0.15

    const val ABSOLUTE_CALM_MS = 22.0

    const val BASELINE_HEADROOM = 1.8

    const val BASELINE_SEED_MAX_MS = 150.0

    const val ALPHA_BASELINE = 0.10

    const val ALPHA_ATTACK = 0.55
    const val ALPHA_RELEASE = 0.06
    const val ALPHA_IDLE = 0.25

    const val IDLE_AFTER_MS = 20_000L

    const val MIN_HOLD_MS = 20_000L

    const val BASE_TICKS = 30
    const val MIN_TICKS = 6
    const val TICK_MS = 1_000L

    const val MIN_BURST = 2

    const val MAX_BURST = 3

    const val MAX_PROBES_PER_MINUTE = 30.0

    const val DEGRADED_LEVEL = 0.5

    const val BUFFER_SPAN = 1.5

    const val RERANK_STREAK = 3

    const val RERANK_COOLDOWN_MS = 180_000L

    private fun targetFor(verdict: Verdict): Double = when (verdict) {
        Verdict.CALM -> 0.0
        Verdict.ELEVATED -> 0.35
        Verdict.SEVERE -> 0.70
        Verdict.EXTREME -> 1.0
    }

    fun observe(
        sample: Sample,
        state: State,
        window: RobustWindow,
        nowMs: Long = sample.nowMs
    ): Decision {
        val isIdleWakeup = sample.isRadioWakeup

        if (!isIdleWakeup) {
            window.add(if (sample.verified) sample.rttMs else 0.0)
        }
        val samples = window.size
        val jitter = window.robustJitterMs()
        val median = window.medianMs()
        val p95 = window.p95Ms()
        val spikes = window.spikeRatio()

        val canGrade = samples >= MIN_SAMPLES
        val seedable = canGrade && state.baselineJitterMs <= 0.0 && jitter <= BASELINE_SEED_MAX_MS
        val calmNow = canGrade &&
            jitter <= max(ABSOLUTE_CALM_MS, state.baselineJitterMs * BASELINE_HEADROOM)
        val baseline = when {
            !canGrade -> state.baselineJitterMs
            seedable -> jitter
            calmNow -> state.baselineJitterMs + (jitter - state.baselineJitterMs) * ALPHA_BASELINE
            else -> state.baselineJitterMs
        }
        val baselineSamples = if (seedable || calmNow) state.baselineSamples + 1 else state.baselineSamples

        val reference = max(ABSOLUTE_CALM_MS, baseline * BASELINE_HEADROOM)
        val ratio = if (reference > 0.0) jitter / reference else 0.0
        val measured = when {
            samples < MIN_SAMPLES -> Verdict.CALM
            ratio < 1.0 -> Verdict.CALM
            ratio < 2.0 -> Verdict.ELEVATED
            ratio < 4.0 -> Verdict.SEVERE
            else -> Verdict.EXTREME
        }
        val verdict = if (samples >= MIN_SAMPLES && spikes >= SPIKE_SEVERE_RATIO) {
            escalate(measured)
        } else {
            measured
        }

        val target = targetFor(verdict)
        val idle = state.lastSampleAtMs > 0L && nowMs - state.lastSampleAtMs >= IDLE_AFTER_MS
        val attacking = target > state.level
        val alpha = when {
            idle -> ALPHA_IDLE
            attacking -> ALPHA_ATTACK
            else -> ALPHA_RELEASE
        }
        var level = state.level + (target - state.level) * alpha

        val holding = state.escalatedAtMs > 0L && nowMs - state.escalatedAtMs < MIN_HOLD_MS
        if (holding) level = max(level, state.level)
        level = level.coerceIn(0.0, 1.0)

        val escalated = verdict.ordinal >= Verdict.SEVERE.ordinal &&
            state.verdict.ordinal < Verdict.SEVERE.ordinal
        val severeStreak = if (verdict.ordinal >= Verdict.SEVERE.ordinal) state.severeStreak + 1 else 0
        val calmStreak = if (verdict == Verdict.CALM) state.calmStreak + 1 else 0

        val burst = burstFor(level)
        val wantedTicks = (BASE_TICKS - (BASE_TICKS - MIN_TICKS) * level).toInt().coerceAtLeast(MIN_TICKS)
        val probeEveryTicks = budgetedTicks(wantedTicks, burst)
        val requestRerank = verdict == Verdict.EXTREME &&
            severeStreak >= RERANK_STREAK &&
            nowMs - state.lastRerankAtMs >= RERANK_COOLDOWN_MS

        val plan = Plan(
            verdict = verdict,
            level = level,
            jitterMs = jitter,
            medianRttMs = median,
            p95RttMs = p95,
            spikeRatio = spikes,
            baselineJitterMs = baseline,
            samples = samples,
            probeBurst = burst,
            probeEveryTicks = probeEveryTicks,
            bufferScale = 1.0 + BUFFER_SPAN * level,
            requestRerank = requestRerank,
            reason = if (isIdleWakeup) {
                "${verdict.name.lowercase()} • radio-wakeup sample after idle gap • level ${(level * 100).toInt()}%"
            } else {
                "${verdict.name.lowercase()} • jitter ${jitter.toInt()}ms of " +
                    "baseline ${baseline.toInt()}ms • spike ${(spikes * 100).toInt()}% • " +
                    "level ${(level * 100).toInt()}%"
            },
            isRadioWakeup = isIdleWakeup
        )

        val next = state.copy(
            level = level,
            baselineJitterMs = baseline,
            baselineSamples = baselineSamples,
            lastSampleAtMs = nowMs,
            escalatedAtMs = if (escalated) nowMs else state.escalatedAtMs,
            severeStreak = severeStreak,
            calmStreak = calmStreak,
            verdict = verdict,
            lastRerankAtMs = if (requestRerank) nowMs else state.lastRerankAtMs,
            escalations = state.escalations + if (escalated) 1 else 0
        )
        return Decision(next, plan)
    }

    fun burstFor(level: Double): Int = when {
        level >= 0.80 -> MAX_BURST
        level >= 0.45 -> 2
        else -> MIN_BURST
    }

    private fun escalate(verdict: Verdict): Verdict = when (verdict) {
        Verdict.CALM -> Verdict.ELEVATED
        Verdict.ELEVATED -> Verdict.SEVERE
        Verdict.SEVERE, Verdict.EXTREME -> Verdict.EXTREME
    }

    fun budgetedTicks(wantedTicks: Int, burst: Int): Int {
        val ticks = wantedTicks.coerceAtLeast(MIN_TICKS)
        val safeBurst = burst.coerceIn(1, MAX_BURST)
        if (probesPerMinute(ticks, safeBurst) <= MAX_PROBES_PER_MINUTE) return ticks

        val minTicks = ceil(
            safeBurst.toDouble() * 60_000.0 / (MAX_PROBES_PER_MINUTE * TICK_MS.toDouble())
        ).toInt()
        return minTicks.coerceAtLeast(MIN_TICKS)
    }

    fun probesPerMinute(ticks: Int, burst: Int): Double =
        60_000.0 / (ticks.coerceAtLeast(1) * TICK_MS.toDouble()) * burst.coerceAtLeast(1)

    fun probesPerMinute(plan: Plan): Double = probesPerMinute(plan.probeEveryTicks, plan.probeBurst)

    fun describe(plan: Plan): String =
        "${plan.reason} • burst ${plan.probeBurst} every ${plan.probeEveryTicks}t • " +
            "buffer x${(plan.bufferScale * 100).toInt()}%"

    fun naiveJitterMs(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        val deltas = DoubleArray(samples.size - 1)
        for (index in deltas.indices) deltas[index] = abs(samples[index + 1] - samples[index])
        var sum = 0.0
        for (value in deltas) sum += value
        return sum / deltas.size
    }
}
