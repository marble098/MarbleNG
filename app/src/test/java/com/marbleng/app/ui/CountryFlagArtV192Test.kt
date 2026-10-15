package com.marbleng.app.ui

import com.marbleng.app.core.ServerCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryFlagArtV192Test {
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
        assertFalse(CountryFlagSupported("XX"))
        assertEquals("XX", ServerCountry.nameFor("XX"))
    }

    @Test
    fun labelGuessAndTestedLocationShareTheArtLibrary() {
        val fromLabel = ServerCountry.of("🇩🇪 Frankfurt 01")
        assertTrue(CountryFlagSupported(fromLabel.code))
        assertEquals("DE", fromLabel.code)

        assertEquals("TR", ServerCountry.of("TR Istanbul 02").code)
        assertTrue(CountryFlagSupported("TR"))
    }
}
