package com.marbleng.app.jitter

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MARBLE_SMART_JITTER_V238 — the runtime half of the Smart Jitter Reducer.
 *
 * The controller owns one coroutine on a **dedicated background dispatcher** (never Main), a
 * warm [KeepaliveHttpsProbe] channel and one [JitterWindow]. Everything that touches Android or
 * the repository is injected as the [Host] interface, so the loop itself stays host-JVM testable
 * and the service can run it for the live session while the UI reads the [state] flow.
 *
 * Guarantees:
 *  - **The data plane is untouched.** The controller only runs its own probes; it never changes
 *    TUN buffers, sockets or core configuration while packets are moving.
 *  - **Cancel-safe.** [close] cancels the scope and closes the probe channel; every loop body
 *    checks `isActive`, so a disconnect cannot leave a half-finished switch behind.
 *  - **Switches are rare and explainable.** Every accepted or rejected switch attempt lands in
 *    the bounded [SmartJitterController.State.switchLog] with its reason (from → to → why).
 *  - **Failure falls back.** If the probe channel dies, the loop records losses and keeps
 *    publishing; a controller that cannot measure never asks for a switch (its score is `null`,
 *    [SwitchPolicy] holds) — the session then behaves exactly like the module being OFF.
 */
class SmartJitterController(
    baseDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "marble-jitter").apply { isDaemon = true }
    }.asCoroutineDispatcher()
) {
    /** One switchable route as the controller sees it. */
    data class Candidate(
        val id: String,
        val name: String,
        val score: Double?,
        val muxCapable: Boolean = false
    )

    data class SwitchLogEntry(
        val atMs: Long,
        val fromId: String,
        val fromName: String,
        val toId: String,
        val toName: String,
        val reason: String,
        val applied: Boolean
    )

    data class PowerStatus(
        val powerSave: Boolean = false,
        val doze: Boolean = false
    ) {
        val multiplier: Double
            get() = when {
                doze -> 3.0
                powerSave -> 2.0
                else -> 1.0
            }
    }

    data class State(
        val mode: JitterMode = JitterMode.OFF,
        val running: Boolean = false,
        val targetName: String = "",
        val jitterMs: Double = 0.0,
        val rttMeanMs: Double = 0.0,
        val lossPercent: Double = 0.0,
        val currentScore: Double? = null,
        val samples: Int = 0,
        val validSamples: Int = 0,
        val nextProbeInMs: Long = 0L,
        val calmStreak: Int = 0,
        val powerMultiplier: Double = 1.0,
        val series: List<Double> = emptyList(),
        val switchLog: List<SwitchLogEntry> = emptyList(),
        val lastSwitchAtMs: Long = 0L,
        val probeFailures: Int = 0
    )

    /** Collaboration surface the host (the repository / the VPN service) provides. */
    interface Host {
        /** The local SOCKS port of the live tunnel, or 0 when no tunnel is live. */
        fun socksPort(): Int

        /** The display name of the live route. */
        fun liveRouteName(): String

        /** The live route's id, empty when unknown. */
        fun liveRouteId(): String

        /** When the live route became active (wall clock), 0 when unknown. */
        fun liveRouteSinceMs(): Long

        /** Candidate routes worth scoring, best-effort; may be empty. */
        fun candidates(): List<Candidate>

        /** Power state for the cadence (battery saver / Doze). */
        fun powerStatus(): PowerStatus

        /** Applies a switch to [candidateId]; returns whether it was accepted. */
        fun applySwitch(candidateId: String): Boolean

        /** Diagnostic tap for the host's own log. */
        fun onEvent(event: String, detail: String) {}
    }

    data class Tuning(
        val mode: JitterMode = JitterMode.OFF,
        val baseIntervalMs: Long = 5_000L,
        val switchMinImproveFraction: Double = SwitchPolicy.DEFAULT_MIN_IMPROVE_FRACTION,
        val switchCooldownMs: Long = SwitchPolicy.DEFAULT_COOLDOWN_MS,
        val minSamples: Int = JitterWindow.DEFAULT_MIN_SAMPLES,
        val jitterTriggerMs: Double = DEFAULT_JITTER_TRIGGER_MS,
        val probeHost: String = SmartJitterDefaults.PROBE_HOST,
        val burstSamples: Int = SmartJitterDefaults.BURST_SAMPLES,
        val burstGapMs: Long = SmartJitterDefaults.BURST_GAP_MS
    ) {
        companion object {
            const val DEFAULT_JITTER_TRIGGER_MS = 25.0
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + baseDispatcher)
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    @Volatile private var host: Host? = null
    @Volatile private var tuning = Tuning()
    @Volatile private var loop: Job? = null
    @Volatile private var wake: CompletableDeferred<Unit>? = null
    private var probeChannel: KeepaliveHttpsProbe? = null
    private val started = AtomicBoolean(false)

    /**
     * MARBLE_SMART_JITTER_REALTIME_V262 — an optional listener invoked with every published
     * [State] (from [publish], i.e. after every probe tick, every tuning change and close). The
     * repository attaches one so the home hero and the other live surfaces read the reducer's
     * jitter in real time instead of only the slower route-probe figure.
     */
    @Volatile var onState: ((State) -> Unit)? = null

    fun start(host: Host, tuning: Tuning) {
        this.host = host
        this.tuning = tuning
        publish { it.copy(mode = tuning.mode, running = true, targetName = host.liveRouteName()) }
        if (!started.compareAndSet(false, true)) return
        loop = scope.launch { runLoop() }
    }

    fun updateTuning(tuning: Tuning) {
        this.tuning = tuning
        publish { it.copy(mode = tuning.mode) }
        // MARBLE_SMART_JITTER_V238 — the loop exits itself when the mode reads OFF, so turning
        // the module back on (or tuning while it was off) must relaunch it. Without this, the
        // first OFF→active transition slept forever: `start()` is guarded by [started] and only
        // the service's session-start path calls it.
        val currentLoop = loop
        if (tuning.mode != JitterMode.OFF && (currentLoop == null || !currentLoop.isActive)) {
            loop = scope.launch { runLoop() }
        }
        wakeUp()
    }

    /** A network change invalidates the path: drop the window, reset the channel, re-probe now. */
    suspend fun onNetworkChanged() {
        mutex.withLock {
            probeChannel?.close()
            probeChannel = null
        }
        publish {
            it.copy(
                series = emptyList(),
                samples = 0,
                validSamples = 0,
                currentScore = null,
                calmStreak = 0
            )
        }
        wakeUp()
    }

    fun onNetworkChangedAsync() {
        scope.launch { onNetworkChanged() }
    }

    private fun wakeUp() {
        val current = synchronized(this) {
            val existing = wake
            wake = CompletableDeferred()
            existing
        }
        current?.complete(Unit)
    }

    private suspend fun waitForNextProbe(delayMs: Long) {
        val current = synchronized(this) {
            val existing = wake ?: CompletableDeferred<Unit>().also { wake = it }
            existing
        }
        withTimeoutOrNull(delayMs) { current.await() }
    }

    private suspend fun runLoop() {
        var calmStreak = 0
        var probeFailures = 0
        while (scope.isActive) {
            val currentHost = host ?: break
            val currentTuning = tuning
            if (currentTuning.mode == JitterMode.OFF) {
                publish { it.copy(running = false) }
                return
            }
            val port = currentHost.socksPort()
            if (port <= 0) {
                publish { it.copy(running = false, nextProbeInMs = 0L) }
                delay(2_000L)
                continue
            }
            publish { it.copy(running = true, targetName = currentHost.liveRouteName()) }

            // MARBLE_SMART_JITTER_REALTIME_V262 — one tick now takes a small burst of warm
            // keep-alive requests on the same connection instead of a single sample. A lone RTT
            // per tick feeds the RFC 3550 EWMA exactly one delta per interval, so the jitter
            // figure lagged the route by a whole probe interval; a burst of [Tuning.burstSamples]
            // samples a few tens of milliseconds apart gives that many consecutive in-path
            // deltas per tick, which is what lets the home hero's jitter settle fast and true.
            val burst = currentTuning.burstSamples.coerceIn(1, MAX_BURST_SAMPLES)
            var lastRttMs = 0.0
            var lastCold = false
            var tickSuccesses = 0
            val snapshot = mutex.withLock {
                val channel = channel(port, currentTuning.probeHost)
                repeat(burst) { index ->
                    val (rttMs, cold) = channel.probe()
                    lastRttMs = rttMs
                    lastCold = cold
                    // A cold sample (fresh connection) still counts as an attempt but stays out
                    // of the statistics — the cold-start rule, applied to our own probe.
                    val ok = rttMs > 0.0
                    if (ok) tickSuccesses++
                    channel.add(rttMs = rttMs, ok = ok, cold = cold)
                    if (index < burst - 1) delay(currentTuning.burstGapMs.coerceAtLeast(0L))
                }
                channel.summarize(currentTuning.minSamples)
            }
            if (lastRttMs > 0.0) probeFailures = 0 else probeFailures++
            val power = currentHost.powerStatus()
            val triggerMs = currentTuning.jitterTriggerMs *
                JitterModeProfile.of(currentTuning.mode).triggerScale
            val calm = snapshot != null &&
                ProbeScheduler.isCalm(snapshot.jitterMs, snapshot.lossPercent, triggerMs)
            calmStreak = ProbeScheduler.nextStreak(calm, calmStreak)
            val nextDelayMs = ProbeScheduler.nextDelayMs(
                baseIntervalMs = currentTuning.baseIntervalMs,
                calmStreak = calmStreak,
                mode = currentTuning.mode,
                powerMultiplier = power.multiplier
            )

            val score = snapshot?.let {
                ScoreEngine.score(
                    ScoreEngine.PathSample(
                        jitterMs = it.jitterMs,
                        p95RttMs = it.p95RttMs,
                        rttMeanMs = it.rttMeanMs,
                        lossPercent = it.lossPercent,
                        validSamples = it.validSamples
                    ),
                    minSamples = currentTuning.minSamples
                )
            }
            publish { previous ->
                previous.copy(
                    jitterMs = snapshot?.jitterMs ?: previous.jitterMs,
                    rttMeanMs = snapshot?.rttMeanMs ?: previous.rttMeanMs,
                    lossPercent = snapshot?.lossPercent ?: previous.lossPercent,
                    currentScore = score,
                    samples = snapshot?.samples ?: previous.samples,
                    validSamples = snapshot?.validSamples ?: previous.validSamples,
                    series = snapshot?.series?.takeLast(SERIES_MAX) ?: previous.series,
                    nextProbeInMs = nextDelayMs,
                    calmStreak = calmStreak,
                    powerMultiplier = power.multiplier,
                    probeFailures = probeFailures
                )
            }
            currentHost.onEvent(
                "probe",
                if (lastRttMs > 0.0) {
                    "rtt=${lastRttMs.toInt()}ms cold=$lastCold burst=$tickSuccesses/$burst calm=$calmStreak"
                } else {
                    "loss burst=$tickSuccesses/$burst"
                }
            )

            considerSwitch(score, currentTuning)

            waitForNextProbe(nextDelayMs)
        }
    }

    /** The probe channel for the live SOCKS port; rebuilt (cold) whenever port or host change. */
    private fun channel(port: Int, probeHost: String): KeepaliveHttpsProbe {
        val existing = probeChannel
        if (existing != null && existing.matches(port, probeHost)) return existing
        existing?.close()
        return KeepaliveHttpsProbe(
            socksPort = port,
            host = probeHost,
            path = KeepaliveHttpsProbe.DEFAULT_PATH,
            // MARBLE_SMART_JITTER_REALTIME_V262 — the tunnel still dials the configured target
            // (often a literal anycast IP), but TLS needs a real certificate hostname: the
            // 2026-11-03 logs showed the jitter probe reporting `loss` on every tick while the
            // route probe succeeded, because the route probe maps literal IPs to their SNI
            // names and this channel sent the literal itself ("previous literal HTTPS target
            // produced zero verified RTTs").
            tlsHost = SmartJitterDefaults.tlsSniFor(probeHost)
        ).also { probeChannel = it }
    }

    private suspend fun considerSwitch(score: Double?, currentTuning: Tuning) {
        if (!JitterModeProfile.of(currentTuning.mode).switchEnabled) return
        val currentHost = host ?: return
        val best = currentHost.candidates()
            .asSequence()
            .filter { it.id != currentHost.liveRouteId() }
            .filter { (it.score ?: 0.0) > 0.0 }
            .maxByOrNull { it.score ?: 0.0 }
        if (best == null) return

        val state = stateFlow.value
        val decision = SwitchPolicy.decide(
            incumbentScore = score,
            challengerScore = best.score,
            incumbentLiveSinceMs = currentHost.liveRouteSinceMs(),
            state = SwitchPolicy.State(lastSwitchAtMs = state.lastSwitchAtMs),
            nowMs = System.currentTimeMillis(),
            minImproveFraction = currentTuning.switchMinImproveFraction,
            cooldownMs = currentTuning.switchCooldownMs,
            minSamples = currentTuning.minSamples
        )
        if (!decision.shouldSwitch) {
            if (decision.improvementFraction > 0.0) {
                currentHost.onEvent("switch-held", "${best.name} • ${decision.reason}")
            }
            return
        }
        val applied = runCatching { currentHost.applySwitch(best.id) }.getOrDefault(false)
        val entry = SwitchLogEntry(
            atMs = System.currentTimeMillis(),
            fromId = currentHost.liveRouteId(),
            fromName = currentHost.liveRouteName(),
            toId = best.id,
            toName = best.name,
            reason = decision.reason,
            applied = applied
        )
        if (applied) {
            mutex.withLock {
                probeChannel?.close()
                probeChannel = null
            }
            publish { previous ->
                previous.copy(
                    lastSwitchAtMs = entry.atMs,
                    switchLog = (listOf(entry) + previous.switchLog).take(SWITCH_LOG_MAX),
                    series = emptyList(),
                    samples = 0,
                    validSamples = 0,
                    currentScore = null
                )
            }
        }
        currentHost.onEvent(if (applied) "switch-applied" else "switch-refused", entry.reason)
    }

    private fun publish(transform: (State) -> State) {
        val next = transform(stateFlow.value)
        stateFlow.value = next
        runCatching { onState?.invoke(next) }
    }

    /** Stops the loop and closes the probe channel; idempotent, safe from any thread. */
    fun close() {
        started.set(false)
        val job = loop
        loop = null
        wakeUp()
        job?.cancel()
        scope.launch {
            mutex.withLock {
                probeChannel?.close()
                probeChannel = null
            }
        }
        publish { it.copy(running = false, mode = JitterMode.OFF) }
    }

    companion object {
        const val SERIES_MAX = 60
        const val SWITCH_LOG_MAX = 12
        const val MAX_BURST_SAMPLES = 8
    }
}

/** Shared defaults the settings surface and the controller both read. */
object SmartJitterDefaults {
    const val PROBE_HOST = "1.1.1.1"
    const val BASE_INTERVAL_SEC = 5
    const val MIN_INTERVAL_SEC = 2
    const val MAX_INTERVAL_SEC = 60
    const val SWITCH_IMPROVE_PERCENT = 20
    const val SWITCH_COOLDOWN_SEC = 180
    const val MIN_SAMPLES = 8

    /**
     * MARBLE_SMART_JITTER_REALTIME_V262 — how many warm keep-alive requests one probe tick
     * fires, and the gap between them. Three requests ~50 ms apart cost one radio wake and give
     * the RFC 3550 jitter EWMA three consecutive in-path deltas per tick (the route probe's own
     * burst is the same shape: 3 samples, 45 ms apart).
     */
    const val BURST_SAMPLES = 3
    const val BURST_GAP_MS = 50L

    /**
     * The TLS certificate hostname for a probe target. The SOCKS dial still uses the configured
     * [probeHost] (often a literal anycast IP — dialing the IP keeps the path identical to what
     * the route probe measures), but the TLS handshake must present a name the server's
     * certificate actually covers. Sending a literal IP as the SNI/hostname is what made every
     * jitter-probe tick read as `loss` in the 2026-11-03 logs ("previous literal HTTPS target
     * produced zero verified RTTs") while the route probe — which maps the same literals through
     * its own `probeTlsHost` — reported 100% success on the same session.
     */
    fun tlsSniFor(probeHost: String): String {
        var host = probeHost.trim().trim('[', ']').removePrefix("http://").removePrefix("https://")
            .substringBefore('/')
        // Strip a trailing :port only for hostname/IPv4 forms — an IPv6 literal has many
        // colons and must survive intact.
        if (host.count { it == ':' } == 1) host = host.substringBefore(':')
        return when (host) {
            "1.1.1.1", "1.0.0.1" -> "cloudflare-dns.com"
            "8.8.8.8" -> "dns.google"
            "9.9.9.9" -> "dns.quad9.net"
            else -> host.ifBlank { probeHost.trim() }
        }
    }
}
