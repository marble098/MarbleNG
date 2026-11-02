package com.marbleng.app.core

import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.NodeSortMode
import com.marbleng.app.model.ProxyProfile

data class ProtocolTally(val scheme: String, val count: Int)

data class ServerCountryGroup(
    val country: ServerCountry,
    val profiles: List<ProxyProfile>
)

data class ServersFilter(
    val query: String = "",
    val protocol: String = "",
    val sourceId: String = "all",
    val onlyReachable: Boolean = false,
    val maxPingMs: Int = 0,
    val groupByCountry: Boolean = false
) {
    val isActive: Boolean
        get() = query.isNotBlank() || protocol.isNotBlank() || sourceId != "all" ||
            onlyReachable || maxPingMs > 0

    fun cleared(): ServersFilter = ServersFilter()
}

object ServersQuery {
    const val ALL_PROTOCOLS = ""

    const val MAX_PING_OFF = 0

    val MAX_PING_CHOICES: List<Int> = listOf(MAX_PING_OFF, 100, 200, 500)

    /**
     * MARBLE_ONE_RESET_V261 — the list's *narrowing*, cleared in one place.
     *
     * The page had three "Reset Filters" closures and each of them reset a different scope: the chip
     * strip cleared the protocol, the reachability gate, the ping ceiling, the sort direction *and*
     * the source; the filtered-empty state did the same minus nothing; the filter sheet cleared the
     * first three but left the sort and the source alone. Three verbs with one label that do three
     * different things is the worst kind of duplication — a reader learns the label once and it
     * lies to them twice.
     *
     * So the scope is decided here, once: **a filter is anything that hides a server the reader
     * owns.** The source is a filter (a server outside the chosen source is hidden), the protocol is
     * a filter, the reachability gate is a filter, the ping ceiling is a filter, the search text is a
     * filter. **Sort is not** — reversing a list reorders it and hides nothing — so `nodeSortReverse`
     * and `nodeSortMode` are deliberately *not* touched by [clearedFilters], and the chip strip's
     * reset stops clearing them.
     *
     * [resetQuery] exists for the two callers that own a text field: the query is page state and not
     * a setting, so the model cannot clear it, and the caller passes whether it did.
     */
    fun clearedFilters(settings: com.marbleng.app.model.AppSettings): com.marbleng.app.model.AppSettings =
        settings.copy(
            serversProtocolFilter = ALL_PROTOCOLS,
            serversOnlyReachable = false,
            serversMaxPingMs = MAX_PING_OFF
        )

    /**
     * True when [settings] hides at least one server: what a "Reset Filters" verb is *for*.
     *
     * The source is page state rather than a setting (`repo.librarySourceFilter`), so a caller that
     * wants the whole answer passes [sourcesNarrow] as well.
     */
    fun narrows(settings: com.marbleng.app.model.AppSettings, sourcesNarrow: Boolean = false): Boolean =
        settings.serversProtocolFilter.isNotBlank() ||
            settings.serversOnlyReachable ||
            settings.serversMaxPingMs > MAX_PING_OFF ||
            sourcesNarrow

    fun protocolKey(profile: ProxyProfile): String =
        profile.scheme.trim().uppercase().ifBlank { "PROXY" }

    fun badge(profile: ProxyProfile): String {
        val parts = mutableListOf(protocolKey(profile))
        securityLabel(profile.security)?.let { parts += it }
        transportLabel(profile.transport)?.let { parts += it }
        return parts.joinToString("/")
    }

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

    fun countryOf(profile: ProxyProfile): ServerCountry =
        ServerCountry.of(profile.name, profile.host)

    fun measuredMs(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Int =
        benchmarks[profile.id]?.takeIf { it.success > 0 }?.latencyMs?.toInt()?.coerceAtLeast(0) ?: 0

    fun hasFailedMeasurement(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Boolean =
        benchmarks[profile.id]?.let { it.success <= 0 } ?: false

    fun protocolTallies(profiles: List<ProxyProfile>): List<ProtocolTally> =
        profiles.groupingBy { protocolKey(it) }
            .eachCount()
            .map { (scheme, count) -> ProtocolTally(scheme, count) }
            .sortedWith(compareByDescending<ProtocolTally> { it.count }.thenBy { it.scheme })

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

    fun visible(
        profiles: List<ProxyProfile>,
        filter: ServersFilter,
        benchmarks: Map<String, BenchmarkResult> = emptyMap()
    ): List<ProxyProfile> = profiles.filter { matches(it, filter, benchmarks) }

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

                    .thenBy { it.subscriptionId }
                    .thenBy { cleanName(it) }
            )

            NodeSortMode.COUNTRY -> profiles.sortedWith(
                compareBy<ProxyProfile> { countryOf(it).sortKey }
                    .thenBy { cleanName(it) }
            )
        }

        return if (reverse) sorted.asReversed() else sorted
    }

    fun groupByCountry(profiles: List<ProxyProfile>): List<ServerCountryGroup> =
        profiles.groupBy { countryOf(it) }
            .map { (country, members) -> ServerCountryGroup(country, sort(members, NodeSortMode.NAME, false)) }
            .sortedWith(
                compareBy<ServerCountryGroup>(
                    { !it.country.isKnown },
                    { it.country.sortKey }
                )
            )

    private fun cleanName(profile: ProxyProfile): String =
        stripFlag(profile.name).lowercase()

    private fun latencyRank(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Long {
        val result = benchmarks[profile.id] ?: return Long.MAX_VALUE
        if (result.success <= 0) return Long.MAX_VALUE
        return result.latencyMs.toLong().coerceAtLeast(0L)
    }

    private fun scoreOf(profile: ProxyProfile, benchmarks: Map<String, BenchmarkResult>): Double =
        benchmarks[profile.id]?.takeIf { it.success > 0 }?.score ?: -1.0

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
