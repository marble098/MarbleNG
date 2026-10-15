package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.DelayTest
import com.marbleng.app.model.ProbeMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeTruthV156Test {
    private val xray = AppSettings()
    private val singBox = AppSettings(coreEngineId = CoreEngine.SINGBOX.id)

    @Test
    fun urlTestIsOfferedOnlyOnTheEngineThatOwnsIt() {
        assertTrue(ProbeMethod.URL_TEST.availableOn(CoreEngine.SINGBOX))
        assertFalse(ProbeMethod.URL_TEST.availableOn(CoreEngine.XRAY))

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

        val demoted = xray.copy(probeMethod = ProbeMethod.URL_TEST.forEngine(xray.coreEngine()))
        assertEquals(ProbeMethod.REAL_DELAY, demoted.probeMethod)
    }

    @Test
    fun theRealDelayDefaultSurvivesEveryEngine() {
        assertEquals(ProbeMethod.REAL_DELAY, AppSettings().probeMethod)
        assertEquals(ProbeMethod.REAL_DELAY, ProbeMethod.REAL_DELAY.forEngine(CoreEngine.XRAY))
        assertEquals(ProbeMethod.REAL_DELAY, ProbeMethod.REAL_DELAY.forEngine(CoreEngine.SINGBOX))
    }

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

            RouteProbe.realDelayHook = { _, _, _, _, _ ->
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
