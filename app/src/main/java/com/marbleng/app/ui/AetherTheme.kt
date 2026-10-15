package com.marbleng.app.ui

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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.marbleng.app.R
import com.marbleng.app.model.AppFont
import com.marbleng.app.model.DarkOutlineStyle
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseDarkOutlineStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class AppTheme { SYSTEM, DARK, LIGHT, PHONE_DYNAMIC }

fun parseAppTheme(id: String): AppTheme = when {
    id.equals("dark", true) -> AppTheme.DARK
    id.equals("system", true) -> AppTheme.SYSTEM
    id.equals("phone", true) || id.equals("dynamic", true) -> AppTheme.PHONE_DYNAMIC
    else -> AppTheme.LIGHT
}

private data class AetherPalette(
    val void: Color,
    val voidElevated: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassBorderSoft: Color,

    val barGlass: Color,
    val barGlassBorder: Color,
    val barGlassHighlight: Color,

    val floatSurface: Color,
    val floatBorder: Color,
    val floatShadow: Color,

    val floatActions: MarbleFloatActionTones,
    val floatInkLight: Color,
    val floatInkDark: Color,
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
    val inkFaint: Color,

    val dynamic: Boolean = false
)

private object Brand {
    val NavyDeep = Color(0xFF000033)
    val NavyDark = Color(0xFF001144)
    val Electric = Color(0xFF0066CC)
    val Bright = Color(0xFF3399FF)
    val Ice = Color(0xFFADD8E6)
    val IceWhite = Color(0xFFE0FFFF)
    val Alice = Color(0xFFF0F8FF)
    val White = Color(0xFFFFFFFF)
}

private val LightPalette = AetherPalette(
    void = Color(0xFFF4F8FD),
    voidElevated = Brand.White,
    glass = Color(0xFFEDF3FA),
    glassStrong = Color(0xFFE2ECF6),
    glassBorder = Brand.Electric.copy(alpha = .22f).compositeOver(Brand.White),
    glassBorderSoft = Brand.NavyDeep.copy(alpha = .10f).compositeOver(Brand.White),

    barGlass = Brand.White.copy(alpha = .94f),
    barGlassBorder = Brand.NavyDeep.copy(alpha = .16f).compositeOver(Brand.White),
    barGlassHighlight = Color.White.copy(alpha = .60f),

    floatSurface = Color(0xFFEDF3FB),
    floatBorder = Brand.NavyDeep.copy(alpha = .14f).compositeOver(Color(0xFFEDF3FB)),
    floatShadow = Color(0xFF0A1A33),

    floatActions = MarbleFloatActions.Light.tones(),
    floatInkLight = Color(MarbleFloatActions.Light.inkLight),
    floatInkDark = Color(MarbleFloatActions.Light.inkDark),
    amethyst = Brand.NavyDark,
    amethystBright = Brand.Electric,
    cyan = Brand.Electric,
    cyanBright = Brand.Bright,
    slate = Color(0xFFE8EFF7),
    slateBright = Brand.NavyDark.copy(alpha = .55f).compositeOver(Brand.White),
    danger = Color(0xFFE23D5B),
    dangerBright = Color(0xFFF26079),
    emerald = Color(0xFF009A74),
    amber = Color(0xFFD98200),
    ink = Brand.NavyDeep,
    inkMuted = Brand.NavyDeep.copy(alpha = .66f).compositeOver(Brand.White),
    inkFaint = Brand.NavyDeep.copy(alpha = .40f).compositeOver(Brand.White)
)

private val DarkPalette = AetherPalette(

    void = Color.Black,
    voidElevated = Color(0xFF090D14),
    glass = Color(0xFF0C121B),
    glassStrong = Color(0xFF101824),
    glassBorder = Brand.Bright.copy(alpha = .28f).compositeOver(Color(0xFF090D14)),
    glassBorderSoft = Color(0xFF202C3E),

    barGlass = Color(0xFF090D14),
    barGlassBorder = Brand.Ice.copy(alpha = .18f).compositeOver(Color(0xFF090D14)),
    barGlassHighlight = Color.Transparent,

    floatSurface = Color(0xFF090D14),
    floatBorder = Brand.Ice.copy(alpha = .18f).compositeOver(Color(0xFF090D14)),
    floatShadow = Color.Black,

    floatActions = MarbleFloatActions.Dark.tones(),
    floatInkLight = Color(MarbleFloatActions.Dark.inkLight),
    floatInkDark = Color(MarbleFloatActions.Dark.inkDark),
    amethyst = Brand.Electric,
    amethystBright = Brand.Bright,
    cyan = Brand.Bright,
    cyanBright = Brand.Ice,
    slate = Color(0xFF101724),
    slateBright = Brand.Ice,
    danger = Color(0xFFFF718B),
    dangerBright = Color(0xFFFF99AA),
    emerald = Color(0xFF55D7B4),
    amber = Color(0xFFF2B45F),
    ink = Color(0xFFF0F8FF),
    inkMuted = Brand.Ice.copy(alpha = .78f).compositeOver(Color(0xFF000000)),
    inkFaint = Brand.Ice.copy(alpha = .42f).compositeOver(Color(0xFF000000))
)

private fun dynamicPhonePalette(scheme: androidx.compose.material3.ColorScheme, dark: Boolean): AetherPalette {
    val card = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest
    val surface = card
    fun over(fg: Color, alpha: Float): Color = fg.copy(alpha = alpha).compositeOver(surface)

    val floatActions = MarbleFloatActions.dynamic(
        primary = scheme.primary.toArgb(),
        secondary = scheme.secondary.toArgb(),
        tertiary = scheme.tertiary.toArgb(),
        error = scheme.error.toArgb(),
        onPrimary = scheme.onPrimary.toArgb(),
        onPrimaryContainer = scheme.onPrimaryContainer.toArgb()
    )
    return AetherPalette(
        void = if (dark) scheme.background else scheme.surfaceContainerLow,
        voidElevated = card,
        glass = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        glassStrong = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        glassBorder = over(scheme.primary, if (dark) .30f else .22f),
        glassBorderSoft = over(scheme.outlineVariant, if (dark) .80f else .70f),
        barGlass = card.copy(alpha = if (dark) .88f else .78f),
        barGlassBorder = over(scheme.outlineVariant, if (dark) .55f else .60f),
        barGlassHighlight = if (dark) {
            Color.White.copy(alpha = .06f).compositeOver(surface)
        } else {
            Color.White.copy(alpha = .55f).compositeOver(surface)
        },

        floatSurface = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        floatBorder = over(scheme.outlineVariant, if (dark) .70f else .60f),
        floatShadow = scheme.onSurface,
        floatActions = floatActions.tones(),
        floatInkLight = Color(floatActions.inkLight),
        floatInkDark = Color(floatActions.inkDark),
        amethyst = scheme.secondary,
        amethystBright = scheme.tertiary,
        cyan = scheme.primary,
        cyanBright = scheme.tertiary,
        slate = scheme.surfaceVariant,
        slateBright = scheme.onSurfaceVariant,
        danger = Color(0xFFE23D5B),
        dangerBright = Color(0xFFF26079),
        emerald = Color(0xFF009A74),
        amber = Color(0xFFD98200),
        ink = scheme.onSurface,
        inkMuted = scheme.onSurfaceVariant,
        inkFaint = scheme.onSurfaceVariant.copy(alpha = .72f).compositeOver(surface),
        dynamic = true
    )
}

private val LocalAetherPalette = staticCompositionLocalOf { LightPalette }

object Aether {
    val Void: Color @Composable get() = LocalAetherPalette.current.void
    val VoidElevated: Color @Composable get() = LocalAetherPalette.current.voidElevated
    val Glass: Color @Composable get() = LocalAetherPalette.current.glass
    val GlassStrong: Color @Composable get() = LocalAetherPalette.current.glassStrong
    val GlassBorder: Color @Composable get() = LocalAetherPalette.current.glassBorder
    val GlassBorderSoft: Color @Composable get() = LocalAetherPalette.current.glassBorderSoft
    val BarGlass: Color @Composable get() = LocalAetherPalette.current.barGlass
    val BarGlassBorder: Color @Composable get() = LocalAetherPalette.current.barGlassBorder
    val BarGlassHighlight: Color @Composable get() = LocalAetherPalette.current.barGlassHighlight
    val FloatSurface: Color @Composable get() = LocalAetherPalette.current.floatSurface
    val FloatBorder: Color @Composable get() = LocalAetherPalette.current.floatBorder
    val FloatShadow: Color @Composable get() = LocalAetherPalette.current.floatShadow
    val FloatActions: MarbleFloatActionTones @Composable get() = LocalAetherPalette.current.floatActions
    val FloatInkLight: Color @Composable get() = LocalAetherPalette.current.floatInkLight
    val FloatInkDark: Color @Composable get() = LocalAetherPalette.current.floatInkDark
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
    val IsDynamic: Boolean @Composable get() = LocalAetherPalette.current.dynamic
}

internal val VazirFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

internal fun previewFontFamily(id: String): FontFamily = selectedFontFamily(id)

private fun selectedFontFamily(id: String): FontFamily = when (parseAppFont(id)) {
    AppFont.VAZIR -> VazirFamily

    AppFont.SYSTEM -> FontFamily.Default

    AppFont.GOOGLE_SANS -> FontFamily.Default

    AppFont.TIMES_NEW_ROMAN -> FontFamily.Serif
}

private fun aetherTypography(fontId: String, persian: Boolean): Typography {
    val family = if (persian) VazirFamily else selectedFontFamily(fontId)

    fun track(latin: Double): TextUnit = if (persian) 0.sp else latin.sp

    fun labelSmallSize(): TextUnit = if (persian) 12.sp else 11.5.sp
    fun labelSmallLine(): TextUnit = if (persian) 17.sp else 16.sp

    val bodyWeight = if (persian) FontWeight.Medium else FontWeight.Normal

    return Typography(
    displayLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = track(-.50)
    ),
    displayMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = track(-.40)
    ),
    displaySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = track(-.30)
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = track(-.30)
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = track(-.20)
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = track(-.10)
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = track(-.10)
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = track(.05)
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = bodyWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = track(.15)
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = bodyWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = track(.10)
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontWeight = bodyWeight,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = track(.20)
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = track(.10)
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = track(.30)
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = labelSmallSize(),
        lineHeight = labelSmallLine(),
        letterSpacing = track(.40)
    )
)
}

// MARBLE_NATIVE_ANDROID_V213 — the corner ramp moves onto the newest Android shape scale.

val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

internal object MarbleExpressiveShapes {
    val ButtonPressRadius = 999.dp
    val CompactButtonPressRadius = 24.dp
    val TilePressRadius = 22.dp
}

@Composable
private fun applyNightOutline(
    palette: AetherPalette,
    styleId: String
): AetherPalette = when (parseDarkOutlineStyle(styleId)) {
    DarkOutlineStyle.SUBTLE -> palette

    DarkOutlineStyle.BOLD -> palette.copy(
        glassBorder = palette.glassBorder.copy(alpha = (palette.glassBorder.alpha * 2.1f).coerceAtMost(.85f)),
        glassBorderSoft = Color(0xFF31405A),
        barGlassBorder = palette.barGlassBorder.copy(alpha = (palette.barGlassBorder.alpha * 2.4f).coerceAtMost(.75f))
    )

    DarkOutlineStyle.COLORED -> palette.copy(
        glassBorder = Brand.Electric.copy(alpha = .52f),
        glassBorderSoft = Brand.Electric.copy(alpha = .30f),
        barGlassBorder = Brand.Bright.copy(alpha = .40f)
    )

    DarkOutlineStyle.HIDDEN -> palette.copy(
        glassBorder = Color.Transparent,
        glassBorderSoft = Color.Transparent,
        barGlassBorder = Color.Transparent
    )
}

@Composable
fun AetherFlowTheme(
    themeId: String = "light",

    fontId: String = AppFont.DEFAULT.id,
    outlineStyleId: String = DarkOutlineStyle.SUBTLE.id,
    content: @Composable () -> Unit
) {
    val requested=parseAppTheme(themeId)
    val light=when(requested) {
        AppTheme.LIGHT -> true
        AppTheme.DARK -> false
        AppTheme.SYSTEM -> !isSystemInDarkTheme()
        AppTheme.PHONE_DYNAMIC -> !isSystemInDarkTheme()
    }

    val context=LocalContext.current

    val phoneDynamic =
        requested == AppTheme.PHONE_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicPalette: AetherPalette?
    val dynamicScheme: androidx.compose.material3.ColorScheme?
    if (phoneDynamic) {
        val generated =
            if (light) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
        val rebuilt = dynamicPhonePalette(generated, !light)
        dynamicPalette = rebuilt

        dynamicScheme = generated.copy(
            background = rebuilt.void,
            surface = rebuilt.voidElevated,
            surfaceTint = Color.Transparent
        )
    } else {
        dynamicPalette = null
        dynamicScheme = null
    }

    val systemDynamicColor = false

    val palette=dynamicPalette ?: if(light) LightPalette else applyNightOutline(DarkPalette, outlineStyleId)

    val fallback=if(light) {
        lightColorScheme(
            primary=palette.cyan,
            onPrimary=Color.White,
            primaryContainer=palette.cyan.copy(alpha=.10f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,

            onSecondary=Brand.NavyDeep,
            secondaryContainer=palette.emerald.copy(alpha=.10f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            onTertiary=Color.White,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.voidElevated,
            onSurface=palette.ink,
            surfaceVariant=palette.glassStrong,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            error=palette.danger,
            onError=Brand.NavyDeep,
            outline=palette.glassBorderSoft,
            outlineVariant=palette.glassBorderSoft.copy(alpha=.55f),

            // MARBLE_NATIVE_ANDROID_V213 — the ladder opens up. On the cool page (#F4F8FD) the

            surfaceContainerLowest=Brand.White,
            surfaceContainerLow=Color(0xFFF5F8FC),
            surfaceContainer=Color(0xFFEEF3F9),
            surfaceContainerHigh=Color(0xFFE7EEF6),
            surfaceContainerHighest=Color(0xFFDFE8F2),
            surfaceDim=Color(0xFFD5E0EC),
            surfaceBright=Color(0xFFFAFCFF),
            inverseSurface=Color(0xFF0D1420),
            inverseOnSurface=Color(0xFFEEF3FA),
            inversePrimary=Color(0xFFA9CBFF)
        )
    } else {
        darkColorScheme(
            primary=palette.cyan,
            onPrimary=Brand.NavyDeep,
            primaryContainer=palette.cyan.copy(alpha=.14f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,
            onSecondary=Color(0xFF061711),
            secondaryContainer=palette.emerald.copy(alpha=.12f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            onTertiary=Color.White,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.voidElevated,
            onSurface=palette.ink,
            surfaceVariant=palette.glassStrong,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            error=palette.danger,

            onError=Brand.NavyDeep,
            outline=palette.glassBorder,
            outlineVariant=palette.glassBorderSoft,

            // MARBLE_NATIVE_ANDROID_V213 — the dark ladder lifts off the black floor by one

            surfaceContainerLowest=Color.Black,
            surfaceContainerLow=Color(0xFF0B111C),
            surfaceContainer=Color(0xFF131B28),
            surfaceContainerHigh=Color(0xFF1A2432),
            surfaceContainerHighest=Color(0xFF212C3C),
            surfaceDim=Color.Black,
            surfaceBright=Color(0xFF141B27),
            inverseSurface=Color(0xFFEDF2FA),
            inverseOnSurface=Color(0xFF0D1420),
            inversePrimary=Color(0xFF9CC6FF)
        )
    }

    val scheme=dynamicScheme ?: when {
        systemDynamicColor && light -> dynamicLightColorScheme(context)
        systemDynamicColor && !light -> dynamicDarkColorScheme(context)
        else -> fallback
    }

    val view=LocalView.current
    if(!view.isInEditMode) {
        val window=(view.context as Activity).window
        SideEffect {
            val controller=WindowCompat.getInsetsController(window,view)
            controller.isAppearanceLightStatusBars=light
            controller.isAppearanceLightNavigationBars=light

            window.statusBarColor=palette.void.toArgb()

            window.navigationBarColor=Color.Transparent.toArgb()
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(Color.Transparent.toArgb())
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced=false
                window.isNavigationBarContrastEnforced=false
            }
        }
    }

    CompositionLocalProvider(LocalAetherPalette provides palette) {
        val persianActive = LocalMarbleStrings.current.language == MarbleLanguage.FA
        MaterialTheme(
            colorScheme=scheme,
            typography=aetherTypography(fontId, persianActive),
            shapes=AetherShapes
        ) {
            ProvideMarbleMotion(content)
        }
    }
}
