package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LIQUID_GLASS_LENS_V247 — the arithmetic of the magnifier bar, on the JVM.
 *
 * Every number the composable draws with is decided here first: where the aperture may travel,
 * which tab it is over, how much the projection magnifies at a given distance, and how the puck
 * deforms at a given velocity. A screenshot cannot prove any of it, and a 120 Hz panel cannot be
 * asked politely, so the model is what the test owns.
 */
class LiquidGlassLensV247Test {

    private val width = 1000f
    private val count = 4

    @Test
    fun aSlotIsAnEqualShareOfTheContentBox() {
        assertEquals(250f, LiquidGlassLensSpec.itemWidthPx(width, count), 1e-4f)
        assertEquals(1000f, LiquidGlassLensSpec.itemWidthPx(width, 0), 1e-4f)
        assertEquals(1000f, LiquidGlassLensSpec.itemWidthPx(width, -3), 1e-4f)
    }

    @Test
    fun theLensIsClampedInsideTheCapsuleAtBothEnds() {
        val item = LiquidGlassLensSpec.itemWidthPx(width, count)
        assertEquals(125f, LiquidGlassLensSpec.minLensCenterX(item), 1e-4f)
        assertEquals(875f, LiquidGlassLensSpec.maxLensCenterX(width, item), 1e-4f)

        assertEquals(125f, LiquidGlassLensSpec.clampLensCenterX(-9000f, width, item), 1e-4f)
        assertEquals(875f, LiquidGlassLensSpec.clampLensCenterX(9000f, width, item), 1e-4f)
        assertEquals(500f, LiquidGlassLensSpec.clampLensCenterX(500f, width, item), 1e-4f)
    }

    @Test
    fun aClampedCentreStillReachesEveryTabAnchor() {
        for (tabs in 1..6) {
            val item = LiquidGlassLensSpec.itemWidthPx(width, tabs)
            val min = LiquidGlassLensSpec.minLensCenterX(item)
            val max = LiquidGlassLensSpec.maxLensCenterX(width, item)
            for (index in 0 until tabs) {
                val anchor = LiquidGlassLensSpec.tabCenterX(index, item, width)
                assertEquals(anchor, LiquidGlassLensSpec.clampLensCenterX(anchor, width, item), 1e-4f)
                assertTrue("anchor $anchor outside [$min, $max]", anchor >= min && anchor <= max)
            }
        }
    }

    @Test
    fun aContainerNarrowerThanOneSlotCollapsesToTheMiddle() {
        val item = LiquidGlassLensSpec.itemWidthPx(100f, 1)
        assertEquals(50f, LiquidGlassLensSpec.clampLensCenterX(0f, 100f, item), 1e-4f)
        assertEquals(50f, LiquidGlassLensSpec.clampLensCenterX(999f, 100f, item), 1e-4f)
    }

    @Test
    fun theLensKnowsWhichTabItIsOver() {
        val item = LiquidGlassLensSpec.itemWidthPx(width, count)
        assertEquals(0, LiquidGlassLensSpec.indexAt(0f, item, count, width))
        assertEquals(0, LiquidGlassLensSpec.indexAt(249.9f, item, count, width))
        assertEquals(1, LiquidGlassLensSpec.indexAt(250f, item, count, width))
        assertEquals(2, LiquidGlassLensSpec.indexAt(500f, item, count, width))
        assertEquals(3, LiquidGlassLensSpec.indexAt(999f, item, count, width))
        assertEquals(0, LiquidGlassLensSpec.indexAt(5000f, item, 0, width))
    }

    @Test
    fun aRightToLeftRowMirrorsTheTabAnchorsAndTheHitTest() {
        val item = LiquidGlassLensSpec.itemWidthPx(width, count)
        assertEquals(875f, LiquidGlassLensSpec.tabCenterX(0, item, width, rtl = true), 1e-4f)
        assertEquals(125f, LiquidGlassLensSpec.tabCenterX(3, item, width, rtl = true), 1e-4f)
        assertEquals(0, LiquidGlassLensSpec.indexAt(875f, item, count, width, rtl = true))
        assertEquals(3, LiquidGlassLensSpec.indexAt(125f, item, count, width, rtl = true))
        // A left-to-right and a right-to-left bar agree about which tab a mirrored anchor names.
        for (index in 0 until count) {
            val anchor = LiquidGlassLensSpec.tabCenterX(index, item, width, rtl = true)
            assertEquals(index, LiquidGlassLensSpec.indexAt(anchor, item, count, width, rtl = true))
        }
    }

    @Test
    fun theDiscFitsTheCapsuleWithAirAtBothEnds() {
        val barHeight = 60f * 3f
        val inset = 5f * 3f
        val radius = LiquidGlassLensSpec.lensRadiusPx(barHeight, inset)
        assertEquals(barHeight / 2f - inset, radius, 1e-4f)
        assertTrue(radius * 2f <= barHeight)
        // A degenerate capsule still yields a drawable disc rather than a negative radius.
        assertEquals(1f, LiquidGlassLensSpec.lensRadiusPx(4f, 40f), 1e-4f)
    }

    @Test
    fun theMagnificationIsInsideTheRequestedBandUnderTheGlass() {
        val radius = 75f
        val centre = LiquidGlassLensSpec.magnificationAt(0f, radius)
        assertEquals(1f + LiquidGlassLensSpec.LENS_MAGNIFICATION, centre, 1e-4f)
        assertTrue(
            "centre magnification $centre",
            centre >= LiquidGlassLensSpec.MIN_MAGNIFICATION && centre <= LiquidGlassLensSpec.MAX_MAGNIFICATION
        )
    }

    @Test
    fun theMagnificationFallsToUnityAtTheRimAndNeverShrinksTheRow() {
        val radius = 75f
        assertEquals(1f, LiquidGlassLensSpec.magnificationAt(radius, radius), 1e-4f)
        assertEquals(1f, LiquidGlassLensSpec.magnificationAt(radius * 4f, radius), 1e-4f)
        assertEquals(1f, LiquidGlassLensSpec.magnificationAt(-radius * 2f, radius), 1e-4f)
        // Continuity: the projection is monotone from the anchor to the rim, so the overlay and
        // the base layer meet without a visible seam.
        var previous = LiquidGlassLensSpec.magnificationAt(0f, radius)
        for (step in 1..30) {
            val next = LiquidGlassLensSpec.magnificationAt(radius * step / 30f, radius)
            assertTrue("magnification grew at step $step", next <= previous + 1e-5f)
            previous = next
        }
        assertEquals(1f, LiquidGlassLensSpec.magnificationAt(0f, 0f), 1e-4f)
    }

    @Test
    fun theHalfRadiusScaleIsTheQuadraticallySoftenedOne() {
        val radius = 100f
        // (1 - 0.5)^2 = 0.25 -> 1 + 0.36 * 0.25
        assertEquals(1.09f, LiquidGlassLensSpec.magnificationAt(50f, radius), 1e-4f)
    }

    @Test
    fun theLabelFollowsTheGlassAtAThirdOfTheGain() {
        assertEquals(1f, LiquidGlassLensSpec.labelMagnification(1f), 1e-4f)
        // 1 + 0.36 * 0.35 = 1.126: the word grows, but it stays type.
        assertEquals(1.126f, LiquidGlassLensSpec.labelMagnification(1.36f), 1e-3f)
        assertTrue(
            LiquidGlassLensSpec.labelMagnification(1.36f) < LiquidGlassLensSpec.magnificationAt(0f, 75f)
        )
        assertEquals(0.35f, LiquidGlassLensSpec.LABEL_MAGNIFICATION_RATIO, 1e-4f)
    }

    @Test
    fun theDistanceForAGivenScaleInvertsTheProfile() {
        val radius = 80f
        val distance = LiquidGlassLensSpec.distanceForMagnification(
            LiquidGlassLensSpec.magnificationAt(32f, radius),
            radius
        )
        assertEquals(32f, distance, 1e-3f)
        assertEquals(0f, LiquidGlassLensSpec.distanceForMagnification(5f, radius), 1e-4f)
        assertEquals(radius, LiquidGlassLensSpec.distanceForMagnification(1f, radius), 1e-4f)
    }

    @Test
    fun theDragStretchesAlongTheRowAndSquashesAgainstIt() {
        val rest = LiquidGlassLensSpec.deformation(0f)
        assertEquals(1f, rest.scaleX, 1e-4f)
        assertEquals(1f, rest.scaleY, 1e-4f)

        val thrown = LiquidGlassLensSpec.deformation(3000f)
        assertEquals(1f + LiquidGlassLensSpec.MAX_STRETCH, thrown.scaleX, 1e-4f)
        assertEquals(1f - LiquidGlassLensSpec.MAX_SQUASH, thrown.scaleY, 1e-4f)

        // Direction is irrelevant to a deformation: only speed is.
        val mirrored = LiquidGlassLensSpec.deformation(-3000f)
        assertEquals(thrown.scaleX, mirrored.scaleX, 1e-4f)
        assertEquals(thrown.scaleY, mirrored.scaleY, 1e-4f)

        // Small speeds are proportional, not capped.
        val slow = LiquidGlassLensSpec.deformation(300f)
        assertEquals(1.09f, slow.scaleX, 1e-4f)
        assertEquals(0.955f, slow.scaleY, 1e-4f)

        // The puck grows wide and stays readable: the squash is always smaller than the stretch.
        assertTrue(
            LiquidGlassLensSpec.MAX_SQUASH < LiquidGlassLensSpec.MAX_STRETCH
        )
    }

    @Test
    fun thePressGrowsThePuckByEightPercentAndClamps() {
        assertEquals(1f, LiquidGlassLensSpec.pressScale(0f), 1e-4f)
        assertEquals(1.08f, LiquidGlassLensSpec.pressScale(1f), 1e-4f)
        assertEquals(1.08f, LiquidGlassLensSpec.pressScale(1.4f), 1e-4f)
        assertEquals(1f, LiquidGlassLensSpec.pressScale(-0.5f), 1e-4f)
        assertTrue(LiquidGlassLensSpec.pressScale(0.5f) < LiquidGlassLensSpec.pressScale(1f))
    }

    @Test
    fun theSpringsAreTheOnesTheBriefAskedFor() {
        // Spring.DampingRatioMediumBouncy and Spring.StiffnessLow, restated as data so the UI and
        // this test cannot drift apart.
        assertEquals(0.6f, LiquidGlassLensSpec.SNAP_DAMPING_RATIO, 1e-4f)
        assertEquals(400f, LiquidGlassLensSpec.SNAP_STIFFNESS, 1e-4f)
        assertEquals(LiquidGlassLensSpec.SNAP_DAMPING_RATIO, LiquidGlassLensSpec.PRESS_DAMPING_RATIO, 1e-4f)
        assertEquals(LiquidGlassLensSpec.SNAP_STIFFNESS, LiquidGlassLensSpec.PRESS_STIFFNESS, 1e-4f)
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

    @Test
    fun theGlassValuesAreTheOnesTheBriefNamed() {
        assertEquals(0.18f, LiquidGlassLensSpec.LENS_GLASS_ALPHA, 1e-6f)
        assertEquals(0.92f, LiquidGlassLensSpec.GLASS_ALPHA, 1e-6f)
        assertEquals(1.5f, LiquidGlassLensSpec.RIM_WIDTH_DP, 1e-6f)
        assertEquals(24, LiquidGlassLensSpec.OUTER_SHADOW_DP)
        assertEquals(20, LiquidGlassLensSpec.BACKDROP_BLUR_DP)
        assertEquals(16, LiquidGlassLensSpec.HORIZONTAL_MARGIN_DP)
        assertEquals(16, LiquidGlassLensSpec.BOTTOM_MARGIN_DP)
        assertEquals(0.08f, LiquidGlassLensSpec.OUTER_SHADOW_ALPHA, 1e-6f)
    }
}
