package com.marbleng.app.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * MARBLE_CRYSTAL_LENS_V248 — the geometry, optics and physics of the crystal-lens bottom bar.
 *
 * V247 projected the row through an aperture, but it left the muted base row visible *under* the
 * magnified copy (two sets of glyphs at two scales, which the eye reads as a milky smear) and it
 * sampled the page behind the capsule into a blurred layer (which smeared every edge the glass
 * covered). V248 keeps the two-pass idea and fixes the material: the base row is clipped *out* of
 * the aperture, the magnified pass is clipped *into* it, and nothing in the bar is ever rasterised.
 *
 * Every number a screenshot cannot prove lives here, on the JVM side of the boundary, so
 * [com.marbleng.app.ui.LiquidGlassBottomBarV248Test] and [LiquidGlassLensV248Test] can pin it
 * without a device:
 *
 * | question | answer |
 * |---|---|
 * | how tall is the capsule, and how much air does the lens leave? | [BAR_HEIGHT_DP], [BAR_HEIGHT_TALL_DP], [LENS_DIAMETER_DP], [breathingRoomDp] |
 * | how wide is a tab slot? | [itemWidthPx] |
 * | where is tab `i`'s anchor? | [tabCenterX] |
 * | how far may the lens centre travel? | [minLensCenterX], [maxLensCenterX], [clampLensCenterX] |
 * | which tab is under the lens? | [indexAt] |
 * | how does the puck deform while it is thrown? | [deformation] |
 * | what does a two-digit counter look like? | [badgeText] |
 *
 * ## The two sanctioned capsule heights
 *
 * The brief allows 68dp or 72dp. 68dp with a 52dp lens leaves exactly 8dp of air above and below
 * (the minimum the brief allows); 72dp leaves 10dp. The app's existing Compact/Standard/Large
 * preference keeps working by choosing between those two: small and medium are the standard 68dp
 * capsule, large is the airy 72dp one. No third height exists — a lens that touched the rim is
 * exactly the vertical cramping this revision removes.
 */
object LiquidGlassLensSpec {
    const val MARKER: String = "MARBLE_CRYSTAL_LENS_V248"

    // ---- container geometry ----------------------------------------------------------------

    /** The standard capsule. 68 − 52 = 16, i.e. 8dp of air above and below the lens. */
    const val BAR_HEIGHT_DP: Float = 68f

    /** The airy capsule. 72 − 52 = 20, i.e. 10dp of air above and below the lens. */
    const val BAR_HEIGHT_TALL_DP: Float = 72f

    /** The lens diameter, exactly as the brief specifies it. */
    const val LENS_DIAMETER_DP: Float = 52f

    /** Half the lens: the one radius every clamp and every ring is expressed with. */
    const val LENS_RADIUS_DP: Float = LENS_DIAMETER_DP / 2f

    /** Inner padding, so the first and last lens positions never touch the stadium's curve. */
    const val INNER_PADDING_DP: Float = 6f

    /** Air between the capsule and the screen edge. */
    const val HORIZONTAL_MARGIN_DP: Int = 16

    /** Air below the capsule, above the gesture bar. */
    const val BOTTOM_MARGIN_DP: Int = 16

    // ---- tab content -----------------------------------------------------------------------

    /** The tab glyph at rest. */
    const val ICON_SIZE_DP: Float = 22f

    /** The tab label at rest. */
    const val LABEL_FONT_SP: Float = 11f

    /** Air between the glyph and its label. */
    const val LABEL_GAP_DP: Float = 3f

    /** The projection scale inside the aperture: 1.30× for both glyphs and type. */
    const val MAGNIFICATION: Float = 1.3f

    /** The band the brief allows, pinned so a future tweak cannot leave it. */
    const val MIN_MAGNIFICATION: Float = 1.28f
    const val MAX_MAGNIFICATION: Float = 1.32f

    /** Inactive neutral slate. The brief allows #5F6368 or #49454F; this is the first. */
    const val INACTIVE_CONTENT_ARGB: Long = 0xFF5F6368

    /** The vibrant green the active copy is printed in when a tab has no accent of its own. */
    const val ACTIVE_CONTENT_ARGB: Long = 0xFF1E5E3A

    // ---- the acrylic capsule ---------------------------------------------------------------

    /** `Color.White.copy(alpha = 0.88f)` — frosted, never opaque. */
    const val CAPSULE_ALPHA: Float = 0.88f

    /** The diffuse outer shadow: blur and downward offset, as the brief names them. */
    const val CAPSULE_SHADOW_DP: Int = 20
    const val CAPSULE_SHADOW_OFFSET_Y_DP: Float = 6f
    const val CAPSULE_SHADOW_ALPHA: Float = 0.07f

    /** A 1dp perimeter reflection, brighter at the top and nearly gone at the bottom. */
    const val CAPSULE_RIM_WIDTH_DP: Float = 1f
    const val CAPSULE_RIM_TOP_ALPHA: Float = 0.6f
    const val CAPSULE_RIM_BOTTOM_ALPHA: Float = 0.15f

    // ---- the crystal lens ------------------------------------------------------------------

    /** The translucent core: the projected row must stay legible through it. */
    const val LENS_CORE_ALPHA: Float = 0.12f

    /** The dual specular ring. Crisp top-left, translucent flanks, soft bounce bottom-right. */
    const val LENS_RIM_WIDTH_DP: Float = 1.5f
    const val LENS_RIM_TOP_ALPHA: Float = 0.95f
    const val LENS_RIM_MID_ALPHA: Float = 0.20f
    const val LENS_RIM_BOTTOM_ALPHA: Float = 0.50f

    /** The inner caustic: transparent at the axis, a quarter-white at the curved perimeter. */
    const val LENS_CAUSTIC_ALPHA: Float = 0.25f

    /** The droplet's own contact shadow, independent of the capsule's ambient elevation. */
    const val DROPLET_SHADOW_DP: Int = 8
    const val DROPLET_SHADOW_OFFSET_Y_DP: Float = 3f
    const val DROPLET_SHADOW_ALPHA: Float = 0.14f

    // ---- motion ----------------------------------------------------------------------------

    /**
     * `stretch = 1 + min(|v| / 3000, 0.25)` — the brief's liquid stretch, restated as data so the
     * shape drawn on screen and the JVM test cannot drift apart.
     */
    const val VELOCITY_STRETCH_WINDOW_PX: Float = 3000f
    const val MAX_STRETCH: Float = 0.25f

    /** `squash = 1 − min(|v| / 6000, 0.12)` — always gentler than the stretch. */
    const val VELOCITY_SQUASH_WINDOW_PX: Float = 6000f
    const val MAX_SQUASH: Float = 0.12f

    /** `spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)`. */
    const val SNAP_DAMPING_RATIO: Float = 0.65f

    /** `Spring.StiffnessMediumLow` is 400f in androidx.compose.animation.core.Spring. */
    const val SNAP_STIFFNESS: Float = 400f

    // ---- the counter -----------------------------------------------------------------------

    const val BADGE_MAX_COUNT: Int = 9
    const val BADGE_MIN_SIZE_DP: Int = 16
    const val STATUS_DOT_SIZE_DP: Int = 8

    // ---- geometry --------------------------------------------------------------------------

    /** The app's size preference selects one of the two sanctioned capsule heights. */
    fun barHeightDp(size: DockSize): Int =
        if (size == DockSize.LARGE) BAR_HEIGHT_TALL_DP.toInt() else BAR_HEIGHT_DP.toInt()

    /** Air above (and below) the lens inside a capsule of [barHeightDp]. */
    fun breathingRoomDp(barHeightDp: Float, lensDiameterDp: Float = LENS_DIAMETER_DP): Float =
        (barHeightDp - lensDiameterDp) / 2f

    /** A slot's width, measured between the two inner paddings. */
    fun itemWidthPx(
        containerWidthPx: Float,
        paddingStartPx: Float,
        paddingEndPx: Float,
        count: Int
    ): Float {
        if (count <= 0) return 0f
        val usable = containerWidthPx - paddingStartPx - paddingEndPx
        return if (usable <= 0f) 0f else usable / count
    }

    /**
     * The anchor of tab [index], in the same physical left-to-right pixels the drag writes.
     *
     * A right-to-left row mirrors the slots but not the coordinate system: the lens always lives
     * in physical pixels, and only the pixel-to-tab mapping is mirrored.
     */
    fun tabCenterX(
        index: Int,
        itemWidthPx: Float,
        containerWidthPx: Float,
        paddingStartPx: Float,
        rtl: Boolean = false
    ): Float {
        val clamped = index.coerceAtLeast(0)
        val ltr = paddingStartPx + itemWidthPx * (clamped + 0.5f)
        return if (rtl) containerWidthPx - ltr else ltr
    }

    /**
     * The leftmost legal centre: `paddingStart + lensWidth / 2`.
     *
     * The parameter is the lens *width* (its diameter), exactly as the brief writes the formula;
     * the division by two happens here so there is one place where a radius can go wrong.
     */
    fun minLensCenterX(paddingStartPx: Float, lensWidthPx: Float): Float =
        paddingStartPx + lensWidthPx / 2f

    /** The rightmost legal centre: `containerWidth − paddingEnd − lensWidth / 2`. */
    fun maxLensCenterX(
        containerWidthPx: Float,
        paddingEndPx: Float,
        lensWidthPx: Float
    ): Float = containerWidthPx - paddingEndPx - lensWidthPx / 2f

    /**
     * The mandatory boundary clamp. A degenerate capsule narrower than the lens collapses to its
     * centre instead of inverting its bounds.
     */
    fun clampLensCenterX(
        x: Float,
        containerWidthPx: Float,
        paddingStartPx: Float,
        paddingEndPx: Float,
        lensWidthPx: Float
    ): Float {
        val minX = minLensCenterX(paddingStartPx, lensWidthPx)
        val maxX = maxLensCenterX(containerWidthPx, paddingEndPx, lensWidthPx)
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
        paddingStartPx: Float,
        rtl: Boolean = false
    ): Int {
        if (count <= 0 || itemWidthPx <= 0f) return 0
        val logical = if (rtl) containerWidthPx - lensCenterX else lensCenterX
        val raw = floor((logical - paddingStartPx) / itemWidthPx).toInt()
        return raw.coerceIn(0, count - 1)
    }

    // ---- liquid kinematics -----------------------------------------------------------------

    /**
     * The velocity deformation of the puck: stretched along the row, squashed against it.
     * Direction is irrelevant — a throw is a throw in either direction.
     */
    fun deformation(velocityPxPerSecond: Float): LensDeformation {
        val speed = abs(velocityPxPerSecond)
        val stretch = 1f + min(speed / VELOCITY_STRETCH_WINDOW_PX, MAX_STRETCH)
        val squash = 1f - min(speed / VELOCITY_SQUASH_WINDOW_PX, MAX_SQUASH)
        return LensDeformation(scaleX = stretch, scaleY = squash)
    }

    // ---- the counter -----------------------------------------------------------------------

    /** 1, 2, … 9, then "9+". Empty for zero: an empty badge is not drawn at all. */
    fun badgeText(count: Int): String = when {
        count <= 0 -> ""
        count > BADGE_MAX_COUNT -> "$BADGE_MAX_COUNT+"
        else -> count.toString()
    }
}

/** The anisotropic scale of the lens at one instant: 1,1 at rest. */
data class LensDeformation(
    val scaleX: Float,
    val scaleY: Float
) {
    companion object {
        val NONE: LensDeformation = LensDeformation(1f, 1f)
    }
}
