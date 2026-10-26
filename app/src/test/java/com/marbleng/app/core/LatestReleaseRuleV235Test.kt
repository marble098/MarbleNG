package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LATEST_VERSION_RULE_V235 — the one update rule: upgrade to the newest
 * *published* version that exists, beta or release or any other label. The cases
 * below are the branches the old channel/stable-only logic got wrong, pinned so
 * no future "channel" can quietly come back.
 */
class LatestReleaseRuleV235Test {

    private fun c(tag: String, at: String, pre: Boolean = false, draft: Boolean = false) =
        LatestReleaseRule.candidate(tag, at, at, pre, draft)!!

    @Test
    fun aStablePublishedAfterABetaIsTheUpdate() {
        // The production case: Xray v26.10.21 (stable, 2026-10-21) sat newer than the
        // pinned v26.9.30 (beta, 2026-09-30) while the `prerelease` channel ignored it.
        val winner = LatestReleaseRule.newest(
            listOf(
                c("v26.9.30", "2026-09-30T07:41:08Z", pre = true),
                c("v26.10.21", "2026-10-21T12:07:11Z", pre = false),
                c("v26.9.9", "2026-09-08T22:28:10Z", pre = true)
            )
        )
        assertEquals("v26.10.21", winner?.tag)
        assertFalse(winner!!.prerelease)
    }

    @Test
    fun aBetaPublishedAfterAStableIsTheUpdate() {
        // The same rule from the other side — and the case GitHub's `/releases/latest`
        // (which the in-app check used to ask) structurally cannot see.
        val winner = LatestReleaseRule.newest(
            listOf(
                c("v11.2.0", "2026-10-20T10:00:00Z", pre = false),
                c("v11.3.0-beta.1", "2026-10-25T10:00:00Z", pre = true)
            )
        )
        assertEquals("v11.3.0-beta.1", winner?.tag)
        assertTrue(winner!!.prerelease)
        assertTrue(LatestReleaseRule.isUpdate(winner.tag, "11.2.0"))
    }

    @Test
    fun aDraftNeverWinsEvenWhenItIsNewest() {
        val winner = LatestReleaseRule.newest(
            listOf(
                c("v99.0.0", "2026-12-01T00:00:00Z", draft = true),
                c("v11.2.1", "2026-10-26T00:00:00Z")
            )
        )
        assertEquals("v11.2.1", winner?.tag)
        assertNull(LatestReleaseRule.newest(listOf(c("v99.0.0", "2026-12-01T00:00:00Z", draft = true))))
        assertNull(LatestReleaseRule.newest(emptyList()))
    }

    @Test
    fun theSameInstantBreaksByVersion() {
        val at = "2026-07-12T09:48:53Z"
        val winner = LatestReleaseRule.newest(
            listOf(c("v2.7.0-beta", at, pre = true), c("v2.7.0", at))
        )
        assertEquals("v2.7.0", winner?.tag)
    }

    @Test
    fun theDatePicksTheCandidateAndTheVersionGuardsTheDirection() {
        // A hotfix of an old line published *today* is the newest by date…
        val winner = LatestReleaseRule.newest(
            listOf(
                c("v11.0.0", "2026-10-01T00:00:00Z"),
                c("v10.3.4", "2026-10-26T00:00:00Z")
            )
        )
        assertEquals("v10.3.4", winner?.tag)
        // …but it is not an update for someone already on the newer line.
        assertFalse(LatestReleaseRule.isUpdate(winner!!.tag, "11.0.0"))
        assertTrue(LatestReleaseRule.isUpdate(winner.tag, "10.3.3"))
        assertFalse(LatestReleaseRule.isUpdate("v11.0.0", "11.0.0"))
    }

    @Test
    fun createdAtIsTheFallbackAndGarbageDatesDropTheCandidate() {
        val fallback = LatestReleaseRule.candidate("v1.0.0", null, "2026-01-02T03:04:05Z", false, false)
        assertEquals(
            LatestReleaseRule.parseIsoEpochMs("2026-01-02T03:04:05Z"),
            fallback?.publishedAtEpochMs
        )
        assertNull(LatestReleaseRule.candidate("v1.0.0", "not-a-date", "", false, false))
        assertNull(LatestReleaseRule.candidate("", "2026-01-02T03:04:05Z", "", false, false))
        assertNull(LatestReleaseRule.parseIsoEpochMs(null))
    }

    @Test
    fun versionOrderIsNumericAndQualifierAware() {
        fun less(a: String, b: String) =
            assertTrue("$a < $b", LatestReleaseRule.compareVersions(a, b) < 0)

        less("v26.9.30", "v26.10.21")            // numeric segments, not lexicographic
        less("1.0.0-alpha1", "1.0.0-beta1")
        less("1.0.0-beta1", "1.0.0-rc1")
        less("1.0.0-rc1", "1.0.0")
        less("1.0.0-beta2", "1.0.0-beta10")      // qualifier counters are numeric too
        less("2.6.0-beta", "2.6.0")
        less("1.0.0", "1.0.1-alpha")             // a newer line's alpha is newer work
        less("v1.14.3", "v1.14.3-extended-2.7.3")
        assertEquals(0, LatestReleaseRule.compareVersions("1.0", "1.0.0"))
        assertEquals(0, LatestReleaseRule.compareVersions("v11.2.1", "11.2.1"))
        assertEquals(0, LatestReleaseRule.compareVersions("1.0.0+build5", "1.0.0"))
    }

    @Test
    fun displayVersionKeepsTheQualifier() {
        assertEquals("11.3.0-beta.1", LatestReleaseRule.displayVersion("v11.3.0-beta.1"))
        assertEquals("26.10.21", LatestReleaseRule.displayVersion("v26.10.21"))
        assertEquals("2.19.3", LatestReleaseRule.displayVersion("2.19.3"))
    }
}
