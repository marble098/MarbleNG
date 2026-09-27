package com.marbleng.app.core

/**
 * MARBLE_INTELLIGENCE_V141 — DNS storm detector.
 *
 * The log pair defines the healthy and broken regimes precisely:
 *
 *  - healthy (Netherlands-3, v7.0.4): about one attributed DoH failure every six minutes
 *    (~0.17/min) while the tunnel stays up for hours;
 *  - broken (Turkey-14, v7.0.9): six `context deadline exceeded` failures per minute against
 *    1.1.1.1/8.8.8.8/9.9.9.9, apps receiving no IPs and `rejected proxy/socks` socket closures,
 *    worst windows reaching 29/min.
 *
 * Endpoint demotion (V134) already removes a *decisively failing* resolver from the order, but a
 * storm is a property of the moment, not of one endpoint: when every resolver in the pool is
 * missing its budget at once, the correct responses are to race the pool instead of walking it
 * serially, to stop asking for the address family that doubles the failure surface, and to stop
 * the ranking engine from treating DNS-induced socket closures as node failures (which is what
 * turned a resolver problem into six forced restarts).
 *
 * The detector is a rolling window over *attributed deadline events* fed from
 * [MarbleIntelligence.recordResolverEvidence]. It arms fast (three events inside five minutes —
 * already 3.5x the healthy rate) and stands down slowly (at most one event inside ten minutes),
 * because flipping the query mode on every blip is its own instability.
 *
 * MARBLE_DNS_DOMAIN_FAULT_V196 — two corrections, both about *what counts as evidence*:
 *
 *  - failures that belong to one **name** rather than to the pool never arrive here at all. Two
 *    healthy providers missing their budget on the same domestic domain is a fact about that
 *    domain ([DnsDomainFaultPolicy]); it used to arm the storm regime and triple DNS traffic
 *    through the tunnel for ten minutes with nothing actually broken;
 *  - a **proven answer** now stands the guard down instead of only time doing it. Recovery was
 *    the one half of the loop the detector was missing: a network that came back still paid the
 *    full ten-minute stand-down window in parallel-race mode, so the remedy outlived the problem.
 */
internal class DnsStormGuard {

    private val events = ArrayDeque<Long>()

    @Synchronized
    fun recordDeadlineFailures(count: Int, nowMs: Long) {
        repeat(count.coerceIn(0, 64)) { events.addLast(nowMs) }
        trim(nowMs)
    }

    /**
     * One resolver answered for real — the recovery half of the loop.
     *
     * A proven answer does not erase the window (a single lucky lookup during a filtering burst
     * must not disarm the remedy), it *halves* it: two consecutive successes therefore take the
     * regime below the stand-down threshold within seconds, while an isolated success during a
     * genuine storm leaves the guard armed.
     */
    @Synchronized
    fun recordProvenAnswer(nowMs: Long) {
        trim(nowMs)
        if (events.isEmpty()) return
        repeat(events.size / 2 + 1) { if (events.isNotEmpty()) events.removeFirst() }
    }

    @Synchronized
    fun armMs(nowMs: Long): Long? {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STORM_ARM_WINDOW_MS }
        return if (recent >= STORM_ARM_EVENTS) nowMs else null
    }

    /** True while the storm regime is active: armed recently and not yet quiet for the stand-down window. */
    @Synchronized
    fun active(nowMs: Long): Boolean {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STAND_DOWN_WINDOW_MS }
        return recent > STORM_QUIET_EVENTS
    }

    /** Attributed deadline events per minute over the arm window, for diagnostics. */
    @Synchronized
    fun eventsPerMinute(nowMs: Long): Double {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STORM_ARM_WINDOW_MS }
        return recent / (STORM_ARM_WINDOW_MS / 60_000.0)
    }

    @Synchronized
    private fun trim(nowMs: Long) {
        val horizon = maxOf(STORM_ARM_WINDOW_MS, STAND_DOWN_WINDOW_MS)
        while (events.isNotEmpty() && nowMs - events.first() > horizon) events.removeFirst()
        while (events.size > 512) events.removeFirst()
    }

    companion object {
        const val STORM_ARM_WINDOW_MS = 5L * 60_000L
        const val STORM_ARM_EVENTS = 3
        const val STAND_DOWN_WINDOW_MS = 10L * 60_000L
        const val STORM_QUIET_EVENTS = 1
    }
}
