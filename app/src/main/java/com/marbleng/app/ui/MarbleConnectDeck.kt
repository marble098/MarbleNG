package com.marbleng.app.ui

// MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — three buttons, three behaviours, three lights.
//
// The council's rule: a second connect button that calls the same function as the first is a
// skin, not a feature. Each tile here owns one repository entry point (see ConnectDeckAction in
// the model) and one way of "turning on": QUICK ignites a breathing core, SMART sweeps an orbit
// while the selector thinks, LIGHT sends two rings outward for the local-only datapath. Every
// animation is pure Compose Canvas; no new dependency, no View, no Lottie asset.

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.model.ConnectDeckAction
import com.marbleng.app.model.ConnectDeckCatalog
import com.marbleng.app.model.ConnectDeckLight
import com.marbleng.app.ui.theme.rememberReducedMotion

private val ConnectDeckShape = RoundedCornerShape(20.dp)

/** The section Home shows between the hero and the active route. */
@Composable
fun ConnectDeckSection(
    litAction: ConnectDeckAction?,
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    onActivate: (ConnectDeckAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val title = trx("Connect your way")
    val footer = if (connected && litAction != null) {
        trx("Tap again to stop")
    } else {
        trx("Three ways to connect")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MarbleSectionHeader(title = title)
        MarbleCard(contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ConnectDeckCatalog.ordered.forEach { action ->
                    ConnectDeckButton(
                        action = action,
                        lit = litAction == action,
                        busy = disconnecting,
                        onClick = { onActivate(action) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = footer,
                color = if (litAction != null && connected) colors.primary else colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConnectDeckButton(
    action: ConnectDeckAction,
    lit: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val tone = when (action) {
        ConnectDeckAction.QUICK -> colors.primary
        ConnectDeckAction.SMART -> colors.secondary
        ConnectDeckAction.LIGHT -> colors.tertiary
    }
    val glyph = when (action) {
        ConnectDeckAction.QUICK -> MarbleGlyph.POWER
        ConnectDeckAction.SMART -> MarbleGlyph.SPARK
        ConnectDeckAction.LIGHT -> MarbleGlyph.ROUTE
    }
    val title = trx(action.titleKey)
    val hint = if (lit) trx("Lit") else trx(action.hintKey)
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (lit) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "connectDeckScale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .alpha(if (busy) 0.62f else 1f)
            .clip(ConnectDeckShape)
            .background(if (lit) tone.copy(alpha = 0.10f) else colors.surfaceContainer)
            .border(
                width = if (lit) 1.dp else 0.85.dp,
                color = if (lit) tone.copy(alpha = 0.45f) else colors.outlineVariant,
                shape = ConnectDeckShape
            )
            .clickable(enabled = !busy, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            ConnectDeckLightCanvas(
                light = action.light,
                lit = lit,
                tone = tone,
                modifier = Modifier.size(46.dp)
            )
            MarbleGlyphIcon(
                glyph = glyph,
                color = if (lit) Color.White else tone,
                modifier = Modifier.size(20.dp),
                weight = 1.15f
            )
        }
        Text(
            text = title,
            color = if (lit) tone else colors.onSurface,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (lit) FontWeight.Bold else FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = hint,
            color = if (lit) tone else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(30.dp)
        )
    }
}

/**
 * One Canvas, three physically different "turning on" behaviours. All colours are captured in the
 * composable scope before the draw lambda: a DrawScope is not a composable scope.
 */
@Composable
private fun ConnectDeckLightCanvas(
    light: ConnectDeckLight,
    lit: Boolean,
    tone: Color,
    modifier: Modifier = Modifier
) {
    if (!lit) {
        Canvas(modifier = modifier) {
            val radius = size.minDimension / 2f
            drawCircle(color = tone.copy(alpha = 0.13f), radius = radius * 0.78f)
            drawCircle(color = tone.copy(alpha = 0.10f), radius = radius * 0.55f)
        }
        return
    }

    // Reduced motion: the lit lamp renders one frozen frame instead of looping.
    val frozenFrame = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "connectDeckLight")
    val sweepAnimated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connectDeckSweep"
    )
    val breatheAnimated by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connectDeckBreathe"
    )
    val sweep = if (frozenFrame) 0.3f else sweepAnimated
    val breathe = if (frozenFrame) 1f else breatheAnimated
    val strokeWidth = 2.5.dp

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        when (light) {
            ConnectDeckLight.IGNITION -> {
                // A core that lights up: the halo breathes while the centre stays bright.
                drawCircle(
                    color = tone.copy(alpha = 0.12f + 0.10f * breathe),
                    radius = radius * (0.72f + 0.16f * breathe),
                    center = centre
                )
                drawCircle(color = tone.copy(alpha = 0.55f), radius = radius * 0.62f, center = centre)
                drawCircle(color = tone, radius = radius * 0.46f, center = centre)
            }
            ConnectDeckLight.ORBIT -> {
                // A selector's arc sweeping around a quiet disc.
                drawCircle(
                    color = tone.copy(alpha = 0.18f),
                    radius = radius * 0.80f,
                    center = centre,
                    style = Stroke(width = strokeWidth.toPx())
                )
                val arcRadius = radius * 0.80f
                drawArc(
                    color = tone,
                    startAngle = -90f + sweep * 360f,
                    sweepAngle = 115f,
                    useCenter = false,
                    topLeft = Offset(centre.x - arcRadius, centre.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = (3.dp).toPx(), cap = StrokeCap.Round)
                )
                drawCircle(color = tone, radius = radius * 0.46f, center = centre)
            }
            ConnectDeckLight.RIPPLE -> {
                // Two rings travelling outward: a light, local-only connection.
                drawCircle(color = tone.copy(alpha = 0.20f), radius = radius * 0.46f, center = centre)
                val first = sweep
                val second = (sweep + 0.5f) % 1f
                for (phase in listOf(first, second)) {
                    drawCircle(
                        color = tone.copy(alpha = 0.48f * (1f - phase)),
                        radius = radius * (0.40f + 0.55f * phase),
                        center = centre,
                        style = Stroke(width = strokeWidth.toPx())
                    )
                }
                drawCircle(color = tone, radius = radius * 0.42f, center = centre)
            }
        }
    }
}
