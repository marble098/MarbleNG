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
import com.marbleng.app.model.IranModePolicy
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.SplitTunnelMode
import com.marbleng.app.model.WorkloadProfile
import org.json.JSONObject
import java.io.Closeable
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    val jitterEwma: Double = 0.0,
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
        get() = coverage == "DEVICE-WIDE" && tunnelRoutes && ipv4Captured && ipv6Captured &&
            dnsHijack && encryptedDns && systemDnsFallbackBlocked && killSwitchArmed &&
            xrayAlive && hevAlive
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
    val accelerationLabel: String = "OFF",
    val acceleratedRoutes: Int = 0,
    val lastDecision: String = "Waiting for network intelligence"
)

/**
 * Datapath sizing for the userspace tunnel. Buffers and session limits are derived from measured
 * throughput instead of one fixed compromise, so a fast link stops being capped by a 64 KiB
 * socket buffer and a slow/throttled device stops paying for memory it cannot use.
 */
data class TunnelTuning(
    val maxSessions: Int,
    val tcpBufferBytes: Int,
    val udpBufferBytes: Int,
    val label: String
)

/**
 * Persistent, network-scoped health store. SQLite keeps the hot path dependency-free and bounded.
 */
private class HealthDb(context: Context) : SQLiteOpenHelper(context, "marble-intelligence.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS node_health(
                profile_id TEXT NOT NULL,
                network_key TEXT NOT NULL,
                samples INTEGER NOT NULL DEFAULT 0,
                success_ewma REAL NOT NULL DEFAULT 0,
                latency_ewma REAL NOT NULL DEFAULT 9999,
                jitter_ewma REAL NOT NULL DEFAULT 0,
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE node_health ADD COLUMN jitter_ewma REAL NOT NULL DEFAULT 0")
        }
    }

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

    /** Cross-network prior used only when this physical network has no evidence yet. */
    @Synchronized
    fun latest(profileId: String): NodeHealthRecord? {
        readableDatabase.query(
            "node_health", null, "profile_id=?", arrayOf(profileId),
            null, null, "last_seen_at DESC", "1"
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

    /**
     * Whole-network history in a single query.
     *
     * Candidate ordering used to call [get] from inside sort comparators, which produced
     * O(n log n) synchronized SQLite reads for every ranking pass. One bulk read keeps large
     * subscriptions (hundreds of nodes) responsive.
     */
    @Synchronized
    fun all(networkKey: String): Map<String, NodeHealthRecord> {
        val out = HashMap<String, NodeHealthRecord>()
        readableDatabase.query(
            "node_health",
            null,
            "network_key=?",
            arrayOf(networkKey),
            null,
            null,
            null
        ).use { c ->
            if (!c.moveToFirst()) return out
            val idIndex = c.getColumnIndexOrThrow("profile_id")
            val keyIndex = c.getColumnIndexOrThrow("network_key")
            val samplesIndex = c.getColumnIndexOrThrow("samples")
            val successIndex = c.getColumnIndexOrThrow("success_ewma")
            val latencyIndex = c.getColumnIndexOrThrow("latency_ewma")
            val jitterIndex = c.getColumnIndexOrThrow("jitter_ewma")
            val throughputIndex = c.getColumnIndexOrThrow("throughput_ewma")
            val udpIndex = c.getColumnIndexOrThrow("udp_ewma")
            val connectIndex = c.getColumnIndexOrThrow("connect_ms_ewma")
            val streakIndex = c.getColumnIndexOrThrow("failure_streak")
            val fragmentIndex = c.getColumnIndexOrThrow("preferred_fragment")
            val muxIndex = c.getColumnIndexOrThrow("preferred_mux")
            val lastSuccessIndex = c.getColumnIndexOrThrow("last_success_at")
            val lastSeenIndex = c.getColumnIndexOrThrow("last_seen_at")
            do {
                val id = c.getString(idIndex) ?: continue
                out[id] = NodeHealthRecord(
                    profileId = id,
                    networkKey = c.getString(keyIndex),
                    samples = c.getInt(samplesIndex),
                    successEwma = c.getDouble(successIndex),
                    latencyEwma = c.getDouble(latencyIndex),
                    jitterEwma = c.getDouble(jitterIndex),
                    throughputEwma = c.getDouble(throughputIndex),
                    udpEwma = c.getDouble(udpIndex),
                    connectMsEwma = c.getDouble(connectIndex),
                    failureStreak = c.getInt(streakIndex),
                    preferredFragment = c.getInt(fragmentIndex) != 0,
                    preferredMux = c.getInt(muxIndex) != 0,
                    lastSuccessAt = c.getLong(lastSuccessIndex),
                    lastSeenAt = c.getLong(lastSeenIndex)
                )
            } while (c.moveToNext())
        }
        return out
    }

    /** Latest observation per profile across networks, loaded once for cold-network ranking. */
    @Synchronized
    fun latestAll(): Map<String, NodeHealthRecord> {
        val out = HashMap<String, NodeHealthRecord>()
        readableDatabase.query(
            "node_health", null, null, null, null, null, "last_seen_at DESC", "2000"
        ).use { c ->
            if (!c.moveToFirst()) return out
            fun i(name: String) = c.getColumnIndexOrThrow(name)
            do {
                val id = c.getString(i("profile_id")) ?: continue
                if (id in out) continue
                out[id] = NodeHealthRecord(
                    profileId = id,
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
            } while (c.moveToNext())
        }
        return out
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
        val measuredLatency = result.latencyMs.takeIf { result.success > 0 && it > 0.0 }
        val measuredJitter = result.jitterMs.takeIf { result.sampleCount >= 2 && it >= 0.0 }
        val measuredThroughput = result.bytesPerSecond.takeIf { result.success > 0 && it > 0.0 }
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("network_key", networkKey)
            put("samples", n)
            put("success_ewma", ewma(old?.successEwma, success, 0.0))
            put("latency_ewma", measuredLatency?.let { ewma(old?.latencyEwma, it.coerceAtMost(10_000.0), 9999.0) } ?: old?.latencyEwma ?: 9999.0)
            put("jitter_ewma", measuredJitter?.let { ewma(old?.jitterEwma, it.coerceAtMost(5_000.0), 0.0) } ?: old?.jitterEwma ?: 0.0)
            put("throughput_ewma", measuredThroughput?.let { ewma(old?.throughputEwma, it, 0.0) } ?: old?.throughputEwma ?: 0.0)
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
        // Failed connection durations are not connection-latency samples. Reliability and
        // failureStreak already account for the outage, so only successful handshakes teach latency.
        val connectEwma =
            if (success) {
                val currentConnect = connectMs.coerceAtLeast(0).toDouble()
                val previousConnect =
                    old?.connectMsEwma?.takeIf { it > 0.0 } ?: currentConnect
                if (old == null || old.connectMsEwma <= 0.0) {
                    currentConnect
                } else {
                    previousConnect * (1.0 - alpha) + currentConnect * alpha
                }
            } else {
                old?.connectMsEwma ?: 0.0
            }
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
            put("jitter_ewma", old?.jitterEwma ?: 0.0)
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
        // Frequent live telemetry refines quality EWMAs but does not increase evidence confidence.
        // Otherwise a periodic ping loop can turn one real benchmark into high confidence quickly.
        val n = old.samples
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
        throughputBps: Long,
        jitterMs: Int,
        successPercent: Int
    ) {
        val old = get(profileId, networkKey) ?: return
        val now = System.currentTimeMillis()
        // Same confidence rule as recordRoute(): telemetry updates quality, not evidence count.
        val n = old.samples
        val alpha = if (old.samples <= 2) 0.42 else 0.12
        val latencyNow = latencyMs.coerceAtLeast(1).toDouble()
        val latency = if (!old.latencyEwma.isFinite() || old.latencyEwma >= 9000.0) latencyNow
            else old.latencyEwma * (1.0 - alpha) + latencyNow * alpha
        val throughputNow = throughputBps.coerceAtLeast(0).toDouble()
        val throughput = if (old.throughputEwma <= 0.0 && throughputNow > 0.0) throughputNow
            else old.throughputEwma * (1.0 - alpha) + throughputNow * alpha
        val jitterNow = jitterMs.coerceAtLeast(0).toDouble()
        val jitter = if (old.jitterEwma <= 0.0) jitterNow
            else old.jitterEwma * (1.0 - alpha) + jitterNow * alpha
        val observedSuccess = successPercent.coerceIn(0, 100).toDouble()
        val values = ContentValues().apply {
            put("samples", n)
            put("success_ewma", (old.successEwma * (1.0 - alpha) + observedSuccess * alpha).coerceIn(0.0, 100.0))
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
            put("jitter_ewma", old?.jitterEwma ?: 0.0)
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
// MARBLE_MEASURED_FIRST_V14
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

    /**
     * Measured acceleration plans, keyed by profile + physical-network fingerprint. Kept in memory
     * for the hot path and mirrored into prefs so a proven method survives an app restart.
     */
    private val accelerationCache = ConcurrentHashMap<String, AccelerationPlan>()
    @Volatile private var accelerationLoaded = false
    @Volatile private var lastAcceleration = ""

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
        val previousNetwork = activeNetwork
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
            previousNetwork != network ||
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
        if (!settings.adaptiveMtuEnabled) {
            effectiveMtu = settings.mtuMax.coerceIn(1280, 9000)
            return effectiveMtu
        }
        val n = currentSnapshot()
        val recommendation = AdaptiveMtuPolicy.recommend(
            AdaptiveMtuPolicy.Input(
                physicalMtu = n.mtu,
                configuredMin = settings.mtuMin,
                configuredMax = settings.mtuMax,
                networkTransport = n.transport,
                proxyScheme = profile.scheme,
                proxyTransport = profile.transport
            )
        )
        val learned = learnedPathMtu(profile.id)
        effectiveMtu = if (learned in 1280..9000) min(recommendation.mtu, learned) else recommendation.mtu
        return effectiveMtu
    }

    /** Measured health for one node on the current physical network, or null when unknown. */
    fun healthOf(profileId: String): NodeHealthRecord? =
        db.get(profileId, currentSnapshot().key())

    private fun accelerationKey(profileId: String): String =
        "$profileId|${currentSnapshot().key()}"

    @Synchronized
    private fun loadAcceleration() {
        if (accelerationLoaded) return
        accelerationLoaded = true
        runCatching {
            val stored = JSONObject(prefs.getString(ACCELERATION_PREF, "{}") ?: "{}")
            stored.keys().forEach { key ->
                val plan = stored.optJSONObject(key)?.let { AccelerationPlan.fromJson(it) }
                    ?: return@forEach
                accelerationCache[key] = plan
            }
        }
    }

    private fun persistAcceleration() {
        runCatching {
            // Bounded: only the most recently proven routes are worth carrying across restarts.
            val recent = accelerationCache.entries
                .sortedByDescending { it.value.at }
                .take(ACCELERATION_LIMIT)
            val out = JSONObject()
            recent.forEach { (key, plan) -> out.put(key, plan.toJson()) }
            if (recent.size < accelerationCache.size) {
                val keep = recent.mapTo(mutableSetOf()) { it.key }
                accelerationCache.keys.retainAll(keep)
            }
            prefs.edit().putString(ACCELERATION_PREF, out.toString()).apply()
        }
    }

    /**
     * The acceleration method proven for this node on this network, or null when nothing fresh is
     * known. Stale evidence expires instead of being trusted forever: link conditions move.
     */
    fun acceleration(profileId: String): AccelerationPlan? {
        if (profileId.isBlank()) return null
        loadAcceleration()
        val plan = accelerationCache[accelerationKey(profileId)] ?: return null
        if (System.currentTimeMillis() - plan.at > ACCELERATION_TTL_MS) return null
        return plan
    }

    fun rememberAcceleration(profileId: String, plan: AccelerationPlan) {
        if (profileId.isBlank()) return
        loadAcceleration()
        accelerationCache[accelerationKey(profileId)] = plan
        lastAcceleration = if (plan.neutral) "DIRECT" else plan.label.uppercase()
        persistAcceleration()
    }

    fun forgetAcceleration(profileId: String) {
        if (profileId.isBlank()) return
        loadAcceleration()
        if (accelerationCache.remove(accelerationKey(profileId)) != null) persistAcceleration()
    }

    private fun accelerationCount(): Int {
        loadAcceleration()
        val now = System.currentTimeMillis()
        val suffix = "|${currentSnapshot().key()}"
        return accelerationCache.count { (key, plan) ->
            key.endsWith(suffix) && now - plan.at <= ACCELERATION_TTL_MS && !plan.neutral
        }
    }

    private fun pathMtuKey(profileId: String): String = "path-mtu|$profileId|${currentSnapshot().key()}"

    /** Passive kernel PMTU memory; not claimed as full RFC 8899 DPLPMTUD. MARBLE_REALTIME_ENGINE_V70 */
    fun rememberPathMtu(profileId: String, mtu: Int) {
        if (profileId.isBlank() || mtu !in 1280..9000) return
        prefs.edit().putString(pathMtuKey(profileId), "$mtu:${System.currentTimeMillis()}").apply()
    }

    fun learnedPathMtu(profileId: String): Int {
        val parts = (prefs.getString(pathMtuKey(profileId), "") ?: "").split(':')
        val mtu = parts.getOrNull(0)?.toIntOrNull() ?: return 0
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return 0
        if (System.currentTimeMillis() - at !in 0L..PATH_MTU_TTL_MS) return 0
        return mtu.takeIf { it in 1280..9000 } ?: 0
    }

    /**
     * Userspace-tunnel sizing for the node about to carry traffic. Evidence comes from the measured
     * acceleration pass first and from long-run history second; both beat a static guess.
     */
    // MARBLE_LATENCY_FIRST_DATAPATH_V18
    fun tunnelTuning(profileId: String, settings: AppSettings): TunnelTuning {
        val thermal = thermalBudget(settings)
        val network = currentSnapshot()

        val conservative = TunnelTuning(
            maxSessions = if (thermal < 0.55) 2048 else 4096,
            tcpBufferBytes = 65_536,
            udpBufferBytes = 524_288,
            label = "baseline"
        )
        if (!settings.adaptiveBufferEnabled) return conservative

        val measured = acceleration(profileId)?.bytesPerSecond ?: 0.0
        val historical = if (settings.healthHistoryEnabled) {
            db.get(profileId, network.key())?.throughputEwma ?: 0.0
        } else {
            0.0
        }
        val linkBps = network.downstreamKbps.toDouble() * 1000.0 / 8.0
        val evidence = max(max(measured, historical), linkBps * 0.5)

        val throughputTuned = when {
            evidence >= 6.0 * 1024.0 * 1024.0 ->
                TunnelTuning(8192, 262_144, 1_048_576, "high-bandwidth")
            evidence >= 1.5 * 1024.0 * 1024.0 ->
                TunnelTuning(6144, 131_072, 786_432, "wide")
            else ->
                conservative
        }

        /*
         * Bufferbloat guard.
         *
         * Large userspace socket queues help bulk throughput but they also let a cellular burst sit
         * in Marble/HEV longer before the kernel/radio drains it. For AUTO/INTERACTIVE/STABILITY on
         * cellular or any explicitly INTERACTIVE/STABILITY workload, cap the userspace queues at the
         * proven baseline. STREAMING deliberately keeps the wider throughput-tuned datapath.
         *
         * This does not touch kernel qdiscs or require root; it only prevents Marble from adding a
         * second oversized queue in front of the physical link.
         */
        val latencyFirst =
            settings.workloadProfile == WorkloadProfile.INTERACTIVE ||
                settings.workloadProfile == WorkloadProfile.STABILITY ||
                (
                    settings.workloadProfile != WorkloadProfile.STREAMING &&
                        (network.transport == "cellular" || network.metered)
                )

        val latencyCapped = if (latencyFirst) {
            TunnelTuning(
                maxSessions = min(throughputTuned.maxSessions, 4096),
                tcpBufferBytes = min(throughputTuned.tcpBufferBytes, 65_536),
                udpBufferBytes = min(throughputTuned.udpBufferBytes, 524_288),
                label = "${throughputTuned.label}/latency-first"
            )
        } else {
            throughputTuned
        }

        val liveHealth = if (settings.healthHistoryEnabled) db.get(profileId, network.key()) else null
        val jitterCapped = if ((liveHealth?.jitterEwma ?: 0.0) >= 24.0) {
            // Root-free queue pressure control: reduce Marble/HEV buffering when the route itself is jittery.
            // This is intentionally not labelled FQ-CoDel. MARBLE_REALTIME_ENGINE_V70
            TunnelTuning(
                maxSessions = min(latencyCapped.maxSessions, 3072),
                tcpBufferBytes = min(latencyCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(latencyCapped.udpBufferBytes, 262_144),
                label = "${latencyCapped.label}/jitter-pressure"
            )
        } else latencyCapped

        // Never let a bigger datapath fight the thermal governor for the same silicon.
        return if (thermal < 0.55) {
            TunnelTuning(
                maxSessions = min(jitterCapped.maxSessions, 2048),
                tcpBufferBytes = min(jitterCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(jitterCapped.udpBufferBytes, 262_144),
                label = "${jitterCapped.label}/thermal"
            )
        } else jitterCapped
    }

    /**
     * @param withAcceleration false returns the pre-acceleration baseline, which is what the
     *   tuner must measure against so it never re-proves its own previous conclusion.
     */
    // MARBLE_IP_FAMILY_INTELLIGENCE_V24
    fun effectiveSettings(
        profile: ProxyProfile,
        base: AppSettings,
        withAcceleration: Boolean = true
    ): AppSettings {
        startMonitoring()

        val n = snapshot

        // Prefer IPv6 is a preference, not a demand. Suspend it on IPv4-only links and restore it
        // automatically when a real global IPv6 underlay becomes available.
        val effectivePreferIpv6 =
            base.ipv6Enabled &&
                base.preferIpv6 &&
                n.hasIpv6

        val queryStrategy = when {
            !base.ipv6Enabled -> "UseIPv4"
            !base.adaptiveDualStackEnabled -> base.dnsQueryStrategy
            n.hasIpv4 && !n.hasIpv6 -> "UseIPv4"
            n.hasIpv6 && !n.hasIpv4 -> "UseIPv6"
            else -> "UseIP"
        }

        val dnsOrdered = preferredDnsOrder(base)
        val ipRace = SmartIpRacePolicy.decide(n, db.get(profile.id, n.key()), base.copy(preferIpv6 = effectivePreferIpv6))
        // The underlay decides which records are even worth asking for; the plan then decides the
        // family order for the tunnel, the delay test and the probers in one place. A measured IPv6
        // pathology on this node demotes the automatic ordering, but never overrides what the user
        // explicitly asked for.
        val familyBase = base.copy(dnsQueryStrategy = queryStrategy)
        val measuredV6Healthy = if (ipRace.reason == "unstable-race") false else null
        val familyPreference = AddressFamilyPolicy.preference(
            settings = familyBase,
            underlayHasIpv6 = n.hasIpv6,
            measuredV6Healthy = measuredV6Healthy
        )

        // Measured-first policy: history chooses what to test, but never mutates Fragment/Mux by guess.
        // Explicit user settings remain intact, and IranShield may still apply censorship-specific changes.
        val tuned = familyBase.copy(
            dnsQueryStrategy = AddressFamilyPolicy.dnsQueryStrategy(familyBase, familyPreference),
            preferIpv6 = AddressFamilyPolicy.prioritizeIpv6(
                preference = familyPreference,
                underlayHasIpv6 = n.hasIpv6,
                measuredV6Healthy = measuredV6Healthy
            ),
            dnsPrimaryDoH = dnsOrdered.first,
            dnsSecondaryDoH = dnsOrdered.second,
            // A zero delay is meaningful: it tells the hardener not to arm Xray's race at all, which
            // then resolves deterministically instead of leaving the choice to the engine's random pick.
            happyEyeballsTryDelayMs = if (ipRace.tryDelayMs > 0) {
                ipRace.tryDelayMs.coerceIn(AddressFamilyPolicy.MIN_TRY_DELAY_MS, AddressFamilyPolicy.MAX_TRY_DELAY_MS)
            } else {
                0
            },
            happyEyeballsMaxConcurrent = ipRace.maxConcurrentTry
        )

        // Only a freshly measured acceleration plan may change generic transport tuning.
        val accelerated =
            if (withAcceleration && base.connectTuningEnabled) {
                acceleration(profile.id)?.applyTo(tuned) ?: tuned
            } else {
                tuned
            }

        // Iran Mode is applied last so its countermeasures win over generic adaptive tuning.
        return IranShield.apply(accelerated, profile, iranState, iranGeoIpReady)
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

    /** One SQLite read for the whole current-network history. */
    fun healthSnapshot(): Map<String, NodeHealthRecord> =
        db.all(currentSnapshot().key())

    /** Profile ids that already carry measured evidence on the current physical network. */
    fun knownProfileIds(): Set<String> = healthSnapshot().keys

    fun predictedScore(
        profile: ProxyProfile,
        settings: AppSettings
    ): Double {
        val current = db.get(profile.id, currentSnapshot().key())
        return predictedScoreOf(current ?: db.latest(profile.id), settings, if (current == null) 0.35 else 1.0)
    }

    private fun predictedScoreOf(
        record: NodeHealthRecord?,
        settings: AppSettings,
        evidenceScale: Double = 1.0
    ): Double {
        if (
            !settings.intelligenceEnabled ||
            !settings.healthHistoryEnabled
        ) {
            return 50.0
        }

        val h = record ?: return 50.0

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

        // A conservative Wilson lower bound prevents one lucky sample from outranking proven nodes.
        val evidenceCount = max(1, h.samples).toDouble()
        val probability = h.successEwma.coerceIn(0.0, 100.0) / 100.0
        val z = 1.28155 // 80% one-sided confidence: useful without over-penalising new nodes.
        val z2 = z * z
        val wilson = (
            probability + z2 / (2.0 * evidenceCount) -
                z * sqrt((probability * (1.0 - probability) + z2 / (4.0 * evidenceCount)) / evidenceCount)
            ) / (1.0 + z2 / evidenceCount)
        val reliability = (wilson * 100.0).coerceIn(0.0, 100.0)
        val latency =
            expScore(h.latencyEwma, 250.0)
        val jitter = if (h.jitterEwma <= 0.0) 55.0 else expScore(h.jitterEwma, 45.0)

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
            reliability * 0.32 + latency * 0.34 + jitter * 0.22 + connect * 0.12

        val streaming =
            reliability * 0.34 + speed * 0.42 + latency * 0.12 + jitter * 0.12

        val stability =
            reliability * 0.46 + latency * 0.20 + jitter * 0.22 + connect * 0.12

        val resilience =
            reliability * 0.50 + udp * 0.18 + latency * 0.14 + jitter * 0.18 +
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
            (h.samples / 8.0 * evidenceScale)
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

        // Failure streaks decay one step every six quiet hours, allowing recovered nodes back in.
        val failureAgeHours = (System.currentTimeMillis() - h.lastSeenAt).coerceAtLeast(0L) / 3_600_000.0
        val effectiveFailureStreak = (h.failureStreak - (failureAgeHours / 6.0).toInt()).coerceAtLeast(0)
        val failurePenalty =
            effectiveFailureStreak
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
            state.active &&
                state.policy == IranModePolicy.ALWAYS_ON &&
                (previous.policy != IranModePolicy.ALWAYS_ON || !previous.active) ->
                setDecision(
                    "Iran Mode forced on • detection bypassed • protection tier ${IranShield.tier(state)}"
                )

            state.active && (!previous.active || previous.ispLine != state.ispLine) ->
                setDecision(
                    "Iran underlay integrated • ${state.ispLine} • ${state.confidence}% confidence • " +
                        "tier ${IranShield.tier(state)}"
                )

            previous.active && !state.active && state.policy == IranModePolicy.OFF ->
                setDecision("Iran Mode disabled by user policy")

            previous.active && !state.active ->
                setDecision(
                    "Iran Mode returned to standby • physical underlay remains under Marble monitoring"
                )
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

    /**
     * Ranking score for a whole list using a single history read. Callers that need to sort or
     * filter many profiles must use this instead of [rankingScore] per element.
     */
    fun rankingScores(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        health: Map<String, NodeHealthRecord> = healthSnapshot()
    ): Map<String, Double> {
        val out = HashMap<String, Double>(profiles.size * 2)
        val priors = if (health.size < profiles.size) db.latestAll() else emptyMap()
        profiles.forEach { profile ->
            if (!out.containsKey(profile.id)) {
                val exact = health[profile.id]
                out[profile.id] =
                    predictedScoreOf(exact ?: priors[profile.id], settings, if (exact == null) 0.35 else 1.0) +
                        IranShield.profileBias(profile, iranState)
            }
        }
        return out
    }

    fun orderCandidates(
        profiles: List<ProxyProfile>,
        settings: AppSettings
    ): List<ProxyProfile> {
        if (!settings.intelligenceEnabled || profiles.isEmpty()) {
            return profiles
        }

        // Scores and last-success timestamps are resolved once; the comparator only reads memory.
        val health = healthSnapshot()
        val scores = rankingScores(profiles, settings, health)

        return profiles.sortedWith(
            compareByDescending<ProxyProfile> {
                scores[it.id] ?: 50.0
            }.thenByDescending {
                health[it.id]?.lastSuccessAt ?: 0L
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
        val health =
            healthSnapshot()

        val chosen =
            mutableListOf<ProxyProfile>()
        val seenHosts =
            linkedSetOf<String>()

        for (p in ordered) {
            if (chosen.size >= limit) break

            if (
                (health[p.id]?.failureStreak ?: 0) >=
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
        throughputBps: Long,
        jitterMs: Int,
        successPercent: Int,
        settings: AppSettings
    ) {
        if (!settings.healthHistoryEnabled || profileId.isBlank() || latencyMs <= 0) return
        db.recordLiveRoute(
            profileId,
            currentSnapshot().key(),
            latencyMs,
            throughputBps,
            jitterMs,
            successPercent
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
            accelerationLabel =
                if (settings.connectTuningEnabled) {
                    lastAcceleration.ifBlank { "READY" }
                } else {
                    "OFF"
                },
            acceleratedRoutes =
                if (settings.connectTuningEnabled) accelerationCount() else 0,
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
        ipv6RouteCaptured: Boolean,
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
                fullTun && tunUp && ipv6RouteCaptured,
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

        // A bare GET /dns-query can return HTTP 400 quickly even when the resolver itself cannot
        // answer DNS. Send a real RFC 8484 wire-format query so the learned winner represents a
        // usable encrypted resolver rather than just a reachable HTTPS socket.
        val dnsQuery = java.io.ByteArrayOutputStream().apply {
            write(byteArrayOf(0x4d, 0x47, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            "example.com".split('.').forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                write(bytes.size)
                write(bytes)
            }
            write(0)
            write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        }.toByteArray()

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
                            "POST",
                            path,
                            dnsQuery,
                            2_500,
                            4096,
                            mapOf(
                                "Content-Type" to "application/dns-message",
                                "Accept" to "application/dns-message"
                            )
                        )

                    if (probe.status != 200 || probe.body.size < 12) {
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
            setDecision("Encrypted DNS audit failed • current order retained; serial fallback remains armed")
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

    private companion object {
        const val ACCELERATION_PREF = "route-acceleration"
        const val ACCELERATION_LIMIT = 160
        const val ACCELERATION_TTL_MS = 6L * 60L * 60L * 1000L
        const val PATH_MTU_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
