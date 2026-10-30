package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CRYSTAL_LENS_V248 — the arithmetic of the rebuilt bottom bar, pinned on the JVM.
 *
 * Every number the composable draws with lives in [LiquidGlassLensSpec]; this test is what keeps a
 * screenshot from being the only witness. The two claims that matter most are the geometry (68dp
 * capsule, 52dp lens, 8dp of air) and the clamp (the lens may never leave the capsule, and every tab
 * anchor is still reachable).
 */
class LiquidGlassLensV248Test {
    private val width = 1000f
    private val padding = LiquidGlassLensSpec.INNER_PADDING_DP

    @Test
    fun theCapsuleLeavesTheBriefsAirAroundTheLens() {
        assertEquals(68f, LiquidGlassLensSpec.BAR_HEIGHT_DP, 1e-6f)
        assertEquals(72f, LiquidGlassLensSpec.BAR_HEIGHT_TALL_DP, 1e-6f)
        assertEquals(52f, LiquidGlassLensSpec.LENS_DIAMETER_DP, 1e-6f)
        assertEquals(26f, LiquidGlassLensSpec.LENS_RADIUS_DP, 1e-6f)

        // 68 − 52 = 16 → 8dp above and 8dp below; 72 − 52 = 20 → 10dp.
        assertEquals(8f, LiquidGlassLensSpec.breathingRoomDp(68f), 1e-6f)
        assertEquals(10f, LiquidGlassLensSpec.breathingRoomDp(72f), 1e-6f)
        assertTrue(LiquidGlassLensSpec.breathingRoomDp(68f) >= 8f)
        assertTrue(LiquidGlassLensSpec.breathingRoomDp(72f) <= 10f)
    }

    @Test
    fun theSizePreferenceChoosesOneOfTheTwoSanctionedHeights() {
        assertEquals(68, LiquidGlassLensSpec.barHeightDp(DockSize.SMALL))
        assertEquals(68, LiquidGlassLensSpec.barHeightDp(DockSize.MEDIUM))
        assertEquals(72, LiquidGlassLensSpec.barHeightDp(DockSize.LARGE))
    }

    @Test
    fun theOnlyTwoProjectionScalesAreInsideTheBriefsBand() {
        assertTrue(LiquidGlassLensSpec.MAGNIFICATION >= LiquidGlassLensSpec.MIN_MAGNIFICATION)
        assertTrue(LiquidGlassLensSpec.MAGNIFICATION <= LiquidGlassLensSpec.MAX_MAGNIFICATION)
        assertEquals(1.3f, LiquidGlassLensSpec.MAGNIFICATION, 1e-6f)
        assertEquals(1.28f, LiquidGlassLensSpec.MIN_MAGNIFICATION, 1e-6f)
        assertEquals(1.32f, LiquidGlassLensSpec.MAX_MAGNIFICATION, 1e-6f)
    }

    @Test
    fun theApertureCannotLeaveTheCapsule() {
        val lensWidth = LiquidGlassLensSpec.LENS_DIAMETER_DP
        // minX = paddingStart + lensWidth / 2 = 6 + 26 = 32
        // maxX = width − paddingEnd − lensWidth / 2 = 1000 − 6 − 26 = 968
        assertEquals(32f, LiquidGlassLensSpec.minLensCenterX(padding, lensWidth), 1e-4f)
        assertEquals(968f, LiquidGlassLensSpec.maxLensCenterX(width, padding, lensWidth), 1e-4f)

        assertEquals(
            32f,
            LiquidGlassLensSpec.clampLensCenterX(-9000f, width, padding, padding, lensWidth),
            1e-4f
        )
        assertEquals(
            968f,
            LiquidGlassLensSpec.clampLensCenterX(9000f, width, padding, padding, lensWidth),
            1e-4f
        )
        assertEquals(
            500f,
            LiquidGlassLensSpec.clampLensCenterX(500f, width, padding, padding, lensWidth),
            1e-4f
        )
    }

    @Test
    fun aClampedCentreStillReachesEveryTabAnchor() {
        val lensWidth = LiquidGlassLensSpec.LENS_DIAMETER_DP
        val subPixel = 1e-3f
        for (tabs in 1..6) {
            val item = LiquidGlassLensSpec.itemWidthPx(width, padding, padding, tabs)
            val min = LiquidGlassLensSpec.minLensCenterX(padding, lensWidth)
            val max = LiquidGlassLensSpec.maxLensCenterX(width, padding, lensWidth)
            for (index in 0 until tabs) {
                val anchor = LiquidGlassLensSpec.tabCenterX(index, item, width, padding)
                assertEquals(
                    anchor,
                    LiquidGlassLensSpec.clampLensCenterX(anchor, width, padding, padding, lensWidth),
                    subPixel
                )
                assertTrue("anchor $anchor outside [$min, $max]", anchor >= min - subPixel)
                assertTrue("anchor $anchor outside [$min, $max]", anchor <= max + subPixel)
                // Whatever the float noise, the clamped centre is always legal.
                val seated =
                    LiquidGlassLensSpec.clampLensCenterX(anchor, width, padding, padding, lensWidth)
                assertTrue(seated >= min && seated <= max)
            }
        }
    }

    @Test
    fun aCapsuleNarrowerThanTheLensCollapsesToTheMiddle() {
        val lensWidth = LiquidGlassLensSpec.LENS_DIAMETER_DP
        // 60 − 6 − 26 = 28 < 32: the bounds invert, so the centre wins.
        assertEquals(
            30f,
            LiquidGlassLensSpec.clampLensCenterX(0f, 60f, 6f, 6f, lensWidth),
            1e-4f
        )
        assertEquals(
            30f,
            LiquidGlassLensSpec.clampLensCenterX(999f, 60f, 6f, 6f, lensWidth),
            1e-4f
        )
    }

    @Test
    fun theLensKnowsWhichTabItIsOver() {
        val item = 250f
        assertEquals(0, LiquidGlassLensSpec.indexAt(0f, item, 4, width, 0f))
        assertEquals(0, LiquidGlassLensSpec.indexAt(249.9f, item, 4, width, 0f))
        assertEquals(1, LiquidGlassLensSpec.indexAt(250f, item, 4, width, 0f))
        assertEquals(2, LiquidGlassLensSpec.indexAt(500f, item, 4, width, 0f))
        assertEquals(3, LiquidGlassLensSpec.indexAt(999f, item, 4, width, 0f))
        assertEquals(0, LiquidGlassLensSpec.indexAt(500f, item, 0, width, 0f))
    }

    @Test
    fun aRightToLeftRowMirrorsTheAnchorsAndTheHitTest() {
        val item = LiquidGlassLensSpec.itemWidthPx(width, padding, padding, 4)
        // Index 0 sits at the right edge, index 3 at the left one.
        assertEquals(
            1000f - (padding + item * 0.5f),
            LiquidGlassLensSpec.tabCenterX(0, item, width, padding, rtl = true),
            1e-3f
        )
        assertEquals(
            1000f - (padding + item * 3.5f),
            LiquidGlassLensSpec.tabCenterX(3, item, width, padding, rtl = true),
            1e-3f
        )
        for (index in 0 until 4) {
            val anchor = LiquidGlassLensSpec.tabCenterX(index, item, width, padding, rtl = true)
            assertEquals(
                index,
                LiquidGlassLensSpec.indexAt(anchor, item, 4, width, padding, rtl = true)
            )
        }
    }

    @Test
    fun theSlotWidthIsTheUsableWidthDividedByTheTabs() {
        assertEquals(0f, LiquidGlassLensSpec.itemWidthPx(1000f, 6f, 6f, 0), 1e-6f)
        assertEquals(247f, LiquidGlassLensSpec.itemWidthPx(1000f, 6f, 6f, 4), 1e-4f)
        // A capsule narrower than its own padding has no slots, not negative ones.
        assertEquals(0f, LiquidGlassLensSpec.itemWidthPx(10f, 6f, 6f, 4), 1e-6f)
    }

    @Test
    fun theDragStretchesAlongTheRowAndSquashesAgainstIt() {
        val rest = LiquidGlassLensSpec.deformation(0f)
        assertEquals(1f, rest.scaleX, 1e-6f)
        assertEquals(1f, rest.scaleY, 1e-6f)

        // Below the caps the effect is proportional: 1 + 300/3000 = 1.10 and
        // 1 − 300/6000 = 0.95.
        val slow = LiquidGlassLensSpec.deformation(300f)
        assertEquals(1.10f, slow.scaleX, 1e-6f)
        assertEquals(0.95f, slow.scaleY, 1e-6f)

        // The caps are reached at 3000 * 0.25 = 750 px/s (stretch) and 6000 * 0.12 = 720 px/s
        // (squash), so anything faster is already the maximum deformation. This is exactly what
        // the brief's `coerceAtMost` means, and it is why 1500 and 3000 produce the same puck.
        val half = LiquidGlassLensSpec.deformation(1500f)
        assertEquals(1.25f, half.scaleX, 1e-6f)
        assertEquals(0.88f, half.scaleY, 1e-6f)

        val thrown = LiquidGlassLensSpec.deformation(3000f)
        assertEquals(1.25f, thrown.scaleX, 1e-6f)
        assertEquals(0.88f, thrown.scaleY, 1e-6f)

        // Direction is irrelevant; only speed is.
        val mirrored = LiquidGlassLensSpec.deformation(-3000f)
        assertEquals(thrown.scaleX, mirrored.scaleX, 1e-6f)
        assertEquals(thrown.scaleY, mirrored.scaleY, 1e-6f)

        // Both caps hold beyond their windows, and the squash is always gentler.
        val veryFast = LiquidGlassLensSpec.deformation(50_000f)
        assertEquals(1f + LiquidGlassLensSpec.MAX_STRETCH, veryFast.scaleX, 1e-6f)
        assertEquals(1f - LiquidGlassLensSpec.MAX_SQUASH, veryFast.scaleY, 1e-6f)
        assertTrue(LiquidGlassLensSpec.MAX_SQUASH < LiquidGlassLensSpec.MAX_STRETCH)
    }

    @Test
    fun theSpringIsTheOneTheBriefAskedFor() {
        // spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
        assertEquals(0.65f, LiquidGlassLensSpec.SNAP_DAMPING_RATIO, 1e-6f)
        assertEquals(400f, LiquidGlassLensSpec.SNAP_STIFFNESS, 1e-6f)
    }

    @Test
    fun theGlassValuesAreTheOnesTheBriefNamed() {
        assertEquals(0.88f, LiquidGlassLensSpec.CAPSULE_ALPHA, 1e-6f)
        assertEquals(20, LiquidGlassLensSpec.CAPSULE_SHADOW_DP)
        assertEquals(6f, LiquidGlassLensSpec.CAPSULE_SHADOW_OFFSET_Y_DP, 1e-6f)
        assertEquals(0.07f, LiquidGlassLensSpec.CAPSULE_SHADOW_ALPHA, 1e-6f)
        assertEquals(1f, LiquidGlassLensSpec.CAPSULE_RIM_WIDTH_DP, 1e-6f)
        assertEquals(0.6f, LiquidGlassLensSpec.CAPSULE_RIM_TOP_ALPHA, 1e-6f)
        assertEquals(0.15f, LiquidGlassLensSpec.CAPSULE_RIM_BOTTOM_ALPHA, 1e-6f)

        // The lens must be translucent crystal, not a white disc.
        assertEquals(0.12f, LiquidGlassLensSpec.LENS_CORE_ALPHA, 1e-6f)
        assertEquals(1.5f, LiquidGlassLensSpec.LENS_RIM_WIDTH_DP, 1e-6f)
        assertEquals(0.95f, LiquidGlassLensSpec.LENS_RIM_TOP_ALPHA, 1e-6f)
        assertEquals(0.20f, LiquidGlassLensSpec.LENS_RIM_MID_ALPHA, 1e-6f)
        assertEquals(0.50f, LiquidGlassLensSpec.LENS_RIM_BOTTOM_ALPHA, 1e-6f)
        assertEquals(0.25f, LiquidGlassLensSpec.LENS_CAUSTIC_ALPHA, 1e-6f)
        assertEquals(8, LiquidGlassLensSpec.DROPLET_SHADOW_DP)
        assertEquals(3f, LiquidGlassLensSpec.DROPLET_SHADOW_OFFSET_Y_DP, 1e-6f)
        assertEquals(0.14f, LiquidGlassLensSpec.DROPLET_SHADOW_ALPHA, 1e-6f)
    }

    @Test
    fun theTabContentIsTheSizeTheBriefNamed() {
        assertEquals(22f, LiquidGlassLensSpec.ICON_SIZE_DP, 1e-6f)
        assertEquals(11f, LiquidGlassLensSpec.LABEL_FONT_SP, 1e-6f)
        assertEquals(0xFF5F6368L, LiquidGlassLensSpec.INACTIVE_CONTENT_ARGB)
        assertEquals(0xFF1E5E3AL, LiquidGlassLensSpec.ACTIVE_CONTENT_ARGB)
        assertEquals("MARBLE_CRYSTAL_LENS_V248", LiquidGlassLensSpec.MARKER)
    }

    @Test
    fun theCounterIsCappedAtNine() {
        assertEquals("", LiquidGlassLensSpec.badgeText(0))
        assertEquals("", LiquidGlassLensSpec.badgeText(-4))
        assertEquals("1", LiquidGlassLensSpec.badgeText(1))
        assertEquals("9", LiquidGlassLensSpec.badgeText(9))
        assertEquals("9+", LiquidGlassLensSpec.badgeText(10))
        assertEquals("9+", LiquidGlassLensSpec.badgeText(999))
        assertEquals(9, LiquidGlassLensSpec.BADGE_MAX_COUNT)
        assertNotEquals(LiquidGlassLensSpec.badgeText(1), LiquidGlassLensSpec.badgeText(2))
    }
}
