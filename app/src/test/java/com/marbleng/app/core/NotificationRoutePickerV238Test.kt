package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_NOTIFICATION_PICKER_V238 — the shade's named-route picker, on the host JVM.
 *
 * The picker is the answer to "کنترل کامل" from the notification: the V234 verbs move to *some*
 * neighbour; the picker moves to *this* server. These tests pin the row budget, the live
 * subscription's priority, the round-trip of a selection id, and the honest cap.
 */
class NotificationRoutePickerV238Test {

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

    @Test
    fun `the servers of the live subscription come first and the live one is marked`() {
        val rows = NotificationRoutePicker.build(
            NotificationRoutePicker.Input(
                routes = library,
                currentId = "a2",
                currentSourceId = "sub-a"
            )
        )
        assertEquals(NotificationRoutePicker.RowKind.SECTION, rows.first().kind)
        assertEquals("Provider A", rows.first().label)
        val servers = rows.filter { it.kind == NotificationRoutePicker.RowKind.SERVER }
        assertTrue(servers.isNotEmpty())
        assertTrue(
            "every server row belongs to the live subscription",
            servers.all { it.payloadSourceId == "sub-a" }
        )
        val live = servers.firstOrNull { it.active }
        assertTrue(live != null && live.payloadId == "a2")
    }

    @Test
    fun `a selection id round-trips to its profile`() {
        val rows = NotificationRoutePicker.build(
            NotificationRoutePicker.Input(routes = library, currentId = "a2", currentSourceId = "sub-a")
        )
        val selectable = rows.filter { NotificationRoutePicker.isSelect(it.actionId) }
        assertTrue(selectable.isNotEmpty())
        assertEquals("a2", NotificationRoutePicker.selectProfileId(selectable.first { it.active }.actionId))
    }

    @Test
    fun `other subscriptions jump with a representative server`() {
        val rows = NotificationRoutePicker.build(
            NotificationRoutePicker.Input(routes = library, currentId = "a1", currentSourceId = "sub-a")
        )
        val jumps = rows.filter { it.kind == NotificationRoutePicker.RowKind.SUBSCRIPTION }
        assertEquals(setOf("sub-b", "manual"), jumps.map { it.payloadSourceId }.toSet())
        assertTrue(jumps.all { NotificationRoutePicker.isSelect(it.actionId) })
    }

    @Test
    fun `the picker never exceeds its row cap and ends with the open-app row when there is room`() {
        val rows = NotificationRoutePicker.build(
            NotificationRoutePicker.Input(routes = library, currentId = "a1", currentSourceId = "sub-a")
        )
        assertTrue("cap ${rows.size}", rows.size <= NotificationRoutePicker.DEFAULT_CAP)
        assertEquals(
            NotificationRoutePicker.RowKind.OPEN_APP,
            rows.last().kind
        )
        assertTrue(
            NotificationRoutePicker.selectProfileId(rows.last().actionId).isBlank()
        )
    }

    @Test
    fun `a degenerate library still yields an honest picker`() {
        val rows = NotificationRoutePicker.build(
            NotificationRoutePicker.Input(routes = emptyList(), currentId = "", currentSourceId = "")
        )
        assertTrue("nothing to pick from, no rows invented", rows.isEmpty())
        assertNull(
            NotificationRoutePicker.selectProfileId(NotificationRoutePicker.OPEN_APP_PREFIX + "all")
                .ifBlank { null }
        )
    }

    @Test
    fun `row labels stay one line and bounded`() {
        val label = NotificationRoutePicker.rowLabel("Berlin 01\nsecond line".padEnd(200, 'x'))
        assertFalse(label.contains('\n'))
        assertTrue(label.length <= 64)
    }
}
