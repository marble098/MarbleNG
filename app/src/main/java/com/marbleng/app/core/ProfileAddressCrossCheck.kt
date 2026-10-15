package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject

object ProfileAddressCrossCheck {
    enum class Source { CONFIG_JSON, PROFILE_FIELDS, RAW_URI, FRESH_SUBSCRIPTION }

    data class Evidence(
        val source: Source,
        val address: String?,
        val port: Int?,
        val fresh: Boolean
    ) {
        val hasAddress: Boolean get() = !address.isNullOrBlank()
        val hasValidPort: Boolean get() = port != null && port in 1..65535
        val usable: Boolean get() = hasAddress && hasValidPort
    }

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

    data class CrossCheckSources(
        val freshSubscriptionProfiles: List<ProxyProfile> = emptyList(),
        val freshSubscriptionRaw: String = ""
    )

    data class Result(
        val failure: FailureReason,
        val evidences: List<Evidence>,
        val foundAddress: String?,
        val detail: String
    )

    fun crossCheck(
        profile: ProxyProfile,
        configJson: String,
        sources: CrossCheckSources
    ): Result {
        val evidences = mutableListOf<Evidence>()

        val config = addressFromConfigJson(configJson)
        evidences += Evidence(Source.CONFIG_JSON, config.address, config.port, fresh = true)

        val cachedHost = profile.host.trim().takeIf { it.isNotBlank() }
        val cachedPort = profile.port.takeIf { it in 1..65535 }
        evidences += Evidence(Source.PROFILE_FIELDS, cachedHost, cachedPort, fresh = false)

        val (rawHost, rawPort) = parseAuthority(profile.raw)
        if (rawHost != null) {
            evidences += Evidence(Source.RAW_URI, rawHost, rawPort, fresh = false)
        }

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

    fun oneLineMessage(profileName: String, result: Result): String =
        "${profileName.take(48)} • ${result.failure.userMessage}"

    private data class ConfigAddress(
        val address: String?,
        val port: Int?,
        val malformed: Boolean
    )

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
