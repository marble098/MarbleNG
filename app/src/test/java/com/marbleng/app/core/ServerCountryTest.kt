package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the offline country resolver behind the Servers page (MARBLE_SERVERS_COUNTRY_V120).
 *
 * Subscriptions label nodes in every dialect imaginable, and the page has to answer one stable
 * question per node for its flag column, its "Country" sort and its "Group by country" switch.
 * These lock the readable cases in — and, just as importantly, the cases that must stay unknown
 * rather than invent a country.
 */
class ServerCountryTest {

    @Test
    fun leadingFlagEmojiWins() {
        val country = ServerCountry.of("\uD83C\uDDE9\uD83C\uDDEA Frankfurt 01")
        assertEquals("DE", country.code)
        assertEquals("Germany", country.name)
        assertTrue(country.isKnown)
    }

    @Test
    fun bracketedIsoCodeIsRead() {
        assertEquals("NL", ServerCountry.of("[NL] Amsterdam • Netflix").code)
        assertEquals("US", ServerCountry.of("(US) New York 04").code)
    }

    @Test
    fun bracketedTokenThatIsNotACountryStaysUnknown() {
        // "[443]" and "(id)" are common node-label decorations, not countries.
        assertFalse(ServerCountry.of("Node [443] premium").isKnown)
        assertFalse(ServerCountry.of("(id) relay").isKnown)
    }

    @Test
    fun leadingAndTrailingBareCodesAreRead() {
        assertEquals("DE", ServerCountry.of("DE Frankfurt 01").code)
        assertEquals("DE", ServerCountry.of("Frankfurt 01 DE").code)
    }

    @Test
    fun lowerCaseTwoLetterWordIsNotACountry() {
        // The edge-code pattern is deliberately case-sensitive: "No route" must never become
        // Norway, and "In touch" must never become India.
        assertFalse(ServerCountry.of("No route to host").isKnown)
        assertFalse(ServerCountry.of("In touch relay").isKnown)
    }

    @Test
    fun spelledOutCountryNamesAndAliasesResolve() {
        assertEquals("DE", ServerCountry.of("Germany · Frankfurt · 03").code)
        assertEquals("GB", ServerCountry.of("UK London 02").code)
        assertEquals("US", ServerCountry.of("USA - Dallas").code)
        assertEquals("TR", ServerCountry.of("Turkiye Istanbul").code)
    }

    @Test
    fun countryCodeTopLevelDomainIsTheLastSignal() {
        assertEquals("DE", ServerCountry.of("Node 12", "relay01.example.de").code)
        assertEquals("NL", ServerCountry.of("Node 12", "ams.example.nl").code)
    }

    @Test
    fun ipAddressHostIsNeverACountry() {
        assertFalse(ServerCountry.of("Node 12", "185.199.108.153").isKnown)
        assertFalse(ServerCountry.of("Node 12", "[2001:db8::1]").isKnown)
    }

    @Test
    fun unresolvableLabelIsHonestlyUnknown() {
        val country = ServerCountry.of("Mystery premium node")
        assertEquals(ServerCountry.UNKNOWN, country)
        assertFalse(country.isKnown)
        assertEquals("Unknown", country.name)
    }

    @Test
    fun flagIsBuiltFromRegionalIndicators() {
        assertEquals("\uD83C\uDDE9\uD83C\uDDEA", ServerCountry.flagFor("DE"))
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", ServerCountry.flagFor("us"))
        // Anything that is not two letters falls back to the neutral glyph.
        assertEquals(ServerCountry.UNKNOWN.flag, ServerCountry.flagFor("ZZZ"))
        assertEquals(ServerCountry.UNKNOWN.flag, ServerCountry.flagFor(""))
    }

    @Test
    fun unmappedCodeStillRendersAsItself() {
        assertEquals("XX", ServerCountry.nameFor("XX"))
        assertEquals("Germany", ServerCountry.nameFor("DE"))
    }

    @Test
    fun unknownCountriesSortLast() {
        val known = ServerCountry.of("DE node")
        val unknown = ServerCountry.of("Mystery node")
        assertTrue(known.sortKey < unknown.sortKey)
    }
}
