package com.marbleng.app.core

// MARBLE_SSH_PARSE_V25
// MARBLE_PATTNG_TLS_PARITY_V28

import android.net.Uri
import android.util.Base64
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Xray input normalizer. Share URIs cover common Xray URI formats; full Xray JSON is the
 * compatibility escape hatch and is preserved for XrayManager's final `xray run -test` check.
 */
object ProxyParser {
    private val shareSchemes = Regex("(?i)^(vless|vmess|trojan|ss|hysteria2|hy2|socks|socks5|http|https|ssh)://")
    private val shareFinder = Regex("(?i)(?:vless|vmess|trojan|ss|hysteria2|hy2|socks5?|https?|ssh)://[^\\s]+")
    private val infraProtocols = setOf("freedom", "blackhole", "dns", "loopback")

    fun parseInput(input: String, subId: String = "manual", subName: String = "Manual"): List<ProxyProfile> {
        val text = decodeSubscription(input.trim())
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{")) return parseJsonObject(JSONObject(text), text, subId, subName)
        if (trimmed.startsWith("[")) return parseJsonArray(JSONArray(text), subId, subName)
        return text.replace("\r", "\n").lines().flatMap { line ->
            val value = line.trim()
            if (value.isBlank() || value.startsWith("#")) emptyList()
            else shareFinder.findAll(value).mapNotNull { match ->
                runCatching { parseUri(match.value, subId, subName) }.getOrNull()
            }.toList()
        }.distinctBy { it.id }
    }

    private fun parseJsonArray(array: JSONArray, subId: String, subName: String): List<ProxyProfile> {
        val out = mutableListOf<ProxyProfile>()
        for (i in 0 until array.length()) {
            when (val item = array.opt(i)) {
                is JSONObject -> out += parseJsonObject(item, item.toString(), subId, subName)
                is String -> out += runCatching { parseInput(item, subId, subName) }.getOrDefault(emptyList())
            }
        }
        return out.distinctBy { it.id }
    }

    private fun parseJsonObject(value: JSONObject, raw: String, subId: String, subName: String): List<ProxyProfile> {
        val root = when {
            value.optJSONArray("outbounds") != null -> JSONObject(value.toString())
            value.optString("protocol").isNotBlank() -> base(JSONObject(value.toString()))
            else -> error("JSON is not an Xray config or outbound")
        }
        return listOf(profileFromConfig(raw, root, subId, subName))
    }

    private data class EndpointMeta(
        val host: String = "",
        val port: Int = 0,
        val transport: String = "native",
        val security: String = "none"
    )

    private fun profileFromConfig(raw: String, root: JSONObject, subId: String, subName: String): ProxyProfile {
        val outbounds = root.optJSONArray("outbounds") ?: error("Xray JSON has no outbounds")
        var selected: JSONObject? = null
        for (i in 0 until outbounds.length()) {
            val candidate = outbounds.optJSONObject(i) ?: continue
            if (candidate.optString("protocol").lowercase() !in infraProtocols) {
                selected = candidate
                break
            }
        }
        val outbound = selected ?: error("Xray JSON has no proxy outbound")
        val protocol = outbound.optString("protocol", "json").lowercase()
        val meta = endpointMeta(outbound)
        val scheme = when (protocol) {
            "shadowsocks" -> "ss"
            "hysteria" -> "hysteria2"
            else -> protocol
        }
        val name = sequenceOf(
            root.optString("remarks"), root.optString("ps"), root.optString("name"), outbound.optString("tag")
        ).firstOrNull { it.isNotBlank() } ?: "$subName (${protocol.uppercase()})"
        val canonical = root.toString()
        return ProxyProfile(
            id(canonical), dec(name).take(120), scheme, raw, canonical,
            meta.host, meta.port, meta.transport, meta.security, subId, subName
        )
    }

    private fun endpointMeta(outbound: JSONObject): EndpointMeta {
        val settings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
        val protocol = outbound.optString("protocol").lowercase()
        var host = settings.optString("address")
        var port = settings.optInt("port", 0)

        if (host.isBlank()) {
            settings.optJSONArray("vnext")?.optJSONObject(0)?.let { first ->
                host = first.optString("address")
                if (port <= 0) port = first.optInt("port", 0)
            }
        }
        if (host.isBlank()) {
            settings.optJSONArray("servers")?.optJSONObject(0)?.let { first ->
                host = first.optString("address")
                if (port <= 0) port = first.optInt("port", 0)
            }
        }
        if (protocol == "wireguard") {
            settings.optJSONArray("peers")?.optJSONObject(0)?.optString("endpoint")
                ?.takeIf { it.isNotBlank() }?.let { endpoint ->
                    val parsed = splitEndpoint(endpoint)
                    if (host.isBlank()) host = parsed.first
                    if (port <= 0) port = parsed.second
                }
        }
        val transport = stream.optString("method").ifBlank {
            stream.optString("network").ifBlank { if (protocol == "wireguard") "wireguard" else "native" }
        }
        return EndpointMeta(
            host.trim(), port, transport.lowercase(), stream.optString("security", "none").ifBlank { "none" }.lowercase()
        )
    }

    private fun splitEndpoint(raw: String): Pair<String, Int> {
        val value = raw.trim()
        if (value.startsWith("[")) {
            val close = value.indexOf(']')
            if (close > 1) {
                val host = value.substring(1, close)
                val port = value.substring(close + 1).removePrefix(":").toIntOrNull() ?: 0
                return host to port
            }
        }
        val colon = value.lastIndexOf(':')
        if (colon > 0 && value.indexOf(':') == colon) {
            value.substring(colon + 1).toIntOrNull()?.let { return value.substring(0, colon) to it }
        }
        return value to 0
    }

    private fun decodeSubscription(value: String): String {
        if (shareSchemes.containsMatchIn(value) || value.startsWith("{") || value.startsWith("[")) return value
        val compact = value.filterNot(Char::isWhitespace)
        if (compact.length < 16) return value
        return runCatching { String(decode64(compact), Charsets.UTF_8).trim() }.getOrDefault(value)
    }

    private fun decode64(value: String): ByteArray {
        val padded = pad64(value)
        return runCatching { Base64.decode(padded, Base64.DEFAULT) }
            .getOrElse { Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP) }
    }

    private fun pad64(value: String) = value + "=".repeat((4 - value.length % 4) % 4)
    private fun id(raw: String) = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        .take(8).joinToString("") { "%02x".format(it) }
    /**
     * Percent-decoding that never drops a node.
     *
     * Uri.parse() already returns decoded fragments/user-info, and node names legitimately contain
     * a bare '%' ("100% uptime"). URLDecoder throws on those and used to make the whole share link
     * disappear from the library, so decoding is attempted only when it can apply and never throws.
     */
    private fun dec(value: String?): String {
        val raw = value ?: return ""
        if (!raw.contains('%')) return raw
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun parseUri(raw: String, sid: String, sname: String): ProxyProfile = when (raw.substringBefore(':').lowercase()) {
        "vless" -> parseVless(raw, sid, sname)
        "vmess" -> parseVmess(raw, sid, sname)
        "trojan" -> parseTrojan(raw, sid, sname)
        "ss" -> parseSs(raw, sid, sname)
        "hysteria2", "hy2" -> parseHy2(raw, sid, sname)
        "socks", "socks5", "http", "https" -> parseBasic(raw, sid, sname)
        "ssh" -> parseSsh(raw, sid, sname)
        else -> error("Unsupported share URI")
    }

    private fun parseSsh(raw: String, sid: String, sname: String): ProxyProfile {
        val endpoint = SshProfileCodec.parseRaw(raw)
        val name = endpoint.name.ifBlank { "SSH ${endpoint.host}" }.take(120)
        return ProxyProfile(
            id = id(raw),
            name = name,
            scheme = "ssh",
            raw = raw,
            configJson = SshProfileCodec.xrayClientConfig(1),
            host = endpoint.host,
            port = endpoint.port,
            transport = "ssh",
            security = "ssh",
            subscriptionId = sid,
            subscriptionName = sname
        )
    }

    private fun q(uri: Uri, key: String, default: String = "") = uri.getQueryParameter(key) ?: default
    private fun qa(uri: Uri, vararg keys: String, default: String = ""): String {
        for (key in keys) uri.getQueryParameter(key)?.takeIf { it.isNotBlank() }?.let { return it }
        return default
    }

    private fun stream(uri: Uri, host: String): JSONObject {
        val requested = q(uri, "type", "tcp").lowercase()
        val method = when (requested) {
            "tcp", "raw" -> "raw"
            "ws", "websocket" -> "websocket"
            "splithttp", "xhttp" -> "xhttp"
            "kcp", "mkcp" -> "mkcp"
            else -> requested
        }
        val stream = JSONObject()
        val path = q(uri, "path", "/")
        val headerHost = q(uri, "host")

        if (requested in setOf("http", "h2")) {
            stream.put("network", "http")
            stream.put("security", "none")
            stream.put("httpSettings", JSONObject().put("path", path).apply {
                if (headerHost.isNotBlank()) put("host", JSONArray().put(headerHost))
            })
        } else {
            stream.put("method", method)
            stream.put("security", "none")
            when (method) {
                "xhttp" -> stream.put("xhttpSettings", JSONObject().put("path", path).apply {
                    if (headerHost.isNotBlank()) put("host", headerHost)
                    q(uri, "mode").takeIf { it.isNotBlank() }?.let { put("mode", it) }
                    q(uri, "extra").takeIf { it.trimStart().startsWith("{") }?.let { extra ->
                        runCatching { put("extra", JSONObject(extra)) }
                    }
                })
                "websocket" -> stream.put("wsSettings", JSONObject().put("path", path).apply {
                    if (headerHost.isNotBlank()) put("host", headerHost)
                })
                "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject().put("path", path).apply {
                    if (headerHost.isNotBlank()) put("host", headerHost)
                })
                "grpc" -> stream.put("grpcSettings", JSONObject().apply {
                    qa(uri, "serviceName", "service", default = q(uri, "path")).trimStart('/')
                        .takeIf { it.isNotBlank() }?.let { put("serviceName", it) }
                    q(uri, "authority").takeIf { it.isNotBlank() }?.let { put("authority", it) }
                })
                "raw" -> stream.put("rawSettings", JSONObject().put(
                    "header", JSONObject().put("type", q(uri, "headerType", "none"))
                ))
                "mkcp" -> stream.put("kcpSettings", JSONObject())
            }
        }

        var security = q(uri, "security", "none").lowercase()
        if (security == "xtls") security = "tls"
        stream.put("security", security)
        val serverName = qa(uri, "sni", "serverName", default = host)
        val fingerprint = qa(uri, "fp", "fingerprint", default = "chrome")

        if (security == "tls") stream.put("tlsSettings", JSONObject()
            .put("serverName", serverName).put("fingerprint", fingerprint).apply {
                q(uri, "alpn").takeIf { it.isNotBlank() }?.let { put("alpn", JSONArray(it.split(','))) }
                // PattNG share-link extension: `cs` carries Xray's colon-separated cipherSuites.
                qa(uri, "cs", "cipherSuites").takeIf { it.isNotBlank() }
                    ?.let { put("cipherSuites", it) }
                if (qa(uri, "allowInsecure", "insecure") in setOf("1", "true")) put("allowInsecure", true)
                q(uri, "pinnedPeerCertSha256").takeIf { it.isNotBlank() }?.let { put("pinnedPeerCertSha256", it) }
                q(uri, "echConfigList").takeIf { it.isNotBlank() }?.let { put("echConfigList", it) }
            })

        if (security == "reality") stream.put("realitySettings", JSONObject()
            .put("serverName", serverName).put("fingerprint", fingerprint)
            .put("password", qa(uri, "pbk", "publicKey", "password"))
            .put("shortId", qa(uri, "sid", "shortId")).apply {
                qa(uri, "spx", "spiderX").takeIf { it.isNotBlank() }?.let { put("spiderX", it) }
                qa(uri, "pqv", "mldsa65Verify").takeIf { it.isNotBlank() }?.let { put("mldsa65Verify", it) }
            })

        q(uri, "fm").takeIf { it.trimStart().startsWith("{") }?.let { fm ->
            runCatching { stream.put("finalmask", JSONObject(fm)) }
        }
        return stream
    }

    private fun base(outbound: JSONObject) = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
        .put("inbounds", JSONArray().put(JSONObject().put("tag", "socks-in").put("listen", "127.0.0.1")
            .put("port", 10808).put("protocol", "socks").put("settings", JSONObject().put("udp", true))))
        .put("outbounds", JSONArray().put(outbound).put(JSONObject().put("tag", "block").put("protocol", "blackhole")))

    private fun parseVless(raw: String, sid: String, sname: String): ProxyProfile {
        val u = Uri.parse(raw); val host = u.host ?: error("host"); val port = u.port.takeIf { it > 0 } ?: error("port")
        val out = JSONObject().put("tag", "proxy").put("protocol", "vless").put("settings", JSONObject()
            .put("address", host).put("port", port).put("id", dec(u.userInfo)).put("encryption", q(u, "encryption", "none"))
            .apply { q(u, "flow").takeIf { it.isNotBlank() }?.let { put("flow", it) } }).put("streamSettings", stream(u, host))
        return prof(raw, u.fragment ?: "VLESS $host", "vless", host, port, q(u, "type", "tcp"), q(u, "security", "none"), base(out), sid, sname)
    }

    private fun parseVmess(raw: String, sid: String, sname: String): ProxyProfile {
        val body = raw.removePrefix("vmess://").substringBefore('#')
        if (!body.substringBefore('?').contains('@')) {
            val o = JSONObject(String(decode64(body), Charsets.UTF_8)); val host = o.optString("add", o.optString("address"))
            val port = o.optString("port").trim().toIntOrNull() ?: o.optInt("port")
            require(port in 1..65535) { "VMess payload has no usable port" }
            val uuid = o.getString("id"); val net = o.optString("net", "tcp")
            val sec = if (o.optString("tls").lowercase() in setOf("tls", "1", "true")) "tls" else "none"
            val fake = Uri.parse("vmess://$uuid@$host:$port?type=${Uri.encode(net)}&security=$sec&host=${Uri.encode(o.optString("host"))}&path=${Uri.encode(o.optString("path"))}&sni=${Uri.encode(o.optString("sni"))}&fp=${Uri.encode(o.optString("fp", "chrome"))}&cs=${Uri.encode(o.optString("cs", o.optString("cipherSuites")))}")
            val out = JSONObject().put("tag", "proxy").put("protocol", "vmess").put("settings", JSONObject()
                .put("address", host).put("port", port).put("id", uuid).put("security", o.optString("scy", "auto")))
                .put("streamSettings", stream(fake, host))
            return prof(raw, o.optString("ps", "VMess $host"), "vmess", host, port, net, sec, base(out), sid, sname)
        }
        val u = Uri.parse(raw); val host = u.host ?: error("host"); val port = u.port.takeIf { it > 0 } ?: error("port")
        val out = JSONObject().put("tag", "proxy").put("protocol", "vmess").put("settings", JSONObject()
            .put("address", host).put("port", port).put("id", dec(u.userInfo)).put("security", q(u, "encryption", "auto")))
            .put("streamSettings", stream(u, host))
        return prof(raw, u.fragment ?: "VMess $host", "vmess", host, port, q(u, "type", "tcp"), q(u, "security", "none"), base(out), sid, sname)
    }

    private fun parseTrojan(raw: String, sid: String, sname: String): ProxyProfile {
        val u = Uri.parse(raw); val host = u.host ?: error("host"); val port = u.port.takeIf { it > 0 } ?: error("port")
        // Trojan is TLS-only in practice and share links routinely omit `security`. String
        // surgery on the raw URI silently failed when there was no query string at all.
        val rebuilt = if (q(u, "security").isBlank()) {
            u.buildUpon().appendQueryParameter("security", "tls").build()
        } else {
            u
        }
        val out = JSONObject().put("tag", "proxy").put("protocol", "trojan")
            .put("settings", JSONObject().put("address", host).put("port", port).put("password", dec(u.userInfo)))
            .put("streamSettings", stream(rebuilt, host))
        return prof(raw, u.fragment ?: "Trojan $host", "trojan", host, port, q(u, "type", "tcp"), q(rebuilt, "security", "tls"), base(out), sid, sname)
    }

    private fun parseSs(raw: String, sid: String, sname: String): ProxyProfile {
        val u = Uri.parse(raw); require(q(u, "plugin").isBlank()) { "SS plugin is not a native Xray outbound" }
        var host = u.host; var port = u.port; var method = ""; var password = ""
        val userInfo = u.userInfo
        if (host != null && !userInfo.isNullOrBlank()) {
            // SIP002 allows plain "method:password" or base64("method:password") in the user info.
            val credentials = if (userInfo.contains(':')) {
                dec(userInfo)
            } else {
                runCatching { String(decode64(userInfo), Charsets.UTF_8) }.getOrDefault("")
            }
            method = credentials.substringBefore(':', ""); password = credentials.substringAfter(':', "")
        }
        if (method.isBlank()) {
            val decoded = String(decode64(raw.removePrefix("ss://").substringBefore('#').substringBefore('?')), Charsets.UTF_8)
            val du = Uri.parse("ss://$decoded"); host = du.host; port = du.port
            val credentials = dec(du.userInfo)
            method = credentials.substringBefore(':', ""); password = credentials.substringAfter(':', "")
        }
        require(host != null && port > 0 && method.isNotBlank() && password.isNotBlank())
        val out = JSONObject().put("tag", "proxy").put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("address", host).put("port", port).put("method", method).put("password", password))
        return prof(raw, u.fragment ?: "SS $host", "ss", host!!, port, "native", "none", base(out), sid, sname)
    }

    private fun parseHy2(raw: String, sid: String, sname: String): ProxyProfile {
        val u = Uri.parse(raw); val host = u.host ?: error("host"); val port = u.port.takeIf { it > 0 } ?: 443
        val tls = JSONObject().put("serverName", qa(u, "sni", "serverName", default = host))
            .put("fingerprint", qa(u, "fp", "fingerprint", default = "chrome")).put("alpn", JSONArray().put("h3"))
            .apply { if (qa(u, "allowInsecure", "insecure") in setOf("1", "true")) put("allowInsecure", true) }
        val st = JSONObject().put("method", "hysteria").put("security", "tls").put("tlsSettings", tls)
            .put("hysteriaSettings", JSONObject().put("version", 2).put("auth", dec(u.userInfo)))
        val out = JSONObject().put("tag", "proxy").put("protocol", "hysteria")
            .put("settings", JSONObject().put("version", 2).put("address", host).put("port", port)).put("streamSettings", st)
        return prof(raw, u.fragment ?: "Hysteria2 $host", "hysteria2", host, port, "hysteria", "tls", base(out), sid, sname)
    }

    private fun parseBasic(raw: String, sid: String, sname: String): ProxyProfile {
        val u = Uri.parse(raw); val host = u.host ?: error("host"); val scheme = u.scheme!!.lowercase()
        val port = u.port.takeIf { it > 0 } ?: when (scheme) { "https" -> 443; "http" -> 80; else -> 1080 }
        val info = dec(u.userInfo); val user = info.substringBefore(':'); val pass = info.substringAfter(':', "")
        val protocol = if (scheme.startsWith("socks")) "socks" else "http"
        val settings = JSONObject().put("address", host).put("port", port)
        if (user.isNotBlank()) { settings.put("user", user); settings.put("pass", pass) }
        val out = JSONObject().put("tag", "proxy").put("protocol", protocol).put("settings", settings)
        if (scheme == "https") out.put(
            "streamSettings",
            JSONObject()
                .put("method", "raw")
                .put("security", "tls")
                .put(
                    "tlsSettings",
                    JSONObject()
                        .put("serverName", qa(u, "sni", "serverName", default = host))
                        .put("fingerprint", qa(u, "fp", "fingerprint", default = "chrome"))
                        .apply {
                            qa(u, "cs", "cipherSuites").takeIf { it.isNotBlank() }
                                ?.let { put("cipherSuites", it) }
                        }
                )
        )
        return prof(raw, "${protocol.uppercase()} $host", scheme, host, port, "native", if (scheme == "https") "tls" else "none", base(out), sid, sname)
    }

    private fun prof(raw: String, name: String, scheme: String, host: String, port: Int, transport: String, security: String,
                     cfg: JSONObject, sid: String, sname: String) =
        ProxyProfile(id(raw), dec(name).take(120), scheme, raw, cfg.toString(), host, port, transport, security, sid, sname)
}

