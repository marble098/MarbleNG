package com.marbleng.app.core

/**
 * MARBLE_IRAN_AWARE_PING_L0_DEADLINES — the censorship-aware layer over [LinkDeadlinePolicy].
 *
 * ## Why a deadline is a censorship decision
 *
 * [LinkDeadlinePolicy] derives budgets from measured round trips — correct for congestion and
 * geography, and exactly wrong for a stateful filter. An injected RST usually arrives *after* a
 * ClientHello has been answered enough for the filter to identify the SNI. Two failure modes
 * follow from using a plain RTT budget on a filtered path:
 *
 *  1. The probe is cut by *our own socket timeout* before the reset arrives, so the evidence
 *     arrives too late to be classified. The attempt is then recorded as `SILENT_TIMEOUT` —
 *     "no answer" — when the truth is "the filter answered with a reset". The verification
 *     system that is supposed to detect injection becomes incapable of seeing it.
 *  2. The caller retries the *same* target with the same budget, and an adaptive filter
 *     learns our deterministic pattern and starts timing the probe itself.
 *
 * ## The two rules
 *
 *  - **`dpiInjectionMarginMs`**: while injection is suspected (Layer 0 time-to-RST or Layer 2
 *    reset-after-volume), the budget is extended by a fixed margin so the probe survives long
 *    enough to *see the reset* and classify it, instead of timing out first. The margin is
 *    added to the measured-derived budget, never replacing it — a slow healthy path still gets
 *    its full RTT budget.
 *  - **Fail-fast + fallback**: on an injection signature there is nothing more to learn from
 *    hammering that target. The probe fails fast (no repeated samples on the same SNI) and the
 *    caller is told to move to the next rotated target in the [ProbeTargetPool]. Time spent on
 *    one filtered SNI is time not spent finding a live one.
 */
object CensorshipAwareDeadlinePolicy {

    data class ProbeDecision(
        /** Wall-clock budget for one probe attempt. */
        val timeoutMs: Long,
        /** True when the probe should stop after the first classification, not sample more. */
        val failFast: Boolean,
        /** True when the caller should rotate to the next [ProbeTargetPool] target. */
        val useFallbackTarget: Boolean,
        val reason: String
    ) {
        /** Optimise the budget for observing a filter, not for waiting out congestion. */
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

    /**
     * The budget for a *batch* of probes when a national event is likely: the batch must be
     * bounded hard so a filtered fleet cannot make the Home readout hang for minutes.
     */
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
