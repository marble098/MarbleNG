package com.marbleng.app.ui

// Marble Product UI v12 • Solid White command surface
// Compatibility baseline retained for CI: Marble Product UI v9.1.0
// MARBLE_LIBRARY_UI_V10
// MARBLE_BUG_FINDER_UI_V11
// MARBLE_SMART_UI_V14
// MARBLE_ULTIMATE_BUG_FINDER_UI_V15
// MARBLE_HOME_LATENCY_V17
// MARBLE_MANUAL_MOTION_UI_V20
// MARBLE_HOME_COMMAND_CENTER_V22
// MARBLE_LIBRARY_INTELLIGENCE_UI_V24
// MARBLE_LIBRARY_SSH_COMPACT_V25
// MARBLE_SELECTED_SOURCE_UI_V25_4
// MARBLE_AURORA_UI_V26
// MARBLE_HOME_COMMAND_DASHBOARD_V27
// MARBLE_PATTNG_TLS_PARITY_V28
// MARBLE_RUNTIME_POLISH_V29
// MARBLE_INSTANT_QUALITY_V31
// MARBLE_LIBRARY_SCOPE_UI_V32
// MARBLE_LIBRARY_MEMORY_UI_V33
// MARBLE_KINETIC_GLASS_UI_V34
// MARBLE_SOLID_WHITE_UI_V35
// MARBLE_UX_CLEANUP_V37
// MARBLE_SYSTEM_INTEGRITY_UI_V38
// MARBLE_UPDATE_DOCK_UI_V39
// MARBLE_NODE_ENDPOINT_UI_V40
// MARBLE_RANK_RECOVERY_CARD_UX_V43

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.BugSeverity
import com.marbleng.app.core.IranModeState
import com.marbleng.app.core.ManualConfigDraft
import com.marbleng.app.core.ManualProtocol
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
    var detailProfile by remember { mutableStateOf<ProxyProfile?>(null) }
    BackHandler(enabled = detailProfile != null) { detailProfile = null }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(repo.message, repo.busy) {
        if (!repo.busy && repo.message.isNotBlank()) {
            snackbar.showSnackbar(repo.message, duration = SnackbarDuration.Short)
            repo.clearMessage()
        }
    }

    Scaffold(
        containerColor = Aether.Void,
        bottomBar = {
            FloatingSpatialDock(
                selected = tab,
                onSelect = { next ->
                    detailProfile = null
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
                    val enterOffset: (Int) -> Int = { width -> if (forward) width / 9 else -width / 9 }
                    val exitOffset: (Int) -> Int = { width -> if (forward) -width / 13 else width / 13 }
                    (
                        slideInHorizontally(
                            animationSpec = MarbleMotionSpecs.Spatial,
                            initialOffsetX = enterOffset
                        ) +
                            fadeIn(animationSpec = MarbleMotionSpecs.ResponseFloat) +
                            scaleIn(initialScale = .985f, animationSpec = MarbleMotionSpecs.ResponseFloat)
                    ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = MarbleMotionSpecs.SpatialExit,
                            targetOffsetX = exitOffset
                        ) +
                            fadeOut(animationSpec = MarbleMotionSpecs.ExitFloat) +
                            scaleOut(targetScale = .992f, animationSpec = MarbleMotionSpecs.ExitFloat)
                    )
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
                        },
                        onDetails = {
                            val profile = repo.profile(repo.activeProfileId, repo.activeProfileSourceId) ?: repo.lastProfile()
                            if (profile != null) detailProfile = profile else tab = SpatialTab.LIBRARY
                        }
                    )
                    SpatialTab.LIBRARY -> CyberLibrary(
                        repo = repo,
                        onConnect = onConnect,
                        onImportFile = onImportFile,
                        onDetails = { detailProfile = it }
                    )
                    SpatialTab.SETTINGS -> SpatialSettings(
                        repo = repo,
                        onDialog = { dialog = it },
                        focusSection = settingsFocus
                    )
                }
            }


            AnimatedContent(
                targetState = detailProfile,
                modifier = Modifier.matchParentSize(),
                transitionSpec = {
                    (
                        fadeIn(MarbleMotionSpecs.ResponseFloat) +
                            scaleIn(initialScale = .965f, animationSpec = MarbleMotionSpecs.ResponseFloat) +
                            slideInVertically(MarbleMotionSpecs.Spatial) { height -> height / 14 }
                    ) togetherWith (
                        fadeOut(MarbleMotionSpecs.ExitFloat) +
                            scaleOut(targetScale = .985f, animationSpec = MarbleMotionSpecs.ExitFloat) +
                            slideOutVertically(MarbleMotionSpecs.SpatialExit) { height -> height / 18 }
                    )
                },
                label = "connection-detail-container-transform-v20"
            ) { profile ->
                if (profile == null) {
                    Box(Modifier.size(0.dp))
                } else {
                    ConnectionDetailPage(
                        profile = profile,
                        repo = repo,
                        onConnect = onConnect,
                        onBack = { detailProfile = null }
                    )
                }
            }

            // The top bar is the fallback for work that has no card of its own (audits, geo assets,
            // routing verification). Tests and refreshes report on their own node/source cards.
            AnimatedVisibility(
                visible = repo.busy && !repo.inlineProgressActive,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(MarbleMotionSpecs.ResponseFloat),
                exit = fadeOut(MarbleMotionSpecs.ExitFloat)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Aether.Cyan,
                    trackColor = Color.Transparent
                )
            }

            // Bottom toast: stays above Marble's floating dock, follows the IME, and no longer
            // covers page headers / connection controls at the top of the screen.
            MarbleSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    .widthIn(max = 520.dp)
            )
        }
    }

    // MARBLE_APP_UPDATE_UI_V102
    repo.availableUpdate?.let { update ->
        MarbleUpdateDialog(
            update = update,
            onLater = repo::dismissAppUpdate,
            onUpdate = {
                repo.dismissAppUpdate()
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(update.url)
                        )
                    )
                }.onFailure {
                    repo.setRuntimeMessage("Could not open the MarbleNG Releases page")
                }
            }
        )
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
private fun MarbleUpdateDialog(
    update: com.marbleng.app.AppUpdateInfo,
    onLater: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = Aether.VoidElevated,
        tonalElevation = 0.dp,
        icon = {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(18.dp),
                color = Aether.Cyan.copy(alpha = .11f),
                tonalElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "↑",
                        color = Aether.Cyan,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "A fresh MarbleNG build is ready",
                    color = Aether.Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Aether.Emerald.copy(alpha = .10f),
                    tonalElevation = 0.dp
                ) {
                    Text(
                        "VERSION ${update.version}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Aether.Emerald,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A newer signed release is available on GitHub.",
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (update.notes.isNotBlank()) {
                    val notesScroll = rememberScrollState()
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "WHAT CHANGED",
                            color = Aether.InkFaint,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 260.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Aether.GlassStrong,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Aether.GlassBorderSoft
                            ),
                            tonalElevation = 0.dp
                        ) {
                            SelectionContainer {
                                Text(
                                    update.notes,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(notesScroll)
                                        .padding(horizontal = 15.dp, vertical = 14.dp),
                                    color = Aether.InkMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        AnimatedVisibility(visible = notesScroll.maxValue > 0) {
                            Text(
                                if (notesScroll.value < notesScroll.maxValue) {
                                    "Swipe up to read all changes"
                                } else {
                                    "All changes shown"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (notesScroll.value < notesScroll.maxValue) {
                                    Aether.Cyan
                                } else {
                                    Aether.Emerald
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Aether.Cyan,
                    contentColor = Aether.Void
                )
            ) {
                Text("View update", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later", color = Aether.InkMuted)
            }
        }
    )
}

private fun compactInAppMessage(raw: String): String {
    val message = raw.replace(Regex("\\s+"), " ").trim()
    val lower = message.lowercase()
    return when {
        "vless without tls or other encryption is prohibited" in lower ->
            "Unsupported VLESS • enable TLS/REALITY or non-none VLESS encryption"
        "failed to build outbound config" in lower ->
            "Xray rejected this node configuration • check protocol/TLS settings"
        "context deadline exceeded" in lower && "dns-query" in lower ->
            "DNS resolver timed out • Marble is switching to a fallback path"
        message.length > 260 -> message.take(257) + "…"
        else -> message
    }
}

@Composable
private fun MarbleSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val message = compactInAppMessage(data.visuals.message)
        val lower = message.lowercase()

        val tone = when {
            listOf("fail", "error", "blocked", "denied", "could not", "missing", "rejected", "unsupported")
                .any(lower::contains) -> Aether.Danger
            listOf("warn", "inconclusive", "unavailable", "skipped", "timeout")
                .any(lower::contains) -> Aether.Amber
            listOf("connected", "ready", "saved", "added", "refreshed", "verified", "best", "copied", "complete")
                .any(lower::contains) -> Aether.Emerald
            else -> Aether.Cyan
        }

        val noticeIcon = when (tone) {
            Aether.Danger -> HomeIcon.RESET
            Aether.Amber -> HomeIcon.STATUS
            Aether.Emerald -> HomeIcon.VERIFIED
            else -> HomeIcon.SPARK
        }
        val title = when (tone) {
            Aether.Danger -> "Connection issue"
            Aether.Amber -> "Heads up"
            Aether.Emerald -> "Completed"
            else -> "Marble"
        }
        val shape = RoundedCornerShape(18.dp)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(MarbleMotionSpecs.Layout),
            shape = shape,
            color = Aether.VoidElevated,
            contentColor = Aether.Ink,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tone.copy(alpha = .11f)),
                    contentAlignment = Alignment.Center
                ) {
                    HomeVectorIcon(
                        noticeIcon,
                        tone,
                        Modifier.size(20.dp)
                    )
                }

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        title,
                        color = tone,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        message,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                data.visuals.actionLabel?.let { action ->
                    TextButton(
                        onClick = data::performAction,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(action, color = tone, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    Modifier.size(30.dp).clip(CircleShape).clickable { data.dismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    HomeVectorIcon(HomeIcon.CANCEL, Aether.InkMuted, Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DeepSpaceBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Aether.Void, Aether.VoidElevated, Aether.Void)
            )
        )
    )
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 11.dp,
                    shape = dockShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = .055f),
                    spotColor = Color.Black.copy(alpha = .09f)
                ),
            shape = dockShape,
            color = Aether.VoidElevated,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Aether.GlassBorderSoft.copy(alpha = .86f)
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpatialTab.entries.forEach { item ->
                    val active = item == selected
                    val background by animateColorAsState(
                        targetValue = if (active) Aether.Cyan.copy(alpha = .105f) else Color.Transparent,
                        animationSpec = MarbleMotionSpecs.Color,
                        label = "nav-bg-${item.name}"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (active) Aether.Cyan else Aether.InkFaint,
                        animationSpec = MarbleMotionSpecs.Color,
                        label = "nav-icon-${item.name}"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (active) Aether.Ink else Aether.InkMuted,
                        animationSpec = MarbleMotionSpecs.Color,
                        label = "nav-text-${item.name}"
                    )
                    val iconSize by animateDpAsState(
                        targetValue = if (active) 22.dp else 20.dp,
                        animationSpec = MarbleMotionSpecs.Dp,
                        label = "nav-size-${item.name}"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(background)
                            .kineticClickable(role = Role.Tab) { onSelect(item) }
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
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
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

@Suppress("UNUSED_PARAMETER")
@Composable
private fun SpatialHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    status: String? = null,
    statusColor: Color = Aether.Cyan
) {
    // MARBLE_PAGE_TITLES_V37
    val icon = when (title) {
        "Library" -> HomeIcon.LIBRARY
        "Settings" -> HomeIcon.MODE
        else -> HomeIcon.DETAILS
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            HomeIconTile(icon, Aether.Cyan)
            Text(
                title,
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        animationSpec = MarbleMotionSpecs.Color,
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
    val head = -.4f + MarbleMotion.current.loop(1_150) * 1.4f
    val settled by animateFloatAsState(
        targetValue = fraction?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = MarbleMotionSpecs.ProgressFloat,
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


// MARBLE_HOME_VECTOR_ICONS_V36
private enum class HomeIcon {
    BRAND, POWER, STOP, CANCEL, RESET, SHIELD, TUNNEL, ROUTE,
    PING, JITTER, QUALITY, NODES, VERIFIED, MODE, BENCHMARK,
    RANK, LIBRARY, PRIVACY, ROUTING, NETWORK, DOWNLOAD, UPLOAD,
    DETAILS, SPARK, STATUS
}

@Composable
private fun HomeVectorIcon(
    icon: HomeIcon,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = size.minDimension
        val stroke = (m * .085f).coerceAtLeast(1.65f)
        val fine = (stroke * .78f).coerceAtLeast(1.35f)
        val line = Stroke(width = stroke, cap = StrokeCap.Round)
        val fineLine = Stroke(width = fine, cap = StrokeCap.Round)

        when (icon) {
            HomeIcon.BRAND -> {
                drawCircle(color, radius = m * .40f, center = Offset(w * .50f, h * .50f), style = fineLine)
                val marble = Path().apply {
                    moveTo(w * .27f, h * .67f)
                    lineTo(w * .34f, h * .34f)
                    lineTo(w * .50f, h * .55f)
                    lineTo(w * .66f, h * .34f)
                    lineTo(w * .73f, h * .67f)
                }
                drawPath(marble, color, style = line)
            }

            HomeIcon.POWER -> {
                drawArc(
                    color = color,
                    startAngle = -42f,
                    sweepAngle = 264f,
                    useCenter = false,
                    topLeft = Offset(w * .18f, h * .18f),
                    size = Size(w * .64f, h * .64f),
                    style = line
                )
                drawLine(color, Offset(w*.50f,h*.13f), Offset(w*.50f,h*.51f), stroke, StrokeCap.Round)
            }

            HomeIcon.STOP -> {
                val p = Path().apply {
                    moveTo(w*.29f,h*.29f); lineTo(w*.71f,h*.29f)
                    lineTo(w*.71f,h*.71f); lineTo(w*.29f,h*.71f); close()
                }
                drawPath(p, color, style = line)
            }

            HomeIcon.CANCEL -> {
                drawLine(color, Offset(w*.28f,h*.28f), Offset(w*.72f,h*.72f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w*.72f,h*.28f), Offset(w*.28f,h*.72f), stroke, StrokeCap.Round)
            }

            HomeIcon.RESET -> {
                drawArc(
                    color = color, startAngle = -65f, sweepAngle = 286f, useCenter = false,
                    topLeft = Offset(w*.19f,h*.19f), size = Size(w*.62f,h*.62f), style = line
                )
                val arrow = Path().apply {
                    moveTo(w*.29f,h*.22f); lineTo(w*.17f,h*.39f); lineTo(w*.38f,h*.40f)
                }
                drawPath(arrow, color, style = line)
            }

            HomeIcon.SHIELD, HomeIcon.PRIVACY -> {
                val shield = Path().apply {
                    moveTo(w*.50f,h*.14f); lineTo(w*.78f,h*.26f); lineTo(w*.73f,h*.61f)
                    quadraticBezierTo(w*.68f,h*.78f,w*.50f,h*.87f)
                    quadraticBezierTo(w*.32f,h*.78f,w*.27f,h*.61f)
                    lineTo(w*.22f,h*.26f); close()
                }
                drawPath(shield, color, style = fineLine)
                if (icon == HomeIcon.PRIVACY) {
                    drawCircle(color, m*.075f, Offset(w*.50f,h*.44f), style = fineLine)
                    drawLine(color, Offset(w*.50f,h*.52f), Offset(w*.50f,h*.64f), fine, StrokeCap.Round)
                } else {
                    val check = Path().apply {
                        moveTo(w*.36f,h*.50f); lineTo(w*.46f,h*.60f); lineTo(w*.66f,h*.39f)
                    }
                    drawPath(check, color, style = line)
                }
            }

            HomeIcon.TUNNEL -> {
                drawCircle(color, m*.105f, Offset(w*.27f,h*.50f), style = fineLine)
                drawCircle(color, m*.105f, Offset(w*.73f,h*.50f), style = fineLine)
                drawLine(color, Offset(w*.37f,h*.42f), Offset(w*.63f,h*.42f), fine, StrokeCap.Round)
                drawLine(color, Offset(w*.37f,h*.58f), Offset(w*.63f,h*.58f), fine, StrokeCap.Round)
            }

            HomeIcon.ROUTE, HomeIcon.ROUTING, HomeIcon.DETAILS -> {
                drawCircle(color, m*.075f, Offset(w*.24f,h*.70f), style = fineLine)
                drawCircle(color, m*.075f, Offset(w*.50f,h*.34f), style = fineLine)
                drawCircle(color, m*.075f, Offset(w*.77f,h*.62f), style = fineLine)
                val route = Path().apply {
                    moveTo(w*.30f,h*.66f)
                    cubicTo(w*.38f,h*.60f,w*.39f,h*.41f,w*.46f,h*.37f)
                    cubicTo(w*.56f,h*.30f,w*.64f,h*.57f,w*.71f,h*.59f)
                }
                drawPath(route, color, style = fineLine)
            }

            HomeIcon.PING -> {
                drawCircle(color, m*.07f, Offset(w*.50f,h*.66f))
                drawArc(color,205f,130f,false,Offset(w*.34f,h*.43f),Size(w*.32f,h*.32f),style=fineLine)
                drawArc(color,205f,130f,false,Offset(w*.22f,h*.27f),Size(w*.56f,h*.56f),style=fineLine)
            }

            HomeIcon.JITTER -> {
                val p = Path().apply {
                    moveTo(w*.16f,h*.58f); lineTo(w*.30f,h*.58f); lineTo(w*.39f,h*.31f)
                    lineTo(w*.52f,h*.72f); lineTo(w*.62f,h*.43f); lineTo(w*.84f,h*.43f)
                }
                drawPath(p, color, style = line)
            }

            HomeIcon.QUALITY -> {
                drawArc(color,150f,240f,false,Offset(w*.18f,h*.20f),Size(w*.64f,h*.64f),style=fineLine)
                drawLine(color, Offset(w*.50f,h*.56f), Offset(w*.68f,h*.38f), stroke, StrokeCap.Round)
                drawCircle(color, m*.055f, Offset(w*.50f,h*.56f))
            }

            HomeIcon.NODES -> {
                val a = Offset(w*.25f,h*.68f); val b = Offset(w*.50f,h*.28f); val c = Offset(w*.77f,h*.67f)
                drawLine(color,a,b,fine,StrokeCap.Round); drawLine(color,b,c,fine,StrokeCap.Round)
                drawLine(color,a,c,fine,StrokeCap.Round)
                drawCircle(color,m*.075f,a,style=fineLine); drawCircle(color,m*.075f,b,style=fineLine)
                drawCircle(color,m*.075f,c,style=fineLine)
            }

            HomeIcon.VERIFIED -> {
                drawCircle(color,m*.34f,Offset(w*.50f,h*.50f),style=fineLine)
                val check = Path().apply {
                    moveTo(w*.34f,h*.51f); lineTo(w*.45f,h*.62f); lineTo(w*.67f,h*.39f)
                }
                drawPath(check,color,style=line)
            }

            HomeIcon.MODE -> {
                val frame = Path().apply {
                    moveTo(w*.27f,h*.18f); lineTo(w*.73f,h*.18f); lineTo(w*.73f,h*.82f)
                    lineTo(w*.27f,h*.82f); close()
                }
                drawPath(frame,color,style=fineLine)
                drawCircle(color,m*.035f,Offset(w*.50f,h*.72f))
            }

            HomeIcon.BENCHMARK, HomeIcon.RANK -> {
                val xs = listOf(.27f,.50f,.73f)
                val tops = if (icon == HomeIcon.RANK) listOf(.61f,.43f,.24f) else listOf(.47f,.31f,.54f)
                for (i in xs.indices) {
                    drawLine(color,Offset(w*xs[i],h*.73f),Offset(w*xs[i],h*tops[i]),stroke*1.45f,StrokeCap.Round)
                }
                drawLine(color,Offset(w*.17f,h*.78f),Offset(w*.83f,h*.78f),fine,StrokeCap.Round)
            }

            HomeIcon.LIBRARY -> {
                val box = Size(w*.22f,h*.22f)
                drawRect(color,Offset(w*.22f,h*.22f),box,style=fineLine)
                drawRect(color,Offset(w*.56f,h*.22f),box,style=fineLine)
                drawRect(color,Offset(w*.22f,h*.56f),box,style=fineLine)
                drawRect(color,Offset(w*.56f,h*.56f),box,style=fineLine)
            }

            HomeIcon.NETWORK -> {
                val xs = listOf(.24f,.42f,.60f,.78f)
                val tops = listOf(.66f,.54f,.40f,.25f)
                for (i in xs.indices) {
                    drawLine(color,Offset(w*xs[i],h*.75f),Offset(w*xs[i],h*tops[i]),stroke*1.35f,StrokeCap.Round)
                }
            }

            HomeIcon.DOWNLOAD, HomeIcon.UPLOAD -> {
                val down = icon == HomeIcon.DOWNLOAD
                val y1 = if (down) h*.22f else h*.76f
                val y2 = if (down) h*.69f else h*.29f
                drawLine(color,Offset(w*.50f,y1),Offset(w*.50f,y2),stroke,StrokeCap.Round)
                val p = Path().apply {
                    if (down) {
                        moveTo(w*.31f,h*.53f); lineTo(w*.50f,h*.72f); lineTo(w*.69f,h*.53f)
                    } else {
                        moveTo(w*.31f,h*.45f); lineTo(w*.50f,h*.26f); lineTo(w*.69f,h*.45f)
                    }
                }
                drawPath(p,color,style=line)
            }

            HomeIcon.SPARK -> {
                drawLine(color,Offset(w*.50f,h*.16f),Offset(w*.50f,h*.84f),fine,StrokeCap.Round)
                drawLine(color,Offset(w*.16f,h*.50f),Offset(w*.84f,h*.50f),fine,StrokeCap.Round)
                drawLine(color,Offset(w*.27f,h*.27f),Offset(w*.73f,h*.73f),fine,StrokeCap.Round)
                drawLine(color,Offset(w*.73f,h*.27f),Offset(w*.27f,h*.73f),fine,StrokeCap.Round)
                drawCircle(color,m*.08f,Offset(w*.50f,h*.50f))
            }

            HomeIcon.STATUS -> {
                drawCircle(color,m*.31f,Offset(w*.50f,h*.50f),style=fineLine)
                drawCircle(color,m*.08f,Offset(w*.50f,h*.50f))
            }
        }
    }
}

@Composable
private fun HomeIconTile(icon: HomeIcon, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = .105f)),
        contentAlignment = Alignment.Center
    ) {
        HomeVectorIcon(icon, color, Modifier.size(20.dp))
    }
}

@Composable
private fun HomeStatusChip(
    icon: HomeIcon,
    text: String,
    tone: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = .085f))
            .border(1.dp, tone.copy(alpha = .12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HomeVectorIcon(icon, tone, Modifier.size(14.dp))
        Text(
            text,
            color = tone,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
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
    onRouting: () -> Unit,
    onDetails: () -> Unit
) {
    val connected = repo.state == "CONNECTED"
    val connecting = repo.state == "CONNECTING"
    val blocked = repo.state == "BLOCKED"
    val active = repo.profile(repo.activeProfileId) ?: repo.lastProfile()
    val activeName = repo.stateDetail.ifBlank { active?.name ?: "Choose a route" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 11.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            // MARBLE_STABLE_HOME_TITLE_V102
            // A fixed-height title prevents the connection card from moving when runtime text changes.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    HomeIconTile(HomeIcon.BRAND, Aether.Cyan)
                    Text(
                        "MarbleNG",
                        color = Aether.Ink,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }

        item {
            HomeOrbitalHero(
                repo = repo,
                activeName = activeName,
                connected = connected,
                connecting = connecting,
                blocked = blocked,
                onToggle = {
                    if (connected || connecting || blocked) repo.stopVpn()
                    else repo.reconnectLastOrAuto(onConnect)
                },
                onDetails = onDetails
            )
        }


        if (repo.settings.homeShowSummaryMetrics) {
            item {
                val verifiedXray = repo.benchmarks.count {
                    it.probeKind == "TUNNEL" && it.success > 0
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    MiniMetric(
                        "Nodes", repo.libraryProfiles.size.toString(), "", Modifier.weight(1f),
                        accent = Aether.Amethyst, icon = HomeIcon.NODES
                    )
                    MiniMetric(
                        "Xray OK", verifiedXray.toString(), "", Modifier.weight(1f),
                        accent = if (verifiedXray > 0) Aether.Emerald else Aether.Amber,
                        icon = HomeIcon.VERIFIED
                    )
                    MiniMetric(
                        "Mode",
                        if (repo.settings.connectionMode == ConnectionMode.FULL_TUN) "TUN" else "SOCKS",
                        "",
                        Modifier.weight(1f),
                        accent = Aether.Cyan,
                        icon = HomeIcon.MODE
                    )
                }
            }
        }

        if (repo.probeActive) {
            item {
                HoloGlass(
                    borderColor = Aether.Cyan.copy(alpha = .28f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeIconTile(HomeIcon.BENCHMARK, Aether.Cyan)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "XRAY BENCHMARK",
                                color = Aether.Cyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                repo.probeCurrentName.ifBlank { "Preparing next route…" },
                                color = Aether.Ink,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HoloBadge(
                            "${repo.probeDone}/${repo.probeTotal}",
                            Aether.Cyan,
                            compact = true
                        )
                    }
                    LiveProgressBar(
                        fraction = if (repo.probeTotal > 0) {
                            repo.probeDone.toFloat() / repo.probeTotal.toFloat()
                        } else 0f,
                        color = Aether.Cyan
                    )
                    Text(
                        "Real Xray verification across every enabled node • no 8-node cap",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (repo.settings.homeShowIranMode) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
                        expandVertically(MarbleMotionSpecs.Layout),
                    exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
                        shrinkVertically(MarbleMotionSpecs.Layout)
                ) { IranModeStatusPill(repo.iranMode) }
            }
        }

        if (repo.settings.homeShowQuickActions) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HomeVectorIcon(HomeIcon.SPARK, Aether.Cyan, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("QUICK ACTIONS", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HomeActionPortal(
                            HomeIcon.RANK, "Rank all",
                            "Xray • ${repo.libraryProfiles.size} nodes",
                            Aether.Cyan, Modifier.weight(1f)
                        ) { repo.smartRank() }
                        HomeActionPortal(
                            HomeIcon.LIBRARY, "Library", "${repo.libraryProfiles.size} nodes",
                            Aether.Amethyst, Modifier.weight(1f), onLibrary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HomeActionPortal(
                            HomeIcon.PRIVACY, "Privacy", if (connected) "Audit egress" else "Connect first",
                            Aether.Emerald, Modifier.weight(1f), onPrivacy
                        )
                        HomeActionPortal(
                            HomeIcon.ROUTING, "Routing", "Traffic policy",
                            Aether.Amber, Modifier.weight(1f), onRouting
                        )
                    }
                }
            }
        }

        item { HomeRouteRibbon(repo) }
    }
}

@Composable
private fun HomeOrbitalHero(
    repo: AppRepository,
    activeName: String,
    connected: Boolean,
    connecting: Boolean,
    blocked: Boolean,
    onToggle: () -> Unit,
    onDetails: () -> Unit
) {
    val tone = when {
        connected -> Aether.Emerald
        connecting -> Aether.Amethyst
        blocked -> Aether.Danger
        else -> Aether.Cyan
    }
    val shape = RoundedCornerShape(26.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, Aether.GlassBorderSoft, shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HomeStatusChip(
                icon = when {
                    connected -> HomeIcon.SHIELD
                    connecting -> HomeIcon.BENCHMARK
                    blocked -> HomeIcon.RESET
                    else -> HomeIcon.STATUS
                },
                text = when {
                    connected -> "PROTECTED"
                    connecting -> "CONNECTING"
                    blocked -> "FAIL-CLOSED"
                    else -> "STANDBY"
                },
                tone = tone
            )
            Spacer(Modifier.weight(1f))
            HomeStatusChip(
                icon = if (repo.settings.connectionMode == ConnectionMode.FULL_TUN) HomeIcon.TUNNEL else HomeIcon.MODE,
                text = if (repo.settings.connectionMode == ConnectionMode.FULL_TUN) "FULL TUN" else "SOCKS :${repo.settings.localProxyPort}",
                tone = Aether.InkMuted
            )
        }

        Box(
            Modifier
                .size(112.dp)
                .shadow(7.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(tone)
                .kineticClickable(role = Role.Button, pressScale = .94f, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(92.dp),
                    color = Color.White.copy(alpha = .82f),
                    trackColor = Color.White.copy(alpha = .20f),
                    strokeWidth = 3.dp
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                HomeVectorIcon(
                    icon = when {
                        connected -> HomeIcon.STOP
                        connecting -> HomeIcon.CANCEL
                        blocked -> HomeIcon.RESET
                        else -> HomeIcon.POWER
                    },
                    color = Color.White,
                    modifier = Modifier.size(34.dp)
                )
                Text(
                    when { connected -> "DISCONNECT"; connecting -> "CANCEL"; blocked -> "RESET"; else -> "CONNECT" },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HomeVectorIcon(HomeIcon.ROUTE, Aether.InkMuted, Modifier.size(18.dp))
                Text(activeName, color = Aether.Ink, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDetails, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                HomeVectorIcon(HomeIcon.DETAILS, tone, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (connected) "Route details" else "Inspect selected route", color = tone, style = MaterialTheme.typography.labelSmall)
            }
        }

        val pingTone = when {
            repo.livePingMs <= 0 -> Aether.InkMuted
            repo.livePingMs <= 80 -> Aether.Emerald
            repo.livePingMs <= 170 -> Aether.Cyan
            repo.livePingMs <= 320 -> Aether.Amber
            else -> Aether.Danger
        }
        val jitterTone = when {
            repo.liveJitterSamples < 1 -> Aether.InkMuted
            repo.liveJitterMs <= 15 -> Aether.Emerald
            repo.liveJitterMs <= 35 -> Aether.Cyan
            repo.liveJitterMs <= 70 -> Aether.Amber
            else -> Aether.Danger
        }
        val qualityTone = when {
            repo.liveRouteScore < 0 -> Aether.InkMuted
            repo.liveRouteScore >= 85 -> Aether.Emerald
            repo.liveRouteScore >= 65 -> Aether.Cyan
            repo.liveRouteScore >= 40 -> Aether.Amber
            else -> Aether.Danger
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MiniMetric(
                "Ping", if (repo.livePingMs > 0) repo.livePingMs.toString() else "—", "ms",
                Modifier.weight(1f), accent = pingTone, icon = HomeIcon.PING
            )
            MiniMetric(
                "Jitter", if (repo.liveJitterSamples >= 1) repo.liveJitterMs.toString() else "—", "ms",
                Modifier.weight(1f), accent = jitterTone, icon = HomeIcon.JITTER
            )
            MiniMetric(
                "Quality", if (repo.liveRouteScore >= 0) repo.liveRouteScore.toString() else "—",
                if (repo.liveRouteScore >= 0) "%" else "", Modifier.weight(1f),
                accent = qualityTone, icon = HomeIcon.QUALITY
            )
        }
    }
}

@Composable
private fun HomeActionPortal(
    icon: HomeIcon,
    title: String,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .heightIn(min = 82.dp)
            .clip(shape)
            .background(color.copy(alpha = .075f))
            .kineticClickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).background(color.copy(alpha = .13f)),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(icon, color, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(detail, color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HomeVectorIcon(HomeIcon.DETAILS, color.copy(alpha = .72f), Modifier.size(18.dp))
    }
}

@Composable
private fun HomeRouteRibbon(repo: AppRepository) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Aether.GlassStrong)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeIconTile(HomeIcon.NETWORK, Aether.Cyan, Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Text(repo.networkSnapshot.label, color = Aether.Ink, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HomeVectorIcon(HomeIcon.DOWNLOAD, Aether.InkFaint, Modifier.size(12.dp))
                Text(compactRate(repo.liveDownBps), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(4.dp))
                HomeVectorIcon(HomeIcon.UPLOAD, Aether.InkFaint, Modifier.size(12.dp))
                Text(compactRate(repo.liveUpBps), color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
            }
        }
        HomeStatusChip(
            icon = when {
                repo.sentinel.killSwitchArmed -> HomeIcon.SHIELD
                repo.state == "CONNECTED" -> HomeIcon.TUNNEL
                else -> HomeIcon.STATUS
            },
            text = when {
                repo.sentinel.killSwitchArmed -> "KILL SWITCH"
                repo.state == "CONNECTED" -> "TUNNEL"
                else -> "IDLE"
            },
            tone = if (repo.sentinel.killSwitchArmed) Aether.Emerald else Aether.InkMuted
        )
    }
}

@Composable
private fun IranModeStatusPill(state: IranModeState) {
    val forced = state.policy == IranModePolicy.ALWAYS_ON
    val scanning = state.scanning && !forced
    val tone = if (scanning) Aether.Amber else Aether.Emerald
    val pulse = .76f + MarbleMotion.current.breathe(2_100) * .24f
    val shape = RoundedCornerShape(18.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.copy(alpha = .075f))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .alpha(if (scanning) pulse else 1f)
                .clip(RoundedCornerShape(13.dp))
                .background(tone.copy(alpha = .11f)),
            contentAlignment = Alignment.Center
        ) {
            HomeVectorIcon(if (scanning) HomeIcon.BENCHMARK else HomeIcon.SHIELD, tone, Modifier.size(20.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                when {
                    forced -> "IRAN MODE • FORCED ON"
                    scanning -> "IRAN MODE • SCANNING"
                    else -> "IRAN MODE • ACTIVE"
                },
                color = tone,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    forced -> "Always-on protection • physical detection bypassed"
                    scanning -> "Checking the physical underlay"
                    state.isp != null -> buildString {
                        append(state.ispLine)
                        if (state.confidence > 0) append(" • ${state.confidence}%")
                    }
                    else -> "Restricted-network protection active"
                },
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HoloBadge(
            when {
                forced -> "LOCKED ON"
                scanning -> "AUTO"
                else -> "ACTIVE"
            },
            tone,
            compact = true
        )
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
    jitterMs: Int,
    pingSamples: Int,
    jitterSamples: Int,
    routeScore: Int,
    detailsAvailable: Boolean,
    onDetails: () -> Unit,
    onToggle: () -> Unit
) {
    // MARBLE_HOME_LATENCY_V17
    // MARBLE_HOME_COMMAND_CENTER_V22
    val statusColor = when {
        connected -> Aether.Emerald
        connecting -> Aether.Cyan
        blocked -> Aether.Danger
        else -> Aether.Cyan
    }
    val statusTitle = when {
        connected -> "Protected"
        connecting -> "Connecting"
        blocked -> "Blocked"
        else -> "Connect"
    }
    val shape = RoundedCornerShape(28.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        statusColor.copy(alpha = .12f),
                        Aether.VoidElevated,
                        Aether.VoidElevated
                    )
                )
            )
            .border(1.dp, statusColor.copy(alpha = .24f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HoloBadge(
                when {
                    connected -> "● PROTECTED"
                    connecting -> "● CONNECTING"
                    blocked -> "● FAIL-CLOSED"
                    else -> "○ READY"
                },
                statusColor,
                compact = true
            )
            Spacer(Modifier.weight(1f))
            HoloBadge(
                if (mode == ConnectionMode.FULL_TUN) "FULL TUN" else "SOCKS :$localPort",
                Aether.InkMuted,
                compact = true
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = .075f))
                    .border(2.dp, statusColor.copy(alpha = .72f), CircleShape)
                    .kineticClickable(role = Role.Button, pressScale = .95f, onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize().padding(8.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(statusColor.copy(alpha = .08f), r)
                    if (connecting) {
                        drawArc(
                            color = statusColor,
                            startAngle = -70f,
                            sweepAngle = 235f,
                            useCenter = false,
                            style = Stroke(6f, cap = StrokeCap.Round)
                        )
                    }
                }
                Text(
                    when {
                        connected -> "✓"
                        connecting -> "…"
                        blocked -> "!"
                        else -> "↗"
                    },
                    color = statusColor,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    statusTitle,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1
                )
                Text(
                    activeName,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            connecting -> "Tap the orb to cancel"
                            connected -> "Tap the orb to disconnect"
                            blocked -> "Tap the orb to retry"
                            else -> "Tap the orb to start"
                        },
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (detailsAvailable) {
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = onDetails,
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Details",
                                color = Aether.Cyan,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Aether.GlassBorderSoft.copy(alpha = .75f))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            MiniMetric(
                "Ping",
                if (pingMs > 0) pingMs.toString() else "—",
                "ms",
                Modifier.weight(1f)
            )
            MiniMetric(
                "Jitter",
                if (jitterSamples >= 2 && jitterMs >= 0) jitterMs.toString() else "—",
                "ms",
                Modifier.weight(1f)
            )
            MiniMetric(
                "Quality",
                if (routeScore >= 0) routeScore.toString() else "—",
                if (routeScore >= 0) "%" else "",
                Modifier.weight(1f)
            )
        }

        Text(
            when {
                pingSamples <= 0 ->
                    "Waiting for verified HTTPS RTT"
                jitterSamples < 2 ->
                    "Verified HTTPS RTT • $pingSamples sample${if (pingSamples == 1) "" else "s"} • jitter warming"
                else ->
                    "Verified HTTPS burst • $pingSamples RTT • $jitterSamples consecutive deltas"
            },
            color = Aether.InkFaint,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
    icon: HomeIcon? = null
) {
    val valueColor = if (accent == Color.Unspecified) Aether.Ink else accent
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(valueColor.copy(alpha = .075f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            icon?.let { HomeVectorIcon(it, valueColor, Modifier.size(13.dp)) }
            Text(
                label.uppercase(),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = valueColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, color = valueColor.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
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
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(color.copy(alpha = .065f))
            .kineticClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = .13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                glyph,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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


// =================================================================================================
// LIBRARY
// =================================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CyberLibrary(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onDetails: (ProxyProfile) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var search by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf("subscription") }
    var url by remember { mutableStateOf("") }
    var sourceName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ProxyProfile?>(null) }
    var renameText by remember { mutableStateOf("") }
    val sourceFilter = repo.librarySourceFilter
    var manageSubscription by remember { mutableStateOf<Subscription?>(null) }
    var editSubscriptionName by remember { mutableStateOf("") }
    var editSubscriptionUrl by remember { mutableStateOf("") }
    var deleteSubscription by remember { mutableStateOf<Subscription?>(null) }
    var pruneFailedTarget by remember { mutableStateOf<Pair<Subscription, String>?>(null) }

    val sourceIds = repo.subscriptions.map { it.id }
    LaunchedEffect(sourceIds, repo.settings.manualSourceEnabled) {
        repo.ensureLibrarySourceSelectionValid()
    }

    val benchmarkById = repo.benchmarks.associateBy { it.profileId }
    val filtered = repo.libraryProfiles.filter {
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
                        repo.renameProfile(target.id, renameText, target.subscriptionId)
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
        val failedPingCount = repo.failedSubscriptionNodeCount(target.id, "TCP")
        val failedTunnelCount = repo.failedSubscriptionNodeCount(target.id, "TUNNEL")
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
                            if (target.url.isBlank()) "Local source" else "Copy URL",
                            Aether.Emerald,
                            Modifier.weight(1f),
                            enabled = target.url.isNotBlank()
                        ) {
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
                    HorizontalDivider(color = Aether.GlassBorderSoft)
                    Text(
                        "CLEAN FAILED TESTS",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )
                    CyberButton(
                        label = "Remove failed ping ($failedPingCount)",
                        color = Aether.Danger,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !repo.busy && failedPingCount > 0 && repo.state == "DISCONNECTED"
                    ) {
                        pruneFailedTarget = target to "TCP"
                        manageSubscription = null
                    }
                    CyberButton(
                        label = "Remove failed tunnel ($failedTunnelCount)",
                        color = Aether.Danger,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !repo.busy && failedTunnelCount > 0 && repo.state == "DISCONNECTED"
                    ) {
                        pruneFailedTarget = target to "TUNNEL"
                        manageSubscription = null
                    }
                    Text(
                        "Only nodes with a stored failed result of that exact test type are removed.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CyberButton(
                            label = "VIEW NODES",
                            color = Aether.Cyan,
                            modifier = Modifier.weight(1f)
                        ) {
                            repo.selectLibrarySource(target.id)
                            manageSubscription = null
                        }
                        CyberButton(
                            label = if (target.url.isBlank()) "LOCAL" else "Refresh",
                            color = Aether.Amethyst,
                            modifier = Modifier.weight(1f),
                            enabled = !repo.busy && target.url.isNotBlank()
                        ) {
                            manageSubscription = null
                            repo.refresh(target.id)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repo.updateSubscription(target.id, editSubscriptionName, editSubscriptionUrl)) {
                            manageSubscription = null
                        }
                    },
                    enabled = !repo.busy
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

    pruneFailedTarget?.let { request ->
        val target = request.first
        val kind = request.second
        val failedCount = repo.failedSubscriptionNodeCount(target.id, kind)
        AlertDialog(
            onDismissRequest = { pruneFailedTarget = null },
            containerColor = Aether.VoidElevated,
            title = { Text("Remove failed $kind nodes?", color = Aether.Danger) },
            text = {
                Text(
                    "This removes $failedCount node${if (failedCount == 1) "" else "s"} from ${target.name} whose most recent stored $kind test failed. Other sources and other test types are untouched.",
                    color = Aether.InkMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.removeFailedSubscriptionNodes(target.id, kind)
                        pruneFailedTarget = null
                    },
                    enabled = !repo.busy && failedCount > 0 && repo.state == "DISCONNECTED"
                ) { Text("REMOVE FAILED", color = Aether.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pruneFailedTarget = null }) {
                    Text("CANCEL", color = Aether.InkMuted)
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
                        if (sourceFilter == target.id) repo.selectLibrarySource("all")
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
                subtitle = "Quick TCP ping • verified Xray smart rank",
                status = "${repo.libraryProfiles.size} nodes",
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

                CyberButton(
                    label = if (addOpen) "Close" else "Add",
                    color = Aether.Cyan,
                    modifier = Modifier.height(56.dp)
                ) { addOpen = !addOpen }
            }
        }

        item {
            AnimatedVisibility(
                visible = addOpen,
                enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
                    expandVertically(MarbleMotionSpecs.Layout),
                exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
                    shrinkVertically(MarbleMotionSpecs.Layout)
            ) {
                HoloGlass(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = Aether.Amethyst.copy(alpha = .45f)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CyberChoiceChip("Subscription", addMode == "subscription", Aether.Cyan) { addMode = "subscription" }
                        CyberChoiceChip("Manual config", addMode == "manual", Aether.Emerald) { addMode = "manual" }
                    }
                    AnimatedContent(
                        targetState = addMode,
                        transitionSpec = {
                            (
                                fadeIn(MarbleMotionSpecs.ResponseFloat) +
                                    slideInHorizontally(MarbleMotionSpecs.Spatial) { it / 12 }
                            ) togetherWith (
                                fadeOut(MarbleMotionSpecs.ExitFloat) +
                                    slideOutHorizontally(MarbleMotionSpecs.SpatialExit) { -it / 14 }
                            )
                        },
                        label = "library-add-mode-v20"
                    ) { mode ->
                        if (mode == "manual") {
                            ManualConfigEditor(
                                repo = repo,
                                targetSourceId = sourceFilter,
                                onSaved = { addOpen = false }
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = { Text("Subscription URL • optional") },
                                    supportingText = { Text("HTTPS only • leave blank for a local source/folder.") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                OutlinedTextField(
                                    value = sourceName,
                                    onValueChange = { sourceName = it },
                                    label = { Text("Name • optional") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                CyberButton(
                                    label = if (url.isBlank()) "Create local source" else "Add subscription",
                                    color = Aether.Cyan,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !repo.busy && (
                                        url.isBlank() ||
                                            url.startsWith("https://", true)
                                        )
                                ) {
                                    repo.addSubscription(sourceName, url)
                                    url = ""
                                    sourceName = ""
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CyberButton(
                            label = "Paste clipboard",
                            color = Aether.Emerald,
                            modifier = Modifier.weight(1f)
                        ) {
                            repo.importClipboard(
                                clipboard.getText()?.text.orEmpty(),
                                sourceFilter
                            )
                        }
                        CyberButton(
                            label = "Import file",
                            color = Aether.Amethyst,
                            modifier = Modifier.weight(1f)
                        ) { onImportFile() }
                    }
                }
            }
        }


        item {
            LibraryControlDeck(
                repo = repo,
                sourceFilter = sourceFilter,
                onSourceFilter = { repo.selectLibrarySource(it) },
                onManageSubscription = { sub ->
                    editSubscriptionName = sub.name
                    editSubscriptionUrl = sub.url
                    manageSubscription = sub
                }
            )
        }

        item { SectionLabel("Nodes") }

        if (visible.isEmpty()) {
            item {
                HoloGlass(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (repo.libraryProfiles.isEmpty()) "No connections yet" else "Nothing matches",
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (repo.libraryProfiles.isEmpty()) {
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
                            if (editSide) "Rename" else "Full test",
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
                    active = repo.isActiveProfile(profile),
                    probeState = repo.probeStateOf(profile.id),
                    onConnect = onConnect,
                    onEdit = {
                        renameTarget = profile
                        renameText = profile.name
                    },
                    onDetails = { onDetails(profile) }
                )
            }
        }
    }
}

@Composable
private fun ManualConfigEditor(
    repo: AppRepository,
    targetSourceId: String,
    onSaved: () -> Unit
) {
    var draft by remember { mutableStateOf(ManualConfigDraft()) }
    val targetSourceName = when {
        targetSourceId == "manual" && repo.settings.manualSourceEnabled -> "Manual"
        targetSourceId == "manual" -> null
        else -> repo.subscriptions.firstOrNull { it.id == targetSourceId }?.name
    }
    val targetReady = targetSourceName != null
    val protocol = draft.protocol
    val streamProtocols = setOf(ManualProtocol.VLESS, ManualProtocol.VMESS, ManualProtocol.TROJAN)

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ManualProtocol.entries.forEach { item ->
                CyberChoiceChip(item.label, protocol == item, if (item == ManualProtocol.XRAY_JSON) Aether.Amethyst else Aether.Cyan) {
                    draft = ManualConfigDraft(protocol = item).copy(
                        name = draft.name,
                        port = when (item) {
                            ManualProtocol.HTTP -> "80"
                            ManualProtocol.SOCKS5 -> "1080"
                            ManualProtocol.SSH -> "22"
                            else -> "443"
                        },
                        security = when (item) {
                            ManualProtocol.SHADOWSOCKS,
                            ManualProtocol.HTTP,
                            ManualProtocol.SOCKS5,
                            ManualProtocol.SSH,
                            ManualProtocol.WIREGUARD -> "none"
                            else -> "tls"
                        }
                    )
                }
            }
        }

        ManualField("Name", draft.name, { draft = draft.copy(name = it) })

        if (protocol == ManualProtocol.XRAY_JSON) {
            ManualField(
                label = "Xray config / outbound JSON",
                value = draft.customJson,
                onValueChange = { draft = draft.copy(customJson = it) },
                singleLine = false,
                minLines = 10
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManualField("Host", draft.host, { draft = draft.copy(host = it) }, Modifier.weight(1.55f))
                ManualField("Port", draft.port, { draft = draft.copy(port = it.filter(Char::isDigit).take(5)) }, Modifier.weight(.75f))
            }

            when (protocol) {
                ManualProtocol.VLESS -> {
                    ManualField("UUID", draft.uuid, { draft = draft.copy(uuid = it) })
                    ManualField("VLESS encryption", draft.encryption, { draft = draft.copy(encryption = it) })
                    ManualField("Flow", draft.flow, { draft = draft.copy(flow = it) })
                }
                ManualProtocol.VMESS -> {
                    ManualField("UUID", draft.uuid, { draft = draft.copy(uuid = it) })
                    ManualField("Cipher", draft.encryption, { draft = draft.copy(encryption = it) })
                }
                ManualProtocol.TROJAN ->
                    ManualField("Password", draft.password, { draft = draft.copy(password = it) })
                ManualProtocol.SHADOWSOCKS -> {
                    ManualField("Method", draft.method, { draft = draft.copy(method = it) })
                    ManualField("Password", draft.password, { draft = draft.copy(password = it) })
                }
                ManualProtocol.HYSTERIA2 -> {
                    ManualField("Auth", draft.password, { draft = draft.copy(password = it) })
                    ManualField("SNI", draft.sni, { draft = draft.copy(sni = it) })
                    ManualField("Fingerprint", draft.fingerprint, { draft = draft.copy(fingerprint = it) })
                    FingerprintPresetRow(draft.fingerprint, allowUnsafe = true) {
                        draft = draft.copy(fingerprint = it)
                    }
                    ManualField("ALPN", draft.alpn, { draft = draft.copy(alpn = it) })
                    ManualField(
                        "Cipher Suites (: separated)",
                        draft.cipherSuites,
                        { draft = draft.copy(cipherSuites = it) }
                    )
                    CyberChoiceChip("Allow insecure TLS", draft.allowInsecure, Aether.Danger) {
                        draft = draft.copy(allowInsecure = !draft.allowInsecure)
                    }
                }
                ManualProtocol.HTTP, ManualProtocol.HTTPS, ManualProtocol.SOCKS5 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("Username", draft.username, { draft = draft.copy(username = it) }, Modifier.weight(1f))
                        ManualField("Password", draft.password, { draft = draft.copy(password = it) }, Modifier.weight(1f))
                    }
                    if (protocol == ManualProtocol.HTTPS) {
                        ManualField("TLS server name", draft.sni, { draft = draft.copy(sni = it) })
                        ManualField("Fingerprint", draft.fingerprint, { draft = draft.copy(fingerprint = it) })
                        FingerprintPresetRow(draft.fingerprint, allowUnsafe = true) {
                            draft = draft.copy(fingerprint = it)
                        }
                        ManualField(
                            "Cipher Suites (: separated)",
                            draft.cipherSuites,
                            { draft = draft.copy(cipherSuites = it) }
                        )
                        CyberChoiceChip("Allow insecure TLS", draft.allowInsecure, Aether.Danger) {
                            draft = draft.copy(allowInsecure = !draft.allowInsecure)
                        }
                    }
                }
                ManualProtocol.SSH -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("Username", draft.username, { draft = draft.copy(username = it) }, Modifier.weight(1f))
                        ManualField("Password", draft.password, { draft = draft.copy(password = it) }, Modifier.weight(1f))
                    }
                    ManualField("Host key SHA256 • optional", draft.sshHostKeySha256, { draft = draft.copy(sshHostKeySha256 = it) })
                    Text("SSH carries TCP through the protected loopback adapter; UDP is blocked fail-closed.", color = Aether.InkFaint, style = MaterialTheme.typography.bodySmall)
                }
                ManualProtocol.WIREGUARD -> {
                    ManualField("Private key", draft.wireguardSecretKey, { draft = draft.copy(wireguardSecretKey = it) })
                    ManualField("Local address / CIDR", draft.wireguardAddress, { draft = draft.copy(wireguardAddress = it) })
                    ManualField("Peer public key", draft.wireguardPeerPublicKey, { draft = draft.copy(wireguardPeerPublicKey = it) })
                    ManualField("Pre-shared key", draft.wireguardPreSharedKey, { draft = draft.copy(wireguardPreSharedKey = it) })
                    ManualField("Allowed IPs", draft.wireguardAllowedIps, { draft = draft.copy(wireguardAllowedIps = it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("Reserved", draft.wireguardReserved, { draft = draft.copy(wireguardReserved = it) }, Modifier.weight(1f))
                        ManualField("Keepalive", draft.wireguardKeepAlive, { draft = draft.copy(wireguardKeepAlive = it) }, Modifier.weight(1f))
                        ManualField("MTU", draft.wireguardMtu, { draft = draft.copy(wireguardMtu = it) }, Modifier.weight(1f))
                    }
                    CyberChoiceChip("Userspace WireGuard", draft.wireguardNoKernelTun, Aether.Emerald) {
                        draft = draft.copy(wireguardNoKernelTun = !draft.wireguardNoKernelTun)
                    }
                }
                ManualProtocol.XRAY_JSON -> Unit
            }

            if (protocol in streamProtocols) {
                SectionLabel("Transport")
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("raw", "websocket", "xhttp", "grpc", "httpupgrade", "mkcp").forEach { value ->
                        CyberChoiceChip(value.uppercase(), draft.transport == value, Aether.Amethyst) {
                            draft = draft.copy(
                                transport = value,
                                security = if (draft.security == "reality" && value !in setOf("raw", "xhttp", "grpc")) "tls" else draft.security
                            )
                        }
                    }
                }

                if (draft.transport in setOf("websocket", "xhttp", "httpupgrade")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("Path", draft.path, { draft = draft.copy(path = it) }, Modifier.weight(1f))
                        ManualField("Host header", draft.hostHeader, { draft = draft.copy(hostHeader = it) }, Modifier.weight(1f))
                    }
                }
                if (draft.transport == "grpc") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("Service name", draft.serviceName, { draft = draft.copy(serviceName = it) }, Modifier.weight(1f))
                        ManualField("Authority", draft.hostHeader, { draft = draft.copy(hostHeader = it) }, Modifier.weight(1f))
                    }
                }

                SectionLabel("Security")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val realitySupported = draft.transport in setOf("raw", "xhttp", "grpc")
                    val choices = when {
                        protocol == ManualProtocol.TROJAN && realitySupported -> listOf("tls", "reality")
                        protocol == ManualProtocol.TROJAN -> listOf("tls")
                        realitySupported -> listOf("none", "tls", "reality")
                        else -> listOf("none", "tls")
                    }
                    choices.forEach { value ->
                        CyberChoiceChip(value.uppercase(), draft.security == value, if (value == "reality") Aether.Amethyst else Aether.Cyan) {
                            draft = draft.copy(security = value)
                        }
                    }
                }

                if (draft.security in setOf("tls", "reality")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("SNI", draft.sni, { draft = draft.copy(sni = it) }, Modifier.weight(1f))
                        ManualField("Fingerprint", draft.fingerprint, { draft = draft.copy(fingerprint = it) }, Modifier.weight(1f))
                    }
                    FingerprintPresetRow(
                        value = draft.fingerprint,
                        allowUnsafe = draft.security == "tls"
                    ) { draft = draft.copy(fingerprint = it) }
                }
                if (draft.security == "tls") {
                    ManualField("ALPN", draft.alpn, { draft = draft.copy(alpn = it) })
                    ManualField(
                        "Cipher Suites (: separated)",
                        draft.cipherSuites,
                        { draft = draft.copy(cipherSuites = it) }
                    )
                    Text(
                        "PattNG/Xray TLS control • `unsafe` uses native Go TLS; leave Cipher Suites empty for automatic defaults.",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall
                    )
                    CyberChoiceChip("Allow insecure TLS", draft.allowInsecure, Aether.Danger) {
                        draft = draft.copy(allowInsecure = !draft.allowInsecure)
                    }
                }
                if (draft.security == "reality") {
                    ManualField("REALITY public key", draft.realityPublicKey, { draft = draft.copy(realityPublicKey = it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManualField("Short ID", draft.realityShortId, { draft = draft.copy(realityShortId = it) }, Modifier.weight(1f))
                        ManualField("Spider X", draft.realitySpiderX, { draft = draft.copy(realitySpiderX = it) }, Modifier.weight(1f))
                    }
                }
            }
        }

        SectionLabel("Save to", targetSourceName ?: "Select one Library source first")
        Text(
            if (targetReady) {
                "This config will be added to the currently selected source and kept across refreshes."
            } else {
                "All sources is only a view. Select a source chip first, then add the config."
            },
            color = if (targetReady) Aether.Emerald else Aether.Amber,
            style = MaterialTheme.typography.bodySmall
        )

        CyberButton(
            label = if (protocol == ManualProtocol.SSH) "Save SSH connection" else "Save manual config",
            color = Aether.Emerald,
            modifier = Modifier.fillMaxWidth(),
            enabled = targetReady && !repo.busy && (
                (protocol == ManualProtocol.XRAY_JSON && draft.customJson.isNotBlank()) ||
                    (protocol != ManualProtocol.XRAY_JSON && draft.host.isNotBlank())
                )
        ) {
            if (repo.addManualProfile(draft, targetSourceId)) {
                draft = ManualConfigDraft()
                onSaved()
            }
        }
    }
}

@Composable
private fun FingerprintPresetRow(
    value: String,
    allowUnsafe: Boolean,
    onSelect: (String) -> Unit
) {
    val presets = if (allowUnsafe) {
        listOf("chrome", "firefox", "safari", "randomized", "unsafe")
    } else {
        // REALITY uses uTLS; do not suggest PattNG/Xray's native-Go-TLS `unsafe` mode here.
        listOf("chrome", "firefox", "safari", "randomized")
    }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            CyberChoiceChip(
                text = preset,
                selected = value.equals(preset, ignoreCase = true),
                color = if (preset == "unsafe") Aether.Amber else Aether.Cyan
            ) { onSelect(preset) }
        }
    }
}

@Composable
private fun ManualField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else 18,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        textStyle = if (singleLine) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
}

@Composable
private fun ConnectionDetailPage(
    profile: ProxyProfile,
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onBack: () -> Unit
) {
    val current = repo.profile(profile.id, profile.subscriptionId) ?: profile
    val result = repo.benchmarks.firstOrNull { it.profileId == current.id }?.takeIf { it.success > 0 }
    val active = repo.isActiveProfile(current)
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Aether.Void),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("‹ Back", color = Aether.Cyan)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    current.name,
                    color = Aether.Ink,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (active) HoloBadge("Active", Aether.Emerald, compact = true)
            }
        }

        item {
            HoloGlass(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    HoloBadge(current.scheme.uppercase(), Aether.Cyan, compact = true)
                    current.transport.takeIf { it.isNotBlank() && !it.equals("native", true) }
                        ?.let { HoloBadge(it.uppercase(), Aether.Amethyst, compact = true) }
                    current.security.takeIf { it.isNotBlank() && !it.equals("none", true) }
                        ?.let { HoloBadge(it.uppercase(), Aether.Emerald, compact = true) }
                }
                DetailRow("Endpoint", listOf(current.host, current.port.takeIf { it > 0 }?.toString()).filterNotNull().joinToString(":"))
                DetailRow("Source", current.subscriptionName)
            }
        }

        if (result != null) {
            item {
                HoloGlass(Modifier.fillMaxWidth()) {
                    SectionLabel("Measured")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniMetric("Ping", result.latencyMs.roundToInt().toString(), "ms", Modifier.weight(1f))
                        MiniMetric("Reachable", result.success.toString(), "%", Modifier.weight(1f))
                    }
                    if (result.bytesPerSecond > 0) {
                        DetailRow("Throughput", "${formatBytes(result.bytesPerSecond.toLong())}/s")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton(
                    label = if (active) "Disconnect" else "Connect",
                    color = if (active) Aether.Danger else Aether.Emerald,
                    modifier = Modifier.weight(1f),
                    enabled = !repo.busy
                ) {
                    if (active) repo.stopVpn() else onConnect(current)
                }
                CyberButton("Test", Aether.Cyan, Modifier.weight(1f), enabled = !repo.busy) {
                    repo.fullTest(current)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("Copy config", Aether.Amethyst, Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(current.raw.trim().ifBlank { current.configJson }))
                    repo.setRuntimeMessage("Config copied")
                }
                CyberButton("Copy JSON", Aether.InkMuted, Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(current.configJson))
                    repo.setRuntimeMessage("Xray JSON copied")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Aether.InkFaint, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Aether.Ink,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LibraryControlDeck(
    repo: AppRepository,
    sourceFilter: String,
    onSourceFilter: (String) -> Unit,
    onManageSubscription: (Subscription) -> Unit
) {
    // MARBLE_LIBRARY_CLEANUP_V37
    val manualCount = repo.libraryProfiles.count { it.subscriptionId == "manual" }
    val selectedSub = repo.subscriptions.firstOrNull { it.id == sourceFilter }
    val selectedCount = when (sourceFilter) {
        "all" -> repo.libraryProfiles.size
        "manual" -> manualCount
        else -> repo.subscriptionNodeCount(sourceFilter)
    }
    val remoteCount = repo.subscriptions.count { it.url.isNotBlank() }
    val selectedRefreshing = when (sourceFilter) {
        "all" -> repo.refreshingSources.isNotEmpty()
        else -> sourceFilter in repo.refreshingSources
    }
    val canRefreshSelected = when (sourceFilter) {
        "all" -> remoteCount > 0
        "manual" -> false
        else -> selectedSub?.url?.isNotBlank() == true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("source-all") {
                SourceOrbitChip(
                    "All",
                    "${repo.libraryProfiles.size}",
                    sourceFilter == "all",
                    Aether.Cyan,
                    { onSourceFilter("all") }
                )
            }
            if (repo.settings.manualSourceEnabled) {
                item("source-manual") {
                    SourceOrbitChip(
                        "Manual",
                        "$manualCount",
                        sourceFilter == "manual",
                        Aether.Amber,
                        { onSourceFilter("manual") }
                    )
                }
            }
            items(repo.subscriptions, key = { "orbit-${it.id}" }) { sub ->
                val local = sub.url.isBlank()
                val refreshing = sub.id in repo.refreshingSources
                SourceOrbitChip(
                    title = sub.name,
                    detail = when {
                        refreshing -> "syncing"
                        local -> "${repo.subscriptionNodeCount(sub.id)} local"
                        else -> "${repo.subscriptionNodeCount(sub.id)}"
                    },
                    selected = sourceFilter == sub.id,
                    color = if (local) Aether.Emerald else Aether.Amethyst,
                    onClick = { onSourceFilter(sub.id) },
                    onManage = { onManageSubscription(sub) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            NodeSortMode.entries.filter { it != NodeSortMode.SCORE }.forEach { mode ->
                CyberChoiceChip(
                    sortModeLabel(mode),
                    repo.settings.nodeSortMode == mode,
                    if (mode == NodeSortMode.PING) Aether.Cyan else Aether.Amethyst
                ) {
                    repo.updateSettings(
                        repo.settings.copy(nodeSortMode = mode, nodeSortReverse = false)
                    )
                }
            }
            CyberChoiceChip(
                if (repo.settings.nodeSortReverse) "Normal order" else "Reverse",
                repo.settings.nodeSortReverse,
                Aether.Amber
            ) {
                repo.updateSettings(
                    repo.settings.copy(nodeSortReverse = !repo.settings.nodeSortReverse)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryMicroAction(
                HomeIcon.RESET,
                if (selectedRefreshing) "Syncing" else "Refresh",
                Aether.Amethyst,
                Modifier.weight(1f),
                canRefreshSelected && !repo.busy
            ) { repo.refreshLibrarySource(sourceFilter) }

            LibraryMicroAction(
                HomeIcon.PING,
                "Ping",
                Aether.Cyan,
                Modifier.weight(1f),
                selectedCount > 0 && !repo.busy
            ) { repo.testSource(sourceFilter) }

            LibraryMicroAction(
                HomeIcon.RANK,
                "Rank",
                Aether.Emerald,
                Modifier.weight(1f),
                selectedCount > 0 && !repo.busy
            ) { repo.smartRankSource(sourceFilter) }
        }

        AnimatedVisibility(
            visible = repo.probeActive,
            enter = fadeIn(MarbleMotionSpecs.ResponseFloat) +
                expandVertically(MarbleMotionSpecs.Layout),
            exit = fadeOut(MarbleMotionSpecs.ExitFloat) +
                shrinkVertically(MarbleMotionSpecs.Layout)
        ) {
            HoloGlass(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Aether.Cyan.copy(alpha = .30f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    HomeIconTile(HomeIcon.BENCHMARK, Aether.Cyan)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NODE TEST IN PROGRESS",
                            color = Aether.Cyan,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            repo.probeCurrentName.ifBlank { "Preparing selected nodes…" },
                            color = Aether.Ink,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HoloBadge(
                        if (repo.probeTotal > 0) {
                            "${repo.probeDone.coerceAtMost(repo.probeTotal)}/${repo.probeTotal}"
                        } else {
                            "PREPARING"
                        },
                        Aether.Cyan,
                        compact = true
                    )
                }
                LiveProgressBar(
                    fraction = if (repo.probeTotal > 0) {
                        repo.probeDone.toFloat() / repo.probeTotal.toFloat()
                    } else {
                        null
                    },
                    color = Aether.Cyan
                )
                Text(
                    "Results appear on each node as soon as its verification finishes.",
                    color = Aether.InkFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SourceOrbitChip(
    title: String,
    detail: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    onManage: (() -> Unit)? = null
) {
    val width by animateDpAsState(
        targetValue = if (selected) 178.dp else 138.dp,
        animationSpec = MarbleMotionSpecs.Dp,
        label = "source-orbit-$title"
    )
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.width(width).heightIn(min = 58.dp).clip(shape)
            .background(if (selected) color.copy(alpha = .12f) else Aether.GlassStrong.copy(alpha = .50f))
            .border(1.dp, if (selected) color.copy(alpha = .48f) else Aether.GlassBorderSoft, shape)
            .kineticClickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, end = 7.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = if (selected) .18f else .08f)),
            contentAlignment = Alignment.Center
        ) {
            Text(title.trim().firstOrNull()?.uppercase() ?: "S", color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Aether.Ink, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = if (selected) color else Aether.InkFaint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        if (onManage != null && selected) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .kineticClickable(role = Role.Button, onClick = onManage),
                contentAlignment = Alignment.Center
            ) {
                Text("⋮", color = color)
            }
        }
    }
}

@Composable
private fun LibraryMicroAction(
    icon: HomeIcon,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(color.copy(alpha = if (enabled) .09f else .035f))
            .border(1.dp, color.copy(alpha = if (enabled) .19f else .08f), shape)
            .kineticClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HomeVectorIcon(
            icon,
            if (enabled) color else Aether.InkFaint,
            Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = if (enabled) Aether.Ink else Aether.InkFaint, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Source filter and sort order, grouped on one surface instead of two loose label blocks. */
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
    val local = sub.url.isBlank()
    val expired = sub.expireAt > 0L && sub.expireAt < System.currentTimeMillis()
    val accent = when {
        expired -> Aether.Danger
        refreshing -> Aether.Amethyst
        selected -> Aether.Cyan
        local -> Aether.Emerald
        else -> Aether.InkMuted
    }
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = Modifier
            .widthIn(min = 210.dp, max = 270.dp)
            .heightIn(min = 62.dp)
            .clip(shape)
            .background(accent.copy(alpha = if (selected) .10f else .055f))
            .border(1.dp, accent.copy(alpha = if (selected) .42f else .20f), shape)
            .kineticClickable(role = Role.Button, onClick = onView)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = .14f)),
            contentAlignment = Alignment.Center
        ) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(17.dp), color = accent, strokeWidth = 2.dp)
            } else {
                Text(sub.name.trim().firstOrNull()?.uppercase() ?: "S", color = accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(sub.name, color = Aether.Ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$count nodes • ${if (local) "LOCAL" else relativeTime(sub.updatedAt)}${if (selected) " • VIEWING" else ""}",
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .kineticClickable(enabled = !repo.busy, role = Role.Button, onClick = onManage),
            contentAlignment = Alignment.Center
        ) {
            Text("⋮", color = Aether.InkMuted, style = MaterialTheme.typography.titleMedium)
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
    onEdit: () -> Unit,
    onDetails: () -> Unit
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
                    if (repo.updateProfileJson(profile.id, jsonText, profile.subscriptionId)) jsonOpen = false
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
            .animateContentSize(MarbleMotionSpecs.Layout)
            .kineticClickable(
                enabled = !repo.busy && !active,
                role = Role.Button
            ) { onConnect(profile) },
        borderColor = when {
            testing -> Aether.Cyan.copy(alpha = .55f)
            active -> Aether.Emerald.copy(alpha = .55f)
            else -> Aether.GlassBorderSoft
        },
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                val quality = libraryPingQuality(latency)
                val bars = libraryPingBars(latency)
                Column(
                    modifier = Modifier
                        .widthIn(min = 92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(health.copy(alpha = .075f))
                        .border(1.dp, health.copy(alpha = .20f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        (0 until 4).forEach { index ->
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height((5 + index * 3).dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index < bars) health
                                        else Aether.GlassBorderSoft.copy(alpha = .55f)
                                    )
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$latency ms",
                            color = health,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                    val variation = measured
                        ?.takeIf { it.probeKind == "TUNNEL" && it.sampleCount >= 2 }
                        ?.jitterMs
                        ?.roundToInt()
                    Text(
                        listOfNotNull(
                            measured?.probeKind ?: "PING",
                            variation?.let { "±$it ms" },
                            quality
                        ).joinToString(" • "),
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(7.dp))
            }

            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .kineticClickable(role = Role.Button) { menuOpen = true },
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
                        text = { Text("Details") },
                        onClick = {
                            menuOpen = false
                            onDetails()
                        }
                    )
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
                    if (!profile.scheme.equals("ssh", true)) {
                        DropdownMenuItem(
                            text = { Text("Edit Xray JSON") },
                            onClick = {
                                menuOpen = false
                                jsonText = profile.configJson
                                jsonOpen = true
                            }
                        )
                    }
                    if (repo.settings.manualSourceEnabled) {
                        DropdownMenuItem(
                            text = { Text("Duplicate to Manual") },
                            onClick = {
                                menuOpen = false
                                repo.duplicateProfile(profile.id, profile.subscriptionId)
                            }
                        )
                    }
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
                            repo.removeProfile(profile.id, profile.subscriptionId)
                        }
                    )
                }
            }
        }

        // Full-width metadata rail: the hostname may flex/ellipsis, while the port is always
        // visible. Keeping it below the action row prevents long endpoints from moving controls.
        val endpointShape = RoundedCornerShape(13.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(endpointShape)
                .background(Aether.Cyan.copy(alpha = .045f))
                .border(1.dp, Aether.Cyan.copy(alpha = .11f), endpointShape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "HOST",
                color = Aether.Cyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                profile.host.trim().ifBlank { "Unknown host" },
                modifier = Modifier.weight(1f),
                color = Aether.InkMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Aether.GlassBorderSoft)
            )
            Text(
                "PORT",
                color = Aether.InkFaint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                profile.port.takeIf { it > 0 }?.toString() ?: "—",
                color = Aether.Ink,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
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

        if (measured != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                measured.let { evidence ->
                    HoloBadge(
                        evidence.probeKind,
                        if (evidence.probeKind == "TUNNEL") Aether.Emerald else Aether.Cyan,
                        compact = true
                    )
                    HoloBadge(
                        "${evidence.success}% ok",
                        if (evidence.success >= 90) Aether.Emerald else Aether.Amber,
                        compact = true
                    )
                }
            }
        }
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
                "",
                if (expertMode) "Expert" else "Simple",
                if (expertMode) Aether.Cyan else Aether.Emerald
            )
        }

        item {
            HoloGlass(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Appearance", color = Aether.Ink, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CyberChoiceChip(
                            "System",
                            repo.settings.theme.equals("system", true),
                            Aether.Cyan
                        ) {
                            repo.updateSettings(repo.settings.copy(theme = "system"))
                        }
                        CyberChoiceChip(
                            "White",
                            repo.settings.theme.equals("light", true),
                            Aether.Cyan
                        ) {
                            repo.updateSettings(repo.settings.copy(theme = "light"))
                        }
                        CyberChoiceChip(
                            "Dark",
                            repo.settings.theme.equals("dark", true),
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

                HorizontalDivider(color = Aether.GlassBorderSoft)
                Text("UPDATES", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                SettingSwitch(
                    "Automatic app update checks",
                    "Check GitHub Releases whenever MarbleNG returns to the foreground",
                    repo.settings.appUpdateCheckEnabled
                ) { enabled ->
                    repo.updateSettings(repo.settings.copy(appUpdateCheckEnabled = enabled))
                    if (enabled) repo.checkForAppUpdate(force = true)
                }

                HorizontalDivider(color = Aether.GlassBorderSoft)
                Text("HOME LAYOUT", color = Aether.InkFaint, style = MaterialTheme.typography.labelSmall)
                SettingSwitch(
                    "Summary metrics on Home",
                    "Show Nodes, Xray OK and Mode below the connection panel",
                    repo.settings.homeShowSummaryMetrics
                ) { repo.updateSettings(repo.settings.copy(homeShowSummaryMetrics = it)) }
                SettingSwitch(
                    "Iran Mode card on Home",
                    "Hide only the Home card; Iran Mode protection keeps running",
                    repo.settings.homeShowIranMode
                ) { repo.updateSettings(repo.settings.copy(homeShowIranMode = it)) }
                SettingSwitch(
                    "Quick Actions on Home",
                    "Show or hide Rank, Library, Privacy and Routing shortcuts",
                    repo.settings.homeShowQuickActions
                ) { repo.updateSettings(repo.settings.copy(homeShowQuickActions = it)) }
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

    }
}

@Suppress("UNUSED_PARAMETER")
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
        animationSpec = MarbleMotionSpecs.Color,
        label = "accordion-icon-$title"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(MarbleMotionSpecs.Layout)
            .clip(shape)
            .background(Aether.VoidElevated)
            .border(1.dp, Aether.GlassBorderSoft, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().kineticClickable(role = Role.Button) { open = !open },
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
                    transitionSpec = {
                        fadeIn(MarbleMotionSpecs.ResponseFloat) togetherWith
                            fadeOut(MarbleMotionSpecs.ExitFloat)
                    },
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
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(MarbleMotionSpecs.Layout) +
                fadeIn(MarbleMotionSpecs.ResponseFloat),
            exit = shrinkVertically(MarbleMotionSpecs.Layout) +
                fadeOut(MarbleMotionSpecs.ExitFloat)
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

    Text(
        "Last successful route is remembered automatically for one-tap Home reconnect.",
        color = Aether.InkFaint,
        style = MaterialTheme.typography.bodySmall
    )
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
            NumberSetting("Strategies per pass", s.connectTuningMethods, 1..8) {
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).kineticClickable(role=Role.Checkbox,onClick=onToggle).padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if(checked)Aether.Emerald.copy(alpha=.12f) else Aether.GlassStrong),contentAlignment=Alignment.Center){Text(app.label.trim().firstOrNull()?.uppercase()?:"•",color=if(checked)Aether.Emerald else Aether.InkMuted,style=MaterialTheme.typography.labelLarge)};Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(app.label,color=Aether.Ink,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Text(app.packageName,color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Checkbox(checked,{onToggle()},colors=CheckboxDefaults.colors(checkedColor=Aether.Emerald,checkmarkColor=Aether.Void,uncheckedColor=Aether.GlassBorder))}
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
                repo.libraryProfiles.take(80).forEach { profile ->
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
    val underlay = repo.networkSnapshot

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        HoloBadge(
            if (repo.settings.ipv6Enabled) "IPv6 ENABLED" else "IPv6 BLOCKED",
            if (repo.settings.ipv6Enabled) Aether.Emerald else Aether.InkFaint,
            compact = true
        )
        HoloBadge(
            if (repo.settings.preferIpv6 && underlay.hasIpv6) "IPv6 PREFERRED"
            else if (repo.settings.preferIpv6) "IPv6 PREFERENCE WAITING"
            else "IPv4 / AUTO",
            if (repo.settings.preferIpv6 && underlay.hasIpv6) Aether.Cyan else Aether.Amethyst,
            compact = true
        )
        HoloBadge(underlay.label, Aether.InkMuted, compact = true)
    }

    SettingSwitch(
        title = "Enable IPv6",
        subtitle = "Keep IPv6 inside the protected TUN. When off, Xray blocks ::/0 fail-closed instead of letting Android bypass the VPN.",
        checked = repo.settings.ipv6Enabled
    ) {
        repo.updateSettings(
            repo.settings.copy(
                ipv6Enabled = it,
                preferIpv6 = if (it) repo.settings.preferIpv6 else false
            )
        )
    }

    SettingSwitch(
        title = "Prefer IPv6",
        subtitle = "IPv6-first endpoint dialing with IPv4 fallback; Marble suspends the preference automatically on IPv4-only networks.",
        checked = repo.settings.ipv6Enabled && repo.settings.preferIpv6
    ) {
        repo.updateSettings(
            repo.settings.copy(
                ipv6Enabled = if (it) true else repo.settings.ipv6Enabled,
                preferIpv6 = it
            )
        )
    }

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
        subtitle = "Use physical family reachability for DNS and suspend IPv6 preference on IPv4-only links",
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
        enabled = repo.libraryProfiles.isNotEmpty() && !repo.busy
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
        title = "Manual source",
        subtitle = "Show and enable the built-in Manual source. Off by default; stored Manual nodes stay dormant until enabled.",
        checked = repo.settings.manualSourceEnabled
    ) {
        repo.updateSettings(repo.settings.copy(manualSourceEnabled = it))
    }

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
    val debug = repo.settings.debugModeEnabled

    HoloGlass(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when {
            (report?.failures ?: 0) > 0 -> Aether.Danger.copy(alpha = .55f)
            (report?.warnings ?: 0) > 0 -> Aether.Amber.copy(alpha = .50f)
            debug -> Aether.Cyan.copy(alpha = .55f)
            else -> Aether.GlassBorderSoft
        },
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ultimate runtime observatory", color=Aether.Ink, style=MaterialTheme.typography.titleMedium)
                Text("Passive crash, process, thread, Xray, HEV, VPN and engine evidence. The scanner itself does not generate Internet traffic.",
                    color=Aether.InkFaint, style=MaterialTheme.typography.bodySmall)
            }
            HoloBadge(
                when {
                    repo.busy -> "Scanning"
                    debug -> "DEBUG ON"
                    report == null -> "Ready"
                    report.failures > 0 -> "${report.failures} fail"
                    report.warnings > 0 -> "${report.warnings} warn"
                    else -> "Healthy"
                },
                when {
                    repo.busy -> Aether.Cyan
                    debug -> Aether.Cyan
                    report == null -> Aether.InkMuted
                    report.failures > 0 -> Aether.Danger
                    report.warnings > 0 -> Aether.Amber
                    else -> Aether.Emerald
                }, true
            )
        }

        SettingSwitch(
            title = "Debug Mode • Ultimate TXT log",
            subtitle = if (debug) {
                "ON • runtime, Xray and HEV evidence streams asynchronously to ${repo.debugReportLocation()}"
            } else {
                "Off by default. When enabled, normal app/VPN threads only enqueue bounded events; storage work stays on a diagnostics thread."
            },
            checked = debug
        ) { repo.setDebugMode(it) }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                .background((if(debug) Aether.Cyan else Aether.GlassBorderSoft).copy(alpha=.065f))
                .border(1.dp,(if(debug) Aether.Cyan else Aether.GlassBorderSoft).copy(alpha=.24f),RoundedCornerShape(15.dp))
                .padding(11.dp), verticalArrangement=Arrangement.spacedBy(4.dp)
        ) {
            Text("TXT REPORT LOCATION", color=if(debug) Aether.Cyan else Aether.InkFaint, style=MaterialTheme.typography.labelSmall)
            Text(repo.debugReportLocation(), color=Aether.Ink, style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
            Text("Debug Mode records a rolling live session plus Bug Finder snapshots. Raw proxy configs and credentials are redacted.",
                color=Aether.InkMuted, style=MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CyberButton(if(repo.busy)"SCANNING…" else "RUN ULTIMATE SCAN", Aether.Cyan, Modifier.weight(1f), !repo.busy) { repo.runBugFinder() }
            CyberButton("COPY REPORT", Aether.Amethyst, Modifier.weight(1f), report != null && !repo.busy) {
                clipboard.setText(AnnotatedString(repo.bugFinderReportText()))
                repo.setRuntimeMessage("Ultimate Bug Finder report copied")
            }
        }

        if(report != null) CyberButton("SAVE TXT SNAPSHOT",Aether.Emerald,Modifier.fillMaxWidth(),!repo.busy) { repo.saveBugFinderReport() }

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
                    BugSeverity.PASS->Aether.Emerald; BugSeverity.INFO->Aether.Cyan; BugSeverity.WARN->Aether.Amber; BugSeverity.FAIL->Aether.Danger
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(c.copy(alpha=.055f))
                    .border(1.dp,c.copy(alpha=.20f),RoundedCornerShape(15.dp)).padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(check.title,color=Aether.Ink,style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
                        HoloBadge(check.severity.name,c,true)
                    }
                    Text(check.detail,color=Aether.InkMuted,style=MaterialTheme.typography.bodySmall)
                    if(check.action.isNotBlank()) Text("→ ${check.action}",color=c,style=MaterialTheme.typography.bodySmall)
                }
            }
            if(current.evidence.isNotEmpty()) {
                Text("PRIORITY EVIDENCE",color=Aether.InkFaint,style=MaterialTheme.typography.labelSmall)
                SelectionContainer { Text(current.evidence.joinToString("\n"),color=Aether.InkMuted,
                    style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace)) }
            }
            Text("Full process exits, thread stacks, connection history and retained logs are kept in COPY/SAVE TXT output so this screen stays responsive.",
                color=Aether.InkFaint,style=MaterialTheme.typography.bodySmall)
            if(current.failures>0) CyberButton("SAFE RUNTIME RESET",Aether.Danger,Modifier.fillMaxWidth(),!repo.busy) { repo.safeRuntimeResetFromBugFinder() }
        } ?: Text("Run the scan while the problem is happening. It is passive and does not open diagnostic HTTPS/DNS connections.",
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
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (enabled) color.copy(alpha = .11f) else Aether.GlassStrong)
            .border(
                1.dp,
                if (enabled) color.copy(alpha = .22f) else Aether.GlassBorderSoft,
                shape
            )
            .kineticClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) color else Aether.InkFaint,
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
        animationSpec = MarbleMotionSpecs.Color,
        label = "chip-background-$text"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) color else Aether.InkMuted,
        animationSpec = MarbleMotionSpecs.Color,
        label = "chip-content-$text"
    )

    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .border(
                1.dp,
                if (selected) color.copy(alpha = .30f) else Aether.GlassBorderSoft,
                RoundedCornerShape(11.dp)
            )
            .kineticClickable(role = Role.Button, onClick = onClick)
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
        animationSpec = MarbleMotionSpecs.Color,
        label = "segment-$label"
    )
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(
                1.dp,
                if (selected) color.copy(alpha = .28f) else Aether.GlassBorderSoft,
                RoundedCornerShape(16.dp)
            )
            .kineticClickable(role = Role.Button, onClick = onClick)
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
            .kineticClickable(role = Role.Switch) { onChecked(!checked) }
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
        animationSpec = MarbleMotionSpecs.Color,
        label = "marble-toggle-track"
    )
    val border by animateColorAsState(
        targetValue = if (checked) Aether.Cyan else Aether.GlassBorder,
        animationSpec = MarbleMotionSpecs.Color,
        label = "marble-toggle-border"
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) Color.White else Aether.InkMuted,
        animationSpec = MarbleMotionSpecs.Color,
        label = "marble-toggle-thumb"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = MarbleMotionSpecs.Dp,
        label = "marble-toggle-position"
    )

    Box(
        modifier = Modifier
            .width(50.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, border, CircleShape)
            .kineticClickable(role = Role.Switch) { onChecked(!checked) }
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

// MARBLE_LIBRARY_PING_HELPERS_V25_3_1
// Keep the textual quality/bars consistent with healthColor(): green <=130 ms,
// amber <=280 ms, red above 280 ms. The extra <=80 tier only distinguishes
// excellent from merely fast without changing the health color semantics.
private fun libraryPingQuality(latencyMs: Int): String = when {
    latencyMs <= 0 -> "Waiting"
    latencyMs <= 80 -> "Excellent"
    latencyMs <= 130 -> "Fast"
    latencyMs <= 280 -> "Fair"
    else -> "Slow"
}

private fun libraryPingBars(latencyMs: Int): Int = when {
    latencyMs <= 0 -> 0
    latencyMs <= 80 -> 4
    latencyMs <= 130 -> 3
    latencyMs <= 280 -> 2
    else -> 1
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
