package com.marbleng.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.model.LiquidDockSpec
import com.marbleng.app.model.LiquidLens

/**
 * MARBLE_LIQUID_LENS_V262 — one tab of the glass-lens dock: the glyph, its counter and its
 * connection light.
 *
 * MARBLE_QUIET_TABS_V263 — **the caption is gone.** The old tab composed an `AnimatedContent` that
 * faded and scaled the tab's name in beside the glyph while the slot's spring-animated `weight`
 * grew to make room for it; the dock re-armed a two-second timer on every selection and the largest
 * rung kept the word drawn forever. A reader complained that clicking a tab made its name unfold
 * from the side. There is no half-measure that fixes that without leaving a second layout of the
 * same row, so the whole mechanism was removed: this tab is a steady glyph at every rung and in
 * every state, and the dock gives every slot the same fixed weight.
 *
 * The name is not lost. It is the slot's [contentDescription], so a screen reader still reads
 * "Home", "Servers", "IP" and "Settings" while the eye sees four glyphs under one lens. That is the
 * Android pattern for an icon-only navigation bar: the label belongs to accessibility, not to a
 * transient piece of chrome.
 *
 * ## The three things the lens changes
 *
 * 1. **Magnification.** The glyph springs from [LiquidLens.REST_SCALE] to
 *    [LiquidLens.MAGNIFICATION] and gets slightly heavier, because a lens makes an icon bigger and
 *    a bigger icon at the same stroke weight reads as a *thinner* one. The scale is applied on the
 *    glyph's own `graphicsLayer`, so the slot's layout box never changes: the row does not re-flow
 *    while a finger is moving the glass.
 * 2. **Ink.** The glyph interpolates from the muted `onSurfaceVariant` to the tab's own accent —
 *    one colour animation, read by the glyph in its *draw* phase, so the tint travels with the lens
 *    rather than snapping after it.
 * 3. **The badge.** A notification count is a fact, not a decoration of the selection, so it is
 *    always printed as a number. It used to collapse into a mute dot whenever the caption was
 *    closed — with captions retired that would have hidden the count forever.
 *
 * ## Anchors
 *
 * The counter and the connection light are anchored to the **icon box**, not to the slot, and they
 * are drawn *outside* the glyph's magnification layer:
 *
 *  - a counter that zoomed with the glyph would slide off the icon's corner at exactly the moment
 *    the reader is looking at it;
 *  - the light is anchored to the slot's floor and lifted by [LiquidLens.STATUS_DOT_LIFT_DP] of
 *    animated `translationY` while under the glass, so it can grow to
 *    [LiquidLens.STATUS_DOT_SCALE] without ever being clipped by the row's padding.
 *
 * ## MARBLE_LUMEN_DOCK_V265 — the ripple lives inside the slot's own shape
 *
 * The slot is two boxes, and the split is the whole point. The **press target** is clipped to the
 * slot's own outline ([DockStudio.innerRadiusDp] — the product's one inner corner rule) *before*
 * `selectable` is added, so the Material 3 ripple a press raises follows this slot's curve at
 * every position in the row, not only where the row's capsule clip cuts the two edge slots. That
 * is the rule the Lumen Android reference draws in its own tab bar (`krambovic/Lumen`, release
 * `android-v1.5.0`, `ui/components/FloatingNavigationDock.kt`, `DockTab`:
 * `.clip(RoundedCornerShape(26.dp))` **before** `.selectable(...)` — "the feedback lives inside the
 * bar's shape"). The **content** (glyph, counter, light) is a sibling drawn above the press target
 * and deliberately *not* clipped by it: a counter may sit on the slot's rim, so the capsule clip
 * on the row stays the only outer boundary the decorations obey.
 */
@Composable
internal fun NavigationItemView(
    tab: MarbleDockTab,
    selected: Boolean,
    size: DockSize,
    spatialSpec: FiniteAnimationSpec<Float>,
    colorSpec: FiniteAnimationSpec<Color>,
    onClick: () -> Unit,
    onPlaced: (Rect) -> Unit,
    testTag: String,
    badgeTag: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Read in the draw / layer phases below, never in composition: these animate on every selection
    // frame and a composition read would recompose the tab 60–120 times a second.
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
            .onPlaced { coordinates -> onPlaced(coordinates.boundsInRoot()) },
        contentAlignment = Alignment.Center
    ) {
        // MARBLE_LUMEN_DOCK_V265 — the press target, clipped to the slot's own outline *before*
        // the interaction is added: the Material 3 ripple follows the slot's curve everywhere in
        // the row, because `clip` is an ancestor of `selectable`'s indication. The corner is the
        // one inner rule [DockStudio.innerRadiusDp], so the feedback reads as the same surface.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(DockStudio.innerRadiusDp(size).dp))
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
                // MARBLE_QUIET_TABS_V263 — the word lives here, for a reader who cannot see the glyph.
                .semantics { contentDescription = tab.label }
                .testTag(testTag)
        )

        // The icon and its counter share one anchor box. The glyph magnifies inside it; the
        // counter does not, so it keeps the corner it was given. This content box is *not*
        // clipped by the press target above it — only the ripple is (V265).
        Box(contentAlignment = Alignment.Center) {
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
            if (tab.badgeCount > 0) {
                DockBadge(
                    count = tab.badgeCount,
                    tag = badgeTag,
                    badgeGround = colors.error,
                    badgeInk = colors.onError,
                    ring = colors.surface,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
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
 * The counter: always the number, never a mute dot.
 *
 * MARBLE_QUIET_TABS_V263 — the dot was the collapsed state of a caption that no longer exists. A
 * badge that hides its own number hides the one piece of information the reader opened Settings to
 * see, so the same tag `lumen-tab-count-<key>` now always carries the count.
 */
@Composable
private fun DockBadge(
    count: Int,
    tag: String,
    badgeGround: Color,
    badgeInk: Color,
    ring: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-6).dp)
            .clip(RoundedCornerShape(percent = LiquidLens.PILL_CORNER_PERCENT))
            .background(badgeGround)
            .border(
                width = 1.dp,
                color = ring.copy(alpha = 0.85f),
                shape = RoundedCornerShape(percent = 50)
            )
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
}
