package com.marbleng.app.core

// MARBLE_MANUAL_CONFIGS_V20
// MARBLE_SSH_MANUAL_V25

import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class ManualProtocol(val label: String, val scheme: String) {
    VLESS("VLESS", "vless"),
    VMESS("VMess", "vmess"),
    TROJAN("Trojan", "trojan"),
    SHADOWSOCKS("Shadowsocks", "ss"),
    HYSTERIA2("Hysteria2", "hysteria2"),
    HTTP("HTTP", "http"),
    HTTPS("HTTPS", "https"),
    SOCKS5("SOCKS5", "socks5"),
    SSH("SSH", "ssh"),
    WIREGUARD("WireGuard", "wireguard"),
    XRAY_JSON("Xray JSON", "json")
}

data class ManualConfigDraft(
    val protocol: ManualProtocol = ManualProtocol.VLESS,
    val name: String = "",
    val host: String = "",
    val port: String = "443",
    val username: String = "",
    val password: String = "",
    val sshHostKeySha256: String = "",
    val uuid: String = "",
    val encryption: String = "none",
    val flow: String = "",
    val method: String = "aes-128-gcm",
    val transport: String = "raw",
    val security: String = "tls",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val path: String = "/",
    val hostHeader: String = "",
    val serviceName: String = "",
    val alpn: String = "",
    val allowInsecure: Boolean = false,
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "",
    val wireguardSecretKey: String = "",
    val wireguardAddress: String = "10.0.0.2/32",
    val wireguardPeerPublicKey: String = "",
    val wireguardPreSharedKey: String = "",
    val wireguardAllowedIps: String = "0.0.0.0/0, ::/0",
    val wireguardReserved: String = "",
    val wireguardKeepAlive: String = "25",
    val wireguardMtu: String = "1420",
    val wireguardNoKernelTun: Boolean = true,
    val customJson: String = ""
)

/**
 * Builds durable Manual profiles from explicit fields. SSH profiles are carried by Marble's
 * protected loopback SSH-to-SOCKS adapter; Xray remains attached to the Android TUN.
 */
object ManualConfigBuilder {
    fun build(draft: ManualConfigDraft): ProxyProfile {
        if (draft.protocol == ManualProtocol.XRAY_JSON) {
            val parsed = ProxyParser.parseInput(draft.customJson.trim(), "manual", "Manual")
            require(parsed.size == 1) { "Paste one Xray config or one outbound" }
            val source = parsed.single()
            return source.copy(
                name = draft.name.trim().ifBlank { source.name },
                subscriptionId = "manual",
                subscriptionName = "Manual"
            )
        }

        val host = draft.host.trim()
        require(host.isNotBlank()) { "Host is required" }
        val port = draft.port.trim().toIntOrNull()
            ?: defaultPort(draft.protocol)
        require(port in 1..65535) { "Port must be 1-65535" }

        if (draft.protocol == ManualProtocol.SSH) {
            val username = draft.username.trim()
            require(username.isNotBlank()) { "SSH username is required" }
            require(draft.password.isNotBlank()) { "SSH password is required" }
            val name = draft.name.trim().ifBlank { "SSH $host" }.take(120)
            val raw = SshProfileCodec.encode(
                host = host,
                port = port,
                username = username,
                password = draft.password,
                hostKeySha256 = draft.sshHostKeySha256,
                name = name
            )
            return ProxyProfile(
                id = stableId(raw),
                name = name,
                scheme = "ssh",
                raw = raw,
                configJson = SshProfileCodec.xrayClientConfig(1),
                host = host,
                port = port,
                transport = "ssh",
                security = "ssh",
                subscriptionId = "manual",
                subscriptionName = "Manual"
            )
        }

        val outbound = when (draft.protocol) {
            ManualProtocol.VLESS -> vless(draft, host, port)
            ManualProtocol.VMESS -> vmess(draft, host, port)
            ManualProtocol.TROJAN -> trojan(draft, host, port)
            ManualProtocol.SHADOWSOCKS -> shadowsocks(draft, host, port)
            ManualProtocol.HYSTERIA2 -> hysteria2(draft, host, port)
            ManualProtocol.HTTP, ManualProtocol.HTTPS -> http(draft, host, port)
            ManualProtocol.SOCKS5 -> socks(draft, host, port)
            ManualProtocol.SSH -> error("handled above")
            ManualProtocol.WIREGUARD -> wireguard(draft, host, port)
            ManualProtocol.XRAY_JSON -> error("handled above")
        }

        val root = base(outbound)
        val canonical = root.toString()
        val name = draft.name.trim().ifBlank { "${draft.protocol.label} $host" }.take(120)
        val transport = when (draft.protocol) {
            ManualProtocol.HYSTERIA2 -> "hysteria"
            ManualProtocol.WIREGUARD -> "wireguard"
            ManualProtocol.SHADOWSOCKS,
            ManualProtocol.HTTP,
            ManualProtocol.HTTPS,
            ManualProtocol.SOCKS5 -> "native"
            else -> normalizedTransport(draft.transport)
        }
        val security = when (draft.protocol) {
            ManualProtocol.HYSTERIA2, ManualProtocol.HTTPS -> "tls"
            ManualProtocol.WIREGUARD,
            ManualProtocol.SHADOWSOCKS,
            ManualProtocol.HTTP,
            ManualProtocol.SOCKS5 -> "none"
            else -> draft.security.trim().lowercase().ifBlank { "none" }
        }

        return ProxyProfile(
            id = stableId(canonical),
            name = name,
            scheme = draft.protocol.scheme,
            raw = canonical,
            configJson = canonical,
            host = host,
            port = port,
            transport = transport,
            security = security,
            subscriptionId = "manual",
            subscriptionName = "Manual"
        )
    }

    private fun vless(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        val id = d.uuid.trim()
        require(id.isNotBlank()) { "VLESS UUID is required" }
        val encryption = d.encryption.trim().ifBlank { "none" }
        val security = d.security.trim().lowercase().ifBlank { "none" }
        require(security != "none" || !encryption.equals("none", true)) {
            "VLESS needs TLS/REALITY or non-none VLESS encryption"
        }
        val settings = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("id", id)
            .put("encryption", encryption)
        d.flow.trim().takeIf(String::isNotBlank)?.let { settings.put("flow", it) }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", settings)
            .put("streamSettings", stream(d, host))
    }

    private fun vmess(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        require(d.uuid.trim().isNotBlank()) { "VMess UUID is required" }
        val settings = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("id", d.uuid.trim())
            .put("security", d.encryption.trim().ifBlank { "auto" })
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", settings)
            .put("streamSettings", stream(d, host))
    }

    private fun trojan(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        require(d.password.isNotBlank()) { "Trojan password is required" }
        val secured = if (d.security.equals("none", true)) d.copy(security = "tls") else d
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put(
                "settings",
                JSONObject().put("address", host).put("port", port).put("password", d.password)
            )
            .put("streamSettings", stream(secured, host))
    }

    private fun shadowsocks(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        require(d.method.trim().isNotBlank()) { "Shadowsocks method is required" }
        require(d.password.isNotBlank()) { "Shadowsocks password is required" }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put(
                "settings",
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("method", d.method.trim())
                    .put("password", d.password)
            )
    }

    private fun hysteria2(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        require(d.password.isNotBlank()) { "Hysteria2 auth is required" }
        val tls = tlsSettings(d, host).apply { put("alpn", JSONArray().put("h3")) }
        val hysteria = JSONObject().put("version", 2).put("auth", d.password)
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "hysteria")
            .put(
                "settings",
                JSONObject().put("version", 2).put("address", host).put("port", port)
            )
            .put(
                "streamSettings",
                JSONObject()
                    .put("method", "hysteria")
                    .put("security", "tls")
                    .put("tlsSettings", tls)
                    .put("hysteriaSettings", hysteria)
            )
    }

    private fun http(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        val settings = JSONObject().put("address", host).put("port", port)
        if (d.username.isNotBlank()) {
            settings.put("user", d.username).put("pass", d.password)
        }
        val out = JSONObject().put("tag", "proxy").put("protocol", "http").put("settings", settings)
        if (d.protocol == ManualProtocol.HTTPS) {
            out.put(
                "streamSettings",
                JSONObject()
                    .put("method", "raw")
                    .put("security", "tls")
                    .put("tlsSettings", tlsSettings(d, host))
            )
        }
        return out
    }

    private fun socks(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        val settings = JSONObject().put("address", host).put("port", port)
        if (d.username.isNotBlank()) {
            settings.put("user", d.username).put("pass", d.password)
        }
        return JSONObject().put("tag", "proxy").put("protocol", "socks").put("settings", settings)
    }

    private fun wireguard(d: ManualConfigDraft, host: String, port: Int): JSONObject {
        require(d.wireguardSecretKey.trim().isNotBlank()) { "WireGuard private key is required" }
        require(d.wireguardPeerPublicKey.trim().isNotBlank()) { "WireGuard peer public key is required" }
        val addresses = stringArray(d.wireguardAddress)
        require(addresses.length() > 0) { "WireGuard local address is required" }
        val allowed = stringArray(d.wireguardAllowedIps)
        require(allowed.length() > 0) { "WireGuard allowed IPs are required" }

        val peer = JSONObject()
            .put("endpoint", endpoint(host, port))
            .put("publicKey", d.wireguardPeerPublicKey.trim())
            .put("allowedIPs", allowed)
        d.wireguardPreSharedKey.trim().takeIf(String::isNotBlank)?.let { peer.put("preSharedKey", it) }
        d.wireguardKeepAlive.trim().toIntOrNull()?.takeIf { it >= 0 }?.let { peer.put("keepAlive", it) }

        val settings = JSONObject()
            .put("secretKey", d.wireguardSecretKey.trim())
            .put("address", addresses)
            .put("peers", JSONArray().put(peer))
            .put("noKernelTun", d.wireguardNoKernelTun)
            .put("domainStrategy", "ForceIP")

        d.wireguardMtu.trim().toIntOrNull()?.takeIf { it in 576..9000 }?.let { settings.put("mtu", it) }
        val reserved = intArray(d.wireguardReserved)
        if (reserved.length() > 0) settings.put("reserved", reserved)

        return JSONObject().put("tag", "proxy").put("protocol", "wireguard").put("settings", settings)
    }

    private fun stream(d: ManualConfigDraft, host: String): JSONObject {
        val method = normalizedTransport(d.transport)
        val stream = JSONObject().put("method", method).put("security", "none")
        val path = d.path.trim().ifBlank { "/" }
        val headerHost = d.hostHeader.trim()

        when (method) {
            "raw" -> stream.put(
                "rawSettings",
                JSONObject().put("header", JSONObject().put("type", "none"))
            )
            "websocket" -> stream.put(
                "wsSettings",
                JSONObject().put("path", path).apply {
                    if (headerHost.isNotBlank()) put("host", headerHost)
                }
            )
            "xhttp" -> stream.put(
                "xhttpSettings",
                JSONObject().put("path", path).apply {
                    if (headerHost.isNotBlank()) put("host", headerHost)
                }
            )
            "grpc" -> stream.put(
                "grpcSettings",
                JSONObject().apply {
                    d.serviceName.trim().trimStart('/').takeIf(String::isNotBlank)
                        ?.let { put("serviceName", it) }
                    if (headerHost.isNotBlank()) put("authority", headerHost)
                }
            )
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject().put("path", path).apply {
                    if (headerHost.isNotBlank()) put("host", headerHost)
                }
            )
            "mkcp" -> stream.put("kcpSettings", JSONObject())
        }

        val security = d.security.trim().lowercase().ifBlank { "none" }
        if (security == "reality") {
            require(method in setOf("raw", "xhttp", "grpc")) {
                "REALITY is not supported with $method transport"
            }
        }
        stream.put("security", security)
        when (security) {
            "tls" -> stream.put("tlsSettings", tlsSettings(d, host))
            "reality" -> {
                require(d.realityPublicKey.trim().isNotBlank()) { "REALITY public key is required" }
                val reality = JSONObject()
                    .put("serverName", d.sni.trim().ifBlank { host })
                    .put("fingerprint", d.fingerprint.trim().ifBlank { "chrome" })
                    .put("password", d.realityPublicKey.trim())
                    .put("shortId", d.realityShortId.trim())
                d.realitySpiderX.trim().takeIf(String::isNotBlank)?.let { reality.put("spiderX", it) }
                stream.put("realitySettings", reality)
            }
            "none" -> Unit
            else -> error("Unsupported stream security: $security")
        }
        return stream
    }

    private fun tlsSettings(d: ManualConfigDraft, host: String): JSONObject =
        JSONObject()
            .put("serverName", d.sni.trim().ifBlank { host })
            .put("fingerprint", d.fingerprint.trim().ifBlank { "chrome" })
            .apply {
                if (d.allowInsecure) put("allowInsecure", true)
                val values = splitList(d.alpn)
                if (values.isNotEmpty()) put("alpn", JSONArray(values))
            }

    private fun base(outbound: JSONObject): JSONObject =
        JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "socks-in")
                        .put("listen", "127.0.0.1")
                        .put("port", 10808)
                        .put("protocol", "socks")
                        .put("settings", JSONObject().put("udp", true))
                )
            )
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            )

    private fun normalizedTransport(value: String): String = when (value.trim().lowercase()) {
        "tcp", "raw", "" -> "raw"
        "ws", "websocket" -> "websocket"
        "splithttp", "xhttp" -> "xhttp"
        "grpc" -> "grpc"
        "httpupgrade" -> "httpupgrade"
        "kcp", "mkcp" -> "mkcp"
        else -> error("Unsupported transport: $value")
    }

    private fun defaultPort(protocol: ManualProtocol): Int = when (protocol) {
        ManualProtocol.HTTP -> 80
        ManualProtocol.SOCKS5 -> 1080
        ManualProtocol.SSH -> 22
        else -> 443
    }

    private fun splitList(value: String): List<String> =
        value.split(',', '\n', '\r').map(String::trim).filter(String::isNotBlank).distinct()

    private fun stringArray(value: String): JSONArray = JSONArray().apply {
        splitList(value).forEach { put(it) }
    }

    private fun intArray(value: String): JSONArray = JSONArray().apply {
        splitList(value).mapNotNull(String::toIntOrNull).forEach { put(it.coerceIn(0, 255)) }
    }

    private fun endpoint(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun stableId(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
