package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_INTELLIGENCE_V141 — protocol fitness regressions, derived from the attached
 * Turkey-14 (v7.0.9, VLESS+xhttp, six forced restarts, DNS deadline storm, PSS 104 MB) vs
 * Netherlands-3 (v7.0.4, hysteria2, IPv4-only, hours-stable, PSS 98 MB) log pair.
 */
class ProtocolFitnessTest {

    private val calm = LinkEvidence(rttMs = 120.0, tailRttMs = 150.0, jitterMs = 6.0, lossPercent = 0.0, samples = 9)
    private val noisy = LinkEvidence(rttMs = 380.0, tailRttMs = 640.0, jitterMs = 34.0, lossPercent = 11.0, samples = 7)
    private val unknown = LinkEvidence.UNKNOWN

    private fun profile(
        scheme: String,
        transport: String = "",
        security: String = "tls"
    ) = ProxyProfile(
        id = "$scheme-$transport",
        name = "$scheme-$transport",
        scheme = scheme,
        raw = "",
        configJson = "{}",
        host = "node.example.net",
        port = 443,
        transport = transport,
        security = security
    )

    @Test
    fun `noise is measured not guessed`() {
        assertTrue(ProtocolFitness.noisy(noisy))
        assertFalse(ProtocolFitness.noisy(calm))
        // Unmeasured links are never treated as noisy: no evidence, no verdict.
        assertFalse(ProtocolFitness.noisy(unknown))
    }

    @Test
    fun `quic native transports win on noisy links`() {
        val hy2 = profile("hysteria2", "hysteria")
        // The green reference ran hysteria2 on a noisy network and stayed up for hours.
        assertTrue(ProtocolFitness.bias(hy2, noisy) > 0.0)
        assertTrue(ProtocolFitness.bias(hy2, noisy) >= ProtocolFitness.bias(hy2, calm))
    }

    @Test
    fun `vless xhttp is penalised on noisy links and only mildly on calm ones`() {
        val xhttp = profile("vless", "xhttp")
        assertTrue(ProtocolFitness.bias(xhttp, noisy) < 0.0)
        // On a calm link the history must stay in charge: the penalty is a nudge, not a veto.
        assertTrue(ProtocolFitness.bias(xhttp, calm) > -6.0)
        assertTrue(ProtocolFitness.bias(xhttp, calm) > ProtocolFitness.bias(xhttp, noisy))
    }

    @Test
    fun `unsecured stream proxies are penalised regardless of link quality`() {
        val plain = profile("vless", "raw", security = "none")
        assertTrue(ProtocolFitness.bias(plain, calm) < 0.0)
        assertTrue(ProtocolFitness.bias(plain, noisy) < 0.0)
    }

    @Test
    fun `secured tcp transports stay neutral to positive`() {
        val vision = profile("vless", "raw", security = "reality")
        val trojan = profile("trojan", "raw")
        assertTrue(ProtocolFitness.bias(vision, noisy) >= 0.0)
        assertTrue(ProtocolFitness.bias(trojan, noisy) >= -1.0)
    }

    @Test
    fun `hysteria2 outranks vless xhttp exactly when the link is noisy`() {
        val hy2 = profile("hysteria2", "hysteria")
        val xhttp = profile("vless", "xhttp")
        assertTrue(ProtocolFitness.bias(hy2, noisy) > ProtocolFitness.bias(xhttp, noisy))
        // On a calm link the gap must collapse toward neutral so measured history decides.
        val calmGap = ProtocolFitness.bias(hy2, calm) - ProtocolFitness.bias(xhttp, calm)
        val noisyGap = ProtocolFitness.bias(hy2, noisy) - ProtocolFitness.bias(xhttp, noisy)
        assertTrue(calmGap < noisyGap)
    }

    @Test
    fun `mux is disarmed for record chunked transports under noise only`() {
        val xhttp = profile("vless", "xhttp")
        assertTrue(ProtocolFitness.prefersMuxOff(xhttp, noisy))
        assertFalse(ProtocolFitness.prefersMuxOff(xhttp, calm))
        assertFalse(ProtocolFitness.prefersMuxOff(xhttp, unknown))

        val hy2 = profile("hysteria2", "hysteria")
        assertFalse(ProtocolFitness.prefersMuxOff(hy2, noisy))
    }
}
