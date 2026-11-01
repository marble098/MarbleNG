package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.model.LiquidDockSpec
import com.marbleng.app.ui.theme.marbleMotion
import kotlinx.coroutines.delay

/**
 * MARBLE_LUMEN_DOCK_V256 — the floating navigation dock, rebuilt against the four complaints a
 * screenshot cannot argue with.
 *
 * **1. It clipped.** The bar was a `height`-tall object laid out from the bottom edge, so a
 * three-button phone, a gesture phone and a phone with a notch each put the capsule at a different
 * height — and on the worst of them its lower third sat under the system bar. This container is
 * inset *before* it is measured:
 *
 * ```
 * .windowInsetsPadding(WindowInsets.navigationBars)                      // the system bar itself
 * .windowInsetsPadding(WindowInsets.safeDrawing.only(Horizontal + Top))  // cutouts, hinges, RTL edges
 * ```
 *
 * and then [DockStudio.BOTTOM_MARGIN_DP] of its own air. Two passes rather than
 * `safeDrawingPadding()` *and* `navigationBars`: those two together are the same bug from the other
 * side — the bottom would be paid for twice and the pill would float a navigation-bar too high on
 * every gesture phone.
 *
 * **2. It had one size.** The ladder is 48 / 56 / 64 dp ([DockStudio.heightDp]), and the padding,
 * the glyph and the width of an open capsule are derived from the rung instead of restated per call
 * site.
 *
 * **3. A selected tab kept its caption.** Selecting a tab opens it into a capsule carrying its word
 * for exactly [DockStudio.EXPANDED_HOLD_MS], then relaxes it back to the glyph. The hold is a
 * `LaunchedEffect(selectedIndex)` timer, so pressing the tab that is already selected does not
 * restart it and the dock never has to remember a "which one was transient" flag.
 *
 * **4. The motion was a tween.** Every movement here is
 * `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`:
 * the weight of the opening tab, the tint of the selected capsule, the weight of the glyph. The app's
 * calm-motion switch and Android's own *remove animations* still win — [marbleMotion] clamps the
 * travel and a still motion settles without overshoot.
 *
 * The wings are slots, not this file's business: [leading] carries the connection island, [trailing]
 * the power button, [center] the morphing trigger. Which of them exists is
 * [com.marbleng.app.model.ConnectionWidgetStyle]'s decision, taken by the shell.
 */
@Composable
internal fun MarbleLumenDock(
    tabs: List<MarbleDockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: DockSize = DockSize.MEDIUM,
    showIcons: Boolean = true,
    showLabels: Boolean = false,
    /**
     * MARBLE_FLOATING_DOCK_V258 — the air under the capsule, in dp, chosen by the user. It is a
     * parameter rather than a constant because it is a *setting*: the dock floats, and how far
     * above the gesture bar is the reader's answer, not this file's.
     */
    bottomGap: Dp = DockStudio.DEFAULT_BOTTOM_GAP_DP.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    center: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    if (tabs.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val height = DockStudio.heightDp(size)
    val pillPadding = DockStudio.pillPaddingDp(size)
    val corner = DockStudio.cornerRadiusDp(size)
    val shape = RoundedCornerShape(corner)
    val selection = selectedIndex.coerceIn(0, tabs.lastIndex)

    // The transient caption. Re-armed per *selection*, never per recomposition: tapping the tab
    // that is already selected must not re-open a word the reader has just dismissed.
    var labelOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selection) {
        labelOpen = true
        delay(DockStudio.EXPANDED_HOLD_MS)
        labelOpen = false
    }

    // The brief's pair, with calm motion and the platform's remove-animations honoured on top: a
    // still app does not spring, it arrives.
    val floatSpec: FiniteAnimationSpec<Float> = spring(
        dampingRatio = if (motion.still) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
        stiffness = if (motion.still) Spring.StiffnessHigh else Spring.StiffnessMediumLow
    )
    val colorSpec: FiniteAnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (motion.still) Spring.StiffnessHigh else Spring.StiffnessMediumLow
    )

    // MARBLE_FLOATING_DOCK_V258 — one presentation, always floating. The `attached` branch that
    // laid a full-bleed strip on the gesture bar was the oldest-looking surface in the product and
    // it is gone: the capsule floats, and only its distance from the bottom is configurable.
    val container = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
        )
        .padding(
            start = DockStudio.HORIZONTAL_MARGIN_DP.dp,
            end = DockStudio.HORIZONTAL_MARGIN_DP.dp,
            bottom = bottomGap
        )

    Box(
        modifier = modifier
            .then(container)
            .testTag("lumen-dock")
    ) {
        // Two layers, deliberately. The *surface* is the clipped capsule; the *content* row is not
        // clipped, because a wing's micro tooltip has to be able to leave the pill and float 8dp
        // above it. Clipping the row would have cut that card in half at the capsule's rim.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                // MARBLE_FLOATING_DOCK_V258 — the material: a lifted capsule, not a grey strip.
                // Three passes make it read as an object — a soft elevation shadow, a vertical
                // tonal gradient (bright rim on top, body underneath) and a primary-tinted sheen
                // that picks up whatever palette the user chose.
                .shadow(elevation = 18.dp, shape = shape)
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.surfaceContainerHighest,
                            colors.surfaceContainerHigh
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.primary.copy(alpha = 0.12f),
                            Color.Transparent,
                            colors.tertiary.copy(alpha = 0.07f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant.copy(alpha = 0.55f),
                    shape = shape
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .padding(horizontal = pillPadding.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (leading != null) leading()

            // A centred trigger splits the tabs around itself: half of them lead, half follow, so
            // the button is the middle of the dock and not a fifth slot at one end.
            val centerAt = if (center != null) (tabs.size + 1) / 2 else -1
            tabs.forEachIndexed { index, tab ->
                if (index == centerAt) {
                    center?.let { trigger ->
                        Box(
                            modifier = Modifier.padding(horizontal = pillPadding.dp),
                            contentAlignment = Alignment.Center
                        ) { trigger() }
                    }
                }
                val opening = index == selection && (labelOpen || showLabels)
                // The opening slot takes more of the row than its neighbours, and that ratio is a
                // spring-animated Float: a `weight` that moves *is* a capsule that grows, with no
                // second animation object to keep in step with it.
                val slotWeight by animateFloatAsState(
                    targetValue = if (opening) 1.9f else 1f,
                    animationSpec = floatSpec,
                    label = "lumenTabSlot"
                )
                LumenDockTab(
                    tab = tab,
                    selected = index == selection,
                    expanded = opening,
                    size = size,
                    floatSpec = floatSpec,
                    colorSpec = colorSpec,
                    showIcons = showIcons,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(slotWeight)
                )
            }
            if (centerAt == tabs.size) {
                center?.let { trigger ->
                    Box(
                        modifier = Modifier.padding(horizontal = pillPadding.dp),
                        contentAlignment = Alignment.Center
                    ) { trigger() }
                }
            }

            if (trailing != null) trailing()
        }
    }
}

/**
 * One slot. The glyph is always there; the word arrives inside the slot only while the capsule is
 * open, scaled and faded in (`scaleIn() + fadeIn()`) and leaving the same way, so the collapse reads
 * as the same object returning to rest rather than as a label being deleted.
 */
@Composable
private fun LumenDockTab(
    tab: MarbleDockTab,
    selected: Boolean,
    expanded: Boolean,
    size: DockSize,
    floatSpec: FiniteAnimationSpec<Float>,
    colorSpec: FiniteAnimationSpec<Color>,
    showIcons: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    val container by animateColorAsState(
        targetValue = if (selected) colors.secondaryContainer else Color.Transparent,
        animationSpec = colorSpec,
        label = "lumenTabContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) tab.accent else colors.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "lumenTabContent"
    )
    val glyphWeight by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = floatSpec,
        label = "lumenTabWeight"
    )
    val glyphSize = DockStudio.iconSizeDp(size).dp

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = DockStudio.pillPaddingDp(size).dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = {
                    // MARBLE_LUMEN_DOCK_V256 — the brief's own feedback: a selection that moved,
                    // not a button that was pressed. A tab that is already selected says nothing.
                    if (!selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
            .semantics { contentDescription = tab.label }
            .testTag("lumen-tab-${tab.key}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showIcons) {
                Canvas(modifier = Modifier.size(glyphSize)) {
                    drawMarbleGlyph(glyph = tab.glyph, tint = content, weight = glyphWeight)
                }
            }

            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    if (targetState) {
                        (scaleIn(initialScale = 0.82f) + fadeIn()) togetherWith
                            (scaleOut(targetScale = 0.82f) + fadeOut())
                    } else {
                        fadeIn() togetherWith (scaleOut(targetScale = 0.82f) + fadeOut())
                    }
                },
                label = "lumenTabLabel"
            ) { open ->
                if (open) {
                    Text(
                        text = tab.label,
                        color = content,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            // MARBLE_FLOATING_DOCK_V258 — the word grows with the rung, so a
                            // Grand capsule is not a Slim one with a bigger glyph only.
                            fontSize = DockStudio.labelSp(size).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = if (showIcons) 8.dp else 2.dp, end = 6.dp)
                            .widthIn(max = 92.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(0.dp))
                }
            }
        }

        // The counter is a dot at rest and a number while the capsule is open: the dock may never
        // grow a second row of chrome for a badge, and a reader still has to see that one exists.
        if (tab.badgeCount > 0) {
            if (expanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.error)
                        .padding(horizontal = 5.dp)
                        .testTag("lumen-tab-count-${tab.key}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = faDigits(LiquidDockSpec.badgeLabel(tab.badgeCount)),
                        color = colors.onError,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.error)
                        .semantics { contentDescription = tab.label }
                        .testTag("lumen-tab-count-${tab.key}")
                )
            }
        }

        // The living connection light the fourth slot carries when the tunnel is up.
        tab.statusDot?.let { dot ->
            if (!selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .size(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(dot)
                )
            }
        }
    }
}
