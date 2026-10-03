package com.marbleng.app.ui

import kotlin.math.max
import kotlin.math.min

// =============================================================================
// MARBLE_FLOATING_CHROME_V201 — the contrast engine, without Compose.
//
// The chrome's colour decisions were split out of [MarbleFloatingChrome] into this file for
// one reason: this file imports nothing from Android, so the whole engine is unit-testable on
// a plain JVM. A contrast guarantee that cannot be tested is a design opinion with a number
// attached to it, and the defect being fixed here was exactly that.
//
// Everything works on packed `0xAARRGGBB` Ints, which is what a `Color` is underneath and
// what a palette constant is written as anyway — so the tests read in the same notation the
// palette does.
// =============================================================================

/** WCAG relative luminance of one packed colour, 0 (black) .. 1 (white). Alpha is ignored. */
fun marbleLuminanceArgb(argb: Int): Float {
    fun channel(raw: Int): Float {
        val normalised = raw / 255f
        val linear = if (normalised <= 0.04045f) {
            normalised / 12.92f
        } else {
            // java.lang.Math.pow, not kotlin.math.pow: the Float overload is not present in
            // this Kotlin version, and this file must stay JVM-testable without Compose.
            Math.pow(((normalised + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return linear.coerceIn(0f, 1f)
    }
    return 0.2126f * channel((argb ushr 16) and 0xFF) +
        0.7152f * channel((argb ushr 8) and 0xFF) +
        0.0722f * channel(argb and 0xFF)
}

/** Source-over of [foreground] onto [background], both packed. */
fun marbleCompositeArgb(foreground: Int, background: Int): Int {
    val alpha = ((foreground ushr 24) and 0xFF) / 255f
    if (alpha >= 1f) return foreground
    if (alpha <= 0f) return background
    val inverse = 1f - alpha
    val r = ((foreground ushr 16) and 0xFF) * alpha + ((background ushr 16) and 0xFF) * inverse
    val g = ((foreground ushr 8) and 0xFF) * alpha + ((background ushr 8) and 0xFF) * inverse
    val b = (foreground and 0xFF) * alpha + (background and 0xFF) * inverse
    return (0xFF shl 24) or
        (r.toInt().coerceIn(0, 255) shl 16) or
        (g.toInt().coerceIn(0, 255) shl 8) or
        b.toInt().coerceIn(0, 255)
}

/** Set the alpha channel of a packed colour. */
fun marbleWithAlphaArgb(argb: Int, alpha: Float): Int =
    (argb and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24)

/** Alpha of a packed colour, 0..1. */
fun marbleAlphaOf(argb: Int): Float = ((argb ushr 24) and 0xFF) / 255f

/** RGB of a packed colour with alpha forced opaque. */
fun marbleOpaqueArgb(argb: Int): Int = argb or (0xFF shl 24)

/**
 * WCAG contrast ratio between two packed colours, 1..21.
 *
 * Both are flattened onto something opaque first: the chrome stacks translucent washes, and a
 * ratio computed against a translucent colour is a number about nothing.
 */
fun marbleContrastRatioArgb(foreground: Int, background: Int): Float {
    val flattenedBg = marbleCompositeArgb(background, 0xFFFFFFFF.toInt())
    val flattenedFg = marbleCompositeArgb(foreground, flattenedBg)
    val l1 = marbleLuminanceArgb(flattenedFg)
    val l2 = marbleLuminanceArgb(flattenedBg)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Linear blend of two packed colours; [amount] 0 is [from], 1 is [to]. */
fun marbleLerpArgb(from: Int, to: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    val inverse = 1f - t
    val a = marbleAlphaOf(from) * inverse + marbleAlphaOf(to) * t
    val r = ((from ushr 16) and 0xFF) * inverse + ((to ushr 16) and 0xFF) * t
    val g = ((from ushr 8) and 0xFF) * inverse + ((to ushr 8) and 0xFF) * t
    val b = (from and 0xFF) * inverse + (to and 0xFF) * t
    return ((a * 255f).toInt().coerceIn(0, 255) shl 24) or
        (r.toInt().coerceIn(0, 255) shl 16) or
        (g.toInt().coerceIn(0, 255) shl 8) or
        b.toInt().coerceIn(0, 255)
}

/**
 * MARBLE_FLOATING_CHROME_V201 — an accent that is guaranteed readable on a background.
 *
 * The accent is what gives a selected tab, a status pill or a floating action button its
 * identity, so it is never replaced outright: it is pushed along the shortest path toward
 * whichever endpoint (black or white) the background demands until it clears [minimumRatio].
 * A pastel Material You primary lands a few steps darker and stays recognisably itself; the
 * brand electric blue on a light wash is returned untouched because it already passes.
 */
fun marbleReadableOnArgb(accent: Int, background: Int, minimumRatio: Float = 4.5f): Int {
    if (marbleContrastRatioArgb(accent, background) >= minimumRatio) return accent
    val flattenedBackground = marbleCompositeArgb(background, 0xFFFFFFFF.toInt())
    val target = if (marbleLuminanceArgb(flattenedBackground) > 0.5f) {
        marbleOpaqueArgb(0x00000000)
    } else {
        0xFFFFFFFF.toInt()
    }
    // Twelve steps is plenty: the worst case (a mid pastel on a mid wash) resolves in three or
    // four, and stopping early keeps the accent's identity instead of flattening it to ink.
    for (step in 1..12) {
        val blended = marbleLerpArgb(accent, target, step / 12f)
        if (marbleContrastRatioArgb(blended, background) >= minimumRatio) return blended
    }
    return target
}

/**
 * MARBLE_FLOATING_CHROME_V201 — the ink that goes *on* a solid accent: the glyph of a floating
 * action button, the caption of a filled chip, the check inside a status disc.
 *
 * Both candidates are scored rather than one being assumed. The product hard-coded
 * `Color.White` for this, which is fine on the brand electric blue (5.6:1) and fails on the
 * brand bright blue the *dark* theme uses for the same role: #FFFFFF on #3399FF is 2.94:1,
 * under the 3:1 floor for graphical objects. The fix cannot be "always use the dark ink"
 * either — that would break every light accent. So the better of the two wins, and only if
 * even the better one is short is it pushed the rest of the way.
 *
 * The dark ink is not black: on the deep navy this palette uses, pure black reads as a hole
 * punched in the button. #000033 is the palette's own darkest ink.
 */
fun marbleOnColorArgb(
    accent: Int,
    lightInk: Int = 0xFFFFFFFF.toInt(),
    darkInk: Int = 0xFF000033.toInt(),
    minimumRatio: Float = 3.0f
): Int {
    val flattened = marbleCompositeArgb(accent, 0xFFFFFFFF.toInt())
    val onLight = marbleContrastRatioArgb(lightInk, flattened)
    val onDark = marbleContrastRatioArgb(darkInk, flattened)
    val best = if (onLight >= onDark) lightInk else darkInk
    return if (max(onLight, onDark) >= minimumRatio) {
        best
    } else {
        marbleReadableOnArgb(best, flattened, minimumRatio)
    }
}

/**
 * Alpha of the shadow a floating element at [elevationDp] casts, in the system's own range.
 *
 * Not `elevation / 24`: the chrome sits at 3..8 dp and the old flat `.16f` gave a 3 dp bar and
 * an 8 dp dock the same shadow, so the dock read as a sticker instead of a layer.
 */
fun marbleFloatShadowAlpha(elevationDp: Float): Float =
    (0.10f + elevationDp / 140f).coerceIn(0.10f, 0.34f)

/** Ambient and spot alphas of one floating element's shadow, from one elevation. */
fun marbleFloatShadowPair(elevationDp: Float): Pair<Float, Float> {
    val spot = marbleFloatShadowAlpha(elevationDp)
    return (spot * 0.72f) to spot
}
