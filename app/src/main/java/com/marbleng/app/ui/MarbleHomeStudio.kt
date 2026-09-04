@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

// MARBLE_HOME_REDESIGN_V132
//
// The Home surface was rebuilt around one idea: the page is a single, calm instrument for the
// ONE route the user actually selected. Everything here is presentation over the shared
// [HomeEvidence] model — no widget in this file invents, rounds or hides a runtime fact.
//
//   1. [HomeSelectedRouteCard]  the only server on the page: the selected node and the group it
//      was chosen from. Home never renders a list, so "group 1 of 10" is a fact, not a menu.
//   2. [HomePowerStage]         the chosen connection control with the live ping meter beside it.
//   3. [HomeLivePingMeter]      the instrument that opens with an animation while the route comes
//      up. It only ever measures the one server the tunnel is attached to.
//   4. [HomeShortcutDeck]       add / paste / QR / always-visible ping, sitting above the banner.
//   5. [ConnectButtonStream]    the floor bar with a light band travelling right → left.
//   6. [ConnectButtonFloating]  the compact pill docked above the bottom of the page.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.ConnectionPingState
import kotlinx.coroutines.delay
import kotlin.math.max

// ---------------------------------------------------------------------------------------------
// Live ping constants
// ---------------------------------------------------------------------------------------------

/**
 * How long the live meter waits before it re-arms the one-shot connection probe.
 *
 * The repository deliberately owns a ONE-SHOT measurement with no background timer
 * (`MARBLE_PING_ONE_SHOT`); the meter is a visible, user-open instrument, so it is the only
 * place in the product allowed to re-arm it — and only while it is on screen and the route is
 * genuinely up. Close the meter or drop the tunnel and the ladder stops immediately.
 */
internal const val LIVE_PING_INTERVAL_MS = 3_000L

/** Samples kept for the meter's sparkline. */
internal const val LIVE_PING_HISTORY = 24

/** Full-scale latency of the arc gauge, in milliseconds. */
internal const val LIVE_PING_FULL_SCALE_MS = 400

// ---------------------------------------------------------------------------------------------
// 1. The selected route
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_SELECTED_ROUTE_V132 — the one server Home is allowed to show.
 *
 * Home is not a second Servers page. It renders exactly the route the user selected on the
 * Servers tab (the live route wins while a tunnel carries traffic), so "group 1 of 10 groups"
 * is a single fact with a single way to change it: the Servers tab.
 */
@Composable
internal fun HomeSelectedRouteCard(
    evidence: HomeEvidence,
    tone: Color,
    accent: Color,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val shape = RoundedCornerShape(20.dp)
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
            .background(Aether.VoidElevated.copy(alpha = .93f))
            .border(1.dp, borderTone, shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onLibrary)
            .semantics { contentDescription = "${t.selectedRoute}: $node" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The flag in a soft tile: national identity stays readable at a glance without a
        // second colour competing with the state tone.
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

// ---------------------------------------------------------------------------------------------
// 2. Live ping meter
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_LIVE_PING_V132 — the instrument that opens while the route comes up.
 *
 * A compact latency meter in the spirit of the dedicated ping utilities: an arc gauge for the
 * current sample, the number in the middle, a sparkline of the last probes underneath and the
 * name of the ONE server being measured. It never ranks, never compares and never touches
 * another node — it re-arms [HomeActions.onTestPing], which measures the live tunnel and nothing
 * else, so the value on screen is always the latency of the server the user is attached to.
 */
@Composable
internal fun HomeLivePingMeter(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val t = Tr.now
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    val measured = evidence.pingState == ConnectionPingState.MEASURED
    val samples = remember { mutableStateListOf<Int>() }

    // One history point per completed measurement, never per recomposition.
    LaunchedEffect(evidence.pingMs, evidence.pingState) {
        if (evidence.pingState == ConnectionPingState.MEASURED && evidence.pingMs > 0) {
            samples.add(evidence.pingMs)
            while (samples.size > LIVE_PING_HISTORY) samples.removeAt(0)
        }
    }

    // The ladder only runs while the instrument is on screen and the tunnel is genuinely up.
    // It re-arms the repository's one-shot probe instead of owning a background timer.
    LaunchedEffect(active, evidence.connected, evidence.pingState) {
        if (!active || !evidence.connected) return@LaunchedEffect
        if (evidence.pingState == ConnectionPingState.MEASURING) return@LaunchedEffect
        delay(LIVE_PING_INTERVAL_MS)
        if (evidence.connected && evidence.pingState != ConnectionPingState.MEASURING) {
            actions.onTestPing()
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
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "live-ping-fill"
    )
    val pulse = if (measuring) MarbleMotion.current.loop(1_200) else 0f

    val shape = RoundedCornerShape(18.dp)
    val valueLabel = when {
        measured -> "${evidence.pingMs}"
        measuring -> "•••"
        else -> "—"
    }
    val caption = when {
        !evidence.connected -> t.livePingWaiting
        else -> evidence.nodeName.ifBlank { t.livePingHint }
    }
    // Resolved in composable scope: the semantics lambda below is not a composable context.
    val spokenPing = homePingLabel(evidence)

    Column(
        modifier = modifier
            .width(140.dp)
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .94f))
            .border(1.dp, valueTone.copy(alpha = .30f), shape)
            .kineticClickable(
                enabled = homePingTappable(evidence),
                role = Role.Button,
                boundedShape = shape,
                onClick = actions.onTestPing
            )
            .semantics { contentDescription = "${t.livePing}: $spokenPing" }
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
                    .clip(CircleShape)
                    .background(valueTone.copy(alpha = if (measuring) .35f + .65f * pulse else 1f))
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

        // Arc gauge + the number inside it: one glance, no reading required.
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
                    Text(
                        valueLabel,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
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

        // Sparkline: the shape of the last probes, so a spike reads as context, not as a failure.
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

/**
 * MARBLE_HOME_LIVE_PING_V132 — the same instrument as a free-standing slab.
 *
 * The two classic presentations keep their own hero geometry, so they host the meter as its own
 * row directly beneath the connect control instead of inside it. The contract is identical: the
 * meter is visible only while the route is up or coming up, and it measures only the connected
 * server.
 */
@Composable
internal fun HomeLivePingSlab(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val live = evidence.connected || evidence.connecting
    AnimatedVisibility(
        visible = live,
        modifier = modifier,
        enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
            slideInVertically(MarbleMotionSpecs.Spatial) { it / 4 },
        exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
            slideOutVertically(MarbleMotionSpecs.SpatialExit) { it / 4 }
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HomeLivePingMeter(
                evidence = evidence,
                actions = actions,
                tone = tone
            )
        }
    }
}

/**
 * MARBLE_HOME_LIVE_PING_V132 — the connection control with its live ping instrument.
 *
 * The meter sits in a reserved slot so the control never moves when it opens: the slot is always
 * measured, and only its contents animate. On wide layouts (landscape, tablets) the pair renders
 * side by side; on a portrait phone the meter opens above the control.
 */
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
    // The instrument opens the moment the route starts moving and closes the moment it stops.
    val live = evidence.connected || evidence.connecting
    val visible = live && meterEnabled

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // `null` means "decide from the available width". Side by side only when BOTH 140 dp
        // instrument slots fit beside the widest silhouette (the 340 dp slide track) — the two
        // slots are weighted, so the control always keeps its exact centre and can never be
        // pushed off screen. On a portrait phone the instrument stacks above the control.
        val beside = sideBySide ?: (maxWidth >= 640.dp)

        if (beside) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    StageMeter(
                        visible = visible,
                        evidence = evidence,
                        actions = actions,
                        tone = tone
                    )
                }
                control()
                Spacer(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StageMeter(
                    visible = visible,
                    evidence = evidence,
                    actions = actions,
                    tone = tone
                )
                control()
            }
        }
    }
}

@Composable
private fun StageMeter(
    visible: Boolean,
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
            slideInHorizontally(MarbleMotionSpecs.Spatial) { it / 3 } +
            slideInVertically(MarbleMotionSpecs.Spatial) { it / 6 },
        exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
            slideOutHorizontally(MarbleMotionSpecs.SpatialExit) { it / 3 } +
            slideOutVertically(MarbleMotionSpecs.SpatialExit) { it / 6 }
    ) {
        HomeLivePingMeter(
            evidence = evidence,
            actions = actions,
            tone = tone
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Shortcut deck
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_SHORTCUT_DECK_V132 — add / paste / QR / ping, sitting just above the status banner.
 *
 * The four actions a user reaches for while looking at a connection. Ping is not a button that
 * appears when it feels like it: it is always present, always shows the live value when one has
 * been measured, and always says what a tap will do.
 */
@Composable
internal fun HomeShortcutDeck(
    evidence: HomeEvidence,
    actions: HomeActions,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val pingTone = homePingTone(evidence, accent)
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    val pingValue = homePingLabel(evidence)

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

        // Ping is a permanent member of the deck — always visible, always labelled.
        val pingShape = RoundedCornerShape(13.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(pingShape)
                .background(Aether.VoidElevated.copy(alpha = .92f))
                .border(1.dp, pingTone.copy(alpha = .34f), pingShape)
                .kineticClickable(
                    enabled = homePingTappable(evidence),
                    role = Role.Button,
                    boundedShape = pingShape,
                    onClick = actions.onTestPing
                )
                .semantics { contentDescription = "${t.livePing} $pingValue" }
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
            .background(Aether.VoidElevated.copy(alpha = .92f))
            .border(1.dp, tone.copy(alpha = .30f), shape)
            .semantics { contentDescription = label }
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Stream bar — the floor control with a band travelling right → left
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_CONNECT_BUTTON_STYLES_V132 — the full-width floor bar.
 *
 * The band is the whole point of this silhouette: a soft ribbon of the state colour that enters
 * from the RIGHT edge and leaves through the LEFT edge, forever, while the bar is on screen. It
 * is screen-space motion, pinned to LTR, so Persian and English see exactly the same direction —
 * the same discipline the slide knob already follows.
 *
 * The band is bright and quick while the tunnel is opening or closing, slow and quiet while the
 * route is simply protected, and absent when nothing is happening.
 */
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
    val phase = motion.loop(period)
    val shape = RoundedCornerShape(22.dp)
    val label = homeActionLabel(evidence)

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.unit.LocalLayoutDirection provides
            androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = animatedTone.copy(alpha = .18f),
                    spotColor = animatedTone.copy(alpha = .26f)
                )
                .clip(shape)
                .background(Aether.VoidElevated.copy(alpha = .96f))
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
                    onClick = onToggle
                )
                .semantics { contentDescription = "$label connection button" }
        ) {
            // The travelling band sits under the content so the glyph and copy stay crisp.
            Canvas(Modifier.matchParentSize()) {
                // Right → left: the band's centre walks from `width + band` down to `-band`.
                val bandWidth = size.width * .38f
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
            // End-of-track lamp: the floor bar states the line condition without words.
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

// ---------------------------------------------------------------------------------------------
// 6. Floating pill — the docked primary action
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_CONNECT_BUTTON_STYLES_V132 — the compact pill docked above the bottom of the page.
 *
 * v2rayNG made the floating shutter famous; this is the docked reading of it. The pill keeps a
 * fixed slot above the page floor, so it never covers a readout behind it and never has to be
 * hunted for after a drag. Only its colour, its glyph and its halo animate.
 */
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
    val breathe = motion.breathe(3_200)
    val sweep = if (busy) motion.loop(1_150) * 360f else 0f
    val shape = RoundedCornerShape(31.dp)
    val label = homeActionLabel(evidence)

    Row(
        modifier = modifier
            .height(62.dp)
            .width(216.dp)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = animatedTone.copy(alpha = .22f),
                spotColor = animatedTone.copy(alpha = .30f)
            )
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .97f))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        animatedTone.copy(alpha = .26f + .10f * breathe),
                        animatedTone.copy(alpha = .08f)
                    )
                )
            )
            .border(1.5.dp, animatedTone.copy(alpha = .50f), shape)
            .kineticClickable(
                enabled = armed,
                role = Role.Button,
                pressScale = .98f,
                boundedShape = shape,
                onClick = onToggle
            )
            .semantics { contentDescription = "$label connection button" }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                if (busy) {
                    drawArc(
                        color = animatedTone,
                        startAngle = -90f + sweep,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            HomeGlyphIcon(connectButtonGlyph(evidence), animatedTone, Modifier.size(22.dp))
        }
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
    }
}

// ---------------------------------------------------------------------------------------------
// Docked floor chrome shared by the two bottom-docked silhouettes
// ---------------------------------------------------------------------------------------------

/**
 * The capsule behind a bottom-docked control. It gives the bar/pill a deliberate home at the
 * floor of the page instead of leaving it floating between the artwork and the readouts.
 */
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
            .background(Aether.VoidElevated.copy(alpha = .74f))
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
            flavor = HomeFlavor.PRO,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
