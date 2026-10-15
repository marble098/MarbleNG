package com.marbleng.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingBoxCoreCrashV157Test {
    @get:Rule val temporary = TemporaryFolder()

    private fun crash(pc: String = "0x5b24819b6c") =
        "core-start: exited 2: WARN network: initialize package manager: read packages list: " +
            "open /data/system/packages.xml: permission denied\n" +
            "panic: runtime error: invalid memory address or nil pointer dereference\n" +
            "[signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=$pc]\n" +
            "goroutine 1 [running]:"

    private val packageManagerWarn =
        "WARN network: initialize package manager: read packages list: " +
            "open /data/system/packages.xml: permission denied"

    @Test
    fun theReportedCrashIsClassifiedAsACoreFaultByEveryClassifier() {
        val reason = crash()
        assertTrue("the panic must be recognised", SingBoxAndroidRuntime.isCoreCrash(reason))
        assertTrue("…and as a core no node can fix", SingBoxAndroidRuntime.isUnusableCore(reason))
        assertTrue(
            "the VPN service must treat it as an engine fault, not a profile fault",
            SingBoxConfigDoctor.isEngineLevelFault(reason)
        )
        assertTrue(
            "ranking must not punish a profile for it",
            CoreFailurePolicy.isLocal(reason)
        )
        assertFalse(
            "it is not a config refusal: the document was accepted, the process then died",
            SingBoxManager.isConfigRefusal(reason)
        )
        assertTrue(
            "one crash is one fault, not 17 dead servers",
            ProbeLocalFaultGate().isSweepFatal(reason, 0)
        )
    }

    @Test
    fun everyReportedInstanceIsRecognisedWhateverItsAddressWas() {
        listOf("0x5c0a469b6c", "0x628ff65b6c", "0x5b24819b6c").forEach { pc ->
            assertTrue(
                "pc=$pc must classify identically",
                SingBoxAndroidRuntime.isUnusableCore(crash(pc))
            )
        }

        assertTrue(SingBoxAndroidRuntime.isCoreCrash("core-start: exited 2: "))
        assertTrue(SingBoxAndroidRuntime.isCoreCrash("fatal error: all goroutines are asleep"))
        assertTrue(SingBoxAndroidRuntime.isCoreCrash("unexpected fault address 0x0"))
        assertTrue(SingBoxAndroidRuntime.isCoreCrash("signal SIGABRT: aborted"))
    }

    @Test
    fun aCrashIsNeverAnsweredByTryingTheNextReaderOrTheNextServer() {
        val gate = ProbeLocalFaultGate()
        assertTrue("the first crashed node arms the gate", gate.trip(crash(), 0))
        assertFalse("the second one is the same fault, not a new event", gate.trip(crash("0x628ff65b6c"), 0))
        assertTrue(gate.isTripped)
        assertTrue("the sweep stops starting new candidates", !gate.mayStart())
        assertTrue("…through the same predicate the engines already poll", gate.shouldStop())
        assertEquals(crash(), gate.reason)

        gate.reset()
        assertFalse(gate.isTripped)
        assertEquals("", gate.reason)
        assertTrue("and the next sweep is born un-stopped", gate.mayStart())
        assertTrue("a fault found earlier does not poison a later sweep", gate.trip(crash(), 0))
    }

    @Test
    fun concurrentWorkersArmTheGateExactlyOnce() {
        val gate = ProbeLocalFaultGate()
        val workers = 8
        val pool = Executors.newFixedThreadPool(workers)
        val armed = AtomicInteger(0)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        repeat(workers) { index ->
            pool.execute {
                try {
                    start.await()
                    if (gate.trip(crash("0x5b2481${index}b6c"), 0)) armed.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals("one fault, one published stop", 1, armed.get())
        assertTrue(gate.isTripped)
    }

    @Test
    fun aConfigRefusalIsStillAConfigRefusal() {
        val refusal = "core-start: exited 1: FATAL parse config"
        assertTrue(SingBoxManager.isConfigRefusal(refusal))
        assertFalse("a refusal is not a crash", SingBoxAndroidRuntime.isCoreCrash(refusal))
        assertFalse(SingBoxAndroidRuntime.isUnusableCore(refusal))
        assertFalse("…so it must not stop a sweep", ProbeLocalFaultGate().isSweepFatal(refusal, 0))
    }

    @Test
    fun aPerNodeRefusalCarryingThePackageManagerWarningStaysPerNode() {
        val refusal = "core-start: exited 1: $packageManagerWarn\nFATAL parse config: unknown field"
        assertTrue(SingBoxAndroidRuntime.isPackageManagerFault(refusal))
        assertFalse(SingBoxAndroidRuntime.isCoreCrash(refusal))
        assertFalse(SingBoxAndroidRuntime.isUnusableCore(refusal))
        assertTrue(SingBoxManager.isConfigRefusal(refusal))
        assertFalse(ProbeLocalFaultGate().isSweepFatal(refusal, 0))
    }

    @Test
    fun theReasonsThatMustNotStopASweepDoNotStopIt() {
        val gate = ProbeLocalFaultGate()

        listOf(
            "core-config: no sing-box representation of this profile",
            "config-unsupported: settings.address: missing server",
            "core-config: invalid DNS field"
        ).forEach { assertFalse(it, gate.isSweepFatal(it, 0)) }

        listOf(
            "core-busy: measurement capacity is occupied",
            "core-start-timeout: sing-box did not open its listener",
            "core-check-timeout: configuration validation did not finish"
        ).forEach { assertFalse(it, gate.isSweepFatal(it, 0)) }

        listOf(
            "urltest-http-503: upstream unavailable",
            "urltest-transport: connection reset by peer",
            "urltest-url: an HTTPS URL without user info is required",
            "urltest-no-target",
            "timeout"
        ).forEach { assertFalse(it, gate.isSweepFatal(it, 0)) }

        assertFalse(gate.isSweepFatal(crash(), 1))
        assertFalse(gate.isSweepFatal("", 0))
    }

    @Test
    fun theReasonsThatMustStopASweepStopIt() {
        val gate = ProbeLocalFaultGate()
        listOf(
            "core-install: sing-box extended is missing from this APK",
            "core-assets: bundled sing-box rule sets are missing/corrupt; rebuild the APK",
            "core-crash: ${crash()}",
            "core-selftest: cancelled",
            "core-exit: the core process left while it was carrying traffic",
            "core-port: unable to allocate distinct loopback ports",
            "core-unavailable: sing-box is not running",
            "no-live-tunnel: the route is not up"
        ).forEach { assertTrue(it, gate.isSweepFatal(it, 0)) }

        assertTrue(
            gate.isSweepFatal(
                "FATAL initialize network manager: create network monitor: netlink socket in " +
                    "Android is banned by Google, use the root or system (ADB) user to run sing-box",
                0
            )
        )
    }

    @Test
    fun networkShapedReasonsAreNeverLocalFaults() {
        assertFalse(CoreFailurePolicy.isLocal("urltest-http-503: protocol handshake failed"))
        assertTrue(CoreFailurePolicy.isLocal("core-crash: ${crash()}"))
        assertTrue(CoreFailurePolicy.isLocal("core-selftest: cancelled"))
        assertTrue(CoreFailurePolicy.isLocal("core-start: exited 2: panic: runtime error:"))
    }

    @Test
    fun aCrashTravelsWithItsRemediationExactlyOnce() {
        val explained = SingBoxAndroidRuntime.explain(crash())
        assertTrue(explained.contains(SingBoxAndroidRuntime.CRASH_REMEDIATION))
        assertEquals(
            "explaining an explained reason must not stack a second paragraph",
            explained,
            SingBoxAndroidRuntime.explain(explained)
        )

        assertFalse(explained.contains(SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION))
    }

    @Test
    fun thePackageManagerWarningAloneIsExplainedButNotFatal() {
        val explained = SingBoxAndroidRuntime.explain(packageManagerWarn)
        assertTrue(explained.contains(SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION))
        assertTrue(SingBoxAndroidRuntime.isPackageManagerFault(packageManagerWarn))
        assertFalse(SingBoxAndroidRuntime.isUnusableCore(packageManagerWarn))
    }

    @Test
    fun anUnrecognisedReasonIsPassedThroughVerbatim() {
        listOf(
            "connection reset by peer",
            "urltest-http-403: forbidden",
            "core-start: exited 1: FATAL parse config"
        ).forEach { assertEquals(it, SingBoxAndroidRuntime.explain(it)) }
    }

    @Test
    fun theStoppedSweepSaysWhoseFaultItWasNotHowManyServersDied() {
        assertEquals(
            "the tunnel core crashed while starting",
            ProbeLocalFaultGate.summary(crash())
        )
        assertEquals(
            "the tunnel core binary is missing from this build",
            ProbeLocalFaultGate.summary("core-install: sing-box extended is missing from this APK")
        )
        assertEquals(
            "there is no live tunnel to measure through",
            ProbeLocalFaultGate.summary("no-live-tunnel: the route is not up")
        )
        assertEquals(
            "Android would not allocate loopback ports for a measurement core",
            ProbeLocalFaultGate.summary("core-port: unable to allocate distinct loopback ports")
        )

        assertEquals(
            "core-exit: the core process left",
            ProbeLocalFaultGate.summary("core-exit: the core process left")
        )
    }

    @Test
    fun theHeadlineIsTheFatalLineNotTheWarningAboveIt() {
        assertEquals(
            "panic: runtime error: invalid memory address or nil pointer dereference",
            ProbeLocalFaultGate.headline(crash())
        )
        assertEquals(
            "…and the remediation paragraph is not a headline",
            "panic: runtime error: invalid memory address or nil pointer dereference",
            ProbeLocalFaultGate.headline(SingBoxAndroidRuntime.explain(crash()))
        )
        assertEquals(
            "core-config: invalid DNS field",
            ProbeLocalFaultGate.headline("core-config: invalid DNS field")
        )
        val long = "core-config: " + "x".repeat(400)
        assertEquals(201, ProbeLocalFaultGate.headline(long).length)
        assertTrue(ProbeLocalFaultGate.headline(long).endsWith("…"))
    }

    @Test
    fun theCanaryMirrorsTheProductionConstructsThatCrashedAndNothingThatCanReachANetwork() {
        val canary = JSONObject(SingBoxCoreSelfTest.canaryConfig(40_000))
        val inbound = canary.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals(SingBoxConfigBuilder.INBOUND_TAG, inbound.getString("tag"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(40_000, inbound.getInt("listen_port"))

        val outbound = canary.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("direct", outbound.getString("type"))
        assertEquals(SingBoxConfigBuilder.DIRECT_TAG, outbound.getString("tag"))
        assertEquals(SingBoxConfigBuilder.DIRECT_TAG, canary.getJSONObject("route").getString("final"))

        assertEquals(1, canary.getJSONArray("inbounds").length())
        assertEquals(1, canary.getJSONArray("outbounds").length())
        listOf("dns", "experimental", "ntp", "tun").forEach { key ->
            assertFalse("the canary must stay offline: $key", canary.has(key))
        }
        assertFalse(
            "the canary must not ask for the interface monitor Android bans",
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.any { "\"$it\"" in canary.toString() }
        )
        assertThrows(IllegalArgumentException::class.java) { SingBoxCoreSelfTest.canaryConfig(0) }
        assertThrows(IllegalArgumentException::class.java) { SingBoxCoreSelfTest.canaryConfig(70_000) }
    }

    @Test
    fun aVerdictOnlyShortCircuitsTheProductWhenItIsACoreFault() {
        val crashed = SingBoxCoreSelfTest.Verdict(false, SingBoxAndroidRuntime.explain(crash()), "a|1|2", 1L)
        assertTrue(crashed.coreFault)
        assertEquals("core-crash: ${crashed.reason}", crashed.asFailureReason())
        assertEquals("core cannot start on this device", crashed.summary)

        listOf("core-start-timeout: listener did not open", "core-selftest: cancelled", "").forEach {
            val inconclusive = SingBoxCoreSelfTest.Verdict(false, it, "a|1|2", 1L)
            assertFalse(it, inconclusive.coreFault)
            assertEquals("core-unavailable: $it", inconclusive.asFailureReason())
            assertEquals("core self-test inconclusive", inconclusive.summary)
        }

        val healthy = SingBoxCoreSelfTest.Verdict(true, "", "a|1|2", 1L)
        assertFalse(healthy.coreFault)
        assertEquals("core starts and serves its local inbound", healthy.summary)
    }

    @Test
    fun probingAMissingBinaryIsAFaultReportNotAnExceptionAndNotAProcess() {
        val directory = temporary.newFolder("selftest")
        val verdict = SingBoxCoreSelfTest.probe(File(directory, "absent"), directory, 40_001)
        assertFalse(verdict.ok)
        assertTrue(verdict.reason.startsWith("core-install:"))
        assertFalse("a missing binary is not a crash verdict", verdict.coreFault)

        val stub = File(directory, "libsingbox.so").apply { writeText("#!/bin/sh\nexit 2\n") }
        val stubbed = SingBoxCoreSelfTest.probe(stub, directory, 40_002)
        assertFalse(stubbed.ok)
        assertTrue(stubbed.reason.startsWith("core-install:"))
    }

    @Test
    fun aVerdictIsCachedAgainstTheBinaryItDescribes() {
        val directory = temporary.newFolder("selftest-cache")
        val binary = File(directory, "libsingbox.so").apply { writeText("x".repeat(4096)) }
        val cache = File(directory, "singbox-selftest.json")
        val verdict = SingBoxCoreSelfTest.Verdict(false, crash(), SingBoxCoreSelfTest.fingerprint(binary), 1_700_000_000_000L)

        SingBoxCoreSelfTest.write(cache, verdict)
        assertEquals(verdict, SingBoxCoreSelfTest.read(cache, binary))

        binary.writeText("y".repeat(8192))
        binary.setLastModified(binary.lastModified() + 60_000L)
        assertNotEquals(verdict.fingerprint, SingBoxCoreSelfTest.fingerprint(binary))
        assertNull(SingBoxCoreSelfTest.read(cache, binary))
        assertNull(SingBoxCoreSelfTest.read(cache, File(directory, "never-existed")))

        cache.writeText("{ not json")
        assertNull(SingBoxCoreSelfTest.read(cache, binary))
        cache.writeText(
            JSONObject()
                .put("ok", true)
                .put("reason", "")
                .put("fingerprint", SingBoxCoreSelfTest.fingerprint(binary))
                .put("at", 0L)
                .toString()
        )
        assertNull("a verdict with no timestamp is not a verdict", SingBoxCoreSelfTest.read(cache, binary))
    }
}
