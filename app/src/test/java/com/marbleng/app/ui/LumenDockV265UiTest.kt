package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.ConnectPhase
import com.marbleng.app.model.ConnectionWidgetStyle
import com.marbleng.app.model.DockSize
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
 * MARBLE_LUMEN_DOCK_V265 — the shipping dock's two promises, pinned in source and on Robolectric:
 *
 *  1. **The Material 3 ripple conforms to the shape lines.** The row is clipped to the capsule's
 *     exact outline ([PillOutlineShape], MARBLE_LUMEN_RIPPLE_CLIP), each tab's press target is
 *     clipped to the slot's own outline *before* `selectable` (the rule the Lumen Android reference
 *     draws in its `DockTab`: `.clip(RoundedCornerShape(26.dp))` before `.selectable(...)` —
 *     `krambovic/Lumen`, release `android-v1.5.0`, `ui/components/FloatingNavigationDock.kt`), and
 *     both wings clip before their interaction modifiers. The ripple can therefore never cross a
 *     shape line, in any layout.
 *  2. **The bottom bar is draggable.** A horizontal drag selects the tab under the finger and
 *     holds the lens under the finger while it lasts; a swipe that stops mid-bar leaves the mid
 *     tab selected. This is pinned for **all three connection-widget styles** (DOCK_ISLAND,
 *     CENTER_MORPH, HERO_CARD) in **all three connection-button states** (disconnected, connecting,
 *     connected) — nine combinations, because the wings share the row with the tabs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LumenDockV265UiTest {

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

    // ── source pins: the clip always precedes the interaction ─────────────────────────────────

    @Test
    fun theSlotClipsItsOwnRippleToItsShapeBeforeSelectable() {
        val slot = java.io.File("src/main/java/com/marbleng/app/ui/NavigationItemView.kt").readText()
        val clip = slot.indexOf(".clip(RoundedCornerShape(DockStudio.innerRadiusDp(size).dp))")
        // Search from the clip onwards: the file's header comment quotes the Lumen reference's
        // own `.selectable(...)`, and that quote is not the code.
        val selectable = if (clip >= 0) slot.indexOf(".selectable(", clip) else -1
        assertTrue("the slot must clip to the one inner corner rule", clip >= 0)
        assertTrue("the slot must be selectable", selectable >= 0)
        assertTrue("clip must come before selectable, or the ripple is not confined", clip < selectable)
        // The decorations stay outside that clip: the counter and the light may sit on the rim.
        val badge = slot.indexOf("DockBadge(")
        assertTrue("the counter is still drawn", badge > selectable)
    }

    @Test
    fun theRowAndEveryWingClipTheirRipplesToTheirShapes() {
        val bar = java.io.File("src/main/java/com/marbleng/app/ui/LiquidLensNavigationBar.kt").readText()
        assertTrue("the row keeps its capsule clip", ".clip(pillClip)" in bar)
        assertTrue("the capsule outline is expressed in row coordinates", "PillOutlineShape" in bar)

        val wings = java.io.File("src/main/java/com/marbleng/app/ui/MarbleDockWings.kt").readText()
        val islandClip = wings.indexOf(".clip(islandShape)")
        val islandClick = wings.indexOf(".clickable(role = Role.Button)")
        assertTrue("the island clips", islandClip >= 0)
        assertTrue("the island is clickable", islandClick >= 0)
        assertTrue("island: clip before clickable", islandClip < islandClick)

        val grooveClip = wings.indexOf(".clip(grooveShape)")
        val grooveClick = wings.indexOf(".combinedClickable(")
        assertTrue("the groove clips", grooveClip >= 0)
        assertTrue("the groove is pressable", grooveClick >= 0)
        assertTrue("groove: clip before combinedClickable", grooveClip < grooveClick)

        // The power button confines its own ripple through the shape it is given.
        assertTrue(
            "the power button's ripple follows its shape",
            "shape = RoundedCornerShape(DockStudio.innerRadiusDp(size).dp)" in wings
        )
    }

    @Test
    fun theDragHoldsTheLensUnderTheFingerAndSeatsItOnRelease() {
        val bar = java.io.File("src/main/java/com/marbleng/app/ui/LiquidLensNavigationBar.kt").readText()
        assertTrue("the drag writes a held lens position", "heldLensLeftPx.value = it" in bar)
        assertTrue("the held position uses the one lens clamp", "lensLeftFor" in bar)
        assertTrue("release seats the lens", "onDragEnd" in bar && "heldLensLeftPx.value = null" in bar)
        assertTrue("cancel seats the lens too", "onDragCancel" in bar)
    }

    // ── the nine combinations: three widget styles × three connection states ──────────────────

    @Test
    fun islandStyleDisconnectedRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.DOCK_ISLAND, ConnectPhase.IDLE, "dock-island", "dock-power")

    @Test
    fun islandStyleConnectingRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.DOCK_ISLAND, ConnectPhase.CONNECTING, "dock-island", "dock-power")

    @Test
    fun islandStyleConnectedRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.DOCK_ISLAND, ConnectPhase.CONNECTED, "dock-island", "dock-power")

    @Test
    fun centerMorphStyleDisconnectedRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.CENTER_MORPH, ConnectPhase.IDLE, "dock-center", "dock-center-trigger")

    @Test
    fun centerMorphStyleConnectingRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.CENTER_MORPH, ConnectPhase.CONNECTING, "dock-center", "dock-center-trigger")

    @Test
    fun centerMorphStyleConnectedRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.CENTER_MORPH, ConnectPhase.CONNECTED, "dock-center", "dock-center-trigger")

    @Test
    fun heroStyleDisconnectedRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.HERO_CARD, ConnectPhase.IDLE)

    @Test
    fun heroStyleConnectingRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.HERO_CARD, ConnectPhase.CONNECTING)

    @Test
    fun heroStyleConnectedRendersAndSwipes() =
        stylePhaseTest(ConnectionWidgetStyle.HERO_CARD, ConnectPhase.CONNECTED)

    private fun stylePhaseTest(
        style: ConnectionWidgetStyle,
        phase: ConnectPhase,
        vararg widgetTags: String
    ) {
        var picked = -1
        renderDock(style, phaseState(phase)) { picked = it }

        // The style's own connection widget is drawn in this phase, whatever the phase is.
        widgetTags.forEach { tag -> compose.onNodeWithTag(tag).assertExists() }
        if (widgetTags.isEmpty()) {
            compose.onNodeWithTag("dock-island").assertDoesNotExist()
            compose.onNodeWithTag("dock-power").assertDoesNotExist()
            compose.onNodeWithTag("dock-center").assertDoesNotExist()
        }

        // The bar itself: the pill, the lens and all four tabs, plus the counter and the light.
        compose.onNodeWithTag("lumen-dock").assertExists()
        compose.onNodeWithTag("liquid-lens-bar").assertExists()
        compose.onNodeWithTag("lumen-glass-lens").assertExists()
        tabs().forEach { tab -> compose.onNodeWithTag("lumen-tab-${tab.key}").assertExists() }
        compose.onNodeWithTag("lumen-tab-status-IP", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("lumen-tab-count-SETTINGS", useUnmergedTree = true).assertExists()

        // The bottom bar is draggable: a swipe across it selects the tab under the finger.
        compose.onNodeWithTag("liquid-lens-bar").performTouchInput { swipeRight() }
        assertEquals(tabs().lastIndex, picked)
    }

    @Test
    fun aDragThatStopsMidBarSelectsTheTabUnderTheFinger() {
        var picked = -1
        renderDock(ConnectionWidgetStyle.HERO_CARD, DockConnectionState()) { picked = it }

        // Down near the first slot, drag to just past the second slot's centre, lift: the
        // selection is the tab the finger is on, not the last tab of the bar. The node's size is
        // read through its semantics node — the injection scope of this Compose version has no
        // `size` property (CI caught it as Unresolved reference).
        val barSize = compose.onNodeWithTag("liquid-lens-bar").fetchSemanticsNode().size
        val w = barSize.width.toFloat()
        val h = barSize.height.toFloat()
        compose.onNodeWithTag("liquid-lens-bar").performTouchInput {
            swipe(Offset(w * 0.08f, h / 2f), Offset(w * 0.42f, h / 2f), durationMillis = 250L)
        }
        assertEquals(1, picked)
    }

    @Test
    fun aTapOnATabStillSelectsIt() {
        var picked = -1
        renderDock(ConnectionWidgetStyle.HERO_CARD, DockConnectionState()) { picked = it }
        compose.onNodeWithTag("lumen-tab-IP").performClick()
        assertEquals(2, picked)
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private fun phaseState(phase: ConnectPhase): DockConnectionState = when (phase) {
        ConnectPhase.CONNECTING -> DockConnectionState(connecting = true)
        ConnectPhase.CONNECTED -> DockConnectionState(connected = true, latencyMs = 48)
        else -> DockConnectionState()
    }

    private fun renderDock(
        style: ConnectionWidgetStyle,
        state: DockConnectionState,
        onSelect: (Int) -> Unit
    ) {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            val leading: (@Composable RowScope.() -> Unit)? =
                                if (style.showsDockPower) {
                                    {
                                        MarbleConnectionIslandCapsule(
                                            state = state,
                                            size = DockSize.MEDIUM,
                                            onClick = {},
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            val center: (@Composable () -> Unit)? =
                                if (style.showsCenterTrigger) {
                                    {
                                        MarbleCenterSpringTrigger(
                                            state = state,
                                            size = DockSize.MEDIUM,
                                            onToggle = {},
                                            onPing = {}
                                        )
                                    }
                                } else {
                                    null
                                }
                            val trailing: (@Composable RowScope.() -> Unit)? =
                                if (style.showsDockPower) {
                                    {
                                        MarbleDockPowerButton(
                                            state = state,
                                            size = DockSize.MEDIUM,
                                            onToggle = {},
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 0,
                                onSelect = onSelect,
                                size = DockSize.MEDIUM,
                                centerProud = style.showsCenterTrigger,
                                leading = leading,
                                center = center,
                                trailing = trailing
                            )
                        }
                    }
                }
            }
        }
    }
}
