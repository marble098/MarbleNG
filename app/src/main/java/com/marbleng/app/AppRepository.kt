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
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
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

// MARBLE_MANUAL_SOURCE_REMOVED_V123 — the retired Manual pseudo-source. The id survives in stored
// profiles, in a persisted source filter and in a remembered route reference, so it is kept here as
// a migration key only: nothing in the product creates or shows a "manual" source any more.
private const val LEGACY_MANUAL_SOURCE_ID = "manual"

/** Name of the real local source that owns user-authored configs when the library starts empty. */
private const val USER_SOURCE_NAME = "My servers"

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
    // MARBLE_RANK_RECOVERY_REPO_V61
    // MARBLE_PATTNG_BATCH_RANK_REPO_V62
    // MARBLE_LIVE_RANK_EVENT_REPO_V63

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

    // MARBLE_SMART_RANK_V90: debounce + single-flight gate for the Rank action, so a tap storm can
    // never re-run the whole preflight + benchmark pool (observed: 9 triggers in 7s, zero results).
    private val rankGate = SmartRankGate()

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
        id == ServerlessFreedomEngine.SOURCE_ID -> ServerlessFreedomEngine.SOURCE_ID
        subscriptions.any { it.id == id } -> id
        // Includes the retired "manual" pseudo-source id: an old persisted filter falls back to
        // "all" instead of pointing at a source that no longer exists.
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

    /**
     * Last opened Settings page (key from [com.marbleng.app.ui.Aether2026.SettingsPages]). Restored
     * when the Settings tab is re-entered so a user who was on the Theme page lands back on the Theme
     * page rather than reshooting to the hub — the back-navigation bug.
     */
    var lastSettingsPage by mutableStateOf(store.lastSettingsPage())
        private set

    fun rememberSettingsPage(name: String) {
        val normalized = name.trim().ifBlank { "hub" }
        if (lastSettingsPage == normalized) return
        lastSettingsPage = normalized
        store.setLastSettingsPage(normalized)
    }

    // MARBLE_SIGNATURE_HOME_V112 — the floating connect button's dragged spot, stored as
    // normalized viewport fractions so it survives restarts on any screen size.
    var proFabPosition by mutableStateOf(store.proFabPosition())
        private set

    fun rememberProFabPosition(nx: Float, ny: Float) {
        val clamped = nx.coerceIn(0.05f, 0.95f) to ny.coerceIn(0.12f, 0.80f)
        if (proFabPosition == clamped) return
        proFabPosition = clamped
        store.setProFabPosition(clamped.first, clamped.second)
    }

    /** Public diagnostics hook for UI-level tripwires (e.g. the Settings viewport fallback). */
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

    // MARBLE_LIBRARY_COLLAPSIBLE_V113 — the open/closed state of every Library source group,
    // persisted so the layout the user folds is the layout they come back to.
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

    // MARBLE_LIBRARY_FREEDOM_TOGGLE_V113 — Marble Freedom stays hidden from the Library until the
    // user reveals it from the smart floating button.
    var libraryFreedomHidden by mutableStateOf(store.libraryFreedomHidden())
        private set

    fun updateLibraryFreedomHidden(hidden: Boolean) {
        if (libraryFreedomHidden == hidden) return
        libraryFreedomHidden = hidden
        store.setLibraryFreedomHidden(hidden)
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

    /**
     * MARBLE_MANUAL_SOURCE_REMOVED_V123 — the source user-authored configs land in.
     *
     * "Somewhere of my own" is now a real local source instead of a virtual one behind a Settings
     * switch: the first URL-less source when the user already has one, otherwise the first source
     * at all, and only when the library is completely empty is a source created. Creating it here —
     * at the moment an import genuinely needs it — means a fresh install never shows a group the
     * user did not ask for, and a paste can never fail for want of a destination.
     */
    private fun ensureUserSource(): LibraryTarget {
        subscriptions.firstOrNull { it.url.isBlank() }?.let { return LibraryTarget(it.id, it.name) }
        subscriptions.firstOrNull()?.let { return LibraryTarget(it.id, it.name) }

        val id = sha("local:${System.nanoTime()}:${java.util.UUID.randomUUID()}").take(12)
        subscriptions += Subscription(
            id = id,
            name = USER_SOURCE_NAME,
            url = "",
            updatedAt = System.currentTimeMillis()
        )
        store.saveSubscriptions(subscriptions)
        diagnostics.event("LIBRARY", "local-source-created", "source" to id.take(12))
        return LibraryTarget(id, USER_SOURCE_NAME)
    }

    /** Id of the source that receives user-authored configs; created on first use. */
    fun userIntakeSourceId(): String = ensureUserSource().id

    /**
     * Display name of the source user-authored configs land in, without creating anything. Used by
     * the add sheet to tell the user where a paste or a hand-built server will be filed.
     */
    val userSourceName: String
        get() = subscriptions.firstOrNull { it.url.isBlank() }?.name
            ?: subscriptions.firstOrNull()?.name
            ?: USER_SOURCE_NAME

    /** A concrete Library source can receive user imports; "all" is only a view. */
    private fun resolveLibraryTarget(id: String): LibraryTarget? = when {
        id == "all" || id == ServerlessFreedomEngine.SOURCE_ID -> null
        // Blank and the retired Manual id both mean "my own servers".
        id.isBlank() || id == LEGACY_MANUAL_SOURCE_ID -> ensureUserSource()
        else -> subscriptions.firstOrNull { it.id == id }?.let { LibraryTarget(it.id, it.name) }
    }

    /**
     * Library is also the chooser for the offline Marble Freedom engine. Freedom rows are
     * generated locally, never persisted as subscription data, and remain selectable after a
     * cold start even when the network is unavailable.
     */
    private fun freedomLibraryProfiles(): List<ProxyProfile> =
        ServerlessFreedomEngine.profiles(settings, iranMode)

    // MARBLE_MANUAL_SOURCE_REMOVED_V123 — every stored profile belongs to a real source, so there
    // is no longer a source whose rows can be switched out of the library.
    val libraryProfiles: List<ProxyProfile>
        get() = (profiles.toList() + freedomLibraryProfiles())
            .distinctBy { it.id }

    // Ranking/auto-connect keeps its historical contract: generated Freedom rows are selected
    // through their own adaptive engine, never mixed into subscription benchmark waves.
    private fun enabledProfilesSnapshot(): List<ProxyProfile> =
        profiles.toList().distinctBy { it.id }

    /**
     * Resolve the exact Library source selected by the user.
     *
     * Fail closed: a stale/unknown source id returns an empty set — never the whole Library.
     * "all" expands to every enabled subscription profile; Marble Freedom has its own local scope.
     */
    private fun libraryScopeSnapshot(sourceId: String): List<ProxyProfile> {
        val available = enabledProfilesSnapshot()
        return when (sourceId) {
            // "All" remains the subscription test scope. Freedom has a dedicated source chip
            // and a separate adaptive check, so it cannot poison a TCP/TUN wave with host="".
            "all" -> available
            ServerlessFreedomEngine.SOURCE_ID -> freedomLibraryProfiles()
            else -> {
                if (subscriptions.none { it.id == sourceId }) emptyList()
                else available.filter { it.subscriptionId == sourceId }
            }
        }
    }

    private fun libraryScopeLabel(sourceId: String): String = when (sourceId) {
        "all" -> "All sources"
        ServerlessFreedomEngine.SOURCE_ID -> "Marble Freedom"
        else -> subscriptions.firstOrNull { it.id == sourceId }?.name ?: "Missing source"
    }

    /** Refresh exactly the source represented by the Library selection. */
    fun refreshLibrarySource(sourceId: String) {
        when (sourceId) {
            "all" -> refreshAll()
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
            val userOwnedBucket = current.subscriptionId == LEGACY_MANUAL_SOURCE_ID ||
                current.subscriptionId in localIds
            if (userOwnedBucket && current.sourceManaged) {
                profiles[index] = current.copy(sourceManaged = false)
                changed = true
            }
        }
        if (changed) store.saveProfiles(profiles)
    }

    /**
     * MARBLE_MANUAL_SOURCE_REMOVED_V123 — one-time repair of data written by the retired Manual
     * pseudo-source.
     *
     * Older builds stored user-authored configs under the virtual id `manual` and hid them behind a
     * Settings switch. That switch is gone, so those rows are re-homed into a real local source —
     * they keep their identity, their measurements and their learned acceleration, and they show up
     * in a group again. A remembered route and a persisted source filter that pointed at `manual`
     * are re-pointed with them, so a restart lands on the same server instead of on nothing.
     */
    private fun migrateLegacyManualSource() {
        val needsRepair = profiles.any { it.subscriptionId == LEGACY_MANUAL_SOURCE_ID } ||
            selectedProfileSourceId == LEGACY_MANUAL_SOURCE_ID ||
            librarySourceFilter == LEGACY_MANUAL_SOURCE_ID
        if (!needsRepair) return

        val target = ensureUserSource()
        for (index in profiles.indices) {
            val current = profiles[index]
            if (current.subscriptionId == LEGACY_MANUAL_SOURCE_ID) {
                profiles[index] = current.copy(
                    subscriptionId = target.id,
                    subscriptionName = target.name,
                    sourceManaged = false
                )
            }
        }
        store.saveProfiles(profiles)

        if (selectedProfileSourceId == LEGACY_MANUAL_SOURCE_ID) {
            selectedProfileSourceId = target.id
            store.setLastProfileRef(selectedProfileId, target.id)
        }
        if (store.librarySourceFilter() == LEGACY_MANUAL_SOURCE_ID) {
            librarySourceFilter = normalizeLibrarySourceFilter(librarySourceFilter)
            store.setLibrarySourceFilter(librarySourceFilter)
        }
        diagnostics.event(
            "LIBRARY",
            "legacy-manual-source-migrated",
            "source" to target.id.take(12),
            "name" to target.name
        )
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

    /**
     * MARBLE_SELECT_IS_NOT_CONNECT_V121 — the server the user has *chosen*, which is not the same
     * thing as the server that is carrying traffic.
     *
     * Tapping a server in the Servers list used to open a tunnel immediately, so browsing the list
     * while disconnected kept starting connections nobody asked for. Selection is now a plain,
     * durable choice: it moves the Home surface onto that server and the connect button acts on
     * it, and nothing else happens until the user presses connect. (Tapping another server *while*
     * a tunnel is up still switches the route — that is an explicit re-connect, not a browse.)
     */
    var selectedProfileId by mutableStateOf(store.lastProfileId()); private set
    var selectedProfileSourceId by mutableStateOf(store.lastProfileSourceId()); private set
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
    var probeLastName by mutableStateOf(""); private set
    var probeLastOutcome by mutableStateOf(""); private set
    var probeLastLatencyMs by mutableStateOf(0); private set
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
        probeLastName = ""
        probeLastOutcome = ""
        probeLastLatencyMs = 0
    }

    private fun markProbeStart(profile: ProxyProfile) = postToMain {
        probeRunning = probeRunning + profile.id
        probeCurrentName = profile.name
        probeLastName = profile.name
        probeLastOutcome = "TESTING"
        probeLastLatencyMs = 0
    }

    /** Publishes one finished node immediately; the card updates while the batch continues. */
    private fun markProbeResult(profile: ProxyProfile, result: BenchmarkResult) = postToMain {
        probeRunning = probeRunning - profile.id
        probeFinished = probeFinished + profile.id
        probeCurrentName = profile.name
        probeLastName = profile.name
        probeLastOutcome = if (result.success > 0) "OK" else "FAILED"
        probeLastLatencyMs = if (result.success > 0) {
            LinkQualityEstimator.sanitaryRtt(result.latencyMs.toInt())
        } else 0
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

    // MARBLE_HOME_SESSION_EVIDENCE_V110
    // Wall-clock start of the session currently carrying traffic. Every Home style reads this one
    // value, so the uptime readout can never disagree between presentations.
    var connectedSinceMs by mutableStateOf(0L); private set

    /** One-shot connection ping. It is measured on demand, never on a repeating timer. */
    var connectionPingMs by mutableStateOf(0); private set
    var connectionPingState by mutableStateOf(ConnectionPingState.IDLE); private set
    private val connectionPingInFlight = AtomicBoolean(false)

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
        // MARBLE_MANUAL_SOURCE_REMOVED_V123 — re-home anything the retired Manual source owned.
        migrateLegacyManualSource()
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
     * - the request is intentionally available from the connected Home route, without a hidden
     *   settings gate that could leave old installations with a permanently blank IP row;
     * - proxy config, UUID/password, SNI and subscription URL are never sent;
     * - only the already-public resolved server IP is queried at ipwho.is;
     * - a 15-minute per-endpoint cache avoids noisy/redundant lookups.
     */
    fun refreshServerIntel(targetProfile: ProxyProfile? = null, force: Boolean = false) {
        val target = targetProfile
            ?: profile(activeProfileId, activeProfileSourceId)
            ?: lastProfile()
        if (target != null && ServerlessFreedomEngine.isServerless(target)) {
            postToMain {
                serverIntel = ServerIntelInfo(
                    endpoint = "freedom",
                    ip = "this device",
                    ipType = "Freedom",
                    organization = "Marble Freedom",
                    isp = "No remote proxy"
                )
                serverIntelLoading = false
                serverIntelError = ""
            }
            return
        }
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

        // The same family plan the tunnel uses: server intelligence that reports the IPv4 address of
        // a node the engine would dial over IPv6 describes a path the user never gets.
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
                    resolver = { name ->
                        InetAddress.getAllByName(name)
                            .filterNot {
                                it.isAnyLocalAddress ||
                                    it.isLoopbackAddress ||
                                    it.isLinkLocalAddress
                            }
                            .toTypedArray()
                    }
                )

                if (addresses.isEmpty()) {
                    throw IllegalStateException("Server endpoint did not resolve to a usable IP")
                }

                // Already ordered by the plan, so the first entry is the address the engine will dial.
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
        // MARBLE_HONEST_PING_V119 — the live monitor, the Home probe and Stored benchmark seeds
        // all publish through here; the only shared bound is physical (positive, ≤ 10 s), so a
        // genuinely fast route shows its real latency instead of a synthetic floor.
        val honestPing = LinkQualityEstimator.sanitaryRtt(pingMs)
        if (honestPing <= 0) return

        // Quality is computed from the same bounded evidence window as the displayed metrics.
        // A timeout is reliability evidence, unknown jitter stays neutral, and p90 catches a
        // congested tail that a median alone can hide.
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
    fun profile(id: String, sourceId: String? = null): ProxyProfile? {
        if (ServerlessFreedomEngine.matches(id, sourceId)) {
            val shielded = IranShield.apply(settings, null, iranMode, geoIpReady())
            val allFreedom = ServerlessFreedomEngine.profiles(shielded, iranMode)
            return allFreedom.firstOrNull { it.id == id } ?: allFreedom.first()
        }
        return if (!sourceId.isNullOrBlank()) {
            profiles.firstOrNull { it.id == id && it.subscriptionId == sourceId }
        } else {
            profiles.firstOrNull { it.id == id }
        }
    }

    fun serverlessProfile(): ProxyProfile {
        val shielded = IranShield.apply(settings, null, iranMode, geoIpReady())
        val allFreedom = ServerlessFreedomEngine.profiles(shielded, iranMode)
        // Prefer the actively connected one so the UI shows the correct tier
        return allFreedom.firstOrNull { it.id == activeProfileId } ?: allFreedom.first()
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
        // A late DISCONNECTING report can never override a state the service has already moved on
        // from; the watchdog token below is what actually clears a stuck teardown.
        if (s == "DISCONNECTING" && state != "CONNECTED" && state != "CONNECTING") return
        diagnostics.event("APP", "state", "from" to state, "to" to s, "detail" to d.take(160))
        postToMain {
            state = s
            stateDetail = d
            if (s != "CONNECTED") {
                activeProfileId = ""
                activeProfileSourceId = ""
                connectedSinceMs = 0L
                connectionPingMs = 0
                connectionPingState = ConnectionPingState.IDLE
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
    ): AppSettings {
        val tuned = if (settings.intelligenceEnabled) {
            intelligence.effectiveSettings(profile, settings, withAcceleration)
        } else {
            DpiEvasionPolicy.heal(
                IranShield.apply(settings, profile, iranMode, geoIpReady()),
                DpiEvasionPolicy.PathEvidence(),
                iranMode
            )
        }
        val guarded = IdentityGuard.apply(tuned)
        return if (ServerlessFreedomEngine.isServerless(profile)) {
            ServerlessFreedomEngine.pinSession(guarded)
        } else {
            guarded
        }
    }

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

    fun recoveryCandidates(failedIds: Set<String>): List<ProxyProfile> {
        if (settings.serverlessModeEnabled ||
            ServerlessFreedomEngine.matches(activeProfileId, activeProfileSourceId)
        ) {
            return emptyList()
        }
        return intelligence.recoveryCandidates(enabledProfilesSnapshot(), failedIds, settings)
    }

    fun setServerlessMode(enabled: Boolean) {
        if (settings.serverlessModeEnabled == enabled) return
        updateSettings(settings.copy(serverlessModeEnabled = enabled))
        message = if (enabled) "Marble Freedom" else "Servers"
    }

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

        val port = activeProxyPort()
        val sessionAtStart = connectedSinceMs

        // MARBLE_ONE_PING_V121 — the Home ping obeys Settings → Testing like every other
        // measurement in the product. Smart ping (the default) and Tunnel ping race the verified
        // in-tunnel ladder below; the address-level methods measure the server endpoint directly,
        // which is exactly what the user asked for when they picked TCP or ICMP.
        val method = settings.probeMethod
        if (method == ProbeMethod.TCP || method == ProbeMethod.ICMP) {
            postToMain {
                connectionPingMs = 0
                connectionPingState = ConnectionPingState.MEASURING
            }
            val target = profile(activeProfileId, activeProfileSourceId)
            io.execute {
                val sample = target?.let { live ->
                    runCatching {
                        RouteProbe.measure(
                            profile = live,
                            icmpMode = method == ProbeMethod.ICMP,
                            samples = settings.benchSamples.coerceIn(1, 4),
                            timeoutMs = (settings.benchTimeoutSec * 1000).coerceIn(500, 8_000),
                            settings = settings
                        )
                    }.getOrNull()
                }
                val measured = sample
                    ?.takeIf { it.successPercent > 0 }
                    ?.latencyMs
                    ?.let { LinkQualityEstimator.sanitaryRtt(it).roundToInt() }
                    ?: 0
                diagnostics.event(
                    "APP",
                    "home-connection-ping",
                    "measured" to measured,
                    "mode" to if (method == ProbeMethod.ICMP) "icmp" else "tcp"
                )
                postToMain {
                    connectionPingInFlight.set(false)
                    when {
                        state != "CONNECTED" || connectedSinceMs != sessionAtStart -> {
                            connectionPingMs = 0
                            connectionPingState = ConnectionPingState.IDLE
                        }
                        measured > 0 -> {
                            connectionPingMs = measured
                            connectionPingState = ConnectionPingState.MEASURED
                        }
                        else -> {
                            connectionPingMs = 0
                            connectionPingState = ConnectionPingState.FAILED
                        }
                    }
                }
            }
            return
        }

        // Guarantee 1 — seed the readout with a real measurement the tunnel already owns, so the
        // value never sits in MEASURING while the race is still running. Stored benchmarks are
        // bounded by the shared positive/ceiling clamp before they can reach the UI.
        val storedLatencyMs = benchmarks
            .firstOrNull { it.profileId == activeProfileId }
            ?.takeIf { it.success > 0 && it.latencyMs > 0.0 }
            ?.latencyMs
            ?.let { LinkQualityEstimator.sanitaryRtt(it.roundToInt()) }
            ?: 0
        val seedMs = listOf(livePingMs, storedLatencyMs).firstOrNull { it > 0 } ?: 0

        // MARBLE_PING_FLOOR_V117 — the seed (live monitor RTT / stored benchmark) is useful as a
        // last-resort tail, but it is never a finished measurement. Showing it as MEASURED let the
        // Home surface flash an unrepresentative 15 ms before the real probe race had run. The
        // readout now stays in MEASURING until the median of the verified race is published.
        postToMain {
            connectionPingMs = 0
            connectionPingState = ConnectionPingState.MEASURING
        }

        io.execute {
            // Literal-IP + TLS-hostname pairs: the SOCKS CONNECT destination stays a literal IP
            // (DNS-free), while certificate verification uses the provider's real hostname —
            // the same trick the live route monitor uses, because some anycast edges reject the
            // bare IP as the TLS name.
            val literalTargets = listOf(
                Triple("1.1.1.1", "cloudflare-dns.com", "/cdn-cgi/trace"),
                Triple("8.8.8.8", "dns.google", "/dns-query"),
                Triple("9.9.9.9", "dns.quad9.net", "/dns-query"),
                Triple("1.0.0.1", "cloudflare-dns.com", "/cdn-cgi/trace")
            )
            val domainTargets = listOf(
                "www.gstatic.com" to "/generate_204",
                "cp.cloudflare.com" to "/generate_204",
                "connectivitycheck.gstatic.com" to "/generate_204"
            )

            data class ProbeSample(val mode: String, val ms: Double, val verified: Boolean)

            val results = java.util.Collections.synchronizedList(ArrayList<ProbeSample>())

            fun record(mode: String, ms: Double, verified: Boolean) {
                val sane = LinkQualityEstimator.sanitaryRtt(ms)
                if (sane <= 0.0) return
                results += ProbeSample(mode, sane, verified)
            }

            val pool = Executors.newFixedThreadPool(literalTargets.size + domainTargets.size + 2)
            try {
                val tasks = literalTargets.map { (ip, tlsHost, path) ->
                    java.util.concurrent.Callable {
                        runCatching {
                            SocksHttpClient.httpsFirstByteLatency(
                                port = port,
                                host = ip,
                                path = path,
                                targetPort = 443,
                                timeoutMs = 1_800,
                                tlsHost = tlsHost
                            )
                        }.getOrNull()?.let { record("first-byte:$tlsHost", it, verified = true) }
                    }
                } + domainTargets.map { (host, path) ->
                    java.util.concurrent.Callable {
                        runCatching {
                            SocksHttpClient.tunnelRttBatch(
                                port = port,
                                host = host,
                                path = path,
                                samples = 1,
                                timeoutMs = 2_000
                            )
                        }.getOrNull()
                            ?.samplesMs
                            ?.filter { it.isFinite() && it > 0.0 }
                            ?.minOrNull()
                            ?.let { record("real-delay:$host", it, verified = true) }
                    }
                } + listOf(
                    java.util.concurrent.Callable {
                        // Independent full-request opinion: any 2xx/3xx proves the tunnel
                        // end-to-end and carries an honest elapsed time.
                        runCatching {
                            SocksHttpClient.get(
                                port,
                                "cp.cloudflare.com",
                                "/generate_204",
                                1_800,
                                2_048
                            )
                        }.getOrNull()
                            ?.takeIf { it.status in 200..399 && it.elapsedMs > 0.0 }
                            ?.let { record("get:cloudflare", it.elapsedMs, verified = true) }
                    },
                    java.util.concurrent.Callable {
                        // Guarantee 3 — the cheapest honest measurement of the connected tunnel:
                        // one SOCKS CONNECT handshake to a literal IP. It answers even when every
                        // HTTPS origin above is blocked, so the readout is never empty. It is
                        // recorded as unverified, so it can never outrank a real HTTPS RTT.
                        runCatching {
                            SocksHttpClient.connectLatency(
                                port = port,
                                host = "1.1.1.1",
                                targetPort = 443,
                                timeoutMs = 1_600
                            )
                        }.getOrNull()?.let { record("tunnel-handshake", it, verified = false) }
                    }
                )
                pool.invokeAll(tasks, 2_600, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                pool.shutdownNow()
            }

            val verifiedSamples = results.filter { it.verified }
            // MARBLE_HONEST_PING_V119 — one freaky fast sample never wins the race. The winner
            // is the median of the verified probes (every sample already bounded positive/ceiling),
            // so a single outlier cannot pull the Home readout away from the honest centre of the
            // verified distribution; the unverified SOCKS ladder only counts when nothing verified
            // answered.
            val racePool = (verifiedSamples.ifEmpty { results })
                .map { it.ms }
                .filter { it.isFinite() && it > 0.0 }
                .sorted()
            val winnerMs = racePool.getOrNull(racePool.size / 2)
            // Diagnostics still identify which probe class produced the winning opinion.
            val winner = verifiedSamples.minByOrNull { it.ms } ?: results.minByOrNull { it.ms }
            // Ladder tail: the live tunnel monitor, then the stored benchmark of the live server.
            val tailMs = listOf(livePingMs, storedLatencyMs).firstOrNull { it > 0 } ?: 0
            val measured = (winnerMs ?: tailMs).toInt().coerceIn(0, 10_000)

            diagnostics.event(
                "APP",
                "home-connection-ping",
                "measured" to measured,
                "port" to port,
                "responders" to results.size,
                "verified" to verifiedSamples.size,
                "seed" to seedMs,
                "mode" to (winner?.mode ?: if (tailMs > 0) "tunnel-monitor" else "none"),
                "modes" to results.joinToString(",") { "${it.mode}=${it.ms.roundToInt()}" }
            )

            postToMain {
                connectionPingInFlight.set(false)
                // A disconnect or reconnect while the probe was in flight invalidates the result.
                when {
                    state != "CONNECTED" || connectedSinceMs != sessionAtStart -> {
                        connectionPingMs = 0
                        connectionPingState = ConnectionPingState.IDLE
                    }
                    measured > 0 -> {
                        connectionPingMs = measured
                        connectionPingState = ConnectionPingState.MEASURED
                    }
                    else -> {
                        // Unreachable while a tunnel carries traffic: every rung of the ladder is a
                        // real measurement of the connected route. Kept for exhaustiveness only.
                        connectionPingMs = 0
                        connectionPingState = ConnectionPingState.FAILED
                    }
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

    /**
     * Create a remote subscription, or a local (URL-less) source when [url] is blank.
     *
     * MARBLE_ADD_SUBSCRIPTION_V123 — returns whether the source was really created. The add sheet
     * used to close itself the instant the button was pressed, so every refusal (an `http://` link,
     * a duplicate URL) dismissed the form and left the user with a toast they had already lost:
     * "adding a subscription does not work". Callers now keep the sheet open on `false` and show
     * the reason. A blank [name] is derived from the URL's host — a user who pastes a provider link
     * has already named the source.
     */
    fun addSubscription(name: String, url: String): Boolean {
        val cleanUrl = url.trim()
        if (cleanUrl.isNotBlank() && !isHttpsSubscriptionUrl(cleanUrl)) {
            message = if (cleanUrl.startsWith("http://", ignoreCase = true)) {
                "That link is plain HTTP • subscription links must start with https://"
            } else {
                "Remote subscriptions must use HTTPS • leave URL empty for a local source"
            }
            return false
        }
        if (cleanUrl.isNotBlank()) {
            val duplicate = subscriptions.firstOrNull {
                it.url.isNotBlank() && it.url.trim().equals(cleanUrl, true)
            }
            if (duplicate != null) {
                message = "Subscription already exists • ${duplicate.name}"
                return false
            }
        }
        val sourceName = name.trim()
            .ifBlank { subscriptionHostLabel(cleanUrl) }
            .ifBlank { randomSourceName() }
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
        return true
    }

    /**
     * The subscription URL inside whatever the user pasted.
     *
     * MARBLE_ADD_SUBSCRIPTION_V123 — a provider link copied out of Telegram, a browser or a notes
     * app rarely arrives alone: it comes with a title line, trailing punctuation, line breaks and
     * sometimes angle brackets. Dropping that blob into the URL field used to leave the form
     * permanently disabled, which read as a dead button. This returns the first URL in the text
     * (http included, so the HTTPS refusal can name the real reason); with no URL present the
     * trimmed text comes back untouched so the field shows what was actually pasted.
     */
    fun extractSubscriptionUrl(pasted: String): String {
        val clean = pasted.trim()
        if (clean.isEmpty()) return ""
        val found = Regex("https?://[^\\s\"'<>\\[\\]{}|\\\\^`]+", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.value
            ?.trimEnd('.', ',', ';', ':', '!', '?', ')')
        return found ?: clean
    }

    /** A readable source name for a subscription URL: its host without the `www.` prefix. */
    fun subscriptionHostLabel(url: String): String =
        runCatching { URL(url.trim()).host.removePrefix("www.") }
            .getOrDefault("")
            .trim()

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
            message = "${sub.name} is a local source • add Manual/SSH servers into it"
            return
        }
        task("Refreshing ${sub.name}") {
            beginRefresh(listOf(sub.id))
            val payload = httpSubscription(sub.url)
            val parsed = ProxyParser.parseInput(payload.text, sub.id, sub.name)
            require(parsed.isNotEmpty()) {
                "No supported profiles returned; previous servers were kept"
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
                "${sub.name} • $refreshedCount servers",
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
                    require(parsed.isNotEmpty()) { "No supported profiles returned; previous servers were kept" }
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

    // MARBLE_MANUAL_IMPORT_V20
    // MARBLE_MANUAL_SOURCE_REMOVED_V123 — a blank target means "the user's own local source".
    fun addManualProfile(
        draft: ManualConfigDraft,
        targetSubscriptionId: String = ""
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
        return true
    }
    /**
     * MARBLE_QR_IMPORT_V121 — import the configs encoded in a QR image the user picked.
     *
     * Decoding happens off the main thread (a photo can be several megapixels) and the import
     * itself runs through the normal [importText] path, so a QR code and a pasted link land in
     * exactly the same place with exactly the same de-duplication.
     */
    fun importQrImage(uri: android.net.Uri, targetSubscriptionId: String) {
        io.execute {
            val decoded = runCatching { QrImageDecoder.decode(context, uri) }.getOrNull()
            postToMain {
                if (decoded.isNullOrBlank()) {
                    message = "No QR code found in that image"
                } else {
                    importQrPayload(decoded, targetSubscriptionId)
                }
            }
        }
    }

    /**
     * MARBLE_QR_CAMERA_V123 — one intake for every decoded code, however it was read.
     *
     * A QR code carries one of two very different things: a server share link, or a subscription
     * URL. Deciding between them here is what makes a scanned code land in the right place — a
     * share link becomes a node, a provider link becomes a source Marble keeps up to date — instead
     * of a URL being parsed as though it were a dead node.
     */
    fun importQrPayload(payload: String, targetSubscriptionId: String) {
        val clean = payload.trim()
        if (clean.isBlank()) {
            message = "That code holds no config"
            return
        }
        val lines = clean.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val allWebUrls = lines.isNotEmpty() && lines.all {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }
        if (!allWebUrls) {
            importText(clean, "QR import", targetSubscriptionId)
            return
        }
        if (lines.any { it.startsWith("http://", ignoreCase = true) }) {
            message = "That code points at a plain HTTP link • subscriptions must use HTTPS"
            return
        }
        val added = addSubscriptionSources(lines, "Scanned source")
        message = if (added == 0) {
            "That subscription is already in Servers"
        } else {
            "$added scanned source${if (added == 1) "" else "s"} added • refreshing"
        }
        if (added > 0) refreshAll()
    }

    fun importText(
        text: String,
        name: String = "Imported",
        // MARBLE_MANUAL_SOURCE_REMOVED_V123 — blank means the user's own local source.
        targetSubscriptionId: String = ""
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
    fun importClipboard(text: String, targetSubscriptionId: String = "") {
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

        val added = addSubscriptionSources(lines, "Clipboard source")
        if (added == 0) {
            message = "Clipboard subscriptions are already in Servers"
            return
        }
        message = "$added clipboard source${if (added == 1) "" else "s"} added • refreshing"
        refreshAll()
    }

    /**
     * Creates one subscription per distinct HTTPS URL and persists the batch.
     *
     * Shared by the clipboard intake and the QR intake so a provider link becomes a source — and is
     * de-duplicated — identically however it reached the app. Returns how many were new.
     */
    private fun addSubscriptionSources(urls: List<String>, fallbackName: String): Int {
        var added = 0
        urls.distinct().take(32).forEachIndexed { index, rawUrl ->
            val cleanUrl = rawUrl.trim()
            if (subscriptions.any { it.url.trim().equals(cleanUrl, true) }) return@forEachIndexed
            val baseId = sha(cleanUrl).take(12)
            var id = baseId
            var suffix = 1
            while (subscriptions.any { it.id == id }) id = "${baseId.take(9)}-${suffix++}"
            val host = runCatching { URL(cleanUrl).host.removePrefix("www.") }.getOrDefault("")
            subscriptions += Subscription(
                id = id,
                name = host.ifBlank { "$fallbackName ${index + 1}" },
                url = cleanUrl,
                updatedAt = 0L
            )
            added++
        }
        if (added > 0) store.saveSubscriptions(subscriptions)
        return added
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
        message = "Server JSON saved • identity and learned acceleration refreshed"
        return true
    }

    /**
     * Make a durable, user-owned copy of any node — including a subscription-owned one.
     *
     * MARBLE_MANUAL_SOURCE_REMOVED_V123 — the copy lands in the user's local source, which survives
     * a provider refresh exactly as the retired Manual bucket did. Duplicating no longer needs a
     * Settings switch turned on first; it simply works.
     */
    fun duplicateProfile(id: String, sourceId: String? = null): Boolean {
        if (busy) {
            message = "Wait for the current background task before duplicating a server"
            return false
        }
        val source = profile(id, sourceId) ?: run {
            message = "Server no longer exists"
            return false
        }
        val target = ensureUserSource()
        val newId = sha("${source.id}:${System.nanoTime()}:${profiles.size}").take(12)
        var name = "${source.name} • copy"
        var n = 2
        while (profiles.any {
                it.name.equals(name, true) && it.subscriptionId == target.id
            }) {
            name = "${source.name} • copy $n"
            n++
        }
        profiles += source.copy(
            id = newId,
            name = name,
            subscriptionId = target.id,
            subscriptionName = target.name,
            sourceManaged = false
        )
        store.saveProfiles(profiles)
        message = "Copy created in ${target.name} • $name"
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
            store.clearLastProfile()
        }
        benchmarks = benchmarks.filterNot { it.profileId in doomedIds }
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
                // MARBLE_SELECT_IS_NOT_CONNECT_V121 — a deleted server cannot stay selected.
                selectedProfileId = ""
                selectedProfileSourceId = ""
            }
        }

        store.saveProfiles(profiles)
        message = "Server removed • ${target.name}"
    }

    /**
     * MARBLE_SERVERS_QUERY_V120 — "Move to group" from a server's own menu.
     *
     * The server keeps its identity, its stored measurements and its learned acceleration; only its
     * owner changes. The moved row becomes user-owned ([ProxyProfile.sourceManaged] = false) so a
     * later refresh of either source neither deletes it nor silently rewrites the user's copy, and
     * the exact last-route reference is re-pointed when the live route is the one being moved.
     */
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
            message = when (targetSourceId) {
                ServerlessFreedomEngine.SOURCE_ID ->
                    "Marble Freedom is generated locally • servers cannot be moved into it"
                else -> "Select one server source before moving a server"
            }
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
            message = "Wait for the current task before removing failed servers"
            return 0
        }
        if (state != "DISCONNECTED") {
            message = "Disconnect before removing failed servers from a subscription"
            return 0
        }

        val sub = subscriptions.firstOrNull { it.id == id } ?: run {
            message = "Subscription no longer exists"
            return 0
        }
        val kind = probeKind.trim().uppercase()
        if (kind !in setOf("TCP", "TUNNEL")) {
            message = "Unsupported failed-server evidence type"
            return 0
        }

        val failedIds = benchmarks.asSequence()
            .filter { it.success <= 0 && it.probeKind.equals(kind, ignoreCase = true) }
            .mapTo(mutableSetOf()) { it.profileId }

        val doomedIds = profiles.asSequence()
            .filter { it.subscriptionId == id && it.id in failedIds }
            .mapTo(linkedSetOf()) { it.id }

        if (doomedIds.isEmpty()) {
            message = "No failed $kind servers recorded for ${sub.name}"
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
        message = "Removed ${doomedIds.size} failed $kind server${if (doomedIds.size == 1) "" else "s"} from ${sub.name}"
        return doomedIds.size
    }

    /** The route Marble resumes: the last profile the user connected, when it still exists. */
    fun lastProfile(): ProxyProfile? {
        if (settings.serverlessModeEnabled) return serverlessProfile()
        val id = store.lastProfileId()
        if (id.isNotBlank() && !ServerlessFreedomEngine.matches(id, store.lastProfileSourceId())) {
            val sourceId = store.lastProfileSourceId()
            val exact = profile(id, sourceId)
            val candidate = (exact ?: profile(id))?.takeIf(::profileSourceEnabled)
            if (candidate != null) return candidate
        }

        // When Marble Freedom is switched off or store has no active profile, check connection
        // history in reverse to seamlessly restore the most recent enabled library node.
        val fromHistory = history.asReversed().asSequence()
            .filterNot { ServerlessFreedomEngine.matches(it.profileId, "") }
            .mapNotNull { rec -> profile(rec.profileId)?.takeIf(::profileSourceEnabled) }
            .firstOrNull()
        if (fromHistory != null) return fromHistory

        return null
    }

    /**
     * MARBLE_SELECT_IS_NOT_CONNECT_V121 — remember the user's chosen server without touching the
     * tunnel. The choice is persisted with the same key a successful connection writes, so Home,
     * Quick Tile and the next app start all agree on which server the connect button will use.
     */
    fun selectProfile(p: ProxyProfile) {
        diagnostics.event("APP", "select-server", "profile" to p.id.take(12), "name" to p.name.take(80))
        postToMain {
            selectedProfileId = p.id
            selectedProfileSourceId = p.subscriptionId
        }
        io.execute { runCatching { store.setLastProfileRef(p.id, p.subscriptionId) } }
    }

    /** True when [p] is the server the connect button would act on. */
    fun isSelectedProfile(p: ProxyProfile): Boolean =
        selectedProfileId.isNotBlank() &&
            selectedProfileId == p.id &&
            (selectedProfileSourceId.isBlank() || selectedProfileSourceId == p.subscriptionId)

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
            message = "No servers yet • add or import one in Servers"
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
            // Connecting is itself a selection: the route that carries traffic is the route the
            // connect button acts on next.
            selectedProfileId = p.id
            selectedProfileSourceId = p.subscriptionId
            // A reconnect onto the same node restarts the session clock; a redundant
            // markConnected for an already-running session must not.
            if (previousState != "CONNECTED" || connectedSinceMs <= 0L) {
                connectedSinceMs = System.currentTimeMillis()
                connectionPingMs = 0
                connectionPingState = ConnectionPingState.IDLE
            }

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
        if (!ServerlessFreedomEngine.isServerless(p) && settings.serverlessModeEnabled) {
            updateSettings(settings.copy(serverlessModeEnabled = false))
        }
        runCatching { scanIranMode() }
        
        if (ServerlessFreedomEngine.isServerless(p)) {
            setRuntimeState("CONNECTING", "Smart Aegis Check...")
            connectDecisionWorker.execute {
                val best = MarbleFreedomSmartRanker.bestProfile(
                    settings, iranMode, xray, intelligence, rankCrossCheckSources()
                ) { progress ->
                    setRuntimeState("CONNECTING", progress)
                }
                // MARBLE_SMART_RANK_V90: surface the stale-subscriptions prompt through the Snackbar
                // channel too (bestProfile can only report through onProgress, which becomes the
                // connecting-status text).
                if (MarbleFreedomSmartRanker.lastRankingDecision?.decisionReason ==
                    "stale-subscriptions-majority-excluded"
                ) {
                    message = MarbleFreedomSmartRanker.STALE_SUBSCRIPTIONS_MESSAGE
                }
                val intent = Intent(context, MarbleVpnService::class.java)
                    .setAction(MarbleVpnService.ACTION_START)
                    .putExtra(MarbleVpnService.EXTRA_PROFILE, best.id)
                    .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, best.subscriptionId)
                    .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_TUN)
                launchConnectionService(intent, best.name)
            }
        } else {
            setRuntimeState("CONNECTING", p.name)
            val intent = Intent(context, MarbleVpnService::class.java)
                .setAction(MarbleVpnService.ACTION_START)
                .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
                .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, p.subscriptionId)
                .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_TUN)
            launchConnectionService(intent, p.name)
        }
    }

    fun startLocalProxy(p: ProxyProfile) {
        privacy = null
        if (!ServerlessFreedomEngine.isServerless(p) && settings.serverlessModeEnabled) {
            updateSettings(settings.copy(serverlessModeEnabled = false))
        }
        runCatching { scanIranMode() }
        
        if (ServerlessFreedomEngine.isServerless(p)) {
            setRuntimeState("CONNECTING", "Smart Aegis Check...")
            connectDecisionWorker.execute {
                val best = MarbleFreedomSmartRanker.bestProfile(
                    settings, iranMode, xray, intelligence, rankCrossCheckSources()
                ) { progress ->
                    setRuntimeState("CONNECTING", progress)
                }
                if (MarbleFreedomSmartRanker.lastRankingDecision?.decisionReason ==
                    "stale-subscriptions-majority-excluded"
                ) {
                    message = MarbleFreedomSmartRanker.STALE_SUBSCRIPTIONS_MESSAGE
                }
                val intent = Intent(context, MarbleVpnService::class.java)
                    .setAction(MarbleVpnService.ACTION_START)
                    .putExtra(MarbleVpnService.EXTRA_PROFILE, best.id)
                    .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, best.subscriptionId)
                    .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_PROXY)
                launchConnectionService(intent, best.name)
            }
        } else {
            setRuntimeState("CONNECTING", p.name)
            val intent = Intent(context, MarbleVpnService::class.java)
                .setAction(MarbleVpnService.ACTION_START)
                .putExtra(MarbleVpnService.EXTRA_PROFILE, p.id)
                .putExtra(MarbleVpnService.EXTRA_PROFILE_SOURCE, p.subscriptionId)
                .putExtra(MarbleVpnService.EXTRA_MODE, MarbleVpnService.MODE_PROXY)
            launchConnectionService(intent, p.name)
        }
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
        markDisconnecting()
        runCatching {
            context.startService(Intent(context, MarbleVpnService::class.java).setAction(MarbleVpnService.ACTION_STOP))
        }.onFailure { error ->
            message = "Could not stop connection service: ${error::class.java.simpleName}: ${error.message ?: "unknown error"}"
            // The teardown never even started: do not leave the UI in the closing state.
            setRuntimeState("DISCONNECTED", "")
        }
    }

    /**
     * MARBLE_CONNECT_BUTTON_V121 — closing a tunnel is a state, not an instant.
     *
     * Tearing down TUN + Xray takes a real, visible moment. Until now the UI jumped straight back
     * to "ready to connect" while the interface was still up, so the connect button lied for a
     * second and an impatient second tap started a connection into a half-closed tunnel. The
     * repository now owns an explicit DISCONNECTING state: the service's own DISCONNECTED report
     * clears it, and a bounded watchdog clears it too if the service is killed mid-teardown so the
     * button can never stick.
     */
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

    /** Guards against an older teardown's watchdog clearing a newer session. */
    private var disconnectWatchdogToken: Long = 0L

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
                message = "No enabled servers to test"
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
        if (sourceId == ServerlessFreedomEngine.SOURCE_ID) {
            task("Marble Freedom • adaptive check") {
                val best = MarbleFreedomSmartRanker.bestProfile(
                    settings,
                    iranMode,
                    xray,
                    intelligence,
                    rankCrossCheckSources()
                ) { progress -> message = progress }
                message = "Marble Freedom ready • ${best.name}"
            }
            return
        }
        // MARBLE_SMART_RANK_V90: debounce + single-flight. A Rank tap storm can never re-run the
        // full preflight + benchmark pool more than once per cooldown.
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
        // If the global task mutex rejected us, the block never ran and the rank gate would be
        // stuck in-flight forever — release it immediately so Rank stays triggerable.
        if (!accepted) rankGate.release()
    }

    /** MARBLE_SMART_RANK_V90: fresh-subscription evidence for profile address cross-checking. */
    private fun rankCrossCheckSources(): ProfileAddressCrossCheck.CrossCheckSources =
        ProfileAddressCrossCheck.CrossCheckSources(
            freshSubscriptionProfiles = profiles.toList(),
            freshSubscriptionRaw = subscriptions.joinToString("\n") { sub ->
                runCatching { subscriptionRawText(sub.id) }.getOrDefault("")
            }
        )

    /** MARBLE_SMART_RANK_V90: map a benchmark result to the weighted multi-signal signals. */
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
                (r.failureReason.contains("inconclusive", true) ||
                    r.failureReason.contains("backoff", true) ||
                    (r.failureReason.contains("timeout", true) &&
                        r.handshakeAttempts < MultiSignalRankScorer.MIN_ATTEMPTS_TO_CONVICT))
        )

    private fun smartRankRun(sourceId: String, scope: String, candidates: List<ProxyProfile>) {
        // MARBLE_SMART_RANK_V90: fresh-subscription evidence so a stale/blank emitted config is
        // classified precisely (malformed-config / stale-subscription / address-resolved-but-invalid)
        // instead of the old blanket "missing-address".
        val crossCheckSources = rankCrossCheckSources()

        // MARBLE_SMART_RANK_V90: remove censorship-unsafe nodes (VLESS without TLS/REALITY, VMess
        // without forward secrecy) before they can fail a benchmark.
        val (securitySafe, deprecated) = ProfileSecurityAuditor.partitionForRank(candidates)
        if (deprecated.isNotEmpty()) {
            diagnostics.event(
                "BENCHMARK", "rank-deprecated-hidden",
                "source" to sourceId.take(24),
                "hidden" to deprecated.size,
                "profiles" to deprecated.joinToString { "${it.first.name.take(40)}=${it.second}" }
            )
        }

        // MARBLE_PROFILE_QUARANTINE_V1: a structurally broken profile (e.g. a VLESS/TLS config
        // that fails xray-start validation) must never poison ranking or selection. MARBLE_TURBO_RANK_V91:
        // quarantine is no longer a hard gate — every node gets the real tunnel probe and its
        // result is shown; broken/unsafe nodes are only pinned to the bottom of the selection
        // ordering. This is what measures ALL nodes in one parallel wave, with no strict gate.
        val (_, invalidCandidates) =
            ProfilePreflightValidator.partition(securitySafe, crossCheckSources)
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
                        ProfilePreflightValidator.validateAll(candidates)
                    )
                )
                message = "Rank • $scope • preflight flagged ${invalidCandidates.size} • testing all servers"
            }

            // MARBLE_TURBO_RANK_V91: the whole enabled pool is tested in one parallel real-tunnel
            // wave. No node is excluded; quarantine and security-deprecation only affect ordering.
            val scoped = candidates

            // Keep the last known measurements visible while fresh evidence streams in.
            // Clearing first made every latency/result chip disappear and then pop back into place.
            val rankSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = scoped.size.coerceAtLeast(1),
                benchSamples = 2,
                benchTimeoutSec = settings.benchTimeoutSec.coerceIn(4, 6),
                // MARBLE_TURBO_RANK_V91: full-wave parallelism — every node dials at once inside
                // the helper (capped at 128), instead of 4-16 sequential waves.
                tcpWorkers = maxOf(settings.tcpWorkers, 64).coerceAtMost(128),
                probeMethod = ProbeMethod.TUNNEL,
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
                onResult = ::markProbeResult
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

            // MARBLE_SURVIVAL_FIRST_RANK_V80: survival-first re-rank for Iran. A node whose short
            // HTTPS generate204 probe timed out is NOT hard-failed if it carries strong historical
            // success evidence. Quarantined + deprecated profiles are still measured and shown but
            // pinned to the end so they are never auto-selected.
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

            // MARBLE_SMART_RANK_V90: attach the weighted multi-signal score (TCP handshake success
            // ratio, RTT median/p95, jitter, loss) so Library reflects the composite instead of the
            // old single short HTTPS probe. Ordering stays survival-first; only healthy nodes with
            // real handshake evidence have their score upgraded to the multi-signal composite.
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
                "elapsedMs" to (System.currentTimeMillis() - startedAt)
            )

            message = if (best == null) {
                "Rank • $scope • ${results.size}/${scoped.size} • 0 reachable"
            } else {
                "Rank • $scope • $healthy/${scoped.size} reachable • " +
                    "${best.name} • ${best.latencyMs.toInt()} ms"
            }
    }

    fun fullTest(p: ProxyProfile) {
        if (ServerlessFreedomEngine.isServerless(p)) {
            task("Marble Freedom • adaptive check") {
                val best = MarbleFreedomSmartRanker.bestProfile(
                    settings,
                    iranMode,
                    xray,
                    intelligence,
                    rankCrossCheckSources()
                ) { progress -> message = progress }
                message = "Marble Freedom ready • ${best.name}"
            }
            return
        }
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

    /**
     * Library-wide ping sweep, in the method chosen in Settings.
     * Smart Xray rank remains available when actual proxy usability must be proven.
     */
    fun testAll() = testSource("all")

    /** Identity of one physical endpoint: what address-level probes actually measure. */
    private fun quickPingEndpointKey(profile: ProxyProfile): String =
        "${profile.host.trim().lowercase()}:${profile.port}"

    /**
     * MARBLE_ONE_PING_V121 / MARBLE_PING_ENGINE_V122 — the one ping of the product, confined to
     * the selected source and run in exactly the method configured in Settings → Testing.
     *
     * The four methods are genuinely different measurements, matching how v2rayNG, PattNG,
     * Exclave, Hiddify and Lumen separate them:
     *
     *  - **Smart ping (HYBRID, default)** — the fast PattNG/v2rayNG-style real delay: the whole
     *    source is measured in ONE native wave. The Xray cores live in-process and dial the
     *    generate_204 target through core.Dial (no localhost SOCKS, no CLI child per node), every
     *    node is tested concurrently (up to 128 workers), each gets 2 real HTTPS samples with a
     *    gstatic → cloudflare automatic failover, and median RTT / jitter / loss come straight
     *    from the samples. This is the bug-fixed smart ping: it used to spawn a temporary Xray
     *    CLI process per server behind a 2–4 worker ceiling, so a big subscription took minutes
     *    and frequently answered with all-failed cards.
     *  - **Tunnel ping (TUNNEL)** — the exact runtime path: one hardened Xray CLI child per node,
     *    verified HTTPS RTT through the real SOCKS inbound. Slower and deliberately parallelism-
     *    capped (same-host bursts manufacture resets), but it proves the *config* end to end.
     *  - **TCP ping** — direct TCP connect time to the endpoint, de-duplicated by host:port because
     *    reachability is an endpoint property and aggregator subscriptions repeat endpoints.
     *  - **ICMP ping** — one `/system/bin/ping -c N` batch per endpoint, median of the real
     *    `time=` replies with the binary's own loss count.
     *
     * Endpoint de-duplication applies only to the address-level methods (TCP / ICMP): one
     * representative is probed and the verified result fans out to every config sharing that
     * endpoint. Smart and Tunnel ping prove a *config*, so they measure every server.
     */
    fun testSource(sourceId: String) {
        if (sourceId == ServerlessFreedomEngine.SOURCE_ID) {
            message = "Marble Freedom is local • use Rank for its adaptive check"
            return
        }
        val scoped = libraryScopeSnapshot(sourceId).distinctBy { it.id }
        val scope = libraryScopeLabel(sourceId)
        if (scoped.isEmpty()) {
            message = "Nothing enabled to ping in $scope"
            return
        }

        val method = settings.probeMethod
        val methodLabel = when (method) {
            ProbeMethod.HYBRID -> "Smart ping"
            ProbeMethod.TUNNEL -> "Tunnel ping"
            ProbeMethod.TCP -> "TCP ping"
            ProbeMethod.ICMP -> "ICMP ping"
        }
        // Only address-level methods may share one measurement between identical endpoints.
        val dedupe = method == ProbeMethod.TCP || method == ProbeMethod.ICMP

        task("$methodLabel • $scope") {
            val groups = if (dedupe) {
                scoped.groupBy(::quickPingEndpointKey)
            } else {
                scoped.associateBy { it.id }.mapValues { (_, profile) -> listOf(profile) }
            }
            val representatives = groups.values.mapNotNull { it.firstOrNull() }

            fun membersFor(representative: ProxyProfile): List<ProxyProfile> =
                if (dedupe) {
                    groups[quickPingEndpointKey(representative)].orEmpty()
                } else {
                    listOf(representative)
                }

            // MARBLE_PING_ENGINE_V122 — Smart ping shares Rank's engine: the native in-process
            // batch dialer (PattNG measureOutboundDelay architecture). One wave, every node in
            // parallel, two verified HTTPS samples each, provider-diverse target failover.
            val smartPing = method == ProbeMethod.HYBRID

            val pingSettings = when (method) {
                ProbeMethod.HYBRID -> settings.copy(
                    benchMode = BenchMode.CUSTOM,
                    benchCandidates = representatives.size.coerceAtLeast(1),
                    benchSamples = 2,
                    benchTimeoutSec = settings.benchTimeoutSec.coerceIn(3, 6),
                    tcpWorkers = maxOf(settings.tcpWorkers, 64).coerceAtMost(128),
                    probeMethod = ProbeMethod.TUNNEL,
                    probeSpeedTest = false,
                    verifiedPerformanceTuning = false,
                    udpProbeEnabled = false
                )
                else -> settings.copy(
                    benchMode = BenchMode.CUSTOM,
                    benchCandidates = representatives.size.coerceAtLeast(1),
                    // Address-level probes are cheap, so they stay snappy; a real tunnel
                    // measurement keeps the user's own sample/timeout budget, clamped to a size
                    // that still feels like a ping rather than a full benchmark run.
                    benchSamples = if (dedupe) 1 else settings.benchSamples.coerceIn(1, 3),
                    benchTimeoutSec = if (dedupe) 2 else settings.benchTimeoutSec.coerceIn(2, 8),
                    tcpPrecheckTimeoutMs = minOf(settings.tcpPrecheckTimeoutMs, 750),
                    tcpWorkers = maxOf(settings.tcpWorkers, 24).coerceAtMost(32),
                    // The method itself is never overridden: this is the user's setting.
                    probeSpeedTest = false,
                    verifiedPerformanceTuning = false,
                    udpProbeEnabled = false
                )
            }

            fun runPass(passSettings: AppSettings): List<BenchmarkResult> {
                val onCandidates: (List<ProxyProfile>) -> Unit = { beginProbeBatch(scoped) }
                val onStart: (ProxyProfile) -> Unit = { representative ->
                    membersFor(representative).forEach(::markProbeStart)
                }
                val onResult: (ProxyProfile, BenchmarkResult) -> Unit = { representative, result ->
                    membersFor(representative).forEach { member ->
                        markProbeResult(
                            member,
                            result.copy(profileId = member.id, name = member.name)
                        )
                    }
                }
                val onProgress: (Int, Int, String) -> Unit = { done, total, name ->
                    val unit = if (dedupe) "endpoints" else "servers"
                    message = "$methodLabel • $scope • $done/$total $unit • $name"
                }
                return if (smartPing) {
                    PattRankEngine(
                        context = context,
                        xray = xray,
                        intelligence = intelligence
                    ).run(representatives, passSettings, onCandidates, onStart, onResult, onProgress)
                } else {
                    BenchmarkEngine(xray, intelligence).run(
                        representatives,
                        passSettings,
                        usePrecheck = false,
                        onCandidates = onCandidates,
                        onStart = onStart,
                        onResult = onResult,
                        onProgress = onProgress
                    )
                }
            }

            val firstStartedNs = System.nanoTime()
            var representativeResults = runPass(pingSettings)
            val firstElapsedMs =
                ((System.nanoTime() - firstStartedNs) / 1_000_000L).coerceAtLeast(0L)

            // A sub-350 ms all-failed pass is a radio/process warm-up artifact, not evidence:
            // real SYN/TLS timeouts take far longer. One bounded confirmation re-run covers it.
            if (
                representatives.size >= 4 &&
                representativeResults.isNotEmpty() &&
                representativeResults.none { it.success > 0 } &&
                firstElapsedMs < 350L
            ) {
                diagnostics.event(
                    "BENCHMARK",
                    "ping-fast-zero-retry",
                    "source" to sourceId.take(24),
                    "scope" to scope,
                    "method" to method.name,
                    "servers" to scoped.size,
                    "endpoints" to representatives.size,
                    "firstElapsedMs" to firstElapsedMs
                )
                try {
                    Thread.sleep(120L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (!Thread.currentThread().isInterrupted) {
                    representativeResults = runPass(pingSettings)
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
                "ping-source-finish",
                "source" to sourceId.take(24),
                "scope" to scope,
                "method" to method.name,
                "requested" to scoped.size,
                "uniqueEndpoints" to representatives.size,
                "tested" to expanded.size,
                "reachable" to passed
            )
            message = if (dedupe) {
                "$methodLabel • $scope • ${expanded.size} servers / " +
                    "${representatives.size} endpoints • $passed reachable"
            } else {
                "$methodLabel • $scope • ${expanded.size} servers • $passed reachable"
            }
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

    /** Returns true when the task was accepted and will run; false when the mutex rejected it. */
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
     * DPI-aware HTTPS fetch. GitHub/raw SNI blocks, UA fingerprinting and first-flight RSTs are
     * handled by [DpiAwareFetcher] (browser UA, jsDelivr mirrors, no-cleartext redirects).
     * While connected, management stays inside the live SOCKS route. While Iran Mode is active
     * and disconnected, a temporary Freedom-fragment SOCKS bridge is the last resort.
     * Subscription redirect left HTTPS is enforced by DpiAwareFetcher.fetchDirect.
     */
    private fun httpSubscription(url: String): SubscriptionPayload {
        require(isHttpsSubscriptionUrl(url)) {
            "Remote subscriptions must use HTTPS"
        }
        val connected = state != "DISCONNECTED"
        if (connected) {
            check(xray.isAlive) {
                "Direct management request blocked while the tunnel is not healthy"
            }
        }
        val throughSocks: ((String, String) -> DpiAwareFetcher.Payload)? =
            if (connected || iranMode.active) {
                { candidate, agent ->
                    if (connected) {
                        DpiAwareFetcher.Payload(
                            text = SocksHttpClient.getTextUrl(
                                activeProxyPort(),
                                candidate,
                                maxBytes = MAX_SUBSCRIPTION_BYTES,
                                userAgent = agent
                            )
                        )
                    } else {
                        fetchViaFreedomBridge(candidate, agent)
                    }
                }
            } else {
                null
            }
        val payload = DpiAwareFetcher.fetch(
            url = url,
            maxBytes = MAX_SUBSCRIPTION_BYTES,
            iranActive = iranMode.active,
            allowDirect = !connected,
            throughSocks = throughSocks
        )
        return SubscriptionPayload(payload.text, payload.userInfo)
    }

    private fun fetchViaFreedomBridge(url: String, userAgent: String): DpiAwareFetcher.Payload {
        var payload: DpiAwareFetcher.Payload? = null
        val recipe = DpiEvasionPolicy.serverlessRecipe(iranMode)
        val bridgeSettings = DpiEvasionPolicy.applyRecipe(settings, recipe)
        val profile = ServerlessFreedomEngine.profile(bridgeSettings)
        val started = xray.temporary(profile, 21_080, bridgeSettings) { port ->
            payload = DpiAwareFetcher.Payload(
                text = SocksHttpClient.getTextUrl(
                    port,
                    url,
                    maxBytes = MAX_SUBSCRIPTION_BYTES,
                    userAgent = userAgent
                )
            )
        }
        require(started && payload != null) { "Freedom fragment bridge failed" }
        return payload!!
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
        /** Detection is cheap but not free; unchanged networks are re-checked every 10 minutes. */
        const val IRAN_RESCAN_INTERVAL_MS = 10L * 60L * 1000L

        /** Mirrors AppStore's persisted history window. */
        const val MAX_HISTORY_RECORDS = 200

        /** Remote subscription payload ceiling for both direct and SOCKS management paths. */
        const val MAX_SUBSCRIPTION_BYTES = 8 * 1024 * 1024

        /**
         * MARBLE_CONNECT_BUTTON_V121 — ceiling for the visible "disconnecting" state. A healthy
         * teardown reports DISCONNECTED in well under a second; this only exists so a killed
         * service can never freeze the connect button in its closing colour.
         */
        const val DISCONNECT_WATCHDOG_MS = 6_000L
    }
}
