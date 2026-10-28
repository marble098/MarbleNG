package com.marbleng.app.core

/**
 * MARBLE_NOTIFICATION_PICKER_V238 — the pure half of "full route control in the shade".
 *
 * V234 gave the ongoing notification two verbs: next server, next subscription. The council's
 * next ask was blunt again: «کنترل کامل» — the shade must not only walk the list, it must *show*
 * the list, with every server of the live subscription tappable by name and every other
 * subscription reachable in one tap.
 *
 * A shade item has a hard height budget, so this file owns the honest contract of what fits:
 *
 *  - the **live subscription's servers** first, in the user's own order, the live one marked;
 *  - then one **jump row per other subscription** (jumping lands on its first server — exactly
 *    what the V234 subscription verb does), so a library of any depth stays one tap away;
 *  - rows beyond [cap] are dropped, never ellipsized into unreadable slivers;
 *  - the output carries no Android type: `PickerRow` + an action id is everything the
 *    RemoteViews builder needs, and the host-JVM test pins every rule.
 */
object NotificationRoutePicker {

    const val SELECT_PREFIX = "select:"
    const val OPEN_APP_PREFIX = "open:"

    enum class RowKind { SECTION, SERVER, SUBSCRIPTION, OPEN_APP }

    data class PickerRow(
        val kind: RowKind,
        val label: String,
        val actionId: String,
        val active: Boolean = false,
        val payloadId: String = "",
        val payloadSourceId: String = ""
    )

    data class Input(
        val routes: List<CycleRoute>,
        val currentId: String,
        val currentSourceId: String,
        val cap: Int = DEFAULT_CAP
    )

    const val DEFAULT_CAP = 9
    const val MIN_SERVER_ROWS = 4

    fun build(input: Input): List<PickerRow> {
        val routes = NotificationRouteCycle.compact(input.routes)
        if (routes.isEmpty()) return emptyList()
        val cap = input.cap.coerceAtLeast(MIN_SERVER_ROWS + 2)

        val currentId = input.currentId.trim()
        val currentSource = NotificationRouteCycle.currentSource(
            routes,
            currentId,
            input.currentSourceId
        )

        val rows = ArrayList<PickerRow>(cap)
        val liveSourceServers = routes.filter { it.sourceId == currentSource }
        val otherSources = NotificationRouteCycle.sources(routes).filter { it != currentSource }

        var serverBudget = cap - otherSources.size.coerceAtMost(cap / 2) - 2
        serverBudget = serverBudget.coerceIn(MIN_SERVER_ROWS, cap - 2)

        if (liveSourceServers.isNotEmpty()) {
            rows += PickerRow(
                kind = RowKind.SECTION,
                label = NotificationRouteCycle.sourceName(routes, currentSource),
                actionId = ""
            )
            liveSourceServers.take(serverBudget).forEach { route ->
                rows += PickerRow(
                    kind = RowKind.SERVER,
                    label = route.name,
                    actionId = SELECT_PREFIX + route.id,
                    active = route.id == currentId,
                    payloadId = route.id,
                    payloadSourceId = route.sourceId
                )
            }
        }

        otherSources.take(cap - rows.size - 1).forEach { sourceId ->
            val representative = routes.firstOrNull { it.sourceId == sourceId } ?: return@forEach
            rows += PickerRow(
                kind = RowKind.SUBSCRIPTION,
                label = NotificationRouteCycle.sourceName(routes, sourceId),
                actionId = SELECT_PREFIX + representative.id,
                active = false,
                payloadId = representative.id,
                payloadSourceId = representative.sourceId
            )
        }

        if (rows.isNotEmpty() && rows.size < cap) {
            rows += PickerRow(
                kind = RowKind.OPEN_APP,
                label = OPEN_APP_LABEL,
                actionId = OPEN_APP_PREFIX + "all"
            )
        }
        return rows.take(cap)
    }

    const val OPEN_APP_LABEL = "›"

    fun isSelect(actionId: String): Boolean = actionId.startsWith(SELECT_PREFIX)

    fun selectProfileId(actionId: String): String =
        if (isSelect(actionId)) actionId.removePrefix(SELECT_PREFIX) else ""

    /** A RemoteViews row may not be taller than its text: keep names to one line. */
    fun rowLabel(raw: String): String = raw.trim().replace('\n', ' ').take(64)
}
