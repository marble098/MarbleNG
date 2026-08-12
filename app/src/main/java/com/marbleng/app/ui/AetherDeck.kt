package com.marbleng.app.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.BenchMode
import com.marbleng.app.model.ProxyProfile
import kotlinx.coroutines.launch

// =============================================================================
// STATE MODEL
//
// A single immutable snapshot the whole deck renders from. Keeping the UI a
// pure function of `ProxyState` makes every phase trivially previewable and
// keeps the composables free of repository plumbing.
// =============================================================================

enum class ConnectionPhase { Disconnected, Connecting, Connected, Blocked }

data class ProxyState(
    val phase: ConnectionPhase,
    val nodeName: String,
    val endpoint: String,
    val regionFlag: String = "🌐", // 🌐
    val pingMs: Int = 0,
    val downloadKbps: Double = 0.0,
    val uploadKbps: Double = 0.0,
    val libraryCount: Int = 0,
    val routingMode: BenchMode = BenchMode.BALANCED,
    val mode: String = "vpn", // "vpn" | "proxy"
    val localPort: Int = 10101,
    val routingOn: Boolean = false,
    val statusLine: String = "",
) {
    val isConnected get() = phase == ConnectionPhase.Connected
    val isBusy get() = phase == ConnectionPhase.Connecting
    val isProxy get() = mode == "proxy"

    companion object {
        /** Mock snapshot used by @Preview and to demonstrate reactive theming. */
        val PreviewConnected = ProxyState(
            phase = ConnectionPhase.Connected,
            nodeName = "Amsterdam · Reality Vision",
            endpoint = "socks5h · 127.0.0.1:10808",
            regionFlag = "🇳🇱", // 🇳🇱
            pingMs = 42,
            downloadKbps = 8421.0,
            uploadKbps = 1180.0,
            libraryCount = 24,
            routingMode = BenchMode.BALANCED,
            statusLine = "Fail-closed tunnel · privacy sentinel armed",
        )
    }
}

/** Projects live repository state into the immutable deck snapshot. */
fun AppRepository.toProxyState(): ProxyState {
    val phase = when {
        state == "CONNECTED" -> ConnectionPhase.Connected
        state == "BLOCKED" -> ConnectionPhase.Blocked
        busy -> ConnectionPhase.Connecting
        else -> ConnectionPhase.Disconnected
    }
    // Live ping from the tunnel sampler; fall back to the last benchmark for this node.
    val ping = when {
        livePingMs > 0 -> livePingMs
        else -> benchmarks.firstOrNull { it.name == stateDetail && it.success > 0 }?.latencyMs?.toInt() ?: 0
    }
    return ProxyState(
        phase = phase,
        nodeName = when (phase) {
            ConnectionPhase.Connected -> stateDetail.ifBlank { "Secured node" }
            ConnectionPhase.Connecting -> "Locating fastest node"
            ConnectionPhase.Blocked -> "Tunnel blocked · tap to retry"
            ConnectionPhase.Disconnected -> "No active node"
        },
        endpoint = if (settings.tunnelMode == "proxy") "SOCKS · 127.0.0.1:${settings.localProxyPort}" else "socks5h · 127.0.0.1:${settings.socksPort}",
        pingMs = ping,
        downloadKbps = liveDownBps / 1024.0,
        uploadKbps = liveUpBps / 1024.0,
        libraryCount = profiles.size,
        routingMode = settings.benchMode,
        mode = settings.tunnelMode,
        localPort = settings.localProxyPort,
        routingOn = settings.routingEnabled,
        statusLine = if (busy) message else stateDetail,
    )
}

// =============================================================================
// SCREEN — MAIN CONTROL DECK
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherDeck(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onOpenLibrary: () -> Unit,
    onDialog: (String) -> Unit,
) {
    val state = repo.toProxyState()
    var engineRoomOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Aether.Void,
                    0.55f to Aether.Void,
                    1f to Aether.VoidElevated,
                ),
            ),
    ) {
        DeckTopBar(
            state = state,
            onEngineRoom = { engineRoomOpen = true },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            ConnectionModeToggle(
                mode = state.mode,
                enabled = state.phase == ConnectionPhase.Disconnected || state.phase == ConnectionPhase.Blocked,
                onSelect = { repo.updateSettings(repo.settings.copy(tunnelMode = it)) },
            )

            ConnectionHero(
                state = state,
                onTap = {
                    when {
                        state.isConnected && state.isProxy -> repo.stopLocalProxy()
                        state.isConnected -> repo.stopVpn()
                        state.isProxy -> repo.autoProxy()
                        else -> repo.auto(onConnect)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            QuickActions(
                state = state,
                onSmartRoute = { repo.smart(onConnect) },
                onNodeLibrary = onOpenLibrary,
                onPrivacyShield = { repo.audit() },
            )

            RoutingIntelligence(
                selected = state.routingMode,
                onSelect = { repo.updateSettings(repo.settings.copy(benchMode = it)) },
            )
        }
    }

    if (engineRoomOpen) {
        DiagnosticsBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { engineRoomOpen = false },
            onCoreUpdate = { repo.checkCoreUpdates() },
            onImport = onOpenLibrary,
            onDialog = onDialog,
        )
    }
}

// =============================================================================
// TOP BAR
// =============================================================================

@Composable
private fun DeckTopBar(state: ProxyState, onEngineRoom: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Aether Flow",
                style = MaterialTheme.typography.headlineMedium,
                color = Aether.Ink,
            )
            Text(
                state.statusLine.ifBlank { "Invisible network nexus" },
                style = MaterialTheme.typography.labelSmall,
                color = Aether.InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(state)
        Spacer(Modifier.width(10.dp))
        // Engine Room handle — also reachable via swipe-up affordance below the deck.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Aether.Glass)
                .clickable(onClick = onEngineRoom),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚙", style = MaterialTheme.typography.titleMedium, color = Aether.InkMuted) // ⚙
        }
    }
}

@Composable
private fun StatusPill(state: ProxyState) {
    val (dot, label) = when (state.phase) {
        ConnectionPhase.Connected -> Aether.Emerald to "SECURED"
        ConnectionPhase.Connecting -> Aether.Cyan to "LINKING"
        ConnectionPhase.Blocked -> Aether.Danger to "BLOCKED"
        ConnectionPhase.Disconnected -> Aether.SlateBright to "OFFLINE"
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Aether.Glass)
            .border(BorderStroke(1.dp, Aether.GlassBorderSoft), CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Aether.InkMuted)
    }
}

// =============================================================================
// CONNECTION MODE — Full Tunnel (VPN) vs Local Proxy (port selector)
// =============================================================================

@Composable
private fun ConnectionModeToggle(mode: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Aether.Glass)
            .border(BorderStroke(1.dp, Aether.GlassBorderSoft), RoundedCornerShape(18.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ModeSegment("🛰", "Full Tunnel", "Device-wide VPN", mode == "vpn", enabled, Modifier.weight(1f)) { onSelect("vpn") }
        ModeSegment("🔌", "Local Proxy", "SOCKS for chosen apps", mode == "proxy", enabled, Modifier.weight(1f)) { onSelect("proxy") }
    }
}

@Composable
private fun ModeSegment(
    glyph: String, title: String, caption: String,
    selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    val fill by animateColorAsState(if (selected) Aether.Amethyst.copy(alpha = 0.18f) else Color.Transparent, tween(250), label = "segFill")
    val border by animateColorAsState(if (selected) Aether.Amethyst else Color.Transparent, tween(250), label = "segBorder")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (enabled) Aether.Ink else Aether.InkFaint)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =============================================================================
// HERO — THE CONNECTION ORB
//
// A fluid circular nexus. `updateTransition` cross-fades the accent between
// phases; a `rememberInfiniteTransition` drives the "breathing" pulse and the
// slow rotation of the conic ring; a tap fires an expanding ripple ring.
// =============================================================================

@Composable
fun ConnectionHero(
    state: ProxyState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = state.phase
    val active = phase == ConnectionPhase.Connected || phase == ConnectionPhase.Connecting

    // --- Phase-driven accent cross-fade --------------------------------------
    val transition = updateTransition(phase, label = "phase")
    val accentA by transition.animateColor(
        transitionSpec = { tween(700, easing = FastOutSlowInEasing) }, label = "accentA",
    ) {
        when (it) {
            ConnectionPhase.Connected -> Aether.Amethyst
            ConnectionPhase.Connecting -> Aether.Amethyst
            ConnectionPhase.Blocked -> Aether.Danger
            ConnectionPhase.Disconnected -> Aether.Slate
        }
    }
    val accentB by transition.animateColor(
        transitionSpec = { tween(700, easing = FastOutSlowInEasing) }, label = "accentB",
    ) {
        when (it) {
            ConnectionPhase.Connected -> Aether.Cyan
            ConnectionPhase.Connecting -> Aether.AmethystBright
            ConnectionPhase.Blocked -> Aether.DangerBright
            ConnectionPhase.Disconnected -> Aether.SlateBright
        }
    }

    // --- Continuous motion ----------------------------------------------------
    val loop = rememberInfiniteTransition(label = "orb")
    val breathe by loop.animateFloat(
        initialValue = 0.965f,
        targetValue = 1.055f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val spin by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (phase == ConnectionPhase.Connecting) 2600 else 11000, easing = LinearEasing),
        ),
        label = "spin",
    )
    val pulse = if (active) breathe else 1f

    // --- Tap ripple -----------------------------------------------------------
    var tapKey by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(tapKey) {
        if (tapKey == 0) return@LaunchedEffect
        ripple.snapTo(0f)
        ripple.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .drawBehind {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f

                    // Ambient bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentA.copy(alpha = if (active) 0.42f else 0.20f), Color.Transparent),
                            center = c,
                            radius = r * 1.28f,
                        ),
                        radius = r * 1.28f,
                        center = c,
                    )
                    // Glass core disc
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Aether.GlassStrong, Aether.Glass),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                        ),
                        radius = r * 0.80f,
                        center = c,
                    )
                    // Rotating conic accent ring
                    rotate(spin, pivot = c) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(accentA, accentB, accentA.copy(alpha = 0.15f), accentA),
                                center = c,
                            ),
                            radius = r * 0.80f * pulse,
                            center = c,
                            style = Stroke(width = r * 0.045f),
                        )
                    }
                    // Inner hairline
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = r * 0.66f,
                        center = c,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                    // Tap ripple
                    if (ripple.value in 0.001f..0.999f) {
                        drawCircle(
                            color = accentB.copy(alpha = (1f - ripple.value) * 0.55f),
                            radius = r * (0.60f + ripple.value * 0.55f),
                            center = c,
                            style = Stroke(width = r * 0.03f * (1f - ripple.value)),
                        )
                    }
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    tapKey++
                    onTap()
                },
            contentAlignment = Alignment.Center,
        ) {
            OrbFace(state)
        }
    }
}

@Composable
private fun OrbFace(state: ProxyState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val glyph = when (state.phase) {
            ConnectionPhase.Connected -> "⚡" // ⚡
            ConnectionPhase.Connecting -> "∙∙∙" // ···
            ConnectionPhase.Blocked -> "⚠" // ⚠
            ConnectionPhase.Disconnected -> "⏻" // ⏻ power
        }
        Text(
            glyph,
            style = MaterialTheme.typography.displayLarge,
            color = Aether.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when (state.phase) {
                ConnectionPhase.Connected -> "CONNECTED"
                ConnectionPhase.Connecting -> "CONNECTING"
                ConnectionPhase.Blocked -> "TAP TO RETRY"
                ConnectionPhase.Disconnected -> "TAP TO CONNECT"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (state.isConnected) Aether.CyanBright else Aether.InkMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            state.nodeName,
            style = MaterialTheme.typography.bodyMedium,
            color = Aether.InkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 190.dp),
        )

        // Live telemetry — fades in only once there is a tunnel to measure.
        if (state.isConnected) {
            Spacer(Modifier.height(14.dp))
            if (state.isProxy) {
                // Local-proxy mode has no TUN byte counters — surface ping + the address to point apps at.
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Telemetry("PING", if (state.pingMs > 0) "${state.pingMs} ms" else "—")
                    Telemetry("SOCKS", "127.0.0.1")
                    Telemetry("PORT", state.localPort.toString())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Telemetry("PING", if (state.pingMs > 0) "${state.pingMs} ms" else "—")
                    Telemetry("DOWN", formatRate(state.downloadKbps))
                    Telemetry("UP", formatRate(state.uploadKbps))
                }
            }
            if (state.routingOn) {
                Spacer(Modifier.height(8.dp))
                Text("◈ SMART ROUTING ON", style = MaterialTheme.typography.labelSmall, color = Aether.CyanBright)
            }
        }
    }
}

@Composable
private fun Telemetry(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
    }
}

private fun formatRate(kbps: Double): String = when {
    kbps <= 0.0 -> "—"
    kbps >= 1024 -> "%.1f MB/s".format(kbps / 1024)
    else -> "%.0f KB/s".format(kbps)
}

// =============================================================================
// SMART ACTION ROW — exactly three contextual cards
// =============================================================================

@Composable
fun QuickActions(
    state: ProxyState,
    onSmartRoute: () -> Unit,
    onNodeLibrary: () -> Unit,
    onPrivacyShield: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickActionCard(
            glyph = "🧭", // 🧭
            title = "Smart Route",
            caption = "Auto-pick best egress",
            modifier = Modifier.weight(1f),
            onClick = onSmartRoute,
        )
        QuickActionCard(
            glyph = state.regionFlag,
            title = "Node Library",
            caption = "${state.libraryCount} nodes",
            modifier = Modifier.weight(1f),
            onClick = onNodeLibrary,
        )
        QuickActionCard(
            glyph = "🛡", // 🛡
            title = "Privacy Shield",
            caption = "Audit leaks",
            modifier = Modifier.weight(1f),
            onClick = onPrivacyShield,
        )
    }
}

@Composable
private fun QuickActionCard(
    glyph: String,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Aether.Glass)
            .border(BorderStroke(1.dp, Aether.GlassBorderSoft), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.headlineMedium)
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = Aether.Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = Aether.InkFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// =============================================================================
// ROUTING INTELLIGENCE — cryptic REL/BAL/FAS -> human selector cards
// =============================================================================

private data class RoutingOption(
    val mode: BenchMode,
    val glyph: String,
    val title: String,
    val description: String,
    val accent: Color,
)

private val routingOptions = listOf(
    RoutingOption(BenchMode.RELIABLE, "🛡", "Reliability Mode", "Locks onto the most stable, long-lived nodes.", Aether.Cyan),
    RoutingOption(BenchMode.BALANCED, "⚖", "Balanced", "The optimal blend of speed and stability.", Aether.Amethyst),
    RoutingOption(BenchMode.FAST, "⚡", "Maximum Speed", "Chases the absolute lowest-latency egress.", Aether.AmethystBright),
    RoutingOption(BenchMode.TURBO, "🚀", "Turbo Burst", "Aggressive parallel probing for peak throughput.", Aether.CyanBright),
)

@Composable
fun RoutingIntelligence(selected: BenchMode, onSelect: (BenchMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "ROUTING INTELLIGENCE",
            style = MaterialTheme.typography.labelSmall,
            color = Aether.InkFaint,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            routingOptions.forEach { option ->
                RoutingCard(
                    option = option,
                    selected = option.mode == selected,
                    onClick = { onSelect(option.mode) },
                )
            }
        }
    }
}

@Composable
private fun RoutingCard(option: RoutingOption, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(
        if (selected) option.accent else Aether.GlassBorderSoft, tween(300), label = "routeBorder",
    )
    val fill by animateColorAsState(
        if (selected) option.accent.copy(alpha = 0.18f) else Aether.Glass,
        tween(300), label = "routeFill",
    )
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.glyph, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (selected) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(option.accent))
            }
        }
        Text(option.title, style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
        Text(
            option.description,
            style = MaterialTheme.typography.labelSmall,
            color = Aether.InkMuted,
        )
    }
}

// =============================================================================
// ENGINE ROOM — the developer sandbox, swept into a bottom sheet
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCoreUpdate: () -> Unit,
    onImport: () -> Unit,
    onDialog: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) = scope.launch {
        sheetState.hide()
    }.invokeOnCompletion {
        onDismiss()
        after()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Aether.VoidElevated,
        contentColor = Aether.Ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Engine Room", style = MaterialTheme.typography.headlineMedium, color = Aether.Ink)
            Text(
                "Advanced diagnostics & core internals",
                style = MaterialTheme.typography.labelSmall,
                color = Aether.InkFaint,
            )
            Spacer(Modifier.height(14.dp))

            EngineGroup("Observability") {
                EngineRow("🧾", "Runtime Logs", "Live Xray + HEV tunnel bundle") { close { onDialog("Logs") } }
                EngineRow("🕙", "Connection History", "Recent sessions & reasons") { close { onDialog("History") } }
            }
            EngineGroup("Integrity") {
                EngineRow("🩺", "System Doctor", "Native binary & bridge checks") { close { onDialog("System Doctor") } }
                EngineRow("🗂", "Capabilities", "Supported protocols & transports") { close { onDialog("Capabilities") } }
                EngineRow("🔐", "Core Lock", "Pinned core manifest") { close { onDialog("Core lock") } }
            }
            EngineGroup("Maintenance") {
                EngineRow("⬆", "Core Update", "Check Xray / HEV releases") { close { onCoreUpdate() } }
                EngineRow("📥", "Import Xray JSON", "Paste or load raw configs") { close { onImport() } }
            }
        }
    }
}

@Composable
private fun EngineGroup(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(6.dp))
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Aether.InkFaint,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Aether.Glass)
            .border(BorderStroke(1.dp, Aether.GlassBorderSoft), RoundedCornerShape(20.dp)),
    ) { content() }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun EngineRow(glyph: String, title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Aether.GlassStrong),
                contentAlignment = Alignment.Center,
            ) { Text(glyph, style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = Aether.InkFaint)
        }
    }
}
