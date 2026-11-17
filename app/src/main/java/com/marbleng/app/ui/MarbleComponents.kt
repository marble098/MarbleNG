@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.headerCollapseFraction
import com.marbleng.app.ui.theme.MarbleVeil
import com.marbleng.app.ui.theme.MarbleElevation
import com.marbleng.app.ui.theme.MarbleCardShape
import com.marbleng.app.ui.theme.MarbleLevelSpec
import com.marbleng.app.ui.theme.MarbleMeasure
import com.marbleng.app.ui.theme.MarbleSkeletonSpec
import com.marbleng.app.ui.theme.MarbleSpace
import com.marbleng.app.ui.theme.MarbleStatSpec
import com.marbleng.app.ui.theme.MarbleWash
import com.marbleng.app.ui.theme.MarbleControlShape
import com.marbleng.app.ui.theme.MarbleFieldShape
import com.marbleng.app.ui.theme.MarbleRowIconShape
import com.marbleng.app.ui.theme.MarbleSheetShape
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.rememberReducedMotion

// MARBLE_M3_EXPRESSIVE_V240 — the expressive component library: every shared surface of the
// app built on Material 3 Expressive components, the expressive motion scheme and the
// dynamic/fallback color system. One clear feedback language across all of them:
// springs for motion, ripples for press, haptics for selection and key actions.

/**
 * Contained group card: tonal container, expressive 24dp rounding and **no outline**.
 *
 * MARBLE_EXPRESSIVE_SHELL_V241 — the audit called the old cards "hundreds of rounded
 * rectangles with grey lines between them". A card is now a change of surface tone, not a
 * border: `borderColor` exists only for the two places that must shout (a live error), and
 * the elevation stays at zero in flat mode so a stack of cards reads as layers of one sheet.
 * Rows inside are separated by air ([MarbleDivider]) instead of hairlines.
 */
@Composable
fun MarbleCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    borderColor: Color? = null,
    shape: Shape = MarbleCardShape,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevated: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val flat = MarbleTheme.flat
    // MARBLE_ONE_LANGUAGE_V281 — the four intake cells were the last raw `Card(` in the product:
    // their own corner, their own colours and their own elevation, beside a kit card that already
    // answered all three. The kit card now takes those as *parameters*, so a tappable cell and a
    // static section are one component with one geometry, and the corner they share is the control
    // rung because a cell you tap is a control.
    val cardColors = CardDefaults.cardColors(
        containerColor = containerColor
            ?: if (flat) colors.surfaceContainerLow else colors.surfaceContainer,
        contentColor = contentColor ?: colors.onSurface
    )
    val cardElevation = CardDefaults.cardElevation(
        defaultElevation = when {
            !elevated || flat -> MarbleElevation.FLAT_DP.dp
            else -> MarbleElevation.TONAL_DP.dp
        }
    )
    val cardBorder = borderColor?.let { BorderStroke(1.dp, it) }
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = cardColors,
            elevation = cardElevation,
            border = cardBorder,
            content = cardContent
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = cardColors,
            elevation = cardElevation,
            border = cardBorder,
            content = cardContent
        )
    }
}


/**
 * Tonal row icon: the tone as a wash behind a full-tone glyph. Replaces the old solid
 * squircles — same scannability, AA-safe on any scheme, dynamic-color friendly.
 */
@Composable
internal fun MarbleRowIcon(
    glyph: MarbleGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = MarbleSpec.ROW_ICON_SIZE_DP.dp,
    glyphSize: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MarbleRowIconShape)
            .background(tint.copy(alpha = MarbleWash.TINT)),
        contentAlignment = Alignment.Center
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = tint,
            modifier = Modifier.size(glyphSize),
            weight = 1.08f
        )
    }
}

/**
 * Disclosure chevron: renders `<` in RTL and `>` in LTR.
 */
@Composable
fun MarbleChevron(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 16.dp
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    MarbleGlyphIcon(
        glyph = if (isRtl) MarbleGlyph.CHEVRON_LEFT else MarbleGlyph.CHEVRON,
        color = tint,
        modifier = modifier.size(size)
    )
}

/**
 * MARBLE_EXPRESSIVE_SHELL_V241 — the row separator is air now, not a line.
 *
 * The name is kept because thirty call sites already ask for "a separator between two rows";
 * what changed is the answer. Two rows in one tonal group are separated by
 * [MarbleSpec.ROW_GAP_DP] of empty space, which is how M3 expressive groups read: tone says
 * "same group", air says "different row". A hairline said both, which is why every page used
 * to look like an office spreadsheet.
 */
@Composable
fun MarbleDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp = 0.dp,
    insetEnd: Dp = 0.dp
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = insetStart, end = insetEnd)
            .height(MarbleSpec.ROW_GAP_DP.dp)
    )
}

/** The softer twin of [MarbleDivider]; it is the same air, kept for call-site compatibility. */
@Composable
fun MarbleFadeDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp = 16.dp
) {
    MarbleDivider(modifier = modifier, insetStart = insetStart)
}

/**
 * Standard settings/server row:
 * - Leading: tonal icon chip (or a custom slot such as a server avatar)
 * - Body: title + subtitle
 * - Trailing: value text / control / chevron
 */
@Composable
internal fun MarbleListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    value: String = "",
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = MarbleSpec.ROW_MIN_HEIGHT_DP.dp)
        .alpha(if (enabled) 1f else 0.45f)
        .then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = MarbleSpace.LG.dp, vertical = MarbleSpace.MD.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.TILE.dp)
    ) {
        when {
            leading != null -> leading()
            glyph != null -> MarbleRowIcon(glyph = glyph, tint = iconTint)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (value.isNotBlank()) {
            Text(
                text = value,
                modifier = Modifier.widthIn(max = 120.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            trailing()
        }

        if (showChevron && onClick != null && trailing == null) {
            MarbleChevron()
        }
    }
}

/**
 * Expressive switch with haptic selection feedback. The checked track follows [activeColor]
 * (default: primary) instead of the old fixed green.
 */
@Composable
fun MarbleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(
                if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
            )
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = if (activeColor.luminance() > 0.179f) Color.Black else Color.White,
            checkedTrackColor = activeColor,
            checkedBorderColor = Color.Transparent
        )
    )
}

@Composable
internal fun MarbleSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    MarbleListItem(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        iconTint = iconTint,
        showChevron = false,
        enabled = enabled,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            MarbleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

@Composable
internal fun MarbleChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    badge: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    MarbleListItem(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        iconTint = iconTint,
        value = badge,
        showChevron = false,
        enabled = enabled,
        modifier = modifier,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        trailing = {
            if (selected) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CHECK,
                    color = colors.primary,
                    modifier = Modifier.size(20.dp),
                    weight = 1.2f
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    )
}

@Composable
internal fun MarbleNumberStepperRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    suffix: String = "",
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onValueChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val minusInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val plusInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isMinusPressed by minusInteraction.collectIsPressedAsState()
    val isPlusPressed by plusInteraction.collectIsPressedAsState()

    // MARBLE_M3_EXPRESSIVE_V242 — long-press continuous auto-increment with haptic tick,
    // started after a short hold. The spring-driven pill and AnimatedContent number flip
    // replace the cramped +- pair the audit called \"squeezed\".
    LaunchedEffect(isMinusPressed) {
        if (!isMinusPressed) return@LaunchedEffect
        kotlinx.coroutines.delay(420)
        while (isMinusPressed) {
            val current = latestValue
            val next = (current - step).coerceIn(range)
            if (next == current) break
            latestOnChange(next)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            kotlinx.coroutines.delay(90)
        }
    }
    LaunchedEffect(isPlusPressed) {
        if (!isPlusPressed) return@LaunchedEffect
        kotlinx.coroutines.delay(420)
        while (isPlusPressed) {
            val current = latestValue
            val next = (current + step).coerceIn(range)
            if (next == current) break
            latestOnChange(next)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            kotlinx.coroutines.delay(90)
        }
    }

    MarbleListItem(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        iconTint = iconTint,
        showChevron = false,
        trailing = {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
                    .padding(MarbleSpace.XS.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)
            ) {
                // Minus capsule button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (value > range.first) colors.surfaceContainerHigh else colors.surfaceContainerHighest
                        )
                        .clickable(
                            enabled = value > range.first,
                            interactionSource = minusInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            latestOnChange((latestValue - step).coerceIn(range))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (value > range.first) colors.onSurface else colors.outline
                    )
                }

                // Animated number flip - expressive pill center
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )) togetherWith slideOutVertically { height -> -height / 2 } + fadeOut()
                    },
                    label = "stepperNumber"
                ) { target ->
                    Box(
                        modifier = Modifier.width(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$target$suffix",
                            color = colors.primary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Plus capsule button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (value < range.last) colors.surfaceContainerHigh else colors.surfaceContainerHighest
                        )
                        .clickable(
                            enabled = value < range.last,
                            interactionSource = plusInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            latestOnChange((latestValue + step).coerceIn(range))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (value < range.last) colors.onSurface else colors.outline
                    )
                }
            }
        }
    )
}

@Composable
fun MarbleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String = "",
    badgeColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MarbleSpace.ROW_GAP.dp, vertical = MarbleSpace.HAIR.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge.isNotBlank()) {
                MarbleBadge(text = badge, color = badgeColor ?: colors.primary)
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Friendly empty/error state: a Cookie-shape tonal illustration, an emphasized title,
 * calm body copy and an optional action slot.
 */
@Composable
internal fun MarbleEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyph: MarbleGlyph = MarbleGlyph.SERVER,
    tint: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    MarbleCard(
        modifier = modifier,
        contentPadding = PaddingValues(MarbleSpace.XXL.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(marbleExpressiveShape(MarbleExpressiveMotif.forGlyph(glyph)))
                    .background(tint.copy(alpha = MarbleWash.TINT)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = glyph,
                    color = tint,
                    modifier = Modifier.size(28.dp),
                    weight = 1.1f
                )
            }
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleLargeEmphasized,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

/** Breathing liveness dot; renders static under reduced motion. */
@Composable
internal fun MarblePulseDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "marblePulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900)),
        label = "marblePulseScale"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun MarbleSectionFooter(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MarbleSpace.SM.dp, vertical = MarbleSpace.HAIR.dp)
    )
}

enum class MarbleButtonStyle {
    PRIMARY,
    TINTED,
    SECONDARY,
    DESTRUCTIVE
}

/**
 * Expressive buttons: filled, tonal, outlined and error-tonal, with press ripples,
 * spring motion from the theme and light haptic confirmation.
 */
@Composable
internal fun MarbleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MarbleButtonStyle = MarbleButtonStyle.PRIMARY,
    glyph: MarbleGlyph? = null,
    colorOverride: Color? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val tapped: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onClick()
    }
    val content: @Composable RowScope.() -> Unit = {
        if (glyph != null) {
            MarbleGlyphIcon(
                glyph = glyph,
                color = Color.Unspecified,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    val sized = modifier.heightIn(min = if (compact) 48.dp else 52.dp)
    when (style) {
        MarbleButtonStyle.PRIMARY -> Button(
            onClick = tapped,
            modifier = sized,
            enabled = enabled,
            colors = if (colorOverride != null) {
                ButtonDefaults.buttonColors(
                    containerColor = colorOverride,
                    contentColor = if (colorOverride.luminance() > 0.179f) Color.Black else Color.White
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            content = content
        )

        MarbleButtonStyle.TINTED -> FilledTonalButton(
            onClick = tapped,
            modifier = sized,
            enabled = enabled,
            colors = if (colorOverride != null) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorOverride.copy(alpha = MarbleWash.TINT),
                    contentColor = colorOverride
                )
            } else {
                ButtonDefaults.filledTonalButtonColors()
            },
            content = content
        )

        MarbleButtonStyle.SECONDARY -> OutlinedButton(
            onClick = tapped,
            modifier = sized,
            enabled = enabled,
            content = content
        )

        MarbleButtonStyle.DESTRUCTIVE -> FilledTonalButton(
            onClick = tapped,
            modifier = sized,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer
            ),
            content = content
        )
    }
}

/**
 * Connected single-select control built on the expressive ButtonGroup: selected items
 * morph shape and tone with springs, and overflow collapses into a "more" menu.
 */
@Composable
fun <T> MarbleSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    ButtonGroup(
        modifier = modifier.fillMaxWidth(),
        overflowIndicator = { menuState ->
            FilledIconButton(
                onClick = {
                    if (menuState.isShowing) menuState.dismiss() else menuState.show()
                }
            ) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.MORE,
                    color = colors.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    ) {
        options.forEach { (item, label) ->
            toggleableItem(
                checked = item == selected,
                label = label,
                weight = 1f,
                onCheckedChange = { checked ->
                    if (checked) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(item)
                    }
                }
            )
        }
    }
}

/**
 * Selectable chip with a check affordance when on. Selected state is tonal (accent wash
 * + accent ink) so it stays contrast-safe for every accent, including amber.
 */
@Composable
fun MarblePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
        },
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CHECK,
                    color = accent,
                    modifier = Modifier.size(16.dp),
                    weight = 1.2f
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = MarbleWash.TINT),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent
        )
    )
}

@Composable
fun MarbleCountChip(
    label: String,
    count: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (selected) 0.22f else 0.12f))
                        .padding(horizontal = MarbleSpace.SM.dp, vertical = MarbleSpace.HAIR.dp)
                ) {
                    Text(
                        text = count,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
        },
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CHECK,
                    color = accent,
                    modifier = Modifier.size(16.dp),
                    weight = 1.2f
                )
            }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = MarbleWash.TINT),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent
        )
    )
}

/**
 * Expressive search field: high-contrast filled pill, leading search glyph, live clear
 * action and an optional result-count badge.
 */
@Composable
fun MarbleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingCount: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val clearSearchLabel = trx("Clear search")
    val ime = rememberMarbleImeHider()
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            MarbleGlyphIcon(
                glyph = MarbleGlyph.SEARCH,
                color = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            when {
                value.isNotEmpty() -> IconButton(
                    modifier = Modifier.semantics { contentDescription = clearSearchLabel },
                    onClick = { onValueChange("") }
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CLOSE,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                trailingCount.isNotBlank() -> MarbleBadge(
                    text = trailingCount,
                    color = colors.primary
                )
            }
        },
        singleLine = true,
        shape = MarbleFieldShape,
        keyboardOptions = marbleImeOptions(ImeAction.Search),
        keyboardActions = marbleImeActions(ime),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHighest,
            unfocusedContainerColor = colors.surfaceContainerHighest,
            focusedIndicatorColor = colors.primary,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun MarbleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    monospace: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 10,
    isError: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val ime = rememberMarbleImeHider()
    val textStyle: TextStyle = if (monospace) {
        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodyLarge
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        textStyle = textStyle,
        shape = MarbleControlShape,
        keyboardOptions = marbleImeOptions(if (singleLine) ImeAction.Done else ImeAction.None),
        keyboardActions = marbleImeActions(ime),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.primary,
            cursorColor = colors.primary
        )
    )
}

@Composable
fun MarbleSecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val ime = rememberMarbleImeHider()
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        shape = MarbleControlShape,
        keyboardOptions = marbleImeOptions(ImeAction.Done),
        keyboardActions = marbleImeActions(ime),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                MarbleGlyphIcon(
                    glyph = if (visible) MarbleGlyph.EYE_OFF else MarbleGlyph.EYE,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.primary,
            cursorColor = colors.primary
        )
    )
}

data class MarbleMenuOption(
    val value: String,
    val label: String = value,
    val detail: String = "",
    val enabled: Boolean = true
)

/**
 * Read-only dropdown field: filled tonal box with a spring-rotating chevron and an
 * expressive menu. Never owns the keyboard (read-only by construction).
 */
@Composable
fun MarbleDropdownField(
    label: String,
    value: String,
    options: List<MarbleMenuOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: value
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = marbleDefaultSpatialSpec(),
        label = "marbleDropdownChevron"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(MarbleControlShape)
                .background(colors.surfaceContainerHighest)
                .marbleImeInert()
                .clickable(
                    enabled = options.any { it.enabled },
                    role = Role.DropdownList
                ) { expanded = true }
                .padding(horizontal = MarbleSpace.LG.dp, vertical = MarbleSpace.MD.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)) {
                Text(
                    text = label,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = selectedLabel,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MarbleGlyphIcon(
                glyph = MarbleGlyph.CHEVRON_DOWN,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.surfaceContainerHigh
        ) {
            options.forEach { option ->
                val isSelected = option.value == value
                DropdownMenuItem(
                    enabled = option.enabled,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option.value)
                        expanded = false
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)) {
                            Text(
                                text = option.label,
                                color = if (option.enabled) colors.onSurface else colors.outline,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                            if (option.detail.isNotBlank()) {
                                Text(
                                    text = option.detail,
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            MarbleGlyphIcon(
                                glyph = MarbleGlyph.CHECK,
                                color = colors.primary,
                                modifier = Modifier.size(18.dp),
                                weight = 1.2f
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * Tonal header icon button: 48dp touch target with a high-contrast container, so header
 * actions are reachable and legible in both modes.
 */
@Composable
internal fun MarbleIconButton(
    glyph: MarbleGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    motif: MarbleExpressiveMotif = MarbleExpressiveMotif.forGlyph(glyph),
    prominent: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = marbleMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // MARBLE_ONE_LANGUAGE_V281 — this was two components doing one job: a calm `FilledTonalIconButton`
    // here and an expressive polygon-morphing `MarbleExpressiveIconButton` in the surfaces file, so a
    // header action on Home morphed on press while the same action on Servers did not. One icon
    // button now, and its press feedback is the Material 3 Expressive one every other control in the
    // product already has: the glyph's own polygon at rest, a circle under the thumb, a slight scale
    // and a counter-rotation that keeps the mark upright while the shape turns.
    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "marbleIconBtnMorph"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) MarbleSpec.PRESSED_SCALE else 1f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "marbleIconBtnScale"
    )
    val spin by animateFloatAsState(
        targetValue = if (pressed) -16f else 0f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "marbleIconBtnSpin"
    )
    val size = if (prominent) MarbleSpec.HEADER_ACTION_SIZE_DP + 4 else MarbleSpec.HEADER_ACTION_SIZE_DP
    val glyphSize = if (prominent) 22 else 20
    val shape = marbleMorphingShape(
        from = motif,
        to = MarbleExpressiveMotif.CIRCLE,
        progress = morphProgress
    )
    val described = if (contentDescription.isNotBlank()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Box(
        modifier = described
            .size(size.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                rotationZ = spin
            }
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = MarbleVeil.FADED))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = if (enabled) tint else colors.outline,
            modifier = Modifier
                .size(glyphSize.dp)
                .graphicsLayer { rotationZ = -spin },
            weight = if (prominent) 1.15f else 1.08f
        )
    }
}

/**
 * MARBLE_EXPRESSIVE_SHELL_V241 — how far a list has scrolled towards a collapsed title, 0..1.
 *
 * Pure arithmetic on the lazy-list measurements so `ExpressiveShellV241Test` can pin the curve
 * without a device: the first page collapses linearly over
 * [MarbleSpec.HEADER_COLLAPSE_DISTANCE_PX]; any page after it is already fully collapsed.
 */
fun collapseFractionOf(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    distancePx: Int = MarbleSpec.HEADER_COLLAPSE_DISTANCE_PX
): Float = headerCollapseFraction(
    firstVisibleItemIndex = firstVisibleItemIndex,
    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    distancePx = distancePx
)

/**
 * Large display header for primary tabs: emphasized display title, calm subtitle and a
 * trailing action row.
 *
 * [collapseFraction] (0 = at rest, 1 = fully collapsed) shrinks the title from the display
 * rung into the toolbar rung as the page scrolls under it, and fades the subtitle out. The
 * default 0 keeps every page that has not opted in exactly as it was.
 */
@Composable
fun MarbleLargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    collapseFraction: Float = 0f,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val fraction = collapseFraction.coerceIn(0f, 1f)
    val expanded = MaterialTheme.typography.headlineLarge
    val collapsed = MaterialTheme.typography.titleMedium
    val titleStyle = expanded.copy(
        fontSize = lerp(expanded.fontSize, collapsed.fontSize, fraction),
        lineHeight = lerp(expanded.lineHeight, collapsed.lineHeight, fraction)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MarbleSpace.SM.dp, bottom = MarbleSpace.XS.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MarbleSpace.XS.dp)
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank() && fraction < 0.98f) {
                Text(
                    text = subtitle,
                    color = colors.onSurfaceVariant.copy(alpha = 1f - fraction),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun MarbleBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = MarbleWash.TINT))
            .padding(horizontal = MarbleSpace.INNER.dp, vertical = MarbleSpace.XS.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

/**
 * Expressive bottom sheet: high tonal container, wide top rounding, title + subtitle +
 * close affordance. Predictive back and scrim dimming come from ModalBottomSheet.
 */
@Composable
fun MarbleBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String = "",
    scrollable: Boolean = false,
    showsClose: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        shape = MarbleSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
                )
                .padding(
                    start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    top = MarbleSpace.XS.dp,
                    bottom = navBottom + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(MarbleSpace.LG.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)) {
                    Text(
                        text = title,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // MARBLE_ONE_LANGUAGE_V281 — a sheet that reads out a state (the core log) carries
                // that state where the close button would be, and dismisses by scrim or swipe like
                // every other sheet. Both are parameters of one sheet, not a second sheet.
                if (trailing != null) {
                    trailing()
                }
                if (showsClose) {
                    FilledTonalIconButton(onClick = onDismiss) {
                        MarbleGlyphIcon(
                            glyph = MarbleGlyph.CLOSE,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * Expressive alert dialog: high tonal container, emphasized title, text-button actions
 * with error-red destructive confirmation.
 */
@Composable
fun MarbleAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = Tr.now.cancel,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    glyph: MarbleGlyph? = null,
    accent: Color? = null,
    actionsAsButtons: Boolean = false,
    body: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val titleTint = accent ?: colors.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        shape = MarbleCardShape,
        title = {
            // MARBLE_ONE_LANGUAGE_V281 — the permission dialog was the product's second alert: the
            // same container and corner, but its own title row with a row icon and its own two
            // buttons. A dialog is one role, so the kit now carries the icon and the button actions
            // and that second drawing is gone.
            if (glyph != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp)
                ) {
                    MarbleRowIcon(glyph = glyph, tint = titleTint)
                    Text(
                        text = title,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            } else {
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp), content = body)
        },
        confirmButton = {
            if (actionsAsButtons) {
                MarbleButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    style = if (destructive) MarbleButtonStyle.DESTRUCTIVE else MarbleButtonStyle.PRIMARY,
                    colorOverride = if (destructive) null else accent,
                    enabled = confirmEnabled,
                    compact = true
                )
            } else {
                TextButton(
                    enabled = confirmEnabled,
                    onClick = {
                        haptics.performHapticFeedback(
                            if (destructive) HapticFeedbackType.LongPress else HapticFeedbackType.VirtualKey
                        )
                        onConfirm()
                    }
                ) {
                    Text(
                        text = confirmLabel,
                        color = if (destructive) colors.error else colors.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        dismissButton = {
            if (actionsAsButtons) {
                MarbleButton(
                    label = dismissLabel,
                    onClick = onDismiss,
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(0.36f)
                )
            } else {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    )
}

@Composable
fun MarbleChipStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ---------------------------------------------------------------------------------------------
// MARBLE_ONE_LANGUAGE_V281 — one component per role.
//
// The audit behind this pass counted five private implementations of "a label and a value in a
// tonal box" (Servers, Subscriptions, the jitter page, the council page and the IP page), two of
// "a label and a value on one row", two loading skeletons breathing at two different speeds, three
// hand-styled copies of the same slider and four-bar level meters typed as literal heights. Each
// was reasonable alone; side by side they were five answers to one question, and the reader could
// see the seams — a tile on the Servers page was 14 dp round with 7 dp of air and the same tile in
// Settings was 16 dp round with 9 dp of air.
//
// The five roles below are now drawn once. A page keeps its own composable name, its own test tag
// and its own call sites; what it no longer keeps is a second opinion about geometry, type or
// rhythm. Every number they use comes from `theme/MarbleTokens.kt`.
// ---------------------------------------------------------------------------------------------

/** The voice a metric row speaks in: a number you compare, or a string you read. */
enum class MarbleMetricVoice {
    /** A measurement: tabular, bold, one line — two readings must align digit by digit. */
    MEASURED,

    /** A literal you read: an address, a name, a path. It wraps instead of aligning. */
    READING
}

/**
 * The one loading breath.
 *
 * Two skeletons used to breathe at two speeds (900 ms between 0.24 and 0.62, 1200 ms between 0.35
 * and 0.70) on two pages a reader moves between, so "still working" looked like two different
 * products working. One rhythm now, from [MarbleSkeletonSpec], and one still value when the reader
 * has asked the product to stop moving.
 *
 * The returned number is the *breath*, not an alpha: a caller multiplies it by the wash its own
 * surface needs ([MarbleWash.TINT] for a reading block, full strength for a pill that is only
 * 26 dp wide and has to stay visible).
 */
@Composable
fun marbleSkeletonBreath(label: String = "marbleSkeleton"): Float {
    // A breath is decorative by definition, so the two ways a reader can ask for quiet — the app's
    // own calm-motion setting and the platform's "remove animations" — both hold it at its middle.
    if (!marbleMotion().loops) {
        return (MarbleSkeletonSpec.BREATH_MIN + MarbleSkeletonSpec.BREATH_MAX) / 2f
    }
    val transition = rememberInfiniteTransition(label = label)
    val breath by transition.animateFloat(
        initialValue = MarbleSkeletonSpec.BREATH_MIN,
        targetValue = MarbleSkeletonSpec.BREATH_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MarbleSkeletonSpec.BREATH_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "${label}Breath"
    )
    return breath
}

/**
 * The one loading placeholder: a quiet block that breathes where content is about to be.
 *
 * Height, corner and ink are parameters because a page needs both a one-line placeholder and a
 * whole-card one. [strength] is a parameter for the same reason: a placeholder that sits behind text
 * breathes at a tint, while a placeholder that *is* the mark — the ping pill, 26 dp of it beside a
 * wave ring — has to breathe at full strength or it disappears.
 *
 * The *rhythm* is never a parameter, because that is what makes two placeholders on two pages read
 * as the same product waiting.
 */
@Composable
fun MarbleSkeleton(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = MarbleSkeletonSpec.LINE_HEIGHT_DP.dp,
    shape: Shape = RoundedCornerShape(MarbleSkeletonSpec.CORNER_DP.dp),
    ink: Color = MaterialTheme.colorScheme.onSurface,
    strength: Float = MarbleWash.TINT,
    label: String = "marbleSkeleton"
) {
    val breath = marbleSkeletonBreath(label = label)
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .background(ink.copy(alpha = breath * strength))
    )
}

/**
 * The one stat tile: a label and a value on a tonal ground.
 *
 * This is the surface every measurement in the product sits on — a subscription's remaining days,
 * a jitter reading, an address family, a core's pinned tag. One corner ([MarbleStatSpec.CORNER_DP]),
 * one wash ([MarbleStatSpec.WASH_ALPHA]), one air between the label and the value
 * ([MarbleStatSpec.GAP_DP]) and two densities, so the same tile can be a page's focal number or one
 * of three inside another card.
 *
 * The two type voices are the theme's own: [emphasized] prints the value in the emphasized twin of
 * `titleMedium`, and the resting voice prints it tabular so two tiles side by side align.
 *
 * [flush] drops the ground and the inner padding for the one place a measurement sits on a surface
 * that is already its container — four live numbers inside a card — so even there the label voice,
 * the value voice and the air between them are this component's and not a fourth private opinion.
 */
@Composable
fun MarbleStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    wash: Color = tint,
    ground: Color? = null,
    emphasized: Boolean = false,
    valueFirst: Boolean = false,
    centered: Boolean = false,
    compact: Boolean = false,
    flush: Boolean = false,
    tabularValue: Boolean = true,
    glyph: MarbleGlyph? = null,
    trailing: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(MarbleStatSpec.CORNER_DP.dp),
    tag: String = ""
) {
    val colors = MaterialTheme.colorScheme
    // A flush tile is the same measurement with no ground of its own: the surface under it is
    // already the container, so the wash and the inner padding would be a second box inside a box.
    val paddingH = if (flush) 0 else if (compact) {
        MarbleStatSpec.COMPACT_PADDING_H_DP
    } else {
        MarbleStatSpec.PADDING_H_DP
    }
    val paddingV = if (flush) 0 else if (compact) {
        MarbleStatSpec.COMPACT_PADDING_V_DP
    } else {
        MarbleStatSpec.PADDING_V_DP
    }
    val alignment = if (centered) TextAlign.Center else TextAlign.Start

    val labelRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) {
                Arrangement.spacedBy(MarbleSpace.XS.dp, Alignment.CenterHorizontally)
            } else {
                Arrangement.spacedBy(MarbleSpace.XS.dp)
            }
        ) {
            if (glyph != null) {
                MarbleGlyphIcon(
                    glyph = glyph,
                    color = tint,
                    modifier = Modifier.size(MarbleMeasure.GLYPH_SM_DP.dp)
                )
            }
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = alignment,
                modifier = if (trailing != null) Modifier.weight(1f) else Modifier
            )
            if (trailing != null) {
                trailing()
            }
        }
    }
    val valueText: @Composable () -> Unit = {
        Text(
            text = value,
            color = tint,
            style = if (emphasized) {
                MaterialTheme.typography.titleMediumEmphasized
            } else {
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = if (tabularValue) "tnum" else null
                )
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = alignment
        )
    }

    Column(
        modifier = modifier
            .clip(shape)
            .then(if (ground != null) Modifier.background(ground) else Modifier)
            .then(
                if (flush) Modifier else Modifier.background(wash.copy(alpha = MarbleStatSpec.WASH_ALPHA))
            )
            .then(if (tag.isEmpty()) Modifier else Modifier.testTag(tag))
            .padding(horizontal = paddingH.dp, vertical = paddingV.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MarbleStatSpec.GAP_DP.dp)
    ) {
        if (valueFirst) {
            valueText()
            labelRow()
        } else {
            labelRow()
            valueText()
        }
    }
}

/**
 * The one metric row: a label on the leading edge, its value on the trailing edge.
 *
 * Rows are what a card full of facts uses when the facts are sentences rather than numbers — the
 * council's measurements, the IP page's copyable literals. One geometry ([MarbleSpace.LG] across,
 * [MarbleSpace.INNER] down), one label voice, and a value that either aligns as a measurement or
 * wraps as something you read ([MarbleMetricVoice]).
 */
@Composable
fun MarbleMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.onSurface,
    voice: MarbleMetricVoice = MarbleMetricVoice.MEASURED,
    monospaceValue: Boolean = false,
    glyph: MarbleGlyph? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
    valueLines: Int = 2,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val measured = voice == MarbleMetricVoice.MEASURED
    val valueStyle = if (measured) {
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
            fontFamily = if (monospaceValue) FontFamily.Monospace else null
        )
    } else {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = MarbleSpace.LG.dp,
                vertical = MarbleSpace.INNER.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.TILE.dp)
    ) {
        if (glyph != null) {
            MarbleRowIcon(
                glyph = glyph,
                tint = iconTint,
                size = MarbleMeasure.ICON_WELL_DP.dp - MarbleSpace.XS.dp,
                glyphSize = MarbleMeasure.GLYPH_MD_DP.dp
            )
        }
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = tone,
            style = valueStyle,
            maxLines = valueLines,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * The one level meter: a ladder of bars that says how far up a short scale a reading is.
 *
 * The heights come from [MarbleLevelSpec.barHeightDp] rather than from a list somebody typed, so a
 * meter on Servers and a meter anywhere else are the same object at the same size, and an unlit bar
 * is one sanctioned wash ([MarbleWash.TINT]) of the neutral ink rather than a second grey.
 */
@Composable
fun MarbleLevelBars(
    level: Int,
    modifier: Modifier = Modifier,
    steps: Int = MarbleLevelSpec.STEPS,
    tone: Color = MaterialTheme.colorScheme.primary,
    idleTone: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MarbleLevelSpec.GAP_DP.dp)
    ) {
        repeat(steps) { index ->
            Box(
                modifier = Modifier
                    .width(MarbleLevelSpec.BAR_WIDTH_DP.dp)
                    .height(MarbleLevelSpec.barHeightDp(index).dp)
                    .clip(CircleShape)
                    .background(
                        if (index < level) tone else idleTone.copy(alpha = MarbleWash.TINT)
                    )
            )
        }
    }
}

/**
 * The one slider.
 *
 * Three settings pages had each styled their own copy of this control with the same three colours,
 * so the app had three sliders that agreed by accident. The thumb and the active track take the
 * page's accent, the rest of the track is the scheme's quiet line, and nothing else about it is a
 * choice a page gets to make.
 */
@Composable
fun MarbleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = tint,
            activeTrackColor = tint,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}
