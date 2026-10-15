package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject

object ProfilePreflightValidator {
    enum class Verdict {
        VALID,
        INVALID
    }

    data class PreflightVerdict(
        val verdict: Verdict,
        val reason: String,
        val detail: String = ""
    ) {
        val valid: Boolean get() = verdict == Verdict.VALID
    }

    private val DIALING_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "ss", "socks", "http", "tuic", "anytls", "hysteria", "hysteria2", "hy2", "wireguard"
    )

    private val SINGBOX_LINK_ONLY_PROTOCOLS = setOf("tuic", "anytls")

    private val TLS_REQUIRING_SECURITY = setOf("tls", "reality")

    fun validate(profile: ProxyProfile): PreflightVerdict =
        validate(profile, ProfileAddressCrossCheck.CrossCheckSources())

    fun validate(
        profile: ProxyProfile,
        sources: ProfileAddressCrossCheck.CrossCheckSources = ProfileAddressCrossCheck.CrossCheckSources(),
        engine: CoreEngine = CoreEngine.XRAY,
        settings: AppSettings = AppSettings()
    ): PreflightVerdict {
        if (AddressFamilyPolicy.excludedIpv6Endpoint(profile.host, settings)) {
            return PreflightVerdict(Verdict.INVALID, "ipv6-disabled", AddressFamilyPolicy.IPV6_LITERAL_DISABLED)
        }
        if (AddressFamilyPolicy.excludedIpv4Endpoint(profile.host, settings)) {
            return PreflightVerdict(Verdict.INVALID, "ipv4-disabled", AddressFamilyPolicy.IPV4_LITERAL_DISABLED)
        }

        if (profile.configJson.isBlank()) {
            if (profile.scheme.lowercase() in SINGBOX_LINK_ONLY_PROTOCOLS &&
                SingBoxConfigBuilder.shareLink(profile) != null
            ) {
                return PreflightVerdict(
                    Verdict.VALID,
                    "singbox-parser-link",
                    "sing-box extended's parser runs the stored share link; no Xray JSON needed"
                )
            }

            val link = SingBoxConfigBuilder.shareLink(profile)

            val linkOnly = engine == CoreEngine.SINGBOX && profile.raw.isNotBlank() &&
                link != null && !SingBoxConfigBuilder.linkCarriesPin(link)
            if (linkOnly) {
                return PreflightVerdict(
                    Verdict.VALID,
                    "singbox-parser-link",
                    "sing-box extended's parser runs the stored share link; no Xray JSON needed"
                )
            }
            return PreflightVerdict(
                Verdict.INVALID,
                "config-json-blank",
                "profile has no emitted Xray JSON and cannot be validated"
            )
        }

        val root = try {
            JSONObject(profile.configJson)
        } catch (e: Exception) {
            return PreflightVerdict(
                Verdict.INVALID,
                "invalid-json",
                e.message.orEmpty().take(160)
            )
        }

        if (NativeSingBoxConfig.isNative(root)) {
            return try {
                NativeSingBoxConfig.outbounds(root)
                PreflightVerdict(Verdict.VALID, "native-singbox", "The selected core validates protocol capabilities at runtime")
            } catch (error: Exception) {
                PreflightVerdict(Verdict.INVALID, "config-unsupported", error.message.orEmpty())
            }
        }

        val outbounds = try {
            root.optJSONArray("outbounds")
        } catch (_: Exception) {
            null
        }
        if (outbounds == null || outbounds.length() == 0) {
            return PreflightVerdict(
                Verdict.INVALID,
                "no-outbounds",
                "config has no outbounds; Xray would fail at startup"
            )
        }

        val dialers = (0 until outbounds.length()).mapNotNull { index ->
            try {
                outbounds.optJSONObject(index)
            } catch (_: Exception) {
                null
            }
        }.filter { out ->
            val protocol = out.optString("protocol", "").lowercase()
            DIALING_PROTOCOLS.contains(protocol)
        }

        if (dialers.isEmpty()) {
            return PreflightVerdict(
                Verdict.INVALID,
                "no-dialing-outbound",
                "config has outbounds but none dial a server"
            )
        }

        for (dialer in dialers) {
            val protocol = dialer.optString("protocol", "").lowercase()
            val settings = dialer.optJSONObject("settings")
            val address = resolveAddress(protocol, dialer, settings)
            if (address.isNullOrBlank()) {
                val cross = ProfileAddressCrossCheck.crossCheck(profile, profile.configJson, sources)
                return PreflightVerdict(
                    Verdict.INVALID,
                    cross.failure.code,
                    cross.detail
                )
            }

            val port = resolvePort(protocol, dialer, settings)
            if (port == null || port !in 1..65535) {
                return PreflightVerdict(
                    Verdict.INVALID,
                    "invalid-port",
                    "outbound $protocol has invalid port $port"
                )
            }

            val stream = dialer.optJSONObject("streamSettings")
            val security = stream?.optString("security", "")?.lowercase()
            if (stream != null && security != null && security in TLS_REQUIRING_SECURITY) {
                val tlsSettings = stream.optJSONObject(if (security == "reality") "realitySettings" else "tlsSettings")
                val serverName = tlsSettings?.optString("serverName", "").orEmpty()
                if (serverName.isBlank() && security == "reality") {
                    return PreflightVerdict(
                        Verdict.INVALID,
                        "vless-tls-missing-servername",
                        "outbound $protocol security=$security has no tls serverName; Xray rejects at startup"
                    )
                }
            }
        }

        CoreConfigSuperset.coreGapIssue(profile, engine)?.let { gap ->
            return PreflightVerdict(Verdict.INVALID, "core-gap", gap)
        }
        val verdict = CoreConfigSuperset.verdict(profile, engine, settings)
        if (!verdict.runnable) {
            return PreflightVerdict(Verdict.INVALID, verdict.reason, verdict.detail)
        }

        return PreflightVerdict(
            Verdict.VALID,
            if (verdict.note.isEmpty()) "structurally-valid" else "structurally-valid-unencrypted",
            if (verdict.note.isEmpty()) "structurally-valid" else verdict.note
        )
    }

    private fun resolveAddress(protocol: String, outbound: JSONObject, settings: JSONObject?): String? {
        outbound.optString("address").takeIf { it.isNotBlank() }?.let { return it }

        val settingsObject = settings ?: return null
        listOf("address", "server", "host").firstNotNullOfOrNull { key ->
            settingsObject.optString(key).takeIf { it.isNotBlank() }
        }?.let { return it }
        return when (protocol) {
            "vless", "vmess", "trojan", "socks", "http", "shadowsocks", "ss" -> {
                val vnext = settingsObject.optJSONArray("vnext")
                    ?: settingsObject.optJSONArray("servers")
                if (vnext == null || vnext.length() == 0) null
                else vnext.optJSONObject(0)?.optString("address", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: vnext.optJSONObject(0)?.optString("host", "")?.takeIf { it.isNotBlank() }
            }
            "wireguard" -> {
                settingsObject.optString("address", "").takeIf { it.isNotBlank() }
            }
            "tuic", "anytls" -> {
                settingsObject.optString("server", "").takeIf { it.isNotBlank() }
            }
            "hysteria", "hysteria2", "hy2" -> {
                settingsObject.optString("address", "")
                    .ifBlank { settingsObject.optString("server", "") }
                    .takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun resolvePort(protocol: String, outbound: JSONObject, settings: JSONObject?): Int? {
        val directPort = outbound.optInt("port", 0)
        if (directPort in 1..65535) return directPort
        val directPortObj = outbound.opt("port")
        if (directPortObj is JSONObject) {
            directPortObj.optInt("value", 0).takeIf { it in 1..65535 }?.let { return it }
        }

        val settingsObject = settings ?: return null
        settingsObject.optInt("port").takeIf { it in 1..65535 }?.let { return it }
        settingsObject.optInt("server_port").takeIf { it in 1..65535 }?.let { return it }
        return when (protocol) {
            "vless", "vmess", "trojan", "socks", "http", "shadowsocks", "ss" -> {
                val vnext = settingsObject.optJSONArray("vnext")
                    ?: settingsObject.optJSONArray("servers")
                if (vnext == null || vnext.length() == 0) null
                else vnext.optJSONObject(0)?.optInt("port", 0)?.takeIf { it in 1..65535 }
            }
            "wireguard" -> outbound.optInt("port", 0).takeIf { it in 1..65535 }
            "tuic", "anytls" -> settingsObject.optInt("port", 0).takeIf { it in 1..65535 }
            "hysteria", "hysteria2", "hy2" -> {
                settingsObject.optInt("port", 0).takeIf { it in 1..65535 }
                    ?: settingsObject.optInt("server_port", 0).takeIf { it in 1..65535 }
            }
            else -> null
        }
    }

    fun partition(profiles: List<ProxyProfile>): Pair<List<ProxyProfile>, List<Pair<ProxyProfile, PreflightVerdict>>> =
        partition(profiles, ProfileAddressCrossCheck.CrossCheckSources())

    fun partition(
        profiles: List<ProxyProfile>,
        engine: CoreEngine,
        settings: AppSettings
    ): Pair<List<ProxyProfile>, List<Pair<ProxyProfile, PreflightVerdict>>> =
        partition(profiles, ProfileAddressCrossCheck.CrossCheckSources(), engine, settings)

    fun partition(
        profiles: List<ProxyProfile>,
        sources: ProfileAddressCrossCheck.CrossCheckSources,
        engine: CoreEngine = CoreEngine.XRAY,
        settings: AppSettings = AppSettings()
    ): Pair<List<ProxyProfile>, List<Pair<ProxyProfile, PreflightVerdict>>> {
        val valid = mutableListOf<ProxyProfile>()
        val invalid = mutableListOf<Pair<ProxyProfile, PreflightVerdict>>()
        profiles.forEach { profile ->
            val verdict = validate(profile, sources, engine, settings)
            if (verdict.valid) valid += profile else invalid += profile to verdict
        }
        return valid to invalid
    }

    fun validateAll(
        profiles: List<ProxyProfile>,
        engine: CoreEngine = CoreEngine.XRAY,
        settings: AppSettings = AppSettings()
    ): Map<String, PreflightVerdict> =
        profiles.associate { it.id to validate(it, ProfileAddressCrossCheck.CrossCheckSources(), engine, settings) }

    fun renderMachineReadable(verdicts: Map<String, PreflightVerdict>): String {
        val invalid = verdicts.filter { !it.value.valid }
        return buildString {
            append("preflight.valid=").append(verdicts.size - invalid.size)
            append(";preflight.invalid=").append(invalid.size)
            if (invalid.isNotEmpty()) {
                append(";preflight.quarantined=").append(
                    invalid.keys.joinToString(",") { it.take(24) }
                )
                append(";preflight.reasons=").append(
                    invalid.values.groupBy { it.reason }
                        .map { (r, list) -> "$r:${list.size}" }
                        .joinToString(",")
                )
            }
        }
    }

    fun isQuarantined(profile: ProxyProfile, verdict: PreflightVerdict?): Boolean =
        verdict == null || !verdict.valid

    fun isMajorityQuarantined(invalid: Int, total: Int): Boolean =
        total > 0 && invalid * 2 > total
}
