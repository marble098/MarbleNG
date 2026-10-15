package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
