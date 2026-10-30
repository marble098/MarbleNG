package com.marbleng.app.core

import com.marbleng.app.model.ShadeControlDefaults

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
/**
 * MARBLE_SHADE_STUDIO_V249 — which floor of the library the shade is standing on.
 *
 * V238's picker showed one thing: the live subscription's servers, then a jump row per other
 * subscription. That is a good list and a bad *browser* — with forty subscriptions the jump rows
 * are the whole budget and no server is reachable. Two floors, one control that moves between them,
 * is what makes a library of any shape reachable without opening the app.
 */
enum class ShadeLevel(val id: String) {
    SERVERS(ShadeControlDefaults.LEVEL_SERVERS),
    SOURCES(ShadeControlDefaults.LEVEL_SOURCES);

    companion object {
        fun fromId(raw: String?): ShadeLevel =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: SERVERS

        fun other(level: ShadeLevel): ShadeLevel =
            if (level == SERVERS) SOURCES else SERVERS
    }
}

/** How much route control the ongoing notification carries. */
enum class ShadePickerStyle(val id: String) {
    /** The V249 studio: two floors, paging, a measured ping per row. */
    STUDIO(ShadeControlDefaults.STYLE_STUDIO),

    /** The V238 list: the live subscription's servers, then one jump row per other source. */
    LIST(ShadeControlDefaults.STYLE_LIST);

    companion object {
        val DEFAULT: ShadePickerStyle get() = STUDIO

        fun fromId(raw: String?): ShadePickerStyle =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

object NotificationRoutePicker {

    const val SELECT_PREFIX = "select:"
    const val OPEN_APP_PREFIX = "open:"

    /** MARBLE_SHADE_STUDIO_V249 — the studio's own chrome: page and floor control. */
    const val CONTROL_PREFIX = "control:"
    const val CONTROL_PREVIOUS = "previous"
    const val CONTROL_NEXT = "next"
    const val CONTROL_LEVEL = "level"

    enum class RowKind { SECTION, SERVER, SUBSCRIPTION, OPEN_APP, CONTROL }

    data class PickerRow(
        val kind: RowKind,
        val label: String,
        val actionId: String,
        val active: Boolean = false,
        val payloadId: String = "",
        val payloadSourceId: String = "",
        /**
         * MARBLE_SHADE_STUDIO_V249 — the trailing half of a row: a measured ping on a server, a
         * server count on a subscription, a page number on the heading. Empty means "draw nothing
         * there", which is what keeps an unmeasured library honest instead of showing `— ms` forty
         * times.
         */
        val meta: String = ""
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

    // ---------------------------------------------------------------------------------------------
    // MARBLE_SHADE_STUDIO_V249 — the advanced picker: two floors, paging, and a measured ping.
    //
    // [build] above is the V238 list and stays exactly as it shipped: some readers want the short
    // item. The studio is the same row type and the same action ids with three things the list
    // cannot do — it pages through a library of any depth, it has a floor for subscriptions so a
    // source is chosen by name instead of by cycling, and it prints the measurement each server
    // already has, so the choice is made on evidence instead of on a name.
    // ---------------------------------------------------------------------------------------------

    /**
     * Servers or subscriptions listed per page. The shade's height is the budget, not the library's
     * size, and the numbers come from [ShadeControlDefaults] so the persisted setting, the dial on
     * the settings page and the page counter here cannot disagree about what fits.
     */
    const val DEFAULT_STUDIO_ROWS: Int = ShadeControlDefaults.ROWS
    const val MIN_STUDIO_ROWS: Int = ShadeControlDefaults.ROWS_MIN
    const val MAX_STUDIO_ROWS: Int = ShadeControlDefaults.ROWS_MAX

    /**
     * The chrome words, already in the shade's own language and already shaped for its digits.
     * The studio decides *what* a row says and never *how it is spelled*: `NotificationCopy` owns
     * that, exactly as it does for every other word the notification carries.
     */
    data class Chrome(
        val serversHeading: String,
        val sourcesHeading: String,
        val previous: String,
        val next: String,
        val openApp: String,
        val serverCount: (Int) -> String,
        val pageLabel: (page: Int, pages: Int) -> String
    )

    data class StudioInput(
        val routes: List<CycleRoute>,
        val currentId: String,
        val currentSourceId: String,
        val level: ShadeLevel = ShadeLevel.SERVERS,
        val page: Int = 0,
        val rowBudget: Int = DEFAULT_STUDIO_ROWS,
        val showPing: Boolean = true,
        val pingLabels: Map<String, String> = emptyMap(),
        val chrome: Chrome
    )

    /**
     * One page of the studio: the floor it is on, the heading that names the floor, and the rows.
     * [pageCount] is the honest number of pages, so a caller can tell "one page" from "the first of
     * nine" without counting rows.
     */
    data class StudioView(
        val level: ShadeLevel,
        val heading: String,
        val rows: List<PickerRow>,
        val page: Int,
        val pageCount: Int
    )

    fun pageCount(items: Int, budget: Int): Int {
        val per = budget.coerceAtLeast(MIN_STUDIO_ROWS)
        if (items <= 0) return 0
        return ((items + per - 1) / per).coerceAtLeast(1)
    }

    /** A page that no longer exists (the library shrank) resolves to the last page that does. */
    fun clampPage(page: Int, items: Int, budget: Int): Int {
        val pages = pageCount(items, budget)
        if (pages <= 0) return 0
        return page.coerceIn(0, pages - 1)
    }

    fun studio(input: StudioInput): StudioView {
        val routes = NotificationRouteCycle.compact(input.routes)
        if (routes.isEmpty()) {
            return StudioView(
                level = input.level,
                heading = "",
                rows = emptyList(),
                page = 0,
                pageCount = 0
            )
        }

        val budget = ShadeControlDefaults.rowChoice(input.rowBudget)
        val currentId = input.currentId.trim()
        val currentSource = NotificationRouteCycle.currentSource(
            routes,
            currentId,
            input.currentSourceId
        )
        val sources = NotificationRouteCycle.sources(routes)

        // One subscription has no second floor to stand on: the studio stays on the servers.
        val level = if (input.level == ShadeLevel.SOURCES && sources.size <= 1) {
            ShadeLevel.SERVERS
        } else {
            input.level
        }

        val rows = ArrayList<PickerRow>(budget + 5)

        if (level == ShadeLevel.SOURCES) {
            val sourceIds = sources
            val pages = pageCount(sourceIds.size, budget)
            val page = clampPage(input.page, sourceIds.size, budget)
            val heading = input.chrome.sourcesHeading
            rows += PickerRow(
                kind = RowKind.SECTION,
                label = heading,
                actionId = "",
                meta = if (pages > 1) input.chrome.pageLabel(page + 1, pages) else ""
            )
            sourceIds.drop(page * budget).take(budget).forEach { sourceId ->
                val members = routes.filter { it.sourceId == sourceId }
                // Tapping a subscription lands on the server of it that is live, and on its first
                // server when none is: the same answer the V234 subscription verb gives.
                val representative = members.firstOrNull { it.id == currentId } ?: members.first()
                rows += PickerRow(
                    kind = RowKind.SUBSCRIPTION,
                    label = NotificationRouteCycle.sourceName(routes, sourceId),
                    actionId = SELECT_PREFIX + representative.id,
                    active = sourceId == currentSource,
                    payloadId = representative.id,
                    payloadSourceId = sourceId,
                    meta = input.chrome.serverCount(members.size)
                )
            }
            addPaging(rows, page, pages, input.chrome)
            return finish(
                rows = rows,
                level = level,
                heading = heading,
                page = page,
                pageCount = pages,
                sources = sources,
                chrome = input.chrome
            )
        }

        val liveServers = routes.filter { it.sourceId == currentSource }
        // A subscription with one server has nothing to choose between, so the floor widens to the
        // whole library instead of showing a single row that cannot move anything.
        val scoped = liveServers.size > 1
        items = if (scoped) liveServers else routes
        scopeName = if (scoped) {
            NotificationRouteCycle.sourceName(routes, currentSource)
        } else {
            input.chrome.serversHeading
        }
        val pages = pageCount(items.size, budget)
        val page = clampPage(input.page, items.size, budget)
        rows += PickerRow(
            kind = RowKind.SECTION,
            label = scopeName,
            actionId = "",
            meta = if (pages > 1) input.chrome.pageLabel(page + 1, pages) else ""
        )
        items.drop(page * budget).take(budget).forEach { route ->
            rows += PickerRow(
                kind = RowKind.SERVER,
                label = route.name,
                actionId = SELECT_PREFIX + route.id,
                active = route.id == currentId,
                payloadId = route.id,
                payloadSourceId = route.sourceId,
                meta = if (input.showPing) input.pingLabels[route.id].orEmpty() else ""
            )
        }
        addPaging(rows, page, pages, input.chrome)
        return finish(
            rows = rows,
            level = level,
            heading = scopeName,
            page = page,
            pageCount = pages,
            sources = sources,
            chrome = input.chrome
        )
    }

    private fun addPaging(
        rows: MutableList<PickerRow>,
        page: Int,
        pages: Int,
        chrome: Chrome
    ) {
        if (pages <= 1) return
        if (page > 0) {
            rows += PickerRow(
                kind = RowKind.CONTROL,
                label = chrome.previous,
                actionId = CONTROL_PREFIX + CONTROL_PREVIOUS
            )
        }
        if (page < pages - 1) {
            rows += PickerRow(
                kind = RowKind.CONTROL,
                label = chrome.next,
                actionId = CONTROL_PREFIX + CONTROL_NEXT
            )
        }
    }

    /**
     * The two rows that are the same on every page: the floor control (only when a second floor
     * exists) and the way back into the app. The studio never hides the second one — a shade that
     * can only switch routes is a shade the user has to unlock to leave.
     */
    private fun finish(
        rows: MutableList<PickerRow>,
        level: ShadeLevel,
        heading: String,
        page: Int,
        pageCount: Int,
        sources: List<String>,
        chrome: Chrome
    ): StudioView {
        if (sources.size > 1) {
            rows += PickerRow(
                kind = RowKind.CONTROL,
                label = if (level == ShadeLevel.SERVERS) {
                    chrome.sourcesHeading
                } else {
                    chrome.serversHeading
                },
                actionId = CONTROL_PREFIX + CONTROL_LEVEL
            )
        }
        rows += PickerRow(
            kind = RowKind.OPEN_APP,
            label = chrome.openApp,
            actionId = OPEN_APP_PREFIX + "all"
        )
        return StudioView(
            level = level,
            heading = heading,
            rows = rows.toList(),
            page = page,
            pageCount = pageCount
        )
    }

    fun isControl(actionId: String): Boolean = actionId.startsWith(CONTROL_PREFIX)

    /** `previous`, `next` or `level` — the verb a control row carries. */
    fun controlVerb(actionId: String): String =
        if (isControl(actionId)) actionId.removePrefix(CONTROL_PREFIX) else ""

    fun isSelect(actionId: String): Boolean = actionId.startsWith(SELECT_PREFIX)

    fun selectProfileId(actionId: String): String =
        if (isSelect(actionId)) actionId.removePrefix(SELECT_PREFIX) else ""

    /** A RemoteViews row may not be taller than its text: keep names to one line. */
    fun rowLabel(raw: String): String = raw.trim().replace('\n', ' ').take(64)
}
