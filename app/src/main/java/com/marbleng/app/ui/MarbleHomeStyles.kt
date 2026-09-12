@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

// iOS-STYLED 4 HOME THEMES
//
// 1. IOS_SLIDER: Bottom Slide-to-connect slider, sub & server list box in center with centered sub name, wide status card at top.
// 2. IOS_FLOATING: Floating button on right that splits into disconnect (pause) and ping when connected, wide status card at top, expanded servers box.
// 3. IOS_EMBOSSED: Bold embossed center circular button, wide status card at top, sub & servers box at bottom.
// 4. IOS_MODULAR: Modular customizable layout where user can reorder boxes, toggle modules, and customize widgets.
//
// All 4 themes feature iOS-style glass boxes, fixed screen height (no outer page scroll),
// and inner scrollable components where needed.

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.ServerIntelInfo
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.ConnectButtonStyle
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ModularCardSize
import com.marbleng.app.model.ModularLayout
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.parseConnectButtonStyle
import com.marbleng.app.model.ProxyProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Shared evidence model
// ---------------------------------------------------------------------------------------------

/**
 * Everything the Home styles are allowed to show, resolved exactly once per composition.
 */
internal data class HomeEvidence(
    val profile: ProxyProfile?,
    val nodeName: String,
    val sourceName: String,
    val ip: String,
    val flag: String,
    val countryCode: String,
    val location: String,
    val ipLoading: Boolean,
    val ipError: Boolean,
    val connected: Boolean,
    val connecting: Boolean,
    val disconnecting: Boolean,
    val blocked: Boolean,
    val connectedSinceMs: Long,
    val pingMs: Int,
    val pingState: ConnectionPingState,
    val pingFailure: String,
    val selectedPingMs: Int,
    val selectedPingState: ConnectionPingState,
    val selectedPingFailure: String,
    val downBps: Long,
    val upBps: Long,
    // MARBLE_IRAN_AWARE_PING_UI — Layer 0/2 signals: the capsule's ⚠️ and the sparkline's
    // injection segments consume them.
    val injectedResetSuspected: Boolean = false,
    val stabilityClass: String = ""
)

internal fun buildHomeEvidence(
    repo: AppRepository,
    profile: ProxyProfile?,
    displayName: String,
    info: ServerIntelInfo?,
    fallbackFlag: String?
): HomeEvidence {
    val connected = repo.state == "CONNECTED"
    return HomeEvidence(
        profile = profile,
        nodeName = displayName,
        sourceName = profile?.subscriptionName?.trim().orEmpty(),
        ip = when {
            info != null && info.ip.isNotBlank() -> info.ip
            else -> profile?.host?.trim()?.removeSurrounding("[", "]").orEmpty()
        },
        flag = info?.flag?.takeIf { it.isNotBlank() } ?: fallbackFlag.orEmpty(),
        countryCode = info?.countryCode.orEmpty(),
        location = info?.locationLabel.orEmpty(),
        ipLoading = repo.serverIntelLoading,
        ipError = repo.serverIntelError.isNotBlank() && info == null,
        connected = connected,
        connecting = repo.state == "CONNECTING",
        disconnecting = repo.state == "DISCONNECTING",
        blocked = repo.state == "BLOCKED",
        connectedSinceMs = repo.connectedSinceMs,
        pingMs = repo.connectionPingMs,
        pingState = repo.connectionPingState,
        pingFailure = repo.connectionPingFailure,
        selectedPingMs = repo.selectedPingMs,
        selectedPingState = repo.selectedPingState,
        selectedPingFailure = repo.selectedPingFailure,
        downBps = if (connected) repo.liveDownBps else 0L,
        upBps = if (connected) repo.liveUpBps else 0L,
        injectedResetSuspected = repo.homePingInjectedReset,
        stabilityClass = repo.homePingStabilityClass
    )
}

/** Actions the evidence block can trigger. Identical in every style. */
internal data class HomeActions(
    val onToggleConnection: () -> Unit,
    val onCopyIp: () -> Unit,
    val onRefreshIp: () -> Unit,
    val onIpDetails: () -> Unit,
    val onTestPing: () -> Unit,
    val onLibrary: () -> Unit,
    val onConnectProfile: (ProxyProfile) -> Unit = {},
    val onAddRoute: () -> Unit = {},
    val onRank: () -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onRouting: () -> Unit = {},
    val onTests: () -> Unit = {},
    val onPasteImport: () -> Unit = {},
    val onQrImport: () -> Unit = {},
    /**
     * MARBLE_HOME_PING_ROUTE_GROUP_V146 — measure every server of the subscription that the
     * route currently shown on Home belongs to. Distinct from [onTestPing], which measures the
     * one route the connect button acts on.
     */
    val onPingGroup: () -> Unit = {}
)

/** The per-style skin every shared evidence widget renders through. */
internal enum class HomeFlavor { IOS_SLIDER, IOS_FLOATING, IOS_EMBOSSED, IOS_MODULAR }

/** The single source of truth for which presentation skin a [HomeStyle] renders through. */
internal fun homeFlavorFor(style: HomeStyle): HomeFlavor = when (style) {
    HomeStyle.IOS_SLIDER -> HomeFlavor.IOS_SLIDER
    HomeStyle.IOS_FLOATING -> HomeFlavor.IOS_FLOATING
    HomeStyle.IOS_EMBOSSED -> HomeFlavor.IOS_EMBOSSED
    HomeStyle.IOS_MODULAR -> HomeFlavor.IOS_MODULAR
}

@Composable
internal fun homeTone(evidence: HomeEvidence): Color = when {
    evidence.connected && evidence.pingState == ConnectionPingState.MEASURED && evidence.pingMs > 0 ->
        marbleMetricTone(pingMetricBand(evidence.pingMs))
    evidence.connected -> Aether.Emerald
    evidence.connecting -> Aether.CyanBright
    evidence.disconnecting -> Aether.Amber
    evidence.blocked -> Aether.Danger
    else -> Aether.Cyan
}

@Composable
internal fun styleConnectedTone(flavor: HomeFlavor): Color = when (flavor) {
    HomeFlavor.IOS_SLIDER -> Aether.Emerald
    HomeFlavor.IOS_FLOATING -> Aether.CyanBright
    HomeFlavor.IOS_EMBOSSED -> Aether.Emerald
    HomeFlavor.IOS_MODULAR -> Aether.AmethystBright
}

@Composable
internal fun styleStateTone(flavor: HomeFlavor, evidence: HomeEvidence): Color = when {
    evidence.connected -> styleConnectedTone(flavor)
    evidence.connecting -> Aether.CyanBright
    evidence.disconnecting -> Aether.Amber
    evidence.blocked -> Aether.Danger
    else -> Aether.Cyan
}

@Composable
internal fun homeStatusText(evidence: HomeEvidence): String {
    val t = Tr.now
    return when {
        evidence.connected -> t.statusProtected
        evidence.connecting -> t.securingRoute
        evidence.disconnecting -> t.closingRoute
        evidence.blocked -> t.connectionStopped
        else -> t.socksStandby
    }
}

@Composable
internal fun homeActionLabel(evidence: HomeEvidence): String {
    val t = Tr.now
    return when {
        evidence.connected -> t.disconnect
        evidence.connecting -> t.cancel
        evidence.disconnecting -> t.disconnecting
        evidence.blocked -> t.reset
        else -> t.connect
    }
}

@Composable
internal fun rememberUptimeLabel(connectedSinceMs: Long): String {
    var nowMs by remember(connectedSinceMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSinceMs) {
        if (connectedSinceMs <= 0L) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }
    if (connectedSinceMs <= 0L) return "00:00"
    val totalSeconds = ((nowMs - connectedSinceMs) / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

internal fun homeV137PingChannel(evidence: HomeEvidence): Triple<Int, ConnectionPingState, String> =
    if (evidence.connected) {
        Triple(evidence.pingMs, evidence.pingState, evidence.pingFailure)
    } else {
        Triple(evidence.selectedPingMs, evidence.selectedPingState, evidence.selectedPingFailure)
    }

/**
 * MARBLE_IRAN_AWARE_PING_UI — the latency capsule carries the verdict glyph as well as the
 * number: ✅ verified, ⚠️ injection or instability observed, 🚫 failed. The glyph is the
 * one-glance cross-validation summary; the number alone is not a status.
 */
@Composable
internal fun homePingLabel(evidence: HomeEvidence): String {
    val t = Tr.now
    val (ms, state, _) = homeV137PingChannel(evidence)
    val glyph = when {
        state == ConnectionPingState.FAILED -> "🚫"
        evidence.injectedResetSuspected ||
            evidence.stabilityClass == "UNSTABLE_UNDER_OBSERVATION" -> "⚠️"
        state == ConnectionPingState.MEASURED -> "✅"
        else -> ""
    }
    return when (state) {
        ConnectionPingState.MEASURING -> t.pingMeasuringValue
        ConnectionPingState.MEASURED -> if (ms >= 1) "$glyph $ms ms" else "$glyph ✕"
        ConnectionPingState.FAILED -> "🚫 ${t.pingFailedShort}"
        ConnectionPingState.IDLE -> t.pingIdleValue
    }
}

@Composable
internal fun homePingTone(evidence: HomeEvidence, fallback: Color): Color {
    val (ms, state, _) = homeV137PingChannel(evidence)
    return when (state) {
        ConnectionPingState.MEASURED -> if (ms >= 1) marbleMetricTone(pingMetricBand(ms)) else Aether.Danger
        ConnectionPingState.FAILED -> Aether.Danger
        ConnectionPingState.MEASURING -> Aether.Cyan
        ConnectionPingState.IDLE -> fallback
    }
}

/** The three words a failed Home ping may show, resolved against the active language. */
@Composable
internal fun pingFailureLabel(failure: String): String {
    val t = Tr.now
    return when (failure.trim().lowercase()) {
        "timeout" -> t.pingTimeout
        "unreachable" -> t.pingUnreachable
        else -> t.pingFailedShort
    }
}

@Composable
internal fun homePingActionHint(evidence: HomeEvidence): String {
    val t = Tr.now
    val (_, state, failure) = homeV137PingChannel(evidence)
    return when (state) {
        ConnectionPingState.MEASURING -> t.measuring
        ConnectionPingState.MEASURED -> t.retestPing
        ConnectionPingState.FAILED -> if (failure.isNotBlank()) pingFailureLabel(failure) else t.pingFailed
        ConnectionPingState.IDLE -> t.testPing
    }
}

/**
 * MARBLE_CONNECT_BUTTON_V121 — the Home tree provides the user's chosen silhouette here so every
 * [HomePowerControl] call site resolves it identically.
 */
internal val LocalConnectButtonStyle = staticCompositionLocalOf { ConnectButtonStyle.ROUND }

internal fun connectButtonGlyph(evidence: HomeEvidence): HomeGlyph = when {
    evidence.connected -> HomeGlyph.CHECK
    evidence.blocked -> HomeGlyph.RESET
    else -> HomeGlyph.POWER
}

@Composable
internal fun ConnectButtonCaption(
    evidence: HomeEvidence,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Text(
        homeActionLabel(evidence),
        color = tone,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * The connection control.
 *
 * Deliberately carries NO quality ring: link quality lives in its own readouts, and wrapping the
 * primary action in a score made the button's own state ambiguous. Every Home presentation shares
 * the exact same control, in the silhouette the user picked in Settings.
 *
 * MARBLE_CONNECT_BUTTON_V121 — the silhouette is resolved in order: an explicit per-call style,
 * then the user's Settings choice, then the product default. The iOS themes keep their own
 * [IosSlideToConnect] for the dedicated slider theme; every shared call site renders through this
 * entry point so the Home evidence and action contract stays identical everywhere.
 */
@Composable
internal fun HomePowerControl(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    flavor: HomeFlavor = HomeFlavor.IOS_SLIDER,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    haloBrush: Brush? = null,
    style: ConnectButtonStyle? = null
) {
    MarbleConnectionButton(
        evidence = evidence,
        tone = tone,
        onToggle = onToggle,
        flavor = flavor,
        modifier = modifier,
        diameter = diameter,
        haloBrush = haloBrush,
        style = style ?: LocalConnectButtonStyle.current
    )
}

// ---------------------------------------------------------------------------------------------
// MARBLE_CONNECT_BUTTON_V121 — the one connection button of the product, in every silhouette
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_CONNECT_BUTTON_V121 — the semantic colour of the primary action.
 *
 * The connect control is the one element of the product whose meaning must be readable in a
 * quarter of a second, so its colour is a pure function of the runtime state and nothing else:
 *
 *  | state          | tone                               |
 *  |----------------|------------------------------------|
 *  | disconnected   | ice blue  — armed and ready        |
 *  | connecting     | amethyst  — work in progress       |
 *  | connected      | emerald   — protected              |
 *  | disconnecting  | amber     — winding the tunnel down|
 *  | fail-closed    | danger    — blocked, needs a reset |
 *
 * Every transition between them is animated ([MarbleMotionSpecs.Color]); the control itself never
 * moves, floats, drifts or resizes.
 */
@Composable
internal fun connectButtonTone(evidence: HomeEvidence): Color = when {
    evidence.blocked -> Aether.Danger
    evidence.disconnecting -> Aether.Amber
    evidence.connecting -> Aether.Amethyst
    evidence.connected -> Aether.Emerald
    else -> Aether.Cyan
}

/**
 * MARBLE_CONNECT_BUTTON_V121 — the one connection button of the product, in five silhouettes.
 *
 *  - [ConnectButtonStyle.ROUND]    the large round shutter (default), centred in the hero;
 *  - [ConnectButtonStyle.SLIDE]    a slide-to-connect track dragged from left to right;
 *  - [ConnectButtonStyle.CLASSIC]  the classic rectangular power switch, docked under the hero;
 *  - [ConnectButtonStyle.STREAM]   the full-width floor bar with a travelling light band;
 *  - [ConnectButtonStyle.FLOATING] the compact pill docked at the bottom-end corner.
 *
 * Rules shared by all five: the control never changes position or size, never floats and never
 * breathes. Only its colour, its copy and — while a route is actually being secured — a single
 * progress indicator animate, so the button is calm at rest and unmistakable while it works.
 *
 * The two docked silhouettes live in `MarbleHomeStudio.kt` and are dispatched here so every Home
 * presentation reaches them through the same evidence and action contract.
 */
@Composable
internal fun MarbleConnectionButton(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    @Suppress("UNUSED_PARAMETER") flavor: HomeFlavor,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    haloBrush: Brush? = null,
    style: ConnectButtonStyle = ConnectButtonStyle.ROUND
) {
    val stateTone = connectButtonTone(evidence)
    val animatedTone by animateColorAsState(
        targetValue = stateTone,
        animationSpec = MarbleMotionSpecs.Color,
        label = "marble-connection-tone"
    )
    // The one action the control is busy with cannot be re-triggered by an impatient tap.
    val armed = !evidence.disconnecting

    when (style) {
        ConnectButtonStyle.ROUND -> ConnectButtonRound(
            evidence = evidence,
            accent = tone,
            animatedTone = animatedTone,
            armed = armed,
            onToggle = onToggle,
            modifier = modifier,
            diameter = diameter,
            haloBrush = haloBrush
        )

        ConnectButtonStyle.SLIDE -> ConnectButtonSlide(
            evidence = evidence,
            animatedTone = animatedTone,
            armed = armed,
            onToggle = onToggle,
            modifier = modifier,
            width = (diameter * 2.1f).coerceIn(240.dp, 340.dp)
        )

        ConnectButtonStyle.CLASSIC -> ConnectButtonClassic(
            evidence = evidence,
            animatedTone = animatedTone,
            armed = armed,
            onToggle = onToggle,
            modifier = modifier,
            width = (diameter * 1.55f).coerceIn(200.dp, 280.dp)
        )

        // MARBLE_CONNECT_BUTTON_STYLES_V132 — the two bottom-docked silhouettes. Both are
        // full-bleed by nature, so they take the caller's modifier and never a derived width.
        ConnectButtonStyle.STREAM -> ConnectButtonStream(
            evidence = evidence,
            animatedTone = animatedTone,
            armed = armed,
            onToggle = onToggle,
            modifier = modifier
        )

        ConnectButtonStyle.FLOATING -> ConnectButtonFloating(
            evidence = evidence,
            animatedTone = animatedTone,
            armed = armed,
            onToggle = onToggle,
            modifier = modifier
        )
    }
}

/**
 * Style 1 — the round shutter. Big, centred, fixed. A hairline rim states the state colour, and a
 * single indeterminate arc is drawn only while the route is actually being secured or closed.
 *
 * MARBLE_HOME_V137 — Style A (Classic). The tap answers the finger: the face compresses under
 * pressure and springs back, an acknowledgement ring expands outward once, the securing arc
 * rotates with a breathing pulse while busy, and the connected ring glows with a slow halo
 * instead of sitting static. Disconnected stays calm — a resting instrument, not a screensaver.
 */
@Composable
private fun ConnectButtonRound(
    evidence: HomeEvidence,
    accent: Color,
    animatedTone: Color,
    armed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    diameter: Dp,
    haloBrush: Brush?
) {
    val motion = MarbleMotion.current
    val busy = evidence.connecting || evidence.disconnecting
    val sweep = if (busy) motion.loop(1_150) * 360f else 0f
    // The securing arc breathes while it rotates: width and alpha pulse on a shared clock.
    val busyPulse = if (busy) motion.breathe(1_150) else 0f
    // The connected halo breathes slowly; disconnected holds still.
    val haloPulse = if (evidence.connected) motion.breathe(2_800) else 0f
    val label = homeActionLabel(evidence)
    // One-shot acknowledgement ring: 0 = rest, 1 = fully expanded and faded.
    val tapRipple = remember { Animatable(0f) }
    val rippleScope = rememberCoroutineScope()
    // The icon eases between its connected/disconnected sizes instead of jumping.
    val iconFraction by animateFloatAsState(
        targetValue = if (evidence.connected) .22f else .26f,
        animationSpec = MarbleMotionSpecs.ResponseFloat,
        label = "round-icon-size"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .24f),
                    spotColor = animatedTone.copy(alpha = .34f)
                )
                .clip(CircleShape)
                .background(
                    haloBrush ?: Brush.radialGradient(
                        listOf(
                            animatedTone.copy(alpha = .22f),
                            animatedTone.copy(alpha = .07f)
                        )
                    )
                )
                .background(
                    Brush.radialGradient(
                        listOf(Color.Transparent, accent.copy(alpha = .05f))
                    )
                )
                .kineticClickable(
                    enabled = armed,
                    role = Role.Button,
                    pressScale = .94f,
                    boundedShape = CircleShape,
                    onClick = {
                        // The acknowledgement ring expands outward once per tap, on the shared
                        // response spring, while the press scale (owned by kineticClickable)
                        // compresses and releases the face.
                        rippleScope.launch {
                            tapRipple.snapTo(0f)
                            tapRipple.animateTo(1f, MarbleMotionSpecs.ResponseFloat)
                        }
                        onToggle()
                    }
                )
                .semantics { contentDescription = "$label connection button" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize().padding(10.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // Outer rim — the calm resting statement of the current state.
                drawCircle(
                    color = animatedTone.copy(alpha = .22f),
                    radius = r * .94f,
                    center = c,
                    style = Stroke(width = 1.6.dp.toPx())
                )
                // Inner face.
                drawCircle(
                    color = animatedTone.copy(alpha = .10f),
                    radius = r * .70f,
                    center = c
                )
                when {
                    busy -> {
                        // Rotating securing arc with a breathing pulse: the width swells and the
                        // alpha lifts on the shared clock, so progress reads as alive, not stuck.
                        drawArc(
                            color = animatedTone.copy(alpha = .85f + .15f * busyPulse),
                            startAngle = -90f + sweep,
                            sweepAngle = 104f,
                            useCenter = false,
                            topLeft = Offset(c.x - r * .80f, c.y - r * .80f),
                            size = Size(r * 1.60f, r * 1.60f),
                            style = Stroke(
                                width = (4.2f + 1.6f * busyPulse).dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                        // Faint full track so the arc travels a visible orbit.
                        drawCircle(
                            color = animatedTone.copy(alpha = .18f),
                            radius = r * .80f,
                            center = c,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    evidence.connected -> {
                        // Breathing halo: the ring swells outward a touch and glows, on a slow
                        // loop that never distracts.
                        drawCircle(
                            color = animatedTone.copy(alpha = .16f + .10f * haloPulse),
                            radius = r * (.84f + .03f * haloPulse),
                            center = c,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = animatedTone.copy(alpha = .74f + .14f * haloPulse),
                            radius = r * (.80f + .012f * haloPulse),
                            center = c,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }

                    else -> drawCircle(
                        color = animatedTone.copy(alpha = .45f),
                        radius = r * .80f,
                        center = c,
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                }
                // Tap acknowledgement: one ring expanding outward and fading, driven by the
                // one-shot ripple progress. Invisible at rest (progress 0 or 1).
                val ripple = tapRipple.value
                if (ripple in 0.001f..0.999f) {
                    drawCircle(
                        color = animatedTone.copy(alpha = .55f * (1f - ripple)),
                        radius = r * (.70f + .30f * ripple),
                        center = c,
                        style = Stroke(width = (3f * (1f - ripple) + 1f).dp.toPx())
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                HomeGlyphIcon(
                    connectButtonGlyph(evidence),
                    animatedTone,
                    Modifier.size(diameter * iconFraction)
                )
                if (evidence.connected) {
                    Spacer(Modifier.height(4.dp))
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
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(animatedTone.copy(alpha = .14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(pingTone)
                        )
                        Text(
                            pingLabel,
                            color = pingTone,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ConnectButtonCaption(evidence, animatedTone)
    }
}


/**
 * Style 2 — slide to connect.
 *
 * A safety switch: the user drags the knob from left to right across the track to arm or close
 * the tunnel. The knob is the only thing that ever moves, it follows the finger exactly, and it
 * springs back when the gesture is released before the end of the track, so a pocket tap can
 * never toggle the connection. The gesture is pinned to LTR because it is a physical, screen-space
 * control: it reads left → right in Persian exactly as it does in English.
 *
 * MARBLE_HOME_V137 — Style B (Lumen swipe). Crossing the generous threshold answers with a
 * haptic tick and lights the end chevrons; releasing past it flies the knob home through the
 * end first (a visible completion beat) instead of vanishing mid-track; releasing short of it
 * springs back with the response spring. The track fill deepens with progress so the drag reads
 * as charging the action.
 */
@Composable
private fun ConnectButtonSlide(
    evidence: HomeEvidence,
    animatedTone: Color,
    armed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    width: Dp
) {
    val motion = MarbleMotion.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val trackHeight = 66.dp
    val knobSize = 54.dp
    val padding = 6.dp
    val travelDp = width - knobSize - padding * 2
    val travelPx = with(density) { travelDp.toPx() }.coerceAtLeast(1f)
    val shape = RoundedCornerShape(trackHeight / 2)
    val busy = evidence.connecting || evidence.disconnecting
    val label = homeActionLabel(evidence)
    // Generous on purpose: the last fifth of the travel is all commitment.
    val threshold = .78f

    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }
    val progress = (knob.value / travelPx).coerceIn(0f, 1f)
    val shimmer = if (busy) motion.loop(1_400) else 0f
    var dragging by remember { mutableStateOf(false) }
    val thresholdReached = progress >= threshold

    // MARBLE_SLIDE_PARK_V146 — the knob parks on the side it was dragged to. While a route is
    // live (or being opened) it rests at the END of the track; otherwise at the START. This
    // replaces the old "always spring back to zero", which left a connected control still
    // reading "slide to connect".
    val restAtEnd = evidence.connected || evidence.connecting
    LaunchedEffect(restAtEnd, travelPx) {
        if (!dragging) {
            knob.animateTo(if (restAtEnd) travelPx else 0f, MarbleMotionSpecs.QuickReveal)
        }
    }

    // One haptic tick at the exact moment the finger crosses the threshold — never while the
    // knob animates on its own (completion beat, spring-back), only while dragged.
    LaunchedEffect(thresholdReached, dragging) {
        if (thresholdReached && dragging) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(width)
                    .height(trackHeight)
                    .clip(shape)
                    .background(homeCloudCardFill())
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                animatedTone.copy(alpha = .20f + .18f * progress),
                                animatedTone.copy(alpha = .06f)
                            )
                        )
                    )
                    .border(1.4.dp, animatedTone.copy(alpha = .38f), shape)
                    .semantics { contentDescription = "$label slider" },
                contentAlignment = Alignment.CenterStart
            ) {
                if (busy) {
                    Canvas(Modifier.matchParentSize()) {
                        // One travelling highlight, drawn only while the tunnel is actually
                        // opening or closing: the track states progress without ever moving.
                        val x = size.width * shimmer
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, animatedTone.copy(alpha = .22f), Color.Transparent),
                                startX = x - size.width * .22f,
                                endX = x + size.width * .22f
                            )
                        )
                    }
                }
                Text(
                    label,
                    color = animatedTone.copy(alpha = .92f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The threshold chevrons own the end of the track; the word stays clear.
                        .padding(start = knobSize, end = 44.dp)
                )
                // Threshold chevrons: dim at rest, lit once the knob crosses the commitment
                // point, so the eye knows exactly where the action arms.
                Canvas(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(width = 22.dp, height = 18.dp)
                ) {
                    val chevronTone = animatedTone.copy(
                        alpha = if (thresholdReached) .95f else .35f
                    )
                    val stroke = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    val midY = size.height / 2f
                    val arm = size.height * .32f
                    listOf(size.width * .30f, size.width * .62f).forEach { x ->
                        drawPath(
                            path = Path().apply {
                                moveTo(x - arm * .55f, midY - arm)
                                lineTo(x + arm * .55f, midY)
                                lineTo(x - arm * .55f, midY + arm)
                            },
                            color = chevronTone,
                            style = stroke
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = padding)
                        .offset { IntOffset(knob.value.toInt(), 0) }
                        .size(knobSize)
                        .clip(CircleShape)
                        .background(animatedTone.copy(alpha = .92f))
                        .pointerInput(armed, evidence.connected, travelPx) {
                            if (!armed) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    // MARBLE_SLIDE_PARK_V146 — the commit direction follows the
                                    // route state: connected drags back toward the start to
                                    // disconnect, disconnected drags to the end to connect. A
                                    // completed drag flies to the committed side and HOLDS there;
                                    // a short drag springs back to the state's own side.
                                    val completed = if (evidence.connected) {
                                        knob.value <= travelPx * (1f - threshold)
                                    } else {
                                        knob.value >= travelPx * threshold
                                    }
                                    scope.launch {
                                        if (completed) {
                                            knob.animateTo(
                                                if (evidence.connected) 0f else travelPx,
                                                MarbleMotionSpecs.QuickReveal
                                            )
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                            onToggle()
                                        } else {
                                            knob.animateTo(
                                                if (evidence.connected) travelPx else 0f,
                                                MarbleMotionSpecs.ResponseFloat
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch {
                                        knob.animateTo(
                                            if (evidence.connected) travelPx else 0f,
                                            MarbleMotionSpecs.ResponseFloat
                                        )
                                    }
                                }
                            ) { change, amount ->
                                change.consume()
                                scope.launch {
                                    knob.snapTo((knob.value + amount).coerceIn(0f, travelPx))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    HomeGlyphIcon(
                        connectButtonGlyph(evidence),
                        Aether.Void,
                        Modifier.size(knobSize * .42f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                Tr.now.slideToAct,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Style 3 — the classic power switch: a rectangle with the old desktop power glyph, a state lamp
 * and the action word. Nothing about it moves; the lamp and the frame carry the state colour.
 */
@Composable
private fun ConnectButtonClassic(
    evidence: HomeEvidence,
    animatedTone: Color,
    armed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    width: Dp
) {
    val motion = MarbleMotion.current
    val busy = evidence.connecting || evidence.disconnecting
    val sweep = if (busy) motion.loop(1_150) * 360f else 0f
    val shape = RoundedCornerShape(14.dp)
    val label = homeActionLabel(evidence)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .height(84.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .20f),
                    spotColor = animatedTone.copy(alpha = .28f)
                )
                .clip(shape)
                .background(homeCloudCardFill())
                .background(
                    Brush.verticalGradient(
                        listOf(animatedTone.copy(alpha = .16f), animatedTone.copy(alpha = .05f))
                    )
                )
                .border(1.6.dp, animatedTone.copy(alpha = .45f), shape)
                .kineticClickable(
                    enabled = armed,
                    role = Role.Button,
                    pressScale = 1f,
                    boundedShape = shape,
                    onClick = onToggle
                )
                .semantics { contentDescription = "$label connection button" }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = animatedTone.copy(alpha = .18f),
                        radius = r * .92f,
                        center = c,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    if (busy) {
                        drawArc(
                            color = animatedTone,
                            startAngle = -90f + sweep,
                            sweepAngle = 108f,
                            useCenter = false,
                            topLeft = Offset(c.x - r * .92f, c.y - r * .92f),
                            size = Size(r * 1.84f, r * 1.84f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                HomeGlyphIcon(
                    connectButtonGlyph(evidence),
                    animatedTone,
                    Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
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
            // State lamp / live ping: the classic switch shows live line state and ping.
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
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(animatedTone)
                )
            }
        }
    }
}

@Composable
internal fun HomePowerDock(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    flavor: HomeFlavor = HomeFlavor.IOS_SLIDER,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(homeCloudCardFill())
            .border(1.dp, tone.copy(alpha = .20f), shape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        HomePowerControl(
            evidence = evidence,
            tone = tone,
            onToggle = onToggle,
            flavor = flavor,
            diameter = 156.dp
        )
    }
}

internal fun homePingTappable(evidence: HomeEvidence): Boolean {
    val (_, state, _) = homeV137PingChannel(evidence)
    return state != ConnectionPingState.MEASURING
}

// MARBLE_SEAMLESS_LOOPS_V112
internal fun loopFade(t: Float): Float = sin((t.coerceIn(0f, 1f)) * PI.toFloat())

// ---------------------------------------------------------------------------------------------
// Glyph system
// ---------------------------------------------------------------------------------------------

internal enum class HomeGlyph {
    POWER, CHECK, RESET, COPY, REFRESH, MORE, PULSE, CLOCK, LIBRARY, PLUS, BOLT, PASTE, QR, INFO,

    /**
     * MARBLE_PING_CANCEL_V156 — a filled rounded square: the universal stop. It is the glyph the
     * Home pulse action swaps to while a bulk measurement is live, so the control that started a
     * sweep is always the one that can end it.
     */
    STOP
}

@Composable
internal fun HomeGlyphIcon(glyph: HomeGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (size.minDimension * .095f).coerceIn(1.3f, 3.2f)
        val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            HomeGlyph.POWER -> {
                drawArc(
                    color = color,
                    startAngle = -62f,
                    sweepAngle = 304f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawLine(color, Offset(w * .5f, h * .12f), Offset(w * .5f, h * .48f), stroke, StrokeCap.Round)
            }
            HomeGlyph.CHECK -> {
                val p = Path().apply {
                    moveTo(w * .20f, h * .52f)
                    lineTo(w * .42f, h * .74f)
                    lineTo(w * .80f, h * .28f)
                }
                drawPath(p, color, style = line)
            }
            HomeGlyph.RESET -> {
                drawArc(
                    color = color,
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawLine(color, Offset(w * .78f, h * .40f), Offset(w * .78f, h * .62f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .78f, h * .62f), Offset(w * .56f, h * .62f), stroke, StrokeCap.Round)
            }
            HomeGlyph.COPY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * .30f, h * .30f),
                    size = Size(w * .54f, h * .54f),
                    cornerRadius = CornerRadius(w * .10f, h * .10f),
                    style = line
                )
                val p = Path().apply {
                    moveTo(w * .30f, h * .64f)
                    lineTo(w * .18f, h * .64f)
                    lineTo(w * .18f, h * .18f)
                    lineTo(w * .64f, h * .18f)
                    lineTo(w * .64f, h * .30f)
                }
                drawPath(p, color, style = line)
            }
            HomeGlyph.REFRESH -> {
                drawArc(
                    color = color,
                    startAngle = 30f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(w * .16f, h * .16f),
                    size = Size(w * .68f, h * .68f),
                    style = line
                )
                drawLine(color, Offset(w * .72f, h * .20f), Offset(w * .86f, h * .20f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .86f, h * .20f), Offset(w * .86f, h * .34f), stroke, StrokeCap.Round)
            }
            HomeGlyph.MORE -> {
                drawCircle(color, stroke * .9f, Offset(w * .5f, h * .26f))
                drawCircle(color, stroke * .9f, Offset(w * .5f, h * .50f))
                drawCircle(color, stroke * .9f, Offset(w * .5f, h * .74f))
            }
            HomeGlyph.PULSE -> {
                val p = Path().apply {
                    moveTo(w * .14f, h * .50f)
                    lineTo(w * .34f, h * .50f)
                    lineTo(w * .44f, h * .22f)
                    lineTo(w * .56f, h * .78f)
                    lineTo(w * .66f, h * .50f)
                    lineTo(w * .86f, h * .50f)
                }
                drawPath(p, color, style = line)
            }
            HomeGlyph.CLOCK -> {
                drawCircle(color = color, radius = w * .38f, center = Offset(w * .5f, h * .5f), style = line)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .5f, h * .24f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .70f, h * .5f), stroke, StrokeCap.Round)
            }
            HomeGlyph.LIBRARY -> {
                drawRoundRect(color, Offset(w * .16f, h * .22f), Size(w * .20f, h * .56f), CornerRadius(3f), line)
                drawRoundRect(color, Offset(w * .40f, h * .22f), Size(w * .20f, h * .56f), CornerRadius(3f), line)
                drawRoundRect(color, Offset(w * .64f, h * .22f), Size(w * .20f, h * .56f), CornerRadius(3f), line)
            }
            HomeGlyph.PLUS -> {
                drawLine(color, Offset(w * .5f, h * .20f), Offset(w * .5f, h * .80f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .20f, h * .5f), Offset(w * .80f, h * .5f), stroke, StrokeCap.Round)
            }
            HomeGlyph.BOLT -> {
                val p = Path().apply {
                    moveTo(w * .54f, h * .14f)
                    lineTo(w * .30f, h * .52f)
                    lineTo(w * .50f, h * .52f)
                    lineTo(w * .46f, h * .86f)
                    lineTo(w * .70f, h * .46f)
                    lineTo(w * .50f, h * .46f)
                    close()
                }
                drawPath(p, color, style = line)
            }
            HomeGlyph.PASTE -> {
                drawRoundRect(color, Offset(w * .26f, h * .26f), Size(w * .52f, h * .58f), CornerRadius(4f), line)
                drawRoundRect(color, Offset(w * .38f, h * .14f), Size(w * .24f, h * .20f), CornerRadius(2f), line)
            }
            HomeGlyph.QR -> {
                drawRoundRect(color, Offset(w * .18f, h * .18f), Size(w * .28f, h * .28f), CornerRadius(3f), line)
                drawRoundRect(color, Offset(w * .54f, h * .18f), Size(w * .28f, h * .28f), CornerRadius(3f), line)
                drawRoundRect(color, Offset(w * .18f, h * .54f), Size(w * .28f, h * .28f), CornerRadius(3f), line)
                drawCircle(color, stroke * 1.1f, Offset(w * .68f, h * .68f))
            }
            HomeGlyph.INFO -> {
                drawCircle(color = color, radius = w * .38f, center = Offset(w * .5f, h * .5f), style = line)
                drawCircle(color = color, radius = stroke * .7f, center = Offset(w * .5f, h * .32f))
                drawLine(color, Offset(w * .5f, h * .44f), Offset(w * .5f, h * .68f), stroke, StrokeCap.Round)
            }
            HomeGlyph.STOP -> {
                // Solid, not outlined: a stop has to read at 12 dp and at a glance, and a hollow
                // square at this size reads as an empty checkbox instead.
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * .22f, h * .22f),
                    size = Size(w * .56f, h * .56f),
                    cornerRadius = CornerRadius(w * .12f, h * .12f)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stat autofit helpers (enforces MARBLE_HOME_PING_AUTOFIT_V112)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HomeStatValueText(
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Bold,
    sizeScale: Float = 1f
) {
    Text(
        text = value,
        color = tone,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = weight,
            fontSize = (15 * sizeScale).sp
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
internal fun HomeIdentityBlock(
    evidence: HomeEvidence,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            evidence.nodeName.ifBlank { Tr.now.chooseRoute },
            color = Aether.Ink,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            evidence.sourceName.ifBlank { "—" },
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun HomeIpRow(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(homeCloudInsetFill())
            .border(1.dp, homeCloudInsetBorder(), RoundedCornerShape(14.dp))
            .clickable(onClick = actions.onIpDetails)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (evidence.flag.isNotBlank()) evidence.flag else "🌐",
                fontSize = 15.sp
            )
            Spacer(Modifier.width(8.dp))
            // MARBLE_HOME_IP_STRIP_V144 — same quiet scale as the banner strip above: the
            // address never outranks its own row.
            Text(
                if (evidence.ip.isNotBlank()) evidence.ip else Tr.now.resolving,
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = actions.onCopyIp, modifier = Modifier.size(26.dp)) {
                HomeGlyphIcon(HomeGlyph.COPY, Aether.Cyan, Modifier.size(13.dp))
            }
            IconButton(onClick = actions.onIpDetails, modifier = Modifier.size(26.dp)) {
                HomeGlyphIcon(HomeGlyph.INFO, tone, Modifier.size(13.dp))
            }
        }
    }
}

@Composable
internal fun HomeSessionStats(
    evidence: HomeEvidence,
    actions: HomeActions,
    tone: Color,
    modifier: Modifier = Modifier
) {
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)
    val ping = homePingLabel(evidence)
    val pingTone = homePingTone(evidence, Aether.Cyan)

    // MARBLE_HOME_CLOUD_V140 — the stats strip is the same cloud card as every other Home box.
    HomeCloudCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Tr.now.uptime, color = Aether.InkMuted, style = MaterialTheme.typography.labelSmall)
                HomeStatValueText(uptime, tone, sizeScale = 1.1f)
            }
            Box(Modifier.width(1.dp).height(24.dp).background(homeCloudDivider()))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = actions.onTestPing)
            ) {
                Text(Tr.now.connectionPing, color = Aether.InkMuted, style = MaterialTheme.typography.labelSmall)
                HomeStatValueText(ping, pingTone, sizeScale = 1.1f)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// COMPONENT 1: WIDE STATUS BAR (Shared across all 4 iOS themes)
// ---------------------------------------------------------------------------------------------

/**
 * The iOS-styled comprehensive Wide Status Bar.
 *
 * Includes:
 * 1. Connection status (Connected / Disconnected / Connecting) with glowing dot & Uptime
 * 2. Connected server name, country flag & protocol tag
 * 3. One-shot ping check button (with auto-ping on first connect & inline non-shifting readout)
 * 4. Information icon (popup IP details dialog + animated opening IP badge + user IP when disconnected)
 * 5. Quick Add button (+ icon): auto-paste from clipboard and auto-connect
 * 6. SOCKS proxy display with one-tap copy button
 *
 * MARBLE_HOME_STABLE_GEOMETRY_V141 — every strip in this card is a permanent, fixed-height slot.
 * The SOCKS strip used to be composed only while connected, so the instant the tunnel came up
 * the card grew a whole row and shoved the server deck and the connect control down the page —
 * the exact "connecting pushes everything below" defect. The strip now always exists: it says
 * "waiting for connection" before the tunnel is up and the live address afterwards, and only its
 * colour changes. No AnimatedVisibility, no height animation, no layout change — ever.
 */
@Composable
internal fun IosStatusWideCard(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val t = Tr.now

    // MARBLE_PING_USER_TAPPED_ONLY_V143 — the banner never measures automatically. Ping is
    // only ever taken when the user taps the ping shortcut at the top of the Home page.
    val stateColor by animateColorAsState(
        targetValue = homeStateTone(evidence),
        animationSpec = MarbleMotionSpecs.Color,
        label = "status-color"
    )

    // MARBLE_HOME_CLOUD_V141 — the canonical cloud card: one opaque white box, exactly the size
    // of the card, one hairline, one shallow shadow. Nothing translucent, nothing nested.
    HomeCloudCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Slot 1 (fixed height): status line. The top action cluster lives outside the
            // banner as a separate transparent cluster (MARBLE_HOME_BANNER_V143).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(stateColor = stateColor, busy = evidence.connecting)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        evidence.connected -> t.statusProtected
                        evidence.connecting -> t.securingRoute
                        evidence.disconnecting -> t.closingRoute
                        evidence.blocked -> t.connectionStopped
                        else -> t.socksStandby
                    }.uppercase(),
                    color = stateColor,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (evidence.connected) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• ${rememberUptimeLabel(evidence.connectedSinceMs)}",
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = homeCloudDivider())

            // ── Slot 2 (fixed height): route identity + ping readout ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flag inside a flat tile so emoji flags of different drawing heights can
                    // never wobble the baseline of the row.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(homeCloudInsetFill())
                            .border(1.dp, homeCloudInsetBorder(), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (evidence.flag.isNotBlank()) evidence.flag else "🌐",
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = evidence.nodeName.ifBlank { t.chooseRoute },
                            color = Aether.Ink,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val proto = evidence.profile?.scheme?.uppercase() ?: "PROXY"
                            Text(
                                text = proto,
                                color = Aether.CyanBright,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            if (evidence.sourceName.isNotBlank()) {
                                Text("•", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = evidence.sourceName,
                                    color = Aether.InkMuted,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Inline Ping Result — reserved width, so digits appearing never re-flow the row.
                val pingVal = homePingLabel(evidence)
                val pingT = homePingTone(evidence, Aether.Cyan)
                Row(
                    modifier = Modifier
                        .widthIn(min = 58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pingT.copy(alpha = 0.14f))
                        .border(1.dp, pingT.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable(enabled = homePingTappable(evidence)) { actions.onTestPing() }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pingVal,
                        color = pingT,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Slot 3 (fixed height): IP identity strip ───────────────────────────────
            // MARBLE_HOME_IP_STRIP_V144 — the address used to be the only bodySmall (12sp) ink
            // in a strip of labelSmall (10.5sp) copy, so one full step larger than its own tag,
            // country and details link it visually shouted over the node name above it. The whole
            // strip now speaks the same quiet size: the address keeps its monospace face and a
            // semibold cut so it stays scrutable, but at the strip's own scale and in one
            // ellipsized line, so long hostnames can never stretch the banner.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(homeCloudInsetFill())
                    .border(1.dp, homeCloudInsetBorder(), RoundedCornerShape(12.dp))
                    .clickable { actions.onIpDetails() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "IP",
                        color = HomeCloud.Accent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (evidence.ip.isNotBlank()) evidence.ip else "127.0.0.1",
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (evidence.countryCode.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "(${evidence.countryCode})",
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                // MARBLE_HOME_IP_STRIP_V151 — the "Show complete IP information" caption is gone.
                //
                // The strip already opens the full report on tap, and the caption repeated the
                // action in nine words under a 32 dp row that only had room to ellipsize it. The
                // INFO glyph stays as the affordance; the words survive as its content
                // description, where a screen reader can still read them and a sighted user never
                // has to.
                HomeGlyphIcon(
                    glyph = HomeGlyph.INFO,
                    color = HomeCloud.Accent,
                    modifier = Modifier
                        .size(12.dp)
                        .semantics { contentDescription = t.ipDetails }
                )
            }

        }
    }
}

/**
 * MARBLE_HOME_WORDMARK_V145 — the product signature that opens every Home presentation.
 *
 * A single line of type, drawn with the brand's own prism ramp (ice → cyan → amethyst →
 * emerald) through a text brush, so it is one glyph run rather than four coloured Text nodes
 * that would break apart under RTL, ellipsis or a font change. It is decorative, so it carries
 * no click target and no semantics: nothing about the layout below it moves because of it.
 */
@Composable
internal fun MarbleWordmark(modifier: Modifier = Modifier) {
    // The palette tokens are theme-aware composable reads, so they are resolved here and the
    // brush is only re-created when one of them actually changes.
    val ice = Aether.CyanBright
    val cyan = Aether.Cyan
    val amethyst = Aether.AmethystBright
    val emerald = Aether.Emerald
    val ramp = remember(ice, cyan, amethyst, emerald) {
        Brush.linearGradient(
            0.00f to ice,
            0.42f to cyan,
            0.72f to amethyst,
            1.00f to emerald
        )
    }
    // MARBLE_HOME_TOPBAR_CLEAN_V155 — the wordmark grew a step (titleLarge → headlineSmall) so
    // the logo reads as the header's primary element now that the plate behind it is gone.
    Text(
        text = "MarbleNG",
        style = MaterialTheme.typography.headlineSmall.copy(
            brush = ramp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * MARBLE_HOME_BANNER_V143 — the top actions (add, ping, IP details) live OUTSIDE the status
 * banner: a transparent cluster above it. There is no background pill, no card frame, and every
 * icon is a true circle so a tap reads as an icon, not a button.
 *
 * MARBLE_HOME_WORDMARK_V145 — the row now opens with the MarbleNG wordmark, so all four Home
 * presentations carry the product signature in the same place, at the same size.
 *
 * MARBLE_HOME_ADD_MENU_V145 — the + opens its menu ANCHORED UNDER THE + (a DropdownMenu inside
 * the icon's own Box) instead of throwing a full-screen dialog over the page. A three-entry
 * chooser is a menu; making it a modal meant the page vanished, the backdrop dimmed and the
 * user had to travel back to the icon they were already touching.
 *
 * MARBLE_HOME_PING_ROUTE_GROUP_V146 — the pulse icon measures the subscription that the route
 * currently shown on the page belongs to, which is the question the Home page is asking ("how is
 * the subscription I am looking at doing?"). The per-route ping of that same server is still one
 * tap away on the status banner.
 */
@Composable
internal fun HomeTopActionBar(
    evidence: HomeEvidence,
    actions: HomeActions,
    repo: AppRepository,
    modifier: Modifier = Modifier
) {
    var addMenuOpen by remember { mutableStateOf(false) }
    val groupLabel = repo.homeGroupPingLabel()
    val groupBusy = repo.homeGroupPingRunning

    // MARBLE_HOME_TOPBAR_CLEAN_V155 — the header plate is gone. The translucent gradient plate
    // this row used to sit on read as a second status card fighting the banner underneath it, so
    // it is removed: the wordmark and the three actions now float directly on the page with no
    // pill, no frame and no background behind them.
    //
    // MARBLE_HOME_TOPBAR_NO_STATUS_V156 — the status dot and its CONNECTED / READY word next to
    // the logo are gone too, on all four Home presentations. They were a third place announcing a
    // session state the status banner underneath already owns in full (with the reason, the
    // uptime and the route), and next to the wordmark they read as part of the brand rather than
    // as information. The header is now the product signature and its three actions.
    val cancelling = repo.probeCancelling
    val sweeping = groupBusy || cancelling
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarbleWordmark()
        }

        Box {
            HomeBareAction(
                glyph = HomeGlyph.PLUS,
                tone = Aether.CyanBright,
                description = Tr.now.proAddRoute,
                onClick = { addMenuOpen = true }
            )
            HomeAddRouteMenu(
                expanded = addMenuOpen,
                onDismiss = { addMenuOpen = false },
                actions = actions
            )
        }
        // MARBLE_PING_CANCEL_V156 — the same icon that starts the group sweep ends it. While a
        // sweep is live the pulse becomes a filled STOP square, so the Home page can cancel a
        // bulk measurement without travelling to the Servers page.
        HomeBareAction(
            glyph = if (sweeping) HomeGlyph.STOP else HomeGlyph.PULSE,
            tone = if (sweeping) Aether.Danger else Aether.Emerald,
            description = if (sweeping) trx("Cancel measuring") else "${Tr.now.testPing} • $groupLabel",
            enabled = sweeping || !groupBusy,
            busy = false,
            onClick = { if (sweeping) repo.cancelProbes() else actions.onPingGroup() }
        )
        HomeBareAction(
            glyph = HomeGlyph.INFO,
            tone = Aether.AmethystBright,
            description = Tr.now.ipDetails,
            onClick = {
                if (repo.serverIntel == null) repo.refreshServerIntel(evidence.profile, force = true)
                actions.onIpDetails()
            }
        )
    }
}

/**
 * MARBLE_HOME_ADD_MENU_V145 — the three ways a server reaches MarbleNG, in a menu that hangs
 * directly under the + icon. Styling matches the group menu of the same page.
 */
@Composable
private fun HomeAddRouteMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: HomeActions
) {
    val t = Tr.now
    HomeGroupMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        tone = Aether.CyanBright
    ) {
        HomeActionMenuItem(
            label = t.pasteShortcut,
            detail = trx("Import every link on the clipboard"),
            glyph = HomeGlyph.PASTE,
            tone = Aether.CyanBright
        ) {
            onDismiss()
            actions.onPasteImport()
        }
        HomeActionMenuItem(
            label = t.qrShortcut,
            detail = trx("Scan with the camera or pick an image"),
            glyph = HomeGlyph.QR,
            tone = Aether.Emerald
        ) {
            onDismiss()
            actions.onQrImport()
        }
        HomeActionMenuItem(
            label = t.library,
            detail = trx("Open the Servers page to add or edit"),
            glyph = HomeGlyph.LIBRARY,
            tone = Aether.AmethystBright
        ) {
            onDismiss()
            actions.onAddRoute()
        }
    }
}

/** One labelled action row of a Home dropdown: glyph chip, title and a quiet explanation. */
@Composable
private fun HomeActionMenuItem(
    label: String,
    detail: String,
    glyph: HomeGlyph,
    tone: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tone.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(glyph, tone, Modifier.size(15.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                label,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A transparent circular top action: no background surface, rounded icon, kinetic press only.
 *
 * MARBLE_HOME_GROUP_PING_V145 — [busy] draws the work in the icon's own footprint (the ring
 * replaces the glyph, the circle never resizes), so a sweep that takes a few seconds is visibly
 * running instead of looking like a tap that did nothing.
 */
@Composable
private fun HomeBareAction(
    glyph: HomeGlyph,
    tone: Color,
    description: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .kineticClickable(
                enabled = enabled && !busy,
                role = Role.Button,
                pressScale = .92f,
                boundedShape = CircleShape,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = tone,
                strokeWidth = 2.dp
            )
        } else {
            HomeGlyphIcon(
                glyph,
                if (enabled) tone else tone.copy(alpha = .40f),
                Modifier.size(18.dp)
            )
        }
    }
}

/** The semantic state colour of the Home instrument — one function, four themes, no drift. */
@Composable
internal fun homeStateTone(evidence: HomeEvidence): Color = when {
    evidence.connected -> Aether.Emerald
    evidence.connecting -> Aether.CyanBright
    evidence.disconnecting -> Aether.Amber
    evidence.blocked -> Aether.Danger
    else -> Aether.SlateBright
}

/** Flat status pip with a soft halo; breathes only while a handshake is actually running. */
@Composable
private fun StatusDot(stateColor: Color, busy: Boolean) {
    val motion = MarbleMotion.current
    Canvas(modifier = Modifier.size(18.dp)) {
        // The shared clock is read in the draw phase: ambient motion costs zero recompositions.
        val breathe = motion.breathe(900)
        val haloAlpha = if (busy) 0.22f + 0.20f * breathe else 0.16f
        drawCircle(color = stateColor.copy(alpha = haloAlpha), radius = size.minDimension * 0.5f)
        drawCircle(
            color = stateColor.copy(alpha = if (busy) 0.75f + 0.25f * breathe else 1f),
            radius = size.minDimension * 0.28f
        )
    }
}

// ---------------------------------------------------------------------------------------------
// COMPONENT 2: SUB & SERVER LIST BOX (Inner scrollable, centered sub name)
// ---------------------------------------------------------------------------------------------

/**
 * Scrollable Box showing the user's selected sub/group name uniquely centered at the top,
 * and the servers inside that sub scrollable below the sub name.
 *
 * MARBLE_HOME_FLOATING_CLEARANCE_V141 — [bottomOverlayClearance] reserves room for controls
 * that float above the list (the Theme 2 split button), so the last server row is never hidden
 * underneath them.
 *
 * MARBLE_HOME_SMART_LIST_HEIGHT_V144 — the box owns no fixed height. It wraps its content
 * (the sub header plus exactly as many server rows as exist) and only stops growing where the
 * page itself runs out of room: every caller places this box with `weight(1f, fill = false)`,
 * so the page column hands it the remaining screen space as its measuring bound and the inner
 * list scrolls the moment the servers outgrow that bound. The old 244.dp ceiling is gone —
 * it clipped large libraries into a peephole and left small ones floating in empty card.
 * A finite [maxListHeight] is still honoured when a caller passes one (Theme 4's user-chosen
 * card height); [Dp.Unspecified] — the default — means "grow with the servers, scroll on
 * overflow", which is the contract every fixed theme now uses.
 */
@Composable
internal fun IosServerListBox(
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    bottomOverlayClearance: Dp = 0.dp,
    maxListHeight: Dp = Dp.Unspecified
) {
    val t = Tr.now
    val activeSubId = repo.librarySourceFilter
    val allSubs = repo.subscriptions
    val activeSubName = when {
        activeSubId.isBlank() || activeSubId == "all" -> t.homeAllServers
        activeSubId == "manual" -> t.homeManualGroup
        else -> allSubs.firstOrNull { it.id == activeSubId }?.name ?: t.homeAllServers
    }
    // MARBLE_HOME_MIRRORS_SERVERS_V150 — the Home server section is not its own state. It is
    // the exact Servers-page list: the same filter (protocol / reachable / max-ping, scoped to
    // the group chip), the same sort mode the user chose (nodeSortMode + reverse), the same
    // measured latency per server, and the same selected/active predicate. A user who sorted
    // Servers by ping and picked the winner sees that winner, that order and those pings here.
    val settings = repo.settings
    val benchmarks = repo.benchmarks.associateBy { it.profileId }
    val visibleServers = ServersQuery.sort(
        profiles = ServersQuery.visible(
            profiles = repo.libraryProfiles,
            filter = ServersFilter(
                protocol = settings.serversProtocolFilter,
                sourceId = if (activeSubId.isBlank()) "all" else activeSubId,
                onlyReachable = settings.serversOnlyReachable,
                maxPingMs = settings.serversMaxPingMs
            ),
            benchmarks = benchmarks
        ),
        mode = settings.nodeSortMode,
        reverse = settings.nodeSortReverse,
        benchmarks = benchmarks
    )

    // MARBLE_HOME_CLOUD_V140/V141 — the server list is a cloud card: one opaque box, quiet
    // inset rows inside it, and only the selected server earns the sky fill + accent rim.
    // MARBLE_HOME_SMART_LIST_HEIGHT_V144 — the cap below exists only for callers that pass an
    // explicit maxListHeight. With the default (Unspecified) the page's own weight bound is the
    // only ceiling, so the card is exactly as tall as its servers until the screen ends.
    val listHeightCap = if (maxListHeight.isSpecified) {
        Modifier.heightIn(max = maxListHeight)
    } else {
        Modifier
    }
    HomeCloudCard(
        modifier = modifier
            .fillMaxWidth()
            .then(listHeightCap),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Group selector: name + live count chip + chevron, one flat pill.
            var groupDropdownOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(homeCloudInsetFill())
                    .border(1.dp, homeCloudInsetBorder(), RoundedCornerShape(14.dp))
                    .clickable { groupDropdownOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeSubName,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(7.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(HomeCloud.Accent.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${visibleServers.size}",
                        color = HomeCloud.Accent,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.weight(1f))
                GroupChevron(HomeCloud.Accent, Modifier.size(12.dp))
            }

            HomeGroupMenu(
                expanded = groupDropdownOpen,
                onDismiss = { groupDropdownOpen = false },
                tone = HomeCloud.Accent
            ) {
                HomeGroupMenuItem(
                    name = t.homeAllServers,
                    count = repo.libraryProfiles.size,
                    // The repository normalises "" to "all", so both spellings mean "All".
                    selected = activeSubId.isBlank() || activeSubId == "all",
                    tone = HomeCloud.Accent,
                    onClick = {
                        repo.selectLibrarySource("")
                        groupDropdownOpen = false
                    }
                )
                HomeGroupMenuItem(
                    name = t.homeManualGroup,
                    count = repo.libraryProfiles.count { it.subscriptionId == "manual" },
                    selected = activeSubId == "manual",
                    tone = HomeCloud.Accent,
                    onClick = {
                        repo.selectLibrarySource("manual")
                        groupDropdownOpen = false
                    }
                )
                allSubs.forEach { sub ->
                    HomeGroupMenuItem(
                        name = sub.name,
                        count = repo.libraryProfiles.count { it.subscriptionId == sub.id },
                        selected = activeSubId == sub.id,
                        tone = HomeCloud.Accent,
                        onClick = {
                            repo.selectLibrarySource(sub.id)
                            groupDropdownOpen = false
                        }
                    )
                }
            }

            HorizontalDivider(color = homeCloudDivider(), modifier = Modifier.padding(bottom = 6.dp))

            // Inner Scrollable Server List (No whole-page scroll!)
            if (visibleServers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = t.homeNoServers,
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .then(listHeightCap),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = bottomOverlayClearance)
                ) {
                    items(visibleServers, key = { it.id }) { server ->
                        // MARBLE_HOME_MIRRORS_SERVERS_V150 — same predicates as the Servers page:
                        // selected is the stored selection (any state), active is the row actually
                        // carrying traffic — so the Home list and Servers list agree about which
                        // server is chosen and which is live, and a tap selects exactly as Servers does.
                        val isSelected = repo.isSelectedProfile(server)
                        val isConnected = repo.isActiveProfile(server)
                        // animateItem keeps reorders/gliding smooth without touching row heights.
                        Box(Modifier.animateItem()) {
                            IosServerItemRow(
                                server = server,
                                result = benchmarks[server.id],
                                isSelected = isSelected,
                                isConnected = isConnected,
                                testing = repo.probeStateOf(server.id) == ProbeState.TESTING,
                                onClick = {
                                    if (repo.probeActive || repo.probeCancelling) {
                                        repo.setRuntimeMessage("Wait until ping finishes before changing server")
                                    } else {
                                        repo.selectProfile(server)
                                        if (evidence.connected) {
                                            actions.onConnectProfile(server)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * MARBLE_HOME_GROUP_MENU_V143 — the subscription/group chooser is not a bare Android dropdown
 * any more. It is a rounded, elevated product panel with a hairline, generous row height, a
 * live count chip and an explicit selected check.
 */
@Composable
internal fun HomeGroupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    tone: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 240.dp, max = 320.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = Aether.VoidElevated,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, tone.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            content()
        }
    }
}

@Composable
internal fun HomeGroupMenuItem(
    name: String,
    count: Int,
    selected: Boolean,
    tone: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tone.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) tone.copy(alpha = 0.40f) else Color.Transparent, shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            name,
            color = if (selected) tone else Aether.Ink,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background((if (selected) tone else Aether.InkMuted).copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                count.toString(),
                color = if (selected) tone else Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                maxLines = 1
            )
        }
        if (selected) {
            HomeGlyphIcon(HomeGlyph.CHECK, tone, Modifier.size(15.dp))
        }
    }
}

/** Tiny vector chevron-down for the group selector (font-independent, like every glyph here). */
@Composable
private fun GroupChevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = (size.minDimension * 0.14f).coerceIn(1.2f, 2.6f)
        drawLine(
            color,
            Offset(size.width * 0.22f, size.height * 0.38f),
            Offset(size.width * 0.5f, size.height * 0.66f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.5f, size.height * 0.66f),
            Offset(size.width * 0.78f, size.height * 0.38f),
            stroke,
            StrokeCap.Round
        )
    }
}

/** The wire-scheme colour family of a server row — one flat hue per protocol. */
@Composable
private fun protocolTone(scheme: String): Color = when (scheme.trim().lowercase()) {
    "vmess" -> Aether.Cyan
    "vless" -> Aether.Amethyst
    "trojan" -> Aether.Amber
    "shadowsocks", "ss" -> Aether.Emerald
    "ssh" -> Aether.SlateBright
    "socks", "http", "https" -> Aether.CyanBright
    else -> Aether.Cyan
}

/** The 1–2 letter monogram of a wire scheme, drawn in the protocol colour tile. */
private fun protocolMonogram(scheme: String): String = when (scheme.trim().lowercase()) {
    "vmess" -> "VM"
    "vless" -> "VL"
    "trojan" -> "TJ"
    "shadowsocks", "ss" -> "SS"
    "ssh" -> "SH"
    "socks" -> "SO"
    "http", "https" -> "HT"
    else -> scheme.trim().take(2).uppercase().ifBlank { "PR" }
}

/**
 * MARBLE_HOME_SERVER_ROW_V141 — one clean, flat server row.
 *
 * A protocol monogram tile (colour-coded per wire scheme) leads, name + scheme/host caption
 * follow, and the trailing state is one of three quiet marks: nothing (resting), a check
 * (selected) or a live pill (carrying traffic). Resting rows are near-invisible insets; the
 * selected row is the one saturated element with the sky fill and accent rim.
 *
 * MARBLE_HOME_MIRRORS_SERVERS_V150 — the row also carries the server's measured latency, the
 * exact same value the Servers page shows for that server, so sorting by ping in Servers and
 * reading the Home list cannot disagree. A probe that is running shows a small spinner; one that
 * ran and failed shows ✕; one that was never attempted shows —.
 */
@Composable
private fun IosServerItemRow(
    server: ProxyProfile,
    result: BenchmarkResult?,
    isSelected: Boolean,
    isConnected: Boolean,
    testing: Boolean,
    onClick: () -> Unit
) {
    val motion = MarbleMotion.current
    val t = Tr.now
    val rowShape = RoundedCornerShape(14.dp)
    val itemBg by animateColorAsState(
        targetValue = if (isSelected) homeCloudSelectedFill() else homeCloudInsetFill(),
        animationSpec = MarbleMotionSpecs.Color,
        label = "srv-row-bg"
    )
    val itemBorder by animateColorAsState(
        targetValue = if (isSelected) homeCloudSelectedBorder() else homeCloudInsetBorder(),
        animationSpec = MarbleMotionSpecs.Color,
        label = "srv-row-border"
    )
    val tone = protocolTone(server.scheme)
    // Captured in composition: the draw lambda below must not read @Composable palette getters.
    val liveTone = Aether.Emerald
    // MARBLE_HOME_MIRRORS_SERVERS_V150 — same "measured" gate as the Servers page
    // (ServersPingCapsule): a latency only counts once a real measurement succeeded and is not a
    // synthetic sub-20 ms handshake. A failed probe is an attempted (red ✕) fact, never "—".
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= 20 }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val attempted = result != null && measured == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) HomeCloud.SelectedElevation else 0.dp,
                shape = rowShape,
                spotColor = HomeCloud.Accent.copy(alpha = 0.30f)
            )
            .clip(rowShape)
            .background(itemBg)
            .border(1.dp, itemBorder, rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Protocol monogram tile — flat tint fill + monogram, one hue per wire scheme.
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tone.copy(alpha = if (isSelected) 0.16f else 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = protocolMonogram(server.scheme),
                color = tone,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                color = if (isSelected) Aether.Ink else Aether.Ink.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = server.scheme.uppercase(),
                    color = tone,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (server.host.isNotBlank()) {
                    Text("•", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = server.host,
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Trailing state — measured latency plus one quiet mark, never a layout of its own.
        // MARBLE_PING_AIR_V152 — one step more air between the tone-only latency and the mark,
        // so the two never read as one glued chip now that the slab fill is gone.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // MARBLE_HOME_MIRRORS_SERVERS_V150 — the same latency capsule the Servers page shows.
            HomeServerLatencySlab(
                latencyMs = latency,
                measured = measured != null,
                testing = testing,
                attempted = attempted
            )
            when {
                isConnected -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Aether.Emerald.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(6.dp)) {
                            drawCircle(
                                color = liveTone.copy(alpha = 0.55f + 0.45f * motion.breathe(1400)),
                                radius = size.minDimension * 0.5f
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = t.homeConnectedBadge,
                            color = Aether.Emerald,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                isSelected -> {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(HomeCloud.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        HomeGlyphIcon(HomeGlyph.CHECK, Color.White, Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

/**
 * MARBLE_HOME_MIRRORS_SERVERS_V150 — a compact latency readout for one Home server row. It is the
 * Home expression of the Servers page's latency capsule (same measured gate, same tone ramp),
 * just at the row's own scale so the list never outshouts the status banner.
 *
 * MARBLE_PING_AIR_V152 — the tinted slab is gone, matching the Servers page: inside a row that
 * already carries a protocol tile, badges and a live pill, the latency's own background was one
 * surface too many and squeezed the number against its edges. The measurement now stands alone
 * in its quality tone with room around it.
 */
@Composable
private fun HomeServerLatencySlab(
    latencyMs: Int,
    measured: Boolean,
    testing: Boolean,
    attempted: Boolean
) {
    // Same tone ramp as the Servers page's latency capsule: green <100 ms, amber ≤250 ms, red
    // above that. A probe that ran and failed reads as a red ✕, never as an unknown —.
    val tone = when {
        testing -> Aether.Cyan
        measured -> when {
            latencyMs < 100 -> Aether.Emerald
            latencyMs <= 250 -> Aether.Amber
            else -> Aether.Danger
        }
        attempted -> Aether.Danger
        else -> Aether.InkFaint
    }
    val quality = when {
        latencyMs <= 0 -> "Waiting"
        latencyMs < 100 -> "Fast"
        latencyMs <= 250 -> "Fair"
        else -> "Slow"
    }
    val spoken = when {
        testing -> trx("Testing server")
        measured -> trx("Latency") + " $latencyMs ms, $quality"
        attempted -> trx("No response")
        else -> trx("Not measured")
    }
    Box(
        modifier = Modifier
            .height(26.dp)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        when {
            testing -> CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                color = tone,
                strokeWidth = 1.6.dp
            )
            !measured -> Text(
                if (attempted) "✕" else "—",
                color = tone,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "$latencyMs",
                    color = tone,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1
                )
                Text(
                    trx("ms"),
                    color = tone.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// COMPONENT 3: SLIDE TO CONNECT (Theme 1 Slider Control)
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_CONNECT_CONTROLS_V141 — the Theme 1 connect control, redrawn flat and alive.
 *
 * A pill track of solid card white with a state-tinted hairline; while armed, a soft light band
 * sweeps the track so the control visibly invites the drag; while a handshake runs, the band
 * accelerates into an indeterminate sweep; the drag fill and the flat thumb follow the finger.
 * The drag gesture (including its RTL mirroring and the 65% release threshold) is unchanged —
 * only the surface was redesigned.
 */
@Composable
internal fun IosSlideToConnect(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = MarbleMotion.current
    val trackShape = RoundedCornerShape(30.dp)
    val t = Tr.now

    val busy = evidence.connecting || evidence.disconnecting
    val tone by animateColorAsState(
        targetValue = when {
            evidence.connected -> Aether.Danger
            busy -> Aether.CyanBright
            else -> Aether.Emerald
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "slide-tone"
    )

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 52.dp
    val density = LocalDensity.current
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    val dragOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }

    val labelText = when {
        evidence.connected -> t.slideToDisconnect
        evidence.connecting -> t.securingRoute
        evidence.disconnecting -> t.closingRoute
        else -> t.slideToConnect
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(3.dp, trackShape, spotColor = tone.copy(alpha = 0.18f))
            .clip(trackShape)
            .background(homeCloudCardFill())
            .border(1.5.dp, tone.copy(alpha = 0.40f), trackShape)
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        val maxDragPx = max(1f, trackWidthPx - thumbSizePx - 8f)
        val progress = (dragOffset.value / maxDragPx).coerceIn(0f, 1f)

        // MARBLE_SLIDE_PARK_V146 — the thumb parks on the side it was dragged to, instead of
        // always springing back to the start. While a route is live (or still being opened) the
        // thumb rests at the END; otherwise it rests at the START. The offset is layout-aware,
        // so "end" is the right edge in LTR and the left edge in RTL — the physical side the
        // finger travelled to either way.
        val restOffset = if (evidence.connected || evidence.connecting) maxDragPx else 0f
        LaunchedEffect(evidence.connected, evidence.connecting, maxDragPx) {
            // Never fight the finger: a state change that lands mid-drag waits for the release.
            if (!dragging) {
                dragOffset.animateTo(restOffset, tween(260, easing = FastOutSlowInEasing))
            }
        }

        // Ambient sheen: a single light band on the shared motion clock — slow when armed,
        // fast while the tunnel negotiates, invisible once the user owns the gesture. The clock
        // is read inside the draw lambda, so the sweep costs zero recompositions.
        Canvas(modifier = Modifier.matchParentSize()) {
            val sheenPhase = motion.loop(if (busy) 1100 else 3000)
            val sheenAlpha = (if (busy) 0.30f else 0.20f) * (1f - progress)
            if (sheenAlpha > 0.01f) {
                val band = size.width * 0.34f
                val x = sheenPhase * (size.width + band) - band
                drawRect(
                    topLeft = Offset(x, 0f),
                    size = Size(band, size.height),
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = sheenAlpha),
                            Color.White.copy(alpha = 0f)
                        ),
                        startX = x,
                        endX = x + band
                    )
                )
            }
        }

        // Drag fill — flat state tint that grows with the thumb.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceAtLeast(0.04f))
                .background(tone.copy(alpha = 0.16f))
        )

        // Centered Label
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelText,
                color = Aether.Ink.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        // Sliding Thumb Knob — flat disc, glyph, micro-lift while dragged.
        Box(
            modifier = Modifier
                .padding(start = 5.dp, end = 5.dp)
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(thumbSizeDp)
                .graphicsLayer {
                    val lift = 1f + 0.06f * (dragOffset.value / max(1f, maxDragPx))
                    scaleX = lift
                    scaleY = lift
                }
                .shadow(6.dp, CircleShape, spotColor = tone)
                .clip(CircleShape)
                .background(tone)
                .pointerInput(evidence.connected, busy, maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            // MARBLE_SLIDE_PARK_V146 — the commit direction follows the route
                            // state: while connected the safety switch is dragged back toward the
                            // start to disconnect; while disconnected it is dragged to the end to
                            // connect. On a completed drag the thumb flies to the committed side
                            // and HOLDS there; a short drag springs back to the state's own side
                            // instead of always to zero.
                            val completed = if (evidence.connected) {
                                dragOffset.value <= maxDragPx * 0.35f
                            } else {
                                dragOffset.value >= maxDragPx * 0.65f
                            }
                            if (completed) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                actions.onToggleConnection()
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        if (evidence.connected) 0f else maxDragPx,
                                        tween(220, easing = FastOutSlowInEasing)
                                    )
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        restOffset,
                                        tween(250, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            coroutineScope.launch {
                                dragOffset.animateTo(restOffset, tween(200))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = if (isRtl) -dragAmount else dragAmount
                            coroutineScope.launch {
                                val next = (dragOffset.value + delta).coerceIn(0f, maxDragPx)
                                dragOffset.snapTo(next)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.POWER, Color.White, Modifier.size(26.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// THEME 1: iOS SLIDER THEME (Fixed Screen)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HomeThemeSlider(
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top actions (outside the banner) + Wide Status Bar
        HomeTopActionBar(evidence, actions, repo)
        IosStatusWideCard(evidence, actions)

        // Center: Sub & Server List Box (Scrollable inner list)
        IosServerListBox(
            repo = repo,
            evidence = evidence,
            actions = actions,
            modifier = Modifier.weight(1f, fill = false)
        )

        // Bottom: Slide to connect Slider
        IosSlideToConnect(evidence, actions)
    }
}

// ---------------------------------------------------------------------------------------------
// THEME 2: iOS FLOATING SPLIT-BUTTON THEME (Fixed Screen)
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_CONNECT_CONTROLS_V141 — the Theme 2 floating control, redrawn flat.
 *
 * Armed: a solid ice-blue disc with a breathing halo ring. Busy: the halo becomes a spinning
 * arc that orbits the disc. Connected: the whole control morphs into the split pair — a flat
 * danger pause and a flat emerald ping. Every ambient phase runs on Marble's one shared frame
 * clock, and press feedback is the product-standard kinetic scale.
 */
@Composable
private fun FloatingConnectFab(
    evidence: HomeEvidence,
    onToggle: () -> Unit
) {
    val motion = MarbleMotion.current
    val busy = evidence.connecting || evidence.disconnecting
    val tone by animateColorAsState(
        targetValue = if (busy) Aether.CyanBright else Aether.Cyan,
        animationSpec = MarbleMotionSpecs.Color,
        label = "fab-tone"
    )

    Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        // Orbit ring: spinning arc while busy, breathing halo while armed. Both phases are read
        // in the draw layer, so the orbit never recomposes the FAB.
        Canvas(modifier = Modifier.size(88.dp)) {
            val spin = motion.loop(1150)
            val breathe = motion.breathe(2400)
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val ring = Size(size.width - inset * 2f, size.height - inset * 2f)
            if (busy) {
                rotate(degrees = spin * 360f) {
                    drawArc(
                        color = tone,
                        startAngle = -90f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = ring,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawCircle(
                    color = tone.copy(alpha = 0.16f + 0.22f * breathe),
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(stroke)
                )
            }
        }

        // Core button — flat solid disc.
        Box(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    val breathe = motion.breathe(2400)
                    val scale = if (busy) 1f + 0.04f * breathe else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(4.dp, CircleShape, spotColor = tone)
                .clip(CircleShape)
                .background(tone)
                .kineticClickable(boundedShape = CircleShape) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.POWER, Color.White, Modifier.size(32.dp))
        }
    }
}

/** One flat circular secondary action of the Theme 2 split pair. */
@Composable
private fun FloatingSplitAction(
    tone: Color,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .shadow(3.dp, CircleShape, spotColor = tone)
            .clip(CircleShape)
            .background(tone)
            .kineticClickable(enabled = enabled, boundedShape = CircleShape, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun HomeThemeFloating(
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(bottom = bottomClearance)
    ) {
        // Main fixed column: top actions + Status Bar + Expanded Server List Box
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeTopActionBar(evidence, actions, repo)
            IosStatusWideCard(evidence, actions)
            IosServerListBox(
                repo = repo,
                evidence = evidence,
                actions = actions,
                modifier = Modifier.weight(1f, fill = false),
                // The split FAB floats above the last rows; reserve the room so no server is
                // ever hidden underneath it (MARBLE_HOME_FLOATING_CLEARANCE_V141).
                bottomOverlayClearance = 104.dp
            )
        }

        // Floating Action Controls Pinned to the Right. MARBLE_MODULAR_FLOATING_V151 moved the
        // control into its own composable so the customizer layout can offer the exact same one.
        HomeFloatingSplitControl(
            evidence = evidence,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 12.dp)
        )
    }
}

/**
 * MARBLE_MODULAR_FLOATING_V151 — Home style 2's floating control, lifted out of that theme.
 *
 * The behaviour is unchanged from the theme it came from: while the tunnel is down it is one
 * shutter-style FAB that starts the connection, and the moment it is up it splits into two
 * stacked actions — disconnect and ping — so the two things a connected user reaches for are
 * already under the thumb. It lives here rather than inside [HomeThemeFloating] because the
 * customizer layout ([HomeThemeModular]) now offers it as its own connect style, and two copies
 * of an animation would drift apart the first time either is tuned.
 */
@Composable
internal fun HomeFloatingSplitControl(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = evidence.connected,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300)))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200)))
            },
            label = "floating-split-anim"
        ) { isConnected ->
            if (isConnected) {
                // Split into TWO buttons: Disconnect (pause) and Ping
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FloatingSplitAction(
                        tone = Aether.Danger,
                        description = Tr.now.disconnect,
                        onClick = { actions.onToggleConnection() }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White)
                            )
                        }
                    }

                    FloatingSplitAction(
                        tone = Aether.Emerald,
                        description = Tr.now.testPing,
                        enabled = homePingTappable(evidence),
                        onClick = { actions.onTestPing() }
                    ) {
                        HomeGlyphIcon(HomeGlyph.PULSE, Color.White, Modifier.size(24.dp))
                    }
                }
            } else {
                FloatingConnectFab(evidence = evidence, onToggle = { actions.onToggleConnection() })
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// THEME 3: iOS CENTER ORBITAL THEME (Fixed Screen)
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_CONNECT_CONTROLS_V141 — the Theme 3 control: an orbital power core.
 *
 * A 118 dp dial: four slowly rotating dashes invite the drag-free tap while armed, a single
 * fast arc orbits while the tunnel negotiates, and the ring solidifies with a gentle breath
 * once protected. The flat core disc carries the power glyph and the kinetic press. Distinct
 * silhouette from both the slider (Theme 1) and the floating FAB (Theme 2) by design.
 */
@Composable
internal fun OrbitalConnectControl(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val motion = MarbleMotion.current
    val connected = evidence.connected
    val busy = evidence.connecting || evidence.disconnecting
    val tone by animateColorAsState(
        targetValue = when {
            connected -> Aether.Emerald
            busy -> Aether.CyanBright
            else -> Aether.Cyan
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "orbit-tone"
    )

    Box(modifier = modifier.size(118.dp), contentAlignment = Alignment.Center) {
        // The whole dial is one draw-phase canvas on the shared clock: dashes rotate while
        // armed, an arc orbits while busy, the ring breathes while protected — and none of it
        // ever recomposes the control.
        Canvas(modifier = Modifier.size(118.dp)) {
            val spin = motion.loop(1200)
            val slowSpin = motion.loop(9000)
            val breathe = motion.breathe(2600)
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val ring = Size(size.width - inset * 2f, size.height - inset * 2f)
            // Flat halo wash under everything — one alpha, no gradient stack.
            drawCircle(
                color = tone.copy(alpha = 0.08f + 0.06f * breathe),
                radius = size.minDimension * 0.44f
            )
            when {
                busy -> rotate(degrees = spin * 360f) {
                    drawArc(
                        color = tone,
                        startAngle = -80f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = ring,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                connected -> drawArc(
                    color = tone.copy(alpha = 0.70f + 0.30f * breathe),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = ring,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                else -> rotate(degrees = slowSpin * 360f) {
                    repeat(4) { index ->
                        drawArc(
                            color = tone.copy(alpha = 0.55f),
                            startAngle = index * 90f,
                            sweepAngle = 54f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = ring,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Core disc — flat solid tone, kinetic press.
        Box(
            modifier = Modifier
                .size(86.dp)
                .shadow(5.dp, CircleShape, spotColor = tone)
                .clip(CircleShape)
                .background(tone)
                .kineticClickable(boundedShape = CircleShape) { actions.onToggleConnection() },
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.POWER, Color.White, Modifier.size(40.dp))
        }
    }
}

@Composable
internal fun HomeThemeEmbossed(
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(bottom = bottomClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top actions (outside the banner) + Status Bar
        HomeTopActionBar(evidence, actions, repo)
        IosStatusWideCard(evidence, actions)

        // Center: Orbital power core + caption (fixed height, never resizes with status text)
        Box(
            modifier = Modifier.padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            OrbitalConnectControl(evidence = evidence, actions = actions)
        }
        ConnectButtonCaption(
            evidence = evidence,
            tone = homeStateTone(evidence),
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // Bottom: Server List Box
        IosServerListBox(
            repo = repo,
            evidence = evidence,
            actions = actions,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// THEME 4: iOS MODULAR CUSTOMIZABLE THEME (Fixed Screen)
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_MODULAR_LAYOUT_V145 — Theme 4, the customizer layout, repaired end to end.
 *
 * Bugs this rewrite removes, all of them reachable with nothing but the customizer's own
 * controls:
 *
 *  1. **A module could disappear for good.** The page rendered `modularCardOrder` literally, so
 *     an order string that had lost an entry (older build, partial save, migrated preference)
 *     never drew that module again — including CONNECT, i.e. a Home page with no way to
 *     connect. The order now goes through [ModularLayout.order], which is always a permutation
 *     of the known modules.
 *  2. **A duplicated entry drew the same card twice** (same reason, same fix).
 *  3. **The page could not be reached past the screen edge.** A fixed Column with a 216 dp
 *     connect slot, a status banner, a stats strip and a server card sized up to 360 dp
 *     overflows a small screen; a Column clips instead of scrolling, so the bottom modules —
 *     often the connect button — were simply unreachable. The page scrolls now.
 *  4. **`weight(1f)` inside that Column** fought the user's own card-height choice: the servers
 *     module was stretched by the layout instead of sized by the slider. It is bounded by the
 *     chosen height, exactly as the customizer promises.
 *  5. **Two settings did nothing.** `modularShowShortcuts` and `modularShowSocks` were
 *     persisted, restored and never read by any composable. Both are real modules now.
 *  6. **The floating connect style had no ping companion.** Theme 2 splits into disconnect +
 *     ping once the tunnel is up; the same control inside the customizer stayed alone, so the
 *     one gesture the layout advertises was missing. Every connect silhouette in Theme 4 now
 *     gains the ping action beside it while connected.
 */
@Composable
internal fun HomeThemeModular(
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    var customizeOpen by remember { mutableStateOf(false) }
    val settings = repo.settings
    val cardOrder = ModularLayout.order(settings.modularCardOrder)
    val serverListMaxHeight = settings.modularCardHeightDp
        .coerceIn(MODULAR_CARD_HEIGHT_MIN, MODULAR_CARD_HEIGHT_MAX).dp
    val modularConnect = parseConnectButtonStyle(settings.modularConnectStyle)

    // MARBLE_MODULAR_FLOATING_V151 — "Floating button" in the customizer is Home style 2's own
    // control, and it behaves the way it does there: an overlay pinned to the bottom-end corner
    // that splits into disconnect + ping once the tunnel is up. It cannot be an inline module,
    // because a module scrolls away and the whole point of the silhouette is that it stays under
    // the thumb. The page therefore reserves the same 104 dp of clearance Theme 2 reserves, so no
    // module is ever buried underneath it.
    val floatingOverlay = modularConnect == ConnectButtonStyle.FLOATING

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(bottom = bottomClearance + (if (floatingOverlay) 104.dp else 0.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top actions (outside the banner) + Top Bar with Customize Layout Button
            HomeTopActionBar(evidence, actions, repo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Tr.now.modularStudioTitle,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                // MARBLE_MODULAR_CUSTOMIZER_V151 — the Customize affordance can be hidden. The user
            // asks for it at the moment they are customizing, and the switch that brings it back
            // lives in Settings → General → Home layout, so hiding it can never strand the layout.
                if (!settings.modularHideCustomizerButton) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HomeCloud.Accent.copy(alpha = 0.12f))
                            .border(1.dp, HomeCloud.Accent.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                            .kineticClickable(
                                role = Role.Button,
                                boundedShape = RoundedCornerShape(12.dp)
                            ) { customizeOpen = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeGlyphIcon(HomeGlyph.MORE, HomeCloud.Accent, Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = Tr.now.customizeLayout,
                            color = HomeCloud.Accent,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

        // Render the modules in the (repaired) configured order, honouring every visibility
        // switch the customizer offers. CONNECT is deliberately not hideable: a Home page that
        // cannot open a tunnel is not a layout choice, it is a broken product.
        cardOrder.forEach { cardType ->
            when (cardType) {
                ModularLayout.STATUS -> if (settings.modularShowStatus) {
                    IosStatusWideCard(evidence, actions)
                }
                ModularLayout.SERVERS -> if (settings.modularShowServers) {
                    IosServerListBox(
                        repo = repo,
                        evidence = evidence,
                        actions = actions,
                        maxListHeight = serverListMaxHeight
                    )
                }
                ModularLayout.CONNECT -> if (!floatingOverlay) {
                    ModularConnectModule(
                        evidence = evidence,
                        actions = actions,
                        style = modularConnect
                    )
                }
                ModularLayout.STATS -> if (settings.modularShowStats) {
                    HomeSessionStats(evidence, actions, Aether.Cyan)
                }
                ModularLayout.SHORTCUTS -> if (settings.modularShowShortcuts) {
                    HomeShortcutDeck(evidence, actions, HomeCloud.Accent)
                }
            }
        }

        if (settings.modularShowSocks) {
            ModularSocksCard(repo = repo, evidence = evidence)
        }
        }

        if (floatingOverlay) {
            HomeFloatingSplitControl(
                evidence = evidence,
                actions = actions,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = bottomClearance + 12.dp)
            )
        }
    }

    // Customization Sheet / Dialog
    if (customizeOpen) {
        ModularCustomizerDialog(
            repo = repo,
            onDismiss = { customizeOpen = false }
        )
    }
}

/**
 * The connect module of Theme 4: the chosen silhouette, plus the ping companion the layout has
 * always promised once a tunnel is up.
 *
 * The slot keeps one fixed footprint per silhouette family so switching the shape in the
 * customizer never re-flows the modules above and below it.
 */
@Composable
private fun ModularConnectModule(
    evidence: HomeEvidence,
    actions: HomeActions,
    style: ConnectButtonStyle
) {
    val fullWidth = style == ConnectButtonStyle.SLIDE || style == ConnectButtonStyle.STREAM
    val slotHeight = when (style) {
        ConnectButtonStyle.ROUND -> 216.dp
        ConnectButtonStyle.FLOATING -> 132.dp
        ConnectButtonStyle.CLASSIC -> 116.dp
        ConnectButtonStyle.SLIDE, ConnectButtonStyle.STREAM -> 104.dp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = slotHeight, max = slotHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = if (fullWidth) Modifier.weight(1f) else Modifier,
            contentAlignment = Alignment.Center
        ) {
            MarbleConnectionButton(
                evidence = evidence,
                tone = homeStateTone(evidence),
                onToggle = actions.onToggleConnection,
                flavor = HomeFlavor.IOS_MODULAR,
                style = style,
                modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier
            )
        }
        AnimatedVisibility(
            visible = evidence.connected,
            enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
                scaleIn(MarbleMotionSpecs.ResponseFloat, initialScale = .80f),
            exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
                scaleOut(MarbleMotionSpecs.ExitFloat, targetScale = .80f)
        ) {
            Row {
                Spacer(Modifier.width(12.dp))
                ModularPingAction(
                    enabled = homePingTappable(evidence),
                    onClick = actions.onTestPing
                )
            }
        }
    }
}

/** The circular ping companion that appears beside the connect control once protected. */
@Composable
private fun ModularPingAction(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val description = Tr.now.testPing
    Box(
        modifier = Modifier
            .size(54.dp)
            .shadow(3.dp, CircleShape, spotColor = Aether.Emerald)
            .clip(CircleShape)
            .background(Aether.Emerald)
            .kineticClickable(enabled = enabled, boundedShape = CircleShape, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        HomeGlyphIcon(HomeGlyph.PULSE, Color.White, Modifier.size(24.dp))
    }
}

/**
 * MARBLE_MODULAR_LAYOUT_V145 — the local SOCKS endpoint module.
 *
 * `modularShowSocks` was a persisted preference with no renderer. It exists for the people who
 * point another app at MarbleNG's proxy, so the module states the address, whether it is live,
 * and copies it on tap.
 */
@Composable
private fun ModularSocksCard(repo: AppRepository, evidence: HomeEvidence) {
    val clipboard = LocalClipboardManager.current
    val address = "127.0.0.1:${repo.activeProxyPort()}"
    val copiedMessage = trx("SOCKS proxy address copied")
    val tone = if (evidence.connected) Aether.Emerald else Aether.InkMuted
    HomeCloudCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .kineticClickable(role = Role.Button) {
                    clipboard.setText(AnnotatedString(address))
                    repo.setRuntimeMessage(copiedMessage)
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeGlyphIcon(HomeGlyph.BOLT, tone, Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    Tr.now.socksProxyLabel,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    address,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
            HomeGlyphIcon(HomeGlyph.COPY, HomeCloud.Accent, Modifier.size(15.dp))
        }
    }
}

private const val MODULAR_CARD_HEIGHT_MIN = 160
private const val MODULAR_CARD_HEIGHT_MAX = 360

@Composable
private fun ModularCustomizerDialog(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val s = repo.settings
    // MARBLE_MODULAR_LAYOUT_V145 — the editor starts from a repaired order, so a legacy value
    // cannot present the user with a list that is missing the module they are looking for.
    var order by remember { mutableStateOf(ModularLayout.order(s.modularCardOrder)) }
    var showStatus by remember { mutableStateOf(s.modularShowStatus) }
    var showServers by remember { mutableStateOf(s.modularShowServers) }
    var showStats by remember { mutableStateOf(s.modularShowStats) }
    var showShortcuts by remember { mutableStateOf(s.modularShowShortcuts) }
    var showSocks by remember { mutableStateOf(s.modularShowSocks) }
    var connectStyle by remember { mutableStateOf(s.modularConnectStyle) }
    // MARBLE_MODULAR_CUSTOMIZER_V151 — the hide affordance lives here, at the moment the user is
    // actually customizing, which is when they ask for it.
    var hideCustomize by remember { mutableStateOf(s.modularHideCustomizerButton) }
    var cardSize by remember { mutableStateOf(s.modularCardSize) }
    var cardHeight by remember {
        mutableIntStateOf(s.modularCardHeightDp.coerceIn(MODULAR_CARD_HEIGHT_MIN, MODULAR_CARD_HEIGHT_MAX))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = Aether.VoidElevated,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, HomeCloud.Accent.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    Tr.now.customizeLayout,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    trx("Reorder the widget, pick the connect shape and resize the cards."),
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(trx("Connect button"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectButtonStyle.entries.forEach { style ->
                        val selected = parseConnectButtonStyle(connectStyle) == style
                        val shape = RoundedCornerShape(13.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (selected) HomeCloud.Accent.copy(alpha = 0.12f) else homeCloudInsetFill())
                                .border(1.dp, if (selected) HomeCloud.Accent.copy(alpha = 0.45f) else homeCloudInsetBorder(), shape)
                                .kineticClickable(role = Role.Button, boundedShape = shape) {
                                    connectStyle = style.id
                                }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) HomeCloud.Accent.copy(alpha = 0.18f) else homeCloudInsetFill()),
                                contentAlignment = Alignment.Center
                            ) {
                                HomeGlyphIcon(
                                    when (style) {
                                        ConnectButtonStyle.ROUND -> HomeGlyph.POWER
                                        ConnectButtonStyle.SLIDE -> HomeGlyph.PULSE
                                        ConnectButtonStyle.CLASSIC -> HomeGlyph.CHECK
                                        ConnectButtonStyle.STREAM -> HomeGlyph.BOLT
                                        ConnectButtonStyle.FLOATING -> HomeGlyph.MORE
                                    },
                                    if (selected) HomeCloud.Accent else Aether.InkMuted,
                                    Modifier.size(14.dp)
                                )
                            }
                            Text(
                                trx(connectStyleName(style)),
                                color = if (selected) HomeCloud.Accent else Aether.Ink,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                HomeGlyphIcon(HomeGlyph.CHECK, HomeCloud.Accent, Modifier.size(15.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(color = homeCloudDivider())

                Text(trx("Card size"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ModularCardSize.entries.forEach { size ->
                        // The chip reflects the height that is actually configured: dragging the
                        // slider away from a preset now deselects the chip instead of leaving a
                        // "Comfortable" badge lit next to a 187 dp card.
                        val selected = modularCardHeightFor(size) == cardHeight
                        val shape = RoundedCornerShape(12.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(if (selected) HomeCloud.Accent.copy(alpha = 0.12f) else homeCloudInsetFill())
                                .border(1.dp, if (selected) HomeCloud.Accent.copy(alpha = 0.45f) else homeCloudInsetBorder(), shape)
                                .kineticClickable(role = Role.Button, boundedShape = shape) {
                                    cardSize = size.id
                                    cardHeight = modularCardHeightFor(size)
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                trx(cardSizeName(size)),
                                color = if (selected) HomeCloud.Accent else Aether.Ink,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                trx(cardSizeHint(size)),
                                color = Aether.InkMuted,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = homeCloudDivider())

                Text(
                    trx("Custom height"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Slider(
                        value = cardHeight.toFloat(),
                        onValueChange = {
                            cardHeight = it.toInt().coerceIn(MODULAR_CARD_HEIGHT_MIN, MODULAR_CARD_HEIGHT_MAX)
                        },
                        valueRange = MODULAR_CARD_HEIGHT_MIN.toFloat()..MODULAR_CARD_HEIGHT_MAX.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$cardHeight dp",
                        color = HomeCloud.Accent,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                HorizontalDivider(color = homeCloudDivider())

                Text(trx("Widgets Order"), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                order.forEachIndexed { index, item ->
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(homeCloudInsetFill())
                            .border(1.dp, homeCloudInsetBorder(), shape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeGlyphIcon(HomeGlyph.LIBRARY, HomeCloud.Accent, Modifier.size(14.dp))
                            Text(
                                trx(modularCardName(item)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Aether.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row {
                            if (index > 0) {
                                TextButton(
                                    onClick = {
                                        val next = order.toMutableList()
                                        val temp = next[index]
                                        next[index] = next[index - 1]
                                        next[index - 1] = temp
                                        order = next
                                    }
                                ) { Text(trx("Move up")) }
                            }
                            if (index < order.size - 1) {
                                TextButton(
                                    onClick = {
                                        val next = order.toMutableList()
                                        val temp = next[index]
                                        next[index] = next[index + 1]
                                        next[index + 1] = temp
                                        order = next
                                    }
                                ) { Text(trx("Move down")) }
                            }
                        }
                    }
                }

                HorizontalDivider(color = homeCloudDivider())

                Text(
                    trx("Modules"),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                ModularToggleRow(
                    label = trx("Status banner"),
                    detail = trx("Route, ping and IP"),
                    checked = showStatus
                ) { showStatus = it }
                ModularToggleRow(
                    label = trx("Servers"),
                    detail = trx("The group picker and its server list"),
                    checked = showServers
                ) { showServers = it }
                ModularToggleRow(
                    label = trx("Show traffic stats"),
                    detail = trx("Uptime, ping and session traffic"),
                    checked = showStats
                ) { showStats = it }
                ModularToggleRow(
                    label = trx("Quick shortcuts"),
                    detail = trx("Add, paste, QR and the ping readout"),
                    checked = showShortcuts
                ) { showShortcuts = it }
                ModularToggleRow(
                    label = trx("Local SOCKS address"),
                    detail = trx("Show the proxy endpoint other apps can use"),
                    checked = showSocks
                ) { showSocks = it }
                ModularToggleRow(
                    label = Tr.now.hideCustomizeButton,
                    detail = Tr.now.hideCustomizeButtonHint,
                    checked = hideCustomize
                ) { hideCustomize = it }
                Text(
                    trx("The connect button is always shown."),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A layout editor without a way back to the factory layout is a trap: the
                    // user who reorders everything and hides three modules has to reconstruct
                    // the default from memory.
                    TextButton(
                        onClick = {
                            order = ModularLayout.CANONICAL
                            showStatus = true
                            showServers = true
                            showStats = true
                            showShortcuts = true
                            showSocks = false
                            connectStyle = ConnectButtonStyle.ROUND.id
                            hideCustomize = false
                            cardSize = ModularCardSize.COMPACT.id
                            cardHeight = modularCardHeightFor(ModularCardSize.COMPACT)
                        }
                    ) { Text(trx("Reset layout")) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text(trx("Cancel")) }
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                repo.updateSettings(
                                    repo.settings.copy(
                                        modularCardOrder = ModularLayout.serialize(order),
                                        modularShowStatus = showStatus,
                                        modularShowServers = showServers,
                                        modularShowStats = showStats,
                                        modularShowShortcuts = showShortcuts,
                                        modularShowSocks = showSocks,
                                        modularConnectStyle = connectStyle,
                                        modularHideCustomizerButton = hideCustomize,
                                        modularCardSize = cardSize,
                                        modularCardHeightDp = cardHeight.coerceIn(
                                            MODULAR_CARD_HEIGHT_MIN,
                                            MODULAR_CARD_HEIGHT_MAX
                                        )
                                    )
                                )
                                onDismiss()
                            }
                        ) { Text(trx("Save")) }
                    }
                }
            }
        }
    }
}

/** One module switch of the Theme 4 customizer: what it is, what it shows, on/off. */
@Composable
private fun ModularToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun connectStyleName(style: ConnectButtonStyle): String = when (style) {
    ConnectButtonStyle.ROUND -> "Round"
    ConnectButtonStyle.SLIDE -> "Slide to connect"
    ConnectButtonStyle.CLASSIC -> "Classic switch"
    ConnectButtonStyle.STREAM -> "Stream bar"
    ConnectButtonStyle.FLOATING -> "Floating button"
}

private fun modularCardName(item: String): String = when (item) {
    ModularLayout.STATUS -> "Status"
    ModularLayout.SERVERS -> "Servers"
    ModularLayout.CONNECT -> "Connect"
    ModularLayout.STATS -> "Stats"
    ModularLayout.SHORTCUTS -> "Quick shortcuts"
    else -> item
}

private fun cardSizeName(size: ModularCardSize): String = when (size) {
    ModularCardSize.COMPACT -> "Compact"
    ModularCardSize.COMFORTABLE -> "Comfortable"
    ModularCardSize.SPACIOUS -> "Spacious"
}

private fun cardSizeHint(size: ModularCardSize): String = when (size) {
    ModularCardSize.COMPACT -> "Small"
    ModularCardSize.COMFORTABLE -> "Medium"
    ModularCardSize.SPACIOUS -> "Large"
}

private fun modularCardHeightFor(size: ModularCardSize): Int = when (size) {
    ModularCardSize.COMPACT -> 180
    ModularCardSize.COMFORTABLE -> 240
    ModularCardSize.SPACIOUS -> 320
}

// ---------------------------------------------------------------------------------------------
// SURFACE DISPATCHER
// ---------------------------------------------------------------------------------------------

/** Renders the Home surface in the presentation the user selected in Settings. */
@Composable
internal fun HomeStyleSurface(
    style: HomeStyle,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp,
    onScrollChanged: (Boolean) -> Unit = {},
    repo: AppRepository
) {
    when (style) {
        HomeStyle.IOS_SLIDER -> HomeThemeSlider(
            repo = repo,
            evidence = evidence,
            actions = actions,
            bottomClearance = bottomClearance
        )
        HomeStyle.IOS_FLOATING -> HomeThemeFloating(
            repo = repo,
            evidence = evidence,
            actions = actions,
            bottomClearance = bottomClearance
        )
        HomeStyle.IOS_EMBOSSED -> HomeThemeEmbossed(
            repo = repo,
            evidence = evidence,
            actions = actions,
            bottomClearance = bottomClearance
        )
        HomeStyle.IOS_MODULAR -> HomeThemeModular(
            repo = repo,
            evidence = evidence,
            actions = actions,
            bottomClearance = bottomClearance
        )
    }
}

/** Clipboard helper shared by every style so "copy" behaves identically across presentations. */
@Composable
internal fun rememberCopyIpAction(repo: AppRepository, ip: String): () -> Unit {
    val clipboard = LocalClipboardManager.current
    val copied = Tr.now.ipCopied
    return {
        if (ip.isNotBlank()) {
            clipboard.setText(AnnotatedString(ip))
            repo.setRuntimeMessage(copied)
        }
    }
}
