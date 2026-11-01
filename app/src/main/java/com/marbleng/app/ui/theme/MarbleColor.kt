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
import com.marbleng.app.model.MarblePalette

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

    // MARBLE_HOME_STUDIO_V249 — cyan: the hue between OCEAN's blue and EMERALD's green that no
    // seed covered. Its light primary is a dark teal (AA on white), its dark primary a bright
    // aqua, and the two are further from OCEAN's pair than the swatch row's 48dp circles are.
    FlatAccent.SKY -> if (dark) {
        AccentRamp(
            primary = Color(0xFF6CD9F2),
            onPrimary = Color(0xFF003541),
            primaryContainer = Color(0xFF004D5D),
            onPrimaryContainer = Color(0xFFB4EAFF)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF00687A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA8EDFF),
            onPrimaryContainer = Color(0xFF001F26)
        )
    }

    // Magenta: ROSE is a pink that leans red, VIOLET a purple that leans blue, and neither is the
    // orchid between them.
    FlatAccent.PLUM -> if (dark) {
        AccentRamp(
            primary = Color(0xFFF5A8E8),
            onPrimary = Color(0xFF570068),
            primaryContainer = Color(0xFF6E2379),
            onPrimaryContainer = Color(0xFFFFD6F8)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF86368F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFD6F8),
            onPrimaryContainer = Color(0xFF330039)
        )
    }

    // Olive: EMERALD is a garden green, AMBER a warm gold, and olive is the earthy yellow-green
    // that sits between them without being either.
    FlatAccent.MOSS -> if (dark) {
        AccentRamp(
            primary = Color(0xFFC7D87F),
            onPrimary = Color(0xFF303800),
            primaryContainer = Color(0xFF46510A),
            onPrimaryContainer = Color(0xFFE3F49A)
        )
    } else {
        AccentRamp(
            primary = Color(0xFF5E6B1E),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE3F49A),
            onPrimaryContainer = Color(0xFF1B2000)
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
 * MARBLE_HOME_STUDIO_V249 — the "true black" pass, for OLED screens and for the theme id `amoled`
 * that [resolveMarbleDarkMode] already accepted but nothing could draw.
 *
 * It is a *pass over* a dark scheme rather than a fourth scheme, for one reason: the accent ramp,
 * the error ramp and every container tone are contrast-proven in [marbleFallbackDarkScheme] (and
 * chosen by the wallpaper when dynamic colour is on), and a hand-written black scheme would have to
 * re-prove all of them. So this changes only what has to change — the page and the lowest surfaces
 * go to `Color.Black`, which on an OLED panel is a pixel that is off — and leaves the lifted
 * containers exactly where the dark ladder put them, so a card still reads as a card. The hierarchy
 * survives because it is carried by the containers, not by the background.
 *
 * Applying it to a light scheme would paint black text on black, so the caller only ever passes a
 * dark scheme; [isTrueBlack] keeps that decision in one place.
 */
fun marbleTrueBlackSurfaces(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A2A30),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0E),
    surfaceContainer = Color(0xFF151519),
    surfaceContainerHigh = Color(0xFF1E1E23),
    surfaceContainerHighest = Color(0xFF27272D),
    outlineVariant = Color(0xFF2C2C31),
    scrim = Color.Black
)

/** `amoled`/`oled` in the theme id means "the darkest thing this app can draw". */
fun isTrueBlack(themeId: String): Boolean = when (themeId.trim().lowercase()) {
    "amoled", "oled", "trueblack", "true-black", "black" -> true
    else -> false
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

// ---------------------------------------------------------------------------------------------
// MARBLE_PALETTE_STUDIO_V258 — three whole-scheme colour themes.
// ---------------------------------------------------------------------------------------------
//
// An accent seeds the primary ramp and nothing else, which is why nine swatches still read as one
// app in nine blues. A palette replaces every *expressive* role at once — primary, secondary,
// tertiary, their containers, the four surface container tones, the outline pair and the inverse
// pair — so the whole product, not just its buttons, changes temperature.
//
// Every value below is a designed pair (a light ramp and its dark twin), not a computed tint: the
// dark rows are the light rows re-lit, so switching the system to dark keeps the palette's identity
// instead of washing it to grey. Error ramps stay M3's own, because a failure is a failure in any
// palette; scrim stays black for the same reason.

/**
 * The full scheme of [palette] in [dark] mode.
 *
 * [MarblePalette.CLASSIC] deliberately falls back to the accent-seeded scheme, because "Classic"
 * means *the accent you picked*, not a fourth palette.
 */
fun marblePaletteScheme(palette: MarblePalette, dark: Boolean): ColorScheme =
    when (palette) {
        MarblePalette.CLASSIC ->
            if (dark) marbleFallbackDarkScheme(FlatAccent.OCEAN) else marbleFallbackLightScheme(FlatAccent.OCEAN)

        // ── Aurora: arctic teal on the primary, a cold slate secondary and a dusk-violet tertiary.
        //    Cool, luminous and quiet; the palette for a page that is read at night.
        MarblePalette.AURORA -> if (dark) {
            darkColorScheme().copy(
                primary = Color(0xFF82D5DB),
                onPrimary = Color(0xFF00363A),
                primaryContainer = Color(0xFF004F53),
                onPrimaryContainer = Color(0xFF9DF2F8),
                inversePrimary = Color(0xFF00696E),
                secondary = Color(0xFFB1CADD),
                onSecondary = Color(0xFF1C3444),
                secondaryContainer = Color(0xFF334B5B),
                onSecondaryContainer = Color(0xFFCEE6FA),
                tertiary = Color(0xFFC0C2F5),
                onTertiary = Color(0xFF272B54),
                tertiaryContainer = Color(0xFF3E4273),
                onTertiaryContainer = Color(0xFFE1E0F9),
                background = Color(0xFF0E1415),
                onBackground = Color(0xFFDDE3E4),
                surface = Color(0xFF0E1415),
                onSurface = Color(0xFFDDE3E4),
                surfaceVariant = Color(0xFF3F494A),
                onSurfaceVariant = Color(0xFFBEC8C9),
                surfaceTint = Color(0xFF82D5DB),
                inverseSurface = Color(0xFFDDE3E4),
                inverseOnSurface = Color(0xFF2A3132),
                outline = Color(0xFF889394),
                outlineVariant = Color(0xFF3F494A),
                surfaceBright = Color(0xFF343A3B),
                surfaceDim = Color(0xFF0E1415),
                surfaceContainer = Color(0xFF1A2122),
                surfaceContainerHigh = Color(0xFF242B2C),
                surfaceContainerHighest = Color(0xFF2F3637),
                surfaceContainerLow = Color(0xFF161D1E),
                surfaceContainerLowest = Color(0xFF080D0E),
                primaryFixed = Color(0xFF9FF0F5),
                primaryFixedDim = Color(0xFF82D5DB),
                onPrimaryFixed = Color(0xFF00201F),
                onPrimaryFixedVariant = Color(0xFF004F53),
                secondaryFixed = Color(0xFFCEE6FA),
                secondaryFixedDim = Color(0xFFB1CADD),
                onSecondaryFixed = Color(0xFF051E2C),
                onSecondaryFixedVariant = Color(0xFF334B5B),
                tertiaryFixed = Color(0xFFE1E0F9),
                tertiaryFixedDim = Color(0xFFC0C2F5),
                onTertiaryFixed = Color(0xFF13163C),
                onTertiaryFixedVariant = Color(0xFF3E4273)
            )
        } else {
            lightColorScheme().copy(
                primary = Color(0xFF00696E),
                onPrimary = Color.White,
                primaryContainer = Color(0xFF9FF0F5),
                onPrimaryContainer = Color(0xFF00201F),
                inversePrimary = Color(0xFF82D5DB),
                secondary = Color(0xFF4A6273),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCEE6FA),
                onSecondaryContainer = Color(0xFF051E2C),
                tertiary = Color(0xFF565D92),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE1E0F9),
                onTertiaryContainer = Color(0xFF13163C),
                background = Color(0xFFF5FBFC),
                onBackground = Color(0xFF171D1E),
                surface = Color(0xFFF5FBFC),
                onSurface = Color(0xFF171D1E),
                surfaceVariant = Color(0xFFDAE4E6),
                onSurfaceVariant = Color(0xFF3F494A),
                surfaceTint = Color(0xFF00696E),
                inverseSurface = Color(0xFF2A3132),
                inverseOnSurface = Color(0xFFEBF1F2),
                outline = Color(0xFF6F797A),
                outlineVariant = Color(0xFFC0C9CA),
                surfaceBright = Color(0xFFF5FBFC),
                surfaceDim = Color(0xFFD5DADB),
                surfaceContainer = Color(0xFFE9EFF0),
                surfaceContainerHigh = Color(0xFFE3E9EA),
                surfaceContainerHighest = Color(0xFFDDE3E4),
                surfaceContainerLow = Color(0xFFEFF5F6),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                primaryFixed = Color(0xFF9FF0F5),
                primaryFixedDim = Color(0xFF82D5DB),
                onPrimaryFixed = Color(0xFF00201F),
                onPrimaryFixedVariant = Color(0xFF004F53),
                secondaryFixed = Color(0xFFCEE6FA),
                secondaryFixedDim = Color(0xFFB1CADD),
                onSecondaryFixed = Color(0xFF051E2C),
                onSecondaryFixedVariant = Color(0xFF334B5B),
                tertiaryFixed = Color(0xFFE1E0F9),
                tertiaryFixedDim = Color(0xFFC0C2F5),
                onTertiaryFixed = Color(0xFF13163C),
                onTertiaryFixedVariant = Color(0xFF3E4273)
            )
        }

        // ── Ember: a coral primary, a plum secondary and a lamp-gold tertiary on warm paper.
        //    The palette that makes a phone feel lit rather than lit *up*.
        MarblePalette.EMBER -> if (dark) {
            darkColorScheme().copy(
                primary = Color(0xFFFFB59B),
                onPrimary = Color(0xFF5B1A00),
                primaryContainer = Color(0xFF7E2E12),
                onPrimaryContainer = Color(0xFFFFDBD2),
                inversePrimary = Color(0xFFA73A21),
                secondary = Color(0xFFDABDE8),
                onSecondary = Color(0xFF3B2947),
                secondaryContainer = Color(0xFF52405E),
                onSecondaryContainer = Color(0xFFF3DAFF),
                tertiary = Color(0xFFE4C46A),
                onTertiary = Color(0xFF3E2E00),
                tertiaryContainer = Color(0xFF584400),
                onTertiaryContainer = Color(0xFFFFE08B),
                background = Color(0xFF191110),
                onBackground = Color(0xFFF0DED8),
                surface = Color(0xFF191110),
                onSurface = Color(0xFFF0DED8),
                surfaceVariant = Color(0xFF53433F),
                onSurfaceVariant = Color(0xFFD8C2BC),
                surfaceTint = Color(0xFFFFB59B),
                inverseSurface = Color(0xFFF0DED8),
                inverseOnSurface = Color(0xFF3A2F2C),
                outline = Color(0xFFA08C87),
                outlineVariant = Color(0xFF53433F),
                surfaceBright = Color(0xFF413633),
                surfaceDim = Color(0xFF191110),
                surfaceContainer = Color(0xFF261D1B),
                surfaceContainerHigh = Color(0xFF312725),
                surfaceContainerHighest = Color(0xFF3C322F),
                surfaceContainerLow = Color(0xFF221917),
                surfaceContainerLowest = Color(0xFF120C0B),
                primaryFixed = Color(0xFFFFDBD2),
                primaryFixedDim = Color(0xFFFFB59B),
                onPrimaryFixed = Color(0xFF3D0700),
                onPrimaryFixedVariant = Color(0xFF7E2E12),
                secondaryFixed = Color(0xFFF3DAFF),
                secondaryFixedDim = Color(0xFFDABDE8),
                onSecondaryFixed = Color(0xFF261437),
                onSecondaryFixedVariant = Color(0xFF52405E),
                tertiaryFixed = Color(0xFFFFE08B),
                tertiaryFixedDim = Color(0xFFE4C46A),
                onTertiaryFixed = Color(0xFF241A00),
                onTertiaryFixedVariant = Color(0xFF584400)
            )
        } else {
            lightColorScheme().copy(
                primary = Color(0xFFA73A21),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFDBD2),
                onPrimaryContainer = Color(0xFF3D0700),
                inversePrimary = Color(0xFFFFB59B),
                secondary = Color(0xFF6B5778),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF3DAFF),
                onSecondaryContainer = Color(0xFF261437),
                tertiary = Color(0xFF765B00),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFE08B),
                onTertiaryContainer = Color(0xFF241A00),
                background = Color(0xFFFFF8F5),
                onBackground = Color(0xFF221A18),
                surface = Color(0xFFFFF8F5),
                onSurface = Color(0xFF221A18),
                surfaceVariant = Color(0xFFF4DED8),
                onSurfaceVariant = Color(0xFF53433F),
                surfaceTint = Color(0xFFA73A21),
                inverseSurface = Color(0xFF3A2F2C),
                inverseOnSurface = Color(0xFFFFEDE7),
                outline = Color(0xFF85736E),
                outlineVariant = Color(0xFFD8C2BC),
                surfaceBright = Color(0xFFFFF8F5),
                surfaceDim = Color(0xFFE8D6D0),
                surfaceContainer = Color(0xFFFCEAE4),
                surfaceContainerHigh = Color(0xFFF6E4DE),
                surfaceContainerHighest = Color(0xFFF0DED8),
                surfaceContainerLow = Color(0xFFFFF1EC),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                primaryFixed = Color(0xFFFFDBD2),
                primaryFixedDim = Color(0xFFFFB59B),
                onPrimaryFixed = Color(0xFF3D0700),
                onPrimaryFixedVariant = Color(0xFF7E2E12),
                secondaryFixed = Color(0xFFF3DAFF),
                secondaryFixedDim = Color(0xFFDABDE8),
                onSecondaryFixed = Color(0xFF261437),
                onSecondaryFixedVariant = Color(0xFF52405E),
                tertiaryFixed = Color(0xFFFFE08B),
                tertiaryFixedDim = Color(0xFFE4C46A),
                onTertiaryFixed = Color(0xFF241A00),
                onTertiaryFixedVariant = Color(0xFF584400)
            )
        }

        // ── Neon Prism: magenta, acid lime and signal cyan on glass that is almost black even in
        //    daylight. The loudest palette, and the only one whose *light* scheme is a bright room
        //    with the same three neons in it.
        MarblePalette.PRISM -> if (dark) {
            darkColorScheme().copy(
                primary = Color(0xFFFFA9DC),
                onPrimary = Color(0xFF5E0042),
                primaryContainer = Color(0xFF83005C),
                onPrimaryContainer = Color(0xFFFFD9EF),
                inversePrimary = Color(0xFFA6007A),
                secondary = Color(0xFFBBD97C),
                onSecondary = Color(0xFF283500),
                secondaryContainer = Color(0xFF3C4E00),
                onSecondaryContainer = Color(0xFFD9F58F),
                tertiary = Color(0xFF93CCFF),
                onTertiary = Color(0xFF003354),
                tertiaryContainer = Color(0xFF004A76),
                onTertiaryContainer = Color(0xFFCBE6FF),
                background = Color(0xFF100B10),
                onBackground = Color(0xFFECDFE6),
                surface = Color(0xFF100B10),
                onSurface = Color(0xFFECDFE6),
                surfaceVariant = Color(0xFF51434B),
                onSurfaceVariant = Color(0xFFD4C2CC),
                surfaceTint = Color(0xFFFFA9DC),
                inverseSurface = Color(0xFFECDFE6),
                inverseOnSurface = Color(0xFF352F33),
                outline = Color(0xFF9C8C96),
                outlineVariant = Color(0xFF51434B),
                surfaceBright = Color(0xFF382F37),
                surfaceDim = Color(0xFF100B10),
                surfaceContainer = Color(0xFF1D151D),
                surfaceContainerHigh = Color(0xFF281F27),
                surfaceContainerHighest = Color(0xFF332A32),
                surfaceContainerLow = Color(0xFF191219),
                surfaceContainerLowest = Color(0xFF0A060A),
                primaryFixed = Color(0xFFFFD9EF),
                primaryFixedDim = Color(0xFFFFA9DC),
                onPrimaryFixed = Color(0xFF3B0029),
                onPrimaryFixedVariant = Color(0xFF83005C),
                secondaryFixed = Color(0xFFD9F58F),
                secondaryFixedDim = Color(0xFFBBD97C),
                onSecondaryFixed = Color(0xFF131F00),
                onSecondaryFixedVariant = Color(0xFF3C4E00),
                tertiaryFixed = Color(0xFFCBE6FF),
                tertiaryFixedDim = Color(0xFF93CCFF),
                onTertiaryFixed = Color(0xFF001D34),
                onTertiaryFixedVariant = Color(0xFF004A76)
            )
        } else {
            lightColorScheme().copy(
                primary = Color(0xFFA6007A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFD9EF),
                onPrimaryContainer = Color(0xFF3B0029),
                inversePrimary = Color(0xFFFFA9DC),
                secondary = Color(0xFF4C6B00),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFD9F58F),
                onSecondaryContainer = Color(0xFF131F00),
                tertiary = Color(0xFF00639B),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFCBE6FF),
                onTertiaryContainer = Color(0xFF001D34),
                background = Color(0xFFFFF7FB),
                onBackground = Color(0xFF201A1E),
                surface = Color(0xFFFFF7FB),
                onSurface = Color(0xFF201A1E),
                surfaceVariant = Color(0xFFF0DEE8),
                onSurfaceVariant = Color(0xFF51434B),
                surfaceTint = Color(0xFFA6007A),
                inverseSurface = Color(0xFF352F33),
                inverseOnSurface = Color(0xFFFAEEF4),
                outline = Color(0xFF83737D),
                outlineVariant = Color(0xFFD4C2CC),
                surfaceBright = Color(0xFFFFF7FB),
                surfaceDim = Color(0xFFE4D6DE),
                surfaceContainer = Color(0xFFF8EAF2),
                surfaceContainerHigh = Color(0xFFF2E4EC),
                surfaceContainerHighest = Color(0xFFECDFE6),
                surfaceContainerLow = Color(0xFFFDF0F7),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                primaryFixed = Color(0xFFFFD9EF),
                primaryFixedDim = Color(0xFFFFA9DC),
                onPrimaryFixed = Color(0xFF3B0029),
                onPrimaryFixedVariant = Color(0xFF83005C),
                secondaryFixed = Color(0xFFD9F58F),
                secondaryFixedDim = Color(0xFFBBD97C),
                onSecondaryFixed = Color(0xFF131F00),
                onSecondaryFixedVariant = Color(0xFF3C4E00),
                tertiaryFixed = Color(0xFFCBE6FF),
                tertiaryFixedDim = Color(0xFF93CCFF),
                onTertiaryFixed = Color(0xFF001D34),
                onTertiaryFixedVariant = Color(0xFF004A76)
            )
        }
    }

/**
 * A palette's own three-colour signature, for swatches and the About hero: primary, secondary and
 * tertiary of the live mode, in the order the interface paints them.
 */
fun marblePaletteSignature(palette: MarblePalette, dark: Boolean): List<Color> {
    val scheme = marblePaletteScheme(palette, dark)
    return listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
}

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
