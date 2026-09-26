package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * MARBLE_SERVER_LOCATION_V192 — the once-per-server location test.
 *
 * Pins the contract the Home flags depend on: the cache key is the endpoint (stable across
 * renames), private addresses never cross the wire, a code is accepted only as clean ISO
 * alpha-2, and contradictory lookups resolve to "unknown" rather than a coin flip.
 */
class ServerLocationResolverV192Test {

    // ── cache key ────────────────────────────────────────────────────────────────────────

    @Test
    fun keyNormalisesHostAndPort() {
        assertEquals("node.example.com:443", ServerLocationKey.of("node.example.com", 443))
        assertEquals("node.example.com", ServerLocationKey.of("  NODE.example.com. ", 0))
        assertEquals("2001:db8::1:8443", ServerLocationKey.of("[2001:db8::1]", 8443))
        assertEquals("", ServerLocationKey.of("   ", 443))
        // A port outside the TCP range is not part of the identity.
        assertEquals("node.example.com", ServerLocationKey.of("node.example.com", 70000))
    }

    // ── private-address gate ─────────────────────────────────────────────────────────────

    @Test
    fun publicIpv4IsPublic() {
        for (ip in listOf("1.2.3.4", "8.8.8.8", "172.32.0.1", "100.63.0.1", "100.128.0.1", "192.0.3.1")) {
            assertTrue("$ip must be public", ServerLocationResolver.isPublicAddress(ip))
        }
    }

    @Test
    fun privateIpv4IsNeverPublic() {
        for (ip in listOf(
            "0.0.0.1", "10.1.2.3", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "172.31.255.254", "192.168.1.1",
            "100.64.0.1", "100.127.255.254", "192.0.0.1", "192.0.2.53"
        )) {
            assertFalse("$ip must stay private", ServerLocationResolver.isPublicAddress(ip))
        }
    }

    @Test
    fun ipv6RangesAreJudged() {
        assertTrue(ServerLocationResolver.isPublicAddress("2001:db8::1"))
        assertTrue(ServerLocationResolver.isPublicAddress("2400:3200::1"))
        assertFalse(ServerLocationResolver.isPublicAddress("::1"))
        assertFalse(ServerLocationResolver.isPublicAddress("fe80::1"))
        assertFalse(ServerLocationResolver.isPublicAddress("fc00::1"))
        assertFalse(ServerLocationResolver.isPublicAddress("fd12:3456::7"))
        // Malformed literals are not public either — they are not addresses at all.
        assertFalse(ServerLocationResolver.isPublicAddress("1:2:3:4:5:6:7:8:9"))
        assertFalse(ServerLocationResolver.isPublicAddress("::"))
    }

    @Test
    fun mappedV4IsJudgedByItsV4() {
        assertFalse(ServerLocationResolver.isPublicAddress("::ffff:127.0.0.1"))
        assertFalse(ServerLocationResolver.isPublicAddress("::ffff:10.1.2.3"))
        assertTrue(ServerLocationResolver.isPublicAddress("::ffff:8.8.8.8"))
    }

    @Test
    fun publicAddressPrefersThePublicAnswerFromDns() {
        val dns = LocationDnsSource {
            listOf("192.168.1.50", "1.2.3.4")
        }
        assertEquals("1.2.3.4", ServerLocationResolver.publicAddressOf("node.example.com", dns))
    }

    @Test
    fun privateOnlyAnswersYieldNothing() {
        val dns = LocationDnsSource { listOf("10.0.0.4", "192.168.7.7") }
        assertNull(ServerLocationResolver.publicAddressOf("node.example.com", dns))
        // A literal IP host is judged directly, no DNS at all.
        assertNull(ServerLocationResolver.publicAddressOf("172.20.1.1"))
        assertEquals("1.2.3.4", ServerLocationResolver.publicAddressOf("1.2.3.4"))
    }

    // ── lookup parsing ───────────────────────────────────────────────────────────────────

    @Test
    fun parseLookupReadsAllThreeEndpointShapes() {
        // ipwho.is style: code first, name as fallback.
        assertEquals("TR", ServerLocationResolver.parseLookup("""{"country_code":"tr","country":"Turkey"}""").country)
        // api.country.is style: lowercase code.
        assertEquals("DE", ServerLocationResolver.parseLookup("""{"country_code":"de","country":"Germany"}""").country)
        // name-only answers map back through the reverse table.
        assertEquals("NL", ServerLocationResolver.parseLookup("""{"country":"Netherlands"}""").country)
        assertEquals("US", ServerLocationResolver.parseLookup("""{"country":"USA"}""").country)
    }

    @Test
    fun parseLookupRejectsAnythingNotACleanAlpha2() {
        assertEquals("", ServerLocationResolver.parseLookup("not json at all").country)
        assertEquals("", ServerLocationResolver.parseLookup("""{"country_code":""}""").country)
        assertEquals("", ServerLocationResolver.parseLookup("""{"country_code":"TUK"}""").country)
        assertEquals("", ServerLocationResolver.parseLookup("""{"error":"blocked"}""").country)
    }

    // ── consensus ───────────────────────────────────────────────────────────────────────

    @Test
    fun voteMajorityAndSingles() {
        val tr = GeoObservation("TR")
        val de = GeoObservation("DE")
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(tr, tr)))
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(tr, de, tr)))
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(GeoObservation("tr"), tr)))
        // A single usable observation is still an answer.
        assertEquals("TR", ServerLocationResolver.voteCountry(listOf(GeoObservation(""), tr)))
        // A tie is a coin flip — the product refuses to flip.
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, de)))
        assertEquals("", ServerLocationResolver.voteCountry(listOf(tr, de, GeoObservation("FR"))))
        assertEquals("", ServerLocationResolver.voteCountry(emptyList()))
    }

    // ── end to end, off-device ───────────────────────────────────────────────────────────

    @Test
    fun resolveCountryAgreesAcrossLookups() {
        val dns = LocationDnsSource { listOf("1.2.3.4") }
        val http = LocationHttpSource { url ->
            when {
                url.contains("ipwho.is") -> """{"country_code":"TR"}"""
                url.contains("country.is") -> """{"country":"Turkey"}"""
                else -> null
            }
        }
        assertEquals("TR", ServerLocationResolver.resolveCountry("node.example.com", 443, dns, http))
    }

    @Test
    fun resolveCountryStaysSilentWhenNothingAgrees() {
        val dns = LocationDnsSource { listOf("1.2.3.4") }
        val http = LocationHttpSource { url ->
            when {
                url.contains("ipwho.is") -> """{"country_code":"TR"}"""
                url.contains("country.is") -> """{"country_code":"DE"}"""
                else -> null
            }
        }
        assertEquals("", ServerLocationResolver.resolveCountry("node.example.com", 443, dns, http))
        // A node that only resolves privately is never tested at all.
        val privateDns = LocationDnsSource { listOf("192.168.0.9") }
        val countingHttp = LocationHttpSource { fail("private address crossed the wire") }
        assertEquals("", ServerLocationResolver.resolveCountry("lan.example.com", 443, privateDns, countingHttp))
    }
}
