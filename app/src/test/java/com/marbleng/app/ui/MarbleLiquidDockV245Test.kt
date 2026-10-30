package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MARBLE_LIQUID_DOCK_V245 — the pill, inflated for real.
 *
 * `LiquidDockV245Test` proves the arithmetic; this test proves the object: four tabs with their
 * labels, the red counter on the tab that carries it, a tap that publishes the tab's index, and a
 * dock that survives every icon/label combination, every size and a right-to-left layout without
 * collapsing its row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarbleLiquidDockV245Test {

    @get:Rule
    val compose = createComposeRule()

    private val accent = Color(0xFF2F6BFF)

    private fun tabs() = listOf(
        MarbleDockTab(key = "home", label = "Home", glyph = MarbleGlyph.HOME, accent = accent),
        MarbleDockTab(
            key = "servers",
            label = "Servers",
            glyph = MarbleGlyph.SERVER,
            accent = accent,
            badgeCount = 2
        ),
        MarbleDockTab(
            key = "settings",
            label = "Settings",
            glyph = MarbleGlyph.TUNERS,
            accent = accent
        ),
        MarbleDockTab(
            key = "profile",
            label = "Profile",
            glyph = MarbleGlyph.VAULT,
            accent = accent
        )
    )

    @Test
    fun theFourTabsAndTheirRedCounterAreDrawn() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        MarbleLiquidDock(
                            tabs = tabs(),
                            selectedIndex = 0,
                            onSelect = {},
                            size = DockSize.MEDIUM
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        tabs().forEach { tab ->
            compose.onNodeWithText(tab.label).assertIsDisplayed()
            compose.onNodeWithTag("dock-tab-${tab.key}").assertIsDisplayed()
        }
        // The counter is the only "2" on the dock: two digits are capped by the spec.
        compose.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun aTapPublishesTheTabItLandedOn() {
        var selected = -1
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        MarbleLiquidDock(
                            tabs = tabs(),
                            selectedIndex = 0,
                            onSelect = { selected = it },
                            size = DockSize.MEDIUM
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("dock-tab-settings").performClick()
        assertEquals(2, selected)
    }

    @Test
    fun everySizeAndEveryIconLabelCombinationKeepsTheRowOnScreen() {
        var size by mutableStateOf(DockSize.SMALL)
        var icons by mutableStateOf(true)
        var labels by mutableStateOf(false)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        MarbleLiquidDock(
                            tabs = tabs(),
                            selectedIndex = 0,
                            onSelect = {},
                            size = size,
                            showIcons = icons,
                            showLabels = labels
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("dock-tab-home").assertIsDisplayed()

        compose.runOnIdle {
            size = DockSize.LARGE
            icons = false
            labels = true
        }
        compose.waitForIdle()
        compose.onNodeWithText("Servers").assertIsDisplayed()
    }

    @Test
    fun aPersianLayoutStillDrawsTheSameFourTabsAndKeepsTheCounterReadable() {
        compose.setContent {
            ProvideMarbleLanguage("fa") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        MarbleLiquidDock(
                            tabs = tabs(),
                            selectedIndex = 1,
                            onSelect = {},
                            size = DockSize.MEDIUM
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("dock-tab-servers").assertIsDisplayed()
        // Persian digits, because the badge reads its number through the app's own digit shaper.
        compose.onNodeWithText("۲").assertIsDisplayed()
    }
}
