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
 * process runner. Temporary tests are isolated and limited to two children even when the user
 * requests hundreds of TCP workers. A measurement cannot overwrite or stop the live session. */
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
        lastStartPhase = "config"
        var dns: AndroidDnsBridge.Lease? = null
        try {
            check(isInstalled) { "core-install: sing-box extended is missing from this APK" }
            require(port in 1..65535) { "core-config: Invalid local SOCKS port" }
            dns = bootstrap.acquire()
            val controller = freePort(excluding = port)
            val secret = UUID.randomUUID().toString()
            val config = build(profile, settings, port, controller, secret, dns.port, false)
            lastSelfHealNotes = config.notes
            lastStartPhase = "check-and-start"
            val process = SingBoxProcessSession.open(bin, config.json,
                File(context.filesDir, "runtime-singbox.json"), logFile,
                File(context.cacheDir, "singbox-tmp"), port, controller, secret)
            session = process
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
        lastStartPhase = "stopped"
    }

    private fun build(profile: ProxyProfile, settings: AppSettings, port: Int, controller: Int,
                      secret: String, dnsPort: Int, forTest: Boolean): SingBoxConfigBuilder.Build {
        val runtimeSettings = if (forTest) measurementSettings(settings) else settings
        val paths = if (forTest) emptyMap() else try { rules.prepare() } catch (error: Exception) {
            throw IllegalStateException("core-assets: bundled sing-box rule sets are missing/corrupt; rebuild the APK", error)
        }
        val built = SingBoxConfigBuilder.build(profile, runtimeSettings, port, controller, secret,
            "", File(context.filesDir, "singbox-cache.db").absolutePath,
            resolverPool = intelligence?.singBoxResolverPool(settings).orEmpty(),
            ruleSetPaths = paths, bootstrapDnsPort = dnsPort, forTest = forTest)
        val hardened = SingBoxConfigDoctor.hardenForAndroid(built.json)
        return built.copy(json = hardened.json, notes = (built.notes + hardened.notes).distinct())
    }

    /** Shared isolated transport for Real Delay, rank, tuner and bootstrap fetches. */
    fun temporary(profile: ProxyProfile, settings: AppSettings, block: (Int) -> Unit): Boolean =
        withTemporary(profile, settings) { _, port -> block(port); true }

    private fun <T> withTemporary(profile: ProxyProfile, settings: AppSettings,
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
            val built = build(profile, settings, socks, controller, secret, dns.port, true)
            SingBoxProcessSession.open(bin, built.json, File(directory, "config.json"),
                File(directory, "core.log"), directory, socks, controller, secret).use { child ->
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
        withTemporary(profile, settings) { child, _ -> testTargets(urls, timeoutMs) { url, budget -> child.delay(url, budget) } }
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
        private val testSlots = Semaphore(2, true)
        private val diagnosticLock = Any()
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
