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
    val isAlive: Boolean get() = session?.isAlive == true
    private val bin: File get() = File(context.applicationInfo.nativeLibraryDir, CoreEngineInfo.SINGBOX_BINARY)
    val isInstalled: Boolean get() = bin.isFile && bin.length() > 1024
    val logFile: File get() = File(context.filesDir, "logs/singbox.log")

    @Synchronized fun start(profile: ProxyProfile, port: Int, settings: AppSettings = AppSettings()): Boolean {
        stop()
        lastStartError = ""
        lastSelfHealNotes = emptyList()
        lastStartStrategy = ""
        lastStartPhase = "config"
        var dns: AndroidDnsBridge.Lease? = null
        try {
            check(isInstalled) { "core-install: sing-box extended is missing from this APK" }
            require(port in 1..65535) { "core-config: Invalid local SOCKS port" }
            dns = bootstrap.acquire()
            val controller = freePort(excluding = port)
            val secret = UUID.randomUUID().toString()
            // Not `candidates`: a local val of that name would shadow the reader function.
            val builds = candidates(profile, settings, port, controller, secret, dns.port, false)
            lastStartPhase = "check-and-start"
            // A live session gets the strictest start: every candidate is validated with
            // `sing-box check` and its Clash controller is awaited, because the URL test and the
            // Engine page both read that controller while traffic flows.
            val started = openFirst(
                candidates = builds,
                configFile = File(context.filesDir, "runtime-singbox.json"),
                logFile = logFile,
                tempDir = File(context.cacheDir, "singbox-tmp"),
                socksPort = port,
                controller = controller,
                secret = secret,
                validate = true,
                awaitApi = true
            )
            lastStartStrategy = started.second.strategy
            lastSelfHealNotes = started.second.notes
            session = started.first
            liveDns = dns
            apiPort = controller
            lastStartPhase = "ready"
            return true
        } catch (error: Exception) {
            dns?.close()
            if (error is InterruptedException) Thread.currentThread().interrupt()
            lastStartError = explain(error.message ?: error.javaClass.simpleName)
            lastStartPhase = "failed"
            return false
        }
    }

    @Synchronized fun stop() {
        val previous = session
        session = null
        apiPort = 0
        previous?.close()
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
        awaitApi: Boolean
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
                    tempDir, socksPort, controller, secret, validate = validate, awaitApi = awaitApi)
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

    /** One process per profile, short-circuit on success. The old eager targets.map() started and
     * destroyed a core for EVERY reference URL even after the first one had succeeded. */
    fun urlTestProfileTargets(profile: ProxyProfile, settings: AppSettings, urls: List<String>, timeoutMs: Int): CoreUrlTestResult = try {
        withTemporary(profile, settings, needsController = true) { child, _ ->
            testTargets(urls, timeoutMs) { url, budget -> child.delay(url, budget) }
        }
    } catch (error: Exception) {
        if (error is InterruptedException) { Thread.currentThread().interrupt(); throw error }
        CoreUrlTestResult(0, false, explain(error.message ?: error.javaClass.simpleName))
    }

    fun urlTestLiveTargets(urls: List<String>, timeoutMs: Int): CoreUrlTestResult =
        testTargets(urls, timeoutMs) { url, budget -> urlTestLive(SingBoxConfigBuilder.PROXY_TAG, url, budget) }

    private fun testTargets(urls: List<String>, timeoutMs: Int,
                            measure: (String, Int) -> CoreUrlTestResult): CoreUrlTestResult {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceIn(500, 30_000).toLong())
        var last = CoreUrlTestResult(0, false, "urltest-no-target")
        for (url in urls.distinct().take(3)) {
            SingBoxProcessSession.checkInterrupted()
            val left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).toInt()
            if (left <= 0) break
            last = measure(url, left)
            if (last.ok) return last
        }
        return last
    }

    private fun explain(reason: String): String = if (SingBoxAndroidRuntime.isNetlinkBan(reason))
        "$reason — ${SingBoxAndroidRuntime.NETLINK_REMEDIATION}" else reason

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
        private val testSlots = Semaphore(MAX_TEMPORARY_CORES, true)
        private val diagnosticLock = Any()

        /**
         * True when the core rejected the *document*, which is the only thing a different reader
         * can fix. Everything else (ports, permissions, memory, a killed child) would fail
         * identically for every candidate, so walking on would only multiply the wait.
         */
        internal fun isConfigRefusal(reason: String): Boolean {
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
