package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MARBLE_SERVER_TILE_PARITY_V212 / MARBLE_HOME_PING_CONTROLS_V212 — the surfaces, not the pixels.
 *
 * Both chapters are about a control that was not one. Neither can be caught by a unit test of a
 * composable, and both can be caught by a test of the source: a server box that stops taking the
 * measured address family, a menu that stops being composed into the grid, or a ping surface that
 * stops taking a tap are all single lines that a refactor can drop without changing any type.
 *
 * What is pinned here is therefore the wiring — which surfaces accept a tap, which verbs they
 * resolve, and that the two server silhouettes share one menu — while the geometry stays the
 * layout tests' business.
 */
class ServerTileParityV212Test {

    @Test
    fun theCompactBoxTakesTheSameFactsAndTheSameMenuAsTheRow() {
        val tiles = sourceFile("src/main/java/com/marbleng/app/ui/MarbleServerTiles.kt")
        assertTrue(
            "the tile no longer has a slot for the measured address family",
            tiles.contains("familyChip: String? = null")
        )
        assertTrue(
            "the tile no longer has a slot for the page's own menu",
            tiles.contains("trailing: (@Composable () -> Unit)? = null")
        )
        assertTrue(
            "the family verdict is drawn, not just accepted",
            tiles.contains("ServerStateChip(trx(familyChip), familyTone)")
        )

        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        val home = sourceFile("src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt")
        // Both layouts that draw tiles answer the IPv4/IPv6 question, because both are the
        // servers list — the preference only chooses the silhouette.
        assertTrue("the Servers grid lost the family chip", ui.contains("familyChip = familyScan?.chip"))
        assertTrue("the Home grid lost the family chip", home.contains("familyChip = familyScan?.chip"))
        // One menu, composed by the layout that owns the actions: two smaller menus is how the
        // grid ended up with fewer verbs than the list.
        assertTrue("the grid no longer composes the server menu", ui.contains("ServersNodeMenu("))
        assertTrue(
            "the menu is no longer shared, so the two layouts can drift apart again",
            ui.contains("internal fun ServersNodeMenu(")
        )
        assertTrue(
            "the family colour is no longer shared between the two silhouettes",
            ui.contains("internal fun familyChipTone(")
        )
    }

    @Test
    fun everyPingSurfaceOnTheConnectionPageIsAControl() {
        val studio = sourceFile("src/main/java/com/marbleng/app/ui/MarbleHomeStudio.kt")
        val home = sourceFile("src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt")

        // The gauge and the deck pill were displays by design in V208. Both take a tap now, and
        // both resolve their verb instead of hard-coding one.
        val meter = studio.substringAfter("fun HomeLivePingMeter(").substringBefore("\ninternal fun ")
        assertTrue("the live-ping gauge is not a control", meter.contains("kineticClickable("))
        assertTrue("the gauge picks its own verb", meter.contains("runHomePingAction(pingAction, actions)"))
        assertTrue(
            "the gauge decides whether it may be tapped by the product's own rule",
            meter.contains("homePingTappable(evidence)")
        )

        val deck = studio.substringAfter("fun HomeShortcutDeck(").substringBefore("\ninternal fun ")
        assertTrue("the shortcut pill is not a control", deck.contains("kineticClickable("))
        assertTrue("the pill picks its own verb", deck.contains("runHomePingAction(pingAction, actions)"))

        val stats = home.substringAfter("fun HomeSessionStats(").substringBefore("\nprivate fun ")
        assertTrue("the latency cell is not a control", stats.contains("kineticClickable("))
        assertTrue(
            "the latency cell picks its own verb",
            stats.contains("runHomePingAction(evidence.pingGaugeAction, actions)")
        )
        // And it says out loud what pressing it does — a control whose label promises something
        // else is the defect this chapter exists to remove.
        assertTrue(stats.contains("gaugeSpoken"))
    }

    @Test
    fun oneMappingTurnsAStoredVerbIntoTheActionItNames() {
        val home = sourceFile("src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt")
        val mapping = home.substringAfter("fun runHomePingAction(").substringBefore("\ninternal fun ")
        // Four verbs, four destinations: no branch may be empty, or a setting can still point at
        // a button that does nothing.
        assertEquals(4, Regex("HomePingAction\\.\\w+ ->").findAll(mapping).count())
        assertTrue(mapping.contains("onPingRoute()"))
        assertTrue(mapping.contains("onPingGroup()"))
        assertTrue(mapping.contains("onPingLibrary()"))
        assertTrue(mapping.contains("onTests()"))
    }

    @Test
    fun settingsOwnsTheVerbOfEverySurface() {
        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        assertTrue("the settings page lost the ping controls", ui.contains("HomePingControlsSettings(repo)"))
        // Three surfaces, three preferences: a chooser that writes one field cannot configure
        // the page it claims to configure.
        assertTrue(ui.contains("homePingGaugeAction = action.id"))
        assertTrue(ui.contains("homePingChipAction = action.id"))
        assertTrue(ui.contains("homePingHeaderAction = action.id"))
        // The chooser's vocabulary is the controls' own, not a second list that can drift.
        assertTrue(ui.contains("HomePingAction.entries.forEach"))
        assertTrue(ui.contains("homePingActionLabel(action)"))
    }

    @Test
    fun theRoutingSettingsDrawTheSetOfDatabasesAndThePrecisionDial() {
        val ui = sourceFile("src/main/java/com/marbleng/app/ui/Aether2026.kt")
        assertTrue("the routing page lost the source set", ui.contains("GeoSourceSetControls(repo)"))
        assertTrue("the routing page lost the precision dial", ui.contains("GeoPrecisionChoice(repo)"))
        assertTrue(ui.contains("GeoPrecision.entries.forEach"))
        // Picking a primary re-orders; it never replaces the set — that was the defect.
        assertTrue(ui.contains("repo.applyGeoAssetSource(spec.id)"))
        assertTrue(ui.contains("repo.toggleGeoAssetSource(spec.id)"))
        assertTrue(ui.contains("repo.setGeoPrecision(level)"))
    }

    private fun sourceFile(relative: String): String {
        val candidate = listOf(File(relative), File("../$relative"), File("app/$relative"))
            .firstOrNull { it.isFile }
        requireNotNull(candidate) { "cannot locate $relative from ${File(".").absolutePath}" }
        return candidate.readText()
    }
}
