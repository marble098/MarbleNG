package com.marbleng.app.ui

import com.marbleng.app.core.SmartNotificationKind
import com.marbleng.app.core.kindSubText
import com.marbleng.app.core.statusSubText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's own numbers, and the surfaces that now wear them.
 *
 * The chapter has two halves and neither can be caught by a compiler.
 *
 * 1. **The measurements.** A list row is 56 dp because Android's is; a control 48 dp because that
 *    is the platform's touch floor. Those are numbers, and a number is exactly the kind of design
 *    decision that drifts back one release at a time — so they are pinned here, as numbers.
 * 2. **The wiring.** "The switch is the platform's switch" and "the card is a container" are
 *    properties of *call sites*: a row that stops being a `MarbleNativeListItem`, a notifier field
 *    that quietly stops being set, a manifest flag that is removed by a merge. All three are single
 *    lines that no type system can defend, so they are asserted against the source, the way
 *    [ServerTileParityV212Test] asserts the tile's wiring.
 */
class MarbleNativeAndroidV213Test {

    // ------------------------------------------------------------------ the platform's numbers

    @Test
    fun theRowHeightTableIsThePlatforms() {
        // Android's list item: 56 dp one line, 72 dp with a supporting line.
        assertEquals(56, MarbleNativeSpec.RowMinHeightDp)
        assertEquals(72, MarbleNativeSpec.RowTwoLineHeightDp)
        assertEquals(56, MarbleNativeSpec.rowHeightDp(hasSubtitle = false))
        assertEquals(72, MarbleNativeSpec.rowHeightDp(hasSubtitle = true))
        // The dense form is still the touch floor, never below it.
        assertEquals(48, MarbleNativeSpec.RowDenseHeightDp)
        assertEquals(48, MarbleNativeSpec.TouchTargetDp)
    }

    @Test
    fun nothingInteractiveMayBePaintedUnderTheTouchFloor() {
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.RowMinHeightDp))
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.RowTwoLineHeightDp))
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.RowDenseHeightDp))
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.TouchTargetDp))
        // The defect this chapter exists to remove: the old option row painted ~36 dp.
        assertTrue(!MarbleNativeSpec.meetsTouchFloor(36))
        assertTrue(!MarbleNativeSpec.meetsTouchFloor(0))
    }

    @Test
    fun theCornerRampAndTheDividerAreThePlatforms() {
        // Card / tile / row, in the platform's descending order.
        assertEquals(24, MarbleNativeSpec.CardCornerDp)
        assertEquals(20, MarbleNativeSpec.TileCornerDp)
        assertEquals(16, MarbleNativeSpec.RowCornerDp)
        assertTrue(MarbleNativeSpec.CardCornerDp > MarbleNativeSpec.TileCornerDp)
        assertTrue(MarbleNativeSpec.TileCornerDp > MarbleNativeSpec.RowCornerDp)
        // The leading container is the platform's 40 dp, and a divider clears it.
        assertEquals(40, MarbleNativeSpec.LeadingContainerDp)
        assertEquals(22, MarbleNativeSpec.LeadingGlyphDp)
        assertEquals(MarbleNativeSpec.DividerInsetDp, MarbleNativeSpec.dividerInsetDp(hasLeading = false))
        assertTrue(
            "a divider after a leading container starts past it",
            MarbleNativeSpec.dividerInsetDp(hasLeading = true) >=
                MarbleNativeSpec.RowPaddingHorizontalDp + MarbleNativeSpec.LeadingContainerDp
        )
        assertEquals(1, MarbleNativeSpec.DividerThicknessDp)
    }

    @Test
    fun theLiftStartsWhereTheEyeCanSeeIt() {
        // The defect: a 2 dp resting lift on a layered page is a smudge, not a lift.
        assertTrue(MarbleNativeSpec.CardElevationDp >= 4)
        assertTrue(MarbleNativeSpec.CardLiftedElevationDp > MarbleNativeSpec.CardElevationDp)
        // And the contact shadow is below the lift — it is what puts a surface ON the page.
        assertTrue(MarbleNativeSpec.ContactElevationDp < MarbleNativeSpec.CardElevationDp)
        assertEquals(2, MarbleNativeSpec.ContactElevationDp)
        // The navigation bar's indicator, as Material defines it.
        assertEquals(56, MarbleNativeSpec.IndicatorWidthDp)
        assertEquals(32, MarbleNativeSpec.IndicatorHeightDp)
    }

    @Test
    fun theNotificationSubtextIsThePlatformsSecondLine() {
        // The body's first clause is the state — that is the service's own format.
        assertEquals("Connected", statusSubText("Connected • Frankfurt • 42 ms"))
        assertEquals("Reconnecting", statusSubText("Reconnecting"))
        assertEquals("Securing", statusSubText("Securing • route 2"))
        // A body with no leading state is not turned into one: the line falls back to the app.
        assertEquals("MarbleNG", statusSubText(""))
        assertEquals("MarbleNG", statusSubText("   "))
        assertEquals("MarbleNG", statusSubText("x".repeat(64)))
        // Trailing punctuation never reaches the shade.
        assertEquals("Connected", statusSubText("Connected:"))
    }

    @Test
    fun everyAlertKindNamesItselfInThePlatformsSubtext() {
        assertEquals("Privacy", kindSubText(SmartNotificationKind.PRIVACY))
        assertEquals("Recovery", kindSubText(SmartNotificationKind.RECOVERY))
        assertEquals("Network", kindSubText(SmartNotificationKind.NETWORK))
        assertEquals("Subscription", kindSubText(SmartNotificationKind.SUBSCRIPTION))
        assertEquals("Update", kindSubText(SmartNotificationKind.CORE))
        assertEquals("Test", kindSubText(SmartNotificationKind.TEST))
        assertEquals("Connection", kindSubText(SmartNotificationKind.CONNECTION))
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun theCardComponentIsThePlatformsCard() {
        val native = sourceFile("src/main/java/com/marbleng/app/ui/MarbleNativeAndroid.kt")
        assertTrue("the card component is gone", native.contains("fun MarbleNativeCard("))
        assertTrue("the list item is gone", native.contains("fun MarbleNativeListItem("))
        assertTrue("the platform switch is gone", native.contains("fun MarbleNativeSwitch("))
        assertTrue("the radio row is gone", native.contains("fun MarbleNativeChoiceRow("))
        assertTrue("the segmented control is gone", native.contains("fun MarbleNativeSegmentedRow("))
        assertTrue("the section heading is gone", native.contains("fun MarbleNativeSectionHeader("))
        assertTrue("the divider is gone", native.contains("fun MarbleNativeDivider("))
        assertTrue("the chevron is gone", native.contains("fun MarbleNativeChevron("))
        // Every colour the file paints is a platform role, never a hand-mixed brand value.
        assertTrue(native.contains("MaterialTheme.colorScheme.surfaceContainerLowest"))
        assertTrue(native.contains("scheme.outlineVariant"))
        assertTrue(native.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(native.contains("scheme.surfaceContainerHighest"))
        assertTrue(native.contains("scheme.onPrimary"))
        // The platform's contact shadow is part of the card, not an option.
        assertTrue(native.contains("MarbleNativeSpec.ContactElevationDp"))
    }

    @Test
    fun theSettingsRowsAndSwitchesAreThePlatforms() {
        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        // The option row is the platform's list item, with the platform's switch.
        assertTrue(ui.contains("private fun SettingSwitch("))
        assertTrue(ui.contains("MarbleNativeListItem("))
        assertTrue(ui.contains("MarbleNativeSwitch("))
        // The five hand-picked switch colours are gone: the palette is the platform's roles.
        assertTrue(
            "the switch palette is not the platform's",
            ui.contains("checkedTrackColor = MaterialTheme.colorScheme.primary") &&
                ui.contains("uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest")
        )
        assertTrue(
            "a hand-picked switch colour survived",
            "checkedThumbColor = Aether.VoidElevated" !in ui &&
                "uncheckedThumbColor = Aether.InkMuted" !in ui
        )
        // The hub's group heading stopped shouting: no uppercase, no 1.6 sp tracking.
        assertTrue(ui.contains("MarbleNativeSpec.SectionLabelTrackingSp"))
        assertTrue("a group heading is still uppercased", "trx(title).uppercase()" !in ui)
    }

    @Test
    fun theChromeTabIndicatorAndTileAreOnThePlatformsSkin() {
        val chrome = sourceFile("src/main/java/com/marbleng/app/ui/MarbleFloatingChrome.kt")
        assertTrue(chrome.contains("surface = marbleNativeContainerHigh()"))
        assertTrue(chrome.contains("border = marbleNativeOutline()"))
        // The palette's own shadow and the four action tones are deliberately untouched.
        assertTrue(chrome.contains("shadow = Aether.FloatShadow"))
        assertTrue(chrome.contains("actions = Aether.FloatActions"))

        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        assertTrue("the dock's indicator is not a stadium", ui.contains("val glassShape = marbleNativePill()"))

        val tiles = sourceFile("src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt")
        assertTrue(tiles.contains("marbleNativeShape(MarbleNativeSpec.TileCornerDp.dp)"))
        assertTrue(tiles.contains("marbleNativeCardFill()"))
        assertTrue(tiles.contains("marbleNativeOutline()"))
        // The V210/V212 guarantees survive the re-skin.
        assertTrue(tiles.contains("ServerTileTruth.alphaOf(reach)"))
        assertTrue(tiles.contains("marbleSurfaceDepth(shape = shape, fill = fill, rim = rim"))
    }

    @Test
    fun theDepthContractAndTheFramesAreNeutral() {
        val depth = sourceFile("src/main/java/com/marbleng/app/ui/MarbleSurfaceDepth.kt")
        assertTrue(depth.contains("val ContactElevation: Dp = 2.dp"))
        assertTrue(depth.contains("MarbleDepthPolicy.ContactElevation"))
        assertTrue(depth.contains("val RestingElevation: Dp = 4.dp"))
        assertTrue(depth.contains("val RaisedElevation: Dp = 9.dp"))

        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        // A container's edge is neutral: the accent hairline is off the subscription frame.
        assertTrue(ui.contains("private fun serversGroupFrameColor(): Color = marbleNativeOutline()"))
        assertTrue("the server divider still uses the accent hairline", "Aether.GlassBorderSoft.copy(alpha = ServersHierarchy.ROW_DIVIDER_ALPHA)" !in ui)
    }

    @Test
    fun theFlagCircleCanBeFramed() {
        val art = sourceFile("src/main/java/com/marbleng/app/ui/CountryFlagArt.kt")
        assertTrue("the flag circle lost its rim", art.contains("ring: Color? = null"))
        assertTrue("the rim is not drawn", art.contains("Modifier.border(1.dp, ring, CircleShape)"))
        val home = sourceFile("src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt")
        assertTrue("the Home route chip has no frame", home.contains("ring = marbleNativeOutline()"))
    }

    @Test
    fun theNotificationsFillThePlatformsFields() {
        val notifier = sourceFile("src/main/java/com/marbleng/app/core/SmartNotifier.kt")
        assertTrue(notifier.contains("const val ACCENT = 0xFF0066CC.toInt()"))
        assertTrue(notifier.contains(".setColor(ACCENT)"))
        assertTrue(notifier.contains(".setSubText(statusSubText(text))"))
        assertTrue(notifier.contains(".setSubText(kindSubText(kind))"))
        assertTrue(notifier.contains(".setGroup(GROUP_ID)"))
        assertTrue(notifier.contains(".setLocalOnly(true)"))
        assertTrue(notifier.contains("setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)"))
        assertTrue(notifier.contains("R.drawable.ic_notification_stop"))
        // The ongoing channel is silent — the defect was a status channel that buzzed.
        assertTrue(notifier.contains("enableVibration(false)"))
        assertTrue(notifier.contains("setSound(null, null)"))

        val manifest = sourceFile("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:enableOnBackInvokedCallback=\"true\""))
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))

        val icon = File(repoRoot(), "app/src/main/res/drawable/ic_notification_stop.xml")
        assertTrue("the stop action icon does not exist", icon.isFile)
    }

    /**
     * The second wave: the chapter is only a chapter if it reaches the *rest* of the product. One
     * card left on the old recipe — a lift written by hand, a rim in the accent's hue, a settings
     * row 3 dp wider than the platform's anatomy — and the product is a mix of two materials again.
     */
    @Test
    fun theWholeProductIsOnOneMaterial() {
        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        // The menu panel, the fourth tab's cards and the probe strip cast the platform's pair.
        assertTrue(ui.contains("val panelShape = marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)"))
        assertTrue(ui.contains("val panelShadow = marbleNativeShadow(Aether.Cyan)"))
        assertTrue(ui.contains("val slotShadow = marbleNativeShadow(tone)"))
        assertTrue(ui.contains("val stripShadow = marbleNativeShadow(Aether.Cyan)"))
        // The settings rail has no definition and no call site left.
        assertTrue("the settings tone rail survived", "private fun SettingsToneRail(" !in ui)
        assertTrue("a settings row still draws the tone rail", "SettingsToneRail(tone" !in ui)
        // The hub's sub-sections are the platform's inset, and the doors wear the drawn chevron.
        assertTrue(ui.contains("val insetFill = marbleNativeContainerHigh()"))
        assertTrue(ui.contains("val slotOutline = marbleNativeOutline()"))
        assertTrue(ui.contains("MarbleNativeChevron("))
        // The chips are stadiums, the option tiles rest on the platform's container.
        assertTrue(ui.contains("val shape = marbleNativePill()"))
        assertTrue(ui.contains("val restingFill = marbleNativeCardFill()"))
        // The subscription card moved onto the platform's tile corner.
        assertTrue(ui.contains("private val ServersCardShape = RoundedCornerShape(20.dp)"))

        val design = sourceFile("src/main/java/com/marbleng/app/ui/MarbleDesignSystem.kt")
        // The Home page's private copy of the surface ladder is gone: the roles read the scheme.
        assertTrue(design.contains("internal fun homeCloudCardFill(): Color = marbleNativeCardFill()"))
        assertTrue(design.contains("internal fun homeCloudCardBorder(): Color = marbleNativeOutline()"))
        assertTrue(design.contains("internal fun homeCloudInsetFill(): Color = marbleNativeContainerHigh()"))
        assertTrue(design.contains("internal fun homeCloudInsetBorder(): Color = marbleNativeOutline()"))
        assertTrue(
            "the Home-only surface table survived",
            "val LightCardFill" !in design &&
                "val DarkCardFill" !in design &&
                "val LightInsetFill" !in design &&
                "val DarkInsetFill" !in design
        )
        // The one Home container still routes through those roles, and it takes the platform's
        // corner and the one depth contract.
        assertTrue(design.contains("fill: Color = if (selected) homeCloudSelectedFill() else homeCloudCardFill()"))
        assertTrue(design.contains("val CardShape = RoundedCornerShape(MarbleNativeSpec.CardCornerDp.dp)"))
        assertTrue(design.contains("val CardElevation = MarbleDepthPolicy.RestingElevation"))
        assertTrue(design.contains("val SelectedElevation = MarbleDepthPolicy.RaisedElevation"))
        // A choice tile is never painted under the platform's touch floor, and never shouts.
        assertTrue(design.contains("minHeight: Dp = MarbleNativeSpec.TouchTargetDp.dp"))
        assertTrue(design.contains("val tileChosen = marbleNativeSelectedFill(tone)"))
        assertTrue("an uppercased label survived in the design system", "trx(title).uppercase()" !in design)
    }

    @Test
    fun theChapterIsWrittenDown() {
        assertTrue(
            "the chapter has no document",
            sourceFile("docs/NATIVE_ANDROID_V213.md").contains("MARBLE_NATIVE_ANDROID_V213")
        )
        assertTrue(
            "the critique has no document",
            sourceFile("docs/APPEARANCE_CRITIQUE_V213.md").contains("the seven surfaces")
        )
        assertTrue(
            "the README does not link the chapter",
            sourceFile("../README.md").contains("docs/NATIVE_ANDROID_V213.md")
        )
    }

    // ------------------------------------------------------------------ helpers

    /** The repository root, whichever directory the test runner started in. */
    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "README.md").isFile && File(dir, "app").isDirectory) return dir
            dir = dir.parentFile
        }
        return File(".").absoluteFile
    }

    /** One source file, resolved the way the rest of the UI suite resolves it. */
    private fun sourceFile(relative: String): String {
        val candidate = listOf(File(relative), File("../$relative"), File("app/$relative"))
            .firstOrNull { it.isFile }
        requireNotNull(candidate) { "cannot locate $relative from ${File(".").absolutePath}" }
        return candidate.readText()
    }
}
