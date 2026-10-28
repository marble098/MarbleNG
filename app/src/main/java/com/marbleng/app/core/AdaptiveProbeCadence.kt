package com.marbleng.app.core

/**
 * MARBLE_PROBE_CADENCE_V238 — how often the live route probe (verified HTTPS RTT) may run.
 *
 * The Bug Finder report (v11.2.3, P0-2) measured the old fixed cadence taking a 12-sample HTTPS
 * burst roughly every 9–10 seconds on a *calm* network — heavy for the radio and the battery —
 * while a degraded network still needs the fast cadence. So the cadence breathes with the same
 * link evidence the probe itself produces:
 *
 *  - a streak of calm cycles (low jitter, no loss, healthy success) stretches the interval by
 *    [GROWTH_PER_CALM_CYCLE] up to [MAX_INTERVAL_TICKS];
 *  - any unsettled cycle returns to the fast interval;
 *  - the fast floor is the degraded cadence the jitter-control path already uses, so recovery
 *    speed is never slower than before.
 *
 * Pure and tick-based (the monitor loop's tick is ~1 s), so the unit test pins it on the host
 * JVM without Android or a clock.
 */
object AdaptiveProbeCadence {

    const val GROWTH_PER_CALM_CYCLE = 1.5
    const val MAX_CALM_STREAK = 5

    fun intervalTicks(
        calmStreak: Int,
        normalTicks: Int,
        degradedTicks: Int,
        jitterControlActive: Boolean
    ): Int {
        val fast = degradedTicks.coerceAtLeast(1)
        val normal = normalTicks.coerceAtLeast(fast)
        val floor = if (jitterControlActive) fast else normal
        val effectiveStreak = calmStreak.coerceIn(0, MAX_CALM_STREAK)
        if (effectiveStreak <= 0) return floor
        val stretched = floor * Math.pow(GROWTH_PER_CALM_CYCLE, effectiveStreak.toDouble())
        return stretched.toInt().coerceAtMost(MAX_INTERVAL_TICKS).coerceAtLeast(fast)
    }

    fun nextStreak(
        calm: Boolean,
        currentStreak: Int,
        jitterMs: Int,
        lossPercent: Int,
        successPercent: Int
    ): Int {
        val linkCalm = jitterMs in 0..JITTER_CALM_MS &&
            lossPercent <= 0 &&
            successPercent >= SUCCESS_CALM_PERCENT
        return if (calm && linkCalm) (currentStreak + 1).coerceAtMost(MAX_CALM_STREAK) else 0
    }

    const val JITTER_CALM_MS = 12
    const val SUCCESS_CALM_PERCENT = 95
    const val MAX_INTERVAL_TICKS = 120
}
