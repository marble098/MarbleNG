package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_TRUTH_V147 — product ping methods are exactly the two answers that can be about a
 * *proxy server*.
 *
 * ICMP, HTTP and DNS measured the local network/resolver and never touched the proxy. TCP was
 * Smart's internal verified Layer-0 gate; exposing it as a peer method let an endpoint handshake
 * masquerade as a proxy verdict. The primitives still exist in RouteProbe for Smart to use, but
 * they must never re-enter the product method set.
 */
class ProbeMethodV147Test {

    @Test
    fun productMethodsAreExactlySmartAndRealTunnel() {
        assertEquals(listOf(ProbeMethod.HYBRID, ProbeMethod.TUNNEL), ProbeMethod.entries.toList())
    }

    @Test
    fun removedAddressLevelMethodsDoNotExist() {
        val names = ProbeMethod.entries.map { it.name }
        assertFalse("\"TCP\" must not be a product method", "TCP" in names)
        assertFalse("\"ICMP\" must not be a product method", "ICMP" in names)
        assertFalse("\"HTTP\" must not be a product method", "HTTP" in names)
        assertFalse("\"DNS\" must not be a product method", "DNS" in names)
    }

    @Test
    fun smartIsStillTheDefaultAndIsEndpointOnlyByDesign() {
        assertTrue(ProbeMethod.entries.contains(AppSettings().probeMethod))
        assertEquals(ProbeMethod.HYBRID, AppSettings().probeMethod)
    }
}
