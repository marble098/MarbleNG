package com.marbleng.app.model

import com.marbleng.app.core.UrlTestTarget
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CENSORSHIP_REACH_TARGETS_V166 — the delay-test pool both cores ping through.
 *
 * Xray's real delay and sing-box's URL test consume the exact same [DelayTest.candidates] list,
 * so on a restricted network the pool IS the measurement plane: one blocked operator in it
 * cannot be compensated by another spelling of the same operator. These tests pin the pool's
 * censorship-resilience contract: three distinct origins, three distinct operators, every entry
 * a target the URL-test contract accepts, and the configured primary always first.
 */
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
        // The sing-box delay endpoint refuses http and user info (UrlTestTarget); Xray's real
        // delay fetches the same URLs through its SOCKS inbound. A candidate that either core
        // would reject is a dead slot in the walk.
        DelayTest.candidates("").forEach { candidate ->
            assertNull(
                "URL test must accept $candidate",
                UrlTestTarget.validate(candidate)
            )
        }
    }

    @Test
    fun thePoolNeverCarriesTwoSpellingsOfOneOperator() {
        // The V166 failure mode: gstatic and google.com are one operator twice, so a filter
        // that touches Google kills two of three walk slots at once. Registrable domains must
        // be pairwise different.
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

    /** The last two labels of a hostname: `www.gstatic.com` → `gstatic.com`. */
    private fun registrableDomain(host: String): String =
        host.split(".").takeLast(2).joinToString(".")
}
