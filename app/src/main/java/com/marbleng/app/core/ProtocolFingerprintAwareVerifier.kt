package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import kotlin.math.abs
import kotlin.math.min

/**
 * MARBLE_IRAN_AWARE_PING_L1 — Layer 1: Protocol-Fingerprint-Aware tunnel verification.
 *
 * ## What this replaces
 *
 * Every real-tunnel measurement collapsed the result into one number. When a server offers
 * both VLESS+Reality and Hysteria2, the old engine measured whichever transport the config
 * happened to carry and treated that single verdict as the health of the *server*. In a
 * firewall environment that is exactly backwards: filtering is frequently **protocol
 * targeted** — Hysteria2 on UDP stays free while VLESS+Reality on TCP is throttled, or vice
 * versa. A one-transport verdict cannot distinguish "server is bad" from "this transport is
 * the current target".
 *
 * ## The rules
 *
 *  1. **Per-transport verdicts, never averages.** Each transport type measured for a server is
 *     stored separately with its own label, so a throttled transport never drags a healthy one
 *     down and a healthy transport is still available as the fallback.
 *  2. **Two-stage throughput**: after the RTT probe, a bounded 256 KiB download is sampled in
 *     time buckets. The shape of the transfer, not its average, is the signal: a rate that
 *     starts high and collapses toward zero (sawtooth) is the fingerprint of gradual DPI
 *     throttling; a flat slow rate is ordinary congestion.
 *  3. **Baseline delta**: the verdict is compared with the previous verdict on the same
 *     transport (kept by [MarbleIntelligence]); a sudden simultaneous shift across many
 *     servers is a network-wide event, not server blame (Layer 3).
 */
object ProtocolFingerprintAwareVerifier {

    /** One transport on one server, measured independently. */
    data class TransportVerdict(
        /** e.g. `vless/reality/tcp`, `hysteria2/quic/udp`. */
        val transportType: String,
        val successPercent: Int,
        val latencyMs: Double,
        val jitterMs: Double,
        val throughputBps: Double,
        /** 0..1 — confidence that the transfer shape is deliberate throttling. */
        val sawtoothConfidence: Double,
        val injectedResetSuspected: Boolean,
        val silentTimeoutSuspected: Boolean,
        /** current latency / baseline latency; 1.0 = identical to the 15-minute baseline. */
        val baselineDeltaRatio: Double,
        val probeTargetUsed: String,
        val atMs: Long = System.currentTimeMillis()
    ) {
        val healthy: Boolean
            get() = successPercent > 0 && !injectedResetSuspected

        val throttled: Boolean
            get() = sawtoothConfidence >= SAWTOOTH_HIGH_CONFIDENCE
    }

    /** One point of a sampled download: cumulative bytes at a wall-clock offset. */
    data class ThroughputSample(
        val elapsedMs: Long,
        val bytes: Long
    )

    /** The shape verdict of a sampled transfer. */
    data class ThrottlePattern(
        val confidence: Double,
        val sawtoothSuspected: Boolean,
        val peakRateBps: Double,
        val tailRateBps: Double,
        val stallAfterBytes: Long
    )

    const val SAWTOOTH_HIGH_CONFIDENCE = 0.66
    const val SAWTOOTH_ABSOLUTE_CONFIDENCE = 0.90

    /** A transport label is the fingerprint identity the whole product keys on. */
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

    /**
     * Verdict for one transport, measured through a throwaway Xray core of that exact config.
     *
     * The caller supplies the core port (the same reservation discipline as
     * [BenchmarkEngine.measure]); this function owns the measurement semantics so every caller
     * (Rank, Tuner, Autopilot) measures the same way.
     */
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
                // First target that answers wins the RTT opinion. Rotated per server, so one
                // SNI-throttled member of the pool never becomes a permanent false negative.
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

    /**
     * Latency ratio against the last measurement (15-minute baseline). 0 when no baseline
     * exists; 1.0 when identical.
     */
    fun baselineDeltaRatio(current: Double, baseline: Double?): Double {
        if (baseline == null || baseline <= 0.0 || current <= 0.0) return 0.0
        return current / baseline
    }

    /** Constant bulk-transfer size for the two-stage throughput probe. */
    const val DEFAULT_SPEED_BYTES = 256 * 1024

    /** Host used for the throughput segment (a dedicated pool member serving synthetic payload). */
    const val THROUGHPUT_TARGET = "speed.cloudflare.com"
}

/**
 * MARBLE_IRAN_AWARE_PING_L1_SAWTOOTH — the shape detector.
 *
 * EWMA hides bursts; the whole point of Layer 2 is that bursts are the signal. This detector
 * splits a transfer into 250 ms buckets and reads the *shape*:
 *
 *  - **sawtooth**: the first third of the transfer runs far faster than the last third AND the
 *    tail is nearly silent. Gradual in-path throttling ramps in after the connection proves
 *    itself, so the transfer starts fast and then stops.
 *  - **flat**: peak ≈ tail. Ordinary congestion/geography.
 *  - **early-stall**: the transfer stopped almost immediately with a large portion of the
 *    payload undelivered — a mid-stream RST signature.
 */
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
        // Per-bucket INCREMENTAL rate (bytes delivered in that bucket / bucket width). The
        // cumulative bytes over total time would rise monotonically and fake a "collapse";
        // the incremental rate is what actually starts high and stalls.
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
        val confidence = when {
            stalling -> SAWTOOTH_HIGH_CONFIDENCE + confidenceFromCorrelation(rates) * 0.3
            earlyStall -> SAWTOOTH_HIGH_CONFIDENCE * 0.85
            else -> 0.0
        }
        val stallBytes = buckets.entries.lastOrNull { (_, bytes) -> bytes < totalBytes }
            ?.value ?: 0L
        return ProtocolFingerprintAwareVerifier.ThrottlePattern(
            confidence = confidence.coerceIn(0.0, 1.0),
            sawtoothSuspected = confidence >= SAWTOOTH_HIGH_CONFIDENCE,
            peakRateBps = peak,
            tailRateBps = tail,
            stallAfterBytes = stallBytes
        )
    }

    /**
     * Negative Pearson correlation between bucket index and rate: the more strictly
     * monotone the decline, the more deliberately shaped the throttling.
     */
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
