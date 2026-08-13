package com.marbleng.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** MarbleNG ships exactly two themes: a neon Dark mode and a crisp Light mode, both energetic. */
enum class AppTheme { DARK, LIGHT }

fun parseAppTheme(id: String): AppTheme = if (id.equals("light", ignoreCase = true)) AppTheme.LIGHT else AppTheme.DARK

private data class AetherPalette(
    val void: Color,
    val voidElevated: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassBorderSoft: Color,
    val amethyst: Color,
    val amethystBright: Color,
    val cyan: Color,
    val cyanBright: Color,
    val slate: Color,
    val slateBright: Color,
    val danger: Color,
    val dangerBright: Color,
    val emerald: Color,
    val amber: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color
)

private val DarkPalette = AetherPalette(
    void = Color(0xFF000000),
    voidElevated = Color(0xFF06070A),
    glass = Color(0xCC0A0D13),
    glassStrong = Color(0xE6101520),
    glassBorder = Color(0xFF253344),
    glassBorderSoft = Color(0xFF151D28),
    amethyst = Color(0xFFB15CFF),
    amethystBright = Color(0xFFD09BFF),
    cyan = Color(0xFF20F6FF),
    cyanBright = Color(0xFF9BFBFF),
    slate = Color(0xFF3B4150),
    slateBright = Color(0xFF667085),
    danger = Color(0xFFFF617D),
    dangerBright = Color(0xFFFF8FA6),
    emerald = Color(0xFF45FFB1),
    amber = Color(0xFFFFD35A),
    ink = Color(0xFFF7FAFF),
    inkMuted = Color(0xFFABB7C8),
    inkFaint = Color(0xFF657184)
)

private val LightPalette = AetherPalette(
    void = Color(0xFFF6F7FB),
    voidElevated = Color(0xFFFFFFFF),
    glass = Color(0xFFFFFFFF),
    glassStrong = Color(0xFFEEF0F8),
    glassBorder = Color(0xFFD7DAE8),
    glassBorderSoft = Color(0xFFE6E8F2),
    amethyst = Color(0xFF7C3AED),
    amethystBright = Color(0xFF6425D0),
    cyan = Color(0xFF0891B2),
    cyanBright = Color(0xFF0E7490),
    slate = Color(0xFFCBD1DC),
    slateBright = Color(0xFF8991A3),
    danger = Color(0xFFE11D48),
    dangerBright = Color(0xFFBE123C),
    emerald = Color(0xFF059669),
    amber = Color(0xFFD97706),
    ink = Color(0xFF14161F),
    inkMuted = Color(0xFF4B5163),
    inkFaint = Color(0xFF767C8C)
)

private val LocalAetherPalette = staticCompositionLocalOf { DarkPalette }

/** Semantic color constants used across the app; resolve against whichever theme is active. */
object Aether {
    val Void: Color @Composable get() = LocalAetherPalette.current.void
    val VoidElevated: Color @Composable get() = LocalAetherPalette.current.voidElevated
    val Glass: Color @Composable get() = LocalAetherPalette.current.glass
    val GlassStrong: Color @Composable get() = LocalAetherPalette.current.glassStrong
    val GlassBorder: Color @Composable get() = LocalAetherPalette.current.glassBorder
    val GlassBorderSoft: Color @Composable get() = LocalAetherPalette.current.glassBorderSoft
    val Amethyst: Color @Composable get() = LocalAetherPalette.current.amethyst
    val AmethystBright: Color @Composable get() = LocalAetherPalette.current.amethystBright
    val Cyan: Color @Composable get() = LocalAetherPalette.current.cyan
    val CyanBright: Color @Composable get() = LocalAetherPalette.current.cyanBright
    val Slate: Color @Composable get() = LocalAetherPalette.current.slate
    val SlateBright: Color @Composable get() = LocalAetherPalette.current.slateBright
    val Danger: Color @Composable get() = LocalAetherPalette.current.danger
    val DangerBright: Color @Composable get() = LocalAetherPalette.current.dangerBright
    val Emerald: Color @Composable get() = LocalAetherPalette.current.emerald
    val Amber: Color @Composable get() = LocalAetherPalette.current.amber
    val Ink: Color @Composable get() = LocalAetherPalette.current.ink
    val InkMuted: Color @Composable get() = LocalAetherPalette.current.inkMuted
    val InkFaint: Color @Composable get() = LocalAetherPalette.current.inkFaint
}

private val AetherFontFamily = FontFamily.Default

val AetherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.05.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.45.sp
    )
)

@Composable
fun AetherFlowTheme(themeId: String = "dark", content: @Composable () -> Unit) {
    val palette = if (parseAppTheme(themeId) == AppTheme.LIGHT) LightPalette else DarkPalette
    val isLight = palette === LightPalette

    val scheme = if (isLight) {
        lightColorScheme(
            primary = palette.amethyst,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = palette.amethyst.copy(alpha = 0.12f),
            onPrimaryContainer = palette.ink,
            secondary = palette.cyan,
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = palette.cyan.copy(alpha = 0.12f),
            onSecondaryContainer = palette.ink,
            tertiary = palette.amethystBright,
            background = palette.void,
            onBackground = palette.ink,
            surface = palette.voidElevated,
            onSurface = palette.ink,
            surfaceVariant = palette.glassStrong,
            onSurfaceVariant = palette.inkMuted,
            error = palette.danger,
            outline = palette.glassBorder
        )
    } else {
        darkColorScheme(
            primary = palette.amethyst,
            onPrimary = Color(0xFF06070A),
            primaryContainer = palette.amethyst.copy(alpha = 0.16f),
            onPrimaryContainer = palette.ink,
            secondary = palette.cyan,
            onSecondary = Color(0xFF06070A),
            secondaryContainer = palette.cyan.copy(alpha = 0.14f),
            onSecondaryContainer = palette.ink,
            tertiary = palette.amethystBright,
            background = palette.void,
            onBackground = palette.ink,
            surface = palette.voidElevated,
            onSurface = palette.ink,
            surfaceVariant = palette.glassStrong,
            onSurfaceVariant = palette.inkMuted,
            error = palette.danger,
            outline = palette.glassBorder
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = isLight
            insets.isAppearanceLightNavigationBars = isLight
            window.statusBarColor = palette.void.toArgb()
            window.navigationBarColor = palette.void.toArgb()
        }
    }

    CompositionLocalProvider(LocalAetherPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AetherTypography, content = content)
    }
}
