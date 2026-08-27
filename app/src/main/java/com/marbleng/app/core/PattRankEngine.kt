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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp

/**
 * PattNG-style Rank for MarbleNG.
 *
 * PattNG's fast path does not spawn an Xray CLI child + localhost SOCKS listener for every node.
 * It creates Xray cores in-process and calls core.Dial for the HTTPS delay request. MarbleNG keeps
 * its existing CLI Xray for VPN runtime, while a tiny Go helper hosts the whole Rank batch in ONE
 * Android process and creates the per-node Xray core instances inside it.
 */
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

    private val helper: File
        get() = File(context.applicationInfo.nativeLibraryDir, "libmarblerank.so")

    fun run(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        onCandidates: (List<ProxyProfile>) -> Unit = {},
        onStart: (ProxyProfile) -> Unit = {},
        onResult: (ProxyProfile, BenchmarkResult) -> Unit = { _, _ -> },
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): List<BenchmarkResult> {
        val scoped = profiles.distinctBy { it.id }
        if (scoped.isEmpty()) return emptyList()
        onCandidates(scoped)

        val profileById = scoped.associateBy { it.id }
        val nativeProfiles = scoped.filterNot { it.scheme.equals("ssh", true) }
        val legacyProfiles = scoped.filter { it.scheme.equals("ssh", true) }.toMutableList()
        val results = ConcurrentHashMap<String, BenchmarkResult>()
        val completed = AtomicInteger(0)

        fun publish(profile: ProxyProfile, result: BenchmarkResult) {
            // A fallback path must never publish the same card twice.
            if (results.putIfAbsent(profile.id, result) != null) return
            onResult(profile, result)
            val done = completed.incrementAndGet()
            onProgress(done, scoped.size, profile.name)
        }

        if (nativeProfiles.isNotEmpty() && helper.isFile) {
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
                    .coerceIn(4, 16)
                    .coerceAtMost(jobs.length())

                val timeoutMs = (settings.benchTimeoutSec * 1000)
                    .coerceIn(4_000, 6_000)

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

                    val process = ProcessBuilder(helper.absolutePath, input.absolutePath)
                        .redirectErrorStream(true)
                        .apply {
                            environment()["XRAY_LOCATION_ASSET"] =
                                File(context.filesDir, "xray-assets").absolutePath
                        }
                        .start()

                    val reader = Thread({
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                if (!line.startsWith(PREFIX)) return@forEach
                                val event = runCatching {
                                    JSONObject(line.removePrefix(PREFIX))
                                }.getOrNull() ?: return@forEach

                                val id = event.optString("id")
                                val profile = profileById[id] ?: return@forEach
                                when (event.optString("event")) {
                                    "start" -> onStart(profile)
                                    "result" -> {
                                        val ok = event.optBoolean("ok", false)
                                        val latency = if (ok) {
                                            event.optDouble("latencyMs", 9_999.0)
                                        } else 9_999.0
                                        val result = BenchmarkResult(
                                            profileId = profile.id,
                                            name = profile.name,
                                            success = if (ok) 100 else 0,
                                            latencyMs = latency,
                                            bytesPerSecond = 0.0,
                                            score = nativeScore(ok, latency),
                                            probeKind = "TUNNEL",
                                            jitterMs = event.optDouble("jitterMs", 0.0),
                                            warmupMs = event.optDouble("warmupMs", 0.0),
                                            sampleCount = event.optInt("samples", 0),
                                            failureReason = event.optString("error").take(180)
                                        )
                                        publish(profile, result)
                                    }
                                }
                            }
                        }
                    }, "marble-rank-reader")
                    reader.isDaemon = true
                    reader.start()

                    val waves = (jobs.length() + workers - 1) / workers
                    val maxWaitMs = (
                        (timeoutMs.toLong() * 2L + 3_000L) * waves.coerceAtLeast(1) +
                            3_000L
                        ).coerceAtMost(180_000L)

                    if (!process.waitFor(maxWaitMs, TimeUnit.MILLISECONDS)) {
                        process.destroy()
                        if (!process.waitFor(800L, TimeUnit.MILLISECONDS)) {
                            process.destroyForcibly()
                        }
                    }
                    reader.join(2_000L)
                } catch (_: Throwable) {
                    nativeProfiles
                        .filter { it.id !in results }
                        .forEach { if (it !in legacyProfiles) legacyProfiles += it }
                } finally {
                    runCatching { input.delete() }
                }

                nativeProfiles
                    .filter { it.id !in results && it !in legacyProfiles }
                    .forEach { profile ->
                        publish(
                            profile,
                            BenchmarkResult(
                                profileId = profile.id,
                                name = profile.name,
                                success = 0,
                                latencyMs = 9_999.0,
                                bytesPerSecond = 0.0,
                                score = -1.0,
                                probeKind = "TUNNEL",
                                failureReason = "native-rank-helper-no-result"
                            )
                        )
                    }
            }
        } else if (nativeProfiles.isNotEmpty()) {
            legacyProfiles += nativeProfiles
        }

        // SSH needs Marble's Java SSH bridge. Keep the old isolated-Xray path only there
        // (and as an emergency helper-packaging fallback), never as normal multi-node Rank.
        if (legacyProfiles.isNotEmpty()) {
            val pendingLegacy = legacyProfiles.distinctBy { it.id }.filter { it.id !in results }
            if (pendingLegacy.isNotEmpty()) {
                val legacySettings = settings.copy(
                    benchMode = BenchMode.CUSTOM,
                    benchCandidates = pendingLegacy.size,
                    benchSamples = 1,
                    benchTimeoutSec = settings.benchTimeoutSec.coerceIn(4, 6),
                    tcpWorkers = 2,
                    probeMethod = ProbeMethod.TUNNEL,
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
                failureReason = "rank-no-result"
            )
        }

        // Persistence is deliberately outside the native reader fast path.
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
