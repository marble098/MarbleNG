package com.marbleng.app.model

/**
 * MARBLE_HOME_QUIET_V259 — a fresh install starts on the quietest Home, and the rest is added.
 *
 * The report was about *the first minute*: «صفحه اصلی باید در خلوت‌ترین حالت باشد و فقط چیزهای ضروری».
 * A brand-new install opened on six sections — heading, connect stage, one-tap presets, route, the
 * live speed graph and four quick-action tiles — so the first thing a person saw was a dashboard
 * built for somebody who already knew what every card did. Three of those six are answers to
 * questions a first-run user has not asked yet.
 *
 * The rule this file encodes has two halves, and the second half is the one that makes it safe:
 *
 *  1. **A first run is quiet.** [CALM_SECTIONS] is what Home draws before anybody has said
 *     otherwise: the heading, the one decision (connect), and the route it will use. Nothing else.
 *     Presets, the speed graph and the quick actions are one tap away in Settings → Home layout, and
 *     Home itself offers a card that says so.
 *  2. **An install that has ever saved its settings is not touched.** The store reads
 *     `homeHiddenSections` and falls back to [CALM_HIDDEN] only when the key is absent, and every
 *     save writes the key. So an upgrade keeps the Home the reader built, and the quiet default
 *     reaches exactly the installs that never had a layout to keep.
 *
 * The section list stays [HomeSection]'s: this file decides *which* of them a first run shows, not
 * what they are, so the studio in Settings and Home itself can never disagree about the vocabulary.
 */
object HomeQuiet {

    const val MARKER: String = "MARBLE_HOME_QUIET_V259"

    /**
     * What a first run draws, in the shipped order: heading, the one decision, its route.
     *
     * Deliberately short. `CONNECT` is the reason the app exists, `ROUTE` is the one fact that makes
     * the decision meaningful ("what will I be connected to?") and `HEADER` is how the page names
     * itself. Everything else is a reading, and a reading is a reward for having connected once.
     */
    val CALM_SECTIONS: List<HomeSection> = listOf(
        HomeSection.HEADER,
        HomeSection.CONNECT,
        HomeSection.ROUTE
    )

    /** The sections a fresh install keeps switched off. */
    val CALM_HIDDEN_SECTIONS: List<HomeSection> =
        HomeSection.entries.filterNot { it in CALM_SECTIONS }

    /**
     * The calm layout as the two strings the store persists.
     *
     * Note that the *order* string is always the complete six-section list: `HomeStudio.order`
     * completes any string it is given, so a partial one would only be rewritten on the way in.
     * The calm default therefore lives in the hidden string, which names the three sections a
     * first run switches off — positively, one id per section, never as "everything except…".
     */
    fun calmOrder(): String = HomeStudio.serialize(CALM_SECTIONS)

    fun calmHidden(): String = HomeStudio.serializeHidden(CALM_HIDDEN_SECTIONS.map { it.id })

    /** The quiet default, as the value the store falls back to when it has never been written. */
    val CALM_HIDDEN: String get() = calmHidden()

    /** A layout that draws every section, which is what "Show everything" writes. */
    fun everythingOrder(): String = HomeStudio.DEFAULT_ORDER

    fun nothingHidden(): String = HomeStudio.DEFAULT_HIDDEN

    /**
     * Is this Home still the quiet one?
     *
     * Both halves have to hold: the calm sections are the visible ones *and* nothing extra has been
     * switched on since. A reader who added the speed graph is not on a quiet Home any more, and the
     * Home card that offers the studio has to know that.
     */
    fun isCalm(
        orderRaw: String,
        hiddenRaw: String,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): Boolean = HomeStudio.visible(
        orderRaw = orderRaw,
        hiddenRaw = hiddenRaw,
        legacyStatsVisible = legacyStatsVisible,
        legacyShortcutsVisible = legacyShortcutsVisible
    ).toSet() == CALM_SECTIONS.toSet()

    /** How many of the six a fresh install is *not* showing — the number the card prints. */
    val calmHiddenCount: Int get() = CALM_HIDDEN_SECTIONS.size

    /**
     * One section switched on or off, as the string to persist.
     *
     * The two legacy switches (`modularShowStats`, `modularShowShortcuts`) are floors under the
     * hidden list, so turning a section *on* has to lift the floor as well — otherwise the switch
     * would write a string that the floor immediately overrules and the reader would watch a
     * toggle that does nothing. The legacy value is returned alongside the string for exactly that
     * reason, and the caller writes both in one update.
     */
    fun toggle(
        orderRaw: String,
        hiddenRaw: String,
        section: HomeSection,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): HomeToggle {
        val wasVisible = !HomeStudio.isHidden(
            section = section,
            hiddenRaw = hiddenRaw,
            legacyStatsVisible = legacyStatsVisible,
            legacyShortcutsVisible = legacyShortcutsVisible
        )
        val hidden = HomeStudio.toggleHidden(
            hiddenRaw = hiddenRaw,
            section = section,
            legacyStatsVisible = legacyStatsVisible,
            legacyShortcutsVisible = legacyShortcutsVisible
        )
        return HomeToggle(
            order = HomeStudio.canonicalOrder(orderRaw),
            hidden = hidden,
            section = section,
            nowVisible = !wasVisible,
            legacyStatsVisible = if (section == HomeSection.STATS) !wasVisible else legacyStatsVisible,
            legacyShortcutsVisible = if (section == HomeSection.SHORTCUTS) !wasVisible else legacyShortcutsVisible
        )
    }

    /** What [toggle] produced: the two layout strings *and* the two legacy floors. */
    data class HomeToggle(
        val order: String,
        val hidden: String,
        val section: HomeSection,
        val nowVisible: Boolean,
        val legacyStatsVisible: Boolean,
        val legacyShortcutsVisible: Boolean
    )

    /**
     * "3 of 6 shown" — the summary line the Home layout page and the Home card both print.
     */
    fun shownCount(
        orderRaw: String,
        hiddenRaw: String,
        legacyStatsVisible: Boolean = true,
        legacyShortcutsVisible: Boolean = true
    ): Int = HomeStudio.visible(
        orderRaw = orderRaw,
        hiddenRaw = hiddenRaw,
        legacyStatsVisible = legacyStatsVisible,
        legacyShortcutsVisible = legacyShortcutsVisible
    ).size

    /** The English keys the studio prints for a section, matching the Persian dictionary. */
    fun titleOf(section: HomeSection): String = section.title

    fun hintOf(section: HomeSection): String = section.hint
}
