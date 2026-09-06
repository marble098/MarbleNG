package com.marbleng.app.core

import kotlin.math.abs

/**
 * MARBLE_IRAN_AWARE_PING_L3 — Layer 3: Causal Attribution Engine.
 *
 * ## Why attribution exists
 *
 * Layers 0–2 answer "is this endpoint healthy right now". A national firewall turns that
 * question into "is the *endpoint* the problem, or is the *country's filter* the problem?"
 * When the same three DNS-provider targets, the same exit IP and the same tunneled route
 * degrade together across ASNs, blaming the server is not just wrong, it is actively
 * destructive: the ranking reorders the user onto another server, another 20-minute
 * observation window starts, and the user keeps failing while the app "tries" servers that are
 * as good as the one it left.
 *
 * ## The rule set (each verdict must be evidence-driven, never guessed)
 *
 *  - [AttributedCause.NATIONAL_FILTERING_EVENT] — more than 70% of *independent* servers
 *    dropped simultaneously, across different IPs and different ASNs. This is the only cause
 *    that freezes ranking (nothing to rank) and shows the banner.
 *  - [AttributedCause.PROTOCOL_TARGETED_THROTTLE] — the drop is correlated with the transport
 *    fingerprint: one transport family is uniformly degraded while another on the same
 *    servers is not. This is the counterexample to "the server is bad".
 *  - [AttributedCause.CARRIER_SPECIFIC_ISSUE] — degradation clusters on one underlay carrier
 *    while other carriers remain healthy; the fix is an underlay change, not a server change.
 *  - [AttributedCause.SERVER_SIDE_DEGRADATION] — a distinct subset of servers degrades while
 *    the rest stays healthy; the fix is to move off those servers.
 *  - [AttributedCause.INJECTED_RESET] — Layer 0 time-to-RST and/or Layer 2 reset-after-volume
 *    evidence is above threshold on the degraded path; the filter is actively killing streams.
 *  - [AttributedCause.NORMAL_CONGESTION] — degradation exists but correlates with nothing:
 *    no mass drop, no transport bias, no carrier bias, no injection. Changing the server would
 *    change nothing; the honest answer is that the path is simply slow right now.
 */
object CausalAttribution {

    enum class AttributedCause {
        NATIONAL_FILTERING_EVENT,
        PROTOCOL_TARGETED_THROTTLE,
        CARRIER_SPECIFIC_ISSUE,
        SERVER_SIDE_DEGRADATION,
        INJECTED_RESET,
        NORMAL_CONGESTION
    }

    /** One server's observation window, as seen by the attribution engine. */
    data class ServerObservation(
        val profileId: String,
        /** Exit/remote IP of the server (for ASN/carrier correlation). */
        val ip: String = "",
        /** ASN when known, e.g. `AS-51167`; blank when unknown. */
        val asn: String = "",
        /** Underlay carrier/ISP context when known; blank when unknown. */
        val carrier: String = "",
        /** Transport fingerprint (see [ProtocolFingerprintAwareVerifier.transportTypeOf]). */
        val transportType: String = "",
        /** was the server's last measurement a hard drop (success 0) or a soft degrade? */
        val dropped: Boolean = false,
        /** current baseline ratio (1.0 = same as 15 min ago). */
        val baselineDeltaRatio: Double = 0.0,
        val injectedResetSuspected: Boolean = false,
        val sawtoothConfidence: Double = 0.0,
        /** Set when this server had 3 spaced healthy samples before the event. */
        val priorEvidence: Boolean = false,
        /** The event window in which the drop happened (all observations are within it). */
        val windowStartMs: Long = 0L,
        val windowEndMs: Long = 0L
    )

    data class AttributionResult(
        val cause: AttributedCause,
        /** 0..1 confidence in the attributed cause. */
        val confidence: Double,
        val affected: Int,
        val total: Int,
        /** Fraction of measured servers that dropped in the same window. */
        val simultaneousDropFraction: Double,
        val reasons: List<String>
    ) {
        /** The two causes that freeze ranking. */
        val rankingFreeze: Boolean
            get() = cause == AttributedCause.NATIONAL_FILTERING_EVENT
    }

    /** More than this fraction of independent servers dropping together = national event. */
    const val NATIONAL_DROP_FRACTION = 0.70

    /** A drop counts as "simultaneous" only within this window. */
    const val SIMULTANEITY_WINDOW_MS = 10L * 60L * 1000L

    /** Confidence floor before a positive verdict may be persisted as [AttributedCause]. */
    const val MIN_CONFIDENCE = 0.60

    /** Protocol bias is only diagnosed when at least this many transports are represented. */
    const val PROTOCOL_BIAS_MIN_GROUPS = 2

    /** Carrier bias is only diagnosed when at least this many carriers are represented. */
    const val CARRIER_BIAS_MIN_GROUPS = 2

    /**
     * Attribute a set of server observations to one cause.
     *
     * The evaluation order is the causal hierarchy: a national event (all hosts, all ASNs)
     * dominates any per-server/per-protocol explanation, because those explanations would still
     * be true under a national event — the whole fleet is the victim. Protocol and carrier
     * biases are checked before per-server blame, because a transport that loses while its
     * sibling succeeds is a firewall choice, not a maintenance issue.
     */
    fun attribute(
        observations: List<ServerObservation>
    ): AttributionResult {
        if (observations.isEmpty()) {
            return AttributionResult(
                cause = AttributedCause.NORMAL_CONGESTION,
                confidence = 0.0,
                affected = 0,
                total = 0,
                simultaneousDropFraction = 0.0,
                reasons = listOf("no-observations")
            )
        }

        val total = observations.size
        val windowOpen = observations.map { it.windowStartMs }.filter { it > 0L }.minOrNull() ?: 0L
        val windowClose = observations.map { it.windowEndMs }.filter { it > 0L }.maxOrNull() ?: 0L
        val withinWindow = windowOpen > 0L && windowClose - windowOpen <= SIMULTANEITY_WINDOW_MS
        val dropped = observations.filter { it.dropped }
        val dropFraction = dropped.size.toDouble() / total.toDouble()
        val reasons = ArrayList<String>()

        // --- 1. National filtering event ------------------------------------------------
        if (withinWindow && dropFraction > NATIONAL_DROP_FRACTION) {
            // Independence check: if every dropping server is one ASN on one carrier, this is a
            // single provider outage, not a national event. The rule is the user's: "regardless
            // of IP/ASN" the drop must be mass — and therefore must span ≥2 independent ASNs
            // (or ≥3 distinct IPs across ≥2 carriers when ASN data is unavailable).
            val droppingIps = dropped.map { it.ip }.filter { it.isNotBlank() }.toSet()
            val droppingAsns = dropped.map { it.asn }.filter { it.isNotBlank() }.toSet()
            val droppingCarriers = dropped.map { it.carrier }.filter { it.isNotBlank() }.toSet()
            val diverse = droppingAsns.size >= 2 ||
                (droppingIps.size >= 3 && droppingCarriers.size >= 2)
            if (diverse) {
                reasons += "simultaneous-drop-fraction=%.2f".format(dropFraction)
                reasons += "distinct-ips=${droppingIps.size}"
                return AttributionResult(
                    cause = AttributedCause.NATIONAL_FILTERING_EVENT,
                    confidence = confidenceOf(dropFraction, diverse = true),
                    affected = dropped.size,
                    total = total,
                    simultaneousDropFraction = dropFraction,
                    reasons = reasons
                )
            }
            reasons += "mass-drop-but-single-provider"
        }

        // --- 2. Injection (needs independent Layer 0/2 evidence) -------------------------
        val resetObservations = observations.count { it.injectedResetSuspected }
        val resetConfidence = resetObservations.toDouble() / total.toDouble()
        if (resetConfidence >= 0.35) {
            reasons += "injected-reset-fraction=%.2f".format(resetConfidence)
            return AttributionResult(
                cause = AttributedCause.INJECTED_RESET,
                confidence = (0.55 + resetConfidence * 0.45).coerceIn(0.0, 1.0),
                affected = resetObservations,
                total = total,
                simultaneousDropFraction = dropFraction,
                reasons = reasons
            )
        }

        // --- 3. Protocol-targeted throttle ----------------------------------------------
        if (dropped.isNotEmpty()) {
            val byTransport = observations.groupBy { it.transportType }.filterKeys { it.isNotBlank() }
            if (byTransport.size >= PROTOCOL_BIAS_MIN_GROUPS) {
                val transportDropAligned = byTransport.any { (_, group) ->
                    val frac = group.count { it.dropped }.toDouble() / group.size
                    frac >= 0.8
                }
                val hasHealthyTransport = byTransport.any { (_, group) ->
                    group.any { !it.dropped } && group.none { it.dropped }
                }
                val hasThrottleShape = observations.any { it.sawtoothConfidence >= 0.66 }
                if (transportDropAligned && hasHealthyTransport && hasThrottleShape) {
                    val target = byTransport.entries
                        .filter { (_, group) ->
                            group.count { it.dropped }.toDouble() / group.size >= 0.8
                        }
                        .map { it.key }
                    reasons += "protocol-bias=${target.joinToString("+")}"
                    reasons += "sawtooth-confirmed"
                    return AttributionResult(
                        cause = AttributedCause.PROTOCOL_TARGETED_THROTTLE,
                        confidence = confidenceOf(dropFraction, diverse = true) * 0.9,
                        affected = dropped.size,
                        total = total,
                        simultaneousDropFraction = dropFraction,
                        reasons = reasons
                    )
                }
            }
        }

        // --- 4. Carrier-specific issue ---------------------------------------------------
        if (dropped.isNotEmpty()) {
            val byCarrier = observations.groupBy { it.carrier }.filterKeys { it.isNotBlank() }
            if (byCarrier.size >= CARRIER_BIAS_MIN_GROUPS) {
                val carrierAligned = byCarrier.any { (_, group) ->
                    group.count { it.dropped }.toDouble() / group.size >= 0.7
                }
                val otherCarrierHealthy = byCarrier.any { (_, group) ->
                    group.none { it.dropped } && group.size > 0
                }
                if (carrierAligned && otherCarrierHealthy) {
                    reasons += "carrier-outage"
                    return AttributionResult(
                        cause = AttributedCause.CARRIER_SPECIFIC_ISSUE,
                        confidence = confidenceOf(dropFraction, diverse = true) * 0.85,
                        affected = dropped.size,
                        total = total,
                        simultaneousDropFraction = dropFraction,
                        reasons = reasons
                    )
                }
            }
        }

        // --- 5. Server-side degradation --------------------------------------------------
        if (dropFraction in 0.001..0.69 && dropped.any { it.priorEvidence }) {
            reasons += "subset-degraded"
            return AttributionResult(
                cause = AttributedCause.SERVER_SIDE_DEGRADATION,
                confidence = confidenceOf(dropFraction, diverse = false).coerceIn(0.0, 0.9),
                affected = dropped.size,
                total = total,
                simultaneousDropFraction = dropFraction,
                reasons = reasons
            )
        }

        // --- 6. Normal congestion --------------------------------------------------------
        reasons += "no-correlation"
        return AttributionResult(
            cause = AttributedCause.NORMAL_CONGESTION,
            confidence = 0.35,
            affected = dropped.size,
            total = total,
            simultaneousDropFraction = dropFraction,
            reasons = reasons
        )
    }

    /** Confidence curve: a mass drop is strong evidence; a bias needs more trials to be sure. */
    private fun confidenceOf(fraction: Double, diverse: Boolean): Double {
        val base = ((fraction - 0.5) / 0.5).coerceIn(0.0, 1.0)
        return (if (diverse) 0.75 + base * 0.25 else 0.6 + base * 0.2)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Serialize a cause for persistence. Stored verbatim in `node_health.attributed_cause_last`
     * and parsed back with [parseCause].
     */
    fun encode(cause: AttributedCause): String = cause.name

    fun parseCause(raw: String?): AttributedCause? =
        raw?.let { text ->
            AttributedCause.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
        }

    /** Human-stable short key used by the UI copy keys. */
    fun shortKey(cause: AttributedCause): String = when (cause) {
        AttributedCause.NATIONAL_FILTERING_EVENT -> "national-filtering"
        AttributedCause.PROTOCOL_TARGETED_THROTTLE -> "protocol-throttle"
        AttributedCause.CARRIER_SPECIFIC_ISSUE -> "carrier-issue"
        AttributedCause.SERVER_SIDE_DEGRADATION -> "server-degraded"
        AttributedCause.INJECTED_RESET -> "injected-reset"
        AttributedCause.NORMAL_CONGESTION -> "normal-congestion"
    }
}
