package com.marbleng.app.model

/**
 * MARBLE_HOME_STUDIO_V249 — Home is a layout the user owns, not a page the product ships.
 *
 * The ask was «هر قسمتی رو حذف یا غیرفعال کنه»: any part of Home can be turned off. Two of the six
 * parts could already be hidden through `modularShowStats` and `modularShowShortcuts`, and nothing
 * else could, so a user who only wants the pill and the route had to scroll past four sections they
 * had no way to remove.
 *
 * This model owns the whole layout in two persisted strings, because two strings survive a backup,
 * a restore and a settings change without a migration:
 *
 *  - **order** — every section id, in the order Home draws them. Unknown ids are dropped, missing
 *    ones are appended in the canonical order, so an old install and a hand-edited backup both
 *    resolve to a complete layout.
 *  - **hidden** — the ids that are switched off. Absence means visible; the default is nothing
 *    hidden, so an upgrade changes nobody's Home.
 *
 * The two legacy booleans are honoured as a floor rather than replaced: a user who turned the speed
 * card off in an older build still has it off, and the settings page keeps writing both so the two
 * records can never disagree.
 *
 * The titles and hints are the *English keys* the settings page hands to `trx`, and they are
 * spelled exactly as the Persian dictionary already spells them (`Connect Control`, `Active Route`,
 * `One-tap modes`, `Quick Actions`): a key that differs from the dictionary by one capital letter is
 * not a translation miss, it is a transliteration — the fallback would spell the English word in
 * Persian letters and call it Farsi.
 *
 * Pure Kotlin with no Android import, so `HomeStudioV249Test` pins every rule on the host JVM.
 */
enum class HomeSection(val id: String, val title: String, val hint: String) {
    HEADER(
        id = "header",
        title = "Page heading",
        hint = "The name, the mode line and the three actions at the top"
    ),
    CONNECT(
        id = "connect",
        title = "Connect Control",
        hint = "The one pill, with the mode chip and the status chip under it"
    ),
    PRESETS(
        id = "presets",
        title = "One-tap modes",
        hint = "Gaming, streaming and blocked-network presets in a row"
    ),
    ROUTE(
        id = "route",
        title = "Active Route",
        hint = "The live server, its country, its ping and the exit IP"
    ),
    STATS(
        id = "stats",
        title = "Network Speed & Session Data",
        hint = "Live throughput with the session and total counters"
    ),
    SHORTCUTS(
        id = "shortcuts",
        title = "Quick Actions",
        hint = "Show clipboard import, QR scanner, Smart Rank and Censorship Lab"
    );

    companion object {
        fun fromId(raw: String?): HomeSection? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

object HomeStudio {

    const val MARKER: String = "MARBLE_HOME_STUDIO_V249"

    /** The order Home ships in: heading, the one decision, the modes, the route, then the readings. */
    val DEFAULT_ORDER: String = HomeSection.entries.joinToString(SEPARATOR) { it.id }

    /** Nothing is hidden by default, so an upgrade never removes a section from anybody's Home. */
    const val DEFAULT_HIDDEN: String = ""

    private const val SEPARATOR = ","

    /** Every id in the order the caller asked for, completed with whatever they did not name. */
    fun order(raw: String): List<HomeSection> {
        val requested = raw.split(SEPARATOR)
            .mapNotNull { HomeSection.fromId(it) }
            .distinct()
        return requested + HomeSection.entries.filterNot { it in requested }
    }

    fun serialize(sections: List<HomeSection>): String =
        order(sections.joinToString(SEPARATOR) { it.id }).joinToString(SEPARATOR) { it.id }

    /**
     * The persisted order, canonicalized. Used on the way in *and* on the way out of the store so
     * one rule decides what a layout string means: a section added by a future build is appended
     * instead of dropped, a retired one disappears, and a duplicate is drawn once.
     */
    fun canonicalOrder(raw: String): String =
        order(raw).joinToString(SEPARATOR) { it.id }

    /** The persisted hidden ids, canonicalized: real sections only, each named once. */
    fun canonicalHidden(raw: String): String = serializeHidden(hiddenIds(raw))

    /** The hidden ids, de-duplicated and limited to sections that exist. */
    fun hiddenIds(raw: String): Set<String> =
        raw.split(SEPARATOR)
            .mapNotNull { HomeSection.fromId(it)?.id }
            .distinct()
            .toSet()

    fun serializeHidden(ids: Collection<String>): String =
        ids.mapNotNull { HomeSection.fromId(it)?.id }.distinct().joinToString(SEPARATOR)

    /**
     * One switch flipped, returned as the string to persist.
     *
     * The two legacy booleans are the floor: a section they already hide stays hidden until the
     * caller writes them too, which the settings page always does in the same update.
     */
    fun toggleHidden(
        hiddenRaw: String,
        section: HomeSection,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): String {
        val hidden = hiddenIds(hiddenRaw).toMutableSet()
        if (isHidden(section, hiddenRaw, legacyStatsVisible, legacyShortcutsVisible)) {
            hidden -= section.id
        } else {
            hidden += section.id
        }
        return serializeHidden(hidden)
    }

    /**
     * Is this section off? A hidden id turns it off, and so does the legacy switch that owned it
     * before this page existed.
     */
    fun isHidden(
        section: HomeSection,
        hiddenRaw: String,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): Boolean = when (section) {
        HomeSection.STATS -> section.id in hiddenIds(hiddenRaw) || !legacyStatsVisible
        HomeSection.SHORTCUTS -> section.id in hiddenIds(hiddenRaw) || !legacyShortcutsVisible
        else -> section.id in hiddenIds(hiddenRaw)
    }

    /** What Home draws, in order. Never empty-handed: [visible] may be empty, and the page says so. */
    fun visible(
        orderRaw: String,
        hiddenRaw: String,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): List<HomeSection> = order(orderRaw).filterNot {
        isHidden(it, hiddenRaw, legacyStatsVisible, legacyShortcutsVisible)
    }

    /** How many of the six are switched off; the settings page shows it as a summary line. */
    fun hiddenCount(
        hiddenRaw: String,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): Int = HomeSection.entries.count {
        isHidden(it, hiddenRaw, legacyStatsVisible, legacyShortcutsVisible)
    }

    /** A Home with everything off is allowed — it is a choice — and the page has to name it. */
    fun isBare(
        orderRaw: String,
        hiddenRaw: String,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): Boolean = visible(orderRaw, hiddenRaw, legacyStatsVisible, legacyShortcutsVisible).isEmpty()

    /**
     * Move one section [steps] places in the order (negative is up). Returns the string to persist,
     * or `null` when the move would leave the layout — the ends of the list have nowhere to go.
     */
    fun move(orderRaw: String, section: HomeSection, steps: Int): String? {
        if (steps == 0) return null
        val list = order(orderRaw).toMutableList()
        val from = list.indexOf(section)
        if (from < 0) return null
        val to = from + steps
        if (to < 0 || to >= list.size) return null
        list.removeAt(from)
        list.add(to, section)
        return serialize(list)
    }

    fun canMove(orderRaw: String, section: HomeSection, steps: Int): Boolean =
        move(orderRaw, section, steps) != null

    /** Both strings back to what Home shipped with. */
    fun resetOrder(): String = DEFAULT_ORDER

    fun resetHidden(): String = DEFAULT_HIDDEN
}
