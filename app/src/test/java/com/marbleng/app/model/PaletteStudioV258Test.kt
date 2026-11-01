package com.marbleng.app.model

import com.marbleng.app.ui.theme.marblePaletteScheme
import com.marbleng.app.ui.theme.marblePaletteSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PALETTE_STUDIO_V258 — three whole-scheme colour themes, pinned on the host JVM.
 *
 * The point of a palette is that it is *not* another accent: it replaces primary, secondary,
 * tertiary, every container tone and the surface ladder, in both modes. These tests are the three
 * ways that promise can silently break — a palette that resolves to Classic, two palettes that are
 * the same scheme with a different name, and a palette whose dark twin is its light twin.
 */
class PaletteStudioV258Test {

    @Test
    fun everyPaletteIdSurvivesAStoredPreferenceAndAnUnknownOneFallsBackToClassic() {
        MarblePalette.entries.forEach { palette ->
            assertEquals(palette, MarblePalette.fromId(palette.id))
            assertEquals(palette, MarblePalette.fromId("  ${palette.id.uppercase()}  "))
            assertEquals(palette, parseMarblePalette(palette.id))
        }
        assertEquals(MarblePalette.CLASSIC, MarblePalette.fromId(null))
        assertEquals(MarblePalette.CLASSIC, MarblePalette.fromId(""))
        assertEquals(MarblePalette.CLASSIC, MarblePalette.fromId("not-a-palette"))
        assertEquals(MarblePalette.AURORA, MarblePalette.fromId("teal"))
        assertEquals(MarblePalette.EMBER, MarblePalette.fromId("sunset"))
        assertEquals(MarblePalette.PRISM, MarblePalette.fromId("neon"))
        assertEquals(MarblePalette.DEFAULT, MarblePalette.CLASSIC)
    }

    @Test
    fun classicIsTheAccentSeededSchemeAndTheOtherThreeAreNot() {
        // Classic means "the accent you picked on the neutral ladder", i.e. nothing changed.
        // `ColorScheme` has no value equality, so the roles are compared one by one.
        val seeded = com.marbleng.app.ui.theme.marbleFallbackLightScheme(FlatAccent.OCEAN)
        val seededDark = com.marbleng.app.ui.theme.marbleFallbackDarkScheme(FlatAccent.OCEAN)
        val classic = marblePaletteScheme(MarblePalette.CLASSIC, dark = false)
        val classicDark = marblePaletteScheme(MarblePalette.CLASSIC, dark = true)
        assertEquals(seeded.primary, classic.primary)
        assertEquals(seeded.primaryContainer, classic.primaryContainer)
        assertEquals(seeded.surface, classic.surface)
        assertEquals(seededDark.primary, classicDark.primary)
        assertEquals(seededDark.surface, classicDark.surface)

        assertTrue(MarblePalette.brokenOut.isNotEmpty())
        MarblePalette.brokenOut.forEach { palette ->
            val light = marblePaletteScheme(palette, dark = false)
            val dark = marblePaletteScheme(palette, dark = true)
            assertNotEquals(
                "Classic and ${palette.id} share a primary",
                seeded.primary,
                light.primary
            )
            assertNotEquals(
                "Classic and ${palette.id} share a surface ladder",
                seeded.surfaceContainer,
                light.surfaceContainer
            )
            assertNotEquals(
                "Classic and ${palette.id} share a dark primary",
                seededDark.primary,
                dark.primary
            )
        }
    }

    @Test
    fun atLeastThreePalettesExistAndNoTwoPaintTheSamePrimary() {
        assertTrue(MarblePalette.brokenOut.size >= 3)
        val lightPrimaries = MarblePalette.brokenOut.map { marblePaletteScheme(it, dark = false).primary }
        assertEquals(lightPrimaries.size, lightPrimaries.toSet().size)
        val darkPrimaries = MarblePalette.brokenOut.map { marblePaletteScheme(it, dark = true).primary }
        assertEquals(darkPrimaries.size, darkPrimaries.toSet().size)
    }

    @Test
    fun aPaletteOwnsItsWholeExpressiveRampNotJustThePrimary() {
        MarblePalette.brokenOut.forEach { palette ->
            val light = marblePaletteScheme(palette, dark = false)
            val dark = marblePaletteScheme(palette, dark = true)

            // The three expressive ramps are three different colours, in both modes.
            listOf(light, dark).forEach { scheme ->
                assertEquals(1f, scheme.primary.alpha, 0.001f)
                assertEquals(1f, scheme.secondary.alpha, 0.001f)
                assertEquals(1f, scheme.tertiary.alpha, 0.001f)
                assertNotEquals(scheme.primary, scheme.secondary)
                assertNotEquals(scheme.secondary, scheme.tertiary)
                assertNotEquals(scheme.primary, scheme.primaryContainer)
                assertNotEquals(scheme.primaryContainer, scheme.onPrimaryContainer)
                // The surface ladder survives the repaint: a card still reads as a card.
                assertNotEquals(scheme.surface, scheme.surfaceContainer)
                assertNotEquals(scheme.surfaceContainer, scheme.surfaceContainerHigh)
                assertNotEquals(scheme.onSurface, scheme.onSurfaceVariant)
            }

            // A palette is not a light theme with a dark switch: the dark twin is re-lit.
            assertNotEquals(light.surface, dark.surface)
            assertNotEquals(light.primary, dark.primary)
            assertNotEquals(light.secondary, dark.secondary)
            assertNotEquals(light.tertiary, dark.tertiary)
        }
    }

    @Test
    fun everyPaletteHasANameAHintAndAFourToneSignature() {
        MarblePalette.entries.forEach { palette ->
            assertTrue(palette.id.isNotBlank())
            assertTrue(palette.label.isNotBlank())
            assertTrue(palette.hint.isNotBlank())
            listOf(true, false).forEach { dark ->
                val signature = marblePaletteSignature(palette, dark)
                assertEquals(4, signature.size)
                assertEquals(4, signature.toSet().size)
                signature.forEach { assertEquals(1f, it.alpha, 0.001f) }
            }
        }
    }

    @Test
    fun thePaletteIsStoredAsOneGroupedBehaviourAxis() {
        // MARBLE_PALETTE_STUDIO_V258 rides in AppBehaviorPrefs, because AppSettings' own
        // constructor is already at the JVM's 255-slot method limit.
        assertEquals(MarblePalette.CLASSIC.id, AppBehaviorPrefs().themePalette)
        assertEquals(MarblePalette.CLASSIC.id, AppSettings().themePalette)
        assertEquals(
            MarblePalette.PRISM.id,
            AppSettings(appBehavior = AppBehaviorPrefs(themePalette = MarblePalette.PRISM.id)).themePalette
        )
    }

    @Test
    fun theDockGapShipsLargerThanTheOldConstantAndIsAStoredSetting() {
        // MARBLE_FLOATING_DOCK_V258 — the capsule visibly floats now, and the number is the user's.
        assertTrue(DockStudio.DEFAULT_BOTTOM_GAP_DP > 16)
        assertEquals(DockStudio.DEFAULT_BOTTOM_GAP_DP, AppSettings().dockBottomGapDp)
        assertEquals(
            DockStudio.MAX_BOTTOM_GAP_DP,
            AppSettings(dockBottomGapDp = 5_000).dockBottomGapDp.let(DockStudio::bottomGapDp)
        )
        // The attached presentation is gone: every stored value reads back as floating.
        DockSize.entries.forEach { size ->
            val encoded = encodeDockPreference(size, DockPresentation.FLOATING)
            assertEquals(size, parseDockSize(encoded))
            assertEquals(DockPresentation.FLOATING, parseDockPresentation(encoded))
        }
    }
}
