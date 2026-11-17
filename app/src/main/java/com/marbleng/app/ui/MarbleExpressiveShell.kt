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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ConnectControl
import com.marbleng.app.model.ConnectModeChip
import com.marbleng.app.model.ConnectPhase
import com.marbleng.app.model.ConnectPillMotion
import com.marbleng.app.model.ConnectPulse
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
import com.marbleng.app.ui.theme.MarbleRadius
import com.marbleng.app.ui.theme.MarbleSelectionRung
import com.marbleng.app.ui.theme.MarbleSheetShape
import com.marbleng.app.ui.theme.MarbleSpace
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTone
import com.marbleng.app.ui.theme.MarbleVeil
import com.marbleng.app.ui.theme.MarbleWash
import com.marbleng.app.ui.theme.marbleDefaultEffectsSpec
import com.marbleng.app.ui.theme.marbleDefaultSpatialSpec
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onToneColor
import com.marbleng.app.ui.theme.rememberReducedMotion
import com.marbleng.app.ui.theme.toneColor
import com.marbleng.app.ui.theme.warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// The one connect control
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 / MARBLE_STEADY_CONNECT_V264 — the single primary action of the
 * app.
 *
 * Replaces non-Expressive `CircleShape` halos/dots and linear gradients with solid Material 3
 * Expressive tonal/status fills, spatial-spring corner morphing, a `Clover4Leaf` ↔ `Flower`
 * morphing emblem, and Google's Expressive `LoadingIndicator` while negotiating the tunnel.
 *
 * V264 fixed the pill's four defects in the same state table ([ConnectControl]) the two dock
 * buttons use: `CONNECTING` no longer says "Connect" while a tap cancels the dial (the caller now
 * passes a state-aware [title]), `DISCONNECTING` is disabled instead of a dead tap, `BLOCKED` gets
 * its own error tone and a reset glyph, and the pill keeps **one** width and height in every phase:
 * the old 78 → 88dp height spring and the outer 20 → 0dp inset pushed the whole Home row around on
 * connect. The inset spring is kept — with its V242 crash guard — as an *inner* content breathing
 * that cannot resize the pill, and the emblem, its loading twin and the trailing mark reserve the
 * same space in all states so the row never needs a size animation of its own.
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
    ticker: String = "",
    blocked: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()
    val motion = marbleMotion()
    val phase = ConnectControl.phaseOf(connected, connecting, disconnecting, blocked)
    val working = ConnectControl.working(phase)
    val pressable = ConnectControl.pressable(phase)
    val spokenState = trx(ConnectControl.stateKey(phase))

    // MARBLE_ONE_LANGUAGE_V281 — the pill used to carry its own copy of the verdict table (and a
    // second copy for the ink inside it). The phase is resolved by ConnectControl, the tone by
    // connectPhaseTone, and the scheme answers both colours, so the pill cannot disagree with the
    // hero, the dock dot or the IP headline about what a state looks like.
    val verdict = connectPhaseTone(phase)
    val tone by animateColorAsState(
        targetValue = colors.toneColor(verdict),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connectPillTone"
    )
    val onTone = colors.onToneColor(verdict)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // V242: Effects springs do not overshoot; ConnectPillMotion.insetDp is the second line of
    // defence. V264 moved this padding *inside* the fixed-width pill, so its travel can no longer
    // resize the card (or the row it sits in).
    val inset by animateDpAsState(
        targetValue = if (phase == ConnectPhase.CONNECTED) 0.dp else ConnectPillMotion.RESTING_INSET_DP.dp,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connectPillInset"
    )
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 20.dp
            phase == ConnectPhase.CONNECTED -> 26.dp
            working || phase == ConnectPhase.BLOCKED -> 30.dp
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
            phase == ConnectPhase.CONNECTED -> 1f
            pressed -> 0.55f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "connectPillStateMorph"
    )
    val emblemSpin by animateFloatAsState(
        targetValue = when {
            pressed -> -18f
            phase == ConnectPhase.CONNECTED -> 45f
            else -> 0f
        },
        animationSpec = if (motion.still) snap() else marbleDefaultSpatialSpec(),
        label = "connectPillSpin"
    )

    val ambientTurn = if (phase == ConnectPhase.CONNECTED && motion.loops) {
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

    // MARBLE_CONNECT_ISLAND_V276 — the pill wears a soft state-coloured glow behind one fixed
    // tonal surface: the halo breathes with the phase, the island never changes size, and only
    // the corners and the colour move between states.
    val glow by animateFloatAsState(
        targetValue = when {
            phase == ConnectPhase.CONNECTED -> 0.30f
            working -> 0.15f
            else -> 0f
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connectPillGlow"
    )
    // V279: the same bounded heartbeat as the dock, painted inside the pill's fixed 82dp box.
    val heartbeat = if (ConnectPulse.active(phase, motion.loops)) {
        val transition = rememberInfiniteTransition(label = "homeConnectedPulse")
        val value by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(ConnectPulse.PERIOD_MS)),
            label = "homePulsePhase"
        )
        value
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            // One height in every phase: a connect must not push the rows below the pill down.
            .height(82.dp)
            .scale(pressScale)
            .clickable(
                enabled = pressable,
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onToggle()
            }
            .semantics {
                contentDescription = title
                stateDescription = spokenState
            },
        contentAlignment = Alignment.Center
    ) {
        // The halo: wraps the island inside the fixed 82dp box so it never pushes rows around.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(shape)
                .background(tone.copy(alpha = glow))
        )
        if (phase == ConnectPhase.CONNECTED) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(82.dp).testTag("home-connect-pulse")
            ) {
                val pulseInset = 3.dp.toPx() + ConnectPulse.ringRadiusFraction(heartbeat) * 5.dp.toPx()
                drawRoundRect(
                    color = tone.copy(alpha = ConnectPulse.ringAlpha(heartbeat, 0.7f)),
                    topLeft = Offset(pulseInset, pulseInset),
                    size = Size(size.width - pulseInset * 2f, size.height - pulseInset * 2f),
                    cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
                    style = Stroke(1.5.dp.toPx())
                )
            }
        }
        // The tonal island: inner breathing inset keeps outer bounds steady while content sits
        // inside a properly padded Material 3 Expressive surface.
        Box(
            modifier = Modifier
                .padding(horizontal = ConnectPillMotion.insetDp(inset.value).dp)
                .fillMaxWidth()
                .height(74.dp)
                .clip(shape)
                .background(tone),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MarbleSpace.TILE.dp, vertical = MarbleSpace.INNER.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp)
            ) {
                if (working) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(marbleExpressiveShape(MarbleExpressiveMotif.BURST))
                            .background(onTone.copy(alpha = MarbleWash.TINT)),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleWaveRing(size = 24.dp, color = onTone)
                    }
                } else {
                    val emblemShape = marbleMorphingShape(
                        from = MarbleExpressiveMotif.CLOVER,
                        to = MarbleExpressiveMotif.FLOWER,
                        progress = stateMorph
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { rotationZ = emblemSpin + ambientTurn }
                            .clip(emblemShape)
                            .background(onTone.copy(alpha = MarbleWash.TINT)),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = when (phase) {
                                ConnectPhase.CONNECTED -> MarbleGlyph.STOP
                                ConnectPhase.BLOCKED -> MarbleGlyph.REFRESH
                                else -> MarbleGlyph.POWER
                            },
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
                    verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)
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
                                color = onTone.copy(alpha = MarbleVeil.NEAR),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (ticker.isNotEmpty()) {
                            MarbleRollingClock(
                                clock = ticker,
                                color = onTone.copy(alpha = MarbleVeil.NEAR),
                                modifier = Modifier.padding(start = MarbleSpace.ROW_GAP.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                // Trailing Expressive badge reserves fixed space inside the Row so text never collides.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(MarbleRadius.CONTROL.dp))
                        .background(
                            onTone.copy(
                                alpha = when {
                                    working -> 0.12f
                                    phase == ConnectPhase.CONNECTED -> 0.18f
                                    else -> 0.16f
                                }
                            )
                        )
                        .padding(horizontal = MarbleSpace.INNER.dp, vertical = MarbleSpace.SM.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(
                                    marbleExpressiveShape(
                                        if (phase == ConnectPhase.CONNECTED) {
                                            MarbleExpressiveMotif.SUNNY
                                        } else {
                                            MarbleExpressiveMotif.DIAMOND
                                        }
                                    )
                                )
                                .background(
                                    onTone.copy(
                                        alpha = when {
                                            working -> 0.45f
                                            phase == ConnectPhase.CONNECTED -> 0.95f
                                            else -> 0.82f
                                        }
                                    )
                                )
                        )
                        MarbleGlyphIcon(
                            glyph = when (phase) {
                                ConnectPhase.CONNECTED -> MarbleGlyph.STOP
                                ConnectPhase.BLOCKED -> MarbleGlyph.REFRESH
                                ConnectPhase.CONNECTING, ConnectPhase.DISCONNECTING -> MarbleGlyph.TUNNEL
                                ConnectPhase.IDLE -> MarbleGlyph.CHEVRON
                            },
                            color = onTone,
                            modifier = Modifier.size(13.dp),
                            weight = 1.2f
                        )
                    }
                }
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
        rung = MarbleSelectionRung.CONTROL
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
                .padding(start = MarbleSpace.MD.dp, end = MarbleSpace.MD.dp, top = MarbleSpace.SM.dp, bottom = MarbleSpace.SM.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp)
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
                        Column(verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)) {
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
fun marbleHealthColor(health: ServerHealth): Color =
    // MARBLE_ONE_LANGUAGE_V281 — the health verdict is named here and coloured once, by the same
    // resolver the ping cell, the council page and the plan cards ask. This table used to be one of
    // eleven private answers to "what colour is this verdict?".
    MaterialTheme.colorScheme.toneColor(healthTone(health))

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
            .clip(RoundedCornerShape(MarbleRadius.ROW_ICON.dp))
            .background(colors.surfaceContainerHighest.copy(alpha = MarbleVeil.QUIET))
            .padding(start = MarbleSpace.SM.dp, end = MarbleSpace.INNER.dp, top = MarbleSpace.XS.dp, bottom = MarbleSpace.XS.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
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
        rung = MarbleSelectionRung.COMPACT
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MarbleSpace.INNER.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MarbleSpace.XS.dp)) {
                Row(
                    modifier = Modifier
                        .clip(lensShape)
                        .background(colors.surfaceContainerHigh)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            lens = lens.next()
                        }
                        .padding(horizontal = MarbleSpace.INNER.dp, vertical = MarbleSpace.XS.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
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
                horizontalArrangement = Arrangement.spacedBy(MarbleSpace.MD.dp)
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
                            tone = downTone.copy(alpha = MarbleWash.STRONG),
                            smooth = true
                        )
                    }
                    if (showUp) {
                        drawSeries(
                            values = idleUp,
                            width = w,
                            height = h,
                            tone = upTone.copy(alpha = MarbleWash.TINT),
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
                        .padding(horizontal = MarbleSpace.LG.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(MarbleSpace.HAIR.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
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
            colors = listOf(tone.copy(alpha = MarbleWash.STRONG), tone.copy(alpha = 0f)),
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

    // MARBLE_ONE_LANGUAGE_V281 — this sheet was a hand-copy of the kit's sheet: the same container
    // colour, the same MarbleSheetShape, the same gutters and the same title/subtitle row, drawn a
    // second time. It now *is* the kit's sheet, with the live dot in the trailing slot and no close
    // button, because a log panel is dismissed by the scrim or a swipe like every other sheet.
    MarbleBottomSheet(
        title = trx("Core logs"),
        subtitle = logSummaryLabel(lines, loaded),
        onDismiss = onDismiss,
        showsClose = false,
        trailing = { MarbleHealthDot(health = ServerHealth.GOOD, size = 14.dp) }
    ) {
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
                        .padding(horizontal = MarbleSpace.XL.dp)
                )

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(MarbleSpace.MD.dp),
                    verticalArrangement = Arrangement.spacedBy(MarbleSpace.XS.dp)
                ) {
                    items(lines) { line ->
                        Text(
                            text = line.text,
                            color = logLineColor(line.level, colors.onSurface, colors),
                            // MARBLE_ONE_LANGUAGE_V281 — the log line was bodySmall forced down
                            // to 11 sp, which is exactly the labelSmall slot: one ladder, so the
                            // override goes and the slot stays.
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
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
    LogLevel.ERROR -> colors.toneColor(MarbleTone.POOR)
    LogLevel.WARN -> colors.toneColor(MarbleTone.FAIR)
    // An ordinary line is the page's own ink; it is not a verdict and takes no verdict colour.
    LogLevel.INFO -> colors.onSurface
    LogLevel.DEBUG -> colors.toneColor(MarbleTone.UNKNOWN)
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
        verticalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp)
    ) {
        MarbleSectionHeader(
            title = trx("Tuned for you"),
            trailing = {
                Text(
                    text = trx("All presets"),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(MarbleRadius.ROW_ICON.dp))
                        .clickable(role = Role.Button) { onOpenAll() }
                        .padding(horizontal = MarbleSpace.SM.dp, vertical = MarbleSpace.XS.dp)
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MarbleSpace.SM.dp)
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
                    rung = MarbleSelectionRung.CONTROL
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
                        .padding(horizontal = MarbleSpace.INNER.dp, vertical = MarbleSpace.INNER.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(marbleExpressiveShape(motif))
                            .background(if (active) colors.primary else badgeTint.copy(alpha = MarbleWash.TINT)),
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
                    .clip(
                        marbleSelectionMorphShape(
                            selected = selected,
                            rung = MarbleSelectionRung.CONTROL
                        )
                    )
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
                    .padding(MarbleSpace.LG.dp),
                verticalArrangement = Arrangement.spacedBy(MarbleSpace.ROW_GAP.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MarbleSpace.INNER.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(marbleExpressiveShape(motif))
                            .background((if (selected) colors.primary else colors.secondary).copy(alpha = MarbleWash.TINT)),
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
                    color = if (selected) colors.onPrimaryContainer.copy(alpha = MarbleVeil.NEAR) else colors.onSurfaceVariant,
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
