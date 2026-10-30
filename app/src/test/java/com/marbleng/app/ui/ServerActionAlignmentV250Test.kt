package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServersLibraryCard
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.Subscription
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
 * MARBLE_EXPRESSIVE_BENTO_V250 — the reported bug: in the all-sources view the overflow
 * targets drifted from card to card. The fix is a reserved trailing action column; these
 * tests measure the real Compose layout instead of trusting a padding constant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerActionAlignmentV250Test {

    @get:Rule
    val compose = createComposeRule()

    private fun profile(id: String) = ProxyProfile(
        id = id,
        name = "Server $id",
        scheme = "vless",
        raw = "vless://example",
        configJson = "{}",
        host = "example.test",
        port = 443
    )

    private fun subscription(id: String, name: String, updatedAt: Long = 0L) = Subscription(
        id = id,
        name = name,
        url = "https://example.test/sub",
        updatedAt = updatedAt
    )

    @Test
    fun longRtlGroupHeadersKeepTheirOverflowInTheSameColumn() {
        val short = ServerGroupBucket(
            id = "short",
            title = "Germany",
            subscription = subscription("short", "Germany", updatedAt = System.currentTimeMillis()),
            profiles = listOf(profile("1"))
        )
        val long = ServerGroupBucket(
            id = "long",
            title = "یک اشتراک خیلی طولانی با نام فارسی و چند کلمه اضافه",
            subscription = subscription("long", "Long plan", updatedAt = 1L),
            profiles = listOf(profile("2"), profile("3"))
        )
        compose.setContent {
            ProvideMarbleLanguage("fa") {
                ProvideMarbleTextScale(150) {
                    MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                        Box(Modifier.width(320.dp)) {
                            Column {
                                listOf(short, long).forEach { bucket ->
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
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val a = compose.onNodeWithTag("server-group-action-short", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("server-group-action-long", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(a.left, b.left)
        assertEquals(a.right, b.right)
        assertTrue((a.right - a.left) >= 48.dp)

        val more = compose.onNodeWithTag("server-group-more-long", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue((more.right - more.left) >= 48.dp)
        assertTrue(more.left >= b.left && more.right <= b.right)
    }

    @Test
    fun sourceCardsReserveTheActionColumnEvenWhenTheyHaveNoMenu() {
        val cards = listOf(
            ServersLibraryCard("all", "All sources", 12, listOf("DE", "NL", "FR")),
            ServersLibraryCard("sub-a", "Alpha", 4, listOf("DE"), subscription = subscription("sub-a", "Alpha")),
            ServersLibraryCard(
                "sub-b",
                "یک اشتراک فارسی با نام بسیار طولانی و حجم زیاد",
                99,
                listOf("IR", "TR"),
                usageFraction = 0.8f,
                subscription = subscription("sub-b", "Beta")
            ),
            ServersLibraryCard("manual", "Manual", 2, emptyList())
        )
        compose.setContent {
            ProvideMarbleLanguage("fa") {
                ProvideMarbleTextScale(150) {
                    MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                        Box(Modifier.width(320.dp)) {
                            Column {
                                cards.forEach { card ->
                                    SourceLibraryCard(
                                        card = card,
                                        selected = card.id == "all",
                                        onSelect = {},
                                        onManage = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()

        val bounds = cards.map { card ->
            compose.onNodeWithTag("server-source-action-${card.id}", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        }
        bounds.forEach { bound ->
            assertEquals(bounds.first().left, bound.left)
            assertEquals(bounds.first().right, bound.right)
            assertTrue((bound.right - bound.left) >= 48.dp)
        }
    }

    @Test
    fun theSourceOverflowStillOpensTheExistingSubscriptionEditor() {
        var managed = ""
        val sub = subscription("sub-a", "Alpha")
        val card = ServersLibraryCard("sub-a", "Alpha", 4, listOf("DE"), subscription = sub)
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(themeId = "light", dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        SourceLibraryCard(
                            card = card,
                            selected = false,
                            onSelect = {},
                            onManage = { managed = it.id }
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("server-source-more-sub-a", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit subscription").performClick()
        compose.waitForIdle()
        assertEquals("sub-a", managed)
    }
}
