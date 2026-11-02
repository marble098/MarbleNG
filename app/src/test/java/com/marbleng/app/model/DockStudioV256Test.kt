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
 * clearance arithmetic that stopped the capsule clipping, the centre tooltip's hold, the clipboard
 * classifier behind the 2×2 grid and the six verbs of the row bubble are all pure Kotlin, so a
 * regression is a red test in ten seconds rather than a screenshot somebody has to notice.
 * MARBLE_QUIET_TABS_V263 retired the two-second caption hold; the ladder is pinned without it.
 */
class DockStudioV256Test {

    // ── the ladder ──────────────────────────────────────────────────────────────────────────

    @Test
    fun theLadderIsFiftySixSixtyEightEightyTwo() {
        // MARBLE_FLOATING_DOCK_V258 — the rungs are far enough apart to be told apart in a
        // screenshot, which the V256 48 / 56 / 64 ladder was not.
        assertEquals(56, DockStudio.heightDp(DockSize.SMALL))
        assertEquals(68, DockStudio.heightDp(DockSize.MEDIUM))
        assertEquals(82, DockStudio.heightDp(DockSize.LARGE))
        // Every step is at least ten dp and every step grows.
        val heights = DockSize.entries.map { DockStudio.heightDp(it) }
        heights.zipWithNext().forEach { (small, large) ->
            assertTrue("$small -> $large is not a visible step", large - small >= 10)
        }
    }

    @Test
    fun theLadderNamesItsOwnRungsAndSaysWhatChanges() {
        assertEquals("Slim", DockStudio.ladderTitle(DockSize.SMALL))
        assertEquals("Classic", DockStudio.ladderTitle(DockSize.MEDIUM))
        assertEquals("Grand", DockStudio.ladderTitle(DockSize.LARGE))
        assertEquals("82 dp", DockStudio.ladderHeightLabel(DockSize.LARGE))
        // The three rungs differ in glyph size and in words, not only in a number.
        assertEquals(20, DockStudio.iconSizeDp(DockSize.SMALL))
        assertEquals(24, DockStudio.iconSizeDp(DockSize.MEDIUM))
        assertEquals(28, DockStudio.iconSizeDp(DockSize.LARGE))
        // MARBLE_QUIET_TABS_V263 — the rungs differ in size, not in words: no caption policy is
        // left in the model, and the Settings hints no longer promise one.
        assertEquals("Icons only, tightest capsule", DockStudio.ladderHint(DockSize.SMALL))
        assertEquals("Bigger glyphs, a roomier capsule", DockStudio.ladderHint(DockSize.MEDIUM))
        assertEquals("Largest glyphs, the widest capsule", DockStudio.ladderHint(DockSize.LARGE))
        val studio = java.io.File("src/main/java/com/marbleng/app/model/DockStudio.kt").readText()
        assertFalse("a caption policy came back", "EXPANDED_HOLD_MS" in studio)
        assertFalse("a caption policy came back", "showsCaptionsAlways" in studio)
        DockSize.entries.forEach { size ->
            assertTrue(DockStudio.ladderHint(size).isNotBlank())
        }
    }

    @Test
    fun theFloatingGapIsTheOnlyThingBetweenTheCapsuleAndTheEdge() {
        // MARBLE_FLOATING_DOCK_V258 — the attached presentation is retired, so the gap *is* the
        // dock's vertical place, and it is clamped into a range the shell can honour.
        assertEquals(22, DockStudio.DEFAULT_BOTTOM_GAP_DP)
        assertEquals(DockStudio.DEFAULT_BOTTOM_GAP_DP, DockStudio.BOTTOM_MARGIN_DP)
        assertEquals(DockStudio.MIN_BOTTOM_GAP_DP, DockStudio.bottomGapDp(-40))
        assertEquals(DockStudio.MAX_BOTTOM_GAP_DP, DockStudio.bottomGapDp(9_999))
        assertEquals(26, DockStudio.bottomGapDp(26))
        DockSize.entries.forEach { size ->
            assertEquals(
                DockStudio.heightDp(size) + 26,
                DockStudio.occupiedHeightDp(size, 26)
            )
        }
    }

    @Test
    fun theClearanceEveryPageReservesGrowsWithTheChosenGap() {
        DockSize.entries.forEach { size ->
            val floor = DockStudio.clearanceDp(size, DockStudio.MIN_BOTTOM_GAP_DP)
            val tall = DockStudio.clearanceDp(size, DockStudio.MAX_BOTTOM_GAP_DP)
            // Never below the floor the product shipped with, and never below the dock itself.
            assertTrue(floor >= DockStudio.BASE_CLEARANCE_DP)
            assertTrue(tall > floor)
            assertTrue(tall >= DockStudio.occupiedHeightDp(size, DockStudio.MAX_BOTTOM_GAP_DP))
            // Whatever the gap, the capsule fits the clearance the page was told to reserve.
            assertTrue(
                DockStudio.fitsClearance(size, tall, DockStudio.MAX_BOTTOM_GAP_DP)
            )
        }
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
            // MARBLE_DOCK_RADII_V260 pinned 44 % of the short side and an explicit "not a stadium".
            // MARBLE_GLASS_DOCK_V261 reversed that half of the judgement — the bar read as a
            // rectangle with soft ends next to the floating glass reference the brief named — so the
            // pinned rule is the shipped ratio *and* the inequality in the other direction: the
            // capsule's ends are two half-circles, exactly.
            assertEquals(
                "half the short side at every rung",
                kotlin.math.round(height * DockStudio.CORNER_RATIO),
                DockStudio.cornerRadiusDp(size),
                0.001f
            )
            assertTrue(
                "the capsule is a stadium, so its ends are two half-circles",
                DockStudio.cornerRadiusDp(size) >= height / 2f - 0.5f
            )
            // …and everything nested in it still reads *one* inset less round, at every rung.
            assertEquals(
                DockStudio.cornerRadiusIntDp(size) - DockStudio.INNER_CORNER_INSET_DP.toFloat(),
                DockStudio.innerRadiusDp(size),
                0.001f
            )
        }
    }

    @Test
    fun everyRungStillDiffersInItsGlyphAndItsInnerAir() {
        // MARBLE_QUIET_TABS_V263 — with the caption retired these are the three differences the
        // ladder actually offers; all three must still grow with the rung.
        val glyphs = DockSize.entries.map { DockStudio.iconSizeDp(it) }
        val air = DockSize.entries.map { DockStudio.pillPaddingDp(it) }
        val heights = DockSize.entries.map { DockStudio.heightDp(it) }
        listOf(glyphs, air, heights).forEach { ladder ->
            ladder.zipWithNext().forEach { (small, large) ->
                assertTrue("$small -> $large must grow", large > small)
            }
        }
    }

    @Test
    fun theCentreTooltipIsTheOnlyTimedWordInTheDock() {
        // V263 removed the tab caption and its two-second hold. The tooltip is a reading the
        // reader asked for, so it stays; nothing else in the dock times a word.
        assertEquals(1_400L, DockStudio.CENTER_TOOLTIP_HOLD_MS)
        val dock = java.io.File("src/main/java/com/marbleng/app/ui/MarbleLumenDock.kt").readText()
        val item = java.io.File("src/main/java/com/marbleng/app/ui/NavigationItemView.kt").readText()
        assertFalse("a tab caption came back", "LaunchedEffect(selection)" in dock)
        assertFalse("a tab caption came back", "AnimatedContent(" in item)
        assertTrue("the label must stay readable to a screen reader", "contentDescription = tab.label" in item)
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
        // A mixed clipboard is not a clean source (there is no single subscription URL to offer),
        // but it is still a share link: the importer imports the links it recognises.
        assertEquals(
            IntakeKind.SHARE_LINK,
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
        assertEquals(ServerMenuAction.EXPORT_QR, ServerMenuAction.fromId("export_qr"))
        assertNull(ServerMenuAction.fromId("nope"))
    }

    @Test
    fun theCopiedCheckmarkIsClaimedForFifteenHundredMilliseconds() {
        assertEquals(1_500L, IpTileCopy.COPIED_HOLD_MS)
    }
}
