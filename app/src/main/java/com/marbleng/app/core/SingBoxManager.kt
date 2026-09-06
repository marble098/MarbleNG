package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One sing-box extended URL test: the delay the core itself measured, or why it could not. */
data class SingBoxUrlTestResult(
    val delayMs: Long,
    val ok: Boolean,
    val detail: String = "",
    /** True when the measurement came from the tunnel the user is already connected to. */
    val live: Boolean = false
)

/**
 * MARBLE_SINGBOX_CORE_V151 — lifecycle of the sing-box extended process.
 *
 * Deliberately a sibling of [XrayManager] rather than a base-class sibling: the two cores share
 * nothing but the shape of their contract (write a config, spawn a child, wait for the local
 * listener, kill it on stop), and a shared abstraction would only hide which core a given line of
 * code actually drives. The one thing they must agree on is the local SOCKS endpoint, because
 * hev-socks5-tunnel dials it without knowing or caring which core owns it.
 *
 * On top of the run/stop pair this owns the **URL test**: sing-box extended speaks the Clash API,
 * so `GET /proxies/{tag}/delay?url=…&timeout=…` asks the *core* to measure a real round trip
 * through the selected outbound. That is the honest version of a latency number — no Kotlin socket
 * stands in for the tunnel.
 */
class SingBoxManager(private val context: Context) {

    @Volatile private var process: Process? = null

    @Volatile var lastStartError: String = ""
        private set

    @Volatile var lastStartPhase: String = "idle"
        private set

    /**
     * MARBLE_ENGINE_SELF_HEAL_V152 — repairs the last `sing-box check` rejection applied
     * automatically by [SingBoxConfigDoctor]. Empty when the last config was accepted as
     * written (the normal case), or when no repair was possible. Surfaced through the VPN
     * service diagnostics so a self-healed start is visible in the log instead of silent.
     */
    @Volatile var lastSelfHealNotes: List<String> = emptyList()
        private set

    /** Clash API port of the running instance; 0 when nothing is running. */
    @Volatile var apiPort: Int = 0
        private set

    @Volatile private var apiSecret: String = ""

    val isAlive: Boolean get() = process?.isAlive == true

    private val bin: File get() = File(context.applicationInfo.nativeLibraryDir, CoreEngineInfo.SINGBOX_BINARY)

    val isInstalled: Boolean get() = bin.isFile && bin.length() > 1024L

    val logFile: File get() = File(context.filesDir, "logs/singbox.log")

    private val runtimeConfig: File get() = File(context.filesDir, "runtime-singbox.json")

    private val cacheFile: File get() = File(context.filesDir, "singbox-cache.db")

    /**
     * Starts the second engine for [profile].
     *
     * @param port the local SOCKS/HTTP mixed endpoint hev-socks5-tunnel will dial.
     */
    fun start(
        profile: ProxyProfile,
        port: Int,
        settings: AppSettings = AppSettings()
    ): Boolean {
        synchronized(this) {
            stopLocked()
            lastStartError = ""
            lastStartPhase = "begin"
            lastSelfHealNotes = emptyList()

            if (!isInstalled) {
                return fail("sing-box extended binary is missing from this build")
            }
            if (port !in 1..65535) {
                return fail("Invalid local SOCKS port: $port")
            }

            val support = SingBoxConfigBuilder.describe(profile, settings)
            if (!support.supported) return fail(support.reason)

            logFile.parentFile?.mkdirs()
            val controllerPort = freePort() ?: return fail("No free port for the sing-box controller")
            val secret = UUID.randomUUID().toString()

            val built = runCatching {
                SingBoxConfigBuilder.build(
                    profile = profile,
                    settings = settings,
                    socksPort = port,
                    apiPort = controllerPort,
                    apiSecret = secret,
                    logPath = logFile.absolutePath,
                    cachePath = cacheFile.absolutePath
                )
            }.getOrElse { return fail("Config: ${it.message ?: it::class.java.simpleName}") }

            lastStartPhase = "write"
            val config = runtimeConfig
            runCatching { config.writeText(built.json) }
                .onFailure { return fail("Config write failed: ${it.message}") }

            // MARBLE_ENGINE_SELF_HEAL_V152 — a config the core rejects is repaired in place and
            // re-checked before the attempt is allowed to fail. The shipped log shows what
            // happens without this: a single removed `dns` outbound refused all 17 profiles and
            // every session ended BLOCKED. The doctor fixes the known removals (deprecated dns
            // outbound, pre-1.12 DNS `address` key), and only a config that still fails after
            // repair is reported as a start error.
            lastStartPhase = "check"
            var rejection = checkConfig(config)
            if (rejection != null) {
                val repair = SingBoxConfigDoctor.repair(config.readText())
                if (repair.repaired) {
                    runCatching {
                        config.writeText(repair.json)
                        lastSelfHealNotes = repair.notes
                    }.onFailure {
                        lastSelfHealNotes = emptyList()
                        return fail("Config write failed: ${it.message}")
                    }
                    lastStartPhase = "self-heal"
                    rejection = checkConfig(config)
                    if (rejection == null) {
                        lastStartPhase = "check"
                    }
                }
            }
            rejection?.let { return fail("sing-box rejected the config: $it") }

            lastStartPhase = "spawn"
            val child = runCatching {
                ProcessBuilder(bin.absolutePath, "run", "-c", config.absolutePath)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .start()
            }.getOrElse { return fail("Spawn failed: ${it.message}") }

            process = child
            apiPort = controllerPort
            apiSecret = secret

            lastStartPhase = "listener"
            if (!waitPort(port, 7_000L, child)) {
                val alive = child.isAlive
                val exit = if (alive) null else runCatching { child.exitValue() }.getOrNull()
                stopLocked()
                return fail(
                    if (alive) {
                        "sing-box listener did not open: ${lastLogHint()}"
                    } else {
                        "sing-box exited with code ${exit ?: -1}: ${lastLogHint()}"
                    }
                )
            }

            lastStartPhase = "ready"
            return true
        }
    }

    fun stop() {
        synchronized(this) { stopLocked() }
    }

    private fun stopLocked() {
        val child = process
        process = null
        apiPort = 0
        apiSecret = ""
        lastStartPhase = "stopped"
        if (child == null) return
        runCatching { child.destroy() }
        runCatching {
            if (!child.waitFor(2_500L, TimeUnit.MILLISECONDS)) child.destroyForcibly()
        }
    }

    private fun fail(reason: String): Boolean {
        lastStartError = reason
        lastStartPhase = "failed"
        return false
    }

    /** `sing-box check` — the core's own verdict, before the tunnel is trusted with it. */
    fun checkConfig(config: File): String? {
        val output = File(context.cacheDir, "singbox-check.log")
        runCatching { output.delete() }
        val check = runCatching {
            ProcessBuilder(bin.absolutePath, "check", "-c", config.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.to(output))
                .start()
        }.getOrElse { return it.message ?: it::class.java.simpleName }

        if (!runCatching { check.waitFor(15, TimeUnit.SECONDS) }.getOrDefault(false)) {
            runCatching { check.destroyForcibly() }
            return "check timed out"
        }
        if (check.exitValue() == 0) return null
        return runCatching {
            output.useLines { lines ->
                lines.filter { it.isNotBlank() }.toList().takeLast(6).joinToString(" | ")
            }.take(900)
        }.getOrDefault("check failed")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // URL test
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Asks the *running* sing-box extended instance to URL-test [tag].
     *
     * @return the delay the core measured, or the reason it could not.
     */
    fun urlTestLive(tag: String, url: String, timeoutMs: Int): SingBoxUrlTestResult {
        val port = apiPort
        val secret = apiSecret
        if (port <= 0 || !isAlive) {
            return SingBoxUrlTestResult(0L, false, "sing-box extended is not running")
        }
        val result = requestDelay(port, secret, tag, url, timeoutMs)
        return result.copy(live = true)
    }

    /**
     * URL-tests a node that is not connected.
     *
     * A throwaway sing-box extended instance is started with the node's own outbound and a local
     * mixed inbound, the core measures the delay through it, and the instance is destroyed. This
     * is the sing-box equivalent of MarbleNG's Xray real-delay test: the number comes from the
     * core's transport, never from a Kotlin socket that never saw the tunnel.
     */
    fun urlTestProfile(
        profile: ProxyProfile,
        settings: AppSettings,
        url: String,
        timeoutMs: Int
    ): SingBoxUrlTestResult {
        if (!isInstalled) {
            return SingBoxUrlTestResult(0L, false, "sing-box extended binary is missing")
        }
        val support = SingBoxConfigBuilder.describe(profile, settings)
        if (!support.supported) {
            return SingBoxUrlTestResult(0L, false, support.reason)
        }

        val socksPort = freePort() ?: return SingBoxUrlTestResult(0L, false, "no free local port")
        val controllerPort = freePort()
            ?: return SingBoxUrlTestResult(0L, false, "no free controller port")
        val secret = UUID.randomUUID().toString()
        val config = File(context.cacheDir, "singbox-urltest.json")
        val log = File(context.cacheDir, "singbox-urltest.log")
        runCatching { log.delete() }

        val built = runCatching {
            SingBoxConfigBuilder.build(
                profile = profile,
                settings = settings,
                socksPort = socksPort,
                apiPort = controllerPort,
                apiSecret = secret,
                logPath = log.absolutePath,
                cachePath = File(context.cacheDir, "singbox-urltest-cache.db").absolutePath
            )
        }.getOrElse {
            return SingBoxUrlTestResult(0L, false, it.message ?: it::class.java.simpleName)
        }

        // MARBLE_ENGINE_SELF_HEAL_V152 — the throwaway URL-test instance gets the same doctor
        // pass as a real start, so a measurement never dies on a schema the core outgrew.
        val healed = SingBoxConfigDoctor.repair(built.json)

        runCatching { config.writeText(healed.json) }
            .onFailure {
                return SingBoxUrlTestResult(0L, false, "config write failed: ${it.message}")
            }

        var child: Process? = null
        return try {
            child = ProcessBuilder(bin.absolutePath, "run", "-c", config.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                .start()
            if (!waitPort(controllerPort, 8_000L, child)) {
                val hint = runCatching {
                    log.useLines { lines ->
                        lines.filter { it.isNotBlank() }.toList().takeLast(4).joinToString(" | ")
                    }.take(600)
                }.getOrDefault("")
                return SingBoxUrlTestResult(0L, false, "core did not start: $hint")
            }
            requestDelay(controllerPort, secret, SingBoxConfigBuilder.PROXY_TAG, url, timeoutMs)
        } catch (error: Throwable) {
            SingBoxUrlTestResult(0L, false, error.message ?: error::class.java.simpleName)
        } finally {
            child?.let { target ->
                runCatching { target.destroy() }
                runCatching {
                    if (!target.waitFor(2_000L, TimeUnit.MILLISECONDS)) target.destroyForcibly()
                }
            }
            runCatching { config.delete() }
        }
    }

    /**
     * `GET /proxies/{tag}/delay?url=&timeout=` — the Clash-compatible endpoint sing-box extended
     * serves from `experimental.clash_api`. It answers `{"delay": ms}` on success and a JSON
     * `message` on failure; both are surfaced verbatim so the UI never invents a reason.
     */
    private fun requestDelay(
        port: Int,
        secret: String,
        tag: String,
        url: String,
        timeoutMs: Int
    ): SingBoxUrlTestResult {
        val budget = timeoutMs.coerceIn(500, 30_000)
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val encodedTag = URLEncoder.encode(tag, "UTF-8")
        val endpoint = "http://127.0.0.1:$port/proxies/$encodedTag/delay" +
            "?url=$encodedUrl&timeout=${budget.coerceAtMost(30_000)}"

        val connection = runCatching {
            (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = budget + 2_000
                readTimeout = budget + 2_000
                instanceFollowRedirects = false
                if (secret.isNotBlank()) setRequestProperty("Authorization", "Bearer $secret")
            }
        }.getOrElse {
            return SingBoxUrlTestResult(0L, false, it.message ?: it::class.java.simpleName)
        }

        return try {
            val status = connection.responseCode
            val body = runCatching {
                (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            if (status in 200..299) {
                val delay = runCatching { JSONObject(body).optLong("delay", -1L) }.getOrDefault(-1L)
                if (delay > 0) {
                    SingBoxUrlTestResult(delay, true)
                } else {
                    SingBoxUrlTestResult(0L, false, "core returned no delay: $body".take(300))
                }
            } else {
                val message = runCatching { JSONObject(body).optString("message") }
                    .getOrDefault("")
                    .ifBlank { "HTTP $status" }
                SingBoxUrlTestResult(0L, false, message.take(300))
            }
        } catch (error: Throwable) {
            SingBoxUrlTestResult(0L, false, error.message ?: error::class.java.simpleName)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private fun freePort(): Int? = runCatching {
        ServerSocket(0).use { it.localPort }
    }.getOrNull()

    private fun waitPort(port: Int, timeoutMs: Long, child: Process): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!child.isAlive) return false
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                }
            }.onSuccess { return true }
            runCatching { Thread.sleep(90L) }
        }
        return false
    }

    private fun lastLogHint(): String = runCatching {
        logFile.useLines { lines ->
            lines.filter { it.isNotBlank() }.toList().takeLast(4).joinToString(" | ")
        }.take(600)
    }.getOrDefault("")
}
