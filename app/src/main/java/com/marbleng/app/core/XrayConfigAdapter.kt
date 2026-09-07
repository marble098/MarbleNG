package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject

/** Reverse side of the runtime bridge. Common native sing-box outbounds are translated for Xray
 * without mutating the stored profile. Core-exclusive protocols/options fail explicitly; changing
 * a protocol, disabling certificate checks or dropping a detour is never a conversion strategy. */
object XrayConfigAdapter {
    fun document(source: String): JSONObject {
        val parsed = JSONObject(source)
        if (!NativeSingBoxConfig.isNative(parsed)) return parsed
        val root = NativeSingBoxConfig.root(parsed)
        val entry = NativeSingBoxConfig.entry(root)
        if (root.has("endpoints") || root.has("certificate") || root.has("certificate_providers")) {
            fail("resources", "native endpoint/certificate resources require sing-box")
        }
        val byTag = NativeSingBoxConfig.objects(root.getJSONArray("outbounds")).associateBy { it.optString("tag") }
        val ordered = mutableListOf<JSONObject>()
        val visiting = mutableSetOf<String>()
        var hop: JSONObject? = entry
        while (hop != null) {
            val current = hop
            val tag = current.optString("tag").ifBlank { "proxy" }
            if (!visiting.add(tag) || visiting.size > 16) fail("outbounds.detour", "cyclic or excessively deep chain")
            ordered += outbound(current).put("tag", tag)
            val next = current.optString("detour")
            hop = if (next.isBlank()) null else byTag[next] ?: fail("outbounds.detour", "missing hop '$next'")
        }
        return JSONObject().put("outbounds", JSONArray(ordered))
    }

    private fun outbound(source: JSONObject): JSONObject {
        val type = source.getString("type")
        if (source.optJSONObject("multiplex")?.optBoolean("enabled") == true) fail("multiplex", "sing-box smux/h2mux/yamux is not Xray Mux.Cool")
        if (source.has("domain_resolver")) fail("domain_resolver", "a native per-dial resolver requires sing-box")
        val settings = JSONObject()
        val result = JSONObject().put("protocol", when (type) {
            "direct" -> "freedom"
            "block" -> "blackhole"
            "hysteria2" -> "hysteria"
            "vless", "vmess", "trojan", "shadowsocks", "socks", "http" -> type
            else -> fail("outbounds.type", "'$type' is not implemented by the pinned Xray core; select sing-box")
        }).put("settings", settings)
        val stream = JSONObject()
        if (type !in setOf("direct", "block")) {
            settings.put("address", source.getString("server")).put("port", source.getInt("server_port"))
        }
        when (type) {
            "vless", "vmess" -> {
                settings.put("id", source.getString("uuid"))
                if (type == "vless") {
                    settings.put("encryption", source.optString("encryption").ifBlank { "none" })
                    copy(source, settings, "flow", "flow")
                } else {
                    copy(source, settings, "security", "security")
                    copy(source, settings, "alter_id", "alterId")
                    copy(source, settings, "global_padding", "globalPadding")
                    copy(source, settings, "authenticated_length", "authenticatedLength")
                }
                if (source.has("packet_encoding")) settings.put("packetEncoding", source.optString("packet_encoding").ifBlank { "none" })
            }
            "trojan", "shadowsocks" -> {
                settings.put("password", source.getString("password"))
                if (type == "shadowsocks") {
                    settings.put("method", source.getString("method"))
                    if (source.optString("plugin").isNotBlank()) fail("plugin", "SIP003 plugins require sing-box")
                }
            }
            "socks", "http" -> {
                if (type == "socks" && source.optString("version", "5") !in setOf("", "5")) fail("version", "only SOCKS5 is supported by Xray")
                if (source.has("username") || source.has("password")) {
                    settings.put("user", source.optString("username")).put("pass", source.optString("password"))
                }
            }
            "hysteria2" -> {
                settings.put("version", 2)
                stream.put("network", "hysteria")
                val hysteria = JSONObject().put("version", 2).put("auth", source.optString("password"))
                if (source.has("up_mbps")) hysteria.put("up", source.get("up_mbps").toString())
                if (source.has("down_mbps")) hysteria.put("down", source.get("down_mbps").toString())
                stream.put("hysteriaSettings", hysteria)
                source.optJSONObject("obfs")?.let { obfs ->
                    if (obfs.optString("type") != "salamander") fail("obfs.type", "unsupported Hysteria2 mask")
                    stream.put("finalmask", JSONObject().put("udp", JSONArray().put(JSONObject()
                        .put("type", "salamander").put("settings", JSONObject().put("password", obfs.getString("password"))))))
                }
            }
        }
        source.optJSONObject("transport")?.let { transport(it, stream) }
        source.optJSONObject("tls")?.let { tls(it, stream) }
        val detour = source.optString("detour")
        if (detour.isNotBlank() || source.has("tcp_fast_open")) {
            val sockopt = JSONObject()
            if (detour.isNotBlank()) sockopt.put("dialerProxy", detour)
            copy(source, sockopt, "tcp_fast_open", "tcpFastOpen")
            stream.put("sockopt", sockopt)
        }
        // Reject fields with unimplemented semantics, rather than creating a config that only
        // looks equivalent. This list is intentionally smaller than the two cores' union.
        val allowed = setOf("type", "tag", "server", "server_port", "uuid", "flow", "encryption",
            "security", "alter_id", "global_padding", "authenticated_length", "packet_encoding",
            "password", "username", "method", "version", "plugin", "multiplex", "tls", "transport",
            "detour", "tcp_fast_open", "up_mbps", "down_mbps", "obfs")
        source.keys().forEach { if (it !in allowed) fail("outbounds.$it", "no lossless Xray mapping is implemented") }
        if (stream.length() > 0) result.put("streamSettings", stream)
        return result
    }

    private fun transport(source: JSONObject, stream: JSONObject) {
        val type = source.getString("type")
        val clean = JSONObject(source.toString()).apply { remove("type") }
        val method = if (type == "mkcp") "kcp" else type
        stream.put("network", method)
        fun invert(fields: Map<String, String>) = fields.entries.associate { it.value to it.key }
        val key: String
        val options: JSONObject
        when (type) {
            "xhttp" -> {
                key = "xhttpSettings"
                options = SingBoxTransportTranslator.map(clean,
                    invert(SingBoxTransportTranslator.xhttpFields), "transport", setOf("mode", "xmux", "download"))
                copy(clean, options, "mode", "mode")
                clean.optJSONObject("xmux")?.let { options.put("xmux", SingBoxTransportTranslator.map(it,
                    invert(SingBoxTransportTranslator.xmuxFields), "transport.xmux")) }
                clean.optJSONObject("download")?.let { download ->
                    val downStream = JSONObject().put("network", "xhttp")
                    copy(download, downStream, "server", "address")
                    copy(download, downStream, "server_port", "port")
                    download.optJSONObject("tls")?.let { tls(it, downStream) }
                    if (download.optString("detour").isNotBlank()) fail("transport.download.detour", "download detour requires sing-box")
                    val downOptions = JSONObject(download.toString()).apply {
                        listOf("server", "server_port", "tls", "detour").forEach(::remove)
                        put("type", "xhttp")
                    }
                    transport(downOptions, downStream)
                    options.put("downloadSettings", downStream)
                }
            }
            "mkcp" -> {
                key = "kcpSettings"
                options = SingBoxTransportTranslator.map(clean, invert(SingBoxTransportTranslator.kcpFields), "transport", setOf("header_type"))
                if (clean.has("header_type")) options.put("header", JSONObject().put("type", clean.get("header_type")))
            }
            "ws" -> {
                key = "wsSettings"
                options = SingBoxTransportTranslator.map(clean, mapOf("path" to "path", "headers" to "headers",
                    "max_early_data" to "maxEarlyData", "early_data_header_name" to "earlyDataHeaderName"), "transport")
            }
            "grpc" -> {
                key = "grpcSettings"
                options = SingBoxTransportTranslator.map(clean, mapOf("service_name" to "serviceName",
                    "permit_without_stream" to "permit_without_stream"), "transport")
            }
            "http", "quic" -> fail("transport.type", "the pinned Xray removed this legacy transport; select sing-box rather than changing the server's wire protocol")
            "httpupgrade" -> {
                key = "httpupgradeSettings"
                options = SingBoxTransportTranslator.map(clean, mapOf("host" to "host", "path" to "path", "headers" to "headers"), "transport")
            }
            else -> fail("transport.type", "'$type' has no lossless Xray mapping")
        }
        stream.put(key, options)
    }

    private fun tls(source: JSONObject, stream: JSONObject) {
        if (!source.optBoolean("enabled", false)) return
        val reality = source.optJSONObject("reality")?.takeIf { it.optBoolean("enabled") }
        if (source.optBoolean("insecure", false)) fail("tls.insecure", "the pinned Xray removed allowInsecure; provide a verified certificate or use sing-box")
        val mapped = JSONObject()
        copy(source, mapped, "server_name", "serverName")
        copy(source, mapped, "alpn", "alpn")
        copy(source, mapped, "min_version", "minVersion")
        copy(source, mapped, "max_version", "maxVersion")
        source.optJSONObject("utls")?.takeIf { it.optBoolean("enabled") }?.let { mapped.put("fingerprint", it.getString("fingerprint")) }
        if (reality != null) {
            mapped.put("password", reality.getString("public_key"))
            copy(reality, mapped, "short_id", "shortId")
            stream.put("security", "reality").put("realitySettings", mapped)
        } else {
            source.opt("certificate")?.let { pem ->
                val text = if (pem is JSONArray) (0 until pem.length()).joinToString("\n") { pem.getString(it) } else pem.toString()
                mapped.put("disableSystemRoot", true).put("certificates", JSONArray().put(JSONObject()
                    .put("usage", "verify").put("certificate", JSONArray(text.lines()))))
            }
            stream.put("security", "tls").put("tlsSettings", mapped)
        }
        val allowed = setOf("enabled", "server_name", "alpn", "min_version", "max_version", "utls", "reality", "certificate", "insecure")
        source.keys().forEach { if (it !in allowed) fail("tls.$it", "security setting cannot be silently removed when converting to Xray") }
    }

    private fun copy(source: JSONObject, target: JSONObject, old: String, new: String) {
        if (source.has(old) && !source.isNull(old)) target.put(new, source.get(old))
    }
    private fun fail(path: String, explanation: String): Nothing = throw ConfigTranslationException(path, explanation)
}
