package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// =============================================================================
// MARBLE_FLOATING_CHROME_V201
//
// A critique of the floating chrome this product shipped, because the fix is only meaningful
// next to the defect:
//
//  1. **Contrast was assumed, never computed.** Every floating surface built its own colour
//     the same way: take an accent, `copy(alpha = .24f)` for the pill, and paint the accent
//     itself — the identical hue, at 100 % — as the label on top of it. That works when the
//     accent happens to be the brand electric blue (#0066CC on a 24 % wash of itself is about
//     3.9:1, passable for a large glyph and marginal for a 12 sp caption) and stops working
//     the moment it is not: with Material You's wallpaper palette, `primary` is routinely a
//     pastel, and a pastel at 24 % under the same pastel at 100 % is a pill whose caption you
//     cannot read. The theme had no way to know, because nothing measured it.
//
//  2. **The shadow was hard-coded black.** `Color.Black.copy(alpha = .16f)` on a navy/ice
//     light surface is not a shadow, it is a smudge: every hue in this system is blue, and a
//     neutral shadow on an ice page reads grey-brown. A floating object's shadow colour is a
//     palette decision like any other.
//
//  3. **The light bar had no body.** `#FFFFFF @ .94` over a `#F4F8FD` page is very close to
//     the page, and the pure-white cards scrolling underneath are *brighter* than it — so on
//     the light theme the bar read as a hole the content fell into rather than a layer the
//     content passed behind. On AMOLED the opposite was true: `#000` on `#000` gave the bar
//     no body at all.
//
//  4. **Selection was a wash, not a state.** A 24 % tint plus a 34 % rim is a *hint*. A
//     navigation bar has to answer "where am I?" from across the room, and a hint does not.
//
// What follows is the replacement: a contrast engine the chrome actually consults (in
// [MarbleContrast], deliberately free of Compose so it can be unit tested), palette tokens
// for a floating object's body/border/shadow, and a selected-state recipe that is a lit
// container rather than a tint.
// =============================================================================

/** WCAG relative luminance of an opaque-looking colour, 0 (black) .. 1 (white). */
fun marbleLuminance(color: Color): Float = marbleLuminanceArgb(color.toArgb())

/**
 * WCAG contrast ratio between two colours, 1..21.
 *
 * Both colours are flattened onto an opaque backdrop first: the chrome stacks translucent
 * washes, and a ratio computed against a translucent colour is a number about nothing.
 */
fun marbleContrastRatio(foreground: Color, background: Color): Float =
    marbleContrastRatioArgb(foreground.toArgb(), background.toArgb())

/** See [marbleReadableOnArgb]. */
fun marbleReadableOn(
    accent: Color,
    background: Color,
    minimumRatio: Float = 4.5f
): Color = Color(marbleReadableOnArgb(accent.toArgb(), background.toArgb(), minimumRatio))

/** See [marbleOnColorArgb]. */
fun marbleOnColor(
    accent: Color,
    lightInk: Color = Color.White,
    darkInk: Color = Color(0xFF000033),
    minimumRatio: Float = 3.0f
): Color = Color(
    marbleOnColorArgb(accent.toArgb(), lightInk.toArgb(), darkInk.toArgb(), minimumRatio)
)

/**
 * The body of a selected floating pill: the accent at [fillAlpha] over the chrome's own
 * surface, so the fill is a real container rather than a tint floating over nothing.
 */
fun marblePillFill(accent: Color, surface: Color, fillAlpha: Float = 0.22f): Color =
    accent.copy(alpha = fillAlpha).compositeOver(surface)

/**
 * The hairline of a selected floating pill: above the fill, below the content.
 *
 * It is drawn in the accent but pushed toward the readable endpoint too, because a rim is a
 * 1 dp line — the one place where a marginal ratio is most visible.
 */
fun marblePillRim(accent: Color, surface: Color, rimAlpha: Float = 0.42f): Color =
    marbleReadableOn(accent, surface, 1.6f).copy(alpha = rimAlpha).compositeOver(surface)

/** The chrome token set, read once per composition instead of per call site. */
data class MarbleFloatChrome(
    val surface: Color,
    val border: Color,
    val shadow: Color
)

@Composable
fun rememberMarbleFloatChrome(): MarbleFloatChrome = MarbleFloatChrome(
    surface = Aether.FloatSurface,
    border = Aether.FloatBorder,
    shadow = Aether.FloatShadow
)
