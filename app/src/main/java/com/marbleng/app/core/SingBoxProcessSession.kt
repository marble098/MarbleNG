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

/**
 * MARBLE_SINGBOX_STARTUP_GATE_V162 — what a start-up wait actually observed.
 *
 * The pinned core's `box.Start()` walks its components through five stages. The local `mixed`
 * inbound — the only socket hev-socks5-tunnel ever dials — is listening at the `Start` stage. The
 * Clash controller MarbleNG reads for the URL test and the Engine page is an internal service, and
 * it binds at `Started`, the **last** stage, after every outbound's post-start walk, the rule-set
 * updater and the DNS router's final stage have run.
 *
 * Waiting for "inbound **and** controller" is therefore waiting for the entire start-up: a delay
 * in any stage that carries no user traffic is paid as a failed connect with the kill switch held,
 * even when the inbound is already serving. It also makes the two failures indistinguishable in a
 * report: `core-start-timeout` was printed for a core that never opened its inbound and for one
 * whose tunnel was up and whose controller never answered — opposite faults with the same sentence.
 *
 * So the wait is phased, and a session is **usable** as soon as its data path is up:
 *
 *  - [inboundUp] — the tunnel. Nothing else in this object decides whether a session lives.
 *  - [controllerUp] — a measurement surface. Its absence degrades the URL test, never the route.
 *
 * A core that dies in either later phase still becomes `core-start: exited N`, which is the whole
 * point of the settle window V157 added, and a death after the wait ends is caught by the
 * one-second liveness watchdog the VPN service already runs.
 */
data class StartReadiness(
    /** The local inbound answered a TCP connect. This is the tunnel. */
    val inboundUp: Boolean,
    /** The Clash controller answered `/proxies` with this session's outbound in it. */
    val controllerUp: Boolean,
    /** False for the measurement cores that never dial the controller at all. */
    val controllerAwaited: Boolean,
    /** `inbound` or `controller` — the phase the wait ended in, or `ready` when it did not. */
    val phase: String,
    /** Milliseconds from `open` to this verdict. */
    val elapsedMs: Long
) {
    /** True when the controller was waited for and never answered — a degraded, usable session. */
    val controllerMissing: Boolean get() = controllerAwaited && !controllerUp
}

/**
 * The two questions the start-up loop asks about a child, in a form a JVM test can answer without
 * a device. The production implementation is the loopback socket probe; the tests answer for a
 * child that is only pretending to be a core.
 */
interface ReadinessProbes {
    /** True when the core's local inbound accepts a connection on 127.0.0.1:[port]. */
    fun inbound(port: Int, timeoutMs: Long): Boolean

    /** True when the Clash controller answers `/proxies` with this session's outbound. */
    fun controller(port: Int, secret: String, timeoutMs: Long): Boolean
}

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
     * What the start-up wait observed. `inboundUp = false` before the wait finishes; the session
     * is only handed out once the data path is up, so a caller that holds one may read it.
     */
    @Volatile var readiness: StartReadiness = StartReadiness(
        inboundUp = false, controllerUp = false, controllerAwaited = false,
        phase = "starting", elapsedMs = 0L
    )
        internal set

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
         * MARBLE_SINGBOX_STARTUP_GATE_V162 — how long a live connect waits for the Clash
         * controller once the tunnel is up, and how long `sing-box check` may take.
         *
         * 2.5 s is more than the whole start-up of a healthy pinned core on a phone (hundreds of
         * milliseconds) and far less than the 12 s the inbound has. It is deliberately *not* the
         * budget: a controller that is merely slow must not cost the user a connect, and the
         * controller is not what carries the traffic.
         */
        const val CONTROLLER_TIMEOUT_MS: Long = 2_500L

        /** The `check` spawn's own budget. It validates a document; it does not open a tunnel. */
        const val VALIDATE_TIMEOUT_MS: Long = 8_000L

        /** The production answers to [ReadinessProbes]: one loopback connect, one controller GET. */
        private object SocketProbes : ReadinessProbes {
            override fun inbound(port: Int, timeoutMs: Long): Boolean = listening(port, timeoutMs)
            override fun controller(port: Int, secret: String, timeoutMs: Long): Boolean =
                apiReady(port, secret, timeoutMs)
        }

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
         * Defaults keep every path exactly as strict as it was: MARBLE_SINGBOX_STARTUP_GATE_V162
         * changed what the *live connect* asks for, not what a caller gets when it asks for
         * nothing.
         *
         * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — [settleMs] is the third parameter of the same
         * kind, and it exists because "the port listened" is not the same statement as "the core
         * started". `box.Start()` opens the inbounds at `StartStateStart` and only then walks the
         * outbounds through `StartStatePostStart`, so a core that dies in an outbound's post-start
         * — which is exactly where the Android nil-interface-monitor crash lived — can publish a
         * listening SOCKS port microseconds before it panics. A startup loop that returns on the
         * first successful connect therefore reports success for a process that is already dead.
         *
         * The measurement paths keep the default of `0`: a connect-time grace period would be paid
         * on every server, and they only need the inbound. The self-test in [SingBoxCoreSelfTest]
         * and the live connect (MARBLE_SINGBOX_STARTUP_GATE_V162) both pass a real window: the
         * canary because its whole job is to answer "does this binary survive startup on this
         * device?" once and a false PASS there is worse than a slow one, and the live connect
         * because the controller — which used to be the readiness signal, and which a core creates
         * in its last start-up stage — is no longer waited for to completion.
         *
         * [requireController] is the V162 switch. It defaults to [awaitApi] so every caller that
         * already dialled the controller (the URL test) keeps demanding it, and the live connect
         * can say that the tunnel is what it is waiting for:
         *
         *  - `awaitApi = false` — the controller is never dialled (Real delay, rank, bootstrap).
         *  - `requireController = true` — no controller, no session. The wait runs to the budget,
         *     which is what the URL test has always done.
         *  - `requireController = false` — the controller is waited for inside
         *     [controllerTimeoutMs] and its absence is *reported, not fatal*: the inbound is up
         *     and the child is alive, so the tunnel is up.
         *
         * [validateTimeoutMs] is the V162 budget fix. [startupTimeoutMs] is the window the *run*
         * gets to open its inbound; the `check` spawn used to be paid out of the same window, so a
         * validation that took eight seconds left the child four to start in and then reported the
         * child as broken.
         */
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
            // Null rather than a `SocketProbes` default: the production probe is an object
            // private to this companion, and a public signature should not have to carry it.
            val probe = probes ?: SocketProbes
            val openedNs = System.nanoTime()
            fun elapsed(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - openedNs)
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
                        // MARBLE_SINGBOX_STARTUP_GATE_V162 — its own budget, not the child's:
                        // a validation that takes eight seconds used to leave the run four.
                        if (!validator.waitFor(validateTimeoutMs, TimeUnit.MILLISECONDS)) {
                            error("core-check-timeout: configuration validation did not finish")
                        }
                        check(validator.exitValue() == 0) { "core-config: ${tail(diagnostic)}" }
                    } finally { stop(validator) }
                } finally { diagnostic.delete() }
            }
            checkInterrupted()
            // MARBLE_SINGBOX_STARTUP_GATE_V162 — the window starts when the child does. It used to
            // start before `check`, so a validation that took eight seconds left the child four to
            // open its inbound in and the failure was then reported as the child's. `elapsed()`
            // above still measures the whole call, which is what a report wants to see.
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
                /*
                 * MARBLE_PING_SPEED_V160 — the readiness poll no longer sleeps a flat 60 ms.
                 *
                 * This loop is the last thing standing between "the child is running" and "the
                 * delay can be asked for", and it runs once per measured server. A flat 60 ms
                 * nap between probes handed every URL test up to 60 ms of pure idleness — a
                 * hundred-node subscription gave a whole minute of the sweep to nothing, and the
                 * core is usually ready a few milliseconds after one of those naps began.
                 *
                 * The probes themselves are nearly free on loopback (a port that is not open
                 * yet is refused immediately), so they are taken quickly at first and only back
                 * off when a core genuinely takes its time: 4, 8, 16, 25 ms, and 25 ms from then
                 * on. Nothing about the readiness contract changes — the inbound is still the
                 * first condition and the startup budget still binds. What V162 changes is the
                 * second one: the controller is no longer a condition the inbound has to wait
                 * behind, it is a bounded phase of its own after the tunnel is up.
                 */
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
                // MARBLE_SINGBOX_STARTUP_GATE_V162 — phase 1 is the tunnel. A child that never
                // opened its inbound is the one failure this loop reports as a timeout, and it now
                // says which half of the wait it was, because the remedy for the other half is a
                // working tunnel rather than a new core.
                if (!inboundUp) {
                    error(
                        "core-start-timeout: the local inbound never answered in " +
                            "$startupTimeoutMs ms: ${tail(logFile)}"
                    )
                }
                // Phase 2 — the V157 grace window. A core that dies in an outbound's post-start
                // does so microseconds after the inbound opened, so this is the window that turns
                // "the port answered" into "the core survived".
                settle(child, pump, logFile, settleMs, left())
                if (!awaitApi) {
                    session.readiness = StartReadiness(
                        inboundUp = true, controllerUp = false, controllerAwaited = false,
                        phase = "ready", elapsedMs = elapsed()
                    )
                    return session
                }
                // Phase 3 — the controller. It binds in the core's last start-up stage, so it is
                // the *last* thing to arrive and the first thing a slow core withholds; it is not
                // part of the data path, so a live tunnel is never thrown away because of it.
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

        /**
         * Waits for the Clash controller inside [windowMs], re-checking the child on every probe so
         * a core that dies in a later start-up stage is still reported as the crash it is. Returns
         * whether the controller answered; interrupting throws, and a zero window asks once.
         */
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
                // Probed before the deadline is consulted: a controller that came up during the
                // last sleep still gets to answer.
                if (probes.controller(apiPort, secret, remaining)) return true
                if (remaining <= 0) return false
                Thread.sleep(minOf(pollDelayMs, remaining))
                pollDelayMs = (pollDelayMs * 2L).coerceAtMost(100L)
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
