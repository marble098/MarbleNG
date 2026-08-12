package com.marbleng.app.vpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.marbleng.app.MarbleApplication
import com.marbleng.app.core.RuntimeDiagnostics
import com.marbleng.app.core.SocksHttpClient
import com.marbleng.app.core.XrayManager
import com.marbleng.app.nativebridge.HevTunnel
import com.marbleng.app.model.ProxyProfile
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
    }

    private var tun: ParcelFileDescriptor? = null
    private var hevFd = -1
    private val running = AtomicBoolean(false)
    private val worker = Executors.newCachedThreadPool()
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
        diag.event("VPN", "command", "action" to intent?.action, "startId" to startId, "flags" to flags)
        when (intent?.action) {
            ACTION_STOP -> shutdown(true)
            ACTION_START -> intent.getStringExtra(EXTRA_PROFILE)?.let { id ->
                startConnection(id, intent.getStringExtra(EXTRA_MODE) ?: MODE_TUN)
            }
        }
        return START_STICKY
    }

    private fun startConnection(id: String, mode: String) {
        if (running.get()) cleanupRuntime(setDisconnected = false)

        val app = application as MarbleApplication
        val profile = app.repo.profile(id) ?: run {
            diag.event("VPN", "profile-missing", "profileId" to id.take(12))
            return
        }
        val settings = app.repo.settings
        val normalizedMode = if (mode == MODE_PROXY) MODE_PROXY else MODE_TUN
        val port = if (normalizedMode == MODE_PROXY) settings.localProxyPort else settings.socksPort
        val session = System.currentTimeMillis().toString(36)

        activeSession = session
        activeMode = normalizedMode
        synchronized(latencyWindow) { latencyWindow.clear() }
        diag.prepareHevSession()
        diag.event(
            "VPN", "connect-request",
            "session" to session,
            "profileId" to profile.id.take(12),
            "profileName" to profile.name,
            "mode" to normalizedMode,
            "socksPort" to port
        )

        startForeground(
            NOTIFY,
            note(
                if (normalizedMode == MODE_PROXY) "Starting local proxy • ${profile.name}" else "Connecting • ${profile.name}",
                true
            )
        )
        running.set(true)

        worker.execute {
            diag.event("XRAY", "start-begin", "session" to session, "port" to port, "mode" to normalizedMode)
            if (!xray.start(profile, port, settings)) {
                blocked("Xray rejected profile or routing policy")
                return@execute
            }
            diag.event("XRAY", "socks-ready", "session" to session, "alive" to xray.isAlive, "port" to port)

            if (normalizedMode == MODE_PROXY) {
                app.repo.markConnected(profile)
                notifyNow("SOCKS5 • 127.0.0.1:$port • ${profile.name}", true)
                startProxyMonitor(session, port)
                return@execute
            }

            startTun(profile, session, port)
        }
    }

    private fun startTun(profile: ProxyProfile, session: String, socksPort: Int) {
        val profileName = profile.name
        val app = application as MarbleApplication
        val builder = Builder()
            .setSession("MarbleNG • $profileName")
            .setMtu(8500)
            .setBlocking(false)
            .addAddress("198.18.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        runCatching { builder.addAddress("fc00::1", 128).addRoute("::", 0) }
            .onSuccess { diag.event("TUN", "ipv6-enabled", "session" to session) }
            .onFailure { diag.error("TUN", "ipv6-builder-failed", it, "session" to session) }

        runCatching { builder.addDisallowedApplication(packageName) }
            .onSuccess { diag.event("TUN", "self-disallowed", "session" to session, "package" to packageName) }
            .onFailure { diag.error("TUN", "self-disallow-failed", it, "session" to session) }

        tun = runCatching { builder.establish() }
            .onFailure { diag.error("TUN", "establish-exception", it, "session" to session) }
            .getOrNull()
        if (tun == null) {
            blocked("VPN establish failed")
            return
        }

        diag.event("TUN", "established", "session" to session, "vpnFd" to tun!!.fd, "mtu" to 8500)
        val dupFd = runCatching { ParcelFileDescriptor.dup(tun!!.fileDescriptor).detachFd() }
            .onFailure { diag.error("TUN", "dup-fd-failed", it, "session" to session) }
            .getOrNull()
        if (dupFd == null) {
            blocked("TUN fd duplication failed")
            return
        }
        hevFd = dupFd

        val cfg = listOf(
            "tunnel:",
            "  mtu: 8500",
            "  ipv4: 198.18.0.1",
            "  ipv6: 'fc00::1'",
            "  icmp: 'reply'",
            "socks5:",
            "  address: '127.0.0.1'",
            "  port: $socksPort",
            "  udp: 'udp'",
            "  pipeline: false",
            "misc:",
            "  log-file: '${diag.hevLog.absolutePath}'",
            "  log-level: warning",
            "  task-stack-size: 86016",
            "  tcp-buffer-size: 65536",
            "  udp-recv-buffer-size: 524288"
        ).joinToString(separator = "\n", postfix = "\n")

        if (cfg.contains("\\n") || !cfg.contains('\n')) {
            blocked("Internal HEV YAML encoding failure")
            return
        }

        diag.event(
            "HEV", "config-ready",
            "session" to session,
            "bytes" to cfg.toByteArray().size,
            "lines" to cfg.lineSequence().count(),
            "sha256" to diag.sha256(cfg)
        )

        app.repo.markConnected(profile)
        notifyNow("Protected • $profileName", true)
        hevActive = true
        startHevMonitor(session)
        startTelemetry(session, socksPort)

        diag.event("HEV", "run-enter", "session" to session, "hevFd" to hevFd, "xrayAlive" to xray.isAlive)
        val result = runCatching { HevTunnel.run(cfg, hevFd) }
        hevActive = false
        val code = result.getOrElse {
            diag.error("HEV", "jni-run-exception", it, "session" to session, "hevFd" to hevFd)
            -10001
        }
        diag.event("HEV", "run-exit", "session" to session, "code" to code, "runningFlag" to running.get(), "xrayAlive" to xray.isAlive)
        if (running.get() && activeSession == session) blocked("HEV stopped ($code) — traffic held")
    }

    private fun startProxyMonitor(session: String, port: Int) {
        worker.execute {
            var tick = 0
            while (running.get() && activeSession == session && activeMode == MODE_PROXY) {
                if (!xray.isAlive) {
                    blocked("Xray local proxy stopped")
                    return@execute
                }
                if (tick % 5 == 0) sampleRouteLatency(port)
                try {
                    Thread.sleep(1000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                tick++
            }
        }
    }

    private fun startHevMonitor(session: String) {
        worker.execute {
            var ticks = 0
            while (running.get() && hevActive && activeSession == session) {
                try {
                    Thread.sleep(2000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                if (!running.get() || !hevActive || activeSession != session) break
                ticks++
                if (ticks % 2 == 0) {
                    diag.event(
                        "HEV", "heartbeat",
                        "session" to session,
                        "stats" to hevStats(),
                        "xrayAlive" to xray.isAlive,
                        "tunOpen" to (tun != null),
                        "hevFd" to hevFd
                    )
                }
            }
        }
    }

    private fun startTelemetry(session: String, port: Int) {
        worker.execute {
            val repo = (application as MarbleApplication).repo
            var lastUp = -1L
            var lastDown = -1L
            var lastT = System.nanoTime()
            var tick = 0

            while (running.get() && hevActive && activeSession == session) {
                try {
                    Thread.sleep(1000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                if (!running.get() || !hevActive || activeSession != session) break

                val stats = runCatching { HevTunnel.stats() }.getOrNull()
                if (stats != null && stats.size >= 4) {
                    val now = System.nanoTime()
                    val dt = (now - lastT) / 1e9
                    val up = stats[1]
                    val down = stats[3]
                    if (lastUp >= 0 && dt > 0.25) {
                        repo.updateTelemetry(
                            ((down - lastDown) / dt).toLong(),
                            ((up - lastUp) / dt).toLong()
                        )
                    }
                    lastUp = up
                    lastDown = down
                    lastT = now
                }

                if (tick % 5 == 0) sampleRouteLatency(port)
                tick++
            }
            repo.resetTelemetry()
        }
    }

    /**
     * Measures the real application path: localhost SOCKS negotiation + remote TCP + TLS handshake +
     * HTTPS 204 response. The old implementation stopped after the SOCKS CONNECT reply and could
     * therefore report localhost-like 2–4 ms values. A rolling median suppresses one-off spikes.
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
        if (ms <= 0) return

        val median = synchronized(latencyWindow) {
            latencyWindow.addLast(ms)
            while (latencyWindow.size > 5) latencyWindow.removeFirst()
            latencyWindow.toList().sorted()[latencyWindow.size / 2]
        }
        (application as MarbleApplication).repo.updatePing(median)
    }

    private fun hevStats(): String = if (hevFd < 0) {
        "not-started"
    } else {
        runCatching {
            val s = HevTunnel.stats()
            if (s.size >= 4) "txPackets=${s[0]},txBytes=${s[1]},rxPackets=${s[2]},rxBytes=${s[3]}" else "unexpected-size=${s.size}"
        }.getOrElse { "stats-error=${it::class.simpleName}:${it.message}" }
    }

    private fun blocked(reason: String) {
        diag.event(
            "VPN", "blocked",
            "session" to activeSession,
            "mode" to activeMode,
            "reason" to reason,
            "xrayAlive" to xray.isAlive,
            "hevFd" to hevFd,
            "tunOpen" to (tun != null)
        )
        running.set(false)
        if (hevActive) runCatching { HevTunnel.quit() }
        hevActive = false
        xray.stop()
        closeTun()
        (application as MarbleApplication).repo.setRuntimeState("BLOCKED", reason)
        notifyNow("BLOCKED • $reason", false)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun cleanupRuntime(setDisconnected: Boolean) {
        val oldSession = activeSession
        running.set(false)
        if (hevActive) runCatching { HevTunnel.quit() }
        hevActive = false
        xray.stop()
        closeTun()
        (application as MarbleApplication).repo.resetTelemetry()
        if (setDisconnected) {
            (application as MarbleApplication).repo.setRuntimeState("DISCONNECTED", "User disconnected")
        }
        diag.event("VPN", "runtime-clean", "session" to oldSession, "setDisconnected" to setDisconnected)
        activeSession = ""
    }

    private fun closeTun() {
        runCatching {
            if (hevFd >= 0) ParcelFileDescriptor.adoptFd(hevFd).close()
        }
        hevFd = -1
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
        if (running.get()) shutdown(false)
        super.onRevoke()
    }

    override fun onDestroy() {
        diag.event("VPN", "service-destroy", "session" to activeSession, "running" to running.get())
        if (running.get()) cleanupRuntime(setDisconnected = true)
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "MarbleNG connection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun note(text: String, ongoing: Boolean) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_warning)
        .setContentTitle("MarbleNG")
        .setContentText(text)
        .setOngoing(ongoing)
        .build()

    private fun notifyNow(text: String, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFY, note(text, ongoing))
    }
}
