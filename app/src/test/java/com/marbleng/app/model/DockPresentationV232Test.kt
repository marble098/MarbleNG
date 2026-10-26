package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DockPresentationV232Test {
    @Test
    fun legacyAndMalformedPreferencesFallBackToAttached() {
        assertEquals(DockPresentation.ATTACHED, parseDockPresentation("medium"))
        assertEquals(DockPresentation.ATTACHED, parseDockPresentation("medium|unknown"))
        assertEquals(DockSize.MEDIUM, parseDockSize("unknown|floating"))
    }

    @Test
    fun sizeAndPresentationShareTheExistingPreferenceWithoutLoss() {
        DockSize.entries.forEach { size ->
            DockPresentation.entries.forEach { presentation ->
                val encoded = encodeDockPreference(size, presentation)
                assertEquals(size, parseDockSize(encoded))
                assertEquals(presentation, parseDockPresentation(encoded))
            }
        }
    }
}
