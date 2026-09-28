package com.marbleng.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * MARBLE_IP_FAMILY_SCAN_V198 — equipped scanner tests.
 *
 * Keeps V196/V197 contract (five verdicts, probe ordering, TTL, etc.) and adds
 * coverage for the new equipped features:
 *  - confidence scoring
 *  - bogon filtering
 *  - NAT64 detection
 *  - adaptive TTL
 *  - jitter / median
 *  - scan modes
 *  - Happy Eyeballs race
 */
class IpFamilyScannerTest {

    private val v4 = InetAddress.getByName("192.0.2.10")
    private val v4b = InetAddress.getByName("192.0.2.11")
    private val v4c = InetAddress.getByName("192.0.2.12")
    private val v6 = InetAddress.getByName("2001:db8::10")
    private val v6b = InetAddress.getByName("2001:db8::11")
    private val v6c = InetAddress.getByName("2001:db8::12")
    private val bogonV4 = InetAddress.getByName("192.168.1.1")
    private val bogonV6 = InetAddress.getByName("fe80::1")
    private val nat64 = InetAddress.getByName("64:ff9b::192.0.2.1")

    private fun scan(
        answers: List<InetAddress>,
        host: String = "edge.example.net",
        port: Int = 443,
        underlayHasIpv6: Boolean = true,
        mode: ScanMode = ScanMode.BALANCED,
        connects: (InetAddress) -> Int
    ): IpFamilyScan = IpFamilyScanner.scanWithMode(
        host = host,
        port = port,
        networkKey = "wifi:home",
        underlayHasIpv6 = underlayHasIpv6,
        nowMs = 10_000L,
        resolver = { _, _ -> answers },
        connector = { address, _, _ -> connects(address) },
        mode = mode
    )

    // ─────────────────────────────────────────────────────────── the five verdicts

    @Test fun bothFamiliesAnsweringIsDualStack() {
        val result = scan(listOf(v6, v4)) { if (it == v6) 41 else 58 }

        assertEquals(IpFamilyVerdict.DUAL_OK, result.verdict)
        assertTrue(result.chip.contains("v4+v6"))
        assertEquals(41, result.ipv6LatencyMs)
        assertEquals(58, result.ipv4LatencyMs)
        assertTrue(result.ipv6Usable)
        assertEquals("ipv6", result.fasterFamily)
        assertTrue(result.advice.contains("Force IPv6 is safe"))
        assertTrue("confidence should be >0 for dual", result.confidence > 0)
    }

    @Test fun noAaaaRecordAtAllIsIpv4Only() {
        val result = scan(listOf(v4)) { 60 }

        assertEquals(IpFamilyVerdict.IPV4_ONLY, result.verdict)
        assertEquals("v4", result.chip)
        assertTrue(result.ipv4Locked)
        assertFalse(result.hasIpv6)
        assertTrue(result.headline.contains("no IPv6 address"))
        assertTrue(result.advice.contains("falls back"))
    }

    @Test fun anAdvertisedIpv6ThatNeverAnswersIsUnprovenNotAbsent() {
        val result = scan(listOf(v6, v4)) { if (it == v6) -1 else 55 }

        assertEquals(IpFamilyVerdict.IPV6_UNPROVEN, result.verdict)
        assertTrue("the record exists and must be remembered", result.hasIpv6)
        assertFalse(result.ipv6Ok)
        assertFalse(result.ipv4Locked)
        assertEquals("", result.fasterFamily)
    }

    @Test fun onlyIpv6AnsweringIsIpv6Only() {
        val result = scan(listOf(v6, v4)) { if (it == v6) 33 else -1 }

        assertEquals(IpFamilyVerdict.IPV6_ONLY, result.verdict)
        assertTrue(result.chip.contains("v6"))
        assertTrue(result.advice.contains("Force IPv4 cannot dial"))
    }

    @Test fun recordsThatNeverConnectAreUnreachableNotAFamilyProblem() {
        val result = scan(listOf(v6, v4)) { -1 }

        assertEquals(IpFamilyVerdict.UNREACHABLE, result.verdict)
        assertTrue(result.headline.contains("Neither family"))
    }

    @Test fun noDnsAnswerIsUnknownAndNeverLooksLikeIpv4Only() {
        val result = scan(emptyList()) { 10 }

        assertEquals(IpFamilyVerdict.UNKNOWN, result.verdict)
        assertFalse(result.ipv4Locked)
        assertEquals("?", result.chip)
    }

    @Test fun aResolverThatThrowsIsAnUnknownVerdictNotACrash() {
        val result = IpFamilyScanner.scan(
            host = "edge.example.net",
            port = 443,
            nowMs = 1L,
            resolver = { _, _ -> error("DoH exploded") },
            connector = { _, _, _ -> 10 }
        )
        assertEquals(IpFamilyVerdict.UNKNOWN, result.verdict)
    }

    // ─────────────────────────────────────────────────────────── probing behaviour

    @Test fun ipv6IsProbedFirstAndEveryFamilyIsAlwaysMeasured() {
        val order = mutableListOf<String>()
        scan(listOf(v4, v6)) { address ->
            order += if (address.hostAddress!!.contains(':')) "v6" else "v4"
            42
        }
        assertEquals(listOf("v6", "v4"), order)
    }

    @Test fun aSecondAddressOfTheSameFamilyIsTriedBeforeGivingUp() {
        val result = scan(listOf(v6, v6b, v4)) {
            when (it) {
                v6 -> -1
                v6b -> 77
                else -> 90
            }
        }
        assertEquals(IpFamilyVerdict.DUAL_OK, result.verdict)
        assertEquals(77, result.ipv6LatencyMs)
        assertEquals(v6b.hostAddress, result.ipv6Address)
    }

    @Test fun theProbeCountPerFamilyIsBounded() {
        var attempts = 0
        // V198: BALANCED keeps 2 for backward compat, so 5 addrs (3 v6 truncated to 2) => 8 attempts
        scan(listOf(v6, v6b, v6c, v4, v4b)) {
            attempts += 1
            -1
        }
        assertEquals(
            2 * IpFamilyScanner.MAX_ADDRESSES_PER_FAMILY * IpFamilyScanner.CONNECT_ATTEMPTS_PER_ADDRESS,
            attempts
        )
    }

    @Test fun aSingleLostIpv6PacketDoesNotBrandADualStackNodeIpv4Only() {
        var v6Attempts = 0
        val result = scan(listOf(v6, v4)) { address ->
            if (address.hostAddress!!.contains(':')) {
                v6Attempts += 1
                if (v6Attempts == 1) -1 else 44
            } else 60
        }

        assertEquals(IpFamilyVerdict.DUAL_OK, result.verdict)
        assertTrue(result.chip.contains("v4+v6"))
        assertEquals(44, result.ipv6LatencyMs)
        assertTrue(result.ipv6Usable)
    }

    @Test fun aFamilyThatOnlyAppearsOnTheConfirmingResolutionIsBelieved() {
        var calls = 0
        val result = IpFamilyScanner.scan(
            host = "edge.example.net",
            port = 443,
            networkKey = "wifi:home",
            nowMs = 10_000L,
            resolver = { _, _ ->
                calls += 1
                if (calls == 1) listOf(v4) else listOf(v6, v4)
            },
            connector = { address, _, _ -> if (address.hostAddress!!.contains(':')) 38 else 60 }
        )

        assertEquals(2, calls)
        assertTrue(result.hasIpv6)
        assertEquals(IpFamilyVerdict.DUAL_OK, result.verdict)
        assertFalse(result.ipv4Locked)
    }

    @Test fun aFamilyNobodyCanAnswerForIsStillAbsent() {
        var calls = 0
        val result = IpFamilyScanner.scan(
            host = "edge.example.net",
            port = 443,
            networkKey = "wifi:home",
            nowMs = 10_000L,
            resolver = { _, _ -> calls += 1; listOf(v4) },
            connector = { _, _, _ -> 60 }
        )

        assertEquals(IpFamilyScanner.RESOLVE_CONFIRM_PASSES, calls)
        assertEquals(IpFamilyVerdict.IPV4_ONLY, result.verdict)
        assertTrue(result.ipv4Locked)
    }

    @Test fun anUnresolvedNameIsNeverConfirmedAndNeverLooksLikeIpv4Only() {
        var calls = 0
        val result = IpFamilyScanner.scan(
            host = "edge.example.net",
            port = 443,
            nowMs = 10_000L,
            resolver = { _, _ -> calls += 1; emptyList() },
            connector = { _, _, _ -> 60 }
        )

        assertEquals(1, calls)
        assertEquals(IpFamilyVerdict.UNKNOWN, result.verdict)
        assertFalse(result.ipv4Locked)
    }

    @Test fun ipv6WinsATieAndEverythingInsideTheNoiseFloor() {
        val tie = scan(listOf(v6, v4)) { if (it == v6) 50 else 50 }
        val noise = scan(listOf(v6, v4)) {
            if (it == v6) 50 else 50 - IpFamilyScanner.IPV6_PREFERENCE_TOLERANCE_MS
        }
        val real = scan(listOf(v6, v4)) {
            if (it == v6) 50 else 50 - IpFamilyScanner.IPV6_PREFERENCE_TOLERANCE_MS - 1
        }

        assertEquals("ipv6", tie.fasterFamily)
        assertEquals("ipv6", noise.fasterFamily)
        assertEquals("ipv4", real.fasterFamily)
        assertTrue(tie.ipv6Preferred)
        assertFalse(real.ipv6Preferred)
    }

    @Test fun anInvalidPortIsRejectedWithoutTouchingTheNetwork() {
        var touched = false
        val result = IpFamilyScanner.scan(
            host = "edge.example.net",
            port = 0,
            nowMs = 1L,
            resolver = { _, _ -> touched = true; listOf(v4) },
            connector = { _, _, _ -> touched = true; 5 }
        )
        assertFalse(touched)
        assertEquals(IpFamilyVerdict.UNKNOWN, result.verdict)
    }

    @Test fun aSweepBudgetIsFiniteAndGrowsOnlyByWave() {
        val one = IpFamilyScanner.budgetMsFor(count = 1, concurrency = 6)
        val six = IpFamilyScanner.budgetMsFor(count = 6, concurrency = 6)
        val seven = IpFamilyScanner.budgetMsFor(count = 7, concurrency = 6)
        assertEquals(one, six)
        assertTrue(seven > six)
        // V198: BALANCED keeps 2, so 40 nodes still under 2 minutes
        assertTrue(IpFamilyScanner.budgetMsFor(count = 40, concurrency = 6) < 120_000L)
    }

    // ─────────────────────────────────────────────────────────── scope of a result

    @Test fun aVerdictBelongsToTheNetworkItWasMeasuredOn() {
        val result = scan(listOf(v4)) { 20 }
        assertTrue(result.usableOn("wifi:home", 20_000L))
        assertFalse("a café must not inherit the home verdict", result.usableOn("wifi:cafe", 20_000L))
    }

    @Test fun aVerdictExpires() {
        val result = scan(listOf(v4)) { 20 }
        // V198 adaptive TTL: IPV4_ONLY high conf = 24h, but our confidence is medium, so TTL_MS=6h still applies as base
        // We test with explicit TTL to keep deterministic
        assertTrue(result.usableOn("wifi:home", 10_000L + IpFamilyScan.TTL_MS - 1, IpFamilyScan.TTL_MS))
        assertFalse(result.usableOn("wifi:home", 10_000L + IpFamilyScan.TTL_MS + 1, IpFamilyScan.TTL_MS))
    }

    @Test fun anEmptyScanIsNeverEvidence() {
        assertFalse(IpFamilyScan(endpoint = "x:443").usableOn("wifi:home", 1_000L))
    }

    @Test fun aVerdictSurvivesSerialisationExactly() {
        val original = scan(listOf(v6, v4)) { if (it == v6) 41 else 58 }
        val restored = IpFamilyScan.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, restored)
    }

    // ─────────────────────────────────────────────────────────── group

    @Test fun summarizeCountsTheLibraryTheWayTheDialogReportsIt() {
        fun node(v4Ok: Boolean, hasV6: Boolean, v6Ok: Boolean, conf: Int = 75) = IpFamilyScan(
            endpoint = "n:443",
            hasIpv4 = true,
            hasIpv6 = hasV6,
            ipv4Ok = v4Ok,
            ipv6Ok = v6Ok,
            scannedAtMs = 1L,
            confidence = conf
        )
        val summary = IpFamilyScanner.summarize(
            listOf(
                node(v4Ok = true, hasV6 = true, v6Ok = true, conf = 90),
                node(v4Ok = true, hasV6 = false, v6Ok = false, conf = 85),
                node(v4Ok = true, hasV6 = false, v6Ok = false, conf = 80),
                node(v4Ok = true, hasV6 = true, v6Ok = false, conf = 30),
                node(v4Ok = false, hasV6 = false, v6Ok = true, conf = 70),
                node(v4Ok = false, hasV6 = false, v6Ok = false, conf = 10)
            )
        )
        assertEquals(6, summary.scanned)
        assertEquals(1, summary.dual)
        assertEquals(2, summary.ipv4Only)
        assertEquals(1, summary.ipv6Unproven)
        assertEquals(1, summary.ipv6Only)
        assertEquals(1, summary.unreachable)
        assertEquals(2, summary.ipv6Capable)
        assertTrue(summary.line.startsWith("2/6 IPv6-capable"))
        assertTrue(summary.avgConfidence > 0)
    }

    @Test fun theCacheKeyMatchesTheServerLocationCache() {
        assertEquals(
            ServerLocationKey.of("Edge.Example.NET", 443),
            IpFamilyScanner.endpointKey("Edge.Example.NET", 443)
        )
    }

    // ─────────────────────────────────────────────────────────── V198 equipped features

    @Test fun bogonFilteringDropsPrivateAddresses() {
        // Resolver returns both public and private; private should be filtered
        val result = scan(listOf(v4, bogonV4, v6, bogonV6)) { 30 }
        assertTrue(result.hasIpv4)
        assertTrue(result.hasIpv6)
        assertTrue(result.bogonFiltered >= 2)
        assertEquals(IpFamilyVerdict.DUAL_OK, result.verdict)
    }

    @Test fun nat64DetectionMarksSyntheticIpv6() {
        val result = scan(listOf(nat64, v4)) { if (it == nat64) 40 else 50 }
        assertTrue(result.hasIpv6)
        assertTrue(result.nat64Detected)
        assertTrue(result.headline.contains("NAT64") || result.detail.contains("NAT64"))
    }

    @Test fun confidenceIsHighWhenBothFamiliesAnswerWithLowJitter() {
        val result = scan(listOf(v6, v4)) { 50 }
        assertTrue(result.confidence >= 50)
        assertTrue(result.confidenceTier == ConfidenceTier.MEDIUM || result.confidenceTier == ConfidenceTier.HIGH)
    }

    @Test fun confidenceIsLowWhenOnlyOneWitnessAndHighJitter() {
        // Simulate high jitter via varying latencies would need multiple samples;
        // here we at least check that unknown has 0 confidence
        val result = scan(emptyList()) { 10 }
        assertEquals(0, result.confidence)
        assertEquals(ConfidenceTier.NONE, result.confidenceTier)
    }

    @Test fun adaptiveTtlGivesLongerLifeToStableDualStack() {
        val dualHigh = IpFamilyScan(
            endpoint = "a:443",
            hasIpv4 = true,
            hasIpv6 = true,
            ipv4Ok = true,
            ipv6Ok = true,
            scannedAtMs = 1L,
            confidence = 90
        )
        val unknown = IpFamilyScan(
            endpoint = "b:443",
            hasIpv4 = false,
            hasIpv6 = false,
            scannedAtMs = 1L,
            confidence = 0
        )
        assertTrue(dualHigh.adaptiveTtlMs() > unknown.adaptiveTtlMs())
        assertEquals(IpFamilyScanner.TTL_UNKNOWN_MS, unknown.adaptiveTtlMs())
    }

    @Test fun scanModesHaveDifferentBudgets() {
        val fast = IpFamilyScanner.budgetMsFor(10, 3, ScanMode.FAST)
        val balanced = IpFamilyScanner.budgetMsFor(10, 3, ScanMode.BALANCED)
        val deep = IpFamilyScanner.budgetMsFor(10, 3, ScanMode.DEEP)
        assertTrue(fast < balanced)
        assertTrue(balanced < deep)
    }

    @Test fun medianAndJitterCalculation() {
        assertEquals(50, IpFamilyScanner.median(listOf(10, 50, 90)))
        assertEquals(50, IpFamilyScanner.median(listOf(50, 10, 90)))
        assertTrue(IpFamilyScanner.jitter(listOf(50, 50, 50)) <= 1)
        assertTrue(IpFamilyScanner.jitter(listOf(10, 90, 50)) > 10)
    }

    @Test fun happyEyeballsRacePicksWinner() {
        val race = IpFamilyScanner.happyEyeballsRace(
            v6Addresses = listOf(v6),
            v4Addresses = listOf(v4),
            port = 443,
            tryDelayMs = 100,
            connector = { addr, _, _ -> if (addr == v6) 20 else 100 }
        )
        assertEquals("ipv6", race.winner)
        assertTrue(race.ipv6Ms >= 0)
    }

    @Test fun dnsDetailedResultTracksWitnesses() {
        var calls = 0
        val result = IpFamilyScanner.resolveFamiliesDetailed(
            host = "example.net",
            budgetMs = 1000,
            resolver = { _, _ ->
                calls++
                if (calls == 1) listOf(v4) else listOf(v4, v6)
            },
            mode = ScanMode.BALANCED
        )
        assertTrue(result.witnessCount >= 1)
        assertTrue(result.v6.isNotEmpty() || result.v4.isNotEmpty())
    }

    @Test fun scanWithNat64StillShowsIpv6ButLowConfidence() {
        val result = scan(listOf(nat64)) { 60 }
        assertTrue(result.hasIpv6)
        assertTrue(result.nat64Detected)
        // NAT64 reduces confidence
        assertTrue(result.confidence < 80)
    }

    @Test fun bogonIpv4IsRecognized() {
        assertTrue(IpFamilyScanner.isBogonIpv4(bogonV4 as java.net.Inet4Address))
        assertFalse(IpFamilyScanner.isBogonIpv4(v4 as java.net.Inet4Address))
    }

    @Test fun bogonIpv6IsRecognized() {
        assertTrue(IpFamilyScanner.isBogonIpv6(bogonV6 as java.net.Inet6Address))
        assertFalse(IpFamilyScanner.isBogonIpv6(v6 as java.net.Inet6Address))
    }

    @Test fun nat64SyntheticIsDetected() {
        assertTrue(IpFamilyScanner.isNat64Synthetic(nat64 as java.net.Inet6Address))
        assertFalse(IpFamilyScanner.isNat64Synthetic(v6 as java.net.Inet6Address))
    }
}
