package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * MARBLE_CONNECT_WARMUP_V221 — the first ping and the first connection stop paying for DNS.
 *
 * Every measurement in the product begins with `AddressFamilyPolicy.resolveCandidates`, and every
 * call to it is a fresh resolver round trip. On a mobile path that is 40-300 ms per call, paid once
 * per probe *and* once when the tunnel is dialed — which is exactly the interval a user reads as
 * "the app is slow", because it is the interval between a tap and anything happening.
 *
 * This is a tiny, bounded, TTL'd cache in front of that resolver. It is deliberately not a resolver
 * of its own: it holds the addresses a real resolution just produced, hands them back for
 * [TTL_MS], and forgets the oldest entry past [MAX_HOSTS]. A negative answer is never cached, so a
 * name that failed once is asked again next time; an address that goes stale costs one extra
 * connect attempt, not a wrong route, because the measured path re-validates at TCP.
 *
 * The clock is injectable so the TTL and the eviction policy can be tested without sleeping.
 */
object ConnectWarmup {
    const val MARKER: String = "MARBLE_CONNECT_WARMUP_V221"

    const val TTL_MS: Long = 90_000L
    const val MAX_HOSTS: Int = 64
    const val WARM_TIMEOUT_MS: Int = 1_200
    const val MAX_WARM_HOSTS: Int = 24

    internal data class Entry(val addresses: List<InetAddress>, val atMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun hostKey(host: String): String =
        host.trim().lowercase().removePrefix("[").removeSuffix("]")

    fun remember(host: String, addresses: List<InetAddress>, nowMs: Long = System.currentTimeMillis()) {
        val key = hostKey(host)
        if (key.isBlank() || addresses.isEmpty()) return
        cache[key] = Entry(addresses.toList(), nowMs)
        if (cache.size > MAX_HOSTS) evictOldest()
    }

    fun cached(host: String, nowMs: Long = System.currentTimeMillis()): List<InetAddress>? {
        val key = hostKey(host)
        if (key.isBlank()) return null
        val entry = cache[key] ?: return null
        if (nowMs - entry.atMs > TTL_MS || nowMs < entry.atMs) {
            cache.remove(key, entry)
            return null
        }
        return entry.addresses
    }

    fun clear() = cache.clear()

    fun size(): Int = cache.size

    /**
     * Resolve every host that is not already warm, at most [MAX_WARM_HOSTS] of them, in parallel.
     *
     * Returns the number of hosts that answered. Failures are silent by design: a warmup is an
     * optimisation, and an optimisation that can surface an error dialog is a bug.
     */
    fun warm(
        hosts: List<String>,
        settings: AppSettings,
        timeoutMs: Int = WARM_TIMEOUT_MS,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val plan = AddressFamilyPolicy.plan(settings = settings)
        val pending = hosts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { hostKey(it) }
            .filter { cached(it, nowMs) == null }
            .take(MAX_WARM_HOSTS)
        if (pending.isEmpty()) return 0

        val budget = timeoutMs.coerceIn(250, 5_000)
        val pool = Executors.newFixedThreadPool(minOf(4, pending.size)) { runnable ->
            Thread(runnable, "marble-connect-warmup").apply { isDaemon = true }
        }
        return try {
            val futures = pending.map { host ->
                pool.submit(Callable {
                    val resolved = AddressFamilyPolicy.resolveCandidates(host, plan, budget)
                    if (resolved.isNotEmpty()) {
                        remember(host, resolved)
                        true
                    } else {
                        false
                    }
                })
            }
            // The wait is wall-clock; [nowMs] only stamps the entries, so a test can drive expiry
            // without pretending to be the scheduler.
            val deadline = System.currentTimeMillis() + budget + 250L
            futures.count { future ->
                runCatching {
                    future.get(
                        (deadline - System.currentTimeMillis()).coerceAtLeast(1L),
                        TimeUnit.MILLISECONDS
                    )
                }.getOrDefault(false)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun evictOldest() {
        val keep = cache.entries
            .sortedByDescending { it.value.atMs }
            .take(MAX_HOSTS)
            .associate { it.key to it.value }
        cache.keys.retainAll(keep.keys)
        if (cache.size > MAX_HOSTS) cache.clear()
    }
}
