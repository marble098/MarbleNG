package com.marbleng.app.jitter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SMART_JITTER_REALTIME_V262 — the fixes for the 2026-11-03 log finding "jitter probe
 * reports loss on every tick while the route probe reports 100% success":
 *
 *  - [SmartJitterDefaults.tlsSniFor] gives a literal anycast probe target the real certificate
 *    hostname for the TLS handshake (the SOCKS dial still uses the literal itself), the same
 *    mapping the route probe's `probeTlsHost` applies — a literal sent as SNI is what produced
 *    "zero verified RTTs" on the literal HTTPS target;
 *  - the per-tick burst defaults keep the RFC 3550 estimate fed with several consecutive
 *    in-path deltas per probe interval;
 *  - the [SmartJitterController.onState] hook fires on every published state so the repository
 *    can mirror the reducer's jitter into the home page in real time.
 */
class JitterRealtimeV262Test {

    // ---------------------------------------------------------------- TLS SNI mapping

    @Test
    fun `literal anycast ips get their real certificate hostname`() {
        assertEquals("cloudflare-dns.com", SmartJitterDefaults.tlsSniFor("1.1.1.1"))
        assertEquals("cloudflare-dns.com", SmartJitterDefaults.tlsSniFor("1.0.0.1"))
        assertEquals("dns.google", SmartJitterDefaults.tlsSniFor("8.8.8.8"))
        assertEquals("dns.quad9.net", SmartJitterDefaults.tlsSniFor("9.9.9.9"))
    }

    @Test
    fun `domain targets pass through as their own SNI`() {
        assertEquals("www.gstatic.com", SmartJitterDefaults.tlsSniFor("www.gstatic.com"))
        assertEquals(
            "cloudflare-dns.com",
            SmartJitterDefaults.tlsSniFor(" https://cloudflare-dns.com/cdn-cgi/trace ")
        )
        assertEquals("dns.google", SmartJitterDefaults.tlsSniFor("dns.google:443"))
    }

    @Test
    fun `an ipv6 literal survives the port stripping`() {
        assertEquals(
            "2606:4700:4700::1111",
            SmartJitterDefaults.tlsSniFor("[2606:4700:4700::1111]")
        )
    }

    // ---------------------------------------------------------------- burst defaults

    @Test
    fun `one tick takes a small warm burst by default`() {
        val tuning = SmartJitterController.Tuning()
        assertEquals(SmartJitterDefaults.BURST_SAMPLES, tuning.burstSamples)
        assertEquals(SmartJitterDefaults.BURST_GAP_MS, tuning.burstGapMs)
        assertTrue(
            "the burst must stay small: one radio wake per tick",
            tuning.burstSamples in 1..SmartJitterController.MAX_BURST_SAMPLES
        )
        assertTrue(
            "the gap must stay tens of milliseconds so samples are consecutive, not spaced out",
            tuning.burstGapMs in 1L..1_000L
        )
    }

    // ---------------------------------------------------------------- state listener

    @Test
    fun `the onState listener sees every published state`() {
        val seen = mutableListOf<SmartJitterController.State>()
        val controller = SmartJitterController()
        controller.onState = { seen.add(it) }
        controller.close()
        assertFalse("close() must publish at least once", seen.isEmpty())
        val last = seen.last()
        assertFalse(last.running)
        assertEquals(JitterMode.OFF, last.mode)
    }

    @Test
    fun `a throwing listener never breaks the controller`() {
        val controller = SmartJitterController()
        controller.onState = { error("host bug") }
        controller.close() // must not throw
        assertFalse(controller.state.value.running)
    }
}
