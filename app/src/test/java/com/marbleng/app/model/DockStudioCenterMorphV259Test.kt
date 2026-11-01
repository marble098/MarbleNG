package com.marbleng.app.model

import com.marbleng.app.ui.faTranslate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CENTER_MORPH_STUDIO_V259 / MARBLE_HOME_QUIET_V259 — the two design decisions of this pass,
 * pinned on the host JVM.
 *
 * Both complaints were about *relationships* rather than standalone numbers, so both halves of this
 * file assert comparisons:
 *
 *  - the centre trigger is **bigger** than the tab beside it and **fits** the clearance every page
 *    reserves — a trigger that outgrew the clearance would be the V256 clipping bug with a new
 *    number, and a trigger that matched the tab height would be the fifth tab the brief rejected;
 *  - a first run is **quieter** than a full Home, every section can be switched back on, and the two
 *    legacy floors cannot make a switch look broken.
 */
class DockStudioCenterMorphV259Test {

    // ── the centre trigger ──────────────────────────────────────────────────────────────────

    @Test
    fun theCentreDockIsTallerAndItsTriggerOutgrowsEveryTab() {
        DockSize.entries.forEach { size ->
            val plain = DockStudio.heightDp(size)
            val centre = DockStudio.centerDockHeightDp(size)
            // The whole capsule is taller by exactly the proud rung, at every rung.
            assertEquals(plain + DockStudio.CENTER_PROUD_DP, centre)
            // …and the circle inside it is taller than the circle a tab rests in, which is the
            // visible difference the report asked for.
            assertTrue(
                "${size.name}: the trigger is not bigger than a tab",
                DockStudio.centerTriggerSizeDp(size) > DockStudio.restingTabSizeDp(size)
            )
            // The tab row still fits inside the taller capsule: no tab is clipped by the new height.
            assertTrue(DockStudio.restingTabSizeDp(size) + DockStudio.pillPaddingDp(size) * 2 <= centre)
            // The glyph grows with the trigger and stays smaller than it.
            assertTrue(DockStudio.centerGlyphDp(size) > DockStudio.iconSizeDp(size))
            assertTrue(DockStudio.centerGlyphDp(size) < DockStudio.centerTriggerSizeDp(size))
        }
    }

    @Test
    fun theBloomedCentrePillIsWiderThanTheCircleAndNeverSwallowsTheDock() {
        DockSize.entries.forEach { size ->
            val bloomed = DockStudio.centerBloomedWidthDp(size)
            assertTrue("${size.name}: the bloom is not wider than the circle", bloomed > DockStudio.centerTriggerSizeDp(size))
            // 196dp is the ceiling: on a 360dp phone the pill must never take the row the tabs need.
            assertTrue(bloomed <= 196)
        }
        // The rung is visible in the bloom too — a Grand pill is wider than a Slim one.
        assertTrue(
            DockStudio.centerBloomedWidthDp(DockSize.LARGE) >=
                DockStudio.centerBloomedWidthDp(DockSize.SMALL)
        )
    }

    @Test
    fun theCentreClearanceCoversTheTallerCapsuleAtEveryGapAndRung() {
        DockSize.entries.forEach { size ->
            listOf(
                DockStudio.MIN_BOTTOM_GAP_DP,
                DockStudio.DEFAULT_BOTTOM_GAP_DP,
                DockStudio.MAX_BOTTOM_GAP_DP
            ).forEach { gap ->
                val clearance = DockStudio.centerClearanceDp(size, gap)
                // The page must reserve at least the taller capsule plus the air under it.
                assertTrue(
                    "${size.name}@$gap: the capsule is outside the clearance",
                    clearance >= DockStudio.centerDockHeightDp(size) + gap
                )
                // And never less than the plain rung's clearance, so switching styles can only add.
                assertTrue(clearance >= DockStudio.clearanceDp(size, gap))
                // The floor the product shipped with survives.
                assertTrue(clearance >= DockStudio.BASE_CLEARANCE_DP)
            }
        }
    }

    @Test
    fun theThreeStylesStillOwnThreeDifferentPlaces() {
        // The pass changed how the centre style is drawn, not which style draws what.
        assertTrue(ConnectionWidgetStyle.CENTER_MORPH.showsCenterTrigger)
        assertFalse(ConnectionWidgetStyle.CENTER_MORPH.showsDockPower)
        assertFalse(ConnectionWidgetStyle.CENTER_MORPH.showsHomeHero)
        assertFalse(ConnectionWidgetStyle.DOCK_ISLAND.showsCenterTrigger)
        assertFalse(ConnectionWidgetStyle.HERO_CARD.showsCenterTrigger)
    }

    // ── the quiet Home ─────────────────────────────────────────────────────────────────────

    @Test
    fun aFirstRunIsTheCalmLayoutAndNotTheFullOne() {
        val calm = HomeQuiet.CALM_SECTIONS
        assertEquals(
            listOf(HomeSection.HEADER, HomeSection.CONNECT, HomeSection.ROUTE),
            calm
        )
        // The calm layout is a real subset: quieter than the shipped one, and not empty.
        assertEquals(calm.toSet(), HomeStudio.visible(HomeQuiet.calmOrder(), HomeQuiet.CALM_HIDDEN).toSet())
        assertTrue("a first run must not be bare", HomeStudio.isBare(HomeQuiet.calmOrder(), HomeQuiet.CALM_HIDDEN).not())
        assertTrue(calm.size < HomeSection.entries.size)
        // The three essentials are the page, the decision and the route it will use.
        assertTrue(HomeSection.HEADER in calm)
        assertTrue(HomeSection.CONNECT in calm)
        assertTrue(HomeSection.ROUTE in calm)
        // Everything that is a *reading* rather than a decision starts off.
        listOf(HomeSection.PRESETS, HomeSection.STATS, HomeSection.SHORTCUTS).forEach { section ->
            assertTrue(
                "$section should start hidden",
                HomeStudio.isHidden(section, HomeQuiet.CALM_HIDDEN)
            )
        }
        assertEquals(HomeSection.entries.size - calm.size, HomeQuiet.calmHiddenCount)
    }

    @Test
    fun theFullLayoutIsStillOneAnswerToOneQuestion() {
        // "Show everything" writes the shipped order and nothing hidden — and reading it back is
        // the complete list, which is what makes the quiet default a default and not a deletion.
        val order = HomeQuiet.everythingOrder()
        val hidden = HomeQuiet.nothingHidden()
        assertEquals(HomeSection.entries.toSet(), HomeStudio.visible(order, hidden).toSet())
        assertEquals(HomeSection.entries.size, HomeStudio.visible(order, hidden).size)
        assertEquals("", hidden)
        // The calm layout is the same *order*, with three sections switched off — a reader who adds
        // one back gets it in its designed place rather than at the end.
        assertEquals(
            HomeSection.entries.filterNot { it in HomeQuiet.CALM_SECTIONS },
            HomeStudio.order(HomeQuiet.calmOrder()).filterNot { it in HomeQuiet.CALM_SECTIONS }
        )
    }

    @Test
    fun aFreshStoreWritesTheCalmLayoutAndAnUpgradeIsUntouched() {
        // The store's fallbacks are the calm ones; the *saved* string is whatever the reader built.
        assertEquals(
            HomeStudio.canonicalOrder(HomeQuiet.CALM_SECTIONS.joinToString(",") { it.id }),
            HomeQuiet.calmOrder()
        )
        assertTrue(HomeQuiet.isCalm(HomeQuiet.calmOrder(), HomeQuiet.CALM_HIDDEN))
        // A Home that has ever added a section back is no longer "calm", which is what hides the card.
        val added = HomeStudio.visible(HomeQuiet.calmOrder(), "")
        assertEquals(HomeSection.entries.size, added.size)
        assertFalse(HomeQuiet.isCalm(HomeQuiet.calmOrder(), ""))
    }

    @Test
    fun everySectionCanBeSwitchedOnAndBackOff() {
        HomeSection.entries.forEach { section ->
            val on = HomeQuiet.toggle(
                orderRaw = HomeQuiet.calmOrder(),
                hiddenRaw = HomeQuiet.CALM_HIDDEN,
                section = section
            )
            // Every section a first run already draws turns off, and turns back on; every hidden
            // one turns on. (The studio locks the heading's *switch* — that is a UI rule, and the
            // model stays symmetric, which is what keeps the round trip exact.)
            if (section in HomeQuiet.CALM_SECTIONS) {
                assertFalse("$section should have been switched off", on.nowVisible)
                assertTrue(HomeStudio.isHidden(section, on.hidden))
                // …and switching it back restores exactly the calm layout.
                val off = HomeQuiet.toggle(on.order, on.hidden, section)
                assertTrue(off.nowVisible)
                assertFalse(HomeStudio.isHidden(section, off.hidden))
            } else {
                assertTrue("$section should have been switched on", on.nowVisible)
                assertFalse(HomeStudio.isHidden(section, on.hidden))
            }
            // The order string is canonical whatever the switch did.
            assertEquals(HomeStudio.canonicalOrder(on.order), on.order)
        }

        // One pass that turns the three hidden sections on lands on the full, designed Home.
        var order = HomeQuiet.calmOrder()
        var hidden = HomeQuiet.CALM_HIDDEN
        HomeSection.entries.filterNot { it in HomeQuiet.CALM_SECTIONS }.forEach { section ->
            val toggle = HomeQuiet.toggle(order, hidden, section)
            order = toggle.order
            hidden = toggle.hidden
        }
        // Every section is drawn again — and in the string's own order, which is what the store
        // canonicalized. (A calm layout lists its three sections first; `HomeStudio.order` appends
        // the ones the string did not name rather than re-sorting, which is the rule that lets a
        // layout string be read without inverting anything.) "Show everything" writes the shipped
        // order itself, and that is the assertion one test above.
        assertEquals(HomeSection.entries.toSet(), HomeStudio.visible(order, hidden).toSet())
        assertEquals(HomeSection.entries.size, HomeStudio.visible(order, hidden).size)
    }

    @Test
    fun theLegacyFloorCannotMakeASwitchLookBroken() {
        // `modularShowStats` and `modularShowShortcuts` hide their sections even when the hidden
        // string says nothing. Switching such a section *on* has to lift the floor in the same
        // update, otherwise the reader presses a switch that changes nothing.
        val statsOn = HomeQuiet.toggle(
            orderRaw = HomeStudio.DEFAULT_ORDER,
            hiddenRaw = "",
            section = HomeSection.STATS,
            legacyStatsVisible = false
        )
        assertTrue(statsOn.nowVisible)
        assertTrue(statsOn.legacyStatsVisible)
        assertFalse(
            HomeStudio.isHidden(
                HomeSection.STATS,
                statsOn.hidden,
                legacyStatsVisible = statsOn.legacyStatsVisible,
                legacyShortcutsVisible = statsOn.legacyShortcutsVisible
            )
        )

        val shortcutsOff = HomeQuiet.toggle(
            orderRaw = HomeStudio.DEFAULT_ORDER,
            hiddenRaw = "",
            section = HomeSection.SHORTCUTS,
            legacyShortcutsVisible = false
        )
        // It was already hidden by the floor, so the switch turns it *on* — the same rule.
        assertTrue(shortcutsOff.nowVisible)
        assertTrue(shortcutsOff.legacyShortcutsVisible)

        // The two floors belong to their own sections and nothing else moves.
        assertEquals(true, statsOn.legacyShortcutsVisible)
        assertEquals(true, shortcutsOff.legacyStatsVisible)
    }

    @Test
    fun theCountsTheCardsPrintAreTheSectionsThePageDraws() {
        assertEquals(3, HomeQuiet.shownCount(HomeQuiet.calmOrder(), HomeQuiet.CALM_HIDDEN))
        assertEquals(
            HomeSection.entries.size,
            HomeQuiet.shownCount(HomeQuiet.everythingOrder(), HomeQuiet.nothingHidden())
        )
        // A legacy floor is part of the count, not an exception to it: the number under the preview
        // strip in Settings is the number of rows Home will draw.
        assertEquals(
            HomeSection.entries.size - 2,
            HomeQuiet.shownCount(
                HomeStudio.DEFAULT_ORDER,
                "",
                legacyStatsVisible = false,
                legacyShortcutsVisible = false
            )
        )
    }

    // ── the copy ───────────────────────────────────────────────────────────────────────────

    @Test
    fun everyNewSentenceHasAPersianAnswer() {
        val keys = listOf(
            "Sections on Home",
            "A quiet Home",
            "Only what you need is here. Add the rest whenever you want it.",
            // MARBLE_HOME_DECLUTTER_V260 — the quiet card's two verbs ("Show everything" and the
            // section picker) left with the card itself, so they are no longer keys to translate.
            "Home starts quiet: the heading, the connect control and the route it will use.",
            "The order is designed, not dragged: the heading, the decision, the modes, " +
                "the route, the live speed and the shortcuts.",
            "Protected uptime",
            "Tap to pick a server",
            "Home shows everything again",
            "Scan a QR"
        )
        keys.forEach { key ->
            val persian = faTranslate(key)
            assertNotEquals(key, persian)
            assertTrue("$key is not Persian", persian.any { it in '\u0600'..'\u06FF' })
            // V249's orthography rule survives the new copy: no glued plural, no glued `می`.
            assertEquals(PersianOrthography.normalize(persian), persian)
        }
    }
}
