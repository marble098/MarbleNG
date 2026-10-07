package com.marbleng.app.data

import android.content.Context
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.IpFamilyScan
import com.marbleng.app.core.parseCoreEngine
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

    /** Portable, versioned backup of all user-owned local state (including future preferences). */
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

    /** Atomically replaces local state after validating a MarbleNG backup. */
    fun restoreBackup(raw: String) {
        val root = JSONObject(raw)
        require(root.optString("format") == "marbleng-backup") { "Not a MarbleNG backup" }
        require(root.optInt("version") == 1) { "Unsupported backup version" }
        val values = root.getJSONObject("values")

        // Validate the complete document before clear() is staged. JSONObject.NULL and nested
        // objects are not SharedPreferences values; silently ignoring either would turn a
        // malformed backup into a successful but destructive partial restore.
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

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // MARBLE_REMEMBERED_PING_V160 — the last measurement of every server, across restarts.
    // ───────────────────────────────────────────────────────────────────────────────────────────
    //
    // A ping is work the user paid for — a sweep over a 100-node subscription is minutes of the
    // device's radio — and until now it lived in the repository's memory and nowhere else, so
    // leaving the app and coming back showed a list of servers with no latency at all. Everything
    // else the user did survive a restart (the last route, the sources, the settings); the
    // measurement was the one thing the product forgot.
    //
    // The rule is the same one the connection history follows: the newest measurement of a node
    // wins, the table is bounded ([benchmarkMemoryLimit]) so a subscription of any size stays
    // cheap to write, and a stamp older than [benchmarkMemoryMaxAgeMs] is dropped on read —
    // a number from a month ago describes a network that no longer exists.
    // ───────────────────────────────────────────────────────────────────────────────────────────

    /** How many measurements are remembered at most, newest first. */
    val benchmarkMemoryLimit: Int get() = 400

    /** A remembered measurement older than this is no longer evidence about the route. */
    val benchmarkMemoryMaxAgeMs: Long get() = 30L * 24L * 60L * 60L * 1000L

    /**
     * Every remembered measurement that has not aged out, newest first.
     *
     * Entries whose node no longer exists are *not* dropped here: a refresh rewrites a
     * subscription's profile ids while the user's measurements are the point of the feature, so
     * the caller (which owns the live library) does the filtering. An orphan row costs one JSON
     * object and is pruned the next time its node is gone for good.
     */
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

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // MARBLE_SERVER_LOCATION_V192 — the once-per-endpoint location tests, across restarts.
    // ───────────────────────────────────────────────────────────────────────────────────────────
    //
    // A location test costs a handful of kilobytes of the radio exactly once per server. The
    // table below is the durable half of that contract: key (host:port, canonical form from
    // ServerLocationKey) → ISO code + the moment it was learned. A failure is never written —
    // a lookup that could not be answered today may answer tomorrow, so only positive results
    // are remembered and the retry happens naturally on the next app launch.

    /** How many learned locations are remembered at most, newest first. */
    val serverLocationMemoryLimit: Int get() = 1024

    /** Learned locations: endpoint key → (ISO alpha-2 code, learned-at epoch millis). */
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

    /** Merges one learned location; the table is bounded and the oldest entries age out. */
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
                    // MARBLE_SERVER_LOCATION_V197 — a lone answer is remembered as lone. Without
                    // this the app could not tell "measured, five services agreed" from "measured,
                    // one service answered while four were unreachable", so the thin answer was
                    // either trusted forever or discarded forever.
                    .put("provisional", provisionalFlags[k] == true)
            )
        }
        prefs.edit().putString("serverLocations", out.toString()).apply()
    }

    /**
     * MARBLE_SERVER_LOCATION_V197 — which learned locations were single-witness answers.
     *
     * Kept as its own read so the existing [loadServerLocations] contract is untouched: the rows
     * that only want a flag keep asking for a flag.
     */
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

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // MARBLE_IP_FAMILY_SCAN_V196 — which address families each endpoint actually has.
    // ───────────────────────────────────────────────────────────────────────────────────────────
    //
    // A family verdict is cheap to measure and expensive to guess wrong: it decides whether Force
    // IPv6 can dial a node at all. The table below is the durable half — endpoint key (the same
    // `host:port` form the location cache uses) → the whole measurement, including the physical
    // network it was taken on and the moment it was taken. Both are load-bearing: a verdict from a
    // v6-capable home Wi-Fi must not keep Force IPv6 armed in a v4-only café, and a node that
    // gained an AAAA record last week must not stay branded IPv4-only forever. Failures ARE stored
    // here (unlike locations): "this node has no IPv6" is exactly the fact the ladder needs.

    /** How many endpoint family verdicts are remembered at most, newest first. */
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

    /** Merges one measured verdict; the table is bounded and the oldest entries age out. */
    fun saveIpFamilyScan(scan: IpFamilyScan) {
        if (scan.endpoint.isBlank()) return
        val all = loadIpFamilyScans().toMutableMap()
        all[scan.endpoint] = scan
        saveIpFamilyScans(all)
    }

    /** Writes a whole batch at once: a group scan must not cost one commit per server. */
    fun saveIpFamilyScans(scans: Map<String, IpFamilyScan>) {
        val kept = scans.values
            .filter { it.endpoint.isNotBlank() }
            .sortedByDescending { it.scannedAtMs }
            .take(ipFamilyScanMemoryLimit)
        val out = JSONObject()
        kept.forEach { scan -> out.put(scan.endpoint, scan.toJson()) }
        prefs.edit().putString("ipFamilyScans", out.toString()).apply()
    }

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // MARBLE_SESSION_USAGE_V192 — data used per connection, across restarts.
    // ───────────────────────────────────────────────────────────────────────────────────────────
    //
    // The live counter of a session is a memory fact; these rows are the durable half: what
    // each past connection carried, plus the running total. The history is bounded the same way
    // the remembered pings are — a VPN's identity is its routes, and the newest sessions are
    // the ones the user reads.

    /** How many finished sessions are remembered at most, newest first. */
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

    /**
     * The in-flight session's anchor: the byte counter it started from, and the route it runs
     * on. Persisted at CONNECTED and cleared at teardown, so a process death mid-session cannot
     * swallow the session's usage — the next launch finishes the accounting from this row.
     */
    fun loadSessionAnchor(): JSONObject? =
        runCatching { JSONObject(prefs.getString("sessionAnchor", "") ?: "") }.getOrNull()

    fun saveSessionAnchor(anchor: JSONObject) =
        prefs.edit().putString("sessionAnchor", anchor.toString()).apply()

    fun clearSessionAnchor() = prefs.edit().remove("sessionAnchor").apply()

    // ───────────────────────────────────────────────────────────────────────────────────────────
    // MARBLE_AUTO_SERVER_SELECTOR_V202 — the round-robin cursor.
    //
    // Rotation is only rotation if it continues where it stopped. The cursor is a user-visible
    // fact about the library ("next time it picks the third server"), so it survives a restart
    // exactly like the last route does, and it resets to the head when the pool is empty so a
    // rotation nobody remembers starting cannot resume in its middle.
    // ───────────────────────────────────────────────────────────────────────────────────────────
    fun loadAutoServerCursor(): Int =
        prefs.getInt("autoServerCursor", 0).coerceAtLeast(0)

    fun saveAutoServerCursor(cursor: Int) =
        prefs.edit().putInt("autoServerCursor", cursor.coerceAtLeast(0)).apply()

    /**
     * MARBLE_CORE_OPTIONS_V211 — the operator memory table is gone with the learner that wrote
     * it. What it stored was a learned fragment/Mux pair, and the product no longer has a
     * fragment shape to learn: the two cores' options are the user's settings, written to the
     * config verbatim. A preferences file from an older build simply still contains the key; it
     * is never read again (and `resetSettings` drops it with everything else).
     */
    fun dropTransportMemory() {
        if (prefs.contains("transportMemory")) prefs.edit().remove("transportMemory").apply()
    }

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

    /**
     * MARBLE_DURABLE_TUNNEL_INTENT_V133 — "the user asked for a tunnel and never asked to stop it".
     *
     * Android kills this process outright when the APK is replaced; the exit history records it as
     * `REASON_PACKAGE_UPDATE` (reason 16) and the attached log had thirteen of them. A killed tunnel
     * is not a user disconnect, but until now the two were indistinguishable after a restart, so the
     * user had to notice and reconnect by hand every time the app or a core module was updated.
     *
     * This flag is durable user intent, written with the same commit as the last-route reference and
     * cleared only by an explicit disconnect or a blocked startup — never by process death.
     */
    fun tunnelIntentActive(): Boolean = prefs.getBoolean("tunnelIntentActive", false)

    fun setTunnelIntentActive(active: Boolean) =
        prefs.edit().putBoolean("tunnelIntentActive", active).apply()

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

    // MARBLE_LIBRARY_COLLAPSIBLE_V113 — which Library source groups the user has folded shut.
    // Stored as a string set so the open/closed state survives every later visit.
    fun libraryCollapsedSources(): Set<String> =
        (prefs.getStringSet("libraryCollapsedSources", emptySet()) ?: emptySet()).toSet()

    fun setLibraryCollapsedSources(collapsed: Set<String>) =
        prefs.edit()
            .putStringSet("libraryCollapsedSources", collapsed)
            .apply()

    /** Last top-level app destination. Kept outside AppSettings because it is navigation state. */
    fun lastAppTab(): String = prefs.getString("lastAppTab", "DECK")
        ?.trim()
        .orEmpty()
        .ifBlank { "DECK" }

    fun setLastAppTab(name: String) =
        prefs.edit()
            .putString("lastAppTab", name.trim().ifBlank { "DECK" })
            .apply()

    /** Last Settings workspace tab so returning to Settings reopens the user's exact area. */
    fun lastSettingsTab(): String = prefs.getString("lastSettingsTab", "GENERAL")
        ?.trim()
        .orEmpty()
        .ifBlank { "GENERAL" }

    fun setLastSettingsTab(name: String) =
        prefs.edit()
            .putString("lastSettingsTab", name.trim().ifBlank { "GENERAL" })
            .apply()

    /**
     * Last opened Settings page (sub-page or workspace key). Returning to the Settings tab reopens
     * the exact page the user was on instead of dumping them back at the hub — this is the
     * Theme-page-after-back-navigation fix.
     */
    fun lastSettingsPage(): String = prefs.getString("lastSettingsPage", "hub")
        ?.trim()
        .orEmpty()
        .ifBlank { "hub" }

    fun setLastSettingsPage(name: String) =
        prefs.edit()
            .putString("lastSettingsPage", name.trim().ifBlank { "hub" })
            .apply()

    /**
     * v8.1 migration: existing installs already have old proxy-all/ads-off preferences persisted,
     * so changing AppSettings constructor defaults alone would not activate the new policy.
     * Apply the new Iran baseline exactly once while preserving user custom block/proxy lists.
     *
     * v2 (MARBLE_SMART_FAMILY_V136 / MARBLE_ROUTING_ENGINE_V136): the routing rules list is now
     * the single source of truth, so v1 installs receive the recommended rule set **once**, and
     * the smart address-family policy makes IPv6-on safe — both family switches are enabled once;
     * any later user choice persists because the schema only ever advances.
     */
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
            // The v1 baseline forced IPv6 off. The underlay gate + per-node measurement in
            // AddressFamilyPolicy make IPv6-on safe on every link now, so both switches turn on
            // exactly once; a user who turns them off afterwards keeps that choice.
            prefs.edit()
                .putString("addressFamilyMode", AddressFamilyMode.SMART.name)
                .putBoolean("ipv6Enabled", true)
                .putBoolean("preferIpv6", true)
                .apply()
        }

        prefs.edit()
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
        val familyMode = if (prefs.contains("addressFamilyMode")) {
            enumValue("addressFamilyMode", AddressFamilyMode.SMART)
        } else {
            // Lossless one-time interpretation of installations created before the five-state
            // selector. A disabled v6 switch was an IPv4 demand; otherwise preserve its ordering.
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
        probeSpeedTest = prefs.getBoolean("probeSpeedTest", false),

        // MARBLE_PATTNG_PING_V151 / MARBLE_SINGBOX_CORE_V151
        delayTestUrl = DelayTest.url(prefs.getString("delayTestUrl", DelayTest.URL) ?: DelayTest.URL),
        coreEngineId = parseCoreEngine(prefs.getString("coreEngineId", CoreEngine.XRAY.id) ?: CoreEngine.XRAY.id).id,
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

        // MARBLE_PING_CONTROL_V145 — the user-owned ping budget, clamped on the way in so a
        // hand-edited or migrated preference can never hand the engine an illegal budget.
        pingTimeoutSec = PingBudget.timeoutSec(prefs.getInt("pingTimeoutSec", 5)),
        pingSamples = PingBudget.samples(prefs.getInt("pingSamples", 3)),
        pingConcurrency = PingBudget.concurrency(prefs.getInt("pingConcurrency", 16)),

        // MARBLE_PING_SPEED_DIAL_V199 — the speed dial: off by default (the shipped ~1.5×
        // baseline), and the percent clamped to the dial's legal range on the way in.
        pingSpeedCustom = prefs.getBoolean("pingSpeedCustom", false),
        pingSpeedPercent = PingSpeed.percent(prefs.getInt("pingSpeedPercent", PingSpeed.DEFAULT_PERCENT)),

        // MARBLE_PING_PARALLEL_V200 — the width is the device's by default. An install that
        // already touched the chips (any value other than the old 16 default) keeps Manual, so
        // upgrading never silently discards a number a user chose on purpose.
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

        // MARBLE_SERVERS_QUERY_V120 — the Servers filter bar survives a restart.
        serversProtocolFilter = prefs.getString("serversProtocolFilter", "")?.trim().orEmpty(),
        serversOnlyReachable = prefs.getBoolean("serversOnlyReachable", false),
        serversMaxPingMs = prefs.getInt("serversMaxPingMs", 0).coerceAtLeast(0),
        serversGroupByCountry = prefs.getBoolean("serversGroupByCountry", false),
        // MARBLE_SERVER_TILE_LAYOUT_V208 — rows are the shipped default, so an install that
        // never chose opens on rows and an install that chose tiles keeps them.
        serversLayout = prefs.getString("serversLayout", ServerLayout.DEFAULT.id)
            ?: ServerLayout.DEFAULT.id,

        rememberLast = prefs.getBoolean("rememberLast", true),
        subscriptionAutoRefresh = prefs.getBoolean("subscriptionAutoRefresh", true),
        // MARBLE_APP_UPDATE_STORE_V102
        appUpdateCheckEnabled = prefs.getBoolean("appUpdateCheckEnabled", true),
        subscriptionRefreshHours = prefs.getInt("subscriptionRefreshHours", 12),
        homeShowSummaryMetrics = prefs.getBoolean("homeShowSummaryMetrics", false),
        homeShowIranMode = prefs.getBoolean("homeShowIranMode", false),
        homeShowQuickActions = prefs.getBoolean("homeShowQuickActions", true),
        homeShowLiveQuality = prefs.getBoolean("homeShowLiveQuality", true),
        homeShowServerSelector = prefs.getBoolean("homeShowServerSelector", true),
        homeShowRouteDetails = prefs.getBoolean("homeShowRouteDetails", true),
        homeShowRouteRibbon = prefs.getBoolean("homeShowRouteRibbon", true),
        homeSpeedWidgetEnabled = prefs.getBoolean("homeSpeedWidgetEnabled", false),
        autoConnectBestAfterScan = prefs.getBoolean("autoConnectBestAfterScan", false),
        serverIntelEnabled = prefs.getBoolean("serverIntelEnabled", true),
        // MARBLE_SERVER_LOCATION_V192 / MARBLE_SESSION_USAGE_V192
        serverLocationAutoDetect = prefs.getBoolean("serverLocationAutoDetect", true),
        homeShowDataUsage = prefs.getBoolean("homeShowDataUsage", false),
        // MARBLE_ROUTE_ATELIER_V207 — the ambient field, on by default, off by preference.
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

        routingMode = enumValue("routingMode", RoutingMode.GEO_DIRECT),
        customRoutingEnabled = prefs.getBoolean("customRoutingEnabled", false),
        geoAssetSourceId = prefs.getString("geoAssetSourceId", RoutingDefaults.SOURCE_CHOCOLATE4U)
            ?: RoutingDefaults.SOURCE_CHOCOLATE4U,
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
        // Five explicit modes replace the ambiguous pair of family switches. The compatibility
        // booleans are projected from that mode so every existing TUN/routing consumer agrees.
        addressFamilyMode = familyMode,
        ipv6Enabled = familyMode != AddressFamilyMode.FORCE_IPV4,
        preferIpv6 = familyMode == AddressFamilyMode.PREFER_IPV6 ||
            familyMode == AddressFamilyMode.FORCE_IPV6 || familyMode == AddressFamilyMode.SMART,
        // MARBLE_IPV6_FALLBACK_LADDER_V196 — a forced family degrades along a ladder by default;
        // only an explicit opt-in lets it refuse to connect.
        strictAddressFamily = prefs.getBoolean("strictAddressFamily", false),
        // MARBLE_REALTIME_ENGINE_V70
        adaptiveHappyEyeballsEnabled = prefs.getBoolean("adaptiveHappyEyeballsEnabled", true),
        happyEyeballsTryDelayMs = prefs.getInt("happyEyeballsTryDelayMs", 60).coerceIn(0, 500),
        happyEyeballsMaxConcurrent = prefs.getInt("happyEyeballsMaxConcurrent", 4).coerceIn(2, 8),
        adaptiveTcpFastOpenEnabled = prefs.getBoolean("adaptiveTcpFastOpenEnabled", true),
        tcpFastOpenEnabled = prefs.getBoolean("tcpFastOpenEnabled", false),
        adaptiveMssEnabled = prefs.getBoolean("adaptiveMssEnabled", true),
        tcpMaxSeg = prefs.getInt("tcpMaxSeg", 0).coerceIn(0, 9000),

        // doh.sb is intentionally absent: its addresses are not stable enough to pin in Xray


        muxEnabled = prefs.getBoolean("muxEnabled", false),
        muxConcurrency = prefs.getInt("muxConcurrency", 8).coerceIn(1, 128),
        muxXudpConcurrency = prefs.getInt("muxXudpConcurrency", 16).coerceIn(1, 128),
        muxUdp443 = XrayMuxUdp443Modes.parse(prefs.getString("muxUdp443", "skip") ?: "skip"),

        // MARBLE_CORE_OPTIONS_V211 — every reader below is a *value the user set*, validated
        // against the vocabulary its own core accepts. Nothing here is a policy knob: the
        // automatic layers no longer rewrite a core option, so what is stored is what is written
        // into the config.
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
        xrayRoutingDomainStrategy = XrayRoutingDomainStrategies.parse(
            prefs.getString("xrayRoutingDomainStrategy", "AsIs") ?: "AsIs"
        ),
        xrayRoutingDomainMatcher = XrayDomainMatchers.parse(
            prefs.getString("xrayRoutingDomainMatcher", "") ?: ""
        ),
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
        iranDeepProbeEnabled = prefs.getBoolean("iranDeepProbeEnabled", true),
        iranModeNotify = false,

        intelligenceEnabled = true,
        configCompatibilityMode = prefs.getBoolean("configCompatibilityMode", true),
        // MARBLE_CORE_CONFIG_SUPERSET_V165 — on by default: the product's own core carries the
        // matching policy patch, and refusing a whole subscription is not a safety feature.
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
        // MARBLE_FAKE_IP_V184 — default true: an upgrade must get the fake-IP cold-DNS fix
        // without any user action; the DNS page carries the explicit off switch.
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
        // iOS-styled Home presentations: IOS_SLIDER, IOS_FLOATING, IOS_EMBOSSED, IOS_MODULAR
        homeStyle = storedHomeStyle().id,
        appLanguage = parseAppLanguage(prefs.getString("appLanguage", AppLanguage.SYSTEM.id) ?: AppLanguage.SYSTEM.id).id,

        // MARBLE_MODULAR_LAYOUT_V145 — a persisted order is repaired on read, so a legacy or
        // truncated value can never hide a Home module (CONNECT included).
        modularCardOrder = ModularLayout.serialize(
            ModularLayout.order(
                prefs.getString("modularCardOrder", ModularLayout.DEFAULT_ORDER)
                    ?: ModularLayout.DEFAULT_ORDER
            )
        ),
        modularShowStats = prefs.getBoolean("modularShowStats", true),
        modularShowSocks = prefs.getBoolean("modularShowSocks", false),
        modularShowShortcuts = prefs.getBoolean("modularShowShortcuts", true),
        modularShowStatus = prefs.getBoolean("modularShowStatus", true),
        modularShowServers = prefs.getBoolean("modularShowServers", true),
        modularConnectStyle = prefs.getString("modularConnectStyle", "SLIDER") ?: "SLIDER",
        modularCardSize = parseModularCardSize(
            prefs.getString("modularCardSize", ModularCardSize.COMPACT.id) ?: ModularCardSize.COMPACT.id
        ).id,
        modularCardHeightDp = prefs.getInt("modularCardHeightDp", 180).coerceIn(160, 360),
        modularHideCustomizerButton = prefs.getBoolean("modularHideCustomizerButton", false),

        // MARBLE_CONNECT_BUTTON_V121
        connectButtonStyle = parseConnectButtonStyle(prefs.getString("connectButtonStyle", ConnectButtonStyle.ROUND.id) ?: ConnectButtonStyle.ROUND.id).id,

        // MARBLE_NIGHT_OUTLINES_V112
        darkOutlineStyle = parseDarkOutlineStyle(prefs.getString("darkOutlineStyle", DarkOutlineStyle.SUBTLE.id) ?: DarkOutlineStyle.SUBTLE.id).id,

        // MARBLE_DOCK_CUSTOM_V145
        dockShowLabels = prefs.getBoolean("dockShowLabels", true),
        dockShowIcons = prefs.getBoolean("dockShowIcons", true),
        dockSize = parseDockSize(prefs.getString("dockSize", DockSize.MEDIUM.id) ?: DockSize.MEDIUM.id).id,

        // MARBLE_DOCK_SLOT_V167 — the fourth tab. Every field is normalised on read, so a value
        // written by an older build (or hand-edited) can never leave the slot blank or undefined.
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

        debugModeEnabled = prefs.getBoolean("debugModeEnabled", false),
        expertMode = prefs.getBoolean("expertMode", false),

        // MARBLE_AUTO_SERVER_SELECTOR_V202 — the selector is opt-in, and the three "when may it
        // act" switches default to the one place a sweep already produced fresh evidence: the end
        // of a ping run. On-connect and on-failure stay off, because moving a route the user did
        // not ask to move is the one thing this must never do by surprise.
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
            .coerceIn(0, 100)
        )
    }

    fun saveSettings(s: AppSettings) = prefs.edit()
        .putInt("socksPort", s.socksPort)
        .putInt("localProxyPort", s.localProxyPort)
        .putString("connectionMode", s.connectionMode.name)

        .putString("probeMethod", s.probeMethod.name)
        // MARBLE_PATTNG_PING_V151 / MARBLE_SINGBOX_CORE_V151
        .putString("delayTestUrl", DelayTest.url(s.delayTestUrl))
        .putString("coreEngineId", parseCoreEngine(s.coreEngineId).id)
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

        // MARBLE_PING_CONTROL_V145
        .putInt("pingTimeoutSec", PingBudget.timeoutSec(s.pingTimeoutSec))
        .putInt("pingSamples", PingBudget.samples(s.pingSamples))

        // MARBLE_PING_SPEED_DIAL_V199 — the dial persists next to the budget it scales.
        .putBoolean("pingSpeedCustom", s.pingSpeedCustom)
        .putInt("pingSpeedPercent", PingSpeed.percent(s.pingSpeedPercent))

        // MARBLE_PING_PARALLEL_V200 — who owns the sweep width. The chip persists either way so
        // switching to Manual and back never loses the number the user had.
        .putString("pingParallelMode", s.pingParallelMode.name)
        .putInt("pingConcurrency", PingBudget.concurrency(s.pingConcurrency))

        .putString("nodeSortMode", s.nodeSortMode.name)
        .putBoolean("nodeSortReverse", s.nodeSortReverse)

        // MARBLE_SERVERS_QUERY_V120
        .putString("serversProtocolFilter", s.serversProtocolFilter.trim().uppercase())
        .putBoolean("serversOnlyReachable", s.serversOnlyReachable)
        .putInt("serversMaxPingMs", s.serversMaxPingMs.coerceAtLeast(0))
        .putBoolean("serversGroupByCountry", s.serversGroupByCountry)
        .putString("serversLayout", s.serversLayout)

        .putBoolean("rememberLast", s.rememberLast)
        .putBoolean("subscriptionAutoRefresh", s.subscriptionAutoRefresh)
        .putBoolean("appUpdateCheckEnabled", s.appUpdateCheckEnabled)
        .putInt("subscriptionRefreshHours", s.subscriptionRefreshHours)
        .putBoolean("homeShowSummaryMetrics", s.homeShowSummaryMetrics)
        .putBoolean("homeShowIranMode", s.homeShowIranMode)
        .putBoolean("homeShowQuickActions", s.homeShowQuickActions)
        .putBoolean("homeShowLiveQuality", s.homeShowLiveQuality)
        .putBoolean("homeShowServerSelector", s.homeShowServerSelector)
        .putBoolean("homeShowRouteDetails", s.homeShowRouteDetails)
        .putBoolean("homeShowRouteRibbon", s.homeShowRouteRibbon)
        .putBoolean("homeSpeedWidgetEnabled", s.homeSpeedWidgetEnabled)
        .putBoolean("autoConnectBestAfterScan", s.autoConnectBestAfterScan)
        .putBoolean("serverIntelEnabled", s.serverIntelEnabled)
        // MARBLE_SERVER_LOCATION_V192 / MARBLE_SESSION_USAGE_V192
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

        .putString("routingMode", s.routingMode.name)
        .putBoolean("customRoutingEnabled", s.customRoutingEnabled)
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

        // MARBLE_CORE_CONFIG_SUPERSET_V165 — the consent gate for a cleartext public node. It was
        // read on every load and never written, so the switch could not survive a restart.
        .putBoolean("allowUnencryptedPublicOutbound", s.allowUnencryptedPublicOutbound)

        // MARBLE_CORE_OPTIONS_V211 — the two cores' own options. Every value is written back
        // exactly as it was set (the readers above are the only place a range is enforced), so an
        // upgrade cannot silently move a number the user chose.
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
        .putString("xrayRoutingDomainStrategy", XrayRoutingDomainStrategies.parse(s.xrayRoutingDomainStrategy))
        .putString("xrayRoutingDomainMatcher", XrayDomainMatchers.parse(s.xrayRoutingDomainMatcher))
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
        .putBoolean("modularShowStatus", s.modularShowStatus)
        .putBoolean("modularShowServers", s.modularShowServers)
        .putString("modularConnectStyle", s.modularConnectStyle)
        .putString("modularCardSize", parseModularCardSize(s.modularCardSize).id)
        .putInt("modularCardHeightDp", s.modularCardHeightDp.coerceIn(160, 360))
        .putBoolean("modularHideCustomizerButton", s.modularHideCustomizerButton)

        // MARBLE_CONNECT_BUTTON_V121
        .putString("connectButtonStyle", parseConnectButtonStyle(s.connectButtonStyle).id)

        // MARBLE_NIGHT_OUTLINES_V112
        .putString("darkOutlineStyle", parseDarkOutlineStyle(s.darkOutlineStyle).id)

        // MARBLE_DOCK_CUSTOM_V145
        .putBoolean("dockShowLabels", s.dockShowLabels)
        .putBoolean("dockShowIcons", s.dockShowIcons)
        .putString("dockSize", parseDockSize(s.dockSize).id)

        // MARBLE_DOCK_SLOT_V167
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
        .putBoolean("expertMode", s.expertMode)

        // MARBLE_AUTO_SERVER_SELECTOR_V202
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

        // MARBLE_TRANSPORT_ADAPTATION_V203
        .apply()

    /**
     * MARBLE_GOOGLE_SANS_DEFAULT_V160 — the typeface of a first launch is Google Sans; an install
     * that already chose one keeps it.
     *
     * The old line passed the default *into* `prefs.getString`, which is the same thing as
     * persisting it: the moment an existing install read its settings, "vazir-on-first-launch"
     * became an explicit stored value and every later change of the default was invisible to it.
     * Reading `null` first keeps absence and choice apart, so this default only ever applies to
     * an app that has never written the key.
     */
    private fun storedFontFamily(): AppFont {
        val raw = prefs.getString("fontFamily", null) ?: return AppFont.DEFAULT
        return AppFont.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
            ?: AppFont.DEFAULT
    }

    /**
     * MARBLE_HOME_THEME_TWO_DEFAULT_V160 — the presentation a first launch opens with is
     * Theme 2; an install that already chose one keeps it.
     *
     * This reads `null` first for exactly the reason [storedFontFamily] does: a default handed
     * to `prefs.getString` is written back the first time settings are read, which turns
     * "whatever the default was on the day you installed" into a permanent explicit choice and
     * makes the default impossible to change afterwards. Absence and choice stay apart here.
     */
    private fun storedHomeStyle(): HomeStyle {
        val raw = prefs.getString("homeStyle", null) ?: return HomeStyle.DEFAULT
        return HomeStyle.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
            ?: HomeStyle.DEFAULT
    }

    /**
     * MARBLE_PATTNG_PING_V151 — the persisted ping method, migrated from the seven-method V148
     * menu.
     *
     * The old names are gone from the enum, so `enumValueOf` would throw and every upgraded
     * install would silently reset to the default. Mapping them keeps the user's intent: a
     * "Real test" user stays on the real measurement, a "TCP Connect" user stays on the raw
     * handshake, and the four estimates collapse onto the honest default.
     */
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
