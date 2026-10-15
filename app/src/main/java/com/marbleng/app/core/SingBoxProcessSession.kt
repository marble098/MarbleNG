package com.marbleng.app.core

import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class StartReadiness(
    val inboundUp: Boolean,
    val controllerUp: Boolean,
    val controllerAwaited: Boolean,
    val phase: String,
    val elapsedMs: Long
) {
    val controllerMissing: Boolean get() = controllerAwaited && !controllerUp
}

interface ReadinessProbes {
    fun inbound(port: Int, timeoutMs: Long): Boolean

    fun controller(port: Int, secret: String, timeoutMs: Long): Boolean
}

class SingBoxProcessSession private constructor(
    private val child: Process,
    private val logPump: Thread,
    private val apiPort: Int,
    private val secret: String,
    val logFile: File
) : Closeable {
    val isAlive: Boolean get() = child.isAlive

    @Volatile var readiness: StartReadiness = StartReadiness(
        inboundUp = false, controllerUp = false, controllerAwaited = false,
        phase = "starting", elapsedMs = 0L
    )
        internal set

    @Volatile var stopEvidence: String = ""
        private set

    fun delay(url: String, timeoutMs: Int): CoreUrlTestResult {
        if (!isAlive) return CoreUrlTestResult(0, false, "core-exit: ${tail(logFile)}")

        UrlTestTarget.validate(url)?.let { return CoreUrlTestResult(0, false, it) }
        checkInterrupted()
        val budget = timeoutMs.coerceIn(1, 30_000)
        val endpoint = "http://127.0.0.1:$apiPort/proxies/${SingBoxConfigBuilder.PROXY_TAG}/delay" +
            "?url=${URLEncoder.encode(url, "UTF-8")}&timeout=$budget"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = minOf(1500, budget)
            readTimeout = budget + 250
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $secret")
        }
        return try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readTextLimited(4096) }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            val delay = json?.optLong("delay", -1) ?: -1
            if (status in 200..299 && delay > 0) CoreUrlTestResult(delay, true)
            else CoreUrlTestResult(0, false, "urltest-http-$status: ${json?.optString("message").orEmpty()}".take(300))
        } catch (error: Exception) {
            checkInterrupted()
            CoreUrlTestResult(0, false, "urltest-transport: ${error.message ?: error.javaClass.simpleName}".take(300))
        } finally { connection.disconnect() }
    }

    override fun close() {
        val exited = stop(child)
        if (!exited) {
            stopEvidence = "the previous core process did not exit within the stop budget " +
                "(destroy → destroyForcibly → wait); the port reaper owns it now"
        }

        val interrupted = Thread.interrupted()
        try { logPump.join(1000) } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    companion object {
        private const val LOG_LIMIT = 512 * 1024L

        const val CONTROLLER_TIMEOUT_MS: Long = 2_500L

        const val VALIDATE_TIMEOUT_MS: Long = 8_000L

        private object SocketProbes : ReadinessProbes {
            override fun inbound(port: Int, timeoutMs: Long): Boolean = listening(port, timeoutMs)
            override fun controller(port: Int, secret: String, timeoutMs: Long): Boolean =
                apiReady(port, secret, timeoutMs)
        }

        fun open(binary: File, config: String, configFile: File, logFile: File,
                 tempDir: File, socksPort: Int, apiPort: Int, secret: String,
                 startupTimeoutMs: Long = 12_000,
                 validate: Boolean = true,
                 awaitApi: Boolean = true,
                 settleMs: Long = 0,
                 requireController: Boolean = awaitApi,
                 controllerTimeoutMs: Long = CONTROLLER_TIMEOUT_MS,
                 validateTimeoutMs: Long = VALIDATE_TIMEOUT_MS,
                 probes: ReadinessProbes? = null): SingBoxProcessSession {
            checkInterrupted()

            val probe = probes ?: SocketProbes
            val openedNs = System.nanoTime()
            fun elapsed(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - openedNs)
            val root = JSONObject(config)

            root.optJSONObject("log")?.remove("output")
            configFile.parentFile?.mkdirs()
            atomicWrite(configFile, root.toString())
            check(binary.isFile) { "core-install: sing-box executable is missing" }
            tempDir.mkdirs()
            logFile.parentFile?.mkdirs()
            logFile.writeText("")
            if (validate) {
                val diagnostic = File.createTempFile("check-", ".log", tempDir)
                try {
                    val validator = builder(binary, listOf("check", "-c", configFile.absolutePath), configFile.parentFile, tempDir)
                        .redirectOutput(diagnostic).start()
                    try {
                        if (!validator.waitFor(validateTimeoutMs, TimeUnit.MILLISECONDS)) {
                            error("core-check-timeout: configuration validation did not finish")
                        }
                        check(validator.exitValue() == 0) { "core-config: ${tail(diagnostic)}" }
                    } finally { stop(validator) }
                } finally { diagnostic.delete() }
            }
            checkInterrupted()

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupTimeoutMs)
            fun left(): Long = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(0)
            check(left() > 0) { "core-start-timeout" }
            val child = builder(binary, listOf("run", "-c", configFile.absolutePath), configFile.parentFile, tempDir).start()
            val pump = Thread({
                runCatching {
                    child.inputStream.use { input ->
                        RandomAccessFile(logFile, "rw").use { sink ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (sink.length() + count > LOG_LIMIT) { sink.setLength(0); sink.seek(0) }
                                sink.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }, "marble-singbox-log").apply { isDaemon = true; start() }
            val session = SingBoxProcessSession(child, pump, apiPort, secret, logFile)
            try {
                var pollDelayMs = 4L
                var inboundUp = false
                while (left() > 0) {
                    checkInterrupted()
                    if (!child.isAlive) {
                        pump.join(200)
                        error("core-start: exited ${child.exitValue()}: ${tail(logFile)}")
                    }
                    if (probe.inbound(socksPort, left())) {
                        inboundUp = true
                        break
                    }
                    Thread.sleep(minOf(pollDelayMs, left()).coerceAtLeast(1))
                    pollDelayMs = (pollDelayMs * 2L).coerceAtMost(25L)
                }

                if (!inboundUp) {
                    error(
                        "core-start-timeout: the local inbound never answered in " +
                            "$startupTimeoutMs ms: ${tail(logFile)}"
                    )
                }

                settle(child, pump, logFile, settleMs, left())
                if (!awaitApi) {
                    session.readiness = StartReadiness(
                        inboundUp = true, controllerUp = false, controllerAwaited = false,
                        phase = "ready", elapsedMs = elapsed()
                    )
                    return session
                }

                val window = if (requireController) left() else minOf(controllerTimeoutMs, left())
                val controllerUp = awaitController(child, pump, logFile, probe, apiPort, secret, window)
                if (!controllerUp && requireController) {
                    error(
                        "core-start-timeout: the controller never answered in $window ms " +
                            "(the local inbound was up): ${tail(logFile)}"
                    )
                }
                session.readiness = StartReadiness(
                    inboundUp = true, controllerUp = controllerUp, controllerAwaited = true,
                    phase = if (controllerUp) "ready" else "controller", elapsedMs = elapsed()
                )
                return session
            } catch (error: Throwable) {
                session.close()
                throw error
            }
        }

        private fun awaitController(child: Process, pump: Thread, logFile: File,
                                    probes: ReadinessProbes, apiPort: Int, secret: String,
                                    windowMs: Long): Boolean {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(windowMs.coerceAtLeast(0))
            var pollDelayMs = 25L
            while (true) {
                checkInterrupted()
                if (!child.isAlive) {
                    pump.join(200)
                    error("core-start: exited ${child.exitValue()}: ${tail(logFile)}")
                }
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(0)

                if (probes.controller(apiPort, secret, remaining)) return true
                if (remaining <= 0) return false
                Thread.sleep(minOf(pollDelayMs, remaining))
                pollDelayMs = (pollDelayMs * 2L).coerceAtMost(100L)
            }
        }

        private fun settle(child: Process, pump: Thread, logFile: File, settleMs: Long, left: Long) {
            if (settleMs <= 0) return
            val deadline = System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(minOf(settleMs, left).coerceAtLeast(0))
            while (System.nanoTime() < deadline) {
                checkInterrupted()
                if (!child.isAlive) {
                    pump.join(200)
                    error("core-start: exited ${child.exitValue()}: ${tail(logFile)}")
                }
                Thread.sleep(minOf(25, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)))
            }
        }

        private fun builder(binary: File, args: List<String>, workingDir: File?, tempDir: File) =
            SingBoxAndroidRuntime.prepare(ProcessBuilder(listOf(binary.absolutePath) + args).redirectErrorStream(true), workingDir, tempDir)

        internal fun listening(port: Int, left: Long): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), minOf(left, 150).coerceAtLeast(1).toInt()) }
            true
        }.getOrDefault(false)

        internal fun apiReady(port: Int, secret: String, left: Long): Boolean {
            val connection = (URL("http://127.0.0.1:$port/proxies").openConnection() as HttpURLConnection).apply {
                connectTimeout = minOf(left, 200).coerceAtLeast(1).toInt()
                readTimeout = connectTimeout
                setRequestProperty("Authorization", "Bearer $secret")
            }
            return try {
                connection.responseCode == 200 && connection.inputStream.bufferedReader().use {
                    JSONObject(it.readTextLimited(128 * 1024)).optJSONObject("proxies")?.has(SingBoxConfigBuilder.PROXY_TAG) == true
                }
            } catch (_: Exception) { false } finally { connection.disconnect() }
        }

        internal fun stop(child: Process): Boolean {
            val interrupted = Thread.interrupted()
            try {
                child.destroy()
                if (!child.waitFor(600, TimeUnit.MILLISECONDS)) {
                    child.destroyForcibly()
                    child.waitFor(1500, TimeUnit.MILLISECONDS)
                }
            } catch (_: InterruptedException) {
                child.destroyForcibly()
                Thread.currentThread().interrupt()
            } finally { if (interrupted) Thread.currentThread().interrupt() }

            return !child.isAlive
        }

        internal fun checkInterrupted() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("probe cancelled")
        }

        internal fun atomicWrite(target: File, text: String) {
            val temp = File.createTempFile("${target.name}-", ".tmp", target.parentFile)
            try {
                temp.outputStream().use { output -> output.write(text.toByteArray()); output.fd.sync() }
                check(temp.renameTo(target)) { "Cannot replace runtime config atomically" }
            } finally { temp.delete() }
        }

        fun tail(file: File, limit: Int = 4096): String = runCatching {
            RandomAccessFile(file, "r").use { input ->
                val size = minOf(input.length(), limit.toLong()).toInt()
                input.seek(input.length() - size)
                val bytes = ByteArray(size)
                input.readFully(bytes)
                String(bytes, Charsets.UTF_8).trim()
            }
        }.getOrDefault("")

        private fun java.io.Reader.readTextLimited(limit: Int): String {
            val buffer = CharArray(limit + 1)
            var total = 0
            while (total < buffer.size) {
                val count = read(buffer, total, buffer.size - total)
                if (count < 0) break
                total += count
            }
            check(total <= limit) { "Controller response exceeds limit" }
            return String(buffer, 0, total)
        }
    }
}
