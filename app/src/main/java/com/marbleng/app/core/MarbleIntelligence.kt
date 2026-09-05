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
    /** MARBLE_INTELLIGENCE_V141 — live DNS storm verdict for the current physical network. */
    val dnsStormActive: Boolean = false,
    /** MARBLE_INTELLIGENCE_V141 — non-empty when the family plan is locked to IPv4 by evidence. */
    val familyLock: String = "",
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
 * MARBLE_INTELLIGENCE_V141 — protocol fitness under a measured noisy link.
 *
 * The attached Turkey-14 vs Netherlands-3 log pair is the specification: the same app on the same
 * network kept a stable multi-hour session on a hysteria2 exit, while a VLESS+xhttp exit produced
 * jitter-driven teardowns, DNS error buffers and six forced restarts in seven minutes. Transport
 * shape is evidence, and ranking/recovery must consume it:
 *
 *  - QUIC-native transports (hysteria2/hysteria/tuic) carry their own loss recovery and pacing,
 *    so they are the right answer on jitter/loss exactly when a TCP-based proxy transport starts
 *    folding. That is the green reference's shape.
 *  - VLESS+xhttp multiplexes several streams over one h2/h3 connection and re-chunks TLS records,
 *    so a lossy link stalls every stream behind the slowest record (head-of-line blocking). Under
 *    noise it is both fragile and memory-hungry: error buffers queue behind the stalled stream.
 *  - Plain TCP transports with TLS (trojan/vless+vision/vmess) are neutral-to-good.
 *
 * The bias is additive and bounded, exactly like [IranShield.profileBias]: it may reorder
 * candidates on a noisy link, but it can never push a dead node above a proven one, because the
 * reliability term still dominates [MarbleIntelligence.predictedScoreOf].
 */
object ProtocolFitness {

    /** A link is "noisy" when measured jitter/loss/rtt say reassembly and retransmits dominate. */
    fun noisy(link: LinkEvidence): Boolean = link.known && (
        link.jitterMs >= 20.0 ||
            link.lossPercent >= 6.0 ||
            (link.rttMs >= 250.0 && link.jitterMs >= 12.0)
        )

    /**
     * Additive ranking bias for one profile on the given link. Positive favours, negative
     * penalises, and the magnitude is capped so history still wins on a clean link.
     */
    fun bias(profile: ProxyProfile, link: LinkEvidence): Double {
        val scheme = profile.scheme.trim().lowercase()
        val transport = profile.transport.trim().lowercase()
        if (scheme.isEmpty()) return 0.0
        val onNoisy = noisy(link)

        // QUIC-native, FEC/ARQ-carrying transports: the green reference's shape.
        if (scheme in setOf("hysteria2", "hysteria", "tuic")) return if (onNoisy) 12.0 else 3.0
        if (scheme == "wireguard") return if (onNoisy) 5.0 else 1.0

        // Multiplexed-over-one-connection transports: head-of-line blocking amplifies loss.
        if (transport in setOf("xhttp", "splithttp", "httpupgrade", "h2", "h2c", "quic")) {
            return if (onNoisy) -12.0 else -2.0
        }
        if (transport == "websocket") return if (onNoisy) -7.0 else -1.0

        // Stream transports over TCP with TLS: neutral baseline, vision/reality slightly favoured
        // because their first-flight shape is the least reassembly-sensitive.
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

    /**
     * Multiplexing on top of a multiplexed/record-chunked transport under noise stacks two
     * head-of-line blockers; the config must not arm it on a measured noisy link.
     */
    fun prefersMuxOff(profile: ProxyProfile, link: LinkEvidence): Boolean {
        if (!noisy(link)) return false
        val transport = profile.transport.trim().lowercase()
        return transport in setOf("xhttp", "splithttp", "httpupgrade", "h2", "h2c", "websocket")
    }
}

/**
 * MARBLE_INTELLIGENCE_V141 — DNS storm detector.
 *
 * The log pair defines the healthy and broken regimes precisely:
 *
 *  - healthy (Netherlands-3, v7.0.4): about one attributed DoH failure every six minutes
 *    (~0.17/min) while the tunnel stays up for hours;
 *  - broken (Turkey-14, v7.0.9): six `context deadline exceeded` failures per minute against
 *    1.1.1.1/8.8.8.8/9.9.9.9, apps receiving no IPs and `rejected proxy/socks` socket closures,
 *    worst windows reaching 29/min.
 *
 * Endpoint demotion (V134) already removes a *decisively failing* resolver from the order, but a
 * storm is a property of the moment, not of one endpoint: when every resolver in the pool is
 * missing its budget at once, the correct responses are to race the pool instead of walking it
 * serially, to stop asking for the address family that doubles the failure surface, and to stop
 * the ranking engine from treating DNS-induced socket closures as node failures (which is what
 * turned a resolver problem into six forced restarts).
 *
 * The detector is a rolling window over *attributed deadline events* fed from
 * [MarbleIntelligence.recordResolverEvidence]. It arms fast (three events inside five minutes —
 * already 3.5x the healthy rate) and stands down slowly (at most one event inside ten minutes),
 * because flipping the query mode on every blip is its own instability.
 */
private class DnsStormGuard {

    private val events = ArrayDeque<Long>()

    @Synchronized
    fun recordDeadlineFailures(count: Int, nowMs: Long) {
        repeat(count.coerceIn(0, 64)) { events.addLast(nowMs) }
        trim(nowMs)
    }

    @Synchronized
    fun armMs(nowMs: Long): Long? {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STORM_ARM_WINDOW_MS }
        return if (recent >= STORM_ARM_EVENTS) nowMs else null
    }

    /** True while the storm regime is active: armed recently and not yet quiet for the stand-down window. */
    @Synchronized
    fun active(nowMs: Long): Boolean {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STAND_DOWN_WINDOW_MS }
        return recent > STORM_QUIET_EVENTS
    }

    /** Attributed deadline events per minute over the arm window, for diagnostics. */
    @Synchronized
    fun eventsPerMinute(nowMs: Long): Double {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STORM_ARM_WINDOW_MS }
        return recent / (STORM_ARM_WINDOW_MS / 60_000.0)
    }

    @Synchronized
    private fun trim(nowMs: Long) {
        val horizon = maxOf(STORM_ARM_WINDOW_MS, STAND_DOWN_WINDOW_MS)
        while (events.isNotEmpty() && nowMs - events.first() > horizon) events.removeFirst()
        while (events.size > 512) events.removeFirst()
    }

    companion object {
        const val STORM_ARM_WINDOW_MS = 5L * 60_000L
        const val STORM_ARM_EVENTS = 3
        const val STAND_DOWN_WINDOW_MS = 10L * 60_000L
        const val STORM_QUIET_EVENTS = 1
    }
}

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
 *
 * MARBLE_INTELLIGENCE_V141 — rewritten around the five failure classes of the attached
 * Turkey-14 (v7.0.9) vs Netherlands-3 (v7.0.4) log pair:
 *
 *  1. **DNS crisis** — [DnsStormGuard] watches the rate of attributed DoH deadline failures
 *     (healthy ≈ 1 per 6 min; broken 6/min, worst 29/min). While a storm is active the resolver
 *     pool is raced (`measuredDnsParallel`), the family plan collapses to IPv4, and tunnel
 *     buffers shrink so error queues cannot grow into the PSS regression.
 *  2. **IPv6 penalty** — "IPv6 preferred, IPv4 raced after 60 ms" is eliminated by evidence:
 *     an unstable race, a stored per-network verdict, a noisy link or a DNS storm locks the plan
 *     to IPv4-first with a zero race delay, and the verdict is persisted per network for 24 h so
 *     it survives reconnects.
 *  3. **Protocol fitness** — [ProtocolFitness] biases ranking and recovery toward QUIC-native
 *     transports (the green reference ran hysteria2) and away from VLESS+xhttp on measured noisy
 *     links, and disables multiplexing under the same evidence.
 *  4. **Memory** — tunnel sizing gains a DNS-storm guard next to the existing loss/thermal
 *     caps; the DNS error buffers that inflated PSS from 98 to 104 MB are bounded again.
 *  5. **Reconnect storms** — the node that just carried traffic successfully gets a bounded
 *     fresh-success bonus, so a DNS-induced socket closure no longer reshuffles the candidate
 *     list behind nodes with no evidence — the direct antidote to six forced restarts in seven
 *     minutes.
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

    /** MARBLE_INTELLIGENCE_V141 — attributed-deadline storm detector for the current network. */
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

    /**
     * MARBLE_LINK_DEADLINE_V134 — the one place that turns stored health into deadline evidence.
     *
     * V133 derived every budget from this and it fixed the live tunnel, but the source it read was
     * narrower than the evidence the store actually holds: the per-node record **for this physical
     * network**, and nothing else. On the first connect of a session that record does not exist yet
     * (it is written by the measurements the session is about to make), the live ping is still zero,
     * and the config was therefore emitted with the legacy 1350/1650 ms budgets — the exact numbers
     * V133 proved cannot survive a slow route. A node that had been measured at 444 ms ten minutes
     * earlier on another network, and a network where every other node measures 400 ms, both knew
     * better and were not asked.
     *
     * The order below is evidence strength, and the merge is [LinkEvidence.conservativeOf] because
     * deadline sizing is asymmetric: a generous budget costs one slow failure detection, a truncated
     * one costs every lookup on the route.
     *
     *  1. this node on this network (authoritative);
     *  2. this node on a previous network (a prior about the node's own path);
     *  3. only when the node has never been measured: the round-trip scale of *this network*, from
     *     the nodes that actually worked on it;
     *  4. the last honest live ping.
     *
     * With none of them the result is [LinkEvidence.UNKNOWN] and every budget stays exactly as it
     * was before this policy existed.
     */
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

    /**
     * Round-trip scale of a physical network, taken from the nodes that were measured working on it.
     *
     * A node that never connected says nothing about the link, so records below a 50 % success EWMA
     * are excluded: a dead server must not inflate the deadlines of a healthy one. Medians rather
     * than means, because one 8 s outlier is a broken node, not a slow network.
     */
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

    // ------------------------------------------------------------------ resolver evidence + storm

    private fun resolverEvidenceKey(): String = "resolver-evidence:${currentSnapshot().key()}"

    /**
     * Encrypted resolver failures attributed to an endpoint and scoped to the current physical
     * network. Empty means "nothing has been observed failing here", never "all resolvers are good".
     */
    fun resolverEvidence(): List<ResolverEvidencePolicy.EndpointEvidence> =
        ResolverEvidencePolicy.deserialize(prefs.getString(resolverEvidenceKey(), "") ?: "")

    /**
     * Fold raw core-log lines into the persisted evidence set, and feed every *new* attributed
     * deadline into the storm detector.
     *
     * Only attributed, decisive failures are stored; shutdown-safe cancellations and closed-pipe
     * teardown lines are dropped by [ResolverEvidencePolicy.observe], so a reconnect can never
     * demote a resolver for having been interrupted — and can never arm the storm guard either.
     */
    fun recordResolverEvidence(
        lines: Sequence<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<ResolverEvidencePolicy.EndpointEvidence> {
        val key = resolverEvidenceKey()
        val before = resolverEvidence()
        val next = ResolverEvidencePolicy.observe(lines, before, nowMs)
        val encoded = ResolverEvidencePolicy.serialize(next)

        // MARBLE_INTELLIGENCE_V141 — the storm is measured on the *delta*, never on the stored
        // counters: decay must not look like recovery, and a re-observed old failure must not
        // look like a new one.
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

    /**
     * A proven answer clears the pressure on that endpoint immediately.
     *
     * This is the recovery half of the loop. Without it a demotion would only ever expire by TTL,
     * and a resolver that came back would keep its demoted rank for half an hour.
     */
    fun recordResolverSuccess(
        endpoint: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = resolverEvidenceKey()
        val next = ResolverEvidencePolicy.recordSuccess(endpoint, resolverEvidence(), nowMs)
        val encoded = ResolverEvidencePolicy.serialize(next)
        if (encoded != (prefs.getString(key, "") ?: "")) {
            prefs.edit().putString(key, encoded).apply()
        }
    }

    /** Every encrypted resolver Marble may emit: the user's pair first, then independent stock. */
    fun dnsCandidatePool(settings: AppSettings): List<String> =
        (listOf(settings.dnsPrimaryDoH, settings.dnsSecondaryDoH) + STOCK_DOH_RESOLVERS)
            .map { it.trim() }
            .filter { it.startsWith("https://") }
            .distinctBy { ResolverEvidencePolicy.normalize(it) }

    /** Endpoints of [settings]' resolver pool that are currently demoted on this network. */
    fun resolverDemotedEndpoints(settings: AppSettings): List<String> {
        if (!settings.adaptiveDnsEnabled) return emptyList()
        val nowMs = System.currentTimeMillis()
        return ResolverEvidencePolicy.demoted(
            dnsCandidatePool(settings),
            resolverEvidence(),
            nowMs
        )
    }

    /**
     * MARBLE_INTELLIGENCE_V141 — live DNS storm verdict for the current physical network.
     *
     * Healthy is ~1 attributed deadline per 6 minutes (0.17/min); the broken log ran 6/min with
     * worst windows at 29/min. The guard arms at 3 events per 5 minutes — already 3.5× the healthy
     * rate — and stands down only after ten quiet minutes, so the query mode never flaps.
     */
    fun dnsStormActive(): Boolean = stormGuard.active(System.currentTimeMillis())

    /** Attributed deadline rate per minute for diagnostics; 0.0 when the window is calm. */
    fun dnsStormRatePerMinute(): Double = stormGuard.eventsPerMinute(System.currentTimeMillis())

    // ------------------------------------------------------------------ per-network IPv6 verdict

    private fun ipv6VerdictKey(networkKey: String): String = "ipv6-verdict|$networkKey"

    /**
     * MARBLE_INTELLIGENCE_V141 — persist the measured IPv6 verdict for this physical network.
     *
     * "IPv6 preferred, IPv4 raced after 60 ms" cost every connection of the broken log a 60 ms
     * penalty and a wasted socket on a family the link could not carry. An unstable race is
     * remembered for 24 h so reconnects and app restarts do not re-learn it the hard way; a
     * healthy verdict is deliberately *not* persisted, because v6 health must be re-proven by
     * [SmartIpRacePolicy] on every session — only the pathology is sticky.
     */
    fun rememberIpv6Unhealthy() {
        prefs.edit()
            .putString(ipv6VerdictKey(currentSnapshot().key()), "${System.currentTimeMillis()}")
            .apply()
    }

    /** Stored verdict for the current network: false = measured unhealthy, true/unknown = keep automatic. */
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
        val jitterHigh = (liveHealth?.jitterEwma ?: 0.0) >= 24.0
        val pingHigh = (liveHealth?.latencyEwma ?: 0.0) >= 250.0 &&
            (liveHealth?.latencyEwma ?: 0.0) < 9000.0
        val lossy = (liveHealth?.successEwma ?: 100.0) in 1.0..84.0
        val pathCapped = if (jitterHigh || pingHigh || lossy) {
            // Root-free queue pressure control: shrink Marble/HEV buffers when live ping/jitter/loss
            // say the underlay is reassembling or dropping shredded TLS records.
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

        /*
         * MARBLE_INTELLIGENCE_V141 — DNS storm memory guard.
         *
         * The broken log's PSS regression (104 MB vs the 98 MB green reference) grew exactly while
         * the DoH deadline storm ran: every failed lookup parked an error buffer, and the resolver
         * pool was retried serially, so the buffers accumulated faster than they drained. While the
         * storm detector is armed the userspace datapath is clamped to the baseline-or-smaller
         * shape regardless of measured throughput, because throughput measured before the storm
         * describes a link that no longer exists.
         */
        val stormCapped = if (dnsStormActive()) {
            TunnelTuning(
                maxSessions = min(pathCapped.maxSessions, 2048),
                tcpBufferBytes = min(pathCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(pathCapped.udpBufferBytes, 131_072),
                label = "${pathCapped.label}/dns-storm"
            )
        } else pathCapped

        // Never let a bigger datapath fight the thermal governor for the same silicon.
        return if (thermal < 0.55) {
            TunnelTuning(
                maxSessions = min(stormCapped.maxSessions, 2048),
                tcpBufferBytes = min(stormCapped.tcpBufferBytes, 65_536),
                udpBufferBytes = min(stormCapped.udpBufferBytes, 262_144),
                label = "${stormCapped.label}/thermal"
            )
        } else stormCapped
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
        val nowMs = System.currentTimeMillis()
        val storm = stormGuard.active(nowMs)
        val health = db.get(profile.id, n.key())
        val link = LinkEvidence.fromHealth(health)
            .conservativeOf(networkLinkPrior(n.key()))
        val noisy = ProtocolFitness.noisy(link)

        // Prefer IPv6 is a preference, not a demand. Suspend it on IPv4-only links and restore it
        // automatically when a real global IPv6 underlay becomes available.
        val effectivePreferIpv6 =
            base.ipv6Enabled &&
                base.preferIpv6 &&
                n.hasIpv6

        val ipRace = SmartIpRacePolicy.decide(
            n,
            health,
            base.copy(preferIpv6 = effectivePreferIpv6)
        )
        val raceUnstable = ipRace.reason == "unstable-race"

        /*
         * MARBLE_INTELLIGENCE_V141 — the 60 ms penalty is eliminated by evidence, not by guess.
         *
         * The broken log showed "IPv6 preferred, IPv4 raced after 60 ms" on a link whose IPv6 path
         * was never proven: every connection paid the race delay and a dead-family socket. The
         * family plan now collapses to IPv4-first whenever any of the following holds:
         *
         *  - the race itself was unstable (SmartIpRacePolicy measured failure streak, low success
         *    EWMA or high jitter on this node) — and the verdict is persisted for 24 h;
         *  - this physical network carries a stored unhealthy verdict from an earlier session;
         *  - the link is measured noisy and IPv6 has no *positive* proof (the green reference was
         *    IPv4-only end to end, and an unproven family is the first thing a noisy link breaks);
         *  - a DNS storm is active — AAAA lookups double the failure surface exactly when the
         *    resolver pool is already missing its budgets.
         *
         * Only strict user demands (IPv6 off, or an explicit v6-only strategy) outrank evidence;
         * everything else keeps the automatic behaviour when IPv6 is actually proven healthy.
         */
        if (raceUnstable) rememberIpv6Unhealthy()
        val storedV6Unhealthy = storedIpv6Unhealthy()
        val measuredV6Healthy = when {
            raceUnstable -> false
            storedV6Unhealthy -> false
            else -> null
        }
        val familyLockReason = when {
            !base.ipv6Enabled -> null
            raceUnstable -> "unstable-race"
            storedV6Unhealthy -> "stored-verdict"
            noisy && measuredV6Healthy != true -> "noisy-link"
            storm && measuredV6Healthy != true -> "dns-storm"
            else -> null
        }
        val forceIpv4First = familyLockReason != null

        val queryStrategy = when {
            !base.ipv6Enabled -> "UseIPv4"
            base.dnsQueryStrategy.equals("UseIPv6", true) && n.hasIpv6 && !forceIpv4First -> "UseIPv6"
            !base.adaptiveDualStackEnabled -> base.dnsQueryStrategy
            forceIpv4First -> "UseIPv4"
            n.hasIpv4 && !n.hasIpv6 -> "UseIPv4"
            n.hasIpv6 && !n.hasIpv4 -> "UseIPv6"
            else -> "UseIP"
        }

        val dnsOrdered = preferredDnsOrder(base)

        /*
         * MARBLE_RESOLVER_EVIDENCE_V134 — publish the resolver verdict inside the settings object,
         * exactly like the IPv6 verdict below it. The encrypted resolver list is assembled by the
         * config writer from these settings, so a verdict that only this function knew about is why
         * 29 attributed `DoH deadline` events never changed a single emitted resolver: the
         * observation and the emission were two ends of an open loop.
         *
         * MARBLE_INTELLIGENCE_V141 — while a storm is armed the pool is raced regardless of the
         * per-endpoint demotion state: a storm means the *serial* walk is the failure mode, and
         * Xray's own documentation names `enableParallelQuery` as the remedy for exactly that
         * symptom. Three tiny queries per lookup cost nothing against a tunnel that currently
         * resolves nothing.
         */
        val resolverPool = dnsCandidatePool(base)
        val endpointEvidence = if (base.adaptiveDnsEnabled) resolverEvidence() else emptyList()
        val dnsDemoted = if (base.adaptiveDnsEnabled) {
            ResolverEvidencePolicy.demoted(resolverPool, endpointEvidence, nowMs)
        } else {
            emptyList()
        }
        val dnsParallel = base.adaptiveDnsEnabled && (
            storm ||
                ResolverEvidencePolicy.parallelQueryJustified(
                    resolverPool, endpointEvidence, nowMs
                )
            )

        // The underlay decides which records are even worth asking for; the plan then decides the
        // family order for the tunnel, the delay test and the probers in one place. A measured IPv6
        // pathology on this node demotes the automatic ordering, but never overrides what the user
        // explicitly asked for.
        val familyBase = base.copy(dnsQueryStrategy = queryStrategy)
        val familyPreference = AddressFamilyPolicy.preference(
            settings = familyBase,
            underlayHasIpv6 = n.hasIpv6,
            measuredV6Healthy = measuredV6Healthy
        )

        // Measured-first policy: history chooses what to test, but never mutates Fragment/Mux by guess.
        // Explicit user settings remain intact, and IranShield may still apply censorship-specific changes.
        val tuned = familyBase.copy(
            dnsQueryStrategy = AddressFamilyPolicy.dnsQueryStrategy(familyBase, familyPreference),
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
            // A zero delay is meaningful: it tells the hardener not to arm Xray's race at all, which
            // then resolves deterministically instead of leaving the choice to the engine's random pick.
            // Under a family lock the race is not merely early — it is disarmed, because a proven-dead
            // family must not burn a concurrent dial slot on every destination.
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
            // MARBLE_MEASURED_FAMILY_V133 — publish the measured verdict inside the settings object
            // itself. AddressFamilyPolicy is consulted from the config writer, the delay-test config,
            // the Kotlin probers and Bug Finder; a verdict only this function knew about is why the
            // diagnostics kept reporting "IPv6 preferred, IPv4 raced after 60 ms" while the node's own
            // history said IPv6 was unhealthy.
            measuredIpv6Unhealthy = measuredV6Healthy == false,
            measuredDnsDemotedEndpoints = dnsDemoted.joinToString(","),
            measuredDnsParallel = dnsParallel
        )

        // Only a freshly measured acceleration plan may change generic transport tuning.
        val accelerated =
            if (withAcceleration && base.connectTuningEnabled) {
                acceleration(profile.id)?.applyTo(tuned) ?: tuned
            } else {
                tuned
            }

        // MARBLE_INTELLIGENCE_V141 — protocol fitness: multiplexing on top of a record-chunked
        // transport under measured noise stacks two head-of-line blockers, which is how VLESS+xhttp
        // turned jitter into teardowns on the broken log while the hysteria2 reference stayed up.
        val muxGuarded = if (ProtocolFitness.prefersMuxOff(profile, link)) {
            accelerated.copy(muxEnabled = false)
        } else {
            accelerated
        }

        // Iran Mode is applied last so its countermeasures win over generic adaptive tuning.
        val shielded = IranShield.apply(muxGuarded, profile, iranState, iranGeoIpReady)
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

        // Failure streaks decay one step every six quiet hours, allowing recovered nodes back in.
        val failureAgeHours = (nowMs - h.lastSeenAt).coerceAtLeast(0L) / 3_600_000.0
        val effectiveFailureStreak = (h.failureStreak - (failureAgeHours / 6.0).toInt()).coerceAtLeast(0)
        val failurePenalty =
            effectiveFailureStreak
                .coerceAtMost(6) *
                7.5

        /*
         * MARBLE_INTELLIGENCE_V141 — fresh-success stickiness.
         *
         * The broken log forced six restarts in seven minutes partly because every DNS-induced
         * socket closure reshuffled the candidate list behind nodes with no evidence at all. A node
         * that demonstrably carried traffic minutes ago is the cheapest safe bet on this network,
         * so it gets a bounded bonus that decays with time and never outranks a large reliability
         * gap: it is a tiebreaker with memory, not an override.
         */
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
     *
     * MARBLE_INTELLIGENCE_V141 — the transport shape now matters on *every* noisy link, not only
     * under censorship: [ProtocolFitness.bias] is the term that makes a hysteria2 exit outrank a
     * VLESS+xhttp exit when jitter and loss are measured, which is precisely the difference between
     * the green and the broken log.
     */
    fun rankingScore(
        profile: ProxyProfile,
        settings: AppSettings
    ): Double =
        predictedScore(profile, settings) +
            IranShield.profileBias(profile, iranState) +
            ProtocolFitness.bias(profile, networkLinkPrior(currentSnapshot().key()))

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
        // One link prior for the whole pass: protocol fitness is a property of the link, and
        // re-deriving it per profile would repeat the SQLite bulk read the bulk map just avoided.
        val link = networkLinkPrior(currentSnapshot().key())
        profiles.forEach { profile ->
            if (!out.containsKey(profile.id)) {
                val exact = health[profile.id]
                out[profile.id] =
                    predictedScoreOf(exact ?: priors[profile.id], settings, if (exact == null) 0.35 else 1.0) +
                        IranShield.profileBias(profile, iranState) +
                        ProtocolFitness.bias(profile, link)
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
        val storm = dnsStormActive()
        val familyLocked = storedIpv6Unhealthy()

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
            familyLock = if (familyLocked) "IPv4 (stored verdict)" else "",
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

        // A bare GET /dns-query can return HTTP 400 quickly even when the resolver itself cannot
        // answer DNS. Send a real RFC 8484 wire-format query so the learned winner represents a
        // usable encrypted resolver rather than just a reachable HTTPS socket.
        //
        // MARBLE_TUNING_MEASUREMENT_PLANE_V134 — the audit's own budget was the same species of
        // constant: 2500 ms for a DoH transaction that travels *through the tunnel it is auditing*
        // (SOCKS connect, TLS handshake, query — four round trips). On the 267–444 ms route in the
        // attached runtime log that left no margin at all, so every endpoint timed out, the audit
        // reported "current order retained", and the resolver order was never learned on exactly the
        // links that need it most. Derived from the same evidence as every other budget, with 2500 ms
        // kept as the floor.
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

        // MARBLE_RESOLVER_EVIDENCE_V134 — a real RFC 8484 answer is the strongest evidence
        // available that an endpoint works on this network right now, so it clears the attributed
        // failure pressure immediately. This is the recovery half of the loop: without it a demotion
        // could only expire by TTL, and a resolver that came back mid-session would keep its demoted
        // rank for the whole window.
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

    /**
     * MARBLE_RESOLVER_EVIDENCE_V134 — resolver order for the config that is about to be emitted.
     *
     * This used to be a latency audit with two possible outcomes: the user's pair, or the user's
     * pair swapped. That is why 29 attributed `DoH deadline` events changed nothing — the audit ran
     * before the connect, measured how *fast* an endpoint answered, and had no input at all for how
     * often an endpoint failed once the tunnel was carrying traffic.
     *
     * Two evidence sources now decide, in this order:
     *
     *  1. **attributed failures** ([ResolverEvidencePolicy]) — an endpoint the core itself was seen
     *     failing decisively on this physical network loses its rank to any endpoint that was not.
     *     Decayed on a half-life, expired by a TTL, and cleared by a single proven answer, so a
     *     filtering window that ends stops influencing the order;
     *  2. **measured latency** (the pre-connect RFC 8484 audit) — decides the order *within* the
     *     healthy group, with the hysteresis it already had.
     *
     * A latency winner may never outrank failure evidence: an endpoint that answered fast once and
     * has been timing out since is not the resolver you want first.
     */
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
        val ordered = ResolverEvidencePolicy.order(
            dnsCandidatePool(settings),
            evidence,
            nowMs
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

        /**
         * MARBLE_INTELLIGENCE_V141 — how long a measured-unhealthy IPv6 verdict stays binding for
         * its physical network. One day: long enough to survive a full session of reconnects, short
         * enough that a carrier fixing its v6 path is re-proven the next day.
         */
        const val IPV6_VERDICT_TTL_MS = 24L * 60L * 60L * 1000L

        /**
         * MARBLE_RESOLVER_EVIDENCE_V134 — the same independent stock resolvers the config writer
         * appends, kept here so the ordering decision and the emitted list are drawn from one pool.
         * A demotion can only promote an endpoint that is actually a candidate.
         */
        val STOCK_DOH_RESOLVERS = listOf(
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/dns-query",
            "https://9.9.9.9/dns-query"
        )

        /** The learned resolver order stays valid for this long before it must be re-measured. */
        const val DNS_WINNER_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
