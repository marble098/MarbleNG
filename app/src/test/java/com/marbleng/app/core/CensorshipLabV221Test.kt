package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.FragmentStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CENSORSHIP_LAB_V221 — the lab's two pure halves, pinned.
 *
 * Every case below is a path shape a real censoring middlebox produces, described only by what a
 * probe would have measured. Nothing here touches a socket, so the suite is deterministic and can
 * run on the JVM in CI.
 */
class CensorshipLabV221Test {
    private val dnsOk = CensorshipLab.Observation(
        target = "www.gstatic.com:53",
        kind = CensorshipLab.ProbeKind.DNS,
        reachable = true
    )

    private fun tls(reachable: Boolean, tcp: Boolean, reset: Boolean = false, latency: Int = 40) =
        CensorshipLab.Observation(
            target = "www.gstatic.com:443",
            kind = CensorshipLab.ProbeKind.TLS,
            reachable = reachable,
            latencyMs = latency,
            tcpConnected = tcp,
            tlsCompleted = reachable,
            resetSuspected = reset
        )

    private fun tcp(reachable: Boolean, target: String = "1.1.1.1:443", latency: Int = 30) =
        CensorshipLab.Observation(
            target = target,
            kind = CensorshipLab.ProbeKind.TCP,
            reachable = reachable,
            latencyMs = latency,
            tcpConnected = reachable
        )

    @Test
    fun aCleanPathFindsNothing() {
        val diagnosis = CensorshipLab.diagnose(listOf(dnsOk, tls(true, true), tcp(true)))

        assertTrue(diagnosis.clean)
        assertFalse(diagnosis.decisive)
        assertEquals(CensorshipLab.Severity.INFO, diagnosis.severity)
        assertTrue(diagnosis.summary.contains("No interference"))
    }

    @Test
    fun aHandshakeThatDiesAfterTcpIsNameFiltering() {
        val diagnosis = CensorshipLab.diagnose(listOf(dnsOk, tls(false, true), tcp(true)))

        assertTrue(CensorshipLab.Technique.SNI_FILTER in diagnosis.techniques)
        assertEquals(CensorshipLab.Severity.CRITICAL, diagnosis.severity)
        assertFalse("a plain timeout must not be promoted to a reset", CensorshipLab.Technique.TLS_RESET in diagnosis.techniques)
    }

    @Test
    fun anInjectedResetIsItsOwnFindingAndTheHeaviestPrescription() {
        val diagnosis = CensorshipLab.diagnose(listOf(dnsOk, tls(false, true, reset = true), tcp(true)))

        assertTrue(CensorshipLab.Technique.TLS_RESET in diagnosis.techniques)
        val plan = CensorshipLab.prescribe(diagnosis, AppSettings())
        assertEquals(FragmentStrategy.FULL, plan.fragmentStrategy)
        assertTrue(plan.requireExtendedCore)
        assertTrue(plan.muxPadding)
    }

    @Test
    fun aResolversFailureWhileAddressesAnswerIsDnsInterference() {
        val deadDns = dnsOk.copy(reachable = false)
        val diagnosis = CensorshipLab.diagnose(listOf(deadDns, tls(true, true), tcp(true)))

        assertTrue(CensorshipLab.Technique.DNS_TAMPER in diagnosis.techniques)
        val plan = CensorshipLab.prescribe(diagnosis, AppSettings())
        assertTrue(plan.fakeIpDns)
        assertTrue(plan.notes.any { it.contains("encrypted DNS") })
    }

    @Test
    fun aLiteralAddressThatNeverAnswersIsAnAddressBlockNotANameBlock() {
        val diagnosis = CensorshipLab.diagnose(
            listOf(dnsOk, tcp(false), tls(false, false))
        )

        assertTrue(CensorshipLab.Technique.IP_BLOCK in diagnosis.techniques)
        assertFalse(CensorshipLab.Technique.SNI_FILTER in diagnosis.techniques)
    }

    @Test
    fun aFilteredAlternatePortIsReportedOnlyWhen443Answers() {
        val alt = CensorshipLab.Observation(
            target = "1.1.1.1:853",
            kind = CensorshipLab.ProbeKind.ALT_PORT,
            reachable = false,
            tcpConnected = false
        )
        val blocked = CensorshipLab.diagnose(listOf(dnsOk, tls(true, true), tcp(true), alt))
        assertTrue(CensorshipLab.Technique.PORT_BLOCK in blocked.techniques)

        val everythingDead = CensorshipLab.diagnose(
            listOf(dnsOk, tls(false, false), tcp(false), alt)
        )
        assertFalse(
            "a dead path is not a filtered port",
            CensorshipLab.Technique.PORT_BLOCK in everythingDead.techniques
        )
    }

    @Test
    fun unevenAnswersAreShapingNotBlocking() {
        val diagnosis = CensorshipLab.diagnose(
            listOf(
                dnsOk,
                tls(true, true, latency = 70),
                tcp(true, latency = 900)
            )
        )

        assertTrue(CensorshipLab.Technique.THROTTLING in diagnosis.techniques)
        assertFalse(diagnosis.decisive)
    }

    @Test
    fun thePrescriptionNeverSpendsAProtectionTheUserSwitchedOff() {
        val off = AppSettings(fragmentStrategy = FragmentStrategy.OFF.id)
        val diagnosis = CensorshipLab.diagnose(listOf(dnsOk, tls(false, true), tcp(true)))

        val plan = CensorshipLab.prescribe(diagnosis, off)
        assertEquals(FragmentStrategy.OFF, plan.fragmentStrategy)
        assertTrue(plan.notes.any { it.contains("Fragmentation stays off") })
    }

    @Test
    fun thePrescriptionIsAProjectionAndLeavesIdentityAlone() {
        val base = AppSettings()
        val diagnosis = CensorshipLab.diagnose(listOf(dnsOk, tls(false, true), tcp(true)))
        val next = CensorshipLab.prescribe(diagnosis, base).applyTo(base)

        assertEquals(FragmentStrategy.RECORD.id, next.fragmentStrategy)
        assertTrue(next.singBoxMuxPadding)
        assertEquals("identity must survive a prescription", base.coreEngineId, next.coreEngineId)
        assertEquals("routing must survive a prescription", base.routingMode, next.routingMode)
        assertEquals("certificate policy must survive", base.strictAddressFamily, next.strictAddressFamily)
    }

    @Test
    fun aCleanDiagnosisAsksForNothing() {
        val plan = CensorshipLab.prescribe(
            CensorshipLab.diagnose(listOf(dnsOk, tls(true, true), tcp(true))),
            AppSettings()
        )
        assertEquals(FragmentStrategy.OFF, plan.fragmentStrategy)
        assertFalse(plan.requireExtendedCore)
        assertFalse(plan.muxPadding)
    }

    @Test
    fun theScannerRunsItsProbesAndReportsSomethingEitherWay() {
        // The scanner must never throw on a dead sandbox network: an unreachable path is a
        // diagnosis ("everything failed"), not an exception the page has to catch.
        val diagnosis = CensorshipProbe.scan(
            settings = AppSettings(),
            timeoutMs = 500,
            dnsLookup = { _, _ -> false }
        )

        assertNotNull(diagnosis)
        assertTrue(diagnosis.observations.isNotEmpty())
        assertEquals(
            "the DNS probe is always the first observation",
            CensorshipLab.ProbeKind.DNS,
            diagnosis.observations.first().kind
        )
    }
}
