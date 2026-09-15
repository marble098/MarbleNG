package com.marbleng.app.core

// MARBLE_RESERVED_TAG_COLLISION_V183
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// A config-load refusal from Xray is a fact about the document, never about the server
// ─────────────────────────────────────────────────────────────────────────────────────────────
//
// The report that motivated this showed two profiles (`Serverless-v50-fragA` / `-fragB`) failing
// identically within nine seconds:
//
// ```
// XRAY | start-result | ok=false | phase=failed | reason=Xray exited with code 23:
//   … [Info] app/dns: DNS: created DOH client for https://1.1.1.1/dns-query …
//   Failed to start: main: failed to create server > app/proxyman/outbound: existing tag found: block
// APP | state | from=CONNECTING | to=BLOCKED | detail=Kill switch active • Xray exited with code 23 …
// ```
//
// The hardener appended its own `block` outbound to a document that already carried one (the
// upstream XTLS serverless shape ships `block-out`, `dns-out`, `tcp-direct-out`; many hand-edited
// derivatives rename them to the bare `block`/`direct`). The core's outbound manager refuses a
// duplicate tag at load, so the start died before a single packet was sent — and the UI said
// "Kill switch active", the Bug Finder said nothing, and the same user would retry every profile.
//
// This object turns the core's stderr into three things the rest of the app can act on: a stable
// *kind*, a one-line headline, and a remediation. `XrayConfigHardener` prevents the collision
// itself (`renameReservedImportedTags`); this exists so a future load refusal of any kind is
// reported for what it is.
object XrayStartFailure {

    enum class Kind(val label: String) {
        /** `app/proxyman/outbound: existing tag found: <tag>` — two outbounds share a tag. */
        DUPLICATE_OUTBOUND_TAG("Duplicate outbound tag"),
        /** Any other `Failed to start:` / config-load error. */
        CONFIG_REJECTED("Configuration rejected by the core"),
        /** The local SOCKS/HTTP listener could not bind. */
        PORT_IN_USE("Local port in use")
    }

    data class Fault(
        val kind: Kind,
        /** The single line the core printed that names the cause, trimmed of `[Info]` noise. */
        val cause: String,
        val headline: String,
        val remediation: String
    )

    private val EXISTING_TAG = Regex("existing tag found:\\s*([^\\s|\"]+)", RegexOption.IGNORE_CASE)
    private val EXIT_CODE = Regex("exited with code\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val ADDRESS_IN_USE = Regex("address already in use|bind: .*in use", RegexOption.IGNORE_CASE)

    /** True when [reason] is a load-time refusal rather than a runtime death or a stop. */
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

    /** The core line that names the cause, or "" when [reason] carries none. */
    fun causeLine(reason: String): String {
        val segments = reason.split('|', '\n').map { it.trim() }.filter { it.isNotBlank() }
        return segments.lastOrNull { segment ->
            segment.contains("Failed to start", ignoreCase = true) ||
                segment.contains("existing tag found", ignoreCase = true) ||
                segment.contains("address already in use", ignoreCase = true) ||
                segment.contains("[Error]", ignoreCase = true)
        }.orEmpty()
    }

    /**
     * Classify the manager's `lastStartError` (or the last `XRAY | start-result` line in
     * [runtimeLog] when the in-memory reason is empty because the app process restarted).
     * Returns `null` when neither describes a load failure.
     */
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

    /**
     * The `reason=` field of the most recent `XRAY | start-result` runtime line — but only when
     * that latest start *failed*. A failure followed by a successful start is history, not a
     * current fault, and must not keep a repaired profile in FAIL.
     */
    internal fun lastStartResultReason(runtimeLog: String): String {
        if (runtimeLog.isBlank()) return ""
        val line = runtimeLog.lineSequence()
            .filter { it.contains("XRAY | start-result") }
            .lastOrNull() ?: return ""
        if (!line.contains("ok=false")) return ""
        return line.substringAfter("reason=", "").trim()
    }

    /** The BLOCKED-state label for [reason]; falls back to the caller's default wording. */
    fun faultClass(reason: String, default: String): String =
        classify(reason)?.let { fault ->
            when (fault.kind) {
                Kind.DUPLICATE_OUTBOUND_TAG, Kind.CONFIG_REJECTED -> "Configuration rejected by Xray"
                Kind.PORT_IN_USE -> "Local port in use"
            }
        } ?: default
}
