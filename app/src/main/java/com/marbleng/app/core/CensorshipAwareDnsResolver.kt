package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Censorship-Aware DNS Resolution Engine for MarbleNG.
 *
 * Problem addressed: The old DnsHedgePolicy had a single primary + secondary resolver
 * and relied on DoH deadlines that would burst during Iran's filtering windows (78+ retained
 * resolver errors seen in logs). This engine provides:
 *
 * 1. Multi-layer parallel resolution with measured scoring per network session.
 * 2. Stage-based fallback: DoH -> DoT -> remote-in-tunnel -> direct-UDP (as last resort).
 * 3. Bounded deadlines with fast-fail on per-provider timeout.
 * 4. Per-session cache with realistic TTL to avoid re-querying poisoned resolvers.
 * 5. Resolver health tracking with automatic exclusion of degraded providers.
 * 6. Anti-poisoning: validates responses against known-block-page ranges.
 * 7. Parallel racing: first valid response wins, slow/failed providers scored down.
 *
 * All decisions are network-scoped: a bad resolver on one ISP session does not
 * contaminate the next session's choices.
 */
object CensorshipAwareDnsResolver {

    /** Resolution stage ordered by censorship resistance. */
    enum class DnsStage(val label: String, val encrypted: Boolean) {
        DOH("DoH", encrypted = true),
        DOT("DoT", encrypted = true),
        REMOTE_IN_TUNNEL("Remote-in-Tunnel", encrypted = true),
        FALLBACK_DOH("Fallback DoH", encrypted = true),
        DIRECT_UDP("Direct-UDP", encrypted = false);
    }

    /** Per-resolver health telemetry for a given network session. */
    data class ResolverHealth(
        val id: String,
        val stage: DnsStage,
        val endpoint: String,
        val successCount: AtomicInteger = AtomicInteger(0),
        val failCount: AtomicInteger = AtomicInteger(0),
        val totalLatencyMs: AtomicLong = AtomicLong(0),
        val lastSuccessAt: AtomicLong = AtomicLong(0),
        val lastFailAt: AtomicLong = AtomicLong(0),
        val consecutiveFails: AtomicInteger = AtomicInteger(0),
        val blacklistedUntil: AtomicLong = AtomicLong(0)
    ) {
        val successRate: Double
            get() {
                val s = successCount.get()
                val f = failCount.get()
                val total = s + f
                return if (total == 0) 0.5 else s.toDouble() / total
            }

        val avgLatencyMs: Long
            get() {
                val s = successCount.get()
                return if (s == 0) 0L else (totalLatencyMs.get() / s).coerceIn(0, 30_000)
            }

        val isHealthy: Boolean
            get() {
                if (System.currentTimeMillis() < blacklistedUntil.get()) return false
                if (consecutiveFails.get() >= MAX_CONSECUTIVE_FAILS) return false
                return true
            }

        /** Weighted score: higher is better. Combines reliability and speed. */
        val score: Double
            get() {
                val rate = successRate
                val latency = avgLatencyMs.coerceAtLeast(1)
                val failPenalty = max(0, consecutiveFails.get() - 1) * 15.0
                val reliabilityScore = rate * 100.0
                val speedScore = 100.0 * (1.0 - latency.toDouble() / 10_000.0).coerceIn(0.0, 1.0)
                return (reliabilityScore * 0.60 + speedScore * 0.40 - failPenalty).coerceIn(-100.0, 100.0)
            }

        fun recordSuccess(latencyMs: Long) {
            successCount.incrementAndGet()
            totalLatencyMs.addAndGet(latencyMs)
            lastSuccessAt.set(System.currentTimeMillis())
            consecutiveFails.set(0)
        }

        fun recordFailure(blacklistDurationMs: Long = 0L) {
            failCount.incrementAndGet()
            lastFailAt.set(System.currentTimeMillis())
            val prev = consecutiveFails.incrementAndGet()
            if (blacklistDurationMs > 0 && prev >= BLACKLIST_THRESHOLD) {
                blacklistedUntil.set(System.currentTimeMillis() + blacklistDurationMs)
            }
        }

        fun snapshot(): ResolverHealth = ResolverHealth(
            id, stage, endpoint,
            AtomicInteger(successCount.get()),
            AtomicInteger(failCount.get()),
            AtomicLong(totalLatencyMs.get()),
            AtomicLong(lastSuccessAt.get()),
            AtomicLong(lastFailAt.get()),
            AtomicInteger(consecutiveFails.get()),
            AtomicLong(blacklistedUntil.get())
        )
    }

    /** Cache entry for resolved hostnames. */
    data class CacheEntry(
        val addresses: List<InetAddress>,
        val resolvedAt: Long,
        val ttlMs: Long,
        val resolverId: String,
        val stage: DnsStage
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - resolvedAt > ttlMs
    }

    /** Resolution result with full provenance. */
    data class ResolutionResult(
        val addresses: List<InetAddress>,
        val resolverId: String,
        val stage: DnsStage,
        val latencyMs: Long,
        val fromCache: Boolean,
        val poisoned: Boolean = false,
        val valid: Boolean = true
    )

    // Default resolver endpoints for each stage
    private val DEFAULT_DOH_RESOLVERS = listOf(
        Triple("cloudflare-doh", "https://1.1.1.1/dns-query", DnsStage.DOH),
        Triple("google-doh", "https://8.8.8.8/dns-query", DnsStage.DOH),
        Triple("quad9-doh", "https://9.9.9.9/dns-query", DnsStage.DOH),
        Triple("adguard-doh", "https://dns.adguard-dns.com/dns-query", DnsStage.DOH)
    )

    private val DEFAULT_FALLBACK_DOH = listOf(
        Triple("shecan-doh", "https://dns.shecan.ir/dns-query", DnsStage.FALLBACK_DOH),
        Triple("cloudflare-fallback", "https://1.0.0.1/dns-query", DnsStage.FALLBACK_DOH)
    )

    // Known Iranian block-page address ranges for anti-poisoning validation
    private val KNOWN_BLOCK_PAGE_PREFIXES = setOf(
        "10.10.34.",
        "192.168.51.",
        "172.24.0.",
        "fd19:"
    )

    // Per-session state
    private val sessionResolvers = CopyOnWriteArrayList<ResolverHealth>()
    private val sessionCache = ConcurrentHashMap<String, CacheEntry>()
    private val sessionNetworkKey = AtomicReference("")
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)
    private val totalResolutions = AtomicInteger(0)
    private val poisonedDetections = AtomicInteger(0)

    // MARBLE_RESOLVER_QUARANTINE_V1 / MARBLE_DIAGNOSTICS_CONSISTENCY_V1
    // Session-scoped endpoint quarantine + category counters so the resolver telemetry is the
    // same source of truth as the offline log classifier.
    private val endpointQuarantine = ResolverEndpointQuarantine()
    private val deadlineCount = AtomicInteger(0)
    private val eofCount = AtomicInteger(0)
    private val closedPipeCount = AtomicInteger(0)
    private val cancelledCount = AtomicInteger(0)
    private val tlsCount = AtomicInteger(0)
    private val certExpiredCount = AtomicInteger(0)
    private val poisonedCategoryCount = AtomicInteger(0)
    private val otherFailureCount = AtomicInteger(0)
    /** Session stickiness: the resolver that won the session and should be reused. */
    private val stickyResolverId = AtomicReference("")

    // MARBLE_SMART_RANK_V90: parallel racing DoH pool with a dedicated keep-alive connection pool
    // and fallback on ANY error class (not just timeout). The pool is lazily created once and can
    // be overridden for deterministic unit testing.
    @Volatile
    private var poolOverride: DohResolverPool? = null
    @Volatile
    private var resolverPool: DohResolverPool? = null
    private val raceExecutor: java.util.concurrent.ExecutorService by lazy {
        java.util.concurrent.Executors.newFixedThreadPool(4) { r ->
            Thread(r, "marble-doh-race").apply { isDaemon = true }
        }
    }

    /** Install a test/diagnostic pool; pass null to restore the production pool. */
    fun setPoolOverride(pool: DohResolverPool?) {
        poolOverride = pool
    }

    private fun pool(): DohResolverPool {
        poolOverride?.let { return it }
        val existing = resolverPool
        if (existing != null) return existing
        val created = DohResolverPool(HttpUrlConnectionDohTransport(), raceExecutor, DEADLINE_MS)
        resolverPool = created
        return created
    }

    private const val MAX_PARALLEL_DOH = 6

    private const val MAX_CONSECUTIVE_FAILS = 5
    private const val BLACKLIST_THRESHOLD = 3
    private const val CACHE_TTL_MS = 300_000L  // 5 minutes
    private const val SHORT_CACHE_TTL_MS = 30_000L  // 30 seconds for failed lookups
    private const val BLACKLIST_DURATION_MS = 60_000L  // 1 minute blacklist
    private const val MAX_CACHE_SIZE = 256
    private const val DEADLINE_MS = 4_000L
    private const val PER_RESOLVER_DEADLINE_MS = 2_500L

    /**
     * Initialize or refresh the resolver pool for a new network session.
     */
    fun initializeForSession(networkKey: String, settings: AppSettings): List<ResolverHealth> {
        val prev = sessionNetworkKey.get()
        if (prev == networkKey && sessionResolvers.isNotEmpty()) {
            return sessionResolvers.toList()
        }

        sessionNetworkKey.set(networkKey)
        sessionResolvers.clear()
        sessionCache.clear()
        cacheHits.set(0)
        cacheMisses.set(0)
        totalResolutions.set(0)
        poisonedDetections.set(0)
        endpointQuarantine.reset()
        deadlineCount.set(0); eofCount.set(0); closedPipeCount.set(0); cancelledCount.set(0)
        tlsCount.set(0); certExpiredCount.set(0); poisonedCategoryCount.set(0); otherFailureCount.set(0)
        stickyResolverId.set("")

        val resolvers = mutableListOf<ResolverHealth>()

        DEFAULT_DOH_RESOLVERS.forEach { (id, endpoint, stage) ->
            resolvers += ResolverHealth(id, stage, endpoint)
        }

        if (settings.dnsPrimaryDoH.isNotBlank()) {
            val existing = resolvers.any { it.endpoint == settings.dnsPrimaryDoH }
            if (!existing) {
                resolvers += ResolverHealth("user-primary", DnsStage.DOH, settings.dnsPrimaryDoH)
            }
        }
        if (settings.dnsSecondaryDoH.isNotBlank()) {
            val existing = resolvers.any { it.endpoint == settings.dnsSecondaryDoH }
            if (!existing) {
                resolvers += ResolverHealth("user-secondary", DnsStage.DOH, settings.dnsSecondaryDoH)
            }
        }

        DEFAULT_FALLBACK_DOH.forEach { (id, endpoint, stage) ->
            resolvers += ResolverHealth(id, stage, endpoint)
        }

        settings.freedomDnsCleanResolvers.split(',').map { it.trim() }.filter { it.isNotBlank() }
            .forEach { endpoint ->
                val existing = resolvers.any { it.endpoint == endpoint }
                if (!existing) {
                    resolvers += ResolverHealth("freedom-${resolvers.size}", DnsStage.FALLBACK_DOH, endpoint)
                }
            }

        sessionResolvers.addAll(resolvers)
        return resolvers.toList()
    }

    /**
     * Resolve a hostname with full censorship awareness.
     */
    fun resolve(
        hostname: String,
        networkKey: String,
        settings: AppSettings,
        deadlineMs: Long = DEADLINE_MS
    ): ResolutionResult? {
        totalResolutions.incrementAndGet()

        if (sessionNetworkKey.get() != networkKey) {
            initializeForSession(networkKey, settings)
        }

        // Stage 1: Check cache
        val cached = sessionCache[hostname]
        if (cached != null && !cached.isExpired) {
            cacheHits.incrementAndGet()
            return ResolutionResult(
                addresses = cached.addresses,
                resolverId = cached.resolverId,
                stage = cached.stage,
                latencyMs = 0,
                fromCache = true,
                poisoned = false,
                valid = true
            )
        }
        cacheMisses.incrementAndGet()

        if (sessionCache.size > MAX_CACHE_SIZE) {
            evictExpiredCache()
        }

        // Stage 2: race healthy, non-quarantined DoH resolvers in parallel (MARBLE_SMART_RANK_V90).
        // A quarantined endpoint (expired cert, repeated handshake/EOF/deadline failures) is not
        // tried at all within this session, and every error class — not just timeouts — falls back
        // automatically to the next provider.
        val deadline = System.currentTimeMillis() + deadlineMs
        val healthy = sessionResolvers.filter { resolver ->
            resolver.isHealthy && !endpointQuarantine.isQuarantined(resolver.endpoint)
        }.sortedByDescending { it.score }

        if (healthy.isEmpty()) {
            val worst = sessionResolvers.minByOrNull { it.score }
            if (worst != null) {
                worst.blacklistedUntil.set(0)
                worst.consecutiveFails.set(0)
            }
            return null
        }

        // Session stickiness: the resolver that already won this session races first.
        val sticky = stickyResolverId.get()
        val ordered = if (sticky.isNotBlank()) {
            val stickyResolver = healthy.firstOrNull { it.id == sticky && !endpointQuarantine.isQuarantined(it.endpoint) }
            if (stickyResolver != null) {
                listOf(stickyResolver) + healthy.filterNot { it.id == sticky }
            } else {
                healthy
            }
        } else {
            healthy
        }

        val wire = DnsWireCodec.buildQuery(hostname)
        val providers = ordered
            .filter { it.stage == DnsStage.DOH || it.stage == DnsStage.FALLBACK_DOH }
            .take(MAX_PARALLEL_DOH)
            .map { DohResolverPool.Provider(it.id, it.endpoint, internal = it.stage == DnsStage.FALLBACK_DOH) }

        val outcome = pool().raceResolve(
            wire = wire,
            providers = providers,
            perResolverTimeoutMs = (deadline - System.currentTimeMillis())
                .coerceIn(PER_RESOLVER_DEADLINE_MS, deadlineMs)
        )

        if (outcome.success) {
            val winner = ordered.firstOrNull { it.id == outcome.providerId }
            val addresses = DnsWireCodec.parseAnswers(outcome.body)
            if (addresses.isEmpty()) {
                // The resolver answered, but with no usable A/AAAA record.
                winner?.let { recordFailureKind(it, ResolverFailureKind.EOF) }
                return null
            }
            val poisoned = addresses.any { addr ->
                val host = addr.hostAddress ?: return@any false
                KNOWN_BLOCK_PAGE_PREFIXES.any { prefix -> host.startsWith(prefix) }
            }
            if (poisoned) {
                poisonedDetections.incrementAndGet()
                poisonedCategoryCount.incrementAndGet()
                winner?.let { r ->
                    endpointQuarantine.record(ResolverFailureKind.POISON, r.endpoint)
                    r.recordFailure(BLACKLIST_DURATION_MS * 2)
                }
                return ResolutionResult(
                    addresses = addresses,
                    resolverId = outcome.providerId ?: "doh",
                    stage = winner?.stage ?: DnsStage.DOH,
                    latencyMs = outcome.latencyMs,
                    fromCache = false,
                    poisoned = true,
                    valid = false
                )
            }
            winner?.let { r ->
                r.recordSuccess(outcome.latencyMs)
                endpointQuarantine.recordSuccess(r.endpoint)
            }
            stickyResolverId.set(outcome.providerId ?: "")
            val result = ResolutionResult(
                addresses = addresses,
                resolverId = outcome.providerId ?: "doh",
                stage = winner?.stage ?: DnsStage.DOH,
                latencyMs = outcome.latencyMs,
                fromCache = false,
                poisoned = false,
                valid = true
            )
            cacheResult(hostname, result)
            return result
        }

        // Every provider failed: record each failure. Cancellations and closed-pipe events remain
        // shutdown-safe (recorded but never quarantined), matching the shared failure classifier.
        outcome.failures.forEach { (provider, failure) ->
            val resolver = sessionResolvers.firstOrNull { it.id == provider.id } ?: return@forEach
            recordFailureKind(resolver, failure.failureKind ?: ResolverFailureKind.OTHER)
        }
        return null
    }

    /**
     * Record a classified resolver outcome, updating category counters, quarantine state and
     * resolver health.
     *
     * Cancellations and closed-pipe events are shutdown-safe: they update their counters but NEVER
     * count toward the resolver's failure streak or endpoint quarantine, so a clean reconnect can
     * no longer flood "resolver broken" misclassifications.
     */
    private fun recordFailureKind(resolver: ResolverHealth, kind: ResolverFailureKind) {
        when (kind) {
            ResolverFailureKind.CANCELLED -> cancelledCount.incrementAndGet()
            ResolverFailureKind.CLOSED_PIPE -> closedPipeCount.incrementAndGet()
            ResolverFailureKind.DEADLINE -> {
                deadlineCount.incrementAndGet()
                endpointQuarantine.record(kind, resolver.endpoint)
                resolver.recordFailure(BLACKLIST_DURATION_MS)
            }
            ResolverFailureKind.EOF -> {
                eofCount.incrementAndGet()
                endpointQuarantine.record(kind, resolver.endpoint)
                resolver.recordFailure(BLACKLIST_DURATION_MS)
            }
            ResolverFailureKind.TLS -> {
                tlsCount.incrementAndGet()
                endpointQuarantine.record(kind, resolver.endpoint)
                resolver.recordFailure(BLACKLIST_DURATION_MS)
            }
            ResolverFailureKind.CERT_EXPIRED -> {
                certExpiredCount.incrementAndGet()
                endpointQuarantine.record(kind, resolver.endpoint)
                // Cert-expired is decisive: blacklist the endpoint for this session.
                resolver.recordFailure(BLACKLIST_DURATION_MS * 4)
            }
            ResolverFailureKind.POISON -> poisonedCategoryCount.incrementAndGet()
            ResolverFailureKind.OTHER -> {
                otherFailureCount.incrementAndGet()
                endpointQuarantine.record(kind, resolver.endpoint)
                resolver.recordFailure(BLACKLIST_DURATION_MS)
            }
        }
    }

    private fun cacheResult(hostname: String, result: ResolutionResult) {
        if (result.addresses.isEmpty()) return
        val ttl = if (result.valid) CACHE_TTL_MS else SHORT_CACHE_TTL_MS
        sessionCache[hostname] = CacheEntry(
            addresses = result.addresses,
            resolvedAt = System.currentTimeMillis(),
            ttlMs = ttl,
            resolverId = result.resolverId,
            stage = result.stage
        )
    }

    private fun evictExpiredCache() {
        val toRemove = sessionCache.entries
            .filter { it.value.isExpired }
            .map { it.key }
        toRemove.forEach { sessionCache.remove(it) }
    }

    /**
     * Get current resolver health snapshot for diagnostics/reporting.
     */
    fun snapshot(): List<ResolverHealth> {
        return sessionResolvers.map { it.snapshot() }.sortedByDescending { it.score }
    }

    /**
     * Get session statistics.
     */
    fun stats(): Map<String, Int> = mapOf(
        "totalResolutions" to totalResolutions.get(),
        "cacheHits" to cacheHits.get(),
        "cacheMisses" to cacheMisses.get(),
        "poisonedDetections" to poisonedDetections.get(),
        "activeResolvers" to sessionResolvers.count { it.isHealthy },
        "totalResolvers" to sessionResolvers.size,
        "cacheSize" to sessionCache.size,
        "deadline" to deadlineCount.get(),
        "eof" to eofCount.get(),
        "closedPipe" to closedPipeCount.get(),
        "cancelled" to cancelledCount.get(),
        "tls" to tlsCount.get(),
        "certExpired" to certExpiredCount.get(),
        "quarantinedEndpoints" to endpointQuarantine.snapshot().count { it.quarantineUntil.get() > System.currentTimeMillis() }
    )

    /**
     * Current session resolver failure summary, derived from the same counters the live path
     * maintains. Consistent with [ResolverFailureClassifier.summarize] for offline log lines.
     */
    fun failureSummary(): ResolverFailureSummary = ResolverFailureSummary(
        deadlineCount = deadlineCount.get(),
        eofCount = eofCount.get(),
        closedPipeCount = closedPipeCount.get(),
        cancelledCount = cancelledCount.get(),
        tlsCount = tlsCount.get(),
        certExpiredCount = certExpiredCount.get(),
        poisonedCount = poisonedCategoryCount.get(),
        otherCount = otherFailureCount.get()
    )

    /** Shutdown-safe counters for teardown diagnostics. */
    fun shutdownCounters(): DiagnosticsSummary.ShutdownCounters = DiagnosticsSummary.ShutdownCounters(
        cancellations = cancelledCount.get(),
        closedPipes = closedPipeCount.get(),
        cleanStops = 0,
        misclassifiedTransportFailures = 0
    )

    /**
     * Build the recommended DNS server ordering for Xray config generation.
     */
    fun recommendedServerOrder(settings: AppSettings): List<Triple<String, DnsStage, String>> {
        val healthy = sessionResolvers.filter {
            it.isHealthy && !endpointQuarantine.isQuarantined(it.endpoint)
        }.sortedByDescending { it.score }
        if (healthy.isEmpty()) {
            return (DEFAULT_DOH_RESOLVERS + DEFAULT_FALLBACK_DOH).map { (id, endpoint, stage) ->
                Triple(id, stage, endpoint)
            }
        }
        // Session stickiness: a healthy winning resolver is preferred over an unproven peer.
        val sticky = stickyResolverId.get()
        val ordered = if (sticky.isNotBlank()) {
            healthy.sortedByDescending { it.id == sticky }
        } else {
            healthy
        }
        return ordered.map { Triple(it.id, it.stage, it.endpoint) }
    }
}
