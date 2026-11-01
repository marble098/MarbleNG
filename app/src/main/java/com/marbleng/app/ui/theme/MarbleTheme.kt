package com.marbleng.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.marbleng.app.model.FlatAccent
import com.marbleng.app.model.MarblePalette
import com.marbleng.app.model.parseFlatAccent
import com.marbleng.app.model.parseMarblePalette
import com.marbleng.app.ui.MarbleLanguage
import com.marbleng.app.ui.Tr

// MARBLE_M3_EXPRESSIVE_V240 — the theme every screen reads.
//
// MaterialExpressiveTheme + MotionScheme.expressive() + the expressive shape set + the
// Vazirmatn ladder (with emphasized twins). Color is dynamic (Material You) on Android 12+
// when enabled, otherwise the bold accent-seeded fallback from MarbleColor.

/**
 * Geometric tokens of the MarbleNG expressive system. Plain primitives, like the color
 * ramps they sit beside, so JVM unit tests can assert every design rule directly.
 */
object MarbleSpec {
    const val SCREEN_HORIZONTAL_PADDING_DP: Int = 20
    const val SECTION_SPACING_DP: Int = 20
    const val CARD_PADDING_DP: Int = 16
    const val CARD_GENEROUS_PADDING_DP: Int = 20
    const val ROW_MIN_HEIGHT_DP: Int = 64
    const val ROW_ICON_SIZE_DP: Int = 40
    const val ROW_ICON_CORNER_DP: Int = 14
    const val HEADER_ACTION_SIZE_DP: Int = 48

    /**
     * MARBLE_EXPRESSIVE_SHELL_V241 — retired. The hairline between two rows is gone: grouping
     * is carried by the tonal step between surfaces and by air. The token survives only so the
     * old call sites and the source audit keep a single named value to point at.
     */
    const val DIVIDER_THICKNESS_DP: Int = 1

    const val HERO_BUTTON_HEIGHT_DP: Int = 64

    /** Cards and grouped surfaces: soft, 24–32 dp, never a hairline box. */
    const val CARD_CORNER_DP: Int = 24
    const val GROUP_CORNER_DP: Int = 32

    /** Air between two rows of the same group, now that no line separates them. */
    const val ROW_GAP_DP: Int = 6

    /** The one connect pill: resting and connected heights. */
    const val CONNECT_PILL_HEIGHT_DP: Int = 76
    const val CONNECT_PILL_CONNECTED_HEIGHT_DP: Int = 88

    /** Height of the live traffic graph. */
    const val SPARKLINE_HEIGHT_DP: Int = 104

    /**
     * Scroll distance, in pixels, over which a display title collapses into the toolbar.
     * A pure number so the header's curve can be asserted without a device.
     */
    const val HEADER_COLLAPSE_DISTANCE_PX: Int =
        com.marbleng.app.model.HEADER_COLLAPSE_DISTANCE_PX

    /** Bottom chrome: the dock's floating height budget above the gesture bar. */
    const val DOCK_FLOAT_MARGIN_DP: Int = 10

    /** Scroll clearance above the bottom navigation bar. */
    const val BOTTOM_BAR_CLEARANCE_DP: Int = 112

    /** Scroll clearance on rail layouts, where the bottom edge is free. */
    const val RAIL_CLEARANCE_DP: Int = 28

    /** Window widths at/above this get a NavigationRail instead of a NavigationBar. */
    const val WIDE_RAIL_MIN_WIDTH_DP: Int = 600

    /** Pressed-state scale for hero surfaces. */
    const val PRESSED_SCALE: Float = 0.97f

    /** Slide-to-connect track geometry (the connect-style setting keeps this control). */
    const val SLIDE_TRACK_HEIGHT_DP: Int = 56
    const val SLIDE_THUMB_SIZE_DP: Int = 48

    /** Active-tab pill corner of the bottom navigation bar. */
    const val DOCK_ACTIVE_PILL_CORNER_DP: Int = 16
}

fun resolveMarbleDarkMode(themeId: String, systemDark: Boolean): Boolean =
    when (themeId.trim().lowercase()) {
        "dark", "amoled", "oled", "night", "trueblack", "true-black", "black" -> true
        "system", "auto" -> systemDark
        else -> false
    }

fun marbleColorScheme(
    accent: FlatAccent,
    dark: Boolean,
    dynamicColor: Boolean,
    context: Context,
    trueBlack: Boolean = false,
    palette: MarblePalette = MarblePalette.DEFAULT
): ColorScheme {
    val scheme = when {
        // MARBLE_PALETTE_STUDIO_V258 — a chosen palette outranks both the wallpaper and the accent.
        // A person who just picked Aurora is not asking the wallpaper to overrule them, and a
        // palette that lost to dynamic colour on Android 12+ would be a setting that does nothing
        // on the phones most people own.
        palette != MarblePalette.CLASSIC -> marblePaletteScheme(palette, dark)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> marbleFallbackDarkScheme(accent)
        else -> marbleFallbackLightScheme(accent)
    }
    // MARBLE_HOME_STUDIO_V249 — true black is a pass over a dark scheme, never over a light one:
    // black text on a black page is not a theme, it is an outage.
    return if (trueBlack && dark) marbleTrueBlackSurfaces(scheme) else scheme
}

val LocalMarbleFlat = staticCompositionLocalOf { false }

/**
 * Bottom scroll clearance for the current window: the shell provides
 * [MarbleSpec.BOTTOM_BAR_CLEARANCE_DP] under a bottom bar and [MarbleSpec.RAIL_CLEARANCE_DP]
 * beside a navigation rail, so screens never hard-code which chrome they sit in.
 */
val LocalBottomClearance = staticCompositionLocalOf { MarbleSpec.BOTTOM_BAR_CLEARANCE_DP.dp }

object MarbleTheme {
    val status: MarbleStatusColors
        @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current

    val flat: Boolean
        @Composable @ReadOnlyComposable get() = LocalMarbleFlat.current

    val bottomClearance: Dp
        @Composable @ReadOnlyComposable get() = LocalBottomClearance.current
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
fun MarbleExpressiveTheme(
    themeId: String = "system",
    accentId: String = FlatAccent.DEFAULT.id,
    fontId: String = com.marbleng.app.model.AppFont.DEFAULT.id,
    flatSurfaces: Boolean = false,
    dynamicColor: Boolean = true,
    paletteId: String = MarblePalette.CLASSIC.id,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = resolveMarbleDarkMode(themeId, systemDark)
    val accent = parseFlatAccent(accentId)
    val palette = parseMarblePalette(paletteId)
    val context = LocalContext.current
    val scheme = marbleColorScheme(
        accent = accent,
        dark = dark,
        dynamicColor = dynamicColor,
        context = context,
        palette = palette,
        // MARBLE_HOME_STUDIO_V249 — `amoled` was already a theme id this app accepted and could not
        // draw. Reading it here means no new parameter, no new call site and no way for the theme
        // and the resolver to disagree about what "the darkest option" is.
        trueBlack = isTrueBlack(themeId)
    )
    val persian = Tr.now.language == MarbleLanguage.FA
    val typography = buildMarbleTypography(resolveMarbleFontFamily(fontId, persian))

    // The system bars are transparent (edge-to-edge in MainActivity); their icon tone still
    // belongs to the live theme, so a light theme never gets white-on-white status icons
    // even when the phone itself is in dark mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findHostActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalMarbleStatusColors provides marbleStatusColors(dark),
        LocalMarbleFlat provides flatSurfaces
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MarbleShapes,
            typography = typography,
            content = content
        )
    }
}

/** Fallback used only by previews and JVM-safe call sites that have no Activity context. */
fun previewMarbleScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else marbleFallbackLightScheme(FlatAccent.OCEAN)
