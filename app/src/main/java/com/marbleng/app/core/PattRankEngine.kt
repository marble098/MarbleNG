package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchMode
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp

class PattRankEngine(
    private val context: Context,
    private val xray: XrayManager,
    private val intelligence: MarbleIntelligence? = null
) {
    companion object {
        private const val PREFIX = "MARBLE_RANK "
        private const val PRIMARY_URL = "https://www.gstatic.com/generate_204"
        private const val FALLBACK_URL = "https://cp.cloudflare.com/generate_204"
    }

    private val xrayBinary: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libxray.so")

    private val diagnostics = RuntimeDiagnostics(context)

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        shouldStop: () -> Boolean = { false },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        val scoped = profiles.distinctBy { it.id }
        if (scoped.isEmpty()) return emptyList()
        if (settings.coreEngine() == CoreEngine.SINGBOX) {
            return BenchmarkEngine(xray, intelligence).run(scoped, settings.copy(
                probeMethod = ProbeMethod.REAL_DELAY, verifiedPerformanceTuning = false,
                probeSpeedTest = false, udpProbeEnabled = false
            ), usePrecheck = false, onCandidates = onCandidates, onStart = onStart,
                onResult = onResult, onProgress = onProgress, shouldStop = shouldStop)
        }
        onCandidates(scoped)

        val profileById = scoped.associateBy { it.id }
        val nativeProfiles = scoped.filterNot { it.scheme.equals("ssh", true) }
        val legacyProfiles = scoped.filter { it.scheme.equals("ssh", true) }.toMutableList()
        val results = ConcurrentHashMap<String, BenchmarkResult>()
        val nativeFailureReasons = ConcurrentHashMap<String, String>()
        val integratedFailure = AtomicReference("")
        val completed = AtomicInteger(0)

        fun publish(profile: ProxyProfile, result: BenchmarkResult) {
            if (results.putIfAbsent(profile.id, result) != null) return
            onResult(profile, result)
            val done = completed.incrementAndGet()
            onProgress(done, scoped.size, profile.name)
        }

        if (nativeProfiles.isNotEmpty() && xrayBinary.isFile) {
            val jobs = JSONArray()
            nativeProfiles.forEach { profile ->
                val config = runCatching {
                    XrayConfigHardener.hardenForNativeRank(profile.configJson, settings)
                }.getOrNull()

                if (config.isNullOrBlank()) {
                    legacyProfiles += profile
                } else {
                    jobs.put(
                        JSONObject()
                            .put("id", profile.id)
                            .put("config", config)
                    )
                }
            }

            if (jobs.length() > 0) {
                val workers = settings.tcpWorkers
                    .coerceIn(1, 4)
                    .coerceAtMost(jobs.length())

                val timeoutMs = (settings.benchTimeoutSec * 1000)
                    .coerceIn(4_000, 10_000)

                val input = File.createTempFile("marble-rank-", ".json", context.cacheDir)
                try {
                    input.writeText(
                        JSONObject()
                            .put("workers", workers)
                            .put("timeoutMs", timeoutMs)
                            .put("primaryUrl", PRIMARY_URL)
                            .put("fallbackUrl", FALLBACK_URL)
                            .put("jobs", jobs)
                            .toString()
                    )

                    val protocolSeen = AtomicBoolean(false)
                    val doneSeen = AtomicBoolean(false)
                    val nativeStarts = AtomicInteger(0)
                    val nativeEvents = AtomicInteger(0)
                    val lastNoise = AtomicReference("")

                    diagnostics.event(
                        "BENCHMARK",
                        "rank-integrated-start",
                        "jobs" to jobs.length(),
                        "workers" to workers,
                        "timeoutMs" to timeoutMs,
                        "binaryBytes" to xrayBinary.length()
                    )

                    val process = ProcessBuilder(
                        xrayBinary.absolutePath,
                        "marble-rank",
                        input.absolutePath
                    )
                        .redirectErrorStream(true)
                        .apply {
                            environment()["XRAY_LOCATION_ASSET"] =
                                File(context.filesDir, "xray-assets").absolutePath
                        }
                        .start()

                    val readerDone = CountDownLatch(1)
                    val reader = Thread({
                        try {
                            process.inputStream.bufferedReader().useLines { lines ->
                                lines.forEach { line ->

                                    try {
                                        if (!line.startsWith(PREFIX)) {
                                            if (line.isNotBlank()) lastNoise.set(line.take(220))
                                            return@forEach
                                        }

                                        val event = runCatching {
                                            JSONObject(line.removePrefix(PREFIX))
                                        }.getOrNull() ?: return@forEach

                                        when (event.optString("event")) {
                                            "batch" -> {
                                                protocolSeen.set(true)
                                                diagnostics.event(
                                                    "BENCHMARK",
                                                    "rank-integrated-ready",
                                                    "jobs" to event.optInt("jobs", jobs.length()),
                                                    "workers" to event.optInt("workers", workers)
                                                )
                                            }

                                            "done" -> {
                                                protocolSeen.set(true)
                                                doneSeen.set(true)
                                            }

                                            "fatal" -> {
                                                protocolSeen.set(true)
                                                integratedFailure.set(
                                                    event.optString("error")
                                                        .take(180)
                                                        .ifBlank { "integrated-rank-fatal" }
                                                )
                                            }

                                            "start" -> {
                                                protocolSeen.set(true)
                                                val profile = profileById[event.optString("id")]
                                                    ?: return@forEach
                                                nativeStarts.incrementAndGet()
                                                onStart(profile)
                                            }

                                            "result" -> {
                                                protocolSeen.set(true)
                                                val profile = profileById[event.optString("id")]
                                                    ?: return@forEach
                                                nativeEvents.incrementAndGet()

                                                val ok = event.optBoolean("ok", false)
                                                if (!ok) {
                                                    nativeFailureReasons[profile.id] =
                                                        event.optString("error")
                                                            .take(180)
                                                            .ifBlank { "native-probe-failed" }
                                                    return@forEach
                                                }

                                                val latency = event.optDouble("latencyMs", 9_999.0)
                                                val samples = event.optInt("samples", 0)
                                                val attempts = event.optInt("attempts", 0)
                                                val rawResult = BenchmarkResult(
                                                    profileId = profile.id, name = profile.name,
                                                    success = (100.0 - event.optDouble("lossPercent", 0.0)).toInt().coerceIn(1, 100),
                                                    latencyMs = latency, bytesPerSecond = 0.0, score = 0.0, probeKind = "TUNNEL",
                                                    jitterMs = event.optDouble("jitterMs", 0.0), warmupMs = event.optDouble("warmupMs", 0.0),
                                                    sampleCount = samples,
                                                    p90LatencyMs = event.optDouble("p90LatencyMs", latency),
                                                    p95LatencyMs = event.optDouble("p95LatencyMs", latency),
                                                    medianJitterMs = event.optDouble("medianJitterMs", 0.0),
                                                    p95JitterMs = event.optDouble("p95JitterMs", 0.0),
                                                    madLatencyMs = event.optDouble("madLatencyMs", 0.0),
                                                    lossPercent = event.optDouble("lossPercent", 0.0),
                                                    spikePercent = event.optDouble("spikePercent", 0.0), failureReason = "",
                                                    tcpHandshakeSuccessRatio = event.optDouble(
                                                        "handshakeSuccessRatio",
                                                        if (attempts > 0) samples.toDouble() / attempts else 0.0
                                                    ),
                                                    handshakeAttempts = attempts)
                                                val quality = RealtimeQualityEngine.score(rawResult, settings.workloadProfile, settings.benchMode)
                                                publish(profile, rawResult.copy(score = quality.selected,
                                                    interactiveScore = quality.interactive, streamingScore = quality.streaming,
                                                    stabilityScore = quality.stability, resilienceScore = quality.resilience))
                                            }
                                        }
                                    } catch (eventError: Throwable) {
                                        diagnostics.event(
                                            "BENCHMARK",
                                            "rank-reader-event-error",
                                            "error" to (eventError.message.orEmpty().take(160).ifBlank {
                                                eventError::class.java.simpleName
                                            })
                                        )
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            val expectedShutdown = t is InterruptedIOException || t is IOException
                            lastNoise.set(
                                if (expectedShutdown) {
                                    "reader-closed"
                                } else {
                                    "${t::class.java.simpleName}:${t.message.orEmpty().take(160)}"
                                }
                            )
                            diagnostics.event(
                                "BENCHMARK",
                                "rank-reader-closed",
                                "expected" to expectedShutdown,
                                "error" to lastNoise.get().take(180)
                            )
                        } finally {
                            readerDone.countDown()
                        }
                    }, "marble-rank-reader")
                    reader.isDaemon = true
                    reader.start()

                    val waves = (jobs.length() + workers - 1) / workers
                    val maxWaitMs = (
                        (timeoutMs.toLong() * 2L + 3_000L) * waves.coerceAtLeast(1) +
                            3_000L
                        ).coerceAtMost(180_000L)

                    var timedOut = false
                    val exited = try {
                        process.waitFor(maxWaitMs, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        false
                    }
                    if (!exited) {
                        timedOut = true
                        integratedFailure.compareAndSet("", "integrated-rank-timeout")
                        process.destroy()
                        try {
                            if (!process.waitFor(800L, TimeUnit.MILLISECONDS)) {
                                process.destroyForcibly()
                            }
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            runCatching { process.destroyForcibly() }
                        }
                    }

                    reader.interrupt()
                    try {
                        if (!readerDone.await(2_000L, TimeUnit.MILLISECONDS)) {
                            runCatching { process.inputStream.close() }
                            reader.interrupt()
                            if (!readerDone.await(1_000L, TimeUnit.MILLISECONDS)) {
                                lastNoise.set("reader-stuck")
                            }
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        runCatching { process.inputStream.close() }
                    }

                    val exitCode = if (process.isAlive) {
                        -999
                    } else {
                        runCatching { process.exitValue() }.getOrDefault(-998)
                    }

                    if (!protocolSeen.get()) {
                        integratedFailure.compareAndSet("", "rank-protocol-handshake-missing")
                    }
                    if (exitCode != 0 && exitCode != -999) {
                        integratedFailure.compareAndSet("", "rank-process-exit-$exitCode")
                    }
                    if (!doneSeen.get() && !timedOut && protocolSeen.get()) {
                        integratedFailure.compareAndSet("", "rank-protocol-done-missing")
                    }

                    diagnostics.event(
                        "BENCHMARK",
                        "rank-integrated-exit",
                        "exit" to exitCode,
                        "protocol" to protocolSeen.get(),
                        "done" to doneSeen.get(),
                        "starts" to nativeStarts.get(),
                        "events" to nativeEvents.get(),
                        "success" to results.size,
                        "noise" to lastNoise.get().take(180),
                        "failure" to integratedFailure.get().take(180)
                    )
                } catch (t: Throwable) {
                    integratedFailure.compareAndSet(
                        "",
                        "${t::class.java.simpleName}:${t.message.orEmpty().take(150)}"
                    )
                    diagnostics.event(
                        "BENCHMARK",
                        "rank-integrated-exception",
                        "error" to integratedFailure.get()
                    )
                } finally {
                    runCatching { input.delete() }
                }
            }
        } else if (nativeProfiles.isNotEmpty()) {
            legacyProfiles += nativeProfiles
        }

        if (legacyProfiles.isNotEmpty()) {
            val pendingLegacy = legacyProfiles.distinctBy { it.id }.filter { !results.containsKey(it.id) }
            if (pendingLegacy.isNotEmpty()) {
                val legacySettings = settings.copy(
                    benchMode = BenchMode.CUSTOM,
                    benchCandidates = pendingLegacy.size,
                    benchSamples = 1,
                    benchTimeoutSec = settings.benchTimeoutSec.coerceIn(4, 6),
                    tcpWorkers = pendingLegacy.size.coerceIn(1, 4),

                    probeMethod = ProbeMethod.REAL_DELAY,
                    probeSpeedTest = false,
                    verifiedPerformanceTuning = false,
                    udpProbeEnabled = false
                )
                BenchmarkEngine(xray, intelligence).run(
                    pendingLegacy,
                    legacySettings,
                    usePrecheck = false,
                    v2rayStyleDelay = true,
                    onCandidates = { },
                    onStart = onStart,
                    onResult = { profile, result -> publish(profile, result) },
                    onProgress = { _, _, _ -> }
                )
            }
        }

        val ordered = scoped.map { profile ->
            results[profile.id] ?: BenchmarkResult(
                profileId = profile.id,
                name = profile.name,
                success = 0,
                latencyMs = 9_999.0,
                bytesPerSecond = 0.0,
                score = -1.0,
                probeKind = "TUNNEL",
                failureReason = nativeFailureReasons[profile.id]
                    ?: integratedFailure.get().ifBlank { "rank-no-result" }
            )
        }

        diagnostics.event(
            "BENCHMARK",
            "rank-final",
            "requested" to scoped.size,
            "healthy" to ordered.count { it.success > 0 },
            "failed" to ordered.count { it.success <= 0 },
            "integratedFailure" to integratedFailure.get().take(160)
        )

        ordered.forEach { result ->
            profileById[result.profileId]?.let { profile ->
                intelligence?.recordBenchmark(profile, result, settings)
            }
        }

        return ordered.sortedWith(
            compareBy<BenchmarkResult> { if (it.success > 0) 0 else 1 }
                .thenByDescending { it.score }
                .thenBy { it.latencyMs }
        )
    }

    private fun nativeScore(ok: Boolean, latencyMs: Double): Double {
        if (!ok || !latencyMs.isFinite() || latencyMs <= 0.0) return -1.0
        val latency = 100.0 * exp(-latencyMs.coerceAtMost(5_000.0) / 240.0)
        return (45.0 + latency * 0.55).coerceIn(0.0, 100.0)
    }
}
