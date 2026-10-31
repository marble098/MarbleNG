package com.marbleng.app.model

import java.util.Locale

/**
 * MARBLE_IP_IDENTITY_V251 — the arithmetic of the IP page.
 *
 * The fourth tab used to be a user-owned shortcut (pulse, a subscription group or one pinned
 * server). It is now the one place that answers the question that tab was never able to answer
 * with a shortcut: *what address is this phone actually using right now*. Every number and every
 * label on that page is computed here, in plain Kotlin, so it is proven on the JVM
 * (`IpIdentityV251Test`) instead of only on a device.
 *
 * Three rules the whole file obeys:
 *
 * 1. **A literal is never resolved.** Addresses arrive as text from `NetworkInterface` and are
 *    classified by arithmetic on their bytes. Nothing here resolves a host name, because that
 *    would turn drawing a screen into a DNS query.
 * 2. **A scope is a fact, not a guess.** Private, loopback, link-local, carrier-grade NAT, NAT64,
 *    documentation and multicast each get their own name, so the page can say *which* address it
 *    is showing instead of printing a number and hoping.
 * 3. **The tunnel is not the network.** An address that belongs to a `tun`/`utun`/`ppp`/`wg`
 *    interface is the address the tunnel lends the phone; an address on `wlan0`/`rmnet` is the real
 *    one underneath. The page separates the two and never mixes them in one count.
 */

enum class IpKind(val label: String) {
    IPV4("IPv4"),
    IPV6("IPv6"),
    OTHER("Other")
}

enum class IpScope(val id: String) {
    PUBLIC("public"),
    PRIVATE("private"),
    CGNAT("cgnat"),
    LOOPBACK("loopback"),
    LINK_LOCAL("link_local"),
    UNIQUE_LOCAL("unique_local"),
    NAT64("nat64"),
    DOCUMENTATION("documentation"),
    MULTICAST("multicast"),
    UNSPECIFIED("unspecified"),
    OTHER("other");

    /** True for the scopes that can be drawn as "a real address on the internet". */
    val routable: Boolean
        get() = this == PUBLIC || this == NAT64 || this == CGNAT
}

/** One address as the device holds it, with the interface that owns it. */
data class IpAddressEntry(
    val address: String,
    val prefixLength: Int = -1,
    val kind: IpKind = IpKind.OTHER,
    val scope: IpScope = IpScope.OTHER,
    val interfaceName: String = "",
    val tunnel: Boolean = false
) {
    val short: String get() = IpAddressBook.shorten(address)

    val cidr: String
        get() = when {
            address.isBlank() -> ""
            prefixLength < 0 -> address
            else -> "$address/$prefixLength"
        }

    val isPublic: Boolean get() = scope == IpScope.PUBLIC
}

/** One reachability witness: does this network actually carry that family right now? */
data class IpReachability(
    val label: String,
    val target: String,
    val kind: IpKind,
    val ok: Boolean = false,
    val latencyMs: Int = -1,
    val detail: String = ""
)

/** One observed public address: through the tunnel, or straight off the device. */
data class IpEgress(
    val family: IpKind,
    val address: String = "",
    val viaTunnel: Boolean = false,
    /** True for a globally routable literal, false for one the network lends locally. */
    val global: Boolean = false,
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
    val asn: String = "",
    val organization: String = "",
    val detail: String = "",
    /** MARBLE_IP_PASSPORT_V253 — the browserleaks.com/ip set: region and time zone ride along. */
    val region: String = "",
    val timezone: String = ""
) {
    val discovered: Boolean get() = address.isNotBlank()
}

/**
 * The whole answer of one sweep. Everything is optional and every field has a default, because a
 * page that is opened while offline still has to draw: it shows what it knows and says what it
 * could not measure, instead of drawing an empty screen.
 */
data class IpStackReport(
    val capturedAtMs: Long = 0L,
    val transport: String = "",
    val metered: Boolean = false,
    val validated: Boolean = false,
    val mtu: Int = 0,
    val addresses: List<IpAddressEntry> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val reachability: List<IpReachability> = emptyList(),
    val egress: List<IpEgress> = emptyList(),
    val tunnelActive: Boolean = false,
    val lookupRunning: Boolean = false,
    val note: String = ""
) {
    val tunnelAddresses: List<IpAddressEntry>
        get() = addresses.filter { it.tunnel }

    val underlayAddresses: List<IpAddressEntry>
        get() = addresses.filterNot { it.tunnel }

    val ipv4Addresses: List<IpAddressEntry> get() = addresses.filter { it.kind == IpKind.IPV4 }
    val ipv6Addresses: List<IpAddressEntry> get() = addresses.filter { it.kind == IpKind.IPV6 }

    val hasIpv4: Boolean get() = ipv4Addresses.any { it.scope != IpScope.UNSPECIFIED }
    val hasIpv6: Boolean get() = ipv6Addresses.any { it.scope != IpScope.UNSPECIFIED }

    /**
     * The address a person means when they ask "what is my IP": the one on this network, IPv4 first.
     *
     * A dual-stack phone holds `192.168.1.12` and `2606:…` at once, and the v4 is the one a router's
     * admin page, a LAN game or a support call will recognise. The order is therefore public v4,
     * any v4, public v6, then whatever is left — never just "the first entry", which depended on the
     * kernel's ordering.
     */
    val primaryDeviceAddress: IpAddressEntry?
        get() = underlayAddresses.firstOrNull { it.kind == IpKind.IPV4 && it.isPublic }
            ?: underlayAddresses.firstOrNull { it.kind == IpKind.IPV4 }
            ?: underlayAddresses.firstOrNull { it.isPublic }
            ?: underlayAddresses.firstOrNull()

    /** The internet-facing IPv4, when one was actually observed (never a local one). */
    val publicV4: IpEgress?
        get() = egress.firstOrNull { it.family == IpKind.IPV4 && it.discovered && it.global }

    /** The internet-facing IPv6, when one was actually observed. */
    val publicV6: IpEgress?
        get() = egress.firstOrNull { it.family == IpKind.IPV6 && it.discovered && it.global }

    /** The exit as the tunnel shows it, even if the tunnel lent a local address. */
    val exitIsGlobal: Boolean get() = exit?.global == true

    /**
     * The exit identity: what the internet believes this phone is, tunnel included.
     *
     * A locally scoped address a tunnel lends the device (10.8.0.2 on `tun0`) is a real observation
     * but it is not an exit, so a global tunnel answer wins over a local one; only when no global
     * answer exists does the page fall back to what it has, and [exitIsGlobal] says which happened.
     */
    val exit: IpEgress?
        get() = egress.firstOrNull { it.viaTunnel && it.discovered && it.global }
            ?: egress.firstOrNull { it.discovered && it.global }
            ?: egress.firstOrNull { it.viaTunnel && it.discovered }
            ?: egress.firstOrNull { it.discovered }

    val reachableV4: IpReachability? get() = reachability.firstOrNull { it.kind == IpKind.IPV4 }
    val reachableV6: IpReachability? get() = reachability.firstOrNull { it.kind == IpKind.IPV6 }

    val networkLine: String
        get() = IpAddressBook.networkLine(this)

    val addressLine: String
        get() = IpAddressBook.addressCountLine(this)

    /** A stable key for caching: the sweep is re-run when the network underneath changes. */
    fun networkKey(): String = listOf(
        transport,
        if (hasIpv4) "v4" else "no4",
        if (hasIpv6) "v6" else "no6",
        if (tunnelActive) "tun" else "direct",
        "m$mtu"
    ).joinToString("|")
}

/** The device half of a sweep, before any network measurement has happened. */
data class IpDeviceSnapshot(
    val addresses: List<IpAddressEntry> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val transport: String = "",
    val metered: Boolean = false,
    val validated: Boolean = false,
    val mtu: Int = 0,
    val tunnelActive: Boolean = false
)

object IpAddressBook {
    const val MARKER: String = "MARBLE_IP_IDENTITY_V251"

    /** The largest number of addresses the page draws before it says "and n more". */
    const val MAX_LISTED_ADDRESSES: Int = 16

    private val TUNNEL_PREFIXES = listOf("tun", "utun", "ppp", "tap", "wg", "ipsec", "clat")

    /** Interface names that mean "this address was lent to us by a tunnel". */
    fun isTunnelInterface(name: String): Boolean {
        val lower = name.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return false
        return TUNNEL_PREFIXES.any { prefix -> lower.startsWith(prefix) }
    }

    fun kindOf(address: String): IpKind = when {
        parseIpv4(address) != null -> IpKind.IPV4
        parseIpv6(address) != null -> IpKind.IPV6
        else -> IpKind.OTHER
    }

    /**
     * Classify a literal by its own bytes. Unknown text is [IpScope.OTHER], never a query.
     *
     * The zone is stripped before the brackets, not after: `[fe80::1%wlan0]` is exactly how a
     * link-local address arrives from a socket, and reading the zone first left the leading `[`
     * glued to the literal, which silently made it "other" instead of "link-local".
     */
    fun scopeOf(address: String): IpScope {
        val raw = address.trim().removeSurrounding("[", "]").substringBefore('%').trim()
        parseIpv4(raw)?.let { return scopeOfV4(it) }
        parseIpv6(raw)?.let { return scopeOfV6(it) }
        return IpScope.OTHER
    }

    private fun scopeOfV4(octets: List<Int>): IpScope {
        val a = octets[0]
        val b = octets[1]
        val c = octets[2]
        return when {
            a == 0 -> IpScope.UNSPECIFIED
            a == 127 -> IpScope.LOOPBACK
            a == 10 -> IpScope.PRIVATE
            a == 172 && b in 16..31 -> IpScope.PRIVATE
            a == 192 && b == 168 -> IpScope.PRIVATE
            a == 169 && b == 254 -> IpScope.LINK_LOCAL
            a == 100 && b in 64..127 -> IpScope.CGNAT
            a == 192 && b == 0 && c == 2 -> IpScope.DOCUMENTATION
            a == 198 && b == 51 && c == 100 -> IpScope.DOCUMENTATION
            a == 203 && b == 0 && c == 113 -> IpScope.DOCUMENTATION
            a in 224..239 -> IpScope.MULTICAST
            a >= 240 -> IpScope.OTHER
            else -> IpScope.PUBLIC
        }
    }

    private fun scopeOfV6(groups: List<Int>): IpScope {
        if (groups.isEmpty()) return IpScope.OTHER
        val first = groups[0]
        val allZero = groups.all { it == 0 }
        return when {
            allZero -> IpScope.UNSPECIFIED
            allZero.not() && groups.dropLast(1).all { it == 0 } && groups.last() == 1 -> IpScope.LOOPBACK
            // fe80::/10 is a ten-bit prefix: masking with 0xFE00 (nine bits) let 0xFE80 read as
            // 0xFE00 and every link-local address fall through to "public".
            first and 0xFFC0 == 0xFE80 -> IpScope.LINK_LOCAL
            first and 0xFE00 == 0xFC00 -> IpScope.UNIQUE_LOCAL
            first and 0xFF00 == 0xFF00 -> IpScope.MULTICAST
            first == 0x0064 && groups.getOrNull(1) == 0xFF9B -> IpScope.NAT64
            first == 0x2001 && groups.getOrNull(1) == 0x0DB8 -> IpScope.DOCUMENTATION
            first == 0x2002 -> IpScope.OTHER
            else -> IpScope.PUBLIC
        }
    }

    fun parseIpv4(address: String): List<Int>? {
        val raw = address.trim()
        if (raw.isEmpty() || raw.contains(':')) return null
        val parts = raw.split('.')
        if (parts.size != 4) return null
        val octets = ArrayList<Int>(4)
        parts.forEach { part ->
            if (part.isEmpty() || part.length > 3) return null
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            octets.add(value)
        }
        return octets
    }

    /** IPv6 with one `::`, optional zone, and an optional trailing dotted quad. */
    fun parseIpv6(address: String): List<Int>? {
        val raw = address.trim()
            .removeSurrounding("[", "]")
            .substringBefore('%')
            .trim()
            .lowercase(Locale.US)
        if (raw.isEmpty() || !raw.contains(':')) return null
        val head: List<Int>
        val tail: List<Int>
        val halves = raw.split("::")
        if (halves.size > 2) return null
        if (halves.size == 2) {
            head = groupsOf(halves[0]) ?: return null
            tail = groupsOf(halves[1]) ?: return null
            if (head.size + tail.size > 7) return null
            val zeros = 8 - head.size - tail.size
            if (zeros < 1) return null
            return head + List(zeros) { 0 } + tail
        }
        val groups = groupsOf(raw) ?: return null
        return groups.takeIf { it.size == 8 }
    }

    private fun groupsOf(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val pieces = part.split(':')
        val out = ArrayList<Int>(pieces.size)
        pieces.forEachIndexed { index, piece ->
            if (piece.isEmpty()) return null
            if (piece.contains('.')) {
                if (index != pieces.lastIndex) return null
                val v4 = parseIpv4(piece) ?: return null
                out.add((v4[0] shl 8) or v4[1])
                out.add((v4[2] shl 8) or v4[3])
            } else {
                if (piece.length > 4) return null
                val value = piece.toIntOrNull(16) ?: return null
                out.add(value)
            }
        }
        return out
    }

    /** Lower case, no zone, no brackets: the form an address is spoken and copied in. */
    fun shorten(address: String): String =
        address.trim()
            .removeSurrounding("[", "]")
            .substringBefore('%')
            .trim()
            .lowercase(Locale.US)

    /** 192.168.1.12/24 -> 192.168.1.0/24 for IPv4; a v6 literal is shown as written. */
    fun networkOf(entry: IpAddressEntry): String {
        if (entry.prefixLength < 0) return entry.address
        val octets = parseIpv4(entry.address) ?: return entry.cidr
        val prefix = entry.prefixLength.coerceIn(0, 32)
        // Bits *left in this octet*, counted from the start of the address — not a running
        // remainder: mutating it made 172.20.3.4/12 come out as 172.0.0.0/12.
        val masked = List(4) { index ->
            val bits = (prefix - index * 8).coerceIn(0, 8)
            if (bits == 0) 0 else (octets[index] and ((0xFF shl (8 - bits)) and 0xFF))
        }
        return masked.joinToString(".") + "/${entry.prefixLength}"
    }

    /** IPv4 addresses first, then IPv6, then the tunnels last: the page reads top down. */
    fun ordered(entries: List<IpAddressEntry>): List<IpAddressEntry> =
        entries.sortedWith(
            compareBy<IpAddressEntry>(
                { if (it.tunnel) 1 else 0 },
                { if (it.kind == IpKind.IPV4) 0 else if (it.kind == IpKind.IPV6) 1 else 2 },
                { if (it.scope == IpScope.PUBLIC) 0 else if (it.scope.routable) 1 else 2 },
                { it.interfaceName }
            )
        )

    fun addressCountLine(report: IpStackReport): String = buildString {
        append("${report.ipv4Addresses.size} IPv4")
        append(" · ")
        append("${report.ipv6Addresses.size} IPv6")
        val landlines = report.underlayAddresses.size
        if (landlines > 0) append(" · $landlines on this network")
        if (report.tunnelAddresses.isNotEmpty()) {
            append(" · ${report.tunnelAddresses.size} in the tunnel")
        }
    }

    fun networkLine(report: IpStackReport): String = buildString {
        append(report.transport.ifBlank { "unknown" }.uppercase(Locale.US))
        append(" · ")
        append(
            when {
                report.hasIpv4 && report.hasIpv6 -> "dual stack"
                report.hasIpv6 -> "IPv6 only"
                report.hasIpv4 -> "IPv4 only"
                else -> "no address yet"
            }
        )
        if (report.mtu > 0) append(" · MTU ${report.mtu}")
        if (report.metered) append(" · metered")
    }

    /**
     * The one-line verdict on the *device* half: does this network carry each family, and is that
     * answer measured or merely advertised? `advertised` is what the interface says; the page only
     * claims what a witness confirmed.
     */
    fun familyVerdict(report: IpStackReport): String {
        val v4 = report.reachableV4
        val v6 = report.reachableV6
        if (v4 == null && v6 == null) return "not measured yet"
        return buildString {
            append("IPv4 ")
            append(
                when {
                    v4 == null -> "not measured"
                    v4.ok -> "reached in ${v4.latencyMs} ms"
                    v4.detail.isNotBlank() -> v4.detail
                    else -> "no answer"
                }
            )
            append(" · IPv6 ")
            append(
                when {
                    v6 == null -> "not measured"
                    v6.ok -> "reached in ${v6.latencyMs} ms"
                    v6.detail.isNotBlank() -> v6.detail
                    else -> "no answer"
                }
            )
        }
    }

    fun egressLine(observation: IpEgress?): String = when {
        observation == null -> ""
        observation.address.isBlank() -> observation.detail.ifBlank { "not discovered" }
        else -> observation.address
    }

    /** `Berlin, Germany · AS15169 Google` — assembled only from what was actually answered. */
    fun egressDetail(observation: IpEgress?): String {
        if (observation == null) return ""
        val place = listOf(observation.city, observation.region, observation.country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        val network = listOf(observation.asn, observation.organization)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return listOf(place, network, observation.detail)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    /**
     * What the page says about leaks, from the two observations alone: the address the tunnel
     * shows and the address the device shows. If the second one is visible through the tunnel as
     * well, the page says so; if it was never discovered, the page says that too.
     */
    fun leakLine(report: IpStackReport): String {
        val exit = report.exit
        val direct = report.egress.firstOrNull { !it.viaTunnel && it.discovered }
        return when {
            exit == null && direct == null -> "No public address was observed"
            exit == null -> "Only the direct address was observed · the tunnel did not answer"
            direct == null -> "Only the tunnel address was observed · the network underneath was not measured"
            exit.address.equals(direct.address, ignoreCase = true) ->
                "The tunnel and the network show the same address · the exit is not being replaced"
            !report.tunnelActive -> "The tunnel is down · the direct address is what the internet sees"
            else -> "The tunnel shows a different address than the network · the real address is hidden"
        }
    }

    fun reachabilityOf(report: IpStackReport, kind: IpKind): IpReachability? =
        report.reachability.firstOrNull { it.kind == kind }

    fun targets(): List<Pair<String, IpKind>> = listOf(
        "1.1.1.1" to IpKind.IPV4,
        "8.8.8.8" to IpKind.IPV4,
        "2606:4700:4700::1111" to IpKind.IPV6,
        "2001:4860:4860::8888" to IpKind.IPV6
    )
}
