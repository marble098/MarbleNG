package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchMode
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * MARBLE_PING_ENGINE_V130 — the one accurate ping engine behind every measurement the user can
 * trigger: the Home ping, a subscription's ping entry, "Ping all" and the per-server three-dot
 * Ping. It implements the four [ProbeMethod]s with the same semantics the reference clients use:
 *
 *  - [ProbeMethod.TCP]    — v2rayNG/Exclave-style raw TCP handshake against the endpoint. The
 *    address is resolved once and every RFC 8305 candidate is tried under the active family plan,
 *    so a dual-stack node is measured on the family the tunnel itself would use.
 *  - [ProbeMethod.ICMP]   — a classic ICMP echo through `/system/bin/ping`, parsed across locales
 *    (summary `avg` preferred, per-reply `time=` as fallback). It never fabricates a value when
 *    the carrier drops ICMP.
 *  - [ProbeMethod.TUNNEL] — v2rayNG "real delay": a full HTTPS 200/204 round trip through a real
 *    temporary Xray process, Google (`www.gstatic.com`) first with independent Cloudflare and
 *    Google fallbacks. The fastest verified round trip wins, matching v2rayNG's keep-minimum rule.
 *  - [ProbeMethod.HYBRID] — "Smart ping": a fast, parallel reachability gate orders the batch so
 *    reachable endpoints surface first, then every node still gets its real tunnel measurement.
 *    The gate is ordering evidence only and NEVER rejects a profile — a fronted REALITY/WS route
 *    can be blocked at the raw endpoint yet work perfectly through Xray. This fixes the old Smart
 *    behaviour where the gate could empty the candidate list (a single working node would then
 *    read "Test failed") and where the tunnel wave silently ignored the configured worker count.
 */
class PingEngine(
    private val xray: XrayManager,
    private val intelligence: MarbleIntelligence? = null
) {

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        method: ProbeMethod,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        val scoped = profiles.distinctBy { it.id }
        if (scoped.isEmpty()) return emptyList()
        onCandidates(scoped)
        return when (method) {
            ProbeMethod.TCP ->
                directSweep(scoped, settings, icmpMode = false, onStart, onResult, onProgress)
            ProbeMethod.ICMP ->
                directSweep(scoped, settings, icmpMode = true, onStart, onResult, onProgress)
            ProbeMethod.TUNNEL ->
                tunnelSweep(scoped, settings, onStart, onResult, onProgress)
            ProbeMethod.HYBRID ->
                tunnelSweep(gateOrder(scoped, settings), settings, onStart, onResult, onProgress)
        }
    }

    // ------------------------------------------------------------------ shared batch runner

    private fun sweep(
        profiles: List<ProxyProfile>,
        workers: Int,
        onStart: (ProxyProfile) -> Unit,
        onResult: (ProxyProfile, BenchmarkResult) -> Unit,
        onProgress: (Int, Int, String) -> Unit,
        measure: (ProxyProfile) -> BenchmarkResult
    ): List<BenchmarkResult> {
        if (profiles.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(workers.coerceIn(1, profiles.size))
        val completed = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<BenchmarkResult>())
        try {
            val jobs = profiles.map { profile ->
                pool.submit {
                    onStart(profile)
                    val result = measure(profile)
                    results += result
                    onResult(profile, result)
                    onProgress(completed.incrementAndGet(), profiles.size, profile.name)
                }
            }
            jobs.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdown()
        }
        return results.toList()
    }

    // ------------------------------------------------------------------ TCP / ICMP

    private fun directSweep(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        icmpMode: Boolean,
        onStart: (ProxyProfile) -> Unit,
        onResult: (ProxyProfile, BenchmarkResult) -> Unit,
        onProgress: (Int, Int, String) -> Unit
    ): List<BenchmarkResult> {
        val samples = settings.benchSamples.coerceIn(1, 6)
        val timeoutMs = if (icmpMode) {
            (settings.benchTimeoutSec * 1000).coerceIn(500, 6_000)
        } else {
            min(settings.benchTimeoutSec * 1000, settings.tcpPrecheckTimeoutMs)
                .coerceIn(300, 6_000)
        }
        // Raw sockets spawn no process and cost almost nothing: run a genuinely wide wave.
        val workers = maxOf(settings.tcpWorkers, 24).coerceAtMost(64)
        return sweep(profiles, workers, onStart, onResult, onProgress) { profile ->
            val sample = RouteProbe.measure(profile, icmpMode, samples, timeoutMs, settings)
            scored(
                BenchmarkResult(
                    profileId = profile.id,
                    name = profile.name,
                    success = sample.successPercent,
                    latencyMs = sample.latencyMs,
                    bytesPerSecond = 0.0,
                    score = 0.0,
                    probeKind = if (icmpMode) "ICMP" else "TCP",
                    sampleCount = samples
                ),
                settings
            )
        }
    }

    // ------------------------------------------------------------------ Smart gate

    /**
     * Fast reachability gate used by Smart ping. It partitions nothing away — it only returns the
     * profiles ordered reachable-first. UDP-native protocols (hysteria2/wireguard/kcp) cannot be
     * TCP-gated and are treated as open so a fronted route is never penalised.
     */
    private fun gateOrder(profiles: List<ProxyProfile>, settings: AppSettings): List<ProxyProfile> {
        if (profiles.size <= 1) return profiles
        val timeoutMs = settings.tcpPrecheckTimeoutMs.coerceIn(200, 1_200)
        val workers = maxOf(settings.tcpWorkers, 16).coerceAtMost(64)
        val gate = ConcurrentHashMap<String, Boolean>()
        val pool = Executors.newFixedThreadPool(workers.coerceIn(1, profiles.size))
        try {
            val jobs = profiles.map { profile ->
                pool.submit {
                    gate[profile.id] = when {
                        isUdpNative(profile) -> true
                        profile.host.isBlank() || profile.port !in 1..65535 -> false
                        else -> RouteProbe.tcp(profile.host, profile.port, timeoutMs, settings) <
                            RouteProbe.UNREACHABLE
                    }
                }
            }
            jobs.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdown()
        }
        return profiles.filter { gate[it.id] == true } + profiles.filter { gate[it.id] != true }
    }

    // ------------------------------------------------------------------ real tunnel (v2rayNG real delay)

    private fun tunnelSweep(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        onStart: (ProxyProfile) -> Unit,
        onResult: (ProxyProfile, BenchmarkResult) -> Unit,
        onProgress: (Int, Int, String) -> Unit
    ): List<BenchmarkResult> {
        val probeSettings = settings.copy(
            benchMode = BenchMode.CUSTOM,
            probeSpeedTest = false,
            verifiedPerformanceTuning = false,
            udpProbeEnabled = false
        )
        val samples = settings.benchSamples.coerceIn(1, 4)
        val timeoutMs = (settings.benchTimeoutSec * 1000).coerceIn(2_000, 8_000)
        // Each node owns a temporary Xray child, so the pool is bounded — but it is far wider than
        // the old rank path's 2–4 workers, which is what made Smart/Tunnel ping crawl on big lists.
        val workers = maxOf(settings.tcpWorkers, 8).coerceAtMost(16)
        val networkKey = intelligence?.currentSnapshot()?.key()
        return sweep(profiles, workers, onStart, onResult, onProgress) { profile ->
            val result = scored(realDelay(profile, samples, timeoutMs, probeSettings), settings)
            // Endpoint-only evidence never poisons tunnel intelligence; a real tunnel measurement
            // is allowed to, exactly as ranking does, and only while the network did not change.
            if (networkKey == null || intelligence?.currentSnapshot()?.key() == networkKey) {
                intelligence?.recordBenchmark(profile, result, settings)
            }
            result
        }
    }

    private fun realDelay(
        profile: ProxyProfile,
        samples: Int,
        timeoutMs: Int,
        settings: AppSettings
    ): BenchmarkResult {
        var latency = 9_999.0
        var success = 0
        var successes = 0
        var failureReason = "xray-start"
        runCatching {
            xray.temporary(profile, preferredPort(profile), settings, delayTest = false) { livePort ->
                for (target in REAL_DELAY_TARGETS) {
                    val batch = runCatching {
                        SocksHttpClient.tunnelRttBatch(
                            port = livePort,
                            host = target.first,
                            path = target.second,
                            samples = samples,
                            timeoutMs = timeoutMs
                        )
                    }.onFailure { error ->
                        failureReason = "https:${error::class.java.simpleName}:${error.message.orEmpty()}"
                    }.getOrNull() ?: continue
                    val verified = batch.samplesMs.filter { it.isFinite() && it > 0.0 }
                    if (verified.isNotEmpty()) {
                        // v2rayNG real-delay semantics: the fastest verified round trip wins.
                        latency = verified.minOrNull() ?: latency
                        successes = verified.size
                        success = 100
                        failureReason = ""
                        break
                    }
                }
            }
        }.getOrDefault(false)
        return BenchmarkResult(
            profileId = profile.id,
            name = profile.name,
            success = success,
            latencyMs = latency,
            bytesPerSecond = 0.0,
            score = 0.0,
            probeKind = "TUNNEL",
            sampleCount = successes,
            failureReason = failureReason.take(180)
        )
    }

    private fun scored(result: BenchmarkResult, settings: AppSettings): BenchmarkResult {
        if (result.success <= 0) return result.copy(score = -1.0)
        val q = RealtimeQualityEngine.score(result, settings.workloadProfile, settings.benchMode)
        return result.copy(
            score = q.selected,
            interactiveScore = q.interactive,
            streamingScore = q.streaming,
            stabilityScore = q.stability,
            resilienceScore = q.resilience
        )
    }

    private fun isUdpNative(p: ProxyProfile): Boolean =
        p.scheme.equals("hysteria2", true) || p.scheme.equals("wireguard", true) ||
            p.transport.contains("hysteria", true) || p.transport.equals("mkcp", true) ||
            p.transport.equals("kcp", true)

    private fun preferredPort(profile: ProxyProfile): Int {
        val slot = (profile.id.hashCode() and 0x7fffffff) % PORT_SLOTS
        return BASE_PORT + slot
    }

    private companion object {
        const val BASE_PORT = 18_080
        const val PORT_SLOTS = 20_000

        // v2rayNG-style real-delay targets: Google first, then independent Google/Cloudflare 204s.
        val REAL_DELAY_TARGETS = listOf(
            "www.gstatic.com" to "/generate_204",
            "www.google.com" to "/generate_204",
            "cp.cloudflare.com" to "/generate_204"
        )
    }
}
