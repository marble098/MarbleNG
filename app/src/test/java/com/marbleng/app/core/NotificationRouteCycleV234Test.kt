package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_NOTIFICATION_SWITCH_V234 — "the next server" and "the next subscription", on the host JVM.
 *
 * Every rule this test pins used to be a decision nobody could check without a phone in Persian: a
 * cycle that leaves the tunnel where it was, a subscription with one node that traps the verb, a
 * walk that depends on the latency ranking instead of the library order. The walk is pure Kotlin so
 * all of it is testable here.
 */
class NotificationRouteCycleV234Test {

    private fun route(id: String, name: String, sourceId: String, sourceName: String) =
        CycleRoute(id = id, name = name, sourceId = sourceId, sourceName = sourceName)

    private val library = listOf(
        route("a1", "Berlin 01", "sub-a", "Provider A"),
        route("a2", "Frankfurt 01", "sub-a", "Provider A"),
        route("a3", "Amsterdam 01", "sub-a", "Provider A"),
        route("b1", "Tokyo 01", "sub-b", "Provider B"),
        route("b2", "Seoul 01", "sub-b", "Provider B"),
        route("m1", "Home box", "manual", "Manual")
    )

    @Test
    fun aSubscriptionWalkStaysInsideTheLiveSubscriptionAndWraps() {
        val second = NotificationRouteCycle.next(
            NotificationCycleKind.SERVER, library, "a1", "sub-a"
        )
        assertEquals("a2", second?.id)

        val third = NotificationRouteCycle.next(
            NotificationCycleKind.SERVER, library, "a2", "sub-a"
        )
        assertEquals("a3", third?.id)

        // The last node of the subscription returns to its first: a cycle, not a ramp.
        val wrapped = NotificationRouteCycle.next(
            NotificationCycleKind.SERVER, library, "a3", "sub-a"
        )
        assertEquals("a1", wrapped?.id)
    }

    @Test
    fun aSingleNodeSubscriptionFallsBackToTheWholeLibrary() {
        // `manual` holds one route: staying inside it would make the verb dead.
        val next = NotificationRouteCycle.next(NotificationCycleKind.SERVER, library, "m1", "manual")
        assertEquals("a1", next?.id)

        // And the last route of the library wraps to the first, skipping nothing silently.
        val wrapped = NotificationRouteCycle.next(
            NotificationCycleKind.SERVER, library, "m1", "manual"
        )
        assertTrue(wrapped?.id != "m1")
    }

    @Test
    fun aLibraryWithOneRouteHasNothingToSwitchTo() {
        val single = listOf(route("only", "Only node", "manual", "Manual"))
        assertNull(NotificationRouteCycle.next(NotificationCycleKind.SERVER, single, "only", "manual"))
        assertNull(
            NotificationRouteCycle.next(NotificationCycleKind.SUBSCRIPTION, single, "only", "manual")
        )
        assertNull(NotificationRouteCycle.next(NotificationCycleKind.SERVER, emptyList(), "", ""))
    }

    @Test
    fun theSubscriptionWalkFollowsTheLibraryOrderAndWraps() {
        val fromA = NotificationRouteCycle.next(
            NotificationCycleKind.SUBSCRIPTION, library, "a2", "sub-a"
        )
        assertEquals("b1", fromA?.id)
        assertEquals("Provider B", fromA?.sourceName)

        val fromB = NotificationRouteCycle.next(
            NotificationCycleKind.SUBSCRIPTION, library, "b2", "sub-b"
        )
        assertEquals("m1", fromB?.id)

        // Manual is the last source, so the next subscription is the first one again.
        val wrapped = NotificationRouteCycle.next(
            NotificationCycleKind.SUBSCRIPTION, library, "m1", "manual"
        )
        assertEquals("a1", wrapped?.id)
    }

    @Test
    fun aLibraryWithOneSubscriptionStillMoves() {
        val oneSource = listOf(
            route("a1", "Berlin 01", "sub-a", "Provider A"),
            route("a2", "Frankfurt 01", "sub-a", "Provider A")
        )
        // The subscription verb degrades into the server verb instead of doing nothing.
        assertEquals(
            "a2",
            NotificationRouteCycle.next(NotificationCycleKind.SUBSCRIPTION, oneSource, "a1", "sub-a")?.id
        )
    }

    @Test
    fun theCurrentSourceComesFromTheLiveRouteWhenTheCallerOnlyKnowsTheId() {
        assertEquals("sub-b", NotificationRouteCycle.currentSource(library, "b2", ""))
        assertEquals("sub-a", NotificationRouteCycle.currentSource(library, "a1", "sub-a"))
        // A source id that no longer exists falls back to whatever the route itself says.
        assertEquals("sub-b", NotificationRouteCycle.currentSource(library, "b1", "gone"))
        assertEquals("", NotificationRouteCycle.currentSource(emptyList(), "", ""))
    }

    @Test
    fun sourcesKeepTheFirstAppearanceOrderAndDuplicateIdsAreDropped() {
        val messy = listOf(
            route("b2", "Seoul 01", "sub-b", "Provider B"),
            route("a1", "Berlin 01", "sub-a", "Provider A"),
            route("b2", "Seoul (duplicate)", "sub-b", "Provider B"),
            route("", "ghost", "sub-a", "Provider A")
        )
        assertEquals(listOf("sub-b", "sub-a"), NotificationRouteCycle.sources(messy))
        val compact = NotificationRouteCycle.compact(messy)
        assertEquals(listOf("b2", "a1"), compact.map { it.id })
        assertEquals("Seoul 01", compact.first().name)
    }

    @Test
    fun theSecondLineNeverStutters() {
        assertEquals("Berlin 01 · Provider A", NotificationRouteCycle.routeLine("Berlin 01", "Provider A"))
        assertEquals("Berlin 01", NotificationRouteCycle.routeLine("Berlin 01", "Berlin 01"))
        assertEquals("Berlin 01", NotificationRouteCycle.routeLine(" Berlin 01 ", "  "))
        assertEquals("Manual", NotificationRouteCycle.routeLine("", "Manual"))
        assertEquals("", NotificationRouteCycle.routeLine("", ""))
    }
}
