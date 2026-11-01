package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay

/**
 * MARBLE_LUMEN_DOCK_V256 — everything the dock needs to know about the tunnel, in one value.
 *
 * Seven parameters would be seven chances for the island, the power button and the centre trigger
 * to disagree about what "connected" means, so the shell resolves the state once and hands it over.
 */
internal data class DockConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val disconnecting: Boolean = false,
    val latencyMs: Int = 0,
    val pingState: ConnectionPingState = ConnectionPingState.IDLE,
    val probing: Boolean = false,
    val probeDone: Int = 0,
    val probeTotal: Int = 0
) {
    val working: Boolean get() = connecting || disconnecting

    /** "48 ms", or the ping's own name while it is being measured. */
    val latencyLabel: String
        get() = when {
            pingState == ConnectionPingState.MEASURING -> "\u2026"
            latencyMs > 0 -> DockStudio.pingLabel(latencyMs)
            else -> "\u2014"
        }
}

/**
 * MARBLE_LUMEN_DOCK_V256 — Style A's left wing: the Dynamic Island capsule.
 *
 * It is a *badge*, not a banner. One tap pings the active route and the reading lands inline; while
 * a multi-server sweep runs it becomes the silent batch tracker (`12/79 pinging…` with a micro bar
 * under the words) instead of the giant top banner the old build posted. A batch of seventy-nine
 * servers is not an event that deserves the whole width of the screen; it is a counter in the corner
 * of the control that started it.
 *
 * The glow is the Expressive accent, not a shadow: a connected capsule breathes between two tonal
 * steps, and a still (reduced-motion) app simply holds the brighter one.
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
        state.connected -> colors.successContainer
        state.working -> colors.warningContainer
        else -> colors.surfaceContainerHighest
    }
    val ink = when {
        state.connected -> colors.onSuccessContainer
        state.working -> colors.onWarningContainer
        else -> colors.onSurfaceVariant
    }

    // The pulse: 0.86 … 1.0 of the accent, only while the tunnel is actually up. Two tonal steps
    // is what makes it read as breathing rather than blinking, and calm motion holds the frame.
    val pulse = if (!motion.still && state.connected) {
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

    Column(
        modifier = modifier
            .height(height.dp)
            .widthIn(max = DockStudio.islandMaxWidthDp(size).dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = spoken }
            .testTag("dock-island")
            .padding(horizontal = horizontal.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        when {
                            state.connected -> colors.success
                            state.working -> colors.warning
                            else -> colors.outline
                        }
                    )
            )
            AnimatedContent(
                targetState = if (state.probing) {
                    DockStudio.batchLabel(state.probeDone, state.probeTotal)
                } else {
                    state.latencyLabel
                },
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
        }

        // The micro bar: two dp under the words, and only while a batch is running.
        AnimatedVisibility(
            visible = state.probing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(ink.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            DockStudio.batchProgress(state.probeDone, state.probeTotal)
                                .coerceIn(0.02f, 1f)
                        )
                        .height(2.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(ink)
                        .testTag("dock-island-progress")
                )
            }
        }
    }
}

/**
 * MARBLE_LUMEN_DOCK_V256 — Style A's right wing: the power button.
 *
 * A tactile circle inside the dock's right edge, as tall as the pill's inner row so it is the same
 * object at all three rungs. The glyph *morphs* — `POWER` at rest, `STOP` while the tunnel is up —
 * through an `AnimatedContent` that scales the outgoing glyph out and the incoming one in, and the
 * container tint travels with the state instead of switching.
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

    val container by animateColorAsState(
        targetValue = when {
            state.connected -> colors.success
            state.working -> colors.warning
            else -> colors.primary
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "powerContainer"
    )
    val ink = when {
        state.connected -> colors.onSuccess
        state.working -> colors.onWarning
        else -> colors.onPrimary
    }
    val description = if (state.connected) trx("Disconnect") else trx("Connect")

    FilledIconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        },
        modifier = modifier
            .size(side.dp)
            .semantics { contentDescription = description }
            .testTag("dock-power"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = ink
        )
    ) {
        AnimatedContent(
            targetState = state.connected,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "powerGlyph"
        ) { live ->
            MarbleGlyphIcon(
                glyph = if (live) MarbleGlyph.STOP else MarbleGlyph.POWER,
                color = ink,
                weight = 1.1f,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * MARBLE_LUMEN_DOCK_V256 — Style B: the morphing central spring button.
 *
 * A minimal 48dp circle that blooms into an expressive pill on press (`animateDpAsState` on the
 * width, clipped to a stadium, with the arrow sliding out of the glyph and a ripple travelling the
 * pill behind it). The latency and the state ride a micro tooltip **8dp above the button** and leave
 * as soon as the interaction is over — [DockStudio.CENTER_TOOLTIP_HOLD_MS] after the tap — because a
 * floating card that stays is a second nav bar.
 *
 * The tooltip draws above the pill, which is why the dock's surface and its content are two layers:
 * a clipped row would have cut the card in half.
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
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            delay(DockStudio.CENTER_TOOLTIP_HOLD_MS)
            open = false
        }
    }

    val resting = DockStudio.powerButtonSizeDp(size).dp
    val bloomed = (DockStudio.powerButtonSizeDp(size) + 44).dp
    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (open) bloomed else resting,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerTriggerWidth"
    )
    val ripple by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerTriggerRipple"
    )

    val container by animateColorAsState(
        targetValue = when {
            state.connected -> colors.success
            state.working -> colors.warning
            else -> colors.primary
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerTriggerContainer"
    )
    val ink = when {
        state.connected -> colors.onSuccess
        state.working -> colors.onWarning
        else -> colors.onPrimary
    }
    val description = if (state.connected) trx("Disconnect") else trx("Connect")
    val pingDescription = trx("Ping the active route")

    Box(
        modifier = modifier.testTag("dock-center"),
        contentAlignment = Alignment.Center
    ) {
        // The micro tooltip, 8dp above the pill's top edge. Fixed height so the offset is exact.
        AnimatedVisibility(
            visible = open && !motion.still,
            enter = fadeIn() + scaleIn(initialScale = 0.86f),
            exit = fadeOut() + scaleOut(targetScale = 0.86f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { translationY = -46.dp.toPx() }
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPing()
                    }
                    .semantics { contentDescription = pingDescription }
                    .testTag("dock-center-tooltip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (state.connected) colors.success else colors.outline)
                    )
                    Text(
                        text = "${trx(if (state.connected) "Connected" else "Ready")} · " +
                            faDigits(
                                if (state.probing) {
                                    DockStudio.batchLabel(state.probeDone, state.probeTotal)
                                } else {
                                    state.latencyLabel
                                }
                            ),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(width)
                .height(DockStudio.powerButtonSizeDp(size).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(container)
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    open = true
                    onToggle()
                }
                .semantics { contentDescription = description }
                .testTag("dock-center-trigger"),
            contentAlignment = Alignment.Center
        ) {
            // The ripple: a wash that travels the pill's width while it is open.
            Box(
                modifier = Modifier
                    .fillMaxWidth(ripple.coerceIn(0f, 1f))
                    .height(DockStudio.powerButtonSizeDp(size).dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(ink.copy(alpha = 0.10f))
            )
            AnimatedContent(
                targetState = open,
                transitionSpec = {
                    (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.7f) + fadeOut())
                },
                label = "centerTriggerGlyph"
            ) { bloom ->
                MarbleGlyphIcon(
                    glyph = when {
                        bloom && state.connected -> MarbleGlyph.STOP
                        bloom -> MarbleGlyph.POWER
                        state.connected -> MarbleGlyph.STOP
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
