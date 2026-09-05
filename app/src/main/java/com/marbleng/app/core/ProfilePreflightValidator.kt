package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structural preflight validation for proxy profiles.
 *
 * Problem addressed (MARBLE_PROFILE_QUARANTINE_V1):
 * A structurally broken profile (for example a VLESS/TLS config that fails Xray's config-load /
 * xray-start validation, like the "Turkey 4-All" profile seen in logs) previously joined the
 * benchmark / Smart Rank pool, burned a probe slot, and could poison ranking or user selection
 * even though it could never connect.
 *
 * This validator cheaply checks the emitted Xray JSON for the conditions that make Xray reject a
 * config at startup (missing outbounds, a dialing outbound with no address/port, a VLESS/TLS or
 * REALITY outbound missing its required TLS serverName, a port outside the valid range, etc.) and
 * marks the profile INVALID so it is quarantined before Smart Rank ever probes it.
 *
 * The validator is intentionally conservative about what it *allows*: it only rejects clearly
 * broken configs and never second-guesses legitimate transport choices, so it cannot flag healthy
 * censorship-resistant profiles (VLESS + REALITY, etc.).
 */
object ProfilePreflightValidator {

    /** Preflight outcome. */
    enum class Verdict {
        /** Structurally sound; allowed to join benchmark / rank selection. */
        VALID,
        /** Structurally broken; quarantined before benchmark / rank selection. */
        INVALID
    }

    /** Machine-readable, stable reason code plus human detail. */
    data class PreflightVerdict(
        val verdict: Verdict,
        val reason: String,
        val detail: String = ""
    ) {
        val valid: Boolean get() = verdict == Verdict.VALID
    }

    /** Minimal Xray outbound protocols that actually dial a server and must have an address+port. */
    private val DIALING_PROTOCOLS = setOf(
        "vless", "vmess", "trojan", "shadowsocks", "ss", "socks", "http", "tuic", "hysteria", "hysteria2", "hy2", "wireguard"
    )

    /** TLS/REALITY security schemes that require a serverName to be present to validate. */
    private val TLS_REQUIRING_SECURITY = setOf("tls", "reality")

    /**
     * Validate a single profile.
     *
     * MARBLE_SMART_RANK_V90: before a node is quarantined for a missing address, its address is
     * cross-checked against the local cache + fresh subscription through
     * [ProfileAddressCrossCheck], so the old blanket `missing-address` verdict becomes one of the
     * precise reasons `malformed-config`, `stale-subscription` or `address-resolved-but-invalid`.
     */
    fun validate(profile: ProxyProfile): PreflightVerdict =
        validate(profile, ProfileAddressCrossCheck.CrossCheckSources())

    /**
     * Validate a single profile with fresh-subscription evidence available for address
     * cross-checking.
     */
    fun validate(
        profile: ProxyProfile,
        sources: ProfileAddressCrossCheck.CrossCheckSources
    ): PreflightVerdict {
        // SS/SSR hostname-only profiles may carry no emitted JSON yet; allow them through so the
        // engine (XrayManager) is the judge, but flag them for re-check.
        if (profile.configJson.isBlank()) {
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
            DIALING_PROTOCOLS.contains(protocol) && out.optString("tag", "").isNotBlank()
        }

        // A config that only defines routing/free/blackhole outbounds has nothing that can carry
        // user traffic. Treat as invalid (nothing to rank).
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
                // MARBLE_SMART_RANK_V90: cross-check the address against the local cache and the
                // fresh subscription before quarantining, and report a precise reason instead of
                // the blanket "missing-address" (which wrongly quarantined 13 of 14 nodes when the
                // address actually existed but the emitted JSON could not be parsed).
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

            // VLESS/TLS and REALITY outbounds that omit the TLS serverName will be rejected by
            // Xray at config-load (seen as the "Turkey 4-All" xray-start invalid-config family).
            val stream = dialer.optJSONObject("streamSettings")
            val security = stream?.optString("security", "")?.lowercase()
            if (stream != null && security != null && security in TLS_REQUIRING_SECURITY) {
                val tlsSettings = stream.optJSONObject("tlsSettings")
                val serverName = tlsSettings?.optString("serverName", "").orEmpty()
                if (serverName.isBlank()) {
                    return PreflightVerdict(
                        Verdict.INVALID,
                        "vless-tls-missing-servername",
                        "outbound $protocol security=$security has no tls serverName; Xray rejects at startup"
                    )
                }
            }
        }

        return PreflightVerdict(Verdict.VALID, "structurally-valid")
    }

    private fun resolveAddress(protocol: String, outbound: JSONObject, settings: JSONObject?): String? {
        // Direct address field (used by some wireguard/hysteria outbounds).
        outbound.optString("address").takeIf { it.isNotBlank() }?.let { return it }

        val settingsObject = settings ?: return null
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
            "tuic" -> {
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
        return when (protocol) {
            "vless", "vmess", "trojan", "socks", "http", "shadowsocks", "ss" -> {
                val vnext = settingsObject.optJSONArray("vnext")
                    ?: settingsObject.optJSONArray("servers")
                if (vnext == null || vnext.length() == 0) null
                else vnext.optJSONObject(0)?.optInt("port", 0)?.takeIf { it in 1..65535 }
            }
            "wireguard" -> outbound.optInt("port", 0).takeIf { it in 1..65535 }
            "tuic" -> settingsObject.optInt("port", 0).takeIf { it in 1..65535 }
            "hysteria", "hysteria2", "hy2" -> {
                settingsObject.optInt("port", 0).takeIf { it in 1..65535 }
                    ?: settingsObject.optInt("server_port", 0).takeIf { it in 1..65535 }
            }
            else -> null
        }
    }

    /** Split a candidate list into valid and quarantined (invalid) profiles. */
    fun partition(profiles: List<ProxyProfile>): Pair<List<ProxyProfile>, List<Pair<ProxyProfile, PreflightVerdict>>> =
        partition(profiles, ProfileAddressCrossCheck.CrossCheckSources())

    /**
     * Split a candidate list into valid and quarantined (invalid) profiles, cross-checking each
     * missing-address candidate against [sources] (local cache + fresh subscription).
     */
    fun partition(
        profiles: List<ProxyProfile>,
        sources: ProfileAddressCrossCheck.CrossCheckSources
    ): Pair<List<ProxyProfile>, List<Pair<ProxyProfile, PreflightVerdict>>> {
        val valid = mutableListOf<ProxyProfile>()
        val invalid = mutableListOf<Pair<ProxyProfile, PreflightVerdict>>()
        profiles.forEach { profile ->
            val verdict = validate(profile, sources)
            if (verdict.valid) valid += profile else invalid += profile to verdict
        }
        return valid to invalid
    }

    /** Validate many profiles, returning a map of profileId -> verdict for diagnostics. */
    fun validateAll(profiles: List<ProxyProfile>): Map<String, PreflightVerdict> =
        profiles.associate { it.id to validate(it) }

    /** Render a machine-readable preflight block for diagnostics. */
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

    /** True when the profile should be excluded from ranking. */
    fun isQuarantined(profile: ProxyProfile, verdict: PreflightVerdict?): Boolean =
        verdict == null || !verdict.valid

    /**
     * MARBLE_SMART_RANK_V90: true when more than half of the candidate pool was quarantined. A
     * majority quarantine means the subscription is stale or broken, not that the nodes are dead —
     * Rank must stop and ask the user to refresh instead of silently finishing empty.
     */
    fun isMajorityQuarantined(invalid: Int, total: Int): Boolean =
        total > 0 && invalid * 2 > total
}
