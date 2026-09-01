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
    val isDark: Boolean,
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
    isDark = false,
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

/*
 * MARBLE_AMOLED_BLACK_THEME_V103
 *
 * Dark is a true AMOLED theme, not a navy tint.
 *
 *  - The base surface is pure #000000 so OLED pixels are physically off. That is the whole point
 *    of the theme: battery, infinite contrast, and a product that disappears into the bezel.
 *  - Depth is built by *near-black* steps (#000 -> #08090C -> #0E1118 -> #151A24), never by a
 *    coloured slab. Each step is ~3-4% luminance apart, which is the smallest difference that
 *    still reads as a separate plane on an OLED panel without introducing a grey haze.
 *  - Borders are ice-blue at very low alpha over black, so hairlines stay visible on black
 *    without turning into the bright rings that a navy background used to hide.
 *  - Ink is intentionally NOT pure white: #EAF2FF at full and softer steps below it. Pure white
 *    on pure black smears on OLED and raises perceived glare on a VPN app that is often opened
 *    at night.
 *  - The accents stay on Marble's blue ramp, one step brighter than in Light, because a
 *    saturated colour on black loses apparent brightness.
 */
private object Amoled {
    val Black = Color(0xFF000000)          // true AMOLED base — pixels off
    val Raised = Color(0xFF08090C)         // cards / elevated planes
    val Panel = Color(0xFF0E1118)          // panels, wells
    val PanelStrong = Color(0xFF151A24)    // strongest inset step
    val Ink = Color(0xFFEAF2FF)            // primary ink (not pure white, on purpose)
}

private val DarkPalette = AetherPalette(
    isDark = true,
    void = Amoled.Black,
    voidElevated = Amoled.Raised,
    glass = Amoled.Panel,
    glassStrong = Amoled.PanelStrong,
    glassBorder = Brand.Bright.copy(alpha = .26f).compositeOver(Amoled.Black),
    glassBorderSoft = Brand.Ice.copy(alpha = .11f).compositeOver(Amoled.Black),
    amethyst = Brand.Bright,
    amethystBright = Brand.Ice,
    cyan = Brand.Bright,
    cyanBright = Brand.Ice,
    slate = Amoled.Panel,
    slateBright = Brand.Ice,
    danger = Color(0xFFFF7089),
    dangerBright = Color(0xFFFF9CAD),
    emerald = Color(0xFF3FE0B0),
    amber = Color(0xFFFFC061),
    ink = Amoled.Ink,
    inkMuted = Amoled.Ink.copy(alpha = .68f).compositeOver(Amoled.Black),
    inkFaint = Amoled.Ink.copy(alpha = .40f).compositeOver(Amoled.Black)
)

private val LocalAetherPalette = staticCompositionLocalOf { LightPalette }

object Aether {
    /** True when the AMOLED (black) palette is active — surfaces must stay near #000000. */
    val IsDark: Boolean @Composable get() = LocalAetherPalette.current.isDark
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
            onPrimary=Amoled.Black,
            primaryContainer=palette.cyan.copy(alpha=.14f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,
            onSecondary=Amoled.Black,
            secondaryContainer=palette.emerald.copy(alpha=.12f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.void,
            onSurface=palette.ink,
            surfaceContainerLowest=palette.void,
            surfaceContainerLow=palette.voidElevated,
            surfaceContainer=palette.voidElevated,
            surfaceContainerHigh=palette.glass,
            surfaceContainerHighest=palette.glassStrong,
            surfaceVariant=palette.glass,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            scrim=Color.Black,
            error=palette.danger,
            outline=palette.glassBorder,
            outlineVariant=palette.glassBorderSoft
        )
    }

    val scheme=when {
        dynamicColor && light -> dynamicLightColorScheme(context)
        // Even under Material You, dark stays AMOLED: only the accents come from the wallpaper,
        // the surface stack is forced back to true black.
        dynamicColor && !light -> dynamicDarkColorScheme(context).copy(
            background=palette.void,
            surface=palette.void,
            surfaceContainerLowest=palette.void,
            surfaceContainerLow=palette.voidElevated,
            surfaceContainer=palette.voidElevated,
            surfaceContainerHigh=palette.glass,
            surfaceContainerHighest=palette.glassStrong,
            surfaceVariant=palette.glass,
            surfaceTint=Color.Transparent,
            scrim=Color.Black
        )
        else -> fallback
    }

    val view=LocalView.current
    if(!view.isInEditMode) {
        val window=(view.context as Activity).window
        SideEffect {
            val controller=WindowCompat.getInsetsController(window,view)
            controller.isAppearanceLightStatusBars=light
            controller.isAppearanceLightNavigationBars=light
            // System bars are painted with the exact app background so an AMOLED build has no
            // visible seam between the app and the gesture bar.
            window.statusBarColor=palette.void.toArgb()
            window.navigationBarColor=palette.void.toArgb()
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
