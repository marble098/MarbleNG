package com.marbleng.app.vpn

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.AccelerationPlan
import com.marbleng.app.core.ActiveRouteQuality
import com.marbleng.app.core.BenchmarkEngine
import com.marbleng.app.core.ConnectionTuner
import com.marbleng.app.core.ContinuousRouteOptimizer
import com.marbleng.app.core.ConnectivityDiagnosticsObserver
import com.marbleng.app.core.DataStallGuard
import com.marbleng.app.core.LinkQualityEstimator
import com.marbleng.app.core.NetworkSnapshot
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.SocksHttpClient
import com.marbleng.app.core.SmartNotificationKind
import com.marbleng.app.core.SmartNotifier
import com.marbleng.app.core.TransportTelemetry
import com.marbleng.app.core.XrayManager
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.nativebridge.HevTunnel
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fail-closed Android tunnel controller.
 *
 * The Android VPN is established before Xray. If Xray/HEV fails later, the VPN fd stays open and
 * traffic is blackholed while Marble Intelligence attempts bounded fallback on the same TUN.
 */
class MarbleVpnService : VpnService() {
    // MARBLE_STABILITY_V10
    // MARBLE_ENGINE_RESCUE_V11
    // MARBLE_DNS_SELF_HEAL_V111
    // MARBLE_CONNECT_RESCUE_V12
    // MARBLE_CONNECT_RESCUE_V13
    // MARBLE_FAST_DECISION_V14
    // MARBLE_RUNTIME_HARDENING_V16
    // MARBLE_JITTER_ENGINE_V17
    // MARBLE_JITTER_CONTROL_V18
    // MARBLE_VERIFIED_LATENCY_V19
    // MARBLE_RUNTIME_STARTUP_RESCUE_V21
    // MARBLE_VERIFIED_JITTER_BURST_V22
    // MARBLE_RUNTIME_EXTREME_V23
    // MARBLE_FAST_READY_V25
    // MARBLE_RUNTIME_POLISH_V29
    // MARBLE_EXTREME_NETWORK_V30
    // MARBLE_INSTANT_QUALITY_V31
    // MARBLE_FAST_READY_V33
    // MARBLE_EXACT_PROFILE_SOURCE_V38
    // MARBLE_RTT_RESILIENCE_V47
    // MARBLE_REAL_RTT_XHTTP_V50
    // MARBLE_REALTIME_ENGINE_V70
    // Live optimisation may learn while connected, but it must never intentionally tear down
    // a healthy user tunnel merely to hot-apply a transport experiment.
    companion object {
        const val ACTION_START = "com.marbleng.START"
        const val ACTION_STOP = "com.marbleng.STOP"

        /** User-triggered acceleration pass on the route that is already carrying traffic. */
        const val ACTION_TUNE = "com.marbleng.TUNE"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_PROFILE_SOURCE = "profileSource"
        const val EXTRA_MODE = "mode"
        const val MODE_TUN = "tun"
        const val MODE_PROXY = "proxy"
        const val CHANNEL = "marbleng-vpn"
        const val NOTIFY = 7301
        private const val ROUTE_PROBE_INTERVAL_TICKS = 30
        private const val ROUTE_DEGRADED_PROBE_TICKS = 8
        private const val ROUTE_HEAVY_PROBE_TICKS = 60
        private const val ROUTE_JITTER_TRIGGER_MS = 25
        private const val ROUTE_JITTER_RELEASE_MS = 12
        // A bounded outcome window records both verified RTTs and misses. Twelve attempts remain
        // responsive while providing enough evidence for p90, IPDV and reliability scoring.
        private const val ROUTE_WINDOW_SIZE = 12
        private const val JITTER_HIGH_CONFIRMATIONS = 3
        private const val JITTER_RELEASE_CONFIRMATIONS = 4
        private const val JITTER_OPTIMIZER_COOLDOWN_MS = 180_000L
        private const val TURBO_INCONCLUSIVE_BASE_BACKOFF_MS = 600_000L
        private const val TURBO_INCONCLUSIVE_MAX_BACKOFF_MS = 1_800_000L
        private const val JITTER_PRIMARY_HOST = "1.1.1.1"
        private val JITTER_PROBE_TARGETS = listOf(
            "1.1.1.1" to "/cdn-cgi/trace",
            "8.8.8.8" to "/dns-query",
            "9.9.9.9" to "/dns-query",
            "1.0.0.1" to "/cdn-cgi/trace"
        )
        // Full HTTPS fallback uses SOCKS domain ATYP. Android/system DNS is never involved.
        // Order spans Google and Cloudflare instead of retrying one provider.
        private val LIVE_DOMAIN_RTT_TARGETS = listOf(
            "www.gstatic.com" to "/generate_204",
            "cp.cloudflare.com" to "/generate_204",
            "www.google.com" to "/generate_204"
        )
        private const val LIVE_RTT_BURST_SAMPLES = 3
        private const val LIVE_RTT_BURST_GAP_MS = 45L
        private const val LIVE_RTT_TIMEOUT_MS = 1_250
        private const val LIVE_DOMAIN_RTT_TIMEOUT_MS = 3_500
        // Failed XHTTP probes can leave expensive pending work inside the core. Retry slowly
        // instead of creating a new synthetic dial every telemetry tick.
        private const val VERIFIED_RTT_BACKOFF_MS = 60_000L
        // Synthetic endpoints are advisory. Process death is handled immediately; route failure
        // requires normally-spaced misses plus a second independent confirmation.
        private const val PROBE_FAILURES_BEFORE_RECOVERY = 4
        private const val ROUTE_CONFIRM_TIMEOUT_MS = 4_500
        private const val RECENT_TRAFFIC_GRACE_MS = 75_000L

        // A freshly established mobile tunnel often carries real traffic before synthetic HTTPS
        // probes have warmed TLS/DNS state. Process death is still handled immediately; only
        // synthetic miss accounting gets this short grace.
        private const val ROUTE_FAILURE_WARMUP_MS = 18_000L
        // HEV main_from_str() is blocking. If it is still alive after this grace, publish
        // CONNECTED sooner; native stats keep their own later warm-up and are NOT moved earlier.
        private const val HEV_READY_GRACE_MS = 250L
        private const val CONNECT_STARTUP_TIMEOUT_MS = 90_000L
        private const val HEV_STALL_MIN_MS = 35_000L
        private const val HEV_STALL_MIN_TX_BYTES = 64L * 1024L
        private const val HEV_STATS_FAILURE_LIMIT = 5
        private const val EXIT_IDENTITY_PROBE_INTERVAL_TICKS = 180

        /** Let a fresh route settle before spending link capacity on measuring alternatives. */
        private const val FIRST_TUNE_DELAY_MS = 20_000L
        private const val HEAVY_TRAFFIC_BPS = 3L * 1024L * 1024L
    }

    private var tun: ParcelFileDescriptor? = null
    private var hevFd = -1
    private val running = AtomicBoolean(false)
    private val recoveryScheduled = AtomicBoolean(false)
    private val routeProbeRequested = AtomicBoolean(false)
    private val optimizerScanRequested = AtomicBoolean(false)
    private val optimizerRunning = AtomicBoolean(false)
    private val tuningRequested = AtomicBoolean(false)
    private val tuningRunning = AtomicBoolean(false)
    private val tunReadyPublished = AtomicBoolean(false)
    private val startupTimedOut = AtomicBoolean(false)
    private val routeGeneration = AtomicInteger(0)
    private val startCommandGeneration = AtomicInteger(0)

    // HEV is process-global; start/stop/recovery is serialized here.
    private val connectionWorker = Executors.newSingleThreadExecutor()

    // Service commands may terminate a child process. XrayManager.stop() deliberately waits for a
    // bounded process shutdown, so START/STOP must never execute that wait on Android's main thread.
    private val controlWorker = Executors.newSingleThreadExecutor()

    // Delayed gates belong on a scheduler. Sleeping watchdog/readiness jobs inside monitorWorker
    // could occupy half of the health pool during the exact startup window we are trying to prove.
    private val timerWorker = Executors.newSingleThreadScheduledExecutor()

    // Telemetry, the proxy monitor, DNS probing and a long autopilot cycle can all be in flight at
    // once, so a long optimizer cycle must not queue the identity/health probes behind it.
    private val monitorWorker = Executors.newFixedThreadPool(4)

    private lateinit var xray: XrayManager
    private lateinit var diag: RuntimeDiagnostics
    private lateinit var notifier: SmartNotifier
    private lateinit var routeOptimizer: ContinuousRouteOptimizer
    private lateinit var tuner: ConnectionTuner
    private var networkListener: Closeable? = null
    private var connectivityDiagnostics: Closeable? = null
    private val dataStallGuard = DataStallGuard()

    @Volatile private var activeSession = ""
    @Volatile private var activeMode = MODE_TUN
    @Volatile private var activeProfileId = ""
    @Volatile private var activeProfileSourceId = ""
    @Volatile private var activeSettings: AppSettings? = null
    @Volatile private var hevActive = false
    @Volatile private var activeMtu = 1500
    @Volatile private var connectStartedNs = 0L
    @Volatile private var consecutiveProbeFailures = 0
    @Volatile private var identityRecoveryAttempts = 0
    @Volatile private var ipv6RouteCaptured = false
    @Volatile private var pinnedExitV4 = ""
    @Volatile private var pinnedExitV6 = ""
    @Volatile private var lastNetworkKey = ""
    @Volatile private var lastNetworkValidated = false
    @Volatile private var lastTuneAt = 0L
    @Volatile private var lastTrafficProgressAt = 0L
    @Volatile private var sessionTuned = false

    /** Acceleration method the live Xray process was actually started with. */
    @Volatile private var activeMethodId = AccelerationPlan.DIRECT

    private val recoveryTried = linkedSetOf<String>()
    /** Positive values are verified HTTPS RTTs; -1 is a failed attempt. */
    private val routeOutcomeWindow = ArrayDeque<Int>()
    @Volatile private var jitterProbeHost = JITTER_PRIMARY_HOST
    @Volatile private var jitterHighStreak = 0
    @Volatile private var jitterLowStreak = 0
    @Volatile private var jitterControlActive = false
    @Volatile private var lastJitterOptimizerRequestAt = 0L
    @Volatile private var tuningInconclusiveStreak = 0
    @Volatile private var tuningBackoffUntilMs = 0L
    @Volatile private var verifiedRttBackoffUntilMs = 0L

    override fun onCreate() {
        super.onCreate()
        val app = application as MarbleApplication
        xray = app.xray
        diag = RuntimeDiagnostics(this)
        notifier = SmartNotifier(this)
        notifier.ensureChannels()
        routeOptimizer = ContinuousRouteOptimizer(app.repo.intelligence)
        tuner = ConnectionTuner(xray, app.repo.intelligence)
        app.repo.intelligence.startMonitoring()
        networkListener = app.repo.intelligence.addNetworkListener(::onUnderlyingNetworkChanged)
        connectivityDiagnostics = ConnectivityDiagnosticsObserver.register(this, monitorWorker) { signal ->
            if (signal.kind == ConnectivityDiagnosticsObserver.Kind.DATA_STALL && running.get()) {
                val now = signal.observedAtMs
                val trafficRecent = lastTrafficProgressAt > 0L &&
                    now - lastTrafficProgressAt <= RECENT_TRAFFIC_GRACE_MS
                val decision = dataStallGuard.onSignal(now, trafficRecent)
                if (decision == DataStallGuard.Decision.PROBE ||
                    decision == DataStallGuard.Decision.CONFIRM
                ) {
                    routeProbeRequested.set(true)
                }
                if (decision == DataStallGuard.Decision.CONFIRM) {
                    optimizerScanRequested.set(
                        (activeSettings ?: app.repo.settings).continuousOptimizerEnabled &&
                            !(activeSettings ?: app.repo.settings).identityGuardEnabled
                    )
                }
                diag.event(
                    "NETWORK",
                    "android-data-stall",
                    "session" to activeSession,
                    "network" to signal.network.toString(),
                    "observedAtMs" to signal.observedAtMs,
                    "decision" to decision.name,
                    "trafficRecent" to trafficRecent
                )
            }
        }
        diag.event("VPN", "service-created", "system" to diag.systemSnapshot())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return runCatching {
            diag.event("VPN", "command", "action" to intent?.action, "startId" to startId, "flags" to flags)
            when (intent?.action) {
                ACTION_STOP -> {
                    startCommandGeneration.incrementAndGet()
                    // cleanupRuntime() can wait for the Xray child to terminate; never block
                    // MainActivity/input dispatch while Android delivers this service command.
                    controlWorker.execute {
                        runCatching { shutdown(true) }
                            .onFailure { diag.error("VPN", "async-stop-failed", it) }
                    }
                }
                ACTION_TUNE -> {
                    tuningRequested.set(true)
                    diag.event("TURBO", "manual-request", "session" to activeSession)
                }
                ACTION_START -> {
                    val id = intent.getStringExtra(EXTRA_PROFILE)
                    val sourceId = intent.getStringExtra(EXTRA_PROFILE_SOURCE)
                    if (id.isNullOrBlank()) {
                        failBeforeTunnel("Missing profile id")
                    } else {
                        val requestedMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TUN
                        val requestGeneration = startCommandGeneration.incrementAndGet()

                        // startForegroundService() has a strict Android deadline. Publish a small
                        // foreground notification immediately, then do DB/process/runtime cleanup
                        // on the serialized control worker instead of the app main thread.
                        if (!promoteForeground("Preparing secure route", ongoing = true)) {
                            failBeforeTunnel("Android rejected foreground-service startup")
                        } else {
                            controlWorker.execute {
                                if (requestGeneration != startCommandGeneration.get()) {
                                    diag.event(
                                        "VPN",
                                        "start-command-superseded",
                                        "profile" to id.take(12),
                                        "mode" to requestedMode,
                                        "requestGeneration" to requestGeneration,
                                        "latestGeneration" to startCommandGeneration.get()
                                    )
                                    return@execute
                                }
                                runCatching { startConnection(id, sourceId, requestedMode) }
                                    .onFailure { error ->
                                        diag.error(
                                            "VPN",
                                            "async-start-failed",
                                            error,
                                            "profile" to id.take(12),
                                            "mode" to requestedMode
                                        )
                                        if (activeSession.isBlank()) {
                                            failBeforeTunnel(
                                                "Connection setup failed: ${safeMessage(error)}"
                                            )
                                        } else {
                                            handleFailure(
                                                activeSession,
                                                "Connection setup failed: ${safeMessage(error)}"
                                            )
                                        }
                                    }
                            }
                        }
                    }
                }
                null -> diag.event("VPN", "null-intent-ignored")
                else -> diag.event("VPN", "unknown-action", "action" to intent.action)
            }
            START_NOT_STICKY
        }.getOrElse { error ->
            diag.error("VPN", "command-crash-guard", error, "action" to intent?.action)
            handleFailure(activeSession, "Service error: ${safeMessage(error)}")
            START_NOT_STICKY
        }
    }

    @Synchronized
    private fun startConnection(id: String, sourceId: String?, mode: String) {
        if (running.get() || tun != null || hevActive || xray.isAlive) cleanupRuntime(setDisconnected = false)

        val app = application as MarbleApplication
        val profile = app.repo.profile(id, sourceId) ?: run {
            diag.event("VPN", "profile-missing", "profileId" to id.take(12))
            failBeforeTunnel("Profile no longer exists")
            return
        }
        profileCompatibilityIssue(profile)?.let { issue ->
            diag.event(
                "XRAY", "profile-preflight-rejected",
                "profile" to profile.id.take(12),
                "scheme" to profile.scheme,
                "reason" to issue
            )
            failBeforeTunnel(issue)
            return
        }

        val settings = app.repo.effectiveSettingsFor(profile)
        val normalizedMode = if (mode == MODE_PROXY) MODE_PROXY else MODE_TUN
        val port = if (normalizedMode == MODE_PROXY) settings.localProxyPort else settings.socksPort
        val session = System.currentTimeMillis().toString(36) + "-" + Integer.toHexString(profile.id.hashCode())

        activeSession = session
        activeMode = normalizedMode
        activeProfileId = profile.id
        activeProfileSourceId = profile.subscriptionId
        activeSettings = settings
        connectStartedNs = System.nanoTime()
        consecutiveProbeFailures = 0
        identityRecoveryAttempts = 0
        ipv6RouteCaptured = false
        pinnedExitV4 = ""
        pinnedExitV6 = ""
        routeGeneration.incrementAndGet()
        recoveryScheduled.set(false)
        optimizerScanRequested.set(false)
        tuningRequested.set(false)
        lastTuneAt = 0L
        lastTrafficProgressAt = 0L
        sessionTuned = false
        tunReadyPublished.set(false)
        startupTimedOut.set(false)
        routeOptimizer.reset(System.currentTimeMillis())
        synchronized(recoveryTried) {
            recoveryTried.clear()
            recoveryTried += profile.id
        }
        synchronized(routeOutcomeWindow) { routeOutcomeWindow.clear() }
        jitterProbeHost = JITTER_PRIMARY_HOST
        jitterHighStreak = 0
        jitterLowStreak = 0
        jitterControlActive = false
        lastJitterOptimizerRequestAt = 0L
        tuningInconclusiveStreak = 0
        tuningBackoffUntilMs = 0L

        diag.event(
            "VPN", "connect-request",
            "session" to session,
            "profileId" to profile.id.take(12),
            "profileName" to profile.name,
            "mode" to normalizedMode,
            "socksPort" to port,
            "network" to app.repo.intelligence.currentSnapshot().label
        )

        val promoted = promoteForeground(
            if (normalizedMode == MODE_PROXY) "Starting local proxy • ${profile.name}"
            else "Securing device route • ${profile.name}",
            ongoing = true
        )
        if (!promoted) {
            failBeforeTunnel("Android rejected foreground-service startup")
            return
        }

        running.set(true)
        updateSentinel(killSwitch = normalizedMode == MODE_TUN)

        // One connection attempt gets a finite wall-clock budget. A scheduled watchdog does not
        // consume a monitor thread for the full startup window, so HEV readiness/identity/DNS workers cannot be
        // starved by the watchdog whose job is to protect them.
        timerWorker.schedule({
            if (isCurrent(session) && !tunReadyPublished.get()) {
                startupTimedOut.set(true)
                diag.event(
                    "VPN", "startup-timeout",
                    "session" to session,
                    "mode" to normalizedMode,
                    "timeoutMs" to CONNECT_STARTUP_TIMEOUT_MS
                )
                diag.event(
                    "XRAY",
                    "startup-cancel-request",
                    "session" to session,
                    "phase" to xray.lastStartPhase
                )
                xray.stop()
                handleFailure(
                    session,
                    "Connection startup timed out after ${CONNECT_STARTUP_TIMEOUT_MS / 1_000}s"
                )
            }
        }, CONNECT_STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        connectionWorker.execute {
            runCatching {
                if (!isCurrent(session)) return@runCatching
                if (normalizedMode == MODE_TUN && !establishTun(profile, session, settings)) {
                    handleFailure(session, "VPN establish failed")
                    return@runCatching
                }
                if (!isCurrent(session)) return@runCatching
                startXrayAndForward(profile, session, port, settings, recovering = false)
            }.onFailure { error ->
                diag.error(
                    "VPN",
                    "connection-worker-crash-guard",
                    error,
                    "session" to session,
                    "profile" to profile.id.take(12),
                    "mode" to normalizedMode
                )
                if (isCurrent(session)) {
                    handleFailure(
                        session,
                        "Connection worker failed: ${safeMessage(error)}"
                    )
                }
            }
        }
    }

    private fun startXrayAndForward(
        profile: ProxyProfile,
        session: String,
        port: Int,
        requestedSettings: AppSettings,
        recovering: Boolean
    ) {
        val app = application as MarbleApplication
        activeProfileId = profile.id
        activeSettings = requestedSettings

        // Marble Turbo runs before the route carries traffic: the TUN is already established and
        // fail-closed, so measuring transport methods here costs nothing but a few seconds.
        val settings = preTune(profile, session, requestedSettings, recovering)
        if (!isCurrent(session)) return
        activeSettings = settings
        activeMethodId = app.repo.intelligence.acceleration(profile.id)?.methodId
            ?: AccelerationPlan.DIRECT
        connectStartedNs = System.nanoTime()
        verifiedRttBackoffUntilMs = 0L
        val generation = routeGeneration.incrementAndGet()

        diag.event(
            "XRAY", if (recovering) "recovery-start" else "start-begin",
            "session" to session,
            "profile" to profile.name,
            "port" to port,
            "mode" to activeMode,
            "fragment" to settings.fragmentEnabled,
            "mux" to settings.muxEnabled,
            "dnsStrategy" to settings.dnsQueryStrategy
        )

        val coreStartNs = System.nanoTime()
        val coreStarted = xray.start(profile, port, settings)
        val coreStartMs = ((System.nanoTime() - coreStartNs) / 1_000_000L).coerceAtLeast(0L)
        diag.event(
            "XRAY",
            "start-result",
            "session" to session,
            "ok" to coreStarted,
            "elapsedMs" to coreStartMs,
            "phase" to xray.lastStartPhase,
            "alive" to xray.isAlive,
            "reason" to if (coreStarted) "" else xray.lastStartError.take(500)
        )
        if (!coreStarted) {
            // STOP/watchdog/new START may have invalidated this attempt while core startup was
            // unwinding. Never record a second failure or re-block a newer user command.
            if (!isCurrent(session)) {
                diag.event(
                    "XRAY",
                    "start-result-stale",
                    "session" to session,
                    "phase" to xray.lastStartPhase
                )
                return
            }
            handleFailure(
                session,
                xray.lastStartError.ifBlank {
                    "Xray rejected profile or routing policy"
                }
            )
            return
        }
        if (!isCurrent(session)) {
            xray.stop()
            return
        }

        // Synthetic Internet observations are diagnostics, not startup gates. Defer them until the
        // tunnel has had time to settle so Marble does not inject TLS/HTTP work into the exact
        // window where interactive jitter is being established.
        scheduleSyntheticEgressObservation(session, generation, port, profile)

        diag.event("XRAY", "socks-ready", "session" to session, "alive" to xray.isAlive, "port" to port)

        // On a fresh session there is no previous exit identity to compare against, so a blocking
        // 5-second trace request cannot prove a rotation and only delays HEV startup. Pin the first
        // observed exit in the live monitor. Recovery/handoff still verifies an existing pin BEFORE
        // traffic is exposed, preserving the strict same-session anti-rotation guarantee.
        val existingIdentityPin = pinnedExitV4.isNotBlank() || pinnedExitV6.isNotBlank()
        if (
            settings.identityGuardEnabled &&
            (recovering || existingIdentityPin) &&
            !verifyExitIdentity(session, port, generation)
        ) {
            return
        }
        if (settings.identityGuardEnabled && !recovering && !existingIdentityPin) {
            diag.event("IDENTITY", "initial-pin-deferred", "session" to session)
        }

        scheduleAdaptiveDnsObservation(session, generation, port, settings)

        if (activeMode == MODE_PROXY) {
            startupTimedOut.set(false)
            tunReadyPublished.set(true)
            app.repo.markConnected(profile)
            app.repo.intelligence.recordConnect(profile.id, true, elapsedConnectMs(), settings)
            notifyNow("SOCKS5 • 127.0.0.1:$port • ${profile.name}", true)
            notifier.alert(
                SmartNotificationKind.CONNECTION,
                "proxy-ready:$session:${profile.id}",
                "Local proxy ready",
                "127.0.0.1:$port • ${profile.name}",
                settings
            )
            updateSentinel(killSwitch = false)
            startProxyMonitor(session, port, generation)
            return
        }
        runTun(profile, session, port, settings, recovering, generation)
    }

    private fun establishTun(profile: ProxyProfile, session: String, settings: AppSettings): Boolean {
        val app = application as MarbleApplication
        val mtu = app.repo.intelligence.adaptiveMtu(profile, settings)
        activeMtu = mtu

        val builder = Builder()
            .setSession("MarbleNG • ${profile.name}")
            .setMtu(mtu)
            .setBlocking(false)
            .addAddress("198.18.0.1", 32)
            .addRoute("0.0.0.0", 0)

        if (Build.VERSION.SDK_INT >= 22) {
            val upstream = app.repo.intelligence.underlyingNetworks()
            if (upstream.isNotEmpty()) runCatching { builder.setUnderlyingNetworks(upstream.toTypedArray()) }
        }

        // This resolver list is what apps use whenever the encrypted DNS hijack is not answering,
        // and it is also the only way an IPv6-only underlay can look anything up: handing a v6-only
        // network two IPv4 resolvers makes every AAAA query — and therefore every IPv6 node —
        // unreachable before the engine even starts.
        val underlay = app.repo.intelligence.currentSnapshot()
        val dnsServers = linkedSetOf<String>()
        listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { candidate ->
                // A v4 resolver on a network that has no IPv4 can only ever time out.
                if (candidate.contains(':') || underlay.hasIpv4) dnsServers += candidate
            }
        if (settings.ipv6Enabled && dnsServers.none { it.contains(':') }) {
            // Bootstrap v6 resolvers so the underlay can resolve a node's AAAA records even when the
            // user configured IPv4 DNS only. Both are literals, so no lookup is needed to reach them.
            dnsServers += "2606:4700:4700::1111"
            dnsServers += "2001:4860:4860::8888"
        }

        var dnsCount = 0
        dnsServers.forEach { dns ->
            runCatching {
                builder.addDnsServer(dns)
                dnsCount++
            }.onFailure {
                diag.error("TUN", "dns-builder-failed", it, "dns" to dns, "session" to session)
            }
        }
        if (dnsCount == 0) {
            diag.event("TUN", "dns-policy-empty", "session" to session)
            return false
        }

        // Always attempt to capture IPv6. Privacy state must reflect the real Builder result.
        var ipv6Ok = false
        runCatching {
            builder.addAddress("fc00::1", 128).addRoute("::", 0)
        }.onSuccess {
            ipv6Ok = true
            diag.event("TUN", "ipv6-enabled", "session" to session)
        }.onFailure {
            diag.error("TUN", "ipv6-builder-failed", it, "session" to session)
        }
        if (settings.identityGuardEnabled && !ipv6Ok) {
            diag.event("TUN", "identity-ipv6-fail-closed", "session" to session)
            return false
        }

        val packages = settings.splitTunnelPackages
            .split(',', '\n', '\r', ';')
            .map(String::trim)
            .filter { it.isNotBlank() && it != packageName }
            .distinct()
        if (
            settings.splitTunnelMode == com.marbleng.app.model.SplitTunnelMode.ONLY_SELECTED &&
            packages.isEmpty()
        ) {
            diag.event("TUN", "split-only-selected-empty", "session" to session)
            return false
        }

        var policyOk = true
        when (settings.splitTunnelMode) {
            com.marbleng.app.model.SplitTunnelMode.ONLY_SELECTED -> {
                packages.forEach { pkg ->
                    val ok = runCatching { builder.addAllowedApplication(pkg) }
                        .onFailure { diag.error("TUN", "split-allow-failed", it, "package" to pkg, "session" to session) }
                        .isSuccess
                    policyOk = policyOk && ok
                }
            }
            com.marbleng.app.model.SplitTunnelMode.BYPASS_SELECTED -> {
                policyOk = runCatching { builder.addDisallowedApplication(packageName) }
                    .onFailure { diag.error("TUN", "self-disallow-failed", it, "session" to session) }
                    .isSuccess
                packages.forEach { pkg ->
                    runCatching { builder.addDisallowedApplication(pkg) }
                        .onFailure { diag.error("TUN", "split-bypass-stale", it, "package" to pkg, "session" to session) }
                }
            }
            com.marbleng.app.model.SplitTunnelMode.ALL_APPS -> {
                policyOk = runCatching { builder.addDisallowedApplication(packageName) }
                    .onFailure { diag.error("TUN", "self-disallow-failed", it, "session" to session) }
                    .isSuccess
            }
        }
        if (!policyOk) return false

        val established = runCatching { builder.establish() }
            .onFailure { diag.error("TUN", "establish-exception", it, "session" to session) }
            .getOrNull() ?: return false
        if (!isCurrent(session)) {
            runCatching { established.close() }
            return false
        }
        tun = established
        ipv6RouteCaptured = ipv6Ok
        diag.event(
            "TUN", "established",
            "session" to session,
            "vpnFd" to established.fd,
            "mtu" to mtu,
            "splitMode" to settings.splitTunnelMode.name,
            "splitPackages" to packages.size,
            "dnsCount" to dnsCount,
            "dnsHijack" to settings.dnsHijackEnabled,
            "ipv6Captured" to ipv6Ok
        )
        app.repo.refreshIntelligenceStatus()
        updateSentinel(killSwitch = true)
        return true
    }

    private fun runTun(
        profile: ProxyProfile,
        session: String,
        socksPort: Int,
        settings: AppSettings,
        recovering: Boolean,
        generation: Int
    ) {
        if (!isRouteCurrent(session, generation)) return
        val currentTun = tun ?: run {
            handleFailure(session, "TUN disappeared before HEV startup")
            return
        }
        diag.prepareHevSession()
        val dupFd = runCatching { ParcelFileDescriptor.dup(currentTun.fileDescriptor).detachFd() }
            .onFailure { diag.error("TUN", "dup-fd-failed", it, "session" to session) }
            .getOrNull()
        if (dupFd == null) {
            handleFailure(session, "TUN fd duplication failed")
            return
        }
        hevFd = dupFd

        // Datapath sizing follows measured throughput: a fast link stops being capped by a 64 KiB
        // socket buffer, and a throttled device stops paying for buffers it cannot fill.
        val datapath = (application as MarbleApplication).repo.intelligence
            .tunnelTuning(profile.id, settings)
        val cfg = listOf(
            "tunnel:",
            "  mtu: $activeMtu",
            "  ipv4: 198.18.0.1",
            "  ipv6: 'fc00::1'",
            "  icmp: 'off'",
            "socks5:",
            "  address: '127.0.0.1'",
            "  port: $socksPort",
            "  udp: 'udp'",
            "misc:",
            "  log-file: '${diag.hevLog.absolutePath}'",
            "  log-level: error",
            "  task-stack-size: 86016",
            "  tcp-buffer-size: ${datapath.tcpBufferBytes}",
            "  udp-recv-buffer-size: ${datapath.udpBufferBytes}",
            "  max-session-count: ${datapath.maxSessions}"
        ).joinToString(separator = "\n", postfix = "\n")
        if (cfg.contains("\\n") || !cfg.contains('\n')) {
            handleFailure(session, "Internal HEV YAML encoding failure")
            return
        }

        diag.event(
            "HEV", "config-ready",
            "session" to session,
            "bytes" to cfg.toByteArray().size,
            "lines" to cfg.lineSequence().count(),
            "sha256" to diag.sha256(cfg),
            "mtu" to activeMtu,
            "sessions" to datapath.maxSessions,
            "tcpBuffer" to datapath.tcpBufferBytes,
            "udpBuffer" to datapath.udpBufferBytes,
            "datapath" to datapath.label
        )
        if (!isCurrent(session)) {
            closeHevFd()
            return
        }

        val app = application as MarbleApplication

        // HEV's public API defines main_from_str() as a blocking run call: it returns only after
        // quit/error. A run that remains alive through this short grace is therefore a much safer
        // startup signal than calling the native stats function while HEV is still initializing.
        recoveryScheduled.set(false)
        consecutiveProbeFailures = 0
        lastTrafficProgressAt = 0L
        tunReadyPublished.set(false)
        hevActive = true
        updateSentinel(killSwitch = true)

        timerWorker.schedule({
            if (isRouteCurrent(session, generation) && hevActive && xray.isAlive) {
                if (tunReadyPublished.compareAndSet(false, true) && isRouteCurrent(session, generation)) {
                startupTimedOut.set(false)
                app.repo.markConnected(profile)
                app.repo.intelligence.recordConnect(profile.id, true, elapsedConnectMs(), settings)
                val iran = app.repo.iranMode
                notifyNow(
                    if (iran.active) "Protected • ${profile.name} • Iran Mode ${iran.ispShortName}".trimEnd()
                    else "Protected • ${profile.name}", true
                )
                notifier.alert(
                    if (recovering) SmartNotificationKind.RECOVERY else SmartNotificationKind.CONNECTION,
                    if (recovering) "recovered:$session:${profile.id}" else "connected:$session:${profile.id}",
                    if (recovering) "Route recovered" else "VPN protected",
                    if (recovering) "Traffic moved safely to ${profile.name}" else profile.name,
                    settings
                )
                diag.event(
                    "HEV", "ready",
                    "session" to session,
                    "graceMs" to HEV_READY_GRACE_MS,
                    "proof" to "blocking-run-alive"
                )

                // Telemetry starts only after readiness is published. Its own warm-up below delays
                // the first native stats call again, eliminating the early JNI initialization race.
                    startTelemetry(session, socksPort, generation)
                }
            }
        }, HEV_READY_GRACE_MS, TimeUnit.MILLISECONDS)

        diag.event("HEV", "run-enter", "session" to session, "hevFd" to hevFd, "xrayAlive" to xray.isAlive)
        val result = runCatching { HevTunnel.run(cfg, hevFd) }
        hevActive = false
        val code = result.getOrElse {
            diag.error("HEV", "jni-run-exception", it, "session" to session, "hevFd" to hevFd)
            -10001
        }
        diag.event("HEV", "run-exit", "session" to session, "code" to code, "runningFlag" to running.get(), "xrayAlive" to xray.isAlive)

        if (isCurrent(session)) {
            if (!tunReadyPublished.get()) {
                diag.event("HEV", "exited-before-ready", "session" to session, "code" to code)
            }
            if (!recoveryScheduled.get()) handleFailure(session, "HEV stopped ($code)")
        }
    }

private fun startProxyMonitor(session: String, port: Int, generation: Int) {
        (application as MarbleApplication).repo.beginRouteMeasurement()
        monitorWorker.execute {
            var tick = 0

            // Publish a real active-path score immediately instead of waiting for the periodic
            // health cadence. A failure requests a quick confirmation through routeProbeRequested.
            sampleRouteLatency(session, port, generation)

            while (isRouteCurrent(session, generation) && activeMode == MODE_PROXY) {
                if (!xray.isAlive) {
                    handleFailure(session, "Xray local proxy stopped")
                    return@execute
                }
                val routeProbeNow = routeProbeRequested.getAndSet(false)
                if (shouldSampleRoute(tick) || routeProbeNow) {
                    sampleRouteLatency(session, port, generation)
                }
                if (
                    (tick == 12 || (tick > 0 && tick % EXIT_IDENTITY_PROBE_INTERVAL_TICKS == 0)) &&
                    !verifyExitIdentity(session, port, generation)
                ) {
                    return@execute
                }
                maybeAccelerate(session, generation)
                maybeScheduleOptimizer(session, generation)
                if (!sleepQuietly(1_000L)) return@execute
                tick++
            }
        }
    }

private fun startTelemetry(session: String, port: Int, generation: Int) {
        (application as MarbleApplication).repo.beginRouteMeasurement()
        monitorWorker.execute {
            val repo = (application as MarbleApplication).repo
            var lastUp = -1L
            var lastDown = -1L
            var lastT = System.nanoTime()
            var tick = 0
            var statsFailures = 0
            var txWithoutRxBytes = 0L
            var txWithoutRxSince = 0L

            // The dashboard should become meaningful as soon as Xray is live. Probe the actual
            // SOCKS/Xray path now, then keep an adaptive low-noise health cadence.
            sampleRouteLatency(session, port, generation)

            while (isRouteCurrent(session, generation) && hevActive) {
                if (!sleepQuietly(1_000L)) return@execute
                if (!isRouteCurrent(session, generation) || !hevActive) break
                if (!xray.isAlive) {
                    handleFailure(session, "Xray core stopped")
                    return@execute
                }

                // HevTunnel.stats() is advisory telemetry, not a startup primitive. Give the native
                // tunnel several seconds after its blocking run begins before the first JNI read.
                if (tick < 2) {
                    tick++
                    continue
                }

                val stats = runCatching { HevTunnel.stats() }.getOrNull()
                if (stats == null || stats.size < 4) {
                    statsFailures++
                    diag.event("HEV", "stats-unavailable", "session" to session, "count" to statsFailures)
                    if (statsFailures >= HEV_STATS_FAILURE_LIMIT) {
                        handleFailure(session, "HEV stats unavailable $statsFailures times")
                        return@execute
                    }
                } else {
                    statsFailures = 0
                    val nowNs = System.nanoTime()
                    val nowMs = System.currentTimeMillis()
                    val dt = (nowNs - lastT) / 1e9
                    val up = stats[1].coerceAtLeast(0L)
                    val down = stats[3].coerceAtLeast(0L)
                    if (lastUp >= 0 && lastDown >= 0 && dt > 0.25) {
                        val deltaDown = (down - lastDown).coerceAtLeast(0)
                        val deltaUp = (up - lastUp).coerceAtLeast(0)
                        if (deltaDown > 0L || deltaUp > 0L) {
                            lastTrafficProgressAt = nowMs
                            // Real HEV byte movement is stronger route-health evidence than a
                            // blocked synthetic connectivity-check hostname.
                            consecutiveProbeFailures = 0
                        }

                        if (deltaDown > 0L) {
                            txWithoutRxBytes = 0L
                            txWithoutRxSince = 0L
                        } else if (deltaUp > 0L) {
                            if (txWithoutRxSince == 0L) txWithoutRxSince = nowMs
                            txWithoutRxBytes += deltaUp
                            if (txWithoutRxBytes >= HEV_STALL_MIN_TX_BYTES &&
                                nowMs - txWithoutRxSince >= HEV_STALL_MIN_MS &&
                                isRouteCurrent(session, generation)) {
                                diag.event("HEV", "datapath-stall-suspected",
                                    "session" to session,
                                    "txWithoutRxBytes" to txWithoutRxBytes,
                                    "stallMs" to (nowMs - txWithoutRxSince),
                                    "xrayAlive" to xray.isAlive)
                                // Upload-only traffic is legitimate. Confirm the suspected stall
                                // with independent HTTPS evidence before entering fail-closed
                                // recovery; the old byte-only rule killed a working route after a
                                // small one-way transfer in the attached runtime log.
                                if (confirmRouteUnavailable(session, port, generation)) {
                                    diag.event(
                                        "HEV", "datapath-stalled-confirmed",
                                        "session" to session,
                                        "txWithoutRxBytes" to txWithoutRxBytes,
                                        "stallMs" to (nowMs - txWithoutRxSince)
                                    )
                                    handleFailure(session,
                                        "HEV datapath stalled and independent HTTPS confirmation failed")
                                    return@execute
                                }
                                txWithoutRxBytes = 0L
                                txWithoutRxSince = 0L
                                diag.event("HEV", "datapath-stall-held", "session" to session)
                            }
                        }

                        repo.updateTelemetry((deltaDown / dt).toLong(), (deltaUp / dt).toLong())
                    }
                    lastUp = up
                    lastDown = down
                    lastT = nowNs
                }

                if (tick % 2 == 0) {
                    TransportTelemetry.latest(xray.transportTelemetryFile)?.takeIf { it.fresh() }?.let { transport ->
                        val liveSettings = activeSettings ?: repo.settings
                        val stressed = transport.retransDelta >= 2 || transport.lost > 0 ||
                            transport.rttVarMs >= maxOf(20, transport.rttMs / 3)
                        if (stressed) routeProbeRequested.set(true)
                        if (liveSettings.adaptiveMssEnabled && transport.pmtu in 1280..9000) {
                            repo.intelligence.rememberPathMtu(activeProfileId, transport.pmtu)
                            if (activeMtu > transport.pmtu) tuningRequested.set(true)
                        }
                        if (tick % 10 == 0) diag.event("XRAY", "tcp-info", "session" to session,
                            "sockets" to transport.sockets, "rttMs" to transport.rttMs,
                            "p95RttMs" to transport.p95RttMs, "rttVarMs" to transport.rttVarMs,
                            "retransDelta" to transport.retransDelta, "lost" to transport.lost,
                            "unacked" to transport.unacked, "pmtu" to transport.pmtu,
                            "mss" to transport.mss, "cwnd" to transport.cwndPackets, "stressed" to stressed)
                    }
                }

                val routeProbeNow = routeProbeRequested.getAndSet(false)
                if (shouldSampleRoute(tick) || routeProbeNow) {
                    sampleRouteLatency(session, port, generation)
                }

                if (
                    (tick == 12 || (tick > 0 && tick % EXIT_IDENTITY_PROBE_INTERVAL_TICKS == 0)) &&
                    !verifyExitIdentity(session, port, generation)
                ) {
                    return@execute
                }

                maybeAccelerate(session, generation)
                maybeScheduleOptimizer(session, generation)

                if (tick % 5 == 0 && (activeSettings ?: repo.settings).notificationLiveStats) {
                    val name = repo.profile(activeProfileId)?.name ?: "Active route"
                    val ping = repo.livePingMs.takeIf { it > 0 }?.let { "${it} ms" } ?: "— ms"
                    notifyNow(
                        "Protected • $name",
                        true,
                        "$ping • ↓ ${SmartNotifier.formatRate(repo.liveDownBps)} • ↑ ${SmartNotifier.formatRate(repo.liveUpBps)}"
                    )
                }

                if (tick % 10 == 0) {
                    repo.refreshIntelligenceStatus()
                    updateSentinel(killSwitch = true)
                }
                tick++
            }

            if (isRouteCurrent(session, generation)) {
                repo.resetTelemetry()
            }
        }
    }

    private fun activeRouteQuality(): ActiveRouteQuality {
        val link = LinkQualityEstimator.summarize(synchronized(routeOutcomeWindow) { routeOutcomeWindow.toList() })
            ?: return ActiveRouteQuality(0, 0)
        return ActiveRouteQuality(link.medianRttMs, link.successes, link.ewmaJitterMs,
            link.p95RttMs, link.lossPercent, link.spikePercent)
    }

    private fun maybeScheduleOptimizer(session: String, generation: Int) {
        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return
        val app = application as MarbleApplication
        val repo = app.repo
        val settings = activeSettings ?: repo.settings
        if (!settings.continuousOptimizerEnabled) {
            optimizerScanRequested.set(false)
            if (settings.identityGuardEnabled) {
                repo.intelligence.setDecision(
                    "Identity Guard • session pinned • proactive exit switching disabled"
                )
            }
            return
        }
        val quality = activeRouteQuality()
        val force = optimizerScanRequested.getAndSet(false)
        val thermal = repo.intelligence.thermalBudget(settings)
        if (!routeOptimizer.shouldScan(settings, quality, thermal, repo.liveDownBps, force)) return
        if (!optimizerRunning.compareAndSet(false, true)) return
        monitorWorker.execute {
            try {
                runOptimizerCycle(session, quality, generation)
            } finally {
                optimizerRunning.set(false)
            }
        }
    }

    private fun runOptimizerCycle(session: String, quality: ActiveRouteQuality, generation: Int) {
        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return
        val app = application as MarbleApplication
        val repo = app.repo
        val active = repo.profile(activeProfileId) ?: return
        val settings = activeSettings ?: repo.settings
        if (settings.identityGuardEnabled || !settings.continuousOptimizerEnabled) return
        val all = repo.profiles.toList()
        if (all.size < 2) return
        val plan = routeOptimizer.plan(active, all, settings)
        if (plan.candidates.isEmpty()) return
        val probeSet = (listOf(active) + plan.candidates).distinctBy { it.id }
        diag.event(
            "OPTIMIZER", "cycle-start",
            "session" to session,
            "cycle" to plan.cycle,
            "deep" to plan.deep,
            "active" to active.name,
            "activePing" to quality.latencyMs,
            "challengers" to plan.candidates.size
        )
        val results = BenchmarkEngine(xray, repo.intelligence).continuousProbe(
            probeSet, settings, plan.deep
        ) { name ->
            repo.intelligence.setDecision("Autopilot cycle ${plan.cycle} • testing $name")
        }
        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return
        val decision = routeOptimizer.resolveTarget(active, all, results, settings)
        repo.intelligence.setDecision(decision.summary)
        repo.refreshIntelligenceStatus()
        val target = decision.target ?: return
        if (target.id == activeProfileId || !isRouteCurrent(session, generation)) return
        scheduleOptimizerSwitch(session, target, decision.summary, generation)
    }

    @Synchronized
    private fun scheduleOptimizerSwitch(
        session: String,
        target: ProxyProfile,
        reason: String,
        generation: Int
    ) {
        if (!isRouteCurrent(session, generation)) return
        if (!recoveryScheduled.compareAndSet(false, true)) return
        val app = application as MarbleApplication
        val repo = app.repo
        val settings = activeSettings ?: repo.settings
        if (settings.identityGuardEnabled || !settings.continuousOptimizerEnabled) {
            recoveryScheduled.set(false)
            return
        }
        val holdTun = activeMode == MODE_TUN && tun != null
        routeGeneration.incrementAndGet()
        routeOptimizer.noteSwitch()
        optimizerScanRequested.set(false)
        repo.setRuntimeState("CONNECTING", "Autopilot → ${target.name}")
        diag.event(
            "OPTIMIZER", "switch",
            "session" to session,
            "from" to activeProfileId.take(12),
            "to" to target.id.take(12),
            "target" to target.name,
            "failClosed" to holdTun,
            "reason" to reason
        )
        notifier.alert(
            SmartNotificationKind.RECOVERY,
            "autopilot:$session:${target.id}",
            "Marble Autopilot",
            if (holdTun) "Moving to ${target.name} • TUN remains fail-closed during handoff"
            else "Moving local proxy to ${target.name}",
            settings,
            minIntervalOverrideMs = 60_000L
        )
        if (hevActive) runCatching { HevTunnel.quit() }
        xray.stop()
        closeHevFd()
        repo.resetTelemetry()
        updateSentinel(killSwitch = holdTun)
        connectionWorker.execute {
            if (!isCurrent(session)) {
                recoveryScheduled.set(false)
                return@execute
            }
            recoveryScheduled.set(false)
            recoverRoute(target, session)
        }
    }

    /**
     * Connect-time acceleration must never hold the user's connection hostage.
     *
     * Any fresh acceleration plan is already folded into requested settings by effectiveSettingsFor().
     * If a node/network still needs measurement, queue that work for the healthy live tunnel instead
     * of running multiple temporary Xray processes before the real connection even starts.
     */
    private fun preTune(
        profile: ProxyProfile,
        session: String,
        requested: AppSettings,
        recovering: Boolean
    ): AppSettings {
        if (!requested.intelligenceEnabled || !requested.connectTuningEnabled) return requested
        if (!isCurrent(session)) return requested

        // A fresh connect is not a manual/urgent tuning request. The old forced flag bypassed
        // FIRST_TUNE_DELAY_MS, sample-count, thermal and heavy-traffic guards and spawned several
        // throwaway Xray processes immediately after every connection.
        diag.event(
            "TURBO", "pre-connect-deferred",
            "session" to session,
            "profile" to profile.name,
            "recovering" to recovering
        )
        return requested
    }

    /**
     * Live acceleration gate. The active route may be re-measured when degraded or on demand.
     * A better method is learned for the next reconnect; the live tunnel is never torn down.
     */
    private fun maybeAccelerate(session: String, generation: Int) {
        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return
        if (tuningRunning.get()) return

        val app = application as MarbleApplication
        val repo = app.repo
        val settings = activeSettings ?: repo.settings
        if (!settings.intelligenceEnabled || !settings.connectTuningEnabled) return

        val forced = tuningRequested.getAndSet(false)
        if (!forced && !settings.liveTuningEnabled) return

        val quality = activeRouteQuality()
        val jitterDegraded = jitterControlActive
        val degraded = quality.samples >= 3 && (
            quality.latencyMs >= settings.liveTuningPingTriggerMs.coerceIn(80, 1200) ||
                jitterDegraded
        )

        if (!forced) {
            if (System.currentTimeMillis() < tuningBackoffUntilMs) return
            if (quality.samples < 3) return
            if (sessionTuned && !degraded) return
            if (elapsedConnectMs() < FIRST_TUNE_DELAY_MS) return
            val intervalMs = settings.liveTuningIntervalSec.coerceIn(60, 3600) * 1_000L
            val sinceLast = System.currentTimeMillis() - lastTuneAt
            if (lastTuneAt > 0L && sinceLast < (if (degraded) intervalMs else intervalMs * 2L)) return
            if (repo.intelligence.thermalBudget(settings) < 0.55) return
            if (settings.optimizerAvoidHeavyTraffic && repo.liveDownBps >= HEAVY_TRAFFIC_BPS) return
        }

        if (!tuningRunning.compareAndSet(false, true)) return
        lastTuneAt = System.currentTimeMillis()
        monitorWorker.execute {
            try {
                runAccelerationCycle(session, generation, degraded || forced)
            } finally {
                tuningRunning.set(false)
            }
        }
    }

    private fun runAccelerationCycle(session: String, generation: Int, urgent: Boolean) {
        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return
        val app = application as MarbleApplication
        val repo = app.repo
        val profile = repo.profile(activeProfileId) ?: return
        val settings = activeSettings ?: repo.settings
        val base = repo.tuningBaseFor(profile)

        // Broad quick-race first; deep confirmation only for the strongest strategy.
        val budgetMs = (settings.connectTuningBudgetSec.coerceIn(2, 12) * 1_000L)
            .coerceIn(4_000L, 12_000L)
        val measureSpeed = urgent && !repo.intelligence.currentSnapshot().metered

        diag.event(
            "TURBO", "live-begin",
            "session" to session,
            "profile" to profile.name,
            "urgent" to urgent,
            "speedEvidence" to measureSpeed,
            "budgetMs" to budgetMs
        )

        val report = runCatching {
            tuner.accelerate(profile, base, budgetMs, measureSpeed) { label ->
                repo.intelligence.setDecision("Marble Turbo • ${profile.name} • $label")
            }
        }.onFailure {
            diag.error("TURBO", "live-failed", it, "session" to session)
        }.getOrNull()

        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return

        if (report == null || !report.healthy) {
            sessionTuned = true
            tuningInconclusiveStreak = (tuningInconclusiveStreak + 1).coerceAtMost(8)
            val multiplier = 1L shl (tuningInconclusiveStreak - 1).coerceIn(0, 2)
            val backoffMs =
                (TURBO_INCONCLUSIVE_BASE_BACKOFF_MS * multiplier)
                    .coerceAtMost(TURBO_INCONCLUSIVE_MAX_BACKOFF_MS)
            tuningBackoffUntilMs = System.currentTimeMillis() + backoffMs

            if (report != null) repo.intelligence.setDecision(report.summary)
            diag.event(
                "TURBO", "live-inconclusive-backoff",
                "session" to session,
                "streak" to tuningInconclusiveStreak,
                "backoffSec" to (backoffMs / 1000L),
                "jitterControl" to jitterControlActive
            )
            return
        }

        sessionTuned = true
        tuningInconclusiveStreak = 0
        tuningBackoffUntilMs = 0L

        // Compare against the method the live process is really running, not against whatever a
        // previous held pass wrote to the store.
        val appliedMethod = activeMethodId
        repo.intelligence.rememberAcceleration(profile.id, report.winner)
        repo.intelligence.setDecision(report.summary)
        repo.refreshIntelligenceStatus()

        if (report.winner.methodId == appliedMethod) {
            diag.event("TURBO", "live-confirmed", "session" to session, "method" to appliedMethod)
            return
        }

        // Restarting Xray costs a short interruption, so it has to buy a real improvement over the
        // method that is running right now — not merely over the untouched baseline.
        val appliedTrial = report.trials.firstOrNull { it.methodId == appliedMethod }
        val winnerTrial = report.trials.firstOrNull { it.methodId == report.winner.methodId }
        val improvement = when {
            appliedTrial == null || winnerTrial == null -> report.gainPercent
            appliedTrial.success <= 0 -> 100.0
            appliedTrial.score <= 0.0 -> 100.0
            else -> (winnerTrial.score - appliedTrial.score) / appliedTrial.score * 100.0
        }
        val minGain = settings.liveTuningMinGainPercent.coerceIn(5, 80).toDouble()
        if (improvement < minGain && !(urgent && improvement > 0.0)) {
            diag.event(
                "TURBO", "live-held",
                "session" to session,
                "applied" to appliedMethod,
                "candidate" to report.winner.methodId,
                "improvement" to improvement.toInt()
            )
            return
        }

        repo.intelligence.setDecision(
            "Marble Turbo • learned ${report.winner.label} • applies on next reconnect without interrupting this tunnel"
        )
        repo.refreshIntelligenceStatus()
        diag.event(
            "TURBO", "live-learned-no-restart",
            "session" to session,
            "profile" to profile.name,
            "applied" to appliedMethod,
            "learned" to report.winner.methodId,
            "improvement" to improvement.toInt()
        )
    }

    /*
     * Remote egress proof is advisory, not a startup gate. XHTTP/VLESS-ENC may reject one public
     * probe while user traffic still works; local SOCKS readiness is also not remote proof.
     * Live metrics below therefore accept certificate-verified HTTPS evidence only.
     */

    private fun scheduleSyntheticEgressObservation(
        session: String,
        generation: Int,
        port: Int,
        profile: ProxyProfile
    ) {
        timerWorker.schedule({
            if (isRouteCurrent(session, generation) && xray.isAlive) {
                monitorWorker.execute {
                    if (!isRouteCurrent(session, generation) || !xray.isAlive) return@execute
                    if (System.currentTimeMillis() < verifiedRttBackoffUntilMs) {
                        diag.event(
                            "EGRESS", "startup-observation-held-by-rtt-backoff",
                            "session" to session,
                            "profile" to profile.id.take(12)
                        )
                        return@execute
                    }
                    val app = application as MarbleApplication
                    val domainHealthy = probeDomainHttps(port)
                    val literalHealthy = domainHealthy || probeLiteralIpHttps(port)
                    if (!isRouteCurrent(session, generation) || !xray.isAlive) {
                        diag.event(
                            "EGRESS", "startup-observation-stale-discarded",
                            "session" to session,
                            "profile" to profile.id.take(12)
                        )
                        return@execute
                    }
                    diag.event(
                        "EGRESS",
                        if (domainHealthy) "startup-observation-ok" else "startup-observation-inconclusive",
                        "session" to session,
                        "literalIpHttps" to literalHealthy,
                        "domainHttps" to domainHealthy,
                        "profile" to profile.id.take(12)
                    )

                    if (!domainHealthy && literalHealthy) {
                        val liveSettings = activeSettings ?: app.repo.settings
                        if (
                            liveSettings.intelligenceEnabled &&
                            liveSettings.connectTuningEnabled
                        ) {
                            tuningRequested.set(true)
                            diag.event(
                                "TURBO", "startup-family-tune-requested",
                                "session" to session,
                                "profile" to profile.id.take(12),
                                "reason" to "literal-ip-ok-domain-inconclusive"
                            )
                        }
                    }
                    if (!domainHealthy && literalHealthy) {
                        app.repo.intelligence.setDecision(
                            "DNS health probe inconclusive • route remains connected and monitored"
                        )
                        app.repo.refreshIntelligenceStatus()
                    }
                }
            }
        }, 20_000L, TimeUnit.MILLISECONDS)
    }

    private fun scheduleAdaptiveDnsObservation(
        session: String,
        generation: Int,
        port: Int,
        settings: AppSettings,
        delayMs: Long = 15_000L
    ) {
        timerWorker.schedule({
            if (isRouteCurrent(session, generation) && xray.isAlive) {
                val repo = (application as MarbleApplication).repo
                val throughput = repo.liveDownBps + repo.liveUpBps
                if (throughput < HEAVY_TRAFFIC_BPS) {
                    monitorWorker.execute {
                        if (isRouteCurrent(session, generation) && xray.isAlive) {
                            runCatching { repo.intelligence.probeDnsResolvers(port, settings) }
                        }
                    }
                } else {
                    diag.event(
                        "DNS", "adaptive-observation-deferred",
                        "session" to session,
                        "jitterControl" to jitterControlActive,
                        "throughputBps" to throughput
                    )
                    // A one-shot defer meant resolver learning never ran on long downloads. Retry
                    // later without interrupting the active flow or restarting Xray.
                    scheduleAdaptiveDnsObservation(session, generation, port, settings, 30_000L)
                }
            }
        }, delayMs.coerceIn(5_000L, 60_000L), TimeUnit.MILLISECONDS)
    }

    /**
     * The SOCKS CONNECT destination stays literal (DNS-free), while TLS verification/SNI uses the
     * provider's real certificate hostname. Using the IP as both transport address and TLS name is
     * rejected by some anycast/CDN edges and was a source of false "RTT unavailable" results.
     */
    private fun probeTlsHost(host: String): String = when (host) {
        "1.1.1.1", "1.0.0.1" -> "cloudflare-dns.com"
        "8.8.8.8" -> "dns.google"
        "9.9.9.9" -> "dns.quad9.net"
        else -> host
    }

    private fun probeLiteralIpHttps(port: Int): Boolean {
        // No single provider is authoritative. The attached log shows a healthy tunnel while one
        // public DoH/HTTPS anycast target was unreachable, so recovery confirmation races a small
        // provider-diverse literal set without invoking Android/system DNS.
        return JITTER_PROBE_TARGETS.take(3).any { (host, path) ->
            runCatching {
                SocksHttpClient.httpsFirstByteLatency(
                    port = port,
                    host = host,
                    path = path,
                    timeoutMs = 1_500,
                    tlsHost = probeTlsHost(host)
                ) > 0.0
            }.getOrDefault(false)
        }
    }

    private fun probeDomainHttps(port: Int): Boolean =
        LIVE_DOMAIN_RTT_TARGETS.take(2).any { (host, path) ->
            runCatching {
                SocksHttpClient.get(
                    port,
                    host,
                    path,
                    3_500,
                    1_024
                ).status in 200..399
            }.getOrDefault(false)
        }

    /**
     * Confirms a synthetic route failure against independent HTTPS endpoints.
     * A single blocked connectivity-check domain must never tear down a healthy tunnel.
     */
    private fun confirmRouteUnavailable(
        session: String,
        port: Int,
        generation: Int
    ): Boolean {
        if (!isRouteCurrent(session, generation)) return false
        if (!xray.isAlive) return true
        if (activeMode == MODE_TUN && !hevActive) return true

        if (probeLiteralIpHttps(port)) {
            diag.event(
                "ROUTE", "transport-alive-dns-suspect",
                "session" to session,
                "profile" to activeProfileId.take(12)
            )
            return false
        }

        val confirmations = arrayOf(
            "www.cloudflare.com" to "/cdn-cgi/trace",
            "www.google.com" to "/generate_204"
        )
        confirmations.forEach { (host, path) ->
            if (!isRouteCurrent(session, generation)) return false
            val healthy = runCatching {
                SocksHttpClient.get(port, host, path, ROUTE_CONFIRM_TIMEOUT_MS, 2_048).status in 200..399
            }.getOrDefault(false)
            if (healthy) return false
        }
        return isRouteCurrent(session, generation) &&
            xray.isAlive &&
            (activeMode != MODE_TUN || hevActive)
    }

    /**
     * Live route timing is deliberately different from a health check.
     *
     * The meter pins each burst to one literal HTTPS endpoint, so origin/DNS variation never gets
     * mislabeled as jitter. Provider rotation happens only after a target produces no verified
     * sample; the new target starts a fresh IPDV baseline.
     */
    private fun shouldSampleRoute(tick: Int): Boolean {
        // Three sparse warm-up samples establish a baseline without a burst immediately on connect.
        if (tick == 2 || tick == 5 || tick == 9) return true

        val repo = (application as MarbleApplication).repo
        val throughput = repo.liveDownBps + repo.liveUpBps
        val cadence = when {
            throughput >= HEAVY_TRAFFIC_BPS -> ROUTE_HEAVY_PROBE_TICKS
            jitterControlActive -> ROUTE_DEGRADED_PROBE_TICKS
            else -> ROUTE_PROBE_INTERVAL_TICKS
        }
        return tick > 0 && tick % cadence == 0
    }

    private fun resetJitterBaselineForProbeHost(nextHost: String) {
        synchronized(routeOutcomeWindow) { routeOutcomeWindow.clear() }
        jitterHighStreak = 0
        jitterLowStreak = 0
        jitterControlActive = false
        jitterProbeHost = nextHost
        (application as MarbleApplication).repo.invalidateLiveJitter()
    }

    private fun sampleRouteLatency(
        session: String,
        port: Int,
        generation: Int
    ): Boolean {
        if (!isRouteCurrent(session, generation)) return false

        val app = application as MarbleApplication
        val repo = app.repo

        /*
         * v31 keeps one same-target rolling RTT/IPDV window and streams every verified result.
         * The first valid RTT publishes Ping + Quality immediately; the second valid RTT adds
         * the first real jitter delta. Later samples refine the same rolling metric.
         *
         * Jitter is mean absolute consecutive RTT variation (IPDV) on one literal-IP target.
         * It includes real spikes instead of deleting the largest delta, while the bounded rolling
         * window prevents one ancient spike from contaminating the value forever.
         */
        fun publishRollingOutcome(host: String, sample: Int) {
            if (!isRouteCurrent(session, generation) || host != jitterProbeHost) return

            val snapshot = synchronized(routeOutcomeWindow) {
                routeOutcomeWindow.addLast(if (sample > 0) sample.coerceIn(1, 10_000) else -1)
                while (routeOutcomeWindow.size > ROUTE_WINDOW_SIZE) routeOutcomeWindow.removeFirst()
                val outcomes = routeOutcomeWindow.toList()
                val link = LinkQualityEstimator.summarize(outcomes)
                    ?: return@synchronized intArrayOf(0, -1, 0, 0, outcomes.size, 0, 0)
                intArrayOf(link.medianRttMs, link.ewmaJitterMs, link.successes, link.jitterSamples,
                    link.attempts, link.successPercent, link.p90RttMs)
            }

            if (snapshot[0] <= 0) return
            repo.updateRouteQuality(
                snapshot[0],
                snapshot[1],
                snapshot[2],
                snapshot[3],
                snapshot[4],
                snapshot[5],
                snapshot[6]
            )
        }

        fun probePath(host: String): String =
            JITTER_PROBE_TARGETS.firstOrNull { it.first == host }?.second ?: "/dns-query"

        fun probeLabel(host: String): String = when (host) {
            "1.1.1.1", "1.0.0.1" -> "Cloudflare"
            "8.8.8.8" -> "Google"
            "9.9.9.9" -> "Quad9"
            else -> "HTTPS"
        }

        fun measureOne(host: String): Int =
            runCatching {
                SocksHttpClient.httpsFirstByteLatency(
                    port = port,
                    host = host,
                    path = probePath(host),
                    targetPort = 443,
                    timeoutMs = LIVE_RTT_TIMEOUT_MS,
                    tlsHost = probeTlsHost(host)
                ).roundToInt()
            }.getOrDefault(-1)

        fun measurePinnedBurst(host: String, firstSample: Int): List<Int> {
            val values = ArrayList<Int>(LIVE_RTT_BURST_SAMPLES)

            fun accept(value: Int): Boolean {
                if (value <= 0) {
                    publishRollingOutcome(host, -1)
                    return false
                }
                val verified = value.coerceIn(1, 10_000)
                values += verified
                // Ping + Quality become visible after this first verified response; Jitter appears
                // after the next same-target response instead of waiting for the entire burst.
                publishRollingOutcome(host, verified)
                return true
            }

            if (!accept(firstSample)) return values
            while (values.size < LIVE_RTT_BURST_SAMPLES && isRouteCurrent(session, generation)) {
                try {
                    Thread.sleep(LIVE_RTT_BURST_GAP_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                if (!accept(measureOne(host))) break
            }
            return values
        }

        fun measureDomainBurst(host: String, path: String): List<Int> =
            runCatching {
                SocksHttpClient.tunnelRttBatch(
                    port = port,
                    host = host,
                    path = path,
                    samples = LIVE_RTT_BURST_SAMPLES,
                    timeoutMs = LIVE_DOMAIN_RTT_TIMEOUT_MS
                )
            }.getOrNull()
                ?.samplesMs
                ?.filter { it.isFinite() && it > 0.0 }
                ?.map { it.roundToInt().coerceIn(1, 10_000) }
                .orEmpty()

        var host = jitterProbeHost
        var rttSamples = emptyList<Int>()
        val nowMs = System.currentTimeMillis()

        if (nowMs < verifiedRttBackoffUntilMs) {
            val retrySec = ((verifiedRttBackoffUntilMs - nowMs + 999L) / 1_000L)
                .coerceAtLeast(1L)
            repo.updateRouteProbeStatus(
                "Verified HTTPS RTT unavailable • retry in ${retrySec}s • no estimate substituted"
            )
            return false
        }

        repo.updateRouteProbeStatus("Measuring verified RTT • ${probeLabel(host)}")
        val primarySample = measureOne(host)
        rttSamples = if (primarySample > 0) {
            measurePinnedBurst(host, primarySample)
        } else {
            publishRollingOutcome(host, -1)
            emptyList()
        }

        if (rttSamples.isEmpty() && isRouteCurrent(session, generation)) {
            val alternates = JITTER_PROBE_TARGETS
                .map { it.first }
                .filterNot { it == host }
                .take(2)

            for ((index, alternate) in alternates.withIndex()) {
                repo.updateRouteProbeStatus(
                    "${probeLabel(host)} RTT unavailable • trying ${probeLabel(alternate)} " +
                        "${index + 1}/${alternates.size}"
                )
                val alternateSample = measureOne(alternate)
                if (alternateSample <= 0) continue

                resetJitterBaselineForProbeHost(alternate)
                host = alternate
                rttSamples = measurePinnedBurst(alternate, alternateSample)
                diag.event(
                    "ROUTE", "jitter-probe-pivot",
                    "session" to session,
                    "target" to alternate,
                    "provider" to probeLabel(alternate),
                    "reason" to "previous literal HTTPS target produced zero verified RTTs"
                )
                break
            }
        }

        // If literal HTTPS is unavailable, use the same class of full HTTPS-through-SOCKS
        // measurement used by Smart Rank. Domain ATYP stays inside Xray and does not leak to
        // Android/system DNS.
        if (rttSamples.isEmpty() && isRouteCurrent(session, generation)) {
            val domainTargets = LIVE_DOMAIN_RTT_TARGETS.take(2)
            for ((index, target) in domainTargets.withIndex()) {
                repo.updateRouteProbeStatus(
                    "Literal HTTPS RTT unavailable • verified domain probe " +
                        "${index + 1}/${domainTargets.size}"
                )
                val batch = measureDomainBurst(target.first, target.second)
                if (batch.isEmpty()) continue

                resetJitterBaselineForProbeHost(target.first)
                host = target.first
                batch.forEach { sample -> publishRollingOutcome(host, sample) }
                rttSamples = batch
                diag.event(
                    "ROUTE", "jitter-probe-domain-fallback",
                    "session" to session,
                    "target" to target.first,
                    "samples" to batch.size,
                    "reason" to "literal providers unavailable; full HTTPS domain probe verified"
                )
                break
            }
        }

        if (rttSamples.isNotEmpty()) {
            verifiedRttBackoffUntilMs = 0L
        } else {
            verifiedRttBackoffUntilMs = nowMs + VERIFIED_RTT_BACKOFF_MS
            repo.updateRouteProbeStatus(
                "Verified HTTPS RTT unavailable • retrying in 60s • no synthetic ping shown"
            )
        }

        if (rttSamples.isEmpty() || !isRouteCurrent(session, generation)) {
            if (isRouteCurrent(session, generation)) {
                repo.updateRouteProbeStatus(
                    "Tunnel traffic is active • verified HTTPS RTT is currently unavailable"
                )
            }
            // Keep the last verified rolling metrics across an advisory miss. A host pivot,
            // disconnect, generation reset, or real recovery still resets them explicitly.
            val trafficRecentlyMoved =
                activeMode == MODE_TUN &&
                    hevActive &&
                    (System.currentTimeMillis() - lastTrafficProgressAt) in
                        0L..RECENT_TRAFFIC_GRACE_MS

            if (trafficRecentlyMoved) {
                consecutiveProbeFailures = 0
                diag.event(
                    "ROUTE", "probe-advisory-miss-held",
                    "session" to session,
                    "trafficRecent" to true,
                    "xrayAlive" to xray.isAlive,
                    "hevActive" to hevActive
                )
                return false
            }

            if (!isRouteCurrent(session, generation)) {
                diag.event(
                    "ROUTE", "probe-result-stale-discarded",
                    "session" to session,
                    "generation" to generation
                )
                return false
            }

            val routeAgeMs = (
                (System.nanoTime() - connectStartedNs) / 1_000_000L
            ).coerceAtLeast(0L)
            if (routeAgeMs < ROUTE_FAILURE_WARMUP_MS) {
                consecutiveProbeFailures = 0
                diag.event(
                    "ROUTE", "probe-warmup-miss-held",
                    "session" to session,
                    "ageMs" to routeAgeMs,
                    "warmupMs" to ROUTE_FAILURE_WARMUP_MS,
                    "profile" to activeProfileId.take(12)
                )
                return false
            }

            consecutiveProbeFailures++
            diag.event(
                "ROUTE", "probe-failed",
                "session" to session,
                "count" to consecutiveProbeFailures,
                "profile" to activeProfileId.take(12)
            )

            val settings = activeSettings ?: repo.settings
            val recoveryEnabled =
                settings.smartFallbackEnabled || settings.networkChangeRecoveryEnabled

            if (recoveryEnabled && consecutiveProbeFailures >= PROBE_FAILURES_BEFORE_RECOVERY) {
                val confirmedUnavailable = when {
                    !xray.isAlive -> true
                    activeMode == MODE_TUN && !hevActive -> true
                    trafficRecentlyMoved -> false
                    else -> confirmRouteUnavailable(session, port, generation)
                }
                if (confirmedUnavailable) {
                    handleFailure(
                        session,
                        "Route unavailable after $consecutiveProbeFailures spaced probes + confirmation"
                    )
                } else {
                    consecutiveProbeFailures =
                        (PROBE_FAILURES_BEFORE_RECOVERY - 1).coerceAtLeast(1)
                    diag.event(
                        "ROUTE", "probe-failure-held",
                        "session" to session,
                        "trafficRecent" to trafficRecentlyMoved,
                        "xrayAlive" to xray.isAlive,
                        "hevActive" to hevActive
                    )
                }
            }
            return false
        }

        consecutiveProbeFailures = 0
        identityRecoveryAttempts = 0

        val rolling = synchronized(routeOutcomeWindow) { routeOutcomeWindow.toList() }
        val link = LinkQualityEstimator.summarize(rolling) ?: return false
        val jitterSampleCount = link.jitterSamples
        val attemptCount = link.attempts
        val successPercent = link.successPercent
        val tailLatencyMs = link.p90RttMs
        val jitterMs = link.ewmaJitterMs

        val quality = ActiveRouteQuality(
            latencyMs = link.medianRttMs, samples = link.successes, jitterMs = jitterMs,
            p95LatencyMs = link.p95RttMs, lossPercent = link.lossPercent, spikePercent = link.spikePercent
        )

        repo.updateRouteQuality(
            quality.latencyMs,
            jitterMs,
            quality.samples,
            jitterSampleCount,
            attemptCount,
            successPercent,
            tailLatencyMs
        )
        diag.event(
            "ROUTE", "latency-sample",
            "session" to session,
            "target" to host,
            "rawMs" to rttSamples.last(),
            "pingMs" to quality.latencyMs,
            "jitterMs" to jitterMs,
            "samples" to quality.samples,
            "jitterSamples" to jitterSampleCount,
            "attempts" to attemptCount,
            "successPercent" to successPercent,
            "successLowerBoundPercent" to link.successLowerBoundPercent,
            "p90Ms" to tailLatencyMs, "p95Ms" to link.p95RttMs,
            "medianIpdvMs" to link.medianIpdvMs, "p95IpdvMs" to link.p95IpdvMs,
            "madMs" to link.madRttMs, "lossPercent" to link.lossPercent,
            "spikePercent" to link.spikePercent,
            "method" to "verified-https-ttfb-robust-ipdv-loss-tail"
        )

        repo.intelligence.recordLiveRoute(
            activeProfileId,
            quality.latencyMs,
            repo.liveDownBps + repo.liveUpBps,
            jitterMs,
            successPercent,
            activeSettings ?: repo.settings
        )

        val highThreshold = maxOf(
            ROUTE_JITTER_TRIGGER_MS,
            (quality.latencyMs / 4).coerceAtLeast(1)
        )
        val releaseThreshold = maxOf(
            ROUTE_JITTER_RELEASE_MS,
            (quality.latencyMs / 8).coerceAtLeast(1)
        )
        val jitterReady = jitterMs >= 0 && jitterSampleCount >= 2
        val tailJitterHigh = link.p95IpdvMs >= maxOf(highThreshold * 2, 35)
        val instabilityHigh = link.lossPercent >= 15 || link.spikePercent >= 25
        val instantHigh = quality.samples >= 3 && jitterReady &&
            (jitterMs >= highThreshold || tailJitterHigh || instabilityHigh)
        val instantLow = quality.samples >= 3 && jitterReady && jitterMs <= releaseThreshold &&
            link.p95IpdvMs <= maxOf(releaseThreshold * 2, 20) && link.lossPercent <= 5 && link.spikePercent <= 10

        when {
            instantHigh -> {
                jitterHighStreak = (jitterHighStreak + 1).coerceAtMost(1000)
                jitterLowStreak = 0
            }
            instantLow -> {
                jitterLowStreak = (jitterLowStreak + 1).coerceAtMost(1000)
                jitterHighStreak = (jitterHighStreak - 1).coerceAtLeast(0)
            }
            else -> {
                jitterLowStreak = 0
                jitterHighStreak = (jitterHighStreak - 1).coerceAtLeast(0)
            }
        }

        if (!jitterControlActive && jitterHighStreak >= JITTER_HIGH_CONFIRMATIONS) {
            jitterControlActive = true
            diag.event(
                "ROUTE", "jitter-control-enter",
                "session" to session,
                "target" to host,
                "pingMs" to quality.latencyMs,
                "jitterMs" to jitterMs,
                "highStreak" to jitterHighStreak
            )
        } else if (jitterControlActive && jitterLowStreak >= JITTER_RELEASE_CONFIRMATIONS) {
            jitterControlActive = false
            jitterHighStreak = 0
            diag.event(
                "ROUTE", "jitter-control-exit",
                "session" to session,
                "target" to host,
                "pingMs" to quality.latencyMs,
                "jitterMs" to jitterMs,
                "lowStreak" to jitterLowStreak
            )
        }

        val settings = activeSettings ?: repo.settings
        if (
            jitterControlActive &&
            settings.continuousOptimizerEnabled &&
            !settings.identityGuardEnabled
        ) {
            val now = System.currentTimeMillis()
            if (now - lastJitterOptimizerRequestAt >= JITTER_OPTIMIZER_COOLDOWN_MS) {
                lastJitterOptimizerRequestAt = now
                optimizerScanRequested.set(true)
                diag.event(
                    "ROUTE", "jitter-optimizer-request",
                    "session" to session,
                    "pingMs" to quality.latencyMs,
                    "jitterMs" to jitterMs,
                    "samples" to quality.samples
                )
            }
        }
        return true
    }

    /**
     * Pins observed public egress independently for IPv4 and IPv6. A provider can legitimately
     * expose one stable address per family; only a change inside the SAME family counts as rotation.
     *
     * The observation itself travels through Xray SOCKS. No direct physical-network IP lookup is sent.
     * Failure to observe an address is inconclusive and does not tear down an otherwise healthy route.
     */
    private fun verifyExitIdentity(
        session: String,
        port: Int,
        generation: Int
    ): Boolean {
        if (!isRouteCurrent(session, generation)) return false

        val trace =
            runCatching {
                val response =
                    SocksHttpClient.get(
                        port,
                        "www.cloudflare.com",
                        "/cdn-cgi/trace",
                        5_000,
                        8_192
                    )
                if (response.status !in 200..399) "" else String(response.body)
            }.getOrDefault("")

        val observed =
            Regex("(?m)^ip=([^\\r\\n]+)$")
                .find(trace)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()

        if (observed.isBlank()) return true
        if (!isRouteCurrent(session, generation)) return false

        val isV6 = observed.contains(':')
        val pinned = if (isV6) pinnedExitV6 else pinnedExitV4
        val settings = activeSettings ?: (application as MarbleApplication).repo.settings

        if (
            pinned.isNotBlank() &&
            pinned != observed &&
            settings.identityGuardEnabled &&
            settings.identityGuardStrictNoFailover
        ) {
            diag.event(
                "IDENTITY",
                "exit-rotation-blocked",
                "session" to session,
                "family" to if (isV6) "ipv6" else "ipv4",
                "fromHash" to Integer.toHexString(pinned.hashCode()),
                "toHash" to Integer.toHexString(observed.hashCode())
            )
            handleFailure(
                session,
                "Identity Guard detected public exit-IP rotation"
            )
            return false
        }

        if (isV6) {
            pinnedExitV6 = observed
        } else {
            pinnedExitV4 = observed
        }

        val repo = (application as MarbleApplication).repo
        val label =
            listOf(
                pinnedExitV4.takeIf { it.isNotBlank() }?.let { "IPv4 $it" },
                pinnedExitV6.takeIf { it.isNotBlank() }?.let { "IPv6 $it" }
            ).filterNotNull().joinToString(" • ")

        repo.updateSentinel(
            repo.sentinel.copy(
                exitIp = label,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    private fun onUnderlyingNetworkChanged(
        snapshot: NetworkSnapshot
    ) {
        val app =
            application as
                MarbleApplication

        app.repo
            .refreshIntelligenceStatus()

        if (
            Build.VERSION.SDK_INT >=
            22 &&
            tun != null
        ) {
            app.repo
                .intelligence
                .currentUnderlyingNetwork()
                ?.let {
                    network ->
                    runCatching {
                        setUnderlyingNetworks(
                            arrayOf(
                                network
                            )
                        )
                    }
                }
        }

        val key =
            snapshot.key()

        val meaningful =
            key !=
                lastNetworkKey ||
                snapshot.validated !=
                    lastNetworkValidated

        lastNetworkKey =
            key
        lastNetworkValidated =
            snapshot.validated

        if (
            !meaningful ||
            !running.get()
        ) {
            return
        }

        // Connectivity callbacks briefly publish UNKNOWN/NO IP while Android hands a cellular or
        // Wi-Fi network over. Treat that snapshot as a transient gap, not as proof the Xray route
        // failed. HEV/Xray liveness and the datapath-stall detector still fail closed on real loss.
        if (!snapshot.hasIpv4 && !snapshot.hasIpv6) {
            routeProbeRequested.set(false)
            optimizerScanRequested.set(false)
            diag.event(
                "NETWORK",
                "underlay-transient-no-ip",
                "session" to activeSession,
                "network" to snapshot.label
            )
            return
        }

        val settings =
            activeSettings
                ?: app.repo.settings

        if (
            settings
                .networkChangeRecoveryEnabled
        ) {
            synchronized(routeOutcomeWindow) { routeOutcomeWindow.clear() }
            dataStallGuard.reset()
            jitterProbeHost = JITTER_PRIMARY_HOST
            jitterHighStreak = 0
            jitterLowStreak = 0
            jitterControlActive = false
            tuningInconclusiveStreak = 0
            tuningBackoffUntilMs = 0L
            routeOptimizer.reset(0L)
            optimizerScanRequested.set(
                settings.continuousOptimizerEnabled && !settings.identityGuardEnabled
            )
            routeProbeRequested
                .set(true)

            diag.event(
                "NETWORK",
                "underlay-change",
                "session" to
                    activeSession,
                "network" to
                    snapshot.label
            )

            notifier.alert(
                SmartNotificationKind.NETWORK,
                "underlay:$key:" +
                    "${snapshot.validated}",
                "Network changed",
                "${snapshot.label} • " +
                    "validating the active Xray route",
                settings,
                minIntervalOverrideMs =
                    60_000L
            )
        }
    }

    /**
     * Stop forwarding but keep TUN captured. If configured, schedule bounded fallback while the fd
     * remains established. This preserves the kill switch during route changes and core crashes.
     */
    @Synchronized
    private fun handleFailure(
        session: String,
        reason: String
    ) {
        if (
            session.isNotBlank() &&
            activeSession.isNotBlank() &&
            session != activeSession
        ) {
            return
        }

        if (
            recoveryScheduled.get() &&
            reason.startsWith(
                "HEV stopped"
            )
        ) {
            return
        }

        val app =
            application as
                MarbleApplication
        val repo =
            app.repo
        val settings =
            activeSettings
                ?: repo.settings
        val failedId =
            activeProfileId
        val holdTun =
            activeMode ==
                MODE_TUN &&
                tun != null

        val invalidatedGeneration = routeGeneration.incrementAndGet()
        diag.event(
            "VPN",
            "failure-generation",
            "session" to activeSession,
            "generation" to invalidatedGeneration
        )

        diag.event(
            "VPN",
            "blocked",
            "session" to
                activeSession,
            "mode" to
                activeMode,
            "reason" to
                reason,
            "killSwitchHold" to
                holdTun,
            "xrayAlive" to
                xray.isAlive,
            "hevFd" to
                hevFd,
            "tunOpen" to
                (tun != null),
            "profile" to
                failedId
                    .take(12)
        )

        if (hevActive) {
            runCatching {
                HevTunnel.quit()
            }
        }

        hevActive =
            false
        xray.stop()
        closeHevFd()
        repo.resetTelemetry()

        // Exactly one historical failure update for one connection incident.
        // A damaged/locked history DB must never crash the failure handler itself.
        runCatching {
            repo.intelligence.recordConnect(
                failedId,
                false,
                elapsedConnectMs(),
                settings
            )
        }.onFailure { error ->
            diag.error(
                "VPN",
                "failure-history-write-skipped",
                error,
                "profile" to failedId.take(12)
            )
        }

        updateSentinel(
            killSwitch =
                holdTun
        )

        if (activeMode == MODE_TUN && !holdTun) {
            recoveryScheduled.set(false)
            running.set(false)
            closeTun()
            repo.setRuntimeState(
                "BLOCKED",
                "VPN interface unavailable • $reason"
            )
            diag.event(
                "VPN",
                "full-tun-missing-stop",
                "session" to activeSession,
                "reason" to reason
            )
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }

        if (holdTun) {
            running.set(true)

            repo.setRuntimeState(
                "BLOCKED",
                "Kill switch active • " +
                    reason
            )

            promoteForeground(
                "BLOCKED • " +
                    "Kill switch holding traffic",
                ongoing = true
            )

            notifier.alert(
                SmartNotificationKind.PRIVACY,
                "blocked:" +
                    "${activeSession}:" +
                    failedId,
                "Traffic blocked safely",
                "Kill switch is holding traffic • " +
                    reason,
                settings,
                minIntervalOverrideMs =
                    60_000L
            )
        }

        // A startup timeout is terminal for this user attempt. Do not turn one hung connect into
        // an unbounded chain of Identity Guard retries or Smart Fallback candidates. In Full TUN
        // the TUN fd stays open as a fail-closed blackhole until the user retries or disconnects.
        if (startupTimedOut.get()) {
            recoveryScheduled.set(false)
            tunReadyPublished.set(false)

            if (holdTun) {
                running.set(false)
                repo.setRuntimeState("BLOCKED", "Kill switch active • $reason")
                promoteForeground("BLOCKED • Startup timed out • tap Retry", ongoing = true)
                diag.event(
                    "VPN", "startup-timeout-held",
                    "session" to activeSession,
                    "profile" to failedId.take(12)
                )
                return
            }

            running.set(false)
            closeTun()
            repo.setRuntimeState("BLOCKED", reason)
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }

        val failedBeforeFirstReady = !tunReadyPublished.get()
        if (
            failedBeforeFirstReady &&
            settings.identityGuardEnabled &&
            settings.identityGuardStrictNoFailover
        ) {
            recoveryScheduled.set(false)
            diag.event(
                "IDENTITY",
                "startup-failure-no-identity-loop",
                "session" to activeSession,
                "profile" to failedId.take(12),
                "reason" to reason
            )

            if (holdTun) {
                running.set(false)
                repo.setRuntimeState(
                    "BLOCKED",
                    "Startup failed • Kill switch active • $reason"
                )
                promoteForeground(
                    "BLOCKED • Startup failed • tap Retry",
                    ongoing = true
                )
                return
            }

            running.set(false)
            repo.setRuntimeState("BLOCKED", "Startup failed • $reason")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return
        }

        if (holdTun && !settings.autoReconnectAfterKillSwitch) {
            recoveryScheduled.set(false)
            repo.setRuntimeState("BLOCKED", "Kill switch active • manual reconnect required")
            promoteForeground("BLOCKED • Tap Connect to retry", ongoing = true)
            notifier.alert(
                SmartNotificationKind.PRIVACY,
                "manual-hold:${activeSession}:$failedId",
                "Kill switch is holding traffic",
                "Automatic reconnect is disabled • tap Connect when you want MarbleNG to retry",
                settings,
                minIntervalOverrideMs = 60_000L
            )
            return
        }

        // Identity Guard keeps the public exit pinned. It may restart the SAME profile
        // while Full TUN remains fail-closed, but it never silently selects another profile/IP.
        if (
            settings.identityGuardEnabled &&
            settings.identityGuardStrictNoFailover
        ) {
            val pinned =
                repo.profile(failedId, activeProfileSourceId)

            val maxRetries =
                settings
                    .identityGuardSameRouteRetries
                    .coerceIn(0, 5)

            if (
                pinned != null &&
                identityRecoveryAttempts < maxRetries &&
                recoveryScheduled.compareAndSet(false, true)
            ) {
                identityRecoveryAttempts++
                val attempt =
                    identityRecoveryAttempts
                val delayMs =
                    (750L * attempt)
                        .coerceAtMost(3_000L)

                running.set(true)
                repo.setRuntimeState(
                    "CONNECTING",
                    "Identity Guard • retry $attempt/$maxRetries • ${pinned.name}"
                )
                promoteForeground(
                    "Identity Guard • recovering pinned route",
                    ongoing = true
                )

                notifier.alert(
                    SmartNotificationKind.RECOVERY,
                    "identity-retry:${activeSession}:$failedId:$attempt",
                    "Identity Guard",
                    if (holdTun) {
                        "Retrying the same route • Full TUN remains fail-closed • public IP will not be rotated"
                    } else {
                        "Retrying the same local-proxy route • public exit will not be rotated"
                    },
                    settings,
                    minIntervalOverrideMs = 60_000L
                )

                diag.event(
                    "IDENTITY",
                    "same-route-retry",
                    "session" to activeSession,
                    "profile" to failedId.take(12),
                    "attempt" to attempt,
                    "maxRetries" to maxRetries,
                    "failClosed" to holdTun
                )

                connectionWorker.execute {
                    if (delayMs > 0L) {
                        try {
                            Thread.sleep(delayMs)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }

                    if (!isCurrent(session)) {
                        recoveryScheduled.set(false)
                        return@execute
                    }

                    recoveryScheduled.set(false)
                    recoverRoute(
                        pinned,
                        session
                    )
                }
                return
            }

            recoveryScheduled.set(false)

            diag.event(
                "IDENTITY",
                "pinned-route-held",
                "session" to activeSession,
                "profile" to failedId.take(12),
                "attempts" to identityRecoveryAttempts,
                "reason" to reason,
                "failClosed" to holdTun
            )

            if (holdTun) {
                running.set(true)
                repo.setRuntimeState(
                    "BLOCKED",
                    "Identity Guard • pinned route unavailable • public IP not changed"
                )
                promoteForeground(
                    "BLOCKED • Identity Guard holding traffic",
                    ongoing = true
                )
                notifier.alert(
                    SmartNotificationKind.PRIVACY,
                    "identity-hold:${activeSession}:$failedId",
                    "Identity Guard is holding traffic",
                    "Pinned exit is unavailable • traffic stays blocked instead of switching to another public IP",
                    settings,
                    minIntervalOverrideMs = 60_000L
                )
                return
            }

            running.set(false)
            repo.setRuntimeState(
                "BLOCKED",
                "Identity Guard • pinned local-proxy route unavailable"
            )
            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
            stopSelf()
            return
        }

        if (
            settings.smartFallbackEnabled &&
            recoveryScheduled
                .compareAndSet(
                    false,
                    true
                )
        ) {
            synchronized(
                recoveryTried
            ) {
                recoveryTried +=
                    failedId
            }

            val tried =
                synchronized(
                    recoveryTried
                ) {
                    recoveryTried
                        .toSet()
                }

            val next =
                repo.recoveryCandidates(
                    tried
                ).firstOrNull()

            if (
                next != null &&
                tried.size <=
                    settings
                        .fallbackCount
                        .coerceIn(
                            1,
                            8
                        ) +
                        1
            ) {
                synchronized(
                    recoveryTried
                ) {
                    recoveryTried +=
                        next.id
                }

                running.set(true)

                repo.setRuntimeState(
                    "CONNECTING",
                    "Failover → " +
                        next.name
                )

                promoteForeground(
                    "Recovering • " +
                        next.name,
                    ongoing = true
                )

                notifier.alert(
                    SmartNotificationKind.RECOVERY,
                    "failover:" +
                        "${activeSession}:" +
                        next.id,
                    "Smart fallback",
                    if (holdTun) {
                        "Switching to ${next.name} " +
                            "while Full TUN stays fail-closed"
                    } else {
                        "Local proxy route failed • " +
                            "switching to ${next.name}"
                    },
                    settings
                )

                diag.event(
                    "VPN",
                    "fallback-selected",
                    "from" to
                        failedId
                            .take(12),
                    "to" to
                        next.id
                            .take(12)
                )

                connectionWorker
                    .execute {
                        // Clear before the candidate starts so a failed fallback can advance to
                        // the next bounded candidate.
                        recoveryScheduled
                            .set(false)

                        recoverRoute(
                            next,
                            session
                        )
                    }

                return
            }

            recoveryScheduled
                .set(false)
        }

        if (holdTun) {
            // No fallback survived. Keep the established TUN open as a fail-closed blackhole.
            running.set(true)
            return
        }

        running.set(false)
        closeTun()

        repo.setRuntimeState(
            "BLOCKED",
            reason
        )

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun recoverRoute(
        profile: ProxyProfile,
        session: String
    ) {
        val app =
            application as
                MarbleApplication

        if (!isCurrent(session)) {
            return
        }

        if (
            activeMode ==
                MODE_TUN &&
            tun == null
        ) {
            return
        }

        val settings =
            app.repo
                .effectiveSettingsFor(
                    profile
                )

        activeProfileId =
            profile.id
        activeProfileSourceId =
            profile.subscriptionId
        activeSettings =
            settings
        consecutiveProbeFailures =
            0

        synchronized(routeOutcomeWindow) { routeOutcomeWindow.clear() }

        val port =
            if (
                activeMode ==
                MODE_PROXY
            ) {
                settings
                    .localProxyPort
            } else {
                settings
                    .socksPort
            }

        startXrayAndForward(
            profile,
            session,
            port,
            settings,
            recovering = true
        )
    }

    private fun updateSentinel(killSwitch: Boolean) {
        val app = application as MarbleApplication
        val repo = app.repo
        val settings = activeSettings ?: repo.settings
        repo.updateSentinel(
            repo.intelligence.buildSentinel(
                settings = settings,
                mode = activeMode,
                tunUp = tun != null,
                ipv6RouteCaptured = ipv6RouteCaptured,
                xrayUp = xray.isAlive,
                hevUp = hevActive,
                killSwitchArmed = killSwitch,
                previous = repo.sentinel
            )
        )
    }

    /**
     * Fail fast for a configuration Xray itself treats as permanently invalid.
     *
     * Recent Xray releases reject public VLESS with both stream security=none and
     * encryption=none. Starting Android TUN first used to create a kill-switch hold and then
     * retry the same impossible profile. Private/LAN endpoints remain allowed.
     */
    private fun profileCompatibilityIssue(profile: ProxyProfile): String? =
        runCatching {
            val root = JSONObject(profile.configJson)
            val outbounds = root.optJSONArray("outbounds") ?: return@runCatching null

            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                if (!outbound.optString("protocol").equals("vless", ignoreCase = true)) continue

                val settings = outbound.optJSONObject("settings") ?: JSONObject()
                val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
                val security = stream.optString("security", "none")
                    .ifBlank { "none" }
                    .lowercase()

                val legacyUser = settings
                    .optJSONArray("vnext")
                    ?.optJSONObject(0)
                    ?.optJSONArray("users")
                    ?.optJSONObject(0)

                val encryption = settings.optString("encryption")
                    .ifBlank { legacyUser?.optString("encryption").orEmpty() }
                    .ifBlank { "none" }
                    .lowercase()

                val host = settings.optString("address")
                    .ifBlank {
                        settings.optJSONArray("vnext")
                            ?.optJSONObject(0)
                            ?.optString("address")
                            .orEmpty()
                    }
                    .trim()

                if (
                    security == "none" &&
                    encryption == "none" &&
                    !isPrivateEndpointHost(host)
                ) {
                    return@runCatching (
                        "Unsupported VLESS • public VLESS now requires TLS/REALITY " +
                            "or non-none VLESS encryption"
                    )
                }
            }
            null
        }.getOrNull()

    private fun isPrivateEndpointHost(raw: String): Boolean {
        val host = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .lowercase()
        if (host.isBlank()) return false

        if (
            host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".lan") ||
            host.endsWith(".home.arpa")
        ) return true

        // Single-label DNS names are normally private search-domain hosts.
        if (!host.contains('.') && !host.contains(':')) return true

        if (
            host == "::1" ||
            host.startsWith("fc") ||
            host.startsWith("fd") ||
            host.startsWith("fe8") ||
            host.startsWith("fe9") ||
            host.startsWith("fea") ||
            host.startsWith("feb")
        ) return true

        val ipv4 = host.removePrefix("::ffff:")
        val parts = ipv4.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val a = octets[0]
        val b = octets[1]
        return when {
            a == 10 -> true
            a == 127 -> true
            a == 169 && b == 254 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            a == 100 && b in 64..127 -> true
            else -> false
        }
    }

    private fun conciseFailure(raw: String): String {
        val compact = raw.replace(Regex("\\s+"), " ").trim()
        val lower = compact.lowercase()
        return when {
            "vless without tls or other encryption is prohibited" in lower ->
                "Unsupported VLESS • enable TLS/REALITY or non-none VLESS encryption"
            "failed to build outbound config" in lower ->
                "Xray rejected this node configuration • check protocol/TLS settings"
            "failed to load config files" in lower ->
                "Xray rejected the generated configuration"
            compact.length > 240 -> compact.take(237) + "…"
            else -> compact.ifBlank { "Connection could not be started" }
        }
    }

    private fun failBeforeTunnel(reason: String) {
        running.set(false)
        val concise = conciseFailure(reason)
        val repo = (application as MarbleApplication).repo
        repo.setRuntimeState("DISCONNECTED", concise)
        repo.setRuntimeMessage(concise)
        diag.event("VPN", "startup-failed-before-tun", "reason" to concise)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    @Synchronized
    private fun cleanupRuntime(setDisconnected: Boolean) {
        val oldSession = activeSession
        running.set(false)
        activeSession = ""
        routeGeneration.incrementAndGet()
        recoveryScheduled.set(false)
        startupTimedOut.set(false)
        routeProbeRequested.set(false)
        optimizerScanRequested.set(false)
        dataStallGuard.reset()
        if (::routeOptimizer.isInitialized) routeOptimizer.reset(System.currentTimeMillis())
        if (hevActive) runCatching { HevTunnel.quit() }
        hevActive = false
        xray.stop()
        closeHevFd()
        closeTun()

        val repo = (application as MarbleApplication).repo
        repo.resetTelemetry()
        activeSettings = null
        activeProfileId = ""
        activeProfileSourceId = ""
        pinnedExitV4 = ""
        pinnedExitV6 = ""
        ipv6RouteCaptured = false
        if (setDisconnected) repo.setRuntimeState("DISCONNECTED", "User disconnected")
        repo.updateSentinel(
            repo.sentinel.copy(
                coverage = "OFFLINE",
                tunnelRoutes = false,
                ipv4Captured = false,
                ipv6Captured = false,
                killSwitchArmed = false,
                xrayAlive = false,
                hevAlive = false,
                updatedAt = System.currentTimeMillis()
            )
        )
        diag.event("VPN", "runtime-clean", "session" to oldSession, "setDisconnected" to setDisconnected)
    }

    private fun closeHevFd() {
        val fd = hevFd
        hevFd = -1
        if (fd >= 0) runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
    }

    private fun closeTun() {
        runCatching { tun?.close() }
        tun = null
        ipv6RouteCaptured = false
    }

    private fun shutdown(explicit: Boolean) {
        diag.event("VPN", "shutdown-begin", "session" to activeSession, "explicit" to explicit, "mode" to activeMode)
        cleanupRuntime(setDisconnected = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        diag.event("VPN", "permission-revoked", "session" to activeSession)
        cleanupRuntime(setDisconnected = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        diag.event("VPN", "service-destroy", "session" to activeSession, "running" to running.get())
        if (running.get() || tun != null || hevActive || xray.isAlive) cleanupRuntime(setDisconnected = true)
        runCatching { connectivityDiagnostics?.close() }
        connectivityDiagnostics = null
        runCatching { networkListener?.close() }
        networkListener = null
        timerWorker.shutdownNow()
        monitorWorker.shutdownNow()
        controlWorker.shutdownNow()
        connectionWorker.shutdownNow()
        super.onDestroy()
    }

    private fun isCurrent(session: String): Boolean = running.get() && activeSession == session

    private fun isRouteCurrent(session: String, generation: Int): Boolean =
        isCurrent(session) && routeGeneration.get() == generation

    private fun elapsedConnectMs(): Long =
        if (connectStartedNs <= 0L) 0L else ((System.nanoTime() - connectStartedNs) / 1_000_000L).coerceAtLeast(0L)

    private fun sleepQuietly(ms: Long): Boolean = try {
        Thread.sleep(ms)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun promoteForeground(text: String, ongoing: Boolean): Boolean {
        val notification = notifier.connectionNotification("MarbleNG", text, ongoing)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        return runCatching { ServiceCompat.startForeground(this, NOTIFY, notification, type) }
            .onFailure { diag.error("VPN", "foreground-start-failed", it, "sdk" to Build.VERSION.SDK_INT) }
            .isSuccess
    }

    private fun notifyNow(text: String, ongoing: Boolean, detail: String? = null) {
        val body = if (detail.isNullOrBlank()) text else "$text • $detail"
        notifier.updateConnection(NOTIFY, "MarbleNG", body, ongoing)
    }

    private fun safeMessage(t: Throwable): String =
        t.message?.take(180)?.ifBlank { t::class.java.simpleName } ?: t::class.java.simpleName
}
