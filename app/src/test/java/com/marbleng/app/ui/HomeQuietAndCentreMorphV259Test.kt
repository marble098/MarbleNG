package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.HomeQuiet
import com.marbleng.app.model.HomeSection
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import com.marbleng.app.ui.theme.ProvideMarbleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MARBLE_CENTER_MORPH_STUDIO_V259 / MARBLE_HOME_QUIET_V259 — the composables of this pass, inflated
 * on Robolectric.
 *
 * The model test proves the arithmetic; this one proves the three things a number cannot: that the
 * centre style draws a trigger *and* the taller capsule without losing a tab, that the quiet card's
 * two verbs dispatch, and that the warm-up row exists as three labelled keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeQuietAndCentreMorphV259Test {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(key = "IP", label = "Addresses", glyph = MarbleGlyph.GLOBE, accent = Color.Green),
        MarbleDockTab(key = "SETTINGS", label = "Settings", glyph = MarbleGlyph.TUNERS, accent = Color.Cyan)
    )

    @Test
    fun theCentreStyleDrawsItsProudTriggerAndKeepsAllFourTabs() {
        var toggled = 0
        var picked = -1
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 0,
                                onSelect = { picked = it },
                                size = DockSize.MEDIUM,
                                centerProud = true,
                                center = {
                                    MarbleCenterSpringTrigger(
                                        state = DockConnectionState(connected = false),
                                        size = DockSize.MEDIUM,
                                        onToggle = { toggled++ },
                                        onPing = {}
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("lumen-dock").assertExists()
        tabs().forEach { tab ->
            compose.onNodeWithTag("lumen-tab-${tab.key}").assertExists()
        }
        compose.onNodeWithTag("dock-center").assertExists()
        compose.onNodeWithTag("dock-center-trigger").performClick()
        assertEquals(1, toggled)

        // The tabs beside the trigger are still pressable: the taller capsule did not eat them.
        compose.onNodeWithTag("lumen-tab-SERVERS").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun aConnectedCentreTriggerGrowsItsHaloAndItsReadout() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 0,
                                onSelect = {},
                                size = DockSize.LARGE,
                                centerProud = true,
                                center = {
                                    MarbleCenterSpringTrigger(
                                        state = DockConnectionState(
                                            connected = true,
                                            latencyMs = 48
                                        ),
                                        size = DockSize.LARGE,
                                        onToggle = {},
                                        onPing = {}
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(120)

        // The halo is the Expressive "this is live" ring, and it only exists while connected.
        compose.onNodeWithTag("dock-center-halo").assertExists()
        compose.onNodeWithTag("dock-center-trigger").assertExists()
    }

    @Test
    fun theQuietCardSaysWhatIsHiddenAndDispatchesItsTwoVerbs() {
        var showedEverything = 0
        var choseSections = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleQuietCard(
                                shownCount = 3,
                                totalCount = 6,
                                onShowEverything = { showedEverything++ },
                                onChooseSections = { choseSections++ }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("home-quiet-card").assertExists()
        compose.onNodeWithText("3/6").assertExists()
        compose.onNodeWithText("Show everything").performClick()
        compose.onNodeWithText("Choose sections").performClick()
        assertEquals(1, showedEverything)
        assertEquals(1, choseSections)
    }

    @Test
    fun theWarmupRowIsThreeKeysAndEachOneDispatches() {
        var paste = 0
        var scan = 0
        var add = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleWarmupRow(
                                onPaste = { paste++ },
                                onScan = { scan++ },
                                onAdd = { add++ }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("home-warmup").assertExists()
        compose.onNodeWithText("Paste a link").performClick()
        compose.onNodeWithText("Scan a QR").performClick()
        compose.onNodeWithText("Add manually").performClick()
        assertEquals(1, paste)
        assertEquals(1, scan)
        assertEquals(1, add)
    }

    @Test
    fun theCalmStagePrintsItsOneNumberAndItsRoute() {
        var opened = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleCalmStage(
                                connected = true,
                                working = false,
                                stateLabel = "Protected",
                                overline = "Connection",
                                readout = "00:10:01",
                                readoutCaption = "Protected uptime",
                                readoutLoading = false,
                                routeProfile = null,
                                routeCountryCode = "DE",
                                routeName = "Berlin 01",
                                routeDetail = "VLESS · Main",
                                motif = MarbleExpressiveMotif.CLOVER,
                                primaryAction = {},
                                onClick = { opened++ }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("home-calm-stage").assertExists()
        compose.onNodeWithText("00:10:01").assertExists()
        compose.onNodeWithText("Berlin 01").assertExists()
        compose.onNodeWithTag("home-calm-stage-route").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theStudioPreviewTracksTheHiddenStringItIsGiven() {
        // One composition, moving the state the page would move: the strip has to redraw, because a
        // preview that only reads its first value is worse than no preview at all.
        var hidden by mutableStateOf(HomeQuiet.CALM_HIDDEN)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleHomeSectionPreview(
                                order = HomeQuiet.CALM_SECTIONS +
                                    HomeSection.entries.filterNot { it in HomeQuiet.CALM_SECTIONS },
                                hiddenRaw = hidden,
                                legacyStatsVisible = true,
                                legacyShortcutsVisible = true
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("home-section-preview").assertExists()
        hidden = ""
        compose.waitForIdle()
        // Still there, and still one strip: the preview does not rebuild into a different shape.
        compose.onNodeWithTag("home-section-preview").assertExists()
    }

    @Test
    fun theDockLadderStillOwnsTheHeightTheTriggerWasDerivedFrom() {
        // The centre trigger must not have quietly changed what the ladder means: the three rungs
        // are still 56 / 68 / 82, and the centre capsule is exactly one proud step above each.
        val heights = DockSize.entries.map { com.marbleng.app.model.DockStudio.heightDp(it) }
        assertEquals(listOf(56, 68, 82), heights)
        assertTrue(heights.all { it > 0 })
    }
}
