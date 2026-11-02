@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.ConnectControl
import com.marbleng.app.model.ConnectPhase
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.DockStudio
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.onSuccess
import com.marbleng.app.ui.theme.onSuccessContainer
import com.marbleng.app.ui.theme.onWarning
import com.marbleng.app.ui.theme.onWarningContainer
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.successContainer
import com.marbleng.app.ui.theme.warning
import com.marbleng.app.ui.theme.warningContainer

/**
 * MARBLE_STEADY_CONNECT_V264 — everything the dock needs to know about the tunnel, in one value.
 *
 * The booleans arrive from the shell; the *meaning* of them is resolved once by [ConnectControl], so
 * the island, the power button and the centre trigger cannot disagree about what "connected" means.
 * [blocked] is the state the old dock had no word for: a kill switch or a refused startup still owns
 * a tunnel the reader must be able to reset.
 */
internal data class DockConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val disconnecting: Boolean = false,
    val blocked: Boolean = false,
    val latencyMs: Int = 0,
    val pingState: ConnectionPingState = ConnectionPingState.IDLE,
    val probing: Boolean = false,
    val probeDone: Int = 0,
    val probeTotal: Int = 0
) {
    val phase: ConnectPhase
        get() = ConnectControl.phaseOf(connected, connecting, disconnecting, blocked)

    /** Both ends of a transition show the same indeterminate activity. */
    val working: Boolean get() = ConnectControl.working(phase)

    /** A closing tunnel is visibly disabled, not a tap the handler silently swallows. */
    val pressable: Boolean get() = ConnectControl.pressable(phase)

    val actionKey: String get() = ConnectControl.actionKey(phase)

    val stateKey: String get() = ConnectControl.stateKey(phase)

    /** "48 ms", or the ping's own name while it is being measured. */
    val latencyLabel: String
        get() = when {
            pingState == ConnectionPingState.MEASURING -> "\u2026"
            latencyMs > 0 -> DockStudio.pingLabel(latencyMs)
            else -> "\u2014"
        }
}

/**
 * MARBLE_STEADY_CONNECT_V264 — Style A's left wing: the Dynamic Island capsule.
 *
 * It is a *badge*, not a banner. One tap pings the active route and the reading lands inline; while
 * a multi-server sweep runs it becomes a silent batch tracker. V264 pins its width to
 * [DockStudio.islandWidthDp]: the old `widthIn(max = …)` let "—", "48 ms" and "12/79 pinging…" each
 * take a different amount of the dock's weighted row, so a ping or a connect moved every tab. The
 * reading now ellipsizes inside one reserved space and the tabs never learn that it changed.
 */
@Composable
internal fun MarbleConnectionIslandCapsule(
    state: DockConnectionState,
    size: DockSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val haptics = LocalHapticFeedback.current

    val base = when {
        state.phase == ConnectPhase.CONNECTED -> colors.successContainer
        state.working -> colors.warningContainer
        state.phase == ConnectPhase.BLOCKED -> colors.errorContainer
        else -> colors.surfaceContainerHighest
    }
    val ink = when {
        state.phase == ConnectPhase.CONNECTED -> colors.onSuccessContainer
        state.working -> colors.onWarningContainer
        state.phase == ConnectPhase.BLOCKED -> colors.onErrorContainer
        else -> colors.onSurfaceVariant
    }

    // A connected capsule breathes between two tonal steps; reduced motion holds the brighter one.
    val pulse = if (!motion.still && state.phase == ConnectPhase.CONNECTED) {
        val transition = rememberInfiniteTransition(label = "islandPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.86f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "islandPulseAlpha"
        )
        alpha
    } else {
        1f
    }

    val container by animateColorAsState(
        targetValue = base.copy(alpha = pulse),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "islandContainer"
    )
    val height = DockStudio.islandHeightDp(size)
    val horizontal = DockStudio.islandPaddingDp(size)
    val spoken = trx("Ping the active route")
    val islandShape = RoundedCornerShape(DockStudio.innerRadiusDp(size))

    Row(
        modifier = modifier
            .height(height.dp)
            // Fixed, not content-driven: the readout cannot resize the dock's weighted row.
            .width(DockStudio.islandWidthDp(size).dp)
            .clip(islandShape)
            .background(container)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = spoken }
            .testTag("dock-island")
            .padding(horizontal = horizontal.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        state.phase == ConnectPhase.CONNECTED -> colors.success
                        state.working -> colors.warning
                        state.phase == ConnectPhase.BLOCKED -> colors.error
                        else -> colors.outline
                    }
                )
        )
        AnimatedContent(
            targetState = if (state.probing) {
                // Compact: the micro bar beside it already says a batch is running.
                DockStudio.batchCompactLabel(state.probeDone, state.probeTotal)
            } else {
                state.latencyLabel
            },
            // Weight, not content width: on the smallest rung "12/79" plus the micro bar must
            // ellipsize into the reserved space instead of spilling out of the capsule.
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (scaleIn(initialScale = 0.9f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.9f) + fadeOut())
            },
            label = "islandReadout"
        ) { readout ->
            Text(
                text = faDigits(readout),
                color = ink,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        AnimatedVisibility(
            visible = state.probing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            DockStudio.batchProgress(state.probeDone, state.probeTotal)
                                .coerceIn(0.02f, 1f)
                        )
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(ink)
                        .testTag("dock-island-progress")
                )
            }
        }
    }
}

/**
 * MARBLE_STEADY_CONNECT_V264 — Style A's right wing: the power button.
 *
 * V256 morphed POWER ↔ STOP on `connected` alone. A dial in flight still showed POWER and still
 * said "Connect" while the tap actually cancelled the dial; a closing tunnel stayed clickable and
 * did nothing; `BLOCKED` looked like an idle button. All three are answered from [ConnectPhase]:
 * the verb, the tone, the glyph, the activity indicator and `enabled` come from one table.
 */
@Composable
internal fun MarbleDockPowerButton(
    state: DockConnectionState,
    size: DockSize,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val side = DockStudio.powerButtonSizeDp(size)
    val phase = state.phase

    val container by animateColorAsState(
        targetValue = when {
            phase == ConnectPhase.CONNECTED -> colors.success
            state.working -> colors.warning
            phase == ConnectPhase.BLOCKED -> colors.error
            else -> colors.primary
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "powerContainer"
    )
    val ink = when {
        phase == ConnectPhase.CONNECTED -> colors.onSuccess
        state.working -> colors.onWarning
        phase == ConnectPhase.BLOCKED -> colors.onError
        else -> colors.onPrimary
    }
    val description = trx(state.actionKey)
    val spokenState = trx(state.stateKey)

    FilledIconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onToggle()
        },
        // A closing tunnel has no verb: the button is disabled rather than a dead tap.
        enabled = state.pressable,
        modifier = modifier
            .size(side.dp)
            .semantics {
                contentDescription = description
                stateDescription = spokenState
            }
            .testTag("dock-power"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = ink,
            disabledContainerColor = container.copy(alpha = 0.65f),
            disabledContentColor = ink.copy(alpha = 0.85f)
        )
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "powerGlyph"
        ) { shown ->
            if (ConnectControl.working(shown)) {
                LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = ink
                )
            } else {
                MarbleGlyphIcon(
                    glyph = when (shown) {
                        ConnectPhase.CONNECTED -> MarbleGlyph.STOP
                        ConnectPhase.BLOCKED -> MarbleGlyph.REFRESH
                        else -> MarbleGlyph.POWER
                    },
                    color = ink,
                    weight = 1.1f,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * MARBLE_STEADY_CONNECT_V264 — Style B: the centre control, rebuilt as a **fixed** object.
 *
 * This is the reported bug. V259/V260 drew a groove whose width was an `animateDpAsState` between
 * a circle and a 130…196dp bloom; a tap set `open = true`, the bloom made room for a state word and
 * a latency, a tooltip appeared above the capsule, and a connected halo grew a `Canvas` larger than
 * the groove. All three are children of a slot inside the dock's weighted `Row` — so a tap, a
 * connect, a disconnect and the tooltip's own 1.4s timeout each re-measured the row and moved the
 * tabs beside the button. The reader's words were exact: "the tabs move after connecting" and "the
 * text beside it opens, and it must not".
 *
 * V264 retires the mechanism, not the object:
 *
 *  1. **One footprint.** The root is `size(centerTriggerWidthDp, centerTriggerSizeDp)` and the dock
 *     reserves that width for the slot. No child can measure the row; the halo is a
 *     `Canvas(matchParentSize())`, drawn inside the same fixed square.
 *  2. **No word.** The state word, the latency and the timed tooltip are gone. The state is the
 *     knob's tone, its glyph and its activity ring; a screen reader still receives it through
 *     `stateDescription`, and `contentDescription` carries the verb ("Cancel connect" while a dial
 *     is in flight, "Reset" after a kill switch).
 *  3. **One press.** `combinedClickable` toggles on a tap and measures the route on a long press.
 *     Both are disabled while the tunnel is closing. No `open` state, no timer, no `LaunchedEffect`,
 *     no width spring and no second measured child.
 *  4. **All five phases** come from [ConnectControl] — connected, connecting, disconnecting,
 *     blocked and idle — with the same tones and the same press rules as the other two controls.
 */
@Composable
internal fun MarbleCenterSpringTrigger(
    state: DockConnectionState,
    size: DockSize,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = marbleMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val phase = state.phase

    val grooveWidth = DockStudio.centerTriggerWidthDp(size).dp
    val grooveHeight = DockStudio.centerTriggerSizeDp(size).dp
    val knobSize = DockStudio.centerKnobSizeDp(size).dp
    val knobDrift by animateDpAsState(
        targetValue = if (phase == ConnectPhase.CONNECTED) 1.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerKnobDrift"
    )
    val live by animateFloatAsState(
        targetValue = if (phase == ConnectPhase.CONNECTED) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerTriggerLive"
    )
    val breath = if (!motion.still && phase == ConnectPhase.CONNECTED) {
        val transition = rememberInfiniteTransition(label = "centerBreath")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "centerBreathPhase"
        )
        value
    } else {
        0f
    }

    val knob by animateColorAsState(
        targetValue = when {
            phase == ConnectPhase.CONNECTED -> colors.success
            state.working -> colors.warning
            phase == ConnectPhase.BLOCKED -> colors.error
            else -> colors.primary
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerTriggerContainer"
    )
    val ink = when {
        phase == ConnectPhase.CONNECTED -> colors.onSuccess
        state.working -> colors.onWarning
        phase == ConnectPhase.BLOCKED -> colors.onError
        else -> colors.onPrimary
    }
    val grooveTone by animateColorAsState(
        targetValue = if (phase == ConnectPhase.CONNECTED) {
            colors.successContainer.copy(alpha = 0.55f)
        } else {
            colors.surfaceContainerHighest.copy(alpha = 0.72f)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerGrooveTone"
    )
    val glyph = DockStudio.centerGlyphDp(size).dp
    val description = trx(state.actionKey)
    val spokenState = trx(state.stateKey)
    val reading = if (state.probing) {
        faDigits(DockStudio.batchLabel(state.probeDone, state.probeTotal))
    } else {
        faDigits(state.latencyLabel)
    }
    val grooveShape = RoundedCornerShape(DockStudio.innerRadiusDp(size))
    val knobShape = RoundedCornerShape(DockStudio.centerTriggerRadiusDp(size))

    Box(
        modifier = modifier
            // The one measured object in this composable. Nothing below can resize the dock row.
            .size(width = grooveWidth, height = grooveHeight)
            .testTag("dock-center"),
        contentAlignment = Alignment.Center
    ) {
        // The halo is a ring *inside* the fixed footprint, drawn before the groove. It used to be a
        // Canvas larger than the groove — a connected tunnel therefore widened the centre slot.
        if (live > 0.01f) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .testTag("dock-center-halo")
            ) {
                drawCircle(
                    color = knob.copy(alpha = 0.14f * live * (1f - breath * 0.45f)),
                    radius = (this.size.minDimension / 2f - 1.dp.toPx()).coerceAtLeast(0f)
                )
            }
        }

        Row(
            modifier = Modifier
                .width(grooveWidth)
                .height(grooveHeight)
                .clip(grooveShape)
                .background(grooveTone)
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant.copy(alpha = 0.45f),
                    shape = grooveShape
                )
                .combinedClickable(
                    enabled = state.pressable,
                    role = Role.Button,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onToggle()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPing()
                    }
                )
                .semantics {
                    contentDescription = description
                    stateDescription = "$spokenState · $reading"
                }
                .testTag("dock-center-trigger")
                .padding(horizontal = DockStudio.CENTER_GROOVE_INSET_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (rtl) Arrangement.End else Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(knobSize)
                    .graphicsLayer {
                        translationX = (if (rtl) -1f else 1f) * knobDrift.toPx()
                    }
                    .shadow(elevation = 3.dp, shape = knobShape)
                    .shadow(
                        elevation = 12.dp,
                        shape = knobShape,
                        ambientColor = knob.copy(alpha = 0.55f),
                        spotColor = knob.copy(alpha = 0.65f)
                    )
                    .clip(knobShape)
                    .background(knob)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.26f), shape = knobShape),
                contentAlignment = Alignment.Center
            ) {
                if (state.working) {
                    LoadingIndicator(
                        modifier = Modifier.size(glyph),
                        color = ink
                    )
                } else {
                    MarbleGlyphIcon(
                        glyph = when (phase) {
                            ConnectPhase.CONNECTED -> MarbleGlyph.STOP
                            ConnectPhase.BLOCKED -> MarbleGlyph.REFRESH
                            else -> MarbleGlyph.POWER
                        },
                        color = ink,
                        weight = 1.15f,
                        modifier = Modifier.size(glyph)
                    )
                }
            }
        }
    }
}
