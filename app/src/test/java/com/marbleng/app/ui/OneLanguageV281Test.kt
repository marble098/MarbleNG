package com.marbleng.app.ui

import com.marbleng.app.core.NetworkCouncil
import com.marbleng.app.core.PingReadout
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ConnectPhase
import com.marbleng.app.model.DockSlotAccent
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.HeroTone
import com.marbleng.app.model.PlanStatus
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.ui.theme.MarbleElevation
import com.marbleng.app.ui.theme.MarbleLevelSpec
import com.marbleng.app.ui.theme.MarbleRadius
import com.marbleng.app.ui.theme.MarbleSelectionRung
import com.marbleng.app.ui.theme.MarbleSkeletonSpec
import com.marbleng.app.ui.theme.MarbleSpace
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleStatSpec
import com.marbleng.app.ui.theme.MarbleTone
import com.marbleng.app.ui.theme.MarbleVeil
import com.marbleng.app.ui.theme.MarbleWash
import com.marbleng.app.ui.theme.marbleFallbackLightScheme
import com.marbleng.app.ui.theme.marbleOnToneColor
import com.marbleng.app.ui.theme.marbleOnToneContainer
import com.marbleng.app.ui.theme.marbleStatusColors
import com.marbleng.app.ui.theme.marbleToneColor
import com.marbleng.app.ui.theme.marbleToneContainer
import com.marbleng.app.ui.theme.marbleToneOfFraction
import com.marbleng.app.ui.theme.marbleToneOfScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MARBLE_ONE_LANGUAGE_V281 — the pins for one design language.
 *
 * The complaint this pass answers is not "a screen is ugly". It is that the product had grown
 * eleven private answers to "what colour is a verdict", fourteen wash strengths, fifteen corner
 * radii, twelve gaps, five stat tiles, two icon buttons and two bottom sheets — so the same fact
 * was drawn differently on two pages, and a change of theme moved only some of them.
 *
 * Everything here is pure JVM: the tone tables are plain functions of a scheme and a status ramp,
 * the scales are `const val` ladders, and the component rules are source invariants read from the
 * same files the compiler reads. `tools/design-system-conformance.py` enforces the same rules over
 * every file in CI; this holds the *meanings* — which tone a state gets, which rung a tie snaps to,
 * which component owns a role — where a reviewer can read them.
 */
class OneLanguageV281Test {

    private val scheme = marbleFallbackLightScheme(FlatAccent.OCEAN)
    private val status = marbleStatusColors(dark = false)

    private fun tone(tone: MarbleTone) = marbleToneColor(scheme, status, tone)

    private fun main(path: String): String =
        File("src/main/java/com/marbleng/app/$path").readText()

    // ------------------------------------------------------------------ one verdict, one colour

    @Test
    fun everyToneResolvesToExactlyOneColourRole() {
        assertEquals(status.success, tone(MarbleTone.GOOD))
        assertEquals(status.warning, tone(MarbleTone.FAIR))
        assertEquals(scheme.error, tone(MarbleTone.POOR))
        assertEquals(scheme.primary, tone(MarbleTone.BRAND))
        assertEquals(scheme.tertiary, tone(MarbleTone.ACCENT))
        assertEquals(scheme.secondary, tone(MarbleTone.SUPPORT))
        assertEquals(scheme.onSurfaceVariant, tone(MarbleTone.UNKNOWN))
        assertEquals(scheme.outline, tone(MarbleTone.STRUCTURAL))

        // A dead route is a fact, not an alarm: the quiet ink, quieted by exactly one veil rung.
        assertEquals(
            scheme.onSurfaceVariant.copy(alpha = MarbleVeil.QUIET),
            tone(MarbleTone.LOST)
        )
        assertNotEquals("a lost route must not shout like an unmeasured one", tone(MarbleTone.UNKNOWN), tone(MarbleTone.LOST))
    }

    @Test
    fun everyToneCarriesItsOwnInkAndContainer() {
        assertEquals(status.onSuccess, marbleOnToneColor(scheme, status, MarbleTone.GOOD))
        assertEquals(status.onWarning, marbleOnToneColor(scheme, status, MarbleTone.FAIR))
        assertEquals(scheme.onError, marbleOnToneColor(scheme, status, MarbleTone.POOR))
        assertEquals(scheme.onPrimary, marbleOnToneColor(scheme, status, MarbleTone.BRAND))
        assertEquals(scheme.onTertiary, marbleOnToneColor(scheme, status, MarbleTone.ACCENT))
        assertEquals(scheme.onSecondary, marbleOnToneColor(scheme, status, MarbleTone.SUPPORT))

        assertEquals(status.successContainer, marbleToneContainer(scheme, status, MarbleTone.GOOD))
        assertEquals(status.warningContainer, marbleToneContainer(scheme, status, MarbleTone.FAIR))
        assertEquals(scheme.errorContainer, marbleToneContainer(scheme, status, MarbleTone.POOR))
        assertEquals(scheme.primaryContainer, marbleToneContainer(scheme, status, MarbleTone.BRAND))
        assertEquals(scheme.tertiaryContainer, marbleToneContainer(scheme, status, MarbleTone.ACCENT))

        assertEquals(
            status.onSuccessContainer,
            marbleOnToneContainer(scheme, status, MarbleTone.GOOD)
        )
        assertEquals(
            status.onWarningContainer,
            marbleOnToneContainer(scheme, status, MarbleTone.FAIR)
        )
        assertEquals(
            scheme.onErrorContainer,
            marbleOnToneContainer(scheme, status, MarbleTone.POOR)
        )
    }

    @Test
    fun aToneAndItsInkAreNeverTheSameColour() {
        MarbleTone.entries.forEach { tone ->
            assertNotEquals(
                "$tone would print its own label in its own ground",
                marbleToneColor(scheme, status, tone),
                marbleOnToneColor(scheme, status, tone)
            )
            assertNotEquals(
                "$tone would print its own label in its own container",
                marbleToneContainer(scheme, status, tone),
                marbleOnToneContainer(scheme, status, tone)
            )
        }
    }

    // ------------------------------------------------------------------ the connection verdict

    @Test
    fun theConnectionVerdictIsOneTableEverywhere() {
        assertEquals(MarbleTone.GOOD, connectPhaseTone(ConnectPhase.CONNECTED))
        assertEquals(MarbleTone.FAIR, connectPhaseTone(ConnectPhase.CONNECTING))
        assertEquals(MarbleTone.FAIR, connectPhaseTone(ConnectPhase.DISCONNECTING))
        assertEquals(MarbleTone.POOR, connectPhaseTone(ConnectPhase.BLOCKED))
        assertEquals(MarbleTone.BRAND, connectPhaseTone(ConnectPhase.IDLE))

        // The pill, the hero stage, the hero state light, the log capsule, the IP headline and the
        // dock dot all read this one table now, so none of them can keep a private fifth answer.
        val surfaces = listOf(
            "ui/MarbleExpressiveShell.kt",
            "ui/MarbleHomeHero.kt",
            "ui/IpIdentityScreen.kt",
            "ui/MarbleApp.kt"
        )
        surfaces.forEach { path ->
            assertTrue("$path must ask the shared table", "connectPhaseTone(" in main(path))
        }
        // A phase, never three booleans: precedence lives in the model, where V264 already tested it.
        assertFalse(
            "the hero stage is still choosing its own container per state",
            "working -> colors.tertiaryContainer" in main("ui/MarbleHomeHero.kt")
        )
    }

    @Test
    fun aHeroCellTakesAVerdictColourOnlyForAVerdict() {
        assertEquals(MarbleTone.GOOD, heroTone(HeroTone.GOOD))
        assertEquals(MarbleTone.FAIR, heroTone(HeroTone.FAIR))
        assertEquals(MarbleTone.POOR, heroTone(HeroTone.POOR))
        // Being measured, live, pending and idle are not verdicts: they stay in the stage's ink.
        listOf(HeroTone.WORKING, HeroTone.LIVE, HeroTone.PENDING, HeroTone.IDLE).forEach {
            assertNull("$it is not a verdict and must not take a verdict colour", heroTone(it))
        }
    }

    @Test
    fun everyOtherVerdictMapperSpeaksInTones() {
        assertEquals(MarbleTone.GOOD, healthTone(ServerHealth.GOOD))
        assertEquals(MarbleTone.FAIR, healthTone(ServerHealth.SLOW))
        assertEquals(MarbleTone.LOST, healthTone(ServerHealth.DEAD))
        assertEquals(MarbleTone.STRUCTURAL, healthTone(ServerHealth.UNTESTED))

        assertEquals(MarbleTone.GOOD, pingTone(PingReadout.FAST, ServerHealth.UNTESTED))
        assertEquals(MarbleTone.FAIR, pingTone(PingReadout.OK, ServerHealth.UNTESTED))
        assertEquals(MarbleTone.POOR, pingTone(PingReadout.SLOW, ServerHealth.UNTESTED))
        assertEquals(MarbleTone.LOST, pingTone(PingReadout.TIMEOUT, ServerHealth.GOOD))
        // Nothing measured yet is not a weak measurement: it falls back to the row's health.
        assertEquals(MarbleTone.STRUCTURAL, pingTone(PingReadout.UNKNOWN, ServerHealth.UNTESTED))
        assertEquals(MarbleTone.GOOD, pingTone(PingReadout.UNKNOWN, ServerHealth.GOOD))

        assertEquals(MarbleTone.BRAND, protocolTone("VLESS"))
        assertEquals(MarbleTone.SUPPORT, protocolTone(" trojan "))
        assertEquals(MarbleTone.ACCENT, protocolTone("shadowsocks"))
        assertEquals(MarbleTone.GOOD, protocolTone("wireguard"))

        assertEquals(MarbleTone.BRAND, pingBadgeTone(null, ProbeState.TESTING))
        assertEquals(MarbleTone.UNKNOWN, pingBadgeTone(null, ProbeState.IDLE))
        assertEquals(MarbleTone.POOR, pingBadgeTone(probe(success = 0, latencyMs = 0.0), ProbeState.IDLE))
        assertEquals(MarbleTone.GOOD, pingBadgeTone(probe(success = 3, latencyMs = 120.0), ProbeState.IDLE))
        assertEquals(MarbleTone.FAIR, pingBadgeTone(probe(success = 3, latencyMs = 300.0), ProbeState.IDLE))
        assertEquals(MarbleTone.POOR, pingBadgeTone(probe(success = 3, latencyMs = 900.0), ProbeState.IDLE))

        assertEquals(MarbleTone.BRAND, dockSlotTone(DockSlotAccent.OCEAN))
        assertEquals(MarbleTone.GOOD, dockSlotTone(DockSlotAccent.MINT))
        assertEquals(MarbleTone.SUPPORT, dockSlotTone(DockSlotAccent.VIOLET))
        assertEquals(MarbleTone.FAIR, dockSlotTone(DockSlotAccent.AMBER))

        assertEquals(MarbleTone.BRAND, planTone(PlanStatus.Tone.CALM))
        assertEquals(MarbleTone.FAIR, planTone(PlanStatus.Tone.WARN))
        assertEquals(MarbleTone.POOR, planTone(PlanStatus.Tone.DANGER))
        assertEquals(MarbleTone.UNKNOWN, planTone(PlanStatus.Tone.UNKNOWN))

        // A source stripe is a categorical palette, not a verdict: it cycles over six slots, five of
        // them scheme roles, and `mod` keeps a negative index inside the same cycle.
        assertEquals(MarbleTone.BRAND, sourceStripeTone(0))
        assertEquals(MarbleTone.SUPPORT, sourceStripeTone(1))
        assertEquals(MarbleTone.FAIR, sourceStripeTone(4))
        assertEquals(MarbleTone.BRAND, sourceStripeTone(6))
        // The sixth slot is the expressive plum: a palette accent, so no tone claims it.
        assertNull(sourceStripeTone(5))
        assertNull(sourceStripeTone(-1))

        assertEquals(
            MarbleTone.GOOD,
            severityTone(NetworkCouncil.CouncilSeverity.GOOD)
        )
        assertEquals(
            MarbleTone.BRAND,
            severityTone(NetworkCouncil.CouncilSeverity.NOTICE)
        )
        assertEquals(
            MarbleTone.FAIR,
            severityTone(NetworkCouncil.CouncilSeverity.WARNING)
        )
        assertEquals(
            MarbleTone.POOR,
            severityTone(NetworkCouncil.CouncilSeverity.CRITICAL)
        )

        assertEquals(
            MarbleTone.GOOD,
            filterVerdictTone(NetworkCouncil.FilterVerdict.CLEAN)
        )
        assertEquals(
            MarbleTone.FAIR,
            filterVerdictTone(NetworkCouncil.FilterVerdict.SUSPECT)
        )
        assertEquals(
            MarbleTone.POOR,
            filterVerdictTone(NetworkCouncil.FilterVerdict.FILTERED)
        )
        assertEquals(
            MarbleTone.POOR,
            filterVerdictTone(NetworkCouncil.FilterVerdict.UNREACHABLE)
        )
        assertEquals(
            MarbleTone.UNKNOWN,
            filterVerdictTone(NetworkCouncil.FilterVerdict.UNMEASURED)
        )

        assertEquals(MarbleTone.GOOD, tlsProbeVerdict(tls(NetworkCouncil.TlsOutcome.COMPLETED)))
        assertEquals(MarbleTone.POOR, tlsProbeVerdict(tls(NetworkCouncil.TlsOutcome.RESET)))
        assertEquals(MarbleTone.FAIR, tlsProbeVerdict(tls(NetworkCouncil.TlsOutcome.TIMEOUT)))
        assertEquals(MarbleTone.FAIR, tlsProbeVerdict(tls(NetworkCouncil.TlsOutcome.COMPLETED, certificateOk = false)))
        assertEquals(MarbleTone.UNKNOWN, tlsProbeVerdict(tls(NetworkCouncil.TlsOutcome.DNS_FAILED)))
    }

    @Test
    fun oneScoreLadderFeedsEveryGauge() {
        assertEquals(MarbleTone.GOOD, marbleToneOfScore(85))
        assertEquals(MarbleTone.ACCENT, marbleToneOfScore(84))
        assertEquals(MarbleTone.ACCENT, marbleToneOfScore(70))
        assertEquals(MarbleTone.FAIR, marbleToneOfScore(69))
        assertEquals(MarbleTone.FAIR, marbleToneOfScore(55))
        assertEquals(MarbleTone.POOR, marbleToneOfScore(54))

        assertEquals(MarbleTone.GOOD, marbleToneOfFraction(1f))
        assertEquals(MarbleTone.POOR, marbleToneOfFraction(0.1f))
        // Out-of-range input is clamped, not guessed: a gauge can never invent a verdict.
        assertEquals(MarbleTone.GOOD, marbleToneOfFraction(4f))
        assertEquals(MarbleTone.POOR, marbleToneOfFraction(-2f))
    }

    // ------------------------------------------------------------------ the scales themselves

    @Test
    fun everyScaleIsAscendingUniqueAndSnapsTiesUpwards() {
        listOf(
            "MarbleRadius" to MarbleRadius.SCALE,
            "MarbleSpace" to MarbleSpace.SCALE,
            "MarbleElevation" to MarbleElevation.LADDER
        ).forEach { (name, scale) ->
            assertEquals("$name repeats a rung", scale.distinct(), scale)
            assertEquals("$name is not ascending", scale.sorted(), scale)
        }
        listOf("MarbleWash" to MarbleWash.SCALE, "MarbleVeil" to MarbleVeil.SCALE)
            .forEach { (name, scale) ->
                assertEquals("$name repeats a rung", scale.distinct(), scale)
                assertEquals("$name is not ascending", scale.sorted(), scale)
            }

        // A tie is always an optical rung against a structural one, and the structural one wins.
        assertEquals(MarbleSpace.INNER, MarbleSpace.nearest(9))
        assertEquals(MarbleSpace.XL, MarbleSpace.nearest(18))
        assertEquals(MarbleSpace.SM, MarbleSpace.snapUp(7))
        assertEquals(MarbleSpace.XS, MarbleSpace.snapUp(3))
        assertEquals(MarbleRadius.TILE, MarbleRadius.nearest(18))
        assertTrue(MarbleRadius.isOnScale(MarbleSpec.CARD_CORNER_DP))
        assertFalse("an off-rung corner must not pass as one", MarbleRadius.isOnScale(18))
    }

    @Test
    fun washesAndVeilsAreTwoHalvesOfOneAlphaLadder() {
        assertTrue(
            "a wash must stay behind content",
            MarbleWash.SCALE.max() < MarbleVeil.SCALE.min()
        )
        assertEquals(MarbleWash.TINT, MarbleWash.nearest(0.14f))
        assertEquals(MarbleWash.SOFT, MarbleWash.nearest(0.11f))
        assertEquals(MarbleVeil.QUIET, MarbleVeil.nearest(0.74f))
        assertEquals(MarbleVeil.FADED, MarbleVeil.nearest(0.56f))
        // The one ink the tone table quiets is a veil rung, not a private number.
        assertEquals(0.72f, MarbleVeil.QUIET, 0.0001f)
    }

    @Test
    fun theElevationLadderIsTwoIdeasNotFourStrengths() {
        assertEquals(listOf(0, 2, 8, 12), MarbleElevation.LADDER)
        assertEquals(0, MarbleElevation.FLAT_DP)
        assertEquals(2, MarbleElevation.TONAL_DP)
        assertTrue(MarbleElevation.SOFT_DP < MarbleElevation.FLOAT_DP)
    }

    @Test
    fun theShapeRungsAgreeWithTheGeometrySpecTheyReplace() {
        assertEquals(MarbleSpec.CARD_CORNER_DP, MarbleRadius.CARD)
        assertEquals(MarbleSpec.GROUP_CORNER_DP, MarbleRadius.GROUP)
        assertEquals(MarbleSpec.ROW_ICON_CORNER_DP, MarbleRadius.ROW_ICON)
        assertEquals(MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP, MarbleSpace.XL)
        assertEquals(MarbleSpec.CARD_PADDING_DP, MarbleSpace.LG)
        // The screen gutter and the generous card padding are the same rung on purpose: a card that
        // fills a narrow window has one gutter, not two competing ones.
        assertEquals(MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP, MarbleSpec.CARD_GENEROUS_PADDING_DP)
        assertEquals(MarbleSpec.CARD_GENEROUS_PADDING_DP, MarbleSpace.XL)
        assertEquals(MarbleSpec.ROW_GAP_DP, MarbleSpace.ROW_GAP)

        // The two selection morphs are the small and the thumb-sized rung, and nothing else.
        assertEquals(listOf(MarbleSelectionRung.COMPACT, MarbleSelectionRung.CONTROL), MarbleSelectionRung.RUNGS)
        assertEquals(MarbleRadius.SM, MarbleSelectionRung.COMPACT.idleDp)
        assertEquals(MarbleRadius.CARD, MarbleSelectionRung.COMPACT.selectedDp)
        assertEquals(MarbleRadius.CONTROL, MarbleSelectionRung.CONTROL.idleDp)
        assertEquals(MarbleRadius.GROUP, MarbleSelectionRung.CONTROL.selectedDp)
    }

    @Test
    fun theComponentSpecsAreBuiltFromTheScalesNotBesideThem() {
        assertEquals(MarbleRadius.TILE, MarbleStatSpec.CORNER_DP)
        assertEquals(MarbleSpace.MD, MarbleStatSpec.PADDING_H_DP)
        assertEquals(MarbleSpace.INNER, MarbleStatSpec.PADDING_V_DP)
        assertEquals(MarbleSpace.SM, MarbleStatSpec.COMPACT_PADDING_H_DP)
        assertEquals(MarbleSpace.ROW_GAP, MarbleStatSpec.COMPACT_PADDING_V_DP)
        assertEquals(MarbleSpace.XS, MarbleStatSpec.GAP_DP)
        assertEquals(MarbleWash.SOFT, MarbleStatSpec.WASH_ALPHA, 0.0001f)

        assertEquals(MarbleSpace.HAIR, MarbleLevelSpec.GAP_DP)
        assertEquals(listOf(4, 7, 10, 13), (0 until MarbleLevelSpec.STEPS).map { MarbleLevelSpec.barHeightDp(it) })
        assertEquals(0, MarbleLevelSpec.levelOf(0f))
        assertEquals(MarbleLevelSpec.STEPS, MarbleLevelSpec.levelOf(1f))
        assertEquals(2, MarbleLevelSpec.levelOf(0.5f))
        // A meter and a tone read the same fraction, so they can never disagree about a route: a
        // fully lit meter is the same verdict as a perfect score.
        assertEquals(
            marbleToneOfScore(100),
            marbleToneOfFraction(MarbleLevelSpec.levelOf(1f).toFloat() / MarbleLevelSpec.STEPS)
        )
        assertEquals(0, MarbleLevelSpec.levelOf(-1f))
        assertEquals(MarbleLevelSpec.STEPS, MarbleLevelSpec.levelOf(3f))

        assertEquals(MarbleRadius.XS, MarbleSkeletonSpec.CORNER_DP)
        assertEquals(MarbleSpace.MD, MarbleSkeletonSpec.LINE_HEIGHT_DP)
        assertTrue(
            "the breath has to be visible but must not blink",
            MarbleSkeletonSpec.BREATH_MIN < MarbleSkeletonSpec.BREATH_MAX
        )
        assertTrue(MarbleSkeletonSpec.BREATH_MIN > MarbleWash.CLEAR)
        assertTrue(MarbleSkeletonSpec.BREATH_MAX < 1f)
    }

    // ------------------------------------------------------------------ one component per role

    @Test
    fun theRetiredDuplicatesNowDelegateToTheKit() {
        assertTrue("the jitter page still draws its own stat tile", "MarbleStatTile(" in main("ui/SettingsJitterPages.kt"))
        assertTrue("the subscriptions page still draws its own stat tile", "MarbleStatTile(" in main("ui/SettingsSystemPages.kt"))
        assertTrue("the servers page still draws its own stat tile", "MarbleStatTile(" in main("ui/ServersScreen.kt"))
        assertTrue("the council still draws its own metric row", "MarbleMetricRow(" in main("ui/SettingsNetworkCouncilPages.kt"))
        assertTrue("the IP page still draws its own skeleton", "MarbleSkeleton(" in main("ui/IpIdentityScreen.kt"))
        assertTrue("the permission dialog still owns a second AlertDialog", "MarbleAlertDialog(" in main("ui/MarblePermissionOnboarding.kt"))
        assertTrue("the core log sheet is still a hand-copy of the kit sheet", "MarbleBottomSheet(" in main("ui/MarbleExpressiveShell.kt"))
        assertTrue("the intake cells are still raw cards", "MarbleCard(" in main("ui/AddServerGrid.kt"))
        assertTrue("the ping bars are not the kit's level meter", "MarbleLevelBars(" in main("ui/ServersScreen.kt"))
    }

    @Test
    fun oneIconButtonDownFromTwo() {
        val surfaces = main("ui/MarbleExpressiveSurfaces.kt")
        assertFalse(
            "a second icon button came back",
            "fun MarbleExpressiveIconButton(" in surfaces
        )
        assertTrue("the morph the button is built from must stay with the polygon family", "fun marbleMorphingShape(" in surfaces)
        assertTrue("the kit must own the one icon button", "internal fun MarbleIconButton(" in main("ui/MarbleComponents.kt"))
    }

    @Test
    fun noRawMaterialControlSurvivesOutsideItsOwner() {
        val forbidden = listOf(
            "AlertDialog", "AssistChip", "Button", "Card", "Checkbox", "CircularProgressIndicator",
            "ElevatedButton", "FilledIconButton", "FilledTonalButton", "FilledTonalIconButton",
            "FilterChip", "HorizontalDivider", "Icon", "IconButton", "LinearProgressIndicator",
            "ListItem", "ModalBottomSheet", "NavigationBar", "OutlinedButton",
            "OutlinedIconButton", "OutlinedTextField", "RadioButton", "SegmentedButton", "Slider",
            "Snackbar", "Switch", "TabRow", "TextField", "TextButton"
        )
        // The kit draws every control; the shell owns the page furniture; the dock draws an optic.
        val owners = setOf(
            "MarbleComponents.kt", "MarbleExpressivePages.kt", "MarbleApp.kt", "MarbleWave.kt",
            "GlassLensIndicator.kt", "LiquidGlassBottomBar.kt", "LiquidLensNavigationBar.kt",
            "MarbleDockBackdrop.kt", "MarbleDockWings.kt", "MarbleLiquidDock.kt",
            "MarbleLumenDock.kt", "NavigationItemView.kt"
        )
        val dir = File("src/main/java/com/marbleng/app/ui")
        var scanned = 0
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            if (file.name in owners) return@forEach
            scanned += 1
            val code = file.readText()
                .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                .split("\n")
                .joinToString("\n") { it.substringBefore("//") }
            forbidden.forEach { name ->
                val raw = Regex("(?<![\\w.])${Regex.escape(name)}\\(").findAll(code)
                    .filter { match ->
                        val at = match.range.first
                        at < 6 || code.substring(at - 6, at) != "Marble"
                    }
                    .toList()
                assertTrue(
                    "${file.name} draws a raw $name ${raw.size} time(s) instead of the kit's",
                    raw.isEmpty()
                )
            }
        }
        assertTrue("the scan found no screens at all", scanned > 30)
    }

    @Test
    fun oneSweepControlAndOneNamedSheetSection() {
        val servers = main("ui/ServersScreen.kt")
        // The header used to offer a second copy of the sweep the sheet already owns.
        assertEquals(
            "the sweep is offered twice again",
            1,
            Regex("""trx\("Measure every server"\)""").findAll(servers).count()
        )
        assertFalse("the retired header copy came back", "Test every server" in servers)
        assertEquals(
            "the sheet's ping section must have one header",
            1,
            Regex("""trx\("Ping controls"\)""").findAll(servers).count()
        )
        // A key with no Persian entry would print English inside the Persian UI.
        assertTrue(
            "the new section header has no lexicon entry",
            "\"Ping controls\" to" in main("ui/MarblePersianLexicon.kt")
        )
    }

    private fun probe(success: Int, latencyMs: Double) = BenchmarkResult(
        profileId = "probe",
        name = "probe",
        success = success,
        latencyMs = latencyMs,
        bytesPerSecond = 0.0,
        score = 0.0
    )

    private fun tls(
        outcome: NetworkCouncil.TlsOutcome,
        certificateOk: Boolean = outcome == NetworkCouncil.TlsOutcome.COMPLETED
    ) = NetworkCouncil.TlsProbe(
        host = "example.com",
        outcome = outcome,
        latencyMs = 60,
        certificateOk = certificateOk
    )
}
