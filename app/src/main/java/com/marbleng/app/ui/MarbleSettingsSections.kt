package com.marbleng.app.ui

// MARBLE_SETTINGS_SECTIONS_V210 — Settings is a tree, not a shelf.
//
// The report: "organise the settings page into a few main sections, each with its own
// sub-sections." Which sounds like a styling request and is really a complaint about the shape
// of the page. What Settings actually was: six cards of equal weight, each a flat column of rows,
// sitting in a single scrolling list. Six is already too many things to hold in your head, and
// nothing in the page said how they related to each other — "Home & display", "Appearance",
// "System & privacy" and "Data & sources" are not siblings of "Engine", they are different
// *kinds* of thing, and the page drew them as one list of equals.
//
// Two failures follow from that shape:
//
//  1. **No path.** A user looking for "the typeface" has to know it lives under Appearance. The
//     page offered no way to learn that except reading all six cards, and no way to skip a
//     section once they knew they did not want it.
//  2. **No depth.** Everything a section contains was drawn at one level, so a live switch, a
//     door to another page and a destructive action all read as the same kind of object. A
//     settings page is a hierarchy of decisions, and a flat list is a bad way to draw one.
//
// What replaces it:
//
//  - **A main section** ([SettingsHubGroupSpec]) is a titled, collapsible parent with its own
//    icon and tone. Collapsing is the user's, not the designer's: the page opens with every
//    section expanded, and a tap on the header closes it, so a user who only ever touches
//    Appearance can close the other five and keep them closed for the visit.
//  - **A sub-section** ([SettingsHubSectionSpec]) is a named group of settings *inside* a main
//    section, drawn as an inset card with its own label and rail. Sub-sections are what make the
//    hierarchy legible: "Theme", "Navigation" and "Language & type" are visibly children of
//    Appearance, not six more equal cards.
//  - **The open/closed state is one policy** ([SettingsHubPolicy]) rather than six booleans
//    sprinkled through a composable, because "which sections are collapsed" is a pure question
//    and the answer has to survive a recomposition, a theme switch and a test.
//
// The keys are strings, not indices: a section added or reordered in a later release must not
// inherit another section's collapsed state.

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One sub-section of a Settings main section: a name, one sentence, and the settings inside it.
 */
internal data class SettingsHubSectionSpec(
    val title: String,
    val subtitle: String = "",
    val content: @Composable () -> Unit
)

/**
 * One main section of the Settings hub.
 *
 * The icon is a composable lambda rather than an enum entry because the glyph set belongs to the
 * screen that draws it; this file owns the shape of the tree and nothing else.
 */
internal data class SettingsHubGroupSpec(
    /** Stable identity: collapsed state is remembered by key, never by position. */
    val key: String,
    val title: String,
    val subtitle: String,
    val tone: Color,
    val sections: List<SettingsHubSectionSpec>,
    val icon: @Composable (tone: Color) -> Unit
)

/**
 * The main sections of the Settings hub, in the order the page draws them.
 *
 * The keys live here, next to the policy that stores them, rather than inside the page's own
 * composable: "collapse all" has to name every section, and a name invented twice is a name
 * that drifts. `scripts/system-integrity-check.py` pins the page against this list.
 */
internal object SettingsHubGroups {
    val Keys: List<String> = listOf(
        "home",        // Home & display
        "connection",  // Connection
        "measurement", // Measurement & servers
        "data",        // Data & sources
        "appearance",  // Appearance
        "system"       // System
    )
}

/**
 * MARBLE_SETTINGS_SECTIONS_V210 — which of the hub's main sections are open.
 *
 * The state is the set of *collapsed* keys, not the set of expanded ones. That is a deliberate
 * inversion: a section nobody has touched is open, so a new section shipped in a later release
 * appears open too instead of being hidden by a stored list that has never heard of it.
 *
 * Everything here is pure, so "collapse all, expand one, collapse it again" is a unit test
 * instead of a tap sequence on a device.
 */
internal object SettingsHubPolicy {

    /** A section is open unless it has been collapsed. */
    fun isOpen(collapsed: Set<String>, key: String): Boolean = key !in collapsed

    /** Tapping a header flips exactly that section and leaves the others alone. */
    fun toggled(collapsed: Set<String>, key: String): Set<String> =
        if (key in collapsed) collapsed - key else collapsed + key

    /** "Expand all" is the empty set: no section is collapsed, whatever was collapsed before. */
    fun expandAll(): Set<String> = emptySet()

    /** "Collapse all" names the keys it closes, so a section added later is named too. */
    fun collapseAll(keys: Collection<String>): Set<String> = keys.toSet()

    /** How many sections are open — the one number the page reports about its own state. */
    fun openCount(collapsed: Set<String>, keys: Collection<String>): Int =
        keys.count { it !in collapsed }

    /** True when every section is open, which is when the header offers "Collapse all". */
    fun allOpen(collapsed: Set<String>, keys: Collection<String>): Boolean =
        keys.isNotEmpty() && keys.all { it !in collapsed }

    /** How many sub-sections a main section holds, for the badge on its header. */
    fun sectionCount(group: SettingsHubGroupSpec): Int = group.sections.size
}
