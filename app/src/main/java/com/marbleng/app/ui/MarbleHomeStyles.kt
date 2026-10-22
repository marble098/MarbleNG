@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.marbleng.app.core.AddressFamilyPolicy
import com.marbleng.app.core.ServersFilter
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.ConnectButtonStyle

import com.marbleng.app.model.HomePingAction
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ModularCardSize
import com.marbleng.app.model.ModularLayout
import com.marbleng.app.model.ProbeState

import com.marbleng.app.model.ServerLayout
import com.marbleng.app.model.serversLayoutEnum
import com.marbleng.app.model.parseConnectButtonStyle
import com.marbleng.app.model.parseHomePingAction
import com.marbleng.app.model.ProxyProfile
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class HomeEvidence(
    val profile: ProxyProfile?,
    val nodeName: String,
    val sourceName: String,
    val ip: String,
    val flag: String,
    val countryCode: String,
    val location: String,
    val ipFamily: String,
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
    val pingProvisional: Boolean = false,
    val selectedPingMs: Int,
    val selectedPingState: ConnectionPingState,
    val selectedPingFailure: String,
    val selectedProvisional: Boolean = false,
    val downBps: Long,
    val upBps: Long,
    val showSpeedWidget: Boolean,

    val injectedResetSuspected: Boolean = false,
    val stabilityClass: String = "",

    val sessionBytes: Long = 0L,
    val lastSessionBytes: Long = 0L,
    val showDataUsage: Boolean = false,

    val flagCode: String = "",
    val routeState: MarbleRouteState = MarbleRouteState.READY,
    val locationTrust: MarbleLocationTrust = MarbleLocationTrust.UNKNOWN,

    val pingGaugeAction: HomePingAction = HomePingAction.ROUTE,
    val pingChipAction: HomePingAction = HomePingAction.GROUP,
    val pingHeaderAction: HomePingAction = HomePingAction.ROUTE
) {
    val mayPaintLocationFlag: Boolean get() = locationTrust.mayDrawFlag()
}

internal fun buildHomeEvidence(
    repo: AppRepository,
    profile: ProxyProfile?,
    displayName: String,
    info: ServerIntelInfo?,
    fallbackFlag: String?
): HomeEvidence {
    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING"
    val disconnecting = repo.state == "DISCONNECTING"
    val blocked = repo.state == "BLOCKED"

    val intelCode = info?.countryCode?.takeIf { it.isNotBlank() }.orEmpty()
    val measuredCode = profile?.let { repo.serverLocation(it).code }.orEmpty()
    val labelCode = leadingFlagCodeOf(profile?.name.orEmpty())
    val locationTrust = marbleLocationTrustOf(
        hasSessionReport = intelCode.isNotBlank(),
        hasMeasuredCode = measuredCode.isNotBlank(),
        measuredIsProvisional = repo.serverLocationIsProvisional(profile),
        hasLabelGlyph = labelCode.isNotBlank()
    )
    val flagCode = if (intelCode.isNotBlank()) intelCode else measuredCode
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
        ipFamily = if (connected) {
            info?.ipType?.takeIf { it.equals("IPv4", true) || it.equals("IPv6", true) }
                ?: profile?.host?.trim()?.removeSurrounding("[", "]")?.let { host ->
                    when {
                        host.contains(':') -> "IPv6"
                        host.matches(Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")) -> "IPv4"
                        else -> null
                    }
                }
                ?: AddressFamilyPolicy.plan(
                    settings = repo.settings,
                    underlayHasIpv6 = repo.networkSnapshot.hasIpv6
                ).let { if (it.prioritizeIpv6) "IPv6" else "IPv4" }
        } else "",
        ipLoading = repo.serverIntelLoading,
        ipError = repo.serverIntelError.isNotBlank() && info == null,
        connected = connected,
        connecting = connecting,
        disconnecting = disconnecting,
        blocked = blocked,
        routeState = marbleRouteStateOf(
            hasRoute = profile != null,
            connected = connected,
            connecting = connecting,
            disconnecting = disconnecting,
            blocked = blocked
        ),
        locationTrust = locationTrust,
        connectedSinceMs = repo.connectedSinceMs,
        pingMs = repo.connectionPingMs,
        pingState = repo.connectionPingState,
        pingFailure = repo.connectionPingFailure,
        pingProvisional = repo.connectionPingProvisional,
        selectedPingMs = repo.selectedPingMs,
        selectedPingState = repo.selectedPingState,
        selectedPingFailure = repo.selectedPingFailure,
        selectedProvisional = repo.selectedPingProvisional,
        downBps = if (connected) repo.liveDownBps else 0L,
        upBps = if (connected) repo.liveUpBps else 0L,
        showSpeedWidget = repo.settings.homeSpeedWidgetEnabled,
        injectedResetSuspected = repo.homePingInjectedReset,
        stabilityClass = repo.homePingStabilityClass,

        sessionBytes = if (repo.state == "CONNECTED") repo.sessionBytes else 0L,
        lastSessionBytes = repo.lastSessionBytes,
        showDataUsage = repo.settings.homeShowDataUsage,
        flagCode = flagCode,

        pingGaugeAction = parseHomePingAction(repo.settings.homePingGaugeAction),
        pingChipAction = parseHomePingAction(repo.settings.homePingChipAction),
        pingHeaderAction = parseHomePingAction(repo.settings.homePingHeaderAction)
    )
}

private fun leadingFlagCodeOf(name: String): String {
    val points = name.trim().codePoints().toArray()
    val base = 0x1F1E6
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (a !in base..base + 25) continue
        if (b !in base..base + 25) continue
        val c1 = ('A' + (a - base)).toChar()
        val c2 = ('A' + (b - base)).toChar()
        return c1.toString() + c2.toString()
    }
    return ""
}

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
    val onPingGroup: () -> Unit = {},
    /**
     * MARBLE_HOME_ROUTE_PING_V210 — measure the ONE server this page is showing.
     *
     * Connected, that is the tunnel carrying traffic; disconnected, it is the server the connect
     * button would use. It is the verb behind both the header's pulse and the split control's
     * second disc, because those two buttons are the same question — "is *this* server alive?" —
     * asked from two places, and two controls that answer it differently are one bug. The group
     * sweep ([onPingGroup]) remains a different question and keeps its own door on the Servers
     * page.
     */
    val onPingRoute: () -> Unit = {},
    val onPingLibrary: () -> Unit = {}
)

internal fun runHomePingAction(action: HomePingAction, actions: HomeActions) {
    when (action) {
        HomePingAction.ROUTE -> actions.onPingRoute()
        HomePingAction.GROUP -> actions.onPingGroup()
        HomePingAction.LIBRARY -> actions.onPingLibrary()
        HomePingAction.TESTS -> actions.onTests()
    }
}

@Composable
internal fun homePingActionLabel(action: HomePingAction): String = when (action) {
    HomePingAction.ROUTE -> trx("Measure this server")
    HomePingAction.GROUP -> trx("Measure this subscription")
    HomePingAction.LIBRARY -> trx("Measure every server")
    HomePingAction.TESTS -> trx("Open ping settings")
}

@Composable
internal fun homePingActionDetail(action: HomePingAction): String = when (action) {
    HomePingAction.ROUTE -> trx("The route on screen, through the tunnel when one is up")
    HomePingAction.GROUP -> trx("Every server of the subscription this route belongs to")
    HomePingAction.LIBRARY -> trx("The whole library, one sweep across every source")
    HomePingAction.TESTS -> trx("Jump to the Tests workspace instead of measuring")
}

internal enum class HomeFlavor { IOS_SLIDER, IOS_FLOATING, IOS_EMBOSSED, IOS_MODULAR, ROUTE_ATELIER }

internal fun homeFlavorFor(style: HomeStyle): HomeFlavor = when (style) {
    HomeStyle.IOS_SLIDER -> HomeFlavor.IOS_SLIDER
    HomeStyle.IOS_FLOATING -> HomeFlavor.IOS_FLOATING
    HomeStyle.IOS_EMBOSSED -> HomeFlavor.IOS_EMBOSSED
    HomeStyle.IOS_MODULAR -> HomeFlavor.IOS_MODULAR
    HomeStyle.ROUTE_ATELIER -> HomeFlavor.ROUTE_ATELIER
}

@Composable
internal fun homeStatusText(evidence: HomeEvidence): String {
    val t = Tr.now
    return when (evidence.routeState) {
        MarbleRouteState.NO_ROUTE -> t.chooseRoute
        MarbleRouteState.READY -> t.readyToConnect
        MarbleRouteState.SECURING -> t.securingRoute
        MarbleRouteState.CONNECTED -> t.statusProtected
        MarbleRouteState.CLOSING -> t.closingRoute
        MarbleRouteState.BLOCKED -> t.connectionStopped
    }
}

@Composable
internal fun homeActionLabel(evidence: HomeEvidence): String {
    val t = Tr.now
    return when (evidence.routeState.connectVerb()) {
        MarbleConnectVerb.ADD_ROUTE -> t.proAddRoute
        MarbleConnectVerb.CONNECT -> t.connect
        MarbleConnectVerb.CANCEL -> t.cancel
        MarbleConnectVerb.DISCONNECT -> t.disconnect
        MarbleConnectVerb.WAIT -> t.disconnecting
        MarbleConnectVerb.RESET -> t.reset
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

internal fun homePingIsProvisional(evidence: HomeEvidence): Boolean =
    if (evidence.connected) evidence.pingProvisional else evidence.selectedProvisional

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

    val provisional = homePingIsProvisional(evidence) && ms >= 1
    return when (state) {
        ConnectionPingState.MEASURING -> if (provisional) "~ $ms ms" else t.pingMeasuringValue
        ConnectionPingState.MEASURED -> if (ms >= 1) "$glyph $ms ms" else "$glyph ✕"
        ConnectionPingState.FAILED -> if (provisional) "~ $ms ms" else "🚫 ${t.pingFailedShort}"
        ConnectionPingState.IDLE -> t.pingIdleValue
    }
}

internal enum class HomePingToneKind { MEASURING, BAND, FAILED, IDLE }

internal fun homePingToneKind(state: ConnectionPingState, ms: Int, provisional: Boolean): HomePingToneKind =
    when {
        state == ConnectionPingState.MEASURED && ms >= 1 -> HomePingToneKind.BAND
        state == ConnectionPingState.MEASURED -> HomePingToneKind.FAILED
        state == ConnectionPingState.FAILED && provisional -> HomePingToneKind.MEASURING
        state == ConnectionPingState.FAILED -> HomePingToneKind.FAILED
        state == ConnectionPingState.MEASURING -> HomePingToneKind.MEASURING
        else -> HomePingToneKind.IDLE
    }

@Composable
internal fun homePingTone(evidence: HomeEvidence, fallback: Color): Color {
    val (ms, state, _) = homeV137PingChannel(evidence)
    val provisional = homePingIsProvisional(evidence) && ms >= 1
    return when (homePingToneKind(state, ms, provisional)) {
        HomePingToneKind.BAND -> marbleMetricTone(pingMetricBand(ms))
        HomePingToneKind.FAILED -> Aether.Danger
        HomePingToneKind.MEASURING -> Aether.Cyan
        HomePingToneKind.IDLE -> fallback
    }
}

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

internal val LocalConnectButtonStyle = staticCompositionLocalOf { ConnectButtonStyle.ROUND }

internal fun connectButtonGlyph(evidence: HomeEvidence): HomeGlyph = when {
    evidence.connected -> HomeGlyph.CHECK
    evidence.blocked -> HomeGlyph.RESET
    evidence.routeState == MarbleRouteState.NO_ROUTE -> HomeGlyph.PLUS
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

@Composable
internal fun connectButtonTone(evidence: HomeEvidence): Color = marbleRouteTone(evidence.routeState)

/**
 * MARBLE_HOME_ROUTE_PING_V210 — true while the ONE route Home is showing is being measured.
 *
 * The two live ping channels are the connected tunnel's own measurement and the selected
 * server's endpoint probe; whichever the page is in, the button that started it has to say so.
 * It is one predicate because two controls (the header pulse and the split control's second
 * disc) ask the same question, and two copies of "is it running?" is two chances to disagree.
 */
internal fun homeRouteMeasuring(evidence: HomeEvidence): Boolean =
    evidence.pingState == ConnectionPingState.MEASURING ||
        evidence.selectedPingState == ConnectionPingState.MEASURING

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

    val armed = evidence.routeState.isActionable()

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

    val label = homeActionLabel(evidence)
    val controlDescription = "${trx(label)} ${trx("connection button")}"

    val tapRipple = remember { Animatable(0f) }
    val rippleScope = rememberCoroutineScope()

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
                .marblePopWhen(evidence.connected, peak = MarbleConnectMotion.statePopPeak)
                // MARBLE_SHADOW_CALM_V220 — the round shutter floats instead of looming: half the
                // lift, half the tint.
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .14f),
                    spotColor = animatedTone.copy(alpha = .20f)
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
                .kineticClickable(
                    enabled = armed,
                    role = Role.Button,
                    pressScale = MarbleControlKind.Primary.pressScale,
                    boundedShape = CircleShape,
                    releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                    onClick = {
                        if (motion.acknowledges(MarbleControlKind.Primary)) {
                            rippleScope.launch {
                                tapRipple.snapTo(0f)
                                tapRipple.animateTo(1f, MarbleMotionSpecs.ResponseFloat)
                            }
                        }
                        onToggle()
                    }
                )
                .semantics { contentDescription = controlDescription },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize().padding(10.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)

                val sweep = if (busy) motion.loop(1_150) * 360f else 0f

                val busyPulse = if (busy) motion.breathe(1_150) else 0f

                val haloPulse = if (evidence.connected && motion.acknowledges(MarbleControlKind.Primary)) {
                    motion.breathe(2_800)
                } else {
                    0f
                }

                drawCircle(
                    color = animatedTone.copy(alpha = .22f),
                    radius = r * .94f,
                    center = c,
                    style = Stroke(width = 1.6.dp.toPx())
                )

                drawCircle(
                    color = animatedTone.copy(alpha = .10f),
                    radius = r * .70f,
                    center = c
                )
                when {
                    busy -> {
                        drawArc(
                            color = animatedTone.copy(alpha = .85f + .15f * busyPulse),
                            startAngle = -90f + sweep,

                            sweepAngle = ExpressiveMath.arcSweep(busyPulse, 74f, 136f),
                            useCenter = false,
                            topLeft = Offset(c.x - r * .80f, c.y - r * .80f),
                            size = Size(r * 1.60f, r * 1.60f),
                            style = Stroke(
                                width = (4.2f + 1.6f * busyPulse).dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        drawCircle(
                            color = animatedTone.copy(alpha = .18f),
                            radius = r * .80f,
                            center = c,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    evidence.connected -> {
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
                MarbleExpressiveGlyphSwap(
                    key = connectButtonGlyph(evidence),
                    modifier = Modifier.size(diameter * iconFraction)
                ) {
                    HomeGlyphIcon(
                        connectButtonGlyph(evidence),
                        animatedTone,
                        Modifier.size(diameter * iconFraction)
                    )
                }
                if (evidence.connected) {
                    Spacer(Modifier.height(4.dp))

                    val settled = evidence.pingMs >= 20
                    val measuring = evidence.pingState == ConnectionPingState.MEASURING
                    val provisional = settled && evidence.pingProvisional &&
                        (measuring || evidence.pingState == ConnectionPingState.FAILED)
                    val pingLabel = when {
                        provisional -> "~ ${evidence.pingMs} ms"
                        evidence.pingState == ConnectionPingState.MEASURED && settled -> "${evidence.pingMs} ms"
                        measuring -> "•••"
                        evidence.pingState == ConnectionPingState.FAILED -> "✕"
                        else -> "—"
                    }
                    val pingTone = when {
                        provisional -> marbleMetricTone(pingMetricBand(evidence.pingMs))
                        evidence.pingState == ConnectionPingState.MEASURED && settled ->
                            marbleMetricTone(pingMetricBand(evidence.pingMs))
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

                                fontSize = 11.5.sp
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
    val trackHeight = MarbleSlideSpec.TrackHeight
    val knobSize = MarbleSlideSpec.ThumbSize
    val padding = MarbleSlideSpec.Inset
    val travelDp = width - knobSize - padding * 2
    val travelPx = with(density) { travelDp.toPx() }.coerceAtLeast(1f)
    val shape = marbleSlideShape(trackHeight)
    val busy = evidence.connecting || evidence.disconnecting
    val label = homeActionLabel(evidence)
    val controlDescription = "${trx(label)} ${trx("slider")}"

    val threshold = .78f

    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }
    val progress = (knob.value / travelPx).coerceIn(0f, 1f)

    var dragging by remember { mutableStateOf(false) }
    val thresholdReached = progress >= threshold

    // MARBLE_SLIDE_PARK_V146 — the knob parks on the side it was dragged to. While a route is

    val restAtEnd = evidence.connected || evidence.connecting
    val animates = motion.acknowledges(MarbleControlKind.Primary)
    LaunchedEffect(restAtEnd, travelPx, animates) {
        if (!dragging) {
            val target = if (restAtEnd) travelPx else 0f
            if (animates) knob.animateTo(target, MarbleMotionSpecs.QuickReveal) else knob.snapTo(target)
        }
    }

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
                    .marbleSlideTrack(
                        shape = shape,
                        tone = animatedTone,
                        progress = progress,
                        busy = busy,
                        engaged = evidence.connected || evidence.connecting
                    )
                    .semantics {
                        contentDescription = controlDescription
                        onClick(label = controlDescription) {
                            if (armed) onToggle()
                            true
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                MarbleSlideEnergyBed(
                    tone = animatedTone,
                    progress = progress,
                    busy = busy,
                    modifier = Modifier.matchParentSize()
                )
                MarbleSlideGuideMarks(
                    tone = animatedTone,
                    fade = if (evidence.connected) progress else 1f - progress,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .size(width = 58.dp, height = 22.dp)
                )
                Text(
                    label,
                    color = marbleSlideLabelTone(animatedTone, evidence.connected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .2.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = knobSize + padding, end = 52.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = padding)
                        .offset { IntOffset(knob.value.toInt(), 0) }
                        .then(Modifier.marbleSlideThumb(animatedTone, knobSize, dragging))
                        .pointerInput(armed, evidence.connected, travelPx) {
                            if (!armed) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = {
                                    dragging = false
                                    // MARBLE_SLIDE_PARK_V146 — the commit direction follows the

                                    val wasConnected = evidence.connected
                                    val completed = if (wasConnected) {
                                        knob.value <= travelPx * (1f - threshold)
                                    } else {
                                        knob.value >= travelPx * threshold
                                    }
                                    if (completed) {
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                        onToggle()
                                        scope.launch {
                                            val target = if (wasConnected) 0f else travelPx
                                            if (animates) {
                                                knob.animateTo(target, MarbleMotionSpecs.QuickReveal)
                                            } else {
                                                knob.snapTo(target)
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            val rest = if (wasConnected) travelPx else 0f
                                            if (animates) {
                                                knob.animateTo(
                                                    rest,
                                                    MarbleExpressiveSpecs.WaveSpringFloat
                                                )
                                            } else {
                                                knob.snapTo(rest)
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch {
                                        val rest = if (evidence.connected) travelPx else 0f
                                        if (animates) {
                                            knob.animateTo(
                                                rest,
                                                MarbleExpressiveSpecs.WaveSpringFloat
                                            )
                                        } else {
                                            knob.snapTo(rest)
                                        }
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
                    MarbleExpressiveGlyphSwap(
                        key = connectButtonGlyph(evidence),
                        modifier = Modifier.size(knobSize * .42f)
                    ) {
                        HomeGlyphIcon(
                            connectButtonGlyph(evidence),
                            prismOnColor(animatedTone),
                            Modifier.size(knobSize * .42f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Text(
                if (evidence.connected) trx("Slide left to disconnect") else trx("Slide right to connect"),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = .2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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

    val shape = RoundedCornerShape(14.dp)
    val label = homeActionLabel(evidence)
    val controlDescription = "${trx(label)} ${trx("connection button")}"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .width(width)
                .height(84.dp)
                // MARBLE_SHADOW_CALM_V220 — the slide rail sits on the page instead of hovering
                // over it.
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .12f),
                    spotColor = animatedTone.copy(alpha = .18f)
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
                .semantics { contentDescription = controlDescription }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)

                    val sweep = if (busy) motion.loop(1_150) * 360f else 0f
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

            if (evidence.connected) {
                val settled = evidence.pingMs >= 20
                val measuring = evidence.pingState == ConnectionPingState.MEASURING
                val provisional = settled && evidence.pingProvisional &&
                    (measuring || evidence.pingState == ConnectionPingState.FAILED)
                val pingLabel = when {
                    provisional -> "~ ${evidence.pingMs} ms"
                    evidence.pingState == ConnectionPingState.MEASURED && settled -> "${evidence.pingMs} ms"
                    measuring -> "•••"
                    evidence.pingState == ConnectionPingState.FAILED -> "✕"
                    else -> "—"
                }
                val pingTone = when {
                    provisional -> marbleMetricTone(pingMetricBand(evidence.pingMs))
                    evidence.pingState == ConnectionPingState.MEASURED && settled ->
                        marbleMetricTone(pingMetricBand(evidence.pingMs))
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

internal enum class HomeGlyph {
    POWER, CHECK, RESET, COPY, REFRESH, MORE, PULSE, CLOCK, LIBRARY, PLUS, BOLT, PASTE, QR, INFO,
    DOWNLOAD, UPLOAD,
    DATA,

    STOP
}

@Composable
internal fun HomeGlyphIcon(glyph: HomeGlyph, color: Color, modifier: Modifier = Modifier) {
    // MARBLE_AURORA_SURFACE_V219 — the connect control's glyphs moved onto the product's one grid.
    //
    // This used to be an eighteen-branch hand-drawn set whose stroke was computed per glyph and
    // which shared no proportions with the Settings icons. It is now a mapping: same grid, same
    // 1.75-unit weight, same round joins, and the thumb's power glyph is drawn by the same code as
    // the power glyph in a Settings row.
    MarbleGlyphIcon(glyph.marbleGlyph, color, modifier)
}

/** MARBLE_AURORA_SURFACE_V219 — `HomeGlyph` on the shared grid. */
internal val HomeGlyph.marbleGlyph: MarbleGlyph
    get() = when (this) {
        HomeGlyph.POWER -> MarbleGlyph.POWER
        HomeGlyph.CHECK -> MarbleGlyph.CHECK
        HomeGlyph.RESET -> MarbleGlyph.REFRESH
        HomeGlyph.COPY -> MarbleGlyph.FOLDER
        HomeGlyph.REFRESH -> MarbleGlyph.REFRESH
        HomeGlyph.MORE -> MarbleGlyph.MORE
        HomeGlyph.PULSE -> MarbleGlyph.RADAR
        HomeGlyph.CLOCK -> MarbleGlyph.REFRESH
        HomeGlyph.LIBRARY -> MarbleGlyph.VAULT
        HomeGlyph.PLUS -> MarbleGlyph.PLUS
        HomeGlyph.BOLT -> MarbleGlyph.SPARK
        HomeGlyph.PASTE -> MarbleGlyph.FOLDER
        HomeGlyph.QR -> MarbleGlyph.QR
        HomeGlyph.INFO -> MarbleGlyph.INFO
        HomeGlyph.DOWNLOAD -> MarbleGlyph.DOWNLOAD
        HomeGlyph.UPLOAD -> MarbleGlyph.UPLOAD
        HomeGlyph.DATA -> MarbleGlyph.GAUGE
        HomeGlyph.STOP -> MarbleGlyph.STOP
    }

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

    val gaugeSpoken = homePingActionLabel(evidence.pingGaugeAction)
    val pingWord = Tr.now.connectionPing

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

            val statShape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .clip(statShape)
                    .semantics {
                        contentDescription = "$pingWord $ping — $gaugeSpoken"
                    }
                    .kineticClickable(
                        enabled = homePingTappable(evidence),
                        role = Role.Button,
                        pressScale = .95f,
                        boundedShape = statShape,
                        releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                        onClick = { runHomePingAction(evidence.pingGaugeAction, actions) }
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(Tr.now.connectionPing, color = Aether.InkMuted, style = MaterialTheme.typography.labelSmall)
                HomeStatValueText(ping, pingTone, sizeScale = 1.1f)
            }

            if (evidence.showDataUsage) {
                val dataBytes = if (evidence.connected) evidence.sessionBytes else evidence.lastSessionBytes
                Box(Modifier.width(1.dp).height(24.dp).background(homeCloudDivider()))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(trx("Data"), color = Aether.InkMuted, style = MaterialTheme.typography.labelSmall)
                    HomeStatValueText(homeCompactBytes(dataBytes), Aether.CyanBright, sizeScale = 1.1f)
                }
            }
        }
    }
}

private fun homeCompactBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "${bytes} B"
}

/**
 * The iOS-styled Wide Status Bar — one shared card for all four Home presentations.
 *
 * It carries one stable status line (state, animated dot and uptime), one route line (flag, node,
 * compact endpoint/protocol, a tappable ping readout and copy action), and an optional two-cell
 * download/upload grid. The route identity opens the full IP report. Keeping the telemetry optional
 * preserves the quieter default while placing live rates inside the same card when requested.
 *
 * MARBLE_PING_USER_TAPPED_ONLY_V143 — this card never arms a measurement. The ping it prints was
 * taken when the user asked for one, from the Home ping action or the pulse page.
 *
 * MARBLE_HOME_STATUS_REFRAME_V187 — the old status slab repeated source/protocol/IP in crowded
 * strips. This hierarchy prioritizes connection state and selected route; a short emphasized
 * expansion reveals live transfer rates only when connected and the existing display preference
 * is on. The first theme's center lane absorbs that growth while its slide control stays on the
 * page floor.
 */
@Composable
internal fun IosStatusWideCard(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val stateColor by animateColorAsState(
        targetValue = homeStateTone(evidence),
        animationSpec = MarbleMotionSpecs.Color,
        label = "status-color"
    )
    val shape = RoundedCornerShape(22.dp)
    val pingValue = compactHomePingValue(evidence)
    val pingSpoken = homePingLabel(evidence)
    val pingTone = homePingTone(evidence, Aether.Cyan)
    val routeMeta = listOfNotNull(
        evidence.ip.takeIf { it.isNotBlank() },
        evidence.profile?.scheme?.uppercase()?.takeIf { it.isNotBlank() }
    ).joinToString("  ·  ").ifBlank {
        evidence.location.takeIf { it.isNotBlank() }
            ?: evidence.countryCode.takeIf { it.isNotBlank() }
            ?: trx("Route details")
    }

    // MARBLE_HOME_STATUS_REFRAME_V187 — one clear connection line and one route line. The old

    HomeCloudCard(modifier = modifier.fillMaxWidth(), shape = shape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(stateColor.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center
                ) {
                    StatusDot(stateColor = stateColor, busy = evidence.connecting, size = 11.dp)
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = homeStatusText(evidence),
                    transitionSpec = {
                        (
                            fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat) +
                                slideInVertically(MarbleExpressiveSpecs.RollInSpatial) { it / 2 } +
                                scaleIn(
                                    tween(
                                        durationMillis = MarbleExpressiveMotion.Medium2,
                                        easing = MarbleExpressiveMotion.EmphasizedDecelerate
                                    ),
                                    initialScale = .94f
                                )
                            ) togetherWith (
                            fadeOut(
                                tween(
                                    durationMillis = MarbleExpressiveMotion.Short4,
                                    easing = MarbleExpressiveMotion.EmphasizedAccelerate
                                )
                            ) + slideOutVertically(MarbleExpressiveSpecs.RollOutSpatial) { -it / 2 }
                            )
                    },
                    label = "home-status-word-roll"
                ) { stateWord ->
                    Text(
                        text = stateWord,
                        color = stateColor,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedVisibility(
                    visible = evidence.connected && evidence.ipFamily.isNotBlank(),
                    enter = fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat),
                    exit = fadeOut(tween(MarbleExpressiveMotion.Short4))
                ) {
                    Text(
                        text = evidence.ipFamily.uppercase(),
                        color = if (evidence.ipFamily.equals("IPv6", true)) Aether.CyanBright else Aether.AmethystBright,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = .7.sp
                        ),
                        modifier = Modifier
                            .padding(start = 7.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                (if (evidence.ipFamily.equals("IPv6", true)) Aether.CyanBright else Aether.AmethystBright)
                                    .copy(alpha = .10f)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))

                AnimatedVisibility(
                    visible = evidence.connected,
                    enter = fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat),
                    exit = fadeOut(
                        tween(
                            durationMillis = MarbleExpressiveMotion.Short4,
                            easing = MarbleExpressiveMotion.EmphasizedAccelerate
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(stateColor.copy(alpha = .10f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HomeGlyphIcon(HomeGlyph.CLOCK, stateColor, Modifier.size(12.dp))
                        Text(
                            text = if (evidence.connected) rememberUptimeLabel(evidence.connectedSinceMs) else "",
                            color = stateColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !evidence.connected &&
                        !evidence.connecting &&
                        evidence.showDataUsage &&
                        evidence.lastSessionBytes > 0L,
                    enter = fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat),
                    exit = fadeOut(
                        tween(
                            durationMillis = MarbleExpressiveMotion.Short4,
                            easing = MarbleExpressiveMotion.EmphasizedAccelerate
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Aether.CyanBright.copy(alpha = .08f))
                            .border(1.dp, Aether.CyanBright.copy(alpha = .18f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        HomeGlyphIcon(HomeGlyph.DATA, Aether.CyanBright, Modifier.size(10.dp))
                        Text(
                            text = homeCompactBytes(evidence.lastSessionBytes),
                            color = Aether.CyanBright,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = trx("last session"),
                            color = Aether.CyanBright.copy(alpha = .72f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            maxLines = 1
                        )
                    }
                }
            }

            HorizontalDivider(color = homeCloudDivider().copy(alpha = .72f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { actions.onIpDetails() }
                        .semantics { contentDescription = t.ipDetails },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    CountryFlagCircle(
                        code = evidence.flagCode.ifBlank { null }?.takeIf { evidence.mayPaintLocationFlag },
                        size = 34.dp,
                        fallbackText = "🌐",
                        fallbackFill = homeCloudInsetFill(),
                        fallbackTone = Aether.Ink,
                        ring = marbleNativeOutline()
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = evidence.nodeName.ifBlank { t.chooseRoute },
                            color = Aether.Ink,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = routeMeta,
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .semantics { contentDescription = pingSpoken }
                        .padding(start = 2.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(pingTone.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        HomeGlyphIcon(HomeGlyph.PULSE, pingTone, Modifier.size(14.dp))
                    }

                    if (pingValue != "—") {
                        MarbleExpressiveValueText(
                            value = pingValue,
                            color = pingTone,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(homeCloudInsetFill())
                        .kineticClickable(
                            role = Role.Button,
                            boundedShape = CircleShape,
                            pressScale = .9f,
                            releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat
                        ) { actions.onCopyIp() }
                        .semantics { contentDescription = t.copyIp },
                    contentAlignment = Alignment.Center
                ) {
                    HomeGlyphIcon(HomeGlyph.COPY, Aether.InkMuted, Modifier.size(14.dp))
                }
            }

            AnimatedVisibility(
                visible = evidence.connected && (evidence.showSpeedWidget || evidence.showDataUsage),
                enter = expandVertically(
                    animationSpec = tween(
                        durationMillis = MarbleExpressiveMotion.Medium2,
                        easing = MarbleExpressiveMotion.EmphasizedDecelerate
                    )
                ) + fadeIn(MarbleExpressiveSpecs.EntranceFadeFloat),
                exit = shrinkVertically(
                    animationSpec = tween(
                        durationMillis = MarbleExpressiveMotion.Short4,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    )
                ) + fadeOut(
                    tween(
                        durationMillis = MarbleExpressiveMotion.Short4,
                        easing = MarbleExpressiveMotion.EmphasizedAccelerate
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (evidence.showSpeedWidget) {
                        HomeConnectionMetric(
                            glyph = HomeGlyph.DOWNLOAD,
                            label = t.download,
                            value = homeCompactRate(evidence.downBps),
                            tone = Aether.CyanBright,
                            modifier = Modifier.weight(1f)
                        )
                        if (evidence.showDataUsage) {
                            Box(Modifier.width(1.dp).height(42.dp).background(homeCloudDivider()))
                        }
                        HomeConnectionMetric(
                            glyph = HomeGlyph.UPLOAD,
                            label = t.upload,
                            value = homeCompactRate(evidence.upBps),
                            tone = Aether.AmethystBright,
                            modifier = Modifier.weight(1f)
                        )
                        if (evidence.showDataUsage) {
                            Box(Modifier.width(1.dp).height(42.dp).background(homeCloudDivider()))
                        }
                    }
                    if (evidence.showDataUsage) {
                        HomeConnectionMetric(
                            glyph = HomeGlyph.DATA,
                            label = trx("Data"),
                            value = homeCompactBytes(evidence.sessionBytes),
                            tone = Aether.Emerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeConnectionMetric(
    glyph: HomeGlyph,
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(glyph, tone, Modifier.size(14.dp))
        }
        Text(
            label,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        MarbleExpressiveValueText(
            value = value,
            color = tone,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum"
            ),
            maxLines = 1
        )
    }
}

private fun homeCompactRate(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L ->
        String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L ->
        String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
    else -> "${bytesPerSecond} B/s"
}

private fun compactHomePingValue(evidence: HomeEvidence): String {
    val (milliseconds, state, _) = homeV137PingChannel(evidence)
    return when (state) {
        ConnectionPingState.MEASURING -> "•••"
        ConnectionPingState.MEASURED -> if (milliseconds > 0) "${milliseconds}ms" else "—"
        ConnectionPingState.FAILED -> "×"
        ConnectionPingState.IDLE -> "—"
    }
}

@Composable
internal fun MarbleWordmark(modifier: Modifier = Modifier) {
    val motion = MarbleMotion.current
    val phase = if (motion.motionEnabled) motion.coarseBreathe(11_200) else .5f
    val ice = Aether.CyanBright
    val cyan = Aether.Cyan
    val amethyst = Aether.AmethystBright
    val emerald = Aether.Emerald
    val liveA = lerp(cyan, emerald, phase)
    val liveB = lerp(amethyst, ice, phase)
    val ramp = Brush.linearGradient(
        0.00f to ice,
        0.36f to liveA,
        0.70f to liveB,
        1.00f to emerald
    )

    Text(
        text = "MarbleNG",
        style = MaterialTheme.typography.headlineSmall.copy(
            brush = ramp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * MARBLE_HOME_ONE_PING_V208 — the Home header's one ping control.
 * MARBLE_HOME_ROUTE_PING_V210 — and it measures the server on screen, not the subscription.
 *
 * What it replaced, and the two findings behind the replacement:
 *
 *  1. **"The ping button should not show ping."** The control was a pill that printed the
 *     route's latency — `♥ 132 ms` — next to the wordmark. That made the header a second
 *     read-out of a number the page already shows twice (the status card's latency and the
 *     quality card's Latency cell), and it made the *verb* ambiguous: a pill with a number on it
 *     reads as a measurement, not as a button. It is now an icon and nothing else, and its one
 *     meaning is stated in its accessibility label.
 *  2. **"The box goes away and comes back."** The pill's *content* changed when a sweep started:
 *     `pulse + number + "ms"` became `stop + "Cancel"`, so its measured width changed, the
 *     header row re-laid out, and the pill visibly jumped and re-settled. A control that changes
 *     size when you press it reads as a different control appearing. This one is a constant
 *     [HomePingButtonSize] circle in every state: only the glyph and its hue change, so nothing
 *     in the header moves when a measurement starts or ends.
 *
 * What V210 changes: the *scope*. V208 made this button measure the whole subscription, on the
 * reasoning that the page's question is "how good is my group right now". The report that
 * reopened it says otherwise: a button sitting on the status box of a page that is showing one
 * named server, pressed by someone whose tunnel just went slow, is being asked about *that*
 * server. So the verb is [HomeActions.onPingRoute] — the connected route while a tunnel is up,
 * the selected route while it is down — and the group sweep keeps its own door on the Servers
 * page. The label names the server, so the button never promises a scope it does not measure.
 *
 * Two states are not the same promise, so they are not drawn the same way:
 *  - a group sweep in flight is cancellable, and this button is where Home cancels it (V156);
 *  - a single-route measurement in flight is *not* cancellable — there is nothing to interrupt —
 *    so the button shows the spinner and stops accepting taps instead of offering a STOP square
 *    that would do nothing.
 */
@Composable
private fun HomeRoutePingButton(
    sweeping: Boolean,
    measuring: Boolean,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val chrome = rememberMarbleFloatChrome()

    val tone = if (sweeping) Aether.Danger else chrome.actions.measure
    val shape = CircleShape
    Box(
        modifier = Modifier
            .marbleTapTarget()
            .size(HomePingButtonSize)
            .clip(shape)
            .background(tone.copy(alpha = if (sweeping) .16f else .10f))
            .border(1.dp, tone.copy(alpha = if (sweeping) .44f else .24f), shape)
            .kineticClickable(
                enabled = enabled,
                role = Role.Button,
                pressScale = .92f,
                boundedShape = shape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        when {
            sweeping -> HomeGlyphIcon(HomeGlyph.STOP, tone, Modifier.size(12.dp))

            measuring -> MarbleExpressiveCircularIndicator(
                modifier = Modifier.size(15.dp),
                color = tone,
                strokeWidth = 1.7.dp,
                arcCount = 2
            )
            else -> HomeGlyphIcon(HomeGlyph.PULSE, tone, Modifier.size(16.dp))
        }
    }
}

private val HomePingButtonSize = 38.dp

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
 * currently shown on the page belongs to.
 * MARBLE_HOME_ROUTE_PING_V210 — which is no longer what it does. The pulse now measures the ROUTE
 * itself: the connected server while a tunnel is up, the selected one while it is down, named on
 * the button's own label. The group sweep keeps its door on the Servers page, and the per-route
 * reading the status banner prints is still a display, not a second verb.
 */
@Composable
internal fun HomeTopActionBar(
    evidence: HomeEvidence,
    actions: HomeActions,
    repo: AppRepository,
    modifier: Modifier = Modifier
) {
    var addMenuOpen by remember { mutableStateOf(false) }
    val groupBusy = repo.homeGroupPingRunning
    // MARBLE_HOME_ROUTE_PING_V210 — the header pulse answers the question the box underneath it

    val routeLabel = evidence.nodeName.ifBlank { Tr.now.chooseRoute }
    val routeMeasuring = homeRouteMeasuring(evidence)

    // MARBLE_HOME_TOPBAR_NO_STATUS_V156 — the status dot and its CONNECTED / READY word next to

    val cancelling = repo.probeCancelling
    val sweeping = groupBusy || cancelling

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // MARBLE_AURORA_SURFACE_V219 — the Home header carries the mark itself, not only its
            // name: a 34 dp prism beside the gradient wordmark, so the first screen of the product
            // has an object at its leading edge instead of a line of text floating on the backdrop.
            MarbleBrandMark(markSize = 34.dp)
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

        // MARBLE_HOME_ROUTE_PING_V210 — the verb is the ROUTE, not the group: the server named on

        val headerAction = evidence.pingHeaderAction
        val headerVerb = homePingActionLabel(headerAction)
        HomeRoutePingButton(
            sweeping = sweeping,
            measuring = routeMeasuring,
            description = when {
                sweeping -> trx("Cancel measuring")
                routeMeasuring -> "${trx("Measuring")} • $headerVerb"
                else -> "$headerVerb • $routeLabel"
            },
            enabled = !cancelling && !routeMeasuring,
            onClick = {
                when {
                    cancelling -> Unit
                    groupBusy -> repo.cancelProbes()
                    else -> runHomePingAction(headerAction, actions)
                }
            }
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
}

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

@Composable
private fun HomeBareAction(
    glyph: HomeGlyph,
    tone: Color,
    description: String,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val pageTone = marbleReadableOn(tone, Aether.Void, 3.0f)

    Box(
        modifier = Modifier
            .marbleTapTarget(MarbleTapTarget.Floor)
            .clip(CircleShape)
            .kineticClickable(
                enabled = enabled && !busy,
                role = Role.Button,
                pressScale = .92f,
                boundedShape = CircleShape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                MarbleExpressiveCircularIndicator(
                    modifier = Modifier.size(17.dp),
                    color = pageTone,
                    strokeWidth = 2.dp,
                    arcCount = 3
                )
            } else {
                HomeGlyphIcon(
                    glyph,
                    if (enabled) pageTone else pageTone.copy(alpha = .40f),
                    Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun homeStateTone(evidence: HomeEvidence): Color = marbleRouteTone(evidence.routeState)

@Composable
private fun StatusDot(stateColor: Color, busy: Boolean, size: Dp = 18.dp) {
    val motion = MarbleMotion.current

    val ambient = LocalMarbleAmbientField.current
    Canvas(modifier = Modifier.size(size)) {
        val diameter = size.toPx()

        val breathe = motion.breathe(900)
        val haloAlpha = if (busy) 0.22f + 0.20f * breathe else 0.16f
        if (!busy && motion.motionEnabled && ambient) {
            val phase = motion.loop(2_600)
            drawCircle(
                color = stateColor.copy(alpha = 0.30f * (1f - phase)),
                radius = diameter * (0.28f + 0.22f * phase)
            )
            val offsetPhase = ExpressiveMath.wrap01(phase + 0.5f)
            drawCircle(
                color = stateColor.copy(alpha = 0.18f * (1f - offsetPhase)),
                radius = diameter * (0.28f + 0.22f * offsetPhase)
            )
        }
        drawCircle(color = stateColor.copy(alpha = haloAlpha), radius = diameter * 0.5f)
        drawCircle(
            color = stateColor.copy(alpha = if (busy) 0.75f + 0.25f * breathe else 1f),
            radius = diameter * 0.28f
        )
    }
}

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

    val entranceArmed = rememberMarbleEntranceWindow()
    val activeSubId = repo.librarySourceFilter
    val allSubs = repo.subscriptions
    val activeSubName = when {
        activeSubId.isBlank() || activeSubId == "all" -> t.homeAllServers
        activeSubId == "manual" -> t.homeManualGroup
        else -> allSubs.firstOrNull { it.id == activeSubId }?.name ?: t.homeAllServers
    }

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
            var groupDropdownOpen by remember { mutableStateOf(false) }
            // MARBLE_HOME_ROW_POLISH_V220 — the subscription switcher is a real control now: a
            // kinetic press, a 48 dp floor and a rim that lights while its menu is open, instead of
            // a bare clickable that gave no answer to the finger.
            val switcherShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .heightIn(min = 48.dp)
                    .clip(switcherShape)
                    .background(homeCloudInsetFill())
                    .border(
                        1.dp,
                        if (groupDropdownOpen) homeCloudSelectedBorder() else homeCloudInsetBorder(),
                        switcherShape
                    )
                    .kineticClickable(
                        role = Role.Button,
                        boundedShape = switcherShape,
                        onClick = { groupDropdownOpen = true }
                    )
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
            } else if (settings.serversLayoutEnum == ServerLayout.GRID) {
                val tileColumns = rememberServerTileColumns()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .then(listHeightCap),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = bottomOverlayClearance)
                ) {
                    itemsIndexed(
                        items = ServerTilePolicy.chunkRows(visibleServers, tileColumns),
                        key = { rowIndex, row -> "tiles:$rowIndex:${row.first().id}" }
                    ) { rowIndex, row ->
                        Box(
                            Modifier
                                .animateItem()
                                .marbleStaggerIn(rowIndex + 1, enabled = entranceArmed() && rowIndex < 6)
                        ) {
                            ServerTileRow(profiles = row, columns = tileColumns) { server ->
                                val location = repo.serverLocation(server)

                                ServerTile(
                                    profile = server,
                                    result = benchmarks[server.id],
                                    selected = repo.isSelectedProfile(server),
                                    active = repo.isActiveProfile(server),
                                    testing = repo.probeStateOf(server.id) == ProbeState.TESTING,
                                    locationCode = location.code,
                                    locationProvisional = repo.serverLocationIsProvisional(server),
                                    onClick = {
                                        if (repo.probeActive || repo.probeCancelling) {
                                            repo.setRuntimeMessage(
                                                "Wait until ping finishes before changing server"
                                            )
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
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .then(listHeightCap),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = bottomOverlayClearance)
                ) {
                    itemsIndexed(visibleServers, key = { _, server -> server.id }) { rowIndex, server ->

                        val isSelected = repo.isSelectedProfile(server)
                        val isConnected = repo.isActiveProfile(server)

                        Box(
                            Modifier
                                .animateItem()
                                .marbleStaggerIn(
                                    rowIndex + 1,
                                    enabled = entranceArmed() && rowIndex < 8
                                )
                        ) {
                            val location = repo.serverLocation(server)
                            IosServerItemRow(
                                server = server,
                                result = benchmarks[server.id],
                                isSelected = isSelected,
                                isConnected = isConnected,
                                testing = repo.probeStateOf(server.id) == ProbeState.TESTING,
                                countryCode = location.code,
                                countryName = location.name,
                                provisionalLocation = repo.serverLocationIsProvisional(server),
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

@Composable
internal fun HomeGroupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    tone: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // MARBLE_SHADOW_CALM_V220 — the subscription switcher floats on a quiet 8 dp shadow, not the
    // 18 dp slab that used to darken the whole server box every time a sub was changed.
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 240.dp, max = 320.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = Aether.VoidElevated,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
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

@Composable
private fun IosServerItemRow(
    server: ProxyProfile,
    result: BenchmarkResult?,
    isSelected: Boolean,
    isConnected: Boolean,
    testing: Boolean,
    countryCode: String = "",
    countryName: String = "",
    provisionalLocation: Boolean = false,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(16.dp)

    val itemBg by animateColorAsState(
        targetValue = when {
            isConnected -> Aether.Emerald
                .copy(alpha = if (homeCloudDark()) .12f else .09f)
                .compositeOver(homeCloudInsetFill())
            isSelected -> homeCloudSelectedFill()
            else -> homeCloudInsetFill()
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "srv-row-bg"
    )
    val itemBorder by animateColorAsState(
        targetValue = when {
            isConnected -> Aether.Emerald.copy(alpha = .42f)
            isSelected -> homeCloudSelectedBorder()
            else -> homeCloudInsetBorder()
        },
        animationSpec = MarbleMotionSpecs.Color,
        label = "srv-row-border"
    )
    val flag = leadingFlagGlyph(server.name)
    val measured = result?.takeIf { it.success > 0 && it.latencyMs >= 20 }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val attempted = result != null && measured == null
    val endpoint = buildString {
        append(server.host.trim().removeSurrounding("[", "]"))
        if (server.port > 0) append(":${server.port}")
    }.ifBlank { server.scheme.uppercase() }
    val rowStateDescription = when {
        isConnected -> trx("Connected")
        isSelected -> trx("Selected")
        else -> ""
    }

    // MARBLE_HOME_ROW_POLISH_V220 — rows answer the finger like every other control (a kinetic
    // press instead of a bare clickable), meet the 56 dp touch floor, and the chosen/connected row
    // reads through a stronger rim rather than a heavier shadow.
    val rowRimWidth = if (isConnected || isSelected) 1.5.dp else 1.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(rowShape)
            .background(itemBg)
            .border(rowRimWidth, itemBorder, rowShape)
            .kineticClickable(role = Role.Button, boundedShape = rowShape, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(
                    stripLeadingFlag(server.name),
                    server.scheme.uppercase().takeIf { it.isNotBlank() },
                    endpoint.takeIf { it.isNotBlank() }
                ).joinToString(", ")
                stateDescription = rowStateDescription
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val locationTrust = marbleLocationTrustOf(
            hasSessionReport = false,
            hasMeasuredCode = countryCode.isNotBlank(),
            measuredIsProvisional = provisionalLocation,
            hasLabelGlyph = flag != null
        )
        ProtocolTile(
            scheme = server.scheme,
            size = 38.dp,
            flag = flag,
            flagCode = countryCode,
            locationTrust = locationTrust,
            stateTone = when {
                isConnected -> Aether.Emerald
                isSelected -> HomeCloud.Accent
                else -> null
            }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Text(
                text = displayServerName(server.name, server.host, server.scheme),
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected || isConnected) FontWeight.Bold else FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ProtocolBadge(scheme = server.scheme)

                if (countryName.isNotBlank() && countryName != "Unknown") {
                    Text(
                        text = countryName,
                        color = Aether.InkMuted.copy(alpha = .85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "•",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                Text(
                    text = endpoint,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ServerPingStat(
            latencyMs = latency,
            measured = measured != null,
            testing = testing,
            attempted = attempted
        )
    }
}

@Composable
internal fun IosSlideToConnect(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = MarbleMotion.current
    val trackHeight = MarbleSlideSpec.TrackHeight
    val trackShape = marbleSlideShape(trackHeight)
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
    val thumbSizeDp = MarbleSlideSpec.ThumbSize
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
            .height(trackHeight)
            .marbleSlideTrack(
                shape = trackShape,
                tone = tone,
                progress = (dragOffset.value / max(1f, trackWidthPx - thumbSizePx - 12f))
                    .coerceIn(0f, 1f),
                busy = busy,
                engaged = evidence.connected || evidence.connecting
            )
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        val maxDragPx = max(1f, trackWidthPx - thumbSizePx - 12f)
        val progress = (dragOffset.value / maxDragPx).coerceIn(0f, 1f)

        // MARBLE_SLIDE_PARK_V146 — the thumb parks on the side it was dragged to, instead of
        // springing back to the start the moment the tunnel answers.
        val restOffset = if (evidence.connected || evidence.connecting) maxDragPx else 0f
        LaunchedEffect(evidence.connected, evidence.connecting, maxDragPx) {
            if (!dragging) {
                dragOffset.animateTo(restOffset, MarbleExpressiveSpecs.WaveSpringFloat)
            }
        }

        MarbleSlideEnergyBed(
            tone = tone,
            progress = progress,
            busy = busy,
            modifier = Modifier.matchParentSize()
        )

        MarbleSlideGuideMarks(
            tone = tone,
            fade = if (evidence.connected) progress else 1f - progress,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(width = 62.dp, height = 24.dp)
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = labelText,
                color = marbleSlideLabelTone(tone, evidence.connected),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .2.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(start = thumbSizeDp + 8.dp, end = 58.dp)
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 6.dp, end = 6.dp)
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .graphicsLayer {
                    val lift = 1f + 0.03f * progress
                    scaleX = lift
                    scaleY = lift
                }
                .then(Modifier.marbleSlideThumb(tone, thumbSizeDp, dragging))
                .pointerInput(evidence.connected, busy, maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            // MARBLE_SLIDE_PARK_V146 — the commit direction follows the route:
                            // a live tunnel is closed by pulling back, a quiet one is opened by
                            // pushing forward.
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
                                        MarbleExpressiveSpecs.WaveSpringFloat
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            coroutineScope.launch {
                                dragOffset.animateTo(restOffset, MarbleExpressiveSpecs.WaveSpringFloat)
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
            HomeGlyphIcon(
                HomeGlyph.POWER,
                prismOnColor(tone),
                Modifier.size(thumbSizeDp * .44f)
            )
        }
    }
}

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
        HomeTopActionBar(evidence, actions, repo)

        NationalEventBanner(repo = repo)

        IosStatusWideCard(evidence, actions, modifier = Modifier.marbleStaggerIn(1))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            IosServerListBox(
                repo = repo,
                evidence = evidence,
                actions = actions,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .marbleStaggerIn(2)
            )
        }

        IosSlideToConnect(evidence, actions, modifier = Modifier.marbleStaggerIn(3))
    }
}

@Composable
private fun FloatingConnectFab(
    evidence: HomeEvidence,
    onToggle: () -> Unit
) {
    val motion = MarbleMotion.current
    val busy = evidence.connecting || evidence.disconnecting
    val chrome = rememberMarbleFloatChrome()
    val tone by animateColorAsState(
        targetValue = when {
            evidence.disconnecting -> chrome.actions.stop
            busy -> chrome.actions.securing
            else -> chrome.actions.connect
        },
        animationSpec = MarbleMotionSpecs.DockColor,
        label = "fab-tone"
    )

    val face by animateColorAsState(
        targetValue = Color(marbleDeepFaceArgb(tone.toArgb())),
        animationSpec = MarbleMotionSpecs.DockColor,
        label = "fab-face"
    )

    Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(88.dp)) {
            val spin = motion.loop(1_150)
            val breathe = motion.breathe(2_400)
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2f
            val ring = Size(size.width - inset * 2f, size.height - inset * 2f)
            if (busy) {
                rotate(degrees = spin * 360f) {
                    drawArc(
                        color = tone,
                        startAngle = -90f,
                        sweepAngle = ExpressiveMath.arcSweep(motion.loop(1_900), 170f, 300f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = ring,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawCircle(
                    color = tone.copy(alpha = .16f + .12f * breathe),
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(stroke)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(68.dp)
                .marblePopWhen(busy, peak = 1.05f)
                // MARBLE_SHADOW_CALM_V220 — the floating disc keeps its ring of light and loses its
                // slab of shadow.
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tone.copy(alpha = .15f),
                    spotColor = tone.copy(alpha = .22f)
                )
                .clip(CircleShape)
                .background(face)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .14f), Color.Transparent)
                    )
                )
                .kineticClickable(
                    pressScale = .94f,
                    boundedShape = CircleShape,

                    releaseSpec = MarbleMotionSpecs.ExitFloat
                ) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.POWER, Color.White, Modifier.size(30.dp))
        }
    }
}

@Composable
private fun FloatingSplitAction(
    tone: Color,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (ink: Color) -> Unit
) {
    val face = Color(marbleDeepFaceArgb(tone.toArgb()))
    Box(
        modifier = Modifier
            .size(52.dp)
            // MARBLE_SHADOW_CALM_V220 — the split discs are small; their shadows are small too.
            .shadow(
                elevation = 5.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = tone.copy(alpha = .14f),
                spotColor = tone.copy(alpha = .20f)
            )
            .clip(CircleShape)
            .background(if (enabled) face else face.copy(alpha = .55f))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .12f), Color.Transparent)
                )
            )
            .kineticClickable(
                enabled = enabled,
                pressScale = .94f,
                boundedShape = CircleShape,
                releaseSpec = MarbleMotionSpecs.ExitFloat,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content(Color.White)
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
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeTopActionBar(evidence, actions, repo)

            NationalEventBanner(repo = repo)

            IosStatusWideCard(evidence, actions, modifier = Modifier.marbleStaggerIn(1))
            IosServerListBox(
                repo = repo,
                evidence = evidence,
                actions = actions,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .marbleStaggerIn(2),

                bottomOverlayClearance = 104.dp
            )
        }

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
 *
 * MARBLE_HOME_ROUTE_PING_V210 — the split is two halves again. V208 dropped the ping half on the
 * grounds that "one ping, and it pings the group" was the honest reading of a page with several
 * ping surfaces; what it actually did was leave a connected user with a disconnect button and no
 * way to ask the question they are holding — is *this* server still answering? — without
 * travelling to another page. The second disc is back, it measures the connected server, and it
 * is the same verb the header's pulse runs ([HomeActions.onPingRoute]), so the two buttons cannot
 * answer the same question differently.
 */
@Composable
internal fun HomeFloatingSplitControl(
    evidence: HomeEvidence,
    actions: HomeActions,
    modifier: Modifier = Modifier
) {
    val chrome = rememberMarbleFloatChrome()
    val routeMeasuring = homeRouteMeasuring(evidence)

    Box(
        modifier = modifier.size(width = 88.dp, height = 118.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = evidence.connected,
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                expressiveFadeThrough()
            },
            label = "floating-split-anim"
        ) { isConnected ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                if (isConnected) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FloatingSplitAction(

                            tone = chrome.actions.stop,
                            description = Tr.now.disconnect,
                            onClick = { actions.onToggleConnection() }
                        ) { ink ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ink)
                                )
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ink)
                                )
                            }
                        }

                        // MARBLE_HOME_ROUTE_PING_V210 — the second half is the measurement of the

                        FloatingSplitAction(
                            tone = chrome.actions.measure,
                            description = if (routeMeasuring) {
                                trx("Measuring this server")
                            } else {
                                trx("Measure this server")
                            },
                            enabled = !routeMeasuring,
                            onClick = { actions.onPingRoute() }
                        ) { ink ->
                            if (routeMeasuring) {
                                MarbleExpressiveCircularIndicator(
                                    modifier = Modifier.size(19.dp),
                                    color = ink,
                                    strokeWidth = 1.8.dp,
                                    arcCount = 2
                                )
                            } else {
                                HomeGlyphIcon(HomeGlyph.PULSE, ink, Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
                    FloatingConnectFab(evidence = evidence, onToggle = { actions.onToggleConnection() })
                }
            }
        }
    }
}

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
        Canvas(modifier = Modifier.size(118.dp)) {
            val spin = motion.loop(1200)
            val slowSpin = motion.loop(9000)
            val breathe = motion.breathe(2600)
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val ring = Size(size.width - inset * 2f, size.height - inset * 2f)

            drawCircle(
                color = tone.copy(alpha = 0.08f + 0.06f * breathe),
                radius = size.minDimension * 0.44f
            )
            when {
                busy -> {
                    val wobble = motion.loop(1_700)
                    rotate(degrees = spin * 360f) {
                        drawArc(
                            color = tone,
                            startAngle = -80f,
                            sweepAngle = ExpressiveMath.arcSweep(wobble, 180f, 320f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = ring,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
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
                            color = tone.copy(alpha = 0.42f),
                            startAngle = index * 90f + 15f,
                            sweepAngle = 60f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = ring,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(86.dp)
                .marblePopWhen(connected, peak = 1.05f)
                // MARBLE_SHADOW_CALM_V220 — the orbital disc keeps its 5 dp lift but loses the
                // heavy tinted cast around it.
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tone.copy(alpha = .14f),
                    spotColor = tone.copy(alpha = .22f)
                )
                .clip(CircleShape)
                .background(tone)
                .kineticClickable(
                    pressScale = .93f,
                    boundedShape = CircleShape,
                    releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat
                ) { actions.onToggleConnection() },
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.POWER, marbleOnColor(tone), Modifier.size(40.dp))
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
        HomeTopActionBar(evidence, actions, repo)

        NationalEventBanner(repo = repo)

        IosStatusWideCard(evidence, actions, modifier = Modifier.marbleStaggerIn(1))

        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .marbleStaggerIn(2),
            contentAlignment = Alignment.Center
        ) {
            OrbitalConnectControl(evidence = evidence, actions = actions)
        }
        ConnectButtonCaption(
            evidence = evidence,
            tone = homeStateTone(evidence),
            modifier = Modifier
                .marbleStaggerIn(3)
                .padding(bottom = 2.dp)
        )

        IosServerListBox(
            repo = repo,
            evidence = evidence,
            actions = actions,
            modifier = Modifier
                .weight(1f, fill = false)
                .marbleStaggerIn(4)
        )
    }
}

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

    val floatingOverlay = modularConnect == ConnectButtonStyle.FLOATING

    val entranceArmed = rememberMarbleEntranceWindow()

    Box(modifier = Modifier.fillMaxSize()) {
        // MARBLE_HOME_ROW_POLISH_V220 — the modular page breathes on the same 10 dp rhythm as the
        // other four presentations instead of its own tighter one.
        Column(
            modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(bottom = bottomClearance + (if (floatingOverlay) 104.dp else 0.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeTopActionBar(evidence, actions, repo)

            NationalEventBanner(repo = repo)
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

        cardOrder.forEachIndexed { moduleIndex, cardType ->
            when (cardType) {
                ModularLayout.STATUS -> if (settings.modularShowStatus) {
                    IosStatusWideCard(
                        evidence,
                        actions,
                        modifier = Modifier.marbleStaggerIn(
                            moduleIndex + 1,
                            enabled = entranceArmed()
                        )
                    )
                }
                ModularLayout.SERVERS -> if (settings.modularShowServers) {
                    IosServerListBox(
                        repo = repo,
                        evidence = evidence,
                        actions = actions,
                        modifier = Modifier.marbleStaggerIn(
                            moduleIndex + 1,
                            enabled = entranceArmed()
                        ),
                        maxListHeight = serverListMaxHeight
                    )
                }
                ModularLayout.CONNECT -> if (!floatingOverlay) {
                    Box(
                        Modifier.marbleStaggerIn(moduleIndex + 1, enabled = entranceArmed())
                    ) {
                        ModularConnectModule(
                            evidence = evidence,
                            actions = actions,
                            style = modularConnect
                        )
                    }
                }
                ModularLayout.STATS -> if (settings.modularShowStats) {
                    Box(
                        Modifier.marbleStaggerIn(moduleIndex + 1, enabled = entranceArmed())
                    ) {
                        HomeSessionStats(evidence, actions, Aether.Cyan)
                    }
                }
                ModularLayout.SHORTCUTS -> if (settings.modularShowShortcuts) {
                    Box(
                        Modifier.marbleStaggerIn(moduleIndex + 1, enabled = entranceArmed())
                    ) {
                        HomeShortcutDeck(
                            evidence,
                            actions,
                            HomeCloud.Accent,

                            pingAction = evidence.pingChipAction
                        )
                    }
                }
            }
        }

        if (settings.modularShowSocks) {
            Box(Modifier.marbleStaggerIn(cardOrder.size + 1, enabled = entranceArmed())) {
                ModularSocksCard(repo = repo, evidence = evidence)
            }
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

    if (customizeOpen) {
        ModularCustomizerDialog(
            repo = repo,
            onDismiss = { customizeOpen = false }
        )
    }
}

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
    }
}

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
            .kineticClickable(
                enabled = enabled,
                pressScale = .92f,
                boundedShape = CircleShape,
                releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        HomeGlyphIcon(HomeGlyph.PULSE, marbleOnColor(Aether.Emerald), Modifier.size(24.dp))
    }
}

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

    var order by remember { mutableStateOf(ModularLayout.order(s.modularCardOrder)) }
    var showStatus by remember { mutableStateOf(s.modularShowStatus) }
    var showServers by remember { mutableStateOf(s.modularShowServers) }
    var showStats by remember { mutableStateOf(s.modularShowStats) }
    var showShortcuts by remember { mutableStateOf(s.modularShowShortcuts) }
    var showSocks by remember { mutableStateOf(s.modularShowSocks) }
    var connectStyle by remember { mutableStateOf(s.modularConnectStyle) }

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
            // MARBLE_SHADOW_CALM_V220 — a near-fullscreen dialog still floats, but on a 12 dp
            // shadow instead of the old 18 dp slab.
            shadowElevation = 12.dp,
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

        HomeStyle.ROUTE_ATELIER -> HomeThemeAtelier(
            repo = repo,
            evidence = evidence,
            actions = actions,
            bottomClearance = bottomClearance
        )
    }
}

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
