package com.marbleng.app.model

import com.marbleng.app.core.HomePingScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MARBLE_HOME_PING_ROUTE_GROUP_V146 — the Home pulse icon measures one group, never the whole
 * library. The resolution rule is pure ([HomePingScope]) so it is pinned here: the subscription
 * of the route on the page is the scope; anything that resolved without a source degrades to
 * the Manual bucket instead of silently sweeping every subscription.
 */
class HomePingScopeTest {

    private fun route(subscriptionId: String = "sub-1") = ProxyProfile(
        id = "node-1",
        name = "Germany • 01",
        scheme = "vless",
        raw = "",
        configJson = "",
        host = "192.0.2.1",
        port = 443,
        subscriptionId = subscriptionId
    )

    @Test
    fun theRouteOnThePageDefinesTheScope() {
        assertEquals("sub-1", HomePingScope.sourceIdFor(route("sub-1")))
        assertEquals("sub-9", HomePingScope.sourceIdFor(route("sub-9")))
    }

    @Test
    fun aManualRoutePingsTheManualBucket() {
        assertEquals("manual", HomePingScope.sourceIdFor(route("manual")))
        assertEquals("manual", HomePingScope.sourceIdFor(route("")))
    }

    @Test
    fun noRouteNeverDegradesIntoAFullSweep() {
        assertEquals("manual", HomePingScope.sourceIdFor(null))
    }
}
