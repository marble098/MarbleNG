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
import com.marbleng.app.model.IranModePolicy
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.SplitTunnelMode
import com.marbleng.app.model.WorkloadProfile
import org.json.JSONObject
import java.io.Closeable
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
    val lastSuccessAt: Long = 0L,
    val lastSeenAt: Long = 0L,

    val injectedResetRate: Double = 0.0,
    val throttlePatternConfidence: Double = 0.0,
    val timeOfDayBucketScores: Map<String, Double> = emptyMap(),
    val transportSpecificScores: Map<String, Double> = emptyMap(),
    val attributedCauseLast: String = "",
    val carrierContext: String = "",
        val observationSeriesKey: String = ""
)

private fun parseScoreMap(raw: String?): Map<String, Double> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        val out = HashMap<String, Double>(obj.length())
        obj.keys().forEach { key -> out[key] = obj.optDouble(key, 0.0) }
        out
    }.getOrDefault(emptyMap())
}

private fun encodeScoreMap(map: Map<String, Double>): String {
    val obj = JSONObject()
    map.forEach { (key, value) -> obj.put(key, value) }
    return obj.toString()
}

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
    val dnsStormActive: Boolean = false,
    val familyLock: String = "",
    val lastDecision: String = "Waiting for network intelligence"
)

data class TunnelTuning(
    val maxSessions: Int,
    val tcpBufferBytes: Int,
    val udpBufferBytes: Int,
    val label: String
)

object ProtocolFitness {
    fun noisy(link: LinkEvidence): Boolean = link.known && (
        link.jitterMs >= 20.0 ||
            link.lossPercent >= 6.0 ||
            (link.rttMs >= 250.0 && link.jitterMs >= 12.0)
        )

    fun bias(profile: ProxyProfile, link: LinkEvidence): Double {
        val scheme = profile.scheme.trim().lowercase()
        val transport = profile.transport.trim().lowercase()
        if (scheme.isEmpty()) return 0.0
        val onNoisy = noisy(link)

        if (scheme in setOf("hysteria2", "hysteria", "tuic")) return if (onNoisy) 12.0 else 3.0
        if (scheme == "wireguard") return if (onNoisy) 5.0 else 1.0

        if (transport in setOf("xhttp", "splithttp", "httpupgrade", "h2", "h2c", "quic")) {
            return if (onNoisy) -12.0 else -2.0
        }
        if (transport == "websocket") return if (onNoisy) -7.0 else -1.0

        if (scheme == "vless" || scheme == "trojan" || scheme == "vmess") {
            val secured = profile.security.trim().lowercase() in setOf("tls", "reality")
            return when {
                !secured -> -8.0
                transport == "raw" || transport.isEmpty() -> if (onNoisy) 2.0 else 1.0
                else -> 0.0
            }
        }
        return 0.0
    }

    fun prefersMuxOff(profile: ProxyProfile, link: LinkEvidence): Boolean {
        if (!noisy(link)) return false
        val transport = profile.transport.trim().lowercase()
        return transport in setOf("xhttp", "splithttp", "httpupgrade", "h2", "h2c", "websocket")
    }
}

private class HealthDb(context: Context) : SQLiteOpenHelper(context, "marble-intelligence.db", null, 3) {
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
                last_success_at INTEGER NOT NULL DEFAULT 0,
                last_seen_at INTEGER NOT NULL DEFAULT 0,
                injected_reset_rate REAL NOT NULL DEFAULT 0,
                throttle_pattern_confidence REAL NOT NULL DEFAULT 0,
                time_of_day_bucket_scores TEXT NOT NULL DEFAULT '{}',
                transport_specific_scores TEXT NOT NULL DEFAULT '{}',
                attributed_cause_last TEXT NOT NULL DEFAULT '',
                carrier_context TEXT NOT NULL DEFAULT '',
                observation_series_key TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(profile_id, network_key)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_health_network ON node_health(network_key, last_seen_at DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ping_observation(
                profile_id TEXT NOT NULL,
                network_key TEXT NOT NULL,
                at_ms INTEGER NOT NULL,
                latency_ms REAL NOT NULL DEFAULT 0,
                jitter_ms REAL NOT NULL DEFAULT 0,
                success_percent REAL NOT NULL DEFAULT 0,
                throughput_bps REAL NOT NULL DEFAULT 0,
                transport_type TEXT NOT NULL DEFAULT '',
                injected_reset_suspected INTEGER NOT NULL DEFAULT 0,
                injected_reset_rate REAL NOT NULL DEFAULT 0,
                sawtooth_confidence REAL NOT NULL DEFAULT 0,
                PRIMARY KEY(profile_id, network_key, at_ms)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ping_obs_network ON ping_observation(network_key, at_ms DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE node_health ADD COLUMN jitter_ewma REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE node_health ADD COLUMN injected_reset_rate REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE node_health ADD COLUMN throttle_pattern_confidence REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE node_health ADD COLUMN time_of_day_bucket_scores TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE node_health ADD COLUMN transport_specific_scores TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE node_health ADD COLUMN attributed_cause_last TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE node_health ADD COLUMN carrier_context TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE node_health ADD COLUMN observation_series_key TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ping_observation(
                    profile_id TEXT NOT NULL,
                    network_key TEXT NOT NULL,
                    at_ms INTEGER NOT NULL,
                    latency_ms REAL NOT NULL DEFAULT 0,
                    jitter_ms REAL NOT NULL DEFAULT 0,
                    success_percent REAL NOT NULL DEFAULT 0,
                    throughput_bps REAL NOT NULL DEFAULT 0,
                    transport_type TEXT NOT NULL DEFAULT '',
                    injected_reset_suspected INTEGER NOT NULL DEFAULT 0,
                    injected_reset_rate REAL NOT NULL DEFAULT 0,
                    sawtooth_confidence REAL NOT NULL DEFAULT 0,
                    PRIMARY KEY(profile_id, network_key, at_ms)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ping_obs_network ON ping_observation(network_key, at_ms DESC)")
        }
    }

    private fun readRecord(c: android.database.Cursor): NodeHealthRecord {
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
            lastSuccessAt = c.getLong(i("last_success_at")),
            lastSeenAt = c.getLong(i("last_seen_at")),
            injectedResetRate = if (c.getColumnIndex("injected_reset_rate") >= 0) c.getDouble(i("injected_reset_rate")) else 0.0,
            throttlePatternConfidence =
                if (c.getColumnIndex("throttle_pattern_confidence") >= 0) c.getDouble(i("throttle_pattern_confidence")) else 0.0,
            timeOfDayBucketScores = if (c.getColumnIndex("time_of_day_bucket_scores") >= 0) {
                parseScoreMap(c.getString(i("time_of_day_bucket_scores")))
            } else emptyMap(),
            transportSpecificScores = if (c.getColumnIndex("transport_specific_scores") >= 0) {
                parseScoreMap(c.getString(i("transport_specific_scores")))
            } else emptyMap(),
            attributedCauseLast =
                if (c.getColumnIndex("attributed_cause_last") >= 0) c.getString(i("attributed_cause_last")) ?: "" else "",
            carrierContext = if (c.getColumnIndex("carrier_context") >= 0) c.getString(i("carrier_context")) ?: "" else "",
            observationSeriesKey =
                if (c.getColumnIndex("observation_series_key") >= 0) c.getString(i("observation_series_key")) ?: "" else ""
        )
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
            return readRecord(c)
        }
    }

    @Synchronized
    fun latest(profileId: String): NodeHealthRecord? {
        readableDatabase.query(
            "node_health", null, "profile_id=?", arrayOf(profileId),
            null, null, "last_seen_at DESC", "1"
        ).use { c ->
            if (!c.moveToFirst()) return null
            return readRecord(c)
        }
    }

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
            do {
                val id = c.getString(idIndex) ?: continue
                out[id] = readRecord(c)
            } while (c.moveToNext())
        }
        return out
    }

    @Synchronized
    fun latestAll(): Map<String, NodeHealthRecord> {
        val out = HashMap<String, NodeHealthRecord>()
        readableDatabase.query(
            "node_health", null, null, null, null, null, "last_seen_at DESC", "2000"
        ).use { c ->
            if (!c.moveToFirst()) return out
            val idIndex = c.getColumnIndexOrThrow("profile_id")
            do {
                val id = c.getString(idIndex) ?: continue
                if (id in out) continue
                out[id] = readRecord(c)
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
        if (CoreFailurePolicy.isLocal(result.failureReason) || result.probeKind == RouteProbe.METHOD_TCP_PING) return
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
            put("last_success_at", if (result.success > 0) now else old?.lastSuccessAt ?: 0L)
            put("last_seen_at", now)
        }

        old?.let { carryLayerColumns(it, values) }
        writableDatabase.insertWithOnConflict("node_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        trim(networkKey)
    }

    private fun carryLayerColumns(old: NodeHealthRecord, values: ContentValues) {
        values.put("injected_reset_rate", old.injectedResetRate)
        values.put("throttle_pattern_confidence", old.throttlePatternConfidence)
        values.put("time_of_day_bucket_scores", encodeScoreMap(old.timeOfDayBucketScores))
        values.put("transport_specific_scores", encodeScoreMap(old.transportSpecificScores))
        values.put("attributed_cause_last", old.attributedCauseLast)
        values.put("carrier_context", old.carrierContext)
        values.put("observation_series_key", old.observationSeriesKey)
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
            put("last_success_at", if (success) now else old?.lastSuccessAt ?: 0L)
            put("last_seen_at", now)
        }
        old?.let { carryLayerColumns(it, values) }
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
            put("last_success_at", old?.lastSuccessAt ?: 0L)
            put("last_seen_at", System.currentTimeMillis())
        }
        old?.let { carryLayerColumns(it, values) }
        writableDatabase.insertWithOnConflict("node_health", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun trim(networkKey: String) {
        writableDatabase.execSQL(
            "DELETE FROM node_health WHERE network_key=? AND rowid NOT IN " +
                "(SELECT rowid FROM node_health WHERE network_key=? ORDER BY last_seen_at DESC LIMIT 600)",
            arrayOf(networkKey, networkKey)
        )
    }

    @Synchronized
    fun updateLayerFields(
        profileId: String,
        networkKey: String,
        values: ContentValues,
        old: NodeHealthRecord?
    ) {
        if (old != null) {
            writableDatabase.update(
                "node_health",
                values,
                "profile_id=? AND network_key=?",
                arrayOf(profileId, networkKey)
            )
        } else {
            values.put("profile_id", profileId)
            values.put("network_key", networkKey)
            writableDatabase.insertWithOnConflict(
                "node_health",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    @Synchronized
    fun insertObservation(
        profileId: String,
        networkKey: String,
        sample: BehavioralConsistency.ConsistencySample
    ) {
        val values = ContentValues().apply {
            put("profile_id", profileId)
            put("network_key", networkKey)
            put("at_ms", sample.atMs)
            put("latency_ms", sample.latencyMs)
            put("jitter_ms", sample.jitterMs)
            put("success_percent", sample.successPercent)
            put("throughput_bps", sample.throughputBps)
            put("transport_type", sample.transportType)
            put("injected_reset_suspected", if (sample.injectedResetSuspected) 1 else 0)
            put("injected_reset_rate", sample.injectedResetRatePercent)
            put("sawtooth_confidence", sample.sawtoothConfidence)
        }
        writableDatabase.insertWithOnConflict(
            "ping_observation",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        writableDatabase.execSQL(
            "DELETE FROM ping_observation WHERE profile_id=? AND network_key=? AND at_ms < ?",
            arrayOf<Any?>(profileId, networkKey, System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1000L)
        )
    }

    @Synchronized
    fun observations(
        profileId: String,
        networkKey: String,
        sinceMs: Long
    ): List<BehavioralConsistency.ConsistencySample> {
        val out = ArrayList<BehavioralConsistency.ConsistencySample>()
        readableDatabase.query(
            "ping_observation",
            null,
            "profile_id=? AND network_key=? AND at_ms>=?",
            arrayOf(profileId, networkKey, sinceMs.toString()),
            null,
            null,
            "at_ms ASC"
        ).use { c ->
            val at = c.getColumnIndexOrThrow("at_ms")
            val lat = c.getColumnIndexOrThrow("latency_ms")
            val jit = c.getColumnIndexOrThrow("jitter_ms")
            val success = c.getColumnIndexOrThrow("success_percent")
            val tp = c.getColumnIndexOrThrow("throughput_bps")
            val transport = c.getColumnIndexOrThrow("transport_type")
            val reset = c.getColumnIndexOrThrow("injected_reset_suspected")
            val rate = c.getColumnIndexOrThrow("injected_reset_rate")
            val saw = c.getColumnIndexOrThrow("sawtooth_confidence")
            while (c.moveToNext()) {
                out += BehavioralConsistency.ConsistencySample(
                    atMs = c.getLong(at),
                    latencyMs = c.getDouble(lat),
                    jitterMs = c.getDouble(jit),
                    successPercent = c.getDouble(success),
                    throughputBps = c.getDouble(tp),
                    transportType = c.getString(transport) ?: "",
                    injectedResetSuspected = c.getInt(reset) != 0,
                    injectedResetRatePercent = c.getDouble(rate),
                    sawtoothConfidence = if (c.getColumnIndex("sawtooth_confidence") >= 0) c.getDouble(saw) else 0.0
                )
            }
        }
        return out
    }

    @Synchronized
    fun networkObservations(
        networkKey: String,
        sinceMs: Long
    ): Map<String, List<BehavioralConsistency.ConsistencySample>> {
        val out = HashMap<String, MutableList<BehavioralConsistency.ConsistencySample>>()
        readableDatabase.query(
            "ping_observation",
            null,
            "network_key=? AND at_ms>=?",
            arrayOf(networkKey, sinceMs.toString()),
            null,
            null,
            "at_ms ASC"
        ).use { c ->
            val id = c.getColumnIndexOrThrow("profile_id")
            val at = c.getColumnIndexOrThrow("at_ms")
            val lat = c.getColumnIndexOrThrow("latency_ms")
            val jit = c.getColumnIndexOrThrow("jitter_ms")
            val success = c.getColumnIndexOrThrow("success_percent")
            val tp = c.getColumnIndexOrThrow("throughput_bps")
            val transport = c.getColumnIndexOrThrow("transport_type")
            val reset = c.getColumnIndexOrThrow("injected_reset_suspected")
            val rate = c.getColumnIndexOrThrow("injected_reset_rate")
            val saw = if (c.getColumnIndex("sawtooth_confidence") >= 0) c.getColumnIndexOrThrow("sawtooth_confidence") else -1
            while (c.moveToNext()) {
                val profileId = c.getString(id) ?: continue
                out.getOrPut(profileId) { ArrayList<BehavioralConsistency.ConsistencySample>() } += BehavioralConsistency.ConsistencySample(
                    atMs = c.getLong(at),
                    latencyMs = c.getDouble(lat),
                    jitterMs = c.getDouble(jit),
                    successPercent = c.getDouble(success),
                    throughputBps = c.getDouble(tp),
                    transportType = c.getString(transport) ?: "",
                    injectedResetSuspected = c.getInt(reset) != 0,
                    injectedResetRatePercent = c.getDouble(rate),
                    sawtoothConfidence = if (saw >= 0) c.getDouble(saw) else 0.0
                )
            }
        }
        return out
    }

    @Synchronized
    fun trimMemory() {
        runCatching {
            writableDatabase.execSQL("PRAGMA shrink_memory")
        }
    }
}

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

    private val capsByNetwork =
        ConcurrentHashMap<Network, NetworkCapabilities>()
    private val linksByNetwork =
        ConcurrentHashMap<Network, LinkProperties>()
    private val availableTransports =
        ConcurrentHashMap<Network, Set<String>>()

    private val stormGuard = DnsStormGuard()

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

                val v4Route = lp.routes.any { it.isDefaultRoute && it.destination.address is Inet4Address }
                val v6Route = lp.routes.any { it.isDefaultRoute && it.destination.address is Inet6Address }

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
                    hasIpv4 = v4Route && addresses.any { it is Inet4Address && !it.isLoopbackAddress },
                    hasIpv6 = v6Route && addresses.any {
                        it is Inet6Address && !it.isLinkLocalAddress && !it.isLoopbackAddress &&
                            !it.isSiteLocalAddress && (it.address[0].toInt() and 0xfe) != 0xfc
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
                proxyTransport = profile.transport,

                hasIpv6 = n.hasIpv6
            )
        )
        // MARBLE_TUN_MTU_CEILING_V238 — the device MTU is capped by what the tunnel's own
        // overhead leaves of the underlay path (report P1-7: a 1500 TUN over REALITY/XHTTP on a
        // 1500 underlay fragments by construction). A confirmed Auto-Tuner override (default
        // off, user-applied, reversible) wins inside the transport ceiling.
        val protocolCeiling = ProtocolMtuCeiling.forScheme(profile.scheme, profile.transport)
        val tunerOverride = if (settings.jitterAutoTunerEnabled) {
            com.marbleng.app.jitter.JitterAutoTuner.sanitizeOverride(settings.jitterTunerMtu)
        } else {
            null
        }
        val learned = learnedPathMtu(profile.id)
        val capped = minOf(recommendation.mtu, protocolCeiling)
        effectiveMtu = when {
            tunerOverride != null -> minOf(tunerOverride, protocolCeiling)
            learned in 1280..9000 -> min(capped, learned)
            else -> capped
        }
        return effectiveMtu
    }

    fun healthOf(profileId: String): NodeHealthRecord? =
        db.get(profileId, currentSnapshot().key())

    fun linkEvidenceFor(profileId: String, livePingMs: Int = 0): LinkEvidence {
        val networkKey = currentSnapshot().key()
        val live = if (livePingMs in 1..8_000) {
            LinkEvidence(rttMs = livePingMs.toDouble(), samples = 1)
        } else {
            LinkEvidence.UNKNOWN
        }
        val own = LinkEvidence.fromHealth(db.get(profileId, networkKey))
            .conservativeOf(LinkEvidence.fromHealth(db.latest(profileId)))
        return if (own.known) {
            own.conservativeOf(live)
        } else {
            networkLinkPrior(networkKey).conservativeOf(live)
        }
    }

    private fun networkLinkPrior(networkKey: String): LinkEvidence {
        val records = db.all(networkKey).values
            .filter { it.samples > 0 && it.latencyEwma in 1.0..8_000.0 && it.successEwma >= 50.0 }
        if (records.isEmpty()) return LinkEvidence.UNKNOWN
        val latencies = records.map { it.latencyEwma }.sorted()
        val jitters = records.map { it.jitterEwma.coerceIn(0.0, 2_000.0) }.sorted()
        val medianRtt = latencies[latencies.size / 2]
        val medianJitter = jitters[jitters.size / 2]
        val worstLoss = records.maxOf {
            if (it.successEwma in 1.0..100.0) 100.0 - it.successEwma else 0.0
        }
        return LinkEvidence(
            rttMs = medianRtt,
            tailRttMs = medianRtt + medianJitter * 2.0,
            jitterMs = medianJitter,
            lossPercent = worstLoss,
            samples = records.maxOf { it.samples }
        )
    }

    private fun resolverEvidenceKey(): String = "resolver-evidence:${currentSnapshot().key()}"

    private fun domainFaultKey(): String = "dns-domain-faults:${currentSnapshot().key()}"

    fun dnsDomainFaults(): List<DnsDomainFaultPolicy.DomainFault> =
        DnsDomainFaultPolicy.deserialize(prefs.getString(domainFaultKey(), "") ?: "")

    fun dnsDomainFaultSummary(nowMs: Long = System.currentTimeMillis()): String =
        DnsDomainFaultPolicy.summary(dnsDomainFaults(), nowMs)

    fun resolverEvidence(): List<ResolverEvidencePolicy.EndpointEvidence> =
        ResolverEvidencePolicy.deserialize(prefs.getString(resolverEvidenceKey(), "") ?: "")

    fun recordResolverEvidence(
        lines: Sequence<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<ResolverEvidencePolicy.EndpointEvidence> {
        val key = resolverEvidenceKey()
        val before = resolverEvidence()

        val snapshot = lines.toList()

        val faultKey = domainFaultKey()
        val faults = DnsDomainFaultPolicy.observe(snapshot.asSequence(), dnsDomainFaults(), nowMs)
        val encodedFaults = DnsDomainFaultPolicy.serialize(faults)
        if (encodedFaults != (prefs.getString(faultKey, "") ?: "")) {
            prefs.edit().putString(faultKey, encodedFaults).apply()
        }
        val attributable = snapshot.filterNot {
            DnsDomainFaultPolicy.isDomainFaultLine(it, faults, nowMs)
        }
        val next = ResolverEvidencePolicy.observe(attributable.asSequence(), before, nowMs)
        val encoded = ResolverEvidencePolicy.serialize(next)

        val deadlinesBefore = before.sumOf { it.deadlines }
        val deadlinesAfter = next.sumOf { it.deadlines }
        if (deadlinesAfter > deadlinesBefore) {
            stormGuard.recordDeadlineFailures(deadlinesAfter - deadlinesBefore, nowMs)
        }

        if (encoded != (prefs.getString(key, "") ?: "")) {
            prefs.edit().putString(key, encoded).apply()
        }
        return next
    }

    fun recordResolverSuccess(
        endpoint: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = resolverEvidenceKey()

        stormGuard.recordProvenAnswer(nowMs)
        val next = ResolverEvidencePolicy.recordSuccess(endpoint, resolverEvidence(), nowMs)
        val encoded = ResolverEvidencePolicy.serialize(next)
        if (encoded != (prefs.getString(key, "") ?: "")) {
            prefs.edit().putString(key, encoded).apply()
        }
    }

    fun dnsCandidatePool(settings: AppSettings): List<String> =
        DnsResolverCatalog.candidates(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH)

    private fun singBoxDnsCandidates(settings: AppSettings): List<String> =
        (listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH) + STOCK_DOH_RESOLVERS + "tls://9.9.9.9")
            .map(String::trim)
            .filter {
                it.startsWith("https://", ignoreCase = true) || it.startsWith("tls://", ignoreCase = true) ||
                    it.startsWith("quic://", ignoreCase = true) || it.startsWith("h3://", ignoreCase = true)
            }
            .distinctBy(ResolverEvidencePolicy::normalize)

    private fun selectSingBoxResolvers(
        candidates: List<String>,
        evidence: List<ResolverEvidencePolicy.EndpointEvidence>,
        nowMs: Long,
        seed: String,
        limit: Int
    ): List<String> {
        val survivors = ResolverEvidencePolicy.withoutExcluded(candidates, evidence, nowMs)
        val demoted = ResolverEvidencePolicy.demoted(survivors, evidence, nowMs).toSet()
        val eligible = survivors.filter { it !in demoted }

        return ResolverEvidencePolicy.order(eligible.ifEmpty { survivors }, evidence, nowMs, seed)
            .take(limit.coerceIn(1, 3))
    }

    fun singBoxResolverPool(settings: AppSettings, limit: Int = 3): List<String> {
        val candidates = singBoxDnsCandidates(settings)
        if (!settings.adaptiveDnsEnabled) {
            return candidates.filterNot { ResolverEvidencePolicy.isDomesticResolver(it) }
                .ifEmpty { candidates }.take(limit.coerceIn(1, 3))
        }
        val now = System.currentTimeMillis()
        return selectSingBoxResolvers(candidates, resolverEvidence(), now, currentSnapshot().key(), limit)
    }

    fun resolverDemotedEndpoints(settings: AppSettings): List<String> {
        if (!settings.adaptiveDnsEnabled) return emptyList()
        val nowMs = System.currentTimeMillis()
        return ResolverEvidencePolicy.demoted(
            dnsCandidatePool(settings),
            resolverEvidence(),
            nowMs
        )
    }

    fun dnsStormActive(): Boolean = stormGuard.active(System.currentTimeMillis())

    fun dnsStormRatePerMinute(): Double = stormGuard.eventsPerMinute(System.currentTimeMillis())

    private fun ipv6VerdictKey(networkKey: String): String = "ipv6-verdict|$networkKey"

    fun rememberIpv6Unhealthy() {
        prefs.edit()
            .putString(ipv6VerdictKey(currentSnapshot().key()), "${System.currentTimeMillis()}")
            .apply()
    }

    fun storedIpv6Unhealthy(): Boolean {
        val networkKey = currentSnapshot().key()
        val at = prefs.getString(ipv6VerdictKey(networkKey), "")?.toLongOrNull() ?: return false
        if (System.currentTimeMillis() - at !in 0L..IPV6_VERDICT_TTL_MS) return false
        return true
    }

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

        val latencyFirst =
            settings.workloadProfile == WorkloadProfile.INTERACTIVE ||
                settings.workloadProfile == WorkloadProfile.STABILITY ||
                (
                    settings.workloadProfile != WorkloadProfile.STREAMING &&
                        (network.transport == "cellular" || network.metered)
                    )

        val liveHealth = if (settings.healthHistoryEnabled) db.get(profileId, network.key()) else null
        val latencyRttMs = (liveHealth?.latencyEwma ?: 0.0).takeIf { it in 20.0..4_000.0 } ?: 0.0

        val latencyCapped = if (latencyFirst) {
            TunnelTuning(
                maxSessions = min(throughputTuned.maxSessions, 4096),

                tcpBufferBytes = LatencyBufferPolicy.tcpBytes(
                    downstreamKbps = network.downstreamKbps,
                    rttMs = latencyRttMs,
                    tunedBytes = throughputTuned.tcpBufferBytes
                ),
                udpBufferBytes = LatencyBufferPolicy.udpBytes(
                    downstreamKbps = network.downstreamKbps,
                    rttMs = latencyRttMs,
                    tunedBytes = throughputTuned.udpBufferBytes
                ),
                label = "${throughputTuned.label}/latency-first"
            )
        } else {
            throughputTuned
        }

        val jitterHigh = (liveHealth?.jitterEwma ?: 0.0) >= 24.0
        val pingHigh = (liveHealth?.latencyEwma ?: 0.0) >= 250.0 &&
            (liveHealth?.latencyEwma ?: 0.0) < 9000.0
        val lossy = (liveHealth?.successEwma ?: 100.0) in 1.0..84.0
        val pathCapped = if (jitterHigh || pingHigh || lossy) {
            TunnelTuning(
                maxSessions = min(latencyCapped.maxSessions, if (lossy) 2048 else 3072),
                tcpBufferBytes = min(latencyCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(latencyCapped.udpBufferBytes, if (lossy) 131_072 else 262_144),
                label = when {
                    lossy -> "${latencyCapped.label}/loss-pressure"
                    pingHigh -> "${latencyCapped.label}/ping-pressure"
                    else -> "${latencyCapped.label}/jitter-pressure"
                }
            )
        } else latencyCapped

        val stormCapped = if (dnsStormActive()) {
            TunnelTuning(
                maxSessions = min(pathCapped.maxSessions, 2048),
                tcpBufferBytes = min(pathCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(pathCapped.udpBufferBytes, 131_072),
                label = "${pathCapped.label}/dns-storm"
            )
        } else pathCapped

        return if (thermal < 0.55) {
            TunnelTuning(
                maxSessions = min(stormCapped.maxSessions, 2048),
                tcpBufferBytes = min(stormCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(stormCapped.udpBufferBytes, 262_144),
                label = "${stormCapped.label}/thermal"
            )
        } else stormCapped
    }

    @Volatile
    var ipFamilyEvidence: ((String, Int) -> IpFamilyScan?)? = null

    fun familyResolution(
        profile: ProxyProfile,
        settings: AppSettings,
        network: NetworkSnapshot = currentSnapshot(),
        nowMs: Long = System.currentTimeMillis()
    ): FamilyResolution {
        val underlayHasIpv6 = network.hasIpv6 || AddressFamilyPolicy.underlayHasIpv6()
        val scan = runCatching { ipFamilyEvidence?.invoke(profile.host, profile.port) }
            .getOrNull()
            ?.takeIf { it.usableOn(network.key(), nowMs) }
        return Ipv6FallbackLadder.resolve(
            requested = settings.addressFamilyMode,
            evidence = Ipv6FallbackLadder.evidenceFor(profile.host, scan, underlayHasIpv6),
            strict = settings.strictAddressFamily
        )
    }

    fun effectiveSettings(
        profile: ProxyProfile,
        base: AppSettings,
        withAcceleration: Boolean = true
    ): AppSettings {
        startMonitoring()

        val n = snapshot
        val nowMs = System.currentTimeMillis()
        val storm = stormGuard.active(nowMs)
        val health = db.get(profile.id, n.key())
        val link = LinkEvidence.fromHealth(health)
            .conservativeOf(networkLinkPrior(n.key()))
        val noisy = ProtocolFitness.noisy(link)

        val effectivePreferIpv6 =
            base.ipv6Enabled &&
                base.preferIpv6 &&
                n.hasIpv6

        val ipRace = SmartIpRacePolicy.decide(
            n,
            health,
            base.copy(preferIpv6 = effectivePreferIpv6)
        )

        val measuredV6Healthy: Boolean? = null
        val forceIpv4First = false
        val queryStrategy = base.dnsQueryStrategy

        val dnsOrdered = preferredDnsOrder(base)

        val resolverPool = dnsCandidatePool(base)
        val endpointEvidence = if (base.adaptiveDnsEnabled) resolverEvidence() else emptyList()
        val dnsDemoted = if (base.adaptiveDnsEnabled) {
            ResolverEvidencePolicy.demoted(resolverPool, endpointEvidence, nowMs)
        } else {
            emptyList()
        }

        val dnsExcluded = if (base.adaptiveDnsEnabled) {
            ResolverEvidencePolicy.excluded(resolverPool, endpointEvidence, nowMs)
        } else {
            emptyList()
        }

        val dnsEmissionCandidates = if (base.adaptiveDnsEnabled) {
            val excluded = dnsExcluded.map(ResolverEvidencePolicy::normalize).toSet()
            val configured = listOf(dnsOrdered.first, dnsOrdered.second)
                .map(String::trim)
                .filter(DnsResolverCatalog::isXrayBootstrapSafe)
                .filterNot { ResolverEvidencePolicy.isDomesticResolver(it) }
                .filterNot { ResolverEvidencePolicy.normalize(it) in excluded }
                .distinctBy(ResolverEvidencePolicy::normalize)
            val configuredOrFallback = configured.ifEmpty { DnsResolverCatalog.STOCK_DOH.take(2) }
            (configuredOrFallback + DnsResolverCatalog.STOCK_DOH)
                .filterNot { ResolverEvidencePolicy.isDomesticResolver(it) }
                .filterNot { ResolverEvidencePolicy.normalize(it) in excluded }
                .distinctBy(ResolverEvidencePolicy::normalize)
        } else {
            emptyList()
        }
        val dnsEmissionOrder = if (base.adaptiveDnsEnabled) {
            ResolverEvidencePolicy.selectForEmission(
                dnsEmissionCandidates,
                endpointEvidence,
                nowMs,
                seed = n.key(),
                limit = 3
            )
        } else {
            emptyList()
        }
        val dnsParallel = base.adaptiveDnsEnabled && dnsEmissionOrder.size >= 2 && (
            storm || ResolverEvidencePolicy.parallelQueryJustified(
                dnsEmissionOrder, endpointEvidence, nowMs
            )
        )

        val singBoxEmissionOrder = if (base.adaptiveDnsEnabled) {
            selectSingBoxResolvers(
                singBoxDnsCandidates(base), endpointEvidence, nowMs, n.key(), limit = 3
            )
        } else {
            emptyList()
        }
        val singBoxDnsParallel = base.adaptiveDnsEnabled && singBoxEmissionOrder.size >= 2 && (
            storm || ResolverEvidencePolicy.parallelQueryJustified(
                singBoxEmissionOrder, endpointEvidence, nowMs
            )
        )

        val familyResolution = familyResolution(profile, base, n)
        val familyBase = Ipv6FallbackLadder.apply(
            base.copy(dnsQueryStrategy = queryStrategy),
            familyResolution
        )
        val familyPreference = AddressFamilyPolicy.preference(
            settings = familyBase,
            underlayHasIpv6 = n.hasIpv6,
            measuredV6Healthy = measuredV6Healthy
        )

        val tuned = familyBase.copy(
            dnsQueryStrategy = AddressFamilyPolicy.dnsQueryStrategy(
                familyBase,
                familyPreference,
                underlayHasIpv6 = n.hasIpv6
            ),
            preferIpv6 = if (forceIpv4First) {
                false
            } else {
                AddressFamilyPolicy.prioritizeIpv6(
                    preference = familyPreference,
                    underlayHasIpv6 = n.hasIpv6,
                    measuredV6Healthy = measuredV6Healthy
                )
            },
            dnsPrimaryDoH = dnsOrdered.first,
            dnsSecondaryDoH = dnsOrdered.second,

            happyEyeballsTryDelayMs = if (forceIpv4First || ipRace.tryDelayMs <= 0) {
                0
            } else {
                ipRace.tryDelayMs.coerceIn(AddressFamilyPolicy.MIN_TRY_DELAY_MS, AddressFamilyPolicy.MAX_TRY_DELAY_MS)
            },
            happyEyeballsMaxConcurrent = when {
                measuredV6Healthy == false -> 1
                forceIpv4First -> 1
                else -> ipRace.maxConcurrentTry
            },

            measuredIpv6Unhealthy = measuredV6Healthy == false,
            measuredDnsDemotedEndpoints = dnsDemoted.joinToString(","),
            measuredDnsExcludedEndpoints = dnsExcluded.joinToString(","),
            measuredDnsParallel = dnsParallel,
            measuredDnsResolverOrder = dnsEmissionOrder.joinToString(","),
            measuredSingBoxDnsParallel = singBoxDnsParallel
        )

        val accelerated =
            if (withAcceleration && base.connectTuningEnabled) {
                acceleration(profile.id)?.applyTo(tuned) ?: tuned
            } else {
                tuned
            }

        val shielded = IranShield.apply(accelerated, profile, iranState, iranGeoIpReady)
        val healed = DpiEvasionPolicy.heal(
            shielded,
            DpiEvasionPolicy.PathEvidence(
                pingMs = health?.latencyEwma?.toInt() ?: 0,
                jitterMs = health?.jitterEwma?.toInt() ?: 0,
                successPercent = health?.successEwma?.toInt() ?: 100,
                samples = health?.samples ?: 0
            ),
            iranState
        )
        return healed
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

    fun healthSnapshot(): Map<String, NodeHealthRecord> =
        db.all(currentSnapshot().key())

    fun knownProfileIds(): Set<String> = healthSnapshot().keys

    private fun currentCarrierContext(): String =
        currentSnapshot().label.take(80)

    fun recordReachability(
        profileId: String,
        summary: MultiVectorReachability.ReachabilitySummary,
        transportType: String = ""
    ) {
        val n = currentSnapshot()
        val old = db.get(profileId, n.key())
        val rate = summary.injectedResetRatePercent.coerceIn(0.0, 100.0)
        val newRate = if (old == null || old.injectedResetRate <= 0.0) rate
        else old.injectedResetRate * 0.7 + rate * 0.3
        val now = System.currentTimeMillis()
        db.updateLayerFields(
            profileId,
            n.key(),
            ContentValues().apply {
                put("injected_reset_rate", newRate)
                put("carrier_context", currentCarrierContext())
                put("last_seen_at", now)
            },
            old
        )
        db.insertObservation(
            profileId,
            n.key(),
            BehavioralConsistency.ConsistencySample(
                atMs = now,
                latencyMs = summary.medianTtfbMs.takeIf { it > 0.0 } ?: 0.0,
                successPercent = if (summary.signals.isEmpty()) 0.0
                else summary.reachableSamples * 100.0 / summary.signals.size,
                transportType = transportType,
                injectedResetSuspected = summary.injectedResetCount > 0,
                injectedResetRatePercent = rate
            )
        )
    }

    fun recordTransportVerdict(
        profileId: String,
        verdict: ProtocolFingerprintAwareVerifier.TransportVerdict
    ) {
        val n = currentSnapshot()
        val old = db.get(profileId, n.key())
        val now = System.currentTimeMillis()
        val scores = HashMap(old?.transportSpecificScores ?: emptyMap())
        val transportScore = transportScoreOf(verdict)
        scores[verdict.transportType] =
            if (scores.containsKey(verdict.transportType)) {
                scores.getValue(verdict.transportType) * 0.65 + transportScore * 0.35
            } else {
                transportScore
            }
        val sawEwma = if (old == null || old.throttlePatternConfidence <= 0.0) {
            verdict.sawtoothConfidence
        } else {
            old.throttlePatternConfidence * 0.7 + verdict.sawtoothConfidence * 0.3
        }
        db.updateLayerFields(
            profileId,
            n.key(),
            ContentValues().apply {
                put("transport_specific_scores", encodeScoreMap(scores))
                put("throttle_pattern_confidence", sawEwma.coerceIn(0.0, 1.0))
                put("injected_reset_rate", if (verdict.injectedResetSuspected) 1.0 else 0.0)
                put("carrier_context", currentCarrierContext())
                put("last_seen_at", now)
            },
            old
        )
        db.insertObservation(
            profileId,
            n.key(),
            BehavioralConsistency.ConsistencySample(
                atMs = now,
                latencyMs = verdict.latencyMs.takeIf { it > 0.0 } ?: 0.0,
                jitterMs = verdict.jitterMs,
                successPercent = verdict.successPercent.toDouble(),
                throughputBps = verdict.throughputBps,
                transportType = verdict.transportType,
                injectedResetSuspected = verdict.injectedResetSuspected,
                injectedResetRatePercent = if (verdict.injectedResetSuspected) 100.0 else 0.0,
                sawtoothConfidence = verdict.sawtoothConfidence
            )
        )
    }

    private fun transportScoreOf(v: ProtocolFingerprintAwareVerifier.TransportVerdict): Double {
        val reliability = v.successPercent.coerceIn(0, 100).toDouble()
        val latencyScore = if (v.latencyMs > 0.0) {
            100.0 * kotlin.math.exp(-v.latencyMs / 250.0)
        } else {
            0.0
        }
        val jitterScore = if (v.jitterMs > 0.0) 100.0 * kotlin.math.exp(-v.jitterMs / 45.0) else 50.0
        var score = 0.45 * reliability + 0.35 * latencyScore + 0.20 * jitterScore
        score -= v.sawtoothConfidence * 40.0
        if (v.injectedResetSuspected) score -= 30.0
        if (v.silentTimeoutSuspected) score -= 20.0
        return score.coerceIn(0.0, 100.0)
    }

    fun recordAttribution(
        profileId: String,
        result: CausalAttribution.AttributionResult
    ) {
        if (result.confidence < CausalAttribution.MIN_CONFIDENCE &&
            result.cause != CausalAttribution.AttributedCause.NATIONAL_FILTERING_EVENT
        ) {
            return
        }
        val n = currentSnapshot()
        val old = db.get(profileId, n.key())
        db.updateLayerFields(
            profileId,
            n.key(),
            ContentValues().apply {
                put("attributed_cause_last", CausalAttribution.encode(result.cause))
                put("carrier_context", currentCarrierContext())
                put("last_seen_at", System.currentTimeMillis())
            },
            old
        )
    }

    fun attributedCauseFor(profileId: String): CausalAttribution.AttributedCause? =
        CausalAttribution.parseCause(healthOf(profileId)?.attributedCauseLast)

    fun consistencyReport(profileId: String): BehavioralConsistency.ConsistencyReport {
        val n = currentSnapshot()
        val observations = db.observations(profileId, n.key(), 0L)
        return BehavioralConsistency.evaluate(observations)
    }

    fun scoreForCurrentTimeWindow(profileId: String): Double =
        windowScoreFromRecord(healthOf(profileId) ?: return 50.0)

    private fun windowScoreFromRecord(record: NodeHealthRecord): Double {
        val bucket = BehavioralConsistency.timeOfDayBucket(System.currentTimeMillis())
        val bucketKey = BehavioralConsistency.bucketKey(bucket)
        val bucketLatency = record.timeOfDayBucketScores[bucketKey]
        val bucketScore = if (bucketLatency == null || bucketLatency <= 0.0) {
            val latency = record.latencyEwma.takeIf { it in 1.0..8_000.0 } ?: return 50.0
            (100.0 * kotlin.math.exp(-latency / 250.0)) * 0.85
        } else {
            100.0 * kotlin.math.exp(-bucketLatency / 250.0)
        }
        val transportBest = record.transportSpecificScores.values.maxOrNull() ?: 0.0

        val stability = if (record.samples >= 3) 0.75 else 0.5
        return (0.5 * bucketScore + 0.3 * transportBest + 0.2 * stability * 100.0)
            .coerceIn(0.0, 100.0)
    }

    fun preferredTransportFor(profileId: String): String {
        val record = healthOf(profileId) ?: return ""
        return record.transportSpecificScores
            .maxByOrNull { it.value }
            ?.key
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }

    fun detectNationalEvent(
        profiles: Map<String, ProxyProfile> = emptyMap()
    ): CausalAttribution.AttributionResult? {
        val n = currentSnapshot()
        val since = System.currentTimeMillis() - CausalAttribution.SIMULTANEITY_WINDOW_MS
        val observationsByProfile = db.networkObservations(n.key(), since)
        if (observationsByProfile.size < 5) return null
        val health = healthSnapshot()
        val observations = observationsByProfile.mapNotNull { (profileId, series) ->
            val latest = series.lastOrNull() ?: return@mapNotNull null
            val record = health[profileId]
            val profile = profiles[profileId]
            CausalAttribution.ServerObservation(
                profileId = profileId,
                ip = profile?.host?.takeIf { it.isNotBlank() } ?: "",
                asn = "",
                carrier = record?.carrierContext?.ifBlank { null } ?: n.label,
                transportType = latest.transportType.ifBlank {
                    record?.transportSpecificScores?.keys?.maxByOrNull { record.transportSpecificScores.getValue(it) } ?: ""
                },
                dropped = latest.successPercent <= 25.0,
                injectedResetSuspected = latest.injectedResetSuspected,
                sawtoothConfidence = latest.sawtoothConfidence,
                priorEvidence = (record?.samples ?: 0) >= 3 &&
                    (record?.lastSuccessAt ?: 0L) < (series.firstOrNull()?.atMs ?: Long.MAX_VALUE),
                windowStartMs = series.first().atMs,
                windowEndMs = series.last().atMs
            )
        }
        return CausalAttribution.attribute(observations)
    }

    fun recordLiveObservation(
        profileId: String,
        latencyMs: Double,
        jitterMs: Double,
        successPercent: Double,
        transportType: String = "",
        injectedResetSuspected: Boolean = false
    ) {
        if (profileId.isBlank() || latencyMs <= 0.0) return
        val n = currentSnapshot()
        val now = System.currentTimeMillis()
        val old = db.get(profileId, n.key())
        val bucket = BehavioralConsistency.timeOfDayBucket(now)
        val bucketKey = BehavioralConsistency.bucketKey(bucket)
        val buckets = HashMap(old?.timeOfDayBucketScores ?: emptyMap())
        val previous = buckets[bucketKey]
            ?: (old?.latencyEwma?.takeIf { it in 1.0..8_000.0 } ?: latencyMs)
        val alpha = BehavioralConsistency.BUCKET_EWMA_ALPHA
        buckets[bucketKey] = previous * (1.0 - alpha) + latencyMs * alpha
        db.updateLayerFields(
            profileId,
            n.key(),
            ContentValues().apply {
                put("time_of_day_bucket_scores", encodeScoreMap(buckets))
                put("carrier_context", currentCarrierContext())
                put("last_seen_at", now)
            },
            old
        )
        db.insertObservation(
            profileId,
            n.key(),
            BehavioralConsistency.ConsistencySample(
                atMs = now,
                latencyMs = latencyMs,
                jitterMs = jitterMs,
                successPercent = successPercent.coerceIn(0.0, 100.0),
                transportType = transportType,
                injectedResetSuspected = injectedResetSuspected
            )
        )
    }

    fun throttleConfidenceFor(profileId: String): Double =
        healthOf(profileId)?.throttlePatternConfidence?.coerceIn(0.0, 1.0) ?: 0.0

    fun attributionTick(
        profiles: Map<String, ProxyProfile> = emptyMap()
    ): CausalAttribution.AttributionResult? {
        val result = detectNationalEvent(profiles) ?: return null
        if (result.confidence >= CausalAttribution.MIN_CONFIDENCE) {
            healthSnapshot().keys.forEach { profileId ->
                recordAttribution(profileId, result)
            }
            lastNationalAttribution = result
        }
        return result
    }

    @Volatile private var lastNationalAttribution: CausalAttribution.AttributionResult? = null

    fun nationalEventResult(): CausalAttribution.AttributionResult? = lastNationalAttribution

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
        if (!settings.healthHistoryEnabled) {
            return 50.0
        }

        val h = record ?: return 50.0
        val nowMs = System.currentTimeMillis()

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

        val evidenceCount = max(1, h.samples).toDouble()
        val probability = h.successEwma.coerceIn(0.0, 100.0) / 100.0
        val z = 1.28155
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
            reliability * 0.50 + udp * 0.18 + latency * 0.14 + jitter * 0.18

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
                    nowMs -
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

        val failureAgeHours = (nowMs - h.lastSeenAt).coerceAtLeast(0L) / 3_600_000.0
        val effectiveFailureStreak = (h.failureStreak - (failureAgeHours / 6.0).toInt()).coerceAtLeast(0)
        val failurePenalty =
            effectiveFailureStreak
                .coerceAtMost(6) *
                7.5

        val freshSuccessBonus =
            if (h.lastSuccessAt <= 0L) {
                0.0
            } else {
                val minutesSinceSuccess = (nowMs - h.lastSuccessAt) / 60_000.0
                when {
                    minutesSinceSuccess <= 30.0 -> 6.0
                    minutesSinceSuccess <= 120.0 -> 3.0
                    else -> 0.0
                }
            }

        return (
            confidenceAdjusted +
                freshSuccessBonus -
                stalePenalty -
                failurePenalty
            ).coerceIn(0.0, 100.0)
    }

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

    fun rankingScore(
        profile: ProxyProfile,
        settings: AppSettings
    ): Double =
        predictedScore(profile, settings) +
            IranShield.profileBias(profile, iranState) +
            ProtocolFitness.bias(profile, networkLinkPrior(currentSnapshot().key()))

    fun rankingScores(
        profiles: List<ProxyProfile>,
        settings: AppSettings,
        health: Map<String, NodeHealthRecord> = healthSnapshot()
    ): Map<String, Double> {
        val out = HashMap<String, Double>(profiles.size * 2)
        val priors = if (health.size < profiles.size) db.latestAll() else emptyMap()

        val link = networkLinkPrior(currentSnapshot().key())
        profiles.forEach { profile ->
            if (!out.containsKey(profile.id)) {
                val exact = health[profile.id]
                val predicted =
                    predictedScoreOf(exact ?: priors[profile.id], settings, if (exact == null) 0.35 else 1.0) +
                        IranShield.profileBias(profile, iranState) +
                        ProtocolFitness.bias(profile, link)

                out[profile.id] = if (exact != null) {
                    predicted * 0.6 + windowScoreFromRecord(exact) * 0.4
                } else {
                    predicted
                }
            }
        }
        return out
    }

    fun orderCandidates(
        profiles: List<ProxyProfile>,
        settings: AppSettings
    ): List<ProxyProfile> {
        if (profiles.isEmpty()) {
            return profiles
        }

        if (nationalEventResult()?.rankingFreeze == true) {
            return profiles
        }

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

    fun onMemoryPressure(level: Int) {
        if (level >= 20) {
            accelerationCache.clear()
        }
        if (level >= 40) {
            val active = activeNetwork
            if (active != null) {
                capsByNetwork.keys.retainAll(setOf(active))
                linksByNetwork.keys.retainAll(setOf(active))
                availableTransports.keys.retainAll(setOf(active))
            }
            db.trimMemory()
        }
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
        val storm = dnsStormActive()

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
            dnsStormActive = storm,
            familyLock = "",
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
                fullTun && tunUp && (ipv6RouteCaptured || !settings.ipv6Enabled),
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
        settings: AppSettings,
        link: LinkEvidence = LinkEvidence.UNKNOWN
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

        val auditTimeoutMs = LinkDeadlinePolicy.httpsProbeTimeoutMs(
            link,
            floorMs = 2_500L,
            ceilingMs = 8_000L
        ).toInt().coerceIn(1_000, 30_000)
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
                            auditTimeoutMs,
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

        timings.forEach { (endpoint, _) ->
            recordResolverSuccess(endpoint)
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
        val nowMs = System.currentTimeMillis()

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
                nowMs - learnedAt <= DNS_WINNER_TTL_MS

        val evidence = resolverEvidence()

        val ordered = ResolverEvidencePolicy.orderForEmission(
            ResolverEvidencePolicy.withoutExcluded(dnsCandidatePool(settings), evidence, nowMs),
            evidence,
            nowMs,
            seed = key
        )
        if (ordered.size < 2) {
            return settings.dnsPrimaryDoH to
                settings.dnsSecondaryDoH
        }

        val promoted = if (fresh && winner.isNotBlank() &&
            !ResolverEvidencePolicy.isDemoted(winner, evidence, nowMs)
        ) {
            val winnerIndex = ordered.indexOfFirst {
                ResolverEvidencePolicy.normalize(it) == ResolverEvidencePolicy.normalize(winner)
            }
            if (winnerIndex > 0) {
                listOf(ordered[winnerIndex]) +
                    ordered.filterIndexed { index, _ -> index != winnerIndex }
            } else {
                ordered
            }
        } else {
            ordered
        }

        return promoted[0] to promoted[1]
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

        const val IPV6_VERDICT_TTL_MS = 24L * 60L * 60L * 1000L

        val STOCK_DOH_RESOLVERS = DnsResolverCatalog.STOCK_DOH

        const val DNS_WINNER_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
