package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_FLOATING_ACTIONS_V210 — the guarantee behind "the floating buttons look different in
 * every theme, and stay coordinated".
 *
 * The old split control painted its faces with whatever colour the call site reached for, and in
 * the dynamic theme that colour was a hard-coded brand red sitting on a wallpaper-coloured page.
 * Nothing could have caught it, because nothing measured a floating action's colour: not whether
 * the two halves of one control are distinguishable, not whether the glyph on either half is
 * readable, and not whether two themes actually differ.
 *
 * Those are three testable questions, so they are three tests. Every set is packed ARGB, so the
 * whole thing runs on a plain JVM with no Compose runtime in sight.
 */
class MarbleFloatingActionsV210Test {

    // A Material You scheme, in the shape the theme reads it: a pastel primary is the case the
    // old design failed, because a pastel at 100 % under a pastel wash is unreadable.
    private val dynamicLight = MarbleFloatActions.dynamic(
        primary = 0xFFA8C7FA.toInt(),
        secondary = 0xFFBCC7DC.toInt(),
        tertiary = 0xFFEFB8C8.toInt(),
        error = 0xFFB3261E.toInt(),
        onPrimary = 0xFF0B2F5B.toInt(),
        onPrimaryContainer = 0xFFD6E3FF.toInt()
    )

    // The same wallpaper in its dark form, where `onPrimary` is the DARK one of the pair — the
    // ordering the palette has to repair rather than assume.
    private val dynamicDark = MarbleFloatActions.dynamic(
        primary = 0xFFB8C8FF.toInt(),
        secondary = 0xFFC2C6DC.toInt(),
        tertiary = 0xFFEFC0D1.toInt(),
        error = 0xFFFFB4AB.toInt(),
        onPrimary = 0xFF1A2B5C.toInt(),
        onPrimaryContainer = 0xFFDCE2FF.toInt()
    )

    private val themes = listOf(
        "Daylight" to MarbleFloatActions.Light,
        "Pure black" to MarbleFloatActions.Dark,
        "Wallpaper (light)" to dynamicLight,
        "Wallpaper (dark)" to dynamicDark
    )

    private fun MarbleFloatActionSet.roles(): List<Pair<String, Int>> = listOf(
        "connect" to connect,
        "securing" to securing,
        "stop" to stop,
        "measure" to measure
    )

    @Test
    fun `the four verbs of one control are four different colours`() {
        // Two discs under one thumb that happen to resolve to the same pastel are two discs the
        // user cannot tell apart, which is the whole defect this token set exists to prevent.
        for ((name, set) in themes) {
            val roles = set.roles()
            for (i in roles.indices) {
                for (j in i + 1 until roles.size) {
                    assertTrue(
                        "$name: ${roles[i].first} and ${roles[j].first} are the same colour",
                        roles[i].second != roles[j].second
                    )
                }
            }
        }
    }

    @Test
    fun `every theme answers all four verbs differently from every other theme`() {
        // The report was "the floating buttons must show different colours in different themes".
        // Four roles, three palettes, twelve chances to accidentally share a value.
        val brandLight = MarbleFloatActions.Light.roles().toMap()
        val brandDark = MarbleFloatActions.Dark.roles().toMap()
        for (role in brandLight.keys) {
            assertTrue(
                "Daylight and Pure black share the $role hue",
                brandLight[role] != brandDark[role]
            )
            assertTrue(
                "Daylight and the wallpaper palette share the $role hue",
                brandLight[role] != dynamicLight.roles().toMap()[role]
            )
        }
    }

    @Test
    fun `a glyph on any action clears the three to one graphical floor in every theme`() {
        for ((name, set) in themes) {
            for ((role, tone) in set.roles()) {
                val ink = set.inkOn(tone)
                val ratio = marbleContrastRatioArgb(ink, tone)
                assertTrue(
                    "$name: the $role glyph reaches only $ratio:1 on #${hex(tone)}",
                    ratio >= 3.0f
                )
            }
        }
    }

    @Test
    fun `a pastel disc takes the dark ink and a deep disc takes the light one`() {
        // The V201 regression, stated for the V210 tokens: the Pure black theme's stop disc is a
        // light rose (#FF718B) and its connect disc a bright blue (#3399FF). Near-white ink on
        // either scores 2.45:1 and 2.74:1 — both under the 3:1 floor for graphical objects — so
        // the scored ink on both is the navy one. Assuming white is what put invisible pause
        // bars on a light-rose disc.
        assertEquals(
            MarbleFloatActions.Dark.inkDark,
            MarbleFloatActions.Dark.inkOn(MarbleFloatActions.Dark.stop)
        )
        assertEquals(
            MarbleFloatActions.Dark.inkDark,
            MarbleFloatActions.Dark.inkOn(MarbleFloatActions.Dark.connect)
        )
        // The opposite case, so the test cannot pass by "always the dark ink": Daylight's connect
        // disc is a deep electric blue, and the same white ink scores 5.57:1 on it.
        assertEquals(
            MarbleFloatActions.Light.inkLight,
            MarbleFloatActions.Light.inkOn(MarbleFloatActions.Light.connect)
        )
    }

    @Test
    fun `both ink candidates really are in play in a brand palette`() {
        // The scoring has to be a choice, not a constant. If every disc of a palette came back
        // with the same ink, `inkOn` would be a `val` — and the day a palette ships a pale
        // action tone it would ship an invisible glyph with it.
        for ((name, set) in listOf("Daylight" to MarbleFloatActions.Light, "Pure black" to MarbleFloatActions.Dark)) {
            val chosen = set.roles().map { (_, tone) -> set.inkOn(tone) }.toSet()
            val report = set.roles().joinToString { (role, tone) ->
                "$role ${if (set.inkOn(tone) == set.inkDark) "dark" else "light"}"
            }
            assertTrue("$name: no disc takes the light ink ($report)", set.inkLight in chosen)
            assertTrue("$name: no disc takes the dark ink ($report)", set.inkDark in chosen)
        }
    }

    @Test
    fun `a wallpaper palette orders its own ink pair by luminance`() {
        // Material You guarantees `onPrimary` reads on `primary`, not that `onPrimary` is the
        // light one — in a dark scheme it is the dark one. The pair is ordered here, so
        // "inkLight" means what it says in both modes.
        for ((name, set) in listOf("light" to dynamicLight, "dark" to dynamicDark)) {
            assertTrue(
                "$name: inkLight must be the brighter candidate",
                marbleLuminanceArgb(set.inkLight) > marbleLuminanceArgb(set.inkDark)
            )
        }
    }

    @Test
    fun `the wallpaper palette keeps its own roles untouched`() {
        // Identity matters: a wallpaper theme that quietly re-tinted the phone's primary would
        // look like every other theme with a blue filter on it.
        assertEquals(0xFFA8C7FA.toInt(), dynamicLight.connect)
        assertEquals(0xFFBCC7DC.toInt(), dynamicLight.securing)
        assertEquals(0xFFEFB8C8.toInt(), dynamicLight.measure)
        // "Stop" stays the scheme's error, never a hue borrowed from the wallpaper's decoration.
        assertEquals(0xFFB3261E.toInt(), dynamicLight.stop)
    }

    private fun hex(argb: Int): String = Integer.toHexString(argb).uppercase()
}
