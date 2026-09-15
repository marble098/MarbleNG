package com.marbleng.app.core

/**
 * MARBLE_SINGBOX_START_FAILURE
 *
 * Classifies a sing-box start failure into actionable diagnosis and remediation.
 * When a profile cannot build a proxy outbound or the core cannot start, it is classified
 * honestly on the diagnostic plane (in BugFinder, blocked state, and runtime events).
 */
object SingBoxStartFailure {

    enum class Kind(val label: String) {
        NO_PROXY_OUTBOUND("No proxy outbound"),
        CONFIG_REJECTED("Configuration rejected by sing-box"),
        PORT_IN_USE("Local port in use"),
        CORE_CRASH("Core crashed on start")
    }

    data class Fault(
        val kind: Kind,
        val cause: String,
        val headline: String,
        val remediation: String
    )

    private val ADDRESS_IN_USE = Regex("address already in use|bind: .*in use|core-port:", RegexOption.IGNORE_CASE)

    fun isNoProxyOutbound(reason: String): Boolean =
        reason.contains("no proxy outbound", ignoreCase = true)

    fun isPortInUse(reason: String): Boolean =
        ADDRESS_IN_USE.containsMatchIn(reason)

    fun isCoreCrash(reason: String): Boolean =
        reason.contains("runtime: out of memory", ignoreCase = true) ||
            reason.contains("fatal error:", ignoreCase = true) ||
            reason.contains("SIGSEGV", ignoreCase = true) ||
            reason.contains("SIGBUS", ignoreCase = true)

    fun causeLine(reason: String): String {
        val segments = reason.split('|', '\n').map { it.trim() }.filter { it.isNotBlank() }
        return segments.lastOrNull { segment ->
            segment.contains("no proxy outbound", ignoreCase = true) ||
                segment.contains("address already in use", ignoreCase = true) ||
                segment.contains("bind:", ignoreCase = true) ||
                segment.contains("runtime: out of memory", ignoreCase = true) ||
                segment.contains("fatal error:", ignoreCase = true) ||
                segment.contains("failed", ignoreCase = true) ||
                segment.contains("FATAL", ignoreCase = true) ||
                segment.contains("panic", ignoreCase = true)
        }.orEmpty()
    }

    fun classify(lastStartError: String, runtimeLog: String = ""): Fault? {
        val reason = lastStartError.ifBlank { lastStartResultReason(runtimeLog) }
        if (reason.isBlank()) return null
        val cause = causeLine(reason).ifBlank { reason.take(300) }

        if (isCoreCrash(reason)) {
            return Fault(
                kind = Kind.CORE_CRASH,
                cause = cause,
                headline = "sing-box core crashed while starting: $cause",
                remediation = "Core crashed or memory exhausted; check device resources or update core"
            )
        }
        if (isNoProxyOutbound(reason)) {
            return Fault(
                kind = Kind.NO_PROXY_OUTBOUND,
                cause = cause,
                headline = "sing-box refused the profile: no proxy outbound could be constructed for this configuration",
                remediation = "Verify the profile contains at least one valid outbound or update MarbleNG to auto-map serverless profiles"
            )
        }
        if (isPortInUse(reason)) {
            return Fault(
                kind = Kind.PORT_IN_USE,
                cause = cause,
                headline = "sing-box could not bind its local listener: $cause",
                remediation = "Another process holds the local port; run Safe runtime reset or adjust port settings"
            )
        }
        if (reason.contains("failed", ignoreCase = true) ||
            reason.contains("panic", ignoreCase = true) ||
            reason.contains("error", ignoreCase = true) ||
            reason.contains("fatal", ignoreCase = true)
        ) {
            return Fault(
                kind = Kind.CONFIG_REJECTED,
                cause = cause,
                headline = "sing-box core start-up failed: $cause",
                remediation = "Inspect SINGBOX start-result in the connection timeline or verify profile structure"
            )
        }
        return null
    }

    internal fun lastStartResultReason(runtimeLog: String): String {
        if (runtimeLog.isBlank()) return ""
        val line = runtimeLog.lineSequence()
            .filter { it.contains("SINGBOX | start-result") }
            .lastOrNull() ?: return ""
        if (!line.contains("ok=false")) return ""
        return line.substringAfter("reason=", "").trim()
    }

    fun faultClass(reason: String, default: String): String =
        classify(reason)?.let { fault ->
            when (fault.kind) {
                Kind.NO_PROXY_OUTBOUND, Kind.CONFIG_REJECTED -> "Configuration rejected by sing-box"
                Kind.PORT_IN_USE -> "Local port in use"
                Kind.CORE_CRASH -> "Core crashed on start"
            }
        } ?: default
}
