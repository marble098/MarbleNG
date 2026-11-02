package com.marbleng.app.model

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LIQUID_LENS_V262 — the lens, pinned on the host JVM.
 *
 * A lens is the one object in this product that can go wrong in a way a screenshot *forgives*: a
 * pane at 0.45 instead of 0.55 still looks like glass, and a lens that is thrown 4dp past its slot
 * still reads as a spring. So every number the brief names is asserted, and the two behaviours that
 * cannot be photographed are simulated here — the spring's overshoot and the deformation's shape.
 */
class LiquidLensV262Test {

    private val epsilon = 1e-6f

    @Test
    fun theMarkerNamesThePass() {
        assertEquals("MARBLE_LIQUID_LENS_V262", LiquidLens.MARKER)
    }

    @Test
    fun theBriefsMaterialNumbersArePinned() {
        // The pill: 16dp / 4dp / 8 %, a 1dp hairline at 0.6, a stadium.
        assertEquals(16, LiquidLens.PILL_SHADOW_BLUR_DP)
        assertEquals(4f, LiquidLens.PILL_SHADOW_OFFSET_Y_DP, epsilon)
        assertEquals(0.08f, LiquidLens.PILL_SHADOW_ALPHA, epsilon)
        assertEquals(1f, LiquidLens.PILL_BORDER_DP, epsilon)
        assertEquals(0.6f, LiquidLens.PILL_BORDER_ALPHA, epsilon)
        assertEquals(50, LiquidLens.PILL_CORNER_PERCENT)
        assertTrue(
            "the floor of the hairline must be quieter than its crown",
            LiquidLens.PILL_BORDER_FLOOR_ALPHA < LiquidLens.PILL_BORDER_ALPHA
        )

        // The lens: semi-transparent white, a 12px backdrop blur, a 1.5dp rim from 0.95 to 0.15.
        assertEquals(0.55f, LiquidLens.GLASS_ALPHA_LIGHT, epsilon)
        assertTrue(
            "a dark page needs a quieter white than a light one",
            LiquidLens.GLASS_ALPHA_DARK < LiquidLens.GLASS_ALPHA_LIGHT
        )
        assertEquals(12f, LiquidLens.GLASS_BLUR_RADIUS_PX, epsilon)
        assertEquals(1.5f, LiquidLens.GLASS_RIM_DP, epsilon)
        assertEquals(0.95f, LiquidLens.GLASS_RIM_START_ALPHA, epsilon)
        assertEquals(0.15f, LiquidLens.GLASS_RIM_END_ALPHA, epsilon)
        assertTrue(
            "the rim is brighter where the light lands",
            LiquidLens.GLASS_RIM_START_ALPHA - LiquidLens.GLASS_RIM_END_ALPHA > 0.7f
        )

        // The rest of the pane: the refracting band, the catch-light arc, the reflected room and
        // the fallback frost — the material is *all* here, so the composable carries no numbers.
        assertEquals(0.26f, LiquidLens.GLASS_BAND_TOP_ALPHA, epsilon)
        assertEquals(202f, LiquidLens.GLASS_ARC_START_DEG, epsilon)
        assertEquals(136f, LiquidLens.GLASS_ARC_SWEEP_DEG, epsilon)
        assertTrue(
            "the catch-light covers the upper third, not the whole rim",
            LiquidLens.GLASS_ARC_START_DEG + LiquidLens.GLASS_ARC_SWEEP_DEG <= 360f
                && LiquidLens.GLASS_ARC_SWEEP_DEG < 180f
        )
        assertTrue("the fallback frost is a pool, not a wash", LiquidLens.GLASS_FROST_ALPHA > 0f)
        assertTrue(LiquidLens.GLASS_FROST_ALPHA < LiquidLens.GLASS_ALPHA_LIGHT)

        // The optics: 1.0 -> 1.28, inside the band the brief allows.
        assertEquals(1f, LiquidLens.REST_SCALE, epsilon)
        assertEquals(1.28f, LiquidLens.MAGNIFICATION, epsilon)
        assertTrue(LiquidLens.MAGNIFICATION > LiquidLens.REST_SCALE)
        assertTrue(LiquidLens.MAGNIFICATION >= LiquidLens.MIN_MAGNIFICATION)
        assertTrue(LiquidLens.MAGNIFICATION <= LiquidLens.MAX_MAGNIFICATION)
        assertTrue("magnification is not a dye: the glyph also gets heavier", LiquidLens.GLYPH_WEIGHT_ACTIVE > 1f)

        // The connection light: it grows and lifts, and neither number may be a no-op.
        assertTrue(LiquidLens.STATUS_DOT_SCALE > 1f)
        assertTrue(LiquidLens.STATUS_DOT_LIFT_DP > 0f)
        assertEquals(0f, LiquidLens.statusDotLiftDp(selected = false), epsilon)
        assertEquals(LiquidLens.STATUS_DOT_LIFT_DP, LiquidLens.statusDotLiftDp(selected = true), epsilon)
    }

    @Test
    fun theSpringIsThePairTheBriefNames() {
        // The brief names `Spring.DampingRatioMediumBouncy` *and* writes ≈0.75 beside it; in this
        // framework those are two different springs (the token is 0.5, and 0.75 is the low-bouncy
        // one). The dock takes the number — 0.75 is a 2.8 % arrival overshoot, 0.5 would be 16 % —
        // and this test is where that judgement is written down instead of remembered.
        assertEquals(0.75f, LiquidLens.SPRING_DAMPING_RATIO, epsilon)
        assertTrue(
            "the lens must settle, not ring: calmer than the framework's medium-bouncy token",
            LiquidLens.SPRING_DAMPING_RATIO > Spring.DampingRatioMediumBouncy
        )
        assertTrue(
            "…but it must still arrive with energy, or the travel reads as a tween",
            LiquidLens.SPRING_DAMPING_RATIO < Spring.DampingRatioNoBouncy
        )
        // The number is the low-bouncy value, so the dock reads as that token to anyone comparing.
        assertEquals(Spring.DampingRatioLowBouncy, LiquidLens.SPRING_DAMPING_RATIO, epsilon)
        // The other half of the pair is the framework's own constant, by value.
        assertEquals(Spring.StiffnessMediumLow, LiquidLens.SPRING_STIFFNESS, epsilon)
        assertEquals(400f, LiquidLens.SPRING_STIFFNESS, epsilon)
    }

    @Test
    fun theLensArrivesWithABounceAndNotWithARubberBall() {
        val spec = spring<Float>(
            dampingRatio = LiquidLens.SPRING_DAMPING_RATIO,
            stiffness = LiquidLens.SPRING_STIFFNESS
        ).vectorize(Float.VectorConverter)

        val from = AnimationVector1D(0f)
        val to = AnimationVector1D(100f)
        val atRest = AnimationVector1D(0f)
        val duration = spec.getDurationNanos(from, to, atRest)
        val samples = (0..duration step 1_000_000L).map { nanos ->
            spec.getValueFromNanos(nanos, from, to, atRest).value
        }

        assertEquals("the spring must land", 100f, samples.last(), 1f)
        assertTrue(
            "medium bouncy has to overshoot, or the travel reads as a tween",
            samples.max() > to.value
        )
        assertTrue(
            "a lens is not a rubber ball: the overshoot stays inside a few dp",
            samples.max() < to.value * 1.1f
        )
        assertTrue("the spring never goes backwards before it arrives", samples.none { it < -1f })
    }

    @Test
    fun atRestTheLensIsTheUnitAndNothingElse() {
        val rest = LiquidLens.deformation(0f)
        assertEquals(1f, rest.scaleX, epsilon)
        assertEquals(1f, rest.scaleY, epsilon)
        assertEquals(LensDeformation.NONE, rest)
    }

    @Test
    fun aThrowStretchesTheLensAlongTheRowAndSquashesItAgainstIt() {
        // The brief's numbers, at the saturation velocity: 15 % wider, 8 % shorter.
        val thrown = LiquidLens.deformation(LiquidLens.STRETCH_SATURATION_PX_PER_S)
        assertEquals(1f + LiquidLens.MAX_STRETCH, thrown.scaleX, epsilon)
        assertEquals(1f - LiquidLens.MAX_SQUASH, thrown.scaleY, epsilon)
        assertEquals(1.15f, thrown.scaleX, epsilon)
        assertEquals(0.92f, thrown.scaleY, epsilon)
        assertTrue("a liquid drop stretches more than it squashes", LiquidLens.MAX_STRETCH > LiquidLens.MAX_SQUASH)

        // Beyond saturation it is clamped, not amplified: a flung lens may not become a line.
        val veryFast = LiquidLens.deformation(50_000f)
        assertEquals(thrown, veryFast)

        // Direction is irrelevant.
        assertEquals(LiquidLens.deformation(1_200f), LiquidLens.deformation(-1_200f))

        // Half the saturation is half the deformation, and the recovery is continuous: a spring
        // that snapped back to a circle on its first frame would flicker.
        val half = LiquidLens.deformation(LiquidLens.STRETCH_SATURATION_PX_PER_S / 2f)
        assertTrue(half.scaleX > 1f && half.scaleX < thrown.scaleX)
        assertTrue(half.scaleY < 1f && half.scaleY > thrown.scaleY)
        assertTrue(LiquidLens.deformation(1f).scaleX > 1f)
    }

    @Test
    fun theLensFillsTheInnerRowAtEveryRung() {
        // The capsule minus one inset above and below: 44 / 52 / 60 dp, with the same air at the
        // top and the floor that the row's own padding describes.
        assertEquals(44, LiquidLens.lensDiameterDp(56, 6))
        assertEquals(52, LiquidLens.lensDiameterDp(68, 8))
        assertEquals(60, LiquidLens.lensDiameterDp(82, 11))
        DockSize.entries.forEach { rung ->
            val height = DockStudio.heightDp(rung)
            val inset = DockStudio.pillPaddingDp(rung)
            val diameter = LiquidLens.lensDiameterDp(height, inset)
            assertTrue("$rung must leave a lens", diameter > 0)
            assertTrue("$rung: the lens must fit its capsule", diameter < height)
            assertEquals(
                "$rung: the lens is centred in the capsule",
                (height - diameter) / 2,
                inset
            )
            // A lens is a capsule: its corner is half its diameter, i.e. a circle at rest.
            assertEquals(
                diameter / 2f,
                LiquidLens.lensCornerDp(diameter.toFloat()),
                epsilon
            )
        }
        // A degenerate capsule may not produce a zero-sized lens.
        assertEquals(1, LiquidLens.lensDiameterDp(6, 8))
    }

    @Test
    fun thePillReadsTheLaddersCornerAndTheBriefsPercent() {
        // "RoundedCornerShape(percent = 50)" is a ratio, and the ladder's corner is that ratio
        // applied to its own height. Two statements of one rule, so they are asserted equal: the
        // composable reads [DockStudio.cornerRadiusDp] and never invents a stadium of its own.
        DockSize.entries.forEach { rung ->
            val height = DockStudio.heightDp(rung).toFloat()
            assertEquals(
                "$rung: the ladder's corner is the brief's percent-50 stadium",
                height * LiquidLens.PILL_CORNER_PERCENT / 100f,
                DockStudio.cornerRadiusDp(rung),
                0.5f
            )
        }
    }

    @Test
    fun theLensNeverLeavesTheCapsuleOnAnySlot() {
        val barWidth = 300f
        val diameter = LiquidLens.lensDiameterDp(68, 8).toFloat()
        val inset = DockStudio.pillPaddingDp(DockSize.MEDIUM).toFloat()
        // Four tabs plus the centre action, at the centres a measured row reports.
        val centres = listOf(32f, 105f, 150f, 195f, 268f)
        centres.forEach { centre ->
            val left = LiquidLens.lensLeftPx(barWidth, centre, diameter, inset)
            assertTrue("left $left must not leave the leading rim", left >= inset - epsilon)
            assertTrue(
                "left $left must not push the trailing rim out",
                left + diameter <= barWidth - inset + epsilon
            )
            // Away from the rims the lens is exactly centred on its slot; at the rims it is the
            // rim that wins, which is what keeps the glass inside the bar.
            val centred = (centre - diameter / 2f).coerceIn(inset, barWidth - inset - diameter)
            assertEquals(centred, left, epsilon)
        }
        // Nothing measured yet: the bar answers with its inset rather than with a guess.
        assertEquals(inset, LiquidLens.lensLeftPx(0f, 120f, diameter, inset), epsilon)
        // The model defers to the one clamp in the product rather than inventing a second one.
        assertEquals(
            GlassDock.apertureLeftPx(barWidth, 150f, diameter, inset),
            LiquidLens.lensLeftPx(barWidth, 150f, diameter, inset),
            epsilon
        )
    }

    @Test
    fun theCaptionFadesAndTheCounterStaysOutOfTheLens() {
        // A spring that overshoots passes 1 and one that bounces back passes 0; `Color.copy` with
        // either would be an exception in the graphics pipeline.
        assertEquals(0f, LiquidLens.labelAlpha(-0.2f), epsilon)
        assertEquals(0f, LiquidLens.labelAlpha(0f), epsilon)
        assertEquals(0.5f, LiquidLens.labelAlpha(0.5f), epsilon)
        assertEquals(1f, LiquidLens.labelAlpha(1f), epsilon)
        assertEquals(1f, LiquidLens.labelAlpha(1.4f), epsilon)
        // The accent wash inside the pane is a hint, never a fill: the glyph is printed over it.
        assertTrue(LiquidLens.GLASS_ACCENT_ALPHA > 0f)
        assertTrue(LiquidLens.GLASS_ACCENT_ALPHA < LiquidLens.GLASS_ALPHA_LIGHT)
    }
}
