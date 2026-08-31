package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the machine-readable diagnostics block (MARBLE_DIAGNOSTICS_BLOCK_V1).
 *
 * The block must consistently surface resolver category counts, ranking decision reason,
 * uncertain-vs-failed distinction, profile preflight validity, session flapping reason and
 * cancellation-safe shutdown counters — and the resolver portion must never disagree with the raw
 * log lines it was derived from.
 */
class DiagnosticsSummaryTest {

    private fun rawWithEofAndDeadline() = listOf(
        "context deadline exceeded while waiting for dns-query",
        "app/dns: failed to read response > unexpected EOF for DNS-over-HTTPS traffic",
        "context canceled during shutdown",
        "clean tunnel line"
    )

    @Test
    fun renderIncludesResolverCategories() {
        val summary = ResolverFailureClassifier.summarize(rawWithEofAndDeadline())
        val block = DiagnosticsSummary.render(
            resolver = summary,
            preflight = emptyMap(),
            ranking = DiagnosticsSummary.RankingDecision(),
            shutdown = DiagnosticsSummary.ShutdownCounters(cancellations = 3, closedPipes = 2)
        )
        assertTrue(block.contains("resolver.eof=1"))
        assertTrue(block.contains("resolver.deadline=1"))
        assertTrue(block.contains("resolver.transport_failures=2"))
        assertTrue(block.contains("shutdown.cancelled=3"))
        assertTrue(block.contains("shutdown.closed_pipe=2"))
        assertTrue(block.contains("ranking.uncertain="))
    }

    @Test
    fun renderIncludesPreflightAndRanking() {
        val preflight = mapOf(
            "healthy" to ProfilePreflightValidator.PreflightVerdict(ProfilePreflightValidator.Verdict.VALID, "structurally-valid"),
            "turkey-4-all" to ProfilePreflightValidator.PreflightVerdict(ProfilePreflightValidator.Verdict.INVALID, "vless-tls-missing-servername")
        )
        val block = DiagnosticsSummary.render(
            resolver = ResolverFailureSummary(),
            preflight = preflight,
            ranking = DiagnosticsSummary.RankingDecision(
                selectedProfileId = "healthy",
                decisionReason = "class=uncertain|probe=probe-timeout|history=history-strong",
                uncertainCount = 1, failedCount = 1, healthCount = 2,
                flapReason = "dwell-time-not-met"
            ),
            shutdown = DiagnosticsSummary.ShutdownCounters()
        )
        assertTrue(block.contains("preflight.valid=1"))
        assertTrue(block.contains("preflight.invalid=1"))
        assertTrue(block.contains("preflight.quarantined=turkey-4-all"))
        assertTrue(block.contains("ranking.reason=class=uncertain/probe=probe-timeout/history=history-strong"))
        assertTrue(block.contains("ranking.uncertain=1"))
        assertTrue(block.contains("ranking.flap_reason=dwell-time-not-met"))
    }

    @Test
    fun resolverBlockIsConsistentWithRawEvidence() {
        val raw = rawWithEofAndDeadline()
        val summary = ResolverFailureClassifier.summarize(raw)
        // EOF appears in the raw lines, so the summary must report it (this was the inconsistency).
        assertEquals(1, summary.eofCount)
        assertTrue(DiagnosticsSummary.resolverBlockConsistent(raw, summary))
    }

    @Test
    fun inconsistentSummaryIsDetected() {
        val raw = rawWithEofAndDeadline()
        // A summary that claims zero EOF while raw has EOF is inconsistent.
        val wrong = ResolverFailureSummary()
        assertFalse(DiagnosticsSummary.resolverBlockConsistent(raw, wrong))
    }

    @Test
    fun sanitizeStripsSeparators() {
        val block = DiagnosticsSummary.render(
            resolver = ResolverFailureSummary(),
            preflight = emptyMap(),
            ranking = DiagnosticsSummary.RankingDecision(decisionReason = "a;b|c"),
            shutdown = DiagnosticsSummary.ShutdownCounters()
        )
        // Semicolons are separators; injected values must be sanitised.
        assertFalse(block.contains("reason=a;b"))
        assertTrue(block.contains("reason=a-b/c"))
    }
}
