package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.model.LiquidDockSpec
import com.marbleng.app.model.LiquidLens

/**
 * MARBLE_LIQUID_LENS_V262 — one tab of the glass-lens dock: the glyph, its word, its counter and
 * its connection light.
 *
 * The three things this file is responsible for are the three things that change when the lens
 * covers a tab:
 *
 * 1. **Magnification.** The glyph springs from [LiquidLens.REST_SCALE] to
 *    [LiquidLens.MAGNIFICATION] and gets slightly heavier, because a lens makes an icon bigger and
 *    a bigger icon at the same stroke weight reads as a *thinner* one. The scale is applied on the
 *    glyph's own `graphicsLayer`, so the slot's layout box never changes: the row does not re-flow
 *    while a finger is moving the glass — which is what "no jarring layout jerks" means at 120 Hz.
 *
 * 2. **Ink.** The glyph and the word interpolate from the muted `onSurfaceVariant` to the tab's own
 *    accent — one colour animation, read by the glyph in its *draw* phase and by the text in
 *    composition, so the tint travels with the lens rather than snapping after it.
 *
 * 3. **The word.** The selected tab opens into its caption for as long as the dock says so
 *    (`expanded`), growing the slot with a spring-animated `weight` in the container. The label
 *    itself fades and scales in through `AnimatedContent`, and it is *removed* on collapse rather
 *    than made transparent — a transparent node is still a node to a screen reader and still a
 *    match for `onNodeWithText`.
 *
 * ## Anchors
 *
 * The counter and the connection light are anchored to the **icon box**, not to the slot, and they
 * are drawn *outside* the glyph's magnification layer:
 *
 *  - a counter that zoomed with the glyph would slide off the icon's corner at exactly the moment
 *    the reader is looking at it;
 *  - a counter that was a child of the zoomed layer would be scaled away from the anchor its
 *    padding describes;
 *  - the light is anchored to the slot's floor and lifted by [LiquidLens.STATUS_DOT_LIFT_DP] of
 *    animated `translationY` while under the glass, so it can grow to
 *    [LiquidLens.STATUS_DOT_SCALE] without ever being clipped by the row's padding.
 *
 * Nothing in this file clips: the row must be able to draw a tooltip, a counter or a lifted light
 * over its own rim.
 */
@Composable
internal fun NavigationItemView(
    tab: MarbleDockTab,
    selected: Boolean,
    expanded: Boolean,
    size: DockSize,
    spatialSpec: FiniteAnimationSpec<Float>,
    colorSpec: FiniteAnimationSpec<Color>,
    onClick: () -> Unit,
    onPlaced: (Rect) -> Unit,
    testTag: String,
    badgeTag: String,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Read in the draw / layer phases below, never in composition: these two animate on every
    // selection frame and a composition read would recompose the tab 60–120 times a second.
    val glyphScale = animateFloatAsState(
        targetValue = if (selected) LiquidLens.MAGNIFICATION else LiquidLens.REST_SCALE,
        animationSpec = spatialSpec,
        label = "liquidLensMagnify"
    )
    val glyphWeight = animateFloatAsState(
        targetValue = if (selected) LiquidLens.GLYPH_WEIGHT_ACTIVE else 1f,
        animationSpec = spatialSpec,
        label = "liquidLensWeight"
    )
    val statusPresence = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spatialSpec,
        label = "liquidLensStatusDot"
    )
    // The tint is also needed by the caption, which is composed — so this one is read here.
    val ink by animateColorAsState(
        targetValue = if (selected) tab.accent else colors.onSurfaceVariant,
        animationSpec = colorSpec,
        label = "liquidLensInk"
    )

    val iconSize = DockStudio.iconSizeDp(size).dp
    val statusLiftPx = with(density) { LiquidLens.STATUS_DOT_LIFT_DP.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = DockStudio.pillPaddingDp(size).dp)
            // The slot's own geometry, reported upwards so the container can place the lens over
            // it. Root space for both this rectangle and the row's, so the subtraction cancels the
            // capsule's insets, the safe-area insets and the page's offset in one step.
            .onPlaced { coordinates -> onPlaced(coordinates.boundsInRoot()) }
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = {
                    // A selection that moved, not a button that was pressed: a tab that is
                    // already selected says nothing.
                    if (!selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
            .semantics { contentDescription = tab.label }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // The icon and its counter share one anchor box. The glyph magnifies inside it; the
            // counter does not, so it keeps the corner it was given.
            Box(contentAlignment = Alignment.Center) {
                if (showIcons) {
                    Canvas(
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                val scale = glyphScale.value
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin.Center
                            }
                    ) {
                        drawMarbleGlyph(
                            glyph = tab.glyph,
                            tint = ink,
                            weight = glyphWeight.value
                        )
                    }
                }
                if (tab.badgeCount > 0) {
                    DockBadge(
                        count = tab.badgeCount,
                        expanded = expanded,
                        tag = badgeTag,
                        badgeGround = colors.error,
                        badgeInk = colors.onError,
                        ring = colors.surface,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
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
                label = "liquidLensLabel"
            ) { open ->
                if (open) {
                    Text(
                        text = tab.label,
                        color = ink,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            // The word grows with the rung, so a Grand capsule is not a Slim one
                            // with a bigger glyph only.
                            fontSize = DockStudio.labelSp(size).sp,
                            // The brief's fade: a caption that appears at full strength under a
                            // lens that has not finished arriving reads as a pop.
                            letterSpacing = 0.15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = if (showIcons) 8.dp else 2.dp, end = 6.dp)
                            .widthIn(max = 92.dp)
                    )
                } else {
                    // Removed, not hidden: the collapsing caption must leave the semantics tree.
                    Box(modifier = Modifier.size(0.dp))
                }
            }
        }

        // The living connection light. Always drawn while the tunnel is up — the fourth slot's
        // status is a fact, not a decoration of the selection — and enlarged + lifted by an
        // animated `graphicsLayer` so it never asks the row for a second layout pass.
        tab.statusDot?.let { dot ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(LiquidLens.STATUS_DOT_SIZE_DP.dp)
                    .graphicsLayer {
                        val presence = statusPresence.value
                        val scale = 1f + (LiquidLens.STATUS_DOT_SCALE - 1f) * presence
                        scaleX = scale
                        scaleY = scale
                        translationY = -statusLiftPx * presence
                    }
                    .clip(CircleShape)
                    .background(dot)
                    .border(
                        width = 1.dp,
                        color = colors.surface.copy(alpha = 0.85f),
                        shape = CircleShape
                    )
                    .testTag("lumen-tab-status-${tab.key}")
            )
        }
    }
}

/**
 * The counter: a dot at rest, a number while the capsule is open.
 *
 * The dock may never grow a second row of chrome for a badge, so the same tag carries both states —
 * `lumen-tab-count-<key>` — and a reader still sees that a notification exists when the caption is
 * closed.
 */
@Composable
private fun DockBadge(
    count: Int,
    expanded: Boolean,
    tag: String,
    badgeGround: Color,
    badgeInk: Color,
    ring: Color,
    modifier: Modifier = Modifier
) {
    if (expanded) {
        Box(
            modifier = modifier
                .offset(x = 6.dp, y = (-6).dp)
                .clip(RoundedCornerShape(percent = LiquidLens.PILL_CORNER_PERCENT))
                .background(badgeGround)
                .border(width = 1.dp, color = ring.copy(alpha = 0.85f), shape = RoundedCornerShape(percent = 50))
                .padding(horizontal = 5.dp)
                .testTag(tag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = faDigits(LiquidDockSpec.badgeLabel(count)),
                color = badgeInk,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    lineHeight = 9.sp
                ),
                maxLines = 1
            )
        }
    } else {
        Box(
            modifier = modifier
                .offset(x = 5.dp, y = (-4).dp)
                .defaultMinSize(minWidth = 6.dp, minHeight = 6.dp)
                .clip(CircleShape)
                .background(badgeGround)
                .border(width = 1.dp, color = ring.copy(alpha = 0.8f), shape = CircleShape)
                .testTag(tag)
        )
    }
}
