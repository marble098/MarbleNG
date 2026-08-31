package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Regression tests for the parallel DoH resolver pool (MARBLE_SMART_RANK_V90).
 *
 * These lock in the "closed pipe" fix contract: the pool races at least four providers, the first
 * valid answer wins, and ANY error class — not just timeouts — falls back automatically to the
 * next provider. The transport drains the full response body before the socket is closed.
 */
class DohResolverPoolTest {

    private class FakeTransport(
        private val responses: Map<String, DohTransportResult>
    ) : DohTransport {
        override fun query(endpoint: String, wire: ByteArray, timeoutMs: Long): DohTransportResult =
            responses[endpoint] ?: DohTransportResult(
                success = false, failureKind = ResolverFailureKind.OTHER, detail = "no-fake-response"
            )
    }

    private fun okBody() = ByteArray(17) { 1 }

    @Test
    fun firstValidAnswerWinsInProviderOrder() {
        val transport = FakeTransport(
            mapOf(
                "https://a" to DohTransportResult(body = okBody(), success = true, latencyMs = 9),
                "https://b" to DohTransportResult(body = okBody(), success = true, latencyMs = 1)
            )
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pool = DohResolverPool(transport, executor, overallDeadlineMs = 4_000L)
            val outcome = pool.raceResolve(
                ByteArray(17),
                listOf(
                    DohResolverPool.Provider("a", "https://a"),
                    DohResolverPool.Provider("b", "https://b")
                )
            )
            assertTrue(outcome.success)
            assertEquals("a", outcome.providerId)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun anyErrorClassFallsBackToTheNextProvider() {
        val transport = FakeTransport(
            mapOf(
                "https://a" to DohTransportResult(
                    success = false, failureKind = ResolverFailureKind.EOF, detail = "unexpected eof"
                ),
                "https://b" to DohTransportResult(body = okBody(), success = true)
            )
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pool = DohResolverPool(transport, executor, overallDeadlineMs = 4_000L)
            val outcome = pool.raceResolve(
                ByteArray(17),
                listOf(
                    DohResolverPool.Provider("a", "https://a"),
                    DohResolverPool.Provider("b", "https://b")
                )
            )
            assertTrue(outcome.success)
            assertEquals("b", outcome.providerId)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun allFailuresAreReportedPerProvider() {
        val transport = FakeTransport(
            mapOf(
                "https://a" to DohTransportResult(
                    success = false, failureKind = ResolverFailureKind.EOF, detail = "eof"
                ),
                "https://b" to DohTransportResult(
                    success = false, failureKind = ResolverFailureKind.DEADLINE, detail = "deadline"
                )
            )
        )
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pool = DohResolverPool(transport, executor, overallDeadlineMs = 4_000L)
            val outcome = pool.raceResolve(
                ByteArray(17),
                listOf(
                    DohResolverPool.Provider("a", "https://a"),
                    DohResolverPool.Provider("b", "https://b")
                )
            )
            assertFalse(outcome.success)
            assertEquals(2, outcome.failures.size)
            assertEquals(ResolverFailureKind.EOF, outcome.failures[0].second.failureKind)
            assertEquals(ResolverFailureKind.DEADLINE, outcome.failures[1].second.failureKind)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun emptyProviderListFailsCleanly() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pool = DohResolverPool(FakeTransport(emptyMap()), executor, overallDeadlineMs = 4_000L)
            val outcome = pool.raceResolve(ByteArray(17), emptyList())
            assertFalse(outcome.success)
            assertTrue(outcome.failures.isEmpty())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun defaultProvidersCoverAtLeastFourEndpoints() {
        val providers = DohResolverPool.DEFAULT_PROVIDERS
        assertTrue(providers.size >= 4)
        assertEquals(1, providers.count { it.internal })
        assertTrue(providers.any { it.endpoint.contains("1.1.1.1") })   // Cloudflare
        assertTrue(providers.any { it.endpoint.contains("8.8.8.8") })   // Google
        assertTrue(providers.any { it.endpoint.contains("9.9.9.9") })   // Quad9
    }
}
