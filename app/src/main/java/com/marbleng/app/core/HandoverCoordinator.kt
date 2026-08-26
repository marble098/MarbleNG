package com.marbleng.app.core

/**
 * Make-before-break decision model. Runtime integration remains gated until Xray is resident with
 * multiple outbounds; the current external-process architecture cannot truthfully drain flows.
 */
class HandoverCoordinator {
    enum class State { IDLE, WARMING, VERIFIED, DRAINING }
    enum class Action { NONE, START_CHALLENGER, SWITCH_NEW_FLOWS, DRAIN_OLD, ABORT }

    data class Snapshot(
        val state: State,
        val activeRoute: String,
        val challengerRoute: String,
        val generation: Long
    )

    private var state = State.IDLE
    private var active = ""
    private var challenger = ""
    private var generation = 0L

    @Synchronized
    fun begin(activeRoute: String, challengerRoute: String, identityGuard: Boolean): Action {
        if (identityGuard || activeRoute.isBlank() || challengerRoute.isBlank() ||
            activeRoute == challengerRoute || state != State.IDLE
        ) return Action.NONE
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
        return Action.DRAIN_OLD
    }

    @Synchronized
    fun drained(): Action {
        if (state != State.DRAINING) return Action.NONE
        active = challenger
        challenger = ""
        state = State.IDLE
        return Action.NONE
    }

    @Synchronized
    fun abort(): Action {
        if (state == State.IDLE) return Action.NONE
        clear()
        return Action.ABORT
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(state, active, challenger, generation)

    private fun clear() {
        challenger = ""
        state = State.IDLE
    }
}
