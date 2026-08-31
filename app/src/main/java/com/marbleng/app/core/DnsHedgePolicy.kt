package com.marbleng.app.core

/**
 * Policy for a future full wire-format DoH/DoQ stub. It is deliberately transport-independent so
 * it can be tested now without pretending Xray's A/AAAA DNS outbound supports every qtype.
 *
 * MARBLE_CENSORSHIP_AWARE_DNS_V80: Now integrates with CensorshipAwareDnsResolver
 * for multi-layer parallel resolution with per-session scoring, anti-poisoning,
 * and automatic blacklist of degraded providers.
 */
object DnsHedgePolicy {
    data class Resolver(
        val id: String,
        val medianMs: Int,
        val p95Ms: Int,
        val successPercent: Int
    )

    data class Plan(
        val primaryId: String,
        val secondaryId: String?,
        val hedgeDelayMs: Int,
        /** MARBLE_CENSORSHIP_AWARE_DNS_V80: additional fallback resolvers in priority order */
        val fallbackIds: List<String> = emptyList(),
        /** MARBLE_CENSORSHIP_AWARE_DNS_V80: whether this plan is Iran-mode aware */
        val iranAware: Boolean = false,
        /** MARBLE_CENSORSHIP_AWARE_DNS_V80: per-resolver deadlines in ms */
        val perResolverDeadlineMs: Int = 2_500,
        /** MARBLE_CENSORSHIP_AWARE_DNS_V80: overall deadline in ms */
        val overallDeadlineMs: Int = 4_000,
        /** MARBLE_CENSORSHIP_AWARE_DNS_V80: max consecutive fails before blacklisting */
        val maxConsecutiveFails: Int = 3
    )

    fun plan(resolvers: List<Resolver>): Plan? {
        val usable = resolvers
            .filter { it.id.isNotBlank() && it.successPercent > 0 }
            .sortedWith(
                compareByDescending<Resolver> { it.successPercent }
                    .thenBy { it.medianMs.coerceAtLeast(0) }
                    .thenBy { it.p95Ms.coerceAtLeast(0) }
            )
        val primary = usable.firstOrNull() ?: return null
        val secondary = usable.drop(1).firstOrNull()

        // MARBLE_CENSORSHIP_AWARE_DNS_V80: Build a richer fallback chain
        val fallbackIds = usable.drop(2).map { it.id }
        val tailGap = (primary.p95Ms - primary.medianMs).coerceAtLeast(0)

        // In censorship environments, hedge delay should be shorter to fail fast
        // but overall deadline should be longer to allow more fallback attempts
        val delay = (primary.medianMs / 2 + tailGap / 3).coerceIn(150, 350)

        // Adaptive per-resolver deadline based on observed latency
        val perResolverDeadline = (primary.p95Ms * 1.5 + 500).toInt()
            .coerceIn(1_500, 4_000)
        val overallDeadline = (perResolverDeadline * 2 + 1_000)
            .coerceIn(3_000, 8_000)

        return Plan(
            primaryId = primary.id,
            secondaryId = secondary?.id,
            hedgeDelayMs = delay,
            fallbackIds = fallbackIds,
            iranAware = true,
            perResolverDeadlineMs = perResolverDeadline,
            overallDeadlineMs = overallDeadline,
            maxConsecutiveFails = 3
        )
    }

    /**
     * MARBLE_CENSORSHIP_AWARE_DNS_V80: Build an Iran-optimized plan.
     * Uses shorter per-resolver deadlines and longer overall deadlines
     * to maximize the chance of finding a working resolver under censorship.
     */
    fun iranOptimizedPlan(resolvers: List<Resolver>): Plan? {
        val usable = resolvers
            .filter { it.id.isNotBlank() && it.successPercent > 0 }
            .sortedWith(
                compareByDescending<Resolver> { it.successPercent }
                    .thenBy { it.medianMs.coerceAtLeast(0) }
                    .thenBy { it.p95Ms.coerceAtLeast(0) }
            )
        val primary = usable.firstOrNull() ?: return null
        val secondary = usable.drop(1).firstOrNull()
        val fallbackIds = usable.drop(2).map { it.id }

        // Iran-specific: very short per-resolver deadline, long overall to try many
        return Plan(
            primaryId = primary.id,
            secondaryId = secondary?.id,
            hedgeDelayMs = 100, // Very short hedge delay
            fallbackIds = fallbackIds,
            iranAware = true,
            perResolverDeadlineMs = 2_000, // 2s per resolver
            overallDeadlineMs = 6_000,     // 6s overall
            maxConsecutiveFails = 2        // Blacklist faster
        )
    }
}
