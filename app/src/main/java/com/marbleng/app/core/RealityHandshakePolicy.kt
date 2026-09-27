package com.marbleng.app.core

// MARBLE_REALITY_MLKEM_HANDSHAKE_V194
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// Why `reality verification failed` is a handshake-shape fault, not a server-down fault
// ─────────────────────────────────────────────────────────────────────────────────────────────
//
// Since Xray-core v26.9.8 (`github.com/xtls/reality@8cdf7bf9`) the REALITY *server* rejects any
// ClientHello that does not carry `X25519MLKEM768 before optional X25519`, and silently falls
// back to the decoy site instead. The client then fails session verification — on sing-box with
// the exact line `reality verification failed` (its `common/tls/reality_client.go`; Xray never
// prints this string), on Xray with `REALITY: received real certificate (...)`.
//
// The pinned sing-box extended core *strips* that hybrid share from every uTLS handshake unless
// its reality block sets `support_x25519mlkem768` — and neither its link parser nor MarbleNG's
// translator used to set it. The result was a deterministic, per-destination-independent failure
// (55/55 attempts, zero success) for a node whose public key, short ID, SNI and transport were
// all byte-correct — which is exactly why it connected in v2rayNG and nowhere else. The wire fix
// lives in [SingBoxTransportTranslator] (emit the flag) and [SingBoxConfigBuilder] (REALITY links
// skip the core's link parser, which cannot emit it).
//
// This object is the diagnostic half: a pure, JVM-testable reader that counts those rejections in
// retained core logs and turns them into one Bug Finder check with the actual remedy, so a
// residual case — a non-chrome fingerprint whose uTLS preset carries no hybrid share, a pasted
// native sing-box document without the flag, or genuinely mismatched keys — is named instead of
// being retried blindly and misreported as a dead server.

import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject

object RealityHandshakePolicy {

    /** The exact sing-box client line; no benign shape of it exists. */
    const val SINGBOX_MARKER = "reality verification failed"

    /** Xray's client-side equivalents: the handshake fell back to a real (unverifiable) peer. */
    private val XRAY_MARKERS = listOf(
        "reality: received real certificate",
        "reality: processed invalid connection"
    )

    /** One Bug Finder check per scan at most; a lone line warns, a burst fails. */
    private const val FAIL_THRESHOLD = 3

    data class Evidence(val singboxFailures: Int, val xrayFallbacks: Int) {
        val total: Int get() = singboxFailures + xrayFallbacks
        val empty: Boolean get() = total == 0
    }

    /** Count both markers in retained log text. Never throws. */
    fun scan(text: String): Evidence {
        if (text.isBlank()) return Evidence(0, 0)
        var singbox = 0
        var xray = 0
        text.lineSequence().forEach { line ->
            if (isSingboxVerificationFailure(line)) singbox++
            else if (isXrayRealityFallback(line)) xray++
        }
        return Evidence(singbox, xray)
    }

    fun isSingboxVerificationFailure(line: String): Boolean =
        line.contains(SINGBOX_MARKER, ignoreCase = true)

    fun isXrayRealityFallback(line: String): Boolean {
        val lower = line.lowercase()
        return XRAY_MARKERS.any { it in lower }
    }

    /**
     * True when [fingerprint] can never satisfy a current REALITY server on the sing-box engine.
     *
     * In the pinned metacubex/utls v1.8.7 only the chrome presets carry the X25519MLKEM768 share
     * (HelloChrome_Auto = HelloChrome_133, plus HelloChrome_131); firefox, safari, edge, ios,
     * android, 360 and qq carry none, so the compat flag has nothing to keep for them. An empty
     * fingerprint is chrome on the pinned fork (`case "chrome", ""`), and `random`/`randomized`
     * may draw chrome — both are flaky rather than certainly broken, so neither counts as lacking.
     */
    fun lacksMlkemShare(fingerprint: String): Boolean {
        val fp = fingerprint.trim().lowercase()
        if (fp.isEmpty()) return false
        if (fp == "chrome" || fp.startsWith("chrome_")) return false
        if (fp == "random" || fp == "randomized") return false
        return true
    }

    /** The active profile's REALITY/TLS fingerprint, `""` when none is stated or readable. */
    fun fingerprintOf(profile: ProxyProfile?): String {
        val raw = profile?.configJson.orEmpty()
        if (raw.isBlank()) return ""
        return runCatching {
            val outbounds = JSONObject(raw).optJSONArray("outbounds") ?: return ""
            for (i in 0 until outbounds.length()) {
                val stream = outbounds.optJSONObject(i)?.optJSONObject("streamSettings") ?: continue
                val key = if (stream.optString("security").equals("reality", ignoreCase = true)) {
                    "realitySettings"
                } else {
                    "tlsSettings"
                }
                stream.optJSONObject(key)?.optString("fingerprint").orEmpty()
                    .takeIf { it.isNotBlank() }?.let { return it }
            }
            ""
        }.getOrDefault("")
    }

    /**
     * The Bug Finder check for [evidence], or null when the logs carry no REALITY rejection.
     *
     * The remedy depends on the active profile's fingerprint: a non-chrome fingerprint is itself
     * the cause on sing-box (no flag can conjure a share its preset never emits), while a
     * chrome-capable handshake that still fails points at genuinely mismatched keys — or at a
     * pasted native sing-box document, which MarbleNG passes through untouched and which therefore
     * carries no compat flag of its own.
     */
    fun check(evidence: Evidence, active: ProxyProfile?): BugCheck? {
        if (evidence.empty) return null
        val severity = if (evidence.total >= FAIL_THRESHOLD) BugSeverity.FAIL else BugSeverity.WARN
        val fingerprint = fingerprintOf(active)
        val detail = if (lacksMlkemShare(fingerprint)) {
            "${evidence.total} REALITY handshake rejection(s) in the retained core log " +
                "(sing-box: ${evidence.singboxFailures}, xray: ${evidence.xrayFallbacks}). " +
                "The server requires the X25519MLKEM768 ClientHello share (Xray >= v26.9.8), " +
                "which the pinned uTLS library only carries for chrome-family fingerprints — " +
                "`$fingerprint` cannot satisfy it on the sing-box engine. " +
                "The keys are not the problem: the handshake bytes are."
        } else {
            "${evidence.total} REALITY handshake rejection(s) in the retained core log " +
                "(sing-box: ${evidence.singboxFailures}, xray: ${evidence.xrayFallbacks}). " +
                "This build emits the X25519MLKEM768 share current Xray servers require on " +
                "every translated outbound, so a rejection now usually means the server's keys " +
                "or short ID genuinely mismatch — re-import the current share link and compare " +
                "every REALITY field. A pasted native sing-box document carries no such flag: " +
                "add `\"support_x25519mlkem768\": true` to its reality block, or run the node " +
                "on the Xray core."
        }
        val action = if (lacksMlkemShare(fingerprint)) {
            "Run this node on the Xray core, or switch its fingerprint to chrome"
        } else {
            "Re-import the current share link; run the node on the Xray core if it persists"
        }
        return BugCheck("REALITY handshake failures", severity, detail, action)
    }
}
