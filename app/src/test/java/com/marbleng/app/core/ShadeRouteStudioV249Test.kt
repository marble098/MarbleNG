package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SHADE_STUDIO_V249 — the shade's route control, on the host JVM.
 *
 * V238 gave the notification a list. A list is a good answer to "show me the servers of this
 * subscription" and a bad answer to "show me forty subscriptions": the jump rows eat the whole height
 * budget and nothing is tappable. The studio is the same rows with two floors, a page, and the
 * measurement each server already has — and every rule that decides what fits is here, because the
 * shade's height is a hard budget and a builder that guesses gets clipped by SystemUI.
 */
class ShadeRouteStudioV249Test {

    private fun route(id: String, name: String, sourceId: String, sourceName: String) =
        CycleRoute(id = id, name = name, sourceId = sourceId, sourceName = sourceName)

    private val library = listOf(
        route("a1", "Berlin 01", "sub-a", "Provider A"),
        route("a2", "Frankfurt 01", "sub-a", "Provider A"),
        route("a3", "Amsterdam 01", "sub-a", "Provider A"),
        route("a4", "Vienna 01", "sub-a", "Provider A"),
        route("b1", "Tokyo 01", "sub-b", "Provider B"),
        route("b2", "Seoul 01", "sub-b", "Provider B"),
        route("m1", "Home box", "manual", "Manual")
    )

    private val singleSource = listOf(
        route("a1", "Berlin 01", "sub-a", "Provider A"),
        route("a2", "Frankfurt 01", "sub-a", "Provider A")
    )

    private val deep = (1..9).map { index ->
        route("s$index", "Node $index", "sub-a", "Provider A")
    }

    /** The chrome is the caller's words: the studio decides what a row says, never how it is spelled. */
    private val chrome = NotificationRoutePicker.Chrome(
        serversHeading = "Servers",
        sourcesHeading = "Subscriptions",
        previous = "Previous page",
        next = "Next page",
        openApp = "Open MarbleNG",
        serverCount = { count -> if (count == 1) "1 server" else "$count servers" },
        pageLabel = { page, pages -> "$page/$pages" }
    )

    private fun studio(
        routes: List<CycleRoute> = library,
        currentId: String = "a2",
        currentSourceId: String = "sub-a",
        level: ShadeLevel = ShadeLevel.SERVERS,
        page: Int = 0,
        rowBudget: Int = NotificationRoutePicker.DEFAULT_STUDIO_ROWS,
        showPing: Boolean = true,
        pingLabels: Map<String, String> = emptyMap()
    ) = NotificationRoutePicker.studio(
        NotificationRoutePicker.StudioInput(
            routes = routes,
            currentId = currentId,
            currentSourceId = currentSourceId,
            level = level,
            page = page,
            rowBudget = rowBudget,
            showPing = showPing,
            pingLabels = pingLabels,
            chrome = chrome
        )
    )

    private fun servers(view: NotificationRoutePicker.StudioView) =
        view.rows.filter { it.kind == NotificationRoutePicker.RowKind.SERVER }

    private fun controls(view: NotificationRoutePicker.StudioView) =
        view.rows.filter { it.kind == NotificationRoutePicker.RowKind.CONTROL }

    @Test
    fun `the servers floor lists the live subscription and marks the live one`() {
        val view = studio()
        assertEquals(ShadeLevel.SERVERS, view.level)
        assertEquals("Provider A", view.heading)

        val heading = view.rows.first()
        assertEquals(NotificationRoutePicker.RowKind.SECTION, heading.kind)
        assertEquals("Provider A", heading.label)
        assertEquals("one page needs no page number", "", heading.meta)

        assertEquals(4, servers(view).size)
        assertTrue(servers(view).all { it.payloadSourceId == "sub-a" })
        val live = servers(view).single { it.active }
        assertEquals("a2", live.payloadId)
        assertEquals(1, servers(view).count { it.active })
        // Every selectable row is a real route of the library, and its action id round-trips to it.
        servers(view).forEach { row ->
            assertTrue(NotificationRoutePicker.isSelect(row.actionId))
            assertEquals(row.payloadId, NotificationRoutePicker.selectProfileId(row.actionId))
        }
    }

    @Test
    fun `a subscription with one server widens the floor to the whole library`() {
        // A floor with one row on it cannot move anything, so the studio shows the library instead of
        // a single name and a dead end.
        val view = studio(currentId = "m1", currentSourceId = "manual")
        val heading = view.rows.first()
        assertEquals("Servers", heading.label)
        assertEquals("1/2", heading.meta)
        assertEquals(4, servers(view).size)
        // The whole library, in the library's own order, from the top.
        assertEquals(listOf("a1", "a2", "a3", "a4"), servers(view).map { it.payloadId })
        val live = servers(view).filter { it.active }
        assertEquals("the live route is on a later page, so this page marks nothing", 0, live.size)
        assertTrue(controls(view).any { NotificationRoutePicker.controlVerb(it.actionId) == "next" })
    }

    @Test
    fun `the sources floor names every subscription with its server count`() {
        val view = studio(level = ShadeLevel.SOURCES)
        assertEquals(ShadeLevel.SOURCES, view.level)
        assertEquals("Subscriptions", view.heading)

        val sources = view.rows.filter { it.kind == NotificationRoutePicker.RowKind.SUBSCRIPTION }
        assertEquals(3, sources.size)
        assertEquals(listOf("Provider A", "Provider B", "Manual"), sources.map { it.label })
        assertEquals(listOf("4 servers", "2 servers", "1 server"), sources.map { it.meta })
        // Tapping a subscription lands on the server of it that is live, and on its first when none is:
        // exactly what the V234 subscription verb does, so the two controls cannot disagree.
        assertEquals("a2", sources[0].payloadId)
        assertTrue(sources[0].active)
        assertEquals("b1", sources[1].payloadId)
        assertEquals("m1", sources[2].payloadId)
        sources.forEach { row ->
            assertTrue(NotificationRoutePicker.isSelect(row.actionId))
            assertEquals(row.payloadId, NotificationRoutePicker.selectProfileId(row.actionId))
        }
    }

    @Test
    fun `one subscription has no second floor, so the studio stays on the servers`() {
        val view = studio(routes = singleSource, currentId = "a1", currentSourceId = "sub-a", level = ShadeLevel.SOURCES)
        assertEquals(ShadeLevel.SERVERS, view.level)
        assertFalse(
            "no floor control when there is nowhere else to stand",
            controls(view).any { NotificationRoutePicker.controlVerb(it.actionId) == "level" }
        )
    }

    @Test
    fun `the floor control exists exactly when there is a second floor`() {
        val levelVerbs = controls(studio()).filter {
            NotificationRoutePicker.controlVerb(it.actionId) == NotificationRoutePicker.CONTROL_LEVEL
        }
        assertEquals(1, levelVerbs.size)
        // The control is named for where it goes, not for where the user is.
        assertEquals("Subscriptions", levelVerbs.single().label)

        val fromSources = controls(studio(level = ShadeLevel.SOURCES)).filter {
            NotificationRoutePicker.controlVerb(it.actionId) == NotificationRoutePicker.CONTROL_LEVEL
        }
        assertEquals("Servers", fromSources.single().label)
    }

    @Test
    fun `a library deeper than the budget is paged, never truncated`() {
        val budget = 4
        val first = studio(routes = deep, currentId = "s1", rowBudget = budget)
        assertEquals(3, first.pageCount)
        assertEquals(0, first.page)
        assertEquals(budget, servers(first).size)
        assertEquals("1/3", first.rows.first().meta)
        assertTrue(controls(first).any { NotificationRoutePicker.controlVerb(it.actionId) == "next" })
        assertFalse(controls(first).any { NotificationRoutePicker.controlVerb(it.actionId) == "previous" })

        val middle = studio(routes = deep, currentId = "s1", rowBudget = budget, page = 1)
        assertEquals(1, middle.page)
        assertEquals("2/3", middle.rows.first().meta)
        assertEquals(
            setOf(
                NotificationRoutePicker.CONTROL_PREVIOUS,
                NotificationRoutePicker.CONTROL_NEXT
            ),
            controls(middle).map { NotificationRoutePicker.controlVerb(it.actionId) }.toSet()
        )

        val last = studio(routes = deep, currentId = "s1", rowBudget = budget, page = 2)
        assertEquals(2, last.page)
        assertEquals(1, servers(last).size)
        assertTrue(controls(last).any { NotificationRoutePicker.controlVerb(it.actionId) == "previous" })
        assertFalse(controls(last).any { NotificationRoutePicker.controlVerb(it.actionId) == "next" })

        // Every route of the library is reachable: paging moves the window, it does not drop rows.
        val seen = (servers(first) + servers(middle) + servers(last)).map { it.payloadId }
        assertEquals(deep.map { it.id }.toSet(), seen.toSet())
        assertEquals(deep.size, seen.size)
    }

    @Test
    fun `a page that no longer exists resolves to the last real one`() {
        assertEquals(1, NotificationRoutePicker.clampPage(9, 5, 4))
        assertEquals(0, NotificationRoutePicker.clampPage(-3, 5, 4))
        assertEquals(0, NotificationRoutePicker.clampPage(0, 0, 4))
        assertEquals(2, NotificationRoutePicker.clampPage(2, 9, 4))

        // The library shrank between two taps: the shade shows the last page that exists rather than
        // an empty one, which is what a stale page number would draw.
        val stale = studio(routes = deep, currentId = "s1", rowBudget = 4, page = 99)
        assertEquals(2, stale.page)
        assertEquals(1, servers(stale).size)
        assertTrue(stale.rows.isNotEmpty())
    }

    @Test
    fun `the row budget is snapped, so the page counter and the drawing agree`() {
        assertEquals(5, NotificationRoutePicker.pageCount(9, 2))
        assertEquals(1, NotificationRoutePicker.pageCount(4, 4))
        assertEquals(0, NotificationRoutePicker.pageCount(0, 4))
        assertEquals(0, NotificationRoutePicker.pageCount(-3, 4))
        // A stored 5 is not one of the four counts the settings page offers; the studio draws the same
        // number of rows the page counter assumes, so "next page" is never a button that does nothing.
        val snapped = studio(routes = deep, currentId = "s1", rowBudget = 5)
        assertEquals(4, servers(snapped).size)
        assertEquals(3, snapped.pageCount)
        val snappedHigh = studio(routes = deep, currentId = "s1", rowBudget = 99)
        assertTrue(servers(snappedHigh).size <= NotificationRoutePicker.MAX_STUDIO_ROWS)
        assertTrue(snappedHigh.rows.isNotEmpty())
    }

    @Test
    fun `the measured ping is the word of the caller, and an unmeasured route says nothing`() {
        val withPing = studio(pingLabels = mapOf("a1" to "42 ms", "a3" to "118 ms"))
        val metas = servers(withPing).associate { it.payloadId to it.meta }
        assertEquals("42 ms", metas["a1"])
        assertEquals("118 ms", metas["a3"])
        // A route that was never measured has no meta at all: printing "— ms" down a column is noise
        // the user cannot turn off, and it reads as a measurement of nothing.
        assertEquals("", metas["a2"])
        assertEquals("", metas["a4"])

        val withoutPing = studio(showPing = false, pingLabels = mapOf("a1" to "42 ms"))
        assertTrue(servers(withoutPing).all { it.meta.isEmpty() })
    }

    @Test
    fun `the way back into the app is always the last row`() {
        listOf(
            studio(),
            studio(level = ShadeLevel.SOURCES),
            studio(routes = singleSource, currentId = "a1", currentSourceId = "sub-a"),
            studio(routes = deep, currentId = "s1", rowBudget = 2, page = 1)
        ).forEach { view ->
            val last = view.rows.last()
            assertEquals(NotificationRoutePicker.RowKind.OPEN_APP, last.kind)
            assertEquals("Open MarbleNG", last.label)
            assertTrue(last.actionId.startsWith(NotificationRoutePicker.OPEN_APP_PREFIX))
        }
    }

    @Test
    fun `an empty library draws no rows at all`() {
        val empty = studio(routes = emptyList())
        assertTrue(empty.rows.isEmpty())
        assertEquals(0, empty.pageCount)
        assertEquals(0, empty.page)
        assertEquals("", empty.heading)

        val blank = studio(routes = listOf(route("", "No id", "sub-a", "Provider A")))
        assertTrue("a route with no id is not a route", blank.rows.isEmpty())
    }

    @Test
    fun `the control verbs are previous, next and level and nothing else`() {
        assertEquals(
            NotificationRoutePicker.CONTROL_PREVIOUS,
            NotificationRoutePicker.controlVerb(NotificationRoutePicker.CONTROL_PREFIX + "previous")
        )
        assertEquals(
            NotificationRoutePicker.CONTROL_NEXT,
            NotificationRoutePicker.controlVerb(NotificationRoutePicker.CONTROL_PREFIX + "next")
        )
        assertEquals(
            NotificationRoutePicker.CONTROL_LEVEL,
            NotificationRoutePicker.controlVerb(NotificationRoutePicker.CONTROL_PREFIX + "level")
        )
        assertEquals("", NotificationRoutePicker.controlVerb("select:a1"))
        assertEquals("", NotificationRoutePicker.controlVerb("open:all"))
        assertEquals("", NotificationRoutePicker.controlVerb(""))
        assertFalse(NotificationRoutePicker.isControl("select:a1"))
        assertFalse(NotificationRoutePicker.isSelect(NotificationRoutePicker.CONTROL_PREFIX + "next"))
    }

    @Test
    fun `the two floors are named by id and the level flips between exactly them`() {
        assertEquals(ShadeLevel.SERVERS, ShadeLevel.fromId("servers"))
        assertEquals(ShadeLevel.SOURCES, ShadeLevel.fromId("SOURCES"))
        assertEquals(ShadeLevel.SERVERS, ShadeLevel.fromId("bogus"))
        assertEquals(ShadeLevel.SERVERS, ShadeLevel.fromId(null))
        assertEquals(ShadeLevel.SOURCES, ShadeLevel.other(ShadeLevel.SERVERS))
        assertEquals(ShadeLevel.SERVERS, ShadeLevel.other(ShadeLevel.SOURCES))
        assertEquals(
            ShadeLevel.SERVERS,
            ShadeLevel.other(ShadeLevel.other(ShadeLevel.SERVERS))
        )
        assertEquals("studio", ShadePickerStyle.DEFAULT.id)
        assertEquals(ShadePickerStyle.STUDIO, ShadePickerStyle.fromId("Studio"))
        assertEquals(ShadePickerStyle.LIST, ShadePickerStyle.fromId("list"))
        // An unknown style falls back to the studio, not to nothing: the one setting here whose
        // absence the user notices at a glance.
        assertEquals(ShadePickerStyle.STUDIO, ShadePickerStyle.fromId("bogus"))
        assertEquals(ShadePickerStyle.STUDIO, ShadePickerStyle.fromId(null))
    }

    @Test
    fun `the V238 list keeps its own shape beside the studio`() {
        val rows = NotificationRoutePicker.build(
            NotificationRoutePicker.Input(
                routes = library,
                currentId = "a2",
                currentSourceId = "sub-a"
            )
        )
        assertEquals(NotificationRoutePicker.RowKind.SECTION, rows.first().kind)
        assertTrue(rows.size <= NotificationRoutePicker.DEFAULT_CAP)
        assertFalse("the list has no floors and no pages", rows.any { it.kind == NotificationRoutePicker.RowKind.CONTROL })
        assertTrue(rows.any { it.kind == NotificationRoutePicker.RowKind.SUBSCRIPTION })
        assertTrue(rows.all { it.meta.isEmpty() })
    }

    @Test
    fun `a row label stays on one line and inside the budget of the row`() {
        // A RemoteViews row may not be taller than its text: a newline in a server name would make
        // the shade draw a row SystemUI clips, and a 400-character name would make it unreadable.
        assertEquals("Frankfurt 01", NotificationRoutePicker.rowLabel("  Frankfurt\n01  "))
        assertEquals("one line", NotificationRoutePicker.rowLabel("one\nline"))
        assertEquals(64, NotificationRoutePicker.rowLabel("x".repeat(200)).length)
        assertEquals("", NotificationRoutePicker.rowLabel("   "))
    }
}
