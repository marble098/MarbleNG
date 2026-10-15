package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv6FallbackLadderTest {
    private val v6Underlay = FamilyEvidence(underlayHasIpv6 = true)
    private val v4Underlay = FamilyEvidence(underlayHasIpv6 = false)

    @Test fun forceIpv6OnAnIpv4OnlyNodeDialsIpv4InsteadOfRefusing() {
        val evidence = v6Underlay.copy(nodeHasIpv6 = false, nodeHasIpv4 = true)
        val resolved = Ipv6FallbackLadder.resolve(AddressFamilyMode.FORCE_IPV6, evidence)

        assertEquals(FamilyRung.IPV4_FIRST, resolved.rung)
        assertEquals(AddressFamilyMode.PREFER_IPV6, resolved.effective)
        assertEquals("node-v4-only", resolved.code)
        assertNull("an IPv4-only node must never be refused by default", resolved.refusal)
        assertTrue(resolved.degraded)
        assertFalse(resolved.blocked)
    }

    @Test fun forceIpv6OnANetworkWithoutIpv6KeepsTheSessionAlive() {
        val evidence = v4Underlay.copy(nodeHasIpv6 = true, nodeHasIpv4 = true)
        val resolved = Ipv6FallbackLadder.resolve(AddressFamilyMode.FORCE_IPV6, evidence)

        assertEquals(FamilyRung.IPV4_FIRST, resolved.rung)
        assertEquals("underlay-v4-only", resolved.code)
        assertNull(resolved.refusal)

        assertTrue(resolved.advice.contains("returns by itself"))
    }

    @Test fun everyIpv4OnlyNodeOfTheReportedLibraryConnects() {
        val nodes = listOf(
            "185.99.133.12", "45.147.51.7", "91.107.180.4", "213.183.58.9",
            "de4.example.net", "tr8.example.net", "mci.example.ir", "nl3.example.net"
        )
        for (host in nodes) {
            val scan = IpFamilyScan(
                endpoint = "$host:443",
                hasIpv4 = true,
                ipv4Ok = true,
                scannedAtMs = 1_000L
            )
            val resolved = Ipv6FallbackLadder.resolve(
                requested = AddressFamilyMode.FORCE_IPV6,
                evidence = Ipv6FallbackLadder.evidenceFor(host, scan, underlayHasIpv6 = false)
            )
            assertNull("$host must not be refused", resolved.refusal)
            assertEquals(host, FamilyRung.IPV4_FIRST, resolved.rung)
        }
    }

    @Test fun provenIpv6KeepsForceIpv6Exactly() {
        val evidence = v6Underlay.copy(
            nodeHasIpv6 = true,
            nodeIpv6Proven = true,
            nodeHasIpv4 = true
        )
        val resolved = Ipv6FallbackLadder.resolve(AddressFamilyMode.FORCE_IPV6, evidence)

        assertEquals(FamilyRung.IPV6_STRICT, resolved.rung)
        assertEquals(AddressFamilyMode.FORCE_IPV6, resolved.effective)
        assertEquals("v6-proven", resolved.code)
        assertFalse(resolved.degraded)
    }

    @Test fun anUnmeasuredNodeIsGivenTheBenefitOfTheDoubtOnce() {
        val resolved = Ipv6FallbackLadder.resolve(AddressFamilyMode.FORCE_IPV6, v6Underlay)
        assertEquals(FamilyRung.IPV6_STRICT, resolved.rung)
        assertEquals("v6-available", resolved.code)
    }

    @Test fun anAdvertisedButUnprovenIpv6NodeKeepsIpv4Armed() {
        val evidence = v6Underlay.copy(
            nodeHasIpv6 = true,
            nodeIpv6Proven = false,
            nodeHasIpv4 = true
        )
        val resolved = Ipv6FallbackLadder.resolve(AddressFamilyMode.FORCE_IPV6, evidence)

        assertEquals(FamilyRung.IPV6_FIRST, resolved.rung)
        assertEquals(AddressFamilyMode.PREFER_IPV6, resolved.effective)
        assertNull(resolved.refusal)
    }

    @Test fun strictEnforcementStillRefusesAndNamesTheAlternative() {
        val evidence = v6Underlay.copy(nodeHasIpv6 = false, nodeHasIpv4 = true)
        val resolved = Ipv6FallbackLadder.resolve(
            requested = AddressFamilyMode.FORCE_IPV6,
            evidence = evidence,
            strict = true
        )

        assertEquals(FamilyRung.REFUSED, resolved.rung)
        assertEquals(Ipv6FallbackLadder.STRICT_NODE_REFUSAL, resolved.refusal)
        assertTrue(resolved.blocked)

        val refusal = resolved.refusal.orEmpty()
        assertTrue(refusal.contains("strict"))
        assertTrue(refusal.contains("IPv4"))
    }

    @Test fun anIpv6LiteralWithoutAnIpv6NetworkIsTheOneGenuineDeadEnd() {
        val resolved = Ipv6FallbackLadder.resolve(
            requested = AddressFamilyMode.FORCE_IPV6,
            evidence = Ipv6FallbackLadder.evidenceFor(
                host = "[2a01:4f8:1:2::9]",
                scan = null,
                underlayHasIpv6 = false
            )
        )
        assertEquals(FamilyRung.REFUSED, resolved.rung)
        assertEquals(Ipv6FallbackLadder.IPV6_LITERAL_NO_UNDERLAY, resolved.refusal)
    }

    @Test fun forceIpv4MeetingAnIpv6OnlyNodeIsTheMirrorImage() {
        val onV6Network = Ipv6FallbackLadder.resolve(
            requested = AddressFamilyMode.FORCE_IPV4,
            evidence = FamilyEvidence(underlayHasIpv6 = true, nodeIsIpv6Literal = true)
        )
        assertEquals(FamilyRung.IPV4_FIRST, onV6Network.rung)
        assertEquals(AddressFamilyMode.PREFER_IPV4, onV6Network.effective)
        assertNull(onV6Network.refusal)

        val onV4Network = Ipv6FallbackLadder.resolve(
            requested = AddressFamilyMode.FORCE_IPV4,
            evidence = FamilyEvidence(underlayHasIpv6 = false, nodeIsIpv6Literal = true)
        )
        assertEquals(FamilyRung.REFUSED, onV4Network.rung)
        assertNotNull(onV4Network.refusal)
    }

    @Test fun nonForcedModesAreNeverRewritten() {
        for (mode in listOf(AddressFamilyMode.SMART, AddressFamilyMode.PREFER_IPV4)) {
            val resolved = Ipv6FallbackLadder.resolve(mode, v4Underlay)
            assertEquals(mode, resolved.effective)
            assertEquals(FamilyRung.AS_REQUESTED, resolved.rung)
            assertNull(resolved.refusal)
        }

        val preferOnV4 = Ipv6FallbackLadder.resolve(
            AddressFamilyMode.PREFER_IPV6,
            v4Underlay.copy(nodeHasIpv6 = false)
        )
        assertEquals(AddressFamilyMode.PREFER_IPV6, preferOnV4.effective)
        assertEquals(FamilyRung.IPV4_FIRST, preferOnV4.rung)
    }

    @Test fun applyRewritesEveryLegacyProjectionTogether() {
        val requested = AppSettings(
            addressFamilyMode = AddressFamilyMode.FORCE_IPV6,
            ipv6Enabled = true,
            preferIpv6 = true,
            dnsQueryStrategy = "UseIPv6"
        )
        val resolved = Ipv6FallbackLadder.resolve(
            AddressFamilyMode.FORCE_IPV6,
            v6Underlay.copy(nodeHasIpv6 = false, nodeHasIpv4 = true)
        )
        val effective = Ipv6FallbackLadder.apply(requested, resolved)

        assertEquals(AddressFamilyMode.PREFER_IPV6, effective.addressFamilyMode)

        assertTrue(effective.ipv6Enabled)
        assertTrue(effective.preferIpv6)
        assertEquals("UseIP", effective.dnsQueryStrategy)

        assertFalse(AddressFamilyPolicy.excludedIpv4Endpoint("192.0.2.1", effective))
        assertEquals(
            IpFamilyPreference.IPV6_ONLY,
            AddressFamilyPolicy.preference(requested, underlayHasIpv6 = true)
        )
        assertTrue(
            AddressFamilyPolicy.preference(effective, underlayHasIpv6 = true) !=
                IpFamilyPreference.IPV6_ONLY
        )
    }

    @Test fun applyIsIdempotentAndUntouchedWhenNothingChanged() {
        val settings = AppSettings(addressFamilyMode = AddressFamilyMode.FORCE_IPV6)
        val strictRung = Ipv6FallbackLadder.resolve(AddressFamilyMode.FORCE_IPV6, v6Underlay)
        assertEquals(settings, Ipv6FallbackLadder.apply(settings, strictRung))

        val degradeRung = Ipv6FallbackLadder.resolve(
            AddressFamilyMode.FORCE_IPV6,
            v4Underlay.copy(nodeHasIpv4 = true)
        )
        val once = Ipv6FallbackLadder.apply(settings, degradeRung)
        assertEquals(once, Ipv6FallbackLadder.apply(once, degradeRung))
    }

    @Test fun literalsAreDecidedLocallyAndNeverNeedAScan() {
        val v4 = Ipv6FallbackLadder.evidenceFor("185.99.133.12", null, underlayHasIpv6 = true)
        assertTrue(v4.nodeIsIpv4Literal)
        assertTrue(v4.ipv6Impossible)
        assertFalse(v4.ipv4Impossible)

        val v6 = Ipv6FallbackLadder.evidenceFor("[2a01::1]", null, underlayHasIpv6 = true)
        assertTrue(v6.nodeIsIpv6Literal)
        assertTrue(v6.ipv4Impossible)
        assertFalse(v6.ipv6Impossible)
    }

    @Test fun anUnmeasuredHostnameCarriesNoNegativeEvidence() {
        val evidence = Ipv6FallbackLadder.evidenceFor("de2.example.net", null, true)
        assertNull("never measured must not read as 'absent'", evidence.nodeHasIpv6)
        assertNull(evidence.nodeIpv6Proven)
        assertFalse(evidence.ipv6Impossible)
        assertFalse(evidence.ipv4Impossible)
    }

    @Test fun aScanThatSawNoAaaaIsPositiveEvidenceOfAbsence() {
        val scan = IpFamilyScan(
            endpoint = "de2.example.net:443",
            hasIpv4 = true,
            ipv4Ok = true,
            hasIpv6 = false,
            scannedAtMs = 5_000L
        )
        val evidence = Ipv6FallbackLadder.evidenceFor("de2.example.net", scan, true)
        assertEquals(false, evidence.nodeHasIpv6)
        assertTrue(evidence.ipv6Impossible)

        assertNull(evidence.nodeIpv6Proven)
    }
}
