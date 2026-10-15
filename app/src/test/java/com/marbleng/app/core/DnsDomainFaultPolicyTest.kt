package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsDomainFaultPolicyTest {
    private val cloudflare =
        "app/dns: failed to lookup ip for domain noveo.ir at DOH//1.0.0.1 > context deadline exceeded"
    private val quad9 =
        "app/dns: failed to lookup ip for domain noveo.ir at DOH//149.112.112.112 > context deadline exceeded"
    private val sameEndpointOtherName =
        "app/dns: failed to lookup ip for domain example.com at DOH//1.0.0.1 > context deadline exceeded"

    @Test fun theQueriedNameIsReadFromBothCores() {
        assertEquals("noveo.ir", DnsDomainFaultPolicy.domainOf(cloudflare))
        assertEquals(
            "noveo.ir",
            DnsDomainFaultPolicy.domainOf("dns: exchange failed for noveo.ir IN A: i/o timeout")
        )
        assertEquals(
            "shop.example.co.uk",
            DnsDomainFaultPolicy.domainOf("resolve error domain=shop.example.co.uk rcode=2")
        )
    }

    @Test fun anAddressIsNotAName() {
        assertNull(DnsDomainFaultPolicy.domainOf("failed for domain 1.0.0.1 at DOH//8.8.8.8"))
        assertNull(DnsDomainFaultPolicy.domainOf("exchange failed for 2001:db8::1 IN AAAA"))
        assertFalse(DnsDomainFaultPolicy.isPlausibleDomain("localhost"))
        assertFalse(DnsDomainFaultPolicy.isPlausibleDomain("trailing."))
        assertFalse(DnsDomainFaultPolicy.isPlausibleDomain("example.123"))
        assertTrue(DnsDomainFaultPolicy.isPlausibleDomain("noveo.ir"))
    }

    @Test fun aLineWithNoNameIsIgnoredEntirely() {
        assertNull(DnsDomainFaultPolicy.domainOf("app/dns: DOH//1.0.0.1 context deadline exceeded"))
        assertNull(DnsDomainFaultPolicy.domainOf(""))
    }

    @Test fun oneEndpointFailingOneNameIsNotYetADomainFault() {
        val faults = DnsDomainFaultPolicy.observe(sequenceOf(cloudflare), emptyList(), 1_000L)
        assertEquals(1, faults.size)
        assertFalse(faults.single().decisive)
        assertFalse(DnsDomainFaultPolicy.isDomainFaultLine(cloudflare, faults, 1_000L))
    }

    @Test fun twoIndependentEndpointsFailingTheSameNameIsADomainFault() {
        val faults = DnsDomainFaultPolicy.observe(sequenceOf(cloudflare, quad9), emptyList(), 1_000L)
        val fault = faults.single()

        assertEquals("noveo.ir", fault.domain)
        assertEquals(2, fault.endpoints.size)
        assertEquals(2, fault.failures)
        assertTrue(fault.decisive)

        assertTrue(DnsDomainFaultPolicy.isDomainFaultLine(cloudflare, faults, 1_000L))
        assertTrue(DnsDomainFaultPolicy.isDomainFaultLine(quad9, faults, 1_000L))
    }

    @Test fun oneEndpointFailingManyNamesStaysEndpointEvidence() {
        val lines = sequenceOf(
            sameEndpointOtherName,
            "app/dns: failed to lookup ip for domain a.example.net at DOH//1.0.0.1 > context deadline exceeded",
            "app/dns: failed to lookup ip for domain b.example.net at DOH//1.0.0.1 > context deadline exceeded"
        )
        val faults = DnsDomainFaultPolicy.observe(lines, emptyList(), 1_000L)
        assertEquals(3, faults.size)
        assertTrue("a single failing resolver must still be demotable", faults.none { it.decisive })
        assertFalse(DnsDomainFaultPolicy.isDomainFaultLine(sameEndpointOtherName, faults, 1_000L))
    }

    @Test fun theSameEndpointTwiceIsStillOneEndpoint() {
        val faults = DnsDomainFaultPolicy.observe(
            sequenceOf(cloudflare, cloudflare, cloudflare),
            emptyList(),
            1_000L
        )
        val fault = faults.single()
        assertEquals(1, fault.endpoints.size)
        assertEquals(3, fault.failures)
        assertFalse("repetition is not independence", fault.decisive)
    }

    @Test fun teardownArtefactsNeverBlameANameEither() {
        val lines = sequenceOf(
            "app/dns: failed to lookup ip for domain noveo.ir at DOH//1.0.0.1 > context canceled",
            "app/dns: failed to lookup ip for domain noveo.ir at DOH//9.9.9.9 > use of closed network connection"
        )
        assertTrue(DnsDomainFaultPolicy.observe(lines, emptyList(), 1_000L).isEmpty())
    }

    @Test fun evidenceAgesOutOfTheWindow() {
        val first = DnsDomainFaultPolicy.observe(sequenceOf(cloudflare, quad9), emptyList(), 1_000L)
        assertTrue(first.single().decisive)

        val later = 1_000L + DnsDomainFaultPolicy.WINDOW_MS + 1
        assertFalse(first.single().fresh(later))
        assertTrue(DnsDomainFaultPolicy.faulted(first, later).isEmpty())

        assertTrue(DnsDomainFaultPolicy.observe(emptySequence(), first, later).isEmpty())
    }

    @Test fun observationsAccumulateAcrossCalls() {
        val first = DnsDomainFaultPolicy.observe(sequenceOf(cloudflare), emptyList(), 1_000L)
        val second = DnsDomainFaultPolicy.observe(sequenceOf(quad9), first, 2_000L)
        assertTrue(second.single().decisive)
        assertEquals(2, second.single().failures)
    }

    @Test fun theTableIsBounded() {
        val lines = (1..DnsDomainFaultPolicy.MAX_DOMAINS * 2).asSequence().map { index ->
            "app/dns: failed to lookup ip for domain site$index.example at DOH//1.0.0.1 > context deadline exceeded"
        }
        assertEquals(
            DnsDomainFaultPolicy.MAX_DOMAINS,
            DnsDomainFaultPolicy.observe(lines, emptyList(), 1_000L).size
        )
    }

    @Test fun theCoreClientTokenIsTheResolverIdentityThatMatters() {
        assertEquals("doh//1.0.0.1", DnsDomainFaultPolicy.endpointOf(cloudflare))
        assertEquals("doh//149.112.112.112", DnsDomainFaultPolicy.endpointOf(quad9))
        assertEquals(
            "dohl//9.9.9.9",
            DnsDomainFaultPolicy.endpointOf(
                "app/dns: failed to lookup ip for domain noveo.ir at DOHL//9.9.9.9 > context deadline exceeded"
            )
        )
        assertEquals(
            "udp//8.8.8.8:53",
            DnsDomainFaultPolicy.endpointOf(
                "app/dns: failed to lookup ip for domain noveo.ir at UDP//8.8.8.8:53 > i/o timeout"
            )
        )

        assertEquals(
            "https://1.0.0.1/dns-query",
            DnsDomainFaultPolicy.endpointOf(
                """dns: lookup for domain noveo.ir via "https://1.0.0.1/dns-query" failed: context deadline exceeded"""
            )
        )
        assertEquals(
            DnsDomainFaultPolicy.UNATTRIBUTED,
            DnsDomainFaultPolicy.endpointOf(
                "app/dns: failed to lookup ip for domain noveo.ir > context deadline exceeded"
            )
        )
    }

    @Test fun anonymousFailuresNeverCorroborateEachOther() {
        val anonymous = "app/dns: failed to lookup ip for domain noveo.ir > context deadline exceeded"
        val faults = DnsDomainFaultPolicy.observe(sequenceOf(anonymous, anonymous, anonymous), emptyList(), 1_000L)
        val fault = faults.single()
        assertEquals(3, fault.failures)
        assertEquals(0, fault.namedEndpoints)
        assertFalse(fault.decisive)
        assertFalse(DnsDomainFaultPolicy.isFaulted("noveo.ir", faults, 1_000L))
    }

    @Test fun theSummaryNamesTheSiteNotThePool() {
        val faults = DnsDomainFaultPolicy.observe(sequenceOf(cloudflare, quad9), emptyList(), 1_000L)
        val summary = DnsDomainFaultPolicy.summary(faults, 1_000L)
        assertEquals("noveo.ir (2 resolvers, 2 failures)", summary)
        assertEquals("", DnsDomainFaultPolicy.summary(emptyList(), 1_000L))
    }

    @Test fun theTableSurvivesSerialisation() {
        val faults = DnsDomainFaultPolicy.observe(sequenceOf(cloudflare, quad9), emptyList(), 1_000L)
        val restored = DnsDomainFaultPolicy.deserialize(DnsDomainFaultPolicy.serialize(faults))
        assertEquals(faults, restored)
        assertTrue(DnsDomainFaultPolicy.deserialize("not json").isEmpty())
        assertTrue(DnsDomainFaultPolicy.deserialize("").isEmpty())
    }
}
