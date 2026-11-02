package com.marbleng.app.ui

import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MARBLE_QUIET_TABS_V263 / MARBLE_READABLE_SURFACES_V263 — the two complaints of this pass, pinned
 * where a screenshot cannot argue with them.
 *
 * The first is a behaviour: a tap on the bottom bar must never make a tab's name unfold beside its
 * glyph. The second is a layout rule: an official `MaterialShapes` polygon is a *badge*, never the
 * clip of a container that has to be read — its concave lobes were cutting the ends off IP
 * addresses and About core tags.
 *
 * Both are partly source invariants (a composable that is not there cannot animate, and a clip that
 * is not there cannot cut a letter), so this is a host-JVM test with no device and no inflation: it
 * reads the very files the compiler will read. The Robolectric tests in `LumenDockV256Test` and
 * `FloatingDockV258Test` prove the same rule on an inflated tree.
 */
class ReadableSurfacesV263Test {

    private fun main(path: String): String =
        File("src/main/java/com/marbleng/app/$path").readText()

    @Test
    fun aTabIsOneSteadyGlyphAndItsNameIsAnAccessibleLabel() {
        val studio = main("model/DockStudio.kt")
        val dock = main("ui/MarbleLumenDock.kt")
        val item = main("ui/NavigationItemView.kt")

        // The whole caption mechanism is gone: no two-second hold, no per-rung "keeps its word"
        // policy, no transient state, no spring-animated slot weight and no AnimatedContent.
        assertFalse("the caption hold came back", "EXPANDED_HOLD_MS" in studio)
        assertFalse("the rung caption policy came back", "showsCaptionsAlways" in studio)
        assertFalse("the transient label came back", "labelOpen" in dock)
        assertFalse("the two-second timer came back", "LaunchedEffect(selection)" in dock)
        assertFalse("the expanding slot came back", "animateFloatAsState(" in dock)
        assertFalse("the caption transition came back", "AnimatedContent(" in item)
        assertFalse("the collapsed label placeholder came back", "Modifier.size(0.dp)" in item)

        // What replaced it: equal slots and an accessible name on every tab.
        assertTrue("slots must keep a fixed weight", "Modifier.weight(1f)" in dock)
        assertTrue(
            "a screen reader must still hear the tab's name",
            "contentDescription = tab.label" in item
        )
        assertTrue(
            "the notification count must stay visible",
            "faDigits(LiquidDockSpec.badgeLabel(count))" in item
        )
    }

    @Test
    fun theLadderDescribesItsSizeNotAWordThatOpens() {
        assertEquals("Icons only, tightest capsule", DockStudio.ladderHint(DockSize.SMALL))
        assertEquals("Bigger glyphs, a roomier capsule", DockStudio.ladderHint(DockSize.MEDIUM))
        assertEquals("Largest glyphs, the widest capsule", DockStudio.ladderHint(DockSize.LARGE))

        val settings = main("ui/SettingsAppearancePages.kt")
        val lexicon = main("ui/MarblePersianLexicon.kt")
        assertFalse(
            "Settings still promises an opening caption",
            "Classic opens the selected word for a moment" in settings
        )
        assertTrue(
            "Settings must say the bar is icon-only",
            "Every size is icon-only; tapping a tab never opens its name beside the glyph." in settings
        )
        assertTrue(
            "the new footer needs its Persian sentence",
            "همهٔ اندازه" in lexicon
        )
    }

    @Test
    fun aPolygonIsABadgeNeverAReadingContainer() {
        val surfaces = main("ui/MarbleExpressiveSurfaces.kt")
        val ip = main("ui/IpIdentityScreen.kt")
        val about = main("ui/AboutScreen.kt")

        // One shape token owns every surface that holds a sentence.
        assertTrue(
            "the reading shape must exist",
            "internal val MarbleReadingShape: Shape" in surfaces
        )
        assertTrue(
            "the reading shape must use the theme's card corner",
            "RoundedCornerShape(MarbleSpec.CARD_CORNER_DP.dp)" in surfaces
        )
        assertTrue("the IP stack tiles must read it", ".clip(MarbleReadingShape)" in ip)
        assertTrue("the About core tiles must read it", ".clip(MarbleReadingShape)" in about)

        // The two container clips that cut characters off are gone; the small badges remain.
        assertFalse(
            "an IP tile is clipped to a polygon again",
            ".clip(marbleExpressiveShape(motif))\n            .background(colors.surfaceContainerLow)" in ip
        )
        assertFalse(
            "an About tile is clipped to a polygon again",
            ".clip(marbleExpressiveShape(core.motif))\n            .background(colors.surfaceContainerLow)" in about
        )
        assertTrue(
            "the expressive badge must survive on IP",
            ".clip(marbleExpressiveShape(motif))" in ip
        )
        assertTrue(
            "the expressive badge must survive on About",
            ".clip(marbleExpressiveShape(core.motif))" in about
        )
    }

    @Test
    fun theFactsOnIpAndAboutArePrintedInFull() {
        val ip = main("ui/IpIdentityScreen.kt")
        val about = main("ui/AboutScreen.kt")

        // The address is the one fact the IP page exists to print. A long IPv6 steps down from
        // 32sp and wraps; nothing is ellipsized at the headline any more.
        assertTrue(
            "a long address must step down instead of being cut",
            "val longAddress = shown.length > 18" in ip
        )
        assertTrue("the headline keeps its 32sp rung", "if (longAddress) 20.sp else 32.sp" in ip)
        assertTrue(
            "the stack tile's value must wrap",
            "text = shown,\n            color = if (value.isBlank())" in ip
        )
        assertFalse(
            "an IP fact is still capped at three lines",
            "maxLines = 3,\n            overflow = TextOverflow.Ellipsis" in ip
        )

        // About's build line and core tags are facts about the binary, not decoration.
        assertTrue(
            "the build line must stay a test-tagged fact",
            "about-build-line" in about
        )
        assertFalse(
            "the build line is still cut at two lines",
            "maxLines = 2,\n                    modifier = Modifier.testTag(\"about-build-line\")" in about
        )
        assertFalse(
            "a core tag is still ellipsized",
            "fontFamily = FontFamily.Monospace),\n                maxLines = 1" in about
        )
    }
}
