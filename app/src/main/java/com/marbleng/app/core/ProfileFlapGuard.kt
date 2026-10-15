package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ProfileFlapGuard(
    private val iranAware: Boolean = true
) {
    private var activeProfileId: String = ""
    private val connectedSinceMs = AtomicLong(0)
    private val lastSwitchMs = AtomicLong(0)
    private val switchCountInWindow = AtomicInteger(0)
    private val windowStartMs = AtomicLong(0)
    private var consecutiveFailures = 0

    private val cleanStops = AtomicInteger(0)
    private val cancelledStops = AtomicInteger(0)
    @Volatile private var lastFlapReason: String = "none"

    private val normalDwellMs = 90_000L
    private val iranDwellMs = 180_000L
    private val normalCooldownMs = 120_000L
    private val iranCooldownMs = 300_000L
    private val windowMs = 600_000L
    private val maxSwitchesInWindow = 3
    private val emergencyThreshold = 3

    private val effectiveDwellMs: Long
        get() = if (iranAware) iranDwellMs else normalDwellMs

    private val effectiveCooldownMs: Long
        get() {
            val base = if (iranAware) iranCooldownMs else normalCooldownMs

            val recentSwitches = switchCountInWindow.get()
            val backoffMultiplier = when {
                recentSwitches <= 1 -> 1.0
                recentSwitches <= 2 -> 1.5
                else -> 2.0
            }
            return (base * backoffMultiplier).toLong()
        }

    data class SwitchDecision(
        val allowed: Boolean,
        val reason: String,
        val remainingDwellMs: Long = 0,
        val remainingCooldownMs: Long = 0,
        val isEmergency: Boolean = false
    )

    @Synchronized
    fun onConnected(profileId: String, nowMs: Long = System.currentTimeMillis()) {
        activeProfileId = profileId
        connectedSinceMs.set(nowMs)
        consecutiveFailures = 0
    }

    @Synchronized
    fun onConnectionFailure(nowMs: Long = System.currentTimeMillis()) {
        consecutiveFailures++
    }

    @Synchronized
    fun noteSwitch(nowMs: Long = System.currentTimeMillis()) {
        recordSwitch(nowMs)
    }

    @Synchronized
    fun evaluateSwitch(
        targetProfileId: String,
        currentQuality: Double,
        targetQuality: Double,
        evidenceComplete: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): SwitchDecision {
        if (targetProfileId == activeProfileId) {
            return SwitchDecision(false, "same-profile-no-switch-needed")
        }

        if (activeProfileId.isBlank() || connectedSinceMs.get() == 0L) {
            return SwitchDecision(true, "no-active-profile-immediate-connect")
        }

        if (consecutiveFailures >= emergencyThreshold || currentQuality < 10.0) {
            recordSwitch(nowMs)
            return SwitchDecision(
                true,
                "emergency-failover: ${consecutiveFailures} consecutive failures",
                isEmergency = true
            )
        }

        val dwellMs = nowMs - connectedSinceMs.get()
        val remainingDwell = effectiveDwellMs - dwellMs
        if (remainingDwell > 0 && !evidenceComplete) {
            return SwitchDecision(
                false,
                "dwell-time-not-met: ${remainingDwell / 1000}s remaining",
                remainingDwellMs = remainingDwell
            )
        }

        val sinceLastSwitch = nowMs - lastSwitchMs.get()
        val remainingCooldown = effectiveCooldownMs - sinceLastSwitch
        if (lastSwitchMs.get() > 0 && remainingCooldown > 0) {
            return SwitchDecision(
                false,
                "switch-cooldown: ${remainingCooldown / 1000}s remaining",
                remainingCooldownMs = remainingCooldown
            )
        }

        refreshWindow(nowMs)
        if (switchCountInWindow.get() >= maxSwitchesInWindow) {
            return SwitchDecision(
                false,
                "switch-budget-exhausted: ${switchCountInWindow.get()}/$maxSwitchesInWindow in current window"
            )
        }

        if (evidenceComplete) {
            val gain = targetQuality - currentQuality
            val minGain = if (iranAware) 12.0 else 8.0

            if (gain < minGain) {
                return SwitchDecision(
                    false,
                    "insufficient-gain: ${String.format("%.1f", gain)} < ${String.format("%.1f", minGain)} required"
                )
            }
        } else {
            val gain = targetQuality - currentQuality
            if (gain < 20.0) {
                return SwitchDecision(
                    false,
                    "incomplete-evidence-high-bar: gain ${String.format("%.1f", gain)} < 20.0"
                )
            }
        }

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

    @Synchronized
    fun recordShutdown(clean: Boolean, reason: String = "") {
        if (clean) cleanStops.incrementAndGet() else cancelledStops.incrementAndGet()
        if (reason.isNotBlank()) lastFlapReason = reason
    }

    fun shutdownCounters(): DiagnosticsSummary.ShutdownCounters = DiagnosticsSummary.ShutdownCounters(
        cancellations = cancelledStops.get(),
        closedPipes = 0,
        cleanStops = cleanStops.get(),
        misclassifiedTransportFailures = 0
    )

    fun flapReason(): String = lastFlapReason

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
