package com.marbleng.app.core

/**
 * MARBLE_FAILOVER_TRUST_V238 — the failure-driven switch decision, pure and host-testable.
 *
 * The Bug Finder report (P1-5) caught failovers firing at confidence 0.15 while the underlay was
 * mid-handover, then again seconds later: each unmeasured "best candidate" cost a full connect
 * and the session flapped through the library. A switch now needs both a quiet period since the
 * last one and evidence worth trusting; below the floor the session keeps its (broken) route and
 * the next cycle decides again. The two gate sites (the route-quality failure trigger in
 * [com.marbleng.app.AppRepository] and the auto-selector's `reason == "failure"` path) both read
 * this object, so the numbers have exactly one home.
 */
object FailoverTrustPolicy {

    /** No two failure-driven switches closer than this — the anti-flap quiet period. */
    const val COOLDOWN_MS = 90_000L

    /** A challenger below this evidence confidence is a guess, not a measurement. */
    const val MIN_CONFIDENCE = 0.5

    enum class Reason {
        ALLOWED,
        COOLDOWN,
        LOW_CONFIDENCE
    }

    data class Decision(
        val allowed: Boolean,
        val reason: Reason,
        /** For COOLDOWN: how long the next attempt must still wait. */
        val remainingMs: Long = 0L
    )

    /**
     * @param lastSwitchAtMs wall clock of the last applied failure-driven switch, 0 when none
     * @param confidence the challenger's evidence confidence, 0..1
     */
    fun decide(nowMs: Long, lastSwitchAtMs: Long, confidence: Double): Decision {
        if (lastSwitchAtMs > 0L && nowMs - lastSwitchAtMs < COOLDOWN_MS) {
            return Decision(
                allowed = false,
                reason = Reason.COOLDOWN,
                remainingMs = (COOLDOWN_MS - (nowMs - lastSwitchAtMs)).coerceAtLeast(0L)
            )
        }
        if (confidence < MIN_CONFIDENCE) {
            return Decision(allowed = false, reason = Reason.LOW_CONFIDENCE)
        }
        return Decision(allowed = true, reason = Reason.ALLOWED)
    }
}
