package com.marbleng.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
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

/**
 * MARBLE_DOCK_RADII_V260 / MARBLE_CENTER_TRIGGER_V260 / MARBLE_SERVER_PLAN_CARDS_V260 — the objects
 * of this pass, inflated on Robolectric.
 *
 * The model tests prove the arithmetic (a radius that is no longer half the height, a plan's days and
 * tones); this file proves the four things a number cannot:
 *
 *  - the dock's corner and every object inside it are derived from *one* rule, at every rung;
 *  - the centre trigger is a groove with a knob in it, and the whole groove is the press target;
 *  - a subscription card grows a plan plate that prints the percentage, the pair and the expiry day;
 *  - a card with no quota draws no bar, because a 0 % strip is a lie about a plan with no ceiling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h740dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignPassV260Test {

    @get:Rule
    val compose = createComposeRule()

    private fun tabs(): List<MarbleDockTab> = listOf(
        MarbleDockTab(key = "HOME", label = "Home", glyph = MarbleGlyph.HOME, accent = Color.Cyan),
        MarbleDockTab(key = "SERVERS", label = "Servers", glyph = MarbleGlyph.SERVER, accent = Color.Cyan),
        MarbleDockTab(key = "IP", label = "Addresses", glyph = MarbleGlyph.GLOBE, accent = Color.Green),
        MarbleDockTab(key = "SETTINGS", label = "Settings", glyph = MarbleGlyph.TUNERS, accent = Color.Cyan)
    )

    // ── the corner language ───────────────────────────────────────────────────────────────────

    @Test
    fun theBarAndEverythingInItReadOneCornerRule() {
        // "The bar should be round and its buttons should be round to the same degree as it."
        DockSize.entries.forEach { rung ->
            val outer = DockStudio.cornerRadiusDp(rung)
            val inner = DockStudio.innerRadiusDp(rung)
            // It is a very round capsule…
            assertTrue("$rung must stay round", outer >= 22f)
            // …but no longer a stadium: half the height is what the report asked to move away from.
            assertTrue("$rung must not be a stadium", outer < DockStudio.heightDp(rung) / 2f)
            // …and a nested object curves exactly one rim inside it.
            assertEquals(outer - DockStudio.INNER_CORNER_INSET_DP, inner, 0.001f)
            // The knob in the centre is the same curve, so it reads as part of the bar.
            assertEquals(inner, DockStudio.centerTriggerRadiusDp(rung), 0.001f)
        }
        // The ratios are the shipped ones: 25 / 30 / 36 of 56 / 68 / 82.
        assertEquals(25f, DockStudio.cornerRadiusDp(DockSize.SMALL), 0.001f)
        assertEquals(30f, DockStudio.cornerRadiusDp(DockSize.MEDIUM), 0.001f)
        assertEquals(36f, DockStudio.cornerRadiusDp(DockSize.LARGE), 0.001f)
    }

    @Test
    fun theCentreKnobFitsInsideItsGrooveAtEveryRung() {
        DockSize.entries.forEach { rung ->
            val groove = DockStudio.centerTriggerSizeDp(rung)
            val knob = DockStudio.centerKnobSizeDp(rung)
            assertTrue("$rung knob must be smaller than its groove", knob < groove)
            assertEquals(DockStudio.CENTER_GROOVE_INSET_DP * 2, groove - knob)
            // …and the knob never grows into the tabs beside it: the groove is what a tab would be.
            assertTrue(knob >= 24)
        }
    }

    // ── the centre trigger ────────────────────────────────────────────────────────────────────

    @Test
    fun theCentreTriggerIsAGrooveWithAKnobAndTheWholeGroovePresses() {
        var toggled = 0
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
                                        state = DockConnectionState(connected = false),
                                        size = DockSize.MEDIUM,
                                        onToggle = { toggled++ },
                                        onPing = {}
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("dock-center").assertExists()
        // The press target is the groove, not the knob inside it: a finger aims at the object.
        compose.onNodeWithTag("dock-center-trigger").performClick()
        assertEquals(1, toggled)
        // The state and the measurement are printed without a tap, which is the whole point.
        compose.onNodeWithText("Ready").assertExists()
    }

    @Test
    fun aLiveCentreTriggerSaysConnectedAndKeepsItsHalo() {
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
                                size = DockSize.LARGE,
                                centerProud = true,
                                center = {
                                    MarbleCenterSpringTrigger(
                                        state = DockConnectionState(
                                            connected = true,
                                            latencyMs = 48
                                        ),
                                        size = DockSize.LARGE,
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
        compose.mainClock.advanceTimeBy(160)

        compose.onNodeWithTag("dock-center-halo").assertExists()
        compose.onNodeWithText("Connected").assertExists()
        compose.onNodeWithText("48 ms").assertExists()
    }

    // ── the subscription card ─────────────────────────────────────────────────────────────────

    private fun subscription(
        id: String = "s1",
        total: Long = 50L * GIB,
        used: Long = 14L * GIB,
        expireAt: Long = 0L
    ) = Subscription(
        id = id,
        name = "Provider",
        url = "https://example.test/sub",
        updatedAt = 1_700_000_000_000L,
        uploadBytes = used / 4,
        downloadBytes = used - used / 4,
        totalBytes = total,
        expireAt = expireAt
    )

    private fun secondsFromNow(days: Int): Long = System.currentTimeMillis() + days * 86_400_000L

    @Test
    fun aSubscriptionCardPrintsItsQuotaItsPairAndItsExpiryDay() {
        val now = System.currentTimeMillis()
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            SourceLibraryCard(
                                card = com.marbleng.app.core.ServersLibraryCard(
                                    id = "s1",
                                    title = "Provider",
                                    count = 12,
                                    flags = listOf("DE", "NL"),
                                    subscription = subscription(expireAt = secondsFromNow(6))
                                ),
                                selected = false,
                                onSelect = {},
                                onManage = {},
                                nowMs = now
                            )
                        }
                    }
                }
            }
        }

        // The card is one clickable, so Compose *merges* its descendants into a single node: the
        // plate's own tags only exist in the unmerged tree. Reading them there is also the more
        // honest assertion — it proves the plate is a separate object inside the card, not that the
        // card happens to say the same words.
        compose.onNodeWithTag("server-plan-plate", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-plan-bar", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-plan-expiry", useUnmergedTree = true).assertExists()
        // 14 of 50 GiB is 28 %.
        compose.onNodeWithText("28%").assertExists()
        // The pair the bar is drawn from, in the product's units. `substring = true` because the plate
        // prints it beside "N left" in one row, and Compose folds that row into one text node.
        compose.onNodeWithText("14.00 GB / 50.00 GB", substring = true).assertExists()
        // The distance in words and the day itself, on one line.
        compose.onNodeWithText("Expires · in 6 days", substring = true).assertExists()
    }

    @Test
    fun aPlanWithNoCeilingDrawsNoBarAndSaysSoInWords() {
        val now = System.currentTimeMillis()
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            SourceLibraryCard(
                                card = com.marbleng.app.core.ServersLibraryCard(
                                    id = "s2",
                                    title = "No cap",
                                    count = 3,
                                    flags = emptyList(),
                                    subscription = subscription(
                                        id = "s2",
                                        total = 0L,
                                        used = 2L * GIB,
                                        expireAt = secondsFromNow(40)
                                    )
                                ),
                                selected = false,
                                onSelect = {},
                                onManage = {},
                                alternate = true,
                                nowMs = now
                            )
                        }
                    }
                }
            }
        }

        // A plan that simply has no quota must not draw a 0 % bar: that would be a false statement.
        compose.onNodeWithTag("server-plan-plate", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("server-plan-bar", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("server-plan-percent", useUnmergedTree = true).assertDoesNotExist()
        // …and the expiry is still printed, because that is the fact it does have.
        compose.onNodeWithTag("server-plan-expiry", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Expires · in 40 days", substring = true).assertExists()
    }

    @Test
    fun aCardWithNothingToSayDrawsNoPlateAtAll() {
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            // "All sources" is every profile and no subscription: there is no plan.
                            SourceLibraryCard(
                                card = com.marbleng.app.core.ServersLibraryCard(
                                    id = "all",
                                    title = "All sources",
                                    count = 20,
                                    flags = listOf("DE"),
                                    subscription = null
                                ),
                                selected = true,
                                onSelect = {},
                                onManage = {}
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("server-source-all").assertExists()
        compose.onNodeWithTag("server-plan-plate", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun everySubscriptionCardCarriesAStableStripe() {
        // The stripe is keyed by the id, which is what makes four libraries distinguishable without
        // turning the sheet into a palette test.
        val shown = "s1"
        compose.setContent {
            ProvideMarbleLanguage("en") {
                MarbleExpressiveTheme(dynamicColor = false) {
                    ProvideMarbleMotion(calmMotion = false, systemReducedMotion = false) {
                        Box(Modifier.width(360.dp)) {
                            MarbleSourceStripe(id = shown, selected = false)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("server-source-stripe-s1", useUnmergedTree = true).assertExists()
        // The wheel itself is pure and stable, which is what keeps a subscription's colour still
        // across restarts, refreshes and reorders.
        assertEquals(stripeTone("s1"), stripeTone("s1"))
        assertTrue(stripeTone("s1") in 0 until STRIPE_TONES)
        assertTrue(stripeTone("") == 0)
    }

    private companion object {
        const val GIB = 1_024L * 1_024L * 1_024L
    }
}
