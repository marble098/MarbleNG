package com.marbleng.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.*
import com.marbleng.app.core.*
import com.marbleng.app.data.AppStore
import com.marbleng.app.model.*
import com.marbleng.app.net.*
import com.marbleng.app.vpn.MarbleVpnService
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.roundToInt

class AppRepository(private val context: Context, val xray: XrayManager) {
    private val store = AppStore(context)
    private val io = Executors.newFixedThreadPool(3)

    // Iran Mode scanning runs on its own thread so a detection sweep can never block or be blocked
    // by a user-visible task such as a subscription refresh.
    private val iranScanner = Executors.newSingleThreadExecutor()
    private val iranScanInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    val intelligence = MarbleIntelligence(context)
    private val notifier = SmartNotifier(context)
    private val iranDetector = IranModeDetector(context, intelligence)

    val profiles = mutableStateListOf<ProxyProfile>().apply { addAll(store.loadProfiles()) }
    val subscriptions = mutableStateListOf<Subscription>().apply { addAll(store.loadSubscriptions()) }
    val history = mutableStateListOf<ConnectionRecord>().apply { addAll(store.loadHistory()) }

    var settings by mutableStateOf(store.settings()); private set
    var networkSnapshot by mutableStateOf(intelligence.currentSnapshot()); private set
    var intelligenceStatus by mutableStateOf(intelligence.status(settings)); private set
    var sentinel by mutableStateOf(PrivacySentinelState()); private set
    var iranMode by mutableStateOf(IranModeState()); private set
    var state by mutableStateOf("DISCONNECTED"); private set
    var stateDetail by mutableStateOf(""); private set

    /**
     * Id of the profile currently carrying traffic. Screens must identify the active node by id;
     * display names are user-editable and are frequently duplicated inside one subscription.
     */
    var activeProfileId by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    var benchmarks by mutableStateOf<List<BenchmarkResult>>(emptyList()); private set
    var privacy by mutableStateOf<PrivacyReport?>(null); private set

    // Live tunnel telemetry. Ping is HTTPS time-to-first-response through the selected Xray path,
    // not the localhost SOCKS handshake.
    // ------------------------------------------------------------------
    // Live batch progress
    //
    // Tests and refreshes report per-item state so each node/source card can show its own progress
    // instead of one anonymous bar at the top of the screen.
    // ------------------------------------------------------------------
    var probeBatch by mutableStateOf<Set<String>>(emptySet()); private set
    var probeRunning by mutableStateOf<Set<String>>(emptySet()); private set
    var probeFinished by mutableStateOf<Set<String>>(emptySet()); private set
    var probeTotal by mutableStateOf(0); private set
    var probeCurrentName by mutableStateOf(""); private set
    var refreshingSources by mutableStateOf<Set<String>>(emptySet()); private set

    val probeDone: Int get() = probeFinished.size
    val probeActive: Boolean get() = probeTotal > 0

    /** True while any card is showing its own progress, so the global bar can stay hidden. */
    val inlineProgressActive: Boolean get() = probeActive || refreshingSources.isNotEmpty()

    fun probeStateOf(id: String): ProbeState = when {
        id in probeRunning -> ProbeState.TESTING
        probeTotal > 0 && id in probeBatch && id !in probeFinished -> ProbeState.QUEUED
        else -> ProbeState.IDLE
    }

    private fun beginProbeBatch(candidates: List<ProxyProfile>) = postToMain {
        probeBatch = candidates.mapTo(mutableSetOf()) { it.id }
        probeRunning = emptySet()
        probeFinished = emptySet()
        probeTotal = probeBatch.size
        probeCurrentName = ""
    }

    private fun markProbeStart(profile: ProxyProfile) = postToMain {
        probeRunning = probeRunning + profile.id
        probeCurrentName = profile.name
    }

    /** Publishes one finished node immediately; the card updates while the batch continues. */
    private fun markProbeResult(profile: ProxyProfile, result: BenchmarkResult) = postToMain {
        probeRunning = probeRunning - profile.id
        probeFinished = probeFinished + profile.id
        mergeBenchmarks(listOf(result))
    }

    private fun endProbeBatch() = postToMain {
        probeBatch = emptySet()
        probeRunning = emptySet()
        probeFinished = emptySet()
        probeTotal = 0
        probeCurrentName = ""
    }

    private fun beginRefresh(ids: Collection<String>) = postToMain {
        refreshingSources = ids.toSet()
    }

    private fun endRefresh(id: String) = postToMain {
        refreshingSources = refreshingSources - id
    }

    var livePingMs by mutableStateOf(0); private set
    var liveDownBps by mutableStateOf(0L); private set
    var liveUpBps by mutableStateOf(0L); private set
    var liveRouteScore by mutableStateOf(-1); private set
    var liveRouteSamples by mutableStateOf(0); private set

    init {
        notifier.ensureChannels()
        intelligence.startMonitoring()
        intelligence.addNetworkListener { next ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                networkSnapshot = next
                refreshIntelligenceStatus()
            }
            // The physical underlay changed, so the ISP almost certainly changed with it.
            scanIranMode(force = true, deep = false)
        }
        refreshIntelligenceStatus()
        scanIranMode(force = true, deep = true)
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
        val down = downBps.coerceAtLeast(0)
        val up = upBps.coerceAtLeast(0)
        postToMain {
            liveDownBps = down
            liveUpBps = up
        }
    }

fun updateRouteQuality(pingMs: Int) {
        if (pingMs <= 0) return
        val rawScore = calculateLiveRouteScore(pingMs)
        postToMain {
            livePingMs = pingMs
            liveRouteScore =
                if (liveRouteScore < 0) rawScore
                else ((liveRouteScore * 3 + rawScore * 2) / 5).coerceIn(0, 100)
            liveRouteSamples = (liveRouteSamples + 1).coerceAtMost(1_000_000)
        }
    }

fun resetTelemetry() {
        postToMain {
            livePingMs = 0
            liveDownBps = 0
            liveUpBps = 0
            liveRouteScore = -1
            liveRouteSamples = 0
        }
    }

    fun profile(id: String) = profiles.firstOrNull { it.id == id }

    /** True only for the node that is actually carrying traffic right now. */
    fun isActiveProfile(id: String): Boolean {
        if (state != "CONNECTED" || id.isBlank()) return false
        if (activeProfileId.isNotBlank()) return activeProfileId == id
        // Pre-connect state restored without an id: fall back to the displayed route name.
        return stateDetail.isNotBlank() && profile(id)?.name == stateDetail
    }

    fun setRuntimeState(s: String, d: String) {
        state = s
        stateDetail = d
        if (s != "CONNECTED") {
            activeProfileId = ""
            resetTelemetry()
        }
    }

    fun updateSettings(v: AppSettings) {
        settings = v
        store.saveSettings(v)
        notifier.ensureChannels()
        if (!v.smartNotificationsEnabled) notifier.cancelOptional()
        refreshIntelligenceStatus()
    }

    /**
     * Effective settings for one profile. Marble Intelligence already folds Iran Mode into its own
     * output, so the shield is only applied here when the intelligence engine is switched off.
     */
    fun effectiveSettingsFor(profile: ProxyProfile): AppSettings =
        IdentityGuard.apply(
            if (settings.intelligenceEnabled) {
                intelligence.effectiveSettings(profile, settings)
            } else {
                IranShield.apply(settings, profile, iranMode, geoIpReady())
            }
        )

    private fun geoIpReady(): Boolean =
        runCatching { xray.routingAssetStatus().geoIpReady }.getOrDefault(false)

    // ------------------------------------------------------------------
    // Iran Mode
    // ------------------------------------------------------------------

    /**
     * Runs an Iran Mode detection sweep on the physical underlay.
     *
     * @param force ignores the re-scan interval (used on network change and manual re-scan).
     * @param deep also fingerprints the filtering techniques currently applied to the link.
     */
    fun scanIranMode(force: Boolean = false, deep: Boolean = false) {
        val policy = settings.iranModePolicy
        val previous = iranMode
        val now = System.currentTimeMillis()
        val networkChanged = previous.networkKey != intelligence.currentSnapshot().key()
        val stale = now - previous.lastScanAt >= IRAN_RESCAN_INTERVAL_MS

        if (!force && !networkChanged && !stale) return
        if (!iranScanInFlight.compareAndSet(false, true)) return

        val deepProbe = (deep || previous.techniques.isEmpty() || networkChanged) &&
            settings.iranDeepProbeEnabled

        postToMain { iranMode = previous.copy(scanning = true) }

        iranScanner.execute {
            val detected = runCatching {
                iranDetector.detect(
                    policy = policy,
                    tunnelActive = state != "DISCONNECTED",
                    deepProbe = deepProbe,
                    previous = previous
                )
            }.getOrElse {
                previous.copy(
                    scanning = false,
                    lastScanAt = now,
                    summary = "Iran Mode scan failed • ${it::class.java.simpleName}"
                )
            }

            // The countermeasure list is produced by the engine; trim it to what the user's switches
            // actually allow so the panel never claims something that is turned off.
            val next = when {
                !detected.active -> detected
                !settings.iranModeCountermeasures -> detected.copy(
                    countermeasures = listOf("Detection only • countermeasures are switched off in settings")
                )
                !settings.iranDomesticDirect -> detected.copy(
                    countermeasures = detected.countermeasures.filterNot { it.startsWith("Domestic") }
                )
                else -> detected
            }

            iranScanInFlight.set(false)
            intelligence.setIranModeState(next, geoIpReady())
            postToMain {
                iranMode = next
                // Keep the UI status and every downstream engine decision synchronized with the
                // same Iran-underlay observation; do not wait for another connectivity callback.
                refreshIntelligenceStatus()
                announceIranMode(previous, next)
            }
        }
    }

    fun setIranModePolicy(policy: IranModePolicy) {
        if (settings.iranModePolicy == policy) return
        updateSettings(settings.copy(iranModePolicy = policy))
        message = when (policy) {
            IranModePolicy.AUTO -> "Iran Mode set to automatic ISP detection"
            IranModePolicy.ALWAYS_ON -> "Iran Mode forced on for every network"
            IranModePolicy.OFF -> "Iran Mode disabled"
        }
        scanIranMode(force = true, deep = policy != IranModePolicy.OFF)
    }

    private fun announceIranMode(previous: IranModeState, next: IranModeState) {
        if (!next.active) {
            if (previous.active) message = "Iran Mode off • ${next.summary}"
            return
        }

        val ispLine = next.ispLine
        if (!previous.active || previous.ispLine != ispLine) {
            message = "IRAN MODE ON • $ispLine • ${next.confidence}% confidence"
            if (settings.iranModeNotify) {
                notifier.alert(
                    SmartNotificationKind.NETWORK,
                    "iran-mode:${next.networkKey}:$ispLine",
                    "Iran Mode activated",
                    "Detected $ispLine. Anti-filtering countermeasures are active.",
                    settings.copy(notifyNetworkChanges = settings.iranModeNotify),
                    minIntervalOverrideMs = 60_000L
                )
            }
        }
    }

private fun postToMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
        }
    }

    private fun calculateLiveRouteScore(pingMs: Int): Int {
        val latencyFactor = exp(-pingMs.coerceIn(1, 10_000) / 360.0)
        return (100.0 * latencyFactor).roundToInt().coerceIn(0, 100)
    }

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
            beginRefresh(listOf(sub.id))
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

            val pending = subscriptions.toList()
            beginRefresh(pending.map { it.id })
            pending.forEach { sub ->
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

                endRefresh(sub.id)
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
            (
                activeProfileId in doomedIds ||
                    (activeProfileId.isBlank() && profiles.any { it.subscriptionId == id && it.name == stateDetail })
                )
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

    fun subscriptionNodeCount(id: String): Int = profiles.count { it.subscriptionId == id }

    /** Reassigns a profile to another subscription bucket (or "manual") so nodes can move between library sources. */
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
        activeProfileId = p.id
        if (settings.rememberLast) store.setLastProfileId(p.id)
        history += ConnectionRecord(p.id, p.name, System.currentTimeMillis(), "connected:${settings.connectionMode.name}")
        // The store keeps the last 200 records; keep the in-memory list bounded the same way.
        while (history.size > MAX_HISTORY_RECORDS) history.removeAt(0)
        store.saveHistory(history)
    }

    fun startVpn(p: ProxyProfile) {
        scanIranMode()
        setRuntimeState("CONNECTING", p.name)
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_START)
            .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
            .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_TUN)
        launchConnectionService(intent, p.name)
    }

    fun startLocalProxy(p: ProxyProfile) {
        scanIranMode()
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

    /**
     * Folds a fresh measurement set into the existing evidence table instead of replacing it.
     * Testing one node used to erase every other measured route from Library/Quality.
     */
    private fun mergeBenchmarks(fresh: List<BenchmarkResult>) {
        if (fresh.isEmpty()) return
        val freshIds = fresh.mapTo(mutableSetOf()) { it.profileId }
        val liveIds = profiles.mapTo(mutableSetOf()) { it.id }
        benchmarks = (fresh + benchmarks.filterNot { it.profileId in freshIds })
            .distinctBy { it.profileId }
            .filter { it.profileId in liveIds || it.profileId in freshIds }
            .sortedWith(
                compareByDescending<BenchmarkResult> { it.score }
                    .thenBy { it.latencyMs }
            )
    }

    fun smart(onBest: (ProxyProfile) -> Unit) {
        task("Marble Intelligence • selecting route") {
            val engine = BenchmarkEngine(xray, intelligence)
            if (settings.intelligenceEnabled && settings.raceConnectEnabled && profiles.size > 1) {
                val raced = engine.race(
                    profiles.toList(),
                    settings,
                    onCandidates = ::beginProbeBatch,
                    onStart = ::markProbeStart,
                    onResult = ::markProbeResult
                ) { n -> message = "Connection race • $n" }
                endProbeBatch()
                if (raced != null) {
                    mergeBenchmarks(listOf(raced.second))
                    message = "Race winner: ${raced.first.name}"
                    android.os.Handler(android.os.Looper.getMainLooper()).post { onBest(raced.first) }
                    return@task
                }
            }
            val results = engine.run(
                profiles.toList(),
                settings,
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult
            ) { a, b, n -> message = "Tunnel intelligence $a/$b • $n" }
            mergeBenchmarks(results)
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
            val results = BenchmarkEngine(xray, intelligence).run(
                profiles.toList(),
                settings,
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult
            ) { done, total, name ->
                message = "Smart rank $done/$total • $name"
            }
            mergeBenchmarks(results)
            val best = results.firstOrNull { it.success > 0 }
            message = if (best == null) "Smart rank finished • no healthy route found"
            else "Best route • ${best.name} • ${best.latencyMs.toInt()} ms • score ${best.score.toInt()}"
        }
    }

    fun fullTest(p: ProxyProfile) {
        task("Full test ${p.name}") {
            val result = BenchmarkEngine(xray, intelligence).run(
                listOf(p),
                settings.copy(benchCandidates = 1),
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult
            )
            mergeBenchmarks(result)
            message = result.firstOrNull()?.let {
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
            // A full-library sweep is about reachability and latency ranking. Extra samples per
            // node multiply the wall-clock cost of the whole batch for very little extra evidence.
            val testSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = all.size,
                benchSamples = settings.benchSamples.coerceAtMost(2)
            )
            val results = BenchmarkEngine(xray, intelligence).run(
                all,
                testSettings,
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult
            ) { a, b, n -> message = "Tunnel test $a/$b • $n" }
            mergeBenchmarks(results)
            val passed = results.count { it.success > 0 }
            message = "Tested ${results.size} configs • $passed reachable"
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

    fun routingAssetStatus(): RoutingAssetStatus = xray.routingAssetStatus()

    /** Apply MarbleNG's recommended Iran routing baseline without erasing explicit block/proxy lists. */
    fun applyIranRoutingPreset(prepareAssets: Boolean = true) {
        updateSettings(
            settings.copy(
                routingMode = RoutingMode.GEO_DIRECT,
                geoIpUrl = RoutingDefaults.GEOIP_URL,
                geoSiteUrl = RoutingDefaults.GEOSITE_URL,
                routeGeoIpTags = RoutingDefaults.GEOIP_DIRECT_TAGS,
                routeGeoSiteTags = RoutingDefaults.GEOSITE_DIRECT_TAGS,
                routeBypassPrivate = true,
                routeBlockAds = true,
                routeAdsTag = RoutingDefaults.ADS_TAG,
                routeDomainStrategy = RoutingDefaults.DOMAIN_STRATEGY,
                iranDomesticDirect = true
            )
        )

        when {
            state != "DISCONNECTED" ->
                message = "Iran routing preset saved • reconnect to apply it to the active tunnel"
            prepareAssets && !busy ->
                prepareRoutingAssets(force = false)
            else ->
                message = "Iran routing preset saved"
        }
    }

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

    fun resetSettings() {
        updateSettings(AppSettings())
        message = "Settings reset • Iran direct routing + ad blocking restored"
    }

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
        checks += when {
            iranMode.active -> "✔ Iran Mode ACTIVE • ${iranMode.ispLine} • ${iranMode.confidence}% confidence"
            settings.iranModePolicy == IranModePolicy.OFF -> "⚠ Iran Mode disabled in settings"
            else -> "✔ Iran Mode standby • ${iranMode.summary}"
        }
        if (iranMode.active && iranMode.techniques.isNotEmpty()) {
            checks += "✔ Filtering observed • ${iranMode.techniques.joinToString(", ") { it.label }}"
        }
        checks += "✔ Thermal budget ${intelligenceStatus.thermalBudgetPercent}% • effective MTU ${intelligenceStatus.effectiveMtu.takeIf { it > 0 } ?: settings.mtuMax}"
        checks += if (notifier.optionalPermissionGranted()) "✔ Optional notification permission" else "⚠ Optional notification permission not granted"
        return checks.joinToString("\n")
    }

    fun clearMessage() { message = "" }
    fun readLogs(): String = RuntimeDiagnostics(context).bundle(xray.logFile)

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
                // No card may be left spinning if a batch aborts.
                endProbeBatch()
                postToMain { refreshingSources = emptySet() }
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

    private companion object {
        /** Detection is cheap but not free; unchanged networks are re-checked every 10 minutes. */
        const val IRAN_RESCAN_INTERVAL_MS = 10L * 60L * 1000L

        /** Mirrors AppStore's persisted history window. */
        const val MAX_HISTORY_RECORDS = 200
    }
}
