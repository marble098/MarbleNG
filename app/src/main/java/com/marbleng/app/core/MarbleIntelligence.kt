package com.marbleng.app.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.PowerManager
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.SplitTunnelMode
import com.marbleng.app.model.WorkloadProfile
import java.io.Closeable
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Network state MarbleNG can observe without privileged/root-only APIs.
 * No SSID, IMSI or other user identifiers are persisted in the fingerprint.
 */
data class NetworkSnapshot(
    val transport: String = "unknown",
    val validated: Boolean = false,
    val metered: Boolean = false,
    val hasIpv4: Boolean = false,
    val hasIpv6: Boolean = false,
    val mtu: Int = 0,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val dualNetworkAvailable: Boolean = false,
    val at: Long = System.currentTimeMillis()
) {
    fun key(): String {
        fun bucket(v: Int): Int = when {
            v <= 0 -> 0
            v < 10_000 -> 1
            v < 50_000 -> 2
            v < 200_000 -> 3
            else -> 4
        }
        return listOf(
            transport,
            if (hasIpv4) "v4" else "no4",
            if (hasIpv6) "v6" else "no6",
            if (metered) "metered" else "unmetered",
            "m${mtu.coerceAtLeast(0)}",
            "d${bucket(downstreamKbps)}",
            "u${bucket(upstreamKbps)}"
        ).joinToString("|")
    }

    val label: String
        get() = buildString {
            append(transport.uppercase())
            append(" • ")
            append(
                when {
                    hasIpv4 && hasIpv6 -> "DUAL"
                    hasIpv6 -> "IPv6"
                    hasIpv4 -> "IPv4"
                    else -> "NO IP"
                }
            )
            if (mtu > 0) append(" • MTU $mtu")
        }
}

data class NodeHealthRecord(
    val profileId: String,
    val networkKey: String,
    val samples: Int = 0,
    val successEwma: Double = 0.0,
    val latencyEwma: Double = 9999.0,
    val jitterEwma: Double = 9999.0,
    val throughputEwma: Double = 0.0,
    val udpEwma: Double = 0.0,
    val connectMsEwma: Double = 0.0,
    val failureStreak: Int = 0,
    val preferredFragment: Boolean = false,
    val preferredMux: Boolean = false,
    val lastSuccessAt: Long = 0L,
    val lastSeenAt: Long = 0L
)

data class PrivacySentinelState(
    val coverage: String = "OFFLINE",
    val tunnelRoutes: Boolean = false,
    val ipv4Captured: Boolean = false,
    val ipv6Captured: Boolean = false,
    val dnsHijack: Boolean = false,
    val encryptedDns: Boolean = false,
    val systemDnsFallbackBlocked: Boolean = false,
    val killSwitchArmed: Boolean = false,
    val splitBypassCount: Int = 0,
    val xrayAlive: Boolean = false,
    val hevAlive: Boolean = false,
    val exitIp: String = "",
    val dnsObservation: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val healthy: Boolean
        get() = coverage == "DEVICE-WIDE" && tunnelRoutes && ipv4Captured && dnsHijack &&
            encryptedDns && systemDnsFallbackBlocked && xrayAlive && hevAlive
}

data class IntelligenceStatus(
    val networkLabel: String = "UNKNOWN",
    val networkKey: String = "unknown",
    val physicalMtu: Int = 0,
    val effectiveMtu: Int = 0,
    val thermalBudgetPercent: Int = 100,
    val powerSaveMode: Boolean = false,
    val dualNetworkAvailable: Boolean = false,
    val kernelSuDetected: Boolean = false,
    val ebpfCapable: Boolean = false,
    val historyRecords: Int = 0,
    val lastDecision: String = "Waiting for network intelligence"
)

/**
 * Persistent, network-scoped health store. SQLite keeps the hot path dependency-free and bounded.
 */
private class HealthDb(context: Context) : SQLiteOpenHelper(context, "marble-intelligence.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS node_health(
                profile_id TEXT NOT NULL,
                network_key TEXT NOT NULL,
                samples INTEGER NOT NULL DEFAULT 0,
                success_ewma REAL NOT NULL DEFAULT 0,
                latency_ewma REAL NOT NULL DEFAULT 9999,
                jitter_ewma REAL NOT NULL DEFAULT 9999,
                throughput_ewma REAL NOT NULL DEFAULT 0,
                udp_ewma REAL NOT NULL DEFAULT 0,
                connect_ms_ewma REAL NOT NULL DEFAULT 0,
                failure_streak INTEGER NOT NULL DEFAULT 0,
                preferred_fragment INTEGER NOT NULL DEFAULT 0,
                preferred_mux INTEGER NOT NULL DEFAULT 0,
                last_success_at INTEGER NOT NULL DEFAULT 0,
                last_seen_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(profile_id, network_key)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_network ON node_health(network_key, last_seen_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun get(profileId: String, networkKey: String): NodeHealthRecord? {
        readableDatabase.query(
            "node_health",
            null,
            "profile_id=? AND network_key=?",
            arrayOf(profileId, networkKey),
            null,
            null,
            null,
            "1"
        ).use { c ->
            if (!c.moveToFirst()) return null
            fun i(name: String) = c.getColumnIndexOrThrow(name)
            return NodeHealthRecord(
                profileId = c.getString(i("profile_id")),
                networkKey = c.getString(i("network_key")),
                samples = c.getInt(i("samples")),
                successEwma = c.getDouble(i("success_ewma")),
                latencyEwma = c.getDouble(i("latency_ewma")),
                jitterEwma = c.getDouble(i("jitter_ewma")),
                throughputEwma = c.getDouble(i("throughput_ewma")),
                udpEwma = c.getDouble(i("udp_ewma")),
                connectMsEwma = c.getDouble(i("connect_ms_ewma")),
                failureStreak = c.getInt(i("failure_streak")),
                preferredFragment = c.getInt(i("preferred_fragment")) != 0,
                preferredMux = c.getInt(i("preferred_mux")) != 0,
                lastSuccessAt = c.getLong(i("last_success_at")),
                lastSeenAt = c.getLong(i("last_seen_at"))
            )
        }
    }

    @Synchronized
    fun count(networkKey: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM node_health WHERE network_key=?",
            arrayOf(networkKey)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    @Synchronized
    fun recordBenchmark(profileId: String, networkKey: String, result: BenchmarkResult) {
        val old = get(profileId, networkKey)
        val n = (old?.samples ?: 0) + 1
        val alpha = when {
            n <= 2 -> 0.65
            n <= 8 -> 0.35
            else -> 0.20
        }
        fun ewma(previous: Double?, current: Double, fallback: Double): Double {
            val p = previous?.takeIf { it.isFinite() } ?: fallback
            return if (n <= 1 || p == fallback) current else p * (1.0 - alpha) + current * alpha
        }
        val now = System.currentTimeMillis()
        val success = result.success.coerceIn(0, 100).toDouble()
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("network_key", networkKey)
            put("samples", n)
            put("success_ewma", ewma(old?.successEwma, success, 0.0))
            put("latency_ewma", ewma(old?.latencyEwma, result.latencyMs.coerceAtMost(10_000.0), 9999.0))
            put("jitter_ewma", ewma(old?.jitterEwma, result.jitterMs.coerceAtMost(10_000.0), 9999.0))
            put("throughput_ewma", ewma(old?.throughputEwma, max(0.0, result.bytesPerSecond), 0.0))
            put("udp_ewma", ewma(old?.udpEwma, result.udpSuccess.toDouble(), 0.0))
            put("connect_ms_ewma", old?.connectMsEwma ?: 0.0)
            put("failure_streak", if (result.success > 0) 0 else (old?.failureStreak ?: 0) + 1)
            put(
                "preferred_fragment",
                when {
                    result.usedFragment -> 1
                    result.success >= 75 -> 0
                    old?.preferredFragment == true -> 1
                    else -> 0
                }
            )
            put("preferred_mux", if (result.usedMux) 1 else if (old?.preferredMux == true) 1 else 0)
            put("last_success_at", if (result.success > 0) now else old?.lastSuccessAt ?: 0L)
            put("last_seen_at", now)
        }
        writableDatabase.insertWithOnConflict("node_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        trim(networkKey)
    }

    @Synchronized
    fun recordConnect(profileId: String, networkKey: String, success: Boolean, connectMs: Long) {
        val old = get(profileId, networkKey)
        val now = System.currentTimeMillis()
        val n = max(1, old?.samples ?: 0)
        val alpha = if (n < 5) 0.4 else 0.2
        val previous = old?.connectMsEwma?.takeIf { it > 0 } ?: connectMs.toDouble()
        val connectEwma = previous * (1.0 - alpha) + connectMs.coerceAtLeast(0).toDouble() * alpha
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("network_key", networkKey)
            put("samples", n)
            put("success_ewma", old?.successEwma ?: if (success) 100.0 else 0.0)
            put("latency_ewma", old?.latencyEwma ?: 9999.0)
            put("jitter_ewma", old?.jitterEwma ?: 9999.0)
            put("throughput_ewma", old?.throughputEwma ?: 0.0)
            put("udp_ewma", old?.udpEwma ?: 0.0)
            put("connect_ms_ewma", connectEwma)
            put("failure_streak", if (success) 0 else (old?.failureStreak ?: 0) + 1)
            put("preferred_fragment", if (old?.preferredFragment == true) 1 else 0)
            put("preferred_mux", if (old?.preferredMux == true) 1 else 0)
            put("last_success_at", if (success) now else old?.lastSuccessAt ?: 0L)
            put("last_seen_at", now)
        }
        writableDatabase.insertWithOnConflict("node_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun recordRoute(profileId: String, networkKey: String, latencyMs: Int, throughputBps: Long) {
        val old = get(profileId, networkKey) ?: return
        val alpha = 0.12
        val latency = old.latencyEwma * (1.0 - alpha) + latencyMs.coerceAtLeast(1) * alpha
        val throughput = old.throughputEwma * (1.0 - alpha) + throughputBps.coerceAtLeast(0) * alpha
        val values = ContentValues().apply {
            put("latency_ewma", latency)
            put("throughput_ewma", throughput)
            put("failure_streak", 0)
            put("last_success_at", System.currentTimeMillis())
            put("last_seen_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "node_health",
            values,
            "profile_id=? AND network_key=?",
            arrayOf(profileId, networkKey)
        )
    }

    @Synchronized
    fun recordFailure(profileId: String, networkKey: String) {
        val old = get(profileId, networkKey)
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("network_key", networkKey)
            put("samples", max(1, old?.samples ?: 0))
            put("success_ewma", (old?.successEwma ?: 50.0) * 0.78)
            put("latency_ewma", old?.latencyEwma ?: 9999.0)
            put("jitter_ewma", old?.jitterEwma ?: 9999.0)
            put("throughput_ewma", old?.throughputEwma ?: 0.0)
            put("udp_ewma", old?.udpEwma ?: 0.0)
            put("connect_ms_ewma", old?.connectMsEwma ?: 0.0)
            put("failure_streak", (old?.failureStreak ?: 0) + 1)
            put("preferred_fragment", if (old?.preferredFragment == true) 1 else 0)
            put("preferred_mux", if (old?.preferredMux == true) 1 else 0)
            put("last_success_at", old?.lastSuccessAt ?: 0L)
            put("last_seen_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("node_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun trim(networkKey: String) {
        writableDatabase.execSQL(
            "DELETE FROM node_health WHERE network_key=? AND rowid NOT IN " +
                "(SELECT rowid FROM node_health WHERE network_key=? ORDER BY last_seen_at DESC LIMIT 600)",
            arrayOf(networkKey, networkKey)
        )
    }
}

/**
 * MarbleNG's policy brain. It intentionally does not claim KernelSU/eBPF or bandwidth bonding:
 * those are surfaced as detected capabilities only until a real datapath backend exists.
 */
class MarbleIntelligence(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val db = HealthDb(context)
    private val prefs = context.getSharedPreferences("marble-intelligence", Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArrayList<(NetworkSnapshot) -> Unit>()
    private val availableTransports = ConcurrentHashMap<Network, Set<String>>()

    @Volatile private var started = false
    @Volatile private var activeNetwork: Network? = null
    @Volatile private var snapshot = NetworkSnapshot()
    @Volatile private var effectiveMtu = 0
    @Volatile private var lastDecision = "Waiting for network intelligence"

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network)
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh(network)
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh(network)
        override fun onLost(network: Network) {
            availableTransports.remove(network)
            if (activeNetwork == network) refresh(connectivity.activeNetwork)
            else notifyCurrentDualState()
        }
    }

    @Synchronized
    fun startMonitoring() {
        if (started) return
        started = true
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity.registerNetworkCallback(request, callback)
        }
        refresh(connectivity.activeNetwork)
    }

    fun addNetworkListener(listener: (NetworkSnapshot) -> Unit): Closeable {
        startMonitoring()
        listeners += listener
        listener(snapshot)
        return Closeable { listeners.remove(listener) }
    }

    fun currentSnapshot(): NetworkSnapshot {
        startMonitoring()
        return snapshot
    }

    fun currentUnderlyingNetwork(): Network? {
        startMonitoring()
        return activeNetwork
    }

    fun underlyingNetworks(): List<Network> = currentUnderlyingNetwork()?.let(::listOf) ?: emptyList()

    private fun refresh(candidate: Network?) {
        val network = candidate ?: connectivity.activeNetwork
        val caps = network?.let(connectivity::getNetworkCapabilities)
        val lp = network?.let(connectivity::getLinkProperties)
        if (caps == null) {
            val next = NetworkSnapshot()
            snapshot = next
            activeNetwork = network
            listeners.forEach { runCatching { it(next) } }
            return
        }

        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
        }
        if (network != null) availableTransports[network] = transports.toSet()
        val addresses = lp?.linkAddresses.orEmpty().map { it.address }
        val allTransports = availableTransports.values.flatten().toSet()
        val dual = "wifi" in allTransports && "cellular" in allTransports
        val next = NetworkSnapshot(
            transport = transports.firstOrNull() ?: "other",
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            hasIpv4 = addresses.any { it.address.size == 4 },
            hasIpv6 = addresses.any { it.address.size == 16 && !it.isLinkLocalAddress },
            mtu = lp?.mtu ?: 0,
            downstreamKbps = caps.linkDownstreamBandwidthKbps.coerceAtLeast(0),
            upstreamKbps = caps.linkUpstreamBandwidthKbps.coerceAtLeast(0),
            dualNetworkAvailable = dual
        )
        activeNetwork = network
        snapshot = next
        listeners.forEach { runCatching { it(next) } }
    }

    private fun notifyCurrentDualState() {
        val allTransports = availableTransports.values.flatten().toSet()
        val dual = "wifi" in allTransports && "cellular" in allTransports
        if (snapshot.dualNetworkAvailable != dual) {
            snapshot = snapshot.copy(dualNetworkAvailable = dual, at = System.currentTimeMillis())
            listeners.forEach { runCatching { it(snapshot) } }
        }
    }

    fun adaptiveMtu(profile: ProxyProfile, settings: AppSettings): Int {
        val n = currentSnapshot()
        if (!settings.adaptiveMtuEnabled) {
            effectiveMtu = settings.mtuMax.coerceIn(1280, 9000)
            return effectiveMtu
        }
        val physical = n.mtu.takeIf { it in 1280..9000 } ?: 1500
        var chosen = min(physical, settings.mtuMax.coerceIn(1280, 9000))
        if (n.transport == "cellular") chosen = min(chosen, 1420)
        if (profile.scheme.equals("hysteria2", true) || profile.transport.contains("kcp", true)) {
            chosen = min(chosen, 1380)
        }
        chosen = chosen.coerceAtLeast(settings.mtuMin.coerceIn(1280, 1500))
        effectiveMtu = chosen
        return chosen
    }

    fun effectiveSettings(profile: ProxyProfile, base: AppSettings): AppSettings {
        startMonitoring()
        val n = snapshot
        val health = if (base.healthHistoryEnabled) db.get(profile.id, n.key()) else null
        val queryStrategy = if (!base.adaptiveDualStackEnabled) {
            base.dnsQueryStrategy
        } else {
            when {
                n.hasIpv4 && !n.hasIpv6 -> "UseIPv4"
                n.hasIpv6 && !n.hasIpv4 -> "UseIPv6"
                else -> "UseIP"
            }
        }

        val canFragment = profile.security.contains("tls", true) || profile.security.contains("reality", true)
        val autoFragment = base.adaptiveFragmentEnabled && canFragment &&
            (health?.preferredFragment == true || (health?.failureStreak ?: 0) >= 2)

        val muxEligible = profile.scheme.lowercase() in setOf("vless", "vmess", "trojan", "ss") &&
            !profile.transport.contains("hysteria", true)
        val autoMux = base.adaptiveMuxEnabled && muxEligible && health != null &&
            health.successEwma >= 80.0 && health.latencyEwma >= 160.0 &&
            health.throughputEwma < 12.0 * 1024.0 * 1024.0 &&
            (health.preferredMux || health.samples >= 4)

        val dnsOrdered = preferredDnsOrder(base)
        return base.copy(
            dnsQueryStrategy = queryStrategy,
            dnsPrimaryDoH = dnsOrdered.first,
            dnsSecondaryDoH = dnsOrdered.second,
            fragmentEnabled = base.fragmentEnabled || autoFragment,
            muxEnabled = base.muxEnabled || autoMux
        )
    }

    fun predictedScore(profile: ProxyProfile, settings: AppSettings): Double {
        if (!settings.intelligenceEnabled || !settings.healthHistoryEnabled) return 50.0
        val h = db.get(profile.id, currentSnapshot().key()) ?: return 50.0
        val latency = 100.0 * exp(-h.latencyEwma.coerceAtMost(5000.0) / 260.0)
        val jitter = 100.0 * exp(-h.jitterEwma.coerceAtMost(3000.0) / 120.0)
        val speed = (ln(1.0 + h.throughputEwma / 131072.0) / ln(1.0 + 128.0)).coerceIn(0.0, 1.0) * 100.0
        val freshness = if (h.lastSuccessAt <= 0) 0.7 else {
            val ageHours = (System.currentTimeMillis() - h.lastSuccessAt).coerceAtLeast(0) / 3_600_000.0
            exp(-ageHours / 72.0).coerceIn(0.35, 1.0)
        }
        val base = h.successEwma * 0.42 + latency * 0.23 + jitter * 0.12 + speed * 0.13 + h.udpEwma * 0.10
        return (base * freshness - h.failureStreak * 8.0).coerceIn(0.0, 100.0)
    }

    fun orderCandidates(profiles: List<ProxyProfile>, settings: AppSettings): List<ProxyProfile> {
        if (!settings.intelligenceEnabled) return profiles
        return profiles.sortedWith(
            compareByDescending<ProxyProfile> { predictedScore(it, settings) }
                .thenByDescending { db.get(it.id, currentSnapshot().key())?.lastSuccessAt ?: 0L }
                .thenBy { it.name }
        )
    }

    fun recoveryCandidates(
        profiles: List<ProxyProfile>,
        failedIds: Set<String>,
        settings: AppSettings
    ): List<ProxyProfile> = orderCandidates(profiles, settings)
        .filterNot { it.id in failedIds }
        .take(settings.fallbackCount.coerceIn(1, 8))

    fun recordBenchmark(profile: ProxyProfile, result: BenchmarkResult, settings: AppSettings) {
        if (!settings.healthHistoryEnabled) return
        db.recordBenchmark(profile.id, currentSnapshot().key(), result)
    }

    fun recordConnect(profileId: String, success: Boolean, connectMs: Long, settings: AppSettings) {
        if (!settings.healthHistoryEnabled || profileId.isBlank()) return
        db.recordConnect(profileId, currentSnapshot().key(), success, connectMs)
    }

    fun recordFailure(profileId: String, settings: AppSettings) {
        if (!settings.healthHistoryEnabled || profileId.isBlank()) return
        db.recordFailure(profileId, currentSnapshot().key())
    }

    fun recordRoute(profileId: String, latencyMs: Int, throughputBps: Long, settings: AppSettings) {
        if (!settings.healthHistoryEnabled || profileId.isBlank() || latencyMs <= 0) return
        db.recordRoute(profileId, currentSnapshot().key(), latencyMs, throughputBps)
    }

    fun thermalBudget(settings: AppSettings): Double {
        if (!settings.thermalAwareEnabled) return 1.0
        var factor = if (power.isPowerSaveMode) 0.72 else 1.0
        if (Build.VERSION.SDK_INT >= 29) {
            factor = min(
                factor,
                when (power.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> 0.20
                    PowerManager.THERMAL_STATUS_CRITICAL -> 0.28
                    PowerManager.THERMAL_STATUS_SEVERE -> 0.40
                    PowerManager.THERMAL_STATUS_MODERATE -> 0.62
                    PowerManager.THERMAL_STATUS_LIGHT -> 0.82
                    else -> 1.0
                }
            )
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val headroom = runCatching { power.getThermalHeadroom(10) }.getOrDefault(Float.NaN)
            if (headroom.isFinite()) {
                factor = min(
                    factor,
                    when {
                        headroom >= 1.0f -> 0.42
                        headroom >= 0.85f -> 0.62
                        headroom >= 0.70f -> 0.80
                        else -> 1.0
                    }
                )
            }
        }
        return factor.coerceIn(0.20, 1.0)
    }

    fun status(settings: AppSettings): IntelligenceStatus {
        val n = currentSnapshot()
        return IntelligenceStatus(
            networkLabel = n.label,
            networkKey = n.key(),
            physicalMtu = n.mtu,
            effectiveMtu = effectiveMtu,
            thermalBudgetPercent = (thermalBudget(settings) * 100.0).toInt().coerceIn(0, 100),
            powerSaveMode = power.isPowerSaveMode,
            dualNetworkAvailable = n.dualNetworkAvailable,
            kernelSuDetected = kernelSuDetected(),
            ebpfCapable = ebpfCapable(),
            historyRecords = if (settings.healthHistoryEnabled) db.count(n.key()) else 0,
            lastDecision = lastDecision
        )
    }

    fun setDecision(text: String) {
        lastDecision = text.take(180)
    }

    fun buildSentinel(
        settings: AppSettings,
        mode: String,
        tunUp: Boolean,
        xrayUp: Boolean,
        hevUp: Boolean,
        killSwitchArmed: Boolean,
        previous: PrivacySentinelState = PrivacySentinelState()
    ): PrivacySentinelState {
        val n = currentSnapshot()
        val selected = settings.splitTunnelPackages
            .split(',', '\n', '\r', ';')
            .map(String::trim)
            .count { it.isNotBlank() }
        val bypassCount = when (settings.splitTunnelMode) {
            SplitTunnelMode.BYPASS_SELECTED -> selected
            SplitTunnelMode.ONLY_SELECTED -> -1 // unknown number of intentionally bypassed apps
            SplitTunnelMode.ALL_APPS -> 0
        }
        val fullTun = mode == "tun"
        return previous.copy(
            coverage = if (fullTun) {
                if (settings.splitTunnelMode == SplitTunnelMode.ALL_APPS) "DEVICE-WIDE" else "PARTIAL"
            } else {
                "PROXY-ONLY"
            },
            tunnelRoutes = fullTun && tunUp,
            ipv4Captured = fullTun && tunUp,
            ipv6Captured = fullTun && tunUp && n.hasIpv6,
            dnsHijack = fullTun && settings.dnsHijackEnabled,
            encryptedDns = settings.dnsPrimaryDoH.startsWith("https://") && settings.dnsSecondaryDoH.startsWith("https://"),
            systemDnsFallbackBlocked = settings.dnsHijackEnabled && settings.dnsPrimaryDoH.startsWith("https://"),
            killSwitchArmed = fullTun && killSwitchArmed,
            splitBypassCount = bypassCount,
            xrayAlive = xrayUp,
            hevAlive = hevUp,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Benchmarks configured DoH endpoints through the already-connected SOCKS path.
     * The probe measures the complete HTTPS handshake/request path; HTTP 4xx is still a valid
     * reachability result because an empty DoH GET is intentionally not a DNS query.
     * Winner ordering is applied on the next connection; current Xray DNS already queries in parallel.
     */
    fun probeDnsResolvers(port: Int, settings: AppSettings) {
        if (!settings.adaptiveDnsEnabled || port !in 1..65535) return
        val candidates = listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH)
            .filter { it.startsWith("https://") }
            .distinct()
        if (candidates.size < 2) return
        val timings = candidates.mapNotNull { raw ->
            runCatching {
                val u = URL(raw)
                val targetPort = u.port.takeIf { it > 0 } ?: 443
                val path = u.file.takeIf { it.isNotBlank() } ?: "/dns-query"
                val p = SocksHttpClient.request(port, u.host, targetPort, "GET", path, null, 4_000, 4096)
                if (p.status <= 0) null else raw to p.elapsedMs
            }.getOrNull()
        }.sortedBy { it.second }
        if (timings.isNotEmpty()) {
            val winner = timings.first().first
            prefs.edit().putString("dns-winner:${currentSnapshot().key()}", winner).apply()
        }
    }

    private fun preferredDnsOrder(settings: AppSettings): Pair<String, String> {
        if (!settings.adaptiveDnsEnabled) return settings.dnsPrimaryDoH to settings.dnsSecondaryDoH
        val winner = prefs.getString("dns-winner:${currentSnapshot().key()}", "").orEmpty()
        return if (winner == settings.dnsSecondaryDoH && winner.isNotBlank()) {
            settings.dnsSecondaryDoH to settings.dnsPrimaryDoH
        } else {
            settings.dnsPrimaryDoH to settings.dnsSecondaryDoH
        }
    }

    private fun kernelSuDetected(): Boolean = listOf(
        "/data/adb/ksu",
        "/data/adb/ksud",
        "/data/adb/ksu/bin/ksud"
    ).any { runCatching { java.io.File(it).exists() && java.io.File(it).canRead() }.getOrDefault(false) }

    private fun ebpfCapable(): Boolean = Build.VERSION.SDK_INT >= 26 && listOf(
        "/sys/fs/bpf",
        "/sys/fs/bpf/netd_shared"
    ).any { runCatching { java.io.File(it).exists() }.getOrDefault(false) }
}

