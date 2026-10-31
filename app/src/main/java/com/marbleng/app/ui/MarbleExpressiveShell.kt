@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

// MARBLE_EXPRESSIVE_SHELL_V241 + MARBLE_EXPRESSIVE_STUDIO_V252 — the Expressive controls that
// power Home and Servers.
//
//   * [MarbleConnectPill]       — solid M3 Expressive tonal/status pill with spatial corner
//                                 morphing, a Clover4Leaf ↔ Flower morphing emblem, and Google's
//                                 Expressive LoadingIndicator while negotiating the tunnel.
//   * [MarbleModeMenuChip]      — spring-morphing mode capsule with an Expressive Fan badge.
//   * [MarbleTrafficSparkline]  — grid-free live throughput canvas inside an asymmetric Expressive
//                                 container with a morphing series lens pill.
//   * [MarbleHealthPill]        — Expressive polygon health dot + calm tonal capsule.
//   * [QuickLogsSheet]          — the live core tail one tap from Home.
//   * [SmartPresetStrip]        — three Expressive morphing preset capsules with Sunny / Fan /
//                                 Clover4Leaf polygon badges.

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ConnectModeChip
import com.marbleng.app.model.ConnectPillMotion
import com.marbleng.app.model.LogLevel
import com.marbleng.app.model.QuickLogLine
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.SmartPreset
import com.marbleng.app.model.TrafficSample
import com.marbleng.app.model.TrafficTimeline
import com.marbleng.app.model.matchSmartPreset
import com.marbleng.app.model.quickLogLines
import com.marbleng.app.model.sparklineAreaPoints
import com.marbleng.app.model.sparklinePoints
import com.marbleng.app.model.trafficPeak
import com.marbleng.app.model.trafficSeries
import com.marbleng.app.ui.theme.MarbleSheetShape
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.marbleDefaultEffectsSpec
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onSuccess
import com.marbleng.app.ui.theme.onWarning
import com.marbleng.app.ui.theme.rememberReducedMotion
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// The one connect control
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — the single primary action of the app.
 *
 * Replaces non-Expressive `CircleShape` halos/dots and linear gradients with solid Material 3
 * Expressive tonal/status fills, spatial-spring corner morphing, a `Clover4Leaf` ↔ `Flower`
 * morphing emblem, and Google's Expressive `LoadingIndicator` while negotiating the tunnel.
 */
@Composable
fun MarbleConnectPill(
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    title: String,
    subtitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    ticker: String = ""
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val motion = marbleMotion()
    val working = connecting || disconnecting

    val tone by animateColorAsState(
        targetValue = when {
            connected -> colors.success
            working -> colors.warning
            else -> colors.primary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connectPillTone"
    )
    val onTone = when {
        connected -> colors.onSuccess
        working -> colors.onWarning
        else -> colors.onPrimary
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val height by animateDpAsState(
        targetValue = if (connected) 88.dp else 78.dp,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "connectPillHeight"
    )
    // V242: Effects springs do not overshoot; ConnectPillMotion.insetDp is the second line of defence.
    val inset by animateDpAsState(
        targetValue = if (connected) 0.dp else ConnectPillMotion.RESTING_INSET_DP.dp,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connectPillInset"
    )
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 20.dp
            connected -> 26.dp
            working -> 30.dp
            else -> 38.dp
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "connectPillCorner"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) MarbleSpec.PRESSED_SCALE else 1f,
        animationSpec = if (reducedMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "connectPillPress"
    )
    val stateMorph by animateFloatAsState(
        targetValue = when {
            connected -> 1f
            pressed -> 0.55f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "connectPillStateMorph"
    )
    val emblemSpin by animateFloatAsState(
        targetValue = when {
            pressed -> -18f
            connected -> 45f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "connectPillSpin"
    )

    val ambientTurn = if (connected && motion.loops) {
        val transition = rememberInfiniteTransition(label = "connectEmblemIdle")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 22_000, easing = LinearEasing)
            ),
            label = "connectEmblemAngle"
        )
        angle
    } else {
        0f
    }

    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ConnectPillMotion.insetDp(inset.value).dp)
            .height(height.coerceAtLeast(64.dp))
            .scale(pressScale)
            .clip(shape)
            .background(tone)
            .clickable(
                enabled = !disconnecting,
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onToggle()
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (reducedMotion) Modifier
                    else Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (working) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(marbleExpressiveShape(MarbleExpressiveMotif.BURST))
                        .background(onTone.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = onTone
                    )
                }
            } else {
                val emblemShape = marbleMorphingShape(
                    from = MarbleExpressiveMotif.CLOVER,
                    to = MarbleExpressiveMotif.FLOWER,
                    progress = stateMorph
                )
                Box(
                    modifier = Modifier
                        .size(if (connected) 52.dp else 48.dp)
                        .graphicsLayer { rotationZ = emblemSpin + ambientTurn }
                        .clip(emblemShape)
                        .background(onTone.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = if (connected) MarbleGlyph.STOP else MarbleGlyph.POWER,
                        color = onTone,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = -(emblemSpin + ambientTurn) },
                        weight = 1.2f
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (slideInVertically { h -> h / 2 } + fadeIn()) togetherWith
                            slideOutVertically { h -> -h / 2 } + fadeOut()
                    },
                    label = "pillTitle"
                ) { t ->
                    Text(
                        text = t,
                        color = onTone,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(
                        targetState = subtitle,
                        modifier = Modifier.weight(1f, fill = false),
                        transitionSpec = {
                            (slideInVertically { h -> h / 3 } + fadeIn(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )) togetherWith slideOutVertically { h -> -h / 3 } + fadeOut()
                        },
                        label = "pillSubtitle"
                    ) { s ->
                        Text(
                            text = s,
                            color = onTone.copy(alpha = 0.84f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (ticker.isNotEmpty()) {
                        MarbleRollingClock(
                            clock = ticker,
                            color = onTone.copy(alpha = 0.92f),
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            if (!working) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(
                            marbleExpressiveShape(
                                if (connected) MarbleExpressiveMotif.SUNNY else MarbleExpressiveMotif.DIAMOND
                            )
                        )
                        .background(onTone.copy(alpha = if (connected) 0.92f else 0.78f))
                )
            }
        }
    }
}

/**
 * The mode chip under the pill: morphs its corners when open and wears an Expressive `Fan` badge
 * instead of a plain circle dot.
 */
@Composable
fun MarbleModeMenuChip(
    selected: ConnectModeChip,
    onSelect: (ConnectModeChip) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }
    val chevronTurn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "modeChipChevron"
    )
    val shape = marbleSelectionMorphShape(
        selected = open,
        idleDp = 16.dp,
        selectedDp = 28.dp
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .clickable(enabled = enabled, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    open = true
                }
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(marbleExpressiveShape(MarbleExpressiveMotif.FAN))
                    .background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = MarbleGlyph.TUNNEL,
                    color = colors.onPrimary,
                    modifier = Modifier.size(10.dp),
                    weight = 1.25f
                )
            }
            Text(
                text = trx(selected.label),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            MarbleGlyphIcon(
                glyph = MarbleGlyph.CHEVRON_DOWN,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(13.dp)
                    .graphicsLayer { rotationZ = chevronTurn },
                weight = 1.15f
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            ConnectModeChip.ordered.forEach { chip ->
                DropdownMenuItem(
                    onClick = {
                        open = false
                        if (chip != selected) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(chip)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = trx(chip.label),
                                color = if (chip == selected) colors.primary else colors.onSurface,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = trx(chip.hint),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Server health
// ---------------------------------------------------------------------------------------------

@Composable
fun marbleHealthColor(health: ServerHealth): Color {
    val colors = MaterialTheme.colorScheme
    return when (health) {
        ServerHealth.GOOD -> colors.success
        ServerHealth.SLOW -> colors.warning
        ServerHealth.DEAD -> colors.onSurfaceVariant.copy(alpha = 0.70f)
        ServerHealth.UNTESTED -> colors.outline
    }
}

/** Expressive polygon health mark (`Flower` good, `Sunny` slow, `Pentagon` dead, `Cookie` idle). */
@Composable
fun MarbleHealthDot(
    health: ServerHealth,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val tone = marbleHealthColor(health)
    val motif = when (health) {
        ServerHealth.GOOD -> MarbleExpressiveMotif.FLOWER
        ServerHealth.SLOW -> MarbleExpressiveMotif.SUNNY
        ServerHealth.DEAD -> MarbleExpressiveMotif.PENTAGON
        ServerHealth.UNTESTED -> MarbleExpressiveMotif.COOKIE
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(marbleExpressiveShape(motif))
            .background(tone.copy(alpha = if (health == ServerHealth.DEAD) 0.65f else 0.92f))
    )
}

@Composable
fun MarbleHealthPill(
    health: ServerHealth,
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tone = marbleHealthColor(health)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHighest.copy(alpha = 0.78f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MarbleHealthDot(health = health, size = 9.dp)
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            color = if (health == ServerHealth.DEAD) colors.onSurfaceVariant else tone,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The live traffic graph
// ---------------------------------------------------------------------------------------------

private enum class TrafficLens(val label: String) {
    BOTH("Down + up"),
    DOWN("Down"),
    UP("Up");

    fun next(): TrafficLens = when (this) {
        BOTH -> DOWN
        DOWN -> UP
        UP -> BOTH
    }
}

private const val TRAFFIC_SAMPLE_MS = 1_000L

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — grid-free live throughput sparkline inside an asymmetric
 * Material 3 Expressive surface with a spring-morphing series lens capsule.
 */
@Composable
fun MarbleTrafficSparkline(
    downBps: Long,
    upBps: Long,
    active: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val samples = remember { mutableStateListOf<TrafficSample>() }
    var lens by remember { mutableStateOf(TrafficLens.BOTH) }
    val liveDown = rememberUpdatedState(downBps)
    val liveUp = rememberUpdatedState(upBps)

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            samples.add(TrafficSample(liveDown.value, liveUp.value))
            while (samples.size > TrafficTimeline.DEFAULT_CAPACITY) samples.removeAt(0)
            delay(TRAFFIC_SAMPLE_MS)
        }
    }

    val snapshot = samples.toList()
    val downValues = snapshot.map { it.downBps }
    val upValues = snapshot.map { it.upBps }
    val peak = trafficPeak(snapshot.map { maxOf(it.downBps, it.upBps) })
    val downSeries = trafficSeries(downValues, peak)
    val upSeries = trafficSeries(upValues, peak)

    val downTone = colors.primary
    val upTone = colors.tertiary
    val lensShape = marbleSelectionMorphShape(
        selected = lens != TrafficLens.BOTH,
        idleDp = 12.dp,
        selectedDp = 24.dp
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .clip(lensShape)
                        .background(colors.surfaceContainerHigh)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lens = lens.next()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(marbleExpressiveShape(MarbleExpressiveMotif.SUNNY))
                            .background(if (lens == TrafficLens.UP) upTone else downTone)
                    )
                    Text(
                        text = trx(lens.label),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Text(
                    text = faDigits(formatRate(peak)) + " " + trx("peak"),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparklineLegend(
                    label = Tr.now.download,
                    value = faDigits(formatRate(snapshot.lastOrNull()?.downBps ?: 0L)),
                    color = downTone,
                    motif = MarbleExpressiveMotif.FAN
                )
                SparklineLegend(
                    label = Tr.now.upload,
                    value = faDigits(formatRate(snapshot.lastOrNull()?.upBps ?: 0L)),
                    color = upTone,
                    motif = MarbleExpressiveMotif.GEM
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(marbleAsymmetricShape(prominent = false, flipped = true))
                .background(colors.surfaceContainer)
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    lens = lens.next()
                }
                .semantics {
                    contentDescription = "Traffic graph — tap to change the series"
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val hasLiveData = downSeries.size > 1 || upSeries.size > 1
                val showDown = lens != TrafficLens.UP
                val showUp = lens != TrafficLens.DOWN
                if (hasLiveData) {
                    if (showDown && downSeries.size > 1) {
                        drawSeries(
                            values = downSeries,
                            width = w,
                            height = h,
                            tone = downTone,
                            smooth = !reducedMotion
                        )
                    }
                    if (showUp && upSeries.size > 1) {
                        drawSeries(
                            values = upSeries,
                            width = w,
                            height = h,
                            tone = upTone,
                            smooth = !reducedMotion
                        )
                    }
                } else {
                    val idleDown = (0..28).map { i ->
                        0.46f + 0.14f * kotlin.math.sin(i * 0.52).toFloat() +
                            0.04f * kotlin.math.cos(i * 0.27).toFloat()
                    }
                    val idleUp = (0..28).map { i ->
                        0.38f + 0.11f * kotlin.math.cos(i * 0.48).toFloat() +
                            0.03f * kotlin.math.sin(i * 0.31).toFloat()
                    }
                    if (showDown) {
                        drawSeries(
                            values = idleDown,
                            width = w,
                            height = h,
                            tone = downTone.copy(alpha = 0.22f),
                            smooth = true
                        )
                    }
                    if (showUp) {
                        drawSeries(
                            values = idleUp,
                            width = w,
                            height = h,
                            tone = upTone.copy(alpha = 0.18f),
                            smooth = true
                        )
                    }
                }
            }

            if (samples.size < 2) {
                Text(
                    text = if (active) {
                        trx("Sampling the tunnel…")
                    } else {
                        trx("Connect to see live traffic")
                    },
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SparklineLegend(
    label: String,
    value: String,
    color: Color,
    motif: MarbleExpressiveMotif
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(marbleExpressiveShape(motif))
                    .background(color)
            )
            Text(
                text = label,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Text(
            text = value,
            color = colors.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Float>,
    width: Float,
    height: Float,
    tone: Color,
    smooth: Boolean
) {
    val line = sparklinePoints(values, width, height)
    val area = sparklineAreaPoints(values, width, height)
    if (line.size < 2 || area.size < 3) return

    val linePath = smoothPath(line, smooth)
    val areaPath = smoothPath(area, smooth = false)
    areaPath.close()

    drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(
            colors = listOf(tone.copy(alpha = 0.26f), tone.copy(alpha = 0f)),
            startY = 0f,
            endY = height
        )
    )
    drawPath(
        path = linePath,
        color = tone,
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun smoothPath(points: List<Pair<Float, Float>>, smooth: Boolean): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().first, points.first().second)
    if (!smooth) {
        for (index in 1 until points.size) {
            path.lineTo(points[index].first, points[index].second)
        }
        return path
    }
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val midX = (previous.first + current.first) / 2f
        path.cubicTo(
            midX,
            previous.second,
            midX,
            current.second,
            current.first,
            current.second
        )
    }
    return path
}

// ---------------------------------------------------------------------------------------------
// Quick core logs
// ---------------------------------------------------------------------------------------------

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QuickLogsSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var lines by remember { mutableStateOf(emptyList<QuickLogLine>()) }
    var loaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        while (true) {
            val raw = withContext(Dispatchers.IO) {
                runCatching { repo.readCoreLogTail() }.getOrDefault("")
            }
            lines = quickLogLines(raw)
            loaded = true
            delay(1_500L)
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            runCatching { listState.scrollToItem(lines.size - 1) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        shape = MarbleSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
                    bottom = 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = trx("Core logs"),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = logSummaryLabel(lines, loaded),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                MarbleHealthDot(health = ServerHealth.GOOD, size = 14.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 420.dp)
                    .clip(marbleAsymmetricShape(prominent = false))
                    .background(colors.surfaceContainerLow)
            ) {
                when {
                    !loaded -> Text(
                        text = trx("Reading the core…"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    lines.isEmpty() -> Text(
                        text = trx("No runtime log entries yet."),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 20.dp)
                    )

                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line.text,
                                color = logLineColor(line.level, colors.onSurface, colors),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun logSummaryLabel(lines: List<QuickLogLine>, loaded: Boolean): String {
    if (!loaded) return trx("Reading the core…")
    if (lines.isEmpty()) return trx("No runtime log entries yet.")
    val errors = lines.count { it.level == LogLevel.ERROR }
    val warnings = lines.count { it.level == LogLevel.WARN }
    if (errors > 0 || warnings > 0) {
        return listOfNotNull(
            errors.takeIf { it > 0 }?.let { faDigits("$it") + " " + trx("errors") },
            warnings.takeIf { it > 0 }?.let { faDigits("$it") + " " + trx("warnings") }
        ).joinToString(" · ")
    }
    return trx("All clear") + " · " + faDigits("${lines.size}") + " " + trx("lines")
}

@Composable
private fun logLineColor(
    level: LogLevel,
    plain: Color,
    colors: androidx.compose.material3.ColorScheme
): Color = when (level) {
    LogLevel.ERROR -> colors.error
    LogLevel.WARN -> colors.warning
    LogLevel.INFO -> colors.onSurface
    LogLevel.DEBUG -> colors.onSurfaceVariant
    LogLevel.PLAIN -> plain
}

// ---------------------------------------------------------------------------------------------
// Smart presets
// ---------------------------------------------------------------------------------------------

/**
 * Three-up Expressive preset capsules on Home. Each preset wears its own Material 3 Expressive
 * polygon (`Sunny`, `Fan`, `Clover4Leaf`) and morphs from a soft squircle into a stadium pill when
 * active.
 */
@Composable
fun SmartPresetStrip(
    repo: AppRepository,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val selected = matchSmartPreset(repo.settings)
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarbleSectionHeader(
            title = trx("Tuned for you"),
            trailing = {
                Text(
                    text = trx("All presets"),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { onOpenAll() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmartPreset.ordered.forEach { preset ->
                val active = preset == selected
                val appliedLabel = trx(preset.title) + " • " + trx("applied")
                val motif = when (preset) {
                    SmartPreset.GAMING -> MarbleExpressiveMotif.SUNNY
                    SmartPreset.STREAMING -> MarbleExpressiveMotif.FAN
                    SmartPreset.EXTREME_BYPASS -> MarbleExpressiveMotif.CLOVER
                }
                val badgeTint = when (preset) {
                    SmartPreset.GAMING -> colors.warning
                    SmartPreset.STREAMING -> colors.primary
                    SmartPreset.EXTREME_BYPASS -> colors.tertiary
                }
                val shape = marbleSelectionMorphShape(
                    selected = active,
                    idleDp = 16.dp,
                    selectedDp = 28.dp
                )
                val bg by animateColorAsState(
                    targetValue = if (active) colors.primaryContainer else colors.surfaceContainerLow,
                    animationSpec = marbleDefaultEffectsSpec(),
                    label = "presetStripBg"
                )
                val fg = if (active) colors.onPrimaryContainer else colors.onSurface

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(shape)
                        .background(bg)
                        .clickable(role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            repo.updateSettings(preset.apply(repo.settings))
                            repo.setRuntimeMessage(appliedLabel)
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(marbleExpressiveShape(motif))
                            .background(if (active) colors.primary else badgeTint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = presetGlyph(preset),
                            color = if (active) colors.onPrimary else badgeTint,
                            modifier = Modifier.size(14.dp),
                            weight = 1.15f
                        )
                    }
                    Text(
                        text = trx(preset.title),
                        color = fg,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The full preset sheet: one decision, its consequences, one tap. */
@Composable
fun SmartPresetSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val active = matchSmartPreset(repo.settings)

    MarbleBottomSheet(
        title = trx("Smart presets"),
        subtitle = trx("One tap, and the app tunes itself — your routing, DNS and servers stay as they are."),
        onDismiss = onDismiss
    ) {
        SmartPreset.ordered.forEach { preset ->
            val selected = preset == active
            val appliedLabel = trx(preset.title) + " • " + trx("applied")
            val motif = when (preset) {
                SmartPreset.GAMING -> MarbleExpressiveMotif.SUNNY
                SmartPreset.STREAMING -> MarbleExpressiveMotif.FAN
                SmartPreset.EXTREME_BYPASS -> MarbleExpressiveMotif.CLOVER
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(marbleSelectionMorphShape(selected = selected, idleDp = 20.dp, selectedDp = 28.dp))
                    .background(
                        if (selected) {
                            colors.primaryContainer
                        } else {
                            colors.surfaceContainerLow
                        }
                    )
                    .clickable {
                        repo.updateSettings(preset.apply(repo.settings))
                        repo.setRuntimeMessage(appliedLabel)
                        onDismiss()
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(marbleExpressiveShape(motif))
                            .background((if (selected) colors.primary else colors.secondary).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = presetGlyph(preset),
                            color = if (selected) colors.primary else colors.secondary,
                            modifier = Modifier.size(18.dp),
                            weight = 1.1f
                        )
                    }
                    Text(
                        text = trx(preset.title),
                        color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        MarbleGlyphIcon(
                            glyph = MarbleGlyph.CHECK,
                            color = colors.primary,
                            modifier = Modifier.size(16.dp),
                            weight = 1.15f
                        )
                    }
                }
                Text(
                    text = trx(preset.body),
                    color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun presetGlyph(preset: SmartPreset): MarbleGlyph = when (preset) {
    SmartPreset.GAMING -> MarbleGlyph.SPARK
    SmartPreset.STREAMING -> MarbleGlyph.DOWNLOAD
    SmartPreset.EXTREME_BYPASS -> MarbleGlyph.SHIELD
}
