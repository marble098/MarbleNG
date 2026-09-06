package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PROBE_METHODS_V151 — the product exposes exactly three measurements.
 *
 * The seven-method V148 list is gone. Five of its entries answered one question with a different
 * socket call, so the same server reported five numbers depending on which button was pressed, and
 * ICMP answered it with the carrier instead of the server. What is left is the three that mean
 * different things: Real delay (a real page through the tunnel), TCP ping (the port answers) and
 * URL test (what the running sing-box extended core measured for its own outbound).
 */
class ProbeMethodV151Test {

    @Test
    fun productMethodsAreExactlyThree() {
        assertEquals(
            listOf(
                ProbeMethod.REAL_DELAY,
                ProbeMethod.TCP_PING,
                ProbeMethod.URL_TEST
            ),
            ProbeMethod.entries.toList()
        )
    }

    @Test
    fun retiredMethodsAreGone() {
        val names = ProbeMethod.entries.map { it.name }
        listOf(
            "HYBRID",
            "TUNNEL",
            "TCP_CONNECT",
            "TCP_RECOMMENDED",
            "HTTP_GET",
            "HTTP_HEAD",
            "ICMP",
            "DNS"
        ).forEach { retired ->
            assertFalse("\"$retired\" must not be a product method", retired in names)
        }
    }

    @Test
    fun realDelayIsTheDefault() {
        assertEquals(ProbeMethod.REAL_DELAY, AppSettings().probeMethod)
        assertTrue(ProbeMethod.entries.contains(AppSettings().probeMethod))
    }

    @Test
    fun onlyTcpPingIsEndpointLevel() {
        // TCP ping measures `host:port` and nothing else, so one verdict may stand for every
        // duplicate of the same endpoint. The other two go through the tunnel and are per-route.
        assertTrue(ProbeMethod.TCP_PING.isEndpointLevel())
        assertFalse(ProbeMethod.REAL_DELAY.isEndpointLevel())
        assertFalse(ProbeMethod.URL_TEST.isEndpointLevel())
    }

    @Test
    fun delayTestUrlFallsBackToTheShippedDefault() {
        assertEquals(DelayTest.URL, DelayTest.url(""))
        assertEquals(DelayTest.URL, DelayTest.url("   "))
        assertEquals(DelayTest.URL, DelayTest.url("ftp://example.com/x"))
        assertEquals("https://example.com/g", DelayTest.url("  https://example.com/g  "))
        // Marble's own real-delay measurement accepts plain http; sing-box's endpoint does not.
        assertEquals("http://example.com/g", DelayTest.url("http://example.com/g"))
        assertEquals(DelayTest.URL, AppSettings().delayTestUrl)
    }
}
