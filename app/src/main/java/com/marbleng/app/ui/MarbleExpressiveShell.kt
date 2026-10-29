package com.marbleng.app.ui

// MARBLE_EXPRESSIVE_SHELL_V241 — the five controls that replace the clutter.
//
// The audit of the old Home screen was blunt: one job (connect) had five controls (a power
// orb, a slide track and three deck cards), so the user had to decide *how* to connect before
// deciding *whether* to. The screen now has one pill, one menu chip and one status chip. The
// same chapter answers the rest of the audit here:
//
//   * [MarbleTrafficSparkline]  — a live, tap-to-inspect throughput graph instead of four
//                                 dead strings, drawn on the window's own peak so a slow link
//                                 and a fast link both fill the height.
//   * [MarbleHealthPill]        — mint / amber / muted-grey capsule + dot for a probe result,
//                                 never the old error-red field behind the rows.
//   * [QuickLogsSheet]          — the live core tail one tap from Home, not five taps into
//                                 Settings → Diagnostics.
//   * [SmartPresetStrip]        — Gaming / Streaming / Extreme Bypass over the knobs that used
//                                 to be raw switches.
//
// Every animation is a theme spring or a Canvas; no new dependency and no View is introduced.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.marbleng.app.model.QuickLogLine
import com.marbleng.app.model.ServerHealth
import com.marbleng.app.model.SmartPreset
import com.marbleng.app.model.TrafficSample
import com.marbleng.app.model.TrafficTimeline
import com.marbleng.app.model.LogLevel
import com.marbleng.app.model.matchSmartPreset
import com.marbleng.app.model.quickLogLines
import com.marbleng.app.model.sparklineAreaPoints
import com.marbleng.app.model.sparklinePoints
import com.marbleng.app.model.trafficPeak
import com.marbleng.app.model.trafficSeries
import com.marbleng.app.ui.theme.MarbleCardShape
import com.marbleng.app.ui.theme.MarbleSheetShape
import com.marbleng.app.ui.theme.MarbleSpec
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
 * The single primary action of the app: a wide pill that squishes on press and grows into its
 * connected shape (taller, wider, flatter corners) when the tunnel is up.
 *
 * There is no second copy of this control anywhere on Home — no orb above it, no slide track
 * under it and no row of deck cards beside it. Changing *how* it connects is the chip below.
 */
@Composable
fun MarbleConnectPill(
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    title: String,
    subtitle: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
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

    // The squish: a connected pill is 8 dp taller and 20 dp wider (it gives back its side
    // insets), with a slightly flatter corner. Unconnected it is the classic stadium.
    val height by animateDpAsState(
        targetValue = if (connected) 88.dp else 76.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "connectPillHeight"
    )
    // V242: padding is a layout constraint, not a springy visual. The expressive spatial
    // spring overshoots 20 -> 0 below zero and crashed every connect/reconnect. Effects
    // springs do not overshoot; ConnectPillMotion.insetDp is the second line of defence.
    val inset by animateDpAsState(
        targetValue = if (connected) 0.dp else ConnectPillMotion.RESTING_INSET_DP.dp,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connectPillInset"
    )
    val corner by animateDpAsState(
        targetValue = if (connected) 28.dp else 38.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "connectPillCorner"
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) MarbleSpec.PRESSED_SCALE else 1f,
        animationSpec = if (reducedMotion) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "connectPillPress"
    )

    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ConnectPillMotion.insetDp(inset.value).dp)
            .height(height)
            .scale(pressScale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(tone, tone.copy(alpha = 0.86f))
                )
            )
            .clickable(
                enabled = !disconnecting,
                role = Role.Button,
                interactionSource = interaction,
                indication = null
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onToggle()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (connected) 48.dp else 44.dp)
                    .clip(CircleShape)
                    .background(onTone.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = if (connected || connecting) MarbleGlyph.STOP else MarbleGlyph.POWER,
                    color = onTone,
                    modifier = Modifier.size(22.dp),
                    weight = 1.2f
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = onTone,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = onTone.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (connected) {
                MarblePulseDot(color = onTone)
            }
        }
    }
}

/**
 * The mode chip under the pill. One tap opens a three-item menu; the current mode is the chip
 * label, so the answer to "which mode am I in?" never needs a tap at all.
 */
@Composable
fun MarbleModeMenuChip(
    selected: ConnectModeChip,
    onSelect: (ConnectModeChip) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .clickable(enabled = enabled, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    open = true
                }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
            Text(
                text = trx(selected.label),
                color = colors.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            MarbleGlyphIcon(
                glyph = MarbleGlyph.CHEVRON_DOWN,
                color = colors.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
                weight = 1.1f
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

/** The colour of one health state — mint, amber, muted grey and the quiet untested grey. */
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

/** A dot that says the state in one glance, with a soft halo for the live states. */
@Composable
fun MarbleHealthDot(
    health: ServerHealth,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val tone = marbleHealthColor(health)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone.copy(alpha = if (health == ServerHealth.DEAD) 0.55f else 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size * 0.52f)
                .clip(CircleShape)
                .background(tone)
        )
    }
}

/**
 * Capsule badge for a probe result: dot + value on the card's own surface container.
 * No filled red field, no error container behind the row — a slow server is amber ink on a
 * neutral capsule, which is a reading, not an alarm.
 */
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
            .clip(CircleShape)
            .background(colors.surfaceContainerHighest.copy(alpha = 0.75f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MarbleHealthDot(health = health, size = 8.dp)
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
 * A live sparkline of tunnel throughput. It samples the repository's rate once a second into
 * a one-minute window, then draws that window against its own peak — so the graph answers
 * "is data moving?" at any link speed.
 *
 * Tap the graph to switch the lens between down+up, down only and up only. The peak and the
 * current rate sit above it as numbers, because the graph is a shape and the numbers are truth.
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = trx(lens.label),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
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
                    color = downTone
                )
                SparklineLegend(
                    label = Tr.now.upload,
                    value = faDigits(formatRate(snapshot.lastOrNull()?.upBps ?: 0L)),
                    color = upTone
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(MarbleCardShape)
                .background(colors.surfaceContainerLow)
                .clickable(role = Role.Button) {
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

                // Two quiet grid lines: the peak and the half-height, so the shape has a scale.
                val gridTone = colors.outlineVariant.copy(alpha = 0.45f)
                drawLine(
                    color = gridTone,
                    start = androidx.compose.ui.geometry.Offset(0f, h * 0.08f),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.08f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridTone.copy(alpha = 0.25f),
                    start = androidx.compose.ui.geometry.Offset(0f, h * 0.54f),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.54f),
                    strokeWidth = 1f
                )

                val showDown = lens != TrafficLens.UP
                val showUp = lens != TrafficLens.DOWN
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
private fun SparklineLegend(label: String, value: String, color: Color) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
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

/**
 * Draw one normalised series. A DrawScope is not a composable scope, so every colour and
 * every geometry value arrives as a parameter — the same rule the glyph art follows.
 */
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
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawCircle(
        color = tone,
        radius = 3.dp.toPx(),
        center = androidx.compose.ui.geometry.Offset(line.last().first, line.last().second)
    )
}

/** Build a path through the points; [smooth] swaps the straight segments for mid-point cubics. */
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

/**
 * The live core tail, in a tall sheet, one tap from wherever the status is shown.
 *
 * It polls the manager's own log file every 1.5 s on the IO dispatcher (never the main
 * thread) and keeps the last [com.marbleng.app.model.DEFAULT_QUICK_LOG_LINES] classified
 * lines. The overlay is deliberately read-only: copy is one tap, editing is Settings' job.
 */
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
                MarblePulseDot(color = colors.success)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 420.dp)
                    .clip(MarbleCardShape)
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

/**
 * The one-line verdict above the log list. Counts are assembled from translated parts rather
 * than from a translated sentence, because "3 errors · 5 warnings" has two numbers in it and
 * a phrase table cannot carry those.
 */
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
 * The three named decisions as a chip strip. The active preset is derived from the settings
 * themselves, so a hand-tuned configuration simply shows no chip selected.
 */
@Composable
fun SmartPresetStrip(
    repo: AppRepository,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clickable { onOpenAll() }
                )
            }
        )
        MarbleChipStrip {
            SmartPreset.ordered.forEach { preset ->
                val appliedLabel = trx(preset.title) + " • " + trx("applied")
                MarblePillChip(
                    label = trx(preset.title),
                    selected = preset == selected,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        repo.updateSettings(preset.apply(repo.settings))
                        repo.setRuntimeMessage(appliedLabel)
                    }
                )
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
        subtitle = trx("One tap bundles the tuning knobs — routing, DNS and servers stay untouched."),
        onDismiss = onDismiss
    ) {
        SmartPreset.ordered.forEach { preset ->
            val selected = preset == active
            val appliedLabel = trx(preset.title) + " • " + trx("applied")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MarbleCardShape)
                    .background(
                        if (selected) {
                            colors.primary.copy(alpha = 0.12f)
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
                    MarbleRowIcon(
                        glyph = presetGlyph(preset),
                        tint = if (selected) colors.primary else colors.secondary,
                        size = 34.dp,
                        glyphSize = 18.dp
                    )
                    Text(
                        text = trx(preset.title),
                        color = colors.onSurface,
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
                    color = colors.onSurfaceVariant,
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
