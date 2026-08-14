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
        // Stable history fingerprint. Bandwidth estimates can move every few seconds and must
        // never create a new health bucket while the physical underlay is unchanged.
        val mtuBucket = when {
            mtu <= 0 -> 0
            mtu < 1360 -> 1280
            mtu < 1460 -> 1400
            mtu < 2000 -> 1500
            else -> 9000
        }
        return listOf(
            transport,
            if (hasIpv4) "v4" else "no4",
            if (hasIpv6) "v6" else "no6",
            if (metered) "metered" else "unmetered",
            "m$mtuBucket"
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
            put(
                "preferred_mux",
                when {
                    result.usedMux -> 1
                    result.success >= 75 -> 0
                    old?.preferredMux == true -> 1
                    else -> 0
                }
            )
            put("last_success_at", if (result.success > 0) now else old?.lastSuccessAt ?: 0L)
            put("last_seen_at", now)
        }
        writableDatabase.insertWithOnConflict("node_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        trim(networkKey)
    }

    @Synchronized
    fun recordConnect(
        profileId: String,
        networkKey: String,
        success: Boolean,
        connectMs: Long
    ) {
        val old = get(profileId, networkKey)
        val now = System.currentTimeMillis()
        val n = (old?.samples ?: 0) + 1
        val alpha = when {
            n <= 2 -> 0.55
            n <= 8 -> 0.30
            else -> 0.18
        }
        val previousConnect =
            old?.connectMsEwma?.takeIf { it > 0 } ?: connectMs.toDouble()
        val connectEwma =
            previousConnect * (1.0 - alpha) +
                connectMs.coerceAtLeast(0).toDouble() * alpha
        val successNow = if (success) 100.0 else 0.0
        val successEwma =
            old?.successEwma?.let {
                it * (1.0 - alpha) + successNow * alpha
            } ?: successNow

        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("network_key", networkKey)
            put("samples", n)
            put("success_ewma", successEwma)
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
        writableDatabase.insertWithOnConflict(
            "node_health",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        trim(networkKey)
    }

    @Synchronized
    fun recordRoute(
        profileId: String,
        networkKey: String,
        latencyMs: Int,
        throughputBps: Long
    ) {
        val old = get(profileId, networkKey) ?: return
        val now = System.currentTimeMillis()
        val n = old.samples + 1
        val alpha = if (old.samples <= 2) 0.42 else 0.12
        val currentLatency = latencyMs.coerceAtLeast(1).toDouble()
        val latency =
            if (!old.latencyEwma.isFinite() || old.latencyEwma >= 9000.0) {
                currentLatency
            } else {
                old.latencyEwma * (1.0 - alpha) + currentLatency * alpha
            }

        val currentThroughput = throughputBps.coerceAtLeast(0).toDouble()
        val throughput =
            if (old.throughputEwma <= 0.0 && currentThroughput > 0.0) {
                currentThroughput
            } else {
                old.throughputEwma * (1.0 - alpha) + currentThroughput * alpha
            }

        val values = ContentValues().apply {
            put("samples", n)
            put("success_ewma", (old.successEwma * 0.92 + 8.0).coerceIn(0.0, 100.0))
            put("latency_ewma", latency)
            put("throughput_ewma", throughput)
            put("failure_streak", 0)
            put("last_success_at", now)
            put("last_seen_at", now)
        }
        writableDatabase.update(
            "node_health",
            values,
            "profile_id=? AND network_key=?",
            arrayOf(profileId, networkKey)
        )
        trim(networkKey)
    }

    @Synchronized
    fun recordLiveRoute(
        profileId: String,
        networkKey: String,
        latencyMs: Int,
        jitterMs: Int,
        throughputBps: Long
    ) {
        val old = get(profileId, networkKey) ?: return
        val now = System.currentTimeMillis()
        val n = old.samples + 1
        val alpha = if (old.samples <= 2) 0.42 else 0.12
        val latencyNow = latencyMs.coerceAtLeast(1).toDouble()
        val latency = if (!old.latencyEwma.isFinite() || old.latencyEwma >= 9000.0) latencyNow
            else old.latencyEwma * (1.0 - alpha) + latencyNow * alpha
        val jitterNow = jitterMs.coerceAtLeast(0).toDouble()
        val jitter = if (!old.jitterEwma.isFinite() || old.jitterEwma >= 9000.0) jitterNow
            else old.jitterEwma * (1.0 - alpha) + jitterNow * alpha
        val throughputNow = throughputBps.coerceAtLeast(0).toDouble()
        val throughput = if (old.throughputEwma <= 0.0 && throughputNow > 0.0) throughputNow
            else old.throughputEwma * (1.0 - alpha) + throughputNow * alpha
        val values = ContentValues().apply {
            put("samples", n)
            put("success_ewma", (old.successEwma * 0.92 + 8.0).coerceIn(0.0, 100.0))
            put("latency_ewma", latency)
            put("jitter_ewma", jitter)
            put("throughput_ewma", throughput)
            put("failure_streak", 0)
            put("last_success_at", now)
            put("last_seen_at", now)
        }
        writableDatabase.update(
            "node_health", values,
            "profile_id=? AND network_key=?",
            arrayOf(profileId, networkKey)
        )
        trim(networkKey)
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
    private val connectivity =
        context.getSystemService(ConnectivityManager::class.java)
    private val power =
        context.getSystemService(PowerManager::class.java)
    private val db = HealthDb(context)
    private val prefs =
        context.getSharedPreferences("marble-intelligence", Context.MODE_PRIVATE)
    private val listeners =
        CopyOnWriteArrayList<(NetworkSnapshot) -> Unit>()

    // registerNetworkCallback can report multiple physical networks. Consume callback payloads
    // directly instead of performing race-prone synchronous queries from inside the callback.
    private val capsByNetwork =
        ConcurrentHashMap<Network, NetworkCapabilities>()
    private val linksByNetwork =
        ConcurrentHashMap<Network, LinkProperties>()
    private val availableTransports =
        ConcurrentHashMap<Network, Set<String>>()

    @Volatile private var started = false
    @Volatile private var iranState = IranModeState()
    @Volatile private var iranGeoIpReady = false
    @Volatile private var activeNetwork: Network? = null
    @Volatile private var snapshot = NetworkSnapshot()
    @Volatile private var effectiveMtu = 0
    @Volatile private var lastDecision =
        "Waiting for network intelligence"
    @Volatile private var lastThermalPollAt = 0L
    @Volatile private var cachedThermalFactor = 1.0

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            availableTransports.putIfAbsent(network, emptySet())
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            capsByNetwork[network] = networkCapabilities
            availableTransports[network] = transportsOf(networkCapabilities)
            if (linksByNetwork.containsKey(network)) publishPrimary()
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties
        ) {
            linksByNetwork[network] = linkProperties
            if (capsByNetwork.containsKey(network)) publishPrimary()
        }

        override fun onLost(network: Network) {
            capsByNetwork.remove(network)
            linksByNetwork.remove(network)
            availableTransports.remove(network)
            if (activeNetwork == network) activeNetwork = null
            publishPrimary()
        }
    }

    @Synchronized
    fun startMonitoring() {
        if (started) return
        started = true

        // Seed once outside callback dispatch. Callback methods below do not synchronously query
        // ConnectivityManager for capabilities/link properties.
        seedDefaultPhysicalNetwork()

        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            connectivity.registerNetworkCallback(request, callback)
        }
        publishPrimary()
    }

    fun addNetworkListener(
        listener: (NetworkSnapshot) -> Unit
    ): Closeable {
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

    fun underlyingNetworks(): List<Network> =
        currentUnderlyingNetwork()?.let(::listOf) ?: emptyList()

    private fun seedDefaultPhysicalNetwork() {
        val network = connectivity.activeNetwork ?: return
        val caps =
            runCatching {
                connectivity.getNetworkCapabilities(network)
            }.getOrNull() ?: return

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return

        activeNetwork = network
        capsByNetwork[network] = caps
        availableTransports[network] = transportsOf(caps)
        runCatching {
            connectivity.getLinkProperties(network)
        }.getOrNull()?.let {
            linksByNetwork[network] = it
        }
    }

    private fun transportsOf(
        caps: NetworkCapabilities
    ): Set<String> {
        val out = linkedSetOf<String>()
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            out += "ethernet"
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            out += "wifi"
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            out += "cellular"
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            out += "bluetooth"
        }
        return out
    }

    private fun transportPriority(
        caps: NetworkCapabilities
    ): Int = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 4
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 3
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> 1
        else -> 0
    }

    private fun choosePrimaryPhysical(): Network? {
        // Keep a currently validated underlay sticky so dual Wi-Fi/mobile availability doesn't
        // cause unnecessary tunnel route flapping.
        activeNetwork?.let { sticky ->
            val caps = capsByNetwork[sticky]
            if (
                caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            ) {
                return sticky
            }
        }

        return capsByNetwork.entries.sortedWith(
            compareByDescending<Map.Entry<Network, NetworkCapabilities>> {
                if (
                    it.value.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
                ) 1 else 0
            }.thenByDescending {
                if (
                    it.value.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                    )
                ) 1 else 0
            }.thenByDescending {
                transportPriority(it.value)
            }.thenByDescending {
                it.value.linkDownstreamBandwidthKbps
            }
        ).firstOrNull()?.key
    }

    @Synchronized
    private fun publishPrimary() {
        val network = choosePrimaryPhysical()
        val caps = network?.let { capsByNetwork[it] }
        val lp = network?.let { linksByNetwork[it] }

        val allTransports =
            availableTransports.values.flatten().toSet()
        val dual =
            "wifi" in allTransports && "cellular" in allTransports

        val next =
            if (network == null || caps == null || lp == null) {
                if (network == null) {
                    NetworkSnapshot(dualNetworkAvailable = dual)
                } else {
                    snapshot.copy(
                        dualNetworkAvailable = dual,
                        at = System.currentTimeMillis()
                    )
                }
            } else {
                val transports = transportsOf(caps)
                val addresses = lp.linkAddresses.map { it.address }

                NetworkSnapshot(
                    transport =
                        listOf(
                            "ethernet",
                            "wifi",
                            "cellular",
                            "bluetooth"
                        ).firstOrNull {
                            it in transports
                        } ?: "other",
                    validated =
                        caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_VALIDATED
                        ),
                    metered =
                        !caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                        ),
                    hasIpv4 =
                        addresses.any { it.address.size == 4 },
                    hasIpv6 =
                        addresses.any {
                            it.address.size == 16 &&
                                !it.isLinkLocalAddress
                        },
                    mtu = lp.mtu,
                    downstreamKbps =
                        caps.linkDownstreamBandwidthKbps.coerceAtLeast(0),
                    upstreamKbps =
                        caps.linkUpstreamBandwidthKbps.coerceAtLeast(0),
                    dualNetworkAvailable = dual
                )
            }

        val previous = snapshot
        activeNetwork = network
        snapshot = next

        // Dynamic bandwidth-only changes do not trigger recovery probes.
        val meaningful =
            previous.key() != next.key() ||
                previous.validated != next.validated ||
                previous.dualNetworkAvailable !=
                    next.dualNetworkAvailable

        if (meaningful) {
            listeners.forEach {
                runCatching { it(next) }
            }
        }
    }

    fun adaptiveMtu(
        profile: ProxyProfile,
        settings: AppSettings
    ): Int {
        val n = currentSnapshot()

        if (!settings.adaptiveMtuEnabled) {
            effectiveMtu =
                settings.mtuMax.coerceIn(1280, 9000)
            return effectiveMtu
        }

        val physical =
            n.mtu.takeIf { it in 1280..9000 } ?: 1500
        val configuredCeiling =
            settings.mtuMax.coerceIn(1280, 9000)
        val hardCeiling =
            min(physical, configuredCeiling)

        var chosen = hardCeiling

        if (n.transport == "cellular") {
            chosen = min(chosen, 1420)
        }

        if (
            profile.scheme.equals("hysteria2", true) ||
            profile.transport.contains("kcp", true)
        ) {
            chosen = min(chosen, 1380)
        }

        // A user floor can never push the TUN above the physical/transport ceiling.
        val requestedFloor =
            settings.mtuMin.coerceIn(1280, 1500)
        val safeFloor =
            min(requestedFloor, chosen)

        chosen = chosen.coerceIn(safeFloor, hardCeiling)
        effectiveMtu = chosen
        return chosen
    }

    fun effectiveSettings(
        profile: ProxyProfile,
        base: AppSettings
    ): AppSettings {
        startMonitoring()

        val n = snapshot
        val health =
            if (base.healthHistoryEnabled) {
                db.get(profile.id, n.key())
            } else {
                null
            }

        val queryStrategy =
            if (!base.adaptiveDualStackEnabled) {
                base.dnsQueryStrategy
            } else {
                when {
                    n.hasIpv4 && !n.hasIpv6 -> "UseIPv4"
                    n.hasIpv6 && !n.hasIpv4 -> "UseIPv6"
                    else -> "UseIP"
                }
            }

        val now = System.currentTimeMillis()
        val recentSuccess =
            health?.lastSuccessAt
                ?.takeIf { it > 0L }
                ?.let {
                    now - it <=
                        72L * 60L * 60L * 1000L
                } == true

        val canFragment =
            profile.security.contains("tls", true) ||
                profile.security.contains("reality", true)

        val rescueFragment =
            health != null &&
                recentSuccess &&
                health.failureStreak in 2..3

        val autoFragment =
            base.adaptiveFragmentEnabled &&
                canFragment &&
                (
                    health?.preferredFragment == true ||
                        rescueFragment
                )

        val muxEligible =
            profile.scheme.lowercase() in
                setOf("vless", "vmess", "trojan", "ss") &&
                !profile.transport.contains("hysteria", true)

        val autoMux =
            base.adaptiveMuxEnabled &&
                muxEligible &&
                health != null &&
                health.preferredMux &&
                recentSuccess &&
                health.failureStreak == 0 &&
                health.successEwma >= 80.0 &&
                health.latencyEwma >= 150.0 &&
                health.throughputEwma <
                    12.0 * 1024.0 * 1024.0

        val dnsOrdered = preferredDnsOrder(base)

        val tuned = base.copy(
            dnsQueryStrategy = queryStrategy,
            dnsPrimaryDoH = dnsOrdered.first,
            dnsSecondaryDoH = dnsOrdered.second,
            fragmentEnabled =
                base.fragmentEnabled || autoFragment,
            muxEnabled =
                base.muxEnabled || autoMux
        )

        // Iran Mode is applied last so its countermeasures win over generic adaptive tuning.
        return IranShield.apply(tuned, profile, iranState, iranGeoIpReady)
    }

    fun hasHistory(
        profile: ProxyProfile
    ): Boolean =
        db.get(
            profile.id,
            currentSnapshot().key()
        ) != null

    fun historyConfidence(
        profile: ProxyProfile
    ): Double {
        val h =
            db.get(
                profile.id,
                currentSnapshot().key()
            ) ?: return 0.0

        return (h.samples / 8.0)
            .coerceIn(0.0, 1.0)
    }

    fun predictedScore(
        profile: ProxyProfile,
        settings: AppSettings
    ): Double {
        if (
            !settings.intelligenceEnabled ||
            !settings.healthHistoryEnabled
        ) {
            return 50.0
        }

        val h =
            db.get(
                profile.id,
                currentSnapshot().key()
            ) ?: return 50.0

        fun expScore(
            value: Double,
            scale: Double,
            unknown: Double = 55.0
        ): Double =
            if (
                !value.isFinite() ||
                value >= 9000.0
            ) {
                unknown
            } else {
                100.0 *
                    exp(
                        -value.coerceAtLeast(0.0) /
                            scale
                    )
            }

        val reliability =
            h.successEwma.coerceIn(0.0, 100.0)
        val latency =
            expScore(h.latencyEwma, 250.0)
        val jitter =
            expScore(h.jitterEwma, 115.0)

        val speed =
            if (h.throughputEwma <= 0.0) {
                50.0
            } else {
                (
                    ln(
                        1.0 +
                            h.throughputEwma /
                                131072.0
                    ) /
                        ln(129.0)
                ).coerceIn(0.0, 1.0) *
                    100.0
            }

        val udp =
            if (h.udpEwma <= 0.0) {
                55.0
            } else {
                h.udpEwma.coerceIn(0.0, 100.0)
            }

        val connect =
            if (h.connectMsEwma <= 0.0) {
                55.0
            } else {
                expScore(
                    h.connectMsEwma,
                    1800.0
                )
            }

        val interactive =
            reliability * 0.34 +
                latency * 0.32 +
                jitter * 0.19 +
                connect * 0.15

        val streaming =
            reliability * 0.31 +
                speed * 0.42 +
                jitter * 0.17 +
                latency * 0.10

        val stability =
            reliability * 0.49 +
                jitter * 0.23 +
                latency * 0.16 +
                connect * 0.12

        val resilience =
            reliability * 0.58 +
                udp * 0.16 +
                latency * 0.11 +
                jitter * 0.10 +
                if (h.preferredFragment) 5.0 else 0.0

        val measured =
            when (settings.workloadProfile) {
                WorkloadProfile.INTERACTIVE ->
                    interactive
                WorkloadProfile.STREAMING ->
                    streaming
                WorkloadProfile.STABILITY ->
                    stability
                WorkloadProfile.STEALTH ->
                    resilience
                WorkloadProfile.AUTO ->
                    interactive * 0.30 +
                        streaming * 0.24 +
                        stability * 0.34 +
                        resilience * 0.12
            }

        // Low-sample history is shrunk toward neutral.
        val confidence =
            (h.samples / 8.0)
                .coerceIn(0.12, 1.0)

        val confidenceAdjusted =
            52.0 * (1.0 - confidence) +
                measured * confidence

        val ageHours =
            if (h.lastSuccessAt <= 0L) {
                168.0
            } else {
                (
                    System.currentTimeMillis() -
                        h.lastSuccessAt
                ).coerceAtLeast(0L) /
                    3_600_000.0
            }

        val stalePenalty =
            (
                1.0 -
                    exp(
                        -ageHours / 96.0
                    ).coerceIn(0.20, 1.0)
            ) * 16.0

        val failurePenalty =
            h.failureStreak
                .coerceAtMost(6) *
                7.5

        return (
            confidenceAdjusted -
                stalePenalty -
                failurePenalty
        ).coerceIn(0.0, 100.0)
    }

    /**
     * Latest Iran Mode observation. It biases candidate ordering toward filter-resistant paths and
     * is folded into every effective-settings computation, so benchmarks, races, the autopilot and
     * the live tunnel all run under the same countermeasure policy.
     */
    fun setIranModeState(state: IranModeState, geoIpReady: Boolean) {
        val previous = iranState
        iranState = state
        iranGeoIpReady = geoIpReady

        when {
            state.active && (!previous.active || previous.ispLine != state.ispLine) ->
                setDecision(
                    "Iran underlay integrated • ${state.ispLine} • ${state.confidence}% confidence • " +
                        "tier ${IranShield.tier(state)}"
                )
            previous.active && !state.active ->
                setDecision("Iran Mode returned to standby • physical underlay remains under Marble monitoring")
        }
    }

    fun iranModeState(): IranModeState = iranState

    /**
     * Health prediction plus the active environment bias. While Iran Mode is on, a node's transport
     * shape matters as much as its measured history: a fast node on a filtered transport is not a
     * usable node.
     */
    fun rankingScore(
        profile: ProxyProfile,
        settings: AppSettings
    ): Double = predictedScore(profile, settings) + IranShield.profileBias(profile, iranState)

    fun orderCandidates(
        profiles: List<ProxyProfile>,
        settings: AppSettings
    ): List<ProxyProfile> {
        if (!settings.intelligenceEnabled) {
            return profiles
        }

        val key =
            currentSnapshot().key()

        return profiles.sortedWith(
            compareByDescending<ProxyProfile> {
                rankingScore(
                    it,
                    settings
                )
            }.thenByDescending {
                db.get(
                    it.id,
                    key
                )?.lastSuccessAt ?: 0L
            }.thenBy {
                it.name
            }
        )
    }

    fun recoveryCandidates(
        profiles: List<ProxyProfile>,
        failedIds: Set<String>,
        settings: AppSettings
    ): List<ProxyProfile> {
        val limit =
            settings.fallbackCount
                .coerceIn(1, 8)

        val ordered =
            orderCandidates(
                profiles,
                settings
            ).filterNot {
                it.id in failedIds
            }

        if (ordered.size <= limit) {
            return ordered
        }

        // Prefer different physical hosts first; then fill remaining slots.
        val key =
            currentSnapshot().key()

        val chosen =
            mutableListOf<ProxyProfile>()
        val seenHosts =
            linkedSetOf<String>()

        for (p in ordered) {
            if (chosen.size >= limit) break

            val health =
                db.get(
                    p.id,
                    key
                )

            if (
                (health?.failureStreak ?: 0) >=
                    4
            ) {
                continue
            }

            val hostKey =
                p.host
                    .trim()
                    .lowercase()
                    .ifBlank { p.id }

            if (seenHosts.add(hostKey)) {
                chosen += p
            }
        }

        for (p in ordered) {
            if (chosen.size >= limit) break
            if (
                chosen.none {
                    it.id == p.id
                }
            ) {
                chosen += p
            }
        }

        return chosen
    }

    fun recordBenchmark(
        profile: ProxyProfile,
        result: BenchmarkResult,
        settings: AppSettings
    ) {
        if (!settings.healthHistoryEnabled) return
        db.recordBenchmark(
            profile.id,
            currentSnapshot().key(),
            result
        )
    }

    fun recordConnect(
        profileId: String,
        success: Boolean,
        connectMs: Long,
        settings: AppSettings
    ) {
        if (
            !settings.healthHistoryEnabled ||
            profileId.isBlank()
        ) return

        db.recordConnect(
            profileId,
            currentSnapshot().key(),
            success,
            connectMs
        )
    }

    fun recordFailure(
        profileId: String,
        settings: AppSettings
    ) {
        if (
            !settings.healthHistoryEnabled ||
            profileId.isBlank()
        ) return

        db.recordFailure(
            profileId,
            currentSnapshot().key()
        )
    }

    fun recordLiveRoute(
        profileId: String,
        latencyMs: Int,
        jitterMs: Int,
        throughputBps: Long,
        settings: AppSettings
    ) {
        if (!settings.healthHistoryEnabled || profileId.isBlank() || latencyMs <= 0) return
        db.recordLiveRoute(
            profileId,
            currentSnapshot().key(),
            latencyMs,
            jitterMs,
            throughputBps
        )
    }

    fun recordRoute(
        profileId: String,
        latencyMs: Int,
        throughputBps: Long,
        settings: AppSettings
    ) {
        if (
            !settings.healthHistoryEnabled ||
            profileId.isBlank() ||
            latencyMs <= 0
        ) return

        db.recordRoute(
            profileId,
            currentSnapshot().key(),
            latencyMs,
            throughputBps
        )
    }

    @Synchronized
    fun thermalBudget(
        settings: AppSettings
    ): Double {
        if (!settings.thermalAwareEnabled) {
            return 1.0
        }

        val now =
            System.currentTimeMillis()

        if (
            now - lastThermalPollAt <
            1_500L
        ) {
            return cachedThermalFactor
        }

        var factor =
            if (power.isPowerSaveMode) {
                0.72
            } else {
                1.0
            }

        if (Build.VERSION.SDK_INT >= 29) {
            factor =
                min(
                    factor,
                    when (
                        power.currentThermalStatus
                    ) {
                        PowerManager.THERMAL_STATUS_SHUTDOWN ->
                            0.20
                        PowerManager.THERMAL_STATUS_CRITICAL ->
                            0.28
                        PowerManager.THERMAL_STATUS_SEVERE ->
                            0.40
                        PowerManager.THERMAL_STATUS_MODERATE ->
                            0.62
                        PowerManager.THERMAL_STATUS_LIGHT ->
                            0.82
                        else ->
                            1.0
                    }
                )
        }

        if (Build.VERSION.SDK_INT >= 30) {
            val headroom =
                runCatching {
                    power.getThermalHeadroom(10)
                }.getOrDefault(Float.NaN)

            if (headroom.isFinite()) {
                factor =
                    min(
                        factor,
                        when {
                            headroom >= 1.0f ->
                                0.42
                            headroom >= 0.85f ->
                                0.62
                            headroom >= 0.70f ->
                                0.80
                            else ->
                                1.0
                        }
                    )
            }
        }

        cachedThermalFactor =
            factor.coerceIn(0.20, 1.0)
        lastThermalPollAt = now

        return cachedThermalFactor
    }

    fun status(
        settings: AppSettings
    ): IntelligenceStatus {
        val n =
            currentSnapshot()

        return IntelligenceStatus(
            networkLabel = n.label,
            networkKey = n.key(),
            physicalMtu = n.mtu,
            effectiveMtu = effectiveMtu,
            thermalBudgetPercent =
                (
                    thermalBudget(settings) *
                        100.0
                ).toInt().coerceIn(0, 100),
            powerSaveMode =
                power.isPowerSaveMode,
            dualNetworkAvailable =
                n.dualNetworkAvailable,
            kernelSuDetected =
                kernelSuDetected(),
            ebpfCapable =
                ebpfCapable(),
            historyRecords =
                if (
                    settings.healthHistoryEnabled
                ) {
                    db.count(n.key())
                } else {
                    0
                },
            lastDecision =
                lastDecision
        )
    }

    fun setDecision(text: String) {
        lastDecision =
            text.take(180)
    }

    fun buildSentinel(
        settings: AppSettings,
        mode: String,
        tunUp: Boolean,
        xrayUp: Boolean,
        hevUp: Boolean,
        killSwitchArmed: Boolean,
        previous: PrivacySentinelState =
            PrivacySentinelState()
    ): PrivacySentinelState {
        val selected =
            settings.splitTunnelPackages
                .split(
                    ',',
                    '\n',
                    '\r',
                    ';'
                )
                .map(String::trim)
                .count {
                    it.isNotBlank()
                }

        val bypassCount =
            when (
                settings.splitTunnelMode
            ) {
                SplitTunnelMode.BYPASS_SELECTED ->
                    selected
                SplitTunnelMode.ONLY_SELECTED ->
                    -1
                SplitTunnelMode.ALL_APPS ->
                    0
            }

        val fullTun =
            mode == "tun"

        return previous.copy(
            coverage =
                if (fullTun) {
                    if (
                        settings.splitTunnelMode ==
                        SplitTunnelMode.ALL_APPS
                    ) {
                        "DEVICE-WIDE"
                    } else {
                        "PARTIAL"
                    }
                } else {
                    "PROXY-ONLY"
                },
            tunnelRoutes =
                fullTun && tunUp,
            ipv4Captured =
                fullTun && tunUp,
            ipv6Captured =
                fullTun && tunUp,
            dnsHijack =
                fullTun &&
                    settings.dnsHijackEnabled,
            encryptedDns =
                settings.dnsPrimaryDoH
                    .startsWith("https://") &&
                    settings.dnsSecondaryDoH
                        .startsWith("https://"),
            systemDnsFallbackBlocked =
                fullTun &&
                    settings.dnsHijackEnabled &&
                    settings.dnsPrimaryDoH
                        .startsWith("https://"),
            killSwitchArmed =
                fullTun &&
                    killSwitchArmed,
            splitBypassCount =
                bypassCount,
            xrayAlive =
                xrayUp,
            hevAlive =
                hevUp,
            updatedAt =
                System.currentTimeMillis()
        )
    }

    fun probeDnsResolvers(
        port: Int,
        settings: AppSettings
    ) {
        if (
            !settings.adaptiveDnsEnabled ||
            port !in 1..65535
        ) {
            return
        }

        val candidates =
            listOf(
                settings.dnsPrimaryDoH,
                settings.dnsSecondaryDoH
            )
                .map(String::trim)
                .filter {
                    it.startsWith("https://")
                }
                .distinct()

        if (candidates.size < 2) {
            return
        }

        val timings =
            candidates.mapNotNull { raw ->
                runCatching {
                    val u = URL(raw)
                    val targetPort =
                        u.port
                            .takeIf {
                                it > 0
                            } ?: 443
                    val path =
                        u.file
                            .takeIf {
                                it.isNotBlank()
                            } ?: "/dns-query"

                    val probe =
                        SocksHttpClient.request(
                            port,
                            u.host,
                            targetPort,
                            "GET",
                            path,
                            null,
                            4_000,
                            4096
                        )

                    if (probe.status <= 0) {
                        null
                    } else {
                        raw to
                            probe.elapsedMs
                    }
                }.getOrNull()
            }.sortedBy {
                it.second
            }

        if (timings.isEmpty()) {
            return
        }

        val key =
            currentSnapshot().key()
        val winnerKey =
            "dns-winner:$key"
        val atKey =
            "dns-winner-at:$key"

        val oldWinner =
            prefs.getString(
                winnerKey,
                ""
            ).orEmpty()

        val best =
            timings.first()

        val oldTiming =
            timings.firstOrNull {
                it.first == oldWinner
            }

        // Hysteresis prevents one noisy DoH sample from continuously flipping resolver order.
        val chosen =
            when {
                oldTiming == null ->
                    best
                oldWinner == best.first ->
                    best
                best.second <=
                    oldTiming.second *
                        0.88 ->
                    best
                else ->
                    oldTiming
            }

        prefs.edit()
            .putString(
                winnerKey,
                chosen.first
            )
            .putLong(
                atKey,
                System.currentTimeMillis()
            )
            .apply()
    }

    private fun preferredDnsOrder(
        settings: AppSettings
    ): Pair<String, String> {
        if (!settings.adaptiveDnsEnabled) {
            return settings.dnsPrimaryDoH to
                settings.dnsSecondaryDoH
        }

        val key =
            currentSnapshot().key()

        val winner =
            prefs.getString(
                "dns-winner:$key",
                ""
            ).orEmpty()

        val learnedAt =
            prefs.getLong(
                "dns-winner-at:$key",
                0L
            )

        val fresh =
            learnedAt > 0L &&
                System.currentTimeMillis() -
                    learnedAt <=
                    7L *
                    24L *
                    60L *
                    60L *
                    1000L

        return if (
            fresh &&
            winner ==
            settings.dnsSecondaryDoH &&
            winner.isNotBlank()
        ) {
            settings.dnsSecondaryDoH to
                settings.dnsPrimaryDoH
        } else {
            settings.dnsPrimaryDoH to
                settings.dnsSecondaryDoH
        }
    }

    private fun kernelSuDetected(): Boolean =
        listOf(
            "/data/adb/ksu",
            "/data/adb/ksud",
            "/data/adb/ksu/bin/ksud"
        ).any {
            runCatching {
                java.io.File(it).exists() &&
                    java.io.File(it).canRead()
            }.getOrDefault(false)
        }

    private fun ebpfCapable(): Boolean =
        Build.VERSION.SDK_INT >= 26 &&
            listOf(
                "/sys/fs/bpf",
                "/sys/fs/bpf/netd_shared"
            ).any {
                runCatching {
                    java.io.File(it).exists()
                }.getOrDefault(false)
            }
}
