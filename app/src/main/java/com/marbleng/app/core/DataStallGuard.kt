package com.marbleng.app.core

/**
 * Turns noisy Android data-stall callbacks into bounded validation requests.
 *
 * It never tears a tunnel down. CONFIRM means that the service may run its existing independent
 * route confirmation immediately. Recent real traffic always wins over a synthetic OS signal.
 */
class DataStallGuard(
    private val confirmationWindowMs: Long = 15_000L,
    private val confirmationSignals: Int = 2,
    private val cooldownMs: Long = 30_000L
) {
    enum class Decision {
        IGNORE_RECENT_TRAFFIC,
        IGNORE_COOLDOWN,
        PROBE,
        CONFIRM
    }

    private var windowStartedAtMs = 0L
    private var signalsInWindow = 0
    private var cooldownUntilMs = 0L

    @Synchronized
    fun onSignal(nowMs: Long, trafficRecentlyMoved: Boolean): Decision {
        require(nowMs >= 0L) { "nowMs must be monotonic/non-negative" }
        if (trafficRecentlyMoved) {
            resetWindow()
            return Decision.IGNORE_RECENT_TRAFFIC
        }
        if (nowMs < cooldownUntilMs) return Decision.IGNORE_COOLDOWN
        if (windowStartedAtMs == 0L || nowMs - windowStartedAtMs > confirmationWindowMs) {
            windowStartedAtMs = nowMs
            signalsInWindow = 0
        }
        signalsInWindow++
        if (signalsInWindow < confirmationSignals.coerceAtLeast(1)) return Decision.PROBE
        cooldownUntilMs = nowMs + cooldownMs.coerceAtLeast(0L)
        resetWindow()
        return Decision.CONFIRM
    }

    @Synchronized
    fun reset() {
        resetWindow()
        cooldownUntilMs = 0L
    }

    private fun resetWindow() {
        windowStartedAtMs = 0L
        signalsInWindow = 0
    }
}
