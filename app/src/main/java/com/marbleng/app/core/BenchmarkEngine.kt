package com.marbleng.app.core

import com.marbleng.app.model.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Real-tunnel benchmark and predictive route selector.
 *
 * TCP precheck is deliberately only a dead-node gate. Historical tunnel health decides which
 * survivors deserve expensive full tests. This prevents a fast SYN from being mistaken for a fast
 * REALITY/XHTTP/gRPC route.
 */
class BenchmarkEngine(
    private val xray: XrayManager,
    private val intelligence: MarbleIntelligence? = null
) {
    // MARBLE_EVIDENCE_TIERS_V24
    // MARBLE_BENCHMARK_ALL_NODES_V27
    // MARBLE_DIRECT_PING_TIMEOUT_V33
    // MARBLE_TEMP_PORT_CONSUMER_V38
    // MARBLE_WARM_TUNNEL_RANK_V42

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        usePrecheck: Boolean = true,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        if (profiles.isEmpty()) return emptyList()
        val s = tuned(settings)
        // TUNNEL means "test everything for real"; HYBRID keeps the cheap TCP gate in front of the
        // expensive tunnel tests, which is what makes a large library finish in reasonable time.
        val precheck = usePrecheck && s.probeMethod != ProbeMethod.TUNNEL
        // The same node can legitimately appear in two subscriptions; test it once.
        val candidates = selectCandidates(profiles, s, precheck).distinctBy { it.id }
        if (candidates.isEmpty()) return emptyList()
        onCandidates(candidates)

        val thermal = intelligence?.thermalBudget(s) ?: 1.0
        val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        // Probing waits on sockets far more than on the CPU, and direct probes spawn no process at
        // all, so the old (cpu / 2) cap left most of the batch idle behind four workers.
        val nominalWorkers = if (directProbe(s)) {
            // Direct probes are socket-wait bound. Honor the bounded ping-concurrency control.
            s.tcpWorkers.coerceIn(4, 32)
        } else {
            // Real Xray tests launch temporary native processes. Bound fanout for large batches.
            cpu.coerceIn(2, 4)
        }
        val liveWorkers = max(1, (nominalWorkers * thermal).toInt())
            .coerceAtMost(candidates.size)

        val livePool = Executors.newFixedThreadPool(liveWorkers)
        val completed = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<BenchmarkResult>())
        val jobs = candidates.mapIndexed { idx, p ->
            livePool.submit {
                onStart(p)
                // Score each measurement as it lands so the caller can publish a finished node
                // immediately instead of holding every result back until the batch ends.
                val measured = testCandidate(p, benchmarkPort(idx), s)
                val result = rank(listOf(measured), s).firstOrNull() ?: measured
                results += result
                // TCP/ICMP proves endpoint reachability, not that the Xray route/account works.
                // Never poison persistent tunnel intelligence with underlay-only measurements.
                if (!directProbe(s)) {
                    intelligence?.recordBenchmark(p, result, s)
                }
                onResult(p, result)
                onProgress(completed.incrementAndGet(), candidates.size, p.name)
            }
        }
        jobs.forEach { runCatching { it.get() } }
        livePool.shutdown()

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

    /**
     * Small happy-eyeballs style race across the historically best candidates. Each candidate gets
     * a real Xray process and one HTTPS route probe. First healthy completion wins; losers are
     * cancelled and their temporary processes terminate in XrayManager.temporary() finally blocks.
     */
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

    /**
     * Autopilot background comparison. Fast cycles do two real HTTPS samples for latency.
     * Deep cycles do three samples plus one bounded 128 KiB download for fresh speed evidence.
     */
    fun continuousProbe(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        deep: Boolean,
        onProgress: (String) -> Unit = {}
    ): List<BenchmarkResult> {
        val unique = profiles.distinctBy { it.id }.take(9)
        if (unique.isEmpty()) return emptyList()
        val probeSettings = tuned(settings).copy(
            benchMode = BenchMode.CUSTOM,
            benchCandidates = unique.size,
            benchSamples = if (deep) 3 else 2,
            benchTimeoutSec = min(settings.benchTimeoutSec, 6),
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
            usedFragment = effective.fragmentEnabled && !settings.fragmentEnabled,
            usedMux = effective.muxEnabled && !settings.muxEnabled
        )
    }

    private fun continuousScore(
        result: BenchmarkResult,
        historicalScore: Double,
        deep: Boolean
    ): Double {
        if (result.success <= 0) return -1.0
        val reliability = result.success.toDouble().coerceIn(0.0, 100.0)
        val latency = 100.0 * exp(-result.latencyMs.coerceAtMost(5000.0) / 230.0)
        val history = historicalScore.coerceIn(0.0, 100.0)
        if (!deep) {
            return (reliability * 0.48 + latency * 0.44 + history * 0.08)
                .coerceIn(0.0, 100.0)
        }
        val mbps = result.bytesPerSecond * 8.0 / 1_000_000.0
        val speed = (ln(1.0 + mbps) / ln(51.0) * 100.0).coerceIn(0.0, 100.0)
        return (reliability * 0.40 + latency * 0.35 + speed * 0.17 + history * 0.08)
            .coerceIn(0.0, 100.0)
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

        // Evidence lookups are resolved once per selection pass. Querying history per profile (or
        // worse, per comparison) turned every "test all" on a large subscription into thousands of
        // synchronized SQLite reads before a single node was probed.
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
            // Caller explicitly disabled candidate gating: test every distinct supplied node.
            return ordered
        }

        val workers =
            s.tcpWorkers
                .coerceIn(1, 32)

        // Huge subscriptions no longer receive a TCP SYN test for every config on every tap.
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

        // Rotate unknown exploration every six hours so new nodes are not permanently starved.
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
                                s.tcpPrecheckTimeoutMs
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

                // Enough healthy pre-gate survivors; let actual Xray tunnel tests decide.
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
        val sampleCount: Int = 0
    )

    /** True when the selected method never needs a temporary Xray process. */
    private fun directProbe(s: AppSettings): Boolean =
        s.probeMethod == ProbeMethod.TCP || s.probeMethod == ProbeMethod.ICMP

    private fun directResult(p: ProxyProfile, s: AppSettings): BenchmarkResult {
        val directTimeoutMs =
            if (s.probeMethod == ProbeMethod.TCP) {
                min(
                    (s.benchTimeoutSec * 1000).coerceIn(500, 10_000),
                    s.tcpPrecheckTimeoutMs.coerceIn(250, 10_000)
                )
            } else {
                (s.benchTimeoutSec * 1000).coerceIn(500, 10_000)
            }
        val sample = RouteProbe.measure(
            profile = p,
            icmpMode = s.probeMethod == ProbeMethod.ICMP,
            samples = s.benchSamples.coerceIn(1, 8),
            timeoutMs = directTimeoutMs
        )
        return BenchmarkResult(
            profileId = p.id,
            name = p.name,
            success = sample.successPercent,
            latencyMs = sample.latencyMs,
            bytesPerSecond = 0.0,
            score = 0.0,
            probeKind = if (s.probeMethod == ProbeMethod.ICMP) "ICMP" else "TCP"
        )
    }

    private fun testCandidate(p: ProxyProfile, port: Int, s: AppSettings): BenchmarkResult {
        if (directProbe(s)) return directResult(p, s)
        val effective = intelligence?.effectiveSettings(p, s) ?: s
        if (!s.verifiedPerformanceTuning) {
            val direct = measure(p, port, effective, true)
            return benchmarkResult(p, direct, effective.fragmentEnabled && !s.fragmentEnabled, effective.muxEnabled && !s.muxEnabled)
        }
        val baselineSettings = effective.copy(fragmentEnabled = s.fragmentEnabled, muxEnabled = s.muxEnabled)
        var chosenSettings = baselineSettings
        var chosen = measure(p, port, baselineSettings, true)
        var usedFragment = false
        var usedMux = false

        val learnedFragment = effective.fragmentEnabled && !s.fragmentEnabled
        if (s.adaptiveFragmentEnabled && !s.fragmentEnabled && canFragment(p) && (chosen.success == 0 || learnedFragment)) {
            val candidateSettings = baselineSettings.copy(fragmentEnabled = true, muxEnabled = false)
            val candidate = measure(p, port + 1, candidateSettings, true)
            if (materiallyBetter(candidate, chosen)) {
                chosen = candidate; chosenSettings = candidateSettings; usedFragment = true; usedMux = false
            }
        }

        val learnedMux = effective.muxEnabled && !s.muxEnabled
        if (s.adaptiveMuxEnabled && !s.muxEnabled && canMux(p) && chosen.success > 0 &&
            (learnedMux || chosen.latency >= 120.0) && (intelligence?.thermalBudget(s) ?: 1.0) >= 0.60) {
            val probeSettings = chosenSettings.copy(
                muxEnabled = true,
                benchSamples = min(2, s.benchSamples.coerceAtLeast(1)),
                benchBytes = min(256 * 1024, s.benchBytes.coerceAtLeast(64 * 1024)),
                adaptiveThroughputEnabled = false,
                udpProbeEnabled = false
            )
            val candidate = measure(p, port + 2, probeSettings, true)
            if (materiallyBetter(candidate, chosen)) { chosen = candidate; usedMux = true }
        }
        return benchmarkResult(p, chosen, usedFragment, usedMux)
    }

    private fun benchmarkResult(profile: ProxyProfile, m: Measurement, usedFragment: Boolean, usedMux: Boolean) = BenchmarkResult(
        profile.id, profile.name, m.success, m.latency, m.speed, 0.0,
        udpSuccess = m.udpSuccess, interactiveScore = 0.0, streamingScore = 0.0,
        stabilityScore = 0.0, resilienceScore = 0.0, usedFragment = usedFragment,
        usedMux = usedMux, jitterMs = m.jitter, warmupMs = m.warmup,
        sampleCount = m.sampleCount
    )

    private fun materiallyBetter(candidate: Measurement, baseline: Measurement): Boolean {
        if (candidate.success <= 0) return false
        if (baseline.success <= 0) return true
        if (candidate.success + 20 < baseline.success) return false
        val latencyGain = (baseline.latency - candidate.latency) / max(1.0, baseline.latency)
        val speedGain = if (baseline.speed > 32.0 * 1024.0) {
            (candidate.speed - baseline.speed) / baseline.speed
        } else if (candidate.speed > baseline.speed + 128.0 * 1024.0) 1.0 else 0.0
        return (latencyGain >= 0.12 && candidate.speed >= baseline.speed * 0.82) ||
            (speedGain >= 0.22 && candidate.latency <= baseline.latency * 1.12) ||
            candidate.success >= baseline.success + 25
    }

    private fun measure(
        p: ProxyProfile,
        port: Int,
        s: AppSettings,
        includeThroughput: Boolean
    ): Measurement {
        val requested = s.benchSamples.coerceIn(1, 8)
        val timeoutMs = (s.benchTimeoutSec * 1000).coerceIn(1_200, 3_000)
        var times = emptyList<Double>()
        var warmup = 0.0
        var speed = 0.0
        var udpSuccess = 0

        runCatching {
            xray.temporary(p, port, s) { livePort ->
                // Official Xray HTTPing defaults to gstatic 204. Cloudflare is an independent
                // fallback for routes where that origin is unavailable. A timeout is a failed
                // sample, never a synthetic 5000/9999 ms latency value.
                val batch = TUNNEL_PROBE_TARGETS.firstNotNullOfOrNull { target ->
                    runCatching {
                        SocksHttpClient.tunnelRttBatch(
                            port = livePort,
                            host = target.first,
                            path = target.second,
                            samples = requested,
                            timeoutMs = timeoutMs
                        )
                    }.getOrNull()
                }
                if (batch != null) {
                    times = batch.samplesMs.filter { it.isFinite() && it > 0.0 }
                    warmup = batch.warmupMs
                }

                if (includeThroughput && s.probeSpeedTest && times.isNotEmpty()) {
                    var bytes = s.benchBytes.coerceIn(64 * 1024, 4 * 1024 * 1024)
                    var transfer = SocksHttpClient.get(
                        livePort,
                        "speed.cloudflare.com",
                        "/__down?bytes=$bytes",
                        s.benchTimeoutSec * 1000 + 4_000,
                        bytes + 16_384
                    )
                    speed = max(speed, transfer.bytesPerSecond)
                    if (
                        s.adaptiveThroughputEnabled &&
                        transfer.status > 0 &&
                        transfer.bytesPerSecond > 512.0 * 1024.0 &&
                        (intelligence?.thermalBudget(s) ?: 1.0) >= 0.60
                    ) {
                        bytes = max(1024 * 1024, bytes * 4)
                            .coerceAtMost(s.adaptiveThroughputMaxBytes)
                        transfer = SocksHttpClient.get(
                            livePort,
                            "speed.cloudflare.com",
                            "/__down?bytes=$bytes",
                            s.benchTimeoutSec * 1000 + 7_000,
                            bytes + 16_384
                        )
                        speed = max(speed, transfer.bytesPerSecond)
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
        }

        fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val orderedValues = values.sorted()
            val middle = orderedValues.size / 2
            return if (orderedValues.size % 2 == 1) orderedValues[middle]
            else (orderedValues[middle - 1] + orderedValues[middle]) / 2.0
        }
        val ordered = times.sorted()
        val latency = if (ordered.isEmpty()) 9999.0 else median(ordered)
        val variation = times.zipWithNext { a, b -> kotlin.math.abs(b - a) }.sorted()
        val jitter = median(variation)
        val success = times.size * 100 / requested
        return Measurement(success, latency, speed, udpSuccess, jitter, warmup, times.size)
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

    /**
     * A race probe is one real HTTPS round trip through a real Xray process, so it deserves a real
     * score. Leaving it at 0 made the winning route show "0 / 100" everywhere until the first live
     * telemetry sample arrived.
     */
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
            xray.temporary(p, port, s.copy(benchSamples = 1)) { livePort ->
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
        if (raw.isEmpty()) {
            return raw
        }

        val sampleN =
            settings.benchSamples
                .coerceIn(1, 8)
                .toDouble()

        val confidence =
            (
                sampleN /
                    4.0
            ).coerceIn(
                0.25,
                1.0
            )

        return raw.map {
            result ->

            val latency =
                if (
                    result.success <=
                    0
                ) {
                    0.0
                } else {
                    100.0 *
                        exp(
                            -result.latencyMs
                                .coerceAtMost(
                                    5000.0
                                ) /
                                240.0
                        )
                }

            // No speed evidence (direct probe, or the optional speed test is off) must not read as
            // "slow": an unknown throughput scores neutral instead of zero.
            val speed =
                if (
                    result.success <=
                    0
                ) {
                    0.0
                } else if (
                    result.bytesPerSecond <=
                    0.0
                ) {
                    50.0
                } else {
                    val mbps =
                        result.bytesPerSecond *
                            8.0 /
                            1_000_000.0

                    (
                        ln(
                            1.0 +
                                mbps
                        ) /
                            ln(
                                101.0
                            ) *
                            100.0
                    ).coerceIn(
                        0.0,
                        100.0
                    )
                }

            val observedReliability =
                result.success
                    .toDouble()
                    .coerceIn(
                        0.0,
                        100.0
                    )

            // One 1/1 result is useful, but it is not the same evidence as four independent samples.
            val reliability =
                if (
                    result.success <=
                    0
                ) {
                    0.0
                } else {
                    observedReliability *
                        confidence +
                        65.0 *
                        (
                            1.0 -
                                confidence
                        )
                }

            val udp =
                if (
                    result.udpSuccess <=
                    0
                ) {
                    50.0
                } else {
                    result.udpSuccess
                        .toDouble()
                        .coerceIn(
                            0.0,
                            100.0
                        )
                }

            val variation =
                if (result.sampleCount < 2) 50.0 else
                    100.0 * exp(-result.jitterMs.coerceAtMost(2000.0) / 65.0)

            val interactive =
                reliability * 0.35 +
                    latency * 0.50 +
                    variation * 0.15

            val streaming =
                reliability * 0.30 +
                    speed * 0.55 +
                    latency * 0.10 +
                    variation * 0.05

            val stability =
                reliability * 0.55 +
                    latency * 0.20 +
                    variation * 0.25

            val resilience =
                (
                    reliability * 0.68 +
                        udp * 0.15 +
                        latency * 0.12 +
                        if (
                            result.usedFragment
                        ) {
                            5.0
                        } else {
                            0.0
                        }
                ).coerceIn(
                    0.0,
                    100.0
                )

            val score =
                when (
                    settings.workloadProfile
                ) {
                    WorkloadProfile.INTERACTIVE ->
                        interactive
                    WorkloadProfile.STREAMING ->
                        streaming
                    WorkloadProfile.STABILITY ->
                        stability
                    WorkloadProfile.STEALTH ->
                        resilience
                    WorkloadProfile.AUTO ->
                        when (
                            settings.benchMode
                        ) {
                            BenchMode.RELIABLE ->
                                stability

                            BenchMode.FAST,
                            BenchMode.TURBO ->
                                interactive *
                                    0.58 +
                                    streaming *
                                    0.42

                            BenchMode.BALANCED,
                            BenchMode.CUSTOM ->
                                interactive *
                                    0.31 +
                                    streaming *
                                    0.27 +
                                    stability *
                                    0.31 +
                                    resilience *
                                    0.11
                        }
                }

            result.copy(
                score =
                    if (
                        result.success <=
                        0
                    ) {
                        -1.0
                    } else {
                        score
                    },
                interactiveScore =
                    interactive,
                streamingScore =
                    streaming,
                stabilityScore =
                    stability,
                resilienceScore =
                    resilience
            )
        }.sortedWith(
            compareByDescending<
                BenchmarkResult
            > {
                it.score
            }.thenBy {
                it.latencyMs
            }
        )
    }

    private fun tcpLatency(p: ProxyProfile, timeoutMs: Int): Double {
        if (p.host.isBlank() || p.port <= 0) return DEAD_LATENCY
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(p.host, p.port), timeoutMs) }
            (System.nanoTime() - start) / 1e6
        } catch (_: Throwable) {
            DEAD_LATENCY
        }
    }

    private fun isUdpNative(p: ProxyProfile): Boolean =
        p.scheme.equals("hysteria2", true) || p.scheme.equals("wireguard", true) ||
            p.transport.contains("hysteria", true) || p.transport.equals("mkcp", true) || p.transport.equals("kcp", true)

    private fun canFragment(p: ProxyProfile): Boolean =
        !isUdpNative(p) && (p.security.contains("tls", true) || p.security.contains("reality", true))

    private fun canMux(p: ProxyProfile): Boolean =
        p.scheme.lowercase() in setOf("vless", "vmess", "trojan", "ss") && !isUdpNative(p)

    /**
     * Preferred ports are only hints. XrayManager owns collision-safe reservation across
     * benchmark, race, optimizer and Turbo workers.
     */
    private fun benchmarkPort(index: Int): Int =
        BASE_PORT + (index.coerceAtLeast(0) % BENCHMARK_PORT_SLOTS) * 4

    private fun formatRate(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1024.0 * 1024.0 -> "%.1f MiB/s".format(bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024.0 -> "%.0f KiB/s".format(bytesPerSecond / 1024.0)
        else -> "%.0f B/s".format(bytesPerSecond)
    }

    private companion object {
        const val BASE_PORT = 18080
        const val BENCHMARK_PORT_SLOTS = 10_000
        const val RACE_BASE_PORT = 19280
        const val OPTIMIZER_BASE_PORT = 20580
        val TUNNEL_PROBE_TARGETS = listOf(
            "connectivitycheck.gstatic.com" to "/generate_204",
            "cp.cloudflare.com" to "/generate_204"
        )
        const val DEAD_LATENCY = 99_999.0
    }
}

