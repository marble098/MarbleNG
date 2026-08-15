package com.marbleng.app.ui

// Marble Product UI v9.0.0 • responsive calm-motion product surface

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.shadow
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
import com.marbleng.app.core.CensorTechnique
import com.marbleng.app.core.FilterSeverity
import com.marbleng.app.core.IranIspKind
import com.marbleng.app.core.IranModeState
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
    DECK("Home", "●"),
    LIBRARY("Library", "▦"),
    LAB("Quality", "◔"),
    RADAR("Network", "⌁"),
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

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    if (forward) {
                        (
                            slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            ) { width -> width / 7 } +
                                fadeIn(animationSpec = tween(180))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) { width -> -width / 9 } +
                                fadeOut(animationSpec = tween(130))
                        )
                    } else {
                        (
                            slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            ) { width -> -width / 7 } +
                                fadeIn(animationSpec = tween(180))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ) { width -> width / 9 } +
                                fadeOut(animationSpec = tween(130))
                        )
                    }
                },
                label = "marble-page-transition"
            ) { page ->
                when (page) {
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
                        repo = repo,
                        onDialog = { dialog = it },
                        focusSection = settingsFocus
                    )
                }
            }

            AnimatedVisibility(
                visible = repo.busy,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(180))
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
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
    val accent=Aether.Cyan; val base=Aether.Void; val raised=Aether.VoidElevated; val soft=Aether.GlassBorderSoft
    Canvas(modifier){
        drawRect(brush=Brush.verticalGradient(listOf(base,raised.copy(alpha=.34f),base)))
        drawCircle(brush=Brush.radialGradient(listOf(accent.copy(alpha=.045f),Color.Transparent),center=Offset(size.width*.82f,size.height*.10f),radius=size.minDimension*.72f),radius=size.minDimension*.72f,center=Offset(size.width*.82f,size.height*.10f))
        drawCircle(brush=Brush.radialGradient(listOf(soft.copy(alpha=.18f),Color.Transparent),center=Offset(size.width*.10f,size.height*.86f),radius=size.minDimension*.60f),radius=size.minDimension*.60f,center=Offset(size.width*.10f,size.height*.86f))
    }
}

@Composable
private fun FloatingSpatialDock(
    selected: SpatialTab,
    onSelect: (SpatialTab) -> Unit
) {
    val dockShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, dockShape, clip = false)
                .clip(dockShape)
                .background(Aether.VoidElevated.copy(alpha = .98f))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialTab.entries.forEach { item ->
                val active = item == selected
                val background by animateColorAsState(
                    targetValue = if (active) Aether.Cyan.copy(alpha = .11f) else Color.Transparent,
                    animationSpec = tween(220),
                    label = "nav-background-${item.name}"
                )
                val glyphColor by animateColorAsState(
                    targetValue = if (active) Aether.Cyan else Aether.InkFaint,
                    animationSpec = tween(220),
                    label = "nav-glyph-${item.name}"
                )
                val textColor by animateColorAsState(
                    targetValue = if (active) Aether.Ink else Aether.InkMuted,
                    animationSpec = tween(220),
                    label = "nav-text-${item.name}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(background)
                        .clickable { onSelect(item) }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        item.glyph,
                        color = glyphColor,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        item.label,
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            eyebrow,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!status.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                HoloBadge(status, statusColor, compact = true)
            }
        }
        Text(
            subtitle,
            color = Aether.InkMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(Aether.VoidElevated)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

@Composable
private fun HoloBadge(
    text: String,
    color: Color,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(color.copy(alpha = .085f))
            .padding(
                horizontal = if (compact) 7.dp else 9.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionLabel(title:String,subtitle:String?=null){
    Column(Modifier.padding(horizontal=4.dp,vertical=2.dp)){Text(title,color=Aether.Ink,style=MaterialTheme.typography.titleMedium);if(!subtitle.isNullOrBlank()){Spacer(Modifier.height(2.dp));Text(subtitle,color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)}}
}

// =================================================================================================
// DECK
// =================================================================================================

@Composable
private fun CyberDeck(repo:AppRepository,onConnect:(ProxyProfile)->Unit,onLibrary:()->Unit,onPrivacy:()->Unit,onRouting:()->Unit){
    val connected=repo.state=="CONNECTED"; val connecting=repo.state=="CONNECTING"; val blocked=repo.state=="BLOCKED"
    val activeName=repo.stateDetail.ifBlank{repo.lastProfile()?.name?:"No active connection"}
    val lastId=repo.lastProfile()?.id
    val bench=repo.benchmarks.firstOrNull{it.name==activeName || (lastId!=null && it.profileId==lastId)}
    val score=when{bench!=null&&bench.success>0->bench.score.roundToInt().coerceIn(0,100);connected&&repo.livePingMs>0->(94-repo.livePingMs/7-repo.liveJitterMs/4).coerceIn(35,94);connected->78;else->0}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{SpatialHeader("Privacy dashboard","MarbleNG","Private networking, quietly monitored",when{connected->"Protected";connecting->"Connecting";blocked->"Blocked";else->"Idle"},when{connected->Aether.Emerald;connecting->Aether.Cyan;blocked->Aether.Danger;else->Aether.InkFaint})}
        item{ConnectionCore(activeName,connected,connecting,blocked,repo.settings.connectionMode,repo.settings.localProxyPort,repo.livePingMs,repo.liveDownBps,repo.liveUpBps){if(connected||connecting)repo.stopVpn() else repo.auto(onConnect)}}
        item{PerformanceScoreOverview(score,connected,activeName)}
        item{SectionLabel("Connection path","A simpler view of where protected traffic goes");HoloGlass(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=14.dp,vertical=16.dp)){SpatialRouteMap(repo.settings.connectionMode,activeName,repo.profiles.firstOrNull{it.id==repo.settings.chainSecondProfileId}?.name,repo.settings.chainEnabled,connected,Modifier.fillMaxWidth().height(112.dp))}}
        item{SectionLabel("Shortcuts");Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){HoloActionPill("◔","Performance","Measure routes",Aether.Cyan,Modifier.weight(1f)){repo.smartRank()};HoloActionPill("▦","Library","${repo.profiles.size} connections",Aether.Cyan,Modifier.weight(1f)){onLibrary()}};Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){HoloActionPill("◇","Privacy audit",if(connected)"Check egress & DNS" else "Connect first",Aether.Emerald,Modifier.weight(1f)){onPrivacy()};HoloActionPill("⚙","Routing","Open Expert settings",Aether.InkMuted,Modifier.weight(1f)){onRouting()}}}
        item{Text("Advanced network controls are intentionally kept in Settings → Expert controls.",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=4.dp,vertical=8.dp))}
    }
}

@Composable private fun PerformanceScoreOverview(score:Int,connected:Boolean,routeName:String){
    HoloGlass(Modifier.fillMaxWidth(),contentPadding=PaddingValues(18.dp)){Row(verticalAlignment=Alignment.CenterVertically){ScoreRing(score,Modifier.size(106.dp));Spacer(Modifier.width(18.dp));Column(Modifier.weight(1f)){Text("Performance score",color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(4.dp));Text(when{!connected->"Connect to build a live score.";score>=85->"Excellent route quality";score>=70->"Healthy route quality";score>=50->"Usable, with some pressure";else->"Route quality needs attention"},color=if(connected&&score>=70)Aether.Emerald else Aether.InkMuted,style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(5.dp));Text(if(connected)routeName else "Based on latency, reliability and route evidence.",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}}}
}

@Composable
private fun ConnectionCore(
    activeName: String,
    connected: Boolean,
    connecting: Boolean,
    blocked: Boolean,
    mode: ConnectionMode,
    localPort: Int,
    pingMs: Int,
    downBps: Long,
    upBps: Long,
    onToggle: () -> Unit
) {
    var detailsOpen by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "connection-breath")
    val breath by transition.animateFloat(
        initialValue = .975f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connection-breath-value"
    )
    val statusColor = when {
        connected -> Aether.Emerald
        connecting -> Aether.Cyan
        blocked -> Aether.Danger
        else -> Aether.InkFaint
    }
    val track = Aether.GlassBorderSoft
    val actionShape = RoundedCornerShape(18.dp)

    HoloGlass(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(172.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    val scale = if (connected) breath else 1f
                    val radius = size.minDimension * .40f
                    if (connected) {
                        drawCircle(
                            statusColor.copy(alpha = .045f),
                            size.minDimension * .49f * scale
                        )
                    }
                    drawCircle(track, radius, style = Stroke(7f, cap = StrokeCap.Round))
                    drawArc(
                        color = statusColor,
                        startAngle = -90f,
                        sweepAngle = when {
                            connected -> 360f
                            connecting -> 255f
                            blocked -> 120f
                            else -> 48f
                        },
                        useCenter = false,
                        topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(7f, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            connected -> "Protected"
                            connecting -> "Connecting"
                            blocked -> "Blocked"
                            else -> "Ready"
                        },
                        color = Aether.Ink,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (mode == ConnectionMode.FULL_TUN) "Full-device tunnel" else "Local SOCKS5",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                activeName,
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(15.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(2.dp, actionShape, clip = false)
                    .clip(actionShape)
                    .background(
                        when {
                            connected -> Aether.GlassStrong
                            blocked -> Aether.Danger.copy(alpha = .14f)
                            else -> Aether.Cyan
                        }
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        connected -> "Disconnect"
                        connecting -> "Stop connection"
                        blocked -> "Retry connection"
                        else -> "Connect securely"
                    },
                    color = if (!connected && !blocked) Color.White else Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }

            TextButton(onClick = { detailsOpen = !detailsOpen }) {
                Text(
                    if (detailsOpen) "Hide details" else "Connection details",
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            AnimatedVisibility(
                visible = detailsOpen,
                enter = fadeIn(tween(150)) + expandVertically(tween(220)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(180))
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = Aether.GlassBorderSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniMetric(
                            "HTTPS RTT",
                            if (pingMs > 0) "$pingMs" else "—",
                            "ms",
                            Modifier.weight(1f)
                        )
                        MiniMetric("Download", compactRate(downBps), "", Modifier.weight(1f))
                        MiniMetric("Upload", compactRate(upBps), "", Modifier.weight(1f))
                    }
                    Text(
                        if (mode == ConnectionMode.FULL_TUN)
                            "Android VpnService captures device traffic through HEV → Xray."
                        else
                            "SOCKS5 is available only on 127.0.0.1:$localPort.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .heightIn(min = 72.dp)
            .shadow(1.dp, shape, clip = false)
            .clip(shape)
            .background(Aether.VoidElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = .10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = color, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpatialRouteMap(mode:ConnectionMode,entryName:String,exitName:String?,chainEnabled:Boolean,connected:Boolean,modifier:Modifier=Modifier){
    val active=if(connected)Aether.Cyan else Aether.InkFaint;Row(modifier,verticalAlignment=Alignment.CenterVertically){PremiumFlowStage("Device","Apps",Aether.InkMuted,Modifier.weight(1f));PremiumFlowConnector(connected,active,Modifier.weight(.42f));PremiumFlowStage("Secure tunnel",when{chainEnabled&&!exitName.isNullOrBlank()->"${if(mode==ConnectionMode.FULL_TUN)"TUN" else "SOCKS"} • 2-hop";mode==ConnectionMode.FULL_TUN->"TUN • Xray";else->"Local SOCKS"},active,Modifier.weight(1.18f));PremiumFlowConnector(connected,active,Modifier.weight(.42f));PremiumFlowStage("Internet",if(connected)"Protected" else "Waiting",if(connected)Aether.Emerald else Aether.InkFaint,Modifier.weight(1f))}
}
@Composable private fun PremiumFlowStage(title:String,detail:String,color:Color,modifier:Modifier=Modifier){Column(modifier.clip(RoundedCornerShape(18.dp)).background(Aether.GlassStrong.copy(alpha=.72f)).padding(horizontal=8.dp,vertical=13.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(8.dp).clip(CircleShape).background(color));Spacer(Modifier.height(7.dp));Text(title,color=Aether.Ink,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,maxLines=1);Text(detail,color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)}}
@Composable private fun PremiumFlowConnector(active:Boolean,color:Color,modifier:Modifier=Modifier){Box(modifier.padding(horizontal=5.dp).height(4.dp).clip(CircleShape).background(if(active)color.copy(alpha=.55f) else Aether.GlassBorderSoft))}

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
                            label = "Refresh",
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
                eyebrow = "Connections",
                title = "Library",
                subtitle = "Subscriptions, connections and measured route health",
                status = "${repo.profiles.size} nodes",
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
                    placeholder = { Text("Search connections") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                )

                Button(
                    onClick = { addOpen = !addOpen },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Aether.Cyan.copy(alpha = .10f),
                        contentColor = Aether.Ink
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text(
                        if (addOpen) "Close" else "+ Add",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (addOpen) {
            item {
                HoloGlass(
                    modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
                    glow = Aether.Amethyst
                ) {
                    Text("Add source", color = Aether.AmethystBright, style = MaterialTheme.typography.labelSmall)
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
                            label = "Add subscription",
                            color = Aether.Cyan,
                            modifier = Modifier.weight(1f),
                            enabled = url.startsWith("http")
                        ) {
                            repo.addSubscription(sourceName, url)
                            url = ""
                            sourceName = ""
                        }
                        CyberButton(
                            label = "Import file",
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
                    label = "Refresh",
                    color = Aether.Amethyst,
                    modifier = Modifier.weight(1f),
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy
                ) { repo.refreshAll() }

                CyberButton(
                    label = "Test all",
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
                        Text("Filter", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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
                        Text("Sort nodes", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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
                    Text("No connections", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
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
    val fraction =
        if (sub.totalBytes > 0L)
            (used.toFloat() / sub.totalBytes.toFloat()).coerceIn(0f, 1f)
        else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "subscription-quota-${sub.id}"
    )
    val expired = sub.expireAt > 0L && sub.expireAt < System.currentTimeMillis()
    val color = when {
        expired -> Aether.Danger
        selected -> Aether.Cyan
        else -> Aether.InkMuted
    }

    HoloGlass(
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(color.copy(alpha = .09f)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = color, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sub.name,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$count nodes • ${relativeTime(sub.updatedAt)}",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            if (selected) HoloBadge("Selected", Aether.Cyan, compact = true)
        }

        Text(
            sub.url,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (sub.totalBytes > 0L) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Aether.GlassBorderSoft)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .background(Aether.Cyan)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Used ${formatBytes(used)}",
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "Left ${formatBytes((sub.totalBytes - used).coerceAtLeast(0L))}",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            Text(
                "Quota information not provided",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (sub.expireAt > 0L) {
            Text(
                "Expires ${relativeFuture(sub.expireAt)}",
                color = if (expired) Aether.Danger else Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CyberButton(if (selected) "Selected" else "View", Aether.Cyan, Modifier.weight(1f)) { onView() }
            CyberButton("Refresh", Aether.Cyan, Modifier.weight(1f), enabled = !repo.busy) { repo.refresh(sub.id) }
            CyberButton("Manage", Aether.InkMuted, Modifier.weight(1f), enabled = !repo.busy) { onManage() }
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

    HoloGlass(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .animateContentSize(tween(180)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthOrb(
                label = countryGlyph(profile.host),
                color = if (active) Aether.Emerald else health,
                active = active,
                modifier = Modifier.size(44.dp)
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
                val connectionLine = listOf(
                    profile.scheme.uppercase(),
                    profile.transport.uppercase().takeIf { it.isNotBlank() },
                    profile.security.uppercase().takeIf {
                        it.isNotBlank() && !it.equals("NONE", true)
                    }
                ).filterNotNull().joinToString(" • ")
                Text(
                    connectionLine,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (latency > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$latency",
                        color = health,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text("ms", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background((if (active) Aether.Emerald else Aether.Cyan).copy(alpha = .10f))
                    .clickable {
                        if (active) repo.stopVpn() else onConnect(profile)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (active) "■" else "▶",
                    color = if (active) Aether.Emerald else Aether.Cyan,
                    style = MaterialTheme.typography.labelLarge
                )
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

        if (active || (result != null && result.success > 0)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active) HoloBadge("Active", Aether.Emerald, compact = true)
                if (result != null && result.success > 0) {
                    HoloBadge(
                        "${result.success}% success",
                        if (result.success >= 90) Aether.Emerald else Aether.Amber,
                        compact = true
                    )
                    Text(
                        "Jitter ${result.jitterMs.toInt()} ms • Score ${result.score.roundToInt().coerceIn(0, 100)}",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = .065f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun MicroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Aether.GlassStrong.copy(alpha = .52f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
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
        Spacer(Modifier.height(1.dp))
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
    val best = repo.benchmarks.firstOrNull { it.success > 0 }
    val score = best?.score?.roundToInt()?.coerceIn(0, 100) ?: 0
    val latency = best?.latencyMs?.toInt() ?: 0

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SpatialHeader(
                "Performance",
                "Route quality",
                "A measured score with latency, reliability and jitter kept visible",
                if (best == null) "No sample" else "$score / 100",
                when {
                    best == null -> Aether.InkFaint
                    score >= 80 && latency in 1..500 -> Aether.Emerald
                    score >= 55 && latency in 1..1500 -> Aether.Cyan
                    else -> Aether.Amber
                }
            )
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { mode ->
                    CyberChoiceChip(
                        mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        repo.settings.benchMode == mode,
                        Aether.Cyan
                    ) {
                        repo.updateSettings(repo.settings.copy(benchMode = mode))
                    }
                }
            }
        }

        item {
            CyberButton(
                if (repo.busy) "Measuring…" else "Run performance test",
                Aether.Cyan,
                Modifier.fillMaxWidth(),
                repo.profiles.isNotEmpty() && !repo.busy
            ) {
                repo.smart(onConnect)
            }
        }

        item {
            HoloGlass(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                if (best == null) {
                    EmptyVisual(
                        "◔",
                        "No performance sample yet",
                        "Run a performance test to measure real Xray routes."
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(score, Modifier.size(124.dp))
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Best measured route",
                                color = Aether.InkFaint,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                best.name,
                                color = Aether.Ink,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                when {
                                    latency >= 4000 -> "Very high latency"
                                    latency >= 2000 -> "High latency"
                                    latency >= 1000 -> "Latency constrained"
                                    score >= 85 -> "Excellent"
                                    score >= 70 -> "Healthy"
                                    score >= 50 -> "Fair"
                                    else -> "Needs attention"
                                },
                                color = when {
                                    latency >= 2000 -> Aether.Danger
                                    latency >= 1000 -> Aether.Amber
                                    score >= 70 -> Aether.Emerald
                                    else -> Aether.InkMuted
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider(color = Aether.GlassBorderSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MicroStat("Reliability", "${best.success}%", Modifier.weight(1f))
                        MicroStat("RTT", "${best.latencyMs.toInt()} ms", Modifier.weight(1f))
                        MicroStat("Jitter", "${best.jitterMs.toInt()} ms", Modifier.weight(1f))
                    }
                }
            }
        }

        if (repo.benchmarks.isNotEmpty()) {
            item {
                SectionLabel(
                    "Measured routes",
                    "Ranked evidence from complete proxy-path tests"
                )
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
    0 -> Color(0xFF365FD9)
    1 -> Color(0xFF6078BE)
    else -> Color(0xFF7FA68B)
}

@Composable
private fun CompactBenchmarkRow(
    result: BenchmarkResult,
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit
) {
    val latencyColor = healthColor(result.latencyMs.toInt(), result.success)
    val successColor =
        if (result.success >= 90) Aether.Emerald
        else if (result.success >= 65) Aether.Amber
        else Aether.Danger

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(latencyColor.copy(alpha = .09f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${repo.benchmarks.indexOf(result) + 1}",
                    color = latencyColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }

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
                    "${result.latencyMs.toInt()} ms • ${result.jitterMs.toInt()} ms jitter • ${rate(result.bytesPerSecond.toLong())}",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HoloBadge("${result.success}%", successColor, compact = true)
            TextButton(
                onClick = { repo.profile(result.profileId)?.let(onConnect) },
                enabled = result.success > 0
            ) {
                Text("Use", color = Aether.Cyan, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// =================================================================================================
// RADAR / TELEGRAM DISCOVERY
// =================================================================================================

@Composable
private fun DiscoveryRadar(repo:AppRepository){
    val network=repo.networkSnapshot;val intel=repo.intelligenceStatus;val iran=repo.iranMode;val linkColor=if(network.validated)Aether.Emerald else Aether.Amber
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{SpatialHeader("Network","Environment","Physical-link context and Marble Intelligence",if(network.validated)"Healthy" else "Checking",linkColor)}
        item{SectionLabel("Current network");HoloGlass(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(network.label,color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Text("${if(network.metered)"Metered" else "Unmetered"} • ${if(network.hasIpv4)"IPv4" else "No IPv4"} • ${if(network.hasIpv6)"IPv6" else "No IPv6"}",color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)};HoloBadge(if(network.validated)"Validated" else "Unvalidated",linkColor,true)};Text("MTU ${network.mtu.takeIf{it>0}?:0} • Downlink ${network.downstreamKbps.coerceAtLeast(0)} kbps • Uplink ${network.upstreamKbps.coerceAtLeast(0)} kbps",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall);CyberButton(if(iran.scanning)"Checking network…" else "Refresh network check",Aether.Cyan,Modifier.fillMaxWidth(),!iran.scanning){repo.scanIranMode(force=true,deep=true)}}}
        item{SectionLabel("Marble Intelligence");HoloGlass(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(repo.settings.intelligenceEnabled)"Adaptive engine enabled" else "Adaptive engine disabled",color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Text("Effective MTU ${intel.effectiveMtu.takeIf{it>0}?:network.mtu} • Thermal budget ${intel.thermalBudgetPercent}% • History ${intel.historyRecords}",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)};HoloBadge(if(repo.settings.intelligenceEnabled)"On" else "Off",if(repo.settings.intelligenceEnabled)Aether.Emerald else Aether.InkFaint,true)};Text(intel.lastDecision.ifBlank{"Waiting for the next network decision"},color=Aether.InkMuted,style=MaterialTheme.typography.bodyMedium)}}
        if(iran.active){item{SectionLabel("Regional protection");HoloGlass(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(iran.ispLine,color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Text(iran.summary,color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)};HoloBadge("${iran.confidence}%",Aether.Emerald,true)}}}}
        if(repo.benchmarks.isNotEmpty()){item{SectionLabel("Recent measurements","Read-only route evidence")};items(repo.benchmarks.sortedByDescending{it.score}.take(6),key={"network-memory-${it.profileId}"}){r->val c=healthColor(r.latencyMs.toInt(),r.success);HoloGlass(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=14.dp,vertical=12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.name,color=Aether.Ink,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${r.latencyMs.toInt()} ms • jitter ${r.jitterMs.toInt()} ms • success ${r.success}%",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)};HoloBadge("${r.score.roundToInt().coerceIn(0,100)}",c,true)}}}}
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
    var expertMode by remember(focusSection) { mutableStateOf(focusSection == "Routing") }

    LaunchedEffect(focusSection) {
        if (focusSection == "Routing") {
            expertMode = true
            delay(80)
            listState.animateScrollToItem(9)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            SpatialHeader(
                "Preferences",
                "Settings",
                "Simple controls first. Technical controls stay available when you need them.",
                if (expertMode) "Expert" else "Simple",
                if (expertMode) Aether.Cyan else Aether.Emerald
            )
        }

        item {
            HoloGlass(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Appearance", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Choose the interface that matches your environment.",
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CyberChoiceChip(
                            "Light",
                            repo.settings.theme.equals("light", true),
                            Aether.Cyan
                        ) {
                            repo.updateSettings(repo.settings.copy(theme = "light"))
                        }
                        CyberChoiceChip(
                            "Dark",
                            !repo.settings.theme.equals("light", true),
                            Aether.Cyan
                        ) {
                            repo.updateSettings(repo.settings.copy(theme = "dark"))
                        }
                    }
                }

                HorizontalDivider(color = Aether.GlassBorderSoft)
                SettingSwitch(
                    "Expert controls",
                    "Reveal MTU, DNS, routing, fragmentation, recovery and chain settings",
                    expertMode
                ) { expertMode = it }
            }
        }

        item { SpatialAccordion("Connection","Full-device tunnel or local SOCKS proxy","Core",Aether.Cyan,true){ConnectionSettings(repo)} }
        item { SpatialAccordion("Split tunneling","Choose exactly which apps use or bypass the tunnel","Apps",Aether.Emerald){SplitTunnelSettings(repo)} }
        item { SpatialAccordion("Notifications","Connection, recovery and privacy alerts","Alerts",Aether.InkMuted){NotificationSettings(repo)} }
        item { SpatialAccordion("Subscriptions","Automatic refresh cadence","Sync",Aether.Cyan){SubscriptionSettings(repo)} }

        if (expertMode) {
            item { SpatialAccordion("Regional protection","Iran Mode detection and countermeasures","Expert",Aether.Emerald){IranModeSettings(repo)} }
            item { SpatialAccordion("Marble Intelligence","Adaptive MTU, route history, recovery and optimizer policy","Expert",Aether.Cyan){IntelligenceSettings(repo)} }
            item { SpatialAccordion("DNS","TUN resolvers and encrypted DoH path","Expert",Aether.Cyan){DnsSettings(repo)} }
            item { SpatialAccordion("Routing","Geo assets, direct rules and blocking policy","Expert",Aether.Emerald,focusSection=="Routing"){RoutingSettings(repo)} }
            item { SpatialAccordion("Fragmentation & Mux","DPI resilience and connection reuse","Expert",Aether.InkMuted){FragmentMuxSettings(repo)} }
            item { SpatialAccordion("Chain proxy","Optional two-hop route","Expert",Aether.InkMuted){ChainSettings(repo)} }
        }

        item { SpatialAccordion("Maintenance","Diagnostics, logs and recovery tools","Tools",Aether.InkMuted){MaintenanceSettings(repo,onDialog)} }

        item {
            Text(
                if (expertMode)
                    "Expert controls change low-level Xray and tunnel behavior."
                else
                    "Advanced controls stay out of the way until you enable Expert mode.",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
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
    val shape = RoundedCornerShape(20.dp)
    val iconBackground by animateColorAsState(
        targetValue = if (open) color.copy(alpha = .11f) else Aether.GlassStrong.copy(alpha = .55f),
        animationSpec = tween(180),
        label = "accordion-icon-$title"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(220, easing = FastOutSlowInEasing))
            .shadow(1.dp, shape, clip = false)
            .clip(shape)
            .background(Aether.VoidElevated)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = open,
                    transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(90)) },
                    label = "accordion-chevron-$title"
                ) { expanded ->
                    Text(
                        if (expanded) "−" else "+",
                        color = if (expanded) color else Aether.InkMuted,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            HoloBadge(badge, color, compact = true)
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(100))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                HorizontalDivider(color = Aether.GlassBorderSoft)
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
        title = "Continuous Marble Autopilot",
        subtitle = "Continuously verify the active route and rotate real Xray challenges through the whole library",
        checked = s.continuousOptimizerEnabled
    ) { repo.updateSettings(repo.settings.copy(continuousOptimizerEnabled = it)) }

    AnimatedVisibility(s.continuousOptimizerEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            NumberSetting("Autopilot interval", s.optimizerIntervalSec, 60..900, " sec") {
                repo.updateSettings(repo.settings.copy(optimizerIntervalSec = it))
            }
            NumberSetting("Challengers per cycle", s.optimizerCandidateCount, 2..8) {
                repo.updateSettings(repo.settings.copy(optimizerCandidateCount = it))
            }
            NumberSetting("Deep speed cycle", s.optimizerDeepScanEvery, 3..20, " cycles") {
                repo.updateSettings(repo.settings.copy(optimizerDeepScanEvery = it))
            }
            NumberSetting("Switch cooldown", s.optimizerSwitchCooldownSec, 60..1800, " sec") {
                repo.updateSettings(repo.settings.copy(optimizerSwitchCooldownSec = it))
            }
            NumberSetting("Evidence confirmations", s.optimizerConfirmations, 1..3) {
                repo.updateSettings(repo.settings.copy(optimizerConfirmations = it))
            }
            SettingSwitch(
                title = "Protect heavy downloads",
                subtitle = "Delay non-urgent challenger scans while throughput is already high",
                checked = s.optimizerAvoidHeavyTraffic
            ) { repo.updateSettings(repo.settings.copy(optimizerAvoidHeavyTraffic = it)) }
        }
    }

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
private fun SplitTunnelSettings(repo:AppRepository){
    val context=LocalContext.current;val pm=context.packageManager;var search by remember{mutableStateOf("")}
    val apps=remember{val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);@Suppress("DEPRECATION") pm.queryIntentActivities(intent,PackageManager.MATCH_ALL).mapNotNull{r->val pkg=r.activityInfo?.packageName?:return@mapNotNull null;if(pkg==context.packageName)return@mapNotNull null;InstalledApp(runCatching{r.loadLabel(pm).toString()}.getOrDefault(pkg),pkg)}.distinctBy{it.packageName}.sortedBy{it.label.lowercase()}}
    val selected=remember(repo.settings.splitTunnelPackages){repo.settings.splitTunnelPackages.split(',', '\n','\r',';').map(String::trim).filter(String::isNotBlank).toSet()}
    val visibleApps=remember(apps,search){apps.filter{search.isBlank()||it.label.contains(search,true)||it.packageName.contains(search,true)}}
    fun toggle(pkg:String){val n=selected.toMutableSet();if(!n.add(pkg))n.remove(pkg);repo.updateSettings(repo.settings.copy(splitTunnelPackages=n.sorted().joinToString(",")))}
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){SplitTunnelMode.entries.forEach{m->CyberChoiceChip(when(m){SplitTunnelMode.ALL_APPS->"All apps";SplitTunnelMode.ONLY_SELECTED->"Only selected";SplitTunnelMode.BYPASS_SELECTED->"Bypass selected"},repo.settings.splitTunnelMode==m,Aether.Emerald){repo.updateSettings(repo.settings.copy(splitTunnelMode=m))}}}
    if(repo.settings.splitTunnelMode!=SplitTunnelMode.ALL_APPS){
        TextField(search,{search=it},placeholder={Text("Search installed apps")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Aether.GlassStrong,unfocusedContainerColor=Aether.GlassStrong,disabledContainerColor=Aether.GlassStrong,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("${visibleApps.size} apps",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall);HoloBadge("${selected.size} selected",Aether.Emerald,true)}
        LazyColumn(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(18.dp)).background(Aether.Glass.copy(alpha=.70f)),contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp),userScrollEnabled=true){items(visibleApps,key={it.packageName}){app->SplitTunnelAppRow(app,app.packageName in selected){toggle(app.packageName)}}}
        Text("Changes apply on the next Full TUN connection.",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SplitTunnelAppRow(app:InstalledApp,checked:Boolean,onToggle:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick=onToggle).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if(checked)Aether.Emerald.copy(alpha=.12f) else Aether.GlassStrong),contentAlignment=Alignment.Center){Text(app.label.trim().firstOrNull()?.uppercase()?:"•",color=if(checked)Aether.Emerald else Aether.InkMuted,style=MaterialTheme.typography.labelLarge)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(app.label,color=Aether.Ink,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(app.packageName,color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Checkbox(checked,{onToggle()},colors=CheckboxDefaults.colors(checkedColor=Aether.Emerald,checkmarkColor=Aether.Void))}
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

    fun hasTag(raw: String, name: String, prefix: String): Boolean =
        raw.split(',', '\n', '\r', ';')
            .map(String::trim)
            .any {
                it.equals(name, ignoreCase = true) ||
                    it.equals("$prefix:$name", ignoreCase = true)
            }

    val iranGeoIp = hasTag(s.routeGeoIpTags, "ir", "geoip")
    val iranGeoSite = hasTag(s.routeGeoSiteTags, "ir", "geosite")
    val iranDirect =
        s.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) &&
            iranGeoIp && iranGeoSite

    val geoIpTokens = s.routeGeoIpTags
        .split(',', '\n', '\r', ';')
        .map(String::trim)
        .filter(String::isNotBlank)

    val needsGeoIp =
        s.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) &&
            geoIpTokens.any {
                !it.equals("private", true) && !it.equals("geoip:private", true)
            }

    val needsGeoSite =
        (s.routingMode in setOf(RoutingMode.GEO_DIRECT, RoutingMode.CUSTOM) &&
            s.routeGeoSiteTags.isNotBlank()) ||
            (s.routeBlockAds && s.routeAdsTag.isNotBlank()) ||
            listOf(s.routeDirectDomains, s.routeProxyDomains, s.routeBlockDomains)
                .any { it.contains("geosite:", true) }

    val missingGeoAssets = listOfNotNull(
        "geoip.dat".takeIf { needsGeoIp && !assets.geoIpReady },
        "geosite.dat".takeIf { needsGeoSite && !assets.geoSiteReady }
    ).joinToString(" + ")

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Recommended Iran policy",
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Iran domains/IPs direct • ads blocked • international traffic stays on proxy",
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HoloBadge(
                if (iranDirect && s.routeBlockAds) "Active" else "Custom",
                if (iranDirect && s.routeBlockAds) Aether.Emerald else Aether.Amber,
                compact = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoloBadge(
                if (iranDirect) "Iran direct" else "Iran proxied",
                if (iranDirect) Aether.Emerald else Aether.InkFaint,
                compact = true
            )
            HoloBadge(
                if (s.routeBlockAds) "Ads blocked" else "Ads allowed",
                if (s.routeBlockAds) Aether.Emerald else Aether.InkFaint,
                compact = true
            )
            HoloBadge(
                s.routeDomainStrategy,
                Aether.Cyan,
                compact = true
            )
        }

        CyberButton(
            label = "RESTORE RECOMMENDED IRAN POLICY",
            color = Aether.Emerald,
            modifier = Modifier.fillMaxWidth(),
            enabled = !repo.busy
        ) {
            repo.applyIranRoutingPreset()
        }
    }

    SettingSwitch(
        title = "Bypass Iranian traffic",
        subtitle = "geosite:ir + geoip:ir use the physical network; international traffic remains proxied",
        checked = iranDirect
    ) { enabled ->
        if (enabled) {
            repo.applyIranRoutingPreset()
        } else {
            repo.updateSettings(
                s.copy(
                    routingMode = RoutingMode.PROXY_ALL,
                    routeGeoIpTags = "",
                    routeGeoSiteTags = "",
                    routeBypassPrivate = false,
                    iranDomesticDirect = false
                )
            )
        }
    }

    SettingSwitch(
        title = "Aggressive ad blocking",
        subtitle = "Block geosite:category-ads-all before direct/proxy rules",
        checked = s.routeBlockAds
    ) {
        repo.updateSettings(
            s.copy(
                routeBlockAds = it,
                routeAdsTag = if (s.routeAdsTag.isBlank()) RoutingDefaults.ADS_TAG else s.routeAdsTag
            )
        )
    }

    Text("Routing mode", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RoutingMode.entries.forEach { mode ->
            CyberChoiceChip(
                text = when (mode) {
                    RoutingMode.PROXY_ALL -> "Proxy all"
                    RoutingMode.BYPASS_PRIVATE -> "Private direct"
                    RoutingMode.GEO_DIRECT -> "Geo direct"
                    RoutingMode.CUSTOM -> "Custom"
                },
                selected = s.routingMode == mode,
                color = Aether.Emerald
            ) {
                repo.updateSettings(
                    s.copy(
                        routingMode = mode,
                        iranDomesticDirect = when (mode) {
                            RoutingMode.PROXY_ALL -> false
                            RoutingMode.GEO_DIRECT -> iranGeoIp && iranGeoSite
                            else -> s.iranDomesticDirect
                        }
                    )
                )
            }
        }
    }

    Text("Geo data", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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

    if (missingGeoAssets.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Aether.Amber.copy(alpha = .08f))
                .padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("!", color = Aether.Amber, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(9.dp))
            Text(
                "$missingGeoAssets is required. Signed builds contain a bundled fallback; Prepare also refreshes configured sources.",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    TinyField("geoip.dat source", s.geoIpUrl, Modifier.fillMaxWidth()) {
        repo.updateSettings(s.copy(geoIpUrl = it))
    }
    TinyField("geosite.dat source", s.geoSiteUrl, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(geoSiteUrl = it))
    }

    Text(
        "Default source: Chocolate4U/Iran-v2ray-rules release branch. Marble refreshes remote data after 24 hours and keeps the last known-good file if refresh fails.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CyberButton(
            label = "PREPARE",
            color = Aether.Emerald,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.prepareRoutingAssets(false) }
        CyberButton(
            label = "UPDATE NOW",
            color = Aether.Cyan,
            modifier = Modifier.weight(1f),
            enabled = !repo.busy
        ) { repo.prepareRoutingAssets(true) }
    }

    CyberButton(
        label = "VERIFY WITH XRAY",
        color = Aether.Cyan,
        modifier = Modifier.fillMaxWidth(),
        enabled = repo.profiles.isNotEmpty() && !repo.busy
    ) { repo.verifyRoutingPolicy() }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text("Geo direct rules", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)

    TinyField("GeoIP direct tags", s.routeGeoIpTags, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeGeoIpTags = it))
    }
    TinyField("GeoSite direct tags", s.routeGeoSiteTags, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeGeoSiteTags = it))
    }

    SettingSwitch(
        title = "Bypass private networks",
        subtitle = "LAN/link-local/private CIDRs go direct without depending on geoip.dat",
        checked = s.routeBypassPrivate
    ) { repo.updateSettings(repo.settings.copy(routeBypassPrivate = it)) }

    Text("Domain strategy", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf("IPIfNonMatch", "AsIs", "IPOnDemand").forEach { strategy ->
            CyberChoiceChip(
                text = strategy,
                selected = s.routeDomainStrategy == strategy,
                color = Aether.Cyan
            ) {
                repo.updateSettings(repo.settings.copy(routeDomainStrategy = strategy))
            }
        }
    }

    if (s.routeBlockAds) {
        TinyField("Ad geosite category", s.routeAdsTag, Modifier.fillMaxWidth()) {
            repo.updateSettings(repo.settings.copy(routeAdsTag = it))
        }
    }

    HorizontalDivider(color = Aether.GlassBorderSoft)
    Text("Exceptions & advanced rules", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)

    TinyField("Always proxy domains / geosite tags", s.routeProxyDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeProxyDomains = it))
    }
    TinyField("Block domains / geosite tags", s.routeBlockDomains, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeBlockDomains = it))
    }
    TinyField("Block IP / CIDR / geoip tags", s.routeBlockIps, Modifier.fillMaxWidth()) {
        repo.updateSettings(repo.settings.copy(routeBlockIps = it))
    }

    AnimatedVisibility(s.routingMode == RoutingMode.CUSTOM) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TinyField("Custom direct domains / geosite tags", s.routeDirectDomains, Modifier.fillMaxWidth()) {
                repo.updateSettings(repo.settings.copy(routeDirectDomains = it))
            }
            TinyField("Custom direct IP / CIDR / geoip tags", s.routeDirectIps, Modifier.fillMaxWidth()) {
                repo.updateSettings(repo.settings.copy(routeDirectIps = it))
            }
        }
    }

    Text(
        "Privacy note: Iran-direct is intentional bypass, not a leak. Iranian destinations see your ISP egress IP; other destinations remain on the selected proxy. Identity Guard pins the proxied exit and strips arbitrary public direct rules.",
        color = Aether.Amber,
        style = MaterialTheme.typography.bodySmall
    )
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
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = .11f),
            contentColor = Aether.Ink,
            disabledContainerColor = Aether.GlassStrong.copy(alpha = .62f),
            disabledContentColor = Aether.InkFaint
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CyberChoiceChip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = .12f) else Aether.GlassStrong.copy(alpha = .55f),
        animationSpec = tween(180),
        label = "chip-background-$text"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) color else Aether.InkMuted,
        animationSpec = tween(180),
        label = "chip-content-$text"
    )

    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
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
    val background by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = .10f) else Aether.GlassStrong.copy(alpha = .54f),
        animationSpec = tween(180),
        label = "segment-$label"
    )
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            label,
            color = if (selected) color else Aether.Ink,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
        Text(
            detail,
            color = Aether.InkFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
private fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier
) {
    val target = score.coerceIn(0, 100)
    val progress by animateFloatAsState(
        targetValue = target / 100f,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "score-ring-progress"
    )
    val track = Aether.GlassBorderSoft
    val ring = when {
        target >= 80 -> Aether.Emerald
        target >= 55 -> Aether.Cyan
        target >= 35 -> Aether.Amber
        else -> Aether.Danger
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.minDimension * .072f
            drawArc(track, -90f, 360f, false, style = Stroke(width, cap = StrokeCap.Round))
            if (progress > 0f) {
                drawArc(
                    ring,
                    -90f,
                    360f * progress,
                    false,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                target.toString(),
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text("/ 100", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
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
    val cyanRaw = Color(0xFF365FD9)
    val purpleRaw = Color(0xFF6078BE)
    val mintRaw = Color(0xFF7FA68B)
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
    val cyanRaw = Color(0xFF365FD9)
    val amberRaw = Color(0xFFC3A36B)
    val purpleRaw = Color(0xFF6078BE)
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


// =================================================================================================
// IRAN MODE
// =================================================================================================

@Composable
private fun IranModePanel(repo: AppRepository) {
    val state = repo.iranMode
    // Main-screen rule: the Iran Mode card exists only while the engine is actually active.
    if (!state.active) return

    SectionLabel("Iran Mode", "Active physical-network protection")
    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        glow = Aether.Emerald,
        glowStrength = .34f,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("IRAN MODE ON", color = Aether.Emerald, style = MaterialTheme.typography.labelSmall)
                Text(
                    state.ispLine,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HoloBadge("${state.confidence}%", Aether.Emerald, compact = true)
        }
        Text(state.summary, color = Aether.InkMuted, style = MaterialTheme.typography.bodySmall)
        if (state.techniques.isNotEmpty()) {
            Text(
                state.techniques.joinToString(" • ") { it.label },
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun IranModeSettings(repo: AppRepository) {
    val settings = repo.settings
    val state = repo.iranMode

    Text(
        "Iran Mode detects Iranian ISPs from the carrier code, uplink ASN/geolocation, national " +
            "block-page DNS injection and Iran-only resolvers, then applies countermeasures matched " +
            "to the filtering that is actually observed on the link.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )

    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CyberSegment(
            label = "Auto",
            detail = "Detect ISP",
            selected = settings.iranModePolicy == IranModePolicy.AUTO,
            color = Aether.Emerald,
            modifier = Modifier.weight(1f)
        ) { repo.setIranModePolicy(IranModePolicy.AUTO) }
        CyberSegment(
            label = "Always",
            detail = "Force on",
            selected = settings.iranModePolicy == IranModePolicy.ALWAYS_ON,
            color = Aether.Amber,
            modifier = Modifier.weight(1f)
        ) { repo.setIranModePolicy(IranModePolicy.ALWAYS_ON) }
        CyberSegment(
            label = "Off",
            detail = "Disable",
            selected = settings.iranModePolicy == IranModePolicy.OFF,
            color = Aether.InkMuted,
            modifier = Modifier.weight(1f)
        ) { repo.setIranModePolicy(IranModePolicy.OFF) }
    }

    SettingSwitch(
        "Apply countermeasures",
        "Fragmentation profile, resolver order, MTU ceiling and failover posture",
        settings.iranModeCountermeasures
    ) { repo.updateSettings(settings.copy(iranModeCountermeasures = it)) }

    SettingSwitch(
        "Domestic traffic direct",
        "Route geoip:ir directly so Iranian services stay fast and tunnel volume stays low",
        settings.iranDomesticDirect
    ) { repo.updateSettings(settings.copy(iranDomesticDirect = it)) }

    SettingSwitch(
        "Fingerprint the filtering",
        "Probe for DNS injection, SNI resets, port allowlists and UDP blocking",
        settings.iranDeepProbeEnabled
    ) { repo.updateSettings(settings.copy(iranDeepProbeEnabled = it)) }

    SettingSwitch(
        "Notify when Iran Mode engages",
        "Post an alert naming the detected ISP",
        settings.iranModeNotify
    ) { repo.updateSettings(settings.copy(iranModeNotify = it)) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HoloBadge(
            if (state.active) "ENGINE ON" else "ENGINE IDLE",
            if (state.active) Aether.Emerald else Aether.InkFaint,
            compact = true
        )
        if (state.active) {
            HoloBadge(state.ispLine, Aether.Cyan, compact = true)
        }
    }

    Text(
        state.summary,
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )

    CyberButton(
        label = "Re-scan now",
        color = Aether.Cyan,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.scanning
    ) { repo.scanIranMode(force = true, deep = true) }
}

private fun iranKindLabel(state: IranModeState): String = when (state.isp?.kind) {
    IranIspKind.MOBILE -> "MOBILE CARRIER"
    IranIspKind.FIXED -> "FIXED LINE"
    IranIspKind.INFRASTRUCTURE -> "NATIONAL BACKBONE"
    IranIspKind.HOSTING -> "DOMESTIC HOSTING"
    null -> "UNCLASSIFIED"
}

private fun iranSeverityLabel(severity: FilterSeverity): String = when (severity) {
    FilterSeverity.LIGHT -> "LIGHT"
    FilterSeverity.MODERATE -> "MODERATE"
    FilterSeverity.HEAVY -> "HEAVY"
    FilterSeverity.EXTREME -> "EXTREME"
}

@Composable
private fun iranSeverityColor(severity: FilterSeverity): Color = when (severity) {
    FilterSeverity.LIGHT -> Aether.Emerald
    FilterSeverity.MODERATE -> Aether.Cyan
    FilterSeverity.HEAVY -> Aether.Amber
    FilterSeverity.EXTREME -> Aether.Danger
}

@Composable
private fun iranTechniqueColor(technique: CensorTechnique): Color = when (technique) {
    CensorTechnique.NATIONAL_INTRANET -> Aether.Danger
    CensorTechnique.PROTOCOL_ALLOWLIST -> Aether.Danger
    CensorTechnique.DNS_POISONING -> Aether.Amber
    CensorTechnique.SNI_FILTERING -> Aether.Amber
    CensorTechnique.TCP_RESET -> Aether.Amber
    CensorTechnique.UDP_BLOCKED -> Aether.Amethyst
    CensorTechnique.THROTTLING -> Aether.Cyan
}
