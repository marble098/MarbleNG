package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverFailureClassifierTest {
    @Test
    fun dohUnexpectedEofLineIsCountedAsEof() {
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
        assertNull(ResolverFailureClassifier.classifyThrowable(IllegalStateException("empty-answer")))
        assertNull(ResolverFailureClassifier.classify(""))
    }
}
