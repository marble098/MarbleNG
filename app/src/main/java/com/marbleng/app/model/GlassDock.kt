package com.marbleng.app.model

/**
 * MARBLE_GLASS_DOCK_V261 — the numbers behind the glass capsule, and the one function that places
 * the moving lens under the selected tab.
 *
 * The brief was a comparison, not a colour: *"take the bar from KernelSU — glass, a lens, and much
 * rounder and narrower than ours"*. Three of those four words are arithmetic and one of them is
 * physics, so all four live here where `GlassDockV261Test` pins them on the host JVM:
 *
 *  1. **Narrower.** [DockStudio.HORIZONTAL_MARGIN_DP] is 30dp and the capsule is capped at
 *     [DockStudio.MAX_WIDTH_FRACTION] of the window, so a phone draws a floating object and a
 *     tablet draws the same object rather than a strip.
 *  2. **Rounder.** [DockStudio.CORNER_RATIO] is 0.5, i.e. the bar is a stadium. Everything inside
 *     it still reads [DockStudio.innerRadiusDp] — one rim inside — so the corner rule is one rule.
 *  3. **A lens, not a highlight.** The selected tab does not get a tinted pill: it gets a travelling
 *     glass aperture that is drawn *over* the tab row and *under* the glyph, with a specular rim, an
 *     inner shadow where the glass meets its frame, and a soft chromatic fringe at the rim.
 *  4. **Glass.** The capsule's surface is a translucent layer over the page: two tonal stops, one
 *     top-rim highlight, one bottom sheen, a thin outer light line and a lift shadow. On a phone
 *     there is no cross-process backdrop blur available to a plain Compose app without pulling a
 *     rendering library in, so the material is built the way iOS builds it when it cannot sample:
 *     a translucent ground, a blurred *self*-shadow, a bright top rim and a dark bottom rim — the
 *     four cues the eye reads as "this is in front of that".
 *
 * Nothing here is Android: the geometry is pure, and the composable that draws it reads this file
 * instead of repeating a number.
 */
object GlassDock {
    const val MARKER: String = "MARBLE_GLASS_DOCK_V261"

    // ── the aperture ──────────────────────────────────────────────────────────────────────────

    /**
     * Air between the aperture's rim and the tab it is travelling over.
     *
     * The tab's own press target is the full slot; the glass is slightly larger than the *glyph
     * box* and slightly smaller than the slot, so the lens reads as a window onto one tab rather
     * than as a highlight painted on the row.
     */
    const val APERTURE_INSET_DP: Int = 2

    /** The radius the aperture reads: the selected capsule, not a stadium of its own. */
    fun apertureRadiusDp(size: DockSize): Float = DockStudio.innerRadiusDp(size)

    /**
     * How far the aperture's centre sits from the capsule's two edges.
     *
     * The travelling glass never touches a rim: [DockStudio.pillPaddingDp] of the capsule's own
     * inner air, so the lens is visibly *inside* the bar at every rung and every gap.
     */
    fun apertureTrackInsetDp(size: DockSize): Int =
        DockStudio.pillPaddingDp(size).coerceAtLeast(APERTURE_INSET_DP)

    /**
     * Where the aperture starts, in the row's own coordinates, for a bar of [barWidthPx] wide.
     *
     * [centrePx] is the measured centre of the selected slot inside the tab row, in the row's own
     * pixels — measured, never assumed, because a slot's width changes while its caption is open,
     * the leading and trailing wings are part of the same row, and the centre style draws a taller,
     * differently padded capsule.
     *
     * The function is pure and clamped: a row whose width is not known yet (the first frame) yields
     * the inset, and a slot at either end yields the nearest edge instead of a lens that has left
     * the capsule. The value it returns is in the *same* pixel space, so the caller converts once.
     */
    fun apertureLeftPx(
        barWidthPx: Float,
        centrePx: Float,
        apertureWidthPx: Float,
        trackInsetPx: Float
    ): Float {
        if (barWidthPx <= 0f) return trackInsetPx
        val half = (apertureWidthPx.coerceAtLeast(0f)) / 2f
        val min = trackInsetPx
        val max = (barWidthPx - half - trackInsetPx).coerceAtLeast(min)
        return (centrePx - half).coerceIn(min, max)
    }

    /**
     * The dp twin of [apertureLeftPx], for the call sites that would rather think in dp.
     *
     * [centreFraction] is the measured centre of the selected slot as a 0..1 fraction of the row.
     */
    fun apertureLeftDp(
        barWidthDp: Float,
        centreFraction: Float,
        apertureWidthDp: Float,
        trackInsetDp: Int = APERTURE_INSET_DP
    ): Float {
        if (barWidthDp <= 0f) return trackInsetDp.toFloat()
        return apertureLeftPx(
            barWidthPx = barWidthDp,
            centrePx = barWidthDp * centreFraction.coerceIn(0f, 1f),
            apertureWidthPx = apertureWidthDp,
            trackInsetPx = trackInsetDp.toFloat()
        )
    }

    /**
     * The widest an aperture may be, so it can never eat the bar it travels in.
     *
     * The opening tab takes 1.9x the weight of its neighbours, so on a four-tab bar the selected
     * slot is already about 42 % of the row. Half the row is the hard ceiling: past it the lens
     * would be reading as a second bar laid inside the first.
     */
    fun apertureMaxWidthDp(barWidthDp: Float): Float =
        (barWidthDp * 0.5f).coerceAtLeast(0f)

    /**
     * The tab caption's opacity *inside* the lens: the glass magnifies what is under it, so the
     * selected tab's word is printed at full strength while the neighbours stay secondary.
     */
    const val LENS_INK_ALPHA: Float = 1f

    // ── the material ─────────────────────────────────────────────────────────────────────────

    /**
     * The capsule's own translucency.
     *
     * 0.72 of the tonal ground is the point where the page still tints the bar (so the bar belongs
     * to the screen) while the glyphs keep their contrast on every surface the app can draw: the
     * navy default, the four palettes, AMOLED black, and the flat interface.
     */
    const val SURFACE_ALPHA: Float = 0.72f

    /** The aperture is a little clearer than the bar — it is the window, not the frame. */
    const val LENS_ALPHA: Float = 0.16f

    /** The specular stroke on the top rim. */
    const val RIM_ALPHA: Float = 0.30f

    /** The dark rim under the bottom edge, which is what makes a translucent slab read as glass. */
    const val FLOOR_ALPHA: Float = 0.16f

    /** The lift: one wide, soft shadow under the whole capsule. */
    const val AMBIENT_SHADOW_DP: Int = 22

    /** One contact shadow, tight, so the capsule has an edge on a light page. */
    const val CONTACT_SHADOW_DP: Int = 6

    /**
     * The refracting band along the top edge of the lens, in dp: light entering glass bends, and a
     * 7dp band of the *inverse* sheen is what a lens looks like at 44–66dp wide. It scales with the
     * aperture rather than being a constant, so a Grand bar's lens is not a Slim one's band stretched.
     */
    fun refractionBandDp(apertureWidthDp: Float): Float =
        (apertureWidthDp * 0.14f).coerceIn(4f, 12f)

    /** The cold end of the chromatic fringe — the blue that a glass edge throws above itself. */
    val FRINGE_COLD: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF86C8FF)

    /** The warm end — the pink it throws below. Dispersion is cold above, warm below, always. */
    val FRINGE_WARM: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFFFB0D0)

    /** The chromatic fringe at the rim: 3.5 % of the width, clamped so it is a fringe and not a rainbow. */
    fun fringeDp(apertureWidthDp: Float): Float =
        (apertureWidthDp * 0.035f).coerceIn(1f, 3f)

    /**
     * How long the lens takes to travel between two tabs, in milliseconds.
     *
     * The dock's own spring is used for the movement; this is the number the *test* pins and the
     * number the reduced-motion path short-circuits: a reader who removed animations gets the lens
     * at its destination in one frame.
     */
    const val TRAVEL_MS: Int = 380

    /**
     * The drag's own rubber band: how far the lens may be pulled past its edge before it stops,
     * as a fraction of the aperture's width. The drag is a gesture, not a setting — the lens
     * follows the finger and springs home to the nearest tab.
     */
    const val DRAG_OVERSHOOT_FRACTION: Float = 0.35f

    /** The glass is the bar's own corner rule pulled in by the rim the aperture sits behind. */
    fun lensRadiusDp(size: DockSize): Float =
        (apertureRadiusDp(size) - DockStudio.INNER_CORNER_INSET_DP / 2f)
            .coerceAtLeast(DockStudio.INNER_CORNER_INSET_DP.toFloat())
}
