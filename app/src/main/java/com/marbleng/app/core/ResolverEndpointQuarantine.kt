package com.marbleng.app.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ResolverEndpointQuarantine(
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    data class EndpointState(
        val endpoint: String,
        val consecutiveHandshakeFails: AtomicInteger = AtomicInteger(0),
        val consecutiveEofFails: AtomicInteger = AtomicInteger(0),
        val deadlineStormStreak: AtomicInteger = AtomicInteger(0),
        val certExpiredCount: AtomicInteger = AtomicInteger(0),
        val quarantineUntil: AtomicLong = AtomicLong(0),
        val quarantineLevel: AtomicInteger = AtomicInteger(0)
    ) {
        val decisiveFailures: Int
            get() = consecutiveHandshakeFails.get() + consecutiveEofFails.get() +
                deadlineStormStreak.get() + certExpiredCount.get()
    }

    private val stickyEndpoints = ConcurrentHashMap<String, Long>()

    private val states = ConcurrentHashMap<String, EndpointState>()

    private val quarantineStepsMs = listOf(0L, 15_000L, 60_000L, 5 * 60_000L, 30 * 60_000L)

    private val handshakeThreshold = 2
    private val eofThreshold = 3
    private val deadlineStormThreshold = 4
    private val certExpiredThreshold = 1

    private val maxEntries = 32

    fun record(kind: ResolverFailureKind?, endpoint: String) {
        if (endpoint.isBlank()) return
        val st = state(endpoint)

        when (kind) {
            null -> {
                st.consecutiveHandshakeFails.set(0)
                st.consecutiveEofFails.set(0)
                st.deadlineStormStreak.set(0)
                stickyEndpoints[endpoint] = now()
                return
            }

            ResolverFailureKind.CANCELLED, ResolverFailureKind.CLOSED_PIPE -> return
            ResolverFailureKind.CERT_EXPIRED -> {
                val c = st.certExpiredCount.incrementAndGet()
                if (c >= certExpiredThreshold) escalate(endpoint, "cert-expired")
            }
            ResolverFailureKind.TLS -> {
                val c = st.consecutiveHandshakeFails.incrementAndGet()
                if (c >= handshakeThreshold) escalate(endpoint, "tls-handshake")
            }
            ResolverFailureKind.EOF -> {
                val c = st.consecutiveEofFails.incrementAndGet()
                if (c >= eofThreshold) escalate(endpoint, "eof-burst")
            }
            ResolverFailureKind.DEADLINE -> {
                val c = st.deadlineStormStreak.incrementAndGet()
                if (c >= deadlineStormThreshold) escalate(endpoint, "deadline-storm")
            }
            else -> {
                st.deadlineStormStreak.incrementAndGet()
            }
        }
        trim()
    }

    fun recordSuccess(endpoint: String) = record(null, endpoint)

    private fun escalate(endpoint: String, reason: String) {
        val st = state(endpoint)
        val level = (st.quarantineLevel.get() + 1).coerceAtMost(quarantineStepsMs.size - 1)
        st.quarantineLevel.set(level)
        st.quarantineUntil.set(now() + quarantineStepsMs[level])
    }

    fun isQuarantined(endpoint: String): Boolean {
        val st = states[endpoint] ?: return false
        if (st.quarantineUntil.get() > now()) return true

        st.quarantineLevel.set(0)
        return false
    }

    fun isSticky(endpoint: String, stickyTtlMs: Long = 10 * 60_000L): Boolean {
        val at = stickyEndpoints[endpoint] ?: return false
        if (isQuarantined(endpoint)) return false
        return now() - at in 0L..stickyTtlMs
    }

    fun preferredOrder(endpoints: List<String>, stickyTtlMs: Long = 10 * 60_000L): List<String> {
        val orderedPairs = endpoints.map { it to states[it] }.sortedWith(
            compareBy<Pair<String, EndpointState?>> { pair ->
                val (endpoint, st) = pair
                when {
                    isQuarantined(endpoint) -> 3
                    isSticky(endpoint, stickyTtlMs) -> 0
                    st == null -> 1
                    else -> 2
                }
            }.thenBy { pair ->
                val st = pair.second
                -(st?.decisiveFailures ?: 0)
            }
        )
        return orderedPairs.map { it.first }
    }

    fun snapshot(): List<EndpointState> =
        states.values.sortedBy { it.endpoint }

    fun reset() {
        states.clear()
        stickyEndpoints.clear()
    }

    private fun state(endpoint: String): EndpointState =
        states.computeIfAbsent(endpoint) { EndpointState(endpoint) }

    private fun trim() {
        if (states.size <= maxEntries) return
        val extra = states.size - maxEntries
        val oldest = states.entries
            .sortedBy { it.value.decisiveFailures }
            .take(extra)
        oldest.forEach { states.remove(it.key) }
    }
}
