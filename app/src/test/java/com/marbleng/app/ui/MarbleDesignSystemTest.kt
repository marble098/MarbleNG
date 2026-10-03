package com.marbleng.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MarbleDesignSystemTest {
    // MARBLE_HOME_HEARTBEAT_PING_V206 — the green ceiling moved from 100 ms to
    // HOME_HEARTBEAT_GREEN_MAX_MS (160 ms). Below it a link is simply fine; 100 ms remains the
    // distance at which one server is meaningfully better than another, but that is a sorting
    // question and the grade is a colour, so the colour answers "can I use this?".
    @Test
    fun pingBandsFollowProductThresholds() {
        assertEquals(MarbleMetricBand.UNKNOWN, pingMetricBand(0))
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(1))
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(99))
        // The whole point of the change: a perfectly usable mobile link is green.
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(100))
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(140))
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(HOME_HEARTBEAT_GREEN_MAX_MS - 1))
        assertEquals(MarbleMetricBand.WARNING, pingMetricBand(HOME_HEARTBEAT_GREEN_MAX_MS))
        assertEquals(MarbleMetricBand.WARNING, pingMetricBand(HOME_HEARTBEAT_AMBER_MAX_MS))
        assertEquals(MarbleMetricBand.POOR, pingMetricBand(HOME_HEARTBEAT_AMBER_MAX_MS + 1))
        assertEquals(MarbleMetricBand.POOR, pingMetricBand(999))
    }

    @Test
    fun theProductHasOneGreenCeilingForPing() {
        // A second curve for the heartbeat would be a second opinion about what "green" means,
        // so the heartbeat tone is the ping band, seen through the standard metric colours.
        assertEquals(160, HOME_HEARTBEAT_GREEN_MAX_MS)
        assertEquals(MarbleMetricBand.GOOD, pingMetricBand(159))
        assertEquals(MarbleMetricBand.WARNING, pingMetricBand(160))
    }

    @Test
    fun jitterBandsKeepUnknownNeutral() {
        assertEquals(MarbleMetricBand.UNKNOWN, jitterMetricBand(0,0))
        assertEquals(MarbleMetricBand.GOOD, jitterMetricBand(19,2))
        assertEquals(MarbleMetricBand.WARNING, jitterMetricBand(20,2))
        assertEquals(MarbleMetricBand.WARNING, jitterMetricBand(50,2))
        assertEquals(MarbleMetricBand.POOR, jitterMetricBand(51,2))
    }

    @Test
    fun qualityBandsAreMonotonic() {
        assertEquals(MarbleMetricBand.UNKNOWN, qualityMetricBand(-1))
        assertEquals(MarbleMetricBand.POOR, qualityMetricBand(59))
        assertEquals(MarbleMetricBand.WARNING, qualityMetricBand(60))
        assertEquals(MarbleMetricBand.GOOD, qualityMetricBand(80))
        assertEquals(MarbleMetricBand.GOOD, qualityMetricBand(100))
    }

    @Test
    fun leadingCountryFlagMovesIntoAvatarWithoutLosingName() {
        assertEquals("🇫🇷",leadingFlagGlyph("🇫🇷 France"))
        assertEquals("France",stripLeadingFlag("🇫🇷 France"))
        assertEquals("Emirates 1",stripLeadingFlag("🇦🇪 Emirates 1"))
    }

    @Test
    fun ordinaryNamesArePreserved() {
        assertNull(leadingFlagGlyph("Fast node"))
        assertEquals("Fast node",stripLeadingFlag("Fast node"))
    }

    @Test
    fun amoledHomeBackdropUsesOnlyBlackBasePixels() {
        assertEquals(Color.Black, HomeCloud.DarkBgTop)
        assertEquals(Color.Black, HomeCloud.DarkBgBottom)
    }

    @Test
    fun persianFallbackNeverLeaksAnUntranslatedLatinUiLabel() {
        assertEquals("سرورهای ناموفق هوشمند حذف شوند؟", faTranslate("Remove failed Smart servers?"))
        val translated = faTranslate("Fresh diagnostic panel")
        assertFalse("Persian fallback leaked Latin text: $translated", translated.any { it in 'A'..'Z' || it in 'a'..'z' })
    }
}
