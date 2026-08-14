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
    private val io = Executors.newFixedThreadPool(3)
    val intelligence = MarbleIntelligence(context)
    private val notifier = SmartNotifier(context)

    val profiles = mutableStateListOf<ProxyProfile>().apply { addAll(store.loadProfiles()) }
    val subscriptions = mutableStateListOf<Subscription>().apply { addAll(store.loadSubscriptions()) }
    val history = mutableStateListOf<ConnectionRecord>().apply { addAll(store.loadHistory()) }

    var settings by mutableStateOf(store.settings()); private set
    var networkSnapshot by mutableStateOf(intelligence.currentSnapshot()); private set
    var intelligenceStatus by mutableStateOf(intelligence.status(settings)); private set
    var sentinel by mutableStateOf(PrivacySentinelState()); private set
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

    init {
        notifier.ensureChannels()
        intelligence.startMonitoring()
        intelligence.addNetworkListener { next ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                networkSnapshot = next
                refreshIntelligenceStatus()
            }
        }
        refreshIntelligenceStatus()
        if (settings.subscriptionAutoRefresh && subscriptions.isNotEmpty()) {
            val maxAgeMs = settings.subscriptionRefreshHours.coerceIn(1, 168) * 3_600_000L
            val stale = subscriptions.any {
                it.updatedAt <= 0L || System.currentTimeMillis() - it.updatedAt >= maxAgeMs
            }
            if (stale) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { refreshAll() }
            }
        }
    }

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
        notifier.ensureChannels()
        if (!v.smartNotificationsEnabled) notifier.cancelOptional()
        refreshIntelligenceStatus()
    }

    fun effectiveSettingsFor(profile: ProxyProfile): AppSettings =
        if (settings.intelligenceEnabled) intelligence.effectiveSettings(profile, settings) else settings

    fun recoveryCandidates(failedIds: Set<String>): List<ProxyProfile> =
        intelligence.recoveryCandidates(profiles.toList(), failedIds, settings)

    fun refreshIntelligenceStatus() {
        intelligenceStatus = intelligence.status(settings)
    }

    fun updateSentinel(value: PrivacySentinelState) {
        sentinel = value
    }

    fun testSmartNotification() {
        notifier.ensureChannels()
        val posted = notifier.alert(
            SmartNotificationKind.TEST,
            "manual-test",
            "MarbleNG smart alerts",
            "Notifications are ready • recovery, privacy and subscription events can be surfaced here.",
            settings,
            minIntervalOverrideMs = 0L
        )
        message = if (posted) "Smart test alert sent" else "Notification permission or smart alerts are disabled"
    }

    fun clearSmartNotifications() {
        notifier.cancelOptional()
        message = "Optional MarbleNG alerts cleared"
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
        val cleanUrl = url.trim()
        if (!(cleanUrl.startsWith("https://", true) || cleanUrl.startsWith("http://", true))) {
            message = "Subscription URL must start with http:// or https://"
            return
        }
        val duplicate = subscriptions.firstOrNull { it.url.trim().equals(cleanUrl, true) }
        if (duplicate != null) {
            message = "Subscription already exists • ${duplicate.name}"
            return
        }
        val baseId = sha(cleanUrl).take(12)
        var id = baseId
        var suffix = 1
        while (subscriptions.any { it.id == id }) id = "${baseId.take(9)}-${suffix++}"
        subscriptions += Subscription(
            id = id,
            name = name.trim().ifBlank { "Subscription ${subscriptions.size + 1}" },
            url = cleanUrl,
            updatedAt = 0L
        )
        store.saveSubscriptions(subscriptions)
        message = "Subscription added • refreshing source"
        refresh(id)
    }

    fun refresh(id: String) {
        val sub = subscriptions.firstOrNull { it.id == id } ?: return
        task("Refreshing ${sub.name}") {
            val payload = httpSubscription(sub.url)
            val parsed = ProxyParser.parseInput(payload.text, sub.id, sub.name)
            require(parsed.isNotEmpty()) {
                "No supported profiles returned; previous nodes were kept"
            }

            profiles.removeAll { it.subscriptionId == sub.id }
            profiles.addAll(parsed)

            val meta = parseSubscriptionUserInfo(payload.userInfo)
            val index = subscriptions.indexOfFirst { it.id == sub.id }
            if (index >= 0) {
                val current = subscriptions[index]
                subscriptions[index] = current.copy(
                    updatedAt = System.currentTimeMillis(),
                    uploadBytes = meta?.upload ?: current.uploadBytes,
                    downloadBytes = meta?.download ?: current.downloadBytes,
                    totalBytes = meta?.total ?: current.totalBytes,
                    expireAt = meta?.expireAt ?: current.expireAt
                )
            }

            store.saveSubscriptions(subscriptions)
            store.saveProfiles(profiles)
            notifier.alert(
                SmartNotificationKind.SUBSCRIPTION,
                "subscription:${sub.id}",
                "Subscription refreshed",
                "${sub.name} • ${parsed.size} nodes",
                settings
            )
            message = "${parsed.size} profiles refreshed"
        }
    }

    fun refreshAll() {
        task("Refreshing subscriptions") {
            var refreshed = 0
            var nodeCount = 0
            val failed = mutableListOf<String>()

            subscriptions.toList().forEach { sub ->
                val result = runCatching {
                    val payload = httpSubscription(sub.url)
                    val parsed = ProxyParser.parseInput(payload.text, sub.id, sub.name)
                    require(parsed.isNotEmpty()) {
                        "No supported profiles returned; previous nodes were kept"
                    }

                    profiles.removeAll { it.subscriptionId == sub.id }
                    profiles.addAll(parsed)

                    val meta = parseSubscriptionUserInfo(payload.userInfo)
                    val index = subscriptions.indexOfFirst { it.id == sub.id }
                    if (index >= 0) {
                        val current = subscriptions[index]
                        subscriptions[index] = current.copy(
                            updatedAt = System.currentTimeMillis(),
                            uploadBytes = meta?.upload ?: current.uploadBytes,
                            downloadBytes = meta?.download ?: current.downloadBytes,
                            totalBytes = meta?.total ?: current.totalBytes,
                            expireAt = meta?.expireAt ?: current.expireAt
                        )
                    }
                    parsed.size
                }

                result.onSuccess { count ->
                    refreshed++
                    nodeCount += count
                }.onFailure { error ->
                    failed += "${sub.name}: ${error.message ?: error::class.java.simpleName}"
                }
            }

            store.saveSubscriptions(subscriptions)
            store.saveProfiles(profiles)
            val summary = when {
                failed.isEmpty() -> "$refreshed sources refreshed • $nodeCount nodes"
                refreshed == 0 -> "Refresh failed • ${failed.take(2).joinToString(" • ")}"
                else -> "$refreshed refreshed • ${failed.size} failed • ${failed.take(2).joinToString(" • ")}"
            }
            message = summary
            notifier.alert(
                SmartNotificationKind.SUBSCRIPTION,
                "refresh-all",
                if (failed.isEmpty()) "Subscriptions refreshed" else "Subscription refresh issues",
                summary,
                settings,
                minIntervalOverrideMs = 30_000L
            )
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

    fun updateSubscription(id: String, name: String, url: String): Boolean {
        if (busy) {
            message = "Wait for the current background task before editing a subscription"
            return false
        }
        val index = subscriptions.indexOfFirst { it.id == id }
        if (index < 0) {
            message = "Subscription no longer exists"
            return false
        }
        val cleanUrl = url.trim()
        val cleanName = name.trim().ifBlank { subscriptions[index].name }
        if (!(cleanUrl.startsWith("https://", true) || cleanUrl.startsWith("http://", true))) {
            message = "Subscription URL must start with http:// or https://"
            return false
        }
        if (subscriptions.any { it.id != id && it.url.trim().equals(cleanUrl, true) }) {
            message = "Another subscription already uses this URL"
            return false
        }
        subscriptions[index] = subscriptions[index].copy(name = cleanName, url = cleanUrl)
        for (i in profiles.indices) {
            if (profiles[i].subscriptionId == id) profiles[i] = profiles[i].copy(subscriptionName = cleanName)
        }
        store.saveSubscriptions(subscriptions)
        store.saveProfiles(profiles)
        message = "Subscription updated • $cleanName"
        return true
    }

    fun removeSubscription(id: String) {
        if (busy) {
            message = "Wait for the current background task before deleting a subscription"
            return
        }
        val sub = subscriptions.firstOrNull { it.id == id } ?: run {
            message = "Subscription no longer exists"
            return
        }
        val doomedIds = profiles.filter { it.subscriptionId == id }.map { it.id }.toSet()
        val activeBelongs = state in setOf("CONNECTED", "CONNECTING", "BLOCKED") &&
            profiles.any { it.subscriptionId == id && it.name == stateDetail }
        if (activeBelongs) stopVpn()
        if (lastProfile()?.id?.let { it in doomedIds } == true) store.setLastProfileId("")
        subscriptions.removeAll { it.id == id }
        profiles.removeAll { it.subscriptionId == id }
        benchmarks = benchmarks.filterNot { it.profileId in doomedIds }
        store.saveSubscriptions(subscriptions)
        store.saveProfiles(profiles)
        message = "Removed ${sub.name} • ${doomedIds.size} nodes deleted"
    }

    fun removeProfile(id: String) {
        profiles.removeAll { it.id == id }
        benchmarks = benchmarks.filterNot { it.profileId == id }
        store.saveProfiles(profiles)
    }

    fun renameProfile(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val idx = profiles.indexOfFirst { it.id == id }
        if (idx < 0) return
        profiles[idx] = profiles[idx].copy(name = trimmed)
        store.saveProfiles(profiles)
    }

    fun renameSubscription(id: String, name: String) {
        val current = subscriptions.firstOrNull { it.id == id } ?: return
        updateSubscription(id, name, current.url)
    }

    fun subscriptionNodeCount(id: String): Int = profiles.count { it.subscriptionId == id }

    /** Reassigns a profile to another subscription bucket (or "manual") so nodes can move between library sources. */
    fun moveProfile(id: String, targetSubscriptionId: String) {
        val idx = profiles.indexOfFirst { it.id == id }
        if (idx < 0) return
        val targetName = if (targetSubscriptionId == "manual") "Manual" else {
            subscriptions.firstOrNull { it.id == targetSubscriptionId }?.name ?: return
        }
        profiles[idx] = profiles[idx].copy(subscriptionId = targetSubscriptionId, subscriptionName = targetName)
        store.saveProfiles(profiles)
    }

    fun moveTargets(): List<Pair<String, String>> =
        listOf("manual" to "Manual") + subscriptions.map { it.id to it.name }

    fun lastProfile() = profile(store.lastProfileId())
    fun auto(
        onConnect: (ProxyProfile) -> Unit
    ) {
        if (profiles.isEmpty()) {
            message =
                "Add a subscription or import a node first"
            return
        }

        val last =
            lastProfile()

        val predicted =
            last?.let {
                intelligence
                    .predictedScore(
                        it,
                        settings
                    )
            } ?: 0.0

        val confidence =
            last?.let {
                intelligence
                    .historyConfidence(
                        it
                    )
            } ?: 0.0

        val lastHasHistory =
            last?.let {
                intelligence
                    .hasHistory(it)
            } == true

        if (
            last != null &&
            (
                !settings
                    .intelligenceEnabled ||
                    !lastHasHistory ||
                    confidence <
                        0.45 ||
                    predicted >=
                        68.0
            )
        ) {
            message =
                when {
                    !settings
                        .intelligenceEnabled ->
                        "Connecting remembered route • " +
                            last.name

                    !lastHasHistory ||
                        confidence <
                        0.45 ->
                        "Connecting last route immediately • " +
                            "intelligence is still building confidence"

                    else ->
                        "Connecting proven route • " +
                            "${last.name} • " +
                            "score ${predicted.toInt()}"
                }

            onConnect(last)
        } else {
            message =
                "Last route confidence dropped • " +
                    "selecting a healthier Xray path"

            smart(onConnect)
        }
    }

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
        launchConnectionService(intent, p.name)
    }

    fun startLocalProxy(p: ProxyProfile) {
        setRuntimeState("CONNECTING", p.name)
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_START)
            .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
            .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_PROXY)
        launchConnectionService(intent, p.name)
    }

    private fun launchConnectionService(intent: Intent, profileName: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }.onFailure { error ->
            val detail = "Could not start connection service for $profileName: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
            setRuntimeState("BLOCKED", detail)
            message = detail
        }
    }

    fun stopVpn() {
        runCatching {
            context.startService(Intent(context, MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_STOP))
        }.onFailure { error ->
            message = "Could not stop connection service: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
        }
    }

    fun smart(onBest: (ProxyProfile) -> Unit) {
        task("Marble Intelligence • selecting route") {
            val engine = BenchmarkEngine(xray, intelligence)
            if (settings.intelligenceEnabled && settings.raceConnectEnabled && profiles.size > 1) {
                val raced = engine.race(profiles.toList(), settings) { n -> message = "Connection race • $n" }
                if (raced != null) {
                    benchmarks = listOf(raced.second.copy(score = maxOf(80.0, raced.second.score)))
                    message = "Race winner: ${raced.first.name}"
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onBest(raced.first) }
                    return@task
                }
            }
            val results = engine.run(profiles.toList(), settings) { a, b, n -> message = "Tunnel intelligence $a/$b • $n" }
            benchmarks = results
            val best = results.firstOrNull { it.success > 0 }?.let { profile(it.profileId) }
            message = if (best == null) "No working candidate" else "Best: ${best.name}"
            best?.let { android.os.Handler(android.os.Looper.getMainLooper()).post { onBest(it) } }
        }
    }

    fun smartRank() {
        if (profiles.isEmpty()) {
            message = "Nothing to rank • add a subscription or import nodes first"
            return
        }
        task("Smart rank • testing real Xray routes") {
            val results = BenchmarkEngine(xray, intelligence).run(profiles.toList(), settings) { done, total, name ->
                message = "Smart rank $done/$total • $name"
            }
            benchmarks = results
            val best = results.firstOrNull { it.success > 0 }
            message = if (best == null) "Smart rank finished • no healthy route found"
            else "Best route • ${best.name} • ${best.latencyMs.toInt()} ms • score ${best.score.toInt()}"
        }
    }

    fun fullTest(p: ProxyProfile) {
        task("Full test ${p.name}") {
            benchmarks = BenchmarkEngine(xray, intelligence).run(listOf(p), settings.copy(benchCandidates = 1))
            message = benchmarks.firstOrNull()?.let {
                "${it.success}% • ${"%.0f".format(it.latencyMs)} ms • ${"%.1f".format(it.score)}"
            } ?: "Test failed"
        }
    }

    /** Real-tunnel tests every profile in the library, ignoring the BenchMode candidate cap. */
    fun testAll() {
        if (profiles.isEmpty()) {
            message = "Nothing to test • add nodes first"
            return
        }
        task("Testing all configs") {
            val all = profiles.toList()
            val testSettings = settings.copy(benchMode = BenchMode.CUSTOM, benchCandidates = all.size)
            benchmarks = BenchmarkEngine(xray, intelligence).run(all, testSettings) { a, b, n -> message = "Tunnel test $a/$b • $n" }
            val passed = benchmarks.count { it.success > 0 }
            message = "Tested ${benchmarks.size} configs • $passed reachable"
        }
    }

    fun audit() {
        if (state != "CONNECTED" || !xray.isAlive) {
            privacy = null
            message = "Privacy audit needs an active healthy Xray connection"
            return
        }
        privacy = null
        task("Privacy audit • testing egress and DNS through Xray") {
            privacy = PrivacyAuditor.audit(activeProxyPort())
            val report = privacy
            if (report != null) {
                sentinel = sentinel.copy(
                    exitIp = report.proxyIp,
                    dnsObservation = report.dnsServers,
                    updatedAt = System.currentTimeMillis()
                )
            }
            message = privacy?.note.orEmpty().ifBlank { "Privacy audit finished" }
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
            val radarProxyPort = when {
                state == "DISCONNECTED" -> null
                xray.isAlive -> activeProxyPort()
                else -> error("Telegram fetch blocked while the tunnel is not healthy")
            }
            val out = TelegramRadar.fetch(channel, radarProxyPort, settings.telegramMaxConfigs)
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
            val results = BenchmarkEngine(xray, intelligence).run(candidates, testSettings, settings.telegramTcpGate) { a, b, n ->
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
        if (state != "DISCONNECTED") {
            message = "Disconnect before Cloudflare deployment • direct management traffic is blocked while a tunnel is active"
            return
        }
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
        if (state != "DISCONNECTED" && (settings.geoIpUrl.isNotBlank() || settings.geoSiteUrl.isNotBlank())) {
            message = "Disconnect before downloading routing assets • direct management downloads are blocked while a tunnel is active"
            return
        }
        task(if (force) "Updating routing assets" else "Preparing routing assets") {
            val status = xray.prepareRoutingAssets(settings, force)
            val parts = mutableListOf<String>()
            parts += if (status.geoIpReady) "geoip ${formatBytes(status.geoIpBytes)}" else "geoip missing (add a geoip.dat URL above)"
            parts += if (status.geoSiteReady) "geosite ${formatBytes(status.geoSiteBytes)}" else "geosite missing (add a geosite.dat URL above)"
            message = "Routing assets • ${parts.joinToString(" • ")}"
        }
    }

    fun verifyRoutingPolicy() {
        val candidate = lastProfile() ?: profiles.firstOrNull()
        if (candidate == null) {
            message = "Import at least one profile before verifying routing"
            return
        }
        if (state != "DISCONNECTED" && (settings.geoIpUrl.isNotBlank() || settings.geoSiteUrl.isNotBlank())) {
            message = "Disconnect before routing verification when remote geo data URLs are configured"
            return
        }
        task("Verifying routing policy with Xray") {
            val chain = if (settings.chainEnabled) {
                profile(settings.chainSecondProfileId)?.takeUnless { it.id == candidate.id }
            } else null
            message = xray.verifyRoutingPolicy(candidate, settings, chain)
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
        Split tunneling: all apps, only selected apps, or bypass selected apps in Full TUN mode.
        Advanced Xray: encrypted DNS hijack, adaptive DoH ordering, optional Freedom.fragment, Mux/XUDP and two-hop chained outbounds.
        Marble Intelligence: persistent network-scoped health history, connection race, bounded failover, adaptive MTU/dual-stack/thermal policies and UDP probing.
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
        checks += if (settings.dnsHijackEnabled) "✔ Traditional DNS :53 is hijacked into Xray DNS" else "⚠ DNS hijack disabled"
        checks += "✔ Intelligence network ${networkSnapshot.label}"
        checks += "✔ Thermal budget ${intelligenceStatus.thermalBudgetPercent}% • effective MTU ${intelligenceStatus.effectiveMtu.takeIf { it > 0 } ?: settings.mtuMax}"
        checks += if (notifier.optionalPermissionGranted()) "✔ Optional notification permission" else "⚠ Optional notification permission not granted"
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
            val updateAvailable = latestX != xr || h != hv
            message = if (!updateAvailable) {
                "Cores are current ($xr / $hv)"
            } else {
                "Update available: Xray $latestX • HEV $h. GitHub core-update workflow will rebuild a signed APK."
            }
            if (updateAvailable) {
                notifier.alert(
                    SmartNotificationKind.CORE,
                    "core:$latestX:$h",
                    "Core update available",
                    "Xray $latestX • HEV $h",
                    settings,
                    minIntervalOverrideMs = 6L * 60L * 60L * 1000L
                )
            }
        }
    }

    private fun task(label: String, block: () -> Unit) {
        if (busy) {
            message = "MarbleNG is busy • finish the current task before starting another"
            return
        }
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

    private data class SubscriptionPayload(
        val text: String,
        val userInfo: String = ""
    )

    private data class SubscriptionMeta(
        val upload: Long = 0,
        val download: Long = 0,
        val total: Long = 0,
        val expireAt: Long = 0
    )

    private fun httpSubscription(url: String): SubscriptionPayload {
        if (state != "DISCONNECTED") {
            check(xray.isAlive) {
                "Direct management request blocked while the tunnel is not healthy"
            }

            // The current SOCKS helper returns the body only. Keep previously learned provider
            // quota/expiry metadata rather than erasing it when a refresh runs through the tunnel.
            return SubscriptionPayload(SocksHttpClient.getTextUrl(activeProxyPort(), url))
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MarbleNG/2 AetherFlow")

        return try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code" }

            val userInfo = connection.getHeaderField("subscription-userinfo")
                ?: connection.getHeaderField("Subscription-Userinfo")
                ?: ""

            SubscriptionPayload(
                text = connection.inputStream.bufferedReader().use { it.readText() },
                userInfo = userInfo
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun http(url: String): String = httpSubscription(url).text

    private fun parseSubscriptionUserInfo(raw: String): SubscriptionMeta? {
        if (raw.isBlank()) return null

        val values = raw.split(';')
            .mapNotNull { token ->
                val key = token.substringBefore('=', "").trim().lowercase()
                val value = token.substringAfter('=', "").trim().toLongOrNull()
                if (key.isBlank() || value == null) null else key to value
            }
            .toMap()

        if (values.isEmpty()) return null

        val expireSeconds = values["expire"] ?: 0L
        return SubscriptionMeta(
            upload = values["upload"] ?: 0L,
            download = values["download"] ?: 0L,
            total = values["total"] ?: 0L,
            expireAt = if (expireSeconds > 0) expireSeconds * 1000L else 0L
        )
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
