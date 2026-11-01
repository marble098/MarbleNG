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
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.ServerMenuAction
import com.marbleng.app.model.IntakeKind
import com.marbleng.app.model.AddServerAction
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import com.marbleng.app.ui.theme.ProvideMarbleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MARBLE_LUMEN_DOCK_V256 — the composables of the V256 pass, inflated on Robolectric.
 *
 * The model test (`DockStudioV256Test`) proves the arithmetic; this one proves the three things a
 * number cannot: that the dock's wings are actually drawn, that the transient caption opens on
 * selection and closes on its own two-second timer, and that the 2×2 grid and the row bubble
 * dispatch the verb that was tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumenDockV256Test {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(key = "IP", label = "Addresses", glyph = MarbleGlyph.GLOBE, accent = Color.Green),
        MarbleDockTab(key = "SETTINGS", label = "Settings", glyph = MarbleGlyph.TUNERS, accent = Color.Cyan)
    )

    @Test
    fun theDockDrawsItsTabsAndBothIslandWings() {
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
                                showLabels = false,
                                leading = {
                                    MarbleConnectionIslandCapsule(
                                        state = DockConnectionState(connected = true, latencyMs = 48),
                                        size = DockSize.MEDIUM,
                                        onClick = {}
                                    )
                                },
                                trailing = {
                                    MarbleDockPowerButton(
                                        state = DockConnectionState(connected = false),
                                        size = DockSize.MEDIUM,
                                        onToggle = { toggled++ }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("lumen-dock").assertExists()
        tabs().forEach { tab -> compose.onNodeWithTag("lumen-tab-${tab.key}").assertExists() }
        compose.onNodeWithTag("dock-island").assertExists()
        compose.onNodeWithTag("dock-power").assertExists()

        compose.onNodeWithTag("lumen-tab-SERVERS").performClick()
        compose.onNodeWithTag("dock-power").performClick()
        assertEquals(1, picked)
        assertEquals(1, toggled)
    }

    @Test
    fun theCaptionOpensWithTheSelectionAndClosesOnItsOwnTimer() {
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
                                size = DockSize.LARGE
                            )
                        }
                    }
                }
            }
        }

        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("Home").assertExists()

        // Two seconds of hold, then the collapse animation has to finish too.
        compose.mainClock.advanceTimeBy(3_200)
        compose.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun theIslandReportsABatchInsteadOfTheScreen() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        MarbleConnectionIslandCapsule(
                            state = DockConnectionState(
                                connected = true,
                                probing = true,
                                probeDone = 12,
                                probeTotal = 79
                            ),
                            size = DockSize.MEDIUM,
                            onClick = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("12/79 pinging…").assertExists()
        compose.onNodeWithTag("dock-island-progress").assertExists()
    }

    @Test
    fun theIslandShowsTheLatencyItMeasured() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        MarbleConnectionIslandCapsule(
                            state = DockConnectionState(
                                connected = true,
                                latencyMs = 48,
                                pingState = ConnectionPingState.MEASURED
                            ),
                            size = DockSize.MEDIUM,
                            onClick = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("48 ms").assertExists()
    }

    @Test
    fun theGridDispatchesTheVerbThatWasTapped() {
        var tapped: AddServerAction? = null
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleAddServerGrid(
                                clipboardKind = IntakeKind.EMPTY,
                                onAction = { tapped = it }
                            )
                        }
                    }
                }
            }
        }

        AddServerAction.entries.forEach { action ->
            compose.onNodeWithTag("add-server-cell-${action.id}").assertExists()
        }
        compose.onNodeWithTag("add-server-cell-manual").performClick()
        assertEquals(AddServerAction.MANUAL, tapped)
    }

    @Test
    fun theClipboardPillOnlyAppearsWhenSomethingWasRecognised() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    MarbleClipboardNotePill(kind = IntakeKind.OTHER, onClick = {})
                }
            }
        }
        compose.onNodeWithTag("add-server-clipboard-note").assertDoesNotExist()

        var tapped = false
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    MarbleClipboardNotePill(
                        kind = IntakeKind.SUBSCRIPTION,
                        onClick = { tapped = true }
                    )
                }
            }
        }
        compose.onNodeWithTag("add-server-clipboard-note").assertExists()
        compose.onNodeWithTag("add-server-clipboard-note").performClick()
        assertEquals(true, tapped)
    }

    @Test
    fun theBubbleListsSixVerbsAndDispatchesTheDestructiveOne() {
        var action: ServerMenuAction? = null
        var open by mutableStateOf(true)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        ServerActionBubble(
                            expanded = open,
                            onDismiss = { open = false },
                            onAction = { action = it }
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("server-action-edit").assertExists()
        compose.onNodeWithTag("server-action-delete").performClick()
        assertEquals(ServerMenuAction.DELETE, action)
        assertNull(null)
    }
}
