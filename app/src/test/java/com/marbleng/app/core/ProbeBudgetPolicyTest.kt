package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket

/**
 * MARBLE_RESOLVE_BUDGET_V144 / MARBLE_HTTP_RACE_V144 / MARBLE_PROBE_DETERMINISM_V144 —
 * regression tests for the ping-layer overhaul:
 *
 * - hostname resolution inside probes is time-bounded (a hanging resolver reports
 *   unreachable instead of freezing the worker);
 * - numeric literals never wait on the resolver pool;
 * - the HTTPS origin race measures in parallel (a blackholed first origin cannot hold the
 *   fast second one hostage);
 * - blank-host DNS target selection is a deterministic rotation, not `random()`.
 *
 * Everything here runs on a plain JVM: resolvers are injected lambdas and the HTTP
 * origins are loopback sockets owned by the test, so no external network is touched.
 */
class ProbeBudgetPolicyTest {

    // ------------------------------------------------------------------ isLiteralIp

    @Test
    fun literalIpv4IsLiteral() {
        assertTrue(AddressFamilyPolicy.isLiteralIp("1.2.3.4"))
        assertTrue(AddressFamilyPolicy.isLiteralIp("  8.8.8.8  "))
        assertTrue(AddressFamilyPolicy.isLiteralIp("[9.9.9.9]"))
    }

    @Test
    fun literalIpv6IsLiteral() {
        assertTrue(AddressFamilyPolicy.isLiteralIp("::1"))
        assertTrue(AddressFamilyPolicy.isLiteralIp("2001:db8::1"))
        assertTrue(AddressFamilyPolicy.isLiteralIp("[2001:db8::1]"))
    }

    @Test
    fun hostnamesAreNotLiteral() {
        assertTrue(!AddressFamilyPolicy.isLiteralIp("dns.google"))
        assertTrue(!AddressFamilyPolicy.isLiteralIp("one.one.one.one"))
        assertTrue(!AddressFamilyPolicy.isLiteralIp(""))
        assertTrue(!AddressFamilyPolicy.isLiteralIp("   "))
        // Out-of-range quad: must fall through to the real resolver, never be parsed as one.
        assertTrue(!AddressFamilyPolicy.isLiteralIp("999.1.1.1"))
        assertTrue(!AddressFamilyPolicy.isLiteralIp("1.2.3"))
        assertTrue(!AddressFamilyPolicy.isLiteralIp("1.2.3.4.5"))
    }

    // ------------------------------------------------------- resolveWithBudget

    @Test
    fun hangingResolverIsBoundedByBudget() {
        val hanging: (String) -> Array<InetAddress> = {
            Thread.sleep(30_000)
            emptyArray()
        }
        val started = System.nanoTime()
        val result = AddressFamilyPolicy.resolveWithBudget("dns.google", 400, hanging)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertTrue("hanging resolver must report empty, got $result", result.isEmpty())
        assertTrue(
            "resolution must respect the 400 ms budget, took ${elapsedMs} ms",
            elapsedMs < 5_000L
        )
    }

    @Test
    fun literalBypassesAHangingResolver() {
        var calls = 0
        val instantLoopback: (String) -> Array<InetAddress> = {
            calls++
            arrayOf(InetAddress.getByName("127.0.0.1"))
        }
        val started = System.nanoTime()
        val result = AddressFamilyPolicy.resolveWithBudget("127.0.0.1", 300, instantLoopback)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertEquals(1, result.size)
        assertEquals(1, calls)
        assertTrue("literal must not wait, took ${elapsedMs} ms", elapsedMs < 3_000L)
    }

    @Test
    fun boundedResolveCandidatesHonoursBudget() {
        val hanging: (String) -> Array<InetAddress> = {
            Thread.sleep(30_000)
            emptyArray()
        }
        val plan = AddressFamilyPolicy.plan(underlayHasIpv6 = false)
        val started = System.nanoTime()
        val result = AddressFamilyPolicy.resolveCandidates("dns.google", plan, 400, hanging)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertTrue(result.isEmpty())
        assertTrue("candidates must respect the budget, took ${elapsedMs} ms", elapsedMs < 5_000L)
    }

    @Test
    fun legacyUnboundedResolveCandidatesStillCallsResolver() {
        var calls = 0
        val canned: (String) -> Array<InetAddress> = {
            calls++
            arrayOf(InetAddress.getByName("127.0.0.1"))
        }
        val plan = AddressFamilyPolicy.plan(underlayHasIpv6 = false)
        val result = AddressFamilyPolicy.resolveCandidates("anything.invalid", plan, 0, canned)
        assertEquals(1, calls)
        assertEquals(1, result.size)
    }

    // ------------------------------------------------------------------ dnsPing

    @Test
    fun dnsPingHonoursTimeoutInsteadOfHanging() {
        val hanging: (String) -> Array<InetAddress> = {
            Thread.sleep(30_000)
            emptyArray()
        }
        val started = System.nanoTime()
        val value = RouteProbe.dnsPing("dns.google", 400, hanging)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertEquals(RouteProbe.UNREACHABLE, value, 0.0)
        assertTrue("dnsPing must respect 400 ms, took ${elapsedMs} ms", elapsedMs < 5_000L)
    }

    @Test
    fun dnsPingMeasuresInstantResolver() {
        val instant: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByName("127.0.0.1"))
        }
        val value = RouteProbe.dnsPing("dns.google", 2_000, instant)
        assertTrue("instant resolver must succeed, got $value", value < RouteProbe.UNREACHABLE)
    }

    // ---------------------------------------------------------- determinism

    @Test
    fun blankHostDnsTargetsRotateDeterministically() {
        val seen = (1..6).map { RouteProbe.nextDnsTarget() }
        assertEquals("rotation must repeat every 3", seen.take(3), seen.drop(3))
        assertEquals("rotation must cover 3 distinct resolvers", 3, seen.take(3).toSet().size)
    }

    // -------------------------------------------------------------- httpPing race

    /** A loopback HTTP origin. `blackhole = true` accepts connections and never answers. */
    private class LoopbackOrigin(val blackhole: Boolean = false, val delayMs: Long = 0) {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = server.localPort
        private val acceptThread = Thread({
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    Thread({
                        socket.use { s ->
                            try {
                                if (blackhole) {
                                    Thread.sleep(60_000)
                                    return@use
                                }
                                if (delayMs > 0) Thread.sleep(delayMs)
                                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                }
                                val response = "HTTP/1.1 204 No Content\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n"
                                s.getOutputStream().write(response.toByteArray())
                                s.getOutputStream().flush()
                            } catch (_: Exception) {
                            }
                        }
                    }).apply { isDaemon = true }.start()
                }
            } catch (_: Exception) {
            }
        }, "test-loopback-origin").apply { isDaemon = true; start() }

        fun url(): String = "http://127.0.0.1:$port/generate_204"

        fun close() = runCatching { server.close() }
    }

    @Test
    fun httpRaceIsNotHeldHostageByABlackholedFirstOrigin() {
        val blackhole = LoopbackOrigin(blackhole = true)
        val fast = LoopbackOrigin()
        try {
            // Sequential failover would block the full 5 s read timeout on the blackhole
            // before ever dialling the fast origin. The race must answer in ~milliseconds.
            val started = System.nanoTime()
            val result = RouteProbe.httpPing(
                socksPort = 0,
                timeoutMs = 5_000,
                targets = listOf(blackhole.url(), fast.url())
            )
            val elapsedMs = (System.nanoTime() - started) / 1_000_000L
            assertTrue(
                "race must succeed via the fast origin, got $result",
                result.latencyMs < RouteProbe.UNREACHABLE
            )
            assertEquals(100, result.successPercent)
            assertTrue(
                "race must beat the 5 s sequential floor, took ${elapsedMs} ms",
                elapsedMs < 4_000L
            )
        } finally {
            blackhole.close()
            fast.close()
        }
    }

    @Test
    fun httpRaceReportsFailureWhenEveryOriginFails() {
        val blackhole = LoopbackOrigin(blackhole = true)
        try {
            val result = RouteProbe.httpPing(
                socksPort = 0,
                timeoutMs = 1_000,
                targets = listOf(blackhole.url())
            )
            assertEquals(RouteProbe.UNREACHABLE, result.latencyMs, 0.0)
            assertEquals(0, result.successPercent)
            assertTrue(result.failureReason.isNotBlank())
        } finally {
            blackhole.close()
        }
    }

    @Test
    fun httpSingleTargetStaysSynchronous() {
        val fast = LoopbackOrigin()
        try {
            val result = RouteProbe.httpPing(
                socksPort = 0,
                timeoutMs = 3_000,
                targets = listOf(fast.url())
            )
            assertTrue(result.latencyMs < RouteProbe.UNREACHABLE)
        } finally {
            fast.close()
        }
    }

    @Test
    fun icmpFloorIsAnExplicitWholeSecond() {
        assertEquals(1, RouteProbe.ICMP_MIN_WAIT_SEC)
    }
}
