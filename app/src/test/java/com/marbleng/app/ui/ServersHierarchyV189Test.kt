package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVERS_HIERARCHY_V189 — the rebuilt Servers page, pinned.
 *
 * The page is two levels: a subscription card and the servers nested inside it. Two things make
 * that hierarchy real instead of decorative, and both are pure:
 *
 *  1. **the size table** — level 2 has to be smaller than level 1 in every dimension it shares
 *     with it (tile, name, corner radius), or the nesting is a claim the screen does not keep;
 *  2. **the plan arithmetic** — the usage text, the bar's fill and the bar's colour are three
 *     readings of one rounded percent, so they can never disagree about which side of 70 % or
 *     90 % a subscription is on.
 *
 * The drawing code is allowed to evolve; these numbers are the contract.
 */
class ServersHierarchyV189Test {

    // ------------------------------------------------------------------ level 2 is smaller

    @Test
    fun aNestedServerIsSmallerThanTheCardThatHoldsIt() {
        assertTrue(
            "the nested protocol tile must be smaller than a standalone server's",
            ServersHierarchy.ROW_TILE_DP < ServersHierarchy.STANDALONE_TILE_DP
        )
        assertTrue(
            "the nested protocol tile must be smaller than the card's own chevron tile",
            ServersHierarchy.ROW_TILE_DP < ServersHierarchy.GROUP_TILE_DP
        )
        assertTrue(
            "a server's name must sit a step under its subscription's name",
            ServersHierarchy.ROW_NAME_SP < ServersHierarchy.GROUP_NAME_SP
        )
        assertTrue(
            "the latency must not out-shout the name it belongs to",
            ServersHierarchy.ROW_PING_SP < ServersHierarchy.ROW_NAME_SP
        )
        assertTrue(
            "the endpoint is the quietest type in the row",
            ServersHierarchy.ROW_DETAIL_SP < ServersHierarchy.ROW_PING_SP
        )
    }

    @Test
    fun theNestedBlockIsInsetAndRoundsLessThanTheCard() {
        assertTrue(
            "the nested list must sit inside the card's edge",
            ServersHierarchy.LIST_INSET_DP > 0f
        )
        assertTrue(
            "the nested block's radius must be smaller than the card's",
            ServersHierarchy.LIST_CORNER_DP < ServersHierarchy.GROUP_CORNER_DP
        )
        assertEquals(16f, ServersHierarchy.GROUP_CORNER_DP, 0f)
        assertEquals(12f, ServersHierarchy.LIST_CORNER_DP, 0f)
        assertEquals(30f, ServersHierarchy.ROW_TILE_DP, 0f)
        assertEquals(13f, ServersHierarchy.ROW_NAME_SP, 0f)
    }

    @Test
    fun theCardCarriesTheBoldestOutlineOnThePage() {
        assertTrue(
            "a subscription card is the one outline that contains everything else",
            ServersHierarchy.GROUP_BORDER_DP > 1f
        )
    }

    @Test
    fun anUnreachableServerFadesInsteadOfShouting() {
        assertTrue(
            "a failed row reads faded",
            ServersHierarchy.FAILED_ROW_ALPHA < 1f
        )
        assertTrue(
            "…but it still has to be readable",
            ServersHierarchy.FAILED_ROW_ALPHA > .4f
        )
    }

    // ------------------------------------------------------------------ plan arithmetic

    @Test
    fun usagePercentRoundsToTheNearestWholeNumber() {
        assertEquals(0, ServersHierarchy.usagePercent(0L, 100L))
        assertEquals(50, ServersHierarchy.usagePercent(50L, 100L))
        // 69.9 % and 70.4 % both print 70; 70.5 % prints 71.
        assertEquals(70, ServersHierarchy.usagePercent(699L, 1_000L))
        assertEquals(70, ServersHierarchy.usagePercent(704L, 1_000L))
        assertEquals(71, ServersHierarchy.usagePercent(705L, 1_000L))
        assertEquals(100, ServersHierarchy.usagePercent(999L, 1_000L))
    }

    @Test
    fun anOverQuotaPlanReportsMoreThanAHundredPercentButNeverANegativeOne() {
        assertEquals(150, ServersHierarchy.usagePercent(150L, 100L))
        assertEquals(0, ServersHierarchy.usagePercent(-50L, 100L))
        // A provider that reported nothing at all has no percent to show.
        assertEquals(0, ServersHierarchy.usagePercent(50L, 0L))
    }

    @Test
    fun theBarColourComesFromTheSamePercentTheLabelPrints() {
        assertEquals(SubscriptionUsageTier.CALM, ServersHierarchy.usageTier(700L, 1_000L))
        assertEquals(SubscriptionUsageTier.WATCH, ServersHierarchy.usageTier(705L, 1_000L))
        assertEquals(SubscriptionUsageTier.WATCH, ServersHierarchy.usageTier(900L, 1_000L))
        assertEquals(SubscriptionUsageTier.CRITICAL, ServersHierarchy.usageTier(905L, 1_000L))
        // The boundaries themselves, stated once, in percent.
        assertEquals(SubscriptionUsageTier.CALM, ServersHierarchy.usageTierOf(70))
        assertEquals(SubscriptionUsageTier.WATCH, ServersHierarchy.usageTierOf(71))
        assertEquals(SubscriptionUsageTier.WATCH, ServersHierarchy.usageTierOf(90))
        assertEquals(SubscriptionUsageTier.CRITICAL, ServersHierarchy.usageTierOf(91))
        assertEquals(70, ServersHierarchy.CALM_CEILING_PERCENT)
        assertEquals(90, ServersHierarchy.WATCH_CEILING_PERCENT)
    }

    @Test
    fun anUnmeteredPlanHasNoTierAndNoFill() {
        assertFalse(ServersHierarchy.hasQuota(0L))
        assertTrue(ServersHierarchy.hasQuota(1L))
        assertEquals(SubscriptionUsageTier.UNKNOWN, ServersHierarchy.usageTier(500L, 0L))
        assertEquals(0f, ServersHierarchy.usageFraction(500L, 0L), 0f)
        assertTrue(
            "an unmetered plan says so with the infinity the provider implied",
            ServersHierarchy.usageText(1_000L, 0L).endsWith("\u221E")
        )
    }

    @Test
    fun theBarFillIsClampedToTheTrack() {
        assertEquals(.5f, ServersHierarchy.usageFraction(50L, 100L), 0.0001f)
        assertEquals(1f, ServersHierarchy.usageFraction(150L, 100L), 0f)
        assertEquals(0f, ServersHierarchy.usageFraction(-5L, 100L), 0f)
        assertEquals(0f, ServersHierarchy.usageFraction(0L, 100L), 0f)
    }

    @Test
    fun byteCountsKeepTheUnitsTheProviderReported() {
        assertEquals("0 B", ServersHierarchy.compactBytes(0L))
        assertEquals("0 B", ServersHierarchy.compactBytes(-5L))
        assertEquals("512 B", ServersHierarchy.compactBytes(512L))
        assertEquals("4 KB", ServersHierarchy.compactBytes(4_000L))
        assertEquals("2 MB", ServersHierarchy.compactBytes(2_000_000L))
        assertEquals("1.5 GB", ServersHierarchy.compactBytes(1_500_000_000L))
        assertEquals(
            "1.5 GB / 50.0 GB",
            ServersHierarchy.usageText(1_500_000_000L, 50_000_000_000L)
        )
        assertEquals("42%", ServersHierarchy.percentLabel(42))
    }

    // ------------------------------------------------------------------ count badges

    @Test
    fun everyBadgeNamesTheThingItCounts() {
        assertEquals("1 group", ServersHierarchy.groupBadge(1))
        assertEquals("2 groups", ServersHierarchy.groupBadge(2))
        assertEquals("1 server", ServersHierarchy.serverBadge(1))
        assertEquals("48 servers", ServersHierarchy.serverBadge(48))
        assertEquals("0 servers", ServersHierarchy.serverBadge(0))
        assertEquals("1 filter", ServersHierarchy.filterBadge(1))
        assertEquals("3 filters", ServersHierarchy.filterBadge(3))
        // A negative count is a bug upstream; the badge must still read as a count.
        assertEquals("0 groups", ServersHierarchy.groupBadge(-4))
    }

    @Test
    fun theFilterBadgeCountsOnlyTheSwitchesThatAreOn() {
        assertEquals(
            0,
            ServersHierarchy.activeFilterCount(
                groupByCountry = false,
                onlyReachable = false,
                maxPingMs = 0
            )
        )
        assertEquals(
            1,
            ServersHierarchy.activeFilterCount(
                groupByCountry = true,
                onlyReachable = false,
                maxPingMs = 0
            )
        )
        assertEquals(
            2,
            ServersHierarchy.activeFilterCount(
                groupByCountry = false,
                onlyReachable = true,
                maxPingMs = 250
            )
        )
        assertEquals(
            3,
            ServersHierarchy.activeFilterCount(
                groupByCountry = true,
                onlyReachable = true,
                maxPingMs = 100
            )
        )
    }
}
