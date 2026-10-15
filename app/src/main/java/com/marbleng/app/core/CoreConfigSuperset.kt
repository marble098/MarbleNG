package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject

object CoreConfigSuperset {
    enum class Wire {
        ENCRYPTED,

        POST_QUANTUM,

        PLAINTEXT_PRIVATE,

        PLAINTEXT_PUBLIC,

        UNKNOWN
    }

    data class Verdict(
        val runnable: Boolean,
        val wire: Wire,
        val reason: String,
        val detail: String = "",
        val note: String = ""
    ) {
        val refused: Boolean get() = !runnable
        val cleartext: Boolean get() = wire == Wire.PLAINTEXT_PUBLIC || wire == Wire.PLAINTEXT_PRIVATE
    }

    const val PLAINTEXT_REFUSAL = "Unsupported VLESS • pick a server with TLS/REALITY"

    const val PLAINTEXT_PUBLIC_NOTE = "Unencrypted public node • Marble dials it, your provider can read it"

    const val PLAINTEXT_PRIVATE_NOTE = "Unencrypted node on a private network"

    const val PLAINTEXT_POLICY_ENV = "MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND"

    const val CORE_GAP_TRANSPORT_REMOVED =
        "Xray removed the HTTP/QUIC transports this node speaks • sing-box extended runs them " +
            "(Settings → Tunnel core)"

    const val CORE_GAP_KCP_CAMOUFLAGE =
        "mKCP header camouflage is not implemented by the pinned Xray core • sing-box extended " +
            "runs it (Settings → Tunnel core)"

    const val CORE_GAP_VLESS_ENCRYPTION =
        "VLESS encryption value this core cannot parse • use none or a post-quantum " +
            "mlkem768x25519plus value (Settings → Servers → Edit config)"

    private val CORE_CHECKED_PROTOCOLS = setOf("vless", "trojan")

    private val INFRA_OUTBOUND_PROTOCOLS = setOf("freedom", "direct", "blackhole", "block", "dns", "loopback", "warp")

    private val TRANSPORT_SECURITY = setOf("tls", "reality")

    fun verdict(profile: ProxyProfile, engine: CoreEngine, settings: AppSettings): Verdict =
        when (val wire = wire(profile)) {
            Wire.PLAINTEXT_PUBLIC ->
                if (settings.allowUnencryptedPublicOutbound) {
                    Verdict(
                        runnable = true,
                        wire = wire,
                        reason = "plaintext-accepted",
                        detail = cleartextDetail(engine),
                        note = PLAINTEXT_PUBLIC_NOTE
                    )
                } else {
                    Verdict(
                        runnable = false,
                        wire = wire,
                        reason = "plaintext-prohibited",
                        detail = "$PLAINTEXT_REFUSAL • or turn on Settings → Engine → " +
                            "Dial unencrypted nodes"
                    )
                }

            Wire.PLAINTEXT_PRIVATE ->
                Verdict(
                    runnable = true,
                    wire = wire,
                    reason = "private-cleartext",
                    detail = "The destination is private under the core's own matcher, so no " +
                        "transport security is required for it.",
                    note = PLAINTEXT_PRIVATE_NOTE
                )

            Wire.POST_QUANTUM -> Verdict(true, wire, "post-quantum-vless")
            Wire.ENCRYPTED -> Verdict(true, wire, "transport-secured")
            Wire.UNKNOWN -> Verdict(true, wire, "not-core-checked")
        }

    fun wire(profile: ProxyProfile): Wire {
        val document = profile.configJson.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?: return wireFromProfileFields(profile)

        if (NativeSingBoxConfig.isNative(document)) return wireFromNative(document)

        val outbounds = document.optJSONArray("outbounds") ?: return wireFromProfileFields(profile)
        var best: Wire? = null
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            val protocol = outbound.optString("protocol").trim().lowercase()
            if (protocol.isEmpty()) continue
            if (protocol in INFRA_OUTBOUND_PROTOCOLS) continue
            val wire = if (protocol in CORE_CHECKED_PROTOCOLS) wireOfOutbound(outbound, protocol) else Wire.UNKNOWN
            if (best == null || exposureRank(wire) < exposureRank(best!!)) best = wire
        }
        return best ?: wireFromProfileFields(profile)
    }

    private fun exposureRank(wire: Wire): Int = when (wire) {
        Wire.PLAINTEXT_PUBLIC -> 0
        Wire.PLAINTEXT_PRIVATE -> 1
        Wire.UNKNOWN -> 4
        Wire.POST_QUANTUM -> 2
        Wire.ENCRYPTED -> 3
    }

    internal fun wireOfOutbound(outbound: JSONObject, protocol: String = ""): Wire {
        val resolvedProtocol = protocol.ifBlank { outbound.optString("protocol").trim().lowercase() }
        val stream = outbound.optJSONObject("streamSettings")
        val security = stream?.optString("security").orEmpty().trim().lowercase().ifBlank { "none" }

        if (security in TRANSPORT_SECURITY) return Wire.ENCRYPTED

        val settings = outbound.optJSONObject("settings")
        if (resolvedProtocol == "vless" && isPostQuantumEncryption(vlessEncryption(settings))) {
            return Wire.POST_QUANTUM
        }

        if (resolvedProtocol == "vless") {
            val encryption = vlessEncryption(settings)
            if (encryption.isNotBlank() && encryption != "none") return Wire.ENCRYPTED
        }
        return if (CorePrivateEndpoint.matches(endpointHost(outbound, settings))) {
            Wire.PLAINTEXT_PRIVATE
        } else {
            Wire.PLAINTEXT_PUBLIC
        }
    }

    internal fun isPostQuantumEncryption(encryption: String): Boolean {
        val parts = encryption.trim().split(".")
        if (parts.size < 4) return false
        if (parts[0] != "mlkem768x25519plus") return false
        if (parts[1] !in setOf("native", "xorpub", "random")) return false
        if (parts[2] !in setOf("1rtt", "0rtt")) return false
        for (index in 3 until parts.size) {
            val token = parts[index]

            if (token.length >= 20) {
                val decoded = rawUrlBase64ByteLength(token)
                if (decoded != 32 && decoded != 1184) return false
            }
        }
        return true
    }

    internal fun rawUrlBase64ByteLength(token: String): Int {
        if (token.isEmpty()) return -1
        for (c in token) {
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
            if (!ok) return -1
        }
        return token.length * 6 / 8
    }

    private fun vlessEncryption(settings: JSONObject?): String {
        settings ?: return ""
        settings.optString("encryption").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val user = settings.optJSONArray("vnext")
            ?.optJSONObject(0)
            ?.optJSONArray("users")
            ?.optJSONObject(0)
        return user?.optString("encryption").orEmpty().trim()
    }

    private fun endpointHost(outbound: JSONObject, settings: JSONObject?): String {
        outbound.optString("address").trim().takeIf { it.isNotEmpty() }?.let { return it }
        settings ?: return ""
        sequenceOf(
            settings.optString("address").trim(),
            settings.optString("server").trim(),
            settings.optJSONArray("vnext")?.optJSONObject(0)?.optString("address").orEmpty().trim(),
            settings.optJSONArray("servers")?.optJSONObject(0)?.optString("address").orEmpty().trim()
        ).firstOrNull { it.isNotEmpty() }?.let { return it }
        return ""
    }

    private fun wireFromProfileFields(profile: ProxyProfile): Wire {
        val security = profile.security.trim().lowercase()

        if (security in TRANSPORT_SECURITY || security == "xtls") return Wire.ENCRYPTED
        if (profile.scheme.trim().lowercase() !in CORE_CHECKED_PROTOCOLS) return Wire.UNKNOWN
        return if (CorePrivateEndpoint.matches(profile.host)) Wire.PLAINTEXT_PRIVATE else Wire.PLAINTEXT_PUBLIC
    }

    private fun wireFromNative(document: JSONObject): Wire = runCatching {
        val outbounds = NativeSingBoxConfig.outbounds(NativeSingBoxConfig.root(document))
        var best: Wire? = null
        outbounds.forEach { outbound ->
            val type = outbound.optString("type").trim().lowercase()
            if (type !in setOf("vless", "trojan")) return@forEach
            val tls = outbound.optJSONObject("tls")
            val enabled = tls?.optBoolean("enabled", false) == true || tls?.has("reality") == true
            val server = outbound.optString("server").trim()
            val wire = when {
                enabled -> Wire.ENCRYPTED
                CorePrivateEndpoint.matches(server) -> Wire.PLAINTEXT_PRIVATE
                else -> Wire.PLAINTEXT_PUBLIC
            }
            if (best == null || exposureRank(wire) < exposureRank(best!!)) best = wire
        }
        best ?: Wire.UNKNOWN
    }.getOrDefault(Wire.UNKNOWN)

    private fun cleartextDetail(engine: CoreEngine): String = when (engine) {
        CoreEngine.XRAY ->
            "The pinned Xray core ships with MarbleNG's outbound-policy patch, so a public " +
                "VLESS/Trojan node without TLS is dialled instead of being refused at preflight. " +
                "The payload is not encrypted."
        CoreEngine.SINGBOX ->
            "sing-box extended has no transport-security requirement for these outbounds, so the " +
                "node is dialled as written. The payload is not encrypted."
    }

    fun dialsPlaintextPublicNodes(settings: AppSettings): Boolean = settings.allowUnencryptedPublicOutbound

    fun coreGapIssue(profile: ProxyProfile, engine: CoreEngine): String? {
        if (engine != CoreEngine.XRAY) return null
        val document = profile.configJson.trim()
            .takeIf { it.startsWith("{") }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        var method = ""
        var headerType = ""
        val outbounds = document?.optJSONArray("outbounds")
        if (outbounds != null) {
            for (index in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(index) ?: continue
                val stream = outbound.optJSONObject("streamSettings") ?: continue
                if (method.isEmpty()) {
                    val resolved = stream.optString("method")
                        .ifBlank { stream.optString("network") }
                        .trim()
                        .lowercase()
                    if (resolved.isNotEmpty()) method = resolved
                }
                if (headerType.isEmpty()) {
                    val header = stream.optJSONObject("kcpSettings")?.optJSONObject("header")
                        ?: stream.optJSONObject("rawSettings")?.optJSONObject("header")
                    val type = header?.optString("type").orEmpty().trim().lowercase()
                    if (type.isNotEmpty()) headerType = type
                }
            }
        }
        if (method.isEmpty()) {
            method = profile.transport.trim().lowercase()
        }
        if (headerType.isEmpty()) {
            headerType = ShareLinkParams.ofRawLink(profile.raw).get("headerType").trim().lowercase()
        }
        return when {
            method in setOf("http", "h2", "h3", "quic") -> CORE_GAP_TRANSPORT_REMOVED
            method in setOf("mkcp", "kcp") && headerType.isNotEmpty() && headerType != "none" ->
                CORE_GAP_KCP_CAMOUFLAGE
            vlessEncryptionUnparseable(document) -> CORE_GAP_VLESS_ENCRYPTION
            else -> null
        }
    }

    private fun vlessEncryptionUnparseable(document: JSONObject?): Boolean {
        val outbounds = document?.optJSONArray("outbounds") ?: return false
        for (index in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(index) ?: continue
            if (outbound.optString("protocol").trim().lowercase() != "vless") continue
            val settings = outbound.optJSONObject("settings") ?: continue
            val declared = settings.optString("encryption").trim().ifBlank {
                settings.optJSONArray("vnext")?.optJSONObject(0)?.optJSONArray("users")
                    ?.optJSONObject(0)?.optString("encryption").orEmpty().trim()
            }
            if (declared.isEmpty()) continue
            if (declared.lowercase() in XrayConfigRepairs.VLESS_MEANINGLESS_ENCRYPTION) continue
            if (isPostQuantumEncryption(declared)) continue
            return true
        }
        return false
    }
}

object CorePrivateEndpoint {
    private fun v4(first: Int, second: Int, third: Int, fourth: Int): Int =
        (first shl 24) or (second shl 16) or (third shl 8) or fourth

    private val PRIVATE_V4: List<Pair<Int, Int>> = listOf(
        v4(0, 0, 0, 0) to 8,
        v4(10, 0, 0, 0) to 8,
        v4(100, 64, 0, 0) to 10,
        v4(127, 0, 0, 0) to 8,
        v4(169, 254, 0, 0) to 16,
        v4(172, 16, 0, 0) to 12,
        v4(192, 0, 0, 0) to 24,
        v4(192, 0, 2, 0) to 24,
        v4(192, 88, 99, 0) to 24,
        v4(192, 168, 0, 0) to 16,
        v4(198, 18, 0, 0) to 15,
        v4(198, 51, 100, 0) to 24,
        v4(203, 0, 113, 0) to 24,
        v4(224, 0, 0, 0) to 3
    )

    private val PRIVATE_DOMAIN_SUFFIXES = setOf(
        "lan", "localdomain", "example", "invalid", "localhost", "test", "local", "home.arpa", "internal"
    )

    private val DOTLESS = Regex("^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$")

    fun matches(raw: String): Boolean {
        val host = raw.trim().removePrefix("[").removeSuffix("]").lowercase().trimEnd('.')
        if (host.isEmpty()) return false

        parseIpv4(host)?.let { return isPrivateIpv4Octets(it) }
        parseIpv6(host)?.let { return isPrivateIpv6Bytes(it) }

        parseMappedIpv4(host)?.let { return isPrivateIpv4Octets(it) }

        return isPrivateDomainName(host)
    }

    fun parseIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (index in 0 until 4) {
            val token = parts[index]
            if (token.isEmpty() || token.length > 3) return null
            if (!token.all { it in '0'..'9' }) return null
            val value = token.toIntOrNull() ?: return null
            if (value > 255) return null
            octets[index] = value
        }
        return octets
    }

    fun isPrivateIpv4Octets(octets: IntArray): Boolean {
        if (octets.size != 4) return false
        val value = (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
        return PRIVATE_V4.any { (base, bits) ->
            val mask = if (bits >= 32) -1 else (-1 shl (32 - bits))
            (value and mask) == (base and mask)
        }
    }

    fun parseIpv6(host: String): ByteArray? {
        var value = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (!value.contains(':')) return null

        val lastColon = value.lastIndexOf(':')
        if (lastColon >= 0 && lastColon < value.length - 1) {
            val tail = value.substring(lastColon + 1)
            if (tail.contains('.')) {
                val v4 = parseIpv4(tail) ?: return null
                val head = (v4[0] shl 8) or v4[1]
                val rest = (v4[2] shl 8) or v4[3]
                value = value.substring(0, lastColon + 1) + head.toString(16) + ":" + rest.toString(16)
            }
        }

        val groups = ArrayList<Int>(8)
        val double = value.indexOf("::")
        if (double >= 0) {
            if (value.indexOf("::", double + 1) >= 0) return null
            val left = value.substring(0, double)
            val right = value.substring(double + 2)
            val leftGroups = if (left.isEmpty()) emptyList() else left.split(':')
            val rightGroups = if (right.isEmpty()) emptyList() else right.split(':')
            if (leftGroups.any { parseHexGroup(it) == null }) return null
            if (rightGroups.any { parseHexGroup(it) == null }) return null
            val missing = 8 - leftGroups.size - rightGroups.size
            if (missing < 0) return null
            leftGroups.forEach { groups += parseHexGroup(it) ?: return null }
            repeat(missing) { groups += 0 }
            rightGroups.forEach { groups += parseHexGroup(it) ?: return null }
        } else {
            val parts = value.split(':')
            if (parts.size != 8) return null
            parts.forEach { groups += parseHexGroup(it) ?: return null }
        }
        if (groups.size != 8) return null

        val bytes = ByteArray(16)
        groups.forEachIndexed { index, group ->
            bytes[index * 2] = ((group ushr 8) and 0xFF).toByte()
            bytes[index * 2 + 1] = (group and 0xFF).toByte()
        }
        return bytes
    }

    private fun parseHexGroup(token: String): Int? {
        if (token.isEmpty() || token.length > 4) return null
        if (!token.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return token.toInt(16)
    }

    private fun parseMappedIpv4(host: String): IntArray? {
        if (!host.startsWith("::ffff:") && !host.startsWith("::FFFF:")) return null
        val tail = host.substringAfterLast(':')
        return parseIpv4(tail)
    }

    fun isPrivateIpv6Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false

        val highAllZero = (0 until 15).all { bytes[it].toInt() == 0 }
        if (highAllZero && (bytes[15].toInt() and 0xFF) <= 1) return true

        if ((bytes[0].toInt() and 0xFE) == 0xFC) return true

        if ((bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80) return true

        if ((bytes[0].toInt() and 0xFF) == 0xFF) return true

        if (isIpv4Mapped(bytes)) {
            return isPrivateIpv4Octets(
                intArrayOf(
                    bytes[12].toInt() and 0xFF,
                    bytes[13].toInt() and 0xFF,
                    bytes[14].toInt() and 0xFF,
                    bytes[15].toInt() and 0xFF
                )
            )
        }
        return false
    }

    private fun isIpv4Mapped(bytes: ByteArray): Boolean =
        (0 until 10).all { bytes[it].toInt() == 0 } &&
            (bytes[10].toInt() and 0xFF) == 0xFF &&
            (bytes[11].toInt() and 0xFF) == 0xFF

    fun isPrivateDomainName(host: String): Boolean {
        val name = host.trim().lowercase().trimEnd('.')
        if (name.isEmpty()) return false
        if (PRIVATE_DOMAIN_SUFFIXES.any { name == it || name.endsWith(".$it") }) return true
        return DOTLESS.matches(name)
    }

    fun classify(raw: String): String = if (matches(raw)) "private" else "public"
}
