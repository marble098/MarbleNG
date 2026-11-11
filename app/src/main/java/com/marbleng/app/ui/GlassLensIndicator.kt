package com.marbleng.app.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.LiquidLens

/**
 * MARBLE_LIQUID_LENS_V262 — the convex lens: one puck of glass that travels along the row, blurs
 * what is behind it, magnifies what is under it and deforms while it is thrown.
 *
 * ## What is inside the puck, back to front
 *
 * | layer | what it is | why it is drawn |
 * |---|---|---|
 * | backdrop | the capsule's **own material**, replayed at the lens's position and blurred at `RenderEffect.createBlurEffect(12f, 12f, CLAMP)` (through Compose's `Modifier.blur`) | a lens has to show the surface it is sitting on, not a flat tint: this is the "frosted" half of the material. API 31+ only; below it the glass falls back to a soft radial frost, because the alternative is a solid disc pretending to be clear |
 * | prism wash | selected-tab and secondary palette colours at [LiquidLens.GLASS_ACCENT_ALPHA] / [LiquidLens.GLASS_PRISM_ALPHA] | a vivid, refracted hue says *which* destination is active without obscuring its glyph |
 * | pane | white at [LiquidLens.GLASS_ALPHA_LIGHT] (0.55), or 0.22 on a dark page | the body of the glass; the row's ink is printed **over** this, never under it |
 * | caustic | radial, transparent at the axis, bright at the perimeter | light refracted through a curved rim lands on the rim |
 * | refraction band | a short vertical gradient at the top | the pane has a thickness, and light enters it there |
 * | specular rim | 1.5dp stroke, gradient 0.95 → 0.15 from top-left to bottom-right | a glass edge is incandescent where the light hits it and all but gone opposite |
 * | meniscus | a second, dimmer hairline just inside the rim | the one cue that reads as *thickness* rather than as an outline |
 * | specular arc | one white arc across the upper rim, faded at both ends | the catch-light that makes the object a solid |
 *
 * ## Why nothing here recomposes while the lens moves
 *
 * The travel is one [Animatable]. Its `value` and its `velocity` are read **only** inside
 * `graphicsLayer` blocks and in the draw phase:
 *
 * ```kotlin
 * translationX = heldPosition.value ?: travel.value                                   // layer phase
 * scaleX = LiquidLens.deformation(if (heldPosition.value != null) 0f else travel.velocity).scaleX       // layer phase
 * backdrop(travel.value)                                        // draw phase
 * ```
 *
 * A drag, a tap and a spring recovery therefore invalidate drawing, never composition, and never
 * layout: the lens is a fixed-size node that is *translated*, not a `width` that is re-measured.
 * The same animated `velocity` is what produces the squash and stretch
 * ([LiquidLens.deformation]): 15 % wider and 8 % shorter at full throw, settling back to 1,1 with
 * the spring's own damping rather than on a timer.
 *
 * ## Reduced motion
 *
 * `still` swaps the spring for `snap()`: the lens is at its destination in one frame, in the same
 * object, at the same size. Reduced motion removes the travel, never the material.
 */
@Composable
internal fun GlassLensIndicator(
    targetLeftPx: Float,
    diameter: Dp,
    /**
     * The two insets that place the lens inside the capsule. The lens is laid out in the *row's*
     * coordinate space (the row is inset by the capsule's inner padding), and the backdrop sample
     * has to be replayed in the *capsule's* — one subtraction, done here, so the frost lines up
     * with the surface it is sampling.
     */
    trackInsetPx: Float,
    verticalInsetPx: Float,
    accent: Color,
    prism: Color,
    dark: Boolean,
    reduced: Boolean,
    dragging: Boolean = false,
    /**
     * Repaints the capsule's material as it would appear if the lens's own origin were the
     * capsule's. Nothing else in this file knows how the bar is painted — the lens samples the
     * bar's material, it does not invent one.
     */
    backdrop: DrawScope.(travelPx: Float) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "lumen-glass-lens"
) {
    val travel = remember { Animatable(0f) }
    val seated = remember { mutableStateOf(false) }
    // Keep a snapshot read in the layer/draw phases during direct manipulation.
    // A captured Float alone can leave a cached graphics layer at the previous target.
    val heldPosition = rememberUpdatedState(targetLeftPx.takeIf { dragging })
    // A left edge of zero is "not measured yet", never a legal resting place: both inner insets are
    // at least 2dp, so the first real target is always positive.
    val measured = targetLeftPx > 0.5f

    val settleSpec: FiniteAnimationSpec<Float> = remember(reduced) {
        if (reduced) {
            snap()
        } else {
            spring(
                dampingRatio = LiquidLens.SPRING_DAMPING_RATIO,
                stiffness = LiquidLens.SPRING_STIFFNESS
            )
        }
    }

    // Direct manipulation is not an animation: render the finger coordinate immediately.
    // Keep the spring synchronised while held so release settles from the last touch,
    // not from the previous tab. Only taps and release use the spring.
    LaunchedEffect(targetLeftPx, measured, settleSpec, dragging) {
        if (!measured) return@LaunchedEffect
        if (!seated.value || dragging) {
            travel.snapTo(targetLeftPx)
            seated.value = true
            return@LaunchedEffect
        }
        travel.animateTo(targetLeftPx, settleSpec)
    }

    // The backdrop effect, applied as Compose's own blur — the same
    // `RenderEffect.createBlurEffect(radius, radius, CLAMP)` the brief names, built by the
    // framework under the modifier instead of by hand, so the effect travels with the Compose
    // version the app is compiled against rather than with a cast at every call site.
    //
    // `Modifier.blur` is a documented no-op below API 31. Below it — and on the degraded graphics
    // stacks where the effect never lands — the glass is still glass: the pane, the rim and the
    // caustic never depended on the blur, and the fallback frost below supplies the depth. The
    // radius is stated in the model in *pixels* (12f, as the brief writes it) and converted once
    // here, so the composable carries no number of its own.
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurRadius = with(LocalDensity.current) { LiquidLens.GLASS_BLUR_RADIUS_PX.toDp() }
    val paneAlpha = if (dark) LiquidLens.GLASS_ALPHA_DARK else LiquidLens.GLASS_ALPHA_LIGHT

    Box(
        modifier = modifier
            .size(diameter)
            .graphicsLayer {
                translationX = heldPosition.value ?: travel.value
                val deformation = LiquidLens.deformation(if (heldPosition.value != null) 0f else travel.velocity)
                scaleX = deformation.scaleX
                scaleY = deformation.scaleY
                transformOrigin = TransformOrigin.Center
                // Not seated yet: the first frame is drawn before layout has reported where the
                // selected tab is, and a lens that flashes at the leading edge for one frame is
                // exactly the kind of thing a screenshot never catches and a reader always does.
                alpha = if (seated.value) 1f else 0f
            }
            .clip(CircleShape)
            .testTag(tag)
    ) {
        // 1. The backdrop: the bar's own material, sampled where the lens is, blurred.
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (blurSupported) Modifier.blur(blurRadius) else Modifier)
                .drawWithContent {
                    // `backdrop` is a `DrawScope.(Float) -> Unit`, i.e. a Function2: calling it with
                    // the draw scope explicitly is the same call the extension syntax would make.
                    backdrop(this, heldPosition.value ?: travel.value)
                    drawContent()
                }
        )

        // 2. The glass body and every optical tell, in one pass: they share one outline, and five
        //    composables clipped to the same circle would round the same corner five times.
        Canvas(modifier = Modifier.matchParentSize()) {
            val side = size.minDimension
            if (side <= 0f) return@Canvas
            val radius = side / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val rimPx = LiquidLens.GLASS_RIM_DP.dp.toPx()

            // The fallback frost, for the platforms with no backdrop sample.
            if (!blurSupported) {
                val frostCenter = Offset(
                    center.x - radius * LiquidLens.GLASS_FROST_X_RATIO,
                    center.y - radius * LiquidLens.GLASS_FROST_Y_RATIO
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = LiquidLens.GLASS_FROST_ALPHA),
                            Color.Transparent
                        ),
                        center = frostCenter,
                        radius = radius * LiquidLens.GLASS_FROST_RADIUS_RATIO
                    ),
                    radius = radius,
                    center = center
                )
            }

            // The selected route's colour is the lens's living identity, with a secondary hue
            // refracted across it. The wash remains translucent enough that the icon stays crisp.
            drawCircle(
                color = accent.copy(alpha = LiquidLens.GLASS_ACCENT_ALPHA),
                radius = radius,
                center = center
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        accent.copy(alpha = LiquidLens.GLASS_PRISM_ALPHA),
                        prism.copy(alpha = LiquidLens.GLASS_PRISM_ALPHA),
                        Color.Transparent
                    ),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius,
                center = center
            )

            // The pane itself.
            drawCircle(
                color = Color.White.copy(alpha = paneAlpha),
                radius = radius,
                center = center
            )

            // The caustic: transparent at the axis, bright at the curved perimeter.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = LiquidLens.GLASS_CAUSTIC_ALPHA)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // The refraction band: light entering the top of the pane and landing brighter a few
            // dp lower. Clipped by the lens's own circle, so it follows the curve.
            val bandHeight = radius * LiquidLens.GLASS_BAND_HEIGHT_RATIO
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = LiquidLens.GLASS_BAND_TOP_ALPHA),
                        Color.White.copy(alpha = LiquidLens.GLASS_BAND_FADE_ALPHA),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = bandHeight
                ),
                topLeft = Offset.Zero,
                size = Size(side, bandHeight)
            )

            // The specular rim: incandescent at the top-left, translucent at the bottom-right.
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = LiquidLens.GLASS_RIM_START_ALPHA),
                        accent.copy(alpha = 0.82f),
                        Color.White.copy(alpha = LiquidLens.GLASS_RIM_MID_ALPHA),
                        prism.copy(alpha = 0.70f),
                        Color.White.copy(alpha = LiquidLens.GLASS_RIM_END_ALPHA)
                    ),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = (radius - rimPx / 2f).coerceAtLeast(0f),
                center = center,
                style = Stroke(width = rimPx)
            )

            // The meniscus: the second hairline that gives the glass a thickness.
            drawCircle(
                color = Color.White.copy(alpha = LiquidLens.GLASS_INNER_GLOW_ALPHA),
                radius = (radius - rimPx - LiquidLens.GLASS_MENISCUS_DP.dp.toPx()).coerceAtLeast(0f),
                center = center,
                style = Stroke(width = LiquidLens.GLASS_MENISCUS_DP.dp.toPx())
            )

            // The catch-light across the upper rim: one arc, brightest at its crown.
            val arcRadius = (radius - rimPx - LiquidLens.GLASS_ARC_INSET_DP.dp.toPx()).coerceAtLeast(0f)
            drawArc(
                color = Color.White.copy(alpha = LiquidLens.GLASS_SPECULAR_ALPHA),
                startAngle = LiquidLens.GLASS_ARC_START_DEG,
                sweepAngle = LiquidLens.GLASS_ARC_SWEEP_DEG,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = LiquidLens.GLASS_ARC_WIDTH_DP.dp.toPx(), cap = StrokeCap.Round)
            )

            // The room, reflected once in the upper-left quadrant.
            val hotspot = Offset(
                center.x - radius * LiquidLens.GLASS_HOTSPOT_X_RATIO,
                center.y - radius * LiquidLens.GLASS_HOTSPOT_Y_RATIO
            )
            val hotspotRadius = radius * LiquidLens.GLASS_HOTSPOT_RADIUS_RATIO
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = LiquidLens.GLASS_HOTSPOT_ALPHA),
                        Color.Transparent
                    ),
                    center = hotspot,
                    radius = hotspotRadius
                ),
                radius = hotspotRadius,
                center = hotspot
            )
        }
    }
}
