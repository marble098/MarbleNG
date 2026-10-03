package com.marbleng.app.core

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// =============================================================================
// MARBLE_HIGH_JITTER_SHIELD_V206 — what was wrong before
//
// Very high jitter was handled by three mechanisms that were each individually
// reasonable and collectively wrong for the one link they all fail on:
//
//  1. **The estimate was a mean.** `LinkQualityEstimator` reduces a window to an EWMA of
//     consecutive deltas. A mean is the one statistic a single 2-second stall destroys: one
//     retransmitted TLS handshake inside a 24-sample window moves it more than the other
//     twenty-three samples combined. The product then read that mean as "the link has 180 ms
//     of jitter" and behaved as if the *network* had changed, when in fact one *packet* had.
//
//  2. **The response was binary.** `JitterControlPolicy` is a good state machine, but it is a
//     state machine: `active` is true or false, and every consumer of it (the probe cadence,
//     the tuning gate, the Turbo backoff) branches on that one bit. A link at 24 ms and a link
//     at 900 ms get the same response, and the transition between them is a step — the cadence
//     halves in one tick. Steps are what the user sees as flapping.
//
//  3. **The cost of measuring rose with the severity.** Jitter control shortened the probe
//     interval from 30 ticks to 8, which is right, but nothing bounded the product of burst
//     size and cadence. On the worst link the engine measured the *most*, which is exactly
//     backwards: the link is congested, and every extra probe is congestion we added.
//
//  4. **Nothing remembered what "normal" means for this link.** 60 ms of jitter is a healthy
//     Saturday evening on Iranian mobile and a dying fibre line in Frankfurt. An absolute
//     threshold has to be set for one of them and is therefore wrong for the other.
//
// What follows is the replacement: a robust (median / interquartile-range) estimator, a
// baseline-relative severity, and one *continuous* mitigation level that drives a
// cost-bounded measurement plan.
// =============================================================================
//
// =============================================================================
// MARBLE_HIGH_JITTER_SHIELD_V207 — the critique of V206, and what it became
//
// The first cut of this file shipped the right estimator with the wrong controller. Four
// defects, found by reading it against a 40-minute Tehran evening trace:
//
//  A. **It sorted on every sample.** `RobustWindow` recomputed median/MAD/p95 inside `add()`,
//     so the cost was paid on the route-monitor thread for every sample even when nobody read
//     a statistic — O(n log n) plus two array fills per probe, forever. Fixed: the window is
//     now *dirty-flagged*, and the statistics are recomputed lazily, once, on the first read
//     after a write (`recomputeIfNeeded`). Reading twice without writing costs nothing.
//
//  B. **It used the wall clock.** `System.currentTimeMillis()` jumps — NTP correction, a
//     carrier time update, a user changing the clock. One backwards jump is a negative
//     interval, which the first cut clamped to zero and then counted as a perfect sample.
//     Fixed: the shield never reads a clock. The caller passes the elapsed timestamp it
//     already owns, and a sample is accepted only when it is non-negative and finite.
//
//  C. **The mitigation stepped.** V206 mapped the verdict onto four fixed levels (0 / .35 /
//     .70 / 1.0), so crossing a threshold moved the probe cadence by a third in one tick.
//     Fixed: the target is still per-verdict, but the level *chases* it through an asymmetric
//     filter — fast attack when the link is getting worse, slow release when it is getting
//     better, and a bounded hold so a mitigation that just engaged cannot disengage on the
//     next good sample. The level is now continuous; the verdict is only its label.
//
//  D. **An idle link stayed armed.** A phone in a pocket produces no samples. The first cut
//     held its last level indefinitely, so the next reconnect started with the engine already
//     escalated. Fixed: after `IDLE_AFTER_MS` without evidence the level decays at the idle
//     rate, which is faster than release but slower than attack — stale evidence fades, it
//     does not vanish.
//
//  E. **The estimator collapsed on a bimodal link.** The first cut measured dispersion as the
//     *median absolute deviation*. That is robust to one stalled packet, which is what it was
//     chosen for — but a link that alternates between two paths (a filtered route and its
//     failover, the single most common shape on the networks this product targets) puts half
//     its samples on each mode, so more than half sit *at* the median and the MAD is **zero**:
//     the worst jitter the user has all evening was reported as a perfectly smooth 0 ms.
//     Fixed: dispersion is the **interquartile range** scaled to a sigma-equivalent, which for
//     the same window is the distance between the two modes — and which, unlike the standard
//     deviation, still ignores the single stalled packet it was always meant to ignore. The
//     MAD is still computed, for diagnostics and for the tests that pin this defect.
//
// The estimator also gained one thing the first cut lacked: a **spike ratio**. The quartiles
// are robust to spikes, which is the point, but a window that carries a minority of packets
// from another planet is a different failure from a window that is uniformly noisy, and only
// a fence can tell them apart. It escalates the verdict one step when the window is spiky
// *and* jittery.
// =============================================================================

/**
 * The mechanism that answers "this link's jitter is enormous — now what?".
 *
 * The answer has three parts, in this order:
 *
 *  1. **Measure it honestly** ([RobustWindow]): median and interquartile range instead of
 *     mean and standard deviation, so the estimate describes the link and not its worst
 *     packet — and describes a link that alternates between two paths as what it is.
 *  2. **Judge it against the link itself** ([observe]): a baseline learned while the link is
 *     calm, with an absolute floor so a link that was never calm still gets graded.
 *  3. **Spend a bounded amount on it** ([Plan]): more frequent, hedged probes as the level
 *     rises, capped by a hard probes-per-minute budget — mitigation may cost bandwidth, but
 *     never an unbounded amount of it.
 *
 * Every function here is pure: no clock, no I/O, no Android types. The route monitor owns the
 * window and the state; this object only folds one sample into them.
 */
object HighJitterShield {

    enum class Verdict {
        /** Inside the link's own normal: nothing to do. */
        CALM,

        /** Above normal but usable: watch it. */
        ELEVATED,

        /** Bad enough to change behaviour: measure harder, widen buffers. */
        SEVERE,

        /** Unusable for interactive traffic: ask for another route. */
        EXTREME
    }

    /**
     * One bounded, allocation-free window of recent round-trip times.
     *
     * The ring and its scratch buffer are allocated once, in the constructor. After that,
     * [add] and every read are allocation-free: no boxed doubles, no list copy, no iterator.
     *
     * V207 (defect A): the statistics are computed **lazily**. [add] marks the window dirty
     * and returns; the sort happens on the first read afterwards and not again until another
     * sample arrives. A monitor that writes ten samples and reads one statistic pays for one
     * sort, not ten. [evaluations] counts the recomputations so the tests can prove it.
     */
    class RobustWindow(capacity: Int = DEFAULT_WINDOW) {

        private val ring: DoubleArray
        private val scratch: DoubleArray

        init {
            val size = capacity.coerceIn(MIN_WINDOW, MAX_WINDOW)
            ring = DoubleArray(size)
            scratch = DoubleArray(size)
        }

        private var filled = 0
        private var cursor = 0
        private var dirty = true
        private var cachedMedian = 0.0
        private var cachedQ1 = 0.0
        private var cachedQ3 = 0.0
        private var cachedMad = 0.0
        private var cachedP95 = 0.0
        private var cachedSpikeRatio = 0.0

        /** Diagnostics/tests: how many times the statistics were actually recomputed. */
        var evaluations: Int = 0
            private set

        /** How many samples the window currently holds (never more than its capacity). */
        val size: Int get() = filled

        /** The largest number of samples this window can hold. */
        val limit: Int get() = ring.size

        fun clear() {
            filled = 0
            cursor = 0
            dirty = true
        }

        /**
         * Record one round-trip. A non-finite or non-positive value is not a measurement —
         * it is a probe that failed, and a failed probe says nothing about jitter.
         *
         * V207 (defect B): the shield never reads a clock, so it cannot be fooled by one. The
         * caller's timestamp is used only for the idle decay, never for a delta.
         */
        fun add(rttMs: Double) {
            if (!rttMs.isFinite() || rttMs <= 0.0) return
            ring[cursor] = rttMs
            cursor = (cursor + 1) % ring.size
            if (filled < ring.size) filled++
            dirty = true
        }

        /** The median RTT of the window, ms. 0 when the window is empty. */
        fun medianMs(): Double {
            recomputeIfNeeded()
            return cachedMedian
        }

        /** The 95th percentile RTT of the window, ms — the tail the user actually feels. */
        fun p95Ms(): Double {
            recomputeIfNeeded()
            return cachedP95
        }

        /** The interquartile range, ms: the spread of the middle half of the window. */
        fun iqrMs(): Double {
            recomputeIfNeeded()
            return (cachedQ3 - cachedQ1).coerceAtLeast(0.0)
        }

        /**
         * The median absolute deviation, ms.
         *
         * Reported for diagnostics and for the tests; the verdict is computed from [iqrMs],
         * because the MAD of a *bimodal* window is zero — half the samples sitting on the
         * median is exactly the shape a link that alternates between two paths produces, and
         * a zero MAD would have reported that link as perfectly smooth. The IQR of the same
         * window is the distance between the two modes, which is the truth.
         */
        fun madMs(): Double {
            recomputeIfNeeded()
            return cachedMad
        }

        /**
         * The fraction of samples outside Tukey's fence (median-half +/- 1.5 IQR), 0..1.
         *
         * A robust estimator cannot flag a third of its own window as outliers — by
         * construction the quartiles are computed from the middle half — so this is
         * deliberately a *minority* detector: it separates "noisy" from "noisy plus a few
         * packets that arrived from another planet". A window that is uniformly bad is bad
         * jitter, and [robustJitterMs] already says so.
         */
        fun spikeRatio(): Double {
            recomputeIfNeeded()
            return cachedSpikeRatio
        }

        /**
         * The robust jitter: the IQR scaled to a sigma-equivalent, ms.
         *
         * For a Gaussian, `IQR / 1.349` estimates the standard deviation — and unlike the
         * standard deviation it survives both failure modes that matter on a real link: one
         * stalled packet moves it by nothing (the quartiles do not include the tail), and a
         * link that alternates between two paths reports the distance between them instead of
         * collapsing to zero the way a MAD does.
         */
        fun robustJitterMs(): Double {
            recomputeIfNeeded()
            return ((cachedQ3 - cachedQ1) / IQR_TO_SIGMA).coerceAtLeast(0.0)
        }

        private fun recomputeIfNeeded() {
            if (!dirty) return
            evaluations++
            dirty = false
            if (filled == 0) {
                cachedMedian = 0.0
                cachedQ1 = 0.0
                cachedQ3 = 0.0
                cachedMad = 0.0
                cachedP95 = 0.0
                cachedSpikeRatio = 0.0
                return
            }
            // Oldest first: `cursor` points at the slot the next write will take, so the
            // oldest live sample is `filled` slots behind it.
            for (index in 0 until filled) {
                scratch[index] = ring[(cursor - filled + index + ring.size) % ring.size]
            }
            Arrays.sort(scratch, 0, filled)
            cachedMedian = quantile(scratch, filled, 0.50)
            cachedP95 = quantile(scratch, filled, 0.95)
            cachedQ1 = quantile(scratch, filled, 0.25)
            cachedQ3 = quantile(scratch, filled, 0.75)

            // Tukey's fence, counted while the scratch buffer still holds the sorted values.
            val iqr = (cachedQ3 - cachedQ1).coerceAtLeast(0.0)
            val low = cachedQ1 - SPIKE_IQR_FACTOR * iqr
            val high = cachedQ3 + SPIKE_IQR_FACTOR * iqr
            var spikes = 0
            for (index in 0 until filled) {
                val value = scratch[index]
                if (value < low || value > high) spikes++
            }
            cachedSpikeRatio = spikes.toDouble() / filled.toDouble()

            // The sorted values are no longer needed once the quantiles are out: the same
            // scratch buffer now carries the deviations, so the whole pass costs two sorts
            // and zero allocations after construction.
            for (index in 0 until filled) {
                scratch[index] = abs(scratch[index] - cachedMedian)
            }
            Arrays.sort(scratch, 0, filled)
            cachedMad = quantile(scratch, filled, 0.50)
        }

        /** Linear-interpolated quantile of `sorted[0, n)` — the same convention as NumPy. */
        private fun quantile(sorted: DoubleArray, n: Int, q: Double): Double {
            if (n <= 0) return 0.0
            if (n == 1) return sorted[0]
            val position = q * (n - 1).toDouble()
            val low = floor(position).toInt().coerceIn(0, n - 1)
            val high = (low + 1).coerceAtMost(n - 1)
            return sorted[low] + (sorted[high] - sorted[low]) * (position - low)
        }
    }

    /** One probe result. The caller supplies the timestamp it already owns (V207 defect B). */
    data class Sample(
        /** Round-trip time of one probe, ms. Ignored when [verified] is false. */
        val rttMs: Double,
        /** Elapsed monotonic millis of the observation. */
        val nowMs: Long,
        /** A probe that produced no verified answer is a miss, never a fast sample. */
        val verified: Boolean = true,
        /** True when this sample occurred after a cellular radio idle gap (RRC transition). */
        val isRadioWakeup: Boolean = false
    )

    /** The shield's memory between samples. Immutable: the caller stores what [observe] returns. */
    data class State(
        /** The continuous mitigation level, 0..1. This — not the verdict — drives behaviour. */
        val level: Double = 0.0,
        /** The jitter this link shows when it is calm, ms. 0 until it has been observed calm. */
        val baselineJitterMs: Double = 0.0,
        /** How many calm observations went into [baselineJitterMs]. */
        val baselineSamples: Int = 0,
        /** Timestamp of the previous accepted sample; drives the idle decay. */
        val lastSampleAtMs: Long = 0L,
        /** When the level last crossed into SEVERE or above; drives the hold window. */
        val escalatedAtMs: Long = 0L,
        /** Consecutive ticks at SEVERE or above. */
        val severeStreak: Int = 0,
        /** Consecutive CALM ticks. */
        val calmStreak: Int = 0,
        /** The verdict of the previous evaluation. */
        val verdict: Verdict = Verdict.CALM,
        /** When a re-rank was last requested; drives the re-rank cooldown. */
        val lastRerankAtMs: Long = 0L,
        /** How many times this session crossed into SEVERE. Diagnostics only. */
        val escalations: Int = 0
    )

    /** What the route monitor should now do. Every field is derived, none is stored. */
    data class Plan(
        val verdict: Verdict,
        /** Continuous 0..1 mitigation: rises fast, falls slowly, holds after an escalation. */
        val level: Double,
        /** Robust jitter, ms (median-anchored, spike-resistant). */
        val jitterMs: Double,
        /** Median round-trip, ms. */
        val medianRttMs: Double,
        /** 95th-percentile round-trip, ms. */
        val p95RttMs: Double,
        /** Fraction of the window that is spikes, 0..1. */
        val spikeRatio: Double,
        /** The learned calm baseline this verdict was measured against, ms. */
        val baselineJitterMs: Double,
        /** Samples behind this plan. */
        val samples: Int,
        /** How many probes to send per burst. The fastest verified answer wins. */
        val probeBurst: Int,
        /** Ticks between bursts, on a one-second tick. Budget-bounded, never below [MIN_TICKS]. */
        val probeEveryTicks: Int,
        /** Latency-buffer multiplier, 1.0 (calm) to 2.5 (extreme). */
        val bufferScale: Double,
        /** True when the route optimizer should be asked for a different route. */
        val requestRerank: Boolean,
        /** One line for the diagnostics log: the verdict and the two numbers behind it. */
        val reason: String,
        /** True when the sample was tagged as a cellular radio wakeup after idle. */
        val isRadioWakeup: Boolean = false
    )

    data class Decision(val state: State, val plan: Plan)

    /** Cellular radio RRC dormancy gap threshold: probes after this duration are wakeups. */
    const val RRC_IDLE_GAP_MS = 6_000L

    /** Samples in the rolling window. Bounded: the estimate is recent, and the cost is fixed. */
    const val DEFAULT_WINDOW = 24
    const val MIN_WINDOW = 6
    const val MAX_WINDOW = 64

    /** Samples required before the shield will say anything other than CALM. */
    const val MIN_SAMPLES = 6

    /** Scales the interquartile range to a sigma-equivalent for a Gaussian. */
    const val IQR_TO_SIGMA = 1.349

    /** Tukey's multiplier: how far outside the quartiles a sample must sit to be a spike. */
    const val SPIKE_IQR_FACTOR = 1.5

    /**
     * A window that is this spiky *and* jittery is one verdict worse than its ratio alone.
     *
     * Deliberately low: a robust estimator can only ever flag a minority of its window, so
     * one sample in seven beyond the fence is already a remarkable amount of evidence.
     */
    const val SPIKE_SEVERE_RATIO = 0.15

    /** Absolute jitter that is calm for every link, whatever its own history says. */
    const val ABSOLUTE_CALM_MS = 22.0

    /** How far above its own baseline a link may go before that counts as elevated. */
    const val BASELINE_HEADROOM = 1.8

    /**
     * The worst jitter that may *seed* a link's baseline.
     *
     * Above this the reading is a storm, and a storm is not allowed to become the link's
     * definition of calm (see the seeding rule in [observe]).
     */
    const val BASELINE_SEED_MAX_MS = 150.0

    /** How fast the calm baseline follows the link. Slow: a baseline must not chase a storm. */
    const val ALPHA_BASELINE = 0.10

    /** Filter constants of the level: attack is ~9x faster than release, on purpose. */
    const val ALPHA_ATTACK = 0.55
    const val ALPHA_RELEASE = 0.06
    const val ALPHA_IDLE = 0.25

    /** Without evidence for this long, the level fades instead of waiting for a release. */
    const val IDLE_AFTER_MS = 20_000L

    /** After an escalation the level may not fall at all for this long. */
    const val MIN_HOLD_MS = 20_000L

    /** Cadence endpoints, in one-second route-monitor ticks. */
    const val BASE_TICKS = 30
    const val MIN_TICKS = 6
    const val TICK_MS = 1_000L

    /**
     * The smallest burst the shield will ever ask for.
     *
     * Not one. A burst of one is a burst that cannot detect the jitter it exists to measure:
     * the first sample of a burst is the number the user reads as their ping, and it takes a
     * second to say anything at all about variation. It is also the sample the window needs —
     * the verdict waits for [MIN_SAMPLES], and a one-probe cycle stretches that wait to as
     * many minutes as it takes to fill the window.
     */
    const val MIN_BURST = 2

    /** The largest burst the shield will ever ask for. */
    const val MAX_BURST = 3

    /** The hard ceiling: mitigation may cost bandwidth, never an unbounded amount of it. */
    const val MAX_PROBES_PER_MINUTE = 30.0

    /**
     * The level at which the rest of the product treats the link as degraded.
     *
     * Consumers that only have one bit to spend (the live tuner's urgency gate, for one) ask
     * for this rather than inventing their own threshold: it sits between ELEVATED and SEVERE,
     * so a link has to be clearly past "watching it" before the engine starts spending work
     * on it.
     */
    const val DEGRADED_LEVEL = 0.5

    /** Latency buffers grow by at most this factor at level 1.0. */
    const val BUFFER_SPAN = 1.5

    /** Consecutive EXTREME ticks before the optimizer is asked for another route. */
    const val RERANK_STREAK = 3

    /** ...and how long that request stays open before it may be repeated. */
    const val RERANK_COOLDOWN_MS = 180_000L

    /** The mitigation level each verdict aims at. V207: a *target*, chased by a filter. */
    private fun targetFor(verdict: Verdict): Double = when (verdict) {
        Verdict.CALM -> 0.0
        Verdict.ELEVATED -> 0.35
        Verdict.SEVERE -> 0.70
        Verdict.EXTREME -> 1.0
    }

    /**
     * Fold one probe into the window and the state, and return the plan that follows.
     *
     * @param window the caller's rolling window; the sample is written into it first.
     * @param nowMs  elapsed monotonic millis. Defaults to the sample's own timestamp, so the
     *               common call site cannot accidentally pass a different clock.
     */
    fun observe(
        sample: Sample,
        state: State,
        window: RobustWindow,
        nowMs: Long = sample.nowMs
    ): Decision {
        val isIdleWakeup = sample.isRadioWakeup
        // If this sample is an idle radio wakeup (RRC setup delay), do NOT pollute the steady-state jitter window
        if (!isIdleWakeup) {
            window.add(if (sample.verified) sample.rttMs else 0.0)
        }
        val samples = window.size
        val jitter = window.robustJitterMs()
        val median = window.medianMs()
        val p95 = window.p95Ms()
        val spikes = window.spikeRatio()

        // ── 1. What is normal for THIS link. ──────────────────────────────────────────────
        //
        // Two rules, and their order is the design:
        //
        //  • **Seed once.** The first graded reading of a link is the only prior it has, so it
        //    becomes the baseline — but only if that reading is itself plausible as a calm one
        //    ([BASELINE_SEED_MAX_MS]). A link first met mid-storm is *not* allowed to define
        //    its own normal by the storm: it stays unlearned and is graded against the
        //    absolute floor, which is the conservative answer.
        //  • **Then only calm evidence moves it.** A baseline that chased every reading would
        //    slowly redefine a bad evening as normal and stop reporting it.
        val canGrade = samples >= MIN_SAMPLES
        val seedable = canGrade && state.baselineJitterMs <= 0.0 && jitter <= BASELINE_SEED_MAX_MS
        val calmNow = canGrade &&
            jitter <= max(ABSOLUTE_CALM_MS, state.baselineJitterMs * BASELINE_HEADROOM)
        val baseline = when {
            !canGrade -> state.baselineJitterMs
            seedable -> jitter
            calmNow -> state.baselineJitterMs + (jitter - state.baselineJitterMs) * ALPHA_BASELINE
            else -> state.baselineJitterMs
        }
        val baselineSamples = if (seedable || calmNow) state.baselineSamples + 1 else state.baselineSamples

        // ── 2. How bad this is, relative to that baseline. ────────────────────────────────
        val reference = max(ABSOLUTE_CALM_MS, baseline * BASELINE_HEADROOM)
        val ratio = if (reference > 0.0) jitter / reference else 0.0
        val measured = when {
            samples < MIN_SAMPLES -> Verdict.CALM
            ratio < 1.0 -> Verdict.CALM
            ratio < 2.0 -> Verdict.ELEVATED
            ratio < 4.0 -> Verdict.SEVERE
            else -> Verdict.EXTREME
        }
        val verdict = if (samples >= MIN_SAMPLES && spikes >= SPIKE_SEVERE_RATIO) {
            escalate(measured)
        } else {
            measured
        }

        // ── 3. The continuous level (V207 defect C/D). ────────────────────────────────────
        val target = targetFor(verdict)
        val idle = state.lastSampleAtMs > 0L && nowMs - state.lastSampleAtMs >= IDLE_AFTER_MS
        val attacking = target > state.level
        val alpha = when {
            idle -> ALPHA_IDLE
            attacking -> ALPHA_ATTACK
            else -> ALPHA_RELEASE
        }
        var level = state.level + (target - state.level) * alpha
        // A mitigation that just engaged is not allowed to disengage on the next good sample:
        // inside the hold window the level may rise but never fall.
        val holding = state.escalatedAtMs > 0L && nowMs - state.escalatedAtMs < MIN_HOLD_MS
        if (holding) level = max(level, state.level)
        level = level.coerceIn(0.0, 1.0)

        val escalated = verdict.ordinal >= Verdict.SEVERE.ordinal &&
            state.verdict.ordinal < Verdict.SEVERE.ordinal
        val severeStreak = if (verdict.ordinal >= Verdict.SEVERE.ordinal) state.severeStreak + 1 else 0
        val calmStreak = if (verdict == Verdict.CALM) state.calmStreak + 1 else 0

        // ── 4. What it costs to act on it. ────────────────────────────────────────────────
        // Hedging: under jitter the fastest of N probes is a better estimate of the path than
        // any single probe, because the median of a burst is not dragged by one late packet.
        // The burst only grows once the level is real, and the budget has the last word.
        val burst = burstFor(level)
        val wantedTicks = (BASE_TICKS - (BASE_TICKS - MIN_TICKS) * level).toInt().coerceAtLeast(MIN_TICKS)
        val probeEveryTicks = budgetedTicks(wantedTicks, burst)
        val requestRerank = verdict == Verdict.EXTREME &&
            severeStreak >= RERANK_STREAK &&
            nowMs - state.lastRerankAtMs >= RERANK_COOLDOWN_MS

        val plan = Plan(
            verdict = verdict,
            level = level,
            jitterMs = jitter,
            medianRttMs = median,
            p95RttMs = p95,
            spikeRatio = spikes,
            baselineJitterMs = baseline,
            samples = samples,
            probeBurst = burst,
            probeEveryTicks = probeEveryTicks,
            bufferScale = 1.0 + BUFFER_SPAN * level,
            requestRerank = requestRerank,
            reason = if (isIdleWakeup) {
                "${verdict.name.lowercase()} • radio-wakeup sample after idle gap • level ${(level * 100).toInt()}%"
            } else {
                "${verdict.name.lowercase()} • jitter ${jitter.toInt()}ms of " +
                    "baseline ${baseline.toInt()}ms • spike ${(spikes * 100).toInt()}% • " +
                    "level ${(level * 100).toInt()}%"
            },
            isRadioWakeup = isIdleWakeup
        )

        val next = state.copy(
            level = level,
            baselineJitterMs = baseline,
            baselineSamples = baselineSamples,
            lastSampleAtMs = nowMs,
            escalatedAtMs = if (escalated) nowMs else state.escalatedAtMs,
            severeStreak = severeStreak,
            calmStreak = calmStreak,
            verdict = verdict,
            lastRerankAtMs = if (requestRerank) nowMs else state.lastRerankAtMs,
            escalations = state.escalations + if (escalated) 1 else 0
        )
        return Decision(next, plan)
    }

    /**
     * How many probes one cycle spends, for a given level.
     *
     * Hedging: under jitter the fastest of N probes is a better estimate of the path than any
     * single probe, because one late packet cannot drag the fastest of them. The burst only
     * grows once the level is real, it never leaves [MIN_BURST]..[MAX_BURST], and
     * [budgetedTicks] has the last word on what it costs.
     */
    fun burstFor(level: Double): Int = when {
        level >= 0.80 -> MAX_BURST
        level >= 0.45 -> 2
        else -> MIN_BURST
    }

    /** One verdict step worse, stopping at [Verdict.EXTREME]. */
    private fun escalate(verdict: Verdict): Verdict = when (verdict) {
        Verdict.CALM -> Verdict.ELEVATED
        Verdict.ELEVATED -> Verdict.SEVERE
        Verdict.SEVERE, Verdict.EXTREME -> Verdict.EXTREME
    }

    /**
     * The cadence a burst of [burst] probes may run at without exceeding the probe budget.
     *
     * This is the line the first cut did not have. Jitter is a reason to measure *better*; it
     * is never a reason to measure without limit, because the probes travel on the link they
     * are diagnosing. When [wantedTicks] already fits the budget it is returned untouched, so
     * a calm link never pays for a rule that only binds under stress.
     */
    fun budgetedTicks(wantedTicks: Int, burst: Int): Int {
        val ticks = wantedTicks.coerceAtLeast(MIN_TICKS)
        val safeBurst = burst.coerceIn(1, MAX_BURST)
        if (probesPerMinute(ticks, safeBurst) <= MAX_PROBES_PER_MINUTE) return ticks
        // The slowest cadence that still fits the budget: one budget's worth of probes per
        // minute, spread over as many ticks as it takes.
        val minTicks = ceil(
            safeBurst.toDouble() * 60_000.0 / (MAX_PROBES_PER_MINUTE * TICK_MS.toDouble())
        ).toInt()
        return minTicks.coerceAtLeast(MIN_TICKS)
    }

    /** Probes per minute for a cadence of [ticks] one-second ticks and bursts of [burst]. */
    fun probesPerMinute(ticks: Int, burst: Int): Double =
        60_000.0 / (ticks.coerceAtLeast(1) * TICK_MS.toDouble()) * burst.coerceAtLeast(1)

    /** Probes per minute of a finished plan — the number the budget is written against. */
    fun probesPerMinute(plan: Plan): Double = probesPerMinute(plan.probeEveryTicks, plan.probeBurst)

    /** One line for a diagnostics row. */
    fun describe(plan: Plan): String =
        "${plan.reason} • burst ${plan.probeBurst} every ${plan.probeEveryTicks}t • " +
            "buffer x${(plan.bufferScale * 100).toInt()}%"

    /**
     * The mean-and-standard-deviation estimator this shield replaces, kept **only** as the
     * counter-example the tests assert against.
     *
     * It is here so the claim "a mean is destroyed by one packet" is a testable statement
     * rather than a comment: [HighJitterShieldTest] feeds both estimators the same window and
     * asserts that this one panics while [RobustWindow] does not. Nothing in the product calls
     * it; deleting it would delete the evidence for the design.
     */
    fun naiveJitterMs(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        val deltas = DoubleArray(samples.size - 1)
        for (index in deltas.indices) deltas[index] = abs(samples[index + 1] - samples[index])
        var sum = 0.0
        for (value in deltas) sum += value
        return sum / deltas.size
    }
}
