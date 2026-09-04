@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

// MARBLE_HOME_STYLE_V110
// MARBLE_HOME_STYLE_IMMERSIVE_V111
//
// Four presentations of one connection surface.
//
// Design contract shared by every style — enforced by scripts/system-integrity-check.py:
//   1. all four render the SAME runtime evidence: node name, source name, IP + country flag with
//      its three actions (copy / refresh / details), session uptime, and the one-shot ping;
//   2. no style draws a quality indicator around the connect control;
//   3. the artwork is Canvas-drawn vector work — no bitmaps, no downloads, no per-card infinite
//      transitions (ambient motion comes from Marble's single shared frame clock).
//
// V111 — every style now owns the *entire* viewport: a full-bleed Canvas backdrop plus a layout
// that scales itself from the real available height, and every piece of shared evidence is
// re-skinned per style through [HomeFlavor], so the same facts read as four genuinely different
// products: an abyssal organism, a command deck, a nebula HUD and an architect's blueprint.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.AppRepository
import com.marbleng.app.ServerIntelInfo
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.ConnectButtonStyle
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ProAccent
import com.marbleng.app.model.ProShortcut
import com.marbleng.app.model.ProxyProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Shared evidence model
// ---------------------------------------------------------------------------------------------

/**
 * Everything the Home styles are allowed to show, resolved exactly once per composition.
 *
 * Styles receive this snapshot instead of the repository so no presentation can invent, round or
 * omit a value that another presentation shows differently.
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
    // MARBLE_CONNECT_BUTTON_V121 — tearing a tunnel down is not instantaneous; the control says so.
    val disconnecting: Boolean,
    val blocked: Boolean,
    val connectedSinceMs: Long,
    val pingMs: Int,
    val pingState: ConnectionPingState,
    val downBps: Long,
    val upBps: Long
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
        downBps = if (connected) repo.liveDownBps else 0L,
        upBps = if (connected) repo.liveUpBps else 0L
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
    // MARBLE_SIGNATURE_HOME_V112 — extra actions only the Signature studio surface uses. They are
    // safe no-ops in the four classic styles, which never render the controls that call them.
    val onConnectProfile: (ProxyProfile) -> Unit = {},
    val onAddRoute: () -> Unit = {},
    val onRank: () -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onRouting: () -> Unit = {},
    val onTests: () -> Unit = {}
)

/**
 * MARBLE_SIGNATURE_HOME_V112 — the Signature studio configuration resolved once per composition.
 *
 * The four classic styles receive only [HomeEvidence] and keep rendering exactly as before; the
 * Signature style additionally reads this snapshot so its layers (status banner, corner cluster,
 * shortcut and accent) can be individually customized from Settings.
 *
 * MARBLE_SIGNATURE_STUDIO_TRIM_V121 — the in-Home server rail and style switcher are gone: routes
 * are chosen on the Servers page and the presentation in Settings, so Home stays a single, calm
 * connection surface with nothing that duplicates another screen.
 */
internal data class HomeProContext(
    val showBanner: Boolean,
    val showCornerActions: Boolean,
    val shortcut: ProShortcut,
    val accent: ProAccent
)

/**
 * The per-style skin every shared evidence widget renders through. One flavor per Home style, so
 * node/source, the IP row, uptime and the ping element look hand-made for each presentation while
 * remaining the exact same facts and actions.
 */
internal enum class HomeFlavor { ORBIT, NEBULA, PRO }

/** The single source of truth for which presentation skin a [HomeStyle] renders through. */
internal fun homeFlavorFor(style: HomeStyle): HomeFlavor = when (style) {
    HomeStyle.PRO -> HomeFlavor.PRO
    HomeStyle.COSMIC_ORBIT -> HomeFlavor.ORBIT
    HomeStyle.COSMIC_IMMERSION -> HomeFlavor.NEBULA
}

@Composable
internal fun homeTone(evidence: HomeEvidence): Color = when {
    // MARBLE_DYNAMIC_COLOR_V117 — the connected surface follows the live measurement, not a
    // fixed green: a verified fast route glows cyan/emerald, a fair route warms to amber and a
    // slow route turns coral. Connecting and blocked keep their unmissable state signals.
    evidence.connected && evidence.pingState == ConnectionPingState.MEASURED && evidence.pingMs > 0 ->
        marbleMetricTone(pingMetricBand(evidence.pingMs))
    evidence.connected -> Aether.Emerald
    evidence.connecting -> Aether.Amethyst
    evidence.disconnecting -> Aether.Amber
    evidence.blocked -> Aether.Danger
    else -> Aether.Cyan
}

/**
 * The connected-state identity of one Home presentation. Every style owns its palette: the
 * connecting arc and the connected ring of the power control follow the presentation's own
 * accent instead of always turning generic green, and every Aether token below is theme-aware
 * (Light / AMOLED / phone-dynamic), so the ring changes with the theme too.
 */
@Composable
internal fun styleConnectedTone(flavor: HomeFlavor): Color = when (flavor) {
    HomeFlavor.PRO -> Aether.Cyan
    HomeFlavor.ORBIT -> Aether.Amber
    HomeFlavor.NEBULA -> Aether.AmethystBright
}

/** State tone of a Home presentation: state-driven while busy/blocked, style-owned when live. */
@Composable
internal fun styleStateTone(flavor: HomeFlavor, evidence: HomeEvidence): Color = when {
    evidence.connected -> styleConnectedTone(flavor)
    evidence.connecting -> Aether.Amethyst
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
        else -> t.readyToConnect
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

/** `1:04:09` / `07:31`. Ticks once a second and only while a session is actually running. */
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

/**
 * MARBLE_PING_FIXED_GEOMETRY_V114 — the ping *value* is only ever digit-shaped.
 *
 * The readout is an instrument with a fixed box: a word like "measuring…" or "بدون پاسخ" is
 * longer than "123 ms", so rendering it in the value slot resized the box on every state change.
 * Words now live in the reserved hint slot below ([homePingActionHint]) and the value slot keeps
 * one constant glyph budget — the measured number, three dots while the probe is in flight, or an
 * em dash when the route has never been measured.
 */
@Composable
internal fun homePingLabel(evidence: HomeEvidence): String {
    val t = Tr.now
    return when (evidence.pingState) {
        ConnectionPingState.MEASURING -> t.pingMeasuringValue
        ConnectionPingState.MEASURED -> "${evidence.pingMs} ms"
        // MARBLE_PING_GUARANTEE_V114 — the repository ladder cannot answer "no response" while a
        // tunnel carries traffic, so a failure only ever means "nothing measured on this route yet".
        ConnectionPingState.FAILED -> t.pingIdleValue
        ConnectionPingState.IDLE -> t.pingIdleValue
    }
}

@Composable
internal fun homePingTone(evidence: HomeEvidence, fallback: Color): Color =
    when (evidence.pingState) {
        ConnectionPingState.MEASURED -> marbleMetricTone(pingMetricBand(evidence.pingMs))
        ConnectionPingState.FAILED -> Aether.Danger
        else -> fallback
    }

/** Ping element hint under the value: what a tap will do right now. */
@Composable
internal fun homePingActionHint(evidence: HomeEvidence): String {
    val t = Tr.now
    return when {
        !evidence.connected -> ""
        evidence.pingState == ConnectionPingState.MEASURING -> t.measuring
        evidence.pingState == ConnectionPingState.MEASURED -> t.retestPing
        else -> t.testPing
    }
}

internal fun homePingTappable(evidence: HomeEvidence): Boolean =
    evidence.connected && evidence.pingState != ConnectionPingState.MEASURING

/** Deterministic 0..1 noise so particles/stars need no random state and no allocations. */
private fun hash01(seed: Int): Float {
    val s = sin(seed * 12.9898f + 78.233f) * 43758.5453f
    return s - floor(s)
}

/**
 * MARBLE_SEAMLESS_LOOPS_V112 — smooth 0..1 envelope that is zero at both ends of a loop.
 *
 * Every effect that wraps around (ripples, travelling pulses, drifting motes, scan lines) is
 * multiplied by this fade so the frame where the loop restarts is visually identical to the frame
 * before it started: elements are fully transparent exactly when they teleport back. The user can
 * never tell where an animation begins or ends.
 */
internal fun loopFade(t: Float): Float = sin((t.coerceIn(0f, 1f)) * PI.toFloat())

/**
 * MARBLE_HOME_PING_AUTOFIT_V112 / MARBLE_PING_FIXED_GEOMETRY_V114 — one value renderer for every
 * stat readout (uptime, ping).
 *
 * V112 shrank the type to fit long state words. V114 removes the reason to shrink: state words no
 * longer enter the value slot ([homePingLabel] only emits digit-shaped glyphs), so the face is now
 * locked by default. A locked value keeps one constant optical size across every state change,
 * which is what makes the ping box read as a real instrument instead of a label that breathes.
 *
 * [autoFit] stays available for readouts that must survive an arbitrarily long string in a narrow
 * box; it is opt-in now, never the default. Digits render with tabular figures so `9 ms → 10 ms`
 * does not shift the following glyphs sideways.
 *
 * The face follows the product typeface chosen in Settings: by default [fontFamily] is null, so the
 * value inherits the family already baked into [baseStyle] by AetherTheme.
 */
@Composable
internal fun HomeStatValueText(
    value: String,
    tone: Color,
    baseStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    weight: FontWeight = FontWeight.Bold,
    sizeScale: Float = 1f,
    autoFit: Boolean = false,
    textAlign: TextAlign? = null
) {
    // Length-driven auto-shrink: full size up to 7 glyphs, then two quieter steps. Opt-in only —
    // the stat pair reserves a fixed slot instead (see [homeStatValueStyle]).
    val fitted = if (!autoFit) {
        baseStyle
    } else {
        when {
            value.length <= 7 -> baseStyle
            value.length <= 10 -> baseStyle.copy(fontSize = baseStyle.fontSize * .86f)
            else -> baseStyle.copy(fontSize = baseStyle.fontSize * .70f)
        }
    }
    // A fixed line box + tabular digits: the measured geometry of the value never depends on the
    // characters currently inside it. This is the exact style [HomeStatValueSlot] reserves room for.
    val sized = homeStatValueStyle(fitted, sizeScale)
    Text(
        value,
        color = tone,
        style = if (fontFamily == null) sized else sized.copy(fontFamily = fontFamily),
        fontWeight = weight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
    )
}

/**
 * MARBLE_PING_FIXED_GEOMETRY_V114 — the fixed slot every stat value lives in.
 *
 * Height is reserved by the slot, never by the glyphs, so the uptime and ping instruments of a
 * style are pixel-identical boxes in every state: idle, measuring, measured or reconnecting.
 */
@Composable
internal fun HomeStatValueSlot(
    value: String,
    tone: Color,
    baseStyle: androidx.compose.ui.text.TextStyle,
    height: Dp,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    weight: FontWeight = FontWeight.Bold,
    sizeScale: Float = 1f,
    align: Alignment = Alignment.CenterStart
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = align
    ) {
        HomeStatValueText(
            value = value,
            tone = tone,
            baseStyle = baseStyle,
            // A centred slot must also centre its glyphs, otherwise the fillMaxWidth box pins the
            // value to the leading edge of an otherwise centred instrument.
            modifier = Modifier.fillMaxWidth(),
            fontFamily = fontFamily,
            weight = weight,
            sizeScale = sizeScale,
            textAlign = if (align == Alignment.Center) TextAlign.Center else null
        )
    }
}

/**
 * MARBLE_PING_FIXED_GEOMETRY_V114 — the reserved one-line word slot under a stat value.
 *
 * Both instruments of a pair render this slot even when they have nothing to say, so a hint
 * appearing on the ping can never make it taller than the uptime beside it.
 */
@Composable
internal fun HomeStatHintSlot(
    hint: String,
    height: Dp = 15.dp,
    tone: Color = Aether.Cyan,
    uppercase: Boolean = false,
    monospace: Boolean = false,
    align: Alignment = Alignment.CenterStart,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = align
    ) {
        if (hint.isNotBlank()) {
            val base = MaterialTheme.typography.labelSmall
            Text(
                if (uppercase) hint.uppercase() else hint,
                color = tone,
                // Never pass a null family into copy(): that would drop the themed face (Vazir for
                // Persian) instead of inheriting it.
                style = if (monospace) base.copy(fontFamily = FontFamily.Monospace) else base,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared evidence widgets
// ---------------------------------------------------------------------------------------------

/** Compact icon set owned by the Home styles so they never depend on a font or a drawable. */
internal enum class HomeGlyph {
    POWER, CHECK, RESET, COPY, REFRESH, MORE, PULSE, CLOCK, LIBRARY,
    // MARBLE_SIGNATURE_HOME_V112 — corner-action glyphs for the Signature studio.
    PLUS, BOLT
}

@Composable
internal fun HomeGlyphIcon(glyph: HomeGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (size.minDimension * .095f).coerceIn(1.3f, 3.2f)
        // MARBLE_ICON_POLISH_V115 — rounded joins everywhere: miter corners spike on small
        // canvas glyphs, rounded joins read as one continuous modern stroke.
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
                drawLine(color, Offset(w * .5f, h * .10f), Offset(w * .5f, h * .46f), stroke, StrokeCap.Round)
            }
            HomeGlyph.CHECK -> drawPath(
                Path().apply {
                    moveTo(w * .20f, h * .53f)
                    lineTo(w * .42f, h * .74f)
                    lineTo(w * .80f, h * .27f)
                },
                color,
                style = line
            )
            HomeGlyph.RESET -> {
                drawArc(
                    color = color,
                    startAngle = 40f,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawLine(color, Offset(w * .80f, h * .30f), Offset(w * .84f, h * .10f), stroke, StrokeCap.Round)
            }
            HomeGlyph.COPY -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * .32f, h * .16f),
                    size = Size(w * .50f, h * .52f),
                    cornerRadius = CornerRadius(w * .10f),
                    style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(w * .66f, h * .82f)
                        lineTo(w * .20f, h * .82f)
                        lineTo(w * .20f, h * .32f)
                    },
                    color,
                    style = line
                )
            }
            HomeGlyph.REFRESH -> {
                drawArc(
                    color = color,
                    startAngle = 120f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawPath(
                    Path().apply {
                        moveTo(w * .28f, h * .58f)
                        lineTo(w * .20f, h * .80f)
                        lineTo(w * .44f, h * .76f)
                    },
                    color,
                    style = line
                )
            }
            HomeGlyph.MORE -> listOf(.26f, .50f, .74f).forEach { x ->
                drawCircle(color, radius = stroke * .82f, center = Offset(w * x, h * .5f))
            }
            HomeGlyph.PULSE -> drawPath(
                Path().apply {
                    moveTo(w * .10f, h * .52f)
                    lineTo(w * .32f, h * .52f)
                    lineTo(w * .44f, h * .24f)
                    lineTo(w * .58f, h * .78f)
                    lineTo(w * .69f, h * .52f)
                    lineTo(w * .90f, h * .52f)
                },
                color,
                style = line
            )
            HomeGlyph.CLOCK -> {
                drawCircle(color, radius = w * .34f, center = Offset(w * .5f, h * .5f), style = line)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .5f, h * .28f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .68f, h * .58f), stroke, StrokeCap.Round)
            }
            HomeGlyph.LIBRARY -> listOf(.28f, .50f, .72f).forEach { y ->
                drawLine(color, Offset(w * .20f, h * y), Offset(w * .80f, h * y), stroke, StrokeCap.Round)
            }
            HomeGlyph.PLUS -> {
                drawLine(color, Offset(w * .5f, h * .18f), Offset(w * .5f, h * .82f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .18f, h * .5f), Offset(w * .82f, h * .5f), stroke, StrokeCap.Round)
            }
            HomeGlyph.BOLT -> drawPath(
                Path().apply {
                    moveTo(w * .58f, h * .10f)
                    lineTo(w * .24f, h * .56f)
                    lineTo(w * .47f, h * .56f)
                    lineTo(w * .40f, h * .90f)
                    lineTo(w * .76f, h * .42f)
                    lineTo(w * .53f, h * .42f)
                    close()
                },
                color,
                style = line
            )
        }
    }
}

/**
 * Node + source identity, skinned per style.
 *
 * Node and source are always labelled, so a duplicated node name inside two subscriptions can
 * still be told apart at a glance.
 */
@Composable
internal fun HomeIdentityBlock(
    evidence: HomeEvidence,
    tone: Color,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val nodeValue = evidence.nodeName.ifBlank { t.chooseRoute }
    val sourceValue = evidence.sourceName.ifBlank { "—" }
    when (flavor) {

        HomeFlavor.ORBIT -> Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Instrument readout rows: fixed mono labels, dotted leader, right-anchored values.
            OrbitReadoutRow(label = t.node, value = nodeValue, tone = tone)
            OrbitReadoutRow(label = t.source, value = sourceValue, tone = Aether.InkMuted)
        }

        HomeFlavor.NEBULA -> Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Weightless HUD identity: airy letterspaced label over a luminous value.
            Text(
                t.node.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.2.sp),
                maxLines = 1
            )
            Text(
                nodeValue,
                color = tone,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            NebulaHairline(tone)
            Text(
                "${t.source.uppercase()}  •  $sourceValue",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }


        HomeFlavor.PRO -> Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Signature identity: quiet labeled rows with a breathing accent spine. The spine's
            // glow follows the shared clock, so the block always feels alive but never jumps.
            SignatureIdentityRow(label = t.node, value = nodeValue, tone = tone)
            SignatureIdentityRow(label = t.source, value = sourceValue, tone = Aether.InkMuted)
        }
    }
}

@Composable
private fun SignatureIdentityRow(label: String, value: String, tone: Color) {
    val glow = MarbleMotion.current.breathe(3_600)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Aether.VoidElevated.copy(alpha = .92f))
            .border(1.dp, tone.copy(alpha = .20f), RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(tone.copy(alpha = .40f + .45f * glow), tone.copy(alpha = .12f))
                    )
                )
        )
        Text(
            label.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            value,
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OrbitReadoutRow(label: String, value: String, tone: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        // Dotted leader between the label and the value: pure dashboard language.
        Canvas(
            Modifier
                .weight(1f)
                .height(1.dp)
        ) {
            drawLine(
                color = tone.copy(alpha = .30f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f))
            )
        }
        Text(
            value,
            color = tone,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NebulaHairline(tone: Color) {
    Canvas(
        Modifier
            .width(180.dp)
            .height(8.dp)
    ) {
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, tone.copy(alpha = .55f), Color.Transparent)
            ),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}

/**
 * IP address + country flag + the three actions (copy, refresh, complete details), skinned per
 * style but always the same three actions in the same order.
 *
 * The flag is the country emoji resolved from the public endpoint lookup; when no lookup has
 * landed yet the node's own leading flag glyph is used, and a two-letter country code is the last
 * resort so the row never collapses.
 */
@Composable
internal fun HomeIpRow(
    evidence: HomeEvidence,
    tone: Color,
    actions: HomeActions,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val ipText = when {
        evidence.ip.isNotBlank() -> evidence.ip
        evidence.ipLoading -> t.resolving
        else -> t.unavailable
    }
    val badge = evidence.flag.ifBlank { evidence.countryCode.uppercase() }

    when (flavor) {

        HomeFlavor.ORBIT -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    t.ipAddress.uppercase(),
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    ),
                    fontWeight = FontWeight.Bold
                )
                if (evidence.location.isNotBlank()) {
                    Text(
                        "• ${evidence.location}",
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Aether.Glass)
                    .border(1.dp, tone.copy(alpha = .22f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (badge.isNotBlank()) {
                    Text(badge, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                Text(
                    ipText,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                HomeIpActionCluster(
                    tone,
                    actions,
                    buttonShape = RoundedCornerShape(9.dp),
                    buttonSize = 30.dp
                )
            }
        }

        HomeFlavor.NEBULA -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Aether.VoidElevated.copy(alpha = .55f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(tone.copy(alpha = .34f), Aether.Amethyst.copy(alpha = .26f))
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (badge.isNotBlank()) {
                Text(badge, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Text(
                ipText,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .6.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Ethereal outline-only controls keep the HUD weightless.
            HomeIpActionCluster(
                tone,
                actions,
                buttonShape = CircleShape,
                buttonSize = 30.dp,
                outlined = true
            )
        }


        HomeFlavor.PRO -> Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Aether.VoidElevated.copy(alpha = .92f))
                .border(1.dp, tone.copy(alpha = .24f), RoundedCornerShape(16.dp))
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Signature IP capsule: a flag chip with a live pulsing dot, then the address.
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tone.copy(alpha = .12f))
                    .border(1.dp, tone.copy(alpha = .32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(badge.ifBlank { "•" }, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    ipText,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (evidence.location.isNotBlank()) {
                    Text(
                        evidence.location,
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HomeIpActionCluster(
                tone,
                actions,
                buttonShape = RoundedCornerShape(10.dp),
                buttonSize = 31.dp
            )
        }
    }
}

/** The three IP actions — copy, refresh, complete details — identical order in every skin. */
@Composable
private fun HomeIpActionCluster(
    tone: Color,
    actions: HomeActions,
    buttonShape: androidx.compose.ui.graphics.Shape,
    buttonSize: Dp,
    outlined: Boolean = false
) {
    val t = Tr.now
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        HomeMicroButton(actions.onCopyIp, tone, buttonShape, buttonSize, outlined, t.copyIp) {
            HomeGlyphIcon(HomeGlyph.COPY, Aether.InkMuted, Modifier.size(buttonSize * .48f))
        }
        HomeMicroButton(actions.onRefreshIp, tone, buttonShape, buttonSize, outlined, t.refreshIp) {
            HomeGlyphIcon(HomeGlyph.REFRESH, Aether.InkMuted, Modifier.size(buttonSize * .48f))
        }
        HomeMicroButton(actions.onIpDetails, tone, buttonShape, buttonSize, outlined, t.ipDetails) {
            HomeGlyphIcon(HomeGlyph.MORE, Aether.InkMuted, Modifier.size(buttonSize * .48f))
        }
    }
}

@Composable
private fun HomeMicroButton(
    onClick: () -> Unit,
    tone: Color,
    shape: androidx.compose.ui.graphics.Shape,
    size: Dp,
    outlined: Boolean,
    descriptiveLabel: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (outlined) Color.Transparent else Aether.GlassStrong.copy(alpha = .40f))
            .border(1.dp, tone.copy(alpha = if (outlined) .44f else .22f), shape)
            .semantics { contentDescription = descriptiveLabel }
            .kineticClickable(role = Role.Button, pressScale = .90f, boundedShape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * MARBLE_PING_FIXED_GEOMETRY_V114 — the measured contract of a style's two instruments.
 *
 * Uptime and ping are rendered as a *pair of identical boxes*: same width (both `weight(1f)` in the
 * same Row), same height, same reserved slots. Nothing about the ping depends on its own state any
 * more — a probe starting, landing or being re-run cannot move a single pixel of the layout, in any
 * language and at any font scale. Slots are derived from the live typography through the density, so
 * accessibility font scaling still fits instead of clipping.
 */
internal class HomeStatMetrics(
    val cellHeight: Dp,
    val headerSlot: Dp,
    val valueSlot: Dp,
    val meterSlot: Dp,
    val hintSlot: Dp,
    val spacing: Dp,
    val dialSize: Dp
)

/** The value face of a style: each instrument keeps its own optical size. */
@Composable
private fun homeStatValueBaseStyle(flavor: HomeFlavor): androidx.compose.ui.text.TextStyle =
    when (flavor) {
        HomeFlavor.PRO -> MaterialTheme.typography.titleLarge
        HomeFlavor.ORBIT -> MaterialTheme.typography.headlineSmall
        HomeFlavor.NEBULA -> MaterialTheme.typography.labelLarge
    }

/** One resolved value style, shared by the renderer and the slot that reserves its height. */
@Composable
internal fun homeStatValueStyle(
    baseStyle: androidx.compose.ui.text.TextStyle,
    sizeScale: Float
): androidx.compose.ui.text.TextStyle {
    val scaled = if (sizeScale == 1f) {
        baseStyle
    } else {
        baseStyle.copy(fontSize = baseStyle.fontSize * sizeScale)
    }
    return scaled.copy(lineHeight = scaled.fontSize * 1.30f, fontFeatureSettings = "tnum")
}

@Composable
private fun rememberHomeStatMetrics(flavor: HomeFlavor, valueScale: Float): HomeStatMetrics {
    val labelStyle = MaterialTheme.typography.labelSmall
    val headerSlot = anchoredTextBlockHeight(labelStyle, 1) + 4.dp
    val hintSlot = anchoredTextBlockHeight(labelStyle, 1)
    val valueSlot = anchoredTextBlockHeight(homeStatValueStyle(homeStatValueBaseStyle(flavor), valueScale), 1)
    val dialSize = 92.dp
    // The orbit pair is sized by whichever of its two instruments needs more room: the LCD
    // odometer (label + digits + reserved hint line) or the arc gauge (48.dp dial minimum). Both
    // are computed here rather than inside the when branch, so the branch stays a plain
    // expression and the two candidates stay visible to the caller.
    val orbitOdometerCell = 20.dp + headerSlot + valueSlot + 4.dp + hintSlot + 12.dp
    val orbitGaugeCell = 20.dp + maxOf(headerSlot + valueSlot + hintSlot + 6.dp, 48.dp)

    return when (flavor) {
        HomeFlavor.PRO -> HomeStatMetrics(
            cellHeight = 20.dp + headerSlot + valueSlot + 5.dp + hintSlot + 12.dp,
            headerSlot = headerSlot,
            valueSlot = valueSlot,
            meterSlot = 5.dp,
            hintSlot = hintSlot,
            spacing = 4.dp,
            dialSize = dialSize
        )


        HomeFlavor.ORBIT -> HomeStatMetrics(
            cellHeight = maxOf(orbitOdometerCell, orbitGaugeCell),
            headerSlot = headerSlot,
            valueSlot = valueSlot,
            meterSlot = 4.dp,
            hintSlot = hintSlot,
            spacing = 4.dp,
            dialSize = 48.dp
        )

        HomeFlavor.NEBULA -> HomeStatMetrics(
            cellHeight = 12.dp + dialSize + headerSlot + 6.dp,
            headerSlot = headerSlot,
            valueSlot = valueSlot,
            meterSlot = 0.dp,
            hintSlot = hintSlot,
            spacing = 6.dp,
            dialSize = dialSize
        )

    }
}

/**
 * Uptime + the tappable one-shot ping, skinned per style. Tapping the ping element always runs a
 * fresh, real tunnel measurement (never cached) while a session is live.
 *
 * MARBLE_PING_FIXED_GEOMETRY_V114 — both instruments of a pair are laid out from one
 * [HomeStatMetrics], so the ping box is exactly the uptime box: same width, same height, same
 * reserved word slot. The state lives in the *content* of those slots, never in their geometry.
 */
@Composable
internal fun HomeSessionStats(
    evidence: HomeEvidence,
    tone: Color,
    actions: HomeActions,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val uptime = rememberUptimeLabel(evidence.connectedSinceMs)
    val uptimeValue = if (evidence.connectedSinceMs > 0L) uptime else t.pingIdleValue
    val pingValue = homePingLabel(evidence)
    val pingTone = homePingTone(evidence, tone)
    val hint = homePingActionHint(evidence)
    val tappable = homePingTappable(evidence)
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    val valueScale = 0.86f
    val metrics = rememberHomeStatMetrics(flavor, valueScale)
    // The pair shares one height, so the two boxes are pixel-identical in every state. Each Row
    // below builds the cell modifier inside its own scope, because `weight` only exists there.
    val pingFill = when {
        evidence.pingState == ConnectionPingState.MEASURED ->
            1f - (evidence.pingMs / 500f).coerceIn(0f, .92f)
        else -> 0f
    }

    when (flavor) {

        HomeFlavor.ORBIT -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val cell = Modifier.weight(1f).height(metrics.cellHeight)
            // Digital odometer for the session clock…
            OrbitOdometer(
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                metrics = metrics,
                modifier = cell
            )
            // …and a real arc gauge for the tunnel latency.
            OrbitPingGauge(
                evidence = evidence,
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                metrics = metrics,
                hint = hint,
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = cell,
                valueWeight = FontWeight.Light,
                valueSizeScale = valueScale
            )
        }

        HomeFlavor.NEBULA -> Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cell = Modifier.weight(1f).height(metrics.cellHeight)
            NebulaStatRing(
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                metrics = metrics,
                rotating = evidence.connected,
                modifier = cell
            )
            NebulaStatRing(
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                metrics = metrics,
                rotating = measuring,
                fillFraction = pingFill,
                hint = hint,
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = cell,
                valueWeight = FontWeight.Light,
                valueSizeScale = valueScale
            )
        }


        HomeFlavor.PRO -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val cell = Modifier.weight(1f).height(metrics.cellHeight)
            SignatureStatCell(
                label = t.uptime,
                value = uptimeValue,
                tone = tone,
                glyph = HomeGlyph.CLOCK,
                metrics = metrics,
                live = evidence.connected,
                modifier = cell
            )
            SignatureStatCell(
                label = t.connectionPing,
                value = pingValue,
                tone = pingTone,
                glyph = HomeGlyph.PULSE,
                metrics = metrics,
                live = measuring,
                fillFraction = pingFill,
                hint = hint,
                onClick = actions.onTestPing.takeIf { tappable },
                modifier = cell,
                valueWeight = FontWeight.Light,
                valueSizeScale = valueScale
            )
        }
    }
}

/**
 * MARBLE_SIGNATURE_HOME_V112 / MARBLE_PING_FIXED_GEOMETRY_V114 — the Signature studio instrument.
 *
 * A quiet professional readout: hairline frame, one specular edge, a letterspaced caption, the
 * locked value slot, a graded latency underlay and the reserved word slot. While a probe is in
 * flight a single soft sheen crosses the cell and fades out at both ends, so its loop is seamless.
 */
@Composable
private fun SignatureStatCell(
    label: String,
    value: String,
    tone: Color,
    glyph: HomeGlyph,
    metrics: HomeStatMetrics,
    live: Boolean,
    modifier: Modifier = Modifier,
    fillFraction: Float = 0f,
    hint: String = "",
    onClick: (() -> Unit)? = null,
    valueWeight: FontWeight = FontWeight.Bold,
    valueSizeScale: Float = 1f
) {
    val fill by animateFloatAsState(
        targetValue = fillFraction.coerceIn(0f, 1f),
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "signature-stat-fill"
    )
    val motion = MarbleMotion.current
    val sheen = motion.loop(2_400)
    val breathe = motion.breathe(3_200)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .92f))
            // One specular edge + a tone wash from the leading corner: depth without a second shadow.
            .background(
                Brush.linearGradient(
                    listOf(
                        tone.copy(alpha = if (live) .10f + .05f * breathe else .05f),
                        Color.Transparent,
                        Color.White.copy(alpha = .02f)
                    )
                )
            )
            .border(1.dp, tone.copy(alpha = if (live) .34f else .20f), shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (live) {
            // MARBLE_SEAMLESS_LOOPS_V112 — the sheen is fully transparent at both ends of its travel.
            Canvas(Modifier.matchParentSize()) {
                val fade = loopFade(sheen)
                if (fade > 0f) {
                    val x = size.width * sheen
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, tone.copy(alpha = .13f * fade), Color.Transparent),
                            startX = x - size.width * .22f,
                            endX = x + size.width * .22f
                        )
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.spacing, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.headerSlot),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HomeGlyphIcon(glyph, tone, Modifier.size(11.dp))
                    Text(
                        label.uppercase(),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (live) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(tone.copy(alpha = .55f + .40f * breathe))
                        )
                    }
                }
            }
            HomeStatValueSlot(
                value = value,
                tone = tone,
                baseStyle = homeStatValueBaseStyle(HomeFlavor.PRO),
                height = metrics.valueSlot,
                modifier = Modifier.fillMaxWidth(),
                weight = valueWeight,
                sizeScale = valueSizeScale
            )
            // Latency health underlay: a thin graded bar that settles with the measurement.
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(metrics.meterSlot)
            ) {
                val y = size.height / 2f
                drawLine(
                    color = tone.copy(alpha = .16f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f))
                )
                if (fill > 0f) {
                    drawLine(
                        color = tone,
                        start = Offset(0f, y),
                        end = Offset(size.width * fill, y),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = tone.copy(alpha = .30f),
                        radius = 3.4.dp.toPx(),
                        center = Offset(size.width * fill, y)
                    )
                }
            }
            HomeStatHintSlot(hint = hint, height = metrics.hintSlot, tone = Aether.Cyan)
        }
    }
}

/** Cosmic Orbit instrument (uptime) — a cockpit LCD odometer over a dashed segment baseline. */
@Composable
private fun OrbitOdometer(
    label: String,
    value: String,
    tone: Color,
    metrics: HomeStatMetrics,
    modifier: Modifier = Modifier,
    valueWeight: FontWeight = FontWeight.Bold,
    valueSizeScale: Float = 1f
) {
    val shape = RoundedCornerShape(14.dp)
    val scan = MarbleMotion.current.loop(3_600)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Aether.Glass)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .035f), Color.Transparent, tone.copy(alpha = .05f))
                )
            )
            .border(1.dp, tone.copy(alpha = .22f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.spacing, Alignment.CenterVertically)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.headerSlot),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    HomeGlyphIcon(HomeGlyph.CLOCK, Aether.InkFaint, Modifier.size(11.dp))
                    Text(
                        label.uppercase(),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // MARBLE_HOME_PING_AUTOFIT_V112 / V114 — the odometer digits sit in a locked slot.
            HomeStatValueSlot(
                value = value,
                tone = tone,
                baseStyle = homeStatValueBaseStyle(HomeFlavor.ORBIT),
                height = metrics.valueSlot,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = FontFamily.Monospace,
                weight = valueWeight,
                sizeScale = valueSizeScale
            )
            // Segment baseline under the digits, like a cockpit LCD, with one travelling highlight.
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(metrics.meterSlot)
            ) {
                val y = size.height / 2f
                drawLine(
                    color = tone.copy(alpha = .40f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 5f))
                )
                // MARBLE_SEAMLESS_LOOPS_V112 — the highlight fades out at both ends of the rule.
                val fade = loopFade(scan)
                if (fade > 0f) {
                    val x = size.width * scan
                    drawLine(
                        color = tone.copy(alpha = .85f * fade),
                        start = Offset(x - 7.dp.toPx(), y),
                        end = Offset(x + 7.dp.toPx(), y),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round
                    )
                }
            }
            HomeStatHintSlot(hint = "", height = metrics.hintSlot, monospace = true)
        }
    }
}

/**
 * Cosmic Orbit instrument (ping) — a real arc gauge: graduated dial, a radar sweep while the probe is in
 * flight and a needle that physically travels to the measured latency (0..500 ms full scale).
 */
@Composable
private fun OrbitPingGauge(
    evidence: HomeEvidence,
    label: String,
    value: String,
    tone: Color,
    metrics: HomeStatMetrics,
    hint: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    valueWeight: FontWeight = FontWeight.Bold,
    valueSizeScale: Float = 1f
) {
    val measuring = evidence.pingState == ConnectionPingState.MEASURING
    // MARBLE_SEAMLESS_LOOPS_V112 — the radar sweep rotates a full 360° so it never snaps back.
    val sweepPhase = MarbleMotion.current.loop(1_100)
    // The needle physically travels to the measured latency (0..500 ms full-scale).
    val needle by animateFloatAsState(
        targetValue = when {
            evidence.pingState == ConnectionPingState.MEASURED ->
                (evidence.pingMs / 500f).coerceIn(0f, 1f)
            else -> 0f
        },
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "orbit-ping-needle"
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Aether.Glass)
            .background(
                Brush.linearGradient(
                    listOf(
                        tone.copy(alpha = if (measuring) .10f else .05f),
                        Color.Transparent
                    )
                )
            )
            .border(1.dp, tone.copy(alpha = if (measuring) .38f else .24f), shape)
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            )
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Canvas(Modifier.size(metrics.dialSize)) {
                val stroke = 3.4.dp.toPx()
                val inset = stroke
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                // Dial track.
                drawArc(
                    color = tone.copy(alpha = .18f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // Dial graduations.
                repeat(7) { index ->
                    val angle = (135f + index * 45f) * PI.toFloat() / 180f
                    val r1 = size.minDimension / 2f - stroke * 2.1f
                    val r2 = size.minDimension / 2f - stroke * 1.2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawLine(
                        color = tone.copy(alpha = .40f),
                        start = Offset(c.x + cos(angle) * r1, c.y + sin(angle) * r1),
                        end = Offset(c.x + cos(angle) * r2, c.y + sin(angle) * r2),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
                if (measuring) {
                    // Radar-style sweep while the probe is in flight — a continuous full-circle
                    // rotation, so the moment the loop restarts is invisible.
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, tone.copy(alpha = .25f), tone),
                            center = Offset(size.width / 2f, size.height / 2f)
                        ),
                        startAngle = sweepPhase * 360f,
                        sweepAngle = 74f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                } else if (needle > 0f) {
                    drawArc(
                        color = tone,
                        startAngle = 135f,
                        sweepAngle = 270f * needle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    val headAngle = (135f + 270f * needle) * PI.toFloat() / 180f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f - stroke
                    drawCircle(
                        color = tone.copy(alpha = .32f),
                        radius = 4.6.dp.toPx(),
                        center = Offset(c.x + cos(headAngle) * r, c.y + sin(headAngle) * r)
                    )
                }
                // Hub.
                drawCircle(color = tone.copy(alpha = .55f), radius = 2.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.headerSlot),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        label.uppercase(),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // MARBLE_HOME_PING_AUTOFIT_V112 / V114 — locked value slot, monospace digits.
                HomeStatValueSlot(
                    value = value,
                    tone = tone,
                    baseStyle = homeStatValueBaseStyle(HomeFlavor.ORBIT),
                    height = metrics.valueSlot,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = FontFamily.Monospace,
                    weight = valueWeight,
                    sizeScale = valueSizeScale
                )
                HomeStatHintSlot(hint = hint, height = metrics.hintSlot, monospace = true)
            }
        }
    }
}

/**
 * Cosmic Immersion instrument — a nebula ring: an orbiting dash constellation, an aurora band that settles to
 * the measured latency, starfield specks inside the glass and the locked value/word slots at its
 * heart. The same ring renders uptime, so the pair is identical by construction.
 */
@Composable
private fun NebulaStatRing(
    label: String,
    value: String,
    tone: Color,
    metrics: HomeStatMetrics,
    rotating: Boolean,
    modifier: Modifier = Modifier,
    fillFraction: Float = 0f,
    hint: String = "",
    onClick: (() -> Unit)? = null,
    valueWeight: FontWeight = FontWeight.Bold,
    valueSizeScale: Float = 1f
) {
    val motion = MarbleMotion.current
    val spin = motion.loop(7_400)
    val fastSpin = motion.loop(1_200)
    val twinkle = motion.loop(4_800)
    val fill by animateFloatAsState(
        targetValue = fillFraction,
        animationSpec = MarbleMotionSpecs.HeroFloat,
        label = "nebula-ring-fill"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (onClick == null) Modifier
                else Modifier.kineticClickable(
                    role = Role.Button,
                    boundedShape = RoundedCornerShape(22.dp),
                    onClick = onClick
                )
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.spacing, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier.size(metrics.dialSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 2.2.dp.toPx()
                val r = size.minDimension / 2f - stroke
                val c = Offset(size.width / 2f, size.height / 2f)
                // Nebula wash inside the glass.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tone.copy(alpha = .16f), Color.Transparent),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
                // Starfield specks: sin-driven twinkle is periodic, so the loop has no seam.
                repeat(9) { index ->
                    val angle = hash01(index * 7 + 3) * 2f * PI.toFloat()
                    val distance = r * (.30f + .55f * hash01(index * 11 + 5))
                    val shimmer = .35f + .65f * (.5f + .5f * sin((twinkle + hash01(index * 13 + 1)) * 2f * PI.toFloat()))
                    drawCircle(
                        color = tone.copy(alpha = .42f * shimmer),
                        radius = (.7f + hash01(index * 17 + 9)).dp.toPx(),
                        center = Offset(c.x + cos(angle) * distance, c.y + sin(angle) * distance)
                    )
                }
                // Ethereal base ring.
                drawCircle(
                    color = tone.copy(alpha = .16f),
                    radius = r,
                    style = Stroke(width = stroke)
                )
                // Slowly orbiting dash constellation — a whole turn per period, so it never snaps.
                rotate(if (rotating) fastSpin * 360f else spin * 360f, pivot = c) {
                    drawCircle(
                        color = tone.copy(alpha = if (rotating) .75f else .35f),
                        radius = r,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 26f))
                        )
                    )
                }
                // The measured band fills the ring like an aurora settling.
                if (fill > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(tone.copy(alpha = .10f), tone, tone.copy(alpha = .10f)),
                            center = c
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * fill,
                        useCenter = false,
                        topLeft = Offset(stroke, stroke),
                        size = Size(size.width - stroke * 2, size.height - stroke * 2),
                        style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // MARBLE_HOME_PING_AUTOFIT_V112 / V114 — locked value + word slots inside the ring.
                HomeStatValueSlot(
                    value = value,
                    tone = tone,
                    baseStyle = homeStatValueBaseStyle(HomeFlavor.NEBULA),
                    height = metrics.valueSlot,
                    modifier = Modifier.width(metrics.dialSize - 8.dp),
                    weight = valueWeight,
                    sizeScale = valueSizeScale,
                    align = Alignment.Center
                )
                HomeStatHintSlot(
                    hint = hint,
                    height = metrics.hintSlot,
                    align = Alignment.Center
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.headerSlot),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


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
 * The connection control.
 *
 * Deliberately carries NO quality ring: link quality lives in its own readouts, and wrapping the
 * primary action in a score made the button's own state ambiguous. Every Home presentation shares
 * the exact same control, in the silhouette the user picked in Settings.
 */
@Composable
internal fun HomePowerControl(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
    haloBrush: Brush? = null,
    style: ConnectButtonStyle? = null
) {
    // MARBLE_CONNECT_BUTTON_V121 — the control is one of the three connection button styles,
    // resolved in order: an explicit per-call style, then the user's Settings choice, then the
    // product default. Keeping this entry point preserves the shared Home evidence and action
    // contract for every presentation.
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

/**
 * MARBLE_CONNECT_BUTTON_V121 — the Home tree provides the user's chosen silhouette here so every
 * [HomePowerControl] call site resolves it identically.
 */
internal val LocalConnectButtonStyle = compositionLocalOf { ConnectButtonStyle.ROUND }

/**
 * MARBLE_CONNECT_PLACEMENT_V123 — every silhouette owns its own zone in a Home presentation.
 *
 * The three controls are three physically different metaphors, so they never share one spot:
 *
 *  - [ConnectButtonStyle.ROUND]   the round shutter is the focal instrument → centred in the hero;
 *  - [ConnectButtonStyle.SLIDE]   the wide drag track is a console floor bar → docked at the hero
 *    floor, low and full width, with settle room underneath the drag travel;
 *  - [ConnectButtonStyle.CLASSIC] the rectangular power bar is a piece of hardware → docks below
 *    the hero in its own slim power-deck capsule, clear of the orbiting artwork.
 */
internal enum class ConnectControlZone { HERO_CENTER, HERO_FLOOR, POWER_DOCK }

internal fun connectControlZone(style: ConnectButtonStyle): ConnectControlZone = when (style) {
    ConnectButtonStyle.ROUND -> ConnectControlZone.HERO_CENTER
    ConnectButtonStyle.SLIDE -> ConnectControlZone.HERO_FLOOR
    ConnectButtonStyle.CLASSIC -> ConnectControlZone.POWER_DOCK
}

/**
 * MARBLE_CONNECT_PLACEMENT_V123 — the chrome behind the docked classic power bar. A slim ambient
 * capsule in the presentation's own tone, so the rectangular switch reads as a deliberately
 * docked instrument instead of a floating rectangle between the artwork and the status copy.
 */
@Composable
internal fun HomePowerDock(
    evidence: HomeEvidence,
    tone: Color,
    onToggle: () -> Unit,
    flavor: HomeFlavor,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .72f))
            .background(
                Brush.verticalGradient(
                    listOf(tone.copy(alpha = .11f), Color.Transparent)
                )
            )
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

/**
 * MARBLE_CONNECT_BUTTON_V121 — the one connection button of the product, in three silhouettes.
 *
 *  - [ConnectButtonStyle.ROUND]   the large round shutter (default);
 *  - [ConnectButtonStyle.SLIDE]   a slide-to-connect track dragged from left to right;
 *  - [ConnectButtonStyle.CLASSIC] the classic rectangular power switch.
 *
 * Rules shared by all three: the control never changes position or size, never floats and never
 * breathes. Only its colour, its copy and — while a route is actually being secured — a single
 * progress indicator animate, so the button is calm at rest and unmistakable while it works.
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
    }
}

/** The action word under (or inside) every silhouette. Only the text and its colour change. */
@Composable
private fun ConnectButtonCaption(
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
 * Style 1 — the round shutter. Big, centred, fixed. A hairline rim states the state colour, and a
 * single indeterminate arc is drawn only while the route is actually being secured or closed.
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
    val label = homeActionLabel(evidence)

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
                    pressScale = 1f,
                    boundedShape = CircleShape,
                    onClick = onToggle
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
                    busy -> drawArc(
                        color = animatedTone,
                        startAngle = -90f + sweep,
                        sweepAngle = 104f,
                        useCenter = false,
                        topLeft = Offset(c.x - r * .80f, c.y - r * .80f),
                        size = Size(r * 1.60f, r * 1.60f),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    evidence.connected -> drawCircle(
                        color = animatedTone.copy(alpha = .80f),
                        radius = r * .80f,
                        center = c,
                        style = Stroke(width = 4.dp.toPx())
                    )

                    else -> drawCircle(
                        color = animatedTone.copy(alpha = .45f),
                        radius = r * .80f,
                        center = c,
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                }
            }
            HomeGlyphIcon(
                connectButtonGlyph(evidence),
                animatedTone,
                Modifier.size(diameter * .26f)
            )
        }
        Spacer(Modifier.height(10.dp))
        ConnectButtonCaption(evidence, animatedTone)
    }
}

/** The glyph in the middle of every silhouette: protected, blocked or armed. */
private fun connectButtonGlyph(evidence: HomeEvidence): HomeGlyph = when {
    evidence.connected -> HomeGlyph.CHECK
    evidence.blocked -> HomeGlyph.RESET
    else -> HomeGlyph.POWER
}

/**
 * Style 2 — slide to connect.
 *
 * A safety switch: the user drags the knob from left to right across the track to arm or close
 * the tunnel. The knob is the only thing that ever moves, it follows the finger exactly, and it
 * springs back when the gesture is released before the end of the track, so a pocket tap can
 * never toggle the connection. The gesture is pinned to LTR because it is a physical, screen-space
 * control: it reads left → right in Persian exactly as it does in English.
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
    val trackHeight = 66.dp
    val knobSize = 54.dp
    val padding = 6.dp
    val travelDp = width - knobSize - padding * 2
    val travelPx = with(density) { travelDp.toPx() }.coerceAtLeast(1f)
    val shape = RoundedCornerShape(trackHeight / 2)
    val busy = evidence.connecting || evidence.disconnecting
    val label = homeActionLabel(evidence)

    val scope = rememberCoroutineScope()
    val knob = remember { Animatable(0f) }
    val progress = (knob.value / travelPx).coerceIn(0f, 1f)
    val shimmer = if (busy) motion.loop(1_400) else 0f

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
                    .background(Aether.VoidElevated.copy(alpha = .95f))
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
                        .padding(start = knobSize, end = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = padding)
                        .offset { IntOffset(knob.value.toInt(), 0) }
                        .size(knobSize)
                        .clip(CircleShape)
                        .background(animatedTone.copy(alpha = .92f))
                        .pointerInput(armed, travelPx) {
                            if (!armed) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    // The switch only fires when the knob really reached the end
                                    // of its travel, then springs home so the control is once
                                    // again exactly where it started.
                                    val completed = knob.value >= travelPx * .82f
                                    scope.launch {
                                        knob.animateTo(0f, MarbleMotionSpecs.ResponseFloat)
                                    }
                                    if (completed) onToggle()
                                },
                                onDragCancel = {
                                    scope.launch {
                                        knob.animateTo(0f, MarbleMotionSpecs.ResponseFloat)
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
                .background(Aether.VoidElevated.copy(alpha = .96f))
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
            // State lamp: the classic switch always shows whether the line is live.
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(animatedTone)
            )
        }
    }
}

@Composable
internal fun HomeStatusHeadline(
    evidence: HomeEvidence,
    @Suppress("UNUSED_PARAMETER") tone: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Center
) {
    Text(
        homeStatusText(evidence),
        color = Aether.Ink,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private fun formatBps(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.0f kB/s".format(bps / 1_000.0)
    bps > 0 -> "$bps B/s"
    else -> "—"
}

// ---------------------------------------------------------------------------------------------
// Style 1 — Cosmic Orbit: "the Command Deck"
// ---------------------------------------------------------------------------------------------

/**
 * A pilot's console over a live starfield: the orbital system is the hero instrument, identity is
 * a readout table with dotted leaders, the IP sits on a data bus, and uptime/ping are a digital
 * odometer and a real arc gauge. A slim action rail rides the trailing edge.
 */
@Composable
internal fun HomeStyleCosmicOrbit(
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp,
    onScrollChanged: (Boolean) -> Unit = {}
) {
    val tone = styleStateTone(HomeFlavor.ORBIT, evidence)
    val gold = Color(0xFFE7C36B)
    val deep = Color(0xFF0B1B3A)
    val motion = MarbleMotion.current
    val phase = motion.loop(14_000)
    val twinkle = motion.loop(6_000)
    // MARBLE_SEAMLESS_LOOPS_V114 — one full turn per orbit on coprime periods: the bodies keep
    // genuinely different speeds, and every orbit closes exactly where it opened.
    val orbitPhases = listOf(motion.loop(14_000), motion.loop(23_000), motion.loop(37_000))
    // MARBLE_DOCK_SCROLL_ONLY_V123 — the deck reports its own scroll so the bottom dock can
    // turn to glass only while content genuinely moves, never during a tab transition.
    val orbitScroll = rememberScrollState()
    LaunchedEffect(orbitScroll.isScrollInProgress) {
        onScrollChanged(orbitScroll.isScrollInProgress)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .34f).coerceIn(210.dp, 300.dp)

        // Full-viewport starfield behind the whole console.
        Canvas(Modifier.matchParentSize()) {
            drawStarfield(gold, tone, twinkle, evidence.connected)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp)
                .padding(bottom = bottomClearance),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(orbitScroll),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                PrismPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accent = gold,
                    selected = evidence.connected,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    // MARBLE_CONNECT_PLACEMENT_V123 — the hero: round shutter centred, slide track
                    // docked at the floor, classic power bar below the instrument ring.
                    val zone = connectControlZone(LocalConnectButtonStyle.current)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight + if (zone == ConnectControlZone.HERO_FLOOR) 26.dp else 0.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(deep.copy(alpha = .30f), Color.Transparent)
                                )
                            ),
                        contentAlignment = if (zone == ConnectControlZone.HERO_FLOOR) Alignment.BottomCenter else Alignment.Center
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            drawSolarSystem(gold, tone, orbitPhases, phase, evidence.connected)
                        }
                        if (zone != ConnectControlZone.POWER_DOCK) {
                            HomePowerControl(
                                evidence = evidence,
                                tone = tone,
                                onToggle = actions.onToggleConnection,
                                flavor = HomeFlavor.ORBIT,
                                modifier = if (zone == ConnectControlZone.HERO_FLOOR) Modifier.padding(
                                    start = 10.dp,
                                    end = 10.dp,
                                    bottom = 18.dp
                                ) else Modifier,
                                diameter = 118.dp,
                                haloBrush = Brush.radialGradient(
                                    listOf(
                                        gold.copy(alpha = .55f),
                                        gold.copy(alpha = .16f),
                                        Color.Transparent
                                    )
                                )
                            )
                        }
                    }
                    if (zone == ConnectControlZone.POWER_DOCK) {
                        HomePowerDock(
                            evidence = evidence,
                            tone = tone,
                            onToggle = actions.onToggleConnection,
                            flavor = HomeFlavor.ORBIT,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HomeStatusHeadline(evidence, tone, align = TextAlign.Start)
                    HomeIdentityBlock(evidence, tone, HomeFlavor.ORBIT, Modifier.fillMaxWidth())
                }

                PrismPanel(
                    modifier = Modifier.fillMaxWidth(),
                    accent = tone,
                    contentPadding = PaddingValues(13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            Tr.now.networkSpeed,
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${Tr.now.download} ${formatBps(evidence.downBps)}  •  " +
                                "${Tr.now.upload} ${formatBps(evidence.upBps)}",
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        drawSpeedGraph(tone, gold, phase, evidence.downBps, evidence.upBps)
                    }
                    HomeIpRow(evidence, tone, actions, HomeFlavor.ORBIT, Modifier.fillMaxWidth())
                }

                HomeSessionStats(evidence, tone, actions, HomeFlavor.ORBIT, Modifier.fillMaxWidth())
            }

            // Side navigation rail on the trailing edge.
            Column(
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Aether.VoidElevated)
                    .border(1.dp, Aether.BarGlassBorder, RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrismIconButton(
                    onClick = actions.onLibrary,
                    tone = gold,
                    size = 34.dp,
                    descriptiveLabel = Tr.now.library
                ) { HomeGlyphIcon(HomeGlyph.LIBRARY, Aether.InkMuted, Modifier.size(16.dp)) }
                PrismIconButton(
                    onClick = actions.onTestPing,
                    tone = tone,
                    size = 34.dp,
                    enabled = homePingTappable(evidence),
                    descriptiveLabel = Tr.now.testPing
                ) { HomeGlyphIcon(HomeGlyph.PULSE, Aether.InkMuted, Modifier.size(16.dp)) }
                PrismIconButton(
                    onClick = actions.onIpDetails,
                    tone = tone,
                    size = 34.dp,
                    descriptiveLabel = Tr.now.ipDetails
                ) { HomeGlyphIcon(HomeGlyph.MORE, Aether.InkMuted, Modifier.size(16.dp)) }
            }
        }
    }
}

/** The full-viewport star layer behind the console. */
private fun DrawScope.drawStarfield(gold: Color, accent: Color, twinkle: Float, connected: Boolean) {
    val w = size.width
    val h = size.height
    repeat(48) { index ->
        val x = w * hash01(index * 3 + 1)
        val y = h * hash01(index * 5 + 2)
        val shimmer = .5f + .5f * sin((twinkle + hash01(index * 7 + 3)) * 2f * PI.toFloat())
        val bright = hash01(index * 11 + 4) > .82f
        val star = Offset(x, y)
        // MARBLE_HOME_GLAMOUR_V114 — the brightest stars carry a diffraction cross that breathes
        // with the same twinkle, so the console sky has real points of light instead of dust.
        if (bright && shimmer > .55f) {
            val arm = (2.6f + 3.4f * shimmer).dp.toPx()
            val flare = gold.copy(alpha = .30f * (shimmer - .55f) / .45f)
            drawLine(flare, Offset(x - arm, y), Offset(x + arm, y), .9.dp.toPx())
            drawLine(flare, Offset(x, y - arm), Offset(x, y + arm), .9.dp.toPx())
        }
        drawCircle(
            color = (if (bright) gold else accent)
                .copy(alpha = (if (connected) .34f else .20f) * shimmer + .05f),
            radius = (if (bright) 1.7f else 1.0f).dp.toPx(),
            center = star
        )
    }
    // MARBLE_HOME_GLAMOUR_V114 — a deck horizon: warm glow along the console floor and a vignette
    // that pushes the starfield back behind the instruments.
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.Transparent, gold.copy(alpha = if (connected) .07f else .04f)),
            startY = h * .55f,
            endY = h
        )
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = .34f)),
            center = Offset(w * .5f, h * .40f),
            radius = (w + h) * .60f
        )
    )
}

private fun DrawScope.drawSolarSystem(
    gold: Color,
    accent: Color,
    phaseOrbits: List<Float>,
    phase: Float,
    connected: Boolean
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val unit = min(size.width, size.height) / 2f

    drawCircle(
        brush = Brush.radialGradient(
            listOf(gold.copy(alpha = .55f), Color.Transparent),
            center = center,
            radius = unit * .40f
        ),
        radius = unit * .40f,
        center = center
    )

    // One radius per orbit ring; the animated phase per body comes from phaseOrbits (the caller's
    // MarbleMotion loops). Keeping the two lists separate is what keeps the planets moving —
    // using the static radii as the angle froze every body on its first frame.
    val orbitRadii = listOf(.46f, .66f, .88f)
    orbitRadii.forEachIndexed { index, radius ->
        val r = unit * radius
        drawCircle(
            color = accent.copy(alpha = if (connected) .30f else .16f),
            radius = r,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        // MARBLE_SEAMLESS_LOOPS_V114 — a whole turn per loop, never a fraction of one.
        val angle = phaseOrbits.getOrElse(index) { phase } * 2f * PI.toFloat()
        val planet = Offset(
            center.x + cos(angle) * r,
            center.y + sin(angle) * r * .42f
        )
        // Comet tail: fading ghosts trail each body along its orbit.
        repeat(5) { ghost ->
            val trailAngle = angle - (ghost + 1) * .10f
            drawCircle(
                color = (if (index == 1) gold else accent).copy(alpha = .30f * (1f - ghost / 5f)),
                radius = (2.6f - index * .4f - ghost * .35f).coerceAtLeast(.8f).dp.toPx(),
                center = Offset(
                    center.x + cos(trailAngle) * r,
                    center.y + sin(trailAngle) * r * .42f
                )
            )
        }
        drawCircle(
            color = if (index == 1) gold else accent,
            radius = (4f - index * .6f).dp.toPx(),
            center = planet
        )
    }
}

private fun DrawScope.drawSpeedGraph(tone: Color, gold: Color, phase: Float, down: Long, up: Long) {
    val w = size.width
    val h = size.height
    val active = down + up > 0
    val peak = if (active) .82f else .35f
    // The waveform breathes with the shared clock so the console always feels live.
    val wobble = sin(phase * 2f * PI.toFloat()) * h * .05f
    val curve = Path().apply {
        moveTo(0f, h)
        cubicTo(
            w * .18f, h * .92f + wobble,
            w * .30f, h * (1f - peak * .55f) - wobble,
            w * .46f, h * (1f - peak)
        )
        cubicTo(
            w * .62f, h * (1f - peak * 1.02f) + wobble,
            w * .74f, h * .70f - wobble,
            w, h * .58f
        )
    }
    val filled = Path().apply {
        addPath(curve)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        filled,
        Brush.verticalGradient(listOf(tone.copy(alpha = .26f), Color.Transparent))
    )
    drawPath(curve, color = tone, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    val head = Offset(w * .46f, h * (1f - peak))
    drawCircle(gold, radius = 3.6.dp.toPx(), center = head)
    drawCircle(color = gold.copy(alpha = .28f), radius = 8.dp.toPx(), center = head)
}

// ---------------------------------------------------------------------------------------------
// Style 2 — Cosmic Immersion: "the Nebula HUD"
// ---------------------------------------------------------------------------------------------

/**
 * Edge-to-edge deep space. Three parallax star layers, aurora ribbons across the sky and the
 * energy flower blooming below. Evidence is chrome-less: letterspaced HUD identity, a glass IP
 * strip and twin stat rings whose motion is the interface.
 */
@Composable
internal fun HomeStyleCosmicImmersion(
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp,
    onScrollChanged: (Boolean) -> Unit = {}
) {
    val tone = styleStateTone(HomeFlavor.NEBULA, evidence)
    val cyan = Color(0xFF57E0FF)
    val violet = Color(0xFFB08CFF)
    val motion = MarbleMotion.current
    val orbitPhase = motion.loop(16_000)
    val auroraPhase = motion.loop(12_000)
    val bloom = motion.breathe(4_600)
    // MARBLE_SEAMLESS_LOOPS_V114 — parallax by *period*, not by fractional speed: each layer
    // crosses the whole sky exactly once per loop, so no star teleports when the clock wraps.
    val starPhases = listOf(motion.loop(126_000), motion.loop(84_000), motion.loop(58_000))
    val skyOrbits = listOf(motion.loop(21_000), motion.loop(34_000), motion.loop(55_000))
    // MARBLE_DOCK_SCROLL_ONLY_V123 — the HUD reports its own scroll so the bottom dock turns to
    // glass only while content genuinely moves, never during a tab transition.
    val immersionScroll = rememberScrollState()
    LaunchedEffect(immersionScroll.isScrollInProgress) {
        onScrollChanged(immersionScroll.isScrollInProgress)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val skyClearance = (maxHeight * .10f).coerceIn(40.dp, 110.dp)

        Canvas(Modifier.matchParentSize()) {
            drawImmersiveCosmos(cyan, violet, starPhases, skyOrbits, orbitPhase, auroraPhase, bloom, evidence.connected)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(immersionScroll)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = bottomClearance),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // MARBLE_CONNECT_PLACEMENT_V123 — each silhouette owns its own line of the sky:
            // round shutter at the instrument height, classic power bar a step lower, slide
            // track lowest so its drag travel keeps clear air underneath.
            val zone = connectControlZone(LocalConnectButtonStyle.current)
            Spacer(
                Modifier.height(
                    skyClearance + when (zone) {
                        ConnectControlZone.HERO_FLOOR -> 56.dp
                        ConnectControlZone.POWER_DOCK -> 26.dp
                        ConnectControlZone.HERO_CENTER -> 0.dp
                    }
                )
            )

            if (zone == ConnectControlZone.POWER_DOCK) {
                HomePowerDock(
                    evidence = evidence,
                    tone = tone,
                    onToggle = actions.onToggleConnection,
                    flavor = HomeFlavor.NEBULA,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                HomePowerControl(
                    evidence = evidence,
                    tone = tone,
                    onToggle = actions.onToggleConnection,
                    flavor = HomeFlavor.NEBULA,
                    modifier = if (zone == ConnectControlZone.HERO_FLOOR) Modifier.padding(bottom = 6.dp) else Modifier,
                    diameter = 134.dp,
                    haloBrush = Brush.radialGradient(
                        listOf(
                            cyan.copy(alpha = .30f + .12f * bloom),
                            violet.copy(alpha = .18f),
                            Color.Transparent
                        )
                    )
                )
            }

            Text(
                homeStatusText(evidence).uppercase(),
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.2.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HomeIdentityBlock(evidence, tone, HomeFlavor.NEBULA, Modifier.fillMaxWidth())

            HomeIpRow(evidence, tone, actions, HomeFlavor.NEBULA, Modifier.fillMaxWidth())

            HomeSessionStats(evidence, tone, actions, HomeFlavor.NEBULA, Modifier.fillMaxWidth())

            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun DrawScope.drawImmersiveCosmos(
    cyan: Color,
    violet: Color,
    stars: List<Float>,
    orbits: List<Float>,
    phase: Float,
    aurora: Float,
    bloom: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Nebula wash behind everything.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(violet.copy(alpha = .20f), Color.Transparent),
            center = Offset(w * .5f, h * .20f),
            radius = w * .85f
        ),
        radius = w * .85f,
        center = Offset(w * .5f, h * .20f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(cyan.copy(alpha = .08f), Color.Transparent),
            center = Offset(w * .82f, h * .55f),
            radius = w * .55f
        ),
        radius = w * .55f,
        center = Offset(w * .82f, h * .55f)
    )

    // Three parallax star layers: deeper layers crawl and stay dimmer, nearer ones glide. Each layer
    // owns one full crossing per loop and every star fades to nothing at both screen edges, so the
    // drift loop cannot pop.
    // MARBLE_SEAMLESS_LOOPS_V112 / MARBLE_SEAMLESS_LOOPS_V114
    repeat(3) { layer ->
        val crossing = stars.getOrElse(layer) { phase }
        val alphaBase = .10f + layer * .10f
        repeat(18) { index ->
            val seed = layer * 100 + index
            val x = (w * hash01(seed * 3 + 1) + crossing * w) % w
            val y = h * hash01(seed * 5 + 2)
            val edgeFade = loopFade(x / w)
            drawCircle(
                color = (if (index % 4 == 0) violet else cyan)
                    .copy(alpha = alphaBase * (if (connected) 1.3f else 1f) * edgeFade),
                radius = (.7f + layer * .5f).dp.toPx(),
                center = Offset(x, y)
            )
        }
    }

    // MARBLE_HOME_GLAMOUR_V114 — one long shooting star crosses the sky per orbit loop and fades
    // out completely before it wraps, so the loop still closes on an empty sky.
    // MARBLE_SEAMLESS_LOOPS_V114
    val shootFade = loopFade(phase)
    if (shootFade > 0f) {
        val head = Offset(w * phase, h * (.10f + .22f * phase))
        val tail = head - Offset(w * .16f, h * .055f)
        drawLine(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, cyan.copy(alpha = .55f * shootFade)),
                start = tail,
                end = head
            ),
            start = tail,
            end = head,
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = Color.White.copy(alpha = .55f * shootFade), radius = 1.5.dp.toPx(), center = head)
    }

    // Aurora ribbons: two sine sheets flowing across the upper sky.
    repeat(2) { ribbon ->
        val baseY = h * (.16f + ribbon * .09f)
        val amp = h * (.028f - ribbon * .008f)
        val path = Path()
        var x = 0f
        while (x <= w) {
            val y = baseY + sin((x / w * 4f + aurora * 2f + ribbon) * PI.toFloat()) * amp
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += w / 42f
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    (if (ribbon == 0) cyan else violet).copy(alpha = if (connected) .34f else .18f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = (5f - ribbon * 2f).dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // Upper half: luminous orbits and floating planets.
    val skyCenter = Offset(w * .5f, h * .20f)
    listOf(.42f, .62f, .84f).forEachIndexed { index, radius ->
        val r = w * radius
        drawCircle(
            color = cyan.copy(alpha = if (connected) .22f else .12f),
            radius = r,
            center = skyCenter,
            style = Stroke(width = 1.dp.toPx())
        )
        // MARBLE_SEAMLESS_LOOPS_V114 — a whole turn per loop on its own period.
        val angle = orbits.getOrElse(index) { phase } * 2f * PI.toFloat()
        drawCircle(
            color = if (index % 2 == 0) cyan else violet,
            radius = (3.4f - index * .5f).dp.toPx(),
            center = Offset(skyCenter.x + cos(angle) * r, skyCenter.y + sin(angle) * r * .34f)
        )
    }

    // Lower half: the cosmic energy flower. The whole bloom rotates by exactly five petal
    // slots per loop — a symmetry of the 12-petal layout — so the wrap frame maps petals onto
    // petals and the rotation loop is perfectly seamless.
    // MARBLE_SEAMLESS_LOOPS_V112
    val flower = Offset(w * .5f, h * .84f)
    val petals = 12
    val reach = w * (.34f + .04f * bloom)
    repeat(petals) { index ->
        val angle = index * 2f * PI.toFloat() / petals + phase * 2f * PI.toFloat() * (5f / petals)
        val tip = Offset(flower.x + cos(angle) * reach, flower.y + sin(angle) * reach * .58f)
        val ctrl = Offset(
            flower.x + cos(angle + .34f) * reach * .55f,
            flower.y + sin(angle + .34f) * reach * .34f
        )
        val petal = Path().apply {
            moveTo(flower.x, flower.y)
            quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
            quadraticTo(
                flower.x + cos(angle - .34f) * reach * .55f,
                flower.y + sin(angle - .34f) * reach * .34f,
                flower.x,
                flower.y
            )
        }
        drawPath(
            petal,
            color = (if (index % 2 == 0) cyan else violet)
                .copy(alpha = if (connected) .26f else .13f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            listOf(cyan.copy(alpha = .38f + .12f * bloom), Color.Transparent),
            center = flower,
            radius = w * .26f
        ),
        radius = w * .26f,
        center = flower
    )
}

// ---------------------------------------------------------------------------------------------
// Dispatcher
// ---------------------------------------------------------------------------------------------

/** Renders the Home surface in the presentation the user selected in Settings. */
@Composable
internal fun HomeStyleSurface(
    style: HomeStyle,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp,
    pro: HomeProContext? = null,
    onScrollChanged: (Boolean) -> Unit = {}
) {
    when (style) {
        HomeStyle.PRO -> HomeStyleSignature(
            evidence = evidence,
            actions = actions,
            pro = pro ?: HomeProContext(
                showBanner = false,
                showCornerActions = false,
                shortcut = ProShortcut.LIBRARY,
                accent = ProAccent.ELECTRIC
            ),
            bottomClearance = bottomClearance,
            onScrollChanged = onScrollChanged
        )
        HomeStyle.COSMIC_ORBIT -> HomeStyleCosmicOrbit(
            evidence,
            actions,
            bottomClearance,
            onScrollChanged
        )
        HomeStyle.COSMIC_IMMERSION -> HomeStyleCosmicImmersion(
            evidence,
            actions,
            bottomClearance,
            onScrollChanged
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
