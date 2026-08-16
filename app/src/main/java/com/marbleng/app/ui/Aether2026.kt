package com.marbleng.app.ui

// Marble Product UI v10.0.1 • stability-first Library power surface
// MARBLE_LIBRARY_UI_V10
// MARBLE_BUG_FINDER_UI_V11

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.BugSeverity
import com.marbleng.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class SpatialTab(val label: String) {
    DECK("Home"),
    LIBRARY("Library"),
    RADAR("Network"),
    SETTINGS("Settings")
}

private data class InstalledApp(val label: String, val packageName: String)


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
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            ) { width -> width / 10 } +
                                fadeIn(animationSpec = tween(110))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(150, easing = FastOutSlowInEasing)
                            ) { width -> -width / 12 } +
                                fadeOut(animationSpec = tween(90))
                        )
                    } else {
                        (
                            slideInHorizontally(
                                animationSpec = tween(180, easing = FastOutSlowInEasing)
                            ) { width -> -width / 10 } +
                                fadeIn(animationSpec = tween(110))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(150, easing = FastOutSlowInEasing)
                            ) { width -> width / 12 } +
                                fadeOut(animationSpec = tween(90))
                        )
                    }
                },
                label = "marble-page-transition-fast"
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
                    SpatialTab.RADAR -> DiscoveryRadar(repo)
                    SpatialTab.SETTINGS -> SpatialSettings(
                        repo = repo,
                        onDialog = { dialog = it },
                        focusSection = settingsFocus
                    )
                }
            }

            // The top bar is the fallback for work that has no card of its own (audits, geo assets,
            // routing verification). Tests and refreshes report on their own node/source cards.
            AnimatedVisibility(
                visible = repo.busy && !repo.inlineProgressActive,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(90)),
                exit = fadeOut(tween(120))
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
        /*
         * Logs, doctor and core-lock read files. Doing that inline in composition ran disk I/O on
         * the main thread on every recomposition of the dialog; it is loaded once, off the main
         * thread, and the live Privacy report stays reactive.
         */
        val dialogBody by produceState(initialValue = "", key1 = what) {
            value = if (what == "Privacy") {
                ""
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        when (what) {
                            "Logs" -> repo.readLogs()
                            "System Doctor" -> repo.doctor()
                            "History" -> repo.history.takeLast(80).asReversed().joinToString("\n") {
                                "${DateFormat.getDateTimeInstance().format(Date(it.at))} • ${it.name} • ${it.reason}"
                            }
                            else -> "MarbleNG"
                        }
                    }.getOrElse { "Could not read $what • ${it::class.java.simpleName}" }
                        .ifBlank { "No data yet" }
                }
            }
        }

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
                            else -> dialogBody.ifBlank { "Loading $what…" }
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
                .clip(dockShape)
                .background(Aether.VoidElevated)
                .border(1.dp, Aether.GlassBorderSoft, dockShape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpatialTab.entries.forEach { item ->
                val active = item == selected
                val background by animateColorAsState(
                    targetValue = if (active) Aether.Cyan.copy(alpha = .11f) else Color.Transparent,
                    animationSpec = tween(120),
                    label = "nav-bg-${item.name}"
                )
                val iconColor by animateColorAsState(
                    targetValue = if (active) Aether.Cyan else Aether.InkFaint,
                    animationSpec = tween(120),
                    label = "nav-icon-${item.name}"
                )
                val textColor by animateColorAsState(
                    targetValue = if (active) Aether.Ink else Aether.InkMuted,
                    animationSpec = tween(120),
                    label = "nav-text-${item.name}"
                )
                val iconSize by animateDpAsState(
                    targetValue = if (active) 22.dp else 20.dp,
                    animationSpec = tween(120),
                    label = "nav-size-${item.name}"
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
                    MarbleTabIcon(
                        tab = item,
                        color = iconColor,
                        active = active,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(Modifier.height(3.dp))
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
private fun MarbleTabIcon(
    tab: SpatialTab,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = if (active) 2.35f else 1.9f
        val line = Stroke(width = stroke, cap = StrokeCap.Round)

        when (tab) {
            SpatialTab.DECK -> {
                val roof = Path().apply {
                    moveTo(w * .18f, h * .47f)
                    lineTo(w * .50f, h * .20f)
                    lineTo(w * .82f, h * .47f)
                }
                drawPath(roof, color, style = line)
                drawLine(color, Offset(w*.27f,h*.43f), Offset(w*.27f,h*.80f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.73f,h*.43f), Offset(w*.73f,h*.80f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.27f,h*.80f), Offset(w*.73f,h*.80f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.47f,h*.80f), Offset(w*.47f,h*.60f), stroke, StrokeCap.Round)
            }
            SpatialTab.LIBRARY -> {
                val positions = listOf(
                    Offset(w*.31f,h*.31f), Offset(w*.69f,h*.31f),
                    Offset(w*.31f,h*.69f), Offset(w*.69f,h*.69f)
                )
                positions.forEach { p ->
                    drawCircle(
                        color = color,
                        radius = w * .115f,
                        center = p,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            SpatialTab.RADAR -> {
                val left = Offset(w*.23f,h*.62f)
                val top = Offset(w*.50f,h*.27f)
                val right = Offset(w*.77f,h*.62f)
                drawLine(color,left,top,stroke,StrokeCap.Round)
                drawLine(color,top,right,stroke,StrokeCap.Round)
                drawLine(color,left,right,stroke,StrokeCap.Round)
                listOf(left,top,right).forEach { p ->
                    drawCircle(color,w*.075f,p)
                    drawCircle(color.copy(alpha=.18f),w*.14f,p)
                }
            }
            SpatialTab.SETTINGS -> {
                val center = Offset(w*.50f,h*.50f)
                drawCircle(color,w*.25f,center,style=line)
                drawCircle(color,w*.075f,center,style=line)
                for (i in 0 until 8) {
                    val angle = i * PI.toFloat() / 4f
                    val from = Offset(
                        center.x + cos(angle) * w*.31f,
                        center.y + sin(angle) * h*.31f
                    )
                    val to = Offset(
                        center.x + cos(angle) * w*.40f,
                        center.y + sin(angle) * h*.40f
                    )
                    drawLine(color,from,to,stroke,StrokeCap.Round)
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
            .padding(vertical = 10.dp),
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
    borderColor: Color = Aether.GlassBorderSoft,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val borderTint by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(160),
        label = "surface-border"
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, borderTint, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

/**
 * Progress that belongs to one card.
 *
 * fraction == null runs an indeterminate sweep (this node is being probed right now); a value
 * renders a determinate fill. This replaces the single anonymous bar that used to sit at the top
 * of the screen for every background task.
 */
@Composable
private fun LiveProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
    color: Color = Aether.Cyan
) {
    val track = Aether.GlassBorderSoft
    val sweep = rememberInfiniteTransition(label = "live-progress")
    val head by sweep.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "live-progress-head"
    )
    val settled by animateFloatAsState(
        targetValue = fraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "live-progress-fill"
    )

    Canvas(modifier.fillMaxWidth().height(4.dp)) {
        val y = size.height / 2f
        drawLine(
            color = track,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
        if (fraction == null) {
            val segment = size.width * .4f
            val start = (size.width * head).coerceAtLeast(0f)
            val end = (size.width * head + segment).coerceAtMost(size.width)
            if (end > start) {
                drawLine(
                    color = color,
                    start = Offset(start, y),
                    end = Offset(end, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
        } else if (settled > 0f) {
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width * settled, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }
    }
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
    Column(Modifier.padding(vertical=2.dp)){Text(title,color=Aether.Ink,style=MaterialTheme.typography.titleMedium);if(!subtitle.isNullOrBlank()){Spacer(Modifier.height(2.dp));Text(subtitle,color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)}}
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
    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING"
    val blocked = repo.state == "BLOCKED"
    val activeName = repo.stateDetail.ifBlank {
        repo.lastProfile()?.name ?: "No active connection"
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SpatialHeader(
                "Privacy dashboard",
                "MarbleNG",
                "Private networking, quietly monitored",
                when {
                    connected -> "Protected"
                    connecting -> "Connecting"
                    blocked -> "Blocked"
                    else -> "Idle"
                },
                when {
                    connected -> Aether.Emerald
                    connecting -> Aether.Cyan
                    blocked -> Aether.Danger
                    else -> Aether.InkFaint
                }
            )
        }

        item {
            ConnectionCore(
                activeName,
                connected,
                connecting,
                blocked,
                repo.settings.connectionMode,
                repo.settings.localProxyPort,
                repo.livePingMs,
                repo.liveDownBps,
                repo.liveUpBps
            ) {
                if (connected || connecting) repo.stopVpn() else repo.auto(onConnect)
            }
        }

        item {
            SectionLabel("Shortcuts")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoloActionPill(
                    "◔",
                    "Test nodes",
                    if (repo.probeActive) "Testing ${repo.probeDone}/${repo.probeTotal}" else "Measure real route latency",
                    Aether.Cyan,
                    Modifier.weight(1f)
                ) {
                    repo.smartRank()
                }
                HoloActionPill("▦","Library","${repo.profiles.size} connections",Aether.Cyan,Modifier.weight(1f)) {
                    onLibrary()
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HoloActionPill(
                    "◇",
                    "Privacy audit",
                    if (connected) "Check egress & DNS" else "Connect first",
                    Aether.Emerald,
                    Modifier.weight(1f)
                ) { onPrivacy() }
                HoloActionPill("⚙","Routing","Open Expert settings",Aether.InkMuted,Modifier.weight(1f)) {
                    onRouting()
                }
            }
        }

    }
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
    // Breathing only while the tunnel is up; an idle screen should not animate forever.
    val breath: Float = if (connected) {
        val transition = rememberInfiniteTransition(label = "connection-breath")
        val animated by transition.animateFloat(
            initialValue = .975f,
            targetValue = 1.025f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "connection-breath-value"
        )
        animated
    } else {
        1f
    }
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
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, Aether.GlassBorderSoft, shape)
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
    val clipboard = LocalClipboardManager.current
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
                        CyberButton("Copy URL", Aether.Emerald, Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(target.url))
                            repo.setRuntimeMessage("Subscription URL copied")
                        }
                        CyberButton(
                            "Copy nodes",
                            Aether.Cyan,
                            Modifier.weight(1f),
                            enabled = repo.subscriptionNodeCount(target.id) > 0
                        ) {
                            clipboard.setText(AnnotatedString(repo.subscriptionRawText(target.id)))
                            repo.setRuntimeMessage("${repo.subscriptionNodeCount(target.id)} node links copied")
                        }
                    }
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
        // One gutter for the whole screen: titles, controls and cards share the same edge.
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SpatialHeader(
                eyebrow = "Connections",
                title = "Library",
                subtitle = "Your sources and nodes, with measured route health",
                status = "${repo.profiles.size} nodes",
                statusColor = Aether.Amethyst
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search connections") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                )

                Button(
                    onClick = {
                        repo.importClipboard(clipboard.getText()?.text.orEmpty())
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Aether.Emerald.copy(alpha = .13f),
                        contentColor = Aether.Emerald
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 11.dp)
                ) {
                    Text("✦ Magic", maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = { addOpen = !addOpen },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Aether.Cyan.copy(alpha = .12f),
                        contentColor = Aether.Ink
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        if (addOpen) "Close" else "Add",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (addOpen) {
            item {
                HoloGlass(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = Aether.Amethyst.copy(alpha = .45f)
                ) {
                    Text(
                        "Add a source",
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleMedium
                    )
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
                    "Sources",
                    "View, refresh, edit or delete each subscription"
                )
            }

            items(repo.subscriptions, key = { "subscription-${it.id}" }) { sub ->
                SubscriptionManagerCard(
                    sub = sub,
                    repo = repo,
                    selected = sourceFilter == sub.id,
                    refreshing = sub.id in repo.refreshingSources,
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CyberButton(
                    label = if (repo.refreshingSources.isNotEmpty()) {
                        "Refreshing ${repo.refreshingSources.size}…"
                    } else {
                        "Refresh all"
                    },
                    color = Aether.Amethyst,
                    modifier = Modifier.weight(1f),
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy
                ) { repo.refreshAll() }

                CyberButton(
                    label = if (repo.probeActive) {
                        "Testing ${repo.probeDone}/${repo.probeTotal}"
                    } else {
                        "Test all"
                    },
                    color = Aether.Cyan,
                    modifier = Modifier.weight(1f),
                    enabled = repo.profiles.isNotEmpty() && !repo.busy
                ) { repo.testAll() }
            }
        }

        item {
            SectionLabel(
                "Nodes",
                if (repo.profiles.isEmpty()) {
                    "Add a subscription or import a file to get started"
                } else {
                    "${visible.size} shown • swipe right to test, left to rename"
                }
            )
        }

        item {
            LibraryViewControls(
                repo = repo,
                sourceFilter = sourceFilter,
                onSourceFilter = { sourceFilter = it }
            )
        }

        if (visible.isEmpty()) {
            item {
                HoloGlass(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (repo.profiles.isEmpty()) "No connections yet" else "Nothing matches",
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (repo.profiles.isEmpty()) {
                            "Add a subscription URL above, or import a config file."
                        } else {
                            "Clear the search box or pick another source."
                        },
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Aggregator subscriptions frequently serve the same node, and the profile id is a hash of
        // the share link, so the source has to be part of the key or LazyColumn rejects it.
        items(visible, key = { "${it.subscriptionId}:${it.id}" }) { profile ->
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
                    val accent = if (editSide) Aether.Amethyst else Aether.Cyan
                    val shape = RoundedCornerShape(22.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .background(accent.copy(alpha = .12f))
                            .border(1.dp, accent.copy(alpha = .30f), shape)
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (editSide) Arrangement.End else Arrangement.Start
                    ) {
                        Text(
                            if (editSide) "Rename" else "Test",
                            color = if (editSide) Aether.AmethystBright else Aether.CyanBright,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            ) {
                SpatialServerCard(
                    profile = profile,
                    repo = repo,
                    result = benchmarkById[profile.id],
                    active = repo.isActiveProfile(profile.id),
                    probeState = repo.probeStateOf(profile.id),
                    onConnect = onConnect,
                    onEdit = {
                        renameTarget = profile
                        renameText = profile.name
                    }
                )
            }
        }
    }
}

/** Source filter and sort order, grouped on one surface instead of two loose label blocks. */
@Composable
private fun LibraryViewControls(
    repo: AppRepository,
    sourceFilter: String,
    onSourceFilter: (String) -> Unit
) {
    val manualCount = repo.profiles.count { it.subscriptionId == "manual" }
    val sourceLabel = when (sourceFilter) {
        "all" -> "All sources"
        "manual" -> "Manual"
        else -> repo.subscriptions.firstOrNull { it.id == sourceFilter }?.name ?: "All sources"
    }

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Source",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            HoloBadge(sourceLabel.take(18), Aether.Emerald, compact = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            CyberChoiceChip("All ${repo.profiles.size}", sourceFilter == "all", Aether.Cyan) {
                onSourceFilter("all")
            }
            if (manualCount > 0) {
                CyberChoiceChip("Manual $manualCount", sourceFilter == "manual", Aether.Amber) {
                    onSourceFilter("manual")
                }
            }
            repo.subscriptions.forEach { sub ->
                CyberChoiceChip(
                    "${sub.name} ${repo.subscriptionNodeCount(sub.id)}",
                    sourceFilter == sub.id,
                    Aether.Amethyst
                ) { onSourceFilter(sub.id) }
            }
        }

        HorizontalDivider(color = Aether.GlassBorderSoft)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Sort",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (repo.settings.nodeSortReverse) "Reversed" else "Best first",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            NodeSortMode.entries.filter { it != NodeSortMode.SCORE }.forEach { mode ->
                CyberChoiceChip(
                    text = sortModeLabel(mode),
                    selected = repo.settings.nodeSortMode == mode,
                    color = if (mode == NodeSortMode.PING) Aether.Cyan else Aether.Amethyst
                ) {
                    repo.updateSettings(
                        repo.settings.copy(nodeSortMode = mode, nodeSortReverse = false)
                    )
                }
            }
            CyberChoiceChip(
                text = if (repo.settings.nodeSortReverse) "Order ↑" else "Order ↓",
                selected = repo.settings.nodeSortReverse,
                color = Aether.Amber
            ) {
                repo.updateSettings(
                    repo.settings.copy(nodeSortReverse = !repo.settings.nodeSortReverse)
                )
            }
        }
    }
}

private fun sortModeLabel(mode: NodeSortMode): String = when (mode) {
    NodeSortMode.PING -> "Ping"
    NodeSortMode.SCORE -> "Score"
    NodeSortMode.NAME -> "Name"
    NodeSortMode.PROTOCOL -> "Protocol"
    NodeSortMode.SOURCE -> "Source"
}

@Composable
private fun SubscriptionManagerCard(
    sub: Subscription,
    repo: AppRepository,
    selected: Boolean,
    refreshing: Boolean,
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
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "subscription-quota-${sub.id}"
    )
    val expired = sub.expireAt > 0L && sub.expireAt < System.currentTimeMillis()
    val color = when {
        expired -> Aether.Danger
        selected -> Aether.Cyan
        else -> Aether.InkMuted
    }

    HoloGlass(
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(180)),
        borderColor = when {
            refreshing -> Aether.Amethyst.copy(alpha = .55f)
            selected -> Aether.Cyan.copy(alpha = .50f)
            else -> Aether.GlassBorderSoft
        },
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(color.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    sub.name.trim().firstOrNull()?.uppercase() ?: "S",
                    color = color,
                    style = MaterialTheme.typography.titleMedium
                )
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
            if (refreshing) {
                HoloBadge("Refreshing", Aether.Amethyst, compact = true)
            } else if (selected) {
                HoloBadge("Selected", Aether.Cyan, compact = true)
            }
        }

        if (refreshing) {
            LiveProgressBar(
                fraction = null,
                modifier = Modifier.fillMaxWidth(),
                color = Aether.Amethyst
            )
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
                "No quota reported by this provider",
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
            CyberButton(
                label = if (selected) "Viewing" else "View nodes",
                color = Aether.Cyan,
                modifier = Modifier.weight(1f),
                enabled = !selected
            ) { onView() }
            CyberButton(
                label = if (refreshing) "Refreshing…" else "Refresh",
                color = Aether.Emerald,
                modifier = Modifier.weight(1f),
                enabled = !repo.busy
            ) { repo.refresh(sub.id) }
            CyberButton("Manage", Aether.InkMuted, Modifier.weight(1f), enabled = !repo.busy) { onManage() }
        }
    }
}

@Composable
private fun SpatialServerCard(
    profile: ProxyProfile,
    repo: AppRepository,
    result: BenchmarkResult?,
    active: Boolean,
    probeState: ProbeState,
    onConnect: (ProxyProfile) -> Unit,
    onEdit: () -> Unit
) {
    val measured = result?.takeIf { it.success > 0 }
    val latency = measured?.latencyMs?.toInt() ?: 0
    val health = healthColor(latency, result?.success ?: 0)
    val testing = probeState == ProbeState.TESTING
    val queued = probeState == ProbeState.QUEUED
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var jsonOpen by remember(profile.id) { mutableStateOf(false) }
    var jsonText by remember(profile.id, profile.configJson) { mutableStateOf(profile.configJson) }

    if (jsonOpen) {
        AlertDialog(
            onDismissRequest = { jsonOpen = false },
            containerColor = Aether.VoidElevated,
            title = {
                Column {
                    Text("Edit Xray JSON", color = Aether.Ink)
                    Text(profile.name, color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 430.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        label = { Text("Effective config JSON") },
                        minLines = 10,
                        maxLines = 22
                    )
                    Text(
                        if (profile.subscriptionId == "manual")
                            "Manual node • edits are stored locally."
                        else
                            "Subscription node • refresh can replace this edit. Duplicate to Manual for a permanent fork.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (repo.updateProfileJson(profile.id, jsonText)) jsonOpen = false
                }) { Text("SAVE JSON", color = Aether.Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { jsonOpen = false }) { Text("CANCEL", color = Aether.InkMuted) }
            }
        )
    }

    HoloGlass(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(180)),
        borderColor = when {
            testing -> Aether.Cyan.copy(alpha = .55f)
            active -> Aether.Emerald.copy(alpha = .55f)
            else -> Aether.GlassBorderSoft
        },
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthOrb(
                label = countryGlyph(profile.host),
                color = when {
                    testing -> Aether.Cyan
                    active -> Aether.Emerald
                    else -> health
                },
                active = active,
                pulsing = active || testing,
                modifier = Modifier.size(42.dp)
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
                Spacer(Modifier.height(2.dp))
                val connectionLine = listOfNotNull(
                    profile.scheme.uppercase(),
                    profile.transport.uppercase().takeIf { it.isNotBlank() && it != "NATIVE" },
                    profile.security.uppercase().takeIf {
                        it.isNotBlank() && !it.equals("NONE", true)
                    }
                ).joinToString(" • ")
                Text(
                    connectionLine,
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (latency > 0) {
                Column(
                    modifier = Modifier.widthIn(min = 42.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "$latency",
                        color = health,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                    Text("ms", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background((if (active) Aether.Emerald else Aether.Cyan).copy(alpha = .12f))
                    .clickable { if (active) repo.stopVpn() else onConnect(profile) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (active) "■" else "▶",
                    color = if (active) Aether.Emerald else Aether.Cyan,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋮", color = Aether.InkMuted, style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Aether.VoidElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy config link") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(profile.raw.trim().ifBlank { profile.configJson }))
                            repo.setRuntimeMessage("Config copied")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Xray JSON") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(profile.configJson))
                            repo.setRuntimeMessage("Xray JSON copied")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Xray JSON") },
                        onClick = {
                            menuOpen = false
                            jsonText = profile.configJson
                            jsonOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate to Manual") },
                        onClick = {
                            menuOpen = false
                            repo.duplicateProfile(profile.id)
                        }
                    )
                    HorizontalDivider(color = Aether.GlassBorderSoft)
                    DropdownMenuItem(
                        text = { Text("Test this node") },
                        onClick = {
                            menuOpen = false
                            repo.fullTest(profile)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
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

        if (testing || queued) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (testing) "Testing…" else "Queued",
                    color = if (testing) Aether.Cyan else Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false
                )
                LiveProgressBar(
                    fraction = if (testing) null else 0f,
                    modifier = Modifier.weight(1f),
                    color = Aether.Cyan
                )
            }
        }

        if (active || measured != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active) HoloBadge("Active", Aether.Emerald, compact = true)
                measured?.let { evidence ->
                    HoloBadge(
                        "${evidence.success}% ok",
                        if (evidence.success >= 90) Aether.Emerald else Aether.Amber,
                        compact = true
                    )
                    Text(
                        "${evidence.success}% reachable • measured through Xray",
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
    modifier: Modifier = Modifier,
    pulsing: Boolean = active
) {
    // Only the node that is connected or currently being probed animates. A permanent infinite
    // transition per row made every idle library card pay for a frame callback.
    val pulse: Float = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "health-orb")
        val animated by transition.animateFloat(
            initialValue = .82f,
            targetValue = 1.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (active) 1400 else 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "health-pulse"
        )
        animated
    } else {
        1f
    }

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
// NETWORK / ENVIRONMENT
// =================================================================================================

@Composable
private fun DiscoveryRadar(repo:AppRepository){
    val network=repo.networkSnapshot;val intel=repo.intelligenceStatus;val iran=repo.iranMode;val linkColor=if(network.validated)Aether.Emerald else Aether.Amber
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{SpatialHeader("Network","Environment","Physical-link context and Marble Intelligence",if(network.validated)"Healthy" else "Checking",linkColor)}
        item{SectionLabel("Current network");HoloGlass(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(network.label,color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Text("${if(network.metered)"Metered" else "Unmetered"} • ${if(network.hasIpv4)"IPv4" else "No IPv4"} • ${if(network.hasIpv6)"IPv6" else "No IPv6"}",color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)};HoloBadge(if(network.validated)"Validated" else "Unvalidated",linkColor,true)};Text("MTU ${network.mtu.takeIf{it>0}?:0} • Downlink ${network.downstreamKbps.coerceAtLeast(0)} kbps • Uplink ${network.upstreamKbps.coerceAtLeast(0)} kbps",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall);CyberButton(if(iran.scanning)"Checking network…" else "Refresh network check",Aether.Cyan,Modifier.fillMaxWidth(),!iran.scanning){repo.scanIranMode(force=true,deep=true)}}}
        item{SectionLabel("Marble Intelligence");HoloGlass(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(repo.settings.intelligenceEnabled)"Adaptive engine enabled" else "Adaptive engine disabled",color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Text("Effective MTU ${intel.effectiveMtu.takeIf{it>0}?:network.mtu} • Thermal budget ${intel.thermalBudgetPercent}% • History ${intel.historyRecords}",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)};HoloBadge(if(repo.settings.intelligenceEnabled)"On" else "Off",if(repo.settings.intelligenceEnabled)Aether.Emerald else Aether.InkFaint,true)};Text(intel.lastDecision.ifBlank{"Waiting for the next network decision"},color=Aether.InkMuted,style=MaterialTheme.typography.bodyMedium)}}
        if(iran.active){item{SectionLabel("Regional protection");HoloGlass(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(iran.ispLine,color=Aether.Ink,style=MaterialTheme.typography.titleMedium);Text(iran.summary,color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)};HoloBadge("${iran.confidence}%",Aether.Emerald,true)}}}}
        if(repo.benchmarks.isNotEmpty()){item{SectionLabel("Recent measurements","Read-only route evidence")};items(repo.benchmarks.sortedByDescending{it.score}.take(6),key={"network-memory-${it.profileId}"}){r->val c=healthColor(r.latencyMs.toInt(),r.success);HoloGlass(Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=14.dp,vertical=12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r.name,color=Aether.Ink,style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${r.latencyMs.toInt()} ms • success ${r.success}%",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)};HoloBadge("${r.latencyMs.toInt()} ms",c,true)}}}}
    }
}


// =================================================================================================
// SETTINGS / ACCORDIONS
// =================================================================================================

/**
 * Position of the Routing accordion inside SpatialSettings while Expert controls are on:
 * header, appearance, connection, testing, split tunneling, notifications, subscriptions,
 * regional, intelligence, DNS, routing.
 */
private const val ROUTING_SECTION_ITEM_INDEX = 10

@Composable
private fun SpatialSettings(
    repo: AppRepository,
    onDialog: (String) -> Unit,
    focusSection: String? = null
) {
    val listState = rememberLazyListState()
    // Expert mode is a persisted preference: leaving the tab must not silently hide the controls.
    val expertMode = repo.settings.expertMode

    LaunchedEffect(focusSection) {
        if (focusSection == "Routing") {
            if (!repo.settings.expertMode) {
                repo.updateSettings(repo.settings.copy(expertMode = true))
            }
            delay(80)
            listState.animateScrollToItem(ROUTING_SECTION_ITEM_INDEX)
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
                ) { repo.updateSettings(repo.settings.copy(expertMode = it)) }
            }
        }

        item { SpatialAccordion("Connection","Full-device tunnel or local SOCKS proxy","Core",Aether.Cyan,true){ConnectionSettings(repo)} }
        item { SpatialAccordion("Testing & ping","How MarbleNG measures nodes: tunnel, TCP or ICMP","Tests",Aether.Amethyst){ProbeSettings(repo)} }
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

        item { SpatialAccordion("Bug Finder","Deep Xray + SOCKS + TUN + HEV diagnostics","Live scan",Aether.Danger){BugFinderSettings(repo)} }

        item {
            Text(
                if (expertMode)
                    "Expert controls change low-level Xray and tunnel behavior."
                else
                    "Advanced controls stay out of the way until you enable Expert mode.",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 10.dp)
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
            .animateContentSize(tween(160, easing = FastOutSlowInEasing))
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, Aether.GlassBorderSoft, shape)
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
            enter = expandVertically(tween(165, easing = FastOutSlowInEasing)) + fadeIn(tween(100)),
            exit = shrinkVertically(tween(135, easing = FastOutSlowInEasing)) + fadeOut(tween(80))
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
        HoloBadge(
            if (status.acceleratedRoutes > 0) {
                "TURBO ${status.accelerationLabel} • ${status.acceleratedRoutes}"
            } else {
                "TURBO ${status.accelerationLabel}"
            },
            if (s.connectTuningEnabled) Aether.Emerald else Aether.InkFaint,
            compact = true
        )
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
        title = "Marble Turbo acceleration",
        subtitle = "On connect, execute real transport methods on the selected node — fragmentation shapes, Mux reuse, endpoint address family — and keep the method that measures fastest",
        checked = s.connectTuningEnabled
    ) { repo.updateSettings(repo.settings.copy(connectTuningEnabled = it)) }

    AnimatedVisibility(s.connectTuningEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            NumberSetting("Connect tuning budget", s.connectTuningBudgetSec, 0..20, " sec") {
                repo.updateSettings(repo.settings.copy(connectTuningBudgetSec = it))
            }
            NumberSetting("Methods per pass", s.connectTuningMethods, 1..5) {
                repo.updateSettings(repo.settings.copy(connectTuningMethods = it))
            }
            SettingSwitch(
                title = "Keep improving while connected",
                subtitle = "Re-measure in the background and learn a faster method for the next reconnect — never tear down the live tunnel",
                checked = s.liveTuningEnabled
            ) { repo.updateSettings(repo.settings.copy(liveTuningEnabled = it)) }

            AnimatedVisibility(s.liveTuningEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    NumberSetting("Live tuning interval", s.liveTuningIntervalSec, 60..3600, " sec") {
                        repo.updateSettings(repo.settings.copy(liveTuningIntervalSec = it))
                    }
                    NumberSetting("Ping that triggers tuning", s.liveTuningPingTriggerMs, 80..1200, " ms") {
                        repo.updateSettings(repo.settings.copy(liveTuningPingTriggerMs = it))
                    }
                    NumberSetting("Minimum gain to learn", s.liveTuningMinGainPercent, 5..80, " %") {
                        repo.updateSettings(repo.settings.copy(liveTuningMinGainPercent = it))
                    }
                }
            }

            SettingSwitch(
                title = "Adaptive tunnel datapath",
                subtitle = "Size tunnel buffers and session limits from measured throughput instead of one fixed value",
                checked = s.adaptiveBufferEnabled
            ) { repo.updateSettings(repo.settings.copy(adaptiveBufferEnabled = it)) }

            CyberButton(
                label = "Learn faster route now",
                color = Aether.Emerald,
                modifier = Modifier.fillMaxWidth(),
                enabled = repo.state == "CONNECTED"
            ) { repo.boostActiveRoute() }
        }
    }

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
    // Querying every launcher activity and resolving each label is slow on real devices, so the
    // list is built off the main thread instead of blocking the first frame of this section.
    val apps by produceState(initialValue = emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).mapNotNull { r ->
                    val pkg = r.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    InstalledApp(runCatching { r.loadLabel(pm).toString() }.getOrDefault(pkg), pkg)
                }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }
    val selected=remember(repo.settings.splitTunnelPackages){repo.settings.splitTunnelPackages.split(',', '\n','\r',';').map(String::trim).filter(String::isNotBlank).toSet()}
    val visibleApps=remember(apps,search){apps.filter{search.isBlank()||it.label.contains(search,true)||it.packageName.contains(search,true)}}
    fun toggle(pkg:String){val n=selected.toMutableSet();if(!n.add(pkg))n.remove(pkg);repo.updateSettings(repo.settings.copy(splitTunnelPackages=n.sorted().joinToString(",")))}
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){SplitTunnelMode.entries.forEach{m->CyberChoiceChip(when(m){SplitTunnelMode.ALL_APPS->"All apps";SplitTunnelMode.ONLY_SELECTED->"Only selected";SplitTunnelMode.BYPASS_SELECTED->"Bypass selected"},repo.settings.splitTunnelMode==m,Aether.Emerald){repo.updateSettings(repo.settings.copy(splitTunnelMode=m))}}}
    if(repo.settings.splitTunnelMode!=SplitTunnelMode.ALL_APPS){
        TextField(search,{search=it},placeholder={Text("Search installed apps")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Aether.GlassStrong,unfocusedContainerColor=Aether.GlassStrong,disabledContainerColor=Aether.GlassStrong,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(if(apps.isEmpty())"Loading installed apps…" else "${visibleApps.size} apps",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall);HoloBadge("${selected.size} selected",Aether.Emerald,true)}
        LazyColumn(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(18.dp)).background(Aether.Glass.copy(alpha=.70f)),contentPadding=PaddingValues(vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp),userScrollEnabled=true){items(visibleApps,key={it.packageName}){app->SplitTunnelAppRow(app,app.packageName in selected){toggle(app.packageName)}}}
        Text("Changes apply on the next Full TUN connection.",color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SplitTunnelAppRow(app:InstalledApp,checked:Boolean,onToggle:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick=onToggle).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if(checked)Aether.Emerald.copy(alpha=.12f) else Aether.GlassStrong),contentAlignment=Alignment.Center){Text(app.label.trim().firstOrNull()?.uppercase()?:"•",color=if(checked)Aether.Emerald else Aether.InkMuted,style=MaterialTheme.typography.labelLarge)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(app.label,color=Aether.Ink,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(app.packageName,color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Checkbox(checked,{onToggle()},colors=CheckboxDefaults.colors(checkedColor=Aether.Emerald,checkmarkColor=Aether.Void,uncheckedColor=Aether.GlassBorder))}
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
private fun BugFinderSettings(repo: AppRepository) {
    val clipboard = LocalClipboardManager.current
    val report = repo.bugReport

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when {
            report == null -> Aether.GlassBorderSoft
            report.failures > 0 -> Aether.Danger.copy(alpha = .55f)
            report.warnings > 0 -> Aether.Amber.copy(alpha = .50f)
            else -> Aether.Emerald.copy(alpha = .50f)
        },
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live datapath investigator", color=Aether.Ink, style=MaterialTheme.typography.titleMedium)
                Text("Tests Xray, SOCKS HTTPS, HEV lifecycle/counters, routing assets and logs independently.",
                    color=Aether.InkFaint, style=MaterialTheme.typography.bodySmall)
            }
            HoloBadge(
                when {
                    repo.busy -> "Scanning"
                    report == null -> "Ready"
                    report.failures > 0 -> "${report.failures} fail"
                    report.warnings > 0 -> "${report.warnings} warn"
                    else -> "Healthy"
                },
                when {
                    repo.busy -> Aether.Cyan
                    report == null -> Aether.InkMuted
                    report.failures > 0 -> Aether.Danger
                    report.warnings > 0 -> Aether.Amber
                    else -> Aether.Emerald
                }, true
            )
        }

        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CyberButton(if(repo.busy)"SCANNING…" else "RUN DEEP SCAN", Aether.Cyan,
                Modifier.weight(1f), !repo.busy) { repo.runBugFinder() }
            CyberButton("COPY REPORT", Aether.Amethyst, Modifier.weight(1f),
                report != null && !repo.busy) {
                clipboard.setText(AnnotatedString(repo.bugFinderReportText()))
                repo.setRuntimeMessage("Bug Finder report copied")
            }
        }

        report?.let { current ->
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                HoloBadge("${current.passed} pass",Aether.Emerald,true)
                if(current.warnings>0) HoloBadge("${current.warnings} warn",Aether.Amber,true)
                if(current.failures>0) HoloBadge("${current.failures} fail",Aether.Danger,true)
            }
            Text(current.headline,
                color=if(current.failures>0)Aether.Danger else if(current.warnings>0)Aether.Amber else Aether.Emerald,
                style=MaterialTheme.typography.titleMedium)

            current.checks.forEach { check ->
                val c=when(check.severity){
                    BugSeverity.PASS->Aether.Emerald
                    BugSeverity.INFO->Aether.Cyan
                    BugSeverity.WARN->Aether.Amber
                    BugSeverity.FAIL->Aether.Danger
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(c.copy(alpha=.055f))
                    .border(1.dp,c.copy(alpha=.20f),RoundedCornerShape(15.dp))
                    .padding(11.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(check.title,color=Aether.Ink,style=MaterialTheme.typography.labelLarge,
                            modifier=Modifier.weight(1f))
                        HoloBadge(check.severity.name,c,true)
                    }
                    Text(check.detail,color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)
                    if(check.action.isNotBlank())
                        Text("→ ${check.action}",color=c,style=MaterialTheme.typography.bodySmall)
                }
            }

            if(current.evidence.isNotEmpty()) {
                Text("RECENT EVIDENCE",color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall)
                SelectionContainer {
                    Text(current.evidence.joinToString("
"),color=Aether.InkMuted,
                        style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
                }
            }

            if(current.failures>0)
                CyberButton("SAFE RUNTIME RESET",Aether.Danger,Modifier.fillMaxWidth(),!repo.busy) {
                    repo.safeRuntimeResetFromBugFinder()
                }
        } ?: Text("Run this while the problem is happening. Credentials and subscription contents are not included.",
            color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)
    }
}

// =================================================================================================
// TESTING & PING
// =================================================================================================

private fun probeMethodTitle(method: ProbeMethod): String = when (method) {
    ProbeMethod.HYBRID -> "Smart"
    ProbeMethod.TUNNEL -> "Real tunnel"
    ProbeMethod.TCP -> "TCP ping"
    ProbeMethod.ICMP -> "ICMP ping"
}

private fun probeMethodDetail(method: ProbeMethod): String = when (method) {
    ProbeMethod.HYBRID -> "TCP gate, then real test"
    ProbeMethod.TUNNEL -> "Most accurate"
    ProbeMethod.TCP -> "Fastest"
    ProbeMethod.ICMP -> "Classic ping"
}

private fun probeMethodShortLabel(method: ProbeMethod): String = when (method) {
    ProbeMethod.HYBRID -> "Smart"
    ProbeMethod.TUNNEL -> "Tunnel"
    ProbeMethod.TCP -> "TCP"
    ProbeMethod.ICMP -> "ICMP"
}

private fun probeMethodExplainer(method: ProbeMethod): String = when (method) {
    ProbeMethod.HYBRID ->
        "Dead endpoints are dropped with a quick TCP check, then the survivors get a real Xray " +
            "tunnel test. Best balance of speed and truth, and the default."
    ProbeMethod.TUNNEL ->
        "Every node starts a real Xray process and fetches a real HTTPS URL through it. This is " +
            "the only method that proves a node actually works, and the slowest."
    ProbeMethod.TCP ->
        "Measures the TCP handshake to the server address (tcping). Very fast and light, but it " +
            "cannot tell a working proxy from an expired account or a filtered route."
    ProbeMethod.ICMP ->
        "Classic ping through the system. Fast, but many servers and mobile carriers drop ICMP, " +
            "so healthy nodes can appear unreachable."
}

@Composable
private fun ProbeSettings(repo: AppRepository) {
    val s = repo.settings

    Text(
        "Choose how nodes are measured everywhere: Test all, the performance test, a single node " +
            "test and the automatic route picker.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        ProbeMethod.entries.forEach { method ->
            CyberSegment(
                label = probeMethodTitle(method),
                detail = probeMethodDetail(method),
                selected = s.probeMethod == method,
                color = when (method) {
                    ProbeMethod.TUNNEL -> Aether.Emerald
                    ProbeMethod.TCP -> Aether.Cyan
                    ProbeMethod.ICMP -> Aether.Amber
                    ProbeMethod.HYBRID -> Aether.Amethyst
                },
                modifier = Modifier.width(150.dp)
            ) { repo.updateSettings(repo.settings.copy(probeMethod = method)) }
        }
    }

    Text(
        probeMethodExplainer(s.probeMethod),
        color = Aether.InkMuted,
        style = MaterialTheme.typography.bodySmall
    )

    HorizontalDivider(color = Aether.GlassBorderSoft)

    NumberSetting(
        title = "Pings per node",
        value = s.benchSamples,
        range = 1..8
    ) { repo.updateSettings(repo.settings.copy(benchSamples = it)) }

    NumberSetting(
        title = "Timeout per try",
        value = s.benchTimeoutSec,
        range = 2..20,
        suffix = " sec"
    ) { repo.updateSettings(repo.settings.copy(benchTimeoutSec = it)) }

    NumberSetting(
        title = "Nodes per test run",
        value = s.benchCandidates,
        range = 5..200
    ) { repo.updateSettings(repo.settings.copy(benchCandidates = it)) }

    SettingSwitch(
        title = "Also measure download speed",
        subtitle = "Downloads a sample file through each tested node. Much slower and uses data",
        checked = s.probeSpeedTest
    ) { repo.updateSettings(repo.settings.copy(probeSpeedTest = it)) }

    AnimatedVisibility(s.probeSpeedTest) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SettingSwitch(
                title = "Grow the speed sample",
                subtitle = "Download more only from nodes that are already fast",
                checked = s.adaptiveThroughputEnabled
            ) { repo.updateSettings(repo.settings.copy(adaptiveThroughputEnabled = it)) }
        }
    }

    if (s.probeMethod != ProbeMethod.TUNNEL && s.probeMethod != ProbeMethod.HYBRID) {
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
                "Direct probes never open the proxy, so a node can look fast here and still fail " +
                    "to carry traffic. Auto-connect still verifies the route it picks.",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
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
        animationSpec = tween(120),
        label = "chip-background-$text"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) color else Aether.InkMuted,
        animationSpec = tween(120),
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
        animationSpec = tween(120),
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
            .background(if (checked) Aether.Cyan.copy(alpha = .028f) else Color.Transparent)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(12.dp))
        MarbleToggle(checked = checked, onChecked = onChecked)
    }
}

@Composable
private fun MarbleToggle(
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    val track by animateColorAsState(
        targetValue = if (checked) Aether.Cyan else Aether.GlassStrong,
        animationSpec = tween(130),
        label = "marble-toggle-track"
    )
    val border by animateColorAsState(
        targetValue = if (checked) Aether.Cyan else Aether.GlassBorder,
        animationSpec = tween(130),
        label = "marble-toggle-border"
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) Color.White else Aether.InkMuted,
        animationSpec = tween(130),
        label = "marble-toggle-thumb"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "marble-toggle-position"
    )

    Box(
        modifier = Modifier
            .width(50.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, border, CircleShape)
            .clickable { onChecked(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbX, y = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(thumb)
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


/** Score ring. A negative score means "not measured yet" and renders as an empty dash. */


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

/** One line of evidence for a measured route; speed only appears when it was actually measured. */
private fun routeEvidenceLine(result: BenchmarkResult): String = listOfNotNull(
    "${result.latencyMs.toInt()} ms",
    "${result.success}% reachable",
    result.bytesPerSecond.takeIf { it > 0.0 }?.let { rate(it.toLong()) }
).joinToString(" • ")

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


