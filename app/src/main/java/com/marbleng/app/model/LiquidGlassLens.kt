package com.marbleng.app.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * MARBLE_LIQUID_GLASS_LENS_V247 — the optical and physical model of the magnifier bottom bar.
 *
 * V245 put a drawn lens on top of the row and let a Gaussian falloff magnify the glyph underneath
 * it. That is an *icon* effect: the glass travels, and the icon it covers is redrawn bigger. A real
 * lens is a *projection*: everything under the glass is one continuous, magnified image of the same
 * row, and the glass is the aperture that reveals it. V247 rebuilds the optics around that idea
 * (base layer + a magnified overlay clipped to the aperture) and with it comes the arithmetic that
 * a screenshot cannot prove — where the aperture may travel, which tab it is over, how much the
 * content under it is magnified, and how the puck deforms under velocity.
 *
 * Everything here is pure Kotlin and lives on the JVM side of the boundary, so
 * `LiquidGlassLensV247Test` pins it without a device:
 *
 * | question | answer |
 * |---|---|
 * | how wide is a slot? | [itemWidthPx] |
 * | where is tab `i`'s anchor? | [tabCenterX] |
 * | how far may the lens centre travel before it leaves the capsule? | [minLensCenterX], [maxLensCenterX], [clampLensCenterX] |
 * | which tab is under the lens? | [indexAt] |
 * | how much is the content under the glass magnified? | [magnificationAt] |
 * | how does the puck stretch while it is thrown? | [deformation] |
 * | how big is the puck while the finger is down? | [pressScale] |
 * | what does a two-digit counter look like? | [badgeText] |
 *
 * ## The magnification profile is the brief's distortion formula
 *
 * A spherical lens maps a point at distance `d` from its axis to radius
 * `r' = r · (1 - k · (1 - d/R)²)` — the AGSL line in the brief, with `k` negative for a converging
 * (magnifying) element. Evaluated on the two-tier projection, an item anchor at distance `d` is
 * drawn at
 *
 * ```
 * scale(d) = 1 + 0.36 · (1 - d/R)²          for d <= R,  and 1 outside
 * ```
 *
 * so the anchor directly under the glass is at `1.36×` (inside the requested 1.30–1.38 band), the
 * seam at the rim is exactly `1.0×` — the overlay and the base layer are continuous there, which is
 * what makes it read as optics rather than as a sticker — and a neighbour one slot away is not
 * touched at all because it sits outside the aperture. This continuous-but-sampled-at-anchors form
 * is deliberate: it needs no GPU shader, so no device can lose its navigation row to an AGSL compile
 * failure, and it still produces curvature instead of a flat "active tab" swap.
 */
object LiquidGlassLensSpec {
    const val MARKER: String = "MARBLE_LIQUID_GLASS_LENS_V247"

    // ---- the floating capsule -------------------------------------------------------------

    /** Air between the pill and the screen edge. */
    const val HORIZONTAL_MARGIN_DP: Int = 16

    /** Air below the pill, above the gesture bar. */
    const val BOTTOM_MARGIN_DP: Int = 16

    /** Inner padding, so the first and last item never touch the rim. */
    const val INNER_PADDING_DP: Float = 6f

    /** The frosted acrylic of the capsule. */
    const val GLASS_ALPHA: Float = 0.92f

    /** The same wash on a dark theme: white at a low alpha over the dark surface. */
    const val GLASS_ALPHA_DARK: Float = 0.16f

    /** Sub-pixel rim of the capsule: a reflection, not a border. */
    const val RIM_WIDTH_DP: Float = 1.5f

    /** Ambient elevation of the capsule. */
    const val OUTER_SHADOW_DP: Int = 24

    /** `Color.Black.copy(alpha = 0.08f)` — the brief's container shadow. */
    const val OUTER_SHADOW_ALPHA: Float = 0.08f

    /** The container shadow sits under the capsule, not around it. */
    const val OUTER_SHADOW_OFFSET_Y_DP: Float = 8f

    /** Backdrop blur radius: the page behind the glass is sampled, blurred and washed. */
    const val BACKDROP_BLUR_DP: Int = 20

    // ---- the lens -------------------------------------------------------------------------

    /** Air between the lens rim and the capsule edge, top and bottom. */
    const val LENS_INSET_DP: Float = 5f

    /** The translucent white of the glass disc. */
    const val LENS_GLASS_ALPHA: Float = 0.18f

    /** `0.36` → 1.36× under the glass, inside the requested 1.30–1.38 band. */
    const val LENS_MAGNIFICATION: Float = 0.36f

    /**
     * Text magnifies a third as much as geometry.
     *
     * A glyph is measured with the eye; a word is read. Scaling type by the full projection makes
     * it soft and pushes it out of the aperture, so the label follows the glass at
     * [LABEL_MAGNIFICATION_RATIO] of the geometric gain — it grows, visibly, but it stays type.
     */
    const val LABEL_MAGNIFICATION_RATIO: Float = 0.35f

    /** The band the brief allows, pinned so a future tweak cannot leave it. */
    const val MIN_MAGNIFICATION: Float = 1.30f
    const val MAX_MAGNIFICATION: Float = 1.38f

    /** `0x33` black under the puck: a diffuse contact shadow, never a hard drop. */
    const val AMBIENT_SHADOW_ALPHA: Float = 0.2f
    const val AMBIENT_SHADOW_BLUR_DP: Int = 12
    const val AMBIENT_SHADOW_OFFSET_Y_DP: Float = 4f

    // ---- the physics ----------------------------------------------------------------------

    /** ScaleX = 1 + min(|v| · 0.0003, 0.25) — the brief's stretch. */
    const val VELOCITY_STRETCH_PER_PX: Float = 0.0003f
    const val MAX_STRETCH: Float = 0.25f

    /** ScaleY = 1 − min(|v| · 0.00015, 0.12) — the brief's squash. */
    const val VELOCITY_SQUASH_PER_PX: Float = 0.00015f
    const val MAX_SQUASH: Float = 0.12f

    /** +8 % while the finger is down. */
    const val PRESS_GROWTH: Float = 0.08f

    /** `Spring.DampingRatioMediumBouncy` — a release has to feel like a release. */
    const val SNAP_DAMPING_RATIO: Float = 0.6f

    /** `Spring.StiffnessLow` — slow enough to be watched, quick enough to feel answered. */
    const val SNAP_STIFFNESS: Float = 400f

    /** Press and release share one spring, so the puck never twitches between states. */
    const val PRESS_DAMPING_RATIO: Float = 0.6f
    const val PRESS_STIFFNESS: Float = 400f

    // ---- the counter ----------------------------------------------------------------------

    const val BADGE_MAX_COUNT: Int = 9
    const val BADGE_MIN_SIZE_DP: Int = 16

    // ---- geometry -------------------------------------------------------------------------

    fun itemWidthPx(containerWidthPx: Float, count: Int): Float =
        if (count <= 0) containerWidthPx else containerWidthPx / count

    /**
     * The anchor of tab [index] inside the content box, in the same pixels the drag writes.
     *
     * A right-to-left layout mirrors the row, so index 0 sits at the trailing (right) edge. The
     * lens coordinate itself is always a physical, left-to-right pixel — only the mapping between
     * a pixel and a tab changes with the layout direction.
     */
    fun tabCenterX(
        index: Int,
        itemWidthPx: Float,
        containerWidthPx: Float,
        rtl: Boolean = false
    ): Float {
        val clamped = index.coerceAtLeast(0)
        val ltr = itemWidthPx * (clamped + 0.5f)
        return if (rtl) containerWidthPx - ltr else ltr
    }

    /** The leftmost legal centre: half a slot in, so the disc starts inside the first item. */
    fun minLensCenterX(itemWidthPx: Float): Float = itemWidthPx / 2f

    /** The rightmost legal centre: half a slot in from the trailing edge. */
    fun maxLensCenterX(containerWidthPx: Float, itemWidthPx: Float): Float =
        containerWidthPx - itemWidthPx / 2f

    /**
     * The mandatory boundary clamp. The lens centre can never leave the capsule: on a one-tab bar
     * both bounds collapse onto the centre, and a container narrower than one slot degenerates to
     * the middle instead of inverting.
     */
    fun clampLensCenterX(
        x: Float,
        containerWidthPx: Float,
        itemWidthPx: Float
    ): Float {
        val minX = minLensCenterX(itemWidthPx)
        val maxX = maxLensCenterX(containerWidthPx, itemWidthPx)
        if (maxX <= minX) return containerWidthPx / 2f
        return x.coerceIn(minX, maxX)
    }

    /**
     * The tab under the lens, or — on release — the nearest one. A pixel exactly on a boundary
     * belongs to the slot it is entering, which is the tab the eye is already looking at.
     */
    fun indexAt(
        lensCenterX: Float,
        itemWidthPx: Float,
        count: Int,
        containerWidthPx: Float,
        rtl: Boolean = false
    ): Int {
        if (count <= 0 || itemWidthPx <= 0f) return 0
        val logical = if (rtl) containerWidthPx - lensCenterX else lensCenterX
        return floor(logical / itemWidthPx).toInt().coerceIn(0, count - 1)
    }

    /** The radius of the glass disc: it fits the capsule with [LENS_INSET_DP] of air. */
    fun lensRadiusPx(barHeightPx: Float, insetPx: Float): Float =
        (barHeightPx / 2f - insetPx).coerceAtLeast(1f)

    // ---- optics ---------------------------------------------------------------------------

    /**
     * The projection scale of content whose anchor is [distancePx] from the lens axis.
     *
     * `1 + 0.36 · (1 - d/R)²` inside the aperture, `1` outside, never below `1`: a magnifier in
     * front of a navigation row must never shrink the row it is magnifying.
     */
    fun magnificationAt(distancePx: Float, radiusPx: Float): Float {
        if (radiusPx <= 0f) return 1f
        val t = (1f - abs(distancePx) / radiusPx).coerceIn(0f, 1f)
        return 1f + LENS_MAGNIFICATION * t * t
    }

    /** The label's share of a geometric projection: 1.36 → 1.126, never the full 1.36. */
    fun labelMagnification(projection: Float): Float =
        1f + (projection - 1f) * LABEL_MAGNIFICATION_RATIO

    /**
     * Inverse of [magnificationAt] for a target scale, clamped to the legal band: the radius the
     * projection has to be evaluated at to reach exactly [scale].
     */
    fun distanceForMagnification(scale: Float, radiusPx: Float): Float {
        val target = ((scale - 1f) / LENS_MAGNIFICATION).coerceIn(0f, 1f)
        return radiusPx * (1f - kotlin.math.sqrt(target))
    }

    // ---- physics --------------------------------------------------------------------------

    /** How much the puck is stretched and squashed at a given horizontal velocity, in px/s. */
    fun deformation(velocityPxPerSecond: Float): LensDeformation {
        val speed = abs(velocityPxPerSecond)
        return LensDeformation(
            scaleX = 1f + min(speed * VELOCITY_STRETCH_PER_PX, MAX_STRETCH),
            scaleY = 1f - min(speed * VELOCITY_SQUASH_PER_PX, MAX_SQUASH)
        )
    }

    /** 1.0 at rest, 1.08 with the finger down. */
    fun pressScale(progress: Float): Float =
        1f + PRESS_GROWTH * progress.coerceIn(0f, 1f)

    // ---- the counter ----------------------------------------------------------------------

    /** 1 … 9, then `9+`. Empty for zero: a badge of nothing is not drawn at all. */
    fun badgeText(count: Int): String = when {
        count <= 0 -> ""
        count > BADGE_MAX_COUNT -> "$BADGE_MAX_COUNT+"
        else -> count.toString()
    }
}

/**
 * The velocity squish of the puck: one horizontal growth, one vertical loss.
 *
 * Kept as a value so the draw phase can read both axes from one calculation and so the test can
 * assert the two caps independently.
 */
data class LensDeformation(
    val scaleX: Float,
    val scaleY: Float
) {
    companion object {
        val NONE: LensDeformation = LensDeformation(1f, 1f)
    }
}
