package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_METHODS_V148 — the product exposes exactly the methods a user can reason about.
 *
 * Smart, Real test, raw TCP Connect, the recommended TCP+TLS gate, HTTP GET / HTTP HEAD and ICMP
 * are all user-selectable. DNS is deliberately absent: it only measured the local resolver and
 * never the server or the proxy path.
 */
class ProbeMethodV148Test {

    @Test
    fun productMethodsAreTheFullMarbleList() {
        assertEquals(
            listOf(
                ProbeMethod.HYBRID,
                ProbeMethod.TUNNEL,
                ProbeMethod.TCP_CONNECT,
                ProbeMethod.TCP_RECOMMENDED,
                ProbeMethod.HTTP_GET,
                ProbeMethod.HTTP_HEAD,
                ProbeMethod.ICMP
            ),
            ProbeMethod.entries.toList()
        )
    }

    @Test
    fun dnsRemainsRemovedAndNewAddressMethodsExist() {
        val names = ProbeMethod.entries.map { it.name }
        assertFalse("\"DNS\" must not be a product method", "DNS" in names)
        assertTrue("\"TCP_CONNECT\" must be a product method", "TCP_CONNECT" in names)
        assertTrue("\"TCP_RECOMMENDED\" must be a product method", "TCP_RECOMMENDED" in names)
        assertTrue("\"HTTP_GET\" must be a product method", "HTTP_GET" in names)
        assertTrue("\"HTTP_HEAD\" must be a product method", "HTTP_HEAD" in names)
        assertTrue("\"ICMP\" must be a product method", "ICMP" in names)
    }

    @Test
    fun smartIsStillTheDefault() {
        assertTrue(ProbeMethod.entries.contains(AppSettings().probeMethod))
        assertEquals(ProbeMethod.HYBRID, AppSettings().probeMethod)
    }
}
