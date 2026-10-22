package com.marbleng.app.ui

// MARBLE_AURORA_SURFACE_V219 — the drag-to-connect surface, rebuilt.
//
// The device complaints about the old bar were all about material: a flat 1.5 dp outline around a
// plain card fill, a progress rectangle that snapped to the thumb's left edge, and a solid colour
// disc with no light on it. It read as a 2014 range slider that someone had stretched.
//
// This file owns the material only, not the gesture — both slide controls keep their own drag maths
// and commit rules (V146/V207), and both mount these primitives, so the two clocks can never show
// different materials:
//
//  - the track is a *well*: card fill, a tone-tinted top edge, a rim that is lit at the top and
//    quiet at the bottom, and the tone's own cast shadow so it sits on the page;
//  - the travelled part is a gradient energy bed that grows with the thumb instead of a rectangle
//    that jumps to it, with a bright head exactly under the knob;
//  - three guide chevrons sit on the un-travelled side and fade as the thumb approaches, which is
//    how a modern slide control says "this way" without a paragraph of instruction;
//  - the thumb is a lit sphere: a vertical tone gradient, a specular cap, a white hairline ring and
//    a tone-cast shadow, so the finger has something physical to push.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The one geometry every slide control in the product is built from. */
internal object MarbleSlideSpec {
    val TrackHeight: Dp = 66.dp
    val TrackHeightCompact: Dp = 60.dp
    val ThumbSize: Dp = 54.dp
    val ThumbSizeCompact: Dp = 48.dp
    val Inset: Dp = 6.dp
}

/**
 * MARBLE_AURORA_SURFACE_V219 — the well the thumb travels in.
 *
 * [progress] is the live thumb fraction (0..1). It never changes the geometry of the track, only
 * how much of the tone's energy bed is lit, so the surface stays still while the gesture moves.
 */
@Composable
internal fun Modifier.marbleSlideTrack(
    shape: Shape,
    tone: Color,
    progress: Float,
    busy: Boolean,
    engaged: Boolean
): Modifier {
    val dark = homeCloudDark()
    val fill = marbleNativeCardFill()
    val engagedLift = if (engaged) (if (dark) .05f else .03f) else 0f
    val well = lerp(fill, tone, (if (dark) .16f else .085f) + engagedLift)
    val quiet = lerp(fill, tone, if (dark) .05f else .025f)
    val glow = tone.copy(alpha = if (dark) .40f else .30f)
    val litRim = lerp(tone, Color.White, if (dark) .42f else .60f)
    val baseRim = lerp(fill, tone, if (dark) .30f else .18f)

    return this
        .marbleShadow(
            elevation = 12.dp,
            shape = shape,
            clip = false,
            ambientColor = glow.copy(alpha = glow.alpha * .55f),
            spotColor = glow
        )
        .clip(shape)
        .background(Brush.verticalGradient(listOf(well, fill, quiet)))
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) .07f else .22f),
                    Color.White.copy(alpha = 0f)
                ),
                startY = 0f,
                endY = 28f
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    litRim.copy(alpha = if (dark) .48f else .62f),
                    baseRim.copy(alpha = if (dark) .55f else .42f)
                )
            ),
            shape = shape
        )
}

/**
 * MARBLE_AURORA_SURFACE_V219 — the tone's energy bed, drawn under the thumb.
 *
 * A gradient that ends exactly under the knob (never past it) and a soft head, so the travelled
 * distance is legible at a glance without a second progress indicator.
 */
@Composable
internal fun MarbleSlideEnergyBed(
    tone: Color,
    progress: Float,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    val settled = progress.coerceIn(0f, 1f)
    val motion = MarbleMotion.current
    Canvas(modifier) {
        if (settled <= 0.001f) return@Canvas
        val head = size.width * settled
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    tone.copy(alpha = .05f),
                    tone.copy(alpha = .16f),
                    tone.copy(alpha = .30f)
                ),
                startX = 0f,
                endX = head.coerceAtLeast(1f)
            ),
            size = Size(head, size.height)
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, tone.copy(alpha = .55f)),
                startX = 0f,
                endX = head.coerceAtLeast(1f)
            ),
            start = Offset(0f, size.height / 2f),
            end = Offset(head, size.height / 2f),
            strokeWidth = size.height
        )
        if (busy) {
            val sweep = motion.loop(1_200)
            val band = size.width * .34f
            val x = sweep * (head + band) - band
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = .16f),
                        Color.Transparent
                    ),
                    startX = x,
                    endX = x + band
                ),
                topLeft = Offset(x.coerceAtLeast(0f), 0f),
                size = Size(band, size.height)
            )
        }
    }
}

/**
 * MARBLE_AURORA_SURFACE_V219 — three chevrons that say "this way" and get out of the way.
 *
 * [fade] is the remaining travel: at 1 the marks are at full strength, at 0 they are gone. They are
 * drawn flat (never a ripple, never a loop) because the gesture is the motion.
 */
@Composable
internal fun MarbleSlideGuideMarks(
    tone: Color,
    fade: Float,
    modifier: Modifier = Modifier,
    pointingEnd: Boolean = true
) {
    val strength = fade.coerceIn(0f, 1f)
    Canvas(modifier) {
        val stroke = Stroke(
            width = (2.2f.dp.toPx()).coerceAtMost(size.height * .13f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val unit = size.minDimension
        val arm = unit * .30f
        val centerY = size.height / 2f
        val step = unit * .82f
        val startX = if (pointingEnd) size.width * .34f else size.width * .66f
        val direction = if (pointingEnd) 1f else -1f

        repeat(3) { index ->
            val alpha = strength * (.62f - index * .17f)
            if (alpha <= .02f) return@repeat
            val x = startX + direction * step * index
            val path = Path().apply {
                moveTo(x - direction * arm * .55f, centerY - arm)
                lineTo(x + direction * arm * .55f, centerY)
                lineTo(x - direction * arm * .55f, centerY + arm)
            }
            drawPath(path, tone.copy(alpha = alpha), style = stroke)
        }
    }
}

/**
 * MARBLE_AURORA_SURFACE_V219 — the lit sphere the finger pushes.
 *
 * One gradient for the body, one specular cap, one hairline ring, one tone-cast shadow. The caller
 * owns the offset and the content (the glyph), so this stays a material and never becomes a control.
 */
@Composable
internal fun Modifier.marbleSlideThumb(
    tone: Color,
    thumbSize: Dp,
    lifted: Boolean
): Modifier {
    val dark = homeCloudDark()
    val face = if (dark) lerp(tone, Color.White, .10f) else tone
    val faceDeep = lerp(tone, Color.Black, if (dark) .30f else .18f)
    val glow = tone.copy(alpha = if (dark) .55f else .45f)
    val shape = CircleShape

    return this
        .size(thumbSize)
        .marbleShadow(
            elevation = if (lifted) 14.dp else 10.dp,
            shape = shape,
            clip = false,
            ambientColor = glow.copy(alpha = glow.alpha * .50f),
            spotColor = glow
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    lerp(face, Color.White, .26f),
                    face,
                    faceDeep
                )
            )
        )
        .background(
            Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = .34f), Color.White.copy(alpha = 0f)),
                startY = 0f,
                endY = thumbSize.value * .9f
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = .70f),
                    Color.White.copy(alpha = .10f)
                )
            ),
            shape = shape
        )
}

/** The corner a slide track uses: a true stadium, whatever its height. */
internal fun marbleSlideShape(height: Dp): Shape = RoundedCornerShape(height / 2)

/** MARBLE_AURORA_SURFACE_V219 — the label's tone on the well, kept readable in both themes. */
@Composable
internal fun marbleSlideLabelTone(tone: Color, engaged: Boolean): Color {
    val dark = homeCloudDark()
    val base = if (engaged) tone else lerp(Aether.Ink, tone, if (dark) .55f else .45f)
    return marbleReadableOn(base, marbleNativeCardFill(), 4.5f)
}
