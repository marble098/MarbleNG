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
import java.util.concurrent.locks.ReentrantLock

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
    // MARBLE_RUNTIME_STARTUP_RESCUE_V21
    // MARBLE_SSH_BRIDGE_V25
    // MARBLE_TEMP_PORT_ALLOCATOR_V38
    // MARBLE_WARM_TUNNEL_RANK_V42
    // MARBLE_REPEATABLE_RANK_V44
    // MARBLE_V2RAYNG_SMART_RANK_V45
    // MARBLE_TEMP_CONFIG_FALLBACK_V47
    // MARBLE_REALTIME_ENGINE_V70
    private companion object {
        const val ROUTING_ASSET_REFRESH_MS = 24L * 60L * 60L * 1000L
        const val ROUTING_ASSET_RETRY_MS = 6L * 60L * 60L * 1000L
    }
    private val lifecycleLock = Any()
    private val assetLock = ReentrantLock()

    /** Process-wide ownership of every throwaway local Xray listener. */
    private val temporaryPortLock = Any()
    private val reservedTemporaryPorts = mutableSetOf<Int>()
    private var lifecycleGeneration = 0L
    @Volatile private var process: Process? = null
    @Volatile private var liveSsh: SshTransportManager? = null

    private fun detachLiveSsh(generation: Long? = null): SshTransportManager? =
        synchronized(lifecycleLock) {
            if (generation != null && lifecycleGeneration != generation) {
                null
            } else {
                val current = liveSsh
                liveSsh = null
                current
            }
        }

    private fun startSshBridge(
        generation: Long,
        profile: ProxyProfile,
        settings: AppSettings
    ): Int {
        val candidate = SshTransportManager()
        val port = try {
            candidate.start(profile, settings)
        } catch (error: Throwable) {
            candidate.stop()
            throw error
        }

        var previous: SshTransportManager? = null
        val accepted = synchronized(lifecycleLock) {
            if (lifecycleGeneration != generation) {
                false
            } else {
                previous = liveSsh
                liveSsh = candidate
                true
            }
        }
        if (!accepted) {
            candidate.stop()
            error("SSH start was superseded by a newer connection attempt")
        }
        previous?.stop()
        return port
    }

    @Volatile
    var lastStartError: String = ""
        private set

    @Volatile
    var lastStartPhase: String = "idle"
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

    private data class StartTicket(
        val generation: Long,
        val previous: Process?
    )

    private fun beginStartTicket(): StartTicket = synchronized(lifecycleLock) {
        lifecycleGeneration += 1L
        val previous = process
        process = null
        StartTicket(lifecycleGeneration, previous)
    }

    private fun startStillCurrent(generation: Long): Boolean =
        synchronized(lifecycleLock) { lifecycleGeneration == generation }

    private fun publishProcess(generation: Long, candidate: Process): Boolean =
        synchronized(lifecycleLock) {
            if (lifecycleGeneration != generation) {
                false
            } else {
                process = candidate
                true
            }
        }

    private fun detachOwnedProcess(generation: Long, candidate: Process? = null): Process? =
        synchronized(lifecycleLock) {
            if (lifecycleGeneration != generation) return@synchronized null
            val current = process
            if (candidate != null && current !== candidate) return@synchronized null
            process = null
            current
        }

    /** Stale/cancelled starts may never overwrite diagnostics for a newer generation. */
    private fun publishStartState(
        generation: Long,
        phase: String,
        error: String? = null
    ): Boolean = synchronized(lifecycleLock) {
        if (lifecycleGeneration != generation) {
            false
        } else {
            lastStartPhase = phase
            if (error != null) lastStartError = error.take(1_200)
            true
        }
    }

    private fun failStart(generation: Long, reason: String): Boolean {
        detachLiveSsh(generation)?.stop()
        publishStartState(generation, "failed", reason)
        return false
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
    /** Live-only TCP_INFO JSONL. MARBLE_REALTIME_ENGINE_V70 */
    val transportTelemetryFile: File get() = File(context.filesDir, "logs/xray-transport.jsonl")
    private val bin: File get() = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
    private val assetsDir = File(context.filesDir, "xray-assets")
    private val runtimeConfig: File get() = File(context.filesDir, "runtime.json")

    /**
     * Ensures geoip.dat and geosite.dat exist. If a user URL is configured, the file is downloaded
     * once for that exact URL and then reused on every start. Changing the URL triggers one new
     * download. force=true explicitly refreshes the selected source.
     */
    fun prepareRoutingAssets(
        settings: AppSettings = AppSettings(),
        force: Boolean = false
    ): RoutingAssetStatus {
        assetLock.lock()
        try {
            assetsDir.mkdirs()
            ensureAsset("geoip.dat", settings.geoIpUrl.trim(), force)
            ensureAsset("geosite.dat", settings.geoSiteUrl.trim(), force)
            return routingAssetStatus()
        } finally {
            assetLock.unlock()
        }
    }

    fun deleteRoutingAssets() {
        assetLock.lock()
        try {
            listOf("geoip.dat", "geosite.dat").forEach { name ->
                File(assetsDir, name).delete()
                sourceMarker(name).delete()
                refreshFailureMarker(name).delete()
                File(assetsDir, "$name.download").delete()
                File(assetsDir, "$name.bak").delete()
            }
        } finally {
            assetLock.unlock()
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

    /**
     * Connection-critical asset preparation is OFFLINE-ONLY.
     * Remote routing-data refresh belongs to the explicit management task, never Xray start.
     * MARBLE_CONNECT_ASSET_OFFLINE_V21
     */
    private fun prepareRoutingAssetsForConnect(settings: AppSettings): RoutingAssetStatus {
        val needGeoIp = requiresGeoIp(settings)
        val needGeoSite = requiresGeoSite(settings)
        if (!needGeoIp && !needGeoSite) return routingAssetStatus()

        val acquired = try {
            assetLock.tryLock(250L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        check(acquired) {
            "Routing assets are being updated; retry after preparation finishes"
        }

        try {
            assetsDir.mkdirs()
            var status = routingAssetStatus()

            if (needGeoIp && !status.geoIpReady) {
                val destination = File(assetsDir, "geoip.dat")
                if (copyBundledAsset("geoip.dat", destination)) {
                    sourceMarker("geoip.dat").writeText("apk://xray/geoip.dat")
                    refreshFailureMarker("geoip.dat").delete()
                }
            }

            status = routingAssetStatus()
            if (needGeoSite && !status.geoSiteReady) {
                val destination = File(assetsDir, "geosite.dat")
                if (copyBundledAsset("geosite.dat", destination)) {
                    sourceMarker("geosite.dat").writeText("apk://xray/geosite.dat")
                    refreshFailureMarker("geosite.dat").delete()
                }
            }

            return routingAssetStatus()
        } finally {
            assetLock.unlock()
        }
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
        settings: AppSettings = AppSettings()
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

        val sourceConfig = if (profile.scheme.equals("ssh", true)) {
            SshProfileCodec.xrayClientConfig(19090)
        } else {
            profile.configJson
        }

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

    fun start(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings = AppSettings()
    ): Boolean {
        val ticket = beginStartTicket()
        publishStartState(ticket.generation, "begin", "")
        ticket.previous?.let(::stopProcess)
        detachLiveSsh(ticket.generation)?.stop()

        if (!startStillCurrent(ticket.generation)) return false
        if (!bin.isFile) return failStart(ticket.generation, "Xray native binary is missing")
        if (port !in 1..65535) return failStart(ticket.generation, "Invalid local SOCKS port: $port")

        logFile.parentFile?.mkdirs()
        beginLiveLogSession()
        val config = runtimeConfig

        return runCatching {
            publishStartState(ticket.generation, "port-check")
            if (!portAvailable(port)) {
                return@runCatching failStart(ticket.generation, "Local port $port is already in use")
            }
            if (!startStillCurrent(ticket.generation)) return@runCatching false

            // Connection-critical path: local filesystem + process spawn only. Never remote HTTP.
            publishStartState(ticket.generation, "assets-local")
            val needGeoIp = requiresGeoIp(settings)
            val needGeoSite = requiresGeoSite(settings)
            val assetStatus = prepareRoutingAssetsForConnect(settings)

            if (needGeoIp && !assetStatus.geoIpReady) {
                error("geoip.dat is required by the selected routing policy but no local/bundled copy is available")
            }
            if (needGeoSite && !assetStatus.geoSiteReady) {
                error("geosite.dat is required by the selected routing policy but no local/bundled copy is available")
            }
            if (!startStillCurrent(ticket.generation)) return@runCatching false

            publishStartState(ticket.generation, "config")
            val sourceConfig = if (profile.scheme.equals("ssh", true)) {
                SshProfileCodec.xrayClientConfig(startSshBridge(ticket.generation, profile, settings))
            } else {
                profile.configJson
            }
            config.writeText(XrayConfigHardener.harden(sourceConfig, port, settings))

            if (!startStillCurrent(ticket.generation)) return@runCatching false

            publishStartState(ticket.generation, "spawn")
            transportTelemetryFile.parentFile?.mkdirs(); runCatching { transportTelemetryFile.delete() }
            val liveBuilder = createProcessBuilder("run", "-c", config.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            // Throwaway Rank/Turbo processes intentionally do not receive this environment variable.
            liveBuilder.environment()["MARBLE_TELEMETRY_FILE"] = transportTelemetryFile.absolutePath
            val startedProcess = liveBuilder.start()

            if (!publishProcess(ticket.generation, startedProcess)) {
                stopProcess(startedProcess)
                return@runCatching false
            }

            publishStartState(ticket.generation, "listener")
            if (!waitSocksPort(port, 7_000L, startedProcess)) {
                val cancelled = !startStillCurrent(ticket.generation)
                // Capture the child's state before stopping it; otherwise diagnostics report our
                // own termination signal instead of the process state that caused readiness to fail.
                val aliveBeforeStop = startedProcess.isAlive
                val exitBeforeStop = if (aliveBeforeStop) null else
                    runCatching { startedProcess.exitValue() }.getOrNull()
                val logHint = lastLogHint()

                val owned = detachOwnedProcess(ticket.generation, startedProcess)
                if (owned != null) {
                    stopProcess(owned)
                } else if (startedProcess.isAlive) {
                    stopProcess(startedProcess)
                }

                if (cancelled) {
                    false
                } else {
                    val hint = if (aliveBeforeStop) {
                        "SOCKS listener did not open"
                    } else {
                        "Xray exited with code ${exitBeforeStop ?: -1}"
                    }
                    failStart(ticket.generation, "$hint: $logHint")
                }
            } else if (!startStillCurrent(ticket.generation)) {
                detachOwnedProcess(ticket.generation, startedProcess)?.let(::stopProcess)
                false
            } else {
                publishStartState(ticket.generation, "ready", "")
            }
        }.getOrElse { error ->
            detachOwnedProcess(ticket.generation)?.let(::stopProcess)
            failStart(ticket.generation, "${error::class.java.simpleName}: ${error.message ?: "Xray startup failed"}")
        }
    }

    /**
     * STOP invalidates the in-flight start before waiting for a child process to exit.
     * No monitor is held while destroy()/destroyForcibly() waits.
     */
    fun stop() {
        val stopped = synchronized(lifecycleLock) {
            lifecycleGeneration += 1L
            val existing = process
            process = null
            val ssh = liveSsh
            liveSsh = null
            lastStartPhase = "stopped"
            existing to ssh
        }
        stopped.first?.let(::stopProcess)
        stopped.second?.stop()
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
     * Deterministic shutdown for throwaway benchmark processes.
     *
     * A reservation is released only after this bounded graceful/forced reap attempt. The old
     * 300 + 250 ms window could return while a native worker was still exiting, so rapid Rank
     * reruns accumulated processes, sockets and server sessions behind the next batch.
     */
    private fun stopTemporaryProcess(target: Process) {
        runCatching { target.destroy() }
        val gracefulDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(700L)
        while (target.isAlive && System.nanoTime() < gracefulDeadline) {
            try {
                Thread.sleep(20L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (target.isAlive) runCatching { target.destroyForcibly() }
        val forcedDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1_300L)
        while (target.isAlive && System.nanoTime() < forcedDeadline) {
            try {
                Thread.sleep(20L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        runCatching { target.inputStream.close() }
        runCatching { target.errorStream.close() }
        runCatching { target.outputStream.close() }
    }

    /**
     * Temporary benchmark/optimizer Xray instance.
     *
     * `port` is only a preferred starting point. Benchmark, race, Continuous Optimizer and Turbo
     * may overlap, so XrayManager owns one reservation set and passes the real collision-free port
     * back to the caller.
     */
    fun temporary(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings = AppSettings(),
        delayTest: Boolean = false,
        block: (Int) -> Unit
    ): Boolean {
        if (!bin.isFile || port !in 1..65535) return false
        val actualPort = reserveTemporaryPort(port) ?: return false

        val config = File(context.cacheDir, "bench-$actualPort.json")
        val benchmarkLog = File(context.cacheDir, "xray-bench-$actualPort.log")
        // One log per latest worker lifetime: stale failures from earlier Rank runs must not
        // masquerade as the current node's failure evidence.
        runCatching { benchmarkLog.delete() }

        val portWaitMs = (settings.benchTimeoutSec * 1000L).coerceIn(2_500L, 7_000L)
        val sshBridge = if (profile.scheme.equals("ssh", true)) SshTransportManager() else null

        return try {
            runCatching {
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

                val sourceConfig = if (sshBridge != null) {
                    SshProfileCodec.xrayClientConfig(sshBridge.start(profile))
                } else {
                    profile.configJson
                }

                fun runTemporaryConfig(configText: String): Boolean {
                    config.writeText(configText)
                    val temporaryProcess = createProcessBuilder(
                        "run",
                        "-c",
                        config.absolutePath
                    )
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(benchmarkLog))
                        .start()

                    return try {
                        if (!waitSocksPort(actualPort, portWaitMs, temporaryProcess)) {
                            false
                        } else {
                            block(actualPort)
                            true
                        }
                    } finally {
                        stopTemporaryProcess(temporaryProcess)
                    }
                }

                try {
                    val primaryConfig =
                        if (delayTest) {
                            XrayConfigHardener.hardenForDelayTest(sourceConfig, actualPort)
                        } else {
                            XrayConfigHardener.harden(sourceConfig, actualPort, benchmarkSettings)
                        }

                    val primaryStarted = runTemporaryConfig(primaryConfig)
                    if (!primaryStarted && delayTest) {
                        // Some imported transports depend on runtime-compatible hardening that the
                        // minimal delay config intentionally trims. Retry once with the exact class
                        // of config used by a real connection before declaring the node dead.
                        runCatching {
                            benchmarkLog.appendText(
                                "\n[MarbleNG] minimal delay config failed; retrying runtime-compatible config\n"
                            )
                        }
                        runTemporaryConfig(
                            XrayConfigHardener.harden(
                                sourceConfig,
                                actualPort,
                                benchmarkSettings
                            )
                        )
                    } else {
                        primaryStarted
                    }
                } finally {
                    sshBridge?.stop()
                    runCatching { config.delete() }
                }
            }.getOrElse {
                sshBridge?.stop()
                runCatching { config.delete() }
                false
            }
        } finally {
            releaseTemporaryPort(actualPort)
        }
    }

    private fun waitSocksPort(
        port: Int,
        timeoutMs: Long,
        target: Process? = null
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)

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
        while (System.nanoTime() < deadline) {
            if (target != null && !target.isAlive) return false

            val listenerBound = !portAvailable(port)
            if (listenerBound && (target == null || target.isAlive)) {
                return true
            }

            try {
                Thread.sleep(25L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        return false
    }

    private fun reserveTemporaryPort(preferred: Int): Int? =
        synchronized(temporaryPortLock) {
            val minPort = 18_080
            val maxPort = 62_000
            val span = maxPort - minPort + 1
            val start = preferred.coerceIn(minPort, maxPort)

            for (offset in 0 until span) {
                val candidate = minPort + ((start - minPort + offset) % span)
                if (candidate in reservedTemporaryPorts) continue
                if (!portAvailable(candidate)) continue

                reservedTemporaryPorts += candidate
                return@synchronized candidate
            }
            null
        }

    private fun releaseTemporaryPort(port: Int) {
        synchronized(temporaryPortLock) {
            reservedTemporaryPorts.remove(port)
        }
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
