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
        val probeHost: String = SmartJitterDefaults.PROBE_HOST
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

            val (rttMs, cold) = probeOnce(port, currentTuning.probeHost)
            val ok = rttMs > 0.0
            if (!ok) probeFailures++ else probeFailures = 0

            val snapshot = mutex.withLock {
                channel(port, currentTuning.probeHost).let { channel ->
                    // A cold sample (fresh connection) still counts as an attempt but stays out
                    // of the statistics — the cold-start rule, applied to our own probe.
                    channel.add(rttMs = rttMs, ok = ok, cold = cold)
                    channel.summarize(currentTuning.minSamples)
                }
            }
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
                if (ok) "rtt=${rttMs.toInt()}ms cold=$cold calm=$calmStreak" else "loss"
            )

            considerSwitch(score, currentTuning)

            waitForNextProbe(nextDelayMs)
        }
    }

    private suspend fun probeOnce(port: Int, probeHost: String): Pair<Double, Boolean> =
        mutex.withLock {
            channel(port, probeHost).probe()
        }

    /** The probe channel for the live SOCKS port; rebuilt (cold) whenever port or host change. */
    private fun channel(port: Int, probeHost: String): KeepaliveHttpsProbe {
        val existing = probeChannel
        if (existing != null && existing.matches(port, probeHost)) return existing
        existing?.close()
        return KeepaliveHttpsProbe(
            socksPort = port,
            host = probeHost,
            path = KeepaliveHttpsProbe.DEFAULT_PATH
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
        stateFlow.value = transform(stateFlow.value)
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
}
