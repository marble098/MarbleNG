package com.marbleng.app.core

import com.marbleng.app.model.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Ranks proxy candidates by real tunnel quality. Two phases, both bounded-parallel: a cheap TCP
 * precheck first prunes obviously-dead hosts, then a small worker pool tests the survivors through
 * real Xray tunnels concurrently. Each candidate reuses a single spawned process across all of its
 * samples instead of respawning per sample, so testing a whole library stays fast without
 * overloading the device.
 */
class BenchmarkEngine(private val xray: XrayManager) {

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        usePrecheck: Boolean = true,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        val s = tuned(settings)
        val candidates = selectCandidates(profiles, s, usePrecheck)
        if (candidates.isEmpty()) return emptyList()

        // Real tunnels are far heavier than a TCP probe (process spawn + full handshake), so
        // concurrency is a small fraction of cores rather than the wide TCP precheck pool.
        val liveWorkers = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 5)
            .coerceAtMost(candidates.size)
        val livePool = Executors.newFixedThreadPool(liveWorkers)
        val completed = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<BenchmarkResult>())

        val jobs = candidates.mapIndexed { idx, p ->
            livePool.submit {
                results += testCandidate(p, BASE_PORT + idx, s)
                onProgress(completed.incrementAndGet(), candidates.size, p.name)
            }
        }
        jobs.forEach { runCatching { it.get() } }
        livePool.shutdown()

        return rank(results.toList())
    }

    private fun tuned(settings: AppSettings): AppSettings = when (settings.benchMode) {
        BenchMode.RELIABLE -> settings.copy(benchCandidates = maxOf(settings.benchCandidates, 30), benchSamples = maxOf(settings.benchSamples, 5), benchTimeoutSec = maxOf(settings.benchTimeoutSec, 10), tcpWorkers = 16)
        BenchMode.BALANCED -> settings
        BenchMode.FAST -> settings.copy(benchCandidates = minOf(settings.benchCandidates, 15), benchSamples = minOf(settings.benchSamples, 3), benchTimeoutSec = minOf(settings.benchTimeoutSec, 6), tcpWorkers = 24)
        BenchMode.TURBO -> settings.copy(benchCandidates = minOf(settings.benchCandidates, 10), benchSamples = minOf(settings.benchSamples, 2), benchTimeoutSec = minOf(settings.benchTimeoutSec, 4), tcpWorkers = 28)
        BenchMode.CUSTOM -> settings
    }

    private fun selectCandidates(profiles: List<ProxyProfile>, s: AppSettings, usePrecheck: Boolean): List<ProxyProfile> {
        val maxCandidates = s.benchCandidates.coerceAtMost(profiles.size)
        if (!usePrecheck) return profiles.take(maxCandidates)

        val precheckPool = Executors.newFixedThreadPool(s.tcpWorkers.coerceIn(1, 32))
        val precheck = profiles
            .map { p -> precheckPool.submit<Pair<ProxyProfile, Double>> { p to tcpLatency(p, s.tcpPrecheckTimeoutMs) } }
            .mapNotNull { runCatching { it.get() }.getOrNull() }
        precheckPool.shutdown()

        // UDP-only transports (Hysteria2) can't be TCP-prechecked; reserve a slot for them so a
        // promising UDP node isn't starved out by TCP-only nodes that merely precheck faster.
        val udp = precheck.filter { it.first.scheme == "hysteria2" }
        val tcp = precheck.filter { it.first.scheme != "hysteria2" }.sortedBy { it.second }
        val reserve = if (udp.isEmpty()) 0 else minOf(udp.size, maxOf(2, maxCandidates / 3))
        return (tcp.take(maxCandidates - reserve) + udp.take(reserve)).map { it.first }.take(maxCandidates)
    }

    /** One real Xray process serves every sample for this candidate instead of respawning per sample. */
    private fun testCandidate(p: ProxyProfile, port: Int, s: AppSettings): BenchmarkResult {
        val times = mutableListOf<Double>()
        var speed = 0.0
        var ok = 0

        runCatching {
            xray.temporary(p, port) {
                repeat(s.benchSamples) { sample ->
                    val r = SocksHttpClient.get(port, "cp.cloudflare.com", "/generate_204", s.benchTimeoutSec * 1000, 32 * 1024)
                    if (r.status in 200..399) {
                        times += r.elapsedMs
                        ok++
                    }
                    if (sample == 0 && r.status > 0) {
                        val z = SocksHttpClient.get(port, "speed.cloudflare.com", "/__down?bytes=${s.benchBytes}", s.benchTimeoutSec * 1000 + 4000, s.benchBytes + 16384)
                        speed = max(speed, z.bytesPerSecond)
                    }
                }
            }
        }

        val success = if (s.benchSamples > 0) ok * 100 / s.benchSamples else 0
        val latency = if (times.isEmpty()) 9999.0 else times.sorted()[times.size / 2]
        val mean = if (times.isEmpty()) 9999.0 else times.average()
        val jitter = if (times.size < 2) 0.0 else sqrt(times.sumOf { (it - mean) * (it - mean) } / times.size)
        return BenchmarkResult(p.id, p.name, success, latency, jitter, speed, 0.0)
    }

    private fun rank(raw: List<BenchmarkResult>): List<BenchmarkResult> {
        if (raw.isEmpty()) return raw
        fun norm(values: List<Double>, x: Double, invert: Boolean = false): Double {
            val lo = values.min(); val hi = values.max()
            val v = if (hi - lo < 1e-9) 0.5 else (x - lo) / (hi - lo)
            return if (invert) 1 - v else v
        }
        val latencies = raw.map { minOf(it.latencyMs, 10000.0) }
        val jitters = raw.map { minOf(it.jitterMs, 10000.0) }
        val speeds = raw.map { ln(1 + max(0.0, it.bytesPerSecond)) }
        return raw.map { r ->
            val score = if (r.success <= 0) -1.0 else
                r.success * .55 +
                    norm(latencies, minOf(r.latencyMs, 10000.0), true) * 20 +
                    norm(jitters, minOf(r.jitterMs, 10000.0), true) * 10 +
                    norm(speeds, ln(1 + max(0.0, r.bytesPerSecond))) * 15
            r.copy(score = score)
        }.sortedWith(compareByDescending<BenchmarkResult> { it.score }.thenBy { it.latencyMs })
    }

    private fun tcpLatency(p: ProxyProfile, timeoutMs: Int): Double {
        if (p.host.isBlank() || p.port <= 0) return 99999.0
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(p.host, p.port), timeoutMs) }
            (System.nanoTime() - start) / 1e6
        } catch (_: Throwable) {
            99999.0
        }
    }

    private companion object {
        const val BASE_PORT = 18080
    }
}
