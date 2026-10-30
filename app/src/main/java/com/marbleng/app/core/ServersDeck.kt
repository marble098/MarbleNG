package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile

/**
 * MARBLE_SERVERS_DECK_V243 — the arithmetic of a Servers page that starts with servers.
 *
 * The page used to spend four rows (search, sort strip, group strip, protocol strip) before the
 * first server, then repeat the same address four times inside the group, and offer a red
 * "Delete" label glued to the ping. None of that was a rendering accident: it was a layout with
 * no model behind it, so every new surface added another row.
 *
 * Everything here is pure and lives on the JVM side of the boundary on purpose — the compact row
 * height, the ping thresholds, the transport line, the multi-select roster, the undo window and
 * the "which chips are actually filtering something" question are contracts, and a contract that
 * can only be checked on a device is not a contract. `ServersDeckV243Test` pins each one.
 */
object ServersDeck {

    /** A compact row: 68dp is name (one line) + transport line (one line) + 6dp of air. */
    const val ROW_MIN_HEIGHT_DP: Int = 68

    /** The ceiling the redesign promised. The row may grow with large text; it may not sprawl. */
    const val ROW_MAX_HEIGHT_DP: Int = 88

    /** The ping column is reserved before any result arrives, so a result never moves the row. */
    const val PING_COLUMN_WIDTH_DP: Int = 88

    /** Material's minimum touch target. The overflow action uses exactly this square. */
    const val ACTION_TARGET_DP: Int = 48

    /** Green under this line. */
    const val FAST_LINE_MS: Double = 150.0

    /** Amber under this line, red above it. */
    const val SLOW_LINE_MS: Double = 300.0

    /** How long an undoable delete waits before it becomes true. */
    const val UNDO_WINDOW_MS: Long = 5_000L

    /** Bottom slack the list must carry so the extended FAB can never sit on the last row. */
    const val FAB_CLEARANCE_DP: Int = 96

    /** On a page with one group, the section header is the group itself: it is not drawn. */
    fun showsGroupHeaders(groupCount: Int): Boolean = groupCount > 1

    /** The compact chips only earn their row when at least one filter is really narrowing. */
    fun showsFilterStrip(activeChipCount: Int): Boolean = activeChipCount > 0
}

/**
 * MARBLE_PLAIN_VOICE_V246 — the swipe reveal is drawn only while the row is really being swiped.
 *
 * The reported bug: "when I select a server, a text appears behind the ping and the three dots".
 * It was not a text layer at all. The row's own fill turns translucent in selection
 * (`secondaryContainer` at 38 %) and the swipe-to-delete background — the `Delete 🗑` label, which
 * is right-aligned exactly where the ping column and the overflow live — was drawn underneath it
 * the whole time, at rest, at full alpha. On an opaque row nobody saw it; on a selected row it read
 * as a ghost string glued to the ping.
 *
 * The fix has two halves and this object is the arithmetic half: the reveal fades in with the
 * swipe (nothing at rest, fully there after 18 % of travel), so there is no label to show through
 * anything. The drawing half is one `graphicsLayer { alpha = … }` in `ServerRowItem`, read in the
 * draw phase so a drag never recomposes the row.
 */
object ServersRowSurface {
    const val MARKER: String = "MARBLE_PLAIN_VOICE_V246"

    /** Below this much travel the reveal is not drawn at all — a jitter is not a swipe. */
    const val REVEAL_FLOOR: Float = 0.02f

    /** By this much travel the label is fully opaque. */
    const val REVEAL_FULL: Float = 0.18f

    /** 0 at rest, 1 once the row has travelled [REVEAL_FULL]; clamped in both directions. */
    fun revealAlpha(progress: Float): Float = when {
        progress.isNaN() || progress <= REVEAL_FLOOR -> 0f
        progress >= REVEAL_FULL -> 1f
        else -> (progress - REVEAL_FLOOR) / (REVEAL_FULL - REVEAL_FLOOR)
    }

    /**
     * MARBLE_CLEAN_ROW_V249 — the alpha was the second half of V246's fix and it was still not
     * enough for one reader: an invisible layer is a layer, and a row that turns translucent when
     * it is selected is a window onto whatever sits behind it. The reveal is therefore not composed
     * at all until the finger has really moved the row, and the word "Delete" is gone from it —
     * a trash glyph on a destructive wash says the same thing without ever being able to read as a
     * label inside the row above it.
     *
     * `true` from [REVEAL_FLOOR] upwards: the boolean flips twice per drag, which is the whole
     * recomposition budget the gate costs.
     */
    fun revealComposed(progress: Float): Boolean =
        !progress.isNaN() && progress > REVEAL_FLOOR
}

/**
 * A ping reading, not a server verdict. Four levels, because a number the eye can rank at a
 * glance is worth more than a coloured dot that says "fine" three different ways.
 */
enum class PingReadout(val id: String) {
    FAST("fast"),
    OK("ok"),
    SLOW("slow"),
    TIMEOUT("timeout"),
    UNKNOWN("unknown");

    companion object {
        fun fromId(raw: String?): PingReadout =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Classify one measurement. A failed probe is [PingReadout.TIMEOUT] whatever latency it recorded,
 * because a zero latency is not a fast server — it is no answer at all. Nothing measured stays
 * [PingReadout.UNKNOWN] and is drawn as a skeleton, never as a number.
 */
fun pingReadout(latencyMs: Double?, success: Boolean = true): PingReadout = when {
    !success -> PingReadout.TIMEOUT
    latencyMs == null -> PingReadout.UNKNOWN
    latencyMs.isNaN() || latencyMs.isInfinite() -> PingReadout.UNKNOWN
    latencyMs <= 0.0 -> PingReadout.UNKNOWN
    latencyMs < ServersDeck.FAST_LINE_MS -> PingReadout.FAST
    latencyMs < ServersDeck.SLOW_LINE_MS -> PingReadout.OK
    else -> PingReadout.SLOW
}

/** How many of the four bars are lit. A timeout lights none: no reply is not a weak reply. */
fun pingBars(readout: PingReadout): Int = when (readout) {
    PingReadout.FAST -> 4
    PingReadout.OK -> 3
    PingReadout.SLOW -> 2
    PingReadout.TIMEOUT -> 0
    PingReadout.UNKNOWN -> 0
}

/**
 * The subtitle of the compact row: security and transport only — "TLS · XHTTP".
 *
 * The protocol is already the leading chip, the address is already in the details page, and the
 * name is already the title. Repeating any of them here is what made the old card feel like spam.
 */
fun transportLine(profile: ProxyProfile): String {
    val parts = buildList {
        securityWord(profile.security)?.let(::add)
        transportWord(profile.transport)?.let(::add)
    }
    return parts.joinToString(" · ")
}

/** The two-to-four letter badge the leading tile carries. */
fun protocolChip(profile: ProxyProfile): String {
    val key = ServersQuery.protocolKey(profile)
    return when (key) {
        "SHADOWSOCKS" -> "SS"
        "HYSTERIA2" -> "H2"
        "HYSTERIA" -> "HY"
        "WIREGUARD" -> "WG"
        "VLESS" -> "VL"
        "VMESS" -> "VM"
        "TROJAN" -> "TR"
        else -> key.take(2)
    }
}

internal fun securityWord(raw: String): String? = when (raw.trim().lowercase()) {
    "", "none", "native" -> null
    "reality" -> "REALITY"
    "tls" -> "TLS"
    else -> raw.trim().uppercase()
}

internal fun transportWord(raw: String): String? = when (raw.trim().lowercase()) {
    "", "tcp", "raw", "native", "none", "hysteria", "wireguard" -> null
    "ws", "websocket" -> "WS"
    "h2", "http/2", "http2" -> "H2"
    "httpupgrade" -> "HTTPUP"
    "grpc", "gun" -> "GRPC"
    "xhttp", "splithttp" -> "XHTTP"
    "mkcp", "kcp" -> "KCP"
    "quic" -> "QUIC"
    "ssh" -> "SSH"
    else -> raw.trim().uppercase()
}

/**
 * Long-press multi-select, kept as a set of profile ids so sorting or filtering cannot reassign a
 * selection to a different server.
 */
object ServersSelection {

    fun toggle(current: Set<String>, id: String): Set<String> =
        if (id in current) current - id else current + id

    /** Selects the whole visible page, or clears it when everything is already selected. */
    fun toggleAll(current: Set<String>, ids: Collection<String>): Set<String> {
        val page = ids.toSet()
        if (page.isEmpty()) return emptySet()
        return if (current.containsAll(page)) current - page else current + page
    }

    /** Drops ids that are no longer on the page; an empty roster leaves selection mode. */
    fun prune(current: Set<String>, ids: Collection<String>): Set<String> =
        current.intersect(ids.toSet())

    fun allSelected(current: Set<String>, ids: Collection<String>): Boolean {
        val page = ids.toSet()
        return page.isNotEmpty() && current.containsAll(page)
    }
}

/**
 * One server waiting out its undo window. Nothing is destroyed while the snackbar is up: the row
 * already left the page ([id] is hidden), and only the end of the window makes that true.
 */
data class PendingServerRemoval(
    val profileId: String,
    val profileName: String,
    val subscriptionId: String,
    val deadlineMs: Long
)

object ServersUndo {

    fun schedule(
        profile: ProxyProfile,
        nowMs: Long,
        windowMs: Long = ServersDeck.UNDO_WINDOW_MS
    ): PendingServerRemoval = PendingServerRemoval(
        profileId = profile.id,
        profileName = profile.name,
        subscriptionId = profile.subscriptionId,
        deadlineMs = nowMs + windowMs.coerceAtLeast(0L)
    )

    fun remainingMs(entry: PendingServerRemoval, nowMs: Long): Long =
        (entry.deadlineMs - nowMs).coerceAtLeast(0L)

    fun shouldCommit(entry: PendingServerRemoval, nowMs: Long): Boolean = nowMs >= entry.deadlineMs

    /** The row that hides itself while the snackbar is up. One at a time, by construction. */
    fun hiddenIds(entry: PendingServerRemoval?): Set<String> =
        entry?.let { setOf(it.profileId) } ?: emptySet()
}

/** A chip that is really filtering the page, and the filter it removes when tapped. */
data class ServersFilterChip(val key: String, val label: String)

/**
 * Which filters are actually narrowing the page, as removable chips.
 *
 * The redesign keeps the chip row entirely off the screen until this list is non-empty: sorting
 * and grouping are not filters, and a chip that says "All groups" while showing all groups is
 * a row of the page spent on saying nothing.
 */
fun activeFilterChips(
    filter: ServersFilter,
    sourceName: String? = null,
    reversed: Boolean = false
): List<ServersFilterChip> {
    val chips = mutableListOf<ServersFilterChip>()
    if (filter.protocol.isNotBlank()) {
        chips += ServersFilterChip("protocol", filter.protocol.uppercase())
    }
    if (filter.sourceId != "all") {
        chips += ServersFilterChip("source", sourceName?.takeIf { it.isNotBlank() } ?: filter.sourceId)
    }
    if (filter.onlyReachable) {
        chips += ServersFilterChip("reachable", "Reachable only")
    }
    if (filter.maxPingMs > ServersQuery.MAX_PING_OFF) {
        chips += ServersFilterChip("maxPing", "≤ ${filter.maxPingMs} ms")
    }
    if (reversed) {
        chips += ServersFilterChip("reverse", "Reversed")
    }
    return chips
}
