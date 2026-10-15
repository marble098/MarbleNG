package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class SingBoxStartupGateV162Test {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun aTunnelWhoseControllerNeverAnswersStillCarriesTraffic() {
        val probes = FakeProbes(inboundAnswer = true, controllerAnswer = false)
        val session = openSession(probes = probes, requireController = false, controllerTimeoutMs = 200)
        try {
            assertTrue("the session must be handed out: the inbound is up", session.readiness.inboundUp)
            assertFalse("the controller is recorded as missing, not assumed", session.readiness.controllerUp)
            assertTrue("…and the degradation is named for Bug Finder", session.readiness.controllerMissing)
            assertEquals("the wait ended in the controller phase", "controller", session.readiness.phase)
            assertTrue("a working tunnel is never killed for a missing diagnostic", session.isAlive)
            assertTrue("the controller really was asked", probes.controllerProbes > 0)
        } finally {
            session.close()
        }
    }

    @Test
    fun aControllerTheMeasurementActuallyDialsIsStillRequired() {
        val probes = FakeProbes(inboundAnswer = true, controllerAnswer = false)
        val failure = runCatching {
            openSession(probes = probes, requireController = true, startupTimeoutMs = 500)
        }.exceptionOrNull()
        assertNotNull("no controller, no session", failure)
        val reason = failure!!.message.orEmpty()
        assertTrue("it stays a start-up timeout, so no classifier changes: $reason", reason.startsWith("core-start-timeout:"))
        assertTrue("…and it names the half that failed: $reason", reason.contains("controller never answered"))
        assertFalse("…not the half that succeeded: $reason", reason.contains("local inbound never answered"))
    }

    @Test
    fun anInboundThatNeverOpensIsTheOtherFailureAndSaysWhichOneItWas() {
        val probes = FakeProbes(inboundAnswer = false)
        val failure = runCatching {
            openSession(probes = probes, requireController = false, startupTimeoutMs = 400)
        }.exceptionOrNull()
        assertNotNull("a core that never serves a port is a failure", failure)
        val reason = failure!!.message.orEmpty()
        assertTrue("it stays a start-up timeout: $reason", reason.startsWith("core-start-timeout:"))
        assertTrue("…and it names the inbound: $reason", reason.contains("local inbound never answered"))
        assertFalse("the controller is never even asked once the inbound fails", probes.controllerProbes > 0)
    }

    @Test
    fun aCoreThatDiesAfterItsInboundOpensIsStillReportedAsACrash() {
        val probes = FakeProbes(inboundAnswer = true, controllerAnswer = false)
        val failure = runCatching {
            openSession(probes = probes, requireController = false, settleMs = 4_000, runBody = "sleep 1; exit 2")
        }.exceptionOrNull()
        assertNotNull("a child that dies is a crash, not a session", failure)
        assertTrue(
            "the V157 vocabulary is preserved: ${failure!!.message}",
            failure.message.orEmpty().startsWith("core-start: exited 2:")
        )
    }

    @Test
    fun aSlowValidationNoLongerShrinksTheWindowTheChildStartsIn() {
        val probes = FakeProbes(inboundAnswer = false)
        runCatching {
            openSession(probes = probes, startupTimeoutMs = 2_000, checkBody = "sleep 1")
        }
        assertTrue(
            "the child got its own window, not the remains of the validator's: " +
                "first probe carried ${probes.firstInboundTimeoutMs} ms",
            probes.firstInboundTimeoutMs >= 1_800
        )
    }

    @Test
    fun aChildThatNeedsLongerThanTheLeftoversStillStarts() {
        val probes = FakeProbes(inboundAnswer = true, inboundAfterMs = 1_500)
        val session = openSession(probes = probes, requireController = false, startupTimeoutMs = 2_000, checkBody = "sleep 1")
        try {
            assertTrue("the start-up window belongs to the child", session.readiness.inboundUp)
        } finally {
            session.close()
        }
    }

    @Test
    fun aStartUpTimeoutIsNoLongerExplainedAsThePackageManagerWarning() {
        val reason = "core-start-timeout: the local inbound never answered in 12000 ms: " +
            "WARN network: initialize package manager: read packages list: " +
            "open /data/system/packages.xml: permission denied"
        assertTrue(SingBoxAndroidRuntime.isStartupTimeout(reason))
        assertTrue(
            "the WARN is still evidence",
            SingBoxAndroidRuntime.isPackageManagerFault(reason)
        )
        val explained = SingBoxAndroidRuntime.explain(reason)
        assertTrue(
            "a timeout gets the timeout paragraph",
            explained.contains(SingBoxAndroidRuntime.STARTUP_TIMEOUT_REMEDIATION)
        )
        assertFalse(
            "…not the crash-build paragraph",
            explained.contains(SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION)
        )
        assertTrue(
            "explaining twice must not stack a second paragraph",
            explained == SingBoxAndroidRuntime.explain(explained)
        )
    }

    @Test
    fun theTwoHalvesOfTheWaitAreStillNotVerdictsAboutAServer() {
        val gate = ProbeLocalFaultGate()
        listOf(
            "core-start-timeout: the local inbound never answered in 12000 ms",
            "core-start-timeout: the controller never answered in 2500 ms (the local inbound was up)"
        ).forEach { reason ->
            assertFalse("a slow start is not a sweep-fatal fault: $reason", gate.isSweepFatal(reason, 0))
            assertTrue("it is still a local fault: $reason", CoreFailurePolicy.isLocal(reason))
            assertFalse(
                "no reader walk: every candidate binds the same port: $reason",
                SingBoxManager.isConfigRefusal(reason)
            )
        }
    }

    @Test
    fun aReadinessThatNeverHappenedIsNotADegradedSession() {
        assertFalse(StartReadiness(true, false, false, "ready", 12).controllerMissing)
        assertFalse(StartReadiness(true, true, true, "ready", 12).controllerMissing)
        assertTrue(StartReadiness(true, false, true, "controller", 12).controllerMissing)
    }

    private class FakeProbes(
        private val inboundAnswer: Boolean,
        private val inboundAfterMs: Long = 0L,
        private val controllerAnswer: Boolean = true
    ) : ReadinessProbes {
        @Volatile var firstInboundTimeoutMs: Long = -1L
        @Volatile var controllerProbes: Int = 0
        @Volatile private var firstProbeNs: Long = 0L

        override fun inbound(port: Int, timeoutMs: Long): Boolean {
            val now = System.nanoTime()
            if (firstProbeNs == 0L) {
                firstProbeNs = now
                firstInboundTimeoutMs = timeoutMs
            }
            if (!inboundAnswer) return false
            return TimeUnit.NANOSECONDS.toMillis(now - firstProbeNs) >= inboundAfterMs
        }

        override fun controller(port: Int, secret: String, timeoutMs: Long): Boolean {
            controllerProbes++
            return controllerAnswer
        }
    }

    private fun fakeCore(checkBody: String, runBody: String): File {
        val binary = File(temporary.root, "fake-singbox.sh")

        binary.writeText(
            "#!/bin/sh\n" +
                "case \"${'$'}1\" in\n" +
                "  check) $checkBody ;;\n" +
                "  run)   $runBody ;;\n" +
                "esac\n" +
                "exit 0\n"
        )
        check(binary.setExecutable(true)) { "the fake core must be executable" }
        return binary
    }

    private fun openSession(
        probes: ReadinessProbes,
        requireController: Boolean = false,
        startupTimeoutMs: Long = 3_000,
        settleMs: Long = 0,
        controllerTimeoutMs: Long = 200,
        checkBody: String = "exit 0",
        runBody: String = "exec sleep 30"
    ): SingBoxProcessSession = SingBoxProcessSession.open(
        binary = fakeCore(checkBody, runBody),
        config = "{}",
        configFile = File(temporary.root, "config.json"),
        logFile = File(temporary.root, "core.log"),
        tempDir = temporary.root,
        socksPort = freeLoopbackPort(),
        apiPort = freeLoopbackPort(),
        secret = "secret",
        startupTimeoutMs = startupTimeoutMs,
        validate = true,
        awaitApi = true,
        settleMs = settleMs,
        requireController = requireController,
        controllerTimeoutMs = controllerTimeoutMs,
        probes = probes
    )

    private fun freeLoopbackPort(): Int = java.net.ServerSocket().use { server ->
        server.bind(java.net.InetSocketAddress("127.0.0.1", 0))
        server.localPort
    }
}
