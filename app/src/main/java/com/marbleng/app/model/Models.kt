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
    /**
     * Evidence tier shown in Library. SMART is the endpoint-gate/Smart verdict; TUNNEL proves the
     * full Xray route. Address-level methods are internal primitives and never published here.
     */
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

/**
 * MARBLE_ROUTING_MODEL_V136 — one user rule maps to one Xray `type: field` rule.
 *
 * v2rayNG/RoutingEditActivity parity: a rule may combine a primary matcher ([kind] + [matcher])
 * with optional port, network and protocol refinements, and every rule carries a human remark.
 * Order in the list is priority; the first matching rule wins inside the engine.
 */
data class RoutingRule(
    val id: String,
    val enabled: Boolean = true,
    val kind: RoutingRuleKind,
    val matcher: String,
    val outbound: RoutingOutbound,
    val remark: String = "",
    /** Optional Xray `network` refinement: "", "tcp", "udp" or "tcp,udp". */
    val network: String = "",
    /** Optional Xray `protocol` list, comma separated (e.g. "quic,bittorrent"). */
    val protocol: String = "",
    /**
     * Optional Xray `port` refinement ("443", "80,8443", "1000-2000"). The PORT kind keeps its
     * ports here too, so one field always means one thing.
     */
    val port: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("kind", kind.name)
        put("matcher", matcher)
        put("outbound", outbound.name)
        put("remark", remark)
        if (network.isNotBlank()) put("network", network)
        if (protocol.isNotBlank()) put("protocol", protocol)
        if (port.isNotBlank()) put("port", port)
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
            remark = o.optString("remark"),
            network = o.optString("network"),
            protocol = o.optString("protocol"),
            port = o.optString("port")
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
 * iOS-styled fixed Home Presentations.
 *
 * All 4 themes use iOS glass card styling, fixed screen height (no outer page scroll),
 * and inner scrollable components where needed.
 */
enum class HomeStyle(val id: String) {
    /** Theme 1: Bottom Slide-to-connect slider, centered sub name with inner scrollable servers list, wide status card at top. */
    IOS_SLIDER("ios_slider"),

    /** Theme 2: Right floating action button that splits into disconnect and ping on connect, wide status card at top, expanded servers box. */
    IOS_FLOATING("ios_floating"),

    /** Theme 3: Bold embossed center circular connect button, wide status card at top, sub & servers box at bottom. */
    IOS_EMBOSSED("ios_embossed"),

    /** Theme 4: Modular customizable dashboard allowing user to rearrange widgets and toggle components. */
    IOS_MODULAR("ios_modular")
}

/** How tall and airy modular Home cards render in the modular theme. */
enum class ModularCardSize(val id: String) {
    COMPACT("compact"),
    COMFORTABLE("comfortable"),
    SPACIOUS("spacious")
}

fun parseModularCardSize(raw: String): ModularCardSize = when (raw.trim().lowercase()) {
    "compact" -> ModularCardSize.COMPACT
    "spacious" -> ModularCardSize.SPACIOUS
    else -> ModularCardSize.COMFORTABLE
}

fun parseHomeStyle(raw: String): HomeStyle = when (raw.trim().lowercase()) {
    "ios_slider", "slider", "theme_1", "pro" -> HomeStyle.IOS_SLIDER
    "ios_floating", "floating", "theme_2", "cosmic_orbit" -> HomeStyle.IOS_FLOATING
    "ios_embossed", "embossed", "circle", "theme_3", "cosmic_immersion" -> HomeStyle.IOS_EMBOSSED
    "ios_modular", "modular", "custom", "theme_4" -> HomeStyle.IOS_MODULAR
    else -> HomeStyle.IOS_SLIDER
}

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
        ?: when (raw.trim().uppercase()) {
            "SLIDER" -> ConnectButtonStyle.SLIDE
            "EMBOSSED" -> ConnectButtonStyle.ROUND
            else -> ConnectButtonStyle.ROUND
        }

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
 * MARBLE_PATTNG_PING_V151 — the ping menu is now the PattNG menu, and nothing else.
 *
 * V148 shipped seven methods. Five of them were estimates dressed up as measurements:
 *
 *  - `HYBRID` ("Smart") published an *endpoint gate* latency when the tunnel test could not run,
 *    so the number on Home described a TCP handshake, not the route.
 *  - `TCP_RECOMMENDED` measured a TLS ServerHello, which is a filter probe, not a delay.
 *  - `HTTP_GET` / `HTTP_HEAD` measured a public 204 origin whenever no tunnel was up — identical
 *    for every server in the list.
 *  - `ICMP` never leaves the device, so on a censored mobile link it reported the carrier, not
 *    the node.
 *
 * Two of them were real, and they are the two PattNG (patterniha/PattNG, the v2rayNG fork) uses:
 *
 *  - **Real delay** — `RealPingWorkerService.startRealPing`: a raw TCP connect to `server:port`
 *    as a cheap liveness gate (skipped for protocols where a handshake proves nothing), then the
 *    *core itself* measures an HTTP round trip through the tunnel to the delay-test URL. The
 *    published number is the core's, so it is the number the tunnel really has.
 *  - **TCP ping** — `RealPingWorkerService.startTcping` → `SpeedtestManager.socketConnectTime`:
 *    one `Socket.connect(host, port, timeout)`, and the wall-clock milliseconds it took, or a
 *    failure. Nothing is inferred from it.
 *
 * The third entry is the sing-box extended **URL test**: the core's own Clash API delay endpoint
 * (`GET /proxies/{tag}/delay?url=&timeout=`), which measures through the selected outbound with
 * unified-delay accounting. It exists because sing-box extended can measure a route MarbleNG
 * cannot see from Kotlin.
 *
 * The shared measurement budget ([PingBudget]) still decides the timeout, the sample count and
 * the concurrency of every one of them.
 */
enum class ProbeMethod {
    /** PattNG real delay: TCP gate, then a real round trip through the core to [DelayTest.URL]. */
    REAL_DELAY,

    /** PattNG TCP ping: one raw `Socket.connect` to the node's own address, timed. */
    TCP_PING,

    /** sing-box extended URL test: the core's native delay endpoint through the live tunnel. */
    URL_TEST;

    /** True when the verdict is a property of `host:port` and can be shared across duplicates. */
    fun isEndpointLevel(): Boolean = this == TCP_PING
}

/**
 * MARBLE_PATTNG_PING_V151 — the URL a real-delay / URL-test measurement fetches.
 *
 * PattNG's own constants, byte for byte: `DELAY_TEST_URL` is the primary and `DELAY_TEST_URL2`
 * the retry. A `generate_204` endpoint is the right target because the answer is empty, so the
 * measurement is a round trip and not a download. The user can override the primary from
 * Settings → Tests.
 */
object DelayTest {
    const val URL = "https://www.gstatic.com/generate_204"
    const val URL_SECONDARY = "https://www.google.com/generate_204"

    /** The raw TCP liveness gate PattNG runs before it spends a core on a node. */
    const val TCP_GATE_TIMEOUT_MS = 1_000

    fun url(configured: String): String =
        configured.trim().takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }?.let { candidate ->
            // URL-test is an HTTP client, not a string-prefix check. Reject malformed values here
            // so a bad preference can never turn every node into a red result.
            runCatching {
                val parsed = java.net.URI(candidate)
                require(parsed.host.orEmpty().isNotBlank())
                require(parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true))
                candidate
            }.getOrNull()
        } ?: URL

    /** Deterministic fallbacks for networks that filter one public generate_204 origin. */
    fun candidates(configured: String): List<String> = buildList {
        add(url(configured))
        add(URL_SECONDARY)
        add("https://www.cloudflare.com/cdn-cgi/trace")
    }.distinct()
}

/**
 * MARBLE_PING_CONTROL_V145 — the user-owned measurement budget of the whole ping engine.
 *
 * ## Why this exists
 *
 * Every ping entry point in the product used to hard-code its own budget *below* whatever the
 * engine was configured with, so the numbers on screen were the numbers of whichever clamp
 * happened to be narrowest:
 *
 *  - `AppRepository.testSource` rewrote a sweep to `benchSamples = 1, benchTimeoutSec = 2` for
 *    TCP/ICMP/DNS/Smart, i.e. one single SYN with a two-second budget for every server of a
 *    subscription. A route that answers in 2.4 s — perfectly usable — was reported dead.
 *  - `RouteProbe.smartPing` clamped the caller's budget into `1_200..6_000 ms` and always
 *    measured exactly one sample, so "Smart ping" was a single un-averaged handshake.
 *  - The Home ping clamped its budget into `500..8_000 ms`.
 *  - Concurrency was `max(tcpWorkers, 24).coerceAtMost(32)`: it could neither be lowered on a
 *    weak link (where 32 parallel handshakes distort every number) nor raised on a fast one.
 *
 * A measurement whose budget is decided by four competing clamps is not a measurement. These
 * three values are the single budget now: the user picks them once and every probe — Home,
 * group ping, Ping all, per-server ping — obeys exactly them.
 */
object PingBudget {
    const val TIMEOUT_MIN_SEC = 1
    const val TIMEOUT_MAX_SEC = 30
    const val SAMPLES_MIN = 1
    const val SAMPLES_MAX = 10
    const val CONCURRENCY_MIN = 1
    const val CONCURRENCY_MAX = 64

    /** Quiet time between two samples of the same server, so a burst is never measured. */
    const val SAMPLE_SPACING_MS = 60L

    /** Per-server timeouts offered as one-tap choices (seconds). */
    val TIMEOUT_CHOICES = listOf(2, 3, 5, 10, 15)

    /** Samples per server offered as one-tap choices. */
    val SAMPLE_CHOICES = listOf(1, 2, 3, 5, 8)

    /** Parallel servers offered as one-tap choices. */
    val CONCURRENCY_CHOICES = listOf(1, 2, 4, 8, 16, 32)

    fun timeoutSec(value: Int): Int = value.coerceIn(TIMEOUT_MIN_SEC, TIMEOUT_MAX_SEC)

    fun samples(value: Int): Int = value.coerceIn(SAMPLES_MIN, SAMPLES_MAX)

    fun concurrency(value: Int): Int = value.coerceIn(CONCURRENCY_MIN, CONCURRENCY_MAX)

    /**
     * Wall clock one server may consume: every sample gets the full per-sample budget, plus the
     * inter-sample spacing the prober inserts, plus scheduling grace. Pure, so the batch
     * deadline derived from it stays unit-testable.
     */
    fun perServerBudgetMs(timeoutSec: Int, samples: Int): Long {
        val perSample = timeoutSec(timeoutSec) * 1_000L
        val rounds = samples(samples)
        return perSample * rounds + SAMPLE_SPACING_MS * (rounds - 1) + 750L
    }
}

/** Per-server socket budget, in milliseconds, exactly as the user configured it. */
fun AppSettings.pingTimeoutMs(): Int = PingBudget.timeoutSec(pingTimeoutSec) * 1_000

/** Samples measured per server before a median is published. */
fun AppSettings.pingSampleCount(): Int = PingBudget.samples(pingSamples)

/** Servers measured at the same time. */
fun AppSettings.pingWorkers(): Int = PingBudget.concurrency(pingConcurrency)

/**
 * MARBLE_DOCK_CUSTOM_V145 — how large the bottom navigation bar renders.
 *
 * The dock is the one chrome element present on every page, so its footprint is a user choice
 * rather than a constant: a small bar hands ~20 dp of height back to the content, a large one
 * is easier to hit with a thumb on a big phone.
 */
enum class DockSize(val id: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large")
}

fun parseDockSize(raw: String): DockSize =
    DockSize.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "COMPACT" -> DockSize.SMALL
            "SPACIOUS", "BIG" -> DockSize.LARGE
            else -> DockSize.MEDIUM
        }

/**
 * MARBLE_DOCK_CUSTOM_V145 — the dock's content policy.
 *
 * Labels and icons are independent switches, but a bar with neither is not a navigation
 * control, it is three empty rectangles. These two functions resolve the requested pair into a
 * legal one exactly once, so the bar can never render blank whatever is persisted.
 */
fun dockShowsIcons(showIcons: Boolean, showLabels: Boolean): Boolean = showIcons || !showLabels

@Suppress("UNUSED_PARAMETER")
fun dockShowsLabels(showIcons: Boolean, showLabels: Boolean): Boolean = showLabels

/**
 * MARBLE_MODULAR_LAYOUT_V145 — the Theme 4 (customizer) module order, repaired.
 *
 * The persisted order is a free-form comma string, and Home used to render it literally. A
 * stored order that had lost an entry (an older build, a partial save, a hand-edited
 * preference) therefore dropped that module from the page for good — including CONNECT, which
 * left the user with a Home screen that cannot connect — and a duplicated entry rendered the
 * same card twice. The order is now always a permutation of exactly the known modules: unknown
 * tokens are dropped, duplicates collapse, and anything missing is appended in canonical order.
 */
object ModularLayout {
    const val STATUS = "STATUS"
    const val SERVERS = "SERVERS"
    const val CONNECT = "CONNECT"
    const val STATS = "STATS"
    const val SHORTCUTS = "SHORTCUTS"

    /** Canonical order, also the factory default of [AppSettings.modularCardOrder]. */
    val CANONICAL = listOf(STATUS, SERVERS, CONNECT, STATS, SHORTCUTS)

    val DEFAULT_ORDER: String = CANONICAL.joinToString(",")

    fun order(raw: String): List<String> {
        val requested = raw.split(',')
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() && it in CANONICAL }
            .distinct()
        return requested + CANONICAL.filterNot { it in requested }
    }

    fun serialize(order: List<String>): String = order(order.joinToString(",")).joinToString(",")
}


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
    /**
     * v2 — MARBLE_SMART_FAMILY_V136: installs migrated by v1 had IPv6 forced off; the smart
     * address-family policy makes IPv6-on safe (underlay gate + per-node measurement), so this
     * schema turns both family switches on exactly once. Any later user choice persists.
     */
    const val PREFS_SCHEMA_VERSION = 2
    const val SOURCE_CHOCOLATE4U = "chocolate4u-iran"
    const val STALE_ASSET_MS = 7L * 24L * 60L * 60L * 1000L

    val SOURCES: List<GeoAssetSource> = listOf(
        GeoAssetSource(
            id = SOURCE_CHOCOLATE4U,
            label = "Chocolate4U Iran",
            geoIpUrl = GEOIP_URL,
            geoSiteUrl = GEOSITE_URL,
            geoIpMirror = GEOIP_MIRROR,
            geoSiteMirror = GEOSITE_MIRROR
        ),
        GeoAssetSource(
            id = "loyalsoldier",
            label = "Loyalsoldier",
            geoIpUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
            geoSiteUrl = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
        ),
        GeoAssetSource(
            id = "v2fly",
            label = "v2fly",
            geoIpUrl = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat",
            geoSiteUrl = "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat"
        ),
        GeoAssetSource(
            id = "custom",
            label = "Custom HTTPS URLs",
            geoIpUrl = "",
            geoSiteUrl = ""
        )
    )

    fun sourceById(id: String): GeoAssetSource =
        SOURCES.firstOrNull { it.id == id } ?: SOURCES.first()
}

/** How Iran Mode decides whether the anti-filtering engine should run. */
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
    val probeMethod: ProbeMethod = ProbeMethod.REAL_DELAY,
    val probeSpeedTest: Boolean = false,

    /**
     * MARBLE_PATTNG_PING_V151 — the URL every real delay / URL test fetches through the tunnel.
     * PattNG's default; a blank or non-HTTP value falls back to [DelayTest.URL].
     */
    val delayTestUrl: String = DelayTest.URL,

    /**
     * MARBLE_SINGBOX_CORE_V151 — which core carries the tunnel. See [com.marbleng.app.core.CoreEngine]:
     * `xray` (default) or `singbox` (sing-box extended).
     */
    val coreEngineId: String = "xray",

    /** sing-box extended: measure a real round trip instead of trusting a cached handshake. */
    val singBoxUnifiedDelay: Boolean = true,

    /** sing-box extended: per-connection dial budget, in seconds. */
    val singBoxConnectTimeoutSec: Int = 10,

    /**
     * sing-box extended: keep a cache file of resolved addresses and RDRC answers between runs.
     * Off means every session resolves from scratch — slower to come up, but nothing is written
     * to disk and a stale answer can never be reused.
     */
    val singBoxCacheFile: Boolean = true,

    /** Prefer the Extended link parser for link-only profiles. Stored canonical JSON always
     * wins: the parser supports fewer XHTTP extras and must not discard edits or chain hops. */
    val singBoxPreferParser: Boolean = true,

    val benchMode: BenchMode = BenchMode.BALANCED,
    val benchCandidates: Int = 20,
    val benchSamples: Int = 3,
    val benchTimeoutSec: Int = 6,
    val benchBytes: Int = 262144,
    val tcpPrecheckTimeoutMs: Int = 1000,
    val tcpWorkers: Int = 20,

    // MARBLE_PING_CONTROL_V145 — the ping budget the user owns. See [PingBudget]: these three
    // values (and nothing else) decide how long one server may take, how many samples its
    // published latency is the median of, and how many servers are measured at the same time.
    /** Per-server budget for one ping sample, in seconds (1..30). */
    val pingTimeoutSec: Int = 5,
    /** Samples measured per server; the published latency is their median (1..10). */
    val pingSamples: Int = 3,
    /**
     * Servers measured in parallel during a sweep (1..64).
     *
     * 16 is the shipped compromise: wide enough that a large subscription finishes while the
     * user is still looking at it, narrow enough that the parallel handshakes do not distort
     * each other on a mobile link. Lower it (4, 2, 1) when accuracy matters more than speed.
     */
    val pingConcurrency: Int = 16,

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
    // MARBLE_ROUTING_SEPARATE_V143 — custom Routing is opt-in and off by default. The default
    // mode continues to apply while the dedicated Routing page stays disabled.
    val customRoutingEnabled: Boolean = false,
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
    val routeDomainMatcher: String = "hybrid",

    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.ALL_APPS,
    val splitTunnelPackages: String = "",

    val dnsPrimaryIp: String = "1.1.1.1",
    val dnsSecondaryIp: String = "8.8.8.8",
    val dnsPrimaryDoH: String = "https://1.1.1.1/dns-query",
    val dnsSecondaryDoH: String = "https://8.8.8.8/dns-query",
    val dnsQueryStrategy: String = "UseIP",

    // MARBLE_SMART_FAMILY_V136 — IPv6 and the v6 preference are ON by default; the app, not the
    // user, decides which family actually carries each connection. The decision lives in
    // AddressFamilyPolicy: the underlay must expose a real global IPv6 address, the node's own
    // IPv6 history on this network must be healthy, and an explicit user demand still wins over
    // both. Turning the master switch off remains fail-closed (::/0 blocked inside the tunnel).
    val ipv6Enabled: Boolean = true,
    val preferIpv6: Boolean = true,

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

    // Marble Intelligence Engine — permanently active. There is deliberately no user-visible
    // off switch; the engine is part of the product contract and can never be disabled.
    val intelligenceEnabled: Boolean = true,
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
     * iOS-styled Home presentations: IOS_SLIDER, IOS_FLOATING, IOS_EMBOSSED, IOS_MODULAR.
     */
    val homeStyle: String = HomeStyle.IOS_SLIDER.id,

    // Theme 4: Modular customizable dashboard properties
    val modularCardOrder: String = ModularLayout.DEFAULT_ORDER,
    val modularShowStats: Boolean = true,
    val modularShowSocks: Boolean = false,
    val modularShowShortcuts: Boolean = true,
    /** MARBLE_MODULAR_LAYOUT_V145 — the status banner is a module like every other one. */
    val modularShowStatus: Boolean = true,
    /** MARBLE_MODULAR_LAYOUT_V145 — the server picker is a module like every other one. */
    val modularShowServers: Boolean = true,
    val modularConnectStyle: String = "SLIDER",
    /** Modular Home card sizing, changed from the Theme 4 customizer. */
    val modularCardSize: String = ModularCardSize.COMPACT.id,
    /** Fine-grained server-card height (dp) when the user drags the custom resize slider. */
    val modularCardHeightDp: Int = 180,

    /**
     * MARBLE_MODULAR_HIDE_CUSTOMIZER_V151 — hide the "Customize Layout" chip from Home.
     *
     * The chip is the customizer's only way in, so hiding it is only safe because two ways back
     * remain: long-press the modular studio title, and Settings → Home presentation has the same
     * switch. Nothing about the saved layout changes when it is hidden.
     */
    val modularHideCustomizerButton: Boolean = false,

    /**
     * MARBLE_CONNECT_BUTTON_V121 — the connection-button silhouette shown on every Home style:
     * the large round shutter (default), the slide-to-connect track or the classic power switch.
     */
    val connectButtonStyle: String = ConnectButtonStyle.ROUND.id,

    /** MARBLE_NIGHT_OUTLINES_V112 — dark-theme hairline personality for every frame/card. */
    val darkOutlineStyle: String = DarkOutlineStyle.SUBTLE.id,

    // MARBLE_DOCK_CUSTOM_V145 — the bottom navigation bar is customizable from Settings.
    /** Draw the tab captions in the dock. */
    val dockShowLabels: Boolean = true,
    /** Draw the tab glyphs in the dock. */
    val dockShowIcons: Boolean = true,
    /** Dock footprint: small, medium (default) or large. */
    val dockSize: String = DockSize.MEDIUM.id,

    /** MARBLE_BILINGUAL_V110 — "system" follows the device locale; "en"/"fa" are overrides. */
    val appLanguage: String = AppLanguage.SYSTEM.id,

    /** Settings screen reveals the low-level Xray/tunnel controls. Persisted like any other choice. */
    val expertMode: Boolean = false,

    /** Continuous non-blocking diagnostic export to Downloads/marbleng/report. */
    val debugModeEnabled: Boolean = false
)
