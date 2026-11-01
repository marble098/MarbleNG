package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServersDeck
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V242: the server row keeps its columns when the name, address, language or probe changes.
 *
 * V243 kept the three promises this test was written for — a 48dp action target, an 88dp reserved
 * ping column, and an RTL row whose columns do not cross — while the row itself became the compact
 * deck: the address left the face of the row and the delete left the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerRowLayoutV242Test {

    @get:Rule
    val compose = createComposeRule()

    private val profile = ProxyProfile(
        id = "row-1",
        name = "🇩🇪 Germany 3 with a deliberately long subscription name",
        scheme = "vless",
        raw = "vless://example",
        configJson = "{}",
        host = "2001:db8:2a09:e683:7:b0::a",
        port = 443
    )

    private fun setRow(
        language: String = "en",
        textScale: Int = 100,
        result: BenchmarkResult? = null,
        probeState: ProbeState = ProbeState.IDLE
    ) {
        compose.setContent {
            ProvideMarbleLanguage(language) {
                ProvideMarbleTextScale(textScale) {
                    MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                        Box(Modifier.width(320.dp)) {
                            ServerRowContent(
                                profile = profile,
                                countryCode = "DE",
                                active = false,
                                selected = true,
                                result = result,
                                probeState = probeState,
                                onClick = {},
                                onMore = {}
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun overflowOpensAndDismissesTheRowAnchoredActionSlot() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ServerRowContent(
                            profile = profile, countryCode = "DE", active = false,
                            selected = false, result = null, probeState = ProbeState.IDLE,
                            onClick = {}, onMore = {},
                            actionMenu = { expanded, dismiss ->
                                if (expanded) Text("Close actions", Modifier.clickable { dismiss() })
                            }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Close actions").assertDoesNotExist()
        compose.onNodeWithTag("server-row-more-row-1", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Close actions", useUnmergedTree = true).assertExists().performClick()
        compose.onNodeWithText("Close actions").assertDoesNotExist()
    }

    @Test
    fun activeRouteHasAnExplicitStateAndMetadataInsteadOfAnUnlabelledTick() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "amoled", dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ServerRowContent(
                            profile = profile, countryCode = "DE", active = true,
                            selected = false, result = null, probeState = ProbeState.IDLE,
                            onClick = {}, onMore = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("server-row-row-1").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Active")
        )
        val metadata = compose.onNodeWithTag("server-row-transport-row-1", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(metadata.startsWith("Active · "))
    }

    @Test
    fun longRtlCopyKeepsTheActionTargetAndReservedPingColumn() {
        setRow(language = "fa", textScale = 150, probeState = ProbeState.TESTING)

        val title = compose.onNodeWithTag("server-row-title-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val more = compose.onNodeWithTag("server-row-more-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val transport = compose.onNodeWithTag("server-row-transport-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val ping = compose.onNodeWithTag("server-row-ping-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue("action target is too small: $more", (more.right - more.left) >= 48.dp)
        assertEquals(88.dp, ping.right - ping.left)
        // RTL: the action sits to the left of the title; ping sits to the left of the details.
        assertTrue("more overlaps title: more=$more title=$title", more.right <= title.left)
        assertTrue("ping overlaps transport: ping=$ping transport=$transport", ping.right <= transport.left)
    }

    @Test
    fun thePingColumnDoesNotMoveWhenAResultArrives() {
        var result by mutableStateOf<BenchmarkResult?>(null)
        var probeState by mutableStateOf(ProbeState.IDLE)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ServerRowContent(
                            profile = profile,
                            countryCode = "DE",
                            active = false,
                            selected = false,
                            result = result,
                            probeState = probeState,
                            onClick = {},
                            onMore = {}
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val idle = compose.onNodeWithTag("server-row-ping-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        compose.runOnIdle { probeState = ProbeState.TESTING }
        compose.waitForIdle()
        val testing = compose.onNodeWithTag("server-row-ping-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(idle.left, testing.left)
        assertEquals(idle.right, testing.right)

        compose.runOnIdle {
            probeState = ProbeState.IDLE
            result = BenchmarkResult(
                profileId = profile.id,
                name = profile.name,
                success = 3,
                latencyMs = 9999.0,
                bytesPerSecond = 0.0,
                score = 1.0
            )
        }
        compose.waitForIdle()
        val measured = compose.onNodeWithTag("server-row-ping-row-1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(idle.left, measured.left)
        assertEquals(idle.right, measured.right)
    }

    @Test
    fun theCompactRowKeepsTheAddressOutAndTheDeleteOffTheFace() {
        setRow()

        // V243: the address lives in the details page, and delete is a gesture, not a label.
        assertTrue(
            "a Delete label is back on the row",
            compose.onAllNodesWithText("Delete").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "the address is repeated on the row",
            compose.onAllNodesWithText(profile.host, substring = true).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "the transport line is missing",
            compose.onAllNodesWithTag("server-row-transport-row-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        )

        val row = compose.onNodeWithTag("server-row-row-1").getUnclippedBoundsInRoot()
        val height = row.bottom - row.top
        assertTrue(
            "row is not compact: $height",
            height >= ServersDeck.ROW_MIN_HEIGHT_DP.dp && height <= ServersDeck.ROW_MAX_HEIGHT_DP.dp
        )
    }
}
