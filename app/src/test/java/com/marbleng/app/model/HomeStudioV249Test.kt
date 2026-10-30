package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_HOME_STUDIO_V249 — Home as a layout the user owns, on the host JVM.
 *
 * The request was «هر قسمتی رو حذف یا غیرفعال کنه»: any section can be turned off. Two of the six
 * could already be hidden, and nothing could be moved. These tests pin the three things that make the
 * difference between a list of switches and a layout: the order string always resolves to *every*
 * section exactly once, the two legacy booleans are honoured as a floor instead of being replaced,
 * and an empty Home is a legal answer that the page has to name out loud.
 */
class HomeStudioV249Test {

    private val all = HomeSection.entries

    @Test
    fun `the shipped layout is every section once and nothing hidden`() {
        assertEquals(all, HomeStudio.order(HomeStudio.DEFAULT_ORDER))
        assertEquals("", HomeStudio.DEFAULT_HIDDEN)
        assertEquals(all, HomeStudio.visible(HomeStudio.DEFAULT_ORDER, HomeStudio.DEFAULT_HIDDEN))
        assertFalse(HomeStudio.isBare(HomeStudio.DEFAULT_ORDER, HomeStudio.DEFAULT_HIDDEN))
        assertEquals(0, HomeStudio.hiddenCount(HomeStudio.DEFAULT_HIDDEN))
        assertEquals(
            all.joinToString(",") { it.id },
            HomeStudio.canonicalOrder(HomeStudio.DEFAULT_ORDER)
        )
    }

    @Test
    fun `a blank or unknown order still resolves to a complete layout`() {
        assertEquals(all, HomeStudio.order(""))
        assertEquals(all, HomeStudio.order("bogus,,nope"))
        // An id this build does not know is dropped; the sections it did not name are appended in the
        // canonical order, so a section added by a future build appears instead of being lost.
        assertEquals(
            listOf(
                HomeSection.ROUTE,
                HomeSection.HEADER,
                HomeSection.CONNECT,
                HomeSection.PRESETS,
                HomeSection.STATS,
                HomeSection.SHORTCUTS
            ),
            HomeStudio.order("route,bogus,header")
        )
        // A duplicate is drawn once.
        assertEquals(all, HomeStudio.order("header,header,header"))

        // Canonicalizing is idempotent, complete and free of unknown ids — the property the store
        // relies on when it writes the string back.
        val canonical = HomeStudio.canonicalOrder("route,bogus,header,header")
        assertEquals(canonical, HomeStudio.canonicalOrder(canonical))
        assertEquals(all.size, canonical.split(",").size)
        assertEquals(all.toSet(), HomeStudio.order(canonical).toSet())
        assertFalse("bogus" in canonical)
    }

    @Test
    fun `a section switched off stays off and comes back on`() {
        val hidden = HomeStudio.toggleHidden("", HomeSection.PRESETS)
        assertEquals("presets", hidden)
        assertTrue(HomeStudio.isHidden(HomeSection.PRESETS, hidden))
        assertEquals(all - HomeSection.PRESETS, HomeStudio.visible(HomeStudio.DEFAULT_ORDER, hidden))
        assertEquals(1, HomeStudio.hiddenCount(hidden))

        val shown = HomeStudio.toggleHidden(hidden, HomeSection.PRESETS)
        assertEquals("", shown)
        assertFalse(HomeStudio.isHidden(HomeSection.PRESETS, shown))
        assertEquals(all, HomeStudio.visible(HomeStudio.DEFAULT_ORDER, shown))
    }

    @Test
    fun `the two legacy switches are a floor, not a record the studio replaces`() {
        // Somebody who turned the speed card off two versions ago has an empty hidden string and a
        // false boolean; the section must still be off, because the boolean is what they wrote.
        assertTrue(
            HomeStudio.isHidden(
                section = HomeSection.STATS,
                hiddenRaw = "",
                legacyStatsVisible = false
            )
        )
        assertEquals(
            all - HomeSection.STATS,
            HomeStudio.visible(HomeStudio.DEFAULT_ORDER, "", legacyStatsVisible = false)
        )
        assertTrue(
            HomeStudio.isHidden(
                section = HomeSection.SHORTCUTS,
                hiddenRaw = "",
                legacyShortcutsVisible = false
            )
        )
        // …and the studio's own switch turns it back *on*: the hidden set stays empty and the page
        // writes the boolean in the same update, so the two records cannot drift apart. A section
        // hidden only by the boolean is hidden with no id to remove, and removing nothing is right.
        assertEquals(
            "",
            HomeStudio.toggleHidden("", HomeSection.STATS, legacyStatsVisible = false)
        )
        // The other direction needs no boolean at all: the id is the studio's own record.
        assertEquals("stats", HomeStudio.toggleHidden("", HomeSection.STATS))
        // A section the legacy switches never owned ignores them entirely.
        assertFalse(
            HomeStudio.isHidden(
                section = HomeSection.CONNECT,
                hiddenRaw = "",
                legacyStatsVisible = false,
                legacyShortcutsVisible = false
            )
        )
    }

    @Test
    fun `unknown ids never reach the hidden set`() {
        // A real id survives the noise around it; the noise itself never becomes a section.
        assertEquals(setOf("connect"), HomeStudio.hiddenIds("bogus,,connect,bogus"))
        assertEquals(emptySet<String>(), HomeStudio.hiddenIds("bogus,,nope,bogus"))
        assertEquals("connect", HomeStudio.canonicalHidden("bogus,connect,connect"))
        assertEquals("", HomeStudio.canonicalHidden("nope"))
        assertEquals("", HomeStudio.canonicalHidden(""))
    }

    @Test
    fun `a section moves only as far as the layout allows`() {
        assertEquals(
            "header,connect,presets,stats,route,shortcuts",
            HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.STATS, -1)
        )
        assertEquals(
            "header,connect,presets,route,shortcuts,stats",
            HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.STATS, 1)
        )
        assertEquals(
            "shortcuts,header,connect,presets,route,stats",
            HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.SHORTCUTS, -5)
        )
        assertTrue(HomeStudio.canMove(HomeStudio.DEFAULT_ORDER, HomeSection.STATS, -1))

        // The ends of the list have nowhere to go, and "nowhere" is not a move: the button is
        // disabled rather than pretending to do something.
        assertNull(HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.HEADER, -1))
        assertFalse(HomeStudio.canMove(HomeStudio.DEFAULT_ORDER, HomeSection.HEADER, -1))
        assertNull(HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.SHORTCUTS, 1))
        assertFalse(HomeStudio.canMove(HomeStudio.DEFAULT_ORDER, HomeSection.SHORTCUTS, 1))
        // STATS is one place from the end, so two places down is off the end and is refused rather
        // than wrapped around to the top.
        assertNull(HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.STATS, 2))
        assertNull(HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.HEADER, 9))
        // No distance, no rewrite: an identical string written back is a persisted change to nothing.
        assertNull(HomeStudio.move(HomeStudio.DEFAULT_ORDER, HomeSection.STATS, 0))
    }

    @Test
    fun `an empty Home is allowed and the model says so`() {
        val everythingOff = all.joinToString(",") { it.id }
        assertTrue(HomeStudio.isBare(HomeStudio.DEFAULT_ORDER, everythingOff))
        assertEquals(emptyList<HomeSection>(), HomeStudio.visible(HomeStudio.DEFAULT_ORDER, everythingOff))
        assertEquals(all.size, HomeStudio.hiddenCount(everythingOff))
        // The order survives a Home with nothing in it: turning every section back on restores the
        // layout the user had, not the shipped one.
        assertEquals(all, HomeStudio.order(everythingOff))
    }

    @Test
    fun `resetting gives back the shipped layout`() {
        assertEquals(HomeStudio.DEFAULT_ORDER, HomeStudio.resetOrder())
        assertEquals("", HomeStudio.resetHidden())
        assertEquals(all, HomeStudio.visible(HomeStudio.resetOrder(), HomeStudio.resetHidden()))
    }

    @Test
    fun `every section has a stable id, a dictionary title and a one line hint`() {
        assertEquals(all.size, all.map { it.id }.distinct().size)
        all.forEach { section ->
            assertTrue(section.id.isNotBlank())
            assertEquals(section, HomeSection.fromId(section.id))
            assertEquals(section, HomeSection.fromId("  ${section.id.uppercase()}  "))
            // The title is a key in the Persian dictionary, spelled as the dictionary spells it: a
            // key that differs by one capital is not a missed translation, it is a transliteration.
            assertTrue(section.title.isNotBlank())
            assertTrue(section.hint.isNotBlank())
            assertFalse(section.title.contains("\n"))
            assertFalse(section.hint.contains("\n"))
        }
        assertNull(HomeSection.fromId("bogus"))
        assertNull(HomeSection.fromId(null))
        assertNull(HomeSection.fromId(""))
    }
}
