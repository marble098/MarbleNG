package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import kotlin.math.abs
import kotlin.math.min

object ProtocolFingerprintAwareVerifier {
    data class TransportVerdict(
        val transportType: String,
        val successPercent: Int,
        val latencyMs: Double,
        val jitterMs: Double,
        val throughputBps: Double,
        val sawtoothConfidence: Double,
        val injectedResetSuspected: Boolean,
        val silentTimeoutSuspected: Boolean,
        val baselineDeltaRatio: Double,
        val probeTargetUsed: String,
        val atMs: Long = System.currentTimeMillis()
    ) {
        val healthy: Boolean
            get() = successPercent > 0 && !injectedResetSuspected

        val throttled: Boolean
            get() = sawtoothConfidence >= SAWTOOTH_HIGH_CONFIDENCE
    }

    data class ThroughputSample(
        val elapsedMs: Long,
        val bytes: Long
    )

    data class ThrottlePattern(
        val confidence: Double,
        val sawtoothSuspected: Boolean,
        val peakRateBps: Double,
        val tailRateBps: Double,
        val stallAfterBytes: Long
    )

    const val SAWTOOTH_HIGH_CONFIDENCE = 0.66
    const val SAWTOOTH_ABSOLUTE_CONFIDENCE = 0.90

    fun transportTypeOf(profile: ProxyProfile): String {
        val scheme = profile.scheme.trim().lowercase().ifEmpty { "unknown" }
        val transport = profile.transport.trim().lowercase().ifEmpty {
            when {
                scheme == "hysteria2" || scheme == "hysteria" || scheme == "tuic" -> "quic"
                else -> "tcp"
            }
        }
        val security = profile.security.trim().lowercase().ifEmpty { "none" }
        return "$scheme/$transport/$security"
    }

    fun verify(
        xray: XrayManager,
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings,
        timeoutMs: Int,
        speedBytes: Int = DEFAULT_SPEED_BYTES,
        measureSpeed: Boolean = true,
        link: LinkEvidence = LinkEvidence.UNKNOWN,
        baseline: TransportVerdict? = null
    ): TransportVerdict {
        val transportType = transportTypeOf(profile)
        var success = 0
        var latency = 0.0
        var jitter = 0.0
        var throughput = 0.0
        var sawtooth = ThrottlePattern(0.0, false, 0.0, 0.0, 0L)
        var injected = false
        var silent = false
        var targetUsed = "none"
        val baseSettings = settings.copy(benchSamples = 1)
        runCatching {
            xray.temporary(profile, port, baseSettings, link = link) { livePort ->
                val pool = ProbeTargetPool.ordered(ProbeTargetPool.CDN_TARGETS, profile.id)

                val batch = pool.firstNotNullOfOrNull { target ->
                    targetUsed = ProbeTargetPool.labelOf(target)
                    runCatching {
                        SocksHttpClient.tunnelRttBatch(
                            port = livePort,
                            host = target.host,
                            path = target.path,
                            samples = 2,
                            timeoutMs = timeoutMs.coerceIn(1_000, 12_000)
                        )
                    }.getOrNull()
                }
                if (batch != null) {
                    val samples = batch.samplesMs.filter { it.isFinite() && it > 0.0 }
                    if (samples.isNotEmpty()) {
                        success = 100
                        val sorted = samples.sorted()
                        latency = sorted[sorted.size / 2]
                        jitter = if (samples.size >= 2) samples.zipWithNext { a, b -> abs(a - b) }.average() else 0.0
                    }
                }

                if (measureSpeed && success > 0 && speedBytes > 0) {
                    val transfer = runCatching {
                        SocksHttpClient.sampledGet(
                            port = livePort,
                            host = THROUGHPUT_TARGET,
                            path = "/__down?bytes=$speedBytes",
                            timeoutMs = timeoutMs + 8_000,
                            maxBytes = speedBytes + 16_384
                        )
                    }.getOrNull()
                    if (transfer != null && transfer.status in 200..299) {
                        throughput = transfer.bytesPerSecond
                        sawtooth = SawtoothDetector.evaluate(transfer.samples)
                        if (transfer.injectedResetSuspected) injected = true
                        if (transfer.silentTimeoutSuspected) silent = true
                    } else if (transfer != null) {
                        if (transfer.injectedResetSuspected) injected = true
                        if (transfer.silentTimeoutSuspected) silent = true
                    }
                }
            }
        }

        return TransportVerdict(
            transportType = transportType,
            successPercent = success,
            latencyMs = if (success > 0) latency else 0.0,
            jitterMs = jitter,
            throughputBps = throughput,
            sawtoothConfidence = sawtooth.confidence,
            injectedResetSuspected = injected,
            silentTimeoutSuspected = silent,
            baselineDeltaRatio = baselineDeltaRatio(
                current = latency,
                baseline = baseline?.latencyMs?.takeIf { it > 0.0 }
            ),
            probeTargetUsed = targetUsed
        )
    }

    fun baselineDeltaRatio(current: Double, baseline: Double?): Double {
        if (baseline == null || baseline <= 0.0 || current <= 0.0) return 0.0
        return current / baseline
    }

    const val DEFAULT_SPEED_BYTES = 256 * 1024

    const val THROUGHPUT_TARGET = "speed.cloudflare.com"
}

object SawtoothDetector {
    const val BUCKET_MS = 250L

    fun evaluate(samples: List<ProtocolFingerprintAwareVerifier.ThroughputSample>): ProtocolFingerprintAwareVerifier.ThrottlePattern {
        if (samples.size < 3) {
            return ProtocolFingerprintAwareVerifier.ThrottlePattern(0.0, false, 0.0, 0.0, 0L)
        }
        val sorted = samples.sortedBy { it.elapsedMs }
        val totalBytes = sorted.last().bytes
        val totalMs = sorted.last().elapsedMs.coerceAtLeast(1L)
        if (totalBytes <= 0L || totalMs < BUCKET_MS * 2L) {
            return ProtocolFingerprintAwareVerifier.ThrottlePattern(0.0, false, 0.0, 0.0, 0L)
        }

        val buckets = LinkedHashMap<Long, Long>()
        sorted.forEach { sample ->
            val bucket = sample.elapsedMs / BUCKET_MS
            buckets[bucket] = sample.bytes
        }

        val bucketValues = buckets.entries.toList()
        val rates = ArrayList<Double>(bucketValues.size)
        var previousBytes = 0L
        for ((_, cumulative) in bucketValues) {
            val delta = (cumulative - previousBytes).coerceAtLeast(0L)
            previousBytes = cumulative
            rates += delta * 1000.0 / BUCKET_MS
        }
        val seg = (rates.size / 3).coerceAtLeast(1)
        val peak = rates
            .take(seg.coerceAtMost(rates.size))
            .maxOrNull() ?: 0.0
        val tail = rates
            .takeLast(seg.coerceAtMost(rates.size))
            .average().takeIf { it.isFinite() } ?: 0.0

        val stalling = peak > 0.0 && tail <= peak * 0.15 && peak / tail >= 4.0
        val earlyStall = tail <= 0.0 &&
            buckets.values.last() < totalBytes * 0.85
        val high = ProtocolFingerprintAwareVerifier.SAWTOOTH_HIGH_CONFIDENCE
        val confidence = when {
            stalling -> high + confidenceFromCorrelation(rates) * 0.3
            earlyStall -> high * 0.85
            else -> 0.0
        }
        val stallBytes = buckets.entries.lastOrNull { (_, bytes) -> bytes < totalBytes }
            ?.value ?: 0L
        return ProtocolFingerprintAwareVerifier.ThrottlePattern(
            confidence = confidence.coerceIn(0.0, 1.0),
            sawtoothSuspected = confidence >= high,
            peakRateBps = peak,
            tailRateBps = tail,
            stallAfterBytes = stallBytes
        )
    }

    private fun confidenceFromCorrelation(rates: List<Double>): Double {
        if (rates.size < 3) return 0.0
        val n = rates.size
        val meanX = (n - 1) / 2.0
        val meanY = rates.average()
        var cov = 0.0
        var varX = 0.0
        var varY = 0.0
        rates.forEachIndexed { index, rate ->
            val dx = index - meanX
            val dy = rate - meanY
            cov += dx * dy
            varX += dx * dx
            varY += dy * dy
        }
        if (varX <= 0.0 || varY <= 0.0) return 0.0
        val r = cov / (kotlin.math.sqrt(varX * varY))
        return (min(1.0, -r)).coerceAtLeast(0.0)
    }
}
