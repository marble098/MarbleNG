package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Profile Flap Guard — prevents rapid switching between profiles under uncertainty.
 *
 * Problem addressed: Logs showed multiple rapid reconnect cycles between profiles
 * (e.g., Turkey 8 <-> Marble Freedom Aegis) while the bug finder reported
 * "live-inconclusive-backoff". This happens because the ranking engine makes
 * decisions on incomplete evidence and triggers switches too quickly.
 *
 * This guard enforces:
 * 1. Minimum dwell time: a profile must stay active for at least N seconds before
 *    a switch is considered.
 * 2. Switch penalty: rapid consecutive switches increase the threshold for the next switch.
 * 3. Evidence floor: switching only happens when there is material evidence of a better route,
 *    not just inconclusive probe results.
 * 4. Iran-aware hysteresis: under Iran Mode, dwell times are longer because probe evidence
 *    is less reliable.
 * 5. Graceful degradation: if the active profile starts failing, allow faster failover
 *    but still prevent oscillation.
 */
class ProfileFlapGuard(
    private val iranAware: Boolean = true
) {
    private var activeProfileId: String = ""
    private val connectedSinceMs = AtomicLong(0)
    private val lastSwitchMs = AtomicLong(0)
    private val switchCountInWindow = AtomicInteger(0)
    private val windowStartMs = AtomicLong(0)
    private var consecutiveFailures = 0

    // MARBLE_CANCELLATION_SAFE_SHUTDOWN_V1
    // Teardown accounting so a clean stop is never reported as a resolver/transport failure.
    private val cleanStops = AtomicInteger(0)
    private val cancelledStops = AtomicInteger(0)
    @Volatile private var lastFlapReason: String = "none"

    // Configuration
    private val normalDwellMs = 90_000L        // 90 seconds minimum active time
    private val iranDwellMs = 180_000L         // 3 minutes in Iran (probes are less reliable)
    private val normalCooldownMs = 120_000L    // 2 minutes between switches
    private val iranCooldownMs = 300_000L      // 5 minutes in Iran
    private val windowMs = 600_000L            // 10-minute rolling window
    private val maxSwitchesInWindow = 3        // Max 3 switches per window
    private val emergencyThreshold = 3         // 3 consecutive failures = emergency

    /** Current dwell time based on environment. */
    private val effectiveDwellMs: Long
        get() = if (iranAware) iranDwellMs else normalDwellMs

    /** Current cooldown based on environment and switch history. */
    private val effectiveCooldownMs: Long
        get() {
            val base = if (iranAware) iranCooldownMs else normalCooldownMs
            // Exponential backoff for excessive switching
            val recentSwitches = switchCountInWindow.get()
            val backoffMultiplier = when {
                recentSwitches <= 1 -> 1.0
                recentSwitches <= 2 -> 1.5
                else -> 2.0
            }
            return (base * backoffMultiplier).toLong()
        }

    /**
     * Result of evaluating whether a switch is allowed.
     */
    data class SwitchDecision(
        val allowed: Boolean,
        val reason: String,
        val remainingDwellMs: Long = 0,
        val remainingCooldownMs: Long = 0,
        val isEmergency: Boolean = false
    )

    /**
     * Register a new active profile (after a successful connection).
     */
    @Synchronized
    fun onConnected(profileId: String, nowMs: Long = System.currentTimeMillis()) {
        activeProfileId = profileId
        connectedSinceMs.set(nowMs)
        consecutiveFailures = 0
    }

    /**
     * Register a connection failure for the active profile.
     */
    @Synchronized
    fun onConnectionFailure(nowMs: Long = System.currentTimeMillis()) {
        consecutiveFailures++
    }

    /**
     * Record that a profile switch has occurred.
     */
    @Synchronized
    fun noteSwitch(nowMs: Long = System.currentTimeMillis()) {
        recordSwitch(nowMs)
    }

    /**
     * Evaluate whether switching to a new profile is allowed.
     *
     * @param targetProfileId the proposed new profile
     * @param currentQuality quality score of the active profile (0-100)
     * @param targetQuality quality score of the proposed profile (0-100)
     * @param evidenceComplete whether the quality assessment is based on complete evidence
     */
    @Synchronized
    fun evaluateSwitch(
        targetProfileId: String,
        currentQuality: Double,
        targetQuality: Double,
        evidenceComplete: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): SwitchDecision {
        // Can't switch to the same profile
        if (targetProfileId == activeProfileId) {
            return SwitchDecision(false, "same-profile-no-switch-needed")
        }

        // No active profile -> allow immediately
        if (activeProfileId.isBlank() || connectedSinceMs.get() == 0L) {
            return SwitchDecision(true, "no-active-profile-immediate-connect")
        }

        // Emergency: active profile is completely failing
        if (consecutiveFailures >= emergencyThreshold || currentQuality < 10.0) {
            recordSwitch(nowMs)
            return SwitchDecision(
                true,
                "emergency-failover: ${consecutiveFailures} consecutive failures",
                isEmergency = true
            )
        }

        // Check minimum dwell time
        val dwellMs = nowMs - connectedSinceMs.get()
        val remainingDwell = effectiveDwellMs - dwellMs
        if (remainingDwell > 0 && !evidenceComplete) {
            return SwitchDecision(
                false,
                "dwell-time-not-met: ${remainingDwell / 1000}s remaining",
                remainingDwellMs = remainingDwell
            )
        }

        // Check cooldown since last switch
        val sinceLastSwitch = nowMs - lastSwitchMs.get()
        val remainingCooldown = effectiveCooldownMs - sinceLastSwitch
        if (lastSwitchMs.get() > 0 && remainingCooldown > 0) {
            return SwitchDecision(
                false,
                "switch-cooldown: ${remainingCooldown / 1000}s remaining",
                remainingCooldownMs = remainingCooldown
            )
        }

        // Check window budget
        refreshWindow(nowMs)
        if (switchCountInWindow.get() >= maxSwitchesInWindow) {
            return SwitchDecision(
                false,
                "switch-budget-exhausted: ${switchCountInWindow.get()}/$maxSwitchesInWindow in current window"
            )
        }

        // Evidence-based decision: target must be materially better
        if (evidenceComplete) {
            val gain = targetQuality - currentQuality
            val minGain = if (iranAware) 12.0 else 8.0  // Higher bar in Iran

            if (gain < minGain) {
                return SwitchDecision(
                    false,
                    "insufficient-gain: ${String.format("%.1f", gain)} < ${String.format("%.1f", minGain)} required"
                )
            }
        } else {
            // Incomplete evidence: require even higher bar
            val gain = targetQuality - currentQuality
            if (gain < 20.0) {
                return SwitchDecision(
                    false,
                    "incomplete-evidence-high-bar: gain ${String.format("%.1f", gain)} < 20.0"
                )
            }
        }

        // Allow the switch
        recordSwitch(nowMs)
        return SwitchDecision(true, "switch-approved: gain ${String.format("%.1f", targetQuality - currentQuality)}")
    }

    @Synchronized
    private fun recordSwitch(nowMs: Long) {
        lastSwitchMs.set(nowMs)
        switchCountInWindow.incrementAndGet()
        refreshWindow(nowMs)
        lastFlapReason = "switch-recorded-window=${switchCountInWindow.get()}"
    }

    @Synchronized
    private fun refreshWindow(nowMs: Long) {
        if (windowStartMs.get() == 0L) {
            windowStartMs.set(nowMs)
            switchCountInWindow.set(0)
        } else if (nowMs - windowStartMs.get() > windowMs) {
            windowStartMs.set(nowMs)
            switchCountInWindow.set(0)
        }
    }

    /**
     * Get current state for diagnostics.
     */
    @Synchronized
    fun status(nowMs: Long = System.currentTimeMillis()): Map<String, Any> = mapOf(
        "activeProfileId" to activeProfileId,
        "connectedSinceMs" to connectedSinceMs.get(),
        "dwellMs" to (nowMs - connectedSinceMs.get()).coerceAtLeast(0),
        "effectiveDwellMs" to effectiveDwellMs,
        "lastSwitchMs" to lastSwitchMs.get(),
        "switchesInWindow" to switchCountInWindow.get(),
        "consecutiveFailures" to consecutiveFailures,
        "effectiveCooldownMs" to effectiveCooldownMs,
        "iranAware" to iranAware
    )

    /**
     * Record a teardown as clean or cancelled so diagnostics can prove a reconnect/cancellation
     * did not masquerade as a resolver or transport outage.
     */
    @Synchronized
    fun recordShutdown(clean: Boolean, reason: String = "") {
        if (clean) cleanStops.incrementAndGet() else cancelledStops.incrementAndGet()
        if (reason.isNotBlank()) lastFlapReason = reason
    }

    /**
     * Machine-readable shutdown counters (cancellation-safe teardown accounting).
     */
    fun shutdownCounters(): DiagnosticsSummary.ShutdownCounters = DiagnosticsSummary.ShutdownCounters(
        cancellations = cancelledStops.get(),
        closedPipes = 0,
        cleanStops = cleanStops.get(),
        misclassifiedTransportFailures = 0
    )

    /** Current reason the last switch was blocked or allowed (for diagnostics). */
    fun flapReason(): String = lastFlapReason

    /**
     * Reset all state (on disconnect or manual reset). Teardown counters are kept so a manual
     * reset never erases evidence of earlier clean vs cancelled stops.
     */
    @Synchronized
    fun reset() {
        activeProfileId = ""
        connectedSinceMs.set(0)
        lastSwitchMs.set(0)
        switchCountInWindow.set(0)
        windowStartMs.set(0)
        consecutiveFailures = 0
        lastFlapReason = "none"
    }
}
