package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_HOME_HERO_V261 — the hero's four readings, pinned without a composition.
 *
 * The brief's whole risk was a *dishonest* dashboard: a card that prints `0 ms` while it is
 * connecting, a jitter of zero for a route nobody has measured, an exit address carried over from
 * three reconnects ago. So the model is a pure function over the repository's own fields and this file
 * pins every "we do not know" separately from every "it is bad".
 */
class HomeHeroV261Test {

    @Test
    fun aMetricIsAValueAUnitAndAState() {
        val metrics = HomeHeroStudio.metrics(
            connected = true,
            working = false,
            pingMs = 48,
            jitterMs = 7,
            uptimeLabel = "00:10:01",
            exitAddress = "203.0.113.9",
            hasRoute = true
        )
        assertEquals(HomeHeroStudio.METRIC_COUNT, metrics.size)
        assertEquals(
            listOf(
                HeroMetricKind.PING,
                HeroMetricKind.JITTER,
                HeroMetricKind.UPTIME,
                HeroMetricKind.EXIT
            ),
            metrics.map { it.kind }
        )
        val ping = metrics[0]
        assertEquals("48", ping.value)
        assertEquals("ms", ping.unit)
        assertEquals(HeroTone.GOOD, ping.tone)
        assertEquals("the digits and the unit are apart so the type ladder can set them apart", "48", ping.value)
        assertTrue(metrics[2].isPlaceholder.not())
    }

    @Test
    fun anUnmeasuredMetricIsADashAndNotAZero() {
        val metrics = HomeHeroStudio.metrics(
            connected = false,
            working = false,
            pingMs = 0,
            jitterMs = -1,
            uptimeLabel = "",
            exitAddress = "",
            hasRoute = true
        )
        val ping = metrics.first { it.kind == HeroMetricKind.PING }
        val jitter = metrics.first { it.kind == HeroMetricKind.JITTER }
        val uptime = metrics.first { it.kind == HeroMetricKind.UPTIME }

        assertEquals(HomeHeroStudio.ABSENT, ping.value)
        assertEquals(HeroTone.PENDING, ping.tone)
        assertEquals(HomeHeroStudio.ABSENT, jitter.value)
        assertEquals(HeroTone.PENDING, jitter.tone)
        assertEquals("a clock without a session is not pending, it is idle", HeroTone.IDLE, uptime.tone)
        metrics.forEach { metric ->
            assertTrue("no metric may print a zero it did not measure", metric.value != "0")
            assertTrue(metric.isWorking.not())
        }
    }

    @Test
    fun aRunningProbeSaysSoOnEveryNumberItIsMeasuring() {
        val metrics = HomeHeroStudio.metrics(
            connected = true,
            working = true,
            pingMs = 48,
            jitterMs = 7,
            uptimeLabel = "00:10:01",
            exitAddress = "203.0.113.9",
            hasRoute = true
        )
        val working = metrics.filter { it.isWorking }
        assertEquals("one probe, two readings, one animation each", 2, working.size)
        assertEquals(
            listOf(HeroMetricKind.PING, HeroMetricKind.JITTER).toSet(),
            working.map { it.kind }.toSet()
        )
        working.forEach { assertEquals(HomeHeroStudio.WORKING, it.value) }
        // The clock keeps ticking and the address keeps being an address while a probe runs.
        assertEquals(HeroTone.LIVE, metrics.first { it.kind == HeroMetricKind.UPTIME }.tone)
        assertEquals(HeroTone.LIVE, metrics.first { it.kind == HeroMetricKind.EXIT }.tone)
    }

    @Test
    fun theExitAddressIsNotAboutTheTunnel() {
        // A phone that has never connected still has a public address. The lookup is pending, not
        // idle, and it is *live* the moment it lands.
        val pending = HomeHeroStudio.metrics(
            connected = false, working = false, pingMs = 0, jitterMs = -1,
            uptimeLabel = "", exitAddress = "", hasRoute = false
        )
        assertEquals(HeroTone.PENDING, pending.first { it.kind == HeroMetricKind.EXIT }.tone)
        assertEquals(HeroTone.IDLE, pending.first { it.kind == HeroMetricKind.PING }.tone)

        val live = HomeHeroStudio.metrics(
            connected = false, working = false, pingMs = 0, jitterMs = -1,
            uptimeLabel = "", exitAddress = "198.51.100.4", hasRoute = false
        )
        assertEquals(HeroTone.LIVE, live.first { it.kind == HeroMetricKind.EXIT }.tone)
    }

    @Test
    fun theLiveReadingWinsOnlyWhileATunnelExists() {
        // While connected the live RTT is the honest number; before that it is the last measurement
        // of the route the connect control would use.
        assertEquals(41, HomeHeroStudio.ping(connected = true, livePingMs = 41, selectedPingMs = 190))
        assertEquals(190, HomeHeroStudio.ping(connected = false, livePingMs = 41, selectedPingMs = 190))
        assertEquals("a negative never reaches the panel", 0, HomeHeroStudio.ping(true, -5, 0))
    }

    @Test
    fun jitterFallsBackToTheBenchmarkAndSaysWhenThereIsNone() {
        assertEquals(9, HomeHeroStudio.jitter(connected = true, liveJitterMs = 9, benchmarkedJitterMs = 40.0))
        assertEquals(40, HomeHeroStudio.jitter(connected = false, liveJitterMs = 9, benchmarkedJitterMs = 40.0))
        assertEquals(-1, HomeHeroStudio.jitter(connected = false, liveJitterMs = 0, benchmarkedJitterMs = null))
        assertEquals(-1, HomeHeroStudio.jitter(connected = true, liveJitterMs = 0, benchmarkedJitterMs = null))
    }

    @Test
    fun theBandsAreTheOnesAReaderAlreadyFeels() {
        assertNull("no measurement is not a band", HomeHeroStudio.pingTone(0))
        assertEquals(HeroTone.GOOD, HomeHeroStudio.pingTone(HomeHeroStudio.PING_FAST_MS))
        assertEquals(HeroTone.FAIR, HomeHeroStudio.pingTone(HomeHeroStudio.PING_FAST_MS + 1))
        assertEquals(HeroTone.POOR, HomeHeroStudio.pingTone(HomeHeroStudio.PING_SLOW_MS + 1))

        assertNull(HomeHeroStudio.jitterTone(-1))
        assertEquals(HeroTone.GOOD, HomeHeroStudio.jitterTone(HomeHeroStudio.JITTER_STEADY_MS))
        assertEquals(HeroTone.FAIR, HomeHeroStudio.jitterTone(HomeHeroStudio.JITTER_NOISY_MS))
        assertEquals(HeroTone.POOR, HomeHeroStudio.jitterTone(HomeHeroStudio.JITTER_NOISY_MS + 1))
        // A call clips at 30ms of jitter and is unusable at 80: the hero colours against the two
        // thresholds a reader already feels rather than inventing its own.
        assertEquals(30, HomeHeroStudio.JITTER_STEADY_MS)
        assertEquals(80, HomeHeroStudio.JITTER_NOISY_MS)
    }

    @Test
    fun theSnapshotAddressesEachMetricByName() {
        val snapshot = HomeHeroSnapshot(
            stateLabel = "Protected",
            overline = "Connection",
            routeName = "Berlin 01",
            routeDetail = "VLESS · Main",
            countryCode = "DE",
            metrics = HomeHeroStudio.metrics(
                connected = true, working = false, pingMs = 48, jitterMs = 7,
                uptimeLabel = "00:10:01", exitAddress = "203.0.113.9", hasRoute = true
            ),
            working = false
        )
        assertEquals("48", snapshot.ping?.value)
        assertEquals("7", snapshot.jitter?.value)
        assertEquals("00:10:01", snapshot.uptime?.value)
        assertEquals("203.0.113.9", snapshot.exit?.value)
    }

    @Test
    fun theStatusLineFallsBackWhenThereIsNoServer() {
        assertEquals("Berlin 01", HomeHeroStudio.statusLine("Berlin 01", "Choose server"))
        assertEquals("Choose server", HomeHeroStudio.statusLine("", "Choose server"))
        assertEquals("Choose server", HomeHeroStudio.statusLine("   ", "Choose server"))
    }
}
