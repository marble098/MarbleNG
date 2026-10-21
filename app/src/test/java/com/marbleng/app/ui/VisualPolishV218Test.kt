package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_VISUAL_POLISH_V218 — the corner morph a subscription card runs through when it closes.
 *
 * The complaint off a real device was that a sub's corners broke apart the moment it collapsed:
 * the clip jumped from a 16 dp top-only shape to a 20 dp all-round one in a single frame while the
 * outline kept drawing its own 16 dp geometry, so for the whole collapse the card's silhouette and
 * its border described two different corners. The fix drives both from one animated fraction, and
 * that fraction's arithmetic is pure so these tests can pin it:
 *
 *  - at full attach (open) the card's bottom corners are square and the frame's bottom edge is
 *    pushed two radii past the slice so it never draws;
 *  - at rest (closed) the bottom corners return to the card's own [ServersHierarchy.GROUP_CORNER_DP]
 *    and the frame's bottom edge comes back to the slice boundary so it draws — the same radius the
 *    clip uses, which is the whole point: one corner at every frame of the morph.
 *
 * The drawing code is allowed to evolve; these numbers are the contract.
 */
class VisualPolishV218Test {
    @Test
    fun theAttachFractionIsClampedToTheUnitBand() {
        assertEquals(0f, ServersHierarchy.openFraction(-1f), 0f)
        assertEquals(0f, ServersHierarchy.openFraction(0f), 0f)
        assertEquals(.5f, ServersHierarchy.openFraction(.5f), 0f)
        assertEquals(1f, ServersHierarchy.openFraction(1f), 0f)
        assertEquals(1f, ServersHierarchy.openFraction(4f), 0f)
    }

    @Test
    fun anOpenCardIsSquareBelowAndAClosedCardRoundsAtItsOwnCorner() {
        // Fully attached: the server stack below owns the bottom edge, so the card is square there.
        assertEquals(0f, ServersHierarchy.attachCornerDp(1f), 0f)
        // Fully closed: the card rounds at the same corner its outline has always drawn.
        assertEquals(
            ServersHierarchy.GROUP_CORNER_DP,
            ServersHierarchy.attachCornerDp(0f),
            0f
        )
        // Halfway, the corner is halfway — the morph is linear in the fraction.
        assertEquals(
            ServersHierarchy.GROUP_CORNER_DP / 2f,
            ServersHierarchy.attachCornerDp(.5f),
            1e-5f
        )
        // An out-of-band fraction is clamped, never extrapolated past either silhouette.
        assertEquals(
            ServersHierarchy.GROUP_CORNER_DP,
            ServersHierarchy.attachCornerDp(-3f),
            0f
        )
        assertEquals(0f, ServersHierarchy.attachCornerDp(3f), 0f)
    }

    @Test
    fun theFrameBottomEdgeHidesWhileAttachedAndReturnsWhenClosed() {
        val radius = 16f
        // Attached: the rounded rect over-extends by two radii, so the clipRect hides the bottom.
        assertEquals(2f * radius, ServersHierarchy.frameBottomExtension(1f, radius), 0f)
        // Closed: no over-extension, so the bottom edge draws on the slice boundary.
        assertEquals(0f, ServersHierarchy.frameBottomExtension(0f, radius), 0f)
        // Halfway: one radius of over-extension.
        assertEquals(radius, ServersHierarchy.frameBottomExtension(.5f, radius), 1e-5f)
    }

    @Test
    fun theClipCornerAndTheFrameEdgeAgreeAtBothEndsOfTheMorph() {
        // The two readings of one fraction must land on the same silhouette at the endpoints, or
        // the corner would break apart exactly the way the reported bug did.
        val closedCorner = ServersHierarchy.attachCornerDp(0f)
        val closedExtension = ServersHierarchy.frameBottomExtension(0f, ServersHierarchy.GROUP_CORNER_DP)
        assertEquals(ServersHierarchy.GROUP_CORNER_DP, closedCorner, 0f)
        assertEquals(0f, closedExtension, 0f)

        val openCorner = ServersHierarchy.attachCornerDp(1f)
        val openExtension = ServersHierarchy.frameBottomExtension(1f, ServersHierarchy.GROUP_CORNER_DP)
        assertEquals(0f, openCorner, 0f)
        assertEquals(2f * ServersHierarchy.GROUP_CORNER_DP, openExtension, 0f)

        // And the closed corner is the card's own corner, never a mismatched 20 dp.
        assertTrue(closedCorner == ServersHierarchy.GROUP_CORNER_DP)
    }
}
