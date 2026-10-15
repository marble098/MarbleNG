package com.marbleng.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarbleFloatingActionsV210Test {
    private val dynamicLight = MarbleFloatActions.dynamic(
        primary = 0xFFA8C7FA.toInt(),
        secondary = 0xFFBCC7DC.toInt(),
        tertiary = 0xFFEFB8C8.toInt(),
        error = 0xFFB3261E.toInt(),
        onPrimary = 0xFF0B2F5B.toInt(),
        onPrimaryContainer = 0xFFD6E3FF.toInt()
    )

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
        assertEquals(
            MarbleFloatActions.Dark.inkDark,
            MarbleFloatActions.Dark.inkOn(MarbleFloatActions.Dark.stop)
        )
        assertEquals(
            MarbleFloatActions.Dark.inkDark,
            MarbleFloatActions.Dark.inkOn(MarbleFloatActions.Dark.connect)
        )

        assertEquals(
            MarbleFloatActions.Light.inkLight,
            MarbleFloatActions.Light.inkOn(MarbleFloatActions.Light.connect)
        )
    }

    @Test
    fun `both ink candidates really are in play in a brand palette`() {
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
        for ((name, set) in listOf("light" to dynamicLight, "dark" to dynamicDark)) {
            assertTrue(
                "$name: inkLight must be the brighter candidate",
                marbleLuminanceArgb(set.inkLight) > marbleLuminanceArgb(set.inkDark)
            )
        }
    }

    @Test
    fun `the wallpaper palette keeps its own roles untouched`() {
        assertEquals(0xFFA8C7FA.toInt(), dynamicLight.connect)
        assertEquals(0xFFBCC7DC.toInt(), dynamicLight.securing)
        assertEquals(0xFFEFB8C8.toInt(), dynamicLight.measure)

        assertEquals(0xFFB3261E.toInt(), dynamicLight.stop)
    }

    private fun hex(argb: Int): String = Integer.toHexString(argb).uppercase()
}
