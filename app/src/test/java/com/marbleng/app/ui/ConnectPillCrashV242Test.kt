package com.marbleng.app.ui

import android.app.Application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V242: drives the real Compose animation frame by frame. Before the fix, the 20 -> 0 dp
 * spatial spring produced a negative padding frame and threw the reported fatal exception.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectPillCrashV242Test {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun connectDisconnectAndRapidReversalNeverProduceNegativePadding() {
        compose.mainClock.autoAdvance = false
        var connected by mutableStateOf(false)
        compose.setContent {
            MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                MarbleConnectPill(
                    connected = connected,
                    connecting = false,
                    disconnecting = false,
                    title = if (connected) "Disconnect" else "Connect",
                    subtitle = "Route",
                    onToggle = { connected = !connected }
                )
            }
        }
        compose.waitForIdle()

        fun advanceFrames(count: Int) {
            repeat(count) { compose.mainClock.advanceTimeByFrame() }
        }

        compose.runOnIdle { connected = true }
        advanceFrames(120)
        compose.runOnIdle { connected = false }
        advanceFrames(120)
        // Interrupt the return spring near its fastest point, then reverse again.
        compose.runOnIdle { connected = true }
        advanceFrames(8)
        compose.runOnIdle { connected = false }
        advanceFrames(8)
        compose.runOnIdle { connected = true }
        advanceFrames(160)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
    }
}
