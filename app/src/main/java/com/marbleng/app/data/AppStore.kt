package com.marbleng.app.data

import android.content.Context
import com.marbleng.app.model.*
import org.json.JSONArray
import org.json.JSONObject

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("marbleng-store", Context.MODE_PRIVATE)

    fun loadProfiles(): MutableList<ProxyProfile> = parseArray("profiles") { ProxyProfile.fromJson(it) }
    fun saveProfiles(v: List<ProxyProfile>) = saveArray("profiles", v.map { it.toJson() })
    fun loadSubscriptions(): MutableList<Subscription> = parseArray("subscriptions") { Subscription.fromJson(it) }
    fun saveSubscriptions(v: List<Subscription>) = saveArray("subscriptions", v.map { it.toJson() })

    fun loadHistory(): MutableList<ConnectionRecord> = parseArray("history") {
        ConnectionRecord(it.optString("profileId"), it.optString("name"), it.optLong("at"), it.optString("reason"))
    }

    fun saveHistory(v: List<ConnectionRecord>) = saveArray("history", v.takeLast(200).map {
        JSONObject().put("profileId", it.profileId).put("name", it.name).put("at", it.at).put("reason", it.reason)
    })

    fun lastProfileId(): String = prefs.getString("lastProfileId", "") ?: ""
    fun setLastProfileId(id: String) = prefs.edit().putString("lastProfileId", id).apply()
    fun channels(): MutableList<String> = prefs.getStringSet("channels", emptySet())?.toMutableList() ?: mutableListOf()
    fun saveChannels(v: List<String>) = prefs.edit().putStringSet("channels", v.toSet()).apply()

    fun settings(): AppSettings = AppSettings(
        socksPort = prefs.getInt("socksPort", 10808),
        localProxyPort = prefs.getInt("localProxyPort", 10101),
        connectionMode = enumValue("connectionMode", ConnectionMode.FULL_TUN),
        autoCoreUpdate = prefs.getBoolean("autoCoreUpdate", true),

        benchMode = enumValue("benchMode", BenchMode.BALANCED),
        benchCandidates = prefs.getInt("benchCandidates", 20),
        benchSamples = prefs.getInt("benchSamples", 4),
        benchTimeoutSec = prefs.getInt("benchTimeoutSec", 8),
        benchBytes = prefs.getInt("benchBytes", 262144),
        tcpPrecheckTimeoutMs = prefs.getInt("tcpPrecheckTimeoutMs", 1800),
        tcpWorkers = prefs.getInt("tcpWorkers", 20),

        nodeSortMode = enumValue("nodeSortMode", NodeSortMode.PING),
        nodeSortReverse = prefs.getBoolean("nodeSortReverse", false),

        rememberLast = prefs.getBoolean("rememberLast", true),
        subscriptionAutoRefresh = prefs.getBoolean("subscriptionAutoRefresh", true),
        subscriptionRefreshHours = prefs.getInt("subscriptionRefreshHours", 12),

        smartNotificationsEnabled = prefs.getBoolean("smartNotificationsEnabled", true),
        notifyConnectionEvents = prefs.getBoolean("notifyConnectionEvents", false),
        notifyRecoveryEvents = prefs.getBoolean("notifyRecoveryEvents", true),
        notifyPrivacyWarnings = prefs.getBoolean("notifyPrivacyWarnings", true),
        notifyNetworkChanges = prefs.getBoolean("notifyNetworkChanges", false),
        notifySubscriptionEvents = prefs.getBoolean("notifySubscriptionEvents", true),
        notifyCoreUpdates = prefs.getBoolean("notifyCoreUpdates", true),
        notificationLiveStats = prefs.getBoolean("notificationLiveStats", true),
        notificationCooldownSec = prefs.getInt("notificationCooldownSec", 20).coerceIn(5, 300),

        telegramPosts = prefs.getInt("telegramPosts", 20),
        telegramMaxConfigs = prefs.getInt("telegramMaxConfigs", 80),
        telegramTcpGate = prefs.getBoolean("telegramTcpGate", true),
        telegramTcpSamples = prefs.getInt("telegramTcpSamples", 3),
        telegramAutoSub = prefs.getBoolean("telegramAutoSub", true),
        telegramPassMinSuccess = prefs.getInt("telegramPassMinSuccess", 75),

        routingMode = enumValue("routingMode", RoutingMode.PROXY_ALL),
        geoIpUrl = prefs.getString("geoIpUrl", "") ?: "",
        geoSiteUrl = prefs.getString("geoSiteUrl", "") ?: "",
        routeGeoIpTags = prefs.getString("routeGeoIpTags", "private") ?: "private",
        routeGeoSiteTags = prefs.getString("routeGeoSiteTags", "") ?: "",
        routeDirectDomains = prefs.getString("routeDirectDomains", "") ?: "",
        routeProxyDomains = prefs.getString("routeProxyDomains", "") ?: "",
        routeBlockDomains = prefs.getString("routeBlockDomains", "") ?: "",
        routeDirectIps = prefs.getString("routeDirectIps", "") ?: "",
        routeBlockIps = prefs.getString("routeBlockIps", "") ?: "",
        routeBypassPrivate = prefs.getBoolean("routeBypassPrivate", true),
        routeBlockAds = prefs.getBoolean("routeBlockAds", false),
        routeAdsTag = prefs.getString("routeAdsTag", "category-ads-all") ?: "category-ads-all",
        routeDomainStrategy = prefs.getString("routeDomainStrategy", "AsIs") ?: "AsIs",

        splitTunnelMode = enumValue("splitTunnelMode", SplitTunnelMode.ALL_APPS),
        splitTunnelPackages = prefs.getString("splitTunnelPackages", "") ?: "",

        dnsPrimaryIp = prefs.getString("dnsPrimaryIp", "1.1.1.1") ?: "1.1.1.1",
        dnsSecondaryIp = prefs.getString("dnsSecondaryIp", "8.8.8.8") ?: "8.8.8.8",
        dnsPrimaryDoH = prefs.getString("dnsPrimaryDoH", "https://1.1.1.1/dns-query") ?: "https://1.1.1.1/dns-query",
        dnsSecondaryDoH = prefs.getString("dnsSecondaryDoH", "https://8.8.8.8/dns-query") ?: "https://8.8.8.8/dns-query",
        dnsQueryStrategy = prefs.getString("dnsQueryStrategy", "UseIP") ?: "UseIP",

        fragmentEnabled = prefs.getBoolean("fragmentEnabled", false),
        fragmentPackets = prefs.getString("fragmentPackets", "tlshello") ?: "tlshello",
        fragmentLength = prefs.getString("fragmentLength", "100-200") ?: "100-200",
        fragmentInterval = prefs.getString("fragmentInterval", "10-20") ?: "10-20",

        muxEnabled = prefs.getBoolean("muxEnabled", false),
        muxConcurrency = prefs.getInt("muxConcurrency", 8),
        muxXudpConcurrency = prefs.getInt("muxXudpConcurrency", 16),
        muxUdp443 = prefs.getString("muxUdp443", "skip") ?: "skip",

        chainEnabled = prefs.getBoolean("chainEnabled", false),
        chainSecondProfileId = prefs.getString("chainSecondProfileId", "") ?: "",

        intelligenceEnabled = prefs.getBoolean("intelligenceEnabled", true),
        configCompatibilityMode = prefs.getBoolean("configCompatibilityMode", true),
        verifiedPerformanceTuning = prefs.getBoolean("verifiedPerformanceTuning", true),
        continuousOptimizerEnabled = prefs.getBoolean("continuousOptimizerEnabled", true),
        optimizerIntervalSec = prefs.getInt("optimizerIntervalSec", 120).coerceIn(60, 900),
        optimizerCandidateCount = prefs.getInt("optimizerCandidateCount", 4).coerceIn(2, 8),
        optimizerDeepScanEvery = prefs.getInt("optimizerDeepScanEvery", 8).coerceIn(3, 20),
        optimizerSwitchCooldownSec = prefs.getInt("optimizerSwitchCooldownSec", 300).coerceIn(60, 1800),
        optimizerConfirmations = prefs.getInt("optimizerConfirmations", 2).coerceIn(1, 3),
        optimizerAvoidHeavyTraffic = prefs.getBoolean("optimizerAvoidHeavyTraffic", true),
        healthHistoryEnabled = prefs.getBoolean("healthHistoryEnabled", true),
        raceConnectEnabled = prefs.getBoolean("raceConnectEnabled", true),
        raceWidth = prefs.getInt("raceWidth", 3),
        smartFallbackEnabled = prefs.getBoolean("smartFallbackEnabled", true),
        fallbackCount = prefs.getInt("fallbackCount", 3),
        autoReconnectAfterKillSwitch = prefs.getBoolean("autoReconnectAfterKillSwitch", true),
        networkChangeRecoveryEnabled = prefs.getBoolean("networkChangeRecoveryEnabled", true),
        adaptiveMtuEnabled = prefs.getBoolean("adaptiveMtuEnabled", true),
        mtuMin = prefs.getInt("mtuMin", 1280),
        mtuMax = prefs.getInt("mtuMax", 1500),
        dnsHijackEnabled = prefs.getBoolean("dnsHijackEnabled", true),
        adaptiveDnsEnabled = prefs.getBoolean("adaptiveDnsEnabled", true),
        adaptiveDualStackEnabled = prefs.getBoolean("adaptiveDualStackEnabled", true),
        adaptiveThroughputEnabled = prefs.getBoolean("adaptiveThroughputEnabled", true),
        adaptiveThroughputMaxBytes = prefs.getInt("adaptiveThroughputMaxBytes", 4 * 1024 * 1024),
        udpProbeEnabled = prefs.getBoolean("udpProbeEnabled", true),
        adaptiveMuxEnabled = prefs.getBoolean("adaptiveMuxEnabled", true),
        adaptiveFragmentEnabled = prefs.getBoolean("adaptiveFragmentEnabled", true),
        thermalAwareEnabled = prefs.getBoolean("thermalAwareEnabled", true),
        workloadProfile = enumValue("workloadProfile", WorkloadProfile.AUTO),

        theme = prefs.getString("theme", "dark") ?: "dark"
    )

    fun saveSettings(s: AppSettings) = prefs.edit()
        .putInt("socksPort", s.socksPort)
        .putInt("localProxyPort", s.localProxyPort)
        .putString("connectionMode", s.connectionMode.name)
        .putBoolean("autoCoreUpdate", s.autoCoreUpdate)

        .putString("benchMode", s.benchMode.name)
        .putInt("benchCandidates", s.benchCandidates)
        .putInt("benchSamples", s.benchSamples)
        .putInt("benchTimeoutSec", s.benchTimeoutSec)
        .putInt("benchBytes", s.benchBytes)
        .putInt("tcpPrecheckTimeoutMs", s.tcpPrecheckTimeoutMs)
        .putInt("tcpWorkers", s.tcpWorkers)

        .putString("nodeSortMode", s.nodeSortMode.name)
        .putBoolean("nodeSortReverse", s.nodeSortReverse)

        .putBoolean("rememberLast", s.rememberLast)
        .putBoolean("subscriptionAutoRefresh", s.subscriptionAutoRefresh)
        .putInt("subscriptionRefreshHours", s.subscriptionRefreshHours)

        .putBoolean("smartNotificationsEnabled", s.smartNotificationsEnabled)
        .putBoolean("notifyConnectionEvents", s.notifyConnectionEvents)
        .putBoolean("notifyRecoveryEvents", s.notifyRecoveryEvents)
        .putBoolean("notifyPrivacyWarnings", s.notifyPrivacyWarnings)
        .putBoolean("notifyNetworkChanges", s.notifyNetworkChanges)
        .putBoolean("notifySubscriptionEvents", s.notifySubscriptionEvents)
        .putBoolean("notifyCoreUpdates", s.notifyCoreUpdates)
        .putBoolean("notificationLiveStats", s.notificationLiveStats)
        .putInt("notificationCooldownSec", s.notificationCooldownSec.coerceIn(5, 300))

        .putInt("telegramPosts", s.telegramPosts)
        .putInt("telegramMaxConfigs", s.telegramMaxConfigs)
        .putBoolean("telegramTcpGate", s.telegramTcpGate)
        .putInt("telegramTcpSamples", s.telegramTcpSamples)
        .putBoolean("telegramAutoSub", s.telegramAutoSub)
        .putInt("telegramPassMinSuccess", s.telegramPassMinSuccess)

        .putString("routingMode", s.routingMode.name)
        .putString("geoIpUrl", s.geoIpUrl)
        .putString("geoSiteUrl", s.geoSiteUrl)
        .putString("routeGeoIpTags", s.routeGeoIpTags)
        .putString("routeGeoSiteTags", s.routeGeoSiteTags)
        .putString("routeDirectDomains", s.routeDirectDomains)
        .putString("routeProxyDomains", s.routeProxyDomains)
        .putString("routeBlockDomains", s.routeBlockDomains)
        .putString("routeDirectIps", s.routeDirectIps)
        .putString("routeBlockIps", s.routeBlockIps)
        .putBoolean("routeBypassPrivate", s.routeBypassPrivate)
        .putBoolean("routeBlockAds", s.routeBlockAds)
        .putString("routeAdsTag", s.routeAdsTag)
        .putString("routeDomainStrategy", s.routeDomainStrategy)

        .putString("splitTunnelMode", s.splitTunnelMode.name)
        .putString("splitTunnelPackages", s.splitTunnelPackages)

        .putString("dnsPrimaryIp", s.dnsPrimaryIp)
        .putString("dnsSecondaryIp", s.dnsSecondaryIp)
        .putString("dnsPrimaryDoH", s.dnsPrimaryDoH)
        .putString("dnsSecondaryDoH", s.dnsSecondaryDoH)
        .putString("dnsQueryStrategy", s.dnsQueryStrategy)

        .putBoolean("fragmentEnabled", s.fragmentEnabled)
        .putString("fragmentPackets", s.fragmentPackets)
        .putString("fragmentLength", s.fragmentLength)
        .putString("fragmentInterval", s.fragmentInterval)

        .putBoolean("muxEnabled", s.muxEnabled)
        .putInt("muxConcurrency", s.muxConcurrency)
        .putInt("muxXudpConcurrency", s.muxXudpConcurrency)
        .putString("muxUdp443", s.muxUdp443)

        .putBoolean("chainEnabled", s.chainEnabled)
        .putString("chainSecondProfileId", s.chainSecondProfileId)

        .putBoolean("intelligenceEnabled", s.intelligenceEnabled)
        .putBoolean("configCompatibilityMode", s.configCompatibilityMode)
        .putBoolean("verifiedPerformanceTuning", s.verifiedPerformanceTuning)
        .putBoolean("continuousOptimizerEnabled", s.continuousOptimizerEnabled)
        .putInt("optimizerIntervalSec", s.optimizerIntervalSec.coerceIn(60, 900))
        .putInt("optimizerCandidateCount", s.optimizerCandidateCount.coerceIn(2, 8))
        .putInt("optimizerDeepScanEvery", s.optimizerDeepScanEvery.coerceIn(3, 20))
        .putInt("optimizerSwitchCooldownSec", s.optimizerSwitchCooldownSec.coerceIn(60, 1800))
        .putInt("optimizerConfirmations", s.optimizerConfirmations.coerceIn(1, 3))
        .putBoolean("optimizerAvoidHeavyTraffic", s.optimizerAvoidHeavyTraffic)
        .putBoolean("healthHistoryEnabled", s.healthHistoryEnabled)
        .putBoolean("raceConnectEnabled", s.raceConnectEnabled)
        .putInt("raceWidth", s.raceWidth)
        .putBoolean("smartFallbackEnabled", s.smartFallbackEnabled)
        .putInt("fallbackCount", s.fallbackCount)
        .putBoolean("autoReconnectAfterKillSwitch", s.autoReconnectAfterKillSwitch)
        .putBoolean("networkChangeRecoveryEnabled", s.networkChangeRecoveryEnabled)
        .putBoolean("adaptiveMtuEnabled", s.adaptiveMtuEnabled)
        .putInt("mtuMin", s.mtuMin)
        .putInt("mtuMax", s.mtuMax)
        .putBoolean("dnsHijackEnabled", s.dnsHijackEnabled)
        .putBoolean("adaptiveDnsEnabled", s.adaptiveDnsEnabled)
        .putBoolean("adaptiveDualStackEnabled", s.adaptiveDualStackEnabled)
        .putBoolean("adaptiveThroughputEnabled", s.adaptiveThroughputEnabled)
        .putInt("adaptiveThroughputMaxBytes", s.adaptiveThroughputMaxBytes)
        .putBoolean("udpProbeEnabled", s.udpProbeEnabled)
        .putBoolean("adaptiveMuxEnabled", s.adaptiveMuxEnabled)
        .putBoolean("adaptiveFragmentEnabled", s.adaptiveFragmentEnabled)
        .putBoolean("thermalAwareEnabled", s.thermalAwareEnabled)
        .putString("workloadProfile", s.workloadProfile.name)

        .putString("theme", s.theme)
        .apply()

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, fallback.name) ?: fallback.name) }.getOrDefault(fallback)

    private fun <T> parseArray(key: String, f: (JSONObject) -> T): MutableList<T> {
        val out = mutableListOf<T>()
        val arr = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrElse { JSONArray() }
        for (i in 0 until arr.length()) runCatching { out += f(arr.getJSONObject(i)) }
        return out
    }

    private fun saveArray(key: String, values: List<JSONObject>) {
        val a = JSONArray()
        values.forEach(a::put)
        prefs.edit().putString(key, a.toString()).apply()
    }
}
