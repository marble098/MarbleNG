package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarbleGlyphArtV219Test {

    @Test
    fun theGridStrokeIsTheSameOpticalWeightAtEveryIconSize() {
        listOf(16f, 18f, 20f, 22f, 24f, 28f).forEach { iconSize ->
            val stroke = marbleGlyphStrokePx(iconSize)
            assertEquals(
                "stroke ratio drifted at $iconSize",
                iconSize * (1.75f / 24f),
                stroke,
                0.001f
            )
            assertTrue("stroke at $iconSize is a hairline", stroke >= 1.15f)
            assertTrue("stroke at $iconSize is a slab", stroke <= 2.2f)
        }
    }

    @Test
    fun aSelectedGlyphGainsWeightAndNeverGeometry() {
        val resting = marbleGlyphStrokePx(22f)
        val active = marbleGlyphStrokePx(22f, weight = 1.14f)
        assertTrue("an active glyph must not be lighter", active > resting)
        assertTrue("an active glyph gained too much weight", active / resting < 1.25f)
    }

    @Test
    fun aCollapsedGlyphStillAsksForALegibleStroke() {
        assertTrue(marbleGlyphStrokePx(0.4f) > 0f)
        assertTrue(marbleGlyphStrokePx(1f) > 0f)
    }
}
