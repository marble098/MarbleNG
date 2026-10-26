package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DockPresentationV232Test {
    @Test
    fun invalidOrMissingPresentationFallsBackToAttached() {
        assertEquals(DockPresentation.ATTACHED, parseDockPresentation(""))
        assertEquals(DockPresentation.ATTACHED, parseDockPresentation("unknown"))
    }

    @Test
    fun bothDockLayoutsRoundTripFromPreferenceIds() {
        DockPresentation.entries.forEach { presentation ->
            assertEquals(presentation, parseDockPresentation(presentation.id))
        }
        assertEquals(DockPresentation.FLOATING, parseDockPresentation("FLOATING"))
    }
}
