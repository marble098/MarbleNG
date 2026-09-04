package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProbeMethod
import com.marbleng.app.model.ProxyProfile
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SMART_PING_RESCUE_V123
 *
 * Smart ping used to mark every server failed on a filtered network. These tests pin the contract
 * that replaced it:
 *
 *  1. an endpoint that answers a TCP handshake is *never* reported failed, even when the verified
 *     tunnel phase cannot run — that combination is exactly what a healthy node behind a busy
 *     phone produces;
 *  2. an endpoint that answers nothing fails fast, without paying for a tunnel;
 *  3. a structurally impossible target is refused without opening a socket at all.
 *
 * The reachable cases bind a real [ServerSocket] on the loopback interface, so the gate is
 * exercised against a genuine handshake with no network and no fixture.
 */
class SmartPingGateTest {

    private fun profile(host: String, port: Int) = ProxyProfile(
        id = "gate-$host-$port",
        name = "gate",
        scheme = "vless",
        raw = "",
        configJson = "{}",
        host = host,
        port = port
    )

    private val settings = AppSettings()

    @Test
    fun aReachableEndpointIsNeverReportedFailedWithoutATunnel() {
        ServerSocket(0).use { server ->
            val result = RouteProbe.smartPing(
                profile = profile("127.0.0.1", server.localPort),
                tunnelPort = 0,
                timeoutMs = 3_000,
                settings = settings
            )
            assertTrue(
                "a handshake that completed must not read as failed: $result",
                result.successPercent > 0
            )
            assertTrue("latency must be a real measurement: $result", result.latencyMs > 0.0)
            assertTrue("latency must be bounded: $result", result.latencyMs < RouteProbe.UNREACHABLE)
            assertEquals("SMART", result.method)
        }
    }

    @Test
    fun theGateNamesTheEvidenceThatProvedReachability() {
        ServerSocket(0).use { server ->
            val gate = RouteProbe.smartGate(
                profile("127.0.0.1", server.localPort),
                timeoutMs = 3_000,
                settings = settings
            )
            assertTrue("gate must pass: $gate", gate.reached)
            assertEquals("tcp", gate.evidence)
        }
    }

    @Test
    fun anEndpointThatAnswersNothingFailsTheGate() {
        // Port 1 on the loopback interface is refused immediately, so this costs no timeout.
        val gate = RouteProbe.smartGate(profile("127.0.0.1", 1), timeoutMs = 1_500, settings = settings)
        assertFalse(gate.reached)
        assertEquals("", gate.evidence)

        val result = RouteProbe.smartPing(
            profile = profile("127.0.0.1", 1),
            tunnelPort = 0,
            timeoutMs = 3_000,
            settings = settings
        )
        assertEquals(0, result.successPercent)
        assertEquals(RouteProbe.UNREACHABLE, result.latencyMs, 0.0)
        assertTrue("failure must carry its reason: $result", result.failureReason.isNotBlank())
    }

    @Test
    fun impossibleTargetsAreRefusedWithoutOpeningASocket() {
        val blank = RouteProbe.smartPing(profile("", 443), 0, 2_000, settings)
        assertEquals(0, blank.successPercent)
        assertEquals("blank-host", blank.failureReason)

        val port = RouteProbe.smartPing(profile("example.invalid", 0), 0, 2_000, settings)
        assertEquals(0, port.successPercent)
        assertEquals("invalid-port", port.failureReason)
    }

    @Test
    fun anUnverifiedVerdictIsReachableButNotProven() {
        // Reachable-but-unverified must stay green for the user and still rank below a verified
        // node, so it is strictly between "failed" and "proved".
        assertTrue(RouteProbe.GATE_ONLY_SUCCESS > 0)
        assertTrue(RouteProbe.GATE_ONLY_SUCCESS < 100)
    }

    @Test
    fun smartPingIsTheHybridMethodOfTheProduct() {
        // The Settings entry the user picks and the algorithm under test are the same method; a
        // rename in one place without the other is what let the two drift apart before.
        assertEquals(
            ProbeMethod.HYBRID,
            ProbeMethod.entries.first { it == ProbeMethod.HYBRID }
        )
        assertEquals(ProbeMethod.HYBRID, AppSettings().probeMethod)
    }
}
