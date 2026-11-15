package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServersLibraryCard
import com.marbleng.app.model.AddServerAction
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ConnectPhase
import com.marbleng.app.model.HeroMetricKind
import com.marbleng.app.model.HomeHeroSnapshot
import com.marbleng.app.model.HomeHeroStudio
import com.marbleng.app.model.IntakeKind
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.Subscription
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpressiveConnectionServersSubscriptionsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun sub(
        id: String = "s1",
        name: String = "Provider",
        url: String = "https://sub.example.test/list",
        used: Long = 10L * GIB,
        total: Long = 40L * GIB,
        expireAt: Long = System.currentTimeMillis() + 10L * DAY_MS
    ) = Subscription(
        id = id,
        name = name,
        url = url,
        updatedAt = System.currentTimeMillis() - 60_000L,
        uploadBytes = used / 4,
        downloadBytes = used - used / 4,
        totalBytes = total,
        expireAt = expireAt
    )

    private fun profile(id: String = "p1", subId: String = "s1") = ProxyProfile(
        id = id,
        name = "Frankfurt 01",
        scheme = "vless",
        raw = "vless://example",
        configJson = "{}",
        host = "fra.example.test",
        port = 443,
        subscriptionId = subId,
        subscriptionName = "Provider"
    )

    // ── 1. Connection Box ────────────────────────────────────────────────────────────────────

    @Test
    fun heroPanelHandlesAllFiveConnectPhasesWithContextAndMetricTargets() {
        var phase by mutableStateOf(ConnectPhase.IDLE)
        var clickedMetric: HeroMetricKind? = null
        var openedLogs = 0
        var openedRoute = 0

        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(340.dp)) {
                            val connected = phase == ConnectPhase.CONNECTED
                            val blocked = phase == ConnectPhase.BLOCKED
                            val connecting = phase == ConnectPhase.CONNECTING
                            val disconnecting = phase == ConnectPhase.DISCONNECTING
                            val snapshot = HomeHeroSnapshot(
                                stateLabel = headlineFor(
                                    connected = connected,
                                    connecting = connecting,
                                    disconnecting = disconnecting,
                                    blocked = blocked
                                ),
                                overline = "Connection",
                                routeName = "Frankfurt 01",
                                routeDetail = "VLESS · Provider",
                                countryCode = "DE",
                                metrics = HomeHeroStudio.metrics(
                                    connected = connected,
                                    working = connecting || disconnecting,
                                    pingMs = if (connected) 42 else 0,
                                    jitterMs = if (connected) 3 else -1,
                                    uptimeLabel = if (connected) "00:05:12" else "",
                                    exitAddress = if (connected) "198.51.100.2" else "",
                                    hasRoute = true
                                ),
                                working = connecting || disconnecting
                            )
                            MarbleHeroPanel(
                                snapshot = snapshot,
                                motif = MarbleExpressiveMotif.CLOVER,
                                phase = phase,
                                modeLabel = "Full TUN",
                                engineLabel = "Xray",
                                onMetricClick = { clickedMetric = it },
                                onOpenLogs = { openedLogs++ },
                                onOpenRoute = { openedRoute++ }
                            )
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithTag("home-hero-panel").assertExists()
        compose.onNodeWithText("Full TUN · Xray").assertExists()
        compose.onNodeWithText("Ready when you are").assertExists()

        compose.runOnIdle { phase = ConnectPhase.CONNECTING }
        compose.waitForIdle()
        compose.onNodeWithText("Securing the route").assertExists()

        compose.runOnIdle { phase = ConnectPhase.CONNECTED }
        compose.waitForIdle()
        compose.onNodeWithText("Protected").assertExists()
        compose.onNodeWithText("00:05:12").assertExists()
        compose.onNodeWithTag("home-hero-metric-ping").performClick()
        assertEquals(HeroMetricKind.PING, clickedMetric)
        compose.onNodeWithTag("home-hero-logs").performClick()
        assertEquals(1, openedLogs)
        compose.onNodeWithTag("home-hero-route").performClick()
        assertEquals(1, openedRoute)

        compose.runOnIdle { phase = ConnectPhase.DISCONNECTING }
        compose.waitForIdle()
        compose.onNodeWithText("Closing the route").assertExists()

        compose.runOnIdle { phase = ConnectPhase.BLOCKED }
        compose.waitForIdle()
        compose.onNodeWithText("Blocked").assertExists()
    }

    @Test
    fun connectPillKeepsFixed82DpHeightAndBoundsAcrossPhases() {
        var connected by mutableStateOf(false)
        var connecting by mutableStateOf(false)
        var blocked by mutableStateOf(false)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(320.dp)) {
                            MarbleConnectPill(
                                connected = connected,
                                connecting = connecting,
                                disconnecting = false,
                                blocked = blocked,
                                title = when {
                                    connected -> "Disconnect"
                                    connecting -> "Cancel connect"
                                    blocked -> "Reset"
                                    else -> "Connect"
                                },
                                subtitle = "Frankfurt 01",
                                ticker = if (connected) "00:02:10" else "",
                                onToggle = {}
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val idleBounds = compose.onNodeWithContentDescription("Connect").getBoundsInRoot()
        assertEquals(82.dp, idleBounds.bottom - idleBounds.top)

        compose.runOnIdle { connecting = true }
        compose.waitForIdle()
        val connectingBounds = compose.onNodeWithContentDescription("Cancel connect").getBoundsInRoot()
        assertEquals(idleBounds, connectingBounds)

        compose.runOnIdle {
            connecting = false
            connected = true
        }
        compose.waitForIdle()
        val connectedBounds = compose.onNodeWithContentDescription("Disconnect").getBoundsInRoot()
        assertEquals(idleBounds, connectedBounds)
        compose.onNodeWithTag("home-connect-pulse", useUnmergedTree = true).assertExists()

        compose.runOnIdle {
            connected = false
            blocked = true
        }
        compose.waitForIdle()
        val blockedBounds = compose.onNodeWithContentDescription("Reset").getBoundsInRoot()
        assertEquals(idleBounds, blockedBounds)
    }

    // ── 2. Servers Screen ────────────────────────────────────────────────────────────────────

    @Test
    fun serverGroupHeaderAndRowMaintainExpressiveLayoutAndPlanStrip() {
        val subscription = sub()
        val bucket = ServerGroupBucket(
            id = subscription.id,
            title = subscription.name,
            subscription = subscription,
            profiles = listOf(profile())
        )
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Column(Modifier.width(340.dp)) {
                            ServerGroupHeader(
                                bucket = bucket,
                                collapsed = false,
                                busy = false,
                                probeActive = false,
                                menuOpen = false,
                                onMenuOpenChange = {},
                                onToggleCollapse = {},
                                onRefresh = {},
                                onPing = {},
                                onEdit = {}
                            )
                            ServerRowContent(
                                profile = profile(),
                                countryCode = "DE",
                                countryName = "Germany",
                                active = true,
                                selected = false,
                                result = BenchmarkResult(
                                    profileId = "p1",
                                    name = "Frankfurt 01",
                                    success = 3,
                                    latencyMs = 45.0,
                                    bytesPerSecond = 0.0,
                                    score = 9.0
                                ),
                                probeState = ProbeState.IDLE,
                                onClick = {},
                                onMore = {}
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("server-group-header-s1").assertExists()
        compose.onNodeWithTag("server-plan-strip", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-source-stripe-s1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-row-p1").assertExists()
        compose.onNodeWithText("45 ms", useUnmergedTree = true).assertExists()
    }

    @Test
    fun addServerGridDispatchesAllFourActions() {
        var lastAction: AddServerAction? = null
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(340.dp)) {
                            MarbleAddServerGrid(
                                clipboardKind = IntakeKind.SUBSCRIPTION,
                                onAction = { lastAction = it }
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("add-server-grid").assertExists()
        AddServerAction.entries.forEach { action ->
            compose.onNodeWithTag("add-server-cell-${action.id}").performClick()
            assertEquals(action, lastAction)
        }
    }

    // ── 3. Subscription Management ───────────────────────────────────────────────────────────

    @Test
    fun librarySheetSupportsInlineSubscriptionCreationAndSourceRefresh() {
        var addedName = ""
        var addedUrl = ""
        var refreshedId = ""
        val subscription = sub()
        val cards = listOf(
            ServersLibraryCard("all", "All sources", 4, listOf("DE")),
            ServersLibraryCard("s1", subscription.name, 4, listOf("DE"), subscription = subscription)
        )

        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Column(
                            Modifier
                                .width(340.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            ServersLibrarySheetContent(
                                cards = cards,
                                selectedId = "all",
                                busy = false,
                                scanning = false,
                                located = 4,
                                total = 4,
                                onSelect = {},
                                onManageSubscription = {},
                                onRefreshAll = {},
                                onScanFlags = {},
                                onAdd = {},
                                onRefreshSource = { refreshedId = it },
                                onAddSubscription = { n, u ->
                                    addedName = n
                                    addedUrl = u
                                }
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("New Subscription").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Subscription Name").performScrollTo().performTextInput("Backup Sub")
        compose.onNodeWithText("HTTPS Subscription URL").performScrollTo().performTextInput("https://example.org/sub")
        compose.onNodeWithText("Add").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("Backup Sub", addedName)
        assertEquals("https://example.org/sub", addedUrl)

        compose.onNodeWithTag("server-source-more-s1", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Refresh").performClick()
        compose.waitForIdle()
        assertEquals("s1", refreshedId)
    }

    @Test
    fun subscriptionDirectoryCardDisplaysHostPlanPlateAndDispatchesActions() {
        var refreshed = 0
        var managed = 0
        val subscription = sub()
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(340.dp)) {
                            SubscriptionDirectoryCard(
                                subscription = subscription,
                                serverCount = 4,
                                isRefreshing = false,
                                busy = false,
                                nowMs = System.currentTimeMillis(),
                                persian = false,
                                onRefresh = { refreshed++ },
                                onManage = { managed++ }
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Provider", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Remote", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("sub.example.test", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-plan-plate", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-plan-bar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("25%", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("subscription-directory-refresh-s1", useUnmergedTree = true).performClick()
        assertEquals(1, refreshed)
        compose.onNodeWithTag("subscription-directory-card-s1").performClick()
        assertEquals(1, managed)
        assertTrue(refreshed == 1)
    }

    private companion object {
        const val GIB = 1_024L * 1_024L * 1_024L
        const val DAY_MS = 86_400_000L
    }
}
