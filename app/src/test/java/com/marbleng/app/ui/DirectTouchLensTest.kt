package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
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
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { target.value = 110f }
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onNodeWithTag("held-lens").fetchSemanticsNode().boundsInRoot.left
        assertEquals(100f, after - before, 1f)
    }
}
