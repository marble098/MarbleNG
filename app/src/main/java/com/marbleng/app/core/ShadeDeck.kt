package com.marbleng.app.core

/**
 * MARBLE_SHADE_DECK_V273 — the route control in the shade, rebuilt around one promise:
 * **every server you own is reachable, and nothing is clipped to make room.**
 *
 * ## What V249's studio got wrong
 *
 * The studio was a real improvement over V238's list, and it still failed the two tests this
 * product cares about most.
 *
 *  1. **It hid servers it promised to show.** Its row budget was the *data* rows only
 *     (`ShadeControlDefaults.ROWS`, four by default) and every piece of its own chrome was added
 *     on top of that: a heading, a previous row, a next row, a floor switch and an open-app row.
 *     With paging in play the item could reach nine rows in a container whose height is chosen by
 *     SystemUI, so the last rows — the ones holding the servers the user was paging *towards* —
 *     were simply cut off. A list that says "1/2" and then clips page two is worse than no list.
 *  2. **Its subscription floor could only blind-connect.** A `SUBSCRIPTION` row carried
 *     `select:<first server of that source>`, so tapping "Provider B" did not open Provider B: it
 *     dialled an arbitrary member of it, and **no interaction anywhere in the shade could list
 *     Provider B's other servers at all**. That is the report's «بعضی سرور هارو حتی نشون هم
 *     نمیده» — literally, some servers were unreachable from the shade, not just off-screen.
 *  3. **Its servers floor was scoped to the live subscription** and widened to the whole library
 *     only when that subscription had a single server. A library of six subscriptions therefore
 *     showed four servers of one of them, and the rest were two taps and a rebuild away.
 *
 * ## The deck's contract, and how it is enforced
 *
 *  * **A hard physical row budget.** [MAX_ROWS] is the whole deck — chrome included. Chrome rows
 *    are counted *before* data rows are handed out ([layout]), so the item can never grow past the
 *    height it was designed for. A reader who asks for eight rows gets eight rows *including* the
 *    heading and the open-app row; the deck gives the remainder to servers.
 *  * **Every source is a place you can go, not a thing you dial.** A subscription row on the
 *    sources floor *enters* that subscription ([ACTION_SOURCE_PREFIX]); the servers floor then
 *    lists its members, paged. Nothing in the deck connects to a route the user did not pick.
 *  * **Every page is one tap from every other page.** A single pager row shows the page and
 *    advances with wrap-around ([ACTION_NEXT]), so a forty-server subscription is reachable in one
 *    row of chrome instead of two.
 *  * **The live route is never lost.** When the deck is scoped to another subscription a
 *    [ACTION_LIVE] row returns to the live one; the live source is marked on the sources floor.
 *  * **The live row is marked, not promoted.** The active route is drawn with a marker and gets no
 *    special position, because a control that re-orders itself under the finger is a control you
 *    misread.
 *
 * The deck holds no Android type and no strings: [layout] is pure arithmetic and [view] takes its
 * words from [NotificationRoutePicker.Chrome]. `ShadeDeckV273Test` proves the budget, the paging
 * and the reachability of every route in a library without a device.
 */
enum class ShadeFloor(val id: String) {
    SERVERS("servers"),
    SOURCES("sources");

    companion object {
        fun fromId(raw: String?): ShadeFloor =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: SERVERS

        fun other(floor: ShadeFloor): ShadeFloor =
            if (floor == SERVERS) SOURCES else SERVERS
    }
}

/** Where the deck is standing: which floor, which subscription is open, which page. */
data class ShadeDeckState(
    val floor: ShadeFloor = ShadeFloor.SERVERS,
    /** Empty means \"whatever is live\" — the deck resolves it against the library. */
    val scopeSourceId: String = "",
    val page: Int = 0,
    /** The reader's data-row preference; the deck still refuses to exceed [ShadeDeck.MAX_ROWS]. */
    val rows: Int = ShadeDeck.DEFAULT_ROWS,
    val showPing: Boolean = true
)

/**
 * The deck's geometry, before any words are chosen.
 *
 * [chromeRows] is the number of rows the deck spends on itself (heading, floor switch, live
 * return, pager, open-app), and [dataRows] is what is left for servers or subscriptions. Their sum
 * is always ≤ [ShadeDeck.MAX_ROWS].
 */
data class ShadeDeckLayout(
    val floor: ShadeFloor,
    val scopeSourceId: String,
    val itemCount: Int,
    val chromeRows: Int,
    val dataRows: Int,
    val page: Int,
    val pageCount: Int
) {
    val rowsUsed: Int get() = chromeRows + minOf(dataRows, itemCount.coerceAtLeast(0))
    val hasPager: Boolean get() = pageCount > 1
    /** The first and last item index shown on this page, for a test's reachability sweep. */
    val window: IntRange
        get() = if (itemCount <= 0) IntRange.EMPTY else {
            val start = (page * dataRows).coerceIn(0, itemCount - 1)
            start until minOf(itemCount, start + dataRows)
        }
}

/** One page of the deck: geometry plus the rows SystemUI will actually draw. */
data class ShadeDeckView(
    val layout: ShadeDeckLayout,
    val heading: String,
    val rows: List<NotificationRoutePicker.PickerRow>
) {
    val page: Int get() = layout.page
    val pageCount: Int get() = layout.pageCount
    val floor: ShadeFloor get() = layout.floor
}

object ShadeDeck {

    const val MARKER: String = "MARBLE_SHADE_DECK_V273"

    /**
     * The hard ceiling on the deck's own height: eight rows of 34 dp (272 dp) is what a shade item
     * can carry beside its state line without SystemUI starting to clip the bottom row — and a
     * clipped route row is the failure this deck exists to end.
     */
    const val MAX_ROWS: Int = 8

    /** A data row count that fell to zero would make the floor a label with nothing under it. */
    const val MIN_DATA_ROWS: Int = 1

    /** The default when a caller does not pass the reader's own setting. */
    const val DEFAULT_ROWS: Int = 4

    const val ACTION_LEVEL: String = NotificationRoutePicker.CONTROL_PREFIX + NotificationRoutePicker.CONTROL_LEVEL
    const val ACTION_NEXT: String = NotificationRoutePicker.CONTROL_PREFIX + NotificationRoutePicker.CONTROL_NEXT
    const val ACTION_PREVIOUS: String = NotificationRoutePicker.CONTROL_PREFIX + NotificationRoutePicker.CONTROL_PREVIOUS

    /** `control:live` — leave the opened subscription and go back to the live one. */
    const val ACTION_LIVE: String = NotificationRoutePicker.CONTROL_PREFIX + "live"

    /** The verb a subscription row carries, without the control prefix: `source:<id>`. */
    const val VERB_SOURCE_PREFIX: String = "source:"

    /** `control:source:<id>` — open this subscription's servers on the servers floor. */
    const val ACTION_SOURCE_PREFIX: String = NotificationRoutePicker.CONTROL_PREFIX + VERB_SOURCE_PREFIX

    fun sourceAction(sourceId: String): String = ACTION_SOURCE_PREFIX + sourceId

    fun sourceActionId(actionId: String): String =
        sourceIdFromVerb(
            if (actionId.startsWith(ACTION_SOURCE_PREFIX)) {
                actionId.removePrefix(NotificationRoutePicker.CONTROL_PREFIX)
            } else {
                ""
            }
        )

    /** The subscription id inside a `source:<id>` verb, or empty when the verb is something else. */
    fun sourceIdFromVerb(verb: String): String =
        if (verb.startsWith(VERB_SOURCE_PREFIX)) verb.removePrefix(VERB_SOURCE_PREFIX) else ""

    fun isSourceAction(actionId: String): Boolean = actionId.startsWith(ACTION_SOURCE_PREFIX)

    fun isSourceVerb(verb: String): Boolean = verb.startsWith(VERB_SOURCE_PREFIX)

    /**
     * How many rows the deck spends on itself for a library of [sources] subscriptions.
     *
     * The count is exact rather than conservative, and it is computed before paging is decided:
     * the heading is always there, the floor switch only when there is another floor to stand on,
     * the live-return row only when the deck has wandered away from the live subscription, and the
     * open-app row always (a shade you cannot leave is a trap).
     */
    fun chromeRows(
        sources: Int,
        scopedAwayFromLive: Boolean,
        hasPager: Boolean
    ): Int = 1 +
        (if (sources > 1) 1 else 0) +
        (if (scopedAwayFromLive) 1 else 0) +
        (if (hasPager) 1 else 0) +
        1

    /** Page count, from the same arithmetic the pager uses. Never reports zero for a non-empty list. */
    fun pageCount(items: Int, dataRows: Int): Int {
        if (items <= 0) return 0
        val per = dataRows.coerceAtLeast(MIN_DATA_ROWS)
        return ((items + per - 1) / per).coerceAtLeast(1)
    }

    fun clampPage(page: Int, items: Int, dataRows: Int): Int {
        val pages = pageCount(items, dataRows)
        if (pages <= 0) return 0
        return page.coerceIn(0, pages - 1)
    }

    /**
     * The deck's geometry for one library.
     *
     * The pager row is decided by a fix-point rather than a guess: the deck reserves room for a
     * pager, recomputes the rows that are left, and only keeps the reservation if the list is
     * longer than what fits. That is why a four-item subscription on a four-row budget draws no
     * pager at all (and therefore shows its four rows instead of three plus a page you do not
     * need).
     */
    fun layout(
        routes: List<CycleRoute>,
        currentId: String,
        currentSourceId: String,
        state: ShadeDeckState
    ): ShadeDeckLayout {
        val library = NotificationRouteCycle.compact(routes)
        val sources = library.map { it.sourceId }.distinct()
        val liveSource = NotificationRouteCycle.currentSource(
            library,
            currentId.trim(),
            currentSourceId.trim()
        )
        val floor = if (state.floor == ShadeFloor.SOURCES && sources.size <= 1) {
            ShadeFloor.SERVERS
        } else {
            state.floor
        }
        // The scope has to resolve to a subscription that *has servers*: "the live subscription"
        // is a claim about the library, and a remembered id that no longer exists (a deleted
        // subscription, a library restored from a backup) is not a place to stand.
        val requestedScope = state.scopeSourceId.trim()
        val scope = when {
            floor == ShadeFloor.SOURCES -> ""
            requestedScope.isNotEmpty() && requestedScope in sources -> requestedScope
            liveSource in sources -> liveSource
            else -> sources.firstOrNull().orEmpty()
        }
        val items = if (floor == ShadeFloor.SOURCES) {
            sources
        } else {
            library.filter { it.sourceId == scope }
        }
        val scopedAway = floor == ShadeFloor.SERVERS && scope.isNotBlank() && scope != liveSource
        val requested = state.rows.coerceAtLeast(MIN_DATA_ROWS)

        // The pager is part of the budget, and whether it exists depends on the data-row count the
        // budget allows — so the geometry is resolved in two passes instead of guessed. Pass one
        // assumes no pager; if that leaves more than one page, the pager takes a row and the data
        // rows are recomputed. Paging can only grow in the second pass, never shrink, so two
        // passes are enough and the result is a fix-point (`ShadeDeckV273Test` sweeps every
        // combination of rows, floors, scopes and library sizes against it).
        var chrome = chromeRows(sources.size, scopedAway, hasPager = false)
        var dataRows = minOf(requested, (MAX_ROWS - chrome).coerceAtLeast(MIN_DATA_ROWS))
            .coerceAtLeast(MIN_DATA_ROWS)
        if (pageCount(items.size, dataRows) > 1) {
            chrome = chromeRows(sources.size, scopedAway, hasPager = true)
            dataRows = minOf(requested, (MAX_ROWS - chrome).coerceAtLeast(MIN_DATA_ROWS))
                .coerceAtLeast(MIN_DATA_ROWS)
        }
        val pages = pageCount(items.size, dataRows)
        val page = clampPage(state.page, items.size, dataRows)
        return ShadeDeckLayout(
            floor = floor,
            scopeSourceId = scope,
            itemCount = items.size,
            chromeRows = chrome,
            dataRows = dataRows,
            page = page,
            pageCount = pages
        )
    }

    /**
     * The rows the shade draws: heading, this page's servers or subscriptions, then the deck's own
     * chrome. Data rows come first after the heading so a clipped item — which can no longer
     * happen, but is the failure a future edit could reintroduce — would lose a control row before
     * it lost a route.
     */
    fun view(
        routes: List<CycleRoute>,
        currentId: String,
        currentSourceId: String,
        state: ShadeDeckState,
        chrome: NotificationRoutePicker.Chrome,
        pingLabels: Map<String, String> = emptyMap(),
        /**
         * MARBLE_SERVER_PLAN_V273 — one short plan per subscription id, for the sources floor.
         *
         * The servers floor can say everything about the route it shows, but the *subscription*
         * behind it had a single number: how many servers it carries. That was the "useless and
         * incomplete" half of the report — a reader standing in the shade, looking at four
         * subscriptions and choosing one, could not see which of them was out of traffic or out of
         * time. `18 servers · 42% · in 6 days` answers both without opening the app; a subscription
         * with no plan evidence simply omits the suffix, because an absent measurement is not a
         * measurement of zero.
         */
        sourcePlans: Map<String, String> = emptyMap()
    ): ShadeDeckView {
        val library = NotificationRouteCycle.compact(routes)
        val geometry = layout(library, currentId, currentSourceId, state)
        if (library.isEmpty()) {
            return ShadeDeckView(layout = geometry, heading = "", rows = emptyList())
        }
        val liveSource = NotificationRouteCycle.currentSource(
            library,
            currentId.trim(),
            currentSourceId.trim()
        )
        val sources = library.map { it.sourceId }.distinct()
        val liveSourceName = NotificationRouteCycle.sourceName(library, liveSource)
        val scopedAway = geometry.floor == ShadeFloor.SERVERS &&
            geometry.scopeSourceId.isNotBlank() &&
            geometry.scopeSourceId != liveSource &&
            liveSource.isNotBlank()

        val rows = ArrayList<NotificationRoutePicker.PickerRow>(MAX_ROWS)
        val heading = when (geometry.floor) {
            ShadeFloor.SOURCES -> chrome.sourcesHeading
            else -> NotificationRouteCycle.sourceName(library, geometry.scopeSourceId)
                .ifBlank { chrome.serversHeading }
        }
        val pageMeta = when {
            geometry.pageCount > 1 -> chrome.pageLabel(geometry.page + 1, geometry.pageCount)
            geometry.floor == ShadeFloor.SOURCES -> chrome.serverCount(sources.size)
            else -> ""
        }
        rows += NotificationRoutePicker.PickerRow(
            kind = NotificationRoutePicker.RowKind.SECTION,
            label = heading,
            actionId = "",
            meta = pageMeta
        )

        if (geometry.floor == ShadeFloor.SOURCES) {
            sources.drop(geometry.page * geometry.dataRows)
                .take(geometry.dataRows)
                .forEach { sourceId ->
                    val members = library.filter { it.sourceId == sourceId }
                    rows += NotificationRoutePicker.PickerRow(
                        kind = NotificationRoutePicker.RowKind.SUBSCRIPTION,
                        label = NotificationRouteCycle.sourceName(library, sourceId),
                        actionId = sourceAction(sourceId),
                        active = sourceId == liveSource,
                        payloadId = members.firstOrNull { it.id == currentId }?.id.orEmpty(),
                        payloadSourceId = sourceId,
                        meta = buildString {
                            append(chrome.serverCount(members.size))
                            val plan = sourcePlans[sourceId].orEmpty().trim()
                            if (plan.isNotEmpty()) append(" · ").append(plan)
                        }
                    )
                }
        } else {
            library.filter { it.sourceId == geometry.scopeSourceId }
                .drop(geometry.page * geometry.dataRows)
                .take(geometry.dataRows)
                .forEach { route ->
                    rows += NotificationRoutePicker.PickerRow(
                        kind = NotificationRoutePicker.RowKind.SERVER,
                        label = route.name,
                        actionId = NotificationRoutePicker.SELECT_PREFIX + route.id,
                        active = route.id == currentId.trim(),
                        payloadId = route.id,
                        payloadSourceId = route.sourceId,
                        meta = if (state.showPing) pingLabels[route.id].orEmpty() else ""
                    )
                }
        }

        if (geometry.pageCount > 1) {
            rows += NotificationRoutePicker.PickerRow(
                kind = NotificationRoutePicker.RowKind.CONTROL,
                label = chrome.next,
                actionId = ACTION_NEXT,
                meta = chrome.pageLabel(geometry.page + 1, geometry.pageCount)
            )
        }
        if (sources.size > 1) {
            rows += NotificationRoutePicker.PickerRow(
                kind = NotificationRoutePicker.RowKind.CONTROL,
                label = if (geometry.floor == ShadeFloor.SERVERS) {
                    chrome.sourcesHeading
                } else {
                    chrome.serversHeading
                },
                actionId = ACTION_LEVEL
            )
        }
        if (scopedAway) {
            rows += NotificationRoutePicker.PickerRow(
                kind = NotificationRoutePicker.RowKind.CONTROL,
                label = "← " + liveSourceName,
                actionId = ACTION_LIVE
            )
        }
        rows += NotificationRoutePicker.PickerRow(
            kind = NotificationRoutePicker.RowKind.OPEN_APP,
            label = chrome.openApp,
            actionId = NotificationRoutePicker.OPEN_APP_PREFIX + "all"
        )
        return ShadeDeckView(layout = geometry, heading = heading, rows = rows)
    }

    /**
     * Every route the deck can reach, by walking it the way a reader walks it: each subscription on
     * the sources floor, entered, and every page of it turned. Used by `ShadeDeckV273Test` as the
     * completeness proof — the list this returns has to equal the library, or the deck is hiding
     * something again.
     */
    fun reachableRouteIds(
        routes: List<CycleRoute>,
        currentId: String,
        currentSourceId: String,
        rows: Int = DEFAULT_ROWS
    ): List<String> {
        val library = NotificationRouteCycle.compact(routes)
        if (library.isEmpty()) return emptyList()
        val sources = library.map { it.sourceId }.distinct()
        val reached = LinkedHashSet<String>()
        for (sourceId in sources) {
            val state = ShadeDeckState(
                floor = ShadeFloor.SERVERS,
                scopeSourceId = sourceId,
                page = 0,
                rows = rows
            )
            val geometry = layout(library, currentId, currentSourceId, state)
            for (page in 0 until geometry.pageCount) {
                val view = view(
                    routes = library,
                    currentId = currentId,
                    currentSourceId = currentSourceId,
                    state = state.copy(page = page),
                    chrome = AccessibilityChrome,
                    pingLabels = emptyMap(),
                    sourcePlans = emptyMap()
                )
                view.rows
                    .filter { it.kind == NotificationRoutePicker.RowKind.SERVER }
                    .forEach { reached += it.payloadId }
            }
        }
        return reached.toList()
    }

    /**
     * A chrome used only by [reachableRouteIds]'s structural walk: the row *set* is what is being
     * proven, so the words are irrelevant — but they must exist, because every row carries one.
     */
    private val AccessibilityChrome = NotificationRoutePicker.Chrome(
        serversHeading = "Servers",
        sourcesHeading = "Subscriptions",
        previous = "Previous",
        next = "Next",
        openApp = "Open",
        serverCount = { count -> "$count" },
        pageLabel = { page, pages -> "$page/$pages" }
    )
}
