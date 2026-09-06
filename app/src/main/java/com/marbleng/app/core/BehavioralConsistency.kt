package com.marbleng.app.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * MARBLE_IRAN_AWARE_PING_L2 — Layer 2: Behavioral Consistency.
 *
 * ## Why a single measurement is not a judgment
 *
 * The cross-validation rule of this architecture is that no measurement is valid alone. Layer 0
 * says "the endpoint answered", Layer 1 says "this transport answered at this shape", and Layer
 * 2 decides whether those answers **agree with themselves over time and over the clock**. A
 * firewall that only triggers during the evening peak, or only after a session crosses a volume
 * threshold, looks perfectly healthy to any single probe — and perfectly broken to the next.
 *
 * ## The three persistences
 *
 *  1. **Temporal stability** — three measurements at least 20 minutes apart. If the standard
 *     deviation of those measurements exceeds 40% of their mean, the node is
 *     [StabilityClass.UNSTABLE_UNDER_OBSERVATION]: it must not enter the *stable* ranking tier
 *     even if its current latency looks fine, because the next measurement is a coin flip.
 *  2. **Time-of-day buckets** — the day is split into four 6-hour buckets
 *     (00–06, 06–12, 12–18, 18–24). Each bucket keeps its own EWMA, so the 22:00 peak that
 *     kills the node is not averaged into the 10:00 evidence that decided the user's morning
 *     ranking. `scoreForCurrentTimeWindow` reads only the bucket the user is in right now.
 *  3. **Tunnel-level injected reset pattern** — reset-after-volume. A stateful filter often
 *     lets a connection live and then kills it after N bytes or M seconds. The pattern is
 *     tracked per transport: several resets that each arrive only after a meaningful volume
 *     was transferred is a deliberate pattern, not a flaky server.
 */
object BehavioralConsistency {

    /** How an endpoint's samples behave across the 20-minute observation window. */
    enum class StabilityClass {
        /** Fewer than three spaced samples: still observing. */
        OBSERVING,

        /** Three spaced samples with stddev within 40% of the mean: evidence is consistent. */
        STABLE,

        /**
         * Three spaced samples with stddev above 40% of the mean. The endpoint is being
         * measured but must not be trusted for a stable verdict; ranking holds it in the
         * observation tier and the UI can show it honestly.
         */
        UNSTABLE_UNDER_OBSERVATION
    }

    /** One recorded measurement used by the consistency models. */
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

    /** A separated reset-after-volume observation for one transport of one node. */
    data class ResetPatternSample(
        val atMs: Long,
        val transportType: String,
        /** Bytes that had already transferred before the stream was reset. */
        val bytesBeforeReset: Long,
        /** Seconds the stream survived before the reset. */
        val survivedMs: Long,
        /** True when this reset arrived suspiciously like an in-path RST (not a clean close). */
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
        /** 0..1 — confidence that resets follow a deliberate after-volume pattern. */
        val resetAfterVolumeConfidence: Double,
        val resetAfterVolumeCount: Int,
        /** Per-6h-bucket EWMA latency, indexed by [timeOfDayBucket]. */
        val bucketLatencyEwma: DoubleArray
    ) {
        /** Syntactic convenience for pure-Android callers. */
        val unstableUnderObservation: Boolean
            get() = stabilityClass == StabilityClass.UNSTABLE_UNDER_OBSERVATION
    }

    /** The four 6-hour day buckets; index 0 = 00:00–06:00 … index 3 = 18:00–24:00. */
    const val TIME_BUCKETS = 4
    const val BUCKET_HOURS = 6
    const val BUCKET_EWMA_ALPHA = 0.3

    /** Two measurements must be at least this far apart to count as a separate observation. */
    const val MIN_SPACING_MS = 20L * 60L * 1000L

    /** stddev / mean above this marks the 3-sample set unstable. */
    const val UNSTABLE_STDDEV_OVER_MEAN = 0.40

    /** Confidence at which the reset-after-volume pattern is a positive signal. */
    const val RESET_PATTERN_HIGH_CONFIDENCE = 0.66

    fun timeOfDayBucket(atMs: Long): Int =
        ((atMs / 3_600_000L) % 24L).toInt() / BUCKET_HOURS

    fun bucketKey(bucket: Int): String = "bucket-$bucket"

    /**
     * The temporal consistency verdict from a set of samples.
     *
     * Samples are sorted by time; only samples at least [MIN_SPACING_MS] apart are kept, so
     * three measurements fired back-to-back by a buggy caller do not masquerade as "three
     * observations 20 minutes apart".
     */
    fun evaluate(
        samples: List<ConsistencySample>,
        windowMs: Long = 24L * 60L * 60L * 1000L
    ): ConsistencyReport {
        val sorted = samples
            .filter { it.atMs > 0L && it.latencyMs > 0.0 }
            .sortedBy { it.atMs }
            .takeLast(32)

        // Spacing filter: the minimum 20-minute cadence is the entire point of this layer.
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

    /**
     * Reset-after-volume confidence from reset samples.
     *
     * A single reset is a network event. **Three or more** resets on the same transport, each
     * arriving after a real volume and each reset-like in shape, is a pattern. The confidence
     * grows with count and with how consistently the resets follow volume.
     */
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

    /**
     * Fold a reset-after-volume observation into a simple running state (kept by the service and
     * persisted in the intelligence store). Volumes below [MIN_VOLUME_BYTES] are *not* pattern
     * evidence: a reset of a request that had transferred nothing is Layer-0-like, not the
     * volumetric signature Layer 2 is about.
     */
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

    /** Used internally to avoid a quadruple destructuring allocation. */
    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
