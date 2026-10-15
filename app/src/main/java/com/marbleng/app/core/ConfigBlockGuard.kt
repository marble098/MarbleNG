package com.marbleng.app.core

import java.util.concurrent.ConcurrentHashMap

class ConfigBlockGuard(
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    data class Decision(
        val report: Boolean,
        val attempts: Int,
        val quietForMs: Long,
        val suppressedBefore: Int = 0
    ) {
        val nextRetryInMs: Long get() = quietForMs
    }

    private class State(
        var lastAt: Long,
        var attempts: Int,
        var suppressed: Int,
        var quietForMs: Long
    )

    private val states = ConcurrentHashMap<String, State>()

    /**
     * Fold one refusal into the guard. Pure with respect to the caller's control flow: it never
     * decides *whether* to connect, only whether this refusal is news.
     */
    fun observe(profileId: String, reason: String): Decision {
        val key = key(profileId, reason)
        val at = now()
        val state = states.computeIfAbsent(key) { State(0L, 0, 0, BASE_QUIET_MS) }
        synchronized(state) {
            val folded = state.attempts > 0 && at - state.lastAt < state.quietForMs
            state.lastAt = at
            state.attempts += 1

            state.quietForMs = if (folded) quietWindowMs(state.quietForMs) else BASE_QUIET_MS
            if (folded) {
                state.suppressed += 1
                return Decision(
                    report = false,
                    attempts = state.attempts,
                    quietForMs = state.quietForMs,
                    suppressedBefore = state.suppressed
                )
            }
            val suppressed = state.suppressed
            state.suppressed = 0
            return Decision(
                report = true,
                attempts = state.attempts,
                quietForMs = state.quietForMs,
                suppressedBefore = suppressed
            )
        }
    }

    fun isQuiet(profileId: String, reason: String): Boolean {
        val state = states[key(profileId, reason)] ?: return false
        synchronized(state) {
            if (state.attempts == 0) return false
            return now() - state.lastAt < state.quietForMs
        }
    }

    fun reset(profileId: String = "") {
        if (profileId.isBlank()) {
            states.clear()
            return
        }
        val prefix = profileId.trim() + ":"
        states.keys.filter { it.startsWith(prefix) }.forEach { states.remove(it) }
    }

    fun summary(): String {
        if (states.isEmpty()) return "configBlock=none"
        val total = states.values.sumOf { it.attempts }
        val suppressed = states.values.sumOf { it.suppressed }
        val reasons = states.keys.mapNotNull { key ->
            key.substringAfter(":").takeIf { it.isNotEmpty() }?.take(48)
        }.distinct().sorted()
        return "configBlock=profiles=${states.size},observations=$total,suppressed=$suppressed," +
            "reasons=${reasons.joinToString("|").ifEmpty { "-" }}"
    }

    fun trackedStates(): Int = states.size

    private fun key(profileId: String, reason: String): String =
        profileId.trim() + ":" + reason.trim().lowercase()

    private fun quietWindowMs(current: Long): Long = (current * 2).coerceAtMost(MAX_QUIET_MS)

    companion object {
        const val BASE_QUIET_MS = 30_000L

        const val MAX_QUIET_MS = 600_000L
    }
}
