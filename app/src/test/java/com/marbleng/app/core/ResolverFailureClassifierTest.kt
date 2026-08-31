package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for resolver summary consistency (MARBLE_DIAGNOSTICS_CONSISTENCY_V1).
 *
 * These lock in the fix for the finding where a summary reported 0 DNS EOF while the raw log lines
 * in the same run clearly contained EOF for DNS-over-HTTPS traffic. The summary is always derived
 * from the same raw lines through [ResolverFailureClassifier], so a category can never report zero
 * while the raw text contains it, and cancellations/closed-pipe teardown events are never counted
 * as genuine resolver failures.
 */
class ResolverFailureClassifierTest {

    @Test
    fun dohUnexpectedEofLineIsCountedAsEof() {
        // This exact shape previously fell through the narrow "app/dns: failed to read response
        // length > EOF" substring match and produced a 0 EOF summary.
        val raw = "app/dns: failed to read response > unexpected EOF for DNS-over-HTTPS traffic"
        assertEquals(ResolverFailureKind.EOF, ResolverFailureClassifier.classify(raw))
        val summary = ResolverFailureClassifier.summarize(listOf(raw))
        assertEquals(1, summary.eofCount)
        assertEquals(1, summary.transportFailures)
    }

    @Test
    fun deadlinePlusEofPlusCancellationAreCountedSeparately() {
        val lines = listOf(
            "context deadline exceeded while waiting for DoH dns-query response",
            "app/dns: failed to read response length > EOF",
            "context canceled during resolver shutdown",
            "app/dns: read/write on closed pipe during teardown"
        )
        val summary = ResolverFailureClassifier.summarize(lines)
        assertEquals(1, summary.deadlineCount)
        assertEquals(1, summary.eofCount)
        assertEquals(1, summary.cancelledCount)
        assertEquals(1, summary.closedPipeCount)

        // Cancellations and closed-pipe teardown events are NOT genuine transport failures.
        assertEquals(2, summary.transportFailures)
        assertEquals(4, summary.total)
    }

    @Test
    fun cancelledAndClosedPipeAreShutdownSafeAndNotQuarantinable() {
        assertTrue(ResolverFailureKind.CANCELLED.isShutdownSafe)
        assertTrue(ResolverFailureKind.CLOSED_PIPE.isShutdownSafe)
        assertFalse(ResolverFailureKind.CANCELLED.quarantinable)
        assertFalse(ResolverFailureKind.CLOSED_PIPE.quarantinable)
    }

    @Test
    fun tlsAndCertExpiredAreClassified() {
        assertEquals(
            ResolverFailureKind.CERT_EXPIRED,
            ResolverFailureClassifier.classify("tls: failed to verify certificate: x509: certificate has expired for doh")
        )
        assertEquals(
            ResolverFailureKind.TLS,
            ResolverFailureClassifier.classify("transport/internet: remote error: tls: handshake failure")
        )
    }

    @Test
    fun nonResolverLinesAreIgnored() {
        val lines = listOf(
            "some unrelated tunnel log line",
            "proxy/socks: connection opened",
            ""
        )
        val summary = ResolverFailureClassifier.summarize(lines)
        assertEquals(0, summary.total)
    }

    @Test
    fun summaryAlwaysRecomputesIdenticallyFromRaw() {
        val raw = listOf(
            "context deadline exceeded dns-query",
            "unexpected EOF dns",
            "context canceled",
            "read/write on closed pipe",
            "clean tunnel line"
        )
        val summary = ResolverFailureClassifier.summarize(raw)
        assertTrue(ResolverFailureClassifier.rawConsistentWithSummary(raw, summary))
    }

    @Test
    fun classifyThrowableHandlesEmptyAnswer() {
        // An unmatched throwable is not classified as a cancellation (it is neither shutdown-safe
        // nor a specific failure); the resolver path maps it to OTHER, never to CANCELLED.
        assertNull(ResolverFailureClassifier.classifyThrowable(IllegalStateException("empty-answer")))
        assertNull(ResolverFailureClassifier.classify(""))
    }
}
