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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.model.GlassDock
import com.marbleng.app.model.LiquidDockSpec
import com.marbleng.app.ui.theme.marbleMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * **2. It had one size.** The ladder is 56 / 68 / 82 dp ([DockStudio.heightDp]), and the padding,
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
 * the weight of the opening tab, the weight of the glyph, the travel of the glass. The app's
 * calm-motion switch and Android's own *remove animations* still win — [marbleMotion] clamps the
 * travel and a still motion settles without overshoot.
 *
 * MARBLE_CENTER_MORPH_STUDIO_V259 — **5. The third style was a fifth tab.** `CENTER_MORPH` used to
 * draw a circle of exactly `restingTabSizeDp` in the middle of the bar: the same object as a tab, at
 * the same height, in the place a tab would be. The brief asks for a bar that is *different* there —
 * a little larger, and made of the newer Expressive material rather than the capsule's flat
 * gradient. So when [centerProud] is set the whole capsule grows by [DockStudio.CENTER_PROUD_DP],
 * the tabs sit in the row as before, and the trigger fills the taller row: a solid tonal disc with a
 * specular rim and a stack of three elevation shadows. It is the only object in the product that is
 * deliberately bigger than the chrome it lives in.
 *
 * MARBLE_GLASS_DOCK_V261 — **6. It was a strip with a pill behind one tab.** The last pass of this
 * file rebuilt the *shipping* bar out of the material the brief asked for, so there is one dock in
 * the product and not a second, prettier one that nothing draws:
 *
 *  - **Glass.** The surface is a translucent tonal slab — [GlassDock.SURFACE_ALPHA] of the palette's
 *    container over the page, a bright-to-dark body wash, the palette's own two hues at a tenth,
 *    a light top rim, a dark floor rim and a tight contact shadow, under one wide ambient shadow
 *    drawn from the capsule's own shape. The old `outlineVariant` hairline is gone: it drew a
 *    *frame*, and the material has to be an *edge*.
 *  - **A lens, not a highlight.** The selected tab no longer wears a tinted container role. One
 *    glass aperture travels under the row: an accent wash at [GlassDock.LENS_ALPHA], a refracting
 *    band along its top edge, a cold-then-warm chromatic fringe at the rim, an inner shadow where
 *    the pane meets its frame, and a specular arc across the upper third. The glyph under it is
 *    magnified 12 % and the word inside it is printed at full strength.
 *  - **Much narrower.** 30dp of air on each side instead of 18, capped at
 *    [DockStudio.MAX_WIDTH_DP] so a tablet draws the same object rather than a longer strip.
 *  - **Much rounder.** [DockStudio.CORNER_RATIO] is 0.5: the capsule is a stadium, and a selected
 *    tab's own shape is [DockStudio.innerRadiusDp] — one corner rule, still.
 *  - **It follows the hand.** A horizontal drag moves the selection to the nearest tab, because a
 *    lens that travels on tap alone is a highlight with better manners.
 *
 * The lens is placed from *measurements* — each slot reports its centre in the row's own space — so
 * a leading island, a trailing power button, a centre trigger and an open caption all keep the glass
 * over the tab it belongs to instead of over where an evenly divided row would have put it.
 */
@Composable
internal fun MarbleLumenDock(
    tabs: List<MarbleDockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: DockSize = DockSize.MEDIUM,
    /**
     * MARBLE_FLOATING_DOCK_V258 — the air under the capsule, in dp, chosen by the user. It is a
     * parameter rather than a constant because it is a *setting*: the dock floats, and how far
     * above the gesture bar is the reader's answer, not this file's.
     */
    bottomGap: Dp = DockStudio.DEFAULT_BOTTOM_GAP_DP.dp,
    /**
     * MARBLE_CENTER_MORPH_STUDIO_V259 — true while a centre trigger is going to be drawn.
     *
     * The capsule grows by [DockStudio.CENTER_PROUD_DP] and the rung's own material gives way to the
     * Expressive one. It is a parameter rather than `center != null` only because the dock is also
     * drawn in tests and previews without a wing, and those must stay at the shipped height.
     */
    centerProud: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    center: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    if (tabs.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val height = if (centerProud && center != null) {
        DockStudio.centerDockHeightDp(size)
    } else {
        DockStudio.heightDp(size)
    }
    val pillPadding = DockStudio.pillPaddingDp(size)
    val corner = DockStudio.cornerRadiusDp(size)
    // The capsule's shape, built once and read by both the lift below and the material: the corner
    // rule is the model's (`DockStudio.cornerRadiusDp`), and nothing here rounds a second time.
    val shape = RoundedCornerShape(corner)
    val selection = selectedIndex.coerceIn(0, tabs.lastIndex)
    val tabCount = tabs.size

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

    // ── the glass ────────────────────────────────────────────────────────────────────────────
    //
    // MARBLE_GLASS_DOCK_V261 — the aperture is placed from measurements and nothing else. A slot's
    // width changes while its caption is open, the wings are part of the same row, and the centre
    // style draws a taller capsule: an evenly divided row would put the glass off its tab in all
    // three cases. Each slot reports its own centre and width in the *row's* coordinate space.
    val density = LocalDensity.current
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var slotBounds by remember { mutableStateOf(List(tabCount) { Rect.Zero }) }

    val selectedBounds = slotBounds.getOrNull(selection) ?: Rect.Zero
    val rowWidthPx = rowBounds.width
    val selectedCentrePx = selectedBounds.center.x - rowBounds.left
    val selectedWidthPx = selectedBounds.width
    val lensInsetPx = with(density) { pillPadding.dp.toPx() }
    val lensAperturePx = (selectedWidthPx - with(density) {
        GlassDock.APERTURE_INSET_DP.dp.toPx() * 2f
    }).coerceAtLeast(0f)
    val lensLeftTarget = GlassDock.apertureLeftPx(
        barWidthPx = rowWidthPx,
        centrePx = selectedCentrePx,
        apertureWidthPx = lensAperturePx,
        trackInsetPx = lensInsetPx
    )
    val lensLeft by animateFloatAsState(
        targetValue = lensLeftTarget,
        animationSpec = floatSpec,
        label = "lumenGlassTravel"
    )
    val lensWidth by animateFloatAsState(
        targetValue = lensAperturePx,
        animationSpec = floatSpec,
        label = "lumenGlassWidth"
    )
    val haptics = LocalHapticFeedback.current

    // A drag selects the tab under the finger instead of scrolling the page behind the dock: the
    // glass follows the hand, which is the one affordance a travelling lens has over a tinted pill.
    val selectNearest: (Float) -> Unit = { xPx ->
        if (rowWidthPx > 0f) {
            var best = -1
            var bestDistance = Float.MAX_VALUE
            slotBounds.forEachIndexed { index, bounds ->
                val distance = abs(bounds.center.x - rowBounds.left - xPx)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
            if (best in 0 until tabCount && best != selection) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(best)
            }
        }
    }

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
            .testTag("lumen-dock"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = DockStudio.MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .height(height.dp)
        ) {
            // The *surface* is its own layer, so the wide ambient shadow is drawn from the
            // capsule's shape rather than from whatever the content happens to measure. The
            // *content* row is not clipped, because a wing's micro tooltip has to be able to leave
            // the pill and float above it — clipping the row cut that card in half at the rim.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shadowElevation = GlassDock.AMBIENT_SHADOW_DP.dp.toPx()
                        this.shape = shape
                        clip = false
                    }
                    .glassCapsule(
                        corner = corner.dp,
                        container = colors.surfaceContainerHigh,
                        highlight = colors.primary,
                        second = colors.tertiary,
                        dark = colors.surface.luminance() < 0.5f,
                        reduced = motion.still
                    )
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // MARBLE_GLASS_DOCK_V261 — the lens is drawn *before* the row, so the glass is over
                // the capsule's own surface and *under* everything a reader has to read. A pane
                // painted last would wash the selected glyph with its own accent — a lens magnifies
                // what it covers, it does not tint it.
                //
                // Its left edge is in the row's coordinates, hence the one `pillPadding` of
                // conversion into the capsule's: the row is inset by exactly that much.
                if (lensWidth > 0.5f) {
                    GlassLens(
                        left = lensLeft,
                        width = lensWidth,
                        height = (height - pillPadding * 2).toFloat(),
                        corner = GlassDock.lensRadiusDp(size),
                        accent = tabs.getOrNull(selection)?.accent ?: colors.primary,
                        reduced = motion.still,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = pillPadding.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = pillPadding.dp)
                        .then(
                            if (centerProud && center != null) {
                                Modifier.padding(horizontal = 2.dp)
                            } else {
                                Modifier
                            }
                        )
                        .selectableGroup()
                        // The row's own width, in the space the slots report their centres in.
                        .onPlaced { coordinates -> rowBounds = coordinates.boundsInRoot() }
                        .pointerInput(tabCount, rowWidthPx) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                selectNearest(change.position.x)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (leading != null) leading()

                    // A centred trigger splits the tabs around itself: half of them lead, half
                    // follow, so the button is the middle of the dock and not a fifth slot at one
                    // end.
                    val centerAt = if (center != null) (tabCount + 1) / 2 else -1
                    tabs.forEachIndexed { index, tab ->
                        if (index == centerAt) {
                            center?.let { trigger ->
                                Box(
                                    modifier = Modifier.padding(horizontal = pillPadding.dp),
                                    contentAlignment = Alignment.Center
                                ) { trigger() }
                            }
                        }
                        // MARBLE_FLOATING_DOCK_V258 — the rung decides the caption. V256's two
                        // switches let an icon-only dock sit next to a labelled one at the same
                        // size; the ladder answers that question now, so the biggest capsule keeps
                        // its word and the other two stay icon-led.
                        val opening = index == selection &&
                            (labelOpen || DockStudio.showsCaptionsAlways(size))
                        // The opening slot takes more of the row than its neighbours, and that
                        // ratio is a spring-animated Float: a `weight` that moves *is* a capsule
                        // that grows, with no second animation object to keep in step with it.
                        val slotWeight by animateFloatAsState(
                            targetValue = if (opening) 1.9f else 1f,
                            animationSpec = floatSpec,
                            label = "lumenTabSlot"
                        )
                        key(tab.key) {
                            LumenDockTab(
                                tab = tab,
                                selected = index == selection,
                                expanded = opening,
                                size = size,
                                floatSpec = floatSpec,
                                colorSpec = colorSpec,
                                // MARBLE_FLOATING_DOCK_V258 — the dock always draws its glyphs:
                                // the switch that could hide them left Settings, and a row of
                                // unlabelled dots is not a navigation bar.
                                showIcons = true,
                                onClick = { onSelect(index) },
                                onPlaced = { bounds ->
                                    // Guarded against its own re-run: a slot that reported the same
                                    // rectangle twice would recompose the row forever.
                                    if (slotBounds.getOrNull(index) != bounds) {
                                        slotBounds = slotBounds.toMutableList().also { it[index] = bounds }
                                    }
                                },
                                modifier = Modifier.weight(slotWeight)
                            )
                        }
                    }
                    if (centerAt == tabCount) {
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
    }
}

/**
 * One slot. The glyph is always there; the word arrives inside the slot only while the capsule is
 * open, scaled and faded in (`scaleIn() + fadeIn()`) and leaving the same way, so the collapse reads
 * as the same object returning to rest rather than as a label being deleted.
 *
 * MARBLE_GLASS_DOCK_V261 — the slot paints **no background at all**. Its selection is the lens that
 * travels over the row, which is the whole redesign: a selection the reader looks *through* instead
 * of a pill they look *at*. The only thing the slot still changes on selection is its ink — the
 * accent, a slightly heavier glyph, 12 % larger — because the glass is clear and the glyph has to
 * carry the contrast the tinted pill used to.
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
    onPlaced: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

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
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = floatSpec,
        label = "lumenTabScale"
    )
    val glyphSize = DockStudio.iconSizeDp(size).dp
    // MARBLE_DOCK_RADII_V260 — a tab curves with the bar it sits in, not with its own height.
    val tabShape = RoundedCornerShape(DockStudio.innerRadiusDp(size))

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = DockStudio.pillPaddingDp(size).dp)
            .clip(tabShape)
            // The slot's own geometry, reported upwards so the lens can be placed over it. The
            // rectangle is taken in *root* space and paired with the row's own root rectangle, so
            // the subtraction cancels the capsule's insets, the safe-area insets and the page's
            // own offset in one step — no assumption about which parent is which.
            .onPlaced { coordinates -> onPlaced(coordinates.boundsInRoot()) }
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
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = glyphScale
                scaleY = glyphScale
            }
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

/**
 * MARBLE_GLASS_DOCK_V261 — the aperture itself: one clear pane with a refracting band, a chromatic
 * fringe, an inner shadow and a specular arc.
 *
 * Everything is drawn in one `Canvas` rather than composed, because a lens is a stack of gradients
 * that must all share one outline — five composables clipped to the same shape would round their
 * corners five times and show the seams.
 */
@Composable
private fun GlassLens(
    left: Float,
    width: Float,
    height: Float,
    corner: Float,
    accent: Color,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    // The one colour the lens needs that is not the tab's own accent. Read here, in the
    // composable, and never inside the draw scope: `compose-scope-check` refuses a theme read in a
    // Canvas, which is a fair rule — a draw scope has no recomposition to be invalidated by.
    val light = MaterialTheme.colorScheme.surfaceBright
    // The pane is laid out by its *own* two numbers rather than by padding: the aperture is placed
    // from the row's measurements, which are already in the row's space, so a start padding here
    // would count the capsule's inner air twice.
    Canvas(
        modifier = modifier
            .offset { IntOffset(left.roundToInt(), 0) }
            .width(width.dp)
            .height(height.dp)
            .testTag("lumen-glass-lens")
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val radius = corner.dp.toPx().coerceAtMost(h / 2f)
        val outline = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = w,
                    bottom = h,
                    radiusX = radius,
                    radiusY = radius
                )
            )
        }

        clipPath(outline) {
            // The pane: an accent wash that says *which* tab — nearly clear, because the whole
            // point of a lens is that what is under it is still visible.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = GlassDock.LENS_ALPHA),
                        accent.copy(alpha = GlassDock.LENS_ALPHA * 0.45f)
                    )
                ),
                size = Size(w, h)
            )
            // The refracting band along the top edge: light enters the glass here, bends, and lands
            // brighter a few dp lower. Two thin gradients rather than one, because a lens has an
            // entry *and* a floor.
            val band = GlassDock.refractionBandDp(w).dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        light.copy(alpha = 0.30f),
                        light.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = band
                ),
                size = Size(w, band)
            )
            // The chromatic fringe: cold at the top rim, warm at the bottom one. It is the optical
            // tell of dispersion, and at 2–3dp it is a hint rather than a rainbow.
            val fringe = GlassDock.fringeDp(w).dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        GlassDock.FRINGE_COLD.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = fringe
                ),
                size = Size(w, fringe)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        GlassDock.FRINGE_WARM.copy(alpha = 0.16f)
                    ),
                    startY = h - fringe,
                    endY = h
                ),
                topLeft = Offset(0f, h - fringe),
                size = Size(w, fringe)
            )
            // The inner shadow where the pane meets its frame: the single cue that says the glass
            // sits *inside* a hole rather than on top of a surface.
            drawRoundRect(
                color = Color.Black.copy(alpha = if (reduced) 0.10f else 0.14f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // The specular arc across the upper third: one white stroke that fades at both ends, so the
        // rim catches a light from above instead of being outlined everywhere.
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (reduced) 0.34f else 0.52f),
                    Color.Transparent
                )
            ),
            start = Offset(radius, 1.4.dp.toPx()),
            end = Offset(w - radius, 1.4.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        // …and the rim all the way round, dimmer than the arc: a glass edge is brightest where the
        // light hits it and still visible everywhere else.
        drawPath(
            path = outline,
            color = Color.White.copy(alpha = 0.18f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * MARBLE_GLASS_DOCK_V261 — the capsule's material, drawn under its content.
 *
 * `drawWithContent` rather than `drawBehind`, and every colour arrives as a parameter: a
 * `MaterialTheme` read inside a draw scope is exactly what `compose-scope-check` forbids, and a
 * material that can only be built from inside a composition is a material the tests cannot pin.
 */
private fun Modifier.glassCapsule(
    corner: Dp,
    container: Color,
    highlight: Color,
    second: Color,
    dark: Boolean,
    reduced: Boolean
): Modifier = this.drawWithContent {
    val w = size.width
    val h = size.height
    val radius = corner.toPx().coerceAtMost(h / 2f)
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = w,
                bottom = h,
                radiusX = radius,
                radiusY = radius
            )
        )
    }

    // 1. The ground. A translucent tonal slab: on a light page the container's own tone, and on a
    //    dark page the same tone lifted, because a black slab at 72 % over black is simply black —
    //    the one case where "glass" has to be said with light rather than with tone.
    drawPath(path = outline, color = container.copy(alpha = if (dark) 0.88f else GlassDock.SURFACE_ALPHA))
    if (dark) {
        clipPath(outline) {
            drawRect(color = Color.White.copy(alpha = 0.04f), size = Size(w, h))
        }
    }

    // 2. The wash: the palette's own two hues, top-left to bottom-right, at a tenth. This is what
    //    makes the bar belong to the theme instead of to a neutral grey, and it is why the same
    //    composable is right in all four palettes.
    if (!reduced) {
        clipPath(outline) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(
                        highlight.copy(alpha = 0.13f),
                        Color.Transparent,
                        second.copy(alpha = 0.09f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                size = Size(w, h)
            )
            // The vertical body wash: bright at the top, darker at the bottom. A glass slab lit
            // from above is the oldest and most legible material cue there is.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.06f else 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = if (dark) 0.12f else 0.07f)
                    )
                ),
                size = Size(w, h)
            )
        }
    }

    // 3. The contact shadow: a tight dark curve just inside the bottom rim. The ambient shadow is
    //    already under the capsule (the graphics layer above); this is the one that makes the glass
    //    look *thick*.
    clipPath(outline) {
        val contact = GlassDock.CONTACT_SHADOW_DP.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.10f else 0.06f)),
                startY = h - contact,
                endY = h
            ),
            topLeft = Offset(0f, h - contact),
            size = Size(w, contact)
        )
    }

    // 4. The rims. A bright top curve and a dark floor curve, both strokes of the same outline.
    drawPath(
        path = outline,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = GlassDock.RIM_ALPHA),
                Color.Transparent,
                Color.Black.copy(alpha = GlassDock.FLOOR_ALPHA)
            )
        ),
        style = Stroke(width = 1.2.dp.toPx())
    )
    drawContent()
}
