package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SHADE_DECK_V273 — the shade's route control, on the host JVM.
 *
 * The two failures this deck was built to end are both *navigation* failures, so both are
 * assertions rather than screenshots:
 *
 *  1. the deck can never draw more rows than a shade item can hold (chrome and data together), so
 *     SystemUI has nothing left to clip;
 *  2. every route in a library is reachable by walking the deck the way a reader walks it —
 *     floor, subscription, page — which is what the old studio could not do for any subscription
 *     other than the live one.
 */
class ShadeDeckV273Test {

    private fun route(id: String, name: String, sourceId: String, sourceName: String) =
        CycleRoute(id = id, name = name, sourceId = sourceId, sourceName = sourceName)

    /** Four subscriptions, forty-one servers: the shape that broke the studio. */
    private val library: List<CycleRoute> = buildList {
        repeat(18) { index -> add(route("a$index", "A-$index", "sub-a", "Provider A")) }
        repeat(11) { index -> add(route("b$index", "B-$index", "sub-b", "Provider B")) }
        repeat(7) { index -> add(route("c$index", "C-$index", "sub-c", "Provider C")) }
        repeat(5) { index -> add(route("m$index", "Home $index", "manual", "Manual")) }
    }

    private val chrome = NotificationRoutePicker.Chrome(
        serversHeading = "Servers",
        sourcesHeading = "Subscriptions",
        previous = "Previous page",
        next = "Next page",
        openApp = "Open MarbleNG",
        serverCount = { count -> if (count == 1) "1 server" else "$count servers" },
        pageLabel = { page, pages -> "$page/$pages" }
    )

    private fun view(
        routes: List<CycleRoute> = library,
        currentId: String = "a3",
        currentSourceId: String = "sub-a",
        state: ShadeDeckState = ShadeDeckState()
    ) = ShadeDeck.view(
        routes = routes,
        currentId = currentId,
        currentSourceId = currentSourceId,
        state = state,
        chrome = chrome,
        pingLabels = emptyMap()
    )

    private fun servers(rows: List<NotificationRoutePicker.PickerRow>) =
        rows.filter { it.kind == NotificationRoutePicker.RowKind.SERVER }

    private fun controls(rows: List<NotificationRoutePicker.PickerRow>) =
        rows.filter { it.kind == NotificationRoutePicker.RowKind.CONTROL }

    // -------------------------------------------------------------------------------------
    // The budget
    // -------------------------------------------------------------------------------------

    @Test
    fun `no state of any library can push the deck past its row ceiling`() {
        val rowsSettings = listOf(2, 4, 6, 8)
        for (rows in rowsSettings) {
            for (floor in ShadeFloor.entries) {
                for (scope in listOf("", "sub-a", "sub-b", "manual")) {
                    for (page in 0..8) {
                        val drawn = view(
                            state = ShadeDeckState(
                                floor = floor,
                                scopeSourceId = scope,
                                page = page,
                                rows = rows
                            )
                        ).rows
                        assertTrue(
                            "floor=$floor scope=$scope page=$page rows=$rows drew ${drawn.size}",
                            drawn.size <= ShadeDeck.MAX_ROWS
                        )
                        // The geometry the pager and the repository read is the row count drawn.
                        val geometry = ShadeDeck.layout(
                            library, "a3", "sub-a",
                            ShadeDeckState(floor = floor, scopeSourceId = scope, page = page, rows = rows)
                        )
                        assertEquals(drawn.size, geometry.rowsUsed)
                    }
                }
            }
        }
    }

    @Test
    fun `chrome is counted before servers are handed their rows`() {
        // One subscription, six servers, the largest row setting: heading + open-app are the only
        // chrome, so six servers fit and the deck draws no pager it does not need.
        val single = (1..6).map { route("s$it", "Node $it", "sub-a", "Provider A") }
        val eight = view(
            routes = single,
            state = ShadeDeckState(rows = 8, page = 0)
        )
        assertEquals(6, servers(eight.rows).size)
        assertEquals(ShadeDeck.MAX_ROWS, eight.rows.size)
        assertFalse("no pager where six rows hold six servers", eight.layout.hasPager)

        // The same budget with a second subscription adds the floor row, and the servers lose
        // one — to the *pager*, which the row count then has to make room for again.
        val twoSources = single + route("x1", "Elsewhere", "sub-b", "Provider B")
        val withFloor = view(routes = twoSources, state = ShadeDeckState(rows = 8, page = 0))
        assertEquals(4, servers(withFloor.rows).size)
        assertTrue(withFloor.layout.hasPager)
        assertEquals(ShadeDeck.MAX_ROWS, withFloor.rows.size)
    }

    // -------------------------------------------------------------------------------------
    // The reachability promise
    // -------------------------------------------------------------------------------------

    @Test
    fun `every route in the library is reachable by walking the deck`() {
        val reached = ShadeDeck.reachableRouteIds(library, "a3", "sub-a", rows = 2)
        assertEquals(library.map { it.id }.toSet(), reached.toSet())
        assertEquals("no route is reachable twice", reached.size, reached.toSet().size)
    }

    @Test
    fun `every subscription lists exactly its own servers, page by page`() {
        for (sourceId in listOf("sub-a", "sub-b", "sub-c", "manual")) {
            val expected = library.filter { it.sourceId == sourceId }.map { it.id }
            val seen = mutableListOf<String>()
            var page = 0
            while (true) {
                val drawn = view(
                    state = ShadeDeckState(
                        floor = ShadeFloor.SERVERS,
                        scopeSourceId = sourceId,
                        page = page,
                        rows = 4
                    )
                )
                assertEquals("the floor stays on $sourceId", sourceId, drawn.layout.scopeSourceId)
                seen += servers(drawn.rows).map { it.payloadId }
                if (drawn.page >= drawn.pageCount - 1) break
                page += 1
            }
            assertEquals("subscription $sourceId is listed completely and once", expected, seen)
        }
    }

    @Test
    fun `one pager row reaches every page of a deep subscription`() {
        // Eighteen servers at the default four-row preference: chrome (heading + pager + open) is
        // three rows, so four fit — five pages, all reachable from one wrapping row.
        val state = ShadeDeckState(floor = ShadeFloor.SERVERS, scopeSourceId = "sub-a", rows = 4)
        val geometry = ShadeDeck.layout(library, "a3", "sub-a", state)
        assertEquals(18, geometry.itemCount)
        assertTrue("the pager must exist for a five-page list", geometry.hasPager)

        val visited = mutableSetOf(0)
        var page = 0
        repeat(geometry.pageCount - 1) {
            page = (page + 1) % geometry.pageCount
            visited += page
        }
        assertEquals(
            "walking the one next row covers every page",
            (0 until geometry.pageCount).toSet(),
            visited
        )
        // …and the row is one control row, not two.
        val pagerRows = controls(
            view(state = state.copy(page = 0)).rows
        ).filter { it.actionId == ShadeDeck.ACTION_NEXT }
        assertEquals(1, pagerRows.size)
        assertEquals("1/5", pagerRows.first().meta)
    }

    // -------------------------------------------------------------------------------------
    // Floors, opening a subscription, and the live route
    // -------------------------------------------------------------------------------------

    @Test
    fun `the sources floor opens a subscription instead of dialling its first server`() {
        val drawn = view(state = ShadeDeckState(floor = ShadeFloor.SOURCES))
        val subscriptions = drawn.rows.filter {
            it.kind == NotificationRoutePicker.RowKind.SUBSCRIPTION
        }
        assertEquals(listOf("Provider A", "Provider B", "Provider C", "Manual"), subscriptions.map { it.label })
        assertEquals(listOf("18 servers", "11 servers", "7 servers", "5 servers"), subscriptions.map { it.meta })
        // The live source is marked; none of them is a route selection.
        assertEquals(1, subscriptions.count { it.active })
        assertTrue(subscriptions.first { it.active }.payloadSourceId == "sub-a")
        subscriptions.forEach { row ->
            assertFalse(
                "a subscription row must not be a select: row",
                NotificationRoutePicker.isSelect(row.actionId)
            )
            assertTrue(ShadeDeck.isSourceAction(row.actionId))
            assertEquals(row.payloadSourceId, ShadeDeck.sourceActionId(row.actionId))
        }
    }

    @Test
    fun `a scoped-away deck offers the way back to the live subscription`() {
        val drawn = view(state = ShadeDeckState(scopeSourceId = "sub-c", rows = 4))
        assertEquals("sub-c", drawn.layout.scopeSourceId)
        val live = controls(drawn.rows).firstOrNull { it.actionId == ShadeDeck.ACTION_LIVE }
        assertTrue("a deck standing in another subscription must offer the live one", live != null)
        assertTrue(live!!.label.contains("Provider A"))
        // Standing on the live subscription needs no such row.
        val atHome = view(state = ShadeDeckState(rows = 4))
        assertTrue(controls(atHome.rows).none { it.actionId == ShadeDeck.ACTION_LIVE })
    }

    @Test
    fun `the live route is marked where it is, never promoted`() {
        val drawn = view(state = ShadeDeckState(scopeSourceId = "sub-a", rows = 4, page = 0))
        val rows = servers(drawn.rows)
        assertEquals(listOf("a0", "a1", "a2", "a3"), rows.map { it.payloadId })
        assertEquals("a3", rows.single { it.active }.payloadId)
        // …and the selection action still round-trips to the route it names.
        rows.forEach { row ->
            assertTrue(NotificationRoutePicker.isSelect(row.actionId))
            assertEquals(row.payloadId, NotificationRoutePicker.selectProfileId(row.actionId))
        }
    }

    @Test
    fun `a stale page or an unknown scope resolves instead of lying`() {
        val shrunk = listOf(route("a1", "One", "sub-a", "Provider A"))
        val drawn = view(
            routes = shrunk,
            state = ShadeDeckState(scopeSourceId = "gone", page = 9, rows = 4)
        )
        assertEquals("sub-a", drawn.layout.scopeSourceId)
        assertEquals(0, drawn.layout.page)
        assertEquals(1, drawn.pageCount)
        val single = view(
            routes = listOf(route("b1", "Only", "sub-b", "Provider B")),
            state = ShadeDeckState(scopeSourceId = "sub-a", rows = 4)
        )
        assertEquals("sub-b", single.layout.scopeSourceId)
        assertTrue("nothing is opened when the library shrank", servers(single.rows).isNotEmpty())
    }

    @Test
    fun `an empty library draws nothing at all`() {
        val drawn = view(routes = emptyList(), state = ShadeDeckState())
        assertTrue(drawn.rows.isEmpty())
        assertEquals(0, drawn.pageCount)
    }

    @Test
    fun `a ping label is printed only when it was measured`() {
        val labels = mapOf("a0" to "42 ms")
        val drawn = ShadeDeck.view(
            routes = library,
            currentId = "a3",
            currentSourceId = "sub-a",
            state = ShadeDeckState(scopeSourceId = "sub-a", rows = 2),
            chrome = chrome,
            pingLabels = labels
        )
        val rows = servers(drawn.rows)
        assertEquals("42 ms", rows.first { it.payloadId == "a0" }.meta)
        assertEquals("an unmeasured route prints nothing", "", rows.first { it.payloadId == "a1" }.meta)
    }

    @Test
    fun `a subscription row carries its plan when one was measured, and nothing when it was not`() {
        val plans = mapOf("sub-a" to "42% · in 6 days", "sub-c" to "91% · 3 days ago")
        val drawn = ShadeDeck.view(
            routes = library,
            currentId = "a3",
            currentSourceId = "sub-a",
            state = ShadeDeckState(floor = ShadeFloor.SOURCES, rows = 4),
            chrome = chrome,
            pingLabels = emptyMap(),
            sourcePlans = plans
        )
        val subscriptions = drawn.rows.filter {
            it.kind == NotificationRoutePicker.RowKind.SUBSCRIPTION
        }
        assertEquals(
            listOf(
                "18 servers · 42% · in 6 days",
                "11 servers",
                "7 servers · 91% · 3 days ago",
                "5 servers"
            ),
            subscriptions.map { it.meta }
        )
        // The plan is a suffix, never a replacement: the server count is what the row is *for*.
        subscriptions.forEach { assertTrue(it.meta.contains("servers")) }
        // A deck that was given no plans prints the count alone, exactly as V249 did.
        val bare = view(state = ShadeDeckState(floor = ShadeFloor.SOURCES, rows = 4))
        assertTrue(
            bare.rows.filter { it.kind == NotificationRoutePicker.RowKind.SUBSCRIPTION }
                .all { !it.meta.contains("·") }
        )
    }

    @Test
    fun `the verb of a subscription row round-trips through the control prefix`() {
        val action = ShadeDeck.sourceAction("sub/with odd:id")
        assertEquals("sub/with odd:id", ShadeDeck.sourceActionId(action))
        assertEquals(
            "sub/with odd:id",
            ShadeDeck.sourceIdFromVerb(NotificationRoutePicker.controlVerb(action))
        )
        assertTrue(ShadeDeck.isSourceVerb(NotificationRoutePicker.controlVerb(action)))
        assertFalse(ShadeDeck.isSourceVerb(NotificationRoutePicker.CONTROL_NEXT))
    }
}
