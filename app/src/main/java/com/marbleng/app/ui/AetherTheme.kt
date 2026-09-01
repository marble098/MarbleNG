package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_THEME_V34
// MARBLE_SOLID_WHITE_THEME_V35
// MARBLE_REFINED_PRODUCT_UI_V52
// MARBLE_M3_EXPRESSIVE_THEME_V53
// MARBLE_PRISM_THEME_V54
// MARBLE_NAVY_BRAND_THEME_V77
// The whole identity is re-anchored on the Marble navy/ice/electric blue ramp:
//   #000033 deep navy  •  #001144 dark navy  •  #0066CC electric  •  #3399FF bright
//   #ADD8E6 ice        •  #E0FFFF ice white   •  #F0F8FF alice     •  #FFFFFF white
// Light and Dark are the same formal color system; only the surface/ink roles swap.

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
import androidx.compose.ui.graphics.compositeOver
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

/**
 * The eight Marble brand colors. Every tint below is one of these exact hues composited with
 * alpha over a surface — never a foreign hue — so Light and Dark both stay inside the same
 * identity even where a role needs a softer or deeper step.
 */
private object Brand {
    val NavyDeep = Color(0xFF000033)   // deep navy
    val NavyDark = Color(0xFF001144)   // dark navy
    val Electric = Color(0xFF0066CC)   // electric blue
    val Bright = Color(0xFF3399FF)     // bright blue
    val Ice = Color(0xFFADD8E6)        // ice blue
    val IceWhite = Color(0xFFE0FFFF)   // ice white
    val Alice = Color(0xFFF0F8FF)      // alice blue
    val White = Color(0xFFFFFFFF)      // white
    val Black = Color(0xFF000000)      // true black — dark surface base
}

/*
 * Light surfaces are deliberately opaque. The previous translucent stack produced visible
 * rectangular compositing bands on several Android GPUs and weakened the information hierarchy.
 * Depth now comes from spacing, a single soft outline and state colour—not nested glass layers.
 *
 * MARBLE_NAVY_BRAND_THEME_V77
 * Background, surfaces, borders and ink are the Marble navy/ice ramp. The two accents are the
 * electric blue (primary) and deep navy (secondary), so the product reads as one continuous
 * blue system in both themes. Emerald/Amber/Danger stay as *functional* state colours only:
 * a VPN must never dress "blocked" or "connected" in the brand hue.
 */
private val LightPalette = AetherPalette(
    void = Brand.Alice,
    voidElevated = Brand.White,
    glass = Brand.IceWhite,
    glassStrong = Brand.Ice.compositeOver(Brand.White),
    glassBorder = Brand.Electric.copy(alpha = .30f).compositeOver(Brand.White),
    glassBorderSoft = Brand.Ice.copy(alpha = .40f).compositeOver(Brand.White),
    amethyst = Brand.NavyDark,
    amethystBright = Brand.Electric,
    cyan = Brand.Electric,
    cyanBright = Brand.Bright,
    slate = Brand.Ice.copy(alpha = .42f).compositeOver(Brand.White),
    slateBright = Brand.NavyDark.copy(alpha = .55f).compositeOver(Brand.White),
    danger = Color(0xFFE23D5B),
    dangerBright = Color(0xFFF26079),
    emerald = Color(0xFF009A74),
    amber = Color(0xFFD98200),
    ink = Brand.NavyDeep,
    inkMuted = Brand.NavyDeep.copy(alpha = .70f).compositeOver(Brand.White),
    inkFaint = Brand.NavyDeep.copy(alpha = .42f).compositeOver(Brand.White)
)

/* Dark remains an explicit accessibility/user choice and mirrors the same formal color identity. */
// True-black dark theme: base surface is solid #000000, elevated cards and glass layers stay
// inside a near-black ramp so the OLED black holds while cards and state tints still separate.
private val DarkPalette = AetherPalette(
    void = Brand.Black,
    voidElevated = Color(0xFF0A0A0A),
    glass = Color(0xFF060606),
    glassStrong = Brand.Electric.copy(alpha = .18f).compositeOver(Brand.Black),
    glassBorder = Brand.Electric.copy(alpha = .38f).compositeOver(Brand.Black),
    glassBorderSoft = Brand.Ice.copy(alpha = .16f).compositeOver(Brand.Black),
    amethyst = Brand.Electric,
    amethystBright = Brand.Bright,
    cyan = Brand.Bright,
    cyanBright = Brand.Ice,
    slate = Color(0xFF0A0A0A),
    slateBright = Brand.Ice,
    danger = Color(0xFFFF718B),
    dangerBright = Color(0xFFFF99AA),
    emerald = Color(0xFF55D7B4),
    amber = Color(0xFFF2B45F),
    ink = Brand.Alice,
    inkMuted = Brand.Ice.copy(alpha = .85f).compositeOver(Brand.Black),
    inkFaint = Brand.Ice.copy(alpha = .55f).compositeOver(Brand.Black)
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
            onPrimary=Brand.Alice,
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
