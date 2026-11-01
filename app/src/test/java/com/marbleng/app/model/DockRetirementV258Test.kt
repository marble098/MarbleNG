package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * MARBLE_FLOATING_DOCK_V258 — what is left of V232's presentation preference.
 *
 * V232 let the dock be attached to the bottom edge or float above it, and stored the answer as
 * `<size>|<presentation>`. V258 deleted the attached bar, so the second half of that string has no
 * meaning any more: the preference is the rung alone. These two tests pin the *retirement* rather
 * than the feature — a preference written by an older build still resolves to the right size, and
 * nothing in the model can put a presentation back.
 */
class DockRetirementV258Test {
    @Test
    fun aPreferenceWrittenBeforeV258KeepsItsSizeAndLosesItsRetiredWord() {
        // Legacy strings, including the presentation the product no longer has, and junk.
        assertEquals(DockSize.MEDIUM, parseDockSize("medium"))
        assertEquals(DockSize.MEDIUM, parseDockSize("medium|attached"))
        assertEquals(DockSize.LARGE, parseDockSize("large|floating"))
        assertEquals(DockSize.MEDIUM, parseDockSize("unknown|floating"))
        assertEquals(DockSize.MEDIUM, parseDockSize(""))

        // The rewrite is a bare rung id, so the retired word cannot survive a save.
        val rewritten = encodeDockPreference(parseDockSize("medium|attached"))
        assertEquals("medium", rewritten)
        assertFalse(rewritten.contains('|'))
    }

    @Test
    fun everyRungRoundTripsThroughThePreferenceWithoutAPresentation() {
        DockSize.entries.forEach { size ->
            val encoded = encodeDockPreference(size)
            assertEquals(size.id, encoded)
            assertEquals(size, parseDockSize(encoded))
        }
    }
}
