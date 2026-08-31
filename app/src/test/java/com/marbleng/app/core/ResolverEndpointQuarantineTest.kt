package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for resolver endpoint quarantine (MARBLE_RESOLVER_QUARANTINE_V1).
 *
 * These lock in the fix for resolver stability in 4.0.0: expired certificates, repeated TLS
 * handshake failures, repeated EOF bursts and repeated deadline storms must quarantine an endpoint
 * instead of letting it stay in rotation and accumulate large retained error counts. Cancellation /
 * teardown events must never quarantine anything, and a healthy winner is sticky for the session.
 */
class ResolverEndpointQuarantineTest {

    @Test
    fun expiredCertificateQuarantinesImmediately() {
        val q = ResolverEndpointQuarantine { 0L }
        q.record(ResolverFailureKind.CERT_EXPIRED, "https://dns.shecan.ir/dns-query")
        assertTrue(q.isQuarantined("https://dns.shecan.ir/dns-query"))
    }

    @Test
    fun repeatedHandshakeFailuresQuarantineAfterThreshold() {
        val q = ResolverEndpointQuarantine { 0L }
        q.record(ResolverFailureKind.TLS, "ep")
        assertFalse(q.isQuarantined("ep")) // one is not enough
        q.record(ResolverFailureKind.TLS, "ep")
        assertTrue(q.isQuarantined("ep"))
    }

    @Test
    fun repeatedEofBurstsQuarantineAfterThreshold() {
        val q = ResolverEndpointQuarantine { 0L }
        repeat(2) { q.record(ResolverFailureKind.EOF, "ep") }
        assertFalse(q.isQuarantined("ep"))
        q.record(ResolverFailureKind.EOF, "ep")
        assertTrue(q.isQuarantined("ep"))
    }

    @Test
    fun deadlineStormQuarantinesAfterThreshold() {
        val q = ResolverEndpointQuarantine { 0L }
        repeat(3) { q.record(ResolverFailureKind.DEADLINE, "ep") }
        assertFalse(q.isQuarantined("ep"))
        q.record(ResolverFailureKind.DEADLINE, "ep")
        assertTrue(q.isQuarantined("ep"))
    }

    @Test
    fun singleTimeoutDoesNotQuarantine() {
        val q = ResolverEndpointQuarantine { 0L }
        q.record(ResolverFailureKind.DEADLINE, "ep")
        assertFalse(q.isQuarantined("ep"))
    }

    @Test
    fun cancellationAndClosedPipeNeverQuarantine() {
        val q = ResolverEndpointQuarantine { 0L }
        repeat(20) {
            q.record(ResolverFailureKind.CANCELLED, "ep")
            q.record(ResolverFailureKind.CLOSED_PIPE, "ep")
        }
        assertFalse(q.isQuarantined("ep"))
        assertEquals(0, q.snapshot().single().decisiveFailures)
    }

    @Test
    fun quarantineExpiresAfterBackoffWindow() {
        var now = 0L
        val q = ResolverEndpointQuarantine { now }
        q.record(ResolverFailureKind.CERT_EXPIRED, "ep")
        assertTrue(q.isQuarantined("ep"))
        now = 15_001L
        assertFalse(q.isQuarantined("ep"))
    }

    @Test
    fun healthyWinnerIsStickyAndPreferred() {
        var now = 0L
        val q = ResolverEndpointQuarantine { now }
        q.recordSuccess("https://1.1.1.1/dns-query")
        assertTrue(q.isSticky("https://1.1.1.1/dns-query"))

        val order = q.preferredOrder(
            listOf("https://8.8.8.8/dns-query", "https://1.1.1.1/dns-query")
        )
        assertEquals("https://1.1.1.1/dns-query", order.first())

        // A quarantined endpoint is never sticky even if it previously won.
        q.record(ResolverFailureKind.CERT_EXPIRED, "https://1.1.1.1/dns-query")
        assertFalse(q.isSticky("https://1.1.1.1/dns-query"))
    }
}
