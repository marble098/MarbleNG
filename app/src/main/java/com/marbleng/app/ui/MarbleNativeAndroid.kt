package com.marbleng.app.ui

// MARBLE_NATIVE_ANDROID_V213 — the chapter where the product stops dressing itself like its own

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
    const val RowMinHeightDp: Int = 56

    const val RowTwoLineHeightDp: Int = 72

    const val RowDenseHeightDp: Int = 48

    const val LeadingContainerDp: Int = 40

    const val LeadingGlyphDp: Int = 22

    const val CardCornerDp: Int = 24

    const val TileCornerDp: Int = 20

    const val RowCornerDp: Int = 16

    const val CardPaddingDp: Int = 16

    const val RowPaddingHorizontalDp: Int = 16

    const val RowPaddingVerticalDp: Int = 8

    const val DividerInsetDp: Int = 16

    const val DividerThicknessDp: Int = 1

    const val CardElevationDp: Int = 4

    const val CardLiftedElevationDp: Int = 8

    const val ContactElevationDp: Int = 2

    const val TouchTargetDp: Int = 48

    const val IndicatorWidthDp: Int = 56
    const val IndicatorHeightDp: Int = 32

    const val ChoiceIndicatorDp: Int = 20
    const val ChoiceDotDp: Int = 10

    const val SectionLabelTrackingSp: Float = 0.4f

    fun rowHeightDp(hasSubtitle: Boolean): Int =
        if (hasSubtitle) RowTwoLineHeightDp else RowMinHeightDp

    fun meetsTouchFloor(heightDp: Int): Boolean = heightDp >= TouchTargetDp

    fun dividerInsetDp(hasLeading: Boolean): Int =
        if (hasLeading) RowPaddingHorizontalDp + LeadingContainerDp + RowPaddingHorizontalDp / 2
        else DividerInsetDp
}

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

@Composable
internal fun marbleNativeOutline(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = homeCloudDark()

    val stroke = lerp(scheme.outlineVariant, scheme.onSurfaceVariant, if (dark) .16f else .22f)
    return stroke.compositeOver(marbleNativeCardFill())
}

@Composable
internal fun marbleNativeInkVariant(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun marbleNativeContainerHigh(): Color = if (homeCloudDark()) {
    MaterialTheme.colorScheme.surfaceContainerHigh
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

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
    // MARBLE_SHADOW_CALM_V220 — the platform pair is quieter in both themes: a shadow that reads
    // as weather under every card is not depth, it is dirt.
    val dark = homeCloudDark()
    val scheme = MaterialTheme.colorScheme
    return if (dark) {
        MarbleNativeShadow(
            ambient = scheme.surfaceContainerLowest.copy(alpha = .40f),
            spot = tone.copy(alpha = .20f),
            contact = scheme.surfaceContainerLowest.copy(alpha = .50f)
        )
    } else {
        MarbleNativeShadow(
            ambient = Color(0xFF0A2540).copy(alpha = .10f),
            spot = tone.copy(alpha = .16f),
            contact = Color(0xFF0A2540).copy(alpha = .07f)
        )
    }
}

internal fun marbleNativeShape(radius: Dp): Shape = RoundedCornerShape(radius)

internal fun marbleNativePill(): Shape = RoundedCornerShape(percent = 50)

/**
 * MARBLE_AURORA_SURFACE_V219 — the one row/surface skin of the product.
 *
 * A Marble surface is three things stacked in the order light arrives: the fill (opaque on any
 * page), one specular wash at the top edge so the top of the box catches the key light, and a rim
 * that is lit above and settles into the platform outline below. Every hand-written row used to
 * spell those three out — or, worse, spell out only the border — which is exactly why some rows
 * read as cards and others read as table cells.
 *
 * [fill] defaults to the platform card fill; [tone] tints the wash and the top of the rim when the
 * surface belongs to a category (a group's colour, a server's state).
 */
@Composable
internal fun Modifier.marbleLitSurface(
    shape: Shape,
    fill: Color? = null,
    tone: Color? = null,
    washAlpha: Float = 1f
): Modifier {
    val dark = homeCloudDark()
    val body = fill ?: marbleNativeCardFill()
    val outline = marbleNativeOutline()
    val wash = tone ?: Color.White
    val topAlpha = (if (tone == null) (if (dark) .045f else .30f) else (if (dark) .10f else .06f)) * washAlpha
    val litRim = if (tone == null) {
        lerp(outline, Color.White, if (dark) .18f else .55f)
    } else {
        lerp(tone, Color.White, if (dark) .30f else .45f)
    }

    return this
        .clip(shape)
        .background(body)
        .background(
            Brush.verticalGradient(
                colors = listOf(wash.copy(alpha = topAlpha), wash.copy(alpha = 0f)),
                startY = 0f,
                endY = 44f
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    litRim.copy(alpha = if (tone == null) .90f else .55f),
                    if (tone == null) outline else tone.copy(alpha = .30f)
                )
            ),
            shape = shape
        )
}

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

            .shadow(
                elevation = MarbleNativeSpec.ContactElevationDp.dp,
                shape = shape,
                clip = false,
                ambientColor = shadow.contact,
                spotColor = shadow.contact
            )
            .clip(shape)
            .background(fill)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tone.copy(alpha = if (dark) .07f else .040f),
                        tone.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = 150f
                )
            )
            // MARBLE_AURORA_SURFACE_V219 — one specular plane at the top edge. A card that only
            // has a border and a tint reads as a filled rectangle; the wash is what makes the top
            // edge catch light and the box sit in the page instead of on it.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) .045f else .34f),
                        Color.White.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = 44f
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
            .border(
                1.dp,
                Brush.verticalGradient(
                    if (selected) {
                        listOf(
                            lerp(tone, Color.White, .35f).copy(alpha = .60f),
                            tone.copy(alpha = .40f)
                        )
                    } else {
                        listOf(
                            lerp(outline, Color.White, if (homeCloudDark()) .18f else .55f)
                                .copy(alpha = .90f),
                            outline
                        )
                    }
                ),
                shape
            )
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

            .padding(
                horizontal = MarbleNativeSpec.RowPaddingHorizontalDp.dp,
                vertical = MarbleNativeSpec.RowPaddingVerticalDp.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) {
            // MARBLE_AURORA_SURFACE_V219 — the leading container is a squircle with the tone's own
            // gradient and a lit rim, at the same 40 dp the platform reserves for it. A flat circle
            // of 12 % tone was the single most repeated "weak icon" surface in Settings.
            val leadingShape = RoundedCornerShape(MarbleNativeSpec.LeadingContainerDp.dp * .34f)
            val leadingBase = if (homeCloudDark()) .22f else .12f
            Box(
                modifier = Modifier
                    .size(MarbleNativeSpec.LeadingContainerDp.dp)
                    .clip(leadingShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                tone.copy(alpha = leadingBase * 1.45f).compositeOver(fill),
                                tone.copy(alpha = leadingBase * .75f).compositeOver(fill)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                lerp(tone, Color.White, if (homeCloudDark()) .30f else .45f)
                                    .copy(alpha = .50f),
                                tone.copy(alpha = .22f)
                            )
                        ),
                        leadingShape
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
    // MARBLE_AURORA_SURFACE_V219 — one hairline, weighted so it is strongest at the inset and
    // gone by the trailing edge. A flat 1 dp rule is what makes a list look like a spreadsheet.
    val outline = marbleNativeOutline()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MarbleNativeSpec.dividerInsetDp(hasLeading).dp)
            .height(MarbleNativeSpec.DividerThicknessDp.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        outline.copy(alpha = .90f),
                        outline.copy(alpha = .55f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * MARBLE_AURORA_SURFACE_V219 — the product's dividing line.
 *
 * A 1 dp flat rule is the single most "unfinished" mark on a modern surface: it draws a box edge that
 * the eye reads as a table cell. This one is a light-weight gradient that is strongest where the
 * content starts and gone before it reaches the far edge, so a divider separates two things without
 * drawing a container around them.
 */
@Composable
internal fun MarbleFadeDivider(
    modifier: Modifier = Modifier,
    tone: Color? = null,
    leadingInset: Dp = 0.dp
) {
    val edge = tone ?: marbleNativeOutline()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = leadingInset)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        edge.copy(alpha = .92f),
                        edge.copy(alpha = .45f),
                        edge.copy(alpha = 0f)
                    )
                )
            )
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
 *
 * MARBLE_OFF_STATE_POLISH_V220 — the off state is drawn with intent now instead of leftovers: an
 * `onSurfaceVariant` thumb on the container track inside a real `outline` border, a check drawn in
 * the thumb while on, and dimmed-but-legible disabled roles. Still scheme roles only, so a
 * wallpaper palette still moves every one of them.
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
        thumbContent = if (checked) {
            {
                Canvas(Modifier.size(SwitchDefaults.IconSize)) {
                    val stroke = (size.minDimension * .13f).coerceIn(1.4f, 2.4f)
                    drawLine(
                        color = scheme.primary,
                        start = Offset(size.width * .26f, size.height * .55f),
                        end = Offset(size.width * .45f, size.height * .72f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = scheme.primary,
                        start = Offset(size.width * .45f, size.height * .72f),
                        end = Offset(size.width * .74f, size.height * .30f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = scheme.primary,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedBorderColor = scheme.outline,
            uncheckedIconColor = scheme.surfaceContainerHighest,
            disabledCheckedThumbColor = scheme.surfaceContainerHighest,
            disabledCheckedTrackColor = scheme.onSurface.copy(alpha = .12f),
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = scheme.onSurface.copy(alpha = .38f),
            disabledUncheckedTrackColor = scheme.surfaceContainerHighest.copy(alpha = .5f),
            disabledUncheckedBorderColor = scheme.outline.copy(alpha = .5f)
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
    // MARBLE_AURORA_SURFACE_V219 — the native chevron is the grid chevron.
    //
    // It used to be two hand-placed strokes at a 1.8 dp weight, so a disclosure chevron beside a
    // 19 dp glyph looked like it came from a different product. It now draws the same 24-unit
    // CHEVRON every other surface draws, mirrored on request, at a weight that scales with size.
    Canvas(modifier.size(16.dp)) {
        drawMarbleGlyph(
            glyph = MarbleGlyph.CHEVRON,
            tint = color,
            weight = .92f,
            mirror = mirror
        )
    }
}
