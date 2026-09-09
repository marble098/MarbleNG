package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * MARBLE_PING_FALSE_FAILED_V159 — the transient-failure truth of the two core-measured pings.
 *
 * The report: URL test and Real delay both work, but *occasionally* a server that is perfectly
 * healthy comes back FAILED. The three promises here are the ones that were broken:
 *
 *  1. **A fallback target owns its full budget.** The URL test used to share one deadline across
 *     the candidate targets, so the first (coldest) target's timeout left the fallbacks with
 *     nothing — the fallback was dead code for the exact failure mode it exists for.
 *  2. **A filtered origin is not a verdict about the route.** Real delay walks every
 *     [com.marbleng.app.model.DelayTest] candidate before it reports failure, exactly like the
 *     Rank sweep always did.
 *  3. **A cancelled sweep stops between targets**, so the longer honest walk never outlives the
 *     user's cancel.
 *
 * Everything here is a pure policy check on [ProbeTargetWalk]: the walk owns the budget and the
 * order, the callers own the sockets and the cores.
 */
class ProbeTransientTruthV159Test {

    // ───────────────────────────────────────────── URL test: every target owns its budget

    @Test
    fun aFallbackTargetReceivesTheFullBudgetAfterTheFirstTargetSpentItsOwn() {
        val budgets = mutableListOf<Int>()
        val result = ProbeTargetWalk.urlTest(
            urls = listOf("https://a.example/generate_204", "https://b.example/generate_204"),
            timeoutMs = 5_000
        ) { _, budget ->
            budgets += budget
            CoreUrlTestResult(0, false, "urltest-http-408: context deadline exceeded")
        }
        assertFalse("two silent targets must stay a failure", result.ok)
        assertEquals(
            "each target must be measured with the FULL budget, never the leftover of the previous one",
            listOf(5_000, 5_000),
            budgets
        )
    }

    @Test
    fun theFirstTargetThatAnswersWinsAndTheWalkStopsThere() {
        val measured = mutableListOf<String>()
        val result = ProbeTargetWalk.urlTest(
            urls = listOf("https://a.example/204", "https://b.example/204", "https://c.example/204"),
            timeoutMs = 3_000
        ) { url, _ ->
            measured += url
            if (url.startsWith("https://b.")) CoreUrlTestResult(212, true)
            else CoreUrlTestResult(0, false, "timeout")
        }
        assertTrue(result.ok)
        assertEquals(212L, result.delayMs)
        assertEquals(
            "a healthy server must never pay for the walk after it answered",
            listOf("https://a.example/204", "https://b.example/204"),
            measured
        )
    }

    @Test
    fun atMostThreeDistinctTargetsAreEverWalked() {
        val measured = mutableListOf<String>()
        ProbeTargetWalk.urlTest(
            urls = listOf(
                "https://a.example/", "https://a.example/", "https://b.example/",
                "https://c.example/", "https://d.example/", "https://e.example/"
            ),
            timeoutMs = 1_000
        ) { url, _ ->
            measured += url
            CoreUrlTestResult(0, false, "timeout")
        }
        assertEquals(
            "duplicates collapse and the walk is capped, so a dead node's cost stays bounded",
            listOf("https://a.example/", "https://b.example/", "https://c.example/"),
            measured
        )
    }

    @Test
    fun impossibleBudgetsAreClampedToTheWindowTheCoreAccepts() {
        assertEquals(500, ProbeTargetWalk.perTargetBudgetMs(1))
        assertEquals(30_000, ProbeTargetWalk.perTargetBudgetMs(60_000))
        assertEquals(5_000, ProbeTargetWalk.perTargetBudgetMs(5_000))
    }

    @Test
    fun anEmptyTargetListReportsNoTargetInsteadOfMeasuringSomethingElse() {
        val result = ProbeTargetWalk.urlTest(urls = emptyList(), timeoutMs = 5_000) { _, _ ->
            error("no target must ever be measured for an empty list")
        }
        assertFalse(result.ok)
        assertEquals("urltest-no-target", result.detail)
    }

    // ───────────────────────────────────────────── Real delay: origin fallback

    @Test
    fun realDelayHandsTheNextOriginTheSameBudgetWhenTheFirstIsSilent() {
        val measured = mutableListOf<String>()
        val result = ProbeTargetWalk.realDelay(
            listOf("https://a.example/204", "https://b.example/204", "https://c.example/204")
        ) { url ->
            measured += url
            if (url.startsWith("https://b.")) {
                RouteProbe.ProbeResult("TUNNEL", 240.0, 100, 3)
            } else {
                // The exact shape a momentarily filtered origin produces today.
                RouteProbe.ProbeResult(
                    "TUNNEL", RouteProbe.UNREACHABLE, 0, 3,
                    lossPercent = 100.0, failureReason = "delay-url-failed"
                )
            }
        }
        assertEquals(
            "the secondary origin's answer must be the published measurement",
            100, result.successPercent
        )
        assertEquals(240.0, result.latencyMs, 0.001)
        assertEquals(
            "one silent origin must hand the walk to the next candidate and stop on success",
            listOf("https://a.example/204", "https://b.example/204"),
            measured
        )
    }

    @Test
    fun realDelayKeepsTheLastHonestFailureWhenEveryOriginStayedSilent() {
        val result = ProbeTargetWalk.realDelay(
            listOf("https://a.example/204", "https://b.example/204")
        ) { url ->
            RouteProbe.ProbeResult(
                "TUNNEL", RouteProbe.UNREACHABLE, 0, 3,
                lossPercent = 100.0, failureReason = "delay-url-failed:${url.substringAfter("//").substringBefore('.')}"
            )
        }
        assertEquals(0, result.successPercent)
        assertEquals("delay-url-failed:b", result.failureReason)
    }

    @Test
    fun realDelayWithNoCandidatesAtAllSaysSoWithTheRealDelayMethod() {
        val result = ProbeTargetWalk.realDelay(urls = emptyList()) {
            error("no target must ever be measured for an empty list")
        }
        assertEquals(0, result.successPercent)
        assertEquals("delay-url-failed", result.failureReason)
        assertEquals(RouteProbe.METHOD_REAL_DELAY, result.method)
        assertEquals(RouteProbe.UNREACHABLE, result.latencyMs, 0.001)
    }

    // ───────────────────────────────────────────── cancellation

    @Test
    fun aCancelledSweepStopsBeforeTheNextTarget() {
        val measured = mutableListOf<String>()
        try {
            ProbeTargetWalk.urlTest(
                urls = listOf("https://a.example/204", "https://b.example/204"),
                timeoutMs = 1_000
            ) { url, _ ->
                measured += url
                // The sweep was cancelled while target one was still in flight.
                Thread.currentThread().interrupt()
                CoreUrlTestResult(0, false, "timeout")
            }
            fail("the walk must notice the cancellation before measuring the second target")
        } catch (expected: InterruptedException) {
            assertEquals(listOf("https://a.example/204"), measured)
        } finally {
            // Leave the thread's flag clear for whichever test runs next on it.
            Thread.interrupted()
        }
    }

    @Test
    fun aCancelledRealDelayWalkStopsBeforeTheNextOrigin() {
        val measured = mutableListOf<String>()
        try {
            ProbeTargetWalk.realDelay(
                listOf("https://a.example/204", "https://b.example/204")
            ) { url ->
                measured += url
                Thread.currentThread().interrupt()
                RouteProbe.ProbeResult(
                    "TUNNEL", RouteProbe.UNREACHABLE, 0, 3,
                    lossPercent = 100.0, failureReason = "delay-url-failed"
                )
            }
            fail("the walk must notice the cancellation before measuring the second origin")
        } catch (expected: InterruptedException) {
            assertEquals(listOf("https://a.example/204"), measured)
        } finally {
            Thread.interrupted()
        }
    }
}
