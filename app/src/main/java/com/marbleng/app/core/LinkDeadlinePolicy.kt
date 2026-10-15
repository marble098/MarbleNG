package com.marbleng.app.core

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class LinkEvidence(
    val rttMs: Double = 0.0,
    val tailRttMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val lossPercent: Double = 0.0,
    val samples: Int = 0
) {
    val known: Boolean
        get() = samples > 0 && rttMs > 0.0

    val effectiveRttMs: Double
        get() = max(rttMs, tailRttMs)

    fun conservativeOf(other: LinkEvidence): LinkEvidence = when {
        !other.known -> this
        !known -> other
        else -> LinkEvidence(
            rttMs = max(rttMs, other.rttMs),
            tailRttMs = max(tailRttMs, other.tailRttMs),
            jitterMs = max(jitterMs, other.jitterMs),
            lossPercent = max(lossPercent, other.lossPercent),
            samples = max(samples, other.samples)
        )
    }

    companion object {
        val UNKNOWN = LinkEvidence()

        fun fromHealth(record: NodeHealthRecord?): LinkEvidence {
            if (record == null || record.samples <= 0) return UNKNOWN
            val latency = record.latencyEwma.takeIf { it in 1.0..8_000.0 } ?: return UNKNOWN
            return LinkEvidence(
                rttMs = latency,

                tailRttMs = latency + record.jitterEwma.coerceIn(0.0, 2_000.0) * 2.0,
                jitterMs = record.jitterEwma.coerceIn(0.0, 2_000.0),

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

object LinkDeadlinePolicy {
    const val PRIMARY_DNS_FLOOR_MS = 1_350L
    const val SECONDARY_DNS_FLOOR_MS = 1_650L
    const val SECONDARY_DNS_STEP_MS = 250L

    const val MAX_DNS_TIMEOUT_MS = 10_000L

    const val FRAGMENTED_DNS_BASE_MS = 8_000L
    const val FRAGMENTED_DNS_STEP_MS = 1_000L

    const val FRAGMENT_PACING_OVERHEAD_MS = 2_100L

    const val FRAGMENT_DNS_FLOOR_MS = 3_000L

    const val TLS_TRANSACTION_HOPS = 3

    const val HTTPS_PROBE_HOPS = 4

    const val JITTER_HEADROOM_MULTIPLIER = 3.0

    const val LOSS_HEADROOM_FACTOR = 0.05

    const val RACE_DELAY_RTT_FRACTION = 0.25
    const val MIN_RACE_DELAY_MS = AddressFamilyPolicy.MIN_TRY_DELAY_MS
    const val MAX_RACE_DELAY_MS = AddressFamilyPolicy.MAX_TRY_DELAY_MS

    const val PROBE_BATCH_SLACK_MS = 600L

    const val MAX_TUNING_TRIAL_MS = 9_000L
    const val TUNING_PASS_SLACK_MS = 4_000L
    const val MAX_TUNING_PASS_MS = 24_000L

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

    fun dnsServerTimeoutMs(
        evidence: LinkEvidence,
        index: Int,
        fragmented: Boolean
    ): Long {
        val step = index.coerceAtLeast(0)
        if (fragmented) {
            if (!evidence.known) {
                return (FRAGMENTED_DNS_BASE_MS + step * FRAGMENTED_DNS_STEP_MS)
                    .coerceAtMost(MAX_DNS_TIMEOUT_MS * 2L)
            }
            val tail = evidence.effectiveRttMs
            val jitterHeadroom = evidence.jitterMs.coerceAtLeast(0.0) * JITTER_HEADROOM_MULTIPLIER
            val lossHeadroom = tail * LOSS_HEADROOM_FACTOR * evidence.lossPercent.coerceIn(0.0, 100.0)
            val derived = ceil(TLS_TRANSACTION_HOPS * tail + jitterHeadroom + lossHeadroom).toLong() +
                FRAGMENT_PACING_OVERHEAD_MS +
                step * SECONDARY_DNS_STEP_MS
            return derived.coerceIn(FRAGMENT_DNS_FLOOR_MS, MAX_DNS_TIMEOUT_MS)
        }
        val floor = if (step == 0) {
            PRIMARY_DNS_FLOOR_MS
        } else {
            SECONDARY_DNS_FLOOR_MS + (step - 1) * SECONDARY_DNS_STEP_MS
        }
        return transactionMs(evidence, TLS_TRANSACTION_HOPS, floor, MAX_DNS_TIMEOUT_MS)
    }

    fun httpsProbeTimeoutMs(
        evidence: LinkEvidence,
        floorMs: Long = 1_800L,
        ceilingMs: Long = 8_000L
    ): Long = transactionMs(evidence, HTTPS_PROBE_HOPS, floorMs, ceilingMs)

    fun probeBatchBudgetMs(
        perProbeTimeoutMs: Long,
        floorMs: Long = 2_600L,
        ceilingMs: Long = 9_000L
    ): Long = (perProbeTimeoutMs + PROBE_BATCH_SLACK_MS).coerceIn(floorMs, ceilingMs)

    fun tuningTrialTimeoutMs(evidence: LinkEvidence, floorMs: Long): Long =
        httpsProbeTimeoutMs(evidence, floorMs = floorMs, ceilingMs = MAX_TUNING_TRIAL_MS)

    fun tuningPassBudgetMs(trialTimeoutMs: Long, requestedMs: Long): Long =
        (trialTimeoutMs + TUNING_PASS_SLACK_MS)
            .coerceAtLeast(requestedMs)
            .coerceAtMost(MAX_TUNING_PASS_MS)

    fun raceTryDelayMs(evidence: LinkEvidence, configuredMs: Int): Int {
        val scaled = if (evidence.known) {
            (evidence.effectiveRttMs * RACE_DELAY_RTT_FRACTION).roundToInt()
        } else {
            0
        }
        return maxOf(configuredMs, scaled).coerceIn(MIN_RACE_DELAY_MS, MAX_RACE_DELAY_MS)
    }
}
