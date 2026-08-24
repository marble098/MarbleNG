package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_THEME_V34
// MARBLE_SOLID_WHITE_THEME_V35

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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

/** Marble White — calm solid surfaces with vivid, state-driven accents. */
enum class AppTheme { SYSTEM, DARK, LIGHT }

fun parseAppTheme(id: String): AppTheme = when {
    id.equals("dark", true) -> AppTheme.DARK
    id.equals("system", true) -> AppTheme.SYSTEM
    else -> AppTheme.LIGHT
}

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

/*
 * Light surfaces are deliberately opaque. The previous translucent stack produced visible
 * rectangular compositing bands on several Android GPUs and weakened the information hierarchy.
 * Depth now comes from spacing, a single soft outline and state colour—not nested glass layers.
 */
private val LightPalette = AetherPalette(
    void = Color(0xFFF7F9FC),
    voidElevated = Color(0xFFFFFFFF),
    glass = Color(0xFFFFFFFF),
    glassStrong = Color(0xFFF2F5FA),
    glassBorder = Color(0xFFE8EDF4),
    glassBorderSoft = Color(0xFFD9E1EC),
    amethyst = Color(0xFF7C3AED),
    amethystBright = Color(0xFF9B5CFF),
    cyan = Color(0xFF246BFD),
    cyanBright = Color(0xFF00A6FB),
    slate = Color(0xFFE7ECF4),
    slateBright = Color(0xFF60728C),
    danger = Color(0xFFEF3E5B),
    dangerBright = Color(0xFFFF5A70),
    emerald = Color(0xFF00A67E),
    amber = Color(0xFFF08A00),
    ink = Color(0xFF102033),
    inkMuted = Color(0xFF4C6078),
    inkFaint = Color(0xFF7B8CA2)
)

/* Dark remains an explicit accessibility/user choice and mirrors the same formal color identity. */
private val DarkPalette = AetherPalette(
    void = Color(0xFF080E19),
    voidElevated = Color(0xFF182232),
    glass = Color(0xFF202C40),
    glassStrong = Color(0xFF243044),
    glassBorder = Color(0xFF425570),
    glassBorderSoft = Color(0xFF33445E),
    amethyst = Color(0xFF9B85FF),
    amethystBright = Color(0xFFB8A9FF),
    cyan = Color(0xFF5B9DFF),
    cyanBright = Color(0xFF79B6FF),
    slate = Color(0xFF29364B),
    slateBright = Color(0xFF94A6C2),
    danger = Color(0xFFFF7088),
    dangerBright = Color(0xFFFF94A6),
    emerald = Color(0xFF55D6B7),
    amber = Color(0xFFF2B35D),
    ink = Color(0xFFF7FAFF),
    inkMuted = Color(0xFFC0CCDC),
    inkFaint = Color(0xFF8FA0B8)
)

private val LocalAetherPalette = staticCompositionLocalOf { LightPalette }

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

private val ProductSans = FontFamily.SansSerif

val AetherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-.72).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-.34).sp
    ),
    titleMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-.08).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = .10.sp
    )
)

@Composable
fun AetherFlowTheme(
    themeId: String = "light",
    content: @Composable () -> Unit
) {
    val requested = parseAppTheme(themeId)
    val light = when (requested) {
        AppTheme.LIGHT -> true
        AppTheme.DARK -> false
        AppTheme.SYSTEM -> !isSystemInDarkTheme()
    }
    val palette = if (light) LightPalette else DarkPalette

    val scheme = if (light) {
        lightColorScheme(
            primary = palette.cyan,
            onPrimary = Color.White,
            primaryContainer = palette.cyan.copy(alpha = .10f),
            onPrimaryContainer = palette.ink,
            secondary = palette.emerald,
            onSecondary = Color.White,
            secondaryContainer = palette.emerald.copy(alpha = .10f),
            onSecondaryContainer = palette.ink,
            tertiary = palette.amethyst,
            background = palette.void,
            onBackground = palette.ink,
            surface = palette.voidElevated,
            onSurface = palette.ink,
            surfaceVariant = palette.glassStrong,
            onSurfaceVariant = palette.inkMuted,
            surfaceTint = Color.Transparent,
            error = palette.danger,
            outline = palette.glassBorderSoft,
            outlineVariant = palette.glassBorderSoft.copy(alpha = .55f)
        )
    } else {
        darkColorScheme(
            primary = palette.cyan,
            onPrimary = Color(0xFF061329),
            primaryContainer = palette.cyan.copy(alpha = .14f),
            onPrimaryContainer = palette.ink,
            secondary = palette.emerald,
            onSecondary = Color(0xFF061711),
            secondaryContainer = palette.emerald.copy(alpha = .12f),
            onSecondaryContainer = palette.ink,
            tertiary = palette.amethyst,
            background = palette.void,
            onBackground = palette.ink,
            surface = palette.voidElevated,
            onSurface = palette.ink,
            surfaceVariant = palette.glassStrong,
            onSurfaceVariant = palette.inkMuted,
            surfaceTint = Color.Transparent,
            error = palette.danger,
            outline = palette.glassBorder,
            outlineVariant = palette.glassBorderSoft
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
            window.statusBarColor = palette.void.toArgb()
            window.navigationBarColor = palette.void.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(LocalAetherPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AetherTypography) {
            ProvideMarbleMotion(content)
        }
    }
}
