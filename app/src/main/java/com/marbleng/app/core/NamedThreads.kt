package com.marbleng.app.core

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * MARBLE_NAMED_WORKERS_V278 — executors whose threads say what they are.
 *
 * `Executors.newFixedThreadPool(n)` names its threads `pool-<N>-thread-<M>`, and a native
 * tombstone or a heartbeat that says "pool-64-thread-1" names nothing. The factory keeps every
 * other default (non-daemon, normal priority) so swapping it in changes the label only.
 */
object NamedThreads {
    fun factory(prefix: String): ThreadFactory {
        val counter = AtomicInteger(0)
        return ThreadFactory { runnable -> Thread(runnable, name(prefix, counter.incrementAndGet())) }
    }

    /** The kernel keeps 15 characters of a thread name; the index is what must survive. */
    fun name(prefix: String, index: Int): String = if (index <= 1) prefix else "$prefix-$index"

    /** `pool-64-thread-3` and `marble-vpn-mon-2` → `pool-thread` and `marble-vpn-mon`. */
    fun family(threadName: String): String =
        threadName
            .replace(Regex("[-_#: ]?\\d+"), "")
            .trim('-', '_', ' ')
            .ifBlank { "unnamed" }

    /** The busiest thread families, largest first: `marble-vpn-mon=4,pool-thread=40`. */
    fun census(threadNames: Collection<String>, top: Int = 8): String =
        threadNames
            .groupingBy(::family)
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(top.coerceAtLeast(1))
            .joinToString(",") { "${it.key}=${it.value}" }
}
