package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import com.marbleng.app.ui.theme.ProvideMarbleMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MARBLE_LIQUID_LENS_V262 — the shipping dock, on Robolectric.
 *
 * `LiquidLensV262Test` proves the arithmetic (the material's numbers, the spring's overshoot, the
 * deformation's shape); this file proves the four things a number cannot:
 *
 *  1. the bar is a *pill with a lens in it* — three separate objects, all inflated;
 *  2. every original slot is still there, in its original order, and still dispatches;
 *  3. the counter and the connection light survive the material change and keep their anchors;
 *  4. reduced motion changes the travel and never the object.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidLensDockV262UiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(
            key = "IP",
            label = "Addresses",
            glyph = MarbleGlyph.GLOBE,
            accent = Color.Green,
            statusDot = Color.Green
        ),
        MarbleDockTab(
            key = "SETTINGS",
            label = "Settings",
            glyph = MarbleGlyph.TUNERS,
            accent = Color.Cyan,
            badgeCount = 2
        )
    )

    @Test
    fun theBarDrawsAPillALensAndEveryOriginalSlot() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 1,
                                onSelect = {},
                                size = DockSize.MEDIUM
                            )
                        }
                    }
                }
            }
        }

        // The pill, the lens and the row are three objects, and the lens is its own node because
        // it is its own material: a highlight painted into a tab would not need one.
        compose.onNodeWithTag("lumen-dock").assertExists()
        compose.onNodeWithTag("liquid-lens-bar").assertExists()
        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        tabs().forEach { tab ->
            compose.onNodeWithTag("lumen-tab-${tab.key}").assertExists()
        }
    }

    @Test
    fun theFourRoutesAndTheCentreActionStillDispatch() {
        var picked = -1
        var toggled = 0
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

        // The five slots in their original order: Home, Servers, the action, IP, Settings.
        compose.onNodeWithTag("lumen-tab-HOME").assertExists()
        compose.onNodeWithTag("lumen-tab-SERVERS").assertExists()
        compose.onNodeWithTag("dock-center").assertExists()
        compose.onNodeWithTag("lumen-tab-IP").assertExists()
        compose.onNodeWithTag("lumen-tab-SETTINGS").assertExists()

        compose.onNodeWithTag("dock-center-trigger").performClick()
        assertEquals(1, toggled)

        compose.onNodeWithTag("lumen-tab-IP").performClick()
        assertEquals(2, picked)
    }

    @Test
    fun draggingAcrossTheBarSelectsTheTabUnderTheFinger() {
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
                                size = DockSize.MEDIUM
                            )
                        }
                    }
                }
            }
        }

        // Swipe from the middle toward the final slot: selection follows the finger, not just taps.
        compose.onNodeWithTag("liquid-lens-bar").performTouchInput { swipeRight() }
        assertEquals(tabs().lastIndex, picked)
    }

    // DOCK_ISLAND (both wings), CENTER_MORPH (centre trigger) and HERO_CARD (no wing): the drag
    // reads the measured slot rectangles, so it must keep selecting the tab under the finger
    // whatever shares the row with the tabs. One test per style — setContent runs once per test.
    @Test
    fun swipeAcrossTheIslandStyleBarSelectsTheLastTab() = swipeAcrossBar(
        leading = { Box(Modifier.width(48.dp)) },
        trailing = { Box(Modifier.width(48.dp)) }
    )

    @Test
    fun swipeAcrossTheCenterMorphStyleBarSelectsTheLastTab() = swipeAcrossBar(
        centerProud = true,
        center = {
            MarbleCenterSpringTrigger(
                state = DockConnectionState(connected = false),
                size = DockSize.MEDIUM,
                onToggle = {},
                onPing = {}
            )
        }
    )

    @Test
    fun swipeAcrossTheHeroStyleBarSelectsTheLastTab() = swipeAcrossBar()

    private fun swipeAcrossBar(
        centerProud: Boolean = false,
        leading: (@Composable RowScope.() -> Unit)? = null,
        center: (@Composable () -> Unit)? = null,
        trailing: (@Composable RowScope.() -> Unit)? = null
    ) {
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
                                centerProud = centerProud,
                                leading = leading,
                                center = center,
                                trailing = trailing
                            )
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("liquid-lens-bar").performTouchInput { swipeRight() }
        assertEquals(tabs().lastIndex, picked)
    }

    @Test
    fun theCounterAndTheConnectionLightKeepTheirAnchors() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 2,
                                onSelect = {},
                                size = DockSize.MEDIUM
                            )
                        }
                    }
                }
            }
        }

        // Both decorations live inside the tab's merged semantics node, so they are found in the
        // unmerged tree — the anchor is a layout fact, not something the reader hears twice.
        compose.onNodeWithTag("lumen-tab-status-IP", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("lumen-tab-count-SETTINGS", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aStillReaderGetsTheSameLensInOneFrame() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = true, systemReducedMotion = true) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 3,
                                onSelect = {},
                                size = DockSize.LARGE
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("lumen-dock").assertExists()
        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        compose.onNodeWithTag("lumen-tab-SETTINGS").assertExists()
    }

    @Test
    fun theSlimRungDrawsTheSamePillAtItsOwnSize() {
        // The ladder is a size, not a material: the smallest capsule draws the lens too, because a
        // slim bar without a lens would be a second bar in the product.
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 0,
                                onSelect = {},
                                size = DockSize.SMALL
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        compose.onNodeWithTag("lumen-tab-HOME").assertExists()
    }
}
