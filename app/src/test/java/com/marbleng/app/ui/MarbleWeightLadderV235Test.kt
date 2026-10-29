package com.marbleng.app.ui

import androidx.compose.ui.text.font.FontWeight
import com.marbleng.app.ui.theme.MarbleWeightSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_WEIGHT_LADDER_V235 — one weight per role, every rung backed by a real
 * bundled Vazirmatn face. These pins keep the ladder a ladder: if two rungs ever
 * collapse into one weight, the hierarchy the surfaces were retuned for is gone.
 */
class MarbleWeightLadderV235Test {

    @Test
    fun theLadderClimbsOneRungPerRole() {
        assertTrue(MarbleWeightSpec.BODY < MarbleWeightSpec.LABEL)
        assertTrue(MarbleWeightSpec.LABEL < MarbleWeightSpec.TITLE)
        assertTrue(MarbleWeightSpec.TITLE < MarbleWeightSpec.HEADLINE)
        assertTrue(MarbleWeightSpec.HEADLINE < MarbleWeightSpec.DISPLAY)
        // Measured values read as firmly as headlines, never firmer.
        assertEquals(MarbleWeightSpec.HEADLINE, MarbleWeightSpec.NUMERIC)
    }

    @Test
    fun everyRungHasARealBundledFace() {
        assertEquals(
            listOf(400, 500, 600, 700, 800),
            MarbleWeightSpec.REGISTERED
        )
        listOf(
            MarbleWeightSpec.BODY, MarbleWeightSpec.LABEL, MarbleWeightSpec.TITLE,
            MarbleWeightSpec.HEADLINE, MarbleWeightSpec.DISPLAY, MarbleWeightSpec.NUMERIC
        ).forEach { assertTrue("$it registered", MarbleWeightSpec.isRegistered(it)) }

        // The deliberately unshipped weights: thin strokes at caption sizes are where
        // Persian legibility dies, and nothing in this app is a poster.
        assertFalse(MarbleWeightSpec.isRegistered(300))
        assertFalse(MarbleWeightSpec.isRegistered(900))
    }

    @Test
    fun rungsAreTheNamedComposeWeights() {
        assertEquals(FontWeight.Normal, FontWeight(MarbleWeightSpec.BODY))
        assertEquals(FontWeight.Medium, FontWeight(MarbleWeightSpec.LABEL))
        assertEquals(FontWeight.SemiBold, FontWeight(MarbleWeightSpec.TITLE))
        assertEquals(FontWeight.Bold, FontWeight(MarbleWeightSpec.HEADLINE))
        assertEquals(FontWeight.ExtraBold, FontWeight(MarbleWeightSpec.DISPLAY))
    }

    @Test
    fun theUpdateDialogCanNameAPreReleaseInPersian() {
        // MARBLE_LATEST_VERSION_RULE_V235 — the dialog offers the newest published
        // build under its real label; the Persian UI must be able to say so.
        val persian = faTranslate("Pre-release")
        assertTrue(persian.any { it.code in 0x0600..0x06FF })
        assertFalse(persian == "Pre-release")
    }
}
