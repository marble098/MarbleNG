package com.marbleng.app.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

private enum class AetherTab(val label: String, val glyph: String) {
    DECK("Deck", "⌁"),
    LIBRARY("Library", "▦"),
    LAB("Lab", "◎"),
    RADAR("Radar", "◉"),
    SETTINGS("Settings", "⚙")
}

private data class InstalledApp(val label: String, val packageName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Aether2026App(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit
) {
    var tab by remember { mutableStateOf(AetherTab.DECK) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(repo.message, repo.busy) {
        if (!repo.busy && repo.message.isNotBlank()) {
            snackbar.showSnackbar(repo.message, duration = SnackbarDuration.Short)
            repo.clearMessage()
        }
    }

    Scaffold(
        containerColor = Aether.Void,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AetherBottomBar(
                selected = tab,
                onSelect = { tab = it },
                isLight = repo.settings.theme.equals("light", true),
                onTheme = {
                    repo.updateSettings(
                        repo.settings.copy(
                            theme = if (repo.settings.theme.equals("light", true)) "dark" else "light"
                        )
                    )
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Aether.Void,
                            Aether.Amethyst.copy(alpha = 0.035f),
                            Aether.Void
                        )
                    )
                )
        ) {
            when (tab) {
                AetherTab.DECK -> Deck2026(
                    repo = repo,
                    onConnect = onConnect,
                    onLibrary = { tab = AetherTab.LIBRARY },
                    onAdvanced = { tab = AetherTab.SETTINGS }
                )
                AetherTab.LIBRARY -> Library2026(repo, onConnect, onImportFile)
                AetherTab.LAB -> Lab2026(repo, onConnect)
                AetherTab.RADAR -> Radar2026(repo)
                AetherTab.SETTINGS -> Settings2026(repo, onDialog = { dialog = it })
            }

            if (repo.busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = Aether.Cyan,
                    trackColor = Color.Transparent
                )
            }
        }
    }

    dialog?.let { what ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
            title = { Text(what) },
            text = {
                SelectionContainer {
                    Text(
                        when (what) {
                            "Logs" -> repo.readLogs()
                            "Capabilities" -> repo.capabilities()
                            "System Doctor" -> repo.doctor()
                            "Core lock" -> repo.coreLock()
                            "History" -> repo.history.takeLast(80).asReversed().joinToString("\n") {
                                "${DateFormat.getDateTimeInstance().format(Date(it.at))} • ${it.name} • ${it.reason}"
                            }
                            else -> "MarbleNG"
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun AetherBottomBar(
    selected: AetherTab,
    onSelect: (AetherTab) -> Unit,
    isLight: Boolean,
    onTheme: () -> Unit
) {
    Surface(
        color = Aether.VoidElevated.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Aether.GlassBorderSoft)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(70.dp)
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AetherTab.entries.forEach { item ->
                val active = selected == item
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (active) {
                                Brush.verticalGradient(
                                    listOf(
                                        Aether.Amethyst.copy(alpha = 0.20f),
                                        Aether.Cyan.copy(alpha = 0.08f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .clickable { onSelect(item) }
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        item.glyph,
                        color = if (active) Aether.Cyan else Aether.InkFaint,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        item.label,
                        color = if (active) Aether.Ink else Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(width = 42.dp, height = 55.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Aether.Glass)
                    .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(15.dp))
                    .clickable(onClick = onTheme),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isLight) "☀" else "☾", color = Aether.Cyan)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, badge: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Aether.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Aether.InkFaint)
        }
        badge?.let { Pill(it, Aether.Cyan) }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background((if (strong) Aether.GlassStrong else Aether.Glass).copy(alpha = 0.95f))
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun Pill(text: String, color: Color = Aether.Amethyst) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.11f))
            .border(1.dp, color.copy(alpha = 0.24f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(title.uppercase(), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Deck — live telemetry + route visualization
// -------------------------------------------------------------------------------------------------

@Composable
private fun Deck2026(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onLibrary: () -> Unit,
    onAdvanced: () -> Unit
) {
    val downHistory = remember { mutableStateListOf<Float>() }
    val upHistory = remember { mutableStateListOf<Float>() }
    val pingHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        while (true) {
            downHistory.add(repo.liveDownBps.toFloat())
            upHistory.add(repo.liveUpBps.toFloat())
            pingHistory.add(repo.livePingMs.toFloat())
            while (downHistory.size > 42) downHistory.removeAt(0)
            while (upHistory.size > 42) upHistory.removeAt(0)
            while (pingHistory.size > 42) pingHistory.removeAt(0)
            delay(1000)
        }
    }

    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING"
    val profileName = repo.stateDetail.ifBlank { repo.lastProfile()?.name ?: "No active node" }
    val chainExit = repo.profiles.firstOrNull { it.id == repo.settings.chainSecondProfileId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MarbleNG", style = MaterialTheme.typography.headlineMedium, color = Aether.Ink)
                    Text("Aether Flow • Xray control plane", color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
                }
                Pill(
                    when (repo.state) {
                        "CONNECTED" -> "ONLINE"
                        "CONNECTING" -> "STARTING"
                        "BLOCKED" -> "BLOCKED"
                        else -> "OFFLINE"
                    },
                    when (repo.state) {
                        "CONNECTED" -> Aether.Emerald
                        "CONNECTING" -> Aether.Cyan
                        "BLOCKED" -> Aether.Danger
                        else -> Aether.InkFaint
                    }
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        (if (connected) Aether.Cyan else Aether.Amethyst).copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                (if (connected) Aether.Cyan else Aether.Amethyst).copy(alpha = 0.45f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (connected) "✓" else "⌁",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Aether.Ink
                        )
                    }

                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profileName,
                            color = Aether.Ink,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when (repo.settings.connectionMode) {
                                ConnectionMode.FULL_TUN -> "Full TUN • encrypted device route"
                                ConnectionMode.LOCAL_PROXY -> "SOCKS5 • 127.0.0.1:${repo.settings.localProxyPort}"
                            },
                            color = Aether.InkMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (repo.settings.chainEnabled && chainExit != null) {
                            Text(
                                "2-hop • entry → ${chainExit.name}",
                                color = Aether.Cyan,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (connected || connecting) repo.stopVpn() else repo.auto(onConnect)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connected) Aether.GlassStrong else Aether.Amethyst,
                            contentColor = Aether.Ink
                        )
                    ) {
                        Text(if (connected) "Disconnect" else if (connecting) "Stop" else "Connect")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("HTTPS RTT", if (repo.livePingMs > 0) "${repo.livePingMs} ms" else "—", Modifier.weight(1f))
                    Metric("DOWN", rate(repo.liveDownBps), Modifier.weight(1f))
                    Metric("UP", rate(repo.liveUpBps), Modifier.weight(1f))
                }

                SignalChart(
                    primary = downHistory,
                    secondary = upHistory,
                    modifier = Modifier.fillMaxWidth().height(92.dp)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LIVE TRAFFIC", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "↓ ${rate(repo.liveDownBps)}   ↑ ${rate(repo.liveUpBps)}",
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        item {
            SectionTitle("Latency pulse", "Rolling HTTPS path latency, not localhost handshake")
            GlassCard(Modifier.fillMaxWidth()) {
                SignalChart(primary = pingHistory, modifier = Modifier.fillMaxWidth().height(72.dp))
            }
        }

        item {
            SectionTitle("Transport")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Aether.Glass)
                    .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(18.dp))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ModeChip(
                    text = "Full TUN",
                    active = repo.settings.connectionMode == ConnectionMode.FULL_TUN,
                    modifier = Modifier.weight(1f)
                ) { repo.setConnectionMode(ConnectionMode.FULL_TUN) }

                ModeChip(
                    text = "Local :${repo.settings.localProxyPort}",
                    active = repo.settings.connectionMode == ConnectionMode.LOCAL_PROXY,
                    modifier = Modifier.weight(1f)
                ) { repo.setConnectionMode(ConnectionMode.LOCAL_PROXY) }
            }
        }

        item {
            SectionTitle("Quick actions")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile("◎", "Smart test", "Rank real routes", Modifier.weight(1f)) { repo.smart(onConnect) }
                ActionTile("▦", "Library", "${repo.profiles.size} nodes", Modifier.weight(1f), onLibrary)
                ActionTile("◇", "Privacy", "Audit route", Modifier.weight(1f), repo::audit)
            }
        }

        item {
            SectionTitle("Route graph", "See the active route as nodes instead of a wall of toggles")
            GlassCard(Modifier.fillMaxWidth()) {
                RouteNode("CLIENT", "Android apps", Aether.Amethyst)
                RouteArrow()
                RouteNode(
                    if (repo.settings.connectionMode == ConnectionMode.FULL_TUN) "TUN" else "SOCKS",
                    if (repo.settings.connectionMode == ConnectionMode.FULL_TUN) "HEV → Xray" else "127.0.0.1:${repo.settings.localProxyPort}",
                    Aether.Cyan
                )
                RouteArrow()
                RouteNode("ENTRY", profileName, Aether.Emerald)
                if (repo.settings.chainEnabled && chainExit != null) {
                    RouteArrow()
                    RouteNode("EXIT", chainExit.name, Aether.Amber)
                }
                RouteArrow()
                RouteNode("INTERNET", repo.settings.routingMode.name.replace('_', ' '), Aether.InkMuted)
                TextButton(onClick = onAdvanced) { Text("Open advanced route controls →") }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Aether.Void.copy(alpha = 0.55f))
            .padding(11.dp)
    ) {
        Text(label, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Aether.Ink, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun SignalChart(
    primary: List<Float>,
    secondary: List<Float>? = null,
    modifier: Modifier = Modifier
) {
    val c1 = Aether.Cyan
    val c2 = Aether.Amethyst
    val grid = Aether.GlassBorderSoft

    Canvas(modifier) {
        fun drawSeries(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val high = max(values.maxOrNull() ?: 1f, 1f)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1).coerceAtLeast(1)
                val y = size.height - (value / high).coerceIn(0f, 1f) * size.height * 0.82f - size.height * 0.08f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }

        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        drawSeries(primary, c1)
        secondary?.let { drawSeries(it, c2) }
    }
}

@Composable
private fun ModeChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (active) Aether.Amethyst.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (active) Aether.Amethyst.copy(alpha = 0.40f) else Color.Transparent,
                RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (active) Aether.Ink else Aether.InkMuted, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ActionTile(
    glyph: String,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(Aether.Glass)
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(glyph, color = Aether.Cyan, style = MaterialTheme.typography.titleMedium)
        Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun RouteNode(title: String, subtitle: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(title, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(60.dp))
        Text(
            subtitle,
            color = Aether.Ink,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RouteArrow() {
    Text("↓", color = Aether.InkFaint, modifier = Modifier.padding(start = 20.dp))
}

// -------------------------------------------------------------------------------------------------
// Library — compact cards + gestures + smart subscription metadata
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Library2026(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var renameText by remember { mutableStateOf("") }

    val visible = repo.profiles.filter {
        search.isBlank() ||
            it.name.contains(search, true) ||
            it.scheme.contains(search, true) ||
            it.host.contains(search, true) ||
            it.transport.contains(search, true)
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename node") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.renameProfile(target.id, renameText)
                        renameTarget = null
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Library", "Subscriptions, nodes and health in one place", "${repo.profiles.size} nodes") }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search nodes, host, protocol…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = { addOpen = !addOpen }) {
                    Text(if (addOpen) "Close" else "+ Add")
                }
            }
        }

        if (addOpen) {
            item {
                GlassCard(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                    Text("Add source", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Subscription URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                repo.addSubscription(sourceName, url)
                                sourceName = ""
                                url = ""
                            },
                            enabled = url.startsWith("http"),
                            modifier = Modifier.weight(1f)
                        ) { Text("Add") }
                        OutlinedButton(onClick = onImportFile, modifier = Modifier.weight(1f)) {
                            Text("Import file")
                        }
                    }
                }
            }
        }

        if (repo.subscriptions.isNotEmpty()) {
            item {
                SectionTitle(
                    "Smart subscriptions",
                    "Auto-refresh, node count, quota and expiry when provider metadata is available"
                )
            }

            items(repo.subscriptions, key = { "sub-${it.id}" }) { sub ->
                val used = (sub.uploadBytes + sub.downloadBytes).coerceAtLeast(0)
                GlassCard(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${repo.profiles.count { it.subscriptionId == sub.id }} nodes • ${relativeTime(sub.updatedAt)}",
                                color = Aether.InkFaint,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { repo.refresh(sub.id) }, enabled = !repo.busy) {
                            Text("Refresh")
                        }
                    }

                    if (sub.totalBytes > 0) {
                        val fraction = (used.toFloat() / sub.totalBytes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${formatBytes(used)} used",
                                color = Aether.InkMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "${formatBytes((sub.totalBytes - used).coerceAtLeast(0))} left",
                                color = Aether.Cyan,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text(
                            "Quota: provider did not expose subscription-userinfo on the last direct refresh.",
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (sub.expireAt > 0) {
                        Text(
                            "Expires ${relativeFuture(sub.expireAt)}",
                            color = if (sub.expireAt < System.currentTimeMillis()) Aether.Danger else Aether.Amber,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = repo::refreshAll,
                        modifier = Modifier.weight(1f),
                        enabled = !repo.busy
                    ) { Text("Refresh all") }
                    OutlinedButton(
                        onClick = repo::testAll,
                        modifier = Modifier.weight(1f),
                        enabled = repo.profiles.isNotEmpty() && !repo.busy
                    ) { Text("Real test all") }
                }
            }
        }

        item {
            SectionTitle(
                "Nodes",
                "Swipe right to rename • swipe left to delete • tap the glowing action to connect"
            )
        }

        if (visible.isEmpty()) {
            item {
                GlassCard(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
                    Text("Nothing here yet", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add a subscription, import a file, or clear your search.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(visible, key = { it.id }) { profile ->
            @Suppress("DEPRECATION")
            val swipeState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    when (value) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            renameTarget = profile
                            renameText = profile.name
                            false
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            repo.removeProfile(profile.id)
                            true
                        }
                        SwipeToDismissBoxValue.Settled -> false
                    }
                }
            )

            SwipeToDismissBox(
                state = swipeState,
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = true,
                backgroundContent = {
                    val deleting = swipeState.targetValue == SwipeToDismissBoxValue.EndToStart
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (deleting) Aether.Danger.copy(alpha = 0.20f)
                                else Aether.Cyan.copy(alpha = 0.16f)
                            )
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (deleting) Arrangement.End else Arrangement.Start
                    ) {
                        Text(
                            if (deleting) "Delete" else "Rename",
                            color = Aether.Ink,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            ) {
                ServerCard(profile, repo, onConnect)
            }
        }
    }
}

@Composable
private fun ServerCard(profile: ProxyProfile, repo: AppRepository, onConnect: (ProxyProfile) -> Unit) {
    val result = repo.benchmarks.firstOrNull { it.profileId == profile.id }
    val active = repo.state == "CONNECTED" && repo.stateDetail == profile.name

    GlassCard(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), strong = active) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Aether.Emerald.copy(alpha = 0.14f)
                        else Aether.Amethyst.copy(alpha = 0.10f)
                    )
                    .border(
                        1.dp,
                        if (active) Aether.Emerald.copy(alpha = 0.32f) else Aether.GlassBorder,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(countryGlyph(profile.host), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Pill(profile.scheme.uppercase(), Aether.Amethyst)
                    if (profile.transport.isNotBlank()) Pill(profile.transport.uppercase(), Aether.Cyan)
                    if (profile.security.isNotBlank() && profile.security != "none") {
                        Pill(profile.security.uppercase(), Aether.Emerald)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                (if (active) Aether.Emerald else Aether.Cyan).copy(alpha = 0.30f),
                                (if (active) Aether.Emerald else Aether.Amethyst).copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        (if (active) Aether.Emerald else Aether.Cyan).copy(alpha = 0.40f),
                        CircleShape
                    )
                    .clickable {
                        if (active) repo.stopVpn() else onConnect(profile)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (active) "■" else "▶", color = Aether.Ink)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Metric(
                "RTT",
                result?.takeIf { it.success > 0 }?.let { "${it.latencyMs.toInt()} ms" } ?: "—",
                Modifier.weight(1f)
            )
            Metric(
                "JITTER",
                result?.takeIf { it.success > 0 }?.let { "${it.jitterMs.toInt()} ms" } ?: "—",
                Modifier.weight(1f)
            )
            Metric(
                "SCORE",
                result?.takeIf { it.success > 0 }?.let { "%.0f".format(it.score) } ?: "—",
                Modifier.weight(1f)
            )
        }

        TextButton(onClick = { repo.fullTest(profile) }, enabled = !repo.busy) {
            Text("Run real tunnel test")
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Lab — numbers become charts and relative scores
// -------------------------------------------------------------------------------------------------

@Composable
private fun Lab2026(repo: AppRepository, onConnect: (ProxyProfile) -> Unit) {
    val modes = listOf(BenchMode.RELIABLE, BenchMode.BALANCED, BenchMode.FAST, BenchMode.TURBO)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                "Lab",
                "Visual benchmark studio for real tunnel quality",
                "${repo.benchmarks.size} results"
            )
        }

        item {
            SectionTitle("Benchmark profile")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = repo.settings.benchMode == mode,
                        onClick = { repo.updateSettings(repo.settings.copy(benchMode = mode)) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        item {
            Button(
                onClick = { repo.smart(onConnect) },
                modifier = Modifier.fillMaxWidth(),
                enabled = repo.profiles.isNotEmpty() && !repo.busy
            ) {
                Text("◎ Benchmark and choose best")
            }
        }

        if (repo.benchmarks.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("No benchmark data yet", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Run a real-tunnel benchmark. Results appear as score bars with RTT, jitter and success rate.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(repo.benchmarks, key = { it.profileId }) { result ->
            val best = repo.benchmarks.maxOfOrNull { it.score }?.coerceAtLeast(1.0) ?: 1.0
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "#${repo.benchmarks.indexOf(result) + 1}",
                        color = Aether.Cyan,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        result.name,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Pill("${result.success}%", if (result.success >= 75) Aether.Emerald else Aether.Amber)
                }

                LinearProgressIndicator(
                    progress = { (result.score / best).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Metric("RTT", "${result.latencyMs.toInt()} ms", Modifier.weight(1f))
                    Metric("JITTER", "${result.jitterMs.toInt()} ms", Modifier.weight(1f))
                    Metric("SPEED", rate(result.bytesPerSecond.toLong()), Modifier.weight(1f))
                }

                TextButton(
                    onClick = { repo.profile(result.profileId)?.let(onConnect) },
                    enabled = result.success > 0
                ) {
                    Text("Use this route →")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Radar — graphical scanner / gate
// -------------------------------------------------------------------------------------------------

@Composable
private fun Radar2026(repo: AppRepository) {
    var channel by remember { mutableStateOf(repo.channels().firstOrNull().orEmpty()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                "Radar",
                "Telegram discovery with a smart quality gate",
                "${repo.radarConfigs.size} passed"
            )
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Aether.Cyan.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("◉", color = Aether.Cyan, style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Telegram scanner", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Fetch → parse → real tunnel gate → optional auto-import",
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("Channel / public URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { repo.telegram(channel) },
                    enabled = channel.isNotBlank() && !repo.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan and validate")
                }
            }
        }

        item {
            SectionTitle("Smart gate")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    "TCP pre-gate",
                    "Quickly reject dead candidates before the expensive tunnel benchmark",
                    repo.settings.telegramTcpGate
                ) {
                    repo.updateSettings(repo.settings.copy(telegramTcpGate = it))
                }

                SettingSwitch(
                    "Auto-import passed",
                    "Passed routes become a managed Telegram source",
                    repo.settings.telegramAutoSub
                ) {
                    repo.updateSettings(repo.settings.copy(telegramAutoSub = it))
                }

                NumberSetting("Minimum success %", repo.settings.telegramPassMinSuccess, 10..100) {
                    repo.updateSettings(repo.settings.copy(telegramPassMinSuccess = it))
                }

                NumberSetting("Max configs", repo.settings.telegramMaxConfigs, 10..300) {
                    repo.updateSettings(repo.settings.copy(telegramMaxConfigs = it))
                }
            }
        }

        if (repo.radarResults.isNotEmpty()) {
            item { SectionTitle("Signal board") }
            items(repo.radarResults.take(20), key = { "radar-${it.profileId}" }) { result ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            result.name,
                            color = Aether.Ink,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Pill(
                            "${result.success}%",
                            if (result.success >= repo.settings.telegramPassMinSuccess) Aether.Emerald else Aether.Danger
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (result.success / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${result.latencyMs.toInt()} ms • jitter ${result.jitterMs.toInt()} ms • score ${"%.0f".format(result.score)}",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (repo.radarConfigs.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = repo::importRadar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import ${repo.radarConfigs.size} passed configs")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Settings — progressive disclosure
// -------------------------------------------------------------------------------------------------

@Composable
private fun Settings2026(repo: AppRepository, onDialog: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                "Settings",
                "Progressive disclosure instead of a wall of equal buttons",
                "2026"
            )
        }

        item {
            ExpandableSettingsCard("Split tunneling", "Per-app TUN policy", "APPS") {
                SplitTunnelSettings(repo)
            }
        }

        item {
            ExpandableSettingsCard("Fragment & Mux", "DPI-resilience and connection aggregation", "XRAY") {
                FragmentMuxSettings(repo)
            }
        }

        item {
            ExpandableSettingsCard("Chain proxy", "Visual two-hop routing", "2-HOP") {
                ChainSettings(repo)
            }
        }

        item {
            ExpandableSettingsCard("DNS", "DoH + TUN resolver policy", "DOH") {
                DnsSettings(repo)
            }
        }

        item {
            ExpandableSettingsCard("Routing", "Geo assets, direct rules and ad blocking", "RULES") {
                RoutingSettings(repo)
            }
        }

        item {
            ExpandableSettingsCard("Subscriptions", "Automatic refresh cadence", "SYNC") {
                SettingSwitch(
                    "Auto refresh",
                    "Refresh stale subscriptions when MarbleNG starts",
                    repo.settings.subscriptionAutoRefresh
                ) {
                    repo.updateSettings(repo.settings.copy(subscriptionAutoRefresh = it))
                }

                NumberSetting("Refresh every (hours)", repo.settings.subscriptionRefreshHours, 1..168) {
                    repo.updateSettings(repo.settings.copy(subscriptionRefreshHours = it))
                }

                OutlinedButton(
                    onClick = repo::refreshAll,
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh now")
                }
            }
        }

        item {
            ExpandableSettingsCard("Maintenance", "Diagnostics and recovery tools", "TOOLS") {
                SettingsAction("System Doctor", "Check native runtime, routing assets and environment") {
                    onDialog("System Doctor")
                }
                SettingsAction("Logs", "Open shareable runtime logs") {
                    onDialog("Logs")
                }
                SettingsAction("Capabilities", "See the active feature matrix") {
                    onDialog("Capabilities")
                }
                SettingsAction("Core lock", "Inspect pinned Xray / HEV metadata") {
                    onDialog("Core lock")
                }
                SettingsAction("History", "Recent connection records") {
                    onDialog("History")
                }
                OutlinedButton(onClick = repo::resetSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset settings")
                }
            }
        }

        item {
            Text(
                "Fragment is attached only to the physical dial path. Mux remains opt-in because it can reduce bulk/video throughput. Two-hop mode preserves the exit transport layer while dialing it through the entry.",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
private fun ExpandableSettingsCard(
    title: String,
    subtitle: String,
    badge: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
            }
            Pill(badge, Aether.Cyan)
            Spacer(Modifier.width(8.dp))
            Text(if (open) "⌃" else "⌄", color = Aether.InkMuted)
        }

        if (open) {
            HorizontalDivider(color = Aether.GlassBorderSoft)
            content()
        }
    }
}

@Composable
private fun SplitTunnelSettings(repo: AppRepository) {
    val context = LocalContext.current
    val pm = context.packageManager
    var search by remember { mutableStateOf("") }

    val apps = remember {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(
                    label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg),
                    packageName = pkg
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    val selected = repo.settings.splitTunnelPackages
        .split(',', '\n', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableSet()

    Text("Policy", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SplitTunnelMode.entries.forEach { mode ->
            FilterChip(
                selected = repo.settings.splitTunnelMode == mode,
                onClick = { repo.updateSettings(repo.settings.copy(splitTunnelMode = mode)) },
                label = {
                    Text(
                        when (mode) {
                            SplitTunnelMode.ALL_APPS -> "All apps"
                            SplitTunnelMode.ONLY_SELECTED -> "Only selected"
                            SplitTunnelMode.BYPASS_SELECTED -> "Bypass selected"
                        }
                    )
                }
            )
        }
    }

    if (repo.settings.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search installed apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("${selected.size} selected", color = Aether.Cyan, style = MaterialTheme.typography.labelSmall)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Aether.Void.copy(alpha = 0.45f))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            apps.filter {
                search.isBlank() ||
                    it.label.contains(search, true) ||
                    it.packageName.contains(search, true)
            }.take(80).forEach { app ->
                val checked = app.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val next = selected.toMutableSet()
                            if (!next.add(app.packageName)) next.remove(app.packageName)
                            repo.updateSettings(
                                repo.settings.copy(splitTunnelPackages = next.sorted().joinToString(","))
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { value ->
                            val next = selected.toMutableSet()
                            if (value) next.add(app.packageName) else next.remove(app.packageName)
                            repo.updateSettings(
                                repo.settings.copy(splitTunnelPackages = next.sorted().joinToString(","))
                            )
                        }
                    )
                    Column {
                        Text(app.label, color = Aether.Ink, style = MaterialTheme.typography.bodyMedium)
                        Text(app.packageName, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Text(
            "The per-app policy is applied on the next Full TUN connection.",
            color = Aether.Amber,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FragmentMuxSettings(repo: AppRepository) {
    SettingSwitch(
        "TLS ClientHello fragmentation",
        "Applies Xray Freedom.fragment only to the physical server dial",
        repo.settings.fragmentEnabled
    ) {
        repo.updateSettings(repo.settings.copy(fragmentEnabled = it))
    }

    if (repo.settings.fragmentEnabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TinyField("Packets", repo.settings.fragmentPackets, Modifier.weight(1f)) {
                repo.updateSettings(repo.settings.copy(fragmentPackets = it))
            }
            TinyField("Length", repo.settings.fragmentLength, Modifier.weight(1f)) {
                repo.updateSettings(repo.settings.copy(fragmentLength = it))
            }
            TinyField("Interval ms", repo.settings.fragmentInterval, Modifier.weight(1f)) {
                repo.updateSettings(repo.settings.copy(fragmentInterval = it))
            }
        }
        Text(
            "Safe starting preset: tlshello • 100-200 bytes • 10-20 ms. More aggressive values can reduce stability.",
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall
        )
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        "Mux / XUDP",
        "Reduces handshake overhead for many small connections; it is not a speed booster",
        repo.settings.muxEnabled
    ) {
        repo.updateSettings(repo.settings.copy(muxEnabled = it))
    }

    if (repo.settings.muxEnabled) {
        NumberSetting("TCP concurrency", repo.settings.muxConcurrency, 1..128) {
            repo.updateSettings(repo.settings.copy(muxConcurrency = it))
        }
        NumberSetting("XUDP concurrency", repo.settings.muxXudpConcurrency, 1..1024) {
            repo.updateSettings(repo.settings.copy(muxXudpConcurrency = it))
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("reject", "allow", "skip").forEach { value ->
                FilterChip(
                    selected = repo.settings.muxUdp443 == value,
                    onClick = { repo.updateSettings(repo.settings.copy(muxUdp443 = value)) },
                    label = { Text("UDP/443 $value") }
                )
            }
        }
    }
}

@Composable
private fun ChainSettings(repo: AppRepository) {
    SettingSwitch(
        "Enable two-hop chain",
        "The node you connect to is the entry; choose another library node as the exit",
        repo.settings.chainEnabled
    ) {
        repo.updateSettings(repo.settings.copy(chainEnabled = it))
    }

    if (repo.settings.chainEnabled) {
        val exit = repo.profiles.firstOrNull { it.id == repo.settings.chainSecondProfileId }

        RouteNode("CLIENT", "Android", Aether.Amethyst)
        RouteArrow()
        RouteNode("ENTRY", repo.lastProfile()?.name ?: "Selected at connect time", Aether.Cyan)
        RouteArrow()
        RouteNode("EXIT", exit?.name ?: "Choose exit node", Aether.Amber)
        RouteArrow()
        RouteNode("INTERNET", "Exit server egress", Aether.Emerald)

        Text("Exit node", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            repo.profiles.take(60).forEach { profile ->
                FilterChip(
                    selected = repo.settings.chainSecondProfileId == profile.id,
                    onClick = {
                        repo.updateSettings(repo.settings.copy(chainSecondProfileId = profile.id))
                    },
                    label = {
                        Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
        }

        Text(
            "The exit node keeps its own REALITY/XHTTP/gRPC/transport settings while its connection is dialed through the entry hop.",
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DnsSettings(repo: AppRepository) {
    Text("Quick presets", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        DnsPreset(
            "Cloudflare",
            repo,
            "1.1.1.1",
            "1.0.0.1",
            "https://1.1.1.1/dns-query",
            "https://1.0.0.1/dns-query"
        )
        DnsPreset(
            "Google",
            repo,
            "8.8.8.8",
            "8.8.4.4",
            "https://8.8.8.8/dns-query",
            "https://8.8.4.4/dns-query"
        )
        DnsPreset(
            "Quad9",
            repo,
            "9.9.9.9",
            "149.112.112.112",
            "https://9.9.9.9/dns-query",
            "https://149.112.112.112/dns-query"
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TinyField("TUN DNS 1", repo.settings.dnsPrimaryIp, Modifier.weight(1f)) {
            repo.updateSettings(repo.settings.copy(dnsPrimaryIp = it))
        }
        TinyField("TUN DNS 2", repo.settings.dnsSecondaryIp, Modifier.weight(1f)) {
            repo.updateSettings(repo.settings.copy(dnsSecondaryIp = it))
        }
    }

    TinyField("Primary DoH", repo.settings.dnsPrimaryDoH, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(dnsPrimaryDoH = it))
    }
    TinyField("Secondary DoH", repo.settings.dnsSecondaryDoH, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(dnsSecondaryDoH = it))
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf("UseIP", "UseIPv4", "UseIPv6", "UseSystem").forEach { strategy ->
            FilterChip(
                selected = repo.settings.dnsQueryStrategy == strategy,
                onClick = { repo.updateSettings(repo.settings.copy(dnsQueryStrategy = strategy)) },
                label = { Text(strategy) }
            )
        }
    }

    Text(
        "Remote DoH is tagged inside Xray and follows the selected proxy path. Local bootstrap DoH is restricted to resolving proxy endpoint domains.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun DnsPreset(
    label: String,
    repo: AppRepository,
    ip1: String,
    ip2: String,
    doh1: String,
    doh2: String
) {
    AssistChip(
        onClick = {
            repo.updateSettings(
                repo.settings.copy(
                    dnsPrimaryIp = ip1,
                    dnsSecondaryIp = ip2,
                    dnsPrimaryDoH = doh1,
                    dnsSecondaryDoH = doh2
                )
            )
        },
        label = { Text(label) }
    )
}

@Composable
private fun RoutingSettings(repo: AppRepository) {
    Text("Routing mode", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RoutingMode.entries.forEach { mode ->
            FilterChip(
                selected = repo.settings.routingMode == mode,
                onClick = { repo.updateSettings(repo.settings.copy(routingMode = mode)) },
                label = { Text(mode.name.replace('_', ' ')) }
            )
        }
    }

    SettingSwitch(
        "Bypass private networks",
        "Keep LAN / RFC1918 traffic direct where the routing mode permits it",
        repo.settings.routeBypassPrivate
    ) {
        repo.updateSettings(repo.settings.copy(routeBypassPrivate = it))
    }

    SettingSwitch(
        "Aggressive ad blocking",
        "Uses the configured geosite ad tag when geosite.dat is available",
        repo.settings.routeBlockAds
    ) {
        repo.updateSettings(repo.settings.copy(routeBlockAds = it))
    }

    TinyField("geoip.dat HTTPS URL", repo.settings.geoIpUrl, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(geoIpUrl = it))
    }
    TinyField("geosite.dat HTTPS URL", repo.settings.geoSiteUrl, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(geoSiteUrl = it))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { repo.prepareRoutingAssets(false) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Prepare assets")
        }
        OutlinedButton(
            onClick = repo::deleteRoutingAssets,
            modifier = Modifier.weight(1f)
        ) {
            Text("Remove assets")
        }
    }

    TinyField("Direct domains", repo.settings.routeDirectDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeDirectDomains = it))
    }
    TinyField("Proxy exceptions", repo.settings.routeProxyDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeProxyDomains = it))
    }
    TinyField("Block domains", repo.settings.routeBlockDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeBlockDomains = it))
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onValue((value - 1).coerceAtLeast(range.first)) }) {
            Text("−")
        }
        Pill(value.toString(), Aether.Cyan)
        TextButton(onClick = { onValue((value + 1).coerceAtMost(range.last)) }) {
            Text("+")
        }
    }
}

@Composable
private fun TinyField(
    label: String,
    value: String,
    modifier: Modifier,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = Aether.Cyan, style = MaterialTheme.typography.titleMedium)
    }
}

private fun rate(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB/s".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB/s".format(bytes / 1024.0)
    else -> "$bytes B/s"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun relativeTime(at: Long): String {
    if (at <= 0) return "never refreshed"
    val delta = System.currentTimeMillis() - at
    return when {
        delta < 60_000 -> "updated now"
        delta < 3_600_000 -> "updated ${delta / 60_000}m ago"
        delta < 86_400_000 -> "updated ${delta / 3_600_000}h ago"
        else -> "updated ${delta / 86_400_000}d ago"
    }
}

private fun relativeFuture(at: Long): String {
    val delta = at - System.currentTimeMillis()
    if (delta <= 0) return "expired"
    return when {
        delta < 3_600_000 -> "in ${delta / 60_000}m"
        delta < 86_400_000 -> "in ${delta / 3_600_000}h"
        else -> "in ${delta / 86_400_000}d"
    }
}

private fun countryGlyph(host: String): String {
    val normalized = host.lowercase()
    return when {
        normalized.endsWith(".de") -> "🇩🇪"
        normalized.endsWith(".nl") -> "🇳🇱"
        normalized.endsWith(".fr") -> "🇫🇷"
        normalized.endsWith(".tr") -> "🇹🇷"
        normalized.endsWith(".us") -> "🇺🇸"
        normalized.endsWith(".uk") || normalized.endsWith(".co.uk") -> "🇬🇧"
        normalized.endsWith(".jp") -> "🇯🇵"
        normalized.endsWith(".sg") -> "🇸🇬"
        else -> "◈"
    }
}
