package com.marbleng.app.core

import kotlin.math.abs
import kotlin.math.sqrt

object BehavioralConsistency {
    enum class StabilityClass {
        OBSERVING,

        STABLE,

        UNSTABLE_UNDER_OBSERVATION
    }

    data class ConsistencySample(
        val atMs: Long,
        val latencyMs: Double,
        val jitterMs: Double = 0.0,
        val successPercent: Double = 100.0,
        val throughputBps: Double = 0.0,
        val transportType: String = "",
        val injectedResetSuspected: Boolean = false,
        val injectedResetRatePercent: Double = 0.0,
        val sawtoothConfidence: Double = 0.0
    )

    data class ResetPatternSample(
        val atMs: Long,
        val transportType: String,
        val bytesBeforeReset: Long,
        val survivedMs: Long,
        val resetLike: Boolean
    )

    data class ConsistencyReport(
        val samples: List<ConsistencySample>,
        val stabilityClass: StabilityClass,
        val stabilityScore: Double,
        val meanLatencyMs: Double,
        val stddevMs: Double,
        val stddevOverMean: Double,
        val observing: Boolean,
        val resetAfterVolumeConfidence: Double,
        val resetAfterVolumeCount: Int,
        val bucketLatencyEwma: DoubleArray
    ) {
        val unstableUnderObservation: Boolean
            get() = stabilityClass == StabilityClass.UNSTABLE_UNDER_OBSERVATION
    }

    const val TIME_BUCKETS = 4
    const val BUCKET_HOURS = 6
    const val BUCKET_EWMA_ALPHA = 0.3

    const val MIN_SPACING_MS = 20L * 60L * 1000L

    const val UNSTABLE_STDDEV_OVER_MEAN = 0.40

    const val RESET_PATTERN_HIGH_CONFIDENCE = 0.66

    fun timeOfDayBucket(atMs: Long): Int =
        ((atMs / 3_600_000L) % 24L).toInt() / BUCKET_HOURS

    fun bucketKey(bucket: Int): String = "bucket-$bucket"

    fun evaluate(
        samples: List<ConsistencySample>,
        windowMs: Long = 24L * 60L * 60L * 1000L
    ): ConsistencyReport {
        val sorted = samples
            .filter { it.atMs > 0L && it.latencyMs > 0.0 }
            .sortedBy { it.atMs }
            .takeLast(32)

        val spaced = ArrayList<ConsistencySample>(4)
        for (sample in sorted) {
            if (spaced.isEmpty() || sample.atMs - spaced.last().atMs >= MIN_SPACING_MS) {
                spaced += sample
            }
        }

        val buckets = DoubleArray(TIME_BUCKETS) { Double.NaN }
        val noiseBucket = sorted
            .filter { it.atMs >= System.currentTimeMillis() - windowMs }
        for (sample in noiseBucket) {
            val b = timeOfDayBucket(sample.atMs)
            val current = buckets[b]
            buckets[b] = if (current.isNaN()) {
                sample.latencyMs
            } else {
                current * (1.0 - BUCKET_EWMA_ALPHA) + sample.latencyMs * BUCKET_EWMA_ALPHA
            }
        }

        val recent = spaced.takeLast(3)
        val recentMean = if (recent.isEmpty()) 0.0 else recent.map { it.latencyMs }.average()
        val (clazz, stddev, mean, ratio) = when {
            recent.size < 3 -> Tuple4(
                StabilityClass.OBSERVING,
                0.0,
                recentMean,
                0.0
            )
            else -> {
                val mean3 = recent.map { it.latencyMs }.average()
                val variance = recent.map { (it.latencyMs - mean3) * (it.latencyMs - mean3) }
                    .average()
                val sd = sqrt(variance)
                val r = if (mean3 > 0.0) sd / mean3 else 0.0
                if (r > UNSTABLE_STDDEV_OVER_MEAN) {
                    Tuple4(StabilityClass.UNSTABLE_UNDER_OBSERVATION, sd, mean3, r)
                } else {
                    Tuple4(StabilityClass.STABLE, sd, mean3, r)
                }
            }
        }

        return ConsistencyReport(
            samples = sorted,
            stabilityClass = clazz,
            stabilityScore = when (clazz) {
                StabilityClass.STABLE -> 1.0
                StabilityClass.UNSTABLE_UNDER_OBSERVATION -> (1.0 - ratio).coerceIn(0.0, 0.99)
                StabilityClass.OBSERVING -> 0.5
            },
            meanLatencyMs = mean,
            stddevMs = stddev,
            stddevOverMean = ratio,
            observing = clazz == StabilityClass.OBSERVING,
            resetAfterVolumeConfidence = resetPatternConfidence(samples),
            resetAfterVolumeCount = samples.count { it.injectedResetSuspected },
            bucketLatencyEwma = buckets
        )
    }

    fun resetPatternConfidence(samples: List<ConsistencySample>): Double {
        val resetLike = samples.count { it.injectedResetSuspected }
        if (resetLike < 3) return 0.0
        val rate = samples.map { it.injectedResetRatePercent }.filter { it > 0.0 }
        val meanRate = if (rate.isEmpty()) 0.0 else rate.average()
        if (meanRate <= 0.0) return 0.0
        val countScore = (resetLike / 8.0).coerceIn(0.0, 1.0)
        val rateScore = (meanRate / 100.0).coerceIn(0.0, 1.0)
        val sustained = (0.5 * countScore + 0.5 * rateScore).coerceIn(0.0, 1.0)
        return when {
            sustained >= 0.9 -> 0.95
            sustained >= 0.75 -> 0.85
            sustained >= 0.6 -> RESET_PATTERN_HIGH_CONFIDENCE
            else -> sustained
        }
    }

    data class ResetPatternState(
        val count: Int = 0,
        val lastAtMs: Long = 0L,
        val meanBytesBeforeReset: Long = 0L,
        val meanSurvivedMs: Long = 0L,
        val confidence: Double = 0.0
    )

    fun resetAfterVolumePattern(
        sample: ResetPatternSample,
        state: ResetPatternState,
        minVolumeBytes: Long = 8L * 1024L
    ): ResetPatternState {
        val valid = sample.resetLike && sample.bytesBeforeReset >= minVolumeBytes
        if (!valid) return state
        val count = state.count + 1
        val meanBytes = (state.meanBytesBeforeReset * (count - 1) + sample.bytesBeforeReset) / count
        val meanSurvived = (state.meanSurvivedMs * (count - 1) + sample.survivedMs) / count
        val confidence = when {
            count >= 6 -> 0.95
            count >= 4 -> 0.85
            count >= 3 -> (0.66 + (count - 3) * 0.1).coerceAtMost(0.85)
            else -> 0.0
        }
        return ResetPatternState(
            count = count,
            lastAtMs = sample.atMs,
            meanBytesBeforeReset = meanBytes,
            meanSurvivedMs = meanSurvived,
            confidence = confidence
        )
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
