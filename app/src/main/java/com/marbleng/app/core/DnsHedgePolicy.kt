package com.marbleng.app.core

/**
 * Policy for a future full wire-format DoH/DoQ stub. It is deliberately transport-independent so
 * it can be tested now without pretending Xray's A/AAAA DNS outbound supports every qtype.
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
        val hedgeDelayMs: Int
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
        val tailGap = (primary.p95Ms - primary.medianMs).coerceAtLeast(0)
        val delay = (primary.medianMs / 2 + tailGap / 3).coerceIn(150, 350)
        return Plan(primary.id, secondary?.id, delay)
    }
}
