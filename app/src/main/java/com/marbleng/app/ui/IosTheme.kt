package com.marbleng.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.marbleng.app.R
import com.marbleng.app.model.AppFont
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseFlatAccent

/**
 * Core geometric & color tokens of the MarbleNG iOS Design System.
 * Exposed as plain primitives so JVM unit tests can assert every design rule directly.
 */
object IosSpec {
    const val BACKGROUND_HEX: Long = 0xFFF8F9FA
    const val CARD_HEX: Long = 0xFFFFFFFF
    const val TITLE_INK_HEX: Long = 0xFF1C1C1E
    const val SUBTITLE_GRAY_HEX: Long = 0xFF8E8E93
    const val CHEVRON_GRAY_HEX: Long = 0xFFC7C7CC
    const val DIVIDER_GRAY_HEX: Long = 0xFFE5E5EA
    const val SQUIRCLE_GLYPH_HEX: Long = 0xFFFFFFFF

    const val CARD_CORNER_DP: Int = 20
    const val SQUIRCLE_SIZE_DP: Int = 36
    const val SQUIRCLE_CORNER_DP: Int = 12
    const val SQUIRCLE_GLYPH_DP: Int = 20
    const val DIVIDER_THICKNESS_DP: Int = 1
    const val CHEVRON_SIZE_DP: Int = 16

    const val SCREEN_HORIZONTAL_PADDING_DP: Int = 20
    const val CARD_PADDING_DP: Int = 16
    const val CARD_GENEROUS_PADDING_DP: Int = 20
    const val SECTION_SPACING_DP: Int = 20
    const val ITEM_VERTICAL_PADDING_DP: Int = 14
    const val ITEM_HORIZONTAL_PADDING_DP: Int = 16
    const val ROW_MIN_HEIGHT_DP: Int = 56
    const val SOFT_SHADOW_ELEVATION_DP: Int = 2

    // Council refinement — motion & depth ladder (pure tokens, no behavior)
    const val CARD_PRESSED_SCALE: Float = 0.98f
    const val HERO_PULSE_MIN_SCALE: Float = 0.92f
    const val HERO_PULSE_MAX_SCALE: Float = 1.06f
    const val DOCK_ACTIVE_PILL_CORNER_DP: Int = 16
    const val SLIDE_THUMB_SIZE_DP: Int = 48
    const val SLIDE_TRACK_HEIGHT_DP: Int = 56
    const val EMPTY_ILLUSTRATION_SIZE_DP: Int = 56
}

@Immutable
data class IosPalette(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val cardSecondary: Color,
    val cardBorder: Color,
    val softShadow: Color,
    val title: Color,
    val subtitle: Color,
    val tertiary: Color,
    val divider: Color,
    val chevron: Color,
    val squircleGlyph: Color,
    val tabBar: Color,
    val tabBarBorder: Color,
    val searchFill: Color,
    val switchOffTrack: Color,
    val accent: Color,
    val blue: Color,
    val green: Color,
    val indigo: Color,
    val orange: Color,
    val red: Color,
    val teal: Color,
    val cyan: Color,
    val purple: Color,
    val pink: Color,
    val gray: Color
)

fun iosAccentColor(accent: FlatAccent, dark: Boolean = false): Color = when (accent) {
    FlatAccent.OCEAN -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    FlatAccent.VIOLET -> if (dark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
    FlatAccent.EMERALD -> if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    FlatAccent.AMBER -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    FlatAccent.ROSE -> if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    FlatAccent.GRAPHITE -> if (dark) Color(0xFF98989D) else Color(0xFF3A3A3C)
}

fun lightIosPalette(accent: FlatAccent = FlatAccent.OCEAN): IosPalette = IosPalette(
    dark = false,
    background = Color(IosSpec.BACKGROUND_HEX),
    card = Color(IosSpec.CARD_HEX),
    cardSecondary = Color(0xFFF2F2F7),
    cardBorder = Color(0x0F000000),
    softShadow = Color(0x12000000),
    title = Color(IosSpec.TITLE_INK_HEX),
    subtitle = Color(IosSpec.SUBTITLE_GRAY_HEX),
    tertiary = Color(0xFFAEAEB2),
    divider = Color(IosSpec.DIVIDER_GRAY_HEX),
    chevron = Color(IosSpec.CHEVRON_GRAY_HEX),
    squircleGlyph = Color(IosSpec.SQUIRCLE_GLYPH_HEX),
    tabBar = Color(0xF5FFFFFF),
    tabBarBorder = Color(0xFFE5E5EA),
    searchFill = Color(0xFFEEEEF2),
    switchOffTrack = Color(0xFFE9E9EB),
    accent = iosAccentColor(accent, dark = false),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    indigo = Color(0xFF5856D6),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    teal = Color(0xFF30B0C7),
    cyan = Color(0xFF32ADE6),
    purple = Color(0xFFAF52DE),
    pink = Color(0xFFFF2D55),
    gray = Color(0xFF8E8E93)
)

fun darkIosPalette(accent: FlatAccent = FlatAccent.OCEAN): IosPalette = IosPalette(
    dark = true,
    background = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    cardSecondary = Color(0xFF2C2C2E),
    cardBorder = Color(0x22FFFFFF),
    softShadow = Color(0x00000000),
    title = Color(0xFFF5F5F7),
    subtitle = Color(0xFF98989D),
    tertiary = Color(0xFF636366),
    divider = Color(0xFF38383A),
    chevron = Color(0xFF636366),
    squircleGlyph = Color(IosSpec.SQUIRCLE_GLYPH_HEX),
    tabBar = Color(0xF21C1C1E),
    tabBarBorder = Color(0xFF38383A),
    searchFill = Color(0xFF2C2C2E),
    switchOffTrack = Color(0xFF39393D),
    accent = iosAccentColor(accent, dark = true),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    indigo = Color(0xFF5E5CE6),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    teal = Color(0xFF40C8E0),
    cyan = Color(0xFF64D2FF),
    purple = Color(0xFFBF5AF2),
    pink = Color(0xFFFF375F),
    gray = Color(0xFF8E8E93)
)

fun resolveIosDarkMode(themeId: String, systemDark: Boolean): Boolean =
    when (themeId.trim().lowercase()) {
        "dark", "amoled", "oled", "night" -> true
        "system", "auto" -> systemDark
        else -> false
    }

/**
 * MARBLE_WEIGHT_LADDER_V235 — the one weight ladder every surface reads.
 *
 * Until V235 the app effectively had two voices: Bold for anything that looked like a title
 * (page titles, dialog titles, every settings row, empty states, verdicts) and Normal for
 * prose, with SemiBold/Medium sprinkled on labels. Same weight everywhere is the same
 * hierarchy nowhere: nothing could be louder than a list row.
 *
 * The ladder assigns one weight per *role*, and `buildIosTypography` plus the components
 * read it from here instead of naming weights ad hoc:
 *
 *   DISPLAY   800  the largest type on a surface — page (large-header) titles, display slot
 *   HEADLINE  700  headlines inside a page — hero status, sheet/dialog titles, verdicts
 *   TITLE     600  row and card titles, section headers, button and chip labels
 *   LABEL     500  small labels, captions, hints, unselected segmented/chip states
 *   BODY      400  prose — subtitles, body copy, notes
 *   NUMERIC   700  measured values (stat tiles, steppers, percentages) — data reads firmer
 *                  than the prose around it at the same size
 *
 * Selected/active states step one rung up from their resting rung (LABEL 500 → TITLE 600
 * → HEADLINE 700); that step, not color alone, is what marks the active choice.
 *
 * Deliberately not shipped: Light/Thin (300 and below). The app has no large quiet text
 * for them to carry — the biggest secondary line is a 14sp subtitle — and thin Vazirmatn
 * strokes at caption sizes are exactly where Persian legibility dies first. Black (900)
 * is left out for the mirror reason: nothing here is a poster.
 *
 * Plain primitives, like [IosSpec], so JVM unit tests can pin the ladder directly.
 * Every weight named here has a real bundled face in [VazirmatnFamily] — a weight the
 * family does not register would be synthesised by the platform, and a synthesised
 * Persian bold is a smear, not a weight.
 */
object MarbleWeightSpec {
    const val BODY: Int = 400
    const val LABEL: Int = 500
    const val TITLE: Int = 600
    const val HEADLINE: Int = 700
    const val DISPLAY: Int = 800
    const val NUMERIC: Int = 700

    /** The weights with a real bundled Vazirmatn face, in ladder order. */
    val REGISTERED: List<Int> = listOf(BODY, LABEL, TITLE, HEADLINE, DISPLAY)

    fun isRegistered(weight: Int): Boolean = weight in REGISTERED
}

private val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight(MarbleWeightSpec.BODY)),
    Font(R.font.vazirmatn_medium, FontWeight(MarbleWeightSpec.LABEL)),
    Font(R.font.vazirmatn_semibold, FontWeight(MarbleWeightSpec.TITLE)),
    Font(R.font.vazirmatn_bold, FontWeight(MarbleWeightSpec.HEADLINE)),
    // MARBLE_WEIGHT_LADDER_V235 — the DISPLAY rung. Same Vazirmatn release (fontRevision
    // 33.003) as the other four faces, so metrics and joining behaviour stay one family.
    Font(R.font.vazirmatn_extrabold, FontWeight(MarbleWeightSpec.DISPLAY))
)

internal fun resolveIosFontFamily(fontId: String, persianActive: Boolean): FontFamily {
    if (persianActive) return VazirmatnFamily
    return when (parseAppFont(fontId)) {
        AppFont.VAZIR -> VazirmatnFamily
        AppFont.SYSTEM -> FontFamily.SansSerif
        AppFont.GOOGLE_SANS -> VazirmatnFamily
        AppFont.TIMES_NEW_ROMAN -> FontFamily.Serif
    }
}

// MARBLE_WEIGHT_LADDER_V235 — slots carry ladder rungs, so a call site that just names a
// slot already speaks at the right weight; `.copy(fontWeight = …)` is reserved for a
// deliberate step up or down (selected states, numeric values, warnings).
private fun buildIosTypography(family: FontFamily): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.DISPLAY),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.DISPLAY),
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.HEADLINE),
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.HEADLINE),
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.HEADLINE),
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.TITLE),
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.TITLE),
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.BODY),
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.BODY),
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.BODY),
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.TITLE),
        fontSize = 13.5.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.LABEL),
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(MarbleWeightSpec.LABEL),
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

private val IosMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(IosSpec.CARD_CORNER_DP.dp),
    large = RoundedCornerShape(IosSpec.CARD_CORNER_DP.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private fun iosMaterialColorScheme(palette: IosPalette): ColorScheme =
    if (palette.dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.indigo,
            onSecondary = Color.White,
            tertiary = palette.green,
            background = palette.background,
            onBackground = palette.title,
            surface = palette.card,
            onSurface = palette.title,
            surfaceVariant = palette.cardSecondary,
            onSurfaceVariant = palette.subtitle,
            outline = palette.divider,
            outlineVariant = palette.divider,
            error = palette.red,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.indigo,
            onSecondary = Color.White,
            tertiary = palette.green,
            background = palette.background,
            onBackground = palette.title,
            surface = palette.card,
            onSurface = palette.title,
            surfaceVariant = palette.cardSecondary,
            onSurfaceVariant = palette.subtitle,
            outline = palette.divider,
            outlineVariant = palette.divider,
            error = palette.red,
            onError = Color.White
        )
    }

val LocalIosPalette = staticCompositionLocalOf { lightIosPalette() }
val LocalIosFlat = staticCompositionLocalOf { true }

object IosTheme {
    val colors: IosPalette
        @Composable @ReadOnlyComposable get() = LocalIosPalette.current

    val flat: Boolean
        @Composable @ReadOnlyComposable get() = LocalIosFlat.current
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
fun MarbleIosTheme(
    themeId: String = "light",
    accentId: String = FlatAccent.DEFAULT.id,
    fontId: String = AppFont.DEFAULT.id,
    flatSurfaces: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = resolveIosDarkMode(themeId, systemDark)
    val accent = parseFlatAccent(accentId)
    val palette = if (dark) darkIosPalette(accent) else lightIosPalette(accent)
    val persian = Tr.now.language == MarbleLanguage.FA
    val typography = buildIosTypography(resolveIosFontFamily(fontId, persian))

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findHostActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.dark
                isAppearanceLightNavigationBars = !palette.dark
            }
        }
    }

    CompositionLocalProvider(
        LocalIosPalette provides palette,
        LocalIosFlat provides flatSurfaces
    ) {
        MaterialTheme(
            colorScheme = iosMaterialColorScheme(palette),
            typography = typography,
            shapes = IosMaterialShapes,
            content = content
        )
    }
}
