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
//
// MARBLE_EXPRESSIVE_MOTION_V186 — the Material 3 Expressive motion chapter for Home: every
// presentation cascades its cards in on arrival, the state word and the ping readout roll on
// the emphasized curves instead of hard-swapping, the connect controls' securing arcs stretch
// and contract on the shared clock (the wavy rhythm of the newest Android loaders), released
// knobs and pressed discs spring back with one visible overshoot, and the status pip became a
// living double-pulse dot. The status and route lines plus the ping slot remain stable; optional
// download/upload telemetry expands inside that same card, while Theme 1's center lane absorbs
// the extra height and keeps the slide-to-connect control anchored at the page floor.

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
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ModularCardSize
import com.marbleng.app.model.ModularLayout
import com.marbleng.app.model.ProbeState
// MARBLE_SERVER_TILE_LAYOUT_V208 — the row/tile preference this box reads.
import com.marbleng.app.model.ServerLayout
import com.marbleng.app.model.serversLayoutEnum
import com.marbleng.app.model.parseConnectButtonStyle
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
    /** Address family selected for the active server path (never the device's unrelated public IP). */
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
    /**
     * MARBLE_HOME_PING_SPEED_V200 — the number on screen is one sample of a run still in
     * progress, not the settled median.
     */
    val pingProvisional: Boolean = false,
    val selectedPingMs: Int,
    val selectedPingState: ConnectionPingState,
    val selectedPingFailure: String,
    /** The disconnected twin of [pingProvisional]. */
    val selectedProvisional: Boolean = false,
    val downBps: Long,
    val upBps: Long,
    val showSpeedWidget: Boolean,
    // MARBLE_IRAN_AWARE_PING_UI — Layer 0/2 signals: the capsule's ⚠️ and the sparkline's
    // injection segments consume them.
    val injectedResetSuspected: Boolean = false,
    val stabilityClass: String = "",
    // MARBLE_SESSION_USAGE_V192 — the data this connection has moved (live) and the one before
    // it finished with, plus the display choice that gates both readouts.
    val sessionBytes: Long = 0L,
    val lastSessionBytes: Long = 0L,
    val showDataUsage: Boolean = false,
    // MARBLE_SERVER_LOCATION_V192 — the ISO code the status card's flag circle should draw:
    // the live server-intel geo, else the repository's once-tested location. A country an operator
    // wrote into a node name is NOT part of this value any more — see [locationTrust].
    val flagCode: String = "",
    /**
     * MARBLE_ROUTE_ATELIER_V207 — the one state this page is in, resolved from the same evidence
     * every surface reads. Hue, copy and enabled-ness of the connect control all derive from it, so
     * the five silhouettes cannot drift apart, and neither can the status card next to them.
     */
    val routeState: MarbleRouteState = MarbleRouteState.READY,
    /** What the flag in this page's tile is allowed to claim: measured, lone, label, or nothing. */
    val locationTrust: MarbleLocationTrust = MarbleLocationTrust.UNKNOWN
) {
    /** True when a national flag may be painted for this route at all. */
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
    // MARBLE_ROUTE_ATELIER_V207 — the location answer and its strength travel together.
    //
    // Three sources can name a country here and they are not three versions of one fact: the exit
    // report of a running session is data about the tunnel, the resolver's measured code is data
    // about the endpoint, and an emoji inside a node's own name is marketing copy. The flag circle
    // used to read them as one fallback chain, which let a label claim a measurement's authority.
    // The chain stays — a page should still use the best code it has — but the label tier is no
    // longer a flag, and a lone witness is no longer a confirmed one.
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
        // MARBLE_SESSION_USAGE_V192 — the live counter only means something while the session
        // runs; the last one stays readable afterwards for the "last session" chip.
        sessionBytes = if (repo.state == "CONNECTED") repo.sessionBytes else 0L,
        lastSessionBytes = repo.lastSessionBytes,
        showDataUsage = repo.settings.homeShowDataUsage,
        flagCode = flagCode
    )
}

/** The ISO code a leading flag emoji in a node name encodes, or blank when there is none. */
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
    val onPingRoute: () -> Unit = {}
)

/** The per-style skin every shared evidence widget renders through. */
internal enum class HomeFlavor { IOS_SLIDER, IOS_FLOATING, IOS_EMBOSSED, IOS_MODULAR, ROUTE_ATELIER }

/** The single source of truth for which presentation skin a [HomeStyle] renders through. */
internal fun homeFlavorFor(style: HomeStyle): HomeFlavor = when (style) {
    HomeStyle.IOS_SLIDER -> HomeFlavor.IOS_SLIDER
    HomeStyle.IOS_FLOATING -> HomeFlavor.IOS_FLOATING
    HomeStyle.IOS_EMBOSSED -> HomeFlavor.IOS_EMBOSSED
    HomeStyle.IOS_MODULAR -> HomeFlavor.IOS_MODULAR
    HomeStyle.ROUTE_ATELIER -> HomeFlavor.ROUTE_ATELIER
}

/**
 * The state word of every Home surface.
 *
 * MARBLE_ROUTE_ATELIER_V207 — the three per-style tone functions that used to sit here (`homeTone`,
 * `styleConnectedTone`, `styleStateTone`) are deleted rather than deprecated. They were the review's
 * complaint in miniature: four Home presentations holding four private opinions about what
 * "connected" looks like — one of them reading `Aether.AmethystBright`, a token that holds the very
 * value of `Cyan` in both brand palettes — and nothing was wired to them, so the drift cost nothing
 * to keep and everything to reason about. A state colour is one mapping now ([marbleRouteTone]); a
 * style may choose its accent, never its meaning.
 *
 * The copy follows the same table, and it closes a hole the button had: with no route selected the
 * page used to offer "Connect" for a connection that cannot be made. It offers the picker instead,
 * because that is the real next step, and the glyph on the face now agrees with the word.
 */
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

/** The verb of the connect control: one table for the five silhouettes and for the Atelier hero. */
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

/**
 * MARBLE_HOME_PING_SPEED_V200 — is the number on screen one sample of a run still in progress?
 *
 * The old Home ping showed nothing until every sample was in: `IDLE -> MEASURING ("•••") ->
 * MEASURED`. On a slow link the capsule sat blank for seconds after the first round trip had
 * already come back and answered the user's question. The number now lands with the first
 * sample and is marked with a tilde until the run settles — the same measurement, shown the
 * moment it exists, never a different one.
 */
internal fun homePingIsProvisional(evidence: HomeEvidence): Boolean =
    if (evidence.connected) evidence.pingProvisional else evidence.selectedProvisional

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
    // MARBLE_HOME_PING_SPEED_V200 — a real sample outranks a placeholder. The tilde is the
    // whole contract: the number is real, the run is not finished.
    val provisional = homePingIsProvisional(evidence) && ms >= 1
    return when (state) {
        ConnectionPingState.MEASURING -> if (provisional) "~ $ms ms" else t.pingMeasuringValue
        ConnectionPingState.MEASURED -> if (ms >= 1) "$glyph $ms ms" else "$glyph ✕"
        ConnectionPingState.FAILED -> if (provisional) "~ $ms ms" else "🚫 ${t.pingFailedShort}"
        ConnectionPingState.IDLE -> t.pingIdleValue
    }
}

@Composable
internal fun homePingTone(evidence: HomeEvidence, fallback: Color): Color {
    val (ms, state, _) = homeV137PingChannel(evidence)
    // A provisional number is still a number, so it is coloured by the band it falls in —
    // otherwise "how fast is this route" would go grey exactly when it first has an answer.
    val provisional = homePingIsProvisional(evidence) && ms >= 1
    return when (state) {
        ConnectionPingState.MEASURED -> if (ms >= 1) marbleMetricTone(pingMetricBand(ms)) else Aether.Danger
        ConnectionPingState.FAILED -> if (provisional) marbleMetricTone(pingMetricBand(ms)) else Aether.Danger
        ConnectionPingState.MEASURING -> if (provisional) marbleMetricTone(pingMetricBand(ms)) else Aether.Cyan
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
    // MARBLE_ROUTE_ATELIER_V207 — the gate is the state table's own answer rather than a local
    // reading of one flag, so the five silhouettes cannot disagree about what "wait" means.
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
    // MARBLE_SMOOTH_CLOCK_V193 — sweep/busyPulse/haloPulse moved into the draw lambda below;
    // this round button no longer recomposes at the frame rate while connected.
    val label = homeActionLabel(evidence)
    val controlDescription = "${trx(label)} ${trx("connection button")}"
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
                // MARBLE_EXPRESSIVE_MOTION_V186 — the acknowledgement beat: when the session
                // flips, the whole face springs past rest once, then settles on the emphasized
                // decelerate curve. MARBLE_ROUTE_ATELIER_V207 halves the travel and gates it: five
                // percent of a 168 dp disc is a lurch, and a control that leaps for its own state
                // change is competing with the status word directly above it for the same sentence.
                .marblePopWhen(evidence.connected, peak = MarbleConnectMotion.statePopPeak)
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
                .kineticClickable(
                    enabled = armed,
                    role = Role.Button,
                    pressScale = MarbleControlKind.Primary.pressScale,
                    boundedShape = CircleShape,
                    releaseSpec = MarbleExpressiveSpecs.SpringReleaseFloat,
                    onClick = {
                        // The acknowledgement ring expands outward once per tap, on the shared
                        // response spring, while the press scale (owned by kineticClickable)
                        // compresses and releases the face. The ring is motion, so reduced motion
                        // keeps the press and drops the echo instead of the whole conversation.
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
                // Draw-phase clock reads (MARBLE_SMOOTH_CLOCK_V193) — recomposes nothing.
                val sweep = if (busy) motion.loop(1_150) * 360f else 0f
                // The securing arc breathes while it rotates: width and alpha pulse on a shared clock.
                val busyPulse = if (busy) motion.breathe(1_150) else 0f
                // MARBLE_ROUTE_ATELIER_V207 — a connected tunnel used to breathe forever: a ring
                // swelling on a 2.8 s loop that reported nothing, kept a frame callback alive on the
                // single most-seen screen, and made "it is working" look like "something is happening".
                // The face now holds still while a session is up and moves only for the two changes a
                // user actually needs to see: the securing arc and the press echo. The ambient switch
                // can bring the breathing back for whoever liked it.
                val haloPulse = if (evidence.connected && motion.acknowledges(MarbleControlKind.Primary)) {
                    motion.breathe(2_800)
                } else {
                    0f
                }
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
                            // MARBLE_EXPRESSIVE_MOTION_V186 — the securing arc STRETCHES: its
                            // sweep oscillates between 74 and 136 degrees on the breathing
                            // clock, the elastic rhythm of the newest Android loaders.
                            sweepAngle = ExpressiveMath.arcSweep(busyPulse, 74f, 136f),
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
                // MARBLE_EXPRESSIVE_MOTION_V186 — the power glyph rolls instead of snapping:
                // the departing glyph accelerates out of the slot while the incoming one
                // springs in from a slight underscale, all inside the animated icon box, so
                // the button face keeps its exact footprint in every state.
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
                    // MARBLE_HOME_PING_SPEED_V200 — the first sample shows as "~ N ms" instead of the
                    // dots, so the capsule answers as soon as the route does.
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
                                // MARBLE_MATERIAL_YOU_REFRESH_V185 — the micro chip rides the new
                                // label floor (11.5 sp) instead of the retired 11 sp.
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
    val controlDescription = "${trx(label)} ${trx("slider")}"
    // Generous on purpose: the last fifth of the travel is all commitment.
    val threshold = .78f

    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }
    val progress = (knob.value / travelPx).coerceIn(0f, 1f)
    // MARBLE_SMOOTH_CLOCK_V193 — the shimmer phase is read in the draw lambda below.
    var dragging by remember { mutableStateOf(false) }
    val thresholdReached = progress >= threshold

    // MARBLE_SLIDE_PARK_V146 — the knob parks on the side it was dragged to. While a route is
    // live (or being opened) it rests at the END of the track; otherwise at the START. This
    // replaces the old "always spring back to zero", which left a connected control still
    // reading "slide to connect".
    val restAtEnd = evidence.connected || evidence.connecting
    val animates = motion.acknowledges(MarbleControlKind.Primary)
    LaunchedEffect(restAtEnd, travelPx, animates) {
        if (!dragging) {
            val target = if (restAtEnd) travelPx else 0f
            if (animates) knob.animateTo(target, MarbleMotionSpecs.QuickReveal) else knob.snapTo(target)
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
                    // MARBLE_ROUTE_ATELIER_V207 — a drag-only control has no accessibility path: a
                    // TalkBack user heard "connect slider" and had nothing to activate. The drag stays
                    // the touch gesture (it is the whole point of a slide-to-confirm: a pocket tap
                    // must never close a tunnel) and the semantic action is offered next to it, so the
                    // verb exists for everyone who cannot sweep a 54 dp knob across a track.
                    .semantics {
                        contentDescription = controlDescription
                        onClick(label = controlDescription) {
                            if (armed) onToggle()
                            true
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                if (busy) {
                    Canvas(Modifier.matchParentSize()) {
                        // One travelling highlight, drawn only while the tunnel is actually
                        // opening or closing: the track states progress without ever moving.
                        // Draw-phase clock read (MARBLE_SMOOTH_CLOCK_V193).
                        val x = size.width * (if (busy) motion.loop(1_400) else 0f)
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
                                    val wasConnected = evidence.connected
                                    val completed = if (wasConnected) {
                                        knob.value <= travelPx * (1f - threshold)
                                    } else {
                                        knob.value >= travelPx * threshold
                                    }
                                    if (completed) {
                                        // MARBLE_ROUTE_ATELIER_V207 — the command leaves FIRST.
                                        //
                                        // The commit used to read `animateTo(…) then onToggle()`, so a
                                        // network action sat behind a 120 ms decorative tween: on a
                                        // dropped frame the haptic had promised a connection the
                                        // engine had not been told about yet, and the whole control
                                        // felt like a UI toy rather than a switch. Follow-through is
                                        // worth keeping, so it now runs BESIDE the command, and with
                                        // animations off it is an instant snap instead of a wait.
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
                                            // MARBLE_EXPRESSIVE_MOTION_V186 — a short drag releases on
                                            // the wave spring: the knob springs back to its resting
                                            // side with one visible overshoot and squishes against the
                                            // track's clip before settling.
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
                    // MARBLE_EXPRESSIVE_MOTION_V186 — the knob's glyph rolls on state flips
                    // inside its fixed 42%-of-knob slot; the drag geometry never changes.
                    MarbleExpressiveGlyphSwap(
                        key = connectButtonGlyph(evidence),
                        modifier = Modifier.size(knobSize * .42f)
                    ) {
                        HomeGlyphIcon(
                            connectButtonGlyph(evidence),
                            Aether.Void,
                            Modifier.size(knobSize * .42f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // MARBLE_ROUTE_ATELIER_V207 — the track is deliberately pinned left-to-right (it is a
            // physical, screen-space control), which means the generic "slide to act" caption cannot
            // tell a Persian reader which way to move, or that the direction does not flip with the
            // page. The hint states the direction the verb needs instead.
            Text(
                if (evidence.connected) trx("Slide left to disconnect") else trx("Slide right to connect"),
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
    // MARBLE_SMOOTH_CLOCK_V193 — the securing sweep is read in the draw lambda below.
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
                .semantics { contentDescription = controlDescription }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    // Draw-phase clock read (MARBLE_SMOOTH_CLOCK_V193) — recomposes nothing.
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
            // State lamp / live ping: the classic switch shows live line state and ping.
            if (evidence.connected) {
                // MARBLE_HOME_PING_SPEED_V200 — the first sample shows as "~ N ms" instead of the
                // dots, so the capsule answers as soon as the route does.
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

// ---------------------------------------------------------------------------------------------
// Glyph system
// ---------------------------------------------------------------------------------------------

internal enum class HomeGlyph {
    POWER, CHECK, RESET, COPY, REFRESH, MORE, PULSE, CLOCK, LIBRARY, PLUS, BOLT, PASTE, QR, INFO,
    DOWNLOAD, UPLOAD,
    /** MARBLE_SESSION_USAGE_V192 — a storage cylinder: the data a session has moved. */
    DATA,

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
        // MARBLE_MATERIAL_YOU_REFRESH_V185 — the Home glyph stroke lifts to the refreshed
        // Material Symbols weight so every Home presentation's icons read fuller and modern.
        val stroke = (size.minDimension * .102f).coerceIn(1.45f, 3.5f)
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
            HomeGlyph.DOWNLOAD, HomeGlyph.UPLOAD -> {
                val down = glyph == HomeGlyph.DOWNLOAD
                val startY = if (down) h * .18f else h * .82f
                val endY = if (down) h * .64f else h * .36f
                drawLine(color, Offset(w * .5f, startY), Offset(w * .5f, endY), stroke, StrokeCap.Round)
                val arrow = Path().apply {
                    if (down) {
                        moveTo(w * .30f, h * .48f)
                        lineTo(w * .50f, h * .68f)
                        lineTo(w * .70f, h * .48f)
                        moveTo(w * .24f, h * .82f)
                        lineTo(w * .76f, h * .82f)
                    } else {
                        moveTo(w * .30f, h * .52f)
                        lineTo(w * .50f, h * .32f)
                        lineTo(w * .70f, h * .52f)
                        moveTo(w * .24f, h * .18f)
                        lineTo(w * .76f, h * .18f)
                    }
                }
                drawPath(arrow, color, style = line)
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
            HomeGlyph.DATA -> {
                // A storage cylinder: top ellipse + two walls + a lower arc.
                drawOval(
                    color = color,
                    topLeft = Offset(w * .22f, h * .18f),
                    size = Size(w * .56f, h * .22f),
                    style = line
                )
                drawLine(color, Offset(w * .22f, h * .29f), Offset(w * .22f, h * .71f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .78f, h * .29f), Offset(w * .78f, h * .71f), stroke, StrokeCap.Round)
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * .22f, h * .59f),
                    size = Size(w * .56f, h * .22f),
                    style = line
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
            // MARBLE_HOME_ONE_PING_V208 — a latency read-out is not a ping button. This cell
            // used to accept a tap that started a *second*, different measurement (one route
            // instead of the group), so the page had two controls that both said "ping" and did
            // different things. It is a display now; the header owns the only ping verb.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Tr.now.connectionPing, color = Aether.InkMuted, style = MaterialTheme.typography.labelSmall)
                HomeStatValueText(ping, pingTone, sizeScale = 1.1f)
            }
            // MARBLE_SESSION_USAGE_V192 — the data the session moved joins the strip as its own
            // quiet cell, only when the user opted in: live while connected, the last session's
            // total afterwards.
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

/** MARBLE_SESSION_USAGE_V192 — a byte total as the quietest human number that still is one. */
private fun homeCompactBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "${bytes} B"
}


// ---------------------------------------------------------------------------------------------
// COMPONENT 1: WIDE STATUS BAR (Shared across all 4 iOS themes)
// ---------------------------------------------------------------------------------------------

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
    // banner packed four metadata fields, a ping badge, copy and info glyphs into the same row;
    // this hierarchy keeps only the endpoint + protocol at a glance and moves the full report
    // behind the route tap. State and latency still roll in their fixed slots.
    HomeCloudCard(modifier = modifier.fillMaxWidth(), shape = shape) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
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
                // MARBLE_HOME_STATUS_V190 — the uptime exists only while a session does. A clock
                // beside a bare dash said nothing while disconnected and read as a broken widget;
                // the chip now fades in with the session and leaves with it.
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
                // MARBLE_SESSION_USAGE_V192 — the twin of the uptime chip for the disconnected
                // state: what the previous connection moved. The uptime leaves with the session,
                // the usage stays as the fact that replaces it — same slot, same quiet tone.
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
                    // MARBLE_ROUTE_ATELIER_V207 — the circle is the route's PLACE, and it may only
                    // claim a country the app measured or the session reported. What used to sit here
                    // was a fallback chain that ended in the emoji an operator put in the node's own
                    // name: a big official-looking flag, for a guess. An unconfirmed route now shows
                    // the world glyph and says so in the caption under it — the emoji stays where it
                    // was written, inside the name.
                    CountryFlagCircle(
                        code = evidence.flagCode.ifBlank { null }?.takeIf { evidence.mayPaintLocationFlag },
                        size = 34.dp,
                        fallbackText = "🌐",
                        fallbackFill = homeCloudInsetFill(),
                        fallbackTone = Aether.Ink
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

                // MARBLE_HOME_ONE_PING_V208 — the status card prints the route's latency; it no
                // longer starts a measurement. Two controls on one page both labelled "ping" and
                // measuring different scopes is how a user learns that neither does what it says.
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
                    // MARBLE_HOME_STATUS_V190 — an unmeasured route shows the pulse button alone:
                    // a lone dash beside it looked like a missing value, not an invitation to tap.
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

            // MARBLE_SESSION_USAGE_V192 — the grid shows when EITHER display choice is on:
            // the live rates (speed widget) and/or the session's data total. Both on, three
            // equal cells; data only, the single cell still earns the row.
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

/**
 * ZedSecure-inspired connection telemetry: two quiet, equal cells with circular glyph wells.
 * Values roll in place, so changing rates never reflows the server/status hierarchy.
 */
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

/** A short, calm ping value for the Home card; the full verdict remains in accessibility copy. */
private fun compactHomePingValue(evidence: HomeEvidence): String {
    val (milliseconds, state, _) = homeV137PingChannel(evidence)
    return when (state) {
        ConnectionPingState.MEASURING -> "•••"
        ConnectionPingState.MEASURED -> if (milliseconds > 0) "${milliseconds}ms" else "—"
        ConnectionPingState.FAILED -> "×"
        ConnectionPingState.IDLE -> "—"
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
    // MARBLE_STABLE_NAVIGATION_V202 — the wordmark joins the product's one shared clock instead
    // of owning a second infinite transition. A coarse phase is ample for this decorative tide,
    // avoids a permanent 60/120 Hz recomposition in the header, and freezes with every other
    // ambient detail when the system animation scale is off.
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
    // One hue per state, and none of them is a latency colour: the header is a verb, not a meter.
    // MARBLE_FLOATING_ACTIONS_V210 — the measure hue is the theme's own action token, so the
    // header's pulse and the split control's second disc are the same colour in every theme.
    val chrome = rememberMarbleFloatChrome()
    // Danger only while the button is a cancel (V156); otherwise the theme's measure hue, which
    // is the same hue the split control's second disc wears in this theme.
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
            // A one-route measurement is single-flight and has nothing to interrupt, so the
            // control reports the wait rather than advertising a stop that would be a no-op.
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

/**
 * The constant footprint of the header's ping control.
 *
 * It is a named number because the whole point is that it never changes: the idle and the
 * cancelling state are the same circle, so pressing the button cannot re-layout the header.
 */
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
    // is asking: is THIS server alive? So the label names the server on screen, and the two live
    // ping states are read from the evidence the status card is already drawing.
    val routeLabel = evidence.nodeName.ifBlank { Tr.now.chooseRoute }
    val routeMeasuring = homeRouteMeasuring(evidence)

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
    // The Home header stays intentionally quiet: no transient status strip is inserted under it.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
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
        // MARBLE_PING_CANCEL_V156 — the same control that can end a group sweep ends it: while a
        // bulk sweep is live (started here or on the Servers page) the pulse becomes a filled
        // STOP square, so the Home page can cancel it without travelling anywhere.
        //
        // MARBLE_HOME_ONE_PING_V208 — and this is the ONLY ping button on the Home page. It shows
        // no number: the header is a verb, not a meter, and the two places that do report
        // latency (the status card, the quality card) are displays that do not accept a tap.
        //
        // MARBLE_HOME_ROUTE_PING_V210 — the verb is the ROUTE, not the group: the server named on
        // its own label, measured through the tunnel while one is up and at its endpoint while
        // one is down. The group sweep keeps its own door on the Servers page, where the list of
        // servers it measures is actually on screen.
        HomeRoutePingButton(
            sweeping = sweeping,
            measuring = routeMeasuring,
            description = when {
                sweeping -> trx("Cancel measuring")
                routeMeasuring -> "${Tr.now.testPing} • $routeLabel"
                else -> "${Tr.now.testPing} • $routeLabel"
            },
            enabled = !cancelling && !routeMeasuring,
            onClick = {
                when {
                    cancelling -> Unit
                    groupBusy -> repo.cancelProbes()
                    else -> actions.onPingRoute()
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
    // MARBLE_HEADER_ICON_CONTRAST_V205 — the header actions sit bare on the page, so their ink
    // is measured against the page itself. The bright sky the dark theme needs (#3399FF) is
    // only 2.8:1 on the light page — under the 3:1 floor a graphical object must clear, which
    // is exactly how the + / pulse / info icons read as washed-out in the System theme while
    // looking perfect on AMOLED. Pushing the tone toward the readable endpoint keeps its hue
    // and restores the floor in every palette.
    val pageTone = marbleReadableOn(tone, Aether.Void, 3.0f)
    // MARBLE_ROUTE_ATELIER_V207 — three 36 dp icons used to hang off the top of the product's main
    // page: the smallest targets of the app, on the row a user taps before they have decided to
    // trust it. The artwork keeps its 36 dp; the hit box takes the floor the grammar promises.
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
                // MARBLE_EXPRESSIVE_MOTION_V186 — the bare action's busy beat is the wavy
                // expressive indicator: three arcs stretching around the 17 dp slot on the
                // shared clock instead of a rigid stock spinner.
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

/** The semantic state colour of the Home instrument — one function, four themes, no drift. */
@Composable
internal fun homeStateTone(evidence: HomeEvidence): Color = marbleRouteTone(evidence.routeState)

/**
 * Flat status pip with a soft halo; breathes only while a handshake is actually running.
 *
 * MARBLE_EXPRESSIVE_MOTION_V186 — a settled session now reads as ALIVE: one soft ring leaves the
 * pip every 2.6 s on the shared frame clock, and a second ring follows half a period behind it,
 * so the pulse never has a dead beat. Both rings expand and fade strictly inside the pip's own
 * [size] slot — the banner's 13 dp geometry is untouched — and with animations disabled the
 * rings rest and the pip renders exactly as the classic static dot.
 */
@Composable
private fun StatusDot(stateColor: Color, busy: Boolean, size: Dp = 18.dp) {
    val motion = MarbleMotion.current
    // MARBLE_ROUTE_ATELIER_V207 — while a session is *up*, nothing is happening: the rings used to
    // leave the pip every 2.6 s forever, which read as "activity" on the one state that has no
    // activity to report, and cost a permanent redraw on the most-looked-at screen in the product.
    // The pulse now belongs to the ambient setting (and to a busy handshake, which is real work).
    val ambient = LocalMarbleAmbientField.current
    Canvas(modifier = Modifier.size(size)) {
        // MARBLE_HOME_COMPACT_BANNER_V167 — the diameter is now a parameter, and a parameter named
        // `size` shadows DrawScope's own metric, so the pip's geometry reads it as a length.
        val diameter = size.toPx()
        // The shared clock is read in the draw phase: ambient motion costs zero recompositions.
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
    // MARBLE_EXPRESSIVE_MOTION_V186 — the Home server card owns its arrival cascade: rows rise
    // one stagger step apart the first time the card is composed. The window disarms itself, so
    // reorders, filter changes and scroll-backs never replay the entrance.
    val entranceArmed = rememberMarbleEntranceWindow()
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
            } else if (settings.serversLayoutEnum == ServerLayout.GRID) {
                /*
                 * MARBLE_SERVER_TILE_LAYOUT_V208 — the same list as the Servers page, in the same
                 * compact boxes: one preference answers for both, so a user who chose tiles on
                 * Servers does not get rows back on Home.
                 */
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
                    // MARBLE_EXPRESSIVE_MOTION_V186 — the rows arrive in a cascade: each one
                    // rises and settles one stagger step after the previous, first eight only
                    // (the rest are below the fold; the index clamps at the library's maximum).
                    itemsIndexed(visibleServers, key = { _, server -> server.id }) { rowIndex, server ->
                        // MARBLE_HOME_MIRRORS_SERVERS_V150 — same predicates as the Servers page:
                        // selected is the stored selection (any state), active is the row actually
                        // carrying traffic — so the Home list and Servers list agree about which
                        // server is chosen and which is live, and a tap selects exactly as Servers does.
                        val isSelected = repo.isSelectedProfile(server)
                        val isConnected = repo.isActiveProfile(server)
                        // animateItem keeps reorders/gliding smooth without touching row heights;
                        // the stagger adds the one-time arrival rise-and-settle inside that box.
                        Box(
                            Modifier
                                .animateItem()
                                .marbleStaggerIn(
                                    rowIndex + 1,
                                    enabled = entranceArmed() && rowIndex < 8
                                )
                        ) {
                            // MARBLE_SERVER_LOCATION_V192 — the row's location is the
                            // repository's answer for this endpoint: the once-tested geolocation
                            // when the app has learned it, the label's own country otherwise.
                            // It recomposes in place the moment a background test lands.
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

/**
 * MARBLE_HOME_SERVER_ROW_V141 — one clean, flat server row.
 *
 * MARBLE_PROTOCOL_IDENTITY — a circular protocol tile (the type's own glyph and tone, the flag
 * on the rim, the connection state on the rim colour) leads, the name with its tiny protocol
 * badge follows, the latency reads in the shared right-aligned stat column, and the trailing
 * state is one of two quiet marks: a check (selected) or a live check (carrying traffic).
 * Resting rows are near-invisible insets; the selected row is the one saturated element with
 * the sky fill and accent rim.
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
    countryCode: String = "",
    countryName: String = "",
    provisionalLocation: Boolean = false,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(16.dp)
    // MARBLE_FLOATING_CHROME_OPAQUE_V205 — the connected wash is composited onto the resting
    // fill instead of being painted translucent. Animating between a translucent wash and an
    // opaque fill passed through half-alpha frames that let the page aurora bleed through the
    // row mid-transition; every endpoint of the tween is now a solid colour.
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

    // MARBLE_PROTOCOL_TEXT_IDENTITY_V193 — the row now opens with the server's place in a
    // circular tile: the real flag when the location is known, the name's own flag glyph
    // otherwise — never a protocol doodle. Then the name with its coloured text badge (each
    // protocol its own hue), and the latency in its own right-aligned stat column with a
    // quality meter — the same anatomy the Servers page uses.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(itemBg)
            .border(1.dp, itemBorder, rowShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(
                    stripLeadingFlag(server.name),
                    server.scheme.uppercase().takeIf { it.isNotBlank() },
                    endpoint.takeIf { it.isNotBlank() }
                ).joinToString(", ")
                stateDescription = rowStateDescription
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MARBLE_SERVER_LOCATION_V192 — the row opens with the server's location, not its
        // protocol: the tested country's flag fills the circle, and the protocol keeps its
        // identity in the small badge under the name. Without a known location the protocol
        // glyph returns to the circle, so the row never reads empty.
        // MARBLE_ROUTE_ATELIER_V207 — the tile states the strength of its own answer (see
        // [MarbleLocationTrust]): a measured country paints the flag, a name that merely contains a
        // flag paints the world with a dashed rim.
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
                // MARBLE_SERVER_LOCATION_V192 — the tested location reads beside the badge: the
                // circle shows the flag, the line says the country, and the endpoint follows.
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
                // Tabular figures instead of a monospace face: digits still line up row to row,
                // but the endpoint no longer spends a full em per dot and colon, so the port
                // stays visible instead of being ellipsised away on a normal phone.
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

        // MARBLE_PROTOCOL_IDENTITY — the latency reads in the same right-aligned stat column the
        // Servers page uses, so one list's number is the other's number in the same place.
        // MARBLE_HOME_ROW_V190 — the trailing check badge (and the empty 22 dp slot every other
        // row reserved for it) is gone. Selection was already stated three times — the sky fill,
        // the accent rim and the lit tile ring — and the reserved slot cut the endpoint short on
        // every row. The latency column now owns the trailing edge alone.
        ServerPingStat(
            latencyMs = latency,
            measured = measured != null,
            testing = testing,
            attempted = attempted
        )
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
            // MARBLE_HOME_CLOUD_DEPTH_V191 — the track keeps its control identity (no card lift)
            // but gains the one soft cool shadow every raised surface now carries, so it no
            // longer reads as a hole cut in the page between two shadowed cards.
            .shadow(
                elevation = 3.dp,
                shape = trackShape,
                clip = false,
                ambientColor = Color(0xFF0A2540).copy(alpha = .18f),
                spotColor = tone.copy(alpha = .26f)
            )
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
                // MARBLE_EXPRESSIVE_MOTION_V186 — the thumb parks on the wave spring: one soft
                // overshoot against the track's clip, then it holds. The committed flight
                // below keeps its deliberate 220 ms tween — that beat belongs to the action.
                dragOffset.animateTo(restOffset, MarbleExpressiveSpecs.WaveSpringFloat)
            }
        }

        // Ambient sheen: a single light band on the shared motion clock — slow when armed,
        // fast while the tunnel negotiates, invisible once the user owns the gesture. The clock
        // is read inside the draw lambda, so the sweep costs zero recompositions.
        //
        // MARBLE_SLIDE_SHEEN_THEMED_V205 — the band used to be hard-coded white. Over the dark
        // track that reads as light; over the light theme's white track it is literally
        // invisible, so the invitation the control advertises only ever existed in dark mode.
        // The sheen now paints with the state tone on light pages and stays white on dark ones.
        val sheenTint = if (homeCloudDark()) Color.White else tone
        Canvas(modifier = Modifier.matchParentSize()) {
            val sheenPhase = motion.loop(if (busy) 1100 else 3000)
            val sheenAlpha = (if (busy) 0.30f else 0.20f) * (1f - progress)
            if (sheenAlpha > 0.01f) {
                // MARBLE_EXPRESSIVE_MOTION_V186 — the invitation band breathes: its width
                // oscillates between 22% and 42% of the track on its own slow clock while it
                // sweeps, so the sheen reads as a living highlight instead of a fixed stripe.
                val band = size.width * ExpressiveMath.wavyValue(motion.loop(1900), 0.22f, 0.42f)
                val x = sheenPhase * (size.width + band) - band
                drawRect(
                    topLeft = Offset(x, 0f),
                    size = Size(band, size.height),
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            sheenTint.copy(alpha = 0f),
                            sheenTint.copy(alpha = sheenAlpha),
                            sheenTint.copy(alpha = 0f)
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
                // MARBLE_SLIDE_SHEEN_THEMED_V205 — the knob's lift is cast in the track's own
                // hue family. The stock shadow pair (a black ambient under a full-strength spot)
                // painted a grey smudge under the thumb on the ice page and an over-saturated
                // ring on the AMOLED page.
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tone.copy(alpha = .22f),
                    spotColor = tone.copy(alpha = .40f)
                )
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
                                    // MARBLE_EXPRESSIVE_MOTION_V186 — a short drag releases on
                                    // the wave spring: the thumb springs back to its resting
                                    // side with one visible overshoot, exactly the elastic
                                    // release the newest Android slide controls advertise.
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
            // MARBLE_FLOATING_CHROME_V201 — the glyph is chosen against the disc, not assumed white.
            HomeGlyphIcon(HomeGlyph.POWER, marbleOnColor(tone), Modifier.size(26.dp))
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
        // MARBLE_BANNER_IN_FLOW_V205 — the national-filtering alert occupies its own slot under
        // the header instead of floating over it.
        NationalEventBanner(repo = repo)
        // MARBLE_EXPRESSIVE_MOTION_V186 — Theme 1 arrives in a cascade: banner, server card,
        // then the slide control, one stagger step apart on the emphasized entrance pair.
        IosStatusWideCard(evidence, actions, modifier = Modifier.marbleStaggerIn(1))

        // The center lane owns all remaining height, while the server card stays top-aligned
        // inside it. This caps a long list for its own scroll and leaves short libraries with
        // intentional breathing room — the horizontal connect track always stays at the floor.
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

        // The horizontal slide control is a true page-floor action in the first theme.
        IosSlideToConnect(evidence, actions, modifier = Modifier.marbleStaggerIn(3))
    }
}

// ---------------------------------------------------------------------------------------------
// THEME 2: iOS FLOATING SPLIT-BUTTON THEME (Fixed Screen)
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_HOME_CONNECT_CONTROLS_V141 — the Theme 2 floating control, redrawn flat.
 *
 * Armed: a solid state-coloured disc with a breathing halo ring. Busy: the halo becomes a
 * spinning arc that orbits the disc. Connected: the whole control morphs into the split pair —
 * a flat danger pause and a flat emerald ping. Every ambient phase runs on Marble's one shared
 * frame clock, and press feedback is the product-standard kinetic scale.
 *
 * MARBLE_FLOATING_BUTTONS_V205 — the critique that reshaped this control:
 *  1. **It was a doughnut, not a button.** The V202 pass wrapped the coloured face in a
 *     neutral chrome bezel — a 76 dp pale disc, a 1 dp hairline and a 60 dp colour inside it.
 *     Three concentric rings around one action: on the light page the pale bezel read as a
 *     dirty halo and the seam between bezel and face read as a defect. The floating-action
 *     grammar every platform ships is ONE filled disc in the action's own colour; that is what
 *     this is now.
 *  2. **The busy state never changed colour.** The tone was `if (busy) AmethystBright else
 *     Cyan` — and in BOTH the light and the dark brand palettes those two tokens hold the very
 *     same value, so the "colour" branch was dead. The face then followed the product's one
 *     semantic connect ramp, and from V210 it reads the theme's own action tokens instead (see
 *     below), which is the same promise kept by a palette that has to answer for it.
 *  3. **The glyph ink is measured, not assumed** — [marbleOnColor] scores white against the
 *     palette's dark ink on the live face and takes whichever clears 3:1.
 *
 * MARBLE_FLOATING_ACTIONS_V210 — the face is a token now. `connectButtonTone` is the semantic
 * ramp every connect control in the product shares, but a *floating* button's colour is a chrome
 * decision like its body and its shadow, so it reads [MarbleFloatActionTones] instead: armed is
 * `connect`, negotiating is `securing`, closing is `stop`. Daylight, Pure black and the phone's
 * own palette each answer all three, and the V205 defect — a busy state that held the armed
 * state's value — cannot come back, because the two are now two tokens a palette has to keep
 * apart.
 */
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
    val ink by animateColorAsState(
        targetValue = chrome.inkOn(tone),
        animationSpec = MarbleMotionSpecs.DockColor,
        label = "fab-ink"
    )

    Box(
        modifier = Modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        // The only ambient signal is painted inside the stable 88 dp slot. It cannot resize or
        // translate the button, and it freezes through MarbleMotion when the system disables motion.
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

        // One solid face in the semantic state colour. Depth comes from a shadow cast in the
        // face's own hue — never a grey or black smudge under a blue button — plus the faint
        // top-light wash every raised Marble surface carries.
        Box(
            modifier = Modifier
                .size(68.dp)
                .marblePopWhen(busy, peak = 1.05f)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tone.copy(alpha = .26f),
                    spotColor = tone.copy(alpha = .38f)
                )
                .clip(CircleShape)
                .background(tone)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .14f), Color.Transparent)
                    )
                )
                .kineticClickable(
                    pressScale = .94f,
                    boundedShape = CircleShape,
                    // The FAB is a direct manipulation control; its release is quick and
                    // damped so it never rebounds into the dock underneath it.
                    releaseSpec = MarbleMotionSpecs.ExitFloat
                ) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(HomeGlyph.POWER, ink, Modifier.size(30.dp))
        }
    }
}

/**
 * One circular secondary action of the Theme 2 split pair.
 *
 * MARBLE_FLOATING_BUTTONS_V205 — the same doughnut critique as the FAB: the chrome bezel around
 * a smaller colour disc is gone, the action is one filled disc in its own tone with its hue-cast
 * shadow, and the glyph ink is computed against that exact fill.
 *
 * MARBLE_FLOATING_ACTIONS_V210 — the tone arrives from the theme's own action token and the ink
 * is scored by the same chrome the sibling disc uses, so "stop" and "measure" are two colours
 * the palette guarantees are different and two glyphs the palette guarantees are readable.
 */
@Composable
private fun FloatingSplitAction(
    tone: Color,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    /** Receives the ink the palette scores for [tone], so a glyph can never be assumed white. */
    content: @Composable (ink: Color) -> Unit
) {
    val chrome = rememberMarbleFloatChrome()
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = tone.copy(alpha = .24f),
                spotColor = tone.copy(alpha = .34f)
            )
            .clip(CircleShape)
            .background(if (enabled) tone else tone.copy(alpha = .55f))
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
        content(chrome.inkOn(tone))
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
            // MARBLE_BANNER_IN_FLOW_V205 — the national-filtering alert occupies its own slot
            // under the header instead of floating over it.
            NationalEventBanner(repo = repo)
            // MARBLE_EXPRESSIVE_MOTION_V186 — Theme 2 arrives in a cascade: banner first,
            // then the expanded server card one stagger step behind it.
            IosStatusWideCard(evidence, actions, modifier = Modifier.marbleStaggerIn(1))
            IosServerListBox(
                repo = repo,
                evidence = evidence,
                actions = actions,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .marbleStaggerIn(2),
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
    // MARBLE_FLOATING_CONNECT_V202 — reserve the connected pair's full footprint even while
    // disconnected. The bottom action therefore stays at the same physical coordinate when a
    // connection completes; only an upper action fades in, never the whole control jumping.
    Box(
        modifier = modifier.size(width = 88.dp, height = 118.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = evidence.connected,
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                // Both states already occupy an identical, bottom-anchored box. A short
                // fade-through is deliberately the whole transition: no size interpolation, no
                // overshooting scale and no competing child measurements at connection time.
                expressiveFadeThrough()
            },
            label = "floating-split-anim"
        ) { isConnected ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                if (isConnected) {
                    // Split into TWO buttons: Disconnect (pause) and Ping. The lower action
                    // shares the disconnected FAB's anchor exactly.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    FloatingSplitAction(
                        // MARBLE_FLOATING_ACTIONS_V210 — the theme's own stop hue, not a
                        // hard-coded danger: in the dynamic palette the brand red used to sit
                        // unchanged on a wallpaper-coloured page.
                        tone = chrome.actions.stop,
                        description = Tr.now.disconnect,
                        onClick = { actions.onToggleConnection() }
                    ) { ink ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // MARBLE_FLOATING_CHROME_V201 — the pause bars read against the
                            // danger disc instead of assuming white survives it: the dark
                            // theme's danger is a light rose (#FF718B), and white bars on a
                            // light rose are nearly invisible. In V210 the ink is the one the
                            // palette scores for the theme's own stop hue.
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
                    // server this page is connected to. While it runs, the disc shows the wait
                    // instead of a stop square: a one-route probe is single-flight and there is
                    // nothing on the other side of that button to interrupt.
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
                busy -> {
                    // MARBLE_EXPRESSIVE_MOTION_V186 — the securing orbit stretches: the sweep
                    // oscillates on a second clock while the arc rotates, so the dial breathes
                    // the way the newest Android busy rings do.
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
                // MARBLE_ORBIT_REST_V190 — the resting orbit is four even, softer dashes with
                // equal gaps (60° arc / 30° gap) so it reads as one calm ring around the core,
                // not as broken segments of different weights.
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

        // Core disc — flat solid tone, kinetic press.
        Box(
            modifier = Modifier
                .size(86.dp)
                // MARBLE_EXPRESSIVE_MOTION_V186 — acknowledgement beat on the session flip.
                .marblePopWhen(connected, peak = 1.05f)
                // MARBLE_SLIDE_SHEEN_THEMED_V205 — the lift is cast in the disc's own hue; the
                // default black ambient read as a grey smudge under the core on the light page.
                .shadow(
                    elevation = 5.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = tone.copy(alpha = .24f),
                    spotColor = tone.copy(alpha = .40f)
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
            // MARBLE_FLOATING_CHROME_V201 — the glyph is chosen against the disc, not assumed white.
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
        // Top actions (outside the banner) + Status Bar
        HomeTopActionBar(evidence, actions, repo)
        // MARBLE_BANNER_IN_FLOW_V205 — the national-filtering alert occupies its own slot under
        // the header instead of floating over it.
        NationalEventBanner(repo = repo)
        // MARBLE_EXPRESSIVE_MOTION_V186 — Theme 3 arrives in a cascade: banner, orbital core,
        // caption, server card — one stagger step apart on the emphasized entrance pair.
        IosStatusWideCard(evidence, actions, modifier = Modifier.marbleStaggerIn(1))

        // Center: Orbital power core + caption (fixed height, never resizes with status text)
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

        // Bottom: Server List Box
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
    // MARBLE_EXPRESSIVE_MOTION_V186 — the customizer page cascades its modules in on arrival.
    val entranceArmed = rememberMarbleEntranceWindow()

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
            // MARBLE_BANNER_IN_FLOW_V205 — the national-filtering alert occupies its own slot
            // under the header instead of floating over it.
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
                        HomeShortcutDeck(evidence, actions, HomeCloud.Accent)
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
        // MARBLE_HOME_ONE_PING_V208 — the modular theme's ping companion is gone: the connect
        // control now owns the whole row and the header owns the page's one ping verb.
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
            // MARBLE_EXPRESSIVE_MOTION_V186 — the deck's ping disc releases on the bouncy spring.
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
        // MARBLE_ROUTE_ATELIER_V207 — the route presentation lives in its own file, on the same
        // evidence and the same shared widgets as the four above it.
        HomeStyle.ROUTE_ATELIER -> HomeThemeAtelier(
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
