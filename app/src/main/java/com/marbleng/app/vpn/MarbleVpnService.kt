package com.marbleng.app.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.AccelerationPlan
import com.marbleng.app.core.ActiveRouteQuality
import com.marbleng.app.core.AddressFamilyPolicy
import com.marbleng.app.core.BenchmarkEngine
import com.marbleng.app.core.ConnectionTuner
import com.marbleng.app.core.ContinuousRouteOptimizer
import com.marbleng.app.core.ConnectivityDiagnosticsObserver
import com.marbleng.app.core.DataStallGuard
import com.marbleng.app.core.ConfigBlockGuard
import com.marbleng.app.core.HevTunnelPolicy
import com.marbleng.app.core.HighJitterShield
import com.marbleng.app.core.Ipv6FallbackLadder
import com.marbleng.app.core.CoreConfigSuperset
import com.marbleng.app.core.EgressObservationPolicy
import com.marbleng.app.core.JitterControlPolicy
import com.marbleng.app.core.LinkDeadlinePolicy
import com.marbleng.app.core.LinkEvidence
import com.marbleng.app.core.LinkQualityEstimator
import com.marbleng.app.core.NetworkSnapshot
import com.marbleng.app.core.PathMtuPolicy
import com.marbleng.app.core.ProfileFlapGuard
import com.marbleng.app.core.ProtocolFingerprintAwareVerifier
import com.marbleng.app.core.RecoveryBackoffPolicy
import com.marbleng.app.core.ResolverEvidencePolicy
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.SocksHttpClient
import com.marbleng.app.core.SmartNotificationKind
import com.marbleng.app.core.SmartNotifier
import com.marbleng.app.core.TransportTelemetry
import com.marbleng.app.core.TcpStressMonitor
import com.marbleng.app.core.TcpStressTelemetryPolicy
import com.marbleng.app.core.TurboBackoffPolicy
import com.marbleng.app.core.DatapathStop
import com.marbleng.app.core.Tun2SocksPolicy
import com.marbleng.app.core.TunnelCore
import com.marbleng.app.core.TunnelCoreInfo
import com.marbleng.app.core.TunnelShutdownPolicy
import com.marbleng.app.core.parseTunnelCore
import com.marbleng.app.core.tunnelCore
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.SingBoxConfigBuilder
import com.marbleng.app.core.SingBoxAndroidRuntime
import com.marbleng.app.core.SingBoxConfigDoctor
import com.marbleng.app.core.SingBoxManager
import com.marbleng.app.core.SingBoxStartFailure
import com.marbleng.app.core.XrayManager
import com.marbleng.app.core.XrayStartFailure
import com.marbleng.app.core.coreEngine
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.nativebridge.HevTunnel
import com.marbleng.app.nativebridge.Tun2SocksTunnel
import java.io.Closeable
import java.io.RandomAccessFile
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

class MarbleVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.marbleng.START"
        const val ACTION_STOP = "com.marbleng.STOP"

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

        private const val ROUTE_WINDOW_SIZE = 12

        /**
         * MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — how many literal targets a pivot races at once.
         *
         * Two is the whole point: one alternate is a fallback that can itself be filtered, and
         * three turns a latency probe into a traffic source.
         */
        private const val PIVOT_RACE_TARGETS = 2

        /**
         * MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — samples a fresh target must earn before its
         * score is published again.
         *
         * A pivot clears the evidence window, so the first burst back is three successes out of
         * three attempts. That is a healthy route, but a 95 % confidence interval around 3/3
         * starts at 44 %, and a score built on it swings for no reason the user caused. Four
         * samples is the point where the window says something; latency and jitter never wait.
         */
        private const val PIVOT_QUALITY_MIN_SAMPLES = 4

        private const val PROBE_RACE_GRACE_MS = 400L
        private const val JITTER_OPTIMIZER_COOLDOWN_MS = 180_000L
        private const val GOOD_ROUTE_TICKS_FOR_RELEASE = 2
        private const val TURBO_INCONCLUSIVE_BASE_BACKOFF_MS = 600_000L
        private const val TURBO_INCONCLUSIVE_MAX_BACKOFF_MS = 1_800_000L

        private const val RESOLVER_EVIDENCE_INTERVAL_TICKS = 30
        private const val RESOLVER_EVIDENCE_CHUNK_BYTES = 256 * 1024
        private const val JITTER_PRIMARY_HOST = "1.1.1.1"
        private val JITTER_PROBE_TARGETS = listOf(
            "1.1.1.1" to "/cdn-cgi/trace",
            "8.8.8.8" to "/dns-query",
            "9.9.9.9" to "/dns-query",
            "1.0.0.1" to "/cdn-cgi/trace"
        )

        private val LIVE_DOMAIN_RTT_TARGETS = listOf(
            "www.gstatic.com" to "/generate_204",
            "cp.cloudflare.com" to "/generate_204",
            "www.google.com" to "/generate_204"
        )
        private const val LIVE_RTT_BURST_SAMPLES = 3
        private const val LIVE_RTT_BURST_GAP_MS = 45L
        private const val LIVE_RTT_TIMEOUT_MS = 1_250
        private const val LIVE_DOMAIN_RTT_TIMEOUT_MS = 3_500

        private const val VERIFIED_RTT_BACKOFF_MS = 60_000L

        private const val VERIFIED_RTT_SHORT_BACKOFF_MS = 8_000L

        private const val PROBE_FAILURES_BEFORE_RECOVERY = 4
        private const val ROUTE_CONFIRM_TIMEOUT_MS = 4_500
        private const val RECENT_TRAFFIC_GRACE_MS = 75_000L

        private const val ROUTE_FAILURE_WARMUP_MS = 18_000L

        private const val HEV_READY_GRACE_MS = 250L
        private const val CONNECT_STARTUP_TIMEOUT_MS = 90_000L
        private const val HEV_STALL_MIN_MS = 35_000L
        private const val HEV_STALL_MIN_TX_BYTES = 64L * 1024L
        private const val HEV_STATS_FAILURE_LIMIT = 5
        private const val EXIT_IDENTITY_PROBE_INTERVAL_TICKS = 180

        private const val FIRST_TUNE_DELAY_MS = 20_000L
        private const val EGRESS_FIRST_OBSERVATION_DELAY_MS = 20_000L
        private const val HEAVY_TRAFFIC_BPS = 3L * 1024L * 1024L
    }

    private var tun: ParcelFileDescriptor? = null

    /**
     * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — the duplicated TUN descriptor, and the one rule about it:
     * exactly one close, by whoever holds it.
     *
     * [hevFd] is written only under [hevFdLock] and only cleared by the close that actually
     * happens, so a service-side cleanup and the datapath worker can never both close the same
     * number. [datapathOwnsFd] is set the moment the worker takes the descriptor and cleared when
     * its native run has returned: while it is set, a service-side close is refused, because
     * closing a descriptor under a datapath that is still polling it is what makes the process
     * abort on disconnect.
     */
    @Volatile private var hevFd = -1
    private val hevFdLock = Any()
    private val datapathOwnsFd = AtomicBoolean(false)

    /**
     * MARBLE_TUN2SOCKS_CORE_V222 — the datapath this session actually runs. It is resolved once
     * per connect (Settings closes the tunnel on a switch), logged, and used for the run, the
     * stats poll and the stop, so a setting change mid-session can never leave one core reading a
     * descriptor the other one owns.
     */
    @Volatile private var activeTunnelCore: TunnelCore = TunnelCore.HEV
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

    private val connectionWorker = Executors.newSingleThreadExecutor()

    private val controlWorker = Executors.newSingleThreadExecutor()

    private val timerWorker = Executors.newSingleThreadScheduledExecutor()

    private val monitorWorker = Executors.newFixedThreadPool(4)

    private lateinit var xray: XrayManager
    private lateinit var singBox: SingBoxManager

    @Volatile private var activeEngine: CoreEngine = CoreEngine.XRAY

    private val coreTag: String get() = if (activeEngine == CoreEngine.SINGBOX) "SINGBOX" else "XRAY"

    private val coreAlive: Boolean
        get() = if (activeEngine == CoreEngine.SINGBOX) singBox.isAlive else xray.isAlive

    private val coreStartPhase: String
        get() = if (activeEngine == CoreEngine.SINGBOX) singBox.lastStartPhase else xray.lastStartPhase

    private val coreStartError: String
        get() = if (activeEngine == CoreEngine.SINGBOX) singBox.lastStartError else xray.lastStartError

    private val coreLogFile: java.io.File
        get() = if (activeEngine == CoreEngine.SINGBOX) singBox.logFile else xray.logFile

    private fun coreStop() {
        runCatching { singBox.stop() }
        runCatching { xray.stop() }
    }

    private lateinit var diag: RuntimeDiagnostics
    private lateinit var notifier: SmartNotifier
    private lateinit var routeOptimizer: ContinuousRouteOptimizer
    private lateinit var tuner: ConnectionTuner
    private var networkListener: Closeable? = null
    private var connectivityDiagnostics: Closeable? = null
    private val dataStallGuard = DataStallGuard()

    private val configBlockGuard = ConfigBlockGuard()

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

    @Volatile private var recoveryBackoff = RecoveryBackoffPolicy.State()
    @Volatile private var ipv6RouteCaptured = false
    @Volatile private var pinnedExitV4 = ""
    @Volatile private var pinnedExitV6 = ""
    @Volatile private var lastNetworkKey = ""
    @Volatile private var lastNetworkValidated = false
    @Volatile private var lastTuneAt = 0L
    @Volatile private var lastTrafficProgressAt = 0L
    @Volatile private var sessionTuned = false

    @Volatile private var activeMethodId = AccelerationPlan.DIRECT

    private val recoveryTried = linkedSetOf<String>()
    private val routeOutcomeWindow = ArrayDeque<Int>()
    @Volatile private var jitterProbeHost = JITTER_PRIMARY_HOST

    /**
     * MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — whether the previous sampling cycle produced a
     * verified RTT. A cycle that follows a failed one measures two targets from its very first
     * sample instead of re-discovering, one target at a time, that the pinned one is still
     * filtered.
     */
    @Volatile private var lastRouteCycleVerified = true

    /**
     * MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — how many more samples the score must wait for after
     * a target change. Zero means "the window is worth scoring".
     */
    @Volatile private var routeQualityHoldSamples = 0

    private val routeProbeRacePool by lazy {
        Executors.newFixedThreadPool(PIVOT_RACE_TARGETS) { runnable ->
            Thread(runnable, "marble-route-probe").apply { isDaemon = true }
        }
    }

    @Volatile private var jitterControlActive = false
    @Volatile private var lastJitterOptimizerRequestAt = 0L
    @Volatile private var verifiedRttBackoffUntilMs = 0L

    private val highJitterWindow = HighJitterShield.RobustWindow()
    @Volatile private var highJitterShield = HighJitterShield.State()
    @Volatile private var highJitterPlan: HighJitterShield.Plan? = null
    @Volatile private var lastRouteProbeMonotonicMs = 0L
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun acquireLowLatencyWifiLock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wifiLock == null) {
            runCatching {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                wifiLock = wifiManager?.createWifiLock(
                    android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                    "MarbleNG:VpnLowLatency"
                )?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
    }

    private fun releaseLowLatencyWifiLock() {
        runCatching {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        }
        wifiLock = null
    }

    @Volatile private var jitterControlState = JitterControlPolicy.State()
    @Volatile private var turboBackoffState = TurboBackoffPolicy.State()

    @Volatile private var resolverLogOffset = 0L
    @Volatile private var egressObservationState = EgressObservationPolicy.State()
    @Volatile private var pathMtuState = PathMtuPolicy.State()
    private val tcpStressMonitor = TcpStressMonitor()
    @Volatile private var goodRouteTicks = 0

    override fun onCreate() {
        super.onCreate()
        val app = application as MarbleApplication
        xray = app.xray
        singBox = app.repo.singBox
        diag = RuntimeDiagnostics(this)
        notifier = SmartNotifier(this)
        notifier.ensureChannels()
        routeOptimizer = ContinuousRouteOptimizer(app.repo.intelligence, ProfileFlapGuard(iranAware = true))
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
        val replacingFullTun = tun != null && mode != MODE_PROXY
        if (running.get() || tun != null || hevActive || coreAlive) {
            cleanupRuntime(setDisconnected = false, preserveTun = replacingFullTun)
        }

        val app = application as MarbleApplication

        if (mode != MODE_PROXY && !app.repo.settings.dnsHijackEnabled) {
            failBeforeTunnel("Full VPN requires DNS hijacking to prevent plaintext DNS; enable DNS hijack in settings")
            return
        }
        val profile = app.repo.profile(id, sourceId) ?: run {
            diag.event("VPN", "profile-missing", "profileId" to id.take(12))
            failBeforeTunnel("Profile no longer exists")
            return
        }
        val preflightEngine = app.repo.settings.coreEngine()
        profileCompatibilityIssue(profile, preflightEngine)?.let { issue ->

            val decision = configBlockGuard.observe(profile.id, issue)
            val rejectionTag = if (preflightEngine == CoreEngine.SINGBOX) "SINGBOX" else "XRAY"
            if (decision.report) {
                diag.event(
                    rejectionTag, "profile-preflight-rejected",
                    "profile" to profile.id.take(12),
                    "scheme" to profile.scheme,
                    "engine" to preflightEngine.id,
                    "reason" to issue,
                    "wire" to CoreConfigSuperset.wire(profile).name,
                    "foldedRepeats" to decision.suppressedBefore
                )
            }
            failBeforeTunnel(issue, loud = decision.report)
            return
        }

        val normalizedMode = if (mode == MODE_PROXY) MODE_PROXY else MODE_TUN
        val session = System.currentTimeMillis().toString(36) + "-" + Integer.toHexString(profile.id.hashCode())

        activeSession = session
        activeMode = normalizedMode
        activeProfileId = profile.id
        activeProfileSourceId = profile.subscriptionId
        connectStartedNs = System.nanoTime()
        consecutiveProbeFailures = 0
        identityRecoveryAttempts = 0

        recoveryBackoff = RecoveryBackoffPolicy.reset()
        ipv6RouteCaptured = tun != null
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
        lastRouteCycleVerified = true
        routeQualityHoldSamples = 0
        jitterControlState = JitterControlPolicy.State()
        jitterControlActive = false

        resetHighJitterShield()
        lastJitterOptimizerRequestAt = 0L
        turboBackoffState = TurboBackoffPolicy.State()
        resolverLogOffset = 0L
        egressObservationState = EgressObservationPolicy.State()
        pathMtuState = PathMtuPolicy.State()
        goodRouteTicks = 0

        diag.event(
            "VPN", "connect-request",
            "session" to session,
            "profileId" to profile.id.take(12),
            "profileName" to profile.name,
            "mode" to normalizedMode,

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
                    coreTag,
                    "startup-cancel-request",
                    "session" to session,
                    "phase" to coreStartPhase
                )
                coreStop()
                handleFailure(
                    session,
                    "Connection startup timed out after ${CONNECT_STARTUP_TIMEOUT_MS / 1_000}s"
                )
            }
        }, CONNECT_STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        connectionWorker.execute {
            runCatching {
                if (!isCurrent(session)) return@runCatching

                val resolved = app.repo.effectiveSettingsFor(profile)

                val settings = if (normalizedMode == MODE_TUN) resolved.copy(dnsHijackEnabled = true)
                    else resolved
                if (!isCurrent(session)) return@runCatching
                activeSettings = settings
                val port = if (normalizedMode == MODE_PROXY) {
                    settings.localProxyPort
                } else {
                    settings.socksPort
                }
                if (normalizedMode == MODE_TUN) {
                    connectTunOverlapped(profile, session, port, settings)
                } else {
                    if (!isCurrent(session)) return@runCatching
                    startXrayAndForward(profile, session, port, settings, recovering = false)
                }
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

    private data class CoreLaunch(
        val started: Boolean,
        val generation: Int,
        val settings: AppSettings
    )

    private fun launchCore(
        profile: ProxyProfile,
        session: String,
        port: Int,
        requestedSettings: AppSettings,
        recovering: Boolean
    ): CoreLaunch {
        val app = application as MarbleApplication
        activeProfileId = profile.id
        activeSettings = requestedSettings
        var generation = 0

        val settings = preTune(profile, session, requestedSettings, recovering)
        if (!isCurrent(session)) return CoreLaunch(false, generation, settings)
        activeSettings = settings
        activeMethodId = app.repo.intelligence.acceleration(profile.id)?.methodId
            ?: AccelerationPlan.DIRECT
        connectStartedNs = System.nanoTime()
        verifiedRttBackoffUntilMs = 0L
        generation = routeGeneration.incrementAndGet()

        val linkEvidence = linkEvidenceFor(profile.id)

        // Explicit engine selection is a contract. A local startup/config fault must never

        activeEngine = settings.coreEngine()
        diag.event(
            coreTag, if (recovering) "recovery-start" else "start-begin",
            "engine" to activeEngine.id,
            "session" to session,
            "profile" to profile.name,
            "port" to port,
            "mode" to activeMode,
            "mux" to settings.muxEnabled,
            "dnsStrategy" to settings.dnsQueryStrategy,
            "linkRttMs" to linkEvidence.rttMs.toInt(),
            "linkJitterMs" to linkEvidence.jitterMs.toInt(),
            "linkSamples" to linkEvidence.samples,

            "dnsTimeoutMs" to LinkDeadlinePolicy.dnsServerTimeoutMs(linkEvidence, 0, fragmented = false)
        )

        val coreStartNs = System.nanoTime()

        val coreStarted = when (activeEngine) {
            CoreEngine.SINGBOX -> singBox.start(profile, port, settings)
            CoreEngine.XRAY -> xray.start(profile, port, settings, linkEvidence)
        }
        val coreStartMs = ((System.nanoTime() - coreStartNs) / 1_000_000L).coerceAtLeast(0L)

        val readiness = if (activeEngine == CoreEngine.SINGBOX) singBox.lastStartReadiness else null
        diag.event(
            coreTag,
            "start-result",
            "session" to session,
            "ok" to coreStarted,
            "elapsedMs" to coreStartMs,
            "phase" to coreStartPhase,
            "alive" to coreAlive,
            "inbound" to (readiness?.inboundUp ?: coreStarted),
            "controller" to (readiness?.controllerUp ?: true),
            "reason" to if (coreStarted) "" else coreStartError.take(500)
        )
        if (!coreStarted) {
            if (!isCurrent(session)) {
                diag.event(
                    coreTag,
                    "start-result-stale",
                    "session" to session,
                    "phase" to coreStartPhase
                )
                return CoreLaunch(false, generation, settings)
            }

            // see "Explicit engine selection is a contract" above `activeEngine = settings.coreEngine()`.

            val unusableCore = activeEngine == CoreEngine.SINGBOX &&
                SingBoxAndroidRuntime.isUnusableCore(coreStartError)
            val xrayFault = if (activeEngine == CoreEngine.XRAY) XrayStartFailure.classify(coreStartError) else null
            diag.event(
                coreTag,
                if (unusableCore) "core-unusable-on-device" else "core-start-failure-not-a-server-verdict",

                "reason" to (xrayFault?.cause ?: coreStartError).take(300),
                "faultKind" to (xrayFault?.kind?.name ?: "unclassified"),
                "selfTest" to (singBox.lastSelfTest?.summary ?: "not run")
            )
            handleFailure(
                session,
                coreStartError.ifBlank {
                    if (activeEngine == CoreEngine.SINGBOX) {
                        "sing-box extended rejected profile or routing policy"
                    } else {
                        "Xray rejected profile or routing policy"
                    }
                },
                allowRecovery = false,
                recordProfileFailure = false,
                faultClass = when {
                    unusableCore -> "Core cannot run on this device"

                    SingBoxAndroidRuntime.isPortBindConflict(coreStartError) -> "Local port in use"

                    activeEngine == CoreEngine.XRAY ->
                        XrayStartFailure.faultClass(coreStartError, "Core/configuration error")
                    activeEngine == CoreEngine.SINGBOX ->
                        SingBoxStartFailure.faultClass(coreStartError, "Core/configuration error")
                    else -> "Core/configuration error"
                }
            )
            return CoreLaunch(false, generation, settings)
        }
        if (!isCurrent(session)) {
            coreStop()
            return CoreLaunch(false, generation, settings)
        }

        scheduleSyntheticEgressObservation(session, generation, port, profile)

        diag.event(coreTag, "socks-ready", "session" to session, "alive" to coreAlive, "port" to port)

        val existingIdentityPin = pinnedExitV4.isNotBlank() || pinnedExitV6.isNotBlank()
        if (
            settings.identityGuardEnabled &&
            (recovering || existingIdentityPin) &&
            !verifyExitIdentity(session, port, generation)
        ) {
            return CoreLaunch(false, generation, settings)
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
            return CoreLaunch(true, generation, settings)
        }
        return CoreLaunch(true, generation, settings)
    }

    private fun connectTunOverlapped(
        profile: ProxyProfile,
        session: String,
        port: Int,
        settings: AppSettings
    ) {
        val t0 = System.nanoTime()
        val coreResult = AtomicReference<CoreLaunch?>(null)
        val coreThread = Thread({
            try {
                coreResult.set(launchCore(profile, session, port, settings, recovering = false))
            } catch (t: Throwable) {
                diag.error("VPN", "core-launch-crash", t, "session" to session)
            }
        }, "marble-core-start").apply { isDaemon = true; start() }

        val tunOk = establishTun(profile, session, settings)
        diag.event(
            "VPN", "connect-phase", "session" to session, "step" to "tun",
            "ms" to ((System.nanoTime() - t0) / 1_000_000L), "ok" to tunOk
        )

        runCatching { coreThread.join() }
        if (!isCurrent(session)) {
            coreStop()
            return
        }
        val launch = coreResult.get()
        diag.event(
            "VPN", "connect-phase", "session" to session, "step" to "core",
            "ms" to ((System.nanoTime() - t0) / 1_000_000L),
            "started" to (launch?.started == true)
        )
        if (launch?.started != true) {
            return
        }
        if (!tunOk) {
            coreStop()
            handleFailure(session, "VPN establish failed")
            return
        }
        runTun(profile, session, port, launch.settings, recovering = false, generation = launch.generation)
    }

    private fun startXrayAndForward(
        profile: ProxyProfile,
        session: String,
        port: Int,
        requestedSettings: AppSettings,
        recovering: Boolean
    ) {
        val launch = launchCore(profile, session, port, requestedSettings, recovering)
        if (launch.started && activeMode == MODE_TUN) {
            runTun(profile, session, port, launch.settings, recovering, launch.generation)
        }
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
            val upstream = usableUpstreamNetworks(app.repo.intelligence.underlyingNetworks())
            if (upstream.isNotEmpty()) {
                runCatching { builder.setUnderlyingNetworks(upstream.toTypedArray()) }
                    .onFailure {
                        diag.error("TUN", "underlying-networks-failed", it, "session" to session)
                    }
                diag.event(
                    "TUN", "underlying-networks",
                    "session" to session,
                    "pinned" to upstream.size,
                    "candidates" to app.repo.intelligence.underlyingNetworks().size
                )
            }
        }

        val ipv6Ok = AddressFamilyPolicy.shouldCaptureIpv6() && runCatching {
            builder.addAddress("fc00::1", 128).addRoute("::", 0)
        }.onFailure { diag.error("TUN", "ipv6-capture-required", it, "session" to session) }.isSuccess
        if (!ipv6Ok) return false

        val dnsCount = 2
        if (runCatching {
            builder.addDnsServer("198.18.0.2")
            builder.addDnsServer("fc00::2")
        }.onFailure { diag.error("TUN", "dns-capture-required", it, "session" to session) }.isFailure) {
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
        val previousTun = tun
        tun = established
        ipv6RouteCaptured = ipv6Ok

        runCatching { previousTun?.close() }
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
        acquireLowLatencyWifiLock()
        return true
    }

    private fun usableUpstreamNetworks(candidates: List<Network>): List<Network> {
        if (candidates.isEmpty()) return emptyList()
        val connectivity = runCatching {
            getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return emptyList()

        return candidates.filter { network ->
            val caps = runCatching { connectivity.getNetworkCapabilities(network) }.getOrNull()
                ?: return@filter false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
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
        val requestedTunnel = settings.tunnelCore()
        val tunnelCore = if (TunnelCoreInfo.isAvailable(requestedTunnel)) requestedTunnel else TunnelCore.HEV
        activeTunnelCore = tunnelCore
        if (tunnelCore != requestedTunnel) {
            diag.event(
                "TUNNEL", "datapath-fallback",
                "session" to session,
                "requested" to requestedTunnel.id,
                "running" to tunnelCore.id,
                "reason" to TunnelCoreInfo.unavailableReason(requestedTunnel)
            )
        }
        diag.prepareTunnelSession(tunnelCore)
        val dupFd = runCatching { ParcelFileDescriptor.dup(currentTun.fileDescriptor).detachFd() }
            .onFailure { diag.error("TUN", "dup-fd-failed", it, "session" to session) }
            .getOrNull()
        if (dupFd == null) {
            handleFailure(session, "TUN fd duplication failed")
            return
        }
        // MARBLE_TUN2SOCKS_SHUTDOWN_V223 — from here to the end of the run the descriptor has one
        // owner: this worker. Cleanup on any other thread may ask for it to be closed, and the ask
        // is deferred (logged) until the native run has returned, because a number closed under a
        // datapath that is still polling it is the disconnect abort this release fixes.
        adoptDatapathFd(dupFd)
        try {
            runDatapath(profile, session, socksPort, settings, recovering, generation, tunnelCore)
        } finally {
            releaseDatapathFd(tunnelCore)
        }
    }

    /**
     * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — the datapath body, unchanged apart from its parameters: it
     * runs with the duplicated descriptor already adopted by the caller, so every path out of it
     * (a refusal, a lost session, a core that stopped) closes the descriptor exactly once, in the
     * caller's `finally`.
     */
    private fun runDatapath(
        profile: ProxyProfile,
        session: String,
        socksPort: Int,
        settings: AppSettings,
        recovering: Boolean,
        generation: Int,
        tunnelCore: TunnelCore
    ) {
        if (!isRouteCurrent(session, generation)) return

        val datapath = (application as MarbleApplication).repo.intelligence
            .tunnelTuning(profile.id, settings)

        // MARBLE_TUN2SOCKS_CORE_V222 — the same descriptor, handed to whichever datapath the user
        // chose. HEV reads it in C; tun2socks reads it in the pinned Go core loaded in-process.
        val cfg = if (tunnelCore == TunnelCore.HEV) {
            HevTunnelPolicy.buildConfig(
                socksPort = socksPort,
                mtu = activeMtu,
                ipv6 = ipv6RouteCaptured,
                logFilePath = diag.hevLog.absolutePath,
                datapath = datapath,
                fastOpen = settings.tcpFastOpenEnabled
            )
        } else {
            ""
        }
        if (tunnelCore == TunnelCore.HEV && (cfg.contains("\\n") || !cfg.contains('\n'))) {
            handleFailure(session, "Internal HEV YAML encoding failure")
            return
        }
        val tun2socks = if (tunnelCore == TunnelCore.TUN2SOCKS) {
            Tun2SocksPolicy.buildConfig(
                socksPort = socksPort,
                mtu = activeMtu,
                tunFd = hevFd,
                udpTimeoutSec = settings.tun2SocksUdpTimeoutSec,
                tcpSendBufferKb = settings.tun2SocksTcpSendBufferKb,
                tcpReceiveBufferKb = settings.tun2SocksTcpReceiveBufferKb,
                moderateReceiveBuffer = settings.tun2SocksModerateReceiveBuffer,
                logLevel = settings.tun2SocksLogLevel,
                logPath = diag.tun2SocksLog.absolutePath,
                mtuCap = settings.tun2SocksMtu
            )
        } else {
            null
        }
        if (tunnelCore == TunnelCore.TUN2SOCKS && tun2socks == null) {
            handleFailure(session, "tun2socks rejected the datapath configuration")
            return
        }

        diag.event(
            tunnelCore.name, "config-ready",
            "session" to session,
            "bytes" to cfg.toByteArray().size,
            "lines" to cfg.lineSequence().count(),
            "sha256" to diag.sha256(if (tunnelCore == TunnelCore.HEV) cfg else tun2socks?.summary().orEmpty()),
            "mtu" to activeMtu,
            "sessions" to datapath.maxSessions,
            "tcpBuffer" to datapath.tcpBufferBytes,
            "udpBuffer" to datapath.udpBufferBytes,
            "datapath" to datapath.label,
            "tunnelCore" to tunnelCore.id,
            "tun2socks" to tun2socks?.summary()
        )
        if (!isCurrent(session)) {
            closeHevFd("datapath-session-lost")
            return
        }

        val app = application as MarbleApplication

        recoveryScheduled.set(false)
        consecutiveProbeFailures = 0
        lastTrafficProgressAt = 0L
        tunReadyPublished.set(false)
        hevActive = true
        updateSentinel(killSwitch = true)

        timerWorker.schedule({
            if (isRouteCurrent(session, generation) && hevActive && coreAlive) {
                if (tunReadyPublished.compareAndSet(false, true) && isRouteCurrent(session, generation)) {
                startupTimedOut.set(false)

                recoveryBackoff = RecoveryBackoffPolicy.reset()
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
                    tunnelCore.name, "ready",
                    "session" to session,
                    "graceMs" to HEV_READY_GRACE_MS,
                    "proof" to "blocking-run-alive",
                    "tunnelCore" to tunnelCore.id
                )

                    startTelemetry(session, socksPort, generation)
                }
            }
        }, HEV_READY_GRACE_MS, TimeUnit.MILLISECONDS)

        diag.event(
            tunnelCore.name, "run-enter",
            "session" to session, "hevFd" to hevFd, "xrayAlive" to coreAlive,
            "tunnelCore" to tunnelCore.id
        )
        val result = runCatching {
            when (tunnelCore) {
                TunnelCore.HEV -> HevTunnel.run(cfg, hevFd)
                TunnelCore.TUN2SOCKS -> {
                    val plan = tun2socks ?: return@runCatching -10002
                    Tun2SocksTunnel.run(
                        proxy = plan.proxy,
                        mtu = plan.mtu,
                        tunFd = plan.tunFd,
                        udpTimeoutSec = plan.udpTimeoutSec,
                        tcpSendBuffer = plan.tcpSendBufferBytes,
                        tcpReceiveBuffer = plan.tcpReceiveBufferBytes,
                        moderateReceiveBuffer = plan.moderateReceiveBuffer,
                        logLevel = plan.logLevel,
                        logPath = plan.logPath
                    ).also { code ->
                        if (code != 0) {
                            diag.event(
                                "TUN2SOCKS", "native-refused",
                                "session" to session, "code" to code,
                                "reason" to Tun2SocksTunnel.lastError()
                            )
                        }
                    }
                }
            }
        }
        hevActive = false
        val code = result.getOrElse {
            diag.error(tunnelCore.name, "jni-run-exception", it, "session" to session, "hevFd" to hevFd)
            -10001
        }
        diag.event(
            tunnelCore.name, "run-exit",
            "session" to session, "code" to code, "runningFlag" to running.get(), "xrayAlive" to coreAlive
        )

        if (isCurrent(session)) {
            if (!tunReadyPublished.get()) {
                diag.event(tunnelCore.name, "exited-before-ready", "session" to session, "code" to code)
            }
            if (!recoveryScheduled.get()) handleFailure(session, "${TunnelCoreInfo.displayName(tunnelCore)} stopped ($code)")
        }
    }

private fun startProxyMonitor(session: String, port: Int, generation: Int) {
        (application as MarbleApplication).repo.beginRouteMeasurement()
        monitorWorker.execute {
            var tick = 0

            sampleRouteLatency(session, port, generation)

            while (isRouteCurrent(session, generation) && activeMode == MODE_PROXY) {
                if (!coreAlive) {
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
            tcpStressMonitor.resetForProfile(
                activeProfileId,
                repo.intelligence.currentSnapshot().key(),
                activeMtu
            )
            var lastUp = -1L
            var lastDown = -1L
            var lastT = System.nanoTime()
            var tick = 0
            var statsFailures = 0
            var txWithoutRxBytes = 0L
            var txWithoutRxSince = 0L

            // The dashboard should become meaningful as soon as Xray is live. Probe the actual

            sampleRouteLatency(session, port, generation)

            while (isRouteCurrent(session, generation) && hevActive) {
                if (!sleepQuietly(1_000L)) return@execute
                if (!isRouteCurrent(session, generation) || !hevActive) break
                if (!coreAlive) {
                    handleFailure(session, "Xray core stopped")
                    return@execute
                }

                if (tick < 2) {
                    tick++
                    continue
                }

                val stats = runCatching {
                    when (activeTunnelCore) {
                        TunnelCore.HEV -> HevTunnel.stats()
                        TunnelCore.TUN2SOCKS -> Tun2SocksTunnel.stats() ?: LongArray(0)
                    }
                }.getOrNull()
                if (stats == null || stats.size < 4) {
                    statsFailures++
                    diag.event(activeTunnelCore.name, "stats-unavailable", "session" to session, "count" to statsFailures)
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
                                diag.event(activeTunnelCore.name, "datapath-stall-suspected",
                                    "session" to session,
                                    "txWithoutRxBytes" to txWithoutRxBytes,
                                    "stallMs" to (nowMs - txWithoutRxSince),
                                    "xrayAlive" to coreAlive)

                                if (confirmRouteUnavailable(session, port, generation)) {
                                    diag.event(
                                        activeTunnelCore.name, "datapath-stalled-confirmed",
                                        "session" to session,
                                        "txWithoutRxBytes" to txWithoutRxBytes,
                                        "stallMs" to (nowMs - txWithoutRxSince)
                                    )
                                    handleFailure(session,
                                        "${TunnelCoreInfo.displayName(activeTunnelCore)} datapath stalled and independent HTTPS confirmation failed")
                                    return@execute
                                }
                                txWithoutRxBytes = 0L
                                txWithoutRxSince = 0L
                                diag.event(activeTunnelCore.name, "datapath-stall-held", "session" to session)
                            }
                        }

                        repo.updateTelemetry((deltaDown / dt).toLong(), (deltaUp / dt).toLong())
                    }
                    lastUp = up
                    lastDown = down
                    lastT = nowNs
                }

                if (tick % 2 == 0 && activeEngine == CoreEngine.XRAY) {
                    TransportTelemetry.latest(xray.transportTelemetryFile)?.takeIf { it.fresh() }?.let { transport ->
                        val liveSettings = activeSettings ?: repo.settings
                        val liveNetwork = repo.intelligence.currentSnapshot()
                        val assessment = TcpStressTelemetryPolicy.assess(
                            sample = transport,
                            activeMtu = activeMtu,
                            hasIpv6 = liveNetwork.hasIpv6
                        )
                        val stressed = assessment.stressed
                        if (stressed) routeProbeRequested.set(true)

                        tcpStressMonitor.observe(
                            retransmitRate = assessment.retransmitRate,
                            lossRate = assessment.lossRate,
                            mssRatio = assessment.mssRatio,
                            unackedSegments = transport.unacked,
                            stressed = assessment.mtuRelevantStress,
                            rttMs = transport.rttMs,
                            profileId = activeProfileId,
                            networkKey = liveNetwork.key(),
                            currentMtu = activeMtu
                        )
                        val stressDecision = tcpStressMonitor.evaluate()
                        if (stressDecision.shouldReduceMtu) {
                            val targetMtu = stressDecision.recommendedLevel.mtu
                            if (targetMtu < activeMtu) {
                                repo.intelligence.rememberPathMtu(activeProfileId, targetMtu)
                                tuningRequested.set(true)
                                diag.event(
                                    "MTU", "tcp-stress-mtu-reduced",
                                    "session" to session,
                                    "targetMtu" to targetMtu,
                                    "reason" to stressDecision.reason,
                                    "urgency" to stressDecision.urgency.name
                                )
                            }
                        }

                        if (liveSettings.adaptiveMssEnabled && transport.pmtu in 1280..9000) {
                            val mtuDecision = PathMtuPolicy.observe(
                                state = pathMtuState,
                                observedMtu = transport.pmtu,
                                activeMtu = activeMtu,
                                nowMs = System.currentTimeMillis(),

                                sawtoothConfidence = throttleEvidenceForActiveRoute(),
                                throttleActive = jitterControlState.throttleActive
                            )
                            pathMtuState = mtuDecision.state
                            mtuDecision.commitMtu?.let { committed ->
                                repo.intelligence.rememberPathMtu(activeProfileId, committed)
                            }
                            if (mtuDecision.requestTune) tuningRequested.set(true)
                            if (mtuDecision.throttlingSynchronized) {
                                runCatching { repo.intelligence.attributionTick() }
                            }
                            if (mtuDecision.commitMtu != null || mtuDecision.requestTune) {
                                diag.event(
                                    "MTU", "path-mtu-learned",
                                    "session" to session,
                                    "observed" to transport.pmtu,
                                    "active" to activeMtu,
                                    "committed" to (mtuDecision.commitMtu ?: 0),
                                    "repeats" to mtuDecision.state.repeats,
                                    "requestTune" to mtuDecision.requestTune,
                                    "reason" to mtuDecision.reason
                                )
                            }
                        }
                        if (tick % 10 == 0) diag.event(coreTag, "tcp-info", "session" to session,
                            "sockets" to transport.sockets, "rttMs" to transport.rttMs,
                            "p95RttMs" to transport.p95RttMs, "rttVarMs" to transport.rttVarMs,
                            "retransDelta" to transport.retransDelta, "lost" to transport.lost,
                            "lostDelta" to transport.lostDelta,
                            "unacked" to transport.unacked, "pmtu" to transport.pmtu,
                            "mss" to transport.mss, "cwnd" to transport.cwndPackets,
                            "stressRate" to assessment.retransmitRate,
                            "stressLoss" to assessment.lossRate,
                            "retransmissionPressure" to assessment.retransmissionPressure,
                            "lossPressure" to assessment.lossPressure,
                            "jitterPressure" to assessment.jitterPressure,
                            "stressed" to stressed)
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

                if (tick % RESOLVER_EVIDENCE_INTERVAL_TICKS == 0) {
                    harvestResolverEvidence(session)
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

    private fun linkEvidenceFor(profileId: String): LinkEvidence {
        val repo = (application as MarbleApplication).repo
        return runCatching {
            repo.intelligence.linkEvidenceFor(profileId, repo.livePingMs)
        }.getOrDefault(LinkEvidence.UNKNOWN)
    }

    private fun harvestResolverEvidence(session: String) {
        val file = coreLogFile
        if (!file.isFile) return
        val length = file.length()
        if (length <= 0L) return

        if (length < resolverLogOffset) resolverLogOffset = 0L
        val available = length - resolverLogOffset
        if (available <= 0L) return
        val chunk = available.coerceAtMost(RESOLVER_EVIDENCE_CHUNK_BYTES.toLong()).toInt()

        val read = runCatching {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(resolverLogOffset)
                val buffer = ByteArray(chunk)
                val count = raf.read(buffer)
                if (count <= 0) {
                    0 to ""
                } else {
                    val lastNewline = buffer.indexOfLast { it.toInt() == 10 }
                    val usable = if (lastNewline >= 0) lastNewline + 1 else count
                    usable to String(buffer, 0, usable, Charsets.UTF_8)
                }
            }
        }.getOrDefault(0 to "")

        val consumedBytes = read.first
        if (consumedBytes <= 0) return
        resolverLogOffset += consumedBytes.toLong()
        val text = read.second
        if (text.isBlank()) return

        val repo = (application as MarbleApplication).repo
        val settings = activeSettings ?: repo.settings
        val before = repo.intelligence.resolverEvidence()
        val after = runCatching {
            repo.intelligence.recordResolverEvidence(
                text.lineSequence().filter { it.isNotBlank() }
            )
        }.getOrDefault(before)
        if (after == before) return

        val nowMs = System.currentTimeMillis()
        diag.event(
            "DNS", "resolver-evidence-updated",
            "session" to session,
            "endpoints" to after.size,
            "demoted" to repo.intelligence.resolverDemotedEndpoints(settings)
                .joinToString(",").ifBlank { "none" },
            "parallelQuery" to ResolverEvidencePolicy.parallelQueryJustified(
                repo.intelligence.dnsCandidatePool(settings), after, nowMs
            ),

            "storm" to if (repo.intelligence.dnsStormActive()) {
                "armed:${"%.2f".format(java.util.Locale.US, repo.intelligence.dnsStormRatePerMinute())}/min"
            } else {
                "calm"
            },
            "detail" to ResolverEvidencePolicy.describe(after, nowMs).take(300)
        )
    }

    private fun releaseTurboBackoffIfRecovered(session: String, quality: ActiveRouteQuality) {
        val settings = activeSettings ?: return
        val triggerMs = settings.liveTuningPingTriggerMs.coerceIn(80, 1200)
        val good = quality.samples >= JitterControlPolicy.MIN_SAMPLES &&
            quality.latencyMs in 1 until triggerMs
        goodRouteTicks = if (good) goodRouteTicks + 1 else 0
        if (goodRouteTicks < GOOD_ROUTE_TICKS_FOR_RELEASE) return

        val now = System.currentTimeMillis()
        if (!TurboBackoffPolicy.isWaiting(turboBackoffState, now)) return
        val released = TurboBackoffPolicy.observeRoute(turboBackoffState, now, recovered = true)
        if (released == turboBackoffState) return
        turboBackoffState = released
        goodRouteTicks = 0
        diag.event(
            "TURBO", "backoff-released-by-recovered-route",
            "session" to session,
            "pingMs" to quality.latencyMs,
            "triggerMs" to triggerMs,
            "streak" to released.streak,
            "earlyReleases" to released.earlyReleases
        )
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
        stopDatapath()
        coreStop()
        closeHevFd("autopilot-switch")
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

    private fun preTune(
        profile: ProxyProfile,
        session: String,
        requested: AppSettings,
        recovering: Boolean
    ): AppSettings {
        if (!requested.connectTuningEnabled) return requested
        if (!isCurrent(session)) return requested

        diag.event(
            "TURBO", "pre-connect-deferred",
            "session" to session,
            "profile" to profile.name,
            "recovering" to recovering
        )
        return requested
    }

    private fun maybeAccelerate(session: String, generation: Int) {
        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return
        if (tuningRunning.get()) return

        val app = application as MarbleApplication
        val repo = app.repo
        val settings = activeSettings ?: repo.settings
        if (!settings.connectTuningEnabled) return

        val forced = tuningRequested.getAndSet(false)
        if (!forced && !settings.liveTuningEnabled) return

        val quality = activeRouteQuality()

        val jitterDegraded = jitterControlActive ||
            (highJitterPlan?.level ?: 0.0) >= HighJitterShield.DEGRADED_LEVEL
        val degraded = quality.samples >= 3 && (
            quality.latencyMs >= settings.liveTuningPingTriggerMs.coerceIn(80, 1200) ||
                jitterDegraded
        )

        if (!forced) {
            if (TurboBackoffPolicy.isWaiting(turboBackoffState, System.currentTimeMillis())) return
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

        var tunerFailure = ""
        val report = runCatching {
            tuner.accelerate(profile, base, budgetMs, measureSpeed) { label ->
                repo.intelligence.setDecision("Marble Turbo • ${profile.name} • $label")
            }
        }.onFailure {
            tunerFailure = "${it::class.java.simpleName}: ${it.message ?: "no message"}".take(160)
            diag.error("TURBO", "live-failed", it, "session" to session)
        }.getOrNull()

        if (!isRouteCurrent(session, generation) || recoveryScheduled.get()) return

        if (report == null || !report.healthy) {
            sessionTuned = true

            val liveQuality = activeRouteQuality()
            val liveRouteAnswering = liveQuality.samples > 0

            val cause = when {
                report == null -> TurboBackoffPolicy.Cause.NOT_ATTEMPTED
                liveRouteAnswering -> TurboBackoffPolicy.Cause.PROBE_UNAVAILABLE
                else -> TurboBackoffPolicy.Cause.TRANSPORT_INCONCLUSIVE
            }
            val outcome = TurboBackoffPolicy.inconclusive(
                state = turboBackoffState,
                nowMs = System.currentTimeMillis(),
                cause = cause,
                baseMs = TURBO_INCONCLUSIVE_BASE_BACKOFF_MS,
                maxMs = TURBO_INCONCLUSIVE_MAX_BACKOFF_MS
            )
            turboBackoffState = outcome.state

            if (report != null) repo.intelligence.setDecision(report.summary)
            diag.event(
                "TURBO", "live-inconclusive-backoff",
                "session" to session,
                "streak" to outcome.state.streak,
                "backoffSec" to (outcome.backoffMs / 1000L),
                "cause" to cause.name,
                "escalated" to outcome.escalated,
                "reason" to outcome.reason,
                "liveRouteSamples" to liveQuality.samples,
                "liveRoutePingMs" to liveQuality.latencyMs,
                "jitterControl" to jitterControlActive,
                "tunerFailure" to tunerFailure.ifBlank { "none" }
            )
            return
        }

        sessionTuned = true
        turboBackoffState = TurboBackoffPolicy.succeeded(turboBackoffState)
        goodRouteTicks = 0

        val appliedMethod = activeMethodId
        repo.intelligence.rememberAcceleration(profile.id, report.winner)
        repo.intelligence.setDecision(report.summary)
        repo.refreshIntelligenceStatus()

        if (report.winner.methodId == appliedMethod) {
            diag.event("TURBO", "live-confirmed", "session" to session, "method" to appliedMethod)
            return
        }

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

    private fun scheduleSyntheticEgressObservation(
        session: String,
        generation: Int,
        port: Int,
        profile: ProxyProfile,
        attempt: Int = 0
    ) {
        val delayMs = if (attempt <= 0) {
            EGRESS_FIRST_OBSERVATION_DELAY_MS
        } else {
            EgressObservationPolicy.rearmDelay(attempt)
        }
        if (delayMs <= 0L) return
        timerWorker.schedule({
            if (isRouteCurrent(session, generation) && coreAlive) {
                monitorWorker.execute {
                    if (!isRouteCurrent(session, generation) || !coreAlive) return@execute
                    val nowMs = System.currentTimeMillis()
                    if (nowMs < verifiedRttBackoffUntilMs) {
                        val waitMs = (verifiedRttBackoffUntilMs - nowMs).coerceIn(200L, 5_000L)
                        diag.event(
                            "EGRESS", "startup-observation-held-by-rtt-backoff",
                            "session" to session,
                            "profile" to profile.id.take(12),
                            "retryAfterMs" to waitMs
                        )
                        timerWorker.schedule({
                            if (isRouteCurrent(session, generation) && coreAlive) {
                                scheduleSyntheticEgressObservation(session, generation, port, profile, attempt)
                            }
                        }, waitMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        return@execute
                    }
                    val app = application as MarbleApplication
                    val domainHealthy = probeDomainHttps(port)
                    val literalHealthy = domainHealthy || probeLiteralIpHttps(port)
                    if (!isRouteCurrent(session, generation) || !coreAlive) {
                        diag.event(
                            "EGRESS", "startup-observation-stale-discarded",
                            "session" to session,
                            "profile" to profile.id.take(12)
                        )
                        return@execute
                    }

                    val decision = EgressObservationPolicy.observe(
                        state = egressObservationState,
                        domainHealthy = domainHealthy,
                        literalHealthy = literalHealthy
                    )
                    egressObservationState = decision.state

                    diag.event(
                        "EGRESS",
                        when (decision.verdict) {
                            EgressObservationPolicy.Verdict.HEALTHY -> "startup-observation-ok"
                            EgressObservationPolicy.Verdict.INCONCLUSIVE ->
                                "startup-observation-inconclusive"
                            EgressObservationPolicy.Verdict.ROUTE_SUSPECT ->
                                "startup-observation-route-suspect"
                        },
                        "session" to session,
                        "literalIpHttps" to literalHealthy,
                        "domainHttps" to domainHealthy,
                        "attempt" to attempt,
                        "observations" to decision.state.observations,
                        "suspicions" to decision.state.consecutiveDnsSuspicions,
                        "reason" to decision.reason,
                        "profile" to profile.id.take(12)
                    )

                    if (decision.requestFamilyTune) {
                        val liveSettings = activeSettings ?: app.repo.settings
                        if (
                            liveSettings.connectTuningEnabled
                        ) {
                            tuningRequested.set(true)
                            diag.event(
                                "TURBO", "startup-family-tune-requested",
                                "session" to session,
                                "profile" to profile.id.take(12),
                                "reason" to decision.reason
                            )
                        }
                        app.repo.intelligence.setDecision(
                            "DNS health probe inconclusive • route remains connected and monitored"
                        )
                        app.repo.refreshIntelligenceStatus()
                    }

                    if (decision.rearmDelayMs > 0L && !domainHealthy) {
                        scheduleSyntheticEgressObservation(
                            session, generation, port, profile, attempt + 1
                        )
                    }
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun scheduleAdaptiveDnsObservation(
        session: String,
        generation: Int,
        port: Int,
        settings: AppSettings,
        delayMs: Long = 15_000L
    ) {
        timerWorker.schedule({
            if (isRouteCurrent(session, generation) && coreAlive) {
                val repo = (application as MarbleApplication).repo
                val throughput = repo.liveDownBps + repo.liveUpBps
                if (throughput < HEAVY_TRAFFIC_BPS) {
                    monitorWorker.execute {
                        if (isRouteCurrent(session, generation) && coreAlive) {
                            runCatching {
                                repo.intelligence.probeDnsResolvers(
                                    port,
                                    settings,
                                    linkEvidenceFor(activeProfileId)
                                )
                            }
                        }
                    }
                } else {
                    diag.event(
                        "DNS", "adaptive-observation-deferred",
                        "session" to session,
                        "jitterControl" to jitterControlActive,
                        "throughputBps" to throughput
                    )

                    scheduleAdaptiveDnsObservation(session, generation, port, settings, 30_000L)
                }
            }
        }, delayMs.coerceIn(5_000L, 60_000L), TimeUnit.MILLISECONDS)
    }

    private fun probeTlsHost(host: String): String = when (host) {
        "1.1.1.1", "1.0.0.1" -> "cloudflare-dns.com"
        "8.8.8.8" -> "dns.google"
        "9.9.9.9" -> "dns.quad9.net"
        else -> host
    }

    private fun egressProbeTimeoutMs(): Int =
        LinkDeadlinePolicy.httpsProbeTimeoutMs(
            linkEvidenceFor(activeProfileId),
            floorMs = 1_500L,
            ceilingMs = 8_000L
        ).toInt().coerceIn(500, 30_000)

    private fun probeEgressTarget(port: Int, host: String, path: String, timeoutMs: Int): Boolean =
        runCatching {
            SocksHttpClient.httpsFirstByteLatency(
                port = port,
                host = host,
                path = path,
                timeoutMs = timeoutMs,
                tlsHost = probeTlsHost(host)
            ) > 0.0
        }.getOrDefault(false)

    private fun probeLiteralIpHttps(port: Int): Boolean {
        val timeoutMs = egressProbeTimeoutMs()
        return JITTER_PROBE_TARGETS.take(3).any { (host, path) ->
            probeEgressTarget(port, host, path, timeoutMs)
        }
    }

    private fun probeDomainHttps(port: Int): Boolean {
        val timeoutMs = egressProbeTimeoutMs()
        return LIVE_DOMAIN_RTT_TARGETS.take(2).any { (host, path) ->
            probeEgressTarget(port, host, path, timeoutMs)
        }
    }

    private fun confirmRouteUnavailable(
        session: String,
        port: Int,
        generation: Int
    ): Boolean {
        if (!isRouteCurrent(session, generation)) return false
        if (!coreAlive) return true
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
            coreAlive &&
            (activeMode != MODE_TUN || hevActive)
    }

    private fun shouldSampleRoute(tick: Int): Boolean {
        if (tick == 1 || tick == 2 || tick == 3) return true

        val repo = (application as MarbleApplication).repo
        val throughput = repo.liveDownBps + repo.liveUpBps
        val cadence = when {
            throughput >= HEAVY_TRAFFIC_BPS -> ROUTE_HEAVY_PROBE_TICKS
            else -> highJitterProbeCadenceTicks()
        }
        return tick > 0 && tick % cadence == 0
    }

    private fun highJitterProbeCadenceTicks(): Int {
        val latched = if (jitterControlActive) ROUTE_DEGRADED_PROBE_TICKS else ROUTE_PROBE_INTERVAL_TICKS
        val shield = highJitterPlan ?: return latched
        return minOf(latched, shield.probeEveryTicks).coerceAtLeast(HighJitterShield.MIN_TICKS)
    }

    private fun resetHighJitterShield() {
        highJitterWindow.clear()
        highJitterShield = HighJitterShield.State()
        highJitterPlan = null
        lastRouteProbeMonotonicMs = 0L
    }

    private fun observeHighJitter(session: String, samples: List<Int>) {
        if (samples.isEmpty()) return

        val nowMs = android.os.SystemClock.elapsedRealtime()
        val isIdleWakeup = lastRouteProbeMonotonicMs > 0L &&
            (nowMs - lastRouteProbeMonotonicMs) >= HighJitterShield.RRC_IDLE_GAP_MS
        lastRouteProbeMonotonicMs = nowMs

        val previousVerdict = highJitterShield.verdict
        var state = highJitterShield
        var plan: HighJitterShield.Plan? = null
        for (sample in samples) {
            val decision = HighJitterShield.observe(
                sample = HighJitterShield.Sample(
                    rttMs = sample.toDouble(),
                    nowMs = nowMs,
                    isRadioWakeup = isIdleWakeup
                ),
                state = state,
                window = highJitterWindow
            )
            state = decision.state
            plan = decision.plan
        }
        val settled = plan ?: return
        highJitterShield = state
        highJitterPlan = settled

        if (settled.verdict != previousVerdict) {
            diag.event(
                "ROUTE", "jitter-shield-verdict",
                "session" to session,
                "from" to previousVerdict.name,
                "to" to settled.verdict.name,
                "detail" to HighJitterShield.describe(settled)
            )
        }
        if (settled.requestRerank) {
            optimizerScanRequested.set(true)
            diag.event(
                "ROUTE", "jitter-shield-rerank-requested",
                "session" to session,
                "detail" to HighJitterShield.describe(settled)
            )
        }
    }

    private fun resetJitterBaselineForProbeHost(nextHost: String) {
        synchronized(routeOutcomeWindow) { routeOutcomeWindow.clear() }

        jitterControlState = jitterControlState.copy(highStreak = 0, lowStreak = 0)
        jitterControlActive = jitterControlState.active
        jitterProbeHost = nextHost
        (application as MarbleApplication).repo.invalidateLiveJitter()
    }

    private fun throttleEvidenceForActiveRoute(): Double {
        if (activeProfileId.isBlank()) return 0.0
        return (application as MarbleApplication).repo
            .intelligence
            .throttleConfidenceFor(activeProfileId)
    }

    /**
     * MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — measure [targets] at once and return the first
     * verified answer.
     *
     * The whole reason this exists is that a probe target is not reliable one at a time. Each
     * literal anycast address in [JITTER_PROBE_TARGETS] is filtered intermittently and
     * independently, so "the pinned target is quiet" is a fact about that target, not about the
     * route — and the only way to learn it quickly is to ask somebody else at the same moment.
     *
     * The loser of the race is cancelled, so a filtered target cannot hold the cycle's budget
     * hostage after the winner has already answered.
     *
     * @return the target that answered and its RTT, or the first target with `-1` when none did.
     */
    private fun raceFirstSample(
        targets: List<String>,
        measure: (String) -> Int
    ): Pair<String, Int> {
        if (targets.isEmpty()) return "" to -1
        if (targets.size == 1) return targets.first() to measure(targets.first())

        val jobs = targets.map { target ->
            routeProbeRacePool.submit<Pair<String, Int>> { target to measure(target) }
        }
        var firstFailure: Pair<String, Int>? = null
        for (job in jobs) {
            val outcome = try {
                job.get(LIVE_RTT_TIMEOUT_MS.toLong() + PROBE_RACE_GRACE_MS, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                job.cancel(true)
                continue
            } catch (_: Throwable) {
                job.cancel(true)
                continue
            }
            if (outcome.second > 0) {
                jobs.forEach { other -> if (other !== job) runCatching { other.cancel(true) } }
                return outcome
            }
            if (firstFailure == null) firstFailure = outcome
        }
        return firstFailure ?: (targets.first() to -1)
    }

    private fun routeProbePartnerFor(host: String): String {
        val order = JITTER_PROBE_TARGETS.map { it.first }
        val at = order.indexOf(host)
        return if (at < 0) order.firstOrNull().orEmpty() else order[(at + 1) % order.size]
    }

    private fun sampleRouteLatency(
        session: String,
        port: Int,
        generation: Int
    ): Boolean {
        if (!isRouteCurrent(session, generation)) return false

        val app = application as MarbleApplication
        val repo = app.repo

        fun publishRollingOutcome(host: String, sample: Int) {
            if (!isRouteCurrent(session, generation) || host != jitterProbeHost) return

            val snapshot = synchronized(routeOutcomeWindow) {
                routeOutcomeWindow.addLast(if (sample > 0) LinkQualityEstimator.sanitaryRtt(sample) else -1)
                while (routeOutcomeWindow.size > ROUTE_WINDOW_SIZE) routeOutcomeWindow.removeFirst()
                val outcomes = routeOutcomeWindow.toList()
                val link = LinkQualityEstimator.summarize(outcomes)
                    ?: return@synchronized intArrayOf(0, -1, 0, 0, outcomes.size, 0, 0)
                intArrayOf(link.medianRttMs, link.ewmaJitterMs, link.successes, link.jitterSamples,
                    link.attempts, link.successPercent, link.p90RttMs)
            }

            if (snapshot[0] <= 0) return
            // MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — latency and jitter are measurements and are

            val holdQuality = routeQualityHoldSamples > 0
            if (holdQuality) {
                routeQualityHoldSamples = (routeQualityHoldSamples - 1).coerceAtLeast(0)
            }
            repo.updateRouteQuality(
                snapshot[0],
                snapshot[1],
                snapshot[2],
                snapshot[3],
                snapshot[4],
                snapshot[5],
                snapshot[6],
                holdQuality
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
                LinkQualityEstimator.sanitaryRtt(
                    SocksHttpClient.httpsFirstByteLatency(
                        port = port,
                        host = host,
                        path = probePath(host),
                        targetPort = 443,
                        timeoutMs = LIVE_RTT_TIMEOUT_MS,
                        tlsHost = probeTlsHost(host)
                    ).roundToInt()
                )
            }.getOrDefault(-1)

        fun measurePinnedBurst(
            host: String,
            firstSample: Int,
            burst: Int = LIVE_RTT_BURST_SAMPLES
        ): List<Int> {
            val wanted = burst.coerceIn(1, LIVE_RTT_BURST_SAMPLES)
            val values = ArrayList<Int>(wanted)

            fun accept(value: Int): Boolean {
                if (value <= 0) {
                    publishRollingOutcome(host, -1)
                    return false
                }
                val verified = value.coerceIn(1, 10_000)
                values += verified

                publishRollingOutcome(host, verified)
                return true
            }

            if (!accept(firstSample)) return values
            while (values.size < wanted && isRouteCurrent(session, generation)) {
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

        fun measureDomainBurst(host: String, path: String, burst: Int = LIVE_RTT_BURST_SAMPLES): List<Int> =
            runCatching {
                SocksHttpClient.tunnelRttBatch(
                    port = port,
                    host = host,
                    path = path,
                    samples = burst.coerceIn(1, LIVE_RTT_BURST_SAMPLES),
                    timeoutMs = LIVE_DOMAIN_RTT_TIMEOUT_MS
                )
            }.getOrNull()
                ?.samplesMs
                ?.filter { it.isFinite() && it > 0.0 }
                ?.map { LinkQualityEstimator.sanitaryRtt(it.roundToInt()) }
                .orEmpty()

        var host = jitterProbeHost

        val probeBurst = highJitterPlan?.probeBurst ?: LIVE_RTT_BURST_SAMPLES
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

        // MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — multi-target from the first sample of a cycle,

        val partner = if (lastRouteCycleVerified) "" else routeProbePartnerFor(host)
        if (partner.isBlank()) {
            repo.updateRouteProbeStatus("Measuring verified RTT • ${probeLabel(host)}")
        } else {
            repo.updateRouteProbeStatus(
                "Measuring verified RTT • ${probeLabel(host)} + ${probeLabel(partner)}"
            )
        }
        val (racedHost, primarySample) = if (partner.isBlank()) {
            host to measureOne(host)
        } else {
            raceFirstSample(listOf(host, partner)) { target -> measureOne(target) }
        }
        if (primarySample > 0 && racedHost != host) {
            resetJitterBaselineForProbeHost(racedHost)
            host = racedHost
            routeQualityHoldSamples = PIVOT_QUALITY_MIN_SAMPLES
            diag.event(
                "ROUTE", "jitter-probe-hedge",
                "session" to session,
                "from" to jitterProbeHost,
                "target" to racedHost,
                "provider" to probeLabel(racedHost),
                "reason" to "pinned target was silent in the previous cycle; partner answered"
            )
        }
        rttSamples = if (primarySample > 0) {
            measurePinnedBurst(host, primarySample, probeBurst)
        } else {
            publishRollingOutcome(host, -1)
            emptyList()
        }

        if (rttSamples.isEmpty() && isRouteCurrent(session, generation)) {
            // MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — the pivot is a race, not a queue.

            val alternates = JITTER_PROBE_TARGETS
                .map { it.first }
                .filterNot { it == host }
                .take(PIVOT_RACE_TARGETS)

            if (alternates.isNotEmpty()) {
                repo.updateRouteProbeStatus(
                    "${probeLabel(host)} RTT unavailable • racing ${alternates.size} alternates"
                )
                val (alternate, alternateSample) = raceFirstSample(alternates) { target -> measureOne(target) }
                if (alternateSample > 0) {
                    resetJitterBaselineForProbeHost(alternate)
                    host = alternate
                    routeQualityHoldSamples = PIVOT_QUALITY_MIN_SAMPLES
                    rttSamples = measurePinnedBurst(alternate, alternateSample, probeBurst)
                    diag.event(
                        "ROUTE", "jitter-probe-pivot",
                        "session" to session,
                        "target" to alternate,
                        "provider" to probeLabel(alternate),
                        "candidates" to alternates.size,
                        "reason" to "previous literal HTTPS target produced zero verified RTTs"
                    )
                }
            }
        }

        if (rttSamples.isEmpty() && isRouteCurrent(session, generation)) {
            val domainTargets = LIVE_DOMAIN_RTT_TARGETS.take(2)
            for ((index, target) in domainTargets.withIndex()) {
                repo.updateRouteProbeStatus(
                    "Literal HTTPS RTT unavailable • verified domain probe " +
                        "${index + 1}/${domainTargets.size}"
                )
                val batch = measureDomainBurst(target.first, target.second, probeBurst)
                if (batch.isEmpty()) continue

                resetJitterBaselineForProbeHost(target.first)
                host = target.first
                routeQualityHoldSamples = PIVOT_QUALITY_MIN_SAMPLES
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

        // MARBLE_ROUTE_PROBE_MULTI_TARGET_V197 — recorded before any early return: the next cycle

        lastRouteCycleVerified = rttSamples.isNotEmpty()

        if (rttSamples.isNotEmpty()) {
            verifiedRttBackoffUntilMs = 0L
        } else {
            val routeAgeMs = (
                (System.nanoTime() - connectStartedNs) / 1_000_000L
            ).coerceAtLeast(0L)
            val trafficMoving =
                activeMode == MODE_TUN && hevActive &&
                    lastTrafficProgressAt > 0L &&
                    (System.currentTimeMillis() - lastTrafficProgressAt) <= RECENT_TRAFFIC_GRACE_MS

            val backoffMs = if (routeAgeMs >= ROUTE_FAILURE_WARMUP_MS && !trafficMoving) {
                VERIFIED_RTT_BACKOFF_MS
            } else {
                VERIFIED_RTT_SHORT_BACKOFF_MS
            }
            verifiedRttBackoffUntilMs = nowMs + backoffMs
            repo.updateRouteProbeStatus(
                "Verified HTTPS RTT unavailable • retrying in ${backoffMs / 1000}s • no synthetic ping shown"
            )
        }

        if (rttSamples.isEmpty() || !isRouteCurrent(session, generation)) {
            if (isRouteCurrent(session, generation)) {
                repo.updateRouteProbeStatus(
                    "Tunnel traffic is active • verified HTTPS RTT is currently unavailable"
                )
            }

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
                    "xrayAlive" to coreAlive,
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
                    !coreAlive -> true
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
                        "xrayAlive" to coreAlive,
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

        observeHighJitter(session, rttSamples)

        repo.intelligence.recordLiveRoute(
            activeProfileId,
            quality.latencyMs,
            repo.liveDownBps + repo.liveUpBps,
            jitterMs,
            successPercent,
            activeSettings ?: repo.settings
        )

        if (activeProfileId.isNotBlank()) {
            val profile = repo.profile(activeProfileId)
            repo.intelligence.recordLiveObservation(
                activeProfileId,
                quality.latencyMs.toDouble(),
                jitterMs.toDouble(),
                successPercent.toDouble(),
                transportType = profile?.let { ProtocolFingerprintAwareVerifier.transportTypeOf(it) }
                    ?: ""
            )
            runCatching { repo.intelligence.attributionTick() }
        }

        val highThreshold = maxOf(
            ROUTE_JITTER_TRIGGER_MS,
            (quality.latencyMs / 4).coerceAtLeast(1)
        )
        val releaseThreshold = maxOf(
            ROUTE_JITTER_RELEASE_MS,
            (quality.latencyMs / 8).coerceAtLeast(1)
        )
        val jitterReady = jitterMs >= 0 && jitterSampleCount >= 2

        val jitterDecision = JitterControlPolicy.evaluate(
            sample = JitterControlPolicy.Sample(
                samples = quality.samples,
                jitterReady = jitterReady,
                jitterMs = jitterMs.toDouble(),
                triggerMs = highThreshold.toDouble(),
                releaseMs = releaseThreshold.toDouble(),

                p95IpdvMs = link.p95IpdvMs.toDouble(),
                lossPercent = link.lossPercent.toDouble(),
                spikePercent = link.spikePercent.toDouble(),

                sawtoothConfidence = throttleEvidenceForActiveRoute()
            ),
            state = jitterControlState,
            nowMs = System.currentTimeMillis()
        )
        jitterControlState = jitterDecision.state
        jitterControlActive = jitterDecision.state.active

        when (jitterDecision.verdict) {
            JitterControlPolicy.Verdict.SUSPECTED_THROTTLE -> diag.event(
                "ROUTE", "jitter-throttle-suspected",
                "session" to session,
                "target" to host,
                "pingMs" to quality.latencyMs,
                "jitterMs" to jitterMs,
                "sawtooth" to throttleEvidenceForActiveRoute(),
                "tick" to jitterDecision.tick
            )
            JitterControlPolicy.Verdict.THROTTLE_CLEARED -> diag.event(
                "ROUTE", "jitter-throttle-cleared",
                "session" to session,
                "target" to host,
                "pingMs" to quality.latencyMs,
                "tick" to jitterDecision.tick
            )
            JitterControlPolicy.Verdict.ENTER -> diag.event(
                "ROUTE", "jitter-control-enter",
                "session" to session,
                "target" to host,
                "pingMs" to quality.latencyMs,
                "jitterMs" to jitterMs,
                "highStreak" to jitterDecision.highStreak,
                "tick" to jitterDecision.tick,
                "minHoldMs" to JitterControlPolicy.MIN_HOLD_MS
            )
            JitterControlPolicy.Verdict.EXIT -> {
                diag.event(
                    "ROUTE", "jitter-control-exit",
                    "session" to session,
                    "target" to host,
                    "pingMs" to quality.latencyMs,
                    "jitterMs" to jitterMs,
                    "lowStreak" to jitterDecision.lowStreak,
                    "tick" to jitterDecision.tick,
                    "minDwellMs" to JitterControlPolicy.MIN_DWELL_MS
                )

                releaseTurboBackoffIfRecovered(session, quality)
            }
            JitterControlPolicy.Verdict.HOLD -> Unit
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
            lastRouteCycleVerified = true
            routeQualityHoldSamples = 0

            jitterControlState = JitterControlPolicy.State()
            jitterControlActive = false
            resetHighJitterShield()
            turboBackoffState = TurboBackoffPolicy.State()
            egressObservationState = EgressObservationPolicy.State()
            pathMtuState = PathMtuPolicy.State()
            goodRouteTicks = 0
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

    @Synchronized
    private fun handleFailure(
        session: String,
        reason: String,
        allowRecovery: Boolean = true,
        recordProfileFailure: Boolean = true,
        faultClass: String = "Core/configuration error"
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
                coreAlive,
            "hevFd" to
                hevFd,
            "tunOpen" to
                (tun != null),
            "profile" to
                failedId
                    .take(12)
        )

        stopDatapath()

        hevActive =
            false
        coreStop()
        closeHevFd("handle-failure")
        repo.resetTelemetry()

        if (recordProfileFailure) runCatching {
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

        if (!allowRecovery) {
            recoveryScheduled.set(false)
            tunReadyPublished.set(false)
            running.set(false)
            repo.setRuntimeState("BLOCKED", "$faultClass • $reason")
            if (holdTun) promoteForeground("BLOCKED • $faultClass • tap Retry", ongoing = true)
            else {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
            return
        }

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

                val identityNowMs = System.currentTimeMillis()
                val identityLadder = RecoveryBackoffPolicy.recordAttempt(recoveryBackoff, identityNowMs)
                recoveryBackoff = identityLadder
                val legacyDelayMs =
                    (750L * attempt)
                        .coerceAtMost(3_000L)
                val delayMs = maxOf(
                    legacyDelayMs,
                    RecoveryBackoffPolicy.delayMs(
                        RecoveryBackoffPolicy.recentAttempts(identityLadder, identityNowMs)
                    )
                )

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
            val circuitNowMs = System.currentTimeMillis()
            if (RecoveryBackoffPolicy.circuitOpen(recoveryBackoff, circuitNowMs)) {
                recoveryScheduled.set(false)
                diag.event(
                    "VPN", "recovery-circuit-open",
                    "session" to activeSession,
                    "attemptsInWindow" to RecoveryBackoffPolicy.recentAttempts(recoveryBackoff, circuitNowMs),
                    "windowMs" to RecoveryBackoffPolicy.WINDOW_MS,
                    "reason" to reason
                )
                if (holdTun) {
                    running.set(true)
                    repo.setRuntimeState(
                        "BLOCKED",
                        "Automatic recovery paused • repeated failures • tap Connect to retry"
                    )
                    promoteForeground(
                        "BLOCKED • Recovery paused • tap Connect",
                        ongoing = true
                    )
                    notifier.alert(
                        SmartNotificationKind.PRIVACY,
                        "recovery-circuit:${activeSession}:$failedId",
                        "Recovery paused",
                        "Too many automatic reconnects in a short window • traffic stays blocked • tap Connect to try again",
                        settings,
                        minIntervalOverrideMs = 60_000L
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
            val ladderWithAttempt = RecoveryBackoffPolicy.recordAttempt(recoveryBackoff, circuitNowMs)
            recoveryBackoff = ladderWithAttempt
            val fallbackDelayMs = RecoveryBackoffPolicy.delayMs(
                RecoveryBackoffPolicy.nextAttemptIndex(ladderWithAttempt, circuitNowMs) - 1
            )
            diag.event(
                "VPN", "recovery-paced",
                "session" to activeSession,
                "attempt" to RecoveryBackoffPolicy.recentAttempts(ladderWithAttempt, circuitNowMs),
                "delayMs" to fallbackDelayMs
            )

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
                        if (fallbackDelayMs > 0L) {
                            try {
                                Thread.sleep(fallbackDelayMs)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                        }
                        if (!isCurrent(session)) {
                            recoveryScheduled.set(false)
                            return@execute
                        }

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
                xrayUp = coreAlive,
                hevUp = hevActive,
                killSwitchArmed = killSwitch,
                previous = repo.sentinel
            )
        )
    }

    private fun profileCompatibilityIssue(profile: ProxyProfile, engine: CoreEngine): String? {
        val repo = (application as MarbleApplication).repo
        val requested = activeSettings ?: repo.settings

        val resolution = runCatching { repo.familyResolutionFor(profile) }.getOrNull()
        resolution?.refusal?.let { refusal ->
            diag.event(
                "VPN", "family-refused",
                "profile" to profile.id.take(12),
                "trace" to resolution.trace
            )
            return refusal
        }
        val settings = resolution?.let { Ipv6FallbackLadder.apply(requested, it) } ?: requested
        if (resolution != null && resolution.degraded) {
            diag.event(
                "VPN", "family-degraded",
                "profile" to profile.id.take(12),
                "trace" to resolution.trace,
                "headline" to resolution.headline
            )
        }
        if (AddressFamilyPolicy.excludedIpv6Endpoint(profile.host, settings)) {
            return AddressFamilyPolicy.IPV6_LITERAL_DISABLED
        }
        if (AddressFamilyPolicy.excludedIpv4Endpoint(profile.host, settings)) {
            return AddressFamilyPolicy.IPV4_LITERAL_DISABLED
        }
        if (!CoreConfigSuperset.dialsPlaintextPublicNodes(settings) &&
            CoreConfigSuperset.wire(profile) == CoreConfigSuperset.Wire.PLAINTEXT_PUBLIC
        ) {
            return CoreConfigSuperset.PLAINTEXT_REFUSAL
        }
        if (engine == CoreEngine.SINGBOX) return SingBoxConfigBuilder.pinnedPeerRefusal(profile)
        if (engine != CoreEngine.XRAY) return null
        CoreConfigSuperset.coreGapIssue(profile, engine)?.let { return it }
        val verdict = CoreConfigSuperset.verdict(profile, engine, settings)
        if (verdict.refused) return CoreConfigSuperset.PLAINTEXT_REFUSAL
        if (verdict.note.isNotEmpty()) {
            configBlockGuard.reset(profile.id)
            diag.event(
                if (engine == CoreEngine.SINGBOX) "SINGBOX" else "XRAY", "profile-wire-note",
                "profile" to profile.id.take(12),
                "wire" to verdict.wire.name,
                "reason" to verdict.reason,
                "note" to verdict.note
            )
        }
        return null
    }

    private fun conciseFailure(raw: String): String {
        val compact = raw.replace(Regex("\\s+"), " ").trim()
        val lower = compact.lowercase()
        return when {
            "vless without tls or other encryption is prohibited" in lower ->
                "Unsupported VLESS • pick a server with TLS/REALITY"
            "failed to build outbound config" in lower ->
                "Xray rejected this server configuration • check protocol/TLS settings"
            "failed to load config files" in lower ->
                "Xray rejected the generated configuration"
            compact.length > 240 -> compact.take(237) + "…"
            else -> compact.ifBlank { "Connection could not be started" }
        }
    }

    private fun failBeforeTunnel(reason: String, loud: Boolean = true) {
        running.set(false)
        val concise = conciseFailure(reason)
        val repo = (application as MarbleApplication).repo
        if (tun != null && activeMode == MODE_TUN) {
            updateSentinel(killSwitch = true)
            repo.setRuntimeState("BLOCKED", "Kill switch active • $concise")
            if (loud) repo.setRuntimeMessage(concise)
            promoteForeground("BLOCKED • Kill switch holding traffic", ongoing = true)
            diag.event("VPN", "preflight-failed-tun-held", "reason" to concise)
            return
        }
        if (loud) {
            repo.setRuntimeState("DISCONNECTED", concise)
            repo.setRuntimeMessage(concise)
            diag.event("VPN", "startup-failed-before-tun", "reason" to concise)
        } else {
            diag.event("VPN", "startup-failed-folded", "reason" to concise)
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    @Synchronized
    private fun cleanupRuntime(setDisconnected: Boolean, preserveTun: Boolean = false) {
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
        stopDatapath()
        hevActive = false
        releaseLowLatencyWifiLock()
        coreStop()
        closeHevFd("runtime-cleanup")
        if (!preserveTun) closeTun()

        val repo = (application as MarbleApplication).repo
        repo.resetTelemetry()
        activeSettings = null
        activeProfileId = ""
        activeProfileSourceId = ""
        pinnedExitV4 = ""
        pinnedExitV6 = ""
        ipv6RouteCaptured = preserveTun && tun != null
        if (setDisconnected) {
            repo.setRuntimeState("DISCONNECTED", "User disconnected")

            configBlockGuard.reset()
        }
        if (preserveTun && tun != null) {
            updateSentinel(killSwitch = true)
        } else {
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
        }
        diag.event("VPN", "runtime-clean", "session" to oldSession,
            "setDisconnected" to setDisconnected, "tunHeld" to (tun != null))
    }

    /**
     * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — stops the datapath this session actually started, and
     * nothing else.
     *
     * V222 called both quits on every cleanup, on the belief that the idle one is a no-op. It is
     * not: `hev_socks5_tunnel_stop()` ORs a sticky `SYNC_STOP` bit into HEV's `tsync`, so a
     * tun2socks disconnect left HEV armed to fall out of its own run loop the next time it was
     * used in this process — and, when the bit was set while HEV still owned `SYNC_SEND` but its
     * event socketpair had already been closed, the `assert()` on `write(event_fds[1], …)` inside
     * that function aborts the whole process. That abort is a SIGABRT, which is exactly the
     * `reason=SIGNALED status=6` record with no Java stack and no tombstone.
     *
     * The tun2socks stop is joined ([Tun2SocksTunnel.stopAndWait]) because gVisor keeps polling
     * the descriptor until its stopfd fires: the caller closes the descriptor next, and it may
     * only do that once the core has said it stopped. HEV blocks in its own loop and owns no
     * descriptor it did not open, so it is only signalled, exactly as before.
     */
    private fun stopDatapath() {
        val core = activeTunnelCore
        // The datapath is live if its flag says so, or if its worker still holds the descriptor —
        // the window between adopting the descriptor and entering the native run is real, and a
        // cleanup that lands inside it has to stop the run rather than walk past it.
        val live = hevActive || datapathOwnsFd.get()
        when (TunnelShutdownPolicy.stopPlan(core, live)) {
            DatapathStop.NONE ->
                diag.event(
                    "TUNNEL", "datapath-stop-skipped",
                    "session" to activeSession, "tunnelCore" to core.id,
                    "hevActive" to hevActive, "datapathOwnsFd" to datapathOwnsFd.get(),
                    "hevFd" to hevFd
                )
            DatapathStop.QUIT -> {
                diag.event(
                    "TUNNEL", "datapath-stop",
                    "session" to activeSession, "tunnelCore" to core.id,
                    "hevFd" to hevFd, "join" to false
                )
                runCatching { HevTunnel.quit() }
                    .onFailure { diag.error("HEV", "quit-failed", it, "session" to activeSession) }
            }
            DatapathStop.QUIT_AND_JOIN -> {
                val joined = runCatching { Tun2SocksTunnel.stopAndWait() }.getOrDefault(false)
                diag.event(
                    "TUN2SOCKS", if (joined) "datapath-stop-ack" else "datapath-stop-timeout",
                    "session" to activeSession,
                    "hevFd" to hevFd,
                    "timeoutMs" to Tun2SocksTunnel.STOP_TIMEOUT_MS
                )
            }
        }
    }

    /**
     * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — takes the duplicated TUN descriptor for this datapath run.
     * Recorded before any native call so a concurrent cleanup defers to this worker instead of
     * closing the number underneath it.
     */
    private fun adoptDatapathFd(dupFd: Int) {
        synchronized(hevFdLock) { hevFd = dupFd }
        datapathOwnsFd.set(true)
        diag.event(
            "TUN", "fd-owned-by-datapath",
            "hevFd" to dupFd, "tunnelCore" to activeTunnelCore.id
        )
    }

    /**
     * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — the native run is over, so the descriptor is this worker's
     * to close. This is the one place a datapath-owned descriptor leaves the process, and it runs
     * on every exit path of the run (see [runTun]'s `finally`).
     */
    private fun releaseDatapathFd(core: TunnelCore) {
        datapathOwnsFd.set(false)
        diag.event(
            "TUN", "datapath-released-fd",
            "hevFd" to hevFd, "tunnelCore" to core.id
        )
        closeHevFd("datapath-worker")
    }

    /**
     * MARBLE_TUN2SOCKS_SHUTDOWN_V223 — closes the duplicated TUN descriptor exactly once.
     *
     * Two guarantees, both new in V223:
     *
     *  - **at most one close.** The number is taken out of [hevFd] under [hevFdLock], so the
     *    datapath worker and any cleanup thread asking at the same time cannot both close it.
     *    V222 closed it here *and* the Go core closed it in `marble_t2s_run`, which is a double
     *    close of a number the app may already have recycled — the disconnect abort.
     *  - **never under a live datapath.** While [datapathOwnsFd] is set the close is deferred to
     *    [releaseDatapathFd] and logged. gVisor's fdbased dispatcher polls this number until its
     *    stopfd fires; closing it first would let the netstack read whatever descriptor inherits
     *    the number next.
     */
    private fun closeHevFd(owner: String) {
        if (!TunnelShutdownPolicy.mayCloseDescriptor(datapathOwnsFd.get())) {
            diag.event(
                "TUN", "fd-close-deferred",
                "hevFd" to hevFd, "owner" to owner, "tunnelCore" to activeTunnelCore.id,
                "reason" to "the datapath still owns the descriptor and closes it when its run returns"
            )
            return
        }
        val fd = synchronized(hevFdLock) {
            val current = hevFd
            hevFd = -1
            current
        }
        if (fd < 0) return
        diag.event(
            "TUN", "fd-close",
            "hevFd" to fd, "owner" to owner, "tunnelCore" to activeTunnelCore.id
        )
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            .onFailure { diag.error("TUN", "fd-close-failed", it, "hevFd" to fd, "owner" to owner) }
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
        if (running.get() || tun != null || hevActive || coreAlive) cleanupRuntime(setDisconnected = true)
        runCatching { connectivityDiagnostics?.close() }
        connectivityDiagnostics = null
        runCatching { networkListener?.close() }
        networkListener = null
        timerWorker.shutdownNow()
        monitorWorker.shutdownNow()
        controlWorker.shutdownNow()
        connectionWorker.shutdownNow()
        // MARBLE_TUN2SOCKS_SHUTDOWN_V223 — last chance for the descriptor: a datapath run that
        // already returned has closed it in [releaseDatapathFd], and one that never returns is
        // left to the process teardown rather than closed under a live dispatcher.
        closeHevFd("service-destroy")
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
