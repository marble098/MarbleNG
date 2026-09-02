@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

// MARBLE_HOME_STYLE_V110
// MARBLE_HOME_STYLE_IMMERSIVE_V111
//
// Four presentations of one connection surface.
//
// Design contract shared by every style — enforced by scripts/system-integrity-check.py:
//   1. all four render the SAME runtime evidence: node name, source name, IP + country flag with
//      its three actions (copy / refresh / details), session uptime, and the one-shot ping;
//   2. no style draws a quality indicator around the connect control;
//   3. the artwork is Canvas-drawn vector work — no bitmaps, no downloads, no per-card infinite
//      transitions (ambient motion comes from Marble's single shared frame clock).
//
// V111 — every style now owns the *entire* viewport: a full-bleed Canvas backdrop plus a layout
// that scales itself from the real available height, and every piece of shared evidence is
// re-skinned per style through [HomeFlavor], so the same facts read as four genuinely different
// products: an abyssal organism, a command deck, a nebula HUD and an architect's blueprint.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.ServerIntelInfo
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ProxyProfile
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Shared evidence model
// ---------------------------------------------------------------------------------------------

/**
 * Everything the four Home styles are allowed to show, resolved exactly once per composition.
 *
 * Styles receive this snapshot instead of the repository so no presentation can invent, round or
 * omit a value that another presentation shows differently.
 */
internal data class HomeEvidence(
    val profile: ProxyProfile?,
    val nodeName: String,
    val sourceName: String,
    val ip: String,
    val flag: String,
    val countryCode: String,
    val location: String,
    val ipLoading: Boolean,
    val ipError: Boolean,
    val connected: Boolean,
    val connecting: Boolean,
    val blocked: Boolean,
    val connectedSinceMs: Long,
    val pingMs: Int,
    val pingState: ConnectionPingState,
    val downBps: Long,
    val upBps: Long
)

internal fun buildHomeEvidence(
    repo: AppRepository,
    profile: ProxyProfile?,
    displayName: String,
    info: ServerIntelInfo?,
    fallbackFlag: String?
): HomeEvidence {
    val connected = repo.state == "CONNECTED"
    return HomeEvidence(
        profile = profile,
        nodeName = displayName,
        sourceName = profile?.subscriptionName?.trim().orEmpty(),
        ip = when {
            info != null && info.ip.isNotBlank() -> info.ip
            else -> profile?.host?.trim()?.removeSurrounding("[", "]").orEmpty()
        },
        flag = info?.flag?.takeIf { it.isNotBlank() } ?: fallbackFlag.orEmpty(),
        countryCode = info?.countryCode.orEmpty(),
        location = info?.locationLabel.orEmpty(),
        ipLoading = repo.serverIntelLoading,
        ipError = repo.serverIntelError.isNotBlank() && info == null,
        connected = connected,
        connecting = repo.state == "CONNECTING",
        blocked = repo.state == "BLOCKED",
        connectedSinceMs = repo.connectedSinceMs,
        pingMs = repo.connectionPingMs,
        pingState = repo.connectionPingState,
        downBps = if (connected) repo.liveDownBps else 0L,
        upBps = if (connected) repo.liveUpBps else 0L
    )
}

/** Actions the evidence block can trigger. Identical in every style. */
internal data class HomeActions(
    val onToggleConnection: () -> Unit,
    val onCopyIp: () -> Unit,
    val onRefreshIp: () -> Unit,
    val onIpDetails: () -> Unit,
    val onTestPing: () -> Unit,
    val onLibrary: () -> Unit
)

/**
 * The per-style skin every shared evidence widget renders through. One flavor per Home style, so
 * node/source, the IP row, uptime and the ping element look hand-made for each presentation while
 * remaining the exact same facts and actions.
 */
internal enum class HomeFlavor { ORGANIC, ORBIT, NEBULA, BLUEPRINT }

@Composable
internal fun homeTone(evidence: HomeEvidence): Color = when {
    evidence.connected -> Aether.Emerald
    evidence.connecting -> Aether.Amethyst
    evidence.blocked -> Aether.Danger
    else -> Aether.Cyan
}

@Composable
internal fun homeStatusText(evidence: HomeEvidence): String {
    val t = Tr.now
    return when {
        evidence.connected -> t.statusProtected
        evidence.connecting -> t.securingRoute
        evidence.blocked -> t.connectionStopped
        else -> t.readyToConnect
    }
}

@Composable
internal fun homeActionLabel(evidence: HomeEvidence): String {
    val t = Tr.now
    return when {
        evidence.connected -> t.disconnect
        evidence.connecting -> t.cancel
        evidence.blocked -> t.reset
        else -> t.connect
    }
}

/** `1:04:09` / `07:31`. Ticks once a second and only while a session is actually running. */
@Composable
internal fun rememberUptimeLabel(connectedSinceMs: Long): String {
    var nowMs by remember(connectedSinceMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSinceMs) {
        if (connectedSinceMs <= 0L) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (connectedSinceMs <= 0L) return "00:00"
    val totalSeconds = ((nowMs - connectedSinceMs) / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
internal fun homePingLabel(evidence: HomeEvidence): String {
    val t = Tr.now
    return when (evidence.pingState) {
        ConnectionPingState.MEASURING -> t.measuring
        ConnectionPingState.MEASURED -> "${evidence.pingMs} ms"
        ConnectionPingState.FAILED -> t.pingFailed
        ConnectionPingState.IDLE -> if (evidence.connected) t.notMeasured else "—"
    }
}

@Composable
internal fun homePingTone(evidence: HomeEvidence, fallback: Color): Color =
    when (evidence.pingState) {
        ConnectionPingState.MEASURED -> marbleMetricTone(pingMetricBand(evidence.pingMs))
        ConnectionPingState.FAILED -> Aether.Danger
        else -> fallback
    }

/** Ping element hint under the value: what a tap will do right now. */
@Composable
internal fun homePingActionHint(evidence: HomeEvidence): String {
    val t = Tr.now
    return when {
        !evidence.connected -> ""
        evidence.pingState == ConnectionPingState.MEASURING -> ""
        evidence.pingState == ConnectionPingState.IDLE -> t.testPing
        else -> t.retestPing
    }
}

internal fun homePingTappable(evidence: HomeEvidence): Boolean =
    evidence.connected && evidence.pingState != ConnectionPingState.MEASURING

/** Deterministic 0..1 noise so particles/stars need no random state and no allocations. */
private fun hash01(seed: Int): Float {
    val s = sin(seed * 12.9898f + 78.233f) * 43758.5453f
    return s - floor(s)
}

// ---------------------------------------------------------------------------------------------
// Shared evidence widgets
// ---------------------------------------------------------------------------------------------

/** Compact icon set owned by the Home styles so they never depend on a font or a drawable. */
internal enum class HomeGlyph { POWER, CHECK, RESET, COPY, REFRESH, MORE, PULSE, CLOCK, LIBRARY }

@Composable
internal fun HomeGlyphIcon(glyph: HomeGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (size.minDimension * .095f).coerceIn(1.3f, 3.2f)
        val line = Stroke(width = stroke, cap = StrokeCap.Round)
        when (glyph) {
            HomeGlyph.POWER -> {
                drawArc(
                    color = color,
                    startAngle = -62f,
                    sweepAngle = 304f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawLine(color, Offset(w * .5f, h * .10f), Offset(w * .5f, h * .46f), stroke, StrokeCap.Round)
            }
            HomeGlyph.CHECK -> drawPath(
                Path().apply {
                    moveTo(w * .20f, h * .53f)
                    lineTo(w * .42f, h * .74f)
                    lineTo(w * .80f, h * .27f)
                },
                color,
                style = line
            )
            HomeGlyph.RESET -> {
                drawArc(
                    color = color,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawLine(color, Offset(w * .80f, h * .30f), Offset(w * .84f, h * .10f), stroke, StrokeCap.Round)
            }
            HomeGlyph.COPY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * .32f, h * .16f),
                    size = Size(w * .50f, h * .52f),
                    cornerRadius = CornerRadius(w * .10f),
                    style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(w * .66f, h * .82f)
                        lineTo(w * .20f, h * .82f)
                        lineTo(w * .20f, h * .32f)
                    },
                    color,
                    style = line
                )
            }
            HomeGlyph.REFRESH -> {
                drawArc(
                    color = color,
                    startAngle = 120f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(w * .28f, h * .58f)
                        lineTo(w * .20f, h * .80f)
                        lineTo(w * .44f, h * .76f)
                    },
                    color,
                    style = line
                )
            }
            HomeGlyph.MORE -> listOf(.26f, .50f, .74f).forEach { x ->
                drawCircle(color, radius = stroke * .82f, center = Offset(w * x, h * .5f))
            }
            HomeGlyph.PULSE -> drawPath(
                Path().apply {
                    moveTo(w * .10f, h * .52f)
                    lineTo(w * .32f, h * .52f)
                    lineTo(w * .44f, h * .24f)
                    lineTo(w * .58f, h * .78f)
                    lineTo(w * .69f, h * .52f)
                    lineTo(w * .90f, h * .52f)
                },
                color,
                style = line
            )
            HomeGlyph.CLOCK -> {
                drawCircle(color, radius = w * .34f, center = Offset(w * .5f, h * .5f), style = line)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .5f, h * .28f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .68f, h * .58f), stroke, StrokeCap.Round)
            }
            HomeGlyph.LIBRARY -> listOf(.28f, .50f, .72f).forEach { y ->
                drawLine(color, Offset(w * .20f, h * y), Offset(w * .80f, h * y), stroke, StrokeCap.Round)
            }
        }
    }
}

/**
 * Node + source identity, skinned per style.
 *
 * Node and source are always labelled, so a duplicated node name inside two subscriptions can
 * still be told apart at a glance.
 */
@Composable
internal fun HomeIdentityBlock(
    evidence: HomeEvidence,
    tone: Color,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val nodeValue = evidence.nodeName.ifBlank { t.chooseRoute }
    val sourceValue = evidence.sourceName.ifBlank { "—" }
    when (flavor) {
        HomeFlavor.ORGANIC -> Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Two floating "leaf cells": asymmetric corners, one leaning each way.
            OrganicLeafChip(
                label = t.node,
                value = nodeValue,
                tone = tone,
                leanStart = true,
                modifier = Modifier.fillMaxWidth().padding(end = 26.dp)
            )
            OrganicLeafChip(
                label = t.source,
                value = sourceValue,
                tone = Aether.Amethyst,
                leanStart = false,
                modifier = Modifier.fillMaxWidth().padding(start = 26.dp)
            )
        }

        HomeFlavor.ORBIT -> Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Instrument readout rows: fixed mono labels, dotted leader, right-anchored values.
            OrbitReadoutRow(label = t.node, value = nodeValue, tone = tone)
            OrbitReadoutRow(label = t.source, value = sourceValue, tone = Aether.InkMuted)
        }

        HomeFlavor.NEBULA -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Weightless HUD identity: airy letterspaced label over a luminous value.
            Text(
                t.node.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.2.sp),
                maxLines = 1
            )
            Text(
                nodeValue,
                color = tone,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            NebulaHairline(tone)
            Text(
                "${t.source.uppercase()}  •  $sourceValue",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HomeFlavor.BLUEPRINT -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Spec-sheet modules: indexed entries with a structural left rule.
            BlueprintSpecRow(index = "01", label = t.node, value = nodeValue, tone = tone)
            BlueprintSpecRow(index = "02", label = t.source, value = sourceValue, tone = Aether.InkMuted)
        }
    }
}

@Composable
private fun OrganicLeafChip(
    label: String,
    value: String,
    tone: Color,
    leanStart: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = if (leanStart) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 22.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 8.dp)
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(tone.copy(alpha = .085f))
            .border(1.dp, tone.copy(alpha = .26f), shape)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = .85f))
        )
        Text(
            label,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value,
            color = Aether.Ink,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OrbitReadoutRow(label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        // Dotted leader between the label and the value: pure dashboard language.
        Canvas(
            Modifier
                .weight(1f)
                .height(1.dp)
        ) {
            drawLine(
                color = tone.copy(alpha = .30f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f))
            )
        }
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NebulaHairline(tone: Color) {
    Canvas(
        Modifier
            .width(180.dp)
            .height(8.dp)
    ) {
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, tone.copy(alpha = .55f), Color.Transparent)
            ),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

@Composable
private fun BlueprintSpecRow(index: String, label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            index,
            color = tone.copy(alpha = .70f),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold
        )
        Box(
            Modifier
                .width(2.dp)
                .height(24.dp)
                .background(tone.copy(alpha = .45f))
        )
        Column(Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.6.sp
                ),
                maxLines = 1
            )
            Text(
                value,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * IP address + country flag + the three actions (copy, refresh, complete details), skinned per
 * style but always the same three actions in the same order.
 *
 * The flag is the country emoji resolved from the public endpoint lookup; when no lookup has
 * landed yet the node's own leading flag glyph is used, and a two-letter country code is the last
 * resort so the row never collapses.
 */
@Composable
internal fun HomeIpRow(
    evidence: HomeEvidence,
    tone: Color,
    actions: HomeActions,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val ipText = when {
        evidence.ip.isNotBlank() -> evidence.ip
        evidence.ipLoading -> t.resolving
        else -> t.unavailable
    }
    val badge = evidence.flag.ifBlank { evidence.countryCode.uppercase() }

    when (flavor) {
        HomeFlavor.ORGANIC -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Aether.VoidElevated.copy(alpha = .88f))
                .border(1.dp, tone.copy(alpha = .24f), RoundedCornerShape(26.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // The flag floats inside its own luminous bubble.
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = .14f))
                    .border(1.dp, tone.copy(alpha = .34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge.ifBlank { "•" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
            Text(
                ipText,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            HomeIpActionCluster(tone, actions, buttonShape = CircleShape, buttonSize = 32.dp)
        }

        HomeFlavor.ORBIT -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    t.ipAddress.uppercase(),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    ),
                    fontWeight = FontWeight.Bold
                )
                if (evidence.location.isNotBlank()) {
                    Text(
                        "• ${evidence.location}",
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Aether.Glass)
                    .border(1.dp, tone.copy(alpha = .22f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (badge.isNotBlank()) {
                    Text(badge, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Text(
                    ipText,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                HomeIpActionCluster(
                    tone,
                    actions,
                    buttonShape = RoundedCornerShape(9.dp),
                    buttonSize = 30.dp
                )
            }
        }

        HomeFlavor.NEBULA -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Aether.VoidElevated.copy(alpha = .55f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(tone.copy(alpha = .34f), Aether.Amethyst.copy(alpha = .26f))
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (badge.isNotBlank()) {
                Text(badge, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Text(
                ipText,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .6.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Ethereal outline-only controls keep the HUD weightless.
            HomeIpActionCluster(
                tone,
                actions,
                buttonShape = CircleShape,
                buttonSize = 30.dp,
                outlined = true
            )
        }

        HomeFlavor.BLUEPRINT -> Box(modifier = modifier) {
            Canvas(Modifier.matchParentSize()) {
                drawCornerBrackets(tone.copy(alpha = .65f), 1.6.dp.toPx(), 9.dp.toPx())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (badge.isNotBlank()) {
                    Text(badge, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        t.ipAddress.uppercase(),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        ipText,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HomeIpActionCluster(
                    tone,
                    actions,
                    buttonShape = RoundedCornerShape(6.dp),
                    buttonSize = 31.dp
                )
            }
        }
    }
}

/** The three IP actions — copy, refresh, complete details — identical order in every skin. */
@Composable
private fun HomeIpActionCluster(
    tone: Color,
    actions: HomeActions,
    buttonShape: androidx.compose.ui.graphics.Shape,
    buttonSize: Dp,
    outlined: Boolean = false
) {
    val t = Tr.now
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        HomeMicroButton(actions.onCopyIp, tone, buttonShape, buttonSize, outlined, t.copyIp) {
            HomeGlyphIcon(HomeGlyph.COPY, Aether.InkMuted, Modifier.size(buttonSize * .48f))
        }
        HomeMicroButton(actions.onRefreshIp, tone, buttonShape, buttonSize, outlined, t.refreshIp) {
            HomeGlyphIcon(HomeGlyph.REFRESH, Aether.InkMuted, Modifier.size(buttonSize * .48f))
        }
        HomeMicroButton(actions.onIpDetails, tone, buttonShape, buttonSize, outlined, t.ipDetails) {
            HomeGlyphIcon(HomeGlyph.MORE, Aether.InkMuted, Modifier.size(buttonSize * .48f))
        }
    }
}

@Composable
private fun HomeMicroButton(
    onClick: () -> Unit,
    tone: Color,
    shape: androidx.compose.ui.graphics.Shape,
    size: Dp,
    outlined: Boolean,
    descriptiveLabel: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (outlined) Color.Transparent else Aether.GlassStrong.copy(alpha = .40f))
            .border(1.dp, tone.copy(alpha = if (outlined) .44f else .22f), shape)
            .semantics { contentDescription = descriptiveLabel }
            .kineticClickable(role = Role.Button, pressScale = .90f, boundedShape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Uptime + the tappable one-shot ping, skinned per style. Tapping the ping element always runs a
 * fresh, real tunnel measurement (never cached) while a session is live.
 */
@Composable
internal fun HomeSessionStats(
    evidence: HomeEvidence,
    tone: Color,
    actions: HomeActions,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)
    val uptimeValue = if (evidence.connectedSinceMs > 0L) uptime else "—"
    val pingValue = homePingLabel(evidence)
    val pingTone = homePingTone(evidence, tone)
    val hint = homePingActionHint(evidence)
    val tappable = homePingTappable(evidence)
    val measuring = evidence.pingState == ConnectionPingState.MEASURING

    when (flavor) {
        HomeFlavor.ORGANIC -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrganicStatCell(
                glyph = HomeGlyph.CLOCK,
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                spinning = evidence.connected,
                modifier = Modifier.weight(1f)
            )
            OrganicStatCell(
                glyph = HomeGlyph.PULSE,
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                spinning = measuring,
                hint = hint,
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = Modifier.weight(1f)
            )
        }

        HomeFlavor.ORBIT -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Digital odometer for the session clock…
            OrbitOdometer(
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                modifier = Modifier.weight(1f)
            )
            // …and a real arc gauge for the tunnel latency.
            OrbitPingGauge(
                evidence = evidence,
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                hint = hint,
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = Modifier.weight(1f)
            )
        }

        HomeFlavor.NEBULA -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NebulaStatRing(
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                rotating = evidence.connected,
                modifier = Modifier.weight(1f)
            )
            NebulaStatRing(
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                rotating = measuring,
                fillFraction = when {
                    evidence.pingState == ConnectionPingState.MEASURED ->
                        1f - (evidence.pingMs / 500f).coerceIn(0f, .92f)
                    else -> 0f
                },
                hint = hint,
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = Modifier.weight(1f)
            )
        }

        HomeFlavor.BLUEPRINT -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BlueprintDataSlab(
                index = "03",
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                modifier = Modifier.weight(1f)
            )
            BlueprintDataSlab(
                index = "04",
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                hint = hint,
                measuring = measuring,
                barFraction = when {
                    evidence.pingState == ConnectionPingState.MEASURED ->
                        (evidence.pingMs / 500f).coerceIn(.05f, 1f)
                    else -> 0f
                },
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OrganicStatCell(
    glyph: HomeGlyph,
    label: String,
    value: String,
    tone: Color,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    hint: String = "",
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 26.dp)
    val spin = MarbleMotion.current.loop(5_200)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .90f))
            .border(1.dp, tone.copy(alpha = .28f), shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            )
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    // A living dashed rim slowly orbits the icon while the cell is active.
                    Canvas(Modifier.matchParentSize()) {
                        if (spinning) {
                            rotate(spin * 360f) {
                                drawCircle(
                                    color = tone.copy(alpha = .55f),
                                    radius = size.minDimension / 2f,
                                    style = Stroke(
                                        width = 1.4.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))
                                    )
                                )
                            }
                        } else {
                            drawCircle(
                                color = tone.copy(alpha = .30f),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                    }
                    HomeGlyphIcon(glyph, tone, Modifier.size(10.dp))
                }
                Text(
                    label,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Text(
                value,
                color = tone,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun OrbitOdometer(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Aether.Glass)
            .border(1.dp, tone.copy(alpha = .22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            HomeGlyphIcon(HomeGlyph.CLOCK, Aether.InkFaint, Modifier.size(11.dp))
            Text(
                label.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Segment baseline under the digits, like a cockpit LCD.
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            drawLine(
                color = tone.copy(alpha = .40f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 5f))
            )
        }
    }
}

@Composable
private fun OrbitPingGauge(
    evidence: HomeEvidence,
    label: String,
    value: String,
    tone: Color,
    hint: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    val sweepPhase = MarbleMotion.current.loop(1_100)
    // The needle physically travels to the measured latency (0..500 ms full-scale).
    val needle by animateFloatAsState(
        targetValue = when {
            evidence.pingState == ConnectionPingState.MEASURED ->
                (evidence.pingMs / 500f).coerceIn(0f, 1f)
            else -> 0f
        },
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "orbit-ping-needle"
    )
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Aether.Glass)
            .border(1.dp, tone.copy(alpha = .26f), shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            )
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Canvas(Modifier.size(46.dp)) {
            val stroke = 3.4.dp.toPx()
            val inset = stroke
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            // Dial track.
            drawArc(
                color = tone.copy(alpha = .18f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Dial graduations.
            repeat(7) { index ->
                val angle = (135f + index * 45f) * PI.toFloat() / 180f
                val r1 = size.minDimension / 2f - stroke * 2.1f
                val r2 = size.minDimension / 2f - stroke * 1.2f
                val c = Offset(size.width / 2f, size.height / 2f)
                drawLine(
                    color = tone.copy(alpha = .40f),
                    start = Offset(c.x + cos(angle) * r1, c.y + sin(angle) * r1),
                    end = Offset(c.x + cos(angle) * r2, c.y + sin(angle) * r2),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
            if (measuring) {
                // Radar-style sweep while the probe is in flight.
                drawArc(
                    color = tone,
                    startAngle = 135f + sweepPhase * 270f,
                    sweepAngle = 48f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            } else if (needle > 0f) {
                drawArc(
                    color = tone,
                    startAngle = 135f,
                    sweepAngle = 270f * needle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                label.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                value,
                color = tone,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NebulaStatRing(
    label: String,
    value: String,
    tone: Color,
    rotating: Boolean,
    modifier: Modifier = Modifier,
    fillFraction: Float = 0f,
    hint: String = "",
    onClick: (() -> Unit)? = null
) {
    val spin = MarbleMotion.current.loop(7_400)
    val fastSpin = MarbleMotion.current.loop(1_200)
    val fill by animateFloatAsState(
        targetValue = fillFraction,
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "nebula-ring-fill"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(
                    role = Role.Button,
                    boundedShape = RoundedCornerShape(22.dp),
                    onClick = onClick
                )
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(92.dp)) {
                val stroke = 2.2.dp.toPx()
                val r = size.minDimension / 2f - stroke
                // Ethereal base ring.
                drawCircle(
                    color = tone.copy(alpha = .16f),
                    radius = r,
                    style = Stroke(width = stroke)
                )
                // Slowly orbiting dash constellation.
                rotate(if (rotating) fastSpin * 360f else spin * 360f) {
                    drawCircle(
                        color = tone.copy(alpha = if (rotating) .75f else .35f),
                        radius = r,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 26f))
                        )
                    )
                }
                // The measured band fills the ring like an aurora settling.
                if (fill > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(tone.copy(alpha = .10f), tone, tone.copy(alpha = .10f))
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * fill,
                        useCenter = false,
                        topLeft = Offset(stroke, stroke),
                        size = Size(size.width - stroke * 2, size.height - stroke * 2),
                        style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    color = tone,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (hint.isNotBlank()) {
                    Text(
                        hint,
                        color = Aether.Cyan,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            label.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun BlueprintDataSlab(
    index: String,
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
    hint: String = "",
    measuring: Boolean = false,
    barFraction: Float = -1f,
    onClick: (() -> Unit)? = null
) {
    val scan = MarbleMotion.current.loop(1_400)
    val bar by animateFloatAsState(
        targetValue = barFraction.coerceAtLeast(0f),
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "blueprint-slab-bar"
    )
    Box(
        modifier = modifier
            .background(Aether.VoidElevated)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(
                    role = Role.Button,
                    boundedShape = RoundedCornerShape(4.dp),
                    onClick = onClick
                )
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCornerBrackets(tone.copy(alpha = .70f), 1.6.dp.toPx(), 9.dp.toPx())
        }
        Column(
            Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    index,
                    color = tone.copy(alpha = .70f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    label.uppercase(),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.6.sp
                    ),
                    maxLines = 1
                )
            }
            Text(
                value,
                color = tone,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Engineering scale: measured latency as a dimension bar on a graded rule.
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val y = size.height / 2f
                drawLine(tone.copy(alpha = .22f), Offset(0f, y), Offset(size.width, y), 1.2.dp.toPx())
                repeat(11) { i ->
                    val x = size.width * i / 10f
                    drawLine(
                        tone.copy(alpha = .34f),
                        Offset(x, y - 2.dp.toPx()),
                        Offset(x, y + 2.dp.toPx()),
                        1.dp.toPx()
                    )
                }
                when {
                    measuring -> {
                        // A survey cursor sweeps the rule while the probe runs.
                        val x = size.width * scan
                        drawLine(tone, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                    }
                    bar > 0f -> drawLine(
                        color = tone,
                        start = Offset(0f, y),
                        end = Offset(size.width * bar, y),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            if (hint.isNotBlank()) {
                Text(
                    hint.uppercase(),
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

private fun DrawScope.drawCornerBrackets(color: Color, stroke: Float, length: Float) {
    val w = size.width
    val h = size.height
    val l = length
    // Four technical corner brackets — the drafting-table framing device.
    drawPath(Path().apply { moveTo(0f, l); lineTo(0f, 0f); lineTo(l, 0f) }, color, style = Stroke(stroke))
    drawPath(Path().apply { moveTo(w - l, 0f); lineTo(w, 0f); lineTo(w, l) }, color, style = Stroke(stroke))
    drawPath(Path().apply { moveTo(w, h - l); lineTo(w, h); lineTo(w - l, h) }, color, style = Stroke(stroke))
    drawPath(Path().apply { moveTo(l, h); lineTo(0f, h); lineTo(0f, h - l) }, color, style = Stroke(stroke))
}

/**
 * The connection control.
 *
 * Deliberately carries NO quality ring: link quality lives in its own readouts, and wrapping the
 * primary action in a score made the button's own state ambiguous. Each flavor decorates the
 * control with its own vocabulary — membranes, dial bezels, aurora rings or drafting ticks — but
 * state (idle / connecting / connected / blocked) always reads identically.
 */
@Composable
internal fun HomePowerControl(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
    haloBrush: Brush? = null
) {
    val label = homeActionLabel(evidence)
    val animatedTone by animateColorAsState(
        targetValue = tone,
        animationSpec = MarbleMotionSpecs.Color,
        label = "home-power-tone"
    )
    val motion = MarbleMotion.current
    val pulse = if (evidence.connecting) .70f + motion.breathe(1_150) * .30f else 1f
    val sweep = if (evidence.connecting) motion.loop(950) * 360f else 0f
    val calm = motion.breathe(3_800)
    val slowSpin = motion.loop(11_000)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .24f),
                    spotColor = animatedTone.copy(alpha = .34f)
                )
                .clip(CircleShape)
                .background(
                    haloBrush ?: Brush.radialGradient(
                        listOf(
                            animatedTone.copy(alpha = .20f * pulse),
                            animatedTone.copy(alpha = .07f * pulse)
                        )
                    )
                )
                .kineticClickable(
                    role = Role.Button,
                    pressScale = .95f,
                    boundedShape = CircleShape,
                    onClick = onToggle
                )
                .semantics { contentDescription = "$label connection button" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize().padding(10.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // Flavor-specific bezel decoration — never a quality score.
                when (flavor) {
                    HomeFlavor.ORGANIC -> {
                        // A soft breathing membrane hugs the control.
                        drawCircle(
                            color = animatedTone.copy(alpha = .16f + .10f * calm),
                            radius = r * (.88f + .05f * calm),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                        if (evidence.connected) {
                            // An expanding, fading ripple: the organism is alive.
                            val ripple = motion.loop(2_600)
                            drawCircle(
                                color = animatedTone.copy(alpha = .34f * (1f - ripple)),
                                radius = r * (.62f + .38f * ripple),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }
                    }
                    HomeFlavor.ORBIT -> {
                        // Instrument bezel: 36 graduation ticks around the dial.
                        repeat(36) { index ->
                            val a = index * 10f * PI.toFloat() / 180f
                            val major = index % 9 == 0
                            val r1 = r * (if (major) .86f else .92f)
                            drawLine(
                                color = animatedTone.copy(alpha = if (major) .55f else .26f),
                                start = Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                                end = Offset(c.x + cos(a) * r * .97f, c.y + sin(a) * r * .97f),
                                strokeWidth = (if (major) 1.8f else 1.1f).dp.toPx()
                            )
                        }
                        if (evidence.connected) {
                            // Two counter-rotating settled arcs.
                            rotate(slowSpin * 360f, pivot = c) {
                                drawArc(
                                    color = animatedTone.copy(alpha = .55f),
                                    startAngle = 0f,
                                    sweepAngle = 84f,
                                    useCenter = false,
                                    topLeft = Offset(c.x - r * .80f, c.y - r * .80f),
                                    size = Size(r * 1.6f, r * 1.6f),
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            rotate(-slowSpin * 360f, pivot = c) {
                                drawArc(
                                    color = animatedTone.copy(alpha = .35f),
                                    startAngle = 180f,
                                    sweepAngle = 84f,
                                    useCenter = false,
                                    topLeft = Offset(c.x - r * .70f, c.y - r * .70f),
                                    size = Size(r * 1.4f, r * 1.4f),
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                    HomeFlavor.NEBULA -> {
                        // Aurora bezel: a slowly rotating spectral sweep.
                        rotate(slowSpin * 360f, pivot = c) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        animatedTone.copy(alpha = .55f),
                                        Aether.Amethyst.copy(alpha = .40f),
                                        Color.Transparent
                                    ),
                                    center = c
                                ),
                                radius = r * .92f,
                                center = c,
                                style = Stroke(width = 2.4.dp.toPx())
                            )
                        }
                    }
                    HomeFlavor.BLUEPRINT -> {
                        // Drafting crosshair ticks at the four cardinal points.
                        listOf(0f, 90f, 180f, 270f).forEach { deg ->
                            val a = deg * PI.toFloat() / 180f
                            drawLine(
                                color = animatedTone.copy(alpha = .60f),
                                start = Offset(c.x + cos(a) * r * .84f, c.y + sin(a) * r * .84f),
                                end = Offset(c.x + cos(a) * r * .98f, c.y + sin(a) * r * .98f),
                                strokeWidth = 1.8.dp.toPx()
                            )
                        }
                        drawCircle(
                            color = animatedTone.copy(alpha = .22f),
                            radius = r * .91f,
                            center = c,
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 9f))
                            )
                        )
                    }
                }

                // Shared state ring: connecting shows indeterminate motion; connected a settled rim.
                when {
                    evidence.connecting -> drawArc(
                        color = animatedTone,
                        startAngle = -90f + sweep,
                        sweepAngle = 104f,
                        useCenter = false,
                        topLeft = Offset(c.x - r * .74f, c.y - r * .74f),
                        size = Size(r * 1.48f, r * 1.48f),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    evidence.connected -> drawCircle(
                        color = animatedTone.copy(alpha = .70f),
                        radius = r * .74f,
                        center = c,
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                }
            }
            HomeGlyphIcon(
                when {
                    evidence.connected -> HomeGlyph.CHECK
                    evidence.blocked -> HomeGlyph.RESET
                    else -> HomeGlyph.POWER
                },
                animatedTone,
                Modifier.size(diameter * .24f)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            label,
            color = animatedTone,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun HomeStatusHeadline(
    evidence: HomeEvidence,
    @Suppress("UNUSED_PARAMETER") tone: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center
) {
    Text(
        homeStatusText(evidence),
        color = Aether.Ink,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private fun formatBps(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f kB/s".format(bps / 1_000.0)
    bps > 0 -> "$bps B/s"
    else -> "—"
}

// ---------------------------------------------------------------------------------------------
// Style 1 — Organic Bioluminescence: "the Abyss"
// ---------------------------------------------------------------------------------------------

/**
 * A full-screen abyssal dive. Light rays fall from the surface, plankton motes drift upward, a
 * living seed breathes on a fluid boundary and nerve tendrils carry data into the deep. Evidence
 * floats in asymmetric leaf cells; the ping and uptime live in twin bio-cells with orbiting rims.
 */
@Composable
internal fun HomeStyleBioluminescent(
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    val tone = homeTone(evidence)
    val seedGlow = if (evidence.connected) Color(0xFF9BE8B6) else Color(0xFFB9C7F0)
    val tendrilTone = if (evidence.connected) Color(0xFFB9A7E8) else Aether.InkFaint
    val motion = MarbleMotion.current
    val phase = motion.loop(9_000)
    val drift = motion.loop(16_000)
    val breathe = motion.breathe(3_400)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .40f).coerceIn(260.dp, 380.dp)

        // The whole viewport is the ocean — not a banner above cards.
        Canvas(Modifier.matchParentSize()) {
            drawAbyssBackdrop(seedGlow, tendrilTone, drift, breathe, evidence.connected)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                .padding(bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                contentAlignment = Alignment.TopCenter
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawOrganicSurface(seedGlow, tendrilTone, phase, breathe, evidence.connected)
                }
                HomePowerControl(
                    evidence = evidence,
                    tone = tone,
                    onToggle = actions.onToggleConnection,
                    flavor = HomeFlavor.ORGANIC,
                    diameter = 148.dp,
                    haloBrush = Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = .92f),
                            seedGlow.copy(alpha = .55f + .18f * breathe),
                            seedGlow.copy(alpha = .16f)
                        )
                    ),
                    modifier = Modifier.padding(top = heroHeight * .16f)
                )
            }

            HomeStatusHeadline(evidence, tone)

            HomeIdentityBlock(
                evidence,
                tone,
                HomeFlavor.ORGANIC,
                Modifier.fillMaxWidth()
            )

            HomeIpRow(evidence, tone, actions, HomeFlavor.ORGANIC, Modifier.fillMaxWidth())

            HomeSessionStats(evidence, tone, actions, HomeFlavor.ORGANIC, Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
        }
    }
}

/** Everything behind the content: rays, motes and the deep gradient. */
private fun DrawScope.drawAbyssBackdrop(
    glow: Color,
    tendril: Color,
    drift: Float,
    breathe: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Depth gradient — brighter towards the surface, void below.
    drawRect(
        Brush.verticalGradient(
            listOf(
                glow.copy(alpha = .10f + .04f * breathe),
                Color.Transparent,
                tendril.copy(alpha = .05f)
            )
        )
    )

    // God-rays falling from the surface, slowly panning.
    repeat(4) { index ->
        val seed = hash01(index * 11 + 3)
        val x = w * ((seed + drift * .18f) % 1f)
        val width = w * (.05f + .06f * hash01(index * 7 + 1))
        val ray = Path().apply {
            moveTo(x, 0f)
            lineTo(x + width, 0f)
            lineTo(x + width * 2.6f, h * .62f)
            lineTo(x + width * 1.4f, h * .62f)
            close()
        }
        drawPath(
            ray,
            Brush.verticalGradient(
                listOf(glow.copy(alpha = .07f + .05f * breathe), Color.Transparent),
                startY = 0f,
                endY = h * .62f
            )
        )
    }

    // Plankton motes drifting upward; each has its own deterministic lane, speed and size.
    repeat(26) { index ->
        val lane = hash01(index * 13 + 5)
        val speed = .4f + hash01(index * 17 + 2) * .9f
        val progress = (drift * speed + hash01(index * 19 + 7)) % 1f
        val x = w * lane + sin((progress * 4f + index) * PI.toFloat()) * w * .015f
        val y = h * (1f - progress)
        val fade = sin(progress * PI.toFloat())
        drawCircle(
            color = (if (index % 3 == 0) tendril else glow)
                .copy(alpha = (if (connected) .34f else .16f) * fade),
            radius = (.8f + 1.8f * hash01(index * 23 + 11)).dp.toPx(),
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawOrganicSurface(
    glow: Color,
    tendril: Color,
    phase: Float,
    breathe: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Fluid surface the seed rests on — two interfering waves, not one static curve.
    val surfaceY = h * .58f
    val waveA = sin(phase * 2f * PI.toFloat()) * h * .012f
    val waveB = cos(phase * 4f * PI.toFloat()) * h * .008f
    val fluid = Path().apply {
        moveTo(0f, surfaceY + waveA)
        cubicTo(
            w * .25f, surfaceY - h * .07f + waveB,
            w * .70f, surfaceY + h * .07f - waveA,
            w, surfaceY - h * .02f + waveB
        )
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        fluid,
        Brush.verticalGradient(
            listOf(glow.copy(alpha = .16f), Color.Transparent),
            startY = surfaceY - h * .08f,
            endY = h
        )
    )
    drawPath(fluid, color = glow.copy(alpha = .30f), style = Stroke(width = 1.6.dp.toPx()))
    // A second, fainter offset crest gives the surface real depth.
    drawPath(
        Path().apply {
            moveTo(0f, surfaceY + h * .035f - waveB)
            cubicTo(
                w * .30f, surfaceY - h * .03f - waveA,
                w * .68f, surfaceY + h * .10f + waveB,
                w, surfaceY + h * .015f - waveA
            )
        },
        color = glow.copy(alpha = .14f),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Nerve-like tendrils flowing downward: data leaving the seed.
    val strands = 5
    repeat(strands) { index ->
        val origin = w * (.30f + .10f * index)
        val sway = sin((phase * 2f * PI + index).toFloat()) * w * .035f
        val path = Path().apply {
            moveTo(origin, surfaceY - h * .02f)
            cubicTo(
                origin + sway, h * .74f,
                origin - sway, h * .86f,
                origin + sway * .5f, h
            )
        }
        drawPath(
            path,
            color = tendril.copy(alpha = if (connected) .34f else .16f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
        if (connected) {
            // Two travelling luminous nodes per strand visualise the live flow.
            repeat(2) { pulseIndex ->
                val travel = ((phase + index / strands.toFloat() + pulseIndex * .5f) % 1f)
                drawCircle(
                    color = glow.copy(alpha = .70f * (1f - travel)),
                    radius = (2.6f + breathe).dp.toPx(),
                    center = Offset(origin + sway * travel, surfaceY + (h - surfaceY) * travel)
                )
            }
        }
    }

    // Ambient bloom behind the seed.
    val center = Offset(w * .5f, h * .30f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(glow.copy(alpha = .22f + .08f * breathe), Color.Transparent),
            center = center,
            radius = w * .48f
        ),
        radius = w * .48f,
        center = center
    )
}

// ---------------------------------------------------------------------------------------------
// Style 2 — Cosmic Orbit: "the Command Deck"
// ---------------------------------------------------------------------------------------------

/**
 * A pilot's console over a live starfield: the orbital system is the hero instrument, identity is
 * a readout table with dotted leaders, the IP sits on a data bus, and uptime/ping are a digital
 * odometer and a real arc gauge. A slim action rail rides the trailing edge.
 */
@Composable
internal fun HomeStyleCosmicOrbit(
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    val tone = homeTone(evidence)
    val gold = Color(0xFFE7C36B)
    val deep = Color(0xFF0B1B3A)
    val motion = MarbleMotion.current
    val phase = motion.loop(14_000)
    val twinkle = motion.loop(6_000)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .34f).coerceIn(210.dp, 300.dp)

        // Full-viewport starfield behind the whole console.
        Canvas(Modifier.matchParentSize()) {
            drawStarfield(gold, tone, twinkle, evidence.connected)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp)
                .padding(bottom = bottomClearance),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                PrismPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accent = gold,
                    selected = evidence.connected,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(deep.copy(alpha = .30f), Color.Transparent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            drawSolarSystem(gold, tone, phase, evidence.connected)
                        }
                        HomePowerControl(
                            evidence = evidence,
                            tone = tone,
                            onToggle = actions.onToggleConnection,
                            flavor = HomeFlavor.ORBIT,
                            diameter = 118.dp,
                            haloBrush = Brush.radialGradient(
                                listOf(
                                    gold.copy(alpha = .55f),
                                    gold.copy(alpha = .16f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                    HomeStatusHeadline(evidence, tone, align = TextAlign.Start)
                    HomeIdentityBlock(evidence, tone, HomeFlavor.ORBIT, Modifier.fillMaxWidth())
                }

                PrismPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accent = tone,
                    contentPadding = PaddingValues(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            Tr.now.networkSpeed,
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${Tr.now.download} ${formatBps(evidence.downBps)}  •  " +
                                "${Tr.now.upload} ${formatBps(evidence.upBps)}",
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        drawSpeedGraph(tone, gold, phase, evidence.downBps, evidence.upBps)
                    }
                    HomeIpRow(evidence, tone, actions, HomeFlavor.ORBIT, Modifier.fillMaxWidth())
                }

                HomeSessionStats(evidence, tone, actions, HomeFlavor.ORBIT, Modifier.fillMaxWidth())
            }

            // Side navigation rail on the trailing edge.
            Column(
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Aether.VoidElevated)
                    .border(1.dp, Aether.BarGlassBorder, RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrismIconButton(
                    onClick = actions.onLibrary,
                    tone = gold,
                    size = 34.dp,
                    descriptiveLabel = Tr.now.library
                ) { HomeGlyphIcon(HomeGlyph.LIBRARY, Aether.InkMuted, Modifier.size(16.dp)) }
                PrismIconButton(
                    onClick = actions.onTestPing,
                    tone = tone,
                    size = 34.dp,
                    enabled = homePingTappable(evidence),
                    descriptiveLabel = Tr.now.testPing
                ) { HomeGlyphIcon(HomeGlyph.PULSE, Aether.InkMuted, Modifier.size(16.dp)) }
                PrismIconButton(
                    onClick = actions.onIpDetails,
                    tone = tone,
                    size = 34.dp,
                    descriptiveLabel = Tr.now.ipDetails
                ) { HomeGlyphIcon(HomeGlyph.MORE, Aether.InkMuted, Modifier.size(16.dp)) }
            }
        }
    }
}

/** The full-viewport star layer behind the console. */
private fun DrawScope.drawStarfield(gold: Color, accent: Color, twinkle: Float, connected: Boolean) {
    val w = size.width
    val h = size.height
    repeat(48) { index ->
        val x = w * hash01(index * 3 + 1)
        val y = h * hash01(index * 5 + 2)
        val shimmer = .5f + .5f * sin((twinkle + hash01(index * 7 + 3)) * 2f * PI.toFloat())
        val bright = hash01(index * 11 + 4) > .82f
        drawCircle(
            color = (if (bright) gold else accent)
                .copy(alpha = (if (connected) .34f else .20f) * shimmer + .05f),
            radius = (if (bright) 1.7f else 1.0f).dp.toPx(),
            center = Offset(x, y)
        )
    }
}

private fun DrawScope.drawSolarSystem(gold: Color, accent: Color, phase: Float, connected: Boolean) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val unit = min(size.width, size.height) / 2f

    drawCircle(
        brush = Brush.radialGradient(
            listOf(gold.copy(alpha = .55f), Color.Transparent),
            center = center,
            radius = unit * .40f
        ),
        radius = unit * .40f,
        center = center
    )

    val orbits = listOf(.46f, .66f, .88f)
    orbits.forEachIndexed { index, radius ->
        val r = unit * radius
        drawCircle(
            color = accent.copy(alpha = if (connected) .30f else .16f),
            radius = r,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        val speed = 1f + index * .55f
        val angle = ((phase * speed) % 1f) * 2f * PI.toFloat()
        val planet = Offset(
            center.x + cos(angle) * r,
            center.y + sin(angle) * r * .42f
        )
        // Comet tail: fading ghosts trail each body along its orbit.
        repeat(5) { ghost ->
            val trailAngle = angle - (ghost + 1) * .10f
            drawCircle(
                color = (if (index == 1) gold else accent).copy(alpha = .30f * (1f - ghost / 5f)),
                radius = (2.6f - index * .4f - ghost * .35f).coerceAtLeast(.8f).dp.toPx(),
                center = Offset(
                    center.x + cos(trailAngle) * r,
                    center.y + sin(trailAngle) * r * .42f
                )
            )
        }
        drawCircle(
            color = if (index == 1) gold else accent,
            radius = (4f - index * .6f).dp.toPx(),
            center = planet
        )
    }
}

private fun DrawScope.drawSpeedGraph(tone: Color, gold: Color, phase: Float, down: Long, up: Long) {
    val w = size.width
    val h = size.height
    val active = down + up > 0
    val peak = if (active) .82f else .35f
    // The waveform breathes with the shared clock so the console always feels live.
    val wobble = sin(phase * 2f * PI.toFloat()) * h * .05f
    val curve = Path().apply {
        moveTo(0f, h)
        cubicTo(
            w * .18f, h * .92f + wobble,
            w * .30f, h * (1f - peak * .55f) - wobble,
            w * .46f, h * (1f - peak)
        )
        cubicTo(
            w * .62f, h * (1f - peak * 1.02f) + wobble,
            w * .74f, h * .70f - wobble,
            w, h * .58f
        )
    }
    val filled = Path().apply {
        addPath(curve)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        filled,
        Brush.verticalGradient(listOf(tone.copy(alpha = .26f), Color.Transparent))
    )
    drawPath(curve, color = tone, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    val head = Offset(w * .46f, h * (1f - peak))
    drawCircle(gold, radius = 3.6.dp.toPx(), center = head)
    drawCircle(color = gold.copy(alpha = .28f), radius = 8.dp.toPx(), center = head)
}

// ---------------------------------------------------------------------------------------------
// Style 3 — Cosmic Immersion: "the Nebula HUD"
// ---------------------------------------------------------------------------------------------

/**
 * Edge-to-edge deep space. Three parallax star layers, aurora ribbons across the sky and the
 * energy flower blooming below. Evidence is chrome-less: letterspaced HUD identity, a glass IP
 * strip and twin stat rings whose motion is the interface.
 */
@Composable
internal fun HomeStyleCosmicImmersion(
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    val tone = homeTone(evidence)
    val cyan = Color(0xFF57E0FF)
    val violet = Color(0xFFB08CFF)
    val motion = MarbleMotion.current
    val orbitPhase = motion.loop(16_000)
    val auroraPhase = motion.loop(12_000)
    val bloom = motion.breathe(4_600)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val skyClearance = (maxHeight * .10f).coerceIn(40.dp, 110.dp)

        Canvas(Modifier.matchParentSize()) {
            drawImmersiveCosmos(cyan, violet, orbitPhase, auroraPhase, bloom, evidence.connected)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(skyClearance))

            HomePowerControl(
                evidence = evidence,
                tone = tone,
                onToggle = actions.onToggleConnection,
                flavor = HomeFlavor.NEBULA,
                diameter = 134.dp,
                haloBrush = Brush.radialGradient(
                    listOf(
                        cyan.copy(alpha = .30f + .12f * bloom),
                        violet.copy(alpha = .18f),
                        Color.Transparent
                    )
                )
            )

            Text(
                homeStatusText(evidence).uppercase(),
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.2.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HomeIdentityBlock(evidence, tone, HomeFlavor.NEBULA, Modifier.fillMaxWidth())

            HomeIpRow(evidence, tone, actions, HomeFlavor.NEBULA, Modifier.fillMaxWidth())

            HomeSessionStats(evidence, tone, actions, HomeFlavor.NEBULA, Modifier.fillMaxWidth())

            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun DrawScope.drawImmersiveCosmos(
    cyan: Color,
    violet: Color,
    phase: Float,
    aurora: Float,
    bloom: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Nebula wash behind everything.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(violet.copy(alpha = .20f), Color.Transparent),
            center = Offset(w * .5f, h * .20f),
            radius = w * .85f
        ),
        radius = w * .85f,
        center = Offset(w * .5f, h * .20f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(cyan.copy(alpha = .08f), Color.Transparent),
            center = Offset(w * .82f, h * .55f),
            radius = w * .55f
        ),
        radius = w * .55f,
        center = Offset(w * .82f, h * .55f)
    )

    // Three parallax star layers: deeper layers drift slower and dimmer.
    repeat(3) { layer ->
        val speed = .04f + layer * .05f
        val alphaBase = .10f + layer * .10f
        repeat(18) { index ->
            val seed = layer * 100 + index
            val x = (w * hash01(seed * 3 + 1) + phase * speed * w) % w
            val y = h * hash01(seed * 5 + 2)
            drawCircle(
                color = (if (index % 4 == 0) violet else cyan)
                    .copy(alpha = alphaBase * (if (connected) 1.3f else 1f)),
                radius = (.7f + layer * .5f).dp.toPx(),
                center = Offset(x, y)
            )
        }
    }

    // Aurora ribbons: two sine sheets flowing across the upper sky.
    repeat(2) { ribbon ->
        val baseY = h * (.16f + ribbon * .09f)
        val amp = h * (.028f - ribbon * .008f)
        val path = Path()
        var x = 0f
        while (x <= w) {
            val y = baseY + sin((x / w * 4f + aurora * 2f + ribbon) * PI.toFloat()) * amp
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += w / 42f
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    (if (ribbon == 0) cyan else violet).copy(alpha = if (connected) .34f else .18f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = (5f - ribbon * 2f).dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // Upper half: luminous orbits and floating planets.
    val skyCenter = Offset(w * .5f, h * .20f)
    listOf(.42f, .62f, .84f).forEachIndexed { index, radius ->
        val r = w * radius
        drawCircle(
            color = cyan.copy(alpha = if (connected) .22f else .12f),
            radius = r,
            center = skyCenter,
            style = Stroke(width = 1.dp.toPx())
        )
        val angle = ((phase * (1f + index * .4f)) % 1f) * 2f * PI.toFloat()
        drawCircle(
            color = if (index % 2 == 0) cyan else violet,
            radius = (3.4f - index * .5f).dp.toPx(),
            center = Offset(skyCenter.x + cos(angle) * r, skyCenter.y + sin(angle) * r * .34f)
        )
    }

    // Lower half: the cosmic energy flower.
    val flower = Offset(w * .5f, h * .84f)
    val petals = 12
    val reach = w * (.34f + .04f * bloom)
    repeat(petals) { index ->
        val angle = index * 2f * PI.toFloat() / petals + phase * .6f
        val tip = Offset(flower.x + cos(angle) * reach, flower.y + sin(angle) * reach * .58f)
        val ctrl = Offset(
            flower.x + cos(angle + .34f) * reach * .55f,
            flower.y + sin(angle + .34f) * reach * .34f
        )
        val petal = Path().apply {
            moveTo(flower.x, flower.y)
            quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
            quadraticTo(
                flower.x + cos(angle - .34f) * reach * .55f,
                flower.y + sin(angle - .34f) * reach * .34f,
                flower.x,
                flower.y
            )
        }
        drawPath(
            petal,
            color = (if (index % 2 == 0) cyan else violet)
                .copy(alpha = if (connected) .26f else .13f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(cyan.copy(alpha = .38f + .12f * bloom), Color.Transparent),
            center = flower,
            radius = w * .26f
        ),
        radius = w * .26f,
        center = flower
    )
}

// ---------------------------------------------------------------------------------------------
// Style 4 — Minimalist Parametric Architecture: "the Blueprint"
// ---------------------------------------------------------------------------------------------

/**
 * A full-screen drafting table: a live scanning grid, an isometric glass structure lit from
 * within, dimension lines and corner brackets. Evidence reads as an indexed spec sheet; the ping
 * is measured on an engineering rule that a survey cursor sweeps during the probe.
 */
@Composable
internal fun HomeStyleParametric(
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    val tone = homeTone(evidence)
    val warm = Color(0xFFE9B872)
    val structure = Aether.InkMuted
    val motion = MarbleMotion.current
    val glowPulse = motion.breathe(5_200)
    val scan = motion.loop(7_000)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .36f).coerceIn(210.dp, 320.dp)

        // The drafting grid owns the entire viewport, with a slow scan line sweeping it.
        Canvas(Modifier.matchParentSize()) {
            drawBlueprintField(structure, warm, scan)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                .padding(bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Aether.VoidElevated.copy(alpha = .82f))
                    .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawParametricStructure(structure, warm, glowPulse, scan, evidence.connected)
                }
                HomePowerControl(
                    evidence = evidence,
                    tone = tone,
                    onToggle = actions.onToggleConnection,
                    flavor = HomeFlavor.BLUEPRINT,
                    diameter = 120.dp,
                    haloBrush = Brush.radialGradient(
                        listOf(
                            warm.copy(alpha = .30f + .10f * glowPulse),
                            Color.Transparent
                        )
                    )
                )
            }

            HomeStatusHeadline(evidence, tone, align = TextAlign.Start)

            // Precise geometric modules: one fact per module, clear separation between modules.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Aether.VoidElevated.copy(alpha = .88f))
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCornerBrackets(tone.copy(alpha = .60f), 1.6.dp.toPx(), 9.dp.toPx())
                }
                HomeIdentityBlock(
                    evidence,
                    tone,
                    HomeFlavor.BLUEPRINT,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 11.dp)
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Aether.VoidElevated.copy(alpha = .88f))
            ) {
                Column {
                    HomeIpRow(evidence, warm, actions, HomeFlavor.BLUEPRINT, Modifier.fillMaxWidth())
                    if (evidence.location.isNotBlank()) {
                        Text(
                            evidence.location,
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 9.dp)
                        )
                    }
                }
            }

            HomeSessionStats(evidence, tone, actions, HomeFlavor.BLUEPRINT, Modifier.fillMaxWidth())
        }
    }
}

/** Full-viewport drafting grid + the sweeping scan line. */
private fun DrawScope.drawBlueprintField(structure: Color, warm: Color, scan: Float) {
    val w = size.width
    val h = size.height
    val step = w / 14f

    var x = step
    while (x < w) {
        drawLine(structure.copy(alpha = .05f), Offset(x, 0f), Offset(x, h), 1f)
        x += step
    }
    var y = step
    while (y < h) {
        drawLine(structure.copy(alpha = .05f), Offset(0f, y), Offset(w, y), 1f)
        y += step
    }

    // The plotter's scan line travels the sheet with a soft luminous wake.
    val scanY = h * scan
    if (scanY > 2f) {
        val wakeTop = (scanY - h * .10f).coerceAtLeast(0f)
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, warm.copy(alpha = .05f)),
                startY = wakeTop,
                endY = scanY
            ),
            topLeft = Offset(0f, wakeTop),
            size = Size(w, scanY - wakeTop)
        )
        drawLine(warm.copy(alpha = .18f), Offset(0f, scanY), Offset(w, scanY), 1.2.dp.toPx())
    }
}

private fun DrawScope.drawParametricStructure(
    structure: Color,
    warm: Color,
    pulse: Float,
    scan: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Fine inner grid, denser than the sheet behind it.
    val step = w / 12f
    var x = step
    while (x < w) {
        drawLine(structure.copy(alpha = .06f), Offset(x, 0f), Offset(x, h), 1f)
        x += step
    }
    var y = step
    while (y < h) {
        drawLine(structure.copy(alpha = .06f), Offset(0f, y), Offset(w, y), 1f)
        y += step
    }

    // Isometric volume: top rhombus + two side faces, glowing from within.
    val cx = w * .5f
    val cy = h * .52f
    val halfW = w * .26f
    val halfD = h * .16f
    val height = h * .24f

    val top = Path().apply {
        moveTo(cx, cy - halfD - height)
        lineTo(cx + halfW, cy - height)
        lineTo(cx, cy + halfD - height)
        lineTo(cx - halfW, cy - height)
        close()
    }
    val left = Path().apply {
        moveTo(cx - halfW, cy - height)
        lineTo(cx, cy + halfD - height)
        lineTo(cx, cy + halfD)
        lineTo(cx - halfW, cy)
        close()
    }
    val right = Path().apply {
        moveTo(cx + halfW, cy - height)
        lineTo(cx, cy + halfD - height)
        lineTo(cx, cy + halfD)
        lineTo(cx + halfW, cy)
        close()
    }

    val innerLight = warm.copy(alpha = (if (connected) .26f else .12f) + .06f * pulse)
    drawPath(left, color = innerLight)
    drawPath(right, color = innerLight.copy(alpha = innerLight.alpha * .70f))
    drawPath(top, color = structure.copy(alpha = .08f))

    listOf(top, left, right).forEach {
        drawPath(it, color = structure.copy(alpha = .38f), style = Stroke(width = 1.4.dp.toPx()))
    }

    // Floor slabs — the modular language of the facade.
    repeat(3) { index ->
        val fy = cy - height + (height / 3f) * (index + 1)
        drawLine(
            color = structure.copy(alpha = .22f),
            start = Offset(cx - halfW, fy),
            end = Offset(cx, fy + halfD),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = structure.copy(alpha = .22f),
            start = Offset(cx + halfW, fy),
            end = Offset(cx, fy + halfD),
            strokeWidth = 1.dp.toPx()
        )
    }

    // Elevator light: a warm cell rides the left facade while a session is live.
    if (connected) {
        val lift = (sin(scan * 2f * PI.toFloat()) * .5f + .5f)
        val ly = cy - height * lift
        drawCircle(
            color = warm.copy(alpha = .70f),
            radius = 2.6.dp.toPx(),
            center = Offset(cx - halfW * .5f, ly + halfD * .5f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(warm.copy(alpha = .30f), Color.Transparent),
                center = Offset(cx - halfW * .5f, ly + halfD * .5f),
                radius = 9.dp.toPx()
            ),
            radius = 9.dp.toPx(),
            center = Offset(cx - halfW * .5f, ly + halfD * .5f)
        )
    }

    // Dimension lines with end ticks: the architect's measurement callouts.
    val dimY = cy + halfD + h * .10f
    drawLine(structure.copy(alpha = .34f), Offset(cx - halfW, dimY), Offset(cx + halfW, dimY), 1.dp.toPx())
    listOf(cx - halfW, cx + halfW).forEach { dx ->
        drawLine(structure.copy(alpha = .34f), Offset(dx, dimY - 4.dp.toPx()), Offset(dx, dimY + 4.dp.toPx()), 1.dp.toPx())
    }
    val dimX = cx + halfW + w * .07f
    drawLine(structure.copy(alpha = .34f), Offset(dimX, cy - height), Offset(dimX, cy), 1.dp.toPx())
    listOf(cy - height, cy).forEach { dy ->
        drawLine(structure.copy(alpha = .34f), Offset(dimX - 4.dp.toPx(), dy), Offset(dimX + 4.dp.toPx(), dy), 1.dp.toPx())
    }
}

// ---------------------------------------------------------------------------------------------
// Dispatcher
// ---------------------------------------------------------------------------------------------

/** Renders the Home surface in the presentation the user selected in Settings. */
@Composable
internal fun HomeStyleSurface(
    style: HomeStyle,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    when (style) {
        HomeStyle.BIOLUMINESCENT -> HomeStyleBioluminescent(evidence, actions, bottomClearance)
        HomeStyle.COSMIC_ORBIT -> HomeStyleCosmicOrbit(evidence, actions, bottomClearance)
        HomeStyle.COSMIC_IMMERSION -> HomeStyleCosmicImmersion(evidence, actions, bottomClearance)
        HomeStyle.PARAMETRIC -> HomeStyleParametric(evidence, actions, bottomClearance)
    }
}

/** Clipboard helper shared by every style so "copy" behaves identically across presentations. */
@Composable
internal fun rememberCopyIpAction(repo: AppRepository, ip: String): () -> Unit {
    val clipboard = LocalClipboardManager.current
    val copied = Tr.now.ipCopied
    return {
        if (ip.isNotBlank()) {
            clipboard.setText(AnnotatedString(ip))
            repo.setRuntimeMessage(copied)
        }
    }
}
