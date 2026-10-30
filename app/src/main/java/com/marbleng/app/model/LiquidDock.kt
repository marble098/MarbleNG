package com.marbleng.app.model

import kotlin.math.abs
import kotlin.math.exp

/**
 * MARBLE_LIQUID_DOCK_V245 — the floating glass pill has a geometry, a physics and an optics
 * profile, and all three live on the JVM side of the boundary.
 *
 * V233 gave the bottom bar a measured active line; the line is retired now (its spec and its test
 * stay as the record of what "measured instead of offset" meant). What replaces it is not a
 * recoloured slot but a physical object: a frosted capsule that floats above the page, and a
 * circular lens of glass that sits on top of the row, magnifies whatever it covers and is dragged
 * left and right by a finger.
 *
 * Numbers that a screenshot cannot prove belong here, not in the composable:
 *
 * | question | answer |
 * |---|---|
 * | how tall is the pill, and does it fit the chrome budget? | [barHeightDp], [fitsChrome] |
 * | which tab is under the finger? | [nearestIndex] |
 * | did the lens cross a tab edge (one haptic tick)? | [boundaryCrossed] |
 * | how much is the icon under the lens magnified? | [magnification] |
 * | how big is the lens while the finger is down? | [pressedScale] |
 * | what does a two-digit badge look like? | [badgeLabel] |
 *
 * `LiquidDockV245Test` pins every one of them.
 */
object LiquidDockSpec {
    const val MARKER: String = "MARBLE_LIQUID_DOCK_V245"

    // ---- the capsule ---------------------------------------------------------------------

    /** Air between the pill and the screen edge. The pill is compact, never full-bleed. */
    const val HORIZONTAL_MARGIN_DP: Int = 18

    /** Air below the pill, above the gesture bar. */
    const val BOTTOM_MARGIN_DP: Int = 12

    /** Inner padding that keeps the first and last lens travel from touching the rim. */
    const val INNER_PADDING_DP: Int = 6

    /**
     * Everything the dock may occupy from the bottom edge. The screens reserve
     * `MarbleSpec.BOTTOM_BAR_CLEARANCE_DP` (112dp) of scroll clearance; a dock that grew past it
     * would start hiding the last row of every list, which is the exact complaint V243 answered.
     */
    const val CHROME_BUDGET_DP: Int = 112

    /** The frosted white wash of the pill. Deliberately inside the 0.85–0.90 band. */
    const val GLASS_ALPHA: Float = 0.87f

    /** The same wash on a dark theme: white at a low alpha over the dark surface. */
    const val GLASS_ALPHA_DARK: Float = 0.16f

    /** The frosted rim. Sub-pixel so it reads as a reflection, not as a border. */
    const val RIM_WIDTH_DP: Float = 0.9f

    /** Ambient elevation of the pill. Soft, wide, never a hard drop shadow. */
    const val OUTER_SHADOW_DP: Int = 18

    /** Backdrop blur radius. The page behind the pill is sampled, blurred and washed. */
    const val BACKDROP_BLUR_DP: Int = 22

    // ---- the lens ------------------------------------------------------------------------

    /** How far the lens is inset from the top and bottom of the pill. */
    const val LENS_INSET_DP: Int = 5

    /** The lens is an ellipse, a little wider than it is tall: a drop, not a coin. */
    const val LENS_WIDEN_DP: Int = 8

    /** 13 % growth while the finger is down — inside the 10–15 % brief. */
    const val LENS_PRESS_GROWTH: Float = 0.13f

    /** Magnification at the centre of the lens. +30 % is visible without becoming a cartoon. */
    const val LENS_MAGNIFICATION: Float = 0.30f

    /** The Gaussian half-width of the lens, in slot widths. One slot away is ~0.25. */
    const val LENS_FALLOFF_SLOTS: Float = 0.85f

    /** Labels magnify less than icons: text that jumps in size is a rendering bug, not an optic. */
    const val LENS_LABEL_MAGNIFICATION: Float = 0.12f

    // ---- the physics ---------------------------------------------------------------------

    /**
     * Snap spring after the finger lifts. 0.72 damping is bouncy enough to feel like it was let go
     * and calm enough that it never overshoots into the neighbouring tab; 620 stiffness settles in
     * roughly 300 ms, which is the expressive-spatial band.
     */
    const val SNAP_DAMPING_RATIO: Float = 0.72f
    const val SNAP_STIFFNESS: Float = 620f

    /** Press spring. Slightly bouncier, because a touch is a smaller distance than a trip. */
    const val PRESS_DAMPING_RATIO: Float = 0.66f
    const val PRESS_STIFFNESS: Float = 760f

    // ---- the badge -----------------------------------------------------------------------

    /** Above this, the badge says "9+" — a count nobody reads past the first digit. */
    const val BADGE_MAX_COUNT: Int = 9

    /** The badge never shrinks below 16dp, so a single digit is still a target for the eye. */
    const val BADGE_MIN_SIZE_DP: Int = 16

    // ---- geometry ------------------------------------------------------------------------

    /** The bar height is still the V233 ladder: the physical bar did not move, its skin did. */
    fun barHeightDp(size: DockSize): Int = DockIndicatorSpec.barHeightDp(size)

    fun iconSizeDp(size: DockSize): Int = DockIndicatorSpec.iconSizeDp(size)

    /** A stadium: the corner radius is exactly half the height, at every size. */
    fun cornerRadiusDp(size: DockSize): Float = barHeightDp(size) / 2f

    fun lensHeightDp(size: DockSize): Int = barHeightDp(size) - LENS_INSET_DP * 2

    fun lensWidthDp(size: DockSize): Int = lensHeightDp(size) + LENS_WIDEN_DP

    /** The pill plus the air below it — what the bottom of the screen actually loses. */
    fun totalHeightDp(size: DockSize): Int = barHeightDp(size) + BOTTOM_MARGIN_DP

    fun fitsChrome(size: DockSize): Boolean = totalHeightDp(size) <= CHROME_BUDGET_DP

    fun allFit(): Boolean = DockSize.entries.all { size -> fitsChrome(size) }

    fun slotWidthDp(barWidthDp: Float, tabCount: Int): Float =
        if (tabCount <= 0) 0f else barWidthDp / tabCount

    // ---- the drag arithmetic -------------------------------------------------------------

    /** Centre of a tab as a fraction of the pill's width: 0 is the leading edge, 1 the trailing one. */
    fun slotCenterFraction(index: Int, count: Int): Float {
        if (count <= 0) return 0.5f
        val clamped = index.coerceIn(0, count - 1)
        return (clamped + 0.5f) / count
    }

    fun fractionForX(xPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) 0.5f else (xPx / widthPx).coerceIn(0f, 1f)

    /**
     * The tab under a lens fraction. A lens exactly on a boundary belongs to the tab it is
     * entering, which is what the eye sees and what the haptic should announce.
     */
    fun nearestIndex(fraction: Float, count: Int): Int {
        if (count <= 0) return 0
        val clamped = fraction.coerceIn(0f, 1f)
        val raw = (clamped * count).toInt()
        return raw.coerceIn(0, count - 1)
    }

    /** True when a drag from [from] to [to] changed the tab under the lens: one haptic tick. */
    fun boundaryCrossed(from: Float, to: Float, count: Int): Boolean =
        nearestIndex(from, count) != nearestIndex(to, count)

    /** Distance between the lens and a tab centre, measured in slot widths. */
    fun distanceInSlots(fraction: Float, index: Int, count: Int): Float =
        abs(fraction - slotCenterFraction(index, count)) * count

    // ---- the optics ----------------------------------------------------------------------

    /**
     * The Gaussian bulge of the lens. 1.30 at the centre, 1.075 one slot away, 1.0 far away —
     * so two neighbouring tabs are never magnified at once and the row never wobbles as a whole.
     */
    fun magnification(distanceInSlots: Float): Float =
        1f + LENS_MAGNIFICATION * bulge(distanceInSlots)

    fun labelMagnification(distanceInSlots: Float): Float =
        1f + LENS_LABEL_MAGNIFICATION * bulge(distanceInSlots)

    /** 1 under the lens, ~0 elsewhere: how blue a tab is, how bright the caustic is. */
    fun highlight(distanceInSlots: Float): Float = bulge(distanceInSlots).coerceIn(0f, 1f)

    private fun bulge(distanceInSlots: Float): Float {
        val scaled = abs(distanceInSlots) / LENS_FALLOFF_SLOTS
        return exp(-(scaled * scaled))
    }

    /** Lens size multiplier: 1.0 at rest, 1.13 with the finger down. */
    fun pressedScale(progress: Float): Float = 1f + LENS_PRESS_GROWTH * progress.coerceIn(0f, 1f)

    /** 1, 2, … 9, then "9+". Empty for zero: an empty badge is not drawn at all. */
    fun badgeLabel(count: Int): String = when {
        count <= 0 -> ""
        count > BADGE_MAX_COUNT -> "$BADGE_MAX_COUNT+"
        else -> count.toString()
    }
}

/**
 * The red counter is a notification, so it is fed by things that are actually waiting for a tap:
 * an update that is published and not dismissed, and the findings of the last network scan that
 * the user has not applied yet. Two unrelated numbers, one honest sum.
 */
object LiquidDockNotices {
    const val MARKER: String = "MARBLE_LIQUID_DOCK_V245"

    fun count(updateAvailable: Boolean, unappliedFindings: Int): Int {
        val update = if (updateAvailable) 1 else 0
        return update + unappliedFindings.coerceAtLeast(0)
    }
}
