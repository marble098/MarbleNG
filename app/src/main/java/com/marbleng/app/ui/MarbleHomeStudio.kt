@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.ConnectionPingState

import com.marbleng.app.model.HomePingAction
import com.marbleng.app.model.parseHomePingAction
import kotlinx.coroutines.delay
import kotlin.math.max

internal const val LIVE_PING_INTERVAL_MS = 3_000L

internal const val LIVE_PING_HISTORY = 24

internal const val LIVE_PING_FULL_SCALE_MS = 400

@Composable
internal fun HomeSelectedRouteCard(
    evidence: HomeEvidence,
    tone: Color,
    accent: Color,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = Tr.now

    val shape = RoundedCornerShape(22.dp)
    val node = evidence.nodeName.ifBlank { t.chooseRoute }
    val group = evidence.sourceName.ifBlank { "—" }
    val protocol = evidence.profile?.scheme?.trim().orEmpty()

    val borderTone by animateColorAsState(
        targetValue = if (evidence.connected) tone.copy(alpha = .42f) else accent.copy(alpha = .22f),
        animationSpec = MarbleMotionSpecs.Color,
        label = "selected-route-border"
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, borderTone, shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onLibrary)
            .semantics { contentDescription = "${t.selectedRoute}: $node" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                evidence.flag.ifBlank { "🌐" },
                style = MaterialTheme.typography.titleMedium
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                t.selectedRoute.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                node,
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    group,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (protocol.isNotBlank()) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = .14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            protocol.uppercase(),
                            color = accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                t.changeRoute,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun HomeLivePingMeter(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    visible: Boolean = true,

    pingAction: HomePingAction = HomePingAction.ROUTE
) {
    val t = Tr.now
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    val measured = evidence.pingState == ConnectionPingState.MEASURED
    val samples = remember { mutableStateListOf<Int>() }

    LaunchedEffect(evidence.pingMs, evidence.pingState) {
        if (evidence.pingState == ConnectionPingState.MEASURED && evidence.pingMs > 0) {
            samples.add(evidence.pingMs)
            while (samples.size > LIVE_PING_HISTORY) samples.removeAt(0)
        }
    }

    val band = if (measured) pingMetricBand(evidence.pingMs) else MarbleMetricBand.UNKNOWN
    val valueTone = if (measured) marbleMetricTone(band) else tone
    val fill = if (measured) {
        (1f - evidence.pingMs / LIVE_PING_FULL_SCALE_MS.toFloat()).coerceIn(.06f, 1f)
    } else {
        0f
    }

    val animatedFill by animateFloatAsState(
        targetValue = fill,
        animationSpec = MarbleExpressiveSpecs.ProgressSettleFloat,
        label = "live-ping-fill"
    )

    val gaugeMotion = MarbleMotion.current

    val shape = RoundedCornerShape(22.dp)
    val valueLabel = when {
        measured -> "${evidence.pingMs}"
        measuring -> "•••"
        else -> "—"
    }
    val caption = when {
        !evidence.connected -> t.livePingWaiting
        else -> evidence.nodeName.ifBlank { t.livePingHint }
    }

    val spokenPing = homePingLabel(evidence)

    val tappable = visible && homePingTappable(evidence)
    val spokenAction = homePingActionLabel(pingAction)

    val presence by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            tween(
                durationMillis = MarbleExpressiveMotion.Medium2,
                easing = MarbleExpressiveMotion.EmphasizedDecelerate
            )
        } else {
            tween(
                durationMillis = MarbleExpressiveMotion.Short4,
                easing = MarbleExpressiveMotion.EmphasizedAccelerate
            )
        },
        label = "live-ping-presence"
    )

    Column(
        modifier = modifier
            .width(150.dp)
            .graphicsLayer { alpha = presence }
            .clip(shape)
            .background(Aether.VoidElevated)
            .background(
                Brush.verticalGradient(
                    listOf(tone.copy(alpha = .12f), Color.Transparent)
                )
            )
            .border(1.dp, tone.copy(alpha = .30f), shape)
            .semantics {
                contentDescription = "${t.livePing}: $spokenPing — $spokenAction"
            }
            .kineticClickable(
                enabled = tappable,
                role = Role.Button,
                pressScale = .97f,
                boundedShape = shape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = { runHomePingAction(pingAction, actions) }
            )
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        alpha = if (measuring) .35f + .65f * gaugeMotion.loop(1_200) else 1f
                    }
                    .clip(CircleShape)
                    .background(valueTone)
            )
            Text(
                t.livePing.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(78.dp, 46.dp)) {
                val w = size.width
                val h = size.height
                val stroke = 7.dp.toPx()
                val box = Size(w, h * 2f)
                drawArc(
                    color = valueTone.copy(alpha = .16f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(0f, 0f),
                    size = box,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                if (animatedFill > 0.01f) {
                    drawArc(
                        color = valueTone,
                        startAngle = 180f,
                        sweepAngle = 180f * animatedFill,
                        useCenter = false,
                        topLeft = Offset(0f, 0f),
                        size = box,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    MarbleExpressiveValueText(
                        value = valueLabel,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (measured) {
                        Text(
                            " ms",
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (samples.size >= 2) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                val peak = max(1, samples.maxOrNull() ?: 0)
                val floorValue = 0
                val span = (peak - floorValue).coerceAtLeast(1)
                val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                val path = Path()
                samples.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = size.height - (size.height * ((value - floorValue).toFloat() / span.toFloat()))
                        .coerceIn(.08f, 1f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    valueTone,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }

        Text(
            caption,
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HomeLivePingSlab(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        HomeLivePingMeter(
            evidence = evidence,
            actions = actions,
            tone = tone,
            visible = evidence.connected,

            pingAction = evidence.pingGaugeAction
        )
    }
}

@Composable
internal fun HomePowerStage(
    evidence: HomeEvidence,
    tone: Color,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    meterEnabled: Boolean = true,
    sideBySide: Boolean? = null,
    control: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        control()
    }
}

@Composable
private fun StageMeter(
    visible: Boolean,
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color
) {
    HomeLivePingMeter(
        evidence = evidence,
        actions = actions,
        tone = tone,
        visible = visible,

        pingAction = evidence.pingGaugeAction
    )
}

@Composable
internal fun HomeShortcutDeck(
    evidence: HomeEvidence,
    actions: HomeActions,
    accent: Color,
    modifier: Modifier = Modifier,

    pingAction: HomePingAction = HomePingAction.GROUP
) {
    val t = Tr.now
    val pingTone = homePingTone(evidence, accent)
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    val pingValue = homePingLabel(evidence)
    val pingTappable = homePingTappable(evidence)
    val pingSpoken = homePingActionLabel(pingAction)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeDeckButton(
            onClick = actions.onAddRoute,
            tone = accent,
            label = t.proAddRoute
        ) {
            HomeGlyphIcon(HomeGlyph.PLUS, accent, Modifier.size(17.dp))
        }
        HomeDeckButton(
            onClick = actions.onPasteImport,
            tone = accent,
            label = t.pasteShortcut
        ) {
            HomeGlyphIcon(HomeGlyph.PASTE, accent, Modifier.size(17.dp))
        }
        HomeDeckButton(
            onClick = actions.onQrImport,
            tone = accent,
            label = t.qrShortcut
        ) {
            HomeGlyphIcon(HomeGlyph.QR, accent, Modifier.size(17.dp))
        }

        val pingShape = RoundedCornerShape(13.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(pingShape)
                .background(Aether.VoidElevated)
                .border(1.dp, pingTone.copy(alpha = .34f), pingShape)
                .semantics { contentDescription = "${t.livePing} $pingValue — $pingSpoken" }
                .kineticClickable(
                    enabled = pingTappable,
                    role = Role.Button,
                    pressScale = .94f,
                    boundedShape = pingShape,
                    releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                    onClick = { runHomePingAction(pingAction, actions) }
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            HomeGlyphIcon(
                HomeGlyph.PULSE,
                if (measuring) accent else pingTone,
                Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    t.livePing,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                pingValue,
                color = if (measuring) Aether.InkMuted else Aether.Ink,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeDeckButton(
    onClick: () -> Unit,
    tone: Color,
    label: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, tone.copy(alpha = .30f), shape)
            .semantics { contentDescription = label }
            .kineticClickable(
                role = Role.Button,
                pressScale = .9f,
                boundedShape = shape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun ConnectButtonStream(
    evidence: HomeEvidence,
    animatedTone: Color,
    armed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = MarbleMotion.current
    val busy = evidence.connecting || evidence.disconnecting
    val period = if (busy) 1_600 else if (evidence.connected) 3_400 else 5_600

    val shape = RoundedCornerShape(22.dp)
    val label = homeActionLabel(evidence)
    val controlDescription = "${trx(label)} ${trx("connection button")}"

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides
            androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(68.dp)
                // MARBLE_SHADOW_CALM_V220 — the studio rail floats on half the shadow it had.
                .marbleShadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = animatedTone.copy(alpha = .10f),
                    spotColor = animatedTone.copy(alpha = .16f)
                )
                .clip(shape)
                .background(Aether.VoidElevated)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            animatedTone.copy(alpha = .22f),
                            animatedTone.copy(alpha = .05f)
                        )
                    )
                )
                .border(1.5.dp, animatedTone.copy(alpha = .42f), shape)
                .kineticClickable(
                    enabled = armed,
                    role = Role.Button,
                    pressScale = .99f,
                    boundedShape = shape,
                    releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                    onClick = onToggle
                )
                .semantics { contentDescription = controlDescription }
        ) {
            Canvas(Modifier.matchParentSize()) {
                val phase = motion.loop(period)
                val bandWidth = size.width * ExpressiveMath.wavyValue(phase, .24f, .46f)
                val travel = size.width + bandWidth
                val x = size.width + bandWidth - travel * phase
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            animatedTone.copy(alpha = if (busy) .34f else .20f),
                            Color.Transparent
                        ),
                        startX = x - bandWidth / 2f,
                        endX = x + bandWidth / 2f
                    )
                )
            }

            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            HomeGlyphIcon(
                connectButtonGlyph(evidence),
                animatedTone,
                Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                ConnectButtonCaption(evidence, animatedTone)
                Text(
                    homeStatusText(evidence),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (evidence.connected) {
                val pingLabel = when {
                    evidence.pingState == ConnectionPingState.MEASURED && evidence.pingMs >= 20 -> "${evidence.pingMs} ms"
                    evidence.pingState == ConnectionPingState.MEASURING -> "•••"
                    evidence.pingState == ConnectionPingState.FAILED -> "✕"
                    else -> "—"
                }
                val pingTone = when {
                    evidence.pingState == ConnectionPingState.MEASURED && evidence.pingMs >= 20 -> marbleMetricTone(pingMetricBand(evidence.pingMs))
                    evidence.pingState == ConnectionPingState.FAILED -> Aether.Danger
                    else -> animatedTone
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(animatedTone.copy(alpha = .14f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(pingTone)
                    )
                    Text(
                        pingLabel,
                        color = pingTone,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(animatedTone)
                )
            }
            }
        }
    }
}

@Composable
internal fun ConnectButtonFloating(
    evidence: HomeEvidence,
    animatedTone: Color,
    armed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = MarbleMotion.current
    val busy = evidence.connecting || evidence.disconnecting

    val label = homeActionLabel(evidence)
    val controlDescription = "${trx(label)} ${trx("floating connection button")}"
    val iconFraction by animateFloatAsState(
        targetValue = if (evidence.connected) .30f else .36f,
        animationSpec = MarbleMotionSpecs.ResponseFloat,
        label = "floating-icon-size"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .marblePopWhen(evidence.connected, peak = 1.06f)
                // MARBLE_SHADOW_CALM_V220 — the studio disc keeps its glow ring; the 18 dp slab
                // under it is gone.
                .marbleShadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .14f),
                    spotColor = animatedTone.copy(alpha = .20f)
                )
                .clip(CircleShape)
                .background(Aether.VoidElevated)
                .background(
                    Brush.radialGradient(
                        listOf(
                            animatedTone.copy(alpha = .28f),
                            animatedTone.copy(alpha = .06f)
                        )
                    )
                )
                .border(1.6.dp, animatedTone.copy(alpha = .55f), CircleShape)
                .kineticClickable(
                    enabled = armed,
                    role = Role.Button,
                    pressScale = .93f,
                    boundedShape = CircleShape,
                    releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                    onClick = onToggle
                )
                .semantics { contentDescription = controlDescription },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize().padding(7.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)

                val breathe = if (busy || evidence.connected) motion.breathe(2_400) else 0f
                val sweep = if (busy) motion.loop(1_150) * 360f else 0f
                when {
                    busy -> {
                        drawCircle(
                            color = animatedTone.copy(alpha = .18f),
                            radius = r * .86f,
                            center = c,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawArc(
                            color = animatedTone.copy(alpha = .85f + .15f * breathe),
                            startAngle = -90f + sweep,

                            sweepAngle = ExpressiveMath.arcSweep(motion.loop(1_900), 70f, 132f),
                            useCenter = false,
                            topLeft = Offset(c.x - r * .86f, c.y - r * .86f),
                            size = Size(r * 1.72f, r * 1.72f),
                            style = Stroke(
                                width = (3.4f + 1.2f * breathe).dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                    evidence.connected -> {
                        drawCircle(
                            color = animatedTone.copy(alpha = .14f + .08f * breathe),
                            radius = r * (.90f + .02f * breathe),
                            center = c,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                        drawCircle(
                            color = animatedTone.copy(alpha = .78f + .12f * breathe),
                            radius = r * .86f,
                            center = c,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                    else -> drawCircle(
                        color = animatedTone.copy(alpha = .45f),
                        radius = r * .86f,
                        center = c,
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                }
            }

            MarbleExpressiveGlyphSwap(
                key = connectButtonGlyph(evidence),
                modifier = Modifier.size(76.dp * iconFraction)
            ) {
                HomeGlyphIcon(
                    connectButtonGlyph(evidence),
                    animatedTone,
                    Modifier.size(76.dp * iconFraction)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ConnectButtonCaption(evidence, animatedTone)
    }
}

@Composable
internal fun HomeFloorDock(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated)
            .background(
                Brush.verticalGradient(
                    listOf(tone.copy(alpha = .10f), Color.Transparent)
                )
            )
            .border(1.dp, tone.copy(alpha = .18f), shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        HomePowerControl(
            evidence = evidence,
            tone = tone,
            onToggle = onToggle,
            flavor = HomeFlavor.IOS_SLIDER,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
