package com.marbleng.app.core

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * MarbleNG Smart Runtime Diagnostics v1
 *
 * Privacy rules:
 * - Never log raw proxy configs.
 * - Never log UUID/password/token/private-key/subscription content.
 * - Only operational metadata, local ports, file sizes, return codes,
 *   exception summaries and HEV traffic counters are recorded.
 */
class RuntimeDiagnostics(
    private val context: Context
) {
    companion object {
        private const val MARKER = "MarbleNG Smart Runtime Diagnostics v1"
        private const val RUNTIME_MAX_BYTES = 2_000_000L
        private const val HEV_MAX_BYTES = 3_000_000L
        private const val MAX_VALUE_CHARS = 3_000
    }

    private val lock = Any()

    private val logDir: File =
        File(context.filesDir, "logs").apply {
            mkdirs()
        }

    val runtimeLog: File =
        File(logDir, "runtime-debug.log")

    val hevLog: File =
        File(logDir, "hev-native.log")

    fun systemSnapshot(): String =
        buildString {
            append("sdk=").append(Build.VERSION.SDK_INT)
            append("; android=").append(Build.VERSION.RELEASE)
            append("; manufacturer=").append(Build.MANUFACTURER)
            append("; model=").append(Build.MODEL)
            append("; abis=").append(Build.SUPPORTED_ABIS.joinToString(","))
            append("; pid=").append(Process.myPid())
        }

    /**
     * Rotate HEV native log only before a new HEV session starts.
     * Logging failures must never break the VPN path.
     */
    fun prepareHevSession() {
        runCatching {
            synchronized(lock) {
                logDir.mkdirs()
                rotateIfNeeded(hevLog, HEV_MAX_BYTES)
                if (!hevLog.exists()) {
                    hevLog.createNewFile()
                }
            }
        }

        event(
            "DIAG",
            "hev-log-prepared",
            "path" to hevLog.absolutePath,
            "bytes" to hevLog.length()
        )
    }

    fun event(
        component: String,
        event: String,
        vararg fields: Pair<String, Any?>
    ) {
        val payload =
            fields.joinToString(" | ") { (key, value) ->
                "$key=${safe(value)}"
            }

        val message =
            if (payload.isBlank()) {
                "$component | $event"
            } else {
                "$component | $event | $payload"
            }

        runCatching {
            Log.i("MarbleNG/$component", message)
        }

        appendLine(message)
    }

    fun error(
        component: String,
        event: String,
        throwable: Throwable,
        vararg fields: Pair<String, Any?>
    ) {
        val stack =
            throwable.stackTrace
                .take(12)
                .joinToString(" <- ") {
                    "${it.className}.${it.methodName}:${it.lineNumber}"
                }

        val allFields =
            fields.toMutableList().apply {
                add("errorClass" to throwable::class.java.name)
                add("errorMessage" to throwable.message)
                add("stack" to stack)
            }

        val payload =
            allFields.joinToString(" | ") { (key, value) ->
                "$key=${safe(value)}"
            }

        val message =
            "$component | $event | $payload"

        runCatching {
            Log.e("MarbleNG/$component", message, throwable)
        }

        appendLine(message)
    }

    /**
     * Short digest for configuration identity without storing configuration
     * contents themselves.
     */
    fun sha256(text: String): String {
        val bytes =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray())

        return bytes
            .joinToString("") { "%02x".format(it) }
            .take(16)
    }

    /**
     * Build the in-app log view from three independent sources:
     *  - runtime-debug.log : Android VPN/TUN/JNI breadcrumbs
     *  - hev-native.log    : native HEV debug logger
     *  - xray.log          : Xray stdout/stderr
     */
    fun bundle(xrayLog: File): String =
        buildString {
            appendLine("=== MarbleNG Diagnostic Bundle ===")
            appendLine(MARKER)
            appendLine(systemSnapshot())
            appendLine("generated=${Instant.now()}")
            appendLine()

            appendLine("=== VPN / TUN / JNI runtime ===")
            appendLine(tail(runtimeLog, 48_000))
            appendLine()

            appendLine("=== HEV native ===")
            appendLine(tail(hevLog, 48_000))
            appendLine()

            appendLine("=== Xray ===")
            appendLine(tail(xrayLog, 48_000))
        }

    private fun appendLine(message: String) {
        runCatching {
            synchronized(lock) {
                logDir.mkdirs()
                rotateIfNeeded(runtimeLog, RUNTIME_MAX_BYTES)

                runtimeLog.appendText(
                    "${Instant.now()} | thread=${Thread.currentThread().name} | $message\n"
                )
            }
        }
    }

    private fun rotateIfNeeded(
        file: File,
        maxBytes: Long
    ) {
        if (!file.exists() || file.length() <= maxBytes) {
            return
        }

        val previous =
            File(file.parentFile, "${file.name}.1")

        runCatching {
            previous.delete()
        }

        if (!file.renameTo(previous)) {
            runCatching {
                file.writeText("")
            }
        }
    }

    private fun tail(
        file: File,
        maxChars: Int
    ): String {
        if (!file.exists()) {
            return "No log file yet: ${file.name}"
        }

        return runCatching {
            val text = file.readText()
            if (text.length <= maxChars) {
                text
            } else {
                "[tail ${maxChars} chars of ${text.length}]\n" +
                    text.takeLast(maxChars)
            }
        }.getOrElse {
            "Could not read ${file.name}: ${it::class.simpleName}: ${it.message}"
        }
    }

    private fun safe(value: Any?): String {
        val text =
            when (value) {
                null -> "null"
                is Boolean,
                is Number -> value.toString()
                else -> value.toString()
            }

        return text
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .let {
                if (it.length <= MAX_VALUE_CHARS) {
                    it
                } else {
                    it.take(MAX_VALUE_CHARS) + "…"
                }
            }
    }
}
