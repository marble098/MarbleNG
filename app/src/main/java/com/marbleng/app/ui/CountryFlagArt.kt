package com.marbleng.app.ui

// =================================================================================================
// MARBLE_SERVER_LOCATION_V192 — one full-circle national flag per server, drawn offline.
// =================================================================================================
//
// The server rows used to carry a country as a two-code-point emoji on the rim of the protocol
// tile: a font glyph the platform may render as "DE" in a rectangle on some devices, nothing on
// a custom typeface, and never the flag the user meant to see. This library replaces the emoji
// with the flag itself — every location the product knows is drawn with the same Canvas
// primitives the rest of the app uses, clipped to a full circle so the flag IS the tile.
//
// Everything is offline by design (a geolocation image CDN is exactly the network the user is
// trying to fix), resolution-independent, and free of assets: the table below is the single
// source of truth for what a location looks like at 38 dp. Unrecognised codes fall back to the
// neutral code tile, so a brand-new location can never render as the wrong flag.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// -------------------------------------------------------------------------------------------------
// Flag palette.
// -------------------------------------------------------------------------------------------------

private object F {
    const val White = 0xFFFFFFFF
    const val Black = 0xFF000000
    const val Red = 0xFFD80027
    const val DeepRed = 0xFFB22234
    const val Maroon = 0xFF9E1B32
    const val Green = 0xFF6DA544
    const val DeepGreen = 0xFF007A5E
    const val Forest = 0xFF009543
    const val Emerald = 0xFF009B3A
    const val Yellow = 0xFFFFD700
    const val Gold = 0xFFCFB53B
    const val Saffron = 0xFFFF9933
    const val Orange = 0xFFFF8200
    const val Blue = 0xFF0052B4
    const val Navy = 0xFF00247D
    const val Cobalt = 0xFF003893
    const val Sky = 0xFF338FFF
    const val Powder = 0xFF748DBE
    const val Teal = 0xFF006B57
    const val Cyan2 = 0xFF00AEEF
    const val Purple = 0xFF6C3FA0
    const val Grey = 0xFF8C8C8C
    const val Charcoal = 0xFF333333
    const val Brown = 0xFF7A5C3A
    const val Crimson = 0xFFED2939
    const val Ruby = 0xFFC8102E
    const val Turquoise = 0xFF3EB489
    const val Olive = 0xFF4C7C2A
    const val Khaki = 0xFFE7C660
    const val Steel = 0xFF4A6DA7
}

// -------------------------------------------------------------------------------------------------
// Primitives. All fractions are of the FLAG canvas, which is 3:2 (width : height). The
// composition below hands the table a DrawScope whose size is exactly that flag rectangle, so
// a "circle" drawn here is a true circle in flag space and a band is a true fraction of the
// flag's height.
// -------------------------------------------------------------------------------------------------

private fun DrawScope.base(c: Color) {
    drawRect(c, Offset.Zero, Size(size.width, size.height))
}

/** Equal horizontal bands, top to bottom. */
private fun DrawScope.bandsH(cs: List<Color>) {
    val h = size.height / cs.size
    cs.forEachIndexed { i, c -> drawRect(c, Offset(0f, i * h), Size(size.width, h + 0.5f)) }
}

/** Horizontal bands with custom fractions of the height. */
private fun DrawScope.bandsH(fracs: List<Float>, cs: List<Color>) {
    require(fracs.size == cs.size)
    var y = 0f
    fracs.forEachIndexed { i, f ->
        drawRect(cs[i], Offset(0f, y * size.height), Size(size.width, (f + 0.5f) * size.height))
        y += f
    }
}

/** Equal vertical bands, left to right. */
private fun DrawScope.bandsV(cs: List<Color>) {
    val w = size.width / cs.size
    cs.forEachIndexed { i, c -> drawRect(c, Offset(i * w, 0f), Size(w + 0.5f, size.height)) }
}

private fun DrawScope.hBand(y0: Float, y1: Float, c: Color) {
    drawRect(c, Offset(0f, y0 * size.height), Size(size.width, (y1 - y0) * size.height + 0.5f))
}

private fun DrawScope.vBand(x0: Float, x1: Float, c: Color) {
    drawRect(c, Offset(x0 * size.width, 0f), Size((x1 - x0) * size.width + 0.5f, size.height))
}

private fun DrawScope.disc(cx: Float, cy: Float, r: Float, c: Color) {
    drawCircle(c, radius = r * size.width, center = Offset(cx * size.width, cy * size.height))
}

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, thickness: Float, c: Color) {
    drawCircle(
        c,
        radius = r * size.width,
        center = Offset(cx * size.width, cy * size.height),
        style = Stroke((thickness * size.width).coerceAtLeast(1f))
    )
}

private fun starPath(
    cx: Float,
    cy: Float,
    outer: Float,
    inner: Float,
    points: Int,
    rotationDeg: Float
): Path {
    val path = Path()
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians(rotationDeg + i * 180f / points.toDouble()).toFloat()
        val x = cx + r * cos(a)
        val y = cy + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.star(
    cx: Float,
    cy: Float,
    r: Float,
    points: Int = 5,
    inner: Float = .382f,
    c: Color,
    rotation: Float = -90f
) {
    val center = Offset(cx * size.width, cy * size.height)
    val outer = r * size.width
    drawPath(starPath(center.x, center.y, outer, outer * inner, points, rotation), c)
}

/** A crescent opening toward the fly (right). [cutColor] erases the offset disc. */
private fun DrawScope.crescent(
    cx: Float,
    cy: Float,
    r: Float,
    c: Color,
    cutColor: Color,
    cutShift: Float = .28f,
    cutScale: Float = .86f
) {
    val center = Offset(cx * size.width, cy * size.height)
    val rr = r * size.width
    drawCircle(c, radius = rr, center = center)
    drawCircle(cutColor, radius = rr * cutScale, center = Offset(center.x + rr * cutShift, center.y))
}

/** A centred plus cross; [thickness] is a fraction of the flag height. */
private fun DrawScope.plusCross(c: Color, thickness: Float) {
    val t = thickness * size.height
    val half = t / 2f
    drawRect(c, Offset(0f, size.height / 2f - half), Size(size.width, t))
    drawRect(c, Offset(size.width / 2f - half, 0f), Size(t, size.height))
}

/** The Nordic cross: vertical bar shifted to the hoist. */
private fun DrawScope.nordicCross(c: Color, thickness: Float, xCenter: Float = .4f) {
    val t = thickness * size.height
    val half = t / 2f
    drawRect(c, Offset(0f, size.height / 2f - half), Size(size.width, t))
    drawRect(c, Offset(xCenter * size.width - half, 0f), Size(t, size.height))
}

/** A solid triangle from the hoist edge, base on the hoist, apex at [width] of the fly. */
private fun DrawScope.hoistTriangle(c: Color, width: Float) {
    drawPath(
        Path().apply {
            moveTo(0f, 0f)
            lineTo(width * size.width, size.height / 2f)
            lineTo(0f, size.height)
            close()
        },
        c
    )
}

/** Nine saw-teeth of [color] eating into the hoist side (Bahrain / Qatar style). */
private fun DrawScope.serration(c: Color, depth: Float) {
    val n = 9
    val step = size.height / n
    val path = Path()
    for (i in 0 until n) {
        val yTop = i * step
        path.moveTo(0f, yTop)
        path.lineTo(depth * size.width, yTop + step / 2f)
        path.lineTo(0f, yTop + step)
    }
    drawPath(path, c)
}

/** The union-jack diagonals and cross on a [field], covering the whole canvas. */
private fun DrawScope.unionJack(field: Color) {
    base(field)
    val w = size.width
    val h = size.height
    val whiteDiag = h * .3f
    val redDiag = h * .12f
    drawLine(Color(F.White), Offset(0f, 0f), Offset(w, h), whiteDiag, cap = StrokeCap.Butt)
    drawLine(Color(F.White), Offset(w, 0f), Offset(0f, h), whiteDiag, cap = StrokeCap.Butt)
    drawLine(Color(F.DeepRed), Offset(0f, 0f), Offset(w, h), redDiag, cap = StrokeCap.Butt)
    drawLine(Color(F.DeepRed), Offset(w, 0f), Offset(0f, h), redDiag, cap = StrokeCap.Butt)
    val whiteBar = h * .32f
    val redBar = h * .14f
    drawRect(Color(F.White), Offset(0f, h / 2f - whiteBar / 2f), Size(w, whiteBar))
    drawRect(Color(F.White), Offset(w / 2f - whiteBar / 2f, 0f), Size(whiteBar, h))
    drawRect(Color(F.DeepRed), Offset(0f, h / 2f - redBar / 2f), Size(w, redBar))
    drawRect(Color(F.DeepRed), Offset(w / 2f - redBar / 2f, 0f), Size(redBar, h))
}

/** The union jack shrunk to the top-left canton (Commonwealth flags). */
private fun DrawScope.cantonJack() {
    scale(.5f, .5f) { unionJack(Color(F.Cobalt)) }
}

/** The Korean taegeuk: an S of two half-discs, centred at (cx, cy). */
private fun DrawScope.taegeuk(cx: Float, cy: Float, r: Float) {
    val c = Offset(cx * size.width, cy * size.height)
    val rr = r * size.width
    val small = rr / 2f
    drawCircle(Color(F.Crimson), radius = rr, center = c)
    val blue = Color(0xFF0047A0)
    val bluePath = Path().apply {
        arcTo(Rect(c.x - rr, c.y - rr, c.x + rr, c.y + rr), 0f, 180f, forceMoveTo = false)
        arcTo(Rect(c.x, c.y - small, c.x + small * 2f, c.y + small), 0f, 180f, forceMoveTo = false)
        arcTo(Rect(c.x - small * 2f, c.y - small, c.x, c.y + small), 180f, -180f, forceMoveTo = false)
        close()
    }
    drawPath(bluePath, blue)
}

private fun DrawScope.trigram(cx: Float, cy: Float, color: Color, angle: Float) {
    // Three short bars (the four trigrams differ only in which bar is broken — at badge size
    // the three-bar silhouette reads correctly for all four).
    val len = size.width * .1f
    val barH = size.height * .045f
    rotate(degrees = angle, pivot = Offset(cx * size.width, cy * size.height)) {
        drawRect(color, Offset(cx * size.width - len / 2f, cy * size.height - barH * 1.7f), Size(len, barH))
        drawRect(color, Offset(cx * size.width - len / 2f, cy * size.height - barH / 2f), Size(len, barH))
        drawRect(color, Offset(cx * size.width - len / 2f, cy * size.height + barH * .3f), Size(len, barH))
    }
}

// -------------------------------------------------------------------------------------------------
// The table.
// -------------------------------------------------------------------------------------------------

/**
 * Draws the flag for an ISO 3166-1 alpha-2 code into the current canvas. The canvas must be the
 * 3:2 flag rectangle (see [CountryFlagCircle]); the flag fills it exactly.
 */
fun DrawScope.drawCountryFlag(code: String) {
    val w = Color(F.White)
    val k = Color(F.Black)
    val r = Color(F.Red)
    val g = Color(F.Forest)
    val y = Color(F.Saffron)
    val b = Color(F.Cobalt)
    when (code.trim().uppercase()) {
        // — Africa —
        "DZ" -> { bandsV(listOf(Color(F.Forest), w)); crescent(.48f, .5f, .2f, Color(F.DeepRed), w, .3f); star(.6f, .5f, .065f, 5, .45f, Color(F.DeepRed)) }
        "BD" -> { base(Color(F.Green)); disc(.44f, .5f, .23f, Color(F.Crimson)) }
        "BJ" -> { bandsV(listOf(Color(F.Olive), w, Color(F.Olive))); vBand(0f, .44f, Color(F.Olive)); star(.22f, .5f, .11f, 5, .45f, Color(F.Red)) }
        "BF" -> { bandsH(listOf(Color(F.Crimson), Color(F.Saffron))); disc(.5f, .5f, .13f, g) }
        "BI" -> {
            base(w)
            drawRect(Color(F.Crimson), Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
            drawRect(Color(F.Crimson), Offset(0f, size.height / 2f), Size(size.width / 2f, size.height / 2f))
            drawLine(Color(F.Saffron), Offset(0f, 0f), Offset(size.width, size.height), size.height * .16f)
            drawLine(Color(F.Saffron), Offset(size.width, 0f), Offset(0f, size.height), size.height * .16f)
            disc(.5f, .5f, .17f, Color(F.Saffron))
        }
        "BW" -> { bandsH(listOf(Color(F.Sky), k, w)); hBand(.4f, .44f, w); hBand(.56f, .6f, w); hBand(.44f, .56f, k) }
        "CM" -> { bandsV(listOf(g, Color(F.Saffron), Color(F.Crimson))); star(.5f, .5f, .13f, 5, .45f, g) }
        "CI" -> { bandsV(listOf(Color(F.Orange), w, g)) }
        "CV" -> { base(w); hBand(.22f, .68f, Color(F.Cyan2)); vBand(.66f, 1f, Color(F.Crimson)); hBand(0f, .22f, Color(F.Cyan2)); disc(.33f, .36f, .13f, Color(F.Saffron)); star(.33f, .36f, .09f, 5, .45f, k) }
        "EG" -> { bandsH(listOf(r, w, k)); disc(.5f, .5f, .12f, Color(F.Gold)) }
        "ET" -> { bandsH(listOf(g, Color(F.Saffron), Color(F.Crimson))); disc(.5f, .5f, .19f, Color(F.Navy)); disc(.5f, .5f, .08f, Color(F.Gold)) }
        "GH" -> { bandsH(listOf(Color(F.Crimson), Color(F.Saffron), g)); star(.5f, .5f, .15f, 5, .45f, k) }
        "GN" -> { bandsV(listOf(Color(F.Saffron), Color(F.Cyan2), Color(F.Crimson))) }
        "KE" -> { bandsH(listOf(k, Color(F.Crimson), g)); hBand(.43f, .455f, w); hBand(.545f, .57f, w); disc(.5f, .5f, .15f, w); disc(.5f, .5f, .1f, Color(F.Crimson)) }
        "LY" -> { bandsH(listOf(Color(F.Crimson), k, g)); crescent(.42f, .5f, .13f, w, k, .3f); star(.54f, .5f, .065f, 5, .45f, w) }
        "MA" -> {
            base(Color(F.Crimson))
            // The green pentagram is an interlaced outline star.
            drawPath(starPath(size.width / 2f, size.height / 2f, .3f * size.width, .382f * .3f * size.width, 5, -90f), Color(F.Teal), style = Stroke(size.width * .05f))
        }
        "MG" -> { base(w); vBand(.5f, 1f, Color(F.Crimson)); hBand(.5f, 1f, g) }
        "MU" -> { bandsH(listOf(Color(F.Crimson), Color(F.Saffron), Color(F.Cyan2), g)) }
        "MW" -> { bandsH(listOf(k, g, Color(F.Crimson))); disc(.28f, .3f, .11f, Color(F.Saffron)) }
        "NG" -> { bandsV(listOf(g, w, g)) }
        "NE" -> { bandsV(listOf(Color(F.Orange), w, g)); star(.5f, .5f, .1f, 5, .45f, Color(F.Orange)) }
        "RW" -> { bandsH(listOf(Color(F.Cyan2), Color(F.Saffron), w)); disc(.72f, .27f, .11f, Color(F.Saffron)); star(.72f, .27f, .08f, 5, .45f, Color(F.Cyan2)) }
        "SN" -> { bandsV(listOf(g, Color(F.Saffron), Color(F.Crimson))); star(.5f, .5f, .12f, 5, .45f, g) }
        "SD" -> { bandsH(listOf(Color(F.Crimson), w, k)); hoistTriangle(g, .5f) }
        // MARBLE_SERVER_LOCATION_V192 — red field, white disc, red crescent opening to the
        // fly and the red five-point star in its middle.
        "TN" -> {
            base(Color(F.Crimson))
            disc(.5f, .5f, .13f, w)
            crescent(.5f, .5f, .085f, Color(F.Crimson), w)
            star(.54f, .5f, .045f, 5, .45f, Color(F.Crimson))
        }
        "TZ" -> {
            base(Color(F.Cyan2))
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close() }, g)
            drawLine(Color(F.Saffron), Offset(-size.width * .1f, size.height * 1.1f), Offset(size.width * 1.1f, -size.height * .1f), size.height * .44f)
            drawLine(k, Offset(-size.width * .1f, size.height * 1.1f), Offset(size.width * 1.1f, -size.height * .1f), size.height * .14f)
        }
        "UG" -> { bandsH(listOf(k, Color(F.Saffron), Color(F.Crimson), k, Color(F.Saffron), Color(F.Crimson))); disc(.5f, .5f, .15f, w); disc(.5f, .5f, .1f, Color(F.Charcoal)) }
        "ZA" -> {
            base(Color(F.Cyan2))
            drawRect(Color(F.Crimson), Offset(0f, 0f), Size(size.width, size.height * .12f))
            drawRect(Color(F.Crimson), Offset(0f, size.height * .88f), Size(size.width, size.height * .12f))
            val bandH = size.height * .3f
            drawRect(w, Offset(0f, size.height / 2f - bandH * .62f), Size(size.width, bandH * 1.24f))
            drawRect(Color(F.Forest), Offset(0f, size.height / 2f - bandH / 2f), Size(size.width, bandH))
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width * .55f, size.height / 2f - bandH * .62f); lineTo(0f, size.height / 2f - bandH * .62f); close() }, w)
            drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width * .55f, size.height / 2f + bandH * .62f); lineTo(0f, size.height / 2f + bandH * .62f); close() }, w)
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width * .42f, size.height / 2f - bandH / 2f); lineTo(0f, size.height / 2f - bandH / 2f); close() }, Color(F.Forest))
            drawPath(Path().apply { moveTo(0f, size.height); lineTo(size.width * .42f, size.height / 2f + bandH / 2f); lineTo(0f, size.height / 2f + bandH / 2f); close() }, Color(F.Forest))
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width * .2f, size.height / 2f); lineTo(0f, size.height); close() }, k)
        }
        "ZM" -> { base(g); hBand(.64f, .73f, Color(F.Crimson)); hBand(.75f, .84f, w); hBand(.86f, .95f, k) }
        "ZW" -> { bandsH(listOf(g, Color(F.Saffron), w, Color(F.Crimson), w, Color(F.Saffron), g)); hoistTriangle(Color(F.Crimson), .5f); star(.17f, .5f, .08f, 5, .45f, w) }

        // — Americas —
        "AR" -> {
            bandsH(listOf(Color(F.Powder), w, Color(F.Powder)))
            disc(.5f, .5f, .09f, Color(F.Saffron))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45f.toDouble()).toFloat()
                drawLine(Color(F.Saffron), Offset(size.width / 2f, size.height / 2f), Offset(size.width / 2f + cos(a) * .16f * size.width, size.height / 2f + sin(a) * .16f * size.height), size.width * .018f)
            }
        }
        "BO" -> { bandsH(listOf(Color(F.Crimson), Color(F.Saffron), g)) }
        "BR" -> {
            base(g)
            drawPath(Path().apply { moveTo(size.width * .07f, size.height / 2f); lineTo(size.width / 2f, size.height * .13f); lineTo(size.width * .93f, size.height / 2f); lineTo(size.width / 2f, size.height * .87f); close() }, Color(F.Saffron))
            disc(.5f, .5f, .19f, Color(F.Navy))
        }
        "CA" -> {
            bandsV(listOf(Color(F.Crimson), w, Color(F.Crimson)))
            vBand(0f, .333f, Color(F.Crimson))
            vBand(.667f, 1f, Color(F.Crimson))
            // A nine-point maple leaf, simplified.
            val leaf = Path().apply {
                val cx = size.width * .5f
                val top = size.height * .18f
                val bottom = size.height * .82f
                moveTo(cx, top)
                lineTo(cx + .06f * size.width, size.height * .3f)
                lineTo(cx + .16f * size.width, size.height * .26f)
                lineTo(cx + .14f * size.width, size.height * .46f)
                lineTo(cx + .24f * size.width, size.height * .52f)
                lineTo(cx + .14f * size.width, size.height * .62f)
                lineTo(cx + .12f * size.width, size.height * .8f)
                lineTo(cx + .04f * size.width, size.height * .74f)
                lineTo(cx, bottom)
                lineTo(cx - .04f * size.width, size.height * .74f)
                lineTo(cx - .12f * size.width, size.height * .8f)
                lineTo(cx - .14f * size.width, size.height * .62f)
                lineTo(cx - .24f * size.width, size.height * .52f)
                lineTo(cx - .14f * size.width, size.height * .46f)
                lineTo(cx - .16f * size.width, size.height * .26f)
                lineTo(cx - .06f * size.width, size.height * .3f)
                close()
            }
            drawPath(leaf, Color(F.Crimson))
        }
        "CL" -> {
            base(w)
            vBand(.5f, 1f, Color(F.Cyan2))
            hBand(.5f, 1f, Color(F.Cyan2))
            drawRect(Color(F.Crimson), Offset(0f, 0f), Size(size.width * .5f, size.height * .5f))
            star(.25f, .25f, .17f, 5, .45f, w)
        }
        "CO" -> { bandsH(fracs = listOf(.5f, .25f, .25f), listOf(Color(F.Saffron), Color(F.Navy), Color(F.Crimson))) }
        "CR" -> { bandsH(listOf(Color(F.Cobalt), w, Color(F.Cobalt))) }
        "CU" -> { bandsH(listOf(Color(F.Crimson), w, Color(F.Crimson), w, Color(F.Crimson))); hoistTriangle(w, .55f); star(.19f, .5f, .1f, 5, .45f, Color(F.Crimson)) }
        "DO" -> {
            base(w)
            plusCross(Color(F.Cobalt), .34f)
            plusCross(Color(F.Cobalt), .12f)
            star(.25f, .25f, .07f, 5, .45f, k)
            star(.75f, .25f, .07f, 5, .45f, k)
            star(.25f, .75f, .07f, 5, .45f, k)
            star(.75f, .75f, .07f, 5, .45f, k)
        }
        "EC" -> { bandsH(fracs = listOf(.5f, .25f, .25f), listOf(Color(F.Saffron), Color(F.Cobalt), Color(F.Crimson))) }
        "GT" -> { bandsV(listOf(Color(F.Powder), w, Color(F.Powder))) }
        "HN" -> { bandsH(listOf(Color(F.Cobalt), w, Color(F.Cobalt))) }
        "MX" -> { bandsV(listOf(g, w, Color(F.Crimson))); disc(.5f, .5f, .1f, Color(F.Brown)) }
        "NI" -> { bandsH(listOf(Color(F.Cobalt), w, Color(F.Cobalt))) }
        "PA" -> {
            base(w)
            vBand(.5f, 1f, Color(F.Crimson))
            hBand(.5f, 1f, Color(F.Cyan2))
            vBand(.5f, 1f, Color(F.Crimson))
            hBand(.5f, 1f, Color(F.Cyan2))
            star(.25f, .25f, .1f, 5, .45f, Color(F.Cobalt))
            star(.75f, .75f, .1f, 5, .45f, Color(F.Crimson))
        }
        "PE" -> { bandsV(listOf(Color(F.Crimson), w, Color(F.Crimson))) }
        "PY" -> { bandsH(listOf(Color(F.Crimson), w, Color(F.Cyan2))) }
        "UY" -> {
            bandsH(listOf(Color(F.Cobalt), w, Color(F.Cobalt), w, Color(F.Cobalt), w, Color(F.Cobalt), w, Color(F.Cobalt)))
            disc(.27f, .27f, .12f, Color(F.Saffron))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45f.toDouble()).toFloat()
                drawLine(Color(F.Saffron), Offset(size.width * .27f, size.height * .27f), Offset(size.width * (.27f + cos(a) * .17f), size.height * (.27f + sin(a) * .17f)), size.width * .015f)
            }
        }
        "US" -> {
            base(w)
            for (i in 0 until 13 step 2) drawRect(Color(F.DeepRed), Offset(0f, i * size.height / 13f), Size(size.width, size.height / 13f))
            drawRect(Color(F.Cobalt), Offset(0f, 0f), Size(size.width * .42f, size.height * 8f / 13f))
            val rows = listOf(.14f, .28f, .42f, .56f, .7f)
            for (ri in rows.indices) {
                val cols = if (ri % 2 == 0) listOf(.08f, .18f, .28f, .38f) else listOf(.13f, .23f, .33f)
                for (cx in cols) disc(cx, rows[ri], .02f, w)
            }
        }
        "VE" -> {
            bandsH(listOf(Color(F.Saffron), Color(F.Cobalt), Color(F.Crimson)))
            for (i in 0 until 8) {
                val a = Math.toRadians(200f + i * 160f / 7f.toDouble()).toFloat()
                disc(.5f + cos(a) * .17f, .34f + sin(a) * .09f, .016f, w)
            }
        }

        // — Asia —
        "AE" -> { bandsH(listOf(g, w, k)); vBand(0f, .25f, Color(F.Crimson)) }
        "AF" -> { bandsV(listOf(k, Color(F.Crimson), g)) }
        "AZ" -> { bandsH(listOf(Color(F.Cyan2), Color(F.Crimson), g)); crescent(.42f, .5f, .11f, w, Color(F.Crimson), .3f); star(.56f, .5f, .07f, 8, .45f, w) }
        "BH" -> { base(w); serration(Color(F.Crimson), .34f) }
        "BN" -> {
            base(Color(F.Saffron))
            drawLine(k, Offset(0f, 0f), Offset(size.width, size.height), size.height * .2f)
            drawLine(k, Offset(size.width, 0f), Offset(0f, size.height), size.height * .2f)
            drawLine(Color(F.Crimson), Offset(0f, 0f), Offset(size.width, size.height), size.height * .1f)
            drawLine(Color(F.Crimson), Offset(size.width, 0f), Offset(0f, size.height), size.height * .1f)
            star(.35f, .38f, .12f, 9, .5f, w)
            disc(.62f, .62f, .09f, w)
        }
        "BT" -> { bandsH(listOf(Color(F.Saffron), Color(F.Orange))); disc(.5f, .5f, .13f, Color(F.Grey)) }
        "CN" -> {
            base(Color(F.Crimson))
            star(.18f, .2f, .14f, 5, .45f, Color(F.Saffron))
            // Four small stars, each pointed at the big one.
            for (pair in listOf(.36f to .1f, .42f to .22f, .42f to .34f, .36f to .46f)) {
                val (cx, cy) = pair
                val angle = (Math.toDegrees(Math.atan2((.2 - cy).toDouble(), (.18 - cx).toDouble()).toDouble()).toFloat())
                star(cx, cy, .045f, 5, .45f, Color(F.Saffron), rotation = angle)
            }
        }
        "HK" -> {
            base(Color(F.Cyan2))
            for (i in 0 until 5) {
                val a = Math.toRadians(-90f + i * 72f.toDouble()).toFloat()
                val px = size.width / 2f + cos(a) * .14f * size.width
                val py = size.height / 2f + sin(a) * .14f * size.height
                drawPath(starPath(px, py, .11f * size.width, .045f * size.width, 5, -90f + i * 72f + 36f), Color(F.Crimson))
            }
        }
        "ID" -> { bandsH(listOf(Color(F.Crimson), w)) }
        "IL" -> {
            base(w)
            hBand(.14f, .26f, Color(F.Cobalt))
            hBand(.74f, .86f, Color(F.Cobalt))
            drawPath(starPath(size.width / 2f, size.height * .4f, .13f * size.width, .055f * size.width, 3, 0f), Color(F.Cobalt), style = Stroke(size.width * .028f))
            drawPath(starPath(size.width / 2f, size.height * .6f, .13f * size.width, .055f * size.width, 3, 180f), Color(F.Cobalt), style = Stroke(size.width * .028f))
        }
        "IN" -> {
            bandsH(listOf(Color(F.Saffron), w, g))
            ring(.5f, .5f, .16f, .028f, Color(F.Navy))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30f.toDouble()).toFloat()
                drawLine(Color(F.Navy), Offset(size.width / 2f + cos(a) * .1f * size.width, size.height / 2f + sin(a) * .1f * size.height), Offset(size.width / 2f + cos(a) * .16f * size.width, size.height / 2f + sin(a) * .16f * size.height), size.width * .013f)
            }
        }
        "IQ" -> { bandsH(listOf(Color(F.Crimson), w, k)); for (i in 0 until 3) drawRect(g, Offset(size.width * (.38f + i * .09f), size.height * .46f), Size(size.width * .05f, size.height * .08f)) }
        "IR" -> {
            bandsH(fracs = listOf(.32f, .36f, .32f), listOf(g, w, Color(F.Crimson)))
            hBand(.32f, .34f, Color(F.Saffron))
            hBand(.66f, .68f, Color(F.Saffron))
            disc(.5f, .5f, .12f, Color(F.Crimson))
        }
        "JO" -> { bandsH(listOf(k, w, g)); hoistTriangle(Color(F.Crimson), .55f); star(.19f, .5f, .08f, 7, .5f, w) }
        "JP" -> { base(w); disc(.5f, .5f, .3f, Color(F.Crimson)) }
        "KG" -> {
            base(Color(F.Crimson))
            disc(.5f, .5f, .2f, Color(F.Saffron))
            disc(.5f, .5f, .1f, Color(F.Crimson))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45f.toDouble()).toFloat()
                val p1 = Offset(size.width / 2f + cos(a) * .12f * size.width, size.height / 2f + sin(a) * .12f * size.height)
                val p2 = Offset(size.width / 2f + cos(a - .22f) * .18f * size.width, size.height / 2f + sin(a - .22f) * .18f * size.height)
                val p3 = Offset(size.width / 2f + cos(a + .22f) * .18f * size.width, size.height / 2f + sin(a + .22f) * .18f * size.height)
                drawPath(Path().apply { moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() }, Color(F.Crimson))
            }
        }
        "KZ" -> {
            base(Color(F.Cyan2))
            disc(.5f, .45f, .14f, Color(F.Saffron))
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30f.toDouble()).toFloat()
                drawLine(Color(F.Saffron), Offset(size.width / 2f, size.height * .45f), Offset(size.width / 2f + cos(a) * .2f * size.width, size.height * .45f + sin(a) * .2f * size.height), size.width * .015f)
            }
            vBand(.08f, .12f, Color(F.Saffron))
        }
        "KH" -> {
            bandsH(fracs = listOf(.25f, .5f, .25f), listOf(Color(F.Cyan2), Color(F.Crimson), Color(F.Cyan2)))
            // Angkor Wat: a row of five towers.
            val baseY = size.height * .62f
            val cx = size.width / 2f
            drawRect(w, Offset(cx - .26f * size.width, baseY), Size(.52f * size.width, size.height * .08f))
            for (i in -2..2) {
                val tx = cx + i * .1f * size.width
                val th = if (i == 0) .2f else .14f
                drawPath(Path().apply { moveTo(tx - .03f * size.width, baseY); lineTo(tx, baseY - th * size.height); lineTo(tx + .03f * size.width, baseY); close() }, w)
            }
        }
        "KR" -> {
            base(w)
            taegeuk(.5f, .5f, .28f)
            trigram(.16f, .16f, k, 0f)
            trigram(.84f, .16f, k, 90f)
            trigram(.16f, .84f, k, -90f)
            trigram(.84f, .84f, k, 180f)
        }
        "LA" -> { bandsH(fracs = listOf(.25f, .5f, .25f), listOf(Color(F.Crimson), Color(F.Cobalt), Color(F.Crimson))); disc(.5f, .5f, .13f, w) }
        "LK" -> {
            base(w)
            vBand(0f, .4f, w)
            drawRect(Color(F.Saffron), Offset(0f, 0f), Size(size.width * .2f, size.height))
            drawRect(g, Offset(size.width * .2f, 0f), Size(size.width * .2f, size.height))
            drawRect(Color(F.Purple), Offset(size.width * .4f, 0f), Size(size.width * .6f, size.height))
            drawRect(Color(F.Gold), Offset(size.width * .55f, size.height * .28f), Size(size.width * .28f, size.height * .44f))
        }
        "MM" -> { bandsH(listOf(Color(F.Saffron), g, Color(F.Crimson))); star(.5f, .5f, .17f, 5, .45f, w) }
        "MN" -> {
            bandsV(listOf(Color(F.Crimson), Color(F.Cyan2), Color(F.Crimson)))
            disc(.19f, .34f, .07f, Color(F.Saffron))
            drawRect(Color(F.Saffron), Offset(size.width * .14f, size.height * .47f), Size(size.width * .1f, size.height * .18f))
        }
        "MY" -> {
            base(w)
            for (i in 0 until 14 step 2) drawRect(Color(F.Crimson), Offset(0f, i * size.height / 14f), Size(size.width, size.height / 14f))
            drawRect(Color(F.Cobalt), Offset(0f, 0f), Size(size.width * .5f, size.height * .5f))
            crescent(.3f, .25f, .15f, Color(F.Saffron), Color(F.Cobalt), .3f)
            star(.43f, .25f, .09f, 14, .45f, Color(F.Saffron))
        }
        "NP" -> {
            base(k)
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width * .95f, size.height * .5f); lineTo(size.width * .95f, size.height); lineTo(0f, size.height); close() }, Color(F.Crimson))
            drawPath(Path().apply { moveTo(size.width * .1f, 0f); lineTo(size.width * .85f, size.height * .52f); lineTo(size.width * .85f, size.height * .9f); lineTo(size.width * .1f, size.height * .9f); close() }, w)
            crescent(.32f, .2f, .09f, Color(F.Cobalt), w, .25f)
            star(.6f, .7f, .08f, 5, .45f, Color(F.Cobalt))
        }
        "OM" -> { bandsH(listOf(w, Color(F.Crimson), g)); vBand(0f, .3f, Color(F.Crimson)); drawRect(w, Offset(size.width * .08f, size.height * .44f), Size(size.width * .14f, size.height * .12f)) }
        "PH" -> {
            base(w)
            hBand(0f, .5f, Color(F.Cobalt))
            hBand(.5f, 1f, Color(F.Crimson))
            hoistTriangle(w, .5f)
            star(.18f, .5f, .09f, 8, .45f, Color(F.Saffron))
            star(.07f, .25f, .035f, 5, .45f, Color(F.Saffron))
            star(.07f, .5f, .035f, 5, .45f, Color(F.Saffron))
            star(.07f, .75f, .035f, 5, .45f, Color(F.Saffron))
        }
        "PK" -> {
            base(g)
            vBand(0f, .25f, w)
            crescent(.62f, .5f, .18f, w, g, .3f)
            star(.75f, .38f, .06f, 5, .45f, w)
        }
        "QA" -> { base(Color(F.Maroon)); serration(w, .34f) }
        "SA" -> {
            base(g)
            drawRect(w, Offset(0f, size.height * .26f), Size(size.width, size.height * .09f))
            drawLine(w, Offset(size.width * .58f, size.height * .42f), Offset(size.width * .95f, size.height * .56f), size.height * .05f)
        }
        "SG" -> {
            bandsH(listOf(Color(F.Crimson), w))
            crescent(.22f, .3f, .13f, w, Color(F.Crimson), .32f)
            for (i in 0 until 5) star(.36f + (i % 3) * .045f, .24f + (i / 3) * .06f, .025f, 5, .45f, w)
        }
        "TH" -> { bandsH(fracs = listOf(.15f, .15f, .4f, .15f, .15f), listOf(Color(F.Crimson), w, Color(F.Navy), w, Color(F.Crimson))) }
        "TR" -> {
            base(Color(F.Crimson))
            crescent(.4f, .5f, .2f, w, Color(F.Crimson), .3f)
            star(.56f, .5f, .09f, 5, .45f, w)
        }
        "TW" -> {
            base(Color(F.Crimson))
            drawRect(Color(F.Navy), Offset(0f, 0f), Size(size.width * .5f, size.height * .5f))
            disc(.25f, .25f, .12f, w)
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30f.toDouble()).toFloat()
                drawLine(w, Offset(size.width * .25f + cos(a) * .07f * size.width, size.height * .25f + sin(a) * .07f * size.height), Offset(size.width * .25f + cos(a) * .12f * size.width, size.height * .25f + sin(a) * .12f * size.height), size.width * .018f)
            }
        }
        "UZ" -> {
            bandsH(listOf(Color(F.Cyan2), w, g))
            hBand(.31f, .33f, Color(F.Crimson))
            hBand(.67f, .69f, Color(F.Crimson))
            crescent(.15f, .25f, .09f, w, Color(F.Cyan2), .3f)
            for (i in 0 until 3) for (j in 0 until 3) star(.24f + j * .05f, .12f + i * .05f, .015f, 5, .5f, w)
        }
        "VN" -> { base(Color(F.Crimson)); star(.5f, .5f, .22f, 5, .45f, Color(F.Saffron)) }
        "YE" -> { bandsH(listOf(Color(F.Crimson), w, k)) }
        "SY" -> { bandsH(listOf(Color(F.Crimson), w, k)); star(.44f, .5f, .09f, 5, .45f, g); star(.56f, .5f, .09f, 5, .45f, g) }

        // — Europe —
        "AD" -> { bandsV(listOf(Color(F.Cobalt), w, Color(F.Crimson))); disc(.5f, .5f, .13f, Color(F.Saffron)) }
        "AL" -> { base(Color(F.Crimson)); star(.5f, .5f, .3f, 5, .45f, k) }
        "AM" -> {
            bandsH(listOf(Color(F.Cobalt), Color(F.Crimson), Color(F.Saffron)))
            drawRect(Color(F.Crimson), Offset(size.width * .1f, size.height * .22f), Size(size.width * .24f, size.height * .12f))
            drawRect(Color(F.Crimson), Offset(size.width * .16f, size.height * .22f), Size(size.width * .12f, size.height * .46f))
        }
        "AT" -> { bandsH(fracs = listOf(.34f, .32f, .34f), listOf(Color(F.Crimson), w, Color(F.Crimson))) }
        "BA" -> {
            base(Color(F.Cobalt))
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(0f, size.height); lineTo(size.width, 0f); close() }, Color(F.Saffron))
            for (i in 0 until 7) star(.14f + i * .055f, .14f + i * .055f, .018f, 5, .5f, w)
        }
        "BE" -> { bandsV(listOf(k, Color(F.Saffron), Color(F.Crimson))) }
        "BG" -> { bandsH(listOf(w, g, Color(F.Crimson))) }
        "BY" -> {
            bandsH(fracs = listOf(.6f, .4f), listOf(g, Color(F.Crimson)))
            vBand(0f, .09f, w)
            for (i in 0 until 6) drawRect(Color(F.Crimson), Offset(size.width * .02f, (i * 2f / 6f + .1f) * size.height), Size(size.width * .05f, size.height * .06f))
        }
        "CH" -> { base(Color(F.Crimson)); plusCross(w, .3f) }
        "CY" -> {
            base(w)
            hBand(.5f, .76f, Color(F.Teal))
            disc(.5f, .36f, .13f, Color(F.Maroon))
            drawRect(Color(F.Maroon), Offset(size.width * .47f, size.height * .28f), Size(size.width * .06f, size.height * .2f))
            drawRect(Color(F.Maroon), Offset(size.width * .42f, size.height * .36f), Size(size.width * .16f, size.height * .05f))
        }
        "CZ" -> { bandsH(listOf(w, Color(F.Cobalt))); hoistTriangle(Color(F.Cobalt), .5f) }
        "DE" -> { bandsH(listOf(k, Color(F.Crimson), Color(F.Saffron))) }
        "DK" -> { base(Color(F.Crimson)); nordicCross(w, .18f, .42f) }
        "EE" -> { bandsH(listOf(Color(F.Cobalt), k, w)) }
        "ES" -> { bandsH(fracs = listOf(.25f, .5f, .25f), listOf(Color(F.Crimson), Color(F.Saffron), Color(F.Crimson))) }
        "FI" -> { base(w); nordicCross(Color(F.Cobalt), .16f) }
        "FR" -> { bandsV(listOf(Color(F.Cobalt), w, Color(F.Crimson))) }
        // MARBLE_SERVER_LOCATION_V192 — the Union Jack: the cobalt field carries the white and
        // red saltires plus St George's cross; the same primitive serves the canton of AU/NZ.
        "GB" -> unionJack(Color(F.Cobalt))
        "GE" -> {
            base(w)
            plusCross(Color(F.Crimson), .3f)
            disc(.25f, .25f, .07f, Color(F.Crimson))
            disc(.75f, .25f, .07f, Color(F.Crimson))
            disc(.25f, .75f, .07f, Color(F.Crimson))
            disc(.75f, .75f, .07f, Color(F.Crimson))
        }
        "GR" -> {
            base(Color(F.Cobalt))
            for (i in 1 until 9 step 2) drawRect(w, Offset(0f, i * size.height / 9f), Size(size.width, size.height / 9f))
            val cw = size.width * 5f / 9f
            val ch = size.height * 5f / 9f
            drawRect(Color(F.Cobalt), Offset(0f, 0f), Size(cw, ch))
            drawRect(w, Offset(cw / 2f, 0f), Size(cw / 4f, ch))
            drawRect(w, Offset(0f, ch / 2f), Size(cw, ch / 4f))
        }
        "HR" -> { bandsH(listOf(Color(F.Crimson), w, Color(F.Cobalt))); disc(.5f, .5f, .09f, Color(F.Saffron)) }
        "HU" -> { bandsH(listOf(Color(F.Crimson), w, g)) }
        "IE" -> { bandsV(listOf(g, w, Color(F.Orange))) }
        "IS" -> { base(Color(F.Cobalt)); nordicCross(w, .16f); nordicCross(Color(F.Crimson), .08f) }
        "IT" -> { bandsV(listOf(g, w, Color(F.Crimson))) }
        "LI" -> {
            bandsH(listOf(Color(F.Cobalt), Color(F.Crimson)))
            drawRect(w, Offset(0f, 0f), Size(size.width * .42f, size.height * .5f))
            drawRect(Color(F.Saffron), Offset(size.width * .15f, size.height * .28f), Size(size.width * .12f, size.height * .12f))
        }
        "LT" -> { bandsH(listOf(Color(F.Saffron), g, Color(F.Crimson))) }
        "LU" -> { bandsH(listOf(Color(F.Crimson), w, Color(F.Powder))) }
        "LV" -> { bandsH(fracs = listOf(.25f, .5f, .25f), listOf(Color(F.Maroon), w, Color(F.Maroon))) }
        "MC" -> { bandsH(listOf(w, Color(F.Crimson))) }
        "MD" -> { bandsV(listOf(Color(F.Cobalt), Color(F.Saffron), Color(F.Crimson))) }
        "ME" -> { base(Color(F.Crimson)); ring(.5f, .5f, .4f, .08f, Color(F.Saffron)); disc(.5f, .5f, .12f, Color(F.Saffron)) }
        "MK" -> {
            base(Color(F.Saffron))
            disc(.5f, .5f, .16f, Color(F.Crimson))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45f.toDouble()).toFloat()
                drawLine(Color(F.Crimson), Offset(size.width / 2f + cos(a) * .2f * size.width, size.height / 2f + sin(a) * .2f * size.height), Offset(size.width / 2f + cos(a) * .42f * size.width, size.height / 2f + sin(a) * .42f * size.height), size.width * .045f)
            }
        }
        "MT" -> { bandsV(listOf(w, Color(F.Crimson))); drawRect(Color(F.Grey), Offset(0f, 0f), Size(size.width * .3f, size.height * .3f)) }
        "NL" -> { bandsH(listOf(Color(F.Crimson), w, Color(F.Cobalt))) }
        "NO" -> { base(Color(F.Crimson)); nordicCross(w, .2f, .4f); nordicCross(Color(F.Cobalt), .12f, .4f) }
        "PL" -> { bandsH(listOf(w, Color(F.Crimson))) }
        "PT" -> {
            vBand(0f, .4f, g)
            vBand(.4f, 1f, Color(F.Crimson))
            disc(.4f, .5f, .14f, Color(F.Saffron))
            ring(.4f, .5f, .1f, .03f, w)
        }
        "RO" -> { bandsV(listOf(Color(F.Cobalt), Color(F.Saffron), Color(F.Crimson))) }
        "RS" -> { bandsH(listOf(Color(F.Cobalt), w, Color(F.Crimson))); disc(.5f, .5f, .09f, Color(F.Saffron)) }
        "RU" -> { bandsH(listOf(w, Color(F.Cobalt), Color(F.Crimson))) }
        "SE" -> { base(Color(F.Cobalt)); nordicCross(Color(F.Saffron), .16f) }
        "SI" -> { bandsH(listOf(w, Color(F.Crimson), Color(F.Cobalt))) }
        "SK" -> { bandsH(listOf(w, Color(F.Cobalt), Color(F.Crimson))); disc(.25f, .5f, .09f, w) }
        "SM" -> { bandsH(listOf(w, Color(F.Saffron))) }
        "UA" -> { bandsH(listOf(Color(F.Cobalt), Color(F.Saffron))) }

        // — Oceania —
        "AU" -> {
            base(Color(F.Cobalt))
            cantonJack()
            star(.25f, .25f, .09f, 7, .45f, w)
            star(.75f, .2f, .07f, 7, .45f, w)
            star(.75f, .8f, .07f, 7, .45f, w)
            star(.5f, .14f, .045f, 5, .45f, w)
            star(.5f, .86f, .055f, 7, .45f, w)
        }
        "FJ" -> { base(Color(F.Cyan2)); cantonJack(); disc(.75f, .5f, .11f, Color(F.Gold)); ring(.75f, .5f, .11f, .03f, Color(F.Cobalt)) }
        "MV" -> {
            base(Color(F.Crimson))
            drawRect(g, Offset(size.width * .09f, size.height * .16f), Size(size.width * .82f, size.height * .68f))
            crescent(.46f, .5f, .16f, Color(F.Crimson), g, .3f)
        }
        "NZ" -> {
            base(Color(F.Cobalt))
            cantonJack()
            for ((cx, cy, s) in listOf(
                Triple(.78f, .2f, .055f), Triple(.68f, .45f, .045f),
                Triple(.78f, .7f, .055f), Triple(.88f, .45f, .045f)
            )) {
                star(cx, cy, s + .018f, 5, .45f, w)
                star(cx, cy, s, 5, .45f, Color(F.Crimson))
            }
        }

        // — Middle East / Central Asia / other —
        "KW" -> { bandsH(listOf(k, w, g)); hoistTriangle(Color(F.Crimson), .45f) }
        "LB" -> {
            bandsH(fracs = listOf(.25f, .5f, .25f), listOf(Color(F.Crimson), w, Color(F.Crimson)))
            drawPath(Path().apply { moveTo(size.width * .5f, size.height * .28f); lineTo(size.width * .62f, size.height * .7f); lineTo(size.width * .38f, size.height * .7f); close() }, g)
        }
        "PS" -> { bandsH(listOf(k, w, g)); hoistTriangle(Color(F.Crimson), .55f) }
        "TJ" -> {
            bandsH(listOf(Color(F.Crimson), w, g))
            hBand(.42f, .58f, w)
            hBand(.44f, .56f, Color(F.Crimson))
            disc(.5f, .2f, .05f, Color(F.Saffron))
            for (i in 0 until 7) star(.32f + i * .045f, .3f, .016f, 5, .5f, Color(F.Saffron))
        }
        "TM" -> {
            base(Color(F.Crimson))
            vBand(0f, .18f, g)
            vBand(.18f, .26f, w)
            for (i in 0 until 4) drawRect(g, Offset(size.width * .2f, (0.22f + i * 0.15f) * size.height), Size(size.width * .02f, size.height * .08f))
        }

        else -> Unit
    }
}

/** True when the art library can draw [code] as a real flag. */
fun CountryFlagSupported(code: String?): Boolean =
    (code ?: "").trim().uppercase().let { c -> c.length == 2 && c.all { it in 'A'..'Z' } && KNOWN_CODES.contains(c) }

private val KNOWN_CODES: Set<String> = setOf(
    // Africa
    "DZ", "BD", "BJ", "BF", "BI", "BW", "CM", "CI", "CV", "EG", "ET", "GH", "GN", "KE",
    "LY", "MA", "MG", "MU", "MW", "NE", "NG", "RW", "SN", "SD", "TN", "TZ", "UG", "ZA", "ZM",
    // Americas
    "AR", "BO", "BR", "CA", "CL", "CO", "CR", "CU", "DO", "EC", "GT", "HN", "MX", "NI",
    "PA", "PE", "PY", "UY", "US", "VE",
    // Asia
    "AE", "AF", "AZ", "BH", "BN", "BT", "CN", "HK", "ID", "IL", "IN", "IQ", "IR", "JO",
    "JP", "KG", "KZ", "KH", "KR", "LA", "LK", "MM", "MN", "MY", "NP", "OM", "PH", "PK",
    "QA", "SA", "SG", "SY", "TH", "TR", "TW", "UZ", "VN", "YE",
    // Europe
    "AD", "AL", "AM", "AT", "BA", "BE", "BG", "BY", "CH", "CY", "CZ", "DE", "DK", "EE",
    "ES", "FI", "FR", "GB", "GE", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV",
    "MC", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "RU", "SE", "SI",
    "SK", "SM", "UA",
    // Oceania
    "AU", "FJ", "MV", "NZ",
    // Middle East / Central Asia
    "KW", "LB", "PS", "TJ", "TM"
)

/**
 * One full-circle flag tile: the national flag clipped edge-to-edge to the circle, or the
 * neutral code tile when the code is unknown. This is what fills a server row's circle — the
 * flag IS the tile, not a sticker on it.
 *
 * @param code ISO 3166-1 alpha-2, or null/unknown for the neutral tile.
 * @param size the tile's diameter.
 * @param fallbackText what the neutral tile prints (usually the two-letter code or a glyph).
 */
@Composable
fun CountryFlagCircle(
    code: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackText: String = "◈",
    fallbackTone: Color = Color(0xFF8899AA),
    fallbackFill: Color = Color(0x26334455),
    description: String = ""
) {
    val supported = code != null && CountryFlagSupported(code)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics { if (description.isNotEmpty()) contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (supported) {
            // Keep the table's real 3:2 geometry, but make the rectangle wider than the
            // viewport.  A centred 3:2 rectangle at 2:3 height would leave transparent caps at
            // the top and bottom of the circle (the old "contain" bug).  This is a true cover:
            // the circle is painted edge-to-edge and only the flag's left/right edges are cropped.
            Canvas(Modifier.requiredWidth(size * 1.5f).height(size)) {
                drawCountryFlag(code!!)
            }
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(fallbackFill),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackText,
                    color = fallbackTone,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
