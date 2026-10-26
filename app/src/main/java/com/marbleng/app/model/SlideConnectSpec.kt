package com.marbleng.app.model

import kotlin.math.roundToInt

/**
 * MARBLE_RTL_CONNECT_CONTROL_V234 — the arithmetic of the slidable connect control, in one place.
 *
 * The Home hero can draw the primary connect action as a slide bar instead of a button
 * (`ConnectButtonStyle.SLIDE`). It was written in physical pixels: the thumb was moved with
 * `IntOffset(+offsetPx, 0)`, the drag added `dragAmount` verbatim, and the guide chevrons pointed
 * right. In a Persian (RTL) layout the thumb starts on the **right** edge, so every one of those
 * signs was wrong — the thumb left the track, the drag pulled the wrong way, and the arrows pointed
 * backwards. The bar did not "look slightly off" in Persian: it could not be used.
 *
 * Moving the three signs here — travel, drag, release — makes the mirroring a rule instead of a
 * coincidence of how `Row` happens to lay out its children, and lets
 * `SlideConnectSpecV234Test` pin it on the host JVM without a device or a screenshot.
 */
object SlideConnectSpec {
    const val MARKER: String = "MARBLE_RTL_CONNECT_CONTROL_V234"

    /** How far the thumb must travel before the slide counts as a decision. */
    const val RELEASE_FRACTION: Float = 0.76f

    /**
     * The next fraction, from the finger's horizontal delta.
     *
     * Reads *towards* the far edge: rightwards in LTR, leftwards in RTL. A zero-width track keeps
     * the previous value instead of dividing by zero.
     */
    fun dragFraction(current: Float, dragAmountPx: Float, travelPx: Float, rtl: Boolean): Float {
        val span = travelPx
        if (span <= 0f || span.isNaN()) return current.coerceIn(0f, 1f)
        val signed = if (rtl) -dragAmountPx else dragAmountPx
        return (current + signed / span).coerceIn(0f, 1f)
    }

    /**
     * The thumb's translation from its resting edge.
     *
     * `IntOffset` is a pixel offset, not a start/end one: in RTL the resting edge is the right side
     * and the only valid direction of travel is negative x. This is the line that used to push the
     * thumb out of the track on a Persian phone.
     */
    fun thumbOffsetPx(travelPx: Float, fraction: Float, rtl: Boolean): Int {
        if (travelPx <= 0f || travelPx.isNaN()) return 0
        val distance = fraction.coerceIn(0f, 1f) * travelPx
        return (if (rtl) -distance else distance).roundToInt()
    }

    fun released(fraction: Float): Boolean = fraction >= RELEASE_FRACTION
}
