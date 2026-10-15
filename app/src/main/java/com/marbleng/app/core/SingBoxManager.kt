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

class SingBoxManager(private val context: Context) {
    @Volatile private var session: SingBoxProcessSession? = null
    private val rules = SingBoxRuleSetStore(context)
    @Volatile var lastStartError: String = ""
        private set
    @Volatile var lastStartPhase: String = "idle"
        private set
    @Volatile var lastSelfHealNotes: List<String> = emptyList()
        private set
    @Volatile var lastStartStrategy: String = ""
        private set
    @Volatile var apiPort: Int = 0
        private set
    @Volatile private var apiSecret: String = ""
    @Volatile var intelligence: MarbleIntelligence? = null

    @Volatile var lastStopEvidence: String = ""
        private set

    @Volatile var lastSelfTest: SingBoxCoreSelfTest.Verdict? = null
        private set

    @Volatile var lastStartReadiness: StartReadiness? = null
        private set
    private val selfTestLock = Any()
    val isAlive: Boolean get() = session?.isAlive == true

    val measurementCoreCeiling: Int get() = measurementCeiling

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
        try {
            check(isInstalled) { "core-install: sing-box extended is missing from this APK" }

            requireUsableCore()
            require(port in 1..65535) { "core-config: Invalid local SOCKS port" }

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
            val controller = freePort(excluding = port)
            val secret = UUID.randomUUID().toString()

            val builds = candidates(profile, settings, port, controller, secret, false)
            lastStartPhase = "check-and-start"

            val started = openFirst(
                candidates = builds,
                configFile = File(context.filesDir, "runtime-singbox.json"),
                logFile = logFile,
                tempDir = File(context.cacheDir, "singbox-tmp"),
                socksPort = port,
                controller = controller,
                secret = secret,
                validate = true,
                awaitApi = false,
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
            apiPort = controller
            apiSecret = secret
            lastStartPhase = "ready"

            verifyControllerLater(started.first, controller, secret)
            return true
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            var message = error.message ?: error.javaClass.simpleName

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

    private fun verifyControllerLater(session: SingBoxProcessSession, port: Int, secret: String) {
        Thread({
            try {
                var up = false
                val deadlineNs = System.nanoTime() +
                    TimeUnit.MILLISECONDS.toNanos(SingBoxProcessSession.CONTROLLER_TIMEOUT_MS)
                var delayMs = 50L
                while (System.nanoTime() < deadlineNs) {
                    if (this.session !== session) return@Thread
                    if (SingBoxProcessSession.apiReady(
                            port, secret, SingBoxProcessSession.CONTROLLER_TIMEOUT_MS
                        )
                    ) {
                        up = true
                        break
                    }
                    Thread.sleep(minOf(delayMs, 200L).coerceAtLeast(1L))
                    delayMs = (delayMs * 2L).coerceAtMost(200L)
                }
                if (this.session !== session) return@Thread
                lastStartReadiness = StartReadiness(
                    inboundUp = true,
                    controllerUp = up,
                    controllerAwaited = true,
                    phase = if (up) "ready" else "controller",
                    elapsedMs = 0L
                )
                if (!up) {
                    lastSelfHealNotes = lastSelfHealNotes + listOf(
                        "the core is carrying traffic, but its Clash controller never answered " +
                            "(${SingBoxProcessSession.CONTROLLER_TIMEOUT_MS} ms): Real delay through " +
                            "the core and the Engine page's live counters are unavailable, the route is not"
                    )
                }
            } catch (_: InterruptedException) {
            }
        }, "marble-singbox-ctrl").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        val previous = session
        session = null
        apiPort = 0
        previous?.close()
        lastStopEvidence = previous?.stopEvidence.orEmpty()
        lastStartStrategy = ""
        lastStartPhase = "stopped"
    }

    private fun candidates(profile: ProxyProfile, settings: AppSettings, port: Int, controller: Int,
                           secret: String, forTest: Boolean): List<SingBoxConfigBuilder.Build> {
        val runtimeSettings = if (forTest) measurementSettings(settings) else settings
        val paths = if (forTest) emptyMap() else try { rules.prepare() } catch (error: Exception) {
            throw IllegalStateException("core-assets: bundled sing-box rule sets are missing/corrupt; rebuild the APK", error)
        }
        return SingBoxConfigBuilder.candidateBuilds(profile, runtimeSettings, port, controller, secret,
            "", File(context.filesDir, "singbox-cache.db").absolutePath,
            resolverPool = intelligence?.singBoxResolverPool(settings).orEmpty(),
            ruleSetPaths = paths, forTest = forTest,
            underlayHasIpv6 = intelligence?.currentSnapshot()
                ?.takeIf { it.transport != "unknown" }?.hasIpv6 ?: AddressFamilyPolicy.underlayHasIpv6())
            .map { built ->
                val hardened = SingBoxConfigDoctor.hardenForAndroid(built.json)
                built.copy(json = hardened.json, notes = (built.notes + hardened.notes).distinct())
            }
    }

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

            if (index > 0) runCatching { logFile.writeText("") }
            try {
                val session = SingBoxProcessSession.open(bin, candidate.json, configFile, logFile,
                    tempDir, socksPort, controller, secret, validate = validate, awaitApi = awaitApi,
                    requireController = requireController, settleMs = settleMs)

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

                if (SingBoxAndroidRuntime.isPortBindConflict(reason)) throw error
                if (!isConfigRefusal(reason) || index == candidates.lastIndex) throw error
                refusals += "[${candidate.strategy}] $reason".take(400)
            }
        }
        error("core-config: no sing-box representation of this profile was accepted")
    }

    fun temporary(profile: ProxyProfile, settings: AppSettings, block: (Int) -> Unit): Boolean =
        withTemporary(profile, settings, needsController = false) { _, port -> block(port); true }

    private fun <T> withTemporary(profile: ProxyProfile, settings: AppSettings,
                                  needsController: Boolean,
                                  block: (SingBoxProcessSession, Int) -> T): T {
        SingBoxProcessSession.checkInterrupted()
        check(isInstalled) { "core-install: sing-box extended is missing from this APK" }

        requireUsableCore()

        check(testSlots.tryAcquire(30, TimeUnit.SECONDS)) { "core-busy: measurement capacity is occupied" }
        var directory: File? = null
        try {
            SingBoxProcessSession.checkInterrupted()
            directory = File(context.cacheDir, "singbox-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
            val socks = freePort()
            val controller = freePort(excluding = socks)
            val secret = UUID.randomUUID().toString()
            val started = openFirst(
                candidates = candidates(profile, settings, socks, controller, secret, true),
                configFile = File(directory, "config.json"),
                logFile = File(directory, "core.log"),
                tempDir = directory,
                socksPort = socks,
                controller = controller,
                secret = secret,

                validate = false,
                awaitApi = needsController
            )
            started.first.use { child ->
                return block(child, socks)
            }
        } finally {
            directory?.let { workspace ->

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

    fun selfTest(force: Boolean = false): SingBoxCoreSelfTest.Verdict? {
        if (!isInstalled) return null
        val cache = File(context.filesDir, "singbox-selftest.json")
        if (!force) SingBoxCoreSelfTest.read(cache, bin)?.let { cached ->
            lastSelfTest = cached
            return cached
        }
        return synchronized(selfTestLock) {
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

    private fun requireUsableCore() {
        val verdict = selfTest() ?: return
        if (verdict.coreFault) error(verdict.asFailureReason())
    }

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
        const val MAX_TEMPORARY_CORES = 4

        const val LIVE_SETTLE_MS: Long = SingBoxCoreSelfTest.SETTLE_MS

        private val testSlots = Semaphore(MAX_TEMPORARY_CORES, true)
        private val diagnosticLock = Any()
        private val slotLock = Any()

        @Volatile private var measurementCeiling = MAX_TEMPORARY_CORES

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

        internal fun isConfigRefusal(reason: String): Boolean {
            if (SingBoxAndroidRuntime.isUnusableCore(reason)) return false
            if (reason.startsWith("core-crash:")) return false

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
