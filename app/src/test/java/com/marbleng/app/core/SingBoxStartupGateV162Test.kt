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

/**
 * MARBLE_SINGBOX_STARTUP_GATE_V162 — the local inbound is the tunnel; the controller is a
 * measurement surface.
 *
 * The reported session died like this:
 *
 * ```
 * 12:17:22.809  SINGBOX start-begin   engine=singbox profile=SOLIDVPS port=10808 mode=tun
 * 12:17:23      (core)  WARN network: initialize package manager: read packages list: open
 *                              /data/system/packages.xml: permission denied
 * 12:17:34.930  SINGBOX start-result  ok=false elapsedMs=12119 phase=failed alive=false
 *               reason=core-start-timeout: … (the WARN above, and nothing else)
 * 12:17:34.935  APP     state          from=CONNECTING to=BLOCKED • Kill switch active
 * ```
 *
 * One benign WARN — the line every `GOOS=android` core prints, because `/data/system/packages.xml`
 * is `0660 system:system` and an app UID can only ever get EACCES — and then eleven seconds of
 * silence from a process that was still alive. MarbleNG answered by killing the core and blocking
 * the route.
 *
 * What the log could not say is *which half of the wait* had failed, and that is not a nicety:
 * the pinned core's `box.Start()` opens the local `mixed` inbound — the only socket
 * hev-socks5-tunnel ever dials — two stages before the Clash controller binds, because the
 * controller is an internal service started at `StartStateStarted`, after every outbound's
 * post-start walk. The old readiness condition was "inbound **and** controller", so:
 *
 *  - a delay anywhere in the core's last third, none of which carries a single byte of user
 *    traffic, was paid as a failed connect with the kill switch held; and
 *  - a core that was already carrying traffic was killed because a *diagnostic* had not bound.
 *
 * These tests pin the split, on real child processes and with injectable readiness answers, so
 * the two halves can never again be the same sentence.
 */
class SingBoxStartupGateV162Test {

    @get:Rule val temporary = TemporaryFolder()

    // ─────────────────────────────────────────────────────────────────────────────
    // 1 — the tunnel is the inbound
    // ─────────────────────────────────────────────────────────────────────────────

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
        // The URL test reads /proxies, so a core without a controller is useless to it. The
        // requirement is a decision of the caller, and the URL test still makes it.
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
        // V157's settle window is the reason the live path can stop waiting for the controller at
        // all: the post-start crash it exists to catch lands microseconds after the port opens,
        // and this is the window that catches it.
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

    // ─────────────────────────────────────────────────────────────────────────────
    // 2 — the validation spawn no longer eats the child's window
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun aSlowValidationNoLongerShrinksTheWindowTheChildStartsIn() {
        // `sing-box check` and `sing-box run` used to share one 12 s budget, so a validation that
        // took eight seconds left the child four to open its inbound in and then reported the
        // child as broken. The probe is handed the time the child still has: it is the measurement.
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
        // 1 s of validation + 1.5 s of start-up inside a 2 s budget: the old shared budget left
        // the child 1 s and failed a core that needed 1.5.
        val probes = FakeProbes(inboundAnswer = true, inboundAfterMs = 1_500)
        val session = openSession(probes = probes, requireController = false, startupTimeoutMs = 2_000, checkBody = "sleep 1")
        try {
            assertTrue("the start-up window belongs to the child", session.readiness.inboundUp)
        } finally {
            session.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3 — what the user is told
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun aStartUpTimeoutIsNoLongerExplainedAsThePackageManagerWarning() {
        // The reported reason carried the benign Android package-list WARN and nothing else, so
        // the product handed the user V157's "update MarbleNG / switch to Xray" paragraph — advice
        // for a pre-V157 crash, printed for a timeout on a device that was already up to date.
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
        // Slowness is not brokenness (V157): neither half stops a sweep, neither is a config
        // refusal, and both are still local faults.
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
        // `controllerMissing` drives a Bug Finder WARN, so it must not fire for the measurement
        // cores that never dial the controller, nor for a start that is still running.
        assertFalse(StartReadiness(true, false, false, "ready", 12).controllerMissing)
        assertFalse(StartReadiness(true, true, true, "ready", 12).controllerMissing)
        assertTrue(StartReadiness(true, false, true, "controller", 12).controllerMissing)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // fakes
    // ─────────────────────────────────────────────────────────────────────────────

    /** Answers the two questions the start-up loop asks, without a device or a real core. */
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

    /**
     * A stand-in for the core: `check` and `run` are the only two verbs the session uses, and a
     * POSIX shell is the one runtime a JVM test can rely on without downloading a binary.
     */
    private fun fakeCore(checkBody: String, runBody: String): File {
        val binary = File(temporary.root, "fake-singbox.sh")
        // The shell's own positional parameter is spelled with a dollar sign, which inside a
        // Kotlin string has to be escaped rather than interpolated.
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
