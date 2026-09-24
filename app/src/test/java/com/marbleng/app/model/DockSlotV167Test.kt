package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * MARBLE_DOCK_SLOT_V167 — the fourth slot of the bottom bar.
 *
 * The product shipped three tabs for its whole life, so the fourth one has to be two things at
 * once: a real page the user fills in, and a preference that can be switched off without leaving
 * the app holding a pager page that no longer exists. Everything that decides that — the kind, the
 * glyph, the caption and the mapping from a remembered tab name to a page — is a pure function, and
 * this test pins all four.
 */
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
    fun aFreshInstallOpensOnThePulse() {
        // The only kind that is meaningful with an empty library, so the slot is never blank.
        assertEquals(DockSlotKind.PULSE, DockSlotKind.DEFAULT)
        listOf("", "   ", "nebula", "PULSE-TRACE").forEach { raw ->
            assertEquals("unknown kind '$raw' must fall back", DockSlotKind.PULSE, parseDockSlotKind(raw))
        }
    }

    @Test
    fun theRetiredVocabularyStillNamesItsSlot() {
        // Earlier drafts of this feature persisted SUBSCRIPTION/NODE; a value already written to a
        // device has to keep opening the slot it named.
        listOf("sub", "subscription", "group", "subs").forEach { legacy ->
            assertEquals(legacy, DockSlotKind.SOURCE, parseDockSlotKind(legacy))
        }
        listOf("node", "profile", "server", "configs").forEach { legacy ->
            assertEquals(legacy, DockSlotKind.CONFIG, parseDockSlotKind(legacy))
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
        val shown = dockSlots(all, SpatialTabLike.CUSTOM, showOptional = true)
        assertEquals(all, shown)

        val hidden = dockSlots(all, SpatialTabLike.CUSTOM, showOptional = false)
        assertEquals(3, hidden.size)
        assertFalse(SpatialTabLike.CUSTOM in hidden)
        // The three tabs the product shipped with keep their order, so nothing moves when the
        // fourth slot disappears.
        assertEquals(listOf(SpatialTabLike.DECK, SpatialTabLike.LIBRARY, SpatialTabLike.SETTINGS), hidden)
    }

    @Test
    fun aRememberedFourthTabFallsBackIntoTheBarThatExists() {
        val hidden = dockSlots(SpatialTabLike.entries, SpatialTabLike.CUSTOM, showOptional = false)
        // An install that had the fourth slot selected and then turned it off comes back asking for
        // page 4 of a three-page pager: this is the repair that never asks for it.
        assertEquals(0, dockSlotIndex(hidden, SpatialTabLike.CUSTOM))
        assertEquals(0, dockSlotIndex(hidden, SpatialTabLike.DECK))
        assertEquals(SpatialTabLike.SETTINGS, hidden[dockSlotIndex(hidden, SpatialTabLike.SETTINGS)])

        val shown = dockSlots(SpatialTabLike.entries, SpatialTabLike.CUSTOM, showOptional = true)
        assertEquals(SpatialTabLike.CUSTOM, shown[dockSlotIndex(shown, SpatialTabLike.CUSTOM)])
    }

    @Test
    fun theShownBarKeepsTheFourthSlotExactlyWhereItWas() {
        val shown = dockSlots(SpatialTabLike.entries, SpatialTabLike.CUSTOM, showOptional = true)
        assertEquals(4, shown.size)
        assertEquals(3, dockSlotIndex(shown, SpatialTabLike.CUSTOM))
        assertEquals(SpatialTabLike.CUSTOM, shown.last())
    }

    @Test
    fun withNoSlotsAtAllThePageIsTheFirstOne() {
        // Never a negative index: a pager asked for page -1 is a crash, and the bar is built from
        // this list before anything else is measured.
        assertEquals(0, dockSlotIndex(emptyList<SpatialTabLike>(), SpatialTabLike.DECK))
    }

    @Test
    fun theCaptionIsTheUsersOwnWordsBoundedToTheBar() {
        assertEquals("Pulse", dockSlotCaption("  Pulse  "))
        assertEquals("", dockSlotCaption("   "))
        assertEquals(DOCK_SLOT_CAPTION_MAX, dockSlotCaption("x".repeat(40)).length)
        assertEquals("x".repeat(DOCK_SLOT_CAPTION_MAX), dockSlotCaption("x".repeat(40)))
    }

    @Test
    fun theDefaultCaptionNamesWhatTheSlotActuallyOpens() {
        assertEquals(DOCK_SLOT_CAPTION_PULSE, dockSlotDefaultCaption(DockSlotKind.PULSE, ""))
        // A pulse is never named after a source it does not open.
        assertEquals(DOCK_SLOT_CAPTION_PULSE, dockSlotDefaultCaption(DockSlotKind.PULSE, "My Sub"))
        assertEquals("My Sub", dockSlotDefaultCaption(DockSlotKind.SOURCE, "My Sub"))
        assertEquals("Tokyo", dockSlotDefaultCaption(DockSlotKind.CONFIG, "Tokyo"))
        assertEquals(DOCK_SLOT_CAPTION_FALLBACK, dockSlotDefaultCaption(DockSlotKind.SOURCE, ""))
        assertEquals(DOCK_SLOT_CAPTION_FALLBACK, dockSlotDefaultCaption(DockSlotKind.CONFIG, "   "))
        assertEquals(
            DOCK_SLOT_CAPTION_MAX,
            dockSlotDefaultCaption(DockSlotKind.SOURCE, "a very long subscription name").length
        )
    }

    /** A local stand-in for the dock's own enum, which is private to the UI layer. */
    private enum class SpatialTabLike { DECK, LIBRARY, SETTINGS, CUSTOM }
}
