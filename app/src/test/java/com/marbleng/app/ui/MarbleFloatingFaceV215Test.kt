package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FLOATING_FACE_V215 — the floating discs wear a deep face and a white glyph.
 *
 * The black centre was a contrast decision made on the wrong surface: the navy ink was scored
 * against the bright action tone, so on a light disc it read as a hole. The face is now derived
 * from the tone and the glyph is white, so the guarantee is that white always clears the floor on
 * the face, for every tone a theme can produce, and that the face keeps the tone's own hue.
 */
class MarbleFloatingFaceV215Test {

    private val white = 0xFFFFFFFF.toInt()

    private val tones = listOf(
        "daylight connect" to 0xFF0066CC.toInt(),
        "daylight securing" to 0xFF001144.toInt(),
        "pure black connect" to 0xFF00E5FF.toInt(),
        "pale rose stop" to 0xFFFF718B.toInt(),
        "wallpaper teal" to 0xFF2BD9C2.toInt(),
        "pale yellow measure" to 0xFFFFE27A.toInt(),
        "brand cyan" to 0xFF22D3EE.toInt(),
    )

    @Test
    fun whiteGlyphClearsTheContrastFloorOnEveryFace() {
        tones.forEach { (name, tone) ->
            val face = marbleDeepFaceArgb(tone)
            val ratio = marbleContrastRatioArgb(white, face)
            assertTrue("$name: white on its face is $ratio, below 4.5", ratio >= 4.5f)
        }
    }

    @Test
    fun theFaceKeepsTheHueOfItsTone() {
        // A blue tone must still read blue, and a red tone must still read red: the face is the
        // tone deepened, not a fresh navy disc in every theme.
        val blue = marbleDeepFaceArgb(0xFF0066CC.toInt())
        assertTrue("blue face lost its blue", (blue and 0xFF) > ((blue shr 16) and 0xFF))
        val red = marbleDeepFaceArgb(0xFFFF718B.toInt())
        assertTrue("red face lost its red", ((red shr 16) and 0xFF) > ((red shr 8) and 0xFF))
    }

    @Test
    fun theFaceIsOpaque() {
        tones.forEach { (name, tone) ->
            assertEquals("$name: face must be opaque", 0xFF, (marbleDeepFaceArgb(tone) ushr 24) and 0xFF)
        }
    }

    @Test
    fun theFaceIsDeterministic() {
        tones.forEach { (_, tone) ->
            assertEquals(marbleDeepFaceArgb(tone), marbleDeepFaceArgb(tone))
        }
    }
}
