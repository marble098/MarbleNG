package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal data class SettingsHubSectionSpec(
    val title: String,
    val subtitle: String = "",
    val content: @Composable () -> Unit
)

internal data class SettingsHubGroupSpec(
    val key: String,
    val title: String,
    val subtitle: String,
    val tone: Color,
    val sections: List<SettingsHubSectionSpec>,
    // MARBLE_AURORA_SURFACE_V219 — the group carries a glyph from the product's one icon grid
    // instead of a composable lambda. A lambda was how five different line weights ended up on
    // five Settings headers; a glyph cannot disagree with the tile that mounts it.
    val glyph: MarbleGlyph = MarbleGlyph.SPARK
)

internal object SettingsHubGroups {
    val Keys: List<String> = listOf(
        "home",
        "connection",
        "measurement",
        "data",
        "appearance",
        "system"
    )
}

internal object SettingsHubPolicy {
    fun isOpen(collapsed: Set<String>, key: String): Boolean = key !in collapsed

    fun toggled(collapsed: Set<String>, key: String): Set<String> =
        if (key in collapsed) collapsed - key else collapsed + key

    fun expandAll(): Set<String> = emptySet()

    fun collapseAll(keys: Collection<String>): Set<String> = keys.toSet()

    fun openCount(collapsed: Set<String>, keys: Collection<String>): Int =
        keys.count { it !in collapsed }

    fun allOpen(collapsed: Set<String>, keys: Collection<String>): Boolean =
        keys.isNotEmpty() && keys.all { it !in collapsed }

    fun sectionCount(group: SettingsHubGroupSpec): Int = group.sections.size
}
