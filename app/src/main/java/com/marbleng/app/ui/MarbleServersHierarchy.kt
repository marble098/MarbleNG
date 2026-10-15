package com.marbleng.app.ui

// MARBLE_SERVERS_HIERARCHY_V189 — the pure layer under the rebuilt Servers page.

import java.util.Locale

enum class SubscriptionUsageTier {
    CALM,

    WATCH,

    CRITICAL,

    UNKNOWN
}

object ServersHierarchy {
    const val CALM_CEILING_PERCENT = 70

    const val WATCH_CEILING_PERCENT = 90

    const val GROUP_CORNER_DP = 16f

    const val GROUP_BORDER_DP = 1.5f

    const val GROUP_NAME_SP = 16f

    const val GROUP_TILE_DP = 32f

    const val GROUP_CONTROL_DP = 32f
    const val GROUP_ICON_DP = 18f
    const val GROUP_CONTROL_GAP_DP = 3f

    const val USAGE_BAR_HEIGHT_DP = 6f

    const val GROUP_SECONDARY_SP = 10.5f

    const val LIST_CORNER_DP = 12f

    const val LIST_INSET_DP = 8f

    const val ROW_TILE_DP = 30f

    const val STANDALONE_TILE_DP = 40f

    const val ROW_NAME_SP = 13f

    const val ROW_DETAIL_SP = 10.5f

    const val ROW_PING_SP = 12.5f

    const val ROW_PAD_VERTICAL_DP = 7f
    const val ROW_PAD_START_DP = 11f

    const val ROW_DIVIDER_ALPHA = .70f

    const val FAILED_ROW_ALPHA = .58f

    fun usagePercent(usedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        val used = usedBytes.coerceAtLeast(0L)
        val tenths = used * 1000L / totalBytes
        return ((tenths + 5L) / 10L).toInt().coerceIn(0, 999)
    }

    fun usageFraction(usedBytes: Long, totalBytes: Long): Float {
        if (totalBytes <= 0L || usedBytes <= 0L) return 0f
        return (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun usageTier(usedBytes: Long, totalBytes: Long): SubscriptionUsageTier =
        if (totalBytes <= 0L) {
            SubscriptionUsageTier.UNKNOWN
        } else {
            usageTierOf(usagePercent(usedBytes, totalBytes))
        }

    fun usageTierOf(percent: Int): SubscriptionUsageTier = when {
        percent <= CALM_CEILING_PERCENT -> SubscriptionUsageTier.CALM
        percent <= WATCH_CEILING_PERCENT -> SubscriptionUsageTier.WATCH
        else -> SubscriptionUsageTier.CRITICAL
    }

    fun hasQuota(totalBytes: Long): Boolean = totalBytes > 0L

    fun compactBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun usageText(usedBytes: Long, totalBytes: Long): String =
        compactBytes(usedBytes.coerceAtLeast(0L)) + " / " +
            if (totalBytes <= 0L) "\u221E" else compactBytes(totalBytes)

    fun percentLabel(percent: Int): String = "$percent%"

    fun pluralCount(count: Int, one: String, many: String): String =
        if (count == 1) "1 $one" else "${count.coerceAtLeast(0)} $many"

    fun groupBadge(count: Int): String = pluralCount(count, "group", "groups")

    fun serverBadge(count: Int): String = pluralCount(count, "server", "servers")

    fun filterBadge(count: Int): String = pluralCount(count, "filter", "filters")

    fun activeFilterCount(
        groupByCountry: Boolean,
        onlyReachable: Boolean,
        maxPingMs: Int
    ): Int = listOf(groupByCountry, onlyReachable, maxPingMs > 0).count { it }
}
