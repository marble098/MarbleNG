package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_GLASS_DOCK_V261 — the aperture's geometry, pinned on the host JVM.
 *
 * A lens is the one thing in this product that can go *wrong in a way a screenshot hides*: an
 * aperture that is 4dp off still looks like a lens, and an aperture that has slid past the capsule's
 * rim looks like a styling choice. So the placement is a pure function with three properties, and this
 * file pins all three.
 */
class GlassDockV261Test {

    private val epsilon = 0.5f

    @Test
    fun theCapsuleIsNarrowerThanThePageAtEveryPhoneWidth() {
        // "Much narrower" is a relationship, not a number: the capsule has to be visibly an object
        // *on* the page, so the two margins eat more than a tenth of a 360dp phone and the widest
        // possible capsule never grows into a strip on a tablet.
        val page = 360f
        val capsule = page - DockStudio.HORIZONTAL_MARGIN_DP * 2
        assertTrue("the margins must eat more than a tenth of a phone", capsule / page < 0.9f)
        assertEquals(300f, capsule, epsilon)
        assertTrue(
            "no window may draw a capsule wider than the cap",
            page - DockStudio.HORIZONTAL_MARGIN_DP * 2 <= DockStudio.MAX_WIDTH_DP
        )
        assertTrue(
            "a foldable draws the same object, not a longer one",
            900f - DockStudio.HORIZONTAL_MARGIN_DP * 2 > DockStudio.MAX_WIDTH_DP
        )
    }

    @Test
    fun theCapsuleIsAStadiumAndEveryNestedCurveIsOneInsetInsideIt() {
        // MARBLE_GLASS_DOCK_V261 reversed V260's "not a stadium". The rule itself did not change:
        // one ratio, one inset, nothing nested inventing its own curve.
        assertEquals(0.5f, DockStudio.CORNER_RATIO, 0.0001f)
        DockSize.entries.forEach { rung ->
            val half = DockStudio.heightDp(rung) / 2f
            assertEquals("$rung ends in two half-circles", half, DockStudio.cornerRadiusDp(rung), epsilon)
            assertEquals(
                "$rung nests one inset inside the capsule",
                DockStudio.cornerRadiusIntDp(rung) - DockStudio.INNER_CORNER_INSET_DP.toFloat(),
                DockStudio.innerRadiusDp(rung),
                epsilon
            )
        }
    }

    @Test
    fun theApertureNeverLeavesTheCapsule() {
        val barWidth = 300f
        val aperture = 96f
        val inset = DockStudio.pillPaddingDp(DockSize.MEDIUM).toFloat()
        // Every slot on the row, including the two that sit against the rims.
        listOf(0f, 48f, 150f, 252f, 300f).forEach { centre ->
            val left = GlassDock.apertureLeftPx(barWidth, centre, aperture, inset)
            assertTrue("left $left must not leave the leading rim", left >= inset - epsilon)
            assertTrue(
                "left $left must not push the trailing rim out",
                left + aperture <= barWidth - inset + epsilon
            )
        }
        // A row nobody has measured yet answers with the inset rather than with a guess.
        assertEquals(inset, GlassDock.apertureLeftPx(0f, 120f, aperture, inset), epsilon)
    }

    @Test
    fun theApertureIsCentredOnItsSlotAndStopsAtAnEdge() {
        val left = GlassDock.apertureLeftPx(
            barWidthPx = 300f,
            centrePx = 150f,
            apertureWidthPx = 100f,
            trackInsetPx = 6f
        )
        assertEquals("a centred slot centres its lens", 100f, left, epsilon)

        // The first slot's centre is nearer the rim than half an aperture: the lens clamps instead
        // of hanging out of the glass.
        val clamped = GlassDock.apertureLeftPx(300f, 20f, 100f, 6f)
        assertEquals(6f, clamped, epsilon)
    }

    @Test
    fun theLensIsNeverWiderThanHalfTheBarAndIsRounderThanTheRim() {
        assertEquals(150f, GlassDock.apertureMaxWidthDp(300f), epsilon)
        assertEquals(0f, GlassDock.apertureMaxWidthDp(0f), epsilon)
        DockSize.entries.forEach { rung ->
            val lens = GlassDock.lensRadiusDp(rung)
            assertTrue("$rung lens must be round", lens > DockStudio.INNER_CORNER_INSET_DP)
            assertTrue(
                "$rung lens must sit inside the capsule's own curve",
                lens <= DockStudio.innerRadiusDp(rung)
            )
        }
    }

    @Test
    fun theRefractionBandAndFringeScaleWithTheApertureButStayBounded() {
        // A Slim capsule's aperture and a Grand one's are 44dp and 66dp wide; the band has to read
        // as glass at both and as neither a stripe nor a haze.
        listOf(30f, 48f, 66f, 200f).forEach { width ->
            val band = GlassDock.refractionBandDp(width)
            assertTrue("band $band for $width", band in 4f..12f)
            val fringe = GlassDock.fringeDp(width)
            assertTrue("fringe $fringe for $width", fringe in 1f..3f)
        }
    }

    @Test
    fun theMaterialStaysInsideTheBandWhereGlassStillReadsAsGlass() {
        // Under 0.6 the glyphs lose their ground on a light page; over 0.9 the capsule stops being a
        // pane and becomes a plank. Both ends are pinned.
        assertTrue(GlassDock.SURFACE_ALPHA in 0.6f..0.9f)
        assertTrue(GlassDock.LENS_ALPHA < GlassDock.SURFACE_ALPHA)
        assertTrue(GlassDock.RIM_ALPHA > GlassDock.FLOOR_ALPHA)
        assertTrue("one wide lift and one tight contact", GlassDock.AMBIENT_SHADOW_DP > GlassDock.CONTACT_SHADOW_DP)
    }

    @Test
    fun aStillReaderStillGetsALens() {
        // Motion is a preference, not a prerequisite: the aperture exists at every setting, it just
        // has no travel. `TRAVEL_MS` is the number the reduced-motion path short-circuits.
        assertTrue(GlassDock.TRAVEL_MS in 200..600)
        assertTrue(GlassDock.DRAG_OVERSHOOT_FRACTION in 0.1f..0.5f)
        assertTrue("the marker is the contract", GlassDock.MARKER.isNotBlank())
    }
}
