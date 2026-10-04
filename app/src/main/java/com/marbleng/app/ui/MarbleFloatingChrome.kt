package com.marbleng.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb

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
    val shadow: Color,
    // MARBLE_FLOATING_ACTIONS_V210 — the four verbs a floating control can be, in this theme's
    // own colours, plus the two ink candidates scored against them.
    val actions: MarbleFloatActionTones,
    val inkLight: Color,
    val inkDark: Color
) {
    /**
     * The glyph ink for one action tone: whichever of this theme's two inks reads better on it,
     * pushed the rest of the way if even the better one is short of the 3:1 graphical floor.
     */
    fun inkOn(tone: Color): Color = marbleOnColor(tone, inkLight, inkDark)
}

@Composable
fun rememberMarbleFloatChrome(): MarbleFloatChrome = MarbleFloatChrome(
    surface = Aether.FloatSurface,
    border = Aether.FloatBorder,
    shadow = Aether.FloatShadow,
    actions = Aether.FloatActions,
    inkLight = Aether.FloatInkLight,
    inkDark = Aether.FloatInkDark
)

// =============================================================================
// MARBLE_FLOATING_ACTIONS_V210 — one floating control, four verbs, one palette per theme.
//
// The critique: the chrome around a floating button was theme-aware (V201 gave every theme its
// own body, hairline and shadow) but the *face* of the button was not. The connect disc read
// `Aether.Cyan`, the pause read `Aether.Danger` and the new ping read whatever the call site
// reached for — and in the dynamic theme `Danger` is a hard-coded brand red, so the one control
// a user touches most was the one control that ignored the wallpaper. Worse, the two halves of
// the split control were written independently: nothing guaranteed that "stop" and "measure"
// were distinguishable *in the theme the user actually runs*, which is exactly the question a
// thumb hovering over two discs needs answered.
//
// So an action is a token like any other. Each theme owns four of them — connect, securing,
// stop, measure — and the glyph on each one is scored, not assumed, from the same theme's ink
// pair. The whole set is built as packed ARGB ([MarbleFloatActionSet]) and only converted to
// Compose colours at the edge, so "every theme answers all four questions, and no two answers
// are the same" is a unit test instead of a promise.
// =============================================================================

/** The four verbs a floating control can be, as Compose colours. */
data class MarbleFloatActionTones(
    /** Armed: pressing it secures a tunnel. */
    val connect: Color,
    /** Working: a tunnel is being negotiated, or a measurement is running. */
    val securing: Color,
    /** Ending: stop a live tunnel. */
    val stop: Color,
    /** Measuring: ping the server this page is showing. */
    val measure: Color
)

/**
 * One theme's floating-action set, as packed ARGB so the whole guarantee is testable on a plain
 * JVM without a Compose runtime.
 *
 * [inkLight] and [inkDark] are the two *candidates* for a glyph painted on any of the four
 * tones, not assignments: [inkOn] scores both against the tone it is given and takes the
 * winner. A theme with a pale danger (the dark palette's #FF718B) therefore gets the dark ink
 * on its stop disc and the white one on its connect disc, from the same pair.
 */
data class MarbleFloatActionSet(
    val connect: Int,
    val securing: Int,
    val stop: Int,
    val measure: Int,
    val inkLight: Int,
    val inkDark: Int
) {
    /** The glyph ink for one action tone in this theme. */
    fun inkOn(tone: Int): Int = marbleOnColorArgb(tone, inkLight, inkDark)

    /** The four tones as Compose colours. */
    fun tones(): MarbleFloatActionTones = MarbleFloatActionTones(
        connect = Color(connect),
        securing = Color(securing),
        stop = Color(stop),
        measure = Color(measure)
    )
}

object MarbleFloatActions {
    /**
     * Daylight: the brand ramp at full strength on a near-white page.
     *
     * Connect is the electric blue the product is known by; securing drops a whole step down the
     * navy ramp so a working button is visibly *deeper* than an armed one (the V205 defect was a
     * busy state that held the armed state's value); stop and measure stay functional colours,
     * because a VPN must never dress "disconnect" in the brand hue.
     */
    val Light = MarbleFloatActionSet(
        connect = 0xFF0066CC.toInt(),
        securing = 0xFF001144.toInt(),
        stop = 0xFFE23D5B.toInt(),
        measure = 0xFF009A74.toInt(),
        inkLight = 0xFFFFFFFF.toInt(),
        inkDark = 0xFF000033.toInt()
    )

    /**
     * Pure black: the same four jobs on an OLED floor, where every tone has to carry its own
     * light. Connect rises to the bright blue (the electric step goes muddy against true black),
     * securing falls back to the electric step, and stop/measure take the palette's light roses
     * and mints so a filled disc separates from the void without a border.
     */
    val Dark = MarbleFloatActionSet(
        connect = 0xFF3399FF.toInt(),
        securing = 0xFF0066CC.toInt(),
        stop = 0xFFFF718B.toInt(),
        measure = 0xFF55D7B4.toInt(),
        inkLight = 0xFFF0F8FF.toInt(),
        inkDark = 0xFF000033.toInt()
    )

    /**
     * MARBLE_PHONE_DYNAMIC_THEME_V113 — the wallpaper's own answer to the same four questions.
     *
     * The four tones are the scheme's primary / secondary / error / tertiary: semantic roles a
     * phone palette already guarantees are mutually distinguishable, so the split control cannot
     * hand the user two discs of the same pastel. Error stays error — a "stop" that borrows a
     * wallpaper hue is a stop that stops looking like one.
     *
     * The ink pair is `onPrimary` / `onPrimaryContainer`, which is the one pairing Material You
     * guarantees to be readable on a primary-toned fill; they are ordered by luminance here so
     * [MarbleFloatActionSet.inkLight] really is the light one whichever way the scheme resolved.
     */
    fun dynamic(
        primary: Int,
        secondary: Int,
        tertiary: Int,
        error: Int,
        onPrimary: Int,
        onPrimaryContainer: Int
    ): MarbleFloatActionSet {
        val (light, dark) = if (marbleLuminanceArgb(onPrimary) >= marbleLuminanceArgb(onPrimaryContainer)) {
            onPrimary to onPrimaryContainer
        } else {
            onPrimaryContainer to onPrimary
        }
        return MarbleFloatActionSet(
            connect = primary,
            securing = secondary,
            stop = error,
            measure = tertiary,
            inkLight = light,
            inkDark = dark
        )
    }
}
