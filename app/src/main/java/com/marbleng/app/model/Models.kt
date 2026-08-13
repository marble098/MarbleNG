package com.marbleng.app.model

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

    companion object {
        fun fromJson(o: JSONObject) = ProxyProfile(
            o.optString("id"), o.optString("name"), o.optString("scheme"), o.optString("raw"), o.optString("configJson"),
            o.optString("host"), o.optInt("port"), o.optString("transport"), o.optString("security"),
            o.optString("subscriptionId", "manual"), o.optString("subscriptionName", "Manual")
        )
    }
}

data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = 0,
    val expireAt: Long = 0
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("updatedAt", updatedAt)
        put("uploadBytes", uploadBytes); put("downloadBytes", downloadBytes)
        put("totalBytes", totalBytes); put("expireAt", expireAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = Subscription(
            o.optString("id"), o.optString("name"), o.optString("url"), o.optLong("updatedAt"),
            o.optLong("uploadBytes"), o.optLong("downloadBytes"), o.optLong("totalBytes"), o.optLong("expireAt")
        )
    }
}

data class BenchmarkResult(
    val profileId: String,
    val name: String,
    val success: Int,
    val latencyMs: Double,
    val jitterMs: Double,
    val bytesPerSecond: Double,
    val score: Double
)

data class ConnectionRecord(val profileId: String, val name: String, val at: Long, val reason: String)

enum class BenchMode { RELIABLE, BALANCED, FAST, TURBO, CUSTOM }
enum class ConnectionMode { FULL_TUN, LOCAL_PROXY }
enum class RoutingMode { PROXY_ALL, BYPASS_PRIVATE, GEO_DIRECT, CUSTOM }
enum class SplitTunnelMode { ALL_APPS, ONLY_SELECTED, BYPASS_SELECTED }

data class AppSettings(
    val socksPort: Int = 10808,
    val localProxyPort: Int = 10101,
    val connectionMode: ConnectionMode = ConnectionMode.FULL_TUN,
    val autoCoreUpdate: Boolean = true,

    val benchMode: BenchMode = BenchMode.BALANCED,
    val benchCandidates: Int = 20,
    val benchSamples: Int = 4,
    val benchTimeoutSec: Int = 8,
    val benchBytes: Int = 262144,
    val tcpPrecheckTimeoutMs: Int = 1800,
    val tcpWorkers: Int = 20,

    val rememberLast: Boolean = true,
    val subscriptionAutoRefresh: Boolean = true,
    val subscriptionRefreshHours: Int = 12,

    val telegramPosts: Int = 20,
    val telegramMaxConfigs: Int = 80,
    val telegramTcpGate: Boolean = true,
    val telegramTcpSamples: Int = 3,
    val telegramAutoSub: Boolean = true,
    val telegramPassMinSuccess: Int = 75,

    val routingMode: RoutingMode = RoutingMode.PROXY_ALL,
    val geoIpUrl: String = "",
    val geoSiteUrl: String = "",
    val routeGeoIpTags: String = "private",
    val routeGeoSiteTags: String = "",
    val routeDirectDomains: String = "",
    val routeProxyDomains: String = "",
    val routeBlockDomains: String = "",
    val routeDirectIps: String = "",
    val routeBlockIps: String = "",
    val routeBypassPrivate: Boolean = true,
    val routeBlockAds: Boolean = false,
    val routeAdsTag: String = "category-ads-all",
    val routeDomainStrategy: String = "AsIs",

    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val splitTunnelPackages: String = "",

    val dnsPrimaryIp: String = "1.1.1.1",
    val dnsSecondaryIp: String = "8.8.8.8",
    val dnsPrimaryDoH: String = "https://1.1.1.1/dns-query",
    val dnsSecondaryDoH: String = "https://8.8.8.8/dns-query",
    val dnsQueryStrategy: String = "UseIP",

    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "100-200",
    val fragmentInterval: String = "10-20",

    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 16,
    val muxUdp443: String = "skip",

    val chainEnabled: Boolean = false,
    val chainSecondProfileId: String = "",

    val theme: String = "dark"
)
