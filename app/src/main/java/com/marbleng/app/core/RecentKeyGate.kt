package com.marbleng.app.core

/**
 * MARBLE_STARTUP_DEDUP_V278 — lets one of several identical requests through.
 *
 * Start-up reaches the same work from two places (the repository's init and the network
 * listener's first callback). `tryEnter` answers true for the first caller with a given key
 * and false for a repeat of the same key inside the window; a different key always passes,
 * and keys do not disturb each other.
 */
class RecentKeyGate(private val capacity: Int = 32) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun tryEnter(key: String, nowMs: Long, windowMs: Long): Boolean {
        val last = seen[key]
        if (last != null && nowMs - last in 0 until windowMs) return false
        seen.remove(key)
        seen[key] = nowMs
        while (seen.size > capacity.coerceAtLeast(1)) {
            seen.remove(seen.keys.first())
        }
        return true
    }

    companion object {
        /** Init and the first network callback are a millisecond apart; two seconds covers both. */
        const val STARTUP_WINDOW_MS = 2_000L
    }
}
