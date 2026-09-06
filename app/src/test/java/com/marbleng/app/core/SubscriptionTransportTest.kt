package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SUBSCRIPTION_REACH_V145 — a subscription link must load whether the VPN is on or off.
 *
 * The reported defect was two-sided: some providers only answered over the physical underlay
 * (so their links "only worked with the VPN off", because a connected MarbleNG refused to try
 * direct) and some only answered from outside the country (so they "only worked with the VPN
 * on", because a disconnected MarbleNG had no tunnel to try). The fetcher now always attempts
 * both transports and only reorders them; these tests pin exactly that.
 *
 * The direct pass targets a closed loopback port, so the tests are offline-safe: it fails fast
 * and deterministically, standing in for "this URL is unreachable on this transport".
 */
class SubscriptionTransportTest {

    private val unreachable = "https://127.0.0.1:1/sub.txt"

    @Test
    fun socksIsTriedFirstWhenTheCallerPrefersIt() {
        val calls = mutableListOf<String>()
        val payload = DpiAwareFetcher.fetch(
            url = unreachable,
            maxBytes = 4096,
            iranActive = false,
            allowDirect = true,
            preferSocks = true,
            throughSocks = { candidate, agent ->
                calls += "socks:$candidate"
                assertTrue("the browser UA leads every wave", agent.isNotBlank())
                DpiAwareFetcher.Payload(text = "vless://tunnel")
            }
        )
        assertEquals("vless://tunnel", payload.text)
        assertEquals(1, calls.size)
    }

    @Test
    fun aDeadDirectPathStillFallsBackToTheTunnel() {
        // "This subscription only loads with the VPN on": direct fails, SOCKS must be reached.
        val calls = mutableListOf<String>()
        val payload = DpiAwareFetcher.fetch(
            url = unreachable,
            maxBytes = 4096,
            iranActive = true,
            allowDirect = true,
            preferSocks = false,
            throughSocks = { candidate, _ ->
                calls += candidate
                DpiAwareFetcher.Payload(text = "vmess://through-tunnel")
            }
        )
        assertEquals("vmess://through-tunnel", payload.text)
        assertTrue(calls.isNotEmpty())
    }

    @Test
    fun aDeadTunnelStillFallsBackToTheUnderlay() {
        // "This subscription only loads with the VPN off": the SOCKS bridge is broken, so the
        // direct transport must still be attempted. It is unreachable here, so the call must
        // surface a failure rather than silently returning an empty payload.
        var socksAttempts = 0
        val failure = runCatching {
            DpiAwareFetcher.fetch(
                url = unreachable,
                maxBytes = 4096,
                iranActive = false,
                allowDirect = true,
                preferSocks = true,
                throughSocks = { _, _ ->
                    socksAttempts += 1
                    error("socks bridge is down")
                }
            )
        }
        assertTrue("every transport failed, so the fetch must throw", failure.isFailure)
        assertTrue("the tunnel was attempted", socksAttempts > 0)
    }

    @Test
    fun githubRawAlwaysCarriesItsMirror() {
        val urls = DpiAwareFetcher.candidateUrls(
            "https://raw.githubusercontent.com/user/repo/main/sub.txt"
        )
        assertEquals(2, urls.size)
        assertTrue(urls[1].startsWith("https://cdn.jsdelivr.net/gh/"))
    }
}
