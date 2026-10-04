package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePingScopeTest {
    @Test
    fun homePingUsesTheRouteSourceRatherThanAnotherPagesFilter() {
        assertEquals("sub-42", HomePingScope.sourceId("sub-42"))
        assertEquals("sub-42", HomePingScope.sourceId("  sub-42  "))
    }

    @Test
    fun aManualOrMissingRouteSourceUsesTheManualBucket() {
        assertEquals("manual", HomePingScope.sourceId("manual"))
        assertEquals("manual", HomePingScope.sourceId(""))
        assertEquals("manual", HomePingScope.sourceId("  "))
        assertEquals("manual", HomePingScope.sourceId(null))
    }
}
