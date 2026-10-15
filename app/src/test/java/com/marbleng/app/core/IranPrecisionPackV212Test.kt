package com.marbleng.app.core

import com.marbleng.app.model.GeoPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IranPrecisionPackV212Test {
    @Test
    fun everyEntryIsALowercaseRegistrableDomainAndNotTheBareTld() {
        assertTrue(IranPrecisionPack.isWellFormed())

        assertTrue(
            "the curated list shrank to nothing",
            IranPrecisionPack.ENTRIES.size >= 50
        )
        assertEquals(
            "no duplicate entry survives deduplication silently",
            IranPrecisionPack.ENTRIES.size,
            IranPrecisionPack.ENTRIES.map { it.domain }.distinct().size
        )
    }

    @Test
    fun standardEmitsNothingBecauseItMeansOnlyWhatTheUserConfigured() {
        assertTrue(IranPrecisionPack.entries(GeoPrecision.STANDARD).isEmpty())
        assertTrue(IranPrecisionPack.suffixes(GeoPrecision.STANDARD).isEmpty())
        assertTrue(IranPrecisionPack.keywords(GeoPrecision.STANDARD).isEmpty())
        assertTrue(IranPrecisionPack.domains(GeoPrecision.STANDARD).isEmpty())
        assertEquals("Geo tags only", IranPrecisionPack.summary(GeoPrecision.STANDARD))
        assertFalse(IranPrecisionPack.matches("aparat.com", GeoPrecision.STANDARD))
    }

    @Test
    fun enhancedIsTheTldPlusTheCuratedDomainsAndNoKeywords() {
        assertEquals(listOf("ir"), IranPrecisionPack.suffixes(GeoPrecision.ENHANCED))
        assertTrue(IranPrecisionPack.keywords(GeoPrecision.ENHANCED).isEmpty())
        assertTrue(IranPrecisionPack.domains(GeoPrecision.ENHANCED).isNotEmpty())
        assertTrue(IranPrecisionPack.summary(GeoPrecision.ENHANCED).contains("domains"))
        assertFalse(
            "keywords belong to the strongest level only",
            IranPrecisionPack.summary(GeoPrecision.ENHANCED).contains("keywords")
        )
    }

    @Test
    fun maximumAddsTheBrandStems() {
        assertEquals(IranPrecisionPack.KEYWORDS, IranPrecisionPack.keywords(GeoPrecision.MAXIMUM))
        assertTrue(IranPrecisionPack.KEYWORDS.size >= 20)
        assertTrue(IranPrecisionPack.summary(GeoPrecision.MAXIMUM).contains("keywords"))

        assertEquals(
            IranPrecisionPack.domains(GeoPrecision.ENHANCED),
            IranPrecisionPack.domains(GeoPrecision.MAXIMUM)
        )
    }

    @Test
    fun theTldMatchesTheCountryDomainAndItsSubdomainsOnly() {
        assertTrue(IranPrecisionPack.matches("shaparak.ir", GeoPrecision.ENHANCED))

        assertTrue(IranPrecisionPack.matches("WWW.Shaparak.IR.", GeoPrecision.ENHANCED))
        assertTrue(IranPrecisionPack.matches("ir", GeoPrecision.ENHANCED))

        assertFalse(IranPrecisionPack.matches("iran.example.com", GeoPrecision.ENHANCED))
        assertFalse(IranPrecisionPack.matches("air.example", GeoPrecision.ENHANCED))
    }

    @Test
    fun aCuratedDomainMatchesAtItsRootAndBelowIt() {
        val domain = IranPrecisionPack.domains(GeoPrecision.ENHANCED).first()
        assertTrue(IranPrecisionPack.matches(domain, GeoPrecision.ENHANCED))
        assertTrue(IranPrecisionPack.matches("api.$domain", GeoPrecision.ENHANCED))
        assertFalse(IranPrecisionPack.matches("${domain}x.example", GeoPrecision.ENHANCED))
    }

    @Test
    fun nonsenseHostnamesReturnFalseInsteadOfThrowing() {
        assertFalse(IranPrecisionPack.matches("", GeoPrecision.MAXIMUM))
        assertFalse(IranPrecisionPack.matches("   ", GeoPrecision.MAXIMUM))
        assertFalse(IranPrecisionPack.matches("....", GeoPrecision.MAXIMUM))
        assertFalse(IranPrecisionPack.matches("10.0.0.1", GeoPrecision.MAXIMUM))
    }

    @Test
    fun provenanceNamesTheCategoryThatAnsweredAndOnlyWhenItDid() {
        val entry = IranPrecisionPack.ENTRIES.first()
        assertEquals(entry.category, IranPrecisionPack.categoryOf(entry.domain, GeoPrecision.ENHANCED))
        assertNull(IranPrecisionPack.categoryOf("unrelated.example", GeoPrecision.ENHANCED))

        assertNull(IranPrecisionPack.categoryOf(entry.domain, GeoPrecision.STANDARD))
    }

    @Test
    fun theCategoryBreakdownIsTheNumberBehindTheSettingsLine() {
        val counts = IranPrecisionPack.categoryCounts(GeoPrecision.ENHANCED)
        assertTrue(counts.isNotEmpty())
        assertEquals(
            "the breakdown adds up to the list",
            IranPrecisionPack.ENTRIES.size,
            counts.sumOf { it.second }
        )
        assertTrue(
            "sorted largest first, so the line leads with the real reason",
            counts.map { it.second } == counts.map { it.second }.sortedDescending()
        )
        assertTrue(IranPrecisionPack.categoryCounts(GeoPrecision.STANDARD).isEmpty())
    }

    @Test
    fun everyKeywordIsLongEnoughToBeASubstringMatchWithoutColliding() {
        assertTrue(
            "every keyword is at least the matcher's own minimum length",
            IranPrecisionPack.KEYWORDS.all { it.length >= 4 }
        )
        assertTrue(IranPrecisionPack.KEYWORDS.distinct().size == IranPrecisionPack.KEYWORDS.size)
        assertTrue(IranPrecisionPack.KEYWORDS.all { it == it.lowercase() })
    }
}
