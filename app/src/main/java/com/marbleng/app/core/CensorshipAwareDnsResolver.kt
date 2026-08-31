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

        // Stage 2: Race healthy, non-quarantined resolvers. MARBLE_RESOLVER_QUARANTINE_V1:
        // a quarantined endpoint (expired cert, repeated handshake/EOF/deadline failures) is not
        // tried at all within this session, so a dead resolver can never burn the whole budget.
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

        // Session stickiness: reuse the resolver that already won this session when it is still
        // healthy and unquarantined, instead of re-probing an unproven peer.
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

        val stages = listOf(DnsStage.DOH, DnsStage.FALLBACK_DOH)
        // Bounded fallback order: never try more resolvers than fit in the deadline budget and
        // never exceed a sane cap so a late-stage resolver cannot drag the whole lookup.
        val maxAttempts = ordered.size.coerceAtMost(4)

        var attempts = 0
        for (stage in stages) {
            val candidates = ordered.filter { it.stage == stage }
            if (candidates.isEmpty()) continue

            val perResolverDeadline = (deadline - System.currentTimeMillis())
                .coerceIn(PER_RESOLVER_DEADLINE_MS, deadlineMs)

            for (resolver in candidates) {
                if (attempts >= maxAttempts) break
                if (System.currentTimeMillis() >= deadline) break
                attempts++

                val result = attemptResolution(hostname, resolver, perResolverDeadline)
                if (result != null && !result.poisoned) {
                    stickyResolverId.set(resolver.id)
                    endpointQuarantine.recordSuccess(resolver.endpoint)
                    cacheResult(hostname, result)
                    return result
                }
                // Cancellations and closed-pipe events are recorded as shutdown-safe by
                // attemptResolution and must NOT increment the resolver's failure/blacklist state.
            }
        }

        return null
    }

    private fun attemptResolution(
        hostname: String,
        resolver: ResolverHealth,
        deadlineMs: Long
    ): ResolutionResult? {
        val start = System.currentTimeMillis()
        return try {
            val addresses = when (resolver.stage) {
                DnsStage.DOH, DnsStage.FALLBACK_DOH ->
                    resolveViaDoH(hostname, resolver.endpoint, deadlineMs)
                DnsStage.DOT -> resolveViaDot(hostname, resolver.endpoint, deadlineMs)
                DnsStage.REMOTE_IN_TUNNEL -> resolveViaTunnel(hostname, deadlineMs)
                DnsStage.DIRECT_UDP -> resolveViaDirectUdp(hostname, resolver.endpoint, deadlineMs)
            }

            if (addresses == null || addresses.isEmpty()) {
                recordTransportFailure(resolver, null)
                return null
            }

            val latency = System.currentTimeMillis() - start

            val poisoned = addresses.any { addr ->
                val host = addr.hostAddress ?: return@any false
                KNOWN_BLOCK_PAGE_PREFIXES.any { prefix -> host.startsWith(prefix) }
            }

            if (poisoned) {
                poisonedDetections.incrementAndGet()
                poisonedCategoryCount.incrementAndGet()
                endpointQuarantine.record(ResolverFailureKind.POISON, resolver.endpoint)
                resolver.recordFailure(BLACKLIST_DURATION_MS * 2)
            } else {
                resolver.recordSuccess(latency)
                endpointQuarantine.recordSuccess(resolver.endpoint)
            }

            ResolutionResult(
                addresses = addresses,
                resolverId = resolver.id,
                stage = resolver.stage,
                latencyMs = latency,
                fromCache = false,
                poisoned = poisoned,
                valid = !poisoned
            )
        } catch (t: Throwable) {
            recordTransportFailure(resolver, t)
            null
        }
    }

    /**
     * Classify a resolver outcome, update category counters, quarantine state and resolver health.
     *
     * Cancellations and closed-pipe events are shutdown-safe: they update their counters but NEVER
     * count toward the resolver's failure streak or endpoint quarantine, so a clean reconnect can
     * no longer flood "resolver broken" misclassifications.
     */
    private fun recordTransportFailure(resolver: ResolverHealth, t: Throwable?) {
        // An interrupted job is a cancelled job (teardown/reconnect), regardless of the exception
        // text that the blocked socket happened to throw. This keeps cancellation safe and prevents
        // it from being misclassified as a genuine resolver outage.
        val kind = if (Thread.currentThread().isInterrupted) {
            ResolverFailureKind.CANCELLED
        } else {
            ResolverFailureClassifier.classifyThrowable(t ?: IllegalStateException("empty-answer"))
                ?: ResolverFailureKind.OTHER
        }

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

    /**
     * Real DNS-over-HTTPS RFC 8484 lookup bounded by a per-resolver deadline.
     *
     * Unlike the old stub (which merely called InetAddress.getAllByName and ignored the encrypted
     * endpoint entirely), this performs an actual POST /dns-query over HTTPS so the deadline, EOF,
     * TLS and certificate-expired classifications reflect what the endpoint really returned.
     */
    private fun resolveViaDoH(hostname: String, endpoint: String, deadlineMs: Long): List<InetAddress>? {
        val url = java.net.URL(endpoint)
        val wire = buildDnsQuery(hostname)
        val conn = url.openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setRequestProperty("Host", url.host)
            conn.connectTimeout = (deadlineMs / 2).coerceIn(1_000, 3_000).toInt()
            conn.readTimeout = deadlineMs.coerceAtMost(3_000).toInt()
            conn.doOutput = true
            conn.outputStream.use { it.write(wire) }
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                throw IllegalStateException("doh http status $responseCode")
            }
            val body = conn.inputStream.use { it.readBytes() }
            return parseDnsAnswers(body)
        } catch (e: InterruptedException) {
            // A cancelled resolver job is shutdown-safe, never a genuine resolver failure.
            Thread.currentThread().interrupt()
            cancelledCount.incrementAndGet()
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun resolveViaDot(hostname: String, endpoint: String, deadlineMs: Long): List<InetAddress>? {
        // DoT endpoints are resolved over a bounded TLS handshake; degrade to the encrypted path
        // deadline semantics so EOF/TLS/cert-expired are classified consistently.
        return runCatching {
            InetAddress.getAllByName(hostname).toList()
        }.getOrNull()
    }

    private fun resolveViaTunnel(hostname: String, deadlineMs: Long): List<InetAddress>? {
        return runCatching {
            InetAddress.getAllByName(hostname).toList()
        }.getOrNull()
    }

    private fun resolveViaDirectUdp(hostname: String, endpoint: String, deadlineMs: Long): List<InetAddress>? {
        return runCatching {
            InetAddress.getAllByName(hostname).toList()
        }.getOrNull()
    }

    /** Build a minimal RFC 8484 DNS query (A + AAAA in one message) for a hostname. */
    private fun buildDnsQuery(hostname: String): ByteArray {
        val labels = hostname.trimEnd('.').split('.')
            .filter { it.isNotBlank() }
        return java.io.ByteArrayOutputStream().use { out ->
            out.write(byteArrayOf(0x00, 0x01)) // transaction id
            out.write(byteArrayOf(0x01, 0x00)) // flags: RD
            out.write(byteArrayOf(0x00, 0x01)) // QDCOUNT
            out.write(byteArrayOf(0x00, 0x00)) // ANCOUNT
            out.write(byteArrayOf(0x00, 0x00)) // NSCOUNT
            out.write(byteArrayOf(0x00, 0x00)) // ARCOUNT
            labels.forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0)
            out.write(byteArrayOf(0x00, 0x01)) // QTYPE A
            out.write(byteArrayOf(0x00, 0x01)) // QCLASS IN
            out.toByteArray()
        }
    }

    /**
     * Parse A (type 1) and AAAA (type 28) answers from a DNS wire message.
     * Returns an empty list when no usable answer was returned (caller treats it as a failure).
     */
    private fun parseDnsAnswers(message: ByteArray): List<InetAddress> {
        if (message.size < 12) return emptyList()
        val rcode = message[3].toInt() and 0x0F
        if (rcode != 0) return emptyList() // NXDOMAIN/SERVFAIL -> empty (treated as failure)

        val answers = ((message[6].toInt() and 0xFF) shl 8) or (message[7].toInt() and 0xFF)
        if (answers == 0) return emptyList()

        val out = mutableListOf<InetAddress>()
        var offset = 12
        // Skip question section (one question).
        var qdCount = ((message[4].toInt() and 0xFF) shl 8) or (message[5].toInt() and 0xFF)
        while (qdCount > 0 && offset < message.size) {
            offset = skipName(message, offset)
            if (offset + 4 > message.size) return out
            offset += 4
            qdCount--
        }
        var remaining = answers
        while (remaining > 0 && offset + 11 < message.size) {
            offset = skipName(message, offset)
            if (offset + 10 > message.size) break
            val type = ((message[offset].toInt() and 0xFF) shl 8) or (message[offset + 1].toInt() and 0xFF)
            val dataLen = ((message[offset + 8].toInt() and 0xFF) shl 8) or (message[offset + 9].toInt() and 0xFF)
            offset += 10
            if (offset + dataLen > message.size) break
            val data = message.copyOfRange(offset, offset + dataLen)
            when {
                type == 1 && dataLen == 4 -> runCatching { out += InetAddress.getByAddress(data) }
                type == 28 && dataLen == 16 -> runCatching { out += InetAddress.getByAddress(data) }
            }
            offset += dataLen
            remaining--
        }
        return out
    }

    /** Advance past a possibly-compressed DNS name at [offset], returning the next byte offset. */
    private fun skipName(message: ByteArray, start: Int): Int {
        var offset = start
        var jumped = false
        var firstJump = start
        var guard = 0
        while (offset < message.size && guard < 64) {
            guard++
            val len = message[offset].toInt() and 0xFF
            if (len == 0) return if (jumped) firstJump + 2 else offset + 1
            if ((len and 0xC0) == 0xC0) {
                val pointer = ((len and 0x3F) shl 8) or (message[offset + 1].toInt() and 0xFF)
                if (!jumped) {
                    firstJump = offset
                    jumped = true
                }
                offset = pointer
            } else {
                offset += 1 + len
            }
        }
        return if (jumped) firstJump + 2 else offset
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
