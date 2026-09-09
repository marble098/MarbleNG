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

/** JVM-testable process lifecycle shared by VPN, Real Delay and URL Test. Every session owns its
 * files, controller secret and process. No shell, global check file, shared BoltDB or swallowed
 * interruption. The manager separately bounds how many temporary sessions can exist. */
class SingBoxProcessSession private constructor(
    private val child: Process,
    private val logPump: Thread,
    private val apiPort: Int,
    private val secret: String,
    val logFile: File
) : Closeable {
    val isAlive: Boolean get() = child.isAlive

    /**
     * MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — non-empty when this session's child did not exit
     * inside [stop]'s budget. The manager carries the note into its own stop evidence, and the
     * next start's port reclaim ([CorePortGuard.reclaim]) is the layer that finishes the job:
     * a SIGTERM'd core that ignored it still owns its listening socket, and "stop returned" has
     * never meant "the port is free".
     */
    @Volatile var stopEvidence: String = ""
        private set

    fun delay(url: String, timeoutMs: Int): CoreUrlTestResult {
        if (!isAlive) return CoreUrlTestResult(0, false, "core-exit: ${tail(logFile)}")
        // One guard for the product's one URL test; see UrlTestTarget for why HTTPS and why no
        // user info. The pinned Clash API silently replaces http:// with its own gstatic URL.
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
        // A killed process closes the pipe. Do not leave a daemon copying a stale child's log.
        val interrupted = Thread.interrupted()
        try { logPump.join(1000) } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    companion object {
        private const val LOG_LIMIT = 512 * 1024L

        /**
         * MARBLE_REAL_DELAY_SPEED_V156 — [validate] runs `sing-box check` as a separate process
         * before `run`, and [awaitApi] waits for the Clash controller to answer.
         *
         * Both are load-bearing for a *live* session and both are pure overhead for a throwaway
         * measurement core:
         *
         *  - `run` performs the same schema validation `check` does and exits non-zero with the
         *    same message, which the startup loop below already turns into
         *    `core-start: exited N: <log>`. A second process spawn per measured server roughly
         *    doubled the cost of every Real delay and URL test in a sweep.
         *  - the controller is only needed by [delay]. A Real delay measurement goes through the
         *    SOCKS inbound, so waiting for the API added the router's full start-up to a
         *    measurement that never touches it.
         *
         * Defaults keep the connect path exactly as strict as it was.
         *
         * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — [settleMs] is the third parameter of the same
         * kind, and it exists because "the port listened" is not the same statement as "the core
         * started". `box.Start()` opens the inbounds at `StartStateStart` and only then walks the
         * outbounds through `StartStatePostStart`, so a core that dies in an outbound's post-start
         * — which is exactly where the Android nil-interface-monitor crash lived — can publish a
         * listening SOCKS port microseconds before it panics. A startup loop that returns on the
         * first successful connect therefore reports success for a process that is already dead.
         *
         * The live and measurement paths keep the default of `0`: they observe the child
         * continuously afterwards and a connect-time grace period would be paid on every server.
         * The self-test in [SingBoxCoreSelfTest] passes a real window, because its whole job is to
         * answer "does this binary survive startup on this device?" once, and a false PASS there
         * is worse than a slow one.
         */
        fun open(binary: File, config: String, configFile: File, logFile: File,
                 tempDir: File, socksPort: Int, apiPort: Int, secret: String,
                 startupTimeoutMs: Long = 12_000,
                 validate: Boolean = true,
                 awaitApi: Boolean = true,
                 settleMs: Long = 0): SingBoxProcessSession {
            checkInterrupted()
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(startupTimeoutMs)
            fun left(): Long = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(0)
            val root = JSONObject(config)
            // One sink only: the bounded pump owns the runtime log, including early fatal errors.
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
                    // Not `check`: a local val of that name would shadow kotlin.check and the
                    // assertion below would try to invoke the process instead.
                    val validator = builder(binary, listOf("check", "-c", configFile.absolutePath), configFile.parentFile, tempDir)
                        .redirectOutput(diagnostic).start()
                    try {
                        if (!validator.waitFor(left().coerceAtMost(8000), TimeUnit.MILLISECONDS)) {
                            error("core-check-timeout: configuration validation did not finish")
                        }
                        check(validator.exitValue() == 0) { "core-config: ${tail(diagnostic)}" }
                    } finally { stop(validator) }
                } finally { diagnostic.delete() }
            }
            checkInterrupted()
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
                while (left() > 0) {
                    checkInterrupted()
                    if (!child.isAlive) {
                        pump.join(200)
                        error("core-start: exited ${child.exitValue()}: ${tail(logFile)}")
                    }
                    if (listening(socksPort, left()) && (!awaitApi || apiReady(apiPort, secret, left()))) {
                        // A listening port proves the inbound started, not that the whole box
                        // did. See [settleMs].
                        settle(child, pump, logFile, settleMs, left())
                        return session
                    }
                    Thread.sleep(minOf(60, left()).coerceAtLeast(1))
                }
                error("core-start-timeout: ${tail(logFile)}")
            } catch (error: Throwable) {
                session.close()
                throw error
            }
        }

        /**
         * Waits out a bounded grace window after the endpoint answered, and turns a child that
         * dies inside it into the same `core-start: exited N: <log>` failure the startup loop
         * already produces. Interruptible, and never longer than the startup budget that is left.
         */
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

        private fun listening(port: Int, left: Long): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), minOf(left, 150).coerceAtLeast(1).toInt()) }
            true
        }.getOrDefault(false)

        private fun apiReady(port: Int, secret: String, left: Long): Boolean {
            val connection = (URL("http://127.0.0.1:$port/proxies").openConnection() as HttpURLConnection).apply {
                connectTimeout = minOf(left, 200).coerceAtLeast(1).toInt()
                readTimeout = connectTimeout
                setRequestProperty("Authorization", "Bearer $secret")
            }
            return try {
                // 401 is NOT ready. Also wait for this outbound, not just a listening port.
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
            // The contract this used to leave implicit: a stop that returns while the process is
            // still alive hands a live LISTEN socket to the next start. Returning the verdict
            // lets the session record it and lets the tests pin the escalation.
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
