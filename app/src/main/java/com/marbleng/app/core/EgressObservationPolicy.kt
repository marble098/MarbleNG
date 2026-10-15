package com.marbleng.app.core

object EgressObservationPolicy {
    enum class Verdict {
        HEALTHY,

        INCONCLUSIVE,

        ROUTE_SUSPECT
    }

    data class State(
        val observations: Int = 0,
        val consecutiveDnsSuspicions: Int = 0,
        val tuneRequested: Boolean = false,
        val rearmAttempts: Int = 0
    )

    data class Decision(
        val state: State,
        val verdict: Verdict,
        val requestFamilyTune: Boolean,
        val rearmDelayMs: Long,
        val reason: String
    )

    const val CONFIRMATIONS = 2

    val REARM_SCHEDULE_MS = longArrayOf(30_000L, 60_000L, 120_000L)

    fun observe(
        state: State,
        domainHealthy: Boolean,
        literalHealthy: Boolean
    ): Decision {
        val observations = state.observations + 1

        if (domainHealthy) {
            return Decision(
                state = State(
                    observations = observations,
                    consecutiveDnsSuspicions = 0,
                    tuneRequested = state.tuneRequested,
                    rearmAttempts = 0
                ),
                verdict = Verdict.HEALTHY,
                requestFamilyTune = false,
                rearmDelayMs = 0L,
                reason = "domain-https-verified"
            )
        }

        if (!literalHealthy) {
            return Decision(
                state = state.copy(observations = observations, consecutiveDnsSuspicions = 0),
                verdict = Verdict.ROUTE_SUSPECT,
                requestFamilyTune = false,
                rearmDelayMs = rearmDelay(state.rearmAttempts),
                reason = "no-egress-evidence"
            )
        }

        val suspicions = state.consecutiveDnsSuspicions + 1
        val confirmed = suspicions >= CONFIRMATIONS && !state.tuneRequested
        return Decision(
            state = state.copy(
                observations = observations,
                consecutiveDnsSuspicions = suspicions,
                tuneRequested = state.tuneRequested || confirmed,
                rearmAttempts = state.rearmAttempts + 1
            ),
            verdict = Verdict.INCONCLUSIVE,
            requestFamilyTune = confirmed,
            rearmDelayMs = rearmDelay(state.rearmAttempts + 1),
            reason = if (confirmed) {
                "literal-only-confirmed-$suspicions"
            } else {
                "literal-only-suspected-$suspicions"
            }
        )
    }

    fun rearmDelay(attempt: Int): Long {
        if (attempt < 0) return 0L
        return REARM_SCHEDULE_MS.getOrElse(attempt - 1) { 0L }
    }
}
