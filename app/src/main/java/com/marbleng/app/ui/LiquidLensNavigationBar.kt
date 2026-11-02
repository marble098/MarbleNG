package com.marbleng.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.GlassDock
import com.marbleng.app.model.LiquidLens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * MARBLE_LIQUID_LENS_V262 — the container: one glass pill, one travelling lens, and a row of slots
 * that tell it where they are.
 *
 * ## The three obligations of this file
 *
 * 1. **Be a pill.** The ladder's own corner — `DockStudio.cornerRadiusDp`, which *is*
 *    `RoundedCornerShape(percent = 50)` at every rung — a frosted tonal slab, one
 *    1dp hairline that is white at the crown and almost gone at the floor, and a 16dp / 4dp / 8 %
 *    shadow *offset downward* so the bar reads as hovering over the page. The shadow is its own
 *    layer because a Compose shadow is centred on its shape, and a centred shadow of a 16dp blur is
 *    a glow rather than a lift.
 * 2. **Coordinate.** It owns the measurement of the row and of every slot
 *    ([LiquidLensSlotReporter]), computes the one legal left edge for the lens
 *    ([LiquidLens.lensLeftPx] → [GlassDock.apertureLeftPx], a single clamp for the whole product)
 *    and hands it to [GlassLensIndicator]. Slots are *measured*, never assumed: the caption of the
 *    open tab changes the row's weights, the leading and trailing wings are part of the same row,
 *    and the centre style draws a taller capsule — an evenly divided row would put the glass off
 *    its tab in all three cases.
 * 3. **Follow the hand.** A horizontal drag moves the selection to the nearest slot, one change per
 *    slot crossed, so the lens is a thing that can be pushed rather than a highlight that waits for
 *    a tap. The drag reads the *measured* rectangles, so it keeps working while a caption is
 *    opening.
 *
 * ## What it deliberately does not do
 *
 * It does not clip. The row draws a counter past the icon's corner, a connection light lifted into
 * the row and the centre trigger's contact shadow — a `clip` on this Box would cut them at the
 * glass's edge. (MARBLE_STEADY_CONNECT_V264 retired the wing's tooltip; nothing in the dock draws a
 * word that opens any more.) The capsule's *material* is drawn with `drawWithContent`, which paints
 * without clipping the children.
 *
 * The material is a `DrawScope` function ([drawCapsuleMaterial]) rather than a composable modifier,
 * because [GlassLensIndicator] has to replay it in order to sample it: the lens shows the surface
 * it is lying on, and the only honest way to do that is to paint the same surface again, shifted.
 */
@Immutable
internal interface LiquidLensSlotReporter {
    /** A slot's rectangle, in root space; the container cancels the row's own rectangle out of it. */
    fun reportSlot(index: Int, bounds: Rect)
}

@Composable
internal fun LiquidLensNavigationBar(
    selectedIndex: Int,
    slotCount: Int,
    height: Dp,
    /** The ladder's own corner ([DockStudio.cornerRadiusDp]): one ratio, no second stadium. */
    corner: Dp,
    trackInset: Dp,
    surface: Color,
    highlight: Color,
    second: Color,
    dark: Boolean,
    accent: Color,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    barTag: String = "liquid-lens-bar",
    lensTag: String = "lumen-glass-lens",
    onDragSelect: ((Int) -> Unit)? = null,
    content: @Composable RowScope.(LiquidLensSlotReporter) -> Unit
) {
    if (slotCount <= 0) return

    val density = LocalDensity.current
    val slots = remember(slotCount) { mutableStateOf(List(slotCount) { Rect.Zero }) }
    val rowBounds = remember { mutableStateOf(Rect.Zero) }

    val reporter = remember(slotCount, slots) {
        object : LiquidLensSlotReporter {
            override fun reportSlot(index: Int, bounds: Rect) {
                if (index < 0 || index >= slotCount) return
                val current = slots.value
                // Guarded against its own re-run: a slot that reported the same rectangle twice
                // would recompose the whole bar forever.
                if (current.getOrNull(index) == bounds) return
                slots.value = current.toMutableList().also { it[index] = bounds }
            }
        }
    }

    // ── geometry ─────────────────────────────────────────────────────────────────────────────
    //
    // The lens fills the bar's inner row: the capsule minus one inset above and below, which is
    // exactly the row the tabs live in. `LiquidLens.lensDiameterDp` is the single place that
    // decides it, so the 56 / 68 / 82 dp ladder produces 44 / 52 / 60 dp lenses rather than three
    // hand-tuned radii.
    val barHeightDp = height.value
    val insetDp = trackInset.value
    val diameterDp = LiquidLens.lensDiameterDp(barHeightDp.roundToInt(), insetDp.roundToInt())
    val verticalInsetDp = ((barHeightDp - diameterDp) / 2f).coerceAtLeast(0f)
    val diameterPx = with(density) { diameterDp.dp.toPx() }
    val trackInsetPx = with(density) { insetDp.dp.toPx() }
    val verticalInsetPx = with(density) { verticalInsetDp.dp.toPx() }
    val barHeightPx = with(density) { height.toPx() }

    val row = rowBounds.value
    val slot = slots.value.getOrNull(selectedIndex.coerceIn(0, slotCount - 1)) ?: Rect.Zero
    val measured = row.width > 0f && slot.width > 0f
    val lensLeftPx = if (measured) {
        LiquidLens.lensLeftPx(
            barWidthPx = row.width,
            centrePx = slot.center.x - row.left,
            diameterPx = diameterPx,
            trackInsetPx = trackInsetPx
        )
    } else {
        0f
    }
    // The capsule's width in the *capsule's* space: the row is inset by exactly one track inset.
    val capsuleWidthPx = row.width + trackInsetPx * 2f
    val pillShape = RoundedCornerShape(corner)
    val cornerPx = with(density) { minOf(corner, height / 2f).toPx() }

    // ── the hand ─────────────────────────────────────────────────────────────────────────────
    //
    // The state objects are read inside the gesture, not captured by value: a drag that started
    // before the caption opened must still find the slot where it currently is.
    val dragModifier = Modifier.pointerInput(slotCount, onDragSelect != null) {
        // Null means "this bar is display-only" (a preview, a screenshot of the material): the
        // handler detaches rather than swallowing a drag the page behind it could have used.
        val select = onDragSelect ?: return@pointerInput
        var emitted = -1

        fun nearest(xPx: Float): Int {
            val rects = slots.value
            val rowLeft = rowBounds.value.left
            var best = -1
            var bestDistance = Float.MAX_VALUE
            rects.forEachIndexed { index, rect ->
                if (rect.width <= 0f) return@forEachIndexed
                val distance = abs(rect.center.x - rowLeft - xPx)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
            return best
        }

        detectHorizontalDragGestures(
            onDragStart = { start ->
                emitted = nearest(start.x)
                if (emitted >= 0) select(emitted)
            },
            onHorizontalDrag = { change, _ ->
                change.consume()
                val index = nearest(change.position.x)
                if (index >= 0 && index != emitted) {
                    emitted = index
                    select(index)
                }
            }
        )
    }

    Box(
        modifier = modifier.testTag(barTag),
        contentAlignment = Alignment.Center
    ) {
        // 1. The lift. A separate, contentless layer: the shadow is cast from the pill's own
        //    outline and pushed down [LiquidLens.PILL_SHADOW_OFFSET_Y_DP], so it is a shadow *under*
        //    an object rather than an aura *around* one.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = LiquidLens.PILL_SHADOW_OFFSET_Y_DP.dp)
                .shadow(
                    elevation = LiquidLens.PILL_SHADOW_BLUR_DP.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = LiquidLens.PILL_SHADOW_ALPHA),
                    spotColor = Color.Black.copy(alpha = LiquidLens.PILL_SHADOW_ALPHA)
                )
        )

        // 2. The pill and its row. The material is painted first, the children after it, and
        //    nothing is clipped.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawCapsuleMaterial(
                        width = size.width,
                        height = size.height,
                        cornerPx = cornerPx,
                        surface = surface,
                        highlight = highlight,
                        second = second,
                        dark = dark,
                        reduced = reduced
                    )
                    drawContent()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = trackInset)
            ) {
                // 3. The lens, drawn before the row: the glass is *over* the capsule's surface and
                //    *under* everything a reader has to read. A pane painted last would wash the
                //    selected glyph with its own accent — a lens magnifies what it covers, it does
                //    not tint it.
                if (measured) {
                    GlassLensIndicator(
                        targetLeftPx = lensLeftPx,
                        diameter = diameterDp.dp,
                        trackInsetPx = trackInsetPx,
                        verticalInsetPx = verticalInsetPx,
                        accent = accent,
                        dark = dark,
                        reduced = reduced,
                        backdrop = { travelPx ->
                            if (capsuleWidthPx > 0f) {
                                translate(
                                    left = -(travelPx + trackInsetPx),
                                    top = -verticalInsetPx
                                ) {
                                    drawCapsuleMaterial(
                                        width = capsuleWidthPx,
                                        height = barHeightPx,
                                        cornerPx = cornerPx,
                                        surface = surface,
                                        highlight = highlight,
                                        second = second,
                                        dark = dark,
                                        reduced = reduced
                                    )
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterStart),
                        tag = lensTag
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup()
                        .onPlaced { coordinates -> rowBounds.value = coordinates.boundsInRoot() }
                        .then(dragModifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    content(reporter)
                }
            }
        }
    }
}

/**
 * The capsule's material: what the bar is made of, as one draw pass.
 *
 * It is a plain `DrawScope` function — not a `Modifier` — for one reason: [GlassLensIndicator]
 * samples it. The lens draws this exact code again, translated so its own pixel (0,0) lands where
 * the lens's corner is, blurs the result and shows it through its circle. That is the difference
 * between a lens and a tinted disc: the disc invents a colour, the lens re-reads the bar.
 *
 * Five passes, in order, and each of them is a physical claim:
 *
 *  1. **The ground** — the palette's container tone at [GlassDock.SURFACE_ALPHA], lifted on a dark
 *     page, where a translucent tone over black is simply black.
 *  2. **The wash** — the palette's own two hues at a tenth: this is what makes the bar belong to
 *     the theme rather than to a neutral grey.
 *  3. **The body** — bright at the crown, dark at the floor. A slab lit from above is the oldest
 *     material cue there is, and it survives every palette.
 *  4. **The contact shadow** — a tight dark curve inside the bottom rim, so the glass has *depth*.
 *  5. **The hairline** — 1dp, white at [LiquidLens.PILL_BORDER_ALPHA] (0.6) and fading to
 *     [LiquidLens.PILL_BORDER_FLOOR_ALPHA], continuous round the whole stadium. V261's
 *     `outlineVariant` frame is gone: a material has an *edge*, not a border.
 */
internal fun DrawScope.drawCapsuleMaterial(
    width: Float,
    height: Float,
    cornerPx: Float,
    surface: Color,
    highlight: Color,
    second: Color,
    dark: Boolean,
    reduced: Boolean
) {
    if (width <= 0f || height <= 0f) return
    // The ladder's corner, never wider than half the short side: `RoundedCornerShape(percent = 50)`
    // at every rung, and a guard for a capsule shorter than its own radius.
    val radius = minOf(cornerPx, height / 2f, width / 2f).coerceAtLeast(0f)
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = width,
                bottom = height,
                radiusX = radius,
                radiusY = radius
            )
        )
    }

    // 1. The ground.
    drawPath(
        path = outline,
        color = surface.copy(alpha = if (dark) 0.88f else GlassDock.SURFACE_ALPHA)
    )
    if (dark) {
        clipPath(outline) {
            drawRect(color = Color.White.copy(alpha = 0.04f), size = Size(width, height))
        }
    }

    // 2 + 3. The palette's wash, and the vertical body wash that lights the crown.
    if (!reduced) {
        clipPath(outline) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        highlight.copy(alpha = 0.13f),
                        Color.Transparent,
                        second.copy(alpha = 0.09f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(width, height)
                ),
                size = Size(width, height)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.06f else 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = if (dark) 0.12f else 0.07f)
                    )
                ),
                size = Size(width, height)
            )
        }
    }

    // 4. The contact shadow at the floor.
    clipPath(outline) {
        val contact = GlassDock.CONTACT_SHADOW_DP.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.10f else 0.06f)),
                startY = height - contact,
                endY = height
            ),
            topLeft = Offset(0f, height - contact),
            size = Size(width, contact)
        )
    }

    // 5. The hairline: one continuous stroke, brightest where the light lands.
    drawPath(
        path = outline,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = LiquidLens.PILL_BORDER_ALPHA),
                Color.White.copy(alpha = LiquidLens.PILL_BORDER_FLOOR_ALPHA)
            )
        ),
        style = Stroke(width = LiquidLens.PILL_BORDER_DP.dp.toPx())
    )
}
