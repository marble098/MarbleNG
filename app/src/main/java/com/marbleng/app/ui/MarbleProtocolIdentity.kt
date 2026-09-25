package com.marbleng.app.ui

// =================================================================================================
// MARBLE_PROTOCOL_IDENTITY — one unique, minimal visual identity per wire scheme.
// =================================================================================================
//
// Before this every server type was a plain text chip in a flat colour, and the two lists (Servers
// page, Home page) even disagreed about which colour belongs to which scheme. Each protocol now
// owns a small hand-drawn vector glyph, one flat tone and a tiny badge, drawn in the same
// Canvas-stroke language as the dock icons so the identity reads as part of the product and not
// as a sticker glued onto it.
//
// The layout vocabulary follows the modern VPN clients on the Play Store (ZedSecure and its
// lineage): circular icon containers for the protocol, quiet tinted fills, hairline borders that
// light up with state, and a latency readout that sits in its own right-aligned stat column —
// number first, quality meter under it — instead of a lone number floating in the middle of a
// row.

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The wire schemes Marble knows how to draw an identity for. */
enum class ProtocolFamily(val label: String) {
    VLESS("VLESS"),
    VMESS("VMESS"),
    TROJAN("TROJAN"),
    SHADOWSOCKS("SS"),
    HYSTERIA2("HY2"),
    WIREGUARD("WG"),
    SSH("SSH"),
    SOCKS("SOCKS"),
    HTTP("HTTP"),
    OTHER("PROXY")
}

/** Normalises any stored scheme string onto the family that owns its identity. */
fun protocolFamilyOf(scheme: String): ProtocolFamily =
    when (scheme.trim().uppercase()) {
        "VLESS" -> ProtocolFamily.VLESS
        "VMESS" -> ProtocolFamily.VMESS
        "TROJAN" -> ProtocolFamily.TROJAN
        "SHADOWSOCKS", "SS" -> ProtocolFamily.SHADOWSOCKS
        "HYSTERIA2", "HY2" -> ProtocolFamily.HYSTERIA2
        "WIREGUARD", "WG" -> ProtocolFamily.WIREGUARD
        "SSH" -> ProtocolFamily.SSH
        "SOCKS" -> ProtocolFamily.SOCKS
        "HTTP", "HTTPS" -> ProtocolFamily.HTTP
        else -> ProtocolFamily.OTHER
    }

/**
 * The single tone table for every protocol. Both lists read it, so the Servers page and the Home
 * page can never paint the same scheme in two different colours again.
 */
@Composable
fun protocolTone(family: ProtocolFamily): Color = when (family) {
    ProtocolFamily.VLESS -> Aether.Amethyst
    ProtocolFamily.VMESS -> Aether.Cyan
    ProtocolFamily.TROJAN -> Aether.Emerald
    ProtocolFamily.SHADOWSOCKS -> Aether.Amber
    ProtocolFamily.HYSTERIA2 -> Aether.CyanBright
    ProtocolFamily.WIREGUARD -> Aether.SlateBright
    ProtocolFamily.SSH -> Aether.Slate
    ProtocolFamily.SOCKS -> Aether.InkMuted
    ProtocolFamily.HTTP -> Aether.AmethystBright
    ProtocolFamily.OTHER -> Aether.Cyan
}

@Composable
fun protocolToneOf(scheme: String): Color = protocolTone(protocolFamilyOf(scheme))

/**
 * The protocol's own glyph: one minimal stroke drawing in a 24×24 design space, scaled to the
 * requested size. Each family is deliberately its own silhouette — a V that ripples, an envelope,
 * a shield with a keyhole, a sock, a double chevron, a key, a terminal prompt, a tunnel ring and
 * a swap of arrows — so a server's type is readable even with the label cropped away.
 */
@Composable
fun ProtocolGlyph(family: ProtocolFamily, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Everything is drawn in 24×24 design units and scaled by the real canvas size, so the
        // glyph keeps its stroke weight ratio from 11 dp badges up to full-size tiles.
        val u = size.width / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        val stroke = 1.9f * u
        val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (family) {
            // A lean V with one signal ripple leaving its tip: "less" is a stream.
            ProtocolFamily.VLESS -> {
                drawPath(
                    Path().apply {
                        moveTo(6.4f, 6.6f); lineTo(12f, 17.4f); lineTo(17.6f, 6.6f)
                    },
                    color, style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(8.9f, 19.7f); quadTo(12f, 22.3f, 15.1f, 19.7f)
                    },
                    color, style = line
                )
            }

            // An envelope with its flap: a message in transit.
            ProtocolFamily.VMESS -> {
                drawPath(
                    Path().apply {
                        moveTo(6.8f, 6.6f); lineTo(17.2f, 6.6f); quadTo(19.4f, 6.6f, 19.4f, 8.8f)
                        lineTo(19.4f, 15.2f); quadTo(19.4f, 17.4f, 17.2f, 17.4f)
                        lineTo(6.8f, 17.4f); quadTo(4.6f, 17.4f, 4.6f, 15.2f)
                        lineTo(4.6f, 8.8f); quadTo(4.6f, 6.6f, 6.8f, 6.6f); close()
                    },
                    color, style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(5.6f, 7.8f); lineTo(12f, 13.4f); lineTo(18.4f, 7.8f)
                    },
                    color, style = line
                )
            }

            // A shield with a keyhole: hidden armour.
            ProtocolFamily.TROJAN -> {
                drawPath(
                    Path().apply {
                        moveTo(12f, 3.6f); lineTo(18.7f, 6.4f); lineTo(18.7f, 11.8f)
                        cubicTo(18.7f, 15.5f, 16.1f, 18.8f, 12f, 20.7f)
                        cubicTo(7.9f, 18.8f, 5.3f, 15.5f, 5.3f, 11.8f)
                        lineTo(5.3f, 6.4f); close()
                    },
                    color, style = line
                )
                drawCircle(color, radius = 1.35f * u, center = p(12f, 10.4f))
                drawLine(color, p(12f, 11.9f), p(12f, 14.3f), stroke, StrokeCap.Round)
            }

            // The sock itself — the silhouette the name was built on.
            ProtocolFamily.SHADOWSOCKS -> {
                drawPath(
                    Path().apply {
                        moveTo(9f, 4.6f); lineTo(9f, 10.2f)
                        cubicTo(9f, 13.1f, 10.8f, 15.3f, 13.6f, 16.3f)
                        lineTo(17f, 17.5f); cubicTo(19f, 18.2f, 20.1f, 15.7f, 18.5f, 14.4f)
                        lineTo(13.8f, 10.8f); lineTo(13.8f, 4.6f)
                    },
                    color, style = line
                )
                drawLine(color, p(7.7f, 4.6f), p(15.1f, 4.6f), stroke, StrokeCap.Round)
            }

            // Twin chevrons pushing right: pure speed.
            ProtocolFamily.HYSTERIA2 -> {
                drawPath(
                    Path().apply {
                        moveTo(5.9f, 6.9f); lineTo(12.1f, 12f); lineTo(5.9f, 17.1f)
                    },
                    color, style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(11.9f, 6.9f); lineTo(18.1f, 12f); lineTo(11.9f, 17.1f)
                    },
                    color, style = line
                )
            }

            // A key: what it guards.
            ProtocolFamily.WIREGUARD -> {
                drawCircle(color, radius = 3.4f * u, center = p(8.6f, 12f), style = line)
                drawLine(color, p(12f, 12f), p(19.4f, 12f), stroke, StrokeCap.Round)
                drawLine(color, p(16.2f, 12f), p(16.2f, 15.2f), stroke, StrokeCap.Round)
                drawLine(color, p(19.1f, 12f), p(19.1f, 14.6f), stroke, StrokeCap.Round)
            }

            // A terminal prompt: the shell of remote access.
            ProtocolFamily.SSH -> {
                drawPath(
                    Path().apply {
                        moveTo(6.4f, 8.2f); lineTo(10.8f, 12f); lineTo(6.4f, 15.8f)
                    },
                    color, style = line
                )
                drawLine(color, p(13.2f, 15.8f), p(18f, 15.8f), stroke, StrokeCap.Round)
            }

            // A tunnel ring: a layer you step through.
            ProtocolFamily.SOCKS -> {
                drawCircle(color, radius = 7.4f * u, center = p(12f, 12f), style = line)
                drawCircle(color, radius = 3.2f * u, center = p(12f, 12f), style = line)
            }

            // A request and a reply, one above the other.
            ProtocolFamily.HTTP -> {
                drawLine(color, p(5.8f, 9.2f), p(18.2f, 9.2f), stroke, StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(15.2f, 6.4f); lineTo(18.2f, 9.2f); lineTo(15.2f, 12f)
                    },
                    color, style = line
                )
                drawLine(color, p(18.2f, 14.8f), p(5.8f, 14.8f), stroke, StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(8.8f, 12f); lineTo(5.8f, 14.8f); lineTo(8.8f, 17.6f)
                    },
                    color, style = line
                )
            }

            // The fallback: a sealed hex with its own centre.
            ProtocolFamily.OTHER -> {
                drawPath(
                    Path().apply {
                        moveTo(12f, 4.4f); lineTo(18.58f, 8.2f); lineTo(18.58f, 15.8f)
                        lineTo(12f, 19.6f); lineTo(5.42f, 15.8f); lineTo(5.42f, 8.2f); close()
                    },
                    color, style = line
                )
                drawCircle(color, radius = 1.55f * u, center = p(12f, 12f))
            }
        }
    }
}

/**
 * The tiny type badge: the glyph beside a two-to-five letter label, on a tint that is barely
 * there. This is the "what kind of server is this" answer, sized to sit under a server name.
 */
@Composable
fun ProtocolBadge(
    scheme: String,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val family = protocolFamilyOf(scheme)
    val tone = protocolTone(family)
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(tone.copy(alpha = .10f))
            .border(1.dp, tone.copy(alpha = .24f), shape)
            .padding(start = 5.dp, end = 5.dp, top = 1.5.dp, bottom = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.5.dp)
    ) {
        ProtocolGlyph(family, tone, Modifier.size(11.dp))
        Text(
            text = label ?: family.label,
            color = tone,
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .45.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The circular protocol container of a server row: the glyph centred on a quiet tint, a hairline
 * rim that lights up when the row carries traffic or is the selected one, and an optional flag
 * badge in the corner so the country stays readable at a glance.
 *
 * [stateTone] — when the row is connected or selected — overrides the rim and casts the soft
 * shadow, so the connection state and the protocol identity never fight over the same pixel.
 */
@Composable
fun ProtocolTile(
    scheme: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    glyphFraction: Float = .56f,
    flag: String? = null,
    stateTone: Color? = null,
    lifted: Boolean = false
) {
    val family = protocolFamilyOf(scheme)
    val tone = protocolTone(family)
    val rim = stateTone ?: tone
    val rimAlpha by animateFloatAsState(
        targetValue = if (stateTone != null) .55f else .26f,
        animationSpec = tween(durationMillis = 180),
        label = "protocol-tile-rim"
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (stateTone != null) .16f else .10f,
        animationSpec = tween(durationMillis = 180),
        label = "protocol-tile-fill"
    )
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = family.label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = if (lifted || stateTone != null) 6.dp else 0.dp,
                    shape = CircleShape,
                    spotColor = rim.copy(alpha = .30f)
                )
                .clip(CircleShape)
                .background(tone.copy(alpha = fillAlpha))
                .border(1.dp, rim.copy(alpha = rimAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            ProtocolGlyph(family, tone, Modifier.size((size.value * glyphFraction).dp))
        }
        // The flag chip rides on the rim's edge; it sits OUTSIDE the clipped circle so its
        // border is never cut by the tile's own shape.
        if (!flag.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(Aether.VoidElevated)
                    .border(1.dp, Aether.GlassBorderSoft, CircleShape)
                    .padding(horizontal = 2.5.dp, vertical = 0.5.dp)
            ) {
                Text(
                    text = flag,
                    style = TextStyle(fontSize = 8.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/**
 * The one-line state word of a server row — "Connected", "Securing", "Selected" — at its quietest
 * possible size: a 9.5 sp word on a tint pill.
 */
@Composable
fun ServerStateChip(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(6.5.dp)
    Text(
        text = text,
        color = tone,
        style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(tone.copy(alpha = .13f))
            .padding(start = 5.dp, end = 5.dp, top = 1.dp, bottom = 1.dp)
    )
}

/**
 * The right-hand stat column of a server row: the measured number and its unit on top, a three-bar
 * quality meter under it. Fixed minimum width, content end-aligned, so every row in a list lines
 * its numbers up into one quiet column at the trailing edge — the readout owns the space next to
 * the row's actions instead of floating free in the middle of the row.
 *
 * Same measurement vocabulary as the legacy capsule: a probe that ran and failed is a red fact,
 * one that never ran is a quiet dash, and a running probe is the tiny wavy spinner.
 */
@Composable
fun ServerPingStat(
    latencyMs: Int,
    measured: Boolean,
    testing: Boolean,
    attempted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tone = when {
        testing -> Aether.Cyan
        measured -> when {
            latencyMs < 100 -> Aether.Emerald
            latencyMs <= 250 -> Aether.Amber
            else -> Aether.Danger
        }
        attempted -> Aether.Danger
        else -> Aether.InkFaint
    }
    val quality = when {
        !measured -> 0
        latencyMs < 100 -> 3
        latencyMs <= 250 -> 2
        else -> 1
    }
    val spoken = when {
        testing -> trx("Testing server")
        measured -> trx("Latency") + " $latencyMs ms"
        attempted -> trx("No response")
        else -> trx("Not measured")
    }
    Column(
        modifier = modifier
            .widthIn(min = 52.dp)
            .height(34.dp)
            .semantics { contentDescription = spoken },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            testing -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "ms",
                    color = tone.copy(alpha = .74f),
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                MarbleExpressiveCircularIndicator(
                    modifier = Modifier.size(13.dp),
                    color = tone,
                    strokeWidth = 1.6.dp,
                    arcCount = 2
                )
            }

            !measured -> Text(
                if (attempted) "✕" else "—",
                color = tone,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            else -> Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "$latencyMs",
                        color = tone,
                        style = TextStyle(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                    Text(
                        "ms",
                        color = tone.copy(alpha = .70f),
                        style = TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                }
                PingQualityBars(quality, tone)
            }
        }
    }
}

/**
 * The three-bar quality meter under a measured latency: more bars means a faster route. Filled
 * bars take the measurement tone, the remainder stays a quiet hairline, so the meter reads as an
 * instrument rather than a decoration.
 */
@Composable
fun PingQualityBars(quality: Int, tone: Color, modifier: Modifier = Modifier) {
    // The Aether palette is a @Composable getter, so the resting-bar colour is resolved here,
    // outside the DrawScope, where the Canvas lambda cannot reach it.
    val unlit = Aether.InkFaint.copy(alpha = .30f)
    Canvas(modifier = modifier.size(width = 16.dp, height = 10.dp)) {
        val barW = size.width / 4.6f
        val gap = size.width / 8f
        val heights = listOf(size.height * .42f, size.height * .72f, size.height)
        val total = heights.sum() + gap * 2f
        var x = (size.width - total) / 2f
        heights.forEachIndexed { index, h ->
            val lit = index < quality
            val corner = barW / 2f
            drawRoundRect(
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(corner, corner),
                color = if (lit) tone else unlit
            )
            x += barW + gap
        }
    }
}
