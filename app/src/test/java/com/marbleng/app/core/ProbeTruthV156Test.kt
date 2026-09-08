package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.DelayTest
import com.marbleng.app.model.ProbeMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_URLTEST_SINGBOX_ONLY_V156 / MARBLE_PING_CANCEL_V156 / MARBLE_REAL_DELAY_TRUTH_V156.
 *
 * Three promises about the measurement plane, each one a way the product used to lie:
 *
 *  1. **URL test belongs to one engine.** It is sing-box extended's own delay controller. On Xray
 *     it was answered by a Kotlin HTTPS HEAD pushed through Xray's SOCKS inbound — no controller,
 *     no unified-delay accounting, a second independently-timed HTTP stack — so the same server
 *     reported two incomparable numbers depending on which core was selected.
 *  2. **A bulk measurement can be cancelled**, and cancelling keeps what was already measured.
 *  3. **Real delay does not need a tunnel to already exist.** Without one it builds a throwaway
 *     core; only with no core at all may it say `no-live-tunnel`.
 */
class ProbeTruthV156Test {

    private val xray = AppSettings()
    private val singBox = AppSettings(coreEngineId = CoreEngine.SINGBOX.id)

    // ───────────────────────────────────────────────────────── URL test engine gate

    @Test
    fun urlTestIsOfferedOnlyOnTheEngineThatOwnsIt() {
        assertTrue(ProbeMethod.URL_TEST.availableOn(CoreEngine.SINGBOX))
        assertFalse(ProbeMethod.URL_TEST.availableOn(CoreEngine.XRAY))
        // The other two are engine-agnostic by design: both cores can carry a real round trip,
        // and a TCP handshake never leaves the device.
        ProbeMethod.entries.filter { it != ProbeMethod.URL_TEST }.forEach { method ->
            assertTrue("$method must stay available on both cores", method.availableOn(CoreEngine.XRAY))
            assertTrue("$method must stay available on both cores", method.availableOn(CoreEngine.SINGBOX))
        }
    }

    @Test
    fun anUnavailableMethodExplainsItselfInsteadOfSilentlyFailing() {
        assertTrue(ProbeMethod.URL_TEST.unavailableReason(CoreEngine.XRAY).isNotBlank())
        assertEquals("", ProbeMethod.URL_TEST.unavailableReason(CoreEngine.SINGBOX))
        CoreEngine.entries.forEach { engine ->
            ProbeMethod.entries.forEach { method ->
                assertEquals(
                    "the reason and the gate must never disagree on ${method.name}/${engine.id}",
                    method.availableOn(engine),
                    method.unavailableReason(engine).isEmpty()
                )
            }
        }
    }

    @Test
    fun switchingAwayFromSingBoxDemotesTheStoredChoiceToARealMeasurement() {
        assertEquals(ProbeMethod.REAL_DELAY, ProbeMethod.URL_TEST.forEngine(CoreEngine.XRAY))
        assertEquals(ProbeMethod.URL_TEST, ProbeMethod.URL_TEST.forEngine(CoreEngine.SINGBOX))
        ProbeMethod.entries.forEach { method ->
            assertTrue(
                "the demotion must land on a method the target engine can actually run",
                method.forEngine(CoreEngine.XRAY).availableOn(CoreEngine.XRAY)
            )
        }
        // A settings object the repository would persist after the engine switch.
        val demoted = xray.copy(probeMethod = ProbeMethod.URL_TEST.forEngine(xray.coreEngine()))
        assertEquals(ProbeMethod.REAL_DELAY, demoted.probeMethod)
    }

    @Test
    fun theRealDelayDefaultSurvivesEveryEngine() {
        assertEquals(ProbeMethod.REAL_DELAY, AppSettings().probeMethod)
        assertEquals(ProbeMethod.REAL_DELAY, ProbeMethod.REAL_DELAY.forEngine(CoreEngine.XRAY))
        assertEquals(ProbeMethod.REAL_DELAY, ProbeMethod.REAL_DELAY.forEngine(CoreEngine.SINGBOX))
    }

    // ───────────────────────────────────────────────────────── cancellation

    @Test
    fun aCancelArmsOnceAndCanBeReusedByTheNextSweep() {
        val gate = ProbeCancelGate()
        assertTrue(gate.mayStart())
        assertFalse(gate.isRequested)

        assertTrue("the first tap arms the latch", gate.arm())
        assertFalse("a second tap must not re-arm it", gate.arm())
        assertTrue(gate.isRequested)
        assertFalse("a cancelled sweep starts no further work", gate.mayStart())
        assertTrue(gate.shouldStop())

        gate.reset()
        assertTrue("the next sweep must start un-cancelled", gate.mayStart())
        assertFalse(gate.shouldStop())
        assertTrue("resetting an unarmed gate is harmless", ProbeCancelGate().let { it.reset(); it.mayStart() })
    }

    @Test
    fun cancellingKeepsEveryMeasurementThatAlreadyLanded() {
        val landed = listOf(120.0, 340.0, 88.0)
        assertEquals(
            "cancel means stop, not throw away what was learned",
            landed,
            ProbeCancelGate.retainedAfterCancel(landed)
        )
        assertEquals(emptyList<Double>(), ProbeCancelGate.retainedAfterCancel(emptyList<Double>()))
    }

    // ───────────────────────────────────────────────────────── real delay without a tunnel

    @Test
    fun realDelayWithoutATunnelSaysSoOnlyWhenNoHookCanBuildOne() {
        val profile = com.marbleng.app.model.ProxyProfile(
            id = "node", name = "Node", scheme = "vless", raw = "",
            configJson = "{}", host = "192.0.2.1", port = 443
        )
        RouteProbe.realDelayHook = null
        try {
            val unhanded = RouteProbe.realDelay(profile, 0, 1000, 2, xray)
            assertEquals(0, unhanded.successPercent)
            assertEquals("no-live-tunnel", unhanded.failureReason)

            // With the hook the repository installs, the same call measures instead of refusing.
            RouteProbe.realDelayHook = { _, _, _, _ ->
                RouteProbe.ProbeResult(RouteProbe.METHOD_REAL_DELAY, 137.0, 100, 2)
            }
            val measured = RouteProbe.realDelay(profile, 0, 1000, 2, xray)
            assertEquals(100, measured.successPercent)
            assertEquals(137.0, measured.latencyMs, 0.001)
            assertEquals(RouteProbe.METHOD_REAL_DELAY, measured.method)
        } finally {
            RouteProbe.realDelayHook = null
        }
    }

    @Test
    fun theDelayTargetContractIsOneRuleForTheOneUrlTest() {
        assertEquals(null, UrlTestTarget.validate(DelayTest.URL))
        assertEquals(UrlTestTarget.INVALID, UrlTestTarget.validate("http://example.com/generate_204"))
        assertEquals(UrlTestTarget.INVALID, UrlTestTarget.validate("https://user:pw@example.com/"))
    }
}
