package com.marbleng.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.LensDeformation
import com.marbleng.app.model.LiquidGlassLensSpec
import com.marbleng.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * One tab of [LiquidGlassBottomBar].
 *
 * [icon] and [selectedIcon] are composable slots rather than drawables because the two-pass optic
 * renders the same slot twice: once flat for the passive layer, once bold for the magnified
 * projection the lens reveals. A slot can draw a `MarbleGlyph` on a `Canvas`, an `ImageVector`
 * through [Icon] (see [navigationTabItem]), or anything the host app owns.
 *
 * [accent] is the colour the tab takes inside the lens. [statusDot] is the living connection light;
 * [badgeCount] is the red counter, and zero draws nothing.
 */
@Immutable
data class NavigationTabItem(
    val key: String,
    val title: String,
    val badgeCount: Int = 0,
    val accent: Color? = null,
    val statusDot: Color? = null,
    val icon: (@Composable (tint: Color, weight: Float, modifier: Modifier) -> Unit)? = null,
    val selectedIcon: (@Composable (tint: Color, weight: Float, modifier: Modifier) -> Unit)? = icon
)

/**
 * The `ImageVector` flavour of [NavigationTabItem]: the brief's title/icon/badge quadruple without
 * asking the caller to write a composable slot by hand.
 */
fun navigationTabItem(
    title: String,
    icon: ImageVector,
    selectedIcon: ImageVector = icon,
    key: String = title,
    badgeCount: Int = 0,
    accent: Color? = null
): NavigationTabItem = NavigationTabItem(
    key = key,
    title = title,
    badgeCount = badgeCount,
    accent = accent,
    icon = { tint, _, modifier ->
        Icon(imageVector = icon, contentDescription = null, modifier = modifier, tint = tint)
    },
    selectedIcon = { tint, _, modifier ->
        Icon(imageVector = selectedIcon, contentDescription = null, modifier = modifier, tint = tint)
    }
)

/**
 * MARBLE_CRYSTAL_LENS_V248 — a crystal liquid lens travelling across a frosted stadium.
 *
 * ## Why this file was rebuilt
 *
 * V247's optic had three fatal material properties, all visible in a screenshot: the muted base row
 * stayed visible *underneath* the magnified copy (two glyphs at two scales, read by the eye as a
 * milky smear); the capsule sampled the page into a blurred `GraphicsLayer` (which softens every
 * edge it covers, including the row itself); and a 60dp capsule held a 23dp glyph plus a label with
 * only 4dp of padding (vertical cramping). V248 discards that renderer completely.
 *
 * ## The dual pass
 *
 * | pass | what it draws | how it is bounded |
 * |---|---|---|
 * | base | every tab at scale 1.0, 22dp glyph, 11sp medium label, #5F6368 | clipped **out** of the lens oval, so no base pixel survives under the glass |
 * | projection | the identical row at 1.30×, bold, accent-tinted | clipped **into** the lens oval only |
 *
 * Clipping the base *out* is the part V247 missed: a magnifier does not draw a second image over
 * the first one, it replaces it. Because both passes use the same `Row`, the same weights, the same
 * padding and the same anchors, the only thing that changes at the rim is the scale — a hard,
 * optical boundary rather than a ghosted double image.
 *
 * ## Crispness and frame budget
 *
 * Nothing in this file is blurred, rasterised or scaled as a bitmap. The projection scale is a
 * `graphicsLayer` on each slot's *vector* content, with `TransformOrigin.Center`; the aperture is a
 * `clipPath` in the draw phase; the lens position and its velocity deformation are `Float` state
 * read only inside `graphicsLayer` / `Canvas` / `drawWithContent` lambdas. A drag therefore
 * invalidates drawing, never composition: the tab row is composed once per selection, not once per
 * frame, which is what the 120 Hz requirement asks for.
 *
 * ## The gesture
 *
 * `detectHorizontalDragGestures` plus a `VelocityTracker` owns the scrub. Touch-down seats the lens
 * under the finger immediately; every move writes the clamped pixel; release publishes the nearest
 * tab, fires one `HapticFeedbackType.TextHandleMove` and springs the lens to that anchor at
 * `dampingRatio 0.65 / Spring.StiffnessMediumLow`. A tab tap travels the same spring.
 */
@Composable
fun LiquidGlassBottomBar(
    items: List<NavigationTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = LiquidGlassLensSpec.BAR_HEIGHT_DP.dp,
    lensDiameter: Dp = LiquidGlassLensSpec.LENS_DIAMETER_DP.dp,
    horizontalMargin: Dp = LiquidGlassLensSpec.HORIZONTAL_MARGIN_DP.dp,
    bottomMargin: Dp = LiquidGlassLensSpec.BOTTOM_MARGIN_DP.dp,
    showIcons: Boolean = true,
    showLabels: Boolean = true,
    iconSize: Dp = LiquidGlassLensSpec.ICON_SIZE_DP.dp,
    inactiveContent: Color = Color(LiquidGlassLensSpec.INACTIVE_CONTENT_ARGB),
    activeContent: Color = Color(LiquidGlassLensSpec.ACTIVE_CONTENT_ARGB),
    containerColor: Color = Color.White.copy(alpha = LiquidGlassLensSpec.CAPSULE_ALPHA),
    badgeContainer: Color = Color(0xFFBA1A1A),
    badgeContent: Color = Color.White,
    badgeLabel: @Composable (Int) -> String = { count -> LiquidGlassLensSpec.badgeText(count) },
    tagPrefix: String = "glass-tab-",
    badgeTagPrefix: String = "glass-badge-"
) {
    val count = items.size
    if (count == 0) return
    val activeIndex = selectedIndex.coerceIn(0, count - 1)

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    // A gesture can outlive the composition that started it; the callback the finger releases
    // into must be the newest one the parent supplied.
    val onSelectLatest by rememberUpdatedState(onTabSelected)

    // ---- measured geometry ----------------------------------------------------------------
    // The capsule width arrives from layout; every other number is derived from it. The lens is
    // exactly `lensDiameter` wide, and the clamp is the brief's formula verbatim.
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val paddingPx = with(density) { LiquidGlassLensSpec.INNER_PADDING_DP.dp.toPx() }
    val lensWidthPx = with(density) { lensDiameter.toPx() }
    val itemWidthPx = LiquidGlassLensSpec.itemWidthPx(
        containerWidthPx = containerWidthPx,
        paddingStartPx = paddingPx,
        paddingEndPx = paddingPx,
        count = count
    )

    // ---- animated state, all read in draw/layer phases only --------------------------------
    val lensX = remember { Animatable(0f) }
    var heldLensPx by remember { mutableStateOf<Float?>(null) }
    var velocityX by remember { mutableFloatStateOf(0f) }
    var placed by remember { mutableStateOf(false) }
    val lensReady = placed && containerWidthPx > 1f && itemWidthPx > 0f

    val settleSpec: FiniteAnimationSpec<Float> = remember(reducedMotion) {
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = LiquidGlassLensSpec.SNAP_DAMPING_RATIO,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    }

    // The single pixel reader the draw phase gets. Reading it during composition would recompose
    // the row on every drag frame — the exact cost this architecture exists to avoid.
    val currentLensPx: () -> Float = { heldLensPx ?: lensX.value }

    // The velocity deformation, also a draw-phase read.
    val currentDeformation: () -> LensDeformation = {
        LiquidGlassLensSpec.deformation(velocityX)
    }

    fun anchorOf(index: Int): Float = LiquidGlassLensSpec.tabCenterX(
        index = index,
        itemWidthPx = itemWidthPx,
        containerWidthPx = containerWidthPx,
        paddingStartPx = paddingPx,
        rtl = rtl
    )

    fun tabAt(x: Float): Int = LiquidGlassLensSpec.indexAt(
        lensCenterX = x,
        itemWidthPx = itemWidthPx,
        count = count,
        containerWidthPx = containerWidthPx,
        paddingStartPx = paddingPx,
        rtl = rtl
    )

    // The brief's bounds, in pixels: half a lens in from each inner padding. The model owns the
    // arithmetic (and its test); the composable owns only the coercion.
    val minLensX = LiquidGlassLensSpec.minLensCenterX(paddingPx, lensWidthPx)
    val maxLensX = LiquidGlassLensSpec.maxLensCenterX(containerWidthPx, paddingPx, lensWidthPx)

    fun clamped(x: Float): Float =
        if (maxLensX <= minLensX) containerWidthPx / 2f else x.coerceIn(minLensX, maxLensX)

    // First measurement seats the lens on the active tab with no animation. Every later selection
    // — a tap, a deep link, the back gesture — travels with the same spring a finger would use.
    LaunchedEffect(activeIndex, count, containerWidthPx, itemWidthPx, rtl, placed) {
        if (containerWidthPx <= 1f || itemWidthPx <= 0f) return@LaunchedEffect
        val target = anchorOf(activeIndex)
        if (!placed) {
            lensX.snapTo(target)
            placed = true
            return@LaunchedEffect
        }
        if (heldLensPx != null) return@LaunchedEffect
        val seated = clamped(lensX.value)
        if (seated != lensX.value) lensX.snapTo(seated)
        lensX.animateTo(target, settleSpec)
    }

    // The finger owns the lens from touch-down to release. The pointer pixel is a physical
    // left-to-right coordinate; the RTL mapping lives in `tabAt`/`anchorOf`, not here.
    val dragModifier = Modifier.pointerInput(count, containerWidthPx, itemWidthPx, rtl) {
        if (count == 0 || containerWidthPx <= 1f || itemWidthPx <= 0f) return@pointerInput
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                val start = clamped(offset.x)
                heldLensPx = start
                velocityX = 0f
                tracker.resetTracking()
                tracker.addPosition(SystemClock.uptimeMillis(), Offset(start, 0f))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                velocityX = tracker.calculateVelocity().x
                val current = heldLensPx ?: lensX.value
                heldLensPx = clamped(current + dragAmount)
            },
            onDragEnd = {
                val released = heldLensPx ?: lensX.value
                val target = tabAt(released)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectLatest(target)
                // The held pixel stays live until the spring's coroutine has adopted it, so the
                // lens never flashes back to the old anchor for a frame.
                scope.launch {
                    lensX.snapTo(clamped(released))
                    heldLensPx = null
                    lensX.animateTo(anchorOf(target), settleSpec)
                }
                val thrown = velocityX
                scope.launch {
                    animate(
                        initialValue = thrown,
                        targetValue = 0f,
                        initialVelocity = thrown,
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    ) { value, _ -> velocityX = value }
                }
            },
            onDragCancel = {
                val released = heldLensPx ?: lensX.value
                val target = tabAt(released)
                scope.launch {
                    lensX.snapTo(clamped(released))
                    heldLensPx = null
                    lensX.animateTo(anchorOf(target), settleSpec)
                }
                velocityX = 0f
            }
        )
    }

    // ---- paint constants, resolved once ----------------------------------------------------
    val lensRadiusPx = lensWidthPx / 2f
    val dropletOffsetPx = with(density) {
        LiquidGlassLensSpec.DROPLET_SHADOW_OFFSET_Y_DP.dp.toPx()
    }
    // Canvas has no blur filter (and a filter over the row is exactly what this revision
    // removes), so the 8dp droplet blur is expressed as the radial falloff radius of the contact
    // shadow: lens radius plus half the blur.
    val dropletBlurPx = with(density) { LiquidGlassLensSpec.DROPLET_SHADOW_DP.dp.toPx() }
    val dropletShadowRadiusPx = lensRadiusPx + dropletBlurPx / 2f
    val lensRimPx = with(density) { LiquidGlassLensSpec.LENS_RIM_WIDTH_DP.dp.toPx() }
    val meniscusInsetPx = with(density) { 2.5.dp.toPx() }
    val meniscusWidthPx = with(density) { 0.8.dp.toPx() }
    val arcInsetPx = with(density) { 2.dp.toPx() }
    val arcWidthPx = with(density) { 1.1.dp.toPx() }
    val hotspotRadiusPx = lensRadiusPx * 0.2f

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(
                start = horizontalMargin,
                end = horizontalMargin,
                bottom = bottomMargin
            )
    ) {
        // 1. The diffuse ambient elevation, offset downward. A separate layer from the capsule,
        //    because Compose shadows are centred on their shape and the brief asks for 6dp of
        //    vertical bias. No content: a graphics-layer shadow is cast from the outline.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .offset(y = LiquidGlassLensSpec.CAPSULE_SHADOW_OFFSET_Y_DP.dp)
                .shadow(
                    elevation = LiquidGlassLensSpec.CAPSULE_SHADOW_DP.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(
                        alpha = LiquidGlassLensSpec.CAPSULE_SHADOW_ALPHA
                    ),
                    spotColor = Color.Black.copy(
                        alpha = LiquidGlassLensSpec.CAPSULE_SHADOW_ALPHA
                    )
                )
        )

        // 2. The acrylic capsule: a full stadium, frosted white, with one hairline of light.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(color = containerColor)
                .border(
                    width = LiquidGlassLensSpec.CAPSULE_RIM_WIDTH_DP.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = LiquidGlassLensSpec.CAPSULE_RIM_TOP_ALPHA),
                            Color.White.copy(alpha = LiquidGlassLensSpec.CAPSULE_RIM_BOTTOM_ALPHA)
                        )
                    ),
                    shape = CircleShape
                )
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .then(dragModifier)
        ) {
            // 3. Pass 1 — the passive row. Everything except the strip under the lens is drawn
            //    here, in one crisp composition at scale 1.0.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        if (!lensReady) {
                            drawContent()
                            return@drawWithContent
                        }
                        val cx = currentLensPx()
                        val deformation = currentDeformation()
                        val rx = lensRadiusPx * deformation.scaleX
                        val ry = lensRadiusPx * deformation.scaleY
                        val cy = size.height / 2f
                        // Even-odd: the capsule minus the lens oval. The base row is *absent*
                        // under the glass, so the magnified pass is not drawn over a ghost.
                        val outsideLens = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
                        }
                        clipPath(outsideLens) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .padding(horizontal = LiquidGlassLensSpec.INNER_PADDING_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    CrystalTabSlot(
                        item = item,
                        active = false,
                        tint = inactiveContent,
                        glyphWeight = 1f,
                        showIcons = showIcons,
                        showLabels = showLabels,
                        iconSize = iconSize,
                        badgeLabel = badgeLabel,
                        badgeContainer = badgeContainer,
                        badgeContent = badgeContent,
                        statusRing = Color.White,
                        scale = 1f,
                        decorations = true,
                        interactive = true,
                        selected = index == activeIndex,
                        onSelect = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(index)
                        },
                        tag = tagPrefix + item.key,
                        badgeTag = badgeTagPrefix + item.key,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 4. Pass 2 — the projection. Identical row, identical anchors, 1.30× and bold,
            //    visible only inside the aperture. Semantics are cleared: this is an optical
            //    duplicate, not a second set of controls.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        if (!lensReady) return@drawWithContent
                        val cx = currentLensPx()
                        val deformation = currentDeformation()
                        val rx = lensRadiusPx * deformation.scaleX
                        val ry = lensRadiusPx * deformation.scaleY
                        val cy = size.height / 2f
                        val aperture = Path().apply {
                            addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
                        }
                        clipPath(aperture) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .padding(horizontal = LiquidGlassLensSpec.INNER_PADDING_DP.dp)
                    .clearAndSetSemantics { },
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    CrystalTabSlot(
                        item = item,
                        active = true,
                        tint = item.accent ?: activeContent,
                        glyphWeight = 1.25f,
                        showIcons = showIcons,
                        showLabels = showLabels,
                        iconSize = iconSize,
                        badgeLabel = badgeLabel,
                        badgeContainer = badgeContainer,
                        badgeContent = badgeContent,
                        statusRing = Color.White,
                        scale = LiquidGlassLensSpec.MAGNIFICATION,
                        // The base badge is clipped out of the aperture, so the projection may
                        // draw its own decorations without ever doubling the counter for the eye
                        // (or for a screen reader: this whole row clears its semantics).
                        decorations = true,
                        interactive = false,
                        selected = index == activeIndex,
                        onSelect = {},
                        tag = tagPrefix + item.key + "-projection",
                        badgeTag = badgeTagPrefix + item.key + "-projection",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 5. The lens body, drawn over the junction of both passes. Its scale is the
            //    velocity deformation, pivoted on its own centre, so the disc stretches along the
            //    row and squashes against it without moving off the finger.
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val width = size.width
                        if (width > 0f) {
                            transformOrigin = TransformOrigin(currentLensPx() / width, 0.5f)
                        }
                        val deformation = currentDeformation()
                        scaleX = deformation.scaleX
                        scaleY = deformation.scaleY
                    }
            ) {
                if (!lensReady) return@Canvas
                val center = Offset(currentLensPx(), size.height / 2f)

                // The droplet's own contact shadow: independent of the capsule's elevation and
                // offset farther down, so the puck reads as resting *on* the glass. It is clipped
                // to the outside of the lens: a shadow that reached under the crystal would tint
                // the magnified row and bring the milky disc straight back.
                val shadowOutsideLens = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addOval(
                        Rect(
                            center.x - lensRadiusPx,
                            center.y - lensRadiusPx,
                            center.x + lensRadiusPx,
                            center.y + lensRadiusPx
                        )
                    )
                }
                clipPath(shadowOutsideLens) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(
                                    alpha = LiquidGlassLensSpec.DROPLET_SHADOW_ALPHA
                                ),
                                Color.Black.copy(
                                    alpha = LiquidGlassLensSpec.DROPLET_SHADOW_ALPHA * 0.4f
                                ),
                                Color.Transparent
                            ),
                            center = Offset(center.x, center.y + dropletOffsetPx),
                            radius = dropletShadowRadiusPx
                        ),
                        radius = dropletShadowRadiusPx,
                        center = Offset(center.x, center.y + dropletOffsetPx)
                    )
                }

                // The translucent crystal core. 0.12 white: the projection underneath stays the
                // dominant layer and remains legible while the lens is in transit.
                drawCircle(
                    color = Color.White.copy(alpha = LiquidGlassLensSpec.LENS_CORE_ALPHA),
                    radius = lensRadiusPx,
                    center = center
                )

                // The caustic: light refracted through the curved rim lands along the inner
                // perimeter, transparent at the axis.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = LiquidGlassLensSpec.LENS_CAUSTIC_ALPHA)
                        ),
                        center = center,
                        radius = lensRadiusPx
                    ),
                    radius = lensRadiusPx,
                    center = center
                )

                // The room, reflected once in the upper-left quadrant.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(
                            center.x - lensRadiusPx * 0.34f,
                            center.y - lensRadiusPx * 0.38f
                        ),
                        radius = hotspotRadiusPx * 1.6f
                    ),
                    radius = hotspotRadiusPx,
                    center = Offset(
                        center.x - lensRadiusPx * 0.34f,
                        center.y - lensRadiusPx * 0.38f
                    )
                )

                // The dual specular ring: incandescent top-left, translucent flanks, a soft
                // ambient bounce at the bottom-right.
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = LiquidGlassLensSpec.LENS_RIM_TOP_ALPHA),
                            Color.White.copy(alpha = LiquidGlassLensSpec.LENS_RIM_MID_ALPHA),
                            Color.White.copy(alpha = LiquidGlassLensSpec.LENS_RIM_BOTTOM_ALPHA)
                        ),
                        start = Offset(center.x - lensRadiusPx, center.y - lensRadiusPx),
                        end = Offset(center.x + lensRadiusPx, center.y + lensRadiusPx)
                    ),
                    radius = (lensRadiusPx - lensRimPx / 2f).coerceAtLeast(0f),
                    center = center,
                    style = Stroke(width = lensRimPx)
                )

                // The meniscus: a second hairline that gives the glass its thickness.
                drawCircle(
                    color = Color.White.copy(alpha = 0.32f),
                    radius = (lensRadiusPx - meniscusInsetPx).coerceAtLeast(0f),
                    center = center,
                    style = Stroke(width = meniscusWidthPx)
                )

                // The specular arc across the upper rim.
                val arcRadius = (lensRadiusPx - arcInsetPx).coerceAtLeast(0f)
                drawArc(
                    color = Color.White.copy(alpha = 0.62f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = arcWidthPx, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * One tab, drawn by either pass.
 *
 * The magnification is a `graphicsLayer` on the slot's vector content about its own centre, so the
 * slot's layout anchor does not move and the composable is never rasterised into a bitmap. The
 * label keeps the same two-state weight and colour as the glyph: type that changes size under a
 * finger is a magnification, not a re-layout.
 */
@Composable
private fun CrystalTabSlot(
    item: NavigationTabItem,
    active: Boolean,
    tint: Color,
    glyphWeight: Float,
    showIcons: Boolean,
    showLabels: Boolean,
    iconSize: Dp,
    badgeLabel: @Composable (Int) -> String,
    badgeContainer: Color,
    badgeContent: Color,
    statusRing: Color,
    scale: Float,
    decorations: Boolean,
    interactive: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    tag: String,
    badgeTag: String,
    modifier: Modifier = Modifier
) {
    val labelStyle = TextStyle(
        fontSize = LiquidGlassLensSpec.LABEL_FONT_SP.sp,
        lineHeight = (LiquidGlassLensSpec.LABEL_FONT_SP + 2f).sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        letterSpacing = 0.15.sp
    )

    Column(
        modifier = modifier
            .testTag(tag)
            .then(
                if (interactive) {
                    Modifier.selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = onSelect
                    )
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showIcons) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center
            ) {
                val glyph = if (active) item.selectedIcon ?: item.icon else item.icon
                if (glyph != null) {
                    glyph.invoke(tint, glyphWeight, Modifier.matchParentSize())
                }
                if (decorations && item.statusDot != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(LiquidGlassLensSpec.STATUS_DOT_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(item.statusDot)
                            .border(1.2.dp, statusRing, CircleShape)
                    )
                }
                if (decorations && item.badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .testTag(badgeTag)
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-5).dp)
                            .defaultMinSize(
                                minWidth = LiquidGlassLensSpec.BADGE_MIN_SIZE_DP.dp,
                                minHeight = LiquidGlassLensSpec.BADGE_MIN_SIZE_DP.dp
                            )
                            .clip(CircleShape)
                            .background(badgeContainer)
                            .border(1.2.dp, statusRing, CircleShape)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeLabel(item.badgeCount),
                            color = badgeContent,
                            style = TextStyle(
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
            if (showLabels) {
                Spacer(modifier = Modifier.height(LiquidGlassLensSpec.LABEL_GAP_DP.dp))
            }
        }
        if (showLabels) {
            Text(
                text = item.title,
                color = tint,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
