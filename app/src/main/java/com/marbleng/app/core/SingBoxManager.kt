package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** Both connect and measurements use the same translator, Android DNS bridge, config check and
 * process runner. Temporary tests are isolated and limited to a small native-child pool even when
 * the user requests hundreds of TCP workers. A measurement cannot overwrite or stop the live
 * session.
 *
 * MARBLE_SINGBOX_LINK_AUTHORITY_V156 — a profile is offered to the core as *every* sing-box
 * config it can be expressed as ([SingBoxConfigBuilder.candidateBuilds]), and the first one
 * `sing-box` itself accepts wins. One reader being behind on link syntax can therefore no longer
 * kill a node, and [lastStartStrategy] records which reader is actually carrying the session. */
class SingBoxManager(private val context: Context) {
    @Volatile private var session: SingBoxProcessSession? = null
    private var liveDns: AndroidDnsBridge.Lease? = null
    private val bootstrap = AndroidDnsBridge(context)
    private val rules = SingBoxRuleSetStore(context)
    @Volatile var lastStartError: String = ""
        private set
    @Volatile var lastStartPhase: String = "idle"
        private set
    @Volatile var lastSelfHealNotes: List<String> = emptyList()
        private set
    /** Which reader produced the config the live session is running. "" before the first start. */
    @Volatile var lastStartStrategy: String = ""
        private set
    @Volatile var apiPort: Int = 0
        private set
    @Volatile var intelligence: MarbleIntelligence? = null

    /**
     * MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — what the previous [stop] could not finish, or `""`.
     * A stop that returns while the child still lives is the event that makes the next start pay
     * `bind: address already in use`; the note keeps that causal chain visible instead of letting
     * the next start look like an independent failure.
     */
    @Volatile var lastStopEvidence: String = ""
        private set

    /**
     * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the last verdict about *this* binary, kept so a
     * broken core is paid for once instead of once per node, per reader and per retry.
     */
    @Volatile var lastSelfTest: SingBoxCoreSelfTest.Verdict? = null
        private set

    /**
     * MARBLE_SINGBOX_STARTUP_GATE_V162 — what the last start-up wait observed, kept so Bug Finder
     * and the diagnostics log can tell a core that never opened its inbound (a core that cannot
     * serve this device) from one whose tunnel came up and whose controller never answered (a
     * working route with a missing measurement surface). Those two printed the same sentence
     * before, and they have opposite remedies.
     */
    @Volatile var lastStartReadiness: StartReadiness? = null
        private set
    private val selfTestLock = Any()
    val isAlive: Boolean get() = session?.isAlive == true

    /**
     * MARBLE_PING_SPEED_V160 — the pool of measurement cores this device was granted.
     *
     * Both URL tests and sing-box Real delays spawn one native child per node, so a sweep is
     * only as fast as the number of children that may run at once. The floor is the pool this
     * product has always shipped; a device that reports enough cores and heap gets a wider one
     * (see [MeasurementCoreBudget]), and a device that does not runs exactly what it ran before.
     */
    val measurementCoreCeiling: Int get() = measurementCeiling

    // The width is granted here rather than in the companion's own initialiser because it needs
    // a Context, and a manager has one before any measurement can ask for a slot.
    init {
        grantMeasurementSlots(MeasurementCoreBudget.read(context))
    }
    private val bin: File get() = File(context.applicationInfo.nativeLibraryDir, CoreEngineInfo.SINGBOX_BINARY)
    val isInstalled: Boolean get() = bin.isFile && bin.length() > 1024
    val logFile: File get() = File(context.filesDir, "logs/singbox.log")

    @Synchronized fun start(profile: ProxyProfile, port: Int, settings: AppSettings = AppSettings()): Boolean {
        stop()
        lastStartError = ""
        lastSelfHealNotes = emptyList()
        lastStartStrategy = ""
        lastStartReadiness = null
        lastStartPhase = "config"
        var dns: AndroidDnsBridge.Lease? = null
        try {
            check(isInstalled) { "core-install: sing-box extended is missing from this APK" }
            // MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — whether this binary can start is not a
            // per-node and not a per-reader question, so the canary answers it once and the
            // connect stops here instead of after a spawn, a crash and three refusals.
            requireUsableCore()
            require(port in 1..65535) { "core-config: Invalid local SOCKS port" }
            // MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — a port held by a process nothing tracks
            // (an orphaned core from a killed app process, or a child whose handle a superseded
            // start dropped) used to be paid for as a full spawn → FATAL → BLOCKED round trip,
            // once per retry, forever: the 09:18–09:26 cluster of `bind: address already in
            // use` with `alive=false` on both engines. The port is made actually free —
            // same-UID core binaries are reaped, foreign holders are reported — before the
            // first child is spawned, and what cannot be freed fails here as a `core-port:`
            // local fault instead of as three dead readers per candidate node.
            val reclaim = CorePortGuard.reclaim(
                probe = CorePortGuard.androidProbe(),
                port = port,
                nativeLibDir = context.applicationInfo.nativeLibraryDir,
                coreBinaries = listOf(CoreEngineInfo.SINGBOX_BINARY, CoreEngineInfo.XRAY_BINARY)
            )
            if (reclaim.reaped.isNotEmpty()) {
                lastSelfHealNotes += reclaim.evidence.ifBlank {
                    "reclaimed local port $port from a stale core process"
                }
            }
            if (!reclaim.available) error(reclaim.evidence)
            dns = bootstrap.acquire()
            val controller = freePort(excluding = port)
            val secret = UUID.randomUUID().toString()
            // Not `candidates`: a local val of that name would shadow the reader function.
            val builds = candidates(profile, settings, port, controller, secret, dns.port, false)
            lastStartPhase = "check-and-start"
            // A live session gets the strictest *document* start: every candidate is validated
            // with `sing-box check`, and the child has to survive the V157 settle window.
            //
            // MARBLE_SINGBOX_STARTUP_GATE_V162 — what it no longer gets is a controller gate. The
            // controller is an internal service this core binds in its last start-up stage, after
            // every outbound's post-start walk; before this it was half of the readiness
            // condition, so a core that was already carrying traffic was killed and the session
            // went BLOCKED because a *diagnostic* had not finished binding. The tunnel is the
            // inbound hev-socks5-tunnel dials, so that is what the wait is for, and a controller
            // that never answers is recorded as a degraded session instead of a failed connect.
            val started = openFirst(
                candidates = builds,
                configFile = File(context.filesDir, "runtime-singbox.json"),
                logFile = logFile,
                tempDir = File(context.cacheDir, "singbox-tmp"),
                socksPort = port,
                controller = controller,
                secret = secret,
                validate = true,
                awaitApi = true,
                requireController = false,
                settleMs = LIVE_SETTLE_MS
            )
            val readiness = started.first.readiness
            lastStartStrategy = started.second.strategy
            lastStartReadiness = readiness
            lastSelfHealNotes = started.second.notes + if (readiness.controllerMissing) {
                listOf(
                    "the core is carrying traffic, but its Clash controller never answered " +
                        "(${SingBoxProcessSession.CONTROLLER_TIMEOUT_MS} ms): Real delay through " +
                        "the core and the Engine page's live counters are unavailable, the route is not"
                )
            } else {
                emptyList<String>()
            }
            session = started.first
            liveDns = dns
            apiPort = controller
            lastStartPhase = "ready"
            return true
        } catch (error: Exception) {
            dns?.close()
            if (error is InterruptedException) Thread.currentThread().interrupt()
            var message = error.message ?: error.javaClass.simpleName
            // MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — the TOCTOU safety net. The reclaim above
            // closes the window for tracked and untracked stale cores, but a foreign process can
            // still bind between reclaim and spawn. When the core's own FATAL names the bind
            // conflict, the reason is reborn under the `core-port:` prefix: local fault for
            // CoreFailurePolicy and the sweep gate, never a per-server verdict.
            if (SingBoxAndroidRuntime.isPortBindConflict(message) &&
                !message.startsWith("core-port:")
            ) {
                message = "core-port: $message"
            }
            lastStartError = explain(message)
            lastStartPhase = "failed"
            return false
        }
    }

    @Synchronized fun stop() {
        val previous = session
        session = null
        apiPort = 0
        previous?.close()
        lastStopEvidence = previous?.stopEvidence.orEmpty()
        liveDns?.close()
        liveDns = null
        lastStartStrategy = ""
        lastStartPhase = "stopped"
    }

    /**
     * Every sing-box config this profile can be expressed as, in preference order, each one
     * already through the Android preflight. The connect path and the measurement path build
     * through here and nowhere else, so the two can no longer drift apart.
     */
    private fun candidates(profile: ProxyProfile, settings: AppSettings, port: Int, controller: Int,
                           secret: String, dnsPort: Int, forTest: Boolean): List<SingBoxConfigBuilder.Build> {
        val runtimeSettings = if (forTest) measurementSettings(settings) else settings
        val paths = if (forTest) emptyMap() else try { rules.prepare() } catch (error: Exception) {
            throw IllegalStateException("core-assets: bundled sing-box rule sets are missing/corrupt; rebuild the APK", error)
        }
        return SingBoxConfigBuilder.candidateBuilds(profile, runtimeSettings, port, controller, secret,
            "", File(context.filesDir, "singbox-cache.db").absolutePath,
            resolverPool = intelligence?.singBoxResolverPool(settings).orEmpty(),
            ruleSetPaths = paths, bootstrapDnsPort = dnsPort, forTest = forTest)
            .map { built ->
                val hardened = SingBoxConfigDoctor.hardenForAndroid(built.json)
                built.copy(json = hardened.json, notes = (built.notes + hardened.notes).distinct())
            }
    }

    /**
     * MARBLE_SINGBOX_LINK_AUTHORITY_V156 — hands each candidate to the core until one is
     * accepted, and says which one won.
     *
     * Only a *config-class* refusal moves on to the next candidate. A port that will not bind or
     * a core that is out of memory is not a reason to try the same node in another spelling, so
     * it fails immediately with its own message. The refusals of the losing readers are kept as
     * notes so the Engine page can explain why the winner is not the first choice.
     */
    private fun openFirst(
        candidates: List<SingBoxConfigBuilder.Build>,
        configFile: File,
        logFile: File,
        tempDir: File,
        socksPort: Int,
        controller: Int,
        secret: String,
        validate: Boolean,
        awaitApi: Boolean,
        requireController: Boolean = awaitApi,
        settleMs: Long = 0
    ): Pair<SingBoxProcessSession, SingBoxConfigBuilder.Build> {
        check(candidates.isNotEmpty()) { "core-config: no sing-box representation of this profile" }
        val refusals = mutableListOf<String>()
        candidates.forEachIndexed { index, candidate ->
            SingBoxProcessSession.checkInterrupted()
            // Later candidates reuse the same workspace, so the previous attempt's log cannot be
            // mistaken for this one's.
            if (index > 0) runCatching { logFile.writeText("") }
            try {
                val session = SingBoxProcessSession.open(bin, candidate.json, configFile, logFile,
                    tempDir, socksPort, controller, secret, validate = validate, awaitApi = awaitApi,
                    requireController = requireController, settleMs = settleMs)
                // The losing readers' refusals travel with the winner, so the Engine page can say
                // why the config that is running is not the one that was tried first.
                val winner = if (refusals.isEmpty()) candidate else candidate.copy(
                    notes = (candidate.notes + refusals.map {
                        "${candidate.strategy} accepted after an earlier reader was refused: $it"
                    }).distinct()
                )
                return session to winner
            } catch (error: Exception) {
                if (error is InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw error
                }
                val reason = error.message ?: error.javaClass.simpleName
                // MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — the docstring above already said it, the
                // code did not do it: `core-start: exited 1: … bind: address already in use`
                // matched the `core-start:` refusal prefix, so all three readers spawned a core
                // to die on the same port. A port fault is identical for every candidate.
                if (SingBoxAndroidRuntime.isPortBindConflict(reason)) throw error
                if (!isConfigRefusal(reason) || index == candidates.lastIndex) throw error
                refusals += "[${candidate.strategy}] $reason".take(400)
            }
        }
        error("core-config: no sing-box representation of this profile was accepted")
    }

    /** Shared isolated transport for Real Delay, rank, tuner and bootstrap fetches. The
     * measurement goes through the SOCKS inbound, so the Clash controller is never waited for. */
    fun temporary(profile: ProxyProfile, settings: AppSettings, block: (Int) -> Unit): Boolean =
        withTemporary(profile, settings, needsController = false) { _, port -> block(port); true }

    private fun <T> withTemporary(profile: ProxyProfile, settings: AppSettings,
                                  needsController: Boolean,
                                  block: (SingBoxProcessSession, Int) -> T): T {
        SingBoxProcessSession.checkInterrupted()
        check(isInstalled) { "core-install: sing-box extended is missing from this APK" }
        // Before the queue, not after it: a core that cannot start must not occupy a measurement
        // slot while it proves that, and a sweep of N nodes must not spawn N dead children.
        requireUsableCore()
        // Interruptible queue admission. A cancelled batch must not later start another child.
        check(testSlots.tryAcquire(30, TimeUnit.SECONDS)) { "core-busy: measurement capacity is occupied" }
        var directory: File? = null
        var dns: AndroidDnsBridge.Lease? = null
        try {
            SingBoxProcessSession.checkInterrupted()
            directory = File(context.cacheDir, "singbox-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
            dns = bootstrap.acquire()
            val socks = freePort()
            val controller = freePort(excluding = socks)
            val secret = UUID.randomUUID().toString()
            val started = openFirst(
                candidates = candidates(profile, settings, socks, controller, secret, dns.port, true),
                configFile = File(directory, "config.json"),
                logFile = File(directory, "core.log"),
                tempDir = directory,
                socksPort = socks,
                controller = controller,
                secret = secret,
                // A throwaway measurement core skips the separate `check` spawn (run validates
                // the same schema and its exit is classified identically) and only waits for the
                // Clash controller when the measurement is the URL test, which reads it.
                validate = false,
                awaitApi = needsController
            )
            started.first.use { child ->
                return block(child, socks)
            }
        } finally {
            dns?.close()
            directory?.let { workspace ->
                // Bug Finder retains bounded evidence, never a test's credentials/config/cache.
                val tail = SingBoxProcessSession.tail(File(workspace, "core.log"), 32 * 1024)
                if (tail.isNotBlank()) synchronized(diagnosticLock) {
                    runCatching { SingBoxProcessSession.atomicWrite(File(context.cacheDir, "singbox-urltest.log"), tail) }
                }
                workspace.deleteRecursively()
            }
            testSlots.release()
        }
    }

    fun urlTestLive(tag: String, url: String, timeoutMs: Int): CoreUrlTestResult {
        require(tag == SingBoxConfigBuilder.PROXY_TAG) { "Only the selected outbound may be measured" }
        val active = session ?: return CoreUrlTestResult(0, false, "core-unavailable: sing-box is not running")
        return active.delay(url, timeoutMs).copy(live = true)
    }

    fun urlTestProfile(profile: ProxyProfile, settings: AppSettings, url: String, timeoutMs: Int): CoreUrlTestResult =
        urlTestProfileTargets(profile, settings, listOf(url), timeoutMs)

    /**
     * MARBLE_PING_FALSE_FAILED_V159 — one process per profile, short-circuit on success, and one
     * retry when the *child never came up*. The old eager targets.map() started and destroyed a
     * core for EVERY reference URL even after the first one had succeeded.
     *
     * The retry answers a spawn storm (four measurement cores starting at once on a phone), which
     * is a fact about the device, not a verdict about the node — the same mercy
     * [RouteProbe.realDelayHook]'s own attempt loop and `BenchmarkEngine.measure` already extend.
     * A genuinely unbuildable config exits instantly on both attempts, so the retry costs
     * milliseconds where it is pointless and saves a healthy node where it is not. A measurement
     * that actually RAN is never retried here: it already walked every fallback target with a
     * full budget each (see [ProbeTargetWalk]), so repeating it would only double a dead node's
     * cost.
     */
    fun urlTestProfileTargets(profile: ProxyProfile, settings: AppSettings, urls: List<String>, timeoutMs: Int): CoreUrlTestResult {
        var spawnFailure: CoreUrlTestResult? = null
        repeat(2) {
            try {
                return withTemporary(profile, settings, needsController = true) { child, _ ->
                    ProbeTargetWalk.urlTest(urls, timeoutMs) { url, budget -> child.delay(url, budget) }
                }
            } catch (error: Exception) {
                if (error is InterruptedException) { Thread.currentThread().interrupt(); throw error }
                spawnFailure = CoreUrlTestResult(0, false, explain(error.message ?: error.javaClass.simpleName))
            }
        }
        return spawnFailure ?: CoreUrlTestResult(0, false, "urltest-no-target")
    }

    fun urlTestLiveTargets(urls: List<String>, timeoutMs: Int): CoreUrlTestResult =
        ProbeTargetWalk.urlTest(urls, timeoutMs) { url, budget -> urlTestLive(SingBoxConfigBuilder.PROXY_TAG, url, budget) }

    private fun explain(reason: String): String = SingBoxAndroidRuntime.explain(reason)

    // ─────────────────────────────────────────────────────────────────────────────
    // Core self-test — one canary process per binary, not one crash per node
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Asks the binary whether it can start on this device, and remembers the answer.
     *
     * The canary is [SingBoxCoreSelfTest.canaryConfig]: a local inbound plus a `direct` outbound,
     * which is exactly the path the Android nil-interface-monitor crash lived on and which touches
     * no network. Results are cached against the binary's fingerprint, so the cost is one process
     * per installed core — and one process *per APK*, because a new build changes the fingerprint.
     *
     * Returns `null` when the core is not installed or when the probe was cancelled. Callers must
     * only short-circuit on [SingBoxCoreSelfTest.Verdict.coreFault]; an inconclusive verdict is
     * reported but never blocks the engine.
     *
     * Concurrency: one canary at a time, serialized by [selfTestLock], and callers that arrive
     * while it runs re-read the cache instead of running a second one. It deliberately does *not*
     * take a [testSlots] permit — borrowing the measurement pool would let a full sweep stall a
     * connect for seconds waiting on a diagnostic. The price is one extra short-lived process
     * beside the pool, once per installed binary, which is the cheaper of the two mistakes.
     */
    fun selfTest(force: Boolean = false): SingBoxCoreSelfTest.Verdict? {
        if (!isInstalled) return null
        val cache = File(context.filesDir, "singbox-selftest.json")
        if (!force) SingBoxCoreSelfTest.read(cache, bin)?.let { cached ->
            lastSelfTest = cached
            return cached
        }
        return synchronized(selfTestLock) {
            // Re-read: another thread may have finished the same canary while this one waited.
            if (!force) SingBoxCoreSelfTest.read(cache, bin)?.let { cached ->
                lastSelfTest = cached
                return@synchronized cached
            }
            var workspace: File? = null
            try {
                workspace = File(context.cacheDir, "singbox-selftest").apply { mkdirs() }
                val verdict = SingBoxCoreSelfTest.probe(
                    binary = bin,
                    workspace = workspace,
                    socksPort = freePort()
                )
                lastSelfTest = verdict
                // Only a definitive answer is worth caching. An inconclusive one — a cancelled
                // probe, a lost port race, a device that was momentarily out of memory — has to be
                // asked again, or one unlucky canary would disable the self-test until the next
                // APK update changed the binary's fingerprint.
                if (verdict.ok || verdict.coreFault) SingBoxCoreSelfTest.write(cache, verdict)
                recordSelfTest(verdict)
                verdict
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                lastSelfTest
            } catch (error: Exception) {
                lastSelfTest
            } finally {
                workspace?.deleteRecursively()
            }
        }
    }

    /**
     * Throws the verdict's reason when the core is provably unusable, so the connect path and the
     * measurement path both stop *before* they spawn a child that cannot survive start-up.
     */
    private fun requireUsableCore() {
        val verdict = selfTest() ?: return
        if (verdict.coreFault) error(verdict.asFailureReason())
    }

    /** The canary's own evidence, retained exactly like a core log: bounded and credential-free. */
    private fun recordSelfTest(verdict: SingBoxCoreSelfTest.Verdict) {
        if (verdict.ok) return
        synchronized(diagnosticLock) {
            runCatching {
                SingBoxProcessSession.atomicWrite(
                    File(context.cacheDir, "singbox-selftest.log"),
                    if (verdict.reason.isBlank()) "core-selftest: inconclusive" else verdict.reason
                )
            }
        }
    }

    companion object {
        /**
         * MARBLE_REAL_DELAY_SPEED_V156 — the native-child ceiling for measurements.
         *
         * Two was chosen when a measurement cost a `check` spawn, a `run` spawn and a controller
         * wait. With those gone a child is cheap enough to run four of them, which is what turns
         * a 100-node Real delay sweep from minutes into tens of seconds without approaching the
         * process/memory storm a 20-wide pool would cause on a phone.
         */
        const val MAX_TEMPORARY_CORES = 4

        /**
         * MARBLE_SINGBOX_STARTUP_GATE_V162 — the grace window a live connect pays once, after the
         * inbound answers and before the session is handed to the TUN.
         *
         * It is the same window the canary pays ([SingBoxCoreSelfTest.SETTLE_MS]) for the same
         * reason: `box.Start()` opens the inbounds before it walks the outbounds' post-start, so a
         * core that is about to die in an outbound has already published a listening port. The
         * live path used to pay nothing here and wait for the controller instead — which worked,
         * but made a diagnostic the gate of the tunnel (see [start]).
         */
        const val LIVE_SETTLE_MS: Long = SingBoxCoreSelfTest.SETTLE_MS

        private val testSlots = Semaphore(MAX_TEMPORARY_CORES, true)
        private val diagnosticLock = Any()
        private val slotLock = Any()

        /** The live width of the pool. Starts at [MAX_TEMPORARY_CORES] and only ever widens. */
        @Volatile private var measurementCeiling = MAX_TEMPORARY_CORES

        /**
         * MARBLE_PING_SPEED_V160 — widens the measurement pool by [extra] slots, once.
         *
         * A `Semaphore` has no "set permits" operation, and the alternative — sizing it eagerly
         * from a device read in a static initializer — would ask Android about the running
         * device before the app has a Context. Granting permits at construction keeps the floor
         * honest: the pool is [MAX_TEMPORARY_CORES] until a manager exists, and the extra slots
         * are added before the first measurement can ask for one.
         */
        private fun grantMeasurementSlots(extra: Int) {
            if (extra <= 0) return
            synchronized(slotLock) {
                val granted = (measurementCeiling + extra).coerceAtMost(MeasurementCoreBudget.MAX)
                val delta = granted - measurementCeiling
                if (delta > 0) {
                    measurementCeiling = granted
                    testSlots.release(delta)
                }
            }
        }

        /**
         * True when the core rejected the *document*, which is the only thing a different reader
         * can fix. Everything else (ports, permissions, memory, a killed child) would fail
         * identically for every candidate, so walking on would only multiply the wait.
         *
         * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — a crashed core is the strongest form of that
         * rule and is excluded explicitly. The Android nil-interface-monitor panic happened after
         * the document had been accepted and parsed, so all three readers of the same node
         * produced three identical SIGSEGVs and three identical process spawns; the refusal list
         * in the Engine page then explained a crash as if it were a schema disagreement. A crash
         * and the netlink ban are answered once, immediately, and are handed to the engine-level
         * fault path instead.
         */
        internal fun isConfigRefusal(reason: String): Boolean {
            if (SingBoxAndroidRuntime.isUnusableCore(reason)) return false
            if (reason.startsWith("core-crash:")) return false
            // MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — a bind conflict arrives wearing the
            // `core-start:` prefix (the core exited 1), but it is a property of the device's
            // loopback, not of the document: every reader binds the same port. It also arrives
            // rewritten to `core-port:` at the catch site; this predicate closes the door on the
            // raw form as well, so no future call site can walk on it again.
            if (SingBoxAndroidRuntime.isPortBindConflict(reason)) return false
            val value = reason.lowercase()
            return value.startsWith("core-config:") ||
                value.startsWith("core-start:") ||
                SingBoxConfigDoctor.isEngineLevelFault(reason)
        }
        private fun freePort(excluding: Int = 0): Int {
            repeat(8) {
                ServerSocket().use { server ->
                    server.bind(InetSocketAddress("127.0.0.1", 0))
                    if (server.localPort != excluding) return server.localPort
                }
            }
            error("core-port: unable to allocate distinct loopback ports")
        }

        fun measurementSettings(settings: AppSettings): AppSettings = settings.copy(
            routingMode = RoutingMode.PROXY_ALL, routeGeoIpTags = "", routeGeoSiteTags = "",
            routeDirectDomains = "", routeProxyDomains = "", routeBlockDomains = "",
            routeDirectIps = "", routeBlockIps = "", routeBypassPrivate = false,
            routeBlockAds = false, routingRulesJson = "[]", iranDomesticDirect = false
        )
    }
}
