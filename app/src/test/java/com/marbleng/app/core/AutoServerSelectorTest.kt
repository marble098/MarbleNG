package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.AutoServerScope
import com.marbleng.app.model.AutoServerStrategy
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProxyProfile
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * MARBLE_AUTO_SERVER_SELECTOR_V202 — the five strategies, and the three guards that make them
 * behave.
 *
 * The guards matter more than the strategies. "Pick the lowest ping" is one line; the product
 * already had that line, and it was wrong in three ways this suite pins:
 *
 *  - a node that lost a third of its packets was ranked as "fast" because one probe came back;
 *  - every sweep that reordered the top two by a millisecond moved the user's exit node;
 *  - a node that had failed four times in a row was still in the pool.
 */
class AutoServerSelectorTest {

    private fun profile(id: String, name: String = id) = ProxyProfile(
        id = id,
        name = name,
        scheme = "vless",
        raw = "",
        configJson = ""
    )

    private fun bench(
        id: String,
        latencyMs: Double,
        success: Int = 1,
        jitterMs: Double = 0.0,
        lossPercent: Double = 0.0,
        loadedLatencyMs: Double = 0.0,
        bytesPerSecond: Double = 0.0,
        measuredAtMs: Long = 0L
    ) = BenchmarkResult(
        profileId = id,
        name = id,
        success = success,
        latencyMs = latencyMs,
        bytesPerSecond = bytesPerSecond,
        score = 0.0,
        jitterMs = jitterMs,
        lossPercent = lossPercent,
        loadedLatencyMs = loadedLatencyMs,
        measuredAtMs = measuredAtMs
    )

    private fun candidate(
        id: String,
        latencyMs: Double? = null,
        current: Boolean = false,
        failureStreak: Int = 0,
        nowMs: Long = 1_000_000L,
        jitterMs: Double = 0.0,
        lossPercent: Double = 0.0,
        loadedLatencyMs: Double = 0.0,
        bytesPerSecond: Double = 0.0,
        measuredAtMs: Long = 0L,
        success: Int = 1
    ) = ServerCandidate(
        profile = profile(id),
        benchmark = latencyMs?.let {
            bench(
                id = id,
                latencyMs = it,
                success = success,
                jitterMs = jitterMs,
                lossPercent = lossPercent,
                loadedLatencyMs = loadedLatencyMs,
                bytesPerSecond = bytesPerSecond,
                measuredAtMs = if (measuredAtMs == 0L) nowMs else measuredAtMs
            )
        },
        failureStreak = failureStreak,
        current = current
    )

    // ─── The shared scoring model ────────────────────────────────────────────────────

    @Test
    fun `latency score is a hyperbola not a line`() {
        // 1/(1+r): the first 50 ms of a route matters enormously; 300 → 350 ms barely at all.
        val fast = AutoServerSelector.latencyScore(40.0)
        val mid = AutoServerSelector.latencyScore(160.0)
        val slow = AutoServerSelector.latencyScore(320.0)
        assertTrue(fast > mid && mid > slow)
        assertEquals("the reference latency is worth exactly half credit", 0.5, mid, 0.001)
        val firstStep = fast - mid
        val secondStep = mid - slow
        assertTrue("equal latency must cost less as latency grows", firstStep > secondStep)
        assertEquals("an unknown latency earns nothing", 0.0, AutoServerSelector.latencyScore(0.0))
        assertEquals("NaN is not a measurement", 0.0, AutoServerSelector.latencyScore(Double.NaN))
    }

    @Test
    fun `loss is a hard penalty not a soft one`() {
        assertEquals(1.0, AutoServerSelector.lossScore(0.0), 0.001)
        assertEquals("a third lost is a broken route", 0.0, AutoServerSelector.lossScore(34.0), 0.001)
        assertEquals(0.0, AutoServerSelector.lossScore(100.0), 0.001)
    }

    @Test
    fun `a route whose latency triples under load is a loaded route`() {
        val idleFast = AutoServerSelector.loadScore(50.0, 50.0, 5_000_000.0)
        val idleFastLoaded = AutoServerSelector.loadScore(50.0, 150.0, 5_000_000.0)
        assertTrue(
            "bufferbloat must cost a node even when its idle latency is identical",
            idleFast > idleFastLoaded
        )
        // Without an under-load measurement the score falls back on throughput and says so by
        // never reaching the certainty of a real measurement.
        val unknown = AutoServerSelector.loadScore(50.0, 0.0, 5_000_000.0)
        assertTrue(unknown <= 1.0 && unknown >= 0.0)
    }

    @Test
    fun `stale evidence is discounted but never fully discarded`() {
        val now = 10_000_000L
        val fresh = AutoServerSelector.freshnessScore(now, now)
        val quarter = AutoServerSelector.freshnessScore(now - AutoServerSelector.EVIDENCE_MAX_AGE_MS / 4, now)
        val ancient = AutoServerSelector.freshnessScore(1L, now)
        assertTrue("$fresh / $quarter / $ancient", fresh > quarter && quarter > ancient)
        assertEquals("a memory still counts for something", 0.25, ancient, 0.001)
        assertEquals("an unstamped result is neutral", 0.5, AutoServerSelector.freshnessScore(0L, now), 0.001)
        // An unstamped result is indistinguishable from a negative one, so the guard is on the
        // stamp and not on the arithmetic: a clock that has not run yet must not read as
        // "measured ten evidence windows ago".
        assertEquals(0.5, AutoServerSelector.freshnessScore(-5L, now), 0.001)
    }

    // ─── The three guards ────────────────────────────────────────────────────────────

    @Test
    fun `a node losing a third of its packets is not the fastest node`() {
        val now = 1_000_000L
        val lucky = candidate("lucky", latencyMs = 30.0, lossPercent = 40.0, nowMs = now)
        val steady = candidate("steady", latencyMs = 90.0, lossPercent = 0.0, nowMs = now)
        val choice = AutoServerSelector.choose(
            candidates = listOf(lucky, steady),
            strategy = AutoServerStrategy.LEAST_PING,
            nowMs = now
        )
        assertEquals("steady", choice.profile?.id)
    }

    @Test
    fun `a node with a quarantine streak is out of the pool`() {
        val now = 1_000_000L
        val broken = candidate(
            "broken",
            latencyMs = 20.0,
            failureStreak = AutoServerSelector.QUARANTINE_STREAK,
            nowMs = now
        )
        val healthy = candidate("healthy", latencyMs = 120.0, nowMs = now)
        for (strategy in listOf(
            AutoServerStrategy.SMART,
            AutoServerStrategy.LEAST_PING,
            AutoServerStrategy.LEAST_LOAD
        )) {
            val choice = AutoServerSelector.choose(listOf(broken, healthy), strategy, nowMs = now)
            assertEquals(
                "$strategy must not pick a quarantined node",
                "healthy",
                choice.profile?.id
            )
        }
    }

    @Test
    fun `a quarantined node is the only choice is still served`() {
        // The filter is a filter, not a trapdoor: if every node is failing, the selector still
        // answers with one instead of leaving the user with nothing.
        val now = 1_000_000L
        val only = candidate(
            "only",
            latencyMs = 20.0,
            failureStreak = AutoServerSelector.QUARANTINE_STREAK,
            nowMs = now
        )
        val choice = AutoServerSelector.choose(listOf(only), AutoServerStrategy.SMART, nowMs = now)
        assertNotNull(choice.profile)
    }

    @Test
    fun `a challenger must beat the incumbent by the margin`() {
        val now = 1_000_000L
        val current = candidate("current", latencyMs = 100.0, current = true, nowMs = now)
        val slightly = candidate("slightly", latencyMs = 96.0, nowMs = now)
        val clearly = candidate("clearly", latencyMs = 40.0, nowMs = now)

        val held = AutoServerSelector.choose(
            listOf(current, slightly),
            AutoServerStrategy.LEAST_PING,
            nowMs = now,
            switchMarginPercent = 15
        )
        assertEquals("a 4 % edge must not move the exit node", "current", held.profile?.id)
        assertTrue(held.held)

        val moved = AutoServerSelector.choose(
            listOf(current, clearly),
            AutoServerStrategy.LEAST_PING,
            nowMs = now,
            switchMarginPercent = 15
        )
        assertEquals("clearly", moved.profile?.id)
        assertFalse(moved.held)
    }

    @Test
    fun `an unhealthy incumbent is replaced without waiting for the margin`() {
        // The margin prevents flapping between two *good* routes; it must never protect a bad one.
        val now = 1_000_000L
        val dead = candidate("dead", latencyMs = 400.0, success = 0, current = true, nowMs = now)
        val alive = candidate("alive", latencyMs = 200.0, nowMs = now)
        val choice = AutoServerSelector.choose(
            listOf(dead, alive),
            AutoServerStrategy.LEAST_PING,
            nowMs = now,
            switchMarginPercent = 40
        )
        assertEquals("alive", choice.profile?.id)
    }

    @Test
    fun `a zero margin makes every reorder a roaming route`() {
        val now = 1_000_000L
        val current = candidate("current", latencyMs = 100.0, current = true, nowMs = now)
        val hair = candidate("hair", latencyMs = 99.0, nowMs = now)
        val choice = AutoServerSelector.choose(
            listOf(current, hair),
            AutoServerStrategy.LEAST_PING,
            nowMs = now,
            switchMarginPercent = 0
        )
        assertEquals("margin 0 is the user asking for exactly this", "hair", choice.profile?.id)
    }

    // ─── The five strategies ─────────────────────────────────────────────────────────

    @Test
    fun `least load prefers the node that does not collapse under transfer`() {
        val now = 1_000_000L
        // Both idle at 60 ms. One triples under load, the other barely moves.
        val congested = candidate(
            "congested",
            latencyMs = 60.0,
            loadedLatencyMs = 200.0,
            bytesPerSecond = 1_000_000.0,
            nowMs = now
        )
        val free = candidate(
            "free",
            latencyMs = 60.0,
            loadedLatencyMs = 70.0,
            bytesPerSecond = 6_000_000.0,
            nowMs = now
        )
        val choice = AutoServerSelector.choose(
            listOf(congested, free),
            AutoServerStrategy.LEAST_LOAD,
            nowMs = now
        )
        assertEquals("free", choice.profile?.id)
    }

    @Test
    fun `smart weighs freshness and punishes a failure streak`() {
        val now = 10_000_000L
        val stale = candidate("stale", latencyMs = 30.0, measuredAtMs = now - 3_600_000L, nowMs = now)
        val fresh = candidate("fresh", latencyMs = 80.0, measuredAtMs = now, nowMs = now)
        val choice = AutoServerSelector.choose(
            listOf(stale, fresh),
            AutoServerStrategy.SMART,
            nowMs = now
        )
        assertEquals("an hour-old 30 ms is not worth more than a live 80 ms", "fresh", choice.profile?.id)

        val flaky = candidate("flaky", latencyMs = 40.0, failureStreak = 2, nowMs = now)
        val solid = candidate("solid", latencyMs = 90.0, nowMs = now)
        val second = AutoServerSelector.choose(
            listOf(flaky, solid),
            AutoServerStrategy.SMART,
            nowMs = now
        )
        assertEquals("a node that keeps failing is not a route", "solid", second.profile?.id)
    }

    @Test
    fun `round robin walks the whole pool including unmeasured nodes`() {
        val now = 1_000_000L
        val pool = listOf(
            candidate("a", latencyMs = 10.0, nowMs = now),
            candidate("b", nowMs = now),
            candidate("c", latencyMs = 50.0, nowMs = now)
        )
        val visited = mutableSetOf<String>()
        var cursor = 0
        repeat(pool.size * 2) {
            val choice = AutoServerSelector.choose(
                pool,
                AutoServerStrategy.ROUND_ROBIN,
                nowMs = now,
                roundRobinCursor = cursor
            )
            val id = choice.profile!!.id
            visited += id
            cursor = AutoServerSelector.nextCursor(pool, cursor)
        }
        assertEquals("rotation exists to try the untried node", setOf("a", "b", "c"), visited)
    }

    @Test
    fun `round robin cursor wraps and survives a shrinking pool`() {
        val pool = listOf("a", "b", "c").map { candidate(it, nowMs = 1L) }
        assertEquals(1, AutoServerSelector.nextCursor(pool, 0))
        assertEquals(2, AutoServerSelector.nextCursor(pool, 1))
        assertEquals(0, AutoServerSelector.nextCursor(pool, 2))
        assertEquals("a stale index must not be carried", 0, AutoServerSelector.nextCursor(emptyList(), 7))
        val small = pool.take(1)
        assertEquals(0, AutoServerSelector.nextCursor(small, 5))
    }

    @Test
    fun `random is deterministic under a seed and spread across seeds`() {
        val pool = listOf("a", "b", "c", "d").map { candidate(it, latencyMs = 10.0, nowMs = 1L) }
        val seeded = AutoServerSelector.choose(
            pool, AutoServerStrategy.RANDOM, nowMs = 1L, randomSeed = 42L
        )
        val same = AutoServerSelector.choose(
            pool, AutoServerStrategy.RANDOM, nowMs = 1L, randomSeed = 42L
        )
        assertEquals("the same seed is the same draw", seeded.profile?.id, same.profile?.id)

        val spread = (1L..200L).map { seed ->
            AutoServerSelector.choose(
                pool, AutoServerStrategy.RANDOM, nowMs = 1L, randomSeed = seed
            ).profile?.id
        }.toSet()
        assertEquals("random must reach every node in the pool", 4, spread.size)
    }

    @Test
    fun `never measured nodes are never the smart pick when a measured one exists`() {
        val now = 1_000_000L
        val unknown = candidate("unknown", nowMs = now)
        val known = candidate("known", latencyMs = 250.0, nowMs = now)
        val choice = AutoServerSelector.choose(
            listOf(unknown, known), AutoServerStrategy.SMART, nowMs = now
        )
        assertEquals("known", choice.profile?.id)
    }

    @Test
    fun `an empty pool says so instead of throwing`() {
        val choice = AutoServerSelector.choose(emptyList(), AutoServerStrategy.SMART)
        assertNull(choice.profile)
        assertTrue(choice.reason.isNotBlank())
    }

    @Test
    fun `every strategy labels and describes itself`() {
        AutoServerStrategy.entries.forEach { strategy ->
            assertTrue(AutoServerSelector.shortLabel(strategy).isNotBlank())
            assertTrue(AutoServerSelector.describe(strategy).length > 20)
        }
        assertTrue(AutoServerSelector.scopeLabel(AutoServerScope.SOURCE).isNotBlank())
    }

    @Test
    fun `the shipped default is off and smart`() {
        // The requirement is explicit: the user switches this on. Anything that moves a
        // user's exit node without being asked is a bug however well it chooses.
        val settings = AppSettings()
        assertFalse("the selector must never surprise a user", settings.autoServerSelectorEnabled)
        assertEquals(AutoServerStrategy.SMART, settings.autoServerStrategyEnum)
        assertEquals(AutoServerScope.SOURCE, settings.autoServerScopeEnum)
        assertEquals(15, settings.autoServerSwitchMarginPercent)
    }

    @Test
    fun `when it is switched on it acts on evidence not on an explicit choice`() {
        // The two moments that are on are the two where the selector acts for the user: a
        // sweep just produced fresh evidence for every server, and the live route just failed.
        // Connect stays off until asked, because tapping a server is an explicit choice.
        val settings = AppSettings()
        assertTrue(settings.autoServerOnScan)
        assertTrue(settings.autoServerOnFailure)
        assertFalse(settings.autoServerOnConnect)
    }

    @Test
    fun `unknown strategy strings fall back to smart not to least ping`() {
        val settings = AppSettings(autoServerStrategy = "something-else")
        assertEquals(AutoServerStrategy.SMART, settings.autoServerStrategyEnum)
    }
}
