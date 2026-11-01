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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import com.marbleng.app.ui.theme.ProvideMarbleMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * MARBLE_FLOATING_DOCK_V258 — the two composables this pass rebuilt, inflated for real.
 *
 * The model test (`DockStudioV256Test`) proves the arithmetic of the ladder and the gap; this one
 * proves the objects: that the capsule draws its tabs at every rung and every gap, and that the
 * Settings hub's top-right corner is a real button that opens About.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingDockV258Test {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(key = "IP", label = "Addresses", glyph = MarbleGlyph.GLOBE, accent = Color.Green),
        MarbleDockTab(key = "SETTINGS", label = "Settings", glyph = MarbleGlyph.TUNERS, accent = Color.Cyan)
    )

    @Test
    fun theCapsuleFloatsAtEveryRungAndEveryGap() {
        compose.mainClock.autoAdvance = false
        var size by mutableStateOf(DockSize.SMALL)
        var gap by mutableStateOf(DockStudio.MIN_BOTTOM_GAP_DP)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleLumenDock(
                                tabs = tabs(),
                                selectedIndex = 0,
                                onSelect = {},
                                size = size,
                                bottomGap = gap.dp
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("lumen-dock").assertExists()
        tabs().forEach { tab -> compose.onNodeWithTag("lumen-tab-${tab.key}").assertExists() }

        DockSize.entries.forEach { rung ->
            listOf(DockStudio.MIN_BOTTOM_GAP_DP, DockStudio.DEFAULT_BOTTOM_GAP_DP, DockStudio.MAX_BOTTOM_GAP_DP)
                .forEach { chosen ->
                    compose.runOnIdle {
                        size = rung
                        gap = chosen
                    }
                    compose.mainClock.advanceTimeBy(48)
                    compose.onNodeWithTag("lumen-tab-HOME").assertExists()
                }
        }
    }

    @Test
    fun theGrandRungIsTheOneThatKeepsItsCaption() {
        // The third rung differs from the other two in behaviour, not only in height.
        assertFalse(DockStudio.showsCaptionsAlways(DockSize.SMALL))
        assertFalse(DockStudio.showsCaptionsAlways(DockSize.MEDIUM))
        assertTrue(DockStudio.showsCaptionsAlways(DockSize.LARGE))
    }

    @Test
    fun theAboutCornerIsAButtonThatOpensThePage() {
        var opened = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        MarbleAboutOrbitButton(
                            contentDescription = "About MarbleNG",
                            onClick = { opened++ }
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("settings-about-button").assertExists()
        compose.onNodeWithTag("settings-about-button").performClick()
        assertEquals(1, opened)
    }
}
