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
        rememberLast = prefs.getBoolean("rememberLast", true),
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
        .putBoolean("rememberLast", s.rememberLast)
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
