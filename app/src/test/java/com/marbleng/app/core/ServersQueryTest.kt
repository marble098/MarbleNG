package com.marbleng.app.core

import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.NodeSortMode
import com.marbleng.app.model.ProxyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure engine behind the Servers page (MARBLE_SERVERS_QUERY_V120).
 *
 * The page's search box, protocol chip, group chip and advanced switches all describe *what should
 * be visible*, and one sort mode decides the order. Because every rule lives here instead of in
 * Compose, these tests are the contract the UI is allowed to rely on.
 */
class ServersQueryTest {

    private fun profile(
        id: String,
        name: String = id,
        scheme: String = "vless",
        host: String = "1.2.3.4",
        port: Int = 443,
        transport: String = "raw",
        security: String = "reality",
        sourceId: String = "sub-1",
        sourceName: String = "Provider"
    ) = ProxyProfile(
        id = id,
        name = name,
        scheme = scheme,
        raw = "$scheme://$host",
        configJson = "{}",
        host = host,
        port = port,
        transport = transport,
        security = security,
        subscriptionId = sourceId,
        subscriptionName = sourceName
    )

    private fun result(id: String, latencyMs: Double, success: Int = 100, score: Double = 1.0) =
        BenchmarkResult(
            profileId = id,
            name = id,
            success = success,
            latencyMs = latencyMs,
            bytesPerSecond = 1_000.0,
            score = score
        )

    // ------------------------------------------------------------------ protocol badge

    @Test
    fun badgeNamesSchemeSecurityAndTransport() {
        assertEquals(
            "VLESS/REALITY",
            ServersQuery.badge(profile("a", security = "reality", transport = "raw"))
        )
        assertEquals(
            "VMESS/TLS/WS",
            ServersQuery.badge(
                profile("b", scheme = "vmess", security = "tls", transport = "websocket")
            )
        )
        assertEquals(
            "VLESS/TLS/H2",
            ServersQuery.badge(profile("c", security = "tls", transport = "h2"))
        )
    }

    @Test
    fun badgeDropsRedundantSegmentsAndNeverIsEmpty() {
        assertEquals(
            "SOCKS5",
            ServersQuery.badge(profile("d", scheme = "socks5", security = "none", transport = "tcp"))
        )
        assertEquals(
            "PROXY",
            ServersQuery.badge(profile("e", scheme = "  ", security = "none", transport = "tcp"))
        )
    }

    @Test
    fun addressShowsHostAndPortWithoutIpv6Brackets() {
        assertEquals("1.2.3.4:443", ServersQuery.address(profile("f")))
        assertEquals(
            "2001:db8::1:8443",
            ServersQuery.address(profile("g", host = "[2001:db8::1]", port = 8443))
        )
        assertEquals("", ServersQuery.address(profile("h", host = " ", port = 0)))
    }

    // ------------------------------------------------------------------ protocol menu

    @Test
    fun talliesCountSchemesRichestFirstThenAlphabetically() {
        val profiles = listOf(
            profile("1", scheme = "vless"),
            profile("2", scheme = "VLESS"),
            profile("3", scheme = "vmess"),
            profile("4", scheme = "hysteria2"),
            profile("5", scheme = "vmess")
        )
        val tallies = ServersQuery.protocolTallies(profiles)
        assertEquals(listOf("VLESS", "VMESS", "HYSTERIA2"), tallies.map { it.scheme })
        assertEquals(listOf(2, 2, 1), tallies.map { it.count })
    }

    // ------------------------------------------------------------------ filtering

    @Test
    fun searchMatchesNameProtocolHostAndCountry() {
        val node = profile("s1", name = "\uD83C\uDDE9\uD83C\uDDEA Frankfurt 01", host = "de.example")
        assertTrue(ServersQuery.matchesQuery(node, "frankfurt"))
        assertTrue(ServersQuery.matchesQuery(node, "vless"))
        assertTrue(ServersQuery.matchesQuery(node, "de.example"))
        assertTrue(ServersQuery.matchesQuery(node, "germany"))
        assertFalse(ServersQuery.matchesQuery(node, "tokyo"))
        assertTrue(ServersQuery.matchesQuery(node, "   "))
    }

    @Test
    fun protocolFilterIsCaseInsensitiveAndSourceFilterIsExact() {
        val node = profile("f1", scheme = "vless", sourceId = "sub-1")
        assertTrue(ServersQuery.matches(node, ServersFilter(protocol = "vless")))
        assertTrue(ServersQuery.matches(node, ServersFilter(protocol = "VLESS")))
        assertFalse(ServersQuery.matches(node, ServersFilter(protocol = "vmess")))
        assertTrue(ServersQuery.matches(node, ServersFilter(sourceId = "sub-1")))
        assertFalse(ServersQuery.matches(node, ServersFilter(sourceId = "sub-2")))
        assertTrue(ServersQuery.matches(node, ServersFilter(sourceId = "all")))
    }

    @Test
    fun onlyReachableHidesUnmeasuredAndFailedServers() {
        val nodes = listOf(profile("ok"), profile("failed"), profile("unmeasured"))
        val benchmarks = mapOf(
            "ok" to result("ok", 90.0),
            "failed" to result("failed", 0.0, success = 0)
        )
        val visible = ServersQuery.visible(nodes, ServersFilter(onlyReachable = true), benchmarks)
        assertEquals(listOf("ok"), visible.map { it.id })
    }

    @Test
    fun maxPingCeilingHidesUnmeasuredServersToo() {
        val nodes = listOf(profile("fast"), profile("slow"), profile("unmeasured"))
        val benchmarks = mapOf(
            "fast" to result("fast", 80.0),
            "slow" to result("slow", 300.0)
        )
        val visible = ServersQuery.visible(nodes, ServersFilter(maxPingMs = 100), benchmarks)
        assertEquals(listOf("fast"), visible.map { it.id })
        // "Off" is 0, which must disable the ceiling entirely.
        assertEquals(
            listOf("fast", "slow", "unmeasured"),
            ServersQuery.visible(nodes, ServersFilter(maxPingMs = ServersQuery.MAX_PING_OFF), benchmarks)
                .map { it.id }
        )
    }

    @Test
    fun filtersComposeAndReportWhenAnythingIsActive() {
        val filter = ServersFilter(query = "frank", protocol = "vless", maxPingMs = 200)
        assertTrue(filter.isActive)
        assertFalse(ServersFilter().isActive)
        assertEquals("all", ServersFilter(sourceId = "sub-9").cleared().sourceId)
    }

    // ------------------------------------------------------------------ sorting

    @Test
    fun defaultSortKeepsPublishedOrderAndReverseFlipsIt() {
        val nodes = listOf(profile("3"), profile("1"), profile("2"))
        assertEquals(
            listOf("3", "1", "2"),
            ServersQuery.sort(nodes, NodeSortMode.DEFAULT, reverse = false).map { it.id }
        )
        assertEquals(
            listOf("2", "1", "3"),
            ServersQuery.sort(nodes, NodeSortMode.DEFAULT, reverse = true).map { it.id }
        )
    }

    @Test
    fun nameSortIgnoresLeadingFlags() {
        val nodes = listOf(
            profile("b", name = "\uD83C\uDDFA\uD83C\uDDF8 Atlanta"),
            profile("a", name = "\uD83C\uDDE9\uD83C\uDDEA Berlin")
        )
        assertEquals(
            listOf("b", "a"),
            ServersQuery.sort(nodes, NodeSortMode.NAME, reverse = false).map { it.id }
        )
    }

    @Test
    fun pingSortPutsNeverMeasuredServersLast() {
        val nodes = listOf(profile("unmeasured"), profile("fast"), profile("slow"))
        val benchmarks = mapOf(
            "fast" to result("fast", 40.0),
            "slow" to result("slow", 260.0)
        )
        assertEquals(
            listOf("fast", "slow", "unmeasured"),
            ServersQuery.sort(nodes, NodeSortMode.PING, reverse = false, benchmarks = benchmarks)
                .map { it.id }
        )
    }

    @Test
    fun protocolAndSourceAndCountrySortsAreStable() {
        val nodes = listOf(
            profile("1", name = "\uD83C\uDDE9\uD83C\uDDEA Berlin", scheme = "vmess", sourceId = "zeta"),
            profile("2", name = "\uD83C\uDDFA\uD83C\uDDF8 Atlanta", scheme = "vless", sourceId = "alpha"),
            profile("3", name = "Mystery", scheme = "trojan", sourceId = "mid")
        )
        assertEquals(
            listOf("3", "2", "1"),
            ServersQuery.sort(nodes, NodeSortMode.PROTOCOL, reverse = false).map { it.id }
        )
        assertEquals(
            listOf("2", "3", "1"),
            ServersQuery.sort(nodes, NodeSortMode.SOURCE, reverse = false).map { it.id }
        )
        // Atlanta (United States) and Berlin (Germany) are known; "Mystery" is not and sorts last.
        assertEquals(
            listOf("1", "2", "3"),
            ServersQuery.sort(nodes, NodeSortMode.COUNTRY, reverse = false).map { it.id }
        )
    }

    // ------------------------------------------------------------------ country grouping

    @Test
    fun countryGroupsSortKnownCountriesFirstAndAlphabeticallyInside() {
        val nodes = listOf(
            profile("mystery", name = "Mystery node"),
            profile("berlin", name = "\uD83C\uDDE9\uD83C\uDDEA Berlin 02"),
            profile("frankfurt", name = "\uD83C\uDDE9\uD83C\uDDEA Frankfurt 01"),
            profile("atlanta", name = "\uD83C\uDDFA\uD83C\uDDF8 Atlanta 01")
        )
        val groups = ServersQuery.groupByCountry(nodes)
        assertEquals(listOf("DE", "US", ""), groups.map { it.country.code })
        assertEquals(listOf("berlin", "frankfurt"), groups.first().profiles.map { it.id })
    }

    // ------------------------------------------------------------------ helpers

    @Test
    fun stripFlagRemovesOnlyARealLeadingFlag() {
        assertEquals("Frankfurt 01", ServersQuery.stripFlag("\uD83C\uDDE9\uD83C\uDDEA Frankfurt 01"))
        assertEquals("No route", ServersQuery.stripFlag("No route"))
        assertEquals("", ServersQuery.stripFlag("   "))
    }

    @Test
    fun measuredLatencyIsNeverNegativeAndFailuresReadAsUnmeasured() {
        val node = profile("m")
        assertEquals(0, ServersQuery.measuredMs(node, mapOf("m" to result("m", -12.0))))
        assertEquals(120, ServersQuery.measuredMs(node, mapOf("m" to result("m", 120.4))))
        assertEquals(0, ServersQuery.measuredMs(node, mapOf("m" to result("m", 0.0, success = 0))))
        assertEquals(0, ServersQuery.measuredMs(node, emptyMap()))
        assertTrue(ServersQuery.hasFailedMeasurement(node, mapOf("m" to result("m", 0.0, success = 0))))
        assertFalse(ServersQuery.hasFailedMeasurement(node, mapOf("m" to result("m", 80.0))))
    }
}
