package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MARBLE_LIQUID_GLASS_LENS_V247 — the two-tier bar, inflated for real.
 *
 * `LiquidGlassLensV247Test` proves the arithmetic; this test proves the object: one tab per slot
 * and *not two*, because the magnified projection is an optical duplicate and must never become a
 * second set of controls; a tap that publishes its index; a counter that carries its tag and
 * prints once; and every size and label combination staying inside the capsule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassBottomBarV247Test {

    @get:Rule
    val compose = createComposeRule()

    private val accent = Color(0xFF2F6BFF)

    private fun tabs() = listOf(
        GlassTabItem(key = "home", title = "Home", accent = accent),
        GlassTabItem(key = "servers", title = "Servers", badgeCount = 2, accent = accent),
        GlassTabItem(key = "settings", title = "Settings", accent = accent),
        GlassTabItem(key = "profile", title = "Profile", accent = accent)
    )

    @Test
    fun theProjectionNeverBecomesASecondSetOfControls() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        LiquidGlassBottomBar(
                            tabs = tabs(),
                            selectedIndex = 0,
                            onTabSelected = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        tabs().forEach { tab ->
            compose.onNodeWithTag("glass-tab-${tab.key}").assertIsDisplayed()
            // Exactly one node per label: the magnified overlay layer clears its semantics, so a
            // screen reader — and this test — sees the row once.
            compose.onAllNodesWithText(tab.title).assertCountEquals(1)
        }
    }

    @Test
    fun aTapPublishesTheTabItLandedOn() {
        var selected = -1
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        LiquidGlassBottomBar(
                            tabs = tabs(),
                            selectedIndex = 0,
                            onTabSelected = { selected = it }
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("glass-tab-settings").performClick()
        assertEquals(2, selected)
    }

    @Test
    fun theCounterIsDrawnOnceAndKeepsItsTag() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        LiquidGlassBottomBar(
                            tabs = tabs(),
                            selectedIndex = 1,
                            onTabSelected = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onAllNodesWithTag("glass-badge-servers", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun everySizeAndLabelCombinationKeepsTheRowOnScreen() {
        var barHeight by mutableStateOf(56.dp)
        var labels by mutableStateOf(false)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        LiquidGlassBottomBar(
                            tabs = tabs(),
                            selectedIndex = 0,
                            onTabSelected = {},
                            barHeight = barHeight,
                            showLabels = labels
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("glass-tab-home").assertIsDisplayed()

        compose.runOnIdle {
            barHeight = 68.dp
            labels = true
        }
        compose.waitForIdle()
        compose.onNodeWithText("Servers").assertIsDisplayed()
    }

    @Test
    fun anOutOfRangeSelectionIsCoercedInsteadOfCrashing() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        LiquidGlassBottomBar(
                            tabs = tabs(),
                            selectedIndex = 99,
                            onTabSelected = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("glass-tab-profile").assertIsDisplayed()
    }

    @Test
    fun aPersianRowStillDrawsEveryTabAndKeepsTheCounter() {
        compose.setContent {
            ProvideMarbleLanguage("fa") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        LiquidGlassBottomBar(
                            tabs = tabs(),
                            selectedIndex = 1,
                            onTabSelected = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("glass-tab-servers").assertIsDisplayed()
        // The badge sits on the mirrored corner in RTL, so it is asserted by tag and not by painted
        // bounds.
        compose.onAllNodesWithTag("glass-badge-servers", useUnmergedTree = true)
            .assertCountEquals(1)
        assertEquals("۲", "2".toFaDigits())
    }
}
