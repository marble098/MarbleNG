package com.marbleng.app.core

// MARBLE_ULTIMATE_DIAGNOSTICS_V15

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticEngineStatus(
    val debugEnabled: Boolean,
    val queued: Int,
    val dropped: Long,
    val ringEvents: Int,
    val publicPath: String,
    val writerHealthy: Boolean,
    val writerError: String
)

private sealed class DiagnosticItem {
    data class Line(val text: String) : DiagnosticItem()
    data class Toggle(val enabled: Boolean) : DiagnosticItem()
    data class Export(val label: String, val text: String) : DiagnosticItem()
    data class Barrier(val latch: CountDownLatch) : DiagnosticItem()
    data object Tick : DiagnosticItem()
}

/**
 * MarbleNG Ultimate Runtime Diagnostics v15.
 *
 * Fast-path contract:
 * - callers never write files;
 * - callers never wait for the diagnostics writer;
 * - normal events use ArrayBlockingQueue.offer();
 * - if diagnostics cannot keep up, evidence is dropped instead of slowing the tunnel.
 *
 * Debug Mode mirrors runtime + Xray + HEV-native evidence to:
 * Downloads/marbleng/report as rolling .txt files on Android 10+.
 */
class RuntimeDiagnostics(private val context: Context) {
    companion object {
        private const val MARKER = "MarbleNG Ultimate Runtime Diagnostics v15"
        private const val RUNTIME_MAX_BYTES = 4_000_000L
        private const val EXTERNAL_PART_MAX_BYTES = 12_000_000L
        private const val MAX_VALUE_CHARS = 4_000
        private const val QUEUE_CAPACITY = 4_096
        private const val RING_CAPACITY = 2_000
        private const val MIRROR_CHUNK_BYTES = 256 * 1024
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        private val started = AtomicBoolean(false)
        private val schedulerStarted = AtomicBoolean(false)
        private val crashHandlerInstalled = AtomicBoolean(false)
        private val dropped = AtomicLong(0)
        private val queue = ArrayBlockingQueue<DiagnosticItem>(QUEUE_CAPACITY)
        private val ringLock = Any()
        private val fileLock = Any()
        private val ring = ArrayDeque<String>()
        private val watchedOffsets = HashMap<String, Long>()

        @Volatile private var appContext: Context? = null
        @Volatile private var debugEnabled = false
        @Volatile private var publicPath = "Downloads/marbleng/report"
        @Volatile private var writerHealthy = true
        @Volatile private var writerError = ""

        private var externalWriter: BufferedWriter? = null
        private var externalUri: Uri? = null
        private var externalFile: File? = null
        private var externalBytes = 0L
        private var externalPart = 0
        private var sessionStamp = ""
        private var lastHeartbeatAt = 0L

        fun install(context: Context) {
            appContext = context.applicationContext
            ensureWorkers()
            installCrashHandler(context.applicationContext)
            enqueuePendingCrash(context.applicationContext)
        }

        fun setDebugEnabled(context: Context, enabled: Boolean) {
            install(context)
            if (!queue.offer(DiagnosticItem.Toggle(enabled))) dropped.incrementAndGet()
        }

        fun exportReport(context: Context, label: String, text: String): Boolean {
            install(context)
            val item = DiagnosticItem.Export(
                sanitizeFilePart(label),
                redact(text).take(2_500_000)
            )
            val accepted = queue.offer(item)
            if (!accepted) dropped.incrementAndGet()
            return accepted
        }

        fun reportFolderLabel(): String = publicPath.ifBlank { "Downloads/marbleng/report" }

        fun status(): DiagnosticEngineStatus = DiagnosticEngineStatus(
            debugEnabled = debugEnabled,
            queued = queue.size,
            dropped = dropped.get(),
            ringEvents = synchronized(ringLock) { ring.size },
            publicPath = reportFolderLabel(),
            writerHealthy = writerHealthy,
            writerError = writerError
        )

        fun recentEvents(limit: Int = 500): List<String> = synchronized(ringLock) {
            ring.toList().takeLast(limit.coerceIn(1, RING_CAPACITY))
        }

        fun flush(timeoutMs: Long = 1_500L): Boolean {
            if (!started.get()) return true
            val latch = CountDownLatch(1)
            if (!queue.offer(DiagnosticItem.Barrier(latch))) {
                dropped.incrementAndGet()
                return false
            }
            return runCatching {
                latch.await(timeoutMs.coerceIn(50L, 5_000L), TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
        }

        private fun ensureWorkers() {
            if (started.compareAndSet(false, true)) {
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "marble-diagnostics-writer").apply {
                        priority = Thread.MIN_PRIORITY
                        isDaemon = true
                    }
                }.execute(::writerLoop)
            }

            if (schedulerStarted.compareAndSet(false, true)) {
                Executors.newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "marble-diagnostics-clock").apply {
                        priority = Thread.MIN_PRIORITY
                        isDaemon = true
                    }
                }.scheduleWithFixedDelay(
                    { queue.offer(DiagnosticItem.Tick) },
                    2L,
                    2L,
                    TimeUnit.SECONDS
                )
            }
        }

        private fun writerLoop() {
            while (true) {
                val item = runCatching { queue.take() }.getOrNull() ?: continue
                try {
                    val skipped = dropped.getAndSet(0)
                    if (skipped > 0) {
                        val line = "${Instant.now()} | DIAG | backpressure | dropped=$skipped"
                        appendInternal(line)
                        if (debugEnabled) writeExternal(line)
                    }

                    when (item) {
                        is DiagnosticItem.Line -> {
                            appendInternal(item.text)
                            if (debugEnabled) writeExternal(item.text)
                        }
                        is DiagnosticItem.Toggle -> handleToggle(item.enabled)
                        is DiagnosticItem.Export -> writeStandaloneReport(item.label, item.text)
                        is DiagnosticItem.Barrier -> {
                            runCatching { externalWriter?.flush() }
                            item.latch.countDown()
                        }
                        DiagnosticItem.Tick -> {
                            if (debugEnabled) {
                                mirrorNativeLogs()
                                heartbeatIfDue()
                                runCatching { externalWriter?.flush() }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    writerHealthy = false
                    writerError = "${t::class.java.simpleName}: ${t.message ?: "diagnostic writer failure"}".take(500)
                    runCatching {
                        appendInternal("${Instant.now()} | DIAG | writer-error | ${redact(writerError)}")
                    }
                }
            }
        }

        private fun handleToggle(enabled: Boolean) {
            if (enabled == debugEnabled) return

            if (enabled) {
                debugEnabled = true
                writerHealthy = true
                writerError = ""
                sessionStamp = stamp()
                externalPart = 0
                closeExternal()
                ensureExternalWriter()

                writeExternal("=== MarbleNG DEBUG MODE SESSION ===")
                writeExternal("marker=$MARKER")
                writeExternal("started=${Instant.now()}")
                writeExternal("pid=${Process.myPid()}")
                writeExternal("folder=${reportFolderLabel()}")
                writeExternal("fastPath=bounded non-blocking queue; writer thread only")
                writeExternal("privacy=raw proxy config/credential material redacted")
                writeExternal("=== PRE-DEBUG MEMORY RING ===")
                recentEvents(250).forEach(::writeExternal)
                writeExternal("=== ANDROID HISTORICAL PROCESS EXITS ===")
                historicalExitSnapshot().forEach(::writeExternal)
                writeExternal("=== LIVE STREAM ===")
            } else {
                if (debugEnabled) writeExternal("${Instant.now()} | DIAG | debug-mode-disabled")
                debugEnabled = false
                closeExternal()
            }
        }

        private fun historicalExitSnapshot(): List<String> {
            val ctx = appContext ?: return listOf("app context unavailable")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return listOf("ApplicationExitInfo unavailable below Android 11")
            }

            val am = runCatching { ctx.getSystemService(ActivityManager::class.java) }.getOrNull()
                ?: return listOf("ActivityManager unavailable")
            val exits = runCatching {
                am.getHistoricalProcessExitReasons(ctx.packageName, 0, 16)
            }.getOrDefault(emptyList())

            if (exits.isEmpty()) return listOf("No historical process exits returned")
            return buildList {
                exits.forEachIndexed { index, exit ->
                    val reason = when (exit.reason) {
                        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                        ApplicationExitInfo.REASON_ANR -> "ANR"
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
                        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
                        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
                        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
                        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
                        else -> "REASON_${exit.reason}"
                    }
                    add(
                        "#${index + 1} ${Instant.ofEpochMilli(exit.timestamp)} | " +
                            "process=${redact(exit.processName.orEmpty())} | reason=$reason | " +
                            "status=${exit.status} | importance=${exit.importance} | " +
                            "pssKb=${exit.pss} | rssKb=${exit.rss} | " +
                            "description=${redact(exit.description.orEmpty()).take(1_000)}"
                    )
                }
            }
        }

        private fun heartbeatIfDue() {
            val now = System.currentTimeMillis()
            if (now - lastHeartbeatAt < HEARTBEAT_INTERVAL_MS) return
            lastHeartbeatAt = now

            val ctx = appContext ?: return
            val rt = Runtime.getRuntime()
            val heapUsed = rt.totalMemory() - rt.freeMemory()
            val threadSnapshot = Thread.getAllStackTraces().keys
            val stateCounts = threadSnapshot.groupingBy { it.state.name }.eachCount()
            val am = runCatching { ctx.getSystemService(ActivityManager::class.java) }.getOrNull()
            val pssKb = runCatching {
                am?.getProcessMemoryInfo(intArrayOf(Process.myPid()))?.firstOrNull()?.totalPss
            }.getOrNull() ?: -1

            writeExternal(
                "${Instant.now()} | HEARTBEAT | pid=${Process.myPid()} | " +
                    "cpuMs=${Process.getElapsedCpuTime()} | heapUsed=$heapUsed | " +
                    "heapMax=${rt.maxMemory()} | nativeHeap=${Debug.getNativeHeapAllocatedSize()} | " +
                    "pssKb=$pssKb | threads=${threadSnapshot.size} | states=$stateCounts"
            )
        }

        private fun mirrorNativeLogs() {
            val ctx = appContext ?: return
            val logs = File(ctx.filesDir, "logs")
            mirrorFile(File(logs, "xray.log"), "XRAY_RAW")
            mirrorFile(File(logs, "hev-native.log"), "HEV_NATIVE_RAW")
        }

        private fun mirrorFile(file: File, tag: String) {
            if (!file.isFile) return
            val key = file.absolutePath
            var offset = watchedOffsets[key] ?: 0L
            val length = file.length()
            if (length < offset) offset = 0L
            if (length <= offset) {
                watchedOffsets[key] = offset
                return
            }

            runCatching {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(offset)
                    val remaining = (length - offset).coerceAtMost(MIRROR_CHUNK_BYTES.toLong()).toInt()
                    val buffer = ByteArray(remaining)
                    val read = raf.read(buffer)
                    if (read > 0) {
                        offset += read
                        val chunk = String(buffer, 0, read, Charsets.UTF_8)
                        chunk.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            writeExternal("${Instant.now()} | $tag | ${redact(line).take(8_000)}")
                        }
                    }
                }
                watchedOffsets[key] = offset
            }
        }

        private fun enqueuePendingCrash(context: Context) {
            val file = pendingCrashFile(context)
            if (!file.isFile) return
            val text = runCatching { file.readText().take(120_000) }.getOrDefault("")
            if (text.isNotBlank()) enqueueLine("CRASH | previous-process-fatal | tombstone=${redact(text)}")
            runCatching { file.delete() }
        }

        private fun installCrashHandler(context: Context) {
            if (!crashHandlerInstalled.compareAndSet(false, true)) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                val stack = StringWriter().also { sw ->
                    runCatching { error.printStackTrace(PrintWriter(sw)) }
                }.toString()

                val tombstone = buildString {
                    appendLine("=== MarbleNG FATAL CRASH TOMBSTONE ===")
                    appendLine("generated=${Instant.now()}")
                    appendLine("pid=${Process.myPid()}")
                    appendLine("thread=${thread.name}")
                    appendLine("threadState=${thread.state}")
                    appendLine("error=${error::class.java.name}: ${error.message}")
                    appendLine(redact(stack).take(120_000))
                }

                // Process is already dying: this tiny private write is the only synchronous
                // diagnostics I/O on a caller thread, preserving the crash across process death.
                runCatching {
                    pendingCrashFile(context).apply {
                        parentFile?.mkdirs()
                        writeText(tombstone)
                    }
                }

                enqueueLine("CRASH | uncaught | thread=${thread.name} | ${error::class.java.name}: ${error.message}")

                if (previous != null) previous.uncaughtException(thread, error)
                else Process.killProcess(Process.myPid())
            }
        }

        private fun pendingCrashFile(context: Context) =
            File(context.filesDir, "logs/fatal-crash-pending.txt")

        private fun appendInternal(line: String) {
            val ctx = appContext ?: return
            val file = File(ctx.filesDir, "logs/runtime-debug.log")
            synchronized(fileLock) {
                file.parentFile?.mkdirs()
                rotateInternal(file)
                file.appendText(line + "\n")
            }
        }

        private fun rotateInternal(file: File) {
            if (!file.isFile || file.length() <= RUNTIME_MAX_BYTES) return
            val p1 = File(file.parentFile, "${file.name}.1")
            val p2 = File(file.parentFile, "${file.name}.2")
            val p3 = File(file.parentFile, "${file.name}.3")
            runCatching { p3.delete() }
            if (p2.exists()) runCatching { p2.renameTo(p3) }
            if (p1.exists()) runCatching { p1.renameTo(p2) }
            if (!file.renameTo(p1)) runCatching { file.writeText("") }
        }

        private fun ensureExternalWriter() {
            if (externalWriter != null) return
            val ctx = appContext ?: return
            val suffix = if (externalPart == 0) "" else "-part-${externalPart + 1}"
            val filename = "marbleng-debug-$sessionStamp-pid${Process.myPid()}$suffix.txt"
            val destination = createTextDestination(ctx, filename)
            externalWriter = destination.first
            publicPath = destination.second
            externalBytes = 0L
        }

        private fun writeExternal(line: String) {
            if (!debugEnabled) return
            ensureExternalWriter()
            val clean = redact(line).take(12_000)
            val writer = externalWriter ?: return
            writer.write(clean)
            writer.newLine()
            externalBytes += clean.toByteArray(Charsets.UTF_8).size + 1L

            if (externalBytes >= EXTERNAL_PART_MAX_BYTES) {
                writer.write("${Instant.now()} | DIAG | rolling debug file")
                writer.newLine()
                closeExternal()
                externalPart += 1
                ensureExternalWriter()
            }
        }

        private fun writeStandaloneReport(label: String, text: String) {
            val ctx = appContext ?: return
            val filename = "marbleng-$label-${stamp()}-pid${Process.myPid()}.txt"
            val destination = createTextDestination(ctx, filename)
            destination.first.use { writer ->
                writer.write(text)
                if (!text.endsWith("\n")) writer.newLine()
            }
            publicPath = destination.second
        }

        private fun createTextDestination(context: Context, filename: String): Pair<BufferedWriter, String> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relative = "${Environment.DIRECTORY_DOWNLOADS}/marbleng/report"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore could not create Downloads report")
                val stream = context.contentResolver.openOutputStream(uri, "w")
                    ?: error("MediaStore report stream unavailable")
                externalUri = uri
                externalFile = null
                return BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)) to "$relative/$filename"
            }

            val publicGranted =
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val base = if (publicGranted) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "marbleng/report")
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "marbleng/report")
            }
            base.mkdirs()
            val file = File(base, filename)
            externalUri = null
            externalFile = file
            return file.bufferedWriter(Charsets.UTF_8) to file.absolutePath
        }

        private fun closeExternal() {
            runCatching { externalWriter?.flush() }
            runCatching { externalWriter?.close() }
            externalWriter = null
            externalUri = null
            externalFile = null
            externalBytes = 0L
        }

        private fun enqueueLine(message: String) {
            val line = "${Instant.now()} | thread=${Thread.currentThread().name} | ${redact(message)}"
            synchronized(ringLock) {
                ring.addLast(line)
                while (ring.size > RING_CAPACITY) ring.removeFirst()
            }
            if (!queue.offer(DiagnosticItem.Line(line))) dropped.incrementAndGet()
        }

        private fun stamp(): String =
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

        private fun sanitizeFilePart(value: String): String =
            value.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-')
                .take(50)
                .ifBlank { "report" }

        internal fun redact(value: String): String {
            var out = value
                .replace("\r", "\\r")
                .replace(
                    Regex("(?i)(vless|vmess|trojan|ss|ssr|hysteria2|hy2|tuic|wireguard)://\\S+"),
                    "<redacted-config-uri>"
                )
                .replace(
                    Regex("(?i)(uuid|password|token|private[-_ ]?key|authorization|cookie)\\s*[:=]\\s*[^ |;,&]+")
                ) { m -> "${m.groupValues[1]}=<redacted>" }
                .replace(
                    Regex("(?i)(access_token|token|key|auth)=([^&\\s]+)")
                ) { m -> "${m.groupValues[1]}=<redacted>" }
            if (out.length > 250_000) out = out.take(250_000) + "\n[truncated]"
            return out
        }
    }

    init {
        install(context)
    }

    private val logDir: File = File(context.filesDir, "logs").apply { mkdirs() }
    val runtimeLog: File = File(logDir, "runtime-debug.log")
    val hevLog: File = File(logDir, "hev-native.log")

    fun systemSnapshot(): String {
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionName = packageInfo?.versionName.orEmpty()
        val versionCode = if (packageInfo == null) -1L
        else if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode
        else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return buildString {
            append("sdk=").append(Build.VERSION.SDK_INT)
            append("; android=").append(Build.VERSION.RELEASE)
            append("; manufacturer=").append(Build.MANUFACTURER)
            append("; model=").append(Build.MODEL)
            append("; abis=").append(Build.SUPPORTED_ABIS.joinToString(","))
            append("; pid=").append(Process.myPid())
            append("; app=").append(versionName).append("(").append(versionCode).append(")")
        }
    }

    /** HEV preparation stays private-storage-only and runs on the serialized connection worker. */
    fun prepareHevSession() {
        runCatching {
            synchronized(fileLock) {
                logDir.mkdirs()
                if (hevLog.isFile && hevLog.length() > 3_000_000L) {
                    val previous = File(logDir, "hev-native.log.1")
                    previous.delete()
                    if (!hevLog.renameTo(previous)) hevLog.writeText("")
                }
                if (!hevLog.exists()) hevLog.createNewFile()
            }
        }
        event("DIAG", "hev-log-prepared", "path" to hevLog.absolutePath, "bytes" to hevLog.length())
    }

    fun event(component: String, event: String, vararg fields: Pair<String, Any?>) {
        val payload = fields.joinToString(" | ") { (key, value) -> "$key=${safe(value)}" }
        val message = if (payload.isBlank()) "$component | $event" else "$component | $event | $payload"
        runCatching { Log.i("MarbleNG/$component", message) }
        enqueueLine(message)
    }

    fun error(component: String, event: String, throwable: Throwable, vararg fields: Pair<String, Any?>) {
        val stack = throwable.stackTrace.take(24).joinToString(" <- ") {
            "${it.className}.${it.methodName}:${it.lineNumber}"
        }
        val payload = (fields.toList() + listOf(
            "errorClass" to throwable::class.java.name,
            "errorMessage" to throwable.message,
            "stack" to stack
        )).joinToString(" | ") { (key, value) -> "$key=${safe(value)}" }
        val message = "$component | $event | $payload"
        runCatching { Log.e("MarbleNG/$component", message, throwable) }
        enqueueLine(message)
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    fun bundle(xrayLog: File): String {
        flush()
        val status = status()
        return buildString {
            appendLine("=== MarbleNG Diagnostic Bundle ===")
            appendLine(MARKER)
            appendLine(systemSnapshot())
            appendLine("generated=${Instant.now()}")
            appendLine("debug=${status.debugEnabled} queue=${status.queued} dropped=${status.dropped} writerHealthy=${status.writerHealthy} publicPath=${status.publicPath}")
            if (status.writerError.isNotBlank()) appendLine("writerError=${status.writerError}")
            appendLine()
            appendLine("=== IN-MEMORY RING ===")
            recentEvents(500).forEach(::appendLine)
            appendLine()
            appendLine("=== VPN / TUN / APP RUNTIME ===")
            appendLine(tail(runtimeLog, 160_000))
            appendLine()
            appendLine("=== HEV NATIVE ===")
            appendLine(tail(hevLog, 100_000))
            appendLine()
            appendLine("=== XRAY ===")
            appendLine(tail(xrayLog, 100_000))
        }
    }

    fun exportReport(label: String, text: String): Boolean =
        RuntimeDiagnostics.exportReport(context, label, text)

    private fun tail(file: File, maxBytes: Int): String {
        if (!file.isFile) return "No log file yet: ${file.name}"
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val read = raf.length().coerceAtMost(maxBytes.toLong()).toInt()
                raf.seek((raf.length() - read).coerceAtLeast(0L))
                val bytes = ByteArray(read)
                raf.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrElse { "Could not read ${file.name}: ${it::class.simpleName}: ${it.message}" }
    }

    private fun safe(value: Any?): String {
        val text = when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            else -> value.toString()
        }
        return redact(text).let { if (it.length <= MAX_VALUE_CHARS) it else it.take(MAX_VALUE_CHARS) + "…" }
    }
}
