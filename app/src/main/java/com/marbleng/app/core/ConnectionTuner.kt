package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.WorkloadProfile
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * One acceleration method, expressed as a delta on top of the user's own configuration.
 *
 * A plan never disables something the user (or Iran Mode) asked for: every method only *adds*
 * transport behaviour, so an accelerated route is still the route the user configured.
 */
data class AccelerationPlan(
    val methodId: String = DIRECT,
    val label: String = "Direct baseline",
    val fragment: Boolean = false,
    val fragmentPackets: String = "",
    val fragmentLength: String = "",
    val fragmentInterval: String = "",
    val mux: Boolean = false,
    val muxConcurrency: Int = 0,
    val muxXudpConcurrency: Int = 0,
    val dnsQueryStrategy: String = "",
    val latencyMs: Double = 0.0,
    val bytesPerSecond: Double = 0.0,
    val gainPercent: Double = 0.0,
    val at: Long = 0L
) {
    /** True when the method changes nothing, i.e. the node was already configured optimally. */
    val neutral: Boolean
        get() = !fragment && !mux && dnsQueryStrategy.isBlank()

    fun applyTo(base: AppSettings): AppSettings {
        var next = base
        if (fragment) {
            next = next.copy(
                fragmentEnabled = true,
                fragmentPackets = fragmentPackets.ifBlank { base.fragmentPackets },
                fragmentLength = fragmentLength.ifBlank { base.fragmentLength },
                fragmentInterval = fragmentInterval.ifBlank { base.fragmentInterval }
            )
        }
        if (mux) {
            next = next.copy(
                muxEnabled = true,
                muxConcurrency = muxConcurrency.takeIf { it > 0 } ?: base.muxConcurrency,
                muxXudpConcurrency = muxXudpConcurrency.takeIf { it > 0 } ?: base.muxXudpConcurrency
            )
        }
        if (dnsQueryStrategy.isNotBlank()) {
            next = next.copy(dnsQueryStrategy = dnsQueryStrategy)
        }
        return next
    }

    fun toJson(): JSONObject = JSONObject()
        .put("methodId", methodId)
        .put("label", label)
        .put("fragment", fragment)
        .put("fragmentPackets", fragmentPackets)
        .put("fragmentLength", fragmentLength)
        .put("fragmentInterval", fragmentInterval)
        .put("mux", mux)
        .put("muxConcurrency", muxConcurrency)
        .put("muxXudpConcurrency", muxXudpConcurrency)
        .put("dnsQueryStrategy", dnsQueryStrategy)
        .put("latencyMs", latencyMs)
        .put("bytesPerSecond", bytesPerSecond)
        .put("gainPercent", gainPercent)
        .put("at", at)

    companion object {
        const val DIRECT = "direct"

        fun fromJson(o: JSONObject) = AccelerationPlan(
            methodId = o.optString("methodId", DIRECT),
            label = o.optString("label", "Direct baseline"),
            fragment = o.optBoolean("fragment"),
            fragmentPackets = o.optString("fragmentPackets"),
            fragmentLength = o.optString("fragmentLength"),
            fragmentInterval = o.optString("fragmentInterval"),
            mux = o.optBoolean("mux"),
            muxConcurrency = o.optInt("muxConcurrency"),
            muxXudpConcurrency = o.optInt("muxXudpConcurrency"),
            dnsQueryStrategy = o.optString("dnsQueryStrategy"),
            latencyMs = o.optDouble("latencyMs", 0.0),
            bytesPerSecond = o.optDouble("bytesPerSecond", 0.0),
            gainPercent = o.optDouble("gainPercent", 0.0),
            at = o.optLong("at")
        )
    }
}

/** Result of executing one method against the selected node through a real Xray instance. */
data class TuningTrial(
    val methodId: String,
    val label: String,
    val success: Int,
    val latencyMs: Double,
    val bytesPerSecond: Double,
    val score: Double
)

data class TuningReport(
    val profileId: String,
    val networkKey: String,
    val winner: AccelerationPlan,
    val baselineScore: Double,
    val winnerScore: Double,
    val gainPercent: Double,
    val trials: List<TuningTrial>,
    val summary: String,
    /** True when the winner differs from the plan already applied to this node. */
    val changed: Boolean
) {
    val healthy: Boolean get() = trials.any { it.success > 0 }
}

/**
 * Marble Turbo — the measured acceleration stage.
 *
 * Where [MarbleIntelligence] predicts from history and [ContinuousRouteOptimizer] compares *other*
 * nodes, this class improves the node the user actually selected. It executes a bounded set of
 * transport methods (TLS fragmentation shapes, Mux connection reuse, DNS address family) against
 * that single node through throwaway Xray instances, measures real HTTPS latency and real
 * throughput for each, and keeps the winner only when it beats the untouched baseline by a
 * material margin. Because the exit node never changes, acceleration is safe to run with
 * Identity Guard pinned.
 */
class ConnectionTuner(
    private val xray: XrayManager,
    private val intelligence: MarbleIntelligence
) {
    /**
     * Executes acceleration methods for one profile and returns the measured winner.
     *
     * @param base settings *without* any previously learned acceleration, so every pass starts
     *   from the user's own configuration and re-proves its own conclusion.
     * @param budgetMs hard wall-clock budget. The baseline is always measured first, so an
     *   exhausted budget degrades to "keep what the user configured", never to a guess.
     * @param measureSpeed adds a bounded download to each trial. Off for the connect path (ping
     *   first, connect fast), on for background passes where speed evidence is the point.
     */
    fun accelerate(
        profile: ProxyProfile,
        base: AppSettings,
        budgetMs: Long,
        measureSpeed: Boolean,
        onProgress: (String) -> Unit = {}
    ): TuningReport? {
        val networkKey = intelligence.currentSnapshot().key()
        val thermal = intelligence.thermalBudget(base)
        if (thermal < 0.40) return null

        val deadline = System.currentTimeMillis() + budgetMs.coerceIn(2_000L, 60_000L)
        val methods = methodsFor(profile, base, thermal)
        if (methods.size < 2) return null

        val probeTimeoutMs = (min(base.benchTimeoutSec, 6).coerceAtLeast(3)) * 1_000
        val samples = if (measureSpeed) 3 else 2
        val speedBytes = if (measureSpeed) SPEED_PROBE_BYTES else 0

        // A throughput trial must own the link while it runs, otherwise parallel probes measure
        // each other. Latency-only passes are cheap enough to overlap two at a time.
        val parallelism = if (measureSpeed || thermal < 0.65) 1 else 2

        val trials = Collections.synchronizedList(mutableListOf<TuningTrial>())
        val baseline = runTrial(profile, methods.first(), base, TUNE_BASE_PORT, samples, speedBytes, probeTimeoutMs)
        trials += baseline
        onProgress(methods.first().label)

        if (baseline.success <= 0 && !measureSpeed) {
            // The untouched node is not carrying traffic at all. Fragmentation is the method that
            // rescues that case, so keep going even though the baseline gave us no reference.
            onProgress("baseline blocked • trying countermeasures")
        }

        val challengers = methods.drop(1)
        if (challengers.isNotEmpty() && System.currentTimeMillis() + MIN_TRIAL_MS < deadline) {
            val pool = Executors.newFixedThreadPool(min(parallelism, challengers.size))
            val jobs = challengers.mapIndexed { index, plan ->
                pool.submit {
                    // Every method still has to fit inside the caller's budget.
                    if (System.currentTimeMillis() + MIN_TRIAL_MS < deadline) {
                        onProgress(plan.label)
                        trials += runTrial(
                            profile,
                            plan,
                            base,
                            TUNE_BASE_PORT + (index + 1) * PORT_STRIDE,
                            samples,
                            speedBytes,
                            probeTimeoutMs
                        )
                    }
                }
            }
            val leftMs = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
            pool.shutdown()
            runCatching { pool.awaitTermination(leftMs + TRIAL_GRACE_MS, TimeUnit.MILLISECONDS) }
            jobs.forEach { if (!it.isDone) it.cancel(true) }
            pool.shutdownNow()
        }

        val measured = trials.toList()
        val speedSeen = measured.any { it.bytesPerSecond > 0.0 }
        val scored = measured.map { it.copy(score = score(it, speedSeen, base.workloadProfile)) }
        val baselineScored = scored.firstOrNull { it.methodId == AccelerationPlan.DIRECT }
            ?: return null
        val best = scored.filter { it.success > 0 }.maxByOrNull { it.score }

        val current = intelligence.acceleration(profile.id)
        if (best == null) {
            return TuningReport(
                profileId = profile.id,
                networkKey = networkKey,
                winner = AccelerationPlan(at = System.currentTimeMillis()),
                baselineScore = baselineScored.score,
                winnerScore = 0.0,
                gainPercent = 0.0,
                trials = scored,
                summary = "Marble Turbo • ${profile.name} • no method carried traffic",
                changed = current != null && !current.neutral
            )
        }

        val keepBaseline = best.methodId == AccelerationPlan.DIRECT ||
            !materialGain(best, baselineScored)
        val chosenTrial = if (keepBaseline) baselineScored else best
        val chosenPlan = methods.first { it.methodId == chosenTrial.methodId }
        val gain = gainPercent(chosenTrial, baselineScored)

        val winner = chosenPlan.copy(
            latencyMs = chosenTrial.latencyMs,
            bytesPerSecond = chosenTrial.bytesPerSecond,
            gainPercent = gain,
            at = System.currentTimeMillis()
        )

        val changed = (current?.methodId ?: AccelerationPlan.DIRECT) != winner.methodId
        return TuningReport(
            profileId = profile.id,
            networkKey = networkKey,
            winner = winner,
            baselineScore = baselineScored.score,
            winnerScore = chosenTrial.score,
            gainPercent = gain,
            trials = scored,
            summary = summarize(profile, winner, chosenTrial, baselineScored, gain, scored.size),
            changed = changed
        )
    }

    /**
     * True when this node deserves a measured pass before it starts carrying traffic: nothing fresh
     * has been proven for it on this physical network yet, or its last evidence says it struggles.
     */
    fun shouldPreTune(profile: ProxyProfile, settings: AppSettings): Boolean {
        if (!settings.connectTuningEnabled) return false
        if (settings.connectTuningBudgetSec <= 0) return false
        if (intelligence.thermalBudget(settings) < 0.50) return false
        if (intelligence.acceleration(profile.id) != null) return false
        return true
    }

    /**
     * The methods that are physically meaningful for this node, most promising first.
     * The first entry is always the untouched baseline.
     */
    fun methodsFor(
        profile: ProxyProfile,
        base: AppSettings,
        thermal: Double = 1.0
    ): List<AccelerationPlan> {
        val snapshot = intelligence.currentSnapshot()
        val health = intelligence.healthOf(profile.id)
        val tlsLike = profile.security.contains("tls", true) ||
            profile.security.contains("reality", true)
        val udpNative = profile.scheme.equals("hysteria2", true) ||
            profile.scheme.equals("hysteria", true) ||
            profile.transport.contains("hysteria", true)
        val muxEligible = profile.scheme.lowercase() in MUX_SCHEMES && !udpNative
        val highRtt = (health?.latencyEwma ?: 0.0) >= 150.0
        val struggling = (health?.failureStreak ?: 0) > 0 || (health?.successEwma ?: 100.0) < 70.0

        val fragmentMethods = if (tlsLike && !udpNative && !base.fragmentEnabled) {
            listOf(
                AccelerationPlan(
                    methodId = "fragment-tlshello",
                    label = "TLS hello fragmentation",
                    fragment = true,
                    fragmentPackets = "tlshello",
                    fragmentLength = "100-200",
                    fragmentInterval = "10-20"
                ),
                AccelerationPlan(
                    methodId = "fragment-short",
                    label = "Short-packet fragmentation",
                    fragment = true,
                    fragmentPackets = "1-3",
                    fragmentLength = "40-90",
                    fragmentInterval = "5-10"
                )
            )
        } else {
            emptyList()
        }

        val muxMethods = if (muxEligible && !base.muxEnabled) {
            listOf(
                AccelerationPlan(
                    methodId = "mux-reuse",
                    label = "Mux connection reuse",
                    mux = true,
                    muxConcurrency = 8,
                    muxXudpConcurrency = 16
                ),
                AccelerationPlan(
                    methodId = "mux-light",
                    label = "Light Mux (low head-of-line)",
                    mux = true,
                    muxConcurrency = 4,
                    muxXudpConcurrency = 8
                )
            )
        } else {
            emptyList()
        }

        // Forcing one address family removes a dead AAAA/A dial from every new connection, which
        // is pure connect-latency on links that advertise both but only route one.
        val dnsMethods = if (snapshot.hasIpv4 && snapshot.hasIpv6 && base.dnsQueryStrategy == "UseIP") {
            listOf(
                AccelerationPlan(
                    methodId = "dns-v4",
                    label = "IPv4-first endpoint resolution",
                    dnsQueryStrategy = "UseIPv4"
                )
            )
        } else {
            emptyList()
        }

        val ordered = when {
            struggling -> fragmentMethods + muxMethods + dnsMethods
            highRtt -> muxMethods + fragmentMethods + dnsMethods
            else -> fragmentMethods.take(1) + muxMethods + dnsMethods + fragmentMethods.drop(1)
        }

        val budget = base.connectTuningMethods.coerceIn(1, 5)
        val thermalCap = when {
            thermal >= 0.80 -> budget
            thermal >= 0.60 -> min(budget, 3)
            else -> min(budget, 2)
        }
        return listOf(AccelerationPlan(at = System.currentTimeMillis())) +
            ordered.distinctBy { it.methodId }.take(thermalCap)
    }

    private fun runTrial(
        profile: ProxyProfile,
        plan: AccelerationPlan,
        base: AppSettings,
        port: Int,
        samples: Int,
        speedBytes: Int,
        probeTimeoutMs: Int
    ): TuningTrial {
        val settings = plan.applyTo(base).copy(
            benchSamples = samples,
            benchTimeoutSec = (probeTimeoutMs / 1_000).coerceAtLeast(3)
        )
        val times = mutableListOf<Double>()
        var ok = 0
        var bytesPerSecond = 0.0

        runCatching {
            xray.temporary(profile, port, settings) { livePort ->
                repeat(samples) {
                    val probe = SocksHttpClient.get(
                        livePort,
                        LATENCY_HOST,
                        LATENCY_PATH,
                        probeTimeoutMs,
                        32 * 1024
                    )
                    if (probe.status in 200..399) {
                        ok++
                        times += probe.elapsedMs
                    }
                }
                if (ok > 0 && speedBytes > 0) {
                    val download = SocksHttpClient.get(
                        livePort,
                        SPEED_HOST,
                        "/__down?bytes=$speedBytes",
                        probeTimeoutMs + 4_000,
                        speedBytes + 16_384
                    )
                    if (download.status in 200..299) bytesPerSecond = download.bytesPerSecond
                }
            }
        }

        return TuningTrial(
            methodId = plan.methodId,
            label = plan.label,
            success = ok * 100 / samples.coerceAtLeast(1),
            latencyMs = if (times.isEmpty()) DEAD_LATENCY else times.sorted()[times.size / 2],
            bytesPerSecond = bytesPerSecond,
            score = 0.0
        )
    }

    private fun score(
        trial: TuningTrial,
        speedSeen: Boolean,
        workload: WorkloadProfile
    ): Double {
        if (trial.success <= 0) return 0.0
        val latencyScore = 100.0 * exp(-trial.latencyMs.coerceAtLeast(1.0) / 420.0)
        val speedScore = if (trial.bytesPerSecond <= 0.0) {
            0.0
        } else {
            (100.0 * ln(1.0 + trial.bytesPerSecond / (128.0 * 1024.0)) / ln(65.0)).coerceIn(0.0, 100.0)
        }
        val latencyWeight = if (!speedSeen) 1.0 else when (workload) {
            WorkloadProfile.INTERACTIVE -> 0.80
            WorkloadProfile.STREAMING -> 0.30
            WorkloadProfile.STABILITY -> 0.55
            WorkloadProfile.STEALTH -> 0.65
            WorkloadProfile.AUTO -> 0.60
        }
        val composite = latencyScore * latencyWeight + speedScore * (1.0 - latencyWeight)
        return composite * (trial.success / 100.0)
    }

    /**
     * Hysteresis. A method has to win clearly on the axis it claims to improve, and it may never
     * pay for that win with a large regression on the other axis.
     */
    private fun materialGain(candidate: TuningTrial, baseline: TuningTrial): Boolean {
        if (candidate.success <= 0) return false
        if (baseline.success <= 0) return true
        if (candidate.success + 20 < baseline.success) return false

        val latencyGain = (baseline.latencyMs - candidate.latencyMs) / max(1.0, baseline.latencyMs)
        val speedGain = when {
            baseline.bytesPerSecond > 64.0 * 1024.0 ->
                (candidate.bytesPerSecond - baseline.bytesPerSecond) / baseline.bytesPerSecond
            candidate.bytesPerSecond > baseline.bytesPerSecond + 256.0 * 1024.0 -> 1.0
            else -> 0.0
        }
        val speedSafe = baseline.bytesPerSecond <= 0.0 ||
            candidate.bytesPerSecond >= baseline.bytesPerSecond * 0.85
        val latencySafe = candidate.latencyMs <= baseline.latencyMs * 1.12

        return (latencyGain >= 0.12 && speedSafe) ||
            (speedGain >= 0.22 && latencySafe) ||
            candidate.success >= baseline.success + 25
    }

    private fun gainPercent(chosen: TuningTrial, baseline: TuningTrial): Double {
        if (chosen.methodId == baseline.methodId) return 0.0
        if (baseline.score <= 0.0) return if (chosen.score > 0.0) 100.0 else 0.0
        return ((chosen.score - baseline.score) / baseline.score * 100.0).coerceIn(-100.0, 400.0)
    }

    private fun summarize(
        profile: ProxyProfile,
        winner: AccelerationPlan,
        chosen: TuningTrial,
        baseline: TuningTrial,
        gain: Double,
        methods: Int
    ): String {
        val ping = "${chosen.latencyMs.toInt()} ms"
        val speed = chosen.bytesPerSecond.takeIf { it > 0.0 }?.let { " • ${formatRate(it)}" }.orEmpty()
        return if (winner.neutral) {
            "Marble Turbo • ${profile.name} • baseline already optimal • $ping$speed • $methods methods tested"
        } else {
            "Marble Turbo • ${profile.name} • ${winner.label} • $ping$speed • " +
                "+${gain.toInt()}% over ${baseline.latencyMs.toInt()} ms baseline"
        }
    }

    private fun formatRate(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1024.0 * 1024.0 -> "%.1f MiB/s".format(bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024.0 -> "%.0f KiB/s".format(bytesPerSecond / 1024.0)
        else -> "%.0f B/s".format(bytesPerSecond)
    }

    private companion object {
        const val TUNE_BASE_PORT = 21_580
        const val PORT_STRIDE = 2
        const val MIN_TRIAL_MS = 1_500L
        const val TRIAL_GRACE_MS = 4_000L
        const val SPEED_PROBE_BYTES = 192 * 1024
        const val DEAD_LATENCY = 9_999.0
        const val LATENCY_HOST = "cp.cloudflare.com"
        const val LATENCY_PATH = "/generate_204"
        const val SPEED_HOST = "speed.cloudflare.com"
        val MUX_SCHEMES = setOf("vless", "vmess", "trojan", "ss")
    }
}
