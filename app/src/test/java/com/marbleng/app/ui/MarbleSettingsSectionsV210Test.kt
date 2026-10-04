package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SETTINGS_SECTIONS_V210 — the state of the Settings tree.
 *
 * "Organise the settings page into main sections with sub-sections under them" is a layout
 * request, but the part that can go wrong invisibly is the *state*: which sections are closed,
 * what happens when a new one is added, and whether "expand all" means what it says. Six
 * booleans sprinkled through a composable answer none of those questions; one set of collapsed
 * keys answers all of them, and answers them here.
 */
class MarbleSettingsSectionsV210Test {

    private val keys = SettingsHubGroups.Keys

    @Test
    fun theHubNamesEverySectionItDraws() {
        // The page's keys and the policy's keys are one list: "collapse all" has to name every
        // section, and a name invented twice is a name that drifts.
        assertEquals(6, keys.size)
        assertEquals(keys, keys.distinct())
        assertTrue(keys.all { it.isNotBlank() })
        assertEquals(listOf("home", "connection", "measurement", "data", "appearance", "system"), keys)
    }

    @Test
    fun aSectionNobodyTouchedIsOpen() {
        // The state is the set of CLOSED keys. That inversion is the whole reason a section added
        // in a later release appears open instead of inheriting a stored decision about it.
        assertTrue(SettingsHubPolicy.isOpen(emptySet(), "appearance"))
        assertTrue(
            "a section this build has never heard of still opens",
            SettingsHubPolicy.isOpen(SettingsHubPolicy.collapseAll(keys), "brand-new-section")
        )
    }

    @Test
    fun tappingAHeaderClosesOnlyThatSection() {
        val closed = SettingsHubPolicy.toggled(emptySet(), "appearance")
        assertFalse(SettingsHubPolicy.isOpen(closed, "appearance"))
        assertTrue(SettingsHubPolicy.isOpen(closed, "home"))
        // And tapping it again opens it: the header is one control, not two.
        assertTrue(SettingsHubPolicy.isOpen(SettingsHubPolicy.toggled(closed, "appearance"), "appearance"))
    }

    @Test
    fun expandAllMeansEverySectionIsOpenWhateverWasClosedBefore() {
        val reopened = SettingsHubPolicy.expandAll()
        assertTrue(SettingsHubPolicy.allOpen(reopened, keys))
        assertEquals(keys.size, SettingsHubPolicy.openCount(reopened, keys))
    }

    @Test
    fun collapseAllNamesEverySectionItCloses() {
        val closed = SettingsHubPolicy.collapseAll(keys)
        assertEquals(0, SettingsHubPolicy.openCount(closed, keys))
        assertFalse(SettingsHubPolicy.allOpen(closed, keys))
        // A section missing from the list would be silently left open by "collapse all".
        for (key in keys) assertFalse(SettingsHubPolicy.isOpen(closed, key))
    }

    @Test
    fun theHeaderCountsOnlySectionsThatExist() {
        // The "n / 6 sections open" line reads the same list the page draws, so it can never
        // claim a number the page cannot show.
        val half = SettingsHubPolicy.toggled(SettingsHubPolicy.toggled(emptySet(), "home"), "system")
        assertEquals(keys.size - 2, SettingsHubPolicy.openCount(half, keys))
        assertEquals(0, SettingsHubPolicy.openCount(half, emptyList()))
    }

    @Test
    fun anEmptyTreeIsTriviallyOpenAndNeverReportsAllOpen() {
        // Guards the header's Expand/Collapse label: with nothing to open, "Collapse all" would
        // be a control that does nothing, so the page offers "Expand all" instead.
        assertTrue(SettingsHubPolicy.allOpen(emptySet(), keys))
        assertFalse(SettingsHubPolicy.allOpen(emptySet(), emptyList()))
    }
}
