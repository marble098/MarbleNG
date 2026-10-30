package com.marbleng.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_EXPRESSIVE_BENTO_V250 — the Settings hub may change shape, but it may never lose a
 * destination, duplicate one, or let a featured tile swallow a search result.
 */
class SettingsHubLayoutV250Test {
    private fun spec(destination: SettingsDestination) = SettingsRowSpec(
        destination = destination,
        title = destination.name,
        subtitle = "",
        glyph = MarbleGlyph.INFO,
        iconTint = Color.Unspecified
    )

    @Test
    fun narrowPhonesGetTwoColumnsAndWideWindowsGetThree() {
        assertEquals(2, SettingsHubLayout.columns(320))
        assertEquals(2, SettingsHubLayout.columns(599))
        assertEquals(3, SettingsHubLayout.columns(600))
        assertEquals(3, SettingsHubLayout.columns(900))
    }

    @Test
    fun everyDestinationSurvivesTheBentoExactlyOnce() {
        val items = SettingsDestination.entries.map(::spec)
        listOf(2, 3).forEach { columns ->
            val rows = SettingsHubLayout.rows(
                items = items,
                columns = columns,
                featured = setOf(SettingsDestination.CONNECTION_MODE, SettingsDestination.APPEARANCE)
            )
            val flattened = rows.flatten().filterNotNull()
            assertEquals(items, flattened)
            assertTrue(rows.all { it.size == columns })
        }
    }

    @Test
    fun aFeaturedTileOwnsItsRow() {
        val items = listOf(
            spec(SettingsDestination.CONNECTION_MODE),
            spec(SettingsDestination.CORE_ENGINE),
            spec(SettingsDestination.TUNNEL_DATAPATH)
        )
        val rows = SettingsHubLayout.rows(
            items = items,
            columns = 2,
            featured = setOf(SettingsDestination.CONNECTION_MODE)
        )
        assertEquals(2, rows.size)
        assertEquals(SettingsDestination.CONNECTION_MODE, rows[0][0]?.destination)
        assertEquals(null, rows[0][1])
        assertEquals(SettingsDestination.CORE_ENGINE, rows[1][0]?.destination)
        assertEquals(SettingsDestination.TUNNEL_DATAPATH, rows[1][1]?.destination)
    }

    @Test
    fun searchFlattensTheHubIntoCompactResults() {
        val items = SettingsDestination.entries.map(::spec)
        assertTrue(SettingsHubLayout.featuredDestinations(items, searching = true).isEmpty())
        assertEquals(
            setOf(SettingsDestination.CONNECTION_MODE),
            SettingsHubLayout.featuredDestinations(items, searching = false)
        )
    }

    @Test
    fun unknownCategoryFallsBackToAll() {
        assertEquals(SettingsHubCategory.ALL, SettingsHubCategory.fromId(null))
        assertEquals(SettingsHubCategory.ALL, SettingsHubCategory.fromId("missing"))
        assertEquals(SettingsHubCategory.NETWORK, SettingsHubCategory.fromId("network"))
    }
}
