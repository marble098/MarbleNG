package com.marbleng.app.core

import java.util.ArrayDeque

/**
 * MARBLE_PROBE_TARGET_WINDOWS_V238 — one measurement window per probe target.
 *
 * The report (v11.2.3, P0-2) showed the legacy probe mixing windows across targets: when the
 * pinned literal went silent and the pivot raced to another provider, the old code cleared the
 * window — losing the evidence it had already collected for that target — and when it later
 * returned, the two providers' samples interleaved in one window. Both halves are wrong:
 *
 *  - a window belongs to **one (target, network)** pair;
 *  - returning to a previously measured target **restores** its window instead of starting cold,
 *    so a target that is filtered for one cycle does not poison the next;
 *  - windows are bounded (host count and age), so a walk over many targets cannot grow memory.
 *
 * Each sample may carry a **cold** flag (report P0-2): the first probe after a gap or a target
 * change pays a TLS handshake, so it counts as an attempt but is excluded from the RTT and
 * jitter statistics by [LinkQualityEstimator.summarizeMarked].
 */
class RouteSampleWindows(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val maxHosts: Int = DEFAULT_MAX_HOSTS,
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    private class Window(capacity: Int) {
        val samples = ArrayDeque<Int>(capacity)
        val cold = ArrayDeque<Boolean>(capacity)
        var lastUsedAtMs = 0L
    }

    private val windows = LinkedHashMap<String, Window>()

    @Synchronized
    fun add(host: String, sample: Int, nowMs: Long, cold: Boolean = false) {
        val window = windows[host] ?: Window(windowSize).also {
            if (windows.size >= maxHosts) evictOldest()
            windows[host] = it
        }
        window.lastUsedAtMs = nowMs
        window.samples.addLast(if (sample > 0) LinkQualityEstimator.sanitaryRtt(sample) else -1)
        window.cold.addLast(cold)
        while (window.samples.size > windowSize) window.samples.removeFirst()
        while (window.cold.size > windowSize) window.cold.removeFirst()
    }

    /** The raw outcomes of [host]; empty (never null) when the target has no live window. */
    @Synchronized
    fun outcomes(host: String, nowMs: Long): List<Int> {
        val window = windows[host] ?: return emptyList()
        if (nowMs - window.lastUsedAtMs > ttlMs) {
            windows.remove(host)
            return emptyList()
        }
        window.lastUsedAtMs = nowMs
        return window.samples.toList()
    }

    /** The outcomes plus their cold marks, index-aligned; for [LinkQualityEstimator.summarizeMarked]. */
    @Synchronized
    fun outcomesMarked(host: String, nowMs: Long): Pair<List<Int>, List<Boolean>> {
        val window = windows[host] ?: return emptyList<Int>() to emptyList()
        if (nowMs - window.lastUsedAtMs > ttlMs) {
            windows.remove(host)
            return emptyList<Int>() to emptyList()
        }
        window.lastUsedAtMs = nowMs
        return window.samples.toList() to window.cold.toList()
    }

    @Synchronized
    fun has(host: String): Boolean = windows.containsKey(host)

    @Synchronized
    fun clear(host: String? = null) {
        if (host == null) windows.clear() else windows.remove(host)
    }

    @Synchronized
    fun knownHosts(): List<String> = windows.keys.toList()

    private fun evictOldest() {
        val oldest = windows.entries.minByOrNull { it.value.lastUsedAtMs }?.key
        if (oldest != null) windows.remove(oldest)
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 12
        const val DEFAULT_MAX_HOSTS = 4
        const val DEFAULT_TTL_MS = 10L * 60_000L
    }
}
