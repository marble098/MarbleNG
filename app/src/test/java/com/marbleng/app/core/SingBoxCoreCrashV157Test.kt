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

/**
 * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — a crashed core is a fact about the device, and every
 * surface that measures through it has to say so.
 *
 * The fault these tests pin is not hypothetical. Three shipped releases carried an Android
 * sing-box artifact that panicked in `protocol/direct/outbound.go` for *every* profile, because an
 * app-UID child gets no netlink interface monitor and every MarbleNG config has a `direct`
 * outbound. The product's answers at the time, all of them literally true and all of them wrong:
 *
 * ```
 * 07:02:17  URL test    → reachable = 0 of 17
 * 07:02:23  Real delay  → reachable = 0 of 17
 * 10:30:31  core-start: exited 2: … panic … [signal SIGSEGV … pc=0x5c0a469b6c]   (Turkey 8)
 * 10:30:56  core-start: exited 2: … panic … [signal SIGSEGV … pc=0x628ff65b6c]   (Turkey 15)
 * 10:32:24  core-start: exited 2: … panic … [signal SIGSEGV … pc=0x5b24819b6c]
 *           state=BLOCKED • Core/configuration error
 *           Bug Finder (DISCONNECTED) → scan-finish | failures=0
 * ```
 *
 * Seventeen servers did not go down and no profile was misconfigured. One binary could not run,
 * and each surface reported that local fault in its own remote-sounding vocabulary: the sweeps
 * kept walking because every result looked like an ordinary dead node, the engine kept accepting
 * the next candidate because a crash matched the `core-start:` prefix of a config refusal, and Bug
 * Finder found nothing because all of its core checks are gated on a live connection — the one
 * thing a crashing core never produces.
 *
 * The native half of the fix (backporting the upstream nil-guards into the pinned core and
 * building every ABI from source) is proven by `scripts/inject-singbox-android-fix.py`, the Go
 * tests it writes, and `.github/workflows/verify.yml`. These tests prove the other half: that no
 * future core regression can ever again be published as a verdict about servers.
 */
class SingBoxCoreCrashV157Test {

    @get:Rule val temporary = TemporaryFolder()

    /**
     * The crash exactly as the retained core log carries it into a failure reason: the app-UID
     * package-manager WARN that always precedes it, then the panic, then the signal line. The
     * `pc` differs per run (ASLR) and per profile — it is the one field that changed between the
     * three reports above, and nothing may key on it.
     */
    private fun crash(pc: String = "0x5b24819b6c") =
        "core-start: exited 2: WARN network: initialize package manager: read packages list: " +
            "open /data/system/packages.xml: permission denied\n" +
            "panic: runtime error: invalid memory address or nil pointer dereference\n" +
            "[signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=$pc]\n" +
            "goroutine 1 [running]:"

    /** The same line the core prints on a *successful* Android start-up. Benign, always present. */
    private val packageManagerWarn =
        "WARN network: initialize package manager: read packages list: " +
            "open /data/system/packages.xml: permission denied"

    // ─────────────────────────────────────────────────────────────────────────────
    // 1 — the reported crash is a core fault, in every vocabulary the product speaks
    // ─────────────────────────────────────────────────────────────────────────────

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
        // Turkey 8 at 10:30:31, Turkey 15 at 10:30:56 and the 10:32:24 repeat: same fault, three
        // different `pc` values. Classification keys on the shape of the failure, never on an
        // address, a timestamp or a profile name.
        listOf("0x5c0a469b6c", "0x628ff65b6c", "0x5b24819b6c").forEach { pc ->
            assertTrue(
                "pc=$pc must classify identically",
                SingBoxAndroidRuntime.isUnusableCore(crash(pc))
            )
        }
        // The bare exit status is enough on its own: a Go panic always exits 2, and the retained
        // log tail is line-limited, so the panic text itself may not survive into the reason.
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

    // ─────────────────────────────────────────────────────────────────────────────
    // 2 — what a crash is not: the refusals and observations that must keep working
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun aConfigRefusalIsStillAConfigRefusal() {
        // Pinned by SingBoxLinkAuthorityV156Test as well: exit 1 is `log.Fatal`, the core read the
        // document and rejected it, and another reader may express the same node acceptably.
        val refusal = "core-start: exited 1: FATAL parse config"
        assertTrue(SingBoxManager.isConfigRefusal(refusal))
        assertFalse("a refusal is not a crash", SingBoxAndroidRuntime.isCoreCrash(refusal))
        assertFalse(SingBoxAndroidRuntime.isUnusableCore(refusal))
        assertFalse("…so it must not stop a sweep", ProbeLocalFaultGate().isSweepFatal(refusal, 0))
    }

    @Test
    fun aPerNodeRefusalCarryingThePackageManagerWarningStaysPerNode() {
        // The WARN is printed by every android core, including healthy ones, so it sits above the
        // real cause in the retained tail. If it counted as an unusable core, a genuine schema
        // refusal would stop the sweep and be reported as a broken device.
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
        // Per-node: the next profile is a different document.
        listOf(
            "core-config: no sing-box representation of this profile",
            "config-unsupported: settings.address: missing server",
            "core-config: invalid DNS field"
        ).forEach { assertFalse(it, gate.isSweepFatal(it, 0)) }
        // Transient inside a sweep: capacity frees up, slowness is not brokenness.
        listOf(
            "core-busy: measurement capacity is occupied",
            "core-start-timeout: sing-box did not open its listener",
            "core-check-timeout: configuration validation did not finish"
        ).forEach { assertFalse(it, gate.isSweepFatal(it, 0)) }
        // The sweep's actual subject matter. A server verdict is an observation, never a fault.
        listOf(
            "urltest-http-503: upstream unavailable",
            "urltest-transport: connection reset by peer",
            "urltest-url: an HTTPS URL without user info is required",
            "urltest-no-target",
            "timeout"
        ).forEach { assertFalse(it, gate.isSweepFatal(it, 0)) }
        // Anything that measured something is an observation whatever else its reason says.
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
        // The netlink ban carries no prefix of its own: it is classified from the core's text.
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
        // The boundary CoreFailurePolicy already drew, kept honest next to the new prefixes.
        assertFalse(CoreFailurePolicy.isLocal("urltest-http-503: protocol handshake failed"))
        assertTrue(CoreFailurePolicy.isLocal("core-crash: ${crash()}"))
        assertTrue(CoreFailurePolicy.isLocal("core-selftest: cancelled"))
        assertTrue(CoreFailurePolicy.isLocal("core-start: exited 2: panic: runtime error:"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3 — what the user is told
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun aCrashTravelsWithItsRemediationExactlyOnce() {
        val explained = SingBoxAndroidRuntime.explain(crash())
        assertTrue(explained.contains(SingBoxAndroidRuntime.CRASH_REMEDIATION))
        assertEquals(
            "explaining an explained reason must not stack a second paragraph",
            explained,
            SingBoxAndroidRuntime.explain(explained)
        )
        // The crash wins over the WARN that precedes it in the same log tail: the panic is what
        // stopped the core, and the remediation has to be about the panic.
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
        // Guessing is how a real schema error once got reported as a network outage.
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
        // A local fault with no wording of its own still gets its evidence line.
        assertEquals(
            "core-exit: the core process left",
            ProbeLocalFaultGate.summary("core-exit: the core process left")
        )
    }

    @Test
    fun theHeadlineIsTheFatalLineNotTheWarningAboveIt() {
        // `SingBoxProcessSession.tail()` hands back the last bytes of the log, so the first line
        // of a crash reason is the package-manager WARN. Printing that as the headline is what
        // made the fault look like an Android permission problem instead of a dead core.
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

    // ─────────────────────────────────────────────────────────────────────────────
    // 4 — the canary that asks the binary instead of a server
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun theCanaryMirrorsTheProductionConstructsThatCrashedAndNothingThatCanReachANetwork() {
        val canary = JSONObject(SingBoxCoreSelfTest.canaryConfig(40_000))
        val inbound = canary.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals(SingBoxConfigBuilder.INBOUND_TAG, inbound.getString("tag"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(40_000, inbound.getInt("listen_port"))

        // The `direct` outbound is the component that dereferenced the nil monitor, and
        // SingBoxConfigBuilder emits one for every profile — which is why the crash was total.
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

        // Inconclusive is not broken. A canary that lost its port race or was cancelled must
        // never be allowed to refuse the engine — that would be the canary inventing an outage.
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

        // A stub that is too small to be a core is refused the same way, without being executed.
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

        // A new APK is a new binary: same path, different bytes. The verdict must not survive it,
        // or a fixed core would keep being refused on the strength of the old one's crash.
        binary.writeText("y".repeat(8192))
        binary.setLastModified(binary.lastModified() + 60_000L)
        assertNotEquals(verdict.fingerprint, SingBoxCoreSelfTest.fingerprint(binary))
        assertNull(SingBoxCoreSelfTest.read(cache, binary))
        assertNull(SingBoxCoreSelfTest.read(cache, File(directory, "never-existed")))

        // A corrupt or partial cache costs one extra canary, never a wrong answer.
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
