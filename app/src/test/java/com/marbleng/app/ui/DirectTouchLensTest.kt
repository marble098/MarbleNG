package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DirectTouchLensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun heldLensMovesToTheFingerWithoutWaitingForTheSpring() {
        val target = mutableStateOf(10f)
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
            GlassLensIndicator(
                targetLeftPx = target.value, diameter = 44.dp,
                trackInsetPx = 2f, verticalInsetPx = 2f,
                accent = Color.Cyan, prism = Color.Blue,
                dark = false, reduced = false, dragging = true,
                backdrop = {}, tag = "held-lens"
            )
            }
        }
        val before = compose.onNodeWithTag("held-lens").fetchSemanticsNode().boundsInRoot.left
        // Let the rule deliver snapshot notifications as well as frame callbacks.
        // Pausing only the Compose clock leaves Android's draw/notification queue
        // unsynchronised on Robolectric. Assert a frame budget instead of stalling it.
        val started = compose.mainClock.currentTime
        compose.runOnIdle {
            target.value = 110f
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        val elapsed = compose.mainClock.currentTime - started
        assertTrue("direct manipulation must not wait for a spring ($elapsed ms)", elapsed <= 96L)
        val after = compose.onNodeWithTag("held-lens").fetchSemanticsNode().boundsInRoot.left
        assertTrue(compose.onNodeWithTag("held-lens").fetchSemanticsNode().boundsInRoot.width > 0f)
        assertEquals("held layer: before=$before after=$after", 100f, after - before, 1f)
    }
    @Test fun heldLensUsesPhysicalCoordinatesInRtl() {
        val target = mutableStateOf(10f)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.fillMaxSize()) {
                    GlassLensIndicator(
                        targetLeftPx = target.value, diameter = 44.dp,
                        trackInsetPx = 2f, verticalInsetPx = 2f,
                        accent = Color.Cyan, prism = Color.Blue,
                        dark = true, reduced = false, dragging = true,
                        backdrop = {}, modifier = Modifier.align(Alignment.CenterLeft), tag = "rtl-lens"
                    )
                }
            }
        }
        val before = compose.onNodeWithTag("rtl-lens").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { target.value = 110f }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("rtl-lens").fetchSemanticsNode().boundsInRoot.left
        assertEquals("RTL finger coordinates still move right", 100f, after - before, 1f)
    }

}
