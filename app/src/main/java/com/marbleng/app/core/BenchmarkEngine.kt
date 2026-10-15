package com.marbleng.app.core

import com.marbleng.app.model.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BenchmarkEngine(
    private val xray: XrayManager,
    private val intelligence: MarbleIntelligence? = null
) {
    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        usePrecheck: Boolean = true,
        v2rayStyleDelay: Boolean = false,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        shouldStop: () -> Boolean = { false },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        if (profiles.isEmpty()) return emptyList()
        val s = tuned(settings)
        val batchNetworkKey = intelligence?.currentSnapshot()?.key()

        val precheck = false
        val candidates = selectCandidates(profiles, s, precheck).distinctBy { it.id }
        if (candidates.isEmpty()) return emptyList()
        onCandidates(candidates)

        val thermal = intelligence?.thermalBudget(s) ?: 1.0
        val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

        val nominalWorkers = sweepWidth(
            method = s.probeMethod,
            engine = s.coreEngine(),
            tcpWorkers = s.tcpWorkers,
            cpus = cpu,
            singBoxCeiling = xray.singBox?.measurementCoreCeiling ?: SingBoxManager.MAX_TEMPORARY_CORES,
            xrayChildCeiling = xray.measurementCoreCeiling,
            speedFactor = settings.pingSpeedFactor(),
            v2rayStyleDelay = v2rayStyleDelay
        )
        val liveWorkers = max(1, (nominalWorkers * thermal).toInt())
            .coerceAtMost(candidates.size)

        val livePool = Executors.newFixedThreadPool(liveWorkers)

        val completion = ExecutorCompletionService<Unit>(livePool)
        val completed = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<BenchmarkResult>())
        val jobs = candidates.mapIndexed { idx, p ->
            completion.submit {
                if (shouldStop()) return@submit
                onStart(p)

                val measured = testCandidate(p, benchmarkPort(idx), s, v2rayStyleDelay)
                val result = rank(listOf(measured), s).firstOrNull() ?: measured
                results += result

                if (
                    !directProbe(s) &&
                    (batchNetworkKey == null || intelligence?.currentSnapshot()?.key() == batchNetworkKey)
                ) {
                    intelligence?.recordBenchmark(p, result, s)
                }
                onResult(p, result)
                onProgress(completed.incrementAndGet(), candidates.size, p.name)
            }
        }

        val sampleSpacingMs = settings.pingSampleSpacingMs()
        val perTaskCapMs = PingBudget.perServerBudgetMs(s.benchTimeoutSec, s.benchSamples, sampleSpacingMs) +
            if (s.probeMethod == ProbeMethod.TCP_PING) BATCH_DIRECT_GRACE_MS else maxOf(BATCH_TUNNEL_GRACE_MS, 15_000L)
        val waves = ((candidates.size + liveWorkers - 1) / liveWorkers).coerceAtLeast(1)
        val batchCapMs = (perTaskCapMs * waves + BATCH_WAVE_GRACE_MS)
            .coerceAtMost(BATCH_ABSOLUTE_CAP_MS)
        val batchDeadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(batchCapMs)
        try {
            var remaining = jobs.size
            while (remaining > 0) {
                if (shouldStop()) {
                    jobs.forEach { if (!it.isDone) it.cancel(true) }
                    break
                }
                val leftMs = TimeUnit.NANOSECONDS.toMillis(batchDeadlineNs - System.nanoTime())
                if (leftMs <= 0L) {
                    jobs.forEach { if (!it.isDone) it.cancel(true) }
                    break
                }

                val finished = completion.poll(minOf(leftMs, CANCEL_POLL_SLICE_MS), TimeUnit.MILLISECONDS)
                if (finished != null) {
                    remaining--
                    runCatching { finished.get() }
                }
            }
        } finally {
            jobs.forEach { if (!it.isDone) it.cancel(true) }
            livePool.shutdownNow()
        }

        val ranked = rank(results.toList(), s)
        if (!directProbe(s)) {
            ranked.firstOrNull()?.let { top ->
                intelligence?.setDecision(
                    "${top.name} • ${top.success}% • ${top.latencyMs.toInt()} ms • " +
                        "${formatRate(top.bytesPerSecond)} • ${s.workloadProfile.name.lowercase()}"
                )
            }
        }
        return ranked
    }

    fun race(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        onProgress: (String) -> Unit = {}
    ): Pair<ProxyProfile, BenchmarkResult>? {
        if (profiles.isEmpty()) return null
        val width = settings.raceWidth.coerceIn(2, 4).coerceAtMost(profiles.size)
        val raceSettings = tuned(settings).copy(
            benchCandidates = width,
            benchSamples = 1,
            benchTimeoutSec = min(settings.benchTimeoutSec, 5),
            benchBytes = min(settings.benchBytes, 64 * 1024)
        )
        val candidates = selectCandidates(profiles, raceSettings, true).distinctBy { it.id }.take(width)
        if (candidates.isEmpty()) return null
        onCandidates(candidates)
        val pool = Executors.newFixedThreadPool(candidates.size)
        val completion = ExecutorCompletionService<Pair<ProxyProfile, BenchmarkResult>>(pool)
        val futures = candidates.mapIndexed { idx, profile ->
            completion.submit {
                onStart(profile)
                onProgress(profile.name)
                val result = quickCandidate(profile, RACE_BASE_PORT + idx * 3, raceSettings)
                onResult(profile, result)
                profile to result
            }
        }
        val successes = mutableListOf<Pair<ProxyProfile, BenchmarkResult>>()
        var remaining = candidates.size
        val globalDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos((raceSettings.benchTimeoutSec + 5).toLong())
        var successDeadline = Long.MAX_VALUE
        try {
            while (remaining > 0) {
                val deadline = min(globalDeadline, successDeadline)
                val left = deadline - System.nanoTime()
                if (left <= 0L) break
                val future = completion.poll(left, TimeUnit.NANOSECONDS) ?: break
                remaining--
                val result = runCatching { future.get() }.getOrNull() ?: continue
                intelligence?.recordBenchmark(result.first, result.second, raceSettings)
                if (result.second.success > 0) {
                    successes += result
                    if (successDeadline == Long.MAX_VALUE) {
                        successDeadline = min(globalDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(220L))
                    }
                }
            }
        } finally {
            futures.forEach { if (!it.isDone) it.cancel(true) }
            pool.shutdownNow()
        }
        val winner = successes.minByOrNull { (profile, result) ->
            result.latencyMs - (intelligence?.predictedScore(profile, raceSettings) ?: 50.0) * 0.45
        }
        winner?.let { (profile, result) ->
            intelligence?.setDecision("Verified race winner ${profile.name} • ${result.latencyMs.toInt()} ms")
        }
        return winner
    }

    fun continuousProbe(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        deep: Boolean,
        onProgress: (String) -> Unit = {}
    ): List<BenchmarkResult> {
        val effectiveSettings = if (settings.probeMethod != ProbeMethod.REAL_DELAY) {
            settings.copy(probeMethod = ProbeMethod.REAL_DELAY)
        } else {
            settings
        }
        val unique = profiles.distinctBy { it.id }.take(9)
        if (unique.isEmpty()) return emptyList()
        val probeSettings = tuned(effectiveSettings).copy(
            benchMode = BenchMode.CUSTOM,
            benchCandidates = unique.size,
            benchSamples = if (deep) 3 else 2,
            benchTimeoutSec = min(effectiveSettings.benchTimeoutSec, 6),
            benchBytes = if (deep) 128 * 1024 else 64 * 1024,
            adaptiveThroughputEnabled = false,
            adaptiveThroughputMaxBytes = 128 * 1024,
            udpProbeEnabled = false
        )
        val pool = Executors.newFixedThreadPool(min(3, unique.size))
        val results = Collections.synchronizedList(mutableListOf<BenchmarkResult>())
        val jobs = unique.mapIndexed { index, profile ->
            pool.submit {
                onProgress(profile.name)
                val result = backgroundCandidate(
                    profile,
                    OPTIMIZER_BASE_PORT + index * 4,
                    probeSettings,
                    deep
                )
                results += result
                intelligence?.recordBenchmark(profile, result, probeSettings)
            }
        }
        jobs.forEach { runCatching { it.get() } }
        pool.shutdownNow()
        val byId = unique.associateBy { it.id }
        return results.map { result ->
            val history = byId[result.profileId]?.let {
                intelligence?.predictedScore(it, probeSettings)
            } ?: 50.0
            result.copy(score = continuousScore(result, history, deep))
        }.sortedWith(
            compareByDescending<BenchmarkResult> { it.score }
                .thenBy { it.latencyMs }
        )
    }

    private fun backgroundCandidate(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings,
        deep: Boolean
    ): BenchmarkResult {
        val effective = intelligence?.effectiveSettings(profile, settings) ?: settings
        val measurement = measure(profile, port, effective, includeThroughput = deep)
        return benchmarkResult(
            profile,
            measurement,
            usedMux = effective.muxEnabled && !settings.muxEnabled
        )
    }

    private fun continuousScore(
        result: BenchmarkResult,
        historicalScore: Double,
        deep: Boolean
    ): Double {
        if (result.success <= 0) return -1.0
        val live = RealtimeQualityEngine.score(result, WorkloadProfile.AUTO, if (deep) BenchMode.BALANCED else BenchMode.FAST).selected
        return (live * 0.92 + historicalScore.coerceIn(0.0, 100.0) * 0.08).coerceIn(0.0, 100.0)
    }

    private fun tuned(settings: AppSettings): AppSettings {
        val thermal = intelligence?.thermalBudget(settings) ?: 1.0
        val base = when (settings.benchMode) {
            BenchMode.RELIABLE -> settings.copy(
                benchCandidates = maxOf(settings.benchCandidates, 30),
                benchSamples = maxOf(settings.benchSamples, 5),
                benchTimeoutSec = maxOf(settings.benchTimeoutSec, 10),
                tcpWorkers = 16
            )
            BenchMode.BALANCED -> settings
            BenchMode.FAST -> settings.copy(
                benchCandidates = minOf(settings.benchCandidates, 15),
                benchSamples = minOf(settings.benchSamples, 3),
                benchTimeoutSec = minOf(settings.benchTimeoutSec, 6),
                tcpWorkers = 24
            )
            BenchMode.TURBO -> settings.copy(
                benchCandidates = minOf(settings.benchCandidates, 10),
                benchSamples = minOf(settings.benchSamples, 2),
                benchTimeoutSec = minOf(settings.benchTimeoutSec, 4),
                tcpWorkers = 28
            )
            BenchMode.CUSTOM -> settings
        }
        if (thermal >= 0.80) return base
        return base.copy(
            benchCandidates = max(3, (base.benchCandidates * thermal).toInt()),
            benchSamples = max(1, (base.benchSamples * max(0.55, thermal)).toInt()),
            benchBytes = max(64 * 1024, (base.benchBytes * max(0.35, thermal)).toInt()),
            tcpWorkers = max(4, (base.tcpWorkers * thermal).toInt())
        )
    }

    private fun selectCandidates(
        profiles: List<ProxyProfile>,
        s: AppSettings,
        usePrecheck: Boolean
    ): List<ProxyProfile> {
        val ordered =
            intelligence
                ?.orderCandidates(
                    profiles,
                    s
                ) ?: profiles

        val health: Map<String, NodeHealthRecord> =
            intelligence
                ?.healthSnapshot()
                ?: emptyMap()

        val known = health.keys

        val predicted =
            intelligence
                ?.rankingScores(profiles, s, health)
                ?: emptyMap()

        val maxCandidates =
            s.benchCandidates
                .coerceIn(
                    1,
                    profiles.size
                )

        if (!usePrecheck) {
            return ordered
        }

        val workers =
            s.tcpWorkers
                .coerceIn(1, 32)

        val precheckBudget =
            min(
                profiles.size,
                max(
                    maxCandidates * 4,
                    max(
                        24,
                        workers * 2
                    )
                )
            )

        val exploreBudget =
            max(
                4,
                precheckBudget / 4
            )

        val knownPool =
            ordered
                .filter {
                    it.id in known
                }
                .take(
                    (
                        precheckBudget -
                            exploreBudget
                    ).coerceAtLeast(0)
                )

        val rotation =
            (
                (
                    System.currentTimeMillis() /
                        (
                            6L *
                            60L *
                            60L *
                            1000L
                        )
                ).toInt() xor
                    (
                        intelligence
                            ?.currentSnapshot()
                            ?.key()
                            ?.hashCode()
                            ?: 0
                    )
            )

        val explorePool =
            profiles
                .filter {
                    it.id !in known
                }
                .sortedBy {
                    it.id.hashCode() xor
                        rotation
                }
                .take(
                    precheckBudget -
                        knownPool.size
                )

        val poolCandidates =
            (
                knownPool +
                    explorePool +
                    ordered
            )
                .distinctBy {
                    it.id
                }
                .take(
                    precheckBudget
                )

        val pool =
            Executors.newFixedThreadPool(
                workers
            )

        val completion =
            ExecutorCompletionService<
                Pair<
                    ProxyProfile,
                    Double
                >
            >(pool)

        val futures =
            poolCandidates.map {
                profile ->
                completion.submit {
                    profile to
                        if (
                            isUdpNative(
                                profile
                            )
                        ) {
                            0.0
                        } else {
                            tcpLatency(
                                profile,
                                s.tcpPrecheckTimeoutMs,
                                s
                            )
                        }
                }
            }

        val alive =
            mutableListOf<
                Pair<
                    ProxyProfile,
                    Double
                >
            >()

        var remaining =
            futures.size

        val waves =
            (
                poolCandidates.size +
                    workers -
                    1
            ) / workers

        val deadlineMs =
            (
                s.tcpPrecheckTimeoutMs
                    .toLong() *
                    waves.coerceAtMost(4) +
                    750L
            ).coerceAtMost(
                8_500L
            )

        val deadline =
            System.nanoTime() +
                TimeUnit.MILLISECONDS
                    .toNanos(
                        deadlineMs
                    )

        try {
            while (
                remaining >
                0
            ) {
                val left =
                    deadline -
                        System.nanoTime()

                if (left <= 0L) {
                    break
                }

                val future =
                    completion.poll(
                        left,
                        TimeUnit.NANOSECONDS
                    ) ?: break

                remaining--

                val pair =
                    runCatching {
                        future.get()
                    }.getOrNull()
                        ?: continue

                if (
                    isUdpNative(
                        pair.first
                    ) ||
                    pair.second <
                    DEAD_LATENCY
                ) {
                    alive += pair
                }

                if (
                    alive.size >=
                    maxCandidates * 2 &&
                    alive.size >=
                    maxCandidates + 4
                ) {
                    break
                }
            }
        } finally {
            futures.forEach {
                if (!it.isDone) {
                    it.cancel(true)
                }
            }
            pool.shutdownNow()
        }

        if (alive.isEmpty()) {
            return emptyList()
        }

        val latencyById =
            alive.associate {
                it.first.id to
                    it.second
            }

        val historicallyOrdered =
            alive
                .map {
                    it.first
                }
                .sortedWith(
                    compareByDescending<
                        ProxyProfile
                    > {
                        predicted[it.id]
                            ?: 50.0
                    }.thenBy {
                        latencyById[
                            it.id
                        ] ?: DEAD_LATENCY
                    }
                )

        val exploreCount =
            if (
                maxCandidates >=
                8
            ) {
                max(
                    1,
                    maxCandidates /
                        5
                )
            } else {
                1
            }

        val knownCandidates =
            historicallyOrdered
                .filter {
                    it.id in known
                }
                .take(
                    (
                        maxCandidates -
                            exploreCount
                    ).coerceAtLeast(0)
                )

        val unknownCandidates =
            historicallyOrdered
                .filter {
                    it.id !in known
                }
                .take(
                    exploreCount
                )

        return (
            knownCandidates +
                unknownCandidates +
                historicallyOrdered
        )
            .distinctBy {
                it.id
            }
            .take(
                maxCandidates
            )
    }

    private data class Measurement(
        val success: Int,
        val latency: Double,
        val speed: Double,
        val udpSuccess: Int,
        val jitter: Double = 0.0,
        val warmup: Double = 0.0,
        val sampleCount: Int = 0,
        val failureReason: String = "",
        val p90Latency: Double = 0.0,
        val p95Latency: Double = 0.0,
        val medianJitter: Double = 0.0,
        val p95Jitter: Double = 0.0,
        val madLatency: Double = 0.0,
        val lossPercent: Double = 0.0,
        val spikePercent: Double = 0.0,
        val loadedLatency: Double = 0.0
    )

    private fun directProbe(s: AppSettings): Boolean =
        s.probeMethod != ProbeMethod.REAL_DELAY

    private fun directResult(p: ProxyProfile, s: AppSettings): BenchmarkResult {
        val directTimeoutMs = (s.benchTimeoutSec * 1000).coerceIn(
            PingBudget.TIMEOUT_MIN_SEC * 1_000,
            PingBudget.TIMEOUT_MAX_SEC * 1_000
        )
        val samples = PingBudget.samples(s.benchSamples)

        fun asBenchmark(kind: String, result: RouteProbe.ProbeResult): BenchmarkResult =
            BenchmarkResult(
                profileId = p.id,
                name = p.name,
                success = result.successPercent,
                latencyMs = result.latencyMs,
                bytesPerSecond = 0.0,
                score = 0.0,
                probeKind = kind,
                jitterMs = result.jitterMs,
                p95LatencyMs = result.p95Ms,
                lossPercent = result.lossPercent,
                failureReason = result.failureReason.take(180)
            )

        return when (s.probeMethod) {
            ProbeMethod.TCP_PING -> asBenchmark(
                RouteProbe.METHOD_TCP_PING,
                RouteProbe.tcpPing(
                    profile = p,
                    timeoutMs = directTimeoutMs,
                    samples = samples,
                    settings = s
                )
            )
            ProbeMethod.URL_TEST -> asBenchmark(
                RouteProbe.METHOD_URL_TEST,
                RouteProbe.urlTest(
                    profile = p,
                    timeoutMs = directTimeoutMs,
                    settings = s
                )
            )
            ProbeMethod.REAL_DELAY ->
                error("REAL_DELAY is a native path and must never reach directResult")
        }
    }

    private fun testCandidate(
        p: ProxyProfile,
        port: Int,
        s: AppSettings,
        v2rayStyleDelay: Boolean = false
    ): BenchmarkResult {
        if (directProbe(s)) return directResult(p, s)
        if (v2rayStyleDelay) {
            return benchmarkResult(p, measure(p, port, s, false, true), false)
        }
        val effective = intelligence?.effectiveSettings(p, s) ?: s
        if (!s.verifiedPerformanceTuning) {
            val direct = measure(p, port, effective, true)
            return benchmarkResult(p, direct, effective.muxEnabled && !s.muxEnabled)
        }

        val baselineSettings = effective.copy(muxEnabled = s.muxEnabled)
        val chosen = measure(p, port, baselineSettings, true)
        return benchmarkResult(p, chosen, false)
    }

    private fun benchmarkResult(profile: ProxyProfile, m: Measurement, usedMux: Boolean) = BenchmarkResult(
        profile.id, profile.name, m.success, m.latency, m.speed, 0.0,
        udpSuccess = m.udpSuccess, interactiveScore = 0.0, streamingScore = 0.0,
        stabilityScore = 0.0, resilienceScore = 0.0,
        usedMux = usedMux, jitterMs = m.jitter, warmupMs = m.warmup,
        sampleCount = m.sampleCount, p90LatencyMs = m.p90Latency,
        p95LatencyMs = m.p95Latency, medianJitterMs = m.medianJitter,
        p95JitterMs = m.p95Jitter, madLatencyMs = m.madLatency,
        lossPercent = m.lossPercent, spikePercent = m.spikePercent,
        loadedLatencyMs = m.loadedLatency, failureReason = m.failureReason.take(180)
    )

    private fun materiallyBetter(candidate: Measurement, baseline: Measurement): Boolean {
        if (candidate.success <= 0) return false
        if (baseline.success <= 0) return true
        if (candidate.success + 20 < baseline.success) return false
        val latencyGain = (baseline.latency - candidate.latency) / max(1.0, baseline.latency)
        val speedGain = if (baseline.speed > 32.0 * 1024.0) {
            (candidate.speed - baseline.speed) / baseline.speed
        } else if (candidate.speed > baseline.speed + 128.0 * 1024.0) 1.0 else 0.0
        val jitterSafe = baseline.jitter <= 0.0 || candidate.jitter <= 0.0 || candidate.jitter <= baseline.jitter * 1.35 + 4.0
        val tailSafe = baseline.p95Latency <= 0.0 || candidate.p95Latency <= 0.0 || candidate.p95Latency <= baseline.p95Latency * 1.18 + 10.0
        val jitterGain = if (baseline.jitter > 0.0 && candidate.jitter >= 0.0) (baseline.jitter - candidate.jitter) / baseline.jitter else 0.0
        return (latencyGain >= 0.10 && candidate.speed >= baseline.speed * 0.82 && jitterSafe && tailSafe) ||
            (speedGain >= 0.22 && candidate.latency <= baseline.latency * 1.12 && jitterSafe) ||
            (jitterGain >= 0.20 && candidate.latency <= baseline.latency * 1.10 && tailSafe) ||
            candidate.success >= baseline.success + 25
    }

    private fun linkEvidenceFor(profileId: String): LinkEvidence =
        runCatching { intelligence?.linkEvidenceFor(profileId) }.getOrNull() ?: LinkEvidence.UNKNOWN

    private fun measure(
        p: ProxyProfile,
        port: Int,
        s: AppSettings,
        includeThroughput: Boolean,
        v2rayStyleDelay: Boolean = false
    ): Measurement {
        var sawConfidence = 0.0
        val requested = if (v2rayStyleDelay) 2 else PingBudget.samples(s.benchSamples)
        val linkEvidence = linkEvidenceFor(p.id)

        val timeoutMs = if (v2rayStyleDelay) {
            (s.benchTimeoutSec * 1000).coerceIn(4_000, 12_000)
        } else {
            (s.benchTimeoutSec * 1000).coerceIn(1_200, PingBudget.TIMEOUT_MAX_SEC * 1_000)
        }
        var times = emptyList<Double>()
        var warmup = 0.0
        var speed = 0.0
        var udpSuccess = 0
        var failureReason = "${s.coreEngine().id}-start"

        val started = runCatching {
            fun attempt(): Boolean = xray.temporary(p, port, s, delayTest = false, link = linkEvidence) { livePort ->

                val targets = DelayTest.candidates(s.delayTestUrl)
                val batch = targets.firstNotNullOfOrNull { target ->
                    SingBoxProcessSession.checkInterrupted()
                    runCatching {
                        SocksHttpClient.tunnelRttBatchUrl(livePort, target, requested, timeoutMs)
                    }.onFailure { error ->
                        failureReason = "https:${error::class.java.simpleName}:${error.message.orEmpty()}"
                    }.getOrNull()
                }
                if (batch != null) {
                    times = batch.samplesMs.filter { it.isFinite() && it > 0.0 }
                    warmup = batch.warmupMs
                    failureReason = ""
                }

                if (includeThroughput && s.probeSpeedTest && times.isNotEmpty()) {
                    var bytes = s.benchBytes.coerceIn(64 * 1024, 4 * 1024 * 1024)
                    var transfer = SocksHttpClient.sampledGet(
                        livePort,
                        ProtocolFingerprintAwareVerifier.THROUGHPUT_TARGET,
                        "/__down?bytes=$bytes",
                        s.benchTimeoutSec * 1000 + 4_000,
                        bytes + 16_384
                    )
                    speed = max(speed, transfer.bytesPerSecond)
                    sawConfidence = SawtoothDetector.evaluate(transfer.samples).confidence
                    if (transfer.injectedResetSuspected && failureReason.isBlank()) {
                        failureReason = "reset-after-volume"
                    }
                    if (
                        s.adaptiveThroughputEnabled &&
                        transfer.status > 0 &&
                        transfer.bytesPerSecond > 512.0 * 1024.0 &&
                        (intelligence?.thermalBudget(s) ?: 1.0) >= 0.60
                    ) {
                        bytes = max(1024 * 1024, bytes * 4)
                            .coerceAtMost(s.adaptiveThroughputMaxBytes)
                        val second = SocksHttpClient.sampledGet(
                            livePort,
                            ProtocolFingerprintAwareVerifier.THROUGHPUT_TARGET,
                            "/__down?bytes=$bytes",
                            s.benchTimeoutSec * 1000 + 7_000,
                            bytes + 16_384
                        )
                        transfer = second
                        speed = max(speed, transfer.bytesPerSecond)
                        sawConfidence = max(sawConfidence, SawtoothDetector.evaluate(transfer.samples).confidence)
                    }
                    if (sawConfidence >= ProtocolFingerprintAwareVerifier.SAWTOOTH_HIGH_CONFIDENCE &&
                        failureReason.isBlank()
                    ) {
                        failureReason = "sawtooth-%.2f".format(sawConfidence)
                    }
                }
                if (times.isNotEmpty() && s.udpProbeEnabled) {
                    udpSuccess = if (
                        SocksUdpProbe.stun(
                            livePort,
                            timeoutMs = min(3500, s.benchTimeoutSec * 1000)
                        ) > 0
                    ) 100 else 0
                }
            }
            val spawned = attempt()
            if (spawned || times.isNotEmpty()) spawned else attempt()
        }.onFailure { error ->
            if (error is InterruptedException) { Thread.currentThread().interrupt(); throw error }
            failureReason = error.message ?: "${s.coreEngine().id}-start"
        }.getOrDefault(false)
        if (!started && failureReason.isBlank()) failureReason = "${s.coreEngine().id}-start"

        val outcomes = times.map { kotlin.math.round(it).toInt().coerceIn(1, 10_000) }.toMutableList()
        repeat((requested - outcomes.size).coerceAtLeast(0)) { outcomes += -1 }
        val link = LinkQualityEstimator.summarize(outcomes)
        val ordered = times.sorted()
        val latency = when {
            ordered.isEmpty() -> 9999.0

            v2rayStyleDelay -> LinkQualityEstimator.sanitaryRtt(ordered.first())
            link != null -> link.medianRttMs.toDouble()
            else -> LinkQualityEstimator.sanitaryRtt(ordered[ordered.size / 2])
        }
        val success = if (v2rayStyleDelay) { if (times.isNotEmpty()) 100 else 0 }
            else link?.successPercent ?: (times.size * 100 / requested)

        if (!CoreFailurePolicy.isLocal(failureReason)) runCatching {
            intelligence?.recordTransportVerdict(
                p.id,
                ProtocolFingerprintAwareVerifier.TransportVerdict(
                    transportType = ProtocolFingerprintAwareVerifier.transportTypeOf(p),
                    successPercent = success,
                    latencyMs = latency,
                    jitterMs = (link?.ewmaJitterMs ?: 0.0).toDouble(),
                    throughputBps = speed,
                    sawtoothConfidence = sawConfidence,
                    injectedResetSuspected = failureReason.contains("reset"),
                    silentTimeoutSuspected = success <= 0 && failureReason.contains("timeout"),
                    baselineDeltaRatio = 0.0,
                    probeTargetUsed = "rank-pool"
                )
            )
        }
        return Measurement(
            success, latency, speed, udpSuccess,
            (link?.ewmaJitterMs ?: -1).takeIf { it >= 0 }?.toDouble() ?: 0.0,
            warmup, link?.successes ?: times.size, failureReason,
            link?.p90RttMs?.toDouble() ?: latency, link?.p95RttMs?.toDouble() ?: latency,
            (link?.medianIpdvMs ?: -1).takeIf { it >= 0 }?.toDouble() ?: 0.0,
            (link?.p95IpdvMs ?: -1).takeIf { it >= 0 }?.toDouble() ?: 0.0,
            link?.madRttMs?.toDouble() ?: 0.0, link?.lossPercent?.toDouble() ?: (100-success).toDouble(),
            link?.spikePercent?.toDouble() ?: 0.0
        )
    }

    private fun quickCandidate(p: ProxyProfile, port: Int, s: AppSettings): BenchmarkResult {
        val effective = intelligence?.effectiveSettings(p, s) ?: s
        val m = quickMeasure(p, port, effective)
        return BenchmarkResult(
            p.id,
            p.name,
            m.success,
            m.latency,
            0.0,
            quickScore(m),
            udpSuccess = 0
        )
    }

    private fun quickScore(m: Measurement): Double {
        if (m.success <= 0) return -1.0
        val reliability = m.success.toDouble().coerceIn(0.0, 100.0)
        val latency = 100.0 * exp(-m.latency.coerceAtMost(5000.0) / 240.0)
        return (reliability * 0.45 + latency * 0.55).coerceIn(0.0, 100.0)
    }

    private fun quickMeasure(p: ProxyProfile, port: Int, s: AppSettings): Measurement {
        var elapsed = 9999.0
        var ok = 0
        runCatching {
            xray.temporary(p, port, s.copy(benchSamples = 1), link = linkEvidenceFor(p.id)) { livePort ->
                val r = SocksHttpClient.get(livePort, "cp.cloudflare.com", "/generate_204", s.benchTimeoutSec * 1000, 4096)
                if (r.status in 200..399) {
                    elapsed = r.elapsedMs
                    ok = 1
                }
            }
        }
        return Measurement(if (ok == 1) 100 else 0, elapsed, 0.0, 0)
    }

    private fun rank(
        raw: List<BenchmarkResult>,
        settings: AppSettings
    ): List<BenchmarkResult> {
        if (raw.isEmpty()) return raw
        return raw.map { r ->
            if (r.success <= 0) return@map r.copy(score = -1.0)
            val q = RealtimeQualityEngine.score(r, settings.workloadProfile, settings.benchMode)
            val resilience = q.resilience.coerceIn(0.0, 100.0)
            r.copy(score = if (settings.workloadProfile == WorkloadProfile.STEALTH) resilience else q.selected,
                interactiveScore = q.interactive, streamingScore = q.streaming,
                stabilityScore = q.stability, resilienceScore = resilience)
        }.sortedWith(compareByDescending<BenchmarkResult> { it.score }.thenBy { it.latencyMs }.thenBy { it.jitterMs })
    }

    private fun tcpLatency(p: ProxyProfile, timeoutMs: Int, settings: AppSettings = AppSettings()): Double {
        if (p.host.isBlank() || p.port <= 0) return DEAD_LATENCY

        val signal = MultiVectorReachability.probe(
            host = p.host,
            port = p.port,
            timeoutMs = timeoutMs.coerceIn(300, 12_000),
            settings = settings
        )
        val verifiedMs = signal.timeToFirstByteMs
        return if (signal.verified && verifiedMs != null && verifiedMs.isFinite()) {
            verifiedMs.coerceAtLeast(20.0)
        } else {
            DEAD_LATENCY
        }
    }

    private fun isUdpNative(p: ProxyProfile): Boolean =
        p.scheme.equals("hysteria2", true) || p.scheme.equals("wireguard", true) ||
            p.transport.contains("hysteria", true) || p.transport.equals("mkcp", true) || p.transport.equals("kcp", true)

    private fun canMux(p: ProxyProfile): Boolean =
        p.scheme.lowercase() in setOf("vless", "vmess", "trojan", "ss") && !isUdpNative(p)

    private fun benchmarkPort(index: Int): Int =
        BASE_PORT + (index.coerceAtLeast(0) % BENCHMARK_PORT_SLOTS) * 4

    private fun formatRate(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f MiB/s", bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024.0 -> String.format(Locale.US, "%.0f KiB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.US, "%.0f B/s", bytesPerSecond)
    }

    private companion object {
        const val BASE_PORT = 18080
        const val BENCHMARK_PORT_SLOTS = 10_000
        const val RACE_BASE_PORT = 19280
        const val OPTIMIZER_BASE_PORT = 20580
        const val BATCH_DIRECT_GRACE_MS = 5_000L
        const val BATCH_TUNNEL_GRACE_MS = 45_000L
        const val BATCH_WAVE_GRACE_MS = 10_000L
        const val BATCH_ABSOLUTE_CAP_MS = 15 * 60_000L
        const val CANCEL_POLL_SLICE_MS = 250L
        fun rankTargets(): List<Pair<String, String>> {
            val pool = ProbeTargetPool.ordered(ProbeTargetPool.CDN_TARGETS)
            val literals = ProbeTargetPool.ordered(ProbeTargetPool.LITERAL_TARGETS)
            return pool.map { it.host to it.path } + literals.take(3).map { it.host to it.path }
        }

        val TUNNEL_TARGET_CURSOR = AtomicInteger(0)
        const val DEAD_LATENCY = 99_999.0
    }
}

internal fun sweepWidth(
    method: ProbeMethod,
    engine: CoreEngine,
    tcpWorkers: Int,
    cpus: Int,
    singBoxCeiling: Int,
    xrayChildCeiling: Int,
    speedFactor: Double,
    v2rayStyleDelay: Boolean
): Int = when {
    method == ProbeMethod.TCP_PING -> PingBudget.concurrency(tcpWorkers)
    method == ProbeMethod.URL_TEST || engine == CoreEngine.SINGBOX ->
        tcpWorkers.coerceIn(1, singBoxCeiling.coerceIn(1, 64))
    v2rayStyleDelay -> tcpWorkers.coerceIn(2, 4)
    else -> {
        val base = cpus.coerceIn(2, 4)

        val factor = if (speedFactor.isFinite()) speedFactor else 1.0
        val scaled = (base * factor).roundToInt()
        scaled.coerceIn(2, xrayChildCeiling.coerceIn(2, 8))
    }
}
