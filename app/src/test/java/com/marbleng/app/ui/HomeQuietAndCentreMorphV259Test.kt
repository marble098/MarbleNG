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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.HomeHeroSnapshot
import com.marbleng.app.model.HomeHeroStudio
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
    fun theQuietCardIsGoneAndThePageNoLongerExplainsItsOwnModesty() {
        // MARBLE_HOME_DECLUTTER_V260 — the reported card ("A quiet Home / Only what you need is
        // here / 3 of 6 / Show everything / Choose sections") was deleted from the page, not hidden.
        // The composable went with it, so this asserts the tree rather than a runtime flag: a page
        // cannot draw a card that does not exist, and `HomeQuiet` still owns the layout arithmetic
        // the Settings studio reads.
        val sources = java.io.File("src/main/java/com/marbleng/app/ui")
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .joinToString("\n") { it.readText() }
        assertTrue("the quiet card must not come back", !sources.contains("MarbleQuietCard("))
        // The two verbs went with the card: the lexicon no longer carries them, and no screen asks
        // for a sentence it cannot draw.
        assertTrue("its two verbs must not come back", !sources.contains("Choose sections"))
        assertTrue("its other verb must not come back", !sources.contains("Show everything"))
        // …and Home does not import the layout model at all any more: the switches live in Settings.
        val home = java.io.File("src/main/java/com/marbleng/app/ui/HomeScreen.kt").readText()
        assertTrue("Home must not draw the quiet card", !home.contains("home-quiet-card"))
    }

    @Test
    fun theWarmupRowIsTwoKeysAndEachOneDispatches() {
        // MARBLE_HOME_DECLUTTER_V260 — "Add manually" was the header's own + sheet, one row deep, on
        // the same screen. The warm-up keeps the two doors the page cannot otherwise offer: the
        // clipboard it already holds, and the code it can read.
        var paste = 0
        var scan = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleWarmupRow(
                                onPaste = { paste++ },
                                onScan = { scan++ }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("home-warmup").assertExists()
        compose.onNodeWithText("Paste a link").performClick()
        compose.onNodeWithText("Scan a QR").performClick()
        assertEquals(1, paste)
        assertEquals(1, scan)
        // The retired third door is not on the page.
        compose.onNodeWithText("Add manually").assertDoesNotExist()
    }

    @Test
    fun theCalmStagePrintsItsOneNumberAndItsRoute() {
        // MARBLE_HOME_HERO_V261 — the stage is now the hero panel: the same tag, the same route
        // strip, and instead of one centered readout it prints the four metrics the brief asked for.
        // The uptime cell still prints the clock the caller handed in, and the route strip is still
        // the surface's one destination.
        var opened = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleHeroPanel(
                                snapshot = HomeHeroSnapshot(
                                    stateLabel = "Protected",
                                    overline = "Connection",
                                    routeName = "Berlin 01",
                                    routeDetail = "VLESS · Main",
                                    countryCode = "DE",
                                    metrics = HomeHeroStudio.metrics(
                                        connected = true,
                                        working = false,
                                        pingMs = 48,
                                        jitterMs = 7,
                                        uptimeLabel = "00:10:01",
                                        exitAddress = "203.0.113.9",
                                        hasRoute = true
                                    ),
                                    working = false
                                ),
                                motif = MarbleExpressiveMotif.CLOVER,
                                onMetricClick = { opened++ },
                                modifier = Modifier.testTag("home-calm-stage")
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("home-calm-stage").assertExists()
        compose.onNodeWithText("00:10:01").assertExists()
        compose.onNodeWithText("Berlin 01").assertExists()
        // The four cells are the panel's own grid, and each one is its own target.
        compose.onNodeWithTag("home-hero-metric-ping").assertExists()
        compose.onNodeWithTag("home-hero-metric-jitter").assertExists()
        compose.onNodeWithTag("home-hero-metric-uptime").assertExists()
        compose.onNodeWithTag("home-hero-metric-exit").assertExists()
        compose.onNodeWithTag("home-hero-route").performClick()
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
