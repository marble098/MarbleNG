package com.marbleng.app.ui

import com.marbleng.app.core.SmartNotificationKind
import com.marbleng.app.core.kindSubText
import com.marbleng.app.core.statusSubText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarbleNativeAndroidV213Test {
    @Test
    fun theRowHeightTableIsThePlatforms() {
        assertEquals(56, MarbleNativeSpec.RowMinHeightDp)
        assertEquals(72, MarbleNativeSpec.RowTwoLineHeightDp)
        assertEquals(56, MarbleNativeSpec.rowHeightDp(hasSubtitle = false))
        assertEquals(72, MarbleNativeSpec.rowHeightDp(hasSubtitle = true))

        assertEquals(48, MarbleNativeSpec.RowDenseHeightDp)
        assertEquals(48, MarbleNativeSpec.TouchTargetDp)
    }

    @Test
    fun nothingInteractiveMayBePaintedUnderTheTouchFloor() {
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.RowMinHeightDp))
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.RowTwoLineHeightDp))
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.RowDenseHeightDp))
        assertTrue(MarbleNativeSpec.meetsTouchFloor(MarbleNativeSpec.TouchTargetDp))

        assertTrue(!MarbleNativeSpec.meetsTouchFloor(36))
        assertTrue(!MarbleNativeSpec.meetsTouchFloor(0))
    }

    @Test
    fun theCornerRampAndTheDividerAreThePlatforms() {
        assertEquals(24, MarbleNativeSpec.CardCornerDp)
        assertEquals(20, MarbleNativeSpec.TileCornerDp)
        assertEquals(16, MarbleNativeSpec.RowCornerDp)
        assertTrue(MarbleNativeSpec.CardCornerDp > MarbleNativeSpec.TileCornerDp)
        assertTrue(MarbleNativeSpec.TileCornerDp > MarbleNativeSpec.RowCornerDp)

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
        assertTrue(MarbleNativeSpec.CardElevationDp >= 4)
        assertTrue(MarbleNativeSpec.CardLiftedElevationDp > MarbleNativeSpec.CardElevationDp)

        assertTrue(MarbleNativeSpec.ContactElevationDp < MarbleNativeSpec.CardElevationDp)
        assertEquals(2, MarbleNativeSpec.ContactElevationDp)

        assertEquals(56, MarbleNativeSpec.IndicatorWidthDp)
        assertEquals(32, MarbleNativeSpec.IndicatorHeightDp)
    }

    @Test
    fun theNotificationSubtextIsThePlatformsSecondLine() {
        assertEquals("Connected", statusSubText("Connected • Frankfurt • 42 ms"))
        assertEquals("Reconnecting", statusSubText("Reconnecting"))
        assertEquals("Securing", statusSubText("Securing • route 2"))

        assertEquals("MarbleNG", statusSubText(""))
        assertEquals("MarbleNG", statusSubText("   "))
        assertEquals("MarbleNG", statusSubText("x".repeat(64)))

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

        assertTrue(native.contains("MaterialTheme.colorScheme.surfaceContainerLowest"))
        assertTrue(native.contains("scheme.outlineVariant"))
        assertTrue(native.contains("MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(native.contains("scheme.surfaceContainerHighest"))
        assertTrue(native.contains("scheme.onPrimary"))

        assertTrue(native.contains("MarbleNativeSpec.ContactElevationDp"))
    }

    @Test
    fun theSettingsRowsAndSwitchesAreThePlatforms() {
        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")

        assertTrue(ui.contains("private fun SettingSwitch("))
        assertTrue(ui.contains("MarbleNativeListItem("))
        assertTrue(ui.contains("MarbleNativeSwitch("))

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

        assertTrue(ui.contains("MarbleNativeSpec.SectionLabelTrackingSp"))
        assertTrue("a group heading is still uppercased", "trx(title).uppercase()" !in ui)
    }

    @Test
    fun theChromeTabIndicatorAndTileAreOnThePlatformsSkin() {
        val chrome = sourceFile("src/main/java/com/marbleng/app/ui/MarbleFloatingChrome.kt")
        assertTrue(chrome.contains("surface = marbleNativeContainerHigh()"))
        assertTrue(chrome.contains("border = marbleNativeOutline()"))

        assertTrue(chrome.contains("shadow = Aether.FloatShadow"))
        assertTrue(chrome.contains("actions = Aether.FloatActions"))

        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        assertTrue("the dock's indicator is not a stadium", ui.contains("val glassShape = marbleNativePill()"))

        val tiles = sourceFile("src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt")
        assertTrue(tiles.contains("marbleNativeShape(MarbleNativeSpec.TileCornerDp.dp)"))
        assertTrue(tiles.contains("marbleNativeCardFill()"))
        assertTrue(tiles.contains("marbleNativeOutline()"))

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

        assertTrue(notifier.contains("enableVibration(false)"))
        assertTrue(notifier.contains("setSound(null, null)"))

        val manifest = sourceFile("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:enableOnBackInvokedCallback=\"true\""))
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))

        val icon = File(repoRoot(), "app/src/main/res/drawable/ic_notification_stop.xml")
        assertTrue("the stop action icon does not exist", icon.isFile)
    }

    @Test
    fun theWholeProductIsOnOneMaterial() {
        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")

        assertTrue(ui.contains("val panelShape = marbleNativeShape(MarbleNativeSpec.CardCornerDp.dp)"))
        assertTrue(ui.contains("val panelShadow = marbleNativeShadow(Aether.Cyan)"))
        assertTrue(ui.contains("val slotShadow = marbleNativeShadow(tone)"))
        assertTrue(ui.contains("val stripShadow = marbleNativeShadow(Aether.Cyan)"))

        assertTrue("the settings tone rail survived", "private fun SettingsToneRail(" !in ui)
        assertTrue("a settings row still draws the tone rail", "SettingsToneRail(tone" !in ui)

        assertTrue(ui.contains("val insetFill = marbleNativeContainerHigh()"))
        assertTrue(ui.contains("val slotOutline = marbleNativeOutline()"))
        // MARBLE_AURORA_SURFACE_V219 — the disclosure moved from a naked native hairline to the
        // product's grid chevron on a tone disc, so "the whole product is on one material" is now
        // asserted against the disc. The native chevron itself still owns the platform rows.
        assertTrue(ui.contains("MarbleGlyphIcon(MarbleGlyph.CHEVRON"))
        assertTrue(
            sourceFile("src/main/java/com/marbleng/app/ui/MarbleNativeAndroid.kt")
                .contains("fun MarbleNativeChevron(")
        )

        assertTrue(ui.contains("val shape = marbleNativePill()"))
        assertTrue(ui.contains("val restingFill = marbleNativeCardFill()"))

        assertTrue(ui.contains("private val ServersCardShape = RoundedCornerShape(20.dp)"))

        val design = sourceFile("src/main/java/com/marbleng/app/ui/MarbleDesignSystem.kt")

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

        assertTrue(design.contains("fill: Color = if (selected) homeCloudSelectedFill() else homeCloudCardFill()"))
        assertTrue(design.contains("val CardShape = RoundedCornerShape(MarbleNativeSpec.CardCornerDp.dp)"))
        assertTrue(design.contains("val CardElevation = MarbleDepthPolicy.RestingElevation"))
        assertTrue(design.contains("val SelectedElevation = MarbleDepthPolicy.RaisedElevation"))

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

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "README.md").isFile && File(dir, "app").isDirectory) return dir
            dir = dir.parentFile
        }
        return File(".").absoluteFile
    }

    private fun sourceFile(relative: String): String {
        val candidate = listOf(File(relative), File("../$relative"), File("app/$relative"))
            .firstOrNull { it.isFile }
        requireNotNull(candidate) { "cannot locate $relative from ${File(".").absolutePath}" }
        return candidate.readText()
    }
}
