package com.marbleng.app.ui

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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServerCountry
import androidx.compose.ui.unit.sp

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

@Composable
fun protocolTone(family: ProtocolFamily): Color = when (family) {
    ProtocolFamily.VLESS -> Color(0xFF7C5CFF)
    ProtocolFamily.VMESS -> Color(0xFF0EA5E9)
    ProtocolFamily.TROJAN -> Color(0xFF10B981)
    ProtocolFamily.SHADOWSOCKS -> Color(0xFFF59E0B)
    ProtocolFamily.HYSTERIA2 -> Color(0xFFF43F5E)
    ProtocolFamily.WIREGUARD -> Color(0xFF14B8A6)
    ProtocolFamily.SSH -> Color(0xFF6366F1)
    ProtocolFamily.SOCKS -> Color(0xFFEC4899)
    ProtocolFamily.HTTP -> Color(0xFFF97316)
    ProtocolFamily.OTHER -> Color(0xFF94A3B8)
}

@Composable
fun protocolToneOf(scheme: String): Color = protocolTone(protocolFamilyOf(scheme))

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

@Composable
fun ProtocolTile(
    scheme: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    flag: String? = null,
    flagCode: String? = null,
    stateTone: Color? = null,
    lifted: Boolean = false,
    locationTrust: MarbleLocationTrust = MarbleLocationTrust.LABEL_GUESS
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
    val mayPaintFlag = locationTrust.mayDrawFlag()
    val flagArt = flagCode
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { CountryFlagSupported(it) }
        ?.takeIf { mayPaintFlag }

    val unconfirmed = flagArt == null

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = when {
                    flagArt != null && locationTrust.isVerified() ->
                        "Server in ${ServerCountry.nameFor(flagArt)}"
                    flagArt != null ->
                        "${ServerCountry.nameFor(flagArt)} • reported by one source, not yet confirmed"
                    else -> "${family.label} • location not verified"
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
                    clip = false,
                    ambientColor = rim.copy(alpha = .18f),
                    spotColor = rim.copy(alpha = .30f)
                )
                .clip(CircleShape)
                .background(tone.copy(alpha = fillAlpha))
                .border(1.dp, rim.copy(alpha = rimAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CountryFlagCircle(
                code = flagArt,
                size = size - 3.dp,
                fallbackText = "🌐",
                fallbackTone = tone,
                fallbackFill = Color.Transparent
            )
        }
        if (unconfirmed) {
            Canvas(Modifier.matchParentSize()) {
                val box = this.size
                val strokeW = 1.dp.toPx()
                drawCircle(
                    color = rim.copy(alpha = .58f),
                    radius = ((box.minDimension - strokeW) / 2f).coerceAtLeast(1f),
                    center = Offset(box.width / 2f, box.height / 2f),
                    style = Stroke(
                        width = strokeW,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(2.4.dp.toPx(), 2.2.dp.toPx())
                        )
                    )
                )
            }
        }
    }
}

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

@Composable
fun PingQualityBars(
    quality: Int,
    tone: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
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
