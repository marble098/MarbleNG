package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchMode
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.WorkloadProfile
import java.util.Locale
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
    val tcpFastOpen: Boolean = false,
    val tcpMaxSeg: Int = 0,
    val latencyMs: Double = 0.0,
    val bytesPerSecond: Double = 0.0,
    val gainPercent: Double = 0.0,
    val at: Long = 0L
) {
    /** True when the method changes nothing, i.e. the node was already configured optimally. */
    val neutral: Boolean
        get() = !fragment && !mux && dnsQueryStrategy.isBlank() && !tcpFastOpen && tcpMaxSeg <= 0

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
        if (dnsQueryStrategy.isNotBlank()) next = next.copy(dnsQueryStrategy = dnsQueryStrategy)
        if (tcpFastOpen) next = next.copy(tcpFastOpenEnabled = true)
        if (tcpMaxSeg > 0) next = next.copy(tcpMaxSeg = tcpMaxSeg)
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
        .put("tcpFastOpen", tcpFastOpen)
        .put("tcpMaxSeg", tcpMaxSeg)
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
            tcpFastOpen = o.optBoolean("tcpFastOpen"),
            tcpMaxSeg = o.optInt("tcpMaxSeg"),
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
    val score: Double,
    val jitterMs: Double = 0.0,
    val p95LatencyMs: Double = 0.0,
    val p95JitterMs: Double = 0.0,
    val lossPercent: Double = 0.0,
    val spikePercent: Double = 0.0
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
// MARBLE_FAST_STRATEGY_RACE_V14
// MARBLE_EXTREME_NETWORK_V30
// MARBLE_REALTIME_ENGINE_V70
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

        /*
         * MARBLE_TUNING_MEASUREMENT_PLANE_V134 — a trial is not "one HTTPS GET".
         *
         * `runTrial` probes `cp.cloudflare.com`, a *domain*, through a throwaway Xray core whose own
         * encrypted DNS is routed through the tunnel it is measuring. One trial therefore costs a
         * core start, a DoH lookup (three tunnel round trips) and the GET (four more). Two constants
         * ignored that:
         *
         *  - the throwaway core was hardened with `LinkEvidence.UNKNOWN`, so it kept the legacy
         *    1350/1650 ms resolver budgets that V133 proved cannot survive a slow route. On the
         *    267–444 ms link in the attached runtime log every trial died inside DNS;
         *  - the pass budget (`connectTuningBudgetSec`, 4–12 s) was set independently of the trial
         *    budget (`min(benchTimeoutSec,4) × 1000`), so a pass could be too short to contain even
         *    one trial. That is the Home-ping `responders=1` defect, one layer down.
         *
         * A pass that measures nothing reports an unhealthy verdict, and an unhealthy verdict is what
         * escalated the Turbo backoff to 1800 s. Both budgets now come from the same measured
         * evidence that sizes the live tunnel, with the legacy constants kept as floors so an
         * unmeasured link behaves exactly as it did before.
         */
        val link = intelligence.linkEvidenceFor(profile.id)
        val legacyTrialMs = (min(base.benchTimeoutSec, 4).coerceAtLeast(2)) * 1_000L
        val trialTimeoutMs = LinkDeadlinePolicy.tuningTrialTimeoutMs(link, floorMs = legacyTrialMs)
            .toInt()
            .coerceIn(1_000, 30_000)
        val passBudgetMs = LinkDeadlinePolicy.tuningPassBudgetMs(
            trialTimeoutMs = trialTimeoutMs.toLong(),
            requestedMs = budgetMs.coerceIn(3_000L, 15_000L)
        )

        // Stage 1: broad + shallow. Race every compatible strategy with one real HTTPS sample.
        val deadline = System.currentTimeMillis() + passBudgetMs
        val methods = methodsFor(profile, base, thermal)
        if (methods.size < 2) return null
        val fastTimeoutMs = trialTimeoutMs
        val parallelism = when {
            thermal >= 0.82 -> min(4, methods.size)
            thermal >= 0.62 -> min(3, methods.size)
            else -> min(2, methods.size)
        }
        val fastTrials = Collections.synchronizedList(mutableListOf<TuningTrial>())
        val pool = Executors.newFixedThreadPool(parallelism)
        val jobs = methods.mapIndexed { index, plan ->
            pool.submit {
                if (System.currentTimeMillis() < deadline) {
                    onProgress("Quick race • ${plan.label}")
                    fastTrials += runTrial(profile, plan, base, TUNE_BASE_PORT + index * PORT_STRIDE, 1, 0, fastTimeoutMs, link)
                }
            }
        }
        pool.shutdown()
        val left = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
        runCatching { pool.awaitTermination(left + TRIAL_GRACE_MS, TimeUnit.MILLISECONDS) }
        jobs.forEach { if (!it.isDone) it.cancel(true) }
        pool.shutdownNow()

        var measured = fastTrials.toList()
        val baselineFast = measured.firstOrNull { it.methodId == AccelerationPlan.DIRECT } ?: return null
        val fastScored = measured.map { it.copy(score = score(it, false, base.workloadProfile)) }
        val fastBest = fastScored.filter { it.success > 0 }.maxByOrNull { it.score }

        // Stage 2: confirm only baseline + strongest challenger. Speed is optional and tiny.
        if (fastBest != null && System.currentTimeMillis() + MIN_REFINE_MS < deadline) {
            val winnerPlan = methods.firstOrNull { it.methodId == fastBest.methodId }
            val refinePlans = listOfNotNull(
                methods.firstOrNull { it.methodId == AccelerationPlan.DIRECT },
                winnerPlan?.takeIf { it.methodId != AccelerationPlan.DIRECT }
            ).distinctBy { it.methodId }
            refinePlans.forEachIndexed { index, plan ->
                if (System.currentTimeMillis() + MIN_REFINE_MS >= deadline) return@forEachIndexed
                onProgress("Confirm • ${plan.label}")
                val refined = runTrial(profile, plan, base, REFINE_BASE_PORT + index * PORT_STRIDE, 2, if (measureSpeed) REFINE_SPEED_BYTES else 0, fastTimeoutMs, link)
                measured = measured.filterNot { it.methodId == refined.methodId } + refined
            }
        }

        val speedSeen = measured.any { it.bytesPerSecond > 0.0 }
        val scored = measured.map { it.copy(score = score(it, speedSeen, base.workloadProfile)) }
        val baselineScored = scored.firstOrNull { it.methodId == AccelerationPlan.DIRECT }
            ?: baselineFast.copy(score = score(baselineFast, false, base.workloadProfile))
        val best = scored.filter { it.success > 0 }.maxByOrNull { it.score }
        val current = intelligence.acceleration(profile.id)
        if (best == null) {
            return TuningReport(profile.id, networkKey, AccelerationPlan(at=System.currentTimeMillis()), baselineScored.score, 0.0, 0.0, scored,
                "Marble Turbo • ${profile.name} • no strategy carried traffic", current != null && !current.neutral)
        }
        val keepBaseline = best.methodId == AccelerationPlan.DIRECT || !materialGain(best, baselineScored)
        val chosenTrial = if (keepBaseline) baselineScored else best
        val chosenPlan = methods.first { it.methodId == chosenTrial.methodId }
        val gain = gainPercent(chosenTrial, baselineScored)
        val winner = chosenPlan.copy(latencyMs=chosenTrial.latencyMs, bytesPerSecond=chosenTrial.bytesPerSecond, gainPercent=gain, at=System.currentTimeMillis())
        val changed = (current?.methodId ?: AccelerationPlan.DIRECT) != winner.methodId
        return TuningReport(profile.id, networkKey, winner, baselineScored.score, chosenTrial.score, gain, scored,
            summarize(profile, winner, chosenTrial, baselineScored, gain, scored.size), changed)
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
        val nativeMultiplexTransport = profile.transport.lowercase() in setOf(
            "grpc", "xhttp", "splithttp", "http"
        )
        val muxEligible =
            profile.scheme.lowercase() in MUX_SCHEMES &&
                !udpNative &&
                !nativeMultiplexTransport
        val highRtt = (health?.latencyEwma ?: 0.0) >= 150.0
        val struggling = (health?.failureStreak ?: 0) > 0 || (health?.successEwma ?: 100.0) < 70.0

        val fragmentMethods = if (tlsLike && !udpNative) {
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
                ),
                AccelerationPlan(
                    methodId = "fragment-sni",
                    label = "SNI tlshello length 6",
                    fragment = true,
                    fragmentPackets = "tlshello",
                    fragmentLength = "6",
                    fragmentInterval = "0"
                ),
                AccelerationPlan(
                    methodId = "fragment-iran-max",
                    label = "Iran max slice",
                    fragment = true,
                    fragmentPackets = "1-3",
                    fragmentLength = "5-15",
                    fragmentInterval = "15-30"
                )
            ).filterNot { plan ->
                base.fragmentEnabled &&
                    plan.fragmentPackets == base.fragmentPackets &&
                    plan.fragmentLength == base.fragmentLength &&
                    plan.fragmentInterval == base.fragmentInterval
            }
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
        val dnsMethods = if (
            snapshot.hasIpv4 &&
            snapshot.hasIpv6 &&
            base.dnsQueryStrategy == "UseIP"
        ) {
            val v4 = AccelerationPlan(
                methodId = "dns-v4",
                label = "IPv4-only endpoint resolution",
                dnsQueryStrategy = "UseIPv4"
            )
            val v6 = AccelerationPlan(
                methodId = "dns-v6",
                label = "IPv6-only endpoint resolution",
                dnsQueryStrategy = "UseIPv6"
            )
            // Both candidates are measured through real Xray instances. This is family racing by
            // evidence, not a hard-coded assumption that every dual-stack network prefers IPv4.
            if (base.preferIpv6) listOf(v6, v4) else listOf(v4, v6)
        } else {
            emptyList()
        }

        val tcpEligible = !udpNative && profile.transport.lowercase() !in setOf("mkcp", "kcp", "hysteria", "hysteria2")
        val tfoMethods = if (tcpEligible && base.adaptiveTcpFastOpenEnabled && !base.tcpFastOpenEnabled) {
            listOf(AccelerationPlan(methodId = "tcp-fast-open", label = "TCP Fast Open", tcpFastOpen = true))
        } else emptyList()
        val mssMethods = if (tcpEligible && base.adaptiveMssEnabled && base.tcpMaxSeg <= 0) {
            val pathMtu = intelligence.learnedPathMtu(profile.id).takeIf { it in 1280..9000 } ?: intelligence.adaptiveMtu(profile, base)
            val overhead = if (snapshot.hasIpv6) 60 else 40
            val primary = (pathMtu - overhead).coerceIn(1160, 1460)
            listOf(
                AccelerationPlan(methodId = "mss-learned", label = "PMTU-aware MSS $primary", tcpMaxSeg = primary),
                AccelerationPlan(methodId = "mss-conservative", label = "Conservative MSS ${(primary - 80).coerceAtLeast(1160)}", tcpMaxSeg = (primary - 80).coerceAtLeast(1160))
            ).distinctBy { it.tcpMaxSeg }
        } else emptyList()

        val comboMethods = buildList {
            val fragment = fragmentMethods.firstOrNull()
            val mux = muxMethods.lastOrNull()
            // A combo inherits whichever family the tuner ranked first; hard-coding "-dns-v4" here is
            // how an IPv6-preferred network silently got tested and tuned back onto IPv4.
            val dns = dnsMethods.firstOrNull()
            if (fragment != null && dns != null) add(fragment.copy(methodId="fragment-${dns.methodId}", label="TLS fragmentation + ${dns.label.lowercase()}", dnsQueryStrategy=dns.dnsQueryStrategy))
            if (mux != null && dns != null) add(mux.copy(methodId="mux-${dns.methodId}", label="Light Mux + ${dns.label.lowercase()}", dnsQueryStrategy=dns.dnsQueryStrategy))
            if (fragment != null && mux != null) add(fragment.copy(methodId="fragment-mux-light", label="TLS fragmentation + light Mux", mux=true, muxConcurrency=4, muxXudpConcurrency=8))
            val tfo = tfoMethods.firstOrNull(); val mss = mssMethods.firstOrNull()
            if (tfo != null && mss != null) add(tfo.copy(methodId = "tfo-mss", label = "TCP Fast Open + PMTU MSS", tcpMaxSeg = mss.tcpMaxSeg))
        }

        val ordered = when {
            struggling -> fragmentMethods + mssMethods + tfoMethods + dnsMethods + muxMethods + comboMethods
            highRtt -> tfoMethods + mssMethods + muxMethods + dnsMethods + fragmentMethods + comboMethods
            else -> tfoMethods + mssMethods + dnsMethods + muxMethods + fragmentMethods + comboMethods
        }

        val budget = base.connectTuningMethods.coerceIn(1, 8)
        val thermalCap = when {
            thermal >= 0.80 -> budget
            thermal >= 0.60 -> min(budget, 6)
            else -> min(budget, 3)
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
        probeTimeoutMs: Int,
        link: LinkEvidence = LinkEvidence.UNKNOWN
    ): TuningTrial {
        val settings = plan.applyTo(base).copy(benchSamples = samples, benchTimeoutSec = (probeTimeoutMs / 1_000).coerceAtLeast(3))
        val outcomes = mutableListOf<Int>(); var bytesPerSecond = 0.0
        runCatching {
            xray.temporary(profile, port, settings, link = link) { livePort ->
                repeat(samples) {
                    val p = SocksHttpClient.get(livePort, LATENCY_HOST, LATENCY_PATH, probeTimeoutMs, 32 * 1024)
                    outcomes += if (p.status in 200..399) kotlin.math.round(p.elapsedMs).toInt().coerceIn(1, 10_000) else -1
                }
                if (outcomes.any { it > 0 } && speedBytes > 0) {
                    val d = SocksHttpClient.get(livePort, SPEED_HOST, "/__down?bytes=$speedBytes", probeTimeoutMs + 4_000, speedBytes + 16_384)
                    if (d.status in 200..299) bytesPerSecond = d.bytesPerSecond
                }
            }
        }
        val q = LinkQualityEstimator.summarize(outcomes)
        return TuningTrial(plan.methodId, plan.label, q?.successPercent ?: 0,
            q?.medianRttMs?.toDouble() ?: DEAD_LATENCY, bytesPerSecond, 0.0,
            (q?.ewmaJitterMs ?: -1).takeIf { it >= 0 }?.toDouble() ?: 0.0,
            q?.p95RttMs?.toDouble() ?: 0.0,
            (q?.p95IpdvMs ?: -1).takeIf { it >= 0 }?.toDouble() ?: 0.0,
            q?.lossPercent?.toDouble() ?: 100.0, q?.spikePercent?.toDouble() ?: 0.0)
    }

    private fun score(
        trial: TuningTrial,
        speedSeen: Boolean,
        workload: WorkloadProfile
    ): Double {
        if (trial.success <= 0) return 0.0
        return RealtimeQualityEngine.score(
            RealtimeEvidence(trial.success.toDouble(), trial.latencyMs,
                trial.p95LatencyMs.takeIf { it > 0 } ?: trial.latencyMs,
                trial.jitterMs, trial.p95JitterMs.takeIf { it > 0 } ?: trial.jitterMs,
                trial.lossPercent, trial.spikePercent,
                if (speedSeen) trial.bytesPerSecond else 0.0, samples = if (trial.jitterMs > 0) 2 else 1),
            workload, BenchMode.BALANCED).selected
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
        val speedSafe = baseline.bytesPerSecond <= 0.0 || candidate.bytesPerSecond >= baseline.bytesPerSecond * 0.85
        val latencySafe = candidate.latencyMs <= baseline.latencyMs * 1.12
        val jitterSafe = baseline.jitterMs <= 0.0 || candidate.jitterMs <= 0.0 || candidate.jitterMs <= baseline.jitterMs * 1.35 + 4.0
        val tailSafe = baseline.p95LatencyMs <= 0.0 || candidate.p95LatencyMs <= 0.0 || candidate.p95LatencyMs <= baseline.p95LatencyMs * 1.18 + 10.0
        val jitterGain = if (baseline.jitterMs > 0.0 && candidate.jitterMs >= 0.0) (baseline.jitterMs - candidate.jitterMs) / baseline.jitterMs else 0.0
        return (latencyGain >= 0.10 && speedSafe && jitterSafe && tailSafe) ||
            (speedGain >= 0.22 && latencySafe && jitterSafe) ||
            (jitterGain >= 0.20 && latencySafe && tailSafe) || candidate.success >= baseline.success + 25
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
        bytesPerSecond >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.1f MiB/s", bytesPerSecond / 1024.0 / 1024.0)
        bytesPerSecond >= 1024.0 -> String.format(Locale.US, "%.0f KiB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.US, "%.0f B/s", bytesPerSecond)
    }

    private companion object {
        const val TUNE_BASE_PORT = 21_580
        const val REFINE_BASE_PORT = 21_780
        const val PORT_STRIDE = 2
        const val MIN_TRIAL_MS = 1_500L
        const val MIN_REFINE_MS = 2_200L
        const val TRIAL_GRACE_MS = 1_500L
        const val SPEED_PROBE_BYTES = 192 * 1024
        const val REFINE_SPEED_BYTES = 96 * 1024
        const val DEAD_LATENCY = 9_999.0
        const val LATENCY_HOST = "cp.cloudflare.com"
        const val LATENCY_PATH = "/generate_204"
        const val SPEED_HOST = "speed.cloudflare.com"
        val MUX_SCHEMES = setOf("vless", "vmess", "trojan", "ss")
    }
}
