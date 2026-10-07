package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.XrayDomainStrategies
import org.json.JSONArray
import org.json.JSONObject

// =============================================================================
// MARBLE_CORE_OPTIONS_V211 — the cores' own options, and the last-writer rule.
//
// The report that produced this file said the fragment section "does nothing useful on the
// internet". It was right, and the reason was structural: every option under it was applied by
// whichever automatic policy ran last, so the number on screen was not the number on the wire and
// the control could not fail visibly. This file is the opposite of that arrangement:
//
//   * every function is **pure**: settings in, one JSON object out, no state, no policy calls;
//   * every value is written **verbatim** — the vocabulary is the core's own (see
//     [com.marbleng.app.model.XrayLogLevels] and friends) and a value outside it is normalised at
//     the *reader* in `AppStore`, never rewritten on the way to the wire;
//   * nothing here consults Iran Mode, the intelligence engine, the tuner or a learner. If the
//     user sets `tcpNoDelay`, the config says `tcpNoDelay` — this chapter's whole point;
//   * the **last writer is the user**: the tunnel writer calls these *after* its own liveness and
//     compatibility passes, so the automatic layers fill what the user left alone and never
//     overwrite what they set.
//
// The merge at the bottom is the escape hatch that makes the surface "the most complete the cores
// allow": anything the settings screen does not model (`stats`, `api`, `burstObservatory`,
// `reverse`, a per-outbound `sockopt`, a whole extra `inbounds` entry) can be written as JSON and
// is merged into the final document.
// =============================================================================

/** The result of merging a user's extra JSON into a generated document. */
data class CoreOptionMerge(
    /** Dotted paths that were written, for the log and for Bug Finder. */
    val applied: List<String> = emptyList(),
    /** Non-blank when the user's JSON was refused; the document is then left untouched. */
    val error: String = ""
) {
    val changed: Boolean get() = applied.isNotEmpty()
}

object CoreOptions {
    const val MARKER = "MARBLE_CORE_OPTIONS_V211"

    /** Xray's `fakedns` dest-override entry: required whenever the fake pool is armed. */
    const val FAKEDNS = "fakedns"

    private val SNIFF_PROTOCOLS = listOf("http", "tls", "quic")

    // ── Xray: sniffing ──────────────────────────────────────────────────────────────────────

    /**
     * `sniffing.destOverride`, in the user's order, with `fakedns` appended when the pool is armed.
     *
     * `fakedns` is not a preference: Xray's `FakeDNSPostProcessingStage` refuses a session whose
     * destination came out of the fake pool unless the inbound sniffer carries the entry, so it is
     * added on top of whatever the user chose rather than offered as a switch of its own.
     */
    fun xrayDestOverride(settings: AppSettings, fakeIpArmed: Boolean): JSONArray {
        val chosen = settings.xraySniffDestOverride
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in SNIFF_PROTOCOLS || it == FAKEDNS }
        val ordered = chosen.ifEmpty { SNIFF_PROTOCOLS }
        val withFake = if (fakeIpArmed && FAKEDNS !in ordered) ordered + FAKEDNS else ordered
        return JSONArray(withFake)
    }

    /** The whole `sniffing` block of one Xray inbound. */
    fun xraySniffing(settings: AppSettings, fakeIpArmed: Boolean): JSONObject {
        val sniffing = JSONObject()
            .put("enabled", settings.xraySniffingEnabled)
            .put("routeOnly", settings.xraySniffingRouteOnly)
            .put("destOverride", xrayDestOverride(settings, fakeIpArmed))
        if (settings.xraySniffMetadataOnly) sniffing.put("metadataOnly", true)
        return sniffing
    }

    // ── Xray: inbounds ──────────────────────────────────────────────────────────────────────

    /**
     * The SOCKS inbound's `settings`.
     *
     * `udp` is the user's: turning it off is a real choice on a metered or leak-sensitive
     * connection (it is what QUIC and every UDP-based app ride through the tunnel), and Xray reads
     * it as `"udp": false` — an absent key means the core's default, not the user's decision.
     */
    fun xraySocksSettings(settings: AppSettings): JSONObject = JSONObject()
        .put("udp", settings.xraySocksUdpEnabled)

    // ── Xray: sockopt, routing, policy, log ─────────────────────────────────────────────────

    /**
     * The socket options the user set, and only those.
     *
     * A field left at its neutral value (0 seconds, 0 ms, blank, `AsIs`) is *absent* from the
     * result on purpose: absence is what lets the liveness profile, the address-family plan and
     * the core's own defaults fill in. A value the user did set is written verbatim and survives
     * every later pass.
     */
    fun xrayUserSockopt(settings: AppSettings): JSONObject {
        val sockopt = JSONObject()
        val strategy = XrayDomainStrategies.parse(settings.xraySockoptDomainStrategy)
        if (strategy != "AsIs") sockopt.put("domainStrategy", strategy)
        if (settings.xrayTcpNoDelay) sockopt.put("tcpNoDelay", true)
        if (settings.xrayTcpKeepAliveIntervalSec > 0) {
            sockopt.put("tcpKeepAliveInterval", settings.xrayTcpKeepAliveIntervalSec)
        }
        if (settings.xrayTcpUserTimeoutMs > 0) sockopt.put("tcpUserTimeout", settings.xrayTcpUserTimeoutMs)
        if (settings.xrayTcpCongestion.isNotBlank()) sockopt.put("tcpCongestion", settings.xrayTcpCongestion.trim())
        if (!settings.xrayTcpMptcp) sockopt.put("tcpMptcp", false)
        if (settings.xrayTcpWindowClamp > 0) sockopt.put("tcpWindowClamp", settings.xrayTcpWindowClamp)
        return sockopt
    }

    /**
     * Write the user's socket values onto one `sockopt` object.
     *
     * Called **after** the liveness/compatibility pass, which is the last-writer rule this file's
     * header describes: `tcpKeepAliveInterval = 90` in Settings must survive the Iran liveness
     * profile that would otherwise pick its own number for the same key.
     */
    fun applyXrayUserSockopt(sockopt: JSONObject, settings: AppSettings) {
        val user = xrayUserSockopt(settings)
        val keys = user.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            sockopt.put(key, user.get(key))
        }
    }

    /**
     * Xray `policy.levels.0`, or null when the user left every value at the core's default.
     *
     * These are the four timeouts and the buffer size the core applies to *its own* sessions:
     * `handshake`, `connIdle`, `uplinkOnly`, `downlinkOnly` (seconds) and `bufferSize` (kilobytes).
     * They are the difference between a tunnel that drops an idle flow after 5 minutes and one that
     * holds it, and they were previously unreachable from the app.
     */
    fun xrayPolicy(settings: AppSettings): JSONObject? {
        val level = JSONObject()
        if (settings.xrayPolicyHandshakeSec > 0) level.put("handshake", settings.xrayPolicyHandshakeSec)
        if (settings.xrayPolicyConnIdleSec > 0) level.put("connIdle", settings.xrayPolicyConnIdleSec)
        if (settings.xrayPolicyUplinkOnlySec > 0) level.put("uplinkOnly", settings.xrayPolicyUplinkOnlySec)
        if (settings.xrayPolicyDownlinkOnlySec > 0) level.put("downlinkOnly", settings.xrayPolicyDownlinkOnlySec)
        if (settings.xrayPolicyBufferSizeKb > 0) level.put("bufferSize", settings.xrayPolicyBufferSizeKb)
        if (level.length() == 0) return null
        return JSONObject().put("levels", JSONObject().put("0", level))
    }

    /** Xray `log`, including the DNS log the user may want and the level they chose. */
    fun xrayLog(settings: AppSettings): JSONObject {
        val log = JSONObject().put("loglevel", com.marbleng.app.model.XrayLogLevels.parse(settings.xrayLogLevel))
        if (settings.xrayDnsLog) log.put("dnsLog", true)
        return log
    }

    // ── The escape hatch ────────────────────────────────────────────────────────────────────

    /**
     * Deep-merge [extraJson] into [target].
     *
     * Semantics, stated once because they are the contract: **objects merge key by key,
     * recursively; every other value (array, string, number, boolean, null) replaces what was
     * there.** An array is a whole decision — a routing rule list half-merged with Marble's own
     * would be neither the user's nor the app's — so it is replaced, not appended to.
     *
     * Refusals are total, never partial: a document that does not parse, or that is not an object,
     * or that names a subsystem this app owns (`inbounds`… see [PROTECTED_KEYS]) returns an error
     * and leaves [target] untouched. A half-merged config would be a session that starts and then
     * does something nobody asked for, which is worse than a refusal with a sentence.
     */
    fun merge(target: JSONObject, extraJson: String, protectedKeys: Set<String> = PROTECTED_KEYS): CoreOptionMerge {
        val text = extraJson.trim()
        if (text.isEmpty()) return CoreOptionMerge()
        val parsed = runCatching { JSONObject(text) }.getOrElse {
            return CoreOptionMerge(error = "Not a JSON object: ${it.message ?: "parse error"}")
        }
        val refused = protectedKeys.filter { parsed.has(it) }
        if (refused.isNotEmpty()) {
            return CoreOptionMerge(
                error = "These keys are managed by Marble and cannot be overridden: " +
                    refused.sorted().joinToString(", ")
            )
        }
        val applied = mutableListOf<String>()
        mergeObjects(target, parsed, "", applied)
        return CoreOptionMerge(applied = applied)
    }

    /**
     * The subsystems Marble itself writes and therefore refuses to have overwritten.
     *
     * This is not paternalism about the user's own config — it is the difference between an option
     * and a broken tunnel. Marble replaces `inbounds` wholesale (the local SOCKS listener, the fake
     * DNS pool), `log` (the level and the log file the app reads back), `dns` (the encrypted
     * resolver graph the address-family plan and the sinkhole policy both compute) and, on Xray,
     * `outbounds`/`routing` (the selected node, the chain and the rules the app's own Routing page
     * wrote). Everything else — `stats`, `api`, `metrics`, `observatory`, `reverse`, `policy`,
     * `fakedns`, `experimental` on sing-box, any nested `sockopt` — is fair game.
     */
    val PROTECTED_KEYS: Set<String> = setOf(
        "inbounds", "outbounds", "routing", "dns", "log"
    )

    /**
     * The sing-box half of the same contract — and not the Xray set.
     *
     * sing-box calls its routing block `route`, so [PROTECTED_KEYS] would leave the rules the app
     * computed from the Routing page open to wholesale replacement: the very thing the set exists
     * to prevent. [com.marbleng.app.core.SingBoxConfigBuilder] passes this set for
     * `singBoxExtraJson`. Everything else stays mergeable on purpose — an object patch merges key by
     * key, so `experimental`, `http_clients`, `services`, `endpoints`, `certificate` and every
     * per-outbound field can be extended without deleting the entry the app wrote next to it.
     */
    val SINGBOX_PROTECTED_KEYS: Set<String> = setOf(
        "inbounds", "outbounds", "route", "dns", "log"
    )

    private fun mergeObjects(
        target: JSONObject,
        patch: JSONObject,
        prefix: String,
        applied: MutableList<String>
    ) {
        val keys = patch.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val incoming = patch.opt(key)
            val existing = target.opt(key)
            if (incoming is JSONObject && existing is JSONObject) {
                mergeObjects(existing, incoming, path, applied)
            } else {
                target.put(key, incoming)
                applied += path
            }
        }
    }
}
