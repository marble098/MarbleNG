package com.marbleng.app.ui

// =================================================================================================
// MARBLE_PROTOCOL_IDENTITY — one unique colour identity per wire scheme.
// MARBLE_PROTOCOL_TEXT_IDENTITY_V193 — the flag is the circle; the type is text.
// =================================================================================================
//
// The hand-drawn glyph set (the VLESS "V", the VMESS envelope, the sock, the shields) is gone:
// at tile size it read as the ugly, unexplainable shapes inside the server circles. A server's
// circle now carries its country — the real national flag filling the disc edge to edge, or the
// name's own flag glyph while the tested location is still unknown — and the wire scheme speaks
// as TEXT: [ProtocolBadge] renders each protocol's label bold in a hue that belongs to it alone
// (see [protocolTone]: ten families, ten distinct, hand-picked colours). The same hue tints the
// resting rim of the circle, so type and place still read from the same glance.
//
// The layout vocabulary follows the modern VPN clients on the Play Store: circular flag avatars,
// quiet tinted fills, hairline borders that light up with state, and a latency readout that sits
// in its own right-aligned stat column — number first, quality meter under it — instead of a lone
// number floating in the middle of a row.

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServerCountry
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
 * MARBLE_PROTOCOL_TEXT_IDENTITY_V193 — the single tone table for every protocol. Both lists read
 * it, so the Servers page and the Home page can never paint the same scheme in two different
 * colours again.
 *
 * Every family owns its own hue now — one hand-picked colour per wire scheme, chosen to stay
 * legible on both the light and the dark surface (mid-tone 500-class hues on their own faint
 * tint) and to be unmistakable from its neighbours: violet, sky, emerald, amber, rose, teal,
 * indigo, magenta, orange and cool grey. Ten schemes, ten hues, zero collisions.
 */
@Composable
fun protocolTone(family: ProtocolFamily): Color = when (family) {
    ProtocolFamily.VLESS -> Color(0xFF7C5CFF)          // electric violet
    ProtocolFamily.VMESS -> Color(0xFF0EA5E9)          // sky blue
    ProtocolFamily.TROJAN -> Color(0xFF10B981)         // emerald
    ProtocolFamily.SHADOWSOCKS -> Color(0xFFF59E0B)    // amber
    ProtocolFamily.HYSTERIA2 -> Color(0xFFF43F5E)      // rose — the fast, loud one
    ProtocolFamily.WIREGUARD -> Color(0xFF14B8A6)      // teal
    ProtocolFamily.SSH -> Color(0xFF6366F1)            // indigo
    ProtocolFamily.SOCKS -> Color(0xFFEC4899)          // magenta
    ProtocolFamily.HTTP -> Color(0xFFF97316)           // orange
    ProtocolFamily.OTHER -> Color(0xFF94A3B8)          // cool slate grey
}

@Composable
fun protocolToneOf(scheme: String): Color = protocolTone(protocolFamilyOf(scheme))

/**
 * MARBLE_PROTOCOL_TEXT_IDENTITY_V193 — the protocol as pure text: a solid, pill-shaped chip in
 * the family's own hue, its label bold and slightly tracked. No glyph, no icon well — the colour
 * IS the second identifier, and each of the ten families owns one. This is the "what kind of
 * server is this" answer, sized to sit under a server name.
 */
@Composable
fun ProtocolBadge(
    scheme: String,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val family = protocolFamilyOf(scheme)
    val tone = protocolTone(family)
    Text(
        text = label ?: family.label,
        color = tone,
        style = TextStyle(
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = .45.sp
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.5.dp))
            .background(tone.copy(alpha = .14f))
            .border(1.dp, tone.copy(alpha = .38f), RoundedCornerShape(6.5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

/**
 * The circular server avatar of a row.
 *
 * MARBLE_PROTOCOL_TEXT_IDENTITY_V193 — the flag IS the circle, always. The hand-drawn protocol
 * glyphs that used to sit inside these circles are gone; when the endpoint's country is known the
 * real national flag fills the circle edge to edge, and when it is not, the country the server's
 * own name leads with (its emoji glyph) stands in at full size on the protocol's tinted well —
 * never a wire-scheme doodle. The protocol's hue survives as the resting rim, and the type itself
 * is spoken by [ProtocolBadge]'s coloured text chip on the line below.
 *
 * [stateTone] — when the row is connected or selected — overrides the rim and casts the soft
 * shadow, so the connection state and the protocol identity never fight over the same pixel.
 */
@Composable
fun ProtocolTile(
    scheme: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    flag: String? = null,
    flagCode: String? = null,
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
    val flagArt = flagCode?.takeIf { it.isNotBlank() }?.takeIf { CountryFlagSupported(it) }
    // The emoji a server's own name leads with (🇩🇪, 🇳🇱 …) is the stand-in while the tested
    // location is still unknown — drawn large, centred, filling the well like a flag would.
    val nameFlag = flag?.takeIf { it.isNotBlank() }
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (flagArt != null) {
                    "Server in ${ServerCountry.nameFor(flagArt)}"
                } else {
                    family.label
                }
            },
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
            // One content rule for every state: a real flag fills the circle when the location
            // is known; the name's own flag glyph fills it otherwise. The wire scheme never
            // draws inside this circle again.
            CountryFlagCircle(
                code = flagArt,
                size = size - 2.dp,
                fallbackText = nameFlag ?: "🌐",
                fallbackTone = if (nameFlag != null) Aether.Ink else tone,
                fallbackFill = Color.Transparent,
                styleOverride = if (nameFlag != null) {
                    TextStyle(fontSize = (size.value * .52f).sp)
                } else {
                    null
                }
            )
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
 *
 * MARBLE_SERVERS_HIERARCHY_V189 — two switches, both off by default so the Home list is untouched:
 *
 * - [compact] drops every size one step (13.5 → [ServersHierarchy.ROW_PING_SP] sp, a narrower
 *   column, a smaller meter) for a server row that lives *inside* a subscription card, where the
 *   latency is level-2 information and must not out-shout the subscription's own name;
 * - [quietFailure] takes the red off a probe that ran and got no answer. A server that did not
 *   reply is a fact about that server, not an emergency on the page: the row it sits in fades and
 *   carries the cross, and the readout stays in a muted tone. Red stays for the things a user has
 *   to act on — and "slow" (measured, over 250 ms) still reads red, because that one is a real
 *   measurement.
 */
@Composable
fun ServerPingStat(
    latencyMs: Int,
    measured: Boolean,
    testing: Boolean,
    attempted: Boolean = false,
    compact: Boolean = false,
    quietFailure: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tone = when {
        testing -> Aether.Cyan
        measured -> when {
            latencyMs < 100 -> Aether.Emerald
            latencyMs <= 250 -> Aether.Amber
            else -> Aether.Danger
        }
        attempted -> if (quietFailure) Aether.InkMuted else Aether.Danger
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
    // MARBLE_SERVERS_HIERARCHY_V189 — one step down in every dimension for a nested row.
    val numberSize = if (compact) ServersHierarchy.ROW_PING_SP.sp else 13.5.sp
    val unitSize = if (compact) 8.sp else 8.5.sp
    val glyphSize = if (compact) 12.sp else 13.sp
    val spinner = if (compact) 12.dp else 13.dp
    Column(
        modifier = modifier
            .widthIn(min = if (compact) 44.dp else 52.dp)
            .height(if (compact) 30.dp else 34.dp)
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
                    style = TextStyle(fontSize = unitSize, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                MarbleExpressiveCircularIndicator(
                    modifier = Modifier.size(spinner),
                    color = tone,
                    strokeWidth = 1.6.dp,
                    arcCount = 2
                )
            }

            !measured -> Text(
                if (attempted) "✕" else "—",
                color = tone,
                style = TextStyle(fontSize = glyphSize, fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            else -> Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "$latencyMs",
                        color = tone,
                        style = TextStyle(
                            fontSize = numberSize,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1
                    )
                    Text(
                        "ms",
                        color = tone.copy(alpha = .70f),
                        style = TextStyle(fontSize = unitSize, fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                }
                PingQualityBars(quality, tone, compact = compact)
            }
        }
    }
}

/**
 * The three-bar quality meter under a measured latency: more bars means a faster route. Filled
 * bars take the measurement tone, the remainder stays a quiet hairline, so the meter reads as an
 * instrument rather than a decoration.
 *
 * [compact] shrinks the meter to the size a nested subscription row has room for.
 */
@Composable
fun PingQualityBars(
    quality: Int,
    tone: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    // The Aether palette is a @Composable getter, so the resting-bar colour is resolved here,
    // outside the DrawScope, where the Canvas lambda cannot reach it.
    val unlit = Aether.InkFaint.copy(alpha = .30f)
    Canvas(
        modifier = modifier.size(
            width = if (compact) 14.dp else 16.dp,
            height = if (compact) 8.5.dp else 10.dp
        )
    ) {
        val barW = size.width / 4.6f
        val gap = size.width / 8f
        val heights = listOf(size.height * .42f, size.height * .72f, size.height)
        // Three bars and two gaps: the meter is centred on its own width, not on the bars' heights.
        val total = barW * 3f + gap * 2f
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
