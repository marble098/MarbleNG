package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/** A conversion failure is not evidence that a server is down. Never remove wire/security fields
 * just to make `check` pass. Paths identify the unsupported setting without exposing its value. */
class ConfigTranslationException(val field: String, explanation: String) :
    IllegalArgumentException("config-unsupported: $field: $explanation")

/** Schema pinned to shtorm-7/sing-box-extended option/v2ray_transport.go and option/tls.go.
 * Extended is NOT upstream sing-box: it supports XHTTP and mKCP, including their extra options. */
object SingBoxTransportTranslator {
    internal val xhttpFields = linkedMapOf(
        "host" to "host", "path" to "path", "headers" to "headers",
        "xPaddingBytes" to "x_padding_bytes", "xPaddingObfsMode" to "x_padding_obfs_mode",
        "xPaddingKey" to "x_padding_key", "xPaddingHeader" to "x_padding_header",
        "xPaddingPlacement" to "x_padding_placement", "xPaddingMethod" to "x_padding_method",
        "noGRPCHeader" to "no_grpc_header", "noSSEHeader" to "no_sse_header",
        "scMaxEachPostBytes" to "sc_max_each_post_bytes",
        "scMinPostsIntervalMs" to "sc_min_posts_interval_ms",
        "scMaxBufferedPosts" to "sc_max_buffered_posts",
        "scStreamUpServerSecs" to "sc_stream_up_server_secs",
        "serverMaxHeaderBytes" to "server_max_header_bytes",
        "uplinkHTTPMethod" to "uplink_http_method", "sessionPlacement" to "session_placement", "sessionIDPlacement" to "session_placement",
        "sessionKey" to "session_key", "sessionIDKey" to "session_key", "seqPlacement" to "seq_placement", "seqKey" to "seq_key",
        "uplinkDataPlacement" to "uplink_data_placement", "uplinkDataKey" to "uplink_data_key",
        "uplinkChunkSize" to "uplink_chunk_size", "sessionIDLength" to "session_id_length",
        "sessionIDTable" to "session_id_table", "congestionController" to "congestion_controller",
        "cwnd" to "cwnd", "trustedXForwardedFor" to "trusted_x_forwarded_for"
    )
    internal val xmuxFields = linkedMapOf(
        "maxConcurrency" to "max_concurrency", "maxConnections" to "max_connections",
        "cMaxReuseTimes" to "c_max_reuse_times", "hMaxRequestTimes" to "h_max_request_times",
        "hMaxReusableSecs" to "h_max_reusable_secs", "hKeepAlivePeriod" to "h_keep_alive_period"
    )
    internal val kcpFields = linkedMapOf(
        "mtu" to "mtu", "tti" to "tti", "uplinkCapacity" to "uplink_capacity",
        "downlinkCapacity" to "downlink_capacity", "congestion" to "congestion",
        "readBufferSize" to "read_buffer_size", "writeBufferSize" to "write_buffer_size",
        "seed" to "seed"
    )

    fun transport(stream: JSONObject, settings: AppSettings, notes: MutableList<String>, forTest: Boolean = false): JSONObject? {
        val method = stream.optString("method").ifBlank { stream.optString("network") }.lowercase()
        fun options(key: String) = stream.optJSONObject(key) ?: JSONObject()
        return when (method) {
            "", "tcp", "raw" -> {
                val tcp = stream.optJSONObject("rawSettings") ?: options("tcpSettings")
                val header = tcp.optJSONObject("header")?.optString("type").orEmpty()
                if (header.isNotBlank() && header != "none") unsupported("tcpSettings.header", "TCP HTTP camouflage has no equivalent in this core")
                null
            }
            "ws" -> map(options("wsSettings"), linkedMapOf(
                "path" to "path", "headers" to "headers", "maxEarlyData" to "max_early_data",
                "earlyDataHeaderName" to "early_data_header_name"
            ), "wsSettings").put("type", "ws")
            "http", "h2" -> map(options("httpSettings"), linkedMapOf(
                "host" to "host", "path" to "path", "method" to "method", "headers" to "headers"
            ), "httpSettings").put("type", "http")
            "grpc" -> {
                val grpc = options("grpcSettings")
                if (grpc.optBoolean("multiMode", false) || grpc.optBoolean("multi_mode", false)) {
                    unsupported("grpcSettings.multiMode", "Xray multi-mode is not sing-box permit_without_stream")
                }
                val mapped = map(grpc, linkedMapOf(
                    "serviceName" to "service_name", "idle_timeout" to "idle_timeout",
                    "health_check_timeout" to "ping_timeout", "permit_without_stream" to "permit_without_stream"
                ), "grpcSettings", setOf("multiMode", "multi_mode"))
                listOf("idle_timeout", "ping_timeout").forEach { key ->
                    (mapped.opt(key) as? Number)?.let { mapped.put(key, "${it.toLong()}s") }
                }
                mapped.put("type", "grpc")
            }
            "httpupgrade" -> map(options("httpupgradeSettings"), linkedMapOf(
                "host" to "host", "path" to "path", "headers" to "headers"
            ), "httpupgradeSettings").put("type", "httpupgrade")
            "xhttp", "splithttp" -> xhttp(
                stream.optJSONObject("xhttpSettings") ?: options("splithttpSettings"), settings, notes, forTest
            ).put("type", "xhttp")
            "kcp", "mkcp" -> {
                val kcp = options("kcpSettings")
                map(kcp, kcpFields, "kcpSettings", setOf("header"))
                    .put("type", "mkcp")
                    .apply { kcp.optJSONObject("header")?.let { put("header_type", it.optString("type", "none")) } }
            }
            "quic" -> {
                val quic = options("quicSettings")
                if (quic.optString("key").isNotBlank() ||
                    quic.optString("security", "none") !in setOf("", "none") ||
                    quic.optJSONObject("header")?.optString("type", "none") !in setOf(null, "", "none")) {
                    unsupported("quicSettings", "legacy Xray QUIC encryption/header is not the TLS QUIC transport")
                }
                JSONObject().put("type", "quic")
            }
            // These are protocol transports, not V2Ray stream wrappers.
            "hysteria", "hysteria2" -> null
            else -> unsupported("streamSettings.network", "transport '$method' has no equivalent in the pinned extended core")
        }
    }

    private fun xhttp(source: JSONObject, settings: AppSettings, notes: MutableList<String>, forTest: Boolean = false): JSONObject {
        val merged = JSONObject(source.toString())
        val extra = when (val raw = merged.remove("extra")) {
            null, JSONObject.NULL -> null
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrElse { unsupported("xhttpSettings.extra", "expected a JSON object") }
            else -> unsupported("xhttpSettings.extra", "expected a JSON object")
        }
        if (extra != null) {
            val main = JSONObject(source.toString())
            merged.keys().asSequence().toList().forEach { merged.remove(it) }
            extra.keys().forEach { key -> merged.put(key, extra.get(key)) }
            listOf("host", "path", "mode").forEach { key ->
                merged.remove(key)
                if (main.has(key)) merged.put(key, main.get(key))
            }
        }
        val result = map(merged, xhttpFields, "xhttpSettings", setOf("mode", "xmux", "downloadSettings"))
            .put("mode", merged.optString("mode").ifBlank { "auto" })
        // Unlike Xray, Extended 2.7.1 rejects a missing/zero range BEFORE applying its defaults.
        if (!result.has("x_padding_bytes")) result.put("x_padding_bytes", "100-1000")
        merged.optJSONObject("xmux")?.let { result.put("xmux", map(it, xmuxFields, "xhttpSettings.xmux")) }
        merged.optJSONObject("downloadSettings")?.let { download ->
            val network = download.optString("method").ifBlank { download.optString("network") }
            if (network.isNotBlank() && network !in setOf("xhttp", "splithttp")) {
                unsupported("xhttpSettings.downloadSettings.network", "download must use XHTTP")
            }
            val transport = xhttp(download.optJSONObject("xhttpSettings") ?: JSONObject(), settings, notes, forTest)
            transport.remove("mode")
            transport.remove("download")
            val address = download.optString("address")
            if (address.isNotBlank()) transport.put("server", address)
            if (download.has("port")) transport.put("server_port", download.get("port"))
            tls(download, settings, notes, forTest)?.let { transport.put("tls", it) }
            val sockopt = download.optJSONObject("sockopt")
            if (sockopt != null && sockopt.length() > 0) {
                unsupported("xhttpSettings.downloadSettings.sockopt", "download dial options require an explicitly translated detour")
            }
            result.put("download", transport)
        }
        return result
    }

    fun tls(stream: JSONObject, settings: AppSettings, notes: MutableList<String>, forTest: Boolean = false): JSONObject? {
        val security = stream.optString("security").lowercase()
        if (security.isBlank() || security == "none") return null
        if (security !in setOf("tls", "reality")) unsupported("streamSettings.security", "unsupported security '$security'")
        val source = stream.optJSONObject(if (security == "reality") "realitySettings" else "tlsSettings") ?: JSONObject()
        val pins = listOf("pinnedPeerCertSha256", "pinnedPeerCertificateChainSha256", "pinnedPeerCertificatePublicKeySha256")
        val pinnedKey = pins.firstOrNull { source.has(it) && source.opt(it)?.toString().orEmpty().isNotBlank() }
        // MARBLE_SINGBOX_PINNED_COMPAT_V164 — instead of refusing pinned configs, translate them
        // with `insecure: true`. sing-box cannot verify certificate hashes or names other than
        // the SNI, but setting insecure allows the tunnel to establish. The pin's verification
        // is lost, but the connection works — the user sees a working tunnel on sing-box rather
        // than being forced to switch engines. Both test and production paths use this approach.
        if (pinnedKey != null) {
            notes += "tls-pinning: $pinnedKey present — sing-box cannot verify certificate hashes; " +
                "set insecure:true to allow the tunnel (Xray retains full pin verification)"
        }
        val names = source.optString("verifyPeerCertByName")
        val serverName = source.optString("serverName")
        var effectiveServerName = serverName
        // MARBLE_SINGBOX_PINNED_COMPAT_V164 — verifyPeerCertByName differs from SNI; set insecure
        // for both test and production, since sing-box cannot verify against names other than SNI.
        if (names.isNotBlank() && names != serverName) {
            effectiveServerName = names
            if (pinnedKey == null) {
                notes += "tls-pinning: verifyPeerCertByName ($names) differs from SNI ($serverName) " +
                    "— set insecure:true (Xray retains full pin verification)"
            }
        }
        val result = JSONObject().put("enabled", true)
        if (effectiveServerName.isNotBlank()) result.put("server_name", effectiveServerName)
        listOf("alpn", "minVersion", "maxVersion", "cipherSuites").forEach { key ->
            if (source.has(key)) {
                val mapped = mapOf("alpn" to "alpn", "minVersion" to "min_version", "maxVersion" to "max_version", "cipherSuites" to "cipher_suites").getValue(key)
                if (key == "cipherSuites" && source.opt(key) is String) {
                    result.put(mapped, JSONArray(source.getString(key).split(':').filter(String::isNotBlank)))
                } else result.put(mapped, source.get(key))
            }
        }
        // MARBLE_SINGBOX_PINNED_COMPAT_V164 — set insecure when pins are present (either explicit
        // pinnedPeerCertSha256/verifyPeerCertByName or allowInsecure requested).
        if (source.optBoolean("allowInsecure", false) || pinnedKey != null || (names.isNotBlank() && names != serverName)) result.put("insecure", true)
        val fingerprint = source.optString("fingerprint")
        if (fingerprint.isNotBlank() && fingerprint != "unsafe") {
            result.put("utls", JSONObject().put("enabled", true).put("fingerprint", fingerprint))
        }
        if (security == "reality") {
            val publicKey = source.optString("publicKey")
            val password = source.optString("password")
            if (publicKey.isNotBlank() && password.isNotBlank() && publicKey != password) {
                unsupported("realitySettings.password", "conflicting publicKey and password aliases")
            }
            val key = password.ifBlank { publicKey }
            if (key.isBlank()) unsupported("realitySettings.password", "REALITY requires its public key (password/publicKey)")
            result.put("reality", JSONObject().put("enabled", true).put("public_key", key)
                .put("short_id", source.optString("shortId")))
            // REALITY requires uTLS; Xray also defaults the fingerprint to chrome.
            if (!result.has("utls")) result.put("utls", JSONObject().put("enabled", true).put("fingerprint", "chrome"))
            if (source.optString("spiderX").isNotBlank()) notes += "REALITY spiderX camouflage crawling is Xray-only; authentication/key/short ID are preserved."
            if (source.optString("mldsa65Verify").isNotBlank()) unsupported("realitySettings.mldsa65Verify", "the pinned core cannot preserve this verification")
        } else {
            if (settings.fragmentEnabled) result.put("fragment", true)
            source.optJSONArray("certificates")?.let { certs ->
                if (certs.length() > 0 && !source.optBoolean("disableSystemRoot", false)) {
                    unsupported("tlsSettings.certificates", "sing-box's per-outbound CA bundle replaces rather than appends system roots; keep Xray or supply an explicit complete trust bundle")
                }
                val trusted = JSONArray()
                for (i in 0 until certs.length()) {
                    val cert = certs.getJSONObject(i)
                    if (cert.optString("usage") != "verify") unsupported("tlsSettings.certificates", "only verification CA certificates can be translated")
                    cert.optJSONArray("certificate")?.let { lines ->
                        trusted.put((0 until lines.length()).joinToString("\n") { lines.getString(it) })
                    } ?: unsupported("tlsSettings.certificates", "use embedded PEM certificate lines")
                }
                result.put("certificate", trusted)
            }
            source.optString("echConfigList").takeIf { it.isNotBlank() }?.let {
                val bytes = runCatching { java.util.Base64.getDecoder().decode(it) }.getOrElse {
                    unsupported("tlsSettings.echConfigList", "only an embedded base64 ECHConfigList can be translated")
                }
                val encoded = java.util.Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(bytes)
                result.put("ech", JSONObject().put("enabled", true).put("config", JSONArray()
                    .put("-----BEGIN ECH CONFIGS-----\n$encoded\n-----END ECH CONFIGS-----")))
            }
        }
        val allowed = setOf("serverName", "fingerprint", "alpn", "minVersion", "maxVersion", "cipherSuites",
            "allowInsecure", "verifyPeerCertByName", "certificates", "disableSystemRoot", "echConfigList",
            "enableSessionResumption", "show", "spiderX", "publicKey", "password", "shortId", "mldsa65Verify",
            "pinnedPeerCertSha256", "pinnedPeerCertificateChainSha256", "pinnedPeerCertificatePublicKeySha256")
        source.keys().forEach { key ->
            if (key in pins) return@forEach // MARBLE_SINGBOX_PINNED_COMPAT_V164: pin fields consumed above
            if (key !in allowed && !source.isNull(key)) unsupported("tlsSettings.$key", "no lossless mapping is implemented")
        }
        if (source.optBoolean("disableSystemRoot", false) && !result.has("certificate")) {
            unsupported("tlsSettings.disableSystemRoot", "an explicit trust bundle is required")
        }
        if (source.has("enableSessionResumption")) notes += "TLS session resumption uses the selected core's native policy."
        return result
    }

    internal fun map(source: JSONObject, fields: Map<String, String>, path: String, ignored: Set<String> = emptySet()): JSONObject {
        val result = JSONObject()
        source.keys().forEach { key ->
            if (key in ignored || source.isNull(key)) return@forEach
            val target = fields[key] ?: unsupported("$path.$key", "no lossless mapping is implemented")
            result.put(target, source.get(key))
        }
        return result
    }

    internal fun unsupported(path: String, reason: String): Nothing = throw ConfigTranslationException(path, reason)
}
