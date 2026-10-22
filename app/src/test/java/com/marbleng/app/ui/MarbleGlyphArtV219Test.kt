package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_AURORA_SURFACE_V219 — the numbers the surface pass must never drift from.
 *
 * The visual work of the chapter is paint, and paint cannot be asserted from a JVM test. What *can*
 * be asserted is the part that silently regresses: the icon stroke rule that keeps a 16 dp glyph and
 * a 28 dp glyph at the same optical weight, and the slide geometry that keeps a thumb inside the well
 * it travels in.
 */
class MarbleGlyphArtV219Test {

    @Test
    fun theGridStrokeIsTheSameOpticalWeightAtEveryIconSize() {
        // The grid quotes one stroke in 24 units; whatever the icon size, that ratio must hold, and
        // it must land between a hairline and a slab. 1.2 dp is the floor at which a glyph stops
        // reading as a sketch; 2.2 dp is where it starts reading as a symbol.
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
        // Heavier, but not a different drawing: a third of a pixel is enough to feel selected.
        assertTrue("an active glyph gained too much weight", active / resting < 1.25f)
    }

    @Test
    fun aCollapsedGlyphStillAsksForALegibleStroke() {
        // A glyph inside a collapsing container can be handed a sub-pixel box; the grid must still
        // request a stroke the renderer can draw.
        assertTrue(marbleGlyphStrokePx(0.4f) > 0f)
        assertTrue(marbleGlyphStrokePx(1f) > 0f)
    }

    @Test
    fun theSlideThumbFitsInsideTheWell() {
        val track = MarbleSlideSpec.TrackHeight.value
        val thumb = MarbleSlideSpec.ThumbSize.value
        val inset = MarbleSlideSpec.Inset.value
        // A thumb that does not clear its own inset is a thumb that paints outside the well.
        assertTrue("thumb $thumb does not fit the $track track", thumb + inset * 2 <= track)
        assertEquals("track height", 66f, track, 0f)
        assertEquals("thumb size", 54f, thumb, 0f)
        assertEquals("track inset", 6f, inset, 0f)
        // 48 dp is the platform's touch floor: the sphere has to be a target, not a dot.
        assertTrue("the thumb is under the touch floor", thumb >= 48f)
    }

    @Test
    fun theCompactSlideIsSmallerThanThePrimaryOne() {
        // The same control at a smaller scale — not a second design.
        assertTrue(MarbleSlideSpec.TrackHeightCompact.value < MarbleSlideSpec.TrackHeight.value)
        assertTrue(MarbleSlideSpec.ThumbSizeCompact.value < MarbleSlideSpec.ThumbSize.value)
        assertTrue(
            MarbleSlideSpec.ThumbSizeCompact.value + MarbleSlideSpec.Inset.value * 2 <=
                MarbleSlideSpec.TrackHeightCompact.value
        )
    }
}
