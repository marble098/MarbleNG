package com.marbleng.app.model

import kotlin.math.abs

/**
 * MARBLE_MAP_MOTION_V249 — the tabs are places on one map, not four separate pages.
 *
 * Switching tabs used to swap content in a single frame: the new page simply *was*. The ask was
 * that the app move «مثل یک مپ یا نقشه» — like a map — which means the direction of travel is the
 * meaning of the gesture: a tab to the right of the one you are on arrives from the right, the page
 * you leave departs to the left, and both drift only part of the way while they scale a little, so
 * the two pages are on screen together for the length of the flight. That is a pan, and a pan is
 * readable: the user can see where they came from and where they landed.
 *
 * Everything here is a plain number so `MapMotionV249Test` can pin the geometry without a device:
 *
 *  - [direction] is `-1`, `0` or `+1` from two tab indexes — never from a tab *name*, so reordering
 *    the dock cannot silently reverse the motion;
 *  - [mirrored] flips it for a right-to-left interface, because in Persian the fourth tab is on the
 *    left and a pan that ignores the layout direction reads as moving backwards;
 *  - [distance] scales the flight: crossing one tab is a short pan, crossing three is a longer one,
 *    capped so a long jump never feels slow;
 *  - the arriving page drifts less than the departing one ([ARRIVING_DRIFT_FRACTION] vs
 *    [DEPARTING_DRIFT_FRACTION]), which is what keeps the pair looking like one surface moving
 *    instead of two surfaces trading places.
 */
object MapMotion {

    const val MARKER: String = "MARBLE_MAP_MOTION_V249"

    /** How far the arriving page starts away from its resting place, as a fraction of its width. */
    const val ARRIVING_DRIFT_FRACTION: Float = 0.22f

    /** How far the departing page travels out, as a fraction of its width. A pan, not an eviction. */
    const val DEPARTING_DRIFT_FRACTION: Float = 0.30f

    /** The departing page recedes a little, the way a map tile does under a pinch. */
    const val DEPARTING_SCALE: Float = 0.93f

    /** The arriving page settles up from slightly small, so arrival has somewhere to come from. */
    const val ARRIVING_SCALE: Float = 0.97f

    /** A page in flight never drops below this opacity: a map dims, it does not blink out. */
    const val FLIGHT_ALPHA_FLOOR: Float = 0.25f

    /** Opening a detail page is a zoom into the map, not a pan across it. */
    const val DETAIL_ENTER_SCALE: Float = 0.94f
    const val DETAIL_EXIT_SCALE: Float = 0.96f

    const val BASE_DURATION_MS: Int = 380
    const val PER_STEP_DURATION_MS: Int = 40
    const val MAX_DURATION_MS: Int = 520
    const val MIN_DURATION_MS: Int = 260

    /** `-1` pan left, `+1` pan right, `0` no travel (same tab, or an unknown pair). */
    fun direction(fromIndex: Int, toIndex: Int): Int = when {
        fromIndex < 0 || toIndex < 0 -> 0
        toIndex > fromIndex -> 1
        toIndex < fromIndex -> -1
        else -> 0
    }

    /** How many tabs the flight crosses. */
    fun distance(fromIndex: Int, toIndex: Int): Int =
        if (fromIndex < 0 || toIndex < 0) 0 else abs(toIndex - fromIndex)

    /**
     * A right-to-left interface mirrors the pan: the same two indexes, the opposite travel. The
     * caller passes its own layout direction; this file has no Compose in it.
     */
    fun mirrored(direction: Int, rightToLeft: Boolean): Int =
        if (rightToLeft) -direction.coerceIn(-1, 1) else direction.coerceIn(-1, 1)

    /** The arriving page's start offset, as a signed fraction of its own width. */
    fun arrivingOffsetFraction(direction: Int): Float =
        direction.coerceIn(-1, 1) * ARRIVING_DRIFT_FRACTION

    /**
     * The departing page's end offset, as a signed fraction of its own width.
     *
     * The sign is the opposite of [arrivingOffsetFraction] and that is the whole point: on a pan to
     * the right the new page comes in from the right and the old one leaves to the left, so the two
     * move *together* across the screen. Same-signed offsets would make them trade places — one
     * sliding out of the way while the other arrives from nowhere — which is a slideshow, not a map.
     */
    fun departingOffsetFraction(direction: Int): Float =
        -direction.coerceIn(-1, 1) * DEPARTING_DRIFT_FRACTION

    /**
     * The flight's length. One step is the base; every further step adds a little, because a longer
     * pan covering the same distance in the same time reads as a jerk — but the cap keeps a
     * three-tab jump from feeling like a slow dissolve.
     */
    fun durationMs(distance: Int): Int {
        val steps = distance.coerceAtLeast(0)
        if (steps <= 1) return BASE_DURATION_MS
        return (BASE_DURATION_MS + (steps - 1) * PER_STEP_DURATION_MS)
            .coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    /**
     * A pan with nowhere to go (the same tab pressed again) must not animate: no drift, no scale,
     * no fade — otherwise every redundant tap plays a motion that says "something changed".
     */
    fun travels(direction: Int): Boolean = direction != 0
}
