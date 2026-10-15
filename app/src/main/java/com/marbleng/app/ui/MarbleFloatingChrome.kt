package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

fun marbleLuminance(color: Color): Float = marbleLuminanceArgb(color.toArgb())

fun marbleContrastRatio(foreground: Color, background: Color): Float =
    marbleContrastRatioArgb(foreground.toArgb(), background.toArgb())

fun marbleReadableOn(
    accent: Color,
    background: Color,
    minimumRatio: Float = 4.5f
): Color = Color(marbleReadableOnArgb(accent.toArgb(), background.toArgb(), minimumRatio))

fun marbleOnColor(
    accent: Color,
    lightInk: Color = Color.White,
    darkInk: Color = Color(0xFF000033),
    minimumRatio: Float = 3.0f
): Color = Color(
    marbleOnColorArgb(accent.toArgb(), lightInk.toArgb(), darkInk.toArgb(), minimumRatio)
)

fun marblePillFill(accent: Color, surface: Color, fillAlpha: Float = 0.22f): Color =
    accent.copy(alpha = fillAlpha).compositeOver(surface)

fun marblePillRim(accent: Color, surface: Color, rimAlpha: Float = 0.42f): Color =
    marbleReadableOn(accent, surface, 1.6f).copy(alpha = rimAlpha).compositeOver(surface)

data class MarbleFloatChrome(
    val surface: Color,
    val border: Color,
    val shadow: Color,

    val actions: MarbleFloatActionTones,
    val inkLight: Color,
    val inkDark: Color
) {
    fun inkOn(tone: Color): Color = marbleOnColor(tone, inkLight, inkDark)
}

@Composable
fun rememberMarbleFloatChrome(): MarbleFloatChrome = MarbleFloatChrome(
    surface = marbleNativeContainerHigh(),
    border = marbleNativeOutline(),
    shadow = Aether.FloatShadow,
    actions = Aether.FloatActions,
    inkLight = Aether.FloatInkLight,
    inkDark = Aether.FloatInkDark
)

data class MarbleFloatActionTones(
    val connect: Color,
    val securing: Color,
    val stop: Color,
    val measure: Color
)

data class MarbleFloatActionSet(
    val connect: Int,
    val securing: Int,
    val stop: Int,
    val measure: Int,
    val inkLight: Int,
    val inkDark: Int
) {
    fun inkOn(tone: Int): Int = marbleOnColorArgb(tone, inkLight, inkDark)

    fun tones(): MarbleFloatActionTones = MarbleFloatActionTones(
        connect = Color(connect),
        securing = Color(securing),
        stop = Color(stop),
        measure = Color(measure)
    )
}

object MarbleFloatActions {
    val Light = MarbleFloatActionSet(
        connect = 0xFF0066CC.toInt(),
        securing = 0xFF001144.toInt(),
        stop = 0xFFE23D5B.toInt(),
        measure = 0xFF009A74.toInt(),
        inkLight = 0xFFFFFFFF.toInt(),
        inkDark = 0xFF000033.toInt()
    )

    val Dark = MarbleFloatActionSet(
        connect = 0xFF3399FF.toInt(),
        securing = 0xFF0066CC.toInt(),
        stop = 0xFFFF718B.toInt(),
        measure = 0xFF55D7B4.toInt(),
        inkLight = 0xFFF0F8FF.toInt(),
        inkDark = 0xFF000033.toInt()
    )

    fun dynamic(
        primary: Int,
        secondary: Int,
        tertiary: Int,
        error: Int,
        onPrimary: Int,
        onPrimaryContainer: Int
    ): MarbleFloatActionSet {
        val (light, dark) = if (marbleLuminanceArgb(onPrimary) >= marbleLuminanceArgb(onPrimaryContainer)) {
            onPrimary to onPrimaryContainer
        } else {
            onPrimaryContainer to onPrimary
        }
        return MarbleFloatActionSet(
            connect = primary,
            securing = secondary,
            stop = error,
            measure = tertiary,
            inkLight = light,
            inkDark = dark
        )
    }
}
