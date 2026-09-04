package com.marbleng.app.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MARBLE_LINK_DEADLINE_V133
 *
 * One source of truth for "how long may this network operation take".
 *
 * The runtime log that motivated this file showed a healthy Xray core whose tunnel RTT reached
 * 1126 ms, while every deadline in the stack was a hardcoded constant sized for a fast link:
 *
 *  - Xray's encrypted DNS servers were given 1350 ms (primary) / 1650 ms (fallback) even though
 *    those queries are routed *through the tunnel* (`inboundTag=xgc-dns` → the proxy outbound), so
 *    a single DoH transaction needs a TCP handshake, a TLS handshake and the query itself — three
 *    round trips of ~1.1 s. Sixteen `DoH deadline` errors with **zero** TLS/cert errors is exactly
 *    that signature: the resolvers answered, the budget had already expired.
 *  - the Home connection-ping race was cut at 2600 ms with per-probe budgets of 1800–2000 ms, so
 *    only 1–2 of the 5 probes ever reported (`responders=1`, `responders=2`) and the readout was
 *    the fastest of a truncated sample instead of an honest median.
 *
 * A deadline that ignores the measured link is not a policy, it is a guess. Every budget derived
 * here is `hops × tail-RTT + jitter/loss headroom`, clamped to a floor and a ceiling:
 *
 *  - the **floor** is the constant the code used before, so a fast link and every caller without
 *    evidence behaves exactly as it did (no regression, deterministic unit tests);
 *  - the **ceiling** keeps a dead path bounded, so a broken resolver can never stall a lookup for
 *    longer than the official XTLS Xray-examples budget (10 s).
 *
 * Evidence is optional by design: [LinkEvidence.UNKNOWN] reproduces the legacy constants, which is
 * what a first-ever connect or a caller that has not measured yet must get.
 */
data class LinkEvidence(
    /** Median / EWMA verified round-trip time of the path in milliseconds. 0 = unmeasured. */
    val rttMs: Double = 0.0,
    /** Tail (p95) round-trip time; deadlines must survive the tail, not the median. */
    val tailRttMs: Double = 0.0,
    /** IPDV / jitter in milliseconds. */
    val jitterMs: Double = 0.0,
    /** Observed packet loss percentage, 0–100. */
    val lossPercent: Double = 0.0,
    /** Number of measurements behind the numbers above. */
    val samples: Int = 0
) {
    /** True only when there is at least one real measurement to derive a budget from. */
    val known: Boolean
        get() = samples > 0 && rttMs > 0.0

    /** The RTT a deadline must survive: the slower of median and tail. */
    val effectiveRttMs: Double
        get() = max(rttMs, tailRttMs)

    companion object {
        /** No measurement available: every derived budget falls back to its floor. */
        val UNKNOWN = LinkEvidence()

        /**
         * Evidence from the persistent per-node health store. Values outside the plausible range
         * are ignored rather than trusted, because the store keeps a 9999 ms sentinel for "never
         * measured" and feeding that into a deadline would produce a nonsense budget.
         */
        fun fromHealth(record: NodeHealthRecord?): LinkEvidence {
            if (record == null || record.samples <= 0) return UNKNOWN
            val latency = record.latencyEwma.takeIf { it in 1.0..8_000.0 } ?: return UNKNOWN
            return LinkEvidence(
                rttMs = latency,
                // The store keeps an EWMA, not a distribution: derive a conservative tail from the
                // measured jitter instead of pretending a p95 was recorded.
                tailRttMs = latency + record.jitterEwma.coerceIn(0.0, 2_000.0) * 2.0,
                jitterMs = record.jitterEwma.coerceIn(0.0, 2_000.0),
                // Only a real success measurement may become a loss figure. `successEwma` defaults to
                // 0.0 for "never measured", and reading that as 100% loss inflated every deadline on
                // the node's first session — a guess dressed up as evidence.
                lossPercent = if (record.successEwma in 1.0..100.0) {
                    100.0 - record.successEwma
                } else {
                    0.0
                },
                samples = record.samples
            )
        }
    }
}

/**
 * Derives every network deadline from measured link evidence.
 *
 * Pure and side-effect free: the same evidence always yields the same budget, which is what makes
 * the Xray config a pure function of (profile, port, settings, evidence) and keeps it unit-testable
 * without Android.
 */
object LinkDeadlinePolicy {

    // Legacy floors. They are the exact constants the code used before this policy existed, so an
    // unmeasured link (and every existing test) behaves identically.
    const val PRIMARY_DNS_FLOOR_MS = 1_350L
    const val SECONDARY_DNS_FLOOR_MS = 1_650L
    const val SECONDARY_DNS_STEP_MS = 250L

    /**
     * Ceiling for one encrypted DNS server. The official XTLS Xray-examples
     * `serverless_for_Iran.jsonc` gives its single DoH server 10 s, so Marble never waits longer
     * for one resolver than the upstream reference configuration does.
     */
    const val MAX_DNS_TIMEOUT_MS = 10_000L

    /**
     * Freedom profiles fragment the first write of every TCP stream into 1-byte packets with 4 ms
     * pacing (up to `maxSplit` 517), so a DoH TLS handshake alone can need seconds before the first
     * response byte. Those budgets stay on the upstream XTLS schedule and are not RTT-derived: the
     * fragmentation pacing, not the link, dominates.
     */
    const val FRAGMENTED_DNS_BASE_MS = 8_000L
    const val FRAGMENTED_DNS_STEP_MS = 1_000L

    /** Round trips in one cold HTTPS/DoH transaction: TCP + TLS 1.3 + request/response. */
    const val TLS_TRANSACTION_HOPS = 3

    /** Round trips in a full request that also reads a body (the Home ping probes). */
    const val HTTPS_PROBE_HOPS = 4

    /**
     * Jitter multiplier. Three IPDV samples of headroom covers a reordering burst without
     * stretching every deadline into a stall detector.
     */
    const val JITTER_HEADROOM_MULTIPLIER = 3.0

    /**
     * Fraction of the tail RTT added per percent of loss: a lost packet costs one retransmission
     * round trip, and this is the expected extra time for the observed loss rate.
     */
    const val LOSS_HEADROOM_FACTOR = 0.05

    /**
     * Happy Eyeballs connection-attempt delay as a fraction of the measured RTT. RFC 8305 §5 asks
     * for a delay that gives the preferred family a real chance to win; a fixed 60 ms on a 1126 ms
     * link starts the second dial before the first SYN-ACK can possibly arrive, so both families
     * are dialled on every connection and the race can never be won.
     */
    const val RACE_DELAY_RTT_FRACTION = 0.25
    const val MIN_RACE_DELAY_MS = AddressFamilyPolicy.MIN_TRY_DELAY_MS
    const val MAX_RACE_DELAY_MS = AddressFamilyPolicy.MAX_TRY_DELAY_MS

    /** Scheduler slack added to a per-probe timeout to size a concurrent probe batch. */
    const val PROBE_BATCH_SLACK_MS = 600L

    /**
     * Budget for `hops` round trips on the measured link.
     *
     * Returns [floorMs] when there is no evidence, so unmeasured paths keep the legacy behaviour.
     */
    fun transactionMs(
        evidence: LinkEvidence,
        hops: Int,
        floorMs: Long,
        ceilingMs: Long
    ): Long {
        require(hops > 0) { "hops must be positive" }
        require(ceilingMs >= floorMs) { "ceiling must not be below the floor" }
        if (!evidence.known) return floorMs

        val tail = evidence.effectiveRttMs
        if (tail <= 0.0) return floorMs

        val jitterHeadroom = evidence.jitterMs.coerceAtLeast(0.0) * JITTER_HEADROOM_MULTIPLIER
        val lossHeadroom = tail * LOSS_HEADROOM_FACTOR * evidence.lossPercent.coerceIn(0.0, 100.0)
        val derived = ceil(hops * tail + jitterHeadroom + lossHeadroom).toLong()
        return derived.coerceIn(floorMs, ceilingMs)
    }

    /**
     * `timeoutMs` for one encrypted DNS server in the Xray config.
     *
     * @param index 0 for the primary server, 1+ for each failover server. Each extra server keeps
     *   the legacy +250 ms so a rotating provider still gets progressively more room.
     * @param fragmented true for the Freedom fragment chain, which keeps the upstream XTLS budget.
     */
    fun dnsServerTimeoutMs(
        evidence: LinkEvidence,
        index: Int,
        fragmented: Boolean
    ): Long {
        val step = index.coerceAtLeast(0)
        if (fragmented) {
            return (FRAGMENTED_DNS_BASE_MS + step * FRAGMENTED_DNS_STEP_MS)
                .coerceAtMost(MAX_DNS_TIMEOUT_MS * 2L)
        }
        val floor = if (step == 0) {
            PRIMARY_DNS_FLOOR_MS
        } else {
            SECONDARY_DNS_FLOOR_MS + (step - 1) * SECONDARY_DNS_STEP_MS
        }
        return transactionMs(evidence, TLS_TRANSACTION_HOPS, floor, MAX_DNS_TIMEOUT_MS)
    }

    /** Per-probe timeout for a full HTTPS request measured through the tunnel. */
    fun httpsProbeTimeoutMs(
        evidence: LinkEvidence,
        floorMs: Long = 1_800L,
        ceilingMs: Long = 8_000L
    ): Long = transactionMs(evidence, HTTPS_PROBE_HOPS, floorMs, ceilingMs)

    /**
     * Wall-clock budget for a *batch* of concurrent probes.
     *
     * The batch budget must cover a single probe plus the scheduler slack of running them
     * together; sizing it independently of the per-probe timeout is what truncated the Home ping
     * race to 1–2 responders on a slow link.
     */
    fun probeBatchBudgetMs(
        perProbeTimeoutMs: Long,
        floorMs: Long = 2_600L,
        ceilingMs: Long = 9_000L
    ): Long = (perProbeTimeoutMs + PROBE_BATCH_SLACK_MS).coerceIn(floorMs, ceilingMs)

    /**
     * Happy Eyeballs connection-attempt delay scaled to the measured link.
     *
     * Never below the user's configured value and never outside the engine's accepted range, so
     * "IPv6 on" still means "IPv6 first" — the delay only stops the second dial from starting
     * before the first one could possibly have answered.
     */
    fun raceTryDelayMs(evidence: LinkEvidence, configuredMs: Int): Int {
        val scaled = if (evidence.known) {
            (evidence.effectiveRttMs * RACE_DELAY_RTT_FRACTION).roundToInt()
        } else {
            0
        }
        return maxOf(configuredMs, scaled).coerceIn(MIN_RACE_DELAY_MS, MAX_RACE_DELAY_MS)
    }
}
