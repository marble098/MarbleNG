package com.marbleng.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.marbleng.app.model.FlatAccent

// MARBLE_M3_EXPRESSIVE_V240 — the one color system of the app.
//
// Two sources, one contract:
//   1. Dynamic color (Material You) on Android 12+ when the user leaves it on: the scheme
//      comes from the wallpaper and every role below is ignored.
//   2. Otherwise a bold custom fallback scheme, seeded by the user's accent choice.
//
// The fallback keeps the M3 baseline neutrals, error ramp and secondary/tertiary ramps fixed
// (they are contrast-proven) and varies only the primary ramp per accent, so all six accents
// stay energetic but never noisy. Semantic states that the ColorScheme has no role for
// (success, warning) live in [MarbleStatusColors], which is defined for both light and dark
// and reads fine on top of dynamic schemes too.
//
// Text roles target WCAG AA (4.5:1) on their containers in both modes.

/** The primary ramp one accent seed paints, in one mode. */
private data class AccentRamp(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
)

private fun accentRamp(accent: FlatAccent, dark: Boolean): AccentRamp = when (accent) {
    FlatAccent.OCEAN -> if (dark) {
        AccentRamp(
            primary = Color(0xFFA8C7FA),
            onPrimary = Color(0xFF0A305F),
            primaryContainer = Color(0xFF0842A2),
            onPrimaryContainer = Color(0xFFD3E3FD)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF0B57D0),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD3E3FD),
            onPrimaryContainer = Color(0xFF041E49)
        )
    }

    FlatAccent.VIOLET -> if (dark) {
        AccentRamp(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF6750A4),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF4F378B)
        )
    }

    FlatAccent.EMERALD -> if (dark) {
        AccentRamp(
            primary = Color(0xFF8BD39B),
            onPrimary = Color(0xFF0B3B1E),
            primaryContainer = Color(0xFF0E5A28),
            onPrimaryContainer = Color(0xFFB9F0C0)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF146C2E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFB9F0C0),
            onPrimaryContainer = Color(0xFF072711)
        )
    }

    FlatAccent.AMBER -> if (dark) {
        AccentRamp(
            primary = Color(0xFFFFB945),
            onPrimary = Color(0xFF442B00),
            primaryContainer = Color(0xFF7A5200),
            onPrimaryContainer = Color(0xFFFFDE9E)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF7A5200),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDE9E),
            onPrimaryContainer = Color(0xFF2C1E00)
        )
    }

    FlatAccent.ROSE -> if (dark) {
        AccentRamp(
            primary = Color(0xFFFFA9C7),
            onPrimary = Color(0xFF5B1136),
            primaryContainer = Color(0xFF7E2A52),
            onPrimaryContainer = Color(0xFFFFD8E6)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF9C3C6B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD8E6),
            onPrimaryContainer = Color(0xFF3E0A20)
        )
    }

    FlatAccent.GRAPHITE -> if (dark) {
        AccentRamp(
            primary = Color(0xFFC7C7CC),
            onPrimary = Color(0xFF1C1C1E),
            primaryContainer = Color(0xFF3A3A3C),
            onPrimaryContainer = Color(0xFFF2F2F7)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF3A3A3C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E2E6),
            onPrimaryContainer = Color(0xFF1C1C1E)
        )
    }
}

/**
 * The bold custom fallback scheme for [accent] in light mode. Used whenever dynamic color
 * is off or unavailable; the M3 baseline neutrals/error/secondary ramps are fixed and only
 * the primary ramp is seeded, so every accent stays contrast-safe.
 */
fun marbleFallbackLightScheme(accent: FlatAccent): ColorScheme {
    val ramp = accentRamp(accent, dark = false)
    return lightColorScheme(
        primary = ramp.primary,
        onPrimary = ramp.onPrimary,
        primaryContainer = ramp.primaryContainer,
        onPrimaryContainer = ramp.onPrimaryContainer,
        inversePrimary = accentRamp(accent, dark = true).primary,
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF006A60),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFA6F1E2),
        onTertiaryContainer = Color(0xFF00201C),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceTint = ramp.primary,
        inverseSurface = Color(0xFF322F35),
        inverseOnSurface = Color(0xFFF5EFF7),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFEF7FF),
        surfaceDim = Color(0xFFDED8E1),
        surfaceContainer = Color(0xFFF3EDF7),
        surfaceContainerHigh = Color(0xFFECE6F0),
        surfaceContainerHighest = Color(0xFFE6E0E9),
        surfaceContainerLow = Color(0xFFF7F2FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        primaryFixed = ramp.primaryContainer,
        primaryFixedDim = ramp.primaryContainer,
        onPrimaryFixed = ramp.onPrimaryContainer,
        onPrimaryFixedVariant = ramp.onPrimaryContainer,
        secondaryFixed = Color(0xFFE8DEF8),
        secondaryFixedDim = Color(0xFFE8DEF8),
        onSecondaryFixed = Color(0xFF1D192B),
        onSecondaryFixedVariant = Color(0xFF1D192B),
        tertiaryFixed = Color(0xFFA6F1E2),
        tertiaryFixedDim = Color(0xFFA6F1E2),
        onTertiaryFixed = Color(0xFF00201C),
        onTertiaryFixedVariant = Color(0xFF00201C)
    )
}

/** The bold custom fallback scheme for [accent] in dark mode. See [marbleFallbackLightScheme]. */
fun marbleFallbackDarkScheme(accent: FlatAccent): ColorScheme {
    val ramp = accentRamp(accent, dark = true)
    // Fixed roles stay on the light containers in both modes: that is what "fixed" means.
    val lightRamp = accentRamp(accent, dark = false)
    return darkColorScheme(
        primary = ramp.primary,
        onPrimary = ramp.onPrimary,
        primaryContainer = ramp.primaryContainer,
        onPrimaryContainer = ramp.onPrimaryContainer,
        inversePrimary = lightRamp.primary,
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFF80D8CB),
        onTertiary = Color(0xFF003730),
        tertiaryContainer = Color(0xFF005048),
        onTertiaryContainer = Color(0xFFA6F1E2),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceTint = ramp.primary,
        inverseSurface = Color(0xFFE6E0E9),
        inverseOnSurface = Color(0xFF322F35),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3B383E),
        surfaceDim = Color(0xFF141218),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainerLowest = Color(0xFF0F0D13),
        primaryFixed = lightRamp.primaryContainer,
        primaryFixedDim = lightRamp.primaryContainer,
        onPrimaryFixed = lightRamp.onPrimaryContainer,
        onPrimaryFixedVariant = lightRamp.onPrimaryContainer,
        secondaryFixed = Color(0xFFE8DEF8),
        secondaryFixedDim = Color(0xFFE8DEF8),
        onSecondaryFixed = Color(0xFF1D192B),
        onSecondaryFixedVariant = Color(0xFF1D192B),
        tertiaryFixed = Color(0xFFA6F1E2),
        tertiaryFixedDim = Color(0xFFA6F1E2),
        onTertiaryFixed = Color(0xFF00201C),
        onTertiaryFixedVariant = Color(0xFF00201C)
    )
}

/**
 * Semantic states the [ColorScheme] has no role for. Fixed (non-dynamic) hues by design:
 * success green and warning amber must read as themselves on any wallpaper, the same way
 * M3's error red does. Both modes are provided; the theme picks by darkness, never by accent.
 */
@Immutable
data class MarbleStatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

private val MarbleLightStatusColors = MarbleStatusColors(
    success = Color(0xFF146C2E),
    onSuccess = Color.White,
    successContainer = Color(0xFFB9F0C0),
    onSuccessContainer = Color(0xFF072711),
    warning = Color(0xFF7A5200),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFDE9E),
    onWarningContainer = Color(0xFF2C1E00)
)

private val MarbleDarkStatusColors = MarbleStatusColors(
    success = Color(0xFF8BD39B),
    onSuccess = Color(0xFF0B3B1E),
    successContainer = Color(0xFF0E5A28),
    onSuccessContainer = Color(0xFFB9F0C0),
    warning = Color(0xFFFFB945),
    onWarning = Color(0xFF442B00),
    warningContainer = Color(0xFF573C00),
    onWarningContainer = Color(0xFFFFDE9E)
)

fun marbleStatusColors(dark: Boolean): MarbleStatusColors =
    if (dark) MarbleDarkStatusColors else MarbleLightStatusColors

/** The fallback primary of [accent] in [dark] mode — for swatches and previews. JVM-safe. */
fun marbleAccentPrimary(accent: FlatAccent, dark: Boolean): Color =
    if (dark) marbleFallbackDarkScheme(accent).primary else marbleFallbackLightScheme(accent).primary

val LocalMarbleStatusColors = staticCompositionLocalOf { MarbleLightStatusColors }

// Semantic aliases on the scheme, so call sites read `colors.success` next to
// `colors.primary` with no second accessor. The receiver is the current scheme by
// construction; the values come from [LocalMarbleStatusColors].
val ColorScheme.success: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.success

val ColorScheme.onSuccess: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.onSuccess

val ColorScheme.successContainer: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.successContainer

val ColorScheme.onSuccessContainer: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.onSuccessContainer

val ColorScheme.warning: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.warning

val ColorScheme.onWarning: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.onWarning

val ColorScheme.warningContainer: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.warningContainer

val ColorScheme.onWarningContainer: Color
    @Composable @ReadOnlyComposable get() = LocalMarbleStatusColors.current.onWarningContainer
