package com.marbleng.app.ui

import androidx.compose.ui.graphics.Color

/**
 * MARBLE_EXPRESSIVE_BENTO_V250 — the Settings hub's pure layout arithmetic.
 *
 * The hub keeps every existing destination reachable, but presents each category as a small
 * bento: one featured tile spans the row, the remaining tiles share equal columns. Search
 * flattens the bento back into compact results so a keyword never hides behind a feature card.
 */
internal data class SettingsRowSpec(
    val destination: SettingsDestination,
    val title: String,
    val subtitle: String,
    val glyph: MarbleGlyph,
    val iconTint: Color
)

internal enum class SettingsHubCategory(val id: String, val titleKey: String) {
    ALL("all", "All"),
    CONNECTION("connection", "Connection"),
    NETWORK("network", "Network"),
    TESTS("tests", "Tests"),
    APPEARANCE("appearance", "Appearance"),
    SYSTEM("system", "System");

    companion object {
        fun fromId(raw: String?): SettingsHubCategory =
            entries.firstOrNull { it.id == raw } ?: ALL
    }
}

internal object SettingsHubLayout {
    const val WIDE_MIN_WIDTH_DP: Int = 600

    fun columns(widthDp: Int): Int = if (widthDp >= WIDE_MIN_WIDTH_DP) 3 else 2

    fun featuredDestinations(
        rows: List<SettingsRowSpec>,
        searching: Boolean
    ): Set<SettingsDestination> =
        if (searching || rows.isEmpty()) emptySet() else setOf(rows.first().destination)

    /**
     * Returns rows padded with nulls to [columns]. A featured destination always occupies its
     * own row. Order is preserved and no destination is dropped or duplicated.
     */
    fun rows(
        items: List<SettingsRowSpec>,
        columns: Int,
        featured: Set<SettingsDestination> = emptySet()
    ): List<List<SettingsRowSpec?>> {
        val cols = columns.coerceAtLeast(1)
        if (items.isEmpty()) return emptyList()
        val result = mutableListOf<List<SettingsRowSpec?>>()
        val current = mutableListOf<SettingsRowSpec?>()

        fun flush() {
            while (current.size < cols) current.add(null)
            result.add(current.toList())
            current.clear()
        }

        items.forEach { item ->
            if (item.destination in featured) {
                if (current.isNotEmpty()) flush()
                result.add(listOf(item) + List(cols - 1) { null })
            } else {
                current.add(item)
                if (current.size == cols) flush()
            }
        }
        if (current.isNotEmpty()) flush()
        return result
    }
}
