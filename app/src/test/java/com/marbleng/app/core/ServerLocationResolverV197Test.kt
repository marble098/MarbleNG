package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerLocationResolverV197Test {
    private val tr = GeoObservation("TR", "1.2.3.4")
    private val de = GeoObservation("DE", "1.2.3.4")
    private val silent = GeoObservation("", "1.2.3.4")

    @Test fun aQuorumStillWinsOutright() {
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(tr, tr), 5))
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(tr, de, tr), 5))
        assertEquals("DE", ServerLocationResolver.voteCountry(listOf(de, de, tr), 5))
    }

    @Test fun aGenuineDisagreementIsStillUnknown() {
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, de), 5))
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, de, GeoObservation("FR")), 5))
    }

    @Test fun oneSilentProviderIsNotEnoughToTrustTheOther() {
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, silent), 2))
        assertEquals("", ServerLocationResolver.voteCountry(listOf(silent, tr), 2))
    }

    @Test fun oneAnswerWhileTheRestOfThePoolIsUnreachableIsAccepted() {
        val observations = listOf(tr, silent, silent, silent, silent)

        assertEquals("TR", ServerLocationResolver.voteCountry(observations, 5))
    }

    @Test fun aLoneAnswerIsMarkedProvisionalSoItIsTestedAgain() {
        val dns = LocationDnsSource { listOf("1.2.3.4") }
        val http = LocationHttpSource { url ->
            if (url.contains("ipwho.is")) """{"country_code":"TR"}""" else null
        }

        val verdict = ServerLocationResolver.resolveCountryDetailed(
            host = "node.example.com",
            port = 443,
            dns = dns,
            http = http
        )

        assertEquals("TR", verdict.code)
        assertTrue(verdict.isKnown)
        assertTrue("a single witness must be re-tested later", verdict.provisional)
        assertEquals(LocationConfidence.LONE, verdict.confidence)
        assertEquals(1, verdict.observers)
        assertEquals(1, verdict.witnesses)
        assertEquals("1.2.3.4", verdict.address)
        assertTrue(verdict.evidence.contains("lone"))
    }

    @Test fun twoAgreingProvidersAreNotProvisional() {
        val dns = LocationDnsSource { listOf("1.2.3.4") }
        val http = LocationHttpSource { url ->
            when {
                url.contains("ipwho.is") -> """{"country_code":"DE"}"""
                url.contains("country.is") -> """{"country":"Germany"}"""
                else -> null
            }
        }

        val verdict = ServerLocationResolver.resolveCountryDetailed(
            host = "node.example.com",
            port = 443,
            dns = dns,
            http = http
        )

        assertEquals("DE", verdict.code)
        assertEquals(LocationConfidence.QUORUM, verdict.confidence)
        assertFalse(verdict.provisional)
        assertEquals(2, verdict.witnesses)
        assertTrue(verdict.evidence.contains("quorum"))
    }

    @Test fun aContradictedAddressProducesNoFlagAndNoFalseConfidence() {
        val dns = LocationDnsSource { listOf("1.2.3.4") }
        val http = LocationHttpSource { url ->
            when {
                url.contains("ipwho.is") -> """{"country_code":"TR"}"""
                url.contains("country.is") -> """{"country_code":"DE"}"""
                else -> null
            }
        }

        val verdict = ServerLocationResolver.resolveCountryDetailed(
            host = "node.example.com",
            port = 443,
            dns = dns,
            http = http
        )

        assertEquals("", verdict.code)
        assertFalse(verdict.isKnown)
        assertFalse(verdict.provisional)
        assertEquals(LocationConfidence.NONE, verdict.confidence)
        assertEquals(2, verdict.observers)
    }

    @Test fun thePoolIsWideEnoughForAQuorumToBeReachable() {
        assertTrue(ServerLocationResolver.PROVIDER_COUNT >= 4)
        assertEquals(2, ServerLocationResolver.VOTE_QUORUM)
        assertTrue(
            "a quorum must still be reachable after losing most of the pool",
            ServerLocationResolver.PROVIDER_COUNT - ServerLocationResolver.VOTE_QUORUM >= 2
        )
    }

    @Test fun theNewProviderShapesParseIntoTheSameObservation() {
        assertEquals(
            "NL",
            ServerLocationResolver.parseLookup("""{"countryCode":"NL"}""").country
        )
        assertEquals(
            "NL",
            ServerLocationResolver.parseLookup("""{"country_code":"nl"}""").country
        )
        assertEquals(
            "NL",
            ServerLocationResolver.parseLookup("""{"country":"Netherlands"}""").country
        )
    }

    @Test fun anUnresolvableAddressIsUnknownAndCostsNothing() {
        val dns = LocationDnsSource { emptyList() }
        var asked = 0
        val http = LocationHttpSource { asked += 1; null }

        val verdict = ServerLocationResolver.resolveCountryDetailed(
            host = "node.example.com",
            port = 443,
            dns = dns,
            http = http
        )

        assertEquals("", verdict.code)
        assertEquals(0, asked)
        assertEquals("", ServerLocationResolver.resolveCountry("node.example.com", 443, dns, http))
    }
}
