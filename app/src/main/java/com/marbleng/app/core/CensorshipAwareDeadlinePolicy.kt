package com.marbleng.app.core

object CensorshipAwareDeadlinePolicy {
    data class ProbeDecision(
        val timeoutMs: Long,
        val failFast: Boolean,
        val useFallbackTarget: Boolean,
        val reason: String
    ) {
        val extensionApplied: Boolean
            get() = reason == FILTER_EXTENSION_REASON
    }

    const val DPI_INJECTION_MARGIN_MS = 1_000L
    const val THROTTLE_MARGIN_MS = 1_500L
    private const val FILTER_EXTENSION_REASON = "dpi-injection-margin"

    fun decideProbe(
        baseTimeoutMs: Long,
        injectedResetSuspected: Boolean = false,
        sawtoothConfidence: Double = 0.0,
        resetAfterVolumeConfidence: Double = 0.0
    ): ProbeDecision {
        val throttled = sawtoothConfidence >= ProtocolFingerprintAwareVerifier.SAWTOOTH_HIGH_CONFIDENCE ||
            resetAfterVolumeConfidence >= BehavioralConsistency.RESET_PATTERN_HIGH_CONFIDENCE
        val injection = injectedResetSuspected || throttled
        if (!injection) {
            return ProbeDecision(
                timeoutMs = baseTimeoutMs,
                failFast = false,
                useFallbackTarget = false,
                reason = "rtt-budget"
            )
        }
        val margin = if (injectedResetSuspected) DPI_INJECTION_MARGIN_MS else THROTTLE_MARGIN_MS
        return ProbeDecision(
            timeoutMs = (baseTimeoutMs + margin).coerceAtMost(LinkDeadlinePolicy.MAX_DNS_TIMEOUT_MS + margin),
            failFast = true,
            useFallbackTarget = true,
            reason = FILTER_EXTENSION_REASON
        )
    }

    fun batchBudgetMs(
        perProbeTimeoutMs: Long,
        nationalFilteringEvent: Boolean = false
    ): Long = if (nationalFilteringEvent) {
        (perProbeTimeoutMs + LinkDeadlinePolicy.PROBE_BATCH_SLACK_MS)
            .coerceAtMost(LinkDeadlinePolicy.MAX_TUNING_TRIAL_MS)
    } else {
        LinkDeadlinePolicy.probeBatchBudgetMs(perProbeTimeoutMs)
    }
}
