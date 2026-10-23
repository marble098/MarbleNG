package com.marbleng.app.ui

// MARBLE_AURORA_SURFACE_V219 — one glyph grid for the whole product.
//
// The complaint was that the icons looked hand-drawn and weak. They were: every icon in the app had
// been drawn on its own 0..1 box with its own margins, its own stroke width and its own corner
// treatment, so a 19 dp Settings glyph and a 22 dp row glyph were two different drawings of two
// different weights. Nothing line up, and at small sizes the strokes fell under 1.2 dp and went grey.
//
// This file replaces all of it with one grid, one weight and one set of round joins:
//
//  - **24-unit grid.** Every path below is authored in a 24 × 24 box, then scaled to the icon's real
//    size and centred, so every glyph has the same optical margin by construction.
//  - **1.75-unit stroke.** [marbleGlyphStrokePx] is the only stroke rule in the product: a 1.75 unit
//    stroke with round caps and joins, which is 1.3 dp at 18 dp, 1.6 dp at 22 dp and 2.0 dp at 28 dp.
//    It never reaches a hairline and never becomes a slab.
//  - **Duotone, not doodles.** Each glyph has one primary stroke and, where it helps the form read,
//    one soft secondary stroke (a palette's well, a shield's check, a folder's lip) at 55 % of the
//    weight. That is what makes a glyph legible at 16–20 dp where a single-weight outline dissolves.
//
// The drawing entry point is a plain `DrawScope` extension, so the same glyph can be painted inside
// a Canvas, inside a `drawWithCache`, or inside a `Spacer`'s draw behind; [MarbleGlyphIcon] is only
// the convenience wrapper.

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** The grid every glyph in the product is authored on. */
private const val MarbleGlyphGrid = 24f

/** The one stroke width of the grid, in grid units, before the caller's weight multiplier. */
private const val MarbleGlyphStrokeUnits = 1.75f

/**
 * MARBLE_AURORA_SURFACE_V219 — the product's icon vocabulary.
 *
 * Deliberately small and deliberately many-to-one: several Home icons map onto one glyph, because a
 * Settings page needs a *vocabulary* (twenty forms the eye learns once), not a picture of every row.
 */
internal enum class MarbleGlyph {
    HOME,
    TUNNEL,
    GAUGE,
    VAULT,
    PALETTE,
    TUNERS,
    POWER,
    STOP,
    REFRESH,
    ARROW_BACK,
    CHEVRON,
    CHEVRON_LEFT,
    CHEVRON_DOWN,
    CHECK,
    CLOSE,
    SHIELD,
    ROUTE,
    GLOBE,
    RADAR,
    SERVER,
    SPARK,
    FOLDER,
    LAYERS,
    LINK,
    DOCK,
    FONT,
    QR,
    INFO,
    TRASH,
    SEARCH,
    PLUS,
    MORE,
    MENU,
    FILTER,
    PENCIL,
    DOWNLOAD,
    UPLOAD,
    COPY,
    BELL,
    SHARE,
    EYE,
    EYE_OFF
}

/**
 * MARBLE_AURORA_SURFACE_V219 — the one stroke rule of the grid.
 *
 * Pure on purpose: the stroke is the product's optical contract, and a contract that can only be
 * checked on a device is not a contract. `MarbleGlyphArtV219Test` pins it for the JVM.
 */
internal fun marbleGlyphStrokePx(iconSizePx: Float, weight: Float = 1f): Float =
    (iconSizePx / MarbleGlyphGrid) * MarbleGlyphStrokeUnits * weight

/**
 * MARBLE_AURORA_SURFACE_V219 — draw [glyph], authored on the 24-unit grid, into this scope.
 *
 * [weight] multiplies the stroke only, never the geometry: a selected tab is the same drawing 8 %
 * heavier. A zero-sized canvas returns immediately, so a glyph inside a collapsing layout cannot ask
 * for a zero-width stroke.
 */
internal fun DrawScope.drawMarbleGlyph(
    glyph: MarbleGlyph,
    tint: Color,
    weight: Float = 1f,
    mirror: Boolean = false
) {
    val minSide = size.minDimension
    if (minSide <= 0f) return

    val unit = minSide / MarbleGlyphGrid
    // The stroke is quoted in grid units but floored in device pixels: a glyph drawn inside a 9 dp
    // chip must still resolve to at least one pixel, or it disappears into the antialiasing.
    val strokeUnits = (MarbleGlyphStrokeUnits * weight).coerceAtLeast(1f / unit)
    val stroke = Stroke(width = strokeUnits, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val fine = Stroke(
        width = (strokeUnits * .74f).coerceAtLeast(1f / unit),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val soft = tint.copy(alpha = tint.alpha * .55f)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = strokeUnits) {
        drawLine(tint, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)
    }

    fun softLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(soft, Offset(x1, y1), Offset(x2, y2), strokeUnits * .74f, StrokeCap.Round)
    }

    fun path(vararg points: Pair<Float, Float>): Path {
        val result = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) result.moveTo(point.first, point.second)
            else result.lineTo(point.first, point.second)
        }
        return result
    }

    fun strokePath(build: Path.() -> Unit): Path = Path().apply(build)

    fun roundRect(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        style: Stroke = stroke
    ) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(radius, radius),
            style = style
        )
    }

    fun arc(
        centerX: Float,
        centerY: Float,
        radius: Float,
        startAngle: Float,
        sweepAngle: Float,
        style: Stroke = stroke
    ) {
        drawArc(
            color = tint,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = style
        )
    }

    fun dot(x: Float, y: Float, radius: Float, color: Color = tint) {
        drawCircle(color, radius, Offset(x, y))
    }

    // Everything below is authored in grid units; the transform does the scaling and the centring.
    translate(
        // A mirrored glyph is the same drawing reflected about the grid's centre line: the scale is
        // negated and the whole grid is shifted one side over, so a back arrow or a disclosure
        // chevron points the way the reader reads without a second path being authored.
        left = (size.width - minSide) / 2f + (if (mirror) minSide else 0f),
        top = (size.height - minSide) / 2f
    ) {
        scale(if (mirror) -unit else unit, unit, pivot = Offset.Zero) {
            when (glyph) {
                MarbleGlyph.HOME -> {
                    drawPath(path(3f to 10.8f, 12f to 3.4f, 21f to 10.8f), tint, style = stroke)
                    drawPath(
                        strokePath {
                            moveTo(5.2f, 9.4f)
                            lineTo(5.2f, 18.6f)
                            quadraticBezierTo(5.2f, 20.6f, 7.2f, 20.6f)
                            lineTo(9.4f, 20.6f)
                            lineTo(9.4f, 15.2f)
                            lineTo(14.6f, 15.2f)
                            lineTo(14.6f, 20.6f)
                            lineTo(16.8f, 20.6f)
                            quadraticBezierTo(18.8f, 20.6f, 18.8f, 18.6f)
                            lineTo(18.8f, 9.4f)
                        },
                        tint,
                        style = stroke
                    )
                }

                MarbleGlyph.TUNNEL -> {
                    drawPath(
                        strokePath {
                            moveTo(4.4f, 20.6f)
                            lineTo(4.4f, 10.8f)
                            cubicTo(4.4f, 6.2f, 7.8f, 3.4f, 12f, 3.4f)
                            cubicTo(16.2f, 3.4f, 19.6f, 6.2f, 19.6f, 10.8f)
                            lineTo(19.6f, 20.6f)
                        },
                        tint,
                        style = stroke
                    )
                    drawPath(
                        strokePath {
                            moveTo(8.6f, 20.6f)
                            lineTo(8.6f, 12f)
                            cubicTo(8.6f, 9.4f, 10.2f, 7.8f, 12f, 7.8f)
                            cubicTo(13.8f, 7.8f, 15.4f, 9.4f, 15.4f, 12f)
                            lineTo(15.4f, 20.6f)
                        },
                        soft,
                        style = fine
                    )
                    softLine(2.6f, 20.6f, 21.4f, 20.6f)
                }

                MarbleGlyph.GAUGE -> {
                    arc(12f, 12.6f, 8.4f, 145f, 250f)
                    arc(12f, 12.6f, 6.6f, 145f, 250f, fine)
                    listOf(190f, 270f, 350f).forEach { angle ->
                        val radians = (angle * Math.PI / 180.0).toFloat()
                        val cx = kotlin.math.cos(radians)
                        val cy = kotlin.math.sin(radians)
                        softLine(
                            12f + cx * 2.2f,
                            12.6f + cy * 2.2f,
                            12f + cx * .2f,
                            12.6f + cy * .2f
                        )
                    }
                    drawLine(
                        tint,
                        Offset(12f, 12.6f),
                        Offset(16.6f, 8.4f),
                        strokeUnits,
                        StrokeCap.Round
                    )
                    dot(12f, 12.6f, .95f)
                }

                MarbleGlyph.VAULT -> {
                    roundRect(3.4f, 4.4f, 17.2f, 15.2f, 3.4f)
                    arc(12f, 12f, 4.2f, 0f, 360f)
                    line(12f, 7.8f, 12f, 9.4f)
                    line(12f, 14.6f, 12f, 16.2f)
                    line(7.8f, 12f, 9.4f, 12f)
                    dot(12f, 12f, 1.05f)
                }

                MarbleGlyph.PALETTE -> {
                    arc(12f, 12.4f, 8.4f, 32f, 306f)
                    drawPath(
                        strokePath {
                            moveTo(18.4f, 17.8f)
                            quadraticBezierTo(21.4f, 18.6f, 20.6f, 21.2f)
                        },
                        tint,
                        style = stroke
                    )
                    dot(8.8f, 9.6f, 1.25f)
                    dot(12.4f, 7.6f, 1.25f)
                    dot(16f, 9.8f, 1.25f)
                    dot(9.6f, 14.6f, 1.25f)
                }

                MarbleGlyph.TUNERS -> {
                    listOf(7f to 14.6f, 12f to 8.8f, 17f to 16.4f).forEach { (y, knob) ->
                        softLine(4f, y, 20f, y)
                        dot(knob, y, 2.1f)
                        arc(knob, y, 2.1f, 0f, 360f, fine)
                    }
                }

                MarbleGlyph.POWER -> {
                    arc(12f, 12.6f, 7.2f, -58f, 296f)
                    line(12f, 3.4f, 12f, 11.4f)
                }

                MarbleGlyph.STOP -> roundRect(6.4f, 6.4f, 11.2f, 11.2f, 3.2f)

                MarbleGlyph.REFRESH -> {
                    arc(12f, 12.4f, 7.6f, 42f, 278f)
                    drawPath(path(18.4f to 3.8f, 19.4f to 10.2f, 13.2f to 9.4f), tint, style = fine)
                }

                MarbleGlyph.ARROW_BACK -> {
                    line(20.2f, 12f, 9.8f, 12f)
                    drawPath(path(14.8f to 5.6f, 8.4f to 12f, 14.8f to 18.4f), tint, style = stroke)
                }

                MarbleGlyph.CHEVRON ->
                    drawPath(path(9.4f to 5.6f, 15.8f to 12f, 9.4f to 18.4f), tint, style = stroke)

                MarbleGlyph.CHECK ->
                    drawPath(path(4.8f to 12.4f, 9.8f to 17.4f, 19.2f to 6.8f), tint, style = stroke)

                MarbleGlyph.CLOSE -> {
                    line(6.6f, 6.6f, 17.4f, 17.4f)
                    line(17.4f, 6.6f, 6.6f, 17.4f)
                }

                MarbleGlyph.SHIELD -> {
                    drawPath(
                        strokePath {
                            moveTo(12f, 3.2f)
                            lineTo(19.6f, 6.2f)
                            lineTo(19.6f, 11.6f)
                            cubicTo(19.6f, 16f, 16.2f, 19.4f, 12f, 20.8f)
                            cubicTo(7.8f, 19.4f, 4.4f, 16f, 4.4f, 11.6f)
                            lineTo(4.4f, 6.2f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                    drawPath(path(8.8f to 11.8f, 11.4f to 14.2f, 15.6f to 9.4f), soft, style = fine)
                }

                MarbleGlyph.ROUTE -> {
                    drawPath(
                        strokePath {
                            moveTo(6.2f, 20.4f)
                            lineTo(6.2f, 16.2f)
                            quadraticBezierTo(6.2f, 13.4f, 9f, 13.4f)
                            lineTo(15.2f, 13.4f)
                            quadraticBezierTo(18f, 13.4f, 18f, 10.6f)
                            lineTo(18f, 7.4f)
                        },
                        tint,
                        style = stroke
                    )
                    dot(6.2f, 20.4f, 1.5f)
                    dot(18f, 7.2f, 1.5f)
                }

                MarbleGlyph.GLOBE -> {
                    arc(12f, 12f, 8.4f, 0f, 360f)
                    drawOval(
                        color = soft,
                        topLeft = Offset(6.4f, 3.6f),
                        size = Size(11.2f, 16.8f),
                        style = fine
                    )
                    line(3.8f, 12f, 20.2f, 12f)
                }

                MarbleGlyph.RADAR -> {
                    arc(12f, 12f, 8.4f, 0f, 360f)
                    arc(12f, 12f, 4.8f, 0f, 360f, fine)
                    line(12f, 12f, 17.8f, 7.4f)
                    dot(12f, 12f, 1.1f)
                }

                MarbleGlyph.SERVER -> {
                    roundRect(3.6f, 3.8f, 16.8f, 6.8f, 2.4f)
                    roundRect(3.6f, 13.4f, 16.8f, 6.8f, 2.4f)
                    dot(7f, 7.2f, 1.05f)
                    dot(7f, 16.8f, 1.05f)
                    softLine(10.4f, 7.2f, 17.2f, 7.2f)
                    softLine(10.4f, 16.8f, 17.2f, 16.8f)
                }

                MarbleGlyph.SPARK -> {
                    drawPath(
                        strokePath {
                            moveTo(12f, 3.2f)
                            quadraticBezierTo(12f, 12f, 20.8f, 12f)
                            quadraticBezierTo(12f, 12f, 12f, 20.8f)
                            quadraticBezierTo(12f, 12f, 3.2f, 12f)
                            quadraticBezierTo(12f, 12f, 12f, 3.2f)
                            close()
                        },
                        tint
                    )
                    dot(18.6f, 5.6f, 1.15f, soft)
                }

                MarbleGlyph.FOLDER -> {
                    drawPath(
                        strokePath {
                            moveTo(3.6f, 7.6f)
                            quadraticBezierTo(3.6f, 6f, 5.2f, 6f)
                            lineTo(9.6f, 6f)
                            lineTo(11.8f, 8.6f)
                            lineTo(18.8f, 8.6f)
                            quadraticBezierTo(20.4f, 8.6f, 20.4f, 10.2f)
                            lineTo(20.4f, 17.6f)
                            quadraticBezierTo(20.4f, 19.2f, 18.8f, 19.2f)
                            lineTo(5.2f, 19.2f)
                            quadraticBezierTo(3.6f, 19.2f, 3.6f, 17.6f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                    softLine(3.9f, 11.6f, 20.1f, 11.6f)
                }

                MarbleGlyph.LAYERS -> {
                    drawPath(
                        strokePath {
                            moveTo(12f, 3.4f)
                            lineTo(20.4f, 7.8f)
                            lineTo(12f, 12.2f)
                            lineTo(3.6f, 7.8f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                    drawPath(path(3.6f to 12f, 12f to 16.4f, 20.4f to 12f), soft, style = fine)
                    drawPath(path(3.6f to 16.2f, 12f to 20.6f, 20.4f to 16.2f), soft, style = fine)
                }

                MarbleGlyph.LINK -> {
                    arc(8.8f, 12f, 3.8f, 62f, 236f)
                    arc(15.2f, 12f, 3.8f, -118f, 236f)
                    line(9.6f, 12f, 14.4f, 12f)
                }

                MarbleGlyph.DOCK -> {
                    arc(12f, 12f, 8.4f, 0f, 360f)
                    drawPath(
                        strokePath {
                            moveTo(12f, 5.2f)
                            lineTo(15.2f, 12f)
                            lineTo(12f, 18.8f)
                            lineTo(8.8f, 12f)
                            close()
                        },
                        soft
                    )
                    dot(12f, 12f, 1f)
                }

                MarbleGlyph.FONT -> {
                    drawPath(path(5.4f to 19.6f, 11f to 4.4f, 13f to 4.4f, 18.6f to 19.6f), tint, style = stroke)
                    line(8.2f, 14f, 15.8f, 14f)
                }

                MarbleGlyph.QR -> {
                    roundRect(3.4f, 3.4f, 6.6f, 6.6f, 1.6f)
                    roundRect(14f, 3.4f, 6.6f, 6.6f, 1.6f)
                    roundRect(3.4f, 14f, 6.6f, 6.6f, 1.6f)
                    dot(8.2f, 8.2f, .75f)
                    dot(18.8f, 8.2f, .75f)
                    dot(8.2f, 18.8f, .75f)
                    dot(14.4f, 14.4f, 1.05f)
                    dot(18.6f, 16.6f, 1.05f)
                    dot(14.6f, 19.2f, 1.05f)
                }

                MarbleGlyph.INFO -> {
                    arc(12f, 12f, 8.4f, 0f, 360f)
                    dot(12f, 7.8f, 1.05f)
                    line(12f, 11f, 12f, 16.6f)
                }

                MarbleGlyph.TRASH -> {
                    roundRect(9.2f, 4.2f, 5.6f, 2.4f, 1.2f)
                    line(4.4f, 7.6f, 19.6f, 7.6f)
                    drawPath(
                        strokePath {
                            moveTo(6.4f, 7.6f)
                            lineTo(7.4f, 19.2f)
                            quadraticBezierTo(7.5f, 20.4f, 8.6f, 20.4f)
                            lineTo(15.4f, 20.4f)
                            quadraticBezierTo(16.5f, 20.4f, 16.6f, 19.2f)
                            lineTo(17.6f, 7.6f)
                        },
                        tint,
                        style = stroke
                    )
                    softLine(10.4f, 10.6f, 10.8f, 17.2f)
                    softLine(13.6f, 10.6f, 13.2f, 17.2f)
                }

                MarbleGlyph.SEARCH -> {
                    arc(10.6f, 10.6f, 6.6f, 0f, 360f)
                    line(15.4f, 15.4f, 20.4f, 20.4f)
                }

                MarbleGlyph.PLUS -> {
                    line(12f, 4.6f, 12f, 19.4f)
                    line(4.6f, 12f, 19.4f, 12f)
                }

                MarbleGlyph.MORE -> {
                    dot(5.4f, 12f, 1.7f)
                    dot(12f, 12f, 1.7f)
                    dot(18.6f, 12f, 1.7f)
                }

                MarbleGlyph.MENU -> {
                    line(4.6f, 7.4f, 19.4f, 7.4f)
                    line(4.6f, 12f, 15f, 12f)
                    line(4.6f, 16.6f, 19.4f, 16.6f)
                }

                MarbleGlyph.FILTER -> {
                    drawPath(
                        strokePath {
                            moveTo(4f, 5.4f)
                            lineTo(20f, 5.4f)
                            lineTo(13.8f, 12.4f)
                            lineTo(13.8f, 18.6f)
                            lineTo(10.2f, 20.4f)
                            lineTo(10.2f, 12.4f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                }

                MarbleGlyph.PENCIL -> {
                    drawPath(
                        strokePath {
                            moveTo(4.6f, 19.4f)
                            lineTo(5.6f, 15.2f)
                            lineTo(16.2f, 4.6f)
                            lineTo(19.4f, 7.8f)
                            lineTo(8.8f, 18.4f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                    softLine(14.6f, 6.2f, 17.8f, 9.4f)
                }

                MarbleGlyph.DOWNLOAD -> {
                    line(12f, 3.8f, 12f, 15.2f)
                    drawPath(path(7.4f to 10.6f, 12f to 15.4f, 16.6f to 10.6f), tint, style = stroke)
                    line(4.6f, 19.4f, 19.4f, 19.4f)
                }

                MarbleGlyph.UPLOAD -> {
                    line(12f, 15.6f, 12f, 4.2f)
                    drawPath(path(7.4f to 8.8f, 12f to 4f, 16.6f to 8.8f), tint, style = stroke)
                    line(4.6f, 19.4f, 19.4f, 19.4f)
                }

                MarbleGlyph.CHEVRON_LEFT ->
                    drawPath(path(14.6f to 5.6f, 8.2f to 12f, 14.6f to 18.4f), tint, style = stroke)

                MarbleGlyph.CHEVRON_DOWN ->
                    drawPath(path(5.6f to 9.4f, 12f to 15.8f, 18.4f to 9.4f), tint, style = stroke)

                MarbleGlyph.COPY -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(8.4f, 7.6f),
                        size = Size(11.2f, 12.2f),
                        cornerRadius = CornerRadius(2.4f, 2.4f),
                        style = stroke
                    )
                    drawPath(
                        Path().apply {
                            moveTo(15.2f, 7.2f)
                            lineTo(15.2f, 5.8f)
                            cubicTo(15.2f, 4.7f, 14.3f, 3.8f, 13.2f, 3.8f)
                            lineTo(6.4f, 3.8f)
                            cubicTo(5.3f, 3.8f, 4.4f, 4.7f, 4.4f, 5.8f)
                            lineTo(4.4f, 14.2f)
                            cubicTo(4.4f, 15.3f, 5.3f, 16.2f, 6.4f, 16.2f)
                            lineTo(8.0f, 16.2f)
                        },
                        soft,
                        style = fine
                    )
                }

                MarbleGlyph.BELL -> {
                    drawPath(
                        Path().apply {
                            moveTo(6.4f, 15.8f)
                            lineTo(6.4f, 10.6f)
                            cubicTo(6.4f, 7.2f, 8.9f, 4.8f, 12f, 4.8f)
                            cubicTo(15.1f, 4.8f, 17.6f, 7.2f, 17.6f, 10.6f)
                            lineTo(17.6f, 15.8f)
                            lineTo(19.4f, 17.2f)
                            lineTo(4.6f, 17.2f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                    line(12f, 3.2f, 12f, 4.8f)
                    drawArc(
                        color = soft,
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(9.8f, 17.2f),
                        size = Size(4.4f, 3.4f),
                        style = fine
                    )
                }

                MarbleGlyph.SHARE -> {
                    line(12f, 14.6f, 12f, 3.8f)
                    drawPath(path(8.0f to 7.8f, 12f to 3.8f, 16.0f to 7.8f), tint, style = stroke)
                    drawPath(
                        Path().apply {
                            moveTo(7.4f, 10.6f)
                            lineTo(5.6f, 10.6f)
                            cubicTo(4.7f, 10.6f, 4.2f, 11.2f, 4.2f, 12.1f)
                            lineTo(4.2f, 18.6f)
                            cubicTo(4.2f, 19.5f, 4.7f, 20.1f, 5.6f, 20.1f)
                            lineTo(18.4f, 20.1f)
                            cubicTo(19.3f, 20.1f, 19.8f, 19.5f, 19.8f, 18.6f)
                            lineTo(19.8f, 12.1f)
                            cubicTo(19.8f, 11.2f, 19.3f, 10.6f, 18.4f, 10.6f)
                            lineTo(16.6f, 10.6f)
                        },
                        tint,
                        style = stroke
                    )
                }

                MarbleGlyph.EYE -> {
                    drawPath(
                        Path().apply {
                            moveTo(2.8f, 12f)
                            cubicTo(5.2f, 7.2f, 8.4f, 5.4f, 12f, 5.4f)
                            cubicTo(15.6f, 5.4f, 18.8f, 7.2f, 21.2f, 12f)
                            cubicTo(18.8f, 16.8f, 15.6f, 18.6f, 12f, 18.6f)
                            cubicTo(8.4f, 18.6f, 5.2f, 16.8f, 2.8f, 12f)
                            close()
                        },
                        tint,
                        style = stroke
                    )
                    drawCircle(tint, radius = 2.6f, center = Offset(12f, 12f), style = stroke)
                }

                MarbleGlyph.EYE_OFF -> {
                    drawPath(
                        Path().apply {
                            moveTo(2.8f, 12f)
                            cubicTo(5.2f, 7.2f, 8.4f, 5.4f, 12f, 5.4f)
                            cubicTo(15.6f, 5.4f, 18.8f, 7.2f, 21.2f, 12f)
                            cubicTo(18.8f, 16.8f, 15.6f, 18.6f, 12f, 18.6f)
                            cubicTo(8.4f, 18.6f, 5.2f, 16.8f, 2.8f, 12f)
                            close()
                        },
                        soft,
                        style = fine
                    )
                    line(4.4f, 4.4f, 19.6f, 19.6f)
                }
            }
        }
    }
}

/**
 * Draw [glyph] in [color] inside [modifier]'s bounds on the 24-unit grid.
 */
@Composable
internal fun MarbleGlyphIcon(
    glyph: MarbleGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    mirror: Boolean = false
) {
    Canvas(modifier) {
        drawMarbleGlyph(glyph, color, weight = weight, mirror = mirror)
    }
}
