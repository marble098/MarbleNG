package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
