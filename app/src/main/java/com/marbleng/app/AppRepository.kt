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
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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

    /**
     * MARBLE_SINGBOX_CORE_V151 — the second engine.
     *
     * Owned here, next to [xray], because the repository is the object that already serialises
     * every connect/disconnect and holds the settings both cores are configured from. The VPN
     * service reaches it through [MarbleApplication] exactly like it reaches [xray].
     */
    val singBox: SingBoxManager = SingBoxManager(context).also { xray.singBox = it }

    /**
     * MARBLE_SINGBOX_CORE_V151 — the engine the current settings select, plus the last start
     * report from whichever core that is.
     *
     * Both are read straight from the managers instead of a cached copy: each core writes its own
     * start phase and error, and the Engine page shows the pair that belongs to the engine the
     * user actually picked. The `else` arm always names [xray], so neither getter can read itself.
     */
    val activeCoreEngine: CoreEngine get() = parseCoreEngine(settings.coreEngineId)

    val coreStartPhase: String
        get() = if (activeCoreEngine == CoreEngine.SINGBOX) singBox.lastStartPhase else xray.lastStartPhase

    val coreStartError: String
        get() = if (activeCoreEngine == CoreEngine.SINGBOX) singBox.lastStartError else xray.lastStartError

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

    // IntelligenceStatus includes a SQLite count + thermal inspection. Coalesce it on a worker so
    // a DB writer can never make the Android input thread wait on the HealthDb monitor.
    private val statusWorker = Executors.newSingleThreadExecutor()
    private val statusRefreshInFlight = AtomicBoolean(false)

    // `busy` is UI state, not a cross-thread lock. This atomic gate is the actual task mutex.
    private val taskInFlight = AtomicBoolean(false)

    /**
     * MARBLE_PING_CANCEL_V156 — the handle of the task a sweep is running on, so a cancel can
     * interrupt the worker instead of merely asking it nicely. Kept only while a task is in
     * flight; `null` means there is nothing to cancel.
     */
    private val activeTask = AtomicReference<Future<*>?>(null)

    // MARBLE_SMART_RANK_V90: debounce + single-flight gate for the Rank action, so a tap storm can
    // never re-run the whole preflight + benchmark pool (observed: 9 triggers in 7s, zero results).
    private val rankGate = SmartRankGate()

    // Iran Mode scanning runs on its own thread so a detection sweep can never block or be blocked
    // by a user-visible task such as a subscription refresh.
    private val iranScanner = Executors.newSingleThreadExecutor()
    private val iranScanInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val iranPolicyGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    val intelligence = MarbleIntelligence(context).also {
        // MARBLE_SINGBOX_PROTOCOLS_V153 — the sing-box config writer now consumes the same
        // resolver pool that the Xray hardener already races. Without this wiring the second
        // engine would keep emitting the two configured literals forever while the evidence
        // loop was reordering a pool it never saw.
        singBox.intelligence = it
    }
    private val notifier = SmartNotifier(context)
    private val iranDetector = IranModeDetector(context, intelligence)
    private val bugFinder = BugFinder(context, xray, singBox)
    private val diagnostics = RuntimeDiagnostics(context)

    val profiles = mutableStateListOf<ProxyProfile>().apply { addAll(store.loadProfiles()) }
    val subscriptions = mutableStateListOf<Subscription>().apply { addAll(store.loadSubscriptions()) }
    val history = mutableStateListOf<ConnectionRecord>().apply { addAll(store.loadHistory()) }

    var settings by mutableStateOf(store.settings()); private set

    // MARBLE_MANUAL_BUCKET_V122 — Manual is a permanent local bucket, never a gated option.
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
     * A concrete Library source can receive user imports; "all" is only a view.
     * MARBLE_MANUAL_BUCKET_V122 — Manual always accepts imports, no toggle involved.
     */
    private fun resolveLibraryTarget(id: String): LibraryTarget? = when {
        id == "manual" -> LibraryTarget("manual", "Manual")
        id == "all" || id.isBlank() -> null
        else -> subscriptions.firstOrNull { it.id == id }?.let { LibraryTarget(it.id, it.name) }
    }

    /**
     * A concrete source id that can receive imports; anything else falls back to the
     * always-on Manual bucket instead of refusing the import.
     */
    fun intakeTargetOrManual(id: String): String = resolveLibraryTarget(id)?.id ?: "manual"

    val libraryProfiles: List<ProxyProfile>
        get() = profiles.toList().distinctBy { it.id }

    private fun enabledProfilesSnapshot(): List<ProxyProfile> =
        profiles.toList().distinctBy { it.id }

    /**
     * Resolve the exact Library source selected by the user.
     *
     * Fail closed: a stale/unknown source id returns an empty set — never the whole Library.
     * "all" expands to every enabled subscription profile.
     */
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

    /**
     * MARBLE_SINGBOX_LINK_AUTHORITY_V156 — make the stored config agree with the link it came
     * from, for every node that has one.
     *
     * A profile keeps two copies of the same truth: the share link the user subscribed to
     * (`raw`) and the Xray JSON derived from it at import time (`configJson`). The Xray engine
     * runs the JSON and, until now, the sing-box engine ran a translation of that same JSON — so
     * anything the importer got wrong, or that a later app version learned to read better, was
     * frozen into every node forever and neither core could reach the real configuration.
     *
     * This pass re-reads each link with the *current* parser and repairs the stored copy where
     * the two disagree on anything that decides how the node dials (protocol, endpoint,
     * transport, security) or where the stored copy is missing entirely. It is deliberately
     * narrow:
     *
     *  - only single-line share links are touched, so a pasted JSON document or a subscription
     *    blob a user edited by hand is never rewritten;
     *  - identity (id, name, source, ownership) always comes from the stored profile;
     *  - a link-only scheme the parser stores with a blank `configJson` (tuic, anytls) never
     *    blanks a working stored config — it is handed to the sing-box core's own parser instead;
     *  - it is idempotent: after one repair the comparison matches and nothing is written again.
     *
     * Both engines consume the result, which is what makes "the original config link is the
     * authority" true for Xray as well as for sing-box.
     */
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

    /** The repaired copy of [profile], or `null` when the stored one already agrees with its link. */
    private fun reconcileWithLink(profile: ProxyProfile): ProxyProfile? {
        val link = SingBoxConfigBuilder.shareLink(profile) ?: return null
        val derived = ProxyParser.parseInput(link).singleOrNull() ?: return null
        val derivedJson = derived.configJson.takeIf { it.isNotBlank() } ?: return null
        val agrees = profile.configJson.isNotBlank() &&
            profile.scheme.equals(derived.scheme, ignoreCase = true) &&
            profile.host == derived.host &&
            profile.port == derived.port &&
            storedEndpoint(profile.configJson) == "${derived.host}:${derived.port}"
        if (agrees) return null
        return profile.copy(
            scheme = derived.scheme,
            configJson = derivedJson,
            host = derived.host,
            port = derived.port,
            transport = derived.transport,
            security = derived.security
        )
    }

    /** The `address:port` a stored Xray JSON dials, or "" when it has no readable proxy outbound. */
    private fun storedEndpoint(configJson: String): String = runCatching {
        val outbounds = JSONObject(configJson).optJSONArray("outbounds") ?: return@runCatching ""
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val protocol = outbound.optString("protocol").lowercase()
            if (protocol in setOf("freedom", "blackhole", "dns", "loopback")) continue
            val settings = outbound.optJSONObject("settings") ?: continue
            val server = settings.optJSONArray("vnext")?.optJSONObject(0)
                ?: settings.optJSONArray("servers")?.optJSONObject(0)
                ?: settings
            val address = server.optString("address").ifBlank { server.optString("server") }
            val port = server.optInt("port", 0).takeIf { it > 0 } ?: server.optInt("server_port", 0)
            if (address.isNotBlank() && port > 0) return@runCatching "$address:$port"
        }
        ""
    }.getOrDefault("")

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

    /**
     * MARBLE_REMEMBERED_PING_V160 — the last measurement of every server, restored on launch.
     *
     * The table used to be pure memory: closing the app (or Android killing the process, which
     * happens nightly) threw away every ping the user had paid for, and the Servers list came
     * back blank. The rows are read from disk at construction and written back every time a
     * measurement lands, so the number next to a node survives restarts, updates and process
     * death — while a node that no longer exists is dropped on the way in, because a measurement
     * of a server the library does not have is not a memory, it is clutter.
     */
    var benchmarks by mutableStateOf(rememberedBenchmarks()); private set
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
    /**
     * MARBLE_PING_CANCEL_V156 — a cancel has been asked for and the sweep is unwinding. The stop
     * controls read this so the icon does not flip back to "start" while the workers are still
     * putting their sockets down, which is the moment a second tap would start a new sweep.
     */
    var probeCancelling by mutableStateOf(false); private set
    private val probeCancelGate = ProbeCancelGate()

    /**
     * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the reason a sweep stopped because the *device*
     * could no longer measure (a crashed core, a missing binary, no live tunnel), or `""`.
     *
     * This is the state that was missing when 17 identical SIGSEGVs were published as
     * `reachable = 0 of 17`: the number was right and the meaning was wrong. A sweep that stops
     * here says why, and the surfaces that report a batch read this before they turn a stopped
     * batch into a verdict about servers.
     */
    var probeLocalFault by mutableStateOf(""); private set
    private val probeLocalFaultGate = ProbeLocalFaultGate()
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

    private fun beginProbeBatch(candidates: List<ProxyProfile>) {
        // A new sweep starts un-stopped: the fault that ended the previous one is evidence about
        // that batch, not a permanent verdict on the device (the core may have been updated, the
        // tunnel may be up now). Reset *here* and not in the posted block below — the sweep polls
        // probeShouldStop immediately after its candidates are announced, and a latch cleared one
        // main-thread turn later would abort the new sweep at its first node. This is why
        // [endProbeBatch] resets its latch outside postToMain too.
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

    /**
     * MARBLE_PING_CANCEL_V156 — stops every kind of bulk measurement in the product.
     *
     * "Ping all", a group ping, the Home group ping, Smart and Rank all funnel into the same
     * probe batch, so one control ends all of them. Cancellation is cooperative *and* immediate:
     *
     *  - the flag is polled at every candidate boundary, so nothing new is started;
     *  - the task's worker thread is interrupted, so a worker already blocked on a socket, a
     *    core spawn or the measurement semaphore unwinds now instead of at its own timeout;
     *  - measurements that already finished stay on screen. A cancel means "stop", not "throw
     *    away what you learned".
     *
     * Safe to call at any time: with no sweep live it does nothing at all.
     */
    fun cancelProbes() {
        if (!probeActive && !probeCancelling) return
        // Idempotent: only the tap that actually arms the latch publishes the unwind.
        if (!probeCancelGate.arm()) return
        diagnostics.event(
            "BENCHMARK",
            "probe-cancel-requested",
            "done" to probeDone,
            "total" to probeTotal
        )
        message = "Cancelling • keeping the ${probeDone} measurements already made"
        postToMain { probeCancelling = true }
        // Interrupt last: the flag must be visible before the worker wakes up, otherwise a
        // worker that catches the interrupt could immediately pick up the next candidate.
        runCatching { activeTask.get()?.cancel(true) }
    }

    private fun markProbeStart(profile: ProxyProfile) = postToMain {
        probeRunning = probeRunning + profile.id
        probeCurrentName = profile.name
        probeLastName = profile.name
        probeLastOutcome = "TESTING"
        probeLastLatencyMs = 0
    }

    /** Publishes one finished node immediately; the card updates while the batch continues. */
    private fun markProbeResult(profile: ProxyProfile, result: BenchmarkResult) {
        // MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — observed here, on the worker, *before* anything
        // is posted to the main thread: a fault that will end the sweep has to end it now. Posting
        // it first would let the other workers pick up their next candidates while the message is
        // still queued, which is precisely how one broken core became 17 "dead" servers.
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

    /**
     * Ends a sweep because the device cannot measure, not because anyone asked it to stop.
     *
     * Runs at most once per sweep ([ProbeLocalFaultGate.trip] is idempotent). It publishes the
     * reason, arms the cancel latch so every engine sees one uniform stop, and interrupts the
     * worker so a thread blocked in a core spawn or a socket unwinds immediately — the same three
     * things [cancelProbes] does, because a fault stop and a user stop must not leave the product
     * in two different states. Measurements already made are kept: they are real, and throwing
     * them away would make the honest report look like a failure to report.
     */
    private fun stopProbesForLocalFault(reason: String) {
        val summary = ProbeLocalFaultGate.summary(reason)
        diagnostics.event(
            "BENCHMARK",
            "probe-stopped-local-fault",
            "reason" to ProbeLocalFaultGate.headline(reason),
            "done" to probeDone,
            "total" to probeTotal
        )
        // Arm first: the engines poll one predicate, and a fault stop must look like a stop to
        // every sweep implementation, including the ones that only know about cancels.
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
        // Both latches belong to the batch. probeLocalFault (the *reason*) is deliberately kept:
        // it is the last sweep's evidence, exactly like probeLastOutcome, and the surfaces that
        // report a batch need it to say "stopped by a device fault" instead of "0 reachable".
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

    /**
     * The stop predicate handed to every sweep. Two latches, one question:
     *
     *  - [probeCancelGate] — a person pressed stop;
     *  - [probeLocalFaultGate] — the device proved it cannot measure anything else (a crashed
     *    core is the same for every remaining node, so continuing only multiplies the crash
     *    count and then reports it as a server verdict).
     *
     * Both unwind through the same path, and both keep the measurements already made.
     */
    private val probeShouldStop: () -> Boolean = {
        probeCancelGate.isRequested || probeLocalFaultGate.isTripped
    }

    /**
     * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the summary line a finished batch publishes.
     *
     * "$passed reachable" is a statement about servers, and it is only true if this device could
     * measure them. When the sweep stopped itself on a local fault, printing "0 of 17 reachable"
     * is the exact sentence that made a crashed core look like a dead network: those 17 nodes were
     * never contacted, so nothing at all was learned about any of them. The counts stay — they are
     * real, and a stopped batch still shows what it did measure — but the meaning is corrected,
     * and the full reason is one Bug Finder scan away.
     *
     * Reads the gate first and the published state second: the gate is tripped synchronously on
     * the worker that found the fault, while [probeLocalFault] reaches the UI one main-thread turn
     * later, and a summary written in between must not lose the reason.
     */
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

    // MARBLE_HOME_SESSION_EVIDENCE_V110
    // Wall-clock start of the session currently carrying traffic. Every Home style reads this one
    // value, so the uptime readout can never disagree between presentations.
    var connectedSinceMs by mutableStateOf(0L); private set

    /** One-shot connection ping. It is measured on demand, never on a repeating timer. */
    var connectionPingMs by mutableStateOf(0); private set
    var connectionPingState by mutableStateOf(ConnectionPingState.IDLE); private set
    // MARBLE_HOME_V137 — the *why* behind a FAILED tunnel ping, so the Home live-ping panel can
    // tell a socket timeout ("timeout") from an unreachable endpoint ("unreachable") instead of
    // collapsing every failure into one word. Empty while IDLE/MEASURING/MEASURED.
    var connectionPingFailure by mutableStateOf(""); private set
    private val connectionPingInFlight = AtomicBoolean(false)

    // MARBLE_IRAN_AWARE_PING — Layer 0/2/3 signals surfaced to the Home readout: the latency
    // capsule turns its status glyph into ✅/⚠️/🚫, the sparkline segments injected samples, and
    // the national-event banner is driven by [nationalEventCause].
    var homePingInjectedReset by mutableStateOf(false); private set
    var homePingStabilityClass by mutableStateOf(""); private set
    var nationalEventCause by mutableStateOf(""); private set
    var nationalEventConfidence by mutableStateOf(0f); private set
    // MARBLE_HOME_V137 — the selected-server endpoint ping for the DISCONNECTED / CONNECTING
    // states. The tunnel ladder above needs a live SOCKS port, so it cannot answer while no
    // traffic flows; this measures the *endpoint* of the server the connect button would act on
    // (same Settings → Testing method, same family policy, tunnelPort = 0) and is the value the
    // Home live-ping panel shows before CONNECTED. Measured on demand, never on a timer.
    var selectedPingMs by mutableStateOf(0); private set
    var selectedPingState by mutableStateOf(ConnectionPingState.IDLE); private set
    var selectedPingFailure by mutableStateOf(""); private set
    private val selectedPingInFlight = AtomicBoolean(false)

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

    /**
     * MARBLE_GEO_READY_GATE_V145 — latest gate explanation ("" when the full policy is running),
     * kept in state so the UI never touches the filesystem to render it.
     */
    var geoGateNote by mutableStateOf("")
        private set

    init {
        migrateLocalSourceOwnershipIfNeeded()
        installUrlTestHook()
        installRealDelayHook()
        // MARBLE_SINGBOX_LINK_AUTHORITY_V156 — every stored node is checked against the share
        // link it came from, so both engines run a config that is faithful to that link.
        reconcileProfilesWithTheirLinks()
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
        // MARBLE_GEO_READY_GATE_V145 — the geo databases fetch themselves in the background.
        // Geo routing is disabled while they are absent (see RoutingEngine.withGeoAssetGate), so
        // the only thing left to do is get them here without ever blocking a connect: this runs
        // off the main thread, takes no task slot, never touches `busy` and swallows failure —
        // a missing database costs the geo split for this session and nothing else.
        ensureGeoAssetsInBackground()
    }

    /**
     * Publish the URL test: sing-box extended's own delay controller, and nothing else.
     *
     * MARBLE_URLTEST_SINGBOX_ONLY_V156 — the Xray branch this hook used to carry measured
     * something different behind the same name (a Kotlin HTTPS HEAD through Xray's SOCKS inbound,
     * with no controller and no unified-delay accounting), so the same server reported two
     * incomparable numbers depending on which core was selected. The method now belongs to the
     * engine that owns it and refuses on any other; Settings stops offering it there too.
     */
    private fun installUrlTestHook() {
        RouteProbe.urlTestHook = { profile, probeSettings, timeoutMs ->
            val targets = DelayTest.candidates(probeSettings.delayTestUrl)
            val effective = intelligence.effectiveSettings(profile, probeSettings)
            val sameLiveProfile = activeProfileId == profile.id && state == "CONNECTED"
            val result = if (effective.coreEngine() == CoreEngine.SINGBOX) {
                // The live session's controller answers for the route that is actually carrying
                // traffic; any other profile gets its own throwaway core, because the delay
                // endpoint can only measure the outbound of the process it belongs to.
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

    /**
     * MARBLE_REAL_DELAY_TRUTH_V156 — Real delay with no tunnel up.
     *
     * The method's promise is "how long a real page takes through the tunnel". Before this hook
     * existed it could only keep that promise while a tunnel was already connected; disconnected,
     * it answered `no-live-tunnel` → FAILED, which is why Real delay looked broken on the Home
     * ping button and on every gate-exempt protocol (Hysteria2, WireGuard, and every pasted Xray
     * JSON, whose scheme is `json`). A missing tunnel is not a verdict about the server, so the
     * probe now builds one for the occasion: the same throwaway core the sweep path uses, on the
     * selected engine, timed with the identical HTTPS round-trip measurement.
     *
     * A core that never comes up is retried once — a spawn storm is a fact about the device, not
     * a dead node.
     */
    private fun installRealDelayHook() {
        RouteProbe.realDelayHook = { profile, timeoutMs, samples, probeSettings ->
            val effective = intelligence.effectiveSettings(profile, probeSettings)
            // MARBLE_PING_FALSE_FAILED_V159 — every DelayTest candidate origin is walked inside
            // ONE throwaway core (never one core per target). A momentarily filtered primary
            // used to fail every sample of a healthy route here while the Rank sweep walked on
            // to the secondary; the walk now hands the next origin the identical budget through
            // the same tunnel, and only a route silent against every candidate is failed.
            val targets = DelayTest.candidates(probeSettings.delayTestUrl)
            var measured: RouteProbe.ProbeResult? = null
            fun attempt() {
                runCatching {
                    xray.temporary(profile, 0, effective) { port ->
                        measured = RouteProbe.tunnelHttpsMeasureTargets(
                            socksPort = port,
                            timeoutMs = timeoutMs,
                            samples = samples,
                            urls = targets
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

    /**
     * MARBLE_GEO_READY_GATE_V145 — silent, non-blocking preparation of the routing databases.
     *
     * Only runs when the user's own policy actually needs an asset that is not on disk, and only
     * while no tunnel is up (a management download belongs on the underlay). Everything it can
     * fail at is best effort by design.
     */
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

    /**
     * MARBLE_GEO_READY_GATE_V145 — "" while the policy runs exactly as written, otherwise one
     * line naming the database the engine is waiting for. Surfaced on the Routing page.
     */
    fun geoGateReason(): String {
        val status = runCatching { xray.routingAssetStatus() }.getOrNull() ?: return ""
        return RoutingEngine.geoDowngradeReason(settings, status.geoIpReady, status.geoSiteReady)
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
     *
     * MARBLE_MEMORY_TRIM_V135 — the attached runtime log showed trim levels 20 and 40 arriving
     * repeatedly while PSS sat near 318 MB and the OS eventually revoked the VPN permission. The
     * old ladder released too little too late (bug report at 40, benchmarks only at 60), so the
     * reconstructable Kotlin-side caches kept occupying pages while the kernel was visibly asking
     * for them back. Everything that can be rebuilt by a later tap or refresh is now released one
     * step earlier; the heap footprint is reported inside the trim event so the next log shows
     * whether the trim actually freed Java-side memory.
     */
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
        postToMain {
            if (level >= 15) privacy = null
            if (level >= 20) {
                bugReport = null
                // Server intel (exit IP / flag / geo) is rebuilt by the next refresh cycle.
                serverIntel = null
            }
            // MARBLE_REMEMBERED_PING_KEEP_V163 — the benchmark table is NOT trimmed under memory
            // pressure any more. It is at most 400 small rows (a few tens of KB), and trimming
            // it to the active node was the reason "last ping" vanished at random: the next
            // completed probe merged its one result into the trimmed table and persisted THAT,
            // so the remembered pings of every other server were overwritten on disk by a
            // TRIM_MEMORY callback the user never saw. The large caches above (privacy report,
            // bug report, server intel) are what a trim is for.
        }
    }

    /**
     * Resolve a config. Library mutations pass sourceId so identical configs in two sources remain
     * independent rows; engine callers may intentionally resolve by canonical config id only.
     */
    fun profile(id: String, sourceId: String? = null): ProxyProfile? {
        return if (!sourceId.isNullOrBlank()) {
            profiles.firstOrNull { it.id == id && it.subscriptionId == sourceId }
        } else {
            profiles.firstOrNull { it.id == id }
        }
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

    fun updateSettings(v: AppSettings) {
        val debugChanged = settings.debugModeEnabled != v.debugModeEnabled
        val updateChecksWereEnabled = settings.appUpdateCheckEnabled
        // MARBLE_URLTEST_SINGBOX_ONLY_V156 — the URL test is the sing-box extended core's own
        // delay controller, so switching the engine away from it must not leave a stored choice
        // that can only ever fail. This is the single decision point: every writer of
        // `coreEngineId` and every restore of a stored preference passes through here.
        val usableProbeMethod = v.probeMethod.forEngine(v.coreEngine())
        val probeMethodDemoted = usableProbeMethod != v.probeMethod
        // MARBLE_INTELLIGENCE_ALWAYS_ON_V143 — Marble Intelligence is a permanent product
        // contract. No caller, screen or stored preference may switch it off.
        settings = v.copy(intelligenceEnabled = true, probeMethod = usableProbeMethod)
        if (!v.appUpdateCheckEnabled) {
            postToMain { availableUpdate = null }
        } else if (!updateChecksWereEnabled) {
            dismissedUpdateTag = ""
        }
        ensureLibrarySourceSelectionValid()
        store.saveSettings(settings)
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
        return IdentityGuard.apply(tuned)
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

    /**
     * MARBLE_SINGBOX_CORE_V151 — switching the tunnel core.
     *
     * The engine is the user's choice, and switching it is a real act rather than a flag flip:
     * the other core's process cannot keep the tun, so a live tunnel is closed first and the user
     * reconnects into the engine they just picked. The chosen measurement method is retained;
     * both Real Delay and URL Test have selected-core implementations.
     */
    fun setCoreEngine(engine: CoreEngine) {
        val previous = parseCoreEngine(settings.coreEngineId)
        if (previous == engine) return
        val next = settings.copy(coreEngineId = engine.id)
        if (state == "CONNECTED" || state == "CONNECTING" || state == "BLOCKED") stopVpn()
        updateSettings(next)
        diagnostics.event("CORE", "engine-switch", "from" to previous.id, "to" to engine.id)
        message = "Tunnel core set to ${CoreEngineInfo.displayName(engine)}. Reconnect to run it."
    }

    /** MARBLE_SINGBOX_CORE_V151 — the URL every real-delay measurement is timed against. */
    fun setDelayTestUrl(url: String) {
        updateSettings(settings.copy(delayTestUrl = url.trim()))
        // The hook is rebuilt on every measurement from the current settings, so nothing else has
        // to be reset here; the message just tells the user the next ping already uses it.
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

        // MARBLE_PING_METHODS_V148 — the Home readout obeys Settings → Tests → Ping exactly like
        // every other measurement. The method gets a real SOCKS port when the tunnel is up, so
        // Smart / Real test / HTTP GET / HTTP HEAD measure through the live route while
        // TCP Connect / TCP (recommended) / ICMP intentionally measure the server address itself.
        postToMain {
            connectionPingMs = 0
            connectionPingState = ConnectionPingState.MEASURING
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
                        settings = settings
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
                // A disconnect or reconnect while the probe was in flight invalidates the result.
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
                        connectionPingMs = 0
                        connectionPingState = ConnectionPingState.FAILED
                        connectionPingFailure = classifyPingFailure(probe?.failureReason)
                    }
                }
                homePingInjectedReset = probe?.injectedResetSuspected == true || measured < 20
                homePingStabilityClass = stability?.stabilityClass?.name ?: ""
                nationalEventCause = if (attribution?.rankingFreeze == true) {
                    CausalAttribution.shortKey(attribution.cause)
                } else {
                    ""
                }
                nationalEventConfidence = (attribution?.confidence ?: 0.0).toFloat()
            }
        }
    }

    /**
     * MARBLE_HOME_V137 — collapse a raw probe failure reason into the three words the Home
     * live-ping panel may show: `timeout` (the socket budget expired), `unreachable` (the
     * endpoint answered with nothing usable / refused), or `error` (misconfigured target,
     * missing tunnel, anything else). Pure, so the exact mapping is unit-testable.
     */
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

    /**
     * MARBLE_HOME_V137 / MARBLE_PING_METHODS_V148 — the one ping entry Home calls. Connected and
     * disconnected paths both run the Settings → Testing method; connected simply supplies the
     * live SOCKS port so tunnel-capable methods measure through the route. One tap never reports
     * two latencies.
     */
    fun measureHomePing() {
        if (state == "CONNECTED") {
            measureConnectionPing()
        } else {
            measureSelectedPing()
        }
    }

    /**
     * MARBLE_HOME_V137 / MARBLE_PING_TRUTH_V147 — endpoint ping of the server the connect button
     * would act on, for the DISCONNECTED / CONNECTING states where the tunnel ladder has no port
     * to race through. Dispatches to [RouteProbe.measureUnified] with tunnelPort = 0: Smart runs
     * the verified TCP+TLS gate and Real test honestly falls back to that same gate (there is no
     * way to prove a config without spawning a tunnel, which this one-shot button does not do),
     * so the method, the family plan and the 20 ms honest floor are identical to the connected
     * path. Async, single-flight, measured-only.
     */
    fun measureSelectedPing() {
        if (!selectedPingInFlight.compareAndSet(false, true)) return
        val target = homeRoute() ?: lastProfile()
        if (target == null) {
            postToMain {
                selectedPingInFlight.set(false)
                selectedPingMs = 0
                selectedPingState = if (target == null) ConnectionPingState.IDLE else ConnectionPingState.FAILED
                selectedPingFailure = if (target == null) "" else "error"
            }
            if (target == null) message = "Select a server first • then ping it"
            return
        }
        // A successful ping belongs to this exact server; a selection change or a connect
        // while the probe is in flight invalidates it.
        val targetId = target.id
        val targetSource = target.subscriptionId
        postToMain {
            selectedPingMs = 0
            selectedPingState = ConnectionPingState.MEASURING
            selectedPingFailure = ""
        }
        io.execute {
            // MARBLE_PING_CONTROL_V145 — same user-owned budget as every other measurement.
            val timeoutMs = settings.pingTimeoutMs()
            val samples = settings.pingSampleCount()
            val probeResult = runCatching {
                RouteProbe.measureUnified(
                    profile = target,
                    method = settings.probeMethod,
                    tunnelPort = 0,
                    samples = samples,
                    timeoutMs = timeoutMs,
                    settings = settings
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
                // MARBLE_IRAN_AWARE_PING_L0/L2 — persist the Layer-0 signal (injected reset /
                // silent timeout travel with the ProbeResult) and append the Layer-2 sample.
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
                            // The tunnel took over (tunnel ping owns the readout now) or the user
                            // moved on to another server: this result belongs to nobody.
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
                            selectedPingMs = 0
                            selectedPingState = ConnectionPingState.FAILED
                            selectedPingFailure = classifyPingFailure(probeResult?.failureReason)
                        }
                    }
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
    fun addManualProfile(
        draft: ManualConfigDraft,
        targetSubscriptionId: String = "manual"
    ): Boolean {
        // MARBLE_PING_DOES_NOT_BLOCK_SELECTION_V165 — a probe sweep no longer blocks adding a
        // manual server. A ping owns a snapshot of its scope and merges its results by profile
        // id, so a node added mid-sweep simply joins the library without a fresh measurement.
        // Only genuinely conflicting tasks (refresh/import) still wait for the sweep to finish.
        if (busy && !probeActive) {
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
                    // A QR code may encode a subscription URL, so this runs through the smart
                    // intake instead of straight config parsing (which used to mint a bogus
                    // proxy profile out of the provider URL).
                    val addedId = importClipboard(decoded, targetSubscriptionId)
                    selectLibrarySource(addedId ?: intakeTargetOrManual(targetSubscriptionId))
                }
            }
        }
    }

    /**
     * MARBLE_QR_CAMERA_V122 — a live camera frame decoded to text lands exactly like a picked
     * QR image: configs are imported, subscription URLs become real subscriptions.
     */
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

    /**
     * MARBLE_SMART_INTAKE_V122 — the one smart intake for pasted/imported text, used by the
     * Library + menu, the Add-page import box and decoded QR payloads.
     *
     * Config links and JSON go through [importText]; one or more bare subscription URLs become
     * real subscriptions (added, then refreshed) instead of being mis-parsed into bogus proxy
     * profiles — the old paste-a-sub bug. Returns the first added (or already-known)
     * subscription id so the UI can land on it, or null when the text was handled as configs.
     */
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
        // An authenticated HTTP(S) proxy config is user:pass@host[:port] with NO path. A
        // subscription URL that merely carries credentials (/sub/xxxx?token=...) must never be
        // mistaken for one, or the sub would again land as a bogus proxy profile.
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
        // MARBLE_REMEMBERED_PING_V160 — a re-written config is a new node: its old measurement
        // belongs to a server that no longer exists and must not come back after a restart.
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

    /** Make a durable Manual copy of any node, including subscription-owned nodes. */
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
                // MARBLE_SELECT_IS_NOT_CONNECT_V121 — a deleted server cannot stay selected.
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

    /**
     * Count nodes in one subscription whose most recent stored benchmark explicitly failed
     * the requested evidence type. SMART (endpoint-gate verdict) and TUNNEL (real config verdict)
     * stay separate.
     */
    fun failedSubscriptionNodeCount(id: String, probeKind: String): Int {
        val kind = probeKind.trim().uppercase()
        if (kind !in setOf("SMART", "TUNNEL")) return 0
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
        if (kind !in setOf("SMART", "TUNNEL")) {
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
        persistBenchmarks()
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

    /** Reassigns a profile to another subscription bucket (or "manual") so nodes can move between library sources. */
    fun lastProfile(): ProxyProfile? {
        val id = store.lastProfileId()
        if (id.isNotBlank()) {
            val sourceId = store.lastProfileSourceId()
            val exact = profile(id, sourceId)
            val candidate = (exact ?: profile(id))
            if (candidate != null) return candidate
        }

        // No stored profile: check connection history in reverse to seamlessly restore the most
        // recent enabled library node.
        val fromHistory = history.asReversed().asSequence()
            .mapNotNull { rec -> profile(rec.profileId) }
            .firstOrNull()
        if (fromHistory != null) return fromHistory

        return null
    }

    /**
     * MARBLE_SELECT_IS_NOT_CONNECT_V121 — remember the user's chosen server without touching the
     * tunnel. The choice is persisted with the same key a successful connection writes, so Home,
     * Quick Tile and the next app start all agree on which server the connect button will use.
     *
     * MARBLE_HOME_SELECTION_SYNC_V165 — connecting also lands here first: the user's choice is
     * recorded the moment the handshake starts, not only when it succeeds, so a connection that
     * fails still leaves Home and the Servers page showing exactly the server the user picked.
     */
    fun selectProfile(p: ProxyProfile) {
        diagnostics.event("APP", "select-server", "profile" to p.id.take(12), "name" to p.name.take(80))
        val changed = selectedProfileId != p.id || selectedProfileSourceId != p.subscriptionId
        postToMain {
            selectedProfileId = p.id
            selectedProfileSourceId = p.subscriptionId
            // MARBLE_HOME_V137 — a selection change invalidates the endpoint ping: the old
            // number described a different server. The tunnel ping is session-bound and only
            // clears on disconnect, so it is untouched here.
            if (changed) {
                selectedPingMs = 0
                selectedPingState = ConnectionPingState.IDLE
                selectedPingFailure = ""
            }
        }
        io.execute { runCatching { store.setLastProfileRef(p.id, p.subscriptionId) } }
    }

    /** True when [p] is the server the connect button would act on. */
    fun isSelectedProfile(p: ProxyProfile): Boolean =
        selectedProfileId.isNotBlank() &&
            selectedProfileId == p.id &&
            (selectedProfileSourceId.isBlank() || selectedProfileSourceId == p.subscriptionId)

    /**
     * MARBLE_DURABLE_TUNNEL_INTENT_V133 — true while the user's last explicit instruction was
     * "be connected" and no new instruction has replaced it. A process kill — including Android's
     * `REASON_PACKAGE_UPDATE` kill during an app or core update — leaves it set, which is exactly
     * what makes the tunnel restorable instead of silently gone.
     */
    fun tunnelIntentActive(): Boolean = runCatching { store.tunnelIntentActive() }.getOrDefault(false)

    /** Retires the tunnel intent after a startup that Android blocked or that failed outright. */
    fun clearTunnelIntent(reason: String) {
        diagnostics.event("APP", "tunnel-intent-cleared", "reason" to reason)
        io.execute { runCatching { store.setTunnelIntentActive(false) } }
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
            message = "No servers yet • add a subscription or import configs first"
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
                connectionPingFailure = ""
                // A new session starts unmeasured on both channels: the endpoint probe belongs
                // to the pre-connect state, the tunnel probe to this session.
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

            // Serialize/persist outside MainActivity. SharedPreferences.apply() is asynchronous, but
            // building the 200-record JSON string on the input thread was not.
            val historySnapshot = history.toList()
            io.execute {
                // MARBLE_LAST_ROUTE_V37
                // Successful connection is durable user intent.
                runCatching { store.setLastProfileRef(p.id, p.subscriptionId) }
                // MARBLE_DURABLE_TUNNEL_INTENT_V133 — written together with the route reference so a
                // process kill (including the REASON_16 package-update kill) can never leave the two
                // disagreeing about whether the user wanted to be connected.
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
        privacy = null
        runCatching { scanIranMode() }
        // MARBLE_HOME_SELECTION_SYNC_V165 — connecting is itself a selection (see selectProfile):
        // even if this handshake later fails, the chosen server stays what Home shows.
        selectProfile(p)
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
        selectProfile(p)
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
        // MARBLE_DURABLE_TUNNEL_INTENT_V133 — an explicit user disconnect is the only thing that
        // retires the tunnel intent. Without this the app would offer to restore a tunnel the user
        // deliberately closed.
        io.execute { runCatching { store.setTunnelIntentActive(false) } }
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
        persistBenchmarks()
    }

    /**
     * MARBLE_REMEMBERED_PING_V160 — the measurements the app opens with.
     *
     * A result whose node is gone is dropped rather than shown: a subscription refresh can
     * renumber every row it owns, and a latency attached to a server the library cannot open is
     * not a remembered ping, it is a ghost.
     */
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

    /**
     * MARBLE_REMEMBERED_PING_V160 — writes the table back without touching the input thread.
     *
     * JSON for a few hundred rows is milliseconds, but this runs after every completed node of a
     * sweep and the thread it would otherwise block is the one drawing that sweep's progress.
     */
    private fun persistBenchmarks() {
        val snapshot = benchmarks.toList()
        io.execute { runCatching { store.saveBenchmarks(snapshot) } }
    }

    private fun mergeBenchmarks(fresh: List<BenchmarkResult>) {
        if (fresh.isEmpty()) return
        val stampedAt = System.currentTimeMillis()
        // The stamp is the moment a measurement becomes history rather than a live reading, so it
        // is written once here — the single place a result enters the table — and never guessed.
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
            // MARBLE_SMART_PING_V122 / MARBLE_PING_METHODS_V148 — electing the connected route
            // needs real-tunnel evidence; a light Smart/address-level gate must never choose
            // which server carries traffic.
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

    /**
     * Real Xray ranking confined to the exact Library source selected by the user.
     * Unknown source ids fail closed to zero candidates instead of falling back to all profiles.
     */
    fun smartRankSource(sourceId: String) {
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
                (CoreFailurePolicy.isLocal(r.failureReason) || r.failureReason.contains("inconclusive", true) ||
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

    /**
     * Library-wide ping sweep, in the method chosen in Settings.
     * Smart Xray rank remains available when actual proxy usability must be proven.
     */
    fun testAll() = testSource("all")

    /**
     * MARBLE_HOME_PING_ROUTE_GROUP_V146 — the Home ping button measures the subscription that
     * the route SHOWN on the Home page belongs to.
     *
     * The route on the page is whatever the connect button acts on (the deck's own resolution:
     * the active route, else the selected one, else the remembered one). When the user taps the
     * pulse icon their question is "how is the subscription I am looking at doing?", so the sweep
     * covers exactly that route's group. `"all"` is never substituted: a route always belongs to
     * a concrete source, and a route that somehow resolved without one degrades to the Manual
     * bucket instead of silently sweeping every subscription.
     *
     * The per-server ping of that same route is still one tap away on the status banner, and the
     * group-chip ping remains available on the Servers page, so neither question lost its answer.
     */
    fun pingHomeGroup() {
        val route = homeRoute()
        val sourceId = HomePingScope.sourceIdFor(route)
        // Unknown ids fail closed inside libraryScopeSnapshot (empty set), never a full sweep.
        testSource(sourceId)
    }

    /**
     * The route the Home page is currently showing — the deck's own resolution, in one place.
     * Active (carrying traffic), else selected (the connect button's target), else remembered.
     */
    fun homeRoute(): ProxyProfile? =
        profile(activeProfileId, activeProfileSourceId)
            ?: profile(selectedProfileId, selectedProfileSourceId)
            ?: lastProfile()

    /** Human-readable name of the group [pingHomeGroup] would measure. */
    fun homeGroupPingLabel(): String =
        libraryScopeLabel(HomePingScope.sourceIdFor(homeRoute()))

    /** True while a ping sweep covering the current Home group is running. */
    val homeGroupPingRunning: Boolean
        get() = probeActive

    /** Identity of one physical endpoint: what address-level probes actually measure. */
    private fun quickPingEndpointKey(profile: ProxyProfile): String =
        "${profile.host.trim().lowercase()}:${profile.port}"

    /**
     * MARBLE_ONE_PING_V121 / MARBLE_PING_TRUTH_V147 — the one ping of the product, confined to
     * the selected source.
     *
     * There is no longer a hidden "quick TCP ping" that silently overrode the user's choice: this
     * runs exactly the method configured in Settings → Testing (Smart ping by default), which is
     * the same method the Home ping button and every other measurement in the app use. Two entry
     * points can no longer report two different latencies for the same server.
     *
     * Endpoint de-duplication still applies to Smart ping, where reachability really is a
     * host:port property and aggregator subscriptions repeat the same endpoint dozens of times:
     * one representative is probed and the verified result is fanned out to every config sharing
     * that endpoint. The real tunnel test proves a *config*, so it is measured per server,
     * exactly as ranking does.
     */
    fun testSource(sourceId: String) {
        pingProfiles(
            profiles = libraryScopeSnapshot(sourceId),
            scopeLabel = libraryScopeLabel(sourceId),
            scopeId = sourceId
        )
    }

    /**
     * MARBLE_SERVERS_GROUP_PING_V145 — ping an explicit set of servers under one label.
     *
     * The Servers page can group by country as well as by source, and a country bucket has no
     * source id: routing its ping through [testSource] would resolve "country:de" to "no such
     * source" and measure nothing. The sweep itself is identical for both — this is the entry
     * point that takes the servers directly.
     */
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
        // MARBLE_SMART_PING_V122 / MARBLE_PING_METHODS_V148 — Smart and every address-level
        // method's verdict is an endpoint (host:port) property: a subscription repeating the same
        // endpoint N times genuinely has one thing to measure, so it shares one verified result
        // among those members exactly like before. Real test proves a *config* (protocol +
        // account + route), so it stays per server.
        val dedupe = method.isEndpointLevel()

        task("$methodLabel • $scope") {
            val groups = if (dedupe) {
                scoped.groupBy(::quickPingEndpointKey)
            } else {
                scoped.associateBy { it.id }.mapValues { (_, profile) -> listOf(profile) }
            }
            val representatives = groups.values.mapNotNull { it.firstOrNull() }
            // MARBLE_PING_CONTROL_V145 — a sweep runs the user's budget, full stop.
            //
            // The three numbers below used to be rewritten here: `benchSamples = 1` and
            // `benchTimeoutSec = 2` for every address-level method, and a concurrency of
            // `max(tcpWorkers, 24)..32`. That is why a subscription full of working servers came
            // back red on a slow link — every node was judged by a single handshake with a
            // two-second deadline, 24 of them fired at once — and why raising the timeout in
            // Settings changed nothing. The ping budget (Settings › Tests › Ping) is now the one
            // and only source of these values, and the method is still never overridden.
            val quickSettings = settings.copy(
                benchMode = BenchMode.CUSTOM,
                benchCandidates = representatives.size.coerceAtLeast(1),
                benchSamples = settings.pingSampleCount(),
                benchTimeoutSec = PingBudget.timeoutSec(settings.pingTimeoutSec),
                tcpPrecheckTimeoutMs = settings.tcpPrecheckTimeoutMs,
                tcpWorkers = settings.pingWorkers(),
                probeSpeedTest = false,
                verifiedPerformanceTuning = false,
                udpProbeEnabled = false
            )

            fun membersFor(representative: ProxyProfile): List<ProxyProfile> =
                if (dedupe) {
                    groups[quickPingEndpointKey(representative)].orEmpty()
                } else {
                    listOf(representative)
                }

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
                    val unit = if (dedupe) "endpoints" else "servers"
                    message = "$methodLabel • $scope • $done/$total $unit • $name"
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
                    "ping-fast-zero-retry",
                    "source" to sourceId.take(24),
                    "scope" to scope,
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
    fun applyGeoAssetSource(sourceId: String) {
        val source = RoutingDefaults.sourceById(sourceId)
        val next = if (source.id == "custom") {
            settings.copy(geoAssetSourceId = source.id)
        } else {
            settings.copy(
                geoAssetSourceId = source.id,
                geoIpUrl = source.geoIpUrl,
                geoSiteUrl = source.geoSiteUrl
            )
        }
        updateSettings(next)
    }

    fun setRoutingRules(rules: List<com.marbleng.app.model.RoutingRule>) {
        updateSettings(settings.copy(routingRulesJson = RoutingEngine.serializeRules(rules)))
    }

    /**
     * MARBLE_ROUTING_PRESETS_V136 — replace the rule list with a curated preset. The mode, the
     * geo source and the expert text lists are untouched, so this is always reversible.
     */
    fun applyRoutingPreset(preset: RoutingPresets.Preset) {
        setRoutingRules(RoutingPresets.materialize(preset))
        message = when {
            state != "DISCONNECTED" ->
                "${preset.title} preset saved • reconnect to apply it to the active tunnel"
            else -> "${preset.title} preset applied • ${preset.rules.size} rules"
        }
    }

    /** One-off offline route diagnosis for the Routing page simulator. */
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
            // MARBLE_MEASURED_FAMILY_V133 — Bug Finder must describe the configuration the tunnel
            // actually runs, not the stored preferences. Reporting "IPv6 preferred, IPv4 raced after
            // 60 ms" from the raw settings while the live route had measured IPv6 as unhealthy is how
            // a real defect hid behind a healthy-looking diagnostics line.
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
                // MARBLE_RESOLVER_EVIDENCE_V134 — the denominator for the resolver-health rate. The
                // core log rotates on every live start, so while connected its contents and this
                // window describe the same session; disconnected there is no honest window and the
                // check falls back to the absolute reading instead of inventing a rate.
                tunnelUptimeMs = if (state == "CONNECTED" && connectedSinceMs > 0L) {
                    (System.currentTimeMillis() - connectedSinceMs).coerceAtLeast(0L)
                } else {
                    0L
                },
                // MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — why the last sweep stopped, when it
                // stopped for a reason of its own instead of finishing. Without it the report
                // shows a batch of failures and no way to tell a dead network from a dead core.
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

        val future = io.submit(Runnable {
            try {
                // A cancelled task interrupts this pooled thread. The interrupt is normally
                // consumed by the InterruptedException that ends the block, but if it landed
                // between two interruptible calls the flag would survive into the *next* task on
                // this thread and cancel work nobody asked to cancel. Both ends are cleared.
                Thread.interrupted()
                block()
            } catch (t: Throwable) {
                if (t is InterruptedException) {
                    // MARBLE_PING_CANCEL_V156 — a cancelled sweep is an outcome, not a failure.
                    // The measurements that already landed were published as they landed.
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
                // No card may be left spinning if a batch aborts.
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

        // Last resort while no tunnel is up: borrow one. A subscription the underlay cannot
        // reach is still reachable through any working server the user already owns.
        val bootstrapped = if (livePort <= 0) fetchThroughTemporaryTunnel(url) else null
        if (bootstrapped != null) return bootstrapped

        throw attempt.exceptionOrNull()
            ?: IllegalStateException("Subscription fetch failed on every transport")
    }

    /** The SOCKS port of a tunnel that is genuinely up, or 0 when there is none to borrow. */
    private fun liveSocksPortOrZero(): Int {
        if (state != "CONNECTED") return 0
        if (!(if (activeCoreEngine == CoreEngine.SINGBOX) singBox.isAlive else xray.isAlive)) return 0
        return activeProxyPort().takeIf { it in 1..65535 } ?: 0
    }

    /**
     * MARBLE_SUBSCRIPTION_REACH_V145 — fetch a blocked subscription through a throwaway tunnel.
     *
     * Tries the remembered route first, then the best-ranked alternatives, and stops at the
     * first server that delivers the payload. The temporary core is always torn down by
     * [XrayManager.temporary]; nothing here changes the user's selection or session state.
     */
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

    /** At most three known-good servers worth spending a bootstrap handshake on. */
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
