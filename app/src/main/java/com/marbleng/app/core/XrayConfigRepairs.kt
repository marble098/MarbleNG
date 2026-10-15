package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

object XrayConfigRepairs {
    data class Report(val document: String, val repairs: List<String>) {
        val changed: Boolean get() = repairs.isNotEmpty()
        val summary: String get() = repairs.joinToString(",")
    }

    internal const val REMOVED_CHAIN_FIELD = "proxySettings"

    private val lastApplied = AtomicReference("")

    internal fun record(repairs: List<String>) {
        lastApplied.set(repairs.joinToString(","))
    }

    fun lastSummary(): String = lastApplied.get()

    private val NUMERIC_FIELDS = mapOf(
        "port" to 65535L,
        "server_port" to 65535L,
        "alterId" to 65535L,
        "alter_id" to 65535L,
        "level" to 255L
    )

    private val VLESS_UNFLATTENABLE_USER_FIELDS = setOf("reverse", "testpre", "testseed", "seed", "experiments")

    fun apply(source: String): Report {
        val trimmed = source.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return Report(source, emptyList())
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return Report(source, emptyList())
        if (NativeSingBoxConfig.isNative(root)) return Report(source, emptyList())
        val outbounds = root.optJSONArray("outbounds") ?: return Report(source, emptyList())

        val repairs = LinkedHashSet<String>()
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            canonicalizeProtocolAlias(outbound, repairs)
            unchainProxySettings(outbound, repairs)
            coerceNumericStrings(outbound, repairs)

            val settings = outbound.optJSONObject("settings")
            when (outbound.optString("protocol").trim().lowercase()) {
                "vless" -> {
                    defaultVlessEncryption(outbound, settings, repairs)
                    liftClassicVnext(outbound, settings, repairs)
                }
                "trojan" -> liftTrojanPassword(outbound, settings, repairs)
                else -> Unit
            }
            nameInferredSecurity(outbound, repairs)
        }

        if (repairs.isEmpty()) return Report(source, emptyList())
        return Report(root.toString(), repairs.toList())
    }

    internal val PROTOCOL_ALIASES: Map<String, String> = mapOf(
        "direct" to "freedom",
        "block" to "blackhole"
    )

    internal fun canonicalizeProtocolAlias(outbound: JSONObject, repairs: MutableSet<String>): Boolean {
        val protocol = outbound.optString("protocol").trim().lowercase()
        val canonical = PROTOCOL_ALIASES[protocol] ?: return false
        outbound.put("protocol", canonical)
        repairs += "protocol-$protocol-to-$canonical"
        return true
    }

    internal fun unchainProxySettings(outbound: JSONObject, repairs: MutableSet<String>): Boolean {
        val proxy = outbound.optJSONObject(REMOVED_CHAIN_FIELD) ?: return false
        val target = proxy.optString("tag").trim()
        outbound.remove(REMOVED_CHAIN_FIELD)
        if (target.isEmpty()) {
            repairs += "proxySettings-dropped"
            return true
        }
        val stream = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = stream.optJSONObject("sockopt")
            ?: JSONObject().also { stream.put("sockopt", it) }
        if (sockopt.optString("dialerProxy").isNotBlank()) {
            repairs += "proxySettings-superseded-by-dialerProxy"
            return true
        }
        sockopt.put("dialerProxy", target)
        repairs += "proxySettings-to-dialerProxy"
        return true
    }

    internal val VLESS_MEANINGLESS_ENCRYPTION = setOf("", "none", "auto", "none/none", "zero", "plain", "default")

    internal fun defaultVlessEncryption(
        outbound: JSONObject,
        settings: JSONObject?,
        repairs: MutableSet<String>
    ) {
        settings ?: return
        var touched = false
        val users = settings.optJSONArray("vnext")?.optJSONObject(0)?.optJSONArray("users")
            ?: settings.optJSONArray("users")
        if (users != null && users.length() > 0) {
            forEachObject(users) { user ->
                if (user.optString("encryption").trim().lowercase() in VLESS_MEANINGLESS_ENCRYPTION &&
                    user.optString("encryption") != "none"
                ) {
                    user.put("encryption", "none")
                    touched = true
                }
            }
        }
        val simplified = settings.optString("address").isNotBlank() || settings.optString("id").isNotBlank()
        if (simplified &&
            settings.optString("encryption").trim().lowercase() in VLESS_MEANINGLESS_ENCRYPTION &&
            settings.optString("encryption") != "none"
        ) {
            settings.put("encryption", "none")
            touched = true
        }
        if (touched) repairs += "vless-encryption-defaulted"
    }

    internal fun liftClassicVnext(outbound: JSONObject, settings: JSONObject?, repairs: MutableSet<String>) {
        settings ?: return
        if (settings.optString("address").isNotBlank()) return
        val vnext = settings.optJSONArray("vnext") ?: return
        if (vnext.length() != 1) return
        val server = vnext.optJSONObject(0) ?: return
        val address = server.optString("address").trim()
        val port = portOf(server)
        if (address.isEmpty() || port == null) return

        val users = server.optJSONArray("users")
        if (users == null || users.length() != 1) return
        val user = users.optJSONObject(0) ?: return
        if (VLESS_UNFLATTENABLE_USER_FIELDS.any { user.has(it) }) return
        val id = user.optString("id").trim()
        if (id.isEmpty()) return

        settings.put("address", address)
        settings.put("port", port)
        settings.put("id", id)
        user.optString("flow").trim().takeIf { it.isNotEmpty() }?.let { settings.put("flow", it) }
        user.optString("encryption").trim().takeIf { it.isNotEmpty() }?.let { settings.put("encryption", it) }
        repairs += "vless-simplified-form"
    }

    internal fun liftTrojanPassword(outbound: JSONObject, settings: JSONObject?, repairs: MutableSet<String>) {
        settings ?: return
        if (settings.optString("password").isNotBlank()) return
        val servers = settings.optJSONArray("servers") ?: return
        if (servers.length() != 1) return
        val password = servers.optJSONObject(0)?.optString("password").orEmpty().trim()
        if (password.isEmpty()) return
        settings.put("password", password)
        repairs += "trojan-password-lifted"
    }

    internal fun nameInferredSecurity(outbound: JSONObject, repairs: MutableSet<String>) {
        val stream = outbound.optJSONObject("streamSettings") ?: return
        if (stream.optString("security").trim().isNotEmpty()) return
        val security = when {
            stream.optJSONObject("realitySettings") != null -> "reality"
            stream.optJSONObject("tlsSettings") != null -> "tls"
            else -> return
        }
        stream.put("security", security)
        repairs += "security-inferred-from-$security"
    }

    internal fun coerceNumericStrings(node: Any?, repairs: MutableSet<String>) =
        coerceNumericStrings(node, repairs, 0)

    private fun coerceNumericStrings(node: Any?, repairs: MutableSet<String>, depth: Int) {
        if (depth > 8) return
        when (node) {
            is JSONObject -> {
                node.keys().asSequence().toList().forEach { key ->
                    val limit = NUMERIC_FIELDS[key]
                    val value = node.opt(key)
                    if (limit != null && value is String) {
                        val numeric = value.trim().toLongOrNull()
                        if (numeric != null && numeric in 0..limit) {
                            node.put(key, numeric.toInt())
                            repairs += "$key-unquoted"
                        }
                    } else {
                        coerceNumericStrings(node.opt(key), repairs, depth + 1)
                    }
                }
            }
            is JSONArray -> for (index in 0 until node.length()) {
                coerceNumericStrings(node.opt(index), repairs, depth + 1)
            }
            else -> Unit
        }
    }

    private fun portOf(source: JSONObject): Int? = when (val raw = source.opt("port")) {
        is Int -> raw.takeIf { it in 1..65535 }
        is Long -> raw.toInt().takeIf { raw in 1L..65535L }
        is String -> raw.trim().toIntOrNull()?.takeIf { it in 1..65535 }
        else -> null
    }

    private fun forEachObject(array: JSONArray, block: (JSONObject) -> Unit) {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(block)
        }
    }
}
