package com.marbleng.app.model

import org.json.JSONArray
import org.json.JSONObject

data class ProxyProfile(
    val id: String,
    val name: String,
    val scheme: String,
    val raw: String,
    val configJson: String,
    val host: String = "",
    val port: Int = 0,
    val transport: String = "",
    val security: String = "",
    val subscriptionId: String = "manual",
    val subscriptionName: String = "Manual"
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("scheme", scheme); put("raw", raw)
        put("configJson", configJson); put("host", host); put("port", port)
        put("transport", transport); put("security", security)
        put("subscriptionId", subscriptionId); put("subscriptionName", subscriptionName)
    }
    companion object { fun fromJson(o: JSONObject) = ProxyProfile(
        o.optString("id"), o.optString("name"), o.optString("scheme"), o.optString("raw"), o.optString("configJson"),
        o.optString("host"), o.optInt("port"), o.optString("transport"), o.optString("security"),
        o.optString("subscriptionId", "manual"), o.optString("subscriptionName", "Manual")
    ) }
}

data class Subscription(val id: String, val name: String, val url: String, val updatedAt: Long = 0) {
    fun toJson() = JSONObject().apply { put("id",id); put("name",name); put("url",url); put("updatedAt",updatedAt) }
    companion object { fun fromJson(o: JSONObject)=Subscription(o.optString("id"),o.optString("name"),o.optString("url"),o.optLong("updatedAt")) }
}

data class BenchmarkResult(
    val profileId: String, val name: String, val success: Int, val latencyMs: Double,
    val jitterMs: Double, val bytesPerSecond: Double, val score: Double
)

data class ConnectionRecord(val profileId: String, val name: String, val at: Long, val reason: String)

enum class BenchMode { RELIABLE, BALANCED, FAST, TURBO, CUSTOM }

enum class RouteAction { PROXY, DIRECT, BLOCK }

/** A single user routing rule: matchers (domains / ips) resolved to an action. */
data class RoutingRule(
    val id: String,
    val name: String,
    val action: RouteAction,
    val domains: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("action", action.name)
        put("domains", JSONArray(domains)); put("ips", JSONArray(ips)); put("enabled", enabled)
    }
    companion object {
        fun fromJson(o: JSONObject): RoutingRule {
            fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            return RoutingRule(
                o.optString("id"), o.optString("name"),
                runCatching { RouteAction.valueOf(o.optString("action", "PROXY")) }.getOrDefault(RouteAction.PROXY),
                list("domains"), list("ips"), o.optBoolean("enabled", true)
            )
        }
    }
}

/** Everything the config hardener needs to build a routing block. Null = pure proxy (fail-closed). */
data class RoutingSpec(
    val domainStrategy: String = "IPIfNonMatch",
    val bypassLan: Boolean = true,
    val blockAds: Boolean = false,
    val rules: List<RoutingRule> = emptyList()
)

data class AppSettings(
    val socksPort: Int = 10808,
    val localProxyPort: Int = 10101,
    val tunnelMode: String = "vpn", // "vpn" (full TUN) | "proxy" (local SOCKS only)
    val routingEnabled: Boolean = false,
    val routeDomainStrategy: String = "IPIfNonMatch",
    val bypassLan: Boolean = true,
    val blockAds: Boolean = false,
    val geoipUrl: String = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
    val geositeUrl: String = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
    val autoCoreUpdate: Boolean = true,
    val benchMode: BenchMode = BenchMode.BALANCED,
    val benchCandidates: Int = 20,
    val benchSamples: Int = 4,
    val benchTimeoutSec: Int = 8,
    val benchBytes: Int = 262144,
    val tcpPrecheckTimeoutMs: Int = 1800,
    val tcpWorkers: Int = 20,
    val rememberLast: Boolean = true,
    val telegramPosts: Int = 20,
    val telegramMaxConfigs: Int = 80,
    val telegramTcpGate: Boolean = true,
    val telegramTcpSamples: Int = 3,
    val telegramAutoSub: Boolean = true,
    val telegramPassMinSuccess: Int = 75,
    val theme: String = "aurora",
    val density: String = "comfortable",
    val border: String = "rounded",
    val icons: Boolean = true
)
