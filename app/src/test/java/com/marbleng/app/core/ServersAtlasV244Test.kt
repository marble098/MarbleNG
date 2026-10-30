package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVERS_ATLAS_V244 — the Servers page's new arithmetic, pinned without a device.
 *
 * Each case answers one of the twelve-seat council's complaints: a rail that cannot say which
 * library it is on, a flag cluster that repeats one country, a plan bar that draws past 100 %,
 * a row that spends its width before the name starts, and a subtitle that hides where the
 * server actually is.
 */
class ServersAtlasV244Test {

    private fun profile(
        id: String,
        name: String = "Germany 4",
        host: String = "77.37.65.197",
        scheme: String = "vless",
        security: String = "tls",
        transport: String = "xhttp",
        subscriptionId: String = "sub-1"
    ) = ProxyProfile(
        id = id,
        name = name,
        scheme = scheme,
        raw = "vless://example",
        configJson = "{}",
        host = host,
        port = 443,
        transport = transport,
        security = security,
        subscriptionId = subscriptionId
    )

    private fun subscription(
        id: String,
        name: String = "Plan",
        upload: Long = 0L,
        download: Long = 0L,
        total: Long = 0L
    ) = Subscription(
        id = id,
        name = name,
        url = "https://example.test/sub",
        uploadBytes = upload,
        downloadBytes = download,
        totalBytes = total
    )

    @Test
    fun theRailIsAllThenEverySubscriptionThenTheManualBucket() {
        val subs = listOf(subscription("a", "Alpha"), subscription("b", "Beta"))
        val profiles = listOf(
            profile("1", subscriptionId = "a"),
            profile("2", subscriptionId = "a"),
            profile("3", subscriptionId = "b"),
            profile("4", subscriptionId = "manual")
        )

        val cards = libraryCards(
            subscriptions = subs,
            profiles = profiles,
            countryCodeOf = { "DE" },
            allTitle = "All sources",
            manualTitle = "Manual"
        )

        assertEquals(listOf("all", "a", "b", "manual"), cards.map { it.id })
        assertEquals(listOf(4, 2, 1, 1), cards.map { it.count })
        assertEquals("All sources", cards.first().title)
        assertTrue(cards.first().isAll)
        assertTrue(cards.last().isManual)
        assertEquals("a", cards[1].subscription?.id)
        assertTrue("the manual bucket must not carry a subscription to edit", cards.last().subscription == null)
    }

    @Test
    fun anEmptySourceStillGetsItsCard() {
        val cards = libraryCards(
            subscriptions = listOf(subscription("empty", "Empty plan")),
            profiles = emptyList(),
            countryCodeOf = { "" },
            allTitle = "All sources",
            manualTitle = "Manual"
        )

        assertEquals(listOf("all", "empty"), cards.map { it.id })
        assertEquals(0, cards[1].count)
        assertTrue(cards[1].isEmpty)
        assertEquals(emptyList<String>(), cards[1].flags)
    }

    @Test
    fun aFlagClusterIsUniqueFrequencyOrderedAndCapped() {
        val cluster = ServersAtlas.flagCluster(
            listOf("DE", "NL", "DE", "DE", "FR", "NL", "US", "GB", "de", "", "ZZZ", "1D")
        )

        assertEquals(listOf("DE", "NL", "FR", "GB"), cluster)
        assertEquals(2, ServersAtlas.flagCluster(listOf("NL", "DE", "DE"), limit = 2).size)
        assertEquals(listOf("DE"), ServersAtlas.flagCluster(listOf("DE", "DE", "DE"), limit = 2))
        assertEquals(emptyList<String>(), ServersAtlas.flagCluster(listOf("", "X", "12")))
        assertEquals(emptyList<String>(), ServersAtlas.flagCluster(listOf("DE"), limit = 0))
    }

    @Test
    fun aPlanBarIsClampedAndSilentWithoutACeiling() {
        assertEquals(0f, ServersAtlas.usageFraction(usedBytes = 0L, totalBytes = 0L), 0f)
        assertEquals(0f, ServersAtlas.usageFraction(usedBytes = 10L, totalBytes = 0L), 0f)
        assertEquals(0.5f, ServersAtlas.usageFraction(usedBytes = 50L, totalBytes = 100L), 0.0001f)
        assertEquals(1f, ServersAtlas.usageFraction(usedBytes = 400L, totalBytes = 100L), 0.0001f)
        assertEquals(0f, ServersAtlas.usageFraction(usedBytes = -5L, totalBytes = 100L), 0.0001f)
    }

    @Test
    fun aRailCardCarriesThePlanItsSubscriptionPaidFor() {
        val cards = libraryCards(
            subscriptions = listOf(subscription("a", upload = 10L, download = 40L, total = 100L)),
            profiles = listOf(profile("1", subscriptionId = "a")),
            countryCodeOf = { "NL" },
            allTitle = "All sources",
            manualTitle = "Manual"
        )

        assertEquals(0.5f, cards[1].usageFraction, 0.0001f)
        assertEquals(listOf("NL"), cards[1].flags)
    }

    @Test
    fun theRowSaysWhereTheServerIsAndHowItCarriesTraffic() {
        val de = profile("1")
        assertEquals("Germany · TLS · XHTTP", ServersAtlas.rowSubtitle(de, countryName = "Germany"))
        assertEquals(
            "a blank country must fall back to the protocol, not to an empty prefix",
            "VLESS · TLS · XHTTP",
            ServersAtlas.rowSubtitle(de, countryName = "  ")
        )
        assertEquals(
            "REALITY",
            ServersAtlas.rowSubtitle(
                profile("2", security = "reality", transport = "tcp"),
                countryName = ""
            ).replace("VLESS · ", "")
        )
        assertFalse(
            "the address leaked onto the row",
            ServersAtlas.rowSubtitle(de, countryName = "Germany").contains("77.37")
        )
        assertTrue(
            "the country is missing from the subtitle",
            ServersAtlas.rowSubtitle(de, countryName = "Germany").startsWith("Germany")
        )
        assertEquals(
            "with no measured country the protocol has to lead",
            "VLESS · TLS · XHTTP",
            ServersAtlas.rowSubtitle(de, countryName = "")
        )
    }

    @Test
    fun theRowBudgetLeavesRoomForTheNameOnANarrowPhone() {
        val reserved = ServersAtlas.ROW_FIXED_WIDTH_DP
        assertTrue("the row reserves too much width: $reserved", reserved <= 220)
        assertTrue(
            "the name column collapses below the promised width",
            ServersAtlas.NARROW_PAGE_WIDTH_DP - reserved >= ServersAtlas.ROW_TEXT_MIN_WIDTH_DP
        )
        // The rail, the flag and the ping keep the proportions the chapter promised.
        assertTrue(ServersAtlas.LIBRARY_CARD_WIDTH_DP > ServersAtlas.ROW_FLAG_WIDTH_DP * 2)
        assertTrue(ServersAtlas.LIBRARY_CARD_HEIGHT_DP > ServersAtlas.LIBRARY_FLAG_SIZE_DP * 3)
        assertEquals(9, ServersAtlas.ROW_FLAG_CORNER_DP)
        assertTrue(ServersAtlas.CLUSTER_LIMIT in 3..4)
        assertEquals(ServersAtlas.ROW_FIXED_WIDTH_DP, ServersDeck.PING_COLUMN_WIDTH_DP + 48 + 38 + 24 + 14)
    }

    @Test
    fun theLocationNoticeOnlyAppearsWhenAFlagIsActuallyMissing() {
        assertFalse(ServersAtlas.showsLocationNotice(0))
        assertTrue(ServersAtlas.showsLocationNotice(1))
        assertTrue(ServersAtlas.showsLocationNotice(12))
        assertEquals(1, ServersAtlas.LOCATION_NOTICE_MIN)
    }
}
