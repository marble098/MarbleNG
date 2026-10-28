package com.marbleng.app.ui

import androidx.compose.ui.graphics.Color
import com.marbleng.app.model.FlatAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IosDesignSystemTest {
    @Test
    fun iosDesignTokensMatchSpec() {
        assertEquals(0xFFF8F9FAL, IosSpec.BACKGROUND_HEX)
        assertEquals(0xFFFFFFFFL, IosSpec.CARD_HEX)
        assertEquals(0xFFFFFFFFL, IosSpec.SQUIRCLE_GLYPH_HEX)
        assertEquals(20, IosSpec.CARD_CORNER_DP)
        assertEquals(12, IosSpec.SQUIRCLE_CORNER_DP)
        assertEquals(1, IosSpec.DIVIDER_THICKNESS_DP)
        assertTrue(IosSpec.SCREEN_HORIZONTAL_PADDING_DP in 16..24)
        assertTrue(IosSpec.SECTION_SPACING_DP in 16..24)
    }

    @Test
    fun lightPaletteUsesLightGrayCanvasAndPureWhiteCards() {
        val light = lightIosPalette(FlatAccent.OCEAN)
        assertFalse(light.dark)
        assertEquals(Color(0xFFF8F9FA), light.background)
        assertEquals(Color(0xFFFFFFFF), light.card)
        assertEquals(Color(0xFF007AFF), light.accent)
        assertNotEquals(light.background, light.card)
    }

    @Test
    fun darkPaletteAndModeResolutionRemainConsistent() {
        val dark = darkIosPalette(FlatAccent.VIOLET)
        assertTrue(dark.dark)
        assertNotEquals(dark.background, dark.card)

        assertFalse(resolveIosDarkMode("light", systemDark = true))
        assertFalse(resolveIosDarkMode("light", systemDark = false))
        assertTrue(resolveIosDarkMode("dark", systemDark = false))
        assertTrue(resolveIosDarkMode("system", systemDark = true))
        assertFalse(resolveIosDarkMode("system", systemDark = false))
    }

    @Test
    fun everyAccentMapsToDistinctNonTransparentColor() {
        FlatAccent.entries.forEach { accent ->
            val light = iosAccentColor(accent, dark = false)
            val dark = iosAccentColor(accent, dark = true)
            assertEquals(1f, light.alpha, 0.001f)
            assertEquals(1f, dark.alpha, 0.001f)
        }
    }

    @Test
    fun uiFormattersFormatTelemetryCleanly() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB/s", formatRate(1_572_864))
        assertEquals("00:05:09", formatUptimeSeconds(309))
        assertEquals("01:02:03", formatUptimeSeconds(3723))
        assertEquals("Frankfurt Reality", cleanServerName("🇩🇪 Frankfurt Reality"))
        assertEquals("Just now", relativeTimeLabel(timestampMs = 99_000L, nowMs = 100_000L))
        assertEquals("5m ago", relativeTimeLabel(timestampMs = 100_000L, nowMs = 400_000L))
    }

    @Test
    fun settingsHubCoversAllTwentyOneDestinations() {
        assertEquals(22, SettingsDestination.entries.size)
    }
}
