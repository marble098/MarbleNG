package com.marbleng.app.ui

// MARBLE_NATIVE_ANDROID_V213 — the chapter where the product stops dressing itself like its own
// design system and starts behaving like the platform it lives on.
//
// The critique this file answers, in the order a user meets it:
//
//  1. **The boxes were films, not objects.** Every card was `background(fill)` + a 1 dp rim in an
//     accent hue, and the shadow — when there was one — was cast in the brand's blue on a page
//     that is already blue, which is why surfaces read as painted panels the aurora shows through
//     rather than as material sitting on a light. Android's own answer is a *tonal container*: an
//     opaque step of the platform's surface ladder, one hairline from the platform's outline
//     family, and a shadow in the surface's own hue. That is [MarbleNativeCard].
//  2. **The rows had no platform anatomy.** A settings row was a 36 dp inset with a coloured rail
//     on its leading edge — under the platform's own 48 dp touch floor and unrelated to how
//     Android draws a list item (leading container, title, supporting line, trailing control).
//     [MarbleNativeListItem] is that anatomy, at [MarbleNativeSpec.RowMinHeightDp] / 72 dp, with
//     the platform's switch ([MarbleNativeSwitch]) as the trailing control.
//  3. **The choices were chips that looked like buttons.** A single-choice control on Android is a
//     radio row or a segmented button, not a stack of bordered boxes with a colour wash for the
//     selected one. [MarbleNativeChoiceRow] is the radio row, with a real 20 dp indicator.
//  4. **The headings were uppercase, letter-spaced and tone-coloured.** That is a marketing
//     heading. The platform's settings heading is a `labelLarge` in the primary tone with normal
//     letterspacing — [MarbleNativeSectionHeader].
//
// What this file is NOT: a new design system. It owns no palette and no brand decision. Every
// colour it paints is read from the live `MaterialTheme.colorScheme` — the *platform's* roles
// (surfaceContainerLow/Lowest/High, outlineVariant, onSurfaceVariant, primary, onPrimary) — which
// `AetherTheme` already rebuilds for the brand palettes, for AMOLED and for a wallpaper palette in
// one place. So "native" here means "the same tonal ladder the rest of Android is drawn from",
// not "a second set of Marble colours that happen to look like Android's".
//
// The numbers live in [MarbleNativeSpec] as plain integers so a JVM unit test can pin them
// without a device, exactly the way [ServerTilePolicy] pins the grid.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's own measurements, in one table.
 *
 * Every number here is a *platform* number, not a Marble preference: 56 dp is the Material list
 * item's one-line height, 72 dp its two-line height, 48 dp the minimum touch target Android's
 * accessibility guidance and the Material spec both state, 40 dp the leading icon container every
 * Android settings list uses, 24/20/16 dp the platform's card/tile/row corner steps, and 1 dp the
 * hairline divider. The integers are exposed next to the [Dp] values because the JVM tests can
 * only read the integers.
 */
object MarbleNativeSpec {
    /** A one-line list item, exactly as Android draws it. */
    const val RowMinHeightDp: Int = 56

    /** A list item with a supporting line under its title. */
    const val RowTwoLineHeightDp: Int = 72

    /** A dense item: chips, legend rows and other secondary lists. Never below the floor. */
    const val RowDenseHeightDp: Int = 48

    /** The leading container of a list item: an icon, a glyph or a flag. */
    const val LeadingContainerDp: Int = 40

    /** The glyph a [LeadingContainerDp] container is sized for. */
    const val LeadingGlyphDp: Int = 22

    /** A card's corner. The platform's large step, not Marble's 22 dp. */
    const val CardCornerDp: Int = 24

    /** A tile's corner inside a grid. */
    const val TileCornerDp: Int = 20

    /** A row's corner. */
    const val RowCornerDp: Int = 16

    /** Card content inset. */
    const val CardPaddingDp: Int = 16

    /** Horizontal inset of a list row's content. */
    const val RowPaddingHorizontalDp: Int = 16

    /** Vertical inset of a list row's content. */
    const val RowPaddingVerticalDp: Int = 8

    /** Horizontal inset of a divider, measured from the row's leading edge. */
    const val DividerInsetDp: Int = 16

    /** A divider's painted thickness: one device-independent pixel, the platform's hairline. */
    const val DividerThicknessDp: Int = 1

    /** The resting lift of a card: the platform's own low elevation. */
    const val CardElevationDp: Int = 4

    /** The lift of a card the user chose or that carries traffic. */
    const val CardLiftedElevationDp: Int = 8

    /** The contact shadow: the tight one that puts a surface *on* a page instead of above it. */
    const val ContactElevationDp: Int = 2

    /** Android's minimum touch target. Nothing interactive may be smaller. */
    const val TouchTargetDp: Int = 48

    /** The navigation bar's active indicator, Material 3's own geometry. */
    const val IndicatorWidthDp: Int = 56
    const val IndicatorHeightDp: Int = 32

    /** A selection indicator's outer diameter, and the dot inside it. */
    const val ChoiceIndicatorDp: Int = 20
    const val ChoiceDotDp: Int = 10

    /** A section heading's letterspacing. Normal tracking: the platform does not shout. */
    const val SectionLabelTrackingSp: Float = 0.4f

    /** The height a row must take, from whether it carries a supporting line. */
    fun rowHeightDp(hasSubtitle: Boolean): Int =
        if (hasSubtitle) RowTwoLineHeightDp else RowMinHeightDp

    /** True when a painted height still meets the platform's touch floor. */
    fun meetsTouchFloor(heightDp: Int): Boolean = heightDp >= TouchTargetDp

    /**
     * The horizontal inset a divider starts at when a leading container occupies the row's head.
     *
     * The platform's own rule: a divider between two rows that both have a leading icon starts
     * where the icon container ends, so the leading column reads as one continuous strip.
     */
    fun dividerInsetDp(hasLeading: Boolean): Int =
        if (hasLeading) RowPaddingHorizontalDp + LeadingContainerDp + RowPaddingHorizontalDp / 2
        else DividerInsetDp
}

// ------------------------------------------------------------------------------------------------
// The platform's colour roles, resolved for the theme that is actually running.
// ------------------------------------------------------------------------------------------------

/**
 * MARBLE_NATIVE_ANDROID_V213 — the surface a card or a row is filled with.
 *
 * Read from the live scheme, so the brand palettes, AMOLED and a wallpaper palette all answer it:
 * light takes the brightest step (an opaque white card on the cool page), dark takes a *lifted*
 * step above the black floor rather than black on black.
 */
@Composable
internal fun marbleNativeCardFill(): Color = if (homeCloudDark()) {
    MaterialTheme.colorScheme.surfaceContainerLow
} else {
    MaterialTheme.colorScheme.surfaceContainerLowest
}

/**
 * The hairline family the platform draws its divisions with.
 *
 * `outlineVariant` is the quiet stroke Material uses for dividers and card edges; the brand
 * palettes resolve it from the navy hairline, AMOLED from the slate stroke. It is deliberately
 * *neutral*: a card edge is not a status and must not borrow the accent.
 */
@Composable
internal fun marbleNativeOutline(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = homeCloudDark()
    // A hairline that disappears is not a divider: the quiet role is pushed a little toward the
    // readable one so an edge survives on a bright page, then flattened onto the surface it is
    // drawn on (a translucent stroke over a gradient is the one thing a hairline may not be).
    val stroke = lerp(scheme.outlineVariant, scheme.onSurfaceVariant, if (dark) .16f else .22f)
    return stroke.compositeOver(marbleNativeCardFill())
}

/** The platform's secondary ink: supporting text, values, headings' detail. */
@Composable
internal fun marbleNativeInkVariant(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * The step *above* a card's fill in the platform's tonal ladder — a pressed state, a floating
 * bar's body, a control's own container.
 *
 * The two brightnesses take the step on opposite sides of the page, which is how Material's
 * ladder is built: on a bright page the container is *deeper* than the white card, on the OLED
 * floor it is *lifted* off the black.
 */
@Composable
internal fun marbleNativeContainerHigh(): Color = if (homeCloudDark()) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

/** The tonal wash of a chosen row: the platform's container tone, never the brand's accent. */
@Composable
internal fun marbleNativeSelectedFill(tone: Color): Color = if (homeCloudDark()) {
    tone.copy(alpha = .18f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
} else {
    tone.copy(alpha = .10f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the one shadow pair the platform's surfaces wear.
 *
 * A surface that casts a single shadow reads as *above* the page, not *on* it; Android's own
 * elevation is ambient + spot in the surface's hue. `Color.Black` is never one of the two: a grey
 * shadow under an ice-blue card is dirt (V201's rule, now the default for every box in the
 * product instead of a per-call-site decision).
 */
internal data class MarbleNativeShadow(
    val ambient: Color,
    val spot: Color,
    val contact: Color
)

@Composable
internal fun marbleNativeShadow(tone: Color): MarbleNativeShadow {
    val dark = homeCloudDark()
    val scheme = MaterialTheme.colorScheme
    return if (dark) {
        MarbleNativeShadow(
            ambient = scheme.surfaceContainerLowest.copy(alpha = .55f),
            spot = tone.copy(alpha = .28f),
            contact = scheme.surfaceContainerLowest.copy(alpha = .70f)
        )
    } else {
        MarbleNativeShadow(
            ambient = Color(0xFF0A2540).copy(alpha = .16f),
            spot = tone.copy(alpha = .24f),
            contact = Color(0xFF0A2540).copy(alpha = .10f)
        )
    }
}

// ------------------------------------------------------------------------------------------------
// The components.
// ------------------------------------------------------------------------------------------------

/** The platform's corner for a card, a tile, a row or a pill. */
internal fun marbleNativeShape(radius: Dp): Shape = RoundedCornerShape(radius)

/** The platform's pill: a full-round container whose radius follows the box. */
internal fun marbleNativePill(): Shape = RoundedCornerShape(percent = 50)

/**
 * MARBLE_NATIVE_ANDROID_V213 — one card, drawn the way the platform draws one.
 *
 * Fill: an opaque container step. Edge: one gradient hairline that catches the light at the top
 * and settles into the quiet outline at the bottom, so the card reads as an object catching a
 * light instead of a rectangle with a coloured border. Lift: the platform's ambient + spot pair
 * plus the tight contact shadow that puts the box *on* the page. Press: the product's own kinetic
 * response (V186), because that part of Marble was never the problem.
 *
 * The caller owns [tone] — the accent of the section the card belongs to — and nothing else about
 * the skin, which is what makes forty cards across six pages agree.
 */
@Composable
internal fun MarbleNativeCard(
    modifier: Modifier = Modifier,
    tone: Color = Aether.Cyan,
    lifted: Boolean = false,
    radius: Dp = MarbleNativeSpec.CardCornerDp.dp,
    contentPadding: PaddingValues = PaddingValues(MarbleNativeSpec.CardPaddingDp.dp),
    verticalSpacing: Dp = 9.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = homeCloudDark()
    val shape = marbleNativeShape(radius)
    val fill = marbleNativeCardFill()
    val outline = marbleNativeOutline()
    val shadow = marbleNativeShadow(tone)
    val elevation by animateDpAsState(
        targetValue = (if (lifted) {
            MarbleNativeSpec.CardLiftedElevationDp
        } else {
            MarbleNativeSpec.CardElevationDp
        }).dp,
        animationSpec = MarbleMotionSpecs.Dp,
        label = "native-card-elevation"
    )
    // Light hitting an edge, not a second border: the top of the rim takes the surface's own
    // light (white on a bright page, the section tone on the OLED floor) and fades to the quiet
    // outline by the bottom edge.
    val litRim = if (dark) {
        lerp(outline, tone, .34f)
    } else {
        lerp(outline, Color.White, .70f)
    }
    val rim = Brush.verticalGradient(listOf(litRim, outline))
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = shadow.ambient,
                spotColor = shadow.spot
            )
            // The contact shadow is what makes a card sit *on* a page: without it a 4 dp lift
            // still reads as a floating film on a bright theme.
            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = shadow.contact,
                spotColor = shadow.contact
            )
            .clip(shape)
            .background(fill)
            // One whisper of the section's tone inside the top edge — the platform's tonal
            // elevation, paid by paint rather than by a second translucent plane.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tone.copy(alpha = if (dark) .06f else .035f),
                        Color.Transparent
                    )
                )
            )
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.kineticClickable(
                        role = Role.Button,
                        boundedShape = shape,
                        showIndication = false,
                        onClick = onClick
                    )
                }
            )
            .border(1.dp, rim, shape)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content
        )
    }
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's list item.
 *
 * `rowHeight` is the anatomy, not a hint: a one-line item is 56 dp, one with a supporting line is
 * 72 dp, and both clear Android's 48 dp touch floor by paint as well as by hit box. A [leading]
 * slot is the 40 dp container every Android settings list uses for an icon, a glyph or a flag; a
 * [trailing] slot is where the platform puts the control that changes the row — a switch, a
 * value, a chevron.
 *
 * The row is a *filled* item and not a naked one: the platform's container step, its hairline and
 * nothing else, so a list of switches reads as a list and not as text floating on the page.
 */
@Composable
internal fun MarbleNativeListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    tone: Color = Aether.Cyan,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: String = "",
    selected: Boolean = false,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: (() -> Unit)? = null
) {
    val shape = marbleNativeShape(MarbleNativeSpec.RowCornerDp.dp)
    val outline = marbleNativeOutline()
    val base = marbleNativeCardFill()
    val resting = if (selected) marbleNativeSelectedFill(tone) else base
    val fill by animateColorAsState(
        targetValue = resting,
        animationSpec = MarbleMotionSpecs.Color,
        label = "native-row-fill"
    )
    val subtitleText = subtitle.trim()
    val height = MarbleNativeSpec.rowHeightDp(subtitleText.isNotEmpty()).dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (selected) tone.copy(alpha = .34f) else outline, shape)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.kineticClickable(
                        role = role,
                        boundedShape = shape,
                        showIndication = false,
                        onClick = onClick
                    )
                }
            )
            // The row's own text is the row's own description, and the *state* a row carries is
            // the caller's to speak: a switch row announces "on", a choice row announces
            // "selected", and neither is a fact this composable can know. It is therefore silent
            // here and never overwrites the sentence its owner put on the same node.
            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp,
                vertical = MarbleNativeSpec.RowPaddingVerticalDp.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(MarbleNativeSpec.LeadingContainerDp.dp)
                    .clip(CircleShape)
                    .background(
                        // The platform's tonal container: the tone at low strength over the
                        // row's own surface, so the container has a computable colour instead of
                        // borrowing whatever is behind the row.
                        tone.copy(alpha = if (homeCloudDark()) .22f else .12f).compositeOver(fill)
                    ),
                contentAlignment = Alignment.Center
            ) {
                leading()
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                trx(title),
                color = if (enabled) Aether.Ink else Aether.InkFaint,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitleText.isNotEmpty()) {
                Text(
                    trx(subtitleText),
                    color = marbleNativeInkVariant(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value.isNotBlank()) {
            Text(
                value,
                color = marbleNativeInkVariant(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — one divider, inset the way the platform insets one.
 *
 * A full-bleed line between two rows that both have a leading container is what makes an Android
 * list look hand-drawn; the platform starts the line where the leading column ends.
 */
@Composable
internal fun MarbleNativeDivider(
    modifier: Modifier = Modifier,
    hasLeading: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MarbleNativeSpec.dividerInsetDp(hasLeading).dp)
            .height(MarbleNativeSpec.DividerThicknessDp.dp)
            .background(marbleNativeOutline())
    )
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — a section heading, in the platform's voice.
 *
 * The old heading was uppercased, letterspaced 1.6 sp and painted in the section's tone — three
 * signals for one word, which is why a settings page read as a wall of shouting labels. This is
 * `labelLarge` in the primary tone at normal tracking, which is how Android names a group.
 */
@Composable
internal fun MarbleNativeSectionHeader(
    title: String,
    subtitle: String = "",
    tone: Color = Aether.Cyan
) {
    Column(
        modifier = Modifier.padding(
            start = MarbleNativeSpec.RowPaddingHorizontalDp.dp / 4f,
            top = 6.dp,
            bottom = 2.dp
        ),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            trx(title),
            color = tone,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = MarbleNativeSpec.SectionLabelTrackingSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                trx(subtitle),
                color = marbleNativeInkVariant(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's switch.
 *
 * Marble had been painting the Material component with four hand-picked colours per call site —
 * a white thumb on the brand blue in light mode, a *navy* thumb in dark mode, an `InkFaint` thumb
 * when off — which is a custom control wearing the platform's silhouette: it does not follow a
 * wallpaper palette, and its off state is not the platform's. The switch now takes its four roles
 * from the live scheme (`primary` / `onPrimary` / `surfaceContainerHighest` / `outline`), so it is
 * the same switch Android draws, in the user's own colours, in every theme.
 */
@Composable
internal fun MarbleNativeSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            uncheckedThumbColor = scheme.outline,
            uncheckedTrackColor = scheme.surfaceContainerHighest
        )
    )
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — a single-choice row: the platform's radio grammar.
 *
 * A choice inside a list is a row you tap, with a 20 dp indicator whose inner dot is the only
 * thing that moves when the answer changes. The old control was a bordered box that flooded with
 * a 14 % accent wash when chosen — a button that had been told to behave like a radio, which is
 * why a page of them read as a wall of controls instead of one question with four answers.
 */
@Composable
internal fun MarbleNativeChoiceRow(
    title: String,
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String = "",
    leading: (@Composable () -> Unit)? = null
) {
    val dot by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MarbleMotionSpecs.ResponseFloat,
        label = "native-choice-dot"
    )
    val ring by animateColorAsState(
        targetValue = if (selected) tone else marbleNativeInkVariant(),
        animationSpec = MarbleMotionSpecs.Color,
        label = "native-choice-ring"
    )
    // The state a choice speaks is resolved *here*, in the composition: `trx` is itself composable
    // (it reads the live language), and a `semantics { }` lambda is not a composable scope — the
    // words have to exist before the node is described.
    val selectedState = trx("Selected")
    val notSelectedState = trx("Not selected")
    MarbleNativeListItem(
        title = title,
        subtitle = detail,
        tone = tone,
        selected = selected,
        leading = leading,
        role = Role.RadioButton,
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = listOfNotNull(
                title.takeIf { it.isNotBlank() },
                detail.takeIf { it.isNotBlank() }
            ).joinToString(", ")
            stateDescription = if (selected) selectedState else notSelectedState
        },
        trailing = {
            Box(
                modifier = Modifier
                    .size(MarbleNativeSpec.ChoiceIndicatorDp.dp)
                    .clip(CircleShape)
                    .border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (dot > 0f) {
                    Box(
                        modifier = Modifier
                            .size((MarbleNativeSpec.ChoiceDotDp * dot).dp)
                            .clip(CircleShape)
                            .background(tone)
                    )
                }
            }
        }
    )
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's value row: a label, a right-aligned value and the
 * disclosure chevron. This is the shape Android uses to say "this row opens something", and the
 * product's settings pages had no such row at all: every door was another full-width box.
 */
@Composable
internal fun MarbleNativeValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    tone: Color = Aether.Cyan,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val outline = marbleNativeOutline()
    MarbleNativeListItem(
        title = title,
        subtitle = subtitle,
        tone = tone,
        value = value,
        leading = leading,
        onClick = onClick,
        modifier = modifier,
        trailing = { MarbleNativeChevron(outline) }
    )
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's *segmented* control: mutually exclusive answers to
 * one short question, drawn as one pill split into equal shares.
 *
 * This replaces the "choice chip" family (a bordered box per option with a colour wash for the
 * chosen one). A segmented button is one object with n states, which is what a two-to-four-option
 * setting actually is, and it is what Android's own Settings shows for one.
 */
@Composable
internal fun MarbleNativeSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Aether.Cyan
) {
    if (options.isEmpty()) return
    val shape = marbleNativePill()
    val track = marbleNativeOutline()
    val trackFill = marbleNativeContainerHigh()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MarbleNativeSpec.TouchTargetDp.dp)
            .clip(shape)
            .background(trackFill)
            .border(1.dp, track, shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val chosen = index == selectedIndex
            // Resolved in the composition, not in the semantics lambda below (see
            // [MarbleNativeChoiceRow]).
            val labelText = trx(label)
            val selectedState = trx("Selected")
            val notSelectedState = trx("Not selected")
            val fill by animateColorAsState(
                targetValue = if (chosen) {
                    tone.copy(alpha = if (homeCloudDark()) .30f else .14f).compositeOver(trackFill)
                } else {
                    trackFill
                },
                animationSpec = MarbleMotionSpecs.Color,
                label = "native-segment-fill-$index"
            )
            val ink by animateColorAsState(
                targetValue = if (chosen) Aether.Ink else marbleNativeInkVariant(),
                animationSpec = MarbleMotionSpecs.Color,
                label = "native-segment-ink-$index"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MarbleNativeSpec.RowDenseHeightDp.dp - 8.dp)
                    .clip(marbleNativePill())
                    .background(fill)
                    .then(
                        if (chosen) Modifier.border(1.dp, tone.copy(alpha = .38f), marbleNativePill())
                        else Modifier
                    )
                    .kineticClickable(
                        role = Role.RadioButton,
                        boundedShape = marbleNativePill(),
                        showIndication = false,
                        pressScale = .97f
                    ) { onSelect(index) }
                    .semantics {
                        this.selected = chosen
                        contentDescription = labelText
                        stateDescription = if (chosen) selectedState else notSelectedState
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    trx(label),
                    color = ink,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A little air between two platform rows inside one card. */
@Composable
internal fun MarbleNativeRowGap() {
    Spacer(Modifier.height(2.dp))
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's disclosure mark.
 *
 * A row that opens something says so with a chevron, and the chevron is drawn in the quiet
 * outline family: it is a signpost, not a state, so it may not wear an accent. Drawn with the
 * Canvas primitives the rest of the product uses — no font glyph, so no typeface can turn a
 * chevron into a box.
 */
@Composable
internal fun MarbleNativeChevron(
    color: Color,
    modifier: Modifier = Modifier,
    mirror: Boolean = false
) {
    Canvas(modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val near = if (mirror) w * .72f else w * .28f
        val far = if (mirror) w * .28f else w * .72f
        drawLine(
            color = color,
            start = Offset(near, h * .22f),
            end = Offset(far, h * .50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(far, h * .50f),
            end = Offset(near, h * .78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
