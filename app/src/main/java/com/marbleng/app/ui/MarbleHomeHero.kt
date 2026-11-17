@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.ConnectPhase
import com.marbleng.app.model.ConnectPulse
import com.marbleng.app.model.HeroMetric
import com.marbleng.app.model.HeroMetricKind
import com.marbleng.app.model.HeroTone
import com.marbleng.app.model.HomeHeroSnapshot
import com.marbleng.app.ui.theme.MarbleRadius
import com.marbleng.app.ui.theme.MarbleSpace
import com.marbleng.app.ui.theme.MarbleVeil
import com.marbleng.app.ui.theme.MarbleWash
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onToneContainer
import com.marbleng.app.ui.theme.toneColor
import com.marbleng.app.ui.theme.toneContainer
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_HOME_HERO_V261 — the top of Home, rebuilt as one flexible Material 3 Expressive surface.
 *
 * The panel integrates:
 *  - a morphing `MaterialShapes` phase emblem with bounded `ConnectPulse` when connected and
 *    `MarbleWaveRing` during connection/probe transitions;
 *  - tonal overline + mode/engine context pill and `headlineSmallEmphasized` state heading;
 *  - an elevated route strip showing flag, server title, protocol/source detail, and switch cue;
 *  - an interlocking 2x2 Expressive Bento telemetry grid (`Ping`, `Jitter`, `Uptime`, `Exit IP`)
 *    with per-metric `MaterialShapes` badges and semantic tones from [HeroTone].
 */
@Composable
internal fun MarbleHeroPanel(
    snapshot: HomeHeroSnapshot,
    motif: MarbleExpressiveMotif,
    onMetricClick: (HeroMetricKind) -> Unit,
    modifier: Modifier = Modifier,
    phase: ConnectPhase? = null,
    modeLabel: String = "",
    engineLabel: String = "",
    onOpenLogs: (() -> Unit)? = null,
    onOpenRoute: (() -> Unit)? = null,
    primaryAction: (@Composable () -> Unit)? = null,
    tag: String = "home-hero-panel"
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val inferredConnected = snapshot.metrics.any {
        it.kind == HeroMetricKind.UPTIME && it.tone == HeroTone.LIVE
    }
    val resolvedPhase = phase ?: when {
        inferredConnected -> ConnectPhase.CONNECTED
        snapshot.working -> ConnectPhase.CONNECTING
        else -> ConnectPhase.IDLE
    }
    val connected = resolvedPhase == ConnectPhase.CONNECTED
    val blocked = resolvedPhase == ConnectPhase.BLOCKED
    val transitioning = resolvedPhase == ConnectPhase.CONNECTING ||
        resolvedPhase == ConnectPhase.DISCONNECTING
    val working = transitioning || snapshot.working

    // MARBLE_ONE_LANGUAGE_V281 — three tables (stage, stage ink, state light) all answered the
    // same question, and the middle one answered it differently: an in-flight dial was tertiary
    // here and warning on the connect pill beside it. One verdict now drives all three, so the hero
    // and the pill are the same colour for the same state. `working` can still be true while the
    // phase reads IDLE (a snapshot mid-flight), and that is the one case the phase is corrected for.
    val heroPhase = if (working && resolvedPhase == ConnectPhase.IDLE) {
        ConnectPhase.CONNECTING
    } else {
        resolvedPhase
    }
    val heroVerdict = connectPhaseTone(heroPhase)
    val stageColor by animateColorAsState(
        targetValue = colors.toneContainer(heroVerdict),
        animationSpec = marbleDefaultSpatialSpec(),
        label = "heroPanelStage"
    )
    val onStage = colors.onToneContainer(heroVerdict)
    val stateLight = colors.toneColor(heroVerdict)
    val morph by animateFloatAsState(
        targetValue = when {
            connected -> 1f
            blocked -> 0.72f
            working -> 0.48f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "heroPanelMorph"
    )

    val emblemTarget = when {
        blocked -> MarbleExpressiveMotif.PENTAGON
        transitioning -> MarbleExpressiveMotif.BURST
        else -> MarbleExpressiveMotif.FLOWER
    }
    val emblemShape = marbleMorphingShape(
        from = MarbleExpressiveMotif.CLOVER,
        to = emblemTarget,
        progress = morph
    )
    val heartbeat = if (ConnectPulse.active(resolvedPhase, motion.loops)) {
        val transition = rememberInfiniteTransition(label = "heroEmblemPulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ConnectPulse.PERIOD_MS)),
            label = "heroEmblemPhase"
        )
        value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(marbleAsymmetricShape(prominent = true))
            .background(stageColor)
            .testTag(tag)
    ) {
        // Directional tonal bloom painted into the surface so it carries depth without hairlines.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stateLight.copy(alpha = if (connected) 0.28f else 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.06f),
                    radius = size.width * 0.84f
                ),
                radius = size.width * 0.84f,
                center = Offset(size.width * 0.88f, size.height * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        onStage.copy(alpha = MarbleWash.FAINT),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.10f, size.height * 0.94f),
                    radius = size.width * 0.65f
                ),
                radius = size.width * 0.65f,
                center = Offset(size.width * 0.10f, size.height * 0.94f)
            )
        }
        MarblePetalMark(
            color = onStage.copy(alpha = if (connected || working) 0.14f else 0.09f),
            size = 180.dp,
            motif = motif,
            targetMotif = emblemTarget,
            morphProgress = morph,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer {
                    translationX = 44.dp.toPx()
                    translationY = (-46).dp.toPx()
                }
        )

        Column(
            modifier = Modifier.padding(start = MarbleSpace.XL.dp, end = MarbleSpace.XL.dp, top = MarbleSpace.LG.dp, bottom = MarbleSpace.LG.dp),
            verticalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp)
        ) {
            // ── Header: morphing phase emblem + overline/context + headline + logs capsule ────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp)
            ) {
                Box(
                    modifier = Modifier.size(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (connected && motion.loops) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val r = (size.minDimension / 2f) - 2.dp.toPx() +
                                ConnectPulse.ringRadiusFraction(heartbeat) * 2.dp.toPx()
                            drawCircle(
                                color = stateLight.copy(
                                    alpha = ConnectPulse.ringAlpha(heartbeat, 0.55f)
                                ),
                                radius = r,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(emblemShape)
                            .background(onStage.copy(alpha = MarbleWash.TINT)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (transitioning) {
                            MarbleWaveRing(size = 22.dp, color = onStage)
                        } else {
                            MarbleGlyphIcon(
                                glyph = when {
                                    blocked -> MarbleGlyph.SHIELD
                                    connected -> MarbleGlyph.SHIELD
                                    else -> MarbleGlyph.TUNNEL
                                },
                                color = onStage,
                                modifier = Modifier.size(20.dp),
                                weight = 1.18f
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MarbleSpace.XS.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(stateLight)
                        )
                        Text(
                            text = snapshot.overline,
                            color = onStage.copy(alpha = MarbleVeil.QUIET),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val contextBadge = listOf(modeLabel, engineLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (contextBadge.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(onStage.copy(alpha = MarbleWash.SOFT))
                                    .padding(horizontal = MarbleSpace.SM.dp, vertical = MarbleSpace.HAIR.dp)
                            ) {
                                Text(
                                    text = contextBadge,
                                    color = onStage.copy(alpha = MarbleVeil.NEAR),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = snapshot.stateLabel,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith
                                (fadeOut() + scaleOut(targetScale = 0.94f))
                        },
                        label = "heroPanelState"
                    ) { label ->
                        Text(
                            text = trx(label),
                            color = onStage,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onOpenLogs != null) {
                    HeroLogsCapsule(
                        connected = connected,
                        working = working,
                        onStage = onStage,
                        onClick = onOpenLogs
                    )
                }
            }

            // ── Route strip: flag, route title, protocol + source, switch chevron ─────────────
            HeroRouteStrip(
                snapshot = snapshot,
                onStage = onStage,
                onClick = onOpenRoute ?: { onMetricClick(HeroMetricKind.EXIT) }
            )

            // ── Interlocking 2×2 Telemetry Bento Grid ─────────────────────────────────────────
            HeroMetricGrid(
                metrics = snapshot.metrics,
                onStage = onStage,
                onMetricClick = onMetricClick
            )

            primaryAction?.invoke()
        }
    }
}

/** The four facts, two by two with interlocking bento corners. */
@Composable
internal fun HeroMetricGrid(
    metrics: List<HeroMetric>,
    onStage: Color,
    onMetricClick: (HeroMetricKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home-hero-metrics"),
        verticalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp)
    ) {
        metrics.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp)
            ) {
                row.forEachIndexed { colIndex, metric ->
                    // MARBLE_ONE_LANGUAGE_V281 — the quadrant's one open corner and its three
                    // anchor corners both come from the shape ladder now (22/10 dp matched no rung),
                    // so the metric grid reads as the same family as the asymmetric containers.
                    val outer = MarbleRadius.CARD.dp
                    val inner = MarbleRadius.SM.dp
                    val cellShape = when {
                        rowIndex == 0 && colIndex == 0 -> RoundedCornerShape(
                            topStart = outer, topEnd = inner, bottomStart = inner, bottomEnd = inner
                        )
                        rowIndex == 0 && colIndex == 1 -> RoundedCornerShape(
                            topStart = inner, topEnd = outer, bottomStart = inner, bottomEnd = inner
                        )
                        rowIndex == 1 && colIndex == 0 -> RoundedCornerShape(
                            topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = inner
                        )
                        else -> RoundedCornerShape(
                            topStart = inner, topEnd = inner, bottomStart = inner, bottomEnd = outer
                        )
                    }
                    HeroMetricCell(
                        metric = metric,
                        onStage = onStage,
                        shape = cellShape,
                        onClick = { onMetricClick(metric.kind) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroMetricCell(
    metric: HeroMetric,
    onStage: Color,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tone = metric.tone
    // MARBLE_ONE_LANGUAGE_V281 — the cell's three verdicts come from the shared tone table; the
    // three non-verdicts (being measured, live, nothing to measure) stay in the stage's own ink,
    // quieted when there is no value yet.
    val accent = heroTone(tone)?.let { colors.toneColor(it) }
        ?: if (tone == HeroTone.PENDING || tone == HeroTone.IDLE) {
            onStage.copy(alpha = MarbleVeil.FADED)
        } else {
            onStage
        }
    val motif = when (metric.kind) {
        HeroMetricKind.PING -> MarbleExpressiveMotif.SUNNY
        HeroMetricKind.JITTER -> MarbleExpressiveMotif.FAN
        HeroMetricKind.UPTIME -> MarbleExpressiveMotif.FLOWER
        HeroMetricKind.EXIT -> MarbleExpressiveMotif.CLOVER
    }
    val numeric = metric.kind != HeroMetricKind.EXIT && metric.kind != HeroMetricKind.UPTIME
    val isMono = metric.kind == HeroMetricKind.EXIT || metric.kind == HeroMetricKind.UPTIME
    val label = when (metric.kind) {
        HeroMetricKind.PING -> trx("Ping")
        HeroMetricKind.JITTER -> trx("Jitter")
        HeroMetricKind.UPTIME -> trx("Uptime")
        HeroMetricKind.EXIT -> trx("Exit IP")
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface.copy(alpha = MarbleVeil.GHOST))
            .background(onStage.copy(alpha = MarbleWash.FAINT))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home-hero-metric-${metric.kind.id}")
            .padding(start = MarbleSpace.MD.dp, end = MarbleSpace.MD.dp, top = MarbleSpace.INNER.dp, bottom = MarbleSpace.INNER.dp),
        verticalArrangement = Arrangement.spacedBy(MarbleSpace.XS.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(marbleExpressiveShape(motif))
                    .background(accent.copy(alpha = MarbleWash.STRONG)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(accent)
                )
            }
            Text(
                text = label,
                color = onStage.copy(alpha = MarbleVeil.QUIET),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = with(LocalDensity.current) {
                        MaterialTheme.typography.headlineSmallEmphasized.lineHeight.toDp()
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            if (metric.isWorking) {
                MarbleWaveRing(
                    modifier = Modifier.padding(top = MarbleSpace.XS.dp),
                    size = 20.dp,
                    color = onStage
                )
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpace.XS.dp)
                ) {
                    Text(
                        text = if (numeric) faDigits(metric.value) else metric.value,
                        color = if (tone == HeroTone.POOR) accent else onStage,
                        style = if (isMono) {
                            MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            )
                        } else {
                            MaterialTheme.typography.headlineSmallEmphasized.copy(
                                fontFeatureSettings = "tnum"
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (metric.unit.isNotBlank()) {
                        Text(
                            text = metric.unit,
                            color = onStage.copy(alpha = MarbleVeil.QUIET),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = MarbleSpace.XS.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The route strip: avatar, name, protocol, and a tonal switch pill — the server this panel is about. */
@Composable
private fun HeroRouteStrip(
    snapshot: HomeHeroSnapshot,
    onStage: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MarbleRadius.TILE.dp))
            .background(colors.surface.copy(alpha = MarbleVeil.GHOST))
            .background(onStage.copy(alpha = MarbleWash.FAINT))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("home-hero-route")
            .padding(start = MarbleSpace.INNER.dp, end = MarbleSpace.INNER.dp, top = MarbleSpace.INNER.dp, bottom = MarbleSpace.INNER.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.INNER.dp)
    ) {
        MarbleServerAvatar(
            profile = null,
            countryCode = snapshot.countryCode,
            size = 34.dp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)
        ) {
            Text(
                text = snapshot.routeName,
                color = onStage,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = snapshot.routeDetail,
                color = onStage.copy(alpha = MarbleVeil.QUIET),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(onStage.copy(alpha = MarbleWash.SOFT)),
            contentAlignment = Alignment.Center
        ) {
            MarbleChevron(size = 14.dp, tint = onStage.copy(alpha = MarbleVeil.NEAR))
        }
    }
}

/** The core logs trigger, living inside the hero header rather than duplicating at the bottom. */
@Composable
private fun HeroLogsCapsule(
    connected: Boolean,
    working: Boolean,
    onStage: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val label = trx("Logs")
    // MARBLE_ONE_LANGUAGE_V281 — the capsule's dot reads the same verdict as the pill; only
    // "nothing is happening" keeps the stage's own quieted ink, because that is not a verdict.
    val dotPhase = when {
        connected -> ConnectPhase.CONNECTED
        working -> ConnectPhase.CONNECTING
        else -> ConnectPhase.IDLE
    }
    val dotColor = if (dotPhase == ConnectPhase.IDLE) {
        onStage.copy(alpha = MarbleVeil.FADED)
    } else {
        colors.toneColor(connectPhaseTone(dotPhase))
    }
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.surface.copy(alpha = MarbleVeil.GHOST))
            .background(onStage.copy(alpha = MarbleWash.SOFT))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag("home-hero-logs")
            .padding(horizontal = MarbleSpace.MD.dp, vertical = MarbleSpace.SM.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(dotColor)
        )
        Text(
            text = label,
            color = onStage,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}
