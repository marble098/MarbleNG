package com.marbleng.app.data

import android.content.Context
import com.marbleng.app.core.CouncilReport
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.IpFamilyScan
import com.marbleng.app.core.Tun2SocksPolicy
import com.marbleng.app.core.TunnelCore
import com.marbleng.app.core.parseCoreEngine
import com.marbleng.app.core.parseTunnelCore
import com.marbleng.app.jitter.JitterAutoTuner
import com.marbleng.app.model.*
import org.json.JSONArray
import org.json.JSONObject

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("marbleng-store", Context.MODE_PRIVATE)

    fun createBackup(): String {
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            values.put(key, when (value) {
                is Set<*> -> JSONArray(value.filterIsInstance<String>())
                else -> value
            })
        }
        return JSONObject()
            .put("format", "marbleng-backup")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("values", values)
            .toString(2)
    }

    fun restoreBackup(raw: String) {
        val root = JSONObject(raw)
        require(root.optString("format") == "marbleng-backup") { "Not a MarbleNG backup" }
        require(root.optInt("version") == 1) { "Unsupported backup version" }
        val values = root.getJSONObject("values")

        values.keys().forEach { key ->
            require(key.isNotEmpty()) { "Backup contains an empty preference key" }
            when (val value = values.get(key)) {
                is Boolean, is Int, is Long, is Double, is String -> Unit
                is JSONArray -> (0 until value.length()).forEach { index ->
                    require(value.get(index) is String) {
                        "Backup preference '$key' contains a non-string set item"
                    }
                }
                else -> error("Backup preference '$key' has an unsupported value")
            }
        }
        val edit = prefs.edit().clear()
        values.keys().forEach { key ->
            when (val value = values.get(key)) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Double -> edit.putFloat(key, value.toFloat())
                is String -> edit.putString(key, value)
                is JSONArray -> edit.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
            }
        }
        check(edit.commit()) { "Could not save restored data" }
    }

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

    val benchmarkMemoryLimit: Int get() = 400

    val benchmarkMemoryMaxAgeMs: Long get() = 30L * 24L * 60L * 60L * 1000L

    fun loadBenchmarks(): List<BenchmarkResult> {
        val cutoff = System.currentTimeMillis() - benchmarkMemoryMaxAgeMs
        return parseArray("benchmarks") { BenchmarkResult.fromJson(it) }
            .asSequence()
            .filter { it.profileId.isNotBlank() && it.measuredAtMs >= cutoff }
            .distinctBy { it.profileId }
            .sortedByDescending { it.measuredAtMs }
            .take(benchmarkMemoryLimit)
            .toList()
    }

    fun saveBenchmarks(v: List<BenchmarkResult>) {
        val cutoff = System.currentTimeMillis() - benchmarkMemoryMaxAgeMs
        val kept = v.asSequence()
            .filter { it.profileId.isNotBlank() && it.measuredAtMs >= cutoff }
            .distinctBy { it.profileId }
            .sortedByDescending { it.measuredAtMs }
            .take(benchmarkMemoryLimit)
            .map { it.toJson() }
            .toList()
        saveArray("benchmarks", kept)
    }

    val serverLocationMemoryLimit: Int get() = 1024

    fun loadServerLocations(): Map<String, Pair<String, Long>> {
        val out = mutableMapOf<String, Pair<String, Long>>()
        val root = runCatching { JSONObject(prefs.getString("serverLocations", "{}") ?: "{}") }
            .getOrNull() ?: return out
        for (key in root.keys()) {
            val entry = runCatching { root.getJSONObject(key) }.getOrNull() ?: continue
            val code = entry.optString("code").uppercase()
            if (code.length == 2 && code.all { it in 'A'..'Z' }) {
                out[key] = code to entry.optLong("at")
            }
        }
        return out
    }

    fun saveServerLocation(key: String, code: String, provisional: Boolean = false) {
        if (key.isBlank() || code.length != 2) return
        val all = loadServerLocations().toMutableMap()
        val provisionalFlags = loadServerLocationProvisional().toMutableMap()
        all[key] = code.uppercase() to System.currentTimeMillis()
        provisionalFlags[key] = provisional
        val kept = all.entries
            .sortedByDescending { it.value.second }
            .take(serverLocationMemoryLimit)
            .associate { it.key to it.value }
        val out = JSONObject()
        kept.forEach { (k, v) ->
            out.put(
                k,
                JSONObject()
                    .put("code", v.first)
                    .put("at", v.second)
                    .put("provisional", provisionalFlags[k] == true)
            )
        }
        prefs.edit().putString("serverLocations", out.toString()).apply()
    }

    fun loadServerLocationProvisional(): Map<String, Boolean> {
        val out = mutableMapOf<String, Boolean>()
        val root = runCatching { JSONObject(prefs.getString("serverLocations", "{}") ?: "{}") }
            .getOrNull() ?: return out
        for (key in root.keys()) {
            val entry = runCatching { root.getJSONObject(key) }.getOrNull() ?: continue
            out[key] = entry.optBoolean("provisional", false)
        }
        return out
    }

    val ipFamilyScanMemoryLimit: Int get() = 1024

    fun loadIpFamilyScans(): Map<String, IpFamilyScan> {
        val out = LinkedHashMap<String, IpFamilyScan>()
        val root = runCatching { JSONObject(prefs.getString("ipFamilyScans", "{}") ?: "{}") }
            .getOrNull() ?: return out
        for (key in root.keys()) {
            val entry = runCatching { root.getJSONObject(key) }.getOrNull() ?: continue
            val scan = runCatching { IpFamilyScan.fromJson(entry) }.getOrNull() ?: continue
            if (scan.endpoint.isNotBlank()) out[key] = scan
        }
        return out
    }

    fun saveIpFamilyScan(scan: IpFamilyScan) {
        if (scan.endpoint.isBlank()) return
        val all = loadIpFamilyScans().toMutableMap()
        all[scan.endpoint] = scan
        saveIpFamilyScans(all)
    }

    fun saveIpFamilyScans(scans: Map<String, IpFamilyScan>) {
        val kept = scans.values
            .filter { it.endpoint.isNotBlank() }
            .sortedByDescending { it.scannedAtMs }
            .take(ipFamilyScanMemoryLimit)
        val out = JSONObject()
        kept.forEach { scan -> out.put(scan.endpoint, scan.toJson()) }
        prefs.edit().putString("ipFamilyScans", out.toString()).apply()
    }

    val usageSessionMemoryLimit: Int get() = 60

    fun loadUsageSessions(): List<UsageSessionRecord> =
        parseArray("usageSessions") { UsageSessionRecord.fromJson(it) }
            .sortedByDescending { it.endedAtMs }
            .take(usageSessionMemoryLimit)

    fun saveUsageSessions(v: List<UsageSessionRecord>) =
        saveArray("usageSessions", v.take(usageSessionMemoryLimit).map { it.toJson() })

    fun loadTotalUsageBytes(): Long = prefs.getLong("totalUsageBytes", 0L).coerceAtLeast(0L)

    fun saveTotalUsageBytes(bytes: Long) =
        prefs.edit().putLong("totalUsageBytes", bytes.coerceAtLeast(0L)).apply()

    fun loadSessionAnchor(): JSONObject? =
        runCatching { JSONObject(prefs.getString("sessionAnchor", "") ?: "") }.getOrNull()

    fun saveSessionAnchor(anchor: JSONObject) =
        prefs.edit().putString("sessionAnchor", anchor.toString()).apply()

    fun clearSessionAnchor() = prefs.edit().remove("sessionAnchor").apply()

    fun loadAutoServerCursor(): Int =
        prefs.getInt("autoServerCursor", 0).coerceAtLeast(0)

    fun saveAutoServerCursor(cursor: Int) =
        prefs.edit().putInt("autoServerCursor", cursor.coerceAtLeast(0)).apply()

    fun dropTransportMemory() {
        if (prefs.contains("transportMemory")) prefs.edit().remove("transportMemory").apply()
    }

    fun lastProfileId(): String = prefs.getString("lastProfileId", "") ?: ""
    fun lastProfileSourceId(): String = prefs.getString("lastProfileSourceId", "") ?: ""

    fun setLastProfileRef(id: String, sourceId: String) =
        prefs.edit()
            .putString("lastProfileId", id)
            .putString("lastProfileSourceId", sourceId)
            .apply()

    fun setLastProfileId(id: String) =
        prefs.edit().putString("lastProfileId", id).apply()

    fun clearLastProfile() =
        prefs.edit()
            .remove("lastProfileId")
            .remove("lastProfileSourceId")
            .apply()

    fun tunnelIntentActive(): Boolean = prefs.getBoolean("tunnelIntentActive", false)

    fun setTunnelIntentActive(active: Boolean) =
        prefs.edit().putBoolean("tunnelIntentActive", active).apply()

    fun librarySourceFilter(): String =
        prefs.getString("librarySourceFilter", "all")
            ?.trim()
            .orEmpty()
            .ifBlank { "all" }

    fun setLibrarySourceFilter(id: String) =
        prefs.edit()
            .putString("librarySourceFilter", id.trim().ifBlank { "all" })
            .apply()

    fun libraryCollapsedSources(): Set<String> =
        (prefs.getStringSet("libraryCollapsedSources", emptySet()) ?: emptySet()).toSet()

    fun setLibraryCollapsedSources(collapsed: Set<String>) =
        prefs.edit()
            .putStringSet("libraryCollapsedSources", collapsed)
            .apply()

    // MARBLE_SETTINGS_COLLAPSE_MEMORY_V220 — which Settings hub groups the user closed. An
    // untouched install reads an empty set, so every group starts open exactly as before.
    fun settingsCollapsedGroups(): Set<String> =
        (prefs.getStringSet("settingsCollapsedGroups", emptySet()) ?: emptySet()).toSet()

    fun setSettingsCollapsedGroups(collapsed: Set<String>) =
        prefs.edit()
            .putStringSet("settingsCollapsedGroups", collapsed)
            .apply()

    // MARBLE_NETWORK_COUNCIL_V236 — the last applied verdict, in its own JSON document. It is a
    // report, not settings: restoring a backup must not resurrect a scan that described a network
    // the user left weeks ago, so it lives outside the `marbleng-backup` envelope.
    fun loadCouncilReport(): CouncilReport? =
        CouncilReport.fromJson(prefs.getString("networkCouncilReport", "") ?: "")

    fun saveCouncilReport(report: CouncilReport?) {
        prefs.edit()
            .putString("networkCouncilReport", report?.toJson() ?: "")
            .apply()
    }

    // The auto-apply switch is a preference about the scanner, not a dial the cores read, so it
    // lives beside the report instead of inside AppSettings — which is already at the JVM's
    // 255-parameter limit for a generated `copy` and cannot take another property.
    fun councilAutoApply(): Boolean = prefs.getBoolean("networkCouncilAutoApply", true)

    fun setCouncilAutoApply(enabled: Boolean) {
        prefs.edit()
            .putBoolean("networkCouncilAutoApply", enabled)
            .apply()
    }

    fun lastAppTab(): String = prefs.getString("lastAppTab", "DECK")
        ?.trim()
        .orEmpty()
        .ifBlank { "DECK" }

    fun setLastAppTab(name: String) =
        prefs.edit()
            .putString("lastAppTab", name.trim().ifBlank { "DECK" })
            .apply()

    fun lastSettingsTab(): String = prefs.getString("lastSettingsTab", "GENERAL")
        ?.trim()
        .orEmpty()
        .ifBlank { "GENERAL" }

    fun setLastSettingsTab(name: String) =
        prefs.edit()
            .putString("lastSettingsTab", name.trim().ifBlank { "GENERAL" })
            .apply()

    fun lastSettingsPage(): String = prefs.getString("lastSettingsPage", "hub")
        ?.trim()
        .orEmpty()
        .ifBlank { "hub" }

    fun setLastSettingsPage(name: String) =
        prefs.edit()
            .putString("lastSettingsPage", name.trim().ifBlank { "hub" })
            .apply()

    private fun migrateRoutingDefaultsIfNeeded() {
        val from = prefs.getInt("routingDefaultsSchema", 0)
        if (from >= RoutingDefaults.PREFS_SCHEMA_VERSION) return

        if (from < 1) {
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
                .putString("geoAssetSourceId", RoutingDefaults.SOURCE_CHOCOLATE4U)
                .putString(
                    "routingRulesJson",
                    com.marbleng.app.core.RoutingEngine.serializeRules(
                        com.marbleng.app.core.RoutingEngine.DEFAULT_RULES
                    )
                )
                .putString("iranModePolicy", IranModePolicy.OFF.name)
                .putBoolean("intelligenceEnabled", true)
                .putBoolean("connectTuningEnabled", false)
                .putBoolean("continuousOptimizerEnabled", false)
                .putBoolean("raceConnectEnabled", false)
                .apply()
        }

        if (from < 2) {
            prefs.edit()
                .putString("addressFamilyMode", AddressFamilyMode.SMART.name)
                .putBoolean("ipv6Enabled", true)
                .putBoolean("preferIpv6", true)
                .apply()
        }

        if (from < 3) {
            prefs.edit()
                .putString(
                    "geoAssetSourceIds",
                    com.marbleng.app.core.GeoAssetRegistry.serializeIds(
                        listOf(
                            prefs.getString("geoAssetSourceId", RoutingDefaults.SOURCE_CHOCOLATE4U)
                                ?: RoutingDefaults.SOURCE_CHOCOLATE4U
                        )
                    )
                )
                .putString("geoPrecision", GeoPrecision.DEFAULT.id)
                .putBoolean("geoMultiSourceEnabled", true)
                .apply()
        }

        prefs.edit()
            .putInt("routingDefaultsSchema", RoutingDefaults.PREFS_SCHEMA_VERSION)
            .apply()
    }

    private fun migratePerformanceDefaultsIfNeeded() {
        if (prefs.getInt("performanceDefaultsSchema", 0) >= 1) return
        val edit = prefs.edit()
        if (prefs.getInt("benchSamples", 4) == 4) edit.putInt("benchSamples", 3)
        if (prefs.getInt("benchTimeoutSec", 8) == 8) edit.putInt("benchTimeoutSec", 6)
        if (prefs.getInt("tcpPrecheckTimeoutMs", 1800) == 1800) edit.putInt("tcpPrecheckTimeoutMs", 1000)
        if (prefs.getInt("connectTuningMethods", 4) == 4) edit.putInt("connectTuningMethods", 8)
        if (prefs.getInt("raceWidth", 3) == 3) edit.putInt("raceWidth", 4)
        edit.putInt("performanceDefaultsSchema", 1)
        edit.apply()
    }

    fun settings(): AppSettings {
        migrateRoutingDefaultsIfNeeded()
        migratePerformanceDefaultsIfNeeded()
        val familyMode = if (prefs.contains("addressFamilyMode")) {
            enumValue("addressFamilyMode", AddressFamilyMode.SMART)
        } else {
            when {
                !prefs.getBoolean("ipv6Enabled", true) -> AddressFamilyMode.FORCE_IPV4
                prefs.getBoolean("preferIpv6", true) -> AddressFamilyMode.PREFER_IPV6
                else -> AddressFamilyMode.PREFER_IPV4
            }
        }
        return AppSettings(
        socksPort = prefs.getInt("socksPort", 10808),
        localProxyPort = prefs.getInt("localProxyPort", 10101),
        connectionMode = enumValue("connectionMode", ConnectionMode.FULL_TUN),

        probeMethod = probeMethod(),

        delayTestUrl = DelayTest.url(prefs.getString("delayTestUrl", DelayTest.URL) ?: DelayTest.URL),
        coreEngineId = parseCoreEngine(prefs.getString("coreEngineId", CoreEngine.XRAY.id) ?: CoreEngine.XRAY.id).id,
        tunnelCoreId = parseTunnelCore(prefs.getString("tunnelCoreId", TunnelCore.HEV.id) ?: TunnelCore.HEV.id).id,
        tun2SocksMtu = prefs.getInt("tun2SocksMtu", 0).let { if (it == 0) 0 else it.coerceIn(Tun2SocksPolicy.MIN_MTU, Tun2SocksPolicy.MAX_MTU) },
        tun2SocksUdpTimeoutSec = Tun2SocksPolicy.normalizeUdpTimeout(prefs.getInt("tun2SocksUdpTimeoutSec", Tun2SocksPolicy.DEFAULT_UDP_TIMEOUT_SEC)),
        tun2SocksTcpSendBufferKb = Tun2SocksPolicy.normalizeBufferKb(prefs.getInt("tun2SocksTcpSendBufferKb", 0)),
        tun2SocksTcpReceiveBufferKb = Tun2SocksPolicy.normalizeBufferKb(prefs.getInt("tun2SocksTcpReceiveBufferKb", 0)),
        tun2SocksModerateReceiveBuffer = prefs.getBoolean("tun2SocksModerateReceiveBuffer", false),
        tun2SocksLogLevel = Tun2SocksPolicy.normalizeLogLevel(prefs.getString("tun2SocksLogLevel", "warn") ?: "warn"),
        singBoxUnifiedDelay = prefs.getBoolean("singBoxUnifiedDelay", true),
        singBoxConnectTimeoutSec = prefs.getInt("singBoxConnectTimeoutSec", 10).coerceIn(3, 60),
        singBoxCacheFile = prefs.getBoolean("singBoxCacheFile", true),
        singBoxPreferParser = prefs.getBoolean("singBoxPreferParser", true),
        xrayLogLevel = prefs.getString("xrayLogLevel", "error") ?: "error",
        xraySniffingEnabled = prefs.getBoolean("xraySniffingEnabled", true),
        xraySniffingRouteOnly = prefs.getBoolean("xraySniffingRouteOnly", true),
        xrayAllowLan = prefs.getBoolean("xrayAllowLan", false),
        xrayHttpInboundPort = prefs.getInt("xrayHttpInboundPort", 0).coerceIn(0, 65535),
        singBoxLogLevel = prefs.getString("singBoxLogLevel", "warn") ?: "warn",
        singBoxSniffEnabled = prefs.getBoolean("singBoxSniffEnabled", true),
        singBoxResolveDestination = prefs.getBoolean("singBoxResolveDestination", false),
        singBoxAllowLan = prefs.getBoolean("singBoxAllowLan", false),
        singBoxHttpInboundPort = prefs.getInt("singBoxHttpInboundPort", 0).coerceIn(0, 65535),

        benchMode = enumValue("benchMode", BenchMode.BALANCED),
        benchCandidates = prefs.getInt("benchCandidates", 20),
        benchSamples = prefs.getInt("benchSamples", 3),
        benchTimeoutSec = prefs.getInt("benchTimeoutSec", 6),
        benchBytes = prefs.getInt("benchBytes", 262144),
        tcpPrecheckTimeoutMs = prefs.getInt("tcpPrecheckTimeoutMs", 1000),
        tcpWorkers = prefs.getInt("tcpWorkers", 20),

        pingTimeoutSec = PingBudget.timeoutSec(prefs.getInt("pingTimeoutSec", 5)),
        pingSamples = PingBudget.samples(prefs.getInt("pingSamples", 3)),
        pingConcurrency = PingBudget.concurrency(prefs.getInt("pingConcurrency", 16)),

        pingSpeedCustom = prefs.getBoolean("pingSpeedCustom", false),
        pingSpeedPercent = PingSpeed.percent(prefs.getInt("pingSpeedPercent", PingSpeed.DEFAULT_PERCENT)),

        pingParallelMode = if (prefs.contains("pingParallelMode")) {
            runCatching { PingParallelMode.valueOf(prefs.getString("pingParallelMode", "") ?: "") }
                .getOrDefault(PingParallelMode.AUTO)
        } else if (prefs.getInt("pingConcurrency", 16) != 16) {
            PingParallelMode.MANUAL
        } else {
            PingParallelMode.AUTO
        },

        nodeSortMode = enumValue("nodeSortMode", NodeSortMode.DEFAULT),
        nodeSortReverse = prefs.getBoolean("nodeSortReverse", false),

        serversProtocolFilter = prefs.getString("serversProtocolFilter", "")?.trim().orEmpty(),
        serversOnlyReachable = prefs.getBoolean("serversOnlyReachable", false),
        serversMaxPingMs = prefs.getInt("serversMaxPingMs", 0).coerceAtLeast(0),
        serversGroupByCountry = prefs.getBoolean("serversGroupByCountry", false),

        serversLayout = prefs.getString("serversLayout", ServerLayout.DEFAULT.id)
            ?: ServerLayout.DEFAULT.id,

        homePingGaugeAction = prefs.getString("homePingGaugeAction", HomePingAction.ROUTE.id)
            ?: HomePingAction.ROUTE.id,
        homePingChipAction = prefs.getString("homePingChipAction", HomePingAction.GROUP.id)
            ?: HomePingAction.GROUP.id,
        homePingHeaderAction = prefs.getString("homePingHeaderAction", HomePingAction.ROUTE.id)
            ?: HomePingAction.ROUTE.id,

        subscriptionAutoRefresh = prefs.getBoolean("subscriptionAutoRefresh", true),

        appUpdateCheckEnabled = prefs.getBoolean("appUpdateCheckEnabled", true),
        subscriptionRefreshHours = prefs.getInt("subscriptionRefreshHours", 12),
        homeShowLiveQuality = prefs.getBoolean("homeShowLiveQuality", true),
        homeSpeedWidgetEnabled = prefs.getBoolean("homeSpeedWidgetEnabled", false),
        autoConnectBestAfterScan = prefs.getBoolean("autoConnectBestAfterScan", false),
        serverIntelEnabled = prefs.getBoolean("serverIntelEnabled", true),

        serverLocationAutoDetect = prefs.getBoolean("serverLocationAutoDetect", true),
        homeShowDataUsage = prefs.getBoolean("homeShowDataUsage", false),

        homeAmbientBackdrop = prefs.getBoolean("homeAmbientBackdrop", true),

        smartNotificationsEnabled = prefs.getBoolean("smartNotificationsEnabled", true),
        notifyConnectionEvents = prefs.getBoolean("notifyConnectionEvents", false),
        notifyRecoveryEvents = prefs.getBoolean("notifyRecoveryEvents", true),
        notifyPrivacyWarnings = prefs.getBoolean("notifyPrivacyWarnings", true),
        notifyNetworkChanges = prefs.getBoolean("notifyNetworkChanges", false),
        notifySubscriptionEvents = prefs.getBoolean("notifySubscriptionEvents", true),
        notifyCoreUpdates = prefs.getBoolean("notifyCoreUpdates", true),
        notificationLiveStats = prefs.getBoolean("notificationLiveStats", true),
        notificationCooldownSec = prefs.getInt("notificationCooldownSec", 20).coerceIn(5, 300),
        notifyPrivacyHeadsUp = prefs.getBoolean("notifyPrivacyHeadsUp", true),
        notificationSessionTimer = prefs.getBoolean("notificationSessionTimer", true),

        // MARBLE_SHADE_STUDIO_V249 — the shade's route control. Every default is the behaviour the
        // app already had plus the studio, so a backup restored from before this page exists keeps
        // working and an upgrade changes nobody's notification until they ask.
        shadeControl = ShadeRouteControl.of(
            picker = prefs.getBoolean("notificationShadePicker", true),
            cycle = prefs.getBoolean("notificationShadeCycle", true),
            style = storedShadeStyle(),
            showPing = prefs.getBoolean("notificationShadePing", true),
            rows = prefs.getInt("notificationShadeRows", ShadeControlDefaults.ROWS)
        ),

        routingMode = enumValue("routingMode", RoutingMode.GEO_DIRECT),
        customRoutingEnabled = prefs.getBoolean("customRoutingEnabled", false),
        geoAssetSourceId = prefs.getString("geoAssetSourceId", RoutingDefaults.SOURCE_CHOCOLATE4U)
            ?: RoutingDefaults.SOURCE_CHOCOLATE4U,

        geoAssetSourceIds = prefs.getString(
            "geoAssetSourceIds",
            prefs.getString("geoAssetSourceId", RoutingDefaults.SOURCE_CHOCOLATE4U)
        ) ?: RoutingDefaults.SOURCE_CHOCOLATE4U,
        geoCustomSourcesJson = prefs.getString("geoCustomSourcesJson", "") ?: "",
        geoPrecision = prefs.getString("geoPrecision", GeoPrecision.DEFAULT.id)
            ?: GeoPrecision.DEFAULT.id,
        geoMultiSourceEnabled = prefs.getBoolean("geoMultiSourceEnabled", true),
        routingRulesJson = prefs.getString("routingRulesJson", "") ?: "",
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
        routeDomainMatcher = prefs.getString("routeDomainMatcher", "hybrid") ?: "hybrid",

        splitTunnelMode = enumValue("splitTunnelMode", SplitTunnelMode.ALL_APPS),
        splitTunnelPackages = prefs.getString("splitTunnelPackages", "") ?: "",

        dnsPrimaryIp = prefs.getString("dnsPrimaryIp", "1.1.1.1") ?: "1.1.1.1",
        dnsSecondaryIp = prefs.getString("dnsSecondaryIp", "8.8.8.8") ?: "8.8.8.8",
        dnsPrimaryDoH = prefs.getString("dnsPrimaryDoH", "https://1.1.1.1/dns-query") ?: "https://1.1.1.1/dns-query",
        dnsSecondaryDoH = prefs.getString("dnsSecondaryDoH", "https://8.8.8.8/dns-query") ?: "https://8.8.8.8/dns-query",
        dnsQueryStrategy = prefs.getString("dnsQueryStrategy", "UseIP") ?: "UseIP",

        addressFamilyMode = familyMode,
        ipv6Enabled = familyMode != AddressFamilyMode.FORCE_IPV4,
        preferIpv6 = familyMode == AddressFamilyMode.PREFER_IPV6 ||
            familyMode == AddressFamilyMode.FORCE_IPV6 || familyMode == AddressFamilyMode.SMART,

        strictAddressFamily = prefs.getBoolean("strictAddressFamily", false),

        adaptiveHappyEyeballsEnabled = prefs.getBoolean("adaptiveHappyEyeballsEnabled", true),
        happyEyeballsTryDelayMs = prefs.getInt("happyEyeballsTryDelayMs", 60).coerceIn(0, 500),
        happyEyeballsMaxConcurrent = prefs.getInt("happyEyeballsMaxConcurrent", 4).coerceIn(2, 8),
        adaptiveTcpFastOpenEnabled = prefs.getBoolean("adaptiveTcpFastOpenEnabled", true),
        tcpFastOpenEnabled = prefs.getBoolean("tcpFastOpenEnabled", false),
        adaptiveMssEnabled = prefs.getBoolean("adaptiveMssEnabled", true),
        tcpMaxSeg = prefs.getInt("tcpMaxSeg", 0).coerceIn(0, 9000),

        muxEnabled = prefs.getBoolean("muxEnabled", false),
        muxConcurrency = prefs.getInt("muxConcurrency", 8).coerceIn(1, 128),
        muxXudpConcurrency = prefs.getInt("muxXudpConcurrency", 16).coerceIn(1, 128),
        muxUdp443 = XrayMuxUdp443Modes.parse(prefs.getString("muxUdp443", "skip") ?: "skip"),

        xrayDnsLog = prefs.getBoolean("xrayDnsLog", false),
        xraySniffDestOverride = prefs.getString("xraySniffDestOverride", "http,tls,quic")
            ?: "http,tls,quic",
        xraySniffMetadataOnly = prefs.getBoolean("xraySniffMetadataOnly", false),
        xraySocksUdpEnabled = prefs.getBoolean("xraySocksUdpEnabled", true),
        xraySockoptDomainStrategy = XrayDomainStrategies.parse(
            prefs.getString("xraySockoptDomainStrategy", "AsIs") ?: "AsIs"
        ),
        xrayTcpNoDelay = prefs.getBoolean("xrayTcpNoDelay", false),
        xrayTcpKeepAliveIntervalSec = prefs.getInt("xrayTcpKeepAliveIntervalSec", 0).coerceIn(0, 7200),
        xrayTcpUserTimeoutMs = prefs.getInt("xrayTcpUserTimeoutMs", 0).coerceIn(0, 600_000),
        xrayTcpCongestion = prefs.getString("xrayTcpCongestion", "") ?: "",
        xrayTcpMptcp = prefs.getBoolean("xrayTcpMptcp", true),
        xrayTcpWindowClamp = prefs.getInt("xrayTcpWindowClamp", 0).coerceIn(0, 65535),
        xrayPolicyHandshakeSec = prefs.getInt("xrayPolicyHandshakeSec", 0).coerceIn(0, 3600),
        xrayPolicyConnIdleSec = prefs.getInt("xrayPolicyConnIdleSec", 0).coerceIn(0, 86_400),
        xrayPolicyUplinkOnlySec = prefs.getInt("xrayPolicyUplinkOnlySec", 0).coerceIn(0, 86_400),
        xrayPolicyDownlinkOnlySec = prefs.getInt("xrayPolicyDownlinkOnlySec", 0).coerceIn(0, 86_400),
        xrayPolicyBufferSizeKb = prefs.getInt("xrayPolicyBufferSizeKb", 0).coerceIn(0, 65_536),
        xrayExtraJson = prefs.getString("xrayExtraJson", "") ?: "",
        singBoxLogTimestamp = prefs.getBoolean("singBoxLogTimestamp", false),
        singBoxSniffOverrideDestination = prefs.getBoolean("singBoxSniffOverrideDestination", false),
        singBoxInboundUsername = prefs.getString("singBoxInboundUsername", "") ?: "",
        singBoxInboundPassword = prefs.getString("singBoxInboundPassword", "") ?: "",
        singBoxMuxEnabled = prefs.getBoolean("singBoxMuxEnabled", false),
        singBoxMuxProtocol = SingBoxMuxProtocols.parse(
            prefs.getString("singBoxMuxProtocol", "h2mux") ?: "h2mux"
        ),
        singBoxMuxMaxConnections = prefs.getInt("singBoxMuxMaxConnections", 4).coerceIn(1, 128),
        singBoxMuxMinStreams = prefs.getInt("singBoxMuxMinStreams", 4).coerceIn(0, 128),
        singBoxMuxMaxStreams = prefs.getInt("singBoxMuxMaxStreams", 32).coerceIn(1, 1024),
        singBoxMuxPadding = prefs.getBoolean("singBoxMuxPadding", false),
        singBoxExtraJson = prefs.getString("singBoxExtraJson", "") ?: "",

        iranModePolicy = enumValue("iranModePolicy", IranModePolicy.OFF),
        iranModeCountermeasures = prefs.getBoolean("iranModeCountermeasures", true),
        iranDomesticDirect = prefs.getBoolean("iranDomesticDirect", true),

        intelligenceEnabled = true,
        configCompatibilityMode = prefs.getBoolean("configCompatibilityMode", true),

        allowUnencryptedPublicOutbound = prefs.getBoolean("allowUnencryptedPublicOutbound", true),
        verifiedPerformanceTuning = prefs.getBoolean("verifiedPerformanceTuning", true),
        connectTuningEnabled = prefs.getBoolean("connectTuningEnabled", false),
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
        continuousOptimizerEnabled = prefs.getBoolean("continuousOptimizerEnabled", false),
        optimizerIntervalSec = prefs.getInt("optimizerIntervalSec", 120).coerceIn(60, 900),
        optimizerCandidateCount = prefs.getInt("optimizerCandidateCount", 4).coerceIn(2, 8),
        optimizerDeepScanEvery = prefs.getInt("optimizerDeepScanEvery", 8).coerceIn(3, 20),
        optimizerSwitchCooldownSec = prefs.getInt("optimizerSwitchCooldownSec", 300).coerceIn(60, 1800),
        optimizerConfirmations = prefs.getInt("optimizerConfirmations", 2).coerceIn(1, 3),
        optimizerAvoidHeavyTraffic = prefs.getBoolean("optimizerAvoidHeavyTraffic", true),
        healthHistoryEnabled = prefs.getBoolean("healthHistoryEnabled", true),
        raceConnectEnabled = prefs.getBoolean("raceConnectEnabled", false),
        raceWidth = prefs.getInt("raceWidth", 4).coerceIn(2, 4),
        smartFallbackEnabled = prefs.getBoolean("smartFallbackEnabled", true),
        fallbackCount = prefs.getInt("fallbackCount", 3),
        autoReconnectAfterKillSwitch = prefs.getBoolean("autoReconnectAfterKillSwitch", true),
        networkChangeRecoveryEnabled = prefs.getBoolean("networkChangeRecoveryEnabled", true),
        adaptiveMtuEnabled = prefs.getBoolean("adaptiveMtuEnabled", true),
        mtuMin = prefs.getInt("mtuMin", 1280),
        mtuMax = prefs.getInt("mtuMax", 1500),
        dnsHijackEnabled = prefs.getBoolean("dnsHijackEnabled", true),

        dnsFakeIpEnabled = prefs.getBoolean("dnsFakeIpEnabled", true),
        adaptiveDnsEnabled = prefs.getBoolean("adaptiveDnsEnabled", true),
        adaptiveDualStackEnabled = prefs.getBoolean("adaptiveDualStackEnabled", true),
        adaptiveThroughputEnabled = prefs.getBoolean("adaptiveThroughputEnabled", true),
        adaptiveThroughputMaxBytes = prefs.getInt("adaptiveThroughputMaxBytes", 4 * 1024 * 1024),
        udpProbeEnabled = prefs.getBoolean("udpProbeEnabled", true),
        thermalAwareEnabled = prefs.getBoolean("thermalAwareEnabled", true),
        workloadProfile = enumValue("workloadProfile", WorkloadProfile.AUTO),

        theme = prefs.getString("theme", "light") ?: "light",
        fontFamily = storedFontFamily().id,

        homeStyle = storedHomeStyle().id,
        appLanguage = parseAppLanguage(prefs.getString("appLanguage", AppLanguage.SYSTEM.id) ?: AppLanguage.SYSTEM.id).id,

        modularCardOrder = ModularLayout.serialize(
            ModularLayout.order(
                prefs.getString("modularCardOrder", ModularLayout.DEFAULT_ORDER)
                    ?: ModularLayout.DEFAULT_ORDER
            )
        ),
        modularShowStats = prefs.getBoolean("modularShowStats", true),
        modularShowSocks = prefs.getBoolean("modularShowSocks", false),
        modularShowShortcuts = prefs.getBoolean("modularShowShortcuts", true),
        // MARBLE_HOME_STUDIO_V249 — the layout Home actually draws. Both strings are canonicalized
        // on the way in, so a section added by a future build appears in the order (at the end)
        // instead of being silently dropped, and a section retired by one disappears from it.
        homeLayout = HomeLayout.of(
            sectionOrder = prefs.getString("homeSectionOrder", HomeStudio.DEFAULT_ORDER),
            hiddenSections = prefs.getString("homeHiddenSections", HomeStudio.DEFAULT_HIDDEN)
        ),
        appCalmMotion = prefs.getBoolean("appCalmMotion", false),
        modularShowStatus = prefs.getBoolean("modularShowStatus", true),
        modularShowServers = prefs.getBoolean("modularShowServers", true),
        modularConnectStyle = prefs.getString("modularConnectStyle", "SLIDER") ?: "SLIDER",
        modularCardSize = parseModularCardSize(
            prefs.getString("modularCardSize", ModularCardSize.COMPACT.id) ?: ModularCardSize.COMPACT.id
        ).id,
        modularCardHeightDp = prefs.getInt("modularCardHeightDp", 180).coerceIn(160, 360),
        modularHideCustomizerButton = prefs.getBoolean("modularHideCustomizerButton", false),

        connectButtonStyle = parseConnectButtonStyle(prefs.getString("connectButtonStyle", ConnectButtonStyle.ROUND.id) ?: ConnectButtonStyle.ROUND.id).id,

        darkOutlineStyle = parseDarkOutlineStyle(prefs.getString("darkOutlineStyle", DarkOutlineStyle.SUBTLE.id) ?: DarkOutlineStyle.SUBTLE.id).id,

        dockShowLabels = prefs.getBoolean("dockShowLabels", false),
        dockShowIcons = prefs.getBoolean("dockShowIcons", true),
        dockSize = encodeDockPreference(
            parseDockSize(prefs.getString("dockSize", DockSize.MEDIUM.id) ?: DockSize.MEDIUM.id),
            parseDockPresentation(prefs.getString("dockSize", DockSize.MEDIUM.id) ?: DockSize.MEDIUM.id)
        ),

        dockSlotEnabled = prefs.getBoolean("dockSlotEnabled", true),
        dockSlotKind = parseDockSlotKind(prefs.getString("dockSlotKind", DockSlotKind.DEFAULT.id) ?: DockSlotKind.DEFAULT.id).id,
        dockSlotSourceId = prefs.getString("dockSlotSourceId", "all") ?: "all",
        dockSlotProfileId = prefs.getString("dockSlotProfileId", "") ?: "",
        dockSlotProfileSourceId = prefs.getString("dockSlotProfileSourceId", "") ?: "",
        dockSlotLabel = dockSlotCaption(prefs.getString("dockSlotLabel", "") ?: ""),
        dockSlotIcon = parseDockSlotIcon(prefs.getString("dockSlotIcon", DockSlotIcon.DEFAULT.id) ?: DockSlotIcon.DEFAULT.id).id,
        dockSlotAccent = parseDockSlotAccent(
            prefs.getString("dockSlotAccent", DockSlotAccent.DEFAULT.id) ?: DockSlotAccent.DEFAULT.id
        ).id,
        dockSlotShowStatusBadge = prefs.getBoolean("dockSlotShowStatusBadge", true),

        textScalePercent = TextScale.clamp(prefs.getInt("textScalePercent", TextScale.DEFAULT_PERCENT)),

        autoServerSelectorEnabled = prefs.getBoolean("autoServerSelectorEnabled", false),
        autoServerStrategy = parseAutoServerStrategy(
            prefs.getString("autoServerStrategy", AutoServerStrategy.DEFAULT.id) ?: AutoServerStrategy.DEFAULT.id
        ).id,
        autoServerPingWeight = prefs.getInt("autoServerPingWeight", 40).coerceIn(0, 100),
        autoServerLoadWeight = prefs.getInt("autoServerLoadWeight", 30).coerceIn(0, 100),
        autoServerStabilityWeight = prefs.getInt("autoServerStabilityWeight", 20).coerceIn(0, 100),
        autoServerFreshnessWeight = prefs.getInt("autoServerFreshnessWeight", 10).coerceIn(0, 100),
        autoServerOnScan = prefs.getBoolean("autoServerOnScan", true),
        autoServerOnConnect = prefs.getBoolean("autoServerOnConnect", false),
        autoServerOnFailure = prefs.getBoolean("autoServerOnFailure", true),
        autoServerScope = parseAutoServerScope(
            prefs.getString("autoServerScope", AutoServerScope.DEFAULT.id) ?: AutoServerScope.DEFAULT.id
        ).id,
        autoServerSwitchMarginPercent = prefs.getInt("autoServerSwitchMarginPercent", 15)
            .coerceIn(0, 100),

        // MARBLE_MERIDIAN_V221 — the flat chapter's decisions. Every value is parsed through its
        // own normaliser, so a backup written by another build (or a hand-edited preference) can
        // never put an unknown id into the settings object.
        flatInterface = prefs.getBoolean("flatInterface", true),
        powerProfile = parsePowerProfile(
            prefs.getString("powerProfile", PowerProfile.DEFAULT.id) ?: PowerProfile.DEFAULT.id
        ).id,
        fragmentStrategy = parseFragmentStrategy(
            prefs.getString("fragmentStrategy", FragmentStrategy.DEFAULT.id)
                ?: FragmentStrategy.DEFAULT.id
        ).id,
        // MARBLE_APP_BEHAVIOR_GROUP_V238 — seven app-behavior switches, one grouped axis.
        appBehavior = AppBehaviorPrefs(
            probeSpeedTest = prefs.getBoolean("probeSpeedTest", false),
            iranDeepProbeEnabled = prefs.getBoolean("iranDeepProbeEnabled", true),
            accentTheme = parseFlatAccent(
                prefs.getString("accentTheme", FlatAccent.DEFAULT.id) ?: FlatAccent.DEFAULT.id
            ).id,
            dynamicColor = prefs.getBoolean("dynamicColor", true),
            warmupOnLaunch = prefs.getBoolean("warmupOnLaunch", true),
            censorshipAutoApply = prefs.getBoolean("censorshipAutoApply", false),
            debugModeEnabled = prefs.getBoolean("debugModeEnabled", false)
        ),
        // MARBLE_SMART_JITTER_V238 — the reducer's persisted knobs, one grouped axis.
        smartJitter = SmartJitterSettings(
            mode = JitterSmartMode.fromId(
                prefs.getString("jitterSmartMode", null)
            ).id,
            probeHost = (prefs.getString("jitterProbeHost", null) ?: SmartJitterDefaultsKeys.PROBE_HOST_DEFAULT).trim().take(253),
            probeIntervalSec = prefs.getInt("jitterProbeIntervalSec", SmartJitterDefaultsKeys.INTERVAL_SEC_DEFAULT)
                .coerceIn(SmartJitterDefaultsKeys.INTERVAL_SEC_MIN, SmartJitterDefaultsKeys.INTERVAL_SEC_MAX),
            switchImprovePercent = prefs.getInt("jitterSwitchImprovePercent", SmartJitterDefaultsKeys.IMPROVE_PERCENT_DEFAULT)
                .coerceIn(SmartJitterDefaultsKeys.IMPROVE_PERCENT_MIN, SmartJitterDefaultsKeys.IMPROVE_PERCENT_MAX),
            switchCooldownSec = prefs.getInt("jitterSwitchCooldownSec", SmartJitterDefaultsKeys.COOLDOWN_SEC_DEFAULT)
                .coerceIn(SmartJitterDefaultsKeys.COOLDOWN_SEC_MIN, SmartJitterDefaultsKeys.COOLDOWN_SEC_MAX),
            minSamples = prefs.getInt("jitterMinSamples", SmartJitterDefaultsKeys.MIN_SAMPLES_DEFAULT)
                .coerceIn(SmartJitterDefaultsKeys.MIN_SAMPLES_MIN, SmartJitterDefaultsKeys.MIN_SAMPLES_MAX),
            autoTunerEnabled = prefs.getBoolean("jitterAutoTunerEnabled", false),
            tunerMtu = prefs.getInt("jitterTunerMtu", 0).let { if (it == 0) 0 else JitterAutoTuner.sanitizeOverride(it) ?: 0 }
        ),
        // MARBLE_NETWORK_COUNCIL_V236 — the one switch the scanner owns.
        )
    }

    fun saveSettings(s: AppSettings) = prefs.edit()
        .putInt("socksPort", s.socksPort)
        .putInt("localProxyPort", s.localProxyPort)
        .putString("connectionMode", s.connectionMode.name)
        .putString("probeMethod", s.probeMethod.name)
        .putString("delayTestUrl", DelayTest.url(s.delayTestUrl))
        .putString("coreEngineId", parseCoreEngine(s.coreEngineId).id)
        .putString("tunnelCoreId", parseTunnelCore(s.tunnelCoreId).id)
        .putInt("tun2SocksMtu", s.tun2SocksMtu.let { if (it == 0) 0 else it.coerceIn(Tun2SocksPolicy.MIN_MTU, Tun2SocksPolicy.MAX_MTU) })
        .putInt("tun2SocksUdpTimeoutSec", Tun2SocksPolicy.normalizeUdpTimeout(s.tun2SocksUdpTimeoutSec))
        .putInt("tun2SocksTcpSendBufferKb", Tun2SocksPolicy.normalizeBufferKb(s.tun2SocksTcpSendBufferKb))
        .putInt("tun2SocksTcpReceiveBufferKb", Tun2SocksPolicy.normalizeBufferKb(s.tun2SocksTcpReceiveBufferKb))
        .putBoolean("tun2SocksModerateReceiveBuffer", s.tun2SocksModerateReceiveBuffer)
        .putString("tun2SocksLogLevel", Tun2SocksPolicy.normalizeLogLevel(s.tun2SocksLogLevel))
        .putBoolean("singBoxUnifiedDelay", s.singBoxUnifiedDelay)
        .putInt("singBoxConnectTimeoutSec", s.singBoxConnectTimeoutSec.coerceIn(3, 60))
        .putBoolean("singBoxCacheFile", s.singBoxCacheFile)
        .putBoolean("singBoxPreferParser", s.singBoxPreferParser)
        .putString("xrayLogLevel", s.xrayLogLevel)
        .putBoolean("xraySniffingEnabled", s.xraySniffingEnabled)
        .putBoolean("xraySniffingRouteOnly", s.xraySniffingRouteOnly)
        .putBoolean("xrayAllowLan", s.xrayAllowLan)
        .putInt("xrayHttpInboundPort", s.xrayHttpInboundPort.coerceIn(0, 65535))
        .putString("singBoxLogLevel", s.singBoxLogLevel)
        .putBoolean("singBoxSniffEnabled", s.singBoxSniffEnabled)
        .putBoolean("singBoxResolveDestination", s.singBoxResolveDestination)
        .putBoolean("singBoxAllowLan", s.singBoxAllowLan)
        .putInt("singBoxHttpInboundPort", s.singBoxHttpInboundPort.coerceIn(0, 65535))
        .putBoolean("probeSpeedTest", s.probeSpeedTest)
        .putString("benchMode", s.benchMode.name)
        .putInt("benchCandidates", s.benchCandidates)
        .putInt("benchSamples", s.benchSamples)
        .putInt("benchTimeoutSec", s.benchTimeoutSec)
        .putInt("benchBytes", s.benchBytes)
        .putInt("tcpPrecheckTimeoutMs", s.tcpPrecheckTimeoutMs)
        .putInt("tcpWorkers", s.tcpWorkers)
        .putInt("pingTimeoutSec", PingBudget.timeoutSec(s.pingTimeoutSec))
        .putInt("pingSamples", PingBudget.samples(s.pingSamples))
        .putBoolean("pingSpeedCustom", s.pingSpeedCustom)
        .putInt("pingSpeedPercent", PingSpeed.percent(s.pingSpeedPercent))
        .putString("pingParallelMode", s.pingParallelMode.name)
        .putInt("pingConcurrency", PingBudget.concurrency(s.pingConcurrency))
        .putString("nodeSortMode", s.nodeSortMode.name)
        .putBoolean("nodeSortReverse", s.nodeSortReverse)
        .putString("serversProtocolFilter", s.serversProtocolFilter.trim().uppercase())
        .putBoolean("serversOnlyReachable", s.serversOnlyReachable)
        .putInt("serversMaxPingMs", s.serversMaxPingMs.coerceAtLeast(0))
        .putBoolean("serversGroupByCountry", s.serversGroupByCountry)
        .putString("serversLayout", s.serversLayout)
        .putString("homePingGaugeAction", s.homePingGaugeAction)
        .putString("homePingChipAction", s.homePingChipAction)
        .putString("homePingHeaderAction", s.homePingHeaderAction)
        .putBoolean("subscriptionAutoRefresh", s.subscriptionAutoRefresh)
        .putBoolean("appUpdateCheckEnabled", s.appUpdateCheckEnabled)
        .putInt("subscriptionRefreshHours", s.subscriptionRefreshHours)
        .putBoolean("homeShowLiveQuality", s.homeShowLiveQuality)
        .putBoolean("homeSpeedWidgetEnabled", s.homeSpeedWidgetEnabled)
        .putBoolean("autoConnectBestAfterScan", s.autoConnectBestAfterScan)
        .putBoolean("serverIntelEnabled", s.serverIntelEnabled)
        .putBoolean("serverLocationAutoDetect", s.serverLocationAutoDetect)
        .putBoolean("homeShowDataUsage", s.homeShowDataUsage)
        .putBoolean("homeAmbientBackdrop", s.homeAmbientBackdrop)
        .putBoolean("smartNotificationsEnabled", s.smartNotificationsEnabled)
        .putBoolean("notifyConnectionEvents", s.notifyConnectionEvents)
        .putBoolean("notifyRecoveryEvents", s.notifyRecoveryEvents)
        .putBoolean("notifyPrivacyWarnings", s.notifyPrivacyWarnings)
        .putBoolean("notifyNetworkChanges", s.notifyNetworkChanges)
        .putBoolean("notifySubscriptionEvents", s.notifySubscriptionEvents)
        .putBoolean("notifyCoreUpdates", s.notifyCoreUpdates)
        .putBoolean("notificationLiveStats", s.notificationLiveStats)
        .putInt("notificationCooldownSec", s.notificationCooldownSec.coerceIn(5, 300))
        .putBoolean("notifyPrivacyHeadsUp", s.notifyPrivacyHeadsUp)
        .putBoolean("notificationSessionTimer", s.notificationSessionTimer)
        // MARBLE_SHADE_STUDIO_V249 — written through the same normalizers the load uses.
        .putBoolean("notificationShadePicker", s.shadeControl.picker)
        .putBoolean("notificationShadeCycle", s.shadeControl.cycle)
        .putString("notificationShadeStyle", ShadeControlDefaults.styleId(s.shadeControl.style))
        .putBoolean("notificationShadePing", s.shadeControl.showPing)
        .putInt("notificationShadeRows", ShadeControlDefaults.rowChoice(s.shadeControl.rows))
        .putString("routingMode", s.routingMode.name)
        .putBoolean("customRoutingEnabled", s.customRoutingEnabled)
        .putString(
            "geoAssetSourceIds",

            com.marbleng.app.core.GeoAssetRegistry.serializeIds(
                com.marbleng.app.core.GeoAssetRegistry.parseIds(s.geoAssetSourceIds)
            )
        )
        .putString("geoCustomSourcesJson", s.geoCustomSourcesJson)
        .putString("geoPrecision", s.geoPrecision)
        .putBoolean("geoMultiSourceEnabled", s.geoMultiSourceEnabled)
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
        .putString("routeDomainMatcher", s.routeDomainMatcher)
        .putString("splitTunnelMode", s.splitTunnelMode.name)
        .putString("splitTunnelPackages", s.splitTunnelPackages)
        .putString("dnsPrimaryIp", s.dnsPrimaryIp)
        .putString("dnsSecondaryIp", s.dnsSecondaryIp)
        .putString("dnsPrimaryDoH", s.dnsPrimaryDoH)
        .putString("dnsSecondaryDoH", s.dnsSecondaryDoH)
        .putString("dnsQueryStrategy", s.dnsQueryStrategy)
        .putString("addressFamilyMode", s.addressFamilyMode.name)
        .putBoolean("ipv6Enabled", s.ipv6Enabled)
        .putBoolean("preferIpv6", s.preferIpv6)
        .putBoolean("strictAddressFamily", s.strictAddressFamily)
        .putBoolean("adaptiveHappyEyeballsEnabled", s.adaptiveHappyEyeballsEnabled)
        .putInt("happyEyeballsTryDelayMs", s.happyEyeballsTryDelayMs.coerceIn(0, 500))
        .putInt("happyEyeballsMaxConcurrent", s.happyEyeballsMaxConcurrent.coerceIn(2, 8))
        .putBoolean("adaptiveTcpFastOpenEnabled", s.adaptiveTcpFastOpenEnabled)
        .putBoolean("tcpFastOpenEnabled", s.tcpFastOpenEnabled)
        .putBoolean("adaptiveMssEnabled", s.adaptiveMssEnabled)
        .putInt("tcpMaxSeg", s.tcpMaxSeg.coerceIn(0, 9000))
        .putBoolean("allowUnencryptedPublicOutbound", s.allowUnencryptedPublicOutbound)
        .putBoolean("muxEnabled", s.muxEnabled)
        .putInt("muxConcurrency", s.muxConcurrency.coerceIn(1, 128))
        .putInt("muxXudpConcurrency", s.muxXudpConcurrency.coerceIn(1, 128))
        .putString("muxUdp443", XrayMuxUdp443Modes.parse(s.muxUdp443))
        .putBoolean("xrayDnsLog", s.xrayDnsLog)
        .putString("xraySniffDestOverride", s.xraySniffDestOverride)
        .putBoolean("xraySniffMetadataOnly", s.xraySniffMetadataOnly)
        .putBoolean("xraySocksUdpEnabled", s.xraySocksUdpEnabled)
        .putString("xraySockoptDomainStrategy", XrayDomainStrategies.parse(s.xraySockoptDomainStrategy))
        .putBoolean("xrayTcpNoDelay", s.xrayTcpNoDelay)
        .putInt("xrayTcpKeepAliveIntervalSec", s.xrayTcpKeepAliveIntervalSec)
        .putInt("xrayTcpUserTimeoutMs", s.xrayTcpUserTimeoutMs)
        .putString("xrayTcpCongestion", s.xrayTcpCongestion)
        .putBoolean("xrayTcpMptcp", s.xrayTcpMptcp)
        .putInt("xrayTcpWindowClamp", s.xrayTcpWindowClamp)
        .putInt("xrayPolicyHandshakeSec", s.xrayPolicyHandshakeSec)
        .putInt("xrayPolicyConnIdleSec", s.xrayPolicyConnIdleSec)
        .putInt("xrayPolicyUplinkOnlySec", s.xrayPolicyUplinkOnlySec)
        .putInt("xrayPolicyDownlinkOnlySec", s.xrayPolicyDownlinkOnlySec)
        .putInt("xrayPolicyBufferSizeKb", s.xrayPolicyBufferSizeKb)
        .putString("xrayExtraJson", s.xrayExtraJson)
        .putBoolean("singBoxLogTimestamp", s.singBoxLogTimestamp)
        .putBoolean("singBoxSniffOverrideDestination", s.singBoxSniffOverrideDestination)
        .putString("singBoxInboundUsername", s.singBoxInboundUsername)
        .putString("singBoxInboundPassword", s.singBoxInboundPassword)
        .putBoolean("singBoxMuxEnabled", s.singBoxMuxEnabled)
        .putString("singBoxMuxProtocol", SingBoxMuxProtocols.parse(s.singBoxMuxProtocol))
        .putInt("singBoxMuxMaxConnections", s.singBoxMuxMaxConnections.coerceIn(1, 128))
        .putInt("singBoxMuxMinStreams", s.singBoxMuxMinStreams.coerceIn(0, 128))
        .putInt("singBoxMuxMaxStreams", s.singBoxMuxMaxStreams.coerceIn(1, 1024))
        .putBoolean("singBoxMuxPadding", s.singBoxMuxPadding)
        .putString("singBoxExtraJson", s.singBoxExtraJson)
        .putBoolean("thermalAwareEnabled", s.thermalAwareEnabled)
        .putString("workloadProfile", s.workloadProfile.name)
        .putString("theme", s.theme)
        .putString("fontFamily", parseAppFont(s.fontFamily).id)
        .putString("homeStyle", parseHomeStyle(s.homeStyle).id)
        .putString("appLanguage", parseAppLanguage(s.appLanguage).id)
        .putString("modularCardOrder", ModularLayout.serialize(ModularLayout.order(s.modularCardOrder)))
        .putBoolean("modularShowStats", s.modularShowStats)
        .putBoolean("modularShowSocks", s.modularShowSocks)
        .putBoolean("modularShowShortcuts", s.modularShowShortcuts)
        // MARBLE_HOME_STUDIO_V249 — the layout Home draws, canonicalized on the way out too, so a
        // restored backup can never carry an id this build does not know.
        .putString("homeSectionOrder", HomeStudio.canonicalOrder(s.homeLayout.sectionOrder))
        .putString(
            "homeHiddenSections",
            HomeStudio.canonicalHidden(s.homeLayout.hiddenSections)
        )
        .putBoolean("appCalmMotion", s.appCalmMotion)
        .putBoolean("modularShowStatus", s.modularShowStatus)
        .putBoolean("modularShowServers", s.modularShowServers)
        .putString("modularConnectStyle", s.modularConnectStyle)
        .putString("modularCardSize", parseModularCardSize(s.modularCardSize).id)
        .putInt("modularCardHeightDp", s.modularCardHeightDp.coerceIn(160, 360))
        .putBoolean("modularHideCustomizerButton", s.modularHideCustomizerButton)
        .putString("connectButtonStyle", parseConnectButtonStyle(s.connectButtonStyle).id)
        .putString("darkOutlineStyle", parseDarkOutlineStyle(s.darkOutlineStyle).id)
        .putBoolean("dockShowLabels", s.dockShowLabels)
        .putBoolean("dockShowIcons", s.dockShowIcons)
        .putString(
            "dockSize",
            encodeDockPreference(parseDockSize(s.dockSize), parseDockPresentation(s.dockSize))
        )
        .putBoolean("dockSlotEnabled", s.dockSlotEnabled)
        .putString("dockSlotKind", parseDockSlotKind(s.dockSlotKind).id)
        .putString("dockSlotSourceId", s.dockSlotSourceId.trim())
        .putString("dockSlotProfileId", s.dockSlotProfileId.trim())
        .putString("dockSlotProfileSourceId", s.dockSlotProfileSourceId.trim())
        .putString("dockSlotLabel", dockSlotCaption(s.dockSlotLabel))
        .putString("dockSlotIcon", parseDockSlotIcon(s.dockSlotIcon).id)
        .putString("dockSlotAccent", parseDockSlotAccent(s.dockSlotAccent).id)
        .putBoolean("dockSlotShowStatusBadge", s.dockSlotShowStatusBadge)
        .putBoolean("debugModeEnabled", s.debugModeEnabled)
        .putInt("textScalePercent", s.textScalePercent)
        .putBoolean("autoServerSelectorEnabled", s.autoServerSelectorEnabled)
        .putString("autoServerStrategy", s.autoServerStrategyEnum.id)
        .putInt("autoServerPingWeight", s.autoServerPingWeight.coerceIn(0, 100))
        .putInt("autoServerLoadWeight", s.autoServerLoadWeight.coerceIn(0, 100))
        .putInt("autoServerStabilityWeight", s.autoServerStabilityWeight.coerceIn(0, 100))
        .putInt("autoServerFreshnessWeight", s.autoServerFreshnessWeight.coerceIn(0, 100))
        .putBoolean("autoServerOnScan", s.autoServerOnScan)
        .putBoolean("autoServerOnConnect", s.autoServerOnConnect)
        .putBoolean("autoServerOnFailure", s.autoServerOnFailure)
        .putString("autoServerScope", s.autoServerScopeEnum.id)
        .putInt("autoServerSwitchMarginPercent", s.autoServerSwitchMarginPercent.coerceIn(0, 100))
        // MARBLE_MERIDIAN_V221 — the flat chapter writes what it reads, through the same
        // normalisers, so the preference file can never hold an id the model does not know.
        .putBoolean("flatInterface", s.flatInterface)
        .putString("accentTheme", parseFlatAccent(s.accentTheme).id)
        .putString("powerProfile", parsePowerProfile(s.powerProfile).id)
        .putString("fragmentStrategy", parseFragmentStrategy(s.fragmentStrategy).id)
        .putBoolean("warmupOnLaunch", s.warmupOnLaunch)
        .putBoolean("censorshipAutoApply", s.censorshipAutoApply)
        .putBoolean("dynamicColor", s.dynamicColor)
        // MARBLE_SMART_JITTER_V238 — the reducer's persisted knobs.
        .putString("jitterSmartMode", JitterSmartMode.fromId(s.jitterSmartMode).id)
        .putString("jitterProbeHost", s.jitterProbeHost.trim().take(253))
        .putInt("jitterProbeIntervalSec", s.jitterProbeIntervalSec.coerceIn(SmartJitterDefaultsKeys.INTERVAL_SEC_MIN, SmartJitterDefaultsKeys.INTERVAL_SEC_MAX))
        .putInt("jitterSwitchImprovePercent", s.jitterSwitchImprovePercent.coerceIn(SmartJitterDefaultsKeys.IMPROVE_PERCENT_MIN, SmartJitterDefaultsKeys.IMPROVE_PERCENT_MAX))
        .putInt("jitterSwitchCooldownSec", s.jitterSwitchCooldownSec.coerceIn(SmartJitterDefaultsKeys.COOLDOWN_SEC_MIN, SmartJitterDefaultsKeys.COOLDOWN_SEC_MAX))
        .putInt("jitterMinSamples", s.jitterMinSamples.coerceIn(SmartJitterDefaultsKeys.MIN_SAMPLES_MIN, SmartJitterDefaultsKeys.MIN_SAMPLES_MAX))
        .putBoolean("jitterAutoTunerEnabled", s.jitterAutoTunerEnabled)
        .putInt("jitterTunerMtu", s.jitterTunerMtu.let { if (it == 0) 0 else JitterAutoTuner.sanitizeOverride(it) ?: 0 })
        .apply()

    private fun storedFontFamily(): AppFont {
        val raw = prefs.getString("fontFamily", null) ?: return AppFont.DEFAULT
        return AppFont.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
            ?: AppFont.DEFAULT
    }

    private fun storedHomeStyle(): HomeStyle {
        val raw = prefs.getString("homeStyle", null) ?: return HomeStyle.DEFAULT
        return HomeStyle.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
            ?: HomeStyle.DEFAULT
    }

    /**
     * MARBLE_SHADE_STUDIO_V249 — the shade's picker shape. [ShadeControlDefaults.styleId] owns the
     * normalization so the read and the write can never disagree about a spelling.
     */
    private fun storedShadeStyle(): String =
        ShadeControlDefaults.styleId(prefs.getString("notificationShadeStyle", null))

    private fun probeMethod(): ProbeMethod = when (prefs.getString("probeMethod", null)) {
        ProbeMethod.REAL_DELAY.name, "TUNNEL" -> ProbeMethod.REAL_DELAY
        ProbeMethod.TCP_PING.name, "TCP_CONNECT" -> ProbeMethod.TCP_PING
        ProbeMethod.URL_TEST.name -> ProbeMethod.URL_TEST
        "HYBRID", "TCP_RECOMMENDED", "HTTP_GET", "HTTP_HEAD", "ICMP" -> ProbeMethod.REAL_DELAY
        else -> ProbeMethod.REAL_DELAY
    }

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
