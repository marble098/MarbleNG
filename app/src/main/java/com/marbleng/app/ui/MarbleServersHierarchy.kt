package com.marbleng.app.ui

// MARBLE_SERVERS_HIERARCHY_V189 — the pure layer under the rebuilt Servers page.
//
// The page is a two-level hierarchy now: a subscription card (level 1) and the servers stacked
// *inside* it (level 2). Two things decide whether that hierarchy is actually visible: how much
// smaller level 2 is than level 1 in every dimension, and what a subscription's data plan says.
// Both are plain numbers and plain functions here, so the drawing code in Aether2026.kt reads one
// table and ordinary JVM unit tests pin it — the nesting can never drift back into "two rows of
// the same size" without a test failing first.
//
// Nothing in this file imports Compose. It is arithmetic and a size table, nothing else.

import java.util.Locale

/** Where a subscription's data plan sits on the quota its own provider reported. */
enum class SubscriptionUsageTier {
    /** Up to [ServersHierarchy.CALM_CEILING_PERCENT] percent of the plan consumed. */
    CALM,

    /** Past calm, up to [ServersHierarchy.WATCH_CEILING_PERCENT] percent. */
    WATCH,

    /** Past watch — the plan is about to run out, or already has. */
    CRITICAL,

    /** The provider reported no quota, so there is nothing to measure and no bar to fill. */
    UNKNOWN
}

/**
 * The one table the Servers page reads.
 *
 * Level 1 (the subscription card) owns the largest corner radius, the boldest border, the largest
 * type and the largest controls. Level 2 (a server inside that card) is smaller in *every*
 * dimension, sits inset from the card's edge, and never draws a border of its own — rows are
 * separated by a hairline, and only the row that carries traffic grows one.
 */
object ServersHierarchy {

    // ------------------------------------------------------------------ plan thresholds
    /** Green up to and including this percent. */
    const val CALM_CEILING_PERCENT = 70

    /** Amber up to and including this percent; above it the plan is critical. */
    const val WATCH_CEILING_PERCENT = 90

    // ------------------------------------------------------------------ level 1: subscription card
    /** The largest corner radius on the page. */
    const val GROUP_CORNER_DP = 16f

    /** The boldest hairline on the page: the card that contains everything else. */
    const val GROUP_BORDER_DP = 1.5f

    /** The largest type on the page: a subscription's name. */
    const val GROUP_NAME_SP = 16f

    /** The chevron tile that folds the card. */
    const val GROUP_TILE_DP = 32f

    /** Refresh / status / menu — three equal controls, evenly spaced. */
    const val GROUP_CONTROL_DP = 32f
    const val GROUP_ICON_DP = 18f
    const val GROUP_CONTROL_GAP_DP = 3f

    /** The usage bar's height, and the radius of its own ends. */
    const val USAGE_BAR_HEIGHT_DP = 6f

    /** The secondary line under the bar: expiry, website, auto-update. */
    const val GROUP_SECONDARY_SP = 10.5f

    // ------------------------------------------------------------------ level 2: a server inside it
    /** The nested list block's corner radius — always smaller than [GROUP_CORNER_DP]. */
    const val LIST_CORNER_DP = 12f

    /** How far the nested block sits inside the card's edge, on both sides. */
    const val LIST_INSET_DP = 8f

    /** The protocol tile inside a subscription. A standalone server row keeps a larger one. */
    const val ROW_TILE_DP = 30f

    /** The tile a server gets when it is shown on its own (Home), for the size step to be real. */
    const val STANDALONE_TILE_DP = 40f

    /** The server's name — one step under the subscription name. */
    const val ROW_NAME_SP = 13f

    /** The endpoint under the name. */
    const val ROW_DETAIL_SP = 10.5f

    /** The latency number, one step under the name it belongs to. */
    const val ROW_PING_SP = 12.5f

    /** Tighter rows: the list is a list, not a stack of cards. */
    const val ROW_PAD_VERTICAL_DP = 7f
    const val ROW_PAD_START_DP = 11f

    /** The hairline between two servers, and how quiet it stays. */
    const val ROW_DIVIDER_ALPHA = .70f

    /** A server a probe could not reach reads faded, with a cross — never in a loud red. */
    const val FAILED_ROW_ALPHA = .58f

    // ------------------------------------------------------------------ plan arithmetic

    /**
     * Percent of the plan consumed, rounded to the nearest whole number, or 0 when the provider
     * reported no quota.
     *
     * The rounding happens here, once, and the tier is derived from the same rounded number the
     * label prints — so the percent on screen and the colour of the bar can never disagree about
     * which side of 70 or 90 a plan is on.
     */
    fun usagePercent(usedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        val used = usedBytes.coerceAtLeast(0L)
        val tenths = used * 1000L / totalBytes
        return ((tenths + 5L) / 10L).toInt().coerceIn(0, 999)
    }

    /** The 0f..1f fill of the usage bar, clamped so an over-quota plan fills the track exactly. */
    fun usageFraction(usedBytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L || usedBytes <= 0L) return 0f
        return (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    /** The tier of a plan, from the same rounded percent its label prints. */
    fun usageTier(usedBytes: Long, totalBytes: Long): SubscriptionUsageTier =
        if (totalBytes <= 0L) {
            SubscriptionUsageTier.UNKNOWN
        } else {
            usageTierOf(usagePercent(usedBytes, totalBytes))
        }

    /** The tier of an already-rounded percent. */
    fun usageTierOf(percent: Int): SubscriptionUsageTier = when {
        percent <= CALM_CEILING_PERCENT -> SubscriptionUsageTier.CALM
        percent <= WATCH_CEILING_PERCENT -> SubscriptionUsageTier.WATCH
        else -> SubscriptionUsageTier.CRITICAL
    }

    /** True when the provider reported a real quota, i.e. when a usage bar means anything. */
    fun hasQuota(totalBytes: Long): Boolean = totalBytes > 0L

    /**
     * Human-sized byte count. Latin digits on purpose: every other number in the product prints
     * Latin digits, including the Persian lexicon's own count patterns.
     */
    fun compactBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    /** "1.4 GB / 50.0 GB", or "1.4 GB / ∞" when the plan is unmetered. */
    fun usageText(usedBytes: Long, totalBytes: Long): String =
        compactBytes(usedBytes.coerceAtLeast(0L)) + " / " +
            if (totalBytes <= 0L) "\u221E" else compactBytes(totalBytes)

    /** The percent printed beside the bar. */
    fun percentLabel(percent: Int): String = "$percent%"

    // ------------------------------------------------------------------ count badges
    //
    // The filter rail used to leave its counts on a line of their own above it. They live inside
    // the controls now, so each control states what it is scoping and how much is in that scope.

    /** "1 group" / "12 groups" — the English form the Persian lexicon pattern-matches. */
    fun pluralCount(count: Int, one: String, many: String): String =
        if (count == 1) "1 $one" else "${count.coerceAtLeast(0)} $many"

    /** The badge inside the group control. */
    fun groupBadge(count: Int): String = pluralCount(count, "group", "groups")

    /** The badge inside the protocol control. */
    fun serverBadge(count: Int): String = pluralCount(count, "server", "servers")

    /** The badge on the advanced-filter control: how many filters are narrowing the list. */
    fun filterBadge(count: Int): String = pluralCount(count, "filter", "filters")

    /** How many advanced filters are on, from the three switches the menu owns. */
    fun activeFilterCount(
        groupByCountry: Boolean,
        onlyReachable: Boolean,
        maxPingMs: Int
    ): Int = listOf(groupByCountry, onlyReachable, maxPingMs > 0).count { it }
}
