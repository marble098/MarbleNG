package com.marbleng.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode

private enum class ConnectionPhase { Disconnected, Connecting, Connected, Blocked }

private data class ProxyState(
    val phase: ConnectionPhase,
    val nodeName: String,
    val endpoint: String,
    val pingMs: Int,
    val downloadKbps: Double,
    val uploadKbps: Double,
    val libraryCount: Int,
    val mode: ConnectionMode,
    val routingMode: RoutingMode,
    val statusLine: String
) {
    val isConnected get() = phase == ConnectionPhase.Connected
    val isBusy get() = phase == ConnectionPhase.Connecting
}

private fun AppRepository.toProxyState(): ProxyState {
    val phase = when (state) {
        "CONNECTED" -> ConnectionPhase.Connected
        "CONNECTING" -> ConnectionPhase.Connecting
        "BLOCKED" -> ConnectionPhase.Blocked
        else -> ConnectionPhase.Disconnected
    }
    val fallbackPing = benchmarks.firstOrNull { it.name == stateDetail && it.success > 0 }?.latencyMs?.toInt() ?: 0
    return ProxyState(
        phase = phase,
        nodeName = when (phase) {
            ConnectionPhase.Connected -> stateDetail.ifBlank { "Protected connection" }
            ConnectionPhase.Connecting -> stateDetail.ifBlank { "Preparing route" }
            ConnectionPhase.Blocked -> stateDetail.ifBlank { "Connection blocked" }
            ConnectionPhase.Disconnected -> lastProfile()?.name ?: "No active node"
        },
        endpoint = when (settings.connectionMode) {
            ConnectionMode.FULL_TUN -> "Full-device TUN • internal SOCKS 127.0.0.1:${settings.socksPort}"
            ConnectionMode.LOCAL_PROXY -> "SOCKS5 • 127.0.0.1:${settings.localProxyPort}"
        },
        pingMs = if (livePingMs > 0) livePingMs else fallbackPing,
        downloadKbps = liveDownBps / 1024.0,
        uploadKbps = liveUpBps / 1024.0,
        libraryCount = profiles.size,
        mode = settings.connectionMode,
        routingMode = settings.routingMode,
        statusLine = when (phase) {
            ConnectionPhase.Connected -> "Traffic path is active"
            ConnectionPhase.Connecting -> "Starting Xray and validating route"
            ConnectionPhase.Blocked -> stateDetail
            ConnectionPhase.Disconnected -> "Ready when you are"
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherDeck(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onOpenLibrary: () -> Unit,
    onDialog: (String) -> Unit
) {
    val state = repo.toProxyState()
    var engineRoomOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Aether.Void, MaterialTheme.colorScheme.primary.copy(alpha = 0.035f), Aether.Void)
                )
            ),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { DeckTopBar(state, onEngineRoom = { engineRoomOpen = true }) }
        item {
            ConnectionModeSelector(
                selected = state.mode,
                localPort = repo.settings.localProxyPort,
                onSelect = repo::setConnectionMode
            )
        }
        item {
            ConnectionPanel(
                state = state,
                onToggle = {
                    if (state.isConnected || state.isBusy) repo.stopVpn() else repo.auto(onConnect)
                }
            )
        }
        item {
            QuickActions(
                libraryCount = state.libraryCount,
                onSmart = { repo.smart(onConnect) },
                onLibrary = onOpenLibrary,
                onAudit = repo::audit
            )
        }
        item {
            RoutingSummary(
                selected = state.routingMode,
                onSelect = { repo.updateSettings(repo.settings.copy(routingMode = it)) },
                onPrepare = { repo.prepareRoutingAssets(false) }
            )
        }
        item {
            Text(
                "HTTPS RTT is measured through the complete proxy route and smoothed with a rolling median. Local proxy mode exposes SOCKS5 only on localhost.",
                style = MaterialTheme.typography.bodySmall,
                color = Aether.InkFaint,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }

    if (engineRoomOpen) {
        DiagnosticsBottomSheet(
            onDismiss = { engineRoomOpen = false },
            onCoreUpdate = repo::checkCoreUpdates,
            onImport = onOpenLibrary,
            onDialog = onDialog
        )
    }
}

@Composable
private fun DeckTopBar(state: ProxyState, onEngineRoom: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("MarbleNG", style = MaterialTheme.typography.headlineMedium, color = Aether.Ink)
            Text(
                state.statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = Aether.InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusPill(state.phase)
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier.size(42.dp).clickable(onClick = onEngineRoom),
            shape = CircleShape,
            color = Aether.GlassStrong,
            border = BorderStroke(1.dp, Aether.GlassBorderSoft)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⚙︎", color = Aether.InkMuted, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StatusPill(phase: ConnectionPhase) {
    val color = when (phase) {
        ConnectionPhase.Connected -> Aether.Emerald
        ConnectionPhase.Connecting -> MaterialTheme.colorScheme.secondary
        ConnectionPhase.Blocked -> MaterialTheme.colorScheme.error
        ConnectionPhase.Disconnected -> Aether.InkFaint
    }
    val label = when (phase) {
        ConnectionPhase.Connected -> "ONLINE"
        ConnectionPhase.Connecting -> "STARTING"
        ConnectionPhase.Blocked -> "BLOCKED"
        ConnectionPhase.Disconnected -> "OFFLINE"
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.11f))
            .border(1.dp, color.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ConnectionModeSelector(
    selected: ConnectionMode,
    localPort: Int,
    onSelect: (ConnectionMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("CONNECTION MODE", style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Aether.Glass)
                .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            ModeSegment(
                title = "Full TUN",
                subtitle = "All device traffic",
                selected = selected == ConnectionMode.FULL_TUN,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ConnectionMode.FULL_TUN) }
            )
            Spacer(Modifier.width(4.dp))
            ModeSegment(
                title = "Local proxy",
                subtitle = "127.0.0.1:$localPort",
                selected = selected == ConnectionMode.LOCAL_PROXY,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(ConnectionMode.LOCAL_PROXY) }
            )
        }
        Text(
            if (selected == ConnectionMode.FULL_TUN)
                "Android VpnService captures device traffic."
            else
                "No VPN permission. Point supported apps to SOCKS5 127.0.0.1:$localPort.",
            style = MaterialTheme.typography.bodySmall,
            color = Aether.InkFaint,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ModeSegment(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) primary.copy(alpha = 0.40f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) Aether.Ink else Aether.InkMuted)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (selected) primary else Aether.InkFaint)
    }
}

@Composable
private fun ConnectionPanel(state: ProxyState, onToggle: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val active = state.phase == ConnectionPhase.Connected
    val statusColor = when (state.phase) {
        ConnectionPhase.Connected -> Aether.Emerald
        ConnectionPhase.Connecting -> MaterialTheme.colorScheme.secondary
        ConnectionPhase.Blocked -> MaterialTheme.colorScheme.error
        ConnectionPhase.Disconnected -> Aether.InkFaint
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (active) primary.copy(alpha = 0.15f) else Aether.GlassStrong,
                        Aether.Glass
                    )
                )
            )
            .border(
                1.dp,
                if (active) primary.copy(alpha = 0.32f) else Aether.GlassBorder,
                RoundedCornerShape(24.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Text(
                        when (state.phase) {
                            ConnectionPhase.Connected -> "ACTIVE ROUTE"
                            ConnectionPhase.Connecting -> "NEGOTIATING"
                            ConnectionPhase.Blocked -> "ROUTE BLOCKED"
                            ConnectionPhase.Disconnected -> "READY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    state.nodeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Aether.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.endpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Aether.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onToggle,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) Aether.GlassStrong else primary,
                    contentColor = if (active) Aether.Ink else MaterialTheme.colorScheme.onPrimary
                ),
                border = if (active) BorderStroke(1.dp, Aether.GlassBorder) else null,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    when (state.phase) {
                        ConnectionPhase.Connected -> "Disconnect"
                        ConnectionPhase.Connecting -> "Stop"
                        ConnectionPhase.Blocked -> "Retry"
                        ConnectionPhase.Disconnected -> "Connect"
                    }
                )
            }
        }

        HorizontalDivider(color = Aether.GlassBorderSoft)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                label = "HTTPS RTT",
                value = if (state.pingMs > 0) "${state.pingMs} ms" else "—",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "DOWN",
                value = formatRate(state.downloadKbps),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "UP",
                value = formatRate(state.uploadKbps),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Aether.Void.copy(alpha = 0.48f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = Aether.Ink)
    }
}

@Composable
private fun QuickActions(
    libraryCount: Int,
    onSmart: () -> Unit,
    onLibrary: () -> Unit,
    onAudit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Smart test", "Rank nodes", "◎", Modifier.weight(1f), onSmart)
            QuickAction("Library", "$libraryCount nodes", "▦", Modifier.weight(1f), onLibrary)
            QuickAction("Privacy", "Audit route", "◇", Modifier.weight(1f), onAudit)
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    glyph: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Aether.Glass)
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.labelLarge, color = Aether.Ink, maxLines = 1)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Aether.InkFaint, maxLines = 1)
    }
}

@Composable
private fun RoutingSummary(
    selected: RoutingMode,
    onSelect: (RoutingMode) -> Unit,
    onPrepare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Aether.Glass)
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Routing policy", style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
                Text(
                    "Unmatched traffic always falls back to the proxy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Aether.InkFaint
                )
            }
            TextButton(onClick = onPrepare) { Text("Prepare data") }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            RoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(routingLabel(mode)) }
                )
            }
        }
    }
}

private fun routingLabel(mode: RoutingMode) = when (mode) {
    RoutingMode.PROXY_ALL -> "Proxy all"
    RoutingMode.BYPASS_PRIVATE -> "Private bypass"
    RoutingMode.GEO_DIRECT -> "Geo direct"
    RoutingMode.CUSTOM -> "Custom"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsBottomSheet(
    onDismiss: () -> Unit,
    onCoreUpdate: () -> Unit,
    onImport: () -> Unit,
    onDialog: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        contentColor = Aether.Ink
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Engine room", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Diagnostics and maintenance without crowding the main deck.", style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCoreUpdate, modifier = Modifier.weight(1f)) { Text("Check cores") }
                FilledTonalButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDialog("System Doctor") }, modifier = Modifier.weight(1f)) { Text("Doctor") }
                OutlinedButton(onClick = { onDialog("Logs") }, modifier = Modifier.weight(1f)) { Text("Logs") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDialog("Capabilities") }, modifier = Modifier.weight(1f)) { Text("Capabilities") }
                OutlinedButton(onClick = { onDialog("Core lock") }, modifier = Modifier.weight(1f)) { Text("Core lock") }
            }
        }
    }
}

private fun formatRate(kbps: Double): String = when {
    kbps <= 0.0 -> "—"
    kbps >= 1024.0 -> "%.1f MB/s".format(kbps / 1024.0)
    else -> "%.0f KB/s".format(kbps)
}
