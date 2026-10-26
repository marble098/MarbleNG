package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — "at least three connect buttons, each with its own
 * function". A skin over one click would fail this test.
 */
class ConnectDeckActionV233Test {

    @Test
    fun atLeastThreeButtonsWithIndependentLightsAndBehaviours() {
        assertTrue(ConnectDeckCatalog.ordered.size >= 3)
        assertTrue(ConnectDeckCatalog.isIndependent())
        assertEquals(3, ConnectDeckCatalog.ordered.map { it.light }.toSet().size)
        assertEquals(3, ConnectDeckCatalog.ordered.map { it.behavior }.toSet().size)
        assertEquals(3, ConnectDeckCatalog.ordered.map { it.titleKey }.toSet().size)
        assertEquals(3, ConnectDeckCatalog.ordered.map { it.hintKey }.toSet().size)
    }

    @Test
    fun eachButtonNamesItsOwnRepositoryEntryPoint() {
        assertEquals(
            "reconnect_last_or_auto",
            ConnectDeckCatalog.behaviorOf("quick")
        )
        assertEquals(
            "auto_selector_winner",
            ConnectDeckCatalog.behaviorOf("smart")
        )
        assertEquals(
            "local_proxy_only",
            ConnectDeckCatalog.behaviorOf("light")
        )
    }

    @Test
    fun idsAreStableAndMalformedIdsFallBackToTheFirstButton() {
        ConnectDeckAction.entries.forEach { action ->
            assertEquals(action, ConnectDeckAction.fromId(action.id))
            assertEquals(action, ConnectDeckAction.fromId(" ${action.id.uppercase()} "))
        }
        assertEquals(ConnectDeckAction.QUICK, ConnectDeckAction.fromId(""))
        assertEquals(ConnectDeckAction.QUICK, ConnectDeckAction.fromId(null))
        assertEquals(ConnectDeckAction.QUICK, ConnectDeckAction.fromId("something-else"))
    }

    @Test
    fun theOrderIsQuickSmartLight() {
        assertEquals(
            listOf(ConnectDeckAction.QUICK, ConnectDeckAction.SMART, ConnectDeckAction.LIGHT),
            ConnectDeckCatalog.ordered
        )
    }
}
