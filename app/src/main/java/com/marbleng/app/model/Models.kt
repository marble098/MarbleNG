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
    /** Robust EWMA IPDV; misses break adjacency. MARBLE_REALTIME_ENGINE_V70 */
    val jitterMs: Double = 0.0,
    /** First verified response timing; never used as the final delay when a warmer try wins. */
    val warmupMs: Double = 0.0,
    val sampleCount: Int = 0,
    val p90LatencyMs: Double = 0.0,
    val p95LatencyMs: Double = 0.0,
    val medianJitterMs: Double = 0.0,
    val p95JitterMs: Double = 0.0,
    val madLatencyMs: Double = 0.0,
    val lossPercent: Double = 0.0,
    val spikePercent: Double = 0.0,
    /** RTT while a bounded throughput probe was in flight; zero means unknown. */
    val loadedLatencyMs: Double = 0.0,
    /** Compact stage evidence retained for Bug Finder; never shown as a synthetic ping. */
    val failureReason: String = "",
    /**
     * MARBLE_SMART_RANK_V90: successful handshakes / total attempts across the 2-3 backoff
     * retries the rank probe performs before it settles on a verdict. 0.0 means unknown (legacy
     * single-shot paths). This is the TCP-handshake-success signal in the weighted multi-signal
     * score, so one HTTPS timeout can no longer fail an otherwise healthy node.
     */
    val tcpHandshakeSuccessRatio: Double = 0.0,
    /** MARBLE_SMART_RANK_V90: total handshake attempts the probe performed before settling. */
    val handshakeAttempts: Int = 0
)

data class ConnectionRecord(val profileId: String, val name: String, val at: Long, val reason: String)

enum class BenchMode { RELIABLE, BALANCED, FAST, TURBO, CUSTOM }
enum class ConnectionMode { FULL_TUN, LOCAL_PROXY }
enum class RoutingMode { PROXY_ALL, BYPASS_PRIVATE, GEO_DIRECT, CUSTOM }

enum class RoutingRuleKind { GEOSITE, GEOIP, DOMAIN, IP, PORT }
enum class RoutingOutbound { PROXY, DIRECT, BLOCK }

data class RoutingRule(
    val id: String,
    val enabled: Boolean = true,
    val kind: RoutingRuleKind,
    val matcher: String,
    val outbound: RoutingOutbound,
    val remark: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("kind", kind.name)
        put("matcher", matcher)
        put("outbound", outbound.name)
        put("remark", remark)
    }

    companion object {
        fun fromJson(o: JSONObject) = RoutingRule(
            id = o.optString("id").ifBlank { java.util.UUID.randomUUID().toString().take(12) },
            enabled = o.optBoolean("enabled", true),
            kind = runCatching { RoutingRuleKind.valueOf(o.optString("kind", "DOMAIN")) }
                .getOrDefault(RoutingRuleKind.DOMAIN),
            matcher = o.optString("matcher"),
            outbound = runCatching { RoutingOutbound.valueOf(o.optString("outbound", "PROXY")) }
                .getOrDefault(RoutingOutbound.PROXY),
            remark = o.optString("remark")
        )
    }
}

data class GeoAssetSource(
    val id: String,
    val label: String,
    val geoIpUrl: String,
    val geoSiteUrl: String,
    val geoIpMirror: String = "",
    val geoSiteMirror: String = ""
)
enum class SplitTunnelMode { ALL_APPS, ONLY_SELECTED, BYPASS_SELECTED }
enum class WorkloadProfile { AUTO, INTERACTIVE, STREAMING, STABILITY, STEALTH }
/**
 * MARBLE_SERVERS_QUERY_V120 — how the Servers list is ordered.
 *
 * [DEFAULT] keeps the order the source itself published (a subscription owner numbers their nodes
 * on purpose); [COUNTRY] groups the list by the country resolved from each node's label.
 */
enum class NodeSortMode { DEFAULT, PING, SCORE, NAME, PROTOCOL, SOURCE, COUNTRY }

/**
 * MARBLE_HOME_STYLE_V110 / MARBLE_SIGNATURE_HOME_V112 / MARBLE_HOME_STYLE_TRIM_V121
 *
 * The three user-selectable Home (connection) presentations. Every style renders exactly the same
 * runtime evidence — node, source, IP + flag + three actions, session uptime and the one-shot
 * connection ping — so switching a style is purely a presentation choice and never changes what
 * the user can see or do.
 *
 * MARBLE_HOME_STYLE_TRIM_V121 removed the Bioluminescent and Parametric presentations from the
 * whole product; any persisted value naming them falls back to the Signature studio.
 */
enum class HomeStyle(val id: String) {
    /**
     * MARBLE_SIGNATURE_HOME_V112 — the dedicated professional Signature studio.
     *
     * A fixed, fully customizable connection surface that is the product's default: a status
     * banner, corner quick actions (+ / ping / shortcut / more), the optional floating connect
     * button (app-wide, draggable, v2rayNG-style) and an accent-tinted animated aurora backdrop.
     */
    PRO("pro"),

    /** Cosmic orbit dashboard: orbiting system card plus a network-speed graph. */
    COSMIC_ORBIT("cosmic_orbit"),

    /** Cosmic orbit, full-screen immersion: orbit above, cosmic energy flower below. */
    COSMIC_IMMERSION("cosmic_immersion")
}

fun parseHomeStyle(raw: String): HomeStyle =
    HomeStyle.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: HomeStyle.PRO

/**
 * MARBLE_BILINGUAL_V110
 *
 * Product language. SYSTEM follows the Android device locale on every launch; EN/FA are explicit
 * user overrides that persist like any other preference.
 */
enum class AppLanguage(val id: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    PERSIAN("fa")
}

fun parseAppLanguage(raw: String): AppLanguage =
    AppLanguage.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: AppLanguage.SYSTEM

/**
 * User-selectable product typefaces. The default keeps Persian text readable on every device.
 *
 * MARBLE_SYSTEM_FONT_V112: SYSTEM renders with the device's own default typeface; Persian copy
 * still forces the bundled Vazirmatn ramp inside AetherTheme so Persian shaping never degrades.
 */
enum class AppFont(val id: String, val label: String) {
    VAZIR("vazir", "Vazir"),
    SYSTEM("system", "System"),
    GOOGLE_SANS("google_sans", "Google Sans"),
    TIMES_NEW_ROMAN("times_new_roman", "Times New Roman")
}

fun parseAppFont(raw: String): AppFont =
    AppFont.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: AppFont.VAZIR

/**
 * MARBLE_SIGNATURE_HOME_V112 — the user-chosen accent that drives the whole Signature studio
 * (banner, power rings and aurora backdrop).
 */
enum class ProAccent(val id: String, val label: String) {
    ELECTRIC("electric", "Electric"),
    EMERALD("emerald", "Emerald"),
    AMETHYST("amethyst", "Amethyst"),
    AMBER("amber", "Amber"),
    CYAN("cyan", "Ice")
}

fun parseProAccent(raw: String): ProAccent =
    ProAccent.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: ProAccent.ELECTRIC

/**
 * MARBLE_SIGNATURE_HOME_V112 — where the Signature status banner is rendered.
 */
enum class ProBannerScope(val id: String) {
    HOME("home"),
    ALL("all")
}

fun parseProBannerScope(raw: String): ProBannerScope =
    ProBannerScope.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: ProBannerScope.HOME

/**
 * MARBLE_SIGNATURE_HOME_V112 — which quick action the Signature corner shortcut button runs.
 */
enum class ProShortcut(val id: String) {
    LIBRARY("library"),
    RANK("rank"),
    PRIVACY("privacy"),
    ROUTING("routing"),
    TESTS("tests")
}

fun parseProShortcut(raw: String): ProShortcut =
    ProShortcut.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: ProShortcut.LIBRARY

/**
 * MARBLE_CONNECT_BUTTON_V121 — the connection-button silhouettes, selectable from Settings.
 *
 * Every model renders correctly inside every Home presentation: the model controls the button's
 * own drawing while each Home style supplies its own tone and halo, so a choice is never tied to
 * a specific theme. None of them ever drifts or changes position — the primary action of the
 * product stays exactly where the finger expects it, and only colour and copy animate.
 *
 *  - [ROUND]    the large round shutter. The product default.
 *  - [SLIDE]    a slide-to-connect track the user drags from left to right, like a safety switch.
 *  - [CLASSIC]  the classic rectangular power switch of the old desktop clients.
 *
 * MARBLE_CONNECT_BUTTON_STYLES_V132 — two docked additions. Both live at the floor of the Home
 * page instead of inside the hero, so the primary action is always one thumb away without the
 * artwork having to fight it for space:
 *
 *  - [STREAM]   a full-width floor bar carrying a light band that travels right to left across
 *               the track. The motion is the button's own "the route is live" language: it only
 *               runs while the tunnel is up, and it reads identically in LTR and RTL.
 *  - [FLOATING] a compact pill floating above the bottom edge of the page, v2rayNG-style but
 *               docked rather than draggable, so it can never cover the readouts behind it.
 */
enum class ConnectButtonStyle(val id: String) {
    ROUND("round"),
    SLIDE("slide"),
    CLASSIC("classic"),
    STREAM("stream"),
    FLOATING("floating")
}

fun parseConnectButtonStyle(raw: String): ConnectButtonStyle =
    ConnectButtonStyle.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: ConnectButtonStyle.ROUND

/**
 * MARBLE_NIGHT_OUTLINES_V112 — dark-theme frame outline personality. The user can strengthen
 * every card/frame hairline, tint it with the brand accent, or dissolve the borders entirely.
 */
enum class DarkOutlineStyle(val id: String) {
    SUBTLE("subtle"),
    BOLD("bold"),
    COLORED("colored"),
    HIDDEN("hidden")
}

fun parseDarkOutlineStyle(raw: String): DarkOutlineStyle =
    DarkOutlineStyle.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: DarkOutlineStyle.SUBTLE

/**
 * MARBLE_HOME_SESSION_EVIDENCE_V110
 *
 * Lifecycle of the one-shot Home connection ping. FAILED is a first-class outcome: a probe that
 * got no verified response must say so instead of displaying an estimate.
 */
enum class ConnectionPingState { IDLE, MEASURING, MEASURED, FAILED }

/** Live state of one node inside a running test batch, shown on the node's own card. */
enum class ProbeState { IDLE, QUEUED, TESTING }

/**
 * MARBLE_UNIFIED_PING_V121 / MARBLE_PROBE_TOOLKIT_V130 — the single ping engine of the whole product.
 *
 * One user choice in Settings → Tests → Ping drives every measurement the user can trigger: the
 * Home ping button, the per-source ping in the Servers three-dot menu and the page-wide ping.
 * There is no second, hidden ping path any more.
 *
 *  - [HYBRID] "Smart ping" — the product default: a fast TCP/DNS reachability gate followed by
 *    the real verified HTTPS measurement. Returns quickly when the gate fails, accurately when
 *    it succeeds. Inspired by PattNG's multi-phase probing and Lumen's confidence scoring.
 *  - [TUNNEL] the real tunnel measurement only — HTTPS through the SOCKS proxy, proving the full
 *    route including TLS. Slowest, most accurate. Used by v2rayNG's "real delay" test.
 *  - [TCP] a plain TCP SYN handshake against the endpoint. Fastest, proves reachability only.
 *    Uses Happy-Eyeballs address racing (Exclave-style).
 *  - [ICMP] a classic ICMP echo against the endpoint. Bypasses the proxy entirely.
 *  - [HTTP] a direct HTTPS GET to a well-known 204 endpoint (no tunnel). Proves the underlay
 *    network path including DNS, TCP and TLS. Useful for testing raw network quality.
 *  - [DNS] DNS resolution time for a well-known domain. Fastest indirect check, proves only
 *    that the local DNS path works (Incy-style).
 */
enum class ProbeMethod { TUNNEL, TCP, ICMP, HYBRID, HTTP, DNS }

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

/**
 * Presets for Marble Freedom anti-DPI fragmentation engine.
 *
 * The four operator presets are researched, per-carrier steel profiles (MCI/Hamrah-e-Aval,
 * MTN Irancell, Shatel, Rightel). They are auto-applied by Marble Freedom while
 * [AppSettings.freedomOperatorAuto] is on and Iran Mode has identified the operator.
 */
enum class FreedomPreset {
    SMART_ADAPTIVE,
    MULTI_LAYER_CASCADE,
    SNI_SHREDDER,
    AGGRESSIVE_RECORD_SPLIT,
    EXTREME_ANTI_DPI,
    SHATEL,
    HAMRAH_AVAL,
    IRANCELL,
    RIGHTEL,
    CUSTOM
}

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
    val nodeSortMode: NodeSortMode = NodeSortMode.DEFAULT,
    val nodeSortReverse: Boolean = false,

    // MARBLE_SERVERS_QUERY_V120 — the Servers filter bar. Every field is a control the user can
    // see: the protocol chip, and the three switches inside the advanced-filter menu. They persist
    // so a working filter set survives a restart, and Reset returns them to these defaults.
    /** Wire-scheme filter for the Servers list; blank shows every protocol. */
    val serversProtocolFilter: String = "",
    /** Hide servers whose latest stored measurement failed. */
    val serversOnlyReachable: Boolean = false,
    /** Hide servers slower than this many milliseconds; 0 turns the ceiling off. */
    val serversMaxPingMs: Int = 0,
    /** Bucket the Servers list by resolved country instead of by source. */
    val serversGroupByCountry: Boolean = false,

    val rememberLast: Boolean = true,
    val subscriptionAutoRefresh: Boolean = true,
    // MARBLE_APP_UPDATE_SETTING_V102
    /** Check GitHub Releases whenever MarbleNG returns to the foreground. */
    val appUpdateCheckEnabled: Boolean = true,
    val subscriptionRefreshHours: Int = 12,
    // MARBLE_MANUAL_BUCKET_V122 — the Manual bucket is a permanent, always-on local source.
    // The old opt-in toggle is gone: every install can save servers locally without a setting.

    // Home composition. Hiding a card does not disable its underlying engine.
    val homeShowSummaryMetrics: Boolean = false,
    val homeShowIranMode: Boolean = false,
    val homeShowQuickActions: Boolean = true,
    val homeShowLiveQuality: Boolean = true,
    val homeShowServerSelector: Boolean = true,
    val homeShowRouteDetails: Boolean = true,
    val homeShowRouteRibbon: Boolean = true,
    val homeShowFreedomSwitch: Boolean = true,

    /**
     * Permanent Home switch: when on, Connect uses the built-in Freedom fragment profile
     * instead of a Library node. Not an anonymity proxy.
     */
    val serverlessModeEnabled: Boolean = false,

    /**
     * Optional public metadata lookup for the selected server endpoint shown on Home.
     * Enabled by default; users can hide it, and only the resolved public server IP is queried.
     */
    val serverIntelEnabled: Boolean = true,

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
    val geoAssetSourceId: String = RoutingDefaults.SOURCE_CHOCOLATE4U,
    val routingRulesJson: String = "",
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
    val ipv6Enabled: Boolean = false,
    val preferIpv6: Boolean = false,

    /**
     * MARBLE_MEASURED_FAMILY_V133 — *transient*, never persisted.
     *
     * Marble Intelligence sets this when the node's own history shows IPv6 is unhealthy on this
     * physical network (failure streak, success EWMA below the usable floor, or sustained jitter).
     * It has to travel inside the settings object because [AddressFamilyPolicy] is consulted from
     * four different places — the Xray config writer, the delay-test config, the Kotlin probers and
     * Bug Finder — and a verdict that only one of them sees is how the diagnostics ended up
     * reporting "IPv6 preferred, IPv4 raced after 60 ms" for a tunnel that was measuring IPv6 as
     * broken. It is deliberately absent from [com.marbleng.app.data.AppStore]: a verdict belongs to
     * the network session that produced it, and restoring a stale one after a reboot would demote a
     * family that was never measured on the new link.
     */
    val measuredIpv6Unhealthy: Boolean = false,

    /**
     * MARBLE_RESOLVER_EVIDENCE_V134 — *transient*, never persisted. Comma-separated resolver
     * endpoints that the current network session observed failing decisively (DoH deadline storms,
     * EOF bursts, TLS or certificate failures attributed to that exact endpoint by
     * [com.marbleng.app.core.ResolverEvidencePolicy]).
     *
     * It travels inside the settings object for the same reason [measuredIpv6Unhealthy] does: the
     * resolver list is assembled by the config writer, and a verdict that only the intelligence
     * layer knew about is exactly why 29 attributed `DoH deadline` events never changed a single
     * emitted resolver. Demotion is time-bounded and reversible, so a verdict belongs to the
     * session that measured it and is deliberately absent from
     * [com.marbleng.app.data.AppStore].
     */
    val measuredDnsDemotedEndpoints: String = "",

    /**
     * MARBLE_RESOLVER_EVIDENCE_V134 — *transient*, never persisted. True only when an endpoint that
     * is about to be emitted is decisively failing, which is the one condition under which racing
     * every encrypted resolver beats paying a dead one's full deadline on each cold lookup.
     */
    val measuredDnsParallel: Boolean = false,

    // Realtime transport adaptation. MARBLE_REALTIME_ENGINE_V70
    val adaptiveHappyEyeballsEnabled: Boolean = true,
    val happyEyeballsTryDelayMs: Int = 60,
    val happyEyeballsMaxConcurrent: Int = 4,
    val adaptiveTcpFastOpenEnabled: Boolean = true,
    val tcpFastOpenEnabled: Boolean = false,
    val adaptiveMssEnabled: Boolean = true,
    /** 0 = kernel/default unless a measured Marble Turbo plan supplies an MSS. */
    val tcpMaxSeg: Int = 0,

    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "100-200",
    val fragmentInterval: String = "10-20",
    val fragmentMaxSplit: String = "",
    val fragmentInnerEnabled: Boolean = false,
    val fragmentInnerPackets: String = "1-1",
    val fragmentInnerLength: String = "1",
    val fragmentInnerInterval: String = "4",
    val fragmentInnerMaxSplit: String = "517",

    // Marble Freedom Anti-DPI Multi-Layer Fragmentation & Smart Multi-DNS
    val freedomPreset: FreedomPreset = FreedomPreset.SMART_ADAPTIVE,
    /**
     * When on (default), Marble Freedom matches the detected Iranian operator (MCI/Hamrah-e-Aval,
     * MTN Irancell, Shatel, Rightel) to its researched steel recipe automatically while the preset
     * is SMART_ADAPTIVE. A user-pinned operator preset always wins.
     */
    val freedomOperatorAuto: Boolean = true,
    // Default is the official XTLS 2-hop chain (outer → full-fragment). A middle hop is
    // Custom-only; the previous 3-layer default stalled multi-CDN first flights.
    val freedomLayerCount: Int = 2,
    // Outer hop defaults follow GFW-knocker's packet-split (1-1 / 1-3 / 5-10). The old
    // "tlshello" record-rewriting mode is no longer a default: it emits complete tiny TLS
    // records that real servers and Iran's 2026 DPI reject/RST (Xray #4370, #5969; runtime
    // verified on v26.7.28 against Fastly/Cloudflare/GitHub/AWS).
    val freedomOuterPackets: String = "1-1",
    val freedomOuterLength: String = "1-3",
    val freedomOuterInterval: String = "5-10",
    val freedomOuterMaxSplit: String = "",
    val freedomMiddleEnabled: Boolean = false,
    val freedomMiddlePackets: String = "1-3",
    val freedomMiddleLength: String = "10-30",
    val freedomMiddleInterval: String = "5-10",
    val freedomMiddleMaxSplit: String = "768",
    val freedomInnerEnabled: Boolean = true,
    val freedomInnerPackets: String = "1-1",
    val freedomInnerLength: String = "1",
    val freedomInnerInterval: String = "4",
    val freedomInnerMaxSplit: String = "517",

    // Smart Multi-DNS for Marble Freedom
    val freedomDnsAuto: Boolean = true,
    val freedomDnsPrimaryIp: String = "1.1.1.1",
    val freedomDnsSecondaryIp: String = "8.8.8.8",
    val freedomDnsPrimaryDoH: String = "https://1.1.1.1/dns-query",
    val freedomDnsSecondaryDoH: String = "https://8.8.8.8/dns-query",
    val freedomDnsFallbackDoH: String = "https://9.9.9.9/dns-query",
    // Domain-host resolvers must be pin-able in Xray dns.hosts (see XrayConfigHardener); a
    // hostname DoH without a pin would bootstrap through the poisoned OS resolver or recurse
    // inside the Freedom DNS module. doh.sb is intentionally absent: its addresses are not
    // stable enough to pin, so it would silently fail inside the encrypted path.
    val freedomDnsCleanResolvers: String = "https://1.1.1.1/dns-query,https://8.8.8.8/dns-query,https://9.9.9.9/dns-query,https://dns.adguard-dns.com/dns-query,https://dns.shecan.ir/dns-query",
    val freedomDnsQueryStrategy: String = "UseIP",
    // Official XTLS uses IPOnDemand so domain rules resolve before matching.
    val freedomDomainStrategy: String = "IPOnDemand",
    val freedomDnsHijack: Boolean = true,
    val freedomDirectDomestic: Boolean = true,

    // Streaming reliability for Marble Freedom. YouTube and some media surfaces prefer QUIC; on
    // filtered links those UDP/443 handshakes can stall forever. Default to a fast TCP fallback so
    // the TLS path uses the fragmented Freedom chain, while still letting experts re-enable padded
    // QUIC from Settings when their network carries it.
    val freedomForceTcpForStreaming: Boolean = true,

    // UDP Noise & Padding Defense for Marble Freedom (dedicated outbound, official XTLS shape)
    val freedomUdpNoiseEnabled: Boolean = true,
    val freedomUdpNoisePacket4: String = "1250",
    val freedomUdpNoiseDelay4: String = "10",
    val freedomUdpNoisePacket6: String = "1230",
    val freedomUdpNoiseDelay6: String = "10",
    // Pairs of IPv4+IPv6 noise bursts; official ships ~13 each, 6 is a solid mobile default.
    val freedomUdpNoiseCount: Int = 6,

    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 16,
    val muxUdp443: String = "skip",

    // Iran Mode. Detection is automatic; countermeasures and domestic-direct routing can be
    // switched off independently for users who want detection reporting only.
    val iranModePolicy: IranModePolicy = IranModePolicy.OFF,
    val iranModeCountermeasures: Boolean = true,
    val iranDomesticDirect: Boolean = true,
    val iranDeepProbeEnabled: Boolean = true,
    val iranModeNotify: Boolean = false,

    // Marble Intelligence Engine
    val intelligenceEnabled: Boolean = false,
    val configCompatibilityMode: Boolean = true,
    val verifiedPerformanceTuning: Boolean = true,

    // Marble Turbo. On connect, the engine executes real transport methods against the selected
    // node (fragmentation shapes, Mux reuse, endpoint address family), measures ping and speed for
    // each, and keeps the winner. The exit node never changes, so this stays Identity-Guard safe.
    val connectTuningEnabled: Boolean = false,
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
    val continuousOptimizerEnabled: Boolean = false,
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
    /** Product typeface selected in the standard Settings workspace. */
    val fontFamily: String = AppFont.VAZIR.id,

    /**
     * MARBLE_HOME_STYLE_V110 / MARBLE_SIGNATURE_HOME_V112 — which Home presentation the user
     * picked. The Signature studio (PRO) is the product default: the main connection theme ships
     * enabled out of the box and every one of its layers stays independently customizable below.
     */
    val homeStyle: String = HomeStyle.PRO.id,

    // MARBLE_SIGNATURE_HOME_V112 — the Signature studio customization surface. Every layer of
    // the professional Home is an independent user choice; nothing is hard-wired.
    /** The app-wide draggable floating connect button (v2rayNG-style shutter). Off for a clean first run. */
    val proFloatingButtonEnabled: Boolean = false,
    /** Slim status banner (connection state + selected server) rendered like a persistent strip. Off for a clean first run. */
    val proStatusBannerEnabled: Boolean = false,
    /** Whether the banner lives on Home only or rides on top of every page. */
    val proBannerScope: String = ProBannerScope.HOME.id,
    /** Corner action cluster: add server, grab ping, one configurable shortcut, more (⋮). Off for a clean first run. */
    val proCornerActionsEnabled: Boolean = false,
    /** Accent driving the Signature studio surfaces and animations. */
    val proAccent: String = ProAccent.ELECTRIC.id,
    /** Which quick action the corner shortcut button runs. */
    val proShortcut: String = ProShortcut.LIBRARY.id,

    /**
     * MARBLE_CONNECT_BUTTON_V121 — the connection-button silhouette shown on every Home style:
     * the large round shutter (default), the slide-to-connect track or the classic power switch.
     */
    val connectButtonStyle: String = ConnectButtonStyle.ROUND.id,

    /** MARBLE_NIGHT_OUTLINES_V112 — dark-theme hairline personality for every frame/card. */
    val darkOutlineStyle: String = DarkOutlineStyle.SUBTLE.id,

    /** MARBLE_BILINGUAL_V110 — "system" follows the device locale; "en"/"fa" are overrides. */
    val appLanguage: String = AppLanguage.SYSTEM.id,

    /** Settings screen reveals the low-level Xray/tunnel controls. Persisted like any other choice. */
    val expertMode: Boolean = false,

    /** Continuous non-blocking diagnostic export to Downloads/marbleng/report. */
    val debugModeEnabled: Boolean = false
)
