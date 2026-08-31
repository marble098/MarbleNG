package com.marbleng.app.core

/**
 * Make-before-break decision model. Runtime integration remains gated until Xray is resident with
 * multiple outbounds; the current external-process architecture cannot truthfully drain flows.
 *
 * MARBLE_ANTI_FLAP_V80: Enhanced with dwell time enforcement, hysteresis, and
 * Iran-aware minimum active durations to prevent rapid profile switching.
 * Also integrates with ProfileFlapGuard for comprehensive switch control.
 */
class HandoverCoordinator {
    enum class State { IDLE, WARMING, VERIFIED, DRAINING }
    enum class Action { NONE, START_CHALLENGER, SWITCH_NEW_FLOWS, DRAIN_OLD, ABORT }

    data class Snapshot(
        val state: State,
        val activeRoute: String,
        val challengerRoute: String,
        val generation: Long,
        /** MARBLE_ANTI_FLAP_V80: when the active route was established */
        val activeSinceMs: Long = 0,
        /** MARBLE_ANTI_FLAP_V80: minimum time the active route must stay before switching */
        val minimumDwellMs: Long = 0,
        /** MARBLE_ANTI_FLAP_V80: number of switches attempted in the current window */
        val switchCountInWindow: Int = 0
    )

    private var state = State.IDLE
    private var active = ""
    private var challenger = ""
    private var generation = 0L

    // MARBLE_ANTI_FLAP_V80: Dwell time tracking
    private var activeSinceMs = 0L
    private var lastSwitchMs = 0L
    private var switchCountInWindow = 0
    private var windowStartMs = 0L

    // MARBLE_ANTI_FLAP_V80: Configuration
    private val normalDwellMs = 90_000L       // 90 seconds normal
    private val iranDwellMs = 180_000L         // 3 minutes in Iran
    private val normalCooldownMs = 120_000L    // 2 minutes between switches
    private val iranCooldownMs = 300_000L      // 5 minutes in Iran
    private val windowDurationMs = 600_000L    // 10-minute rolling window
    private val maxSwitchesPerWindow = 3

    // MARBLE_ANTI_FLAP_V80: Iran mode flag
    private var iranModeActive = false

    /**
     * Set whether Iran Mode is active (affects dwell times and hysteresis).
     */
    @Synchronized
    fun setIranMode(active: Boolean) {
        iranModeActive = active
    }

    /**
     * Current minimum dwell time based on environment.
     */
    private val effectiveDwellMs: Long
        get() = if (iranModeActive) iranDwellMs else normalDwellMs

    /**
     * Current cooldown time based on environment and switch history.
     */
    private val effectiveCooldownMs: Long
        get() {
            val base = if (iranModeActive) iranCooldownMs else normalCooldownMs
            val backoff = when (switchCountInWindow) {
                0 -> 1.0
                1 -> 1.0
                2 -> 1.5
                else -> 2.0
            }
            return (base * backoff).toLong()
        }

    @Synchronized
    fun begin(activeRoute: String, challengerRoute: String, identityGuard: Boolean): Action {
        if (identityGuard || activeRoute.isBlank() || challengerRoute.isBlank() ||
            activeRoute == challengerRoute || state != State.IDLE
        ) return Action.NONE

        // MARBLE_ANTI_FLAP_V80: Check dwell time
        val now = System.currentTimeMillis()
        if (active.isNotEmpty() && activeSinceMs > 0) {
            val dwellMs = now - activeSinceMs
            if (dwellMs < effectiveDwellMs) {
                // Dwell time not met — reject the switch unless it's an emergency
                return Action.NONE
            }
        }

        // MARBLE_ANTI_FLAP_V80: Check cooldown since last switch
        if (lastSwitchMs > 0) {
            val sinceLast = now - lastSwitchMs
            if (sinceLast < effectiveCooldownMs) {
                return Action.NONE
            }
        }

        // MARBLE_ANTI_FLAP_V80: Check window budget
        refreshWindow(now)
        if (switchCountInWindow >= maxSwitchesPerWindow) {
            return Action.NONE
        }

        active = activeRoute
        challenger = challengerRoute
        generation++
        state = State.WARMING
        return Action.START_CHALLENGER
    }

    @Synchronized
    fun verified(route: String, healthy: Boolean): Action {
        if (state != State.WARMING || route != challenger) return Action.NONE
        if (!healthy) {
            clear()
            return Action.ABORT
        }
        state = State.VERIFIED
        return Action.SWITCH_NEW_FLOWS
    }

    @Synchronized
    fun switched(route: String): Action {
        if (state != State.VERIFIED || route != challenger) return Action.NONE
        state = State.DRAINING

        // MARBLE_ANTI_FLAP_V80: Record the switch
        val now = System.currentTimeMillis()
        lastSwitchMs = now
        activeSinceMs = now
        switchCountInWindow++
        refreshWindow(now)

        return Action.DRAIN_OLD
    }

    @Synchronized
    fun drained(): Action {
        if (state != State.DRAINING) return Action.NONE
        active = challenger
        challenger = ""
        state = State.IDLE

        // MARBLE_ANTI_FLAP_V80: Update active since
        activeSinceMs = System.currentTimeMillis()

        return Action.NONE
    }

    @Synchronized
    fun abort(): Action {
        if (state == State.IDLE) return Action.NONE
        clear()
        return Action.ABORT
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        state = state,
        activeRoute = active,
        challengerRoute = challenger,
        generation = generation,
        activeSinceMs = activeSinceMs,
        minimumDwellMs = effectiveDwellMs,
        switchCountInWindow = switchCountInWindow
    )

    /**
     * MARBLE_ANTI_FLAP_V80: Check if the active route has met its minimum dwell time.
     */
    @Synchronized
    fun dwellTimeMet(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (activeSinceMs == 0L) return true  // No active route
        return (nowMs - activeSinceMs) >= effectiveDwellMs
    }

    /**
     * MARBLE_ANTI_FLAP_V80: Check if a switch is allowed right now.
     */
    @Synchronized
    fun canSwitch(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (state != State.IDLE) return false
        if (!dwellTimeMet(nowMs)) return false
        if (lastSwitchMs > 0 && nowMs - lastSwitchMs < effectiveCooldownMs) return false
        refreshWindow(nowMs)
        return switchCountInWindow < maxSwitchesPerWindow
    }

    /**
     * MARBLE_ANTI_FLAP_V80: Force a switch (emergency override, bypasses dwell/cooldown).
     */
    @Synchronized
    fun forceBegin(activeRoute: String, challengerRoute: String): Action {
        if (activeRoute.isBlank() || challengerRoute.isBlank() ||
            activeRoute == challengerRoute || state != State.IDLE
        ) return Action.NONE

        active = activeRoute
        challenger = challengerRoute
        generation++
        state = State.WARMING
        return Action.START_CHALLENGER
    }

    /**
     * MARBLE_ANTI_FLAP_V80: Reset all state (on disconnect).
     */
    @Synchronized
    fun reset() {
        state = State.IDLE
        active = ""
        challenger = ""
        generation = 0L
        activeSinceMs = 0L
        lastSwitchMs = 0L
        switchCountInWindow = 0
        windowStartMs = 0L
    }

    private fun clear() {
        challenger = ""
        state = State.IDLE
    }

    private fun refreshWindow(nowMs: Long) {
        if (windowStartMs == 0L) {
            windowStartMs = nowMs
            switchCountInWindow = 0
        } else if (nowMs - windowStartMs > windowDurationMs) {
            windowStartMs = nowMs
            switchCountInWindow = 0
        }
    }
}
