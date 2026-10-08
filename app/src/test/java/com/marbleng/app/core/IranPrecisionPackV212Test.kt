package com.marbleng.app.core

import com.marbleng.app.model.GeoPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_MULTI_SOURCE_ROUTING_V212 — the curated domestic knowledge.
 *
 * Widening the geo sources answers "which list of domains is used"; it cannot answer "what about
 * the domestic service whose domain no list has caught up with". That is what this pack is, and
 * the part of it that can go wrong quietly is its own content and its three levels: a level that
 * emits nothing is a dial that lies, and a keyword that matches a foreign host sends someone's
 * traffic out of the tunnel by accident — the exact leak the feature exists to close.
 */
class IranPrecisionPackV212Test {

    @Test
    fun everyEntryIsALowercaseRegistrableDomainAndNotTheBareTld() {
        assertTrue(IranPrecisionPack.isWellFormed())
        // The list is the product's own, so its size is a fact worth pinning: a future edit that
        // empties it would leave the "Curated" level a label with nothing behind it.
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
        // The curated domains are still there: the strongest level is a superset, not a switch.
        assertEquals(
            IranPrecisionPack.domains(GeoPrecision.ENHANCED),
            IranPrecisionPack.domains(GeoPrecision.MAXIMUM)
        )
    }

    @Test
    fun theTldMatchesTheCountryDomainAndItsSubdomainsOnly() {
        assertTrue(IranPrecisionPack.matches("shaparak.ir", GeoPrecision.ENHANCED))
        // Case and a trailing dot are not a different host.
        assertTrue(IranPrecisionPack.matches("WWW.Shaparak.IR.", GeoPrecision.ENHANCED))
        assertTrue(IranPrecisionPack.matches("ir", GeoPrecision.ENHANCED))
        // A foreign TLD that merely contains the letters is not domestic.
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
        // STANDARD publishes no curated list, so nothing can claim to have answered.
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
        // A keyword is matched as a substring of every new host, so a short one is a leak: "mci"
        // inside a foreign hostname would send that traffic direct. The matcher's own floor and
        // the list's own spelling have to agree, and this is where they are pinned together.
        // Four is the matcher's own floor: below it a stem collides with ordinary hostnames, so a
        // keyword shorter than that would be a rule that can never fire and a name that promises
        // coverage the config does not deliver.
        assertTrue(
            "every keyword is at least the matcher's own minimum length",
            IranPrecisionPack.KEYWORDS.all { it.length >= 4 }
        )
        assertTrue(IranPrecisionPack.KEYWORDS.distinct().size == IranPrecisionPack.KEYWORDS.size)
        assertTrue(IranPrecisionPack.KEYWORDS.all { it == it.lowercase() })
    }
}
