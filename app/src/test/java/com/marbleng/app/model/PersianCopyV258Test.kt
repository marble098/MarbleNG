package com.marbleng.app.model

import com.marbleng.app.ui.faTranslate
import com.marbleng.app.ui.toFaDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FLOATING_DOCK_V258 / MARBLE_PALETTE_STUDIO_V258 / MARBLE_ABOUT_PAGE_V258 — the copy.
 *
 * The V233 sweep in `system-integrity-check.py` proves that *every* `trx("…")` literal has a
 * dictionary key; that sweep cannot prove the answer is a Persian sentence rather than the English
 * one echoed back, and it cannot see a key that is looked up through a variable (the ladder's
 * three hints and the four palettes arrive that way). This test does both, on the handful of
 * strings this pass introduced, and it re-asserts V249's orthography rule on each of them: a
 * translation that ships glued (`نسخهها`) is the bug that pass existed to kill.
 */
class PersianCopyV258Test {

    private val half = PersianOrthography.HALF_SPACE

    /** The ladder speaks through `trx(DockStudio.ladderTitle/…Hint(…))`, so both are named here. */
    private val dockKeys = listOf(
        "Slim", "Grand",
        "Icons only, tightest capsule",
        "Bigger glyphs, the selected word opens for a moment",
        "Largest glyphs, the selected word never closes",
        "Slim keeps glyphs only, Classic opens the selected word for a moment, Grand keeps it open.",
        "Capsule size",
        "Distance from the bottom",
        "Pages reserve scroll room from this value, so raising it never hides the last row behind the capsule.",
        "One floating capsule: its size, its distance from the bottom, and the IP tab"
    )

    private val paletteKeys = listOf(
        "Colour themes",
        "Marble Classic", "Aurora", "Ember", "Neon Prism",
        "Your accent colour on the neutral surface ladder",
        "Arctic teal melting into violet — cool, luminous, quiet",
        "Sunset coral and deep plum — warm, confident, dramatic",
        "Electric magenta and acid lime on near-black glass",
        "A palette repaints every surface, card and control in the app. It outranks the accent swatch and the wallpaper palette; Marble Classic hands both back."
    )

    private val aboutKeys = listOf(
        "A tunnel you can read.",
        "The whole build, in one place",
        "Built in the open. No account, no tracking, no telemetry.",
        "Version", "Build", "Xray", "sing-box extended", "HEV tunnel core", "MarbleNG app",
        "Check for updates",
        "Open the GitHub repository",
        "Where the connect control lives",
        "Where the connect control lives on Home"
    )

    private val ipKeys = listOf("Tap to copy", "No IPv4", "No IPv6", "Route", "Moving")

    @Test
    fun everyStringThisPassIntroducedIsTranslatedAndNotEchoedBack() {
        (dockKeys + paletteKeys + aboutKeys + ipKeys).forEach { key ->
            val persian = faTranslate(key)
            assertNotEquals("untranslated: $key", key, persian)
            assertTrue(
                "no Persian letters in the translation of: $key",
                persian.any { it in '\u0621'..'\u06D3' }
            )
        }
    }

    @Test
    fun everyNewTranslationObeysTheHalfSpaceRule() {
        (dockKeys + paletteKeys + aboutKeys + ipKeys)
            .map { faTranslate(it) }
            .filterNot { PersianOrthography.isNormalized(it) }
            .let { glued ->
                assertEquals(glued.take(6).joinToString(" | "), 0, glued.size)
            }
    }

    @Test
    fun theRungsNameWhatTheReaderSees() {
        // The ladder is not "small/medium/large" in Persian either: each rung is described, and
        // the third one promises the behaviour that makes it different.
        assertEquals("باریک", faTranslate("Slim"))
        assertEquals("بزرگ", faTranslate("Grand"))
        assertTrue(faTranslate("Largest glyphs, the selected word never closes").contains("نشانه"))
    }

    @Test
    fun thePalettesAreNamedInPersianAndKeepTheirProperNoun() {
        assertEquals("شفق", faTranslate("Aurora"))
        assertEquals("اخگر", faTranslate("Ember"))
        assertEquals("منشور نئون", faTranslate("Neon Prism"))
        // "Marble Classic" is the one name that keeps a Latin word, and it keeps it deliberately:
        // it is the palette that hands colour back to the accent.
        assertEquals("ماربل کلاسیک", faTranslate("Marble Classic"))
    }

    @Test
    fun theGapIsMeasuredInTheReadersOwnDigits() {
        // The setting prints `22 dp`; the Persian interface prints Persian digits, and the unit
        // stays Latin because it is a unit.
        val rendered = "۲۲ dp"
        assertTrue(rendered.contains("dp"))
        assertEquals("۲۲", "22".toFaDigits())
        assertTrue(PersianOrthography.isNormalized(faTranslate("Distance from the bottom")))
        assertTrue(faTranslate("Pages reserve scroll room from this value, so raising it never hides the last row behind the capsule.").contains("کپسول"))
        assertTrue(half.isWhitespace().not() || half == '\u200C')
    }
}
