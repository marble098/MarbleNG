package com.marbleng.app.model

import com.marbleng.app.core.UrlTestTarget
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CensorshipReachTargetsV166Test {
    @Test
    fun theDefaultPoolIsThreeDistinctOriginsInWalkOrder() {
        assertEquals(
            listOf(
                DelayTest.URL,
                DelayTest.URL_SECONDARY,
                DelayTest.URL_TERTIARY
            ),
            DelayTest.candidates("")
        )
        assertEquals(3, DelayTest.candidates("").distinct().size)
    }

    @Test
    fun theConfiguredPrimaryAlwaysLeadsTheWalk() {
        val configured = "https://example.com/generate_204"
        val candidates = DelayTest.candidates(configured)
        assertEquals(configured, candidates.first())
        assertEquals(
            listOf(configured, DelayTest.URL_SECONDARY, DelayTest.URL_TERTIARY),
            candidates
        )
    }

    @Test
    fun everyCandidateIsAUrlTestBothCoresAccept() {
        DelayTest.candidates("").forEach { candidate ->
            assertNull(
                "URL test must accept $candidate",
                UrlTestTarget.validate(candidate)
            )
        }
    }

    @Test
    fun thePoolNeverCarriesTwoSpellingsOfOneOperator() {
        val domains = DelayTest.candidates("").map { registrableDomain(URL(it).host) }
        assertEquals(domains.distinct(), domains)
        assertTrue(
            "the pool must span at least three operators",
            domains.distinct().size == 3
        )
    }

    @Test
    fun aConfiguredDuplicateCollapsesInsteadOfWastingAWalkSlot() {
        val candidates = DelayTest.candidates(DelayTest.URL_SECONDARY)
        assertEquals(2, candidates.size)
        assertEquals(DelayTest.URL_SECONDARY, candidates.first())
        assertTrue(DelayTest.URL_TERTIARY in candidates)
    }

    private fun registrableDomain(host: String): String =
        host.split(".").takeLast(2).joinToString(".")
}
