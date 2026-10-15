package com.marbleng.app.core

class HandoverCoordinator {
    enum class State { IDLE, WARMING, VERIFIED, DRAINING }
    enum class Action { NONE, START_CHALLENGER, SWITCH_NEW_FLOWS, DRAIN_OLD, ABORT }

    data class Snapshot(
        val state: State,
        val activeRoute: String,
        val challengerRoute: String,
        val generation: Long,
        val activeSinceMs: Long = 0,
        val minimumDwellMs: Long = 0,
        val switchCountInWindow: Int = 0
    )

    private var state = State.IDLE
    private var active = ""
    private var challenger = ""
    private var generation = 0L

    private var activeSinceMs = 0L
    private var lastSwitchMs = 0L
    private var switchCountInWindow = 0
    private var windowStartMs = 0L

    private val normalDwellMs = 90_000L
    private val iranDwellMs = 180_000L
    private val normalCooldownMs = 120_000L
    private val iranCooldownMs = 300_000L
    private val windowDurationMs = 600_000L
    private val maxSwitchesPerWindow = 3

    private var iranModeActive = false

    private var adaptiveScorer: AdaptiveAegisScorer? = null
    private var adaptiveFingerprint: String = ""

    @Synchronized
    fun setIranMode(active: Boolean) {
        iranModeActive = active
    }

    @Synchronized
    fun setAdaptiveScorer(scorer: AdaptiveAegisScorer?, fingerprint: String) {
        adaptiveScorer = scorer
        adaptiveFingerprint = fingerprint
    }

    data class AdaptiveMigration(
        val action: Action,
        val target: String,
        val decision: AdaptiveAegisScorer.Decision
    )

    @Synchronized
    fun consultAdaptiveMigration(
        activeRoute: String,
        quality: AdaptiveAegisScorer.Quality,
        candidateScores: Map<String, Double>,
        nowMs: Long = System.currentTimeMillis()
    ): AdaptiveMigration {
        val scorer = adaptiveScorer
        if (scorer == null || adaptiveFingerprint.isBlank()) {
            return AdaptiveMigration(
                action = Action.NONE,
                target = "",
                decision = AdaptiveAegisScorer.Decision(
                    keep = true, switchTo = null, state = AdaptiveAegisScorer.State.HEALTHY,
                    uncertain = false, catastrophic = false, reason = "adaptive scorer not configured"
                )
            )
        }
        val decision = scorer.evaluate(adaptiveFingerprint, activeRoute, quality, candidateScores, nowMs)
        val target = decision.switchTo
        if (decision.keep || target.isNullOrBlank() || target == activeRoute) {
            return AdaptiveMigration(action = Action.NONE, target = "", decision = decision)
        }
        val action = if (decision.catastrophic) {
            forceBegin(activeRoute, target)
        } else {
            begin(activeRoute, target, identityGuard = false)
        }
        return AdaptiveMigration(action = action, target = target, decision = decision)
    }

    private val effectiveDwellMs: Long
        get() = if (iranModeActive) iranDwellMs else normalDwellMs

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

        val now = System.currentTimeMillis()
        if (active.isNotEmpty() && activeSinceMs > 0) {
            val dwellMs = now - activeSinceMs
            if (dwellMs < effectiveDwellMs) {
                return Action.NONE
            }
        }

        if (lastSwitchMs > 0) {
            val sinceLast = now - lastSwitchMs
            if (sinceLast < effectiveCooldownMs) {
                return Action.NONE
            }
        }

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

    @Synchronized
    fun dwellTimeMet(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (activeSinceMs == 0L) return true
        return (nowMs - activeSinceMs) >= effectiveDwellMs
    }

    @Synchronized
    fun canSwitch(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (state != State.IDLE) return false
        if (!dwellTimeMet(nowMs)) return false
        if (lastSwitchMs > 0 && nowMs - lastSwitchMs < effectiveCooldownMs) return false
        refreshWindow(nowMs)
        return switchCountInWindow < maxSwitchesPerWindow
    }

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
