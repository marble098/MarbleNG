package com.marbleng.app.ui

import com.marbleng.app.model.AppBehaviorPrefs
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.marbleFallbackDarkScheme
import com.marbleng.app.ui.theme.marbleFallbackLightScheme
import com.marbleng.app.ui.theme.marbleStatusColors
import com.marbleng.app.ui.theme.resolveMarbleDarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_M3_EXPRESSIVE_V240 — pins for the expressive design system: geometry tokens,
 * the dark-mode rule, the accent-seeded fallback schemes and the semantic status ramps.
 * The shared UI formatters and the settings-hub coverage pins live here too (unchanged).
 */
class MarbleExpressiveThemeTest {
    @Test
    fun expressiveGeometryTokensMatchSpec() {
        assertEquals(20, MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP)
        assertEquals(20, MarbleSpec.SECTION_SPACING_DP)
        assertEquals(16, MarbleSpec.CARD_PADDING_DP)
        assertEquals(20, MarbleSpec.CARD_GENEROUS_PADDING_DP)
        assertEquals(40, MarbleSpec.ROW_ICON_SIZE_DP)
        assertEquals(14, MarbleSpec.ROW_ICON_CORNER_DP)
        assertEquals(1, MarbleSpec.DIVIDER_THICKNESS_DP)
        assertEquals(600, MarbleSpec.WIDE_RAIL_MIN_WIDTH_DP)
    }

    @Test
    fun touchTargetsMeetThe48dpFloor() {
        // M3 Expressive (and the research) holds the 48dp accessibility floor; the pins
        // below fail the build before a smaller target can ship.
        assertTrue(MarbleSpec.ROW_MIN_HEIGHT_DP >= 48)
        assertEquals(48, MarbleSpec.HEADER_ACTION_SIZE_DP)
        assertTrue(MarbleSpec.HERO_BUTTON_HEIGHT_DP >= 48)
    }

    @Test
    fun darkModeResolutionFollowsThemeAndSystem() {
        assertFalse(resolveMarbleDarkMode("light", systemDark = true))
        assertFalse(resolveMarbleDarkMode("light", systemDark = false))
        assertTrue(resolveMarbleDarkMode("dark", systemDark = false))
        assertTrue(resolveMarbleDarkMode("amoled", systemDark = false))
        assertTrue(resolveMarbleDarkMode("system", systemDark = true))
        assertFalse(resolveMarbleDarkMode("system", systemDark = false))
        assertTrue(resolveMarbleDarkMode("auto", systemDark = true))
        assertFalse(resolveMarbleDarkMode("auto", systemDark = false))
    }

    @Test
    fun fallbackSchemesSeparateLightFromDark() {
        val light = marbleFallbackLightScheme(FlatAccent.OCEAN)
        val dark = marbleFallbackDarkScheme(FlatAccent.OCEAN)
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.background, dark.background)
        assertNotEquals(light.primary, dark.primary)
        assertNotEquals(light.surface, light.surfaceContainer)
        assertNotEquals(dark.surface, dark.surfaceContainer)
    }

    @Test
    fun everyAccentSeedsADistinctOpaquePrimary() {
        val lightPrimaries = FlatAccent.entries.map { accent ->
            val scheme = marbleFallbackLightScheme(accent)
            assertEquals(1f, scheme.primary.alpha, 0.001f)
            assertEquals(1f, scheme.onPrimary.alpha, 0.001f)
            assertNotEquals(scheme.primary, scheme.onPrimary)
            assertNotEquals(scheme.primary, scheme.primaryContainer)
            assertNotEquals(scheme.primaryContainer, scheme.onPrimaryContainer)
            scheme.primary
        }
        assertEquals(FlatAccent.entries.size, lightPrimaries.toSet().size)

        val darkPrimaries = FlatAccent.entries.map { accent ->
            val scheme = marbleFallbackDarkScheme(accent)
            assertEquals(1f, scheme.primary.alpha, 0.001f)
            assertNotEquals(scheme.primary, scheme.onPrimary)
            scheme.primary
        }
        assertEquals(FlatAccent.entries.size, darkPrimaries.toSet().size)
    }

    @Test
    fun statusRampsAreOpaqueAndModeAware() {
        val light = marbleStatusColors(dark = false)
        val dark = marbleStatusColors(dark = true)
        listOf(light.success, light.successContainer, light.warning, light.warningContainer).forEach {
            assertEquals(1f, it.alpha, 0.001f)
        }
        listOf(dark.success, dark.successContainer, dark.warning, dark.warningContainer).forEach {
            assertEquals(1f, it.alpha, 0.001f)
        }
        assertNotEquals(light.success, dark.success)
        assertNotEquals(light.warning, dark.warning)
        assertNotEquals(light.success, light.successContainer)
        assertNotEquals(light.warning, light.warningContainer)
    }

    @Test
    fun dynamicColorIsOnByDefault() {
        assertTrue(AppBehaviorPrefs().dynamicColor)
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
