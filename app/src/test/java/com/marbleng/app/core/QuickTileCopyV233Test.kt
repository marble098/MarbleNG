package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the system tile is not allowed to stay English. */
class QuickTileCopyV233Test {

    private fun hasPersian(value: String): Boolean =
        value.any { it.code in 0x0600..0x06FF || it.code in 0xFB50..0xFDFF }

    @Test
    fun theTileFollowsTheSameLanguageRuleAsTheApp() {
        assertEquals(NotificationLanguage.FA, QuickTileCopy.languageFor("fa", "en"))
        assertEquals(NotificationLanguage.FA, QuickTileCopy.languageFor("system", "fa"))
        assertEquals(NotificationLanguage.EN, QuickTileCopy.languageFor("en", "fa"))
        assertEquals(NotificationLanguage.EN, QuickTileCopy.languageFor("system", "de"))
        assertEquals(NotificationLanguage.EN, QuickTileCopy.languageFor(null, null))
    }

    @Test
    fun everyTileStateSpeaksPersianWhenPersianIsSelected() {
        val states = listOf("CONNECTED", "CONNECTING", "DISCONNECTING", "BLOCKED", "DISCONNECTED")
        states.forEach { state ->
            val label = QuickTileCopy.label(state, NotificationLanguage.FA)
            assertTrue("$state label stayed English: $label", hasPersian(label))
        }
        assertEquals("MarbleNG • On", QuickTileCopy.label("CONNECTED", NotificationLanguage.EN))
    }

    @Test
    fun transientLabelsAndEmptyHintsAreLocalized() {
        assertTrue(
            hasPersian(
                QuickTileCopy.transientLabel(QuickTileTransient.CONNECTING, NotificationLanguage.FA)
            )
        )
        assertTrue(
            hasPersian(
                QuickTileCopy.transientLabel(QuickTileTransient.DISCONNECTING, NotificationLanguage.FA)
            )
        )
        assertEquals("", QuickTileCopy.transientLabel(null, NotificationLanguage.FA))
        assertEquals(
            "Connecting…",
            QuickTileCopy.transientLabel(QuickTileTransient.CONNECTING, NotificationLanguage.EN)
        )
        assertTrue(
            hasPersian(
                QuickTileCopy.subtitle("DISCONNECTED", "", "", NotificationLanguage.FA)
            )
        )
    }

    @Test
    fun latinServerNamesAreIsolatedNotReordered() {
        val subtitle = QuickTileCopy.subtitle("CONNECTED", "Berlin 01", "", NotificationLanguage.FA)
        assertTrue(subtitle.contains(NotificationCopy.FSI))
        assertTrue(subtitle.contains(NotificationCopy.PDI))
        assertTrue(subtitle.contains("Berlin 01"))
        assertEquals(
            "Berlin 01",
            QuickTileCopy.subtitle("CONNECTED", "Berlin 01", "", NotificationLanguage.EN)
        )
    }

    @Test
    fun theSpokenDescriptionNamesTheActionInTheUsersLanguage() {
        val fa = QuickTileCopy.contentDescription("CONNECTED", "Berlin 01", "", NotificationLanguage.FA)
        assertTrue(hasPersian(fa))
        assertTrue(fa.contains("Berlin 01"))
        val en = QuickTileCopy.contentDescription(
            "DISCONNECTED", "", "Frankfurt 2", NotificationLanguage.EN
        )
        assertTrue(en.contains("Frankfurt 2"))
        assertFalse(hasPersian(en))
    }
}
