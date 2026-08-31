package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject

/**
 * Address cross-check used by profile preflight before a node is quarantined.
 *
 * MARBLE_SMART_RANK_V90 — root-cause fix for the "13 of 14 nodes quarantined as missing-address"
 * failure: the old [ProfilePreflightValidator] quarantined a node with the blanket reason
 * `missing-address` the moment the emitted Xray JSON did not expose a server address. Under a
 * severe-filtration subscription refresh, the address frequently still exists in one of the other
 * sources (the stored `raw` URI, the profile `host`/`port` fields, or the freshly-refreshed
 * subscription) and only the emitted JSON parse failed.
 *
 * Before quarantining, the validator now cross-checks the address against at least two sources —
 * the local cache (`host`/`port` + `raw` URI) and the fresh subscription — and reports a precise,
 * machine-readable reason:
 *
 *  - [FailureReason.MALFORMED_CONFIG]          — emitted JSON is present but structurally broken;
 *  - [FailureReason.STALE_SUBSCRIPTION]        — address only survives in the stale local copy;
 *  - [FailureReason.ADDRESS_RESOLVED_BUT_INVALID] — the address exists in two sources but the
 *                                                emitted config could not use it;
 *  - [FailureReason.MISSING_ADDRESS]           — genuinely absent everywhere (real missing node).
 *
 * Pure and dependency-free so the whole 14-node / 13-missing-address scenario can be reproduced
 * deterministically in a JVM unit test and in the [ProfileAddressCrossCheck] simulation.
 */
object ProfileAddressCrossCheck {

    /** Where a piece of address evidence came from. */
    enum class Source { CONFIG_JSON, PROFILE_FIELDS, RAW_URI, FRESH_SUBSCRIPTION }

    /** One address observation from a single source. */
    data class Evidence(
        val source: Source,
        val address: String?,
        val port: Int?,
        /** True when the evidence comes from a freshly-refreshed subscription, not the local cache. */
        val fresh: Boolean
    ) {
        val hasAddress: Boolean get() = !address.isNullOrBlank()
        val hasValidPort: Boolean get() = port != null && port in 1..65535
        val usable: Boolean get() = hasAddress && hasValidPort
    }

    /**
     * Precise quarantine reason. [code] is the stable machine token surfaced in diagnostics and
     * logs; [userMessage] is the end-user readable explanation.
     */
    enum class FailureReason(val code: String, val userMessage: String) {
        MISSING_ADDRESS(
            "missing-address",
            "This profile has no server address in any source — it may have been removed upstream."
        ),
        MALFORMED_CONFIG(
            "malformed-config",
            "This profile's Xray config is malformed and its server address could not be read."
        ),
        STALE_SUBSCRIPTION(
            "stale-subscription",
            "This profile's data is stale — tap Refresh Subscriptions and Rank again."
        ),
        ADDRESS_RESOLVED_BUT_INVALID(
            "address-resolved-but-invalid",
            "This profile has an address, but the generated config could not use it."
        )
    }

    /** Fresh-subscription evidence supplied by the caller (AppRepository). */
    data class CrossCheckSources(
        /** Freshly-parsed profiles from the most recent subscription refresh. */
        val freshSubscriptionProfiles: List<ProxyProfile> = emptyList(),
        /** Raw link text of the fresh subscription (one share link per line). */
        val freshSubscriptionRaw: String = ""
    )

    /** Full cross-check outcome, kept deterministic for tests. */
    data class Result(
        val failure: FailureReason,
        val evidences: List<Evidence>,
        val foundAddress: String?,
        val detail: String
    )

    /**
     * Cross-check the address of [profile] against its emitted JSON, its local cache fields and
     * the fresh subscription, and classify the most precise reason the emitted config could not be
     * used.
     */
    fun crossCheck(
        profile: ProxyProfile,
        configJson: String,
        sources: CrossCheckSources
    ): Result {
        val evidences = mutableListOf<Evidence>()

        // 1) Emitted config JSON — the source the validator actually failed to read.
        val config = addressFromConfigJson(configJson)
        evidences += Evidence(Source.CONFIG_JSON, config.address, config.port, fresh = true)

        // 2) Local cache: stored host/port fields + the original share URI.
        val cachedHost = profile.host.trim().takeIf { it.isNotBlank() }
        val cachedPort = profile.port.takeIf { it in 1..65535 }
        evidences += Evidence(Source.PROFILE_FIELDS, cachedHost, cachedPort, fresh = false)

        val (rawHost, rawPort) = parseAuthority(profile.raw)
        if (rawHost != null) {
            evidences += Evidence(Source.RAW_URI, rawHost, rawPort, fresh = false)
        }

        // 3) Fresh subscription: the exact profile re-delivered by the last refresh, or a sibling
        //    node in the same subscription proving that the subscription DID deliver addresses.
        val freshMatch = sources.freshSubscriptionProfiles.firstOrNull { it.id == profile.id }
            ?: sources.freshSubscriptionProfiles.firstOrNull { sibling ->
                sibling.subscriptionId == profile.subscriptionId &&
                    sibling.host.isNotBlank() &&
                    sibling.port in 1..65535
            }
        if (freshMatch != null) {
            val host = freshMatch.host.trim().takeIf { it.isNotBlank() }
            val port = freshMatch.port.takeIf { it in 1..65535 }
            evidences += Evidence(Source.FRESH_SUBSCRIPTION, host, port, fresh = true)
        } else if (sources.freshSubscriptionRaw.isNotBlank()) {
            val (subHost, subPort) = parseAuthority(sources.freshSubscriptionRaw)
            if (subHost != null) {
                evidences += Evidence(Source.FRESH_SUBSCRIPTION, subHost, subPort, fresh = true)
            }
        }

        val localUsable = evidences.any {
            it.source in setOf(Source.PROFILE_FIELDS, Source.RAW_URI) && it.usable
        }
        val freshUsable = evidences.any {
            it.source == Source.FRESH_SUBSCRIPTION && it.usable
        }
        val foundAddress = evidences.firstOrNull { it.hasAddress }?.address

        val failure = when {
            config.malformed -> FailureReason.MALFORMED_CONFIG
            localUsable && freshUsable -> FailureReason.ADDRESS_RESOLVED_BUT_INVALID
            localUsable || freshUsable -> FailureReason.STALE_SUBSCRIPTION
            else -> FailureReason.MISSING_ADDRESS
        }

        val detail = buildString {
            append("config=")
            append(if (config.malformed) "malformed" else if (config.address != null) "address" else "no-address")
            append(";cache=")
            append(if (localUsable) "address" else "none")
            append(";subscription=")
            append(if (freshUsable) "address" else "none")
            if (foundAddress != null) append(";found=").append(foundAddress.take(80))
        }

        return Result(failure, evidences.toList(), foundAddress, detail)
    }

    /**
     * Convenience: map a cross-check result into a human-readable one-line message the user can
     * understand, suitable for logs and Snackbars.
     */
    fun oneLineMessage(profileName: String, result: Result): String =
        "${profileName.take(48)} • ${result.failure.userMessage}"

    private data class ConfigAddress(
        val address: String?,
        val port: Int?,
        val malformed: Boolean
    )

    /** Extract the first dialing-outbound address from emitted Xray JSON, flagging malformed config. */
    private fun addressFromConfigJson(configJson: String): ConfigAddress {
        if (configJson.isBlank()) return ConfigAddress(null, null, malformed = false)
        val root = try {
            JSONObject(configJson)
        } catch (_: Exception) {
            return ConfigAddress(null, null, malformed = true)
        }
        val outbounds = try {
            root.optJSONArray("outbounds")
        } catch (_: Exception) {
            return ConfigAddress(null, null, malformed = true)
        }
        if (outbounds == null || outbounds.length() == 0) {
            return ConfigAddress(null, null, malformed = false)
        }
        var malformed = false
        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.optJSONObject(i) ?: continue
            val settings: JSONObject? = try {
                val rawSettings = outbound.opt("settings")
                when (rawSettings) {
                    null -> null
                    is JSONObject -> rawSettings
                    else -> {
                        malformed = true
                        null
                    }
                }
            } catch (_: Exception) {
                malformed = true
                null
            }

            val address = outbound.optString("address").takeIf { it.isNotBlank() }
                ?: settings?.optJSONArray("vnext")?.optJSONObject(0)
                    ?.optString("address")?.takeIf { it.isNotBlank() }
                ?: settings?.optJSONArray("servers")?.optJSONObject(0)
                    ?.optString("address")?.takeIf { it.isNotBlank() }
                ?: settings?.optString("server")?.takeIf { it.isNotBlank() }

            if (address != null) {
                val port = outbound.optInt("port", 0).takeIf { it in 1..65535 }
                    ?: settings?.optJSONArray("vnext")?.optJSONObject(0)
                        ?.optInt("port", 0)?.takeIf { it in 1..65535 }
                    ?: settings?.optJSONArray("servers")?.optJSONObject(0)
                        ?.optInt("port", 0)?.takeIf { it in 1..65535 }
                    ?: settings?.optInt("port", 0)?.takeIf { it in 1..65535 }
                return ConfigAddress(address, port, malformed)
            }
        }
        return ConfigAddress(null, null, malformed)
    }

    /**
     * Parse the `host:port` authority out of a share link (`scheme://userinfo@host:port/path?...`).
     * Handles IPv6 literals in brackets. Returns (null, null) when no usable host is present.
     */
    private fun parseAuthority(raw: String): Pair<String?, Int?> {
        if (raw.isBlank()) return null to null
        val schemeEnd = raw.indexOf("://")
        val rest = if (schemeEnd >= 0) raw.substring(schemeEnd + 3) else raw
        val authority = rest.substringBefore('#').substringBefore('?').substringBefore('/')
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isBlank()) return null to null
        return parseHostPort(hostPort)
    }

    private fun parseHostPort(hostPort: String): Pair<String?, Int?> {
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null to null
            val host = hostPort.substring(1, close)
            val port = hostPort.substring(close + 1).removePrefix(":").toIntOrNull()
            return host to port
        }
        val idx = hostPort.lastIndexOf(':')
        if (idx > 0) {
            val host = hostPort.substring(0, idx)
            val port = hostPort.substring(idx + 1).toIntOrNull()
            return host to port
        }
        return hostPort to null
    }
}
