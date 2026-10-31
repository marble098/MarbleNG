package com.marbleng.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MARBLE_DOCK_ICON_FIRST_V255 — the first-run dock is icon-led, with captions opt-in. */
class DockIconFirstV255Test {
    @Test
    fun freshSettingsShowIconsAndHideCaptions() {
        val settings = AppSettings()

        assertTrue(settings.dockShowIcons)
        assertFalse(settings.dockShowLabels)
        assertTrue(dockShowsIcons(settings.dockShowIcons, settings.dockShowLabels))
        assertFalse(dockShowsLabels(settings.dockShowIcons, settings.dockShowLabels))
    }

    @Test
    fun rendererKeepsAnIconIfBothFlagsAreInvalid() {
        assertTrue(dockShowsIcons(showIcons = false, showLabels = false))
        assertFalse(dockShowsLabels(showIcons = false, showLabels = false))
        assertFalse(dockShowsIcons(showIcons = false, showLabels = true))
        assertTrue(dockShowsLabels(showIcons = false, showLabels = true))
    }
}
