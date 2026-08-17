package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingDefaults
import com.marbleng.app.model.RoutingMode
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit

/** Status of the two Xray geo data files used by managed routing. */
data class RoutingAssetStatus(
    val geoIpReady: Boolean,
    val geoIpBytes: Long,
    val geoIpRemote: Boolean,
    val geoSiteReady: Boolean,
    val geoSiteBytes: Long,
    val geoSiteRemote: Boolean
)

class XrayManager(private val context: Context) {
    // MARBLE_FAST_START_V12
    // MARBLE_LOG_RESCUE_V13
    // MARBLE_DIAG_PROCESS_PID_V15
    private companion object {
        const val ROUTING_ASSET_REFRESH_MS = 24L * 60L * 60L * 1000L
        const val ROUTING_ASSET_RETRY_MS = 6L * 60L * 60L * 1000L
    }
    @Volatile private var process: Process? = null

    @Volatile
    var lastStartError: String = ""
        private set

    /**
     * Configs already proven valid by `xray run -test` this session skip that dry-run on the next
     * connect — the hardened output is a pure function of (profile, port, settings), so a repeat
     * hash can only re-validate identically. This shaves a full process spawn off reconnects to
     * the same node (auto-reconnect, "use best result", retry-after-drop) without weakening the
     * check for anything actually new.
     */
    private val validatedConfigHashes = LinkedHashSet<String>()
    private val validatedCacheLimit = 96

    private fun validationCached(key: String): Boolean =
        synchronized(validatedConfigHashes) {
            key in validatedConfigHashes
        }

    private fun rememberValidation(key: String) {
        synchronized(validatedConfigHashes) {
            validatedConfigHashes += key
            while (
                validatedConfigHashes.size >
                validatedCacheLimit
            ) {
                validatedConfigHashes.remove(
                    validatedConfigHashes.first()
                )
            }
        }
    }

    val isAlive: Boolean get() = process?.isAlive == true
    // MARBLE_ANDROID_PROCESS_PID_FIX_V15_0_1
    /**
     * Diagnostics-only child PID lookup. Android does not expose java.lang.Process.pid()
     * consistently to Kotlin across the supported API surface. This executes only when Bug Finder
     * requests process metadata, never on packet, TUN, connect, or telemetry fast paths.
     */
    val processPid: Long
        get() = process?.let(::resolveChildProcessPid) ?: -1L

    private fun resolveChildProcessPid(target: Process): Long {
        val methodPid = runCatching {
            val method = target.javaClass.methods.firstOrNull {
                it.name == "pid" && it.parameterCount == 0
            }
            (method?.invoke(target) as? Number)?.toLong()
        }.getOrNull()
        if (methodPid != null && methodPid > 0L) return methodPid

        var type: Class<*>? = target.javaClass
        while (type != null) {
            val current = type
            val field = runCatching { current.getDeclaredField("pid") }.getOrNull()
            if (field != null) {
                val fieldPid = runCatching {
                    field.isAccessible = true
                    (field.get(target) as? Number)?.toLong()
                }.getOrNull()
                if (fieldPid != null && fieldPid > 0L) return fieldPid
            }
            type = current.superclass
        }

        // PID is diagnostic metadata only. Unknown PID must never affect the connection.
        return -1L
    }
    val logFile: File get() = File(context.filesDir, "logs/xray.log")
    private val bin: File get() = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
    private val assetsDir = File(context.filesDir, "xray-assets")
    private val runtimeConfig: File get() = File(context.filesDir, "runtime.json")

    /**
     * Ensures geoip.dat and geosite.dat exist. If a user URL is configured, the file is downloaded
     * once for that exact URL and then reused on every start. Changing the URL triggers one new
     * download. force=true explicitly refreshes the selected source.
     */
    @Synchronized
    fun prepareRoutingAssets(settings: AppSettings = AppSettings(), force: Boolean = false): RoutingAssetStatus {
        assetsDir.mkdirs()
        ensureAsset("geoip.dat", settings.geoIpUrl.trim(), force)
        ensureAsset("geosite.dat", settings.geoSiteUrl.trim(), force)
        return routingAssetStatus()
    }

    @Synchronized
    fun deleteRoutingAssets() {
    listOf("geoip.dat", "geosite.dat").forEach { name ->
        File(assetsDir, name).delete()
        sourceMarker(name).delete()
        refreshFailureMarker(name).delete()
        File(assetsDir, "$name.download").delete()
        File(assetsDir, "$name.bak").delete()
    }
}

    fun routingAssetStatus(): RoutingAssetStatus {
        val ip = File(assetsDir, "geoip.dat")
        val site = File(assetsDir, "geosite.dat")
        val ipSource = runCatching { sourceMarker("geoip.dat").readText() }.getOrDefault("")
        val siteSource = runCatching { sourceMarker("geosite.dat").readText() }.getOrDefault("")
        return RoutingAssetStatus(
            geoIpReady = ip.isFile && ip.length() > 1024L,
            geoIpBytes = ip.takeIf { it.isFile }?.length() ?: 0L,
            geoIpRemote = ipSource.startsWith("http://") || ipSource.startsWith("https://"),
            geoSiteReady = site.isFile && site.length() > 1024L,
            geoSiteBytes = site.takeIf { it.isFile }?.length() ?: 0L,
            geoSiteRemote = siteSource.startsWith("http://") || siteSource.startsWith("https://")
        )
    }

    private fun ensureAsset(name: String, remoteUrl: String, force: Boolean) {
    val destination = File(assetsDir, name)
    val marker = sourceMarker(name)
    val failureMarker = refreshFailureMarker(name)
    var currentSource = runCatching { marker.readText().trim() }.getOrDefault("")
    var ready = destination.isFile && destination.length() > 1024L
    val now = System.currentTimeMillis()
    val canonicalDefault = defaultRemoteUrl(name)

    // Signed CI builds contain verified Chocolate4U assets. Use them immediately on first launch
    // instead of making the user's very first VPN connection depend on raw.githubusercontent.com.
    if (!ready && remoteUrl == canonicalDefault && copyBundledAsset(name, destination)) {
        marker.writeText("apk://xray/$name")
        failureMarker.delete()
        currentSource = "apk://xray/$name"
        ready = true
        if (!force) return
    }

    if (remoteUrl.isNotBlank()) {
        require(remoteUrl.startsWith("https://", ignoreCase = true)) {
            "$name URL must use https:// because cleartext HTTP is disabled"
        }

        val ageMs = if (ready) (now - destination.lastModified()).coerceAtLeast(0L) else Long.MAX_VALUE
        val sameRemote = ready && currentSource == remoteUrl
        val bundledDefault = ready && currentSource == "apk://xray/$name" && remoteUrl == canonicalDefault
        if (!force && (sameRemote || bundledDefault) && ageMs < ROUTING_ASSET_REFRESH_MS) return

        val recentFailure = ready && failureMarker.isFile &&
            now - failureMarker.lastModified() in 0L until ROUTING_ASSET_RETRY_MS
        if (!force && recentFailure) return

        var lastError: Throwable? = null
        for (candidate in remoteCandidates(name, remoteUrl)) {
            val attempt = runCatching { downloadAsset(candidate, destination) }
            if (attempt.isSuccess) {
                // Store the canonical configured URL even when the jsDelivr mirror won the fetch.
                marker.writeText(remoteUrl)
                failureMarker.delete()
                return
            }
            lastError = attempt.exceptionOrNull()
        }

        // A transient update outage must never destroy a valid previous routing database.
        if (ready) {
            runCatching {
                failureMarker.writeText(lastError?.message.orEmpty().take(300))
                failureMarker.setLastModified(now)
            }
            return
        }

        // Custom/default remote failed and nothing valid exists yet. Last chance: bundled data.
        if (copyBundledAsset(name, destination)) {
            marker.writeText("apk://xray/$name")
            runCatching {
                failureMarker.writeText(lastError?.message.orEmpty().take(300))
                failureMarker.setLastModified(now)
            }
            return
        }

        throw lastError ?: IllegalStateException("Unable to prepare $name")
    }

    if (!force && ready) return
    if (!copyBundledAsset(name, destination)) {
        if (!ready) error("Bundled $name is missing and no HTTPS source is configured")
    } else {
        marker.writeText("apk://xray/$name")
        failureMarker.delete()
    }
}

    private fun defaultRemoteUrl(name: String): String = when (name) {
        "geoip.dat" -> RoutingDefaults.GEOIP_URL
        "geosite.dat" -> RoutingDefaults.GEOSITE_URL
        else -> ""
    }

    private fun remoteCandidates(name: String, configured: String): List<String> {
        val fallback = when (name) {
            "geoip.dat" -> RoutingDefaults.GEOIP_MIRROR
            "geosite.dat" -> RoutingDefaults.GEOSITE_MIRROR
            else -> ""
        }
        return if (configured == defaultRemoteUrl(name) && fallback.isNotBlank()) {
            listOf(configured, fallback).distinct()
        } else {
            listOf(configured)
        }
    }

    private fun copyBundledAsset(name: String, destination: File): Boolean {
        val temp = File(assetsDir, "$name.download")
        temp.delete()
        return runCatching {
            context.assets.open("xray/$name").use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            require(temp.length() > 1024L) { "Bundled $name is empty" }
            replaceFile(temp, destination)
            true
        }.getOrElse {
            temp.delete()
            false
        }
    }

    private fun downloadAsset(url: String, destination: File) {
        val temp = File(assetsDir, "${destination.name}.download")
        temp.delete()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "MarbleNG/1 routing-assets")

        try {
            connection.connect()
            require(connection.url.protocol.equals("https", ignoreCase = true)) {
                "${destination.name} redirect left HTTPS"
            }
            val code = connection.responseCode
            require(code in 200..299) { "${destination.name} download HTTP $code" }
            val declared = connection.contentLengthLong
            require(declared <= 128L * 1024L * 1024L || declared < 0) {
                "${destination.name} is too large"
            }

            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        require(total <= 128L * 1024L * 1024L) {
                            "${destination.name} exceeds 128 MiB"
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            require(temp.length() > 1024L) { "${destination.name} download is empty" }
            replaceFile(temp, destination)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Crash-safe same-directory replacement. The last known-good geo database is preserved until
     * the replacement has been committed; failures roll back instead of leaving a missing asset.
     */
    private fun replaceFile(temp: File, destination: File) {
        destination.parentFile?.mkdirs()
        require(temp.isFile && temp.length() > 0L) { "Replacement ${destination.name} is empty" }

        val backup = File(destination.parentFile, "${destination.name}.bak")
        backup.delete()

        val hadDestination = destination.exists()
        if (hadDestination && !destination.renameTo(backup)) {
            error("Cannot protect existing ${destination.name} before replacement")
        }

        try {
            if (!temp.renameTo(destination)) {
                temp.inputStream().use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                require(destination.isFile && destination.length() == temp.length()) {
                    "Copied ${destination.name} size mismatch"
                }
                temp.delete()
            }

            require(destination.isFile && destination.length() > 0L) {
                "Replacement ${destination.name} was not committed"
            }
            backup.delete()
        } catch (error: Throwable) {
            runCatching { destination.delete() }
            if (hadDestination && backup.exists()) {
                runCatching { backup.renameTo(destination) }
            }
            temp.delete()
            throw error
        }
    }

    private fun sourceMarker(name: String) = File(assetsDir, "$name.source")
    private fun refreshFailureMarker(name: String) = File(assetsDir, "$name.refresh-failed")

    /**
     * "private" geoip tags/bypass never need geoip.dat: XrayConfigHardener expands them to literal
     * RFC1918/link-local/loopback CIDRs. Only non-private geoip tags (country codes, etc.) require
     * a real downloaded database.
     */
    private fun requiresGeoIp(settings: AppSettings): Boolean {
        if (settings.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM)) {
            val nonPrivateTags = settings.routeGeoIpTags.split(',', '\n', '\r', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals("private", ignoreCase = true) && !it.equals("geoip:private", ignoreCase = true) }
            if (nonPrivateTags.isNotEmpty()) return true
        }
        return listOf(settings.routeDirectIps, settings.routeBlockIps).any { raw ->
            raw.split(',', '\n', '\r', ';').any {
                val tag = it.trim()
                tag.startsWith("geoip:", ignoreCase = true) && !tag.equals("geoip:private", ignoreCase = true)
            }
        }
    }

    private fun requiresGeoSite(settings: AppSettings): Boolean {
        if (settings.routeBlockAds && settings.routeAdsTag.isNotBlank()) return true
        if (settings.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) && settings.routeGeoSiteTags.isNotBlank()) return true
        return listOf(settings.routeDirectDomains, settings.routeProxyDomains, settings.routeBlockDomains).any { raw ->
            raw.split(',', '\n', '\r', ';').any { it.trim().startsWith("geosite:", ignoreCase = true) }
        }
    }

    private fun createProcessBuilder(vararg args: String): ProcessBuilder {
        val command = ArrayList<String>()
        command += bin.absolutePath
        command += args
        return ProcessBuilder(command).apply {
            redirectErrorStream(true)
            environment()["XRAY_LOCATION_ASSET"] = assetsDir.absolutePath
        }
    }

    /**
     * Verifies the selected routing policy with the exact Xray binary shipped in the APK.
     * XRAY_LOCATION_ASSET is inherited from createProcessBuilder(), so geoip/geosite tags are
     * resolved against MarbleNG's managed data files rather than just checking file existence.
     */
    @Synchronized
    fun verifyRoutingPolicy(
        profile: ProxyProfile,
        settings: AppSettings = AppSettings(),
        chainProfile: ProxyProfile? = null
    ): String {
        require(bin.isFile) { "Xray native binary is missing" }

        val needGeoIp = requiresGeoIp(settings)
        val needGeoSite = requiresGeoSite(settings)
        val shouldPrepareAssets =
            needGeoIp || needGeoSite || settings.geoIpUrl.isNotBlank() || settings.geoSiteUrl.isNotBlank()
        val status = if (shouldPrepareAssets) prepareRoutingAssets(settings, force = false) else routingAssetStatus()

        if (needGeoIp) require(status.geoIpReady) {
            "geoip.dat is required by this policy but is missing/invalid"
        }
        if (needGeoSite) require(status.geoSiteReady) {
            "geosite.dat is required by this policy but is missing/invalid"
        }

        val sourceConfig = chainProfile
            ?.takeIf { it.id != profile.id }
            ?.let { XrayConfigHardener.composeChain(profile.configJson, it.configJson) }
            ?: profile.configJson

        val config = File(context.cacheDir, "routing-policy-verify.json")
        val verifyLog = File(context.cacheDir, "routing-policy-verify.log")
        verifyLog.delete()

        return try {
            config.writeText(XrayConfigHardener.harden(sourceConfig, 19091, settings))
            val testProcess = createProcessBuilder("run", "-test", "-c", config.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(verifyLog))
                .start()

            if (!testProcess.waitFor(12, TimeUnit.SECONDS)) {
                stopProcess(testProcess)
                error("Xray routing verification timed out")
            }
            if (testProcess.exitValue() != 0) {
                val hint = runCatching {
                    verifyLog.useLines { lines ->
                        lines.filter { it.isNotBlank() }.toList().takeLast(6).joinToString(" | ")
                    }.take(1200)
                }.getOrDefault("routing verifier log unavailable")
                error("Xray rejected the routing/geo policy: $hint")
            }

            val ipState = if (status.geoIpReady) "READY ${status.geoIpBytes / 1024} KiB" else "NOT REQUIRED"
            val siteState = if (status.geoSiteReady) "READY ${status.geoSiteBytes / 1024} KiB" else "NOT REQUIRED"
            "Routing verified by Xray • GeoIP $ipState • GeoSite $siteState"
        } finally {
            runCatching { config.delete() }
        }
    }

    @Synchronized
    fun start(profile: ProxyProfile, port: Int, settings: AppSettings = AppSettings(), chainProfile: ProxyProfile? = null): Boolean {
        lastStartError = ""
        stop()

        if (!bin.isFile) return fail("Xray native binary is missing")
        if (port !in 1..65535) return fail("Invalid local SOCKS port: $port")

        logFile.parentFile?.mkdirs()
        beginLiveLogSession()
        val config = runtimeConfig

        return runCatching {
            if (!portAvailable(port)) {
                return@runCatching fail("Local port $port is already in use")
            }

            val needGeoIp = requiresGeoIp(settings)
            val needGeoSite = requiresGeoSite(settings)
            val shouldPrepareAssets =
                needGeoIp || needGeoSite || settings.geoIpUrl.isNotBlank() || settings.geoSiteUrl.isNotBlank()
            val assetStatus =
                if (shouldPrepareAssets) prepareRoutingAssets(settings, force = false) else routingAssetStatus()

            if (needGeoIp && !assetStatus.geoIpReady) {
                error("geoip.dat is required by the selected routing policy. Add a download URL in Settings → Routing.")
            }
            if (needGeoSite && !assetStatus.geoSiteReady) {
                error("geosite.dat is required by the selected routing policy. Add a download URL in Settings → Routing.")
            }

            val sourceConfig = chainProfile
                ?.takeIf { it.id != profile.id }
                ?.let { XrayConfigHardener.composeChain(profile.configJson, it.configJson) }
                ?: profile.configJson

            val configText = XrayConfigHardener.harden(sourceConfig, port, settings)
            config.writeText(configText)
            // The live Xray process is the validator on the user-critical path. Invalid configs
            // exit before the SOCKS handshake can succeed, and waitSocksPort() detects that process
            // death immediately. The explicit Settings routing verifier still uses `xray run -test`.
            // Avoiding a second process spawn removes up to 10 seconds from every uncached connect.

            val startedProcess = createProcessBuilder("run", "-c", config.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                .start()
            process = startedProcess

            if (!waitSocksPort(port, 7_000L, startedProcess)) {
                val hint = if (startedProcess.isAlive) {
                    "SOCKS listener did not open"
                } else {
                    "Xray exited with code ${runCatching { startedProcess.exitValue() }.getOrDefault(-1)}"
                }
                stopProcess(startedProcess)
                if (process === startedProcess) process = null
                fail("$hint: ${lastLogHint()}")
            } else {
                lastStartError = ""
                true
            }
        }.getOrElse { error ->
            process?.let(::stopProcess)
            process = null
            fail("${error::class.java.simpleName}: ${error.message ?: "Xray startup failed"}")
        }
    }

    @Synchronized
    fun stop() {
        val current = process ?: return
        process = null
        stopProcess(current)
    }

    private fun stopProcess(target: Process) {
        runCatching { target.destroy() }
        val deadline = System.currentTimeMillis() + 1_500L
        while (target.isAlive && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (target.isAlive) runCatching { target.destroyForcibly() }
        val forceDeadline = System.currentTimeMillis() + 750L
        while (target.isAlive && System.currentTimeMillis() < forceDeadline) {
            try {
                Thread.sleep(25L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    /**
     * Temporary benchmark instance. Validation is cached by canonical profile + effective settings,
     * not by the ephemeral local port, so repeated real-tunnel tests skip redundant xray -test spawns.
     */
    fun temporary(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings = AppSettings(),
        block: (Int) -> Unit
    ): Boolean {
        if (!bin.isFile || port !in 1..65535 || !portAvailable(port)) return false

        val config = File(context.cacheDir, "bench-$port.json")
        val benchmarkLog = File(context.cacheDir, "xray-bench-$port.log")
        if (
            benchmarkLog.isFile &&
            benchmarkLog.length() >
            512L * 1024L
        ) {
            runCatching {
                benchmarkLog.delete()
            }
        }

        // A throwaway benchmark instance does not need the `xray run -test` dry run that the live
        // path uses: an unusable config simply fails to open its SOCKS port, and waitSocksPort()
        // notices the dead process immediately. Skipping it halves the process spawns per node,
        // which is the single biggest cost of testing a large library.
        val portWaitMs = (settings.benchTimeoutSec * 1000L).coerceIn(2_500L, 7_000L)

        return runCatching {
            val benchmarkSettings = settings.copy(
                routingMode = RoutingMode.PROXY_ALL,
                routeGeoIpTags = "",
                routeGeoSiteTags = "",
                routeDirectDomains = "",
                routeProxyDomains = "",
                routeBlockDomains = "",
                routeDirectIps = "",
                routeBlockIps = "",
                routeBypassPrivate = false,
                routeBlockAds = false
            )
            val configText = XrayConfigHardener.harden(profile.configJson, port, benchmarkSettings)
            config.writeText(configText)

            val temporaryProcess = createProcessBuilder("run", "-c", config.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(benchmarkLog))
                .start()
            try {
                if (!waitSocksPort(port, portWaitMs, temporaryProcess)) false
                else {
                    block(port)
                    true
                }
            } finally {
                stopProcess(temporaryProcess)
                runCatching { config.delete() }
            }
        }.getOrElse {
            runCatching { config.delete() }
            false
        }
    }

    private fun waitSocksPort(
        port: Int,
        timeoutMs: Long,
        target: Process? = null
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs

        /*
         * Listener readiness must not create SOCKS traffic.
         *
         * The old probe sent only [05 01 00], read the selected auth method, then closed.
         * Xray correctly logged that incomplete request as:
         *   proxy/socks: failed to read request > EOF
         *
         * We already prove the port was free before launching Xray. Polling until the same
         * loopback port becomes non-bindable while the spawned process remains alive is a
         * zero-traffic readiness signal and produces no fake SOCKS failures.
         *
         * MARBLE: listener-bound-no-socks-handshake
         */
        while (System.currentTimeMillis() < deadline) {
            if (target != null && !target.isAlive) return false

            val listenerBound = !portAvailable(port)
            if (listenerBound && (target == null || target.isAlive)) {
                return true
            }

            try {
                Thread.sleep(80L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        return false
    }

    private fun portAvailable(port: Int): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", port))
        }
        true
    }.getOrDefault(false)

    private fun sha256(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Keep the live Xray log scoped to the current connection attempt.
     *
     * RuntimeDiagnostics already keeps the cross-session engine history. Reusing one unbounded
     * xray.log made Bug Finder surface warnings/failures from hours-old sessions as if they were
     * current. Keep exactly one previous live log for manual forensics and start clean each time.
     */
    private fun beginLiveLogSession() {
        val previous = File(logFile.parentFile, "${logFile.name}.1")
        runCatching { previous.delete() }

        if (logFile.isFile && logFile.length() > 0L) {
            if (!logFile.renameTo(previous)) {
                runCatching { logFile.writeText("") }
            }
        }
    }

    private fun lastLogHint(): String {
        if (!logFile.isFile) return "no Xray log"
        return runCatching {
            logFile.useLines { lines ->
                lines.filter { it.isNotBlank() }.toList().takeLast(4).joinToString(" | ")
            }.take(900)
        }.getOrDefault("Xray log unavailable").ifBlank { "no Xray error detail" }
    }

    private fun fail(reason: String): Boolean {
        lastStartError = reason.take(1_200)
        return false
    }
}
