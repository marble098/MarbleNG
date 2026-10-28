package com.marbleng.app.jitter

/**
 * MARBLE_SMART_JITTER_V238 — when the Smart Jitter Reducer is allowed to switch, and when not.
 *
 * The switcher is the part of the module that can hurt: a wrongly eager switch tears down a live
 * tunnel. Its guard rails are therefore pure, explicit and testable:
 *
 *  - **Hysteresis** — a challenger must beat the incumbent by at least [minImproveFraction]
 *    *relative* improvement of the 0..100 score. A 1-point wobble never moves a tunnel.
 *  - **Cooldown** — at least [cooldownMs] must have passed since the previous switch, so a
 *    flapping pair of servers cannot take turns breaking the session.
 *  - **Evidence** — the challenger's own window must have [minSamples] valid samples; scores of
 *    `null` (insufficient evidence) never trigger a switch, they hold it.
 *  - **Minimum dwell** — the incumbent must have been live for [minDwellMs] before it can be
 *    replaced for *quality* reasons, so a fresh route gets time to warm up.
 */
object SwitchPolicy {

    data class State(
        val lastSwitchAtMs: Long = 0L,
        val switchCount: Long = 0L
    )

    data class Decision(
        val shouldSwitch: Boolean,
        val reason: String,
        val improvementFraction: Double
    )

    fun decide(
        incumbentScore: Double?,
        challengerScore: Double?,
        incumbentLiveSinceMs: Long,
        state: State,
        nowMs: Long,
        minImproveFraction: Double = DEFAULT_MIN_IMPROVE_FRACTION,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        minDwellMs: Long = DEFAULT_MIN_DWELL_MS,
        minSamples: Int = JitterWindow.DEFAULT_MIN_SAMPLES
    ): Decision {
        val challenger = challengerScore
            ?: return Decision(false, "challenger evidence insufficient", 0.0)
        val incumbent = incumbentScore
            ?: return Decision(false, "incumbent score unknown", 0.0)
        if (challenger < 0.0 || challenger > 100.0 || incumbent < 0.0 || incumbent > 100.0) {
            return Decision(false, "score out of range", 0.0)
        }
        if (nowMs - state.lastSwitchAtMs < cooldownMs.coerceAtLeast(0L)) {
            return Decision(false, "cooldown", 0.0)
        }
        if (incumbentLiveSinceMs > 0L && nowMs - incumbentLiveSinceMs < minDwellMs.coerceAtLeast(0L)) {
            return Decision(false, "incumbent warming up", 0.0)
        }
        if (incumbent <= 0.0) {
            // A dead incumbent (0) cannot satisfy a relative improvement; the absolute bar below
            // still applies so a single bad probe cannot move traffic.
            return if (challenger >= ABSOLUTE_MIN_CHALLENGER_SCORE) {
                Decision(true, "incumbent unusable • challenger viable", 1.0)
            } else {
                Decision(false, "challenger score too low", 0.0)
            }
        }
        val improvement = (challenger - incumbent) / incumbent
        return if (improvement >= minImproveFraction.coerceIn(0.0, 10.0)) {
            Decision(
                true,
                "challenger beats incumbent by ${(improvement * 100).toInt()}%",
                improvement
            )
        } else {
            Decision(false, "improvement below hysteresis", improvement)
        }
    }

    const val DEFAULT_MIN_IMPROVE_FRACTION = 0.20
    const val DEFAULT_COOLDOWN_MS = 180_000L
    const val DEFAULT_MIN_DWELL_MS = 90_000L
    const val ABSOLUTE_MIN_CHALLENGER_SCORE = 40.0
}
