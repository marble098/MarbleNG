package com.marbleng.app.core

/**
 * MARBLE_EGRESS_EVIDENCE_V133
 *
 * Why `EGRESS startup-observation-inconclusive` kept requesting a family tune that could never
 * succeed.
 *
 * The startup observation compared two probes that were not measuring the same thing:
 *
 *  - `domainHttps` performed a **full HTTPS GET** (TCP + TLS + request + body) with a 3500 ms budget;
 *  - `literalIpHttps` measured only the **TLS first byte** with a 1500 ms budget.
 *
 * On the 1126 ms link in the runtime log the GET needs roughly three round trips (~3.4 s) and the
 * first-byte probe needs two (~2.3 s), so the domain probe failed and the literal probe passed —
 * not because DNS was broken but because the two probes had different costs and different budgets.
 * The code read that asymmetry as "literal IP works, domains do not" and immediately raised
 * `TURBO startup-family-tune-requested`, sending the acceleration engine off to re-measure address
 * families on a route whose only real problem was a probe budget. The observation was also
 * one-shot: a single inconclusive reading at t+20 s was the last word for the whole session.
 *
 * This policy makes the interpretation honest:
 *
 *  - the caller must probe both targets the *same* way with the *same* RTT-derived budget, so a
 *    disagreement is real evidence (see `MarbleVpnService.probeEgressTarget`);
 *  - one inconclusive reading is never acted on. A family tune is requested only after
 *    [CONFIRMATIONS] consecutive readings agree, which is the difference between "DNS looks odd
 *    once during a filtering window" and "this route cannot resolve domains";
 *  - an inconclusive observation re-arms itself on a bounded schedule instead of going silent, so
 *    a route that recovers is re-checked and a route that stays broken is reported consistently.
 */
object EgressObservationPolicy {

    enum class Verdict {
        /** A domain-name HTTPS transaction through the tunnel succeeded. */
        HEALTHY,

        /** Domains failed while literal IPs worked: DNS-side suspicion, not yet proven. */
        INCONCLUSIVE,

        /** Neither probe answered: the route itself is suspect, not the resolver. */
        ROUTE_SUSPECT
    }

    data class State(
        val observations: Int = 0,
        /** Consecutive readings where domains failed but literal IPs answered. */
        val consecutiveDnsSuspicions: Int = 0,
        /** A family tune has already been requested for this session. */
        val tuneRequested: Boolean = false,
        /** Bounded re-arm counter so a permanently inconclusive route cannot loop forever. */
        val rearmAttempts: Int = 0
    )

    data class Decision(
        val state: State,
        val verdict: Verdict,
        val requestFamilyTune: Boolean,
        /** Delay before the next observation; 0 means "do not re-arm". */
        val rearmDelayMs: Long,
        val reason: String
    )

    /** Consecutive agreeing readings required before a family tune is requested. */
    const val CONFIRMATIONS = 2

    /** Bounded re-arm schedule for an inconclusive observation. */
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
            // Nothing answered. This is the route-failure path's problem, not a DNS verdict, and it
            // must never be reported as "DNS inconclusive".
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

    /** Bounded re-arm delay; 0 once the schedule is exhausted. */
    fun rearmDelay(attempt: Int): Long {
        if (attempt < 0) return 0L
        return REARM_SCHEDULE_MS.getOrElse(attempt - 1) { 0L }
    }
}
