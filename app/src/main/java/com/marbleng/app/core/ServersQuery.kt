package com.marbleng.app.core

import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.NodeSortMode
import com.marbleng.app.model.ProxyProfile

// MARBLE_SERVERS_QUERY_V120
//
// One pure engine behind the Servers page: the search box, the protocol chip, the group chip and
// the advanced-filter switches all describe *what should be visible*, and one sort mode decides
// the order. Keeping that in a single side-effect-free object means the exact behaviour a user
// sees is covered by ordinary JVM unit tests instead of only by what a screenshot happens to show.

/** One protocol row of the "All protocols" menu: the wire scheme and how many servers use it. */
data class ProtocolTally(val scheme: String, val count: Int)

/** One country bucket produced by the "Group by country" switch. */
data class ServerCountryGroup(
    val country: ServerCountry,
    val profiles: List<ProxyProfile>
)

/**
 * The complete Servers filter state. Every field maps 1:1 onto a control in the page, so the UI
 * never invents its own filtering rules.
 */
data class ServersFilter(
    /** Free text matched against name, protocol, host, transport and security. */
    val query: String = "",
    /** Wire scheme filter; blank means every protocol. */
    val protocol: String = "",
    /** Source/group filter: "all", "manual" or a subscription id. */
    val sourceId: String = "all",
    /** Hide servers whose latest measurement failed. */
    val onlyReachable: Boolean = false,
    /** Hide servers slower than this many milliseconds; 0 disables the ceiling. */
    val maxPingMs: Int = 0,
    /** Bucket the list by resolved country instead of by source. */
    val groupByCountry: Boolean = false
) {
    /** True when any control deviates from its default, i.e. the list is not showing everything. */
    val isActive: Boolean
        get() = query.isNotBlank() || protocol.isNotBlank() || sourceId != "all" ||
            onlyReachable || maxPingMs > 0

    /**
     * Every control back to its default, the group scope included: "Reset filters" has to be able
     * to take the page back to "showing everything", which is exactly what [isActive] reports.
     */
    fun cleared(): ServersFilter = ServersFilter()
}

object ServersQuery {

    /** The "All protocols" sentinel used by the filter chip and the persisted setting. */
    const val ALL_PROTOCOLS = ""

    /** The "Max ping: Off" sentinel. */
    const val MAX_PING_OFF = 0

    /** The ping ceilings offered by the advanced-filter menu. */
    val MAX_PING_CHOICES: List<Int> = listOf(MAX_PING_OFF, 100, 200, 500)

    /** Normalised protocol key: upper-case, never blank, so tallies and filters always agree. */
    fun protocolKey(profile: ProxyProfile): String =
        profile.scheme.trim().uppercase().ifBlank { "PROXY" }

    /**
     * The badge text under a server name: the wire scheme plus the security and transport that
     * actually distinguish it, e.g. `VLESS/REALITY` or `VMESS/TLS/H2`. Redundant segments
     * ("TCP", "NONE", "RAW") are dropped so the badge stays one glance wide.
     */
    fun badge(profile: ProxyProfile): String {
        val parts = mutableListOf(protocolKey(profile))
        securityLabel(profile.security)?.let { parts += it }
        transportLabel(profile.transport)?.let { parts += it }
        return parts.joinToString("/")
    }

    /** Visible endpoint of a server: host without brackets, plus its port when known. */
    fun address(profile: ProxyProfile): String = listOfNotNull(
        profile.host.trim().removeSurrounding("[", "]").takeIf(String::isNotBlank),
        profile.port.takeIf { it > 0 }?.toString()
    ).joinToString(":")

    private fun securityLabel(security: String): String? = when (security.trim().lowercase()) {
        "", "none", "native" -> null
        "reality" -> "REALITY"
        "tls" -> "TLS"
        else -> security.trim().uppercase()
    }

    private fun transportLabel(transport: String): String? = when (transport.trim().lowercase()) {
        "", "tcp", "raw", "native", "none" -> null
        "ws", "websocket" -> "WS"
        "h2", "http/2", "http2" -> "H2"
        "httpupgrade" -> "HTTPU"
        "grpc", "gun" -> "GRPC"
        "xhttp", "splithttp" -> "XHTTP"
        "mkcp", "kcp" -> "KCP"
        "quic" -> "QUIC"
        "hysteria" -> null
        "ssh" -> "SSH"
        "wireguard" -> null
        else -> transport.trim().uppercase()
    }

    /** Country of a node, from the label the source gave it. */
    fun countryOf(profile: ProxyProfile): ServerCountry =
        ServerCountry.of(profile.name, profile.host)

    /**
     * Measured latency in whole milliseconds, or 0 when the node has no successful measurement.
     * A failed probe is 0 too: the page shows an honest "not measured", never a negative number.
     */
    fun measuredMs(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Int =
        benchmarks[profile.id]?.takeIf { it.success > 0 }?.latencyMs?.toInt()?.coerceAtLeast(0) ?: 0

    /** True when the newest stored measurement explicitly failed. */
    fun hasFailedMeasurement(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Boolean =
        benchmarks[profile.id]?.let { it.success <= 0 } ?: false

    /**
     * Protocol tallies over [profiles], richest first and alphabetically inside a tie, so the
     * menu order is stable while the user types.
     */
    fun protocolTallies(profiles: List<ProxyProfile>): List<ProtocolTally> =
        profiles.groupingBy { protocolKey(it) }
            .eachCount()
            .map { (scheme, count) -> ProtocolTally(scheme, count) }
            .sortedWith(compareByDescending<ProtocolTally> { it.count }.thenBy { it.scheme })

    /** True when [profile] survives every active control in [filter]. */
    fun matches(
        profile: ProxyProfile,
        filter: ServersFilter,
        benchmarks: Map<String, BenchmarkResult> = emptyMap()
    ): Boolean {
        val query = filter.query.trim()
        if (query.isNotBlank() && !matchesQuery(profile, query)) return false
        if (filter.protocol.isNotBlank() && protocolKey(profile) != filter.protocol.uppercase()) {
            return false
        }
        if (filter.sourceId != "all" && profile.subscriptionId != filter.sourceId) return false
        if (filter.onlyReachable) {
            val result = benchmarks[profile.id]
            if (result == null || result.success <= 0) return false
        }
        if (filter.maxPingMs > MAX_PING_OFF) {
            val latency = measuredMs(profile, benchmarks)
            if (latency <= 0 || latency > filter.maxPingMs) return false
        }
        return true
    }

    /** Search across everything a user can recognise a server by. */
    fun matchesQuery(profile: ProxyProfile, query: String): Boolean {
        val needle = query.trim()
        if (needle.isBlank()) return true
        return profile.name.contains(needle, true) ||
            protocolKey(profile).contains(needle.trim().uppercase(), true) ||
            profile.host.contains(needle, true) ||
            profile.transport.contains(needle, true) ||
            profile.security.contains(needle, true) ||
            countryOf(profile).name.contains(needle, true)
    }

    /** Every profile that survives [filter], in [profiles] order. */
    fun visible(
        profiles: List<ProxyProfile>,
        filter: ServersFilter,
        benchmarks: Map<String, BenchmarkResult> = emptyMap()
    ): List<ProxyProfile> = profiles.filter { matches(it, filter, benchmarks) }

    /**
     * Deterministic ordering for one sort mode.
     *
     * `DEFAULT` keeps the order the source published, which is what a subscription owner means
     * when they number their nodes; every other mode is a real comparison. Unmeasured servers
     * always sort last in the latency modes so a fresh library never pretends 0 ms is fastest.
     */
    fun sort(
        profiles: List<ProxyProfile>,
        mode: NodeSortMode,
        reverse: Boolean,
        benchmarks: Map<String, BenchmarkResult> = emptyMap()
    ): List<ProxyProfile> {
        val sorted = when (mode) {
            NodeSortMode.DEFAULT -> profiles.toList()

            NodeSortMode.NAME -> profiles.sortedBy { cleanName(it) }

            NodeSortMode.PING -> profiles.sortedWith(
                compareBy<ProxyProfile> { latencyRank(it, benchmarks) }
                    .thenBy { cleanName(it) }
            )

            NodeSortMode.SCORE -> profiles.sortedWith(
                compareByDescending<ProxyProfile> { scoreOf(it, benchmarks) }
                    .thenBy { cleanName(it) }
            )

            NodeSortMode.PROTOCOL -> profiles.sortedWith(
                compareBy<ProxyProfile> { protocolKey(it) }
                    .thenBy { cleanName(it) }
            )

            NodeSortMode.SOURCE -> profiles.sortedWith(
                compareBy<ProxyProfile> { it.subscriptionName.lowercase() }
                    // Two groups can share a display name; the id keeps the order deterministic.
                    .thenBy { it.subscriptionId }
                    .thenBy { cleanName(it) }
            )

            NodeSortMode.COUNTRY -> profiles.sortedWith(
                compareBy<ProxyProfile> { countryOf(it).sortKey }
                    .thenBy { cleanName(it) }
            )
        }
        // DEFAULT with reverse is still a real request: it flips the published order.
        return if (reverse) sorted.asReversed() else sorted
    }

    /** Country buckets, known countries alphabetically and the unknown bucket last. */
    fun groupByCountry(profiles: List<ProxyProfile>): List<ServerCountryGroup> =
        profiles.groupBy { countryOf(it) }
            .map { (country, members) -> ServerCountryGroup(country, sort(members, NodeSortMode.NAME, false)) }
            .sortedWith(
                compareBy<ServerCountryGroup>(
                    { !it.country.isKnown },
                    { it.country.sortKey }
                )
            )

    /** Server name without its leading flag, lower-cased, for stable alphabetical order. */
    private fun cleanName(profile: ProxyProfile): String =
        stripFlag(profile.name).lowercase()

    /** Rank used by the latency sort: measured latency first, never-measured last. */
    private fun latencyRank(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Long {
        val result = benchmarks[profile.id] ?: return Long.MAX_VALUE
        if (result.success <= 0) return Long.MAX_VALUE
        return result.latencyMs.toLong().coerceAtLeast(0L)
    }

    private fun scoreOf(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Double =
        benchmarks[profile.id]?.takeIf { it.success > 0 }?.score ?: -1.0

    /**
     * Leading regional-indicator flag stripped from a display name. Mirrors the design-system
     * helper so the sorter and the painter never disagree about where the name starts.
     */
    internal fun stripFlag(text: String): String {
        val clean = text.trim()
        val points = clean.codePoints().limit(2).toArray()
        if (points.size < 2) return clean
        if (points.any { it !in 0x1F1E6..0x1F1FF }) return clean
        val flag = buildString {
            append(String(Character.toChars(points[0])))
            append(String(Character.toChars(points[1])))
        }
        return clean.substring(flag.length).trimStart()
    }
}
