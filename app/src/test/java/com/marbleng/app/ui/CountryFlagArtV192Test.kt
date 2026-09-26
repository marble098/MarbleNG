package com.marbleng.app.ui

import com.marbleng.app.core.ServerCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SERVER_LOCATION_V192 — the circle flags that fill the server rows.
 *
 * The rendering itself needs a canvas; what these tests pin is the contract the rows depend on:
 * every country the product can name (the ServerCountry table, the only source of codes the
 * rest of the app sees) is one the art library can actually draw. A code that falls through
 * to the neutral tile would read as "unknown location" next to a name that names its country —
 * the two tables must never disagree.
 */
class CountryFlagArtV192Test {

    /** Every country the product can name is one the art library draws. */
    @Test
    fun everyNameableCountryIsDrawable() {
        val codes = listOf(
            "AD", "AE", "AF", "AL", "AM", "AR", "AT", "AU", "AZ", "BA",
            "BD", "BE", "BG", "BH", "BN", "BO", "BR", "BY", "CA", "CH",
            "CL", "CN", "CO", "CR", "CY", "CZ", "DE", "DK", "DO", "DZ",
            "EC", "EE", "EG", "ES", "FI", "FR", "GB", "GE", "GH", "GR",
            "GT", "HK", "HR", "HU", "ID", "IE", "IL", "IN", "IQ", "IR",
            "IS", "IT", "JO", "JP", "KE", "KH", "KR", "KW", "KZ", "LB",
            "LI", "LK", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME",
            "MK", "MT", "MU", "MV", "MX", "MY", "NG", "NI", "NL", "NO",
            "NP", "NZ", "OM", "PA", "PE", "PH", "PK", "PL", "PT", "PY",
            "QA", "RO", "RS", "RU", "SA", "SE", "SG", "SI", "SK", "TH",
            "TN", "TR", "TW", "UA", "US", "UY", "UZ", "VE", "VN", "ZA"
        )
        for (code in codes) {
            // The name table knows it, otherwise this is not a product country at all.
            val name = ServerCountry.nameFor(code)
            assertTrue("$code must be nameable (got '$name')", name != code)
            assertTrue("$code must be drawable", CountryFlagSupported(code))
        }
    }

    @Test
    fun supportIsAStrictAlpha2Question() {
        assertTrue(CountryFlagSupported("DE"))
        assertTrue(CountryFlagSupported(" de "))
        assertFalse(CountryFlagSupported(null))
        assertFalse(CountryFlagSupported(""))
        assertFalse(CountryFlagSupported("D"))
        assertFalse(CountryFlagSupported("DEE"))
        assertFalse(CountryFlagSupported("12"))
        assertFalse(CountryFlagSupported("Z"))
    }

    @Test
    fun unknownCodeStillNamesItself() {
        // A code outside the product table renders as its own code, never as a wrong flag:
        // the support gate and the name table must agree on what "unknown" means.
        assertFalse(CountryFlagSupported("XX"))
        assertEquals("XX", ServerCountry.nameFor("XX"))
    }

    @Test
    fun labelGuessAndTestedLocationShareTheArtLibrary() {
        // The offline label path and the tested path both end in a code the circle can draw.
        val fromLabel = ServerCountry.of("🇩🇪 Frankfurt 01")
        assertTrue(CountryFlagSupported(fromLabel.code))
        assertEquals("DE", fromLabel.code)
        // A plain two-letter lead is the same contract.
        assertEquals("TR", ServerCountry.of("TR Istanbul 02").code)
        assertTrue(CountryFlagSupported("TR"))
    }
}
