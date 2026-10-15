package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject

object RealityHandshakePolicy {
    const val SINGBOX_MARKER = "reality verification failed"

    private val XRAY_MARKERS = listOf(
        "reality: received real certificate",
        "reality: processed invalid connection"
    )

    private const val FAIL_THRESHOLD = 3

    data class Evidence(val singboxFailures: Int, val xrayFallbacks: Int) {
        val total: Int get() = singboxFailures + xrayFallbacks
        val empty: Boolean get() = total == 0
    }

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

    fun lacksMlkemShare(fingerprint: String): Boolean {
        val fp = fingerprint.trim().lowercase()
        if (fp.isEmpty()) return false
        if (fp == "chrome" || fp.startsWith("chrome_")) return false
        if (fp == "random" || fp == "randomized") return false
        return true
    }

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
