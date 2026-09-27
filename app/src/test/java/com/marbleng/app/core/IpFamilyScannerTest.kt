package com.marbleng.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * MARBLE_IP_FAMILY_SCAN_V196 — the measurement behind the Servers ⋯ → "Scan IPv4 / IPv6" action.
 *
 * DNS and TCP arrive through function seams, so every branch of the verdict machine is pinned here
 * without a socket: the tests describe servers, not networks. The distinction the whole feature
 * rests on — "publishes an AAAA record" versus "answers over IPv6" — is asserted from both sides,
 * because collapsing the two is what let the app believe an IPv4-only library was IPv6-capable.
 */
class IpFamilyScannerTest {

    private val v4 = InetAddress.getByName("192.0.2.10")
    private val v4b = InetAddress.getByName("192.0.2.11")
    private val v6 = InetAddress.getByName("2001:db8::10")
    private val v6b = InetAddress.getByName("2001:db8::11")

    // `connects` is last so every case can read as `scan(answers) { address -> latency }`.
    private fun scan(
        answers: List<InetAddress>,
        host: String = "edge.example.net",
        port: Int = 443,
        underlayHasIpv6: Boolean = true,
        connects: (InetAddress) -> Int
    ): IpFamilyScan = IpFamilyScanner.scan(
        host = host,
        port = port,
        networkKey = "wifi:home",
        underlayHasIpv6 = underlayHasIpv6,
        nowMs = 10_000L,
        resolver = { _, _ -> answers },
        connector = { address, _, _ -> connects(address) }
    )

    // ─────────────────────────────────────────────────────────── the five verdicts

    @Test fun bothFamiliesAnsweringIsDualStack() {
        val result = scan(listOf(v6, v4)) { if (it == v6) 41 else 58 }

        assertEquals(IpFamilyVerdict.DUAL_OK, result.verdict)
        assertEquals("v4+v6", result.chip)
        assertEquals(41, result.ipv6LatencyMs)
        assertEquals(58, result.ipv4LatencyMs)
        assertTrue(result.ipv6Usable)
        // A tie or a win goes to IPv6: that is the product's whole thesis.
        assertEquals("ipv6", result.fasterFamily)
        assertTrue(result.advice.contains("Force IPv6 is safe"))
    }

    @Test fun noAaaaRecordAtAllIsIpv4Only() {
        val result = scan(listOf(v4)) { 60 }

        assertEquals(IpFamilyVerdict.IPV4_ONLY, result.verdict)
        assertEquals("v4", result.chip)
        assertTrue(result.ipv4Locked)
        assertFalse(result.hasIpv6)
        assertTrue(result.headline.contains("no IPv6 address"))
        // The advice must describe what the app does, not scold the user.
        assertTrue(result.advice.contains("falls back"))
    }

    @Test fun anAdvertisedIpv6ThatNeverAnswersIsUnprovenNotAbsent() {
        val result = scan(listOf(v6, v4)) { if (it == v6) -1 else 55 }

        assertEquals(IpFamilyVerdict.IPV6_UNPROVEN, result.verdict)
        assertTrue("the record exists and must be remembered", result.hasIpv6)
        assertFalse(result.ipv6Ok)
        // Not IPv4-only: this one can recover, and the two states have different remedies.
        assertFalse(result.ipv4Locked)
        assertEquals("", result.fasterFamily)
    }

    @Test fun onlyIpv6AnsweringIsIpv6Only() {
        val result = scan(listOf(v6, v4)) { if (it == v6) 33 else -1 }

        assertEquals(IpFamilyVerdict.IPV6_ONLY, result.verdict)
        assertEquals("v6", result.chip)
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
        scan(listOf(v6, v6b, InetAddress.getByName("2001:db8::12"), v4, v4b)) {
            attempts += 1
            -1
        }
        assertEquals(2 * IpFamilyScanner.MAX_ADDRESSES_PER_FAMILY, attempts)
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
        assertTrue(result.usableOn("wifi:home", 10_000L + IpFamilyScanner.TTL_MS - 1))
        assertFalse(result.usableOn("wifi:home", 10_000L + IpFamilyScanner.TTL_MS + 1))
    }

    @Test fun anEmptyScanIsNeverEvidence() {
        assertFalse(IpFamilyScan(endpoint = "x:443").usableOn("wifi:home", 1_000L))
    }

    @Test fun aVerdictSurvivesSerialisationExactly() {
        val original = scan(listOf(v6, v4)) { if (it == v6) 41 else 58 }
        val restored = IpFamilyScan.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, restored)
    }

    // ─────────────────────────────────────────────────────────── the group answer

    @Test fun summarizeCountsTheLibraryTheWayTheDialogReportsIt() {
        fun node(v4Ok: Boolean, hasV6: Boolean, v6Ok: Boolean) = IpFamilyScan(
            endpoint = "n:443",
            hasIpv4 = true,
            hasIpv6 = hasV6,
            ipv4Ok = v4Ok,
            ipv6Ok = v6Ok,
            scannedAtMs = 1L
        )
        val summary = IpFamilyScanner.summarize(
            listOf(
                node(v4Ok = true, hasV6 = true, v6Ok = true),
                node(v4Ok = true, hasV6 = false, v6Ok = false),
                node(v4Ok = true, hasV6 = false, v6Ok = false),
                node(v4Ok = true, hasV6 = true, v6Ok = false),
                node(v4Ok = false, hasV6 = false, v6Ok = true),
                node(v4Ok = false, hasV6 = false, v6Ok = false)
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
    }

    @Test fun theCacheKeyMatchesTheServerLocationCache() {
        assertEquals(
            ServerLocationKey.of("Edge.Example.NET", 443),
            IpFamilyScanner.endpointKey("Edge.Example.NET", 443)
        )
    }
}
