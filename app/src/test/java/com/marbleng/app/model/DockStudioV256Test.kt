package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_LUMEN_DOCK_V256 + MARBLE_INTAKE_GRID_V256 + MARBLE_SERVER_BUBBLE_V256 — every number and
 * every rule of the V256 pass, pinned on the host JVM.
 *
 * The point of this file is that nothing here needs a device: the dock's height ladder, the
 * clearance arithmetic that stopped the capsule clipping, the two-second caption hold, the
 * clipboard classifier behind the 2×2 grid and the six verbs of the row bubble are all pure Kotlin,
 * so a regression is a red test in ten seconds rather than a screenshot somebody has to notice.
 */
class DockStudioV256Test {

    // ── the ladder ──────────────────────────────────────────────────────────────────────────

    @Test
    fun theLadderIsFortyEightFiftySixSixtyFour() {
        assertEquals(48, DockStudio.heightDp(DockSize.SMALL))
        assertEquals(56, DockStudio.heightDp(DockSize.MEDIUM))
        assertEquals(64, DockStudio.heightDp(DockSize.LARGE))
    }

    @Test
    fun theLadderNamesItsOwnRungsInsteadOfBorrowingTheLensNames() {
        assertEquals("Compact", DockStudio.ladderTitle(DockSize.SMALL))
        assertEquals("Standard", DockStudio.ladderTitle(DockSize.MEDIUM))
        assertEquals("Comfortable", DockStudio.ladderTitle(DockSize.LARGE))
        assertEquals("64 dp", DockStudio.ladderHeightLabel(DockSize.LARGE))
    }

    @Test
    fun everyRungFitsTheClearanceTheScreensActuallyReserve() {
        // 112dp is MarbleSpec.BOTTOM_BAR_CLEARANCE_DP: if a rung outgrew it, the last row of every
        // list would sit under the pill — the clipping bug from the other side of the same fact.
        assertTrue(DockStudio.ladderFits(112))
        DockSize.entries.forEach { size ->
            assertEquals(DockStudio.heightDp(size) + DockStudio.BOTTOM_MARGIN_DP, DockStudio.occupiedHeightDp(size))
            assertTrue("${size.name} does not fit", DockStudio.fitsClearance(size, 112))
        }
        assertFalse(DockStudio.fitsClearance(DockSize.LARGE, 60))
    }

    @Test
    fun paddingGlyphAndButtonGrowWithTheCapsuleAndNeverOutgrowIt() {
        DockSize.entries.forEach { size ->
            val height = DockStudio.heightDp(size)
            val padding = DockStudio.pillPaddingDp(size)
            assertTrue("padding eats the capsule", padding * 2 < height)
            assertTrue("glyph is taller than the row", DockStudio.iconSizeDp(size) <= DockStudio.restingTabSizeDp(size))
            assertTrue("power button is taller than the capsule", DockStudio.powerButtonSizeDp(size) <= height)
            assertEquals("the island must not be taller than its row", height - padding * 2, DockStudio.islandHeightDp(size))
            assertEquals("a stadium at every rung", height / 2f, DockStudio.cornerRadiusDp(size), 0.001f)
        }
    }

    @Test
    fun anOpenCaptionIsWiderThanTheCircleItGrewFromAndIsCapped() {
        val resting = DockStudio.restingTabSizeDp(DockSize.MEDIUM)
        assertTrue(DockStudio.expandedTabWidthDp(DockSize.MEDIUM, 4) > resting)
        // Fourteen characters is the cap: a longer word is elided by the label, not by the dock.
        assertEquals(
            DockStudio.expandedTabWidthDp(DockSize.MEDIUM, 14),
            DockStudio.expandedTabWidthDp(DockSize.MEDIUM, 40)
        )
    }

    @Test
    fun theTransientCaptionHoldsForTwoSeconds() {
        assertEquals(2_000L, DockStudio.EXPANDED_HOLD_MS)
        assertTrue(DockStudio.CENTER_TOOLTIP_HOLD_MS < DockStudio.EXPANDED_HOLD_MS)
    }

    @Test
    fun theIslandReadsLatencyAndCountsItsBatchWithoutDividingByZero() {
        assertEquals("", DockStudio.pingLabel(0))
        assertEquals("48 ms", DockStudio.pingLabel(48))
        assertEquals("12/79 pinging…", DockStudio.batchLabel(12, 79))
        assertEquals(0f, DockStudio.batchProgress(4, 0), 0.0001f)
        assertEquals(0.5f, DockStudio.batchProgress(4, 8), 0.0001f)
        // A batch that shrank between two frames must not draw backwards or past the track.
        assertEquals(1f, DockStudio.batchProgress(9, 8), 0.0001f)
        assertEquals(0f, DockStudio.batchProgress(-3, 8), 0.0001f)
        assertEquals("12/12 pinging…", DockStudio.batchLabel(99, 12))
    }

    // ── the connection styles ───────────────────────────────────────────────────────────────

    @Test
    fun threeStylesOwnThreeDifferentPartsOfTheChrome() {
        assertEquals(ConnectionWidgetStyle.DOCK_ISLAND, ConnectionWidgetStyle.DEFAULT)
        assertTrue(ConnectionWidgetStyle.DOCK_ISLAND.showsDockPower)
        assertFalse(ConnectionWidgetStyle.DOCK_ISLAND.showsCenterTrigger)
        assertFalse(ConnectionWidgetStyle.DOCK_ISLAND.showsHomeHero)

        assertTrue(ConnectionWidgetStyle.CENTER_MORPH.showsCenterTrigger)
        assertFalse(ConnectionWidgetStyle.CENTER_MORPH.showsDockPower)
        assertFalse(ConnectionWidgetStyle.CENTER_MORPH.showsHomeHero)

        assertTrue(ConnectionWidgetStyle.HERO_CARD.showsHomeHero)
        assertFalse(ConnectionWidgetStyle.HERO_CARD.showsDockPower)
        assertFalse(ConnectionWidgetStyle.HERO_CARD.showsCenterTrigger)
    }

    @Test
    fun anUnknownStyleInARestoredBackupResolvesToTheDefault() {
        assertEquals(ConnectionWidgetStyle.DOCK_ISLAND, ConnectionWidgetStyle.fromId(null))
        assertEquals(ConnectionWidgetStyle.DOCK_ISLAND, ConnectionWidgetStyle.fromId("  "))
        assertEquals(ConnectionWidgetStyle.HERO_CARD, ConnectionWidgetStyle.fromId("HERO_CARD"))
        assertEquals(ConnectionWidgetStyle.CENTER_MORPH, ConnectionWidgetStyle.fromId(" center_morph "))
        assertEquals(ConnectionWidgetStyle.DOCK_ISLAND, ConnectionWidgetStyle.fromId("nonsense"))
        assertEquals("Hero card", ConnectionWidgetStyle.labelOf("hero_card"))
    }

    // ── Home settings ──────────────────────────────────────────────────────────────────────

    @Test
    fun everyHomeWidgetIsOnByDefaultSoAnUpgradeChangesNothing() {
        val layout = HomeLayout()
        assertTrue(layout.showRouteCard)
        assertTrue(layout.showPresets)
        assertTrue(layout.showSpeedGraph)
        assertTrue(layout.showIpBox)
        assertEquals(ConnectionWidgetStyle.DOCK_ISLAND.id, layout.connectionStyle)
        assertEquals(4, HomeWidget.entries.size)
        assertEquals(
            4,
            HomeWidgets.visibleCount(
                showRouteCard = layout.showRouteCard,
                showPresets = layout.showPresets,
                showSpeedGraph = layout.showSpeedGraph,
                showIpBox = layout.showIpBox
            )
        )
    }

    @Test
    fun eachSwitchOwnsExactlyOneBlock() {
        val allOff = HomeLayout(
            showRouteCard = false,
            showPresets = false,
            showSpeedGraph = false,
            showIpBox = false
        )
        HomeWidget.entries.forEach { widget ->
            assertFalse(
                widget.id,
                HomeWidgets.isVisible(
                    widget = widget,
                    showRouteCard = allOff.showRouteCard,
                    showPresets = allOff.showPresets,
                    showSpeedGraph = allOff.showSpeedGraph,
                    showIpBox = allOff.showIpBox
                )
            )
        }
        assertTrue(
            HomeWidgets.isVisible(
                widget = HomeWidget.PRESETS,
                showRouteCard = false,
                showPresets = true,
                showSpeedGraph = false,
                showIpBox = false
            )
        )
    }

    @Test
    fun homeLayoutCanonicalizesTheStyleOnTheWayInAndOut() {
        val restored = HomeLayout.of(
            sectionOrder = null,
            hiddenSections = null,
            showPresets = false,
            connectionStyle = "HERO_CARD"
        )
        assertEquals(ConnectionWidgetStyle.HERO_CARD.id, restored.connectionStyle)
        assertFalse(restored.showPresets)
        assertEquals(
            ConnectionWidgetStyle.DOCK_ISLAND.id,
            HomeLayout.of(null, null, connectionStyle = "not-a-style").connectionStyle
        )
    }

    // ── the 2×2 grid ───────────────────────────────────────────────────────────────────────

    @Test
    fun theGridDrawsFourVerbsInTwoRows() {
        assertEquals(4, IntakeClassifier.gridOrder.size)
        assertEquals(AddServerAction.CLIPBOARD, IntakeClassifier.gridOrder[0])
        assertEquals(AddServerAction.MANUAL, IntakeClassifier.gridOrder[3])
        assertEquals(2, IntakeClassifier.gridOrder.chunked(2).size)
    }

    @Test
    fun theClipboardIsClassifiedBeforeTheSheetOffersIt() {
        assertEquals(IntakeKind.EMPTY, IntakeClassifier.kindOf(null))
        assertEquals(IntakeKind.EMPTY, IntakeClassifier.kindOf("   "))
        assertEquals(IntakeKind.JSON, IntakeClassifier.kindOf("{\"outbounds\":[]}"))
        assertEquals(IntakeKind.JSON, IntakeClassifier.kindOf("[{}]"))
        assertEquals(
            IntakeKind.SHARE_LINK,
            IntakeClassifier.kindOf("vless://uuid@host:443?type=ws#Germany")
        )
        assertEquals(
            IntakeKind.SHARE_LINK,
            IntakeClassifier.kindOf("vmess://eyJhZGQiOiJob3N0In0=")
        )
        assertEquals(
            IntakeKind.SUBSCRIPTION,
            IntakeClassifier.kindOf("https://example.com/sub?token=abc")
        )
        assertEquals(
            IntakeKind.OTHER,
            IntakeClassifier.kindOf("vless://uuid@host:443\nhttps://example.com/sub")
        )
        assertEquals(IntakeKind.OTHER, IntakeClassifier.kindOf("example.com:443"))
    }

    @Test
    fun aSubscriptionIsHttpsOnlyAndTheFirstLineIsTheOneThatIsOffered() {
        assertEquals(
            "https://example.com/sub",
            IntakeClassifier.subscriptionUrl("https://example.com/sub\n https://b.example/s ")
        )
        assertNull(IntakeClassifier.subscriptionUrl("http://example.com/sub"))
        assertNull(IntakeClassifier.subscriptionUrl("vless://uuid@host:443"))
        assertNull(IntakeClassifier.subscriptionUrl(""))
    }

    @Test
    fun onlyTheRecognisedKindsSaySomethingAndTheSentenceSaysWhatToDo() {
        assertTrue(IntakeClassifier.kindOf("vless://uuid@host:443").isRecognised)
        assertTrue(IntakeClassifier.kindOf("{\"a\":1}").isRecognised)
        assertFalse(IntakeKind.OTHER.isRecognised)
        assertFalse(IntakeKind.EMPTY.isRecognised)
        assertEquals("", IntakeClassifier.clipboardNote(IntakeKind.EMPTY))
        assertEquals("", IntakeClassifier.clipboardNote(IntakeKind.OTHER))
        assertTrue(
            IntakeClassifier.clipboardNote(IntakeKind.SUBSCRIPTION)
                .startsWith("Subscription detected in clipboard")
        )
        assertTrue(IntakeClassifier.clipboardCardHint(IntakeKind.EMPTY).isNotBlank())
    }

    // ── the bubble ─────────────────────────────────────────────────────────────────────────

    @Test
    fun sixVerbsAndExactlyOneOfThemIsDestructive() {
        assertEquals(6, ServerMenuAction.order.size)
        assertEquals(ServerMenuAction.DELETE, ServerMenuAction.order.last())
        assertEquals(
            listOf(ServerMenuAction.DELETE),
            ServerMenuAction.order.filter { it.destructive }
        )
        assertEquals(
            listOf("edit", "ping", "copy_link", "export_qr", "move_to_group", "delete"),
            ServerMenuAction.order.map { it.id }
        )
        assertEquals(ServerMenuAction.EXPORT_QR, ServerMenuAction.fromId("EXPORT_QR"))
        assertNull(ServerMenuAction.fromId("nope"))
    }

    @Test
    fun theCopiedCheckmarkIsClaimedForFifteenHundredMilliseconds() {
        assertEquals(1_500L, IpTileCopy.COPIED_HOLD_MS)
        assertTrue(IpTileCopy.COPIED_HOLD_MS < DockStudio.EXPANDED_HOLD_MS)
    }
}
