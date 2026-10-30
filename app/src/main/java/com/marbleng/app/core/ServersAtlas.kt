package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.Subscription

/**
 * MARBLE_SERVERS_ATLAS_V244 — the arithmetic of a Servers page that answers three questions
 * before the first row: *which library am I in*, *which route is live*, and *where is each
 * server*.
 *
 * V243 made the page a list of servers. The council that reviewed it found the list was honest
 * but blind: every row looked the same, the country the app had already measured lived in a
 * settings page, and choosing a subscription meant opening a filter sheet, scrolling a chip
 * strip and dismissing it. The rail, the flag tiles and the row budget are decisions with
 * numbers behind them, so they live here — pure, on the JVM — and `ServersAtlasV244Test` pins
 * every one of them without a device.
 */
object ServersAtlas {

    /** The leading flag tile of a row: 4:3, the shape a flag is read in. */
    const val ROW_FLAG_WIDTH_DP: Int = 38

    const val ROW_FLAG_HEIGHT_DP: Int = 28

    const val ROW_FLAG_CORNER_DP: Int = 9

    /** One card of the sources rail. Wide enough for a name, short enough to keep three in view. */
    const val LIBRARY_CARD_WIDTH_DP: Int = 148

    /** Name + flag cluster + a 3dp plan bar, with air. */
    const val LIBRARY_CARD_HEIGHT_DP: Int = 84

    const val LIBRARY_FLAG_SIZE_DP: Int = 20

    /** How much of a stacked flag circle sits on top of the previous one. */
    const val LIBRARY_FLAG_OVERLAP_DP: Int = 7

    /** The flag at the head of a section header. */
    const val SECTION_FLAG_SIZE_DP: Int = 22

    /** A cluster is a signature, not a list: four flags is the most an eye reads as "a set". */
    const val CLUSTER_LIMIT: Int = 4

    /**
     * Everything a row spends before its text starts: leading inset, the flag tile, the three
     * gaps, the reserved ping column ([ServersDeck.PING_COLUMN_WIDTH_DP]) and the 48dp overflow
     * target, plus the trailing inset.
     */
    const val ROW_FIXED_WIDTH_DP: Int = 212

    /** The width the row must leave for the name at the narrowest supported phone (360dp). */
    const val ROW_TEXT_MIN_WIDTH_DP: Int = 108

    /** The narrowest phone the layout is designed against, in dp, after the page's 20dp gutters. */
    const val NARROW_PAGE_WIDTH_DP: Int = 320

    /** Below this many unlocated servers there is nothing worth telling the user about. */
    const val LOCATION_NOTICE_MIN: Int = 1

    /**
     * The flag cluster of a set of servers: most common country first, then alphabetical.
     *
     * Duplicates collapse because three German servers are one flag, and an unknown country is
     * not a flag at all — the tile that would draw it says "no location yet" instead of inventing
     * one. Ordering by frequency means the cluster describes the library, not the first three
     * rows that happened to be parsed.
     */
    fun flagCluster(codes: List<String>, limit: Int = CLUSTER_LIMIT): List<String> {
        if (limit <= 0) return emptyList()
        return codes
            .map { it.trim().uppercase() }
            .filter { code -> code.length == 2 && code.all { it in 'A'..'Z' } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(limit)
    }

    /** The plan's consumed share, clamped; a plan without a ceiling has no bar. */
    fun usageFraction(usedBytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L) return 0f
        val used = usedBytes.coerceAtLeast(0L)
        return (used.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /** The one line under a server's name: where it is, then how it carries traffic. */
    fun rowSubtitle(profile: ProxyProfile, countryName: String = ""): String {
        val head = countryName.trim().ifBlank { ServersQuery.protocolKey(profile) }
        val transport = transportLine(profile)
        return listOf(head, transport)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    /** True when the flag sweep is worth a line of its own. */
    fun showsLocationNotice(locatedCount: Int): Boolean = locatedCount >= LOCATION_NOTICE_MIN
}

/**
 * One card of the sources rail — "All sources", a subscription, or the manual bucket.
 *
 * [flags] is the country cluster the card draws, [usageFraction] the plan bar under it, and
 * [subscription] the row's management target (null for "all" and for the manual bucket).
 */
data class ServersLibraryCard(
    val id: String,
    val title: String,
    val count: Int,
    val flags: List<String>,
    val usageFraction: Float = 0f,
    val usageLabel: String = "",
    val subscription: Subscription? = null
) {
    val isAll: Boolean get() = id == ALL_SOURCES_ID

    val isManual: Boolean get() = id == MANUAL_SOURCE_ID

    /** A source with no nodes is a fact worth seeing (it refreshed to nothing), not a card to hide. */
    val isEmpty: Boolean get() = count <= 0

    companion object {
        const val ALL_SOURCES_ID: String = "all"

        const val MANUAL_SOURCE_ID: String = "manual"
    }
}

/**
 * The rail, built from the library itself: "All sources" first, then every subscription in the
 * order the user added them, then the manual bucket when it holds anything.
 *
 * [countryCodeOf] is the app's own location answer for a profile (normally
 * `repository.serverLocation(profile).code`), passed in so this stays pure.
 */
fun libraryCards(
    subscriptions: List<Subscription>,
    profiles: List<ProxyProfile>,
    countryCodeOf: (ProxyProfile) -> String,
    allTitle: String,
    manualTitle: String
): List<ServersLibraryCard> {
    fun card(id: String, title: String, subscription: Subscription?): ServersLibraryCard {
        val mine = profiles.filter { it.subscriptionId == id }
        val used = subscription?.let { it.uploadBytes + it.downloadBytes } ?: 0L
        val total = subscription?.totalBytes ?: 0L
        return ServersLibraryCard(
            id = id,
            title = title,
            count = mine.size,
            flags = ServersAtlas.flagCluster(mine.map(countryCodeOf)),
            usageFraction = ServersAtlas.usageFraction(used, total),
            subscription = subscription
        )
    }

    val cards = mutableListOf(
        card(ServersLibraryCard.ALL_SOURCES_ID, allTitle, null)
    )
    subscriptions.forEach { sub -> cards += card(sub.id, sub.name, sub) }
    if (profiles.any { it.subscriptionId == ServersLibraryCard.MANUAL_SOURCE_ID }) {
        cards += card(ServersLibraryCard.MANUAL_SOURCE_ID, manualTitle, null)
    }
    return cards
}
