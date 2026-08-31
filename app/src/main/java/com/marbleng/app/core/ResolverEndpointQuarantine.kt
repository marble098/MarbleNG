package com.marbleng.app.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Session-scoped quarantine for DNS resolver endpoints.
 *
 * Problem addressed (MARBLE_RESOLVER_QUARANTINE_V1):
 * Resolver endpoints that repeatedly fail in a *decisive* way (expired certificates, repeated
 * TLS handshake failures, repeated EOF bursts, or repeated deadline storms) were previously kept
 * in the rotation and retried on every lookup. In Iran's filtering windows the resolver pool then
 * spent most of a session hammering endpoints that could never succeed, which produced the
 * "very large retained resolver error counts and DoH deadline storms" seen in the 4.0.0 logs.
 *
 * This engine applies a strict circuit breaker per endpoint:
 * - a single transient timeout does NOT quarantine anything;
 * - repeated handshake failures, EOF bursts, deadline storms and certificate-expired errors move
 *   the endpoint to a progressively longer quarantine;
 * - a healthy resolver that is currently winning is "sticky" for the rest of the session and is
 *   never pre-empted by an unproven peer;
 * - cancellation / closed-pipe events that belong to teardown never count toward quarantine.
 *
 * The engine is pure (no Android, no network) so it can be deterministically unit tested.
 */
class ResolverEndpointQuarantine(
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    /** Per-endpoint, per-session circuit-breaker state. */
    data class EndpointState(
        val endpoint: String,
        val consecutiveHandshakeFails: AtomicInteger = AtomicInteger(0),
        val consecutiveEofFails: AtomicInteger = AtomicInteger(0),
        val deadlineStormStreak: AtomicInteger = AtomicInteger(0),
        val certExpiredCount: AtomicInteger = AtomicInteger(0),
        val quarantineUntil: AtomicLong = AtomicLong(0),
        val quarantineLevel: AtomicInteger = AtomicInteger(0)
    ) {
        /** Total quarantine pressure: how many decisive failures happened in this session. */
        val decisiveFailures: Int
            get() = consecutiveHandshakeFails.get() + consecutiveEofFails.get() +
                deadlineStormStreak.get() + certExpiredCount.get()
    }

    /** Per-session stickiness: a healthy winning endpoint reused within the session. */
    private val stickyEndpoints = ConcurrentHashMap<String, Long>() // endpoint -> lastSuccessAt

    private val states = ConcurrentHashMap<String, EndpointState>()

    /** Current quarantine lengths for escalating backoff. */
    private val quarantineStepsMs = listOf(0L, 15_000L, 60_000L, 5 * 60_000L, 30 * 60_000L)

    /** How many consecutive decisive failures of each class trigger a quarantine step. */
    private val handshakeThreshold = 2
    private val eofThreshold = 3
    private val deadlineStormThreshold = 4
    private val certExpiredThreshold = 1

    /** Max number of entries we keep per session to bound memory. */
    private val maxEntries = 32

    /**
     * Record an outcome for an endpoint.
     *
     * @param kind classification of the failure or success.
     * @param endpoint the resolver endpoint URL/id.
     */
    fun record(kind: ResolverFailureKind?, endpoint: String) {
        if (endpoint.isBlank()) return
        val st = state(endpoint)

        when (kind) {
            // Success resets transient pressure but keeps proven quarantine expiry untouched.
            null -> {
                st.consecutiveHandshakeFails.set(0)
                st.consecutiveEofFails.set(0)
                stickyEndpoints[endpoint] = now()
                return
            }
            // Shutdown-safe events never count toward quarantine.
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
                // Other/Poisoned counts as mild pressure, not quarantine-worthy on its own.
                st.deadlineStormStreak.incrementAndGet()
            }
        }
        trim()
    }

    /** Record a hard success: resets consecutive failure streaks for the endpoint. */
    fun recordSuccess(endpoint: String) = record(null, endpoint)

    private fun escalate(endpoint: String, reason: String) {
        val st = state(endpoint)
        val level = (st.quarantineLevel.get() + 1).coerceAtMost(quarantineStepsMs.size - 1)
        st.quarantineLevel.set(level)
        st.quarantineUntil.set(now() + quarantineStepsMs[level])
    }

    /** Whether the endpoint is currently quarantined (still inside its backoff window). */
    fun isQuarantined(endpoint: String): Boolean {
        val st = states[endpoint] ?: return false
        if (st.quarantineUntil.get() > now()) return true
        // A quarantine window has elapsed: clear pressure so the endpoint can return.
        st.quarantineLevel.set(0)
        return false
    }

    /** Whether the endpoint is sticky (won within this session and not quarantined). */
    fun isSticky(endpoint: String, stickyTtlMs: Long = 10 * 60_000L): Boolean {
        val at = stickyEndpoints[endpoint] ?: return false
        if (isQuarantined(endpoint)) return false
        return now() - at in 0L..stickyTtlMs
    }

    /** Ordered candidate endpoints: sticky + unquarantined first, then unproven, then quarantined. */
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

    /** Snapshot of every tracked endpoint for diagnostics. */
    fun snapshot(): List<EndpointState> =
        states.values.sortedBy { it.endpoint }

    /** Reset all session state (on network/session change). */
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
