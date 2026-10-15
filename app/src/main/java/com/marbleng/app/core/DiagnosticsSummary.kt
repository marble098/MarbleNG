package com.marbleng.app.core

object DiagnosticsSummary {
    data class ShutdownCounters(
        val cancellations: Int = 0,
        val closedPipes: Int = 0,
        val cleanStops: Int = 0,
        val misclassifiedTransportFailures: Int = 0
    )

    data class RankingDecision(
        val selectedProfileId: String = "",
        val decisionReason: String = "",
        val uncertainCount: Int = 0,
        val failedCount: Int = 0,
        val healthCount: Int = 0,
        val flapReason: String = ""
    )

    fun render(
        resolver: ResolverFailureSummary,
        preflight: Map<String, ProfilePreflightValidator.PreflightVerdict>,
        ranking: RankingDecision,
        shutdown: ShutdownCounters
    ): String = buildString {
        append(resolver.toMachineReadable())
        append(";")
        append(ProfilePreflightValidator.renderMachineReadable(preflight))
        append(";ranking.selected=").append(ranking.selectedProfileId.take(24))
        append(";ranking.reason=").append(sanitize(ranking.decisionReason))
        append(";ranking.uncertain=").append(ranking.uncertainCount)
        append(";ranking.failed=").append(ranking.failedCount)
        append(";ranking.healthy=").append(ranking.healthCount)
        append(";ranking.flap_reason=").append(sanitize(ranking.flapReason))
        append(";shutdown.cancelled=").append(shutdown.cancellations)
        append(";shutdown.closed_pipe=").append(shutdown.closedPipes)
        append(";shutdown.clean_stops=").append(shutdown.cleanStops)
        append(";shutdown.misclassified_failures=").append(shutdown.misclassifiedTransportFailures)
    }

    fun resolverBlockConsistent(rawLines: List<String>, resolver: ResolverFailureSummary): Boolean =
        ResolverFailureClassifier.rawConsistentWithSummary(rawLines, resolver)

    private fun sanitize(value: String): String =
        value.replace(';', '-').replace('|', '/').take(160).ifBlank { "unknown" }
}
