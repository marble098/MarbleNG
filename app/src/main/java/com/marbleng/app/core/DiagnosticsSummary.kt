package com.marbleng.app.core

/**
 * Machine-readable diagnostics block builder.
 *
 * Problem addressed (MARBLE_DIAGNOSTICS_BLOCK_V1):
 * The task requires a single machine-readable block that surfaces:
 *   - resolver category counts,
 *   - ranking decision reason,
 *   - uncertain vs failed distinction,
 *   - profile preflight validity,
 *   - session flapping reason,
 *   - cancellation-safe shutdown counters.
 *
 * This object is pure and dependency-free so it can be unit tested on the JVM and rendered into a
 * stable `key=value;key=value` string that RuntimeDiagnostics / Bug Finder can append to a report.
 */
object DiagnosticsSummary {

    /** Shutdown / cancellation-safe counters for the current teardown behaviour. */
    data class ShutdownCounters(
        val cancellations: Int = 0,
        val closedPipes: Int = 0,
        val cleanStops: Int = 0,
        val misclassifiedTransportFailures: Int = 0
    )

    /** Snapshot of the ranking engine decision for diagnostics. */
    data class RankingDecision(
        val selectedProfileId: String = "",
        val decisionReason: String = "",
        val uncertainCount: Int = 0,
        val failedCount: Int = 0,
        val healthCount: Int = 0,
        val flapReason: String = ""
    )

    /**
     * Render the full machine-readable block.
     *
     * @param resolver the resolver failure summary.
     * @param preflight profile preflight verdicts (profileId -> verdict).
     * @param ranking the ranking decision snapshot.
     * @param shutdown the shutdown/cancellation counters.
     */
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

    /**
     * Verify that the resolver portion of a rendered block matches the raw lines it was derived
     * from. Returns true when the classifier reproduces the same category counts.
     */
    fun resolverBlockConsistent(rawLines: List<String>, resolver: ResolverFailureSummary): Boolean =
        ResolverFailureClassifier.rawConsistentWithSummary(rawLines, resolver)

    private fun sanitize(value: String): String =
        value.replace(';', '-').replace('|', '/').take(160).ifBlank { "unknown" }
}
