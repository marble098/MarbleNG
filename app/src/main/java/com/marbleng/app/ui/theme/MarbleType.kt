package com.marbleng.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.marbleng.app.R
import com.marbleng.app.model.AppFont
import com.marbleng.app.model.parseAppFont

// MARBLE_M3_EXPRESSIVE_V240 — one weight ladder, one family, two voices per slot.
//
// The ladder (kept verbatim from the previous design system: every bundled Vazirmatn face,
// every UI test pin) assigns one weight per *role*:
//
//   DISPLAY   800  the largest type on a surface — page titles, hero numerals
//   HEADLINE  700  headlines inside a page — hero status, sheet/dialog titles, verdicts
//   TITLE     600  row and card titles, section headers, button and chip labels
//   LABEL     500  small labels, captions, hints, unselected segmented/chip states
//   BODY      400  prose — subtitles, body copy, notes
//   NUMERIC   700  measured values (stat tiles, steppers, percentages)
//
// Expressive adds the second voice: every baseline slot has an emphasized twin one rung up
// (capped at DISPLAY), used where the research says emphasis pays — the one focal point per
// screen, key actions, verdicts. Selected/active states step one rung up from resting.
//
// Deliberately not shipped: Light/Thin (300 and below — thin Vazirmatn strokes at caption
// sizes are where Persian legibility dies first) and Black (900 — nothing here is a poster).

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
    Font(R.font.vazirmatn_extrabold, FontWeight(MarbleWeightSpec.DISPLAY))
)

internal fun resolveMarbleFontFamily(fontId: String, persianActive: Boolean): FontFamily {
    // Persian always speaks Vazirmatn: the system faces do not join Persian letterforms
    // with the same care, and every other weight in this file assumes the ladder's faces.
    if (persianActive) return VazirmatnFamily
    return when (parseAppFont(fontId)) {
        AppFont.VAZIR -> VazirmatnFamily
        AppFont.SYSTEM -> FontFamily.SansSerif
        AppFont.GOOGLE_SANS -> VazirmatnFamily
        AppFont.TIMES_NEW_ROMAN -> FontFamily.Serif
    }
}

private fun rung(weight: Int): FontWeight = FontWeight(weight)

/** One rung up the ladder, capped at DISPLAY — the expressive voice of a slot. */
private fun emphasize(weight: Int): FontWeight = when {
    weight >= MarbleWeightSpec.DISPLAY -> rung(MarbleWeightSpec.DISPLAY)
    weight >= MarbleWeightSpec.HEADLINE -> rung(MarbleWeightSpec.DISPLAY)
    weight >= MarbleWeightSpec.TITLE -> rung(MarbleWeightSpec.HEADLINE)
    weight >= MarbleWeightSpec.LABEL -> rung(MarbleWeightSpec.TITLE)
    else -> rung(MarbleWeightSpec.LABEL)
}

private fun TextStyle.emphasized(): TextStyle = copy(fontWeight = emphasize(fontWeight?.weight ?: 400))

fun buildMarbleTypography(family: FontFamily): Typography {
    val displayLarge = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.DISPLAY),
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    )
    val displayMedium = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.DISPLAY),
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp
    )
    val displaySmall = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.DISPLAY),
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp
    )
    val headlineLarge = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.DISPLAY),
        fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp
    )
    val headlineMedium = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.HEADLINE),
        fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp
    )
    val headlineSmall = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.HEADLINE),
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    )
    val titleLarge = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.HEADLINE),
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp
    )
    val titleMedium = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.TITLE),
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    )
    val titleSmall = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.TITLE),
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    )
    val bodyLarge = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.BODY),
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp
    )
    val bodyMedium = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.BODY),
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    )
    val bodySmall = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.BODY),
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    )
    val labelLarge = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.TITLE),
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    )
    val labelMedium = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.LABEL),
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    )
    val labelSmall = TextStyle(
        fontFamily = family, fontWeight = rung(MarbleWeightSpec.LABEL),
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    )
    return Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
        displayLargeEmphasized = displayLarge.emphasized(),
        displayMediumEmphasized = displayMedium.emphasized(),
        displaySmallEmphasized = displaySmall.emphasized(),
        headlineLargeEmphasized = headlineLarge.emphasized(),
        headlineMediumEmphasized = headlineMedium.emphasized(),
        headlineSmallEmphasized = headlineSmall.emphasized(),
        titleLargeEmphasized = titleLarge.emphasized(),
        titleMediumEmphasized = titleMedium.emphasized(),
        titleSmallEmphasized = titleSmall.emphasized(),
        bodyLargeEmphasized = bodyLarge.emphasized(),
        bodyMediumEmphasized = bodyMedium.emphasized(),
        bodySmallEmphasized = bodySmall.emphasized(),
        labelLargeEmphasized = labelLarge.emphasized(),
        labelMediumEmphasized = labelMedium.emphasized(),
        labelSmallEmphasized = labelSmall.emphasized()
    )
}
