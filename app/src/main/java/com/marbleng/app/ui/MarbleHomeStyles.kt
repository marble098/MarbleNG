@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

// MARBLE_HOME_STYLE_V110
//
// Four presentations of one connection surface.
//
// Design contract shared by every style — enforced by scripts/system-integrity-check.py:
//   1. all four render the SAME runtime evidence: node name, source name, IP + country flag with
//      its three actions (copy / refresh / details), session uptime, and the one-shot ping;
//   2. no style draws a quality indicator around the connect control;
//   3. the artwork is Canvas-drawn vector work — no bitmaps, no downloads, no per-card infinite
//      transitions (ambient motion comes from Marble's single shared frame clock).

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .10f),
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
 * Node + source identity.
 *
 * Node and source are always labelled, so a duplicated node name inside two subscriptions can
 * still be told apart at a glance.
 */
@Composable
internal fun HomeIdentityBlock(
    evidence: HomeEvidence,
    tone: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = true
) {
    val t = Tr.now
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        HomeLabelledValue(
            label = t.node,
            value = evidence.nodeName.ifBlank { t.chooseRoute },
            tone = tone,
            centered = centered
        )
        HomeLabelledValue(
            label = t.source,
            value = evidence.sourceName.ifBlank { "—" },
            tone = Aether.InkMuted,
            centered = centered
        )
    }
}

@Composable
private fun HomeLabelledValue(
    label: String,
    value: String,
    tone: Color,
    centered: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (centered) Modifier else Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )
    }
}

/**
 * IP address + country flag + the three actions (copy, refresh, complete details).
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
    modifier: Modifier = Modifier,
    buttonSize: Dp = 32.dp
) {
    val t = Tr.now
    val ipText = when {
        evidence.ip.isNotBlank() -> evidence.ip
        evidence.ipLoading -> t.resolving
        else -> t.unavailable
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val badge = evidence.flag.ifBlank { evidence.countryCode.uppercase() }
        if (badge.isNotBlank()) {
            Text(
                badge,
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium,
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
            modifier = Modifier.weight(1f, fill = false)
        )
        PrismIconButton(
            onClick = actions.onCopyIp,
            tone = tone,
            size = buttonSize,
            descriptiveLabel = t.copyIp
        ) { HomeGlyphIcon(HomeGlyph.COPY, Aether.InkMuted, Modifier.size(buttonSize * .48f)) }
        PrismIconButton(
            onClick = actions.onRefreshIp,
            tone = tone,
            size = buttonSize,
            descriptiveLabel = t.refreshIp
        ) { HomeGlyphIcon(HomeGlyph.REFRESH, Aether.InkMuted, Modifier.size(buttonSize * .48f)) }
        PrismIconButton(
            onClick = actions.onIpDetails,
            tone = tone,
            size = buttonSize,
            descriptiveLabel = t.ipDetails
        ) { HomeGlyphIcon(HomeGlyph.MORE, Aether.InkMuted, Modifier.size(buttonSize * .48f)) }
    }
}

/** Uptime and the tappable one-shot ping, presented as one balanced pair. */
@Composable
internal fun HomeSessionStats(
    evidence: HomeEvidence,
    tone: Color,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeStatWell(
            glyph = HomeGlyph.CLOCK,
            label = t.uptime,
            value = if (evidence.connectedSinceMs > 0L) uptime else "—",
            tone = tone,
            modifier = Modifier.weight(1f)
        )
        HomeStatWell(
            glyph = HomeGlyph.PULSE,
            label = t.connectionPing,
            value = homePingLabel(evidence),
            tone = when (evidence.pingState) {
                ConnectionPingState.MEASURED -> marbleMetricTone(pingMetricBand(evidence.pingMs))
                ConnectionPingState.FAILED -> Aether.Danger
                else -> tone
            },
            actionLabel = when {
                !evidence.connected -> ""
                evidence.pingState == ConnectionPingState.MEASURING -> ""
                evidence.pingState == ConnectionPingState.IDLE -> t.testPing
                else -> t.retestPing
            },
            onAction = actions.onTestPing.takeIf {
                evidence.connected && evidence.pingState != ConnectionPingState.MEASURING
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeStatWell(
    glyph: HomeGlyph,
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
    actionLabel: String = "",
    onAction: (() -> Unit)? = null
) {
    PrismWell(
        modifier = modifier,
        tone = tone,
        onClick = onAction,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                HomeGlyphIcon(glyph, Aether.InkFaint, Modifier.size(12.dp))
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
            if (actionLabel.isNotBlank()) {
                Text(
                    actionLabel,
                    color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The connection control.
 *
 * Deliberately carries NO quality ring: link quality lives in its own readouts, and wrapping the
 * primary action in a score made the button's own state ambiguous.
 */
@Composable
internal fun HomePowerControl(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
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
    val pulse = if (evidence.connecting) .70f + MarbleMotion.current.breathe(1_150) * .30f else 1f
    val sweep = if (evidence.connecting) MarbleMotion.current.loop(950) * 360f else 0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .22f),
                    spotColor = animatedTone.copy(alpha = .30f)
                )
                .clip(CircleShape)
                .background(haloBrush ?: Brush.radialGradient(
                    listOf(
                        animatedTone.copy(alpha = .18f * pulse),
                        animatedTone.copy(alpha = .07f * pulse)
                    )
                ))
                .kineticClickable(
                    role = Role.Button,
                    pressScale = .95f,
                    boundedShape = CircleShape,
                    onClick = onToggle
                )
                .semantics { contentDescription = "$label connection button" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize().padding(12.dp)) {
                // Connecting shows indeterminate motion; connected shows a settled rim. Neither
                // encodes a quality score.
                when {
                    evidence.connecting -> drawArc(
                        color = animatedTone,
                        startAngle = -90f + sweep,
                        sweepAngle = 104f,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    evidence.connected -> drawCircle(
                        color = animatedTone.copy(alpha = .70f),
                        radius = size.minDimension / 2f,
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
internal fun HomeStatusHeadline(evidence: HomeEvidence, @Suppress("UNUSED_PARAMETER") tone: Color) {
    Text(
        homeStatusText(evidence),
        color = Aether.Ink,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun formatBps(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f kB/s".format(bps / 1_000.0)
    bps > 0 -> "$bps B/s"
    else -> "—"
}

// ---------------------------------------------------------------------------------------------
// Style 1 — Organic Bioluminescence
// ---------------------------------------------------------------------------------------------

/**
 * A glowing bio-luminescent seed resting on a fluid surface, with nerve-like tendrils carrying
 * data downward. Soft pastel green, pale purple and white; asymmetric floating evidence cards.
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
    val phase = MarbleMotion.current.loop(9_000)
    val breathe = MarbleMotion.current.breathe(3_400)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp)
            .padding(bottom = bottomClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawOrganicSurface(seedGlow, tendrilTone, phase, breathe, evidence.connected)
            }
            HomePowerControl(
                evidence = evidence,
                tone = tone,
                onToggle = actions.onToggleConnection,
                diameter = 148.dp,
                haloBrush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = .92f),
                        seedGlow.copy(alpha = .55f + .18f * breathe),
                        seedGlow.copy(alpha = .16f)
                    )
                ),
                modifier = Modifier.padding(top = 30.dp)
            )
        }

        HomeStatusHeadline(evidence, tone)

        // Asymmetric floating cards: identity leans one way, the IP row the other.
        PrismPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 22.dp),
            accent = seedGlow,
            selected = evidence.connected,
            contentPadding = PaddingValues(13.dp)
        ) {
            HomeIdentityBlock(evidence, tone, centered = false)
        }

        PrismPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp),
            accent = tendrilTone,
            contentPadding = PaddingValues(13.dp)
        ) {
            HomeIpRow(evidence, tone, actions, Modifier.fillMaxWidth())
        }

        HomeSessionStats(evidence, tone, actions, Modifier.fillMaxWidth())
        Spacer(Modifier.height(2.dp))
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

    // Fluid surface the seed rests on.
    val surfaceY = h * .58f
    val fluid = Path().apply {
        moveTo(0f, surfaceY)
        cubicTo(w * .25f, surfaceY - h * .07f, w * .70f, surfaceY + h * .07f, w, surfaceY - h * .02f)
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
    drawPath(
        fluid,
        color = glow.copy(alpha = .30f),
        style = Stroke(width = 1.6.dp.toPx())
    )

    // Nerve-like tendrils flowing downward: data leaving the seed.
    val strands = 5
    repeat(strands) { index ->
        val origin = w * (.30f + .10f * index)
        val drift = sin((phase * 2f * PI + index).toFloat()) * w * .035f
        val path = Path().apply {
            moveTo(origin, surfaceY - h * .02f)
            cubicTo(
                origin + drift, h * .74f,
                origin - drift, h * .86f,
                origin + drift * .5f, h
            )
        }
        drawPath(
            path,
            color = tendril.copy(alpha = if (connected) .34f else .16f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
        if (connected) {
            // One travelling luminous node per strand visualises the live flow.
            val travel = ((phase + index / strands.toFloat()) % 1f)
            drawCircle(
                color = glow.copy(alpha = .70f * (1f - travel)),
                radius = (2.6f + breathe).dp.toPx(),
                center = Offset(origin + drift * travel, surfaceY + (h - surfaceY) * travel)
            )
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
// Style 2 — Cosmic Orbit dashboard
// ---------------------------------------------------------------------------------------------

/**
 * A dashboard: the orbiting system is the hero card, a glowing network-speed graph sits under it,
 * and a slim vertical action rail lives on the trailing edge.
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
    val phase = MarbleMotion.current.loop(14_000)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 12.dp, top = 10.dp)
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
                        .height(212.dp)
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
                        diameter = 112.dp,
                        haloBrush = Brush.radialGradient(
                            listOf(
                                gold.copy(alpha = .55f),
                                gold.copy(alpha = .16f),
                                Color.Transparent
                            )
                        )
                    )
                }
                HomeStatusHeadline(evidence, tone)
                HomeIdentityBlock(evidence, tone, Modifier.fillMaxWidth(), centered = false)
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
                    drawSpeedGraph(tone, gold, evidence.downBps, evidence.upBps)
                }
                HomeIpRow(evidence, tone, actions, Modifier.fillMaxWidth())
            }

            HomeSessionStats(evidence, tone, actions, Modifier.fillMaxWidth())
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
        drawCircle(
            color = if (index == 1) gold else accent,
            radius = (4f - index * .6f).dp.toPx(),
            center = planet
        )
    }
}

private fun DrawScope.drawSpeedGraph(tone: Color, gold: Color, down: Long, up: Long) {
    val w = size.width
    val h = size.height
    val peak = if (down + up > 0) .82f else .35f
    val curve = Path().apply {
        moveTo(0f, h)
        cubicTo(w * .18f, h * .92f, w * .30f, h * (1f - peak * .55f), w * .46f, h * (1f - peak))
        cubicTo(w * .62f, h * (1f - peak * 1.02f), w * .74f, h * .70f, w, h * .58f)
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
    drawCircle(gold, radius = 3.6.dp.toPx(), center = Offset(w * .46f, h * (1f - peak)))
    drawCircle(
        color = gold.copy(alpha = .28f),
        radius = 8.dp.toPx(),
        center = Offset(w * .46f, h * (1f - peak))
    )
}

// ---------------------------------------------------------------------------------------------
// Style 3 — Cosmic Orbit, full-screen immersion
// ---------------------------------------------------------------------------------------------

/**
 * Edge-to-edge: luminous orbits fill the upper half, a cosmic energy flower with cyan/violet
 * petals fills the lower half, and the connection status sits centred between them.
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
    val orbitPhase = MarbleMotion.current.loop(16_000)
    val bloom = MarbleMotion.current.breathe(4_600)

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) {
            drawImmersiveCosmos(cyan, violet, orbitPhase, bloom, evidence.connected)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(96.dp))

            HomePowerControl(
                evidence = evidence,
                tone = tone,
                onToggle = actions.onToggleConnection,
                diameter = 132.dp,
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

            HomeIdentityBlock(evidence, tone)

            // Edge-to-edge layout keeps the evidence weightless: no card frames, one hairline.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Aether.VoidElevated.copy(alpha = .72f))
                    .border(1.dp, cyan.copy(alpha = .22f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeIpRow(evidence, tone, actions, Modifier.fillMaxWidth(), buttonSize = 30.dp)
            }

            HomeSessionStats(evidence, tone, actions, Modifier.fillMaxWidth())
        }
    }
}

private fun DrawScope.drawImmersiveCosmos(
    cyan: Color,
    violet: Color,
    phase: Float,
    bloom: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Upper half: luminous orbits and floating planets.
    val skyCenter = Offset(w * .5f, h * .20f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(violet.copy(alpha = .20f), Color.Transparent),
            center = skyCenter,
            radius = w * .85f
        ),
        radius = w * .85f,
        center = skyCenter
    )
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
// Style 4 — Minimalist Parametric Architecture
// ---------------------------------------------------------------------------------------------

/**
 * An isometric glass-and-concrete structure lit from within, on a light grid. Everything else is
 * precise modular panels with generous separation and minimalist typography.
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
    val glowPulse = MarbleMotion.current.breathe(5_200)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .padding(bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(226.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Aether.VoidElevated)
                .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawParametricStructure(structure, warm, glowPulse, evidence.connected)
            }
            HomePowerControl(
                evidence = evidence,
                tone = tone,
                onToggle = actions.onToggleConnection,
                diameter = 120.dp,
                haloBrush = Brush.radialGradient(
                    listOf(
                        warm.copy(alpha = .30f + .10f * glowPulse),
                        Color.Transparent
                    )
                )
            )
        }

        Text(
            homeStatusText(evidence),
            color = Aether.Ink,
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-.2).sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Precise geometric panels: one fact per module, clear separation between modules.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrismPanel(
                modifier = Modifier.weight(1f),
                accent = tone,
                radius = 16.dp,
                contentPadding = PaddingValues(12.dp)
            ) {
                HomeIdentityBlock(evidence, tone, Modifier.fillMaxWidth(), centered = false)
            }
        }

        PrismPanel(
            modifier = Modifier.fillMaxWidth(),
            accent = warm,
            radius = 16.dp,
            contentPadding = PaddingValues(12.dp)
        ) {
            Text(
                Tr.now.ipAddress,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            HomeIpRow(evidence, tone, actions, Modifier.fillMaxWidth())
            if (evidence.location.isNotBlank()) {
                Text(
                    evidence.location,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HomeSessionStats(evidence, tone, actions, Modifier.fillMaxWidth())
    }
}

private fun DrawScope.drawParametricStructure(
    structure: Color,
    warm: Color,
    pulse: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Subtle background grid lines.
    val step = w / 12f
    var x = step
    while (x < w) {
        drawLine(
            color = structure.copy(alpha = .06f),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 1f
        )
        x += step
    }
    var y = step
    while (y < h) {
        drawLine(
            color = structure.copy(alpha = .06f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1f
        )
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
