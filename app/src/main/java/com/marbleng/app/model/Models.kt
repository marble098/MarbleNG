package com.marbleng.app.model

import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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
    val usedMux: Boolean = false,
    val probeKind: String = "TUNNEL",
    val jitterMs: Double = 0.0,
    val warmupMs: Double = 0.0,
    val sampleCount: Int = 0,
    val p90LatencyMs: Double = 0.0,
    val p95LatencyMs: Double = 0.0,
    val medianJitterMs: Double = 0.0,
    val p95JitterMs: Double = 0.0,
    val madLatencyMs: Double = 0.0,
    val lossPercent: Double = 0.0,
    val spikePercent: Double = 0.0,
    val loadedLatencyMs: Double = 0.0,
    val failureReason: String = "",
    val tcpHandshakeSuccessRatio: Double = 0.0,
    val handshakeAttempts: Int = 0,
    val measuredAtMs: Long = 0L
) {
    fun toJson() = JSONObject().apply {
        put("profileId", profileId)
        put("name", name)
        put("success", success)
        put("latencyMs", latencyMs)
        put("bytesPerSecond", bytesPerSecond)
        put("score", score)
        put("udpSuccess", udpSuccess)
        put("interactiveScore", interactiveScore)
        put("streamingScore", streamingScore)
        put("stabilityScore", stabilityScore)
        put("resilienceScore", resilienceScore)
        put("usedMux", usedMux)
        put("probeKind", probeKind)
        put("jitterMs", jitterMs)
        put("warmupMs", warmupMs)
        put("sampleCount", sampleCount)
        put("p90LatencyMs", p90LatencyMs)
        put("p95LatencyMs", p95LatencyMs)
        put("medianJitterMs", medianJitterMs)
        put("p95JitterMs", p95JitterMs)
        put("madLatencyMs", madLatencyMs)
        put("lossPercent", lossPercent)
        put("spikePercent", spikePercent)
        put("loadedLatencyMs", loadedLatencyMs)
        put("failureReason", failureReason)
        put("tcpHandshakeSuccessRatio", tcpHandshakeSuccessRatio)
        put("handshakeAttempts", handshakeAttempts)
        put("measuredAtMs", measuredAtMs)
    }

    companion object {
        fun fromJson(o: JSONObject) = BenchmarkResult(
            profileId = o.optString("profileId"),
            name = o.optString("name"),
            success = o.optInt("success"),
            latencyMs = o.optDouble("latencyMs"),
            bytesPerSecond = o.optDouble("bytesPerSecond"),
            score = o.optDouble("score"),
            udpSuccess = o.optInt("udpSuccess"),
            interactiveScore = o.optDouble("interactiveScore"),
            streamingScore = o.optDouble("streamingScore"),
            stabilityScore = o.optDouble("stabilityScore"),
            resilienceScore = o.optDouble("resilienceScore"),
            usedMux = o.optBoolean("usedMux"),
            probeKind = o.optString("probeKind").takeIf { it.isNotBlank() } ?: "TUNNEL",
            jitterMs = o.optDouble("jitterMs"),
            warmupMs = o.optDouble("warmupMs"),
            sampleCount = o.optInt("sampleCount"),
            p90LatencyMs = o.optDouble("p90LatencyMs"),
            p95LatencyMs = o.optDouble("p95LatencyMs"),
            medianJitterMs = o.optDouble("medianJitterMs"),
            p95JitterMs = o.optDouble("p95JitterMs"),
            madLatencyMs = o.optDouble("madLatencyMs"),
            lossPercent = o.optDouble("lossPercent"),
            spikePercent = o.optDouble("spikePercent"),
            loadedLatencyMs = o.optDouble("loadedLatencyMs"),
            failureReason = o.optString("failureReason"),
            tcpHandshakeSuccessRatio = o.optDouble("tcpHandshakeSuccessRatio"),
            handshakeAttempts = o.optInt("handshakeAttempts"),
            measuredAtMs = o.optLong("measuredAtMs")
        )
    }
}

data class ConnectionRecord(val profileId: String, val name: String, val at: Long, val reason: String)

data class UsageSessionRecord(
    val profileId: String,
    val name: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val bytes: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("profileId", profileId)
        put("name", name)
        put("startedAt", startedAtMs)
        put("endedAt", endedAtMs)
        put("bytes", bytes)
    }

    companion object {
        fun fromJson(o: JSONObject) = UsageSessionRecord(
            o.optString("profileId"),
            o.optString("name"),
            o.optLong("startedAt"),
            o.optLong("endedAt"),
            o.optLong("bytes")
        )
    }
}

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
    val remark: String = "",
    val network: String = "",
    val protocol: String = "",
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
enum class NodeSortMode { DEFAULT, PING, SCORE, NAME, PROTOCOL, SOURCE, COUNTRY }

enum class ServerLayout(val id: String) {
    ROW("row"),

    GRID("grid");

    companion object {
        val DEFAULT: ServerLayout get() = ROW
    }
}

fun parseServerLayout(raw: String): ServerLayout =
    ServerLayout.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "list", "rows", "linear" -> ServerLayout.ROW
            "tiles", "box", "boxes", "compact", "card", "cards" -> ServerLayout.GRID
            else -> ServerLayout.DEFAULT
        }

enum class HomePingAction(val id: String) {
    ROUTE("route"),
    GROUP("group"),
    LIBRARY("library"),
    TESTS("tests");

    companion object {
        val DEFAULT: HomePingAction get() = ROUTE
    }
}

fun parseHomePingAction(raw: String): HomePingAction =
    HomePingAction.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "node", "server", "route", "live", "current", "single" -> HomePingAction.ROUTE
            "group", "subscription", "source", "batch" -> HomePingAction.GROUP
            "all", "library", "everything", "sweep" -> HomePingAction.LIBRARY
            "tests", "test", "settings", "open" -> HomePingAction.TESTS
            else -> HomePingAction.DEFAULT
        }

enum class GeoPrecision(val id: String) {
    STANDARD("standard"),
    ENHANCED("enhanced"),
    MAXIMUM("maximum");

    companion object {
        val DEFAULT: GeoPrecision get() = ENHANCED
    }
}

fun parseGeoPrecision(raw: String): GeoPrecision =
    GeoPrecision.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "off", "none", "basic", "geo", "tags" -> GeoPrecision.STANDARD
            "high", "max", "strict", "aggressive", "keyword" -> GeoPrecision.MAXIMUM
            "normal", "balanced", "default", "curated" -> GeoPrecision.ENHANCED
            else -> GeoPrecision.DEFAULT
        }

object XrayLogLevels {
    val ALL: List<String> = listOf("none", "error", "warning", "info", "debug")

    fun parse(raw: String): String =
        ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "error"
}

object SingBoxLogLevels {
    val ALL: List<String> = listOf("trace", "debug", "info", "warn", "error", "fatal", "panic")

    fun parse(raw: String): String =
        ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "warn"
}

object XrayDomainStrategies {
    val ALL: List<String> = listOf(
        "AsIs", "UseIP", "UseIPv4", "UseIPv6", "ForceIP", "ForceIPv4", "ForceIPv6"
    )

    fun parse(raw: String): String =
        ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "AsIs"
}

object SingBoxMuxProtocols {
    val ALL: List<String> = listOf("h2mux", "smux", "yamux")

    fun parse(raw: String): String =
        ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "h2mux"
}

object XrayMuxUdp443Modes {
    val ALL: List<String> = listOf("skip", "allow", "reject")

    fun parse(raw: String): String =
        ALL.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: "skip"
}

enum class HomeStyle(val id: String) {
    IOS_SLIDER("ios_slider"),

    IOS_FLOATING("ios_floating"),

    IOS_EMBOSSED("ios_embossed"),

    IOS_MODULAR("ios_modular"),

    ROUTE_ATELIER("route_atelier");

    companion object {
        val DEFAULT: HomeStyle get() = ROUTE_ATELIER
    }
}

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
    "route_atelier", "route", "atelier", "theme_5" -> HomeStyle.ROUTE_ATELIER

    else -> HomeStyle.DEFAULT
}

object TextScale {
    // MARBLE_UI_CUSTOM_SIZE_V220 — the ladder is finer and wider than V215's four rungs: presets
    // every 10 % from 80 % to 140 %, plus a 5 % slider in Settings for anything between. The
    // default stays 100 % and every value is still clamped, so old installs keep working.
    const val DEFAULT_PERCENT = 100
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 140
    const val SLIDER_STEP_PERCENT = 5
    val STEPS: List<Int> = listOf(80, 90, 100, 110, 120, 130, 140)

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun snapped(percent: Int): Int {
        val clamped = clamp(percent)
        val steps = ((clamped - MIN_PERCENT + SLIDER_STEP_PERCENT / 2) / SLIDER_STEP_PERCENT)
        return (MIN_PERCENT + steps * SLIDER_STEP_PERCENT).coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    fun factor(percent: Int): Float = clamp(percent) / 100f
}

enum class AppLanguage(val id: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    PERSIAN("fa")
}

fun parseAppLanguage(raw: String): AppLanguage =
    AppLanguage.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: AppLanguage.SYSTEM

enum class AppFont(val id: String, val label: String) {
    VAZIR("vazir", "Vazir"),
    SYSTEM("system", "System"),
    GOOGLE_SANS("google_sans", "Google Sans"),
    TIMES_NEW_ROMAN("times_new_roman", "Times New Roman");

    companion object {
        val DEFAULT: AppFont get() = GOOGLE_SANS
    }
}

fun parseAppFont(raw: String): AppFont =
    AppFont.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: AppFont.DEFAULT

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

enum class DarkOutlineStyle(val id: String) {
    SUBTLE("subtle"),
    BOLD("bold"),
    COLORED("colored"),
    HIDDEN("hidden")
}

fun parseDarkOutlineStyle(raw: String): DarkOutlineStyle =
    DarkOutlineStyle.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: DarkOutlineStyle.SUBTLE

// MARBLE_FLAT_ACCENT_V221 — the product's accent is a choice, not a constant.
//
// Every colour the interface painted in "Marble blue" was one accent wearing one hue: the selected
// tab, the connected ring, the slider's trail, a selected server's rim. V221 lifts that single hue
// into a named palette the user owns. OCEAN is byte-identical to the V220 brand blue, so an install
// that never opens the new page keeps the face it had; every other value re-tints the same roles
// through AetherFlowTheme's accent pass, which is why one setting reaches every page at once.
enum class FlatAccent(val id: String, val label: String) {
    OCEAN("ocean", "Ocean"),
    VIOLET("violet", "Violet"),
    EMERALD("emerald", "Emerald"),
    AMBER("amber", "Amber"),
    ROSE("rose", "Rose"),
    GRAPHITE("graphite", "Graphite");

    companion object {
        val DEFAULT: FlatAccent get() = OCEAN
    }
}

fun parseFlatAccent(raw: String): FlatAccent =
    FlatAccent.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "blue", "azure", "cyan", "default" -> FlatAccent.OCEAN
            "purple", "amethyst", "indigo" -> FlatAccent.VIOLET
            "green", "mint", "teal" -> FlatAccent.EMERALD
            "gold", "yellow", "orange" -> FlatAccent.AMBER
            "pink", "red", "crimson" -> FlatAccent.ROSE
            "gray", "grey", "mono", "slate" -> FlatAccent.GRAPHITE
            else -> FlatAccent.DEFAULT
        }

// MARBLE_CORE_POWER_V221 — one dial for the whole tuning surface.
//
// The product had twenty expert knobs and no answer to "make it faster". A profile is that answer:
// it projects the device's own core count and memory onto the settings the two cores actually read
// (multiplexing, TCP fast open, socket buffers, measurement concurrency) without touching anything
// that changes identity, DNS fail-closed behaviour or route selection.
enum class PowerProfile(val id: String, val label: String) {
    EFFICIENT("efficient", "Efficient"),
    BALANCED("balanced", "Balanced"),
    TURBO("turbo", "Turbo"),
    EXTREME("extreme", "Extreme");

    companion object {
        val DEFAULT: PowerProfile get() = BALANCED
    }
}

fun parsePowerProfile(raw: String): PowerProfile =
    PowerProfile.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "battery", "saver", "low", "eco" -> PowerProfile.EFFICIENT
            "normal", "default", "auto" -> PowerProfile.BALANCED
            "fast", "boost" -> PowerProfile.TURBO
            "max", "beast", "insane" -> PowerProfile.EXTREME
            else -> PowerProfile.DEFAULT
        }

// MARBLE_CENSORSHIP_LAB_V221 — how the TLS handshake is broken up.
//
// The pinned extended core fragments TLS in two ways, and they are not equivalent: `record_fragment`
// splits the ClientHello across several TLS records (cheap, first choice), while `fragment` splits
// the raw handshake across TCP segments with a fallback delay (heavier, for a name that is already
// known to be blocked). Both are client-side and need no root; the fork's `spoof` does need raw
// sockets, so the product never emits it.
enum class FragmentStrategy(val id: String, val label: String) {
    OFF("off", "Off"),
    AUTO("auto", "Automatic"),
    RECORD("record", "TLS records"),
    FULL("full", "Full handshake");

    companion object {
        val DEFAULT: FragmentStrategy get() = AUTO
    }
}

fun parseFragmentStrategy(raw: String): FragmentStrategy =
    FragmentStrategy.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "none", "disabled", "false" -> FragmentStrategy.OFF
            "record_fragment", "records", "sni" -> FragmentStrategy.RECORD
            "fragment", "tls", "aggressive" -> FragmentStrategy.FULL
            else -> FragmentStrategy.DEFAULT
        }

enum class ConnectionPingState { IDLE, MEASURING, MEASURED, FAILED }

enum class ProbeState { IDLE, QUEUED, TESTING }

enum class ProbeMethod {
    REAL_DELAY,

    TCP_PING,

    URL_TEST;

    fun isEndpointLevel(): Boolean = this == TCP_PING
}

object DelayTest {
    const val URL = "https://www.gstatic.com/generate_204"

    const val URL_SECONDARY = "https://cp.cloudflare.com/generate_204"
    const val URL_TERTIARY = "https://detectportal.firefox.com/success.txt"

    const val TCP_GATE_TIMEOUT_MS = 1_000

    fun url(configured: String): String =
        configured.trim().takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }?.let { candidate ->

            runCatching {
                val parsed = java.net.URI(candidate)
                require(parsed.host.orEmpty().isNotBlank())
                require(parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true))
                candidate
            }.getOrNull()
        } ?: URL

    fun candidates(configured: String): List<String> = buildList {
        add(url(configured))
        add(URL_SECONDARY)
        add(URL_TERTIARY)
    }.distinct()
}

object PingBudget {
    const val TIMEOUT_MIN_SEC = 1
    const val TIMEOUT_MAX_SEC = 30
    const val SAMPLES_MIN = 1
    const val SAMPLES_MAX = 10
    const val CONCURRENCY_MIN = 1
    const val CONCURRENCY_MAX = 64

    const val SAMPLE_SPACING_MS = 60L

    val TIMEOUT_CHOICES = listOf(2, 3, 5, 10, 15)

    val SAMPLE_CHOICES = listOf(1, 2, 3, 5, 8)

    val CONCURRENCY_CHOICES = listOf(1, 2, 3, 4, 6, 8, 10, 12, 13, 16, 20, 24, 32, 48, 64)

    fun timeoutSec(value: Int): Int = value.coerceIn(TIMEOUT_MIN_SEC, TIMEOUT_MAX_SEC)

    fun samples(value: Int): Int = value.coerceIn(SAMPLES_MIN, SAMPLES_MAX)

    fun concurrency(value: Int): Int = value.coerceIn(CONCURRENCY_MIN, CONCURRENCY_MAX)

    fun perServerBudgetMs(timeoutSec: Int, samples: Int, spacingMs: Long): Long {
        val perSample = timeoutSec(timeoutSec) * 1_000L
        val rounds = samples(samples)
        return perSample * rounds + spacingMs.coerceIn(0L, 5_000L) * (rounds - 1) + 750L
    }

    fun perServerBudgetMs(timeoutSec: Int, samples: Int): Long =
        perServerBudgetMs(timeoutSec, samples, SAMPLE_SPACING_MS)
}

enum class PingParallelMode(val id: String) {
    AUTO("auto"),
    MANUAL("manual");

    companion object {
        val DEFAULT: PingParallelMode get() = AUTO
    }
}

fun parsePingParallelMode(raw: String): PingParallelMode =
    PingParallelMode.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "DEVICE", "CPU", "DETECT", "AUTO_DETECT" -> PingParallelMode.AUTO
            "FIXED", "USER", "CUSTOM", "CHIP" -> PingParallelMode.MANUAL
            else -> PingParallelMode.DEFAULT
        }

enum class PingDeviceClass(val id: String) {
    MODEST("modest"),
    STANDARD("standard"),
    CAPABLE("capable"),
    STRONG("strong");

    companion object {
        val DEFAULT: PingDeviceClass get() = STANDARD
    }
}

object PingParallel {
    const val MIN = PingBudget.CONCURRENCY_MIN
    const val MAX = PingBudget.CONCURRENCY_MAX

    const val CORES_FLOOR = 1

    const val CORES_CEILING = 16

    const val MEM_LOW_MB = 1_536L
    const val MEM_MID_MB = 3_072L
    const val MEM_HIGH_MB = 6_144L

    const val FALLBACK = 8

    fun deviceClass(cores: Int, memoryMb: Long): PingDeviceClass {
        val byCores = when (cores.coerceIn(CORES_FLOOR, 64)) {
            1 -> PingDeviceClass.MODEST
            2, 3 -> PingDeviceClass.STANDARD
            4, 5 -> PingDeviceClass.CAPABLE
            else -> PingDeviceClass.STRONG
        }
        val demote = memoryMb in 1..MEM_LOW_MB
        return if (demote) {
            when (byCores) {
                PingDeviceClass.STRONG -> PingDeviceClass.CAPABLE
                PingDeviceClass.CAPABLE -> PingDeviceClass.STANDARD
                else -> PingDeviceClass.MODEST
            }
        } else {
            byCores
        }
    }

    fun recommend(cores: Int, memoryMb: Long, method: ProbeMethod): Int {
        val c = cores.coerceIn(CORES_FLOOR, 64)
        val base = when (deviceClass(c, memoryMb)) {
            PingDeviceClass.MODEST -> if (c <= 1) 2 else 3
            PingDeviceClass.STANDARD -> if (c <= 2) 4 else 6
            PingDeviceClass.CAPABLE -> if (c <= 5) 10 else 12
            PingDeviceClass.STRONG -> if (c < CORES_CEILING) 16 else 20
        }

        val byMethod = if (method == ProbeMethod.REAL_DELAY) (base * 2) / 3 else base
        return byMethod.coerceIn(MIN, MAX)
    }

    fun resolve(
        settings: AppSettings,
        cores: Int,
        memoryMb: Long,
        method: ProbeMethod = settings.probeMethod
    ): Int = when (settings.pingParallelMode) {
        PingParallelMode.AUTO -> recommend(cores, memoryMb, method)
        PingParallelMode.MANUAL -> PingBudget.concurrency(settings.pingConcurrency)
    }

    fun autoFor(settings: AppSettings, cores: Int, memoryMb: Long): Int =
        recommend(cores, memoryMb, settings.probeMethod)

    fun describe(cores: Int, memoryMb: Long, method: ProbeMethod): String {
        val coresText = if (cores <= 1) "1 core" else "$cores cores"
        val memText = when {
            memoryMb <= 0L -> "memory unknown"
            memoryMb >= 1_024L -> "${(memoryMb / 1_024L).coerceAtLeast(1L)} GB RAM"
            else -> "$memoryMb MB RAM"
        }
        return "$coresText • $memText • ${recommend(cores, memoryMb, method)} at once"
    }
}

object PingSpeed {
    const val MIN_PERCENT = 50
    const val MAX_PERCENT = 200
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 5

    const val DEFAULT_FACTOR = 1.5

    const val SLOW_PERCENT = 95

    const val FAST_PERCENT = 150

    fun percent(value: Int): Int = value.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun stepped(rawPercent: Double): Int =
        percent(((rawPercent / STEP_PERCENT).roundToInt()) * STEP_PERCENT)

    fun factor(custom: Boolean, dialPercent: Int): Double =
        if (!custom) DEFAULT_FACTOR else percent(dialPercent) / 100.0

    fun label(custom: Boolean, dialPercent: Int): String =
        if (!custom) {
            "Default"
        } else {
            when (val p = percent(dialPercent)) {
                in Int.MIN_VALUE until SLOW_PERCENT -> "Slow"
                in SLOW_PERCENT..FAST_PERCENT -> if (p == DEFAULT_PERCENT) "Classic" else "Standard"
                else -> "Fast"
            }
        }
}

fun AppSettings.pingTimeoutMs(): Int = PingBudget.timeoutSec(pingTimeoutSec) * 1_000

fun AppSettings.pingSampleCount(): Int = PingBudget.samples(pingSamples)

fun AppSettings.pingSpeedFactor(): Double = PingSpeed.factor(pingSpeedCustom, pingSpeedPercent)

fun AppSettings.pingWorkers(): Int =
    PingBudget.concurrency((pingConcurrency * pingSpeedFactor()).roundToInt())

fun AppSettings.pingWorkers(cores: Int, memoryMb: Long, method: ProbeMethod = probeMethod): Int =
    PingBudget.concurrency(
        (PingParallel.resolve(this, cores, memoryMb, method) * pingSpeedFactor()).roundToInt()
    )

fun AppSettings.pingSampleSpacingMs(): Long =
    (PingBudget.SAMPLE_SPACING_MS / pingSpeedFactor()).roundToLong().coerceIn(20L, 300L)

enum class DockPresentation(val id: String) {
    ATTACHED("attached"),
    FLOATING("floating")
}

fun parseDockPresentation(raw: String): DockPresentation {
    val encoded = raw.substringAfter('|', DockPresentation.ATTACHED.id).trim()
    return DockPresentation.entries.firstOrNull { it.id.equals(encoded, ignoreCase = true) }
        ?: DockPresentation.ATTACHED
}

fun encodeDockPreference(size: DockSize, presentation: DockPresentation): String =
    "${size.id}|${presentation.id}"

enum class DockSize(val id: String) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large")
}

fun parseDockSize(raw: String): DockSize {
    val sizeId = raw.substringBefore('|').trim()
    return DockSize.entries.firstOrNull { it.id.equals(sizeId, ignoreCase = true) }
        ?: when (sizeId.uppercase()) {
            "COMPACT" -> DockSize.SMALL
            "SPACIOUS", "BIG" -> DockSize.LARGE
            else -> DockSize.MEDIUM
        }
}

enum class DockSlotKind(val id: String) {
    PULSE("pulse"),
    SOURCE("source"),
    CONFIG("config");

    companion object {
        val DEFAULT: DockSlotKind get() = PULSE
    }
}

fun parseDockSlotKind(raw: String): DockSlotKind =
    DockSlotKind.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "SUB", "SUBSCRIPTION", "GROUP", "SUBS" -> DockSlotKind.SOURCE
            "NODE", "PROFILE", "SERVER", "CONFIGS" -> DockSlotKind.CONFIG
            else -> DockSlotKind.DEFAULT
        }

enum class DockSlotIcon(val id: String) {
    PULSE("pulse"),
    SPARK("spark"),
    LAYERS("layers"),
    BEARING("bearing");

    companion object {
        val DEFAULT: DockSlotIcon get() = PULSE
    }
}

fun parseDockSlotIcon(raw: String): DockSlotIcon =
    DockSlotIcon.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "BOLT", "STAR", "ACTIVITY" -> DockSlotIcon.SPARK
            "STACK", "LIBRARY", "BOOK" -> DockSlotIcon.LAYERS
            "COMPASS", "ROUTE", "GLOBE" -> DockSlotIcon.BEARING
            else -> DockSlotIcon.DEFAULT
        }

enum class DockSlotAccent(val id: String) {
    OCEAN("ocean"),
    MINT("mint"),
    VIOLET("violet"),
    AMBER("amber");

    companion object {
        val DEFAULT: DockSlotAccent get() = OCEAN
    }
}

fun parseDockSlotAccent(raw: String): DockSlotAccent =
    DockSlotAccent.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "CYAN", "BLUE", "ICE" -> DockSlotAccent.OCEAN
            "GREEN", "EMERALD" -> DockSlotAccent.MINT
            "PURPLE", "AMETHYST" -> DockSlotAccent.VIOLET
            "GOLD", "YELLOW" -> DockSlotAccent.AMBER
            else -> DockSlotAccent.DEFAULT
        }

const val DOCK_SLOT_CAPTION_MAX: Int = 14

const val DOCK_SLOT_CAPTION_PULSE: String = "Pulse"

const val DOCK_SLOT_CAPTION_FALLBACK: String = "Custom"

fun dockSlotCaption(configured: String): String =
    configured.trim().take(DOCK_SLOT_CAPTION_MAX)

fun dockSlotDefaultCaption(kind: DockSlotKind, targetName: String): String = when {
    kind == DockSlotKind.PULSE -> DOCK_SLOT_CAPTION_PULSE
    targetName.isNotBlank() -> targetName.trim().take(DOCK_SLOT_CAPTION_MAX)
    else -> DOCK_SLOT_CAPTION_FALLBACK
}

fun <T> dockSlots(all: List<T>, optional: T, showOptional: Boolean): List<T> =
    if (showOptional) all.toList() else all.filterNot { it == optional }

fun <T> dockSlotIndex(slots: List<T>, remembered: T): Int {
    if (slots.isEmpty()) return 0
    return slots.indexOf(remembered).coerceIn(0, slots.lastIndex)
}

fun dockShowsIcons(showIcons: Boolean, showLabels: Boolean): Boolean = showIcons || !showLabels

@Suppress("UNUSED_PARAMETER")
fun dockShowsLabels(showIcons: Boolean, showLabels: Boolean): Boolean = showLabels

object ModularLayout {
    const val STATUS = "STATUS"
    const val SERVERS = "SERVERS"
    const val CONNECT = "CONNECT"
    const val STATS = "STATS"
    const val SHORTCUTS = "SHORTCUTS"

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
    const val PREFS_SCHEMA_VERSION = 3
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
            id = "v2fly-geoip",
            label = "v2fly geoip only",
            geoIpUrl = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat",
            geoSiteUrl = ""
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

enum class IranModePolicy { AUTO, ALWAYS_ON, OFF }

enum class AddressFamilyMode {
    SMART,
    PREFER_IPV4,
    PREFER_IPV6,
    FORCE_IPV4,
    FORCE_IPV6
}

enum class AutoServerStrategy(val id: String) {
    LEAST_PING("least_ping"),
    LEAST_LOAD("least_load"),
    CUSTOM("custom"),
    ROUND_ROBIN("round_robin"),
    RANDOM("random"),
    SMART("smart");

    companion object {
        val DEFAULT: AutoServerStrategy get() = SMART
    }
}

fun parseAutoServerStrategy(raw: String): AutoServerStrategy =
    AutoServerStrategy.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "PING", "FASTEST", "LOW_LATENCY", "LOWEST_PING" -> AutoServerStrategy.LEAST_PING
            "LOAD", "LEAST_BUSY", "LEAST_CONGESTED" -> AutoServerStrategy.LEAST_LOAD
            "RR", "ROTATE", "ROTATION" -> AutoServerStrategy.ROUND_ROBIN
            "RND", "SHUFFLE" -> AutoServerStrategy.RANDOM
            "BEST", "AUTO", "BALANCED" -> AutoServerStrategy.SMART
            else -> AutoServerStrategy.DEFAULT
        }

enum class AutoServerScope(val id: String) {
    SOURCE("source"),
    ALL("all");

    companion object {
        val DEFAULT: AutoServerScope get() = SOURCE
    }
}

fun parseAutoServerScope(raw: String): AutoServerScope =
    AutoServerScope.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().uppercase()) {
            "SUB", "SUBSCRIPTION", "GROUP", "LIBRARY_SOURCE" -> AutoServerScope.SOURCE
            "EVERYTHING", "GLOBAL", "ALL_SOURCES" -> AutoServerScope.ALL
            else -> AutoServerScope.DEFAULT
        }

val AppSettings.autoServerStrategyEnum: AutoServerStrategy
    get() = parseAutoServerStrategy(autoServerStrategy)

val AppSettings.autoServerScopeEnum: AutoServerScope
    get() = parseAutoServerScope(autoServerScope)

val AppSettings.serversLayoutEnum: ServerLayout
    get() = parseServerLayout(serversLayout)

data class AppSettings(
    val socksPort: Int = 10808,
    val localProxyPort: Int = 10101,
    val connectionMode: ConnectionMode = ConnectionMode.FULL_TUN,

    val probeMethod: ProbeMethod = ProbeMethod.REAL_DELAY,
    val probeSpeedTest: Boolean = false,

    val delayTestUrl: String = DelayTest.URL,

    val coreEngineId: String = "xray",

    // MARBLE_TUN2SOCKS_CORE_V222 — the datapath that carries the TUN device's packets to the
    // proxy core's SOCKS inbound. "hev" is the historical default; "tun2socks" is the pinned
    // alternative, with its own options below.
    val tunnelCoreId: String = "hev",
    val tun2SocksMtu: Int = 0,
    val tun2SocksUdpTimeoutSec: Int = 60,
    val tun2SocksTcpSendBufferKb: Int = 0,
    val tun2SocksTcpReceiveBufferKb: Int = 0,
    val tun2SocksModerateReceiveBuffer: Boolean = false,
    val tun2SocksLogLevel: String = "warn",

    val singBoxUnifiedDelay: Boolean = true,

    val singBoxConnectTimeoutSec: Int = 10,

    val singBoxCacheFile: Boolean = true,

    val singBoxPreferParser: Boolean = true,

    val xrayLogLevel: String = "error",
    val xraySniffingEnabled: Boolean = true,
    val xraySniffingRouteOnly: Boolean = true,
    val xrayAllowLan: Boolean = false,
    val xrayHttpInboundPort: Int = 0,

    val singBoxLogLevel: String = "warn",
    val singBoxSniffEnabled: Boolean = true,
    val singBoxResolveDestination: Boolean = false,
    val singBoxAllowLan: Boolean = false,
    val singBoxHttpInboundPort: Int = 0,

    val benchMode: BenchMode = BenchMode.BALANCED,
    val benchCandidates: Int = 20,
    val benchSamples: Int = 3,
    val benchTimeoutSec: Int = 6,
    val benchBytes: Int = 262144,
    val tcpPrecheckTimeoutMs: Int = 1000,
    val tcpWorkers: Int = 20,

    val pingTimeoutSec: Int = 5,
    val pingSamples: Int = 3,
    val pingConcurrency: Int = 16,

    val pingParallelMode: PingParallelMode = PingParallelMode.AUTO,

    val pingSpeedCustom: Boolean = false,
    val pingSpeedPercent: Int = PingSpeed.DEFAULT_PERCENT,

    val nodeSortMode: NodeSortMode = NodeSortMode.DEFAULT,
    val nodeSortReverse: Boolean = false,

    val serversProtocolFilter: String = "",
    val serversOnlyReachable: Boolean = false,
    val serversMaxPingMs: Int = 0,
    val serversGroupByCountry: Boolean = false,
    val serversLayout: String = ServerLayout.DEFAULT.id,

    val subscriptionAutoRefresh: Boolean = true,

    val appUpdateCheckEnabled: Boolean = true,
    val subscriptionRefreshHours: Int = 12,

    val homeShowLiveQuality: Boolean = true,
    val homeSpeedWidgetEnabled: Boolean = false,
    val autoConnectBestAfterScan: Boolean = false,

    val serverIntelEnabled: Boolean = true,

    val serverLocationAutoDetect: Boolean = true,

    val homeShowDataUsage: Boolean = false,

    val homeAmbientBackdrop: Boolean = true,

    val homePingGaugeAction: String = HomePingAction.ROUTE.id,

    val homePingChipAction: String = HomePingAction.GROUP.id,

    val homePingHeaderAction: String = HomePingAction.ROUTE.id,

    val smartNotificationsEnabled: Boolean = true,
    val notifyConnectionEvents: Boolean = false,
    val notifyRecoveryEvents: Boolean = true,
    val notifyPrivacyWarnings: Boolean = true,
    val notifyNetworkChanges: Boolean = false,
    val notifySubscriptionEvents: Boolean = true,
    val notifyCoreUpdates: Boolean = true,
    val notificationLiveStats: Boolean = true,
    val notificationCooldownSec: Int = 20,

    // MARBLE_NOTIFICATION_COUNCIL_V231 — two switches the old page could not offer because the
    // notifier had no such behaviour. A privacy alarm gets its own channel and may therefore
    // interrupt; the session clock is the platform's chronometer rather than a re-posted string.
    val notifyPrivacyHeadsUp: Boolean = true,
    val notificationSessionTimer: Boolean = true,

    val routingMode: RoutingMode = RoutingMode.GEO_DIRECT,

    val customRoutingEnabled: Boolean = false,
    val geoAssetSourceId: String = RoutingDefaults.SOURCE_CHOCOLATE4U,

    val geoAssetSourceIds: String = RoutingDefaults.SOURCE_CHOCOLATE4U,

    val geoCustomSourcesJson: String = "",

    val geoPrecision: String = GeoPrecision.DEFAULT.id,

    val geoMultiSourceEnabled: Boolean = true,
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

    val addressFamilyMode: AddressFamilyMode = AddressFamilyMode.SMART,
    val ipv6Enabled: Boolean = true,
    val preferIpv6: Boolean = true,

    val strictAddressFamily: Boolean = false,

    val measuredIpv6Unhealthy: Boolean = false,

    val measuredDnsDemotedEndpoints: String = "",

    val measuredDnsParallel: Boolean = false,

    val measuredDnsExcludedEndpoints: String = "",

    val measuredGeoReadyFiles: String = "",
    val measuredGeoTagsByFile: Map<String, Set<String>> = emptyMap(),
    val measuredGeoMembershipKnown: Boolean = false,

    val measuredDnsResolverOrder: String = "",
    val measuredSingBoxDnsParallel: Boolean = false,

    val adaptiveHappyEyeballsEnabled: Boolean = true,
    val happyEyeballsTryDelayMs: Int = 60,
    val happyEyeballsMaxConcurrent: Int = 4,
    val adaptiveTcpFastOpenEnabled: Boolean = true,
    val tcpFastOpenEnabled: Boolean = false,
    val adaptiveMssEnabled: Boolean = true,
    val tcpMaxSeg: Int = 0,

    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    val muxXudpConcurrency: Int = 16,
    val muxUdp443: String = "skip",

    val xrayDnsLog: Boolean = false,

    val xraySniffDestOverride: String = "http,tls,quic",
    val xraySniffMetadataOnly: Boolean = false,

    val xraySocksUdpEnabled: Boolean = true,

    val xraySockoptDomainStrategy: String = "AsIs",
    val xrayTcpNoDelay: Boolean = false,
    val xrayTcpKeepAliveIntervalSec: Int = 0,
    val xrayTcpUserTimeoutMs: Int = 0,
    val xrayTcpCongestion: String = "",
    val xrayTcpMptcp: Boolean = true,
    val xrayTcpWindowClamp: Int = 0,

    val xrayPolicyHandshakeSec: Int = 0,
    val xrayPolicyConnIdleSec: Int = 0,
    val xrayPolicyUplinkOnlySec: Int = 0,
    val xrayPolicyDownlinkOnlySec: Int = 0,
    val xrayPolicyBufferSizeKb: Int = 0,

    val xrayExtraJson: String = "",

    val singBoxLogTimestamp: Boolean = false,
    val singBoxSniffOverrideDestination: Boolean = false,
    val singBoxInboundUsername: String = "",
    val singBoxInboundPassword: String = "",

    val singBoxMuxEnabled: Boolean = false,
    val singBoxMuxProtocol: String = "h2mux",
    val singBoxMuxMaxConnections: Int = 4,
    val singBoxMuxMinStreams: Int = 4,
    val singBoxMuxMaxStreams: Int = 32,
    val singBoxMuxPadding: Boolean = false,

    val singBoxExtraJson: String = "",

    val iranModePolicy: IranModePolicy = IranModePolicy.OFF,
    val iranModeCountermeasures: Boolean = true,
    val iranDomesticDirect: Boolean = true,
    val iranDeepProbeEnabled: Boolean = true,

    val intelligenceEnabled: Boolean = true,
    val configCompatibilityMode: Boolean = true,
    val verifiedPerformanceTuning: Boolean = true,

    val allowUnencryptedPublicOutbound: Boolean = true,

    val connectTuningEnabled: Boolean = false,
    val connectTuningBudgetSec: Int = 5,
    val connectTuningMethods: Int = 8,
    val liveTuningEnabled: Boolean = true,
    val liveTuningIntervalSec: Int = 300,
    val liveTuningPingTriggerMs: Int = 220,
    val liveTuningMinGainPercent: Int = 15,
    val adaptiveBufferEnabled: Boolean = true,

    val identityGuardEnabled: Boolean = true,
    val identityGuardStrictNoFailover: Boolean = true,
    val identityGuardSameRouteRetries: Int = 3,

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

    val dnsFakeIpEnabled: Boolean = true,
    val adaptiveDnsEnabled: Boolean = true,
    val adaptiveDualStackEnabled: Boolean = true,
    val adaptiveThroughputEnabled: Boolean = true,
    val adaptiveThroughputMaxBytes: Int = 4 * 1024 * 1024,
    val udpProbeEnabled: Boolean = true,
    val thermalAwareEnabled: Boolean = true,
    val workloadProfile: WorkloadProfile = WorkloadProfile.AUTO,

    val theme: String = "light",
    val fontFamily: String = AppFont.GOOGLE_SANS.id,

    val homeStyle: String = HomeStyle.DEFAULT.id,

    val modularCardOrder: String = ModularLayout.DEFAULT_ORDER,
    val modularShowStats: Boolean = true,
    val modularShowSocks: Boolean = false,
    val modularShowShortcuts: Boolean = true,
    val modularShowStatus: Boolean = true,
    val modularShowServers: Boolean = true,
    val modularConnectStyle: String = "SLIDER",
    val modularCardSize: String = ModularCardSize.COMPACT.id,
    val modularCardHeightDp: Int = 180,

    val modularHideCustomizerButton: Boolean = false,

    val connectButtonStyle: String = ConnectButtonStyle.ROUND.id,

    val darkOutlineStyle: String = DarkOutlineStyle.SUBTLE.id,

    val dockShowLabels: Boolean = true,
    val dockShowIcons: Boolean = true,
    val dockSize: String = DockSize.MEDIUM.id,

    val dockSlotEnabled: Boolean = true,
    val dockSlotKind: String = DockSlotKind.DEFAULT.id,
    val dockSlotSourceId: String = "all",
    val dockSlotProfileId: String = "",
    val dockSlotProfileSourceId: String = "",
    val dockSlotLabel: String = "",
    val dockSlotIcon: String = DockSlotIcon.DEFAULT.id,
    val dockSlotAccent: String = DockSlotAccent.DEFAULT.id,
    val dockSlotShowStatusBadge: Boolean = true,

    val appLanguage: String = AppLanguage.SYSTEM.id,

    val textScalePercent: Int = TextScale.DEFAULT_PERCENT,

    val autoServerSelectorEnabled: Boolean = false,
    val autoServerStrategy: String = AutoServerStrategy.SMART.id,
    val autoServerPingWeight: Int = 40,
    val autoServerLoadWeight: Int = 30,
    val autoServerStabilityWeight: Int = 20,
    val autoServerFreshnessWeight: Int = 10,
    val autoServerOnScan: Boolean = true,
    val autoServerOnConnect: Boolean = false,
    val autoServerOnFailure: Boolean = true,
    val autoServerScope: String = AutoServerScope.SOURCE.id,
    val autoServerSwitchMarginPercent: Int = 15,

    // MARBLE_MERIDIAN_V221 — the flat chapter's four decisions.
    //
    // flatInterface: the product's boxes lose their material and keep their structure — one tonal
    // fill, one hairline, no shadow, no specular wash. It is on by default because a flat surface
    // is cheaper to draw *and* easier to read; the switch exists so a user who liked the older
    // lifted cards can have them back.
    val flatInterface: Boolean = true,
    val accentTheme: String = FlatAccent.DEFAULT.id,
    // powerProfile projects the device onto the cores' own knobs (see core/CorePower.kt).
    val powerProfile: String = PowerProfile.DEFAULT.id,
    // fragmentStrategy is the TLS-handshake treatment the extended core applies on the wire.
    val fragmentStrategy: String = FragmentStrategy.DEFAULT.id,
    // warmupOnLaunch pre-resolves the active route so the first ping and the first connection do
    // not pay for DNS.
    val warmupOnLaunch: Boolean = true,
    // censorshipAutoApply lets the Censorship Lab's prescription land without a second tap when a
    // scan finds a decisive block.
    val censorshipAutoApply: Boolean = false,

    val debugModeEnabled: Boolean = false
)
