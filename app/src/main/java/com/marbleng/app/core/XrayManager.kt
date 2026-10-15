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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

data class RoutingAssetStatus(
    val geoIpReady: Boolean,
    val geoIpBytes: Long,
    val geoIpRemote: Boolean,
    val geoIpUpdatedAt: Long,
    val geoSiteReady: Boolean,
    val geoSiteBytes: Long,
    val geoSiteRemote: Boolean,
    val geoSiteUpdatedAt: Long
)

class XrayManager(private val context: Context) {
    @Volatile var singBox: SingBoxManager? = null
    @Volatile var intelligence: MarbleIntelligence? = null

    val measurementCoreCeiling: Int =
        MeasurementCoreBudget.BASE + MeasurementCoreBudget.read(context)

    private fun underlayHasIpv6(): Boolean = intelligence?.currentSnapshot()
        ?.takeIf { it.transport != "unknown" }?.hasIpv6 ?: AddressFamilyPolicy.underlayHasIpv6()

    internal companion object {
        const val ROUTING_ASSET_REFRESH_MS = 24L * 60L * 60L * 1000L
        const val ROUTING_ASSET_RETRY_MS = 6L * 60L * 60L * 1000L

        internal fun summarizeStartFailure(lines: List<String>): String {
            val meaningful = lines.map { it.trim() }.filter { it.isNotBlank() }
            if (meaningful.isEmpty()) return ""
            val fatal = meaningful.lastOrNull { line ->
                line.contains("Failed to start", ignoreCase = true) ||
                    line.contains("[Error]", ignoreCase = true) ||
                    line.contains("panic:", ignoreCase = true) ||
                    line.contains("fatal", ignoreCase = true)
            }
            val context = meaningful.takeLast(4).filterNot { it == fatal }
            val ordered = if (fatal != null) listOf(fatal) + context else context
            return ordered.joinToString(" | ").take(900)
        }
    }
    private val lifecycleLock = Any()
    private val assetLock = ReentrantLock()
    private val geoIndexRefreshQueued = AtomicBoolean(false)

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

        return -1L
    }
    val logFile: File get() = File(context.filesDir, "logs/xray.log")
    val transportTelemetryFile: File get() = File(context.filesDir, "logs/xray-transport.jsonl")
    private val bin: File get() = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
    private val assetsDir = File(context.filesDir, "xray-assets")
    private val runtimeConfig: File get() = File(context.filesDir, "runtime.json")

    private fun writeRuntimeConfig(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        runCatching {
            java.io.FileOutputStream(temp).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()

                runCatching { output.fd.sync() }
            }
        }.onFailure {
            runCatching { temp.delete() }
            throw it
        }
        if (!temp.renameTo(target)) {
            target.writeText(text)
            runCatching { temp.delete() }
        }
    }

    fun discardStaleRuntimeArtifacts(): List<String> {
        val removed = mutableListOf<String>()
        listOf(
            runtimeConfig to "runtime.json",
            File(runtimeConfig.parentFile, runtimeConfig.name + ".tmp") to "runtime.json.tmp",
            transportTelemetryFile to "xray-transport.jsonl"
        ).forEach { (file, label) ->
            runCatching {
                if (file.isFile && file.delete()) removed += label
            }
        }
        synchronized(validatedConfigHashes) { validatedConfigHashes.clear() }
        return removed
    }

    fun prepareRoutingAssets(
        settings: AppSettings = AppSettings(),
        force: Boolean = false
    ): RoutingAssetStatus {
        assetLock.lock()
        try {
            assetsDir.mkdirs()

            val sources = GeoAssetRegistry.resolve(settings)
            sources.forEachIndexed { index, spec ->
                val primary = index == 0
                prepareSourceFile(spec, GeoAssetRegistry.Kind.GEOIP, primary, force)
                prepareSourceFile(spec, GeoAssetRegistry.Kind.GEOSITE, primary, force)
            }
            refreshGeoAssetIndex(settings)
            return routingAssetStatus()
        } finally {
            assetLock.unlock()
        }
    }

    private fun prepareSourceFile(
        spec: GeoAssetRegistry.SourceSpec,
        kind: GeoAssetRegistry.Kind,
        primary: Boolean,
        force: Boolean
    ) {
        if (!spec.provides(kind)) return
        val name = GeoAssetRegistry.fileName(spec, kind, primary)
        runCatching { ensureAsset(name, spec.urlFor(kind), force) }
            .onFailure { lastGeoAssetError = "${it.message ?: it.javaClass.simpleName} ($name)" }
    }

    @Volatile
    var lastGeoAssetError: String = ""
        private set

    fun refreshGeoAssetIndex() {
        runCatching { GeoAssetIndex.update(assetsDir) }
    }

    fun refreshGeoAssetIndex(settings: AppSettings): GeoAssetIndex.Snapshot? {
        assetLock.lock()
        try {
            return runCatching {
                val files = ArrayList<GeoAssetIndex.IndexedFile>()
                GeoAssetRegistry.resolve(settings).forEachIndexed { index, spec ->
                    val primary = index == 0
                    listOf(GeoAssetRegistry.Kind.GEOSITE, GeoAssetRegistry.Kind.GEOIP).forEach { kind ->
                        if (!spec.provides(kind)) return@forEach
                        files += GeoAssetIndex.IndexedFile(
                            File(assetsDir, GeoAssetRegistry.fileName(spec, kind, primary)),
                            if (kind == GeoAssetRegistry.Kind.GEOSITE) {
                                GeoAssetIndex.Kind.GEOSITE
                            } else {
                                GeoAssetIndex.Kind.GEOIP
                            }
                        )
                    }
                }
                if (files.isEmpty()) GeoAssetIndex.update(assetsDir)
                else GeoAssetIndex.update(assetsDir, files)
            }.getOrNull()
        } finally {
            assetLock.unlock()
        }
    }

    private fun scheduleGeoAssetIndexRefresh(settings: AppSettings) {
        if (!geoIndexRefreshQueued.compareAndSet(false, true)) return
        runCatching {
            Thread({
                try {
                    assetLock.lock()
                    try {
                        refreshGeoAssetIndex(settings)
                    } finally {
                        assetLock.unlock()
                    }
                } finally {
                    geoIndexRefreshQueued.set(false)
                }
            }, "MarbleGeoAssetIndex").apply {
                isDaemon = true
                start()
            }
        }.onFailure {
            geoIndexRefreshQueued.set(false)
        }
    }

    private data class ReadyGeoAssets(
        val files: List<String>,
        val tagsByFile: Map<String, Set<String>>,
        val geoIpReady: Boolean = false,
        val geoSiteReady: Boolean = false,
        val geoIpBytes: Long = 0L,
        val geoSiteBytes: Long = 0L
    )

    private fun indexedGeoAssets(
        settings: AppSettings,
        refresh: Boolean = true
    ): ReadyGeoAssets {
        val index = (if (refresh) refreshGeoAssetIndex(settings) else GeoAssetIndex.current())
            ?: return ReadyGeoAssets(emptyList(), emptyMap())
        val ready = linkedMapOf<String, Set<String>>()
        var geoIpReady = false
        var geoSiteReady = false
        var geoIpBytes = 0L
        var geoSiteBytes = 0L
        GeoAssetRegistry.resolve(settings).forEachIndexed { indexInSelection, spec ->
            val primary = indexInSelection == 0
            listOf(GeoAssetRegistry.Kind.GEOIP, GeoAssetRegistry.Kind.GEOSITE).forEach { kind ->
                if (!spec.provides(kind)) return@forEach
                val file = File(assetsDir, GeoAssetRegistry.fileName(spec, kind, primary))
                val indexedKind = if (kind == GeoAssetRegistry.Kind.GEOIP) {
                    GeoAssetIndex.Kind.GEOIP
                } else {
                    GeoAssetIndex.Kind.GEOSITE
                }
                if (!index.isUsableFile(file, indexedKind)) return@forEach
                val tags = index.tagsForFile(file.name, indexedKind) ?: return@forEach
                ready[file.name] = tags
                if (kind == GeoAssetRegistry.Kind.GEOIP) {
                    geoIpReady = true
                    geoIpBytes += file.length()
                } else {
                    geoSiteReady = true
                    geoSiteBytes += file.length()
                }
            }
        }
        return ReadyGeoAssets(
            ready.keys.toList(), ready, geoIpReady, geoSiteReady, geoIpBytes, geoSiteBytes
        )
    }

    fun readyGeoAssetFiles(settings: AppSettings): List<String> =
        indexedGeoAssets(settings).files

    fun geoSourceStates(settings: AppSettings): List<GeoAssetRegistry.SourceState> {
        val sources = GeoAssetRegistry.resolve(settings)
        return sources.mapIndexed { index, spec ->
            val primary = index == 0
            val ip = File(assetsDir, GeoAssetRegistry.fileName(spec, GeoAssetRegistry.Kind.GEOIP, primary))
            val site = File(assetsDir, GeoAssetRegistry.fileName(spec, GeoAssetRegistry.Kind.GEOSITE, primary))
            val ipOk = ip.isFile && looksLikeGeoDatabase(ip)
            val siteOk = site.isFile && looksLikeGeoDatabase(site)
            GeoAssetRegistry.SourceState(
                spec = spec,
                primary = primary,
                geoIpReady = ipOk,
                geoSiteReady = siteOk,
                geoIpBytes = if (ip.isFile) ip.length() else 0L,
                geoSiteBytes = if (site.isFile) site.length() else 0L,
                updatedAtMs = maxOf(
                    if (ip.isFile) ip.lastModified() else 0L,
                    if (site.isFile) site.lastModified() else 0L
                )
            )
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

            assetsDir.listFiles()?.forEach { file ->
                val name = file.name
                if (name.startsWith("geoip-") || name.startsWith("geosite-")) {
                    file.delete()
                    sourceMarker(name).delete()
                    refreshFailureMarker(name).delete()
                    File(assetsDir, "$name.download").delete()
                }
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
            geoIpReady = looksLikeGeoDatabase(ip),
            geoIpBytes = ip.takeIf { it.isFile }?.length() ?: 0L,
            geoIpRemote = ipSource.startsWith("http://") || ipSource.startsWith("https://"),
            geoIpUpdatedAt = ip.takeIf { it.isFile }?.lastModified() ?: 0L,
            geoSiteReady = looksLikeGeoDatabase(site),
            geoSiteBytes = site.takeIf { it.isFile }?.length() ?: 0L,
            geoSiteRemote = siteSource.startsWith("http://") || siteSource.startsWith("https://"),
            geoSiteUpdatedAt = site.takeIf { it.isFile }?.lastModified() ?: 0L
        )
    }

    private fun prepareRoutingAssetsForConnect(settings: AppSettings): RoutingAssetStatus {
        val needGeoIp = requiresGeoIp(settings)
        val needGeoSite = requiresGeoSite(settings)
        val present = routingAssetStatus()
        if (!needGeoIp && !needGeoSite) return present

        val primaryIsBundled = GeoAssetRegistry.resolve(settings).firstOrNull()?.bundled == true
        if (!primaryIsBundled) return present

        assetsDir.mkdirs()
        if (needGeoIp && !present.geoIpReady) {
            val destination = File(assetsDir, "geoip.dat")
            if (copyBundledAsset("geoip.dat", destination)) {
                sourceMarker("geoip.dat").writeText("apk://xray/geoip.dat")
                refreshFailureMarker("geoip.dat").delete()
            }
        }
        if (needGeoSite && !present.geoSiteReady) {
            val destination = File(assetsDir, "geosite.dat")
            if (copyBundledAsset("geosite.dat", destination)) {
                sourceMarker("geosite.dat").writeText("apk://xray/geosite.dat")
                refreshFailureMarker("geosite.dat").delete()
            }
        }
        return routingAssetStatus()
    }

    private fun ensureAsset(name: String, remoteUrl: String, force: Boolean) {
    val destination = File(assetsDir, name)
    val marker = sourceMarker(name)
    val failureMarker = refreshFailureMarker(name)
    var currentSource = runCatching { marker.readText().trim() }.getOrDefault("")
    var ready = destination.isFile && destination.length() > 1024L
    val now = System.currentTimeMillis()
    val canonicalDefault = defaultRemoteUrl(name)

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
                marker.writeText(remoteUrl)
                failureMarker.delete()
                return
            }
            lastError = attempt.exceptionOrNull()
        }

        if (ready) {
            runCatching {
                failureMarker.writeText(lastError?.message.orEmpty().take(300))
                failureMarker.setLastModified(now)
            }
            return
        }

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
            require(looksLikeGeoDatabase(temp)) { "Bundled $name is not a geo database" }
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
            if (declared >= 0) {
                require(temp.length() == declared) {
                    "${destination.name} download is incomplete (${temp.length()}/$declared bytes)"
                }
            }
            require(looksLikeGeoDatabase(temp)) {
                "${destination.name} download is not a geo database"
            }
            replaceFile(temp, destination)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

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

    private fun looksLikeGeoDatabase(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() <= 1024L) return@runCatching false
        file.inputStream().use { input ->
            val header = ByteArray(6)
            val read = input.read(header)
            if (read < 2) return@runCatching false
            if (header[0].toInt() and 0xFF != 0x0A) return@runCatching false
            var length = 0L
            var shift = 0
            for (index in 1 until read) {
                val byte = header[index].toInt() and 0xFF
                length = length or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) {
                    return@runCatching length in 1..file.length()
                }
                shift += 7
                if (shift > 28) return@runCatching false
            }
            false
        }
    }.getOrDefault(false)

    private fun sourceMarker(name: String) = File(assetsDir, "$name.source")
    private fun refreshFailureMarker(name: String) = File(assetsDir, "$name.refresh-failed")

    private fun requiresGeoIp(settings: AppSettings): Boolean = RoutingEngine.needsGeoIp(settings)

    private fun requiresGeoSite(settings: AppSettings): Boolean = RoutingEngine.needsGeoSite(settings)

    private fun createProcessBuilder(vararg args: String): ProcessBuilder {
        val command = ArrayList<String>()
        command += bin.absolutePath
        command += args
        return ProcessBuilder(command).apply {
            redirectErrorStream(true)
            environment()["XRAY_LOCATION_ASSET"] = assetsDir.absolutePath

            environment()[CoreConfigSuperset.PLAINTEXT_POLICY_ENV] = "1"
        }
    }

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
        if (shouldPrepareAssets) prepareRoutingAssets(settings, force = false)

        val assetLease = try {
            assetLock.tryLock(250L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        return try {
            val indexed = if (assetLease) indexedGeoAssets(settings, refresh = false)
                else ReadyGeoAssets(emptyList(), emptyMap())
            val geoIpReady = assetLease && indexed.geoIpReady
            val geoSiteReady = assetLease && indexed.geoSiteReady

            val gated = RoutingEngine.withGeoAssetGate(settings, geoIpReady, geoSiteReady)
            val effectiveSettings = gated.copy(
                measuredGeoReadyFiles = indexed.files.joinToString(",").ifEmpty { "none" },
                measuredGeoTagsByFile = indexed.tagsByFile,
                measuredGeoMembershipKnown = true
            )
            val downgrade = RoutingEngine.geoDowngradeReason(settings, geoIpReady, geoSiteReady)

            val sourceConfig = if (profile.scheme.equals("ssh", true)) {
                SshProfileCodec.xrayClientConfig(19090)
            } else {
                profile.configJson
            }

            val config = File(context.cacheDir, "routing-policy-verify.json")
            val verifyLog = File(context.cacheDir, "routing-policy-verify.log")
            verifyLog.delete()

            try {
                config.writeText(XrayConfigHardener.harden(
                    sourceConfig, 19091, effectiveSettings, underlayHasIpv6 = underlayHasIpv6()
                ))
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

                val ipState = when {
                    geoIpReady -> "READY ${indexed.geoIpBytes / 1024} KiB"
                    needGeoIp && !assetLease -> "PAUSED (assets busy)"
                    needGeoIp -> "UNAVAILABLE"
                    else -> "NOT REQUIRED"
                }
                val siteState = when {
                    geoSiteReady -> "READY ${indexed.geoSiteBytes / 1024} KiB"
                    needGeoSite && !assetLease -> "PAUSED (assets busy)"
                    needGeoSite -> "UNAVAILABLE"
                    else -> "NOT REQUIRED"
                }
                val note = if (downgrade.isBlank()) "" else " • $downgrade"
                "Routing verified by Xray • GeoIP $ipState • GeoSite $siteState$note"
            } finally {
                runCatching { config.delete() }
            }
        } finally {
            if (assetLease) assetLock.unlock()
        }
    }

    fun start(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings = AppSettings(),
        link: LinkEvidence = LinkEvidence.UNKNOWN
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

        val assetLease = try {
            assetLock.tryLock(250L, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

        return try {
            runCatching {
            publishStartState(ticket.generation, "port-check")
            if (!portAvailable(port)) {
                val reclaim = CorePortGuard.reclaim(
                    probe = CorePortGuard.androidProbe(),
                    port = port,
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    coreBinaries = listOf(CoreEngineInfo.XRAY_BINARY, CoreEngineInfo.SINGBOX_BINARY)
                )
                if (!reclaim.available) {
                    return@runCatching failStart(
                        ticket.generation,
                        reclaim.evidence.ifBlank { "core-port: Local port $port is already in use" }
                    )
                }
            }
            if (!startStillCurrent(ticket.generation)) return@runCatching false

            publishStartState(ticket.generation, "assets-local")
            if (assetLease) prepareRoutingAssetsForConnect(settings)
            val indexedAssets = if (assetLease) indexedGeoAssets(settings, refresh = false)
                else ReadyGeoAssets(emptyList(), emptyMap())
            val geoIpReady = indexedAssets.geoIpReady
            val geoSiteReady = indexedAssets.geoSiteReady
            if ((requiresGeoIp(settings) && !geoIpReady) ||
                (requiresGeoSite(settings) && !geoSiteReady)
            ) {
                scheduleGeoAssetIndexRefresh(settings)
            }

            val gated = RoutingEngine.withGeoAssetGate(
                settings,
                geoIpReady = geoIpReady,
                geoSiteReady = geoSiteReady
            )
            val effectiveSettings = gated.copy(

                measuredGeoReadyFiles = indexedAssets.files.joinToString(",").ifEmpty { "none" },
                measuredGeoTagsByFile = indexedAssets.tagsByFile,
                measuredGeoMembershipKnown = true
            )
            val downgrade = RoutingEngine.geoDowngradeReason(settings, geoIpReady, geoSiteReady)
            if (downgrade.isNotBlank()) {
                runCatching { logFile.appendText("\n[MarbleNG] $downgrade\n") }
            }
            if (!startStillCurrent(ticket.generation)) return@runCatching false

            publishStartState(ticket.generation, "config")
            val sourceConfig = if (profile.scheme.equals("ssh", true)) {
                SshProfileCodec.xrayClientConfig(startSshBridge(ticket.generation, profile, settings))
            } else {
                profile.configJson
            }
            writeRuntimeConfig(
                config,
                XrayConfigHardener.harden(
                    sourceConfig, port, effectiveSettings, link,
                    underlayHasIpv6 = underlayHasIpv6()
                )
            )

            if (!startStillCurrent(ticket.generation)) return@runCatching false

            publishStartState(ticket.generation, "spawn")
            transportTelemetryFile.parentFile?.mkdirs(); runCatching { transportTelemetryFile.delete() }
            val liveBuilder = createProcessBuilder("run", "-c", config.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))

            liveBuilder.environment()["MARBLE_TELEMETRY_FILE"] = transportTelemetryFile.absolutePath
            val startedProcess = liveBuilder.start()

            if (!publishProcess(ticket.generation, startedProcess)) {
                stopProcess(startedProcess)
                return@runCatching false
            }

            publishStartState(ticket.generation, "listener")
            if (!waitSocksPort(port, 7_000L, startedProcess)) {
                val cancelled = !startStillCurrent(ticket.generation)

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
        } finally {
            if (assetLease) assetLock.unlock()
        }
    }

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

    fun temporary(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings = AppSettings(),
        delayTest: Boolean = false,
        link: LinkEvidence = LinkEvidence.UNKNOWN,
        block: (Int) -> Unit
    ): Boolean {
        if (settings.coreEngine() == CoreEngine.SINGBOX) {
            return (singBox ?: error("core-install: sing-box manager is unavailable"))
                .temporary(profile, settings, block)
        }
        if (!bin.isFile || port !in 0..65535) return false
        val actualPort = reserveTemporaryPort(port) ?: return false

        val config = File(context.cacheDir, "bench-$actualPort.json")
        val benchmarkLog = File(context.cacheDir, "xray-bench-$actualPort.log")

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
                    routeBlockAds = false,
                    routingRulesJson = "[]"
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
                            XrayConfigHardener.hardenForDelayTest(
                                sourceConfig, actualPort, benchmarkSettings,
                                underlayHasIpv6 = underlayHasIpv6(), link = link
                            )
                        } else {
                            XrayConfigHardener.harden(
                                sourceConfig, actualPort, benchmarkSettings, link,
                                underlayHasIpv6 = underlayHasIpv6()
                            )
                        }

                    val primaryStarted = runTemporaryConfig(primaryConfig)
                    if (!primaryStarted && delayTest) {
                        runCatching {
                            benchmarkLog.appendText(
                                "\n[MarbleNG] minimal delay config failed; retrying runtime-compatible config\n"
                            )
                        }
                        runTemporaryConfig(
                            XrayConfigHardener.harden(
                                sourceConfig,
                                actualPort,
                                benchmarkSettings,
                                link,
                                underlayHasIpv6 = underlayHasIpv6()
                            )
                        )
                    } else {
                        primaryStarted
                    }
                } finally {
                    sshBridge?.stop()
                    runCatching { config.delete() }
                }
            }.getOrElse { error ->
                sshBridge?.stop()
                runCatching { config.delete() }
                if (error is InterruptedException) { Thread.currentThread().interrupt(); throw error }
                if (error is ConfigTranslationException) throw error
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
            logFile.useLines { lines -> summarizeStartFailure(lines.toList()) }
        }.getOrDefault("Xray log unavailable").ifBlank { "no Xray error detail" }
    }

    private fun fail(reason: String): Boolean {
        lastStartError = reason.take(1_200)
        return false
    }
}
