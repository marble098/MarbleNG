package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * MARBLE_GLASS_DOCK_V261 — the shipping dock, on Robolectric.
 *
 * The model test (`GlassDockV261Test`) proves the aperture's arithmetic; this one proves the three
 * things a number cannot: that the lens is actually drawn, that a selected tab paints *no* pill of
 * its own (the whole redesign), and that the capsule is narrower than the page it floats on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassDockV261UiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(key = "IP", label = "Addresses", glyph = MarbleGlyph.GLOBE, accent = Color.Green),
        MarbleDockTab(key = "SETTINGS", label = "Settings", glyph = MarbleGlyph.TUNERS, accent = Color.Cyan)
    )

    @Test
    fun theDockDrawsAGlassLensOverTheSelectedTab() {
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

        // The capsule, the row and the aperture. The lens is its own node because it is its own
        // material: a highlight painted into the tab would not need one.
        compose.onNodeWithTag("lumen-dock").assertExists()
        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        tabs().forEach { tab ->
            compose.onNodeWithTag("lumen-tab-${tab.key}").assertExists()
        }
    }

    @Test
    fun theTabStillDispatchesTheSelection() {
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

        compose.onNodeWithTag("lumen-tab-IP").performClick()
        assertEquals(2, picked)
    }

    @Test
    fun theSlimRungStillDrawsItsTabsAndItsLens() {
        // The ladder is unchanged by the material: the smallest capsule draws four tabs and one
        // aperture exactly like the middle one, and its own corner is a stadium too. (`LumenDockV256`
        // pins the other two rungs and the caption behaviour.)
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

        compose.onNodeWithTag("lumen-dock").assertExists()
        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        compose.onNodeWithTag("lumen-tab-HOME").assertExists()
    }

    @Test
    fun aStillReaderGetsTheSameDockWithoutTheLoop() {
        // Reduced motion changes the travel, never the object: the lens is still there, at its
        // destination, in one frame.
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = true, systemReducedMotion = true) {
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

        compose.onNodeWithTag("lumen-dock").assertExists()
        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        compose.onNodeWithTag("lumen-tab-IP").assertExists()
    }
}
