package com.marbleng.app.core

/**
 * MARBLE_NOTIFICATION_SWITCH_V234 — changing the server and the subscription *inside the shade*.
 *
 * V231 gave the ongoing notification a state, a route and two buttons — but both buttons only
 * opened the app on the Servers page, so a user who wants the next route had to unlock the phone,
 * cross a whole screen and come back. The council's request on 2026-10-26 was blunt: «تغییر ساب و
 * سرور داخل همون نوتیفیکیشن قابل انجام باشه» — the shade must be able to do it.
 *
 * This file is the decision half, pure Kotlin with no Android import, so `NotificationRouteCycleV234Test`
 * can pin every rule on the host JVM:
 *
 *  - [CycleRoute] is the only thing the shade needs to know about a route: an id, a label, and the
 *    subscription it came from.
 *  - [next] answers "what comes after the route that is live right now?" for the two verbs the
 *    notification offers.
 *  - **A verb is never dead.** A subscription with one server still moves (the whole library is
 *    walked), a library with one subscription still moves (the server verb is used instead), and a
 *    library with one route answers `null` so the service can say so instead of silently doing
 *    nothing.
 *  - **The order is the user's order.** Both walks follow the list the Servers page shows, not a
 *    hash or a latency sort: pressing "next" twice returns to where it started, which is the only
 *    way a cycle feels like a cycle.
 */
data class CycleRoute(
    val id: String,
    val name: String,
    val sourceId: String,
    val sourceName: String
)

/** The two verbs the ongoing notification carries. The ids travel in a `PendingIntent` action. */
enum class NotificationCycleKind(val id: String) {
    SERVER("server"),
    SUBSCRIPTION("subscription");

    companion object {
        fun fromId(raw: String?): NotificationCycleKind? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

object NotificationRouteCycle {
    const val MARKER: String = "MARBLE_NOTIFICATION_SWITCH_V234"

    /** The source name the shade prints when the profile carries none of its own. */
    const val MANUAL_SOURCE: String = "Manual"

    /** A stable, duplicate-free view of what the library offers. */
    fun compact(routes: List<CycleRoute>): List<CycleRoute> =
        routes.asSequence()
            .filter { it.id.isNotBlank() }
            .map { route ->
                route.copy(
                    name = route.name.trim().ifBlank { route.sourceName },
                    sourceId = route.sourceId.trim().ifBlank { MANUAL_SOURCE },
                    sourceName = route.sourceName.trim().ifBlank { MANUAL_SOURCE }
                )
            }
            .distinctBy { it.id }
            .toList()

    /** The sources in the order they first appear — the order the Servers page shows them in. */
    fun sources(routes: List<CycleRoute>): List<String> =
        compact(routes).map { it.sourceId }.distinct()

    fun currentSource(routes: List<CycleRoute>, currentId: String, currentSourceId: String): String {
        val list = compact(routes)
        val declared = currentSourceId.trim()
        if (declared.isNotEmpty() && list.any { it.sourceId == declared }) return declared
        return list.firstOrNull { it.id == currentId }?.sourceId
            ?: declared.ifBlank { list.firstOrNull()?.sourceId.orEmpty() }
    }

    fun sourceName(routes: List<CycleRoute>, sourceId: String): String =
        compact(routes).firstOrNull { it.sourceId == sourceId }?.sourceName ?: MANUAL_SOURCE

    /**
     * The next route for [kind]. See the class comment for the two fallbacks that keep the verb
     * alive; `null` means the library genuinely has nothing else to offer.
     */
    fun next(
        kind: NotificationCycleKind,
        routes: List<CycleRoute>,
        currentId: String,
        currentSourceId: String
    ): CycleRoute? = when (kind) {
        NotificationCycleKind.SERVER -> nextServer(routes, currentId, currentSourceId)
        NotificationCycleKind.SUBSCRIPTION -> nextSubscription(routes, currentId, currentSourceId)
    }

    /**
     * The next server **inside the live subscription**, and only when that subscription has
     * nothing else to offer does it walk the whole library. A subscription with eleven nodes is
     * the common case, and staying inside it is what "next server" is expected to mean.
     */
    fun nextServer(
        routes: List<CycleRoute>,
        currentId: String,
        currentSourceId: String
    ): CycleRoute? {
        val list = compact(routes)
        if (list.isEmpty()) return null
        if (list.size == 1) return null

        val sourceId = currentSource(routes, currentId, currentSourceId)
        val inSource = list.filter { it.sourceId == sourceId }
        if (inSource.size > 1) {
            val index = inSource.indexOfFirst { it.id == currentId }
            val next = if (index >= 0) {
                inSource[(index + 1) % inSource.size]
            } else {
                inSource.firstOrNull { it.id != currentId }
            }
            if (next != null && next.id != currentId) return next
        }
        return walk(list, currentId)
    }

    /**
     * The first route of the **next subscription**, in library order. A library with a single
     * subscription falls back to [nextServer] rather than doing nothing: the button exists to move
     * traffic, and refusing to move it because there is only one source would be a dead control.
     */
    fun nextSubscription(
        routes: List<CycleRoute>,
        currentId: String,
        currentSourceId: String
    ): CycleRoute? {
        val list = compact(routes)
        if (list.isEmpty()) return null
        val order = list.map { it.sourceId }.distinct()
        if (order.size <= 1) return nextServer(routes, currentId, currentSourceId)

        val sourceId = currentSource(routes, currentId, currentSourceId)
        val index = order.indexOf(sourceId)
        val nextSource = order[(index + 1).mod(order.size)]
        val target = list.firstOrNull { it.sourceId == nextSource && it.id != currentId }
            ?: list.firstOrNull { it.sourceId == nextSource }
        return target ?: walk(list, currentId)
    }

    /** One step forward in the library order, wrapping once, never returning the current route. */
    private fun walk(list: List<CycleRoute>, currentId: String): CycleRoute? {
        if (list.size <= 1) return null
        val index = list.indexOfFirst { it.id == currentId }
        val start = if (index >= 0) index else -1
        for (step in 1..list.size) {
            val candidate = list[(start + step).mod(list.size)]
            if (candidate.id != currentId) return candidate
        }
        return null
    }

    /**
     * `Route · Source` — the second line of the shade. The source is dropped when it is the same
     * word as the route (a one-server subscription named after its node) so the line never stutters.
     */
    fun routeLine(route: String, source: String): String {
        val name = route.trim()
        val from = source.trim()
        return when {
            name.isEmpty() -> from
            from.isEmpty() || from == name -> name
            else -> "$name · $from"
        }
    }
}
