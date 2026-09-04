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

/**
 * Real-tunnel benchmark and predictive route selector.
 *
 * Endpoint-only TCP evidence is never allowed to reject a profile during a real Xray Rank. Some
 * fronted, chained and provider-specific routes work through the protocol core even when a naked
 * socket gate is blocked. Historical tunnel health still decides test order.
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
    // MARBLE_RANK_RECOVERY_CARD_UX_V43
    // MARBLE_REPEATABLE_RANK_V44
    // MARBLE_V2RAYNG_SMART_RANK_V45
    // MARBLE_EVIDENCE_WEIGHTED_QUALITY_V46
    // MARBLE_BENCH_COMPAT_V47
    // MARBLE_RUNTIME_PARITY_RANK_V61
    // MARBLE_REALTIME_ENGINE_V70

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        usePrecheck: Boolean = true,
        v2rayStyleDelay: Boolean = false,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        if (profiles.isEmpty()) return emptyList()
        val s = tuned(settings)
        val batchNetworkKey = intelligence?.currentSnapshot()?.key()
        // TUNNEL means "test everything for real". The v2rayNG-style path also keeps every card
        // and goes straight to Xray, because an underlay TCP failure cannot prove a proxy failure.
        val precheck = usePrecheck && s.probeMethod != ProbeMethod.TUNNEL && !v2rayStyleDelay
        val candidates = selectCandidates(profiles, s, precheck).distinctBy { it.id }
        if (candidates.isEmpty()) return emptyList()
        onCandidates(candidates)

        val thermal = intelligence?.thermalBudget(s) ?: 1.0
        val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        // Probing waits on sockets far more than on the CPU, and direct probes spawn no process at
        // all, so the old (cpu / 2) cap left most of the batch idle behind four workers.
        val nominalWorkers = when {
            directProbe(s) -> s.tcpWorkers.coerceIn(4, 32)
            // MarbleNG launches one native Xray child per candidate, unlike v2rayNG's in-process
            // dialer. Four is the safe ceiling here: larger same-host bursts can manufacture
            // Connection reset / TLS timeout failures that disappear when the node is tapped alone.
            v2rayStyleDelay -> s.tcpWorkers.coerceIn(2, 4)
            else -> cpu.coerceIn(2, 4)
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
                val measured = testCandidate(p, benchmarkPort(idx), s, v2rayStyleDelay)
                val result = rank(listOf(measured), s).firstOrNull() ?: measured
                results += result
                // TCP/ICMP proves endpoint reachability, not that the Xray route/account works.
                // Never poison persistent tunnel intelligence with underlay-only measurements.
                // MARBLE_SMART_PING_RESCUE_V123 — the same rule covers a Smart ping that only
                // reached its reachability gate: it is honest evidence for the card, not proof
                // of a working route, so it must not enter the ranker's memory.
                if (
                    !directProbe(s) &&
                    result.probeKind != SMART_PROBE_KIND &&
                    (batchNetworkKey == null || intelligence?.currentSnapshot()?.key() == batchNetworkKey)
                ) {
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

    /** True when the selected method never needs a temporary Xray process. */
    private fun directProbe(s: AppSettings): Boolean =
        s.probeMethod == ProbeMethod.TCP || s.probeMethod == ProbeMethod.ICMP ||
            s.probeMethod == ProbeMethod.HTTP || s.probeMethod == ProbeMethod.DNS

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
        // MARBLE_PROBE_TOOLKIT_V130 — dispatch to the right RouteProbe method
        return when (s.probeMethod) {
            ProbeMethod.HTTP -> {
                val result = RouteProbe.httpPingBatch(
                    socksPort = 0,
                    timeoutMs = directTimeoutMs,
                    samples = s.benchSamples.coerceIn(1, 8)
                )
                BenchmarkResult(
                    profileId = p.id,
                    name = p.name,
                    success = result.successPercent,
                    latencyMs = result.latencyMs,
                    bytesPerSecond = 0.0,
                    score = 0.0,
                    probeKind = "HTTP",
                    jitterMs = result.jitterMs,
                    p95LatencyMs = result.p95Ms,
                    lossPercent = result.lossPercent
                )
            }
            ProbeMethod.DNS -> {
                val result = RouteProbe.dnsPingExtended(
                    host = p.host,
                    timeoutMs = directTimeoutMs,
                    samples = s.benchSamples.coerceIn(1, 8)
                )
                BenchmarkResult(
                    profileId = p.id,
                    name = p.name,
                    success = result.successPercent,
                    latencyMs = result.latencyMs,
                    bytesPerSecond = 0.0,
                    score = 0.0,
                    probeKind = "DNS",
                    jitterMs = result.jitterMs
                )
            }
            else -> {
                val sample = RouteProbe.measure(
                    profile = p,
                    icmpMode = s.probeMethod == ProbeMethod.ICMP,
                    samples = s.benchSamples.coerceIn(1, 8),
                    timeoutMs = directTimeoutMs,
                    settings = s
                )
                BenchmarkResult(
                    profileId = p.id,
                    name = p.name,
                    success = sample.successPercent,
                    latencyMs = sample.latencyMs,
                    bytesPerSecond = 0.0,
                    score = 0.0,
                    probeKind = if (s.probeMethod == ProbeMethod.ICMP) "ICMP" else "TCP"
                )
            }
        }
    }

    /**
     * MARBLE_SMART_PING_RESCUE_V123 — Smart ping: cheap reachability gate first, real tunnel
     * verification second, and an honest verdict when only the first of those completes.
     *
     * Why the old behaviour failed healthy servers: [ProbeMethod.HYBRID] was not a method of its
     * own, it fell through to the same code path as [ProbeMethod.TUNNEL]. Every server therefore
     * paid for a child Xray process plus a two-second HTTPS budget, and on a phone that budget is
     * routinely lost to process start-up and CPU contention — the measurement came back empty and
     * the node was painted red even though the endpoint answers a handshake in 40 ms.
     *
     * Now:
     *  1. [RouteProbe.smartGate] proves the endpoint answers (one TCP handshake, one resolver
     *     round trip as a second opinion). No child process, so a dead node is reported in well
     *     under a second instead of after a full start-up timeout.
     *  2. Only a gate-passer pays for the verified tunnel measurement, and it is given a
     *     start-up budget ([SMART_VERIFY_START_SEC]) that survives a busy phone.
     *  3. A gate-passer whose verification could not complete is reported *reachable but
     *     unverified* — [RouteProbe.GATE_ONLY_SUCCESS] with the real handshake latency — and is
     *     never recorded as tunnel intelligence, so it can neither show a false red cross nor
     *     teach the ranker that an unverified node is a proven route.
     */
    private fun smartCandidate(p: ProxyProfile, port: Int, s: AppSettings): BenchmarkResult {
        val gateTimeoutMs = (s.benchTimeoutSec * 400).coerceIn(700, 2_500)
        val gate = RouteProbe.smartGate(p, gateTimeoutMs, s)
        if (!gate.reached) {
            return BenchmarkResult(
                profileId = p.id,
                name = p.name,
                success = 0,
                latencyMs = 9999.0,
                bytesPerSecond = 0.0,
                score = 0.0,
                probeKind = SMART_PROBE_KIND,
                failureReason = gate.reason
            )
        }

        val effective = intelligence?.effectiveSettings(p, s) ?: s
        // The verified phase keeps the user's sample count but never inherits a start-up budget
        // that is smaller than the time a cold Xray child needs on a loaded device.
        val verifySettings = effective.copy(
            benchTimeoutSec = max(s.benchTimeoutSec, SMART_VERIFY_START_SEC),
            probeSpeedTest = false,
            udpProbeEnabled = false
        )
        val verified = measure(p, port, verifySettings, includeThroughput = false)
        if (verified.success > 0) {
            return benchmarkResult(
                p,
                verified,
                effective.fragmentEnabled && !s.fragmentEnabled,
                effective.muxEnabled && !s.muxEnabled
            )
        }

        return BenchmarkResult(
            profileId = p.id,
            name = p.name,
            success = RouteProbe.GATE_ONLY_SUCCESS,
            latencyMs = LinkQualityEstimator.sanitaryRtt(gate.latencyMs),
            bytesPerSecond = 0.0,
            score = 0.0,
            probeKind = SMART_PROBE_KIND,
            failureReason = "unverified:${verified.failureReason.ifBlank { "tunnel" }}"
        )
    }

    private fun testCandidate(
        p: ProxyProfile,
        port: Int,
        s: AppSettings,
        v2rayStyleDelay: Boolean = false
    ): BenchmarkResult {
        if (directProbe(s)) return directResult(p, s)
        if (v2rayStyleDelay) {
            return benchmarkResult(p, measure(p, port, s, false, true), false, false)
        }
        // MARBLE_SMART_PING_RESCUE_V123 — Smart ping is its own method, not a rename of the real
        // tunnel test. It used to fall through to the tunnel path, so the product default paid
        // for a child Xray process per server and reported 0% whenever that process lost the
        // race against a two-second budget: every server, healthy ones included, came back red.
        if (s.probeMethod == ProbeMethod.HYBRID) return smartCandidate(p, port, s)
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

    private fun measure(
        p: ProxyProfile,
        port: Int,
        s: AppSettings,
        includeThroughput: Boolean,
        v2rayStyleDelay: Boolean = false
    ): Measurement {
        val requested = if (v2rayStyleDelay) 2 else s.benchSamples.coerceIn(1, 8)
        val timeoutMs = if (v2rayStyleDelay) {
            (s.benchTimeoutSec * 1000).coerceIn(4_000, 12_000)
        } else {
            (s.benchTimeoutSec * 1000).coerceIn(1_200, 2_500)
        }
        var times = emptyList<Double>()
        var warmup = 0.0
        var speed = 0.0
        var udpSuccess = 0
        var failureReason = "xray-start"

        val started = runCatching {
            // Reachability must be judged with the same runtime-compatible hardening class
            // used by a real user connection. The old delayTest=true path deliberately stripped
            // managed runtime pieces; a config could therefore fail Rank yet work immediately when
            // tapped. v2rayStyleDelay still controls the lightweight HTTP measurement semantics.
            xray.temporary(p, port, s, delayTest = false) { livePort ->
                // Official Xray HTTPing defaults to gstatic 204. Cloudflare is an independent
                // fallback for routes where that origin is unavailable. A timeout is a failed
                // sample, never a synthetic 5000/9999 ms latency value.
                val targets = if (v2rayStyleDelay) {
                    REAL_DELAY_TARGETS
                } else {
                    // Every node in the same network/time epoch sees the same origin order.
                    // Profile-id rotation biased Rank when one origin was slower or censored.
                    RankTargetScheduler.ordered(
                        targets = TUNNEL_PROBE_TARGETS,
                        networkKey = intelligence?.currentSnapshot()?.key().orEmpty()
                    )
                }
                val batch = targets.firstNotNullOfOrNull { target ->
                    runCatching {
                        SocksHttpClient.tunnelRttBatch(
                            port = livePort,
                            host = target.first,
                            path = target.second,
                            samples = requested,
                            timeoutMs = timeoutMs
                        )
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
        }.getOrDefault(false)
        if (!started && failureReason.isBlank()) failureReason = "xray-start"

        val outcomes = times.map { kotlin.math.round(it).toInt().coerceIn(1, 10_000) }.toMutableList()
        repeat((requested - outcomes.size).coerceAtLeast(0)) { outcomes += -1 }
        val link = LinkQualityEstimator.summarize(outcomes)
        val ordered = times.sorted()
        val latency = when {
            ordered.isEmpty() -> 9999.0
            // MARBLE_HONEST_PING_V119 — the first-sample path bypasses summarize(), so it gets
            // the same positive/ceiling clamp before a stored benchmark can seed the Home ping.
            v2rayStyleDelay -> LinkQualityEstimator.sanitaryRtt(ordered.first())
            link != null -> link.medianRttMs.toDouble()
            else -> LinkQualityEstimator.sanitaryRtt(ordered[ordered.size / 2])
        }
        val success = if (v2rayStyleDelay) { if (times.isNotEmpty()) 100 else 0 }
            else link?.successPercent ?: (times.size * 100 / requested)
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
        if (raw.isEmpty()) return raw
        return raw.map { r ->
            if (r.success <= 0) return@map r.copy(score = -1.0)
            val q = RealtimeQualityEngine.score(r, settings.workloadProfile, settings.benchMode)
            val resilience = (q.resilience + if (r.usedFragment) 5.0 else 0.0).coerceIn(0.0, 100.0)
            r.copy(score = if (settings.workloadProfile == WorkloadProfile.STEALTH) resilience else q.selected,
                interactiveScore = q.interactive, streamingScore = q.streaming,
                stabilityScore = q.stability, resilienceScore = resilience)
        }.sortedWith(compareByDescending<BenchmarkResult> { it.score }.thenBy { it.latencyMs }.thenBy { it.jitterMs })
    }

    private fun tcpLatency(p: ProxyProfile, timeoutMs: Int, settings: AppSettings = AppSettings()): Double {
        if (p.host.isBlank() || p.port <= 0) return DEAD_LATENCY
        // Dial in the order the tunnel itself will use. `Socket.connect(host, port)` takes the first
        // answer the OS resolver happened to return, which on Android is usually the A record, so a
        // dual-stack node was measured (and therefore ranked) over IPv4 even when Marble would run it
        // over IPv6.
        val candidates = AddressFamilyPolicy.resolveCandidates(
            p.host,
            AddressFamilyPolicy.plan(settings = settings)
        )
        if (candidates.isEmpty()) return DEAD_LATENCY
        val start = System.nanoTime()
        candidates.forEachIndexed { index, address ->
            val spentMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
            val remainingMs = timeoutMs - spentMs
            val attemptMs = when {
                candidates.size == 1 -> timeoutMs
                index == candidates.lastIndex -> max(remainingMs, 250)
                // A family that is black-holed must cost a little, not the whole precheck budget.
                else -> min(max(remainingMs, 250), 1_200)
            }
            if (attemptMs <= 0) return DEAD_LATENCY
            val connected = runCatching {
                Socket().use { it.connect(InetSocketAddress(address, p.port), attemptMs) }
            }.isSuccess
            if (connected) return ((System.nanoTime() - start) / 1e6)
        }
        return DEAD_LATENCY
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
        bytesPerSecond >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f MiB/s", bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024.0 -> String.format(Locale.US, "%.0f KiB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.US, "%.0f B/s", bytesPerSecond)
    }

    private companion object {
        const val BASE_PORT = 18080
        const val BENCHMARK_PORT_SLOTS = 10_000
        const val RACE_BASE_PORT = 19280
        const val OPTIMIZER_BASE_PORT = 20580

        /**
         * MARBLE_SMART_PING_RESCUE_V123 — evidence tier of a Smart ping that proved reachability
         * but could not complete the verified tunnel phase. Deliberately *not* `TUNNEL`: this
         * result must never be recorded as tunnel intelligence.
         */
        const val SMART_PROBE_KIND = "SMART"

        /**
         * Minimum start-up budget (seconds) for the Smart ping verification phase. `measure()`
         * clamps its own sample timeout, so this only widens the window [XrayManager.temporary]
         * gives a cold child process to open its SOCKS listener.
         */
        const val SMART_VERIFY_START_SEC = 6
        // Spread nodes across independent verified endpoints. Domain targets travel as SOCKS
        // ATYP=domain (no Android resolver); literal targets remain available when proxy DNS fails.
        val TUNNEL_PROBE_TARGETS = listOf(
            "connectivitycheck.gstatic.com" to "/generate_204",
            "cp.cloudflare.com" to "/generate_204",
            "1.1.1.1" to "/cdn-cgi/trace",
            "1.0.0.1" to "/cdn-cgi/trace"
        )
        val TUNNEL_TARGET_CURSOR = AtomicInteger(0)
        // Keep v2rayNG-style Google targets, then add an independent Cloudflare 204.
        // A provider-specific reset must not classify an otherwise usable node as dead.
        val REAL_DELAY_TARGETS = listOf(
            "www.gstatic.com" to "/generate_204",
            "www.google.com" to "/generate_204",
            "cp.cloudflare.com" to "/generate_204"
        )
        const val DEAD_LATENCY = 99_999.0
    }
}
