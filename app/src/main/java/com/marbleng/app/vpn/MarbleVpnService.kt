package com.marbleng.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.SocksHttpClient
import com.marbleng.app.core.XrayManager
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.nativebridge.HevTunnel
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

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
    }

    private var tun: ParcelFileDescriptor? = null
    private var hevFd = -1
    private val running = AtomicBoolean(false)

    /*
     * Connection work is deliberately serialized. HEV is a process-global native runtime;
     * allowing two start paths to overlap after rapid connect/reconnect taps can make the JNI
     * lifecycle race with quit(). Monitoring stays on a separate small pool.
     */
    private val connectionWorker = Executors.newSingleThreadExecutor()
    private val monitorWorker = Executors.newFixedThreadPool(2)

    private lateinit var xray: XrayManager
    private lateinit var diag: RuntimeDiagnostics

    @Volatile private var activeSession = ""
    @Volatile private var activeMode = MODE_TUN
    @Volatile private var hevActive = false

    private val latencyWindow = ArrayDeque<Int>()
    private var probeIndex = 0

    override fun onCreate() {
        super.onCreate()
        xray = (application as MarbleApplication).xray
        diag = RuntimeDiagnostics(this)
        createChannel()
        diag.event("VPN", "service-created", "system" to diag.systemSnapshot())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return runCatching {
            diag.event("VPN", "command", "action" to intent?.action, "startId" to startId, "flags" to flags)
            when (intent?.action) {
                ACTION_STOP -> shutdown(true)
                ACTION_START -> {
                    val id = intent.getStringExtra(EXTRA_PROFILE)
                    if (id.isNullOrBlank()) {
                        failBeforeTunnel("Missing profile id")
                    } else {
                        startConnection(id, intent.getStringExtra(EXTRA_MODE) ?: MODE_TUN)
                    }
                }
                null -> {
                    // START_NOT_STICKY prevents ghost restarts with no profile after process death.
                    diag.event("VPN", "null-intent-ignored")
                }
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
        if (running.get() || tun != null || hevActive || xray.isAlive) {
            cleanupRuntime(setDisconnected = false)
        }

        val app = application as MarbleApplication
        val profile = app.repo.profile(id) ?: run {
            diag.event("VPN", "profile-missing", "profileId" to id.take(12))
            failBeforeTunnel("Profile no longer exists")
            return
        }

        val settings = app.repo.settings
        val normalizedMode = if (mode == MODE_PROXY) MODE_PROXY else MODE_TUN
        val port = if (normalizedMode == MODE_PROXY) settings.localProxyPort else settings.socksPort
        val session = System.currentTimeMillis().toString(36) + "-" + Integer.toHexString(profile.id.hashCode())

        activeSession = session
        activeMode = normalizedMode
        synchronized(latencyWindow) { latencyWindow.clear() }

        diag.event(
            "VPN", "connect-request",
            "session" to session,
            "profileId" to profile.id.take(12),
            "profileName" to profile.name,
            "mode" to normalizedMode,
            "socksPort" to port
        )

        val promoted = promoteForeground(
            if (normalizedMode == MODE_PROXY) {
                "Starting local proxy • ${profile.name}"
            } else {
                "Securing device route • ${profile.name}"
            },
            ongoing = true
        )
        if (!promoted) {
            failBeforeTunnel("Android rejected foreground-service startup")
            return
        }

        running.set(true)

        connectionWorker.execute {
            if (!isCurrent(session)) return@execute

            /*
             * Full TUN is established before Xray starts. This closes the old window where device
             * traffic could continue directly during proxy startup. MarbleNG itself is excluded
             * because the standalone Xray child shares this UID and otherwise loops into its own
             * VPN. App management requests are separately guarded in AppRepository.
             */
            if (normalizedMode == MODE_TUN && !establishTun(profile, session)) {
                handleFailure(session, "VPN establish failed")
                return@execute
            }

            if (!isCurrent(session)) return@execute

            diag.event("XRAY", "start-begin", "session" to session, "port" to port, "mode" to normalizedMode)
            if (!xray.start(profile, port, settings)) {
                handleFailure(
                    session,
                    xray.lastStartError.ifBlank { "Xray rejected profile or routing policy" }
                )
                return@execute
            }

            if (!isCurrent(session)) {
                xray.stop()
                return@execute
            }

            diag.event("XRAY", "socks-ready", "session" to session, "alive" to xray.isAlive, "port" to port)

            if (normalizedMode == MODE_PROXY) {
                app.repo.markConnected(profile)
                notifyNow("SOCKS5 • 127.0.0.1:$port • ${profile.name}", true)
                startProxyMonitor(session, port)
                return@execute
            }

            runTun(profile, session, port)
        }
    }

    private fun establishTun(profile: ProxyProfile, session: String): Boolean {
        val builder = Builder()
            .setSession("MarbleNG • ${profile.name}")
            .setMtu(8500)
            .setBlocking(false)
            .addAddress("198.18.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")

        runCatching {
            builder.addAddress("fc00::1", 128)
                .addRoute("::", 0)
        }.onSuccess {
            diag.event("TUN", "ipv6-enabled", "session" to session)
        }.onFailure {
            diag.error("TUN", "ipv6-builder-failed", it, "session" to session)
        }

        /*
         * Required with a standalone Xray executable: the Xray process has the app UID and must
         * reach the physical network rather than being captured by its own TUN.
         */
        val selfDisallowed = runCatching { builder.addDisallowedApplication(packageName) }
            .onSuccess {
                diag.event("TUN", "self-disallowed-for-core-loop-prevention", "session" to session)
            }
            .onFailure {
                diag.error("TUN", "self-disallow-failed", it, "session" to session)
            }
            .isSuccess
        if (!selfDisallowed) return false

        val established = runCatching { builder.establish() }
            .onFailure { diag.error("TUN", "establish-exception", it, "session" to session) }
            .getOrNull()
            ?: return false

        if (!isCurrent(session)) {
            runCatching { established.close() }
            return false
        }

        tun = established
        diag.event("TUN", "established", "session" to session, "vpnFd" to established.fd, "mtu" to 8500)
        return true
    }

    private fun runTun(profile: ProxyProfile, session: String, socksPort: Int) {
        val currentTun = tun
        if (currentTun == null) {
            handleFailure(session, "TUN disappeared before HEV startup")
            return
        }

        diag.prepareHevSession()

        val dupFd = runCatching {
            ParcelFileDescriptor.dup(currentTun.fileDescriptor).detachFd()
        }.onFailure {
            diag.error("TUN", "dup-fd-failed", it, "session" to session)
        }.getOrNull()

        if (dupFd == null) {
            handleFailure(session, "TUN fd duplication failed")
            return
        }
        hevFd = dupFd

        /*
         * Keep this close to upstream HEV defaults. "warning" is not a documented HEV log level;
         * valid levels are debug/info/warn/error. Using error also avoids continuous log I/O.
         * ICMP local-reply is disabled so the tunnel does not manufacture misleading ping replies.
         */
        val cfg = listOf(
            "tunnel:",
            "  mtu: 8500",
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
            "  max-session-count: 4096"
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
            "sha256" to diag.sha256(cfg)
        )

        if (!isCurrent(session)) {
            closeHevFd()
            return
        }

        val app = application as MarbleApplication
        app.repo.markConnected(profile)
        notifyNow("Protected • ${profile.name}", true)

        hevActive = true
        startTelemetry(session, socksPort)

        diag.event("HEV", "run-enter", "session" to session, "hevFd" to hevFd, "xrayAlive" to xray.isAlive)
        val result = runCatching { HevTunnel.run(cfg, hevFd) }
        hevActive = false

        val code = result.getOrElse {
            diag.error("HEV", "jni-run-exception", it, "session" to session, "hevFd" to hevFd)
            -10001
        }
        diag.event(
            "HEV", "run-exit",
            "session" to session,
            "code" to code,
            "runningFlag" to running.get(),
            "xrayAlive" to xray.isAlive
        )

        if (isCurrent(session)) {
            handleFailure(session, "HEV stopped ($code)")
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

                if (tick == 2 || tick % ROUTE_PROBE_INTERVAL_TICKS == 0) {
                    sampleRouteLatency(port)
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

                if (tick == 1 || tick % ROUTE_PROBE_INTERVAL_TICKS == 0) {
                    sampleRouteLatency(port)
                }
                tick++
            }

            repo.resetTelemetry()
        }
    }

    /**
     * Measures the complete SOCKS + remote TCP + TLS + HTTP route.
     * A rolling median suppresses one-off spikes. Probing every ~30 seconds avoids the old
     * behavior of creating a fresh TLS handshake every five seconds forever.
     */
    private fun sampleRouteLatency(port: Int) {
        val probes = arrayOf(
            "cp.cloudflare.com" to "/generate_204",
            "www.gstatic.com" to "/generate_204",
            "connectivitycheck.gstatic.com" to "/generate_204"
        )
        val (host, path) = probes[probeIndex++ % probes.size]

        val ms = runCatching {
            val result = SocksHttpClient.get(port, host, path, 5_000, 1024)
            if (result.status in 200..399) result.elapsedMs.roundToInt() else -1
        }.getOrDefault(-1)

        if (ms <= 0 || !running.get()) return

        val median = synchronized(latencyWindow) {
            latencyWindow.addLast(ms)
            while (latencyWindow.size > 5) latencyWindow.removeFirst()
            latencyWindow.toList().sorted()[latencyWindow.size / 2]
        }
        (application as MarbleApplication).repo.updatePing(median)
    }

    /**
     * Fail closed in full-TUN mode:
     * - stop Xray/HEV
     * - close only HEV's duplicate fd
     * - keep the Android VPN fd and foreground service alive
     *
     * With no userspace forwarder reading the TUN, captured device traffic is blackholed instead
     * of silently falling back to the physical network. Local-proxy mode cannot provide a device
     * kill switch by design, so it shuts down normally.
     */
    @Synchronized
    private fun handleFailure(session: String, reason: String) {
        if (session.isNotBlank() && activeSession.isNotBlank() && session != activeSession) return

        val holdTun = activeMode == MODE_TUN && tun != null

        diag.event(
            "VPN", "blocked",
            "session" to activeSession,
            "mode" to activeMode,
            "reason" to reason,
            "killSwitchHold" to holdTun,
            "xrayAlive" to xray.isAlive,
            "hevFd" to hevFd,
            "tunOpen" to (tun != null)
        )

        if (hevActive) runCatching { HevTunnel.quit() }
        hevActive = false
        xray.stop()
        closeHevFd()

        val repo = (application as MarbleApplication).repo
        repo.resetTelemetry()

        if (holdTun) {
            running.set(true)
            repo.setRuntimeState("BLOCKED", "Kill switch active • $reason")
            promoteForeground("BLOCKED • Kill switch holding traffic", ongoing = true)
        } else {
            running.set(false)
            closeTun()
            repo.setRuntimeState("BLOCKED", reason)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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

        if (hevActive) runCatching { HevTunnel.quit() }
        hevActive = false

        xray.stop()
        closeHevFd()
        closeTun()

        val repo = (application as MarbleApplication).repo
        repo.resetTelemetry()
        if (setDisconnected) {
            repo.setRuntimeState("DISCONNECTED", "User disconnected")
        }

        diag.event("VPN", "runtime-clean", "session" to oldSession, "setDisconnected" to setDisconnected)
    }

    private fun closeHevFd() {
        val fd = hevFd
        hevFd = -1
        if (fd >= 0) {
            runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
        }
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
        if (running.get() || tun != null || hevActive || xray.isAlive) {
            cleanupRuntime(setDisconnected = true)
        }
        monitorWorker.shutdownNow()
        connectionWorker.shutdownNow()
        super.onDestroy()
    }

    private fun isCurrent(session: String): Boolean =
        running.get() && activeSession == session

    private fun sleepQuietly(ms: Long): Boolean = try {
        Thread.sleep(ms)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun promoteForeground(text: String, ongoing: Boolean): Boolean {
        val notification = note(text, ongoing)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        return runCatching {
            ServiceCompat.startForeground(this, NOTIFY, notification, type)
        }.onFailure {
            diag.error("VPN", "foreground-start-failed", it, "sdk" to Build.VERSION.SDK_INT)
        }.isSuccess
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "MarbleNG connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun note(text: String, ongoing: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("MarbleNG")
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()

    private fun notifyNow(text: String, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFY, note(text, ongoing))
    }

    private fun safeMessage(t: Throwable): String =
        t.message?.take(180)?.ifBlank { t::class.java.simpleName } ?: t::class.java.simpleName
}
