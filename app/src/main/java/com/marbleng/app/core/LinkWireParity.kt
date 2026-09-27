package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compare a share link's current wire configuration with its saved JSON cache. Comparing only the
 * endpoint (as the old startup reconciliation did) missed a dropped REALITY key, bad SNI, XHTTP
 * extra or transport mode on the *same* host:port. Ignore log/routing/inbound metadata and extra
 * user-supplied outbound fields, but require every field the current link reader actually writes.
 */
object LinkWireParity {
    fun matches(stored: ProxyProfile, fromLink: ProxyProfile): Boolean {
        if (!stored.scheme.equals(fromLink.scheme, true) || stored.host != fromLink.host ||
            stored.port != fromLink.port || !stored.transport.equals(fromLink.transport, true) ||
            !stored.security.equals(fromLink.security, true)) return false
        val actual = outbound(stored.configJson) ?: return false
        val expected = outbound(fromLink.configJson) ?: return false
        if (!actual.optString("protocol").equals(expected.optString("protocol"), true) ||
            !includes(actual.optJSONObject("settings"), expected.optJSONObject("settings"))) return false

        val stream = actual.optJSONObject("streamSettings") ?: return expected.optJSONObject("streamSettings") == null
        val desired = expected.optJSONObject("streamSettings") ?: return true
        if (method(stream) != method(desired)) return false
        return desired.keys().asSequence().filterNot { it == "method" || it == "network" || it == "sockopt" }
            .all { key -> includes(stream.opt(key), desired.opt(key)) }
    }

    private fun outbound(raw: String): JSONObject? = runCatching {
        val outbounds = JSONObject(raw).getJSONArray("outbounds")
        (0 until outbounds.length()).mapNotNull { outbounds.optJSONObject(it) }
            .firstOrNull { it.optString("protocol").lowercase() !in
                setOf("freedom", "direct", "blackhole", "block", "dns", "loopback") }
    }.getOrNull()

    private fun method(stream: JSONObject): String = when (stream.optString("method")
        .ifBlank { stream.optString("network", "raw") }.lowercase()) {
        "tcp" -> "raw"
        "ws" -> "websocket"
        "splithttp" -> "xhttp"
        "kcp" -> "mkcp"
        else -> stream.optString("method").ifBlank { stream.optString("network", "raw") }.lowercase()
    }

    /** Expected fields are mandatory; unknown fields in a stored config are left undisturbed. */
    private fun includes(actual: Any?, expected: Any?): Boolean = when (expected) {
        is JSONObject -> actual is JSONObject && expected.keys().asSequence().all { key ->
            actual.has(key) && includes(actual.opt(key), expected.opt(key))
        }
        is JSONArray -> actual is JSONArray && actual.length() == expected.length() &&
            (0 until expected.length()).all { includes(actual.opt(it), expected.opt(it)) }
        is Number -> actual is Number && actual.toDouble() == expected.toDouble()
        else -> actual == expected
    }
}
