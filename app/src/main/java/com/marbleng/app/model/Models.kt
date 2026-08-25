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
    val subscriptionName: String = "Manual",
    /** True for nodes owned by remote subscription refresh; false for user-added nodes. */
    val sourceManaged: Boolean = true
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("scheme", scheme); put("raw", raw)
        put("configJson", configJson); put("host", host); put("port", port)
        put("transport", transport); put("security", security)
        put("subscriptionId", subscriptionId); put("subscriptionName", subscriptionName)
        put("sourceManaged", sourceManaged)
    }

    companion object {
        fun fromJson(o: JSONObject) = ProxyProfile(
            o.optString("id"), o.optString("name"), o.optString("scheme"), o.optString("raw"), o.optString("configJson"),
            o.optString("host"), o.optInt("port"), o.optString("transport"), o.optString("security"),
            o.optString("subscriptionId", "manual"), o.optString("subscriptionName", "Manual"),
            if (o.has("sourceManaged")) {
                o.optBoolean("sourceManaged", true)
            } else {
                o.optString("subscriptionId", "manual") != "manual"
            }
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
    val bytesPerSecond: Double,
    val score: Double,
    val udpSuccess: Int = 0,
    val interactiveScore: Double = 0.0,
    val streamingScore: Double = 0.0,
    val stabilityScore: Double = 0.0,
    val resilienceScore: Double = 0.0,
    val usedFragment: Boolean = false,
    val usedMux: Boolean = false,
    /** Evidence tier shown in Library. TCP/ICMP are endpoint reachability; TUNNEL proves Xray. */
    val probeKind: String = "TUNNEL",
    /** Mean absolute IPDV between consecutive verified warm-tunnel samples. */
    val jitterMs: Double = 0.0,
    /** First verified response timing; never used as the final delay when a warmer try wins. */
    val warmupMs: Double = 0.0,
    val sampleCount: Int = 0,
    /** Compact stage evidence retained for Bug Finder; never shown as a synthetic ping. */
    val failureReason: String = ""
)

data class ConnectionRecord(val profileId: String, val name: String, val at: Long, val reason: String)

enum class BenchMode { RELIABLE, BALANCED, FAST, TURBO, CUSTOM }
enum class ConnectionMode { FULL_TUN, LOCAL_PROXY }
enum class RoutingMode { PROXY_ALL, BYPASS_PRIVATE, GEO_DIRECT, CUSTOM }
enum class SplitTunnelMode { ALL_APPS, ONLY_SELECTED, BYPASS_SELECTED }
enum class WorkloadProfile { AUTO, INTERACTIVE, STREAMING, STABILITY, STEALTH }
enum class NodeSortMode { PING, SCORE, NAME, PROTOCOL, SOURCE }

/** Live state of one node inside a running test batch, shown on the node's own card. */
enum class ProbeState { IDLE, QUEUED, TESTING }

/**
 * How MarbleNG measures a node.
 *
 * TUNNEL is the only method that proves a node actually carries traffic; the others are fast
 * reachability estimates of the physical endpoint and cannot see censorship or a dead account.
 */
enum class ProbeMethod { TUNNEL, TCP, ICMP, HYBRID }

/**
 * Canonical MarbleNG routing baseline.
 *
 * Chocolate4U/Iran-v2ray-rules publishes a continuously updated `release` branch containing
 * Xray-compatible geoip.dat/geosite.dat. Signed CI builds also bundle the same two verified files,
 * so a blocked GitHub/raw endpoint can never prevent the first proxy connection.
 */
object RoutingDefaults {
    const val GEOIP_URL =
        "https://raw.githubusercontent.com/Chocolate4U/Iran-v2ray-rules/release/geoip.dat"
    const val GEOSITE_URL =
        "https://raw.githubusercontent.com/Chocolate4U/Iran-v2ray-rules/release/geosite.dat"
    const val GEOIP_MIRROR =
        "https://cdn.jsdelivr.net/gh/chocolate4u/Iran-v2ray-rules@release/geoip.dat"
    const val GEOSITE_MIRROR =
        "https://cdn.jsdelivr.net/gh/chocolate4u/Iran-v2ray-rules@release/geosite.dat"
    const val GEOIP_DIRECT_TAGS = "ir,private"
    const val GEOSITE_DIRECT_TAGS = "ir"
    const val ADS_TAG = "category-ads-all"
    const val DOMAIN_STRATEGY = "IPIfNonMatch"
    const val PREFS_SCHEMA_VERSION = 1
}

/** How Iran Mode decides whether the anti-filtering engine should run. */
enum class IranModePolicy { AUTO, ALWAYS_ON, OFF }

// MARBLE_SMART_DEFAULTS_V14
// MARBLE_ULTIMATE_DEBUG_SETTING_V15
// MARBLE_INTELLIGENCE_V24
// MARBLE_SOURCE_TARGETING_V25_4
// MARBLE_AURORA_UI_SETTINGS_V26
// MARBLE_SYSTEM_THEME_MODEL_V32
// MARBLE_KINETIC_GLASS_DEFAULT_V34
// MARBLE_HOME_SUMMARY_VISIBILITY_V35
data class AppSettings(
    val socksPort: Int = 10808,
    val localProxyPort: Int = 10101,
    val connectionMode: ConnectionMode = ConnectionMode.FULL_TUN,

    // How nodes are measured, and how deep each measurement goes.
    val probeMethod: ProbeMethod = ProbeMethod.HYBRID,
    val probeSpeedTest: Boolean = false,

    val benchMode: BenchMode = BenchMode.BALANCED,
    val benchCandidates: Int = 20,
    val benchSamples: Int = 3,
    val benchTimeoutSec: Int = 6,
    val benchBytes: Int = 262144,
    val tcpPrecheckTimeoutMs: Int = 1000,
    val tcpWorkers: Int = 20,

    // Library order. Ping is intentionally the default; untested nodes stay last.
    val nodeSortMode: NodeSortMode = NodeSortMode.PING,
    val nodeSortReverse: Boolean = false,

    val rememberLast: Boolean = true,
    val subscriptionAutoRefresh: Boolean = true,
    // MARBLE_APP_UPDATE_SETTING_V102
    /** Check GitHub Releases whenever MarbleNG returns to the foreground. */
    val appUpdateCheckEnabled: Boolean = true,
    val subscriptionRefreshHours: Int = 12,
    /** Virtual Manual source is opt-in and disabled by default. */
    val manualSourceEnabled: Boolean = false,

    // Home composition. Hiding a card does not disable its underlying engine.
    val homeShowSummaryMetrics: Boolean = false,
    val homeShowIranMode: Boolean = true,
    val homeShowQuickActions: Boolean = true,

    // Optional smart alerts. Foreground-service status is managed separately while connected.
    val smartNotificationsEnabled: Boolean = true,
    val notifyConnectionEvents: Boolean = false,
    val notifyRecoveryEvents: Boolean = true,
    val notifyPrivacyWarnings: Boolean = true,
    val notifyNetworkChanges: Boolean = false,
    val notifySubscriptionEvents: Boolean = true,
    val notifyCoreUpdates: Boolean = true,
    val notificationLiveStats: Boolean = true,
    val notificationCooldownSec: Int = 20,

    val routingMode: RoutingMode = RoutingMode.GEO_DIRECT,
    val geoIpUrl: String = RoutingDefaults.GEOIP_URL,
    val geoSiteUrl: String = RoutingDefaults.GEOSITE_URL,
    val routeGeoIpTags: String = RoutingDefaults.GEOIP_DIRECT_TAGS,
    val routeGeoSiteTags: String = RoutingDefaults.GEOSITE_DIRECT_TAGS,
    val routeDirectDomains: String = "",
    val routeProxyDomains: String = "",
    val routeBlockDomains: String = "",
    val routeDirectIps: String = "",
    val routeBlockIps: String = "",
    val routeBypassPrivate: Boolean = true,
    val routeBlockAds: Boolean = true,
    val routeAdsTag: String = RoutingDefaults.ADS_TAG,
    val routeDomainStrategy: String = RoutingDefaults.DOMAIN_STRATEGY,

    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val splitTunnelPackages: String = "",

    val dnsPrimaryIp: String = "1.1.1.1",
    val dnsSecondaryIp: String = "8.8.8.8",
    val dnsPrimaryDoH: String = "https://1.1.1.1/dns-query",
    val dnsSecondaryDoH: String = "https://8.8.8.8/dns-query",
    val dnsQueryStrategy: String = "UseIP",

    // Android TUN still captures IPv6 when disabled here. The Xray layer blocks ::/0 so turning
    // IPv6 off can never become an operating-system bypass around the protected route.
    val ipv6Enabled: Boolean = true,
    val preferIpv6: Boolean = false,

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

    // Iran Mode. Detection is automatic; countermeasures and domestic-direct routing can be
    // switched off independently for users who want detection reporting only.
    val iranModePolicy: IranModePolicy = IranModePolicy.AUTO,
    val iranModeCountermeasures: Boolean = true,
    val iranDomesticDirect: Boolean = true,
    val iranDeepProbeEnabled: Boolean = true,
    val iranModeNotify: Boolean = false,

    // Marble Intelligence Engine
    val intelligenceEnabled: Boolean = true,
    val configCompatibilityMode: Boolean = true,
    val verifiedPerformanceTuning: Boolean = true,

    // Marble Turbo. On connect, the engine executes real transport methods against the selected
    // node (fragmentation shapes, Mux reuse, endpoint address family), measures ping and speed for
    // each, and keeps the winner. The exit node never changes, so this stays Identity-Guard safe.
    val connectTuningEnabled: Boolean = true,
    val connectTuningBudgetSec: Int = 5,
    val connectTuningMethods: Int = 8,
    /** Background passes re-measure the live route and hot-apply a materially faster method. */
    val liveTuningEnabled: Boolean = true,
    val liveTuningIntervalSec: Int = 300,
    val liveTuningPingTriggerMs: Int = 220,
    val liveTuningMinGainPercent: Int = 15,
    /** Size the userspace tunnel datapath from measured throughput instead of a fixed guess. */
    val adaptiveBufferEnabled: Boolean = true,

    // Identity Guard. Enabled by default: keep a user-started session on one public exit.
    val identityGuardEnabled: Boolean = true,
    val identityGuardStrictNoFailover: Boolean = true,
    val identityGuardSameRouteRetries: Int = 3,

    // Continuous Marble Autopilot. Cheap active-route monitoring plus real Xray challenger probes.
    val continuousOptimizerEnabled: Boolean = true,
    val optimizerIntervalSec: Int = 120,
    val optimizerCandidateCount: Int = 4,
    val optimizerDeepScanEvery: Int = 8,
    val optimizerSwitchCooldownSec: Int = 300,
    val optimizerConfirmations: Int = 2,
    val optimizerAvoidHeavyTraffic: Boolean = true,

    val healthHistoryEnabled: Boolean = true,
    val raceConnectEnabled: Boolean = true,
    val raceWidth: Int = 4,
    val smartFallbackEnabled: Boolean = true,
    val fallbackCount: Int = 3,
    val autoReconnectAfterKillSwitch: Boolean = true,
    val networkChangeRecoveryEnabled: Boolean = true,
    val adaptiveMtuEnabled: Boolean = true,
    val mtuMin: Int = 1280,
    val mtuMax: Int = 1500,
    val dnsHijackEnabled: Boolean = true,
    val adaptiveDnsEnabled: Boolean = true,
    val adaptiveDualStackEnabled: Boolean = true,
    val adaptiveThroughputEnabled: Boolean = true,
    val adaptiveThroughputMaxBytes: Int = 4 * 1024 * 1024,
    val udpProbeEnabled: Boolean = true,
    val adaptiveMuxEnabled: Boolean = true,
    val adaptiveFragmentEnabled: Boolean = true,
    val thermalAwareEnabled: Boolean = true,
    val workloadProfile: WorkloadProfile = WorkloadProfile.AUTO,

    val theme: String = "light",

    /** Settings screen reveals the low-level Xray/tunnel controls. Persisted like any other choice. */
    val expertMode: Boolean = false,

    /** Continuous non-blocking diagnostic export to Downloads/marbleng/report. */
    val debugModeEnabled: Boolean = false
)
