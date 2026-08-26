package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_THEME_V34
// MARBLE_SOLID_WHITE_THEME_V35
// MARBLE_REFINED_PRODUCT_UI_V52
// MARBLE_M3_EXPRESSIVE_THEME_V53
// MARBLE_PRISM_THEME_V54

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
    void = Color(0xFFF4F7FC),
    voidElevated = Color(0xFFFFFFFF),
    glass = Color(0xFFFBFCFF),
    glassStrong = Color(0xFFEEF3FA),
    glassBorder = Color(0xFFD4DFED),
    glassBorderSoft = Color(0xFFE1E8F2),
    amethyst = Color(0xFF7556F5),
    amethystBright = Color(0xFF927BFF),
    cyan = Color(0xFF0C6FFF),
    cyanBright = Color(0xFF2B8CFF),
    slate = Color(0xFFE8EEF7),
    slateBright = Color(0xFF5A6C85),
    danger = Color(0xFFE23D5B),
    dangerBright = Color(0xFFF26079),
    emerald = Color(0xFF009A74),
    amber = Color(0xFFD98200),
    ink = Color(0xFF0D1C31),
    inkMuted = Color(0xFF50647E),
    inkFaint = Color(0xFF7B8DA5)
)

/* Dark remains an explicit accessibility/user choice and mirrors the same formal color identity. */
private val DarkPalette = AetherPalette(
    void = Color(0xFF060B13),
    voidElevated = Color(0xFF0D1624),
    glass = Color(0xFF111D2D),
    glassStrong = Color(0xFF152338),
    glassBorder = Color(0xFF2E415D),
    glassBorderSoft = Color(0xFF22334A),
    amethyst = Color(0xFFA58CFF),
    amethystBright = Color(0xFFC0AEFF),
    cyan = Color(0xFF6DA8FF),
    cyanBright = Color(0xFF8FC0FF),
    slate = Color(0xFF26364D),
    slateBright = Color(0xFFA6B6CC),
    danger = Color(0xFFFF718B),
    dangerBright = Color(0xFFFF99AA),
    emerald = Color(0xFF55D7B4),
    amber = Color(0xFFF2B45F),
    ink = Color(0xFFF4F7FC),
    inkMuted = Color(0xFFC1CDDD),
    inkFaint = Color(0xFF8D9DB3)
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
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-.74).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-.38).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-.26).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-.16).sp
    ),
    titleLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-.10).sp
    ),
    titleMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = .04.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ProductSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = .08.sp
    )
)

val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun AetherFlowTheme(
    themeId: String = "light",
    content: @Composable () -> Unit
) {
    val requested=parseAppTheme(themeId)
    val light=when(requested) {
        AppTheme.LIGHT -> true
        AppTheme.DARK -> false
        AppTheme.SYSTEM -> !isSystemInDarkTheme()
    }

    val palette=if(light) LightPalette else DarkPalette
    val context=LocalContext.current
    val dynamicColor=
        requested == AppTheme.SYSTEM &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val fallback=if(light) {
        lightColorScheme(
            primary=palette.cyan,
            onPrimary=Color.White,
            primaryContainer=palette.cyan.copy(alpha=.10f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,
            onSecondary=Color.White,
            secondaryContainer=palette.emerald.copy(alpha=.10f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.voidElevated,
            onSurface=palette.ink,
            surfaceVariant=palette.glassStrong,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            error=palette.danger,
            outline=palette.glassBorderSoft,
            outlineVariant=palette.glassBorderSoft.copy(alpha=.55f)
        )
    } else {
        darkColorScheme(
            primary=palette.cyan,
            onPrimary=Color(0xFF061329),
            primaryContainer=palette.cyan.copy(alpha=.14f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,
            onSecondary=Color(0xFF061711),
            secondaryContainer=palette.emerald.copy(alpha=.12f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.voidElevated,
            onSurface=palette.ink,
            surfaceVariant=palette.glassStrong,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            error=palette.danger,
            outline=palette.glassBorder,
            outlineVariant=palette.glassBorderSoft
        )
    }

    val scheme=when {
        dynamicColor && light -> dynamicLightColorScheme(context)
        dynamicColor && !light -> dynamicDarkColorScheme(context)
        else -> fallback
    }

    val view=LocalView.current
    if(!view.isInEditMode) {
        val window=(view.context as Activity).window
        SideEffect {
            val controller=WindowCompat.getInsetsController(window,view)
            controller.isAppearanceLightStatusBars=light
            controller.isAppearanceLightNavigationBars=light
            window.statusBarColor=scheme.background.toArgb()
            window.navigationBarColor=scheme.background.toArgb()
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced=false
                window.isNavigationBarContrastEnforced=false
            }
        }
    }

    CompositionLocalProvider(LocalAetherPalette provides palette) {
        MaterialTheme(
            colorScheme=scheme,
            typography=AetherTypography,
            shapes=AetherShapes
        ) {
            ProvideMarbleMotion(content)
        }
    }
}
