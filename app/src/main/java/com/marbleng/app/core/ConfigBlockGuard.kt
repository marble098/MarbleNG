package com.marbleng.app.core

import java.util.concurrent.ConcurrentHashMap

// MARBLE_CORE_CONFIG_SUPERSET_V165
//
// ─────────────────────────────────────────────────────────────────────────────────────────────
// A config-level refusal is a *state*, not an event — and it must stop being a storm
// ─────────────────────────────────────────────────────────────────────────────────────────────
//
// The reported session logged 31 `XRAY profile-preflight-rejected` lines for two profiles inside
// ten seconds:
//
//     22:04:06.555039Z … profile=2765c26d2960 reason=Unsupported VLESS • pick a server with TLS/REALITY
//     22:04:06.797781Z … profile=2765c26d2960 reason=Unsupported VLESS • pick a server with TLS/REALITY
//     … 24 more of the same line at ~200 ms intervals …
//
// Nothing about the profile changed between those lines. Each one was a full start attempt: the
// service promoted a foreground notification, ran the preflight, refused, tore down, and the
// auto-reconnect path handed the same profile straight back. Twenty-nine of those attempts were
// pure waste — and the diagnostic file they filled is the only place the repetition is visible at
// all, which is why "why is it not connecting?" reports have to be read out of a wall of identical
// rows.
//
// This guard makes a config refusal cheap to repeat and impossible to drown in:
//
//  · the **first** time a profile is refused for a reason, it is reported;
//  · repeats of *the same (profile, reason)* inside a quiet window are counted and suppressed, and
//    the window doubles per repeat up to a cap — the longer a caller storms, the quieter it gets;
//  · a **different** reason for the same profile is a new state and is reported at once, because
//    the reason changing is the only news a config-blocked profile can deliver;
//  · [reset] on a settings change or a re-imported node gives the user their immediate feedback
//    back, so nothing is ever silenced across a change they can see.
//
// It is deliberately not a "do not retry" latch: reconnect, failover and the Quick Settings tile
// keep their own schedules. What changes is that a repeated refusal no longer costs an event, and
// that the very first one carries the count of everything it stood in for.
class ConfigBlockGuard(
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /** One observation of a (profile, reason) pair. */
    data class Decision(
        /** Report this refusal to the diagnostic plane (and the user). */
        val report: Boolean,
        /** How many times this exact refusal has been observed since the last reset. */
        val attempts: Int,
        /** How long a repeat stays suppressed from now. */
        val quietForMs: Long,
        /** Suppressed repeats folded into the decision that did report. */
        val suppressedBefore: Int = 0
    ) {
        val nextRetryInMs: Long get() = quietForMs
    }

    // All four fields are only ever read or written while holding the monitor on the instance, so no
    // additional visibility machinery is needed on the fields themselves.
    private class State(
        var lastAt: Long,
        var attempts: Int,
        var suppressed: Int,
        /** The window a *repeat* is folded into this state for; see [quietWindowMs]. */
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
            // A folded repeat buys the next one more silence; anything that actually got reported
            // starts the window over. A caller that storms every 200 ms therefore converges on one
            // event per [MAX_QUIET_MS] instead of one per attempt, while a profile that is finally
            // fixed — or finally breaks in a new way — is reported the moment it is seen.
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

    /**
     * True while this exact refusal is inside its quiet window, i.e. while a caller may skip the
     * work that would only end in the same refusal again. Used by the connect path to answer a
     * repeated auto-reconnect *before* promoting a foreground service.
     */
    fun isQuiet(profileId: String, reason: String): Boolean {
        val state = states[key(profileId, reason)] ?: return false
        synchronized(state) {
            if (state.attempts == 0) return false
            return now() - state.lastAt < state.quietForMs
        }
    }

    /** Drop the quiet window for one profile (any reason), or for everything when it is blank. */
    fun reset(profileId: String = "") {
        if (profileId.isBlank()) {
            states.clear()
            return
        }
        val prefix = profileId.trim() + ":"
        states.keys.filter { it.startsWith(prefix) }.forEach { states.remove(it) }
    }

    /** One compact line for the diagnostic bundle: what is blocked, and how much noise was folded. */
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

    /** How many distinct (profile, reason) states are currently tracked. */
    fun trackedStates(): Int = states.size

    private fun key(profileId: String, reason: String): String =
        profileId.trim() + ":" + reason.trim().lowercase()

    /** One doubling of the fold window, capped so a permanently blocked profile still resurfaces. */
    private fun quietWindowMs(current: Long): Long = (current * 2).coerceAtMost(MAX_QUIET_MS)

    companion object {
        /** A refusal of the same shape stays quiet for at least this long after it is seen once. */
        const val BASE_QUIET_MS = 30_000L

        /** …and never longer than this, so a stuck profile is re-reported at least every 10 minutes. */
        const val MAX_QUIET_MS = 600_000L

    }
}
