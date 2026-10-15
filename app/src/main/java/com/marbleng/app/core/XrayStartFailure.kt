package com.marbleng.app.core

object XrayStartFailure {
    enum class Kind(val label: String) {
        DUPLICATE_OUTBOUND_TAG("Duplicate outbound tag"),
        CONFIG_REJECTED("Configuration rejected by the core"),
        PORT_IN_USE("Local port in use")
    }

    data class Fault(
        val kind: Kind,
        val cause: String,
        val headline: String,
        val remediation: String
    )

    private val EXISTING_TAG = Regex("existing tag found:\\s*([^\\s|\"]+)", RegexOption.IGNORE_CASE)
    private val EXIT_CODE = Regex("exited with code\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val ADDRESS_IN_USE = Regex("address already in use|bind: .*in use", RegexOption.IGNORE_CASE)

    fun isConfigLoadFailure(reason: String): Boolean {
        val text = reason.lowercase()
        return "failed to start" in text ||
            "failed to create server" in text ||
            "existing tag found" in text ||
            "failed to build config" in text ||
            "failed to load config" in text ||
            "failed to parse" in text ||
            "invalid character" in text ||
            "unknown field" in text
    }

    fun causeLine(reason: String): String {
        val segments = reason.split('|', '\n').map { it.trim() }.filter { it.isNotBlank() }
        return segments.lastOrNull { segment ->
            segment.contains("Failed to start", ignoreCase = true) ||
                segment.contains("existing tag found", ignoreCase = true) ||
                segment.contains("address already in use", ignoreCase = true) ||
                segment.contains("[Error]", ignoreCase = true)
        }.orEmpty()
    }

    fun classify(lastStartError: String, runtimeLog: String = ""): Fault? {
        val reason = lastStartError.ifBlank { lastStartResultReason(runtimeLog) }
        if (reason.isBlank()) return null
        val cause = causeLine(reason).ifBlank { reason.take(300) }
        val code = EXIT_CODE.find(reason)?.groupValues?.get(1).orEmpty()
        val codeLabel = if (code.isBlank()) "" else " (exit code $code)"
        EXISTING_TAG.find(reason)?.groupValues?.get(1)?.let { tag ->
            return Fault(
                kind = Kind.DUPLICATE_OUTBOUND_TAG,
                cause = cause,
                headline = "Xray refused the generated configuration$codeLabel: two outbounds are " +
                    "tagged \"$tag\" — the imported profile already defines the outbound MarbleNG " +
                    "adds under that name. This is a document error, not a server verdict; the " +
                    "kill switch stayed closed and no server was contacted",
                remediation = "Update MarbleNG: the hardener now renames an imported \"$tag\" to " +
                    "\"${XrayConfigHardener.importedAliasFor(tag)}\" before adding its own. Until " +
                    "then rename that outbound tag in the profile JSON, or select a profile that " +
                    "does not define \"$tag\""
            )
        }
        if (ADDRESS_IN_USE.containsMatchIn(reason)) {
            return Fault(
                kind = Kind.PORT_IN_USE,
                cause = cause,
                headline = "Xray could not bind its local listener$codeLabel: $cause",
                remediation = "Another process holds the local SOCKS/HTTP port; run Safe runtime " +
                    "reset or change the port in Settings"
            )
        }
        if (isConfigLoadFailure(reason)) {
            return Fault(
                kind = Kind.CONFIG_REJECTED,
                cause = cause,
                headline = "Xray refused the generated configuration$codeLabel: $cause",
                remediation = "Inspect XRAY start-result in the timeline; a load refusal is the same " +
                    "for every retry of this profile, so retrying does not help — repair the profile " +
                    "JSON or report the cause line"
            )
        }
        return null
    }

    internal fun lastStartResultReason(runtimeLog: String): String {
        if (runtimeLog.isBlank()) return ""
        val line = runtimeLog.lineSequence()
            .filter { it.contains("XRAY | start-result") }
            .lastOrNull() ?: return ""
        if (!line.contains("ok=false")) return ""
        return line.substringAfter("reason=", "").trim()
    }

    fun faultClass(reason: String, default: String): String =
        classify(reason)?.let { fault ->
            when (fault.kind) {
                Kind.DUPLICATE_OUTBOUND_TAG, Kind.CONFIG_REJECTED -> "Configuration rejected by Xray"
                Kind.PORT_IN_USE -> "Local port in use"
            }
        } ?: default
}
