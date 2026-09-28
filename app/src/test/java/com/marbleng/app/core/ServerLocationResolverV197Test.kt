package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVER_LOCATION_V197 — the difference between "this server is in Germany" and "no flag".
 *
 * The V192 contract was honest and unusable: two independent services had to agree, and the app
 * asked three. On a network where two of the three are unreachable — which is the normal case for
 * free, keyless geolocation endpoints in a filtered environment — the rule could never be
 * satisfied, so the app measured a server's location successfully and then drew an empty circle.
 *
 * Silence is not disagreement. These tests pin the two halves of that distinction: a quorum still
 * wins outright, a genuine disagreement still means "unknown", and a single uncontradicted answer
 * from an otherwise unreachable pool is now accepted — and marked provisional so it is re-tested.
 */
class ServerLocationResolverV197Test {

    private val tr = GeoObservation("TR", "1.2.3.4")
    private val de = GeoObservation("DE", "1.2.3.4")
    private val silent = GeoObservation("", "1.2.3.4")

    // ─────────────────────────────────────────────── the quorum rule is unchanged

    @Test fun aQuorumStillWinsOutright() {
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(tr, tr), 5))
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(tr, de, tr), 5))
        assertEquals("DE", ServerLocationResolver.voteCountry(listOf(de, de, tr), 5))
    }

    @Test fun aGenuineDisagreementIsStillUnknown() {
        // Two services that name different countries prove the address is contested. That is worse
        // evidence than one service answering, and it must never become a flag.
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, de), 5))
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, de, GeoObservation("FR")), 5))
    }

    @Test fun oneSilentProviderIsNotEnoughToTrustTheOther() {
        // The V192 rule, preserved: one answer plus one silence is not a quorum.
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, silent), 2))
        assertEquals("", ServerLocationResolver.voteCountry(listOf(silent, tr), 2))
    }

    // ─────────────────────────────────────────────── the case that used to be lost

    @Test fun oneAnswerWhileTheRestOfThePoolIsUnreachableIsAccepted() {
        // Five providers asked, four could not be reached at all, one named Turkey and nobody
        // contradicted it. Nothing here is a fact about the network; it is a fact about the address.
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

    // ─────────────────────────────────────────────── the provider set itself

    @Test fun thePoolIsWideEnoughForAQuorumToBeReachable() {
        // Three keyless providers was a coin flip once one of them was filtered. Five means two
        // independent agreements survive the loss of any two.
        assertTrue(ServerLocationResolver.PROVIDER_COUNT >= 4)
        assertEquals(2, ServerLocationResolver.VOTE_QUORUM)
        assertTrue(
            "a quorum must still be reachable after losing most of the pool",
            ServerLocationResolver.PROVIDER_COUNT - ServerLocationResolver.VOTE_QUORUM >= 2
        )
    }

    @Test fun theNewProviderShapesParseIntoTheSameObservation() {
        // freeipapi answers with countryCode, ip.sb with country_code, country.is with a name.
        // A single upstream changing its schema must not empty the vote.
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
