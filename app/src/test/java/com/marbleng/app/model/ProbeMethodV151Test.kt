package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

        assertEquals("http://example.com/g", DelayTest.url("http://example.com/g"))
        assertEquals(DelayTest.URL, DelayTest.url("https://"))

        assertEquals(
            listOf(
                DelayTest.URL,
                DelayTest.URL_SECONDARY,
                DelayTest.URL_TERTIARY
            ),
            DelayTest.candidates(DelayTest.URL)
        )
        assertEquals(DelayTest.URL, AppSettings().delayTestUrl)
    }
}
