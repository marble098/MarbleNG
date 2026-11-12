package com.marbleng.app.model

/**
 * MARBLE_HARMONY_V279 — the connected button's quiet heartbeat.
 *
 * This is paint, not layout. Both the dock's centre trigger and Home's pill read these bounded
 * values inside a draw phase; neither changes its measured footprint. Only CONNECTED breathes:
 * a dial already owns the wave loader, and an error must not look alive. Calm and reduced motion
 * keep the static halo, never an invisible or half-animated state.
 */
object ConnectPulse {
    const val MARKER: String = "MARBLE_HARMONY_V279"
    const val PERIOD_MS: Int = 2_400
    const val HALO_ALPHA: Float = 0.24f
    const val RING_ALPHA: Float = 0.32f

    fun active(phase: ConnectPhase, loops: Boolean): Boolean =
        phase == ConnectPhase.CONNECTED && loops

    /** A cosine breath has zero velocity at both ends: no flash when the loop wraps. */
    fun breath(phase: Float): Float {
        val p = phase.coerceIn(0f, 1f)
        return (0.5f - 0.5f * kotlin.math.cos(p * 2.0 * Math.PI).toFloat()).coerceIn(0f, 1f)
    }

    fun haloAlpha(breath: Float, presence: Float = 1f): Float =
        (HALO_ALPHA * (0.7f + 0.3f * breath.coerceIn(0f, 1f)) * presence.coerceIn(0f, 1f))
            .coerceIn(0f, HALO_ALPHA)

    fun ringAlpha(progress: Float, presence: Float = 1f): Float =
        (RING_ALPHA * (1f - progress.coerceIn(0f, 1f)) * presence.coerceIn(0f, 1f))
            .coerceIn(0f, RING_ALPHA)

    /** The wave stays between the knob and the fixed canvas's edge. */
    fun ringRadiusFraction(progress: Float): Float = 0.72f + 0.26f * progress.coerceIn(0f, 1f)
}
