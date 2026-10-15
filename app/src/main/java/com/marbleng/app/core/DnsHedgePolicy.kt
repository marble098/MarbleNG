package com.marbleng.app.core

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
        val fallbackIds: List<String> = emptyList(),
        val iranAware: Boolean = false,
        val perResolverDeadlineMs: Int = 2_500,
        val overallDeadlineMs: Int = 4_000,
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

        val fallbackIds = usable.drop(2).map { it.id }
        val tailGap = (primary.p95Ms - primary.medianMs).coerceAtLeast(0)

        val delay = (primary.medianMs / 2 + tailGap / 3).coerceIn(150, 350)

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

        return Plan(
            primaryId = primary.id,
            secondaryId = secondary?.id,
            hedgeDelayMs = 100,
            fallbackIds = fallbackIds,
            iranAware = true,
            perResolverDeadlineMs = 2_000,
            overallDeadlineMs = 6_000,
            maxConsecutiveFails = 2
        )
    }
}
