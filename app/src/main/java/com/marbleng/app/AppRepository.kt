package com.marbleng.app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.*
import com.marbleng.app.core.*
import com.marbleng.app.data.AppStore
import com.marbleng.app.model.*
import com.marbleng.app.quicktile.MarbleQuickTileService
import com.marbleng.app.net.*
import com.marbleng.app.vpn.MarbleVpnService
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.exp
import kotlin.math.roundToInt

// MARBLE_APP_UPDATE_REPO_V102
data class AppUpdateInfo(
    val version: String,
    val tag: String,
    val title: String,
    val notes: String,
    val url: String
)

// MARBLE_SERVER_INTEL_REPO_V56
data class ServerIntelInfo(
    val endpoint: String,
    val ip: String,
    val ipType: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val countryCode: String = "",
    val flag: String = "",
    val asn: String = "",
    val organization: String = "",
    val isp: String = "",
    val domain: String = "",
    val hosting: Boolean = false,
    val proxy: Boolean = false,
    val vpn: Boolean = false,
    val tor: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val datacenterLabel: String
        get() = organization.ifBlank { isp.ifBlank { "Unknown network" } }

    val locationLabel: String
        get() = listOf(city, region, country).filter(String::isNotBlank).distinct().joinToString(", ")
}

class AppRepository(private val context: Context, val xray: XrayManager) {
    // MARBLE_LIBRARY_POWER_V10
    // MARBLE_ENGINE_RESCUE_V11
    // MARBLE_CONNECT_DISPATCH_V13
    // MARBLE_SMART_ENGINE_V14
    // MARBLE_ULTIMATE_BUG_FINDER_REPO_V15
    // MARBLE_REPO_ANR_HARDENING_V16
    // MARBLE_LIVE_LATENCY_V17
    // MARBLE_IRAN_FORCE_RUNTIME_V22
    // MARBLE_INTELLIGENCE_V24
    // MARBLE_FAST_CONNECT_SSH_LIBRARY_V25
    // MARBLE_SELECTED_SOURCE_V25_4
    // MARBLE_MEMORY_PRESSURE_V26
    // MARBLE_SMART_XRAY_RANK_ALL_V27
    // MARBLE_LIBRARY_SCOPE_V32
    // MARBLE_LIBRARY_MEMORY_V33
    // MARBLE_SYSTEM_INTEGRITY_REPO_V38
    // MARBLE_WARM_TUNNEL_RANK_V42
    // MARBLE_REPEATABLE_RANK_V44
    // MARBLE_V2RAYNG_SMART_RANK_V45
    // MARBLE_EVIDENCE_WEIGHTED_QUALITY_V46

    private val store = AppStore(context)
    private val io = Executors.newFixedThreadPool(3)

    // Connect decisions touch synchronized SQLite health history. Keep that work off MainActivity
    // and independent from subscription/import tasks so a refresh can never swallow a Connect tap.
    private val connectDecisionWorker = Executors.newSingleThreadExecutor()
    private val connectDecisionInFlight = AtomicBoolean(false)

    // IntelligenceStatus includes a SQLite count + thermal inspection. Coalesce it on a worker so
    // a DB writer can never make the Android input thread wait on the HealthDb monitor.
    private val statusWorker = Executors.newSingleThreadExecutor()
    private val statusRefreshInFlight = AtomicBoolean(false)

    // `busy` is UI state, not a cross-thread lock. This atomic gate is the actual task mutex.
    private val taskInFlight = AtomicBoolean(false)

    // Iran Mode scanning runs on its own thread so a detection sweep can never block or be blocked
    // by a user-visible task such as a subscription refresh.
    private val iranScanner = Executors.newSingleThreadExecutor()
    private val iranScanInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val iranPolicyGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    val intelligence = MarbleIntelligence(context)
    private val notifier = SmartNotifier(context)
    private val iranDetector = IranModeDetector(context, intelligence)
    private val bugFinder = BugFinder(context, xray)
    private val diagnostics = RuntimeDiagnostics(context)

    val profiles = mutableStateListOf<ProxyProfile>().apply { addAll(store.loadProfiles()) }
    val subscriptions = mutableStateListOf<Subscription>().apply { addAll(store.loadSubscriptions()) }
    val history = mutableStateListOf<ConnectionRecord>().apply { addAll(store.loadHistory()) }

    var settings by mutableStateOf(store.settings()); private set

    private fun normalizeLibrarySourceFilter(id: String): String = when {
        id == "all" -> "all"
        id == "manual" && settings.manualSourceEnabled -> "manual"
        id == "manual" -> "all"
        subscriptions.any { it.id == id } -> id
        else -> "all"
    }

    var librarySourceFilter by mutableStateOf(
        normalizeLibrarySourceFilter(store.librarySourceFilter())
    )
        private set

    fun selectLibrarySource(id: String) {
        val normalized = normalizeLibrarySourceFilter(id)
        if (librarySourceFilter == normalized) return
        librarySourceFilter = normalized
        store.setLibrarySourceFilter(normalized)
        diagnostics.event(
            "LIBRARY",
            "source-selected",
            "source" to normalized.take(24),
            "name" to libraryScopeLabel(normalized)
        )
    }

    fun ensureLibrarySourceSelectionValid() {
        val normalized = normalizeLibrarySourceFilter(librarySourceFilter)
        if (normalized != librarySourceFilter) {
            librarySourceFilter = normalized
            store.setLibrarySourceFilter(normalized)
            diagnostics.event("LIBRARY", "source-selection-repaired", "source" to normalized)
        }
    }

    private data class LibraryTarget(val id: String, val name: String)

    /** A concrete Library source can receive user imports; "all" is only a view. */
    private fun resolveLibraryTarget(id: String): LibraryTarget? = when {
        id == "manual" && settings.manualSourceEnabled -> LibraryTarget("manual", "Manual")
        id == "manual" || id == "all" || id.isBlank() -> null
        else -> subscriptions.firstOrNull { it.id == id }?.let { LibraryTarget(it.id, it.name) }
    }

    private fun profileSourceEnabled(profile: ProxyProfile): Boolean =
        settings.manualSourceEnabled || profile.subscriptionId != "manual"

    val libraryProfiles: List<ProxyProfile>
        get() = profiles.filter(::profileSourceEnabled)

    private fun enabledProfilesSnapshot(): List<ProxyProfile> =
        profiles.filter(::profileSourceEnabled)

    /**
     * Resolve the exact Library source selected by the user.
     *
     * Fail closed: a stale/unknown source id returns an empty set — never the whole Library.
     * "all" is the only id allowed to expand to every enabled profile.
     */
    private fun libraryScopeSnapshot(sourceId: String): List<ProxyProfile> {
        val available = enabledProfilesSnapshot()
        return when (sourceId) {
            "all" -> available
            "manual" -> if (settings.manualSourceEnabled) {
                available.filter { it.subscriptionId == "manual" }
            } else {
                emptyList()
            }
            else -> {
                if (subscriptions.none { it.id == sourceId }) emptyList()
                else available.filter { it.subscriptionId == sourceId }
            }
        }
    }

    private fun libraryScopeLabel(sourceId: String): String = when (sourceId) {
        "all" -> "All sources"
        "manual" -> "Manual"
        else -> subscriptions.firstOrNull { it.id == sourceId }?.name ?: "Missing source"
    }

    /** Refresh exactly the source represented by the Library selection. */
    fun refreshLibrarySource(sourceId: String) {
        when (sourceId) {
            "all" -> refreshAll()
            "manual" -> message = "Manual source has no remote subscription to refresh"
            else -> {
                val sub = subscriptions.firstOrNull { it.id == sourceId }
                when {
                    sub == null -> message = "Selected Library source no longer exists"
                    sub.url.isBlank() -> message = "${sub.name} is a local source • nothing remote to refresh"
                    else -> refresh(sub.id)
                }
            }
        }
    }

    private fun migrateLocalSourceOwnershipIfNeeded() {
        val localIds = subscriptions.asSequence()
            .filter { it.url.isBlank() }
            .mapTo(mutableSetOf()) { it.id }
        var changed = false
        for (index in profiles.indices) {
            val current = profiles[index]
            val userOwnedBucket = current.subscriptionId == "manual" ||
                current.subscriptionId in localIds
            if (userOwnedBucket && current.sourceManaged) {
                profiles[index] = current.copy(sourceManaged = false)
                changed = true
            }
        }
        if (changed) store.saveProfiles(profiles)
    }

    var networkSnapshot by mutableStateOf(intelligence.currentSnapshot()); private set
    var intelligenceStatus by mutableStateOf(IntelligenceStatus()); private set
    var sentinel by mutableStateOf(PrivacySentinelState()); private set
    var iranMode by mutableStateOf(IranModeState()); private set
    var state by mutableStateOf("DISCONNECTED"); private set
    var stateDetail by mutableStateOf(""); private set

    /**
     * Id of the profile currently carrying traffic. Screens must identify the active node by id;
     * display names are user-editable and are frequently duplicated inside one subscription.
     */
    var activeProfileId by mutableStateOf(""); private set
    var activeProfileSourceId by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    // Background engines are allowed to publish status text, but Compose state itself is committed
    // on the main looper. This removes cross-thread snapshot churn from benchmark/refresh callbacks.
    private var messageState by mutableStateOf("")
    var message: String
        get() = messageState
        private set(value) {
            postToMain { messageState = value }
        }

    var benchmarks by mutableStateOf<List<BenchmarkResult>>(emptyList()); private set
    var privacy by mutableStateOf<PrivacyReport?>(null); private set
    var bugReport by mutableStateOf<BugReport?>(null); private set

    // Selected-server public metadata. Requests are opt-in, cached and generation-guarded.
    var serverIntel by mutableStateOf<ServerIntelInfo?>(null); private set
    var serverIntelLoading by mutableStateOf(false); private set
    var serverIntelError by mutableStateOf(""); private set
    private val serverIntelGeneration = AtomicLong(0L)

    /** Latest stable GitHub Release that is newer than this APK. */
    var availableUpdate by mutableStateOf<AppUpdateInfo?>(null); private set

    private val updateCheckInFlight = AtomicBoolean(false)
    @Volatile private var lastUpdateCheckAt = 0L
    @Volatile private var dismissedUpdateTag = ""


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
    var liveJitterMs by mutableStateOf(0); private set
    var liveDownBps by mutableStateOf(0L); private set
    var liveUpBps by mutableStateOf(0L); private set
    var liveRouteScore by mutableStateOf(-1); private set
    var liveRouteSamples by mutableStateOf(0); private set
    var liveRouteAttempts by mutableStateOf(0); private set
    var liveRouteSuccessPercent by mutableStateOf(0); private set
    var liveTailLatencyMs by mutableStateOf(0); private set
    var liveJitterSamples by mutableStateOf(0); private set
    var liveRouteProbeStatus by mutableStateOf(""); private set

    init {
        migrateLocalSourceOwnershipIfNeeded()
        RuntimeDiagnostics.setDebugEnabled(context, settings.debugModeEnabled)
        diagnostics.event("APP", "repository-init", "debugMode" to settings.debugModeEnabled)
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
        // Cheap classification only at app start; deep filtering fingerprints are deferred.
        scanIranMode(force = true, deep = false)
        val remoteSubscriptions = subscriptions.filter { it.url.isNotBlank() }
        if (settings.subscriptionAutoRefresh && remoteSubscriptions.isNotEmpty()) {
            val maxAgeMs = settings.subscriptionRefreshHours.coerceIn(1, 168) * 3_600_000L
            val stale = remoteSubscriptions.any {
                it.updatedAt <= 0L || System.currentTimeMillis() - it.updatedAt >= maxAgeMs
            }
            if (stale) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { refreshAll() }
            }
        }
    }

    /**
     * Resolve the selected endpoint and enrich only its public IP with coarse public metadata.
     *
     * Privacy boundary:
     * - disabled by default;
     * - proxy config, UUID/password, SNI and subscription URL are never sent;
     * - only the already-public resolved server IP is queried at ipwho.is;
     * - a 15-minute per-endpoint cache avoids noisy/redundant lookups.
     */
    fun refreshServerIntel(targetProfile: ProxyProfile? = null, force: Boolean = false) {
        if (!settings.serverIntelEnabled) {
            postToMain {
                serverIntelLoading = false
                serverIntelError = ""
            }
            return
        }

        val target = targetProfile
            ?: profile(activeProfileId, activeProfileSourceId)
            ?: lastProfile()
        val endpoint = target?.host
            ?.trim()
            ?.removeSurrounding("[", "]")
            .orEmpty()

        if (target == null || endpoint.isBlank()) {
            postToMain {
                serverIntel = null
                serverIntelLoading = false
                serverIntelError = "Choose a server with a valid endpoint first"
            }
            return
        }

        val now = System.currentTimeMillis()
        val cached = serverIntel
        if (!force &&
            cached != null &&
            cached.endpoint.equals(endpoint, ignoreCase = true) &&
            now - cached.fetchedAt in 0L until 15 * 60_000L
        ) {
            return
        }

        val preferIpv6Snapshot = settings.preferIpv6
        val generation = serverIntelGeneration.incrementAndGet()
        postToMain {
            serverIntelLoading = true
            serverIntelError = ""
        }

        io.execute {
            var connection: HttpURLConnection? = null
            var basic: ServerIntelInfo? = null
            try {
                val addresses = InetAddress.getAllByName(endpoint)
                    .filterNot {
                        it.isAnyLocalAddress ||
                            it.isLoopbackAddress ||
                            it.isLinkLocalAddress
                    }

                if (addresses.isEmpty()) {
                    throw IllegalStateException("Server endpoint did not resolve to a usable IP")
                }

                val preferred = if (preferIpv6Snapshot) {
                    addresses.firstOrNull { it.address.size == 16 }
                        ?: addresses.firstOrNull { it.address.size == 4 }
                } else {
                    addresses.firstOrNull { it.address.size == 4 }
                        ?: addresses.firstOrNull { it.address.size == 16 }
                } ?: addresses.first()

                val resolvedIp = preferred.hostAddress
                    ?.substringBefore('%')
                    ?.trim()
                    .orEmpty()
                if (resolvedIp.isBlank()) {
                    throw IllegalStateException("Server endpoint resolved without an address")
                }

                val baseInfo = ServerIntelInfo(
                    endpoint = endpoint,
                    ip = resolvedIp,
                    ipType = if (preferred.address.size == 16) "IPv6" else "IPv4",
                    fetchedAt = System.currentTimeMillis()
                )
                basic = baseInfo
                postToMain {
                    if (serverIntelGeneration.get() == generation) {
                        serverIntel = baseInfo
                    }
                }

                val encodedIp = java.net.URLEncoder.encode(
                    resolvedIp,
                    Charsets.UTF_8.name()
                )
                val fields = "success,message,ip,type,country,country_code,region,city,flag,connection,security"
                connection = (URL(
                    "https://ipwho.is/$encodedIp?fields=$fields"
                ).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 6_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "MarbleNG/${BuildConfig.VERSION_NAME}")
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Server metadata lookup returned HTTP $code")
                }

                val payload = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val json = JSONObject(payload)
                if (!json.optBoolean("success", false)) {
                    throw IllegalStateException(
                        json.optString("message").ifBlank { "Server metadata lookup failed" }
                    )
                }

                val flag = json.optJSONObject("flag")
                val network = json.optJSONObject("connection")
                val security = json.optJSONObject("security")
                val asnNumber = network?.optLong("asn") ?: 0L

                val enriched = baseInfo.copy(
                    ip = json.optString("ip").ifBlank { resolvedIp },
                    ipType = json.optString("type").ifBlank { baseInfo.ipType },
                    city = json.optString("city"),
                    region = json.optString("region"),
                    country = json.optString("country"),
                    countryCode = json.optString("country_code"),
                    flag = flag?.optString("emoji").orEmpty(),
                    asn = asnNumber.takeIf { it > 0L }?.let { "AS$it" }.orEmpty(),
                    organization = network?.optString("org").orEmpty(),
                    isp = network?.optString("isp").orEmpty(),
                    domain = network?.optString("domain").orEmpty(),
                    hosting = security?.optBoolean("hosting", false) == true,
                    proxy = security?.optBoolean("proxy", false) == true,
                    vpn = security?.optBoolean("vpn", false) == true,
                    tor = security?.optBoolean("tor", false) == true,
                    fetchedAt = System.currentTimeMillis()
                )

                postToMain {
                    if (serverIntelGeneration.get() == generation) {
                        serverIntel = enriched
                        serverIntelError = ""
                    }
                }
                diagnostics.event(
                    "SERVER_INTEL",
                    "lookup-ready",
                    "endpoint" to endpoint.take(80),
                    "ipType" to enriched.ipType,
                    "country" to enriched.countryCode,
                    "hosting" to enriched.hosting
                )
            } catch (t: Throwable) {
                val fallback = basic
                postToMain {
                    if (serverIntelGeneration.get() == generation) {
                        if (fallback != null) serverIntel = fallback
                        serverIntelError = when {
                            t.message?.contains("429") == true ->
                                "Metadata rate limit reached • resolved IP is still shown"
                            fallback != null ->
                                "Location/network metadata unavailable • resolved IP is still shown"
                            else ->
                                "Could not resolve server information"
                        }
                    }
                }
                diagnostics.event(
                    "SERVER_INTEL",
                    "lookup-failed",
                    "type" to t::class.java.simpleName
                )
            } finally {
                connection?.disconnect()
                postToMain {
                    if (serverIntelGeneration.get() == generation) {
                        serverIntelLoading = false
                    }
                }
            }
        }
    }

    /**
     * Checks the latest stable GitHub Release without blocking the UI.
     *
     * MainActivity calls this from onStart(), so returning to MarbleNG triggers a fresh check.
     * A short throttle prevents lifecycle bounce (permission dialogs/browser return) from
     * hammering GitHub. Dismissing a version hides that exact tag until the process restarts.
     */
    fun checkForAppUpdate(force: Boolean = false) {
        if (!settings.appUpdateCheckEnabled) return

        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < 60_000L) return
        if (!updateCheckInFlight.compareAndSet(false, true)) return
        lastUpdateCheckAt = now

        io.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(
                    "https://api.github.com/repos/marble098/MarbleNG/releases/latest"
                ).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "MarbleNG/${BuildConfig.VERSION_NAME}")
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    diagnostics.event("UPDATE", "check-http", "code" to code)
                    return@execute
                }

                val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText()
                }
                val json = JSONObject(payload)
                val tag = json.optString("tag_name").trim()
                val latest = parseStableSemver(tag) ?: return@execute
                val current = parseStableSemver(BuildConfig.VERSION_NAME) ?: return@execute

                if (!isSemverNewer(latest, current)) {
                    postToMain { availableUpdate = null }
                    return@execute
                }

                if (tag == dismissedUpdateTag) return@execute

                val htmlUrl = json.optString("html_url").trim()
                    .takeIf { it.startsWith("https://github.com/") }
                    ?: "https://github.com/marble098/MarbleNG/releases"

                val release = AppUpdateInfo(
                    version = "${latest.first}.${latest.second}.${latest.third}",
                    tag = tag,
                    title = json.optString("name").trim()
                        .ifBlank { "MarbleNG $tag" }
                        .take(120),
                    notes = json.optString("body")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .trim()
                        .take(1_800),
                    url = htmlUrl
                )

                postToMain {
                    if (settings.appUpdateCheckEnabled && release.tag != dismissedUpdateTag) {
                        availableUpdate = release
                    }
                }
                diagnostics.event(
                    "UPDATE",
                    "available",
                    "current" to BuildConfig.VERSION_NAME,
                    "latest" to release.version
                )
            } catch (error: Throwable) {
                diagnostics.event(
                    "UPDATE",
                    "check-failed",
                    "type" to error::class.java.simpleName,
                    "message" to (error.message ?: "").take(160)
                )
            } finally {
                runCatching { connection?.disconnect() }
                updateCheckInFlight.set(false)
            }
        }
    }

    fun dismissAppUpdate() {
        availableUpdate?.tag?.takeIf { it.isNotBlank() }?.let { dismissedUpdateTag = it }
        postToMain { availableUpdate = null }
    }

    private fun parseStableSemver(raw: String): Triple<Int, Int, Int>? {
        val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(raw.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    private fun isSemverNewer(
        latest: Triple<Int, Int, Int>,
        current: Triple<Int, Int, Int>
    ): Boolean = when {
        latest.first != current.first -> latest.first > current.first
        latest.second != current.second -> latest.second > current.second
        else -> latest.third > current.third
    }

fun updateTelemetry(downBps: Long, upBps: Long) {
        val down = downBps.coerceAtLeast(0)
        val up = upBps.coerceAtLeast(0)
        postToMain {
            liveDownBps = down
            liveUpBps = up
        }
    }

fun updateRouteQuality(
        pingMs: Int,
        jitterMs: Int = -1,
        sampleCount: Int = -1,
        jitterSampleCount: Int = -1,
        attemptCount: Int = sampleCount,
        successPercent: Int = 100,
        tailLatencyMs: Int = -1
    ) {
        if (pingMs <= 0) return

        // Quality is computed from the same bounded evidence window as the displayed metrics.
        // A timeout is reliability evidence, unknown jitter stays neutral, and p90 catches a
        // congested tail that a median alone can hide.
        val rawScore = calculateLiveRouteScore(
            pingMs = pingMs,
            jitterMs = jitterMs,
            successPercent = successPercent,
            attemptCount = attemptCount,
            tailLatencyMs = tailLatencyMs
        )

        postToMain {
            livePingMs = pingMs
            if (jitterMs >= 0) {
                liveJitterMs = jitterMs.coerceIn(0, 10_000)
            }

            // The inputs are already rolling aggregates. A second EWMA here made Quality lag well
            // behind Ping/Jitter after a network change, so publish the evidence-window score.
            liveRouteScore = rawScore
            liveRouteAttempts = attemptCount.coerceIn(0, 10_000)
            liveRouteSuccessPercent = successPercent.coerceIn(0, 100)
            if (tailLatencyMs >= 0) liveTailLatencyMs = tailLatencyMs.coerceIn(0, 10_000)
            liveRouteProbeStatus = buildString {
                append("Verified HTTPS • ")
                append(sampleCount.coerceAtLeast(1))
                append('/')
                append(attemptCount.coerceAtLeast(1))
                append(" RTT • ")
                append(successPercent.coerceIn(0, 100))
                append("% success")
                if (tailLatencyMs > 0) append(" • p90 ${tailLatencyMs.coerceAtMost(10_000)} ms")
            }

            // v18 counted publications forever although ping came from a bounded window.
            // Publish the actual current window sizes.
            liveRouteSamples =
                if (sampleCount >= 0) sampleCount.coerceIn(0, 10_000)
                else (liveRouteSamples + 1).coerceAtMost(10_000)

            liveJitterSamples =
                if (jitterSampleCount >= 0) jitterSampleCount.coerceIn(0, 10_000)
                else if (jitterMs >= 0) {
                    (liveJitterSamples + 1).coerceAtMost(10_000)
                } else {
                    liveJitterSamples
                }
        }
    }

    fun beginRouteMeasurement() {
        postToMain {
            liveRouteProbeStatus = "Tunnel ready • verifying diverse HTTPS RTT targets"
        }
    }

    fun updateRouteProbeStatus(value: String) {
        postToMain {
            liveRouteProbeStatus = value.trim().take(180)
        }
    }

fun invalidateLiveJitter() {
        postToMain {
            liveJitterMs = 0
            liveJitterSamples = 0
        }
    }

fun resetTelemetry() {
        postToMain {
            livePingMs = 0
            liveJitterMs = 0
            liveDownBps = 0
            liveUpBps = 0
            liveRouteScore = -1
            liveRouteSamples = 0
            liveRouteAttempts = 0
            liveRouteSuccessPercent = 0
            liveTailLatencyMs = 0
            liveJitterSamples = 0
            liveRouteProbeStatus = ""
        }
    }

    /**
     * Release only reconstructable UI evidence under Android memory pressure.
     * The live VPN/Xray route, profiles, subscriptions, health DB and durable history are retained.
     * Numeric levels follow ComponentCallbacks2: 15 critical, 20 UI hidden, 40 background,
     * 60 moderate, 80 complete.
     */
    fun onMemoryPressure(level: Int) {
        diagnostics.event(
            "MEMORY", "trim",
            "level" to level,
            "state" to state,
            "benchmarks" to benchmarks.size,
            "hasBugReport" to (bugReport != null)
        )
        if (level < 15) return
        postToMain {
            if (level >= 20) privacy = null
            if (level >= 40) bugReport = null
            if (level >= 60 && !probeActive && probeRunning.isEmpty()) {
                val active = activeProfileId
                benchmarks = if (active.isBlank()) emptyList()
                else benchmarks.filter { it.profileId == active }.take(1)
            }
        }
    }

    /**
     * Resolve a config. Library mutations pass sourceId so identical configs in two sources remain
     * independent rows; engine callers may intentionally resolve by canonical config id only.
     */
    fun profile(id: String, sourceId: String? = null): ProxyProfile? =
        if (!sourceId.isNullOrBlank()) {
            profiles.firstOrNull { it.id == id && it.subscriptionId == sourceId }
        } else {
            profiles.firstOrNull { it.id == id }
        }

    /** True only for the exact Library row currently carrying traffic. */
    fun isActiveProfile(profile: ProxyProfile): Boolean {
        if (state != "CONNECTED" || profile.id.isBlank()) return false
        if (activeProfileId.isNotBlank()) {
            if (activeProfileId != profile.id) return false
            return activeProfileSourceId.isBlank() ||
                activeProfileSourceId == profile.subscriptionId
        }
        return stateDetail.isNotBlank() && profile.name == stateDetail
    }

    /** Compatibility helper for engine callers that intentionally identify configs by id. */
    fun isActiveProfile(id: String): Boolean =
        state == "CONNECTED" && id.isNotBlank() && activeProfileId == id

    fun setRuntimeState(s: String, d: String) {
        diagnostics.event("APP", "state", "from" to state, "to" to s, "detail" to d.take(160))
        postToMain {
            state = s
            stateDetail = d
            if (s != "CONNECTED") {
                activeProfileId = ""
                activeProfileSourceId = ""
                livePingMs = 0
                liveJitterMs = 0
                liveDownBps = 0L
                liveUpBps = 0L
                liveRouteScore = -1
                liveRouteSamples = 0
                liveRouteAttempts = 0
                liveRouteSuccessPercent = 0
                liveTailLatencyMs = 0
                liveJitterSamples = 0
                liveRouteProbeStatus = ""
            }
            MarbleQuickTileService.requestRefresh(context)
        }
    }

    fun updateSettings(v: AppSettings) {
        val debugChanged = settings.debugModeEnabled != v.debugModeEnabled
        val updateChecksWereEnabled = settings.appUpdateCheckEnabled
        settings = v
        if (!v.appUpdateCheckEnabled) {
            postToMain { availableUpdate = null }
        } else if (!updateChecksWereEnabled) {
            dismissedUpdateTag = ""
        }
        ensureLibrarySourceSelectionValid()
        store.saveSettings(v)
        if (debugChanged) {
            RuntimeDiagnostics.setDebugEnabled(context, v.debugModeEnabled)
            diagnostics.event("DEBUG", "mode-changed", "enabled" to v.debugModeEnabled)
        }
        notifier.ensureChannels()
        if (!v.smartNotificationsEnabled) notifier.cancelOptional()
        refreshIntelligenceStatus()
    }

    /**
     * Effective settings for one profile. Marble Intelligence already folds Iran Mode into its own
     * output, so the shield is only applied here when the intelligence engine is switched off.
     */
    fun effectiveSettingsFor(
        profile: ProxyProfile,
        withAcceleration: Boolean = true
    ): AppSettings =
        IdentityGuard.apply(
            if (settings.intelligenceEnabled) {
                intelligence.effectiveSettings(profile, settings, withAcceleration)
            } else {
                IranShield.apply(settings, profile, iranMode, geoIpReady())
            }
        )

    /**
     * Baseline the acceleration tuner measures against: everything the user, Iran Mode and
     * Identity Guard ask for, minus any method a previous tuning pass already applied.
     */
    fun tuningBaseFor(profile: ProxyProfile): AppSettings =
        effectiveSettingsFor(profile, withAcceleration = false)

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
        val networkKey = intelligence.currentSnapshot().key()

        /*
         * ALWAYS_ON / OFF are explicit policy, not detector requests.
         * No SCANNING state and no physical-network probes are allowed for Force on.
         */
        if (policy == IranModePolicy.ALWAYS_ON || policy == IranModePolicy.OFF) {
            val generation = iranPolicyGeneration.get()
            val baseState = if (policy == IranModePolicy.ALWAYS_ON) {
                IranModeState(
                    active = true,
                    policy = IranModePolicy.ALWAYS_ON,
                    confidence = 0,
                    networkKey = networkKey,
                    scanning = false,
                    lastScanAt = now,
                    summary = "Iran Mode forced on • underlay detection bypassed"
                )
            } else {
                IranModeState(
                    active = false,
                    policy = IranModePolicy.OFF,
                    confidence = 0,
                    networkKey = networkKey,
                    scanning = false,
                    lastScanAt = now,
                    summary = "Iran Mode disabled in settings"
                )
            }

            val next = when {
                !baseState.active -> baseState
                !settings.iranModeCountermeasures -> baseState.copy(
                    countermeasures = listOf(
                        "Force on is active • countermeasures are switched off in settings"
                    )
                )
                !settings.iranDomesticDirect -> baseState.copy(
                    countermeasures = IranShield.countermeasures(baseState)
                        .filterNot { it.startsWith("Domestic") }
                )
                else -> baseState.copy(
                    countermeasures = IranShield.countermeasures(baseState)
                )
            }

            intelligence.setIranModeState(next, geoIpReady())
            postToMain {
                if (
                    settings.iranModePolicy == policy &&
                    iranPolicyGeneration.get() == generation
                ) {
                    iranMode = next
                    refreshIntelligenceStatus()
                }
            }
            diagnostics.event(
                "IRAN",
                if (policy == IranModePolicy.ALWAYS_ON) "forced-on" else "disabled",
                "networkKey" to networkKey,
                "scanBypassed" to true
            )
            return
        }

        val networkChanged = previous.networkKey != networkKey
        val stale = now - previous.lastScanAt >= IRAN_RESCAN_INTERVAL_MS
        if (!force && !networkChanged && !stale) return
        if (!iranScanInFlight.compareAndSet(false, true)) return

        val policyGeneration = iranPolicyGeneration.get()
        val deepProbe = (deep || previous.techniques.isEmpty() || networkChanged) &&
            settings.iranDeepProbeEnabled

        postToMain {
            if (
                settings.iranModePolicy == IranModePolicy.AUTO &&
                iranPolicyGeneration.get() == policyGeneration
            ) {
                iranMode = previous.copy(
                    policy = IranModePolicy.AUTO,
                    networkKey = networkKey,
                    scanning = true
                )
            }
        }

        iranScanner.execute {
            var rescanAfterStalePolicy = false
            try {
                val detected = runCatching {
                    iranDetector.detect(
                        policy = IranModePolicy.AUTO,
                        tunnelActive = state != "DISCONNECTED",
                        deepProbe = deepProbe,
                        previous = previous
                    )
                }.getOrElse {
                    previous.copy(
                        policy = IranModePolicy.AUTO,
                        networkKey = networkKey,
                        scanning = false,
                        lastScanAt = now,
                        summary = "Iran Mode scan failed • ${it::class.java.simpleName}"
                    )
                }

                if (
                    iranPolicyGeneration.get() != policyGeneration ||
                    settings.iranModePolicy != IranModePolicy.AUTO
                ) {
                    diagnostics.event(
                        "IRAN",
                        "stale-scan-discarded",
                        "startedPolicy" to IranModePolicy.AUTO.name,
                        "currentPolicy" to settings.iranModePolicy.name
                    )
                    rescanAfterStalePolicy = true
                    return@execute
                }

                val next = when {
                    !detected.active -> detected
                    !settings.iranModeCountermeasures -> detected.copy(
                        countermeasures = listOf(
                            "Detection only • countermeasures are switched off in settings"
                        )
                    )
                    !settings.iranDomesticDirect -> detected.copy(
                        countermeasures = detected.countermeasures
                            .filterNot { it.startsWith("Domestic") }
                    )
                    else -> detected
                }

                intelligence.setIranModeState(next, geoIpReady())
                postToMain {
                    if (
                        settings.iranModePolicy == IranModePolicy.AUTO &&
                        iranPolicyGeneration.get() == policyGeneration
                    ) {
                        iranMode = next.copy(scanning = false)
                        refreshIntelligenceStatus()
                    }
                }
            } finally {
                iranScanInFlight.set(false)
                if (rescanAfterStalePolicy) {
                    scanIranMode(force = true, deep = false)
                }
            }
        }
    }

    fun setIranModePolicy(policy: IranModePolicy) {
        if (settings.iranModePolicy == policy) return
        iranPolicyGeneration.incrementAndGet()
        updateSettings(settings.copy(iranModePolicy = policy))

        message = when (policy) {
            IranModePolicy.AUTO -> "Iran Mode set to automatic ISP detection"
            IranModePolicy.ALWAYS_ON -> "Iran Mode forced on • scanning disabled"
            IranModePolicy.OFF -> "Iran Mode disabled"
        }

        scanIranMode(
            force = true,
            deep = policy == IranModePolicy.AUTO && settings.iranDeepProbeEnabled
        )
    }

private fun postToMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(block)
        }
    }

    private fun calculateLiveRouteScore(
        pingMs: Int,
        jitterMs: Int,
        successPercent: Int,
        attemptCount: Int,
        tailLatencyMs: Int
    ): Int {
        val attempts = attemptCount.coerceIn(0, 12)
        val confidence = (attempts / 8.0).coerceIn(0.15, 1.0)
        val observedReliability = successPercent.coerceIn(0, 100).toDouble()
        val reliability = observedReliability * confidence + 72.0 * (1.0 - confidence)
        val latency = 100.0 * exp(-pingMs.coerceIn(1, 10_000) / 260.0)
        val variation = if (jitterMs < 0) {
            65.0
        } else {
            100.0 * exp(-jitterMs.coerceIn(0, 2_000) / 45.0)
        }
        val tail = if (tailLatencyMs < 0) {
            65.0
        } else {
            val excess = (tailLatencyMs - pingMs).coerceAtLeast(0)
            100.0 * exp(-excess.coerceAtMost(5_000) / 100.0)
        }
        return (
            latency * 0.48 +
                variation * 0.22 +
                reliability * 0.24 +
                tail * 0.06
            ).roundToInt().coerceIn(0, 100)
    }

    fun recoveryCandidates(failedIds: Set<String>): List<ProxyProfile> =
        intelligence.recoveryCandidates(enabledProfilesSnapshot(), failedIds, settings)

    fun refreshIntelligenceStatus() {
        if (!statusRefreshInFlight.compareAndSet(false, true)) return
        val settingsSnapshot = settings
        statusWorker.execute {
            val next = runCatching { intelligence.status(settingsSnapshot) }.getOrNull()
            statusRefreshInFlight.set(false)
            if (next != null) postToMain { intelligenceStatus = next }
        }
    }

    fun updateSentinel(value: PrivacySentinelState) {
        postToMain { sentinel = value }
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

    private fun randomSourceName(): String {
        val first = listOf("Nova", "Orbit", "Aurora", "Pulse", "Nebula", "Comet", "Vector", "Marble")
        val second = listOf("Nest", "Vault", "Dock", "Link", "Hub", "Lab", "Cloud", "Box")
        repeat(64) {
            val suffix = java.util.UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
            val candidate = "${first.random()} ${second.random()} $suffix"
            if (subscriptions.none { it.name.equals(candidate, true) }) return candidate
        }
        return "Local ${System.currentTimeMillis().toString(36).takeLast(6).uppercase()}"
    }

    fun addSubscription(name: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isNotBlank() && !isHttpsSubscriptionUrl(cleanUrl)) {
            message = "Remote subscriptions must use HTTPS • leave URL empty for a local source"
            return
        }
        if (cleanUrl.isNotBlank()) {
            val duplicate = subscriptions.firstOrNull {
                it.url.isNotBlank() && it.url.trim().equals(cleanUrl, true)
            }
            if (duplicate != null) {
                message = "Subscription already exists • ${duplicate.name}"
                return
            }
        }
        val sourceName = name.trim().ifBlank { randomSourceName() }
        val seed = cleanUrl.ifBlank { "local:${System.nanoTime()}:${java.util.UUID.randomUUID()}" }
        val baseId = sha(seed).take(12)
        var id = baseId
        var suffix = 1
        while (subscriptions.any { it.id == id }) id = "${baseId.take(9)}-${suffix++}"
        subscriptions += Subscription(
            id = id,
            name = sourceName,
            url = cleanUrl,
            updatedAt = if (cleanUrl.isBlank()) System.currentTimeMillis() else 0L
        )
        store.saveSubscriptions(subscriptions)
        if (cleanUrl.isBlank()) {
            message = "Local source created • $sourceName"
        } else {
            message = "Subscription added • refreshing source"
            refresh(id)
        }
    }

    /**
     * Replace provider-managed rows atomically while preserving the live config snapshot.
     *
     * A provider may remove/rename the node currently carrying traffic. The running service owns a
     * valid immutable profile snapshot; Repository must not pretend that row vanished until the
     * active tunnel is disconnected.
     */
    private fun replaceManagedProfilesForSource(
        sub: Subscription,
        parsed: List<ProxyProfile>
    ): Int {
        val userOwnedIds = profiles.asSequence()
            .filter { it.subscriptionId == sub.id && !it.sourceManaged }
            .mapTo(mutableSetOf()) { it.id }

        val activeSnapshot = profiles.firstOrNull { current ->
            state == "CONNECTED" &&
                current.sourceManaged &&
                current.subscriptionId == sub.id &&
                current.id == activeProfileId &&
                (activeProfileSourceId.isBlank() || activeProfileSourceId == sub.id)
        }

        val incoming = parsed.asSequence()
            .filterNot { it.id in userOwnedIds }
            .map { it.copy(sourceManaged = true) }
            .toList()

        profiles.removeAll { it.subscriptionId == sub.id && it.sourceManaged }
        profiles.addAll(incoming)

        if (activeSnapshot != null && profiles.none {
                it.id == activeSnapshot.id && it.subscriptionId == activeSnapshot.subscriptionId
            }) {
            profiles += activeSnapshot
            diagnostics.event(
                "LIBRARY",
                "active-profile-preserved-on-refresh",
                "profile" to activeSnapshot.id.take(12),
                "source" to sub.id.take(16)
            )
        }
        return incoming.size
    }

    fun refresh(id: String) {
        val sub = subscriptions.firstOrNull { it.id == id } ?: return
        if (state == "CONNECTING" || state == "BLOCKED") {
            message = "Wait until the connection is stable or disconnected before refreshing"
            return
        }
        if (sub.url.isBlank()) {
            message = "${sub.name} is a local source • add Manual/SSH nodes into it"
            return
        }
        task("Refreshing ${sub.name}") {
            beginRefresh(listOf(sub.id))
            val payload = httpSubscription(sub.url)
            val parsed = ProxyParser.parseInput(payload.text, sub.id, sub.name)
            require(parsed.isNotEmpty()) {
                "No supported profiles returned; previous nodes were kept"
            }

            val refreshedCount = replaceManagedProfilesForSource(sub, parsed)

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
                "${sub.name} • $refreshedCount nodes",
                settings
            )
            message = "$refreshedCount profiles refreshed"
        }
    }

    fun refreshAll() {
        if (state == "CONNECTING" || state == "BLOCKED") {
            message = "Wait until the connection is stable or disconnected before refreshing"
            return
        }
        val remote = subscriptions.filter { it.url.isNotBlank() }
        if (remote.isEmpty()) {
            message = "No remote subscriptions to refresh • local sources were left untouched"
            return
        }
        task("Refreshing subscriptions") {
            var refreshed = 0
            var nodeCount = 0
            val failed = mutableListOf<String>()
            val pending = remote.toList()
            beginRefresh(pending.map { it.id })
            pending.forEach { sub ->
                val result = runCatching {
                    val payload = httpSubscription(sub.url)
                    val parsed = ProxyParser.parseInput(payload.text, sub.id, sub.name)
                    require(parsed.isNotEmpty()) { "No supported profiles returned; previous nodes were kept" }
                    val refreshedCount = replaceManagedProfilesForSource(sub, parsed)
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
                    refreshedCount
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

    // MARBLE_MANUAL_IMPORT_V20
    fun addManualProfile(
        draft: ManualConfigDraft,
        targetSubscriptionId: String = "manual"
    ): Boolean {
        if (busy) {
            message = "Wait for the current task before adding a manual config"
            return false
        }
        val built = runCatching { ManualConfigBuilder.build(draft) }.getOrElse { error ->
            message = "Manual config invalid • ${error.message ?: error::class.java.simpleName}"
            return false
        }
        val target = resolveLibraryTarget(targetSubscriptionId)
        if (target == null) {
            message = if (targetSubscriptionId == "manual" && !settings.manualSourceEnabled) {
                "Manual source is disabled • enable it in Settings → Subscriptions or select another source"
            } else {
                "Select one source in Library before adding a manual config"
            }
            return false
        }
        val stored = built.copy(
            subscriptionId = target.id,
            subscriptionName = target.name,
            sourceManaged = false
        )
        if (profiles.any { it.id == stored.id && it.subscriptionId == stored.subscriptionId }) {
            message = "Config already exists in ${target.name}"
            return false
        }
        profiles += stored
        store.saveProfiles(profiles)
        diagnostics.event(
            "LIBRARY",
            "manual-profile-added",
            "profile" to stored.id.take(12),
            "protocol" to stored.scheme,
            "transport" to stored.transport,
            "security" to stored.security,
            "source" to stored.subscriptionId.take(16)
        )
        message = "${stored.scheme.uppercase()} added • ${stored.name}"
        return true
    }

    /** Persist an independent Manual multi-hop profile; creating another chain never replaces it. */
    fun addManualChain(
        requestedName: String,
        hopRefs: List<Pair<String, String>>,
        targetSubscriptionId: String = "manual"
    ): Boolean {
        if (busy) {
            message = "Wait for the current task before adding a chain"
            return false
        }
        val target = resolveLibraryTarget(targetSubscriptionId) ?: run {
            message = "Select one Library source before saving the chain"
            return false
        }
        val hops = hopRefs.mapNotNull { (sourceId, profileId) -> profile(profileId, sourceId) }
        if (hops.size != hopRefs.size || hops.size < 2) {
            message = "A chain needs at least two available hops"
            return false
        }
        if (hops.any { it.scheme.equals("ssh", true) }) {
            message = "SSH cannot be embedded in a persisted Xray chain"
            return false
        }

        val config = runCatching {
            XrayConfigHardener.composeChain(hops.map { it.configJson })
        }.getOrElse { error ->
            message = "Chain invalid • ${error.message ?: error::class.java.simpleName}"
            return false
        }
        val exit = hops.last()
        val name = requestedName.trim().ifBlank { "Chain • ${hops.size} hops" }.take(120)
        val id = sha("chain:${System.nanoTime()}:${hopRefs.joinToString { "${it.first}:${it.second}" }}").take(16)
        val stored = ProxyProfile(
            id = id,
            name = name,
            scheme = "chain",
            raw = "chain://${hopRefs.joinToString(",") { it.second }}",
            configJson = config,
            host = exit.host,
            port = exit.port,
            transport = "chain-${hops.size}",
            security = "multi-hop",
            subscriptionId = target.id,
            subscriptionName = target.name,
            sourceManaged = false
        )
        profiles += stored
        store.saveProfiles(profiles)
        diagnostics.event(
            "LIBRARY",
            "manual-chain-added",
            "profile" to stored.id.take(12),
            "hops" to hops.size,
            "source" to target.id.take(16)
        )
        message = "${hops.size}-hop chain saved • $name"
        return true
    }
    fun importText(
        text: String,
        name: String = "Manual",
        targetSubscriptionId: String = "manual"
    ) {
        val target = resolveLibraryTarget(targetSubscriptionId)
        if (target == null) {
            message = if (targetSubscriptionId == "manual" && !settings.manualSourceEnabled) {
                "Manual source is disabled • enable it in Settings → Subscriptions or select another source"
            } else {
                "Select one source in Library before importing configs"
            }
            return
        }
        task("Importing into ${target.name}") {
            val parsed = ProxyParser.parseInput(text, target.id, target.name)
                .map {
                    it.copy(
                        subscriptionId = target.id,
                        subscriptionName = target.name,
                        sourceManaged = false
                    )
                }
            val fresh = parsed.filter { incoming ->
                profiles.none {
                    it.id == incoming.id && it.subscriptionId == target.id
                }
            }
            profiles.addAll(fresh)
            store.saveProfiles(profiles)
            message = "${fresh.size} profile${if (fresh.size == 1) "" else "s"} imported into ${target.name}"
        }
    }

    /** Smart clipboard intake used by the Library magic button. */
    fun importClipboard(text: String, targetSubscriptionId: String = "manual") {
        val clean = text.trim()
        if (clean.isBlank()) {
            message = "Clipboard is empty"
            return
        }

        val lines = clean.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val looksLikeJson = clean.startsWith("{") || clean.startsWith("[")
        val hasShareLinks = Regex(
            "(?im)^(vless|vmess|trojan|ss|socks5?|hysteria2|hy2|ssh)://"
        ).containsMatchIn(clean)
        val hasAuthenticatedHttpProxy = Regex(
            "(?im)^https?://[^\\s/@]+:[^\\s/@]*@"
        ).containsMatchIn(clean)

        if (looksLikeJson || hasShareLinks || hasAuthenticatedHttpProxy) {
            importText(clean, "Clipboard", targetSubscriptionId)
            return
        }

        val allWebUrls = lines.isNotEmpty() && lines.all {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }
        if (!allWebUrls) {
            importText(clean, "Clipboard", targetSubscriptionId)
            return
        }
        if (lines.any { it.startsWith("http://", ignoreCase = true) }) {
            message = "Remote subscriptions must use HTTPS"
            return
        }

        var added = 0
        lines.distinct().take(32).forEachIndexed { index, rawUrl ->
            val cleanUrl = rawUrl.trim()
            if (subscriptions.any { it.url.trim().equals(cleanUrl, true) }) return@forEachIndexed
            val baseId = sha(cleanUrl).take(12)
            var id = baseId
            var suffix = 1
            while (subscriptions.any { it.id == id }) id = "${baseId.take(9)}-${suffix++}"
            val host = runCatching { URL(cleanUrl).host.removePrefix("www.") }.getOrDefault("")
            subscriptions += Subscription(
                id = id,
                name = host.ifBlank { "Clipboard source ${index + 1}" },
                url = cleanUrl,
                updatedAt = 0L
            )
            added++
        }

        if (added == 0) {
            message = "Clipboard subscriptions are already in the Library"
            return
        }
        store.saveSubscriptions(subscriptions)
        message = "$added clipboard source${if (added == 1) "" else "s"} added • refreshing"
        refreshAll()
    }

    /** Original links (or JSON fallback) for every node owned by one subscription. */
    fun subscriptionRawText(id: String): String =
        profiles.asSequence()
            .filter { it.subscriptionId == id }
            .map { it.raw.trim().ifBlank { it.configJson.trim() } }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")

    /** Save effective Xray JSON for a node. Xray itself still validates again on connect. */
    fun updateProfileJson(
        id: String,
        jsonText: String,
        sourceId: String? = null
    ): Boolean {
        if (busy) {
            message = "Wait for the current background task before editing a node"
            return false
        }
        val target = profile(id, sourceId)
        if (target != null && isActiveProfile(target)) {
            message = "Disconnect this node before editing its Xray JSON"
            return false
        }
        val index = profiles.indexOfFirst {
            it.id == id && (sourceId.isNullOrBlank() || it.subscriptionId == sourceId)
        }
        if (index < 0) {
            message = "Node no longer exists"
            return false
        }
        val normalized = runCatching {
            val root = JSONObject(jsonText)
            require(root.has("outbounds")) { "Xray JSON must contain outbounds" }
            root.toString(2)
        }.getOrElse {
            message = "Invalid Xray JSON • ${it.message ?: it::class.java.simpleName}"
            return false
        }
        val current = profiles[index]
        val newId = sha(normalized).take(16)
        if (profiles.indices.any { other ->
                other != index &&
                    profiles[other].id == newId &&
                    profiles[other].subscriptionId == current.subscriptionId
            }) {
            message = "Edited config would duplicate another node in this source"
            return false
        }

        profiles[index] = current.copy(
            id = newId,
            configJson = normalized,
            // A JSON edit becomes the effective durable source of truth.
            raw = normalized
        )
        benchmarks = benchmarks.filterNot {
            it.profileId == current.id || it.profileId == newId
        }
        intelligence.forgetAcceleration(current.id)
        intelligence.forgetAcceleration(newId)

        val remembered = lastProfile()
        if (remembered?.id == current.id &&
            remembered.subscriptionId == current.subscriptionId) {
            store.setLastProfileRef(newId, current.subscriptionId)
        }

        store.saveProfiles(profiles)
        message = "Node JSON saved • identity and learned acceleration refreshed"
        return true
    }

    /** Make a durable Manual copy of any node, including subscription-owned nodes. */
    fun duplicateProfile(id: String, sourceId: String? = null): Boolean {
        if (busy) {
            message = "Wait for the current background task before duplicating a node"
            return false
        }
        if (!settings.manualSourceEnabled) {
            message = "Manual source is disabled • enable it in Settings → Subscriptions first"
            return false
        }
        val source = profile(id, sourceId) ?: run {
            message = "Node no longer exists"
            return false
        }
        val newId = sha("${source.id}:${System.nanoTime()}:${profiles.size}").take(12)
        var name = "${source.name} • copy"
        var n = 2
        while (profiles.any { it.name.equals(name, true) && it.subscriptionId == "manual" }) {
            name = "${source.name} • copy $n"
            n++
        }
        profiles += source.copy(
            id = newId,
            name = name,
            subscriptionId = "manual",
            subscriptionName = "Manual",
            sourceManaged = false
        )
        store.saveProfiles(profiles)
        message = "Manual copy created • $name"
        return true
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
        val cleanName = name.trim().ifBlank { randomSourceName() }
        if (cleanUrl.isNotBlank() && !isHttpsSubscriptionUrl(cleanUrl)) {
            message = "Remote subscription URL must use HTTPS, or stay empty for a local source"
            return false
        }
        if (cleanUrl.isNotBlank() && subscriptions.any {
                it.id != id && it.url.isNotBlank() && it.url.trim().equals(cleanUrl, true)
            }) {
            message = "Another subscription already uses this URL"
            return false
        }
        subscriptions[index] = subscriptions[index].copy(name = cleanName, url = cleanUrl)
        for (i in profiles.indices) {
            if (profiles[i].subscriptionId == id) profiles[i] = profiles[i].copy(subscriptionName = cleanName)
        }
        store.saveSubscriptions(subscriptions)
        store.saveProfiles(profiles)
        message = if (cleanUrl.isBlank()) "Local source updated • $cleanName" else "Subscription updated • $cleanName"
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
        if (state != "DISCONNECTED") {
            message = "Disconnect before deleting a subscription source"
            return
        }
        val doomedIds = profiles.filter { it.subscriptionId == id }.map { it.id }.toSet()
        val remembered = lastProfile()
        if (remembered != null && remembered.subscriptionId == id) {
            store.clearLastProfile()
        }
        doomedIds.forEach(intelligence::forgetAcceleration)
        if (librarySourceFilter == id) {
            selectLibrarySource("all")
        }
        subscriptions.removeAll { it.id == id }
        profiles.removeAll { it.subscriptionId == id }
        benchmarks = benchmarks.filterNot { it.profileId in doomedIds }
        store.saveSubscriptions(subscriptions)
        store.saveProfiles(profiles)
        message = "Removed ${sub.name} • ${doomedIds.size} nodes deleted"
    }

    fun removeProfile(id: String, sourceId: String? = null) {
        if (busy) {
            message = "Wait for the current task before deleting a node"
            return
        }
        if (state != "DISCONNECTED") {
            message = "Disconnect before deleting Library nodes"
            return
        }

        val index = profiles.indexOfFirst {
            it.id == id && (sourceId.isNullOrBlank() || it.subscriptionId == sourceId)
        }
        if (index < 0) {
            message = "Node no longer exists"
            return
        }

        val target = profiles.removeAt(index)
        val sameConfigRemains = profiles.any { it.id == target.id }

        if (!sameConfigRemains) {
            benchmarks = benchmarks.filterNot { it.profileId == target.id }
            intelligence.forgetAcceleration(target.id)
        }

        val remembered = lastProfile()
        if (remembered?.id == target.id &&
            remembered.subscriptionId == target.subscriptionId) {
            profiles.firstOrNull { it.id == target.id }?.let { surviving ->
                store.setLastProfileRef(surviving.id, surviving.subscriptionId)
            } ?: store.clearLastProfile()
        }

        store.saveProfiles(profiles)
        message = "Node removed • ${target.name}"
    }

    fun renameProfile(id: String, name: String, sourceId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val idx = profiles.indexOfFirst {
            it.id == id && (sourceId.isNullOrBlank() || it.subscriptionId == sourceId)
        }
        if (idx < 0) return
        profiles[idx] = profiles[idx].copy(name = trimmed)
        store.saveProfiles(profiles)
    }

    fun subscriptionNodeCount(id: String): Int = profiles.count { it.subscriptionId == id }

    /**
     * Count nodes in one subscription whose most recent stored benchmark explicitly failed
     * the requested evidence type. TCP and TUNNEL stay separate.
     */
    fun failedSubscriptionNodeCount(id: String, probeKind: String): Int {
        val kind = probeKind.trim().uppercase()
        if (kind !in setOf("TCP", "TUNNEL")) return 0
        val failedIds = benchmarks.asSequence()
            .filter { it.success <= 0 && it.probeKind.equals(kind, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.profileId }
        return profiles.count { it.subscriptionId == id && it.id in failedIds }
    }

    /**
     * Remove only failed nodes from ONE subscription and ONE evidence type.
     * Group cleanup is disabled while connected/connecting so stale evidence cannot delete
     * the route Android is currently using.
     */
    fun removeFailedSubscriptionNodes(id: String, probeKind: String): Int {
        if (busy) {
            message = "Wait for the current task before removing failed nodes"
            return 0
        }
        if (state != "DISCONNECTED") {
            message = "Disconnect before removing failed nodes from a subscription"
            return 0
        }

        val sub = subscriptions.firstOrNull { it.id == id } ?: run {
            message = "Subscription no longer exists"
            return 0
        }
        val kind = probeKind.trim().uppercase()
        if (kind !in setOf("TCP", "TUNNEL")) {
            message = "Unsupported failed-node evidence type"
            return 0
        }

        val failedIds = benchmarks.asSequence()
            .filter { it.success <= 0 && it.probeKind.equals(kind, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.profileId }

        val doomedIds = profiles.asSequence()
            .filter { it.subscriptionId == id && it.id in failedIds }
            .mapTo(linkedSetOf()) { it.id }

        if (doomedIds.isEmpty()) {
            message = "No failed $kind nodes recorded for ${sub.name}"
            return 0
        }

        val remembered = lastProfile()
        if (remembered != null &&
            remembered.subscriptionId == id &&
            remembered.id in doomedIds) {
            store.clearLastProfile()
        }
        doomedIds.forEach(intelligence::forgetAcceleration)
        profiles.removeAll { it.id in doomedIds }
        benchmarks = benchmarks.filterNot { it.profileId in doomedIds }
        store.saveProfiles(profiles)

        diagnostics.event(
            "LIBRARY",
            "failed-nodes-removed",
            "source" to sub.id.take(16),
            "sourceName" to sub.name,
            "probeKind" to kind,
            "removed" to doomedIds.size
        )
        message = "Removed ${doomedIds.size} failed $kind node${if (doomedIds.size == 1) "" else "s"} from ${sub.name}"
        return doomedIds.size
    }

    /** Reassigns a profile to another subscription bucket (or "manual") so nodes can move between library sources. */
    fun lastProfile(): ProxyProfile? {
        val id = store.lastProfileId()
        if (id.isBlank()) return null

        val sourceId = store.lastProfileSourceId()
        val exact = profile(id, sourceId)
        return (exact ?: profile(id))?.takeIf(::profileSourceEnabled)
    }

    /** Exact one-tap reconnect for Home after app/process restart. */
    fun reconnectLastOrAuto(onConnect: (ProxyProfile) -> Unit) {
        val remembered = lastProfile()
        if (remembered != null) {
            diagnostics.event(
                "APP",
                "one-tap-reconnect-v37",
                "profile" to remembered.id.take(12),
                "name" to remembered.name.take(80)
            )
            message = "Reconnect • ${remembered.name}"
            onConnect(remembered)
            return
        }
        auto(onConnect)
    }

    fun auto(
        onConnect: (ProxyProfile) -> Unit
    ) {
        val available = enabledProfilesSnapshot()
        if (available.isEmpty()) {
            message = "No enabled nodes • select a source or enable Manual source"
            return
        }
        val remembered = lastProfile()
        val measured = benchmarks
            .asSequence()
            .filter { it.success > 0 }
            .sortedWith(
                compareByDescending<BenchmarkResult> { it.probeKind == "TUNNEL" }
                    .thenByDescending { it.score }
                    .thenBy { it.latencyMs }
            )
            .mapNotNull { result -> available.firstOrNull { it.id == result.profileId } }
            .firstOrNull()
        val rememberedResult = remembered?.let { p -> benchmarks.firstOrNull { it.profileId == p.id } }
        val rememberedUsable = remembered != null && (rememberedResult == null || rememberedResult.success > 0)
        val candidate = when {
            rememberedUsable -> remembered
            measured != null -> measured
            else -> available.first()
        } ?: available.first()
        diagnostics.event(
            "APP",
            "fast-connect-v25",
            "profile" to candidate.id.take(12),
            "remembered" to (candidate.id == remembered?.id),
            "measured" to (candidate.id == measured?.id),
            "taskBusy" to busy
        )
        message = "Fast connect • ${candidate.name}"
        onConnect(candidate)
    }

    fun markConnected(p: ProxyProfile) {
        postToMain {
            val previousState = state
            val settingsSnapshot = settings

            state = "CONNECTED"
            stateDetail = p.name
            activeProfileId = p.id
            activeProfileSourceId = p.subscriptionId

            history += ConnectionRecord(
                p.id,
                p.name,
                System.currentTimeMillis(),
                "connected:${settingsSnapshot.connectionMode.name}"
            )
            while (history.size > MAX_HISTORY_RECORDS) history.removeAt(0)

            // Serialize/persist outside MainActivity. SharedPreferences.apply() is asynchronous, but
            // building the 200-record JSON string on the input thread was not.
            val historySnapshot = history.toList()
            io.execute {
                // MARBLE_LAST_ROUTE_V37
                // Successful connection is durable user intent.
                runCatching { store.setLastProfileRef(p.id, p.subscriptionId) }
                runCatching {
                    store.saveHistory(historySnapshot)
                }.onFailure {
                    message = "Connected • history persistence skipped"
                }
            }

            diagnostics.event(
                "APP",
                "state",
                "from" to previousState,
                "to" to "CONNECTED",
                "detail" to p.name.take(160)
            )
            MarbleQuickTileService.requestRefresh(context)
        }
    }

    fun startVpn(p: ProxyProfile) {
        privacy = null
        runCatching { scanIranMode() }
        setRuntimeState("CONNECTING", p.name)
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_START)
            .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
            .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, p.subscriptionId)
            .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_TUN)
        launchConnectionService(intent, p.name)
    }

    fun startLocalProxy(p: ProxyProfile) {
        privacy = null
        runCatching { scanIranMode() }
        setRuntimeState("CONNECTING", p.name)
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_START)
            .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
            .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, p.subscriptionId)
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

    /**
     * Asks the running tunnel to re-measure acceleration methods on the active route.
     * A winner is learned for the next reconnect; the live tunnel is never interrupted.
     */
    fun boostActiveRoute() {
        if (state != "CONNECTED") {
            message = "Marble Turbo needs an active connection"
            return
        }
        if (!settings.intelligenceEnabled || !settings.connectTuningEnabled) {
            message = "Marble Turbo is switched off in Settings → Engine"
            return
        }
        runCatching {
            context.startService(
                Intent(context, MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_TUNE)
            )
        }.onSuccess {
            message = "Marble Turbo • learning a faster method without interrupting this route"
        }.onFailure { error ->
            message = "Could not start Marble Turbo: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
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
    private fun clearBenchmarks(ids: Set<String>) = postToMain {
        benchmarks = benchmarks.filterNot { it.profileId in ids }
    }

    private fun mergeBenchmarks(fresh: List<BenchmarkResult>) {
        if (fresh.isEmpty()) return
        val incoming = fresh.toList()
        postToMain {
            val freshIds = incoming.mapTo(mutableSetOf()) { it.profileId }
            val liveIds = profiles.mapTo(mutableSetOf()) { it.id }
            benchmarks = (incoming + benchmarks.filterNot { it.profileId in freshIds })
                .distinctBy { it.profileId }
                .filter { it.profileId in liveIds || it.profileId in freshIds }
                .sortedWith(
                    compareByDescending<BenchmarkResult> { it.score }
                        .thenBy { it.latencyMs }
                )
        }
    }

    fun smart(onBest: (ProxyProfile) -> Unit) {
        task("Marble Intelligence • selecting route") {
            val available = enabledProfilesSnapshot()
            if (available.isEmpty()) {
                message = "No enabled nodes to test"
                return@task
            }
            val engine = BenchmarkEngine(xray, intelligence)
            if (settings.intelligenceEnabled && settings.raceConnectEnabled && available.size > 1) {
                val raced = engine.race(
                    available,
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
                available,
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

    fun smartRank() = smartRankSource("all")

    /**
     * Real Xray ranking confined to the exact Library source selected by the user.
     * Unknown source ids fail closed to zero candidates instead of falling back to all profiles.
     */
    fun smartRankSource(sourceId: String) {
        val candidates = libraryScopeSnapshot(sourceId).distinctBy { it.id }
        val scope = libraryScopeLabel(sourceId)
        if (candidates.isEmpty()) {
            message = "Nothing enabled to rank in $scope"
            return
        }

        task("Smart rank • $scope") {
            val scoped = candidates
            clearBenchmarks(scoped.mapTo(mutableSetOf()) { it.id })
            val configuredRankMethod = when (settings.probeMethod) {
                ProbeMethod.TUNNEL -> ProbeMethod.TUNNEL
                else -> ProbeMethod.HYBRID
            }
            val rankSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = scoped.size.coerceAtLeast(1),
                // Three bounded application RTT samples share one verified tunnel/TLS
                // session. The first verified response is health evidence, not a discarded warm-up.
                // v2rayNG real delay: two GET attempts, 12 s ceiling, minimum valid result.
                benchSamples = 2,
                benchTimeoutSec = settings.benchTimeoutSec.coerceIn(6, 12),
                tcpPrecheckTimeoutMs = settings.tcpPrecheckTimeoutMs.coerceIn(750, 1_500),
                tcpWorkers = settings.tcpWorkers.coerceIn(4, 8),
                probeMethod = configuredRankMethod,
                probeSpeedTest = false,
                verifiedPerformanceTuning = false,
                udpProbeEnabled = false
            )
            fun executeRank(): List<BenchmarkResult> = BenchmarkEngine(xray, intelligence).run(
                scoped,
                rankSettings,
                usePrecheck = configuredRankMethod == ProbeMethod.HYBRID,
                v2rayStyleDelay = true,
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult
            ) { done, total, name ->
                message = "Tunnel rank • $scope • $done/$total • $name"
            }

            val startedNetwork = intelligence.currentSnapshot().key()
            var results = executeRank()
            val finishedNetwork = intelligence.currentSnapshot().key()
            if (finishedNetwork != startedNetwork) {
                diagnostics.event(
                    "BENCHMARK",
                    "rank-network-changed-retry",
                    "from" to startedNetwork,
                    "to" to finishedNetwork
                )
                message = "Physical network changed • restarting tunnel Rank once"
                Thread.sleep(750L)
                results = executeRank()
            }
            mergeBenchmarks(results)

            val healthy = results.count { it.success > 0 }
            val best = results.firstOrNull { it.success > 0 }
            results.filter { it.success <= 0 }.forEach { failed ->
                diagnostics.event(
                    "BENCHMARK",
                    "rank-node-failed",
                    "profile" to failed.profileId.take(24),
                    "name" to failed.name.take(48),
                    "stage" to failed.failureReason.take(180)
                )
            }
            diagnostics.event(
                "BENCHMARK",
                "smart-rank-source-finish",
                "source" to sourceId.take(24),
                "scope" to scope,
                "requested" to scoped.size,
                "tested" to results.size,
                "healthy" to healthy,
                "method" to configuredRankMethod.name,
                "engine" to "isolated-xray-real-delay-v45"
            )
            message = if (best == null) {
                "Tunnel rank • $scope • ${results.size}/${scoped.size} tested • 0 healthy"
            } else {
                "Tunnel rank • $scope • ${results.size}/${scoped.size} tested • $healthy healthy • " +
                    "best ${best.name} • ${best.latencyMs.toInt()} ms • score ${best.score.toInt()}"
            }
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

    /**
     * Fast Library-wide TCP latency sweep.
     * Full test and Smart Xray rank remain available when actual proxy usability must be proven.
     */
    fun testAll() = testSource("all")

    /**
     * Fast TCP ping confined to the exact selected Library source.
     * Smart Rank remains the real Xray tunnel verifier.
     */
    private fun quickPingEndpointKey(profile: ProxyProfile): String =
        "${profile.host.trim().lowercase()}:${profile.port}"

    /**
     * Fast TCP ping confined to the selected Library source.
     *
     * TCP reachability is a host:port property. Aggregator subscriptions often contain many
     * configs pointing at the same endpoint, so v33 probes one representative per endpoint and
     * fans the verified result back to every matching card. TUNNEL rank remains per-config.
     */
    fun testSource(sourceId: String) {
        val scoped = libraryScopeSnapshot(sourceId).distinctBy { it.id }
        val scope = libraryScopeLabel(sourceId)
        if (scoped.isEmpty()) {
            message = "Nothing enabled to ping in $scope"
            return
        }

        task("Quick ping • $scope") {
            val groups = scoped.groupBy(::quickPingEndpointKey)
            val representatives = groups.values.mapNotNull { it.firstOrNull() }
            val quickSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = representatives.size.coerceAtLeast(1),
                benchSamples = 1,
                benchTimeoutSec = 2,
                tcpPrecheckTimeoutMs = minOf(settings.tcpPrecheckTimeoutMs, 750),
                tcpWorkers = maxOf(settings.tcpWorkers, 24).coerceAtMost(32),
                probeMethod = ProbeMethod.TCP,
                probeSpeedTest = false,
                verifiedPerformanceTuning = false,
                udpProbeEnabled = false
            )

            fun membersFor(representative: ProxyProfile): List<ProxyProfile> =
                groups[quickPingEndpointKey(representative)].orEmpty()

            fun runQuickPass(): List<BenchmarkResult> =
                BenchmarkEngine(xray, intelligence).run(
                    representatives,
                    quickSettings,
                    usePrecheck = false,
                    onCandidates = { beginProbeBatch(scoped) },
                    onStart = { representative ->
                        membersFor(representative).forEach(::markProbeStart)
                    },
                    onResult = { representative, result ->
                        membersFor(representative).forEach { member ->
                            markProbeResult(
                                member,
                                result.copy(profileId = member.id, name = member.name)
                            )
                        }
                    }
                ) { done, total, name ->
                    message = "TCP ping • $scope • $done/$total endpoints • $name"
                }

            val firstStartedNs = System.nanoTime()
            var representativeResults = runQuickPass()
            val firstElapsedMs =
                ((System.nanoTime() - firstStartedNs) / 1_000_000L).coerceAtLeast(0L)

            if (
                representatives.size >= 4 &&
                representativeResults.isNotEmpty() &&
                representativeResults.none { it.success > 0 } &&
                firstElapsedMs < 350L
            ) {
                diagnostics.event(
                    "BENCHMARK",
                    "quick-ping-fast-zero-retry",
                    "source" to sourceId.take(24),
                    "scope" to scope,
                    "nodes" to scoped.size,
                    "endpoints" to representatives.size,
                    "firstElapsedMs" to firstElapsedMs
                )
                try {
                    Thread.sleep(120L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (!Thread.currentThread().isInterrupted) {
                    representativeResults = runQuickPass()
                }
            }

            val expanded = representativeResults.flatMap { result ->
                val representative = representatives.firstOrNull { it.id == result.profileId }
                if (representative == null) {
                    listOf(result)
                } else {
                    membersFor(representative).map { member ->
                        result.copy(profileId = member.id, name = member.name)
                    }
                }
            }

            mergeBenchmarks(expanded)
            val passed = expanded.count { it.success > 0 }
            diagnostics.event(
                "BENCHMARK",
                "quick-ping-source-finish",
                "source" to sourceId.take(24),
                "scope" to scope,
                "requested" to scoped.size,
                "uniqueEndpoints" to representatives.size,
                "tested" to expanded.size,
                "reachable" to passed
            )
            message =
                "Quick ping • $scope • ${expanded.size} nodes / ${representatives.size} endpoints • " +
                    "$passed reachable"
        }
    }

    fun audit() {
        if (state != "CONNECTED" || !xray.isAlive) {
            privacy = null
            message = "Privacy audit needs an active healthy Xray connection"
            return
        }
        privacy = null
        task("Privacy audit • comparing proxy/physical egress and tunnel DNS") {
            privacy = PrivacyAuditor.audit(
                activeProxyPort(),
                intelligence.currentUnderlyingNetwork()
            )
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
        val candidate = lastProfile() ?: enabledProfilesSnapshot().firstOrNull()
        if (candidate == null) {
            message = "Import at least one profile before verifying routing"
            return
        }
        if (state != "DISCONNECTED" && (settings.geoIpUrl.isNotBlank() || settings.geoSiteUrl.isNotBlank())) {
            message = "Disconnect before routing verification when remote geo data URLs are configured"
            return
        }
        task("Verifying routing policy with Xray") {
            message = xray.verifyRoutingPolicy(candidate, settings)
        }
    }

    fun runBugFinder() {
        task("Bug Finder Ultimate • collecting passive runtime evidence") {
            diagnostics.event("BUGFINDER", "scan-begin", "state" to state, "debugMode" to settings.debugModeEnabled)
            val report = bugFinder.scan(
                appState = state,
                stateDetail = stateDetail,
                activeProfileId = activeProfileId,
                settings = settings,
                profiles = profiles.toList(),
                history = history.toList(),
                networkLabel = networkSnapshot.label,
                sentinel = sentinel,
                privacy = privacy
            )
            if (settings.debugModeEnabled) diagnostics.exportReport("bugfinder-auto", report.asText())
            diagnostics.event("BUGFINDER", "scan-finish", "failures" to report.failures, "warnings" to report.warnings, "autoExport" to settings.debugModeEnabled)
            postToMain {
                bugReport = report
                message = if (settings.debugModeEnabled) {
                    "Bug Finder • ${report.headline} • TXT queued in ${RuntimeDiagnostics.reportFolderLabel()}"
                } else "Bug Finder • ${report.headline}"
            }
        }
    }

    fun setDebugMode(enabled: Boolean) {
        updateSettings(settings.copy(debugModeEnabled = enabled))
        message = if (enabled) {
            "Debug Mode ON • async TXT stream → ${RuntimeDiagnostics.reportFolderLabel()}"
        } else "Debug Mode OFF • connection fast path unchanged"
    }

    fun debugReportLocation(): String = RuntimeDiagnostics.reportFolderLabel()

    fun bugFinderReportText(): String = bugReport?.asText() ?: "Run Bug Finder first"

    fun saveBugFinderReport() {
        val report = bugReport ?: run { message = "Run Bug Finder first"; return }
        val accepted = diagnostics.exportReport("bugfinder-manual", report.asText())
        message = if (accepted) {
            "Bug Finder TXT queued → ${RuntimeDiagnostics.reportFolderLabel()}"
        } else "Diagnostics queue is full • report was not allowed to slow the app"
    }

    fun safeRuntimeResetFromBugFinder() {
        bugReport = null
        if (state in setOf("CONNECTED", "CONNECTING", "BLOCKED")) {
            stopVpn()
            message = "Bug Finder • safe runtime reset requested"
        } else {
            xray.stop()
            resetTelemetry()
            setRuntimeState("DISCONNECTED", "")
            message = "Bug Finder • stale runtime state cleared"
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

    fun setRuntimeMessage(value: String) { message = value }
    fun clearMessage() { message = "" }
    fun readLogs(): String = RuntimeDiagnostics(context).bundle(xray.logFile)

    private fun task(label: String, block: () -> Unit) {
        if (!taskInFlight.compareAndSet(false, true)) {
            message = "MarbleNG is busy • finish the current task before starting another"
            diagnostics.event("APP", "task-rejected-busy", "label" to label)
            return
        }

        postToMain {
            busy = true
            message = label
        }
        diagnostics.event("APP", "task-start", "label" to label)

        io.execute {
            try {
                block()
            } catch (t: Throwable) {
                diagnostics.error("APP", "task-failed", t, "label" to label)
                message = "${t::class.simpleName}: ${t.message}"
            } finally {
                diagnostics.event("APP", "task-finish", "label" to label)
                taskInFlight.set(false)
                // No card may be left spinning if a batch aborts.
                endProbeBatch()
                postToMain {
                    busy = false
                    refreshingSources = emptySet()
                }
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

    private fun isHttpsSubscriptionUrl(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)

    private fun readBoundedUtf8(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue

            total += read
            require(total <= maxBytes) {
                "Subscription exceeds ${maxBytes / 1024 / 1024} MiB"
            }
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun httpSubscription(url: String): SubscriptionPayload {
        require(isHttpsSubscriptionUrl(url)) {
            "Remote subscriptions must use HTTPS"
        }

        if (state != "DISCONNECTED") {
            check(xray.isAlive) {
                "Direct management request blocked while the tunnel is not healthy"
            }

            // Keep management traffic inside the current SOCKS route and bound the payload.
            return SubscriptionPayload(
                SocksHttpClient.getTextUrl(
                    activeProxyPort(),
                    url,
                    maxBytes = MAX_SUBSCRIPTION_BYTES
                )
            )
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MarbleNG/3 Integrity")

        return try {
            val code = connection.responseCode
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Subscription redirect left HTTPS"
            }
            require(code in 200..299) { "HTTPS $code" }

            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= MAX_SUBSCRIPTION_BYTES.toLong()) {
                "Subscription exceeds ${MAX_SUBSCRIPTION_BYTES / 1024 / 1024} MiB"
            }

            val userInfo = connection.getHeaderField("subscription-userinfo")
                ?: connection.getHeaderField("Subscription-Userinfo")
                ?: ""

            SubscriptionPayload(
                text = connection.inputStream.use {
                    readBoundedUtf8(it, MAX_SUBSCRIPTION_BYTES)
                },
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

        /** Remote subscription payload ceiling for both direct and SOCKS management paths. */
        const val MAX_SUBSCRIPTION_BYTES = 8 * 1024 * 1024
    }
}
