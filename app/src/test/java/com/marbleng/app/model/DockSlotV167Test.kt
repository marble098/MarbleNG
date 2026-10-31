package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DockSlotV167Test {
    @Test
    fun everySlotKindRoundTripsThroughItsStoredId() {
        DockSlotKind.entries.forEach { kind ->
            assertEquals(kind, parseDockSlotKind(kind.id))
            assertEquals(kind, parseDockSlotKind(kind.id.uppercase()))
            assertEquals(kind, parseDockSlotKind(" ${kind.id} "))
        }
    }

    @Test
    fun aFreshInstallOpensOnTheIpPage() {
        assertEquals(DockSlotKind.IP, DockSlotKind.DEFAULT)
        listOf("", "   ", "nebula", "IP-TRACE").forEach { raw ->
            assertEquals("unknown kind '$raw' must fall back", DockSlotKind.IP, parseDockSlotKind(raw))
        }
    }

    /**
     * MARBLE_IP_IDENTITY_V251 — the three retired shortcut ids all name the tab that replaced them.
     * A phone that had `config` stored must open a working tab on the first launch after the
     * update, not an empty page and not a crash.
     */
    @Test
    fun theRetiredVocabularyStillNamesItsTab() {
        listOf(
            "pulse", "sub", "subscription", "group", "subs",
            "node", "profile", "server", "config", "configs"
        ).forEach { legacy ->
            assertEquals(legacy, DockSlotKind.IP, parseDockSlotKind(legacy))
        }
    }

    @Test
    fun everySlotIconRoundTripsThroughItsStoredId() {
        DockSlotIcon.entries.forEach { icon ->
            assertEquals(icon, parseDockSlotIcon(icon.id))
            assertEquals(icon, parseDockSlotIcon(icon.id.uppercase()))
        }
        assertEquals(DockSlotIcon.PULSE, DockSlotIcon.DEFAULT)
        assertEquals(DockSlotIcon.PULSE, parseDockSlotIcon("nonsense"))
    }

    @Test
    fun fourthTabAccentsRoundTripAndLegacyColorNamesStayCompatible() {
        DockSlotAccent.entries.forEach { accent ->
            assertEquals(accent, parseDockSlotAccent(accent.id))
            assertEquals(accent, parseDockSlotAccent(accent.id.uppercase()))
        }
        assertEquals(DockSlotAccent.OCEAN, DockSlotAccent.DEFAULT)
        assertEquals(DockSlotAccent.OCEAN, parseDockSlotAccent("cyan"))
        assertEquals(DockSlotAccent.MINT, parseDockSlotAccent("emerald"))
        assertEquals(DockSlotAccent.VIOLET, parseDockSlotAccent("amethyst"))
        assertEquals(DockSlotAccent.AMBER, parseDockSlotAccent("gold"))
        assertEquals(DockSlotAccent.DEFAULT, parseDockSlotAccent("not-a-color"))
    }

    @Test
    fun theRetiredIconVocabularyStillNamesItsGlyph() {
        listOf("bolt", "star", "activity").forEach { legacy ->
            assertEquals(legacy, DockSlotIcon.SPARK, parseDockSlotIcon(legacy))
        }
        listOf("stack", "library", "book").forEach { legacy ->
            assertEquals(legacy, DockSlotIcon.LAYERS, parseDockSlotIcon(legacy))
        }
        listOf("compass", "route", "globe").forEach { legacy ->
            assertEquals(legacy, DockSlotIcon.BEARING, parseDockSlotIcon(legacy))
        }
    }

    @Test
    fun theBarIsThreeTabsWhenTheFourthIsSwitchedOff() {
        val all = SpatialTabLike.entries
        val shown = dockSlots(all, SpatialTabLike.IP, showOptional = true)
        assertEquals(all, shown)

        val hidden = dockSlots(all, SpatialTabLike.IP, showOptional = false)
        assertEquals(3, hidden.size)
        assertFalse(SpatialTabLike.IP in hidden)

        assertEquals(listOf(SpatialTabLike.DECK, SpatialTabLike.LIBRARY, SpatialTabLike.SETTINGS), hidden)
    }

    @Test
    fun aRememberedFourthTabFallsBackIntoTheBarThatExists() {
        val hidden = dockSlots(SpatialTabLike.entries, SpatialTabLike.IP, showOptional = false)

        assertEquals(0, dockSlotIndex(hidden, SpatialTabLike.IP))
        assertEquals(0, dockSlotIndex(hidden, SpatialTabLike.DECK))
        assertEquals(SpatialTabLike.SETTINGS, hidden[dockSlotIndex(hidden, SpatialTabLike.SETTINGS)])

        val shown = dockSlots(SpatialTabLike.entries, SpatialTabLike.IP, showOptional = true)
        assertEquals(SpatialTabLike.IP, shown[dockSlotIndex(shown, SpatialTabLike.IP)])
    }

    @Test
    fun theShownBarKeepsTheFourthSlotExactlyWhereItWas() {
        val shown = dockSlots(SpatialTabLike.entries, SpatialTabLike.IP, showOptional = true)
        assertEquals(4, shown.size)
        assertEquals(3, dockSlotIndex(shown, SpatialTabLike.IP))
        assertEquals(SpatialTabLike.IP, shown.last())
    }

    @Test
    fun withNoSlotsAtAllThePageIsTheFirstOne() {
        assertEquals(0, dockSlotIndex(emptyList<SpatialTabLike>(), SpatialTabLike.DECK))
    }

    @Test
    fun theCaptionIsBoundedToTheBar() {
        assertEquals("Pulse", dockSlotCaption("  Pulse  "))
        assertEquals("", dockSlotCaption("   "))
        assertEquals(DOCK_SLOT_CAPTION_MAX, dockSlotCaption("x".repeat(40)).length)
        assertEquals("x".repeat(DOCK_SLOT_CAPTION_MAX), dockSlotCaption("x".repeat(40)))
    }

    /**
     * MARBLE_IP_IDENTITY_V251 — the tab has one name in every case. It does not matter what the
     * caller passes as the target: the fourth tab is the IP page, so its caption is the IP page's
     * name, and a stored custom caption from the shortcut era cannot rename it back.
     */
    @Test
    fun theDefaultCaptionNamesWhatTheTabOpensInEveryCase() {
        assertEquals(DOCK_SLOT_CAPTION_IP, dockSlotDefaultCaption(DockSlotKind.IP, ""))
        assertEquals("IP", DOCK_SLOT_CAPTION_IP)
        listOf("My Sub", "Tokyo", "   ", "a very long subscription name").forEach { target ->
            assertEquals(DOCK_SLOT_CAPTION_IP, dockSlotDefaultCaption(DockSlotKind.IP, target))
        }
    }

    private enum class SpatialTabLike { DECK, LIBRARY, SETTINGS, IP }
}
