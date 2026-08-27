package com.marbleng.app.data

import android.content.Context
import com.marbleng.app.model.*
import org.json.JSONArray
import org.json.JSONObject

class AppStore(context: Context) {
    // MARBLE_ULTIMATE_DEBUG_STORE_V15
    // MARBLE_IP_FAMILY_STORE_V24
    // MARBLE_SOURCE_TARGETING_STORE_V25_4
    // MARBLE_AURORA_UI_STORE_V26
    // MARBLE_SYSTEM_THEME_STORE_V32
    // MARBLE_KINETIC_GLASS_DEFAULT_V34
    // MARBLE_LIBRARY_MEMORY_STORE_V33
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

    // MARBLE_EXACT_LAST_PROFILE_V38
    fun lastProfileId(): String = prefs.getString("lastProfileId", "") ?: ""
    fun lastProfileSourceId(): String = prefs.getString("lastProfileSourceId", "") ?: ""

    /** Exact Library row reference: canonical config id + owner/source id. */
    fun setLastProfileRef(id: String, sourceId: String) =
        prefs.edit()
            .putString("lastProfileId", id)
            .putString("lastProfileSourceId", sourceId)
            .apply()

    /** Migration compatibility for old installs/callers. */
    fun setLastProfileId(id: String) =
        prefs.edit().putString("lastProfileId", id).apply()

    fun clearLastProfile() =
        prefs.edit()
            .remove("lastProfileId")
            .remove("lastProfileSourceId")
            .apply()

    /** Selected Library source is navigation state worth preserving across tabs and restarts. */
    fun librarySourceFilter(): String =
        prefs.getString("librarySourceFilter", "all")
            ?.trim()
            .orEmpty()
            .ifBlank { "all" }

    fun setLibrarySourceFilter(id: String) =
        prefs.edit()
            .putString("librarySourceFilter", id.trim().ifBlank { "all" })
            .apply()

    /**
     * v8.1 migration: existing installs already have old proxy-all/ads-off preferences persisted,
     * so changing AppSettings constructor defaults alone would not activate the new policy.
     * Apply the new Iran baseline exactly once while preserving user custom block/proxy lists.
     */
    private fun migrateRoutingDefaultsIfNeeded() {
        if (prefs.getInt("routingDefaultsSchema", 0) >= RoutingDefaults.PREFS_SCHEMA_VERSION) return

        fun merged(raw: String?, required: List<String>): String =
            ((raw ?: "")
                .split(',', '\n', '\r', ';')
                .map(String::trim)
                .filter(String::isNotBlank) + required)
                .distinctBy { it.lowercase() }
                .joinToString(",")

        val ipTags = merged(prefs.getString("routeGeoIpTags", ""), listOf("ir", "private"))
        val siteTags = merged(prefs.getString("routeGeoSiteTags", ""), listOf("ir"))

        prefs.edit()
            .putString("routingMode", RoutingMode.GEO_DIRECT.name)
            .putString("geoIpUrl", RoutingDefaults.GEOIP_URL)
            .putString("geoSiteUrl", RoutingDefaults.GEOSITE_URL)
            .putString("routeGeoIpTags", ipTags)
            .putString("routeGeoSiteTags", siteTags)
            .putBoolean("routeBypassPrivate", true)
            .putBoolean("routeBlockAds", true)
            .putString("routeAdsTag", RoutingDefaults.ADS_TAG)
            .putString("routeDomainStrategy", RoutingDefaults.DOMAIN_STRATEGY)
            .putBoolean("iranDomesticDirect", true)
            .putInt("routingDefaultsSchema", RoutingDefaults.PREFS_SCHEMA_VERSION)
            .apply()
    }

    // MARBLE_PERFORMANCE_MIGRATION_V14
    private fun migratePerformanceDefaultsIfNeeded() {
        if (prefs.getInt("performanceDefaultsSchema", 0) >= 1) return
        val edit = prefs.edit()
        if (prefs.getInt("benchSamples", 4) == 4) edit.putInt("benchSamples", 3)
        if (prefs.getInt("benchTimeoutSec", 8) == 8) edit.putInt("benchTimeoutSec", 6)
        if (prefs.getInt("tcpPrecheckTimeoutMs", 1800) == 1800) edit.putInt("tcpPrecheckTimeoutMs", 1000)
        if (prefs.getInt("connectTuningMethods", 4) == 4) edit.putInt("connectTuningMethods", 8)
        if (prefs.getInt("raceWidth", 3) == 3) edit.putInt("raceWidth", 4)
        edit.putBoolean("iranModeNotify", false)
        edit.putInt("performanceDefaultsSchema", 1)
        edit.apply()
    }

    fun settings(): AppSettings {
        migrateRoutingDefaultsIfNeeded()
        migratePerformanceDefaultsIfNeeded()
        return AppSettings(
        socksPort = prefs.getInt("socksPort", 10808),
        localProxyPort = prefs.getInt("localProxyPort", 10101),
        connectionMode = enumValue("connectionMode", ConnectionMode.FULL_TUN),

        probeMethod = enumValue("probeMethod", ProbeMethod.HYBRID),
        probeSpeedTest = prefs.getBoolean("probeSpeedTest", false),

        benchMode = enumValue("benchMode", BenchMode.BALANCED),
        benchCandidates = prefs.getInt("benchCandidates", 20),
        benchSamples = prefs.getInt("benchSamples", 3),
        benchTimeoutSec = prefs.getInt("benchTimeoutSec", 6),
        benchBytes = prefs.getInt("benchBytes", 262144),
        tcpPrecheckTimeoutMs = prefs.getInt("tcpPrecheckTimeoutMs", 1000),
        tcpWorkers = prefs.getInt("tcpWorkers", 20),

        nodeSortMode = enumValue("nodeSortMode", NodeSortMode.PING),
        nodeSortReverse = prefs.getBoolean("nodeSortReverse", false),

        rememberLast = prefs.getBoolean("rememberLast", true),
        subscriptionAutoRefresh = prefs.getBoolean("subscriptionAutoRefresh", true),
        // MARBLE_APP_UPDATE_STORE_V102
        appUpdateCheckEnabled = prefs.getBoolean("appUpdateCheckEnabled", true),
        subscriptionRefreshHours = prefs.getInt("subscriptionRefreshHours", 12),
        manualSourceEnabled = prefs.getBoolean("manualSourceEnabled", false),
        homeShowSummaryMetrics = prefs.getBoolean("homeShowSummaryMetrics", false),
        homeShowIranMode = prefs.getBoolean("homeShowIranMode", true),
        homeShowQuickActions = prefs.getBoolean("homeShowQuickActions", true),
        serverIntelEnabled = prefs.getBoolean("serverIntelEnabled", true),

        smartNotificationsEnabled = prefs.getBoolean("smartNotificationsEnabled", true),
        notifyConnectionEvents = prefs.getBoolean("notifyConnectionEvents", false),
        notifyRecoveryEvents = prefs.getBoolean("notifyRecoveryEvents", true),
        notifyPrivacyWarnings = prefs.getBoolean("notifyPrivacyWarnings", true),
        notifyNetworkChanges = prefs.getBoolean("notifyNetworkChanges", false),
        notifySubscriptionEvents = prefs.getBoolean("notifySubscriptionEvents", true),
        notifyCoreUpdates = prefs.getBoolean("notifyCoreUpdates", true),
        notificationLiveStats = prefs.getBoolean("notificationLiveStats", true),
        notificationCooldownSec = prefs.getInt("notificationCooldownSec", 20).coerceIn(5, 300),

        routingMode = enumValue("routingMode", RoutingMode.GEO_DIRECT),
        geoIpUrl = prefs.getString("geoIpUrl", RoutingDefaults.GEOIP_URL) ?: RoutingDefaults.GEOIP_URL,
        geoSiteUrl = prefs.getString("geoSiteUrl", RoutingDefaults.GEOSITE_URL) ?: RoutingDefaults.GEOSITE_URL,
        routeGeoIpTags = prefs.getString("routeGeoIpTags", RoutingDefaults.GEOIP_DIRECT_TAGS) ?: RoutingDefaults.GEOIP_DIRECT_TAGS,
        routeGeoSiteTags = prefs.getString("routeGeoSiteTags", RoutingDefaults.GEOSITE_DIRECT_TAGS) ?: RoutingDefaults.GEOSITE_DIRECT_TAGS,
        routeDirectDomains = prefs.getString("routeDirectDomains", "") ?: "",
        routeProxyDomains = prefs.getString("routeProxyDomains", "") ?: "",
        routeBlockDomains = prefs.getString("routeBlockDomains", "") ?: "",
        routeDirectIps = prefs.getString("routeDirectIps", "") ?: "",
        routeBlockIps = prefs.getString("routeBlockIps", "") ?: "",
        routeBypassPrivate = prefs.getBoolean("routeBypassPrivate", true),
        routeBlockAds = prefs.getBoolean("routeBlockAds", true),
        routeAdsTag = prefs.getString("routeAdsTag", RoutingDefaults.ADS_TAG) ?: RoutingDefaults.ADS_TAG,
        routeDomainStrategy = prefs.getString("routeDomainStrategy", RoutingDefaults.DOMAIN_STRATEGY) ?: RoutingDefaults.DOMAIN_STRATEGY,

        splitTunnelMode = enumValue("splitTunnelMode", SplitTunnelMode.ALL_APPS),
        splitTunnelPackages = prefs.getString("splitTunnelPackages", "") ?: "",

        dnsPrimaryIp = prefs.getString("dnsPrimaryIp", "1.1.1.1") ?: "1.1.1.1",
        dnsSecondaryIp = prefs.getString("dnsSecondaryIp", "8.8.8.8") ?: "8.8.8.8",
        dnsPrimaryDoH = prefs.getString("dnsPrimaryDoH", "https://1.1.1.1/dns-query") ?: "https://1.1.1.1/dns-query",
        dnsSecondaryDoH = prefs.getString("dnsSecondaryDoH", "https://8.8.8.8/dns-query") ?: "https://8.8.8.8/dns-query",
        dnsQueryStrategy = prefs.getString("dnsQueryStrategy", "UseIP") ?: "UseIP",
        ipv6Enabled = prefs.getBoolean("ipv6Enabled", true),
        preferIpv6 = prefs.getBoolean("preferIpv6", false),

        fragmentEnabled = prefs.getBoolean("fragmentEnabled", false),
        fragmentPackets = prefs.getString("fragmentPackets", "tlshello") ?: "tlshello",
        fragmentLength = prefs.getString("fragmentLength", "100-200") ?: "100-200",
        fragmentInterval = prefs.getString("fragmentInterval", "10-20") ?: "10-20",

        muxEnabled = prefs.getBoolean("muxEnabled", false),
        muxConcurrency = prefs.getInt("muxConcurrency", 8),
        muxXudpConcurrency = prefs.getInt("muxXudpConcurrency", 16),
        muxUdp443 = prefs.getString("muxUdp443", "skip") ?: "skip",

        iranModePolicy = enumValue("iranModePolicy", IranModePolicy.AUTO),
        iranModeCountermeasures = prefs.getBoolean("iranModeCountermeasures", true),
        iranDomesticDirect = prefs.getBoolean("iranDomesticDirect", true),
        iranDeepProbeEnabled = prefs.getBoolean("iranDeepProbeEnabled", true),
        iranModeNotify = false,

        intelligenceEnabled = prefs.getBoolean("intelligenceEnabled", true),
        configCompatibilityMode = prefs.getBoolean("configCompatibilityMode", true),
        verifiedPerformanceTuning = prefs.getBoolean("verifiedPerformanceTuning", true),
        connectTuningEnabled = prefs.getBoolean("connectTuningEnabled", true),
        connectTuningBudgetSec = prefs.getInt("connectTuningBudgetSec", 5).coerceIn(0, 20),
        connectTuningMethods = prefs.getInt("connectTuningMethods", 8).coerceIn(1, 8),
        liveTuningEnabled = prefs.getBoolean("liveTuningEnabled", true),
        liveTuningIntervalSec = prefs.getInt("liveTuningIntervalSec", 300).coerceIn(60, 3600),
        liveTuningPingTriggerMs = prefs.getInt("liveTuningPingTriggerMs", 220).coerceIn(80, 1200),
        liveTuningMinGainPercent = prefs.getInt("liveTuningMinGainPercent", 15).coerceIn(5, 80),
        adaptiveBufferEnabled = prefs.getBoolean("adaptiveBufferEnabled", true),
        identityGuardEnabled = prefs.getBoolean("identityGuardEnabled", true),
        identityGuardStrictNoFailover = prefs.getBoolean("identityGuardStrictNoFailover", true),
        identityGuardSameRouteRetries = prefs.getInt("identityGuardSameRouteRetries", 3).coerceIn(0, 5),
        continuousOptimizerEnabled = prefs.getBoolean("continuousOptimizerEnabled", true),
        optimizerIntervalSec = prefs.getInt("optimizerIntervalSec", 120).coerceIn(60, 900),
        optimizerCandidateCount = prefs.getInt("optimizerCandidateCount", 4).coerceIn(2, 8),
        optimizerDeepScanEvery = prefs.getInt("optimizerDeepScanEvery", 8).coerceIn(3, 20),
        optimizerSwitchCooldownSec = prefs.getInt("optimizerSwitchCooldownSec", 300).coerceIn(60, 1800),
        optimizerConfirmations = prefs.getInt("optimizerConfirmations", 2).coerceIn(1, 3),
        optimizerAvoidHeavyTraffic = prefs.getBoolean("optimizerAvoidHeavyTraffic", true),
        healthHistoryEnabled = prefs.getBoolean("healthHistoryEnabled", true),
        raceConnectEnabled = prefs.getBoolean("raceConnectEnabled", true),
        raceWidth = prefs.getInt("raceWidth", 4).coerceIn(2, 4),
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

        theme = prefs.getString("theme", "light") ?: "light",
        debugModeEnabled = prefs.getBoolean("debugModeEnabled", false),
        expertMode = prefs.getBoolean("expertMode", false)
        )
    }

    fun saveSettings(s: AppSettings) = prefs.edit()
        .putInt("socksPort", s.socksPort)
        .putInt("localProxyPort", s.localProxyPort)
        .putString("connectionMode", s.connectionMode.name)

        .putString("probeMethod", s.probeMethod.name)
        .putBoolean("probeSpeedTest", s.probeSpeedTest)

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
        .putBoolean("appUpdateCheckEnabled", s.appUpdateCheckEnabled)
        .putInt("subscriptionRefreshHours", s.subscriptionRefreshHours)
        .putBoolean("manualSourceEnabled", s.manualSourceEnabled)
        .putBoolean("homeShowSummaryMetrics", s.homeShowSummaryMetrics)
        .putBoolean("homeShowIranMode", s.homeShowIranMode)
        .putBoolean("homeShowQuickActions", s.homeShowQuickActions)
        .putBoolean("serverIntelEnabled", s.serverIntelEnabled)

        .putBoolean("smartNotificationsEnabled", s.smartNotificationsEnabled)
        .putBoolean("notifyConnectionEvents", s.notifyConnectionEvents)
        .putBoolean("notifyRecoveryEvents", s.notifyRecoveryEvents)
        .putBoolean("notifyPrivacyWarnings", s.notifyPrivacyWarnings)
        .putBoolean("notifyNetworkChanges", s.notifyNetworkChanges)
        .putBoolean("notifySubscriptionEvents", s.notifySubscriptionEvents)
        .putBoolean("notifyCoreUpdates", s.notifyCoreUpdates)
        .putBoolean("notificationLiveStats", s.notificationLiveStats)
        .putInt("notificationCooldownSec", s.notificationCooldownSec.coerceIn(5, 300))

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
        .putBoolean("ipv6Enabled", s.ipv6Enabled)
        .putBoolean("preferIpv6", s.preferIpv6)

        .putBoolean("fragmentEnabled", s.fragmentEnabled)
        .putString("fragmentPackets", s.fragmentPackets)
        .putString("fragmentLength", s.fragmentLength)
        .putString("fragmentInterval", s.fragmentInterval)

        .putBoolean("muxEnabled", s.muxEnabled)
        .putInt("muxConcurrency", s.muxConcurrency)
        .putInt("muxXudpConcurrency", s.muxXudpConcurrency)
        .putString("muxUdp443", s.muxUdp443)

        .putString("iranModePolicy", s.iranModePolicy.name)
        .putBoolean("iranModeCountermeasures", s.iranModeCountermeasures)
        .putBoolean("iranDomesticDirect", s.iranDomesticDirect)
        .putBoolean("iranDeepProbeEnabled", s.iranDeepProbeEnabled)
        .putBoolean("iranModeNotify", false)

        .putBoolean("intelligenceEnabled", s.intelligenceEnabled)
        .putBoolean("configCompatibilityMode", s.configCompatibilityMode)
        .putBoolean("verifiedPerformanceTuning", s.verifiedPerformanceTuning)
        .putBoolean("connectTuningEnabled", s.connectTuningEnabled)
        .putInt("connectTuningBudgetSec", s.connectTuningBudgetSec.coerceIn(0, 20))
        .putInt("connectTuningMethods", s.connectTuningMethods.coerceIn(1, 8))
        .putBoolean("liveTuningEnabled", s.liveTuningEnabled)
        .putInt("liveTuningIntervalSec", s.liveTuningIntervalSec.coerceIn(60, 3600))
        .putInt("liveTuningPingTriggerMs", s.liveTuningPingTriggerMs.coerceIn(80, 1200))
        .putInt("liveTuningMinGainPercent", s.liveTuningMinGainPercent.coerceIn(5, 80))
        .putBoolean("adaptiveBufferEnabled", s.adaptiveBufferEnabled)
        .putBoolean("identityGuardEnabled", s.identityGuardEnabled)
        .putBoolean("identityGuardStrictNoFailover", s.identityGuardStrictNoFailover)
        .putInt("identityGuardSameRouteRetries", s.identityGuardSameRouteRetries.coerceIn(0, 5))
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
        .putBoolean("debugModeEnabled", s.debugModeEnabled)
        .putBoolean("expertMode", s.expertMode)
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
