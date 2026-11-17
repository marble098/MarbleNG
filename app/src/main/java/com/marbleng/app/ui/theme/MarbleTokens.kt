package com.marbleng.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// MARBLE_ONE_LANGUAGE_V281 — the design tokens every page reads.
//
// Before this pass the app had one *theme* (colour, type, shape, motion) and forty opinions about
// geometry: fifteen different corner radii, twelve different air gaps, eleven different strengths
// of "a tone behind a value", and eleven private functions that each decided which colour role
// means good, which means warning and which means "nothing measured yet". Every one of those
// decisions was reasonable on its own page and wrong next to its neighbour, which is what made the
// product read as several apps wearing one colour scheme.
//
// This file is the single source for the four scales those decisions belong to — space, corner,
// wash and tone — plus the grid every page sits on. They are plain primitives (no Compose types
// beyond `Color`), so a JVM unit test can assert a design rule directly, and `tools/design-system-
// conformance.py` fails the build when a page invents a value that is not on a scale.
//
// The rule for anyone adding a screen: **ask this file for a number, never write one.** If the
// number you need is not here, the scale is missing a rung — add the rung, name it, and let every
// other page see it too.

/**
 * The one spacing scale: a 2 dp optical grid whose structural steps are 4 dp.
 *
 * Every gap in the product is one of these. Odd values (1, 3, 5, 7 dp) were retired by this pass:
 * an odd gap is half a pixel on a 2.75x screen, so it read as "slightly different" rather than as a
 * deliberate step, and it made two tiles that were meant to match disagree by a hair.
 *
 * The rungs are named for the job they do, not for their size, so a call site says why it has air:
 * [HAIR] is a seam inside one drawn mark, [ROW_GAP] is the air that replaces a hairline between two
 * rows of one group, [LG] is a card's own inner padding and [XL] is the screen gutter.
 */
object MarbleSpace {
    /** A seam inside one drawn mark: two bars of a level meter, the lobes of a polygon. */
    const val HAIR: Int = 2

    /** Air between a glyph and the label beside it. */
    const val XS: Int = 4

    /** The air that separates two rows of one tonal group — the retired hairline's replacement. */
    const val ROW_GAP: Int = 6

    /** Air between two objects that belong to one row. */
    const val SM: Int = 8

    /** A tile's own inner air: the gap between its label and its value. */
    const val INNER: Int = 10

    /** Air between two blocks inside one card. */
    const val MD: Int = 12

    /** A compact tile's inner padding. */
    const val TILE: Int = 14

    /** A card's inner padding ([MarbleSpec.CARD_PADDING_DP]). */
    const val LG: Int = 16

    /** The screen gutter and a generous card padding ([MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP]). */
    const val XL: Int = 20

    /** Air around a focal surface: a hero, a sheet's own content. */
    const val XXL: Int = 24

    /** Air between two page sections on a wide surface. */
    const val BLOCK: Int = 32

    /** Every rung, in ascending order. The conformance gate reads this list. */
    val SCALE: List<Int> = listOf(HAIR, XS, ROW_GAP, SM, INNER, MD, TILE, LG, XL, XXL, BLOCK)

    /** The structural step of the scale; the optical half-step is `STRUCTURAL_STEP / 2`. */
    const val STRUCTURAL_STEP: Int = 4

    fun isOnScale(value: Int): Boolean = value in SCALE

    /**
     * The rung nearest a value — how every off-scale gap in the product was snapped. A value exactly
     * between two rungs (9 dp between [SM] and [INNER], 18 dp between [LG] and [XL]) goes *up*,
     * because the two values that tie are always an optical gap and a structural one, and losing an
     * optical gap is invisible while losing a structural one collapses a group.
     */
    fun nearest(value: Int): Int = nearestRung(SCALE, value)

    /**
     * The rung an arbitrary value was snapped to when this pass normalised the product, with ties
     * resolving upwards: 3 dp became [XS], 5 dp became [ROW_GAP], 7 dp became [SM]. Ties go up
     * because the two values that tie are always an optical gap and a structural one, and losing an
     * optical gap is invisible while losing a structural one collapses a group.
     */
    fun snapUp(value: Int): Int = SCALE.firstOrNull { it >= value } ?: BLOCK
}

/**
 * The one corner scale. It is the expressive shape set the theme already ships, named for the role
 * each radius plays, so a page asks for "the tile radius" instead of picking a number that happens
 * to sit near it.
 *
 * Nothing below [XS] exists: Material 3 Expressive corners are large and soft, and a 4 dp corner on
 * a tonal card reads as a manufacturing defect rather than as a shape decision.
 */
object MarbleRadius {
    /** The smallest corner in the product: a hairline chip, a skeleton line. */
    const val XS: Int = 8

    /** A small badge or a short chip. */
    const val SM: Int = 12

    /** A row's leading icon well ([MarbleSpec.ROW_ICON_CORNER_DP]). */
    const val ROW_ICON: Int = 14

    /** Buttons, fields and segmented controls ([MarbleControlShape]). */
    const val CONTROL: Int = 16

    /** A stat tile or any small reading surface. */
    const val TILE: Int = 20

    /** Cards and list-row containers ([MarbleSpec.CARD_CORNER_DP]). */
    const val CARD: Int = 24

    /** A group that holds several rows, and a sheet's top edge ([MarbleSpec.GROUP_CORNER_DP]). */
    const val GROUP: Int = 32

    /** A hero panel. */
    const val HERO: Int = 36

    /** A full-screen state surface. */
    const val STATE: Int = 40

    /** The largest corner: an orbit lens, a display mark. */
    const val DISPLAY: Int = 48

    /** Every rung, ascending. The conformance gate reads this list. */
    val SCALE: List<Int> = listOf(XS, SM, ROW_ICON, CONTROL, TILE, CARD, GROUP, HERO, STATE, DISPLAY)

    /** A capsule: `RoundedCornerShape(percent = PILL_PERCENT)` at any size. */
    const val PILL_PERCENT: Int = 50

    fun isOnScale(value: Int): Boolean = value in SCALE

    /** The rung nearest a value, ties resolving upwards — see [MarbleSpace.nearest]. */
    fun nearest(value: Int): Int = nearestRung(SCALE, value)
}

/**
 * The one elevation ladder.
 *
 * Four shadows were in use (6, 8, 10 and 12 dp) and a card tonal lift of 2 dp, and no rule decided
 * which surface got which. The ladder is now two ideas, not four strengths:
 *
 *  - [TONAL_DP] a surface that is *in* the page and only needs to read as separate from it
 *  - [SOFT_DP]  a plate lifted above the page — a preview, a country panel
 *  - [FLOAT_DP] chrome that floats *over* content and has to survive scrolling beneath it: the
 *    transient message pill, the selection bar, the dock
 *
 * [FLAT_DP] is the sanctioned absence of lift, and the conformance gate reads [LADDER] so a fifth
 * strength cannot be introduced by accident.
 */
/**
 * The upper half of the alpha ladder: how strongly a colour that is *read* — an ink, a stage
 * surface, a mark — is allowed to show the ground through it.
 *
 * [MarbleWash] answers "how much tone sits behind content" and stops at 0.22, because past that a
 * wash starts competing with text. Above it the product needed the same discipline: 20 different
 * strengths between 0.35 and 0.92 were in use for four jobs (a ghosted watermark, a not-yet-known
 * value, secondary ink, and primary ink on a tinted ground), so a subtitle on Home was quieted by a
 * different amount than the same subtitle on Servers. Four rungs, one job each:
 *
 *  - [GHOST] a watermark or a mark that must read as decoration, never as a fact
 *  - [FADED] a value that is not known yet, or an option that is not available
 *  - [QUIET]  secondary ink: an overline, a subtitle, a unit beside a number
 *  - [NEAR]   primary ink on a ground that already carries colour
 *
 * The dock family ([MarbleLumenDock], the liquid lens bar, its indicator and item views, the dock
 * wings and backdrop) is exempt and says so: it draws an optic — glass thickness, magnification,
 * lens highlight — whose alphas are physics the CI suite pins, not surface tones.
 */
object MarbleVeil {
    const val GHOST: Float = 0.38f
    const val FADED: Float = 0.55f
    const val QUIET: Float = 0.72f
    const val NEAR: Float = 0.86f

    /** Every rung, ascending. The conformance gate reads this list. */
    val SCALE: List<Float> = listOf(GHOST, FADED, QUIET, NEAR)

    fun isOnScale(alpha: Float): Boolean = SCALE.any { abs(it - alpha) < 0.001f }

    /** The rung nearest a strength, ties resolving upwards — see [MarbleSpace.nearest]. */
    fun nearest(alpha: Float): Float = nearestRung(SCALE, alpha)
}

object MarbleElevation {
    const val FLAT_DP: Int = 0
    const val TONAL_DP: Int = 2
    const val SOFT_DP: Int = 8
    const val FLOAT_DP: Int = 12

    /** Every rung, ascending. The conformance gate reads this list. */
    val LADDER: List<Int> = listOf(FLAT_DP, TONAL_DP, SOFT_DP, FLOAT_DP)
}

/**
 * The two sanctioned selection morphs.
 *
 * Expressive selection is one idea — an idle control resting as a soft squircle and springing into
 * a full stadium when chosen — but it had been written five different ways (idle 12, 14 or 16 dp
 * against selected 24, 26 or 28 dp), so a chosen chip on Home was a slightly different shape from a
 * chosen chip in the shell. What actually varies is only the *size* of the control, so the size
 * becomes the scale and the corners come from [MarbleRadius]:
 *
 *  - [COMPACT] a small lens or tick: [MarbleRadius.SM] → [MarbleRadius.CARD]
 *  - [CONTROL] anything a thumb aims at: [MarbleRadius.CONTROL] → [MarbleRadius.GROUP]
 */
enum class MarbleSelectionRung(val idleDp: Int, val selectedDp: Int) {
    COMPACT(MarbleRadius.SM, MarbleRadius.CARD),
    CONTROL(MarbleRadius.CONTROL, MarbleRadius.GROUP);

    companion object {
        /** Every rung. The conformance gate reads this list. */
        val RUNGS: List<MarbleSelectionRung> = entries
    }
}

/**
 * The rung of a ladder nearest a value, ties resolving upwards.
 *
 * One helper for every scale in the product, so "which rung is 9 dp" and "which wash is 0.14" are
 * answered by the same rule: closest wins, and an exact tie takes the larger rung. Ties go up
 * because the two rungs that tie are always an optical step and a structural one, and losing the
 * optical step is invisible while losing the structural one collapses a group.
 */
private fun nearestRung(scale: List<Int>, value: Int): Int =
    scale.minWithOrNull(compareBy({ abs(it - value) }, { -it })) ?: value

private fun nearestRung(scale: List<Float>, value: Float): Float =
    scale.minWithOrNull(compareBy({ abs(it - value) }, { -it })) ?: value

/**
 * The one wash scale: how strongly a tone sits *behind* content.
 *
 * Fourteen strengths of the same idea were in use (0.04, 0.08, 0.10, 0.11, 0.12, 0.14, 0.16, 0.18,
 * 0.22, 0.24, 0.26 and friends), so the same tone read differently on two pages. Four rungs are
 * enough, and four is what makes a tone recognisable as itself wherever it appears:
 *
 *  - [FAINT]   an ambient watermark that must never compete with text
 *  - [SOFT]    a tone behind a value — the tile wash, and the answer to "how strong is a wash"
 *  - [TINT]    a tone behind a badge, an icon well or a selected chip
 *  - [STRONG]  a tone behind a focal mark the page wants the eye to reach first
 *
 * Plus [CLEAR], the one member that is not a tint: the transparent end of a gradient ramp. It is a
 * rung rather than a typed `0.02f` so the conformance gate can hold every literal alpha in the
 * product to these two scales without an exception list.
 *
 * A wash stops at [STRONG]. Past that a colour is no longer sitting behind content — it is content,
 * or ground the content sits on — and it belongs to [MarbleVeil], the upper half of the same ladder.
 * Both halves snap by [nearestRung]: closest rung wins, ties go up.
 */
object MarbleWash {
    const val CLEAR: Float = 0.02f
    const val FAINT: Float = 0.08f
    const val SOFT: Float = 0.12f
    const val TINT: Float = 0.16f
    const val STRONG: Float = 0.22f

    val SCALE: List<Float> = listOf(CLEAR, FAINT, SOFT, TINT, STRONG)

    fun isOnScale(alpha: Float): Boolean = SCALE.any { abs(it - alpha) < 0.001f }

    /** The rung nearest a strength, ties resolving upwards — see [MarbleSpace.nearest]. */
    fun nearest(alpha: Float): Float = nearestRung(SCALE, alpha)
}

/**
 * The grid every page sits on: one reading column, one gutter, one section rhythm, and the three
 * window classes that decide whether the chrome is a dock or a rail.
 *
 * The reading column used to be a literal `840.dp` inside the shell. It is a token now because it
 * is a *promise*: a page opened on a tablet is the same measure as the same page on a phone, so a
 * line of Persian prose never gets longer than the eye can track back to its own start.
 */
object MarbleGrid {
    /** The widest a reading column may become, on any window. */
    const val READING_COLUMN_MAX_DP: Int = 840

    /** The gutter every page reserves at both edges. */
    const val GUTTER_DP: Int = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP

    /** The air between two sections of one page. */
    const val SECTION_RHYTHM_DP: Int = MarbleSpec.SECTION_SPACING_DP

    /** Below this width the window is a phone: a floating dock, one column. */
    const val MEDIUM_MIN_WIDTH_DP: Int = MarbleSpec.WIDE_RAIL_MIN_WIDTH_DP

    /** At or above this width the window keeps the reading column and gains side-by-side content. */
    const val EXPANDED_MIN_WIDTH_DP: Int = READING_COLUMN_MAX_DP

    fun windowClassOf(widthDp: Int): MarbleWindowClass = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> MarbleWindowClass.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> MarbleWindowClass.MEDIUM
        else -> MarbleWindowClass.COMPACT
    }

    /** The measure a page of this width actually writes in: the column, capped. */
    fun readingColumnOf(widthDp: Int): Int = minOf(widthDp, READING_COLUMN_MAX_DP)

    /** True when the window is wide enough for the navigation rail instead of the dock. */
    fun showsRail(widthDp: Int): Boolean = widthDp >= MEDIUM_MIN_WIDTH_DP
}

/** The three window classes of Material 3, as this product uses them. */
enum class MarbleWindowClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Measured sizes: the glyphs, wells and touch targets the product draws.
 *
 * The 48 dp touch floor is an accessibility promise, not a style choice — [TOUCH_MIN_DP] exists so
 * a test can fail the build on a control that shrank below it.
 */
object MarbleMeasure {
    /** The accessibility floor for anything that takes a tap. */
    const val TOUCH_MIN_DP: Int = 48

    /** A row's minimum height ([MarbleSpec.ROW_MIN_HEIGHT_DP]). */
    const val ROW_MIN_DP: Int = MarbleSpec.ROW_MIN_HEIGHT_DP

    /** A row's leading icon well ([MarbleSpec.ROW_ICON_SIZE_DP]). */
    const val ICON_WELL_DP: Int = MarbleSpec.ROW_ICON_SIZE_DP

    /** Glyph sizes, ascending: the mark inside a well, never the well itself. */
    const val GLYPH_XS_DP: Int = 10
    const val GLYPH_SM_DP: Int = 14
    const val GLYPH_MD_DP: Int = 18
    const val GLYPH_LG_DP: Int = 22
    const val GLYPH_XL_DP: Int = 24

    val GLYPH_SCALE: List<Int> = listOf(GLYPH_XS_DP, GLYPH_SM_DP, GLYPH_MD_DP, GLYPH_LG_DP, GLYPH_XL_DP)

    fun isOnGlyphScale(value: Int): Boolean = value in GLYPH_SCALE
}

/**
 * The one semantic tone scale.
 *
 * Eleven private colour functions used to answer the same question eleven times — "what colour is
 * this verdict?" — and they disagreed: a dead route was a faded neutral on Servers and a red on the
 * council page, "nothing measured yet" was `onSurfaceVariant` in four places, `outline` in a fifth
 * and `primary` in a sixth.
 *
 * A page now names the *meaning* ([MarbleTone]) and asks the scheme for the colour, so one table
 * decides every verdict the product prints and a theme change moves all of them together.
 */
enum class MarbleTone {
    /** Measured and healthy. */
    GOOD,

    /** Measured and degraded, still usable. */
    FAIR,

    /** Measured and bad. */
    POOR,

    /** Known-bad but deliberately calm: a dead route in a library nobody is reading yet. */
    LOST,

    /** Nothing has been measured. This is not a verdict and never takes a verdict colour. */
    UNKNOWN,

    /** The product's own voice: the active route, a brand accent, a live control. */
    BRAND,

    /** A second identity beside [BRAND]: a protocol, a source stripe, a category. */
    ACCENT,

    /** A third identity, for a categorical palette that has to keep three families apart. */
    SUPPORT,

    /** Structure, not meaning: an untested row's outline, a disabled affordance. */
    STRUCTURAL
}

/**
 * The one resolver from a semantic tone to a colour role.
 *
 * Read the tone, never the role: a page that reaches for `colors.error` directly is a page that has
 * decided for itself what "bad" looks like, which is how eleven answers appeared in the first place.
 *
 * The resolver is a plain function of a scheme and the status ramp, so the whole verdict table is
 * assertable on a JVM ([marbleToneColor]) and the composable below is only the reading of the two
 * values the composition already holds.
 */
@Composable
fun ColorScheme.toneColor(tone: MarbleTone): Color =
    marbleToneColor(scheme = this, status = MarbleTheme.status, tone = tone)

/** The verdict table itself: pure, so a unit test can hold every tone in the product to it. */
fun marbleToneColor(
    scheme: ColorScheme,
    status: MarbleStatusColors,
    tone: MarbleTone
): Color = when (tone) {
    MarbleTone.GOOD -> status.success
    MarbleTone.FAIR -> status.warning
    MarbleTone.POOR -> scheme.error
    MarbleTone.LOST -> scheme.onSurfaceVariant.copy(alpha = MarbleVeil.QUIET)
    MarbleTone.UNKNOWN -> scheme.onSurfaceVariant
    MarbleTone.BRAND -> scheme.primary
    MarbleTone.ACCENT -> scheme.tertiary
    MarbleTone.SUPPORT -> scheme.secondary
    MarbleTone.STRUCTURAL -> scheme.outline
}

/**
 * The ink that reads *on* a tone: a pill's label, a badge's glyph, a dot's centre.
 *
 * The counterpart of [marbleToneColor], and it exists for the same reason. Before it, "the word
 * inside the green pill" was answered per call site — `colors.onSuccess` on the connect pill,
 * `colors.onSurface` on a badge, `Color.White` on a stripe — so two green surfaces could carry two
 * different inks. Every tone now has exactly one.
 */
@Composable
fun ColorScheme.onToneColor(tone: MarbleTone): Color =
    marbleOnToneColor(scheme = this, status = MarbleTheme.status, tone = tone)

/** The on-tone table itself: pure, so a JVM test can hold every pairing to it. */
fun marbleOnToneColor(
    scheme: ColorScheme,
    status: MarbleStatusColors,
    tone: MarbleTone
): Color = when (tone) {
    MarbleTone.GOOD -> status.onSuccess
    MarbleTone.FAIR -> status.onWarning
    MarbleTone.POOR -> scheme.onError
    MarbleTone.BRAND -> scheme.onPrimary
    MarbleTone.ACCENT -> scheme.onTertiary
    MarbleTone.SUPPORT -> scheme.onSecondary
    // LOST, UNKNOWN and STRUCTURAL are inks, not grounds: nothing sits on them.
    MarbleTone.LOST, MarbleTone.UNKNOWN, MarbleTone.STRUCTURAL -> scheme.onSurface
}

/**
 * The container of a tone — the soft ground a verdict is drawn on — and the ink that reads on it.
 *
 * One hero stage, one banner and one status card were each choosing their own container per state
 * (`successContainer` here, `tertiaryContainer` there for the same "working" verdict). A tone now
 * carries its container pair with it, so a state cannot be green in one surface and violet in the
 * next.
 */
@Composable
fun ColorScheme.toneContainer(tone: MarbleTone): Color =
    marbleToneContainer(scheme = this, status = MarbleTheme.status, tone = tone)

@Composable
fun ColorScheme.onToneContainer(tone: MarbleTone): Color =
    marbleOnToneContainer(scheme = this, status = MarbleTheme.status, tone = tone)

/** The container table itself: pure, so a JVM test can hold every tone to it. */
fun marbleToneContainer(
    scheme: ColorScheme,
    status: MarbleStatusColors,
    tone: MarbleTone
): Color = when (tone) {
    MarbleTone.GOOD -> status.successContainer
    MarbleTone.FAIR -> status.warningContainer
    MarbleTone.POOR -> scheme.errorContainer
    MarbleTone.BRAND -> scheme.primaryContainer
    MarbleTone.ACCENT -> scheme.tertiaryContainer
    MarbleTone.SUPPORT -> scheme.secondaryContainer
    MarbleTone.LOST, MarbleTone.UNKNOWN -> scheme.surfaceContainerHigh
    MarbleTone.STRUCTURAL -> scheme.surfaceContainer
}

fun marbleOnToneContainer(
    scheme: ColorScheme,
    status: MarbleStatusColors,
    tone: MarbleTone
): Color = when (tone) {
    MarbleTone.GOOD -> status.onSuccessContainer
    MarbleTone.FAIR -> status.onWarningContainer
    MarbleTone.POOR -> scheme.onErrorContainer
    MarbleTone.BRAND -> scheme.onPrimaryContainer
    MarbleTone.ACCENT -> scheme.onTertiaryContainer
    MarbleTone.SUPPORT -> scheme.onSecondaryContainer
    MarbleTone.LOST, MarbleTone.UNKNOWN -> scheme.onSurfaceVariant
    MarbleTone.STRUCTURAL -> scheme.onSurfaceVariant
}

/** The wash of a tone at one of the four sanctioned strengths ([MarbleWash]). */
@Composable
fun ColorScheme.toneWash(tone: MarbleTone, alpha: Float = MarbleWash.SOFT): Color =
    toneColor(tone).copy(alpha = alpha)

/**
 * A measured score, or any fraction of "how good", as a tone. One ladder for every gauge in the
 * product: the council's grade, a plan's remaining volume, a route's live score.
 *
 * The thresholds are the ones the council page already used, promoted here so a second gauge cannot
 * invent a second ladder: 85 and above is [MarbleTone.GOOD], 70 and above is [MarbleTone.ACCENT]
 * (good, but a different kind of good), 55 and above is [MarbleTone.FAIR], and below that is
 * [MarbleTone.POOR].
 */
fun marbleToneOfScore(score: Int): MarbleTone = when {
    score >= 85 -> MarbleTone.GOOD
    score >= 70 -> MarbleTone.ACCENT
    score >= 55 -> MarbleTone.FAIR
    else -> MarbleTone.POOR
}

/** A fraction (0f…1f) as a tone — the same ladder as [marbleToneOfScore], read as a percentage. */
fun marbleToneOfFraction(fraction: Float): MarbleTone =
    marbleToneOfScore((fraction.coerceIn(0f, 1f) * 100f).toInt())

/**
 * The one stat tile's geometry: the label+value surface every page prints a measurement in.
 *
 * Two densities, one component. [PADDING_H_DP]/[PADDING_V_DP] is the tile a page shows on its own;
 * [COMPACT_PADDING_H_DP]/[COMPACT_PADDING_V_DP] is the same tile inside another card, where three of
 * them share a row and the air between them is doing the separation the padding used to do.
 */
object MarbleStatSpec {
    const val CORNER_DP: Int = MarbleRadius.TILE
    const val PADDING_H_DP: Int = MarbleSpace.MD
    const val PADDING_V_DP: Int = MarbleSpace.INNER
    const val COMPACT_PADDING_H_DP: Int = MarbleSpace.SM
    const val COMPACT_PADDING_V_DP: Int = MarbleSpace.ROW_GAP

    /** The air between a tile's own label and its own value. */
    const val GAP_DP: Int = MarbleSpace.XS

    /** The wash a tone gets when it is the tile's ground rather than its ink. */
    const val WASH_ALPHA: Float = MarbleWash.SOFT
}

/**
 * The one level meter: a row of bars that says "this many steps out of that many", used for a ping
 * reading and for anything else the product measures on a short ladder.
 *
 * The heights are arithmetic, not a list of literals: [BAR_MIN_HEIGHT_DP] plus one
 * [BAR_STEP_DP] per step, which is how the four bars every server row draws stay a ladder instead of
 * four numbers somebody typed.
 */
object MarbleLevelSpec {
    const val STEPS: Int = 4
    const val BAR_WIDTH_DP: Int = 3
    const val BAR_MIN_HEIGHT_DP: Int = 4
    const val BAR_STEP_DP: Int = 3
    const val GAP_DP: Int = MarbleSpace.HAIR

    /** The height of one bar, by its index: 4, 7, 10, 13 dp at the default rung. */
    fun barHeightDp(index: Int): Int = BAR_MIN_HEIGHT_DP + index * BAR_STEP_DP

    /** A fraction of "how good" as a lit bar count, so a meter and a tone never disagree. */
    fun levelOf(fraction: Float, steps: Int = STEPS): Int =
        (fraction.coerceIn(0f, 1f) * steps.toFloat()).toInt().coerceIn(0, steps)
}

/**
 * The one loading placeholder's rhythm.
 *
 * Two skeletons used to breathe at two speeds (900 ms and 1200 ms) between two different alpha
 * pairs, on two pages a reader moves between. One breath now: [BREATH_MS] milliseconds, ping-ponging
 * between [BREATH_MIN] and [BREATH_MAX] of the tone's own ink, and a single still alpha when the
 * reader has asked the product to stop moving.
 */
object MarbleSkeletonSpec {
    const val BREATH_MS: Int = 1_100

    /**
     * The breath runs between these two fractions of the ink's full strength. A caller multiplies
     * by the wash its own surface needs, which is how one rhythm serves both a full-width reading
     * block (× [MarbleWash.TINT]) and a 26 dp pill that has to stay visible (× 1).
     */
    const val BREATH_MIN: Float = 0.35f
    const val BREATH_MAX: Float = 0.70f

    const val CORNER_DP: Int = MarbleRadius.XS
    const val LINE_HEIGHT_DP: Int = MarbleSpace.MD
    const val BLOCK_HEIGHT_DP: Int = 52
}
