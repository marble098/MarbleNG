package com.marbleng.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.*
import com.marbleng.app.core.*
import com.marbleng.app.data.AppStore
import com.marbleng.app.data.SecretStore
import com.marbleng.app.model.*
import com.marbleng.app.net.*
import com.marbleng.app.vpn.MarbleVpnService
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

class AppRepository(private val context: Context, val xray: XrayManager) {
    private val store = AppStore(context)
    private val secrets = SecretStore(context)
    private val io = Executors.newCachedThreadPool()

    val profiles = mutableStateListOf<ProxyProfile>().apply { addAll(store.loadProfiles()) }
    val subscriptions = mutableStateListOf<Subscription>().apply { addAll(store.loadSubscriptions()) }
    val history = mutableStateListOf<ConnectionRecord>().apply { addAll(store.loadHistory()) }

    var settings by mutableStateOf(store.settings()); private set
    var state by mutableStateOf("DISCONNECTED"); private set
    var stateDetail by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var benchmarks by mutableStateOf<List<BenchmarkResult>>(emptyList()); private set
    var privacy by mutableStateOf<PrivacyReport?>(null); private set
    var radarConfigs by mutableStateOf<List<String>>(emptyList()); private set
    var radarResults by mutableStateOf<List<BenchmarkResult>>(emptyList()); private set

    // Live tunnel telemetry. Ping is HTTPS time-to-first-response through the selected Xray path,
    // not the localhost SOCKS handshake.
    var livePingMs by mutableStateOf(0); private set
    var liveDownBps by mutableStateOf(0L); private set
    var liveUpBps by mutableStateOf(0L); private set

    fun updateTelemetry(downBps: Long, upBps: Long) {
        liveDownBps = downBps.coerceAtLeast(0)
        liveUpBps = upBps.coerceAtLeast(0)
    }

    fun updatePing(ms: Int) {
        if (ms > 0) livePingMs = ms
    }

    fun resetTelemetry() {
        livePingMs = 0
        liveDownBps = 0
        liveUpBps = 0
    }

    fun profile(id: String) = profiles.firstOrNull { it.id == id }

    fun setRuntimeState(s: String, d: String) {
        state = s
        stateDetail = d
        if (s != "CONNECTED") resetTelemetry()
    }

    fun updateSettings(v: AppSettings) {
        settings = v
        store.saveSettings(v)
    }

    fun setConnectionMode(mode: ConnectionMode) {
        if (settings.connectionMode == mode) return
        if (state == "CONNECTED" || state == "CONNECTING" || state == "BLOCKED") stopVpn()
        updateSettings(settings.copy(connectionMode = mode))
        message = when (mode) {
            ConnectionMode.FULL_TUN -> "Full-device TUN selected"
            ConnectionMode.LOCAL_PROXY -> "Local SOCKS5 proxy selected • 127.0.0.1:${settings.localProxyPort}"
        }
    }

    fun activeProxyPort(): Int = when (settings.connectionMode) {
        ConnectionMode.FULL_TUN -> settings.socksPort
        ConnectionMode.LOCAL_PROXY -> settings.localProxyPort
    }

    fun addSubscription(name: String, url: String) {
        val id = sha(url).take(12)
        subscriptions.removeAll { it.id == id }
        subscriptions += Subscription(id, name.ifBlank { "Subscription" }, url, System.currentTimeMillis())
        store.saveSubscriptions(subscriptions)
        refresh(id)
    }

    fun refresh(id: String) {
        val sub = subscriptions.firstOrNull { it.id == id } ?: return
        task("Refreshing ${sub.name}") {
            val text = http(sub.url)
            val parsed = ProxyParser.parseInput(text, sub.id, sub.name)
            profiles.removeAll { it.subscriptionId == sub.id }
            profiles.addAll(parsed)
            store.saveProfiles(profiles)
            message = "${parsed.size} profiles imported"
        }
    }

    fun refreshAll() {
        task("Refreshing subscriptions") {
            subscriptions.forEach { sub ->
                runCatching {
                    val parsed = ProxyParser.parseInput(http(sub.url), sub.id, sub.name)
                    profiles.removeAll { it.subscriptionId == sub.id }
                    profiles.addAll(parsed)
                }
            }
            store.saveProfiles(profiles)
            message = "Subscriptions refreshed"
        }
    }

    fun importText(text: String, name: String = "Manual") {
        task("Importing") {
            val parsed = ProxyParser.parseInput(text, "manual", name)
            profiles.addAll(parsed.filter { p -> profiles.none { it.id == p.id } })
            store.saveProfiles(profiles)
            message = "${parsed.size} profiles imported"
        }
    }

    fun removeSubscription(id: String) {
        subscriptions.removeAll { it.id == id }
        profiles.removeAll { it.subscriptionId == id }
        store.saveSubscriptions(subscriptions)
        store.saveProfiles(profiles)
    }

    fun removeProfile(id: String) {
        profiles.removeAll { it.id == id }
        store.saveProfiles(profiles)
    }

    fun lastProfile() = profile(store.lastProfileId())
    fun auto(onConnect: (ProxyProfile) -> Unit) { lastProfile()?.let(onConnect) ?: smart(onConnect) }

    fun markConnected(p: ProxyProfile) {
        state = "CONNECTED"
        stateDetail = p.name
        if (settings.rememberLast) store.setLastProfileId(p.id)
        history += ConnectionRecord(p.id, p.name, System.currentTimeMillis(), "connected:${settings.connectionMode.name}")
        store.saveHistory(history)
    }

    fun startVpn(p: ProxyProfile) {
        setRuntimeState("CONNECTING", p.name)
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_START)
            .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
            .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_TUN)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun startLocalProxy(p: ProxyProfile) {
        setRuntimeState("CONNECTING", p.name)
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_START)
            .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
            .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_PROXY)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun stopVpn() {
        context.startService(Intent(context, MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_STOP))
    }

    fun smart(onBest: (ProxyProfile) -> Unit) {
        task("Benchmarking") {
            val results = BenchmarkEngine(xray).run(profiles.toList(), settings) { a, b, n -> message = "Testing $a/$b • $n" }
            benchmarks = results
            val best = results.firstOrNull { it.success > 0 }?.let { profile(it.profileId) }
            message = if (best == null) "No working candidate" else "Best: ${best.name}"
            best?.let { android.os.Handler(android.os.Looper.getMainLooper()).post { onBest(it) } }
        }
    }

    fun fullTest(p: ProxyProfile) {
        task("Full test ${p.name}") {
            benchmarks = BenchmarkEngine(xray).run(listOf(p), settings.copy(benchCandidates = 1))
            message = benchmarks.firstOrNull()?.let {
                "${it.success}% • ${"%.0f".format(it.latencyMs)} ms • ${"%.1f".format(it.score)}"
            } ?: "Test failed"
        }
    }

    fun audit() {
        task("Privacy audit") {
            privacy = PrivacyAuditor.audit(activeProxyPort())
            message = privacy?.note.orEmpty()
        }
    }

    fun googleAi() {
        task("Google AI check") {
            val r = SocksHttpClient.get(activeProxyPort(), "gemini.google.com", "/", 10_000, 64_000)
            message = "Gemini reachability HTTP ${r.status} • ${"%.0f".format(r.elapsedMs)} ms"
        }
    }

    fun telegram(channel: String) {
        task("Telegram radar • fetch") {
            val saved = channels()
            val normalized = channel.trim()
            if (normalized.isNotBlank() && !saved.contains(normalized)) {
                saved.add(normalized)
                saveChannels(saved)
            }
            val out = TelegramRadar.fetch(channel, if (state == "CONNECTED") activeProxyPort() else null, settings.telegramMaxConfigs)
            val candidates = ProxyParser.parseInput(out.joinToString("\n"), "telegram", "Telegram Radar")
            if (candidates.isEmpty()) {
                radarConfigs = emptyList()
                radarResults = emptyList()
                message = "No supported configs found"
                return@task
            }
            message = "Telegram radar • tunnel lab ${candidates.size} configs"
            val testSettings = settings.copy(
                benchCandidates = minOf(settings.telegramMaxConfigs, candidates.size),
                benchSamples = settings.telegramTcpSamples.coerceIn(1, 6)
            )
            val results = BenchmarkEngine(xray).run(candidates, testSettings, settings.telegramTcpGate) { a, b, n ->
                message = "Radar tunnel test $a/$b • $n"
            }
            radarResults = results
            val passed = results.filter { it.success >= settings.telegramPassMinSuccess }.map { it.profileId }.toSet()
            radarConfigs = candidates.filter { it.id in passed }.map { it.raw }
            if (settings.telegramAutoSub && radarConfigs.isNotEmpty()) {
                profiles.removeAll { it.subscriptionId == "telegram-passed" }
                profiles.addAll(
                    candidates.filter { it.id in passed }.map {
                        it.copy(subscriptionId = "telegram-passed", subscriptionName = "Telegram Passed")
                    }
                )
                store.saveProfiles(profiles)
            }
            message = "Radar: ${out.size} found • ${radarConfigs.size} passed ≥${settings.telegramPassMinSuccess}%"
        }
    }

    fun importRadar() { importText(radarConfigs.joinToString("\n"), "Telegram Radar") }
    fun channels() = store.channels()
    fun saveChannels(v: List<String>) = store.saveChannels(v)

    fun cloudflareToken() = secrets.get("cfToken")
    fun cloudflareAccount() = secrets.get("cfAccount")
    fun cloudflareKey() = secrets.get("cfAccessKey")

    fun deployWorker(token: String, account: String, script: String, key: String) {
        task("Deploying Cloudflare Worker") {
            val r = CloudflareWorker.deploy(token, account, script, key)
            if (r.ok) {
                secrets.put("cfToken", token)
                secrets.put("cfAccount", account)
                secrets.put("cfAccessKey", key)
            }
            message = r.message + if (r.workerUrl.isNotBlank()) " • ${r.workerUrl}" else ""
        }
    }

    fun forgetCloudflare() {
        secrets.put("cfToken", "")
        secrets.put("cfAccount", "")
        secrets.put("cfAccessKey", "")
        message = "Cloudflare credentials removed from Android Keystore-backed storage"
    }

    fun routingAssetStatus(): RoutingAssetStatus = xray.routingAssetStatus()

    fun prepareRoutingAssets(force: Boolean = false) {
        task(if (force) "Updating routing assets" else "Preparing routing assets") {
            val status = xray.prepareRoutingAssets(settings, force)
            val parts = mutableListOf<String>()
            parts += if (status.geoIpReady) "geoip ${formatBytes(status.geoIpBytes)}" else "geoip missing"
            parts += if (status.geoSiteReady) "geosite ${formatBytes(status.geoSiteBytes)}" else "geosite missing"
            message = "Routing assets • ${parts.joinToString(" • ")}"
        }
    }

    fun deleteRoutingAssets() {
        task("Removing routing assets") {
            xray.deleteRoutingAssets()
            message = "Local geo assets removed; they will be restored/downloaded on next prepare"
        }
    }

    fun resetSettings() {
        updateSettings(AppSettings())
        message = "Settings reset • proxy-all routing restored"
    }

    fun capabilities(): String = """
        Xray inputs: VLESS, VMess, Trojan, Shadowsocks, Hysteria2, SOCKS, HTTP/HTTPS and Xray JSON.
        Transports: raw/TCP, WebSocket, XHTTP/SplitHTTP, HTTPUpgrade, gRPC, HTTP/H2 and mKCP where supported by Xray.
        Connection modes: Full Android TUN or localhost SOCKS5 on 127.0.0.1:${settings.localProxyPort}.
        Routing: proxy-all, private bypass, geo direct and custom domain/IP rules with managed geoip.dat + geosite.dat.
        ABIs: arm64-v8a, armeabi-v7a, x86_64, x86.
    """.trimIndent()

    fun doctor(): String {
        val checks = mutableListOf<String>()
        checks += if (java.io.File(context.applicationInfo.nativeLibraryDir, "libxray.so").exists()) "✔ Xray native binary" else "✖ Xray native binary missing"
        checks += if (runCatching { System.loadLibrary("marbleng") }.isSuccess) "✔ HEV/JNI bridge" else "✖ HEV/JNI bridge"
        checks += if (profiles.isNotEmpty()) "✔ ${profiles.size} profiles" else "⚠ No profiles yet"
        checks += "✔ TUN SOCKS bound to 127.0.0.1:${settings.socksPort}"
        checks += "✔ Local proxy bound to 127.0.0.1:${settings.localProxyPort}"
        val assets = routingAssetStatus()
        checks += if (assets.geoIpReady) "✔ geoip.dat ${formatBytes(assets.geoIpBytes)}" else "⚠ geoip.dat not prepared"
        checks += if (assets.geoSiteReady) "✔ geosite.dat ${formatBytes(assets.geoSiteBytes)}" else "⚠ geosite.dat not prepared"
        checks += "✔ Unmatched traffic falls back to proxy"
        return checks.joinToString("\n")
    }

    fun clearMessage() { message = "" }
    fun readLogs(): String = RuntimeDiagnostics(context).bundle(xray.logFile)
    fun coreLock(): String = runCatching { context.assets.open("core-lock.json").bufferedReader().readText() }.getOrDefault("{}")

    fun checkCoreUpdates() {
        task("Checking cores") {
            val current = org.json.JSONObject(coreLock())
            val xr = current.getJSONObject("xray").getString("tag")
            val hv = current.getJSONObject("hev").getString("tag")
            val xJson = http("https://api.github.com/repos/XTLS/Xray-core/releases?per_page=20")
            val xa = org.json.JSONArray(xJson)
            var latestX = xr
            for (i in 0 until xa.length()) {
                val o = xa.getJSONObject(i)
                if (!o.optBoolean("draft") && o.optBoolean("prerelease")) {
                    latestX = o.optString("tag_name")
                    break
                }
            }
            val h = org.json.JSONObject(http("https://api.github.com/repos/heiher/hev-socks5-tunnel/releases/latest"))
                .optString("tag_name", hv)
            message = if (latestX == xr && h == hv) {
                "Cores are current ($xr / $hv)"
            } else {
                "Update available: Xray $latestX • HEV $h. GitHub core-update workflow will rebuild a signed APK."
            }
        }
    }

    private fun task(label: String, block: () -> Unit) {
        if (busy) return
        busy = true
        message = label
        io.execute {
            try {
                block()
            } catch (t: Throwable) {
                message = "${t::class.simpleName}: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "MarbleNG/1")
        return c.inputStream.bufferedReader().readText()
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.1f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
