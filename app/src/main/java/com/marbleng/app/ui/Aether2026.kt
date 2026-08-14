package com.marbleng.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class SpatialTab(val label: String, val glyph: String) {
    DECK("Deck", "⌁"),
    LIBRARY("Library", "▦"),
    LAB("Lab", "△"),
    RADAR("Radar", "◉"),
    SETTINGS("Settings", "⚙")
}

private data class InstalledApp(val label: String, val packageName: String)

private data class RadarNode(
    val id: String,
    val name: String,
    val speed: Float,
    val reliability: Float,
    val ping: Float,
    val jitter: Float,
    val colorIndex: Int
)

private data class GateStage(
    val label: String,
    val detail: String,
    val accepted: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Aether2026App(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit
) {
    var tab by remember { mutableStateOf(SpatialTab.DECK) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var settingsFocus by remember { mutableStateOf<String?>(null) }
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
            FloatingSpatialDock(
                selected = tab,
                onSelect = { next ->
                    settingsFocus = null
                    tab = next
                },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Aether.Void)
        ) {
            DeepSpaceBackdrop(Modifier.matchParentSize())

            when (tab) {
                SpatialTab.DECK -> CyberDeck(
                    repo = repo,
                    onConnect = onConnect,
                    onLibrary = { tab = SpatialTab.LIBRARY },
                    onPrivacy = {
                        repo.audit()
                        dialog = "Privacy"
                    },
                    onRouting = {
                        settingsFocus = "Routing"
                        tab = SpatialTab.SETTINGS
                    }
                )
                SpatialTab.LIBRARY -> CyberLibrary(repo, onConnect, onImportFile)
                SpatialTab.LAB -> BenchmarkStudio(repo, onConnect)
                SpatialTab.RADAR -> DiscoveryRadar(repo)
                SpatialTab.SETTINGS -> SpatialSettings(
                    repo,
                    onDialog = { dialog = it },
                    focusSection = settingsFocus
                )
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
            containerColor = Aether.VoidElevated,
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("Close", color = Aether.Cyan)
                }
            },
            title = { Text(what, color = Aether.Ink) },
            text = {
                SelectionContainer {
                    Text(
                        when (what) {
                            "Logs" -> repo.readLogs()
                            "Privacy" -> {
                                val report = repo.privacy
                                when {
                                    repo.state != "CONNECTED" ->
                                        "Connect first. Privacy audit uses the active Xray path."
                                    repo.busy && report == null ->
                                        "Running privacy audit through the active Xray route…"
                                    report == null ->
                                        "No privacy report yet. Tap Privacy after the tunnel is healthy."
                                    else -> buildString {
                                        append("EXIT IP\n")
                                        append(report.proxyIp.ifBlank { "unverified" })
                                        append("\n\nLOCATION\n")
                                        append(report.cloudflareLocation.ifBlank { "unknown" })
                                        append("\n\nDNS OBSERVATION\n")
                                        append(report.dnsServers.ifBlank { "inconclusive" })
                                        append("\n\nSENTINEL\n")
                                        append(if (repo.sentinel.healthy) "HEALTHY" else repo.sentinel.coverage)
                                        append("\n\n")
                                        append(report.note)
                                    }
                                }
                            }
                            "Capabilities" -> repo.capabilities()
                            "System Doctor" -> repo.doctor()
                            "Core lock" -> repo.coreLock()
                            "History" -> repo.history.takeLast(80).asReversed().joinToString("\n") {
                                "${DateFormat.getDateTimeInstance().format(Date(it.at))} • ${it.name} • ${it.reason}"
                            }
                            else -> "MarbleNG"
                        },
                        color = Aether.InkMuted
                    )
                }
            }
        )
    }
}

@Composable
private fun DeepSpaceBackdrop(modifier: Modifier = Modifier) {
    val cyan = Aether.Cyan
    val purple = Aether.Amethyst
    val faint = Aether.InkFaint

    val stars = remember {
        listOf(
            .04f to .10f, .16f to .07f, .28f to .15f, .42f to .05f, .57f to .12f, .72f to .08f, .91f to .16f,
            .08f to .27f, .22f to .34f, .36f to .25f, .49f to .31f, .63f to .22f, .78f to .35f, .95f to .29f,
            .03f to .48f, .18f to .55f, .32f to .44f, .46f to .59f, .61f to .47f, .74f to .53f, .89f to .45f,
            .11f to .72f, .25f to .66f, .39f to .78f, .52f to .69f, .68f to .81f, .83f to .71f, .97f to .77f,
            .06f to .92f, .31f to .88f, .56f to .95f, .79f to .90f, .93f to .98f
        )
    }

    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(purple.copy(alpha = .10f), Color.Transparent),
                center = Offset(size.width * .16f, size.height * .12f),
                radius = size.minDimension * .55f
            ),
            radius = size.minDimension * .55f,
            center = Offset(size.width * .16f, size.height * .12f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyan.copy(alpha = .065f), Color.Transparent),
                center = Offset(size.width * .88f, size.height * .48f),
                radius = size.minDimension * .60f
            ),
            radius = size.minDimension * .60f,
            center = Offset(size.width * .88f, size.height * .48f)
        )

        stars.forEachIndexed { index, (fx, fy) ->
            val radius = if (index % 7 == 0) 1.8f else 1.0f
            val color = when {
                index % 11 == 0 -> cyan.copy(alpha = .35f)
                index % 13 == 0 -> purple.copy(alpha = .32f)
                else -> faint.copy(alpha = .18f)
            }
            drawCircle(color, radius, Offset(size.width * fx, size.height * fy))
        }
    }
}

@Composable
private fun FloatingSpatialDock(
    selected: SpatialTab,
    onSelect: (SpatialTab) -> Unit,
    isLight: Boolean,
    onTheme: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Aether.VoidElevated.copy(alpha = .90f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Aether.Amethyst.copy(alpha = .30f),
                            Aether.GlassBorderSoft,
                            Aether.Cyan.copy(alpha = .30f)
                        )
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialTab.entries.forEach { item ->
                val active = item == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(
                            if (active) {
                                Brush.verticalGradient(
                                    listOf(
                                        Aether.Cyan.copy(alpha = .13f),
                                        Aether.Amethyst.copy(alpha = .12f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .clickable { onSelect(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        item.glyph,
                        color = if (active) Aether.CyanBright else Aether.InkFaint,
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Aether.Glass.copy(alpha = .72f))
                    .border(1.dp, Aether.GlassBorderSoft, CircleShape)
                    .clickable(onClick = onTheme),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isLight) "☀" else "☾", color = Aether.Cyan)
            }
        }
    }
}

@Composable
private fun SpatialHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    status: String? = null,
    statusColor: Color = Aether.Cyan
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                color = Aether.Cyan,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                subtitle,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!status.isNullOrBlank()) {
            HoloBadge(status, statusColor)
        }
    }
}

@Composable
private fun HoloGlass(
    modifier: Modifier = Modifier,
    glow: Color = Aether.Cyan,
    glowStrength: Float = .18f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(25.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Aether.GlassStrong.copy(alpha = .74f),
                        Aether.Glass.copy(alpha = .48f),
                        Aether.VoidElevated.copy(alpha = .74f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        glow.copy(alpha = glowStrength),
                        Aether.GlassBorderSoft,
                        Aether.Amethyst.copy(alpha = glowStrength * .75f)
                    )
                ),
                RoundedCornerShape(25.dp)
            )
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun HoloBadge(text: String, color: Color, compact: Boolean = false) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = .08f))
            .border(1.dp, color.copy(alpha = .30f), CircleShape)
            .padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 3.dp else 5.dp
            )
    )
}

@Composable
private fun SectionLabel(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 2.dp)) {
        Text(
            title.uppercase(),
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                color = Aether.InkFaint.copy(alpha = .82f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// =================================================================================================
// DECK
// =================================================================================================

@Composable
private fun CyberDeck(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onLibrary: () -> Unit,
    onPrivacy: () -> Unit,
    onRouting: () -> Unit
) {
    val downHistory = remember { mutableStateListOf<Float>() }
    val upHistory = remember { mutableStateListOf<Float>() }
    val pingHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        while (true) {
            downHistory += repo.liveDownBps.toFloat()
            upHistory += repo.liveUpBps.toFloat()
            pingHistory += repo.livePingMs.toFloat()
            while (downHistory.size > 56) downHistory.removeAt(0)
            while (upHistory.size > 56) upHistory.removeAt(0)
            while (pingHistory.size > 56) pingHistory.removeAt(0)
            delay(900)
        }
    }

    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING"
    val statusColor = when (repo.state) {
        "CONNECTED" -> Aether.Emerald
        "CONNECTING" -> Aether.Cyan
        "BLOCKED" -> Aether.Danger
        else -> Aether.InkFaint
    }
    val activeName = repo.stateDetail.ifBlank { repo.lastProfile()?.name ?: "No active node" }
    val activeBench = repo.benchmarks.firstOrNull { it.name == activeName || it.profileId == repo.lastProfile()?.id }
    val packetLossPct = (100 - (activeBench?.success ?: if (connected) 100 else 0)).coerceIn(0, 100)
    val jitterMs = activeBench?.jitterMs?.roundToInt() ?: 0
    val healthScore = when {
        !connected -> 0
        activeBench != null -> activeBench.stabilityScore.roundToInt().coerceIn(0, 100)
        repo.livePingMs <= 0 -> 70
        else -> (100 - repo.livePingMs / 5).coerceIn(20, 95)
    }
    val resilienceScore = activeBench?.resilienceScore?.roundToInt()?.coerceIn(0, 100)
        ?: if (connected && repo.sentinel.dnsHijack && repo.sentinel.xrayAlive) 82 else 0
    val intel = repo.intelligenceStatus
    val kernelBypassActive = intel.kernelSuDetected
    val ebpfActive = intel.ebpfCapable
    val multiWanAvailable = intel.dualNetworkAvailable
    val cpuBudgetPct = intel.thermalBudgetPercent.coerceIn(0, 100)
    val thermalImpact = (100 - intel.thermalBudgetPercent).coerceIn(0, 100)
    val batteryImpact = if (intel.powerSaveMode) 72 else if (connected) 30 else 10
    val bestRank = repo.benchmarks.firstOrNull { it.success > 0 }
    val smartRankDetail = when {
        repo.busy -> "Working…"
        bestRank != null -> "${bestRank.name.take(12)} • ${bestRank.latencyMs.toInt()} ms"
        else -> "Benchmark routes"
    }
    val privacyDetail = when {
        !connected -> "Connect first"
        repo.sentinel.healthy -> "Sentinel healthy"
        else -> "Audit egress + DNS"
    }
    val routingDetail = repo.settings.routingMode.name.replace('_', ' ').lowercase()
        .replaceFirstChar { it.uppercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SpatialHeader(
                eyebrow = "Aether / Deck",
                title = "MarbleNG",
                subtitle = "Spatial Xray control surface",
                status = when (repo.state) {
                    "CONNECTED" -> "ONLINE"
                    "CONNECTING" -> "NEGOTIATING"
                    "BLOCKED" -> "BLOCKED"
                    else -> "IDLE"
                },
                statusColor = statusColor
            )
        }

        item {
            ConnectionCore(
                activeName = activeName,
                connected = connected,
                connecting = connecting,
                mode = repo.settings.connectionMode,
                localPort = repo.settings.localProxyPort,
                pingMs = repo.livePingMs,
                downBps = repo.liveDownBps,
                upBps = repo.liveUpBps,
                onToggle = {
                    if (connected || connecting) repo.stopVpn() else repo.auto(onConnect)
                }
            )
        }

        item {
            SectionLabel("Command matrix", "Health score • privacy sentinel • adaptive network telemetry")
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = when {
                    healthScore >= 80 -> Aether.Emerald
                    healthScore >= 55 -> Aether.Cyan
                    else -> Aether.Amber
                }
            ) {
                ConnectionHealthMatrix(
                    score = healthScore,
                    latencyMs = repo.livePingMs,
                    jitterMs = jitterMs,
                    packetLossPct = packetLossPct,
                    resilienceScore = resilienceScore,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TacticalIndicatorTile(
                        title = "KernelSU",
                        state = if (kernelBypassActive) "DETECTED" else "OPTIONAL",
                        color = if (kernelBypassActive) Aether.Emerald else Aether.InkFaint,
                        detail = if (kernelBypassActive) "Root detected" else "Not required",
                        modifier = Modifier.weight(1f)
                    )
                    TacticalIndicatorTile(
                        title = "eBPF",
                        state = if (ebpfActive) "CAPABLE" else "UNAVAILABLE",
                        color = if (ebpfActive) Aether.Cyan else Aether.InkFaint,
                        detail = if (ebpfActive) "Kernel support" else "Userspace TUN",
                        modifier = Modifier.weight(1f)
                    )
                    TacticalIndicatorTile(
                        title = "Multi-WAN",
                        state = if (multiWanAvailable) "DUAL READY" else "SINGLE",
                        color = if (multiWanAvailable) Aether.Amethyst else Aether.InkFaint,
                        detail = if (multiWanAvailable) "Dual links available" else "Single upstream",
                        modifier = Modifier.weight(1f)
                    )
                }

                MultiWanVisualizer(
                    bonding = multiWanAvailable,
                    modifier = Modifier.fillMaxWidth().height(82.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThermalGauge(
                        title = "CPU Budget",
                        value = cpuBudgetPct.coerceIn(0, 100),
                        color = Aether.Cyan,
                        modifier = Modifier.weight(1f)
                    )
                    ThermalGauge(
                        title = "Thermals",
                        value = thermalImpact.coerceIn(0, 100),
                        color = if (thermalImpact < 55) Aether.Emerald else Aether.Amber,
                        modifier = Modifier.weight(1f)
                    )
                    ThermalGauge(
                        title = "Battery",
                        value = batteryImpact.coerceIn(0, 100),
                        color = if (batteryImpact < 45) Aether.Emerald else Aether.Amber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionLabel("Live traffic", "Liquid data wave • last 50 seconds")
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = if (repo.liveDownBps > 2L * 1024L * 1024L) Aether.Emerald else Aether.Cyan
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    DataReadout("DOWN", rate(repo.liveDownBps), Aether.Cyan, Modifier.weight(1f))
                    DataReadout("UP", rate(repo.liveUpBps), Aether.AmethystBright, Modifier.weight(1f))
                    DataReadout(
                        "RTT",
                        if (repo.livePingMs > 0) "${repo.livePingMs} ms" else "—",
                        healthColor(repo.livePingMs, if (repo.livePingMs > 0) 100 else 0),
                        Modifier.weight(1f)
                    )
                }

                LiquidDataWave(
                    down = downHistory,
                    up = upHistory,
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )

                DpiResilienceStrip(
                    successfulPackets = resilienceScore,
                    packetLossPct = packetLossPct,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            SectionLabel("Quick field", "Four primary actions • no horizontal clipping")
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HoloActionPill(
                        "◎", "Smart rank", smartRankDetail, Aether.Cyan, Modifier.weight(1f)
                    ) { repo.smartRank() }
                    HoloActionPill(
                        "▦", "Library", "${repo.profiles.size} nodes", Aether.Amethyst, Modifier.weight(1f)
                    ) { onLibrary() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HoloActionPill(
                        "◇", "Privacy", privacyDetail, Aether.Emerald, Modifier.weight(1f)
                    ) { onPrivacy() }
                    HoloActionPill(
                        "⚙", "Routing", routingDetail, Aether.Amber, Modifier.weight(1f)
                    ) { onRouting() }
                }
            }
        }

        item {
            SectionLabel("Spatial route", "Client → TUN/HEV → Xray policy → Entry → Internet")
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = Aether.Amethyst,
                contentPadding = PaddingValues(10.dp)
            ) {
                SpatialRouteMap(
                    mode = repo.settings.connectionMode,
                    entryName = activeName,
                    exitName = repo.profiles.firstOrNull { it.id == repo.settings.chainSecondProfileId }?.name,
                    chainEnabled = repo.settings.chainEnabled,
                    connected = connected,
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HoloBadge(
                        if (repo.settings.connectionMode == ConnectionMode.FULL_TUN) "FULL TUN" else "LOCAL SOCKS",
                        Aether.Cyan,
                        compact = true
                    )
                    HoloBadge(
                        repo.settings.routingMode.name.replace('_', ' '),
                        Aether.Amethyst,
                        compact = true
                    )
                    if (repo.settings.chainEnabled) {
                        HoloBadge("2-HOP", Aether.Amber, compact = true)
                    }
                }

                TopologyLifecycleStrip(
                    fragmentEnabled = repo.settings.fragmentEnabled,
                    muxEnabled = repo.settings.muxEnabled,
                    geo = activeName,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            SectionLabel("Latency trace")
            HoloGlass(Modifier.fillMaxWidth(), glow = Aether.Emerald) {
                MiniPulseTrace(
                    values = pingHistory,
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionCore(
    activeName: String,
    connected: Boolean,
    connecting: Boolean,
    mode: ConnectionMode,
    localPort: Int,
    pingMs: Int,
    downBps: Long,
    upBps: Long,
    onToggle: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "core-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = .86f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core-pulse-value"
    )
    val coreColor = if (connected) Aether.Emerald else Aether.Cyan
    val coreSecondaryColor = Aether.Amethyst

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        glow = coreColor,
        glowStrength = .34f
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(84.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(
                        coreColor.copy(alpha = .055f),
                        radius = size.minDimension * .50f * pulse
                    )
                    drawCircle(
                        coreColor.copy(alpha = .12f),
                        radius = size.minDimension * .40f * pulse
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                coreColor.copy(alpha = .34f),
                                coreSecondaryColor.copy(alpha = .13f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension * .38f
                    )
                    drawCircle(
                        color = coreColor.copy(alpha = .70f),
                        radius = size.minDimension * .32f,
                        style = Stroke(width = 1.6f)
                    )
                }

                Text(
                    if (connected) "✓" else if (connecting) "⋯" else "⌁",
                    color = Aether.Ink,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (connected) "ACTIVE ROUTE" else if (connecting) "NEGOTIATING" else "READY",
                    color = coreColor,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    activeName,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (mode) {
                        ConnectionMode.FULL_TUN -> "Device TUN • HEV → Xray"
                        ConnectionMode.LOCAL_PROXY -> "SOCKS5 • 127.0.0.1:$localPort"
                    },
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (connected) {
                                listOf(Aether.GlassStrong, Aether.Glass)
                            } else {
                                listOf(Aether.Amethyst.copy(alpha = .78f), Aether.Cyan.copy(alpha = .62f))
                            }
                        )
                    )
                    .border(
                        1.dp,
                        if (connected) Aether.GlassBorder else Aether.Cyan.copy(alpha = .44f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    if (connected) "STOP" else if (connecting) "CANCEL" else "CONNECT",
                    color = Aether.Ink,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniMetric("PING", if (pingMs > 0) "$pingMs" else "—", "ms", Modifier.weight(1f))
            MiniMetric("DOWN", compactRate(downBps), "", Modifier.weight(1f))
            MiniMetric("UP", compactRate(upBps), "", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DataReadout(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = color,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun MiniMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Aether.Void.copy(alpha = .60f))
            .border(1.dp, Aether.GlassBorderSoft.copy(alpha = .75f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(label, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = Aether.Ink,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LiquidDataWave(
    down: List<Float>,
    up: List<Float>,
    modifier: Modifier = Modifier
) {
    val cyan = Aether.Cyan
    val purple = Aether.AmethystBright
    val grid = Aether.GlassBorderSoft
    val maxNow = max(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f).coerceAtLeast(1f)

    Canvas(modifier) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(
                color = grid.copy(alpha = .45f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        drawLiquidSeries(
            values = down,
            maxValue = maxNow,
            color = cyan,
            fillAlpha = .21f
        )
        drawLiquidSeries(
            values = up,
            maxValue = maxNow,
            color = purple,
            fillAlpha = .10f
        )
    }
}

private fun DrawScope.drawLiquidSeries(
    values: List<Float>,
    maxValue: Float,
    color: Color,
    fillAlpha: Float
) {
    if (values.size < 2) return

    val points = values.mapIndexed { index, raw ->
        val x = size.width * index / (values.size - 1).coerceAtLeast(1)
        val normalized = (raw / maxValue).coerceIn(0f, 1f)
        val y = size.height * .91f - normalized * size.height * .76f
        Offset(x, y)
    }

    val line = smoothPath(points)
    val area = Path().apply {
        moveTo(points.first().x, size.height)
        lineTo(points.first().x, points.first().y)

        if (points.size == 2) {
            lineTo(points.last().x, points.last().y)
        } else {
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val midX = (prev.x + cur.x) / 2f
                quadraticBezierTo(midX, prev.y, cur.x, cur.y)
            }
        }

        lineTo(points.last().x, size.height)
        close()
    }

    drawPath(
        path = area,
        brush = Brush.verticalGradient(
            listOf(
                color.copy(alpha = fillAlpha),
                color.copy(alpha = fillAlpha * .28f),
                Color.Transparent
            )
        )
    )

    drawPath(
        path = line,
        color = color.copy(alpha = .12f),
        style = Stroke(width = 13f, cap = StrokeCap.Round)
    )
    drawPath(
        path = line,
        color = color.copy(alpha = .30f),
        style = Stroke(width = 6f, cap = StrokeCap.Round)
    )
    drawPath(
        path = line,
        color = color,
        style = Stroke(width = 2.4f, cap = StrokeCap.Round)
    )
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path

    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        val midX = (prev.x + cur.x) / 2f
        path.quadraticBezierTo(midX, prev.y, cur.x, cur.y)
    }
    return path
}

@Composable
private fun MiniPulseTrace(values: List<Float>, modifier: Modifier = Modifier) {
    val cyan = Aether.Cyan
    val grid = Aether.GlassBorderSoft
    val high = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)

    Canvas(modifier) {
        drawLine(
            grid.copy(alpha = .45f),
            Offset(0f, size.height * .72f),
            Offset(size.width, size.height * .72f),
            1f
        )

        if (values.size >= 2) {
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = size.width * index / (values.size - 1),
                    y = size.height * .82f - (value / high).coerceIn(0f, 1f) * size.height * .64f
                )
            }
            val path = smoothPath(points)
            drawPath(path, cyan.copy(alpha = .12f), style = Stroke(width = 10f, cap = StrokeCap.Round))
            drawPath(path, cyan, style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun HoloActionPill(
    glyph: String,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        color.copy(alpha = .095f),
                        Aether.Glass.copy(alpha = .58f)
                    )
                )
            )
            .border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(35.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = .12f))
                .border(1.dp, color.copy(alpha = .24f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = color, style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpatialRouteMap(
    mode: ConnectionMode,
    entryName: String,
    exitName: String?,
    chainEnabled: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val flowTransition = rememberInfiniteTransition(label = "route-flow")
    val phase by flowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "route-phase"
    )
    val pulse by flowTransition.animateFloat(
        initialValue = .78f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "route-pulse"
    )

    val cyan = Aether.Cyan
    val purple = Aether.Amethyst
    val green = Aether.Emerald
    val amber = Aether.Amber
    val dim = Aether.InkFaint
    val routeColor = if (connected) cyan else dim

    BoxWithConstraints(modifier) {
        val client = .10f to .57f
        val tunnel = .34f to .26f
        val entry = .56f to .66f
        val exit = .76f to .27f
        val internet = if (chainEnabled && !exitName.isNullOrBlank()) {
            .91f to .62f
        } else {
            .83f to .36f
        }

        val nodes = if (chainEnabled && !exitName.isNullOrBlank()) {
            listOf(client, tunnel, entry, exit, internet)
        } else {
            listOf(client, tunnel, entry, internet)
        }

        Canvas(Modifier.matchParentSize()) {
            val points = nodes.map { Offset(size.width * it.first, size.height * it.second) }

            points.zipWithNext().forEachIndexed { index, (a, b) ->
                drawLine(
                    color = routeColor.copy(alpha = if (connected) .09f else .06f),
                    start = a,
                    end = b,
                    strokeWidth = 13f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = routeColor.copy(alpha = if (connected) .42f else .16f),
                    start = a,
                    end = b,
                    strokeWidth = 2.3f,
                    cap = StrokeCap.Round
                )

                if (connected) {
                    val particle = ((phase + index * .21f) % 1f)
                    val x = a.x + (b.x - a.x) * particle
                    val y = a.y + (b.y - a.y) * particle
                    drawCircle(
                        routeColor.copy(alpha = .13f),
                        radius = 10f * pulse,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        routeColor,
                        radius = 3.2f,
                        center = Offset(x, y)
                    )
                }
            }
        }

        SpatialNode(
            x = maxWidth * client.first,
            y = maxHeight * client.second,
            title = "CLIENT",
            detail = "Apps",
            color = purple,
            pulse = pulse
        )
        SpatialNode(
            x = maxWidth * tunnel.first,
            y = maxHeight * tunnel.second,
            title = if (mode == ConnectionMode.FULL_TUN) "TUN" else "SOCKS",
            detail = if (mode == ConnectionMode.FULL_TUN) "HEV" else "LOCAL",
            color = cyan,
            pulse = pulse
        )
        SpatialNode(
            x = maxWidth * entry.first,
            y = maxHeight * entry.second,
            title = "ENTRY",
            detail = entryName.take(13),
            color = green,
            pulse = pulse
        )

        if (chainEnabled && !exitName.isNullOrBlank()) {
            SpatialNode(
                x = maxWidth * exit.first,
                y = maxHeight * exit.second,
                title = "EXIT",
                detail = exitName.take(13),
                color = amber,
                pulse = pulse
            )
        }

        SpatialNode(
            x = maxWidth * internet.first,
            y = maxHeight * internet.second,
            title = "NET",
            detail = "Internet",
            color = if (connected) green else dim,
            pulse = pulse
        )
    }
}

@Composable
private fun SpatialNode(
    x: Dp,
    y: Dp,
    title: String,
    detail: String,
    color: Color,
    pulse: Float
) {
    Column(
        modifier = Modifier
            .offset(x = x - 35.dp, y = y - 30.dp)
            .width(70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(43.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    color = color.copy(alpha = .07f),
                    radius = size.minDimension * .49f * pulse
                )
                drawCircle(
                    color = color.copy(alpha = .15f),
                    radius = size.minDimension * .38f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            color.copy(alpha = .34f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension * .45f
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension * .27f,
                    style = Stroke(width = 1.6f)
                )
                drawCircle(
                    color = color.copy(alpha = .70f),
                    radius = 2.3f
                )
            }
        }
        Text(
            title,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        Text(
            detail,
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =================================================================================================
// LIBRARY
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CyberLibrary(
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
    var sourceFilter by remember { mutableStateOf("all") }
    var manageSubscription by remember { mutableStateOf<Subscription?>(null) }
    var editSubscriptionName by remember { mutableStateOf("") }
    var editSubscriptionUrl by remember { mutableStateOf("") }
    var deleteSubscription by remember { mutableStateOf<Subscription?>(null) }

    val sourceIds = repo.subscriptions.map { it.id }
    LaunchedEffect(sourceIds, sourceFilter) {
        if (sourceFilter != "all" && sourceFilter != "manual" && sourceFilter !in sourceIds) {
            sourceFilter = "all"
        }
    }

    val benchmarkById = repo.benchmarks.associateBy { it.profileId }
    val filtered = repo.profiles.filter {
        val sourceMatches = when (sourceFilter) {
            "all" -> true
            "manual" -> it.subscriptionId == "manual"
            else -> it.subscriptionId == sourceFilter
        }
        sourceMatches && (
            search.isBlank() ||
                it.name.contains(search, true) ||
                it.scheme.contains(search, true) ||
                it.host.contains(search, true) ||
                it.transport.contains(search, true) ||
                it.security.contains(search, true)
        )
    }
    val naturalOrder = when (repo.settings.nodeSortMode) {
        NodeSortMode.PING -> filtered.sortedWith(
            compareBy<ProxyProfile> { profile ->
                benchmarkById[profile.id]
                    ?.takeIf { it.success > 0 }
                    ?.latencyMs
                    ?: Double.MAX_VALUE
            }.thenBy { it.name.lowercase() }
        )
        NodeSortMode.SCORE -> filtered.sortedWith(
            compareByDescending<ProxyProfile> { profile ->
                benchmarkById[profile.id]
                    ?.takeIf { it.success > 0 }
                    ?.score
                    ?: -1.0
            }.thenBy { it.name.lowercase() }
        )
        NodeSortMode.NAME -> filtered.sortedBy { it.name.lowercase() }
        NodeSortMode.PROTOCOL -> filtered.sortedWith(
            compareBy<ProxyProfile> { it.scheme.lowercase() }
                .thenBy { it.name.lowercase() }
        )
        NodeSortMode.SOURCE -> filtered.sortedWith(
            compareBy<ProxyProfile> { it.subscriptionName.lowercase() }
                .thenBy { it.name.lowercase() }
        )
    }
    val visible = if (repo.settings.nodeSortReverse) naturalOrder.asReversed() else naturalOrder

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Aether.VoidElevated,
            title = { Text("Edit node", color = Aether.Ink) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Display name") },
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
                ) {
                    Text("Save", color = Aether.Cyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = Aether.InkMuted)
                }
            }
        )
    }

    manageSubscription?.let { target ->
        AlertDialog(
            onDismissRequest = { manageSubscription = null },
            containerColor = Aether.VoidElevated,
            title = {
                Column {
                    Text("Manage subscription", color = Aether.Ink)
                    Text(
                        "${repo.subscriptionNodeCount(target.id)} nodes • ${relativeTime(target.updatedAt)}",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editSubscriptionName,
                        onValueChange = { editSubscriptionName = it },
                        label = { Text("Source name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSubscriptionUrl,
                        onValueChange = { editSubscriptionUrl = it },
                        label = { Text("Subscription URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CyberButton(
                            label = "VIEW NODES",
                            color = Aether.Cyan,
                            modifier = Modifier.weight(1f)
                        ) {
                            sourceFilter = target.id
                            manageSubscription = null
                        }
                        CyberButton(
                            label = "REFRESH",
                            color = Aether.Amethyst,
                            modifier = Modifier.weight(1f),
                            enabled = !repo.busy
                        ) {
                            manageSubscription = null
                            repo.refresh(target.id)
                        }
                    }
                    Text(
                        "Editing the URL keeps this source identity. Refresh replaces only this source's nodes.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repo.updateSubscription(target.id, editSubscriptionName, editSubscriptionUrl)) {
                            manageSubscription = null
                        }
                    },
                    enabled = !repo.busy && editSubscriptionUrl.isNotBlank()
                ) { Text("SAVE", color = Aether.Cyan) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            deleteSubscription = target
                            manageSubscription = null
                        },
                        enabled = !repo.busy
                    ) { Text("DELETE", color = Aether.Danger) }
                    TextButton(onClick = { manageSubscription = null }) {
                        Text("CLOSE", color = Aether.InkMuted)
                    }
                }
            }
        )
    }

    deleteSubscription?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteSubscription = null },
            containerColor = Aether.VoidElevated,
            title = { Text("Delete ${target.name}?", color = Aether.Danger) },
            text = {
                Text(
                    "This removes the subscription and ${repo.subscriptionNodeCount(target.id)} nodes that belong to it. Other sources are untouched.",
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (sourceFilter == target.id) sourceFilter = "all"
                        repo.removeSubscription(target.id)
                        deleteSubscription = null
                    },
                    enabled = !repo.busy
                ) { Text("DELETE SOURCE", color = Aether.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSubscription = null }) {
                    Text("CANCEL", color = Aether.InkMuted)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SpatialHeader(
                eyebrow = "Node Space",
                title = "Library",
                subtitle = "Separate source views • full subscription management • node health",
                status = "${repo.subscriptions.size} SUBS • ${repo.profiles.size} NODES",
                statusColor = Aether.Amethyst
            )
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search node / protocol / transport") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                )

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Aether.Amethyst.copy(alpha = .25f),
                                    Aether.Cyan.copy(alpha = .17f)
                                )
                            )
                        )
                        .border(1.dp, Aether.Cyan.copy(alpha = .25f), RoundedCornerShape(20.dp))
                        .clickable { addOpen = !addOpen }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (addOpen) "CLOSE" else "+ SOURCE", color = Aether.Ink, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (addOpen) {
            item {
                HoloGlass(
                    modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
                    glow = Aether.Amethyst
                ) {
                    Text("ADD SOURCE", color = Aether.AmethystBright, style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Subscription URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    )
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyberButton(
                            label = "ADD SUB",
                            color = Aether.Cyan,
                            modifier = Modifier.weight(1f),
                            enabled = url.startsWith("http")
                        ) {
                            repo.addSubscription(sourceName, url)
                            url = ""
                            sourceName = ""
                        }
                        CyberButton(
                            label = "IMPORT FILE",
                            color = Aether.Amethyst,
                            modifier = Modifier.weight(1f)
                        ) {
                            onImportFile()
                        }
                    }
                }
            }
        }

        if (repo.subscriptions.isNotEmpty()) {
            item {
                SectionLabel(
                    "Subscription manager",
                    "Every source is separate • view nodes • edit URL/name • refresh • delete"
                )
            }

            items(repo.subscriptions, key = { "subscription-${it.id}" }) { sub ->
                SubscriptionManagerCard(
                    sub = sub,
                    repo = repo,
                    selected = sourceFilter == sub.id,
                    onView = { sourceFilter = sub.id },
                    onManage = {
                        editSubscriptionName = sub.name
                        editSubscriptionUrl = sub.url
                        manageSubscription = sub
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CyberButton(
                    label = "REFRESH",
                    color = Aether.Amethyst,
                    modifier = Modifier.weight(1f),
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy
                ) { repo.refreshAll() }

                CyberButton(
                    label = "TEST ALL",
                    color = Aether.Cyan,
                    modifier = Modifier.weight(1f),
                    enabled = repo.profiles.isNotEmpty() && !repo.busy
                ) { repo.testAll() }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("SOURCE VIEW", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Nodes below belong only to the selected source",
                            color = Aether.InkFaint.copy(alpha = .82f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HoloBadge(
                        when (sourceFilter) {
                            "all" -> "ALL ${repo.profiles.size}"
                            "manual" -> "MANUAL ${repo.profiles.count { it.subscriptionId == "manual" }}"
                            else -> repo.subscriptions.firstOrNull { it.id == sourceFilter }?.name?.take(16) ?: "ALL"
                        },
                        Aether.Emerald,
                        compact = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    CyberChoiceChip("ALL", sourceFilter == "all", Aether.Cyan) { sourceFilter = "all" }
                    CyberChoiceChip(
                        "MANUAL (${repo.profiles.count { it.subscriptionId == "manual" }})",
                        sourceFilter == "manual",
                        Aether.Amber
                    ) { sourceFilter = "manual" }
                    repo.subscriptions.forEach { sub ->
                        CyberChoiceChip(
                            "${sub.name} (${repo.subscriptionNodeCount(sub.id)})",
                            sourceFilter == sub.id,
                            Aether.Amethyst
                        ) { sourceFilter = sub.id }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("SORT NODES", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                        Text(
                            "Ping is the default • untested routes stay last",
                            color = Aether.InkFaint.copy(alpha = .82f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HoloBadge(
                        if (repo.settings.nodeSortReverse) "REVERSED" else "NATURAL",
                        if (repo.settings.nodeSortReverse) Aether.Amber else Aether.Cyan,
                        compact = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    NodeSortMode.entries.forEach { mode ->
                        CyberChoiceChip(
                            text = mode.name,
                            selected = repo.settings.nodeSortMode == mode,
                            color = if (mode == NodeSortMode.PING) Aether.Cyan else Aether.Amethyst
                        ) {
                            repo.updateSettings(repo.settings.copy(nodeSortMode = mode, nodeSortReverse = false))
                        }
                    }
                    CyberChoiceChip(
                        text = if (repo.settings.nodeSortReverse) "ORDER ↥" else "ORDER ↧",
                        selected = repo.settings.nodeSortReverse,
                        color = Aether.Amber
                    ) {
                        repo.updateSettings(repo.settings.copy(nodeSortReverse = !repo.settings.nodeSortReverse))
                    }
                }
            }
        }

        item {
            SectionLabel(
                "Nodes",
                "Swipe → Test / Edit • overflow → delete • center action → connect"
            )
        }

        if (visible.isEmpty()) {
            item {
                HoloGlass(
                    modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
                    glow = Aether.InkFaint
                ) {
                    Text("NO SIGNALS", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add a source or clear the current search.",
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
                            repo.fullTest(profile)
                            false
                        }
                        SwipeToDismissBoxValue.EndToStart -> {
                            renameTarget = profile
                            renameText = profile.name
                            false
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
                    val editSide = swipeState.targetValue == SwipeToDismissBoxValue.EndToStart
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                if (editSide) {
                                    Aether.Amethyst.copy(alpha = .18f)
                                } else {
                                    Aether.Cyan.copy(alpha = .17f)
                                }
                            )
                            .border(
                                1.dp,
                                if (editSide) Aether.Amethyst.copy(alpha = .24f) else Aether.Cyan.copy(alpha = .24f),
                                RoundedCornerShape(22.dp)
                            )
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (editSide) Arrangement.End else Arrangement.Start
                    ) {
                        Text(
                            if (editSide) "EDIT" else "TEST",
                            color = if (editSide) Aether.AmethystBright else Aether.CyanBright,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            ) {
                SpatialServerCard(profile, repo, onConnect, onEdit = {
                    renameTarget = profile
                    renameText = profile.name
                })
            }
        }
    }
}

@Composable
private fun SubscriptionManagerCard(
    sub: Subscription,
    repo: AppRepository,
    selected: Boolean,
    onView: () -> Unit,
    onManage: () -> Unit
) {
    val count = repo.subscriptionNodeCount(sub.id)
    val used = (sub.uploadBytes + sub.downloadBytes).coerceAtLeast(0L)
    val fraction = if (sub.totalBytes > 0L) (used.toFloat() / sub.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val expired = sub.expireAt > 0L && sub.expireAt < System.currentTimeMillis()
    val color = when {
        expired -> Aether.Danger
        selected -> Aether.Cyan
        else -> Aether.Amethyst
    }

    HoloGlass(
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
        glow = color,
        glowStrength = if (selected) .32f else .18f,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(color.copy(alpha = .10f))
                    .border(1.dp, color.copy(alpha = .30f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("S", color = color, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(sub.name, color = Aether.Ink, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count nodes • ${relativeTime(sub.updatedAt)}", color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall)
            }
            if (selected) HoloBadge("VIEWING", Aether.Cyan, compact = true)
        }

        Text(
            sub.url,
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (sub.totalBytes > 0L) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Aether.Void)) {
                Box(
                    Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Aether.Amethyst, Aether.Cyan)))
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("USED ${formatBytes(used)}", color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall)
                Text("LEFT ${formatBytes((sub.totalBytes-used).coerceAtLeast(0L))}", color = Aether.Cyan,
                    style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text("Provider quota metadata unavailable", color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall)
        }

        if (sub.expireAt > 0L) {
            HoloBadge("EXP ${relativeFuture(sub.expireAt)}",
                if (expired) Aether.Danger else Aether.Amber, compact = true)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CyberButton(if (selected) "SHOWING" else "VIEW NODES", Aether.Cyan, Modifier.weight(1f)) { onView() }
            CyberButton("REFRESH", Aether.Amethyst, Modifier.weight(1f), enabled = !repo.busy) { repo.refresh(sub.id) }
            CyberButton("MANAGE", Aether.Amber, Modifier.weight(1f), enabled = !repo.busy) { onManage() }
        }
    }
}

@Composable
private fun SpatialServerCard(
    profile: ProxyProfile,
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onEdit: () -> Unit
) {
    val result = repo.benchmarks.firstOrNull { it.profileId == profile.id }
    val active = repo.state == "CONNECTED" && repo.stateDetail == profile.name
    val latency = result?.takeIf { it.success > 0 }?.latencyMs?.toInt() ?: 0
    val success = result?.success ?: 0
    val health = healthColor(latency, success)
    var menuOpen by remember { mutableStateOf(false) }
    val fallbackCount = repo.profiles.count { candidate ->
        candidate.id != profile.id &&
            candidate.subscriptionId == profile.subscriptionId &&
            profile.subscriptionId.isNotBlank() &&
            profile.subscriptionId != "manual"
    }

    HoloGlass(
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
        glow = if (active) Aether.Emerald else health,
        glowStrength = if (active) .34f else .18f,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthOrb(
                label = countryGlyph(profile.host),
                color = if (active) Aether.Emerald else health,
                active = active || success > 0,
                modifier = Modifier.size(49.dp)
            )

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (latency > 0) "$latency" else "—",
                    color = health,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text("MS", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.width(9.dp))

            Box(
                modifier = Modifier
                    .size(43.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                (if (active) Aether.Emerald else Aether.Cyan).copy(alpha = .26f),
                                Aether.Amethyst.copy(alpha = .08f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        (if (active) Aether.Emerald else Aether.Cyan).copy(alpha = .40f),
                        CircleShape
                    )
                    .clickable {
                        if (active) repo.stopVpn() else onConnect(profile)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (active) "■" else "▶", color = Aether.Ink, style = MaterialTheme.typography.labelLarge)
            }

            Box {
                TextButton(
                    onClick = { menuOpen = true },
                    contentPadding = PaddingValues(horizontal = 7.dp)
                ) {
                    Text("⋮", color = Aether.InkMuted, style = MaterialTheme.typography.titleMedium)
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Aether.VoidElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("Real tunnel test") },
                        onClick = {
                            menuOpen = false
                            repo.fullTest(profile)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit name") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Aether.Danger) },
                        onClick = {
                            menuOpen = false
                            repo.removeProfile(profile.id)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MicroBadge(profile.scheme.uppercase(), Aether.Amethyst)
            if (profile.transport.isNotBlank()) {
                MicroBadge(profile.transport.uppercase(), Aether.Cyan)
            }
            if (profile.security.isNotBlank() && !profile.security.equals("none", true)) {
                MicroBadge(profile.security.uppercase(), Aether.Emerald)
            }
            if (profile.host.contains(":")) {
                MicroBadge("IPV6", Aether.Cyan)
            }
            if (active) {
                MicroBadge("ACTIVE", Aether.Emerald)
            }
            if (repo.settings.smartFallbackEnabled && fallbackCount > 0) {
                MicroBadge("FALLBACK $fallbackCount", Aether.AmethystBright)
            }
        }

        if (result != null && result.success > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MicroStat("SUCCESS", "${result.success}%", Modifier.weight(1f))
                MicroStat("JITTER", "${result.jitterMs.toInt()} ms", Modifier.weight(1f))
                MicroStat("SCORE", "%.0f".format(result.score), Modifier.weight(1f))
            }
        }

    }
}

@Composable
private fun HealthOrb(
    label: String,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "health-orb")
    val pulse by transition.animateFloat(
        initialValue = .82f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 1400 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "health-pulse"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color.copy(alpha = if (active) .06f else .025f),
                radius = size.minDimension * .50f * pulse
            )
            drawCircle(
                color.copy(alpha = if (active) .19f else .08f),
                radius = size.minDimension * .40f
            )
            drawCircle(
                color = color.copy(alpha = .72f),
                radius = size.minDimension * .36f,
                style = Stroke(width = 1.7f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        color.copy(alpha = .18f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension * .35f
            )
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = Aether.Ink)
    }
}

@Composable
private fun MicroBadge(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = .07f))
            .border(1.dp, color.copy(alpha = .23f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun MicroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Aether.Void.copy(alpha = .46f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            label,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =================================================================================================
// LAB / SPIDER CHART
// =================================================================================================

@Composable
private fun BenchmarkStudio(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit
) {
    val modes = listOf(BenchMode.RELIABLE, BenchMode.BALANCED, BenchMode.FAST, BenchMode.TURBO)
    val radarNodes = remember(repo.benchmarks) {
        buildRadarNodes(repo.benchmarks)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            SpatialHeader(
                eyebrow = "Neural Benchmark",
                title = "Lab",
                subtitle = "Benchmark studio • spider analysis • packet tuning",
                status = "${repo.benchmarks.size} SAMPLES",
                statusColor = Aether.Cyan
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                modes.forEach { mode ->
                    CyberChoiceChip(
                        text = mode.name,
                        selected = repo.settings.benchMode == mode,
                        color = Aether.Cyan
                    ) {
                        repo.updateSettings(repo.settings.copy(benchMode = mode))
                    }
                }
            }
        }

        item {
            CyberButton(
                label = "RUN MULTI-AXIS BENCHMARK",
                color = Aether.Cyan,
                modifier = Modifier.fillMaxWidth(),
                enabled = repo.profiles.isNotEmpty() && !repo.busy
            ) {
                repo.smart(onConnect)
            }
        }

        item {
            SectionLabel("Spider field", "Speed • Reliability • Ping • Jitter")
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = Aether.Cyan,
                contentPadding = PaddingValues(10.dp)
            ) {
                if (radarNodes.isEmpty()) {
                    EmptyVisual(
                        glyph = "△",
                        title = "NO BENCHMARK FIELD",
                        body = "Run the lab to project node quality into the spider space."
                    )
                } else {
                    SpiderComparisonChart(
                        nodes = radarNodes.take(3),
                        modifier = Modifier.fillMaxWidth().height(310.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        radarNodes.take(3).forEach { node ->
                            HoloBadge(
                                node.name.take(18),
                                radarColor(node.colorIndex),
                                compact = true
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("Packet modulator", "Visual simulator for Fragment and Mux")
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = Aether.Amethyst,
                contentPadding = PaddingValues(12.dp)
            ) {
                FragmentMuxVisualizer(
                    fragmentEnabled = repo.settings.fragmentEnabled,
                    muxEnabled = repo.settings.muxEnabled,
                    modifier = Modifier.fillMaxWidth().height(132.dp)
                )
            }
        }

        if (repo.benchmarks.isNotEmpty()) {
            item {
                SectionLabel("Ranked telemetry", "Compact details beneath the visual comparison")
            }

            items(repo.benchmarks, key = { "bench-${it.profileId}" }) { result ->
                CompactBenchmarkRow(result, repo, onConnect)
            }
        }
    }
}

private fun buildRadarNodes(results: List<BenchmarkResult>): List<RadarNode> {
    if (results.isEmpty()) return emptyList()

    val reachable = results.filter { it.success > 0 }
    if (reachable.isEmpty()) return emptyList()

    val maxSpeed = reachable.maxOfOrNull { it.bytesPerSecond }?.coerceAtLeast(1.0) ?: 1.0
    val maxPing = reachable.maxOfOrNull { it.latencyMs }?.coerceAtLeast(1.0) ?: 1.0
    val maxJitter = reachable.maxOfOrNull { it.jitterMs }?.coerceAtLeast(1.0) ?: 1.0

    return reachable.take(8).mapIndexed { index, result ->
        RadarNode(
            id = result.profileId,
            name = result.name,
            speed = (result.bytesPerSecond / maxSpeed).toFloat().coerceIn(.05f, 1f),
            reliability = (result.success / 100f).coerceIn(.05f, 1f),
            ping = (1f - (result.latencyMs / (maxPing * 1.08)).toFloat()).coerceIn(.08f, 1f),
            jitter = (1f - (result.jitterMs / (maxJitter * 1.08)).toFloat()).coerceIn(.08f, 1f),
            colorIndex = index
        )
    }
}

@Composable
private fun SpiderComparisonChart(
    nodes: List<RadarNode>,
    modifier: Modifier = Modifier
) {
    val grid = Aether.GlassBorder
    val gridSoft = Aether.GlassBorderSoft
    val labels = Aether.InkMuted

    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f + 8f)
            val radius = min(size.width, size.height) * .34f

            // Faux 3D back-plane.
            for (layer in 1..4) {
                val scale = layer / 4f
                val r = radius * scale
                val backOffset = Offset(0f, -8f * (1f - scale))
                val back = diamondPath(center + backOffset, r)
                drawPath(
                    back,
                    color = gridSoft.copy(alpha = .24f),
                    style = Stroke(width = 1f)
                )
            }

            // Front-plane and extrusion connectors.
            for (layer in 1..4) {
                val scale = layer / 4f
                val r = radius * scale
                val front = diamondPath(center, r)
                drawPath(
                    front,
                    color = if (layer == 4) grid.copy(alpha = .52f) else gridSoft.copy(alpha = .34f),
                    style = Stroke(width = if (layer == 4) 1.5f else 1f)
                )
            }

            val axes = listOf(
                Offset(center.x, center.y - radius),
                Offset(center.x + radius, center.y),
                Offset(center.x, center.y + radius),
                Offset(center.x - radius, center.y)
            )

            axes.forEach { endpoint ->
                drawLine(
                    color = gridSoft.copy(alpha = .38f),
                    start = center,
                    end = endpoint,
                    strokeWidth = 1f
                )
            }

            nodes.forEach { node ->
                val color = radarColorRaw(node.colorIndex)
                val values = listOf(node.speed, node.reliability, node.ping, node.jitter)
                val polygon = Path().apply {
                    values.forEachIndexed { index, value ->
                        val end = axes[index]
                        val p = Offset(
                            center.x + (end.x - center.x) * value,
                            center.y + (end.y - center.y) * value
                        )
                        if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }

                drawPath(
                    polygon,
                    color = color.copy(alpha = .08f),
                    style = Stroke(width = 13f)
                )
                drawPath(
                    polygon,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = .20f),
                            color.copy(alpha = .06f)
                        ),
                        center = center,
                        radius = radius
                    )
                )
                drawPath(
                    polygon,
                    color = color.copy(alpha = .90f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )

                values.forEachIndexed { index, value ->
                    val end = axes[index]
                    val p = Offset(
                        center.x + (end.x - center.x) * value,
                        center.y + (end.y - center.y) * value
                    )
                    drawCircle(color.copy(alpha = .16f), 8f, p)
                    drawCircle(color, 3f, p)
                }
            }
        }

        Text(
            "SPEED",
            color = labels,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp)
        )
        Text(
            "RELIABILITY",
            color = labels,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp)
        )
        Text(
            "PING",
            color = labels,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
        Text(
            "JITTER",
            color = labels,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp)
        )
    }
}

private fun diamondPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius, center.y)
    lineTo(center.x, center.y + radius)
    lineTo(center.x - radius, center.y)
    close()
}

@Composable
private fun radarColor(index: Int): Color = when (index % 3) {
    0 -> Aether.Cyan
    1 -> Aether.AmethystBright
    else -> Aether.Emerald
}

private fun radarColorRaw(index: Int): Color = when (index % 3) {
    0 -> Color(0xFF20F6FF)
    1 -> Color(0xFFB15CFF)
    else -> Color(0xFF45FFB1)
}

@Composable
private fun CompactBenchmarkRow(
    result: BenchmarkResult,
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit
) {
    val color = healthColor(result.latencyMs.toInt(), result.success)

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        glow = color,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthOrb(
                label = "${repo.benchmarks.indexOf(result) + 1}",
                color = color,
                active = result.success > 0,
                modifier = Modifier.size(42.dp)
            )

            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.name,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${result.latencyMs.toInt()} ms • ${result.jitterMs.toInt()} jitter • ${rate(result.bytesPerSecond.toLong())}",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }

            HoloBadge("${result.success}%", color, compact = true)
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { repo.profile(result.profileId)?.let(onConnect) },
                enabled = result.success > 0
            ) {
                Text("USE", color = Aether.Cyan, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// =================================================================================================
// RADAR / TELEGRAM DISCOVERY
// =================================================================================================

@Composable
private fun DiscoveryRadar(repo: AppRepository) {
    var channel by remember { mutableStateOf(repo.channels().firstOrNull().orEmpty()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            SpatialHeader(
                eyebrow = "Signal Discovery",
                title = "Radar",
                subtitle = "Telegram source intelligence",
                status = "${repo.radarConfigs.size} PASSED",
                statusColor = Aether.Emerald
            )
        }

        item {
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = Aether.Cyan,
                glowStrength = .30f,
                contentPadding = PaddingValues(10.dp)
            ) {
                CircularScanner(
                    resultCount = repo.radarResults.size,
                    passedCount = repo.radarConfigs.size,
                    active = repo.busy,
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )

                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("Telegram channel / public URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(19.dp)
                )

                CyberButton(
                    label = if (repo.busy) "SCANNING FIELD…" else "INITIATE DISCOVERY",
                    color = Aether.Cyan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = channel.isNotBlank() && !repo.busy
                ) {
                    repo.telegram(channel)
                }
            }
        }

        item {
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = Aether.Amethyst,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text("SMART GATE", color = Aether.AmethystBright, style = MaterialTheme.typography.labelSmall)

                SettingSwitch(
                    title = "TCP pre-gate",
                    subtitle = "Discard dead candidates before the tunnel benchmark",
                    checked = repo.settings.telegramTcpGate
                ) {
                    repo.updateSettings(repo.settings.copy(telegramTcpGate = it))
                }

                SettingSwitch(
                    title = "Auto-import passed",
                    subtitle = "Passed signals become a managed source",
                    checked = repo.settings.telegramAutoSub
                ) {
                    repo.updateSettings(repo.settings.copy(telegramAutoSub = it))
                }

                NumberSetting(
                    title = "Minimum success",
                    value = repo.settings.telegramPassMinSuccess,
                    range = 10..100,
                    suffix = "%"
                ) {
                    repo.updateSettings(repo.settings.copy(telegramPassMinSuccess = it))
                }

                NumberSetting(
                    title = "Maximum configs",
                    value = repo.settings.telegramMaxConfigs,
                    range = 10..300
                ) {
                    repo.updateSettings(repo.settings.copy(telegramMaxConfigs = it))
                }
            }
        }

        item {
            SectionLabel("Smart gate", "TCP pre-gates and tunnel benchmarks")
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                glow = Aether.Emerald,
                contentPadding = PaddingValues(12.dp)
            ) {
                SmartGateAssemblyLine(
                    stages = listOf(
                        GateStage("INPUT", "Channel / URL feed", true),
                        GateStage("TCP GATE", if (repo.settings.telegramTcpGate) "Port reachability" else "Bypassed", true),
                        GateStage("TUN BENCH", "Tunnel RTT + success", repo.radarResults.isNotEmpty()),
                        GateStage("IMPORT", "Promote healthy nodes", repo.radarConfigs.isNotEmpty())
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (repo.radarResults.isNotEmpty()) {
            item {
                SectionLabel("Discovered field", "Signals ranked after the smart gate")
            }

            items(repo.radarResults.take(24), key = { "radar-${it.profileId}" }) { result ->
                val color = healthColor(result.latencyMs.toInt(), result.success)
                HoloGlass(
                    modifier = Modifier.fillMaxWidth(),
                    glow = color,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HealthOrb(
                            label = "•",
                            color = color,
                            active = result.success >= repo.settings.telegramPassMinSuccess,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                result.name,
                                color = Aether.Ink,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${result.latencyMs.toInt()} ms • jitter ${result.jitterMs.toInt()} • score ${"%.0f".format(result.score)}",
                                color = Aether.InkFaint,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        HoloBadge("${result.success}%", color, compact = true)
                    }
                }
            }
        }

        if (repo.radarConfigs.isNotEmpty()) {
            item {
                CyberButton(
                    label = "IMPORT ${repo.radarConfigs.size} PASSED SIGNALS",
                    color = Aether.Emerald,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repo.importRadar()
                }
            }
        }
    }
}

@Composable
private fun CircularScanner(
    resultCount: Int,
    passedCount: Int,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 1450 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanner-angle"
    )
    val pulse by transition.animateFloat(
        initialValue = .84f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanner-pulse"
    )

    val cyan = Aether.Cyan
    val green = Aether.Emerald
    val purple = Aether.Amethyst
    val grid = Aether.GlassBorderSoft

    val blips = remember(resultCount, passedCount) {
        val count = resultCount.coerceIn(0, 18)
        List(count) { index ->
            val seed = (index * 47 + resultCount * 13 + passedCount * 7)
            val radial = .18f + ((seed % 67) / 100f)
            val theta = ((seed * 29) % 360) * PI.toFloat() / 180f
            Triple(radial, theta, index < passedCount)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * .39f

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        cyan.copy(alpha = .035f),
                        purple.copy(alpha = .025f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            for (i in 1..4) {
                drawCircle(
                    color = grid.copy(alpha = .38f),
                    radius = radius * i / 4f,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }

            drawLine(
                grid.copy(alpha = .34f),
                Offset(center.x - radius, center.y),
                Offset(center.x + radius, center.y),
                1f
            )
            drawLine(
                grid.copy(alpha = .34f),
                Offset(center.x, center.y - radius),
                Offset(center.x, center.y + radius),
                1f
            )

            // Sweep trail.
            for (trail in 0..12) {
                val trailAngle = (angle - trail * 4.2f) * PI.toFloat() / 180f
                val end = Offset(
                    center.x + cos(trailAngle) * radius,
                    center.y + sin(trailAngle) * radius
                )
                val alpha = (.26f * (1f - trail / 13f)).coerceAtLeast(0f)
                drawLine(
                    cyan.copy(alpha = alpha),
                    center,
                    end,
                    strokeWidth = if (trail == 0) 2.3f else 1.1f,
                    cap = StrokeCap.Round
                )
            }

            val headAngle = angle * PI.toFloat() / 180f
            val head = Offset(
                center.x + cos(headAngle) * radius,
                center.y + sin(headAngle) * radius
            )
            drawCircle(cyan.copy(alpha = .09f), 14f * pulse, head)
            drawCircle(cyan, 3f, head)

            blips.forEach { (radial, theta, passed) ->
                val p = Offset(
                    center.x + cos(theta) * radius * radial,
                    center.y + sin(theta) * radius * radial
                )
                val color = if (passed) green else purple
                drawCircle(color.copy(alpha = .08f), 9f * pulse, p)
                drawCircle(color.copy(alpha = .88f), 2.7f, p)
            }

            drawCircle(
                color = cyan.copy(alpha = .62f),
                radius = 4f,
                center = center
            )
            drawCircle(
                color = cyan.copy(alpha = .10f),
                radius = 16f * pulse,
                center = center
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (active) "SCANNING" else "RADAR READY",
                color = if (active) Aether.CyanBright else Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                resultCount.toString(),
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            )
            Text(
                "$passedCount passed",
                color = Aether.Emerald,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// =================================================================================================
// SETTINGS / ACCORDIONS
// =================================================================================================

@Composable
private fun SpatialSettings(
    repo: AppRepository,
    onDialog: (String) -> Unit,
    focusSection: String? = null
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusSection) {
        if (focusSection == "Routing") {
            delay(45)
            listState.animateScrollToItem(8)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            SpatialHeader(
                eyebrow = "Control Matrix",
                title = "Settings",
                subtitle = "Advanced systems reveal only when needed",
                status = "AETHER V2",
                statusColor = Aether.Amethyst
            )
        }

        item {
            SpatialAccordion(
                title = "Connection surface",
                subtitle = "Full TUN vs localhost proxy",
                badge = "PATH",
                color = Aether.Cyan,
                initiallyOpen = focusSection == null
            ) {
                ConnectionSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Marble Intelligence",
                subtitle = "Predictive routing • DNS shield • adaptive recovery",
                badge = "AI NET",
                color = Aether.Cyan,
                initiallyOpen = focusSection == null
            ) {
                IntelligenceSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Notifications",
                subtitle = "Smart alerts • recovery • privacy • sync",
                badge = "ALERTS",
                color = Aether.Amber
            ) {
                NotificationSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Split tunneling",
                subtitle = "Per-app capture policy",
                badge = "APPS",
                color = Aether.Emerald
            ) {
                SplitTunnelSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Fragment & Mux",
                subtitle = "DPI resilience and connection reuse",
                badge = "XRAY",
                color = Aether.Amethyst
            ) {
                FragmentMuxSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Chain proxy",
                subtitle = "Visual two-hop route",
                badge = "2-HOP",
                color = Aether.Amber
            ) {
                ChainSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "DNS",
                subtitle = "TUN resolvers and DoH path",
                badge = "DOH",
                color = Aether.Cyan
            ) {
                DnsSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Routing",
                subtitle = "Geo assets, direct rules, block rules",
                badge = "RULES",
                color = Aether.Emerald,
                initiallyOpen = focusSection == "Routing"
            ) {
                RoutingSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Subscriptions",
                subtitle = "Automatic refresh cadence",
                badge = "SYNC",
                color = Aether.Amethyst
            ) {
                SubscriptionSettings(repo)
            }
        }

        item {
            SpatialAccordion(
                title = "Maintenance",
                subtitle = "Diagnostics and recovery",
                badge = "TOOLS",
                color = Aether.InkMuted
            ) {
                MaintenanceSettings(repo, onDialog)
            }
        }

        item {
            Text(
                "Marble Intelligence performs bounded health probes through Xray, persists network-scoped route history, and keeps Full TUN fail-closed during recovery. Multi-WAN and KernelSU/eBPF are displayed only as capabilities until a real datapath backend exists.",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SpatialAccordion(
    title: String,
    subtitle: String,
    badge: String,
    color: Color,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val glow by animateFloatAsState(
        targetValue = if (open) .24f else .10f,
        animationSpec = tween(135, easing = FastOutSlowInEasing),
        label = "accordion-glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(165, easing = FastOutSlowInEasing)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Aether.GlassStrong.copy(alpha = .68f),
                        Aether.Glass.copy(alpha = .40f),
                        Aether.VoidElevated.copy(alpha = .72f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        color.copy(alpha = if (open) glow else .10f),
                        Aether.GlassBorderSoft,
                        color.copy(alpha = if (open) glow * .72f else .07f)
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(39.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = .09f))
                    .border(1.dp, color.copy(alpha = .22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (open) "−" else "+", color = color, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
            }
            HoloBadge(badge, color, compact = true)
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(
                animationSpec = tween(150, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(85)),
            exit = shrinkVertically(
                animationSpec = tween(120, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(70))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                HorizontalDivider(color = Aether.GlassBorderSoft.copy(alpha = .72f))
                content()
            }
        }
    }
}

@Composable
private fun ConnectionSettings(repo: AppRepository) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberSegment(
            label = "FULL TUN",
            detail = "Device",
            selected = repo.settings.connectionMode == ConnectionMode.FULL_TUN,
            color = Aether.Cyan,
            modifier = Modifier.weight(1f)
        ) {
            repo.setConnectionMode(ConnectionMode.FULL_TUN)
        }

        CyberSegment(
            label = "LOCAL",
            detail = ":${repo.settings.localProxyPort}",
            selected = repo.settings.connectionMode == ConnectionMode.LOCAL_PROXY,
            color = Aether.Amethyst,
            modifier = Modifier.weight(1f)
        ) {
            repo.setConnectionMode(ConnectionMode.LOCAL_PROXY)
        }
    }

    NumberSetting(
        title = "Local SOCKS port",
        value = repo.settings.localProxyPort,
        range = 1024..65535
    ) {
        repo.updateSettings(repo.settings.copy(localProxyPort = it))
    }

    SettingSwitch(
        title = "Remember last node",
        subtitle = "Reconnect using the last successful route",
        checked = repo.settings.rememberLast
    ) {
        repo.updateSettings(repo.settings.copy(rememberLast = it))
    }
}

@Composable
private fun IntelligenceSettings(repo: AppRepository) {
    val s = repo.settings
    val status = repo.intelligenceStatus
    val sentinel = repo.sentinel

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HoloBadge(status.networkLabel, Aether.Cyan, compact = true)
        HoloBadge("MTU ${status.effectiveMtu.takeIf { it > 0 } ?: s.mtuMax}", Aether.Emerald, compact = true)
        HoloBadge("CPU ${status.thermalBudgetPercent}%", if (status.thermalBudgetPercent >= 65) Aether.Emerald else Aether.Amber, compact = true)
        HoloBadge("HISTORY ${status.historyRecords}", Aether.Amethyst, compact = true)
    }

    Text(status.lastDecision, color = Aether.InkMuted, style = MaterialTheme.typography.bodySmall)

    SettingSwitch(
        title = "Marble Intelligence Engine",
        subtitle = "Use network-scoped history and adaptive policies",
        checked = s.intelligenceEnabled
    ) { repo.updateSettings(s.copy(intelligenceEnabled = it)) }

    SettingSwitch(
        title = "Maximum config compatibility",
        subtitle = "Preserve Xray outbound dependencies and let Xray run -test verify the final config",
        checked = s.configCompatibilityMode
    ) { repo.updateSettings(repo.settings.copy(configCompatibilityMode = it)) }

    SettingSwitch(
        title = "Verified performance auto-tune",
        subtitle = "A/B test adaptive Fragment/Mux and keep only material latency, speed or reliability gains",
        checked = s.verifiedPerformanceTuning
    ) { repo.updateSettings(repo.settings.copy(verifiedPerformanceTuning = it)) }

    SettingSwitch(
        title = "Persistent route intelligence",
        subtitle = "EWMA health history per network fingerprint",
        checked = s.healthHistoryEnabled
    ) { repo.updateSettings(s.copy(healthHistoryEnabled = it)) }

    SettingSwitch(
        title = "Connection race",
        subtitle = "Race a few predicted-good real Xray paths; first healthy route wins",
        checked = s.raceConnectEnabled
    ) { repo.updateSettings(s.copy(raceConnectEnabled = it)) }

    AnimatedVisibility(s.raceConnectEnabled) {
        NumberSetting("Race width", s.raceWidth, 2..4) {
            repo.updateSettings(repo.settings.copy(raceWidth = it))
        }
    }

    SettingSwitch(
        title = "Smart fallback",
        subtitle = "Keep TUN fail-closed while switching to a healthy backup",
        checked = s.smartFallbackEnabled
    ) { repo.updateSettings(s.copy(smartFallbackEnabled = it)) }

    AnimatedVisibility(s.smartFallbackEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            NumberSetting("Fallback depth", s.fallbackCount, 1..8) {
                repo.updateSettings(repo.settings.copy(fallbackCount = it))
            }
            SettingSwitch(
                title = "Auto-connect after kill switch",
                subtitle = "If off, Full TUN stays blocked until you tap Connect again",
                checked = s.autoReconnectAfterKillSwitch
            ) { repo.updateSettings(repo.settings.copy(autoReconnectAfterKillSwitch = it)) }
        }
    }

    SettingSwitch(
        title = "Network-change recovery",
        subtitle = "Re-probe immediately after Wi-Fi/cellular/link-property changes",
        checked = s.networkChangeRecoveryEnabled
    ) { repo.updateSettings(s.copy(networkChangeRecoveryEnabled = it)) }

    SettingSwitch(
        title = "Adaptive MTU",
        subtitle = "Use physical link + transport-aware MTU instead of a permanent jumbo TUN",
        checked = s.adaptiveMtuEnabled
    ) { repo.updateSettings(s.copy(adaptiveMtuEnabled = it)) }

    AnimatedVisibility(s.adaptiveMtuEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberSetting("MTU floor", s.mtuMin, 1280..1500) {
                repo.updateSettings(repo.settings.copy(mtuMin = it.coerceAtMost(repo.settings.mtuMax)))
            }
            NumberSetting("MTU ceiling", s.mtuMax, 1280..1500) {
                repo.updateSettings(repo.settings.copy(mtuMax = it.coerceAtLeast(repo.settings.mtuMin)))
            }
        }
    }

    SettingSwitch(
        title = "Thermal-aware benchmarking",
        subtitle = "Reduce workers/bytes before Android throttles the device",
        checked = s.thermalAwareEnabled
    ) { repo.updateSettings(s.copy(thermalAwareEnabled = it)) }

    SettingSwitch(
        title = "Adaptive throughput test",
        subtitle = "Start small; expand download only when confidence and thermal budget allow",
        checked = s.adaptiveThroughputEnabled
    ) { repo.updateSettings(s.copy(adaptiveThroughputEnabled = it)) }

    SettingSwitch(
        title = "UDP / QUIC probe",
        subtitle = "STUN through SOCKS5 UDP ASSOCIATE for real UDP health",
        checked = s.udpProbeEnabled
    ) { repo.updateSettings(s.copy(udpProbeEnabled = it)) }

    Text("WORKLOAD", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        WorkloadProfile.entries.forEach { mode ->
            CyberChoiceChip(
                text = mode.name,
                selected = s.workloadProfile == mode,
                color = when (mode) {
                    WorkloadProfile.STREAMING -> Aether.Amethyst
                    WorkloadProfile.STEALTH -> Aether.Amber
                    else -> Aether.Cyan
                }
            ) { repo.updateSettings(repo.settings.copy(workloadProfile = mode)) }
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text("PRIVACY SENTINEL", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HoloBadge(sentinel.coverage, if (sentinel.coverage == "DEVICE-WIDE") Aether.Emerald else Aether.Amber, compact = true)
        HoloBadge(if (sentinel.dnsHijack) "DNS HIJACK" else "DNS OPEN", if (sentinel.dnsHijack) Aether.Emerald else Aether.Amber, compact = true)
        HoloBadge(if (sentinel.killSwitchArmed) "KILL SWITCH" else "NO KILL SWITCH", if (sentinel.killSwitchArmed) Aether.Emerald else Aether.InkFaint, compact = true)
    }
    if (sentinel.splitBypassCount > 0) {
        Text("${sentinel.splitBypassCount} apps intentionally bypass the VPN; device protection is partial.", color = Aether.Amber, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotificationSettings(repo: AppRepository) {
    val context = LocalContext.current
    val s = repo.settings
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) repo.testSmartNotification()
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HoloBadge(
            if (permissionGranted) "PERMISSION READY" else "PERMISSION NEEDED",
            if (permissionGranted) Aether.Emerald else Aether.Amber,
            compact = true
        )
        HoloBadge(
            if (s.smartNotificationsEnabled) "SMART ALERTS ON" else "SMART ALERTS OFF",
            if (s.smartNotificationsEnabled) Aether.Cyan else Aether.InkFaint,
            compact = true
        )
        HoloBadge(
            if (s.notificationLiveStats) "LIVE STATUS" else "STATIC STATUS",
            if (s.notificationLiveStats) Aether.Amethyst else Aether.InkFaint,
            compact = true
        )
    }

    Text(
        "Android requires a foreground-service status while the VPN/proxy is running. The controls below manage optional alerts and how much live telemetry is shown.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )

    if (Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
        CyberButton(
            label = "GRANT NOTIFICATION ACCESS",
            color = Aether.Amber,
            modifier = Modifier.fillMaxWidth()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CyberButton(
            label = "TEST ALERT",
            color = Aether.Cyan,
            modifier = Modifier.weight(1f),
            enabled = permissionGranted && s.smartNotificationsEnabled
        ) { repo.testSmartNotification() }
        CyberButton(
            label = "ANDROID CHANNELS",
            color = Aether.Amethyst,
            modifier = Modifier.weight(1f)
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }

    CyberButton(
        label = "CLEAR OPTIONAL ALERTS",
        color = Aether.InkMuted,
        modifier = Modifier.fillMaxWidth()
    ) { repo.clearSmartNotifications() }

    SettingSwitch(
        title = "Live connection telemetry",
        subtitle = "Refresh the persistent status with ping and up/down rates every few seconds",
        checked = s.notificationLiveStats
    ) { repo.updateSettings(s.copy(notificationLiveStats = it)) }

    SettingSwitch(
        title = "Optional smart alerts",
        subtitle = "Master switch for event notifications; the required foreground status remains available",
        checked = s.smartNotificationsEnabled
    ) { repo.updateSettings(s.copy(smartNotificationsEnabled = it)) }

    AnimatedVisibility(s.smartNotificationsEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SettingSwitch(
                title = "Connection events",
                subtitle = "Connected route changes; disabled by default to avoid noise",
                checked = s.notifyConnectionEvents
            ) { repo.updateSettings(repo.settings.copy(notifyConnectionEvents = it)) }
            SettingSwitch(
                title = "Recovery & failover",
                subtitle = "Smart fallback start and successful route recovery",
                checked = s.notifyRecoveryEvents
            ) { repo.updateSettings(repo.settings.copy(notifyRecoveryEvents = it)) }
            SettingSwitch(
                title = "Privacy warnings",
                subtitle = "Kill-switch holds and fail-closed route blocks",
                checked = s.notifyPrivacyWarnings
            ) { repo.updateSettings(repo.settings.copy(notifyPrivacyWarnings = it)) }
            SettingSwitch(
                title = "Network changes",
                subtitle = "Wi-Fi/cellular underlay changes while connected",
                checked = s.notifyNetworkChanges
            ) { repo.updateSettings(repo.settings.copy(notifyNetworkChanges = it)) }
            SettingSwitch(
                title = "Subscription updates",
                subtitle = "Refresh results and source failures",
                checked = s.notifySubscriptionEvents
            ) { repo.updateSettings(repo.settings.copy(notifySubscriptionEvents = it)) }
            SettingSwitch(
                title = "Core updates",
                subtitle = "Notify when a newer Xray or HEV core is detected",
                checked = s.notifyCoreUpdates
            ) { repo.updateSettings(repo.settings.copy(notifyCoreUpdates = it)) }
            NumberSetting(
                title = "Alert cooldown (seconds)",
                value = s.notificationCooldownSec,
                range = 5..300
            ) {
                repo.updateSettings(repo.settings.copy(notificationCooldownSec = it))
            }
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
            .mapNotNull { result ->
                val pkg = result.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                InstalledApp(
                    label = runCatching { result.loadLabel(pm).toString() }.getOrDefault(pkg),
                    packageName = pkg
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SplitTunnelMode.entries.forEach { mode ->
            CyberChoiceChip(
                text = when (mode) {
                    SplitTunnelMode.ALL_APPS -> "ALL APPS"
                    SplitTunnelMode.ONLY_SELECTED -> "ONLY SELECTED"
                    SplitTunnelMode.BYPASS_SELECTED -> "BYPASS SELECTED"
                },
                selected = repo.settings.splitTunnelMode == mode,
                color = Aether.Emerald
            ) {
                repo.updateSettings(repo.settings.copy(splitTunnelMode = mode))
            }
        }
    }

    if (repo.settings.splitTunnelMode != SplitTunnelMode.ALL_APPS) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search installed apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        )

        val selected = repo.settings.splitTunnelPackages
            .split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("APP MATRIX", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            HoloBadge("${selected.size} SELECTED", Aether.Emerald, compact = true)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Aether.Void.copy(alpha = .50f))
                .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(18.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            apps.filter {
                search.isBlank() ||
                    it.label.contains(search, true) ||
                    it.packageName.contains(search, true)
            }.take(48).chunked(2).forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowApps.forEach { app ->
                        val checked = app.packageName in selected
                        AppGridTile(
                            app = app,
                            checked = checked,
                            modifier = Modifier.weight(1f)
                        ) {
                            val next = selected.toMutableSet()
                            if (!next.add(app.packageName)) next.remove(app.packageName)
                            repo.updateSettings(
                                repo.settings.copy(
                                    splitTunnelPackages = next.sorted().joinToString(",")
                                )
                            )
                        }
                    }
                    if (rowApps.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            "Per-app capture changes take effect on the next Full TUN connection.",
            color = Aether.Amber,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FragmentMuxSettings(repo: AppRepository) {
    SettingSwitch(
        title = "Adaptive Fragment",
        subtitle = "Test Fragment only after TLS/REALITY interference; remember successful preference per network",
        checked = repo.settings.adaptiveFragmentEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveFragmentEnabled = it)) }
    SettingSwitch(
        title = "Adaptive Mux",
        subtitle = "Probe Mux only on stable high-RTT TCP routes; never assume it increases bulk speed",
        checked = repo.settings.adaptiveMuxEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveMuxEnabled = it)) }
    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        title = "TLS ClientHello fragmentation",
        subtitle = "Freedom.fragment on the physical server dial",
        checked = repo.settings.fragmentEnabled
    ) {
        repo.updateSettings(repo.settings.copy(fragmentEnabled = it))
    }

    AnimatedVisibility(repo.settings.fragmentEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TinyField(
                    label = "Packets",
                    value = repo.settings.fragmentPackets,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(fragmentPackets = it))
                }
                TinyField(
                    label = "Length",
                    value = repo.settings.fragmentLength,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(fragmentLength = it))
                }
                TinyField(
                    label = "Interval",
                    value = repo.settings.fragmentInterval,
                    modifier = Modifier.weight(1f)
                ) {
                    repo.updateSettings(repo.settings.copy(fragmentInterval = it))
                }
            }

            Text(
                "Recommended baseline: tlshello • 100-200 • 10-20 ms. More aggressive values can lower stability.",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)

    SettingSwitch(
        title = "Mux / XUDP",
        subtitle = "Connection reuse for many small streams",
        checked = repo.settings.muxEnabled
    ) {
        repo.updateSettings(repo.settings.copy(muxEnabled = it))
    }

    AnimatedVisibility(repo.settings.muxEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    CyberChoiceChip(
                        text = "UDP443 ${value.uppercase()}",
                        selected = repo.settings.muxUdp443 == value,
                        color = Aether.Amethyst
                    ) {
                        repo.updateSettings(repo.settings.copy(muxUdp443 = value))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainSettings(repo: AppRepository) {
    SettingSwitch(
        title = "Two-hop route",
        subtitle = "Current connection node → selected exit node",
        checked = repo.settings.chainEnabled
    ) {
        repo.updateSettings(repo.settings.copy(chainEnabled = it))
    }

    AnimatedVisibility(repo.settings.chainEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SpatialRouteMap(
                mode = repo.settings.connectionMode,
                entryName = repo.lastProfile()?.name ?: "Entry node",
                exitName = repo.profiles.firstOrNull { it.id == repo.settings.chainSecondProfileId }?.name,
                chainEnabled = true,
                connected = true,
                modifier = Modifier.fillMaxWidth().height(210.dp)
            )

            Text("EXIT NODE", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repo.profiles.take(80).forEach { profile ->
                    CyberChoiceChip(
                        text = profile.name,
                        selected = repo.settings.chainSecondProfileId == profile.id,
                        color = Aether.Amber
                    ) {
                        repo.updateSettings(
                            repo.settings.copy(chainSecondProfileId = profile.id)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsSettings(repo: AppRepository) {
    SettingSwitch(
        title = "Intercept traditional DNS",
        subtitle = "Route TCP/UDP :53 to Xray dns-out → built-in encrypted DNS",
        checked = repo.settings.dnsHijackEnabled
    ) { repo.updateSettings(repo.settings.copy(dnsHijackEnabled = it)) }
    SettingSwitch(
        title = "Adaptive DoH ordering",
        subtitle = "Measure configured DoH HTTPS paths through the active proxy and remember the winner",
        checked = repo.settings.adaptiveDnsEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveDnsEnabled = it)) }
    SettingSwitch(
        title = "Adaptive IPv4 / IPv6 DNS",
        subtitle = "Use current physical reachability to choose UseIP / UseIPv4 / UseIPv6",
        checked = repo.settings.adaptiveDualStackEnabled
    ) { repo.updateSettings(repo.settings.copy(adaptiveDualStackEnabled = it)) }

    Text("QUICK RESOLVERS", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        DnsPreset(
            "CLOUDFLARE",
            repo,
            "1.1.1.1",
            "1.0.0.1",
            "https://1.1.1.1/dns-query",
            "https://1.0.0.1/dns-query"
        )
        DnsPreset(
            "GOOGLE",
            repo,
            "8.8.8.8",
            "8.8.4.4",
            "https://8.8.8.8/dns-query",
            "https://8.8.4.4/dns-query"
        )
        DnsPreset(
            "QUAD9",
            repo,
            "9.9.9.9",
            "149.112.112.112",
            "https://9.9.9.9/dns-query",
            "https://149.112.112.112/dns-query"
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
            CyberChoiceChip(
                text = strategy.uppercase(),
                selected = repo.settings.dnsQueryStrategy == strategy,
                color = Aether.Cyan
            ) {
                repo.updateSettings(repo.settings.copy(dnsQueryStrategy = strategy))
            }
        }
    }
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
    CyberChoiceChip(
        text = label,
        selected = repo.settings.dnsPrimaryIp == ip1,
        color = Aether.Cyan
    ) {
        repo.updateSettings(
            repo.settings.copy(
                dnsPrimaryIp = ip1,
                dnsSecondaryIp = ip2,
                dnsPrimaryDoH = doh1,
                dnsSecondaryDoH = doh2
            )
        )
    }
}

@Composable
private fun RoutingAssetCard(
    title: String,
    ready: Boolean,
    bytes: Long,
    remote: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (ready) Aether.Emerald else Aether.Amber
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Aether.Void.copy(alpha = .48f))
            .border(1.dp, color.copy(alpha = .24f), RoundedCornerShape(17.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(7.dp))
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(if (ready) "READY" else "MISSING", color = color, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (ready) "${formatBytes(bytes)} • ${if (remote) "REMOTE" else "BUNDLED"}" else "Prepare a valid Xray data file",
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoutingSettings(repo: AppRepository) {
    val s = repo.settings
    val assets = repo.routingAssetStatus()
    val geoIpTokens = s.routeGeoIpTags
        .split(',', '\n', '\r', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val needsGeoIp = s.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) &&
        geoIpTokens.any { !it.equals("private", true) && !it.equals("geoip:private", true) }
    val needsGeoSite = (s.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) && s.routeGeoSiteTags.isNotBlank()) ||
        (s.routeBlockAds && s.routeAdsTag.isNotBlank()) ||
        listOf(s.routeDirectDomains, s.routeProxyDomains, s.routeBlockDomains).any { it.contains("geosite:", true) }
    val missingGeoAssets = listOfNotNull(
        "geoip.dat".takeIf { needsGeoIp && !assets.geoIpReady },
        "geosite.dat".takeIf { needsGeoSite && !assets.geoSiteReady }
    ).joinToString(" + ")

    Text("ROUTING MODE", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RoutingMode.entries.forEach { mode ->
            CyberChoiceChip(
                text = mode.name.replace('_', ' '),
                selected = s.routingMode == mode,
                color = Aether.Emerald
            ) { repo.updateSettings(s.copy(routingMode = mode)) }
        }
    }

    Text("GEO DATA PLANE", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingAssetCard(
            title = "GeoIP",
            ready = assets.geoIpReady,
            bytes = assets.geoIpBytes,
            remote = assets.geoIpRemote,
            modifier = Modifier.weight(1f)
        )
        RoutingAssetCard(
            title = "GeoSite",
            ready = assets.geoSiteReady,
            bytes = assets.geoSiteBytes,
            remote = assets.geoSiteRemote,
            modifier = Modifier.weight(1f)
        )
    }

    if ((needsGeoIp && !assets.geoIpReady) || (needsGeoSite && !assets.geoSiteReady)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Aether.Amber.copy(alpha = .07f))
                .border(1.dp, Aether.Amber.copy(alpha = .24f), RoundedCornerShape(15.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("!", color = Aether.Amber, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "Selected routing policy requires $missingGeoAssets",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    TinyField("geoip.dat HTTPS URL", s.geoIpUrl, Modifier.fillMaxWidth()) {
        repo.updateSettings(s.copy(geoIpUrl = it))
    }
    TinyField("geosite.dat HTTPS URL", s.geoSiteUrl, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(geoSiteUrl = it))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberButton(
            label = "PREPARE",
            color = Aether.Emerald,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.prepareRoutingAssets(false) }
        CyberButton(
            label = "UPDATE",
            color = Aether.Cyan,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.prepareRoutingAssets(true) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberButton(
            label = "VERIFY POLICY",
            color = Aether.Amethyst,
            modifier = Modifier.weight(1f),
            enabled = repo.profiles.isNotEmpty() && !repo.busy
        ) { repo.verifyRoutingPolicy() }
        CyberButton(
            label = "REMOVE DATA",
            color = Aether.Danger,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy && (assets.geoIpReady || assets.geoSiteReady)
        ) { repo.deleteRoutingAssets() }
    }

    Text(
        "VERIFY POLICY builds the exact hardened Xray routing config and runs xray run -test with XRAY_LOCATION_ASSET pointed at these files.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )

    if (s.routingMode == RoutingMode.GEO_DIRECT || s.routingMode == RoutingMode.CUSTOM) {
        Text("GEO DIRECT RULES", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        TinyField("GeoIP direct tags • e.g. ir, private", s.routeGeoIpTags, Modifier.fillMaxWidth()) {
            repo.updateSettings(repo.settings.copy(routeGeoIpTags = it))
        }
        TinyField("GeoSite direct tags • e.g. category-ir", s.routeGeoSiteTags, Modifier.fillMaxWidth()) {
            repo.updateSettings(repo.settings.copy(routeGeoSiteTags = it))
        }
    }

    Text("DOMAIN STRATEGY", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf("AsIs", "IPIfNonMatch", "IPOnDemand").forEach { strategy ->
            CyberChoiceChip(
                text = strategy,
                selected = s.routeDomainStrategy == strategy,
                color = Aether.Cyan
            ) { repo.updateSettings(repo.settings.copy(routeDomainStrategy = strategy)) }
        }
    }

    SettingSwitch(
        title = "Bypass private networks",
        subtitle = "Literal RFC1918/link-local rules • no geoip.dat dependency",
        checked = s.routeBypassPrivate
    ) { repo.updateSettings(repo.settings.copy(routeBypassPrivate = it)) }

    SettingSwitch(
        title = "Aggressive ad blocking",
        subtitle = "Block a geosite category before direct/proxy rules",
        checked = s.routeBlockAds
    ) { repo.updateSettings(repo.settings.copy(routeBlockAds = it)) }

    if (s.routeBlockAds) {
        TinyField("Ad geosite category", s.routeAdsTag, Modifier.fillMaxWidth()) {
            repo.updateSettings(repo.settings.copy(routeAdsTag = it))
        }
    }

    Text("EXPLICIT RULES", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    TinyField("Direct domains / geosite tags", s.routeDirectDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeDirectDomains = it))
    }
    TinyField("Proxy domains / exceptions", s.routeProxyDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeProxyDomains = it))
    }
    TinyField("Block domains / geosite tags", s.routeBlockDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeBlockDomains = it))
    }
    TinyField("Direct IP / CIDR / geoip tags", s.routeDirectIps, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeDirectIps = it))
    }
    TinyField("Block IP / CIDR / geoip tags", s.routeBlockIps, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeBlockIps = it))
    }
}

@Composable
private fun SubscriptionSettings(repo: AppRepository) {
    SettingSwitch(
        title = "Automatic refresh",
        subtitle = "Refresh stale subscriptions when MarbleNG starts",
        checked = repo.settings.subscriptionAutoRefresh
    ) {
        repo.updateSettings(repo.settings.copy(subscriptionAutoRefresh = it))
    }

    NumberSetting(
        title = "Refresh cadence",
        value = repo.settings.subscriptionRefreshHours,
        range = 1..168,
        suffix = "h"
    ) {
        repo.updateSettings(repo.settings.copy(subscriptionRefreshHours = it))
    }

    CyberButton(
        label = "REFRESH ALL NOW",
        color = Aether.Amethyst,
        modifier = Modifier.fillMaxWidth(),
        enabled = repo.subscriptions.isNotEmpty() && !repo.busy
    ) {
        repo.refreshAll()
    }
}

@Composable
private fun MaintenanceSettings(repo: AppRepository, onDialog: (String) -> Unit) {
    MaintenanceRow("System Doctor", "Runtime, assets, native bridge") {
        onDialog("System Doctor")
    }
    MaintenanceRow("Logs", "Shareable runtime diagnostics") {
        onDialog("Logs")
    }
    MaintenanceRow("Capabilities", "Current engine feature matrix") {
        onDialog("Capabilities")
    }
    MaintenanceRow("Core lock", "Pinned Xray / HEV metadata") {
        onDialog("Core lock")
    }
    MaintenanceRow("History", "Recent connection records") {
        onDialog("History")
    }

    CyberButton(
        label = "RESET SETTINGS",
        color = Aether.Danger,
        modifier = Modifier.fillMaxWidth()
    ) {
        repo.resetSettings()
    }
}

// =================================================================================================
// SHARED CONTROL PRIMITIVES
// =================================================================================================

@Composable
private fun CyberButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else .42f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        color.copy(alpha = .24f),
                        Aether.Glass.copy(alpha = .62f)
                    )
                )
            )
            .border(1.dp, color.copy(alpha = .30f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Aether.Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CyberChoiceChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) color.copy(alpha = .13f)
                else Aether.Glass.copy(alpha = .42f)
            )
            .border(
                1.dp,
                if (selected) color.copy(alpha = .38f)
                else Aether.GlassBorderSoft,
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) color else Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CyberSegment(
    label: String,
    detail: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(color.copy(alpha = .16f), Aether.Glass.copy(alpha = .54f))
                    )
                } else {
                    Brush.linearGradient(
                        listOf(Aether.Glass.copy(alpha = .38f), Aether.Glass.copy(alpha = .38f))
                    )
                }
            )
            .border(
                1.dp,
                if (selected) color.copy(alpha = .34f) else Aether.GlassBorderSoft,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(label, color = if (selected) color else Aether.Ink, style = MaterialTheme.typography.labelLarge)
        Text(detail, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (checked) Aether.Cyan.copy(alpha = .035f)
                else Color.Transparent
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Aether.Void,
                checkedTrackColor = Aether.Cyan,
                checkedBorderColor = Aether.Cyan
            )
        )
    }
}

@Composable
private fun NumberSetting(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onValue: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Aether.Void.copy(alpha = .30f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        TextButton(onClick = { onValue((value - 1).coerceAtLeast(range.first)) }) {
            Text("−", color = Aether.InkMuted)
        }

        Text(
            "$value$suffix",
            color = Aether.Cyan,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.Center
        )

        TextButton(onClick = { onValue((value + 1).coerceAtMost(range.last)) }) {
            Text("+", color = Aether.Cyan)
        }
    }
}

@Composable
private fun TinyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(17.dp)
    )
}

@Composable
private fun MaintenanceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(33.dp)
                .clip(CircleShape)
                .background(Aether.InkFaint.copy(alpha = .08f))
                .border(1.dp, Aether.GlassBorderSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("›", color = Aether.Cyan)
        }

        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
        }
        Text("OPEN", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyVisual(
    glyph: String,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = Aether.Cyan, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectionHealthMatrix(
    score: Int,
    latencyMs: Int,
    jitterMs: Int,
    packetLossPct: Int,
    resilienceScore: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ScoreRing(score = score, modifier = Modifier.size(92.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MicroStat("RTT", if (latencyMs > 0) "$latencyMs ms" else "—", Modifier.weight(1f))
                MicroStat("JITTER", "$jitterMs ms", Modifier.weight(1f))
                MicroStat("LOSS", "$packetLossPct%", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MicroStat("HEALTH", "$score/100", Modifier.weight(1f))
                MicroStat("RESILIENCE", "$resilienceScore/100", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * .085f
            drawArc(
                color = Color(0x221B2430),
                startAngle = -210f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val ring = when {
                score >= 80 -> Color(0xFF45FFB1)
                score >= 55 -> Color(0xFF20F6FF)
                score >= 35 -> Color(0xFFFFD35A)
                else -> Color(0xFFFF6F7A)
            }
            drawArc(
                brush = Brush.sweepGradient(listOf(ring.copy(alpha=.35f), ring)),
                startAngle = -210f,
                sweepAngle = 240f * (score.coerceIn(0,100) / 100f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), color = Aether.Ink, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
            Text("HEALTH", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TacticalIndicatorTile(title: String, state: String, color: Color, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Aether.Void.copy(alpha = .56f))
            .border(1.dp, color.copy(alpha = .22f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(title, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        Text(state, color = color, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
        Text(
            detail,
            color = Aether.InkMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MultiWanVisualizer(bonding: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "multiwan")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "multiwan-phase")
    val cyanRaw = Color(0xFF20F6FF)
    val purpleRaw = Color(0xFFB15CFF)
    val mintRaw = Color(0xFF45FFB1)
    Canvas(modifier) {
        val leftTop = Offset(size.width * .12f, size.height * .28f)
        val leftBottom = Offset(size.width * .12f, size.height * .72f)
        val center = Offset(size.width * .50f, size.height * .50f)
        val right = Offset(size.width * .87f, size.height * .50f)

        fun drawStream(from: Offset, to: Offset, color: Color, offset: Float) {
            drawLine(color.copy(alpha = .12f), from, to, strokeWidth = 12f, cap = StrokeCap.Round)
            drawLine(color.copy(alpha = .60f), from, to, strokeWidth = 2.2f, cap = StrokeCap.Round)
            val p = ((phase + offset) % 1f)
            val x = from.x + (to.x - from.x) * p
            val y = from.y + (to.y - from.y) * p
            drawCircle(color.copy(alpha = .18f), 10f, Offset(x, y))
            drawCircle(color, 3.1f, Offset(x, y))
        }

        drawStream(leftTop, center, cyanRaw, 0f)
        drawStream(leftBottom, center, purpleRaw, .28f)
        drawStream(center, right, if (bonding) mintRaw else cyanRaw, .14f)

        listOf(leftTop, leftBottom, center, right).forEachIndexed { idx, p ->
            val c = when(idx){0->cyanRaw;1->purpleRaw;2->if (bonding) mintRaw else cyanRaw; else->mintRaw}
            drawCircle(c.copy(alpha=.15f), 12f, p)
            drawCircle(c, 4f, p)
        }
    }
}

@Composable
private fun ThermalGauge(title: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Aether.Void.copy(alpha = .56f))
            .border(1.dp, color.copy(alpha = .20f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(title, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Aether.GlassBorderSoft)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0,100)/100f)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha=.55f), color)))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("$value%", color = color, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun DpiResilienceStrip(successfulPackets: Int, packetLossPct: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MicroStat("RESILIENCE", "$successfulPackets/100", Modifier.weight(1f))
        MicroStat("ROUTE STATE", if (packetLossPct > 7) "PRESSURED" else "HEALTHY", Modifier.weight(1f))
        MicroStat("LOSS", "$packetLossPct%", Modifier.weight(1f))
    }
}

@Composable
private fun TopologyLifecycleStrip(fragmentEnabled: Boolean, muxEnabled: Boolean, geo: String, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HoloBadge("CLIENT APPS", Aether.Cyan, compact = true)
        HoloBadge("TUN/HEV", Aether.Emerald, compact = true)
        HoloBadge(if (fragmentEnabled) "FRAGMENT ON" else "FRAGMENT OFF", Aether.Amber, compact = true)
        HoloBadge(if (muxEnabled) "MUX ON" else "MUX OFF", Aether.Amethyst, compact = true)
        HoloBadge("XRAY POLICY", Aether.Cyan, compact = true)
    }
}

@Composable
private fun FallbackClusterStrip(title: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Aether.Void.copy(alpha = .46f))
            .border(1.dp, Aether.GlassBorderSoft, RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⛓", color = Aether.Amethyst, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelMedium)
            Text(
                detail,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HoloBadge("READY", Aether.Amethyst, compact = true)
    }
}

@Composable
private fun FragmentMuxVisualizer(fragmentEnabled: Boolean, muxEnabled: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "frag-mux")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "frag-mux-phase")
    val cyanRaw = Color(0xFF20F6FF)
    val amberRaw = Color(0xFFFFD35A)
    val purpleRaw = Color(0xFFB15CFF)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2f
            val start = Offset(size.width * .08f, y)
            val splitX = size.width * .34f
            val mergeX = size.width * .68f
            val end = Offset(size.width * .92f, y)

            drawLine(cyanRaw.copy(alpha=.45f), start, Offset(splitX, y), strokeWidth = 2f, cap = StrokeCap.Round)
            val branches = if (fragmentEnabled) listOf(.25f,.42f,.58f,.75f) else listOf(.5f)
            branches.forEachIndexed { idx, f ->
                val by = size.height * f
                val p1 = Offset(splitX, y)
                val p2 = Offset(mergeX, by)
                drawLine(amberRaw.copy(alpha=.55f), p1, p2, strokeWidth = if (fragmentEnabled) 2f else 3f, cap = StrokeCap.Round)
                val t = (phase + idx * .17f) % 1f
                val x = p1.x + (p2.x - p1.x) * t
                val py = p1.y + (p2.y - p1.y) * t
                drawCircle(amberRaw, 2.8f, Offset(x, py))
            }
            val mergeColor = if (muxEnabled) purpleRaw else cyanRaw
            drawLine(mergeColor.copy(alpha=.62f), Offset(mergeX, y), end, strokeWidth = 3f, cap = StrokeCap.Round)
            drawCircle(mergeColor.copy(alpha=.12f), 12f, Offset(mergeX, y))
            drawCircle(mergeColor, 4f, Offset(mergeX, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HoloBadge("INPUT", Aether.Cyan, compact = true)
            HoloBadge(if (fragmentEnabled) "SPLIT 4" else "DIRECT", Aether.Amber, compact = true)
            HoloBadge(if (muxEnabled) "MUX AGGREGATE" else "SINGLE FLOW", Aether.Amethyst, compact = true)
        }
    }
}

@Composable
private fun SmartGateAssemblyLine(stages: List<GateStage>, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stages.forEachIndexed { index, stage ->
            Column(
                modifier = Modifier
                    .width(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Aether.Void.copy(alpha = .58f))
                    .border(1.dp, (if (stage.accepted) Aether.Emerald else Aether.Amber).copy(alpha = .22f), RoundedCornerShape(16.dp))
                    .padding(10.dp)
            ) {
                Text(stage.label, color = if (stage.accepted) Aether.Emerald else Aether.Amber, style = MaterialTheme.typography.labelSmall)
                Text(if (stage.accepted) "PASS" else "HOLD", color = Aether.Ink, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                Text(stage.detail, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            }
            if (index < stages.lastIndex) {
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("→", color = Aether.Cyan, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun AppGridTile(app: InstalledApp, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (checked) Aether.Emerald.copy(alpha = .08f) else Aether.Void.copy(alpha = .38f))
            .border(1.dp, if (checked) Aether.Emerald.copy(alpha=.26f) else Aether.GlassBorderSoft, RoundedCornerShape(15.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 9.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(Aether.Cyan.copy(alpha=.10f)).border(1.dp, Aether.GlassBorderSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.take(1).uppercase(), color = Aether.Cyan, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(7.dp))
            if (checked) HoloBadge("TUN", Aether.Emerald, compact = true) else HoloBadge("BYPASS", Aether.InkFaint, compact = true)
        }
        Spacer(Modifier.height(7.dp))
        Text(app.label, color = Aether.Ink, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(app.packageName, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// =================================================================================================
// FORMATTING / HEALTH
// =================================================================================================

@Composable
private fun healthColor(latencyMs: Int, success: Int): Color = when {
    success <= 0 -> Aether.InkFaint
    success < 50 -> Aether.Danger
    latencyMs in 1..130 && success >= 80 -> Aether.Emerald
    latencyMs in 1..280 -> Aether.Amber
    latencyMs > 280 -> Aether.Danger
    else -> Aether.Cyan
}

private fun rate(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB/s".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KB/s".format(bytes / 1024.0)
    else -> "$bytes B/s"
}

private fun compactRate(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1fM".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0fK".format(bytes / 1024.0)
    else -> bytes.toString()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GiB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.0f KiB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun relativeTime(at: Long): String {
    if (at <= 0L) return "never"
    val delta = System.currentTimeMillis() - at
    return when {
        delta < 60_000L -> "now"
        delta < 3_600_000L -> "${delta / 60_000L}m"
        delta < 86_400_000L -> "${delta / 3_600_000L}h"
        else -> "${delta / 86_400_000L}d"
    }
}

private fun relativeFuture(at: Long): String {
    val delta = at - System.currentTimeMillis()
    if (delta <= 0L) return "EXPIRED"
    return when {
        delta < 3_600_000L -> "${delta / 60_000L}m"
        delta < 86_400_000L -> "${delta / 3_600_000L}h"
        else -> "${delta / 86_400_000L}d"
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

