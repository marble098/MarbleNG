@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.marbleng.app.ui.theme.MarbleCardShape
import com.marbleng.app.ui.theme.MarbleControlShape
import com.marbleng.app.ui.theme.MarbleFieldShape
import com.marbleng.app.ui.theme.MarbleRowIconShape
import com.marbleng.app.ui.theme.MarbleSheetShape
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
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
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val flat = MarbleTheme.flat
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MarbleCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (flat) colors.surfaceContainerLow else colors.surfaceContainer,
            contentColor = colors.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (flat) 0.dp else 2.dp
        ),
        border = borderColor?.let { BorderStroke(1.dp, it) },
        content = {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    )
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
            .background(tint.copy(alpha = 0.14f)),
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
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when {
            leading != null -> leading()
            glyph != null -> MarbleRowIcon(glyph = glyph, tint = iconTint)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                            indication = null
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
                            indication = null
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
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(tint.copy(alpha = 0.14f)),
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
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
                    containerColor = colorOverride.copy(alpha = 0.16f),
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
            selectedContainerColor = accent.copy(alpha = 0.16f),
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
                horizontalArrangement = Arrangement.spacedBy(7.dp)
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
                        .padding(horizontal = 7.dp, vertical = 2.dp)
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
            selectedContainerColor = accent.copy(alpha = 0.16f),
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
                    modifier = Modifier.semantics { contentDescription = trx("Clear search") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    enabled: Boolean = true,
    contentDescription: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val describedModifier = if (contentDescription.isNotBlank()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    FilledTonalIconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        modifier = describedModifier.size(48.dp),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = tint
        )
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = if (enabled) tint else colors.outline,
            modifier = Modifier.size(20.dp)
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
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/**
 * Sub-page scaffold: collapsing medium top app bar with a mirrored back affordance,
 * tonal scrolled container, and the standard 20dp section rhythm below.
 */
@Composable
fun MarbleSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = Tr.now.backLabel,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    bottomClearance: Dp = MarbleTheme.bottomClearance,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MarbleGlyphIcon(
                            glyph = if (isRtl) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_LEFT,
                            color = colors.onSurface,
                            modifier = Modifier.size(22.dp),
                            weight = 1.15f
                        )
                    }
                },
                actions = { trailing?.invoke(this) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = colors.surface,
                    scrolledContainerColor = colors.surfaceContainer
                )
            )
        },
        floatingActionButton = { floatingActionButton?.invoke() },
        containerColor = colors.surface
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                top = MarbleSpec.SECTION_SPACING_DP.dp,
                bottom = bottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(MarbleSpec.SECTION_SPACING_DP.dp),
            content = content
        )
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
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
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
                    top = 4.dp,
                    bottom = navBottom + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                FilledTonalIconButton(onClick = onDismiss) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CLOSE,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
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
    body: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        shape = MarbleCardShape,
        title = {
            Text(
                text = title,
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    style = MaterialTheme.typography.labelLarge
                )
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
