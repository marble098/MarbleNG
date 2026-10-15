package com.marbleng.app.core

import kotlin.math.abs

object CausalAttribution {
    enum class AttributedCause {
        NATIONAL_FILTERING_EVENT,
        PROTOCOL_TARGETED_THROTTLE,
        CARRIER_SPECIFIC_ISSUE,
        SERVER_SIDE_DEGRADATION,
        INJECTED_RESET,
        NORMAL_CONGESTION
    }

    data class ServerObservation(
        val profileId: String,
        val ip: String = "",
        val asn: String = "",
        val carrier: String = "",
        val transportType: String = "",
        val dropped: Boolean = false,
        val baselineDeltaRatio: Double = 0.0,
        val injectedResetSuspected: Boolean = false,
        val sawtoothConfidence: Double = 0.0,
        val priorEvidence: Boolean = false,
        val windowStartMs: Long = 0L,
        val windowEndMs: Long = 0L
    )

    data class AttributionResult(
        val cause: AttributedCause,
        val confidence: Double,
        val affected: Int,
        val total: Int,
        val simultaneousDropFraction: Double,
        val reasons: List<String>
    ) {
        val rankingFreeze: Boolean
            get() = cause == AttributedCause.NATIONAL_FILTERING_EVENT
    }

    const val NATIONAL_DROP_FRACTION = 0.70

    const val SIMULTANEITY_WINDOW_MS = 10L * 60L * 1000L

    const val MIN_CONFIDENCE = 0.60

    const val PROTOCOL_BIAS_MIN_GROUPS = 2

    const val CARRIER_BIAS_MIN_GROUPS = 2

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

        if (withinWindow && dropFraction > NATIONAL_DROP_FRACTION) {
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

    private fun confidenceOf(fraction: Double, diverse: Boolean): Double {
        val base = ((fraction - 0.5) / 0.5).coerceIn(0.0, 1.0)
        return (if (diverse) 0.75 + base * 0.25 else 0.6 + base * 0.2)
            .coerceIn(0.0, 1.0)
    }

    fun encode(cause: AttributedCause): String = cause.name

    fun parseCause(raw: String?): AttributedCause? =
        raw?.let { text ->
            AttributedCause.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
        }

    fun shortKey(cause: AttributedCause): String = when (cause) {
        AttributedCause.NATIONAL_FILTERING_EVENT -> "national-filtering"
        AttributedCause.PROTOCOL_TARGETED_THROTTLE -> "protocol-throttle"
        AttributedCause.CARRIER_SPECIFIC_ISSUE -> "carrier-issue"
        AttributedCause.SERVER_SIDE_DEGRADATION -> "server-degraded"
        AttributedCause.INJECTED_RESET -> "injected-reset"
        AttributedCause.NORMAL_CONGESTION -> "normal-congestion"
    }
}

object NationalEventBannerPolicy {
    const val ClearConfidence: Double = 0.6

    fun next(previous: String, freeze: Boolean, cause: String, confidence: Double): String = when {
        freeze && cause.isNotBlank() -> cause
        previous.isBlank() -> ""
        confidence >= ClearConfidence && !freeze -> ""
        else -> previous
    }
}
