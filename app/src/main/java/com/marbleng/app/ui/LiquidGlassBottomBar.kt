package com.marbleng.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.LensDeformation
import com.marbleng.app.model.LiquidGlassLensSpec
import com.marbleng.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.abs

/** MARBLE_LIQUID_GLASS_LENS_V247 — the marker the integrity audit greps for. */
const val LIQUID_GLASS_BAR_MARKER: String = "MARBLE_LIQUID_GLASS_LENS_V247"

/**
 * One tab of [LiquidGlassBottomBar].
 *
 * [icon] and [selectedIcon] are composable slots rather than drawables, because the two-tier optic
 * renders the *same* slot twice — once flat and muted for the base layer, once magnified and
 * vibrant for the projection the lens reveals. A slot can draw a `MarbleGlyph` on a `Canvas`, an
 * `ImageVector` through [Icon] (see [glassTabItem]), or anything else the host app owns.
 *
 * [accent] is the colour the tab turns while the glass is over it. [statusDot] is the living
 * connection light the fourth slot carries when a tunnel is up. [badgeCount] is the red counter;
 * zero draws nothing.
 */
@Immutable
data class GlassTabItem(
    val key: String,
    val title: String,
    val badgeCount: Int = 0,
    val accent: Color? = null,
    val statusDot: Color? = null,
    val icon: (@Composable (tint: Color, weight: Float, modifier: Modifier) -> Unit)? = null,
    val selectedIcon: (@Composable (tint: Color, weight: Float, modifier: Modifier) -> Unit)? = icon
)

/**
 * The `ImageVector` flavour of [GlassTabItem]: the brief's `title` / `icon` / `selectedIcon` /
 * `badgeCount` quadruple, without asking the caller to write a composable slot by hand.
 */
fun glassTabItem(
    title: String,
    icon: ImageVector,
    selectedIcon: ImageVector = icon,
    key: String = title,
    badgeCount: Int = 0,
    accent: Color? = null
): GlassTabItem = GlassTabItem(
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
 * The material of the bar. Every stop the brief names is a field, so a host theme can replace the
 * whole skin without touching the optics.
 */
@Immutable
data class GlassBarColors(
    val glassTop: Color,
    val glassBottom: Color,
    val rimTop: Color,
    val rimMid: Color,
    val rimBottom: Color,
    val lensTint: Color,
    val lensRimTop: Color,
    val lensRimMid: Color,
    val lensRimBottom: Color,
    val lensCaustic: Color,
    val lensFringe: Color,
    val innerGlow: Color,
    val inactiveContent: Color,
    val activeContent: Color,
    val badgeContainer: Color,
    val badgeContent: Color,
    val statusRing: Color,
    val ambientShadow: Color
) {
    companion object {
        /** The pure material: light or dark, from five colours. Tested on the JVM. */
        fun of(
            dark: Boolean,
            accent: Color,
            inactiveContent: Color,
            badgeContainer: Color,
            badgeContent: Color,
            surface: Color
        ): GlassBarColors {
            val wash = if (dark) LiquidGlassLensSpec.GLASS_ALPHA_DARK else LiquidGlassLensSpec.GLASS_ALPHA
            return GlassBarColors(
                glassTop = Color.White.copy(alpha = wash),
                glassBottom = Color.White.copy(
                    alpha = if (dark) wash * 0.55f else (wash - 0.06f).coerceAtLeast(0f)
                ),
                rimTop = Color.White.copy(alpha = if (dark) 0.42f else 0.9f),
                rimMid = Color.White.copy(alpha = 0.15f),
                rimBottom = Color.White.copy(alpha = if (dark) 0.12f else 0.45f),
                lensTint = Color.White.copy(
                    alpha = LiquidGlassLensSpec.LENS_GLASS_ALPHA * if (dark) 0.7f else 1f
                ),
                lensRimTop = Color.White.copy(alpha = 0.85f),
                lensRimMid = Color.White.copy(alpha = 0.15f),
                lensRimBottom = Color.White.copy(alpha = 0.45f),
                lensCaustic = Color.White.copy(alpha = if (dark) 0.1f else 0.22f),
                lensFringe = accent.copy(alpha = 0.1f),
                innerGlow = Color.White.copy(alpha = if (dark) 0.1f else 0.22f),
                inactiveContent = inactiveContent,
                activeContent = accent,
                badgeContainer = badgeContainer,
                badgeContent = badgeContent,
                statusRing = if (dark) surface else Color.White,
                ambientShadow = Color.Black.copy(alpha = LiquidGlassLensSpec.OUTER_SHADOW_ALPHA)
            )
        }

        /** The theme-adaptive material: the bar follows the app's scheme unless told otherwise. */
        @Composable
        fun themed(accent: Color = MaterialTheme.colorScheme.primary): GlassBarColors {
            val scheme = MaterialTheme.colorScheme
            val dark = scheme.surface.luminance() < 0.35f
            return remember(scheme, accent, dark) {
                of(
                    dark = dark,
                    accent = accent,
                    inactiveContent = scheme.onSurfaceVariant,
                    badgeContainer = scheme.error,
                    badgeContent = scheme.onError,
                    surface = scheme.surface
                )
            }
        }
    }
}

/**
 * MARBLE_LIQUID_GLASS_LENS_V247 — the floating liquid-glass magnifier navigation bar.
 *
 * ## The two-tier optical projection
 *
 * A lens is not an icon effect. Everything under the glass is one continuous, magnified image of
 * the row, and the glass is the aperture that reveals it. Two stacked rows deliver exactly that
 * without a single GPU shader:
 *
 * 1. **Base layer** — every tab at scale 1.0, muted (`#757575`-class content colour), regular
 *    weight. This is the bar as it is: nothing moves, nothing switches.
 * 2. **Overlay layer** — literally the same tabs, in the same [Row] with the same weights and the
 *    same padding, so their layout bounds are identical; drawn vibrant, bold, and scaled about each
 *    tab's own anchor by [LiquidGlassLensSpec.magnificationAt]. The whole layer is then clipped to
 *    the lens aperture (`clipPath` inside `drawWithContent`), so only the part of the magnified
 *    projection that the glass covers is visible.
 *
 * Because the projection scale falls to exactly `1.0` at the rim, the two layers are continuous at
 * the aperture edge: there is no seam, no pop, and no "selected tab" that switches colour at a
 * boundary. The disc itself is drawn on top of the projection — contact shadow, glass body, rim
 * light, meniscus, caustic, specular arc — so it reads as a physical object over the row rather
 * than as a coloured circle behind it.
 *
 * ## The gesture and the physics
 *
 * One `pressure`/`drag` pair owns the aperture, and it is the only writer of its position:
 *
 * - **down** — the aperture is under the finger in the next frame (the held pixel is read straight
 *   by the draw phase, never animated), it grows 8 %, and one `SegmentTick` fires.
 * - **drag** — every move writes the clamped centre; the [VelocityTracker] feeds the liquid squish
 *   (`scaleX = 1 + min(|v|·0.0003, 0.25)`, `scaleY = 1 − min(|v|·0.00015, 0.12)`); crossing a tab
 *   edge is one more tick and nothing else.
 * - **up** — the nearest tab is published, velocity decays, and the aperture springs to that tab's
 *   anchor at [LiquidGlassLensSpec.SNAP_DAMPING_RATIO] / [LiquidGlassLensSpec.SNAP_STIFFNESS].
 *
 * The drag position, the velocity and the press progress are only ever read inside `graphicsLayer`
 * and `Canvas` blocks, so a scrub invalidates drawing, not the composition tree. A 120 Hz panel
 * gets a frame-time cost of one clip path and one shape draw.
 *
 * ## Why there is no AGSL shader
 *
 * The brief offers an AGSL runtime shader on Android 13+ as an optional enhancement. It is not
 * used, for the reason the two-tier architecture exists: a shader that fails to compile on a
 * device draws nothing at all, which for a navigation bar means the app loses its bottom chrome.
 * The
 * distortion formula itself is not lost — it is evaluated on the JVM in
 * [LiquidGlassLensSpec.magnificationAt], sampled at every tab anchor, which is a curvature profile
 * rather than a flat scale. The only `RenderEffect` in the path is the platform backdrop blur of
 * the capsule, owned by `Modifier.blur`.
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<GlassTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 60.dp,
    floating: Boolean = true,
    showIcons: Boolean = true,
    showLabels: Boolean = true,
    iconSize: Dp = 23.dp,
    colors: GlassBarColors = GlassBarColors.themed(),
    badgeLabel: @Composable (Int) -> String = { count -> LiquidGlassLensSpec.badgeText(count) },
    backdrop: GraphicsLayer? = null,
    backdropOrigin: Offset = Offset.Zero,
    tagPrefix: String = "glass-tab-",
    badgeTagPrefix: String = "glass-badge-"
) {
    if (tabs.isEmpty()) return
    val count = tabs.size
    val activeIndex = selectedIndex.coerceIn(0, count - 1)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val rtl = layoutDirection == LayoutDirection.Rtl
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val shape = if (floating) RoundedCornerShape(percent = 50) else RoundedCornerShape(0.dp)

    // ---- measured geometry -----------------------------------------------------------------
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    val innerPaddingPx = with(density) { LiquidGlassLensSpec.INNER_PADDING_DP.dp.toPx() }
    val insetPx = with(density) { LiquidGlassLensSpec.LENS_INSET_DP.dp.toPx() }
    val barHeightPx = with(density) { barHeight.toPx() }
    val contentWidthPx = (containerSize.width - innerPaddingPx * 2f).coerceAtLeast(1f)
    val itemWidthPx = LiquidGlassLensSpec.itemWidthPx(contentWidthPx, count)
    val lensRadiusPx = LiquidGlassLensSpec.lensRadiusPx(barHeightPx, insetPx)

    // ---- state: aperture, press, velocity ---------------------------------------------------
    val lensOffsetX = remember { Animatable(0f) }
    var heldOffsetX by remember { mutableStateOf<Float?>(null) }
    var velocityX by remember { mutableFloatStateOf(0f) }
    var pressHeld by remember { mutableStateOf(false) }
    var dragHeld by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    // Press and drag are two pieces of state because they arrive from two detectors: a touch that
    // has not moved yet is still a touch, and the puck has to answer it.
    val pressed = pressHeld || dragHeld
    val lensReady = placed && containerSize.width > 0

    val settleSpec: FiniteAnimationSpec<Float> = remember(reducedMotion) {
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = LiquidGlassLensSpec.SNAP_DAMPING_RATIO,
                stiffness = LiquidGlassLensSpec.SNAP_STIFFNESS
            )
        }
    }

    val pressProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = LiquidGlassLensSpec.PRESS_DAMPING_RATIO,
            stiffness = LiquidGlassLensSpec.PRESS_STIFFNESS
        ),
        label = "liquidGlassPress"
    )

    fun anchorOf(index: Int): Float = LiquidGlassLensSpec.tabCenterX(
        index = index,
        itemWidthPx = itemWidthPx,
        containerWidthPx = contentWidthPx,
        rtl = rtl
    )

    fun tabAt(x: Float): Int = LiquidGlassLensSpec.indexAt(
        lensCenterX = x,
        itemWidthPx = itemWidthPx,
        count = count,
        containerWidthPx = contentWidthPx,
        rtl = rtl
    )

    fun clamped(x: Float): Float =
        LiquidGlassLensSpec.clampLensCenterX(x, contentWidthPx, itemWidthPx)

    // The single reader the draw phase gets. Never called during composition, so a drag
    // recomposes nothing: the pixel reaches the clip path and the disc through the draw phase.
    val currentLensX: () -> Float = { heldOffsetX ?: lensOffsetX.value }
    val lensDeformation: () -> LensDeformation = {
        val press = LiquidGlassLensSpec.pressScale(pressProgress.value)
        val squish = LiquidGlassLensSpec.deformation(velocityX)
        LensDeformation(squish.scaleX * press, squish.scaleY * press)
    }

    // The projection of an anchor under the current aperture. Read-only over the draw state, so a
    // drag calls it from `graphicsLayer` without recomposing a single tab.
    fun projectionAt(anchorX: Float): Float =
        if (!lensReady) {
            1f
        } else {
            LiquidGlassLensSpec.magnificationAt(
                distancePx = abs(currentLensX() - anchorX),
                radiusPx = lensRadiusPx * lensDeformation().scaleX
            )
        }

    // A selection that arrives from outside the bar — a deep link, the back gesture — travels with
    // the same spring a finger would have produced. The first measurement seats the aperture on the
    // active tab with no animation at all.
    LaunchedEffect(activeIndex, count, contentWidthPx, itemWidthPx, rtl) {
        if (contentWidthPx <= 1.5f) return@LaunchedEffect
        val target = anchorOf(activeIndex)
        if (!placed) {
            lensOffsetX.snapTo(target)
            placed = true
            return@LaunchedEffect
        }
        // A finger owns the aperture while it is down; the release path below owns it until the
        // spring has adopted the pixel the finger let go at.
        if (heldOffsetX != null) return@LaunchedEffect
        // A rotation can leave the pixel outside a capsule that is now narrower: re-seat it before
        // the spring, never during.
        val seated = clamped(lensOffsetX.value)
        if (seated != lensOffsetX.value) lensOffsetX.snapTo(seated)
        lensOffsetX.animateTo(target, settleSpec)
    }

    // The press detector only reports that a finger is down, so the puck grows 8 % on the frame of
    // the touch instead of on the first pixel of a swipe. It is an ancestor of the tabs, and
    // Compose dispatches the main pass from the innermost node outwards, so a tab's own
    // `selectable` click still resolves before this detector sees the up.
    val pressModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                pressHeld = true
                tryAwaitRelease()
                pressHeld = false
            }
        )
    }

    val dragModifier = Modifier.pointerInput(count, contentWidthPx, itemWidthPx, rtl, haptics) {
        if (count == 0 || contentWidthPx <= 1.5f) return@pointerInput
        val tracker = VelocityTracker()
        var slopCarried = false
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                dragHeld = true
                velocityX = 0f
                val start = clamped(offset.x - innerPaddingPx)
                heldOffsetX = start
                // `onDragStart` fires after the touch slop has been consumed and reports the
                // position at that instant, so the first drag delta is the slop itself. The puck
                // was just placed under the finger; adding the slop again would push it ahead of
                // the finger by the slop distance.
                slopCarried = true
                tracker.resetTracking()
                tracker.addPosition(SystemClock.uptimeMillis(), Offset(start, 0f))
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                velocityX = tracker.calculateVelocity().x
                if (slopCarried) {
                    slopCarried = false
                } else {
                    val current = heldOffsetX ?: lensOffsetX.value
                    val next = clamped(current + dragAmount)
                    // One tick per crossed tab edge, and only when it is really crossed: a finger
                    // jittering inside one slot stays silent.
                    if (tabAt(current) != tabAt(next)) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    heldOffsetX = next
                }
            },
            onDragEnd = {
                val released = heldOffsetX ?: lensOffsetX.value
                val target = tabAt(released)
                dragHeld = false
                onTabSelected(target)
                val thrown = velocityX
                // The held pixel stays live until the spring's coroutine has adopted it, so the
                // aperture never jumps back to the old tab for a frame.
                scope.launch {
                    lensOffsetX.snapTo(released)
                    heldOffsetX = null
                    lensOffsetX.animateTo(anchorOf(target), settleSpec)
                }
                scope.launch {
                    animate(
                        initialValue = thrown,
                        targetValue = 0f,
                        initialVelocity = thrown,
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    ) { value, _ ->
                        velocityX = value
                    }
                }
            },
            onDragCancel = {
                val released = heldOffsetX ?: lensOffsetX.value
                val target = tabAt(released)
                dragHeld = false
                scope.launch {
                    lensOffsetX.snapTo(released)
                    heldOffsetX = null
                    lensOffsetX.animateTo(anchorOf(target), settleSpec)
                }
                velocityX = 0f
            }
        )
    }

    Box(
        modifier = modifier
            .then(
                if (floating) {
                    Modifier.padding(
                        start = LiquidGlassLensSpec.HORIZONTAL_MARGIN_DP.dp,
                        end = LiquidGlassLensSpec.HORIZONTAL_MARGIN_DP.dp,
                        bottom = LiquidGlassLensSpec.BOTTOM_MARGIN_DP.dp
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
                    elevation = LiquidGlassLensSpec.OUTER_SHADOW_DP.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.ambientShadow,
                    spotColor = colors.ambientShadow
                )
                .clip(shape)
                .background(colors.glassBottom)
                .border(
                    width = LiquidGlassLensSpec.RIM_WIDTH_DP.dp,
                    brush = Brush.verticalGradient(
                        listOf(colors.rimTop, colors.rimMid, colors.rimBottom)
                    ),
                    shape = shape
                )
                .onSizeChanged { containerSize = it }
                .onGloballyPositioned { containerOrigin = it.positionInWindow() }
                .then(pressModifier)
                .then(dragModifier)
        ) {
            // 1. The page behind the glass, sampled and blurred. `Modifier.blur` is the platform
            //    RenderEffect on Android 12+ and a documented no-op below it.
            if (backdrop != null) {
                val shift = backdropOrigin - containerOrigin
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(LiquidGlassLensSpec.BACKDROP_BLUR_DP.dp)
                ) {
                    translate(left = shift.x, top = shift.y) {
                        drawLayer(backdrop)
                    }
                }
            }

            // 2. The frosted acrylic of the capsule itself.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(colors.glassTop, colors.glassBottom)))
            )

            // 3. Base layer: the row as it is. Muted, regular weight, scale 1.0 — and the only
            //    layer with semantics, so assistive tech and tests see one tab, not two.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = LiquidGlassLensSpec.INNER_PADDING_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    GlassTabSlot(
                        item = tab,
                        active = false,
                        tint = colors.inactiveContent,
                        glyphWeight = 1f,
                        showIcons = showIcons,
                        showLabels = showLabels,
                        iconSize = iconSize,
                        badgeLabel = badgeLabel,
                        statusRing = colors.statusRing,
                        badgeContainer = colors.badgeContainer,
                        badgeContent = colors.badgeContent,
                        iconScale = { 1f },
                        labelScale = { 1f },
                        decorations = true,
                        interactive = true,
                        selected = index == activeIndex,
                        onSelect = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onTabSelected(index)
                        },
                        tag = tagPrefix + tab.key,
                        badgeTag = badgeTagPrefix + tab.key,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 4. Overlay layer: the same row, same bounds, vibrant and magnified about each anchor,
            //    revealed only inside the aperture. Semantics are cleared here — this layer is an
            //    optical duplicate of layer 3, not a second set of controls.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = LiquidGlassLensSpec.INNER_PADDING_DP.dp)
                    .clearAndSetSemantics { }
                    .drawWithContent {
                        // Before the first measurement there is no aperture to clip to; drawing
                        // the projection unmasked for one frame would flash the whole row vibrant.
                        if (!lensReady) return@drawWithContent
                        val lensX = currentLensX()
                        val deformation = lensDeformation()
                        val radiusX = lensRadiusPx * deformation.scaleX
                        val radiusY = lensRadiusPx * deformation.scaleY
                        val centreY = size.height / 2f
                        val aperture = Path().apply {
                            addOval(
                                Rect(
                                    lensX - radiusX,
                                    centreY - radiusY,
                                    lensX + radiusX,
                                    centreY + radiusY
                                )
                            )
                        }
                        clipPath(aperture) {
                            this@drawWithContent.drawContent()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val anchorX = LiquidGlassLensSpec.tabCenterX(
                        index = index,
                        itemWidthPx = itemWidthPx,
                        containerWidthPx = contentWidthPx,
                        rtl = rtl
                    )
                    GlassTabSlot(
                        item = tab,
                        active = true,
                        tint = tab.accent ?: colors.activeContent,
                        glyphWeight = 1.24f,
                        showIcons = showIcons,
                        showLabels = showLabels,
                        iconSize = iconSize,
                        badgeLabel = badgeLabel,
                        statusRing = colors.statusRing,
                        badgeContainer = colors.badgeContainer,
                        badgeContent = colors.badgeContent,
                        iconScale = { projectionAt(anchorX) },
                        labelScale = {
                            LiquidGlassLensSpec.labelMagnification(projectionAt(anchorX))
                        },
                        // The counter and the connection light belong to the bar, not to the
                        // projection: they are drawn once, on the base layer, where the finger
                        // and the screen reader both expect them.
                        decorations = false,
                        interactive = false,
                        selected = index == activeIndex,
                        onSelect = {},
                        tag = tagPrefix + tab.key,
                        badgeTag = badgeTagPrefix + tab.key,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 5. The glass disc itself, on top of the projection so its rim, meniscus and caustic
            //    read as an object in front of the row.
            Canvas(modifier = Modifier.matchParentSize()) {
                if (!lensReady) return@Canvas
                val lensX = innerPaddingPx + currentLensX()
                val deformation = lensDeformation()
                val radiusX = (lensRadiusPx * deformation.scaleX).coerceAtLeast(1f)
                val radiusY = (lensRadiusPx * deformation.scaleY).coerceAtLeast(1f)
                val centreY = size.height / 2f
                val topLeft = Offset(lensX - radiusX, centreY - radiusY)
                val oval = Size(radiusX * 2f, radiusY * 2f)

                // Ambient shadow: diffuse, offset down, wider than the puck.
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = LiquidGlassLensSpec.AMBIENT_SHADOW_ALPHA),
                            Color.Transparent
                        ),
                        center = Offset(lensX, centreY + radiusY * 0.45f),
                        radius = maxOf(radiusX, radiusY) * 1.4f
                    ),
                    topLeft = Offset(
                        lensX - radiusX * 1.12f,
                        topLeft.y + LiquidGlassLensSpec.AMBIENT_SHADOW_OFFSET_Y_DP.dp.toPx()
                    ),
                    size = Size(radiusX * 2.24f, radiusY * 2.12f)
                )

                // The glass body: a radial wash that peaks above the centre, so the top of the
                // disc catches the room's light.
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.lensTint,
                            colors.lensTint.copy(alpha = colors.lensTint.alpha * 0.35f)
                        ),
                        center = Offset(lensX, centreY - radiusY * 0.35f),
                        radius = maxOf(radiusX, radiusY) * 1.25f
                    ),
                    topLeft = topLeft,
                    size = oval
                )

                // The specular rim: crisp top-left, semi-transparent middle, soft bottom-right.
                drawOval(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.lensRimTop,
                            colors.lensRimMid,
                            colors.lensRimBottom
                        ),
                        start = topLeft,
                        end = Offset(topLeft.x + oval.width, topLeft.y + oval.height)
                    ),
                    topLeft = topLeft,
                    size = oval,
                    style = Stroke(width = LiquidGlassLensSpec.RIM_WIDTH_DP.dp.toPx())
                )

                // The meniscus: a second hairline that gives the glass thickness.
                val meniscusInset = 2.4.dp.toPx()
                drawOval(
                    color = colors.innerGlow,
                    topLeft = Offset(topLeft.x + meniscusInset, topLeft.y + meniscusInset),
                    size = Size(
                        (oval.width - meniscusInset * 2f).coerceAtLeast(1f),
                        (oval.height - meniscusInset * 2f).coerceAtLeast(1f)
                    ),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // The caustic: light bent through the drop lands on its lower half.
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.lensCaustic, Color.Transparent),
                        center = Offset(lensX, centreY + radiusY * 0.45f),
                        radius = radiusX * 0.7f
                    ),
                    topLeft = topLeft,
                    size = oval
                )

                // The specular arc across the upper rim: the room, reflected once.
                val arcInset = 2.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.5f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(topLeft.x + arcInset, topLeft.y + arcInset),
                    size = Size(
                        (oval.width - arcInset * 2f).coerceAtLeast(1f),
                        (oval.height - arcInset * 2f).coerceAtLeast(1f)
                    ),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )

                // The chromatic fringe: a lens this small splits light by a hair, not a rainbow.
                drawOval(
                    color = colors.lensFringe,
                    topLeft = Offset(topLeft.x + 0.6.dp.toPx(), topLeft.y),
                    size = oval,
                    style = Stroke(width = 0.7.dp.toPx())
                )
            }
        }
    }
}

/**
 * One tab, drawn by either layer.
 *
 * The magnification lives in `graphicsLayer` and the tint arrives as a plain argument, so a drag
 * never recomposes a slot — it invalidates the layer that reads [iconScale]. The label keeps a
 * two-state colour and weight: text that changes size under a finger reads as a bug rather than as
 * an optic, which is why only the glyph carries the full projection scale.
 */
@Composable
private fun GlassTabSlot(
    item: GlassTabItem,
    active: Boolean,
    tint: Color,
    glyphWeight: Float,
    showIcons: Boolean,
    showLabels: Boolean,
    iconSize: Dp,
    badgeLabel: @Composable (Int) -> String,
    statusRing: Color,
    badgeContainer: Color,
    badgeContent: Color,
    iconScale: () -> Float,
    labelScale: () -> Float,
    decorations: Boolean,
    interactive: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    tag: String,
    badgeTag: String,
    modifier: Modifier = Modifier
) {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
    )
    Column(
        modifier = modifier
            .testTag(tag)
            .then(
                if (interactive) {
                    Modifier.selectable(selected = selected, role = Role.Tab, onClick = onSelect)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showIcons) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            val scale = iconScale()
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.45f)
                        }
                ) {
                    val glyph = if (active) item.selectedIcon ?: item.icon else item.icon
                    if (glyph != null) {
                        glyph.invoke(tint, glyphWeight, Modifier.matchParentSize())
                    }
                }
                if (decorations && item.statusDot != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(9.dp)
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
                            .offset(x = 8.dp, y = (-6).dp)
                            .defaultMinSize(
                                minWidth = LiquidGlassLensSpec.BADGE_MIN_SIZE_DP.dp,
                                minHeight = LiquidGlassLensSpec.BADGE_MIN_SIZE_DP.dp
                            )
                            .clip(CircleShape)
                            .background(badgeContainer)
                            .border(1.4.dp, statusRing, CircleShape)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeLabel(item.badgeCount),
                            color = badgeContent,
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
                text = item.title,
                color = tint,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (showIcons) 2.dp else 0.dp)
                    .graphicsLayer {
                        val scale = labelScale()
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            )
        }
    }
}
