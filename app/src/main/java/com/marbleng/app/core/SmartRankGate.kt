package com.marbleng.app.core

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe debounce / cooldown gate for the Smart Rank button.
 *
 * MARBLE_SMART_RANK_V90 — fixes the observed "9 triggers in 7 seconds with zero real results":
 * every trigger re-ran the full preflight + benchmark pool, so a rapid tap storm produced nothing
 * but wasted probes and a silent empty result. This gate enforces:
 *
 *  1. a minimum cooldown between accepted triggers (default 1000 ms), and
 *  2. a hard single-flight guarantee — only one Rank run at a time.
 *
 * Both checks are CAS-based ([AtomicBoolean] + [AtomicLong]) so concurrent trigger calls from
 * different threads can never race each other into a double run. The gate is pure (injectable
 * clock) so its thread-safety and debounce behaviour are deterministic in JVM unit tests.
 */
class SmartRankGate(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    enum class Verdict { ACCEPTED, COOLDOWN, BUSY }

    /** Result of a trigger attempt, carrying the user-facing explanation. */
    data class GateResult(
        val verdict: Verdict,
        val retryInMs: Long,
        val message: String
    )

    private val inFlight = AtomicBoolean(false)
    private val lastAcceptedAt = AtomicLong(0L)

    /**
     * Attempt to acquire the gate. Exactly one concurrent caller wins [Verdict.ACCEPTED]; every
     * other caller gets [Verdict.BUSY] (already running) or [Verdict.COOLDOWN] (debounced).
     */
    fun tryAcquire(): GateResult {
        val t = now()
        if (!inFlight.compareAndSet(false, true)) {
            return GateResult(Verdict.BUSY, 0L, "Smart Rank is already running — wait for it to finish")
        }
        val last = lastAcceptedAt.get()
        if (last > 0L && t - last < cooldownMs) {
            inFlight.set(false)
            val retryInMs = (cooldownMs - (t - last)).coerceAtLeast(1L)
            return GateResult(
                Verdict.COOLDOWN,
                retryInMs,
                "Smart Rank is cooling down • retry in ${String.format(Locale.US, "%.1f", retryInMs / 1000.0)}s"
            )
        }
        lastAcceptedAt.set(t)
        return GateResult(Verdict.ACCEPTED, 0L, "")
    }

    /** Release the single-flight lock when a rank run finishes (success or failure). */
    fun release() {
        inFlight.set(false)
    }

    fun isInFlight(): Boolean = inFlight.get()

    /** Seconds since the last accepted trigger, or null when it never ran. */
    fun sinceLastAccepted(nowMs: Long = now()): Long? {
        val last = lastAcceptedAt.get()
        return if (last == 0L) null else (nowMs - last).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 1_000L
    }
}
