@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marbleng.app.ui

// MARBLE_SIGNATURE_HOME_V112
//
// The dedicated professional connection surface ("Signature studio") plus its app-wide layers.
//
// Everything in this file shares one contract with the four classic Home styles:
//   * the SAME runtime evidence (HomeEvidence) and the same actions — nothing is invented here;
//   * every ambient animation is a SEAMLESS LOOP (MARBLE_SEAMLESS_LOOPS_V112): full-circle
//     rotations, sine breathing and loopFade envelopes only. No effect ever snaps back to its
//     start frame, so the user can never tell where an animation begins or ends;
//   * the ping readout renders through HomeStatValueText (MARBLE_HOME_PING_AUTOFIT_V112), so a
//     long Persian/English status word can never overflow its box.
//
// The studio is fully customizable from Settings → Appearance → Signature studio:
//   * floating connect button (app-wide, draggable, v2rayNG-style shutter) — on/off;
//   * status banner (connection state + selected server) — on/off, Home-only or every page;
//   * corner action cluster — add server, grab ping, one configurable shortcut, more (⋮);
//   * the accent color driving every one of the studio's animated surfaces.
//
// MARBLE_SIGNATURE_STUDIO_TRIM_V121 — the in-Home server rail, its card-background choice and the
// bottom style switcher were removed. Choosing a route is the Servers page's job and choosing a
// presentation is Settings' job; duplicating either on Home only made the surface noisy and gave
// the same action two different behaviours.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.ProAccent
import com.marbleng.app.model.ProShortcut
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Accent resolution
// ---------------------------------------------------------------------------------------------

/** The user-chosen Signature accent as a concrete color. */
internal fun signatureAccentColor(accent: ProAccent): Color = when (accent) {
    ProAccent.ELECTRIC -> Color(0xFF3399FF)
    ProAccent.EMERALD -> Color(0xFF2ED3A7)
    ProAccent.AMETHYST -> Color(0xFF9D8CFF)
    ProAccent.AMBER -> Color(0xFFF2B45F)
    ProAccent.CYAN -> Color(0xFF9BE8FF)
}

/**
 * MARBLE_HOME_GRADIENTS_V116 — the two companion hues of each Signature accent. The studio was a
 * single-accent wash; now every accent carries its own professional multi-colour aurora
 * (electric → violet → teal, emerald → ice → gold, …) so the Home never reads grey.
 */
internal fun signatureAuraPartners(accent: ProAccent): List<Color> = when (accent) {
    ProAccent.ELECTRIC -> listOf(Color(0xFF7C5CFF), Color(0xFF2ED3A7))
    ProAccent.EMERALD -> listOf(Color(0xFF9BE8FF), Color(0xFFF2B45F))
    ProAccent.AMETHYST -> listOf(Color(0xFF57E0FF), Color(0xFFE7C36B))
    ProAccent.AMBER -> listOf(Color(0xFF9D8CFF), Color(0xFF57E0FF))
    ProAccent.CYAN -> listOf(Color(0xFF3399FF), Color(0xFF9D8CFF))
}

/** The accent tinted by the live connection state: state always reads first, brand second. */
@Composable
internal fun signatureStatusTone(evidence: HomeEvidence, accent: ProAccent): Color {
    val accentColor = signatureAccentColor(accent)
    return when {
        // MARBLE_CONNECT_RING_STYLE_V115 — the connected ring and its glow follow the studio
        // accent the user picked (and therefore the active theme tokens), never a hard-coded
        // green. Choosing the EMERALD accent restores the classic green look.
        evidence.connected -> accentColor
        evidence.connecting -> accentColor.copy(alpha = .82f)
        // MARBLE_CONNECT_BUTTON_V121 — a closing tunnel is its own state everywhere, including
        // the studio chrome, so the banner cannot claim "ready" while the route is still up.
        evidence.disconnecting -> Aether.Amber
        evidence.blocked -> Aether.Danger
        else -> accentColor
    }
}

// ---------------------------------------------------------------------------------------------
// Style 0 — the Signature studio Home surface
// ---------------------------------------------------------------------------------------------

/**
 * The professional, fully-customizable Home. One column of studio modules over an animated
 * accent aurora: status banner, corner action cluster, the power instrument and the shared
 * evidence blocks in their Signature skin.
 *
 * MARBLE_SIGNATURE_STUDIO_TRIM_V121 — the in-Home server rail and style switcher were removed:
 * routes belong to the Servers page and the presentation to Settings, so Home stays one calm
 * connection surface instead of a third place to change either.
 */
@Composable
internal fun HomeStyleSignature(
    evidence: HomeEvidence,
    actions: HomeActions,
    pro: HomeProContext,
    bottomClearance: Dp,
    // MARBLE_DOCK_SCROLL_V122 — hoisted by the deck so the bottom dock sees the Home scroll.
    scrollState: ScrollState = rememberScrollState()
) {
    val accent = signatureAccentColor(pro.accent)
    val tone = signatureStatusTone(evidence, pro.accent)
    // MARBLE_HOME_GRADIENTS_V116 — the accent's companion hues drive the aurora beneath the
    // single-accent instrument, so the Signature Home is a multi-colour gradient, never grey.
    val aura = signatureAuraPartners(pro.accent)
    val motion = MarbleMotion.current
    val drift = motion.loop(18_000)
    val breathe = motion.breathe(4_200)
    val ringSpin = motion.loop(22_000)
    val cometPhase = motion.loop(9_000)
    val microPhase = motion.loop(6_200)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .34f).coerceIn(220.dp, 330.dp)
        // MARBLE_SLIDE_BAND_PLACEMENT_V123 — the band gets the bottom shelf, not the studio slot.
        val bandShown = rememberConnectBandShown()

        // The studio backdrop owns the whole viewport.
        Canvas(Modifier.matchParentSize()) {
            drawSignatureBackdrop(accent, aura, tone, drift, breathe, ringSpin, evidence.connected)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp)
                .padding(bottom = bottomClearance + if (bandShown) MarbleSlideBandReserve else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pro.showBanner) {
                SignatureStatusBanner(
                    evidence = evidence,
                    accent = accent,
                    tone = tone,
                    onToggle = actions.onToggleConnection,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (pro.showCornerActions) {
                SignatureCornerCluster(
                    evidence = evidence,
                    actions = actions,
                    accent = accent,
                    shortcut = pro.shortcut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.End)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawSignatureHeroField(
                        accent = accent,
                        tone = tone,
                        breathe = breathe,
                        cometPhase = cometPhase,
                        microPhase = microPhase,
                        connected = evidence.connected
                    )
                }
                if (!bandShown) {
                    HomePowerControl(
                        evidence = evidence,
                        tone = tone,
                        onToggle = actions.onToggleConnection,
                        flavor = HomeFlavor.PRO,
                        diameter = 168.dp,
                        haloBrush = Brush.radialGradient(
                            listOf(
                                accent.copy(alpha = .30f + .12f * breathe),
                                accent.copy(alpha = .10f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }

            Text(
                homeStatusText(evidence).uppercase(),
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 1.8.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HomeIdentityBlock(evidence, tone, HomeFlavor.PRO, Modifier.fillMaxWidth())
            HomeIpRow(evidence, tone, actions, HomeFlavor.PRO, Modifier.fillMaxWidth())
            HomeSessionStats(evidence, tone, actions, HomeFlavor.PRO, Modifier.fillMaxWidth())

            Spacer(Modifier.height(2.dp))
        }

        if (bandShown) {
            HomeConnectBandDock(
                evidence = evidence,
                tone = tone,
                flavor = HomeFlavor.PRO,
                onToggle = actions.onToggleConnection,
                bottomClearance = bottomClearance,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * The full-viewport Signature backdrop: a multi-colour accent aurora, an instrument ring field,
 * drifting light motes and a fine dot grid. Every element loops seamlessly.
 */
private fun DrawScope.drawSignatureBackdrop(
    accent: Color,
    aura: List<Color>,
    tone: Color,
    drift: Float,
    breathe: Float,
    ringSpin: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // MARBLE_HOME_GRADIENTS_V116 / MARBLE_ENERGETIC_GRADIENTS_V122 — the brand wash is a
    // three-stop gradient: the accent's own hue across the top, a companion colour through the
    // middle and a second companion at the floor, so the studio reads as a rich vivid gradient
    // instead of one quiet grey-blue veil. V122 lifts the stops so the field carries real energy.
    val companionA = aura.getOrNull(0) ?: accent
    val companionB = aura.getOrNull(1) ?: accent
    drawRect(
        Brush.verticalGradient(
            listOf(
                accent.copy(alpha = .17f + .05f * breathe),
                companionA.copy(alpha = .095f),
                companionB.copy(alpha = .13f)
            )
        )
    )

    // Aurora halos breathing behind the hero: the primary hue centered, its companions drifting
    // to the corners so the whole viewport carries the style's colour story.
    val hero = Offset(w * .5f, h * .26f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                accent.copy(alpha = .26f + .07f * breathe),
                accent.copy(alpha = .10f),
                Color.Transparent
            ),
            center = hero,
            radius = w * .80f
        ),
        radius = w * .80f,
        center = hero
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                companionA.copy(alpha = .20f + .05f * breathe),
                companionA.copy(alpha = .07f),
                Color.Transparent
            ),
            center = Offset(w * .10f, h * .72f),
            radius = w * .58f
        ),
        radius = w * .58f,
        center = Offset(w * .10f, h * .72f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                companionB.copy(alpha = .17f + .04f * breathe),
                companionB.copy(alpha = .06f),
                Color.Transparent
            ),
            center = Offset(w * .90f, h * .86f),
            radius = w * .52f
        ),
        radius = w * .52f,
        center = Offset(w * .90f, h * .86f)
    )

    // Instrument ring field: concentric dashed rings, each rotating a whole circle per period.
    repeat(4) { index ->
        val r = w * (.34f + index * .17f)
        rotate(ringSpin * 360f * (if (index % 2 == 0) 1f else -1f), pivot = hero) {
            drawCircle(
                color = accent.copy(alpha = .10f + .04f * index),
                radius = r,
                center = hero,
                style = Stroke(
                    width = 1.1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf((10f + index * 8f), (26f - index * 4f).coerceAtLeast(8f))
                    )
                )
            )
        }
    }

    // Drifting light motes rising through the studio; each fades in at the bottom and out at the
    // top, so the drift loop is seamless.
    // MARBLE_SEAMLESS_LOOPS_V114 — the rise is an integer number of journeys per loop. A fractional
    // speed left every mote hanging at a random height the instant the clock wrapped, which read as
    // a flicker across the whole studio instead of as motion.
    repeat(16) { index ->
        val lane = hash01Local(index * 13 + 5)
        val cycles = 1 + index % 3
        val progress = (drift * cycles + hash01Local(index * 19 + 7)) % 1f
        val x = w * lane + sin((progress * 3f + index) * PI.toFloat()) * w * .012f
        val y = h * (1f - progress)
        val fade = sin(progress * PI.toFloat())
        drawCircle(
            color = accent.copy(alpha = (if (connected) .26f else .13f) * fade),
            radius = (.8f + 1.5f * hash01Local(index * 23 + 11)).dp.toPx(),
            center = Offset(x, y)
        )
    }

    // MARBLE_HOME_GLAMOUR_V114 — one studio light band crosses the paper per drift loop, and a
    // vignette settles the corners. The band is fully transparent at both ends of its travel
    // ([loopFade]), so its loop has no seam either.
    val bandFade = loopFade(drift)
    if (bandFade > 0f) {
        val bandX = w * drift
        drawRect(
            Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    accent.copy(alpha = .055f * bandFade),
                    Color.Transparent
                ),
                startX = bandX - w * .30f,
                endX = bandX + w * .30f
            )
        )
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = .30f)),
            center = hero,
            radius = (w + h) * .62f
        )
    )

    // Fine dot grid: quiet studio paper.
    val step = w / 16f
    var gy = step
    while (gy < h) {
        var gx = step
        while (gx < w) {
            drawCircle(
                color = tone.copy(alpha = .045f),
                radius = .5f.dp.toPx(),
                center = Offset(gx, gy)
            )
            gx += step
        }
        gy += step
    }
}

/** Focused energy behind the power instrument: a breathing halo, orbiting comets and a rim. */
private fun DrawScope.drawSignatureHeroField(
    accent: Color,
    tone: Color,
    breathe: Float,
    cometPhase: Float,
    microPhase: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height
    val c = Offset(w * .5f, h * .5f)
    val unit = min(w, h) / 2f

    // Breathing inner halo.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                tone.copy(alpha = .16f + .10f * breathe),
                Color.Transparent
            ),
            center = c,
            radius = unit * .95f
        ),
        radius = unit * .95f,
        center = c
    )

    // One comet on an elliptical instrument orbit; a full 2π per period — seamless. The head
    // carries a fading trail of ghosts.
    val orbitA = unit * .92f
    val orbitB = unit * .40f
    repeat(6) { ghost ->
        val angle = (((cometPhase - ghost * .055f) % 1f + 1f) % 1f) * 2f * PI.toFloat()
        val px = c.x + cos(angle) * orbitA
        val py = c.y + sin(angle) * orbitB
        drawCircle(
            color = accent.copy(
                alpha = (if (connected) .50f else .26f) * (1f - ghost / 6f)
            ),
            radius = (2.8f - ghost * .32f).coerceAtLeast(.6f).dp.toPx(),
            center = Offset(px, py)
        )
    }

    // A second, counter-rotating micro-comet while connected. `tone` is the resolved status
    // color (emerald while connected) passed in from composable scope: the Aether palette
    // getters are @Composable and cannot be read inside a DrawScope extension.
    if (connected) {
        val angle2 = microPhase * 2f * PI.toFloat()
        drawCircle(
            color = tone.copy(alpha = .55f),
            radius = 2.2.dp.toPx(),
            center = Offset(c.x + cos(angle2) * unit * .70f, c.y + sin(angle2) * unit * .30f)
        )
    }

    // Quiet graduated rim.
    drawCircle(
        color = tone.copy(alpha = .20f),
        radius = unit * .98f,
        center = c,
        style = Stroke(width = 1.dp.toPx())
    )
}

/** Deterministic 0..1 noise — same helper as the classic styles, local to this file. */
private fun hash01Local(seed: Int): Float {
    val s = sin(seed * 12.9898f + 78.233f) * 43758.5453f
    return s - floorLocal(s)
}

private fun floorLocal(v: Float): Float = kotlin.math.floor(v)

// ---------------------------------------------------------------------------------------------
// Status banner
// ---------------------------------------------------------------------------------------------

/**
 * The slim Signature status banner: live connection state, the selected server and a compact
 * ping/uptime readout on one strip. Tapping the banner toggles the connection.
 */
@Composable
internal fun SignatureStatusBanner(
    evidence: HomeEvidence,
    accent: Color,
    tone: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    val motion = MarbleMotion.current
    val pulse = motion.breathe(1_600)
    val shape = RoundedCornerShape(18.dp)
    // Resolved in composable scope: the semantics lambda below is not a composable context.
    val bannerLabel = homeStatusText(evidence)
    val animatedTone by animateColorAsState(
        targetValue = tone,
        animationSpec = MarbleMotionSpecs.Color,
        label = "signature-banner-tone"
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .95f))
            .border(1.dp, animatedTone.copy(alpha = .26f), shape)
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onToggle)
            .semantics { contentDescription = "$bannerLabel banner" }
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        // Live state lamp: breathing while connected, emphatic while securing.
        Box(Modifier.size(9.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = animatedTone.copy(alpha = .22f + .22f * pulse),
                    radius = size.minDimension * (.72f + .26f * pulse)
                )
            }
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(animatedTone)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                homeStatusText(evidence),
                color = animatedTone,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                evidence.nodeName.ifBlank { t.chooseRoute },
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Compact right readout: measured ping while connected, otherwise the session uptime.
        val readout = when {
            evidence.connected && evidence.pingState == ConnectionPingState.MEASURED ->
                "${evidence.pingMs} ms"
            evidence.pingState == ConnectionPingState.MEASURING -> t.measuring
            evidence.connected -> rememberUptimeLabel(evidence.connectedSinceMs)
            else -> "—"
        }
        // MARBLE_SYSTEM_FONT_V113 — the measured ping / session uptime follows the typeface
        // chosen in Settings instead of a hard-wired monospace face.
        Text(
            readout,
            color = Aether.Ink,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Corner action cluster
// ---------------------------------------------------------------------------------------------

/**
 * The Signature corner cluster: add server (+), grab ping, one configurable shortcut and a
 * more (⋮) menu with the remaining actions. Which action the shortcut button runs is a
 * Settings choice (Library / Rank / Privacy / Routing / Tests).
 */
@Composable
private fun SignatureCornerCluster(
    evidence: HomeEvidence,
    actions: HomeActions,
    accent: Color,
    shortcut: ProShortcut,
    modifier: Modifier = Modifier
) {
    val t = Tr.now
    var menuOpen by remember { mutableStateOf(false) }
    val pingTone = homePingTone(evidence, accent)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SignatureCornerButton(
            onClick = actions.onAddRoute,
            tone = accent,
            label = t.proAddRoute
        ) {
            HomeGlyphIcon(HomeGlyph.PLUS, accent, Modifier.size(17.dp))
        }
        SignatureCornerButton(
            onClick = actions.onTestPing,
            tone = pingTone,
            enabled = homePingTappable(evidence),
            label = t.proGrabPing
        ) {
            HomeGlyphIcon(
                HomeGlyph.PULSE,
                if (evidence.pingState == ConnectionPingState.MEASURING) accent else pingTone,
                Modifier.size(17.dp)
            )
        }
        SignatureCornerButton(
            onClick = when (shortcut) {
                ProShortcut.LIBRARY -> actions.onLibrary
                ProShortcut.RANK -> actions.onRank
                ProShortcut.PRIVACY -> actions.onPrivacy
                ProShortcut.ROUTING -> actions.onRouting
                ProShortcut.TESTS -> actions.onTests
            },
            tone = accent,
            label = t.proShortcut
        ) {
            HomeGlyphIcon(HomeGlyph.BOLT, accent, Modifier.size(17.dp))
        }

        Box {
            SignatureCornerButton(
                onClick = { menuOpen = true },
                tone = accent,
                label = t.proMoreActions
            ) {
                HomeGlyphIcon(HomeGlyph.MORE, Aether.InkMuted, Modifier.size(17.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(20.dp)
            ) {
                DropdownMenuItem(
                    text = { Text(trx(t.copyIp)) },
                    onClick = {
                        menuOpen = false
                        actions.onCopyIp()
                    }
                )
                DropdownMenuItem(
                    text = { Text(trx(t.refreshIp)) },
                    onClick = {
                        menuOpen = false
                        actions.onRefreshIp()
                    }
                )
                DropdownMenuItem(
                    text = { Text(trx(t.ipDetails)) },
                    onClick = {
                        menuOpen = false
                        actions.onIpDetails()
                    }
                )
                DropdownMenuItem(
                    text = { Text(trx(t.library)) },
                    onClick = {
                        menuOpen = false
                        actions.onLibrary()
                    }
                )
            }
        }
    }
}

@Composable
private fun SignatureCornerButton(
    onClick: () -> Unit,
    tone: Color,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(shape)
            .background(Aether.VoidElevated.copy(alpha = .92f))
            .border(1.dp, tone.copy(alpha = .30f), shape)
            .semantics { contentDescription = label }
            .kineticClickable(
                enabled = enabled,
                role = Role.Button,
                boundedShape = shape,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Floating connect button (app-wide, draggable)
// ---------------------------------------------------------------------------------------------

/**
 * MARBLE_SIGNATURE_HOME_V112 — the floating connect shutter, v2rayNG-style.
 *
 * A draggable circular button that overlays every page of the app: tap to connect/disconnect,
 * drag to place it anywhere on the screen. The dragged spot persists as normalized viewport
 * fractions (written on drag end only), so it survives restarts on any screen size. The ring
 * shows live state — breathing when protected, sweeping continuously while securing — and a
 * compact ping readout sits under the glyph once a measurement exists.
 */
@Composable
internal fun SignatureFloatingConnectOverlay(
    evidence: HomeEvidence,
    accent: Color,
    startNx: Float,
    startNy: Float,
    onToggle: () -> Unit,
    onPositionSettled: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val motion = MarbleMotion.current
    val fabSize = 62.dp
    val fabSizePx = with(density) { fabSize.toPx() }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var nx by remember { mutableFloatStateOf(startNx) }
    var ny by remember { mutableFloatStateOf(startNy) }
    var posPx by remember { mutableStateOf(Offset.Unspecified) }
    var dragging by remember { mutableStateOf(false) }

    // Resolve the persisted normalized spot into pixels once the host size is known.
    LaunchedEffect(parentSize) {
        if (parentSize != IntSize.Zero && posPx == Offset.Unspecified) {
            val maxX = (parentSize.width - fabSizePx).coerceAtLeast(1f)
            val maxY = (parentSize.height - fabSizePx).coerceAtLeast(1f)
            posPx = Offset(nx * maxX, ny * maxY)
        }
    }

    // MARBLE_RTL_FLOAT_FIX_V119 — the drag math and the persisted fractions are raw viewport
    // coordinates (x grows to the right). Persian flips the whole app to RTL, which made both the
    // placement box and `Modifier.offset` mirror the x axis, so the button travelled opposite to
    // the finger. This overlay is a physical, screen-space control, so it pins itself to LTR
    // regardless of the product language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { parentSize = it }
    ) {
        val maxX = (parentSize.width - fabSizePx).coerceAtLeast(1f)
        val maxY = (parentSize.height - fabSizePx).coerceAtLeast(1f)
        val resolved = if (posPx == Offset.Unspecified) {
            Offset(nx * maxX, ny * maxY)
        } else {
            posPx
        }

        val tone = homeTone(evidence)
        // Resolved in composable scope: the semantics lambda below is not a composable context.
        val actionLabel = homeActionLabel(evidence)
        val animatedTone by animateColorAsState(
            targetValue = tone,
            animationSpec = MarbleMotionSpecs.Color,
            label = "signature-fab-tone"
        )
        val dragScale by animateFloatAsState(
            targetValue = if (dragging) 1.10f else 1f,
            animationSpec = MarbleMotionSpecs.ResponseFloat,
            label = "signature-fab-scale"
        )
        val breathe = motion.breathe(2_600)
        val sweep = motion.loop(1_400)
        val settledSpin = motion.loop(10_000)
        val shape = CircleShape

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(resolved.x.roundToInt(), resolved.y.roundToInt())
                }
                .size(fabSize)
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .shadow(
                    elevation = 12.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = animatedTone.copy(alpha = .22f),
                    spotColor = animatedTone.copy(alpha = .30f)
                )
                .clip(shape)
                .background(Aether.VoidElevated.copy(alpha = .97f))
                .border(1.4.dp, animatedTone.copy(alpha = .42f), shape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            onPositionSettled(nx, ny)
                        },
                        onDragCancel = {
                            dragging = false
                            onPositionSettled(nx, ny)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        // Read the live state objects (never composition-scoped vals): the drag
                        // coroutine launched by pointerInput keeps running across recompositions,
                        // so a captured val would be stale and the button would snap back.
                        val hostWidth = parentSize.width
                        val hostHeight = parentSize.height
                        if (hostWidth > 0 && hostHeight > 0) {
                            val dragMaxX = (hostWidth - fabSizePx).coerceAtLeast(1f)
                            val dragMaxY = (hostHeight - fabSizePx).coerceAtLeast(1f)
                            val current =
                                if (posPx == Offset.Unspecified) Offset(nx * dragMaxX, ny * dragMaxY)
                                else posPx
                            val next = Offset(
                                (current.x + dragAmount.x).coerceIn(0f, dragMaxX),
                                (current.y + dragAmount.y).coerceIn(0f, dragMaxY)
                            )
                            posPx = next
                            nx = (next.x / dragMaxX).coerceIn(0f, 1f)
                            ny = (next.y / dragMaxY).coerceIn(0f, 1f)
                        }
                    }
                }
                .kineticClickable(
                    role = Role.Button,
                    pressScale = .93f,
                    boundedShape = shape,
                    onClick = onToggle
                )
                .semantics { contentDescription = "$actionLabel floating button" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize().padding(6.dp)) {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // Breathing halo ring.
                drawCircle(
                    color = animatedTone.copy(alpha = .28f + .22f * breathe),
                    radius = r * (.86f + .06f * breathe),
                    style = Stroke(width = 1.6.dp.toPx())
                )
                if (evidence.connecting) {
                    // Continuous rotation while securing the route.
                    drawArc(
                        color = animatedTone,
                        startAngle = sweep * 360f,
                        sweepAngle = 96f,
                        useCenter = false,
                        topLeft = Offset(c.x - r * .74f, c.y - r * .74f),
                        size = Size(r * 1.48f, r * 1.48f),
                        style = Stroke(width = 3.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                if (evidence.connected) {
                    // Settled ring with a slowly rotating dash constellation.
                    rotate(settledSpin * 360f, pivot = c) {
                        drawCircle(
                            color = animatedTone.copy(alpha = .55f),
                            radius = r * .74f,
                            center = c,
                            style = Stroke(
                                width = 1.6.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 22f))
                            )
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HomeGlyphIcon(
                    when {
                        evidence.connected -> HomeGlyph.CHECK
                        evidence.blocked -> HomeGlyph.RESET
                        else -> HomeGlyph.POWER
                    },
                    animatedTone,
                    Modifier.size(20.dp)
                )
                if (evidence.connected && evidence.pingState == ConnectionPingState.MEASURED) {
                    // MARBLE_SYSTEM_FONT_V113 — the live latency follows the Settings typeface.
                    Text(
                        "${evidence.pingMs}",
                        color = animatedTone,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
    }
}
