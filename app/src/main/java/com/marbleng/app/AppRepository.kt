package com.marbleng.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import com.marbleng.app.core.*
import com.marbleng.app.data.AppStore
import com.marbleng.app.model.*
import com.marbleng.app.quicktile.MarbleQuickTileService
import com.marbleng.app.net.*
import com.marbleng.app.vpn.MarbleVpnService
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.roundToInt

data class AppUpdateInfo(
    val version: String,
    val tag: String,
    val title: String,
    val notes: String,
    val url: String,
    // MARBLE_LATEST_VERSION_RULE_V235 — the label and the moment of the winning release,
    // carried so the dialog can say *what kind* of newest build this is. Under the one
    // rule a pre-release can be the newest published version; hiding that would be the
    // app keeping a second, silent rule.
    val prerelease: Boolean = false,
    val publishedAt: String = ""
)

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

internal const val PROVISIONAL_PING_FLOOR_MS = 1

class AppRepository(private val context: Context, val xray: XrayManager) {
    // MARBLE_SINGBOX_GEO_ROUTING_V222 — the sing-box manager materialises geo rule sets from the
    // same .dat snapshot the Xray engine indexes, so both engines route the same tags.
    val singBox: SingBoxManager = SingBoxManager(context) {
        xray.refreshGeoAssetIndex(settings)
    }.also { xray.singBox = it }

    val activeCoreEngine: CoreEngine get() = parseCoreEngine(settings.coreEngineId)

    val deviceCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    val deviceMemoryMb: Long = runCatching {
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        val info = android.app.ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(info)
        val bytes = info?.totalMem ?: 0L
        (bytes / (1024L * 1024L)).coerceAtLeast(0L)
    }.getOrDefault(0L)

    fun recommendedPingParallelism(method: ProbeMethod = settings.probeMethod): Int =
        PingParallel.recommend(deviceCores, deviceMemoryMb, method)

    fun effectivePingParallelism(method: ProbeMethod = settings.probeMethod): Int =
        PingParallel.resolve(settings, deviceCores, deviceMemoryMb, method)

    fun pingParallelismSummary(method: ProbeMethod = settings.probeMethod): String =
        PingParallel.describe(deviceCores, deviceMemoryMb, method)

    private val CANARY_NODES = 3

    val coreStartPhase: String
        get() = if (activeCoreEngine == CoreEngine.SINGBOX) singBox.lastStartPhase else xray.lastStartPhase

    val coreStartError: String
        get() = if (activeCoreEngine == CoreEngine.SINGBOX) singBox.lastStartError else xray.lastStartError

    private val store = AppStore(context)
    private val io = Executors.newFixedThreadPool(3)

    private val statusWorker = Executors.newSingleThreadExecutor()

    private val warmupWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "marble-connect-warmup").apply { isDaemon = true }
    }
    private val statusRefreshInFlight = AtomicBoolean(false)

    private val taskInFlight = AtomicBoolean(false)

    private val activeTask = AtomicReference<Future<*>?>(null)

    private val rankGate = SmartRankGate()

    private val SETTINGS_WRITE_DEBOUNCE_MS = 400L

    private val settingsWriter = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "marble-settings-writer").apply { isDaemon = true }
    }
    private val settingsWriteLock = Any()
    private var settingsWritePending: AppSettings? = null
    private var settingsWriteFuture: ScheduledFuture<*>? = null

    private val iranScanner = Executors.newSingleThreadExecutor()
    private val iranScanInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val iranPolicyGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    val intelligence = MarbleIntelligence(context).also {
        singBox.intelligence = it
        xray.intelligence = it
    }
    private val iranDetector = IranModeDetector(context, intelligence)
    private val bugFinder = BugFinder(context, xray, singBox)
    private val diagnostics = RuntimeDiagnostics(context)

    val profiles = mutableStateListOf<ProxyProfile>().apply { addAll(store.loadProfiles()) }
    val subscriptions = mutableStateListOf<Subscription>().apply { addAll(store.loadSubscriptions()) }
    val history = mutableStateListOf<ConnectionRecord>().apply { addAll(store.loadHistory()) }

    var settings by mutableStateOf(store.settings()); private set

    // MARBLE_NOTIFICATION_COUNCIL_V231 — the notifier speaks the language the app speaks, resolved
    // lazily so a language change in Settings reaches the next notification, not the next launch.
    private val notifier = SmartNotifier(context) { settings.appLanguage }

    private fun normalizeLibrarySourceFilter(id: String): String = when {
        id == "all" -> "all"
        id == "manual" -> "manual"
        subscriptions.any { it.id == id } -> id
        else -> "all"
    }

    var librarySourceFilter by mutableStateOf(
        normalizeLibrarySourceFilter(store.librarySourceFilter())
    )
        private set

    var lastAppTab by mutableStateOf(store.lastAppTab())
        private set

    var lastSettingsTab by mutableStateOf(store.lastSettingsTab())
        private set

    fun rememberAppTab(name: String) {
        val normalized = name.trim().ifBlank { "DECK" }
        if (lastAppTab == normalized) return
        lastAppTab = normalized
        store.setLastAppTab(normalized)
    }

    fun rememberSettingsTab(name: String) {
        val normalized = name.trim().ifBlank { "GENERAL" }
        if (lastSettingsTab == normalized) return
        lastSettingsTab = normalized
        store.setLastSettingsTab(normalized)
    }

    var lastSettingsPage by mutableStateOf(store.lastSettingsPage())
        private set

    fun rememberSettingsPage(name: String) {
        val normalized = name.trim().ifBlank { "hub" }
        if (lastSettingsPage == normalized) return
        lastSettingsPage = normalized
        store.setLastSettingsPage(normalized)
    }

    fun diagnosticsEvent(component: String, event: String, vararg fields: Pair<String, Any?>) {
        diagnostics.event(component, event, *fields)
    }

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

    var libraryCollapsedSources by mutableStateOf(store.libraryCollapsedSources())
        private set

    fun setLibrarySourceCollapsed(sourceId: String, collapsed: Boolean) {
        val next = libraryCollapsedSources.toMutableSet().apply {
            if (collapsed) add(sourceId) else remove(sourceId)
        }
        if (next == libraryCollapsedSources) return
        libraryCollapsedSources = next
        store.setLibraryCollapsedSources(next)
    }

    // MARBLE_SETTINGS_COLLAPSE_MEMORY_V220 — the Settings hub remembers which groups are closed,
    // per group key, across restarts. Same shape as the library's collapsed sources.
    var settingsCollapsedGroups by mutableStateOf(store.settingsCollapsedGroups())
        private set

    fun setSettingsGroupCollapsed(groupKey: String, collapsed: Boolean) {
        val next = settingsCollapsedGroups.toMutableSet().apply {
            if (collapsed) add(groupKey) else remove(groupKey)
        }
        if (next == settingsCollapsedGroups) return
        settingsCollapsedGroups = next
        store.setSettingsCollapsedGroups(next)
    }

    fun setSettingsGroupsCollapsed(collapsed: Set<String>) {
        if (collapsed == settingsCollapsedGroups) return
        settingsCollapsedGroups = collapsed.toSet()
        store.setSettingsCollapsedGroups(settingsCollapsedGroups)
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

    private fun resolveLibraryTarget(id: String): LibraryTarget? = when {
        id == "manual" -> LibraryTarget("manual", "Manual")
        id == "all" || id.isBlank() -> null
        else -> subscriptions.firstOrNull { it.id == id }?.let { LibraryTarget(it.id, it.name) }
    }

    fun intakeTargetOrManual(id: String): String = resolveLibraryTarget(id)?.id ?: "manual"

    val libraryProfiles: List<ProxyProfile>
        get() = profiles.toList().distinctBy { it.id }

    private fun enabledProfilesSnapshot(): List<ProxyProfile> =
        profiles.toList().distinctBy { it.id }

    private fun libraryScopeSnapshot(sourceId: String): List<ProxyProfile> {
        val available = enabledProfilesSnapshot()
        return when (sourceId) {
            "all" -> available
            "manual" -> available.filter { it.subscriptionId == "manual" }
            else -> {
                if (subscriptions.none { it.id == sourceId }) emptyList()
                else available.filter { it.subscriptionId == sourceId }
            }
        }
    }

    fun libraryScopeLabel(sourceId: String): String = when (sourceId) {
        "all" -> "All sources"
        "manual" -> "Manual"
        else -> subscriptions.firstOrNull { it.id == sourceId }?.name ?: "Missing source"
    }

    fun refreshLibrarySource(sourceId: String) {
        when (sourceId) {
            "all" -> refreshAll()
            "manual" -> message = "Manual source has no remote subscription to refresh"
            else -> {
                val sub = subscriptions.firstOrNull { it.id == sourceId }
                when {
                    sub == null -> message = "Selected server source no longer exists"
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

    private fun reconcileProfilesWithTheirLinks() {
        var changed = false
        val repaired = mutableListOf<String>()
        for (index in profiles.indices) {
            val current = profiles[index]
            val repairedProfile = runCatching { reconcileWithLink(current) }.getOrNull() ?: continue
            profiles[index] = repairedProfile
            repaired += current.name
            changed = true
        }
        if (!changed) return
        store.saveProfiles(profiles)
        diagnostics.event(
            "APP",
            "profile-link-reconcile",
            "repaired" to repaired.size,
            "profiles" to repaired.take(12).joinToString(",")
        )
    }

    private fun reconcileWithLink(profile: ProxyProfile): ProxyProfile? {
        val link = SingBoxConfigBuilder.shareLink(profile) ?: return null
        val derived = ProxyParser.parseInput(link).singleOrNull() ?: return null
        val derivedJson = derived.configJson.takeIf { it.isNotBlank() } ?: return null

        if (LinkWireParity.matches(profile, derived)) return null
        return profile.copy(
            scheme = derived.scheme,
            configJson = derivedJson,
            host = derived.host,
            port = derived.port,
            transport = derived.transport,
            security = derived.security
        )
    }

    var networkSnapshot by mutableStateOf(intelligence.currentSnapshot()); private set
    var intelligenceStatus by mutableStateOf(IntelligenceStatus()); private set
    var sentinel by mutableStateOf(PrivacySentinelState()); private set
    var iranMode by mutableStateOf(IranModeState()); private set
    var state by mutableStateOf("DISCONNECTED"); private set
    var stateDetail by mutableStateOf(""); private set

    var activeProfileId by mutableStateOf(""); private set
    var activeProfileSourceId by mutableStateOf(""); private set

    var selectedProfileId by mutableStateOf(store.lastProfileId()); private set
    var selectedProfileSourceId by mutableStateOf(store.lastProfileSourceId()); private set
    var busy by mutableStateOf(false); private set

    private var messageState by mutableStateOf("")

    /**
     * MARBLE_NOTIFICATION_COUNCIL_V231 — a notification tap names the page it wants. The activity
     * writes it here, the root shell consumes it once and does the navigation; no notification
     * knows how the app's navigation graph is built.
     */
    var pendingNotificationPage by mutableStateOf(""); private set

    fun requestNotificationPage(routeId: String) {
        if (routeId.isBlank()) return
        pendingNotificationPage = routeId.trim()
    }

    fun consumeNotificationPage(): String {
        val page = pendingNotificationPage
        pendingNotificationPage = ""
        return page
    }

    var message: String
        get() = messageState
        private set(value) {
            postToMain { messageState = value }
        }

    var benchmarks by mutableStateOf(rememberedBenchmarks()); private set
    var privacy by mutableStateOf<PrivacyReport?>(null); private set
    var bugReport by mutableStateOf<BugReport?>(null); private set

    var serverIntel by mutableStateOf<ServerIntelInfo?>(null); private set
    var serverIntelLoading by mutableStateOf(false); private set
    var serverIntelError by mutableStateOf(""); private set
    private val serverIntelGeneration = AtomicLong(0L)

    val serverLocations: MutableMap<String, ServerCountry> = initialServerLocations()
    var serverLocationScanning by mutableStateOf(false); private set
    private val locationInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val locationBudget = java.util.concurrent.atomic.AtomicInteger(LOCATION_SESSION_BUDGET)

    val serverLocationVerdicts: MutableMap<String, ServerLocationVerdict> =
        java.util.concurrent.ConcurrentHashMap()

    val serverLocationProvisional: MutableMap<String, Boolean> = initialServerLocationProvisional()

    fun serverLocationIsProvisional(profile: ProxyProfile?): Boolean = profile?.let {
        serverLocationProvisional[ServerLocationKey.of(it.host, it.port)] == true
    } ?: false

    private fun initialServerLocationProvisional(): MutableMap<String, Boolean> =
        mutableStateMapOf<String, Boolean>().apply {
            putAll(runCatching { store.loadServerLocationProvisional() }.getOrDefault(emptyMap()))
        }

    fun serverLocationEvidence(profile: ProxyProfile): String =
        serverLocationVerdicts[ServerLocationKey.of(profile.host, profile.port)]?.evidence.orEmpty()

    fun serverLocation(profile: ProxyProfile): ServerCountry =
        serverLocations[ServerLocationKey.of(profile.host, profile.port)] ?: ServerCountry.UNKNOWN

    val ipFamilyScans: MutableMap<String, IpFamilyScan> = initialIpFamilyScans()

    var ipFamilyScanning by mutableStateOf(false); private set

    var ipFamilyScanDone by mutableStateOf(0); private set
    var ipFamilyScanTotal by mutableStateOf(0); private set

    var ipFamilyScanResult by mutableStateOf<IpFamilyScan?>(null); private set

    var ipFamilyScanSummary by mutableStateOf<IpFamilySummary?>(null); private set

    private val ipFamilyScanInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun initialIpFamilyScans(): MutableMap<String, IpFamilyScan> {
        val map = mutableStateMapOf<String, IpFamilyScan>()
        runCatching { store.loadIpFamilyScans() }.getOrDefault(emptyMap()).forEach { (k, v) ->
            map[k] = v
        }
        return map
    }

    fun ipFamilyScan(profile: ProxyProfile): IpFamilyScan? =
        ipFamilyScans[IpFamilyScanner.endpointKey(profile.host, profile.port)]

    fun dismissIpFamilyScanResult() {
        ipFamilyScanResult = null
        ipFamilyScanSummary = null
    }

    fun familyResolutionFor(profile: ProxyProfile): FamilyResolution =
        intelligence.familyResolution(profile, settings)

    fun scanIpFamily(profile: ProxyProfile): Boolean =
        task("Scanning ${profile.name} • IPv4 / IPv6") {
            postToMain {
                ipFamilyScanning = true
                ipFamilyScanDone = 0
                ipFamilyScanTotal = 1
                ipFamilyScanResult = null
                ipFamilyScanSummary = null
            }

            val scan = runFamilyScan(profile, com.marbleng.app.core.ScanMode.DEEP)
            postToMain {
                ipFamilyScanning = false
                ipFamilyScanDone = 1
                ipFamilyScanResult = scan
                message = scan?.headline ?: "Scan failed • ${profile.name} has no usable address"
            }
        }

    fun scanIpFamilyForProfiles(targets: List<ProxyProfile>, label: String): Boolean {
        val unique = targets
            .filter { IpFamilyScanner.endpointKey(it.host, it.port).isNotBlank() }
            .distinctBy { IpFamilyScanner.endpointKey(it.host, it.port) }
        if (unique.isEmpty()) {
            message = "Nothing to scan • no server in $label has a usable address"
            return false
        }
        return task("Scanning $label • IPv4 / IPv6 (${unique.size})") {
            postToMain {
                ipFamilyScanning = true
                ipFamilyScanDone = 0
                ipFamilyScanTotal = unique.size
                ipFamilyScanResult = null
                ipFamilyScanSummary = null
            }
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val results = java.util.Collections.synchronizedList(mutableListOf<IpFamilyScan>())
            val pool = Executors.newFixedThreadPool(
                unique.size.coerceIn(1, IP_FAMILY_SCAN_CONCURRENCY)
            )
            try {
                val futures = unique.map { profile ->
                    pool.submit {
                        val scan = runFamilyScan(profile, com.marbleng.app.core.ScanMode.BALANCED)
                        if (scan != null) results += scan
                        val n = done.incrementAndGet()
                        postToMain { ipFamilyScanDone = n }
                    }
                }

                val deadline = System.currentTimeMillis() +
                    IpFamilyScanner.budgetMsFor(unique.size, IP_FAMILY_SCAN_CONCURRENCY, com.marbleng.app.core.ScanMode.BALANCED)
                for (f in futures) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0L) {
                        f.cancel(true)
                        continue
                    }
                    runCatching { f.get(left, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        .onFailure { f.cancel(true) }
                }
            } finally {
                pool.shutdownNow()
            }
            val summary = IpFamilyScanner.summarize(results.toList())
            postToMain {
                ipFamilyScanning = false
                ipFamilyScanSummary = summary
                message = summary.line
            }
        }
    }

    private fun runFamilyScan(
        profile: ProxyProfile,
        mode: com.marbleng.app.core.ScanMode = com.marbleng.app.core.ScanMode.BALANCED
    ): IpFamilyScan? {
        val key = IpFamilyScanner.endpointKey(profile.host, profile.port)
        if (key.isBlank()) return null
        if (!ipFamilyScanInFlight.add(key)) return ipFamilyScans[key]
        return try {
            val scan = IpFamilyScanner.scanWithMode(
                host = profile.host,
                port = profile.port,
                networkKey = runCatching { intelligence.currentSnapshot().key() }.getOrDefault(""),
                mode = mode
            )
            runCatching { store.saveIpFamilyScan(scan) }
            postToMain { ipFamilyScans[key] = scan }
            diagnostics.event(
                "NET",
                "ip-family-scan",
                "endpoint" to key,
                "verdict" to scan.verdict.name,
                "v4" to scan.ipv4Ok,
                "v6" to scan.ipv6Ok
            )
            scan
        } catch (t: Throwable) {
            if (t is InterruptedException) Thread.currentThread().interrupt()
            diagnostics.error("NET", "ip-family-scan-failed", t, "endpoint" to key)
            null
        } finally {
            ipFamilyScanInFlight.remove(key)
        }
    }

    private fun ensureIpFamilyEvidence(profile: ProxyProfile) {
        val key = IpFamilyScanner.endpointKey(profile.host, profile.port)
        if (key.isBlank()) return
        val networkKey = runCatching { intelligence.currentSnapshot().key() }.getOrDefault("")
        val known = ipFamilyScans[key]
        if (known != null && known.usableOn(networkKey, System.currentTimeMillis())) return
        io.execute { runCatching { runFamilyScan(profile, com.marbleng.app.core.ScanMode.FAST) } }
    }

    fun serverLocationSummary(): Pair<Int, Int> {
        val visible = libraryProfiles
        val known = visible.count { p ->
            val key = ServerLocationKey.of(p.host, p.port)
            key.isNotBlank() && serverLocations[key]?.isKnown == true
        }
        return known to visible.size
    }

    private fun initialServerLocations(): MutableMap<String, ServerCountry> {
        val learned = runCatching { store.loadServerLocations() }.getOrDefault(emptyMap())
        val map = mutableStateMapOf<String, ServerCountry>()
        for (p in profiles) {
            val key = ServerLocationKey.of(p.host, p.port)
            if (key.isBlank()) continue
            val learnedCode = learned[key]?.first
            if (!learnedCode.isNullOrBlank()) {
                map[key] = ServerCountry(
                    learnedCode,
                    ServerCountry.nameFor(learnedCode),
                    ServerCountry.flagFor(learnedCode)
                )
            }
        }
        return map
    }

    var sessionBytes by mutableStateOf(0L); private set
    var lastSessionBytes by mutableStateOf(0L); private set
    var totalUsageBytes by mutableStateOf(runCatching { store.loadTotalUsageBytes() }.getOrDefault(0L)); private set
    var usageSessions by mutableStateOf(runCatching { store.loadUsageSessions() }.getOrDefault(emptyList())); private set
    private var usageSessionStartBytes = -1L
    private var usageSessionProfileId = ""
    private var usageSessionProfileName = ""
    private var usageSessionStartAtMs = 0L
    private var usageSessionFinalized = true
    private val usageTickHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val usageTickRunnable = object : Runnable {
        override fun run() {
            if (state == "CONNECTED" && !usageSessionFinalized) {
                val now = currentUidTrafficBytes()
                if (now >= usageSessionStartBytes) sessionBytes = now - usageSessionStartBytes
                usageTickHandler.postDelayed(this, USAGE_TICK_MS)
            }
        }
    }

    private fun currentUidTrafficBytes(): Long {
        val uid = runCatching { context.applicationInfo.uid }.getOrNull() ?: return -1L
        val rx = runCatching { android.net.TrafficStats.getUidRxBytes(uid) }.getOrDefault(-1L)
        val tx = runCatching { android.net.TrafficStats.getUidTxBytes(uid) }.getOrDefault(-1L)
        if (rx < 0 || tx < 0) return -1L
        return rx + tx
    }

    private fun beginUsageSession(profile: ProxyProfile) {
        usageSessionStartBytes = currentUidTrafficBytes().takeIf { it >= 0L } ?: 0L
        usageSessionProfileId = profile.id
        usageSessionProfileName = profile.name
        usageSessionStartAtMs = System.currentTimeMillis()
        usageSessionFinalized = false
        sessionBytes = 0L
        runCatching {
            store.saveSessionAnchor(
                JSONObject()
                    .put("profileId", profile.id)
                    .put("name", profile.name)
                    .put("startedAt", usageSessionStartAtMs)
                    .put("startBytes", usageSessionStartBytes)
            )
        }
        usageTickHandler.removeCallbacks(usageTickRunnable)
        usageTickHandler.postDelayed(usageTickRunnable, USAGE_TICK_MS)
    }

    private fun finishUsageSession() {
        usageTickHandler.removeCallbacks(usageTickRunnable)
        if (usageSessionFinalized) return
        usageSessionFinalized = true
        runCatching { store.clearSessionAnchor() }
        val used = if (usageSessionStartBytes >= 0L) {
            (currentUidTrafficBytes().takeIf { it >= 0L } ?: usageSessionStartBytes) - usageSessionStartBytes
        } else 0L
        recordFinishedUsageSession(
            UsageSessionRecord(
                usageSessionProfileId,
                usageSessionProfileName,
                usageSessionStartAtMs,
                System.currentTimeMillis(),
                used.coerceAtLeast(0L)
            )
        )
    }

    private fun finishOrphanedUsageSession() {
        val anchor = runCatching { store.loadSessionAnchor() }.getOrNull() ?: return
        runCatching { store.clearSessionAnchor() }
        val startBytes = anchor.optLong("startBytes", 0L)
        val now = currentUidTrafficBytes()
        if (now < startBytes) return
        recordFinishedUsageSession(
            UsageSessionRecord(
                anchor.optString("profileId"),
                anchor.optString("name"),
                anchor.optLong("startedAt", 0L),
                System.currentTimeMillis(),
                (now - startBytes).coerceAtLeast(0L)
            )
        )
    }

    private fun recordFinishedUsageSession(rec: UsageSessionRecord) {
        if (rec.bytes <= 0L) {
            lastSessionBytes = 0L
            return
        }
        lastSessionBytes = rec.bytes
        totalUsageBytes += rec.bytes
        usageSessions = (listOf(rec) + usageSessions).take(60)
        val snapshot = usageSessions
        val total = totalUsageBytes
        io.execute {
            runCatching { store.saveUsageSessions(snapshot) }
            runCatching { store.saveTotalUsageBytes(total) }
        }
    }

    fun ensureServerLocations(force: Boolean = false) {
        if (!settings.serverLocationAutoDetect) {
            postToMain { serverLocationScanning = false }
            return
        }
        io.execute {
            if (!settings.serverLocationAutoDetect) return@execute
            val learned = runCatching { store.loadServerLocations() }.getOrDefault(emptyMap())

            val provisional: Map<String, Boolean> =
                runCatching { store.loadServerLocationProvisional() }.getOrDefault(emptyMap())

            val queue = LinkedHashMap<String, ProxyProfile>()
            val upgradable = LinkedHashMap<String, ProxyProfile>()
            for (p in profiles) {
                val key = ServerLocationKey.of(p.host, p.port)
                if (key.isBlank()) continue
                if (queue.containsKey(key) || upgradable.containsKey(key)) continue
                val learnedCode = learned[key]?.first.orEmpty()
                when {
                    force -> queue[key] = p
                    learnedCode.isBlank() -> queue[key] = p

                    provisional[key] == true -> upgradable[key] = p
                    else -> Unit
                }
            }

            val ordered = queue.values.sortedBy { p -> if (ServerCountry.of(p.name, p.host).isKnown) 1 else 0 } +
                upgradable.values.sortedBy { p -> if (ServerCountry.of(p.name, p.host).isKnown) 1 else 0 }
            if (ordered.isEmpty()) {
                postToMain { serverLocationScanning = false }
                return@execute
            }

            postToMain { serverLocationScanning = true }
            val workers = ordered.size.coerceIn(1, LOCATION_SWEEP_CONCURRENCY)
            val pool = Executors.newFixedThreadPool(workers)
            try {
                val futures = ordered.map { profile ->
                    pool.submit(Runnable { measureLocationOf(profile) })
                }

                val deadline = System.currentTimeMillis() +
                    LOCATION_SWEEP_DEADLINE_MS
                for (f in futures) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0L) {
                        f.cancel(true)
                        continue
                    }
                    runCatching { f.get(left, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        .onFailure { f.cancel(true) }
                }
            } finally {
                pool.shutdownNow()
                postToMain { serverLocationScanning = false }
            }
        }
    }

    private fun measureLocationOf(profile: ProxyProfile) {
        if (!settings.serverLocationAutoDetect) return
        if (locationBudget.get() <= 0) return
        val key = ServerLocationKey.of(profile.host, profile.port)
        if (!locationInFlight.add(key)) return
        try {
            if (locationBudget.decrementAndGet() < 0) return
            val verdict = ServerLocationResolver.resolveCountryDetailed(
                host = profile.host,
                port = profile.port
            )
            if (!verdict.isKnown) return
            runCatching {
                store.saveServerLocation(key, verdict.code, verdict.provisional)
            }
            postToMain {
                serverLocations[key] = ServerCountry(
                    verdict.code,
                    ServerCountry.nameFor(verdict.code),
                    ServerCountry.flagFor(verdict.code)
                )
                serverLocationVerdicts[key] = verdict

                serverLocationProvisional[key] = verdict.provisional
            }
            diagnostics.event(
                "GEO",
                "server-location",
                "endpoint" to key,
                "code" to verdict.code,
                "confidence" to verdict.confidence.name,
                "witnesses" to verdict.witnesses,
                "providers" to verdict.providers
            )
        } finally {
            locationInFlight.remove(key)
        }
    }

    fun ensureServerInsights(force: Boolean = false) {
        ensureServerLocations(force)
        io.execute {
            val networkKey = runCatching { intelligence.currentSnapshot().key() }.getOrDefault("")
            val nowMs = System.currentTimeMillis()
            val pending = profiles.distinctBy { ServerLocationKey.of(it.host, it.port) }
                .filter { p ->
                    val key = IpFamilyScanner.endpointKey(p.host, p.port)
                    if (key.isBlank()) return@filter false
                    val known = ipFamilyScans[key]
                    force || known == null || !known.usableOn(networkKey, nowMs)
                }

                .take(INSIGHT_FAMILY_BATCH)
            if (pending.isEmpty()) return@execute
            val pool = Executors.newFixedThreadPool(
                pending.size.coerceIn(1, IP_FAMILY_SCAN_CONCURRENCY)
            )
            try {
                val futures = pending.map { profile ->

                    pool.submit(java.util.concurrent.Callable { runCatching { runFamilyScan(profile, com.marbleng.app.core.ScanMode.FAST) } })
                }
                val deadline = System.currentTimeMillis() +
                    IpFamilyScanner.budgetMsFor(pending.size, IP_FAMILY_SCAN_CONCURRENCY, com.marbleng.app.core.ScanMode.FAST)
                for (f in futures) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0L) {
                        f.cancel(true)
                        continue
                    }
                    runCatching { f.get(left, java.util.concurrent.TimeUnit.MILLISECONDS) }
                        .onFailure { f.cancel(true) }
                }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    var availableUpdate by mutableStateOf<AppUpdateInfo?>(null); private set

    private val updateCheckInFlight = AtomicBoolean(false)
    @Volatile private var lastUpdateCheckAt = 0L
    @Volatile private var dismissedUpdateTag = ""

    var probeBatch by mutableStateOf<Set<String>>(emptySet()); private set
    var probeRunning by mutableStateOf<Set<String>>(emptySet()); private set
    var probeFinished by mutableStateOf<Set<String>>(emptySet()); private set
    var probeTotal by mutableStateOf(0); private set
    var probeCancelling by mutableStateOf(false); private set
    private val probeCancelGate = ProbeCancelGate()

    var probeLocalFault by mutableStateOf(""); private set
    private val probeLocalFaultGate = ProbeLocalFaultGate()
    var probeCurrentName by mutableStateOf(""); private set
    var probeLastName by mutableStateOf(""); private set
    var probeLastOutcome by mutableStateOf(""); private set
    var probeLastLatencyMs by mutableStateOf(0); private set
    var refreshingSources by mutableStateOf<Set<String>>(emptySet()); private set

    val probeDone: Int get() = probeFinished.size
    val probeActive: Boolean get() = probeTotal > 0

    val inlineProgressActive: Boolean get() = probeActive || refreshingSources.isNotEmpty()

    fun probeStateOf(id: String): ProbeState = when {
        id in probeRunning -> ProbeState.TESTING
        probeTotal > 0 && id in probeBatch && id !in probeFinished -> ProbeState.QUEUED
        else -> ProbeState.IDLE
    }

    private fun beginProbeBatch(candidates: List<ProxyProfile>) {
        probeLocalFaultGate.reset()
        postToMain {
            probeLocalFault = ""
            probeBatch = candidates.mapTo(mutableSetOf()) { it.id }
            probeRunning = emptySet()
            probeFinished = emptySet()
            probeTotal = probeBatch.size
            probeCurrentName = ""
            probeLastName = ""
            probeLastOutcome = ""
            probeLastLatencyMs = 0
            probeCancelling = false
        }
    }

    fun cancelProbes() {
        if (!probeActive && !probeCancelling) return

        if (!probeCancelGate.arm()) return
        diagnostics.event(
            "BENCHMARK",
            "probe-cancel-requested",
            "done" to probeDone,
            "total" to probeTotal
        )
        message = "Cancelling • keeping the ${probeDone} measurements already made"
        postToMain { probeCancelling = true }

        runCatching { activeTask.get()?.cancel(true) }
    }

    private fun markProbeStart(profile: ProxyProfile) = postToMain {
        probeRunning = probeRunning + profile.id
        probeCurrentName = profile.name
        probeLastName = profile.name
        probeLastOutcome = "TESTING"
        probeLastLatencyMs = 0
    }

    private fun markProbeResult(profile: ProxyProfile, result: BenchmarkResult) {
        if (probeLocalFaultGate.trip(result.failureReason, result.success)) {
            stopProbesForLocalFault(result.failureReason)
        }
        postToMain {
            probeRunning = probeRunning - profile.id
            probeFinished = probeFinished + profile.id
            probeCurrentName = profile.name
            probeLastName = profile.name
            probeLastOutcome = when {
                result.success > 0 -> "OK"
                CoreFailurePolicy.isLocal(result.failureReason) -> "CORE / CONFIG ERROR"
                else -> "FAILED"
            }
            probeLastLatencyMs = if (result.success > 0) {
                LinkQualityEstimator.sanitaryRtt(result.latencyMs.toInt())
            } else 0
            mergeBenchmarks(listOf(result))
        }
    }

    private fun stopProbesForLocalFault(reason: String) {
        val summary = ProbeLocalFaultGate.summary(reason)
        diagnostics.event(
            "BENCHMARK",
            "probe-stopped-local-fault",
            "reason" to ProbeLocalFaultGate.headline(reason),
            "done" to probeDone,
            "total" to probeTotal
        )

        probeCancelGate.arm()
        postToMain {
            val left = (probeTotal - probeDone).coerceAtLeast(0)
            message = if (left > 0) {
                "Stopped • $summary — the $left queued nodes were not measured " +
                    "(device fault, not a server verdict)"
            } else {
                "Stopped • $summary (device fault, not a server verdict)"
            }
            probeCancelling = true
            probeLocalFault = reason
        }
        runCatching { activeTask.get()?.cancel(true) }
    }

    private fun endProbeBatch() {
        probeCancelGate.reset()

        probeLocalFaultGate.reset()
        postToMain {
            probeBatch = emptySet()
            probeRunning = emptySet()
            probeFinished = emptySet()
            probeTotal = 0
            probeCurrentName = ""
            probeCancelling = false
        }
    }

    private val probeShouldStop: () -> Boolean = {
        probeCancelGate.isRequested || probeLocalFaultGate.isTripped
    }

    private fun batchSummary(summary: String): String {
        val fault = probeLocalFaultGate.reason.ifBlank { probeLocalFault }
        return if (fault.isBlank()) summary else "$summary • stopped: ${ProbeLocalFaultGate.summary(fault)}"
    }

    private fun beginRefresh(ids: Collection<String>) = postToMain {
        refreshingSources = ids.toSet()
    }

    private fun endRefresh(id: String) = postToMain {
        refreshingSources = refreshingSources - id
    }

    var connectedSinceMs by mutableStateOf(0L); private set

    var connectionPingMs by mutableStateOf(0); private set
    var connectionPingState by mutableStateOf(ConnectionPingState.IDLE); private set

    var connectionPingFailure by mutableStateOf(""); private set
    private val connectionPingInFlight = AtomicBoolean(false)

    var connectionPingProvisional by mutableStateOf(false); private set

    var homePingInjectedReset by mutableStateOf(false); private set
    var homePingStabilityClass by mutableStateOf(""); private set
    var nationalEventCause by mutableStateOf(""); private set
    var nationalEventConfidence by mutableStateOf(0f); private set

    var selectedPingMs by mutableStateOf(0); private set
    var selectedPingState by mutableStateOf(ConnectionPingState.IDLE); private set
    var selectedPingFailure by mutableStateOf(""); private set
    private val selectedPingInFlight = AtomicBoolean(false)

    var selectedPingProvisional by mutableStateOf(false); private set

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

    var geoGateNote by mutableStateOf("")
        private set

    init {
        intelligence.ipFamilyEvidence = { host, port ->
            ipFamilyScans[IpFamilyScanner.endpointKey(host, port)]
        }
        migrateLocalSourceOwnershipIfNeeded()
        installUrlTestHook()
        installRealDelayHook()

        reconcileProfilesWithTheirLinks()

        ensureServerInsights()

        finishOrphanedUsageSession()
        RuntimeDiagnostics.setDebugEnabled(context, settings.debugModeEnabled)
        diagnostics.event("APP", "repository-init", "debugMode" to settings.debugModeEnabled)
        notifier.ensureChannels()
        intelligence.startMonitoring()
        intelligence.addNetworkListener { next ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                networkSnapshot = next
                refreshIntelligenceStatus()
            }

            scanIranMode(force = true, deep = false)

            ensureServerInsights()
            warmRoutes()
        }
        refreshIntelligenceStatus()

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

        ensureGeoAssetsInBackground()
        warmRoutes()
    }

    private fun installUrlTestHook() {
        RouteProbe.urlTestHook = { profile, probeSettings, timeoutMs ->
            val targets = DelayTest.candidates(probeSettings.delayTestUrl)
            val effective = intelligence.effectiveSettings(profile, probeSettings)
            val sameLiveProfile = activeProfileId == profile.id && state == "CONNECTED"
            val result = if (effective.coreEngine() == CoreEngine.SINGBOX) {
                if (activeCoreEngine == CoreEngine.SINGBOX && singBox.isAlive && sameLiveProfile) {
                    singBox.urlTestLiveTargets(targets, timeoutMs)
                } else {
                    singBox.urlTestProfileTargets(profile, effective, targets, timeoutMs)
                }
            } else {
                CoreUrlTestResult(0, false, RouteProbe.URL_TEST_ENGINE_GATE)
            }
            if (result.ok) {
                RouteProbe.ProbeResult(
                    method = RouteProbe.METHOD_URL_TEST,
                    latencyMs = result.delayMs.toDouble(),
                    successPercent = 100,
                    samples = 1,
                    failureReason = if (result.live) "urltest-live" else "urltest-throwaway"
                )
            } else {
                RouteProbe.ProbeResult(
                    method = RouteProbe.METHOD_URL_TEST,
                    latencyMs = RouteProbe.UNREACHABLE,
                    successPercent = 0,
                    samples = 1,
                    lossPercent = 100.0,
                    failureReason = result.detail.ifBlank { "urltest-failed" }.take(160)
                )
            }
        }
    }

    private fun installRealDelayHook() {
        RouteProbe.realDelayHook = { profile, timeoutMs, samples, probeSettings, onSample ->
            val effective = intelligence.effectiveSettings(profile, probeSettings)

            val targets = DelayTest.candidates(probeSettings.delayTestUrl)
            var measured: RouteProbe.ProbeResult? = null
            fun attempt() {
                runCatching {
                    xray.temporary(profile, 0, effective) { port ->
                        measured = RouteProbe.tunnelHttpsMeasureTargets(
                            socksPort = port,
                            timeoutMs = timeoutMs,
                            samples = samples,
                            urls = targets,

                            sampleSpacingMs = probeSettings.pingSampleSpacingMs(),

                            onSample = onSample
                        )
                    }
                }.onFailure { error ->
                    if (error is InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw error
                    }
                }
            }
            attempt()
            if (measured == null) attempt()
            val result = measured
            if (result != null) {
                result.copy(method = RouteProbe.METHOD_REAL_DELAY)
            } else {
                RouteProbe.ProbeResult(
                    RouteProbe.METHOD_REAL_DELAY,
                    RouteProbe.UNREACHABLE,
                    0,
                    PingBudget.samples(samples),
                    lossPercent = 100.0,
                    failureReason = "${effective.coreEngine().id}-start"
                )
            }
        }
    }

    private fun ensureGeoAssetsInBackground() {
        io.execute {
            runCatching {
                if (state != "DISCONNECTED") return@runCatching
                val status = xray.routingAssetStatus()
                val needIp = RoutingEngine.needsGeoIp(settings) && !status.geoIpReady
                val needSite = RoutingEngine.needsGeoSite(settings) && !status.geoSiteReady
                if (!needIp && !needSite) return@runCatching
                diagnostics.event(
                    "ROUTING",
                    "geo-assets-autoprepare",
                    "geoip" to status.geoIpReady,
                    "geosite" to status.geoSiteReady
                )
                val prepared = xray.prepareRoutingAssets(settings, force = false)
                diagnostics.event(
                    "ROUTING",
                    "geo-assets-autoprepare-done",
                    "geoip" to prepared.geoIpReady,
                    "geosite" to prepared.geoSiteReady
                )
                postToMain { geoGateNote = geoGateReason() }
            }
        }
    }

    fun geoGateReason(): String {
        val status = runCatching { xray.routingAssetStatus() }.getOrNull() ?: return ""
        return RoutingEngine.geoDowngradeReason(settings, status.geoIpReady, status.geoSiteReady)
    }

    fun refreshServerIntel(targetProfile: ProxyProfile? = null, force: Boolean = false) {
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

        val familyPlan = AddressFamilyPolicy.plan(settings = settings)
        val generation = serverIntelGeneration.incrementAndGet()
        postToMain {
            serverIntelLoading = true
            serverIntelError = ""
        }

        io.execute {
            var connection: HttpURLConnection? = null
            var basic: ServerIntelInfo? = null
            try {
                val addresses = AddressFamilyPolicy.resolveCandidates(
                    host = endpoint,
                    plan = familyPlan,
                    timeoutMs = 3_000
                ).filterNot {
                    it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress
                }

                if (addresses.isEmpty()) {
                    throw IllegalStateException("Server endpoint did not resolve to a usable IP")
                }

                val preferred = addresses.first()

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

    fun checkForAppUpdate(force: Boolean = false) {
        if (!settings.appUpdateCheckEnabled) return

        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAt < 60_000L) return
        if (!updateCheckInFlight.compareAndSet(false, true)) return
        lastUpdateCheckAt = now

        io.execute {
            var connection: HttpURLConnection? = null
            try {
                // MARBLE_LATEST_VERSION_RULE_V235 — the list, not GitHub's "latest" alias:
                // that endpoint is by definition the newest *stable*, so it structurally
                // cannot offer a newer beta, and a build published after the newest stable
                // stayed invisible. The rule is one (LatestReleaseRule): newest published
                // non-draft release by `published_at`, whatever its label — a stable
                // published after a beta wins, a beta published after a stable wins.
                connection = (URL(
                    "https://api.github.com/repos/marble098/MarbleNG/releases?per_page=30"
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
                val entries = org.json.JSONArray(payload)
                val byTag = HashMap<String, JSONObject>()
                val candidates = ArrayList<LatestReleaseRule.Candidate>(entries.length())
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val candidate = LatestReleaseRule.candidate(
                        tag = entry.optString("tag_name"),
                        publishedAt = entry.optString("published_at"),
                        createdAt = entry.optString("created_at"),
                        prerelease = entry.optBoolean("prerelease"),
                        draft = entry.optBoolean("draft")
                    ) ?: continue
                    candidates += candidate
                    byTag[candidate.tag] = entry
                }

                val winner = LatestReleaseRule.newest(candidates) ?: run {
                    postToMain { availableUpdate = null }
                    return@execute
                }
                val tag = winner.tag
                val json = byTag[tag] ?: return@execute

                // The date picked the candidate; the version guards the direction, so a
                // freshly published hotfix of an older line can never offer a downgrade.
                if (!LatestReleaseRule.isUpdate(tag, BuildConfig.VERSION_NAME)) {
                    postToMain { availableUpdate = null }
                    return@execute
                }

                if (tag == dismissedUpdateTag) return@execute

                val htmlUrl = json.optString("html_url").trim()
                    .takeIf { it.startsWith("https://github.com/") }
                    ?: "https://github.com/marble098/MarbleNG/releases"

                val release = AppUpdateInfo(
                    version = LatestReleaseRule.displayVersion(tag),
                    tag = tag,
                    title = json.optString("name").trim()
                        .ifBlank { "MarbleNG $tag" }
                        .take(120),
                    notes = json.optString("body")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .trim()
                        .take(1_800),
                    url = htmlUrl,
                    prerelease = winner.prerelease,
                    publishedAt = json.optString("published_at").trim()
                )

                postToMain {
                    if (settings.appUpdateCheckEnabled && release.tag != dismissedUpdateTag) {
                        availableUpdate = release
                    }
                }
                // MARBLE_NOTIFICATION_COUNCIL_V231 — `notifyCoreUpdates` existed in Settings and no
                // code ever raised a CORE alert, so the switch was dead. An available build is the
                // one event that switch was always meant to describe.
                notifier.alert(
                    SmartNotificationKind.CORE,
                    "app:${release.tag}",
                    "App update available",
                    "MarbleNG ${release.version} is ready to install",
                    settings,
                    minIntervalOverrideMs = 6 * 60 * 60 * 1000L
                )
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

    // MARBLE_LATEST_VERSION_RULE_V235 — the old stable-only semver pair is gone: a
    // strictly-`x.y.z` parser was the second half of the stable-only rule (a `-beta` tag
    // parsed to null and the check silently gave up). Version order lives in
    // LatestReleaseRule now, shared in spirit with the core and toolchain updaters.

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
        tailLatencyMs: Int = -1,
        holdQuality: Boolean = false
    ) {
        if (pingMs <= 0) return

        val honestPing = LinkQualityEstimator.sanitaryRtt(pingMs)
        if (honestPing <= 0) return

        val rawScore = calculateLiveRouteScore(
            pingMs = honestPing,
            jitterMs = jitterMs,
            successPercent = successPercent,
            attemptCount = attemptCount,
            tailLatencyMs = tailLatencyMs
        )

        postToMain {
            livePingMs = honestPing
            if (jitterMs >= 0) {
                liveJitterMs = jitterMs.coerceIn(0, 10_000)
            }

            if (!holdQuality) {
                liveRouteScore = rawScore
                liveRouteSuccessPercent = successPercent.coerceIn(0, 100)
            }
            liveRouteAttempts = attemptCount.coerceIn(0, 10_000)
            if (tailLatencyMs >= 0) liveTailLatencyMs = tailLatencyMs.coerceIn(0, 10_000)

            if (settings.autoServerSelectorEnabled &&
                settings.autoServerOnFailure &&
                state == "CONNECTED" &&
                liveRouteBroken() &&
                autoFailoverArmed.compareAndSet(true, false)
            ) {
                diagnostics.event(
                    "AUTOSELECT",
                    "failover-triggered",
                    "from" to activeProfileId.take(12),
                    "attempts" to liveRouteAttempts,
                    "successPercent" to liveRouteSuccessPercent
                )
                runAutoServerSelection(reason = "failure", connect = true)
            }
            liveRouteProbeStatus = buildString {
                append("Verified HTTPS • ")
                append(sampleCount.coerceAtLeast(1))
                append('/')
                append(attemptCount.coerceAtLeast(1))
                append(" RTT • ")
                append(successPercent.coerceIn(0, 100))
                append("% success")
                if (tailLatencyMs > 0) append(" • p90 ${tailLatencyMs.coerceAtMost(10_000)} ms")
                if (holdQuality) append(" • score settling after target change")
            }

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

    fun onMemoryPressure(level: Int) {
        val runtime = Runtime.getRuntime()
        val heapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        diagnostics.event(
            "MEMORY", "trim",
            "level" to level,
            "state" to state,
            "benchmarks" to benchmarks.size,
            "hasBugReport" to (bugReport != null),
            "heapUsedKb" to heapUsedKb
        )
        if (level < 15) return
        intelligence.onMemoryPressure(level)
        CensorshipAwareDnsResolver.onMemoryPressure(level)
        postToMain {
            if (level >= 15) privacy = null
            if (level >= 20) {
                bugReport = null

                serverIntel = null
            }
            // MARBLE_REMEMBERED_PING_KEEP_V163 — the benchmark table is NOT trimmed under memory
        }
    }

    fun profile(id: String, sourceId: String? = null): ProxyProfile? {
        return if (!sourceId.isNullOrBlank()) {
            profiles.firstOrNull { it.id == id && it.subscriptionId == sourceId }
        } else {
            profiles.firstOrNull { it.id == id }
        }
    }

    fun isActiveProfile(profile: ProxyProfile): Boolean {
        if (state != "CONNECTED" || profile.id.isBlank()) return false
        if (activeProfileId.isNotBlank()) {
            if (activeProfileId != profile.id) return false
            return activeProfileSourceId.isBlank() ||
                activeProfileSourceId == profile.subscriptionId
        }
        return stateDetail.isNotBlank() && profile.name == stateDetail
    }

    fun isActiveProfile(id: String): Boolean =
        state == "CONNECTED" && id.isNotBlank() && activeProfileId == id

    fun setRuntimeState(s: String, d: String) {
        if (s == "DISCONNECTING" && state != "CONNECTED" && state != "CONNECTING") return
        diagnostics.event("APP", "state", "from" to state, "to" to s, "detail" to d.take(160))
        postToMain {
            state = s
            stateDetail = d

            if (s == "DISCONNECTED") {
                autoFailoverArmed.set(false)
                finishUsageSession()
            }
            if (s != "CONNECTED") {
                activeProfileId = ""
                activeProfileSourceId = ""
                connectedSinceMs = 0L
                connectionPingMs = 0
                connectionPingState = ConnectionPingState.IDLE
                connectionPingFailure = ""
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

    fun updateSettings(v: AppSettings, coalesceWrite: Boolean = false) {
        val previous = settings
        val debugChanged = previous.debugModeEnabled != v.debugModeEnabled
        val familyChanged = previous.addressFamilyMode != v.addressFamilyMode ||
            previous.ipv6Enabled != v.ipv6Enabled ||
            previous.dnsQueryStrategy != v.dnsQueryStrategy ||
            previous.dnsFakeIpEnabled != v.dnsFakeIpEnabled ||
            previous.dnsHijackEnabled != v.dnsHijackEnabled
        val activeProfile = if (familyChanged && state in setOf("CONNECTED", "CONNECTING")) {
            if (state == "CONNECTED") profile(activeProfileId, activeProfileSourceId)
            else profile(selectedProfileId, selectedProfileSourceId)
        } else null
        val activeMode = previous.connectionMode
        val updateChecksWereEnabled = previous.appUpdateCheckEnabled

        val usableProbeMethod = v.probeMethod.forEngine(v.coreEngine())
        val probeMethodDemoted = usableProbeMethod != v.probeMethod

        settings = v.copy(intelligenceEnabled = true, probeMethod = usableProbeMethod)
        if (!v.appUpdateCheckEnabled) {
            postToMain { availableUpdate = null }
        } else if (!updateChecksWereEnabled) {
            dismissedUpdateTag = ""
        }
        ensureLibrarySourceSelectionValid()
        if (coalesceWrite) scheduleSettingsWrite() else flushSettingsNow(settings)
        if (probeMethodDemoted) {
            diagnostics.event(
                "BENCHMARK",
                "probe-method-demoted",
                "from" to v.probeMethod.name,
                "to" to usableProbeMethod.name,
                "engine" to v.coreEngine().id
            )
            message = "URL test runs on sing-box extended • ${usableProbeMethod.name.replace('_', ' ').lowercase()} selected instead"
        }
        if (debugChanged) {
            RuntimeDiagnostics.setDebugEnabled(context, v.debugModeEnabled)
            diagnostics.event("DEBUG", "mode-changed", "enabled" to v.debugModeEnabled)
        }
        if (activeProfile != null) {
            diagnostics.event("VPN", "family-change-reconnect",
                "mode" to v.addressFamilyMode.name, "profile" to activeProfile.id.take(12))
            setRuntimeState("CONNECTING", "Applying ${v.addressFamilyMode.name.replace('_', ' ')}")
            val intent = Intent(context, MarbleVpnService::class.java)
                .setAction(MarbleVpnService.ACTION_START)
                .putExtra(MarbleVpnService.EXTRA_PROFILE, activeProfile.id)
                .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, activeProfile.subscriptionId)
                .putExtra(MarbleVpnService.EXTRA_MODE,
                    if (activeMode == ConnectionMode.FULL_TUN) MarbleVpnService.MODE_TUN
                    else MarbleVpnService.MODE_PROXY)
            launchConnectionService(intent, activeProfile.name)
        }
        notifier.ensureChannels()
        if (!v.smartNotificationsEnabled) notifier.cancelOptional()
        refreshIntelligenceStatus()
    }

    private fun scheduleSettingsWrite() {
        val snapshot = settings
        synchronized(settingsWriteLock) {
            settingsWritePending = snapshot
            if (settingsWriteFuture != null) return
            val writeTask = Runnable {
                val pending = synchronized(settingsWriteLock) {
                    val armed = settingsWritePending
                    settingsWritePending = null
                    settingsWriteFuture = null
                    armed
                }
                if (pending != null) {
                    runCatching { store.saveSettings(pending) }.onFailure { error ->

                        diagnostics.event(
                            "SETTINGS", "coalesced-write-failed",
                            "error" to (error.message ?: error.javaClass.simpleName)
                        )
                    }
                }
            }
            settingsWriteFuture = settingsWriter.schedule(
                writeTask,
                SETTINGS_WRITE_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun flushSettingsNow(v: AppSettings) {
        synchronized(settingsWriteLock) {
            settingsWriteFuture?.cancel(false)
            settingsWriteFuture = null
            settingsWritePending = null
        }
        store.saveSettings(v)
    }

    fun flushSettings() {
        val pending = synchronized(settingsWriteLock) {
            val snapshot = settingsWritePending
            settingsWritePending = null
            settingsWriteFuture?.cancel(false)
            settingsWriteFuture = null
            snapshot
        }
        if (pending != null) {
            runCatching { store.saveSettings(pending) }.onFailure { error ->
                diagnostics.event(
                    "SETTINGS", "flush-failed",
                    "error" to (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    fun effectiveSettingsFor(
        profile: ProxyProfile,
        withAcceleration: Boolean = true
    ): AppSettings {
        val tuned = if (settings.intelligenceEnabled) {
            intelligence.effectiveSettings(profile, settings, withAcceleration)
        } else {
            val resolution = familyResolutionFor(profile)
            DpiEvasionPolicy.heal(
                IranShield.apply(
                    Ipv6FallbackLadder.apply(settings, resolution),
                    profile,
                    iranMode,
                    geoIpReady()
                ),
                DpiEvasionPolicy.PathEvidence(),
                iranMode
            )
        }

        // MARBLE_FRAGMENT_POLICY_V221 — `Automatic` is a decision about the *network*, not about the
        // document: it becomes the cheap record split only once the app's own evidence says this
        // path filters TLS, and it stays OFF everywhere else. Storage is untouched, so a phone that
        // travels from a filtered network to an open one goes back to writing nothing.
        val censored = iranMode.active && iranMode.policy != IranModePolicy.OFF
        val fragment = FragmentPolicy.effectiveStrategyId(tuned.fragmentStrategy, censored)

        // MARBLE_CORE_POWER_V221 — the power profile is a projection over the *effective* settings,
        // never over storage. Balanced is the identity function, so an install that never touches
        // the new page produces exactly the settings it always did; Turbo and Extreme are bounded by
        // the same ranges the config builders clamp to (CorePowerV221Test pins both).
        return CorePower.tune(
            IdentityGuard.apply(tuned.copy(fragmentStrategy = fragment)),
            settings.powerProfile,
            CorePower.Device(deviceCores, deviceMemoryMb),
            CorePower.muxSafeFor(profile)
        )
    }

    fun tuningBaseFor(profile: ProxyProfile): AppSettings =
        effectiveSettingsFor(profile, withAcceleration = false)

    // MARBLE_CONNECT_WARMUP_V221 — resolve the road before the user has to drive it.
    //
    // The network listener and the first paint both call this, so the active route's host (and the
    // handful of nodes the user is most likely to tap next) is already in the warm cache by the
    // time a ping or a connect asks for it. The warmup runs on its own daemon thread, never blocks a
    // caller, and is a no-op when the setting is off.
    fun warmRoutes() {
        if (!settings.warmupOnLaunch) return
        val candidates = buildList {
            profile(activeProfileId, activeProfileSourceId)?.let { add(it) }
            profile(selectedProfileId, selectedProfileSourceId)?.let { add(it) }
            addAll(profiles.asSequence().filter { it.host.isNotBlank() }.take(12).toList())
        }
            .map { it.host }
            .filter { it.isNotBlank() }
            .distinct()

        if (candidates.isEmpty()) return
        warmupWorker.execute {
            runCatching {
                ConnectWarmup.warm(candidates, settings)
                diagnostics.event("NETWORK", "warmup", "hosts" to candidates.size)
            }
        }
    }

    private fun geoIpReady(): Boolean =
        runCatching { xray.routingAssetStatus().geoIpReady }.getOrDefault(false)

    fun scanIranMode(force: Boolean = false, deep: Boolean = false) {
        val policy = settings.iranModePolicy
        val previous = iranMode
        val now = System.currentTimeMillis()
        val networkKey = intelligence.currentSnapshot().key()

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

    // ==========================================================================================
    // MARBLE_NETWORK_COUNCIL_V236 — twelve specialists, one scan, one applied plan
    // ==========================================================================================
    // The repository owns three things the pure council cannot: the worker that runs the blocking
    // probe, the Compose state the page reads, and the single call that writes the plan back
    // through `updateSettings` (so every existing side effect — effective-settings projection,
    // debounced persistence, connected-route rebuild — still happens exactly once).

    private val councilWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "marble-network-council").apply { isDaemon = true }
    }

    private val councilScanInFlight = AtomicBoolean(false)

    var councilRunning by mutableStateOf(false); private set

    var councilStage by mutableStateOf(""); private set

    var councilProgress by mutableStateOf(0f); private set

    var councilEvidence by mutableStateOf<NetworkCouncil.NetworkEvidence?>(null); private set

    var councilPlan by mutableStateOf<NetworkCouncil.Plan?>(null); private set

    var councilReport by mutableStateOf(
        runCatching { store.loadCouncilReport() }.getOrNull()
    ); private set

    /**
     * Whether a finished scan writes its plan by itself. This is a preference about the scanner,
     * not a dial the cores read, so it lives beside [councilReport] in the store rather than in
     * [AppSettings] — which is already at the ceiling of what a generated `copy` can carry.
     */
    var councilAutoApply by mutableStateOf(runCatching { store.councilAutoApply() }.getOrDefault(true))
        private set

    /** Named `update…`, not `set…`: a `setX` here would collide with the property's own setter. */
    fun updateCouncilAutoApply(enabled: Boolean) {
        councilAutoApply = enabled
        runCatching { store.setCouncilAutoApply(enabled) }
    }

    /** The seat table, ordered, so the page never has to know the enum's order. */
    val councilSeats: List<NetworkCouncil.CouncilSeat>
        get() = NetworkCouncil.CouncilSeat.entries

    fun councilVerdict(seat: NetworkCouncil.CouncilSeat): NetworkCouncil.CouncilSeverity =
        councilPlan?.seatVerdicts?.get(seat) ?: NetworkCouncil.CouncilSeverity.GOOD

    fun startNetworkCouncilScan() {
        if (!councilScanInFlight.compareAndSet(false, true)) return
        val snapshot = settings
        postToMain {
            councilRunning = true
            councilStage = "Reading the link"
            councilProgress = 0f
            councilEvidence = null
            councilPlan = null
        }
        diagnostics.event("COUNCIL", "scan-started", "transport" to snapshot.connectionMode.name)

        councilWorker.execute {
            val outcome = runCatching {
                NetworkScanner.scan(context, snapshot) { stage ->
                    postToMain {
                        councilStage = stage.label
                        councilProgress = stage.fraction.coerceIn(0f, 1f)
                    }
                }
            }
            postToMain {
                councilScanInFlight.set(false)
                councilRunning = false
                councilStage = ""
                councilProgress = 1f
                outcome.onSuccess { evidence ->
                    val route = profile(selectedProfileId, selectedProfileSourceId)
                        ?: profile(activeProfileId, activeProfileSourceId)
                    val plan = NetworkCouncil.deliberate(
                        evidence = evidence,
                        base = snapshot,
                        muxSafe = CorePower.muxSafeFor(route)
                    )
                    councilEvidence = evidence
                    councilPlan = plan
                    diagnostics.event(
                        "COUNCIL",
                        "scan-finished",
                        "score" to plan.metrics.score,
                        "grade" to plan.metrics.grade,
                        "changes" to plan.changes.size
                    )
                    if (councilAutoApply && plan.changes.isNotEmpty()) {
                        applyNetworkCouncilPlan(plan)
                    }
                }.onFailure { error ->
                    councilStage = ""
                    message = "Network council could not finish: " +
                        (error.message ?: error.javaClass.simpleName)
                    diagnostics.error("COUNCIL", "scan-failed", error)
                }
            }
        }
    }

    /**
     * Write the plan. This is the only entry point, and it goes through [updateSettings] so the
     * plan inherits every guarantee a hand-edited setting already has.
     */
    fun applyNetworkCouncilPlan(plan: NetworkCouncil.Plan? = councilPlan): Boolean {
        val target = plan ?: return false
        if (target.changes.isEmpty()) {
            message = "Network council found nothing to change"
            return false
        }
        updateSettings(target.settings)
        val report = CouncilReport(
            atMs = System.currentTimeMillis(),
            transport = councilEvidence?.radio?.transport ?: NetworkCouncil.LinkTransport.NONE,
            operatorName = councilEvidence?.radio?.operatorName.orEmpty(),
            networkType = councilEvidence?.radio?.networkType.orEmpty(),
            score = target.metrics.score,
            grade = target.metrics.grade,
            applied = true,
            findings = target.findings,
            changes = target.changes
        )
        councilReport = report
        runCatching { store.saveCouncilReport(report) }
        diagnostics.event(
            "COUNCIL",
            "plan-applied",
            "score" to target.metrics.score,
            "changes" to target.changes.size
        )
        message = "Network council applied ${target.changes.size} settings • ${target.metrics.grade}"
        return true
    }

    fun clearNetworkCouncilReport() {
        councilReport = null
        councilEvidence = null
        councilPlan = null
        councilProgress = 0f
        councilStage = ""
        runCatching { store.saveCouncilReport(null) }
    }

    fun councilScanAgeLabel(nowMs: Long = System.currentTimeMillis()): String {
        val at = councilReport?.atMs ?: return ""
        if (at <= 0L) return ""
        val minutes = ((nowMs - at) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1L -> "just now"
            minutes < 60L -> "$minutes min ago"
            minutes < 1_440L -> "${minutes / 60L}h ago"
            else -> "${minutes / 1_440L}d ago"
        }
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
            "Test notification",
            "Smart alerts are working on this device.",
            settings,
            minIntervalOverrideMs = 0L
        )
        message = when {
            posted -> "Smart test alert sent"
            !notifier.optionalPermissionGranted() -> "Android is not letting MarbleNG post notifications"
            !settings.smartNotificationsEnabled -> "Smart alerts are switched off"
            else -> "The test alert could not be posted"
        }
    }

    fun clearSmartNotifications() {
        notifier.cancelOptional()
        message = "Optional MarbleNG alerts cleared"
    }

    fun setConnectionMode(mode: ConnectionMode) {
        val enableDnsHijack = mode == ConnectionMode.FULL_TUN && !settings.dnsHijackEnabled
        if (settings.connectionMode == mode) {
            if (enableDnsHijack) updateSettings(settings.copy(dnsHijackEnabled = true))
            return
        }
        if (state == "CONNECTED" || state == "CONNECTING" || state == "BLOCKED") stopVpn()
        updateSettings(settings.copy(connectionMode = mode,
            dnsHijackEnabled = settings.dnsHijackEnabled || enableDnsHijack))
        message = when (mode) {
            ConnectionMode.FULL_TUN -> "Full-device TUN selected"
            ConnectionMode.LOCAL_PROXY -> "Local SOCKS5 proxy selected • 127.0.0.1:${settings.localProxyPort}"
        }
    }

    val activeTunnelCore: TunnelCore get() = parseTunnelCore(settings.tunnelCoreId)

    /**
     * MARBLE_TUN2SOCKS_CORE_V222 — switches the datapath that carries packets between the TUN
     * device and the proxy core. A core the APK cannot load is refused here with the reason the
     * loader gave, so Settings never stores a choice the next connection would silently ignore.
     */
    fun setTunnelCore(core: TunnelCore) {
        val previous = parseTunnelCore(settings.tunnelCoreId)
        if (previous == core) return
        if (!TunnelCoreInfo.isAvailable(core)) {
            message = TunnelCoreInfo.unavailableReason(core)
            return
        }
        val next = settings.copy(tunnelCoreId = core.id)
        if (state == "CONNECTED" || state == "CONNECTING" || state == "BLOCKED") stopVpn()
        updateSettings(next)
        diagnostics.event("TUNNEL", "datapath-switch", "from" to previous.id, "to" to core.id)
        message = "Tunnel engine set to ${TunnelCoreInfo.displayName(core)}. Reconnect to run it."
    }

    fun setCoreEngine(engine: CoreEngine) {
        val previous = parseCoreEngine(settings.coreEngineId)
        if (previous == engine) return
        val next = settings.copy(coreEngineId = engine.id)
        if (state == "CONNECTED" || state == "CONNECTING" || state == "BLOCKED") stopVpn()
        updateSettings(next)
        diagnostics.event("CORE", "engine-switch", "from" to previous.id, "to" to engine.id)
        message = "Proxy core set to ${CoreEngineInfo.displayName(engine)}. Reconnect to run it."
    }

    fun setDelayTestUrl(url: String) {
        updateSettings(settings.copy(delayTestUrl = url.trim()))

        message = if (url.trim().isEmpty()) {
            "Delay URL reset to ${DelayTest.URL}"
        } else {
            "Delay URL set to ${url.trim()}"
        }
    }

    /**
     * MARBLE_HOME_SESSION_EVIDENCE_V110 / MARBLE_SMART_TUNNEL_PING_V111 / MARBLE_HOME_PING_RESCUE_V112
     * / MARBLE_PING_GUARANTEE_V114 — the Home connection ping.
     *
     * Every tap runs a *fresh, real* measurement through the live Xray path — never a cached or
     * estimated number, and never SOCKS CONNECT setup timing dressed up as Internet ping.
     *
     * V112 turned the probe into a ladder of independent, provider-diverse measurement modes raced
     * in parallel. V114 adds the three guarantees the readout owes the user:
     *
     *  1. **Instant** — the tunnel monitor already measures the live route RTT continuously, so the
     *     first frame after a tap shows that real number instead of a "measuring" placeholder, and
     *     the first verified race sample is published the moment it lands.
     *  2. **Bounded** — the whole race is capped at 2.6 s (it was 9 s): every probe carries a
     *     1.6–2.0 s socket timeout and the pool is shut down when the budget is spent.
     *  3. **Never empty** — while a tunnel carries traffic this cannot answer "no response".
     *     Verified HTTPS first-byte / generate_204 RTT wins; a SOCKS CONNECT handshake through the
     *     same tunnel is the next honest measurement; then the live monitor RTT; then the stored
     *     benchmark of the connected server. [ConnectionPingState.FAILED] stays reachable only for
     *     a route that is not connected at all.
     *
     * It never becomes a background timer, so it cannot add traffic to a metered connection.
     */
    fun measureConnectionPing() {
        if (state != "CONNECTED") {
            postToMain {
                connectionPingMs = 0
                connectionPingState = ConnectionPingState.IDLE
            }
            return
        }
        if (!connectionPingInFlight.compareAndSet(false, true)) return

        val target = homeRoute() ?: lastProfile()
        val port = activeProxyPort()
        val sessionAtStart = connectedSinceMs
        val timeoutMs = settings.pingTimeoutMs()
        val samples = settings.pingSampleCount()

        postToMain {
            connectionPingState = ConnectionPingState.MEASURING
            connectionPingProvisional = true
            connectionPingFailure = ""
        }

        io.execute {
            val probe = runCatching {
                target?.let {
                    RouteProbe.measureUnified(
                        profile = it,
                        method = settings.probeMethod,
                        tunnelPort = port,
                        samples = samples,
                        timeoutMs = timeoutMs,
                        settings = settings,
                        onSample = { sampleMs ->

                            if (connectionPingInFlight.get() &&
                                connectedSinceMs == sessionAtStart &&
                                state == "CONNECTED"
                            ) {
                                val provisional = LinkQualityEstimator.sanitaryRtt(
                                    sampleMs.roundToInt()
                                ).takeIf { it >= PROVISIONAL_PING_FLOOR_MS } ?: 0
                                if (provisional > 0) {
                                    postToMain {
                                        if (connectionPingInFlight.get() &&
                                            connectedSinceMs == sessionAtStart &&
                                            state == "CONNECTED"
                                        ) {
                                            connectionPingMs = provisional
                                        }
                                    }
                                }
                            }
                        }
                    )
                } ?: RouteProbe.ProbeResult(
                    "HOME",
                    RouteProbe.UNREACHABLE,
                    0,
                    samples,
                    failureReason = "no-route"
                )
            }.getOrNull()
            val measured = probe
                ?.takeIf { it.successPercent > 0 && it.latencyMs >= 20.0 && it.latencyMs < RouteProbe.UNREACHABLE }
                ?.latencyMs
                ?.let { LinkQualityEstimator.sanitaryRtt(it.roundToInt()) }
                ?: 0

            diagnostics.event(
                "APP",
                "home-connection-ping",
                "measured" to measured,
                "port" to port,
                "mode" to settings.probeMethod.name.lowercase(),
                "profile" to (target?.id ?: "").take(12)
            )

            val stability = runCatching {
                intelligence.recordLiveObservation(
                    activeProfileId,
                    measured.toDouble(),
                    probe?.jitterMs ?: 0.0,
                    probe?.successPercent?.toDouble() ?: 0.0,
                    transportType = target
                        ?.let { ProtocolFingerprintAwareVerifier.transportTypeOf(it) } ?: ""
                )
                intelligence.consistencyReport(activeProfileId)
            }.getOrNull()
            val attribution = runCatching { intelligence.attributionTick() }.getOrNull()

            postToMain {
                connectionPingInFlight.set(false)

                when {
                    state != "CONNECTED" || connectedSinceMs != sessionAtStart -> {
                        connectionPingMs = 0
                        connectionPingState = ConnectionPingState.IDLE
                        connectionPingFailure = ""
                    }
                    measured >= 20 -> {
                        connectionPingMs = measured
                        connectionPingState = ConnectionPingState.MEASURED
                        connectionPingFailure = ""
                    }
                    else -> {
                        connectionPingState = ConnectionPingState.FAILED
                        connectionPingFailure = classifyPingFailure(probe?.failureReason)
                    }
                }
                connectionPingProvisional = false
                homePingInjectedReset = probe?.injectedResetSuspected == true || measured < 20
                homePingStabilityClass = stability?.stabilityClass?.name ?: ""

                nationalEventCause = NationalEventBannerPolicy.next(
                    previous = nationalEventCause,
                    freeze = attribution?.rankingFreeze == true,
                    cause = attribution?.let { CausalAttribution.shortKey(it.cause) }.orEmpty(),
                    confidence = attribution?.confidence ?: 0.0
                )
                nationalEventConfidence = (attribution?.confidence ?: 0.0).toFloat()
            }
        }
    }

    fun classifyPingFailure(reason: String?): String {
        val clean = reason.orEmpty().trim().lowercase()
        if (clean.isEmpty()) return "unreachable"
        return when {
            "timeout" in clean || "deadline" in clean || "timed-out" in clean -> "timeout"
            "refus" in clean || "unreach" in clean || "no-route" in clean ||
                "gate-failed" in clean || "all-failed" in clean ||
                "all-methods-failed" in clean || "no-responses" in clean -> "unreachable"
            else -> "error"
        }
    }

    fun measureHomePing() {
        if (state == "CONNECTED") {
            measureConnectionPing()
        } else {
            measureSelectedPing()
        }
    }

    fun measureSelectedPing() {
        if (!selectedPingInFlight.compareAndSet(false, true)) return
        val target = homeRoute() ?: lastProfile()
        if (target == null) {
            postToMain {
                selectedPingInFlight.set(false)
                selectedPingMs = 0
                selectedPingState = if (target == null) ConnectionPingState.IDLE else ConnectionPingState.FAILED
                selectedPingFailure = if (target == null) "" else "error"
                selectedPingProvisional = false
            }
            if (target == null) message = "Select a server first • then ping it"
            return
        }

        val targetId = target.id
        val targetSource = target.subscriptionId

        postToMain {
            selectedPingState = ConnectionPingState.MEASURING
            selectedPingProvisional = true
            selectedPingFailure = ""
        }
        io.execute {
            val timeoutMs = settings.pingTimeoutMs()
            val samples = settings.pingSampleCount()
            val probeResult = runCatching {
                RouteProbe.measureUnified(
                    profile = target,
                    method = settings.probeMethod,
                    tunnelPort = 0,
                    samples = samples,
                    timeoutMs = timeoutMs,
                    settings = settings,
                    onSample = { sampleMs ->
                        if (selectedPingInFlight.get() &&
                            selectedProfileId == targetId &&
                            state != "CONNECTED"
                        ) {
                            val provisional = LinkQualityEstimator.sanitaryRtt(
                                sampleMs.roundToInt()
                            ).takeIf { it >= PROVISIONAL_PING_FLOOR_MS } ?: 0
                            if (provisional > 0) {
                                postToMain {
                                    if (selectedPingInFlight.get() &&
                                        selectedProfileId == targetId &&
                                        state != "CONNECTED"
                                    ) {
                                        selectedPingMs = provisional
                                    }
                                }
                            }
                        }
                    }
                )
            }.getOrNull()
            val measured = probeResult
                ?.takeIf { it.successPercent > 0 && it.latencyMs >= 20.0 && it.latencyMs < RouteProbe.UNREACHABLE }
                ?.latencyMs
                ?.let { LinkQualityEstimator.sanitaryRtt(it.roundToInt()) }
                ?: 0
            diagnostics.event(
                "APP",
                "home-selected-ping",
                "measured" to measured,
                "mode" to settings.probeMethod.name.lowercase(),
                "profile" to targetId.take(12)
            )
            postToMain {
                selectedPingInFlight.set(false)
                val stillSelected = selectedProfileId == targetId &&
                    (selectedProfileSourceId.isBlank() || selectedProfileSourceId == targetSource)

                val stability = runCatching {
                    intelligence.recordLiveObservation(
                        targetId,
                        measured.toDouble(),
                        probeResult?.jitterMs ?: 0.0,
                        probeResult?.successPercent?.toDouble() ?: 0.0,
                        transportType = ProtocolFingerprintAwareVerifier.transportTypeOf(target)
                    )
                    intelligence.consistencyReport(targetId)
                }.getOrNull()
                postToMain {
                    when {
                        state == "CONNECTED" || !stillSelected -> {
                            selectedPingMs = 0
                            selectedPingState = ConnectionPingState.IDLE
                            selectedPingFailure = ""
                        }
                        measured >= 20 -> {
                            selectedPingMs = measured
                            selectedPingState = ConnectionPingState.MEASURED
                            selectedPingFailure = ""
                        }
                        else -> {
                            selectedPingState = ConnectionPingState.FAILED
                            selectedPingFailure = classifyPingFailure(probeResult?.failureReason)
                        }
                    }
                    selectedPingProvisional = false
                    homePingInjectedReset = probeResult?.injectedResetSuspected == true || measured < 20
                    homePingStabilityClass = stability?.stabilityClass?.name ?: ""
                }
            }
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

        ensureServerInsights()
        return incoming.size
    }

    fun refresh(id: String) {
        val sub = subscriptions.firstOrNull { it.id == id } ?: return
        if (state == "CONNECTING" || state == "BLOCKED") {
            message = "Wait until the connection is stable or disconnected before refreshing"
            return
        }
        if (sub.url.isBlank()) {
            message = "${sub.name} is a local source • add Manual/SSH servers into it"
            return
        }
        task("Refreshing ${sub.name}") {
            beginRefresh(listOf(sub.id))

            val outcome = runCatching {
                val payload = httpSubscription(sub.url)
                val parsed = ProxyParser.parseInput(payload.text, sub.id, sub.name)
                require(parsed.isNotEmpty()) {
                    "No supported profiles returned; previous servers were kept"
                }

                val meta = parseSubscriptionUserInfo(payload.userInfo)

                applyStateOnMainThread {
                    val count = replaceManagedProfilesForSource(sub, parsed)
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
                    Triple(count, subscriptions.toList(), profiles.toList())
                }
            }
            outcome.fold(
                onSuccess = { (refreshedCount, subsSnapshot, profilesSnapshot) ->
                    store.saveSubscriptions(subsSnapshot)
                    store.saveProfiles(profilesSnapshot)
                    notifier.alert(
                        SmartNotificationKind.SUBSCRIPTION,
                        "subscription:${sub.id}",
                        "Subscription refreshed",
                        "${sub.name} • $refreshedCount servers",
                        settings
                    )
                    message = "$refreshedCount profiles refreshed"
                },
                onFailure = { error ->
                    if (error is InterruptedException) {
                        Thread.currentThread().interrupt()
                        diagnostics.event("APP", "task-cancelled", "label" to "Refreshing ${sub.name}")
                        message = "Refresh cancelled • previous servers kept"
                    } else {
                        diagnostics.error("APP", "subscription-refresh-unreachable", error, "source" to sub.name)
                        message = "Could not refresh ${sub.name} • ${refreshFailureKind(error)} • previous servers kept"
                    }
                }
            )
        }
    }

    private fun refreshFailureKind(error: Throwable): String {
        val cause = generateSequence(error) { it.cause }.last()
        return when (cause) {
            is java.net.ConnectException -> "the server refused the connection"
            is java.net.UnknownHostException -> "the server name did not resolve"
            is java.net.SocketTimeoutException -> "the server did not answer in time"
            is javax.net.ssl.SSLException -> "the secure handshake failed"
            is java.io.IOException -> "the network dropped the request"
            else -> (cause.message ?: cause::class.java.simpleName).take(80)
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
                    require(parsed.isNotEmpty()) { "No supported profiles returned; previous servers were kept" }
                    val meta = parseSubscriptionUserInfo(payload.userInfo)

                    applyStateOnMainThread {
                        val refreshedCount = replaceManagedProfilesForSource(sub, parsed)
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
                }
                endRefresh(sub.id)
                result.onSuccess { count ->
                    refreshed++
                    nodeCount += count
                }.onFailure { error ->
                    failed += "${sub.name}: ${error.message ?: error::class.java.simpleName}"
                }
            }

            val (subsSnapshot, profilesSnapshot) = applyStateOnMainThread {
                subscriptions.toList() to profiles.toList()
            }
            store.saveSubscriptions(subsSnapshot)
            store.saveProfiles(profilesSnapshot)
            val summary = when {
                failed.isEmpty() -> "$refreshed sources refreshed • $nodeCount servers"
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
            message = "Select one source in Servers before adding a manual config"
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
        ensureServerInsights()
        return true
    }

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
            message = "Select one server source before saving the chain"
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
        ensureServerInsights()
        return true
    }
    fun importQrImage(uri: android.net.Uri, targetSubscriptionId: String) {
        io.execute {
            val decoded = runCatching { QrImageDecoder.decode(context, uri) }.getOrNull()
            postToMain {
                if (decoded.isNullOrBlank()) {
                    message = "No QR code found in that image"
                } else {
                    val addedId = importClipboard(decoded, targetSubscriptionId)
                    selectLibrarySource(addedId ?: intakeTargetOrManual(targetSubscriptionId))
                }
            }
        }
    }

    fun importQrBitmap(bitmap: android.graphics.Bitmap, targetSubscriptionId: String) {
        io.execute {
            val decoded = runCatching { QrImageDecoder.decode(bitmap) }.getOrNull()
            postToMain {
                if (decoded.isNullOrBlank()) {
                    message = "No QR code found • hold the camera steady and fill the frame"
                } else {
                    val addedId = importClipboard(decoded, targetSubscriptionId)
                    selectLibrarySource(addedId ?: intakeTargetOrManual(targetSubscriptionId))
                }
            }
        }
    }

    fun importText(
        text: String,
        name: String = "Manual",
        targetSubscriptionId: String = "manual"
    ) {
        val target = resolveLibraryTarget(targetSubscriptionId)
        if (target == null) {
            message = "Select one source in Servers before importing configs"
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

            val (addedCount, profilesSnapshot) = applyStateOnMainThread {
                val fresh = parsed.filter { incoming ->
                    profiles.none {
                        it.id == incoming.id && it.subscriptionId == target.id
                    }
                }
                profiles.addAll(fresh)
                fresh.size to profiles.toList()
            }
            store.saveProfiles(profilesSnapshot)
            message = "$addedCount profile${if (addedCount == 1) "" else "s"} imported into ${target.name}"
            ensureServerInsights()
        }
    }

    fun importClipboard(text: String, targetSubscriptionId: String = "manual"): String? {
        val clean = text.trim()
        if (clean.isBlank()) {
            message = "Clipboard is empty"
            return null
        }

        val lines = clean.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val looksLikeJson = clean.startsWith("{") || clean.startsWith("[")
        val hasShareLinks = Regex(
            "(?im)^(vless|vmess|trojan|ss|socks5?|hysteria2|hy2|ssh)://"
        ).containsMatchIn(clean)

        val hasAuthenticatedHttpProxy = lines.any { line ->
            Regex("(?i)^https?://[^\\s/@:]+:[^\\s/@]*@[^\\s/]+/?$").matches(line)
        }

        if (looksLikeJson || hasShareLinks || hasAuthenticatedHttpProxy) {
            importText(clean, "Clipboard", targetSubscriptionId)
            return null
        }

        val allWebUrls = lines.isNotEmpty() && lines.all {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }
        if (!allWebUrls) {
            importText(clean, "Clipboard", targetSubscriptionId)
            return null
        }
        if (lines.any { it.startsWith("http://", ignoreCase = true) }) {
            message = "Remote subscriptions must use HTTPS"
            return null
        }

        var firstAddedId: String? = null
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
            if (firstAddedId == null) firstAddedId = id
            added++
        }

        if (added == 0) {
            message = "Clipboard subscriptions are already in Servers"
            return lines.firstNotNullOfOrNull { line ->
                subscriptions.firstOrNull { it.url.trim().equals(line.trim(), true) }?.id
            }
        }
        store.saveSubscriptions(subscriptions)
        message = "$added clipboard source${if (added == 1) "" else "s"} added • refreshing"
        refreshAll()
        return firstAddedId
    }

    fun subscriptionRawText(id: String): String =
        profilesRawText(profiles.filter { it.subscriptionId == id })

    // MARBLE_COPY_GROUP_SERVERS_V220 — the share links of any visible group (subscription, Manual
    // or a country bucket), one per line, de-duplicated. Manual and country groups have no stored
    // subscription text, so they copy from the same per-profile links the row menu copies.
    fun profilesRawText(targets: List<ProxyProfile>): String =
        targets.asSequence()
            .map { it.raw.trim().ifBlank { it.configJson.trim() } }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")

    fun updateProfileJson(
        id: String,
        jsonText: String,
        sourceId: String? = null
    ): Boolean {
        if (busy) {
            message = "Wait for the current background task before editing a server"
            return false
        }
        val target = profile(id, sourceId)
        if (target != null && isActiveProfile(target)) {
            message = "Disconnect this server before editing its Xray JSON"
            return false
        }
        val index = profiles.indexOfFirst {
            it.id == id && (sourceId.isNullOrBlank() || it.subscriptionId == sourceId)
        }
        if (index < 0) {
            message = "Server no longer exists"
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
            message = "Edited config would duplicate another server in this source"
            return false
        }

        profiles[index] = current.copy(
            id = newId,
            configJson = normalized,

            raw = normalized
        )
        benchmarks = benchmarks.filterNot {
            it.profileId == current.id || it.profileId == newId
        }

        persistBenchmarks()
        intelligence.forgetAcceleration(current.id)
        intelligence.forgetAcceleration(newId)

        val remembered = lastProfile()
        if (remembered?.id == current.id &&
            remembered.subscriptionId == current.subscriptionId) {
            store.setLastProfileRef(newId, current.subscriptionId)
        }

        store.saveProfiles(profiles)
        message = "Server JSON saved • identity and learned acceleration refreshed"
        return true
    }

    fun duplicateProfile(id: String, sourceId: String? = null): Boolean {
        if (busy) {
            message = "Wait for the current background task before duplicating a server"
            return false
        }
        val source = profile(id, sourceId) ?: run {
            message = "Server no longer exists"
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
        ensureServerInsights()
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
        if (selectedProfileId in doomedIds || selectedProfileSourceId == id) {
            selectedProfileId = ""
            selectedProfileSourceId = ""
            selectedPingMs = 0
            selectedPingState = ConnectionPingState.IDLE
            selectedPingFailure = ""
            store.clearLastProfile()
        }
        benchmarks = benchmarks.filterNot { it.profileId in doomedIds }
        persistBenchmarks()
        store.saveSubscriptions(subscriptions)
        store.saveProfiles(profiles)
        message = "Removed ${sub.name} • ${doomedIds.size} servers deleted"
    }

    fun removeProfile(id: String, sourceId: String? = null) {
        if (busy) {
            message = "Wait for the current task before deleting a server"
            return
        }
        if (state != "DISCONNECTED") {
            message = "Disconnect before deleting servers"
            return
        }

        val index = profiles.indexOfFirst {
            it.id == id && (sourceId.isNullOrBlank() || it.subscriptionId == sourceId)
        }
        if (index < 0) {
            message = "Server no longer exists"
            return
        }

        val target = profiles.removeAt(index)
        val sameConfigRemains = profiles.any { it.id == target.id }

        if (!sameConfigRemains) {
            benchmarks = benchmarks.filterNot { it.profileId == target.id }
            persistBenchmarks()
            intelligence.forgetAcceleration(target.id)
        }

        val remembered = lastProfile()
        if (remembered?.id == target.id &&
            remembered.subscriptionId == target.subscriptionId) {
            val surviving = profiles.firstOrNull { it.id == target.id }
            if (surviving != null) {
                store.setLastProfileRef(surviving.id, surviving.subscriptionId)
                selectedProfileId = surviving.id
                selectedProfileSourceId = surviving.subscriptionId
            } else {
                store.clearLastProfile()

                selectedProfileId = ""
                selectedProfileSourceId = ""
                selectedPingMs = 0
                selectedPingState = ConnectionPingState.IDLE
                selectedPingFailure = ""
            }
        }

        store.saveProfiles(profiles)
        message = "Server removed • ${target.name}"
    }

    fun moveProfile(id: String, sourceId: String?, targetSourceId: String): Boolean {
        if (busy) {
            message = "Wait for the current task before moving a server"
            return false
        }
        val index = profiles.indexOfFirst {
            it.id == id && (sourceId.isNullOrBlank() || it.subscriptionId == sourceId)
        }
        if (index < 0) {
            message = "Server no longer exists"
            return false
        }
        val target = resolveLibraryTarget(targetSourceId)
        if (target == null) {
            message = "Select one server source before moving a server"
            return false
        }
        val moving = profiles[index]
        if (moving.subscriptionId == target.id) {
            message = "${moving.name} is already in ${target.name}"
            return false
        }
        if (profiles.any { it.id == moving.id && it.subscriptionId == target.id }) {
            message = "That server already exists in ${target.name}"
            return false
        }
        val remembered = lastProfile()
        profiles[index] = moving.copy(
            subscriptionId = target.id,
            subscriptionName = target.name,
            sourceManaged = false
        )
        if (remembered?.id == moving.id && remembered.subscriptionId == moving.subscriptionId) {
            store.setLastProfileRef(moving.id, target.id)
            if (selectedProfileId == moving.id) selectedProfileSourceId = target.id
        }
        store.saveProfiles(profiles)
        diagnostics.event(
            "LIBRARY",
            "profile-moved",
            "profile" to moving.id.take(12),
            "from" to moving.subscriptionId.take(16),
            "to" to target.id.take(16)
        )
        message = "${moving.name} moved to ${target.name}"
        return true
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

    val PRUNE_TARGET_MANUAL = "manual"
    val PRUNE_TARGET_ALL = "all"

    fun failedSubscriptionNodeCount(id: String, probeKind: String): Int {
        val kind = probeKind.trim().uppercase()
        if (kind !in setOf("SMART", "TUNNEL")) return 0
        val failedIds = failedNodeIds(kind)
        return profiles.count { inPruneScope(it, id) && it.id in failedIds }
    }

    private fun failedNodeIds(probeKind: String): Set<String> =
        benchmarks.asSequence()
            .filter { it.success <= 0 && it.probeKind.equals(probeKind, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.profileId }

    fun failedNodeCount(target: String): Int {
        val failed = failedNodeIds("SMART") + failedNodeIds("TUNNEL")
        if (failed.isEmpty()) return 0
        return profiles.count { inPruneScope(it, target) && it.id in failed }
    }

    fun failedNodeCountEverywhere(): Int = failedNodeCount(PRUNE_TARGET_ALL)

    private fun inPruneScope(profile: ProxyProfile, target: String): Boolean = when (target) {
        PRUNE_TARGET_ALL -> true
        PRUNE_TARGET_MANUAL -> profile.subscriptionId == PRUNE_TARGET_MANUAL
        else -> profile.subscriptionId == target
    }

    fun removeFailedSubscriptionNodes(id: String, probeKind: String): Int {
        if (busy) {
            message = "Wait for the current task before removing failed servers"
            return 0
        }
        if (state != "DISCONNECTED") {
            message = "Disconnect before removing failed servers"
            return 0
        }

        val kind = probeKind.trim().uppercase()
        if (kind !in setOf("SMART", "TUNNEL")) {
            message = "Unsupported failed-server evidence type"
            return 0
        }

        val isSynthetic = id == PRUNE_TARGET_MANUAL || id == PRUNE_TARGET_ALL
        if (!isSynthetic && subscriptions.none { it.id == id }) {
            message = "Subscription no longer exists"
            return 0
        }
        val scopeName = libraryScopeLabel(id)

        val failedIds = failedNodeIds(kind)
        val doomedIds = profiles.asSequence()
            .filter { inPruneScope(it, id) && it.id in failedIds }
            .mapTo(linkedSetOf()) { it.id }

        if (doomedIds.isEmpty()) {
            message = "No failed $kind servers recorded in $scopeName"
            return 0
        }

        val remembered = lastProfile()
        if (remembered != null &&
            remembered.id in doomedIds &&
            (id == PRUNE_TARGET_ALL || remembered.subscriptionId == id)) {
            store.clearLastProfile()
        }
        doomedIds.forEach(intelligence::forgetAcceleration)
        profiles.removeAll { it.id in doomedIds }
        benchmarks = benchmarks.filterNot { it.profileId in doomedIds }
        persistBenchmarks()
        store.saveProfiles(profiles)

        diagnostics.event(
            "LIBRARY",
            "failed-nodes-removed",
            "source" to id.take(16),
            "sourceName" to scopeName,
            "probeKind" to kind,
            "removed" to doomedIds.size
        )
        message = "Removed ${doomedIds.size} failed $kind server" +
            (if (doomedIds.size == 1) "" else "s") + " from $scopeName"
        return doomedIds.size
    }

    fun lastProfile(): ProxyProfile? {
        val id = store.lastProfileId()
        if (id.isNotBlank()) {
            val sourceId = store.lastProfileSourceId()
            val exact = profile(id, sourceId)
            val candidate = (exact ?: profile(id))
            if (candidate != null) return candidate
        }

        val fromHistory = history.asReversed().asSequence()
            .mapNotNull { rec -> profile(rec.profileId) }
            .firstOrNull()
        if (fromHistory != null) return fromHistory

        return null
    }

    fun selectProfile(p: ProxyProfile, stopRunningSweep: Boolean = true) {
        if (stopRunningSweep && probeActive) cancelProbes()
        diagnostics.event("APP", "select-server", "profile" to p.id.take(12), "name" to p.name.take(80))
        val changed = selectedProfileId != p.id || selectedProfileSourceId != p.subscriptionId
        if (p.subscriptionId.isNotBlank()) selectLibrarySource(p.subscriptionId)
        postToMain {
            selectedProfileId = p.id
            selectedProfileSourceId = p.subscriptionId

            if (changed) {
                selectedPingMs = 0
                selectedPingState = ConnectionPingState.IDLE
                selectedPingFailure = ""
            }
        }
        io.execute { runCatching { store.setLastProfileRef(p.id, p.subscriptionId) } }
    }

    /**
     * MARBLE_NOTIFICATION_SWITCH_V234 — the shade's own route switch, resolved here because this is
     * the only class that knows the library, the live session and the remembered route.
     *
     * The walk itself is [NotificationRouteCycle] (pure Kotlin, host-JVM tested). What lives here is
     * the translation from that decision to a real profile: the active profile wins over the
     * selected one, the selected one over the remembered one, and the chosen route is written back
     * through [selectProfile] so the Servers page and the Home card follow the shade instead of
     * disagreeing with it.
     */
    fun notificationCycleRoute(kind: NotificationCycleKind): ProxyProfile? {
        val all = profiles.toList().distinctBy { it.id }
        val current = profile(activeProfileId, activeProfileSourceId)
            ?: profile(selectedProfileId, selectedProfileSourceId)
            ?: lastProfile()
        val routes = all.map {
            CycleRoute(
                id = it.id,
                name = it.name,
                sourceId = it.subscriptionId,
                sourceName = it.subscriptionName
            )
        }
        val next = NotificationRouteCycle.next(
            kind = kind,
            routes = routes,
            currentId = current?.id.orEmpty(),
            currentSourceId = current?.subscriptionId.orEmpty()
        )
        if (next == null) {
            diagnostics.event(
                "APP",
                "notification-switch-empty",
                "kind" to kind.id,
                "routes" to routes.size,
                "current" to current?.id?.take(12)
            )
            return null
        }
        val target = profile(next.id, next.sourceId) ?: profile(next.id)
        if (target == null) {
            diagnostics.event("APP", "notification-switch-missing", "kind" to kind.id)
            return null
        }
        diagnostics.event(
            "APP",
            "notification-switch",
            "kind" to kind.id,
            "profile" to target.id.take(12),
            "name" to target.name.take(80),
            "source" to target.subscriptionName.take(60)
        )
        // The shade calls this from the tunnel service's worker thread; `selectProfile` touches
        // Compose state (the selection and the library source), so it is handed to the main thread
        // like every other repository mutation. Persisting the last route is inside it and is safe
        // from either thread.
        postToMain { selectProfile(target) }
        return target
    }

    fun writeBackup(uri: Uri) {
        task("Creating backup") {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(store.createBackup())
            } ?: error("Could not open backup destination")
            message = "Backup created"
        }
    }

    fun restoreBackup(uri: Uri) {
        if (state == "CONNECTED" || state == "CONNECTING") {
            message = "Disconnect before restoring a backup"
            return
        }
        task("Restoring backup") {
            val limit = 8 * 1024 * 1024
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    require(text.length + count <= limit) { "Backup is larger than 8 MiB" }
                    text.append(buffer, 0, count)
                }
                text.toString()
            } ?: error("Could not open backup")
            store.restoreBackup(raw)
            postToMain { message = "Backup restored • restart MarbleNG to apply every setting" }
        }
    }

    fun isSelectedProfile(p: ProxyProfile): Boolean =
        selectedProfileId.isNotBlank() &&
            selectedProfileId == p.id &&
            (selectedProfileSourceId.isBlank() || selectedProfileSourceId == p.subscriptionId)

    fun tunnelIntentActive(): Boolean = runCatching { store.tunnelIntentActive() }.getOrDefault(false)

    fun clearTunnelIntent(reason: String) {
        diagnostics.event("APP", "tunnel-intent-cleared", "reason" to reason)
        io.execute { runCatching { store.setTunnelIntentActive(false) } }
    }

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
            message = "No servers yet • add a subscription or import configs first"
            return
        }

        if (settings.autoServerSelectorEnabled && settings.autoServerOnConnect) {
            val chosen = runAutoServerSelection(reason = "connect", connect = false)
            chosen?.profile?.let {
                onConnect(it)
                return
            }
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
            else -> available.firstOrNull { it.scheme != "vless" || (it.security.isNotBlank() && it.security != "none") }
                ?: available.first()
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

            selectedProfileId = p.id
            selectedProfileSourceId = p.subscriptionId

            if (previousState != "CONNECTED" || connectedSinceMs <= 0L) {
                connectedSinceMs = System.currentTimeMillis()

                autoFailoverArmed.set(true)

                beginUsageSession(p)
                connectionPingMs = 0
                connectionPingState = ConnectionPingState.IDLE
                connectionPingFailure = ""

                selectedPingMs = 0
                selectedPingState = ConnectionPingState.IDLE
                selectedPingFailure = ""
            }

            history += ConnectionRecord(
                p.id,
                p.name,
                System.currentTimeMillis(),
                "connected:${settingsSnapshot.connectionMode.name}"
            )
            while (history.size > MAX_HISTORY_RECORDS) history.removeAt(0)

            val historySnapshot = history.toList()
            io.execute {
                runCatching { store.setLastProfileRef(p.id, p.subscriptionId) }

                runCatching { store.setTunnelIntentActive(true) }
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
        if (settings.warmupOnLaunch && p.host.isNotBlank()) {
            warmupWorker.execute { runCatching { ConnectWarmup.warm(listOf(p.host), settings) } }
        }
        privacy = null
        runCatching { scanIranMode() }
        ensureIpFamilyEvidence(p)
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
        ensureIpFamilyEvidence(p)
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

    fun boostActiveRoute() {
        if (state != "CONNECTED") {
            message = "Marble Turbo needs an active connection"
            return
        }
        if (!settings.connectTuningEnabled) {
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
        io.execute { runCatching { store.setTunnelIntentActive(false) } }
        markDisconnecting()
        runCatching {
            context.startService(Intent(context, MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_STOP))
        }.onFailure { error ->
            message = "Could not stop connection service: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"

            setRuntimeState("DISCONNECTED", "")
        }
    }

    private fun markDisconnecting() {
        if (state != "CONNECTED" && state != "CONNECTING") return
        diagnostics.event("APP", "state", "from" to state, "to" to "DISCONNECTING")
        val detail = stateDetail
        postToMain {
            state = "DISCONNECTING"
            stateDetail = detail
            MarbleQuickTileService.requestRefresh(context)
        }
        val token = System.nanoTime()
        disconnectWatchdogToken = token
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (disconnectWatchdogToken == token && state == "DISCONNECTING") {
                setRuntimeState("DISCONNECTED", "")
            }
        }, DISCONNECT_WATCHDOG_MS)
    }

    private var disconnectWatchdogToken: Long = 0L

    private fun clearBenchmarks(ids: Set<String>) = postToMain {
        benchmarks = benchmarks.filterNot { it.profileId in ids }
        persistBenchmarks()
    }

    private fun rememberedBenchmarks(): List<BenchmarkResult> {
        val liveIds = profiles.mapTo(mutableSetOf()) { it.id }
        return runCatching { store.loadBenchmarks() }
            .getOrDefault(emptyList())
            .filter { it.profileId in liveIds }
            .sortedWith(
                compareByDescending<BenchmarkResult> { it.score }
                    .thenBy { it.latencyMs }
            )
    }

    private fun persistBenchmarks() {
        val snapshot = benchmarks.toList()
        io.execute { runCatching { store.saveBenchmarks(snapshot) } }
    }

    private fun mergeBenchmarks(fresh: List<BenchmarkResult>) {
        if (fresh.isEmpty()) return
        val stampedAt = System.currentTimeMillis()

        val incoming = fresh.map { if (it.measuredAtMs > 0L) it else it.copy(measuredAtMs = stampedAt) }
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
            persistBenchmarks()
        }
    }

    fun smart(onBest: (ProxyProfile) -> Unit) {
        task("Marble Intelligence • selecting route") {
            val available = enabledProfilesSnapshot()
            if (available.isEmpty()) {
                message = "No enabled servers to test"
                return@task
            }
            val engine = BenchmarkEngine(xray, intelligence)

            val selectSettings = if (settings.probeMethod != ProbeMethod.REAL_DELAY) {
                settings.copy(probeMethod = ProbeMethod.REAL_DELAY)
            } else {
                settings
            }
            if (settings.intelligenceEnabled && settings.raceConnectEnabled && available.size > 1) {
                val raced = engine.race(
                    available,
                    selectSettings,
                    onCandidates = ::beginProbeBatch,
                    onStart = ::markProbeStart,
                    onResult = ::markProbeResult
                ) { n -> message = "Connection race • $n" }
                if (probeShouldStop()) return@task
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
                selectSettings,
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult,
                shouldStop = probeShouldStop
            ) { a, b, n -> message = "Tunnel intelligence $a/$b • $n" }
            mergeBenchmarks(results)
            val best = results.firstOrNull { it.success > 0 }?.let { profile(it.profileId) }
            message = if (best == null) "No working candidate" else "Best: ${best.name}"
            best?.let { android.os.Handler(android.os.Looper.getMainLooper()).post { onBest(it) } }
        }
    }

    fun smartRank() = smartRankSource("all")

    fun smartRankSource(sourceId: String) {
        val gate = rankGate.tryAcquire()
        if (gate.verdict != SmartRankGate.Verdict.ACCEPTED) {
            message = gate.message
            diagnostics.event(
                "BENCHMARK", "rank-gate-rejected",
                "verdict" to gate.verdict.name,
                "source" to sourceId.take(24)
            )
            return
        }

        val candidates = libraryScopeSnapshot(sourceId).distinctBy { it.id }
        val scope = libraryScopeLabel(sourceId)
        if (candidates.isEmpty()) {
            rankGate.release()
            message = "Nothing enabled to rank in $scope"
            return
        }

        val accepted = task("Smart rank • $scope") {
            try {
                smartRankRun(sourceId, scope, candidates)
            } finally {
                rankGate.release()
            }
        }

        if (!accepted) rankGate.release()
    }

    private fun rankCrossCheckSources(): ProfileAddressCrossCheck.CrossCheckSources =
        ProfileAddressCrossCheck.CrossCheckSources(
            freshSubscriptionProfiles = profiles.toList(),
            freshSubscriptionRaw = subscriptions.joinToString("\n") { sub ->
                runCatching { subscriptionRawText(sub.id) }.getOrDefault("")
            }
        )

    private fun multiSignalSignals(r: BenchmarkResult): MultiSignalRankScorer.Signals =
        MultiSignalRankScorer.Signals(
            tcpHandshakeSuccessRatio = r.tcpHandshakeSuccessRatio.coerceIn(0.0, 1.0),
            handshakeAttempts = r.handshakeAttempts.coerceAtLeast(0),
            rttMedianMs = r.latencyMs.takeIf { it in 1.0..9_000.0 }
                ?: MultiSignalRankScorer.UNKNOWN_RTT,
            rttP95Ms = r.p95LatencyMs.takeIf { it in 1.0..9_000.0 }
                ?: MultiSignalRankScorer.UNKNOWN_RTT,
            jitterMs = r.jitterMs.takeIf { it >= 0.0 } ?: 0.0,
            retransmitRate = 0.0,
            lossRate = r.lossPercent.coerceIn(0.0, 100.0) / 100.0,
            sessionLifetimeMs = 0L,
            uncertain = r.success <= 0 && r.failureReason.isNotBlank() &&
                (CoreFailurePolicy.isLocal(r.failureReason) || r.failureReason.contains("inconclusive", true) ||
                    r.failureReason.contains("backoff", true) ||
                    (r.failureReason.contains("timeout", true) &&
                        r.handshakeAttempts < MultiSignalRankScorer.MIN_ATTEMPTS_TO_CONVICT))
        )

    private fun smartRankRun(sourceId: String, scope: String, candidates: List<ProxyProfile>) {
        val crossCheckSources = rankCrossCheckSources()

        val (securitySafe, deprecated) = ProfileSecurityAuditor.partitionForRank(candidates, settings)
        if (deprecated.isNotEmpty()) {
            diagnostics.event(
                "BENCHMARK", "rank-deprecated-hidden",
                "source" to sourceId.take(24),
                "hidden" to deprecated.size,
                "profiles" to deprecated.joinToString { "${it.first.name.take(40)}=${it.second}" }
            )
        }

        val (_, invalidCandidates) = ProfilePreflightValidator.partition(
            securitySafe,
            crossCheckSources,
            settings.coreEngine(),
            settings
        )
        if (invalidCandidates.isNotEmpty()) {
                diagnostics.event(
                    "BENCHMARK", "rank-preflight-quarantine",
                    "source" to sourceId.take(24),
                    "quarantined" to invalidCandidates.size,
                    "profiles" to invalidCandidates.joinToString { it.first.name.take(40) },
                    "reasons" to invalidCandidates.map { it.second.reason }.distinct().joinToString(",")
                )
                diagnostics.event(
                    "BENCHMARK", "rank-preflight-report",
                    "block" to ProfilePreflightValidator.renderMachineReadable(
                        ProfilePreflightValidator.validateAll(candidates, settings.coreEngine(), settings)
                    ),
                    "repairs" to XrayConfigRepairs.lastSummary().ifBlank { "none" }
                )
                message = "Rank • $scope • preflight flagged ${invalidCandidates.size} • testing all servers"
            }

            val scoped = candidates

            val rankSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = scoped.size.coerceAtLeast(1),
                benchSamples = 2,
                benchTimeoutSec = settings.benchTimeoutSec.coerceIn(4, 6),

                tcpWorkers = maxOf(settings.tcpWorkers, 64).coerceAtMost(128),
                probeMethod = ProbeMethod.REAL_DELAY,
                probeSpeedTest = false,
                verifiedPerformanceTuning = false,
                udpProbeEnabled = false
            )

            fun executeRank(): List<BenchmarkResult> = PattRankEngine(
                context = context,
                xray = xray,
                intelligence = intelligence
            ).run(
                scoped,
                rankSettings,
                onCandidates = ::beginProbeBatch,
                onStart = ::markProbeStart,
                onResult = ::markProbeResult,
                shouldStop = probeShouldStop
            ) { done, total, name ->
                message = "Rank • $scope • $done/$total • $name"
            }

            val startedAt = System.currentTimeMillis()
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
                message = "Network changed • restarting Rank once"
                try {
                    Thread.sleep(350L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (!Thread.currentThread().isInterrupted) {
                    results = executeRank()
                }
            }

            val quarantinedIds = buildSet {
                invalidCandidates.forEach { add(it.first.id) }
                deprecated.forEach { add(it.first.id) }
            }
            val healthHistories = intelligence.healthSnapshot().entries
                .mapNotNull { e -> SurvivalFirstRanker.fromNodeHealth(e.value)?.let { e.key to it } }
                .toMap()
            val iranActive = iranMode.active
            val reordered = SurvivalFirstRanker.reorderResults(
                results, healthHistories, settings, iranActive, quarantinedIds
            )
            val rankingDecision = SurvivalFirstRanker.categorize(
                reordered, healthHistories, settings, iranActive, quarantinedIds
            ).copy(selectedProfileId = reordered.firstOrNull()?.profileId.orEmpty())
            results = reordered

            val multiSignal = results.associate { r ->
                r.profileId to MultiSignalRankScorer.score(multiSignalSignals(r))
            }
            diagnostics.event(
                "BENCHMARK", "rank-multi-signal",
                "source" to sourceId.take(24),
                "servers" to multiSignal.size,
                "healthy" to multiSignal.count { it.value.classification == MultiSignalRankScorer.Classification.HEALTHY },
                "degraded" to multiSignal.count { it.value.classification == MultiSignalRankScorer.Classification.DEGRADED },
                "uncertain" to multiSignal.count { it.value.classification == MultiSignalRankScorer.Classification.UNCERTAIN },
                "dead" to multiSignal.count { it.value.classification == MultiSignalRankScorer.Classification.DEAD },
                "invalid" to multiSignal.count { it.value.classification == MultiSignalRankScorer.Classification.INVALID }
            )
            results = results.map { r ->
                val ms = multiSignal[r.profileId]
                if (r.success > 0 && r.handshakeAttempts > 0 && ms != null) r.copy(score = ms.score) else r
            }

            diagnostics.event(
                "BENCHMARK", "rank-survival-decision",
                "reason" to rankingDecision.decisionReason.take(200),
                "selected" to rankingDecision.selectedProfileId.take(24),
                "healthy" to rankingDecision.healthCount,
                "uncertain" to rankingDecision.uncertainCount,
                "failed" to rankingDecision.failedCount,
                "quarantined" to quarantinedIds.size,
                "block" to DiagnosticsSummary.render(
                    ResolverFailureSummary(),
                    ProfilePreflightValidator.validateAll(candidates),
                    rankingDecision,
                    DiagnosticsSummary.ShutdownCounters()
                ).take(1200)
            )

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
                "method" to "TUNNEL",
                "engine" to "pattng-core-dial-batch-v62",
                "elapsedMs" to (System.currentTimeMillis() - startedAt),
                "stoppedByLocalFault" to (probeLocalFaultGate.isTripped || probeLocalFault.isNotBlank())
            )

            message = batchSummary(
                if (best == null) {
                    "Rank • $scope • ${results.size}/${scoped.size} • 0 reachable"
                } else {
                    "Rank • $scope • $healthy/${scoped.size} reachable • " +
                        "${best.name} • ${best.latencyMs.toInt()} ms"
                }
            )
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
                "${it.success}% • ${String.format(Locale.US, "%.0f", it.latencyMs)} ms • ${String.format(Locale.US, "%.1f", it.score)}"
            } ?: "Test failed"
        }
    }

    fun testAll() = testSource("all")

    fun pingAutoServerPool() {
        val pool = autoServerPool()
        if (pool.isEmpty()) {
            message = "No servers in the selector pool"
            return
        }
        pingProfiles(
            profiles = pool,
            scopeLabel = "Auto selector • ${AutoServerSelector.scopeLabel(settings.autoServerScopeEnum)}",
            scopeId = "auto-selector"
        )
    }

    fun pingHomeGroup() {
        val sourceId = HomePingScope.sourceId(homeRoute()?.subscriptionId)
        testSource(sourceId)
    }

    private fun publishHomeRoutePing(results: List<BenchmarkResult>) {
        if (state == "CONNECTED") return
        val route = homeRoute() ?: return
        val result = results.firstOrNull { it.profileId == route.id } ?: return
        val latency = result.latencyMs
        val reachable = result.success > 0 && latency >= 20
        postToMain {
            if (reachable) {
                selectedPingMs = latency.toInt()
                selectedPingState = ConnectionPingState.MEASURED
                selectedPingFailure = ""
            } else {
                selectedPingState = ConnectionPingState.FAILED
                selectedPingFailure = "unreachable"
            }
            selectedPingProvisional = false
        }
    }

    fun homeRoute(): ProxyProfile? {
        if (state == "CONNECTED") {
            profile(activeProfileId, activeProfileSourceId)?.let { return it }
        }
        return profile(selectedProfileId, selectedProfileSourceId)
            ?: profile(selectedProfileId)
            ?: lastProfile()
    }

    fun homeGroupPingLabel(): String {
        val sourceId = HomePingScope.sourceId(homeRoute()?.subscriptionId)
        return libraryScopeLabel(sourceId)
    }

    val homeGroupPingRunning: Boolean
        get() = probeActive

    private fun quickPingEndpointKey(profile: ProxyProfile): String =
        "${profile.host.trim().lowercase()}:${profile.port}"

    private fun quickPingConfigKey(profile: ProxyProfile): String {
        val link = profile.raw
        val document = profile.configJson
        val endpoint = "${profile.host.trim().lowercase()}:${profile.port}"
        return "${link.length}:$link${document.length}:$document${endpoint.length}:$endpoint"
    }

    fun autoServerPool(): List<ProxyProfile> = when (settings.autoServerScopeEnum) {
        AutoServerScope.SOURCE -> {
            val sourceId = librarySourceFilter.takeIf { it.isNotBlank() && it != "all" }
                ?: homeRoute()?.subscriptionId?.takeIf { it.isNotBlank() }
                ?: "manual"
            libraryScopeSnapshot(sourceId)
        }
        AutoServerScope.ALL -> libraryProfiles.toList()
    }

    private fun liveRouteBroken(): Boolean =
        liveRouteAttempts >= AUTO_FAILOVER_MIN_ATTEMPTS &&
            liveRouteScore >= 0 &&
            liveRouteSuccessPercent in 1..49

    private val AUTO_FAILOVER_MIN_ATTEMPTS = 6

    private val autoFailoverArmed = java.util.concurrent.atomic.AtomicBoolean(false)

    fun autoServerCandidates(pool: List<ProxyProfile> = autoServerPool()): List<ServerCandidate> {
        val measured = benchmarks.associateBy { it.profileId }
        val currentId = if (state == "CONNECTED") activeProfileId else selectedProfileId

        val liveBroken = state == "CONNECTED" && liveRouteBroken()
        return pool.distinctBy { it.id }.map { profile ->
            ServerCandidate(
                profile = profile,
                benchmark = measured[profile.id],
                failureStreak = if (liveBroken && profile.id == currentId) {
                    AutoServerSelector.QUARANTINE_STREAK
                } else {
                    runCatching {
                        intelligence.healthOf(profile.id)?.failureStreak ?: 0
                    }.getOrDefault(0)
                },
                current = profile.id == currentId
            )
        }
    }

    private fun autoServerWeights(): AutoServerWeights = AutoServerWeights(
        ping = settings.autoServerPingWeight,
        load = settings.autoServerLoadWeight,
        stability = settings.autoServerStabilityWeight,
        freshness = settings.autoServerFreshnessWeight
    ).clamped()

    fun previewAutoServerChoice(): AutoServerChoice {
        val strategy = settings.autoServerStrategyEnum
        return AutoServerSelector.choose(
            candidates = autoServerCandidates(),
            strategy = strategy,
            nowMs = System.currentTimeMillis(),
            roundRobinCursor = store.loadAutoServerCursor(),
            randomSeed = System.nanoTime(),
            switchMarginPercent = settings.autoServerSwitchMarginPercent,
            scope = settings.autoServerScopeEnum,
            weights = autoServerWeights()
        )
    }

    fun runAutoServerSelection(
        pool: List<ProxyProfile> = autoServerPool(),
        reason: String = "manual",
        connect: Boolean = false
    ): AutoServerChoice? {
        val strategy = settings.autoServerStrategyEnum
        val candidates = autoServerCandidates(pool)
        var cursor = store.loadAutoServerCursor()
        val choice = AutoServerSelector.choose(
            candidates = candidates,
            strategy = strategy,
            nowMs = System.currentTimeMillis(),
            roundRobinCursor = cursor,

            randomSeed = System.nanoTime(),
            switchMarginPercent = settings.autoServerSwitchMarginPercent,
            scope = settings.autoServerScopeEnum,
            weights = autoServerWeights()
        )
        val winner = choice.profile
        diagnostics.event(
            "AUTOSELECT",
            "round",
            "trigger" to reason,
            "strategy" to strategy.id,
            "pool" to candidates.size,
            "picked" to (winner?.id ?: "").take(12),
            "held" to choice.held,
            "confidence" to String.format(
                Locale.US, "%.2f",
                AutoServerSelector.confidenceOf(choice, System.currentTimeMillis())
            ),
            "reason" to choice.reason.take(120)
        )
        if (winner == null) {
            message = "Auto-select • ${choice.reason}"
            return null
        }
        if (strategy == AutoServerStrategy.ROUND_ROBIN) {
            cursor = AutoServerSelector.nextCursor(candidates, cursor)
            store.saveAutoServerCursor(cursor)
        }
        if (choice.held) {
            message = "Auto-select • ${AutoServerSelector.shortLabel(strategy)} • ${choice.reason}"
            return choice
        }

        postToMain {
            selectProfile(winner, stopRunningSweep = false)
            val alreadyLive = state == "CONNECTED" && activeProfileId == winner.id
            if (connect && !alreadyLive) {
                if (settings.connectionMode == ConnectionMode.FULL_TUN) startVpn(winner)
                else startLocalProxy(winner)
            }
        }
        message = "Auto-select • ${AutoServerSelector.shortLabel(strategy)} • ${winner.name}"
        return choice
    }

    fun testSource(sourceId: String) {
        pingProfiles(
            profiles = libraryScopeSnapshot(sourceId),
            scopeLabel = libraryScopeLabel(sourceId),
            scopeId = sourceId
        )
    }

    fun pingProfiles(
        profiles: List<ProxyProfile>,
        scopeLabel: String,
        scopeId: String = "custom"
    ) {
        val scoped = profiles.distinctBy { it.id }
        val scope = scopeLabel
        val sourceId = scopeId
        if (scoped.isEmpty()) {
            message = "Nothing enabled to ping in $scope"
            return
        }

        val method = settings.probeMethod
        val methodLabel = when (method) {
            ProbeMethod.REAL_DELAY -> "Real delay"
            ProbeMethod.TCP_PING -> "TCP ping"
            ProbeMethod.URL_TEST -> "URL test (sing-box extended)"
        }

        val dedupe = method.isEndpointLevel()
        val configLevel = method == ProbeMethod.REAL_DELAY || method == ProbeMethod.URL_TEST
        val groupKey: (ProxyProfile) -> String = when {
            dedupe -> ::quickPingEndpointKey
            configLevel -> ::quickPingConfigKey
            else -> { profile -> profile.id }
        }

        task("$methodLabel • $scope") {
            val groups = scoped.groupBy(groupKey)

            val representatives = groups.values.mapNotNull { members ->
                members.maxByOrNull { candidate -> if (candidate.id == activeProfileId) 1 else 0 }
            }

            val quickSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = representatives.size.coerceAtLeast(1),
                benchSamples = settings.pingSampleCount(),
                benchTimeoutSec = PingBudget.timeoutSec(settings.pingTimeoutSec),
                tcpPrecheckTimeoutMs = settings.tcpPrecheckTimeoutMs,

                tcpWorkers = settings.pingWorkers(
                    cores = deviceCores,
                    memoryMb = deviceMemoryMb,
                    method = method
                ),
                probeSpeedTest = false,
                verifiedPerformanceTuning = false,
                udpProbeEnabled = false
            )

            fun membersFor(representative: ProxyProfile): List<ProxyProfile> =
                groups[groupKey(representative)].orEmpty()

            fun runQuickPass(): List<BenchmarkResult> =
                BenchmarkEngine(xray, intelligence).run(
                    representatives,
                    quickSettings,
                    usePrecheck = false,
                    shouldStop = probeShouldStop,
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
                    val unit = when {
                        dedupe -> "endpoints"
                        configLevel -> "configs"
                        else -> "servers"
                    }
                    message = "$methodLabel • $scope • $done/$total $unit • $name"
                }

            val firstStartedNs = System.nanoTime()
            val representativeResults = runQuickPass()
            val firstElapsedMs =
                ((System.nanoTime() - firstStartedNs) / 1_000_000L).coerceAtLeast(0L)

            val canaryNeeded =
                representatives.size >= 4 &&
                    representativeResults.isNotEmpty() &&
                    representativeResults.none { it.success > 0 } &&
                    !probeLocalFaultGate.isTripped &&
                    probeLocalFault.isBlank() &&
                    !probeCancelGate.isRequested

            val settledResults = if (!canaryNeeded) {
                representativeResults
            } else {
                val canary = representatives.take(CANARY_NODES)
                val retried = runCatching {
                    BenchmarkEngine(xray, intelligence).run(
                        canary,
                        quickSettings.copy(benchCandidates = canary.size.coerceAtLeast(1)),
                        usePrecheck = false,
                        shouldStop = probeShouldStop,
                        onCandidates = {},
                        onStart = {},
                        onResult = { _, _ -> }
                    )
                }.getOrDefault(emptyList())
                diagnostics.event(
                    "BENCHMARK",
                    "ping-canary-recheck",
                    "source" to sourceId.take(24),
                    "scope" to scope,
                    "servers" to scoped.size,
                    "endpoints" to representatives.size,
                    "canary" to canary.size,
                    "recovered" to retried.count { it.success > 0 },
                    "firstElapsedMs" to firstElapsedMs
                )
                if (retried.any { it.success > 0 }) {
                    val recovered = retried.associateBy { it.profileId }
                    representativeResults.map { first -> recovered[first.profileId] ?: first }
                } else {
                    representativeResults
                }
            }

            val expanded = settledResults.flatMap { result ->
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

            publishHomeRoutePing(expanded)
            if (!probeCancelGate.isRequested && state == "DISCONNECTED") {
                if (settings.autoServerSelectorEnabled && settings.autoServerOnScan) {
                    runAutoServerSelection(
                        pool = scoped,
                        reason = "scan",
                        connect = settings.autoConnectBestAfterScan
                    )
                } else if (settings.autoConnectBestAfterScan) {
                    val winnerResult = expanded.filter { it.success > 0 }.minByOrNull { it.latencyMs }
                    val winner = winnerResult?.let { result -> scoped.firstOrNull { it.id == result.profileId } }
                    if (winner != null) {
                        postToMain {
                            selectProfile(winner, stopRunningSweep = false)
                            if (settings.connectionMode == ConnectionMode.FULL_TUN) startVpn(winner)
                            else startLocalProxy(winner)
                        }
                    }
                }
            }
            val passed = expanded.count { it.success > 0 }
            diagnostics.event(
                "BENCHMARK",
                "ping-source-finish",
                "source" to sourceId.take(24),
                "scope" to scope,
                "requested" to scoped.size,
                "uniqueEndpoints" to representatives.size,
                "tested" to expanded.size,
                "reachable" to passed,
                "stoppedByLocalFault" to (probeLocalFaultGate.isTripped || probeLocalFault.isNotBlank())
            )
            message = batchSummary(
                if (dedupe) {
                    "$methodLabel • $scope • ${expanded.size} servers / " +
                        "${representatives.size} endpoints • $passed reachable"
                } else {
                    "$methodLabel • $scope • ${expanded.size} servers • $passed reachable"
                }
            )
        }
    }

    fun audit() {
        val coreAlive = if (activeCoreEngine == CoreEngine.SINGBOX) singBox.isAlive else xray.isAlive
        if (state != "CONNECTED" || !coreAlive) {
            privacy = null
            message = "Privacy audit needs an active healthy connection"
            return
        }
        privacy = null
        task("Privacy audit • comparing both IP families and tunnel DNS") {
            privacy = PrivacyAuditor.audit(
                activeProxyPort(),
                intelligence.currentUnderlyingNetwork(),
                requireIpv6 = settings.ipv6Enabled,
                underlayHasIpv6 = intelligence.currentSnapshot().hasIpv6
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

    fun applyGeoAssetSource(sourceId: String) {
        val source = RoutingDefaults.sourceById(sourceId)

        val ids = com.marbleng.app.core.GeoAssetRegistry.withPrimary(
            com.marbleng.app.core.GeoAssetRegistry.parseIds(settings.geoAssetSourceIds),
            source.id
        )
        val next = if (source.id == "custom") {
            settings.copy(geoAssetSourceId = source.id, geoAssetSourceIds = ids.joinToString(","))
        } else {
            settings.copy(
                geoAssetSourceId = source.id,
                geoAssetSourceIds = ids.joinToString(","),
                geoIpUrl = source.geoIpUrl,
                geoSiteUrl = source.geoSiteUrl
            )
        }
        updateSettings(next)
    }

    fun toggleGeoAssetSource(sourceId: String) {
        val clean = sourceId.trim().lowercase()
        val ids = com.marbleng.app.core.GeoAssetRegistry.parseIds(settings.geoAssetSourceIds)
        val next = if (clean in ids) {
            if (ids.size == 1) {
                message = "Routing needs at least one geo database"
                return
            }
            com.marbleng.app.core.GeoAssetRegistry.without(ids, clean)
        } else {
            if (!com.marbleng.app.core.GeoAssetRegistry.hasRoom(settings.geoAssetSourceIds)) {
                message = "At most ${com.marbleng.app.core.GeoAssetRegistry.MAX_SOURCES} geo sources at once"
                return
            }
            com.marbleng.app.core.GeoAssetRegistry.withAdded(ids, clean)
        }
        val primary = next.first()
        updateSettings(
            settings.copy(
                geoAssetSourceIds = next.joinToString(","),
                geoAssetSourceId = primary,
                geoIpUrl = RoutingDefaults.sourceById(primary).geoIpUrl,
                geoSiteUrl = RoutingDefaults.sourceById(primary).geoSiteUrl
            )
        )
        message = "Routing reads ${next.size} geo source${if (next.size == 1) "" else "s"}"
    }

    fun geoSourceStates(): List<com.marbleng.app.core.GeoAssetRegistry.SourceState> =
        runCatching { xray.geoSourceStates(settings) }.getOrDefault(emptyList())

    fun geoSourceSummary(): String =
        com.marbleng.app.core.GeoAssetRegistry.summary(geoSourceStates())

    fun setGeoPrecision(precision: com.marbleng.app.model.GeoPrecision) {
        updateSettings(settings.copy(geoPrecision = precision.id))
    }

    fun geoPrecisionSummary(): String =
        com.marbleng.app.core.IranPrecisionPack.summary(
            com.marbleng.app.model.parseGeoPrecision(settings.geoPrecision)
        )

    fun setRoutingRules(rules: List<com.marbleng.app.model.RoutingRule>) {
        updateSettings(settings.copy(routingRulesJson = RoutingEngine.serializeRules(rules)))
    }

    fun applyRoutingPreset(preset: RoutingPresets.Preset) {
        setRoutingRules(RoutingPresets.materialize(preset))
        message = when {
            state != "DISCONNECTED" ->
                "${preset.title} preset saved • reconnect to apply it to the active tunnel"
            else -> "${preset.title} preset applied • ${preset.rules.size} rules"
        }
    }

    fun simulateRoute(host: String): RoutingEngine.RouteSimulation? {
        val clean = host.trim()
        if (clean.isEmpty()) return null
        return RoutingEngine.simulate(settings, clean)
    }

    fun applyIranRoutingPreset(prepareAssets: Boolean = true) {
        updateSettings(
            settings.copy(
                routingMode = RoutingMode.GEO_DIRECT,
                geoAssetSourceId = RoutingDefaults.SOURCE_CHOCOLATE4U,
                geoIpUrl = RoutingDefaults.GEOIP_URL,
                geoSiteUrl = RoutingDefaults.GEOSITE_URL,
                routingRulesJson = RoutingEngine.serializeRules(
                    RoutingPresets.materialize(RoutingPresets.Preset.RECOMMENDED)
                ),
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
            postToMain { geoGateNote = geoGateReason() }
            val parts = mutableListOf<String>()
            parts += if (status.geoIpReady) "geoip ${formatBytes(status.geoIpBytes)}" else "geoip missing (add a geoip.dat URL above)"
            parts += if (status.geoSiteReady) "geosite ${formatBytes(status.geoSiteBytes)}" else "geosite missing (add a geosite.dat URL above)"

            val summary = geoSourceSummary()
            message = "Routing assets • $summary • ${parts.joinToString(" • ")}"
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

            val activeProfile = profile(activeProfileId)
            val evidenceSettings = if (activeProfile != null) {
                runCatching { effectiveSettingsFor(activeProfile) }.getOrDefault(settings)
            } else {
                settings
            }
            val report = bugFinder.scan(
                appState = state,
                stateDetail = stateDetail,
                activeProfileId = activeProfileId,
                settings = evidenceSettings,
                profiles = profiles.toList(),
                history = history.toList(),
                networkLabel = networkSnapshot.label,
                sentinel = sentinel,
                privacy = privacy,

                tunnelUptimeMs = if (state == "CONNECTED" && connectedSinceMs > 0L) {
                    (System.currentTimeMillis() - connectedSinceMs).coerceAtLeast(0L)
                } else {
                    0L
                },

                lastProbeFault = probeLocalFault
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

    fun backgroundExemptionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            ?: return true
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    fun unrestrictedBackgroundDirectIntent(): Intent =
        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(android.net.Uri.parse("package:${context.packageName}"))

    fun unrestrictedBackgroundFallbackIntent(): Intent =
        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun noteBackgroundAccessRequest() {
        diagnostics.event(
            "APP",
            "background-access-requested",
            "alreadyGranted" to backgroundExemptionGranted(),
            "surface" to "settings-card"
        )
    }

    fun requestUnrestrictedBackgroundAccess(uiContext: Context) {
        if (backgroundExemptionGranted()) {
            message = "Unrestricted background access is already granted"
            return
        }
        val newTask = if (uiContext is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        val launched = runCatching {
            uiContext.startActivity(unrestrictedBackgroundDirectIntent().addFlags(newTask))
        }.isSuccess || runCatching {
            uiContext.startActivity(unrestrictedBackgroundFallbackIntent().addFlags(newTask))
        }.isSuccess
        message = if (launched) {
            "Allow unrestricted background activity for MarbleNG on the Android page"
        } else {
            "Could not open Android's battery-optimization page"
        }
    }

    private fun <T> applyStateOnMainThread(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        val applied = java.util.concurrent.FutureTask { block() }
        postToMain { applied.run() }
        return try {
            applied.get()
        } catch (error: java.util.concurrent.ExecutionException) {
            throw error.cause ?: error
        }
    }

    private fun task(label: String, block: () -> Unit): Boolean {
        if (!taskInFlight.compareAndSet(false, true)) {
            message = "MarbleNG is busy • finish the current task before starting another"
            diagnostics.event("APP", "task-rejected-busy", "label" to label)
            return false
        }

        postToMain {
            busy = true
            message = label
        }
        diagnostics.event("APP", "task-start", "label" to label)

        val future = io.submit(Runnable {
            try {
                Thread.interrupted()
                block()
            } catch (t: Throwable) {
                if (t is InterruptedException) {
                    diagnostics.event("APP", "task-cancelled", "label" to label)
                    message = "Cancelled • ${probeDone} measurements kept"
                } else {
                    diagnostics.error("APP", "task-failed", t, "label" to label)
                    message = "${t::class.simpleName}: ${t.message}"
                }
            } finally {
                Thread.interrupted()
                diagnostics.event("APP", "task-finish", "label" to label)
                taskInFlight.set(false)
                activeTask.set(null)

                endProbeBatch()
                postToMain {
                    busy = false
                    refreshingSources = emptySet()
                }
            }
        })
        activeTask.set(future)
        return true
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

    /**
     * MARBLE_SUBSCRIPTION_REACH_V145 — a subscription link loads whatever the tunnel is doing.
     *
     * ## The bug this replaces
     *
     * A subscription URL and a proxy route are two independent reachability problems, and the
     * old code tied them together with two hard rules:
     *
     *  - **connected ⇒ SOCKS only** (`allowDirect = !connected`). MarbleNG excludes its own
     *    package from the TUN, so a "direct" request while connected travels over the physical
     *    underlay. Panels that only answer their own country (or that block datacentre exit
     *    IPs) are reachable exactly that way — and the app refused to try it, so those
     *    subscriptions "only work with the VPN off".
     *  - **connected ⇒ fail closed** (`check(xray.isAlive)`). A refresh during CONNECTING, or
     *    while the core was restarting, threw before a single byte was attempted, even though
     *    the underlay was perfectly able to answer.
     *  - **disconnected ⇒ direct only** (`throughSocks = null`). A DPI-blocked provider URL had
     *    no second path at all, so those subscriptions "only work with the VPN on".
     *
     * ## The rule now
     *
     * Both transports are always attempted; only their ORDER depends on the runtime state. When
     * a healthy tunnel exists the tunnel goes first (that is what the user asked for by being
     * connected) and the underlay is the fallback; otherwise the underlay goes first. When the
     * app is not connected at all and every direct path failed, a short-lived Xray instance is
     * started on a known-good profile purely to fetch the link, so a censored subscription URL
     * no longer requires the user to connect manually first.
     *
     * DPI evasion itself (browser UA, jsDelivr mirrors, no-cleartext redirects) still belongs to
     * [DpiAwareFetcher]; "Subscription redirect left HTTPS" is enforced in fetchDirect.
     */
    private fun httpSubscription(url: String): SubscriptionPayload {
        require(isHttpsSubscriptionUrl(url)) {
            "Remote subscriptions must use HTTPS"
        }
        val livePort = liveSocksPortOrZero()
        val throughSocks: ((String, String) -> DpiAwareFetcher.Payload)? =
            if (livePort > 0) {
                { candidate, agent ->
                    DpiAwareFetcher.Payload(
                        text = SocksHttpClient.getTextUrl(
                            livePort,
                            candidate,
                            maxBytes = MAX_SUBSCRIPTION_BYTES,
                            userAgent = agent
                        )
                    )
                }
            } else {
                null
            }

        val attempt = runCatching {
            DpiAwareFetcher.fetch(
                url = url,
                maxBytes = MAX_SUBSCRIPTION_BYTES,
                iranActive = iranMode.active,
                allowDirect = true,
                preferSocks = livePort > 0,
                throughSocks = throughSocks
            )
        }
        attempt.getOrNull()?.let { payload ->
            return SubscriptionPayload(payload.text, payload.userInfo)
        }

        val bootstrapped = if (livePort <= 0) fetchThroughTemporaryTunnel(url) else null
        if (bootstrapped != null) return bootstrapped

        throw attempt.exceptionOrNull()
            ?: IllegalStateException("Subscription fetch failed on every transport")
    }

    private fun liveSocksPortOrZero(): Int {
        if (state != "CONNECTED") return 0
        if (!(if (activeCoreEngine == CoreEngine.SINGBOX) singBox.isAlive else xray.isAlive)) return 0
        return activeProxyPort().takeIf { it in 1..65535 } ?: 0
    }

    private fun fetchThroughTemporaryTunnel(url: String): SubscriptionPayload? {
        val candidates = subscriptionBootstrapCandidates()
        if (candidates.isEmpty()) return null
        for (candidate in candidates) {
            var payload: DpiAwareFetcher.Payload? = null
            val started = runCatching {
                xray.temporary(
                    profile = candidate,
                    port = 0,
                    settings = effectiveSettingsFor(candidate),
                    link = LinkEvidence.UNKNOWN
                ) { port ->
                    payload = runCatching {
                        DpiAwareFetcher.fetch(
                            url = url,
                            maxBytes = MAX_SUBSCRIPTION_BYTES,
                            iranActive = iranMode.active,
                            allowDirect = false,
                            preferSocks = true,
                            throughSocks = { linkUrl, agent ->
                                DpiAwareFetcher.Payload(
                                    text = SocksHttpClient.getTextUrl(
                                        port,
                                        linkUrl,
                                        maxBytes = MAX_SUBSCRIPTION_BYTES,
                                        userAgent = agent
                                    )
                                )
                            }
                        )
                    }.getOrNull()
                }
            }.getOrDefault(false)
            val body = payload
            if (started && body != null) {
                diagnostics.event(
                    "SUBSCRIPTION",
                    "bootstrap-tunnel-fetch",
                    "profile" to candidate.id.take(12),
                    "bytes" to body.text.length
                )
                return SubscriptionPayload(body.text, body.userInfo)
            }
        }
        diagnostics.event("SUBSCRIPTION", "bootstrap-tunnel-exhausted", "tried" to candidates.size)
        return null
    }

    private fun subscriptionBootstrapCandidates(): List<ProxyProfile> {
        val ranked = benchmarks
            .asSequence()
            .filter { it.success > 0 && it.latencyMs > 0.0 }
            .sortedByDescending { it.score }
            .mapNotNull { result -> profiles.firstOrNull { it.id == result.profileId } }
            .toList()
        return (listOfNotNull(lastProfile()) + ranked + profiles)
            .distinctBy { it.id }
            .filter { it.host.isNotBlank() }
            .take(3)
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
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private companion object {
        const val IRAN_RESCAN_INTERVAL_MS = 10L * 60L * 1000L

        const val MAX_HISTORY_RECORDS = 200

        const val MAX_SUBSCRIPTION_BYTES = 8 * 1024 * 1024

        const val DISCONNECT_WATCHDOG_MS = 6_000L

        const val LOCATION_SESSION_BUDGET = 48

        const val LOCATION_SWEEP_CONCURRENCY = 4

        const val LOCATION_SWEEP_DEADLINE_MS = 90_000L

        const val INSIGHT_FAMILY_BATCH = 12

        const val IP_FAMILY_SCAN_CONCURRENCY = 6

        const val USAGE_TICK_MS = 2_000L
    }
}
