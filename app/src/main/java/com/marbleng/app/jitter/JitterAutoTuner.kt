package com.marbleng.app.jitter

/**
 * MARBLE_SMART_JITTER_V238 — the optional Auto-Tuner's decision half (pure Kotlin, default OFF).
 *
 * The tuner sweeps the tunnel MTU across 1380–1480 in [MTU_STEP] increments. For every candidate
 * the service runs the *same* jitter probe and hands the resulting [Trial] here. The winner is
 * the MTU with **no material loss first, then the lowest jitter, then the lowest p95** — size
 * only matters once the path stops dropping.
 *
 * The tuner never applies anything by itself: it returns a recommendation, and the settings page
 * applies it to the live profile only after the user confirms. Turning the override off restores
 * the adaptive default — the change is one integer in `AppSettings`, trivially reversible.
 */
object JitterAutoTuner {

    data class Trial(
        val mtu: Int,
        val jitterMs: Double,
        val p95RttMs: Double,
        val lossPercent: Double,
        val validSamples: Int
    )

    data class Recommendation(
        val mtu: Int,
        val reason: String
    )

    const val MTU_MIN = 1380
    const val MTU_MAX = 1480
    const val MTU_STEP = 20
    const val MIN_TRIAL_SAMPLES = 6

    /** Loss at or below this is noise; anything above disqualifies a candidate outright. */
    const val MAX_ACCEPTABLE_LOSS_PERCENT = 1.0

    val SWEEP: List<Int> = (MTU_MAX downTo MTU_MIN step MTU_STEP).toList()

    fun recommend(trials: List<Trial>): Recommendation? {
        val usable = trials
            .filter { it.mtu in MTU_MIN..MTU_MAX }
            .filter { it.validSamples >= MIN_TRIAL_SAMPLES }
        if (usable.isEmpty()) return null

        val healthy = usable.filter { it.lossPercent <= MAX_ACCEPTABLE_LOSS_PERCENT }
        val pool = if (healthy.isNotEmpty()) healthy else usable
        val winner = pool.minWithOrNull(
            compareBy<Trial> { it.lossPercent }
                .thenBy { it.jitterMs }
                .thenBy { it.p95RttMs }
                // Prefer the larger MTU on ties: more payload per packet, fewer interrupts.
                .thenByDescending { it.mtu }
        ) ?: return null

        val lossiest = usable.maxOf { it.lossPercent }
        val reason = when {
            healthy.isEmpty() ->
                "every candidate lost >$MAX_ACCEPTABLE_LOSS_PERCENT% • least-loss ${winner.mtu}"
            lossiest <= 0.0 ->
                "loss-free sweep • jitter picked ${winner.mtu}"
            else ->
                "candidates up to ${"%.1f".format(lossiest)}% loss • ${winner.mtu} jitter ${winner.jitterMs.toInt()} ms"
        }
        return Recommendation(mtu = winner.mtu, reason = reason)
    }

    /** Coerces a stored override into the tunable range; `null` means "no override". */
    fun sanitizeOverride(mtu: Int): Int? =
        mtu.takeIf { it in MTU_MIN..MTU_MAX }
}
