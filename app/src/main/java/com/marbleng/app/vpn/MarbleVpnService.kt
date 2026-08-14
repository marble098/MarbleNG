package com.marbleng.app.vpn

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.NetworkSnapshot
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.SocksHttpClient
import com.marbleng.app.core.SmartNotificationKind
import com.marbleng.app.core.SmartNotifier
import com.marbleng.app.core.XrayManager
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.nativebridge.HevTunnel
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Fail-closed Android tunnel controller.
 *
 * The Android VPN is established before Xray. If Xray/HEV fails later, the VPN fd stays open and
 * traffic is blackholed while Marble Intelligence attempts bounded fallback on the same TUN.
 */
class MarbleVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.marbleng.START"
        const val ACTION_STOP = "com.marbleng.STOP"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_MODE = "mode"
        const val MODE_TUN = "tun"
        const val MODE_PROXY = "proxy"
        const val CHANNEL = "marbleng-vpn"
        const val NOTIFY = 7301
        private const val ROUTE_PROBE_INTERVAL_TICKS = 30
        private const val PROBE_FAILURES_BEFORE_RECOVERY = 3
    }

    private var tun: ParcelFileDescriptor? = null
    private var hevFd = -1
    private val running = AtomicBoolean(false)
    private val recoveryScheduled = AtomicBoolean(false)
    private val routeProbeRequested = AtomicBoolean(false)

    // HEV is process-global; start/stop/recovery is serialized here.
    private val connectionWorker = Executors.newSingleThreadExecutor()
    private val monitorWorker = Executors.newFixedThreadPool(3)

    private lateinit var xray: XrayManager
    private lateinit var diag: RuntimeDiagnostics
    private lateinit var notifier: SmartNotifier
    private var networkListener: Closeable? = null

    @Volatile private var activeSession = ""
    @Volatile private var activeMode = MODE_TUN
    @Volatile private var activeProfileId = ""
    @Volatile private var activeSettings: AppSettings? = null
    @Volatile private var hevActive = false
    @Volatile private var activeMtu = 1500
    @Volatile private var connectStartedNs = 0L
    @Volatile private var consecutiveProbeFailures = 0
    @Volatile private var lastNetworkKey = ""
    @Volatile private var lastNetworkValidated = false

    private val recoveryTried = linkedSetOf<String>()
    private val latencyWindow = ArrayDeque<Int>()
    private var probeIndex = 0

    override fun onCreate() {
        super.onCreate()
        val app = application as MarbleApplication
        xray = app.xray
        diag = RuntimeDiagnostics(this)
        notifier = SmartNotifier(this)
        notifier.ensureChannels()
        app.repo.intelligence.startMonitoring()
        networkListener = app.repo.intelligence.addNetworkListener(::onUnderlyingNetworkChanged)
        diag.event("VPN", "service-created", "system" to diag.systemSnapshot())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return runCatching {
            diag.event("VPN", "command", "action" to intent?.action, "startId" to startId, "flags" to flags)
            when (intent?.action) {
                ACTION_STOP -> shutdown(true)
                ACTION_START -> {
                    val id = intent.getStringExtra(EXTRA_PROFILE)
                    if (id.isNullOrBlank()) failBeforeTunnel("Missing profile id")
                    else startConnection(id, intent.getStringExtra(EXTRA_MODE) ?: MODE_TUN)
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
    private fun startConnection(id: String, mode: String) {
        if (running.get() || tun != null || hevActive || xray.isAlive) cleanupRuntime(setDisconnected = false)

        val app = application as MarbleApplication
        val profile = app.repo.profile(id) ?: run {
            diag.event("VPN", "profile-missing", "profileId" to id.take(12))
            failBeforeTunnel("Profile no longer exists")
            return
        }
        val settings = app.repo.effectiveSettingsFor(profile)
        val normalizedMode = if (mode == MODE_PROXY) MODE_PROXY else MODE_TUN
        val port = if (normalizedMode == MODE_PROXY) settings.localProxyPort else settings.socksPort
        val session = System.currentTimeMillis().toString(36) + "-" + Integer.toHexString(profile.id.hashCode())

        activeSession = session
        activeMode = normalizedMode
        activeProfileId = profile.id
        activeSettings = settings
        connectStartedNs = System.nanoTime()
        consecutiveProbeFailures = 0
        recoveryScheduled.set(false)
        synchronized(recoveryTried) {
            recoveryTried.clear()
            recoveryTried += profile.id
        }
        synchronized(latencyWindow) { latencyWindow.clear() }

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

        connectionWorker.execute {
            if (!isCurrent(session)) return@execute
            if (normalizedMode == MODE_TUN && !establishTun(profile, session, settings)) {
                handleFailure(session, "VPN establish failed")
                return@execute
            }
            if (!isCurrent(session)) return@execute
            startXrayAndForward(profile, session, port, settings, recovering = false)
        }
    }

    private fun startXrayAndForward(
        profile: ProxyProfile,
        session: String,
        port: Int,
        settings: AppSettings,
        recovering: Boolean
    ) {
        val app = application as MarbleApplication
        activeProfileId = profile.id
        activeSettings = settings
        connectStartedNs = System.nanoTime()

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

        val chainProfile = if (settings.chainEnabled) {
            app.repo.profile(settings.chainSecondProfileId)?.takeUnless { it.id == profile.id }
        } else null

        if (!xray.start(profile, port, settings, chainProfile)) {
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

        diag.event("XRAY", "socks-ready", "session" to session, "alive" to xray.isAlive, "port" to port)
        monitorWorker.execute {
            runCatching { app.repo.intelligence.probeDnsResolvers(port, settings) }
        }

        if (activeMode == MODE_PROXY) {
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
            startProxyMonitor(session, port)
            return
        }
        runTun(profile, session, port, settings, recovering)
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

        var dnsCount = 0
        listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { dns ->
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

        // Always attempt to capture IPv6. Even when the physical network has no IPv6, keeping the
        // route in the VPN prevents an OEM/app from treating the missing family as permission to leak.
        runCatching {
            builder.addAddress("fc00::1", 128).addRoute("::", 0)
        }.onSuccess {
            diag.event("TUN", "ipv6-enabled", "session" to session)
        }.onFailure {
            diag.error("TUN", "ipv6-builder-failed", it, "session" to session)
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
        diag.event(
            "TUN", "established",
            "session" to session,
            "vpnFd" to established.fd,
            "mtu" to mtu,
            "splitMode" to settings.splitTunnelMode.name,
            "splitPackages" to packages.size,
            "dnsCount" to dnsCount,
            "dnsHijack" to settings.dnsHijackEnabled
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
        recovering: Boolean
    ) {
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

        val thermal = (application as MarbleApplication).repo.intelligence.thermalBudget(settings)
        val sessions = if (thermal < 0.55) 2048 else 4096
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
            "  tcp-buffer-size: 65536",
            "  udp-recv-buffer-size: 524288",
            "  max-session-count: $sessions"
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
            "sessions" to sessions
        )
        if (!isCurrent(session)) {
            closeHevFd()
            return
        }

        val app = application as MarbleApplication
        app.repo.markConnected(profile)
        app.repo.intelligence.recordConnect(profile.id, true, elapsedConnectMs(), settings)
        notifyNow("Protected • ${profile.name}", true)
        notifier.alert(
            if (recovering) SmartNotificationKind.RECOVERY else SmartNotificationKind.CONNECTION,
            if (recovering) "recovered:$session:${profile.id}" else "connected:$session:${profile.id}",
            if (recovering) "Route recovered" else "VPN protected",
            if (recovering) "Traffic moved safely to ${profile.name}" else profile.name,
            settings
        )
        recoveryScheduled.set(false)
        consecutiveProbeFailures = 0
        hevActive = true
        updateSentinel(killSwitch = true)
        startTelemetry(session, socksPort)

        diag.event("HEV", "run-enter", "session" to session, "hevFd" to hevFd, "xrayAlive" to xray.isAlive)
        val result = runCatching { HevTunnel.run(cfg, hevFd) }
        hevActive = false
        val code = result.getOrElse {
            diag.error("HEV", "jni-run-exception", it, "session" to session, "hevFd" to hevFd)
            -10001
        }
        diag.event("HEV", "run-exit", "session" to session, "code" to code, "runningFlag" to running.get(), "xrayAlive" to xray.isAlive)

        if (isCurrent(session)) {
            // If monitorWorker already scheduled recovery and quit HEV, don't schedule it twice.
            if (!recoveryScheduled.get()) handleFailure(session, "HEV stopped ($code)")
        }
    }

    private fun startProxyMonitor(session: String, port: Int) {
        monitorWorker.execute {
            var tick = 0
            while (isCurrent(session) && activeMode == MODE_PROXY) {
                if (!xray.isAlive) {
                    handleFailure(session, "Xray local proxy stopped")
                    return@execute
                }
                if (tick == 2 || tick % ROUTE_PROBE_INTERVAL_TICKS == 0 || routeProbeRequested.getAndSet(false)) {
                    sampleRouteLatency(session, port)
                }
                if (!sleepQuietly(1_000L)) return@execute
                tick++
            }
        }
    }

    private fun startTelemetry(session: String, port: Int) {
        monitorWorker.execute {
            val repo = (application as MarbleApplication).repo
            var lastUp = -1L
            var lastDown = -1L
            var lastT = System.nanoTime()
            var tick = 0
            while (isCurrent(session) && hevActive) {
                if (!sleepQuietly(1_000L)) return@execute
                if (!isCurrent(session) || !hevActive) break
                val stats = runCatching { HevTunnel.stats() }.getOrNull()
                if (stats != null && stats.size >= 4) {
                    val now = System.nanoTime()
                    val dt = (now - lastT) / 1e9
                    val up = stats[1]
                    val down = stats[3]
                    if (lastUp >= 0 && lastDown >= 0 && dt > 0.25) {
                        repo.updateTelemetry(
                            ((down - lastDown).coerceAtLeast(0) / dt).toLong(),
                            ((up - lastUp).coerceAtLeast(0) / dt).toLong()
                        )
                    }
                    lastUp = up
                    lastDown = down
                    lastT = now
                }
                if (tick == 1 || tick % ROUTE_PROBE_INTERVAL_TICKS == 0 || routeProbeRequested.getAndSet(false)) {
                    sampleRouteLatency(session, port)
                }
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
            repo.resetTelemetry()
        }
    }

    /** Complete SOCKS + remote TCP + TLS + HTTP route health, rolling median. */
    private fun sampleRouteLatency(
        session: String,
        port: Int
    ): Boolean {
        val probes =
            arrayOf(
                "cp.cloudflare.com" to
                    "/generate_204",
                "www.gstatic.com" to
                    "/generate_204",
                "connectivitycheck.gstatic.com" to
                    "/generate_204"
            )

        val (host, path) =
            probes[
                probeIndex++ %
                    probes.size
            ]

        val ms =
            runCatching {
                val result =
                    SocksHttpClient.get(
                        port,
                        host,
                        path,
                        5_000,
                        1024
                    )

                if (
                    result.status in
                    200..399
                ) {
                    result.elapsedMs
                        .roundToInt()
                } else {
                    -1
                }
            }.getOrDefault(-1)

        val app =
            application as
                MarbleApplication
        val repo =
            app.repo

        if (
            ms <= 0 ||
            !running.get()
        ) {
            consecutiveProbeFailures++

            diag.event(
                "ROUTE",
                "probe-failed",
                "session" to
                    session,
                "count" to
                    consecutiveProbeFailures,
                "profile" to
                    activeProfileId
                        .take(12)
            )

            val settings =
                activeSettings
                    ?: repo.settings

            val recoveryEnabled =
                settings
                    .smartFallbackEnabled ||
                    settings
                        .networkChangeRecoveryEnabled

            if (
                recoveryEnabled &&
                consecutiveProbeFailures >=
                PROBE_FAILURES_BEFORE_RECOVERY
            ) {
                // Hysteresis: the whole outage is recorded once by handleFailure().
                handleFailure(
                    session,
                    "Route health degraded after " +
                        "$consecutiveProbeFailures probes"
                )
            } else if (
                recoveryEnabled
            ) {
                // Confirm a transient failure quickly rather than waiting another normal interval.
                routeProbeRequested
                    .set(true)
            }

            return false
        }

        consecutiveProbeFailures =
            0

        val median =
            synchronized(
                latencyWindow
            ) {
                latencyWindow
                    .addLast(ms)

                while (
                    latencyWindow.size >
                    5
                ) {
                    latencyWindow
                        .removeFirst()
                }

                latencyWindow
                    .toList()
                    .sorted()[
                        latencyWindow.size /
                            2
                    ]
            }

        repo.updatePing(
            median
        )

        repo.intelligence
            .recordRoute(
                activeProfileId,
                median,
                repo.liveDownBps +
                    repo.liveUpBps,
                activeSettings
                    ?: repo.settings
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

        val settings =
            activeSettings
                ?: app.repo.settings

        if (
            settings
                .networkChangeRecoveryEnabled
        ) {
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
        repo.intelligence
            .recordConnect(
                failedId,
                false,
                elapsedConnectMs(),
                settings
            )

        updateSentinel(
            killSwitch =
                holdTun
        )

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
        activeSettings =
            settings
        consecutiveProbeFailures =
            0

        synchronized(
            latencyWindow
        ) {
            latencyWindow
                .clear()
        }

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
                xrayUp = xray.isAlive,
                hevUp = hevActive,
                killSwitchArmed = killSwitch,
                previous = repo.sentinel
            )
        )
    }

    private fun failBeforeTunnel(reason: String) {
        running.set(false)
        (application as MarbleApplication).repo.setRuntimeState("BLOCKED", reason)
        diag.event("VPN", "startup-failed", "reason" to reason)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    @Synchronized
    private fun cleanupRuntime(setDisconnected: Boolean) {
        val oldSession = activeSession
        running.set(false)
        activeSession = ""
        recoveryScheduled.set(false)
        routeProbeRequested.set(false)
        if (hevActive) runCatching { HevTunnel.quit() }
        hevActive = false
        xray.stop()
        closeHevFd()
        closeTun()

        val repo = (application as MarbleApplication).repo
        repo.resetTelemetry()
        activeSettings = null
        activeProfileId = ""
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
        runCatching { networkListener?.close() }
        networkListener = null
        monitorWorker.shutdownNow()
        connectionWorker.shutdownNow()
        super.onDestroy()
    }

    private fun isCurrent(session: String): Boolean = running.get() && activeSession == session

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

