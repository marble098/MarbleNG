package com.marbleng.app.core

import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class SmartRankGate(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    enum class Verdict { ACCEPTED, COOLDOWN, BUSY }

    data class GateResult(
        val verdict: Verdict,
        val retryInMs: Long,
        val message: String
    )

    private val inFlight = AtomicBoolean(false)
    private val lastAcceptedAt = AtomicLong(0L)

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

    fun release() {
        inFlight.set(false)
    }

    fun isInFlight(): Boolean = inFlight.get()

    fun sinceLastAccepted(nowMs: Long = now()): Long? {
        val last = lastAcceptedAt.get()
        return if (last == 0L) null else (nowMs - last).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 1_000L
    }
}
