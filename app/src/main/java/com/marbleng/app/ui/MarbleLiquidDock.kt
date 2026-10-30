package com.marbleng.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.LiquidDockSpec
import com.marbleng.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * MARBLE_LIQUID_DOCK_V245 — one tab of the glass pill.
 *
 * [badgeCount] is the red counter (0 draws nothing). [statusDot] is the living connection light the
 * fourth slot carries when the tunnel is up. [accent] is the colour the tab's glyph becomes while
 * the lens covers it — electric blue for the three product tabs, the user's chosen slot accent for
 * the fourth.
 */
@Immutable
internal data class MarbleDockTab(
    val key: String,
    val label: String,
    val glyph: MarbleGlyph,
    val accent: Color,
    val badgeCount: Int = 0,
    val statusDot: Color? = null
)

/**
 * MARBLE_LIQUID_DOCK_V245 — the floating, frosted, liquid-glass navigation pill.
 *
 * The object on screen is three things stacked in one capsule:
 *
 * 1. **The glass body** — a semi-transparent white wash (0.83–0.88, the requested band), an
 *    optional blurred sample of the page behind it, a frosted rim and one wide ambient shadow. The
 *    sample is a recorded [GraphicsLayer] the shell hands in; the blur is `Modifier.blur`, i.e. the
 *    platform `RenderEffect` on Android 12+ and a documented no-op below it — which is exactly the
 *    contract of a backdrop blur, and the reason this file never touches `RenderEffect` directly.
 * 2. **The row** — four tabs. The glyph under the lens is magnified
 *    ([LiquidDockSpec.magnification]) and tinted toward its accent by the lens' own Gaussian
 *    falloff, so size and colour travel with the glass instead of switching at a boundary.
 * 3. **The lens** — a drawn ellipse of glass with a rim light, an inner meniscus, a caustic pool
 *    and a hairline chromatic fringe. It is a `Canvas`, not a bitmap, so it is crisp at every
 *    density and on every display size.
 *
 * ## The gesture
 *
 * One `awaitEachGesture` loop owns the whole interaction and is the only writer of the lens
 * position:
 *
 * - **down** — the lens is under the finger in the next frame (the held fraction is read directly
 *   by the draw phase, not animated), plus one tick.
 * - **drag** — every pointer move writes the fraction; crossing a tab edge is one more tick.
 *   Nothing is animated, so the lens is never chasing the finger.
 * - **up** — the fraction is handed to an [Animatable], the selection is published, and the lens
 *   springs to the nearest tab centre with [LiquidDockSpec.SNAP_DAMPING_RATIO] /
 *   [LiquidDockSpec.SNAP_STIFFNESS]. A plain tap is the same path with zero movement.
 *
 * Press state, drag fraction and selection are three separate pieces of state. The drag fraction is
 * only ever read inside `graphicsLayer` and `Canvas` blocks, so a scrub invalidates drawing, not
 * the composition tree; the row only recomposes when the *selection* changes. That is what keeps a
 * scrub at frame rate on a 120 Hz panel.
 */
@Composable
internal fun MarbleLiquidDock(
    tabs: List<MarbleDockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    size: DockSize = DockSize.MEDIUM,
    showIcons: Boolean = true,
    showLabels: Boolean = true,
    floating: Boolean = true,
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero
) {
    if (tabs.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val count = tabs.size
    val activeIndex = selectedIndex.coerceIn(0, count - 1)

    // The spring is launched here: `awaitEachGesture` is a restricted suspension scope, so it
    // cannot await an Animatable itself.
    val scope = rememberCoroutineScope()
    val dark = colors.surface.luminance() < 0.35f
    val barHeight = LiquidDockSpec.barHeightDp(size).dp
    val lensWidth = LiquidDockSpec.lensWidthDp(size).dp
    val lensHeight = LiquidDockSpec.lensHeightDp(size).dp
    val innerPadding = LiquidDockSpec.INNER_PADDING_DP.dp
    val shape = if (floating) RoundedCornerShape(percent = 50) else RoundedCornerShape(0.dp)

    val glassTop = Color.White.copy(
        alpha = if (dark) LiquidDockSpec.GLASS_ALPHA_DARK else LiquidDockSpec.GLASS_ALPHA
    )
    val glassBottom = Color.White.copy(
        alpha = if (dark) {
            LiquidDockSpec.GLASS_ALPHA_DARK * 0.55f
        } else {
            LiquidDockSpec.GLASS_ALPHA - 0.05f
        }
    )
    val rimTop = Color.White.copy(alpha = if (dark) 0.42f else 0.95f)
    val rimBottom = Color.White.copy(alpha = if (dark) 0.10f else 0.34f)
    // The badge and the status dot wear the pill's own colour as their ring, so they sit *on* the
    // glass instead of floating in front of it.
    val ring = if (dark) colors.surface else Color.White

    // ---- measured geometry ---------------------------------------------------------------
    var barWidthPx by remember { mutableStateOf(0f) }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    val innerPaddingPx = with(density) { innerPadding.toPx() }
    val contentWidthPx = (barWidthPx - innerPaddingPx * 2f).coerceAtLeast(1f)

    // ---- state: settle animation, live drag, press ---------------------------------------
    val settled = remember(count) {
        Animatable(LiquidDockSpec.slotCenterFraction(activeIndex, count))
    }
    var heldFraction by remember { mutableStateOf<Float?>(null) }
    var pressed by remember { mutableStateOf(false) }

    // The one reader the draw phase gets. Never called during composition, so a drag recomposes
    // nothing: the fraction reaches the glyph scale and the lens through the layer/draw phases.
    val lensFraction: () -> Float = { heldFraction ?: settled.value }

    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = LiquidDockSpec.PRESS_DAMPING_RATIO,
            stiffness = LiquidDockSpec.PRESS_STIFFNESS
        ),
        label = "liquidDockPress"
    )
    val lensScale = LiquidDockSpec.pressedScale(pressProgress)

    // Remembered so the gesture handler's key is stable: a new spec instance on every
    // recomposition would restart `pointerInput` in the middle of a drag.
    val snapSpec: FiniteAnimationSpec<Float> = remember(reducedMotion) {
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = LiquidDockSpec.SNAP_DAMPING_RATIO,
                stiffness = LiquidDockSpec.SNAP_STIFFNESS
            )
        }
    }

    // A selection that arrives from outside the dock (a notification route, the back gesture)
    // moves the lens with the same spring a finger would have produced.
    LaunchedEffect(activeIndex, count) {
        if (heldFraction == null) {
            settled.animateTo(LiquidDockSpec.slotCenterFraction(activeIndex, count), snapSpec)
        }
    }

    Box(
        modifier = modifier
            .then(
                if (floating) {
                    Modifier.padding(
                        start = LiquidDockSpec.HORIZONTAL_MARGIN_DP.dp,
                        end = LiquidDockSpec.HORIZONTAL_MARGIN_DP.dp,
                        bottom = LiquidDockSpec.BOTTOM_MARGIN_DP.dp
                    )
                } else {
                    Modifier
                }
            )
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .shadow(
                    elevation = LiquidDockSpec.OUTER_SHADOW_DP.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.primary.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.42f else 0.16f)
                )
                .clip(shape)
                .border(
                    width = LiquidDockSpec.RIM_WIDTH_DP.dp,
                    brush = Brush.verticalGradient(listOf(rimTop, rimBottom)),
                    shape = shape
                )
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .onGloballyPositioned { barOrigin = it.positionInWindow() }
                .pointerInput(count, barWidthPx, haptics, snapSpec) {
                    if (barWidthPx <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downFraction = LiquidDockSpec.fractionForX(
                            down.position.x - innerPaddingPx,
                            contentWidthPx
                        )
                        pressed = true
                        heldFraction = downFraction
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        var previous = downFraction
                        drag(down.id) { change ->
                            val next = LiquidDockSpec.fractionForX(
                                change.position.x - innerPaddingPx,
                                contentWidthPx
                            )
                            // One tick per crossed tab edge, and only when the edge is really
                            // crossed: a finger jittering inside one slot must stay silent.
                            if (LiquidDockSpec.boundaryCrossed(previous, next, count)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            previous = next
                            heldFraction = next
                            change.consume()
                        }
                        val released = heldFraction ?: downFraction
                        val target = LiquidDockSpec.nearestIndex(released, count)
                        pressed = false
                        onSelect(target)
                        // The held fraction stays live until the spring's coroutine has adopted
                        // it, so the lens never jumps back to the old tab for a frame.
                        scope.launch {
                            settled.snapTo(released)
                            heldFraction = null
                            settled.animateTo(
                                LiquidDockSpec.slotCenterFraction(target, count),
                                snapSpec
                            )
                        }
                    }
                }
        ) {
            // 1. The page behind the pill, blurred. `Modifier.blur` is the platform RenderEffect
            //    on Android 12+ and a documented no-op below it, so an older device keeps the
            //    wash alone and loses nothing else.
            if (backdrop != null) {
                val shift = backdropOrigin - barOrigin
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(LiquidDockSpec.BACKDROP_BLUR_DP.dp)
                ) {
                    translate(left = shift.x, top = shift.y) {
                        drawLayer(backdrop)
                    }
                }
            }

            // 2. The frosted wash of the glass itself, brighter at the top edge.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
            )

            // 3. The row.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = innerPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, tab ->
                    LiquidDockItem(
                        tab = tab,
                        index = index,
                        count = count,
                        selected = index == activeIndex,
                        lensFraction = lensFraction,
                        showIcons = showIcons,
                        showLabels = showLabels,
                        iconSize = LiquidDockSpec.iconSizeDp(size).dp,
                        ring = ring,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 4. The lens, on top of everything, so its rim and caustic read as glass.
            Canvas(modifier = Modifier.matchParentSize()) {
                val fraction = lensFraction()
                val centreX = innerPaddingPx + contentWidthPx * fraction
                val halfWidth = lensWidth.toPx() * lensScale / 2f
                val halfHeight = lensHeight.toPx() * lensScale / 2f
                val left = centreX - halfWidth
                // `size` is the DockSize parameter out here: the draw scope's own size is
                // the only one that knows how tall the pill is.
                val top = this.size.height / 2f - halfHeight
                val oval = Size(halfWidth * 2f, halfHeight * 2f)
                val corner = Offset(left, top)

                // A soft contact shadow under the drop.
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = if (dark) 0.26f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(centreX, top + oval.height * 0.62f),
                        radius = halfWidth * 1.25f
                    ),
                    topLeft = Offset(left - halfWidth * 0.12f, top + 2.dp.toPx()),
                    size = Size(oval.width * 1.24f, oval.height * 1.02f)
                )

                // The body of the glass: a radial wash that peaks above the centre.
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.16f else 0.34f),
                            Color.White.copy(alpha = if (dark) 0.04f else 0.10f)
                        ),
                        center = Offset(centreX, top + oval.height * 0.30f),
                        radius = halfWidth * 1.30f
                    ),
                    topLeft = corner,
                    size = oval
                )

                // The rim light: bright where the light comes from, gone at the bottom.
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.55f else 0.92f),
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = if (dark) 0.06f else 0.16f)
                        ),
                        start = corner,
                        end = Offset(corner.x + oval.width, corner.y + oval.height)
                    ),
                    topLeft = corner,
                    size = oval,
                    style = Stroke(width = 1.1.dp.toPx())
                )

                // The meniscus: a second, inner hairline that gives the glass a thickness.
                val inset = 1.6.dp.toPx()
                drawOval(
                    color = Color.White.copy(alpha = if (dark) 0.10f else 0.20f),
                    topLeft = Offset(corner.x + inset, corner.y + inset),
                    size = Size(
                        (oval.width - inset * 2f).coerceAtLeast(1f),
                        (oval.height - inset * 2f).coerceAtLeast(1f)
                    ),
                    style = Stroke(width = 0.7.dp.toPx())
                )

                // The caustic pool: light bent through the drop lands on its lower half.
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.14f else 0.46f),
                            Color.Transparent
                        ),
                        center = Offset(centreX, top + oval.height * 0.80f),
                        radius = halfWidth * 0.62f
                    ),
                    topLeft = corner,
                    size = oval
                )

                // The chromatic fringe: a lens this small splits light by a hair, not a rainbow.
                drawOval(
                    color = colors.primary.copy(alpha = if (dark) 0.16f else 0.10f),
                    topLeft = Offset(corner.x + 0.7.dp.toPx(), corner.y),
                    size = oval,
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
        }
    }
}

/**
 * One tab under the lens.
 *
 * The glyph is drawn by this composable's own `Canvas`, not by [MarbleGlyphIcon], so its tint and
 * its stroke weight can be read from the lens fraction *in the draw phase*. The magnification is a
 * `graphicsLayer` read for the same reason. The label keeps a two-state colour: it states the
 * selection, and text that changes size under a finger reads as a bug rather than as optics.
 */
@Composable
private fun LiquidDockItem(
    tab: MarbleDockTab,
    index: Int,
    count: Int,
    selected: Boolean,
    lensFraction: () -> Float,
    showIcons: Boolean,
    showLabels: Boolean,
    iconSize: Dp,
    ring: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val labelTint = if (selected) tab.accent else colors.onSurfaceVariant
    val idleTint = colors.onSurfaceVariant

    Column(
        modifier = modifier
            .testTag("dock-tab-${tab.key}")
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showIcons) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            val distance = LiquidDockSpec.distanceInSlots(lensFraction(), index, count)
                            val scale = LiquidDockSpec.magnification(distance)
                            scaleX = scale
                            scaleY = scale
                            translationY = -(scale - 1f) * 10.dp.toPx()
                        }
                ) {
                    val distance = LiquidDockSpec.distanceInSlots(lensFraction(), index, count)
                    val highlight = LiquidDockSpec.highlight(distance)
                    drawMarbleGlyph(
                        glyph = tab.glyph,
                        tint = lerp(idleTint, tab.accent, highlight),
                        weight = 1f + 0.24f * highlight
                    )
                }
                if (tab.statusDot != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(tab.statusDot)
                            .border(1.2.dp, ring, CircleShape)
                    )
                }
                if (tab.badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .testTag("dock-badge-${tab.key}")
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-6).dp)
                            .defaultMinSize(
                                minWidth = LiquidDockSpec.BADGE_MIN_SIZE_DP.dp,
                                minHeight = LiquidDockSpec.BADGE_MIN_SIZE_DP.dp
                            )
                            .clip(CircleShape)
                            .background(colors.error)
                            .border(1.4.dp, ring, CircleShape)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = faDigits(LiquidDockSpec.badgeLabel(tab.badgeCount)),
                            color = colors.onError,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (showLabels) {
            Text(
                text = tab.label,
                color = labelTint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (showIcons) 2.dp else 0.dp)
            )
        }
    }
}
