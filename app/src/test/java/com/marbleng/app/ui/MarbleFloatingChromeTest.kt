package com.marbleng.app.ui

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * MARBLE_FLOATING_CHROME_V201 — the contrast guarantee behind the floating buttons.
 *
 * The defect being fixed is that the chrome's colours were *assumed* rather than measured:
 * an accent painted at 100 % on top of the same accent at 24 %, a glyph painted
 * `Color.White` whatever it sat on, and a shadow painted `Color.Black` whatever it fell on.
 * Nothing in the product could have caught those, because nothing in it computed a ratio.
 *
 * These tests pin the two numbers the design actually promises — 4.5:1 for text, 3:1 for a
 * graphical object — across the whole palette, including the Material You pastels that broke
 * the old assumption.
 */
class MarbleFloatingChromeTest {

    // The palette, as the theme writes it.
    private val electricBlue = 0xFF0066CC.toInt()
    private val brightBlue = 0xFF3399FF.toInt()
    private val cyan = 0xFF00E5FF.toInt()
    private val emerald = 0xFF00E08A.toInt()
    private val danger = 0xFFFF4D6D.toInt()
    private val lightDanger = 0xFFFF718B.toInt()
    private val amber = 0xFFFFB300.toInt()
    private val amethyst = 0xFF9D7BFF.toInt()
    private val icePage = 0xFFF4F8FD.toInt()
    private val navyPage = 0xFF0B1220.toInt()

    // Material You `primary` is routinely a pastel. This is the case the old design failed.
    private val pastels = listOf(
        0xFFA8C7FA.toInt(), // dynamic light primary
        0xFFFFB8C6.toInt(),
        0xFFC3E8AC.toInt(),
        0xFFF2B8B5.toInt(),
        0xFFB9C6FF.toInt()
    )

    @Test
    fun `luminance is anchored at black and white`() {
        assertEquals(0f, marbleLuminanceArgb(0xFF000000.toInt()), 0.001f)
        assertEquals(1f, marbleLuminanceArgb(0xFFFFFFFF.toInt()), 0.001f)
        assertTrue(marbleLuminanceArgb(electricBlue) < marbleLuminanceArgb(cyan))
    }

    @Test
    fun `contrast against itself is one and against black and white is the extremes`() {
        assertEquals(1f, marbleContrastRatioArgb(electricBlue, electricBlue), 0.01f)
        assertEquals(21f, marbleContrastRatioArgb(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01f)
        assertEquals(21f, marbleContrastRatioArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01f)
    }

    @Test
    fun `a translucent colour is flattened before it is compared`() {
        // 24 % of the electric blue over the ice page must not be compared as if it were the
        // electric blue itself — that was the exact bug in the selected-pill recipe.
        val wash = marbleWithAlphaArgb(electricBlue, 0.24f)
        val asIfOpaque = marbleContrastRatioArgb(electricBlue, icePage)
        val flattened = marbleContrastRatioArgb(wash, icePage)
        assertTrue("a 24 % wash is not the accent at 100 %", flattened < asIfOpaque)
        assertTrue(flattened >= 1f)
    }

    @Test
    fun `white glyphs fail on the dark theme bright blue and the fix catches it`() {
        // #FFFFFF on #3399FF is 2.94:1 — under the 3:1 floor for a graphical object, on the
        // single most important control in the product. This is the regression test.
        val asShipped = marbleContrastRatioArgb(0xFFFFFFFF.toInt(), brightBlue)
        assertTrue("the old hard-coded white really was failing: $asShipped", asShipped < 3.0f)

        val fixed = marbleOnColorArgb(brightBlue)
        assertTrue(
            "the chosen ink must clear the graphical floor",
            marbleContrastRatioArgb(fixed, brightBlue) >= 3.0f
        )
    }

    @Test
    fun `the chosen ink clears three to one on every accent in the palette`() {
        for (accent in listOf(
            electricBlue, brightBlue, cyan, emerald, danger, lightDanger, amber, amethyst
        ) + pastels) {
            val ink = marbleOnColorArgb(accent)
            val ratio = marbleContrastRatioArgb(ink, accent)
            assertTrue(
                "ink on #${Integer.toHexString(accent).uppercase()} is $ratio:1",
                ratio >= 3.0f
            )
        }
    }

    @Test
    fun `a light accent keeps the dark ink and a dark accent keeps the white one`() {
        assertEquals("navy keeps white ink", 0xFFFFFFFF.toInt(), marbleOnColorArgb(navyPage))
        assertEquals("cyan keeps the dark ink", 0xFF000033.toInt(), marbleOnColorArgb(cyan))
        assertEquals("amber keeps the dark ink", 0xFF000033.toInt(), marbleOnColorArgb(amber))
    }

    @Test
    fun `readableOn leaves a passing accent completely untouched`() {
        // Identity matters: a selected tab must still be the colour the user recognises.
        for (accent in listOf(electricBlue, emerald, danger)) {
            val onDark = marbleReadableOnArgb(accent, navyPage, 4.5f)
            val onLight = marbleReadableOnArgb(accent, icePage, 4.5f)
            if (marbleContrastRatioArgb(accent, navyPage) >= 4.5f) {
                assertEquals(accent, onDark)
            }
            if (marbleContrastRatioArgb(accent, icePage) >= 4.5f) {
                assertEquals(accent, onLight)
            }
        }
    }

    @Test
    fun `readableOn repairs every pastel on both pages`() {
        for (pastel in pastels) {
            for (page in listOf(icePage, navyPage)) {
                val repaired = marbleReadableOnArgb(pastel, page, 4.5f)
                val ratio = marbleContrastRatioArgb(repaired, page)
                assertTrue(
                    "#${Integer.toHexString(pastel).uppercase()} on #${Integer.toHexString(page).uppercase()} reached only $ratio:1",
                    ratio >= 4.5f
                )
            }
        }
    }

    @Test
    fun `readableOn degrades gracefully instead of snapping to ink`() {
        // Pushed toward the endpoint, but recognisably still the accent — otherwise every
        // selected tab in a pastel theme would look the same.
        val repaired = marbleReadableOnArgb(0xFFA8C7FA.toInt(), icePage, 4.5f)
        val distance = kotlin.math.abs(
            ((repaired ushr 16) and 0xFF) - 0xA8
        ) + kotlin.math.abs(((repaired ushr 8) and 0xFF) - 0xC7)
        assertTrue("the repaired colour must stay close to the accent: $distance", distance < 400)
        assertNotBlackOrWhite(repaired)
    }

    @Test
    fun `shadow alpha rises with elevation and stays inside the system range`() {
        var previous = 0f
        for (elevation in listOf(0f, 1f, 2f, 3f, 4f, 6f, 8f, 12f, 24f, 64f)) {
            val alpha = marbleFloatShadowAlpha(elevation)
            assertTrue("elevation $elevation -> $alpha", alpha in 0.10f..0.34f)
            assertTrue("a higher element must not cast a lighter shadow", alpha >= previous)
            previous = alpha
        }
        assertTrue(
            "the dock must read as a layer above the bar, not a sticker on it",
            marbleFloatShadowAlpha(8f) > marbleFloatShadowAlpha(3f)
        )
    }

    @Test
    fun `the ambient shadow is always softer than the spot`() {
        for (elevation in listOf(0f, 3f, 8f, 24f)) {
            val (ambient, spot) = marbleFloatShadowPair(elevation)
            assertTrue("elevation $elevation: $ambient vs $spot", ambient < spot)
            assertTrue(spot <= 0.34f)
        }
    }

    @Test
    fun `compositing is source over`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(white, marbleCompositeArgb(white, black))
        assertEquals(black, marbleCompositeArgb(black, white))
        assertEquals(black, marbleCompositeArgb(marbleWithAlphaArgb(black, 0f), black))
        // Half-transparent white over black is mid grey.
        val grey = marbleCompositeArgb(marbleWithAlphaArgb(white, 0.5f), black)
        val channel = (grey ushr 16) and 0xFF
        assertTrue("expected mid grey, got $channel", channel in 126..129)
    }

    private fun assertNotBlackOrWhite(argb: Int) {
        assertTrue(
            "the accent was flattened to an endpoint: #${Integer.toHexString(argb).uppercase()}",
            argb != 0xFF000000.toInt() && argb != 0xFFFFFFFF.toInt()
        )
    }
}
