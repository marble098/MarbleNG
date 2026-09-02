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
//   * server rail with the servers chosen in the Library source selector — on/off;
//   * server card backgrounds — glass / accent color / plain;
//   * connection style switcher at the bottom of the page — on/off;
//   * the accent color driving every one of the studio's animated surfaces.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ProAccent
import com.marbleng.app.model.ProServerCardStyle
import com.marbleng.app.model.ProShortcut
import com.marbleng.app.model.ProxyProfile
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

/** The accent tinted by the live connection state: state always reads first, brand second. */
@Composable
internal fun signatureStatusTone(evidence: HomeEvidence, accent: ProAccent): Color {
    val accentColor = signatureAccentColor(accent)
    return when {
        evidence.connected -> Aether.Emerald
        evidence.connecting -> accentColor
        evidence.blocked -> Aether.Danger
        else -> accentColor
    }
}

// ---------------------------------------------------------------------------------------------
// Style 0 — the Signature studio Home surface
// ---------------------------------------------------------------------------------------------

/**
 * The professional, fully-customizable Home. One column of studio modules over an animated
 * accent aurora: status banner, corner action cluster, the power instrument, the shared
 * evidence blocks in their Signature skin, the server rail and the bottom style switcher.
 */
@Composable
internal fun HomeStyleSignature(
    evidence: HomeEvidence,
    actions: HomeActions,
    pro: HomeProContext,
    bottomClearance: Dp
) {
    val accent = signatureAccentColor(pro.accent)
    val tone = signatureStatusTone(evidence, pro.accent)
    val motion = MarbleMotion.current
    val drift = motion.loop(18_000)
    val breathe = motion.breathe(4_200)
    val ringSpin = motion.loop(22_000)
    val cometPhase = motion.loop(9_000)
    val microPhase = motion.loop(6_200)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * .34f).coerceIn(220.dp, 330.dp)

        // The studio backdrop owns the whole viewport.
        Canvas(Modifier.matchParentSize()) {
            drawSignatureBackdrop(accent, tone, drift, breathe, ringSpin, evidence.connected)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp, bottom = bottomClearance),
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
                HomePowerControl(
                    evidence = evidence,
                    tone = tone,
                    onToggle = actions.onToggleConnection,
                    flavor = HomeFlavor.PRO,
                    diameter = 132.dp,
                    haloBrush = Brush.radialGradient(
                        listOf(
                            accent.copy(alpha = .30f + .12f * breathe),
                            accent.copy(alpha = .10f),
                            Color.Transparent
                        )
                    )
                )
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

            if (pro.showServerRail) {
                SignatureServerRail(pro = pro, actions = actions)
            }

            if (pro.showStyleSwitcher) {
                SignatureStyleSwitcher(
                    activeStyle = pro.selectedHomeStyle,
                    onSelect = pro.onHomeStyleSelected
                )
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * The full-viewport Signature backdrop: an accent aurora, an instrument ring field, drifting
 * light motes and a fine dot grid. Every element loops seamlessly.
 */
private fun DrawScope.drawSignatureBackdrop(
    accent: Color,
    tone: Color,
    drift: Float,
    breathe: Float,
    ringSpin: Float,
    connected: Boolean
) {
    val w = size.width
    val h = size.height

    // Deep brand wash: brighter around the hero, void at the edges.
    drawRect(
        Brush.verticalGradient(
            listOf(
                accent.copy(alpha = .08f + .03f * breathe),
                Color.Transparent,
                accent.copy(alpha = .04f)
            )
        )
    )

    // Aurora halos breathing behind the hero.
    val hero = Offset(w * .5f, h * .26f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = .14f + .06f * breathe), Color.Transparent),
            center = hero,
            radius = w * .80f
        ),
        radius = w * .80f,
        center = hero
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

    // Drifting light motes rising through the studio; each fades in at the bottom and out at
    // the top, so the drift loop is seamless.
    repeat(16) { index ->
        val lane = hash01Local(index * 13 + 5)
        val speed = .35f + hash01Local(index * 17 + 2) * .7f
        val progress = (drift * speed + hash01Local(index * 19 + 7)) % 1f
        val x = w * lane + sin((progress * 3f + index) * PI.toFloat()) * w * .012f
        val y = h * (1f - progress)
        val fade = sin(progress * PI.toFloat())
        drawCircle(
            color = accent.copy(alpha = (if (connected) .26f else .13f) * fade),
            radius = (.8f + 1.5f * hash01Local(index * 23 + 11)).dp.toPx(),
            center = Offset(x, y)
        )
    }

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
                onDismissRequest = { menuOpen = false }
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
// Server rail
// ---------------------------------------------------------------------------------------------

/**
 * The Signature server rail: the servers of the Library source the user selected, rendered as
 * chips whose background follows the chosen card style (glass / accent / plain). Tapping a chip
 * connects to that server; the server carrying traffic is framed in emerald.
 */
@Composable
private fun SignatureServerRail(
    pro: HomeProContext,
    actions: HomeActions
) {
    val t = Tr.now
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeGlyphIcon(HomeGlyph.LIBRARY, Aether.InkFaint, Modifier.size(12.dp))
            Text(
                t.proServers.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "• ${pro.railLabel}",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (pro.railProfiles.isEmpty()) {
            Text(
                t.proServersDetail,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pro.railProfiles.forEach { profile ->
                    SignatureServerChip(
                        profile = profile,
                        active = pro.connected && profile.id == pro.activeProfileId,
                        cardStyle = pro.cardStyle,
                        onClick = { actions.onConnectProfile(profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureServerChip(
    profile: ProxyProfile,
    active: Boolean,
    cardStyle: ProServerCardStyle,
    onClick: () -> Unit
) {
    val frame = if (active) Aether.Emerald else Aether.Cyan
    val name = stripLeadingFlag(profile.name).ifBlank { profile.name }
    val flag = leadingFlagGlyph(profile.name)
    val shape = RoundedCornerShape(15.dp)

    // The three user-selectable card personalities: frosted glass, solid accent tint, plain.
    val (fill, borderTone) = when (cardStyle) {
        ProServerCardStyle.GLASS ->
            Aether.BarGlass to frame.copy(alpha = if (active) .55f else .24f)
        ProServerCardStyle.ACCENT ->
            frame.copy(alpha = if (active) .20f else .10f) to frame.copy(alpha = if (active) .60f else .30f)
        ProServerCardStyle.PLAIN ->
            Aether.VoidElevated to frame.copy(alpha = if (active) .50f else .16f)
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(fill)
            .border(
                if (active) 1.4.dp else 1.dp,
                borderTone,
                shape
            )
            .kineticClickable(role = Role.Button, boundedShape = shape, onClick = onClick)
            .semantics {
                contentDescription = "Connect to ${profile.name}"
            }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            flag ?: profile.scheme.trim().take(1).uppercase().ifBlank { "M" },
            color = frame,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                name,
                color = Aether.Ink,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                profile.scheme.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        if (active) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Aether.Emerald)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Style switcher
// ---------------------------------------------------------------------------------------------

/** Short style names for the switcher chips. */
@Composable
private fun signatureStyleLabel(style: HomeStyle): String = when (style) {
    HomeStyle.PRO -> Tr.now.stylePro
    HomeStyle.BIOLUMINESCENT -> Tr.now.styleBioluminescent
    HomeStyle.COSMIC_ORBIT -> Tr.now.styleCosmicOrbit
    HomeStyle.COSMIC_IMMERSION -> Tr.now.styleCosmicImmersion
    HomeStyle.PARAMETRIC -> Tr.now.styleParametric
}

/**
 * Bottom-of-page connection style switcher: every Home presentation reachable in one tap,
 * without leaving the screen. The chip of the active style carries the accent frame.
 */
@Composable
private fun SignatureStyleSwitcher(
    activeStyle: HomeStyle,
    onSelect: (HomeStyle) -> Unit
) {
    val t = Tr.now
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            t.proStyleSwitcher.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            HomeStyle.entries.forEach { style ->
                val active = style == activeStyle
                val shape = RoundedCornerShape(12.dp)
                val fill by animateColorAsState(
                    targetValue = if (active) Aether.Cyan.copy(alpha = .13f) else Aether.VoidElevated.copy(alpha = .85f),
                    animationSpec = MarbleMotionSpecs.Color,
                    label = "signature-style-chip"
                )
                Text(
                    signatureStyleLabel(style),
                    color = if (active) Aether.Cyan else Aether.InkMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(shape)
                        .background(fill)
                        .border(
                            1.dp,
                            if (active) Aether.Cyan.copy(alpha = .45f) else Aether.GlassBorderSoft,
                            shape
                        )
                        .kineticClickable(
                            role = Role.Button,
                            boundedShape = shape,
                            showIndication = false,
                            onClick = { onSelect(style) }
                        )
                        .semantics { selected = active }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
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
