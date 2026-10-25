package com.marbleng.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.dp

val IosCardShape = RoundedCornerShape(IosSpec.CARD_CORNER_DP.dp)
val IosSquircleShape = RoundedCornerShape(IosSpec.SQUIRCLE_CORNER_DP.dp)
val IosControlShape = RoundedCornerShape(14.dp)
val IosFieldShape = RoundedCornerShape(12.dp)

/**
 * Council edition — card as lit object: opaque container, directional rim, optional
 * specular wash at the top edge. In light mode the shadow lifts the card off the
 * #F8F9FA canvas; in flat mode the shadow is skipped but the rim remains.
 */
@Composable
fun IosCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = IosTheme.colors
    val flat = IosTheme.flat
    val stroke = borderColor ?: colors.cardBorder
    val shadowModifier = if (flat || colors.dark) {
        Modifier
    } else {
        Modifier.shadow(
            elevation = 3.dp,
            shape = IosCardShape,
            clip = false,
            ambientColor = colors.softShadow,
            spotColor = colors.softShadow
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .clip(IosCardShape)
            .background(colors.card)
            .border(0.85.dp, stroke, IosCardShape)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Colored 12dp-radius squircle icon container with a crisp white glyph.
 */
@Composable
internal fun IosSquircleIcon(
    glyph: MarbleGlyph,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = IosSpec.SQUIRCLE_SIZE_DP.dp,
    glyphSize: Dp = IosSpec.SQUIRCLE_GLYPH_DP.dp
) {
    val glyphTint = IosTheme.colors.squircleGlyph
    Box(
        modifier = modifier
            .size(size)
            .clip(IosSquircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = glyphTint,
            modifier = Modifier.size(glyphSize),
            weight = 1.08f
        )
    }
}

/**
 * Light gray disclosure chevron: renders `chevron-left` (<) in RTL and `chevron-right` (>) in LTR.
 */
@Composable
fun IosChevron(
    modifier: Modifier = Modifier,
    tint: Color = IosTheme.colors.chevron,
    size: Dp = IosSpec.CHEVRON_SIZE_DP.dp
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    MarbleGlyphIcon(
        glyph = if (isRtl) MarbleGlyph.CHEVRON_LEFT else MarbleGlyph.CHEVRON,
        color = tint,
        modifier = modifier.size(size)
    )
}

/**
 * 0.75dp hairline that fades towards its trailing edge — reads as separator,
 * not as table border. Council chose fading over solid to de-emphasize list chrome.
 */
@Composable
fun IosDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp = 66.dp,
    insetEnd: Dp = 0.dp
) {
    HorizontalDivider(
        modifier = modifier.padding(start = insetStart, end = insetEnd),
        thickness = 0.75.dp,
        color = IosTheme.colors.divider.copy(alpha = 0.95f)
    )
}

@Composable
fun IosFadeDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp = 16.dp
) {
    val colors = IosTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = insetStart)
            .height(0.75.dp)
            .background(colors.divider.copy(alpha = 0.7f))
    )
}

/**
 * Standard iOS Card List Item:
 * - Leading (rightmost in RTL, leftmost in LTR): 12dp colored squircle icon with white glyph (or custom slot)
 * - Body: Title (Bold, dark text) + Subtitle (Regular, light gray text)
 * - Trailing (far left in RTL, far right in LTR): value / switch / light gray chevron-left (RTL) or chevron-right (LTR)
 */
@Composable
internal fun IosListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    squircleColor: Color = IosTheme.colors.blue,
    leading: (@Composable () -> Unit)? = null,
    value: String = "",
    titleColor: Color = IosTheme.colors.title,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = IosTheme.colors
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .heightIn(min = IosSpec.ROW_MIN_HEIGHT_DP.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = IosSpec.ITEM_HORIZONTAL_PADDING_DP.dp,
                vertical = IosSpec.ITEM_VERTICAL_PADDING_DP.dp
            )
    } else {
        modifier
            .fillMaxWidth()
            .heightIn(min = IosSpec.ROW_MIN_HEIGHT_DP.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(
                horizontal = IosSpec.ITEM_HORIZONTAL_PADDING_DP.dp,
                vertical = IosSpec.ITEM_VERTICAL_PADDING_DP.dp
            )
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when {
            leading != null -> leading()
            glyph != null -> IosSquircleIcon(glyph = glyph, backgroundColor = squircleColor)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (value.isNotBlank()) {
            Text(
                text = value,
                color = colors.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            trailing()
        }

        if (showChevron) {
            IosChevron()
        }
    }
}

/**
 * Authentic iOS toggle switch (50dp × 30dp pill track, #34C759 green when active).
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = IosTheme.colors.green
) {
    val colors = IosTheme.colors
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else colors.switchOffTrack,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iosSwitchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iosSwitchThumb"
    )

    Box(
        modifier = modifier
            .width(50.dp)
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(26.dp)
                .shadow(1.5.dp, CircleShape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
internal fun IosSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    squircleColor: Color = IosTheme.colors.green,
    enabled: Boolean = true
) {
    IosListItem(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        squircleColor = squircleColor,
        showChevron = false,
        enabled = enabled,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            IosSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

@Composable
internal fun IosChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    squircleColor: Color = IosTheme.colors.blue,
    enabled: Boolean = true,
    badge: String = ""
) {
    val accent = IosTheme.colors.accent
    IosListItem(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        squircleColor = squircleColor,
        value = badge,
        showChevron = false,
        enabled = enabled,
        modifier = modifier,
        onClick = onClick,
        trailing = {
            if (selected) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CHECK,
                    color = accent,
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
internal fun IosNumberStepperRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    suffix: String = "",
    subtitle: String = "",
    glyph: MarbleGlyph? = null,
    squircleColor: Color = IosTheme.colors.blue,
    onValueChange: (Int) -> Unit
) {
    val colors = IosTheme.colors
    val display = "$value$suffix"
    IosListItem(
        title = title,
        subtitle = subtitle,
        glyph = glyph,
        squircleColor = squircleColor,
        showChevron = false,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = display,
                    color = colors.accent,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.cardSecondary)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 30.dp)
                            .clickable(enabled = value > range.first) {
                                onValueChange((value - step).coerceIn(range))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            color = if (value > range.first) colors.title else colors.tertiary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(18.dp)
                            .align(Alignment.CenterVertically)
                            .background(colors.divider)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 30.dp)
                            .clickable(enabled = value < range.last) {
                                onValueChange((value + step).coerceIn(range))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = if (value < range.last) colors.title else colors.tertiary,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun IosSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String = "",
    badgeColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null
) {
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
                color = IosTheme.colors.subtitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background((badgeColor ?: IosTheme.colors.accent).copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor ?: IosTheme.colors.accent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
internal fun IosEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyph: MarbleGlyph = MarbleGlyph.SERVER,
    tint: Color = IosTheme.colors.blue,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = IosTheme.colors
    IosCard(
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
                    .size(IosSpec.EMPTY_ILLUSTRATION_SIZE_DP.dp)
                    .clip(IosSquircleShape)
                    .background(tint.copy(alpha = 0.12f))
                    .border(0.75.dp, tint.copy(alpha = 0.16f), IosSquircleShape),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = glyph,
                    color = tint,
                    modifier = Modifier.size(26.dp),
                    weight = 1.1f
                )
            }
            Text(
                text = title,
                color = colors.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                color = colors.subtitle,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

@Composable
internal fun IosPulseDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun IosSectionFooter(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = IosTheme.colors.subtitle,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

enum class IosButtonStyle {
    PRIMARY,
    TINTED,
    SECONDARY,
    DESTRUCTIVE
}

@Composable
internal fun IosButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.PRIMARY,
    glyph: MarbleGlyph? = null,
    colorOverride: Color? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val colors = IosTheme.colors
    val baseTone = colorOverride ?: when (style) {
        IosButtonStyle.PRIMARY, IosButtonStyle.TINTED -> colors.accent
        IosButtonStyle.SECONDARY -> colors.subtitle
        IosButtonStyle.DESTRUCTIVE -> colors.red
    }
    val container = when (style) {
        IosButtonStyle.PRIMARY -> baseTone
        IosButtonStyle.TINTED -> baseTone.copy(alpha = if (colors.dark) 0.22f else 0.12f)
        IosButtonStyle.SECONDARY -> colors.cardSecondary
        IosButtonStyle.DESTRUCTIVE -> colors.red.copy(alpha = if (colors.dark) 0.22f else 0.12f)
    }
    val contentColor = when (style) {
        IosButtonStyle.PRIMARY -> Color.White
        IosButtonStyle.TINTED -> baseTone
        IosButtonStyle.SECONDARY -> colors.title
        IosButtonStyle.DESTRUCTIVE -> colors.red
    }

    Row(
        modifier = modifier
            .heightIn(min = if (compact) 38.dp else 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(IosControlShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            MarbleGlyphIcon(
                glyph = glyph,
                color = contentColor,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = contentColor,
            style = if (compact) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun <T> IosSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IosTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.searchFill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { (item, label) ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) colors.card else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (active) colors.title else colors.subtitle,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun IosPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = IosTheme.colors.accent
) {
    val colors = IosTheme.colors
    val fill = if (selected) accent else colors.card
    val ink = if (selected) Color.White else colors.title
    val border = if (selected) accent else colors.cardBorder

    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = ink,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            ),
            maxLines = 1
        )
    }
}

@Composable
fun IosCountChip(
    label: String,
    count: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = IosTheme.colors.accent
) {
    val colors = IosTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) accent else colors.card)
            .border(1.dp, if (selected) accent else colors.cardBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background((if (selected) Color.White else accent).copy(alpha = if (selected) 0.22f else 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = count,
                color = if (selected) Color.White else accent,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
fun IosSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingCount: String = ""
) {
    val colors = IosTheme.colors
    val ime = rememberMarbleImeHider()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(IosFieldShape)
            .background(colors.searchFill)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarbleGlyphIcon(
            glyph = MarbleGlyph.SEARCH,
            color = colors.subtitle,
            modifier = Modifier.size(18.dp)
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = colors.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.title),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = marbleImeOptions(ImeAction.Search),
                keyboardActions = marbleImeActions(ime),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (trailingCount.isNotBlank() && value.isEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = trailingCount,
                    color = colors.accent,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary.copy(alpha = 0.35f))
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.CLOSE,
                    color = colors.card,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
fun IosTextField(
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
    val colors = IosTheme.colors
    val ime = rememberMarbleImeHider()
    val textStyle: TextStyle = if (monospace) {
        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    } else {
        MaterialTheme.typography.bodyMedium
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
        shape = IosControlShape,
        keyboardOptions = marbleImeOptions(if (singleLine) ImeAction.Done else ImeAction.None),
        keyboardActions = marbleImeActions(ime),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.card,
            unfocusedContainerColor = colors.card,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.subtitle,
            focusedTextColor = colors.title,
            unfocusedTextColor = colors.title,
            cursorColor = colors.accent
        )
    )
}

@Composable
fun IosSecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val colors = IosTheme.colors
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
        shape = IosControlShape,
        keyboardOptions = marbleImeOptions(ImeAction.Done),
        keyboardActions = marbleImeActions(ime),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { visible = !visible },
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = if (visible) MarbleGlyph.EYE_OFF else MarbleGlyph.EYE,
                    color = colors.subtitle,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.card,
            unfocusedContainerColor = colors.card,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.subtitle,
            focusedTextColor = colors.title,
            unfocusedTextColor = colors.title,
            cursorColor = colors.accent
        )
    )
}

data class IosMenuOption(
    val value: String,
    val label: String = value,
    val detail: String = "",
    val enabled: Boolean = true
)

@Composable
fun IosDropdownField(
    label: String,
    value: String,
    options: List<IosMenuOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IosTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: value

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(IosControlShape)
                .background(colors.card)
                .border(1.dp, colors.divider, IosControlShape)
                .clickable(enabled = options.any { it.enabled }) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = colors.subtitle,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = selectedLabel,
                    color = colors.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MarbleGlyphIcon(
                glyph = MarbleGlyph.CHEVRON_DOWN,
                color = colors.subtitle,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.card
        ) {
            options.forEach { option ->
                val isSelected = option.value == value
                DropdownMenuItem(
                    enabled = option.enabled,
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = option.label,
                                color = if (option.enabled) colors.title else colors.tertiary,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (option.detail.isNotBlank()) {
                                Text(
                                    text = option.detail,
                                    color = colors.subtitle,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            MarbleGlyphIcon(
                                glyph = MarbleGlyph.CHECK,
                                color = colors.accent,
                                modifier = Modifier.size(16.dp)
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
 * Circular iOS top-bar icon pill button.
 */
@Composable
internal fun IosIconActionButton(
    glyph: MarbleGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = IosTheme.colors.accent,
    enabled: Boolean = true
) {
    val colors = IosTheme.colors
    Box(
        modifier = modifier
            .size(38.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(colors.card)
            .border(0.75.dp, colors.cardBorder, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MarbleGlyphIcon(
            glyph = glyph,
            color = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * iOS Large Title header used at the top of primary tab screens.
 */
@Composable
fun IosLargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = IosTheme.colors
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
                color = colors.title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
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
 * iOS Sub-page Scaffold with `< Back` navigation bar, #F8F9FA background, and 20dp generous spacing.
 */
@Composable
fun IosSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = Tr.now.backLabel,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    bottomClearance: Dp = 104.dp,
    content: LazyListScope.() -> Unit
) {
    val colors = IosTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MarbleGlyphIcon(
                    glyph = if (isRtl) MarbleGlyph.CHEVRON else MarbleGlyph.CHEVRON_LEFT,
                    color = colors.accent,
                    modifier = Modifier.size(18.dp),
                    weight = 1.15f
                )
                Text(
                    text = backLabel,
                    color = colors.accent,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
            }

            Text(
                text = title,
                color = colors.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 88.dp)
            )

            if (trailing != null) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = trailing
                )
            }
        }

        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = IosSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                end = IosSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                top = IosSpec.SECTION_SPACING_DP.dp,
                bottom = bottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(IosSpec.SECTION_SPACING_DP.dp),
            content = content
        )
    }
}

@Composable
fun IosBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = IosTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(colors.chevron)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = IosSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    end = IosSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
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
                        color = colors.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = colors.subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.searchFill)
                        .clickable(role = Role.Button, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.CLOSE,
                        color = colors.subtitle,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun IosAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = Tr.now.cancel,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    body: @Composable ColumnScope.() -> Unit
) {
    val colors = IosTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = IosCardShape,
        title = {
            Text(
                text = title,
                color = colors.title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = body)
        },
        confirmButton = {
            IosButton(
                label = confirmLabel,
                onClick = onConfirm,
                style = if (destructive) IosButtonStyle.DESTRUCTIVE else IosButtonStyle.PRIMARY,
                enabled = confirmEnabled,
                compact = true
            )
        },
        dismissButton = {
            IosButton(
                label = dismissLabel,
                onClick = onDismiss,
                style = IosButtonStyle.SECONDARY,
                compact = true
            )
        }
    )
}

@Composable
fun IosChipStrip(
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
