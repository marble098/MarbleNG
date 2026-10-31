package com.marbleng.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.ConnectModeChip
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import com.marbleng.app.ui.theme.ProvideMarbleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — regression tests for the consolidated seven-hub Settings Bento,
 * inline Material 3 Expressive topic bar, and Expressive Home primitives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpressiveStudioV252Test {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sevenBroaderUmbrellasPartitionAllTwentyThreeDestinationsExactlyOnce() {
        val groups = SettingsUmbrellaGroup.entries
        assertEquals(7, groups.size)

        val flattened = groups.flatMap { it.destinations }
        assertEquals(SettingsDestination.entries.size, flattened.size)
        assertEquals(SettingsDestination.entries.toSet(), flattened.toSet())

        SettingsDestination.entries.forEach { dest ->
            val owner = SettingsHubLayout.umbrellaFor(dest)
            assertTrue(dest in owner.destinations)
            assertTrue(SettingsHubLayout.shortTopicTitleKey(dest).isNotBlank())
        }
    }

    @Test
    fun restingSettingsHubHasZeroTrailingGapsAcrossPhoneFiltersAndTabletAll() {
        SettingsHubCategory.visible.forEach { cat ->
            val specs = SettingsHubLayout.umbrellasForCategory(cat).map { group ->
                SettingsRowSpec(
                    destination = group.primaryDestination,
                    title = group.titleKey,
                    subtitle = group.summaryKey,
                    glyph = group.glyph,
                    iconTint = Color.Cyan,
                    motif = group.motif
                )
            }
            val featured = SettingsHubLayout.featuredDestinations(specs, searching = false)
            val phoneRows = SettingsHubLayout.rows(specs, columns = 2, featured = featured)
            phoneRows.forEach { row ->
                val head = row.first()
                if (head != null && head.destination !in featured) {
                    assertTrue(
                        "Non-featured phone row in ${cat.id} had an empty slot: $row",
                        row.none { it == null }
                    )
                }
            }
        }

        val allSpecs = SettingsUmbrellaGroup.entries.map { group ->
            SettingsRowSpec(
                destination = group.primaryDestination,
                title = group.titleKey,
                subtitle = group.summaryKey,
                glyph = group.glyph,
                iconTint = Color.Cyan,
                motif = group.motif
            )
        }
        val featuredAll = SettingsHubLayout.featuredDestinations(allSpecs, searching = false)
        val tabletRows = SettingsHubLayout.rows(allSpecs, columns = 3, featured = featuredAll)
        assertEquals(3, tabletRows.size)
        tabletRows.drop(1).forEach { row ->
            assertTrue("Tablet row had an empty slot: $row", row.none { it == null })
        }
    }

    @Test
    fun expressiveMotifsAreDistinctAcrossSevenHubsAndSpeakPersian() {
        val motifs = SettingsUmbrellaGroup.entries.map { it.motif }.toSet()
        assertEquals(7, motifs.size)

        SettingsUmbrellaGroup.entries.forEach { group ->
            assertTrue("Missing FA key: ${group.titleKey}", isFaLexiconKey(group.titleKey))
            assertTrue("Missing FA key: ${group.summaryKey}", isFaLexiconKey(group.summaryKey))
        }
        SettingsDestination.entries.forEach { dest ->
            val shortKey = SettingsHubLayout.shortTopicTitleKey(dest)
            assertTrue("Missing FA key: $shortKey", isFaLexiconKey(shortKey))
        }
    }

    @Test
    fun expressiveScaffoldTopicBarSwitchesSiblingDestinationsInline() {
        var active by mutableStateOf(SettingsDestination.APPEARANCE)

        compose.setContent {
            MarbleExpressiveTheme(dynamicColor = false) {
                ProvideMarbleMotion(calm = false, systemReducedMotion = false) {
                    val state = SettingsUmbrellaNavState(
                        group = SettingsUmbrellaGroup.APPEARANCE_UI,
                        active = active,
                        onSelect = { active = it }
                    )
                    CompositionLocalProvider(LocalSettingsUmbrellaNav provides state) {
                        MarbleExpressivePageScaffold(
                            title = "Look and colour",
                            subtitle = "Dark · Ocean",
                            glyph = MarbleGlyph.PALETTE,
                            tint = Color(0xFF3E67D6),
                            onBack = {}
                        ) {
                            item { Text("Appearance body") }
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Appearance & interface").assertExists()
        compose.onNodeWithTag("settings-topic-APPEARANCE").assertExists()
        compose.onNodeWithTag("settings-topic-HOME_LAYOUT").performClick()
        compose.waitForIdle()
        assertEquals(SettingsDestination.HOME_LAYOUT, active)
    }

    @Test
    fun expressiveHomeAndTilePrimitivesMorphAcrossStatesWithoutCrash() {
        var connected by mutableStateOf(false)
        var connecting by mutableStateOf(false)

        compose.mainClock.autoAdvance = false
        compose.setContent {
            MarbleExpressiveTheme(dynamicColor = false) {
                ProvideMarbleMotion(calm = false, systemReducedMotion = false) {
                    Column {
                        MarbleConnectPill(
                            connected = connected,
                            connecting = connecting,
                            disconnecting = false,
                            title = if (connected) "Disconnect" else "Connect",
                            subtitle = "VLESS · Frankfurt",
                            ticker = if (connected) "00:01:24" else "",
                            onToggle = {}
                        )
                        MarbleModeMenuChip(
                            selected = ConnectModeChip.FULL_TUN,
                            onSelect = {}
                        )
                        MarbleTrafficSparkline(
                            downBps = 512_000L,
                            upBps = 128_000L,
                            active = false
                        )
                        MarbleExpressiveTile(
                            title = "Appearance & interface",
                            subtitle = "Dark · Ocean",
                            glyph = MarbleGlyph.PALETTE,
                            tint = Color(0xFF3E67D6),
                            motif = MarbleExpressiveMotif.FLOWER,
                            indexBadge = "01",
                            topicPills = listOf("Theme & colour", "Language & font"),
                            featured = true,
                            onClick = {},
                            tag = "hub-appearance"
                        )
                        MarbleExpressiveIconButton(
                            glyph = MarbleGlyph.PLUS,
                            motif = MarbleExpressiveMotif.CLOVER,
                            prominent = true,
                            onClick = {}
                        )
                        MarblePetalMark(
                            color = Color(0x333E67D6),
                            size = 48.dp,
                            motif = MarbleExpressiveMotif.CLOVER,
                            targetMotif = MarbleExpressiveMotif.FLOWER,
                            morphProgress = if (connected) 1f else 0f
                        )
                        MarbleOrbitLens(
                            tint = Color(0xFF3E67D6),
                            modifier = Modifier.size(64.dp),
                            motif = MarbleExpressiveMotif.BURST
                        )
                        MarbleServerAvatar(profile = null, countryCode = "DE")
                    }
                }
            }
        }

        connecting = true
        repeat(15) { compose.mainClock.advanceTimeBy(16L) }
        connected = true
        connecting = false
        repeat(40) { compose.mainClock.advanceTimeBy(16L) }
        connected = false
        repeat(40) { compose.mainClock.advanceTimeBy(16L) }

        compose.onNodeWithTag("hub-appearance").assertExists()
    }
}
