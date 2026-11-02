package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * MARBLE_STEADY_CONNECT_V264 — the reported bug, inflated on Robolectric.
 *
 * "دکمه اتصال مرکزی ... باعث حرکت دکمه های تب بعد از اتصال می شود. متن کنار آن باز میشود که نباید
 * این اتفاق بیفتد." One test measures the four tabs and the centre trigger in all five phases and
 * demands byte-identical rectangles; the others pin the missing word, the press rules and the two
 * phases the old controls did not know about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SteadyConnectControlsV264Test {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(key = "IP", label = "Addresses", glyph = MarbleGlyph.GLOBE, accent = Color.Green),
        MarbleDockTab(key = "SETTINGS", label = "Settings", glyph = MarbleGlyph.TUNERS, accent = Color.Cyan)
    )

    private val phases: List<DockConnectionState> = listOf(
        DockConnectionState(connected = false),
        DockConnectionState(connecting = true),
        DockConnectionState(connected = true, latencyMs = 48),
        DockConnectionState(disconnecting = true),
        DockConnectionState(blocked = true),
        DockConnectionState(connected = false)
    )

    @Test
    fun theCentreTriggerKeepsEveryTabWhereItWasInAllFivePhases() {
        var state by mutableStateOf(phases.first())
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 0,
                                onSelect = {},
                                size = DockSize.MEDIUM,
                                centerProud = true,
                                center = {
                                    MarbleCenterSpringTrigger(
                                        state = state,
                                        size = DockSize.MEDIUM,
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
        compose.waitForIdle()

        val keys = listOf("HOME", "SERVERS", "IP", "SETTINGS")
        val idleTabs = keys.associateWith { key ->
            compose.onNodeWithTag("lumen-tab-$key").getBoundsInRoot()
        }
        val idleCentre = compose.onNodeWithTag("dock-center").getBoundsInRoot()

        phases.forEachIndexed { index, next ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            keys.forEach { key ->
                assertEquals(
                    "phase $index moved tab $key",
                    idleTabs[key],
                    compose.onNodeWithTag("lumen-tab-$key").getBoundsInRoot()
                )
            }
            assertEquals(
                "phase $index resized the centre trigger",
                idleCentre,
                compose.onNodeWithTag("dock-center").getBoundsInRoot()
            )
        }
    }

    @Test
    fun aTapOnTheCentreTriggerOpensNoWordAndFiresExactlyOneAction() {
        var state by mutableStateOf(DockConnectionState())
        var toggled = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleCenterSpringTrigger(
                                state = state,
                                size = DockSize.MEDIUM,
                                onToggle = { toggled++ },
                                onPing = {}
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        // The four pressable phases each dispatch once; the closing phase owns no verb.
        var expected = 0
        phases.take(5).forEachIndexed { index, next ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            if (next.pressable) {
                compose.onNodeWithTag("dock-center-trigger").assertIsEnabled().performClick()
                compose.waitForIdle()
                expected++
            } else {
                compose.onNodeWithTag("dock-center-trigger").assertIsNotEnabled()
            }
            // No state word, no latency and no timed tooltip — in any phase, after a tap.
            compose.onNodeWithText("Ready").assertDoesNotExist()
            compose.onNodeWithText("Connected").assertDoesNotExist()
            compose.onNodeWithText("48 ms").assertDoesNotExist()
            compose.onNodeWithTag("dock-center-tooltip").assertDoesNotExist()
            assertEquals("phase $index fired the wrong number of toggles", expected, toggled)
        }
        // Exactly one action per pressable phase: IDLE, CONNECTING, CONNECTED, BLOCKED.
        assertEquals(4, toggled)
    }

    @Test
    fun thePowerButtonAndTheHeroPillShareTheSamePressRules() {
        var state by mutableStateOf(DockConnectionState())
        var powerToggles = 0
        var pillToggles = 0
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Column(Modifier.width(320.dp)) {
                            MarbleDockPowerButton(
                                state = state,
                                size = DockSize.MEDIUM,
                                onToggle = { powerToggles++ }
                            )
                            MarbleConnectPill(
                                connected = state.connected,
                                connecting = state.connecting,
                                disconnecting = state.disconnecting,
                                blocked = state.blocked,
                                title = "Toggle",
                                subtitle = "Route",
                                onToggle = { pillToggles++ }
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        var expected = 0
        phases.take(5).forEachIndexed { index, next ->
            compose.runOnIdle { state = next }
            compose.waitForIdle()
            if (next.pressable) {
                compose.onNodeWithTag("dock-power").assertIsEnabled().performClick()
                compose.onNodeWithContentDescription("Toggle").assertIsEnabled().performClick()
                compose.waitForIdle()
                expected++
            } else {
                compose.onNodeWithTag("dock-power").assertIsNotEnabled()
                compose.onNodeWithContentDescription("Toggle").assertIsNotEnabled()
            }
            assertEquals("phase $index: power", expected, powerToggles)
            assertEquals("phase $index: pill", expected, pillToggles)
        }
        assertEquals(4, powerToggles)
        assertEquals(4, pillToggles)
    }

    @Test
    fun theHeroPillKeepsOneHeightAndPrintsTheVerbOfTheTap() {
        var state by mutableStateOf(DockConnectionState())
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(320.dp)) {
                            MarbleConnectPill(
                                connected = state.connected,
                                connecting = state.connecting,
                                disconnecting = state.disconnecting,
                                blocked = state.blocked,
                                title = when {
                                    state.connected -> "Disconnect"
                                    state.connecting -> "Cancel connect"
                                    state.blocked -> "Reset"
                                    else -> "Connect"
                                },
                                subtitle = "Route",
                                onToggle = {}
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val resting = compose.onNodeWithContentDescription("Connect").getBoundsInRoot()

        // A dial must not say "Connect" while the tap cancels it, and a kill switch must not say
        // "Connect" while the tap resets it.
        compose.runOnIdle { state = DockConnectionState(connecting = true) }
        compose.waitForIdle()
        compose.onNodeWithText("Cancel connect").assertExists()
        compose.onNodeWithContentDescription("Connect").assertDoesNotExist()
        assertEquals(
            "connecting resized the pill",
            resting,
            compose.onNodeWithContentDescription("Cancel connect").getBoundsInRoot()
        )

        compose.runOnIdle { state = DockConnectionState(connected = true) }
        compose.waitForIdle()
        compose.onNodeWithText("Disconnect").assertExists()
        assertEquals(
            "connected resized the pill",
            resting,
            compose.onNodeWithContentDescription("Disconnect").getBoundsInRoot()
        )

        compose.runOnIdle { state = DockConnectionState(blocked = true) }
        compose.waitForIdle()
        compose.onNodeWithText("Reset").assertExists()
        compose.onNodeWithContentDescription("Connect").assertDoesNotExist()
        assertEquals(
            "blocked resized the pill",
            resting,
            compose.onNodeWithContentDescription("Reset").getBoundsInRoot()
        )
    }
}
