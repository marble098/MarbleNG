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
import kotlin.math.sqrt

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

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        usePrecheck: Boolean = true,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        if (profiles.isEmpty()) return emptyList()
        val s = tuned(settings)
        val candidates = selectCandidates(profiles, s, usePrecheck)
        if (candidates.isEmpty()) return emptyList()

        val thermal = intelligence?.thermalBudget(s) ?: 1.0
        val cpu = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val nominalWorkers = (cpu / 2).coerceIn(2, 5)
        val liveWorkers = max(1, (nominalWorkers * thermal).toInt())
            .coerceAtMost(candidates.size)

        val livePool = Executors.newFixedThreadPool(liveWorkers)
        val completed = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<BenchmarkResult>())
        val jobs = candidates.mapIndexed { idx, p ->
            livePool.submit {
                val result = testCandidate(p, BASE_PORT + idx * 4, s)
                results += result
                intelligence?.recordBenchmark(p, result, s)
                onProgress(completed.incrementAndGet(), candidates.size, p.name)
            }
        }
        jobs.forEach { runCatching { it.get() } }
        livePool.shutdown()

        val ranked = rank(results.toList(), s)
        ranked.firstOrNull()?.let { top ->
            intelligence?.setDecision(
                "${top.name} • ${top.success}% • ${top.latencyMs.toInt()} ms • " +
                    "${formatRate(top.bytesPerSecond)} • ${s.workloadProfile.name.lowercase()}"
            )
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
        onProgress: (String) -> Unit = {}
    ): Pair<ProxyProfile, BenchmarkResult>? {
        if (profiles.isEmpty()) return null
        val width = settings.raceWidth.coerceIn(2, 4).coerceAtMost(profiles.size)
        val raceSettings = tuned(settings).copy(
            benchCandidates = width,
            benchSamples = 1,
            benchTimeoutSec = min(settings.benchTimeoutSec, 5),
            benchBytes = min(settings.benchBytes, 64 * 1024),
            adaptiveMuxEnabled = false,
            adaptiveFragmentEnabled = false
        )
        val candidates = selectCandidates(profiles, raceSettings, usePrecheck = true).take(width)
        if (candidates.isEmpty()) return null

        val pool = Executors.newFixedThreadPool(candidates.size)
        val completion = ExecutorCompletionService<Pair<ProxyProfile, BenchmarkResult>>(pool)
        val futures = candidates.mapIndexed { idx, p ->
            completion.submit {
                onProgress(p.name)
                p to quickCandidate(p, RACE_BASE_PORT + idx * 3, raceSettings)
            }
        }
        var winner: Pair<ProxyProfile, BenchmarkResult>? = null
        try {
            for (ignored in candidates.indices) {
                val result = runCatching {
                    completion.poll((raceSettings.benchTimeoutSec + 4).toLong(), TimeUnit.SECONDS)?.get()
                }.getOrNull() ?: continue
                intelligence?.recordBenchmark(result.first, result.second, raceSettings)
                if (result.second.success > 0) {
                    winner = result
                    break
                }
            }
        } finally {
            futures.forEach { if (!it.isDone) it.cancel(true) }
            pool.shutdownNow()
        }
        winner?.let { (p, r) ->
            intelligence?.setDecision("Race winner ${p.name} • ${r.latencyMs.toInt()} ms")
        }
        return winner
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
        val ordered = intelligence?.orderCandidates(profiles, s) ?: profiles
        val maxCandidates = s.benchCandidates.coerceAtMost(profiles.size)
        if (!usePrecheck) return ordered.take(maxCandidates)

        val pool = Executors.newFixedThreadPool(s.tcpWorkers.coerceIn(1, 32))
        val probes = profiles.map { p ->
            pool.submit<Pair<ProxyProfile, Double>> {
                p to if (isUdpNative(p)) 0.0 else tcpLatency(p, s.tcpPrecheckTimeoutMs)
            }
        }.mapNotNull { runCatching { it.get() }.getOrNull() }
        pool.shutdown()

        val alive = probes.filter { (p, latency) -> isUdpNative(p) || latency < DEAD_LATENCY }
        if (alive.isEmpty()) return emptyList()
        val latencyById = alive.associate { it.first.id to it.second }

        // History is the primary rank. TCP latency is only a tie-breaker for unknown/equal history.
        val historicallyOrdered = alive.map { it.first }.sortedWith(
            compareByDescending<ProxyProfile> { intelligence?.predictedScore(it, s) ?: 50.0 }
                .thenBy { latencyById[it.id] ?: DEAD_LATENCY }
        )

        // Reserve a small exploration slice so new configs can earn history instead of being
        // permanently starved by established nodes.
        val exploreCount = if (maxCandidates >= 8) max(1, maxCandidates / 5) else 1
        val known = historicallyOrdered.filter {
            (intelligence?.predictedScore(it, s) ?: 50.0) != 50.0
        }.take(maxCandidates - exploreCount)
        val unknown = alive.map { it.first }
            .filterNot { p -> known.any { it.id == p.id } }
            .sortedBy { latencyById[it.id] ?: DEAD_LATENCY }
            .take(exploreCount)

        return (known + unknown + historicallyOrdered)
            .distinctBy { it.id }
            .take(maxCandidates)
    }

    private data class Measurement(
        val success: Int,
        val latency: Double,
        val jitter: Double,
        val speed: Double,
        val udpSuccess: Int
    )

    private fun testCandidate(p: ProxyProfile, port: Int, s: AppSettings): BenchmarkResult {
        val effective = intelligence?.effectiveSettings(p, s) ?: s
        var usedFragment = effective.fragmentEnabled && !s.fragmentEnabled
        var usedMux = effective.muxEnabled && !s.muxEnabled
        var measurement = measure(p, port, effective, includeThroughput = true)

        // If a TLS/REALITY path is being interfered with, test the smallest configured fragment
        // intervention before declaring it dead. Successful preference is persisted per network.
        if (
            measurement.success == 0 &&
            s.adaptiveFragmentEnabled &&
            !effective.fragmentEnabled &&
            canFragment(p)
        ) {
            val fragmentSettings = effective.copy(fragmentEnabled = true, muxEnabled = false)
            val fragmented = measure(p, port + 1, fragmentSettings, includeThroughput = true)
            if (fragmented.success > measurement.success) {
                measurement = fragmented
                usedFragment = true
                usedMux = false
            }
        }

        // Mux is never assumed to be a speed accelerator. Only probe it on stable, high-RTT TCP
        // nodes, and remember it when one real route request improves materially.
        if (
            measurement.success > 0 &&
            s.adaptiveMuxEnabled &&
            !effective.muxEnabled &&
            canMux(p) &&
            measurement.latency >= 140.0 &&
            (intelligence?.thermalBudget(s) ?: 1.0) >= 0.60
        ) {
            val muxProbe = quickMeasure(p, port + 2, effective.copy(muxEnabled = true))
            if (muxProbe.success > 0 && muxProbe.latency < measurement.latency * 0.86) {
                usedMux = true
            }
        }

        return BenchmarkResult(
            profileId = p.id,
            name = p.name,
            success = measurement.success,
            latencyMs = measurement.latency,
            jitterMs = measurement.jitter,
            bytesPerSecond = measurement.speed,
            score = 0.0,
            udpSuccess = measurement.udpSuccess,
            interactiveScore = 0.0,
            streamingScore = 0.0,
            stabilityScore = 0.0,
            resilienceScore = 0.0,
            usedFragment = usedFragment,
            usedMux = usedMux
        )
    }

    private fun measure(
        p: ProxyProfile,
        port: Int,
        s: AppSettings,
        includeThroughput: Boolean
    ): Measurement {
        val times = mutableListOf<Double>()
        var speed = 0.0
        var ok = 0
        var udpSuccess = 0

        runCatching {
            xray.temporary(p, port, s) {
                repeat(s.benchSamples.coerceIn(1, 8)) { sample ->
                    val r = SocksHttpClient.get(
                        port,
                        "cp.cloudflare.com",
                        "/generate_204",
                        s.benchTimeoutSec * 1000,
                        32 * 1024
                    )
                    if (r.status in 200..399) {
                        times += r.elapsedMs
                        ok++
                    }

                    if (includeThroughput && sample == 0 && r.status > 0) {
                        var bytes = s.benchBytes.coerceIn(64 * 1024, 4 * 1024 * 1024)
                        var z = SocksHttpClient.get(
                            port,
                            "speed.cloudflare.com",
                            "/__down?bytes=$bytes",
                            s.benchTimeoutSec * 1000 + 4_000,
                            bytes + 16_384
                        )
                        speed = max(speed, z.bytesPerSecond)

                        // Expand only when the first sample is clearly moving data and thermal
                        // budget permits it. Slow/dead nodes stop early and save quota/battery.
                        if (
                            s.adaptiveThroughputEnabled &&
                            z.status > 0 &&
                            z.bytesPerSecond > 512.0 * 1024.0 &&
                            (intelligence?.thermalBudget(s) ?: 1.0) >= 0.60
                        ) {
                            bytes = max(1024 * 1024, bytes * 4).coerceAtMost(s.adaptiveThroughputMaxBytes)
                            z = SocksHttpClient.get(
                                port,
                                "speed.cloudflare.com",
                                "/__down?bytes=$bytes",
                                s.benchTimeoutSec * 1000 + 7_000,
                                bytes + 16_384
                            )
                            speed = max(speed, z.bytesPerSecond)
                        }
                    }
                }
                if (ok > 0 && s.udpProbeEnabled) {
                    udpSuccess = if (SocksUdpProbe.stun(port, timeoutMs = min(3500, s.benchTimeoutSec * 1000)) > 0) 100 else 0
                }
            }
        }

        val sampleCount = s.benchSamples.coerceIn(1, 8)
        val success = ok * 100 / sampleCount
        val latency = if (times.isEmpty()) 9999.0 else times.sorted()[times.size / 2]
        val mean = if (times.isEmpty()) 9999.0 else times.average()
        val jitter = if (times.size < 2) 0.0 else sqrt(times.sumOf { (it - mean) * (it - mean) } / times.size)
        return Measurement(success, latency, jitter, speed, udpSuccess)
    }

    private fun quickCandidate(p: ProxyProfile, port: Int, s: AppSettings): BenchmarkResult {
        val effective = intelligence?.effectiveSettings(p, s) ?: s
        val m = quickMeasure(p, port, effective)
        return BenchmarkResult(
            p.id,
            p.name,
            m.success,
            m.latency,
            m.jitter,
            0.0,
            0.0,
            udpSuccess = 0
        )
    }

    private fun quickMeasure(p: ProxyProfile, port: Int, s: AppSettings): Measurement {
        var elapsed = 9999.0
        var ok = 0
        runCatching {
            xray.temporary(p, port, s.copy(benchSamples = 1)) {
                val r = SocksHttpClient.get(port, "cp.cloudflare.com", "/generate_204", s.benchTimeoutSec * 1000, 4096)
                if (r.status in 200..399) {
                    elapsed = r.elapsedMs
                    ok = 1
                }
            }
        }
        return Measurement(if (ok == 1) 100 else 0, elapsed, 0.0, 0.0, 0)
    }

    private fun rank(raw: List<BenchmarkResult>, settings: AppSettings): List<BenchmarkResult> {
        if (raw.isEmpty()) return raw
        return raw.map { r ->
            val latency = if (r.success <= 0) 0.0 else 100.0 * exp(-r.latencyMs.coerceAtMost(5000.0) / 240.0)
            val jitter = if (r.success <= 0) 0.0 else 100.0 * exp(-r.jitterMs.coerceAtMost(3000.0) / 95.0)
            val speed = if (r.success <= 0) 0.0 else {
                val mbps = r.bytesPerSecond * 8.0 / 1_000_000.0
                (ln(1.0 + mbps) / ln(1.0 + 100.0) * 100.0).coerceIn(0.0, 100.0)
            }
            val reliability = r.success.toDouble().coerceIn(0.0, 100.0)
            val udp = r.udpSuccess.toDouble().coerceIn(0.0, 100.0)
            // Historical preference already shaped candidate selection; final ranking remains based
            // on current-session measurements so stale history can never override fresh evidence.
            val interactive = reliability * 0.30 + latency * 0.45 + jitter * 0.25
            val streaming = reliability * 0.30 + speed * 0.55 + jitter * 0.15
            val stability = reliability * 0.55 + jitter * 0.25 + latency * 0.20
            val resilience = reliability * 0.78 + udp * 0.22

            val score = when (settings.workloadProfile) {
                WorkloadProfile.INTERACTIVE -> interactive
                WorkloadProfile.STREAMING -> streaming
                WorkloadProfile.STABILITY -> stability
                WorkloadProfile.STEALTH -> resilience
                WorkloadProfile.AUTO -> when (settings.benchMode) {
                    BenchMode.RELIABLE -> stability
                    BenchMode.FAST, BenchMode.TURBO -> interactive * 0.55 + streaming * 0.45
                    BenchMode.BALANCED, BenchMode.CUSTOM ->
                        interactive * 0.32 + streaming * 0.28 + stability * 0.30 + resilience * 0.10
                }
            }
            r.copy(
                score = if (r.success <= 0) -1.0 else score,
                interactiveScore = interactive,
                streamingScore = streaming,
                stabilityScore = stability,
                resilienceScore = resilience
            )
        }.sortedWith(compareByDescending<BenchmarkResult> { it.score }.thenBy { it.latencyMs })
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
        p.scheme.equals("hysteria2", true) || p.transport.contains("hysteria", true)

    private fun canFragment(p: ProxyProfile): Boolean =
        p.security.contains("tls", true) || p.security.contains("reality", true)

    private fun canMux(p: ProxyProfile): Boolean =
        p.scheme.lowercase() in setOf("vless", "vmess", "trojan", "ss") && !isUdpNative(p)

    private fun formatRate(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1024.0 * 1024.0 -> "%.1f MiB/s".format(bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024.0 -> "%.0f KiB/s".format(bytesPerSecond / 1024.0)
        else -> "%.0f B/s".format(bytesPerSecond)
    }

    private companion object {
        const val BASE_PORT = 18080
        const val RACE_BASE_PORT = 19280
        const val DEAD_LATENCY = 99_999.0
    }
}

