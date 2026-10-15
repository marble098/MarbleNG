package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarbleSettingsSectionsV210Test {
    private val keys = SettingsHubGroups.Keys

    @Test
    fun theHubNamesEverySectionItDraws() {
        assertEquals(6, keys.size)
        assertEquals(keys, keys.distinct())
        assertTrue(keys.all { it.isNotBlank() })
        assertEquals(listOf("home", "connection", "measurement", "data", "appearance", "system"), keys)
    }

    @Test
    fun aSectionNobodyTouchedIsOpen() {
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

        for (key in keys) assertFalse(SettingsHubPolicy.isOpen(closed, key))
    }

    @Test
    fun theHeaderCountsOnlySectionsThatExist() {
        val half = SettingsHubPolicy.toggled(SettingsHubPolicy.toggled(emptySet(), "home"), "system")
        assertEquals(keys.size - 2, SettingsHubPolicy.openCount(half, keys))
        assertEquals(0, SettingsHubPolicy.openCount(half, emptyList()))
    }

    @Test
    fun anEmptyTreeIsTriviallyOpenAndNeverReportsAllOpen() {
        assertTrue(SettingsHubPolicy.allOpen(emptySet(), keys))
        assertFalse(SettingsHubPolicy.allOpen(emptySet(), emptyList()))
    }
}
