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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
    // MARBLE_DOCK_RADII_V260 — the badge is an object *inside* the bar, so it reads the bar's own
    // inner curve rather than a stadium of its own.
    val islandShape = RoundedCornerShape(DockStudio.innerRadiusDp(size))

    Row(
        modifier = modifier
            .height(height.dp)
            .widthIn(max = DockStudio.islandMaxWidthDp(size).dp)
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
        // One row now: the state dot, the reading, and — only while a batch runs — the micro bar at
        // the end, instead of a second line under the words. A two-line badge inside a 44dp capsule
        // was what made the wings look denser than the tabs they sit between.
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
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

        // The micro bar is a *counter*: a batch is already saying how far it has got, so the bar
        // shrinks to the width of a word-tail instead of claiming a full second row.
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
 * MARBLE_CENTER_TRIGGER_V260 — Style B: the centre control, rebuilt as the dock's *switch*.
 *
 * V259 answered "it is a fifth tab" by making the disc bigger than a tab and stacking three shadows
 * under it. A second review of a screenshot said the honest thing: bigger, yes — beautiful, no. A
 * saturated disc standing on three shadow layers inside a pale capsule is loud for a bar that is
 * otherwise quiet, and "connected" was said twice (a green fill *and* a green halo) while "how fast"
 * was only readable *after* a tap.
 *
 * This is the replacement, and its shape is the argument:
 *
 *  1. **The capsule is a groove, not a stadium.** The trigger rests in a pill of the dock's own
 *     inner curve ([DockStudio.innerRadiusDp]) at the surface's tone — the object the reader
 *     presses is the *whole* groove, 8dp wider than the knob it holds, which is what makes it read
 *     as a switch rather than a button dropped into the bar.
 *  2. **The knob is the only coloured thing.** A 44/52/60dp disc slides to the far edge while the
 *     tunnel is up (mirrored in RTL, because a switch follows the reading direction), and it is the
 *     one object in the product that is *proud of* the chrome it lives in: a specular top sheen, a
 *     1dp rim of white light, and one tinted ambient shadow instead of three stacked ones.
 *  3. **The state is a word and the measurement is beside it.** Idle: `Ready` and the latency the
 *     last probe left. Live: `Connected` and the same number in the knob's own ink — so the dock
 *     answers "what is it doing" and "how fast is it" without a tap, and the tap is still the verb.
 *  4. **It breathes only while it is live**, and only by two dp, so a working machine reads as
 *     working rather than as an alarm.
 *
 * The tooltip and the bloom keep their jobs (a tap opens the reading, a second tap on the tooltip
 * pings). What changed is the object: one disc, one groove, one moving part.
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
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (open) {
            delay(DockStudio.CENTER_TOOLTIP_HOLD_MS)
            open = false
        }
    }

    val grooveWidth = DockStudio.centerTriggerSizeDp(size).dp
    val grooveHeight = DockStudio.centerTriggerSizeDp(size).dp
    val knobSize = DockStudio.centerKnobSizeDp(size).dp
    // The bloom is the groove widening under the knob, never the knob growing: the object a finger
    // aimed at must not move while it is being pressed.
    val bloomed = DockStudio.centerBloomedWidthDp(size).dp
    val width by animateDpAsState(
        targetValue = if (open) bloomed else grooveWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "centerTriggerWidth"
    )
    val knobDrift by animateDpAsState(
        targetValue = if (state.connected) 1.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerKnobDrift"
    )
    // The living state: 0 at rest, 1 while the tunnel is up.
    val live by animateFloatAsState(
        targetValue = if (state.connected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerTriggerLive"
    )
    val breath = if (!motion.still && state.connected) {
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
    val grooveTone by animateColorAsState(
        targetValue = if (state.connected) {
            colors.successContainer.copy(alpha = 0.55f)
        } else {
            colors.surfaceContainerHighest.copy(alpha = 0.72f)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "centerGrooveTone"
    )
    val description = if (state.connected) trx("Disconnect") else trx("Connect")
    val pingDescription = trx("Ping the active route")
    val glyph = DockStudio.centerGlyphDp(size).dp
    val stateWord = when {
        state.working -> trx("Working\u2026")
        state.connected -> trx("Connected")
        else -> trx("Ready")
    }
    val reading = if (state.probing) {
        faDigits(DockStudio.batchLabel(state.probeDone, state.probeTotal))
    } else {
        faDigits(state.latencyLabel)
    }
    val grooveShape = RoundedCornerShape(DockStudio.innerRadiusDp(size))
    val knobShape = RoundedCornerShape(DockStudio.centerTriggerRadiusDp(size))

    Box(
        modifier = modifier.testTag("dock-center"),
        contentAlignment = Alignment.Center
    ) {
        // The micro tooltip, 8dp above the capsule's top edge. Fixed offset so it is exact.
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
                            .clip(CircleShape)
                            .background(if (state.connected) colors.success else colors.outline)
                    )
                    Text(
                        text = "$stateWord · $reading",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // The halo: one soft ring behind the groove while the tunnel is up. It is drawn *outside*
        // the groove, so the switch itself never grows a glow line.
        if (live > 0.01f) {
            val halo = grooveHeight + (breath * 6f).dp + (live * 3f).dp
            Canvas(
                modifier = Modifier
                    .size(halo)
                    .testTag("dock-center-halo")
            ) {
                drawCircle(
                    color = knob.copy(alpha = 0.14f * live * (1f - breath * 0.45f)),
                    radius = this.size.minDimension / 2f
                )
            }
        }

        // The groove + the knob, in one pressable object.
        Row(
            modifier = Modifier
                .width(width)
                .height(grooveHeight)
                .clip(grooveShape)
                .background(grooveTone)
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant.copy(alpha = 0.45f),
                    shape = grooveShape
                )
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    open = true
                    onToggle()
                }
                .semantics { contentDescription = description }
                .testTag("dock-center-trigger")
                .padding(horizontal = DockStudio.CENTER_GROOVE_INSET_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (rtl) Arrangement.End else Arrangement.Start
        ) {
            // The knob: the reading lives *inside* it at rest and the state word appears in the
            // groove beside it, which is the switch metaphor made literal.
            Box(
                modifier = Modifier
                    .size(knobSize)
                    .graphicsLayer {
                        // A switch follows the reading direction; a 1dp drift on the live state is
                        // the whole movement, and it settles back the moment the tunnel drops.
                        translationX = (if (rtl) -1f else 1f) * knobDrift.toPx()
                    }
                    // One contact shadow and one tinted ambient bloom — the three-layer stack of
                    // V259 read as a sticker standing on glass.
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
                MarbleGlyphIcon(
                    glyph = if (state.connected) MarbleGlyph.STOP else MarbleGlyph.POWER,
                    color = ink,
                    weight = 1.15f,
                    modifier = Modifier.size(glyph)
                )
            }

            // The word and the measurement. Idle they sit in the groove's quieter ink; live they
            // take the success tone, so the sentence and the knob agree. The reading is *always*
            // drawn — a switch that only says how fast it is after you press it is a switch you
            // have to press twice.
            Column(
                modifier = Modifier.padding(start = 8.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                AnimatedContent(
                    targetState = stateWord,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.9f))
                    },
                    label = "centerStateWord"
                ) { word ->
                    Text(
                        text = word,
                        color = if (state.connected) colors.success else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
                Text(
                    text = reading,
                    color = colors.onSurfaceVariant.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
