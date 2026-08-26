package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_THEME_V34
// MARBLE_SOLID_WHITE_THEME_V35
// MARBLE_REFINED_PRODUCT_UI_V52

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
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
    // Neutral surfaces carry hierarchy; accents are reserved for state and action.
    void = Color(0xFFF7F9FC),
    voidElevated = Color(0xFFFFFFFF),
    glass = Color(0xFFFFFFFF),
    glassStrong = Color(0xFFF1F5FA),
    glassBorder = Color(0xFFE5EBF2),
    glassBorderSoft = Color(0xFFDCE4EE),
    amethyst = Color(0xFF6F55D9),
    amethystBright = Color(0xFF856EE5),
    cyan = Color(0xFF1769E0),
    cyanBright = Color(0xFF0B88EB),
    slate = Color(0xFFE8EDF4),
    slateBright = Color(0xFF5D7088),
    danger = Color(0xFFD93D58),
    dangerBright = Color(0xFFF05A70),
    emerald = Color(0xFF008F70),
    amber = Color(0xFFC97800),
    ink = Color(0xFF142235),
    inkMuted = Color(0xFF52657A),
    inkFaint = Color(0xFF7B8CA2)
)

/* Dark remains an explicit accessibility/user choice and mirrors the same formal color identity. */
private val DarkPalette = AetherPalette(
    void = Color(0xFF0A111C),
    voidElevated = Color(0xFF121C2A),
    glass = Color(0xFF172334),
    glassStrong = Color(0xFF1D2A3D),
    glassBorder = Color(0xFF34465F),
    glassBorderSoft = Color(0xFF2A3A50),
    amethyst = Color(0xFFA896FF),
    amethystBright = Color(0xFFC0B3FF),
    cyan = Color(0xFF72A8FF),
    cyanBright = Color(0xFF8AB9FF),
    slate = Color(0xFF263449),
    slateBright = Color(0xFF9AAAC0),
    danger = Color(0xFFFF728A),
    dangerBright = Color(0xFFFF98A9),
    emerald = Color(0xFF58D2B4),
    amber = Color(0xFFF1B768),
    ink = Color(0xFFF6F9FD),
    inkMuted = Color(0xFFC2CDDC),
    inkFaint = Color(0xFF91A0B5)
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
    headlineLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 35.sp,
        letterSpacing = (-.42).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-.34).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-.20).sp
    ),
    titleLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-.16).sp
    ),
    titleMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-.08).sp
    ),
    titleSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
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
    labelMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = .06.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = .10.sp
    )
)

val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
        MaterialTheme(
            colorScheme = scheme,
            typography = AetherTypography,
            shapes = AetherShapes
        ) {
            ProvideMarbleMotion(content)
        }
    }
}
