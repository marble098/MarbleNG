package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
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
            File(assetsDir, "$name.download").delete()
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
        val currentSource = runCatching { marker.readText().trim() }.getOrDefault("")
        val ready = destination.isFile && destination.length() > 1024L

        if (remoteUrl.isNotBlank()) {
            val sameRemote = ready && currentSource == remoteUrl
            if (!force && sameRemote) return
            require(remoteUrl.startsWith("https://")) {
                "$name URL must use https:// because cleartext HTTP is disabled by the app network security policy"
            }
            downloadAsset(remoteUrl, destination)
            marker.writeText(remoteUrl)
            return
        }

        if (!force && ready) return
        val temp = File(assetsDir, "$name.download")
        val copied = runCatching {
            context.assets.open("xray/$name").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            require(temp.length() > 1024L) { "Bundled $name is empty" }
            replaceFile(temp, destination)
            marker.writeText("apk://xray/$name")
        }.isSuccess
        if (!copied) temp.delete()
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
        rotateLogIfNeeded()
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
            val configHash = sha256(configText)

            if (!validationCached(configHash)) {
                val testProcess = createProcessBuilder("run", "-test", "-c", config.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .start()

                if (!testProcess.waitFor(10, TimeUnit.SECONDS)) {
                    stopProcess(testProcess)
                    return@runCatching fail("Xray config validation timed out")
                }
                if (testProcess.exitValue() != 0) {
                    return@runCatching fail(
                        "Xray rejected the generated config (exit ${testProcess.exitValue()}): ${lastLogHint()}"
                    )
                }
                rememberValidation(configHash)
            }

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
            val validationKey = "bench:" + sha256(profile.configJson + "|" + benchmarkSettings.toString())
            val needsValidation = !validationCached(validationKey)

            if (needsValidation) {
                val testProcess = createProcessBuilder("run", "-test", "-c", config.absolutePath)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(benchmarkLog))
                    .start()

                if (!testProcess.waitFor(10, TimeUnit.SECONDS)) {
                    stopProcess(testProcess)
                    return@runCatching false
                }
                if (testProcess.exitValue() != 0) return@runCatching false

                rememberValidation(validationKey)
            }

            val temporaryProcess = createProcessBuilder("run", "-c", config.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(benchmarkLog))
                .start()
            try {
                if (!waitSocksPort(port, 7_000L, temporaryProcess)) false
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
        val deadline =
            System.currentTimeMillis() +
                timeoutMs

        while (
            System.currentTimeMillis() <
            deadline
        ) {
            val ready =
                runCatching {
                    Socket().use {
                        socket ->
                        socket.connect(
                            InetSocketAddress(
                                "127.0.0.1",
                                port
                            ),
                            250
                        )
                        socket.soTimeout =
                            350

                        val output =
                            socket
                                .getOutputStream()

                        output.write(
                            byteArrayOf(
                                0x05,
                                0x01,
                                0x00
                            )
                        )
                        output.flush()

                        val input =
                            socket
                                .getInputStream()

                        val version =
                            input.read()
                        val method =
                            input.read()

                        version == 0x05 &&
                            method == 0x00
                    }
                }.getOrDefault(false)

            if (ready) {
                return true
            }

            if (
                target != null &&
                !target.isAlive
            ) {
                return false
            }

            try {
                Thread.sleep(80L)
            } catch (
                _: InterruptedException
            ) {
                Thread.currentThread()
                    .interrupt()
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

    private fun rotateLogIfNeeded() {
        if (logFile.isFile && logFile.length() > 2L * 1024L * 1024L) {
            val previous = File(logFile.parentFile, "${logFile.name}.1")
            runCatching { previous.delete() }
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
