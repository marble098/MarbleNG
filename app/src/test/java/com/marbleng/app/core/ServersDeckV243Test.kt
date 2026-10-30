package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVERS_DECK_V243 — the Servers page arithmetic, pinned without a device.
 *
 * Each case here is one of the complaints the redesign answered: a ping that was too quiet to
 * rank, a row that repeated the address, a filter strip that appeared while nothing was filtered,
 * a group header that stood alone on a single-group library, and a delete that was a fact before
 * the user could undo it.
 */
class ServersDeckV243Test {

    private fun profile(
        id: String = "p1",
        scheme: String = "vless",
        host: String = "77.37.65.197",
        security: String = "tls",
        transport: String = "xhttp",
        subscriptionId: String = "sub-1"
    ) = ProxyProfile(
        id = id,
        name = "Germany 4",
        scheme = scheme,
        raw = "vless://example",
        configJson = "{}",
        host = host,
        port = 443,
        transport = transport,
        security = security,
        subscriptionId = subscriptionId
    )

    @Test
    fun aPingIsRankedInFourLevelsWithTheAgreedLines() {
        assertEquals(PingReadout.FAST, pingReadout(42.0))
        assertEquals(PingReadout.FAST, pingReadout(149.9))
        assertEquals(PingReadout.OK, pingReadout(150.0))
        assertEquals(PingReadout.OK, pingReadout(299.9))
        assertEquals(PingReadout.SLOW, pingReadout(300.0))
        assertEquals(PingReadout.SLOW, pingReadout(9_000.0))
        assertEquals(PingReadout.TIMEOUT, pingReadout(12.0, success = false))
        assertEquals(PingReadout.UNKNOWN, pingReadout(null))
        assertEquals(PingReadout.UNKNOWN, pingReadout(0.0))
        assertEquals(PingReadout.UNKNOWN, pingReadout(Double.NaN))
    }

    @Test
    fun theBarsLightFromTheBottomAndATimeoutLightsNone() {
        assertEquals(4, pingBars(PingReadout.FAST))
        assertEquals(3, pingBars(PingReadout.OK))
        assertEquals(2, pingBars(PingReadout.SLOW))
        assertEquals(0, pingBars(PingReadout.TIMEOUT))
        assertEquals(0, pingBars(PingReadout.UNKNOWN))
    }

    @Test
    fun theSubtitleIsSecurityAndTransportAndNeverTheAddress() {
        assertEquals("TLS · XHTTP", transportLine(profile()))
        assertEquals("REALITY", transportLine(profile(security = "reality", transport = "tcp")))
        assertEquals("WS", transportLine(profile(security = "none", transport = "ws")))
        assertEquals("", transportLine(profile(security = "", transport = "")))
        assertFalse("the address leaked into the subtitle", transportLine(profile()).contains("77.37"))
    }

    @Test
    fun theProtocolChipIsShortEnoughToBeReadAtAGlance() {
        assertEquals("VL", protocolChip(profile()))
        assertEquals("VM", protocolChip(profile(scheme = "vmess")))
        assertEquals("TR", protocolChip(profile(scheme = "trojan")))
        assertEquals("SS", protocolChip(profile(scheme = "shadowsocks")))
        assertEquals("H2", protocolChip(profile(scheme = "hysteria2")))
    }

    @Test
    fun theChipStripOnlyAppearsWhenSomethingIsActuallyFiltering() {
        assertFalse(ServersDeck.showsFilterStrip(activeFilterChips(ServersFilter()).size))

        val narrowing = ServersFilter(protocol = "vless", sourceId = "sub-1", onlyReachable = true, maxPingMs = 200)
        val chips = activeFilterChips(narrowing, sourceName = "Germany", reversed = true)
        assertEquals(listOf("protocol", "source", "reachable", "maxPing", "reverse"), chips.map { it.key })
        assertEquals("Germany", chips.first { it.key == "source" }.label)
        assertTrue(ServersDeck.showsFilterStrip(chips.size))
    }

    @Test
    fun oneGroupDrawsNoHeaderTwoGroupsDo() {
        assertFalse(ServersDeck.showsGroupHeaders(1))
        assertTrue(ServersDeck.showsGroupHeaders(2))
        assertTrue(ServersDeck.showsGroupHeaders(5))
    }

    @Test
    fun theSelectionRosterIsKeyedByIdNotByPosition() {
        var roster = emptySet<String>()
        roster = ServersSelection.toggle(roster, "a")
        roster = ServersSelection.toggle(roster, "b")
        assertEquals(setOf("a", "b"), roster)
        roster = ServersSelection.toggle(roster, "a")
        assertEquals(setOf("b"), roster)

        roster = ServersSelection.toggleAll(roster, listOf("b", "c"))
        assertEquals(setOf("b", "c"), roster)
        assertTrue(ServersSelection.allSelected(roster, listOf("c", "b")))
        roster = ServersSelection.toggleAll(roster, listOf("b", "c"))
        assertEquals(emptySet<String>(), roster)
    }

    @Test
    fun aDeletedRowLeavesThePageButNotTheLibraryUntilTheWindowEnds() {
        val entry = ServersUndo.schedule(profile(id = "gone"), nowMs = 1_000L)
        assertEquals("gone", entry.profileId)
        assertEquals("sub-1", entry.subscriptionId)
        assertEquals(1_000L + ServersDeck.UNDO_WINDOW_MS, entry.deadlineMs)
        assertEquals(setOf("gone"), ServersUndo.hiddenIds(entry))

        assertEquals(ServersDeck.UNDO_WINDOW_MS - 1, ServersUndo.remainingMs(entry, 1_001L))
        assertFalse(ServersUndo.shouldCommit(entry, 1_000L + ServersDeck.UNDO_WINDOW_MS - 1))
        assertTrue(ServersUndo.shouldCommit(entry, 1_000L + ServersDeck.UNDO_WINDOW_MS))
        assertEquals(emptySet<String>(), ServersUndo.hiddenIds(null))
    }

    @Test
    fun theRowCeilingThePagePromisedIsTheOneTheTestMeasures() {
        assertEquals(68, ServersDeck.ROW_MIN_HEIGHT_DP)
        assertEquals(88, ServersDeck.ROW_MAX_HEIGHT_DP)
        assertEquals(88, ServersDeck.PING_COLUMN_WIDTH_DP)
        assertEquals(48, ServersDeck.ACTION_TARGET_DP)
        assertTrue(ServersDeck.FAB_CLEARANCE_DP >= ServersDeck.ROW_MIN_HEIGHT_DP)
    }
}
