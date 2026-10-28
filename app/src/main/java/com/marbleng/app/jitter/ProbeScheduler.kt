package com.marbleng.app.jitter

import kotlin.math.roundToLong

/**
 * MARBLE_SMART_JITTER_V238 — the adaptive probe cadence.
 *
 * The probe is a real HTTPS request through the live tunnel: it costs data and a radio wake. The
 * report (v11.2.3, P0-2) called the fixed ~10 s cadence out as heavy for a calm network, so the
 * interval breathes:
 *
 *  - a streak of calm probes (low jitter, no loss) multiplies the base interval by
 *    [CALM_GROWTH] per streak up to [maxIntervalMs];
 *  - one unsettled probe collapses the streak and returns to the base interval;
 *  - Doze or battery saver multiplies the result ([powerMultiplier] ≥ 1) — measurement is the
 *    first thing to give way when the phone is conserving;
 *  - the mode profile scales the base (Aggressive probes twice as often as Balanced).
 *
 * Pure function of (streak, power, mode, user base): host-JVM testable, no clock inside.
 */
object ProbeScheduler {

    const val CALM_GROWTH = 1.5
    const val MAX_CALM_STREAK = 6
    const val MAX_POWER_MULTIPLIER = 4.0

    const val MIN_INTERVAL_MS = 1_000L
    const val DEFAULT_MAX_INTERVAL_MS = 60_000L

    fun nextDelayMs(
        baseIntervalMs: Long,
        calmStreak: Int,
        mode: JitterMode,
        powerMultiplier: Double = 1.0,
        maxIntervalMs: Long = DEFAULT_MAX_INTERVAL_MS
    ): Long {
        if (mode == JitterMode.OFF) return 0L
        val profile = JitterModeProfile.of(mode)
        val base = baseIntervalMs.coerceIn(MIN_INTERVAL_MS, maxIntervalMs)
        val scaled = (base * profile.probeIntervalMultiplier).coerceAtLeast(MIN_INTERVAL_MS.toDouble())

        val effectiveStreak = calmStreak.coerceIn(0, MAX_CALM_STREAK)
        val grown = scaled * Math.pow(CALM_GROWTH, effectiveStreak.toDouble())

        val capped = grown.coerceAtMost(maxIntervalMs.coerceAtLeast(MIN_INTERVAL_MS).toDouble())
        val power = powerMultiplier.coerceIn(1.0, MAX_POWER_MULTIPLIER)
        return (capped * power).roundToLong().coerceAtLeast(MIN_INTERVAL_MS)
    }

    /**
     * Whether one probe outcome keeps the calm streak alive. [jitterTriggerMs] comes from the
     * mode profile's scaled trigger; loss must be exactly zero for the network to count as calm.
     */
    fun isCalm(jitterMs: Double, lossPercent: Double, jitterTriggerMs: Double): Boolean =
        jitterMs <= jitterTriggerMs.coerceAtLeast(1.0) && lossPercent <= 0.0

    fun nextStreak(wasCalm: Boolean, currentStreak: Int): Int =
        if (wasCalm) (currentStreak + 1).coerceAtMost(MAX_CALM_STREAK) else 0
}
