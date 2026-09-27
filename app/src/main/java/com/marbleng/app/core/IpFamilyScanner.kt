package com.marbleng.app.core

import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * MARBLE_IP_FAMILY_SCAN_V196 — what address families one server actually has, measured.
 *
 * ## Why this exists
 *
 * Marble's whole speed story is "IPv6 when it is real, IPv4 when it is not". Until now the app
 * could only *guess* which of the two a node supported: `AddressFamilyPolicy` knew what the user
 * asked for and what the phone's underlay could carry, but nothing in the product knew whether
 * `de2.example.net` even publishes an AAAA record — let alone whether that AAAA answers on the
 * node's port. That missing fact is exactly what turned "Force IPv6" into a string of
 * `BLOCKED • Kill switch active` states: every IPv4-only server in the library was refused at
 * connect time, one after another, and the user had to discover the family of each node by
 * failing into it.
 *
 * A guess is not good enough for a decision that decides whether a tunnel opens, so this file
 * measures the two facts that matter, per endpoint, on the network the phone is actually on:
 *
 *  1. **Which records exist** — A and/or AAAA, resolved through the app's own encrypted
 *     IP-literal DoH ([AddressFamilyPolicy.resolveWithBudget]). System DNS is never consulted, so
 *     a scan cannot leak a node name to the local ISP and cannot be poisoned by it either.
 *  2. **Which of them actually connects** — one bounded TCP connect per family, to the node's own
 *     port. "Advertises AAAA" and "answers over IPv6" are different claims, and only the second
 *     one is worth routing on.
 *
 * ## What the verdict means
 *
 * The result is deliberately a *five-state* answer rather than a boolean, because each state has
 * a different remedy and the UI has to be able to say which one applies:
 *
 *  - [IpFamilyVerdict.DUAL_OK] — both families connect. Force IPv6 is safe here, and this is the
 *    node Marble should prefer when the user wants maximum speed.
 *  - [IpFamilyVerdict.IPV6_ONLY] — only IPv6 connects. Fast on a v6 network, unreachable without
 *    one; Force IPv4 cannot dial it at all.
 *  - [IpFamilyVerdict.IPV4_ONLY] — no AAAA exists. Force IPv6 can never dial this node, and no
 *    amount of retrying will change that: the ladder in [Ipv6FallbackLadder] must keep IPv6 for
 *    the *destination* side and dial the node over IPv4.
 *  - [IpFamilyVerdict.IPV6_UNPROVEN] — AAAA exists, IPv4 connects, IPv6 did not. Either the node's
 *    v6 listener is broken or this Wi-Fi has no IPv6 route; [IpFamilyScan.underlayHasIpv6] says
 *    which, and the two have opposite remedies.
 *  - [IpFamilyVerdict.UNREACHABLE] — records exist, nothing connects. A family problem is not the
 *    story here; the node or the port is.
 *  - [IpFamilyVerdict.UNKNOWN] — no DNS answer at all, so nothing may be concluded. A scan that
 *    could not resolve is never allowed to look like "IPv4-only".
 *
 * ## Scope of a result
 *
 * A verdict is scoped to the physical network it was measured on ([IpFamilyScan.networkKey], the
 * same key Marble Intelligence buckets node health by) and has a TTL. Moving from a v6-capable
 * home Wi-Fi to a v4-only café must not let yesterday's "IPv6 works" answer keep Force IPv6
 * armed, and a node that gained an AAAA record last week must not stay branded IPv4-only forever.
 * Everything here is pure enough to unit-test: DNS and TCP arrive as function seams.
 */
enum class IpFamilyVerdict {
    /** Nothing measured yet, or the scan produced no DNS answer at all. */
    UNKNOWN,

    /** Both families resolve and both connect. */
    DUAL_OK,

    /** Only IPv6 connects: either no A record, or the IPv4 address refuses. */
    IPV6_ONLY,

    /** No AAAA record exists at all: this node can only ever be dialled over IPv4. */
    IPV4_ONLY,

    /** AAAA exists and IPv4 works, but the IPv6 connect could not be proven here. */
    IPV6_UNPROVEN,

    /** Addresses exist in at least one family and none of them accepted a connection. */
    UNREACHABLE
}

/**
 * One measured address-family fact about one endpoint.
 *
 * Latencies are milliseconds, `-1` when that family never completed a connect. Addresses are kept
 * even for a family that failed: "2a01:… did not answer" is a far more useful line in a report
 * than "IPv6 failed".
 */
data class IpFamilyScan(
    /** Canonical `host:port`, produced by [ServerLocationKey.of] so it matches the location cache. */
    val endpoint: String,
    /** The physical network this verdict belongs to; blank means "not scoped". */
    val networkKey: String = "",
    val hasIpv4: Boolean = false,
    val hasIpv6: Boolean = false,
    val ipv4Ok: Boolean = false,
    val ipv6Ok: Boolean = false,
    val ipv4LatencyMs: Int = -1,
    val ipv6LatencyMs: Int = -1,
    val ipv4Address: String = "",
    val ipv6Address: String = "",
    /** Whether the phone itself had a global IPv6 address while this scan ran. */
    val underlayHasIpv6: Boolean = false,
    val scannedAtMs: Long = 0L
) {
    val verdict: IpFamilyVerdict
        get() = IpFamilyScanner.classify(
            hasIpv4 = hasIpv4,
            hasIpv6 = hasIpv6,
            ipv4Ok = ipv4Ok,
            ipv6Ok = ipv6Ok
        )

    /** True when this node can be dialled over IPv6 right now — the one fact Force IPv6 needs. */
    val ipv6Usable: Boolean get() = ipv6Ok

    /** True when the node publishes no AAAA at all: no retry, no network change can fix that. */
    val ipv4Locked: Boolean get() = !hasIpv6 && hasIpv4

    /** A scan is only evidence while it is young and belongs to the network in front of us. */
    fun usableOn(networkKey: String, nowMs: Long, ttlMs: Long = IpFamilyScanner.TTL_MS): Boolean {
        if (scannedAtMs <= 0L) return false
        if (nowMs - scannedAtMs > ttlMs) return false
        if (this.networkKey.isBlank() || networkKey.isBlank()) return true
        return this.networkKey == networkKey
    }

    /** Two or three characters for a list row: `v4+v6`, `v6`, `v4`, `v4?`, `✕`. */
    val chip: String
        get() = when (verdict) {
            IpFamilyVerdict.DUAL_OK -> "v4+v6"
            IpFamilyVerdict.IPV6_ONLY -> "v6"
            IpFamilyVerdict.IPV4_ONLY -> "v4"
            IpFamilyVerdict.IPV6_UNPROVEN -> "v4 • v6?"
            IpFamilyVerdict.UNREACHABLE -> "no route"
            IpFamilyVerdict.UNKNOWN -> "?"
        }

    /** One sentence naming the verdict, in the product's own voice. */
    val headline: String
        get() = when (verdict) {
            IpFamilyVerdict.DUAL_OK -> "Dual stack • IPv4 and IPv6 both answer"
            IpFamilyVerdict.IPV6_ONLY -> "IPv6 only • this node has no usable IPv4 path"
            IpFamilyVerdict.IPV4_ONLY -> "IPv4 only • this node publishes no IPv6 address"
            IpFamilyVerdict.IPV6_UNPROVEN -> if (underlayHasIpv6) {
                "IPv4 answers • the node's IPv6 address did not"
            } else {
                "IPv4 answers • IPv6 could not be tested on this network"
            }
            IpFamilyVerdict.UNREACHABLE -> "Neither family answered on this port"
            IpFamilyVerdict.UNKNOWN -> "No DNS answer • the family of this node is still unknown"
        }

    /** The remedy line: what the app will do, and what the user can do about it. */
    val advice: String
        get() = when (verdict) {
            IpFamilyVerdict.DUAL_OK -> "Force IPv6 is safe on this node"
            IpFamilyVerdict.IPV6_ONLY ->
                "Keep IPv6 enabled; Force IPv4 cannot dial this node"
            IpFamilyVerdict.IPV4_ONLY ->
                "Force IPv6 falls back to an IPv4 dial here and keeps IPv6 for destinations"
            IpFamilyVerdict.IPV6_UNPROVEN -> if (underlayHasIpv6) {
                "IPv6 stays the second attempt on this node, never the only one"
            } else {
                "This network has no IPv6 route; connect over IPv4 and re-scan on a v6 network"
            }
            IpFamilyVerdict.UNREACHABLE -> "Ping this server or pick another one"
            IpFamilyVerdict.UNKNOWN -> "Re-scan once the network settles"
        }

    /** The measurement itself, compact enough for a menu row or a diagnostics line. */
    val detail: String
        get() = buildString {
            append("IPv6 ")
            append(
                when {
                    ipv6Ok -> "${ipv6Address.ifBlank { "ok" }} • ${ipv6LatencyMs} ms"
                    hasIpv6 -> "${ipv6Address.ifBlank { "advertised" }} • no answer"
                    else -> "no AAAA record"
                }
            )
            append(" · IPv4 ")
            append(
                when {
                    ipv4Ok -> "${ipv4Address.ifBlank { "ok" }} • ${ipv4LatencyMs} ms"
                    hasIpv4 -> "${ipv4Address.ifBlank { "advertised" }} • no answer"
                    else -> "no A record"
                }
            )
            append(" · network ")
            append(if (underlayHasIpv6) "carries IPv6" else "IPv4-only")
        }

    /**
     * Which family is the fast one, when both work: `"ipv6"`, `"ipv4"` or `""` when only one (or
     * neither) family answered. Marble prefers IPv6 on a tie — that is the product's whole point,
     * and a tie means the v6 path costs nothing extra.
     */
    val fasterFamily: String
        get() = when {
            !ipv6Ok || !ipv4Ok -> ""
            ipv6LatencyMs <= ipv4LatencyMs -> "ipv6"
            else -> "ipv4"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("endpoint", endpoint)
        .put("network", networkKey)
        .put("has4", hasIpv4)
        .put("has6", hasIpv6)
        .put("ok4", ipv4Ok)
        .put("ok6", ipv6Ok)
        .put("ms4", ipv4LatencyMs)
        .put("ms6", ipv6LatencyMs)
        .put("ip4", ipv4Address)
        .put("ip6", ipv6Address)
        .put("underlay6", underlayHasIpv6)
        .put("at", scannedAtMs)

    companion object {
        fun fromJson(o: JSONObject): IpFamilyScan = IpFamilyScan(
            endpoint = o.optString("endpoint"),
            networkKey = o.optString("network"),
            hasIpv4 = o.optBoolean("has4"),
            hasIpv6 = o.optBoolean("has6"),
            ipv4Ok = o.optBoolean("ok4"),
            ipv6Ok = o.optBoolean("ok6"),
            ipv4LatencyMs = o.optInt("ms4", -1),
            ipv6LatencyMs = o.optInt("ms6", -1),
            ipv4Address = o.optString("ip4"),
            ipv6Address = o.optString("ip6"),
            underlayHasIpv6 = o.optBoolean("underlay6"),
            scannedAtMs = o.optLong("at")
        )
    }
}

/** Counts for a whole group scan, so the page can report a batch in one line. */
data class IpFamilySummary(
    val scanned: Int = 0,
    val dual: Int = 0,
    val ipv6Only: Int = 0,
    val ipv4Only: Int = 0,
    val ipv6Unproven: Int = 0,
    val unreachable: Int = 0,
    val unknown: Int = 0
) {
    /** Every node Force IPv6 can dial right now. */
    val ipv6Capable: Int get() = dual + ipv6Only

    val line: String
        get() = "$ipv6Capable/$scanned IPv6-capable • $dual dual • $ipv6Only IPv6-only • " +
            "$ipv4Only IPv4-only" +
            (if (ipv6Unproven > 0) " • $ipv6Unproven unproven" else "") +
            (if (unreachable > 0) " • $unreachable unreachable" else "") +
            (if (unknown > 0) " • $unknown unresolved" else "")
}

object IpFamilyScanner {

    /** A family verdict is evidence for six hours; after that the node is re-measured. */
    const val TTL_MS: Long = 6L * 60L * 60L * 1_000L

    /** Encrypted DoH budget for one endpoint. Literals never reach a resolver at all. */
    const val RESOLVE_BUDGET_MS: Int = 2_500

    /** One TCP connect per family. Long enough for a distant node, short enough for a sweep. */
    const val CONNECT_BUDGET_MS: Int = 1_400

    /** At most this many addresses per family are tried, newest answer order preserved. */
    const val MAX_ADDRESSES_PER_FAMILY: Int = 2

    /** The canonical cache key of an endpoint — shared with the server-location cache. */
    fun endpointKey(host: String, port: Int): String = ServerLocationKey.of(host, port)

    /**
     * End-to-end budget for a sweep of [count] endpoints across [concurrency] workers.
     *
     * A sweep holds the app's single task slot, so it must be provably finite even if every DNS
     * server on the planet stops answering: worst case per endpoint is one resolve budget plus a
     * connect budget for every address of both families, and the waves simply multiply.
     */
    fun budgetMsFor(count: Int, concurrency: Int): Long {
        val workers = concurrency.coerceAtLeast(1)
        val waves = ((count.coerceAtLeast(1) + workers - 1) / workers).toLong()
        val perEndpointMs =
            RESOLVE_BUDGET_MS.toLong() + 2L * MAX_ADDRESSES_PER_FAMILY * CONNECT_BUDGET_MS
        return waves * perEndpointMs + 2_000L
    }

    /**
     * The verdict function, isolated from every socket so the state machine itself is pinned by
     * tests. "Has a record" and "the record answers" are kept apart on purpose: an AAAA that never
     * connects must never be reported as IPv6 support, and a node with no AAAA at all must never
     * be reported as merely "IPv6 unproven" — the first case can recover, the second cannot.
     */
    fun classify(
        hasIpv4: Boolean,
        hasIpv6: Boolean,
        ipv4Ok: Boolean,
        ipv6Ok: Boolean
    ): IpFamilyVerdict = when {
        ipv6Ok && ipv4Ok -> IpFamilyVerdict.DUAL_OK
        ipv6Ok -> IpFamilyVerdict.IPV6_ONLY
        ipv4Ok && hasIpv6 -> IpFamilyVerdict.IPV6_UNPROVEN
        ipv4Ok -> IpFamilyVerdict.IPV4_ONLY
        !hasIpv4 && !hasIpv6 -> IpFamilyVerdict.UNKNOWN
        else -> IpFamilyVerdict.UNREACHABLE
    }

    /**
     * Measure one endpoint.
     *
     * IPv6 is probed first — this is an IPv6-forward client, and on a dual-stack node the v6 answer
     * is the one the tunnel will use. Both probes always run: knowing that IPv4 also works is what
     * lets the fallback ladder promise a route instead of a refusal.
     *
     * @param resolver seam: hostname + budget → addresses. Defaults to the encrypted IP-literal
     *   DoH resolver, never the OS resolver.
     * @param connector seam: address + port + budget → latency in ms, or a negative value when the
     *   connect failed. Defaults to a bounded TCP connect.
     */
    fun scan(
        host: String,
        port: Int,
        networkKey: String = "",
        underlayHasIpv6: Boolean = AddressFamilyPolicy.underlayHasIpv6(),
        resolveBudgetMs: Int = RESOLVE_BUDGET_MS,
        connectBudgetMs: Int = CONNECT_BUDGET_MS,
        nowMs: Long = System.currentTimeMillis(),
        resolver: (String, Int) -> List<InetAddress> = { name, budget ->
            AddressFamilyPolicy.resolveWithBudget(name, budget)
        },
        connector: (InetAddress, Int, Int) -> Int = ::tcpLatencyMs
    ): IpFamilyScan {
        val key = endpointKey(host, port)
        val blank = IpFamilyScan(
            endpoint = key,
            networkKey = networkKey,
            underlayHasIpv6 = underlayHasIpv6,
            scannedAtMs = nowMs
        )
        if (key.isBlank() || port !in 1..65535) return blank

        val clean = host.trim().removeSurrounding("[", "]")
        val answers = runCatching { resolver(clean, resolveBudgetMs) }.getOrDefault(emptyList())
        val v6 = answers.filterIsInstance<Inet6Address>()
            .distinctBy { it.hostAddress.orEmpty() }
            .take(MAX_ADDRESSES_PER_FAMILY)
        val v4 = answers.filterIsInstance<Inet4Address>()
            .distinctBy { it.hostAddress.orEmpty() }
            .take(MAX_ADDRESSES_PER_FAMILY)
        if (v6.isEmpty() && v4.isEmpty()) return blank

        var ipv6Latency = -1
        var ipv6Address = v6.firstOrNull()?.hostAddress.orEmpty()
        for (address in v6) {
            val ms = runCatching { connector(address, port, connectBudgetMs) }.getOrDefault(-1)
            if (ms >= 0) {
                ipv6Latency = ms
                ipv6Address = address.hostAddress.orEmpty()
                break
            }
        }

        var ipv4Latency = -1
        var ipv4Address = v4.firstOrNull()?.hostAddress.orEmpty()
        for (address in v4) {
            val ms = runCatching { connector(address, port, connectBudgetMs) }.getOrDefault(-1)
            if (ms >= 0) {
                ipv4Latency = ms
                ipv4Address = address.hostAddress.orEmpty()
                break
            }
        }

        return IpFamilyScan(
            endpoint = key,
            networkKey = networkKey,
            hasIpv4 = v4.isNotEmpty(),
            hasIpv6 = v6.isNotEmpty(),
            ipv4Ok = ipv4Latency >= 0,
            ipv6Ok = ipv6Latency >= 0,
            ipv4LatencyMs = ipv4Latency,
            ipv6LatencyMs = ipv6Latency,
            ipv4Address = ipv4Address,
            ipv6Address = ipv6Address,
            underlayHasIpv6 = underlayHasIpv6,
            scannedAtMs = nowMs
        )
    }

    /** One bounded TCP connect. Never throws; a refusal and a timeout are the same answer here. */
    fun tcpLatencyMs(address: InetAddress, port: Int, timeoutMs: Int): Int {
        val budget = timeoutMs.coerceIn(200, 10_000)
        val startedNs = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), budget)
                ((System.nanoTime() - startedNs) / 1_000_000L).toInt().coerceIn(1, 60_000)
            }
        } catch (_: Throwable) {
            -1
        }
    }

    /** Fold a batch of verdicts into the one line a group scan reports. */
    fun summarize(scans: Collection<IpFamilyScan>): IpFamilySummary {
        var dual = 0
        var v6 = 0
        var v4 = 0
        var unproven = 0
        var dead = 0
        var unknown = 0
        for (scan in scans) {
            when (scan.verdict) {
                IpFamilyVerdict.DUAL_OK -> dual += 1
                IpFamilyVerdict.IPV6_ONLY -> v6 += 1
                IpFamilyVerdict.IPV4_ONLY -> v4 += 1
                IpFamilyVerdict.IPV6_UNPROVEN -> unproven += 1
                IpFamilyVerdict.UNREACHABLE -> dead += 1
                IpFamilyVerdict.UNKNOWN -> unknown += 1
            }
        }
        return IpFamilySummary(
            scanned = scans.size,
            dual = dual,
            ipv6Only = v6,
            ipv4Only = v4,
            ipv6Unproven = unproven,
            unreachable = dead,
            unknown = unknown
        )
    }
}
